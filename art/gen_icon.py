#!/usr/bin/env python3
"""Void Web logo: one geometry, written as the SVG master and the Android icon layers, once per theme.

A throwback to the old fox-and-globe browser icons, in the VOID family's style: a deep-space globe with the
VOID emblem inside (two galaxy arms spiralling out of a white-hot spark), and a flame-fox curled around it,
ear and snout at the top left, its tail flaring over the top right. The fox is coloured along its length
from the theme's accents to a white-hot tail tip. Drawn from scratch: no Mozilla artwork. The same shapes
are drawn (and animated) in Kotlin by WebLogo.kt. Themes come from art/themes.json (the VOID desktop
themes, shared with VOID Portal and Void Browse).

Run from the project root:  python3 art/gen_icon.py
"""
import json
import math
import os
import re

CX, CY = 54.0, 54.0
GLOBE_R = 20.0
THETA_MAX = 1.7 * math.pi
ARM_K = 0.49  # arm size relative to VOID Portal's: the arms reach the globe's edge
R0, R_GROW = 2.5 * ARM_K, 36.0 * ARM_K
ARM_W = 0.85

# The fox lies on the globe's edge and grows outward from it. t runs from the snout (0) to the tail tip (1).
FOX_TH0 = math.radians(-110)  # where the snout is (screen angles, y down: -90 is straight up)
FOX_SWEEP = math.radians(306)  # how far round it wraps (anticlockwise on screen: down the left, under, up the right)
FOX_IN = GLOBE_R - 1.2  # its inner edge, just over the globe's rim
# Thickness along the fox: a pointed snout, the head, a slight neck, the thick body under the globe, a broad tail
# that tapers to a fine tip.
FOX_W = [(0, 0.0), (0.025, 2.0), (0.05, 4.4), (0.08, 7.4), (0.13, 7.8), (0.18, 6.9), (0.3, 9.0), (0.45, 11.2), (0.62, 10.6), (0.76, 9.4), (0.88, 6.4), (0.95, 3.0), (1, 0.0)]
EAR = (0.075, 0.102, 0.124, 5.8)  # ear on the outer edge: starts, tip, ends (in t), height
EAR_LEAN = 0.75  # the ear tip leans toward the snout, so it stands up instead of out
SNOUT_LIFT = 3.2  # the snout points forward from the middle of the head, not along the globe's rim
FOX_SEGMENTS = 120  # pieces for the SVG master (the Android icon and the app use one sweep gradient)


def smoothstep(e0, e1, x):
    x = min(1.0, max(0.0, (x - e0) / (e1 - e0)))
    return x * x * (3 - 2 * x)


def interp(table, t):
    """Smooth (cosine) interpolation through the (t, value) table."""
    for (t0, v0), (t1, v1) in zip(table, table[1:]):
        if t <= t1:
            f = (t - t0) / (t1 - t0) if t1 > t0 else 0
            f = (1 - math.cos(math.pi * f)) / 2
            return v0 + (v1 - v0) * f
    return table[-1][1]


def bump(t, a, tip, b, h):
    """A pointed bump: rises from a to the tip, falls to b."""
    if t <= a or t >= b:
        return 0.0
    f = (t - a) / (tip - a) if t < tip else (b - t) / (b - tip)
    return h * f ** 1.5


def fox_edges(t):
    """The band's (outer, inner) edge points at t."""
    th = FOX_TH0 - t * FOX_SWEEP
    inn = FOX_IN + SNOUT_LIFT * (1 - smoothstep(0, 0.075, t)) - 5.0 * smoothstep(0.7, 1.0, t)  # the tail curls in over the globe
    ear = bump(t, *EAR)
    out = inn + interp(FOX_W, t) + ear
    c, s = math.cos(th), math.sin(th)
    lean = ear * EAR_LEAN  # along the fox toward the snout: (-sin, cos) on screen
    return (CX + out * c - lean * s, CY + out * s + lean * c), (CX + inn * c, CY + inn * s)


