package com.example.acoustictrackingdown;

import static androidx.constraintlayout.helper.widget.MotionEffect.TAG;

import android.content.Context;
import android.graphics.Bitmap;
import android.util.Log;

import org.pytorch.IValue;
import org.pytorch.LiteModuleLoader;
import org.pytorch.Module;
import org.pytorch.Tensor;
import org.pytorch.torchvision.TensorImageUtils;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public class ImageClassifier {
    private Module mModule;

    public ImageClassifier(Context context, String modelPath) {
        // Load the PyTorch model
        mModule = LiteModuleLoader.load(assetFilePath(context, modelPath));
    }

    public int classifyImage(Bitmap image) {
        // Preprocess the image
        Tensor inputTensor = TensorImageUtils.bitmapToFloat32Tensor(image,
                TensorImageUtils.TORCHVISION_NORM_MEAN_RGB,
                TensorImageUtils.TORCHVISION_NORM_STD_RGB);
//        Tensor inputTensor = TensorImage.fromBitmap(image);

        // Run inference
        Tensor outputTensor = mModule.forward(IValue.from(inputTensor)).toTensor();

        // Get the predicted class index
        float[] scores = outputTensor.getDataAsFloatArray();
        int maxIndex = -1;
        float maxScore = -Float.MAX_VALUE;
        for (int i = 0; i < scores.length; i++) {
            if (scores[i] > maxScore) {
                maxIndex = i + 1;
                maxScore = scores[i];
            }
        }

        StringBuilder scoresStr = new StringBuilder();
        for(int i = 0; i < scores.length; i++) {
            scoresStr.append("Score ").append(i).append(": ").append(scores[i]);
            if (i != scores.length - 1) {  // Don't add a comma after the last score
                scoresStr.append(", ");
            }
        }
        // Log the scores and the number of scores
        Log.i(TAG, scoresStr.toString() + ", number of scores: " + scores.length);

        return maxIndex;
    }

    public static String assetFilePath(Context context, String assetName) {
        File file = new File(context.getFilesDir(), assetName);
        if (file.exists() && file.length() > 0) {
            return file.getAbsolutePath();
        }

        try (InputStream is = context.getAssets().open(assetName)) {
            try (OutputStream os = new FileOutputStream(file)) {
                byte[] buffer = new byte[2 * 1024];
                int read;
                while ((read = is.read(buffer)) != -1) {
                    os.write(buffer, 0, read);
                }
                os.flush();
            }
            return file.getAbsolutePath();
        } catch (IOException e) {
            // Error occurred while processing the asset file
            Log.e(TAG, "Error process asset " + assetName + " to file path");
        }
        return null;
    }
}
