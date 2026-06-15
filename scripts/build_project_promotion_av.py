from __future__ import annotations

import asyncio
import math
import re
import subprocess
import sys
from dataclasses import dataclass
from pathlib import Path


BASE = Path(r"D:\物业管理")
sys.path.insert(0, str(BASE / ".codex-video"))
sys.path.insert(0, str(BASE / ".codex-tts"))

import edge_tts
import imageio.v2 as imageio
import imageio_ffmpeg
import numpy as np
from PIL import Image, ImageDraw, ImageFilter, ImageFont, ImageOps


OUT_DIR = BASE / "交付物"
WORK_DIR = BASE / "宣传片输出"
PREVIEW_DIR = OUT_DIR / "rendered_project_promotion_av"
OUT_DIR.mkdir(exist_ok=True)
WORK_DIR.mkdir(exist_ok=True)
PREVIEW_DIR.mkdir(exist_ok=True)

W, H = 1280, 720
FPS = 18

RAW_VIDEO = WORK_DIR / "SPARK_Nexus项目推介书音视频版_无声.mp4"
NARRATION_AUDIO = OUT_DIR / "SPARK_Nexus城市物业治理中枢_项目推介书_音视频版_旁白.mp3"
FINAL_VIDEO = OUT_DIR / "SPARK_Nexus城市物业治理中枢_项目推介书_音视频版.mp4"
SCRIPT_TXT = OUT_DIR / "SPARK_Nexus城市物业治理中枢_项目推介书_音视频版_脚本.txt"
SRT_FILE = OUT_DIR / "SPARK_Nexus城市物业治理中枢_项目推介书_音视频版.srt"
BGM = WORK_DIR / "领码科技SPARK_Nexus城市物业治理中枢_3分钟宣传片_背景音乐.wav"
PRESENTER_AVATAR = "交付物/presenter_avatar_left_profile_cutout.png"

FONT = r"C:\Windows\Fonts\msyh.ttc"
FONT_BOLD = r"C:\Windows\Fonts\msyhbd.ttc"


def font(size: int, bold: bool = False) -> ImageFont.FreeTypeFont:
    return ImageFont.truetype(FONT_BOLD if bold else FONT, size)


F12 = font(12)
F14 = font(14)
F15 = font(15)
F16 = font(16)
F18 = font(18)
F20 = font(20, True)
F22 = font(22, True)
F24 = font(24, True)
F28 = font(28, True)
F34 = font(34, True)
F42 = font(42, True)
F54 = font(54, True)

NAVY = (11, 37, 69)
BLUE = (46, 116, 181)
TEAL = (0, 118, 107)
TEAL_2 = (73, 193, 178)
RED = (153, 27, 27)
RED_2 = (196, 64, 53)
GOLD = (196, 138, 45)
INK = (18, 34, 47)
MUTED = (91, 108, 124)
PAPER = (250, 252, 253)
LINE = (216, 225, 233)
WHITE = (255, 255, 255)
CREAM = (255, 248, 232)


@dataclass(frozen=True)
class Scene:
    key: str
    kicker: str
    title: str
    message: str
    bullets: tuple[str, ...]
    voice: str


