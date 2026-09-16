using System.Globalization;

namespace SpoofingApp.Desktop.Spoofing;

/// <summary>A WGS84 coordinate in decimal degrees.</summary>
public readonly record struct LatLng(double Latitude, double Longitude)
{
    /// <summary>Formats as "lat,lng" with a dot separator, the format the phone app parses.</summary>
    public string ToPointString() =>
        Latitude.ToString("0.######", CultureInfo.InvariantCulture) + "," +
        Longitude.ToString("0.######", CultureInfo.InvariantCulture);

    public static LatLng Normalized(double latitude, double longitude)
    {
        var lat = Math.Clamp(latitude, -90.0, 90.0);
        var lon = (longitude + 180.0) % 360.0;
        if (lon < 0) lon += 360.0;
        return new LatLng(lat, lon - 180.0);
    }
}

/// <summary>Spherical-earth helpers, mirroring the Android app's GeoMath.</summary>
public static class GeoMath
{
    public const double EarthRadiusMeters = 6_371_008.8;

    public static double DistanceMeters(LatLng from, LatLng to) => EarthRadiusMeters * AngularDistance(from, to);

    /// <summary>Initial great-circle bearing in degrees clockwise from north (0..360).</summary>
    public static double InitialBearingDegrees(LatLng from, LatLng to)
    {
        var lat1 = Radians(from.Latitude);
        var lat2 = Radians(to.Latitude);
        var dLon = Radians(to.Longitude - from.Longitude);
        var y = Math.Sin(dLon) * Math.Cos(lat2);
        var x = Math.Cos(lat1) * Math.Sin(lat2) - Math.Sin(lat1) * Math.Cos(lat2) * Math.Cos(dLon);
        return (Degrees(Math.Atan2(y, x)) + 360.0) % 360.0;
    }

    /// <summary>Point at <paramref name="fraction"/> (0..1) along the great circle from one point to another.</summary>
    public static LatLng Interpolate(LatLng from, LatLng to, double fraction)
    {
        if (fraction <= 0.0) return from;
        if (fraction >= 1.0) return to;
        var delta = AngularDistance(from, to);
        if (delta < 1e-12) return from;
        if (Math.PI - delta < 1e-9) return fraction < 0.5 ? from : to;

        var lat1 = Radians(from.Latitude);
        var lon1 = Radians(from.Longitude);
        var lat2 = Radians(to.Latitude);
        var lon2 = Radians(to.Longitude);
        var a = Math.Sin((1 - fraction) * delta) / Math.Sin(delta);
        var b = Math.Sin(fraction * delta) / Math.Sin(delta);
        var x = a * Math.Cos(lat1) * Math.Cos(lon1) + b * Math.Cos(lat2) * Math.Cos(lon2);
        var y = a * Math.Cos(lat1) * Math.Sin(lon1) + b * Math.Cos(lat2) * Math.Sin(lon2);
        var z = a * Math.Sin(lat1) + b * Math.Sin(lat2);
        return LatLng.Normalized(Degrees(Math.Atan2(z, Math.Sqrt(x * x + y * y))), Degrees(Math.Atan2(y, x)));
    }

    private static double AngularDistance(LatLng from, LatLng to)
    {
        var lat1 = Radians(from.Latitude);
        var lat2 = Radians(to.Latitude);
        var dLat = lat2 - lat1;
        var dLon = Radians(to.Longitude - from.Longitude);
        var h = Math.Pow(Math.Sin(dLat / 2), 2) + Math.Cos(lat1) * Math.Cos(lat2) * Math.Pow(Math.Sin(dLon / 2), 2);
        return 2 * Math.Asin(Math.Sqrt(Math.Clamp(h, 0.0, 1.0)));
    }

    private static double Radians(double degrees) => degrees * Math.PI / 180.0;

    private static double Degrees(double radians) => radians * 180.0 / Math.PI;
}
