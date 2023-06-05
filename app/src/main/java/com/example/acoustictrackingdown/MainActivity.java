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
import android.os.Handler;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.Spinner;
import android.widget.Toast;

import org.apache.commons.math3.complex.Complex;
import org.apache.commons.math3.transform.DftNormalization;
import org.apache.commons.math3.transform.FastFourierTransformer;
import org.apache.commons.math3.transform.TransformType;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;

public class MainActivity extends AppCompatActivity {
    private Button specButton, trackButton, gatherDataButton, positionButton;
    private Spinner cellSelect;
    private ImageView spectrogramFull, spectrogramExtract, spectrogramSmallExtract, buildingMap;
    private static String cell = "Not_defined_yet";
    private static String FILE_NAME = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date()) + ".pcm"; // File name with current date and time
    private static String FILE_NAME_2 = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date()) + "_processed" + ".pcm"; // File name with current date and time
    private static String FILE_NAME_3 = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date()) + "_test" + ".png"; // File name with current date and time
    private static String FILE_NAME_CELL = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date()) + "_not_yet_defined" + ".png"; // File name with current date and time
    private static String FILE_NAME_CELL2 = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date()) + "_not_yet_defined" + ".csv";
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
    private static final int SAMPLE_SIZE = 5;
    private static double[] SPECTRAL_CONTRAST = null;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        buildingMap = (ImageView) findViewById(R.id.map);
        positionButton = (Button) findViewById(R.id.positioning);
        specButton = (Button) findViewById(R.id.full_spectrogram_button);
        trackButton = (Button) findViewById(R.id.acoustic_button);
        gatherDataButton = (Button) findViewById(R.id.gatherData);
        spectrogramFull = (ImageView) findViewById(R.id.Spectrogram_Full);
        spectrogramExtract = (ImageView) findViewById(R.id.extracted_spectrogram);
        spectrogramSmallExtract = (ImageView) findViewById(R.id.f_extracted_spectrogram);

        cellSelect = (Spinner) findViewById(R.id.cell_selector);
        ArrayList<String> items = new ArrayList<>();
        for (int i = 1; i <= 16; i++) {
            String item = "C" + i;
            items.add(item);
        }
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, items);
        cellSelect.setAdapter(adapter);

        buildingMap.setImageResource(R.drawable.map);

        // set listener for the track button
        trackButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                trackButton.setEnabled(false);
                specButton.setEnabled(false);
                gatherDataButton.setEnabled(false);
                cellSelect.setEnabled(false);
                positionButton.setEnabled(false);
                // initialize the chirp signal and audio
                CHIRP_SIGNAL = generateChirpSignal();
                CHIRP_AUDIO = formAudioTrack(CHIRP_SIGNAL);
                // renew the file name
                // FILE_NAME for the full spectrogram
                FILE_NAME = generateFileName();
                // FILE_NAME_2 for the extracted spectrogram
                FILE_NAME_2 = generateFileName2();
                // FILE_NAME_3 for the extracted + cut spectrogram
                FILE_NAME_3 = generateFileNameTest();
                recordAudio(RECORDING_DURATION);
                // determine the time of the signal output found via cross correlation
//                TIME_SIGNAL_OUTPUT = findChirpSignal(CHIRP_SIGNAL, generateFilePath());
                INDEX_SIGNAL_OUTPUT = findChirpSignalIndex(CHIRP_SIGNAL, generateFilePath());
//                CharSequence text_time_signal_output = String.valueOf(TIME_SIGNAL_OUTPUT);
//                Toast.makeText(getApplicationContext(), text_time_signal_output, Toast.LENGTH_SHORT).show();
                CharSequence index_time_signal_output = String.valueOf(INDEX_SIGNAL_OUTPUT);
                Toast.makeText(getApplicationContext(), index_time_signal_output, Toast.LENGTH_SHORT).show();
//                extractAudioSegment();
                extractAudioSegmentIndex();
