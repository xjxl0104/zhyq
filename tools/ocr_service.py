"""本地 OCR 服务：RapidOCR + PyMuPDF，支持中文图片与 PDF。"""
from io import BytesIO
from typing import Any

import fitz
from fastapi import FastAPI, File, UploadFile
from fastapi.middleware.cors import CORSMiddleware
from PIL import Image
from rapidocr_onnxruntime import RapidOCR

app = FastAPI(title="ZHYQ Local OCR")
app.add_middleware(CORSMiddleware, allow_origins=["*"], allow_methods=["*"], allow_headers=["*"])
engine = RapidOCR()


def recognize_image(data: bytes) -> list[dict[str, Any]]:
    result, _ = engine(data)
    rows = []
    for box, text, score in result or []:
        rows.append({"text": text, "confidence": round(float(score), 4), "box": box})
    return rows


@app.get("/health")
def health() -> dict[str, str]:
    return {"status": "ok", "engine": "rapidocr-onnxruntime"}


@app.post("/ocr")
async def ocr(file: UploadFile = File(...)) -> dict[str, Any]:
    data = await file.read()
    name = (file.filename or "").lower()
    pages: list[dict[str, Any]] = []
    if name.endswith(".pdf") or file.content_type == "application/pdf":
        doc = fitz.open(stream=data, filetype="pdf")
        try:
            for index, page in enumerate(doc):
                # 200 DPI renders清晰度与速度平衡，适合账单与合同扫描件
                pix = page.get_pixmap(matrix=fitz.Matrix(200 / 72, 200 / 72), alpha=False)
                pages.append({"page": index + 1, "items": recognize_image(pix.tobytes("png"))})
        finally:
            doc.close()
    else:
        Image.open(BytesIO(data)).verify()
        pages.append({"page": 1, "items": recognize_image(data)})
    text = "\n".join(item["text"] for page in pages for item in page["items"])
    return {"filename": file.filename, "text": text, "pages": pages}
