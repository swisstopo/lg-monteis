# Example 3D Tiles dataset (temporary)

Demo data backing the "Measurements 3D View" (see `webapp/src/app/features/overview/measurements-visualization`).
It exists so the Giro3D view loads a model **from our own backend, through our own authentication**,
instead of from a third-party host. It is a placeholder for the IFC models the backend will
eventually manage itself (upload → S3 → conversion), and should be deleted once those exist.

## Source

Downloaded from Oslandia's public 3D Tiles demo server:
<https://3d.oslandia.com/3dtiles/19_rue_Marc_Antoine_Petit_ifc/tileset.json>

An IFC building model converted to 3D Tiles with [py3dtiles](https://gitlab.com/py3dtiles/py3dtiles).
The `extras.properties.spatialStructure.class` fields it carries (`IfcSpace`, `IfcBuildingStorey`, …)
are what `Giro3d` uses to hide `IfcSpace` volumes.

The upstream host states no license. Confirm redistribution is acceptable before relying on this
long-term; any other small 3D Tiles sample would work just as well.

## Re-downloading

Run from the repository root. The tile URIs inside `tileset.json` are relative (`ifc/13.b3dm`), so
nothing has to be rewritten after downloading.

```bash
BASE=https://3d.oslandia.com/3dtiles/19_rue_Marc_Antoine_Petit_ifc
DEST=core/src/main/resources/static/api/tilesets/example
mkdir -p "$DEST/ifc"
curl -sS "$BASE/tileset.json" -o "$DEST/tileset.json"
grep -o '"uri":"[^"]*"' "$DEST/tileset.json" | sed 's/"uri":"//;s/"$//' | sort -u \
  | xargs -P 8 -I{} curl -sS --create-dirs "$BASE/{}" -o "$DEST/{}"
```

Expect 241 files (`tileset.json` + 240 `ifc/*.b3dm`), ~7.7 MB.

## How it is served

There is no controller and no resource-handler registration: Spring Boot maps `classpath:/static/`
onto `/**` by default, so the `api/` directory in this path *is* the URL prefix. These files are
reachable at:

```
/api/tilesets/example/tileset.json
/api/tilesets/example/ifc/<id>.b3dm
```

`/api` is deliberate — it is the only prefix the webapp dev proxy (`webapp/proxy.conf.json`) and the
production CloudFront distribution already route to the backend.

`SecurityConfig` ends in `anyRequest().authenticated()`, which covers static resources too, so these
files require a valid bearer token like every other endpoint. Giro3D fetches tiles outside Angular's
`HttpClient`, so the token is attached separately in `webapp/src/app/core/auth/tileset-auth.ts`.
