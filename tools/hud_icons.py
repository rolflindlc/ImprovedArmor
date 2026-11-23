"""Generates src/main/resources/assets/improvedarmor/textures/gui/hud_icons.png (64x64).

Layout (must match ArmorHud):
  y=0,  9x9:  0 amulet body (gray, tinted)   9 amulet rim + chain (gold)
              18 bottle liquid (gray, tinted) 27 bottle glass + cork
              36 book cover (gray, tinted)    45 book pages
  y=9,  9x9:  0..54 seven glint frames for the book (moving shine)
  y=18, 8x8:  0 fire  8 explosion  16 projectile  24 fall  32 magic  40 fire school  48 ice  56 lightning
  y=26, 8x8:  0 holy  8 ender  16 blood  24 evocation  32 nature  40 eldritch

Tinted layers are grayscale: the HUD multiplies them by the source color. Edit the PNG freely; this script only
gives the starting art.
"""
from PIL import Image
import sys

img = Image.new("RGBA", (64, 64), (0, 0, 0, 0))


def put(x0, y0, rows, palette):
    for y, row in enumerate(rows):
        for x, ch in enumerate(row):
            if ch in palette:
                img.putpixel((x0 + x, y0 + y), palette[ch])


# ---------------------------------------------------------------- amulet
AMULET = [
    ".o.....o.",
    "..o...o..",
    "...o.o...",
    "...OOO...",
    "..O+##O..",
    ".O+###-O.",
    ".O####-O.",
    "..O#--O..",
    "...OOO...",
]
put(0, 0, AMULET, {"+": (255, 255, 255, 255), "#": (200, 200, 200, 255), "-": (135, 135, 135, 255)})
put(9, 0, AMULET, {"o": (214, 178, 74, 255), "O": (240, 200, 60, 255)})

# ---------------------------------------------------------------- bottle
BOTTLE = [
    "...CCC...",
    "...GWG...",
    "...G.G...",
    "..G...G..",
    ".GWLLLLG.",
    ".GLLLLLG.",
    ".GLLLLLG.",
    ".GLLLLLG.",
    "..GGGGG..",
]
liquid = [r.replace("W", "L") if i == 4 else r for i, r in enumerate(BOTTLE)]
put(18, 0, liquid, {"L": (225, 225, 225, 255)})
put(27, 0, BOTTLE, {"C": (139, 94, 52, 255), "G": (205, 225, 235, 255), "W": (255, 255, 255, 255)})

# ---------------------------------------------------------------- book
BOOK = [
    "CCCCCCC..",
    "C#####CP.",
    "C##+##CP.",
    "C#+++#CP.",
    "C##+##CP.",
    "C#####CP.",
    "C#####CP.",
    "CCCCCCCP.",
    ".PPPPPPP.",
]
put(36, 0, BOOK, {"C": (120, 120, 120, 255), "#": (205, 205, 205, 255), "+": (255, 255, 255, 255)})
put(45, 0, BOOK, {"P": (238, 228, 196, 255)})

# Glint: a diagonal band crossing the cover, masked to the cover pixels.
cover = {(x, y) for y, r in enumerate(BOOK) for x, ch in enumerate(r) if ch in "C#+"}
for f in range(7):
    center = -2 + f * 3  # x + y of the band center
    for (x, y) in cover:
        d = abs((x + y) - center)
        if d == 0:
            img.putpixel((f * 9 + x, 9 + y), (255, 225, 255, 190))
        elif d == 1:
            img.putpixel((f * 9 + x, 9 + y), (225, 170, 255, 110))

