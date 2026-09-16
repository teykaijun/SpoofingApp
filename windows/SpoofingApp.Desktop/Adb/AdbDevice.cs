namespace SpoofingApp.Desktop.Adb;

public sealed record AdbDevice(string Serial, string State, string Model, bool IsEmulator)
{
    public bool IsReady => State == "device";

    public string Display => IsEmulator ? $"{Model} — {Serial} (emulator)" : $"{Model} — {Serial}";

    /// <summary>Parses the output of <c>adb devices -l</c>.</summary>
    public static IReadOnlyList<AdbDevice> Parse(string output)
    {
        var devices = new List<AdbDevice>();
        foreach (var raw in output.Split('\n'))
        {
            var line = raw.Trim();
            if (line.Length == 0 ||
                line.StartsWith("List of devices", StringComparison.Ordinal) ||
                line.StartsWith('*') ||
                line.StartsWith("adb server", StringComparison.Ordinal))
            {
                continue;
            }

            var parts = line.Split((char[]?)null, StringSplitOptions.RemoveEmptyEntries);
            if (parts.Length < 2) continue;

            var serial = parts[0];
            var model = Field(parts, "model:") ?? Field(parts, "device:") ?? serial;
            devices.Add(new AdbDevice(
                serial,
                parts[1],
                model.Replace('_', ' '),
                serial.StartsWith("emulator-", StringComparison.Ordinal)));
        }
        return devices;
    }

    private static string? Field(string[] parts, string prefix) =>
        parts.FirstOrDefault(p => p.StartsWith(prefix, StringComparison.Ordinal))?[prefix.Length..];
}

public sealed record AdbResult(int ExitCode, string StandardOutput, string StandardError, string CommandLine)
{
    public bool Success => ExitCode == 0;

    public string Output => string.IsNullOrWhiteSpace(StandardOutput) ? StandardError.Trim() : StandardOutput.Trim();
}

/// <summary>What the phone app needs before it can spoof.</summary>
public sealed record DeviceStatus(bool AppInstalled, bool MockLocationAllowed)
{
    public bool IsReady => AppInstalled && MockLocationAllowed;
}
