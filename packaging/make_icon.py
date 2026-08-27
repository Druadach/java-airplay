# -*- coding: utf-8 -*-
"""Generate an AirPlay-style icon (.ico) with pure Python (no PIL).

Design: rounded-square blue gradient tile + white monitor frame +
white AirPlay triangle at bottom-inside of the screen.
Writes sizes 256/128/64/48/32/16 as raw 32bpp BGRA entries.
"""
import struct

def lerp(a, b, t):
    return tuple(int(a[i] + (b[i] - a[i]) * t) for i in range(4))

# colors RGBA
BG_TL = (33, 150, 243, 255)      # material blue 500
BG_BR = (13, 71, 161, 255)       # material blue 900
WHITE = (255, 255, 255, 255)

def clamp(v, lo, hi):
    return lo if v < lo else hi if v > hi else v

def inside_rounded_square(x, y, n, r):
    m = n * 0.04            # outer transparent margin ratio
    size = n - 2 * m
    if x < m or y < m or x >= n - m or y >= n - m:
        return False
    lx = x - m
    ly = y - m
    rr = r * (size / n)
    cx1, cy1 = rr, rr
    cx2, cy2 = size - rr, size - rr
    qx = min(max(lx, cx1), cx2)
    qy = min(max(ly, cy1), cy2)
    dx = lx - qx
    dy = ly - qy
    return dx * dx + dy * dy <= rr * rr

def monitor_bounds(n):
    # screen area within the tile
    left = n * 0.14
    right = n * 0.86
    top = n * 0.18
    bottom = n * 0.72
    return left, right, top, bottom

def dist_to_ring(x, y, left, right, top, bottom, half):
    """distance outside the rect ring (stroke centered on edge, width 2*half)"""
    if left + half <= x <= right - half and top + half <= y <= bottom - half:
        return -1  # strictly inside inner hole -> background
    dx = max(left - x, 0, x - right)
    dy = max(top - y, 0, y - bottom)
    d = (dx * dx + dy * dy) ** 0.5
    return d

def in_triangle(x, y, n):
    """AirPlay triangle occupying lower-middle area under screen."""
    cx = n * 0.5
    apex_y = n * 0.66
    base_y = n * 0.88
    half_base = n * 0.13
    # sample grid scaled so it doesn't look ragged at 16px
    if y < apex_y or y > base_y:
        return False
    t = (y - apex_y) / (base_y - apex_y)
    halfw = half_base * t
    return abs(x - cx) <= halfw

def render(size):
    n = size
    ss = 4  # supersampling factor
    N = n * ss
    img = bytearray(N * N * 4)
    for py in range(N):
        for px in range(N):
            idx = (py * N + px) * 4
            # accumulate coverage over subpixels
            cov_bg = cov_frame = cov_tri = 0
            for sy in range(ss):
                for sx in range(ss):
                    x = px + (sx + 0.5) / ss
                    y = py + (sy + 0.5) / ss
                    if inside_rounded_square(x, y, N, N * 0.22):
                        cov_bg += 1
                        l, r, t, b = monitor_bounds(N)
                        half = N * 0.020
                        d = dist_to_ring(x, y, l, r, t, b, half)
                        if d == -1:
                            pass  # inner hole
                        elif d <= half:
                            cov_frame += 1
                        if in_triangle(x, y, N):
                            cov_tri += 1
            total = ss * ss
            a_bg = cov_bg / total
            a_f = cov_frame / total
            a_t = cov_tri / total
            # gradient color for bg
            tx = px / max(1, n - 1)
            ty = py / max(1, n - 1)
            g = lerp(BG_TL, BG_BR, clamp(tx * 0.45 + ty * 0.75, 0, 1))
            r_ = g[0]; g_ = g[1]; b_ = g[2]
            alpha = a_bg
            # white overlay (frame or triangle) blended over bg
            aw = min(1.0, a_f + a_t)
            if aw > 0:
                src_a = aw
                out_a = src_a + alpha * (1 - src_a)
                if out_a > 0:
                    r_ = (WHITE[0] * src_a + r_ * alpha * (1 - src_a)) / out_a
                    g_ = (WHITE[1] * src_a + g_ * alpha * (1 - src_a)) / out_a
                    b_ = (WHITE[2] * src_a + b_ * alpha * (1 - src_a)) / out_a
                alpha = out_a
            img[idx + 0] = int(clamp(b_, 0, 255))  # B
            img[idx + 1] = int(clamp(g_, 0, 255))  # G
            img[idx + 2] = int(clamp(r_, 0, 255))  # R
            img[idx + 3] = int(alpha * 255)
    # box-downsample SS -> n
    out = bytearray(n * n * 4)
    inv = ss * ss
    for py in range(n):
        for px in range(n):
            sb = sg = sr = sa = 0
            for sy in range(ss):
                row = ((py * ss + sy) * N + px * ss) * 4
                for sx in range(ss):
                    o = row + sx * 4
                    sb += img[o]; sg += img[o+1]; sr += img[o+2]; sa += img[o+3]
            k = (py * n + px) * 4
            out[k], out[k+1], out[k+2], out[k+3] = sb//inv, sg//inv, sr//inv, sa//inv
    return bytes(out)

def bmp_entry(rgba, n):
    """BITMAPINFOHEADER + XOR(BGRA, bottom-up, +AND mask) payload."""
    hdr = struct.pack('<IiiHHIIiiII', 40, n, n*2, 1, 32, 0,
                      n*n*4 + n*((n+31)//32)*4, 0, 0, 0, 0)
    stride = n * 4
    xor = bytearray()
    for yy in range(n-1, -1, -1):
        row = rgba[yy*stride:(yy+1)*stride]
        # premultiply-ish is unnecessary for ICO; keep straight alpha (fine for win)
        xor.extend(row)
    and_stride = ((n + 31)//32)*4
    andm = bytes(and_stride * n)
    return hdr + bytes(xor) + andm

def main():
    sizes = [16, 24, 32, 48, 64, 128, 256]
    blobs = []
    entries = []
    off = 6 + 16 * len(sizes)
    for s in sizes:
        print('rendering', s)
        data = bmp_entry(render(s), s)
        entries.append((s, len(data), off))
        blobs.append(data)
        off += len(data)
    ico = struct.pack('<HHH', 0, 1, len(sizes))
    for s, ln, o in entries:
        b = 0 if s >= 256 else s
        ico += struct.pack('<BBBBHHII', b, b, 0, 0, 1, 32, ln, o)
    with open('airplay.ico', 'wb') as f:
        f.write(ico)
        for blob in blobs:
            f.write(blob)
    print('written airplay.ico')

if __name__ == '__main__':
    import sys
    main()
