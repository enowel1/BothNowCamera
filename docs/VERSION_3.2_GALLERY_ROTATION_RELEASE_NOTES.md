# BothNow Camera 3.2 – Gallery & Rotation Update

## Gallery
- Replaced the text-only item action flow with a scrollable BothNow Gallery list.
- Tapping a video opens an in-app video preview with playback controls.
- Tapping a photo opens an in-app image preview.
- Preview screens provide Share and Delete actions.
- Sharing uses Android's Sharesheet with a content URI, ClipData, and temporary read permission so compatible social and messaging apps can receive the media.
- Gallery continues to show app-owned content in `Movies/BothNow/` and `Pictures/BothNow/`.

## Orientation and recording
- The camera interface switches to a side-by-side landscape workspace: live preview on the left, vertically scrollable controls on the right.
- Portrait layout remains vertically stacked and scrollable.
- Orientation and screen-size changes are handled without recreating the activity, allowing the active CameraX recording session to continue while the interface rearranges.
- The existing dual-export workflow remains in place and creates both Landscape 16:9 and Portrait 9:16 video files after recording, independent of the phone's orientation during capture.

## Validation
- ZIP structure and source-level delimiter checks completed.
- A full Android Gradle build and physical-device playback/share/rotation test still need to be run in GitHub Actions and on a device.