SCENES = [
    Scene(
        "cover",
        "项目推介书音视频版",
        "把人民至上落到小区",
        "公共收益晒在阳光下，每一项服务闭环有据，群众福祉可感、可查、可验收。",
        ("人民至上", "群众路线", "阳光账本", "基层治理"),
        "领码科技 SPARK Nexus 城市物业治理中枢，不是再造一个物业收费系统，而是把人民至上落到小区，把群众路线落到服务，把公共收益晒在阳光下。小区虽小，连着民心；账本虽薄，照见作风；服务虽细，关系党心。",
    ),
    Scene(
        "pain",
        "为什么必须上",
        "糊涂账会吞掉信任",
        "钱不清，信任回不来；事不闭环，矛盾只会越积越大。",
        ("公共收益说不清", "服务责任扯不清", "监管证据找不到", "群众诉求没回音"),
        "今天的小区治理，表面看是物业收费、报修投诉、公共收益、银行对账，深处其实是信任问题。公共收益不透明，群众信任就会流失；服务过程不留痕，责任就会继续扯皮；监管证据不在线，风险就会继续潜伏。",
    ),
    Scene(
        "belief",
        "信念底座",
        "全心全意为人民服务，必须有基层工具",
        "党的宗旨落到小区，就是群众的钱有人管、群众的事有人办、群众的诉求有回音。",
        ("群众的钱有人管", "群众的事有人办", "群众的监督有入口", "群众的获得感有证据"),
        "中国共产党的根本宗旨，是全心全意为人民服务。落到小区，不是多写几句口号，而是群众的钱有人管，群众的事有人办，群众的监督有入口，群众的获得感有证据。SPARK Nexus 要做的，就是把这套信念变成每天能用的治理工具。",
    ),
    Scene(
        "policy",
        "政策窗口",
        "国家政策正在把治理推向小区",
        "基层治理、城乡社区服务、智慧社区、完整社区、公共收益透明化，正在汇成同一条路。",
        ("党建引领基层治理", "城乡社区服务体系", "智慧社区建设", "完整社区试点", "公共收益阳光管理"),
        "从基层治理现代化，到城乡社区服务体系建设，再到智慧社区、完整社区和公共收益管理，各级政策都在指向同一个方向：治理要下沉到社区，服务要连接到居民，公共收益要回到阳光下。谁先把这件事做成闭环，谁就先形成可复制的治理样板。",
    ),
    Scene(
        "evidence",
        "全国类似探索",
        "海南、惠州、连云港等地已经给出方向",
        "公共收益正在从原则要求走向账户、台账、公示、审计、共管的可操作表单。",
        ("海南：公共收益指导意见", "惠州：智慧物业平台", "连云港：账户共管试点", "七台河：智慧社区平台", "上海：美好家园案例"),
        "全国已经出现类似探索。海南关注公共收益范围、专项账户、公示、审计和会计核算；惠州推进智慧物业平台；连云港探索公共收益账户共管；七台河用智慧社区平台减负基层；上海美好家园案例强调党建引领、业主监督和公共收益反哺小区治理。这说明项目不是孤点，而是顺着全国治理升级的方向往前走。",
    ),
    Scene(
        "system",
        "系统答案",
        "一张小区主档，五方共治闭环",
        "以小区为最小治理单元，把政府、居委会、物业、银行、业主放进同一套数字秩序。",
        ("小区主档", "准入审批", "数据授权", "资金闭环", "审计留痕"),
        "平台的底座是一张小区主档。政府和街道看监管，居委会管准入，物业跑服务，银行管资金，业主看公开、评服务、参与表决。所有主体围绕同一个小区协同，所有动作都形成授权边界和审计证据。",
    ),
    Scene(
        "revenue",
        "公共收益闭环",
        "钱从哪里来、到哪里去、谁同意、谁监督",
        "来源归集、专户管理、审批表决、公示公开、审计导出，缺一环都不叫透明。",
        ("收益来源", "专项账户", "支出审批", "业主公示", "审计留痕"),
        "公共收益是最容易激发矛盾的地方，也是最能建立信任的地方。平台要把收益来源、银行账户、凭证附件、支出审批、业主表决、公示公开和审计导出连成一条链。钱从哪里来，到哪里去，谁同意，谁监督，都必须在系统里留下证据。",
    ),
    Scene(
        "bank_flow",
        "银行对账闭环",
        "每一笔资金都有来源、有去向、有差异处理",
        "支付、流水、回调、对账、差异、放款和导出，形成银行侧可复核证据链。",
        ("在线缴费", "流水导入", "自动匹配", "差异处理", "回调验签", "审计导出"),
        "银行侧不能只做一个支付按钮。真实落地要有银行服务配置、线上缴费、流水导入、自动匹配、差异处理、放款回执、回调验签和审计导出。这样物业看得到到账情况，监管看得到资金流向，业主也能相信每一笔钱都有凭证。",
    ),
    Scene(
        "government",
        "政府/街道价值",
        "不谈个人利益，只谈人民福祉",
        "一套平台，形成群众有感的民生服务抓手、可推广的基层治理样板和可验收的治理证据。",
        ("群众获得感看得见", "基层治理抓得住", "风险隐患早发现", "验收审计有证据"),
        "对政府和街道，价值不是个人利益，更不是谈钱。价值是人民福祉，是群众获得感看得见，是基层治理抓得住，是风险隐患能前置化解，是每一次验收、每一次审计、每一次群众问询都有数据、有证据、有回音。",
    ),
    Scene(
        "stakeholders",
        "物业、银行、业主价值",
        "各方都有收益，治理才跑得久",
        "物业少扯皮，银行有场景，业主能监督，平台才能长期运营。",
        ("物业：收费更顺、投诉更少、续约更稳", "银行：入口、资金、流水、场景", "业主：看见钱、管住事、说话有用"),
        "对物业，平台让账单好收、解释有据、工单闭环，服务从挨骂变成有底气。对银行，平台让缴费通道升级为小区资金入口，拿到监管账户、对账服务和社区金融场景。对业主，公共收益可查，支出可追，服务可评，业主从旁观者变成共同管理人。",
    ),
    Scene(
        "product",
        "产品画面",
        "不是概念，是已经能跑的业务闭环",
        "监管驾驶舱、公共收益、物业收费、银行对账、业主小程序、验收中心，形成一条链。",
        ("管理端", "监管端", "业主端", "银行对账", "验收导出"),
        "这不是停在 PPT 里的概念。现有系统已经具备管理端、业主端、公共收益、物业收费、工单报修、银行对账和监管审计等能力。下一步要做的，是用样板小区把真实账单、真实流水、真实审批、真实公示跑通。",
    ),
    Scene(
        "implementation",
        "实施计划",
        "90 天先跑通样板，不做空转平台",
        "定名单、定数据、定接口、定指标、定验收，让项目从第一天就进入真实业务。",
        ("0-15 天：名单与场景", "16-30 天：数据与账号", "31-60 天：业务闭环", "61-75 天：银行监管", "76-90 天：验收复制"),
        "实施要避免只做页面展示。九十天内，先定街道、居委会、物业、银行和首批小区；再导入小区、楼栋、房屋、业主和账号；然后跑通缴费、公共收益、工单、审批、公示；再接入银行对账和监管预警；最后用验收中心导出证据包，形成可复制模板。",
    ),
    Scene(
        "acceptance",
        "验收指标",
        "验收不是看页面，而是看闭环和证据",
        "业务能跑通、数据能追溯、权限能隔离、证据能导出，才算真正落地。",
        ("基础数据完整", "业务闭环跑通", "监管能力可用", "安全审计留痕", "培训运维到位"),
        "验收不能只看系统有没有页面，而要看业务链路是否真实闭环。基础数据要完整，缴费、退款、对账、公共收益、审批、投票和工单要跑通；监管大屏、风险预警、信用评分和数据交换要可用；越权拦截、回调验签、导出留痕和培训运维也要有交付物。",
    ),
    Scene(
        "business_model",
        "持续运营",
        "样板靠治理点火，长期靠物业和银行续航",
        "政府侧看民生治理样板，物业看效率和信任，银行看场景入口，服务商看合规订单。",
        ("民生治理示范项目", "物业 SaaS 年费", "银行场景服务", "私有化/信创部署", "服务商生态"),
        "项目要能长期跑，不能只靠一次性建设费。政府侧关注群众获得感、基层治理样板和风险防范证据；物业付费买收费率、服务效率和业主信任；银行付费买小区资金入口、监管账户和对账服务；服务商通过合规准入获得长期订单。这样才有可持续运营的商业结构。",
    ),
    Scene(
        "closing",
        "行动口号",
        "早一天上线，群众早一天受益",
        "上了是样板，不上是隐患；晚一天透明，基层多一天风险。",
        ("早一天上线，群众早一天受益", "晚一天透明，基层多一天隐患", "公共收益不晒出来，信任就会继续流失"),
        "所以，这个项目不要只卖系统，要卖一套治理方案。上了是样板，不上是隐患。早一天上线，群众早一天看见透明，基层早一天掌握主动；晚一天透明，公共收益多一天糊涂账，基层治理多一天风险。SPARK Nexus，把人民至上落到小区，把民生福祉做成证据。",
    ),
]


