# Hot Air Balloons — PRD

## Context
User wants to move into content mods (custom blocks + entities) and picked idea #2: a craftable, rideable hot air balloon.
Competition (Modrinth, checked 2026-10-28): **Breezy** 131K downloads, Forge/NeoForge only, MC 1.19–1.20.2; three others under 2K. **Nothing on Fabric 26.x.** Overlap to keep in mind: vanilla happy ghast already flies 4 riders, so the balloon must win on being craftable early (no Nether trip), customizable, cargo-carrying and good-looking.
No existing mod in `mods/` registers a custom entity yet; this is the first. Closest template: `mods/CountryRoads` (Fabric 26.3 single target, registers block + block entity + item in `path/Stake.java`, Mod Menu config screen).

## Decisions (user, 2026-10-28)
- Flight: **hybrid**. Space = burner (heat up), Sprint key = vent (heat down), A/D yaw, W/S slow thrust; Shift stays vanilla dismount. Height-band wind drifts the balloon gently, shown by wisp particles.
- Getting one: **crafted item**, placed like a boat. (Short inflate animation on placement as polish, not a block-assembly step.)
- 1.0 features: **dyeable envelope** (16 dyes + banner patterns), **cargo + passengers** (4 seats incl. mobs, attachable chest), **mooring post** block.
- Target: **Fabric 26.3 only** until 1.0 is good; ports later via the Stonecutter pattern (HavingABlast).
- My defaults (change if wanted): burner needs furnace fuel (right-click burner with fuel, HUD gauge, config `needsFuel`); no fuel = slow sink and soft landing; balloon destroyed mid-air gives riders slow falling.

## Project setup
- Folder `mods/HotAirBalloons/`, id `hotairballoons`, package `io.github.profetgit.hotairballoons` (searchable working name; public title chosen at release).
- Copy build skeleton from `mods/CountryRoads`: `build.gradle.kts`, `settings.gradle.kts`, `gradle.properties` (mc=26.3, loader 0.19.5, fabric_api 0.161.0+26.3, modmenu), `gradle/` incl. `gradle-daemon-jvm.properties`, `gradlew*`, `LICENSE`, `fabric.mod.json` template expansion.
- Own git repo, identity copied from `git -C mods/Stage config`; `CLAUDE.md`, `STATUS.md`, `PRD.md` from the start, CLAUDE/STATUS in `.git/info/exclude`.
- `dev/modtest.json` so `tools/ModTest/mt.py HotAirBalloons quick|full` works; test driver + `ModTestHook` via `tools/ModTest/sync_hook.py`.

## Architecture
- `entity/HotAirBalloon extends VehicleEntity implements Leashable, ContainerEntity` (chest pattern from vanilla ChestBoat). Synced data: heat, fuel ticks, wind vector (server-computed, so clients need no seed), dye colour, banner layers, hasChest, inflate progress.
- `entity/BalloonPhysics` — pure functions: heat → lift with lag, drag, max climb/sink, ceiling, landing; unit-tested.
- `entity/Wind` — deterministic f(seed, height band, game time), slow direction changes; unit-tested.
- `net/BalloonInput` C2S payload (burner/vent state), like vanilla's boat paddle packet, so other players see the flame.
- Seats: 4 corner offsets; mobs board like boats; seat count shared.
- Client: `client/BalloonModel` (LayerDefinition: basket, ropes, burner, envelope panels), `client/BalloonRenderer` with render state; envelope sway as a spring on acceleration; flame flicker; vanilla sounds only (fire/firecharge/blaze), no custom sound files.
- Envelope colour: base dye + banner pattern layers drawn on the envelope panels from the vanilla banner-pattern atlas.
- `block/MooringPost` (+ block item): tie the balloon with a vanilla lead (Leashable); tethered balloon hovers in place, no wind drift, no fuel use.
- Items: envelope, wicker basket, burner → shapeless craft to Hot Air Balloon. Recipes, lang, one advancement.
- Config + Mod Menu screen, same pattern as `mods/CountryRoads/.../client/ConfigScreen`.

## Milestones (each: build green, version bump, install in Fabric 26.3 test profile, commit)
- **M0 Scaffold** — builds, boots in `mt.py quick`.
- **M1 Ride spike** — placeholder cube model; place, mount, burner climbs, vent lands, break drops item. **Risk to settle first:** vanilla kicks players whose vehicle floats > 80 ticks without allow-flight (`ServerGamePacketListenerImpl` vehicle-float check); find how happy ghast avoids it (`wb src` 26.3) and do the same. Exit: client scene measures climb/descend; dedicated-server run with a second client sees the same motion (BeltLantern `dev/server/mp.py` pattern), nobody kicked.
- **M2 Physics + wind + mooring** — inertia, wind bands, particles, lead to post. Exit: unit tests for BalloonPhysics/Wind; scene measures drift with and without tether.
- **M3 Model + art** — Blockbench model (MCP failed to connect today; HTTP fallback per memory `blockbench-http-fallback`), Aseprite textures, sway spring, flame, inflate on place, sounds. Exit: ClientCapture stills, `wb img flicker` clean, user verdict.
- **M4 Passengers + cargo** — 4 riders, mobs, chest persists over reload. Exit: scene asserts seats + chest contents after rejoin.
- **M5 Dye + banners** — 16 colours + banner layers, persisted. Exit: still grid of all colours + one banner, user verdict.
- **M6 Fuel, HUD, recipes, advancement, config.** Exit: scene runs fuel out → soft landing; recipes load.
- **M7 Release prep** — `polish` skill pass, `mt.py full`, icon/banner (`pack-icon-animation`), 60 fps WebP showcases (`pack-showcase`), README with description kit + Ko-fi + BisectHosting, AI disclosure; Modrinth/CurseForge drafts only after user OK.

## Verification
- `./gradlew build` (unit tests incl. physics/wind).
- `python3 tools/ModTest/mt.py HotAirBalloons quick` during work, `full` before commits that touch rendering/sync.
- Multiplayer: dedicated server + 2 clients run (M1, M4), through `wb job run`.
- Visuals: ClientCapture frames + `wb img flicker`; user hands-on in Fabric 26.3 profile.

## Delegation
- Me: M1 physics/sync design, final review of every diff.
- `builder`: M0 scaffold, M4 seats/chest, M6 recipes/config (disjoint files).
- `tester`/`mod-tester`: run tiers, report verbatim.
