using System.Globalization;
using SpoofingApp.Desktop.Spoofing;
using Xunit;

namespace SpoofingApp.Desktop.Tests;

public class SpoofCommandsTests
{
    [Fact]
    public void StartSendsAQuotedPointListToTheApp()
    {
        var args = SpoofCommands.Start([new LatLng(48.85837, 2.29448)], accuracyMeters: 5);

        Assert.Equal(
            new[]
            {
                "shell", "am", "start",
                "-n", "com.spoofingmobileapp/.MainActivity",
                "-a", "com.spoofingmobileapp.action.REMOTE",
                "--es", "command", "start",
                "--es", "points", "'48.85837,2.29448'",
                "--es", "loop", "false",
                "--es", "accuracy", "5",
            },
            args);
    }

    [Fact]
    public void StartSeparatesWaypointsWithSemicolonsAndCarriesSpeedAndLoop()
    {
        var args = SpoofCommands.Start([new LatLng(1.5, 2.5), new LatLng(3.5, 4.5)], speedKmh: 18, loop: true);

        Assert.Contains("'1.5,2.5;3.5,4.5'", args);
        Assert.Equal("18", args[args.IndexOf("speed") + 1]);
        Assert.Equal("true", args[args.IndexOf("loop") + 1]);
    }

    [Fact]
    public void EmulatorGeoFixPutsLongitudeFirst()
    {
        Assert.Equal(
            new[] { "emu", "geo", "fix", "2.29448", "48.85837" },
            SpoofCommands.EmulatorGeoFix(new LatLng(48.85837, 2.29448)));
    }

    [Fact]
    public void NumbersStayDottedUnderACommaDecimalCulture()
    {
        var original = CultureInfo.CurrentCulture;
        try
        {
            CultureInfo.CurrentCulture = new CultureInfo("de-DE");
            Assert.Equal(
                new[] { "emu", "geo", "fix", "2.29448", "48.85837" },
                SpoofCommands.EmulatorGeoFix(new LatLng(48.85837, 2.29448)));
        }
        finally
        {
            CultureInfo.CurrentCulture = original;
        }
    }

    [Fact]
    public void ReadsAppOpsAndPackageOutput()
    {
        Assert.True(SpoofCommands.ParseMockLocationAllowed("com.spoofingmobileapp/10123:\n  mock_location: allow\n"));
        Assert.False(SpoofCommands.ParseMockLocationAllowed("com.spoofingmobileapp/10123:\n  mock_location: deny\n"));
        Assert.True(SpoofCommands.ParseInstalled("package:com.spoofingmobileapp\n"));
        Assert.False(SpoofCommands.ParseInstalled(string.Empty));
    }

    [Fact]
    public void StartRejectsAnEmptyRoute()
    {
        Assert.Throws<ArgumentOutOfRangeException>(() => SpoofCommands.Start([]));
    }
}
