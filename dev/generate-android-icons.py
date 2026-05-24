from PIL import Image, ImageDraw, ImageFilter
from pathlib import Path

SIZE = 1024
REPO_DIR = Path(__file__).resolve().parent.parent
RES_DIR = REPO_DIR / "app" / "src" / "main" / "res"
MIPMAP_DIR = RES_DIR / "mipmap-xxxhdpi"
MIPMAP_DIR.mkdir(parents=True, exist_ok=True)

OUT_BG = MIPMAP_DIR / "ic_launcher_background.png"
OUT_FG = MIPMAP_DIR / "ic_launcher_foreground.png"
OUT_LEGACY = RES_DIR / "mipmap-xxxhdpi" / "ic_launcher_legacy.png" # we can also generate a legacy one if needed

def lerp(a, b, t):
    return tuple(int(a[i] + (b[i] - a[i]) * t) for i in range(len(a)))

def vertical_gradient(w, h, top, bottom):
    img = Image.new("RGB", (w, h))
    px = img.load()
    for y in range(h):
        c = lerp(top, bottom, y / max(h - 1, 1))
        for x in range(w):
            px[x, y] = c
    return img

def radial_glow(w, h, cx, cy, radius, color, max_alpha):
    glow = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    px = glow.load()
    r2 = radius * radius
    for y in range(h):
        dy = y - cy
        for x in range(w):
            dx = x - cx
            d2 = dx * dx + dy * dy
            if d2 < r2:
                t = 1 - (d2 / r2) ** 0.5
                a = int(max_alpha * (t ** 2.2))
                if a:
                    px[x, y] = (*color, a)
    return glow

