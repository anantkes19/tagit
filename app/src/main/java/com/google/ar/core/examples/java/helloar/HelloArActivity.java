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

import android.Manifest;
import android.content.DialogInterface;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationManager;
import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Vibrator;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.media.MediaPlayer;
import android.view.HapticFeedbackConstants;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.Toast;
import android.content.Context;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.StringRequest;
import com.android.volley.toolbox.Volley;
import com.google.ar.core.Anchor;
import com.google.ar.core.ArCoreApk;
import com.google.ar.core.Camera;
import com.google.ar.core.Frame;
import com.google.ar.core.HitResult;
import com.google.ar.core.Plane;
import com.google.ar.core.Point;
import com.google.ar.core.Point.OrientationMode;
import com.google.ar.core.PointCloud;
import com.google.ar.core.Pose;
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
import com.google.ar.core.examples.java.common.rendering.PlaneRenderer;
import com.google.ar.core.examples.java.common.rendering.PointCloudRenderer;
import com.google.ar.core.exceptions.CameraNotAvailableException;
import com.google.ar.core.exceptions.UnavailableApkTooOldException;
import com.google.ar.core.exceptions.UnavailableArcoreNotInstalledException;
import com.google.ar.core.exceptions.UnavailableDeviceNotCompatibleException;
import com.google.ar.core.exceptions.UnavailableSdkTooOldException;
import com.google.ar.core.exceptions.UnavailableUserDeclinedInstallationException;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedOutputStream;
import java.io.BufferedWriter;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.HashMap;

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

  private Map<Anchor,Integer> ancholors = new HashMap<>();

  private String[] curModel = {"models/purpleSquare.png", "models/yellowSquare.png", "models/cyanSquare.png",
                            "models/blueSquare.png", "models/greenSquare.png", "models/redSquare.png", "models/blackSquare.png" };

  private int curDrawingIdx = 1;
  private boolean drawing = false;
  private ArrayList<Integer> curColorsDrawn = new ArrayList<>();

  private Double longitude = 0.0;
  private Double latitude = 0.0;
  //Animations for style
  //Animation pulse;
  //final Animation fadein = AnimationUtils.loadAnimation(this,R.anim.fadein);
  //final Animation fadeout = AnimationUtils.loadAnimation(this,R.anim.fadeout);
  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_main);
    surfaceView = (GLSurfaceView) findViewById(R.id.surfaceview);
    displayRotationHelper = new DisplayRotationHelper(/*context=*/ this);


   // pulse = AnimationUtils.loadAnimation(this,R.anim.pulse);


    LocationManager lm = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
    if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
      System.out.println("Permission Granted");
      Location location = lm.getLastKnownLocation(LocationManager.GPS_PROVIDER);
      latitude = location.getLatitude();
      longitude = location.getLongitude();

    } else {
      longitude = 0.0;
      latitude = 0.0;
    }

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

          // haptic feedback

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
            Anchor newAnchor = hit.createAnchor();
            ancholors.put(newAnchor, 7);  // default entry of 7, 1 greater than
            anchors.add(newAnchor);

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

            // haptic feedback when a new plane is found
            // Get instance of Vibrator from current Context
//            Vibrator v = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
            // Vibrate for 400 milliseconds
//            v.vibrate(400);

            messageSnackbarHelper.hide(this);
            break;
          }
        }
      }

      // Visualize planes.
      planeRenderer.drawPlanes(
          session.getAllTrackables(Plane.class), camera.getDisplayOrientedPose(), projmtx);

      // clear cur colors drawn
//      curColorsDrawn.clear();

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
        this.updateModelMatrices(anchor, scaleFactor, projmtx, viewmtx, colorCorrectionRgba);

      }

