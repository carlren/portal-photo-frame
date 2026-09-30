# Portal Photo Frame

An always-on Android photo frame for Meta Portal hardware. It reads the active frame set from the VPS photo-frame API, caches images locally, and displays a fullscreen slideshow with optional time and weather information.

## Local setup

1. Copy `local.properties.example` to `local.properties`.
2. Replace the example values with the Android SDK path and VPS/weather settings.
3. Build with `./gradlew assembleDebug`.

The password is never placed in a build configuration file. Enter it in the app during setup; the app stores the VPS credentials using Android encrypted preferences. If encrypted storage is unavailable, the app does not fall back to plaintext storage.

`local.properties`, the private project handoff, build output, and signing keys are excluded from Git.

## Configuration

Set `photoFrame.vps.baseUrl` and `photoFrame.vps.username` in ignored `local.properties`. The app asks for the VPS web password on first launch after the update. Leave the weather latitude and longitude blank to disable weather fetching. If `photoFrame.display.timeZone` is blank, the device timezone is used.

## License

Except for the bundled fonts, this project is licensed under the MIT License. See `LICENSE`.

Inter and Plus Jakarta Sans are distributed under the SIL Open Font License, Version 1.1. Their copyright notices and license text are in `FONT_LICENSES.md`.
