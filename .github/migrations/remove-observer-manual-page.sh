#!/usr/bin/env bash
set -euo pipefail

python3 - <<'PY'
from pathlib import Path
import json

manual = Path('src/main/java/dev/totem/vanillatweaks/manual/VanillaTweaksManual.java')
text = manual.read_text()
text = text.replace(
    '                    "book.totem.vanilla_tweaks_manual.page.3",\n'
    '                    "book.totem.vanilla_tweaks_manual.page.4"',
    '                    "book.totem.vanilla_tweaks_manual.page.3"'
)
manual.write_text(text)

lang_dir = Path('src/main/resources/assets/totem/lang')
for path in lang_dir.glob('*.json'):
    data = json.loads(path.read_text())
    data.pop('book.totem.vanilla_tweaks_manual.page.4', None)
    path.write_text(json.dumps(data, ensure_ascii=False, indent=2) + '\n')
PY

! grep -R -n '/observeui' src/main
! grep -R -n 'vanilla_tweaks_manual.page.4' src/main
