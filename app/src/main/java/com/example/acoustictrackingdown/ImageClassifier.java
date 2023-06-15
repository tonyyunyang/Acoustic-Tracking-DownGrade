package com.example.acoustictrackingdown;

import static androidx.constraintlayout.helper.widget.MotionEffect.TAG;

import static java.util.Arrays.sort;

import android.content.Context;
import android.graphics.Bitmap;
import android.util.Log;

import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OnnxValue;
import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtException;
import ai.onnxruntime.OrtSession;

import android.graphics.Bitmap;
import android.graphics.Color;

import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtException;
import java.io.InputStream;

import org.pytorch.Module;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.FloatBuffer;
import java.util.Collections;
import java.util.Optional;

public class ImageClassifier {
    private Module mModule;
    OrtSession session;
    OrtEnvironment envv;

    public ImageClassifier(Context context, String modelPath, OrtEnvironment env) throws OrtException, IOException {
//        mModule = LiteModuleLoader.load(assetFilePath(context, modelPath));

        envv = OrtEnvironment.getEnvironment();;
//        OrtSession.SessionOptions options = new OrtSession.SessionOptions();
////        session = envv.createSession("new_model_architecture.onnx", options);
//
//        // Load the ONNX model from assets
//        InputStream modelStream = context.getAssets().open("new_model_architecture.onnx");
//
//        System.out.println("model stream: " + modelStream);
//        // Create OrtSession from the model stream
//        OrtSession session = envv.createSession(modelStream.toString(), options);
//
//        // Close the model stream
//        modelStream.close();
//
//        // Create an OrtEnvironment
//        OrtEnvironment environment = OrtEnvironment.getEnvironment();

        // Create SessionOptions
        OrtSession.SessionOptions sessionOptions = new OrtSession.SessionOptions();

        // Copy the ONNX model from assets to internal storage
        File modelFile = copyModelFromAssets(context, "new_model_architecture.onnx");

        // Create OrtSession from the model file path
        session = envv.createSession(modelFile.getAbsolutePath(), sessionOptions);

    }

    private File copyModelFromAssets(Context context, String fileName) throws IOException {
        File modelFile = new File(context.getCacheDir(), fileName);

        // Copy the model from assets to internal storage
        try (InputStream inputStream = context.getAssets().open(fileName);
             OutputStream outputStream = new FileOutputStream(modelFile)) {
            byte[] buffer = new byte[4 * 1024];
            int read;
            while ((read = inputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, read);
            }
            outputStream.flush();
        }

        return modelFile;
    }

    public int[] classifyImage(Bitmap image, OrtEnvironment env) throws OrtException {
        // Preprocess the image
//        Tensor inputTensor = TensorImageUtils.bitmapToFloat32Tensor(image,
//                TensorImageUtils.TORCHVISION_NORM_MEAN_RGB, TensorImageUtils.TORCHVISION_NORM_STD_RGB);

        OnnxTensor inputTensor = createTensorFromBitmap(image);

        OrtSession.Result output = session.run(Collections.singletonMap(session.getInputNames().iterator().next(), inputTensor));

        Optional<OnnxValue> outputValue = output.get(session.getOutputNames().iterator().next());
        OnnxTensor outputTensor = (OnnxTensor) outputValue.get();

//        System.out.println(outputTensor);
        float[] scores = {0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0};
        for(float[] array : (float[][])outputTensor.getValue()) {
//            System.out.println("Array: " + counter);
            for (int i = 0; i < 16; i++) {
//                System.out.print(f + ", ");
                scores[i] = array[i];
            }
            break;
        }

        // Perform postprocessing on the output data, e.g., argmax, thresholding, etc.


        // Run inference
//        Tensor outputTensor = mModule.forward(IValue.from(inputTensor)).toTensor();

//        // Get the predicted class index
//        float[] scores = outputTensor.getDataAsFloatArray();

        StringBuilder scoresStr = new StringBuilder();
        for(int j = 0; j < scores.length; j++) {
            scoresStr.append("Score ").append((j+1)).append(": ").append(scores[j]);
            if (j != scores.length - 1) {  // Don't add a comma after the last score
                scoresStr.append(", ");
            }
        }
        Log.i(TAG, scoresStr.toString() + ", number of scores: " + scores.length);

        int[] highest_three = new int[3];

        for(int iteration = 0; iteration < 3; iteration++) {
            int maxIndex = -1;
            float maxScore = -Float.MAX_VALUE;
            for (int i = 0; i < scores.length; i++) {
                if (scores[i] > maxScore) {
                    maxIndex = i + 1;
                    maxScore = scores[i];
                }
            }
            highest_three[iteration] = maxIndex;
            scores[maxIndex - 1] = - Float.MAX_VALUE;
        }

        return highest_three;
    }
        public OnnxTensor createTensorFromBitmap(Bitmap bitmap) throws OrtException {
            // Get the dimensions of the bitmap
            int width = bitmap.getWidth();
            int height = bitmap.getHeight();

            // Create a float array to hold the pixel values
            float[] pixels = new float[width * height * 3]; // Assuming RGB channels

            // Iterate over each pixel in the bitmap
            int pixelIdx = 0;
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    // Get the RGB values of the pixel
                    int color = bitmap.getPixel(x, y);
                    int red = Color.red(color);
                    int green = Color.green(color);
                    int blue = Color.blue(color);

                    // Normalize the pixel values to the range [0.0, 1.0]
                    float normalizedRed = red / 255f;
                    float normalizedGreen = green / 255f;
                    float normalizedBlue = blue / 255f;

                    // Set the pixel values in the float array
                    pixels[pixelIdx++] = normalizedRed;
                    pixels[pixelIdx++] = normalizedGreen;
                    pixels[pixelIdx++] = normalizedBlue;
                }
            }

            // Create an OrtEnvironment
            OrtEnvironment env = OrtEnvironment.getEnvironment();

            // Create an OnnxTensor from the float array
            OnnxTensor tensor = OnnxTensor.createTensor(env, FloatBuffer.wrap(pixels), new long[]{1, 3, height, width}); // Assuming NHWC format

            return tensor;
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
            Log.e(TAG, "Error process asset " + assetName + " to file path");
        }
        return null;
    }
}
