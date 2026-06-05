from __future__ import annotations

import asyncio
import math
import subprocess
import sys
from pathlib import Path

BASE = Path(r"D:\物业管理")
sys.path.insert(0, str(BASE / ".codex-video"))

import edge_tts
import imageio.v2 as imageio
import imageio_ffmpeg
import numpy as np
from PIL import Image, ImageDraw, ImageFont


OUT_DIR = BASE / "宣传片输出"
OUT_DIR.mkdir(exist_ok=True)
DELIVERY_DIR = BASE / "交付物"
DELIVERY_DIR.mkdir(exist_ok=True)

W, H = 1280, 720
FPS = 20

RAW_VIDEO = OUT_DIR / "SPARK_Nexus商业计划书路演视频版_无声.mp4"
NARRATION_AUDIO = OUT_DIR / "SPARK_Nexus商业计划书路演视频版_中文旁白.mp3"
FINAL_VIDEO = DELIVERY_DIR / "SPARK_Nexus城市物业治理中枢_商业计划书路演视频版.mp4"

FONT = r"C:\Windows\Fonts\msyh.ttc"
FONT_BOLD = r"C:\Windows\Fonts\msyhbd.ttc"

f10 = ImageFont.truetype(FONT, 10)
f12 = ImageFont.truetype(FONT, 12)
f14 = ImageFont.truetype(FONT, 14)
f16 = ImageFont.truetype(FONT, 16)
f18 = ImageFont.truetype(FONT, 18)
f20 = ImageFont.truetype(FONT_BOLD, 20)
f22 = ImageFont.truetype(FONT_BOLD, 22)
f24 = ImageFont.truetype(FONT_BOLD, 24)
f28 = ImageFont.truetype(FONT_BOLD, 28)
f34 = ImageFont.truetype(FONT_BOLD, 34)
f42 = ImageFont.truetype(FONT_BOLD, 42)
f52 = ImageFont.truetype(FONT_BOLD, 52)


INK = (16, 34, 34)
DARK = (4, 34, 34)
DARK_2 = (9, 67, 61)
TEAL = (77, 199, 184)
TEAL_D = (0, 125, 112)
AMBER = (202, 135, 40)
CREAM = (244, 238, 228)
PAPER = (251, 248, 241)
MUTED = (96, 114, 118)
LINE = (212, 202, 184)
WHITE = (255, 255, 255)
RED = (190, 66, 48)


