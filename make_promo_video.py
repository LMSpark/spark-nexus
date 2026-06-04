from __future__ import annotations

import math
import subprocess
import sys
import wave
from pathlib import Path

sys.path.insert(0, r"D:\物业管理\.codex-video")

import imageio.v2 as imageio
import imageio_ffmpeg
import numpy as np
from PIL import Image, ImageDraw, ImageFont


BASE = Path(r"D:\物业管理")
OUT_DIR = BASE / "宣传片输出"
OUT_DIR.mkdir(exist_ok=True)

W, H = 1280, 720
FPS = 20
DURATION = 180

RAW_VIDEO = OUT_DIR / "领码科技SPARK_Nexus城市物业治理中枢_3分钟宣传片_无声.mp4"
AUDIO = OUT_DIR / "领码科技SPARK_Nexus城市物业治理中枢_3分钟宣传片_背景音乐.wav"
FINAL_VIDEO = BASE / "领码科技SPARK_Nexus城市物业治理中枢_3分钟宣传片.mp4"

FONT = r"C:\Windows\Fonts\msyh.ttc"
FONT_BOLD = r"C:\Windows\Fonts\msyhbd.ttc"

font_title = ImageFont.truetype(FONT_BOLD, 54)
font_scene = ImageFont.truetype(FONT_BOLD, 34)
font_h2 = ImageFont.truetype(FONT_BOLD, 28)
font_body = ImageFont.truetype(FONT, 24)
font_small = ImageFont.truetype(FONT, 18)
font_caption = ImageFont.truetype(FONT_BOLD, 26)


SCENES = [
    {
        "dur": 18,
        "title": "领码科技 SPARK Nexus 城市物业治理中枢",
        "tag": "多租户注册制 · 政府监管 · 银行对账 · 住户服务 · 物业运营",
        "caption": "一套面向政府、物业、小区、住户和银行的一体化平台升级方案。",
        "mode": "hero",
    },
    {
        "dur": 18,
        "title": "传统项目边界已经不够",
        "tag": "现实业务是多主体、多层级、多账户、多房屋",
        "bullets": ["一个物业服务多个小区", "一个小区接受多级监管", "一个住户绑定多套房屋", "一个银行服务多个账户"],
        "caption": "真实业务里，政府、物业、小区、住户、银行存在复杂的多对多关系。",
        "mode": "network",
    },
    {
        "dur": 18,
        "title": "平台升级为多租户注册制",
        "tag": "机构为租户，小区为协作空间，授权为数据边界",
        "bullets": ["注册审核作为准入入口", "租户关系自动建立", "小区服务可申请可授权", "数据访问按范围计算"],
        "caption": "新版本以注册制和授权制为底座，形成可扩展、可运营、可监管的平台。",
        "mode": "flow",
    },
    {
        "dur": 18,
        "title": "政府监管：穿透到资金和行为",
        "tag": "备案、服务、收益、风险、审计统一监管",
        "bullets": ["小区备案与物业服务台账", "公共收益收支与账户监管", "投诉趋势与风险预警", "审计日志全程留痕"],
        "caption": "政府侧获得跨小区、跨机构、跨资金流的透明监管抓手。",
        "mode": "dashboard",
    },
    {
        "dur": 18,
        "title": "物业运营：多小区统一管理",
        "tag": "房屋、住户、账单、缴费、工单、公告一体化",
        "bullets": ["减少重复录入", "减少线下流转", "统一服务标准", "沉淀运营数据"],
        "caption": "物业侧可以在一个平台内完成多小区运营和服务闭环。",
        "mode": "cards",
    },
    {
        "dur": 18,
        "title": "住户服务：多房屋与可信公开",
        "tag": "在线缴费、报修投诉、公共收益公示、投票参与",
        "bullets": ["绑定多套房屋", "查看账单与公示", "提交工单与投诉", "接收消息提醒"],
        "caption": "住户侧获得更便捷、更透明、更可信的物业服务体验。",
        "mode": "phone",
    },
    {
        "dur": 18,
        "title": "银行对账：资金闭环可追溯",
        "tag": "代收、监管、流水同步、差异处理",
        "bullets": ["支付订单", "银行流水", "自动对账", "差异明细", "监管视图"],
        "caption": "银行侧让每一笔资金都有来源、有去向、有凭证、有差异处理。",
        "mode": "ledger",
    },
    {
        "dur": 18,
        "title": "权限模型：防止越权和串数据",
        "tag": "用户 × 租户 × 角色 × 小区 × 数据范围 × 操作",
        "bullets": ["跨机构隔离", "跨小区授权", "敏感操作留痕", "监管按授权穿透"],
        "caption": "平台通过动态权限计算，控制每个角色能看什么、能做什么、能审批什么。",
        "mode": "formula",
    },
    {
        "dur": 18,
        "title": "五阶段实施路径",
        "tag": "从平台底座到生产化加固",
        "bullets": ["平台底座", "注册审核", "物业住户闭环", "银行监管闭环", "生产化加固"],
        "caption": "项目按五阶段推进，逐项验收，逐步形成真实可用的治理平台。",
        "mode": "timeline",
    },
    {
        "dur": 18,
        "title": "连接多主体，形成数字化治理体系",
        "tag": "城市级物业治理、公共收益监管、银行资金协同统一中枢",
        "bullets": ["政府更透明", "物业更高效", "小区更清晰", "住户更可信", "银行更规范"],
        "caption": "领码科技 SPARK Nexus 为多主体协同治理提供可落地、可持续演进的平台能力。",
        "mode": "closing",
    },
]


