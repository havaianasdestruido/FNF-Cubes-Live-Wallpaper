/*
 * Renderer/main loop class
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
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.Random;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

import net.rbgrn.opengl.GLWallpaperService.GLEngine;

import mobile.wallpaper.cubeslivewallpaper.Game;
import mobile.wallpaper.cubeslivewallpaper.IconLibrary;
import mobile.wallpaper.cubeslivewallpaper.M3DM;
import mobile.wallpaper.cubeslivewallpaper.M3DMATRIX;
import mobile.wallpaper.cubeslivewallpaper.M3DVECTOR;
import mobile.wallpaper.cubeslivewallpaper.Game.mD3DObject;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.opengl.GLUtils;
import android.os.SystemClock;
import android.util.Log;

public class GLES20Renderer implements GLSurfaceView.Renderer {

	final static String TAG = "GLES20Renderer";
	
	/** How long a wall wears one icon group before it switches to another one. */
	final static long ICON_GROUP_SWITCH_MS = 25000;
	/** How long the cubes keep their icons before they pick new ones of the active group. */
	final static long ICON_SHUFFLE_MS = 8000;
	
	boolean reloadedTextures = false;
	boolean preferencesChanged = false;
    
    long _t1,_t2;				// used for FPS calculation
	float FPS = 30.0f;			// frames per seconds
	float Tc = 1.0f/FPS;		// duration of 1 frame
	int Nrenderedframe = 0;
    
	M3DM DEV = null;
	Game game = null;
	M3DM.mD3DFrame scene;
	M3DM.mD3DFrame fcube;
	M3DM.mD3DMesh cube;
	M3DM.mD3DTexture tcube;
	
	Context mContext;
	
	M3DMATRIX mRotation;
    
    /** The icon groups the cubes can wear, see IconGroups. */
    IconLibrary iconLibrary_ = null;
    /** True when the OpenGL textures of the current surface are created. */
    boolean iconsReady_ = false;
    /** The original Android texture, used only when no icon could be decoded. */
    M3DM.mD3DTexture fallbackTexture_ = null;
    long lastGroupSwitch_ = 0;
    long lastIconShuffle_ = 0;
    
    private int mDelay = 10;
    private int mCubes = 5;

    private SharedPreferences preferences_;
	private SettingsUpdater settingsUpdater_;
  
	public GLES20Renderer(Context context) {
		super();
		mContext = context;
		initialize();
	}
	
    public void setDelay(int delay) {
    	mDelay = delay;
    }
    
    public void setCubes(int cubes) {
    	mCubes = cubes;
		initialize();
    }
    
	public void setSharedPreferences(SharedPreferences preferences)
	{
		settingsUpdater_ = new SettingsUpdater(this);
		preferences_ = preferences;
		preferences_.registerOnSharedPreferenceChangeListener(settingsUpdater_);
		settingsUpdater_.onSharedPreferenceChanged(preferences_, null);
	}
	
	private class SettingsUpdater implements SharedPreferences.OnSharedPreferenceChangeListener {
		private GLES20Renderer renderer_;
		
		public SettingsUpdater(GLES20Renderer renderer)
		{
			renderer_ = renderer;
		}
		
		@Override
		public void onSharedPreferenceChanged(
				SharedPreferences sharedPreferences, String key) {
			try
			{

				int delay = sharedPreferences.getInt("delayPref",10);
				renderer_.setDelay(delay);
				
				int cubes = sharedPreferences.getInt("cubesPref",5);
				renderer_.setCubes(cubes);
				
			}
			catch(final Exception e)
			{
				Log.e(TAG, "PREF init error: " + e);			
			}
		}
	}
	
    public void onSurfaceCreated(GL10 unused, EGLConfig config) {
    	
    	_t2 = System.nanoTime();
    	
    	if (DEV == null || game == null) {
    		initialize();
    	}
    	
    	
    	DEV.initializeGL();
    	
    	// the icons are transparent around the artwork, so the cubes have to be
    	// blended with whatever is behind them
        GLES20.glEnable(GLES20.GL_BLEND);
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA);
    	
        // Cube textures: the icons of a randomly picked group are loaded and
        // handed out to the cubes. The wall switches between the groups from
        // time to time, see onDrawFrame.
        loadIcons();
        
        // TODO background/sphere texture not created
        /*  // Texture 2
  		int[] textures2 = new int[1];
        GLES20.glGenTextures(1, textures2, 0);

        M3DM.mD3DTexture texture2 = new M3DM.mD3DTexture();
        texture2.id = textures2[0];
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texture2.id);

        GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR);
        GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);

        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE);

        InputStream is2 = mContext.getResources().openRawResource(R.raw.background);
        Bitmap bitmap2;
        try {
            bitmap2 = BitmapFactory.decodeStream(is2);
        } finally {
            try {
                is.close();
            } catch(IOException e) {
                // Ignore.
            }
        }

        GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bitmap2, 0);
        bitmap2.recycle(); 
  		*/
        
        // Set the background frame color
        GLES20.glClearColor(0.0f, 0.0f, 0.0f, 1.0f);

        reloadedTextures = true;
    }
    
    /**
     * Creates the cube textures of a randomly picked icon group and hands them
     * out to the cubes. Called whenever the OpenGL surface, and with it every
     * texture, was (re)created.
     */
    private void loadIcons() {
    	if (iconLibrary_ == null) {
    		iconLibrary_ = new IconLibrary(mContext);
    	} else {
    		// the textures belonged to the previous, now gone OpenGL context
    		iconLibrary_.invalidate();
    	}
    	fallbackTexture_ = null;
    	iconsReady_ = true;
    	
    	long now = SystemClock.uptimeMillis();
    	lastGroupSwitch_ = now;
    	lastIconShuffle_ = now;
    	
    	iconLibrary_.selectRandomGroup();
    	applyIconsToCubes();
    	if (iconLibrary_.activeTextureCount() == 0) {
    		// not a single icon of the group could be decoded, keep the original
    		// Android texture as a fallback
    		fallbackTexture_ = createFallbackTexture();
    		applyIconsToCubes();
    	}
    	Log.i(TAG, "icon group: " + iconLibrary_.activeGroupName() + " (" + iconLibrary_.activeTextureCount() + " icons)");
    }
    
    /**
     * Gives every cube a random icon of the active group. All cubes of the wall
     * always wear icons of one single group, never a mixture of two groups.
     */
    private void applyIconsToCubes() {
    	if (!iconsReady_ || game == null) {
    		return;
    	}
    	M3DM.mD3DTexture icons[] = (iconLibrary_ != null) ? iconLibrary_.activeTextures() : null;
    	if (icons == null || icons.length == 0) {
    		// no icon of the group could be decoded, use the original texture
    		icons = (fallbackTexture_ != null) ? new M3DM.mD3DTexture[] { fallbackTexture_ } : null;
    	}
    	game.setIcons(icons);
    }
    
    /** The original Android texture, used only when no icon is available. */
    private M3DM.mD3DTexture createFallbackTexture() {
    	InputStream is = mContext.getResources().openRawResource(R.raw.logo);
    	Bitmap bitmap = null;
        try {
            bitmap = BitmapFactory.decodeStream(is);
        } finally {
            try {
                is.close();
            } catch(IOException e) {
                // Ignore.
            }
        }
        if (bitmap == null) {
        	return null;
        }
        
        int[] textures = new int[1];
        GLES20.glGenTextures(1, textures, 0);
        M3DM.mD3DTexture texture = new M3DM.mD3DTexture(textures[0]);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texture.id);
        GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR);
        GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE);
        GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bitmap, 0);
        bitmap.recycle();
        return texture;
    }

    public void initialize() {

    	DEV = new M3DM();
    	
    	// create 1st light
		DEV.N_Lights = 1;
		DEV.Light[0].AR = 0.1f;
		DEV.Light[0].AG = 0.1f;
		DEV.Light[0].AB = 0.1f;
		DEV.Light[0].DR = 1.0f;
		DEV.Light[0].DG = 1.0f;
		DEV.Light[0].DB = 1.0f;
		DEV.Light[0].SR = 1.0f;
		DEV.Light[0].SG = 1.0f;
		DEV.Light[0].SB = 1.0f;
		DEV.Light[0].AT = 0.0005f;
		DEV.Light[0].Pos = new M3DVECTOR(0.0f, 0.0f, 10.0f);

		
		// create scene
		scene = new M3DM.mD3DFrame();
		
		
		// TODO surrounding sphere not rendered
	/*	cube = M3DM.createSphere(Game.SPHERE_R, 20, 20, 0.0f, 0.0f, 1.0f, 1.0f);
		
		//cube.generateNormals();
		cube.Textures = 1;
		cube.setTexture(0, texture2);
		
		M3DM.M3DMATERIAL material = new M3DM.M3DMATERIAL(1.0f, 1.0f, 1.0f, 1.0f, 1.0f, 1.0f, 1.0f, 1.0f, 1.0f, 1.0f, 1.0f, 1.0f, 0.5f, 0.5f, 0.5f, 1.0f, 2.0f, 0.0f, 1.0f, 1.0f);
		cube.setMaterial(material);
		fcube = new M3DM.mD3DFrame(scene);
		fcube.Orientation = new M3DVECTOR(0.0f, 0.0f, -1.0f);
		cube.setFlags(M3DM.MD3DMESHF_FRONTCULLING);
		fcube.addMesh(cube);*/

	   	game = new Game(mCubes);
	    game.initSys(scene);
		
		// the new cubes have no texture yet, hand out the icons of the active
		// group again (does nothing before the textures were created)
		applyIconsToCubes();
    }
    
    public void onDrawFrame(GL10 unused) {

    	// Redraw background color
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT | GLES20.GL_DEPTH_BUFFER_BIT);
        
        // Main Loop
 		
        // switch the whole wall to another icon group from time to time and let
        // the cubes pick new icons of the active group in between
        if (iconsReady_ && iconLibrary_ != null) {
        	long now = SystemClock.uptimeMillis();
        	if (now - lastGroupSwitch_ >= ICON_GROUP_SWITCH_MS) {
        		lastGroupSwitch_ = now;
        		lastIconShuffle_ = now;
        		iconLibrary_.selectRandomGroup();
        		applyIconsToCubes();
        		Log.i(TAG, "icon group: " + iconLibrary_.activeGroupName() + " (" + iconLibrary_.activeTextureCount() + " icons)");
        	} else if (now - lastIconShuffle_ >= ICON_SHUFFLE_MS) {
        		lastIconShuffle_ = now;
        		applyIconsToCubes();
        	}
        }
        
        game.Tc = Tc;
        game.computeScene();
        
		DEV.renderFrame(scene);
		if (reloadedTextures) { 
			reloadedTextures = false;
			DEV.renderFrame(scene);
		}
		
		int glError = GLES20.glGetError();
		if (glError != GLES20.GL_NO_ERROR) {
			Log.e("OpenGL error", "Error code " + glError);
		}
 
		/******** FPS *********/
		Nrenderedframe++;
		if (Nrenderedframe % 25 == 0) {
			_t1 = _t2; // used for FPS
			_t2 = System.nanoTime();
			double _t = ((double) (_t2 - _t1)) / 1.0e9;
			Tc = (float)(_t / ((double) Nrenderedframe));
			if (Tc != 0.0f) {
				FPS = 1.0f / Tc;
			}
			Nrenderedframe = 0;
		}
		
		try {
			Thread.sleep(mDelay);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
    	
    }
    
    public void onSurfaceChanged(GL10 unused, int width, int height) {
        GLES20.glViewport(0, 0, width, height);
        
        DEV.initialize(width, height, 2.0f, 200.0f, new M3DVECTOR(0.0f, 0.0f, 45.0f), new M3DVECTOR(0.0f, 0.0f, -1.0f), new M3DVECTOR(0.0f, 1.0f, 0.0f));
        
    }
}
