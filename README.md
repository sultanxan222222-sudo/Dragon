# Inferno Dragon — Android 3D GPU renderer

This update keeps the existing Android overlay and improves the bundled Three.js renderer.

## Changes in 0.9-gpu-renderer
- Requests a high-performance WebGL context for the phone's GPU.
- Automatically frames and scales the dragon model to fit the screen instead of relying on a fixed camera distance.
- Adds WebGL startup diagnostics, model-loading progress and clearer failure messages.
- Enables shadow rendering and improves lighting/material handling.
- Keeps the existing overlay design and the in-app bite test.

The APK workflow downloads the GLB model and Three.js modules, then builds a debug APK. A successful CI build verifies compilation and packaging; the final visual result still needs to be checked on a real Android device because GPU/WebView behavior and the model's animation data cannot be fully validated in CI.

## Build
Open Actions → Build Inferno Dragon APK, then download the InfernoDragon-debug-apk artifact from a successful run.

Package: com.infernadragon
Version: 0.9-gpu-renderer
