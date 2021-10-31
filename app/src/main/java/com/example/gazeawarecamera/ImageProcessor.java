package com.example.gazeawarecamera;

import androidx.camera.core.ImageProxy;

import com.google.mlkit.vision.face.Face;

public class ImageProcessor {

    private final ImageProxy imageOfFaces;
    private final Face currentFace;

    public ImageProcessor(ImageProxy imageProxy, Face face) {
        imageOfFaces = imageProxy;
        currentFace = face;
    }

    public Eye processImage() {
        return new Eye();
    }

}
