# Signing

`test-release.keystore` is a **test key** that is committed on purpose. CI signs builds
with it when no release key is configured, so every test APK has the same signature and
installs over the previous one. Its password is public (`amharic-keyboard-test`). Do
**not** use it for the Play Store.

## Production (Play Store) signing

1. Create your own upload key and keep it private, never in git:
   `keytool -genkeypair -v -keystore upload.keystore -alias upload -keyalg RSA -keysize 2048 -validity 10000`
2. Add these GitHub repository secrets (Settings → Secrets and variables → Actions):
   - `RELEASE_KEYSTORE_BASE64`: output of `base64 -w0 upload.keystore`
   - `RELEASE_KEYSTORE_PASSWORD`, `RELEASE_KEY_ALIAS`, `RELEASE_KEY_PASSWORD`
3. CI then signs the release APK and the Play Store bundle (`.aab`) with your key.
   Use Play App Signing when you publish.
