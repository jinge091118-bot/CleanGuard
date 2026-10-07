# CleanGuard V0.2 Upload Signing Setup

Status: **R1 wiring implemented; protected Upload Key required from the project owner**

## Security boundary

- Use Google Play App Signing with a separate Upload Key.
- Never commit a keystore, password, private key, `key.properties`, signing secret, or recoverable private-key material.
- Keep the keystore outside the CleanGuard repository.
- Maintain at least two protected backups in separate locations.
- Do not paste signing passwords into chat, tickets, shell history, build logs, source files, README files, or Gradle files.

## Create the Upload Key interactively

Run these commands yourself in a local PowerShell window. `keytool` will prompt for the password and certificate identity without placing the password in the command line.

```powershell
$signingDir = Join-Path $env:USERPROFILE 'SecureKeys\CleanGuard'
New-Item -ItemType Directory -Path $signingDir -Force
& 'I:\Android\jdk-17\bin\keytool.exe' -genkeypair -v `
  -keystore (Join-Path $signingDir 'cleanguard-upload.jks') `
  -alias 'cleanguard-upload' `
  -keyalg RSA `
  -keysize 4096 `
  -validity 10000 `
  -storetype JKS
```

Do not create the keystore inside this repository. After creation, make two protected backups and record the password in a trusted password manager.

## Configure Gradle locally

Choose one method. Do not use both unless the environment-variable values are intentionally overriding the local file.

Release assembly fails closed: `assembleRelease` and `bundleRelease` refuse to run until all four signing values are present and the external keystore exists.

### Environment variables

Set these only in a protected local or CI secret store:

- `CLEANGUARD_UPLOAD_STORE_FILE`
- `CLEANGUARD_UPLOAD_STORE_PASSWORD`
- `CLEANGUARD_UPLOAD_KEY_ALIAS`
- `CLEANGUARD_UPLOAD_KEY_PASSWORD`

### Ignored local properties

Copy `key.properties.example` to `key.properties`, replace every placeholder locally, and never stage the resulting file. The keystore path must point outside the repository.

## Verification commands

```powershell
.\gradlew.bat verifyReleaseSigningConfiguration
.\gradlew.bat clean test lint assembleRelease bundleRelease assembleDebugAndroidTest
.\gradlew.bat connectedDebugAndroidTest
```

After a successful build, verify the APK with Android `apksigner`, verify the AAB with `jarsigner`, export the upload certificate with `keytool -exportcert`, and record only public certificate fingerprints and artifact SHA-256 hashes in the signing report.

## Version-code rule

Version code `2` may be used for RC1 only if it has never previously been uploaded. Once any artifact with version code `2` reaches Google Play, every later RC must use a larger version code. Version name may remain `0.2.0` until production release.
