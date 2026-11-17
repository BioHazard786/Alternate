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

The workflow is triggered when you push a tag that starts with 'v':

```bash
# Create and push a tag
git tag v1.1.0
git push origin v1.1.0
```

## What the Workflow Does

1. **Setup Environment**: Installs Java 17
2. **Build APK**: Runs `./gradlew assembleRelease` (R8 minified, one universal APK)
3. **Sign APK**: Signs it with your release keystore via `keystore.properties`
4. **Read the changelog**: Uses `fastlane/.../changelogs/<versionCode>.txt`
5. **Create Release**: Creates a GitHub release with the tag
6. **Upload APK**: Uploads the APK to the release

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
