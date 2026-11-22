# BothNow Camera 3.1 — Broadcast & Studio Edition

## Added
- Live microphone VU meter driven by CameraX `RecordingStats.audioStats.audioAmplitude` while a recording is active.
- Segmented green/yellow/red level display and peak warning at 85% amplitude.
- Accelerometer-based electronic horizon with a numeric roll readout and user toggle.
- Recording audio state and live input level feedback.

## Preserved
- Application ID `com.ktma.bothnow`; minSdk 29; target/compile SDK 36.
- CameraX capture, teleprompter, composition guides, timers, zoom/exposure, gallery and automatic MediaStore saving.
- One recording exported to landscape 1920x1080 and portrait 1080x1920, with independent user-configurable watermarks.

## Audio meter behavior
The VU meter reads CameraX recording statistics, rather than opening a second `AudioRecord` session that could contend with CameraX for microphone access. Therefore, the live meter becomes active when recording starts. A peak warning is a level indicator, not an automatic gain control; reduce the microphone/input level if the warning persists.

## Build
GitHub Actions builds the debug APK. Physical-device testing is required for microphone permission, audio meter response, sensor orientation and dual-format export.