def fox_path(t0=0.0, t1=1.0, n=360):
    outer, inner = [], []
    for i in range(n + 1):
        o, p = fox_edges(t0 + (t1 - t0) * i / n)
        outer.append(o)
        inner.append(p)
    poly = outer + inner[::-1]
    return "M" + " L".join(f"{x:.2f},{y:.2f}" for x, y in poly) + " Z"


def arm_points(phase, n=64, length=1.0, width_scale=1.0, t0=0.0):
    """Outline of one tapered arm (or the part t0..length of it): thin at the centre, widest early, a fine tail."""
    pts = []
    for i in range(n + 1):
        t = t0 + i / n * (length - t0)
        th = t * THETA_MAX
        r = R0 + R_GROW * t ** 1.25
        pts.append((CX + r * math.cos(th + phase), CY + r * math.sin(th + phase), t))
    left, right = [], []
    for i, (x, y, t) in enumerate(pts):
        x2, y2, _ = pts[min(i + 1, n)] if i < n else pts[i]
        x1, y1, _ = pts[max(i - 1, 0)]
        dx, dy = x2 - x1, y2 - y1
        d = math.hypot(dx, dy) or 1
        nx, ny = -dy / d, dx / d
        w = (0.3 + 2.5 * math.sin(math.pi * min(t, 0.999) ** 0.55)) * width_scale * ARM_W / 2
        left.append((x + nx * w, y + ny * w))
        right.append((x - nx * w, y - ny * w))
    poly = left + right[::-1]
    return "M" + " L".join(f"{x:.2f},{y:.2f}" for x, y in poly) + " Z", pts[-1][:2]


def circle(cx, cy, r):
    return f"M{cx - r:g},{cy} a{r:g},{r:g} 0 1,0 {2 * r:g},0 a{r:g},{r:g} 0 1,0 {-2 * r:g},0"


def ellipse(cx, cy, rx, ry):
    return f"M{cx - rx:g},{cy} a{rx:g},{ry:g} 0 1,0 {2 * rx:g},0 a{rx:g},{ry:g} 0 1,0 {-2 * rx:g},0"


S = 0.8  # spark scale
SPARK = (
    f"M{CX},{CY - 9.5 * S:.2f} C{CX + 0.9 * S:.2f},{CY - 3.4 * S:.2f} {CX + 3.4 * S:.2f},{CY - 0.9 * S:.2f} {CX + 9.5 * S:.2f},{CY} "
    f"C{CX + 3.4 * S:.2f},{CY + 0.9 * S:.2f} {CX + 0.9 * S:.2f},{CY + 3.4 * S:.2f} {CX},{CY + 9.5 * S:.2f} "
    f"C{CX - 0.9 * S:.2f},{CY + 3.4 * S:.2f} {CX - 3.4 * S:.2f},{CY + 0.9 * S:.2f} {CX - 9.5 * S:.2f},{CY} "
    f"C{CX - 3.4 * S:.2f},{CY - 0.9 * S:.2f} {CX - 0.9 * S:.2f},{CY - 3.4 * S:.2f} {CX},{CY - 9.5 * S:.2f} Z"
)
# Faint meridians and a parallel, so the globe reads as a world.
GRID = [ellipse(CX, CY, GLOBE_R * 0.42, GLOBE_R), ellipse(CX, CY, GLOBE_R, GLOBE_R * 0.36)]
STARS = [(17, 22, 0.9, 0.8), (90, 20, 0.7, 0.6), (93, 74, 1.0, 0.7), (14, 84, 0.7, 0.5), (76, 95, 0.6, 0.6), (60, 10, 0.5, 0.5), (32, 95, 0.5, 0.45)]
PHASES = [-math.pi / 2, math.pi / 2]
SEGMENTS = 14


