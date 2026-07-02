#!/usr/bin/env python3
"""Rebrand Wholphin references to Ndorfin for release 1.0."""
from __future__ import annotations

import re
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]

# Order matters: longer / specific patterns first.
TEXT_REPLACEMENTS = [
    ("WholphinTheme", "NdorfinTheme"),
    ("WholphinApplication", "NdorfinApplication"),
    ("WholphinDreamService", "NdorfinDreamService"),
    ("WholphinRenderersFactory", "NdorfinRenderersFactory"),
    ("WholphinCacheStrategy", "NdorfinCacheStrategy"),
    ("WholphinTestRunner", "NdorfinTestRunner"),
    ("Theme.Wholphin", "Theme.Ndorfin"),
    ("WholphinDefault", "NdorfinDefault"),
    ("WholphinCompact", "NdorfinCompact"),
    ("WholphinServerDiscovery", "NdorfinServerDiscovery"),
    ("Wholphini", "Ndorfin"),
    ("Wholphin has crashed", "Ndorfin has crashed"),
    ("Wholphin Crash Report", "Ndorfin Crash Report"),
    ('"Wholphin (Debug)"', '"Ndorfin (Debug)"'),
    ('ASSET_NAME = "Wholphin"', 'ASSET_NAME = "Ndorfin"'),
    ('DEFAULT_CLIENT = if (BuildConfig.DEBUG) "Wholphin (Debug)" else "Wholphin"',
     'DEFAULT_CLIENT = if (BuildConfig.DEBUG) "Ndorfin (Debug)" else "Ndorfin"'),
    ("damontecres/Wholphin", "maguid28/Wholphin_fork"),
    ('applicationId = "com.github.damontecres.wholphin"', 'applicationId = "com.ndorfin.app"'),
    ('testInstrumentationRunner = "com.github.damontecres.wholphin.test.WholphinTestRunner"',
     'testInstrumentationRunner = "com.github.damontecres.wholphin.test.NdorfinTestRunner"'),
    ('APK_SHORT_NAME: Wholphin.apk', 'APK_SHORT_NAME: Ndorfin.apk'),
    ('rootProject.name = "Wholphin"', 'rootProject.name = "Ndorfin"'),
    ('                "wholphin",', '                "ndorfin",'),
    ('tag ?: "Wholphin"', 'tag ?: "Ndorfin"'),
]

SCAN_DIRS = [
    ROOT / "app" / "src",
    ROOT / ".github",
    ROOT / "scripts",
]
SCAN_FILES = [
    ROOT / "settings.gradle.kts",
    ROOT / "app" / "build.gradle.kts",
    ROOT / "README.md",
    ROOT / "DEVELOPMENT.md",
    ROOT / "CONTRIBUTING.md",
    ROOT / "CHANGELOG.md",
    ROOT / "renovate.json",
]

EXTENSIONS = {".kt", ".xml", ".kts", ".md", ".yml", ".json", ".toml", ".proto"}


def should_process(path: Path) -> bool:
    return path.suffix in EXTENSIONS and "build/" not in str(path)


def apply_replacements(text: str) -> str:
    for old, new in TEXT_REPLACEMENTS:
        text = text.replace(old, new)
    # Locale / user-facing strings in translations
    if "strings.xml" in text or 'name="display_preset' in text or 'name="updated_toast' in text or 'name="about"' in text:
        pass  # handled by Wholphini/Wholphin specific keys below
    return text


def apply_locale_replacements(text: str) -> str:
    text = text.replace("Wholphini", "Ndorfin")
    text = text.replace("Wholphin", "Ndorfin")
    return text


def main() -> None:
    changed = 0
    paths: list[Path] = []
    for scan_dir in SCAN_DIRS:
        if scan_dir.exists():
            paths.extend(p for p in scan_dir.rglob("*") if p.is_file() and should_process(p))
    for path in SCAN_FILES:
        if path.exists():
            paths.append(path)

    for path in sorted(set(paths)):
        original = path.read_text(encoding="utf-8")
        updated = apply_replacements(original)
        if path.name == "strings.xml":
            updated = apply_locale_replacements(updated)
        if path.name == "README.md":
            updated = apply_locale_replacements(updated)
            updated = updated.replace("wholphin one jelly", "ndorfin one jelly")
            updated = updated.replace("Never half-phin two jellies", "Never half-fin two jellies")
        if updated != original:
            path.write_text(updated, encoding="utf-8")
            changed += 1
            print(f"updated: {path.relative_to(ROOT)}")

    print(f"Done. {changed} files updated.")


if __name__ == "__main__":
    main()
