/*
 * Definition of the icon groups ("icon modes") of the wallpaper.
 *
 * This file is part of Cubes Live Wallpaper
 * Cubes Live Wallpaper is free software: you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

    Cubes Live Wallpaper is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with Cubes Live Wallpaper.  If not, see <http://www.gnu.org/licenses/>.
 */

package mobile.wallpaper.cubeslivewallpaper;

/**
 * The groups of cube textures the wallpaper can wear.
 *
 * A "group" is one icon mode: all cubes of a wallpaper always wear icons of the
 * currently active group only, never a mixture of two groups. The active group
 * is picked at random and re-picked from time to time, see
 * GLES20Renderer.ICON_GROUP_SWITCH_MS.
 *
 * The textures themselves live in res/drawable-nodpi and are downloaded from
 * the Friday Night Funkin' Phoenix Engine by tools/fetch_icons.py. To add an
 * icon, drop it into res/drawable-nodpi and add it to the group below.
 */
public class IconGroups {
	/** Index of the group holding the pressed note arrows. */
	public static final int ARROWS = 0;
	/** Index of the group holding the health icons of the characters. */
	public static final int CHARACTERS = 1;
	/** Index of the group holding the achievement icons. */
	public static final int ACHIEVEMENTS = 2;

	private static final IconGroup[] GROUPS = new IconGroup[] {
		new IconGroup("Arrows", new int[] {
				R.drawable.ic_arrow_default_down_press, R.drawable.ic_arrow_default_left_press, R.drawable.ic_arrow_default_right_press, R.drawable.ic_arrow_default_up_press,
				R.drawable.ic_arrow_classic_down_press, R.drawable.ic_arrow_classic_left_press, R.drawable.ic_arrow_classic_right_press, R.drawable.ic_arrow_classic_up_press,
				R.drawable.ic_arrow_future_down_press, R.drawable.ic_arrow_future_left_press, R.drawable.ic_arrow_future_right_press, R.drawable.ic_arrow_future_up_press,
				R.drawable.ic_arrow_pixel_blue_down, R.drawable.ic_arrow_pixel_blue_left, R.drawable.ic_arrow_pixel_blue_right, R.drawable.ic_arrow_pixel_blue_up,
				R.drawable.ic_arrow_pixel_dark_down, R.drawable.ic_arrow_pixel_dark_left, R.drawable.ic_arrow_pixel_dark_right, R.drawable.ic_arrow_pixel_dark_up,
				R.drawable.ic_arrow_pixel_green_down, R.drawable.ic_arrow_pixel_green_left, R.drawable.ic_arrow_pixel_green_right, R.drawable.ic_arrow_pixel_green_up,
				R.drawable.ic_arrow_pixel_red_down, R.drawable.ic_arrow_pixel_red_left, R.drawable.ic_arrow_pixel_red_right, R.drawable.ic_arrow_pixel_red_up,
		}),
		new IconGroup("Characters", new int[] {
				R.drawable.ic_char_bf, R.drawable.ic_char_bf_old, R.drawable.ic_char_bf_pixel, R.drawable.ic_char_bfclassic,
				R.drawable.ic_char_bfdoki, R.drawable.ic_char_bffps, R.drawable.ic_char_bfleather, R.drawable.ic_char_bfmup,
				R.drawable.ic_char_bfnonsense, R.drawable.ic_char_bfos, R.drawable.ic_char_dad, R.drawable.ic_char_darnell,
				R.drawable.ic_char_face, R.drawable.ic_char_gf, R.drawable.ic_char_mom, R.drawable.ic_char_monster,
				R.drawable.ic_char_parents, R.drawable.ic_char_pico, R.drawable.ic_char_senpai_pixel, R.drawable.ic_char_spirit_pixel,
				R.drawable.ic_char_spooky, R.drawable.ic_char_tankman,
		}),
		new IconGroup("Achievements", new int[] {
				R.drawable.ic_ach_debugger, R.drawable.ic_ach_friday_night_play, R.drawable.ic_ach_hype, R.drawable.ic_ach_lockedachievement,
				R.drawable.ic_ach_oversinging, R.drawable.ic_ach_roadkill_enthusiast, R.drawable.ic_ach_toastie, R.drawable.ic_ach_two_keys,
				R.drawable.ic_ach_ur_bad, R.drawable.ic_ach_ur_good, R.drawable.ic_ach_week1_nomiss, R.drawable.ic_ach_week2_nomiss,
				R.drawable.ic_ach_week3_nomiss, R.drawable.ic_ach_week4_nomiss, R.drawable.ic_ach_week5_nomiss, R.drawable.ic_ach_week6_nomiss,
				R.drawable.ic_ach_week7_nomiss, R.drawable.ic_ach_weekend1_nomiss,
		}),
	};

	/** Number of available icon groups. */
	public static int count() {
		return GROUPS.length;
	}

	/** The group with the given index, null when the index is out of range. */
	public static IconGroup group(int index) {
		if (index < 0 || index >= GROUPS.length) {
			return null;
		}
		return GROUPS[index];
	}

	/** Name of the group with the given index, null when the index is out of range. */
	public static String name(int index) {
		IconGroup group = group(index);
		return (group != null) ? group.name() : null;
	}
}
