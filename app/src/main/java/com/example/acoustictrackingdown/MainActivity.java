package com.example.acoustictrackingdown;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import android.Manifest;

import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioRecord;
import android.media.AudioTrack;
import android.media.MediaRecorder;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.Toast;

import org.apache.commons.math3.complex.Complex;
import org.apache.commons.math3.transform.DftNormalization;
import org.apache.commons.math3.transform.FastFourierTransformer;
import org.apache.commons.math3.transform.TransformType;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.Date;

public class MainActivity extends AppCompatActivity {

    private Button specButton, trackButton;
    private ImageView spectrogramFull, spectrogramExtract;
    private static String FILE_NAME = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date()) + ".pcm"; // File name with current date and time
    private static String FILE_NAME_2 = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date()) + "_processed" + ".pcm"; // File name with current date and time
    private static final int RECORDING_DURATION = 500; // in milliseconds
    private static final int SAMPLING_RATE_IN_HZ = 44100;
    private static final double START_FREQUENCY = 12000.0;
    private static final double END_FREQUENCY = 13000.0;
    private static double TIME_SIGNAL_OUTPUT = 0.0;
    private static int INDEX_SIGNAL_OUTPUT = 0;
    private static final int DURATION = 10; // in milliseconds
    private static double[] CHIRP_SIGNAL = null;
    private static AudioTrack CHIRP_AUDIO = null;
    private static final int WINDOW_SIZE = 256;
    private static final int OVERLAP = 128;
    private static final int FFT_SIZE = WINDOW_SIZE;



    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        specButton = (Button) findViewById(R.id.full_spectrogram_button);
        trackButton = (Button) findViewById(R.id.acoustic_button);
        spectrogramFull = (ImageView) findViewById(R.id.Spectrogram_Full);
        spectrogramExtract = (ImageView) findViewById(R.id.extracted_spectrogram);

        // set listener for the track button
        trackButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                trackButton.setEnabled(false);
                // initialize the chirp signal and audio
                CHIRP_SIGNAL = generateChirpSignal();
                CHIRP_AUDIO = formAudioTrack(CHIRP_SIGNAL);
                // renew the file name
                FILE_NAME = generateFileName();
                FILE_NAME_2 = generateFileName2();
                recordAudio(RECORDING_DURATION);
                // determine the time of the signal output found via cross correlation
                TIME_SIGNAL_OUTPUT = findChirpSignal(CHIRP_SIGNAL, generateFilePath());
//                INDEX_SIGNAL_OUTPUT = findChirpSignalIndex(CHIRP_SIGNAL, generateFilePath());
//                BYTES_PER_MILLISECOND = 2 * SAMPLING_RATE_IN_HZ / 1000;
                CharSequence text_time_signal_output = String.valueOf(TIME_SIGNAL_OUTPUT);
//                Toast.makeText(getApplicationContext(), text_time_signal_output, Toast.LENGTH_SHORT).show();
                extractAudioSegment();
