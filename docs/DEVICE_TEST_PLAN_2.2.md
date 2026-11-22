# BothNow Camera 2.2 device test plan

Install the newly built debug APK only after GitHub Actions reports success.

1. Confirm the launcher icon and in-app header show the new BothNow neon artwork without clipping.
2. Open the camera and verify the landscape and portrait frame guides.
3. Pinch inward/outward on the camera preview and confirm zoom changes; verify the zoom slider still works.
4. Tap the preview outside the portrait frame and confirm tap-to-focus.
5. Upload a logo and confirm two previews appear, one constrained to each frame.
6. Drag the landscape logo; confirm the portrait logo does not move.
7. Select Edit portrait logo, change size/opacity/position; confirm the landscape logo remains unchanged.
8. Move the portrait frame; confirm the portrait logo stays positioned relative to that frame.
9. Record a short clip and verify both exported videos use their own watermark size, opacity and position.
10. Confirm both videos play and are saved under Movies/BothNow.

Do not consider the release verified until these checks pass on the target Android phone.
