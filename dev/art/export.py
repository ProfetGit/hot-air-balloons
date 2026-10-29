"""balloon.bbmodel -> client/BalloonMesh.java (model layers) + layout.json (per-face texel maps for paint.lua).

Blockbench space is y up with +x to the east; the game's model space is Blockbench turned 180 degrees about z
(x and y negated), which the renderer's scale(-1, -1, 1) undoes. A fractional cube size becomes the next integer
(the box-UV dims) plus a negative CubeDeformation, so textures stay on whole texels.
"""
import json
import math
from pathlib import Path

HERE = Path(__file__).parent
MOD = HERE.parent.parent
JAVA = MOD / "src/main/java/io/github/profetgit/hotairballoons/client/BalloonMesh.java"
# Model layer -> (texture atlas, groups). The flame is its own layer because it renders emissive.
LAYERS = {"main": ("main", ["basket", "rigging", "rope_0", "rope_1", "rope_2", "rope_3"]), "flame": ("main", ["flame", "pilot"]), "envelope": ("envelope", ["envelope"])}


def dims(size):
    return max(1, math.ceil(round(size, 4) - 1e-6))


def num(v):
    v = round(v, 4)
    return f"{int(v)}" if v == int(v) else f"{v}F"


def load():
    d = json.loads((HERE / "balloon.bbmodel").read_text())
    elements = {e["uuid"]: e for e in d["elements"]}
    groups = {g["uuid"]: g for g in d["groups"]}
    tex = {t["name"]: (t["uv_width"], t["uv_height"]) for t in d["textures"]}
    out = []
    for node in d["outliner"]:
        g = groups[node["uuid"]]
        cubes = [elements[c] for c in node["children"] if isinstance(c, str) and c in elements]
        out.append((g["name"], g["origin"], cubes))
    return out, tex


def java_cube(c, pivot):
    """Local game-space box (min corner, integer dims, grow) for a Blockbench cube in a group at pivot."""
    f, t = c["from"], c["to"]
    lo = [-(t[0] - pivot[0]), -(t[1] - pivot[1]), f[2] - pivot[2]]
    size = [t[k] - f[k] for k in range(3)]
    n = [dims(s) for s in size]
    grow = [(size[k] - n[k]) / 2 for k in range(3)]
    return [lo[k] + grow[k] for k in range(3)], n, grow


def faces(c, pivot, atlas):
    """Texel maps of the six faces, mirroring vanilla ModelPart.Cube/Polygon, in Blockbench (world) coordinates."""
    (x, y, z), (w, h, d), (gx, gy, gz) = java_cube(c, pivot)
    off = (-pivot[0], -pivot[1], pivot[2])
    x0, y0, z0, x1, y1, z1 = x - gx, y - gy, z - gz, x + w + gx, y + h + gy, z + d + gz
    P = {k: (a + off[0], b + off[1], cz + off[2]) for k, (a, b, cz) in {
        "t0": (x0, y0, z0), "t1": (x1, y0, z0), "t2": (x1, y1, z0), "t3": (x0, y1, z0),
        "l0": (x0, y0, z1), "l1": (x1, y0, z1), "l2": (x1, y1, z1), "l3": (x0, y1, z1)}.items()}
    U, V = c.get("uv_offset", [0, 0])
    u0, u1, u2, u22, u3, u4 = U, U + d, U + d + w, U + d + w + w, U + d + w + d, U + d + w + d + w
    v0, v1, v2 = V, V + d, V + d + h
    polys = [("DOWN", ["l1", "l0", "t0", "t1"], u1, v0, u2, v1, (0, 1, 0)),
             ("UP", ["t2", "t3", "l3", "l2"], u2, v1, u22, v0, (0, -1, 0)),
             ("WEST", ["t0", "l0", "l3", "t3"], u0, v1, u1, v2, (1, 0, 0)),
             ("NORTH", ["t1", "t0", "t3", "t2"], u1, v1, u2, v2, (0, 0, -1)),
             ("EAST", ["l1", "t1", "t2", "l2"], u2, v1, u3, v2, (-1, 0, 0)),
             ("SOUTH", ["l0", "l1", "l2", "l3"], u3, v1, u4, v2, (0, 0, 1))]
    world = lambda p: (-p[0], -p[1], p[2])
    out = []
    for name, vs, a0, b0, a1, b1, normal in polys:
        V0, V1, V2 = (world(P[vs[i]]) for i in (0, 1, 2))
        du = [(V0[k] - V1[k]) / (a1 - a0) for k in range(3)]
        dv = [(V2[k] - V1[k]) / (b1 - b0) for k in range(3)]
        ua, va = min(a0, a1), min(b0, b1)
        o = [V1[k] + (ua - a0) * du[k] + (va - b0) * dv[k] for k in range(3)]
        out.append({"cube": c["name"], "mat": c["name"].split("_")[0], "atlas": atlas, "face": name,
                    "rect": [ua, va, max(a0, a1), max(b0, b1)], "o": o, "du": du, "dv": dv, "n": normal,
                    "box": [c["from"], c["to"]]})
    return out


def main():
    groups, tex = load()
    layout = {"atlases": tex, "faces": []}
    methods = []
    for layer, (atlas, names) in LAYERS.items():
        lines = []
        for gname, pivot, cubes in groups:
            if gname not in names:
                continue
            lines.append(f'        root.addOrReplaceChild("{gname}", CubeListBuilder.create()')
            for c in cubes:
                (x, y, z), (w, h, d), grow = java_cube(c, pivot)
                dfm = "" if not any(grow) else f", new CubeDeformation({', '.join(num(g) for g in grow)})"
                lines.append(f'            .texOffs({c.get("uv_offset", [0, 0])[0]}, {c.get("uv_offset", [0, 0])[1]})'
                             f'.addBox({num(x)}, {num(y)}, {num(z)}, {w}, {h}, {d}{dfm})')
                layout["faces"] += faces(c, pivot, atlas)
            lines[-1] += ","
            lines.append(f"            PartPose.offset({num(-pivot[0])}, {num(-pivot[1])}, {num(pivot[2])}));")
        w, h = tex[atlas]
        methods.append(f"    static LayerDefinition {layer}() {{\n        MeshDefinition mesh = new MeshDefinition();\n"
                       f"        PartDefinition root = mesh.getRoot();\n" + "\n".join(lines) +
                       f"\n        return LayerDefinition.create(mesh, {w}, {h});\n    }}")
    JAVA.write_text(f"""package io.github.profetgit.hotairballoons.client;

import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;

/** Generated by dev/art/export.py from dev/art/balloon.bbmodel; edit the model in Blockbench, not here. */
final class BalloonMesh {{
    private BalloonMesh() {{}}

{chr(10).join(methods)}
}}
""")
    (HERE / "layout.json").write_text(json.dumps(layout))
    print("faces", len(layout["faces"]), "->", JAVA.name, "+ layout.json")


if __name__ == "__main__":
    main()
