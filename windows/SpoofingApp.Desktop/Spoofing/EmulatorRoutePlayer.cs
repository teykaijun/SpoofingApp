using System.Diagnostics;
using SpoofingApp.Desktop.Adb;

namespace SpoofingApp.Desktop.Spoofing;

/// <summary>
/// Drives an Android emulator by re-sending "adb emu geo fix" as the simulated position advances.
/// Real devices do not need this: the phone app runs the route itself.
/// </summary>
public sealed class EmulatorRoutePlayer : IDisposable
{
    private readonly CancellationTokenSource _cancellation = new();

    public EmulatorRoutePlayer(
        AdbClient adb,
        string serial,
        RouteSimulator simulator,
        double speedMetersPerSecond,
        Action<RouteSample> onSample,
        Action<string> onError)
    {
        _ = RunAsync(adb, serial, simulator, speedMetersPerSecond, onSample, onError);
    }

    private async Task RunAsync(
        AdbClient adb,
        string serial,
        RouteSimulator simulator,
        double speedMetersPerSecond,
        Action<RouteSample> onSample,
        Action<string> onError)
    {
        var interval = simulator.IsStationary ? TimeSpan.FromSeconds(5) : TimeSpan.FromSeconds(1);
        using var timer = new PeriodicTimer(interval);
        var clock = Stopwatch.StartNew();
        try
        {
            while (!_cancellation.IsCancellationRequested)
            {
                var sample = simulator.SampleAtDistance(clock.Elapsed.TotalSeconds * speedMetersPerSecond);
                var result = await adb.RunAsync(
                    SpoofCommands.EmulatorGeoFix(sample.Position), serial, _cancellation.Token);
                if (!result.Success)
                {
                    onError(result.Output);
                    return;
                }
                onSample(sample);
                await timer.WaitForNextTickAsync(_cancellation.Token);
            }
        }
        catch (OperationCanceledException)
        {
            // Stopped by the user.
        }
    }

    public void Dispose()
    {
        _cancellation.Cancel();
        _cancellation.Dispose();
    }
}
