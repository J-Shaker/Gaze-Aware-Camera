/*
 * ICSI 499: Capstone Project in Computer Science
 * Real Time Gaze Aware Mobile Application
 * Team 7:
 * Mathew Bilodeau (001396193)
 * John Shaker (001301965)
 * Brayden Lappies (001317811)
 * Julian Oravetz (001329582)
 * Sponsors: Dr. Pradeep Atrey and Omkar Kulkarni, Albany Lab for Privacy and Security
 */

package com.example.gazeawarecamera;

import static android.content.ContentValues.TAG;

import android.Manifest;
import android.app.Activity;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.media.Image;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.provider.MediaStore;
import android.util.Log;
import android.util.Size;
import android.view.MenuItem;
import android.view.View;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.PopupMenu;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.OptIn;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageCapture;
import androidx.camera.core.ImageCaptureException;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.face.Face;
import com.google.mlkit.vision.face.FaceDetection;
import com.google.mlkit.vision.face.FaceDetector;
import com.google.mlkit.vision.face.FaceDetectorOptions;

import org.opencv.android.OpenCVLoader;
import org.opencv.objdetect.CascadeClassifier;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;


public class MainActivity extends AppCompatActivity implements DrawingListener {

    public static final int PIXEL_COUNT_HORIZONTAL = 1920;
    public static final int PIXEL_COUNT_VERTICAL = 1080;

    public static int desiredNumberOfSubjects = 1;

    private final String MESSAGE_DESIRED_SUBJECTS_CHANGED = "The desired number of subjects has been changed to ";
    private final String MESSAGE_PHOTO_SAVED = "Photo saved!";
    private final String MESSAGE_PHOTO_SAVE_FAILED = "Failed to save photo.";

    private ImageView imageView;
    private PreviewView previewView;
    private TextView faceCounter;
    private TextView gazeCounter;
    private ImageButton albumButton;
    private ImageButton captureButton;
    private ImageButton selectionButton;

    private CameraSelector cameraSelector;
    private ListenableFuture<ProcessCameraProvider> cameraProviderFuture;
    private ProcessCameraProvider cameraProvider;
    private Executor cameraExecutor;
    private ImageCapture imageCapture;

    public static CascadeClassifier eyeCascade;

    private final GazeDetector gazeDetector = new GazeDetector(this);

    private static int getScreenWidth() {
        return Resources.getSystem().getDisplayMetrics().widthPixels;
    }

    private static int getScreenHeight() {
        return Resources.getSystem().getDisplayMetrics().heightPixels;
    }

