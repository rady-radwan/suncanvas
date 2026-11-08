# Regenerates core-solar/src/test/resources/reference_skyfield.csv
# Usage: pip install skyfield skyfield-data && python tools/gen_reference.py > core-solar/src/test/resources/reference_skyfield.csv
# Generates reference sunrise/sunset/twilight/transit data with Skyfield + JPL DE421.
import csv, sys
from datetime import date, datetime, timedelta
from zoneinfo import ZoneInfo
from skyfield.api import load_file, wgs84, Loader
from skyfield import almanac
import skyfield_data, os

ts = Loader(os.path.join(os.path.dirname(skyfield_data.__file__), 'data')).timescale(builtin=True)
eph = load_file(os.path.join(os.path.dirname(skyfield_data.__file__), 'data', 'de421.bsp'))
sun, earth = eph['sun'], eph['earth']

LOC = [
 ("dallas", 32.7767, -96.7970, "America/Chicago"),
 ("newyork", 40.7128, -74.0060, "America/New_York"),
 ("cairo", 30.0444, 31.2357, "Africa/Cairo"),
 ("london", 51.5074, -0.1278, "Europe/London"),
 ("tokyo", 35.6762, 139.6503, "Asia/Tokyo"),
 ("dubai", 25.2048, 55.2708, "Asia/Dubai"),
 ("sydney", -33.8688, 151.2093, "Australia/Sydney"),
 ("auckland", -36.8485, 174.7633, "Pacific/Auckland"),
 ("quito", -0.1807, -78.4678, "America/Guayaquil"),
 ("reykjavik", 64.1466, -21.9426, "Atlantic/Reykjavik"),
 ("tromso", 69.6492, 18.9553, "Europe/Oslo"),
 ("longyearbyen", 78.2232, 15.6267, "Arctic/Longyearbyen"),
 ("mcmurdo", -77.8419, 166.6863, "Antarctica/McMurdo"),
 ("ushuaia", -54.8019, -68.3030, "America/Argentina/Ushuaia"),
 ("apia", -13.8333, -171.7667, "Pacific/Apia"),
 ("kiritimati", 1.8721, -157.4278, "Pacific/Kiritimati"),
 ("honolulu", 21.3069, -157.8583, "Pacific/Honolulu"),
 ("anchorage", 61.2181, -149.9003, "America/Anchorage"),
 ("singapore", 1.3521, 103.8198, "Asia/Singapore"),
 ("fairbanks", 64.8378, -147.7164, "America/Anchorage"),
]
DATES = ["2024-02-29","2025-12-31","2026-01-01","2026-03-08","2026-03-20","2026-03-29","2026-04-05",
         "2026-06-21","2026-09-23","2026-09-27","2026-10-25","2026-11-01","2026-11-08","2026-12-21"]

def fmt(t, tz):
    if t is None: return "none"
    dt = t.utc_datetime().astimezone(tz)
    return dt.strftime("%Y-%m-%dT%H:%M:%S")

def first(ts_list, ys, want_rise):
    for t, y in zip(ts_list, ys):
        if bool(y) == want_rise: return t
    return None

def risings_settings(obs, t0, t1, h):
    tr, yr = almanac.find_risings(obs, sun, t0, t1, horizon_degrees=h)
    tsn, ys = almanac.find_settings(obs, sun, t0, t1, horizon_degrees=h)
    r = [t for t, ok in zip(tr, yr) if ok]
    s = [t for t, ok in zip(tsn, ys) if ok]
    return (r[0] if r else None), (s[-1] if s else None)

w = csv.writer(sys.stdout)
w.writerow(["id","lat","lon","zone","date","sunrise","sunset","noon","civilDawn","civilDusk","nauticalDawn","nauticalDusk","astroDawn","astroDusk"])
for (lid, lat, lon, zone) in LOC:
    tz = ZoneInfo(zone)
    topos = wgs84.latlon(lat, lon)
    obs = earth + topos
    for ds in DATES:
        d = date.fromisoformat(ds)
        s = datetime(d.year, d.month, d.day, tzinfo=tz)
        e = s + timedelta(days=1)
        # handle DST: next local midnight
        e = datetime(e.year, e.month, e.day, tzinfo=tz)
        t0, t1 = ts.from_datetime(s), ts.from_datetime(e)
        rise, sset = risings_settings(obs, t0, t1, -0.8333)
        cd, cdu = risings_settings(obs, t0, t1, -6)
        nd, ndu = risings_settings(obs, t0, t1, -12)
        ad, adu = risings_settings(obs, t0, t1, -18)
        f = almanac.meridian_transits(eph, sun, topos)
        tt, yy = almanac.find_discrete(t0, t1, f)
        noon = None
        for t, y in zip(tt, yy):
            if y == 1: noon = t
        w.writerow([lid, lat, lon, zone, ds] + [fmt(x, tz) for x in (rise, sset, noon, cd, cdu, nd, ndu, ad, adu)])
