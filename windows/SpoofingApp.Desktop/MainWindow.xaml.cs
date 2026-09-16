using System.IO;
using System.Text.Json;
using System.Windows;
using Microsoft.Web.WebView2.Core;
using Microsoft.Win32;
using SpoofingApp.Desktop.Adb;
using SpoofingApp.Desktop.Spoofing;

namespace SpoofingApp.Desktop;

public partial class MainWindow : Window
{
    private static readonly JsonSerializerOptions Json = new(JsonSerializerDefaults.Web);

    private readonly AdbClient? _adb;
    private EmulatorRoutePlayer? _player;

    public MainWindow()
    {
        InitializeComponent();
        var adbPath = AdbClient.Locate();
        _adb = adbPath is null ? null : new AdbClient(adbPath);
        Loaded += OnLoadedAsync;
        Closed += (_, _) => _player?.Dispose();
    }

    private async void OnLoadedAsync(object sender, RoutedEventArgs e)
    {
        try
        {
            await WebView.EnsureCoreWebView2Async();
            var core = WebView.CoreWebView2;
            core.SetVirtualHostNameToFolderMapping(
                "spoofer.local",
                Path.Combine(AppContext.BaseDirectory, "Web"),
                CoreWebView2HostResourceAccessKind.Allow);
            core.Settings.AreDefaultContextMenusEnabled = false;
            core.WebMessageReceived += OnWebMessageAsync;
            core.Navigate("https://spoofer.local/index.html");
        }
        catch (Exception ex)
        {
            MessageBox.Show(
                $"Could not start the embedded browser.\n\n{ex.Message}\n\n" +
                "Install the Microsoft Edge WebView2 Runtime and try again.",
                "Location Spoofer",
                MessageBoxButton.OK,
                MessageBoxImage.Error);
        }
    }

    private async void OnWebMessageAsync(object? sender, CoreWebView2WebMessageReceivedEventArgs e)
    {
        Message? message;
        try
        {
            message = JsonSerializer.Deserialize<Message>(e.WebMessageAsJson, Json);
        }
        catch (JsonException ex)
        {
            Log($"Could not read UI message: {ex.Message}");
            return;
        }
        if (message is null) return;

        try
        {
            switch (message.Type)
            {
                case "ready":
                    Post(new { type = "adb", path = _adb?.AdbPath, found = _adb is not null });
                    await RefreshDevicesAsync();
                    break;
                case "refresh":
                    await RefreshDevicesAsync();
                    break;
                case "status" when message.Serial is not null:
                    await SendStatusAsync(message.Serial);
                    break;
                case "prepare" when message.Serial is not null:
                    await PrepareAsync(message.Serial);
                    break;
                case "install" when message.Serial is not null:
                    await InstallAsync(message.Serial);
                    break;
                case "start" when message.Serial is not null:
                    await StartAsync(message);
                    break;
                case "stop" when message.Serial is not null:
                    await StopAsync(message);
                    break;
            }
        }
        catch (Exception ex)
        {
            Log($"Error: {ex.Message}");
        }
    }

    private async Task RefreshDevicesAsync()
    {
        if (_adb is null)
        {
            Post(new { type = "devices", devices = Array.Empty<object>() });
            return;
        }
        var devices = await _adb.ListDevicesAsync();
        Post(new
        {
            type = "devices",
            devices = devices.Select(d => new
            {
                serial = d.Serial,
                label = d.Display,
                ready = d.IsReady,
                isEmulator = d.IsEmulator,
            }),
        });
    }

    private async Task SendStatusAsync(string serial)
    {
        if (_adb is null) return;
        var status = await _adb.GetStatusAsync(serial);
        Post(new
        {
            type = "status",
            serial,
            appInstalled = status.AppInstalled,
            mockAllowed = status.MockLocationAllowed,
        });
    }

