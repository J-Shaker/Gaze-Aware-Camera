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

import android.graphics.PointF;

import androidx.annotation.NonNull;

import com.google.mlkit.vision.face.Face;
import com.google.mlkit.vision.face.FaceLandmark;

import java.util.ArrayList;
import java.util.List;

import org.opencv.core.Point;


public class GazeDetector {
    /*
     * We need to determine the center point of the image. We are assuming that the center is
     * roughly, if not exactly, the center of the image. So, in order to determine the center, we
     * can divide the horizontal and vertical pixel counts by 2. We only need to perform this
     * calculation once since the resolution of the image will not change while the application is
     * running. Therefore, we have made this PointF object final and private to the class.
     */
    private static final PointF IMAGE_CENTER_POINT = new PointF((float) MainActivity.PIXEL_COUNT_HORIZONTAL / 2, (float) MainActivity.PIXEL_COUNT_VERTICAL / 2);
    /*
     * The following method attempts to determine the number of faces in the image that are
     * looking toward the camera. Specifically, we compare the angle between the center of the image
     * and the center of the eye cavity and the angle between the center of the eye cavity and the
     * center of the pupil.
     */
    public static int detectGazesWithAngles(@NonNull List<Face> faces, ArrayList<Point> pupilCenterCoordinates) {
        /*
         * We assume that there is no one looking toward the camera at first.
         */
        int numberOfFacesLookingTowardCamera = 0;
        /*
         * Then, we begin iterating over the list of faces. For each face that is determined to be
         * looking toward the camera, we will increment the value of
         * numberOfFacesLookingTowardCamera by 1.
         */
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
             * Now we will retrieve the landmarks we are going to be using in . It is possible
             * that ML Kit is unable to build these features, so we must check if either of them are
             * null. If so, it will be impossible to determine the gaze of the face and therefore in
             * this case we will terminate the loop.
             */
            FaceLandmark leftEye = faces.get(i).getLandmark(FaceLandmark.RIGHT_EYE); // Note that the right eye is the viewer's left
            FaceLandmark rightEye = faces.get(i).getLandmark(FaceLandmark.LEFT_EYE); // Note that the left eye is the viewer's right
            FaceLandmark leftEar = faces.get(i).getLandmark(FaceLandmark.RIGHT_EAR);
            FaceLandmark rightEar = faces.get(i).getLandmark(FaceLandmark.LEFT_EAR);
            FaceLandmark nose = faces.get(i).getLandmark(FaceLandmark.NOSE_BASE);
            if (leftEye == null || rightEye == null || leftEar == null || rightEar == null || nose == null) {
                break;
            }
            /*
             * We have a list of OpenCV Point objects which correspond to the coordinates of the
             * centers of the pupils for each face in the image. However, we do not know which
             * Points correspond to which faces.
             */
            Point leftPupilCoordinate = Geometry.findPointInDomain(pupilCenterCoordinates, leftEar.getPosition().x, nose.getPosition().x);
            Point rightPupilCoordinate = Geometry.findPointInDomain(pupilCenterCoordinates, rightEar.getPosition().x, nose.getPosition().x);


            double angleFromLeftEyeToEyeCenter = Geometry.computeAngleBetweenTwoPoints(rightEye.getPosition(), leftPupilCoordinate);


            double angleFromRightEyeToEyeCenter = Geometry.computeAngleBetweenTwoPoints(rightEye.getPosition(), rightPupilCoordinate);



            double angleFromLeftEyeToPhotoCenter = Geometry.computeAngleBetweenTwoPoints(IMAGE_CENTER_POINT, leftEye.getPosition());

            double angleFromRightEyeToPhotoCenter = Geometry.computeAngleBetweenTwoPoints(IMAGE_CENTER_POINT, rightEye.getPosition());


            // A series of if else statements to determine the gaze
            // eg, if (angle > 0 && angle < 45) then ...
            // gaze can be one of the following nine enumerations
            // top-right-low (0-44 degrees), top-right-high (45-89 degrees),
            // top-left-high (90-134 degrees), top-left-low (135-179 degrees),
            // bottom-left-high (180-224 degrees), bottom-left-low (225-269 degrees),
            // bottom-right-low (270-314 degrees), bottom-right-high (315-359 degrees)
            // center ()


            // A series of if else statements to determine if their gaze is toward the camera which
            // we know is in the center of the photo
            // eg, if person is left of center and below center, then their gaze direction should be
            //


        }
        return numberOfFacesLookingTowardCamera;
    }

}
