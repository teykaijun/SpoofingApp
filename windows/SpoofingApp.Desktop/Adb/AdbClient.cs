using System.Diagnostics;
using System.IO;
using SpoofingApp.Desktop.Spoofing;

namespace SpoofingApp.Desktop.Adb;

/// <summary>Runs adb and parses what comes back.</summary>
public sealed class AdbClient(string adbPath)
{
    private static readonly TimeSpan CommandTimeout = TimeSpan.FromSeconds(25);

    public string AdbPath { get; } = adbPath;

    /// <summary>Finds adb.exe in the usual places. Null when the Android SDK platform-tools are missing.</summary>
    public static string? Locate()
    {
        foreach (var variable in new[] { "ANDROID_HOME", "ANDROID_SDK_ROOT" })
        {
            var root = Environment.GetEnvironmentVariable(variable);
            if (!string.IsNullOrWhiteSpace(root))
            {
                var candidate = Path.Combine(root, "platform-tools", "adb.exe");
                if (File.Exists(candidate)) return candidate;
            }
        }

        var sdk = Path.Combine(
            Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData),
            "Android", "Sdk", "platform-tools", "adb.exe");
        if (File.Exists(sdk)) return sdk;

        foreach (var directory in (Environment.GetEnvironmentVariable("PATH") ?? string.Empty).Split(Path.PathSeparator))
        {
            if (string.IsNullOrWhiteSpace(directory)) continue;
            var candidate = Path.Combine(directory.Trim(), "adb.exe");
            if (File.Exists(candidate)) return candidate;
        }
        return null;
    }

    public async Task<AdbResult> RunAsync(
        IEnumerable<string> arguments,
        string? serial = null,
        CancellationToken cancellationToken = default)
    {
        var info = new ProcessStartInfo(AdbPath)
        {
            RedirectStandardOutput = true,
            RedirectStandardError = true,
            UseShellExecute = false,
            CreateNoWindow = true,
        };
        if (!string.IsNullOrEmpty(serial))
        {
            info.ArgumentList.Add("-s");
            info.ArgumentList.Add(serial);
        }
        foreach (var argument in arguments) info.ArgumentList.Add(argument);
        var commandLine = "adb " + string.Join(' ', info.ArgumentList);

        using var timeout = CancellationTokenSource.CreateLinkedTokenSource(cancellationToken);
        timeout.CancelAfter(CommandTimeout);

        using var process = Process.Start(info) ?? throw new InvalidOperationException($"Could not start {AdbPath}");
        var stdout = process.StandardOutput.ReadToEndAsync(timeout.Token);
        var stderr = process.StandardError.ReadToEndAsync(timeout.Token);
        try
        {
            await process.WaitForExitAsync(timeout.Token);
        }
        catch (OperationCanceledException)
        {
            try
            {
                process.Kill(entireProcessTree: true);
            }
            catch (InvalidOperationException)
            {
                // Process already exited.
            }
            return new AdbResult(-1, string.Empty, "adb did not respond in time", commandLine);
        }

        return new AdbResult(process.ExitCode, await stdout, await stderr, commandLine);
    }

    public async Task<IReadOnlyList<AdbDevice>> ListDevicesAsync(CancellationToken cancellationToken = default)
    {
        var result = await RunAsync(["devices", "-l"], cancellationToken: cancellationToken);
        return result.Success ? AdbDevice.Parse(result.StandardOutput) : [];
    }

    /// <summary>Checks whether the phone app is installed and already allowed to mock locations.</summary>
    public async Task<DeviceStatus> GetStatusAsync(string serial, CancellationToken cancellationToken = default)
    {
        var installed = await RunAsync(SpoofCommands.QueryInstalled(), serial, cancellationToken);
        var appOps = await RunAsync(SpoofCommands.QueryMockLocation(), serial, cancellationToken);
        return new DeviceStatus(
            SpoofCommands.ParseInstalled(installed.StandardOutput),
            SpoofCommands.ParseMockLocationAllowed(appOps.StandardOutput));
    }
}
