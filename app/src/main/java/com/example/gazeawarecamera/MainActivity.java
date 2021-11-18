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

    public static final String SAVE_DIRECTORY = "Gaze Aware Camera Photos";
    public static final String FILE_NAME_FORMAT = "IMG ";
    public static final String FILE_TYPE = ".jpg";

    private PreviewView previewView;
    private ListenableFuture<ProcessCameraProvider> cameraProviderFuture;

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
        File photo = null;
        String fileName = FILE_NAME_FORMAT + Calendar.getInstance().getTime() + FILE_TYPE;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            photo = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES) + File.separator + fileName);
        } else {
            photo = new File(Environment.getExternalStorageDirectory() + File.separator + Environment.DIRECTORY_PICTURES + File.separator + fileName);
        }

        // Make sure the path exists.
        if (!photo.exists()) {
            // Make it, if it doesn't exit
            boolean success = photo.mkdirs();
            if (!success) {
                photo = null;
            }
        }

        return photo;
    }

    /*
     * The majority of the code in the bindUseCases method comes from official Android Documentation
     * found at https://developer.android.com/training/camerax. The code is responsible only for the
     * set up of camera usage in an Android application using the CameraX API. We have provided
     * comments below which explain the function of each code snippet.
     */
    private void bindUseCases(@NonNull ProcessCameraProvider cameraProvider) {
        cameraProvider.unbindAll();
        /*
         * First we need a CameraSelector object that tells our application which camera to use. For
         * now, we are defaulting to using the front camera, as that is most appropriate for our
         * use case.
         */
        CameraSelector cameraSelector = new CameraSelector.Builder()
                .requireLensFacing(CameraSelector.LENS_FACING_FRONT)
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
                 * We can increase performance by adjusting many of these settings. We can, for
                 * example, change the landmark mode to only detect the landmarks we're using in our
                 * calculation. We can also change the performance mode from accurate to fast.
                 */
                FaceDetectorOptions highAccuracyOpts = new FaceDetectorOptions.Builder()
                        .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
                        .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)
                        .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL)
                        .build();

                @OptIn(markerClass = androidx.camera.core.ExperimentalGetImage.class)
                Image mediaImage = imageProxy.getImage();
                if (mediaImage != null) {
                    InputImage image = InputImage.fromMediaImage(mediaImage, imageProxy.getImageInfo().getRotationDegrees());
                    FaceDetector detector = FaceDetection.getClient(highAccuracyOpts);

                    Task<List<Face>> result = detector.process(image)
                            .addOnSuccessListener(new OnSuccessListener<List<Face>>() {
                                @Override
                                public void onSuccess(List<Face> faces) {
                                    int numberOfFacesLookingTowardCamera = 0;
                                    if (faces.isEmpty()) {
                                        System.out.println("There are no faces in view.");
                                    } else {
                                        Mat imageMatrix = ImageProcessor.convertYUVtoMat(mediaImage);
                                        ArrayList<Point> pupilCenterCoordinates = ImageProcessor.getPupilCenterCoordinates(imageMatrix);
                                        // numberOfFacesLookingTowardCamera = GazeDetector.detectGazes(faces, processedImage);
                                        System.out.println("There are " + faces.size() + " faces in view.");
                                        System.out.println("There are " + numberOfFacesLookingTowardCamera + " people looking toward the camera.");
                                        if (faces.size() == numberOfFacesLookingTowardCamera) {
                                            System.out.println("Each face is looking toward the camera. Attempting to save image...");
                                        }
                                    }
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
                                    imageProxy.close();
                                }
                            });
                }
            }
        });
        /*
         * Then we create an ImageCapture object so that we may save individual frames as photos. We
         * need to create a method that handles saving an image, and this method will be called both
         * by the onClick method and the analyze method which is implemented above.
         */
        ImageCapture imageCapture = new ImageCapture.Builder()
                .setTargetRotation(previewView.getDisplay().getRotation())
                .build();



        ImageButton captureButton = (ImageButton) findViewById(R.id.button2);
        Executor cameraExecutor = Executors.newSingleThreadExecutor();
        captureButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {

                ImageCapture.OutputFileOptions outputFileOptions = new ImageCapture.OutputFileOptions.Builder(getPhotoPath()).build();

                imageCapture.takePicture(outputFileOptions, cameraExecutor,
                        new ImageCapture.OnImageSavedCallback() {
                            @Override
                            public void onImageSaved(@NonNull ImageCapture.OutputFileResults outputFileResults) {
                                System.out.println("Photo saved.");
                            }
                            @Override
                            public void onError(@NonNull ImageCaptureException error) {
                                System.out.println(error.toString());
                            }
                        });
            }
        });
        /*
         * Finally we create a preview object which provides the camera feed to our layout (GUI). In
         * order for the GUI to function properly, we need to set the surface provider. We are
         * letting Android set the surface provider and using that.
         */
        Preview preview = new Preview.Builder().build();
        preview.setSurfaceProvider(previewView.getSurfaceProvider());
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
                        Toast.makeText(MainActivity.this,
                                "Item Clicked: " + item.getTitle(),
                                Toast.LENGTH_SHORT).show();
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