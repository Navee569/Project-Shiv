# Private and cloud Android variants

`cloudDebug` retains the explicitly consented provider path. `privateDebug` uses
application ID `com.shiv.assistant.private`, excludes `INTERNET` from its merged
manifest, and rejects cloud text and image calls in code. The variants keep
separate Android app data.

Build both APKs with Java 21 and the Android SDK:

```sh
./gradlew :app:assemblePrivateDebug :app:assembleCloudDebug :app:testPrivateDebugUnitTest :app:testCloudDebugUnitTest
```

Before distributing either APK, inspect the actual merged permissions and
native libraries. Supply the built APKs and the Android SDK `aapt` executable:

```sh
python ci/privacy_release_audit.py --private-apk app/build/outputs/apk/private/debug/app-private-debug.apk --cloud-apk app/build/outputs/apk/cloud/debug/app-cloud-debug.apk --aapt /path/to/aapt
```

The script fails if the private APK requests `INTERNET` or the cloud APK lacks
it, and prints SHA-256 values for packaged `.so` files. A source-only run
checks manifest and flavor definitions but does not prove an APK was built.

Speech first-audio callbacks and whole-device battery counter changes are
diagnostic observations. Audible first-word time and model-attributable power
still require direct measurements on the owner's phone.