//                extractAudioSegmentIndex();
                Toast.makeText(getApplicationContext(), "Acoustic Tracking Done", Toast.LENGTH_SHORT).show();
                trackButton.setEnabled(true);
            }
        });

        // Set listener for the spectrogram button.
        specButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                trackButton.setEnabled(false);
                specButton.setEnabled(false);
                Bitmap plot = plotSpectrogram();
                spectrogramFull.setImageBitmap(plot);
                Bitmap plot2 = plotSpectrogram2();
                spectrogramExtract.setImageBitmap(plot2);
                Toast.makeText(getApplicationContext(), "Spectrogram Generated", Toast.LENGTH_SHORT).show();
                trackButton.setEnabled(true);
                specButton.setEnabled(true);
            }
        });
    }

    private String generateFileName() {
        return new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date()) + ".pcm";
    }

    private String generateFilePath() {
        return Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS) + "/" + FILE_NAME;
    }

    private String generateFileName2() {
        return new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date()) + "_processed" + ".pcm";
    }

    private String generateFilePath2() {
        return Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS) + "/" + FILE_NAME_2;
    }

    private void recordAudio(int durationMs) {
        AudioRecord recorder = null;
        FileOutputStream outputStream = null;

        // Set up audio recording parameters
        int sampleRate = 44100;
        int channelConfig = AudioFormat.CHANNEL_IN_MONO;
        int audioFormat = AudioFormat.ENCODING_PCM_16BIT;
        int bufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat);

        // Calculate number of audio samples to record
        int numSamples = (int) ((durationMs / 1000.0) * sampleRate);
        short[] buffer = new short[numSamples];

        try {
            // Set up file output stream
            outputStream = new FileOutputStream(generateFilePath());
            Toast.makeText(getApplicationContext(), generateFilePath(), Toast.LENGTH_SHORT).show();

            // Check recording permissions
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.RECORD_AUDIO}, 0);
                return;
            }

            // Set up audio recorder
            recorder = new AudioRecord(MediaRecorder.AudioSource.MIC, sampleRate, channelConfig, audioFormat, bufferSize);

            // Start audio recording
            recorder.startRecording();

            CHIRP_AUDIO.play();

            int totalSamplesRead = 0;
            while (totalSamplesRead < numSamples) {
                int numRead = recorder.read(buffer, totalSamplesRead, numSamples - totalSamplesRead);
                if (numRead == AudioRecord.ERROR_INVALID_OPERATION || numRead == AudioRecord.ERROR_BAD_VALUE) {
                    break;
                }
                totalSamplesRead += numRead;
            }

            // Write recorded audio data to file
            for (int i = 0; i < buffer.length; i++) {
                outputStream.write(buffer[i] & 0xff);
                outputStream.write((buffer[i] >> 8) & 0xff);
            }

            // Stop audio recording
            recorder.stop();
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            // Clean up resources
            if (outputStream != null) {
                try {
                    outputStream.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            if (recorder != null) {
                recorder.release();
            }
        }
    }

    public double[] generateChirpSignal() {
        int numSamples = DURATION * SAMPLING_RATE_IN_HZ / 1000;
        double[] buffer = new double[numSamples];
        double amplitude = 1.0;

        // generate chirp signal from startFreq to endFreq
        double phase = 0.0;
        for (int i = 0; i < numSamples; i++) {
            double t = (double) i / SAMPLING_RATE_IN_HZ;
            double freq = START_FREQUENCY + (END_FREQUENCY - START_FREQUENCY) * t / ((double) DURATION / 1000.0);
            double angle = 2.0 * Math.PI * phase;
            buffer[i] = amplitude * Math.sin(angle);
            phase += freq / SAMPLING_RATE_IN_HZ;
            phase = phase % 1.0;
        }
        return buffer;
    }

    public AudioTrack formAudioTrack(double[] buffer) {
        int numSamples = buffer.length;
        int bufferSize = numSamples * 2;
        AudioTrack audioTrack = new AudioTrack(AudioManager.STREAM_MUSIC, SAMPLING_RATE_IN_HZ,
                AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT, bufferSize,
                AudioTrack.MODE_STATIC);
        AudioManager audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        int maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, maxVolume, 0);
        audioTrack.setVolume(AudioTrack.getMaxVolume());

        // write buffer to AudioTrack
        audioTrack.write(shortArrayFromDoubleArray(buffer), 0, numSamples);

        return audioTrack;
    }

    private short[] shortArrayFromDoubleArray(double[] buffer) {
        short[] output = new short[buffer.length];
        for (int i = 0; i < buffer.length; i++) {
            output[i] = (short) (buffer[i] * Short.MAX_VALUE);
        }
        return output;
    }

    private double findChirpSignal(double[] chirpSignal, String filePath) {
        File file = new File(filePath);
        byte[] byteBuffer = new byte[(int) file.length()];

        try {
            InputStream inputStream = new FileInputStream(file);
            inputStream.read(byteBuffer);
            inputStream.close();
        } catch (IOException e) {
            e.printStackTrace();
        }

        int numSamples = byteBuffer.length / 2;
        double[] audioSignal = new double[numSamples];

        for (int i = 0; i < numSamples; i++) {
            audioSignal[i] = ((double) ((byteBuffer[2 * i + 1] << 8) | (byteBuffer[2 * i] & 0xff))) / 32768.0;
        }

        int maxLag = numSamples - chirpSignal.length;
        double[] crossCorr = new double[maxLag];

        for (int lag = 0; lag < maxLag; lag++) {
            double sum = 0.0;
            for (int i = 0; i < chirpSignal.length; i++) {
                sum += chirpSignal[i] * audioSignal[lag + i];
            }
            crossCorr[lag] = sum;
        }

        int maxIndex = 0;
        for (int i = 0; i < maxLag; i++) {
            if (crossCorr[i] > crossCorr[maxIndex]) {
                maxIndex = i;
            }
        }

        double timeInMS = (maxIndex / (double) SAMPLING_RATE_IN_HZ) * 1000.0;
        return timeInMS;
    }

    private void extractAudioSegment() {
        try {
            // Set the length of the audio segment to 100ms
            int segmentLength = 150; // in milliseconds

            // Calculate the start and end positions of the segment
//            int startPos = (int) (TIME_SIGNAL_OUTPUT * SAMPLING_RATE_IN_HZ / 1000); // in bytes
            int startPos = (int) ((TIME_SIGNAL_OUTPUT * 2 * SAMPLING_RATE_IN_HZ / 1000) + (DURATION * 2 * SAMPLING_RATE_IN_HZ / 1000)); // in bytes
            int endPos = startPos + segmentLength * SAMPLING_RATE_IN_HZ * 2 / 1000; // in bytes

            // Open the source file for reading
            FileInputStream inputStream = new FileInputStream(generateFilePath());

            // Create a buffer to hold the audio data
            byte[] buffer = new byte[endPos - startPos];

            // Read the audio data from the source file into the buffer
            inputStream.skip(startPos);
            inputStream.read(buffer);

            // Close the input stream
            inputStream.close();

            // Open the target file for writing
            FileOutputStream outputStream = new FileOutputStream(generateFilePath2());

            // Write the audio data to the target file
            outputStream.write(buffer);

            // Close the output stream
            outputStream.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private Bitmap plotSpectrogram() {
        // Read the .pcm audio file into a byte array
        byte[] audioData = null;
        try {
            File audioFile = new File(generateFilePath());
            audioData = new byte[(int) audioFile.length()];
            FileInputStream inputStream = new FileInputStream(audioFile);
            inputStream.read(audioData);
            inputStream.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
        if (audioData == null) {
            // Error reading the audio file
            Toast.makeText(getApplicationContext(), "Audio file empty", Toast.LENGTH_SHORT).show();
            return null;
        }

        int numSmaples = audioData.length / 2;
        double[] audioSamplesDouble = new double[numSmaples];

        for (int i = 0; i < numSmaples; i++) {
            audioSamplesDouble[i] = (audioData[2 * i + 1] << 8) | (audioData[2 * i] & 0xff);
        }

        Log.d("The audio length ", String.valueOf(audioSamplesDouble.length));

        // STFT, determine the size of the 2D array spectrogram first
        int frameBin = (int) Math.floor((audioSamplesDouble.length - WINDOW_SIZE) / OVERLAP) + 1;
        int frequencyBin = (int) Math.floor(FFT_SIZE / 2) + 1;
        double[][] spectrogram = new double[frequencyBin][frameBin];

        // Perform STFT and populate the spectrogram array
        // Iterate over frames
        for (int frame = 0; frame < frameBin; frame++) {
            // Apply window function to the frame
            double[] windowedFrame = applyWindow(audioSamplesDouble, frame * OVERLAP, WINDOW_SIZE);

            // Compute FFT on the windowed frame
            Complex[] fftResult = computeFFT(windowedFrame, FFT_SIZE);

            // Populate the spectrogram array with the magnitude of FFT bins
            for (int frequency = 0; frequency < frequencyBin; frequency++) {
                spectrogram[frequency][frame] = computeMagnitude(fftResult[frequency]);
            }
        }

        Log.d("The height of spectrogram is ", String.valueOf(frequencyBin));
        Log.d("The width of spectrogram is ", String.valueOf(frameBin));

        // plot the bitmap
        int targetWidth = frameBin * 4; // Example target width
        int targetHeight = frequencyBin * 4; // Example target height

        Bitmap spectrogramBitmap = plotFullSpectrogram(spectrogram, targetWidth, targetHeight);
        return spectrogramBitmap;
    }

    private Bitmap plotSpectrogram2() {
        // Read the .pcm audio file into a byte array
        byte[] audioData = null;
        try {
            File audioFile = new File(generateFilePath2());
            audioData = new byte[(int) audioFile.length()];
            FileInputStream inputStream = new FileInputStream(audioFile);
            inputStream.read(audioData);
            inputStream.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
        if (audioData == null) {
            // Error reading the audio file
            Toast.makeText(getApplicationContext(), "Audio file empty", Toast.LENGTH_SHORT).show();
            return null;
        }

        int numSmaples = audioData.length / 2;
        double[] audioSamplesDouble = new double[numSmaples];

        for (int i = 0; i < numSmaples; i++) {
            audioSamplesDouble[i] = (audioData[2 * i + 1] << 8) | (audioData[2 * i] & 0xff);
        }

        Log.d("The audio length ", String.valueOf(audioSamplesDouble.length));

        // STFT, determine the size of the 2D array spectrogram first
        int frameBin = (int) Math.floor((audioSamplesDouble.length - WINDOW_SIZE) / OVERLAP) + 1;
        int frequencyBin = (int) Math.floor(FFT_SIZE / 2) + 1;
        double[][] spectrogram = new double[frequencyBin][frameBin];

        // Perform STFT and populate the spectrogram array
        // Iterate over frames
        for (int frame = 0; frame < frameBin; frame++) {
            // Apply window function to the frame
            double[] windowedFrame = applyWindow(audioSamplesDouble, frame * OVERLAP, WINDOW_SIZE);

            // Compute FFT on the windowed frame
            Complex[] fftResult = computeFFT(windowedFrame, FFT_SIZE);

            // Populate the spectrogram array with the magnitude of FFT bins
            for (int frequency = 0; frequency < frequencyBin; frequency++) {
                spectrogram[frequency][frame] = computeMagnitude(fftResult[frequency]);
            }
        }

        Log.d("The height of spectrogram is ", String.valueOf(frequencyBin));
        Log.d("The width of spectrogram is ", String.valueOf(frameBin));

        // plot the bitmap
        int targetWidth = frameBin * 4; // Example target width
        int targetHeight = frequencyBin * 4; // Example target height

        Bitmap spectrogramBitmap = plotFullSpectrogram(spectrogram, targetWidth, targetHeight);
        return spectrogramBitmap;
    }

    private static double[] applyWindow(double[] frame, int startIndex, int windowSize) {
        double[] windowedFrame = new double[windowSize];

        // Apply Hann window to the frame
        for (int i = 0; i < windowSize; i++) {
            double windowValue = 0.5 * (1 - Math.cos(2 * Math.PI * i / (windowSize - 1)));
            windowedFrame[i] = frame[startIndex + i] * windowValue;
        }

        return windowedFrame;
    }

    private static Complex[] computeFFT(double[] windowedFrame, int fftSize) {
        // Create a transformer for the FFT
        FastFourierTransformer transformer = new FastFourierTransformer(DftNormalization.STANDARD);

        // Perform the FFT on the windowed frame
        Complex[] fftResult = transformer.transform(windowedFrame, TransformType.FORWARD);

        // Return the result as an array of complex numbers
        return fftResult;
    }

    private static double computeMagnitude(Complex complexValue) {
        return complexValue.abs();
    }

    private static Bitmap plotFullSpectrogram(double[][] spectrogram, int targetWidth, int targetHeight) {
        int width = spectrogram[0].length;
        int height = spectrogram.length;

        // Scale up the spectrogram to the target dimensions
        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Bitmap scaledBitmap = Bitmap.createScaledBitmap(bitmap, targetWidth, targetHeight, false);

        // Iterate over the spectrogram and set pixel colors based on the 'jet' colormap
        for (int x = 0; x < targetWidth; x++) {
            for (int y = 0; y < targetHeight; y++) {
                // Map the pixel coordinates to the spectrogram indices
                int originalX = x * width / targetWidth;
                int originalY = y * height / targetHeight;

                // Get the magnitude value at the corresponding spectrogram indices
                double magnitude = spectrogram[originalY][originalX];

                // Map the magnitude value to the 'jet' colormap
                int color = getPlasmaColorFromMagnitude(magnitude);

                // Set the pixel color in the scaled bitmap
                scaledBitmap.setPixel(x, y, color);
            }
        }

        return scaledBitmap;
    }

    private static Bitmap plotExtractedSpectrogram(double[][] spectrogram, int targetWidth, int targetHeight) {
        int width = spectrogram[0].length;
        int height = spectrogram.length;

        // Calculate the frequency range indices
        int startFrequencyBin = calculateFrequencyBinIndex(12000, width);
        int endFrequencyBin = calculateFrequencyBinIndex(13000, width);

        // Scale up the spectrogram to the target dimensions
        Bitmap bitmap = Bitmap.createBitmap(width, endFrequencyBin - startFrequencyBin, Bitmap.Config.ARGB_8888);
        Bitmap scaledBitmap = Bitmap.createScaledBitmap(bitmap, targetWidth, targetHeight, false);

        // Iterate over the spectrogram and set pixel colors based on the 'jet' colormap
        for (int x = 0; x < targetWidth; x++) {
            for (int y = 0; y < targetHeight; y++) {
                // Map the pixel coordinates to the spectrogram indices
                int originalX = x * width / targetWidth;
                int originalY = startFrequencyBin + y * (endFrequencyBin - startFrequencyBin) / targetHeight;

                // Get the magnitude value at the corresponding spectrogram indices
                double magnitude = spectrogram[originalY][originalX];

                // Map the magnitude value to the 'jet' colormap
                int color = getJetColorFromMagnitude(magnitude);

                // Set the pixel color in the scaled bitmap
                scaledBitmap.setPixel(x, y, color);
            }
        }

        return scaledBitmap;
    }

    private static int calculateFrequencyBinIndex(int frequency, int spectrogramHeight) {
        // Calculate the frequency bin index based on the frequency and spectrogram height
        return (int) Math.floor((frequency / (SAMPLING_RATE_IN_HZ / 2)) * (spectrogramHeight - 1));
    }


    private static int getJetColorFromMagnitude(double magnitude) {
        // Map the magnitude value to the 'jet' colormap
        double maxMagnitude = 150;
        double scaledMagnitude = magnitude / maxMagnitude; // Scale the magnitude if needed

        // Get the color components (R, G, B) based on the scaled magnitude
        int r = (int) (255 * scaledMagnitude);
        int g = (int) (255 * (1 - scaledMagnitude));
        int b = (int) (255 * (1 - scaledMagnitude * scaledMagnitude * scaledMagnitude));

        // Create the color using RGB components
        return Color.rgb(r, g, b);
    }

    private static int getGrayColorFromMagnitude(double magnitude) {
        double maxMagnitude = 150;
        double scaledMagnitude = magnitude / maxMagnitude;

        int grayValue = (int) (255 * scaledMagnitude);

        return Color.rgb(grayValue, grayValue, grayValue);
    }

    private static int getMagmaColorFromMagnitude(double magnitude) {
        double maxMagnitude = 100;
        double scaledMagnitude = magnitude / maxMagnitude;

        int r, g, b;

        if (scaledMagnitude < 0.25) {
            r = (int) (4 * scaledMagnitude * 255);
            g = 0;
            b = 0;
        } else if (scaledMagnitude < 0.5) {
            r = 255;
            g = (int) ((4 * scaledMagnitude - 1) * 255);
            b = 0;
        } else if (scaledMagnitude < 0.75) {
            r = (int) ((4 * scaledMagnitude - 2) * 255);
            g = 255;
            b = (int) ((4 * scaledMagnitude - 2) * 255);
        } else {
            r = (int) ((4 * scaledMagnitude - 3) * 255);
            g = (int) ((4 * scaledMagnitude - 3) * 255);
            b = 255;
        }

        return Color.rgb(r, g, b);
    }

    private static int getPlasmaColorFromMagnitude(double magnitude) {
        double maxMagnitude = 100;
        double scaledMagnitude = magnitude / maxMagnitude;

        int color = Color.rgb(
                (int) (scaledMagnitude * 255 * 0.082),
                (int) (scaledMagnitude * 255 * 0.285),
                (int) (scaledMagnitude * 255 * 0.564)
        );

        return color;
    }

}