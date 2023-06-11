package com.example.acoustictrackingdown;

import java.util.ArrayList;

public class Point {
    private String label; // The name or label associated with this point
    private ArrayList<Float> bssidRSSI; // The feature vector representing the point's characteristics

    public Point(String label, ArrayList<Float> bssidRSSI) {
        this.label = label; // Store the name or label provided in the constructor
        this.bssidRSSI = bssidRSSI; // Store the feature vector provided in the constructor
    }

    public float[] getBssidRSSI() {
        float[] floatArray = new float[bssidRSSI.size()]; // Create a float array to hold the feature vector values
        int index = 0;
        for (Float value : bssidRSSI) {
            floatArray[index++] = value; // Convert each Float value to float and add it to the array
        }
        return floatArray; // Return the converted feature vector as a float array
    }

    public String getLabel() {
        return label; // Return the name or label associated with the point
    }
}
