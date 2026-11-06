#!/usr/bin/env python3
"""
IPTV Native — legacy launcher icon generator.

Android 8.0+ (API 26) uses the adaptive icon defined in res/mipmap-anydpi-v26.
Older devices (API 24-25) and some third-party launchers still request a bitmap
from res/mipmap-<density>, so this script renders the same brand mark to PNG using
only the Python standard library (no Pillow/ImageMagick required).

Run from the repository root:
    python3 tools/generate_icons.py
"""

import math
import os
import struct
import zlib

SIZES = {
    "mdpi": 48,
    "hdpi": 72,
    "xhdpi": 96,
    "xxhdpi": 144,
    "xxxhdpi": 192,
}

OUT_DIR = os.path.join("app", "src", "main", "res")

# Brand palette
BG_TOP = (0x22, 0x27, 0x39)
BG_BOTTOM = (0x06, 0x07, 0x0A)
CORAL_START = (0xFF, 0x8A, 0x65)
CORAL_MID = (0xFF, 0x65, 0x52)
CORAL_END = (0xD4, 0x2F, 0x20)


def lerp(a, b, t):
    return a + (b - a) * t


def mix(c1, c2, t):
    return tuple(lerp(c1[i], c2[i], t) for i in range(3))


def gradient3(c1, c2, c3, t):
    if t < 0.5:
        return mix(c1, c2, t * 2.0)
    return mix(c2, c3, (t - 0.5) * 2.0)


def clamp01(v):
    return 0.0 if v < 0.0 else (1.0 if v > 1.0 else v)


def coverage(dist, px):
    """Analytic anti-aliasing for a signed distance (negative = inside)."""
    return clamp01(0.5 - dist / px)


def sd_rounded_box(px_, py_, cx, cy, half, radius):
    qx = abs(px_ - cx) - (half - radius)
    qy = abs(py_ - cy) - (half - radius)
    ax = max(qx, 0.0)
    ay = max(qy, 0.0)
    return math.hypot(ax, ay) + min(max(qx, qy), 0.0) - radius


def sd_convex_polygon(px_, py_, points):
    """Signed distance for a clockwise (screen-space) convex polygon."""
    d = -1e9
    for i in range(len(points)):
        ax, ay = points[i]
        bx, by = points[(i + 1) % len(points)]
        ex, ey = bx - ax, by - ay
        # outward normal for a clockwise polygon in y-down screen coordinates
        nx, ny = ey, -ex
        length = math.hypot(nx, ny)
        nx, ny = nx / length, ny / length
        dist = nx * (px_ - ax) + ny * (py_ - ay)
        d = max(d, dist)
    return d


def render(size, round_icon):
    px = 1.0  # one pixel in icon units
    scale = 0.98

    # Geometry in a 108x108 space, matching the vector drawables.
    def S(v):
        return v * (size / 108.0)

    cx = cy = S(54.0)
    circle_r = S(30.0)
    tri = [
        (S(45.0) + S(1.0), S(38.5)),
        (S(72.0) - S(1.5), S(54.0)),
        (S(45.0) + S(1.0), S(69.5)),
    ]
    box_half = S(54.0 * scale)
    box_radius = S(26.0) if not round_icon else box_half

    rows = []
    for y in range(size):
        row = bytearray()
        py_ = y + 0.5
        for x in range(size):
            px_ = x + 0.5

            # --- background plate ---
            if round_icon:
                bg_d = math.hypot(px_ - cx, py_ - cy) - (box_half - S(1.0))
            else:
                bg_d = sd_rounded_box(px_, py_, cx, cy, box_half, box_radius)
            bg_cov = coverage(bg_d, px)
            if bg_cov <= 0.0:
                row += bytes((0, 0, 0, 0))
                continue

            t = clamp01((px_ / size * 0.35) + (py_ / size * 0.65))
            color = gradient3(BG_TOP, (0x12, 0x14, 0x1C), BG_BOTTOM, t)

            # subtle coral glow behind the mark
            glow = math.hypot(px_ - cx, py_ - (cy + S(4.0)))
            glow_t = clamp01(1.0 - glow / (S(58.0)))
            if glow_t > 0:
                color = mix(color, CORAL_MID, 0.16 * glow_t * glow_t)

            # --- disc ---
            disc_cov = coverage(math.hypot(px_ - cx, py_ - cy) - circle_r, px)
            if disc_cov > 0:
                gt = clamp01(((px_ - S(30.0)) * 0.35 + (py_ - S(26.0)) * 0.65) / S(58.0))
                disc = gradient3(CORAL_START, CORAL_MID, CORAL_END, gt)
                color = mix(color, disc, disc_cov)

                # rim highlight
                rim = abs(math.hypot(px_ - cx, py_ - cy) - (circle_r - S(1.2)))
                rim_cov = coverage(rim - S(0.9), px)
                if rim_cov > 0:
                    color = mix(color, (255, 255, 255), 0.18 * rim_cov * disc_cov)

            # --- play glyph ---
            tri_d = sd_convex_polygon(px_, py_, tri)
            tri_cov = coverage(tri_d, px)
            if tri_cov > 0:
                color = mix(color, (255, 255, 255), tri_cov)

            row += bytes((int(color[0]), int(color[1]), int(color[2]), int(255 * bg_cov)))
        rows.append(bytes(row))
    return rows


def write_png(path, rows, size):
    raw = b"".join(b"\x00" + r for r in rows)

    def chunk(tag, data):
        body = tag + data
        return struct.pack(">I", len(data)) + body + struct.pack(">I", zlib.crc32(body) & 0xFFFFFFFF)

    header = struct.pack(">IIBBBBB", size, size, 8, 6, 0, 0, 0)
    png = (
        b"\x89PNG\r\n\x1a\n"
        + chunk(b"IHDR", header)
        + chunk(b"IDAT", zlib.compress(raw, 9))
        + chunk(b"IEND", b"")
    )
    with open(path, "wb") as handle:
        handle.write(png)


def main():
    for density, size in SIZES.items():
        folder = os.path.join(OUT_DIR, f"mipmap-{density}")
        os.makedirs(folder, exist_ok=True)
        for name, is_round in (("ic_launcher.png", False), ("ic_launcher_round.png", True)):
            rows = render(size, is_round)
            write_png(os.path.join(folder, name), rows, size)
            print(f"wrote {folder}/{name} ({size}x{size})")


if __name__ == "__main__":
    main()
