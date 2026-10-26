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
assert len(services)==2,services
by_name={service.attrib.get(ns+"name"): service for service in services}
assert by_name[".notifications.ShivNotificationListener"].attrib.get(ns+"permission")=="android.permission.BIND_NOTIFICATION_LISTENER_SERVICE"
assert by_name[".integrations.ShivAccessibilityReader"].attrib.get(ns+"permission")=="android.permission.BIND_ACCESSIBILITY_SERVICE"
assert all(s.attrib.get(ns+"exported") == "true" for s in services)
permissions={p.attrib.get(ns+"name") for p in x.findall("uses-permission")}
assert permissions=={"android.permission.INTERNET","android.permission.POST_NOTIFICATIONS","android.permission.RECORD_AUDIO","android.permission.USE_BIOMETRIC","android.permission.READ_CALENDAR","android.permission.READ_CONTACTS","android.permission.PACKAGE_USAGE_STATS"},permissions
gradle=(app/"build.gradle").read_text()
assert 'applicationId "com.shiv.assistant"' in gradle and "namespace = 'com.shiv.assistant'" in gradle
assert "net.zetetic:sqlcipher-android" in gradle and "androidx.room:room-runtime" in gradle
db=(app/"src/main/java/com/shiv/assistant/data/ShivRoomDatabase.kt").read_text()
assert "@Database(" in db and "SupportOpenHelperFactory" in db
assert "allowMainThreadQueries" in db # known pre-production technical debt, see STATUS
sources=list((app/"src/main/java").rglob("*.kt"))
assert len(sources)>=7
for path in sources:
    text=path.read_text()
    assert "com.opendroid" not in text.lower() and "OpenDroid" not in text,path
ids=re.findall(r'Capability\("([^"]+)"',(app/"src/main/java/com/shiv/assistant/domain/ShivCore.kt").read_text())
assert len(ids)>=40 and len(ids)==len(set(ids)),len(ids)
print("Static safety checks passed:",len(sources),"sources,",len(ids),"capabilities")
