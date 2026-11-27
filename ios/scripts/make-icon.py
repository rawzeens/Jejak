#!/usr/bin/env python3
"""Draws the app icon (the same picture as the Android launcher icon): a winding trail from an orange stop to a pin.
Usage: python3 scripts/make-icon.py Jejak/Assets.xcassets/AppIcon.appiconset/icon-1024.png"""
import sys
from PIL import Image, ImageDraw

S = 2048                      # drawn twice as big, then shrunk for smooth edges
K = S / 108 * 1.3             # the Android drawing is 108 units wide; make it fill more of the square
def p(x, y):
    return ((x - 54) * K + S / 2, (y - 54) * K + S / 2)

def bezier(a, b, c, d, n=80):
    out = []
    for i in range(n + 1):
        t = i / n
        u = 1 - t
        out.append(tuple(u**3 * a[k] + 3 * u * u * t * b[k] + 3 * u * t * t * c[k] + t**3 * d[k] for k in range(2)))
    return out

img = Image.new("RGB", (S, S), (29, 78, 216))
d = ImageDraw.Draw(img)
trail = bezier(p(38, 70), p(30, 60), p(44, 54), p(54, 54)) + bezier(p(54, 54), p(64, 54), p(78, 48), p(70, 38))[1:]
w = int(5 * K)
d.line(trail, fill="white", width=w, joint="curve")
for x, y in (trail[0], trail[-1]):
    d.ellipse([x - w / 2, y - w / 2, x + w / 2, y + w / 2], fill="white")
cx, cy = p(38, 70)
r = 8 * K
d.ellipse([cx - r, cy - r, cx + r, cy + r], fill=(249, 115, 22))
# pin: a circle with a point under it
px, py = p(70, 36)
r = 12 * K
d.ellipse([px - r, py - r, px + r, py + r], fill="white")
d.polygon([p(59.6, 42), p(80.4, 42), p(70, 56)], fill="white")
r = 4.5 * K
d.ellipse([px - r, py - r, px + r, py + r], fill=(29, 78, 216))
img.resize((1024, 1024), Image.LANCZOS).save(sys.argv[1], optimize=True)
