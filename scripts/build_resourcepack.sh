#!/bin/bash
# 通貨テクスチャのリソースパック（resourcepack/）を zip にし、sha1 を表示する。
# 出力: target/TofuNomics-resourcepack.zip
# 中身が同じなら毎回同じ sha1 になるよう、ファイルの順番と時刻を固定して詰める。
#
# 使い方: ./scripts/build_resourcepack.sh
# 出てきた sha1 を config/system.yml の resource_pack.sha1 に書き、zip を url の場所に置く
set -euo pipefail
cd "$(dirname "$0")/.."
mkdir -p target
python3 -I - <<'PY'
import hashlib, zipfile
from pathlib import Path

root = Path("resourcepack")
out = Path("target/TofuNomics-resourcepack.zip")
files = [root / "pack.mcmeta"] + sorted(p for p in (root / "assets").rglob("*") if p.is_file() and p.name != ".DS_Store")
with zipfile.ZipFile(out, "w", zipfile.ZIP_DEFLATED) as z:
    for p in files:
        info = zipfile.ZipInfo(p.relative_to(root).as_posix(), date_time=(2026, 1, 1, 0, 0, 0))
        info.compress_type = zipfile.ZIP_DEFLATED
        z.writestr(info, p.read_bytes())
print(out)
print("sha1:", hashlib.sha1(out.read_bytes()).hexdigest())
PY
