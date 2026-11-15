# Debug signing key

orbit-debug.keystore signs debug and test builds only (store and key password: android, alias: androiddebugkey).
It is committed so that every CI run and every machine signs test APKs the same way, which lets a new test
APK install over the previous one and keep its chats and settings. It is public, so it must never sign a release.

CI hands out the perf build as the test APK (Orbit-test-<commit>): not debuggable, so Android shows no
"debuggable app" warning, and signed with this same key, so it installs over earlier debug or perf builds.
