package com.example.acoustictrackingdown;

import java.util.ArrayList;

public class Point {
    private String name; // The name or label associated with this point
    private ArrayList<Float> vector; // The feature vector representing the point's characteristics

    public Point(String name, ArrayList<Float> vector) {
        this.name = name; // Store the name or label provided in the constructor
        this.vector = vector; // Store the feature vector provided in the constructor
    }

    public float[] getVector() {
        float[] floatArray = new float[vector.size()]; // Create a float array to hold the feature vector values
        int index = 0;
        for (Float value : vector) {
            floatArray[index++] = value; // Convert each Float value to float and add it to the array
        }
        return floatArray; // Return the converted feature vector as a float array
    }

    public int getSize() {
        return vector.size(); // Return the number of features in the vector (size of the list)
    }

    public String getName() {
        return name; // Return the name or label associated with the point
    }
}
