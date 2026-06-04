from __future__ import annotations

import math
import subprocess
import sys
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
VIDEO = OUT_DIR / "SPARK_Nexus_UI_无声.mp4"
NARRATION = OUT_DIR / "SPARK_Nexus_中文旁白.mp3"
FINAL = BASE / "领码科技SPARK_Nexus城市物业治理中枢_3分钟宣传片_界面旁白版.mp4"

FONT = r"C:\Windows\Fonts\msyh.ttc"
FONT_BOLD = r"C:\Windows\Fonts\msyhbd.ttc"
f12 = ImageFont.truetype(FONT, 12)
f14 = ImageFont.truetype(FONT, 14)
f16 = ImageFont.truetype(FONT, 16)
f18 = ImageFont.truetype(FONT, 18)
f20 = ImageFont.truetype(FONT, 20)
f22 = ImageFont.truetype(FONT_BOLD, 22)
f24 = ImageFont.truetype(FONT_BOLD, 24)
f28 = ImageFont.truetype(FONT_BOLD, 28)
f34 = ImageFont.truetype(FONT_BOLD, 34)
f44 = ImageFont.truetype(FONT_BOLD, 44)


SCENES = [
    (18, "平台登录与租户入口", "login"),
    (22, "政府监管驾驶舱", "dashboard"),
    (22, "多租户注册审核", "registration"),
    (22, "物业多小区运营台", "operations"),
    (22, "银行流水与自动对账", "bank"),
    (22, "住户端服务小程序", "resident"),
    (22, "权限、授权与审计", "audit"),
    (30, "城市物业治理中枢", "closing"),
]


def rect(draw, box, fill, outline=None, width=1, r=0):
    if r:
        draw.rounded_rectangle(box, r, fill=fill, outline=outline, width=width)
    else:
        draw.rectangle(box, fill=fill, outline=outline, width=width)


def line_text(draw, xy, text, font, fill):
    draw.text(xy, text, font=font, fill=fill)


def fit_text(draw, text, font, max_width):
    out, cur = [], ""
    for ch in text:
        if draw.textlength(cur + ch, font=font) <= max_width:
            cur += ch
        else:
            if cur:
                out.append(cur)
            cur = ch
    if cur:
        out.append(cur)
    return out


def bg(t):
    x = np.linspace(0, 1, W)[None, :]
    y = np.linspace(0, 1, H)[:, None]
    arr = np.zeros((H, W, 3), dtype=np.uint8)
    arr[..., 0] = (232 - 30 * y + 5 * x).astype(np.uint8)
    arr[..., 1] = (238 - 18 * y + 3 * x).astype(np.uint8)
    arr[..., 2] = (242 - 8 * y + 8 * x).astype(np.uint8)
    wave = (5 * np.sin((x * 4 + y * 3 + t * 0.015) * math.pi)).astype(np.int16)
    return Image.fromarray(np.clip(arr.astype(np.int16) + wave[..., None], 0, 255).astype(np.uint8))


def top_bar(draw, title, idx):
    rect(draw, (0, 0, W, 72), (10, 31, 51))
    line_text(draw, (44, 22), "领码科技 SPARK Nexus", f22, (255, 255, 255))
    line_text(draw, (283, 25), "城市物业治理中枢", f16, (178, 196, 211))
    line_text(draw, (1040, 25), f"{idx + 1:02d} / {len(SCENES):02d}", f16, (211, 166, 63))
    rect(draw, (44, 688, 1236, 694), (206, 216, 224), r=3)
    rect(draw, (44, 688, 44 + int(1192 * (idx + 1) / len(SCENES)), 694), (211, 166, 63), r=3)
    line_text(draw, (44, 94), title, f28, (18, 42, 62))


def browser(draw, x, y, w, h, title):
    rect(draw, (x, y, x + w, y + h), (255, 255, 255), outline=(188, 202, 214), width=1, r=12)
    rect(draw, (x, y, x + w, y + 44), (246, 249, 251), outline=(218, 226, 232), width=1, r=12)
    for i, c in enumerate([(230, 90, 90), (236, 180, 75), (88, 180, 110)]):
        draw.ellipse((x + 18 + i * 22, y + 16, x + 30 + i * 22, y + 28), fill=c)
    rect(draw, (x + 110, y + 12, x + w - 24, y + 32), (255, 255, 255), outline=(226, 232, 238), r=8)
    line_text(draw, (x + 124, y + 15), title, f12, (90, 106, 120))


