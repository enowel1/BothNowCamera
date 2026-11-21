BothNow Camera - native Android wrapper

GET AN APK WITHOUT ANDROID STUDIO (GitHub):
1. Create a free GitHub account, make a new repository, and upload ALL files from this folder
   (keep the .github folder). Easiest: "Add file > Upload files" and drag everything in.
2. Open the repository's Actions tab. The "Build APK" run starts by itself (5-8 minutes).
3. Open the finished run, open the Artifacts section, and download "BothNowCamera-APK". Extract it and install app-debug.apk on your phone. If GitHub asks, allow installation from your browser or file manager.

OR WITH ANDROID STUDIO: File > Open this folder, then Build > Build APK(s).

Requires Android 10 (API 29) or newer. Videos save automatically to Gallery > Movies > BothNow. The app requests camera and microphone access when the camera starts. Internet access is used only for external resources such as the default logo; the main camera interface is bundled and works offline.
To change the app later, edit app/src/main/assets/index.html.
