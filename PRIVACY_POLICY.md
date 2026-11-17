# Privacy Policy for Alternate - Local Caller ID Detector

**Effective Date:** November 17, 2026  
**Last Updated:** November 17, 2026 (applies to version 3.0.0 and later)

## Introduction

Alternate ("we," "our," or "us") is committed to protecting your privacy. This Privacy Policy explains what information the mobile application Alternate - Local Caller ID Detector (the "App") handles, how it is used, and who can access it. It applies to the App however you obtain it (Google Play, GitHub Releases, IzzyOnDroid or any other source).

**Key Privacy Principle:** Alternate is designed with privacy at its core. The contacts you save are stored on your device. The App does not have the Android `INTERNET` permission, so it cannot send your data to any server.

## Information the App Handles

### Information You Provide Directly

- **Contact information** that you enter or import: names, prefixes and suffixes, nicknames, phone numbers (with labels and country), email addresses, websites, birthdays, appointments, locations, notes, groups, favorites and profile photos
- **App settings:** your theme, whether the incoming and outgoing call popups are on, and your app lock settings

### Information Used on Your Device

- **Phone number of a call:** when a call starts, the App reads the number from the call so it can look it up in your saved contacts and show the popup. Numbers that are not in your contacts are not stored.
- **Call state:** whether a call is ringing, active or finished, to show and dismiss the popup
- **SIM and network country code:** used only to format and validate phone numbers
- **App lock:** if you turn it on, your PIN is stored as a salted hash, never as plain text. Fingerprint unlock is performed by Android; the App never receives or stores your fingerprint data.
- **Photos:** when you add a profile picture, you choose it with Android's photo picker or take it with your device's camera app. The App does not hold camera or storage permissions. The picture you select is copied into the App's private storage.

### Information the App Does NOT Collect

- Your device's contacts (the App has no Contacts permission)
- Your call history. Android requires the call log permission for an app to receive the number of a call, but the App never reads your call log
- Location data
- Device identifiers or advertising IDs
- Usage analytics or crash reports
- Anything for advertising or profiling

## How the Information Is Used

1. **Caller identification:** to recognise incoming and outgoing calls using the contacts stored in the App
2. **Contact management:** to store, search, group and show the contacts you add
3. **Phone number handling:** formatting, validation and country selection
4. **Preferences and security:** to remember your settings and apply your app lock

## Data Storage and Security

- **Local storage:** contacts are kept in a database in the App's private storage area, and profile photos in its private files directory. Other apps cannot read these files under normal Android security.
- **No network access:** the App has no `INTERNET` permission and does not connect to any server
- **App lock:** the optional PIN and fingerprint lock controls access to the App's screens
- **Not separately encrypted:** the App does not add its own encryption to the database. It relies on Android's app isolation and your device's screen lock and storage encryption. Keep your device protected with a screen lock.

## Permissions Used

### Permissions the App requests

- **READ_PHONE_STATE:** to detect when a call starts and ends and which number is calling
- **READ_CALL_LOG:** required by Android so the App can receive the number of an incoming call. The call log itself is not read
- **SYSTEM_ALERT_WINDOW (Display over other apps):** to show the caller popup on top of your phone app
- **WAKE_LOCK** and **DISABLE_KEYGUARD:** to show the popup when the screen is off or locked
- **USE_BIOMETRIC** and **USE_FINGERPRINT:** for the optional fingerprint unlock (the second is added by Android's biometric library for older Android versions)

### Optional system role

- **Call screening role:** you can give Alternate the "caller ID app" role during setup so the popup appears more reliably. With it, the App receives the number of a call. It never blocks, silences or rejects calls.

### Permissions the App does not request

Contacts, camera, storage, microphone, location and internet.

You can change or revoke permissions at any time in your device settings. The caller popup needs them to work.

## Data Sharing and Disclosure

**We do not share, sell, rent or disclose your personal information to anyone.** We have no servers and receive no data from you. There is no data sharing with advertisers, analytics services, social networks, marketing companies or authorities (except where we are legally compelled, although we hold no data to give).

The following involve other software on your device. They happen only as described.

### Phone app integration (call directory)

The App provides an Android contact directory so your phone app can show the names and photos of your saved numbers. Android only lets apps that hold the system Contacts permission, such as your phone app, read this directory. The lookup happens on your device and nothing is sent to Google or anyone else by the App.

### Actions you start

- **Export and share:** when you export a vCard (.vcf) file or share contacts, the data goes to the app or location you choose. The App places a temporary copy in its cache for the share.
- **Call, text, WhatsApp, Telegram, email and links:** when you tap one of these, the number, address or link is passed to that app. It handles the data under its own privacy policy.
- **Copy:** copied text goes to the Android clipboard, which other apps may be able to read.

### Android backup

The App allows Android's system backup. If device backup is turned on, Android may include the App's data (contacts, settings and photos) in your Google account backup and restore it when you set up a new device. This is done by Android and Google under Google's terms, not by the App. You can switch it off in your device's backup settings. Backups you delete from your Google account are no longer available for restore.

## Data Retention and Deletion

- You can edit or delete a contact at any time, or select many contacts and delete them at once
- You can erase everything with Android's **Clear data** option for the App in your device settings
- Uninstalling the App removes its data from your device. Copies you exported, and any Android backup (see above), stay where they were saved until you delete them
- We keep no data, so we have nothing to delete on our side

## Children's Privacy

Alternate does not knowingly collect personal information from children under 13 years of age. The App is not directed toward children under 13. We hold no user data; if you are a parent or guardian with a concern, please contact us.

## International Data Transfers

The App itself performs no data transfers. If you use Android backup, Google may store your backup in other countries under Google's policies.

## Changes to Privacy Policy

We may update this Privacy Policy from time to time. When we do, we will:

- Update the "Last Updated" date at the top of this policy
- Mention significant changes in the release notes of the App update
- Keep honouring the privacy principles in this policy

## Your Rights and Choices

You can:

- See all stored data inside the App
- Edit or delete any contact, or erase everything with Clear data
- Export your contacts to a vCard file
- Revoke permissions in your device settings
- Turn off Android backup in your device settings
- Uninstall the App

## Open Source

Alternate is open source under the MIT License. You can read exactly what the App does with your data at [https://github.com/BioHazard786/Alternate](https://github.com/BioHazard786/Alternate).

## Contact Information

If you have any questions, concerns or requests regarding this Privacy Policy or our privacy practices, please contact us at:

**Email:** [bzatch70@gmail.com](mailto:bzatch70@gmail.com)  
**GitHub:** [https://github.com/BioHazard786](https://github.com/BioHazard786)  
**App Repository:** [https://github.com/BioHazard786/Alternate](https://github.com/BioHazard786/Alternate)

## Technical Implementation Details

For transparency, here are additional details about how the App protects your privacy:

### Data Isolation

- Contacts stored in Alternate do not appear in your device's main contact list
- Data does not sync with messaging apps (WhatsApp, Telegram, etc.)
- Other apps cannot read the App's database or photos directly

### Components

- The caller popup is shown by a broadcast receiver and an optional call screening service that only read the number of a call
- Contacts are stored with the Android Room library on top of SQLite
- The contact directory is a read-only Android content provider protected by the system Contacts read permission

### No Network Activity

- The App has no `INTERNET` permission and makes no network connections
- No telemetry or usage data is collected or transmitted

---

**By using Alternate, you acknowledge that you have read and understood this Privacy Policy.**

This Privacy Policy is effective as of the date listed above and stays in effect until it is changed. Changes take effect when they are posted on this page.
