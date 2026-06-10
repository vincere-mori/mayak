"""Frames raw desktop screenshots into branded Mayak cards for README/store."""
from pathlib import Path
from PIL import Image, ImageDraw, ImageFilter, ImageFont

ROOT = Path(__file__).resolve().parent.parent
ASSETS = ROOT / ".assets"
ICON = ROOT / "desktop" / "src" / "main" / "resources" / "icon.png"

OS_BAR = 30          # height of the windows title bar to crop away
TITLE_H = 46         # our custom title bar
RADIUS = 16
PAD = 48             # space around the card for the shadow
BAR_BG = (12, 19, 46)
TEXT = (233, 238, 252)

def font(size, bold=True):
    name = "segoeuib.ttf" if bold else "segoeui.ttf"
    try:
        return ImageFont.truetype(f"C:/Windows/Fonts/{name}", size)
    except OSError:
        return ImageFont.load_default()

def rounded_mask(size, radius):
    m = Image.new("L", size, 0)
    ImageDraw.Draw(m).rounded_rectangle([0, 0, size[0] - 1, size[1] - 1], radius, fill=255)
    return m

def frame(src: Path, dst: Path):
    shot = Image.open(src).convert("RGBA")
    w, h = shot.size
    content = shot.crop((0, OS_BAR, w, h))
    cw, ch = content.size

    card = Image.new("RGBA", (cw, TITLE_H + ch), BAR_BG)
    d = ImageDraw.Draw(card)
    d.rectangle([0, 0, cw, TITLE_H], fill=BAR_BG)

    logo = Image.open(ICON).convert("RGBA").resize((28, 28), Image.LANCZOS)
    card.alpha_composite(logo, (18, (TITLE_H - 28) // 2))
    f = font(19)
    d.text((56, TITLE_H // 2), "Маяк", font=f, fill=TEXT, anchor="lm")
    card.alpha_composite(content, (0, TITLE_H))

    card.putalpha(rounded_mask(card.size, RADIUS))

    canvas = Image.new("RGBA", (cw + PAD * 2, card.height + PAD * 2), (0, 0, 0, 0))
    shadow = Image.new("RGBA", canvas.size, (0, 0, 0, 0))
    ImageDraw.Draw(shadow).rounded_rectangle(
        [PAD, PAD + 8, PAD + cw, PAD + card.height + 8], RADIUS, fill=(0, 0, 0, 150)
    )
    shadow = shadow.filter(ImageFilter.GaussianBlur(22))
    canvas.alpha_composite(shadow)
    canvas.alpha_composite(card, (PAD, PAD))
    canvas.save(dst)
    print(f"wrote {dst} {canvas.size}")

frame(ASSETS / "screenshot-off.png", ASSETS / "desktop-off.png")
frame(ASSETS / "screenshot-on.png", ASSETS / "desktop-on.png")