SCENES = [
    {
        "dur": 8,
        "kicker": "BUSINESS PLAN",
        "title": "把小区变成可治理、可付费、可经营的城市单元",
        "subtitle": "政府提供治理背书，居委会审批准入，物业公司与银行成为长期付费主体。",
        "caption": "不是再做一个物业后台，而是建设一套由基层治理背书的社区资金与服务基础设施。",
        "mode": "cover",
        "dark": True,
    },
    {
        "dur": 8,
        "kicker": "MARKET PAIN",
        "title": "小区里缺的不是系统，而是统一的协作秩序",
        "subtitle": "物业、政府、银行、本地服务商各有系统，却缺少共同准入、授权、资金和触达链路。",
        "caption": "SPARK Nexus 的切入点是先建秩序，再做运营和商业化。",
        "mode": "pain",
    },
    {
        "dur": 8,
        "kicker": "POLICY WINDOW",
        "title": "政策资金适合打开试点，长期收入落到物业和银行",
        "subtitle": "智慧社区、完整社区、基层治理、党建引领，都能成为启动理由。",
        "caption": "政府付启动钱，物业和银行付续费钱，这个收入结构更健康。",
        "mode": "policy",
    },
    {
        "dur": 8,
        "kicker": "PLATFORM SYSTEM",
        "title": "一张小区主档，把治理、物业、银行和服务生态连成一个系统",
        "subtitle": "核心资产不是页面，而是小区关系、审批证据、数据授权和资金流水。",
        "caption": "小区主档是平台的城市级资产底座。",
        "mode": "platform",
        "dark": True,
    },
    {
        "dur": 8,
        "kicker": "GATEKEEPER",
        "title": "居委会审批，是服务商进入小区的信任闸门",
        "subtitle": "银行、物业、本地商户从语义上都是服务商，只是服务类型和功能深度不同。",
        "caption": "商户入驻、物业绑定、银行绑定小区，都必须经过居委会审批。",
        "mode": "approval",
    },
    {
        "dur": 8,
        "kicker": "COMMITTEE OS",
        "title": "党建引领进入居委会工作台，形成可审计台账",
        "subtitle": "党组织活动、网格事项、关爱走访、资源联动和微信通知在小区授权里闭环。",
        "caption": "党的领导不是口号，而是平台里的流程、指标、通知和留痕。",
        "mode": "committee",
    },
    {
        "dur": 8,
        "kicker": "PROPERTY REVENUE",
        "title": "物业公司付费，因为平台直接改善现金流、效率和信任证据",
        "subtitle": "收费缴费、报修投诉、公共收益、公示审计，都是物业续费的真实理由。",
        "caption": "建议收费：0.5-2 元/户/月，或 8-30 万/年/物业公司。",
        "mode": "property",
    },
    {
        "dur": 8,
        "kicker": "BANK REVENUE",
        "title": "银行不是支付通道，而是小区资金服务商",
        "subtitle": "物业费、公共收益、监管账户、对账、放款和服务商结算，让银行持续参与。",
        "caption": "银行侧按多银行适配、缴费结算、监管账户、对账服务和数据交换包收费。",
        "mode": "bank",
        "dark": True,
    },
    {
        "dur": 8,
        "kicker": "ECOSYSTEM",
        "title": "服务商生态能增长，但必须先被治理秩序驯服",
        "subtitle": "统一把银行、物业、本地服务商视为服务商，用准入、评价、投诉、清退控风险。",
        "caption": "先做可信服务网络，再做商业流量生态。",
        "mode": "ecosystem",
    },
    {
        "dur": 8,
        "kicker": "GO TO MARKET",
        "title": "政府试点创造进入权，物业和银行续费创造商业结果",
        "subtitle": "推荐用一个街道或区县做 1+N 样板，再复制到更多小区和银行分支。",
        "caption": "关键 KPI：小区备案数、物业线上缴费率、银行交易额、服务商审批通过率。",
        "mode": "gtm",
    },
    {
        "dur": 8,
        "kicker": "ECONOMICS",
        "title": "保守模型下，第 2 年靠物业和银行续费转正",
        "subtitle": "政府项目收入用于启动，ARR 核心来自小区覆盖后的物业 SaaS 和银行场景服务。",
        "caption": "第 3 年目标：8,300 万收入，3,500 万经营利润。",
        "mode": "economics",
    },
    {
        "dur": 8,
        "kicker": "FUNDING ASK",
        "title": "融资 1,000-1,500 万，把一个试点变成可复制城市打法",
        "subtitle": "补齐真实微信/银行接口、居委会移动端、信创部署能力和首批城市样板销售交付。",
        "caption": "90 天完成街道样板，180 天复制到 10-30 小区，12 个月沉淀城市打法。",
        "mode": "ask",
        "dark": True,
    },
]

NARRATION_TEXT = """
SPARK Nexus，把小区变成可治理、可付费、可经营的城市单元。这不是物业后台，而是社区资金和服务基础设施。
市场问题不是缺功能，而是缺统一秩序。政府要留痕，物业要收钱，银行要场景，商户要入口。
政府和街道负责试点背书，长期收入落到物业公司和银行。
平台用一张小区主档，连接治理、物业、银行和服务生态，沉淀小区关系、审批证据、授权和资金流水。
居委会审批，是服务商进入小区的信任闸门。物业、银行、本地商户都先绑定小区，再接受审批。
党建进入居委会工作台，活动、网格、关爱走访和微信通知都留痕，让党的领导落到流程和台账里。
物业付费，因为平台改善缴费、工单、公共收益、公示审计和居民信任，直接影响现金流和效率。
银行付费，因为它从支付通道升级为小区资金服务商，参与代收、监管账户、自动对账和服务商结算。
服务商生态可以增长，但要靠准入、评价、投诉和清退控风险。先做可信服务网络，再做商业流量生态。
打法是先做街道样板，再复制小区、物业、银行和服务商网络，用备案数、缴费率和交易额验证。
保守模型下，第二年靠物业和银行续费转正，第三年目标收入八千三百万，经营利润三千五百万。
融资一千万到一千五百万，补齐微信和银行接口、居委会移动端、信创部署能力和城市样板交付。
""".strip()