NARRATION_TEXT = "\n\n".join(scene.voice for scene in SCENES)


def rect(draw: ImageDraw.ImageDraw, box, fill, outline=None, width=1, radius=0):
    if radius:
        draw.rounded_rectangle(box, radius=radius, fill=fill, outline=outline, width=width)
    else:
        draw.rectangle(box, fill=fill, outline=outline, width=width)


def wrap_text(draw: ImageDraw.ImageDraw, text: str, font_obj, max_width: int) -> list[str]:
    lines: list[str] = []
    for para in text.split("\n"):
        cur = ""
        for ch in para:
            probe = cur + ch
            if draw.textlength(probe, font=font_obj) <= max_width:
                cur = probe
            else:
                if cur:
                    lines.append(cur)
                cur = ch
        if cur:
            lines.append(cur)
    return lines


def draw_block(
    draw: ImageDraw.ImageDraw,
    text: str,
    x: int,
    y: int,
    font_obj,
    fill,
    max_width: int,
    line_gap: int = 8,
    max_lines: int | None = None,
) -> int:
    lines = wrap_text(draw, text, font_obj, max_width)
    if max_lines and len(lines) > max_lines:
        lines = lines[:max_lines]
        lines[-1] = lines[-1].rstrip("，。；、") + "…"
    yy = y
    for line in lines:
        draw.text((x, yy), line, font=font_obj, fill=fill)
        yy += font_obj.size + line_gap
    return yy


def centered(draw: ImageDraw.ImageDraw, text: str, box, font_obj, fill):
    x1, y1, x2, y2 = box
    bbox = draw.textbbox((0, 0), text, font=font_obj)
    tw, th = bbox[2] - bbox[0], bbox[3] - bbox[1]
    draw.text((x1 + (x2 - x1 - tw) / 2, y1 + (y2 - y1 - th) / 2 - 1), text, font=font_obj, fill=fill)


def background(frame_no: int, mode: str) -> Image.Image:
    x = np.linspace(0, 1, W)[None, :]
    y = np.linspace(0, 1, H)[:, None]
    if mode in {"cover", "system", "closing"}:
        base = np.zeros((H, W, 3), dtype=np.float32)
        base[..., 0] = 8 + 10 * x + 4 * y
        base[..., 1] = 31 + 22 * x + 12 * y
        base[..., 2] = 54 + 30 * x + 18 * y
    elif mode in {"belief", "government"}:
        base = np.zeros((H, W, 3), dtype=np.float32)
        base[..., 0] = 254 - 20 * y
        base[..., 1] = 246 - 32 * y + 3 * x
        base[..., 2] = 244 - 30 * y + 6 * x
    else:
        base = np.zeros((H, W, 3), dtype=np.float32)
        base[..., 0] = 244 - 12 * y + 5 * x
        base[..., 1] = 248 - 10 * y + 4 * x
        base[..., 2] = 251 - 8 * y + 6 * x
    wave = 4 * np.sin((x * 3.2 + y * 3.8 + frame_no * 0.014) * math.pi)
    arr = np.clip(base + wave[..., None], 0, 255).astype(np.uint8)
    return Image.fromarray(arr, "RGB")


IMAGE_CACHE: dict[Path, Image.Image] = {}


def load_image(path: str) -> Image.Image | None:
    p = BASE / path
    if not p.exists():
        return None
    if p not in IMAGE_CACHE:
        IMAGE_CACHE[p] = Image.open(p).convert("RGB")
    return IMAGE_CACHE[p]


def paste_image(canvas: Image.Image, draw: ImageDraw.ImageDraw, path: str, box, radius: int = 14):
    img = load_image(path)
    x1, y1, x2, y2 = box
    rect(draw, box, WHITE, outline=(205, 216, 226), width=1, radius=radius)
    if img is None:
        centered(draw, "素材缺失", box, F18, MUTED)
        return
    fitted = ImageOps.fit(img, (x2 - x1, y2 - y1), method=Image.Resampling.LANCZOS, centering=(0.5, 0.5))
    mask = Image.new("L", fitted.size, 0)
    mdraw = ImageDraw.Draw(mask)
    mdraw.rounded_rectangle((0, 0, fitted.width, fitted.height), radius=radius, fill=255)
    canvas.paste(fitted, (x1, y1), mask)
    rect(draw, box, None, outline=(205, 216, 226), width=1, radius=radius)


def draw_header(draw: ImageDraw.ImageDraw, scene: Scene, idx: int, dark: bool):
    color = WHITE if dark else NAVY
    muted = (194, 213, 224) if dark else MUTED
    draw.text((56, 36), "领码科技 SPARK Nexus", font=F20, fill=color)
    draw.text((56, 64), "城市物业治理中枢", font=F14, fill=muted)
    centered(draw, scene.kicker, (882, 36, 1136, 66), F14, muted)
    centered(draw, f"{idx + 1:02d} / {len(SCENES):02d}", (1150, 34, 1228, 66), F14, GOLD)
    rect(draw, (56, 684, 1224, 690), (214, 224, 232) if not dark else (51, 86, 105), radius=3)
    rect(draw, (56, 684, 56 + int(1168 * (idx + 1) / len(SCENES)), 690), GOLD if dark else TEAL, radius=3)


def draw_title(draw: ImageDraw.ImageDraw, scene: Scene, dark: bool):
    color = WHITE if dark else NAVY
    accent = TEAL_2 if dark else TEAL
    draw.text((68, 120), scene.kicker.upper(), font=F14, fill=accent)
    draw.text((68, 148), scene.title, font=F42, fill=color)
    draw_block(draw, scene.message, 70, 214, F20, (217, 235, 237) if dark else INK, 760, line_gap=10, max_lines=3)


