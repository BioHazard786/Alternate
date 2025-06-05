[![Contributors][contributors-shield]][contributors-url]
[![Forks][forks-shield]][forks-url]
[![Stargazers][stars-shield]][stars-url]
[![Issues][issues-shield]][issues-url]
[![MIT License][license-shield]][license-url]

<div align="center">
<a href="https://github.com/BioHazard786/Alternate">
    <img src="assets/icon/ios-tinted.png" alt="Logo" width="100" height="100" style="border-radius:15px">
</a>
<br />
<br />
<a href=https://github.com/BioHazard786/Alternate/releases">Download</a>
    ·
    <a href="https://github.com/BioHazard786/Alternate/issues">Report Bug</a>
    ·
    <a href="https://github.com/BioHazard786/Alternate/issues">Request Feature</a>
    <br />
    <br />
</div>

# Alternate - Local Caller ID Detector

A privacy-focused React Native app that helps you identify unknown callers without cluttering your device's main contact list. Perfect for temporary number storage when you need to know who's calling but don't want the number to appear in WhatsApp, Telegram, or other messaging apps.

## Features

- **Local Caller ID Detection**: Identify incoming calls using your private database
- **Temporary Number Storage**: Save numbers locally without affecting your main contacts
- **Privacy Protection**: Numbers won't appear in WhatsApp, Telegram, or other messaging apps
- **Phone Number Validation**: Smart phone number input with country selection
- **Custom Native Module**: Built-in caller ID functionality using Android's native capabilities
- **Country Selector**: Beautiful bottom sheet country picker with search
- **Material Design**: Modern UI following Material Design 3 principles
- **Offline Storage**: All data stored locally using Android's native SQLite database

## Use Case

When you receive calls from unknown numbers but don't want to save them to your main contact list:

- **Delivery drivers** - Know who's calling without adding to contacts
- **Service providers** - Temporary contractors, repair services, etc.
- **Business contacts** - People you interact with briefly
- **Privacy protection** - Keep your main contact list clean while still identifying callers

## Tech Stack

- **React Native** with **Expo**
- **TypeScript** for type safety
- **React Native Paper** for Material Design components
- **Room** for local data storage

## Screenshots

![Home Screen](./mockups/image1.png)
![Add Contact](./mockups/image2.png)
![Country Picker](./mockups/image3.png)
![Caller Popup](./mockups/image4.png)
**Directory Support:** Only works on google phone app
![Directory Support](./mockups/image5.png)

## Download

Download from [Releases Tab](https://github.com/BioHazard786/Alternate/releases)

## Installation

### Prerequisites

- Node.js (v18 or later)
- npm or yarn
- Expo CLI
- Android Studio (for Android development)
- Xcode (for iOS development, macOS only)

### Setup

1. Clone the repository:

```bash
git clone https://github.com/BioHazard786/Alternate.git
cd Alternate
```

2. Install dependencies:

```bash
npm install
```

3. Start the development server:

```bash
npx expo start
```

## Building APK

### Development Build

To build a development APK:

```bash
npx expo run:android
```

### Production Build with EAS

1. Install EAS CLI:

```bash
npm install -g eas-cli
```

2. Configure EAS:

```bash
npx eas build:configure
```

3. Build for Android:

```bash
npx eas build --platform android
```

## Project Structure

```
├── app/                    # Main app screens
├── components/            # Reusable components
├── constants/            # App constants
├── hooks/               # Custom hooks
├── lib/                 # Utility functions and types
├── modules/             # Custom native modules
│   └── caller-id/       # Caller ID native module with SQLite integration
├── store/               # State management
└── assets/              # Images and other assets
```

## How It Works

1. **Add Numbers Locally**: Save phone numbers with names in your private database
2. **Caller ID Detection**: When calls come in, the app checks against your local database
3. **Privacy Maintained**: Numbers remain completely separate from your device's contact list
4. **No Sync Issues**: Won't interfere with messaging apps or cloud contact syncing

## Benefits

- **Clean Contact List**: Keep your main contacts organized
- **Privacy Control**: Numbers stay private to this app only
- **No Messaging App Clutter**: Saved numbers won't appear in WhatsApp, Telegram, etc.
- **Temporary Storage**: Perfect for short-term contact needs
- **Offline Functionality**: Works completely offline with local SQLite storage

## Contributing

1. Fork the repository
2. Create your feature branch (`git checkout -b feature/amazing-feature`)
3. Commit your changes (`git commit -m 'Add some amazing feature'`)
4. Push to the branch (`git push origin feature/amazing-feature`)
5. Open a Pull Request

## License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

## Contact

Mohd Zaid - [Telegram](https://t.me/LuLu786) - bzatch70@gmail.com

Project Link: [https://github.com/BioHazard786/Alternate](https://github.com/BioHazard786/Alternate)

## Acknowledgments

- Thanks To dmkvsk for native module inspiration [Repo](https://github.com/dmkvsk/react-native-detect-caller-id)
- Thanks To SimpleNexus for call directory implementation [Repo](https://github.com/SimpleNexus/simplecallerid)

---

_Keep your contact list clean while never missing an important call again!_

<!-- MARKDOWN LINKS & IMAGES -->
<!-- https://www.markdownguide.org/basic-syntax/#reference-style-links -->

[contributors-shield]: https://img.shields.io/github/contributors/BioHazard786/Alternate.svg?style=for-the-badge
[contributors-url]: https://github.com/BioHazard786/Alternate/graphs/contributors
[forks-shield]: https://img.shields.io/github/forks/BioHazard786/Alternate.svg?style=for-the-badge
[forks-url]: https://github.com/BioHazard786/Alternate/network/members
[stars-shield]: https://img.shields.io/github/stars/BioHazard786/Alternate.svg?style=for-the-badge
[stars-url]: https://github.com/BioHazard786/Alternate/stargazers
[issues-shield]: https://img.shields.io/github/issues/BioHazard786/Alternate.svg?style=for-the-badge
[issues-url]: https://github.com/BioHazard786/Alternate/issues
[license-shield]: https://img.shields.io/github/license/BioHazard786/Alternate.svg?style=for-the-badge
[license-url]: https://github.com/BioHazard786/Alternate/blob/master/LICENSE