def ease(t: float) -> float:
    return 1 - pow(1 - max(0.0, min(1.0, t)), 3)


def blend_color(a, b, t):
    return tuple(int(a[i] + (b[i] - a[i]) * t) for i in range(3))


def rect(draw, box, fill, outline=None, width=1, radius=0):
    if radius:
        draw.rounded_rectangle(box, radius=radius, fill=fill, outline=outline, width=width)
    else:
        draw.rectangle(box, fill=fill, outline=outline, width=width)


def text_wrap(draw, text, font, max_width):
    lines = []
    cur = ""
    for ch in text:
        if ch == "\n":
            if cur:
                lines.append(cur)
                cur = ""
            continue
        probe = cur + ch
        if draw.textlength(probe, font=font) <= max_width:
            cur = probe
        else:
            if cur:
                lines.append(cur)
            cur = ch
    if cur:
        lines.append(cur)
    return lines


def draw_text_block(draw, text, x, y, font, fill, max_width, line_gap=7, max_lines=None):
    lines = text_wrap(draw, text, font, max_width)
    if max_lines:
        lines = lines[:max_lines]
    for line in lines:
        draw.text((x, y), line, font=font, fill=fill)
        y += font.size + line_gap
    return y


def centered_text(draw, text, x, y, w, font, fill, line_gap=4):
    for line in text.splitlines():
        tw = draw.textlength(line, font=font)
        draw.text((x + (w - tw) / 2, y), line, font=font, fill=fill)
        y += font.size + line_gap


def background(frame_no, dark=False):
    x = np.linspace(0, 1, W)[None, :]
    y = np.linspace(0, 1, H)[:, None]
    arr = np.zeros((H, W, 3), dtype=np.uint8)
    if dark:
        arr[..., 0] = (3 + 8 * y + 3 * x).astype(np.uint8)
        arr[..., 1] = (32 + 40 * y + 11 * x).astype(np.uint8)
        arr[..., 2] = (33 + 32 * y + 8 * x).astype(np.uint8)
        wave = (8 * np.sin((x * 4.5 + y * 2.4 + frame_no * 0.01) * math.pi)).astype(np.int16)
    else:
        arr[..., 0] = (245 + 5 * x - 6 * y).astype(np.uint8)
        arr[..., 1] = (239 + 3 * x - 5 * y).astype(np.uint8)
        arr[..., 2] = (228 + 2 * x - 4 * y).astype(np.uint8)
        wave = (4 * np.sin((x * 3.2 + y * 2.7 + frame_no * 0.008) * math.pi)).astype(np.int16)
    return Image.fromarray(np.clip(arr.astype(np.int16) + wave[..., None], 0, 255).astype(np.uint8))


def draw_header(draw, scene, idx, p, dark=False):
    color = (186, 246, 236) if dark else TEAL_D
    muted = (151, 184, 181) if dark else MUTED
    draw.line((62, 62, 100, 62), fill=color, width=4)
    draw.text((116, 50), scene["kicker"], font=f14, fill=color)
    draw.text((61, 674), "SPARK Nexus 城市物业治理中枢", font=f10, fill=muted)
    draw.line((61, 652, 1218, 652), fill=(147, 171, 165) if dark else LINE, width=1)
    draw.text((1190, 666), f"{idx + 1:02d}", font=f14, fill=color)
    rect(draw, (510, 675, 770, 680), (35, 71, 70) if dark else (226, 216, 197), radius=3)
    rect(draw, (510, 675, 510 + int(260 * ((idx + p) / len(SCENES))), 680), color, radius=3)


