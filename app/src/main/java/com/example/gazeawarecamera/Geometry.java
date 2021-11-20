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

import org.opencv.core.Point;

/*
 * The Geometry class is a collection of static methods intended to perform simple computations we
 * will require in determining the gaze of a face. Note that many of these computations will take in
 * objects representing points as arguments, but we will be working with two different variants of
 * a point object. They are the OpenCV implementation of point, simply called Point, and the ML Kit
 * implementation of a point, called PointF. Note that both implementations of point contain public
 * class variables called x and y. This makes implementing methods with different point objects
 * easy, but may cause confusion when you read the code.
 */
public class Geometry {
    /*
     * The first three methods are variations of a horizontal distance computation. The only
     * difference between them are the objects they take in as arguments, as discussed above. Since
     * these methods are strictly concerned with the horizontal distance between two points, it does
     * not matter what the y value is. The solution is simply x2 - x1.
     */
    public static double computeHorizontalDistanceBetweenTwoPoints(@NonNull Point firstPoint, @NonNull Point secondPoint) {
        /*
         * A simple method to compute the horizontal distance between two points in a 2D geometric
         * space. This method takes two OpenCV Point objects as its arguments.
         */
        return secondPoint.x - firstPoint.x;
    }

    public static double computeHorizontalDistanceBetweenTwoPoints(@NonNull PointF firstPoint, @NonNull PointF secondPoint) {
        /*
         * A simple method to compute the horizontal distance between two points in a 2D geometric
         * space. This method takes two Ml Kit Point objects as its arguments.
         */
        return secondPoint.x - firstPoint.x;
    }

    public static double computeHorizontalDistanceBetweenTwoPoints(@NonNull PointF firstPoint, @NonNull Point secondPoint) {
        /*
         * A simple method to compute the horizontal distance between two points in a 2D geometric
         * space. This method takes one ML Kit PointF object as firstPoint and one OpenCV Point
         * object as secondPoint. In this method, we will return the absolute value of the
         * difference. This is because if the PointF object has a greater x value than the Point
         * object, you will always get a negative number. Since we are only concerned with the
         * scalar distance anyhow, it doesn't really matter.
         */
        return Math.abs(secondPoint.x - firstPoint.x);
    }

    /*
     * The first three methods are variations of a vertical distance computation. The only
     * difference between them are the objects they take in as arguments, as discussed above. Since
     * these methods are strictly concerned with the vertical distance between two points, it does
     * not matter what the y value is. The solution is simply y2 - y1.
     */
    public static double computeVerticalDistanceBetweenTwoPoints(@NonNull Point firstPoint, @NonNull Point secondPoint) {
        /*
         * A simple method to compute the vertical distance between two points in a 2D geometric
         * space. This method takes two OpenCV Point objects as its arguments.
         */
        return secondPoint.y - firstPoint.y;
    }

    public static double computeVerticalDistanceBetweenTwoPoints(@NonNull PointF firstPoint, @NonNull PointF secondPoint) {
        /*
         * A simple method to compute the vertical distance between two points in a 2D geometric
         * space. This method takes two Ml Kit Point objects as its arguments.
         */
        return secondPoint.y - firstPoint.y;
    }

    public static double computeVerticalDistanceBetweenTwoPoints(@NonNull PointF firstPoint, @NonNull Point secondPoint) {
        /*
         * A simple method to compute the vertical distance between two points in a 2D geometric
         * space. This method takes one ML Kit PointF object as firstPoint and one OpenCV Point
         * object as secondPoint. In this method, we will return the absolute value of the
         * difference. This is because if the PointF object has a greater y value than the Point
         * object, you will always get a negative number. Since we are only concerned with the
         * scalar distance anyhow, it doesn't really matter.
         */
        return Math.abs(secondPoint.y - firstPoint.y);
    }

    /*
     * The next three methods are variations of a distance computation. The only difference between
     * them are the objects they take in as arguments, as discussed above. The method computes the
     * two differences x2 - x1 and y2 - y1 first and then returns from the
     */
    public static double computeDistanceBetweenTwoPoints(@NonNull Point firstPoint, @NonNull Point secondPoint) {
        /*
         * A simple method to compute the distance between two points in a 2D geometric space. This
         * method takes two OpenCV Point objects as its arguments.
         */
        double differenceInX = secondPoint.x - firstPoint.x;
        double differenceInY = secondPoint.y - firstPoint.y;
        return Math.sqrt(Math.pow(differenceInX, 2) - Math.pow(differenceInY, 2.0));
    }

    public static double computeDistanceBetweenTwoPoints(@NonNull PointF firstPoint, @NonNull PointF secondPoint) {
        /*
         * A simple method to compute the distance between two points in a 2D geometric space. This
         * method takes two Ml Kit Point objects as its arguments.
         */
        double differenceInX = secondPoint.x - firstPoint.x;
        double differenceInY = secondPoint.y - firstPoint.y;
        return Math.sqrt(Math.pow(differenceInX, 2) - Math.pow(differenceInY, 2.0));
    }

    public static double computeDistanceBetweenTwoPoints(@NonNull PointF firstPoint, @NonNull Point secondPoint) {
        /*
         * A simple method to compute the distance between two points in a 2D geometric space. This
         * method takes one ML Kit PointF object as firstPoint and one OpenCV Point object as
         * secondPoint.
         */
        double differenceInX = secondPoint.x - firstPoint.x;
        double differenceInY = secondPoint.y - firstPoint.y;
        return Math.sqrt(Math.pow(differenceInX, 2) - Math.pow(differenceInY, 2.0));
    }

    /*
     * The next three methods compute the angle between two points. The three variations of this
     * method, once more, correspond to the different point object combinations we may use. The
     * calculation is made by considering one point to be the center of the circle from which the
     * angle is being considered. To that end, a difference in x and y are computed which transform
     * the second point the appropriate distances with respect to center. Then, the inverse tangent
     * of the new Y divided by the new X yields the angle, which is returned after a covnersion to
     * degrees.
     */
    public static double computeAngleBetweenTwoPoints(@NonNull Point centerPoint, @NonNull Point relativePoint) {
        /*
         * This method takes two OpenCV Point objects as its arguments.
         */
        double differenceInY = relativePoint.y - centerPoint.y;
        double differenceInX = relativePoint.x - centerPoint.x;
        double answerInRadians = Math.atan2(differenceInY, differenceInX);
        return Math.toDegrees(answerInRadians);
    }

    public static double computeAngleBetweenTwoPoints(@NonNull PointF centerPoint, @NonNull PointF relativePoint) {
        /*
         * This method takes two ML Kit PointF objects as its arguments.
         */
        double differenceInY = relativePoint.y - centerPoint.y;
        double differenceInX = relativePoint.x - centerPoint.x;
        double answerInRadians = Math.atan2(differenceInY, differenceInX);
        return Math.toDegrees(answerInRadians);
    }

    public static double computeAngleBetweenTwoPoints(@NonNull PointF centerPoint, @NonNull Point relativePoint) {
        /*
         * This method takes one ML Kit PointF object as centerPoint and one OpenCV Point object as
         * relativePoint.
         */
        double differenceInY = relativePoint.y - centerPoint.y;
        double differenceInX = relativePoint.x - centerPoint.x;
        double answerInRadians = Math.atan2(differenceInY, differenceInX);
        return Math.toDegrees(answerInRadians);
    }

}
