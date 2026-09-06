#!/usr/bin/env python3
"""Render fastlane store icon (512x512 PNG) from the app's adaptive-icon vectors.

Reads the real launcher sources so the store icon can never drift from the app:
  res/mipmap-anydpi/ic_launcher.xml      -> background colour + foreground ref
  res/values/colors.xml                  -> @color/ic_launcher_background
  res/drawable/<foreground>.xml          -> vector pathData (+ fillType)

Usage:  python3 scripts/render-icon.py [out.png]
Deps:   cairosvg, Pillow  (pip install cairosvg)
"""
import pathlib
import re
import sys

import cairosvg

ROOT = pathlib.Path(__file__).resolve().parent.parent
RES = ROOT / "app/src/main/res"
OUT = pathlib.Path(sys.argv[1] if len(sys.argv) > 1 else
                   "fastlane/metadata/android/en-US/images/icon.png")


def read(p: pathlib.Path) -> str:
    return p.read_text(encoding="utf-8")


def colors() -> dict:
    txt = read(RES / "values/colors.xml")
    return dict(re.findall(r'<color name="([^"]+)">([^<]+)</color>', txt))


def adaptive() -> tuple[str, str]:
    xml = read(RES / "mipmap-anydpi/ic_launcher.xml")
    bg = re.search(r'<background android:drawable="([^"]+)"', xml).group(1)
    fg = re.search(r'<foreground android:drawable="([^"]+)"', xml).group(1)
    return bg, fg


def resolve_drawable(name: str, c: dict) -> str:
    m = re.match(r"@color/(.+)$", name)
    if m:
        return c[m.group(1)]
    raise SystemExit(f"unsupported background drawable: {name}")


def foreground_paths(xml: str) -> list[tuple[str, str, str]]:
    """Return [(pathData, fill-rule, fillColor)] honouring android:fillType (default nonZero)."""
    out = []
    for block in re.findall(r"<path\b(.*?)/>", xml, re.S):
        d = re.search(r'android:pathData="([^"]+)"', block)
        if not d:
            continue
        ft = re.search(r'android:fillType="([^"]+)"', block)
        rule = {"evenOdd": "evenodd", "nonZero": "nonzero"}.get(
            ft.group(1) if ft else "nonZero", "nonzero")
        fc = re.search(r'android:fillColor="([^"]+)"', block)
        out.append((d.group(1), rule, (fc.group(1) if fc else "#000000")))
    return out


def main() -> None:
    c = colors()
    bg_ref, fg_ref = adaptive()
    bg = resolve_drawable(bg_ref, c)
    fg_xml = read(RES / "drawable" / (fg_ref.rsplit("/", 1)[-1] + ".xml"))
    paths = foreground_paths(fg_xml)
    if not paths:
        raise SystemExit("no pathData found in foreground vector")

    vp = re.search(r'android:viewportWidth="([\d.]+)"', fg_xml).group(1)
    svg = [f'<svg xmlns="http://www.w3.org/2000/svg" width="512" height="512" '
           f'viewBox="0 0 {vp} {vp}">',
           f'<rect x="0" y="0" width="{vp}" height="{vp}" fill="{bg}"/>']
    svg += [f'<path d="{d}" fill="{col}" fill-rule="{r}"/>' for d, r, col in paths]
    svg.append("</svg>")

    OUT.parent.mkdir(parents=True, exist_ok=True)
    cairosvg.svg2png(bytestring="\n".join(svg).encode(),
                     write_to=str(OUT), output_width=512, output_height=512)
    print(f"OK {OUT}  (bg={bg}, {len(paths)} paths, rules={[r for _, r, _ in paths]})")


if __name__ == "__main__":
    main()
