# Hot Air Balloons 0.0.24: release readiness review (2026-10-31)

Read-only review. No code changed, nothing uploaded. Commit 2301661, CurseForge 1776121.

## Verdict: NOT READY for a public release (ready for a beta)

Engineering is solid: release lane 62/62, mp 15/15 + 27/27. What stops a public "release" is a player-visible
blocker nobody has fixed, three open director verdicts (sounds, flight numbers, mooring look), a stale item icon and
a stale CHANGELOG. A **beta** upload (plan already says `beta`) after blocker 1 plus the cheap fixes below is defensible.

## Evidence run
- `python3 tools/Release/release.py plan HotAirBalloons`: version ok, git clean, files 1, description kit ok.
  TODO: changelog (no `## 0.0.24`), tags unverified (`targets.json` never checked against a live files page),
  art (`promo-tile/promo-tile-60fps.webp` missing).
- `wb job run --name hab-release-tests -- release.py test HotAirBalloons` (job hab-release-tests-x4g, 5m52s):
  `mt.py release` 26.3-fabric **PASS 62/62** (257 s); `mp` mp-flight **15/15**, mp-playtest **27/27**
  (dedicated server, allow-flight off: 0 kicks, 0 "moved wrongly", server log clean). Recorded at 2301661; plan now shows tests ok.
- Playtest `dev/playtest/report-2026-10-31.md`: 175/175 script checks.

## Blockers (fix before a public release)
1. **Burner under a low roof hurts the pilot, black screen, no cue.** Playtest blocker 1: 2-block gap, Space ->
   rises exactly 1.0, head inside roof, 1 hp / 10 ticks for 21 events. Likely cause (inferred, not proven):
   `HotAirBalloon.envelopeBox()` (`entity/HotAirBalloon.java:516`) starts 58/16 = 3.6 blocks above the basket, so
   a roof lower than that is already inside the box and collision (`:511`) never blocks the climb; the pilot's
   own eye/head has no ceiling check. Same root as friction 1 (under a tree: stuck at +1.0, dark green view).
   Fix direction: stop climbing (and zero `heat`) when the pilot's head box would enter a solid block; show an
   actionbar line ("Something is in the way above"). Needs a scene in `Scenarios` (roof at +2, assert no hurt events).
2. **No onboarding cue.** Friction 3: no text anywhere says Space = burner, Sprint = vent, coal = fuel; empty-tank
   blink unconfirmed to read (`BalloonHud` `blink`). A newcomer from CurseForge will think it is broken. Cheapest:
   one actionbar/toast line on first boarding ("Space: burner, Sprint: vent, needs coal") and "out of fuel" text.
3. **Creative needs coal** (friction 4, TASTE "Creative should just work"). `useFuel` (`HotAirBalloon.java:730`)
   only checks `Config.needsFuel`; `hasInfiniteMaterials()` is used elsewhere (`:689`, `:698`) but not here.
   Cheap fix: treat a creative pilot as unlimited fuel.