def draw_caption(draw: ImageDraw.ImageDraw, scene: Scene, dark: bool):
    fill = (10, 37, 55, 230) if dark else (255, 255, 255, 235)
    outline = (68, 145, 147, 200) if dark else (204, 216, 226, 255)
    rect(draw, (86, 602, 1194, 660), fill, outline=outline, width=1, radius=14)
    draw_block(draw, scene.message, 116, 616, F18, WHITE if dark else NAVY, 1048, line_gap=5, max_lines=2)


def bullet_card(draw: ImageDraw.ImageDraw, x: int, y: int, w: int, h: int, title: str, color=TEAL):
    rect(draw, (x, y, x + w, y + h), WHITE, outline=LINE, radius=12)
    draw.ellipse((x + 18, y + 22, x + 38, y + 42), fill=color)
    draw.text((x + 52, y + 20), title, font=F18, fill=INK)


def draw_digital_human(canvas: Image.Image, frame_no: int, dark: bool, scene_key: str):
    layer = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    draw = ImageDraw.Draw(layer, "RGBA")
    bob = int(4 * math.sin(frame_no * 0.075))
    d = 116
    x, y = 1090, 122 + bob
    avatar_path = BASE / PRESENTER_AVATAR
    avatar = None
    if avatar_path.exists():
        if avatar_path not in IMAGE_CACHE:
            IMAGE_CACHE[avatar_path] = Image.open(avatar_path).convert("RGBA")
        avatar = IMAGE_CACHE[avatar_path]

    shadow = Image.new("RGBA", (d, d), (0, 0, 0, 0))
    sdraw = ImageDraw.Draw(shadow)
    sdraw.ellipse((5, 7, d - 2, d), fill=(0, 0, 0, 42))
    layer.alpha_composite(shadow.filter(ImageFilter.GaussianBlur(3)), (x + 4, y + 8))

    if avatar is not None:
        crop = ImageOps.fit(avatar, (d, d), method=Image.Resampling.LANCZOS, centering=(0.58, 0.38)).convert("RGBA")
        circle = Image.new("L", (d, d), 0)
        mdraw = ImageDraw.Draw(circle)
        mdraw.ellipse((0, 0, d, d), fill=255)
        alpha = crop.getchannel("A")
        alpha = Image.composite(alpha, Image.new("L", (d, d), 0), circle)
        crop.putalpha(alpha)
        layer.alpha_composite(crop, (x, y))

        mouth_open = 1.0 + 2.8 * (0.5 + 0.5 * math.sin(frame_no * 0.32))
        mx, my = x + 53, y + 53
        draw.ellipse(
            (mx, my, mx + 9, my + 2 + mouth_open),
            fill=(104, 32, 44, 105),
        )
        if mouth_open > 2.6:
            draw.arc((mx - 1, my - 1, mx + 10, my + 7), start=10, end=165, fill=(205, 112, 112, 105), width=1)

    canvas.alpha_composite(layer)


def scene_cover(canvas: Image.Image, draw: ImageDraw.ImageDraw, scene: Scene, p: float):
    paste_image(canvas, draw, "web-admin/src/assets/community-governance-hero.png", (650, 134, 1208, 456), radius=18)
    rect(draw, (70, 344, 598, 506), (255, 255, 255, 24), outline=(86, 169, 162, 120), radius=16)
    x = 98
    for i, b in enumerate(scene.bullets):
        rect(draw, (x + i * 120, 378, x + i * 120 + 92, 438), (14, 76, 83, 230), outline=(79, 191, 179, 160), radius=12)
        centered(draw, b, (x + i * 120, 378, x + i * 120 + 92, 438), F16, WHITE)
    draw.text((96, 470), "从文化理念到项目落地的音视频版", font=F18, fill=(220, 237, 238))


