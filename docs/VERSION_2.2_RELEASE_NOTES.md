# BothNow Camera 2.2 release notes

## Branding
- Replaced the in-app header and Android launcher icon artwork with the supplied BothNow neon camera logo.
- Regenerated legacy density icons and the adaptive launcher foreground with safe padding to reduce launcher-mask cropping.

## Camera controls
- Added pinch-to-zoom on the camera framing overlay while retaining the zoom slider.
- Kept tap-to-focus and portrait-frame dragging.

## Independent watermarks
- One uploaded logo now appears as two separate live preview overlays: one constrained to the landscape guide and one constrained to the portrait guide.
- Landscape and portrait profiles each store their own position, size and opacity.
- Dragging either preview logo updates only that format's saved position.
- Each exported video uses its own watermark settings.
- Existing version 2.1 watermark preferences are migrated to the landscape profile; portrait settings start independently with defaults.

## Build
- Application version: 2.2
- versionCode: 4
- compileSdk / targetSdk: 36
- GitHub Actions installs Android SDK platform 36 and uses Node.js 24-compatible action releases.

## Verification limitation
The source archive and resource files can be inspected here, but a full Android Gradle build and physical-device regression test must be run using GitHub Actions and the Android phone. Do not treat this source update as device-tested until those checks pass.
