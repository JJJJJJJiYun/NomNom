from pathlib import Path
from urllib.request import urlretrieve
from PIL import Image, ImageDraw, ImageFont

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / 'docs' / 'screenshots'
OUT.mkdir(parents=True, exist_ok=True)
FONT_PATH = ROOT / '.cache' / 'fonts' / 'NotoSansCJKsc-Regular.otf'
FONT_URL = 'https://github.com/notofonts/noto-cjk/raw/main/Sans/OTF/SimplifiedChinese/NotoSansCJKsc-Regular.otf'
BG = '#F4F5F8'
CARD = '#FFFFFF'
TEXT = '#151515'
SUB = '#666A73'
BLUE = '#2563EB'
GREEN = '#16A34A'
ORANGE = '#F59E0B'
GRAY_FILL = '#EEF1F5'

restaurants = [
    {
        'name': '鸟居烧肉', 'meta': '日式烧肉 · ¥138 · 820m · 4.6',
        'summary': '主打烧肉和下酒小食，适合下班后两人轻松吃一顿。',
        'reasons': ['符合预算', '距离适中', '当前营业中'],
        'pros': ['牛舌口碑稳定', '环境有氛围', '服务响应快'],
        'cons': ['周末排队较久', '价格略高'],
    },
    {
        'name': '炭吉居酒屋', 'meta': '居酒屋 · ¥118 · 430m · 4.4',
        'summary': '适合夜宵和小酌，串烧稳定，但开门时间偏晚。',
        'reasons': ['更近', '符合预算', '适合收藏稍后去'],
        'pros': ['串烧稳定', '酒单丰富', '气氛轻松'],
        'cons': ['当前未营业', '座位偏紧凑'],
    },
]

history = [
    '鸟居烧肉 vs 炭吉居酒屋 → 你选了 鸟居烧肉',
    '鸟居烧肉 vs 汤城小厨 → 你选了 鸟居烧肉',
    '鸟居烧肉 vs 山葵割烹 → 你选了 鸟居烧肉',
]


def ensure_font():
    if FONT_PATH.exists():
        return
    FONT_PATH.parent.mkdir(parents=True, exist_ok=True)
    urlretrieve(FONT_URL, FONT_PATH)


def font(size, bold=False):
    ensure_font()
    return ImageFont.truetype(str(FONT_PATH), size=size)


def draw_chip(draw, xy, text, fill=GRAY_FILL, text_fill=TEXT, size=28):
    x, y = xy
    f = font(size)
    bbox = draw.textbbox((0, 0), text, font=f)
    w = bbox[2] - bbox[0] + 36
    h = bbox[3] - bbox[1] + 20
    draw.rounded_rectangle((x, y, x + w, y + h), radius=18, fill=fill)
    draw.text((x + 18, y + 8), text, font=f, fill=text_fill)
    return w, h


def draw_card(draw, x, y, w, h, radius=34):
    draw.rounded_rectangle((x, y, x + w, y + h), radius=radius, fill=CARD)


def draw_title(draw, text):
    draw.text((80, 110), text, font=font(54), fill=TEXT)


def screen_base(title):
    img = Image.new('RGB', (1290, 2796), BG)
    draw = ImageDraw.Draw(img)
    draw.rounded_rectangle((30, 30, 1260, 2766), radius=90, outline='#0F172A', width=6, fill=BG)
    draw_title(draw, title)
    return img, draw


