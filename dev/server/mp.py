#!/usr/bin/env python3
"""Hot Air Balloons on a real dedicated server (allow-flight off), off-screen, two modded clients.

mp.py [--mc 26.3] [--port 25671]

Pilot joins, the console seats it in a balloon, it burns for 200 ticks (past vanilla's 80-tick vehicle float limit),
hovers, vents down. Watch joins, stands in the basket for the whole flight (survival, not riding) and must see the
climb and the flame without being kicked for floating or corrected for moving wrongly. Checks: both clients' results.json, the server
never kicked anyone for floating, nobody dropped, the server log is clean.
Work dir: dev/server/.work/mp-<mc>. Pattern from mods/BeltLantern/dev/server/mp.py.
"""
import argparse
import importlib.util
import re
import shutil
import subprocess
import sys
import threading
import time
from pathlib import Path

HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[1]
WS = ROOT.parents[1]
sys.path.insert(0, str(WS / "tools/ModTest"))
sys.path.insert(0, str(WS / "tools/ModJar"))
import client  # noqa: E402
import slots  # noqa: E402

_spec = importlib.util.spec_from_file_location("modjar_smoke", WS / "tools/ModJar" / "smoke.py")
ms = importlib.util.module_from_spec(_spec)
_spec.loader.exec_module(ms)

PILOT = ("Pilot", "5e1dcaa0-0000-4000-8000-00000000ba01")
WATCH = ("Watcher", "5e1dcaa0-0000-4000-8000-00000000ba02")


def jar(mc: str) -> Path:
    version = re.search(r"^mod\.version=(.+)$", (ROOT / "gradle.properties").read_text(), re.M).group(1).strip()
    j = ROOT / "versions" / f"{mc}-fabric" / "build/libs" / f"hotairballoons-{version}+{mc}-fabric.jar"
    if not j.is_file():
        sys.exit(f"no {j.name}: build it first")
    return j


class Server:
    def __init__(self, work: Path, cmd: list[str], port: int, mods: list[Path]):
        shutil.rmtree(work, ignore_errors=True)
        (work / "mods").mkdir(parents=True)
        (work / "tmp").mkdir()
        (work / "eula.txt").write_text("eula=true\n")
        (work / "server.properties").write_text(
            f"online-mode=false\nserver-ip=127.0.0.1\nserver-port={port}\nlevel-type=minecraft\\:flat\ngenerate-structures=false\n"
            "view-distance=6\nsimulation-distance=6\nspawn-protection=0\ndifficulty=peaceful\npause-when-empty-seconds=0\n"
            "allow-flight=false\nwhite-list=false\nenforce-whitelist=false\nsync-chunk-writes=false\n")
        for m in mods:
            shutil.copy(m, work / "mods")
        self.log = work / "server.log"
        self.out = open(self.log, "w")
        self.proc = subprocess.Popen(cmd, cwd=work, stdin=subprocess.PIPE, stdout=self.out, stderr=subprocess.STDOUT, text=True)

    def send(self, *lines):
        for l in lines:
            self.proc.stdin.write(l + "\n")
        self.proc.stdin.flush()

    def text(self) -> str:
        return self.log.read_text(errors="replace")

    def wait(self, pattern: str, timeout: float) -> re.Match | None:
        end = time.time() + timeout
        while time.time() < end:
            m = re.search(pattern, self.text())
            if m:
                return m
            if self.proc.poll() is not None:
                return None
            time.sleep(0.25)
        return None

    def stop(self):
        try:
            self.send("stop")
            self.proc.wait(90)
        except Exception:
            self.proc.kill()
        self.out.close()


def wait_log(path: Path, pattern: str, timeout: float) -> bool:
    end = time.time() + timeout
    while time.time() < end:
        if path.exists() and re.search(pattern, path.read_text(errors="replace")):
            return True
        time.sleep(0.5)
    return False


