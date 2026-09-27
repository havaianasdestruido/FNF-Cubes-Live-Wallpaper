#!/usr/bin/env python3
"""Download the cube face textures used by Cubes Live Wallpaper.

Every texture is taken from the Friday Night Funkin' Phoenix Engine repository
(https://github.com/havaianasdestruido/FNF-Phoenix-Engine), which is licensed
under the Apache License 2.0.

Three "icon groups" are produced (see IconGroups.java for the matching Java
side).  All cubes of a wallpaper wear icons of the currently active group only,
one group at a time:

  * arrows       - the pressed note arrows, cut out of the noteskin sprite
                   sheets (vanilla default/classic/future skins and the pixel
                   skin)
  * characters   - the health icons (icon-*.png)
  * achievements - the achievement icons

The files are written to res/drawable-nodpi/ with names that are valid Android
resource names (lower case letters, digits and underscores only), so they can be
referenced from IconGroups.java as R.drawable.<name>.

Usage:
    python3 tools/fetch_icons.py [--repo owner/name] [--ref main] [--out DIR]

Requires Pillow (pip install pillow).  Files are fetched through the GitHub
contents API; set GITHUB_TOKEN to raise the anonymous rate limit.
"""

import argparse
import os
import re
import sys
import time
import urllib.error
import urllib.request
from io import BytesIO

from PIL import Image

DEFAULT_REPO = "havaianasdestruido/FNF-Phoenix-Engine"
DEFAULT_REF = "main"

CHARACTERS_DIR = "assets/preload/images/icons"
ACHIEVEMENTS_DIR = "assets/preload/images/achievements"

# Noteskins the pressed arrows are taken from: name -> sprite sheet base path.
# The "chip" noteskin is left out on purpose: its sprite sheet stores the very
# same graphic for all four directions, so it would not add any variety.
NOTESKINS = (
    ("default", "assets/shared/images/noteskins/NOTE_assets"),
    ("classic", "assets/shared/images/noteskins/NOTE_assets-classic"),
    ("future", "assets/shared/images/noteskins/NOTE_assets-future"),
)

# Pixel noteskins: the sheet is a plain 4x5 grid without a sprite sheet atlas,
# the first row holds the unpressed arrows, the other rows the pressed ones.
# name -> (sheet, row)
PIXEL_SKINS = (
    ("blue", "assets/shared/images/pixelUI/noteskins/NOTE_assets.png", 1),
    ("dark", "assets/shared/images/pixelUI/noteskins/NOTE_assets.png", 2),
    ("green", "assets/shared/images/pixelUI/noteskins/NOTE_assets.png", 3),
    ("red", "assets/shared/images/pixelUI/noteskins/NOTE_assets.png", 4),
)
PIXEL_CELLS = 4          # arrows per row: left, down, up, right
PIXEL_SCALE = 4          # the pixel arrows are tiny, blow them up a bit

# The icons end up as OpenGL textures, one texture per icon, and a live
# wallpaper should not eat up the whole graphic card memory. 256 pixels are
# plenty for a cube face, so bigger originals are scaled down.
MAX_TEXTURE_SIZE = 256

ARROW_DIRECTIONS = ("left", "down", "up", "right")

ATLAS_FRAME_RE = re.compile(
    r'<SubTexture\s+name="([^"]+)"\s+x="(-?\d+)"\s+y="(-?\d+)"\s+'
    r'width="(\d+)"\s+height="(\d+)"([^/>]*)/?>'
)


class GitHubError(Exception):
    pass


def github_download(repo, ref, path, token=None, retries=3):
    """Return the raw content of a single file of the repository."""
    url = "https://api.github.com/repos/%s/contents/%s?ref=%s" % (repo, path, ref)
    headers = {"Accept": "application/vnd.github.raw", "User-Agent": "cubes-wallpaper"}
    if token:
        headers["Authorization"] = "token %s" % token
    last_error = None
    for attempt in range(retries):
        try:
            request = urllib.request.Request(url, headers=headers)
            return urllib.request.urlopen(request, timeout=120).read()
        except urllib.error.HTTPError as error:
            if error.code == 404:
                raise GitHubError("%s not found in %s" % (path, repo))
            last_error = error
        except urllib.error.URLError as error:
            last_error = error
        time.sleep(2 * (attempt + 1))
    raise GitHubError("could not download %s: %s" % (path, last_error))


def github_list(repo, ref, path, token=None):
    """Return the file names of a directory of the repository."""
    url = "https://api.github.com/repos/%s/contents/%s?ref=%s" % (repo, path, ref)
    headers = {"Accept": "application/vnd.github.raw", "User-Agent": "cubes-wallpaper"}
    if token:
        headers["Authorization"] = "token %s" % token
    request = urllib.request.Request(url, headers=headers)
    body = urllib.request.urlopen(request, timeout=120).read().decode("utf-8")
    names = []
    for entry in re.findall(r'"name":\s*"([^"]+)"\s*,\s*"path"', body):
        names.append(entry)
    if not names:
        raise GitHubError("could not list %s" % path)
    return names


def parse_atlas(atlas):
    """Parse a Sparrow/Starling sprite sheet atlas (the format Funkin uses)."""
    frames = {}
    for match in ATLAS_FRAME_RE.finditer(atlas):
        name = match.group(1)
        x, y = int(match.group(2)), int(match.group(3))
        width, height = int(match.group(4)), int(match.group(5))
        rotated = 'rotated="true"' in match.group(6)
        frames[name] = (x, y, width, height, rotated)
    return frames