//      if (drawing) {
//        draw(scaleFactor, projmtx, viewmtx, colorCorrectionRgba);
//      }

    } catch (Throwable t) {
      // Avoid crashing the application due to unhandled exceptions.
      Log.e(TAG, "Exception on the OpenGL thread", t);
    }
  }


  // update and draw each model
  public void updateModelMatrices(Anchor a, float scaleFactor, float[] projmtx, float[] viewmtx, float[] colorCorrectionRgba) {

    try {

      if (ancholors.get(a) > 6) {  // if the current anchor has not already been drawn with a color
        ancholors.put(a, curDrawingIdx);  // give it the color of the currently drawn object

      }

      int colorIdx = ancholors.get(a);
      curColorsDrawn.add(curDrawingIdx);

      paintColors[colorIdx].updateModelMatrix(anchorMatrix, scaleFactor);

//       update the anchor points on each object renderer
//      for (ObjectRenderer virtObj : this.paintColors) {
//        virtObj.updateModelMatrix(anchorMatrix, scaleFactor);
//      }

      // loop through anchors
        // get virt obj from idx (value of k-v anchor pair) and call update model matrix on the current anchor

    } catch (Throwable t) {
      // Avoid crashing the application due to unhandled exceptions.
      Log.e(TAG, "Exception on the OpenGL thread", t);
    }

    // draw anchor points using user's currently selected color
    if (drawing) {
//
//      for(int i = 0; i < curColorsDrawn.size(); i++) {
//        paintColors[curColorsDrawn.get(i)].draw(viewmtx, projmtx, colorCorrectionRgba);
//      }
//
      for (ObjectRenderer virtObj : this.paintColors) {
        virtObj.draw(viewmtx, projmtx, colorCorrectionRgba);
      }
//        this.paintColors[this.curDrawingIdx].draw(viewmtx, projmtx, colorCorrectionRgba);
    }
  }



  // update drawing index of this.paintColors
  // bound to user button tap
  public void drawPurple(android.view.View view) {

    this.curDrawingIdx = 0;
    setupPurple(view);
  }

  // helper func
  public void setupPurple(android.view.View view) {

    resetSelectPaint(view);
    playCanSound();

    // change spray can image to current color
    ImageButton btn = (ImageButton)findViewById(R.id.buttonPaintCan);
    btn.setBackgroundResource(R.drawable.spray_purple);
  }


  // update drawing index of this.paintColors
  // bound to user button tap
  public void drawYellow(android.view.View view) {

    this.curDrawingIdx = 1;
    setupYellow(view);
  }

  // helper func
  public void setupYellow(android.view.View view) {

    resetSelectPaint(view);
    playCanSound();

    // change spray can image to current color
    ImageButton btn = (ImageButton)findViewById(R.id.buttonPaintCan);
    btn.setBackgroundResource(R.drawable.spray_yellow);
  }


  // update drawing index of this.paintColors
  // bound to user button tap
  public void drawCyan(android.view.View view) {
    this.curDrawingIdx = 2;
    setupCyan(view);
  }

  // helper func
  public void setupCyan(android.view.View view) {

    resetSelectPaint(view);
    playCanSound();

    // change spray can image to current color
    ImageButton btn = (ImageButton)findViewById(R.id.buttonPaintCan);
    btn.setBackgroundResource(R.drawable.spray_cyan);
  }


  // update drawing index of this.paintColors
  // bound to user button tap
  public void drawBlue(android.view.View view) {
    this.curDrawingIdx = 3;
    setupBlue(view);
  }

  // helper func
  public void setupBlue(android.view.View view) {

    resetSelectPaint(view);
    playCanSound();

    // change spray can image to current color
    ImageButton btn = (ImageButton)findViewById(R.id.buttonPaintCan);
    btn.setBackgroundResource(R.drawable.spray_blue);
  }


  // update drawing index of this.paintColors
  // bound to user button tap
  public void drawGreen(android.view.View view) {
    this.curDrawingIdx = 4;
    setupGreen(view);
  }

  // helper func
  public void setupGreen(android.view.View view) {

    resetSelectPaint(view);
    playCanSound();

    // change spray can image to current color
    ImageButton btn = (ImageButton)findViewById(R.id.buttonPaintCan);
    btn.setBackgroundResource(R.drawable.spray_green);
  }


  // update drawing index of this.paintColors
  // bound to user button tap
  public void drawRed(android.view.View view) {

    this.curDrawingIdx = 5;
    setupRed(view);
  }

  // helper func
  public void setupRed(android.view.View view) {

    resetSelectPaint(view);
    playCanSound();

    // change spray can image to current color
    ImageButton btn = (ImageButton)findViewById(R.id.buttonPaintCan);
    btn.setBackgroundResource(R.drawable.spray_red);

  }


  // update drawing index of this.paintColors
  // bound to user button tap
  public void drawBlack(android.view.View view) {

    this.curDrawingIdx = 6;
    setupBlack(view);
  }

  // helper func
  public void setupBlack(android.view.View view) {

    resetSelectPaint(view);
    playCanSound();

    // change spray can image to current color
    ImageButton btn = (ImageButton)findViewById(R.id.buttonPaintCan);
    btn.setBackgroundResource(R.drawable.spray_black);
  }


  // can shaking sound played when a new "can" (color) is selected
  public void playCanSound() {
    MediaPlayer mp = MediaPlayer.create(this, R.raw.shake_can);
    mp.start();
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

  //hamburger
  public void helpDialog(android.view.View view) {
    AlertDialog.Builder builder = new AlertDialog.Builder(this);
    builder.setTitle("How to Use");
    if (drawing) {
      builder.setMessage("After a surface is found, start drawing! To change colors, press "+
      "the spray can in the bottom right corner. When you are done, press 'Upload Graffiti.' "+
      "To cancel drawing, press the X in the upper right corner, and to undo, press the arrow in the bottom left.");
    }
    else {
      builder.setMessage("To start drawing, press the spray can in the upper right corner " +
              "and align with a flat surface until a grid appears.");
    }
    builder.setPositiveButton("OK", null);
    AlertDialog helpDialog = builder.create();
    helpDialog.show();
  }

  // enter draw mode
  public void userStartPaint(ImageButton view) {

    //view.startAnimation(pulse);
    //System.out.println("Colleen lied\n");
    drawing = true;
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

    // Exit draw mode w/ are you sure button
    public void userStopPaint(android.view.View view) {
        //view.startAnimation(pulse);

      //build dialog builder, set the title and message
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Confirm");
        builder.setMessage("Are you sure?");

        //sets the yes button

    //System.out.println("Colleen told the truth\n");



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

                resetSelectPaint(buttonPaintCan);
                drawing = false;    // prevent points from being drawn

                while (anchors.size() != 0) {
                  anchors.get(0).detach();
                  anchors.remove(0);
                }
                dialog.dismiss();
            }
        });
        //sets the no button
        builder.setNegativeButton("No", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.dismiss();
            }
        });
        //builds the dialog
        AlertDialog alert = builder.create();
        alert.show();
        //System.out.println("Colleen told the truth\n");
    }


  // makes color buttons visible
  public void userSelectPaint(android.view.View view) {

    //view.startAnimation(pulse);

//    drawing = true; // allow points to be drawn

    final ImageButton redButton = (ImageButton) findViewById(R.id.buttonRed);
    redButton.setVisibility(View.VISIBLE);
//
    final ImageButton blueButton = (ImageButton) findViewById(R.id.buttonBlue);
    blueButton.setVisibility(View.VISIBLE);
//
    final ImageButton blackButton = (ImageButton) findViewById(R.id.buttonBlack);
    blackButton.setVisibility(View.VISIBLE);
//
    final ImageButton yellowButton = (ImageButton) findViewById(R.id.buttonYellow);
    yellowButton.setVisibility(View.VISIBLE);
//
    final ImageButton cyanButton = (ImageButton) findViewById(R.id.buttonCyan);
    cyanButton.setVisibility(View.VISIBLE);
//
    final ImageButton purpleButton = (ImageButton) findViewById(R.id.buttonPurple);
    purpleButton.setVisibility(View.VISIBLE);
//
    final ImageButton greenButton = (ImageButton) findViewById(R.id.buttonGreen);
    greenButton.setVisibility(View.VISIBLE);

  }

  //Hide the selected paints
  public void resetSelectPaint(android.view.View view) {
    //view.startAnimation(pulse);

    final ImageButton redButton = (ImageButton) findViewById(R.id.buttonRed);
    redButton.setVisibility(View.GONE);
//
    final ImageButton blueButton = (ImageButton) findViewById(R.id.buttonBlue);
    blueButton.setVisibility(View.GONE);
//
    final ImageButton blackButton = (ImageButton) findViewById(R.id.buttonBlack);
    blackButton.setVisibility(View.GONE);
//
    final ImageButton yellowButton = (ImageButton) findViewById(R.id.buttonYellow);
    yellowButton.setVisibility(View.GONE);
//
    final ImageButton cyanButton = (ImageButton) findViewById(R.id.buttonCyan);
    cyanButton.setVisibility(View.GONE);
//
    final ImageButton purpleButton = (ImageButton) findViewById(R.id.buttonPurple);
    purpleButton.setVisibility(View.GONE);
//
    final ImageButton greenButton = (ImageButton) findViewById(R.id.buttonGreen);
    greenButton.setVisibility(View.GONE);
  }
