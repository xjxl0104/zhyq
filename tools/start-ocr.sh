#!/bin/zsh
set -e
cd "$(dirname "$0")/.."
exec python3 -m uvicorn tools.ocr_service:app --host 127.0.0.1 --port 8765
