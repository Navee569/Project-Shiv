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
assert len(services)==3
privileged=[s for s in services if s.attrib.get(ns+"permission")]
assert {s.attrib.get(ns+"permission") for s in privileged} == {
    "android.permission.BIND_NOTIFICATION_LISTENER_SERVICE",
    "android.permission.BIND_ACCESSIBILITY_SERVICE"}
mic=[s for s in services if s.attrib.get(ns+"name")==".voice.ShivMicSessionService"]
assert len(mic)==1 and mic[0].attrib.get(ns+"exported")=="false"
assert mic[0].attrib.get(ns+"foregroundServiceType")=="microphone"
permissions={p.attrib.get(ns+"name") for p in x.findall("uses-permission")}
assert permissions=={"android.permission.POST_NOTIFICATIONS","android.permission.RECORD_AUDIO","android.permission.READ_CALENDAR","android.permission.WRITE_CALENDAR","android.permission.READ_PHONE_STATE","android.permission.READ_CONTACTS",
"android.permission.FOREGROUND_SERVICE","android.permission.FOREGROUND_SERVICE_MICROPHONE",
"android.permission.RECEIVE_BOOT_COMPLETED",
"android.permission.PACKAGE_USAGE_STATS"},permissions
awareness_receivers=[r for r in a.findall("receiver") if
    r.attrib.get(ns+"name")==".awareness.AwarenessControlReceiver"]
assert len(awareness_receivers)==1 and awareness_receivers[0].attrib.get(ns+"exported")=="false"
gradle=(app/"build.gradle").read_text(encoding="utf-8")
assert 'applicationId "com.shiv.assistant"' in gradle and "namespace = 'com.shiv.assistant'" in gradle
assert (app/"src/cloud/AndroidManifest.xml").is_file()
assert (app/"src/private/AndroidManifest.xml").is_file()
db_source=(app/"src/main/java/com/shiv/assistant/data/EncryptedDatabase.kt").read_text(encoding="utf-8")
assert "fallbackToDestructiveMigration" not in db_source
assert "MIGRATION_1_2" in db_source and "checkIntegrity" in db_source
assert (app/"schemas/com.shiv.assistant.data.ShivDatabase/1.json").is_file()
assert (app/"schemas/com.shiv.assistant.data.ShivDatabase/2.json").is_file()
sources=list((app/"src/main/java").rglob("*.kt"))
assert len(sources)>=7
for path in sources:
    text=path.read_text(encoding="utf-8")
    assert "com.opendroid" not in text.lower() and "OpenDroid" not in text,path
ids=re.findall(r'Capability\("([^"]+)"',(app/"src/main/java/com/shiv/assistant/domain/ShivCore.kt").read_text(encoding="utf-8"))
assert len(ids)>=40 and len(ids)==len(set(ids)),len(ids)
print("Static safety checks passed:",len(sources),"sources,",len(ids),"capabilities")
