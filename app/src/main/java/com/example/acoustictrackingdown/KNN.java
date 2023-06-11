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

    // Classify a point using the KNN
    // below is a fancy method
    public String classifyLocation(Point p) {
        Distance[] distanceResults = euclideanDistanceWholeArray(p, points);

        // Count occurrences of each class label among the nearest neighbors
        Map<String, Integer> count = new HashMap<>();
        for (int i = 0; i < Math.min(k, distanceResults.length); i++) {
            Distance distance = distanceResults[i];
            if (distance != null) {
                String name = distance.getPoint().getLabel();
                count.merge(name, 1, Integer::sum);
            }
        }

        // Find the class label with the most occurrences (majority vote)
        String maxClass = count.entrySet()
                .stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse("");

        return maxClass;
    }

//    public String classifyLocation(Point p) {
//        Distance[] distanceResults = euclideanDistanceAll(p, points);
//
//        // Count occurrences of each class label among the nearest neighbors
//        Map<String, Integer> count = new HashMap<>();
//        for (int i = 0; i < Math.min(k, distanceResults.length); i++) {
//            if (distanceResults[i] != null) {
//                String name = distanceResults[i].getPoint().getLabel();
//                count.put(name, count.getOrDefault(name, 0) + 1);
//            }
//        }
//
//        // Find the class label with the most occurrences (majority vote)
//        String maxClass = "";
//        int maxCount = 0;
//        for (Map.Entry<String, Integer> entry : count.entrySet()) {
//            String className = entry.getKey();
//            int classCount = entry.getValue();
//            if (classCount > maxCount) {
//                maxClass = className;
//                maxCount = classCount;
//            }
//        }
//
//        return maxClass;
//    }

    // Calculate the Euclidean distance between a test point and a list of training points
    public static Distance[] euclideanDistanceWholeArray(Point testVec, ArrayList<Point> trainVec) {
        Distance[] distances = new Distance[trainVec.size()];

        for (int i = 0; i < trainVec.size(); i++) {
            // Calculate the Euclidean distance between the test point and each training point
            distances[i] = new Distance(trainVec.get(i), euclideanDistance(testVec, trainVec.get(i)));
        }

        // Sort the distances in ascending order
        Arrays.sort(distances, Comparator.comparingDouble(Distance::getEuclideanDistance));

        return distances;
    }

    // Calculate the Euclidean distance between two points
    private static float euclideanDistance(Point p1, Point p2) {
        float[] v1 = p1.getBssidRSSI();
        float[] v2 = p2.getBssidRSSI();

        float sum = 0;
        for (int i = 0; i < v1.length; i++) {
            sum += Math.pow(v1[i] - v2[i], 2);
        }

        return (float) Math.sqrt(sum);
    }
}
