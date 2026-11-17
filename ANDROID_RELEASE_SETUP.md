# GitHub Actions Android Release Setup

This document explains how to set up the GitHub Actions workflow for automated Android releases.

## Required GitHub Secrets

You need to add the following secrets to your GitHub repository:

### 1. KEYSTORE_BASE64

Your release keystore file encoded in base64.

To generate this:

```bash
# Navigate to your keystore directory
cd keystore
# Encode your keystore file to base64
base64 -i alternate-release-key.jks | tr -d '\n' > keystore_base64.txt
```

Copy the contents of `keystore_base64.txt` and add it as `KEYSTORE_BASE64` secret in GitHub.

### 2. KEYSTORE_PASSWORD

The password for your keystore file.

### 3. KEY_ALIAS

The alias of your signing key within the keystore.

### 4. KEY_PASSWORD

The password for your signing key.

## How to Add Secrets to GitHub

1. Go to your GitHub repository
2. Click on **Settings** tab
3. In the left sidebar, click **Secrets and variables** → **Actions**
4. Click **New repository secret**
5. Add each secret with the exact names mentioned above

## Workflow Trigger

The workflow runs when you push a tag that starts with `v`. The tag must equal `versionName` in `app/build.gradle.kts`
(for example `v3.0.0` for `versionName = "3.0.0"`), otherwise the build stops before doing any work.

```bash
# 1. Bump versionCode and versionName in app/build.gradle.kts
# 2. Add fastlane/metadata/android/en-US/changelogs/<versionCode>.txt
#    (first line "v3.0.0 - Short title", then "- bullet" lines)
# 3. Commit, then tag and push
git tag v3.0.0
git push origin v3.0.0
```

## What the Workflow Does

1. **Check the tag**: compares it with `versionName`
2. **Set up**: JDK 21 (Temurin) and Gradle
3. **Test**: `./gradlew testDebugUnitTest`
4. **Build and sign**: `./gradlew assembleRelease` (R8 minified, one universal APK), signed with your keystore through `keystore.properties`
5. **Verify**: `apksigner verify` fails the run if the APK is not signed
6. **Release notes**: built from `fastlane/.../changelogs/<versionCode>.txt`
7. **Publish**: `gh release create` attaches the APK and `output-metadata.json`

## Generated APK Files

- `alternate-[tag].apk` - Universal APK for all devices
- `output-metadata.json` - Build metadata

## Troubleshooting

### Common Issues:

1. **Keystore decoding fails**: Make sure your base64 encoding doesn't contain newlines
2. **Signing fails**: Verify your keystore password, key alias, and key password are correct
3. **Build fails**: Check that your dependencies are properly defined in `gradle/libs.versions.toml`

### Testing the Build Locally

Before pushing a tag, you can test the build process locally:

```bash
# keystore.properties (git-ignored) in the project root:
#   storeFile=release.jks      (relative to app/)
#   storePassword=...  keyAlias=...  keyPassword=...
./gradlew assembleRelease
```
