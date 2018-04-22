/*
 * Copyright 2017 Google Inc. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.ar.core.examples.java.helloar;

import android.content.DialogInterface;
import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.os.Bundle;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.Toast;
import com.google.ar.core.Anchor;
import com.google.ar.core.ArCoreApk;
import com.google.ar.core.Camera;
import com.google.ar.core.Frame;
import com.google.ar.core.HitResult;
import com.google.ar.core.Plane;
import com.google.ar.core.Point;
import com.google.ar.core.Point.OrientationMode;
import com.google.ar.core.PointCloud;
import com.google.ar.core.Session;
import com.google.ar.core.Trackable;
import com.google.ar.core.TrackingState;
import com.google.ar.core.examples.java.common.helpers.CameraPermissionHelper;
import com.google.ar.core.examples.java.common.helpers.DisplayRotationHelper;
import com.google.ar.core.examples.java.common.helpers.FullScreenHelper;
import com.google.ar.core.examples.java.common.helpers.SnackbarHelper;
import com.google.ar.core.examples.java.common.helpers.TapHelper;
import com.google.ar.core.examples.java.common.rendering.BackgroundRenderer;
import com.google.ar.core.examples.java.common.rendering.ObjectRenderer;
import com.google.ar.core.examples.java.common.rendering.ObjectRenderer.BlendMode;
import com.google.ar.core.examples.java.common.rendering.PlaneRenderer;
import com.google.ar.core.examples.java.common.rendering.PointCloudRenderer;
import com.google.ar.core.exceptions.CameraNotAvailableException;
import com.google.ar.core.exceptions.UnavailableApkTooOldException;
import com.google.ar.core.exceptions.UnavailableArcoreNotInstalledException;
import com.google.ar.core.exceptions.UnavailableDeviceNotCompatibleException;
import com.google.ar.core.exceptions.UnavailableSdkTooOldException;
import com.google.ar.core.exceptions.UnavailableUserDeclinedInstallationException;
import java.io.IOException;
import java.util.ArrayList;
import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

/**
 * This is a simple example that shows how to create an augmented reality (AR) application using the
 * ARCore API. The application will display any detected planes and will allow the user to tap on a
 * plane to place a 3d model of the Android robot.
 */
public class HelloArActivity extends AppCompatActivity implements GLSurfaceView.Renderer {
  private static final String TAG = HelloArActivity.class.getSimpleName();

  // Rendering. The Renderers are created here, and initialized when the GL surface is created.
  private GLSurfaceView surfaceView;

  private boolean installRequested;

  private Session session;
  private final SnackbarHelper messageSnackbarHelper = new SnackbarHelper();
  private DisplayRotationHelper displayRotationHelper;
  private TapHelper tapHelper;

  private final BackgroundRenderer backgroundRenderer = new BackgroundRenderer();


  // 7 Primary Paint Colors
  private final ObjectRenderer purpleCircle = new ObjectRenderer();
  private final ObjectRenderer yellowCircle = new ObjectRenderer();
  private final ObjectRenderer cyanCircle = new ObjectRenderer();
  private final ObjectRenderer blueCircle = new ObjectRenderer();
  private final ObjectRenderer greenCircle = new ObjectRenderer();
  private final ObjectRenderer redCircle = new ObjectRenderer();
  private final ObjectRenderer blackCircle = new ObjectRenderer();

  // list of all ObjectRenderer instances
  private ObjectRenderer[] paintColors = { purpleCircle, yellowCircle, cyanCircle,
                                blueCircle, greenCircle, redCircle, blackCircle };

  private final PlaneRenderer planeRenderer = new PlaneRenderer();
  private final PointCloudRenderer pointCloudRenderer = new PointCloudRenderer();

  // Temporary matrix allocated here to reduce number of allocations for each frame.
  private final float[] anchorMatrix = new float[16];

  // Anchors created from taps used for object placing.
  private final ArrayList<Anchor> anchors = new ArrayList<>();

//  private String[] curModel = {"drawable/Purple.png", "drawable/Yellow.png","drawable/Cyan.png",
//                               "drawable/Blue.png", "drawable/Green.png", "drawable/Red.png", "drawable/Black.png"};
  private String[] curModel = {"models/purpleSquare.png", "models/yellowSquare.png", "models/cyanSquare.png",
                            "models/greenSquare.png", "models/greenSquare.png", "models/redSquare.png", "models/blackSquare.png" };

