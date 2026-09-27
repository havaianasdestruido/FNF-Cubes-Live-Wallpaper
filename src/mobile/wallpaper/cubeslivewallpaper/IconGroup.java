/*
 * One group of cube textures ("icon mode")
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
 * A single icon group: a name and the textures (drawable resource ids) that
 * belong to it. All cubes of a wallpaper wear icons of the active group only,
 * see IconGroups and IconLibrary.
 */
class IconGroup {
	private final String name_;
	private final int textures_[];

	IconGroup(String name, int textures[]) {
		name_ = name;
		textures_ = textures;
	}

	/** Human readable name of the group, used for logging only. */
	String name() {
		return name_;
	}

	/** Number of textures in the group. */
	int size() {
		return textures_.length;
	}

	/** Drawable resource id of the texture with the given index. */
	int texture(int index) {
		return textures_[index];
	}
}
