namespace SpoofingApp.Desktop.Spoofing;

public readonly record struct RouteSample(LatLng Position, double BearingDegrees, double DistanceMeters, bool Finished);

/// <summary>
/// Walks a path of waypoints. Used only for emulators, where this PC steps the position itself
/// with "adb emu geo fix"; real devices run the equivalent simulation inside the phone app.
/// </summary>
public sealed class RouteSimulator
{
    private const double MinLegMeters = 0.5;

    private sealed record Leg(LatLng Start, LatLng End, double Length, double StartOffset);

    private readonly LatLng _origin;
    private readonly List<Leg> _legs = [];
    private readonly bool _loop;

    public double TotalDistanceMeters { get; }

    public bool IsStationary => _legs.Count == 0;

    public RouteSimulator(IReadOnlyList<LatLng> waypoints, bool loop)
    {
        ArgumentOutOfRangeException.ThrowIfZero(waypoints.Count);
        _origin = waypoints[0];
        _loop = loop;

        var stops = new List<LatLng>(waypoints);
        if (loop && waypoints.Count > 1) stops.Add(waypoints[0]);
        var offset = 0.0;
        for (var i = 0; i < stops.Count - 1; i++)
        {
            var length = GeoMath.DistanceMeters(stops[i], stops[i + 1]);
            if (length < MinLegMeters) continue;
            _legs.Add(new Leg(stops[i], stops[i + 1], length, offset));
            offset += length;
        }
        TotalDistanceMeters = offset;
    }

    public RouteSample SampleAtDistance(double traveledMeters)
    {
        if (IsStationary) return new RouteSample(_origin, 0.0, 0.0, false);

        var traveled = Math.Max(0.0, traveledMeters);
        if (!_loop && traveled >= TotalDistanceMeters)
        {
            var last = _legs[^1];
            var arrival = (GeoMath.InitialBearingDegrees(last.End, last.Start) + 180.0) % 360.0;
            return new RouteSample(last.End, arrival, TotalDistanceMeters, true);
        }

        var distance = _loop ? traveled % TotalDistanceMeters : traveled;
        var leg = _legs.Last(l => l.StartOffset <= distance);
        var fraction = Math.Clamp((distance - leg.StartOffset) / leg.Length, 0.0, 1.0);
        var position = GeoMath.Interpolate(leg.Start, leg.End, fraction);
        var bearing = GeoMath.DistanceMeters(position, leg.End) > MinLegMeters
            ? GeoMath.InitialBearingDegrees(position, leg.End)
            : GeoMath.InitialBearingDegrees(leg.Start, leg.End);
        return new RouteSample(position, bearing, distance, false);
    }
}
