BothNow Camera 3.1 Broadcast & Studio Edition — native Android dual-format camera

## Product editions combined
Professional camera controls, broadcast composition tools and creator media workflows in one app.

## Features
- Live CameraX microphone VU meter during recording, with green/yellow/red segments and an audio peak warning at 85% amplitude. The meter uses CameraX recording audio statistics and does not open a second microphone capture session.
- Accelerometer-based electronic horizon with live roll angle and on/off control.
- Recording state, elapsed time, microphone-enabled state and live audio level/peak feedback.
- Native CameraX camera preview, photo capture, front/rear camera, torch, tap-to-focus and zoom slider/pinch zoom.
- Exposure compensation slider using the active camera hardware's supported range; unavailable ranges disable the control.
- 16:9 landscape and 9:16 portrait framing guides; move the portrait guide to set the portrait crop.
- Composition overlays: rule of thirds, centre crosshair or off.
- Photo self-timer: off, 3, 5 or 10 seconds.
- One source video exported as Landscape 1920x1080 and Portrait 1080x1920 MP4 files.
- Recording elapsed-time display and low-storage warning before recording.
- Upload one PNG/JPG/WEBP logo and show independent landscape and portrait live watermark previews. Configure position, size and opacity separately.
- BothNow Gallery lists media saved in Pictures/BothNow and Movies/BothNow, with preview, user-initiated share and delete actions.
- In-app privacy policy and developer contact details.

## Build configuration
- Application ID: com.ktma.bothnow (unchanged).
- Version 3.1, versionCode 7.
- compileSdk 36 / targetSdk 36 / minSdk 29.
- Android Gradle Plugin 8.10.1, Gradle 8.13, Kotlin 2.2.20, Java 17.
- CameraX 1.4.2 and AndroidX Media3 1.6.1.

## Build APK using GitHub Actions
1. Extract this ZIP.
2. Open the existing GitHub repository.
3. Upload/replace the repository files with the CONTENTS of the BothNowCamera-main folder. Do not create a nested duplicate folder.
4. Confirm .github/workflows/build.yml is present at that exact path.
5. Commit the changes.
6. Open Actions, select the latest Build APK run and wait for success.
7. Download the BothNowCamera-APK artifact and extract app-debug.apk.

The workflow builds a debug/testing APK. It is not a signed release Android App Bundle for Google Play. GitHub Actions debug signing may use a different key on a later run, so Android may reject an update over a previously installed debug APK. Preserve the existing app and its data until you have confirmed signing compatibility. For production updates, protect and reuse the same release signing key; never commit a keystore or passwords to GitHub.

## Privacy and Play Store
- In-app privacy policy is available from the camera screen.
- Website policy source: docs/privacy-policy.html. Publish it on your website and verify public HTTPS access before entering its URL in Play Console.
- Review docs/PLAY_STORE_RELEASE_CHECKLIST.md and docs/DEVICE_TEST_PLAN_3.1_BROADCAST_STUDIO.md before release.
- The app uses Android's scoped MediaStore access to list its own BothNow media folders; it does not request broad photo/video library permission. Sharing occurs only when the user chooses Share and selects another app.

## Important framing note
The portrait frame is a composition guide. The portrait export uses its position to shift the crop within the visible camera preview area. A single recording cannot retain every edge in both aspect ratios. Keep the subject and important action inside the portrait guide.

## Developer
Designed By: Kekeli Emmanuel
All Rights Reserved By: Kekeli Tech Media Agency
+233 26 478 3970 | info@ktmaonline.com | https://www.ktmaonline.com

VERSION 3.2 UPDATE
- BothNow Gallery now opens videos in an in-app player and photos in an in-app preview.
- Share and Delete actions are available from each preview.
- Landscape orientation places the camera preview beside a scrollable tool panel.
- Rotation changes rearrange the interface without intentionally restarting an active recording.
- Every completed video continues to export as both Landscape 16:9 and Portrait 9:16.
See docs/VERSION_3.2_GALLERY_ROTATION_RELEASE_NOTES.md for details.
