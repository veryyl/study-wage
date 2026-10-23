# Study Wage

Study time becomes pocket money. Every hour you study pays you (default $1/h). The app tells you how much you've earned and not yet transferred, so you know exactly what to send to your bank account.

## What it does

- **Clock in** with the built-in timer, log hours by hand, or **sync from Toggl Track** with your API token
- **Ready to transfer**: earnings you haven't sent to your account yet; tap "I transferred it" once you have
- **History** of study sessions and transfers
- Everything is saved on the phone

## Install

Open the latest release on your Android phone, download `StudyWage.apk`, and open it. Allow your browser to install unknown apps when Android asks.

## How it's built

- `app/src/main/assets/index.html`: the whole interface (HTML, CSS, JS)
- `app/src/main/java/.../MainActivity.java`: a WebView host that adds phone storage and a Toggl Track API bridge
- `.github/workflows/build.yml`: every push to `main` builds a signed APK and publishes it to Releases

The signing key (`app/study-wage.keystore`) is committed so every build can update the installed app. Keep the repo private if you don't want others able to sign builds with it.