def sidebar(draw, x, y, h, active):
    rect(draw, (x, y, x + 178, y + h), (13, 37, 58))
    items = ["监管驾驶舱", "注册审核", "小区运营", "银行对账", "住户服务", "权限审计"]
    for i, it in enumerate(items):
        yy = y + 72 + i * 48
        if it == active:
            rect(draw, (x + 14, yy, x + 164, yy + 34), (211, 166, 63), r=8)
            color = (19, 35, 48)
        else:
            color = (190, 204, 216)
        line_text(draw, (x + 34, yy + 8), it, f14, color)


def card(draw, box, title, value, note="", accent=(31, 96, 140)):
    rect(draw, box, (255, 255, 255), outline=(222, 230, 236), r=8)
    x1, y1, x2, y2 = box
    line_text(draw, (x1 + 18, y1 + 16), title, f14, (82, 99, 114))
    line_text(draw, (x1 + 18, y1 + 42), value, f28, accent)
    if note:
        line_text(draw, (x1 + 18, y2 - 28), note, f12, (112, 128, 140))


def table(draw, x, y, w, rows, cols):
    row_h = 38
    rect(draw, (x, y, x + w, y + row_h), (18, 49, 75), r=6)
    col_w = w // len(cols)
    for i, c in enumerate(cols):
        line_text(draw, (x + i * col_w + 14, y + 11), c, f14, (255, 255, 255))
    for r, row in enumerate(rows):
        yy = y + row_h + r * row_h
        rect(draw, (x, yy, x + w, yy + row_h), (250, 252, 253) if r % 2 else (255, 255, 255), outline=(226, 232, 238))
        for i, cell in enumerate(row):
            color = (25, 50, 70)
            if cell in ("待审核", "差异", "预警"):
                color = (185, 90, 38)
            if cell in ("已匹配", "通过", "正常"):
                color = (25, 130, 95)
            line_text(draw, (x + i * col_w + 14, yy + 10), cell, f14, color)


def draw_login(draw, p):
    browser(draw, 96, 138, 1088, 468, "spark-nexus.local/login")
    rect(draw, (454, 206, 826, 538), (255, 255, 255), outline=(220, 229, 235), r=10)
    line_text(draw, (510, 250), "SPARK Nexus", f34, (18, 42, 62))
    line_text(draw, (510, 292), "城市物业治理中枢", f18, (98, 115, 130))
    for i, label in enumerate(["账号", "密码", "租户类型"]):
        y = 338 + i * 56
        line_text(draw, (510, y), label, f14, (82, 98, 112))
        rect(draw, (572, y - 8, 770, y + 28), (248, 251, 253), outline=(210, 221, 230), r=6)
    line_text(draw, (590, 330), "gov-supervisor", f14, (38, 61, 80))
    line_text(draw, (590, 386), "********", f14, (38, 61, 80))
    line_text(draw, (590, 442), "政府监管部门", f14, (38, 61, 80))
    rect(draw, (510, 486, 770, 526), (211, 166, 63), r=7)
    line_text(draw, (602, 496), "进入平台", f16, (18, 42, 62))
    line_text(draw, (910, 250), "注册制入口", f22, (18, 42, 62))
    for i, t in enumerate(["物业机构注册", "银行服务申请", "业委会备案", "住户房屋绑定"]):
        rect(draw, (904, 292 + i * 48, 1104, 326 + i * 48), (247, 250, 252), outline=(218, 228, 236), r=7)
        line_text(draw, (926, 301 + i * 48), t, f14, (40, 67, 88))


def draw_dashboard(draw, p):
    browser(draw, 64, 132, 1152, 500, "spark-nexus.local/gov/dashboard")
    sidebar(draw, 64, 176, 456, "监管驾驶舱")
    x0, y0 = 270, 198
    for i, data in enumerate([("监管小区", "126", "+8 本月"), ("公共收益余额", "¥ 2.86亿", "三方监管"), ("对账匹配率", "98.7%", "今日 3 笔差异"), ("风险预警", "12", "待处置")]):
        card(draw, (x0 + i * 220, y0, x0 + i * 220 + 196, y0 + 104), *data, accent=(31, 96, 140) if i != 3 else (188, 82, 43))
    rect(draw, (270, 330, 782, 570), (255, 255, 255), outline=(222, 230, 236), r=8)
    line_text(draw, (292, 350), "公共收益收支趋势", f18, (18, 42, 62))
    pts = []
    for i in range(10):
        x = 310 + i * 46
        y = 510 - int(80 + 42 * math.sin(i * .7 + p * 3))
        pts.append((x, y))
    draw.line(pts, fill=(31, 96, 140), width=4)
    for x, y in pts:
        draw.ellipse((x - 4, y - 4, x + 4, y + 4), fill=(211, 166, 63))
    table(draw, 812, 330, 340, [["银行对账", "3", "预警"], ["工单投诉", "18", "正常"], ["支出审批", "6", "待审核"], ["审计导出", "42", "正常"]], ["事项", "数量", "状态"])


