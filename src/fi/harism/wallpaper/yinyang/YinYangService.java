/*
   Copyright 2012 Harri Smatt

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License.
 */

package fi.harism.wallpaper.yinyang;

import java.nio.ByteBuffer;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.service.wallpaper.WallpaperService;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.widget.Toast;

/**
 * Wallpaper entry point.
 */
public final class YinYangService extends WallpaperService {

	@Override
	public Engine onCreateEngine() {
		return new WallpaperEngine();
	}

	/**
	 * Private wallpaper engine implementation.
	 */
	private final class WallpaperEngine extends Engine implements Runnable {

		// Screen aspect ratio.
		private final float mAspectRatio[] = new float[2];
		// GLSurfaceView implementation.
		private YinYangSurfaceView mGLSurfaceView;
		// Boolean for indicating whether touch events are being followed.
		private boolean mTouchFollow = false;
		// Two {x, y } tuples for indicating touch start and current position.
		private final float mTouchPositions[] = new float[4];
		// Last touch event time.
		private long mTouchTime;
		// Screen width and height.
		private int mWidth, mHeight;

		@Override
		public void onCreate(SurfaceHolder surfaceHolder) {

			// Uncomment for debugging.
			// android.os.Debug.waitForDebugger();

			super.onCreate(surfaceHolder);
			mGLSurfaceView = new YinYangSurfaceView();
			setTouchEventsEnabled(true);
		}

		@Override
		public void onDestroy() {
			super.onDestroy();
			mGLSurfaceView.onDestroy();
			mGLSurfaceView = null;
		}

		@Override
		public void onTouchEvent(MotionEvent me) {
			mTouchTime = SystemClock.uptimeMillis();
			switch (me.getAction()) {
			// On touch down set following flag and initialize touch position
			// start
			// and current values.
			case MotionEvent.ACTION_DOWN:
				mTouchFollow = true;
				mTouchPositions[0] = mTouchPositions[2] = (((2f * me.getX()) / mWidth) - 1f)
						* mAspectRatio[0];
				mTouchPositions[1] = mTouchPositions[3] = (1f - ((2f * me
						.getY()) / mHeight)) * mAspectRatio[1];
				mGLSurfaceView.requestRender();
				break;
			// On touch move update current position only.
			case MotionEvent.ACTION_MOVE:
				mTouchPositions[2] = (((2f * me.getX()) / mWidth) - 1f)
						* mAspectRatio[0];
				mTouchPositions[3] = (1f - ((2f * me.getY()) / mHeight))
						* mAspectRatio[1];
				mGLSurfaceView.requestRender();
				break;
			// On touch up mark touch follow flag as false.
			case MotionEvent.ACTION_UP:
				mTouchFollow = false;
				mGLSurfaceView.requestRender();
				break;
			}
		}

		@Override
		public void onVisibilityChanged(boolean visible) {
			super.onVisibilityChanged(visible);
			if (visible) {
				mGLSurfaceView.onResume();
			} else {
				mGLSurfaceView.onPause();
			}
		}

		@Override
		public void run() {
			Toast.makeText(YinYangService.this, R.string.error_shader_compiler,
					Toast.LENGTH_LONG).show();
		}

		/**
		 * Lazy as I am, I din't bother using GLWallpaperService (found on
		 * GitHub) project for wrapping OpenGL functionality into my wallpaper
		 * service. Instead am using GLSurfaceView and trick it into hooking
		 * into Engine provided SurfaceHolder instead of SurfaceView provided
		 * one GLSurfaceView extends. For saving some bytes Renderer is
		 * implemented here too.
		 */
		public final class YinYangSurfaceView extends GLSurfaceView implements
				GLSurfaceView.Renderer {

			// Screen vertices filling whole view.
			private ByteBuffer mScreenVertices;
			// Boolean value for indicating if shader compiler is supported.
			private final boolean mShaderCompilerSupported[] = new boolean[1];
			// Our one and only shader program id.
			private int mShaderProgram = -1;

			/**
			 * Default constructor.
			 */
			public YinYangSurfaceView() {
				super(YinYangService.this);

				setEGLContextClientVersion(2);
				setRenderer(this);
				setRenderMode(GLSurfaceView.RENDERMODE_WHEN_DIRTY);
				onPause();

				final byte SCREEN_COORDS[] = { -1, 1, -1, -1, 1, 1, 1, -1 };
				mScreenVertices = ByteBuffer.allocateDirect(2 * 4);
				mScreenVertices.put(SCREEN_COORDS).position(0);
			}

			@Override
			public SurfaceHolder getHolder() {
				return WallpaperEngine.this.getSurfaceHolder();
			}

			/**
			 * Private shader program loader method.
			 * 
			 * @param vs
			 *            Vertex shader source.
			 * @param fs
			 *            Fragment shader source.
			 * @return Shader program id.
			 */
			private int loadProgram(String vs, String fs) {
				int vertexShader = loadShader(GLES20.GL_VERTEX_SHADER, vs);
				int fragmentShader = loadShader(GLES20.GL_FRAGMENT_SHADER, fs);
				int program = GLES20.glCreateProgram();
				if (program != 0) {
					GLES20.glAttachShader(program, vertexShader);
					GLES20.glAttachShader(program, fragmentShader);
					GLES20.glLinkProgram(program);
					int[] linkStatus = new int[1];
					GLES20.glGetProgramiv(program, GLES20.GL_LINK_STATUS,
							linkStatus, 0);
					if (linkStatus[0] != GLES20.GL_TRUE) {
						String error = GLES20.glGetProgramInfoLog(program);
						GLES20.glDeleteProgram(program);
						throw new RuntimeException(error);
					}
				}
				return program;
			}

			/**
			 * Private shader loader method.
			 * 
			 * @param shaderType
			 *            Vertex or fragment shader.
			 * @param source
			 *            Shader source code.
			 * @return Loaded shader id.
			 */
			private int loadShader(int shaderType, String source) {
				int shader = GLES20.glCreateShader(shaderType);
				if (shader != 0) {
					GLES20.glShaderSource(shader, source);
					GLES20.glCompileShader(shader);
					int[] compiled = new int[1];
					GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS,
							compiled, 0);
					if (compiled[0] == 0) {
						String error = GLES20.glGetShaderInfoLog(shader);
						GLES20.glDeleteShader(shader);
						throw new RuntimeException(error);
					}
				}
				return shader;
			}

