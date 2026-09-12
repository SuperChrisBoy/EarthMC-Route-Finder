# EarthMC Route Finder

A client-side Fabric companion for **EarthMC Map Addon**, with the ice road and Route Finder features extracted from the existing development project. Install both mods, plus Fabric API, Xaero's Minimap and Xaero's World Map, for your Minecraft version.

| Branch | Minecraft | Java |
| --- | --- | --- |
| `26.2` | 26.2 | 25 |
| `26.1.x` | 26.1.x (built against 26.1.2) | 25 |
| `1.21.11` | 1.21.11 | 21 |

Use one Route Finder JAR matching your game version. This project does not replace the original mod, bundle it, edit its config, or deploy automatically into your Minecraft installation.

## Features

- Ice highways and station markers on the world map and minimap, including station accessibility reports and live dataset updates.
- The existing ice road planner: network drafts, branches, station editing, snapping, undo/redo, autosave, JSON/GeoJSON export and map image export.
- Route Finder with town/nation destinations, access checks, Standard and optional Advanced mode, walking distances and ice road routes.
- Selected route display on the world map and minimap, and route waypoints.
- Independent settings, station reports and spawn reports. Teleport commands default to copying to the clipboard; settings also offer preparing chat or executing eligible commands.

## Use

Open Xaero's World Map on EarthMC. The left-side button column provides **Ice roads**, **Road planner**, and **Route Finder** below Counter. Route Finder opens routes to the map center, or restores your existing results. The planner fills the left edge and hides the map buttons while editing. Use **Exit editor** to return, or **Route Finder** in the planner to switch to route results. Open **Settings → Route settings** for route and teleport options. Double-click a map location to open Route Finder.

- `/routefinder` opens settings.
- `/routefinder roads` toggles ice road overlays.
- `/routefinder planner` toggles the planner; open the world map to edit.
- `/routefinder target <x> <z>` selects a route destination; open the world map to view results.

Route Finder settings → **Accessibility saves** lists named town/nation spawn accessibility snapshots. Save current reports under a new name (or confirm a warning to overwrite an existing save), load a snapshot immediately, or open the save folder. Loading replaces only physical spawn reports; it does not change teleport permissions or other settings. Saves are portable JSON files in `EarthMC Accessibility Saves/` directly inside the game directory. Refresh the list after adding files externally. The same screen can open the planner folder.

Viewer and planner panels automatically scale to the available window size. Viewer results scroll in complete rows above the action footer.

The planner's **Y: unset** button chooses the height used by subsequent placements; the first map placement prompts if no height has been chosen. **Add XYZ** places a point or marker from typed coordinates. Select an existing point or marker, then **Edit X/Y/Z** to edit its prefilled coordinates. Exact input preserves fractional coordinates; map clicks retain snapping.

Teleport preferences are directly editable under the base mod's **Settings → Route settings** category, with its search and scrolling.

Mod Menu also opens Route Finder settings. Ice road settings include width, marker size, station filtering and double-click targeting.

Settings: `config/earthmcroutefinder.json`. Planner files: `EarthMC Ice Road Planner/` directly inside the game directory (the draft library is in that folder). Existing nested planner files are copied there automatically; originals are retained. Existing development-mod drafts can be imported with the planner's clipboard JSON import; the original files are not migrated or modified automatically.

## Build and validation

Check out the desired branch, then run `./gradlew build` (Windows: `.\gradlew.bat build`). The installable JAR is in `build/libs/`; do not install the `-sources.jar`.

Dependencies resolve from pinned Modrinth version IDs in `gradle/mod-dependencies.json`. No manually supplied `libs/` JAR is needed. Optional `node scripts/setup-dependencies.cjs` downloads SHA-512-verified original mod and Xaero JARs to `validation/<version>/mods/`.

`./gradlew runClient -PsmokeTest` starts a separate client under `run-smoke/`, checks that the companion's mixins and the published base mod's integration methods exist, prints `ROUTE_FINDER_SMOKE_OK`, and closes. The smoke-only mod is not included in release JARs. This startup check does not connect to EarthMC or exercise real teleport commands.

The extracted unit tests cover route access/caching, ice road routing, and planner editing/export. Multiplayer interaction and visual alignment should also be checked in-game before publishing a release.

## Integration and maintenance

The companion has its own `earthmcroutefinder` mod ID, Java package, assets and settings. It reads the original mod's town snapshots and coordinate transforms and attaches rendering/input to the original map integration. The published EarthMC Map Addon 1.4.5 is the integration baseline. Future changes to its internal methods or Xaero's screen methods may require updating the companion.

Keep common route logic synchronized across branches; version-specific Fabric, Minecraft rendering and mappings changes belong on their respective branches.

Derived source is Apache-2.0; see `LICENSE` and `NOTICE`. The bundled XiLeF2211 highway dataset retains its own included license.

## Verified builds

See [validation results](docs/VALIDATION.md) for the tested versions and limits. The verified Xaero baseline is Minimap 26.5.0 and World Map 1.46.0, with their bundled XaeroLib.

On the `1.21.11` branch, `./gradlew productionSmoke -PsmokeTest` also tests the remapped release JAR in an isolated production client. Smoke test JARs are test tooling and must not be installed for normal play.

In the planner, **Draw** connects each click to the previous endpoint. Click an existing point with Draw to continue from there; **Esc** finishes the current line. **Start new line on branch** begins a separate run. Ctrl/Shift-click segments or line names to multi-select, or Ctrl+A to select all lines. The selection inspector can set height, move by XYZ offset, or delete segments together. Ctrl+Z undoes the whole edit.
