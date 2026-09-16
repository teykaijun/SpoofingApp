# Location Spoofer

Replace your Android device's location with one you choose, using Android's official mock
location API — either from the phone itself or from a Windows PC. Useful for testing
location-based apps, geofences and navigation flows without leaving your desk.

| Folder | What it is |
| --- | --- |
| [`app/`](app) | Android app (Kotlin, Jetpack Compose) that feeds the chosen location into the device's location stack. |
| [`windows/`](windows) | Windows desktop controller (.NET 10, WPF + WebView2) that drives a connected phone or emulator over adb. |

---

## Android app

### Features

- **Fixed point:** tap the map, search for a place, paste coordinates, or pick a favorite.
- **Route simulation:** tap waypoints and move along them at walking, running, cycling, driving
  or custom speed (1–150 km/h), with optional looping. Speed and bearing are reported too.
- **Covers the whole location stack:** the platform GPS and network providers (plus the
  platform fused provider on Android 12+) and Google Play services' fused location provider,
  which most apps, including Google Maps, read from.
- **Keeps running in the background** as a foreground service, with a Stop button in the
  notification.
- **Live changes:** apply a new point, speed or accuracy while spoofing. Changing only speed,
  loop or accuracy keeps your position on the route.
- **OpenStreetMap** map with no API key, favorites, and adjustable accuracy and update interval.

Requires Android 8.0 (API 26) or newer.

### Build and install

```bash
gradlew.bat assembleDebug
```

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Or open the folder in Android Studio and press **Run**. Building needs JDK 17+ and the Android
SDK, both of which Android Studio bundles.

### One-time setup on the phone

Android only accepts mock locations from the app chosen in Developer options. The app shows a
setup card with shortcuts until these steps are done:

1. **Enable Developer options:** Settings › About phone › tap **Build number** 7 times.
2. **Select the mock location app:** Settings › System › Developer options ›
   **Select mock location app** › **Location Spoofer**.
3. **Allow location access** when prompted. Android requires it for location foreground
   services. Allow notifications too, so the Stop button shows in the notification shade.

The Windows controller can do all of this over adb — see **Prepare device** below.

### Using it

1. Choose **Fixed point** and tap the map, or search (`Eiffel Tower`, or `48.85837, 2.29448`).
2. Or choose **Route**, tap two or more waypoints, then pick a speed.
3. Tap **Start spoofing**. A blue dot shows the position being reported.
4. Tap **Stop** in the app or the notification to restore your real location.

---

## Windows controller

A desktop app for picking locations on a big screen and pushing them to your phone or emulator.

- Lists every device and emulator adb can see.
- **Prepare device** grants everything over adb: it makes the app the selected mock location app
  and grants the location and notification permissions, so Developer options never has to be
  opened.
- **Install APK…** sideloads the app onto the selected device.
- Fixed point or multi-waypoint route with speed and looping, handed to the phone app, which
  runs the simulation itself.
- **Emulators** need no app at all — the PC steps them along the route once per second with
  `adb emu geo fix`.
- Place search (OpenStreetMap Nominatim), coordinate paste, and a log of every adb command.

### Requirements

- Windows 10 or 11, x64
- Android SDK platform-tools (`adb`), found automatically via `ANDROID_HOME`,
  `ANDROID_SDK_ROOT`, `%LOCALAPPDATA%\Android\Sdk`, or `PATH`
- Microsoft Edge WebView2 Runtime (preinstalled on Windows 11)
- The release zip is self-contained; .NET is only needed to build it yourself (SDK 10.0+)

### Build and run

```bash
dotnet run --project windows/SpoofingApp.Desktop
```

```bash
dotnet publish windows/SpoofingApp.Desktop -c Release -r win-x64 --self-contained true -o out
```

---

## Remote control over adb

The phone app accepts commands directly, which is how the Windows controller drives it. Start a
route:

```bash
adb shell am start -n com.spoofingmobileapp/.MainActivity -a com.spoofingmobileapp.action.REMOTE --es command start --es points '48.8584,2.2945;48.8606,2.3376' --es speed 18 --es loop true
```

Stop:

```bash
adb shell am start -n com.spoofingmobileapp/.MainActivity -a com.spoofingmobileapp.action.REMOTE --es command stop
```

Extras are all strings, because that is what `adb shell am` passes reliably: `points` is
`lat,lng` pairs separated by `;`, `speed` is km/h, `loop` is `true`/`false`, and `accuracy` is
metres. A command is applied once the activity resumes, since Android only lets a location
foreground service start while the app is actually in the foreground.

Becoming the mock location app without touching Developer options:

```bash
adb shell appops set com.spoofingmobileapp android:mock_location allow
```

## How it works

`MockLocationService` is a foreground service. It registers test providers through
`LocationManager.addTestProvider`, switches Play services into mock mode with
`FusedLocationProviderClient.setMockMode`, and pushes a fresh `Location` every update interval,
because Android discards mock fixes that stop updating. `RouteSimulator` interpolates positions
along great-circle segments; the Windows app carries a C# port of the same maths for emulators.

```
app/src/main/java/com/spoofingmobileapp/
├── spoof/   MockLocationService, MockLocationPusher, MockLocationSetup, SpoofConfig, RemoteCommand
├── geo/     LatLng, GeoMath, RouteSimulator, CoordinateParser, PlaceSearch, Units
├── data/    FavoritesRepository, SettingsRepository
└── ui/      MainScreen, SpoofMap (osmdroid), ControlPanel, SearchBox, SetupCard, dialogs

windows/SpoofingApp.Desktop/
├── Adb/       AdbClient, AdbDevice parsing
├── Spoofing/  SpoofCommands, GeoMath, RouteSimulator, EmulatorRoutePlayer
├── Web/       the UI (Leaflet map) hosted in WebView2
└── MainWindow.xaml.cs   bridge between the page and adb
```

## Tests

```bash
gradlew.bat testDebugUnitTest
```

```bash
dotnet test windows/SpoofingApp.slnx
```

## Limitations

- Apps can tell a location is mocked (`Location.isMock()`). Some apps, such as banking,
  anti-cheat or attendance apps, deliberately reject mock locations.
- **Windows itself cannot be spoofed.** Windows has no mock location API, so the desktop app
  controls Android devices rather than faking the PC's own position; doing that would need a
  virtual GPS driver.
- Place search on the phone uses the device's built-in geocoder, which needs Google Play
  services and a network connection. Typing coordinates always works.
- Map data and tiles come from OpenStreetMap. Please respect its
  [tile usage policy](https://operations.osmfoundation.org/policies/tiles/) and
  [Nominatim usage policy](https://operations.osmfoundation.org/policies/nominatim/).

## License

[MIT](LICENSE) © kjkaijun

Map data and tiles © OpenStreetMap contributors. This project also depends on osmdroid
(Apache-2.0), Google Play services Location and Microsoft WebView2, which carry their own terms.
