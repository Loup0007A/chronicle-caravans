#!/usr/bin/env python3
"""Covered wagon of Chronicle Caravans: one description, three outputs.

  python3 tools/wagon.py            -> WagonModel.java + textures/entity/wagon.png
  python3 tools/wagon.py --preview  -> also /tmp/wagon_preview.png (software render, to check the look)

Design space: units are pixels (16 = one block), x to the right, y up (0 = ground), the FRONT is -z.
It is converted to Minecraft's model space (x and y flipped, y = 24 at the ground) when the Java code is written.
"""
import math
import random
import sys

from PIL import Image

TEX_W = 256
JAVA = "src/main/java/com/chroniclecaravans/client/WagonModel.java"
PNG = "src/main/resources/assets/chroniclecaravans/textures/entity/wagon.png"


class Box:
    def __init__(self, x, y, z, w, h, d, mat):
        self.x, self.y, self.z = x, y, z
        self.w, self.h, self.d = int(w), int(h), int(d)
        self.mat = mat  # material name, or dict face -> material (top/bottom/side)
        self.u = self.v = 0


class Part:
    def __init__(self, name, pivot=(0, 0, 0), rot=(0, 0, 0)):
        self.name = name
        self.pivot = pivot
        self.rot = rot
        self.boxes = []
        self.children = []

    def box(self, x, y, z, w, h, d, mat):
        self.boxes.append(Box(x, y, z, w, h, d, mat))
        return self

    def child(self, name, pivot=(0, 0, 0), rot=(0, 0, 0)):
        p = Part(name, pivot, rot)
        self.children.append(p)
        return p

    def walk(self):
        yield self
        for c in self.children:
            yield from c.walk()


# ─────────────────────────────────────────────────────────────── the wagon

root = Part("root")
body = root.child("body")
canopy = root.child("canopy")
lantern = root.child("lantern")

BED_X = 11          # half width of the bed
BED_Z0, BED_Z1 = -20, 20
FLOOR_Y = 10        # floor sits on y 10..12
SIDE_TOP = 20
FRONT_AXLE, REAR_AXLE = -13, 14
R_FRONT, R_REAR = 7, 8

# Chassis: two long beams, two axle bars, and the floor
body.box(-8, 8, -24, 2, 2, 46, "darkwood").box(6, 8, -24, 2, 2, 46, "darkwood")
body.box(-14, R_FRONT - 1, FRONT_AXLE - 1, 28, 2, 2, "iron")
body.box(-14, R_REAR - 1, REAR_AXLE - 1, 28, 2, 2, "iron")
body.box(-BED_X, FLOOR_Y, BED_Z0, 2 * BED_X, 2, BED_Z1 - BED_Z0, "planks")
# Side boards (with a dark top rail), front board and tailgate
for sx in (-BED_X, BED_X - 1):
    body.box(sx, FLOOR_Y + 2, BED_Z0, 1, SIDE_TOP - FLOOR_Y - 3, BED_Z1 - BED_Z0, "planks")
    body.box(sx - (1 if sx < 0 else 0), SIDE_TOP - 1, BED_Z0 - 1, 2, 1, BED_Z1 - BED_Z0 + 2, "darkwood")
body.box(-BED_X + 1, FLOOR_Y + 2, BED_Z0, 2 * BED_X - 2, 6, 1, "planks")
body.box(-BED_X + 1, FLOOR_Y + 2, BED_Z1 - 1, 2 * BED_X - 2, 5, 1, "planks")
# Iron corner straps
for sx in (-BED_X - 1, BED_X):
    for z in (BED_Z0, BED_Z1 - 2):
        body.box(sx, FLOOR_Y, z, 1, SIDE_TOP - FLOOR_Y, 2, "iron")
