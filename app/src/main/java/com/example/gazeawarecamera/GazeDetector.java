package com.example.gazeawarecamera;

import androidx.camera.core.ImageProxy;

import com.google.mlkit.vision.face.Face;

import java.util.List;


@SuppressWarnings("ALL")
public class GazeDetector {

    private final ImageProxy imageOfFaces;
    private final List listOfFaces;

    public GazeDetector(ImageProxy imageProxy, List faces) {
        imageOfFaces = imageProxy;
        listOfFaces = faces;
    }

    private boolean detectGazes() {
        double uniformGazeIndex = 0;
        double dividend = 1 / (double) listOfFaces.size();
        for (int i = 0; i < listOfFaces.size(); i++) {
            ImageProcessor imageProcessor = new ImageProcessor(imageOfFaces, (Face) listOfFaces.get(i));
            Eye eye = imageProcessor.processImage();
            // Use eye to calculate gaze vector
            // if gazeVector ...
            uniformGazeIndex += dividend;
        }
        return uniformGazeIndex == 1.00;
    }

}
