"""Verify that portrait layouts retain every view used by the reference logic."""
from pathlib import Path
import json
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
RES = ROOT / "app/src/main/res"
ANDROID = "{http://schemas.android.com/apk/res/android}"


def ids(path):
    return {
        element.attrib[ANDROID + "id"].split("/")[-1]: element.tag
        for element in ET.parse(path).getroot().iter()
        if ANDROID + "id" in element.attrib
    }


baseline = json.loads((ROOT / "tools/reference_layout_contracts.json").read_text())["layouts"]
for filename, original_ids in baseline.items():
    for variant in ("layout", "layout-port"):
        path = RES / variant / filename
        assert path.exists(), f"Missing layout: {path}"
        phone_ids = ids(path)
        for name, view_type in original_ids.items():
            assert phone_ids.get(name) == view_type, (
                variant, filename, name, view_type, phone_ids.get(name)
            )
print(f"All {len(baseline)} layouts in both variants preserve upstream IDs and view types.")
