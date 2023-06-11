package com.example.acoustictrackingdown;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;

public class KNN {
    private ArrayList<Point> points;
    private int k;

    public KNN(ArrayList<Point> points, int k) {
        this.points = points;
        this.k = k;
    }

    public ArrayList<Point> getPoints() {
        return points;
    }

    // Classify a point using the k-nearest neighbors algorithm
    public String classify(Point p) {
        Distance[] distanceResults = euclideanDistanceAll(p, points);

        // Count occurrences of each class label among the nearest neighbors
        Map<String, Integer> count = new HashMap<>();
        for (int i = 0; i < Math.min(k, distanceResults.length); i++) {
            if (distanceResults[i] != null) {
                String name = distanceResults[i].getPoint().getName();
                count.put(name, count.getOrDefault(name, 0) + 1);
            }
        }

        // Find the class label with the most occurrences (majority vote)
        String maxClass = "";
        int maxCount = 0;
        for (Map.Entry<String, Integer> entry : count.entrySet()) {
            String className = entry.getKey();
            int classCount = entry.getValue();
            if (classCount > maxCount) {
                maxClass = className;
                maxCount = classCount;
            }
        }

        return maxClass;
    }

    // Calculate the Euclidean distance between a test point and a list of training points
    public static Distance[] euclideanDistanceAll(Point testVec, ArrayList<Point> trainVec) {
        Distance[] distances = new Distance[trainVec.size()];

        for (int i = 0; i < trainVec.size(); i++) {
            // Calculate the Euclidean distance between the test point and each training point
            distances[i] = new Distance(trainVec.get(i), euclideanDistanceSingle(testVec, trainVec.get(i)));
        }

        // Sort the distances in ascending order
        Arrays.sort(distances, Comparator.comparingDouble(Distance::getDistance));

        return distances;
    }

    // Calculate the Euclidean distance between two points
    private static float euclideanDistanceSingle(Point p1, Point p2) {
        float[] v1 = p1.getVector();
        float[] v2 = p2.getVector();
        // do not need to worry about this, because while gathering data, this is already ensured
        if (v1.length != v2.length) {
            throw new RuntimeException("Vector lengths need to be the same");
        }

        float sum = 0;
        for (int i = 0; i < v1.length; i++) {
            sum += Math.pow(v1[i] - v2[i], 2);
        }

        return (float) Math.sqrt(sum);
    }
}
