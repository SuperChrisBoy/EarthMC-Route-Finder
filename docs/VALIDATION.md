# Validation — 2026-09-10

| Branch | Minecraft tested | Unit tests | Client startup / mixin checks |
| --- | --- | --- | --- |
| 26.2 | 26.2, Java 25 | 105 passed | Passed |
| 26.1.x | 26.1.2, Java 25 | 105 passed | Passed |
| 1.21.11 | 1.21.11, Java 21 | 105 passed | Passed in development and production mappings |

Baseline: published EarthMC Map Addon 1.4.5, Xaero's Minimap 26.5.0, Xaero's World Map 1.46.0, bundled XaeroLib 1.7.3. Exact dependency version IDs and SHA-512 hashes are recorded in `gradle/mod-dependencies.json`.

Tests cover teleport access evaluation, cache identity, route filtering, ice highway routing and planner editing/export. The compatibility smoke mod checks that the world-map and base-mod rendering mixins applied and that the base-mod bridge methods exist. It then closes the isolated client. Smoke code is not packaged in the companion JAR.

The 1.21.11 `productionSmoke` task additionally launches the remapped distribution JAR under intermediary mappings, verifying the installed-client method aliases. The production run reached `ROUTE_FINDER_SMOKE_OK`.

No EarthMC login, live teleport command, multiplayer route verification, or visual overlay alignment check was performed. Other 26.1 patch releases were not individually launched.

Local release JARs are in `releases/`. Local startup logs are retained under `validation/<version>/`. These generated artifacts are ignored by Git.
