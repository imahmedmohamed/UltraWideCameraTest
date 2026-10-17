# UltraWide Camera Test

A small Android Camera2 diagnostic app. It opens the rear logical camera (prefers ID 0), starts preview at 0.6x, and displays the reported zoom ratio and active physical camera ID when Android provides them. Buttons switch between 0.6x and 1.0x.

## Build
- Open this folder in Android Studio (recent version with Android Gradle Plugin 8.7.3 support).
- Allow Gradle to sync and install the required Android SDK Platform 35 / Build Tools.
- Build > Build APK(s), then install the generated debug APK on the phone.

## Notes
- Requires Android 9+; `CONTROL_ZOOM_RATIO` is available on Android 11+.
- On Android 11+, this tests whether the logical camera accepts 0.6x. The reported active physical ID can be absent even if switching works.
- This is a diagnostic preview, not a photo-saving camera app. No internet permission and no root required.
