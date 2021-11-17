package com.example.gazeawarecamera;

import android.graphics.Point;
import android.media.Image;

import com.google.mlkit.vision.face.Face;

import java.util.ArrayList;
import java.util.List;

public class GazeDetector {

    public static int detectGazes(List<Face> faces, Image frame) {

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
            /*
             * Now that we've established both eyes are open, we need to retrieve the coordinates of
             * the center of the pupils. Image processor returns a list containing two points
             */
            ArrayList<Point> coordinates = ImageProcessor.getPupilCenterCoordinates(frame, faces.get(i).getContour(1));
            /*
             * 
             */


        }
        return numberOfFacesLookingTowardCamera;
    }
}
