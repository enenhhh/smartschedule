#!/usr/bin/env python3
"""把官网用到的字裁成 woff2（assets/fonts/gsf.woff2、nsc.woff2）。

页面文字改动后重新跑一次，否则新加的字会退回系统字体：

    pip install fonttools brotli
    python3 tools/subset_fonts.py GoogleSansFlex.ttf NotoSansSC.ttf

两个源字体都是 SIL OFL 开源字体（Google Fonts 上的 Google Sans Flex、Noto Sans SC 可变字体），
体积较大，不放进仓库。
"""
import pathlib
import re
import sys

from fontTools import subset
from fontTools.ttLib import TTFont
from fontTools.varLib import instancer

ROOT = pathlib.Path(__file__).resolve().parent.parent
SOURCES = ['index.html', '404.html', 'disclaimer.html', 'notice.html', 'assets/site.js', 'assets/app.js', 'assets/lib.js']


def used_chars():
    text = ''.join((ROOT / f).read_text(encoding='utf-8') for f in SOURCES)
    text = re.sub(r'&#(\d+);', lambda m: chr(int(m.group(1))), text)
    chars = set(text) | {chr(c) for c in range(0x20, 0x7F)}
    # 动态生成的数字、日期、时间
    chars |= set('0123456789:–—·月日周第节分钟后开始')
    return ''.join(sorted(c for c in chars if c >= ' '))


def build(src, out, axes, chars):
    font = TTFont(src)
    opts = subset.Options()
    opts.layout_features = ['*']
    opts.name_IDs = ['*']
    opts.notdef_outline = True
    sub = subset.Subsetter(opts)
    sub.populate(text=chars)
    sub.subset(font)                       # 先裁字，再固定不需要的轴
    font = instancer.instantiateVariableFont(font, axes)
    font.flavor = 'woff2'
    font.save(out)
    print(f'{out.name}: {out.stat().st_size / 1024:.0f} KB')


if __name__ == '__main__':
    gsf, nsc = sys.argv[1], sys.argv[2]
    chars = used_chars()
    out = ROOT / 'assets' / 'fonts'
    out.mkdir(parents=True, exist_ok=True)
    print(f'{len(chars)} 个字符')
    # Google Sans Flex：保留字重和光学尺寸，固定宽度、圆角、倾斜
    build(gsf, out / 'gsf.woff2', {'wght': (300, 800), 'wdth': 100, 'ROND': 0, 'slnt': 0}, chars)
    build(nsc, out / 'nsc.woff2', {'wght': (300, 800)}, chars)
