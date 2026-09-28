#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
ibge_nomes_scraper.py — Scraper for the IBGE "Nomes no Brasil" (Censo 2022) tool.

Site:  https://censo2022.ibge.gov.br/nomes
API:   GET https://servicodados.ibge.gov.br/api/v3/nomes/2022/localidade/{localidade}/ranking/{dado}?page={n}

Discovered by watching the network calls of the official "Nomes no Brasil"
portal (censo2022.ibge.gov.br/nomes/rankings):
  * dado       : "nome" (first names) or "sobrenome" (surnames)
  * localidade : 0 = Brasil; IBGE locality ids for UF/municipality
  * page       : fixed page size of 30 items (server ignores size/limit params)

One page of the official API looks like:
  {"count":128458,"page":1,"totalPages":4282,"nextPage":2,"previousPage":0,
   "showingFrom":1,"showingTo":30,
   "items":[{"nome":"maria","percent":6.0491,"frequencia":12284478,"rank":1}, ...]}

Output format (JSONL — one JSON object per line):
  {"rank": 1, "nome": "maria", "frequencia": 12284478, "percent": 6.0491}

Stdlib only (no pip install needed).

Examples
--------
# First 10000 first names (Brazil) -> JSONL
python3 tools/ibge_nomes_scraper.py --dado nome --limit 10000 \
    --out data/nomes_censo2022_nomes_top10000.jsonl

# Everything (all 128,458 first names)
python3 tools/ibge_nomes_scraper.py --dado nome --limit 0 \
    --out data/nomes_censo2022_nomes_all.jsonl

# Surnames of the Distrito Federal
python3 tools/ibge_nomes_scraper.py --dado sobrenome --localidade 53 \
    --limit 5000 --out data/sobrenomes_df.jsonl

# Offline: build the JSONL from previously cached raw pages
# (cache accepts page-*.json files or *.ndjson files with one page payload per line)
python3 tools/ibge_nomes_scraper.py --from-cache --cache-dir /tmp/ibge_pages \
    --limit 10000 --out data/nomes_censo2022_nomes_top10000.jsonl
