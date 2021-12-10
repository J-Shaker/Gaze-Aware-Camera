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

import static com.example.gazeawarecamera.MainActivity.eyeCascade;

import android.graphics.Bitmap;
import android.graphics.PointF;
import android.graphics.Rect;
import android.media.Image;

import androidx.annotation.NonNull;

import com.google.mlkit.vision.face.Face;
import com.google.mlkit.vision.face.FaceLandmark;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

import org.opencv.android.Utils;
import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.KeyPoint;
import org.opencv.core.Mat;
import org.opencv.core.MatOfKeyPoint;
import org.opencv.core.MatOfRect;
import org.opencv.core.Point;
import org.opencv.core.Size;
import org.opencv.features2d.SimpleBlobDetector;
import org.opencv.features2d.SimpleBlobDetector_Params;
import org.opencv.imgproc.Imgproc;


public class GazeDetector {

    /*
     * We are going to define horizontal and vertical tolerance values which will account for margin
     * margin of error when making our gaze-determining calculations.
     */
    private static final double HORIZONTAL_TOLERANCE = 20.0;
    private static final double VERTICAL_TOLERANCE = 5.0;


    /*
     * Integers used for analyzing test results.
     */
    public static int totalNumberOfGazesDetected = 0;
    public static int totalNumberOfEyesDetected = 0;
    public static int totalNumberOfPupilsDetected = 0;
    public static int totalNumberOfTimesEveryGazeWasCaptured = 0;


    /*
     * This enumeration allows us to classify the direction of a face's gaze. These classifications
     * will be given once the angle of the pupil relative to the center of the eye is determined.
     * Commented next to them are the degree ranges which warrant the given classification.
     *
     * Contributed by Mathew.
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
            /*
             * This method returns a Direction based on the angle it takes in as an argument. These
             * directions are useful since they provide a general description of what the angles
             * represent. Practically, it may be more useful to determine gaze using these
             * directions rather than working with precise angles due to the imprecise nature of
             * image processing.
             */
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

    private static Object changeRect(Object rectangle) {
        /*
         * A method that converts from Android Graphics Rect to OpenCV Core Rect. These classes
         * represent the same thing, but we often need to switch between them. If an object is
         * passed in that is neither version of the Rect object, null will be returned.
         *
         * Contributed by Mathew
         */
        if (rectangle instanceof org.opencv.core.Rect) {
            /*
             * The Android Graphics Rect constructor has four arguments. They are left, top, right,
             * and bottom. The left and top values represent the x and y coordinates of the top left
             * corner of the rectangle, while the right and bottom values represent the bottom right
             * corner of the rectangle. We obtain left and top directly from the OpenCV Rect, and
             * add width and height to those values to obtain bottom and right.
             */
            int left = ((org.opencv.core.Rect) rectangle).x;
            int top = ((org.opencv.core.Rect) rectangle).y;
            int right = ((org.opencv.core.Rect) rectangle).x + ((org.opencv.core.Rect) rectangle).width;
            int bottom = ((org.opencv.core.Rect) rectangle).y + ((org.opencv.core.Rect) rectangle).height;
            return new android.graphics.Rect(left, top, right, bottom);
        } else if (rectangle instanceof android.graphics.Rect) {
            /*
             * The OpenCV Core Rect constructor also has four arguments. They are x, y, width, and
             * height. The x and y arguments represent the top left corner of the rectangle. All
             * values can be obtained from the OpenCV Rect object.
             */
            int width = ((android.graphics.Rect) rectangle).width();
            int height = ((android.graphics.Rect) rectangle).height();
            int x = ((android.graphics.Rect) rectangle).left;
            int y = ((android.graphics.Rect) rectangle).top;
            return new org.opencv.core.Rect(x, y, width, height);
        } else {
            return null;
        }
    }

    /*
     * Android Studio allows you to view Bitmaps while debugging. This method converts a Mat object
     * to a Bitmap that we can view while debugging.
     *
     * Contributed by Brayden
     */
    private Bitmap convertMatToBitmap(Mat matrix) {
        Bitmap bitmap = Bitmap.createBitmap(matrix.cols(), matrix.rows(), Bitmap.Config.ARGB_8888);
        Utils.matToBitmap(matrix, bitmap);
        return bitmap;
    }

