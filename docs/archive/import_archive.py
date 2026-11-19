# -*- coding: utf-8 -*-
"""
批量把本地文件夹里的资料导入「资料管理」。
流程与网页上传完全一致:/file/upload → /archive/doc/analyze(取内容摘要、文件指纹、查重)→ /archive/doc(归档)。
名称/分类等取自「确认清单」manifest.json(由预览生成、经人工确认),清单里没有的文件会跳过。

用法(token 从浏览器 localStorage 的 zhyq_token 取,登录过期要重新取):
  python -X utf8 import_archive.py --base https://zhyq.aoleplat.com/api --root <本地文件夹> \
      --manifest manifest.json --project-id 3 --token <TOKEN> [--limit 5] [--dry-run]
manifest.json: [{"path": "相对root的路径", "name": "...", "categoryCode": "03.04", "docType": "合同协议",
                 "secretLevel": 3, "docDate": "2026-09-14"|null, "period": "...", "versionLabel": "...", "counterparty": "..."}]
可重复运行:内容已存在(文件指纹相同)的会自动跳过。
"""
import argparse, json, mimetypes, os, sys, time, uuid
import urllib.request, urllib.error


def http(method, url, token, body=None, headers=None, timeout=600):
    h = {"Authorization": "Bearer " + token}
    h.update(headers or {})
    req = urllib.request.Request(url, data=body, method=method, headers=h)
    try:
        with urllib.request.urlopen(req, timeout=timeout) as r:
            return json.loads(r.read().decode("utf-8"))
    except urllib.error.HTTPError as e:
        raw = e.read().decode("utf-8", "replace")
        raise RuntimeError("HTTP %s %s" % (e.code, raw[:300]))


def call(method, url, token, obj=None, timeout=600):
    body = json.dumps(obj, ensure_ascii=False).encode("utf-8") if obj is not None else None
    res = http(method, url, token, body, {"Content-Type": "application/json; charset=utf-8"} if body else None, timeout)
    if res.get("code") != 0:
        raise RuntimeError("%s %s -> %s" % (method, url, res.get("message")))
    return res.get("data")


def upload(base, token, path, biz_type="archive_doc"):
    boundary = "----zhyq" + uuid.uuid4().hex
    fname = os.path.basename(path)
    ctype = mimetypes.guess_type(fname)[0] or "application/octet-stream"
    parts = []
    parts.append(("--%s\r\nContent-Disposition: form-data; name=\"bizType\"\r\n\r\n%s\r\n" % (boundary, biz_type)).encode("utf-8"))
    parts.append(("--%s\r\nContent-Disposition: form-data; name=\"file\"; filename=\"%s\"\r\nContent-Type: %s\r\n\r\n"
                  % (boundary, fname.replace('"', "'"), ctype)).encode("utf-8"))
    with open(path, "rb") as f:
        parts.append(f.read())
    parts.append(("\r\n--%s--\r\n" % boundary).encode("utf-8"))
    res = http("POST", base + "/file/upload", token, b"".join(parts),
               {"Content-Type": "multipart/form-data; boundary=" + boundary}, timeout=1200)
    if res.get("code") != 0:
        raise RuntimeError("upload failed: %s" % res.get("message"))
    return res["data"]


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--base", required=True)
    ap.add_argument("--root", required=True)
    ap.add_argument("--manifest", required=True)
    ap.add_argument("--project-id", type=int, required=True)
    ap.add_argument("--token", default=os.environ.get("ZHYQ_TOKEN"))
    ap.add_argument("--limit", type=int, default=0)
    ap.add_argument("--dry-run", action="store_true")
    a = ap.parse_args()
    if not a.token:
        sys.exit("缺少 token(--token 或环境变量 ZHYQ_TOKEN)")
    base = a.base.rstrip("/")
    items = json.load(open(a.manifest, encoding="utf-8"))
    if a.limit:
        items = items[: a.limit]
    ok = skipped = failed = 0
    for i, it in enumerate(items, 1):
        full = os.path.join(a.root, it["path"].replace("/", os.sep))
        if os.name == "nt" and not full.startswith("\\\\?\\"):  # 路径很长时(>260 字符)需要扩展长度前缀
            full = "\\\\?\\" + os.path.abspath(full)
        tag = "[%d/%d] %s" % (i, len(items), it["path"])
        if not os.path.isfile(full):
            print(tag, "-> 文件不存在,跳过"); failed += 1; continue
        if a.dry_run:
            print(tag, "->", it["name"], it["categoryCode"]); continue
        file_id = None
        try:
            sf = upload(base, a.token, full)
            file_id = sf["id"]
            an = call("POST", base + "/archive/doc/analyze", a.token, {
                "fileId": file_id, "sourcePath": os.path.dirname(it["path"]),
                "lastModified": int(os.path.getmtime(full) * 1000)}, timeout=300)
            if an.get("duplicate"):
                call("DELETE", base + "/file/%d" % file_id, a.token)
                print(tag, "-> 已存在(%s),跳过" % an["duplicate"]["archiveNo"]); skipped += 1; continue
            doc = {
                "projectId": a.project_id, "fileId": file_id, "name": it["name"], "categoryCode": it["categoryCode"],
                "docType": it.get("docType"), "secretLevel": it.get("secretLevel"), "docDate": it.get("docDate"),
                "period": it.get("period"), "versionLabel": it.get("versionLabel"), "counterparty": it.get("counterparty"),
                "summary": an.get("summary"), "fileHash": an.get("fileHash"),
                "sourcePath": it["path"], "remark": it.get("remark"),
            }
            saved = call("POST", base + "/archive/doc", a.token, doc)
            print(tag, "->", saved["archiveNo"], it["name"]); ok += 1
        except Exception as e:  # noqa: BLE001 逐个文件容错,最后汇总
            failed += 1
            print(tag, "-> 失败:", e)
            if file_id is not None:
                try: call("DELETE", base + "/file/%d" % file_id, a.token)
                except Exception: pass
            if "401" in str(e) or "未登录" in str(e):
                print("登录已过期,请重新取 token 后再次运行(已导入的会自动跳过)"); break
        time.sleep(0.05)
    print("完成:成功 %d,已存在跳过 %d,失败 %d" % (ok, skipped, failed))


if __name__ == "__main__":
    main()
