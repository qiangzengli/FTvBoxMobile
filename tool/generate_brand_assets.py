"""Generate deterministic FBox launcher and splash PNGs."""

from pathlib import Path

from PIL import Image, ImageDraw, ImageFont


ROOT = Path(__file__).resolve().parents[1]
BLUE_TOP = (118, 150, 255)
BLUE_BOTTOM = (62, 79, 177)


def font(size: int) -> ImageFont.FreeTypeFont:
    candidates = [
        Path("/System/Library/Fonts/Supplemental/Arial Bold.ttf"),
        Path("/System/Library/Fonts/Helvetica.ttc"),
    ]
    for candidate in candidates:
        if candidate.exists():
            return ImageFont.truetype(str(candidate), size)
    return ImageFont.load_default(size=size)


def launcher(size: int) -> Image.Image:
    image = Image.new("RGB", (size, size), BLUE_BOTTOM)
    pixels = image.load()
    for y in range(size):
        ratio = y / max(size - 1, 1)
        for x in range(size):
            diagonal = min(1.0, ratio * 0.75 + (x / max(size - 1, 1)) * 0.25)
            pixels[x, y] = tuple(
                round(top + (bottom - top) * diagonal)
                for top, bottom in zip(BLUE_TOP, BLUE_BOTTOM)
            )

    draw = ImageDraw.Draw(image, "RGBA")
    pad = round(size * 0.205)
    stroke = max(2, round(size * 0.032))
    radius = round(size * 0.13)
    draw.rounded_rectangle(
        (pad, pad, size - pad, size - pad),
        radius=radius,
        fill=(255, 255, 255, 24),
        outline=(255, 255, 255, 235),
        width=stroke,
    )

    label_font = font(round(size * 0.47))
    bbox = draw.textbbox((0, 0), "F", font=label_font)
    text_width = bbox[2] - bbox[0]
    text_height = bbox[3] - bbox[1]
    draw.text(
        ((size - text_width) / 2, (size - text_height) / 2 - bbox[1]),
        "F",
        font=label_font,
        fill=(255, 255, 255, 255),
    )
    return image


def save_resized(master: Image.Image, path: Path, size: int) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    master.resize((size, size), Image.Resampling.LANCZOS).save(path, optimize=True)


def main() -> None:
    master = launcher(1024)

    android_sizes = {
        "mipmap-mdpi": 48,
        "mipmap-hdpi": 72,
        "mipmap-xhdpi": 96,
        "mipmap-xxhdpi": 144,
        "mipmap-xxxhdpi": 192,
    }
    for folder, size in android_sizes.items():
        save_resized(
            master,
            ROOT / "android/app/src/main/res" / folder / "ic_launcher.png",
            size,
        )

    ios_dir = ROOT / "ios/Runner/Assets.xcassets/AppIcon.appiconset"
    ios_sizes = {
        "Icon-App-20x20@1x.png": 20,
        "Icon-App-20x20@2x.png": 40,
        "Icon-App-20x20@3x.png": 60,
        "Icon-App-29x29@1x.png": 29,
        "Icon-App-29x29@2x.png": 58,
        "Icon-App-29x29@3x.png": 87,
        "Icon-App-40x40@1x.png": 40,
        "Icon-App-40x40@2x.png": 80,
        "Icon-App-40x40@3x.png": 120,
        "Icon-App-60x60@2x.png": 120,
        "Icon-App-60x60@3x.png": 180,
        "Icon-App-76x76@1x.png": 76,
        "Icon-App-76x76@2x.png": 152,
        "Icon-App-83.5x83.5@2x.png": 167,
        "Icon-App-1024x1024@1x.png": 1024,
    }
    for name, size in ios_sizes.items():
        save_resized(master, ios_dir / name, size)

    launch_dir = ROOT / "ios/Runner/Assets.xcassets/LaunchImage.imageset"
    for name, canvas_size, icon_size in [
        ("LaunchImage.png", (168, 185), 72),
        ("LaunchImage@2x.png", (336, 370), 144),
        ("LaunchImage@3x.png", (504, 555), 216),
    ]:
        canvas = Image.new("RGBA", canvas_size, (255, 255, 255, 0))
        icon = master.resize((icon_size, icon_size), Image.Resampling.LANCZOS)
        canvas.alpha_composite(
            icon.convert("RGBA"),
            ((canvas_size[0] - icon_size) // 2, (canvas_size[1] - icon_size) // 2),
        )
        canvas.save(launch_dir / name, optimize=True)


if __name__ == "__main__":
    main()