    /// <summary>Grants everything the phone app needs, so Developer options never has to be opened.</summary>
    private async Task PrepareAsync(string serial)
    {
        if (_adb is null) return;
        var steps = new List<List<string>>
        {
            SpoofCommands.AllowMockLocation(),
            SpoofCommands.GrantPermission("android.permission.ACCESS_FINE_LOCATION"),
            SpoofCommands.GrantPermission("android.permission.ACCESS_COARSE_LOCATION"),
            SpoofCommands.GrantPermission("android.permission.POST_NOTIFICATIONS"),
        };
        foreach (var step in steps)
        {
            var result = await _adb.RunAsync(step, serial);
            // POST_NOTIFICATIONS does not exist below Android 13, so a failure there is expected.
            Log(result.Success ? $"{result.CommandLine} - ok" : $"{result.CommandLine} - {result.Output}");
        }
        await SendStatusAsync(serial);
    }

    private async Task InstallAsync(string serial)
    {
        if (_adb is null) return;
        var dialog = new OpenFileDialog
        {
            Title = "Choose the Location Spoofer APK",
            Filter = "Android package (*.apk)|*.apk",
        };
        if (dialog.ShowDialog(this) != true) return;

        Log($"Installing {Path.GetFileName(dialog.FileName)}...");
        var result = await _adb.RunAsync(SpoofCommands.Install(dialog.FileName), serial);
        Log(result.Success ? "Installed." : result.Output);
        await SendStatusAsync(serial);
    }

    private async Task StartAsync(Message message)
    {
        if (_adb is null || message.Serial is null) return;
        var waypoints = (message.Points ?? []).Select(p => new LatLng(p.Lat, p.Lng)).ToList();
        if (waypoints.Count == 0)
        {
            Log("Pick a point on the map first.");
            return;
        }

        _player?.Dispose();
        _player = null;

        if (message.IsEmulator)
        {
            var simulator = new RouteSimulator(waypoints, message.Loop);
            var speed = waypoints.Count > 1 ? (message.SpeedKmh ?? 5.0) / 3.6 : 0.0;
            _player = new EmulatorRoutePlayer(
                _adb,
                message.Serial,
                simulator,
                speed,
                sample => Dispatcher.Invoke(() => Post(new
                {
                    type = "position",
                    lat = sample.Position.Latitude,
                    lng = sample.Position.Longitude,
                    finished = sample.Finished,
                })),
                error => Dispatcher.Invoke(() => Log($"Emulator: {error}")));
            Log($"Driving emulator {message.Serial} from this PC.");
            Post(new { type = "running", running = true });
            return;
        }

        var result = await _adb.RunAsync(
            SpoofCommands.Start(waypoints, message.SpeedKmh, message.Loop, message.Accuracy),
            message.Serial);
        Log(result.Success ? "Sent to device." : result.Output);
        Post(new { type = "running", running = result.Success });
        if (result.Success)
        {
            Post(new
            {
                type = "position",
                lat = waypoints[0].Latitude,
                lng = waypoints[0].Longitude,
                finished = false,
            });
        }
    }

    private async Task StopAsync(Message message)
    {
        _player?.Dispose();
        _player = null;
        if (_adb is not null && message.Serial is not null && !message.IsEmulator)
        {
            var result = await _adb.RunAsync(SpoofCommands.Stop(), message.Serial);
            Log(result.Success ? "Stopped." : result.Output);
        }
        else
        {
            Log("Stopped driving the emulator. Its last position stays until you change it.");
        }
        Post(new { type = "running", running = false });
    }

    private void Log(string line) => Post(new { type = "log", line });

    private void Post(object payload) =>
        WebView.CoreWebView2?.PostWebMessageAsJson(JsonSerializer.Serialize(payload, Json));

    private sealed record Message(
        string Type,
        string? Serial,
        bool IsEmulator,
        MessagePoint[]? Points,
        double? SpeedKmh,
        bool Loop,
        double? Accuracy);

    private sealed record MessagePoint(double Lat, double Lng);
}
