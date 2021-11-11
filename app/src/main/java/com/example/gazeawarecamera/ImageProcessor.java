package com.example.gazeawarecamera;

import androidx.camera.core.ImageProxy;

import com.google.mlkit.vision.face.Face;

import java.nio.ByteBuffer;

public class ImageProcessor {

    private final ImageProxy imageOfFaces;
    private final Face currentFace;

    public ImageProcessor(ImageProxy imageProxy, Face face) {
        imageOfFaces = imageProxy;
        currentFace = face;
        ByteBuffer facePlanes;
        int i;
        int original;
        int negated;

        //I think this is getting the byte data in RGB...might effect too much if so (alpha channel)
        facePlanes = imageProxy.getPlanes()[0].getBuffer();

        //negate the image by subtracting all RGB values from 255
        for (i = 0; i < (facePlanes.limit() - 3); i++) {
            original = facePlanes.getInt(i);
            negated = 255 - original;
            facePlanes.putInt(i, negated);
        }
    }

    public Eye processImage() {
        return new Eye();
    }

}
