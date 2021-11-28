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
import android.graphics.BitmapFactory;
import android.graphics.ImageFormat;
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
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.features2d.SimpleBlobDetector;
import org.opencv.features2d.SimpleBlobDetector_Params;
import org.opencv.imgproc.Imgproc;


public class GazeDetector {

    /*
     * We need to determine the center point of the image. We are assuming that the camera is in
     * roughly, if not exactly, the center of the image. So, in order to determine the center, we
     * can divide the horizontal and vertical pixel counts by 2. We only need to perform this
     * calculation once since the resolution of the image will not change while the application is
     * running. Therefore, we have made this PointF object final and private to the class.
     */
    private static final PointF IMAGE_CENTER_POINT = new PointF((float) MainActivity.PIXEL_COUNT_HORIZONTAL / 2, (float) MainActivity.PIXEL_COUNT_VERTICAL / 2);

    /*
     * We are also going to define horizontal and vertical tolerance values which will account for
     * margin of error when making certain calculations. Specifically, we will apply these in the
     * landmark version of the algorithm.
     */
    private static final double HORIZONTAL_TOLERANCE = 20.0;
    private static final double VERTICAL_TOLERANCE = 20.0;

    DrawingListener drawingListener;

    public GazeDetector(DrawingListener drawingListener) {
        this.drawingListener = drawingListener;
    }

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

    private Object changeRect(Object rectangle) {
        /*
         * A method that converts from Android Graphics Rect to OpenCV Core Rect. These classes
         * represent the same thing, but we often need to switch between them. If an object is
         * passed in that is neither version of the Rect object, null will be returned.
         */
        if (rectangle instanceof org.opencv.core.Rect) {
            /*
             * The Android Graphics Rect constructor has four arguments. They are left, top, right,
             * and bottom. The left and top values represent the x and y coordinates represent the
             * top left corner of the rectangle, while the right and bottom values represent the
             * bottom right corner of the rectangle. We obtain left and top directly from the
             * OpenCV Rect, and add width and height to those values to obtain bottom and right.
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

    private Mat imageToGreyMatrix(Image image) {
        /*
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


    public void processImage(Mat greyImage, Rect faceBoundingBox) {

        ArrayList<Rect> rectangles = new ArrayList<Rect>();
        rectangles.add(faceBoundingBox);

        Mat greyFace = new Mat(greyImage, (org.opencv.core.Rect) changeRect(faceBoundingBox));

        // debugging
//        Bitmap bmpGrey = null;
//        Bitmap bmpFace = null;
//        bmpGrey = Bitmap.createBitmap(greyImage.cols(), greyImage.rows(), Bitmap.Config.ARGB_8888);
//        Utils.matToBitmap(greyImage, bmpGrey);
//        bmpFace = Bitmap.createBitmap(greyFace.cols(), greyFace.rows(), Bitmap.Config.ARGB_8888);
//        Utils.matToBitmap(greyFace, bmpFace);

        MatOfRect eyes = new MatOfRect();
        eyeCascade.detectMultiScale(greyFace, eyes, 1.05, 5);
        org.opencv.core.Rect[] eyeBoundingBoxes = eyes.toArray();

        SimpleBlobDetector_Params parameters = new SimpleBlobDetector_Params();
        parameters.set_filterByCircularity(true);
        parameters.set_minCircularity((float) 0.5);
        parameters.set_maxCircularity((float) 1.0);
        SimpleBlobDetector detector = SimpleBlobDetector.create(parameters);

        for (int i = 0; i < eyeBoundingBoxes.length; i++) {

            rectangles.add((Rect) changeRect(eyeBoundingBoxes[i]));

            Mat greyEye = new Mat(greyFace, eyeBoundingBoxes[i]);
            // Debugging
            Bitmap bmpEye = null;
            bmpEye = Bitmap.createBitmap(greyEye.cols(), greyEye.rows(), Bitmap.Config.ARGB_8888);
            Utils.matToBitmap(greyEye, bmpEye);


            Mat binaryEye = new Mat();

            // elements used for erode & dilation kernel. values used from https://www.tutorialspoint.com/java_dip/eroding_dilating.htm
            int erosion_size = 3;
            int dilation_size = 3;
            Point defAnchor = new Point(-1, -1);
            Mat erodeElement = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, new Size(2 * erosion_size + 1, 2 * erosion_size + 1));
            Mat dilationElement = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, new Size(2 * dilation_size + 1, 2 * dilation_size + 1));

            Imgproc.adaptiveThreshold(greyEye, binaryEye, 255, Imgproc.ADAPTIVE_THRESH_MEAN_C, Imgproc.THRESH_BINARY, 19, 3);
            Imgproc.erode(binaryEye, binaryEye, erodeElement, defAnchor, 1);
            Imgproc.dilate(binaryEye, binaryEye, dilationElement, defAnchor, 2);
            //Imgproc.bilateralFilter(binaryEye, binaryEye, 7, 75, 75, Core.BORDER_DEFAULT);
            Imgproc.medianBlur(binaryEye, binaryEye, 5);
            MatOfKeyPoint keyPoints = new MatOfKeyPoint();
            detector.detect(binaryEye, keyPoints);


            Bitmap bmpProcessedEye = null;
            bmpProcessedEye = Bitmap.createBitmap(binaryEye.cols(), binaryEye.rows(), Bitmap.Config.ARGB_8888);
            Utils.matToBitmap(binaryEye, bmpProcessedEye);

            drawingListener.drawRectangles(rectangles);

            KeyPoint[] keyPointsArray = keyPoints.toArray();
            for (int j = 0; j < keyPointsArray.length; j++) {
                System.out.println("Keypoint: " + keyPointsArray[j].pt.toString());
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
    public Point isolatePupilCoordinates(ArrayList<Point> points, PointF minimumX, PointF maximumX) {
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
    public int detectGazesWithAngles(@NonNull List<Face> faces, ArrayList<Point> pupilCenterCoordinates) {
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
             *
             *
             */
            double angleFromLeftPupilToEyeCenter = Geometry.computeAngleBetweenTwoPoints(leftEye.getPosition(), leftPupilCenterCoordinates);
            double angleFromRightPupilToEyeCenter = Geometry.computeAngleBetweenTwoPoints(rightEye.getPosition(), rightPupilCenterCoordinates);

            Direction leftPupilDirection = Direction.getDirection(angleFromLeftPupilToEyeCenter);
            Direction rightPupilDirection = Direction.getDirection(angleFromRightPupilToEyeCenter);
        }
        return numberOfFacesLookingTowardCamera;
    }