    private static Mat imageToGreyMatrix(Image image) {
        /*
         * This method takes in the Image object generated by the camera and returns a Mat object
         * that we can use with OpenCV's image processing methods. This method is actually in the
         * OpenCV library, but it is private. The code for this method, however, is available on
         * the OpenCV Github repository here:
         * https://github.com/opencv/opencv/blob/master/modules/java/generator/android-21/java/org/opencv/android/JavaCamera2View.java
         */
        Image.Plane[] planes = image.getPlanes();
        int w = image.getWidth();
        int h = image.getHeight();
        assert(planes[0].getPixelStride() == 1);
        ByteBuffer y_plane = planes[0].getBuffer();
        int y_plane_step = planes[0].getRowStride();
        return new Mat(h, w, CvType.CV_8UC1, y_plane, y_plane_step);
    }

    /*
     * Our most up to date method for finding pupils, contributed by Mathew using some code from
     * http://romanhosek.cz/android-eye-detection-and-tracking-with-opencv/
     * and most code from the getPupilCoordinatesWithBlobDetectorMethod
     */
    private static ArrayList<Point> getPupilCoordinatesWithDarkestLocation(Mat greyImage, Rect faceBoundingBox) {
        /*
         * First, we need to create a matrix of just the face we are looking at from the original
         * image. This is easy to accomplish using our face bounding box, we just need to change it
         * to an OpenCV bounding box first.
         */
        Mat greyFace = new Mat(greyImage, (org.opencv.core.Rect) changeRect(faceBoundingBox));
        /*
         * Now, to eliminate false detections by the CascadeClassifier, we will further constrain
         * the search area to just the top half of the face. This will eliminate nostrils from the
         * image.
         */
        Mat croppedFace = new Mat(greyFace, new org.opencv.core.Rect(0, 0, greyFace.cols(), greyFace.rows()/2));
        /*
         * detectMultiScale initializes a MatOfRect of object. This is a matrix where each element
         * is a rectangle - the eye bounding boxes. The third and fourth arguments for
         * detectMultiScale are scaleFactor and minNeighbors, where higher scaleFactor typically
         * results in more detection, and higher minNeighbours results in lower detection. We found
         * a balance successfully detects eyes most of the time. Finally, MatOfRect has a
         * convenient method which allows us to convert it to an array. This will make it much
         * easier to work with
         */
        MatOfRect eyes = new MatOfRect();
        eyeCascade.detectMultiScale(croppedFace, eyes, 1.3, 25);
        org.opencv.core.Rect[] eyeBoundingBoxes = eyes.toArray();

        totalNumberOfEyesDetected += eyeBoundingBoxes.length;

        /*
         * Now, we need an ArrayList to store the pupil coordinates that we find. We cannot predict
         * the order in which the pupils will be placed, but we have a method which will figure it
         * out for us.
         */
        ArrayList<Point> pupilCoordinates = new ArrayList<Point>();
        /*
         * Now we can start iterating over the
         */
        for (int i = 0; i < eyeBoundingBoxes.length; i++) {
            /*
             * We can use our bounding box and our face image to get the region of the eye as a Mat.
             */
            Mat greyEye = new Mat(greyFace, eyeBoundingBoxes[i]);
            /*
             * minMaxLoc finds the darkest region of an image.
             */
            Core.MinMaxLocResult pupil = Core.minMaxLoc(greyEye);
            /*
             * We must translate the coordinates of the pupil back into the original image, so
             * we make a new Point and sum the corners of each Matrix we've stepped through. Then
             * we can finally add the Point to the list.
             */
            Point pupilCoordinate = new Point(pupil.minLoc.x + eyeBoundingBoxes[i].x + faceBoundingBox.left, pupil.minLoc.y + eyeBoundingBoxes[i].y + faceBoundingBox.top);
            pupilCoordinates.add(pupilCoordinate);
        }

        System.out.println("The number of pupils detected is: " + pupilCoordinates.size());

        totalNumberOfPupilsDetected += pupilCoordinates.size();

        return pupilCoordinates;
    }


    // ---------------------------------------------------------------------------------------------


