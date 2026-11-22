# Google Play release checklist — BothNow Camera

This repository contains technical preparation, not store approval. Complete these items in Play Console before submission.

## Required developer actions
1. Host `docs/privacy-policy.html` at a public, stable HTTPS URL under ktmaonline.com (suggested path: `https://www.ktmaonline.com/privacy-policy/bothnow-camera/`). Confirm it loads without login and exactly matches the in-app policy.
2. Replace the suggested URL in the Play Console privacy-policy field only after it is live. The in-app policy is already available from the camera screen.
3. Complete the Data safety form based on the final shipped binary and all SDKs. The current source is designed for on-device processing only and contains no analytics/ad SDK, but verify the merged manifest and final binary before declaring anything.
4. Declare camera and microphone permission use accurately. Camera is required for the app's core function; microphone is optional and requested only for recording audio.
5. Complete App content declarations: ads (no ads in this source), target audience/children, content rating, app access, and any other applicable declarations.
6. Prepare store listing assets: app name, short/full descriptions, 512×512 icon, phone screenshots, feature graphic, support contact, and category.
7. Test on real devices: camera permission denied/granted, microphone denied/granted, front/rear cameras, flash where available, focus, zoom, frame dragging, logo upload/drag/center/size/opacity, recording, both exports, Gallery saving, interruption, low storage, and Android 16.
8. Create a signed Android App Bundle (AAB) using a protected upload key. Keep the keystore and passwords out of GitHub. Configure Play App Signing in Play Console.
9. Upload to an internal testing track first, review pre-launch reports, fix issues, then submit for production review.

## Current technical settings
- Application ID: `com.ktma.bothnow` (must remain unchanged to update the existing installation/listing)
- Version: `2.1` / versionCode `3`
- compileSdk / targetSdk: `36`
- minSdk: `29`
- Java: `17`
- GitHub Actions currently builds a debug APK artifact. A debug APK is for testing only; it is not the signed release AAB required for a normal Google Play production release.

## Important
Google Play requires new apps and app updates to target Android 16 (API 36) from 31 August 2026. Store acceptance also depends on Play Console declarations, policy review, signing, app quality, and actual device tests. No source-code change can guarantee acceptance by Google Play or another store.