4. **Stale item icon.** `assets/hotairballoons/textures/item/hot_air_balloon.png` (16x16) still shows the old
   teardrop; the envelope is now a box. Candidate `dev/art/candidates/item_icon_box/` got critic FIX ("reads as a
   shield") and needs 2-3 variants plus the director's pick. It is the first thing a player sees in the recipe toast.
5. **CHANGELOG stale and wrong.** `CHANGELOG.md` has only `## 0.0.10 (unreleased)`; says emblem "front and back"
   (actual: all four sides), no walkable basket, wind lines, HUD wind arrow, fence mooring wording partly right.
   `release.py` needs a `## 0.0.24` section (or whichever public version) for the upload changelog.
6. **Open director verdicts** (STATUS): sounds "still unheard by the user" (only vanilla events, `BalloonSounds`
   volumes unjudged); "flight numbers still guesses until the user flies it"; mooring look verdict pending
   (0.0.22); gate 5 per PIPELINE needs the director's yes. Not something a test can close.

## Friction / should-fix (ranked)
1. Envelope pick box: only the basket is clickable (1.75 x 1.0); clicking the big balloon does nothing (playtest friction 5).
2. Placing within 0.5 of a wall silently does nothing; 0.7 off climbs only +0.56 per 120 ticks and slides into the wall (friction 2). Needs ~3 free blocks: say so.
3. Third-person behind while looking steeply down puts the camera inside the envelope (polish 1).
4. Clicking the basket with coal selected after Shift feeds instead of re-taking controls (polish 3).
5. Keeps rising after releasing Space (+6.2 in 60 ticks): feel unjudged (polish 2).
6. Pilot camera ~0.6 under the burner frame; looking straight up fills the view (STATUS Known gaps).
7. Not tested at all (playtest "couldn't test"): sound/subtitles, vanilla client, Sodium/Iris, real quit/rejoin,
   creative tab, fence/lead in a real world, breaking in survival, rain/night, recipe book. STATUS also: world
   rejoin untested (chest test is an entity roundtrip only).

## Description / README vs behaviour
- Fence mooring: README and CurseForge text say "tie to any fence with a lead", matching the code and the removal of
  the post (CLAUDE.md Mooring). OK. No mention of the post anywhere in README (checked).
- Recipe: README "three wool on top, string, a campfire and string in the middle, three wooden slabs" matches
  `recipe/hot_air_balloon.json` (`WWW / SCS / PPP`). OK.
- Controls: README "Sprint (Ctrl)" is the default sprint key; fine. Remaining claims (4 seats, 3 with chest, fuel, dyes,
  banners, shears, settings) match the CLAUDE.md/STATUS behaviour; I did not re-verify each in game.
- **`gradle.properties:13` `mod.homepage=https://modrinth.com/user/Profet`** goes into fabric.mod.json contact.
  Modrinth withheld every project; point it at the CurseForge page or the GitHub repo before building the release jar.
- `fabric.mod.json`: license `LicenseRef-All-Rights-Reserved` matches README "All rights reserved, with permissions".

## Side / compat claims
- README: "jar in the mods folder of the server and of every player, with Fabric API". Correct: `environment: "*"`,
  entity + `ServerGamePacketListenerImplMixin` + `EntityCollideMixin` are common, `BalloonInput` packet is required
  both sides. Plan tags `Client, Server, Java 25, requires fabric-api` match.
- `fabric.mod.json` depends `fabric-api: "*"`, `minecraft: ~${mc}`, `java >=25`. Mod Menu is `compileOnly` (optional): OK.
- EMF compat: `mixin/EmfEntityMixin` priority 1500, `require = 0` in the client list, and the mixins json has
  `defaultRequire: 1` for the rest: if EMF is absent it is skipped, if EMF is present but changes, FA pilot pose
  silently breaks (FA pilot stands verified `--addon fa` in the playtest). Fine for release; mention FA/EMF as optional.
- Not verified: vanilla-client behaviour with a modded server (not applicable, mod needed on both), Sodium/Iris.

## Saved-data compatibility
- Entity saves `heat`, `chest`, `fuel`, `look` plus chest + leash data (`HotAirBalloon.java:927-944`), all read with
  `...Or` defaults, so older saves load. Fuel saved as 0 when unlimited (re-set by `useFuel` next tick).
- Mooring Post removal (0.0.22) drops placed posts in old worlds, but no public version ever had it: no migration needed.
- No public release exists, so there is no upgrade path to protect; the first public version fixes the baseline.
  After release every field above becomes a compatibility contract.

## First public release: version and changelog
- Version is `0.0.24`; CurseForge channel in the plan is `beta`. Recommend keeping 0.x and `beta` (flight numbers and
  sounds unjudged). Do not rename to 1.0 until the director flies it. Bump to 0.0.25 with the fixes (rule: always bump).
- Write `## 0.0.25` (first public beta) covering: flight and wind, walkable basket, 4 seats/chest/mobs, dye and
  banner emblem on four sides, fence mooring, fuel, HUD, config/Mod Menu, advancement. Drop the "(unreleased) 0.0.10" header.
- Also: promo tile webp for the description kit (plan TODO), and verify tags once on the live files page.

## Cheapest next steps (in order)
1. Fix blocker 1 (head-in-roof check + actionbar) with a `Scenarios` roof case; hot swap if the game is open. ~1 builder step.
2. Creative = unlimited fuel; first-board hint line; out-of-fuel text. (same builder step, tweak lane)
3. Item icon: 2-3 variants from `item_icon_box`, critic, then ask the director to pick.
4. Fix `mod.homepage`; write CHANGELOG 0.0.25; make the promo tile.
5. Director: fly it once via `studio.py play HotAirBalloons`, give verdicts on sounds, flight numbers, mooring look.
6. Re-run `release.py test` (~6 min) on the final commit, then `release.py plan`, then upload as beta after the director's "release".

## Nice-to-have (can ship after)
Envelope pick box; wall-clearance message; third-person clipping; hover-after-release tuning; Sodium/Iris check;
real rejoin test; subtitles for the burner/gale sounds.