# Driver bench at the front, a step, and the brake lever
body.box(-9, SIDE_TOP - 1, BED_Z0 + 1, 18, 2, 4, "darkwood")
body.box(-9, FLOOR_Y + 2, BED_Z0 + 1, 2, 7, 2, "darkwood").box(7, FLOOR_Y + 2, BED_Z0 + 1, 2, 7, 2, "darkwood")
body.box(BED_X, 6, BED_Z0 + 2, 3, 1, 4, "darkwood")
lever = body.child("lever", (BED_X + 1, FLOOR_Y + 4, BED_Z0 + 6), (0.35, 0, 0))
lever.box(0, 0, -1, 1, 12, 1, "darkwood").box(-0.5, 11, -1.5, 2, 2, 2, "iron")
# Cargo seen through the rear opening: crates, a barrel, a sack
body.box(-10, FLOOR_Y + 2, 9, 8, 8, 8, "crate")
body.box(-9, FLOOR_Y + 10, 10, 6, 6, 6, "crate")
body.box(1, FLOOR_Y + 2, 10, 7, 10, 7, "barrel")
body.box(-1, FLOOR_Y + 2, 2, 9, 5, 6, "sack")
body.box(-10, FLOOR_Y + 2, -6, 7, 7, 7, "crate")
# Water barrel strapped to the right side, tool chest on the left
body.box(BED_X, FLOOR_Y + 1, 6, 4, 7, 5, "barrel")
body.box(-BED_X - 3, FLOOR_Y + 1, -8, 3, 5, 10, "crate")
# Shafts to the draft llama, and their crossbar
for sx in (-9, 8):
    shaft = body.child("shaft" + ("_l" if sx < 0 else "_r"), (sx + 0.5, 9, BED_Z0 - 2), (0.30, 0, 0))
    shaft.box(-0.5, -0.5, -29, 1, 1, 30, "darkwood")
    shaft.box(-1, -1, -27, 2, 2, 2, "iron")
body.box(-9, 9, BED_Z0 - 3, 18, 1, 1, "darkwood")

# Wheels: a hub, three spoke bars (six spokes) and twelve felloe segments with an iron tyre
WHEELS = []


def wheel(name, x, z, r, side):
    p = body.child(name, (x, r, z))
    WHEELS.append((name, r))
    p.box(-1.5 if side < 0 else -1.5, -2, -2, 3, 4, 4, "hub")
    hub2 = p.child(name + "_hub", (0, 0, 0), (math.pi / 4, 0, 0))
    hub2.box(-1, -2, -2, 2, 4, 4, "hub")
    for k in range(3):
        s = p.child(f"{name}_spoke{k}", (0, 0, 0), (k * math.pi / 3, 0, 0))
        s.box(-0.5, -(r - 1), -0.5, 1, 2 * (r - 1), 1, "spoke")
    seg = 12
    length = 2 * r * math.sin(math.pi / seg) + 1.2
    for k in range(seg):
        a = k * 2 * math.pi / seg
        rim = p.child(f"{name}_rim{k}", (0, 0, 0), (a, 0, 0))
        rim.box(-1, r - 1.5, -length / 2, 2, 2, max(2, round(length)), {"top": "tyre", "bottom": "wood", "side": "wood"})


wheel("wheel_fl", -14, FRONT_AXLE, R_FRONT, -1)
wheel("wheel_fr", 14, FRONT_AXLE, R_FRONT, 1)
wheel("wheel_rl", -14, REAR_AXLE, R_REAR, -1)
wheel("wheel_rr", 14, REAR_AXLE, R_REAR, 1)

# Canopy (tinted with the colour of the caravan's region): a half-ellipse of canvas panels
# from the top of the side boards, with flared ends and four wooden hoops.
ARC_C = SIDE_TOP - 1          # centre height of the arc
ARC_RX, ARC_RY = BED_X + 0.5, 17
N_PANELS = 9


def arc_point(t, scale=1.0):
    a = math.pi * t
    return (-math.cos(a) * ARC_RX * scale, ARC_C + math.sin(a) * ARC_RY * scale)


