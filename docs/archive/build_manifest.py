# -*- coding: utf-8 -*-
"""
把 ArcClassifierTest#previewFolder 产出的预览 TSV 整理成:
  1) manifest.json  —— 供 import_archive.py 导入的确认清单
  2) 预览 xlsx      —— 给人看的「分类与命名预览」(汇总 + 明细,低把握度的行标黄)
用法:python -X utf8 build_manifest.py <preview.tsv> <taxonomy.json> <manifest.json> <preview.xlsx> [本地资料根目录,用于识别内容完全相同的重复文件]
"""
import hashlib, json, os, re, sys, collections
from openpyxl import Workbook
from openpyxl.styles import Alignment, Font, PatternFill
from openpyxl.utils import get_column_letter

SECRET = {"1": "公开", "2": "内部", "3": "机密"}
SKIP_EXT = {"zip", "rar", "7z"}


def skip(rel):
    low = rel.lower()
    name = rel.split("/")[-1]
    if rel.count("/") == 0 and name.endswith(".zip"):
        return "压缩包(与文件夹内容重复)"
    if low.rsplit(".", 1)[-1] in SKIP_EXT:
        return "压缩包(内含的文件已单独逐个录入或可另行解压)"
    if "占位说明" in rel or "/99-历史与辅助资料/" in rel:
        return "占位说明/历史辅助文件,不属于业务资料"
    if name.lower().startswith("readme"):
        return "使用说明文件,不属于业务资料"
    return None


def seg_clean(s):
    s = re.sub(r"^\d{1,2}[、.\-\s]+", "", s)
    s = s.replace("（", "(").replace("）", ")")
    return s.strip()


def ext_of(path):
    return path.rsplit(".", 1)[-1].lower() if "." in path.split("/")[-1] else ""


def useful_segments(it):
    """从近到远列出可用来区分的上级文件夹名(去掉纯期间/日期类、已含在名称里的)。"""
    out = []
    for seg in reversed(it["path"].split("/")[:-1]):
        c = seg_clean(seg)
        if not c or re.fullmatch(r"[\d.\-年月日~至和及号 ]+", c) or re.fullmatch(r"20\d{2}[.\-]?\d{0,2}月?", c):
            continue
        if c in it["name"]:
            continue
        out.append(c)
    return out


def disambiguate(items):
    """同名同格式(扩展名)时,依次追加最近的上级文件夹名直到唯一;仍重复则加序号。
    同名但格式不同(如 xlsx + pdf 同一份通知单)不算重名。"""
    def key(it):
        return (it["name"], ext_of(it["path"]))
    by = collections.defaultdict(list)
    for it in items:
        by[key(it)].append(it)
    for (name, _), group in by.items():
        if len(group) < 2:
            continue
        segs = [useful_segments(it) for it in group]
        for depth in range(1, 4):
            tags = ["-".join(reversed(sg[:depth])) for sg in segs]
            if all(tags) and len(set(tags)) == len(tags):
                for it, t in zip(group, tags):
                    it["name"] = (name + "-" + t)[:100]
                break
    seen = collections.Counter(key(it) for it in items)
    idx = collections.Counter()
    for it in items:
        k = key(it)
        if seen[k] > 1:
            idx[k] += 1
            it["name"] = "%s-%02d" % (it["name"], idx[k])


def main():
    tsv, tax, out_manifest, out_xlsx = sys.argv[1:5]
    root = sys.argv[5] if len(sys.argv) > 5 else None
    cats = {c["code"]: c for c in json.load(open(tax, encoding="utf-8"))["categories"]}
    top_name = {c["code"]: c["name"] for c in cats.values() if not c["parent"]}

    def label(code):
        c = cats.get(code)
        return "%s %s › %s %s" % (c["parent"], top_name.get(c["parent"], ""), code, c["name"]) if c and c["parent"] else code

    items, skipped = [], []
    seen_hash = {}
    for line in open(tsv, encoding="utf-8"):
        f = line.rstrip("\n").split("\t")
        rel, name, code, dtype, secret, conf, ddate, tlen, reasons, period, ver = (f + [""] * 11)[:11]
        why = skip(rel)
        if not why and root:
            h = hashlib.sha256(open(os.path.join(root, rel.replace("/", os.sep)), "rb").read()).hexdigest()
            if h in seen_hash:
                why = "与「%s」内容完全相同,只录一份" % seen_hash[h]
            else:
                seen_hash[h] = rel
        if why:
            skipped.append((rel, why))
            continue
        items.append(dict(path=rel, name=name, categoryCode=code, docType=dtype, secretLevel=int(secret),
                          docDate=None if ddate in ("None", "") else ddate,
                          period=None if period in ("None", "") else period,
                          versionLabel=None if ver in ("None", "") else ver,
                          _conf=int(conf), _reasons=reasons, _tlen=int(tlen)))
    disambiguate(items)

    manifest = [{k: v for k, v in it.items() if not k.startswith("_")} for it in items]
    json.dump(manifest, open(out_manifest, "w", encoding="utf-8"), ensure_ascii=False, indent=1)

    wb = Workbook()
    ws = wb.active
    ws.title = "汇总"
    ws.append(["门类 / 子类", "份数"])
    cnt = collections.Counter(it["categoryCode"] for it in items)
    for top in sorted(top_name):
        n = sum(v for k, v in cnt.items() if k.startswith(top + "."))
        if not n:
            continue
        ws.append(["%s %s" % (top, top_name[top]), n])
        ws.cell(ws.max_row, 1).font = Font(bold=True)
        ws.cell(ws.max_row, 2).font = Font(bold=True)
        for k in sorted(c for c in cnt if c.startswith(top + ".")):
            ws.append(["    %s %s" % (k, cats[k]["name"]), cnt[k]])
    ws.append([])
    ws.append(["合计录入", len(items)])
    ws.append(["不录入(压缩包/占位说明等)", len(skipped)])
    low = sum(1 for it in items if it["_conf"] < 50)
    ws.append(["把握度低、建议人工看一眼", low])
    ws.column_dimensions["A"].width = 46

    ws2 = wb.create_sheet("明细")
    head = ["序号", "原位置(文件夹/文件名)", "新名称", "分类", "类型", "密级", "文件日期", "把握度", "判断依据"]
    ws2.append(head)
    yellow = PatternFill("solid", fgColor="FFF2CC")
    for i, it in enumerate(items, 1):
        ws2.append([i, it["path"], it["name"], label(it["categoryCode"]), it["docType"], SECRET[str(it["secretLevel"])],
                    it["docDate"] or "", "%d%%" % it["_conf"], it["_reasons"]])
        if it["_conf"] < 50:
            for c in range(1, len(head) + 1):
                ws2.cell(ws2.max_row, c).fill = yellow
    for c in range(1, len(head) + 1):
        ws2.cell(1, c).font = Font(bold=True)
    for col, w in zip("ABCDEFGHI", [6, 70, 52, 40, 10, 8, 12, 8, 50]):
        ws2.column_dimensions[col].width = w
    ws2.freeze_panes = "A2"
    ws2.auto_filter.ref = "A1:I%d" % ws2.max_row

    ws3 = wb.create_sheet("不录入")
    ws3.append(["文件", "原因"])
    for rel, why in skipped:
        ws3.append([rel, why])
    ws3.column_dimensions["A"].width = 90
    ws3.column_dimensions["B"].width = 40
    wb.save(out_xlsx)
    print("录入", len(items), "跳过", len(skipped), "低把握", low)


if __name__ == "__main__":
    main()
