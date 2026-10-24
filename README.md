# Study Wage

Study time becomes pocket money. Every hour you study pays you (default $1/h). The app tells you how much you've earned and not yet transferred, so you know exactly what to send to your bank account.

## What it does

- **Hours studied** come from **Toggl Track**: connect once with your API token and finished entries sync automatically
- **Ready to transfer**: earnings you haven't sent to your account yet; tap "I transferred it" once you have
- **Locked-in rate**: you pick your hourly pay once, on first launch
- **Bonuses**: +10% on days over 3 hours; +20% on every day from the 10th day in a row (30+ min a day) while the streak lasts; they stack
- **History** of study sessions and transfers
- **Home-screen widget**: a 4×1 bar with hours studied, earned and to transfer; tap ↻ to sync Toggl, refreshes itself about every 30 minutes
- Everything is saved on the phone

## Install

Open the latest release on your Android phone, download `StudyWage.apk`, and open it. Allow your browser to install unknown apps when Android asks.

## How it's built

- `app/src/main/assets/index.html`: the whole interface (HTML, CSS, JS)
- `app/src/main/java/.../MainActivity.java`: a WebView host that adds phone storage and a Toggl Track API bridge
- `.github/workflows/build.yml`: every push to `main` builds a signed APK and publishes it to Releases

The signing key (`app/study-wage.keystore`) is committed so every build can update the installed app. Keep the repo private if you don't want others able to sign builds with it.
