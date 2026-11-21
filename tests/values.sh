#!/bin/bash
set -euo pipefail
HERE="$(cd "$(dirname "$0")" && pwd)"
WORK="$(mktemp -d "${TMPDIR:?Set TMPDIR to an approved work directory}/despia-ads.XXXXXX")"
trap 'rm -rf "$WORK"' EXIT
python3 - "$HERE/../ios/app/TikTokAdsExtension.swift" "$WORK/Values.swift" <<'PYTHON'
from pathlib import Path
import sys
text=Path(sys.argv[1]).read_text()
Path(sys.argv[2]).write_text('import Foundation\n'+text[text.index('enum TikTokAdsValues {'):])
PYTHON
cp "$HERE/values.swift" "$WORK/main.swift"
swiftc -swift-version 5 "$WORK/Values.swift" "$WORK/main.swift" -o "$WORK/values"
"$WORK/values"