  private int curDrawingIdx = 1;

  //Animations for style
  //final Animation pulse = AnimationUtils.loadAnimation(this,R.anim.pulse);
  //final Animation fadein = AnimationUtils.loadAnimation(this,R.anim.fadein);
  //final Animation fadeout = AnimationUtils.loadAnimation(this,R.anim.fadeout);
  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_main);
    surfaceView = (GLSurfaceView) findViewById(R.id.surfaceview);
    displayRotationHelper = new DisplayRotationHelper(/*context=*/ this);




    // Set up tap listener.
    tapHelper = new TapHelper(/*context=*/ this);
    surfaceView.setOnTouchListener(tapHelper);

    // Set up renderer.
    surfaceView.setPreserveEGLContextOnPause(true);
    surfaceView.setEGLContextClientVersion(2);
    surfaceView.setEGLConfigChooser(8, 8, 8,
                                    8, 16, 0); // Alpha used for plane blending.
    surfaceView.setRenderer(this);
    surfaceView.setRenderMode(GLSurfaceView.RENDERMODE_CONTINUOUSLY);

    installRequested = false;
  }

  @Override
  protected void onResume() {
    super.onResume();

    if (session == null) {
      Exception exception = null;
      String message = null;
      try {
        switch (ArCoreApk.getInstance().requestInstall(this, !installRequested)) {
          case INSTALL_REQUESTED:
            installRequested = true;
            return;
          case INSTALLED:
            break;
        }

        // ARCore requires camera permissions to operate. If we did not yet obtain runtime
        // permission on Android M and above, now is a good time to ask the user for it.
        if (!CameraPermissionHelper.hasCameraPermission(this)) {
          CameraPermissionHelper.requestCameraPermission(this);
          return;
        }

        // Create the session.
        session = new Session(/* context= */ this);

      } catch (UnavailableArcoreNotInstalledException
          | UnavailableUserDeclinedInstallationException e) {
        message = "Please install ARCore";
        exception = e;
      } catch (UnavailableApkTooOldException e) {
        message = "Please update ARCore";
        exception = e;
      } catch (UnavailableSdkTooOldException e) {
        message = "Please update this app";
        exception = e;
      } catch (UnavailableDeviceNotCompatibleException e) {
        message = "This device does not support AR";
        exception = e;
      } catch (Exception e) {
        message = "Failed to create AR session";
        exception = e;
      }

      if (message != null) {
        messageSnackbarHelper.showError(this, message);
        Log.e(TAG, "Exception creating session", exception);
        return;
      }
    }

    // Note that order matters - see the note in onPause(), the reverse applies here.
    try {
      session.resume();
    } catch (CameraNotAvailableException e) {
      // In some cases (such as another camera app launching) the camera may be given to
      // a different app instead. Handle this properly by showing a message and recreate the
      // session at the next iteration.
      messageSnackbarHelper.showError(this, "Camera not available. Please restart the app.");
      session = null;
      return;
    }

    surfaceView.onResume();
    displayRotationHelper.onResume();

  }

  @Override
  public void onPause() {
    super.onPause();
    if (session != null) {
      // Note that the order matters - GLSurfaceView is paused first so that it does not try
      // to query the session. If Session is paused before GLSurfaceView, GLSurfaceView may
      // still call session.update() and get a SessionPausedException.
      displayRotationHelper.onPause();
      surfaceView.onPause();
      session.pause();
    }
  }

  @Override
  public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] results) {
    if (!CameraPermissionHelper.hasCameraPermission(this)) {
      Toast.makeText(this, "Camera permission is needed to run this application", Toast.LENGTH_LONG)
          .show();
      if (!CameraPermissionHelper.shouldShowRequestPermissionRationale(this)) {
        // Permission denied with checking "Do not ask again".
        CameraPermissionHelper.launchPermissionSettings(this);
      }
      finish();
    }
  }

  @Override
  public void onWindowFocusChanged(boolean hasFocus) {
    super.onWindowFocusChanged(hasFocus);
    FullScreenHelper.setFullScreenOnWindowFocusChanged(this, hasFocus);
  }

  @Override
  public void onSurfaceCreated(GL10 gl, EGLConfig config) {
    GLES20.glClearColor(0.1f, 0.1f, 0.1f, 1.0f);

    try {

      backgroundRenderer.createOnGlThread(/*context=*/ this);
      planeRenderer.createOnGlThread(/*context=*/ this, "models/trigrid.png");
      pointCloudRenderer.createOnGlThread(/*context=*/ this);
      System.out.println("\nDRAWING: " + this.curModel[0]);

      this.virtObjsToThread();

    } catch (IOException e) {
      Log.e(TAG, "Failed to read an asset file", e);
    }
    // Prepare the rendering objects. This involves reading shaders, so may throw an IOException.

  }

  // put each virtual color object on the thread
  public void virtObjsToThread() {

    try {

      for (int i = 0; i<this.paintColors.length; i++) {

        this.paintColors[i].createOnGlThread(/*context=*/ this, "models/square.obj",this.curModel[i]);
//        this.paintColors[i].createOnGlThread(/*context=*/ this, this.curModel[0][0], this.curModel[0][1]);
        this.paintColors[i].setMaterialProperties(0.0f, 2.0f, 0.5f, 6.0f);

      }
    } catch (IOException e) {
      Log.e(TAG, "Failed to read an asset file", e);
    }
  }

  @Override
  public void onSurfaceChanged(GL10 gl, int width, int height) {
    displayRotationHelper.onSurfaceChanged(width, height);
    GLES20.glViewport(0, 0, width, height);
  }

  @Override
  public void onDrawFrame(GL10 gl) {
    // Clear screen to notify driver it should not load any pixels from previous frame.
    GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT | GLES20.GL_DEPTH_BUFFER_BIT);

    if (session == null) {
      return;
    }
    // Notify ARCore session that the view size changed so that the perspective matrix and
    // the video background can be properly adjusted.
    displayRotationHelper.updateSessionIfNeeded(session);

    try {
      session.setCameraTextureName(backgroundRenderer.getTextureId());

      // Obtain the current frame from ARSession. When the configuration is set to
      // UpdateMode.BLOCKING (it is by default), this will throttle the rendering to the
      // camera framerate.
      Frame frame = session.update();
      Camera camera = frame.getCamera();

      // Handle taps. Handling only one tap per frame, as taps are usually low frequency
      // compared to frame rate.

      MotionEvent tap = tapHelper.poll();
      if (tap != null && camera.getTrackingState() == TrackingState.TRACKING) {
        for (HitResult hit : frame.hitTest(tap)) {
          // Check if any plane was hit, and if it was hit inside the plane polygon
          Trackable trackable = hit.getTrackable();
          System.out.println("Location getDist() : " + hit.getDistance() );
          System.out.println("Location getPos() : " + hit.getHitPose() );
          // Creates an anchor if a plane or an oriented point was hit.
          if ((trackable instanceof Plane && ((Plane) trackable).isPoseInPolygon(hit.getHitPose()))
              || (trackable instanceof Point
                  && ((Point) trackable).getOrientationMode()
                      == OrientationMode.ESTIMATED_SURFACE_NORMAL)) {
            // Hits are sorted by depth. Consider only closest hit on a plane or oriented point.
            // Cap the number of objects created. This avoids overloading both the
            // rendering system and ARCore.
            if (anchors.size() >= 200) {
              anchors.get(0).detach();
              anchors.remove(0);
            }
            // Adding an Anchor tells ARCore that it should track this position in
            // space. This anchor is created on the Plane to place the 3D model
            // in the correct position relative both to the world and to the plane.
            anchors.add(hit.createAnchor());
            break;
          }
        }
      }

      // Draw background.
      backgroundRenderer.draw(frame);

      // If not tracking, don't draw 3d objects.
      if (camera.getTrackingState() == TrackingState.PAUSED) {
        return;
      }

      // Get projection matrix.
      float[] projmtx = new float[16];
      camera.getProjectionMatrix(projmtx, 0, 0.1f, 100.0f);

      // Get camera matrix and draw.
      float[] viewmtx = new float[16];
      camera.getViewMatrix(viewmtx, 0);

      // Compute lighting from average intensity of the image.
      // The first three components are color scaling factors.
      // The last one is the average pixel intensity in gamma space.
      final float[] colorCorrectionRgba = new float[4];
      frame.getLightEstimate().getColorCorrection(colorCorrectionRgba, 0);

      // Visualize tracked points.
      PointCloud pointCloud = frame.acquirePointCloud();
      pointCloudRenderer.update(pointCloud);
      pointCloudRenderer.draw(viewmtx, projmtx);

      // Application is responsible for releasing the point cloud resources after
      // using it.
      pointCloud.release();

      // Check if we detected at least one plane. If so, hide the loading message.
      if (messageSnackbarHelper.isShowing()) {
        for (Plane plane : session.getAllTrackables(Plane.class)) {
          if (plane.getType() == com.google.ar.core.Plane.Type.HORIZONTAL_UPWARD_FACING
              && plane.getTrackingState() == TrackingState.TRACKING) {
            messageSnackbarHelper.hide(this);
            break;
          }
        }
      }

      // Visualize planes.
      planeRenderer.drawPlanes(
          session.getAllTrackables(Plane.class), camera.getDisplayOrientedPose(), projmtx);

      // Visualize anchors created by touch.
      float scaleFactor = 0.01f;
      for (Anchor anchor : anchors) {
        if (anchor.getTrackingState() != TrackingState.TRACKING) {
          continue;
        }
        // Get the current pose of an Anchor in world space. The Anchor pose is updated
        // during calls to session.update() as ARCore refines its estimate of the world.
        anchor.getPose().toMatrix(anchorMatrix, 0);

        // Update and draw the model and its shadow.
        this.updateModelMatrices(scaleFactor, projmtx, viewmtx, colorCorrectionRgba);

      }

    } catch (Throwable t) {
      // Avoid crashing the application due to unhandled exceptions.
      Log.e(TAG, "Exception on the OpenGL thread", t);
    }
  }


  // update and draw each model
  public void updateModelMatrices( float scaleFactor, float[] projmtx, float[] viewmtx, float[] colorCorrectionRgba) {

    try {

      // update the anchor points on each object renderer
      for (ObjectRenderer virtObj : this.paintColors) {
        virtObj.updateModelMatrix(anchorMatrix, scaleFactor);
      }

    } catch (Throwable t) {
      // Avoid crashing the application due to unhandled exceptions.
      Log.e(TAG, "Exception on the OpenGL thread", t);
    }

    // draw anchor points using user's currently selected color
    this.paintColors[this.curDrawingIdx].draw(viewmtx, projmtx, colorCorrectionRgba);

  }


  // update drawing index of this.paintColors
  // bound to user button tap
  public void drawPurple(android.view.View view) {
    this.curDrawingIdx = 0;
    resetSelectPaint(view);
  }

  // update drawing index of this.paintColors
  // bound to user button tap
  public void drawYellow(android.view.View view) {
    this.curDrawingIdx = 1;
    resetSelectPaint(view);
  }

  // update drawing index of this.paintColors
  // bound to user button tap
  public void drawCyan(android.view.View view) {
    this.curDrawingIdx = 2;
    resetSelectPaint(view);
  }

  // update drawing index of this.paintColors
  // bound to user button tap
  public void drawBlue(android.view.View view) {
    this.curDrawingIdx = 3;
    resetSelectPaint(view);
  }

  // update drawing index of this.paintColors
  // bound to user button tap
  public void drawGreen(android.view.View view) {
    this.curDrawingIdx = 4;
    resetSelectPaint(view);
  }

  // update drawing index of this.paintColors
  // bound to user button tap
  public void drawRed(android.view.View view) {
    this.curDrawingIdx = 5;
    resetSelectPaint(view);
  }

  // update drawing index of this.paintColors
  // bound to user button tap
  public void drawBlack(android.view.View view) {
    this.curDrawingIdx = 6;
    resetSelectPaint(view);
  }

  // delete last 3 points drawn on the screen
  public void undrawLastPoints(android.view.View view) {
    for (int i = 1; i < 4; i++) {
      try {
        this.anchors.remove(this.anchors.size() - i);
      } catch (IndexOutOfBoundsException e) {
          Log.e(TAG, "Index out of bounds: ", e);
      }
    }
  }

  // enter draw mode
  public void userStartPaint(android.view.View view) {

    //view.startAnimation(pulse);
    //System.out.println("Colleen lied\n");
    view.setVisibility(View.GONE);

    final ImageButton undoButton = (ImageButton) findViewById(R.id.undoButton);
    undoButton.setVisibility(View.VISIBLE);

    final ImageButton buttonPaintCan = (ImageButton) findViewById(R.id.buttonPaintCan);
    buttonPaintCan.setVisibility(View.VISIBLE);

    final Button uploadButton = (Button) findViewById(R.id.uploadButton);
    uploadButton.setVisibility(View.VISIBLE);

    final ImageButton backButton = (ImageButton) findViewById(R.id.buttonStopPaint);
    backButton.setVisibility(View.VISIBLE);

  }

    // Exit draw mode
    public void userStopPaint(android.view.View view) {
        //view.startAnimation(pulse);
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Confirm");
        builder.setMessage("Are you sure?");

        builder.setPositiveButton("Yes", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                //delete the points

                final ImageButton startPaint = (ImageButton) findViewById(R.id.buttonStartPaint);
                startPaint.setVisibility(View.VISIBLE);

                final ImageButton undoButton = (ImageButton) findViewById(R.id.undoButton);
                undoButton.setVisibility(View.GONE);

                final ImageButton buttonPaintCan = (ImageButton) findViewById(R.id.buttonPaintCan);
                buttonPaintCan.setVisibility(View.GONE);

                final Button uploadButton = (Button) findViewById(R.id.uploadButton);
                uploadButton.setVisibility(View.GONE);

                final ImageButton backButton = (ImageButton) findViewById(R.id.buttonStopPaint);
                backButton.setVisibility(View.GONE);

                while (anchors.size() != 0) {
                    anchors.get(0).detach();
                    anchors.remove(0);
                }

                dialog.dismiss();
            }
        });

        builder.setNegativeButton("No", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.dismiss();
            }
        });

        AlertDialog alert = builder.create();
        alert.show();
        //System.out.println("Colleen told the truth\n");

    }

  //Hide the selected paints
  public void resetSelectPaint(android.view.View view) {
    //view.startAnimation(pulse);
    final ImageButton redButton = (ImageButton) findViewById(R.id.buttonRed);
    redButton.setVisibility(View.GONE);

    final ImageButton blueButton = (ImageButton) findViewById(R.id.buttonBlue);
    blueButton.setVisibility(View.GONE);

    final ImageButton blackButton = (ImageButton) findViewById(R.id.buttonBlack);
    blackButton.setVisibility(View.GONE);

    final ImageButton yellowButton = (ImageButton) findViewById(R.id.buttonYellow);
    yellowButton.setVisibility(View.GONE);

    final ImageButton cyanButton = (ImageButton) findViewById(R.id.buttonCyan);
    cyanButton.setVisibility(View.GONE);

    final ImageButton purpleButton = (ImageButton) findViewById(R.id.buttonPurple);
    purpleButton.setVisibility(View.GONE);

    final ImageButton greenButton = (ImageButton) findViewById(R.id.buttonGreen);
    greenButton.setVisibility(View.GONE);
  }

  // makes color buttons visible
  public void userSelectPaint(android.view.View view) {

    //view.startAnimation(pulse);
    final ImageButton redButton = (ImageButton) findViewById(R.id.buttonRed);
    redButton.setVisibility(View.VISIBLE);

    final ImageButton blueButton = (ImageButton) findViewById(R.id.buttonBlue);
    blueButton.setVisibility(View.VISIBLE);

    final ImageButton blackButton = (ImageButton) findViewById(R.id.buttonBlack);
    blackButton.setVisibility(View.VISIBLE);

    final ImageButton yellowButton = (ImageButton) findViewById(R.id.buttonYellow);
    yellowButton.setVisibility(View.VISIBLE);

    final ImageButton cyanButton = (ImageButton) findViewById(R.id.buttonCyan);
    cyanButton.setVisibility(View.VISIBLE);

    final ImageButton purpleButton = (ImageButton) findViewById(R.id.buttonPurple);
    purpleButton.setVisibility(View.VISIBLE);

    final ImageButton greenButton = (ImageButton) findViewById(R.id.buttonGreen);
    greenButton.setVisibility(View.VISIBLE);


  }


}