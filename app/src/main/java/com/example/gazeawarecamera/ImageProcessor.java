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

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.PointF;
import android.media.FaceDetector;
import android.media.Image;
import android.util.Log;

import androidx.annotation.NonNull;

import com.google.mlkit.vision.face.FaceLandmark;

import org.opencv.android.Utils;
import org.opencv.core.Core;
import org.opencv.core.CvException;
import org.opencv.core.CvType;
import org.opencv.core.KeyPoint;
import org.opencv.core.Mat;
import org.opencv.core.MatOfKeyPoint;
import org.opencv.core.MatOfRect;
import org.opencv.core.Point;
import org.opencv.core.Rect;
import org.opencv.core.Size;
import org.opencv.features2d.SimpleBlobDetector;
import org.opencv.features2d.SimpleBlobDetector_Params;
import org.opencv.imgproc.Imgproc;
import org.opencv.objdetect.CascadeClassifier;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.ArrayList;


public class ImageProcessor {

    /*
     * Potential Solutions
     * 1) Bounding the search area using a rectangle. We need to determine the size of the box. A
     *    fixed size is limiting because it may not work if you are too close or too far from the
     *    camera.
     * 2) MSER algorithm - maybe in OpenCV?
     * 3) Detect iris before detecting pupils. The iris is easier to locate and gives bounds for the
     *    pupils which are guaranteed to be in the iris.
     * 4) Making the hough circle algorithm incredibly sensitive to everything. GazeDetection
     *    is already programmed to weed out points which are not the pupils. However, increasing the
     *    sensitivity of this algorithm doesn't necessarily mean we will get the pupils. Also, we
     *    aren't yet sure how this can be done.
     */


    public static Mat convertYUVtoMat(@NonNull Image originalImage) {
        /*
         * https://stackoverflow.com/questions/58102717/android-camerax-analyzer-image-with-format-yuv-420-888-to-opencv-mat
         */
        Image.Plane[] planes = originalImage.getPlanes();
        ByteBuffer yBuffer = planes[0].getBuffer();
        ByteBuffer uBuffer = planes[1].getBuffer();
        ByteBuffer vBuffer = planes[2].getBuffer();

        int ySize = yBuffer.remaining();
        int uSize = uBuffer.remaining();
        int vSize = vBuffer.remaining();

        byte[] nv21 = new byte[ySize + uSize + vSize];
        yBuffer.get(nv21, 0, ySize);
        vBuffer.get(nv21, ySize, vSize);
        uBuffer.get(nv21, ySize + vSize, uSize);

        Mat yuv = new Mat(originalImage.getHeight() + originalImage.getHeight()/2, originalImage.getWidth(), CvType.CV_8UC1);
        yuv.put(0, 0, nv21);
        Mat rgb = new Mat();
        Imgproc.cvtColor(yuv, rgb, Imgproc.COLOR_YUV2RGB_NV21, 3);

        return rgb;
    }

    /*
     * https://docs.opencv.org/3.4/d4/d70/tutorial_hough_circle.html
     */
    public static ArrayList<Point> getListOfAllCircleCenterPoints(Mat matrix) {
        /*
         *
         */
        Mat gray = new Mat();
        Imgproc.cvtColor(matrix, gray, Imgproc.COLOR_BGR2GRAY);

        //Imgproc.morphologyEx(gray, gray, 2, Imgproc.getStructuringElement(Imgproc.MORPH_RECT, new Size(5,5)));

        //Imgproc.threshold(gray, gray, 127, 255, Imgproc.THRESH_OTSU);

        Imgproc.medianBlur(gray, gray, 5);

        Mat circles = new Mat();
        Imgproc.HoughCircles(gray, circles, Imgproc.HOUGH_GRADIENT, 1.0,
                (double) gray.rows()/16, // change this value to detect circles with different distances to each other
                100.0, 30.0, 1, 30); // change the last two parameters
        // (min_radius & max_radius) to detect larger circles

        ArrayList<Point> pupilCenterCoordinates = new ArrayList<>();
        for (int i = 0; i < circles.cols(); i++) {
            double[] c = circles.get(0, i);
            Point center = new Point(Math.round(c[0]), Math.round(c[1]));
            pupilCenterCoordinates.add(center);
        }

        return pupilCenterCoordinates;
    }



    public static ArrayList<Point> getCircles(Mat imageMatrix, Rect boundary) {

        Mat ROI = new Mat(imageMatrix, boundary);
        Mat gray = new Mat();

        Imgproc.cvtColor(ROI, gray, Imgproc.COLOR_BGR2GRAY);

        Imgproc.medianBlur(gray, gray, 5);

        //Imgproc.morphologyEx(gray, gray, 2, Imgproc.getStructuringElement(Imgproc.MORPH_RECT, new Size(5,5)));

        //Imgproc.adaptiveThreshold(gray, gray, 255, Imgproc.ADAPTIVE_THRESH_MEAN_C, Imgproc.THRESH_BINARY, 15, 40);

        Mat circles = new Mat();
        Imgproc.HoughCircles(gray, circles, Imgproc.HOUGH_GRADIENT, 1.0, (double) gray.rows()/20, 100.0, 30.0, 0, 0);

        ArrayList<Point> circleCenterPoints = new ArrayList<>();
        for (int i = 0; i < circles.cols(); i++) {
            double[] c = circles.get(0, i);
            Point center = new Point(Math.round(c[0]), Math.round(c[1]));
            circleCenterPoints.add(center);
        }

        for (int i = 0; i < circleCenterPoints.size(); i++) {
            System.out.println(circleCenterPoints.get(i).toString());
        }

        return circleCenterPoints;
    }


    public static void processImage(Mat originalImage, Rect faceBoundingBox) {
        System.out.println("Face bounding box: " + faceBoundingBox.toString());

        Mat greyImage = new Mat();
        Imgproc.cvtColor(originalImage, greyImage, Imgproc.COLOR_BGR2GRAY);
        Mat greyFace = new Mat(greyImage, faceBoundingBox);

        MatOfRect eyes = new MatOfRect();
        eyeCascade.detectMultiScale(greyFace, eyes, 1.05, 5);
        Rect[] eyeBoundingBoxes = eyes.toArray();
        System.out.println("Number of eyes detected: " + eyeBoundingBoxes.length);

        SimpleBlobDetector_Params parameters = new SimpleBlobDetector_Params();
        parameters.set_filterByArea(true);
        SimpleBlobDetector detector = SimpleBlobDetector.create(parameters);

        for (int i = 0; i < eyeBoundingBoxes.length; i++) {
            System.out.println("Potential eye bounding box: " + eyeBoundingBoxes[i].toString());
            Mat greyEye = new Mat(greyImage, eyeBoundingBoxes[i]);
            Mat binaryEye = new Mat();
            Imgproc.adaptiveThreshold(greyEye, binaryEye, 255, Imgproc.ADAPTIVE_THRESH_MEAN_C, Imgproc.THRESH_BINARY, 11, 12);
            MatOfKeyPoint keyPoints = new MatOfKeyPoint();
            detector.detect(binaryEye, keyPoints);

            KeyPoint[] keyPointsArray = keyPoints.toArray();
            for (int j = 0; j < keyPointsArray.length; j++) {
                System.out.println("Keypoint: " + keyPointsArray[j].pt.toString());
            }
        }
    }


}
