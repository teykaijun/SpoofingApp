using SpoofingApp.Desktop.Spoofing;
using Xunit;

namespace SpoofingApp.Desktop.Tests;

public class GeoMathTests
{
    [Fact]
    public void OneDegreeOfLongitudeAtTheEquator()
    {
        Assert.Equal(111_195.0, GeoMath.DistanceMeters(new LatLng(0, 0), new LatLng(0, 1)), 5.0);
    }

    [Fact]
    public void BearingsPointToCardinalDirections()
    {
        var origin = new LatLng(0, 0);
        Assert.Equal(0.0, GeoMath.InitialBearingDegrees(origin, new LatLng(1, 0)), 6);
        Assert.Equal(90.0, GeoMath.InitialBearingDegrees(origin, new LatLng(0, 1)), 6);
        Assert.Equal(180.0, GeoMath.InitialBearingDegrees(origin, new LatLng(-1, 0)), 6);
        Assert.Equal(270.0, GeoMath.InitialBearingDegrees(origin, new LatLng(0, -1)), 6);
    }

    [Fact]
    public void InterpolatesTheMidpointOnTheEquator()
    {
        var midpoint = GeoMath.Interpolate(new LatLng(0, 0), new LatLng(0, 10), 0.5);
        Assert.Equal(0.0, midpoint.Latitude, 9);
        Assert.Equal(5.0, midpoint.Longitude, 9);
    }

    [Fact]
    public void NormalizesLongitudeAndClampsLatitude()
    {
        Assert.Equal(-170.0, LatLng.Normalized(10, 190).Longitude, 9);
        Assert.Equal(90.0, LatLng.Normalized(95, 0).Latitude, 9);
    }
}

public class RouteSimulatorTests
{
    private static readonly LatLng Start = new(0, 0);
    private static readonly LatLng End = new(0, 0.01);

    [Fact]
    public void SingleWaypointHoldsItsPosition()
    {
        var simulator = new RouteSimulator([Start], loop: false);
        var sample = simulator.SampleAtDistance(500);

        Assert.True(simulator.IsStationary);
        Assert.Equal(Start, sample.Position);
        Assert.False(sample.Finished);
    }

    [Fact]
    public void MovesAlongTheRouteWithABearing()
    {
        var simulator = new RouteSimulator([Start, End], loop: false);
        var sample = simulator.SampleAtDistance(simulator.TotalDistanceMeters / 2);

        Assert.Equal(0.005, sample.Position.Longitude, 9);
        Assert.Equal(90.0, sample.BearingDegrees, 6);
        Assert.False(sample.Finished);
    }

    [Fact]
    public void StopsAtTheLastWaypointWhenNotLooping()
    {
        var simulator = new RouteSimulator([Start, End], loop: false);
        var sample = simulator.SampleAtDistance(simulator.TotalDistanceMeters + 100);

        Assert.Equal(End, sample.Position);
        Assert.True(sample.Finished);
    }

    [Fact]
    public void LoopClosesTheRouteAndWrapsAround()
    {
        var simulator = new RouteSimulator([Start, End], loop: true);
        Assert.Equal(2 * GeoMath.DistanceMeters(Start, End), simulator.TotalDistanceMeters, 6);

        var back = simulator.SampleAtDistance(simulator.TotalDistanceMeters * 0.75);
        Assert.Equal(0.005, back.Position.Longitude, 9);
        Assert.Equal(270.0, back.BearingDegrees, 6);

        var secondLap = simulator.SampleAtDistance(simulator.TotalDistanceMeters * 1.25);
        Assert.Equal(0.005, secondLap.Position.Longitude, 9);
        Assert.Equal(90.0, secondLap.BearingDegrees, 6);
    }

    [Fact]
    public void IgnoresDuplicateWaypoints()
    {
        var simulator = new RouteSimulator([Start, Start, End], loop: false);
        Assert.Equal(GeoMath.DistanceMeters(Start, End), simulator.TotalDistanceMeters, 6);
    }
}
