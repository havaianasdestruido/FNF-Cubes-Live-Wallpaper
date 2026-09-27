/*
 * Cube texture library
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

import java.io.IOException;
import java.io.InputStream;
import java.util.Random;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.opengl.GLES20;
import android.opengl.GLUtils;
import android.util.Log;

import mobile.wallpaper.cubeslivewallpaper.IconGroup;
import mobile.wallpaper.cubeslivewallpaper.IconGroups;
import mobile.wallpaper.cubeslivewallpaper.M3DM;

/**
 * Loads the cube textures and hands them out to the cubes.
 *
 * The textures are organized in groups (see IconGroups). Only the textures of
 * the currently active group are kept in the graphic card memory: when the wall
 * switches to another group, the textures of the previous group are deleted
 * again, so a live wallpaper does not have to keep every icon alive.
 *
 * The library survives a surface recreation, but every texture it created
 * before is gone with the OpenGL context: invalidate() must be called before
 * the textures are used again.
 */
public class IconLibrary {
	final static String TAG = "IconLibrary";

	private final Context context_;
	private final Random random_ = new Random();

	/** Textures per group, a null entry means "not loaded (yet)". */
	private final M3DM.mD3DTexture textures_[];
	private int activeGroup_ = -1;

	public IconLibrary(Context context) {
		context_ = context;
		textures_ = new M3DM.mD3DTexture[IconGroups.count()][];
	}

	/** True when the textures of the active group are loaded. */
	public boolean isReady() {
		return (activeGroup_ >= 0) && (textures_[activeGroup_] != null);
	}

	/** Name of the active group, null when no group is active. */
	public String activeGroupName() {
		return IconGroups.name(activeGroup_);
	}

	/** Number of textures of the active group, 0 when no group is active. */
	public int activeTextureCount() {
		return isReady() ? textures_[activeGroup_].length : 0;
	}

	/** All textures of the active group, null when no group is active. */
	public M3DM.mD3DTexture[] activeTextures() {
		return isReady() ? textures_[activeGroup_] : null;
	}

	/**
	 * Picks a random icon group and makes its textures current. The new group is
	 * never the group that is active right now, so the wall always changes.
	 *
	 * @return index of the new active group
	 */
	public int selectRandomGroup() {
		int next = 0;
		if (IconGroups.count() > 1) {
			do {
				next = random_.nextInt(IconGroups.count());
			} while (next == activeGroup_);
		}
		setActiveGroup(next);
		return next;
	}

	/** Makes the textures of the group with the given index current. */
	public void setActiveGroup(int index) {
		if (index < 0 || index >= IconGroups.count()) {
			Log.e(TAG, "unknown icon group " + index);
			return;
		}
		if (index == activeGroup_) {
			if (!isReady()) {
				loadGroup(index);
			}
			return;
		}
		// Load the new group first, the cubes must never end up with a texture
		// that was deleted in the meantime.
		loadGroup(index);
		unloadGroup(activeGroup_);
		activeGroup_ = index;
	}

	/**
	 * Forgets every texture, they belonged to an OpenGL context that does not
	 * exist any more.
	 */
	public void invalidate() {
		for (int group = 0; group < textures_.length; group++) {
			textures_[group] = null;
		}
		activeGroup_ = -1;
	}

	private void loadGroup(int index) {
		if (textures_[index] != null) {
			return;
		}
		IconGroup group = IconGroups.group(index);
		if (group == null || group.size() == 0) {
			Log.e(TAG, "icon group " + index + " is empty");
			textures_[index] = new M3DM.mD3DTexture[0];
			return;
		}

		int ids[] = new int[group.size()];
		GLES20.glGenTextures(ids.length, ids, 0);

		// Textures that could not be decoded are dropped, a wallpaper should
		// show the remaining icons instead of showing nothing at all.
		int loaded = 0;
		M3DM.mD3DTexture result[] = new M3DM.mD3DTexture[ids.length];
		for (int i = 0; i < ids.length; i++) {
			if (uploadTexture(ids[i], group.texture(i))) {
				result[loaded++] = new M3DM.mD3DTexture(ids[i]);
			} else {
				GLES20.glDeleteTextures(1, new int[] { ids[i] }, 0);
			}
		}
		if (loaded != result.length) {
			M3DM.mD3DTexture trimmed[] = new M3DM.mD3DTexture[loaded];
			System.arraycopy(result, 0, trimmed, 0, loaded);
			result = trimmed;
		}
		textures_[index] = result;
		Log.i(TAG, "loaded " + loaded + " icons of group " + index + " (" + group.name() + ")");
	}

	private void unloadGroup(int index) {
		if (index < 0 || index >= textures_.length) {
			return;
		}
		M3DM.mD3DTexture group[] = textures_[index];
		if (group == null) {
			return;
		}
		int ids[] = new int[group.length];
		for (int i = 0; i < group.length; i++) {
			ids[i] = group[i].id;
		}
		GLES20.glDeleteTextures(ids.length, ids, 0);
		textures_[index] = null;
	}

	/** Creates one OpenGL texture out of a drawable resource. */
	private boolean uploadTexture(int textureId, int resourceId) {
		InputStream is = context_.getResources().openRawResource(resourceId);
		Bitmap bitmap = null;
		try {
			bitmap = BitmapFactory.decodeStream(is);
		} finally {
			try {
				is.close();
			} catch (IOException e) {
				// Ignore.
			}
		}
		if (bitmap == null) {
			Log.e(TAG, "could not decode texture resource " + resourceId);
			return false;
		}

		GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureId);
		GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR);
		GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);
		GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE);
		GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE);
		GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bitmap, 0);
		bitmap.recycle();
		return true;
	}
}
