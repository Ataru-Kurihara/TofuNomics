"""通貨テクスチャを作るスクリプト。

- TofuCoin: 依頼で受け取った画像（tofu_coin_source.png、32×32 を 2 倍にした 64×64）を 32×32 に戻し、
  外側の黒い背景を透明にする
- TofuGold: TofuCoin と同じパレット・縁取りで、延べ棒に TOFU の刻印を打ったドット絵を描く

使い方: python3 -I resourcepack/tools/make_textures.py
"""
from pathlib import Path

from PIL import Image

HERE = Path(__file__).resolve().parent
OUT = HERE.parent / "assets" / "tofunomics" / "textures" / "item"

# TofuCoin の画像から取ったパレット（暗い順）
BG = (7, 8, 13, 255)          # 背景（透明にする）
BG_DITHER = (26, 20, 8, 255)  # 背景のまだら（透明にする）
OUTLINE = (42, 26, 5, 255)
DARK = (122, 82, 10, 255)     # 刻印の影・内側の縁
SHADE2 = (168, 116, 15, 255)
SHADE1 = (196, 141, 20, 255)
BASE = (230, 176, 33, 255)    # 面の色
LIGHT1 = (242, 194, 48, 255)
LIGHT2 = (255, 226, 122, 255)
LIGHT3 = (255, 240, 168, 255)
LETTER = (255, 243, 192, 255) # 刻印の文字
CLEAR = (0, 0, 0, 0)


def make_coin():
    src = Image.open(HERE / "tofu_coin_source.png").convert("RGBA")
    img = src.resize((32, 32), Image.NEAREST)
    # 外周から背景色（黒とまだら）だけを塗りつぶしで透明にする。コインの内側の色は触らない
    stack = [(x, y) for x in range(32) for y in (0, 31)] + [(x, y) for x in (0, 31) for y in range(32)]
    seen = set()
    while stack:
        x, y = stack.pop()
        if (x, y) in seen or not (0 <= x < 32 and 0 <= y < 32):
            continue
        seen.add((x, y))
        if img.getpixel((x, y)) in (BG, BG_DITHER):
            img.putpixel((x, y), CLEAR)
            stack += [(x + 1, y), (x - 1, y), (x, y + 1), (x, y - 1)]
    img.save(OUT / "tofu_coin.png")


GLYPHS = {
    "T": ["#####", "..#..", "..#..", "..#..", "..#..", "..#..", "..#.."],
    "O": [".###.", "#...#", "#...#", "#...#", "#...#", "#...#", ".###."],
    "F": ["#####", "#....", "#....", "####.", "#....", "#....", "#...."],
    "U": ["#...#", "#...#", "#...#", "#...#", "#...#", "#...#", ".###."],
}


def make_gold():
    img = Image.new("RGBA", (32, 32), CLEAR)
    px = img.putpixel
    top_y0, front_y0, bottom = 7, 13, 25
    left, right = 2, 29

    # 上面（台形）。奥ほど狭い
    for y in range(top_y0, front_y0):
        inset = front_y0 - y
        for x in range(left + inset, right - inset + 1):
            px((x, y), LIGHT3 if y == top_y0 + 1 and x < 16 else LIGHT2)
    # 上面と前面の境目のハイライト
    for x in range(left, right + 1):
        px((x, front_y0), LIGHT1)
    # 前面
    for y in range(front_y0 + 1, bottom + 1):
        for x in range(left, right + 1):
            if y >= bottom - 1:
                c = SHADE2
            elif y >= bottom - 3:
                c = SHADE1
            else:
                c = BASE
            if x == left and y < bottom - 1:
                c = LIGHT1
            px((x, y), c)

    # 刻印 TOFU
    text = "TOFU"
    width = len(text) * 5 + (len(text) - 1)
    x0 = left + (right - left + 1 - width) // 2
    y0 = front_y0 + 3
    letters = {(x0 + i * 6 + gx, y0 + gy)
               for i, ch in enumerate(text)
               for gy, row in enumerate(GLYPHS[ch])
               for gx, cell in enumerate(row) if cell == "#"}
    # コインの文字と同じく、文字の周り 1 マスを濃い色で縁取る
    for x, y in letters:
        for nx, ny in ((x + 1, y), (x - 1, y), (x, y + 1), (x, y - 1), (x + 1, y + 1)):
            px((nx, ny), DARK)
    for x, y in letters:
        px((x, y), LETTER)

    # きらめき（左上の角）
    for x, y in ((6, 9), (7, 8), (7, 10), (8, 9)):
        px((x, y), LETTER)

    # 縁取り（形の外側 1 マス）
    shape = {(x, y) for x in range(32) for y in range(32) if img.getpixel((x, y))[3]}
    for x, y in shape:
        for nx, ny in ((x + 1, y), (x - 1, y), (x, y + 1), (x, y - 1)):
            if 0 <= nx < 32 and 0 <= ny < 32 and (nx, ny) not in shape:
                px((nx, ny), OUTLINE)
    img.save(OUT / "tofu_gold.png")


if __name__ == "__main__":
    OUT.mkdir(parents=True, exist_ok=True)
    make_coin()
    make_gold()
