using System.Globalization;

namespace SpoofingApp.Desktop.Spoofing;

/// <summary>
/// Builds the adb argument lists used to drive spoofing. Kept free of process handling so the
/// exact commands can be unit tested.
/// </summary>
public static class SpoofCommands
{
    public const string PackageName = "com.spoofingmobileapp";
    public const string Component = PackageName + "/.MainActivity";
    public const string RemoteAction = PackageName + ".action.REMOTE";

    /// <summary>Tells the phone app to start spoofing a point or a route.</summary>
    public static List<string> Start(
        IReadOnlyList<LatLng> waypoints,
        double? speedKmh = null,
        bool loop = false,
        double? accuracyMeters = null)
    {
        ArgumentOutOfRangeException.ThrowIfZero(waypoints.Count);
        var args = RemoteIntent("start");
        args.AddRange(["--es", "points", Quote(string.Join(";", waypoints.Select(w => w.ToPointString())))]);
        if (speedKmh is > 0) args.AddRange(["--es", "speed", Number(speedKmh.Value)]);
        args.AddRange(["--es", "loop", loop ? "true" : "false"]);
        if (accuracyMeters is > 0) args.AddRange(["--es", "accuracy", Number(accuracyMeters.Value)]);
        return args;
    }

    public static List<string> Stop() => RemoteIntent("stop");

    /// <summary>Emulators take positions directly on the console, longitude first.</summary>
    public static List<string> EmulatorGeoFix(LatLng position) =>
        ["emu", "geo", "fix", Number(position.Longitude), Number(position.Latitude)];

    /// <summary>Makes this app the selected mock location app without touching Developer options.</summary>
    public static List<string> AllowMockLocation() =>
        ["shell", "appops", "set", PackageName, "android:mock_location", "allow"];

    public static List<string> GrantPermission(string permission) =>
        ["shell", "pm", "grant", PackageName, permission];

    public static List<string> QueryMockLocation() =>
        ["shell", "appops", "get", PackageName, "android:mock_location"];

    public static List<string> QueryInstalled() =>
        ["shell", "pm", "list", "packages", PackageName];

    public static List<string> Install(string apkPath) => ["install", "-r", apkPath];

    public static bool ParseMockLocationAllowed(string appOpsOutput) =>
        appOpsOutput.Contains("mock_location: allow", StringComparison.OrdinalIgnoreCase);

    public static bool ParseInstalled(string packageListOutput) =>
        packageListOutput.Contains("package:" + PackageName, StringComparison.Ordinal);

    private static List<string> RemoteIntent(string command) =>
        ["shell", "am", "start", "-n", Component, "-a", RemoteAction, "--es", "command", command];

    private static string Number(double value) => value.ToString("0.######", CultureInfo.InvariantCulture);

    /// <summary>The device shell splits on ';', so point lists travel inside single quotes.</summary>
    private static string Quote(string value) => "'" + value + "'";
}