def vd_lin(x1, y1, x2, y2, stops):
    items = "".join(f'<item android:offset="{o}" android:color="{c}"/>' for o, c in stops)
    return f'<gradient android:type="linear" android:startX="{x1:.2f}" android:startY="{y1:.2f}" android:endX="{x2:.2f}" android:endY="{y2:.2f}">{items}</gradient>'


def vd_rad(cx, cy, r, stops):
    items = "".join(f'<item android:offset="{o}" android:color="{c}"/>' for o, c in stops)
    return f'<gradient android:type="radial" android:centerX="{cx}" android:centerY="{cy}" android:gradientRadius="{r}">{items}</gradient>'


def argb(hex_rgb, a):
    return f"#{int(a * 255):02X}{hex_rgb[1:]}"


def mix(a, b, f):
    pa = [int(a[i:i + 2], 16) for i in (1, 3, 5)]
    pb = [int(b[i:i + 2], 16) for i in (1, 3, 5)]
    return "#" + "".join(f"{round(x + (y - x) * f):02X}" for x, y in zip(pa, pb))


def palette(theme):
    """Icon colours from a theme; the arms, spark and sky use the same mapping as VOID Portal and Void Browse."""
    c = theme["colors"]
    light = theme["mode"] == "light"
    a, a2 = c["accent"].upper(), c["accent2"].upper()
    hot = mix("#FFFFFF", a2, 0.05)
    return {
        # Snout to tail tip: a lighter head, the deep accent under the globe, rising through the second accent to white-hot.
        "fox": [(0, mix(a, a2, 0.35)), (0.4, a), (0.72, mix(a, a2, 0.55)), (0.9, a2), (1, mix(hot, a2, 0.25))],
        "fox_glow": mix(a, a2, 0.4),
        "arms": [a2, mix(a, "#FFFFFF", 0.25)],
        "hot": hot,
        "bg": (mix(c["panelAlt"], a, 0.08), c["bg"].upper()) if light else (mix(c["bg"], a, 0.12), mix(c["bg"], "#000000", 0.35)),
        "nebula": (mix(c["bg"], a, 0.35), 0.45) if light else (mix(c["bg"], a, 0.45), 0.85),
        "star": (a, 0.45) if light else ("#FFFFFF", 1.0),
        "core": [(0, mix("#0B0820", a, 0.3)), (0.7, "#0B0820"), (1, "#05040C")],
        "rim": mix(a2, a, 0.3),
        "glow": a2,
        "spark_end": mix("#FFFFFF", a2, 0.35),
    }


def gradient_at(stops, t):
    for (t0, c0), (t1, c1) in zip(stops, stops[1:]):
        if t <= t1:
            return mix(c0, c1, (t - t0) / (t1 - t0))
    return stops[-1][1]


def fox_segments(P):
    """The fox in short overlapping pieces, coloured along its length (for the SVG, which has no sweep gradient)."""
    out = []
    for k in range(FOX_SEGMENTS):
        t0, t1 = k / FOX_SEGMENTS, min(1.0, (k + 1.4) / FOX_SEGMENTS)
        out.append((fox_path(t0, t1, n=6), gradient_at(P["fox"], (t0 + t1) / 2)))
    return out


def fox_sweep(P):
    """The fox's colours as sweep-gradient stops around the centre (0 = three o'clock, clockwise)."""
    th0, sweep = math.degrees(FOX_TH0), math.degrees(FOX_SWEEP)
    stops = []
    for i in range(51):
        t = i / 50
        stops.append((((th0 - t * sweep) % 360) / 360, gradient_at(P["fox"], t)))
    wrap = (th0 % 360) / sweep  # where the fox crosses three o'clock
    stops += [(0.0, gradient_at(P["fox"], wrap)), (1.0, gradient_at(P["fox"], wrap))]
    return sorted(set(stops))


