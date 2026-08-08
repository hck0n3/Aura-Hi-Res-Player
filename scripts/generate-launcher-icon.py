"""Generate Android launcher icons from the owner-supplied Aura A+play design."""
from __future__ import annotations

from pathlib import Path

from PIL import Image, ImageDraw, ImageFilter

SRC = Path(
    r"C:\Users\Jesus Ordoñez\.cursor\projects\d-7-8-26-AURA-HI-RES\assets"
    r"\c__Users_Jesus_Ordo_ez_AppData_Roaming_Cursor_User_workspaceStorage_"
    r"7d259c9d32b5d64f309d0fe35f8abb41_images_1785993497301-256a8037-905c-4a3a-976b-09bcc4db45ce.png"
)
OUT_ROOT = Path(r"d:\7-8-26\AURA HI-RES\app\src\main")
RES = OUT_ROOT / "res"
BASE = (0x1C, 0x1F, 0x22, 255)  # #1C1F22


def is_canvas(p: tuple[int, int, int, int]) -> bool:
    r, g, b, a = p
    if a < 10:
        return True
    brightness = (r + g + b) / 3
    return brightness > 180 and abs(r - g) < 30 and abs(g - b) < 30


def crop_icon(img: Image.Image) -> Image.Image:
    w, h = img.size
    px = img.load()
    step_x = max(1, w // 200)
    step_y = max(1, h // 200)
    xs = [x for x in range(0, w, step_x) for y in range(0, h, step_y) if not is_canvas(px[x, y])]
    ys = [y for x in range(0, w, step_x) for y in range(0, h, step_y) if not is_canvas(px[x, y])]
    min_x, max_x = max(0, min(xs) - 2), min(w - 1, max(xs) + 2)
    min_y, max_y = max(0, min(ys) - 2), min(h - 1, max(ys) + 2)
    min_x2, min_y2, max_x2, max_y2 = w, h, 0, 0
    for y in range(min_y, max_y + 1):
        for x in range(min_x, max_x + 1):
            if not is_canvas(px[x, y]):
                min_x2 = min(min_x2, x)
                min_y2 = min(min_y2, y)
                max_x2 = max(max_x2, x)
                max_y2 = max(max_y2, y)
    cropped = img.crop((min_x2, min_y2, max_x2 + 1, max_y2 + 1)).convert("RGBA")
    side = max(cropped.size)
    sq = Image.new("RGBA", (side, side), BASE)
    sq.paste(cropped, ((side - cropped.size[0]) // 2, (side - cropped.size[1]) // 2), cropped)
    return sq


def resize(im: Image.Image, size: int) -> Image.Image:
    return im.resize((size, size), Image.Resampling.LANCZOS)


def apply_round(im: Image.Image, radius_ratio: float = 0.22) -> Image.Image:
    out = im.copy()
    m = Image.new("L", im.size, 0)
    d = ImageDraw.Draw(m)
    r = int(im.size[0] * radius_ratio)
    d.rounded_rectangle((0, 0, im.size[0] - 1, im.size[1] - 1), radius=r, fill=255)
    out.putalpha(m)
    return out


def make_adaptive_fg(master: Image.Image, canvas: int = 1080) -> Image.Image:
    fg = Image.new("RGBA", (canvas, canvas), (0, 0, 0, 0))
    target = int(canvas * 0.82)
    art = resize(master, target)
    x = (canvas - target) // 2
    y = (canvas - target) // 2
    fg.paste(art, (x, y), art)
    return fg


def make_monochrome(master: Image.Image, canvas: int = 1080) -> Image.Image:
    art = make_adaptive_fg(master, canvas)
    px = art.load()
    mono = Image.new("RGBA", art.size, (0, 0, 0, 0))
    mp = mono.load()
    for y in range(art.size[1]):
        for x in range(art.size[0]):
            r, g, b, a = px[x, y]
            if a < 8:
                continue
            brightness = (r + g + b) / 3
            if brightness > 55 or g > 90 or b > 120 or (r > 200 and g > 200 and b > 200):
                strength = min(255, int((brightness - 40) * 2.2))
                if r > 220 and g > 220 and b > 220:
                    strength = 255
                mp[x, y] = (255, 255, 255, max(strength, 0))
    alpha = mono.split()[-1].filter(ImageFilter.GaussianBlur(1.2))
    return Image.merge("RGBA", (Image.new("L", mono.size, 255),) * 3 + (alpha,))


def make_bg(size: int) -> Image.Image:
    return Image.new("RGBA", (size, size), BASE)


def main() -> None:
    img = Image.open(SRC).convert("RGBA")
    print(f"source={img.size}")
    icon = crop_icon(img)
    print(f"icon={icon.size}")

    fg_hi = make_adaptive_fg(icon, 1080)
    mono_hi = make_monochrome(icon, 1080)

    play_path = OUT_ROOT / "ic_launcher-playstore.png"
    resize(icon, 512).save(play_path, "PNG")
    print("wrote", play_path)

    (RES / "drawable").mkdir(parents=True, exist_ok=True)
    resize(fg_hi, 432).save(RES / "drawable" / "ic_launcher_foreground.png", "PNG")
    resize(mono_hi, 432).save(RES / "drawable" / "ic_launcher_monochrome.png", "PNG")

    nobg = icon.copy()
    p = nobg.load()
    for y in range(nobg.size[1]):
        for x in range(nobg.size[0]):
            r, g, b, a = p[x, y]
            if abs(r - 0x1C) < 18 and abs(g - 0x1F) < 18 and abs(b - 0x22) < 18:
                p[x, y] = (r, g, b, 0)
    resize(nobg, 512).save(RES / "drawable" / "ic_launcher_nobg.png", "PNG")

    legacy = {
        "mipmap-ldpi": 36,
        "mipmap-mdpi": 48,
        "mipmap-hdpi": 72,
        "mipmap-xhdpi": 96,
        "mipmap-xxhdpi": 144,
        "mipmap-xxxhdpi": 192,
    }
    adaptive = {
        "mipmap-mdpi": 108,
        "mipmap-hdpi": 162,
        "mipmap-xhdpi": 216,
        "mipmap-xxhdpi": 324,
        "mipmap-xxxhdpi": 432,
    }

    for folder, size in legacy.items():
        d = RES / folder
        d.mkdir(parents=True, exist_ok=True)
        full = resize(icon, size)
        full.save(d / "ic_launcher.png", "PNG")
        apply_round(full).save(d / "ic_launcher_round.png", "PNG")
        full.save(d / "ic_launcher_static.png", "PNG")
        print(f"{folder} legacy {size}")

    for folder, size in adaptive.items():
        d = RES / folder
        d.mkdir(parents=True, exist_ok=True)
        resize(fg_hi, size).save(d / "ic_launcher_foreground.png", "PNG")
        make_bg(size).convert("RGB").save(d / "ic_launcher_bg.png", "PNG")
        resize(mono_hi, size).save(d / "ic_launcher_monochrome.png", "PNG")
        print(f"{folder} adaptive {size}")

    print("DONE")


if __name__ == "__main__":
    main()