# ---------------------------------------------------------------- plain damage-type icons (vanilla protections)
R, Y, W, O = (220, 60, 20, 255), (255, 200, 40, 255), (255, 250, 220, 255), (240, 120, 30, 255)
put(0, 18, [
    "...R....",
    "..RR..R.",
    "..RYR.R.",
    ".RRYRRR.",
    ".RYYYRR.",
    "RRYWYYRR",
    "RYYWWYYR",
    ".RRYYRR.",
], {"R": R, "Y": Y, "W": W})
put(8, 18, [
    "...O....",
    ".O.Y..O.",
    "..OYYO..",
    "OYYWWYYO",
    ".OYWWYO.",
    "..OYYO..",
    ".O..Y.O.",
    "....O...",
], {"O": O, "Y": Y, "W": W})
put(16, 18, [
    ".....HHH",
    "......HH",
    ".....S.H",
    "....S...",
    "...S....",
    "F.S.....",
    "FFS.....",
    "FFF.....",
], {"H": (200, 200, 210, 255), "S": (150, 105, 60, 255), "F": (245, 245, 245, 255)})
put(24, 18, [
    "......WW",
    ".....WWW",
    "....WWWG",
    "...WWWG.",
    "..WWWG..",
    "..WWG...",
    ".GG.....",
    "G.......",
], {"W": (250, 250, 250, 255), "G": (170, 170, 175, 255)})
put(32, 18, [
    "...P....",
    "...P....",
    "..PLP...",
    "PPLWLPP.",
    "..PLP...",
    "...P..p.",
    "...P.pLp",
    "......p.",
], {"P": (170, 90, 230, 255), "L": (215, 160, 255, 255), "W": (255, 255, 255, 255), "p": (150, 80, 210, 255)})

# ---------------------------------------------------------------- Iron's schools: colored orb + white glyph
ORB = [
    "..KKKK..",
    ".KccccK.",
    "KccccccK",
    "KccccccK",
    "KccccccK",
    "KccccccK",
    ".KccccK.",
    "..KKKK..",
]
GLYPHS = {
    "fire": ["........", "....W...", "...WW...", "..WWW...", "..WWWW..", "...WW...", "........", "........"],
    "ice": ["........", "..W..W..", "...WW...", "..WWWW..", "...WW...", "..W..W..", "........", "........"],
    "lightning": ["........", "....W...", "...W....", "..WWWW..", "....W...", "...W....", "........", "........"],
    "holy": ["........", "...WW...", "..WWWW..", "...WW...", "...WW...", "...WW...", "........", "........"],
    "ender": ["........", "........", "..WWWW..", ".WW..WW.", "..WWWW..", "........", "........", "........"],
    "blood": ["........", "...W....", "...WW...", "..WWWW..", "..WWWW..", "...WW...", "........", "........"],
    "evocation": ["........", "..W..W..", "..W..W..", "..WWWW..", "...WW...", "...WW...", "........", "........"],
    "nature": ["........", ".....WW.", "...WWWW.", "..WWWW..", "..WWW...", ".W......", "........", "........"],
    "eldritch": ["........", "........", "..WWWW..", ".WW.DWW.", "..WWWW..", "........", "........", "........"],
}
SCHOOLS = [
    ("fire", (242, 95, 28), 40, 18), ("ice", (120, 200, 245), 48, 18), ("lightning", (80, 170, 255), 56, 18),
    ("holy", (235, 200, 60), 0, 26), ("ender", (150, 60, 200), 8, 26), ("blood", (165, 20, 20), 16, 26),
    ("evocation", (170, 170, 150), 24, 26), ("nature", (90, 175, 55), 32, 26), ("eldritch", (40, 140, 135), 40, 26),
]
for name, (r, g, b), x, y in SCHOOLS:
    dark = (int(r * .45), int(g * .45), int(b * .45), 255)
    put(x, y, ORB, {"K": dark, "c": (r, g, b, 255)})
    put(x, y, GLYPHS[name], {"W": (255, 255, 255, 255), "D": dark})

out = sys.argv[1] if len(sys.argv) > 1 else "src/main/resources/assets/improvedarmor/textures/gui/hud_icons.png"
img.save(out)
print("saved", out)
