package com.example.redmimic;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.text.format.Formatter;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

// ИМПОРТЫ ДЛЯ NANOHTTPD 2.3.1
import fi.iki.elonen.NanoHTTPD;
import fi.iki.elonen.NanoHTTPD.Response.Status;

import java.io.InputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;

public class MainActivity extends AppCompatActivity {

    private static final int REQUEST_RECORD_AUDIO_PERMISSION = 200;
    private static final int SAMPLE_RATE = 44100;
    private static final int CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO;
    private static final int AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT;
    private static final int PORT = 8080;

    private AudioServer server;
    private AudioRecord audioRecord;
    private int bufferSize;
    private boolean isRecording = false;
    private Thread recordingThread;
    private PipedOutputStream pipedOutputStream;

    private TextView tvStatus;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        tvStatus = findViewById(R.id.tvStatus);

        bufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT);

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.RECORD_AUDIO}, REQUEST_RECORD_AUDIO_PERMISSION);
        } else {
            startAudioServer();
        }
    }

    private void startAudioServer() {
        try {
            server = new AudioServer(PORT);
            server.start(NanoHTTPD.SOCKET_READ_TIMEOUT, false);
            
            WifiManager wifiManager = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
            int ipAddress = wifiManager.getConnectionInfo().getIpAddress();
            String ip = Formatter.formatIpAddress(ipAddress);
            
            tvStatus.setText("Server Running on:\nhttp://" + ip + ":" + PORT + "/audio.wav");
        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(this, "Failed to start server: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private class AudioServer extends NanoHTTPD {
        public AudioServer(int port) {
            super(port);
        }

        @Override
        public Response serve(IHTTPSession session) {
            if (session.getUri().equals("/audio.wav")) {
                stopRecording(); 
                
                try {
                    PipedInputStream inputStream = new PipedInputStream();
                    pipedOutputStream = new PipedOutputStream(inputStream);
                    
                    startRecording();
                    
                    // В 2.3.1 используется этот метод для стриминга
                    return newFixedLengthResponse(Status.OK, "audio/wav", inputStream, -1);
                } catch (Exception e) {
                    return newFixedLengthResponse(Status.INTERNAL_ERROR, NanoHTTPD.MIME_PLAINTEXT, "Error: " + e.getMessage());
                }
            }
            return newFixedLengthResponse(Status.NOT_FOUND, NanoHTTPD.MIME_PLAINTEXT, "Not Found");
        }
    }

    private void startRecording() {
        isRecording = true;
        recordingThread = new Thread(() -> {
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                return;
            }
            audioRecord = new AudioRecord(MediaRecorder.AudioSource.MIC, SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT, bufferSize);
            audioRecord.startRecording();

            byte[] buffer = new byte[bufferSize];
            try {
                while (isRecording && audioRecord != null) {
                    int read = audioRecord.read(buffer, 0, buffer.length);
                    if (read > 0 && pipedOutputStream != null) {
                        pipedOutputStream.write(buffer, 0, read);
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
        recordingThread.start();
    }

    private void stopRecording() {
        isRecording = false;
        if (audioRecord != null) {
            try {
                audioRecord.stop();
                audioRecord.release();
            } catch (Exception ignored) {}
            audioRecord = null;
        }
        if (pipedOutputStream != null) {
            try {
                pipedOutputStream.close();
            } catch (Exception ignored) {}
            pipedOutputStream = null;
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (server != null) {
            server.stop();
        }
        stopRecording();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_RECORD_AUDIO_PERMISSION) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startAudioServer();
            } else {
                Toast.makeText(this, "Permission Denied", Toast.LENGTH_SHORT).show();
            }
        }
    }
}