def draw_registration(draw, p):
    browser(draw, 64, 132, 1152, 500, "spark-nexus.local/registration")
    sidebar(draw, 64, 176, 456, "注册审核")
    line_text(draw, (288, 198), "注册申请审核", f22, (18, 42, 62))
    table(draw, 288, 246, 850, [
        ["REG-20260601-0001", "武汉新城物业", "物业机构", "待审核"],
        ["REG-20260601-0002", "建设银行关山支行", "银行服务", "待审核"],
        ["REG-20260601-0003", "张女士", "住户房屋绑定", "通过"],
        ["REG-20260601-0004", "鼎盛阳光二期业委会", "业委会备案", "通过"],
    ], ["申请编号", "申请主体", "申请类型", "状态"])
    rect(draw, (820, 430, 1138, 570), (247, 250, 252), outline=(220, 229, 235), r=8)
    line_text(draw, (844, 452), "审核后自动生成", f18, (18, 42, 62))
    for i, t in enumerate(["租户 tenant", "用户关系 user_tenant", "小区授权 relation", "数据权限 authorization"]):
        line_text(draw, (852, 488 + i * 22), "• " + t, f14, (57, 82, 102))
    rect(draw, (310, 518, 430, 554), (211, 166, 63), r=7)
    line_text(draw, (342, 527), "通过", f16, (18, 42, 62))
    rect(draw, (448, 518, 568, 554), (244, 247, 249), outline=(210, 221, 230), r=7)
    line_text(draw, (480, 527), "退回", f16, (82, 98, 112))


def draw_operations(draw, p):
    browser(draw, 64, 132, 1152, 500, "spark-nexus.local/property/operations")
    sidebar(draw, 64, 176, 456, "小区运营")
    card(draw, (288, 196, 500, 296), "服务小区", "18", "多项目统一运营")
    card(draw, (522, 196, 734, 296), "本月账单", "8,426", "自动生成")
    card(draw, (756, 196, 968, 296), "工单完成率", "94.8%", "平均 4.2 小时")
    table(draw, 288, 330, 850, [
        ["鼎盛阳光一期", "物业费 2026-06", "¥355.50", "已推送"],
        ["鼎盛阳光二期", "停车费 2026-06", "¥280.00", "已缴费"],
        ["星河湾花园", "电梯广告收益", "¥36,000", "已公示"],
        ["东湖雅苑", "门禁改造工单", "处理中", "正常"],
    ], ["小区", "业务", "金额/状态", "进度"])


def draw_bank(draw, p):
    browser(draw, 64, 132, 1152, 500, "spark-nexus.local/bank/reconciliation")
    sidebar(draw, 64, 176, 456, "银行对账")
    line_text(draw, (288, 198), "银行流水自动对账", f22, (18, 42, 62))
    table(draw, 288, 246, 850, [
        ["PAY20260601001", "¥355.50", "汉口银行", "已匹配"],
        ["PAY20260601002", "¥280.00", "汉口银行", "已匹配"],
        ["REV20260601003", "¥36,000.00", "工商银行", "已匹配"],
        ["EXP20260601004", "¥48,500.00", "建设银行", "差异"],
    ], ["业务单号", "平台金额", "银行渠道", "对账结果"])
    rect(draw, (292, 506, 650, 566), (20, 70, 98), r=8)
    line_text(draw, (318, 526), "资金流：支付订单 → 银行流水 → 对账结果 → 监管驾驶舱", f16, (255, 255, 255))
    rect(draw, (690, 506, 1138, 566), (255, 249, 235), outline=(236, 211, 150), r=8)
    line_text(draw, (716, 526), "差异可追溯到订单、流水、凭证和审批记录", f16, (119, 75, 20))