def arm_segments(phase, color, hot):
    """The arm in short overlapping pieces, coloured along its length: white-hot core, arm colour, fading tail."""
    out = []
    for k in range(SEGMENTS):
        t0, t1 = k / SEGMENTS, min(1.0, (k + 1.35) / SEGMENTS)
        tm = (t0 + t1) / 2
        c = mix(hot, color, min(1.0, tm / 0.22))
        alpha = 1.0 if tm < 0.55 else 1.0 - 0.5 * (tm - 0.55) / 0.45
        d, _ = arm_points(phase, n=10, length=t1, t0=t0)
        out.append((d, c, alpha))
    return out


HEAD = ('<?xml version="1.0" encoding="utf-8"?>\n<vector xmlns:android="http://schemas.android.com/apk/res/android" '
        'xmlns:aapt="http://schemas.android.com/aapt" android:width="108dp" android:height="108dp" android:viewportWidth="108" android:viewportHeight="108">\n')


def opaque(stops):
    return [(o, argb(c, 1)) for o, c in stops]


def background_vd(P):
    out = HEAD
    top, bottom = P["bg"]
    neb, neb_a = P["nebula"]
    star, star_a = P["star"]
    out += f'  <path android:pathData="M0,0h108v108h-108z"><aapt:attr name="android:fillColor">{vd_lin(54, 0, 54, 108, [(0, argb(top, 1)), (1, argb(bottom, 1))])}</aapt:attr></path>\n'
    out += f'  <path android:pathData="M0,0h108v108h-108z"><aapt:attr name="android:fillColor">{vd_rad(54, 54, 50, [(0, argb(neb, neb_a)), (1, argb(neb, 0))])}</aapt:attr></path>\n'
    for cx, cy, r, a in STARS:
        out += f'  <path android:pathData="{circle(cx, cy, r)}" android:fillColor="{argb(star, 1)}" android:fillAlpha="{a * star_a:.2f}"/>\n'
    return out + "</vector>\n"


def foreground_vd(P):
    out = HEAD
    out += f'  <path android:pathData="{circle(CX, CY, GLOBE_R + 13)}"><aapt:attr name="android:fillColor">{vd_rad(CX, CY, GLOBE_R + 13, [(0.55, argb(P["fox_glow"], 0.32)), (1, argb(P["fox_glow"], 0))])}</aapt:attr></path>\n'
    out += f'  <path android:pathData="{circle(CX, CY, GLOBE_R)}"><aapt:attr name="android:fillColor">{vd_rad(CX, CY, GLOBE_R, opaque(P["core"]))}</aapt:attr></path>\n'
    for d in GRID:
        out += f'  <path android:pathData="{d}" android:strokeColor="{argb(P["rim"], 1)}" android:strokeAlpha="0.16" android:strokeWidth="0.7"/>\n'
    out += f'  <path android:pathData="{circle(CX, CY, GLOBE_R)}" android:strokeColor="{argb(P["rim"], 1)}" android:strokeAlpha="0.55" android:strokeWidth="1.1"/>\n'
    for phase, color in zip(PHASES, P["arms"]):
        for scale, alpha in [(4.2, 0.09), (2.4, 0.26)]:  # glow
            d, _ = arm_points(phase, width_scale=scale)
            out += f'  <path android:pathData="{d}" android:fillColor="{argb(color, alpha)}"/>\n'
        for d, c, a in arm_segments(phase, color, P["hot"]):
            out += f'  <path android:pathData="{d}" android:fillColor="{argb(c, a)}"/>\n'
    out += f'  <path android:pathData="{circle(CX, CY, 10)}"><aapt:attr name="android:fillColor">{vd_rad(CX, CY, 10, [(0, argb(P["glow"], 0.6)), (1, argb(P["glow"], 0))])}</aapt:attr></path>\n'
    out += f'  <path android:pathData="{SPARK}"><aapt:attr name="android:fillColor">{vd_rad(CX, CY, 9.5 * S, [(0, "#FFFFFFFF"), (1, argb(P["spark_end"], 1))])}</aapt:attr></path>\n'
    items = "".join(f'<item android:offset="{o:.4f}" android:color="{argb(c, 1)}"/>' for o, c in fox_sweep(P))
    out += f'  <path android:pathData="{fox_path()}"><aapt:attr name="android:fillColor"><gradient android:type="sweep" android:centerX="{CX}" android:centerY="{CY}">{items}</gradient></aapt:attr></path>\n'
    return out + "</vector>\n"