    public int detectGazesWithLandmarks(@NonNull List<Face> faces, Image image) {
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
             * This portion of the algorithm is the same as detectGazesWithAngles.
             */
            try {
                if (faces.get(i).getLeftEyeOpenProbability() < 0.9 || faces.get(i).getRightEyeOpenProbability() < 0.9) {
                    break;
                }
            } catch (NullPointerException e) {
                break;
            }
            /*
             * This portion of the algorithm is the same as detectGazesWithAngles.
             */
            FaceLandmark leftEye = faces.get(i).getLandmark(FaceLandmark.LEFT_EYE);
            FaceLandmark rightEye = faces.get(i).getLandmark(FaceLandmark.RIGHT_EYE);
            FaceLandmark leftEar = faces.get(i).getLandmark(FaceLandmark.LEFT_EAR);
            FaceLandmark rightEar = faces.get(i).getLandmark(FaceLandmark.RIGHT_EAR);
            FaceLandmark nose = faces.get(i).getLandmark(FaceLandmark.NOSE_BASE);
            if (leftEye == null || rightEye == null || leftEar == null || rightEar == null || nose == null) {
                break;
            }

            android.graphics.Rect faceBoundingBox = faces.get(i).getBoundingBox();


            Mat imageMatrix = imageToGreyMatrix(image);

//            if ((faceBoundingBoxAsOpenCVRect.x + faceBoundingBoxAsOpenCVRect.width > MainActivity.PIXEL_COUNT_VERTICAL) ||
//                    (faceBoundingBoxAsOpenCVRect.y + faceBoundingBoxAsOpenCVRect.height > MainActivity.PIXEL_COUNT_HORIZONTAL)) {
//                break;
//            }

            Point leftEyeCoordinate = new Point(leftEye.getPosition().x, leftEye.getPosition().y);
            Point rightEyeCoordinate = new Point(rightEye.getPosition().x, rightEye.getPosition().y);

            processImage(imageMatrix, faceBoundingBox);


            //ArrayList<Point> potentialLeftPupils = ImageProcessor.getCircles(imageMatrix, leftRectangle);
            //ArrayList<Point> potentialRightPupils = ImageProcessor.getCircles(imageMatrix, rightRectangle);

            //Point leftPupilCenterPoint = isolatePupilCoordinates(potentialLeftPupils, leftEar.getPosition(), nose.getPosition());
            //Point rightPupilCenterPoint = isolatePupilCoordinates(potentialRightPupils, nose.getPosition(), rightEar.getPosition());

        }
        return numberOfFacesLookingTowardCamera;
    }

}