def draw_resident(draw, p):
    browser(draw, 64, 132, 1152, 500, "spark-nexus.local/resident-preview")
    sidebar(draw, 64, 176, 456, "住户服务")
    rect(draw, (456, 168, 734, 598), (20, 35, 48), r=34)
    rect(draw, (474, 192, 716, 574), (246, 249, 251), r=22)
    rect(draw, (474, 192, 716, 284), (10, 44, 61), r=22)
    line_text(draw, (500, 220), "张女士，您好", f22, (255, 255, 255))
    line_text(draw, (500, 252), "鼎盛阳光一期 1栋1101", f14, (189, 211, 220))
    line_text(draw, (500, 310), "待缴 ¥355.50", f22, (18, 42, 62))
    for i, t in enumerate(["缴费", "账单", "公告", "投票"]):
        rect(draw, (492 + i * 54, 360, 536 + i * 54, 404), (255, 255, 255), outline=(220, 229, 235), r=8)
        line_text(draw, (502 + i * 54, 373), t, f14, (20, 105, 92))
    table(draw, 790, 244, 330, [["物业费", "¥355.50", "待缴"], ["停车费", "¥280.00", "已缴费"], ["广告收益", "¥36,000", "已公示"]], ["事项", "金额", "状态"])
    line_text(draw, (790, 420), "多房屋绑定 · 在线缴费 · 公示投票 · 报修投诉", f18, (18, 42, 62))


def draw_audit(draw, p):
    browser(draw, 64, 132, 1152, 500, "spark-nexus.local/security/audit")
    sidebar(draw, 64, 176, 456, "权限审计")
    line_text(draw, (288, 198), "动态权限计算", f22, (18, 42, 62))
    rect(draw, (288, 244, 1138, 312), (18, 49, 75), r=8)
    line_text(draw, (330, 266), "用户 × 当前租户 × 角色 × 小区关系 × 数据范围 × 操作 = 最终权限", f20, (255, 255, 255))
    table(draw, 288, 352, 850, [
        ["gov-supervisor", "COMMUNITY_PROFILE", "READ", "允许"],
        ["property-admin", "PAYMENT_ORDER", "WRITE", "允许"],
        ["bank-operator", "BANK_FLOW", "READ", "允许"],
        ["resident-user", "OTHER_HOUSE", "READ", "拒绝"],
    ], ["用户", "数据范围", "动作", "结果"])


def draw_closing(draw, p):
    rect(draw, (92, 142, 1188, 582), (10, 31, 51), r=18)
    line_text(draw, (158, 210), "领码科技 SPARK Nexus", f44, (255, 255, 255))
    line_text(draw, (158, 272), "城市物业治理中枢", f34, (211, 166, 63))
    chips = ["政府监管", "物业运营", "小区治理", "住户服务", "银行对账", "权限审计"]
    for i, c in enumerate(chips):
        x = 160 + (i % 3) * 250
        y = 360 + (i // 3) * 72
        rect(draw, (x, y, x + 190, y + 46), (255, 255, 255), r=10)
        line_text(draw, (x + 48, y + 13), c, f18, (18, 42, 62))
    line_text(draw, (158, 520), "把多主体、多账户、多小区、多房屋关系，落到同一套可监管、可运营、可审计的平台界面里。", f18, (205, 218, 228))


DRAW = {
    "login": draw_login,
    "dashboard": draw_dashboard,
    "registration": draw_registration,
    "operations": draw_operations,
    "bank": draw_bank,
    "resident": draw_resident,
    "audit": draw_audit,
    "closing": draw_closing,
}


def frame(scene_idx, local_frame, scene_frames, global_frame):
    dur, title, mode = SCENES[scene_idx]
    p = local_frame / max(1, scene_frames - 1)
    img = bg(global_frame)
    draw = ImageDraw.Draw(img, "RGBA")
    top_bar(draw, title, scene_idx)
    DRAW[mode](draw, p)
    return np.asarray(img)


def render_video():
    writer = imageio.get_writer(
        VIDEO,
        fps=FPS,
        codec="libx264",
        quality=8,
        macro_block_size=16,
        ffmpeg_log_level="error",
        output_params=["-pix_fmt", "yuv420p"],
    )
    g = 0
    for i, (dur, _, _) in enumerate(SCENES):
        count = dur * FPS
        for lf in range(count):
            writer.append_data(frame(i, lf, count, g))
            g += 1
    writer.close()


def mux():
    ffmpeg = imageio_ffmpeg.get_ffmpeg_exe()
    cmd = [
        ffmpeg,
        "-y",
        "-i",
        str(VIDEO),
        "-i",
        str(NARRATION),
        "-map",
        "0:v:0",
        "-map",
        "1:a:0",
        "-c:v",
        "copy",
        "-filter:a",
        "atempo=1.044,loudnorm=I=-16:TP=-1.5:LRA=11,aformat=sample_rates=44100:channel_layouts=stereo",
        "-c:a",
        "aac",
        "-b:a",
        "192k",
        "-t",
        "180",
        "-movflags",
        "+faststart",
        str(FINAL),
    ]
    subprocess.run(cmd, check=True)


if __name__ == "__main__":
    render_video()
    mux()
    print(FINAL)