def run(mc: str, port: int) -> tuple[bool, list[str]]:
    work = HERE / ".work" / f"mp-{mc}"
    shutil.rmtree(work, ignore_errors=True)
    notes, ok, results = [], True, {}
    mod = jar(mc)
    with slots.slot("server", f"HotAirBalloons mp {mc}"):
        srv = Server(work / "server", ms.command(mc, "fabric", work / "server"), port, [mod, ms.fabric_api(mc)])
        try:
            if not srv.wait(r"Done \(\d", 240):
                return False, ["server did not start"] + ms.tail(srv.log)

            def run_client(name, uuid, role, seconds):
                props = {"hotairballoons.selftest": str(work / f"out-{role}"), "hotairballoons.selftest.mode": f"mp-{role}",
                         "modtest.audit": "1"}
                results[role] = client.run(mc=mc, loader="fabric", out=work / f"out-{role}", game=work / f"game-{role}",
                                           mod_jars=[mod], fabric_api_jar=True, props=props, join=f"127.0.0.1:{port}",
                                           options={"fps": 60, "volume": 0.0, "render_distance": 6},
                                           timeout=seconds, user=name, uuid=uuid, label=f"HotAirBalloons mp {role}")

            pilot = threading.Thread(target=run_client, args=(*PILOT, "pilot", 420))
            pilot.start()
            if not srv.wait(r"Pilot joined the game", 300):
                notes.append("FAIL the pilot never joined")
                ok = False
            else:
                srv.send("gamerule spawn_mobs false", "gamerule spawn_monsters false", "op Pilot", "gamemode survival Pilot", "give Pilot coal 16",
                         "tp Pilot 0.5 -60 0.5 0 0")
                watch = threading.Thread(target=run_client, args=(*WATCH, "watch", 420))
                watch.start()
                if srv.wait(r"Watcher joined the game", 300):
                    srv.send("tp Watcher 12.5 -60 0.5 90 -30", "gamemode spectator Watcher")
                else:
                    notes.append("FAIL the watcher never joined")
                    ok = False
                if not wait_log(work / "game-pilot/client.log", r"HAB-MP pilot ready", 240):
                    notes.append("FAIL the pilot's test driver never started")
                    ok = False
                time.sleep(3)
                srv.send("summon hotairballoons:hot_air_balloon 0.5 -60 2.5",
                         "ride Pilot mount @e[type=hotairballoons:hot_air_balloon,limit=1,sort=nearest]")
                time.sleep(1)
                srv.send("gamemode survival Watcher", "tp Watcher 0.9 -59.85 2.9")
                if wait_log(work / "game-pilot/client.log", r"HAB-MP pilot aloft", 240):
                    srv.send("execute as @e[type=hotairballoons:hot_air_balloon,limit=1] run data get entity @s Pos[1]")
                watch.join(600)
            pilot.join(600)
        finally:
            srv.stop()
    for role, r in results.items():
        for x in r["results"]:
            notes.append(("PASS " if x["pass"] else "FAIL ") + f"{role}/{x['name']}  {x.get('detail', '')}")
            ok &= bool(x["pass"])
        if not r["results"]:
            ok = False
            notes.append(f"FAIL {role}: no results ({'; '.join(r['notes'])})")
    stext = srv.text()
    kicked = re.findall(r"(\w+) was kicked for floating.*", stext)
    notes.append(("FAIL " if kicked else "PASS ") + "server/no_float_kick  " + (", ".join(kicked) or "none"))
    ok &= not kicked
    m = re.search(r"has the following entity data: (-?[\d.]+)d", stext)
    notes.append(("PASS " if m and float(m.group(1)) > -52 else "FAIL ") + f"server/balloon_aloft  y={m.group(1) if m else '?'}")
    ok &= bool(m and float(m.group(1)) > -52)
    wrongly = len(re.findall(r"Watcher moved wrongly", stext))
    notes.append(("PASS " if wrongly <= 5 else "FAIL ") + f"server/walker_not_corrected  {wrongly} 'moved wrongly'")
    ok &= wrongly <= 5
    for who, why in re.findall(r"(Pilot|Watcher) lost connection: (.*)", stext):
        if "Disconnected" not in why and "Server closed" not in why:
            ok = False
            notes.append(f"FAIL server: {who} dropped: {why[:200]}")
    for line in stext.splitlines():
        if ms.PROBLEM.search(line) and not any(re.search(n, line) for n in ms.NOISE) and "Can't keep up" not in line:
            ok = False
            notes.append("FAIL SERVER LOG " + line.strip()[:220])
    return ok, notes


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("--mc", default="26.3")
    ap.add_argument("--port", type=int, default=25671)
    a = ap.parse_args()
    ok, notes = run(a.mc, a.port)
    for n in notes:
        print(n)
    print(f"mp {a.mc}: {'PASS' if ok else 'FAIL'}")
    sys.exit(0 if ok else 1)


if __name__ == "__main__":
    main()