def scene_pain(canvas: Image.Image, draw: ImageDraw.ImageDraw, scene: Scene, p: float):
    draw_title(draw, scene, False)
    for i, item in enumerate(scene.bullets):
        x = 92 + (i % 2) * 552
        y = 330 + (i // 2) * 106
        rect(draw, (x, y, x + 488, y + 76), (255, 255, 255), outline=LINE, radius=13)
        draw.text((x + 30, y + 20), f"0{i + 1}", font=F22, fill=RED_2)
        draw.text((x + 92, y + 22), item, font=F22, fill=INK)
    draw.line((188, 558, 1094, 558), fill=(210, 218, 226), width=3)
    for i, label in enumerate(("不透明", "不信任", "多投诉", "高风险")):
        cx = 220 + i * 290
        draw.ellipse((cx - 42, 516, cx + 42, 600), fill=(255, 246, 232), outline=GOLD, width=2)
        centered(draw, label, (cx - 42, 516, cx + 42, 600), F18, NAVY)


def scene_belief(canvas: Image.Image, draw: ImageDraw.ImageDraw, scene: Scene, p: float):
    draw_title(draw, scene, False)
    rect(draw, (72, 330, 1208, 526), (255, 241, 240), outline=(236, 188, 182), radius=18)
    draw.text((110, 364), "中国共产党的根本宗旨", font=F22, fill=RED)
    draw.text((110, 404), "全心全意为人民服务", font=F54, fill=RED)
    draw.text((110, 478), "落到小区：钱有人管、事有人办、监督有入口、获得感有证据。", font=F20, fill=INK)
    for i, b in enumerate(scene.bullets):
        x = 96 + i * 286
        rect(draw, (x, 548, x + 240, 592), WHITE, outline=(229, 197, 193), radius=10)
        centered(draw, b, (x, 548, x + 240, 592), F16, RED)


def scene_policy(canvas: Image.Image, draw: ImageDraw.ImageDraw, scene: Scene, p: float):
    draw_title(draw, scene, False)
    labels = list(scene.bullets)
    for i, label in enumerate(labels):
        x = 84 + (i % 3) * 372
        y = 338 + (i // 3) * 96
        w = 328 if i < 3 else 514
        if i >= 3:
            x = 220 + (i - 3) * 560
        rect(draw, (x, y, x + w, y + 72), WHITE, outline=LINE, radius=12)
        draw.text((x + 24, y + 22), label, font=F20, fill=TEAL if i != 4 else RED)
    rect(draw, (158, 546, 1122, 592), (237, 247, 246), outline=(194, 221, 218), radius=12)
    centered(draw, "政策不是远处的文件，正在变成小区里的台账、流程、证据和服务入口。", (158, 546, 1122, 592), F18, NAVY)


def scene_evidence(canvas: Image.Image, draw: ImageDraw.ImageDraw, scene: Scene, p: float):
    draw_title(draw, scene, False)
    cards = [
        ("海南", "公共收益指导意见\n账户 / 公示 / 审计 / 核算", RED),
        ("惠州", "智慧物业服务平台\n公示 / 企业档案 / 诉求", TEAL),
        ("连云港", "公共收益账户共管\n银行账户 / 流水 / 审批", BLUE),
        ("七台河", "智慧社区信息化平台\n多部门数据 / 基层减负", GOLD),
        ("上海", "美好家园典型案例\n党建引领 / 业主监督", TEAL),
    ]
    for i, (city, body, color) in enumerate(cards):
        x = 74 + i * 238
        y = 338
        rect(draw, (x, y, x + 198, y + 154), WHITE, outline=LINE, radius=14)
        draw.text((x + 22, y + 24), city, font=F26 if "F26" in globals() else F24, fill=color)
        draw_block(draw, body, x + 22, y + 70, F15, INK, 154, line_gap=5, max_lines=4)
    rect(draw, (132, 538, 1148, 590), CREAM, outline=(232, 211, 164), radius=12)
    centered(draw, "这些探索共同指向一件事：公共收益治理必须从线下解释，升级为线上证据。", (132, 538, 1148, 590), F18, NAVY)


def scene_system(canvas: Image.Image, draw: ImageDraw.ImageDraw, scene: Scene, p: float):
    draw_title(draw, scene, True)
    cx, cy = 640, 424
    draw.ellipse((cx - 112, cy - 112, cx + 112, cy + 112), fill=(8, 77, 82), outline=TEAL_2, width=3)
    centered(draw, "小区主档", (cx - 100, cy - 54, cx + 100, cy - 12), F28, WHITE)
    centered(draw, "统一关系 / 授权 / 资金 / 服务", (cx - 100, cy + 10, cx + 100, cy + 42), F14, (207, 235, 232))
    nodes = [
        ("政府/街道", 208, 332),
        ("居委会", 358, 512),
        ("物业", 620, 570),
        ("银行", 884, 512),
        ("业主", 1036, 332),
    ]
    for name, x, y in nodes:
        draw.line((cx, cy, x, y), fill=(88, 175, 170, 120), width=3)
        rect(draw, (x - 82, y - 32, x + 82, y + 32), (255, 255, 255, 235), outline=TEAL_2, radius=14)
        centered(draw, name, (x - 82, y - 32, x + 82, y + 32), F18, NAVY)


def scene_revenue(canvas: Image.Image, draw: ImageDraw.ImageDraw, scene: Scene, p: float):
    draw_title(draw, scene, False)
    steps = list(scene.bullets)
    notes = ["广告、场地、停车等", "小区/业委会专户", "业委会/业主表决", "业主端公开可查", "凭证、日志、报表"]
    for i, (step, note) in enumerate(zip(steps, notes)):
        x = 78 + i * 238
        y = 356 + (i % 2) * 22
        rect(draw, (x, y, x + 178, y + 118), WHITE, outline=LINE, radius=15)
        draw.ellipse((x + 20, y + 18, x + 58, y + 56), fill=TEAL if i < 3 else GOLD)
        centered(draw, str(i + 1), (x + 20, y + 18, x + 58, y + 56), F20, WHITE)
        draw.text((x + 20, y + 66), step, font=F18, fill=NAVY)
        draw_block(draw, note, x + 20, y + 92, F13 if "F13" in globals() else F12, MUTED, 136, line_gap=4, max_lines=2)
        if i < len(steps) - 1:
            draw.line((x + 184, y + 58, x + 226, y + 58), fill=(188, 200, 210), width=4)
    rect(draw, (150, 546, 1130, 592), (237, 247, 246), outline=(194, 221, 218), radius=12)
    centered(draw, "公共收益透明不是一句承诺，而是一条从来源到审计的证据链。", (150, 546, 1130, 592), F18, NAVY)


def scene_bank_flow(canvas: Image.Image, draw: ImageDraw.ImageDraw, scene: Scene, p: float):
    draw_title(draw, scene, False)
    headers = ["环节", "系统动作", "输出证据"]
    rows = [
        ["缴费", "生成账单、在线支付", "支付订单、渠道流水"],
        ["入账", "导入银行流水、回调验签", "银行流水、验签日志"],
        ["对账", "自动匹配、差异标记", "匹配结果、差异清单"],
        ["处置", "退款、放款、人工复核", "处理记录、回执凭证"],
        ["监管", "导出报表、审计留痕", "审计包、监管报表"],
    ]
    x, y = 112, 332
    widths = [160, 420, 430]
    rect(draw, (x, y, x + sum(widths), y + 42), (18, 52, 78), radius=8)
    cur = x
    for h, w in zip(headers, widths):
        centered(draw, h, (cur, y, cur + w, y + 42), F16, WHITE)
        cur += w
    for r, row in enumerate(rows):
        yy = y + 42 + r * 42
        fill = WHITE if r % 2 == 0 else (247, 250, 252)
        rect(draw, (x, yy, x + sum(widths), yy + 42), fill, outline=LINE)
        cur = x
        for c, (cell, w) in enumerate(zip(row, widths)):
            draw.text((cur + 18, yy + 11), cell, font=F15, fill=TEAL if c == 0 else INK)
            cur += w
    rect(draw, (185, 548, 1095, 590), CREAM, outline=(232, 211, 164), radius=12)
    centered(draw, "银行从支付通道升级为资金证据服务商，才有长期场景价值。", (185, 548, 1095, 590), F18, NAVY)


def scene_government(canvas: Image.Image, draw: ImageDraw.ImageDraw, scene: Scene, p: float):
    draw_title(draw, scene, False)
    for i, item in enumerate(scene.bullets):
        x = 96 + (i % 2) * 552
        y = 342 + (i // 2) * 94
        rect(draw, (x, y, x + 488, y + 68), WHITE, outline=(226, 204, 202), radius=13)
        draw.text((x + 28, y + 19), "✓", font=F24, fill=RED)
        draw.text((x + 78, y + 20), item, font=F20, fill=INK)
    rect(draw, (170, 558, 1110, 602), (255, 241, 240), outline=(238, 205, 200), radius=12)
    centered(draw, "政府侧不讲个人收益，只讲人民福祉、基层治理、社会稳定和风险防范。", (170, 558, 1110, 602), F18, RED)


def scene_stakeholders(canvas: Image.Image, draw: ImageDraw.ImageDraw, scene: Scene, p: float):
    draw_title(draw, scene, False)
    cols = [
        ("物业", "收费更顺\n投诉更少\n续约更稳", TEAL),
        ("银行", "入口\n资金\n流水\n场景", BLUE),
        ("业主", "看见钱\n管住事\n说话有用", GOLD),
    ]
    for i, (name, body, color) in enumerate(cols):
        x = 94 + i * 398
        rect(draw, (x, 338, x + 336, 536), WHITE, outline=LINE, radius=16)
        draw.text((x + 32, 366), name, font=F28, fill=color)
        draw_block(draw, body, x + 34, 420, F22, INK, 260, line_gap=6)
    rect(draw, (176, 562, 1104, 604), (237, 247, 246), outline=(197, 224, 221), radius=12)
    centered(draw, "各方都有看得见的好处，治理闭环才不会停在试点。", (176, 562, 1104, 604), F18, NAVY)


def scene_product(canvas: Image.Image, draw: ImageDraw.ImageDraw, scene: Scene, p: float):
    draw_title(draw, scene, False)
    paste_image(canvas, draw, "outputs/admin-home-check.png", (82, 318, 594, 566), radius=14)
    paste_image(canvas, draw, "outputs/owner-mobile-home-final.png", (630, 308, 806, 574), radius=14)
    paste_image(canvas, draw, "宣传片输出/ui-preview-sheet.png", (838, 318, 1198, 566), radius=14)
    draw.text((116, 580), "管理端 / 监管端", font=F14, fill=MUTED)
    draw.text((650, 580), "业主小程序", font=F14, fill=MUTED)
    draw.text((870, 580), "宣传片界面抽帧", font=F14, fill=MUTED)


def scene_implementation(canvas: Image.Image, draw: ImageDraw.ImageDraw, scene: Scene, p: float):
    draw_title(draw, scene, False)
    steps = list(scene.bullets)
    for i, step in enumerate(steps):
        x = 82 + i * 238
        y = 356
        rect(draw, (x, y, x + 184, y + 132), WHITE, outline=LINE, radius=16)
        draw.ellipse((x + 22, y + 20, x + 66, y + 64), fill=TEAL if i < 3 else GOLD)
        centered(draw, str(i + 1), (x + 22, y + 20, x + 66, y + 64), F22, WHITE)
        draw_block(draw, step, x + 24, y + 78, F16, INK, 132, line_gap=5, max_lines=2)
        if i < len(steps) - 1:
            draw.line((x + 190, y + 64, x + 226, y + 64), fill=(188, 200, 210), width=4)
    rect(draw, (164, 542, 1116, 592), (237, 247, 246), outline=(194, 221, 218), radius=12)
    centered(draw, "试点阶段不贪大：先跑真实账单、真实流水、真实审批、真实公示。", (164, 542, 1116, 592), F18, NAVY)


def scene_acceptance(canvas: Image.Image, draw: ImageDraw.ImageDraw, scene: Scene, p: float):
    draw_title(draw, scene, False)
    rows = [
        ("基础数据", "小区、楼栋、房屋、业主、机构、角色、授权导入完成"),
        ("业务闭环", "缴费、退款、对账、公共收益、审批、投票、工单可跑通"),
        ("监管能力", "监管大屏、风险预警、信用评分、数据交换可用"),
        ("安全审计", "越权拦截、回调验签、防重放、敏感字段审计、导出留痕"),
        ("培训运维", "培训材料、签到记录、FAQ、运维手册齐备"),
    ]
    x, y, w = 100, 312, 1080
    header_h, row_h = 38, 40
    rect(draw, (x, y, x + w, y + header_h), (18, 52, 78), radius=8)
    centered(draw, "验收类别", (x, y, x + 190, y + header_h), F16, WHITE)
    centered(draw, "必须看得见的交付证据", (x + 190, y, x + w, y + header_h), F16, WHITE)
    for i, (name, body) in enumerate(rows):
        yy = y + header_h + i * row_h
        rect(draw, (x, yy, x + w, yy + row_h), WHITE if i % 2 == 0 else (247, 250, 252), outline=LINE)
        centered(draw, name, (x, yy, x + 190, yy + row_h), F15, TEAL)
        draw.text((x + 220, yy + 11), body, font=F14, fill=INK)
    rect(draw, (160, 558, 1120, 594), (255, 241, 240), outline=(236, 188, 182), radius=12)
    centered(draw, "页面存在不等于落地；业务闭环、权限隔离、审计导出才是验收核心。", (160, 558, 1120, 594), F17 if "F17" in globals() else F16, RED)


def scene_business_model(canvas: Image.Image, draw: ImageDraw.ImageDraw, scene: Scene, p: float):
    draw_title(draw, scene, False)
    items = [
        ("民生治理示范", "启动、验证、复制", RED),
        ("物业 SaaS", "收费、工单、公示", TEAL),
        ("银行场景服务", "监管账户、对账、流水", BLUE),
        ("信创部署", "私有化、国产化适配", GOLD),
        ("服务商生态", "准入、评价、长期单", TEAL),
    ]
    total_w = 980
    ratios = [0.18, 0.24, 0.24, 0.18, 0.16]
    x, y = 150, 380
    cur = x
    for (name, body, color), ratio in zip(items, ratios):
        ww = int(total_w * ratio)
        rect(draw, (cur, y, cur + ww, y + 82), color, outline=WHITE, width=2)
        centered(draw, name, (cur + 6, y + 10, cur + ww - 6, y + 38), F14, WHITE)
        centered(draw, body, (cur + 6, y + 42, cur + ww - 6, y + 72), F12, WHITE)
        cur += ww
    labels = ["政府侧看样板", "物业侧看效率", "银行侧看入口", "服务商看订单"]
    for i, label in enumerate(labels):
        bx = 190 + i * 230
        rect(draw, (bx, 508, bx + 180, 552), WHITE, outline=LINE, radius=10)
        centered(draw, label, (bx, 508, bx + 180, 552), F15, NAVY)
    rect(draw, (170, 556, 1110, 596), CREAM, outline=(232, 211, 164), radius=12)
    centered(draw, "样板负责点火，物业与银行负责续航，服务商生态负责放大。", (170, 556, 1110, 596), F18, NAVY)


def scene_roadmap(canvas: Image.Image, draw: ImageDraw.ImageDraw, scene: Scene, p: float):
    draw_title(draw, scene, False)
    steps = list(scene.bullets)
    start_x, y = 96, 394
    for i, step in enumerate(steps):
        x = start_x + i * 292
        draw.line((x + 116, y + 38, x + 292, y + 38), fill=LINE, width=4)
        rect(draw, (x, y, x + 232, y + 118), WHITE, outline=LINE, radius=16)
        draw.ellipse((x + 24, y + 24, x + 72, y + 72), fill=TEAL if i < 2 else GOLD)
        centered(draw, str(i + 1), (x + 24, y + 24, x + 72, y + 72), F22, WHITE)
        draw_block(draw, step, x + 88, y + 28, F18, INK, 120, line_gap=5, max_lines=2)
    rect(draw, (190, 552, 1090, 596), (255, 248, 232), outline=(230, 210, 165), radius=12)
    centered(draw, "先跑真实业务，再沉淀模板；先做样板，再复制城市。", (190, 552, 1090, 596), F18, NAVY)


def scene_closing(canvas: Image.Image, draw: ImageDraw.ImageDraw, scene: Scene, p: float):
    draw_title(draw, scene, True)
    rect(draw, (96, 330, 1184, 492), (255, 255, 255, 236), outline=(91, 185, 175, 180), radius=20)
    centered(draw, "上了是样板，不上是隐患", (120, 350, 1160, 404), F42, RED)
    centered(draw, "早一天上线，群众早一天受益；晚一天透明，基层多一天风险。", (120, 420, 1160, 462), F22, NAVY)
    for i, b in enumerate(scene.bullets):
        x = 100 + i * 360
        rect(draw, (x, 536, x + 320, 586), (10, 82, 82, 235), outline=TEAL_2, radius=12)
        centered(draw, b, (x + 8, 536, x + 312, 586), F14, WHITE)


SCENE_DRAWERS = {
    "cover": scene_cover,
    "pain": scene_pain,
    "belief": scene_belief,
    "policy": scene_policy,
    "evidence": scene_evidence,
    "system": scene_system,
    "revenue": scene_revenue,
    "bank_flow": scene_bank_flow,
    "government": scene_government,
    "stakeholders": scene_stakeholders,
    "product": scene_product,
    "implementation": scene_implementation,
    "acceptance": scene_acceptance,
    "business_model": scene_business_model,
    "roadmap": scene_roadmap,
    "closing": scene_closing,
}


def render_frame(scene_idx: int, local_frame: int, scene_frames: int, frame_no: int):
    scene = SCENES[scene_idx]
    dark = scene.key in {"cover", "system", "closing"}
    p = local_frame / max(1, scene_frames - 1)
    canvas = background(frame_no, scene.key).convert("RGBA")
    draw = ImageDraw.Draw(canvas, "RGBA")

    if dark:
        for i in range(12):
            x = int((frame_no * (0.55 + i * 0.03) + i * 149) % (W + 180) - 90)
            y = 122 + (i * 47) % 430
            draw.line((x, y, x + 140, y - 38), fill=(81, 195, 184, 44), width=2)
    else:
        for i in range(8):
            x = int((frame_no * (0.28 + i * 0.02) + i * 197) % (W + 140) - 70)
            y = 132 + (i * 61) % 410
            draw.line((x, y, x + 120, y - 24), fill=(199, 210, 222, 44), width=2)

    if scene.key not in {
        "pain",
        "belief",
        "policy",
        "evidence",
        "system",
        "revenue",
        "bank_flow",
        "government",
        "stakeholders",
        "product",
        "implementation",
        "acceptance",
        "business_model",
        "roadmap",
        "closing",
    }:
        draw_title(draw, scene, dark)
    SCENE_DRAWERS[scene.key](canvas, draw, scene, p)
    draw_header(draw, scene, scene_idx, dark)
    draw_digital_human(canvas, frame_no, dark, scene.key)
    draw_caption(draw, scene, dark)

    fade = min(1.0, local_frame / (FPS * 0.55), (scene_frames - local_frame - 1) / (FPS * 0.55))
    if fade < 1:
        overlay = Image.new("RGBA", (W, H), (0, 0, 0, int(255 * (1 - fade))))
        canvas = Image.alpha_composite(canvas, overlay)
    return np.asarray(canvas.convert("RGB"))


def write_script_file():
    lines = [
        "SPARK Nexus 城市物业治理中枢｜项目推介书音视频版脚本",
        "",
        "总口径：把人民至上落到小区，把群众路线落到服务，把公共收益晒在阳光下。",
        "",
    ]
    for idx, scene in enumerate(SCENES, 1):
        lines.extend(
            [
                f"{idx:02d}. {scene.title}",
                f"画面主张：{scene.message}",
                "关键词：" + " / ".join(scene.bullets),
                "旁白：" + scene.voice,
                "",
            ]
        )
    SCRIPT_TXT.write_text("\n".join(lines), encoding="utf-8")


async def make_narration():
    communicate = edge_tts.Communicate(
        NARRATION_TEXT,
        voice="zh-CN-XiaoxiaoNeural",
        rate="+10%",
        volume="+0%",
    )
    await communicate.save(str(NARRATION_AUDIO))


def audio_duration_seconds(path: Path) -> float:
    ffmpeg = imageio_ffmpeg.get_ffmpeg_exe()
    proc = subprocess.run(
        [ffmpeg, "-i", str(path)],
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
        text=True,
        encoding="utf-8",
        errors="ignore",
    )
    text = (proc.stderr or "") + (proc.stdout or "")
    m = re.search(r"Duration:\s*(\d+):(\d+):(\d+(?:\.\d+)?)", text)
    if not m:
        return 105.0
    h, mnt, sec = m.groups()
    return int(h) * 3600 + int(mnt) * 60 + float(sec)


def compute_durations(total_seconds: float) -> list[float]:
    weights = [max(95, len(scene.voice)) for scene in SCENES]
    total = max(total_seconds + 0.8, 88.0)
    raw = [total * weight / sum(weights) for weight in weights]
    floor = 7.0
    raised = [max(floor, x) for x in raw]
    scale = total / sum(raised)
    return [x * scale for x in raised]


def make_srt(durations: list[float]):
    def stamp(seconds: float) -> str:
        ms = int(round((seconds - int(seconds)) * 1000))
        seconds = int(seconds)
        h = seconds // 3600
        m = (seconds % 3600) // 60
        s = seconds % 60
        return f"{h:02d}:{m:02d}:{s:02d},{ms:03d}"

    cur = 0.0
    blocks = []
    for idx, (scene, dur) in enumerate(zip(SCENES, durations), 1):
        start, end = cur, cur + dur
        blocks.append(f"{idx}\n{stamp(start)} --> {stamp(end)}\n{scene.title}\n{scene.message}\n")
        cur = end
    SRT_FILE.write_text("\n".join(blocks), encoding="utf-8")


def make_video(durations: list[float]):
    writer = imageio.get_writer(
        str(RAW_VIDEO),
        fps=FPS,
        codec="libx264",
        quality=7,
        macro_block_size=16,
        ffmpeg_log_level="error",
        output_params=["-pix_fmt", "yuv420p", "-movflags", "+faststart"],
    )
    frame_no = 0
    try:
        for idx, dur in enumerate(durations):
            count = max(1, int(dur * FPS))
            for local_frame in range(count):
                writer.append_data(render_frame(idx, local_frame, count, frame_no))
                frame_no += 1
    finally:
        writer.close()


def mux(audio_seconds: float):
    ffmpeg = imageio_ffmpeg.get_ffmpeg_exe()
    if BGM.exists():
        fade_start = max(0.0, audio_seconds - 2.5)
        cmd = [
            ffmpeg,
            "-y",
            "-i",
            str(RAW_VIDEO),
            "-i",
            str(NARRATION_AUDIO),
            "-stream_loop",
            "-1",
            "-i",
            str(BGM),
            "-filter_complex",
            f"[1:a]volume=1.15[a1];[2:a]volume=0.10,atrim=duration={audio_seconds:.3f},afade=t=out:st={fade_start:.3f}:d=2.5[bg];[a1][bg]amix=inputs=2:duration=first:dropout_transition=1[a]",
            "-map",
            "0:v:0",
            "-map",
            "[a]",
        ]
    else:
        cmd = [
            ffmpeg,
            "-y",
            "-i",
            str(RAW_VIDEO),
            "-i",
            str(NARRATION_AUDIO),
            "-map",
            "0:v:0",
            "-map",
            "1:a:0",
        ]
    cmd += [
        "-c:v",
        "copy",
        "-c:a",
        "aac",
        "-b:a",
        "192k",
        "-movflags",
        "+faststart",
        "-shortest",
        str(FINAL_VIDEO),
    ]
    subprocess.run(cmd, check=True)


def make_preview_sheet(durations: list[float]):
    for png in PREVIEW_DIR.glob("*.png"):
        png.unlink()
    thumbs = []
    frame_no = 0
    for idx, dur in enumerate(durations):
        count = max(1, int(dur * FPS))
        mid = count // 2
        img = Image.fromarray(render_frame(idx, mid, count, frame_no + mid))
        out = PREVIEW_DIR / f"scene-{idx + 1:02d}.png"
        img.save(out, quality=95)
        thumb = img.copy()
        thumb.thumbnail((360, 210), Image.Resampling.LANCZOS)
        thumbs.append((idx + 1, thumb))
        frame_no += count

    cols, cell_w, cell_h = 2, 420, 260
    rows = math.ceil(len(thumbs) / cols)
    sheet = Image.new("RGB", (cols * cell_w, rows * cell_h), WHITE)
    draw = ImageDraw.Draw(sheet)
    for i, (num, thumb) in enumerate(thumbs):
        x = (i % cols) * cell_w
        y = (i // cols) * cell_h
        draw.text((x + 16, y + 12), f"scene-{num:02d}", font=F16, fill=NAVY)
        sheet.paste(thumb, (x + (cell_w - thumb.width) // 2, y + 42))
    sheet.save(PREVIEW_DIR / "contact-sheet.png", quality=95)


def main():
    write_script_file()
    asyncio.run(make_narration())
    audio_seconds = audio_duration_seconds(NARRATION_AUDIO)
    durations = compute_durations(audio_seconds)
    make_srt(durations)
    make_video(durations)
    mux(audio_seconds)
    make_preview_sheet(durations)
    print(FINAL_VIDEO)
    print(NARRATION_AUDIO)
    print(SCRIPT_TXT)
    print(PREVIEW_DIR / "contact-sheet.png")


if __name__ == "__main__":
    main()
