"""Builds the offline city database bundled with the app (app/src/main/assets/cities.tsv).

Sources (both derived from GeoNames, CC-BY 4.0 — attribution shown in the app's About screen):
  * geonamescache  — cities with population >= ~15 000, coordinates, population and IANA time zone
  * reverse_geocoder — first-level administrative division names (state / province)

Usage:  pip install geonamescache reverse_geocoder && python tools/build_cities.py
"""
import csv, os, re, sys, glob, importlib.util

import geonamescache

def find_rg_csv():
    spec = importlib.util.find_spec("reverse_geocoder")
    if spec and spec.origin:
        p = os.path.join(os.path.dirname(spec.origin), "rg_cities1000.csv")
        if os.path.exists(p): return p
    hits = glob.glob("**/rg_cities1000.csv", recursive=True)
    return hits[0] if hits else None

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT = os.path.join(ROOT, "app", "src", "main", "assets")
rg_path = sys.argv[1] if len(sys.argv) > 1 else find_rg_csv()

admin = {}
by_name = {}
if rg_path:
    with open(rg_path, encoding="utf-8") as f:
        for row in csv.DictReader(f):
            lat, lon = float(row["lat"]), float(row["lon"])
            admin[(row["cc"], round(lat, 2), round(lon, 2))] = row["admin1"]
            by_name.setdefault((row["cc"], row["name"].lower()), []).append((lat, lon, row["admin1"]))

def admin1_for(c):
    a = admin.get((c["countrycode"], round(c["latitude"], 2), round(c["longitude"], 2)))
    if a: return a
    best, bd = "", 0.5 ** 2  # within ~50 km
    for n in [c["name"]] + [x for x in c.get("alternatenames", []) if x.isascii()][:30]:
        for lat, lon, a1 in by_name.get((c["countrycode"], n.lower()), []):
            d = (lat - c["latitude"]) ** 2 + (lon - c["longitude"]) ** 2
            if d < bd and a1: best, bd = a1, d
    return best

gc = geonamescache.GeonamesCache()
countries = gc.get_countries()
cities = gc.get_cities()
ascii_re = re.compile(r"^[A-Za-z][A-Za-z .\-]{1,40}$")

def clean(s):
    return s.replace("\t", " ").replace("\n", " ").strip()

rows = []
for c in cities.values():
    if not c.get("timezone"): continue
    a1 = admin1_for(c)
    alts = []
    if c["population"] >= 300_000:
        for n in c.get("alternatenames", []):
            if ascii_re.match(n) and n.lower() != c["name"].lower() and n not in alts:
                alts.append(n)
            if len(alts) >= 4: break
    rows.append([c["geonameid"], clean(c["name"]), clean(a1), c["countrycode"],
                 f'{c["latitude"]:.4f}', f'{c["longitude"]:.4f}', c["population"], c["timezone"], "|".join(alts)])

rows.sort(key=lambda r: -int(r[6]))
os.makedirs(OUT, exist_ok=True)
with open(os.path.join(OUT, "cities.tsv"), "w", encoding="utf-8", newline="") as f:
    f.write("# id\tname\tadmin1\tcountry\tlat\tlon\tpopulation\ttimezone\talternates  (GeoNames, CC-BY 4.0)\n")
    for r in rows: f.write("\t".join(str(x) for x in r) + "\n")
with open(os.path.join(OUT, "countries.tsv"), "w", encoding="utf-8", newline="") as f:
    for cc, c in sorted(countries.items()):
        f.write(f"{cc}\t{clean(c['name'])}\n")
print(f"cities={len(rows)} with_admin1={sum(1 for r in rows if r[2])} countries={len(countries)}")