def arc(part, prefix, z0, depth, scale, mat, thick=1):
    for i in range(N_PANELS):
        (x0, y0), (x1, y1) = arc_point(i / N_PANELS, scale), arc_point((i + 1) / N_PANELS, scale)
        mx, my = (x0 + x1) / 2, (y0 + y1) / 2
        length = math.hypot(x1 - x0, y1 - y0) + 0.9
        ang = math.atan2(y1 - y0, x1 - x0)
        p = part.child(f"{prefix}{i}", (mx, my, z0), (0, 0, ang))
        p.box(-length / 2, -thick / 2, 0, max(2, round(length)), thick, depth, mat)


arc(canopy, "cloth", BED_Z0 + 3, BED_Z1 - BED_Z0 - 6, 1.0, "canvas")
arc(canopy, "flare_f", BED_Z0, 3, 1.06, "canvas")
arc(canopy, "flare_r", BED_Z1 - 3, 3, 1.06, "canvas")
for j, z in enumerate((BED_Z0 + 2, -6, 6, BED_Z1 - 3)):
    arc(body, f"hoop{j}_", z, 1, 1.03, "darkwood", thick=1)
# Ropes tying the canvas down
for z in (-10, 0, 10):
    body.box(-BED_X - 1, SIDE_TOP - 2, z, 1, 3, 1, "rope").box(BED_X, SIDE_TOP - 2, z, 1, 3, 1, "rope")

# Lantern hanging under the front hoop (rendered full-bright)
lantern.box(-0.5, 29, BED_Z0 + 2, 1, 4, 1, "chain")
lantern.box(-2, 23, BED_Z0 + 1, 4, 6, 4, "lantern")
lantern.box(-1.5, 29, BED_Z0 + 1.5, 3, 1, 3, "iron")

# ─────────────────────────────────────────────────────────────── texture

PALETTE = {
    "planks": ((172, 128, 80), (120, 86, 52)),
    "darkwood": ((104, 74, 46), (70, 48, 30)),
    "wood": ((150, 108, 66), (104, 74, 44)),
    "spoke": ((160, 118, 72), (110, 80, 48)),
    "iron": ((112, 112, 120), (70, 70, 78)),
    "tyre": ((86, 86, 94), (52, 52, 60)),
    "hub": ((74, 72, 78), (40, 38, 44)),
    "canvas": ((236, 232, 220), (196, 190, 176)),
    "crate": ((196, 160, 104), (128, 96, 56)),
    "barrel": ((128, 92, 56), (86, 60, 36)),
    "sack": ((200, 172, 120), (160, 132, 86)),
    "rope": ((178, 150, 100), (130, 104, 66)),
    "chain": ((80, 80, 88), (50, 50, 56)),
    "lantern": ((255, 210, 110), (60, 56, 60)),
}


def shade(c, k):
    return tuple(max(0, min(255, int(v * k))) for v in c)


