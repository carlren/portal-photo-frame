# Portal Photo Frame

An always-on Android photo frame for Meta Portal hardware. It reads the active frame set from the VPS photo-frame API, caches images locally, and displays a fullscreen slideshow with optional time and weather information.

## Local setup

1. Copy `local.properties.example` to `local.properties`.
2. Replace the example values with the Android SDK path and VPS/weather settings.
3. Build with `./gradlew assembleDebug`.

By default, enter the password in the app during setup; credentials are stored using Android encrypted preferences. If encrypted storage is unavailable, the app does not fall back to plaintext storage.

For a personal build, an optional `photoFrame.vps.password` in ignored `local.properties` enables automatic login with the configured username. That password is embedded in the resulting APK: keep that APK private and exclude generated BuildConfig files from public releases. No credential values belong in source control.

`local.properties`, the private project handoff, build output, and signing keys are excluded from Git.

## Configuration

Set `photoFrame.vps.baseUrl` and `photoFrame.vps.username` in ignored `local.properties`. The app asks for the VPS web password unless the personal-build option above is configured. Leave the weather latitude and longitude blank to disable weather fetching. If `photoFrame.display.timeZone` is blank, the device timezone is used.

## License

Except for the bundled fonts, this project is licensed under the MIT License. See `LICENSE`.

Inter and Plus Jakarta Sans are distributed under the SIL Open Font License, Version 1.1. Their copyright notices and license text are in `FONT_LICENSES.md`.