def mono_vd(color="#FF000000"):
    body = f'  <path android:pathData="{circle(CX, CY, GLOBE_R)}" android:strokeColor="{color}" android:strokeWidth="1.6"/>\n'
    for phase in PHASES:
        d, _ = arm_points(phase, width_scale=1.25)
        body += f'  <path android:pathData="{d}" android:fillColor="{color}"/>\n'
    body += f'  <path android:pathData="{SPARK}" android:fillColor="{color}"/>\n'
    body += f'  <path android:pathData="{fox_path()}" android:fillColor="{color}"/>\n'
    return HEAD + body + "</vector>\n"


def svg(P, size=432):
    top, bottom = P["bg"]
    neb, neb_a = P["nebula"]
    star, star_a = P["star"]
    stops = lambda st: "".join(f'<stop offset="{o}" stop-color="{c}"/>' for o, c in st)
    o = [f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 108 108" width="{size}" height="{size}">', "<defs>",
         f'<linearGradient id="bg" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="{top}"/><stop offset="1" stop-color="{bottom}"/></linearGradient>',
         f'<radialGradient id="neb" cx="54" cy="54" r="50" gradientUnits="userSpaceOnUse"><stop offset="0" stop-color="{neb}" stop-opacity="{neb_a}"/><stop offset="1" stop-color="{neb}" stop-opacity="0"/></radialGradient>',
         f'<radialGradient id="aura" cx="{CX}" cy="{CY}" r="{GLOBE_R + 13}" gradientUnits="userSpaceOnUse"><stop offset="0.55" stop-color="{P["fox_glow"]}" stop-opacity=".32"/><stop offset="1" stop-color="{P["fox_glow"]}" stop-opacity="0"/></radialGradient>',
         f'<radialGradient id="core" cx="{CX}" cy="{CY}" r="{GLOBE_R}" gradientUnits="userSpaceOnUse">{stops(P["core"])}</radialGradient>',
         f'<radialGradient id="glow" cx="{CX}" cy="{CY}" r="10" gradientUnits="userSpaceOnUse"><stop offset="0" stop-color="{P["glow"]}" stop-opacity=".6"/><stop offset="1" stop-color="{P["glow"]}" stop-opacity="0"/></radialGradient>',
         f'<radialGradient id="spark" cx="{CX}" cy="{CY}" r="{9.5 * S}" gradientUnits="userSpaceOnUse"><stop offset="0" stop-color="#fff"/><stop offset="1" stop-color="{P["spark_end"]}"/></radialGradient>',
         "</defs>"]
    o += ['<rect width="108" height="108" fill="url(#bg)"/>', '<rect width="108" height="108" fill="url(#neb)"/>']
    o += [f'<circle cx="{x}" cy="{y}" r="{r}" fill="{star}" opacity="{a * star_a:.2f}"/>' for x, y, r, a in STARS]
    o += [f'<circle cx="{CX}" cy="{CY}" r="{GLOBE_R + 13}" fill="url(#aura)"/>', f'<circle cx="{CX}" cy="{CY}" r="{GLOBE_R}" fill="url(#core)"/>']
    o += [f'<path d="{d}" fill="none" stroke="{P["rim"]}" stroke-opacity=".16" stroke-width=".7"/>' for d in GRID]
    o += [f'<circle cx="{CX}" cy="{CY}" r="{GLOBE_R}" fill="none" stroke="{P["rim"]}" stroke-opacity=".55" stroke-width="1.1"/>']
    for phase, color in zip(PHASES, P["arms"]):
        for scale, alpha in [(4.2, 0.09), (2.4, 0.26)]:
            o.append(f'<path d="{arm_points(phase, width_scale=scale)[0]}" fill="{color}" fill-opacity="{alpha}"/>')
        o += [f'<path d="{d}" fill="{c}" fill-opacity="{a:.2f}"/>' for d, c, a in arm_segments(phase, color, P["hot"])]
    o += [f'<circle cx="{CX}" cy="{CY}" r="10" fill="url(#glow)"/>', f'<path d="{SPARK}" fill="url(#spark)"/>']
    o += [f'<path d="{d}" fill="{c}"/>' for d, c in fox_segments(P)]
    o.append("</svg>")
    return "\n".join(o) + "\n"


