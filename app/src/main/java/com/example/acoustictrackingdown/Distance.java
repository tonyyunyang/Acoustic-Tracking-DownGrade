package com.example.acoustictrackingdown;

public class Distance {
    private Point point; // The point associated with this distance
    private float euclideanDistance; // The distance value

    public Distance(Point point, float euclideanDistance) {
        this.point = point; // Store the point associated with this distance
        this.euclideanDistance = euclideanDistance; // Store the distance value
    }

    public Point getPoint() {
        return point; // Return the associated point
    }

    public float getEuclideanDistance() {
        return euclideanDistance; // Return the distance value
    }
}