def main():
    s = SIZE
    
    # 1. Generate Background Layer (Gradient + Radial Glow)
    bg = vertical_gradient(s, s, (24, 30, 86), (12, 14, 44)).convert("RGBA")
    glow_layer = radial_glow(s, s, int(s * 0.62), int(s * 0.38), int(s * 0.55), (90, 180, 255), 110)
    bg.alpha_composite(glow_layer)
    
    # Save background at 512x512
    bg_512 = bg.resize((512, 512), Image.Resampling.LANCZOS)
    bg_512.save(OUT_BG, "PNG")
    print(f"Wrote background to {OUT_BG}")
    
    # 2. Generate Foreground Layer (Lighthouse, waves, beam, glow - on transparent background)
    fg = Image.new("RGBA", (s, s), (0, 0, 0, 0))
    draw = ImageDraw.Draw(fg, "RGBA")
    
    cx = s // 2
    sea_y = int(s * 0.78)
    
    # Draw Sea
    draw.rectangle((0, sea_y, s, s), fill=(10, 20, 60, 255))
    for i in range(6):
        y = sea_y + int(i * s * 0.012)
        draw.ellipse((-s, y, s * 2, y + int(s * 0.05)), outline=(80, 130, 220, 70), width=2)
        
    # Draw Beam
    beam_top = (cx + int(s * 0.02), int(s * 0.30))
    beam_l = (s + int(s * 0.15), int(s * 0.05))
    beam_r = (s + int(s * 0.15), int(s * 0.58))
    beam = Image.new("RGBA", (s, s), (0, 0, 0, 0))
    bd = ImageDraw.Draw(beam, "RGBA")
    bd.polygon([beam_top, beam_l, beam_r], fill=(140, 220, 255, 90))
    beam = beam.filter(ImageFilter.GaussianBlur(8))
    bd2 = ImageDraw.Draw(beam, "RGBA")
    bd2.polygon([beam_top, beam_l, beam_r], fill=(180, 235, 255, 60))
    fg.alpha_composite(beam)
    
    # Draw Tower Base
    base_w = int(s * 0.46)
    base_x0 = cx - base_w // 2
    base_x1 = cx + base_w // 2
    base_y0 = int(s * 0.74)
    base_y1 = int(s * 0.82)
    draw.rounded_rectangle((base_x0, base_y0, base_x1, base_y1), radius=int(s * 0.012),
                           fill=(235, 240, 255, 240))
                           
    # Draw Tower body
    tower_top_w = int(s * 0.16)
    tower_bot_w = int(s * 0.30)
    tower_y0 = int(s * 0.40)
    tower_y1 = base_y0
    tt_l = cx - tower_top_w // 2
    tt_r = cx + tower_top_w // 2
    tb_l = cx - tower_bot_w // 2
    tb_r = cx + tower_bot_w // 2
    draw.polygon([(tt_l, tower_y0), (tt_r, tower_y0), (tb_r, tower_y1), (tb_l, tower_y1)],
                 fill=(245, 248, 255, 245))
                 
    # Draw Stripes
    stripe_h = int(s * 0.045)
    for i in range(3):
        sy = tower_y0 + int((i + 1) * (tower_y1 - tower_y0) / 4)
        t = (sy - tower_y0) / (tower_y1 - tower_y0)
        l = int(tt_l + (tb_l - tt_l) * t)
        r = int(tt_r + (tb_r - tt_r) * t)
        draw.rectangle((l + 8, sy - stripe_h // 2, r - 8, sy + stripe_h // 2),
                       fill=(60, 130, 230, 230))
                       
    # Draw Lantern room
    lantern_w = int(s * 0.22)
    lantern_h = int(s * 0.10)
    lan_x0 = cx - lantern_w // 2
    lan_x1 = cx + lantern_w // 2
    lan_y0 = tower_y0 - lantern_h
    lan_y1 = tower_y0
    draw.rounded_rectangle((lan_x0, lan_y0, lan_x1, lan_y1), radius=int(s * 0.018),
                           fill=(20, 28, 70, 255))
                           
    # Draw Light bulb
    bulb_r = int(s * 0.045)
    draw.ellipse((cx - bulb_r, lan_y0 + lantern_h // 2 - bulb_r,
                  cx + bulb_r, lan_y0 + lantern_h // 2 + bulb_r),
                 fill=(180, 240, 255, 255))
                 
    # Draw Light glow
    bulb_glow = Image.new("RGBA", (s, s), (0, 0, 0, 0))
    bgd = ImageDraw.Draw(bulb_glow, "RGBA")
    bgd.ellipse((cx - bulb_r * 3, lan_y0 + lantern_h // 2 - bulb_r * 3,
                 cx + bulb_r * 3, lan_y0 + lantern_h // 2 + bulb_r * 3),
                fill=(150, 220, 255, 110))
    bulb_glow = bulb_glow.filter(ImageFilter.GaussianBlur(20))
    fg.alpha_composite(bulb_glow)
    
    # Draw Roof
    roof_half = int(s * 0.14)
    roof_h = int(s * 0.08)
    draw.polygon([(cx - roof_half, lan_y0), (cx + roof_half, lan_y0),
                  (cx, lan_y0 - roof_h)],
                 fill=(60, 130, 230, 255))
    draw.ellipse((cx - int(s * 0.012), lan_y0 - roof_h - int(s * 0.022),
                  cx + int(s * 0.012), lan_y0 - roof_h),
                 fill=(245, 248, 255, 255))
                 
    # Draw Gloss
    gloss = Image.new("RGBA", (s, s), (0, 0, 0, 0))
    gd = ImageDraw.Draw(gloss, "RGBA")
    gd.ellipse((-int(s * 0.2), -int(s * 0.5), int(s * 0.8), int(s * 0.45)),
               fill=(255, 255, 255, 32))
    gloss = gloss.filter(ImageFilter.GaussianBlur(40))
    fg.alpha_composite(gloss)
    
    # For Android adaptive icons, we must scale down the foreground content and center it
    # so it does not get clipped by the adaptive mask. We use a 65% scale factor.
    scale_factor = 0.65
    scaled_w = int(s * scale_factor)
    scaled_h = int(s * scale_factor)
    fg_scaled_content = fg.resize((scaled_w, scaled_h), Image.Resampling.LANCZOS)
    
    fg_final = Image.new("RGBA", (s, s), (0, 0, 0, 0))
    offset_x = (s - scaled_w) // 2
    offset_y = (s - scaled_h) // 2
    fg_final.paste(fg_scaled_content, (offset_x, offset_y), fg_scaled_content)
    
    # Save foreground at 512x512
    fg_512 = fg_final.resize((512, 512), Image.Resampling.LANCZOS)
    fg_512.save(OUT_FG, "PNG")
    print(f"Wrote foreground to {OUT_FG}")

if __name__ == "__main__":
    main()
