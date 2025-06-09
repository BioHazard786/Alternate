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

1. **Setup Environment**: Installs Node.js and Java
2. **Install Dependencies**: Runs `npm ci` to install project dependencies
3. **Build APKs**: Uses Gradle to build release APKs for all architectures
4. **Sign APKs**: Signs the APKs with your release keystore
5. **Create Release**: Creates a GitHub release with the tag
6. **Upload APKs**: Uploads all split APKs to the release

## Generated APK Files

The workflow creates the following APK files:

- `alternate-arm64-v8a-[tag].apk` - For modern 64-bit ARM devices
- `alternate-armeabi-v7a-[tag].apk` - For older 32-bit ARM devices
- `alternate-x86_64-[tag].apk` - For 64-bit x86 devices
- `alternate-x86-[tag].apk` - For 32-bit x86 devices
- `output-metadata.json` - Build metadata

## Troubleshooting

### Common Issues:

1. **Keystore decoding fails**: Make sure your base64 encoding doesn't contain newlines
2. **Signing fails**: Verify your keystore password, key alias, and key password are correct
3. **Build fails**: Check that your dependencies are properly defined in package.json

### Testing the Build Locally

Before pushing a tag, you can test the build process locally:

```bash
# Install dependencies
npm ci

# Prebuild
npx expo prebuild --platform android --clean

# Build (replace with your actual keystore details)
cd android
./gradlew assembleRelease \
  -Pandroid.injected.signing.store.file=../keystore/alternate-release-key.jks \
  -Pandroid.injected.signing.store.password=YOUR_KEYSTORE_PASSWORD \
  -Pandroid.injected.signing.key.alias=YOUR_KEY_ALIAS \
  -Pandroid.injected.signing.key.password=YOUR_KEY_PASSWORD
```

## File Locations

After a successful build, APKs will be located at:

- `android/app/build/outputs/apk/release/app-arm64-v8a-release.apk`
- `android/app/build/outputs/apk/release/app-armeabi-v7a-release.apk`
- `android/app/build/outputs/apk/release/app-x86_64-release.apk`
- `android/app/build/outputs/apk/release/app-x86-release.apk`