def draw_caption(draw, scene, dark=False):
    fill = (8, 36, 36) if dark else (255, 255, 255)
    outline = (57, 198, 182) if dark else (187, 174, 151)
    text_color = WHITE if dark else INK
    rect(draw, (132, 592, 1148, 635), fill, outline=outline, width=1, radius=4)
    centered_text(draw, scene["caption"], 152, 603, 976, f16, text_color)


def title_area(draw, scene, dark=False):
    color = WHITE if dark else INK
    muted = (192, 216, 212) if dark else MUTED
    draw_text_block(draw, scene["title"], 64, 98, f42, color, 1100, 9, 2)
    draw_text_block(draw, scene["subtitle"], 66, 210, f18, muted, 950, 8, 3)


def draw_cover(draw, scene, p, frame_no):
    draw.text((70, 118), "SPARK Nexus", font=f28, fill=TEAL)
    draw_text_block(draw, scene["title"], 70, 178, f52, WHITE, 790, 12)
    draw_text_block(draw, scene["subtitle"], 72, 330, f18, (219, 235, 232), 760, 8)
    rect(draw, (872, 104, 1190, 540), (12, 73, 65), radius=0)
    draw_text_block(draw, "商业计划书\n2026.06", 944, 136, f20, WHITE, 180, 6)
    draw_text_block(draw, "不是再做一个物业后台，\n而是建设一套由基层治理背书的社区资金与服务基础设施。", 918, 302, f28, WHITE, 235, 8)
    metrics = [("1", "小区主档", "所有协作围绕同一小区空间"), ("2", "付费主线", "物业公司 + 银行服务商"), ("N", "服务生态", "本地服务商审批后进入小区")]
    for i, (n, label, note) in enumerate(metrics):
        x = 78 + i * 260
        rect(draw, (x, 496, x + 4, 574), TEAL if i != 1 else AMBER)
        draw.text((x + 20, 500), n, font=f24, fill=WHITE)
        draw.text((x + 20, 538), label, font=f14, fill=WHITE)
        draw.text((x + 20, 560), note, font=f10, fill=(190, 213, 210))


def draw_pain(draw, scene, p):
    title_area(draw, scene)
    cards = [
        ("政府/街道", "想监管风险、推动基层治理，但传统物业系统拿不到完整台账和验收证据。"),
        ("物业公司", "有收费、报修和业主满意度压力，却缺少居委会背书与银行资金协同。"),
        ("银行", "能做缴费和结算，但缺小区治理关系、物业授权和居民持续触达。"),
        ("本地服务商", "想进小区做生意，但没有居委会准入，会变成无序营销风险。"),
    ]
    for i, (name, body) in enumerate(cards):
        x = 72 + i * 292
        y = 305 + int(16 * (1 - ease(p)))
        rect(draw, (x, y, x + 232, y + 146), PAPER, outline=LINE)
        draw.text((x + 18, y + 22), name, font=f18, fill=TEAL_D)
        draw_text_block(draw, body, x + 18, y + 58, f14, MUTED, 190, 5, 4)
        if i < 3:
            draw.line((x + 235, y + 75, x + 278, y + 75), fill=LINE, width=4)
    rect(draw, (408, 502, 872, 552), DARK, radius=0)
    centered_text(draw, "SPARK Nexus 的切入点：先建秩序，再做运营和商业化。", 424, 516, 432, f16, WHITE)


def draw_policy(draw, scene, p):
    title_area(draw, scene)
    rect(draw, (196, 302, 732, 388), (230, 242, 239), outline=TEAL_D)
    draw.text((224, 327), "启动资金", font=f24, fill=TEAL_D)
    draw_text_block(draw, "政府/街道试点、基层治理项目、智慧社区建设、完整社区示范", 370, 326, f16, INK, 315, 4)
    rect(draw, (196, 432, 732, 518), (240, 225, 202), outline=AMBER)
    draw.text((224, 458), "续费收入", font=f24, fill=AMBER)
    draw_text_block(draw, "物业 SaaS 年费 + 银行服务年费 + 本地服务商生态收入", 370, 457, f16, INK, 315, 4)
    draw.line((814, 286, 814, 534), fill=(188, 172, 143), width=1)
    draw.text((874, 296), "政策依据", font=f24, fill=TEAL_D)
    for i, line in enumerate(["社区服务体系建设强调党建引领", "智慧社区要求便民服务和安全发展", "物业服务线上入口已经成熟"]):
        draw_text_block(draw, line, 858, 350 + i * 58, f16, INK, 305, 5)