def alias_block(themes):
    """One launcher entry per theme; the app enables the one matching its theme (see Themes.kt)."""
    out = ["        <!-- ICON ALIASES (generated by art/gen_icon.py) -->"]
    for i, t in enumerate(themes):
        key = t["id"].replace("-", "_")
        out += [
            "        <activity-alias",
            f'            android:name=".Icon_{key}"',
            f'            android:enabled="{"true" if i == 0 else "false"}"',
            '            android:exported="true"',
            f'            android:icon="@mipmap/ic_launcher_{key}"',
            f'            android:roundIcon="@mipmap/ic_launcher_{key}"',
            '            android:targetActivity=".MainActivity">',
            "            <intent-filter>",
            '                <action android:name="android.intent.action.MAIN" />',
            '                <category android:name="android.intent.category.LAUNCHER" />',
            "            </intent-filter>",
            "        </activity-alias>",
        ]
    out.append("        <!-- END ICON ALIASES -->")
    return "\n".join(out)


def adaptive(key):
    return ('<?xml version="1.0" encoding="utf-8"?>\n<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">\n'
            f'    <background android:drawable="@drawable/ic_launcher_background{key}"/>\n'
            f'    <foreground android:drawable="@drawable/ic_launcher_foreground{key}"/>\n'
            '    <monochrome android:drawable="@drawable/ic_launcher_monochrome"/>\n</adaptive-icon>\n')


if __name__ == "__main__":
    themes = json.load(open("art/themes.json"))
    res = "app/src/main/res/"
    os.makedirs("art/themes", exist_ok=True)
    for i, t in enumerate(themes):
        P = palette(t)
        key = t["id"].replace("-", "_")
        open(f"art/themes/{key}.svg", "w").write(svg(P))
        open(f"{res}drawable/ic_launcher_background_{key}.xml", "w").write(background_vd(P))
        open(f"{res}drawable/ic_launcher_foreground_{key}.xml", "w").write(foreground_vd(P))
        open(f"{res}mipmap-anydpi-v26/ic_launcher_{key}.xml", "w").write(adaptive(f"_{key}"))
        if i == 0:  # the first (default) theme is also the app's own icon
            open("art/web-icon.svg", "w").write(svg(P))
            open(res + "drawable/ic_launcher_background.xml", "w").write(background_vd(P))
            open(res + "drawable/ic_launcher_foreground.xml", "w").write(foreground_vd(P))
            open(res + "mipmap-anydpi-v26/ic_launcher.xml", "w").write(adaptive(""))
            open(res + "mipmap-anydpi-v26/ic_launcher_round.xml", "w").write(adaptive(""))
    open(res + "drawable/ic_launcher_monochrome.xml", "w").write(mono_vd())
    manifest = "app/src/main/AndroidManifest.xml"
    if os.path.exists(manifest):
        m = open(manifest).read()
        m = re.sub(r"        <!-- ICON ALIASES.*?<!-- END ICON ALIASES -->", lambda _: alias_block(themes), m, flags=re.S)
        open(manifest, "w").write(m)
    print(f"logo written for {len(themes)} themes")