def lerp(a, b, t):
    return a + (b - a) * t


def rounded_rect(draw, box, radius, fill, outline=None, width=1):
    draw.rounded_rectangle(box, radius=radius, fill=fill, outline=outline, width=width)


def text_wrap(draw, text, font, max_width):
    lines = []
    cur = ""
    for ch in text:
        test = cur + ch
        if draw.textlength(test, font=font) <= max_width:
            cur = test
        else:
            if cur:
                lines.append(cur)
            cur = ch
    if cur:
        lines.append(cur)
    return lines


def draw_text_block(draw, text, x, y, font, fill, max_width, line_gap=8):
    for line in text_wrap(draw, text, font, max_width):
        draw.text((x, y), line, font=font, fill=fill)
        y += font.size + line_gap
    return y


def background(t):
    y = np.linspace(0, 1, H)[:, None]
    x = np.linspace(0, 1, W)[None, :]
    base = np.zeros((H, W, 3), dtype=np.uint8)
    base[..., 0] = (10 + 9 * y + 4 * x).astype(np.uint8)
    base[..., 1] = (27 + 22 * y + 9 * x).astype(np.uint8)
    base[..., 2] = (43 + 37 * y + 16 * x).astype(np.uint8)
    shimmer = (10 * np.sin((x * 5 + y * 2 + t * 0.03) * math.pi)).astype(np.int16)
    base = np.clip(base.astype(np.int16) + shimmer[..., None], 0, 255).astype(np.uint8)
    return Image.fromarray(base, "RGB")


def draw_common(draw, scene_idx, local_t, scene):
    draw.text((54, 34), "领码科技 SPARK Nexus 城市物业治理中枢", font=font_small, fill=(174, 190, 205))
    draw.line((54, 65, W - 54, 65), fill=(64, 88, 110), width=1)
    progress = scene_idx / len(SCENES)
    draw.rectangle((54, H - 34, W - 54, H - 29), fill=(42, 60, 78))
    draw.rectangle((54, H - 34, 54 + int((W - 108) * progress), H - 29), fill=(201, 155, 60))
    draw.text((W - 155, 31), f"{scene_idx + 1:02d} / {len(SCENES):02d}", font=font_small, fill=(201, 155, 60))

    # Caption strip.
    cap = scene["caption"]
    rounded_rect(draw, (72, H - 120, W - 72, H - 58), 8, (8, 20, 32), outline=(61, 82, 103))
    lines = text_wrap(draw, cap, font_caption, W - 190)
    y = H - 103 if len(lines) == 1 else H - 112
    for line in lines[:2]:
        tw = draw.textlength(line, font=font_caption)
        draw.text(((W - tw) / 2, y), line, font=font_caption, fill=(245, 248, 250))
        y += 30


