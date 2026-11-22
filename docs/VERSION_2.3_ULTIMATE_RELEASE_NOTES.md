# BothNow Camera 2.3 — Ultimate Edition

## Product direction
A combined professional, broadcast and creator camera workflow, retaining the existing one-take landscape and portrait video export.

## Added in 2.3
- Exposure compensation control using the active CameraX camera's supported compensation range. Devices that do not expose an adjustable range disable this control.
- Composition overlays: rule-of-thirds guides and centre crosshair guides for both the landscape and portrait frames, with an off option.
- Photo self-timer: off, 3 seconds, 5 seconds and 10 seconds.
- Recording elapsed-time display.
- Low-storage warning before recording when available internal app storage is below 250 MB.
- Built-in BothNow media list for media saved in Pictures/BothNow and Movies/BothNow, with preview, share and delete actions.
- Camera settings (composition guide and self-timer) persist on the device.

## Retained features
- CameraX photo and video capture.
- One recording exported as landscape 1920x1080 and portrait 1080x1920.
- Movable portrait crop guide, tap-to-focus, pinch-to-zoom and zoom slider.
- Independent landscape and portrait logo size, opacity and position controls.
- Flash toggle, camera switch, privacy policy and developer information.

## Compatibility notes
- Minimum Android version remains API 29 (Android 10).
- Compile/target SDK remains API 36.
- Exposure adjustment depends on the active camera hardware. Some devices may offer only zero exposure compensation.
- Preview and share actions use compatible apps installed on the device. Delete applies to files in the app's own BothNow media folders.

## Verification status
The source archive, XML resources and workflow configuration should be validated before release. A successful Android Gradle build and physical-device tests are still required. In particular, test camera permissions, photo timer, exposure range, gallery query, media sharing/deletion, and both exported video files on the target phone.
