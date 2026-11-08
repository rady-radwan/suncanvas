# Accuracy of solar calculations

## Method

`core-solar` implements the NOAA Solar Calculator equations (based on Jean Meeus, *Astronomical Algorithms*). Events are found by sampling the geometric solar elevation across the **local day of the location's IANA time zone** and refining each threshold crossing by bisection (< 0.25 s).

| Event | Definition (centre of the Sun) |
|---|---|
| Sunrise / sunset | −0.833° (34′ standard refraction + 16′ semi-diameter) |
| Civil twilight | −6° |
| Nautical twilight | −12° |
| Astronomical twilight | −18° |
| Golden hour (convention) | between +6° and −4° |
| Blue hour (convention) | between −4° and −6° |
| Solar noon / midnight | hour angle 0° / 180° |

Golden and blue hour are photography conventions, not astronomical definitions; the UI labels them accordingly.

## Reference comparison

`core-solar/src/test/resources/reference_skyfield.csv` contains 280 location/date pairs generated with [Skyfield](https://rhodesmill.org/skyfield/) and the JPL DE421 ephemeris (`tools/gen_reference.py`). They cover:

* 20 places: Dallas, New York, Cairo, London, Tokyo, Dubai, Sydney, Auckland, Quito, Reykjavík, Tromsø, Longyearbyen, McMurdo, Ushuaia, Apia, Kiritimati, Honolulu, Anchorage, Singapore, Fairbanks
* Dates: leap day 2024-02-29, year end/start, US/EU/NZ DST transitions, equinoxes, solstices

Results of the engine against that reference:

| Quantity | Mean abs. error | Max abs. error |
|---|---|---|
| Sunrise | 1 s | 13 s |
| Sunset | 1 s | 8 s |
| Solar noon | 1 s | 3 s |
| Civil dawn | 1 s | 9 s |
| Astronomical dusk | 1 s | 12 s |

The unit tests allow 2 minutes (4 minutes above 60° latitude) so that the tests express the guarantee we make to users, not the noise floor. Equinox/solstice instants are checked against USNO published times (within 15 minutes; actual ≈ 1 minute).

Additional tests: polar night/day (Tromsø, near the pole), DST days are 23 h / 25 h, events stay on the correct local date across the International Date Line (Kiritimati UTC+14, Apia), Southern Hemisphere seasons, leap years, year-end continuity, ordering of golden/blue hours, azimuth at solar noon, next sunrise after the polar night.

## Expected differences from other sources

* **Refraction.** Real refraction near the horizon varies with temperature and pressure; sunrise/sunset can differ from any prediction by 1–2 minutes. NOAA and USNO use the same −0.833° convention, so agreement with them is typically within a minute (both round to the minute).
* **Observer elevation and terrain** are not modelled (sea-level horizon).
* **High latitudes.** When the Sun skims the horizon, a tiny change in elevation shifts the crossing time a lot, so differences of several minutes between sources are normal there.
* **Time zones near borders.** Coordinates within a few km of a zone border may resolve to the neighbouring zone; the coordinate screen lets the user override the zone.