"""

from __future__ import annotations

import argparse
import glob
import json
import os
import sys
import time
import urllib.error
import urllib.request

BASE_URL = (
    "https://servicodados.ibge.gov.br/api/v3/nomes/2022"
    "/localidade/{localidade}/ranking/{dado}"
)
PAGE_SIZE = 30  # fixed by the API (size/limit query params are ignored)
USER_AGENT = (
    "Mozilla/5.0 (compatible; ibge-nomes-scraper/1.0; "
    "+https://censo2022.ibge.gov.br/nomes)"
)


# --------------------------------------------------------------------------- #
# HTTP layer
# --------------------------------------------------------------------------- #
def fetch_page(url: str, retries: int = 5, timeout: int = 30,
               backoff: float = 1.5) -> dict:
    """GET one API page and parse the JSON payload, with retries/backoff."""
    last_err: Exception | None = None
    for attempt in range(1, retries + 1):
        try:
            req = urllib.request.Request(url, headers={"User-Agent": USER_AGENT})
            with urllib.request.urlopen(req, timeout=timeout) as resp:
                return json.loads(resp.read().decode("utf-8"))
        except (urllib.error.URLError, urllib.error.HTTPError,
                TimeoutError, json.JSONDecodeError, OSError) as exc:
            last_err = exc
            wait = backoff ** attempt
            if isinstance(exc, urllib.error.HTTPError) and exc.code == 429:
                wait = max(wait, 5 * attempt)
            print(f"  ! attempt {attempt}/{retries} failed ({exc}); "
                  f"retrying in {wait:.1f}s", file=sys.stderr)
            time.sleep(wait)
    raise RuntimeError(f"failed to fetch {url!r} after {retries} attempts: "
                       f"{last_err}")


# --------------------------------------------------------------------------- #
# Cache layer (raw page payloads saved verbatim; enables offline rebuilds)
# --------------------------------------------------------------------------- #
def save_cache(cache_dir: str | None, page: int, payload: dict) -> None:
    if not cache_dir:
        return
    os.makedirs(cache_dir, exist_ok=True)
    path = os.path.join(cache_dir, f"page-{page:05d}.json")
    with open(path, "w", encoding="utf-8") as fh:
        json.dump(payload, fh, ensure_ascii=False)


def iter_cached_pages(cache_dir: str):
    """Yield (page_number, payload) for every cached raw page, sorted by page.

    Accepts:
      * page-*.json      -> one payload per file
      * *.ndjson         -> one payload per line
    A payload must contain at least {"page": int, "items": [...]}.
    """
    pages: dict[int, dict] = {}
    files = sorted(glob.glob(os.path.join(cache_dir, "page-*.json"))) + \
        sorted(glob.glob(os.path.join(cache_dir, "*.ndjson")))
    for path in files:
        with open(path, "r", encoding="utf-8") as fh:
            text = fh.read().strip()
        if not text:
            continue
        blobs = [text] if path.endswith(".json") else \
            [ln for ln in text.splitlines() if ln.strip()]
        for blob in blobs:
            blob = blob.strip().strip("`")          # tolerate md code fences
            if blob.startswith("json"):
                blob = blob[4:].lstrip()
            try:
                payload = json.loads(blob)
            except json.JSONDecodeError:
                print(f"  ! skipping unparsable blob in {path}",
                      file=sys.stderr)
                continue
            if isinstance(payload, dict) and "items" in payload:
                pages[int(payload.get("page", 0)) or len(pages) + 1] = payload
    for page_no in sorted(pages):
        yield page_no, pages[page_no]


# --------------------------------------------------------------------------- #
# Core scraping / merging
# --------------------------------------------------------------------------- #
def normalize(item: dict) -> dict:
    return {
        "rank": item.get("rank"),
        "nome": item.get("nome"),
        "frequencia": item.get("frequencia"),
        "percent": item.get("percent"),
    }


def scrape_online(args) -> int:
    url_t = BASE_URL.format(localidade=args.localidade, dado=args.dado)

    written = 0
    next_page = 1
    if args.resume and os.path.exists(args.out):
        ranks = []
        with open(args.out, "r", encoding="utf-8") as fh:
            for line in fh:
                line = line.strip()
                if line:
                    ranks.append(json.loads(line)["rank"])
        if ranks:
            written = len(ranks)
            next_page = max(ranks) // PAGE_SIZE + 1
            print(f"resuming: {written} rows already in {args.out}, "
                  f"starting at page {next_page}")

    mode = "a" if (args.resume and written) else "w"
    out = open(args.out, mode, encoding="utf-8")
    total_pages = None
    page = next_page
    try:
        while True:
            if args.limit and written >= args.limit:
                break
            if args.pages and page >= next_page + args.pages:
                break
            if total_pages and page > total_pages:
                break

            payload = fetch_page(f"{url_t}?page={page}",
                                 retries=args.retries, timeout=args.timeout)
            total_pages = payload.get("totalPages") or total_pages
            items = payload.get("items") or []
            save_cache(args.cache_dir, page, payload)

            if not items:
                print(f"page {page}: no items — done.")
                break

            for item in items:
                if args.limit and written >= args.limit:
                    break
                out.write(json.dumps(normalize(item), ensure_ascii=False)
                          + "\n")
                written += 1

            count = payload.get("count", "?")
            print(f"page {page}/{total_pages or '?'} "
                  f"({len(items)} items, total {written} rows; "
                  f"dataset has {count} entries)", flush=True)
            page += 1
            if args.delay:
                time.sleep(args.delay)
    finally:
        out.close()
    return written


def scrape_from_cache(args) -> int:
    if not args.cache_dir:
        raise SystemExit("--from-cache requires --cache-dir DIR")
    written = 0
    expected_page = 1
    with open(args.out, "w", encoding="utf-8") as out:
        for page_no, payload in iter_cached_pages(args.cache_dir):
            if page_no != expected_page:
                raise SystemExit(
                    f"gap in cached pages: expected page {expected_page}, "
                    f"got {page_no} — cache is incomplete")
            items = payload.get("items") or []
            if not items and (payload.get("totalPages") or 0) > 0:
                raise SystemExit(f"page {page_no} cached with 0 items")
            for item in items:
                if args.limit and written >= args.limit:
                    break
                out.write(json.dumps(normalize(item), ensure_ascii=False)
                          + "\n")
                written += 1
            else:
                expected_page += 1
                continue
            break
    print(f"merged {written} rows from cache ({expected_page - 1} pages)")
    return written


# --------------------------------------------------------------------------- #
def main(argv=None) -> int:
    ap = argparse.ArgumentParser(
        description="Scrape IBGE Censo 2022 'Nomes no Brasil' rankings "
                    "(names & surnames) into JSONL.",
        epilog="API: " + BASE_URL)
    ap.add_argument("--dado", choices=["nome", "sobrenome"], default="nome",
                    help="first names (nome) or surnames (sobrenome)")
    ap.add_argument("--localidade", default="0",
                    help="0 = Brasil (default); otherwise an IBGE locality id "
                         "(e.g. 53 = Distrito Federal)")
    ap.add_argument("--limit", type=int, default=10000,
                    help="max rows to output; 0 = everything (default 10000)")
    ap.add_argument("--pages", type=int, default=0,
                    help="max pages to request in this run (0 = no cap)")
    ap.add_argument("--out", default="nomes_censo2022.jsonl",
                    help="output JSONL path")
    ap.add_argument("--delay", type=float, default=0.25,
                    help="politeness delay between requests (s)")
    ap.add_argument("--retries", type=int, default=5)
    ap.add_argument("--timeout", type=int, default=30)
    ap.add_argument("--resume", action="store_true",
                    help="resume an interrupted run using the existing "
                         "output file")
    ap.add_argument("--cache-dir", default=None,
                    help="directory for raw cached page payloads")
    ap.add_argument("--from-cache", action="store_true",
                    help="offline mode: build JSONL solely from --cache-dir")
    args = ap.parse_args(argv)

    if args.limit == 0:
        args.limit = None

    out_dir = os.path.dirname(os.path.abspath(args.out))
    os.makedirs(out_dir, exist_ok=True)

    if args.from_cache:
        written = scrape_from_cache(args)
    else:
        written = scrape_online(args)
    print(f"done: {written} rows -> {args.out}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