			/**
			 * Should be called once underlying Engine is destroyed. Calling
			 * onDetachedFromWindow() will stop rendering thread which is lost
			 * otherwise.
			 */
			public void onDestroy() {
				super.onDetachedFromWindow();
			}

			@Override
			public void onDrawFrame(GL10 unused) {

				// Clear screen buffer.
				GLES20.glClearColor(0, 0, 0, 1);
				GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);

				// If shader compiler is not supported return immediately.
				if (mShaderCompilerSupported[0] == false) {
					return;
				}

				if (!mTouchFollow) {
					long currentTime = SystemClock.uptimeMillis();

					// Adjust "current touch position" towards start touch
					// position in order to hide displacement effect. Which ends
					// once they are equal. We use interpolation for smoother
					// transition no matter what the rendering frame rate is.
					float t = Math.max(0f,
							1f - (currentTime - mTouchTime) * .005f);
					mTouchPositions[2] = mTouchPositions[0]
							+ (mTouchPositions[2] - mTouchPositions[0]) * t;
					mTouchPositions[3] = mTouchPositions[1]
							+ (mTouchPositions[3] - mTouchPositions[1]) * t;

					mTouchTime = currentTime;

					if (Math.abs(mTouchPositions[0] - mTouchPositions[2]) > 0.0001f
							|| Math.abs(mTouchPositions[1] - mTouchPositions[3]) > 0.0001f) {
						requestRender();
					}
				}

				// Disable unneeded rendering flags.
				GLES20.glDisable(GLES20.GL_CULL_FACE);
				GLES20.glDisable(GLES20.GL_BLEND);
				GLES20.glDisable(GLES20.GL_DEPTH_TEST);

				GLES20.glUseProgram(mShaderProgram);
				int uAspectRatio = GLES20.glGetUniformLocation(mShaderProgram,
						"uAspectRatio");
				int uTouchPos = GLES20.glGetUniformLocation(mShaderProgram,
						"uTouchPos");
				int aPosition = GLES20.glGetAttribLocation(mShaderProgram,
						"aPosition");

				GLES20.glUniform2fv(uAspectRatio, 1, mAspectRatio, 0);
				GLES20.glUniform2fv(uTouchPos, 2, mTouchPositions, 0);
				GLES20.glVertexAttribPointer(aPosition, 2, GLES20.GL_BYTE,
						false, 2, mScreenVertices);
				GLES20.glEnableVertexAttribArray(aPosition);

				GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);
			}

			@Override
			public void onSurfaceChanged(GL10 unused, int width, int height) {
				mWidth = width;
				mHeight = height;

				GLES20.glViewport(0, 0, mWidth, mHeight);
				mAspectRatio[0] = (1.1f * mWidth) / Math.min(mWidth, mHeight);
				mAspectRatio[1] = (1.1f * mHeight) / Math.min(mWidth, mHeight);
			}

			@Override
			public void onSurfaceCreated(GL10 unused, EGLConfig config) {
				// Check if shader compiler is supported.
				GLES20.glGetBooleanv(GLES20.GL_SHADER_COMPILER,
						mShaderCompilerSupported, 0);

				// If not, show user an error message and return immediately.
				if (mShaderCompilerSupported[0] == true) {
					new Handler(Looper.getMainLooper())
							.post(WallpaperEngine.this);
					return;
				}

				// Shader compiler supported, load shader.
				mShaderProgram = loadProgram(
						YinYangService.this.getString(R.string.shader_vs),
						YinYangService.this.getString(R.string.shader_fs));
			}

		}

	}

}