def node(draw, x, y, w, h, title, body, strong=False, dark=False):
    fill = (14, 75, 67) if dark else (255, 255, 255)
    outline = TEAL if strong else (39, 94, 87) if dark else LINE
    color = WHITE if dark else INK
    muted = (195, 224, 219) if dark else MUTED
    rect(draw, (x, y, x + w, y + h), fill, outline=outline, width=2 if strong else 1)
    draw.text((x + 18, y + 18), title, font=f20, fill=TEAL if dark else TEAL_D)
    draw_text_block(draw, body, x + 18, y + 54, f14, muted, w - 36, 5, 3)


def draw_platform(draw, scene, p):
    title_area(draw, scene, True)
    cx, cy = 510, 382
    node(draw, cx, cy, 320, 104, "小区主档", "社区 / 楼栋 / 房屋 / 住户 / 机构关系", True, True)
    node(draw, 102, 312, 250, 86, "政府/街道", "监管大屏\n验收留档", False, True)
    node(draw, 102, 474, 250, 86, "居委会", "党建治理\n准入审批", True, True)
    node(draw, 920, 282, 250, 86, "物业公司", "收费报修\n公示票据", False, True)
    node(draw, 920, 436, 250, 86, "银行服务商", "代收监管\n对账放款", False, True)
    node(draw, 540, 540, 250, 86, "服务商生态", "准入上架\n评价退出", False, True)
    links = [
        ((352, 355), (510, 410)),
        ((352, 517), (510, 434)),
        ((830, 410), (920, 325)),
        ((830, 434), (920, 478)),
        ((670, 486), (665, 540)),
    ]
    for (x1, y1), (x2, y2) in links:
        draw.line((x1, y1, x2, y2), fill=TEAL, width=3)
    rect(draw, (462, 262, 610, 292), AMBER)
    centered_text(draw, "关系表", 462, 268, 148, f14, DARK)
    rect(draw, (668, 262, 816, 292), AMBER)
    centered_text(draw, "授权表", 668, 268, 148, f14, DARK)


def draw_approval(draw, scene, p):
    title_area(draw, scene)
    steps = [("01", "主体注册", "物业/银行/本地服务商成为平台租户"), ("02", "选择小区", "申请绑定具体小区与服务范围"), ("03", "居委会审批", "核资质、服务边界、居民影响与退出机制"), ("04", "自动授权", "通过后生成关系和数据权限"), ("05", "持续监管", "评价、投诉、暂停、清退都留痕")]
    for i, (num, name, body) in enumerate(steps):
        x = 72 + i * 238
        y = 350
        fill = DARK if i == 2 else PAPER
        text_color = WHITE if i == 2 else INK
        accent = TEAL if i == 2 else AMBER
        rect(draw, (x, y, x + 178, y + 122), fill, outline=LINE)
        draw.text((x + 20, y + 18), num, font=f22, fill=accent)
        draw.text((x + 20, y + 54), name, font=f20, fill=text_color)
        draw_text_block(draw, body, x + 20, y + 84, f12, text_color if i == 2 else MUTED, 130, 4, 2)
        if i < 4:
            draw.line((x + 184, y + 62, x + 226, y + 62), fill=LINE, width=4)
    rect(draw, (248, 546, 1032, 586), (230, 242, 239), outline=TEAL_D)
    centered_text(draw, "结果：服务商有入口，居民有保障，政府有留痕，平台有可复制的准入规则。", 264, 556, 752, f16, TEAL_D)


