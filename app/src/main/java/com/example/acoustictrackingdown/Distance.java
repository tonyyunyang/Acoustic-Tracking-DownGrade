package com.example.acoustictrackingdown;

public class Distance {
    private Point point; // The point associated with this distance
    private float distance; // The distance value

    public Distance(Point point, float distance) {
        this.point = point; // Store the point associated with this distance
        this.distance = distance; // Store the distance value
    }

    public Point getPoint() {
        return point; // Return the associated point
    }

    public float getDistance() {
        return distance; // Return the distance value
    }
}
