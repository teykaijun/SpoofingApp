# Location Spoofer

An Android app that replaces your device's GPS location with one you choose, using Android's
official mock location API. It's useful for testing location-based apps, geofences and
navigation flows without leaving your desk.

## Features

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
- **OpenStreetMap** map with no API key, favorites, and adjustable accuracy and update
  interval.

## Requirements

- Android 8.0 (API 26) or newer
- JDK 17+ and the Android SDK to build (Android Studio bundles both)

## Build and install

```bash
gradlew.bat assembleDebug
```

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Or open the folder in Android Studio and press **Run**.

## One-time setup on the phone

Android only accepts mock locations from the app chosen in Developer options. The app shows
a setup card with shortcuts until these steps are done:

1. **Enable Developer options:** Settings › About phone › tap **Build number** 7 times.
2. **Select the mock location app:** Settings › System › Developer options ›
   **Select mock location app** › **Location Spoofer**.
3. **Allow location access** when prompted. Android requires it for location foreground
   services. Allow notifications too, so the Stop button shows in the notification shade.

## Using it

1. Choose **Fixed point** and tap the map, or search (`Eiffel Tower`, or `48.85837, 2.29448`).
2. Or choose **Route**, tap two or more waypoints, then pick a speed.
3. Tap **Start spoofing**. A blue dot shows the position being reported.
4. Tap **Stop** in the app or the notification to restore your real location.

## How it works

`MockLocationService` is a foreground service. It registers test providers through
`LocationManager.addTestProvider`, switches Play services into mock mode with
`FusedLocationProviderClient.setMockMode`, and pushes a fresh `Location` every update
interval, because Android discards mock fixes that stop updating. `RouteSimulator`
interpolates positions along great-circle segments.

```
app/src/main/java/com/spoofingmobileapp/
├── spoof/   MockLocationService, MockLocationPusher, MockLocationSetup, SpoofConfig, SpoofSession
├── geo/     LatLng, GeoMath, RouteSimulator, CoordinateParser, PlaceSearch, Units
├── data/    FavoritesRepository, SettingsRepository
└── ui/      MainScreen, SpoofMap (osmdroid), ControlPanel, SearchBox, SetupCard, dialogs
```

Unit tests for the geometry, route simulation and coordinate parsing:

```bash
gradlew.bat testDebugUnitTest
```

## Limitations

- Apps can tell a location is mocked (`Location.isMock()`). Some apps, such as banking,
  anti-cheat or attendance apps, deliberately reject mock locations.
- Place search uses the device's built-in geocoder, which needs Google Play services and a
  network connection. Typing coordinates always works.
- Map tiles come from OpenStreetMap. Please respect its
  [tile usage policy](https://operations.osmfoundation.org/policies/tiles/).
