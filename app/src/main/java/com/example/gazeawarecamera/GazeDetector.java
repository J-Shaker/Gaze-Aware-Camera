package com.example.gazeawarecamera;

import android.media.Image;

import com.google.mlkit.vision.face.Face;

import java.util.ArrayList;
import java.util.List;

import org.opencv.core.Point;


public class GazeDetector {

    public static int detectGazes(List<Face> faces, ArrayList<Point> pupilCenterCoordinates) {

        int numberOfFacesLookingTowardCamera = 0;

        for (int i = 0; i < faces.size(); i++) {
            /*
             * First, we will check whether either eye is closed. If an eye is closed, than we can
             * immediately break as we know that the face cannot be looking toward the camera.
             * Android ML Kit can only give us a probability of a given eye being open. If this
             * probability is not at least 90% for both eyes, we will break and move on to the next
             * frame. Additionally, it may fail to determine a probability and return null. We
             * handle that possibility by catching the exception and again, breaking and moving on
             * to the next frame. Note that it should not fail to compute these probabilities if
             * FaceDetectorOptions are configured with an appropriate classification mode.
             */
            try {
                if (faces.get(i).getLeftEyeOpenProbability() < 0.9 || faces.get(i).getRightEyeOpenProbability() < 0.9) {
                    break;
                }
            } catch(NullPointerException e) {
                break;
            }


        }
        return numberOfFacesLookingTowardCamera;
    }
}