/*
  public JSONObject getJSONObject(Pose pose) {
    JSONObject newJSONObject
    return newJSONObject;
  }*/

  public void getNearbyGraffiti(android.view.View view) {



    RequestQueue queue = Volley.newRequestQueue(this);
    String url ="http://137.146.121.108:8080/_tagit/getGraffiti";

// Request a string response from the provided URL.
    System.out.println("VOLLEY REQUESTS");
    StringRequest stringRequest = new StringRequest(Request.Method.GET, url,
            new Response.Listener<String>() {
              @Override
              public void onResponse(String response) {
                // Display the first 500 characters of the response string.
                System.out.println("RESPONSE IS: "+response);
              }
            }, new Response.ErrorListener() {
      @Override
      public void onErrorResponse(VolleyError error) {
        System.out.println(error);
      }
    });

// Add the request to the RequestQueue.
    queue.add(stringRequest);

    JSONArray anchorJSON = new JSONArray();
    if(!drawing) {
      //Get json string of anchors

      //Read in as json

      //Break into anchors using a trackable, notify user it's ready to draw?
      //Toast message

      //Add to anchors list, or redraw over it*

      //
    }

    //Set anchor (and anchor matrices?) to new data
  }

  public void uploadDrawing(android.view.View view) {
    System.out.println("Uploading Drawing to server!");

    CallAPI dataSender = new CallAPI();
    JSONArray anchorJSON = new JSONArray();

    //Reduce points
    System.out.println("Original: " + anchors.size());
    List<Anchor> reducedAnchors = rdpReduce(anchors, 0.1);
    System.out.println("Reduced: " + reducedAnchors.size());
    anchorJSON.put(latitude);
    anchorJSON.put(longitude);
    //Convert to json
    for (Anchor singleObject : reducedAnchors) {
      anchorJSON.put(singleObject.getPose());
    }

    //reducedAnchors.forEach(singleObject -> anchorJSON.put(singleObject.getPose())); RIP Android studio old java

    String jsonString = anchorJSON.toString();
    //Send points
    dataSender.execute("http://137.146.121.108:8080/_tagit/sendGraffiti",jsonString);
            //.doInBackground("https://hackcolbyclan.appspot.com/_tagit/sendGraffiti",jsonString);
  }

  public class CallAPI extends AsyncTask<String, String, String> {

    public CallAPI(){
      //set context variables if required
    }

    @Override
    protected void onPreExecute() {
      super.onPreExecute();
    }


    @Override
    protected String doInBackground(String... params) {

      String urlString = params[0]; // URL to call

      String data = params[1]; //data to post

      OutputStream out = null;
      try {

        URL url = new URL(urlString);
        System.out.println("Trying to connect!");

        HttpURLConnection urlConnection = (HttpURLConnection) url.openConnection();
        urlConnection.setRequestMethod("POST");
        urlConnection.setDoOutput(true);
        urlConnection.setRequestProperty("Accept","application/json");
        urlConnection.setDoInput(true);

        DataOutputStream os = new DataOutputStream(urlConnection.getOutputStream());

        os.writeBytes(data);

        os.flush();
        os.close();

        Log.i("STATUS", String.valueOf(urlConnection.getResponseCode()));
        Log.i("MSG" , urlConnection.getResponseMessage());

        urlConnection.disconnect();

        System.out.println("WE CONNECTED");


      } catch (Exception e) {
        System.out.println("Error occured in Sending Data");
        System.out.println(e.getMessage());
        e.printStackTrace();
        System.out.println(e.getClass());


      }



      return urlString;
    }


  }

  public static double distancePtToLine(Anchor p0, Anchor p1, Anchor p2) {
    double x0 = p0.getPose().tx();
    double y0 = p0.getPose().ty();
    double z0 = p0.getPose().tz();

    double x1 = p1.getPose().tx();
    double y1 = p1.getPose().ty();
    double z1 = p1.getPose().tz();

    double x2 = p2.getPose().tx();
    double y2 = p2.getPose().ty();
    double z2 = p2.getPose().tz();


    double xc = (y0 - y1)*(z0 - z2) - (z0 - z1)*(y0 - y2);
    double yc = (z0 - z1)*(x0 - x2) - (x0 - x1)*(z0 - z2);
    double zc = (x0 - x1)*(y0 - y2) - (y0 - y1)*(x0 - x2);
    double numer = Math.sqrt(xc*xc + yc*yc + zc*zc);

    double denom = Math.sqrt((x2-x1)*(x2-x1) + (y2-y1)*(y2-y1) + (z2-z1)*(z2-z1));

    return numer/denom;
}

  public static List<Anchor> rdpReduce(List<Anchor> points, double epsilon) {
    double dmax = 0;
    int indexMax = 0;
    int end = points.size();
    for (int i=1; i<end-1; i++){
      double d = distancePtToLine(points.get(i), points.get(0), points.get(end-1));
      if (d > dmax){
        indexMax = i;
        dmax = d;
      }
    }

    List<Anchor> res = new ArrayList<>();
    if (dmax > epsilon) {
      if (points.size() < 2) return res;
      res.addAll(rdpReduce(points.subList(1, indexMax), epsilon));
      res.addAll(rdpReduce(points.subList(indexMax, end), epsilon));
    } else {
      if (points.size() < 2) return res;
      res.add(points.get(0));
      res.add(points.get(end-1));
    }

    return res;
  }


}