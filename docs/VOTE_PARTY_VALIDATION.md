# Vote-party HUD validation

Implemented for 26.2, 26.1.x (built against 26.1.2), and 1.21.11.

- All three `build --offline` runs passed: 139 tests passed, 2 skipped per version.
- Four polling tests cover progress/reset calculations, invalid API payloads, disabled/in-flight polling, stale snapshots, request failure, and recovery.
- Three layout tests cover responsive scaling, centering and screen bounds across small, portrait, ultrawide and high-resolution viewports, long text, all supported size settings, and clearance below vanilla boss-bar stacks.
- Smoke checks also verify both vanilla overlay accessors load, the Vote Party settings page opens, the slider reflects the saved size, Reset size persists 60%, and Done returns to the parent settings screen.
- 26.2 and 26.1.2 `runClient -PsmokeTest --offline` passed (`ROUTE_FINDER_SMOKE_OK`).
- 1.21.11 `productionSmoke -PsmokeTest --offline` passed with the remapped release JAR (`ROUTE_FINDER_SMOKE_OK`).
- Isolated menu/client startup was exercised. Live server play, single-player play, and visual alignment were not manually checked.

Installable JARs: `releases/session-2026-09-28-vote-party-hotkey/`. These retain version 0.1.5 and include existing workspace changes. Install only the JAR for your Minecraft version, replacing the previous Route Finder JAR.

The HUD is on by default and appears at top center in-game and in menus. It scales with the current GUI viewport, stays below visible vanilla boss bars, and hides while the Tab player list is open. It displays EarthMC vote-party percentage, votes remaining, and a progress bar. Use Route Finder settings or `/routefinder voteparty` to toggle it; the preference is saved. Polling runs independently of server/world state every 30 seconds while enabled. Failed requests preserve last-known progress with an explicit stale label; no data displays a loading/unavailable message. F1 hides the gameplay HUD.

The default HUD now uses 60% scale with a content-fitted width, tighter padding, and a 34-unit height. Size is adjustable from 30% to 150% in five-percent steps, saved as `votePartyHudScale`. Existing configs without this field adopt 60%. Use Route settings > Vote Party HUD size, or Mod Menu > Route Finder > Vote Party... for live preview and Reset size.

V now toggles the HUD during gameplay and saves its enabled state. The binding is reassignable in Controls > Key Binds > Miscellaneous > Toggle Vote Party HUD. Open screens suppress the hotkey to protect chat, search, and key rebinding. All version smoke checks verify registration and the V default.
