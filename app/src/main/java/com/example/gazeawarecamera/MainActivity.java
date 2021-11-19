package com.example.gazeawarecamera;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.Image;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Size;
import android.view.MenuItem;
import android.view.View;
import android.widget.ImageButton;
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
import androidx.camera.core.ImageProxy;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.LifecycleOwner;

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
import org.opencv.core.Mat;
import org.opencv.core.Point;

import java.io.File;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;


public class MainActivity extends AppCompatActivity {

    public static final String FILE_SAVE_DIRECTORY = "Gaze Aware Camera Photos";
    public static final String FILE_NAME_FORMAT = "IMG ";
    public static final String FILE_TYPE = ".jpg";

    private final String MESSAGE_DESIRED_SUBJECTS_CHANGED = "The desired number of subjects has been changed to ";
    private final String MESSAGE_PHOTO_SAVED = "Photo saved!";

    private PreviewView previewView;
    private ListenableFuture<ProcessCameraProvider> cameraProviderFuture;

    private int desiredNumberOfSubjects = 1;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        OpenCVLoader.initDebug();
        setContentView(R.layout.activity_main);
        startCamera();
    }

    private void getPermissionToUseCamera() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[] {Manifest.permission.CAMERA}, 1);
        }
    }

    private void getPermissionToReadFromStorage() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.READ_EXTERNAL_STORAGE}, 1);
        }
    }

    private void getPermissionToWriteToStorage() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, 1);
        }
    }

    private File getPhotoPath() {
        /*
         * The following code was partially adapted from a Stack Overflow post by user mshwf at the
         * following URL: https://stackoverflow.com/questions/65637610/saving-files-in-android-11-to-external-storagesdk-30
         */
        final String fileName = FILE_NAME_FORMAT + Calendar.getInstance().getTime() + FILE_TYPE;
        final String childPath = FILE_SAVE_DIRECTORY + File.separator + fileName;

        File directory = null;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            directory = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), childPath);
        } else {
            directory = new File(Environment.getExternalStorageDirectory().toString(), childPath);
        }

        // Make sure the path exists.
        if (!directory.exists()) {
            // Make it, if it doesn't exit
            boolean success = directory.mkdirs();
            if (!success) {
                directory = null;
            }
        }

        return directory;
    }

    /*
     * The majority of the code in the bindUseCases method comes from Google documentation found
     * at https://developer.android.com/training/camerax and https://developers.google.com/ml-kit/reference/android.
     * bindUseCases is responsible for initializing and configuring the camera. We have documented
     * with comments what each line or lines of code do. Face detection via ML Kit is implemented
     * directly at time of camera initialization and is in this method as well. Since face detection
     * is the first trigger of gaze detection, the results of ImageProcessing and GazeDetection are
     * also handled here.
     */
    private void bindUseCases(@NonNull ProcessCameraProvider cameraProvider) {
        cameraProvider.unbindAll();
        /*
         * First we need a CameraSelector object that tells our application which camera to use. Our
         * application is best used with the front camera as we anticipate that users will
         */
        CameraSelector cameraSelector = new CameraSelector.Builder()
                .requireLensFacing(CameraSelector.LENS_FACING_FRONT)
                .build();
        /*
         * Next we create a preview object which provides the camera feed to our layout (GUI). In
         * order for the GUI to function properly, we need to set the surface provider. We are
         * letting Android set the surface provider and using that.
         */
        Preview preview = new Preview.Builder().build();
        preview.setSurfaceProvider(previewView.getSurfaceProvider());
        /*
         * Then we create an ImageCapture object so that we may save individual frames as photos. We
         * need to create a method that handles saving an image, and this method will be called both
         * by the onClick method and the analyze method which is implemented above.
         */
        Executor cameraExecutor = Executors.newSingleThreadExecutor();
        ImageCapture imageCapture = new ImageCapture.Builder()
                .setIoExecutor(cameraExecutor)
                .setTargetRotation(previewView.getDisplay().getRotation())
                .build();
        /*
         * Next we create an ImageAnalysis object which allows us to analyze each frame the camera
         * produces in real time. We are currently capturing frames at 720p, though we may wish to
         * choose a lower resolution in the future to enhance processing speed. We also set the
         * ImageAnalysis object to analyze only the latest frame as we only care about what is
         * happening at the singular, latest instance.
         */
        ImageAnalysis imageAnalysis = new ImageAnalysis.Builder()
                //.setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                // This line causes the application to crash at camera initialization. The feature
                // was recently added to CameraX and does not appear to work. We are instead
                // performing this conversion with OpenCV in the ImageProcessor class with the
                // convertYUVtoMat function.
                .setTargetResolution(new Size(1280, 720))
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build();
        /*
         * We are implementing a custom analyzer for our ImageAnalysis object. Specifically, our
         * gaze detection algorithm. Here we override the default analyze method of ImageAnalysis.
         */
        imageAnalysis.setAnalyzer(ContextCompat.getMainExecutor(this), new ImageAnalysis.Analyzer() {
            @Override
            public void analyze(@NonNull ImageProxy imageProxy) {
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
                     * options can be changed to optimize for performance.
                     */
                    FaceDetectorOptions highAccuracyOpts = new FaceDetectorOptions.Builder()
                            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE) // Change if slow
                            .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL) // Change if slow (we need eye and face contours and nose)
                            .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL)
                            .build();
                    /*
                     * We're ready to instantiate the FaceDetector.
                     */
                    FaceDetector faceDetector = FaceDetection.getClient(highAccuracyOpts);
                    /*
                     * The process method of FaceDetector returns a Task and a List of faces called
                     * Face. Task allows us to add the OnSuccessListener, OnFailureListener, and
                     * OnCompleteListener. It is important to note that the FaceDetector has not
                     * failed if no faces are detected. Failure only refers to encountering an
                     * error. The List faces can be used to conduct further analysis - particularly,
                     * gaze detection.
                     */
                    Task<List<Face>> result = faceDetector.process(image)
                            .addOnSuccessListener(new OnSuccessListener<List<Face>>() {
                                @Override
                                public void onSuccess(List<Face> faces) {
                                    /*
                                     * We are assume that there is no one looking at the camera. It
                                     * is essential to first initialize this integer to 0 so that
                                     * our UI is refreshed with correct information.
                                     */
                                    int numberOfFacesLookingTowardCamera = 0;
                                    /*
                                     * We only want to take action if faces.size() returns an
                                     * integer greater than or equal to the desired number of
                                     * subjects (faces). We only allow the user to select a maximum
                                     * desired amount of four, though the application will attempt
                                     * to perform gaze detection for any number of subjects detected
                                     * by FaceDetector.
                                     */
                                    if (faces.size() >= desiredNumberOfSubjects) {
                                        /*
                                         * The first step toward determining whether each subject is
                                         * looking toward the camera is to instantiate a Mat that
                                         * OpenCV can use in the Hough Circle algorithm.
                                         */
                                        Mat imageMatrix = ImageProcessor.convertYUVtoMat(mediaImage);
                                        /*
                                         * Now that we have our image in the form of a Mat object,
                                         * we can obtain the centers of the pupils for each face in
                                         * the image.
                                         */
                                        ArrayList<Point> pupilCenterCoordinates = ImageProcessor.getPupilCenterCoordinates(imageMatrix);
                                        /*
                                         * Finally, we run call the detectGazes method of
                                         * GazeDetector to determine the number of faces which are
                                         * looking toward the camera and update the value of
                                         * numberOfFacesLookingTowardCamera.
                                         */
                                        numberOfFacesLookingTowardCamera = GazeDetector.detectGazes(faces, pupilCenterCoordinates);
                                        /*
                                         * Helpful print statements.
                                         */
                                        System.out.println("There are " + faces.size() + " faces in view.");
                                        System.out.println("There are " + numberOfFacesLookingTowardCamera + " people looking toward the camera.");
                                        /*
                                         * We now verify if number of subjects looking toward the
                                         * camera is equivalent to the number of faces detected by
                                         * FaceDetector.
                                         */
                                        if (faces.size() == numberOfFacesLookingTowardCamera) {
                                            /*
                                             * Helpful print statement.
                                             */
                                            System.out.println("Each face is looking toward the camera. Attempting to save image...");
                                        }
                                    }
                                    /*
                                     * Finally, we update our two UI TextViews to reflect changes
                                     * in the number of faces detected and the number of gazes
                                     * detected.
                                     */
                                    updateFaceCounter(faces.size());
                                    updateGazeCounter(numberOfFacesLookingTowardCamera);
                                }
                            })
                            .addOnFailureListener(new OnFailureListener() {
                                @Override
                                public void onFailure(@NonNull Exception e) {
                                    // We do not need to handle this case at this time.
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
                                     * (which would always be the first in this case). Not that the
                                     * onComplete method will run regardless of whether
                                     * faceDetector.process() succeeds or fails.
                                     */
                                    imageProxy.close();
                                }
                            });
                }
            }
        });


        ImageButton captureButton = (ImageButton) findViewById(R.id.button2);
        captureButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                ImageCapture.OutputFileOptions outputFileOptions = new ImageCapture.OutputFileOptions.Builder(getPhotoPath()).build();
                imageCapture.takePicture(outputFileOptions, cameraExecutor,
                        new ImageCapture.OnImageSavedCallback() {
                            @Override
                            public void onImageSaved(@NonNull ImageCapture.OutputFileResults outputFileResults) {
                                /*
                                 * Display a message indicating to the user that a photo has been
                                 * saved.
                                 */
                                Toast.makeText(MainActivity.this, MESSAGE_PHOTO_SAVED, Toast.LENGTH_SHORT).show();
                            }
                            @Override
                            public void onError(@NonNull ImageCaptureException error) {
                                System.out.println(error.toString());
                            }
                        });
            }
        });
        /*
         * Tying it all together is the bindToLifecycle method of cameraProvider which executes the
         * three use cases established above.
         */
        cameraProvider.bindToLifecycle((LifecycleOwner) this, cameraSelector, imageCapture, imageAnalysis, preview);
    }

    public void startCamera() {
        getPermissionToUseCamera();
        previewView = findViewById(R.id.cameraView);
        cameraProviderFuture = ProcessCameraProvider.getInstance(this);
        cameraProviderFuture.addListener(() -> {
            try {
                ProcessCameraProvider cameraProvider = cameraProviderFuture.get();
                bindUseCases(cameraProvider);
            } catch (ExecutionException | InterruptedException e) {
                // No errors need to be handled for this Future.
                // This should never be reached.
            }
        }, ContextCompat.getMainExecutor(this));
    }

    public void openAlbum(View view) {
        ImageButton albumButton = (ImageButton) findViewById(R.id.button1);
        albumButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent intent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
                startActivity(intent);
            }
        });
    }

    public void menu(View view) {
        ImageButton menuButton = (ImageButton) findViewById(R.id.button3);
        menuButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                PopupMenu menu = new PopupMenu(MainActivity.this, menuButton);
                menu.getMenuInflater().inflate(R.menu.popup_menu, menu.getMenu());
                menu.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener() {
                    public boolean onMenuItemClick (MenuItem item) {
                        desiredNumberOfSubjects = Integer.parseInt(item.getTitle().toString());
                        Toast.makeText(MainActivity.this, MESSAGE_DESIRED_SUBJECTS_CHANGED + item.getTitle() + ".", Toast.LENGTH_SHORT).show();
                        return true;
                    }
                });
                menu.show();
            }
        });
    }

    public void updateFaceCounter(int numberOfFaces) {
        StringBuilder text = new StringBuilder();
        text.append("Faces detected: ").append(numberOfFaces);
        TextView faceCounter = (TextView) findViewById(R.id.faceCounterTextView);
        faceCounter.setText(text.toString());
    }

    public void updateGazeCounter(int numberOfGazes) {
        StringBuilder text = new StringBuilder();
        text.append("Number of faces looking toward the camera: ").append(numberOfGazes);
        TextView gazeCounter = (TextView) findViewById(R.id.gazeCounterTextView);
        gazeCounter.setText(text.toString());
    }


}