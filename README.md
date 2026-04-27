# NoxMic - Android Audio Streamer

**NoxMic** is a lightweight Android application that transforms your smartphone into a wireless microphone. It hosts a local HTTP server that streams high-quality, real-time audio from your device's microphone directly to your PC.

## 🚀 Key Features
* **Direct Streaming:** No intermediary servers (like Flask) required.
* **Customizable Ports:** Change the server port via the in-app settings to avoid conflicts.
* **Minimalist UI:** Clean, modern dark interface inspired by professional engineering tools.
* **High Quality:** Streams raw PCM audio at 44100Hz, 16-bit, mono.
* **Low Latency:** Designed for real-time use with the NoxMic desktop client.

## 🛠 How It Works
The app initializes a NanoHTTPD server on your device. When a client requests the `/audio.wav` endpoint, the app begins capturing audio data and "pipes" it into the HTTP response stream. 

## 📦 Building the APK
If you are building the project manually or using GitHub Actions:
1.  Push the source code to your GitHub repository.
2.  Ensure your `material_symbols.ttf` font is located in `app/src/main/res/font/`.
3.  Go to the **Actions** tab -> **Build APK**.
4.  Download the generated APK from the **Artifacts** section once the build is complete.

## 📖 Usage Instructions
1.  **Install & Permissions:** Install the APK on your Android device and grant the **Microphone** permission.
2.  **Configuration:** (Optional) Tap the gear icon (⚙) in the top-right corner to set your preferred port (default is `8080`).
3.  **Start Streaming:** Tap the **START STREAMING** button.
4.  **Connect:** The app will display a URL (e.g., `http://192.168.1.5:8080/audio.wav`).
5.  **Desktop Client:** Enter this URL into your NoxMic Python desktop application.

## ⚙️ Technical Specs
* **Sample Rate:** 44100 Hz
* **Encoding:** PCM 16-bit
* **Channel:** Mono
* **Protocol:** HTTP Chunked Response

---
*Developed by DevNexe.*