package com.example.acoustictrackingdown;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import android.Manifest;

import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Paint;
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

import org.jtransforms.fft.DoubleFFT_1D;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;

public class MainActivity extends AppCompatActivity {

    private Button specButton, trackButton;
    private ImageView spectrogram;
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


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        specButton = (Button) findViewById(R.id.spectrogram_button);
        trackButton = (Button) findViewById(R.id.acoustic_button);
        spectrogram = (ImageView) findViewById(R.id.Spectrogram);


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
                spectrogram.setImageBitmap(plot);
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
            audioSignal[i] = ((short) ((byteBuffer[2 * i + 1] << 8) | byteBuffer[2 * i])) / 32768.0;
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

//        // Convert bytes to short array (assuming 16-bit PCM audio)
//        short[] audioSamples = new short[audioData.length / 2];
//        ByteBuffer.wrap(audioData).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().get(audioSamples);
//
//        // Convert short array to double array
//        double[] audioSamplesDouble = new double[audioSamples.length];
//        for (int i = 0; i < audioSamples.length; i++) {
//            audioSamplesDouble[i] = audioSamples[i] / 32768.0; // Normalize to range [-1.0, 1.0]
//        }

        int numSmaples = audioData.length / 2;
        double[] audioSamplesDouble = new double[numSmaples];

        for (int i = 0; i < numSmaples; i++) {
            audioSamplesDouble[i] = ((short) ((audioData[2 * i + 1] << 8) | audioData[2 * i])) / 32768.0;
        }

        Log.d("", String.valueOf(numSmaples));

        // Compute the spectrogram
        int fftSize = 256; // Size of the FFT (power of 2 for optimal performance)
        int hopSize = 128; // Number of samples between consecutive frames
        int windowSize = fftSize; // Size of the analysis window

        double[][] spectrogramData = new double[(audioSamplesDouble.length - windowSize) / hopSize + 1][fftSize / 2];

        // Compute the spectrogram frame by frame
        for (int i = 0; i < spectrogramData.length; i++) {
            // Apply the analysis window to the audio frame
            double[] audioFrame = Arrays.copyOfRange(audioSamplesDouble, i * hopSize, i * hopSize + windowSize);
            double[] windowedFrame = audioFrame; // Apply the desired windowing function

            // Compute the FFT of the windowed frame
            DoubleFFT_1D fftTransformer = new DoubleFFT_1D(fftSize);
            double[] fft = new double[fftSize * 2];
            System.arraycopy(windowedFrame, 0, fft, 0, windowSize); // Zero-pad if necessary

            fftTransformer.realForwardFull(fft);

            // Compute the magnitude spectrum (half of the FFT)
            for (int j = 0; j < fftSize / 2; j++) {
                double real = fft[2 * j];
                double imag = fft[2 * j + 1];
                double magnitude = Math.sqrt(real * real + imag * imag);
                spectrogramData[i][j] = magnitude;
            }
        }

        // Set up the dimensions of the spectrogram
        int width = spectrogramData.length; // Number of time frames
//        int height = spectrogramData[0].length; // Number of frequency bins
        double minFrequency = START_FREQUENCY; // Minimum frequency of interest in Hz
        double maxFrequency = END_FREQUENCY; // Maximum frequency of interest in Hz
        int minIndex = (int) (minFrequency * fftSize / 44100.0);
        int maxIndex = (int) (maxFrequency * fftSize / 44100.0);

        int croppedHeight = maxIndex - minIndex; // Height of the cropped spectrogram
        Bitmap croppedBitmap = Bitmap.createBitmap(width, croppedHeight, Bitmap.Config.ARGB_8888);

        croppedBitmap.eraseColor(Color.WHITE); // Set the background color

        // Set up the paint for drawing
        Paint paint = new Paint();
        paint.setStyle(Paint.Style.FILL);

        // Define the color range (adjust as desired)
        int minColor = Color.BLACK;
        int maxColor = Color.WHITE;

        double minMagnitude = Double.MIN_VALUE;
        double maxMagnitude = Double.MAX_VALUE;

        // Iterate over the spectrogram data to find the minimum and maximum magnitudes
        for (int i = 0; i < spectrogramData.length; i++) {
            for (int j = 0; j < spectrogramData[0].length; j++) {
                double magnitude = spectrogramData[i][j];
                minMagnitude = Math.min(minMagnitude, magnitude);
                maxMagnitude = Math.max(maxMagnitude, magnitude);
            }
        }

        // Plot the spectrogram data
        for (int i = 0; i < width; i++) {
            for (int j = 0; j < croppedHeight; j++) {
                // Get the magnitude value
                double magnitude = spectrogramData[i][j];

                // Map the magnitude value to a color using a custom color mapping function
                int color = mapMagnitudeToColor(magnitude, minColor, maxColor, minMagnitude, maxMagnitude);

                // Set the color of the paint
                paint.setColor(color);

                // Set the pixel color in the bitmap
                croppedBitmap.setPixel(i, j, color);
            }
        }

        return croppedBitmap;
    }

    private int mapMagnitudeToColor(double magnitude, int minColor, int maxColor, double minMagnitude, double maxMagnitude) {
        // Normalize the magnitude value to the range [0, 1]
        double normalizedMagnitude = (magnitude - minMagnitude) / (maxMagnitude - minMagnitude);

        // Define the color map
        int[] colorMap = {
                0xFF000080, // Dark Blue
                0xFF0000FF, // Blue
                0xFF00FFFF, // Cyan
                0xFF00FF00, // Green
                0xFFFFFF00, // Yellow
                0xFFFF0000, // Red
                0xFF800000  // Dark Red
        };

        // Scale the normalized magnitude to the range [0, colorMap.length - 1]
        double scaledMagnitude = normalizedMagnitude * (colorMap.length - 1);

        // Get the lower and upper color indices
        int lowerIndex = (int) Math.floor(scaledMagnitude);
        int upperIndex = lowerIndex + 1;

        // Clip the indices to the valid range
        lowerIndex = Math.max(0, Math.min(lowerIndex, colorMap.length - 1));
        upperIndex = Math.max(0, Math.min(upperIndex, colorMap.length - 1));

        // Get the fractional part for interpolation
        double fraction = scaledMagnitude - lowerIndex;

        // Get the lower and upper colors from the color map
        int lowerColor = colorMap[lowerIndex];
        int upperColor = colorMap[upperIndex];

        // Interpolate between the lower and upper colors based on the fraction
        int red = (int) (Color.red(lowerColor) * (1 - fraction) + Color.red(upperColor) * fraction);
        int green = (int) (Color.green(lowerColor) * (1 - fraction) + Color.green(upperColor) * fraction);
        int blue = (int) (Color.blue(lowerColor) * (1 - fraction) + Color.blue(upperColor) * fraction);

        // Combine the RGB channels into a single color value
        return Color.rgb(red, green, blue);
    }
}