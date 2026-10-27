"""Fail closed on source and built APK privacy gates; print native library hashes."""
import argparse
import hashlib
import re
import subprocess
import xml.etree.ElementTree as ET
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
ANDROID = "{http://schemas.android.com/apk/res/android}"
TOOLS = "{http://schemas.android.com/tools}"


def source_check():
    main = ET.parse(ROOT / "app/src/main/AndroidManifest.xml").getroot()
    cloud = ET.parse(ROOT / "app/src/cloud/AndroidManifest.xml").getroot()
    private = ET.parse(ROOT / "app/src/private/AndroidManifest.xml").getroot()
    internet = "android.permission.INTERNET"
    def permissions(root):
        return [element for element in root.findall("uses-permission")
                if element.get(ANDROID + "name") == internet]
    assert not permissions(main), "Main manifest must not request INTERNET"
    assert len(permissions(cloud)) == 1, "Cloud flavor must request INTERNET"
    assert len(permissions(private)) == 1 and \
        permissions(private)[0].get(TOOLS + "node") == "remove", \
        "Private flavor must remove merged INTERNET permission"
    app = main.find("application")
    assert app is not None and app.get(ANDROID + "allowBackup") == "false"
    assert app.get(ANDROID + "usesCleartextTraffic") == "false"
    for component in app:
        if component.find("intent-filter") is not None:
            assert component.get(ANDROID + "exported") in {"true", "false"}, \
                f"Missing explicit exported flag: {component.get(ANDROID + 'name')}"
    gradle = (ROOT / "app/build.gradle").read_text(encoding="utf-8")
    assert "NETWORK_ALLOWED', 'false'" in gradle
    assert "NETWORK_ALLOWED', 'true'" in gradle
    print("SOURCE PASS: permissions, cleartext, backup, exported components, flavors")


def apk_check(path: Path, aapt: str, private: bool):
    output = subprocess.check_output([aapt, "dump", "permissions", str(path)], text=True)
    has_internet = "android.permission.INTERNET" in output
    assert has_internet != private, f"Wrong INTERNET permission in {path}"
    with zipfile.ZipFile(path) as archive:
        libs = sorted(name for name in archive.namelist()
                      if re.fullmatch(r"lib/[^/]+/[^/]+\.so", name))
        print(f"{path.name}: INTERNET={has_internet}; {len(libs)} native libraries")
        for name in libs:
            digest = hashlib.sha256(archive.read(name)).hexdigest()
            print(f"  {name} sha256={digest}")


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--private-apk", type=Path)
    parser.add_argument("--cloud-apk", type=Path)
    parser.add_argument("--aapt", default="aapt")
    args = parser.parse_args()
    source_check()
    if args.private_apk and args.cloud_apk:
        apk_check(args.private_apk, args.aapt, True)
        apk_check(args.cloud_apk, args.aapt, False)
        print("APK PASS: merged permissions and native library inventory")
    else:
        print("SOURCE ONLY: pass both APK paths and --aapt for release verification")
