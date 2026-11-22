# BothNow Camera 3.1 — Device Test Plan

1. Install the debug APK on Android 10 or later and grant camera permission.
2. Confirm landscape and portrait frame guides, thirds/centre composition guides and portrait frame movement.
3. Toggle Horizon ON/OFF; rotate and tilt the phone and confirm the roll line/readout responds smoothly.
4. Start recording and grant microphone permission. Speak at normal volume; confirm the segmented VU meter moves.
5. Increase input loudness and confirm the peak warning appears at approximately 85% of reported amplitude.
6. Record without microphone permission (or deny it) and confirm video-only status is clear and the app remains usable.
7. Stop recording; confirm the VU meter resets and both landscape 1920x1080 and portrait 1080x1920 exports complete.
8. Confirm teleprompter remains usable while recording and does not become part of the exported video.
9. Confirm both exports are saved to the BothNow Movies gallery and user watermarks are applied independently.
10. Test front/rear cameras and low-light conditions; verify horizon stability and microphone levels on the target device.

Note: The audio meter is active during recording via CameraX statistics. It does not perform pre-recording microphone monitoring or automatic gain control.
