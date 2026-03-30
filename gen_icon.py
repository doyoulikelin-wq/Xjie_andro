"""Generate XJ+ app icon matching brand colors."""
from PIL import Image, ImageDraw
import math

size = 1024
img = Image.new("RGBA", (size, size), (255, 255, 255, 255))
draw = ImageDraw.Draw(img)

# Brand colors from logo
TEAL = (0, 201, 167)       # #00C9A7
MID_BLUE = (27, 150, 201)  # #1B96C9
DEEP_BLUE = (21, 101, 192) # #1565C0
NAVY = (15, 76, 129)       # #0F4C81


def lerp_color(c1, c2, t):
    t = max(0.0, min(1.0, t))
    return tuple(int(c1[i] + (c2[i] - c1[i]) * t) for i in range(3))


def draw_thick_line(x0, y0, x1, y1, w, steps, color_start, color_end):
    for i in range(steps):
        t = i / max(steps - 1, 1)
        x = x0 + (x1 - x0) * t
        y = y0 + (y1 - y0) * t
        c = lerp_color(color_start, color_end, t)
        r = w // 2
        draw.ellipse([x - r, y - r, x + r, y + r], fill=(*c, 255))


# Draw X (two crossing strokes)
sw = 95  # stroke width
# X left arm: top-left to bottom-center
draw_thick_line(170, 240, 430, 780, sw, 300, TEAL, MID_BLUE)
# X right arm: top-right to bottom-center (overlap creates X)
draw_thick_line(500, 240, 240, 780, sw, 300, MID_BLUE, DEEP_BLUE)

# J: vertical bar
draw_thick_line(670, 240, 670, 680, sw, 250, MID_BLUE, DEEP_BLUE)

# J: bottom curve
for i in range(150):
    t = i / 149
    angle = -math.pi * 0.5 + math.pi * 0.6 * t
    cx, cy, radius = 580, 680, 90
    x = cx + radius * math.cos(angle)
    y = cy + radius * math.sin(angle) + 68
    c = lerp_color(DEEP_BLUE, NAVY, t)
    r = sw // 2
    draw.ellipse([x - r, y - r, x + r, y + r], fill=(*c, 255))

# + symbol (upper-right, teal)
pcx, pcy = 740, 250
ps = 38  # plus arm length
pw = 18  # plus arm width
draw.rectangle([pcx - pw // 2, pcy - ps, pcx + pw // 2, pcy + ps], fill=(*TEAL, 255))
draw.rectangle([pcx - ps, pcy - pw // 2, pcx + ps, pcy + pw // 2], fill=(*TEAL, 255))

output = "Xjie/Xjie/Assets.xcassets/AppIcon.appiconset/AppIcon.png"
img.save(output, "PNG")
print(f"Generated {output}  ({size}x{size})")