def draw_hero(draw, scene, p):
    draw.text((96, 150), "SPARK Nexus", font=font_h2, fill=(201, 155, 60))
    draw_text_block(draw, scene["title"], 96, 205, font_title, (245, 248, 250), 900, 10)
    draw_text_block(draw, scene["tag"], 98, 335, font_body, (204, 217, 228), 920, 8)
    rounded_rect(draw, (96, 420, 1110, 505), 10, (18, 48, 74), outline=(201, 155, 60), width=2)
    draw.text((126, 444), "核心升级：从公共收益管理平台，升级为多租户注册制物业治理平台", font=font_h2, fill=(255, 255, 255))


def draw_network(draw, scene, p):
    center = (650, 315)
    nodes = [("政府", 360, 185), ("物业", 335, 420), ("小区", 650, 500), ("住户", 965, 420), ("银行", 940, 185)]
    rounded_rect(draw, (520, 250, 780, 380), 18, (22, 70, 100), outline=(201, 155, 60), width=3)
    draw.text((584, 292), "平台中枢", font=font_h2, fill=(255, 255, 255))
    for label, x, y in nodes:
        draw.line((center[0], center[1], x, y), fill=(83, 130, 165), width=3)
        rounded_rect(draw, (x - 68, y - 34, x + 68, y + 34), 12, (14, 39, 62), outline=(126, 166, 194), width=2)
        tw = draw.textlength(label, font=font_h2)
        draw.text((x - tw / 2, y - 16), label, font=font_h2, fill=(245, 248, 250))
    draw.text((88, 115), scene["title"], font=font_scene, fill=(255, 255, 255))
    draw_bullets(draw, scene["bullets"], 88, 180)


def draw_bullets(draw, bullets, x, y):
    for b in bullets:
        draw.ellipse((x, y + 8, x + 10, y + 18), fill=(201, 155, 60))
        draw.text((x + 26, y), b, font=font_body, fill=(225, 235, 242))
        y += 44


def draw_flow(draw, scene, p):
    draw.text((80, 115), scene["title"], font=font_scene, fill=(255, 255, 255))
    labels = ["注册申请", "平台审核", "租户建立", "小区授权", "数据访问"]
    x0 = 105
    for i, label in enumerate(labels):
        x = x0 + i * 225
        rounded_rect(draw, (x, 300, x + 170, 380), 12, (18, 55, 82), outline=(201, 155, 60) if i <= p * 5 else (82, 110, 132), width=2)
        tw = draw.textlength(label, font=font_h2)
        draw.text((x + 85 - tw / 2, 326), label, font=font_h2, fill=(255, 255, 255))
        if i < len(labels) - 1:
            draw.line((x + 175, 340, x + 218, 340), fill=(201, 155, 60), width=4)
            draw.polygon([(x + 218, 340), (x + 205, 331), (x + 205, 349)], fill=(201, 155, 60))
    draw_bullets(draw, scene["bullets"], 120, 440)


def draw_dashboard(draw, scene, p):
    draw.text((80, 105), scene["title"], font=font_scene, fill=(255, 255, 255))
    cards = [("公共收益", "98.6%", 110, 210), ("对账匹配", "96.2%", 390, 210), ("风险预警", "12", 670, 210), ("审计日志", "全留痕", 950, 210)]
    for name, val, x, y in cards:
        rounded_rect(draw, (x, y, x + 220, y + 120), 10, (248, 251, 253), outline=(191, 205, 217))
        draw.text((x + 22, y + 22), name, font=font_small, fill=(48, 68, 86))
        draw.text((x + 22, y + 55), val, font=font_scene, fill=(23, 50, 77))
    rounded_rect(draw, (110, 370, 1170, 505), 10, (17, 49, 75), outline=(85, 119, 147))
    for i in range(8):
        x = 150 + i * 125
        h = int(35 + 60 * abs(math.sin(i + p * math.pi)))
        draw.rectangle((x, 485 - h, x + 55, 485), fill=(201, 155, 60))
    draw_bullets(draw, scene["bullets"], 110, 540)


