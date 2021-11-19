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

import org.opencv.core.Point;

/*
 * The Geometry class is a collection of static methods intended to perform simple computations we
 * will require in determining the gaze of a face. Note that many of these computations will take in
 * objects representing points as arguments, but we will be working with two different variants of
 * a point object. They are the OpenCV implementation of point, simply called Point, and the ML Kit
 * implementation of a point, called PointF. Note that both implementations of point contain public
 * class variables called x and y. This makes implementing methods with different point objects
 * easy, but may cause confusion when you read the code.
 *
 * This class was designed and implemented by Mathew and John.
 */
public class Geometry {
    /*
     * The first three methods are variations of a horizontal distance computation. The only
     * difference between them are the objects they take in as arguments, as discussed above. Since
     * these methods are strictly concerned with the horizontal distance between two points, it does
     * not matter what the y value is. The solution is simply x2 - x1.
     */
    public static double computeHorizontalDistanceBetweenTwoPoints(Point firstPoint, Point secondPoint) {
        return secondPoint.x - firstPoint.x;
    }

    public static double computeHorizontalDistanceBetweenTwoPoints(PointF firstPoint, PointF secondPoint) {
        return secondPoint.x - firstPoint.x;
    }

    public static double computeHorizontalDistanceBetweenTwoPoints(PointF firstPoint, Point secondPoint) {
        return secondPoint.x - firstPoint.x;
    }

    /*
     * The next three methods
     */
    public static double computeVerticalDistanceBetweenTwoPoints(Point firstPoint, Point secondPoint) {
        return secondPoint.y - firstPoint.y;
    }

    public static double computeVerticalDistanceBetweenTwoPoints(PointF firstPoint, PointF secondPoint) {
        return secondPoint.y - firstPoint.y;
    }

    public static double computeVerticalDistanceBetweenTwoPoints(PointF firstPoint, Point secondPoint) {
        return secondPoint.y - firstPoint.y;
    }

    /*
     * The next three methods are variations of a distance computation. The only difference between
     * them are the objects they take in as arguments, as discussed above. The method computes the
     * two differences x2 - x1 and y2 - y1 first and then returns from the
     */
    public static double computeDistanceBetweenTwoPoints(Point firstPoint, Point secondPoint) {
        /*
         * A simple method to compute the difference between two points in a 2D geometric space.
         * This method takes two OpenCV Point objects as its arguments.
         */
        double differenceInX = secondPoint.x - firstPoint.x;
        double differenceInY = secondPoint.y - firstPoint.y;
        return Math.sqrt(Math.pow(differenceInX, 2) - Math.pow(differenceInY, 2.0));
    }

    public static double computeDistanceBetweenTwoPoints(PointF firstPoint, PointF secondPoint) {
        /*
         * A simple function to compute the difference between two points in a 2D geometric space.
         * This function takes two ML Kit PointF objects as its arguments.
         */
        double differenceInX = secondPoint.x - firstPoint.x;
        double differenceInY = secondPoint.y - firstPoint.y;
        return Math.sqrt(Math.pow(differenceInX, 2) - Math.pow(differenceInY, 2.0));
    }

    public static double computeDistanceBetweenTwoPoints(PointF firstPoint, Point secondPoint) {
        /*
         * A simple function to compute the difference between two points in a 2D geometric space.
         * This function takes a PointF object and a Point object as its arguments.
         */
        double differenceInX = secondPoint.x - firstPoint.x;
        double differenceInY = secondPoint.y - firstPoint.y;
        return Math.sqrt(Math.pow(differenceInX, 2) - Math.pow(differenceInY, 2.0));
    }



}