def paint(img, mat, x0, y0, w, h, long_u, rnd):
    """Paints one face. long_u: the grain runs along u (texture x) rather than v."""
    px = img.load()
    base, dark = PALETTE[mat]
    for j in range(h):
        for i in range(w):
            n = (0.97 + rnd.random() * 0.05) if mat == "canvas" else (0.94 + rnd.random() * 0.10)
            c = shade(base, n)
            a, b = (i, j) if long_u else (j, i)  # a along the grain, b across
            if mat in ("planks", "darkwood", "wood", "spoke"):
                if b % 4 == 3:
                    c = shade(dark, 0.95 + rnd.random() * 0.1)
                elif (a * 7 + b * 13) % 23 == 0:
                    c = shade(dark, 1.1)
            elif mat == "crate":
                if i in (0, w - 1) or j in (0, h - 1) or (w > 4 and h > 4 and (i == j or i == w - 1 - j)):
                    c = shade(dark, 1.0)
            elif mat == "barrel":
                if (j in (1, h - 2) and h > 4) or (h <= 4 and False):
                    c = (76, 76, 84)
                elif i % 3 == 2:
                    c = shade(dark, 1.0)
            elif mat == "canvas":
                # woven cloth: faint seams across the panels, a darker hem near the edges
                if b % 6 == 5:
                    c = shade(c, 0.9)
                if rnd.random() < 0.015:
                    c = shade(c, 0.88)
            elif mat in ("iron", "tyre", "hub", "chain"):
                if rnd.random() < 0.12:
                    c = shade(base, 1.25)
                if mat == "hub" and (i + j) % 3 == 0:
                    c = shade(dark, 1.0)
            elif mat == "sack":
                if (i + j) % 2 == 0:
                    c = shade(c, 0.94)
            elif mat == "rope":
                if (a + b) % 2 == 0:
                    c = shade(dark, 1.0)
            elif mat == "lantern":
                frame = i in (0, w - 1) or j in (0, h - 1)
                c = dark if frame else shade(base, 0.9 + 0.2 * (1 - abs(j - h / 2) / max(1, h / 2)))
            px[x0 + i, y0 + j] = c + (255,)


def faces(b):
    """Texture rectangles of a box (Minecraft's unwrap). Visual top first."""
    u, v, w, h, d = b.u, b.v, b.w, b.h, b.d
    return {
        "top": (u + d, v, w, d),
        "bottom": (u + d + w, v, w, d),
        "west": (u, v + d, d, h),
        "north": (u + d, v + d, w, h),
        "east": (u + d + w, v + d, d, h),
        "south": (u + 2 * d + w, v + d, w, h),
    }


def mat_of(b, face):
    if isinstance(b.mat, str):
        return b.mat
    key = "top" if face == "top" else "bottom" if face == "bottom" else "side"
    return b.mat[key]


def pack(boxes):
    items = sorted(boxes, key=lambda b: -(b.d + b.h))
    x = y = row = 0
    for b in items:
        bw, bh = 2 * (b.w + b.d), b.d + b.h
        if x + bw > TEX_W:
            x, y, row = 0, y + row, 0
        b.u, b.v = x, y
        x += bw
        row = max(row, bh)
    height = y + row
    th = 64
    while th < height:
        th *= 2
    return th


def build_texture(boxes):
    th = pack(boxes)
    img = Image.new("RGBA", (TEX_W, th), (0, 0, 0, 0))
    for k, b in enumerate(boxes):
        rnd = random.Random(k * 7919 + 17)
        dims = {"x": b.w, "y": b.h, "z": b.d}
        longest = max(dims, key=dims.get)
        for f, (fx, fy, fw, fh) in faces(b).items():
            if fw <= 0 or fh <= 0:
                continue
            # which box axis runs along u for this face
            u_axis = "x" if f in ("top", "bottom", "north", "south") else "z"
            v_axis = "z" if f in ("top", "bottom") else "y"
            long_u = longest == u_axis or (longest not in (u_axis, v_axis) and fw >= fh)
            paint(img, mat_of(b, f), fx, fy, fw, fh, long_u, rnd)
    return img


# ─────────────────────────────────────────────────────────────── Java

def fmt(v):
    s = f"{v:.4f}".rstrip("0").rstrip(".")
    if s in ("-0", ""):
        s = "0"
    return s + "F"


