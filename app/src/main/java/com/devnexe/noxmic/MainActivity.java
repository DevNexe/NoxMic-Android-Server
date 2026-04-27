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

import fi.iki.elonen.NanoHTTPD;

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
    private boolean isServerRunning = false;
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
        } catch (Exception e) { e.printStackTrace(); }

        if (tvIp != null) tvIp.setText("NoxMic");
        tvStatus.setText("READY TO START");

        bufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT);

        btnToggle.setOnClickListener(v -> {
            if (!isServerRunning) {
                startAudioServer();
            } else {
                stopAudioServer();
            }
        });

        btnSettings.setOnClickListener(v -> showSettingsDialog());

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.RECORD_AUDIO}, REQUEST_RECORD_AUDIO_PERMISSION);
        }
    }

    // Метод для динамического обновления UI из любого потока
    private void updateUiState(boolean activeStream) {
        runOnUiThread(() -> {
            if (activeStream) {
                btnToggle.setText("STOP STREAMING");
                btnToggle.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor("#A11D1D")));
                updateStatusLabel();
            } else {
                btnToggle.setText("START STREAMING");
                btnToggle.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor("#0078D4")));
                if (isServerRunning) updateStatusLabel();
                else tvStatus.setText("OFFLINE");
            }
        });
    }

    private void startAudioServer() {
        try {
            if (server != null) server.stop();
            server = new AudioServer(currentPort);
            server.start(NanoHTTPD.SOCKET_READ_TIMEOUT, false);
            isServerRunning = true;
            updateUiState(false); // Сервер запущен, но стрима пока нет
        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(this, "Error starting server", Toast.LENGTH_SHORT).show();
        }
    }

    private void stopAudioServer() {
        isServerRunning = false;
        stopRecording();
        if (server != null) server.stop();
        updateUiState(false);
        tvStatus.setText("READY TO START");
    }

    private void updateStatusLabel() {
        WifiManager wifiManager = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        int ipAddress = wifiManager.getConnectionInfo().getIpAddress();
        if (ipAddress == 0) {
            tvStatus.setText("No WI-FI connection");
            return;
        }
        String ip = Formatter.formatIpAddress(ipAddress);
        tvStatus.setText("http://" + ip + ":" + currentPort + "/audio.wav");
    }

    private class AudioServer extends NanoHTTPD {
        public AudioServer(int port) { super(port); }

        @Override
        public Response serve(IHTTPSession session) {
            if ("/audio.wav".equals(session.getUri())) {
                stopRecording(); // Сброс предыдущей сессии, если была
                try {
                    PipedInputStream inputStream = new PipedInputStream(bufferSize * 10);
                    pipedOutputStream = new PipedOutputStream(inputStream);
                    
                    startRecording();
                    updateUiState(true); // Динамически меняем кнопку на красную

                    Response res = newChunkedResponse(Response.Status.OK, "audio/wav", inputStream);
                    res.addHeader("Connection", "close"); 
                    return res;
                } catch (Exception e) {
                    updateUiState(false);
                    return newFixedLengthResponse(Response.Status.INTERNAL_ERROR, NanoHTTPD.MIME_PLAINTEXT, e.getMessage());
                }
            }
            return newFixedLengthResponse(Response.Status.NOT_FOUND, NanoHTTPD.MIME_PLAINTEXT, "Not Found");
        }
    }

    private void startRecording() {
        isRecording = true;
        recordingThread = new Thread(() -> {
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) return;
            
            try {
                audioRecord = new AudioRecord(MediaRecorder.AudioSource.MIC, SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT, bufferSize);
                audioRecord.startRecording();

                byte[] buffer = new byte[bufferSize];
                while (isRecording && audioRecord != null) {
                    int read = audioRecord.read(buffer, 0, buffer.length);
                    if (read > 0 && pipedOutputStream != null) {
                        pipedOutputStream.write(buffer, 0, read);
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
                updateUiState(false);
            } finally {
                stopRecording();
            }
        });
        recordingThread.start();
    }

    private void stopRecording() {
        isRecording = false;
        updateUiState(false); // Возвращаем кнопку в синий цвет

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

    private void showSettingsDialog() {
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_settings, null);
        EditText etPort = dialogView.findViewById(R.id.etPort);
        Button btnSave = dialogView.findViewById(R.id.btnSaveSettings);
        
        etPort.setText(String.valueOf(currentPort));
        AlertDialog dialog = new AlertDialog.Builder(this).setView(dialogView).create();
        if (dialog.getWindow() != null) dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);

        btnSave.setOnClickListener(v -> {
            String newPortStr = etPort.getText().toString();
            if (!newPortStr.isEmpty()) {
                currentPort = Integer.parseInt(newPortStr);
                prefs.edit().putInt("server_port", currentPort).apply();
                if (isServerRunning) {
                    stopAudioServer();
                    startAudioServer();
                }
                dialog.dismiss();
            }
        });
        dialog.show();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopAudioServer();
    }
}