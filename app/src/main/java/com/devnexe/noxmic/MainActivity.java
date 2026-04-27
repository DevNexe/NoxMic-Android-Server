package com.devnexe.noxmic;

import android.Manifest;
import android.app.AlertDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.text.format.Formatter;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.res.ResourcesCompat;

import org.nanohttpd.protocols.http.IHTTPSession;
import org.nanohttpd.protocols.http.NanoHTTPD;
import org.nanohttpd.protocols.http.response.Response;
import org.nanohttpd.protocols.http.response.Status;

import java.io.PipedInputStream;
import java.io.PipedOutputStream;

public class MainActivity extends AppCompatActivity {

    private static final int REQUEST_RECORD_AUDIO_PERMISSION = 200;
    private static final int SAMPLE_RATE = 44100;
    private static final int CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO;
    private static final int AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT;

    private AudioServer server;
    private AudioRecord audioRecord;
    private int bufferSize;
    private boolean isRecording = false;
    private Thread recordingThread;
    private PipedOutputStream pipedOutputStream;

    private TextView tvStatus;
    private TextView tvIp;
    private Button btnToggle;
    private Button btnSettings;

    private SharedPreferences prefs;
    private int currentPort;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        prefs = getSharedPreferences("NoxMicPrefs", MODE_PRIVATE);
        currentPort = prefs.getInt("server_port", 8080);

        tvIp = findViewById(R.id.tvIp);
        tvStatus = findViewById(R.id.tvStatus);
        btnToggle = findViewById(R.id.btnToggle);
        btnSettings = findViewById(R.id.btnSettings);

        try {
            Typeface iconFont = ResourcesCompat.getFont(this, R.font.material_symbols);
            btnSettings.setTypeface(iconFont);
        } catch (Exception e) {
            e.printStackTrace();
        }

        if (tvIp != null) tvIp.setText("NOXMIC");
        
        tvStatus.setText("READY TO CONNECT");

        bufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT);

        btnToggle.setOnClickListener(v -> {
            if (!isRecording) {
                startAudioServer();
            } else {
                stopRecording();
                if (server != null) server.stop();
                tvStatus.setText("READY TO CONNECT");
            }
        });

        btnSettings.setOnClickListener(v -> showSettingsDialog());

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.RECORD_AUDIO}, REQUEST_RECORD_AUDIO_PERMISSION);
        }
    }

    private void showSettingsDialog() {
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_settings, null);
        EditText etPort = dialogView.findViewById(R.id.etPort);
        Button btnSave = dialogView.findViewById(R.id.btnSaveSettings);
        
        etPort.setText(String.valueOf(currentPort));

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setView(dialogView)
                .create();

        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }

        btnSave.setOnClickListener(v -> {
            String newPortStr = etPort.getText().toString();
            if (!newPortStr.isEmpty()) {
                currentPort = Integer.parseInt(newPortStr);
                prefs.edit().putInt("server_port", currentPort).apply();
                
                if (isRecording) {
                    stopRecording();
                    if (server != null) server.stop();
                    startAudioServer();
                }
                dialog.dismiss();
            }
        });

        dialog.show();
    }

    private void updateStatusLabel() {
        WifiManager wifiManager = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        int ipAddress = wifiManager.getConnectionInfo().getIpAddress();
        if (ipAddress == 0) {
            tvStatus.setText("NO WI-FI CONNECTION");
            return;
        }
        String ip = Formatter.formatIpAddress(ipAddress);
        tvStatus.setText("http://" + ip + ":" + currentPort + "/audio.wav");
    }

    private void startAudioServer() {
        try {
            if (server != null) server.stop();
            server = new AudioServer(currentPort);
            server.start(NanoHTTPD.SOCKET_READ_TIMEOUT, false);
            updateStatusLabel();
        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(this, "Port " + currentPort + " is busy", Toast.LENGTH_SHORT).show();
        }
    }

    private class AudioServer extends NanoHTTPD {
        public AudioServer(int port) { super(port); }

        @Override
        public Response handle(IHTTPSession session) {
            if (session.getUri().equals("/audio.wav")) {
                stopRecording();
                try {
                    PipedInputStream inputStream = new PipedInputStream();
                    pipedOutputStream = new PipedOutputStream(inputStream);
                    startRecording();
                    return Response.newChunkedResponse(Status.OK, "audio/wav", inputStream);
                } catch (Exception e) {
                    return Response.newFixedLengthResponse(Status.INTERNAL_ERROR, NanoHTTPD.MIME_PLAINTEXT, e.getMessage());
                }
            }
            return Response.newFixedLengthResponse(Status.NOT_FOUND, NanoHTTPD.MIME_PLAINTEXT, "Not Found");
        }
    }

    private void startRecording() {
        isRecording = true;
        runOnUiThread(() -> {
            btnToggle.setText("STOP STREAMING");
            btnToggle.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor("#A11D1D")));
        });

        recordingThread = new Thread(() -> {
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) return;
            
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
        runOnUiThread(() -> {
            btnToggle.setText("START STREAMING");
            btnToggle.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor("#0078D4")));
        });

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
        if (server != null) server.stop();
        stopRecording();
    }
}