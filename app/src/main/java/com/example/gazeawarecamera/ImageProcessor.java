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

    public static Object changeRect(Object rectangle) {
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
            int left = ((Rect) rectangle).x;
            int top = ((Rect) rectangle).y;
            int right = ((Rect) rectangle).x + ((Rect) rectangle).width;
            int bottom = ((Rect) rectangle).y + ((Rect) rectangle).height;
            return new android.graphics.Rect(left, top, right, bottom);
        } else if (rectangle instanceof android.graphics.Rect) {
            /*
             * The OpenCV Core Rect constructor also has four arguments. They are x, y, width, and
             * height. The x and y arguments represent the top left corner of the rectangle. The
             * Android Rect does not give us direct access to any of the corners of the rectangles,
             * but it does give us the center x and y coordinates from which we can easily find the
             * corners using width and height.
             */
            int width = ((android.graphics.Rect) rectangle).width();
            int height = ((android.graphics.Rect) rectangle).height();
            int x = ((android.graphics.Rect) rectangle).centerX() - (width / 2);
            int y = ((android.graphics.Rect) rectangle).centerY() - (height / 2);
            return new org.opencv.core.Rect(x, y, width, height);
        } else {
            return null;
        }
    }


    public static void processImage(Mat originalImage, Rect faceBoundingBox, Point rightEye, Point leftEye) {
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

            System.out.println("Left eye is in this box: " + eyeBoundingBoxes[i].contains(leftEye));
            System.out.println("Right eye is in this box: " + eyeBoundingBoxes[i].contains(rightEye));

            Mat greyEye = new Mat(greyImage, eyeBoundingBoxes[i]);
            Mat binaryEye = new Mat();

            // elements used for erode & dilation kernel. values used from https://www.tutorialspoint.com/java_dip/eroding_dilating.htm
            int erosion_size = 5;
            int dilation_size = 5;
            Point defAnchor = new Point(-1,-1);
            Mat erodeElement = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, new Size(2 * erosion_size + 1, 2 * erosion_size + 1));
            Mat dilationElement = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, new Size(2 * dilation_size + 1, 2 * dilation_size + 1));

            Imgproc.adaptiveThreshold(greyEye, binaryEye, 255, Imgproc.ADAPTIVE_THRESH_MEAN_C, Imgproc.THRESH_BINARY, 11, 10);
            Imgproc.erode(binaryEye, binaryEye, erodeElement, defAnchor, 2);
            Imgproc.dilate(binaryEye, binaryEye, dilationElement, defAnchor, 4);
            Imgproc.medianBlur(binaryEye, binaryEye, 5);
            MatOfKeyPoint keyPoints = new MatOfKeyPoint();
            detector.detect(binaryEye, keyPoints);

            KeyPoint[] keyPointsArray = keyPoints.toArray();
            for (int j = 0; j < keyPointsArray.length; j++) {
                System.out.println("Keypoint: " + keyPointsArray[j].pt.toString());
            }
        }
    }


}