def cut_frame(sheet, frame):
    """Cut a frame out of a sprite sheet, undoing the atlas rotation."""
    x, y, width, height, rotated = frame
    image = sheet.crop((x, y, x + width, y + height))
    if rotated:
        # The frame was packed rotated by 90 degrees, rotate it back.
        image = image.transpose(Image.ROTATE_90)
    return image


def pad_to_square(image):
    """Center an image on a transparent square canvas of the same size."""
    size = max(image.width, image.height)
    if image.width == size and image.height == size:
        return image
    square = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    square.paste(image, ((size - image.width) // 2, (size - image.height) // 2), image)
    return square


def slugify(name, strip=None):
    """Turn a file name into a valid Android resource name fragment."""
    name = name.rsplit(".", 1)[0]
    if strip and name.startswith(strip):
        name = name[len(strip):]
    name = re.sub(r"[^0-9a-zA-Z]+", "_", name).strip("_").lower()
    return name or "icon"


def save_png(image, directory, name):
    image = pad_to_square(image.convert("RGBA"))
    if max(image.size) > MAX_TEXTURE_SIZE:
        scale = float(MAX_TEXTURE_SIZE) / float(max(image.size))
        image = image.resize(
            (max(1, int(round(image.width * scale))), max(1, int(round(image.height * scale)))),
            Image.LANCZOS,
        )
    path = os.path.join(directory, name + ".png")
    image.save(path, "PNG", optimize=True)
    return path


def fetch_pressed_arrows(repo, ref, out_dir, token):
    """Cut the pressed note arrows out of the noteskin sprite sheets."""
    written = []
    for skin, base in NOTESKINS:
        sheet = Image.open(BytesIO(github_download(repo, ref, base + ".png", token)))
        atlas = github_download(repo, ref, base + ".xml", token).decode("utf-8")
        frames = parse_atlas(atlas)
        for direction in ARROW_DIRECTIONS:
            frame_name = "%s press0000" % direction
            if frame_name not in frames:
                raise GitHubError("frame %r missing from %s" % (frame_name, base))
            image = cut_frame(sheet, frames[frame_name])
            written.append(save_png(image, out_dir, "ic_arrow_%s_%s_press" % (skin, direction)))
    return written


def fetch_pixel_arrows(repo, ref, out_dir, token):
    """Cut the pressed arrows out of the pixel noteskin grid."""
    written = []
    sheets = {}
    for skin, path, row in PIXEL_SKINS:
        if path not in sheets:
            sheets[path] = Image.open(BytesIO(github_download(repo, ref, path, token))).convert("RGBA")
        sheet = sheets[path]
        cell = sheet.width // PIXEL_CELLS
        if sheet.height // cell < len(PIXEL_SKINS) + 1:
            raise GitHubError("unexpected pixel noteskin layout in %s" % path)
        for column, direction in enumerate(ARROW_DIRECTIONS):
            box = (column * cell, row * cell, (column + 1) * cell, (row + 1) * cell)
            image = sheet.crop(box)
            if PIXEL_SCALE != 1:
                image = image.resize((image.width * PIXEL_SCALE, image.height * PIXEL_SCALE), Image.NEAREST)
            written.append(save_png(image, out_dir, "ic_arrow_pixel_%s_%s" % (skin, direction)))
    return written


def fetch_plain_icons(repo, ref, out_dir, token, directory, prefix, strip=None):
    """Copy a whole directory of icons, renaming them on the way."""
    written = []
    for name in github_list(repo, ref, directory, token):
        if not name.lower().endswith(".png"):
            continue
        data = github_download(repo, ref, "%s/%s" % (directory, name), token)
        image = Image.open(BytesIO(data))
        written.append(save_png(image, out_dir, prefix + slugify(name, strip)))
    return written


def main(argv=None):
    parser = argparse.ArgumentParser(description="Download the cube face textures.")
    parser.add_argument("--repo", default=DEFAULT_REPO, help="GitHub owner/name to fetch from")
    parser.add_argument("--ref", default=DEFAULT_REF, help="branch, tag or commit to fetch from")
    parser.add_argument("--out", default=None, help="output directory (default: res/drawable-nodpi)")
    parser.add_argument("--clean", action="store_true", help="remove ic_*.png files before writing")
    args = parser.parse_args(argv)

    here = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
    out_dir = args.out or os.path.join(here, "res", "drawable-nodpi")
    if not os.path.isdir(out_dir):
        os.makedirs(out_dir)
    if args.clean:
        for name in os.listdir(out_dir):
            if name.startswith("ic_") and name.endswith(".png"):
                os.remove(os.path.join(out_dir, name))

    token = os.environ.get("GITHUB_TOKEN")
    try:
        written = []
        written += fetch_pressed_arrows(args.repo, args.ref, out_dir, token)
        written += fetch_pixel_arrows(args.repo, args.ref, out_dir, token)
        written += fetch_plain_icons(args.repo, args.ref, out_dir, token, CHARACTERS_DIR, "ic_char_", strip="icon-")
        written += fetch_plain_icons(args.repo, args.ref, out_dir, token, ACHIEVEMENTS_DIR, "ic_ach_")
    except GitHubError as error:
        sys.stderr.write("error: %s\n" % error)
        return 1

    for path in written:
        print(os.path.relpath(path, here))
    print("%d textures written to %s" % (len(written), os.path.relpath(out_dir, here)))
    return 0


if __name__ == "__main__":
    sys.exit(main())
