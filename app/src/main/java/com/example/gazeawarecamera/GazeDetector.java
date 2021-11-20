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
     * This enumeration allows us to classify the direction of a face's gaze. These classifications
     * will be given once the angle of the pupil relative to the center of the eye is determined.
     * Commented next to them are the degree ranges which warrant the given classification.
     */
    private enum Direction {
        TOP_RIGHT_LOW, // (0-44 degrees)
        TOP_RIGHT_HIGH, // (45-89 degrees)
        TOP_LEFT_HIGH, // (90-134 degrees)
        TOP_LEFT_LOW, // (135-179 degrees)
        BOTTOM_LEFT_HIGH, // (180-224 degrees)
        BOTTOM_LEFT_LOW, // (225-269 degrees)
        BOTTOM_RIGHT_LOW, // (270-314 degrees)
        BOTTOM_RIGHT_HIGH; // (315-359 degrees)

        public static Direction getDirection(double angle) {
            if (angle >= 0 && angle < 45) {
                return TOP_RIGHT_LOW;
            } else if (angle >= 45 && angle < 90) {
                return TOP_RIGHT_HIGH;
            } else if (angle >= 90 && angle < 135) {
                return TOP_LEFT_HIGH;
            } else if (angle >= 135 && angle < 180) {
                return TOP_LEFT_LOW;
            } else if (angle >= 180 && angle < 225) {
                return BOTTOM_LEFT_HIGH;
            } else if (angle >= 225 && angle < 270) {
                return BOTTOM_LEFT_LOW;
            } else if (angle >= 270 && angle < 315) {
                return BOTTOM_RIGHT_LOW;
            } else {
                return BOTTOM_RIGHT_HIGH;
            }
        }
    }
    /*
     * Our GazeDetector takes in a List of faces and an ArrayList of Points. These points represent
     * the coordinates of the center of all of the eye pupils in the image. We need a way to
     * associate particular points in our ArrayList with the particular face we're analyzing at a
     * given time. Since we know that the points are going to be at some horizontal location between
     * an ear and the nose, we can isolate these points by finding the point such that the sum of
     * the distances between the ear and the point and the point and the nose is equal to the
     * distance between the nose and the ear.
     */
    public static Point isolatePupilCoordinates(ArrayList<Point> points, PointF minimumX, PointF maximumX) {
        /*
         * We have to iterate over the ArrayList of points. It is important to note that while an
         * iteration like this could slow down the program, the size of the ArrayList is generally
         * going to be 2 * the number of faces. Since we do not anticipate too many faces appearing
         * in an image at a time, the performance hit should be negligible. One way in which we may
         * increase performance is by removing the point from the list once it is found. While this
         * can be done in O(1) time, Java also moves every other element over in O(n) time.
         * Therefore, given that the anticipated data size is small, this may have negative
         * consequences for performance rather than positive ones.
         */
        for (int i = 0; i < points.size(); i++) {
            /*
             * Get the point.
             */
            Point point = points.get(i);
            /*
             * As discussed above, we need to make 3 calculations. We are only concerned with the
             * horizontal distance between these points. It would not benefit us to consider the
             * actual distance between these points for the purposes of this algorithm.
             */
            double distanceFromMinToPoint = Geometry.computeHorizontalDistanceBetweenTwoPoints(minimumX, point);
            double distanceFromPointToMax = Geometry.computeDistanceBetweenTwoPoints(maximumX, point);
            double distanceFromMinToMax = Geometry.computeHorizontalDistanceBetweenTwoPoints(minimumX, maximumX);
            /*
             * Now, check if the sum of two smaller distances adds up to the longer distance. If it
             * does, we have found our point, and will return it.
             */
            if (distanceFromMinToPoint + distanceFromPointToMax == distanceFromMinToMax) {
                return points.get(i);
            }
        }
        /*
         * If the pupils could not be found, we will return null.
         */
        return null;
    }
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
            FaceLandmark leftEar = faces.get(i).getLandmark(FaceLandmark.RIGHT_EAR); // Note that the right ear is the viewer's left
            FaceLandmark rightEar = faces.get(i).getLandmark(FaceLandmark.LEFT_EAR); // Note that the left ear is the viewer's right
            FaceLandmark nose = faces.get(i).getLandmark(FaceLandmark.NOSE_BASE);
            if (leftEye == null || rightEye == null || leftEar == null || rightEar == null || nose == null) {
                break;
            }
            /*
             * We have a list of OpenCV Point objects which correspond to the coordinates of the
             * centers of the pupils for each face in the image. However, we do not know which
             * Points correspond to which faces. We have implemented an algorithm which will use
             * the coordinates of the nose and ears to isolate the point which is between them. For
             * each face, there are two such points, on the left and right sides, and there is
             * exactly one point in the list which corresponds to either side of each face. The only
             * exception that could be encountered is if there are two faces, one on top of the
             * other, in which the distances between their nose and ears are the same. It is an
             * extremely unlikely case. If this point is not found, the algorithm will return null,
             * so we perform yet another check and terminate if that is the case.
             */
            Point leftPupilCenterCoordinates = isolatePupilCoordinates(pupilCenterCoordinates, leftEar.getPosition(), nose.getPosition());
            Point rightPupilCenterCoordinates = isolatePupilCoordinates(pupilCenterCoordinates, nose.getPosition(), rightEar.getPosition());
            if (leftPupilCenterCoordinates == null || rightPupilCenterCoordinates == null) {
                break;
            }
            /*
             * We are ready to determine the angle between the
             */
            double angleFromLeftEyeToEyeCenter = Geometry.computeAngleBetweenTwoPoints(rightEye.getPosition(), leftPupilCenterCoordinates);
            double angleFromRightEyeToEyeCenter = Geometry.computeAngleBetweenTwoPoints(rightEye.getPosition(), rightPupilCenterCoordinates);

            Direction leftEyeDirection = Direction.getDirection(angleFromLeftEyeToEyeCenter);
            Direction rightEyeDirection = Direction.getDirection(angleFromRightEyeToEyeCenter);



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