def draw_cards(draw, scene, p):
    draw.text((80, 105), scene["title"], font=font_scene, fill=(255, 255, 255))
    xs = [105, 365, 625, 885]
    for i, b in enumerate(scene["bullets"]):
        rounded_rect(draw, (xs[i], 245, xs[i] + 210, 425), 12, (245, 249, 252), outline=(201, 155, 60), width=2)
        draw.text((xs[i] + 34, 285), f"0{i+1}", font=font_scene, fill=(201, 155, 60))
        draw_text_block(draw, b, xs[i] + 34, 345, font_h2, (23, 50, 77), 150)
    draw_text_block(draw, scene["tag"], 120, 480, font_body, (220, 232, 240), 1000)


def draw_phone(draw, scene, p):
    draw.text((80, 105), scene["title"], font=font_scene, fill=(255, 255, 255))
    rounded_rect(draw, (800, 135, 1040, 560), 32, (238, 243, 247), outline=(44, 66, 84), width=4)
    rounded_rect(draw, (825, 190, 1015, 248), 10, (23, 50, 77))
    draw.text((852, 206), "我的房屋", font=font_body, fill=(255, 255, 255))
    for i, b in enumerate(scene["bullets"]):
        y = 285 + i * 54
        rounded_rect(draw, (828, y, 1012, y + 38), 7, (255, 255, 255), outline=(205, 216, 225))
        draw.text((846, y + 8), b, font=font_small, fill=(35, 55, 70))
    draw_bullets(draw, scene["bullets"], 110, 240)


def draw_ledger(draw, scene, p):
    draw.text((80, 105), scene["title"], font=font_scene, fill=(255, 255, 255))
    labels = scene["bullets"]
    for i, label in enumerate(labels):
        x = 110 + i * 215
        y = 315 + int(18 * math.sin(p * math.pi * 2 + i))
        rounded_rect(draw, (x, y, x + 165, y + 70), 10, (16, 48, 75), outline=(201, 155, 60), width=2)
        tw = draw.textlength(label, font=font_h2)
        draw.text((x + 82 - tw / 2, y + 20), label, font=font_h2, fill=(255, 255, 255))
        if i < len(labels) - 1:
            draw.line((x + 170, y + 35, x + 205, y + 35), fill=(201, 155, 60), width=4)
    draw_text_block(draw, scene["tag"], 120, 455, font_body, (220, 232, 240), 980)


def draw_formula(draw, scene, p):
    draw.text((80, 105), scene["title"], font=font_scene, fill=(255, 255, 255))
    rounded_rect(draw, (120, 245, 1160, 330), 10, (201, 155, 60), outline=None)
    draw.text((190, 270), "用户 × 租户 × 角色 × 小区 × 数据范围 × 操作 = 最终权限", font=font_h2, fill=(12, 30, 47))
    draw_bullets(draw, scene["bullets"], 180, 390)


def draw_timeline(draw, scene, p):
    draw.text((80, 105), scene["title"], font=font_scene, fill=(255, 255, 255))
    y = 330
    draw.line((150, y, 1130, y), fill=(201, 155, 60), width=5)
    for i, b in enumerate(scene["bullets"]):
        x = 150 + i * 245
        draw.ellipse((x - 24, y - 24, x + 24, y + 24), fill=(201, 155, 60))
        draw.text((x - 12, y - 16), str(i + 1), font=font_body, fill=(12, 30, 47))
        draw_text_block(draw, b, x - 62, y + 52, font_h2, (245, 248, 250), 150)