//                Toast.makeText(getApplicationContext(), "Acoustic Tracking Done", Toast.LENGTH_SHORT).show();
                gatherDataButton.setEnabled(true);
                cellSelect.setEnabled(true);
                trackButton.setEnabled(true);
                specButton.setEnabled(true);
                positionButton.setEnabled(true);
            }
        });

        // Set listener for the spectrogram button.
        specButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                trackButton.setEnabled(false);
                specButton.setEnabled(false);
                gatherDataButton.setEnabled(false);
                cellSelect.setEnabled(false);
                positionButton.setEnabled(false);
                Bitmap plot = plotSpectrogram();
                spectrogramFull.setImageBitmap(plot);
                Bitmap plot2 = plotSpectrogram2();
                spectrogramExtract.setImageBitmap(plot2);
                Bitmap plot3 = plotSpectrogram3();
                spectrogramSmallExtract.setImageBitmap(plot3);
                Bitmap plotSave = plotSpectrogramSave();
//                Toast.makeText(getApplicationContext(), "Spectrogram Generated", Toast.LENGTH_SHORT).show();

                // Save the bitmap to a file
                File file = new File(generateFilePathTest());
                try {
                    FileOutputStream fos = new FileOutputStream(file);
                    plotSave.compress(Bitmap.CompressFormat.PNG, 100, fos); // Adjust the compression quality as needed
                    fos.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
                gatherDataButton.setEnabled(true);
                cellSelect.setEnabled(true);
                trackButton.setEnabled(true);
                specButton.setEnabled(true);
                positionButton.setEnabled(true);
            }
        });

        cellSelect.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                String selectedItem = (String) parent.getItemAtPosition(position);
                cell = selectedItem;
                // Do something with the selected item
                String finalText = (String) "Gather cell " + cell +" data";
                gatherDataButton.setText(finalText); // Change the text of the button to the selected item
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                // Do nothing when no item is selected
            }
        });

        gatherDataButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                trackButton.setEnabled(false);
                specButton.setEnabled(false);
                gatherDataButton.setEnabled(false);
                cellSelect.setEnabled(false);
                positionButton.setEnabled(false);

                // Define the delay between iterations (in milliseconds)
                final int delay = 1500;

                final Handler handler = new Handler();
                handler.postDelayed(new Runnable() {
                    int iteration = 0;

                    @Override
                    public void run() {
                        // Inside this run() method, place the code you want to execute after the delay

                        if (iteration < SAMPLE_SIZE) {
                            // Your existing code goes here
                            CHIRP_SIGNAL = generateChirpSignal();
                            CHIRP_AUDIO = formAudioTrack(CHIRP_SIGNAL);
                            FILE_NAME = generateFileName();
                            FILE_NAME_2 = generateFileName2();
                            FILE_NAME_CELL = generateFileNameCell();
                            FILE_NAME_CELL2 = generateFileNameCell2();
                            recordAudio(RECORDING_DURATION);
                            INDEX_SIGNAL_OUTPUT = findChirpSignalIndex(CHIRP_SIGNAL, generateFilePath());
                            extractAudioSegmentIndex();
                            Bitmap plot = plotSpectrogram();
                            spectrogramFull.setImageBitmap(plot);
                            Bitmap plot2 = plotSpectrogram2();
                            spectrogramExtract.setImageBitmap(plot2);
                            Bitmap plot3 = plotSpectrogram3();
                            spectrogramSmallExtract.setImageBitmap(plot3);
                            Bitmap plotSave = plotSpectrogramSave();
                            File file = new File(generateFilePathCell());
                            try {
                                FileOutputStream fos = new FileOutputStream(file);
                                plotSave.compress(Bitmap.CompressFormat.PNG, 100, fos);
                                fos.close();
                            } catch (IOException e) {
                                e.printStackTrace();
                            }

                            iteration++;
                            handler.postDelayed(this, delay); // Schedule the next iteration after the delay
                        } else {
                            // The loop has completed all iterations
                            trackButton.setEnabled(true);
                            specButton.setEnabled(true);
                            gatherDataButton.setEnabled(true);
                            cellSelect.setEnabled(true);
                            positionButton.setEnabled(true);
                        }
                    }
                }, delay);
            }
        });

        positionButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                CHIRP_SIGNAL = generateChirpSignal();
                CHIRP_AUDIO = formAudioTrack(CHIRP_SIGNAL);
                FILE_NAME = generateFileName();
                FILE_NAME_2 = generateFileName2();
                FILE_NAME_3 = generateFileNameTest();
                FILE_NAME_CELL2 = "Track" + ".csv";
                recordAudio(RECORDING_DURATION);
                INDEX_SIGNAL_OUTPUT = findChirpSignalIndex(CHIRP_SIGNAL, generateFilePath());
                extractAudioSegmentIndex();
                Bitmap plot = plotSpectrogram();
                spectrogramFull.setImageBitmap(plot);
                Bitmap plot2 = plotSpectrogram2();
                spectrogramExtract.setImageBitmap(plot2);
                Bitmap plot3 = plotSpectrogram3();
                spectrogramSmallExtract.setImageBitmap(plot3);
                Bitmap plotSave = plotSpectrogramSave();
                // Save the bitmap to a file
                File file = new File(generateFilePathTest());
                try {
                    FileOutputStream fos = new FileOutputStream(file);
                    plotSave.compress(Bitmap.CompressFormat.PNG, 100, fos); // Adjust the compression quality as needed
                    fos.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
                // Then apply the model here to determine position
                gatherDataButton.setEnabled(true);
                cellSelect.setEnabled(true);
                trackButton.setEnabled(true);
                specButton.setEnabled(true);
                positionButton.setEnabled(true);
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

    private String generateFileNameTest() {
        return new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date()) + "_spectrogram" + ".png";
    }

    private String generateFilePathTest() {
        File storageDir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "Test");
        storageDir.mkdirs(); // Create the "Test" folder if it doesn't exist
        return Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS) + "/" + "Test" + "/" + FILE_NAME_3;
    }

    private String generateFileNameCell() {
        return new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date()) + "_" + cell + ".png";
    }

    private String generateFileNameCell2() {
        return cell + ".csv";
    }

    private String generateFilePathCell() {
        File storageDir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), cell);
        storageDir.mkdirs(); // Create the "Test" folder if it doesn't exist
        return Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS) + "/" + cell + "/" + FILE_NAME_CELL;
    }

    private String generateFilePathCell2() {
        File storageDir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "CSVs");
        storageDir.mkdirs(); // Create the "Test" folder if it doesn't exist
        return Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS) + "/" + "CSVs" + "/" + FILE_NAME_CELL2;
    }

    private String generateFilePathCell2Testing() {
        File storageDir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "Track");
        storageDir.mkdirs(); // Create the "Test" folder if it doesn't exist
        return Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS) + "/" + "Track" + "/" + FILE_NAME_CELL2;
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
//            Toast.makeText(getApplicationContext(), generateFilePath(), Toast.LENGTH_SHORT).show();

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

        double timeInMS = ((double) maxIndex / SAMPLING_RATE_IN_HZ) * 1000.0;
        return timeInMS;
    }

    private int findChirpSignalIndex(double[] chirpSignal, String filePath) {
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

        return maxIndex;
    }

    private void extractAudioSegment() {
        try {
            // Set the length of the audio segment to 100ms
            int segmentLength = 150; // in milliseconds

            // Calculate the start and end positions of the segment
//            int startPos = (int) (TIME_SIGNAL_OUTPUT * SAMPLING_RATE_IN_HZ / 1000); // in bytes
//            int startPos = (int) ((TIME_SIGNAL_OUTPUT * 2 * SAMPLING_RATE_IN_HZ / 1000) + (DURATION * 2 * SAMPLING_RATE_IN_HZ / 1000)); // in bytes
            int startPos = (int) ((TIME_SIGNAL_OUTPUT + DURATION) * 2 * SAMPLING_RATE_IN_HZ / 1000);
            int endPos = startPos + (segmentLength * SAMPLING_RATE_IN_HZ * 2 / 1000); // in bytes

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

    private void extractAudioSegmentIndex() {
        try {
            // Set the length of the audio segment to 100ms
            int segmentLength = 150; // in milliseconds

            // Calculate the start and end positions of the segment
//            int startPos = INDEX_SIGNAL_OUTPUT; // in bytes
            int startPos = (INDEX_SIGNAL_OUTPUT * 2) + (SAMPLING_RATE_IN_HZ * DURATION * 2 / 1000); // in bytes
//            int endPos = startPos + segmentLength * BYTES_PER_MILLISECOND; // in bytes
            int endPos = startPos + (segmentLength * SAMPLING_RATE_IN_HZ * 2 / 1000); // in bytes

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
//
//        for (int i = 0; i < frameBin; i++) {
//            for (int j = 0; j < frequencyBin; j++) {
//                Log.d("", String.valueOf(spectrogram[j][i]));
//            }
//        }

        // plot the bitmap
        int targetWidth = frameBin * 4; // Example target width
        int targetHeight = frequencyBin * 4; // Example target height

        Bitmap spectrogramBitmap = plotFullSpectrogram(spectrogram, targetWidth, targetHeight);
        return spectrogramBitmap;
    }

    private Bitmap plotSpectrogram3() {
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
        int frameBin = (int) Math.floor((double) (audioSamplesDouble.length - WINDOW_SIZE) / OVERLAP) + 1;
        int frequencyBin = (int) Math.floor((double) FFT_SIZE / 2) + 1;
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

//        Log.d("The height of spectrogram is ", String.valueOf(frequencyBin));
//        Log.d("The width of spectrogram is ", String.valueOf(frameBin));
//
//        for (int i = 0; i < frameBin; i++) {
//            for (int j = 0; j < frequencyBin; j++) {
//                Log.d("", String.valueOf(spectrogram[j][i]));
//            }
//        }
        int newFrameBin = frameBin;
        int newFrequencyBin = (int) Math.ceil((END_FREQUENCY - START_FREQUENCY) / ((SAMPLING_RATE_IN_HZ / 2.0) / frequencyBin)) + 4;
        double[][] newSpectrogram = new double[newFrequencyBin][newFrameBin];

        Log.d("The height of spectrogram is ", String.valueOf(newFrequencyBin));
        Log.d("The width of spectrogram is ", String.valueOf(newFrameBin));

        int startRow = (int) Math.floor(START_FREQUENCY / ((SAMPLING_RATE_IN_HZ / 2.0) / frequencyBin)) - 2;
        int endRow = (int) startRow + newFrequencyBin;

        for (int frame = 0; frame < newFrameBin; frame++) {
            for (int frequency = startRow; frequency < endRow; frequency++) {
                newSpectrogram[frequency - startRow][frame] = spectrogram[frequency][frame];
            }
        }

        // plot the bitmap
        int targetWidth = newFrameBin * 8; // Example target width
        int targetHeight = newFrequencyBin * 8; // Example target height

        Bitmap spectrogramBitmap = plotExtractedSpectrogram(newSpectrogram, targetWidth, targetHeight);
        return spectrogramBitmap;
    }

    private Bitmap plotSpectrogramSave() {
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
        int frameBin = (int) Math.floor((double) (audioSamplesDouble.length - WINDOW_SIZE) / OVERLAP) + 1;
        int frequencyBin = (int) Math.floor((double) FFT_SIZE / 2) + 1;
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

//        Log.d("The height of spectrogram is ", String.valueOf(frequencyBin));
//        Log.d("The width of spectrogram is ", String.valueOf(frameBin));
//
//        for (int i = 0; i < frameBin; i++) {
//            for (int j = 0; j < frequencyBin; j++) {
//                Log.d("", String.valueOf(spectrogram[j][i]));
//            }
//        }
        int newFrameBin = frameBin;
        int newFrequencyBin = (int) Math.ceil((END_FREQUENCY - START_FREQUENCY) / ((SAMPLING_RATE_IN_HZ / 2.0) / frequencyBin)) + 4;
        double[][] newSpectrogram = new double[newFrequencyBin][newFrameBin];

        Log.d("The height of spectrogram is ", String.valueOf(newFrequencyBin));
        Log.d("The width of spectrogram is ", String.valueOf(newFrameBin));

        int startRow = (int) Math.floor(START_FREQUENCY / ((SAMPLING_RATE_IN_HZ / 2.0) / frequencyBin)) - 2;
        int endRow = (int) startRow + newFrequencyBin;

        for (int frame = 0; frame < newFrameBin; frame++) {
            for (int frequency = startRow; frequency < endRow; frequency++) {
                newSpectrogram[frequency - startRow][frame] = spectrogram[frequency][frame];
            }
        }
        SPECTRAL_CONTRAST = computeAndSaveSpectralContrast(newSpectrogram);

        // plot the bitmap
        int targetWidth = newFrameBin * 2; // Example target width
        int targetHeight = newFrequencyBin * 2; // Example target height

        Bitmap spectrogramBitmap = plotExtractedSpectrogram(newSpectrogram, targetWidth, targetHeight);
        return spectrogramBitmap;
    }

    private double[] computeAndSaveSpectralContrast(double[][] newSpectrogram) {
        // Determine the number of frequency bins and frames
        int frequencyBin = newSpectrogram.length;
        int frameBin = newSpectrogram[0].length;

        // Initialize an array to store the spectral contrast values
        double[] spectralContrast = new double[frameBin];

        // Iterate over frames
        for (int frame = 0; frame < frameBin; frame++) {

            // Initialize peak and valley
            double peak = Double.MIN_VALUE;
            double valley = Double.MAX_VALUE;

            // Iterate over frequencies
            for (int frequency = 0; frequency < frequencyBin; frequency++) {

                // Update peak and valley
                peak = Math.max(peak, newSpectrogram[frequency][frame]);
                valley = Math.min(valley, newSpectrogram[frequency][frame]);
            }

            // Calculate spectral contrast for this frame
            spectralContrast[frame] = peak - valley;
        }

        // Now, write the spectralContrast array to a CSV file

        try {
            // Create a File object
            File file = new File(generateFilePathCell2());

            // Create the file if it does not exist
            if (!file.exists()) {
                file.createNewFile();
            }

            // Create a FileWriter in append mode
            FileWriter writer = new FileWriter(file, true);

            // Create a StringBuilder to hold the CSV data
            StringBuilder sb = new StringBuilder();

            // Append the spectral contrast values to the StringBuilder
            for (int i = 0; i < spectralContrast.length; i++) {
                sb.append(spectralContrast[i]);

                // If not the last element, append a comma
                if (i != spectralContrast.length - 1) {
                    sb.append(",");
                }
            }

            // Append a newline to end this line of the CSV
            sb.append("\n");

            // Write the StringBuilder's content to the file
            writer.write(sb.toString());

            // Close the FileWriter
            writer.close();

        } catch (IOException e) {
            e.printStackTrace();
        }
        return spectralContrast;
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

        // Normalize the spectrogram values between 0 and 1
        double[][] normalizedSpectrogram = normalizeSpectrogram(spectrogram);

        // Iterate over the spectrogram and set pixel colors based on the 'jet' colormap
        for (int x = 0; x < targetWidth; x++) {
            for (int y = 0; y < targetHeight; y++) {
                // Map the pixel coordinates to the spectrogram indices
                int originalX = x * width / targetWidth;
                int originalY = (targetHeight - y - 1) * height / targetHeight; // Flip the vertical axis

                // Get the magnitude value at the corresponding spectrogram indices
                double magnitude = normalizedSpectrogram[originalY][originalX];

                // Map the magnitude value to the 'jet' colormap
//                int color = getPlasmaColorFromMagnitude(magnitude);
                int color = getGrayColorFromMagnitude(magnitude);

                // Set the pixel color in the scaled bitmap
                scaledBitmap.setPixel(x, y, color);
            }
        }

        return scaledBitmap;
    }

    // this is not working yet
    private static Bitmap plotExtractedSpectrogram(double[][] spectrogram, int targetWidth, int targetHeight) {
        int width = spectrogram[0].length;
        int height = spectrogram.length;

        // Scale up the spectrogram to the target dimensions
        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Bitmap scaledBitmap = Bitmap.createScaledBitmap(bitmap, targetWidth, targetHeight, false);

        // Normalize the spectrogram values between 0 and 1
        double[][] normalizedSpectrogram = normalizeSpectrogram(spectrogram);

        // Iterate over the spectrogram and set pixel colors based on the 'jet' colormap
        for (int x = 0; x < targetWidth; x++) {
            for (int y = 0; y < targetHeight; y++) {
                // Map the pixel coordinates to the spectrogram indices
                int originalX = x * width / targetWidth;
                int originalY = (targetHeight - y - 1) * height / targetHeight; // Flip the vertical axis

                // Get the magnitude value at the corresponding spectrogram indices
                double magnitude = normalizedSpectrogram[originalY][originalX];

                // Map the magnitude value to the 'jet' colormap
//                int color = getPlasmaColorFromMagnitude(magnitude);
                int color = getGray2ColorFromMagnitude(magnitude);

                // Set the pixel color in the scaled bitmap
                scaledBitmap.setPixel(x, y, color);
            }
        }

        return scaledBitmap;
    }

    private static double[][] normalizeSpectrogram(double[][] spectrogram) {
        int height = spectrogram.length;
        int width = spectrogram[0].length;

        double maxMagnitude = Double.MIN_VALUE;
        double minMagnitude = Double.MAX_VALUE;

        // Find the maximum and minimum magnitude values in the spectrogram
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                double magnitude = spectrogram[y][x];
                maxMagnitude = Math.max(maxMagnitude, magnitude);
                minMagnitude = Math.min(minMagnitude, magnitude);
            }
        }

        double[][] normalizedSpectrogram = new double[height][width];

        // Normalize the spectrogram values between 0 and 1
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                double magnitude = spectrogram[y][x];
                double normalizedMagnitude = (magnitude - minMagnitude) / (maxMagnitude - minMagnitude);
                normalizedSpectrogram[y][x] = normalizedMagnitude;
            }
        }

        return normalizedSpectrogram;
    }

    private static int getGrayColorFromMagnitude(double magnitude) {
        // scale the magnitude up a bit, but cap it at 1.
        double factor = 10;
        double scaledMagnitude = magnitude * factor;

        scaledMagnitude = Math.min(1.0, scaledMagnitude);

        int binaryValue = (int) (255 * (1 - scaledMagnitude));

        // Ensure the binaryValue is within the valid range of 0-255
        binaryValue = Math.max(0, Math.min(255, binaryValue));

        return Color.rgb(binaryValue, binaryValue, binaryValue);
    }

    private static int getGray2ColorFromMagnitude(double magnitude) {
        // scale the magnitude up a bit, but cap it at 1.
        double factor = 2.4;
        double scaledMagnitude = magnitude * factor;

        scaledMagnitude = Math.min(1.0, scaledMagnitude);

        int binaryValue = (int) (255 * (1 - scaledMagnitude));

        // Ensure the binaryValue is within the valid range of 0-255
        binaryValue = Math.max(0, Math.min(255, binaryValue));

        return Color.rgb(binaryValue, binaryValue, binaryValue);
    }

    private static int getPlasmaColorFromMagnitude(double magnitude) {
        // scale the magnitude up a bit, but cap it at 1.
        double factor = 50;
        double scaledMagnitude = magnitude * factor;

//        scaledMagnitude = Math.min(1.0, scaledMagnitude);

        int color = Color.rgb(
                (int) (scaledMagnitude * 255 * 0.082),
                (int) (scaledMagnitude * 255 * 0.285),
                (int) (scaledMagnitude * 255 * 0.564)
        );

        return color;
    }

}