def java_part(p, parent_var, absolute, lines, depth=0):
    px, py, pz = p.pivot
    mx, my, mz = (-px, 24 - py, pz) if absolute else (-px, -py, pz)
    rx, ry, rz = -p.rot[0], -p.rot[1], p.rot[2]
    cubes = "CubeListBuilder.create()"
    for b in p.boxes:
        bx, by, bz = -(b.x + b.w), -(b.y + b.h), b.z
        cubes += f"\n                .texOffs({b.u}, {b.v}).addBox({fmt(bx)}, {fmt(by)}, {fmt(bz)}, {b.w}, {b.h}, {b.d})"
    pose = (f"PartPose.offsetAndRotation({fmt(mx)}, {fmt(my)}, {fmt(mz)}, {fmt(rx)}, {fmt(ry)}, {fmt(rz)})"
            if any(abs(r) > 1e-9 for r in (rx, ry, rz)) else f"PartPose.offset({fmt(mx)}, {fmt(my)}, {fmt(mz)})")
    var = "p_" + p.name
    lines.append(f"        PartDefinition {var} = {parent_var}.addOrReplaceChild(\"{p.name}\", {cubes}, {pose});")
    for c in p.children:
        java_part(c, var, False, lines, depth + 1)


def write_java(th):
    lines = []
    for top in root.children:
        java_part(top, "root", True, lines)
    wheels = "\n".join(f"        this.wheels[{i}] = this.body.getChild(\"{n}\");" for i, (n, _) in enumerate(WHEELS))
    radii = ", ".join(fmt(r / 16.0) for _, r in WHEELS)
    src = f"""package com.chroniclecaravans.client;

import com.chroniclecaravans.ChronicleCaravans;
import com.chroniclecaravans.entity.CaravanWagon;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
//#if !mc26
import net.minecraft.client.model.EntityModel;
//#endif
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
//#if !mc26
import net.minecraft.client.renderer.RenderType;
//#endif

/** Covered wagon. GENERATED by tools/wagon.py: edit the script, not this file. */
//#if mc26
//$$ public class WagonModel {{
//#else
public class WagonModel extends EntityModel<CaravanWagon> {{
//#endif
    public static final ModelLayerLocation LAYER = new ModelLayerLocation(ChronicleCaravans.id("wagon"), "main");
    /** Radius of each wheel, in blocks (same order as {{@link #wheels}}). */
    public static final float[] WHEEL_RADII = {{ {radii} }};

    public final ModelPart body;
    public final ModelPart canopy;
    public final ModelPart lantern;
    public final ModelPart[] wheels = new ModelPart[{len(WHEELS)}];

    public WagonModel(ModelPart root) {{
        //#if !mc26
        super(RenderType::entityCutoutNoCull);
        //#endif
        this.body = root.getChild("body");
        this.canopy = root.getChild("canopy");
        this.lantern = root.getChild("lantern");
{wheels}
    }}

    public static LayerDefinition createLayer() {{
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
{chr(10).join(lines)}
        return LayerDefinition.create(mesh, {TEX_W}, {th});
    }}

    /** {{@code roll}}: distance travelled, in blocks. */
    public void setRoll(float roll) {{
        for (int i = 0; i < wheels.length; i++) wheels[i].xRot = roll / WHEEL_RADII[i];
    }}

    //#if !mc26
    @Override
    public void setupAnim(CaravanWagon wagon, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch) {{
    }}

    //#if mc120
    //$$ @Override
    //$$ public void renderToBuffer(PoseStack poseStack, VertexConsumer buffer, int light, int overlay, float r, float g, float b, float a) {{
    //$$     body.render(poseStack, buffer, light, overlay, r, g, b, a);
    //$$     canopy.render(poseStack, buffer, light, overlay, r, g, b, a);
    //$$     lantern.render(poseStack, buffer, light, overlay, r, g, b, a);
    //$$ }}
    //#else
    @Override
    public void renderToBuffer(PoseStack poseStack, VertexConsumer buffer, int light, int overlay, int color) {{
        body.render(poseStack, buffer, light, overlay, color);
        canopy.render(poseStack, buffer, light, overlay, color);
        lantern.render(poseStack, buffer, light, overlay, color);
    }}
    //#endif
    //#endif
}}
"""
    with open(JAVA, "w") as f:
        f.write(src)


# ─────────────────────────────────────────────────────────────── preview (software renderer)

