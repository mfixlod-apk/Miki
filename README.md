# MFIX POS Android APK

This repository runs a reproducible GitHub Actions build of the complete MFIX POS Android app from the canonical source repository.

## Download the APK
1. Open [Actions](https://github.com/mfixlod-apk/Miki/actions).
2. Open the latest successful **Build MFIX Android APK** run.
3. Under **Artifacts**, download **MFIX-POS-APK** and extract `app-release.apk`.
4. Install it on the Android device. Android may ask you to allow installation from the browser or file manager.

## Source
The workflow clones [mfixlod-apk/Mfix-pos](https://github.com/mfixlod-apk/Mfix-pos), validates its full web assets and 19 content parts, builds the release APK, and verifies the APK signature before uploading it.

**Note:** The APK is only ready to download after the workflow completes successfully. A failed or running build is not a finished APK.