def draw_closing(draw, scene, p):
    draw.text((90, 150), scene["title"], font=font_title, fill=(255, 255, 255))
    draw_text_block(draw, scene["tag"], 92, 250, font_body, (220, 232, 240), 1040)
    for i, b in enumerate(scene["bullets"]):
        x = 110 + (i % 5) * 220
        y = 390
        rounded_rect(draw, (x, y, x + 170, y + 76), 14, (245, 249, 252), outline=(201, 155, 60), width=2)
        tw = draw.textlength(b, font=font_h2)
        draw.text((x + 85 - tw / 2, y + 22), b, font=font_h2, fill=(23, 50, 77))


DRAWERS = {
    "hero": draw_hero,
    "network": draw_network,
    "flow": draw_flow,
    "dashboard": draw_dashboard,
    "cards": draw_cards,
    "phone": draw_phone,
    "ledger": draw_ledger,
    "formula": draw_formula,
    "timeline": draw_timeline,
    "closing": draw_closing,
}


def render_frame(scene_idx, local_frame, scene_frames, global_frame):
    scene = SCENES[scene_idx]
    p = min(1, local_frame / max(1, scene_frames - 1))
    img = background(global_frame)
    draw = ImageDraw.Draw(img, "RGBA")
    for i in range(18):
        x = int((i * 173 + global_frame * (0.7 + i * 0.03)) % (W + 220) - 110)
        y = 95 + (i * 37) % 520
        draw.line((x, y, x + 150, y - 46), fill=(42, 87, 118, 90), width=2)
    DRAWERS[scene["mode"]](draw, scene, p)
    draw_common(draw, scene_idx, p, scene)
    return np.asarray(img)


def make_video():
    writer = imageio.get_writer(
        RAW_VIDEO,
        fps=FPS,
        codec="libx264",
        quality=7,
        macro_block_size=16,
        ffmpeg_log_level="error",
        output_params=["-pix_fmt", "yuv420p"],
    )
    frame_no = 0
    for idx, scene in enumerate(SCENES):
        count = int(scene["dur"] * FPS)
        for lf in range(count):
            writer.append_data(render_frame(idx, lf, count, frame_no))
            frame_no += 1
    writer.close()


def make_audio():
    sample_rate = 44100
    n = int(DURATION * sample_rate)
    t = np.linspace(0, DURATION, n, endpoint=False)
    audio = np.zeros(n, dtype=np.float32)
    chords = [
        (146.83, 220.00, 293.66),
        (130.81, 196.00, 261.63),
        (164.81, 246.94, 329.63),
        (110.00, 220.00, 277.18),
    ]
    seg = 15
    for i, chord in enumerate(chords * 4):
        start = i * seg
        end = min(DURATION, start + seg)
        mask = (t >= start) & (t < end)
        env_t = (t[mask] - start) / max(1, end - start)
        env = np.minimum(1, env_t * 4) * np.minimum(1, (1 - env_t) * 4)
        pad = sum(np.sin(2 * np.pi * f * t[mask]) for f in chord) / len(chord)
        audio[mask] += 0.12 * env * pad
    pulse = 0.05 * np.sin(2 * np.pi * 55 * t) * (0.5 + 0.5 * np.sin(2 * np.pi * 0.5 * t))
    audio += pulse
    audio = np.clip(audio, -0.35, 0.35)
    data = (audio * 32767).astype(np.int16)
    with wave.open(str(AUDIO), "wb") as wf:
        wf.setnchannels(1)
        wf.setsampwidth(2)
        wf.setframerate(sample_rate)
        wf.writeframes(data.tobytes())


def mux():
    ffmpeg = imageio_ffmpeg.get_ffmpeg_exe()
    cmd = [
        ffmpeg,
        "-y",
        "-i",
        str(RAW_VIDEO),
        "-i",
        str(AUDIO),
        "-c:v",
        "copy",
        "-c:a",
        "aac",
        "-b:a",
        "160k",
        "-shortest",
        str(FINAL_VIDEO),
    ]
    subprocess.run(cmd, check=True)


if __name__ == "__main__":
    make_video()
    make_audio()
    mux()
    print(FINAL_VIDEO)
