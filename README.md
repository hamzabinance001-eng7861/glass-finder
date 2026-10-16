# Glass Finder

Offline Android app for finding which box holds the tempered glass for a phone model.

- **Add box**: photograph a box (or pick a picture). The app reads the phone models off it
  (on-device OCR, no internet), you fix any mistakes, type the box number, and save.
- **Find**: type a phone model. The app shows the box number(s) and the box photo.
- **Boxes**: browse, edit or delete saved boxes.

Everything (SQLite database + box photos) is stored on the phone. Nothing is uploaded.

## Get the APK (GitHub Actions)

1. Create a new empty repository on GitHub.
2. Upload this whole folder to it (keep the `.github` folder), or push with git.
3. Open the repo's **Actions** tab, then the **Build APK** run (about 5 minutes).
   You can also start it with **Run workflow**.
4. When it is green, open the run and download the **GlassFinder-apk** artifact (a zip containing `app-debug.apk`).
5. Copy the APK to your phone and open it. Allow "install from unknown sources" when asked.

## Build locally instead

Open this folder in Android Studio and choose Build > Build APK(s).
