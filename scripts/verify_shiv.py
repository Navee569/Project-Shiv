#!/usr/bin/env python3
from pathlib import Path
import re
import xml.etree.ElementTree as ET
root=Path(__file__).resolve().parents[1]
app=root/"app"
ns="{http://schemas.android.com/apk/res/android}"
x=ET.parse(app/"src/main/AndroidManifest.xml").getroot()
a=x.find("application")
assert a is not None
assert a.attrib.get(ns+"allowBackup")=="false"
assert a.attrib.get(ns+"usesCleartextTraffic")=="false"
services=a.findall("service")
assert len(services)==1 and services[0].attrib.get(ns+"permission")=="android.permission.BIND_NOTIFICATION_LISTENER_SERVICE"
permissions={p.attrib.get(ns+"name") for p in x.findall("uses-permission")}
assert permissions=={"android.permission.INTERNET","android.permission.POST_NOTIFICATIONS","android.permission.RECORD_AUDIO","android.permission.USE_BIOMETRIC"},permissions
gradle=(app/"build.gradle").read_text()
assert 'applicationId "com.shiv.assistant"' in gradle and "namespace = 'com.shiv.assistant'" in gradle
sources=list((app/"src/main/java").rglob("*.kt"))
assert len(sources)>=7
for path in sources:
    text=path.read_text()
    assert "com.opendroid" not in text.lower() and "OpenDroid" not in text,path
ids=re.findall(r'Capability\("([^"]+)"',(app/"src/main/java/com/shiv/assistant/domain/ShivCore.kt").read_text())
assert len(ids)>=40 and len(ids)==len(set(ids)),len(ids)
print("Static safety checks passed:",len(sources),"sources,",len(ids),"capabilities")