def render_home():
    img, draw = screen_base('决策助手')
    draw_card(draw, 70, 220, 1150, 180)
    draw.text((110, 270), '晚饭双人场景', font=font(34), fill=TEXT)
    draw.text((110, 320), '预算 ¥80-¥180 · 最远 1500m', font=font(28), fill=SUB)
    draw.text((110, 362), '偏好：日式烧肉、居酒屋、割烹日料', font=font(28), fill=SUB)
    draw.text((110, 404), '数据来源：本地 Java API /api/v1/restaurants', font=font(24), fill=SUB)

    draw.text((80, 455), '本轮候选', font=font(40), fill=TEXT)
    y = 520
    for r in restaurants:
        draw_card(draw, 70, y, 1150, 300)
        draw.text((110, y + 36), r['name'], font=font(38), fill=TEXT)
        draw.text((110, y + 90), r['meta'], font=font(26), fill=SUB)
        draw.text((110, y + 140), r['summary'], font=font(28), fill=TEXT)
        cx = 110
        for chip in r['reasons']:
            w, _ = draw_chip(draw, (cx, y + 205), chip)
            cx += w + 12
        y += 340

    draw.rounded_rectangle((80, 2450, 1210, 2570), radius=28, fill=BLUE)
    draw.text((450, 2483), '开始今晚的选择', font=font(34), fill='white')
    img.save(OUT / 'home.png')


def draw_reason_block(draw, x, y, reasons):
    cx = x
    cy = y
    for chip in reasons:
        w, h = draw_chip(draw, (cx, cy), chip)
        cx += w + 10
        if cx > 1020:
            cx = x
            cy += h + 10
    return cy + 70


def render_decision():
    img, draw = screen_base('两两 PK')
    draw.text((80, 220), '选出今晚最想去的一家', font=font(40), fill=TEXT)
    y = 300
    for idx, r in enumerate(restaurants, start=1):
        draw_card(draw, 70, y, 1150, 760)
        draw.text((110, y + 30), '左边' if idx == 1 else '右边', font=font(24), fill=SUB)
        draw.text((110, y + 70), r['name'], font=font(42), fill=TEXT)
        draw.text((110, y + 125), r['meta'], font=font(28), fill=SUB)
        draw.text((110, y + 190), '推荐理由', font=font(30), fill=TEXT)
        next_y = draw_reason_block(draw, 110, y + 235, r['reasons'] + ['AI 推荐', '适合双人'])
        draw.text((110, next_y), 'AI 一句话简介', font=font(30), fill=TEXT)
        draw.text((110, next_y + 45), r['summary'], font=font(28), fill=SUB)
        draw.text((110, next_y + 120), '大家常夸', font=font(28), fill=GREEN)
        for i, item in enumerate(r['pros']):
            draw.text((110, next_y + 160 + i * 38), f'• {item}', font=font(26), fill=SUB)
        draw.text((660, next_y + 120), '常见提醒', font=font(28), fill=ORANGE)
        for i, item in enumerate(r['cons']):
            draw.text((660, next_y + 160 + i * 38), f'• {item}', font=font(26), fill=SUB)
        draw.rounded_rectangle((110, y + 650, 1180, y + 720), radius=22, fill=BLUE)
        draw.text((560, y + 670), '选这家', font=font(30), fill='white')
        y += 810
    img.save(OUT / 'decision.png')


def render_result():
    img, draw = screen_base('决策结果')
    winner = restaurants[0]
    draw.text((80, 220), '今晚就去这家', font=font(32), fill=SUB)
    draw.text((80, 270), winner['name'], font=font(62), fill=TEXT)
    draw.text((80, 345), winner['meta'], font=font(30), fill=SUB)

    sections = [
        ('为什么它胜出', [f'• {x}' for x in winner['reasons']]),
        ('AI 简介', [winner['summary']]),
        ('优缺点摘要', ['👍 ' + '、'.join(winner['pros']), '⚠️ ' + '、'.join(winner['cons'])]),
        ('本轮对战记录', [f'• {x}' for x in history]),
    ]
    y = 420
    for title, lines in sections:
        height = 80 + len(lines) * 46
        draw_card(draw, 70, y, 1150, height)
        draw.text((110, y + 26), title, font=font(32), fill=TEXT)
        for i, line in enumerate(lines):
            draw.text((110, y + 80 + i * 42), line, font=font(27), fill=SUB)
        y += height + 24
    draw.rounded_rectangle((80, 2470, 1210, 2590), radius=28, fill=BLUE)
    draw.text((480, 2503), '重新开始一轮', font=font(34), fill='white')
    img.save(OUT / 'result.png')


if __name__ == '__main__':
    render_home()
    render_decision()
    render_result()
    print('Generated screenshots in', OUT)