def draw_committee(draw, scene, p):
    title_area(draw, scene)
    rect(draw, (120, 322, 560, 540), DARK, radius=0)
    draw.text((154, 356), "居委会工作台", font=f34, fill=WHITE)
    draw_text_block(draw, "6 个核心指标\n治理事项 / 办理中 / 闭环率 / 到期提醒 / 关爱对象 / 党建引领", 154, 424, f20, WHITE, 340, 8)
    draw.text((154, 516), "权限边界：COMMUNITY_GOVERNANCE", font=f14, fill=TEAL)
    cards = [
        ("党建引领", "支部会议、党员先锋服务、红色议事厅、党建联席会。"),
        ("网格治理", "居民诉求、物业矛盾、资源联动、办理轨迹。"),
        ("关爱走访", "独居老人、困境儿童、残障居民、困难家庭。"),
        ("微信通知", "政府/居委会背书，治理消息触达并留调用日志。"),
    ]
    for i, (name, body) in enumerate(cards):
        x = 640 + (i % 2) * 260
        y = 304 + (i // 2) * 128
        rect(draw, (x, y, x + 230, y + 88), PAPER, outline=LINE)
        draw.text((x + 18, y + 16), name, font=f18, fill=TEAL_D)
        draw_text_block(draw, body, x + 18, y + 46, f12, MUTED, 190, 4, 2)


def draw_property(draw, scene, p):
    title_area(draw, scene)
    items = [("收费缴费", "线上缴费、欠费催缴、支付/退款、电子票据", 0.92, "现金流"), ("报修投诉", "SLA、处理回复、服务评价、微信/站内触达", 0.78, "效率"), ("公共收益", "支出审批、业主投票、银行放款、财务凭证", 0.68, "透明"), ("公示审计", "公告、公示、导出留痕、监管验收证据", 0.74, "信任")]
    for i, (name, body, bar, tag) in enumerate(items):
        y = 316 + i * 66
        draw.text((108, y), name, font=f20, fill=INK)
        draw_text_block(draw, body, 250, y + 2, f14, MUTED, 430, 4, 1)
        rect(draw, (694, y + 10, 1010, y + 26), (226, 216, 197))
        rect(draw, (694, y + 10, 694 + int(316 * bar * ease(p)), y + 26), TEAL_D if i == 0 else AMBER)
        draw.text((1044, y - 2), tag, font=f20, fill=TEAL_D if i == 0 else AMBER)
    rect(draw, (900, 252, 1130, 304), DARK)
    centered_text(draw, "建议收费：0.5-2 元/户/月\n或 8-30 万/年/物业公司", 915, 260, 200, f14, WHITE)


def draw_bank(draw, scene, p):
    title_area(draw, scene, True)
    node(draw, 530, 352, 220, 94, "银行服务商", "", True, True)
    centered_text(draw, "银行服务商", 530, 382, 220, f24, DARK)
    nodes = [(155, 308, "物业费代收"), (555, 232, "服务商结算"), (920, 308, "放款回执"), (260, 500, "监管账户"), (785, 500, "对账/差异")]
    for x, y, label in nodes:
        rect(draw, (x, y, x + 170, y + 58), (13, 69, 62), outline=TEAL, width=1)
        centered_text(draw, label, x, y + 18, 170, f16, WHITE)
        draw.line((x + 85, y + 58 if y < 352 else y, 640, 399), fill=TEAL, width=2)
    rect(draw, (98, 582, 352, 612), AMBER)
    centered_text(draw, "10-50 万/年/城市或区县", 98, 589, 254, f14, DARK)
    draw_text_block(draw, "收费点：多银行适配、缴费结算、监管账户、对账服务、回调验签、数据交换包。", 390, 586, f14, (205, 225, 222), 720, 4)


def draw_ecosystem(draw, scene, p):
    title_area(draw, scene)
    steps = [("入驻认证", "主体资质\n服务范围"), ("居委会评议", "居民影响\n退出机制"), ("小区授权", "数据范围\n服务边界"), ("服务履约", "评价投诉\n订单结算"), ("动态退出", "暂停清退\n证据留存")]
    for i, (name, body) in enumerate(steps):
        x = 72 + i * 238
        y = 404
        fill = (230, 242, 239) if i == 1 else PAPER
        outline = TEAL_D if i == 1 else LINE
        rect(draw, (x, y, x + 178, y + 114), fill, outline=outline)
        draw.text((x + 20, y + 22), name, font=f20, fill=TEAL_D if i == 1 else INK)
        draw_text_block(draw, body, x + 20, y + 62, f14, MUTED, 140, 4)
        if i < 4:
            draw.line((x + 184, y + 58, x + 226, y + 58), fill=LINE, width=4)
    rect(draw, (340, 576, 940, 620), DARK)
    centered_text(draw, "平台态度：先做可信服务网络，再做商业流量生态。", 356, 589, 568, f16, WHITE)


def draw_gtm(draw, scene, p):
    title_area(draw, scene)
    steps = [("1 个街道/区县", "政策背书\n试点采购"), ("1-3 个居委会", "党建/治理\n准入审批"), ("10-30 个小区", "主档和授权\n数据闭环"), ("2-5 家物业", "收费报修\n公共收益"), ("1-2 家银行", "代收监管\n对账放款"), ("20-50 服务商", "准入上架\n评价退出")]
    for i, (name, body) in enumerate(steps):
        x = 74 + i * 186
        y = 382 + int(10 * math.sin(p * math.pi + i))
        fill = (231, 243, 240) if i < 2 else (241, 226, 204) if i < 5 else WHITE
        rect(draw, (x, y, x + 144, y + 112), fill, outline=LINE)
        centered_text(draw, name, x, y + 24, 144, f16, TEAL_D if i < 2 else INK)
        centered_text(draw, body.split("\n")[0], x, y + 62, 144, f12, MUTED)
        centered_text(draw, body.split("\n")[1], x, y + 82, 144, f12, MUTED)
        if i < 5:
            draw.line((x + 150, y + 56, x + 178, y + 56), fill=LINE, width=4)
    centered_text(draw, "关键 KPI：小区备案数、物业线上缴费率、银行交易额、服务商审批通过率、治理事项闭环率。", 260, 582, 760, f16, TEAL_D)


def draw_economics(draw, scene, p):
    title_area(draw, scene)
    headers = ["年度", "覆盖小区", "物业/银行客户", "总收入", "经营结果"]
    rows = [["第 1 年", "100 小区", "12 付费客户", "480 万", "-170 万"], ["第 2 年", "600 小区", "60 付费客户", "2,100 万", "500 万"], ["第 3 年", "2,500 小区", "200 付费客户", "8,300 万", "3,500 万"]]
    x0, y0, table_w = 110, 318, 900
    col_w = table_w // len(headers)
    for i, h in enumerate(headers):
        draw.text((x0 + i * col_w + 16, y0), h, font=f14, fill=TEAL_D)
    for r, row in enumerate(rows):
        y = y0 + 42 + r * 74
        rect(draw, (x0, y, x0 + table_w, y + 48), WHITE, outline=LINE)
        for c, val in enumerate(row):
            color = RED if val.startswith("-") else INK
            if r == 2 and c in (3, 4):
                color = TEAL_D
            font = f18 if c == 0 else f16
            draw.text((x0 + c * col_w + 16, y + 14), val, font=font, fill=color)
    for i, value in enumerate([0.12, 0.28, 0.86]):
        y = 362 + i * 74
        rect(draw, (1050, y + 10, 1190, y + 24), (226, 216, 197))
        rect(draw, (1050, y + 10, 1050 + int(140 * value * ease(p)), y + 24), AMBER if i < 2 else TEAL_D)
    rect(draw, (850, 580, 1140, 626), DARK)
    centered_text(draw, "第 3 年目标：8,300 万收入 / 3,500 万经营利润", 866, 592, 258, f14, WHITE)


def draw_ask(draw, scene, p):
    title_area(draw, scene, True)
    x, y, w, h = 96, 360, 1088, 58
    parts = [("产品研发\n45%", 0.45, TEAL), ("市场销售\n25%", 0.25, AMBER), ("实施交付\n15%", 0.15, (142, 204, 193)), ("合规安全\n10%", 0.10, (219, 194, 143)), ("运营储备\n5%", 0.05, (235, 219, 176))]
    cur = x
    for label, ratio, color in parts:
        ww = int(w * ratio)
        rect(draw, (cur, y, cur + ww, y + h), color)
        centered_text(draw, label, cur, y + 13, ww, f12, DARK)
        cur += ww
    milestones = [("90 天", "完成街道样板、微信模板消息、银行服务配置闭环。"), ("180 天", "复制到 10-30 小区，形成物业和银行付费案例。"), ("12 月", "沉淀城市复制包，启动第二个区县或银行渠道。")]
    for i, (day, body) in enumerate(milestones):
        bx = 96 + i * 372
        rect(draw, (bx, 510, bx + 310, 586), (8, 55, 51), outline=TEAL_D)
        draw.text((bx + 22, 532), day, font=f28, fill=TEAL)
        draw_text_block(draw, body, bx + 120, 528, f14, (215, 233, 230), 165, 4, 2)


DRAWERS = {
    "cover": draw_cover,
    "pain": draw_pain,
    "policy": draw_policy,
    "platform": draw_platform,
    "approval": draw_approval,
    "committee": draw_committee,
    "property": draw_property,
    "bank": draw_bank,
    "ecosystem": draw_ecosystem,
    "gtm": draw_gtm,
    "economics": draw_economics,
    "ask": draw_ask,
}


def render_frame(scene_idx, local_frame, scene_frames, frame_no):
    scene = SCENES[scene_idx]
    dark = bool(scene.get("dark"))
    p = local_frame / max(1, scene_frames - 1)
    img = background(frame_no, dark=dark)
    draw = ImageDraw.Draw(img, "RGBA")

    if dark:
        for i in range(15):
            x = int((frame_no * (0.5 + i * 0.02) + i * 173) % (W + 240) - 120)
            y = 110 + (i * 41) % 485
            draw.line((x, y, x + 145, y - 42), fill=(68, 185, 173, 34), width=2)
    else:
        for i in range(10):
            x = int((frame_no * (0.35 + i * 0.02) + i * 211) % (W + 180) - 90)
            y = 128 + (i * 53) % 420
            draw.line((x, y, x + 135, y - 30), fill=(210, 198, 176, 45), width=2)

    DRAWERS[scene["mode"]](draw, scene, ease(p), frame_no) if scene["mode"] == "cover" else DRAWERS[scene["mode"]](draw, scene, ease(p))
    draw_header(draw, scene, scene_idx, p, dark=dark)
    draw_caption(draw, scene, dark=dark)

    fade = min(1.0, local_frame / (FPS * 0.45), (scene_frames - local_frame - 1) / (FPS * 0.45))
    if fade < 1:
        overlay = Image.new("RGBA", (W, H), (0, 0, 0, int(255 * (1 - fade))))
        img = Image.alpha_composite(img.convert("RGBA"), overlay).convert("RGB")
    return np.asarray(img)


def make_video():
    writer = imageio.get_writer(
        RAW_VIDEO,
        fps=FPS,
        codec="libx264",
        quality=7,
        macro_block_size=16,
        ffmpeg_log_level="error",
        output_params=["-pix_fmt", "yuv420p", "-movflags", "+faststart"],
    )
    frame_no = 0
    try:
        for idx, scene in enumerate(SCENES):
            count = int(scene["dur"] * FPS)
            for lf in range(count):
                writer.append_data(render_frame(idx, lf, count, frame_no))
                frame_no += 1
    finally:
        writer.close()


async def make_narration():
    communicate = edge_tts.Communicate(
        NARRATION_TEXT,
        voice="zh-CN-XiaoxiaoNeural",
        rate="+22%",
        volume="+0%",
    )
    await communicate.save(str(NARRATION_AUDIO))


def mux_with_narration():
    ffmpeg = imageio_ffmpeg.get_ffmpeg_exe()
    subprocess.run(
        [
            ffmpeg,
            "-y",
            "-i",
            str(RAW_VIDEO),
            "-i",
            str(NARRATION_AUDIO),
            "-c:v",
            "copy",
            "-c:a",
            "aac",
            "-b:a",
            "160k",
            "-movflags",
            "+faststart",
            str(FINAL_VIDEO),
        ],
        check=True,
    )


if __name__ == "__main__":
    make_video()
    asyncio.run(make_narration())
    mux_with_narration()
    print(FINAL_VIDEO)