def rot_matrix(rx, ry, rz):
    cx, sx, cy, sy, cz, sz = math.cos(rx), math.sin(rx), math.cos(ry), math.sin(ry), math.cos(rz), math.sin(rz)
    Rx = [[1, 0, 0], [0, cx, -sx], [0, sx, cx]]
    Ry = [[cy, 0, sy], [0, 1, 0], [-sy, 0, cy]]
    Rz = [[cz, -sz, 0], [sz, cz, 0], [0, 0, 1]]
    return mm(mm(Rz, Ry), Rx)


def mm(a, b):
    return [[sum(a[i][k] * b[k][j] for k in range(3)) for j in range(3)] for i in range(3)]


def mv(a, v):
    return [sum(a[i][k] * v[k] for k in range(3)) for i in range(3)]


def preview(tex, tint, roll, out, yaw=-0.72, pitch=0.42, size=(900, 620)):
    import numpy as np
    W, H = size
    S = 9.0
    zbuf = np.full((H, W), -1e9)
    img = np.zeros((H, W, 3))
    # sky gradient and grass
    for y in range(H):
        t = y / H
        img[y, :] = (np.array([150, 190, 235]) * (1 - t) + np.array([210, 228, 245]) * t)
    tx = np.array(tex.convert("RGBA")).astype(float)
    light = np.array([0.45, 0.8, -0.35])
    light /= np.linalg.norm(light)
    cyw, syw, cp, sp = math.cos(yaw), math.sin(yaw), math.cos(pitch), math.sin(pitch)

    def project(p):
        x, y, z = p
        x, z = x * cyw - z * syw, x * syw + z * cyw
        y, z = y * cp - z * sp, y * sp + z * cp
        return np.array([W / 2 + x * S, H * 0.62 - y * S + 60, z])

    def tri(P, UV, col_mul, shade_k, cut=True):
        (x0, y0, _), (x1, y1, _), (x2, y2, _) = P
        minx, maxx = int(max(0, min(x0, x1, x2))), int(min(W - 1, max(x0, x1, x2) + 1))
        miny, maxy = int(max(0, min(y0, y1, y2))), int(min(H - 1, max(y0, y1, y2) + 1))
        den = (y1 - y2) * (x0 - x2) + (x2 - x1) * (y0 - y2)
        if abs(den) < 1e-9 or maxx < minx or maxy < miny:
            return
        xs, ys = np.meshgrid(np.arange(minx, maxx + 1) + 0.5, np.arange(miny, maxy + 1) + 0.5)
        a = ((y1 - y2) * (xs - x2) + (x2 - x1) * (ys - y2)) / den
        b = ((y2 - y0) * (xs - x2) + (x0 - x2) * (ys - y2)) / den
        c = 1 - a - b
        m = (a >= 0) & (b >= 0) & (c >= 0)
        if not m.any():
            return
        z = a * P[0][2] + b * P[1][2] + c * P[2][2]
        u = a * UV[0][0] + b * UV[1][0] + c * UV[2][0]
        v = a * UV[0][1] + b * UV[1][1] + c * UV[2][1]
        ui = np.clip(u.astype(int), 0, tx.shape[1] - 1)
        vi = np.clip(v.astype(int), 0, tx.shape[0] - 1)
        texel = tx[vi, ui]
        m &= texel[..., 3] > 10
        sub = zbuf[miny:maxy + 1, minx:maxx + 1]
        m &= z > sub
        sub[m] = z[m]
        col = texel[..., :3] * col_mul * shade_k
        img[miny:maxy + 1, minx:maxx + 1][m] = col[m]

    # ground
    g = 60
    quad = [(-g, 0, -g), (g, 0, -g), (g, 0, g), (-g, 0, g)]
    P = [project(q) for q in quad]
    for (i, j, k) in ((0, 1, 2), (0, 2, 3)):
        Pp = [P[i], P[j], P[k]]
        (x0, y0, _), (x1, y1, _), (x2, y2, _) = Pp
        tri(Pp, [(0, 0)] * 3, np.array([1, 1, 1]), 1, cut=False) if False else None
    ys = np.arange(H)
    # draw a flat grass plane by projecting each pixel row (cheap approximation)
    gy = project((0, 0, 0))[1]
    for y in range(H):
        pass

    def draw_part(p, M, T, tint_on):
        R = rot_matrix(*p.rot)
        M2 = mm(M, R)
        T2 = [T[i] + mv(M, p.pivot)[i] for i in range(3)]
        for b in p.boxes:
            corners = {}
            for dx in (0, 1):
                for dy in (0, 1):
                    for dz in (0, 1):
                        lp = (b.x + dx * b.w, b.y + dy * b.h, b.z + dz * b.d)
                        wp = [T2[i] + mv(M2, lp)[i] for i in range(3)]
                        corners[(dx, dy, dz)] = wp
            F = faces(b)
            spec = {
                "top": ([(0, 1, 0), (1, 1, 0), (1, 1, 1), (0, 1, 1)], (0, 1, 0)),
                "bottom": ([(0, 0, 0), (1, 0, 0), (1, 0, 1), (0, 0, 1)], (0, -1, 0)),
                "north": ([(0, 1, 0), (1, 1, 0), (1, 0, 0), (0, 0, 0)], (0, 0, -1)),
                "south": ([(0, 1, 1), (1, 1, 1), (1, 0, 1), (0, 0, 1)], (0, 0, 1)),
                "west": ([(0, 1, 0), (0, 1, 1), (0, 0, 1), (0, 0, 0)], (-1, 0, 0)),
                "east": ([(1, 1, 0), (1, 1, 1), (1, 0, 1), (1, 0, 0)], (1, 0, 0)),
            }
            for f, (cs, n) in spec.items():
                fx, fy, fw, fh = F[f]
                uv = [(fx, fy), (fx + fw - 0.01, fy), (fx + fw - 0.01, fy + fh - 0.01), (fx, fy + fh - 0.01)]
                pts = [project(corners[c]) for c in cs]
                wn = mv(M2, n)
                k = 0.55 + 0.45 * max(0.0, float(np.dot(wn, light)))
                mul = np.array(tint) if tint_on else np.array([1, 1, 1])
                if b.mat == "lantern":
                    k = 1.0
                tri([pts[0], pts[1], pts[2]], [uv[0], uv[1], uv[2]], mul, k)
                tri([pts[0], pts[2], pts[3]], [uv[0], uv[2], uv[3]], mul, k)
        for c in p.children:
            draw_part(c, M2, T2, tint_on)

    I = [[1, 0, 0], [0, 1, 0], [0, 0, 1]]
    for w_name, r in WHEELS:
        for p in body.walk():
            if p.name == w_name:
                p.rot = (-roll / r, 0, 0)
    draw_part(body, I, [0, 0, 0], False)
    draw_part(canopy, I, [0, 0, 0], True)
    draw_part(lantern, I, [0, 0, 0], False)
    Image.fromarray(np.clip(img, 0, 255).astype("uint8")).save(out)


if __name__ == "__main__":
    all_boxes = [b for p in root.walk() for b in p.boxes]
    tex = build_texture(all_boxes)
    tex.save(PNG)
    write_java(tex.height)
    print(f"{len(all_boxes)} boxes, {sum(1 for _ in root.walk())} parts, texture {TEX_W}x{tex.height}")
    if "--preview" in sys.argv:
        preview(tex, (0.93, 0.70, 0.40), 0.0, "/tmp/wagon_preview.png")
        preview(tex, (0.40, 0.58, 0.88), 3.0, "/tmp/wagon_preview_back.png", yaw=2.4, pitch=0.3)
        print("previews in /tmp")
