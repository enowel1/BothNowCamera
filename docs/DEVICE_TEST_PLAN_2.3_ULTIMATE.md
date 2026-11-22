# BothNow Camera 2.3 Ultimate — Device Test Plan

Run these checks on the intended Android phone after a successful debug build.

1. Install over the previous build using the same signing key, or preserve the old installation until signing compatibility is confirmed.
2. Launch the app; grant camera permission. Confirm live preview and camera switching.
3. Test tap-to-focus and pinch/slider zoom.
4. Cycle composition guide through Thirds, Centre and OFF. Confirm both landscape and portrait guides update.
5. Cycle photo timer through OFF, 3s, 5s and 10s. Confirm countdown and saved photo.
6. Move exposure compensation across its available range. Confirm preview brightness changes; confirm unsupported devices show the control disabled.
7. Start a video. Confirm elapsed timer updates. Stop and wait for both landscape and portrait exports.
8. Confirm low-storage warning appears when app storage is below the threshold (do not deliberately fill a production phone; use a test device).
9. Open BothNow Gallery. Preview a photo and a video, share each to another app, and delete a test file.
10. Confirm separate portrait and landscape watermarks retain their independent settings in both exported files.
11. Confirm no media is uploaded by the app and privacy policy remains accessible.

Record device model, Android version, test result and any crash/logcat output. A successful GitHub build alone does not replace these tests.
