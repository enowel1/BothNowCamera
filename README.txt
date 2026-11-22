BothNow Camera — Dual-format native Android camera app

## Features
- Native CameraX camera preview, photo capture, front/rear camera, torch, zoom and tap-to-focus.
- Live 16:9 landscape and 9:16 portrait framing guides.
- Drag the portrait guide to change the portrait crop position. Tap outside the guide to focus.
- Record one source video once, then export Landscape 1920×1080 and Portrait 1080×1920 MP4 files.
- Upload your own PNG/JPG/WEBP logo. The logo appears in the live camera preview and is burned into both exports.
- Drag the live logo, or use the corner/center controls. Adjust size and opacity.
- Videos save to Movies/BothNow; photos save to Pictures/BothNow.
- In-app privacy policy and developer contact details.

## Build configuration
- Application ID: `com.ktma.bothnow` (keep this unchanged to update the existing app).
- Version 2.1, versionCode 3.
- compileSdk 36 / targetSdk 36 / minSdk 29.
- Android Gradle Plugin 8.10.1, Gradle 8.13, Kotlin 2.2.20, Java 17.
- CameraX 1.4.2 and AndroidX Media3 1.6.1.

## Build APK using GitHub Actions
1. Extract the ZIP.
2. Open your existing GitHub repository.
3. Upload/replace the repository files with the CONTENTS of the `BothNowCamera-main` folder. Do not create an extra nested `BothNowCamera-main/BothNowCamera-main` folder.
4. Commit the changes to your main branch.
5. Open the repository's Actions tab and select the latest **Build APK** run.
6. Wait for the workflow to finish successfully. Download the `BothNowCamera-APK` artifact and install the APK on your Android phone.

The APK produced by this workflow is a debug/testing build. It is not the signed release App Bundle needed for a normal Google Play production submission. GitHub-hosted runners generate a temporary debug signing key, so an APK from a later run may not install over an earlier debug APK. If Android reports a signature conflict, uninstall the old app before installing the new debug APK (this clears its local settings/media permissions). For an in-place production update, use the same protected release/upload signing key for every release; never commit the keystore or passwords to GitHub.

## Privacy policy and Play Store
- In-app privacy policy: available from the camera screen.
- Website privacy policy source: `docs/privacy-policy.html`.
- Suggested public URL once hosted on your website: `https://www.ktmaonline.com/privacy-policy/bothnow-camera/`.
- Store release steps and Data safety guidance: `docs/PLAY_STORE_RELEASE_CHECKLIST.md`.

Before using the suggested URL in Play Console, publish the HTML policy on your website and verify the page is publicly accessible over HTTPS. Review the policy against the final app and any future SDK or feature changes.

## Important framing note
The portrait frame is a composition guide. The portrait export uses its position to shift the crop within the part of the camera preview visible on screen. Both outputs are derived from one camera recording; a single recording cannot retain every edge in both aspect ratios. Keep the subject and important action inside the guide area.

## Developer
Designed By: Kekeli Emmanuel  
All Rights Reserved By: Kekeli Tech Media Agency  
+233 26 478 3970 · info@ktmaonline.com · https://www.ktmaonline.com
