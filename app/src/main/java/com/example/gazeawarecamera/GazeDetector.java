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

import com.google.mlkit.vision.face.Face;
import com.google.mlkit.vision.face.FaceLandmark;

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

            FaceLandmark leftEye = faces.get(i).getLandmark(FaceLandmark.LEFT_EYE);
            FaceLandmark rightEye = faces.get(i).getLandmark(FaceLandmark.RIGHT_EYE);
            FaceLandmark nose = faces.get(i).getLandmark(FaceLandmark.NOSE_BASE);



        }
        return numberOfFacesLookingTowardCamera;
    }
}
