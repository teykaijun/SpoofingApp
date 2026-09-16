using SpoofingApp.Desktop.Adb;
using Xunit;

namespace SpoofingApp.Desktop.Tests;

public class AdbDeviceTests
{
    private const string DevicesOutput = """
        List of devices attached
        emulator-5554          device product:sdk_gphone64_x86_64 model:sdk_gphone64_x86_64 device:emu64xa transport_id:1
        39021FDJH00K5S         device product:raven model:Pixel_6_Pro device:raven transport_id:2
        1a2b3c4d               unauthorized usb:1-3

        """;

    [Fact]
    public void ParsesSerialsModelsAndState()
    {
        var devices = AdbDevice.Parse(DevicesOutput);

        Assert.Equal(3, devices.Count);
        Assert.True(devices[0].IsEmulator);
        Assert.Equal("emulator-5554", devices[0].Serial);
        Assert.Equal("Pixel 6 Pro", devices[1].Model);
        Assert.True(devices[1].IsReady);
        Assert.False(devices[1].IsEmulator);
        Assert.False(devices[2].IsReady);
        Assert.Equal("1a2b3c4d", devices[2].Model);
    }

    [Fact]
    public void IgnoresDaemonChatterAndBlankLines()
    {
        var devices = AdbDevice.Parse("* daemon not running; starting now at tcp:5037\n* daemon started successfully\nList of devices attached\n\n");
        Assert.Empty(devices);
    }
}
