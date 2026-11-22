#!/usr/bin/env python3
"""
Генератор всех текстур мода Hellblock (только Pillow).
Запуск из корня проекта:  python3 tools/generate_textures.py
Можно править цвета/узоры и перегенерировать.
"""
import math
import os
import random

from PIL import Image

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..")
BLOCK_DIR = os.path.join(ROOT, "src/main/resources/assets/hellblock/textures/block")
ENTITY_DIR = os.path.join(ROOT, "src/main/resources/assets/hellblock/textures/entity")
os.makedirs(BLOCK_DIR, exist_ok=True)
os.makedirs(ENTITY_DIR, exist_ok=True)


def clamp(v):
    return max(0, min(255, int(v)))


def shade(c, f):
    return (clamp(c[0] * f), clamp(c[1] * f), clamp(c[2] * f), c[3] if len(c) > 3 else 255)


def mix(a, b, t):
    return tuple(clamp(a[i] + (b[i] - a[i]) * t) for i in range(3)) + (255,)


def value_noise(w, h, cell, seed):
    """Бесшовный (тайлящийся) value-noise 0..1."""
    rnd = random.Random(seed)
    gw, gh = max(1, w // cell), max(1, h // cell)
    grid = [[rnd.random() for _ in range(gw)] for _ in range(gh)]

    def smooth(t):
        return t * t * (3 - 2 * t)

    out = [[0.0] * w for _ in range(h)]
    for y in range(h):
        for x in range(w):
            fx, fy = x / cell, y / cell
            x0, y0 = int(fx) % gw, int(fy) % gh
            x1, y1 = (x0 + 1) % gw, (y0 + 1) % gh
            tx, ty = smooth(fx - int(fx)), smooth(fy - int(fy))
            a = grid[y0][x0] * (1 - tx) + grid[y0][x1] * tx
            b = grid[y1][x0] * (1 - tx) + grid[y1][x1] * tx
            out[y][x] = a * (1 - ty) + b * ty
    return out


def fbm(w, h, seed, cells=(8, 4, 2)):
    total = [[0.0] * w for _ in range(h)]
    amp, norm = 1.0, 0.0
    for i, c in enumerate(cells):
        n = value_noise(w, h, c, seed + i * 31)
        for y in range(h):
            for x in range(w):
                total[y][x] += n[y][x] * amp
        norm += amp
        amp *= 0.5
    return [[v / norm for v in row] for row in total]


def crack_mask(w, h, seed, branches=5, length=14):
    """Случайные извилистые трещины, возвращает set пикселей."""
    rnd = random.Random(seed)
    px = set()
    for _ in range(branches):
        x, y = rnd.randrange(w), rnd.randrange(h)
        ang = rnd.uniform(0, math.tau)
        for _ in range(length):
            px.add((int(x) % w, int(y) % h))
            ang += rnd.uniform(-0.7, 0.7)
            x += math.cos(ang)
            y += math.sin(ang)
            if rnd.random() < 0.12:
                bx, by, ba = x, y, ang + rnd.choice([-1, 1]) * 0.9
                for _ in range(rnd.randrange(3, 7)):
                    px.add((int(bx) % w, int(by) % h))
                    ba += rnd.uniform(-0.5, 0.5)
                    bx += math.cos(ba)
                    by += math.sin(ba)
    return px


def save(img, directory, name):
    path = os.path.join(directory, name)
    img.save(path)
    print("  ->", os.path.relpath(path, ROOT))


# ---------------------------------------------------------------- БЛОК ----

def stage0_top():
    n = fbm(16, 16, 11)
    img = Image.new("RGBA", (16, 16))
    for y in range(16):
        for x in range(16):
            v = n[y][x]
            img.putpixel((x, y), mix((52, 104, 38), (110, 168, 66), v))
    rnd = random.Random(5)
    for _ in range(14):
        img.putpixel((rnd.randrange(16), rnd.randrange(16)), (140, 196, 84, 255))
    return img


def stage0_side():
    n = fbm(16, 16, 22)
    img = Image.new("RGBA", (16, 16))
    rnd = random.Random(9)
    drop = [3 + rnd.randrange(0, 3) for _ in range(16)]
    for y in range(16):
        for x in range(16):
            v = n[y][x]
            if y < drop[x]:
                c = mix((52, 104, 38), (110, 168, 66), v)
            else:
                c = mix((92, 62, 38), (134, 94, 60), v)
            img.putpixel((x, y), c)
    return img


def frames_to_strip(frames):
    w, h = frames[0].size
    strip = Image.new("RGBA", (w, h * len(frames)))
    for i, f in enumerate(frames):
        strip.paste(f, (0, i * h))
    return strip


def stage1():
    """Тёмный камень с красными светящимися трещинами (4 кадра пульсации)."""
    base_n = fbm(16, 16, 33)
    cracks = crack_mask(16, 16, 77, branches=4, length=12)
    frames = []
    for k in range(4):
        glow = 0.55 + 0.45 * math.sin(k / 4 * math.tau)
        img = Image.new("RGBA", (16, 16))
        for y in range(16):
            for x in range(16):
                v = base_n[y][x]
                c = mix((38, 36, 40), (74, 70, 76), v)
                if (x, y) in cracks:
                    c = mix((120, 12, 10), (255, 70, 30), glow)
                else:
                    near = any(((x + dx) % 16, (y + dy) % 16) in cracks
                               for dx in (-1, 0, 1) for dy in (-1, 0, 1))
                    if near:
                        c = mix(c, (150, 30, 20), 0.35 * glow)
                img.putpixel((x, y), c)
        frames.append(img)
    return frames_to_strip(frames)


def stage2():
    """Незерак + лава + кровавые подтёки."""
    n = fbm(16, 16, 44, cells=(4, 2, 1))
    cracks = crack_mask(16, 16, 91, branches=3, length=9)
    rnd = random.Random(3)
    drips = [(rnd.randrange(16), rnd.randrange(2, 12), rnd.randrange(3, 8)) for _ in range(4)]
    frames = []
    for k in range(4):
        glow = 0.5 + 0.5 * math.sin(k / 4 * math.tau + 1.2)
        img = Image.new("RGBA", (16, 16))
        for y in range(16):
            for x in range(16):
                v = n[y][x]
                c = mix((74, 26, 28), (128, 46, 46), v)
                if (x, y) in cracks:
                    c = mix((255, 110, 20), (255, 210, 70), glow)
                img.putpixel((x, y), c)
        for (dx, dy, ln) in drips:
            for i in range(ln):
                yy = (dy + i) % 16
                t = 0.7 + 0.3 * math.sin(i)
                img.putpixel((dx, yy), mix((90, 0, 0), (170, 10, 10), t))
            img.putpixel((dx, (dy + ln) % 16), (200, 20, 20, 255))
        frames.append(img)
    return frames_to_strip(frames)


def stage3():
    """Обсидиан с огненными венами и руной."""
    n = fbm(16, 16, 55)
    cracks = crack_mask(16, 16, 123, branches=5, length=10)
    rune = {(7, 3), (8, 3), (7, 4), (8, 4), (5, 6), (6, 6), (9, 6), (10, 6), (6, 7), (9, 7),
            (6, 8), (7, 8), (8, 8), (9, 8), (7, 9), (8, 9), (7, 11), (8, 11), (6, 12), (9, 12)}
    frames = []
    for k in range(4):
        glow = 0.5 + 0.5 * math.sin(k / 4 * math.tau + 2.4)
        img = Image.new("RGBA", (16, 16))
        for y in range(16):
            for x in range(16):
                v = n[y][x]
                c = mix((14, 8, 22), (36, 22, 52), v)
                if (x, y) in cracks:
                    c = mix((200, 60, 10), (255, 150, 40), glow)
                if (x, y) in rune:
                    c = mix((140, 0, 20), (255, 40, 60), glow)
                img.putpixel((x, y), c)
        frames.append(img)
    return frames_to_strip(frames)


def stage4():
    """Живая плоть с огромным налитым кровью глазом. Глаз моргает/смотрит (8 кадров)."""
    n = fbm(16, 16, 66, cells=(4, 2, 1))
    veins = crack_mask(16, 16, 201, branches=5, length=9)
    frames = []
    cx = cy = 7.5
    # смещение зрачка по кадрам и «веко» (для моргания)
    pupil = [(0, 0), (1, 0), (1, 1), (0, 1), (-1, 0), (0, 0), (0, 0), (0, 0)]
    lid = [0, 0, 0, 0, 0, 3, 6, 3]
    for k in range(8):
        px, py = pupil[k]
        closed = lid[k]
        img = Image.new("RGBA", (16, 16))
        for y in range(16):
            for x in range(16):
                v = n[y][x]
                c = mix((58, 8, 10), (110, 20, 22), v)
                if (x, y) in veins:
                    c = mix((30, 0, 4), (60, 4, 8), v)
                dx, dy = x - cx, (y - cy) * 1.25
                d = math.hypot(dx, dy)
                if d < 5.6:
                    # белок налитый кровью
                    sclera = mix((220, 200, 150), (200, 60, 50), min(1, d / 5.6) ** 2)
                    c = sclera
                    # красные прожилки
                    if (x * 7 + y * 13 + k) % 11 == 0 and d > 2.6:
                        c = (170, 20, 20, 255)
                    if d < 3.0:
                        # радужка
                        c = mix((255, 150, 0), (220, 40, 0), d / 3.0)
                    pd = math.hypot(x - (cx + px), (y - (cy + py)) * 1.0)
                    if pd < 1.7:
                        c = (8, 0, 0, 255)
                    if pd < 0.7 and (x, y) == (int(cx + px + 1), int(cy + py - 1)):
                        c = (255, 255, 255, 255)
                    # веко
                    if closed and abs(y - cy) > (5.6 - closed):
                        c = mix((70, 10, 12), (100, 16, 18), v)
                elif d < 6.4:
                    c = (30, 0, 2, 255)
                img.putpixel((x, y), c)
        frames.append(img)
    return frames_to_strip(frames)


def write_mcmeta(name, frametime, interpolate):
    import json
    path = os.path.join(BLOCK_DIR, name + ".png.mcmeta")
    with open(path, "w") as f:
        json.dump({"animation": {"frametime": frametime, "interpolate": interpolate}}, f)


def make_blocks():
    print("Блок-печать:")
    save(stage0_top(), BLOCK_DIR, "seal_stage0_top.png")
    save(stage0_side(), BLOCK_DIR, "seal_stage0_side.png")
    save(stage1(), BLOCK_DIR, "seal_stage1.png")
    write_mcmeta("seal_stage1", 10, True)
    save(stage2(), BLOCK_DIR, "seal_stage2.png")
    write_mcmeta("seal_stage2", 8, True)
    save(stage3(), BLOCK_DIR, "seal_stage3.png")
    write_mcmeta("seal_stage3", 6, True)
    save(stage4(), BLOCK_DIR, "seal_stage4.png")
    write_mcmeta("seal_stage4", 7, False)


# --------------------------------------------------------------- ДЕМОН ----

TEX = 128


def paint_box(img, u, v, w, h, d, fn):
    """Закрашивает развёртку кубоида (как в ванильном формате UV) функцией fn(face,x,y,fw,fh)."""
    faces = {
        "top": (u + d, v, w, d),
        "bottom": (u + d + w, v, w, d),
        "right": (u, v + d, d, h),
        "front": (u + d, v + d, w, h),
        "left": (u + d + w, v + d, d, h),
        "back": (u + d + w + d, v + d, w, h),
    }
    for name, (fx, fy, fw, fh) in faces.items():
        for y in range(fh):
            for x in range(fw):
                col = fn(name, x, y, fw, fh)
                if col is not None:
                    img.putpixel((fx + x, fy + y), col)


SKIN_A = (92, 14, 14)
SKIN_B = (150, 28, 22)
DARK = (28, 4, 6)
BONE = (214, 200, 168)
GLOW = (255, 150, 30)


def make_demon():
    print("Демон:")
    img = Image.new("RGBA", (TEX, TEX), (0, 0, 0, 0))
    nz = fbm(TEX, TEX, 303, cells=(8, 4, 2, 1))
    rnd = random.Random(404)

    def skin(x, y, f=1.0):
        v = nz[y % TEX][x % TEX]
        return shade(mix(SKIN_A, SKIN_B, v), f) + ()

    # ----- голова (0,0, 8x8x8)
    def head(face, x, y, fw, fh):
        base = mix(SKIN_A, SKIN_B, nz[y][x])
        if face == "front":
            # брови
            if y == 2 and (x in (0, 1, 2, 5, 6, 7)):
                return DARK + (255,)
            if y == 3 and x in (1, 2, 5, 6):
                return (255, 235, 120, 255)          # глаза (ярче всего)
            if y == 4 and x in (1, 2, 5, 6):
                return (255, 160, 20, 255)
            if y == 3 and x in (0, 3, 4, 7):
                return DARK + (255,)
            # нос — две ноздри
            if y == 5 and x in (3, 4):
                return DARK + (255,)
            # пасть
            if y == 6:
                return DARK + (255,)
            if y == 7:
                return (240, 236, 220, 255) if x % 2 == 1 else DARK + (255,)
            if y == 5 and x in (1, 6):
                return (240, 236, 220, 255)          # клыки вниз/вверх
        if face == "top":
            if (x + y) % 5 == 0:
                return shade(base, 0.6)
        return shade(base, 0.9 if face in ("left", "right") else 1.0)

    paint_box(img, 0, 0, 8, 8, 8, head)

    # ----- торс (16,16, 8x12x4)
    cracks = crack_mask(40, 40, 505, branches=9, length=14)

    def body(face, x, y, fw, fh):
        base = mix(SKIN_A, SKIN_B, nz[16 + y][16 + x])
        if face == "front":
            # светящиеся трещины на груди + рёбра
            if (x, y) in cracks or (x + 1, y) in cracks and y % 3 == 0:
                return GLOW + (255,)
            if y in (3, 5, 7) and 1 <= x <= fw - 2:
                return shade(base, 0.55)
            if y > 9:
                return DARK + (255,)    # пояс
            if (x, y) == (3, 1) or (x, y) == (4, 1):
                return (255, 80, 20, 255)  # печать на груди
        if face == "back":
            if y % 4 == 0 and x % 2 == 0:
                return shade(base, 0.5)
        if face == "bottom":
            return DARK + (255,)
        return shade(base, 0.85)

    paint_box(img, 16, 16, 8, 12, 4, body)

    def limb(face, x, y, fw, fh):
        base = mix(SKIN_A, SKIN_B, nz[y % TEX][x % TEX])
        if face == "bottom":
            return (200, 190, 160, 255) if (x + y) % 2 else DARK + (255,)   # когти / копыта
        if y >= fh - 2 and face in ("front", "back", "left", "right"):
            return (200, 190, 160, 255) if x % 2 == 0 else DARK + (255,)
        if y < 3:
            return shade(base, 0.55)
        if (x * 3 + y * 5) % 17 == 0:
            return GLOW + (255,) if rnd.random() < 0.15 else shade(base, 0.7)
        return shade(base, 0.9 if face in ("left", "right") else 1.0)

    paint_box(img, 40, 16, 4, 12, 4, limb)     # правая рука
    paint_box(img, 32, 48, 4, 12, 4, limb)     # левая рука
    paint_box(img, 0, 16, 4, 12, 4, limb)      # правая нога
    paint_box(img, 16, 48, 4, 12, 4, limb)     # левая нога

    # ----- рога (64,0 база 2x3x2; 64,8 кончик 1x3x1)
    def horn(face, x, y, fw, fh):
        t = y / max(1, fh - 1)
        return mix((24, 18, 20), (230, 220, 190), t) if face not in ("top", "bottom") else (230, 220, 190, 255)

    paint_box(img, 64, 0, 2, 3, 2, horn)
    paint_box(img, 64, 8, 1, 3, 1, horn)

    # ----- крылья (0,64, 14x16x1)
    def wing(face, x, y, fw, fh):
        if face not in ("front", "back"):
            return DARK + (255,)
        # форма: диагональная косточка и перепонка со скруглённым низом
        if y >= fh - x * 1.05 + 1:
            return None                       # прозрачно (вырез)
        if y <= 1 or x <= 1:
            return (22, 8, 10, 255)           # костяная рама
        # «пальцы» по диагоналям
        if (x * 3 + y * 2) % 9 == 0 or (x - y // 2) % 5 == 0 and y > 4:
            return (40, 10, 12, 255)
        v = nz[64 + y][x]
        return mix((60, 6, 10), (130, 16, 18), v) if v > 0.2 else (60, 6, 10, 235)

    paint_box(img, 0, 64, 14, 16, 1, wing)

    save(img, ENTITY_DIR, "archdemon.png")


# ------------------------------------------------------------ ИКОНКА -----

def make_icon():
    print("Иконка мода:")
    img = Image.new("RGBA", (128, 128), (10, 2, 4, 255))
    nz = fbm(128, 128, 707, cells=(16, 8, 4))
    for y in range(128):
        for x in range(128):
            d = math.hypot(x - 64, y - 64) / 90
            v = nz[y][x]
            c = mix((10, 2, 4), (150, 20, 10), max(0, 1 - d) * 0.8 * v)
            img.putpixel((x, y), c)
    # большой глаз
    for y in range(128):
        for x in range(128):
            dx, dy = x - 64, (y - 64) * 1.5
            d = math.hypot(dx, dy)
            if d < 34:
                c = mix((235, 215, 170), (200, 50, 40), (d / 34) ** 2)
                if d < 18:
                    c = mix((255, 160, 0), (200, 30, 0), d / 18)
                if d < 8:
                    c = (6, 0, 0, 255)
                img.putpixel((x, y), c)
            elif d < 38:
                img.putpixel((x, y), (30, 0, 2, 255))
    save(img, os.path.join(ROOT, "src/main/resources/assets/hellblock"), "icon.png")


if __name__ == "__main__":
    make_blocks()
    make_demon()
    make_icon()
    print("Готово.")