    /*
     * Our deprecated method for finding pupils as of 12/10/2021
     */
    private static ArrayList<Point> getPupilCoordinatesWithBlobDetector(Mat greyImage, Rect faceBoundingBox) {
        /*
         * https://medium.com/@stepanfilonov/tracking-your-eyes-with-python-3952e66194a6 - methodology
         * https://www.tutorialspoint.com/java_dip/eroding_dilating.htm - values for erosion and dilation
         *
         * A joint effort contributed by Mathew, John, and Brayden using the help of the sources
         * above.
         */
        ArrayList<Point> pupilCoordinates = new ArrayList<Point>();

        Mat greyFace = new Mat(greyImage, (org.opencv.core.Rect) changeRect(faceBoundingBox));

        Mat croppedFace = new Mat(greyFace, new org.opencv.core.Rect(0, 0, greyFace.cols(), greyFace.rows()/2));


        MatOfRect eyes = new MatOfRect();
        eyeCascade.detectMultiScale(croppedFace, eyes, 1.3, 25);
        org.opencv.core.Rect[] eyeBoundingBoxes = eyes.toArray();

        totalNumberOfEyesDetected += eyeBoundingBoxes.length;

        int erosion_size = 2;
        int dilation_size = 2;
        Point defAnchor = new Point(-1, -1);
        Mat erodeElement = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, new Size(2 * erosion_size + 1, 2 * erosion_size + 1));
        Mat dilationElement = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, new Size(2 * dilation_size + 1, 2 * dilation_size + 1));

        SimpleBlobDetector_Params parameters = new SimpleBlobDetector_Params();
        parameters.set_filterByCircularity(true);
        parameters.set_minCircularity((float) 0.3);
        parameters.set_maxCircularity((float) 1.0);
        SimpleBlobDetector detector = SimpleBlobDetector.create(parameters);

        for (int i = 0; i < eyeBoundingBoxes.length; i++) {

            Mat greyEye = new Mat(greyFace, eyeBoundingBoxes[i]);

            Mat binaryEye = new Mat();

            Imgproc.adaptiveThreshold(greyEye, binaryEye, 255, Imgproc.ADAPTIVE_THRESH_MEAN_C, Imgproc.THRESH_BINARY, 81, 55);
            Imgproc.erode(binaryEye, binaryEye, erodeElement, defAnchor, 2);
            Imgproc.dilate(binaryEye, binaryEye, dilationElement, defAnchor, 4);
            Imgproc.medianBlur(binaryEye, binaryEye, 5);

            MatOfKeyPoint keyPoints = new MatOfKeyPoint();
            detector.detect(binaryEye, keyPoints);
            KeyPoint[] keyPointsArray = keyPoints.toArray();

            for (int j = 0; j < keyPointsArray.length; j++) {
                Point point = keyPointsArray[j].pt;
                double adjustedX = point.x + eyeBoundingBoxes[i].x + faceBoundingBox.left;
                double adjustedY = point.y + eyeBoundingBoxes[i].y + faceBoundingBox.top;
                Point adjustedPoint = new Point(adjustedX, adjustedY);
                System.out.println("Eye: " + (i + 1) + ", pupil " + (j + 1) + ": " + adjustedPoint.toString());
                pupilCoordinates.add(adjustedPoint);
            }

        }

        totalNumberOfPupilsDetected += pupilCoordinates.size();

        if (pupilCoordinates.size() > 2) {
            System.out.println("Warning: SimpleBlobDetector located more than two blobs for this face. Results may be inaccurate.");
        }

        System.out.println("The number of pupils detected is: " + pupilCoordinates.size());

        return pupilCoordinates;
    }


    // ---------------------------------------------------------------------------------------------


    /*
     * Our GazeDetector takes in a List of faces and an ArrayList of Points. These points represent
     * the coordinates of the center of all of the eye pupils in the image. We need a way to
     * associate particular points in our ArrayList with the particular face we're analyzing at a
     * given time. Since we know that the points are going to be at some horizontal location between
     * an ear and the nose, we can isolate these points by finding the point such that the sum of
     * the distances between the ear and the point and the point and the nose is equal to the
     * distance between the nose and the ear.
     *
     * Contributed by Mathew.
     */
    private static Point isolatePupilCoordinates(ArrayList<Point> points, PointF minimumX, PointF maximumX) {
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
            double distanceFromPointToMax = Geometry.computeHorizontalDistanceBetweenTwoPoints(maximumX, point);
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
     * This is the primary GazeDetection method. A joint effort contributed by Mathew and John.
     */
    public static int detectGazesWithDistances(@NonNull List<Face> faces, Image originalImage) {
        /*
         * First, since we are using OpenCV for image processing, we need to convert our image into
         * matrix form. Since all imageProcessing needs to be done on a greyscaled image, we do not
         * care that the matrix returned is in grey.
         */
        Mat imageMatrix = imageToGreyMatrix(originalImage);
        /*
         * At the end of the loop, we will pass the bounding boxes of each face into a drawing
         * method in MainActivity so that the user can see that their face is recognized. Here we
         * instantiate the ArrayList of Rect objects in which we'll place these.
         */
        ArrayList<Rect> faceBoundingBoxes = new ArrayList<Rect>();
        /*
         * We assume that there is no one looking toward the camera.
         */
        int numberOfFacesLookingTowardCamera = 0;
        /*
         * Then, we begin iterating over the list of faces. For each face that is determined to be
         * looking toward the camera, we will increment the value of
         * numberOfFacesLookingTowardCamera by 1.
         */
        for (int i = 0; i < faces.size(); i++) {
            /*
             * Now, we will check whether either eye is closed. If an eye is closed, than we can
             * immediately break as we know that the face cannot be looking toward the camera.
             * Android ML Kit can only give us a probability of a given eye being open. If this
             * probability is not at least 90% for both eyes, we will break and move on to the next
             * frame. Additionally, it may fail to determine a probability and return null. We
             * handle that possibility by catching the exception and again, breaking and moving on
             * to the next frame. Note that it should not fail to compute these probabilities if
             * FaceDetectorOptions is configured with an appropriate classification mode.
             */
            try {
                if (faces.get(i).getLeftEyeOpenProbability() < 0.9 || faces.get(i).getRightEyeOpenProbability() < 0.9) {
                    System.out.println("The probability of one or more eyes on face " + (i + 1) + " being open is less than 90%.");
                    continue;
                }
            } catch (NullPointerException e) {
                continue;
            }
            /*
             * Next, we will retrieve the landmarks we are going to be using. It is possible that ML
             * Kit is unable to build these features, so we must check if any of them are null. If
             * so, it will be impossible to determine the gaze of the face and therefore in this
             * case we will terminate this iteration.
             */
            FaceLandmark leftEye = faces.get(i).getLandmark(FaceLandmark.LEFT_EYE);
            FaceLandmark rightEye = faces.get(i).getLandmark(FaceLandmark.RIGHT_EYE);
            FaceLandmark leftEar = faces.get(i).getLandmark(FaceLandmark.LEFT_EAR);
            FaceLandmark rightEar = faces.get(i).getLandmark(FaceLandmark.RIGHT_EAR);
            FaceLandmark nose = faces.get(i).getLandmark(FaceLandmark.NOSE_BASE);
            if (leftEye == null || rightEye == null || leftEar == null || rightEar == null || nose == null) {
                System.out.println("ML Kit did not find one or more of the required landmarks.");
                continue;
            }
            /*
             * Those landmarks are necessary, but they are not the only location we need to
             * determine. We also need the find the pupil coordinates. We have a method for finding
             * those, but it needs the original image as well as the area which contains the current
             * face. Since we are using OpenCV, we need our image in matrix form. We found this
             * before we starting looping. We also need to pass in the face bounding box, which
             * we can get from the Face object. We can pass those into our method and get back the
             * list of coordinates. The list should have a size of two, but it could be larger. We
             * discuss that below.
             */
            Rect faceBoundingBox = faces.get(i).getBoundingBox();
            ArrayList<Point> pupilCoordinates = getPupilCoordinatesWithDarkestLocation(imageMatrix, faceBoundingBox);
            /*
             * We now have a list of OpenCV Point objects which correspond to the coordinates of the
             * centers of the pupils for each eye in the current face. However, we do not know which
             * Points correspond to which eye. We also need to handle the case that features that
             * are not eyes are detected and returned in the list of points. To handle these cases,
             * we have implemented an algorithm which will use the coordinates of the nose and ears
             * to isolate the point which is between them. In the event these points are not found,
             * the method will return null, so we need to make sure that we check for that and
             * terminate if that is the case.
             */
            Point leftPupilCenterPoint = isolatePupilCoordinates(pupilCoordinates, leftEar.getPosition(), nose.getPosition());
            Point rightPupilCenterPoint = isolatePupilCoordinates(pupilCoordinates, nose.getPosition(), rightEar.getPosition());

            if (leftPupilCenterPoint == null) {
                System.out.println("The coordinates of the left pupil could not be determined.");
            } else {
                System.out.println("Face: " + (i + 1) + ", left pupil: " + leftPupilCenterPoint.toString());
            }

            if (rightPupilCenterPoint == null) {
                System.out.println("The coordinates of the right pupil could not be determined.");
            } else {
                System.out.println("Face: " + (i + 1) + ", right pupil: " + rightPupilCenterPoint.toString());
            }

            if (leftPupilCenterPoint == null || rightPupilCenterPoint == null) {
                continue;
            }

            /*
             * Now that we have our two pupil coordinates, we can compare their locations to the
             * locations of landmarks. We are only concerned with an eye that is looking forward.
             * Therefore, we want a pupil that is roughly centered on both eyes. There are two
             * distances we will look at in making this determination. The first distance we will
             * consider is the distance between each pupil and the nose. Imagine the following two
             * text graphics are faces where the two Os are pupil locations and the dotted lines are
             * distances.
             *
             * 1) | O-----|-O     |
             * 2) |   O---|---O   |
             *
             * Notice that in 1, the distances are 5 units and 1 unit, whereas in 2,
             * the distances are both 3 units. We consider 1 to be a face that is gazed to the left
             * because the pupil on the left is farther from the nose than the pupil on the right.
             * On the other hand, 2 may be considered to be looking toward the camera since the gaze
             * does not lean to the right or left. Analogous to this, we must consider a vertical
             * case to ensure that the face is not looking up or down. For this we compare the
             * vertical distance of the pupils to the center of the eye cavity. The only difference
             * in this case is that we are checking whether the pupils are above or below the center
             * of the eyes. The next four lines of code find these four distances.
             */
            double horizontalDistanceFromLeftPupilToNose = Geometry.computeHorizontalDistanceBetweenTwoPoints(nose.getPosition(), leftPupilCenterPoint);
            double horizontalDistanceFromRightPupilToNose = Geometry.computeHorizontalDistanceBetweenTwoPoints(nose.getPosition(), rightPupilCenterPoint);
            double verticalDistanceFromLeftPupilToLeftEye = Geometry.computeVerticalDistanceBetweenTwoPoints(leftEye.getPosition(), leftPupilCenterPoint);
            double verticalDistanceFromRightPupilToRightEye = Geometry.computeVerticalDistanceBetweenTwoPoints(rightEye.getPosition(), rightPupilCenterPoint);
            /*
             * Now we need to determine the difference between the left and right horizontal and
             * vertical cases. Ideally, both of these differences would be 0. We allow negative
             * values to occur because this will tell us which direction the eye is looking in the
             * horizontal case.
             */
            double horizontalDifference = horizontalDistanceFromRightPupilToNose - horizontalDistanceFromLeftPupilToNose;
            double verticalDifference = verticalDistanceFromRightPupilToRightEye - verticalDistanceFromLeftPupilToLeftEye;
            /*
             * With those, we can finally check whether the face is looking toward the camera.
             * Again, the ideal value for these differences is 0, but we need to account for margin
             * of error as well as allow the user a small degree of freedom, so we are checking
             * that the differences are less than or equal to the set tolerance levels.
             */
            if (Math.abs(horizontalDifference) <= HORIZONTAL_TOLERANCE && Math.abs(verticalDifference) <= VERTICAL_TOLERANCE) {
                System.out.println("Gaze detected on face " + (i + 1) + "!\n");
                numberOfFacesLookingTowardCamera += 1;
                totalNumberOfGazesDetected += 1;
            } else {
                if (horizontalDifference < 0) {
                    System.out.println("Face " + (i + 1) + " is looking to the right.\n");
                } else {
                    System.out.println("Face " + (i + 1) + " is looking to the left.\n");
                }
            }
        }
        /*
         * Finally, we return our final number of gazes detected.
         */
        return numberOfFacesLookingTowardCamera;
    }


    // ---------------------------------------------------------------------------------------------


    /*
     * The following method attempts to determine the number of faces in the image that are
     * looking toward the camera. Specifically, we compare the angle between the center of the image
     * and the center of the eye cavity and the angle between the center of the eye cavity and the
     * center of the pupil. This method may be flawed because we do not know where the camera
     * actually lies in the image, and this would vary between hardware configurations. Where this
     * method might be useful is in determining the exact direction someone is looking, but that is
     * outside the scope of the project. If we have time, we may wish to determine the direction and
     * then place it above the corresponding face bounding box.
     *
     * A joint effort contributed by Mathew and John.
     */
    public static int detectGazesWithAngles(@NonNull List<Face> faces, Image image) {
        /*
         * First, since we are using OpenCV for image processing, we need to convert our image into
         * matrix form. Since all imageProcessing needs to be done on a greyscaled image, we do not
         * care that the matrix returned is in grey.
         */
        Mat imageMatrix = imageToGreyMatrix(image);
        /*
         * We assume that there is no one looking toward the camera.
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
            } catch (NullPointerException e) {
                break;
            }
            /*
             * Now we will retrieve the landmarks we are going to be using. It is possible that ML
             * Kit is unable to build these features, so we must check if either of them are null.
             * If so, it will be impossible to determine the gaze of the face and therefore in this
             * case we will terminate the loop.
             */
            FaceLandmark leftEye = faces.get(i).getLandmark(FaceLandmark.LEFT_EYE);
            FaceLandmark rightEye = faces.get(i).getLandmark(FaceLandmark.RIGHT_EYE);
            FaceLandmark leftEar = faces.get(i).getLandmark(FaceLandmark.LEFT_EAR);
            FaceLandmark rightEar = faces.get(i).getLandmark(FaceLandmark.RIGHT_EAR);
            FaceLandmark nose = faces.get(i).getLandmark(FaceLandmark.NOSE_BASE);
            if (leftEye == null || rightEye == null || leftEar == null || rightEar == null || nose == null) {
                break;
            }
            /*
             * Those landmarks are necessary, but they are not the only location we need to
             * determine. We also need the find the pupil coordinates. We have a method for finding
             * those, but it needs the original image as well as the area which contains the current
             * face. Since we are using OpenCV, we need our image in matrix form. We found this
             * before we starting looping. But, we also need to pass in the face bounding box, which
             * we can obtain from ML Kit.
             */
            android.graphics.Rect faceBoundingBox = faces.get(i).getBoundingBox();
            /*
             * Now we can pass those into our method and get back the list of coordinates. The list
             * should have a size of two, but it could be larger. We discuss that below.
             */
            ArrayList<Point> pupilCoordinates = getPupilCoordinatesWithBlobDetector(imageMatrix, faceBoundingBox);
            /*
             * We now have a list of OpenCV Point objects which correspond to the coordinates of the
             * centers of the pupils for each eye in the current face. However, we do not know which
             * Points correspond to which eye. We also need to handle the case that features that
             * are not eyes are detected and returned in the list of points. To handle these cases,
             * we have implemented an algorithm which will use the coordinates of the nose and ears
             * to isolate the point which is between them. In the event these points are not found,
             * the method will return null, so we need to make sure that we check for that and
             * terminate if that is the case.
             */
            Point leftPupilCenterPoint = isolatePupilCoordinates(pupilCoordinates, leftEar.getPosition(), nose.getPosition());
            Point rightPupilCenterPoint = isolatePupilCoordinates(pupilCoordinates, nose.getPosition(), rightEar.getPosition());
            if (leftPupilCenterPoint == null || rightPupilCenterPoint == null) {
                break;
            }
            /*
             * Now that we have our pupil coordinates, we can compute the angle between the pupil
             * and the center of the eye cavity using the method implemented in the Geometry class.
             */
            double angleFromLeftPupilToEyeCenter = Geometry.computeAngleBetweenTwoPoints(leftEye.getPosition(), leftPupilCenterPoint);
            double angleFromRightPupilToEyeCenter = Geometry.computeAngleBetweenTwoPoints(rightEye.getPosition(), rightPupilCenterPoint);
            /*
             * We use the Direction enumeration to reduce the angle into a statement of which of
             * eight directional zones the face is looking toward. This gives the user a general
             * idea of where each gaze is.
             */
            Direction leftPupilDirection = Direction.getDirection(angleFromLeftPupilToEyeCenter);
            Direction rightPupilDirection = Direction.getDirection(angleFromRightPupilToEyeCenter);

            // Stuff to do

        }
        return numberOfFacesLookingTowardCamera;
    }

}