    @Override
    public void drawRectangles(ArrayList<Rect> rectangles) {
        Bitmap bitmap = Bitmap.createBitmap(getScreenWidth(), getScreenHeight(), Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        Paint paint = new Paint();
        paint.setStyle(Paint.Style.STROKE);
        paint.setColor(Color.YELLOW);
        paint.setStrokeWidth(8);
        paint.setAntiAlias(true);
        for (int i = 0; i < rectangles.size(); i++) {
            canvas.drawRect(rectangles.get(i), paint);
        }
        imageView.setImageBitmap(bitmap);
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        imageView = findViewById(R.id.image_view);
        previewView = findViewById(R.id.preview_view);
        faceCounter = findViewById(R.id.face_counter);
        gazeCounter = findViewById(R.id.gaze_counter);
        albumButton = findViewById(R.id.album_button);
        captureButton = findViewById(R.id.capture_button);
        selectionButton = findViewById(R.id.selector_menu);
        cameraSelector = new CameraSelector.Builder().requireLensFacing(CameraSelector.LENS_FACING_FRONT).build();
        getPermissionToUseCamera();
        startCamera();
        setOnClickListeners();
        OpenCVLoader.initDebug();
        openResourceFile();
    }

    private void getPermissionToUseCamera() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, 1);
        }
    }

    /*
     * The startCamera method completes the initialization and configuration of the camera. Most of
     * the code in startCamera, bindUseCases, and the derivative binding methods were developed
     * following the official Android Developers Docs found here: https://developer.android.com/training/camerax.
     * Additionally, the imageAnalysis binding uses Google ML Kit for face and facial landmark
     * detection. That code was implemented with the help of ML Kit Docs found here:
     * https://developers.google.com/ml-kit/reference/android. There are comments explaining what
     * each line or lines of code do.
     */
    public void startCamera() {
        cameraProviderFuture = ProcessCameraProvider.getInstance(this);
        cameraProviderFuture.addListener(() -> {
            try {
                cameraProvider = cameraProviderFuture.get();
                bindUseCases();
            } catch (ExecutionException | InterruptedException e) {
                // No errors need to be handled for this Future.
                // This should never be reached.
            }
        }, ContextCompat.getMainExecutor(this));
    }

    private void bindUseCases() {
        /*
         * CameraX requires that previous bindings are unbound before attempting to create any
         * new ones. These bindings can persist across applications, so unbindAll must always be
         * called. After that we are free to call our own bindings which are initialized and
         * completed in their own methods.
         */
        cameraProvider.unbindAll();
        bindAnalysisUseCase();
        bindCaptureUseCase();
        bindPreviewUseCase();
    }

    private void bindPreviewUseCase() {
        /*
         * This method implements the preview use case of CameraX which provides the camera feed to
         * our layout (GUI). In order for the GUI to function properly, we need to set the surface
         * provider. We are letting Android set the surface provider and using that.
         */
        Preview preview = new Preview.Builder().build();
        preview.setSurfaceProvider(previewView.getSurfaceProvider());
        /*
         * With preview initialized, it can be bound to the hardware (cameraProvider) as follows:
         */
        cameraProvider.bindToLifecycle(this, cameraSelector, preview);
    }

    private void bindCaptureUseCase() {
        /*
         * This method implements the ImageCapture use case of CameraX and binds it to the
         * cameraProvider (hardware device) so that may save individual frames/images as photos.
         */
        cameraExecutor = Executors.newSingleThreadExecutor();
        imageCapture = new ImageCapture.Builder()
                .setIoExecutor(cameraExecutor)
                .build();
        /*
         * With imageCapture initialized, it can be bound to the hardware (cameraProvider) as
         * follows:
         */
        cameraProvider.bindToLifecycle(this, cameraSelector, imageCapture);
    }

    private void bindAnalysisUseCase() {
        /*
         * This method implements the ImageAnalysis use case of CameraX which allows us to analyze
         * each frame the camera produces in real time. We are currently capturing frames at 720p,
         * though we may wish to choose a lower resolution in the future to enhance processing
         * speed. We also set the ImageAnalysis object to analyze only the latest frame as we only
         * care about what is happening at the singular, latest instance.
         */
        ImageAnalysis imageAnalysis = new ImageAnalysis.Builder()
                .setTargetResolution(new Size(PIXEL_COUNT_HORIZONTAL, PIXEL_COUNT_VERTICAL))
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build();
        /*
         * We are implementing a custom analyzer for our ImageAnalysis object. While it would have
         * been ideal to create a separate class which contains the analyze function, Android
         * context items (such as TextViews) cannot be made static and therefore cannot be accessed
         * from outside the class.
         */
        imageAnalysis.setAnalyzer(ContextCompat.getMainExecutor(this), imageProxy -> {
            /*
             * The ImageProxy object given as an argument to analyze is the image the camera is
             * currently looking at as an object in memory. The first thing we need to do is
             * convert this ImageProxy into an Image which the ML Kit FaceDetector can use as
             * input. This conversion method is considered experimental at this time and
             * requires an OptIn flag.
             */
            @OptIn(markerClass = androidx.camera.core.ExperimentalGetImage.class)
            Image mediaImage = imageProxy.getImage();
            /*
             * Before doing anything else, we need to make sure that mediaImage is not null.
             */
            if (mediaImage != null) {
                /*
                 * Now we can use the Image object to create an InputImage for FaceDetector. An
                 * InputImage can use various image objects, but we are using the Image object
                 * we got from the camera. When using an Image object, it is required to also
                 * give an integer representing the rotation of the camera. We can get this
                 * integer from the ImageProxy object in memory.
                 */
                InputImage image = InputImage.fromMediaImage(mediaImage, imageProxy.getImageInfo().getRotationDegrees());
                /*
                 * Now we will set the options for FaceDetector. The following options are given
                 * in the Android Developer Docs and optimize FaceDetector for accuracy. These
                 * options can be changed to optimize for performance if need be.
                 */
                FaceDetectorOptions options = new FaceDetectorOptions.Builder()
                        .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
                        .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)
                        .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL)
                        .build();
                /*
                 * We're ready to instantiate the FaceDetector.
                 */
                FaceDetector faceDetector = FaceDetection.getClient(options);
                /*
                 * The process method of FaceDetector returns a Task and a List of faces called
                 * Face. Task allows us to add the OnSuccessListener, OnFailureListener, and
                 * OnCompleteListener. It is important to note that the FaceDetector has not
                 * failed if no faces are detected. Failure only refers to encountering an
                 * error. The List faces can be used to conduct further analysis - particularly,
                 * gaze detection.
                 */
                Task<List<Face>> result = faceDetector.process(image).addOnSuccessListener(new OnSuccessListener<List<Face>>() {
                    @Override
                    public void onSuccess(List<Face> faces) {
                        /*
                         * We let numberOfFacesDetected be equal to faces.size(), as
                         * this is the list containing all detected faces and therefore
                         * it's size reveals the amount. Then we assume that there are
                         * 0 faces looking toward the camera.
                         */
                        int numberOfFacesDetected = faces.size();
                        int numberOfGazesDetected = 0;
                        /*
                         * We only want to take action if faces.size() returns an
                         * integer greater than or equal to the desired number of
                         * subjects (faces). We only allow the user to select a maximum
                         * desired amount of four, though the application will attempt
                         * to perform gaze detection for any number of subjects detected
                         * by FaceDetector.
                         */
                        if (numberOfFacesDetected >= desiredNumberOfSubjects) {
                            /*
                             * Now, we call the detectGazes method of GazeDetector to
                             * determine the number of faces which are looking toward the
                             * camera and update the value of
                             * numberOfFacesLookingTowardCamera.
                             */
                            numberOfGazesDetected = gazeDetector.detectGazesWithDistances(faces, mediaImage);
                            /*
                             * We now verify if number of subjects looking toward the
                             * camera is equivalent to the number of faces detected by
                             * FaceDetector.
                             */
                            if (numberOfFacesDetected == numberOfGazesDetected) {
                                capturePhoto();
                            }
                        }
                        /*
                         * Finally, we update our two UI TextViews to reflect changes
                         * in the number of faces detected and the number of gazes
                         * detected.
                         */
                        updateFaceCounter(numberOfFacesDetected);
                        updateGazeCounter(numberOfGazesDetected);
                    }
                })
                        .addOnFailureListener(new OnFailureListener() {
                            @Override
                            public void onFailure(@NonNull Exception e) {
                                /*
                                 * We are not equipped to handle any errors FaceDetector
                                 * encounters, but we can update the UI to let the user know
                                 * that their face is definitely undetected.
                                 */
                                updateFaceCounter(0);
                                updateGazeCounter(0);
                            }
                        })
                        .addOnCompleteListener(new OnCompleteListener<List<Face>>() {
                            @Override
                            public void onComplete(@NonNull Task<List<Face>> task) {
                                /*
                                 * The ImageProxy in memory must be closed because we have
                                 * configured the camera to keep only the latest frame. If we
                                 * failed to close the ImageProxy, we would not be able to
                                 * analyze any more frames past the one which was not closed
                                 * (which would always be the first in this case). Note that the
                                 * onComplete method will run regardless of whether
                                 * faceDetector.process succeeds or fails.
                                 */
                                imageProxy.close();
                            }
                        });
            }
        });
        /*
         * With imageAnalysis initialized, it can be bound to the hardware (cameraProvider) as
         * follows:
         */
        cameraProvider.bindToLifecycle(this, cameraSelector, imageAnalysis);
    }

    /*
     * The next set of methods are responsible for functionality pertaining to the UI. This includes
     * updating the text on screen to adapt to changes in variables as well as setting the behavior
     * of buttons when clicked by the user.
     */
    private void updateFaceCounter(int numberOfFaces) {
        /*
         * This method updates the faceCounterTextView widget, which tells the user how many faces
         * have been detected by ML Kit.
         */
        faceCounter.setText(getString(R.string.face_counter, numberOfFaces));
    }

    private void updateGazeCounter(int numberOfGazes) {
        /*
         * This method updates the gazeCounterTextView widget, which tells the user how many faces
         * have been detected by ML Kit.
         */
        gazeCounter.setText(getString(R.string.gaze_counter, numberOfGazes));
    }

    /*
     * setOnClickListener is an object that allows us to tell the application what to do when a UI
     * element is pressed. We have three buttons and are setting each of their onClickListeners in
     * this single method.
     */
    private void setOnClickListeners() {
        /*
         * The album button opens up the user's photo gallery. This function is implemented in
         * openAlbum. We set the onClick method to call openAlbum().
         */
        albumButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                openAlbum();
            }
        });
        /*
         * The capture button saves the current image that the camera is looking at. This function
         * is implemented in capturePhoto. We set the onClick method to call capturePhoto.
         */
        captureButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                capturePhoto();
            }
        });
        /*
         * The selection button, for lack of a better name, brings up the pop up menu which allows
         * the user to select the number of people they want to be in the photo. This function is
         * implemented in openSelection. We set the onClick method to call openSelection.
         */
        selectionButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                openSelection();
            }
        });

    }

    private void openAlbum() {
        Intent intent = new Intent(Intent.ACTION_VIEW, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
        startActivity(intent);
    }

    private void capturePhoto() {

        ContentValues contentValues = new ContentValues();
        contentValues.put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg");

        ImageCapture.OutputFileOptions outputFileOptions = new ImageCapture.OutputFileOptions.Builder(
                getContentResolver(),
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
                .build();

        imageCapture.takePicture(outputFileOptions, cameraExecutor, new ImageCapture.OnImageSavedCallback() {
            @Override
            public void onImageSaved(@NonNull ImageCapture.OutputFileResults outputFileResults) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        Toast.makeText(MainActivity.this, MESSAGE_PHOTO_SAVED, Toast.LENGTH_SHORT).show();
                    }
                });
            }

            @Override
            public void onError(@NonNull ImageCaptureException error) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        Toast.makeText(MainActivity.this, MESSAGE_PHOTO_SAVE_FAILED, Toast.LENGTH_SHORT).show();
                    }
                });
            }
        });
    }

    public void openSelection() {
        PopupMenu selectionMenu = new PopupMenu(MainActivity.this, selectionButton);
        selectionMenu.getMenuInflater().inflate(R.menu.popup_menu, selectionMenu.getMenu());
        selectionMenu.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener() {
            public boolean onMenuItemClick(MenuItem item) {
                desiredNumberOfSubjects = Integer.parseInt(item.getTitle().toString());
                Toast.makeText(MainActivity.this, MESSAGE_DESIRED_SUBJECTS_CHANGED + item.getTitle() + ".", Toast.LENGTH_SHORT).show();
                return true;
            }
        });
        selectionMenu.show();
    }

    private void openResourceFile() {
        try {
            InputStream inputStream = getResources().openRawResource(R.raw.haarcascade_eye);
            File cascadeDirectory = getDir("cascades", Context.MODE_PRIVATE);
            File cascadeFile = new File(cascadeDirectory, "haarcascade_eye.xml");
            FileOutputStream outputStream = new FileOutputStream(cascadeFile);

            byte[] buffer = new byte[4096];
            int bytesRead;
            while ((bytesRead = inputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, bytesRead);
            }
            inputStream.close();
            outputStream.close();

            eyeCascade = new CascadeClassifier(cascadeFile.getAbsolutePath());

            eyeCascade.load(cascadeFile.getAbsolutePath());
            if (eyeCascade.empty()) {
                Log.e(TAG, "Failed to load cascade classifier");
                eyeCascade = null;
            } else
                Log.i(TAG, "Loaded cascade classifier from " + cascadeFile.getAbsolutePath());

            cascadeDirectory.delete();

        } catch (IOException e) {
            e.printStackTrace();
            Log.e(TAG, "Failed to load cascade. Exception thrown: " + e);
        }

    }

}