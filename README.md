<div align="center">

# Nira Browser (alpha)

Nira (निरा) means pure — शुद्ध / absolute — पूर्ण / the only one - एकमात्र.

### A Privacy-Focused Android Browser Built on Mozilla's GeckoView

[![Issues](https://img.shields.io/github/issues-raw/prirai/nira-browser)](https://github.com/prirai/nira-browser/issues)
[![Release](https://img.shields.io/github/v/release/prirai/nira-browser)](https://github.com/prirai/nira-browser/releases)
[![Stars](https://img.shields.io/github/stars/prirai/nira-browser?style=social)](https://github.com/prirai/nira-browser/stargazers)
[![License](https://img.shields.io/badge/License-MPL%202.0-blue.svg)](LICENSE)

*Experience the web with power, privacy, and performance*

[Features](#features) • [Installation](#installation) • [Screenshots](#screenshots) • [Contributing](#contributing)

</div>

> [!WARNING]  
> This browser is still in alpha stage and may contain bugs or issues. Use at your own risk. Please submit issues in the [issues tab](https://github.com/prirai/nira-browser/issues) and feature requests in discussions under the "Ideas" section [here](https://github.com/prirai/nira-browser/discussions/new?category=ideas).

---

## Features

### Core Browsing Experience

- Uses the GeckoView Engine and features Material 3 Design
- **Private Browsing** - Built-in private browsing mode with no history or cookie tracking
- **Multi-Profile System** - Create multiple browser profiles with isolated cookies, sessions, and browsing data
- **Progressive Web Apps** - Full PWA support with installation, offline capabilities, and app management
- **Tab groups and bar** - First of its kind support for tab grouping and quick bar for faster navigation
- **Privacy & Security** - Built-in ad and tracker blocking, enhanced tracking protection and safe browsing

### Complete feature-set

- **Tab Groups** support with multiple view modes: list, and bar with groups
- **Bookmark** support with import/export and folder organization
- **WebExtension** Support including sideloading from URLs or files (XPI support)
- **Theming** options including light, dark, material 3 dynamic color themes and amoled
- **Toolbar Positioning** on top or bottom toolbar
- **Contextual Toolbar** is a smart toolbar that adapts based on page context
- **Homepage** shows shortcuts and bookmarks for easy access
- **Allow showing/hiding** different components of the app for full freedom
- **Multiple Search Engines** like Google, DuckDuckGo, Bing, Baidu, Brave, Naver, Qwant, Startpage, Yandex and you can add your own search engines with custom URLs
- Distraction-free **reading** with customizable fonts and colors
- Convert any **webpage to PDF** with one tap
- **Custom Tab integration** when opening links from other apps
- Built-in **download manager** with external downloader support
- LRU tab navigation by swiping left or right to backward to navigation tabs by their order of recency
- **Background audio/video** with media session controls
- **Multiple Language support** - Support for Arabic, French, Italian, Japanese, Polish, Portuguese (BR & PT), Russian, Turkish, Vietnamese, Chinese (CN)
- Create separate profiles for work, personal, shopping, or any use case with full cookie and data isolation and a unique color and emoji to easily identify profiles
- Switch between profiles seamlessly without losing your tabs
- Install web apps as standalone applications with one tap with smart suggestions including binding pwa storage with a specific profile
- Enable/disable, update, or uninstall PWAs from settings

---

## Screenshots

*Screenshots will be updated here.*

---

## Installation

### From Releases

Download the latest APK from the [Releases page](https://github.com/prirai/nira-browser/releases) and install it on your device.

### From Source

1. **Clone the repository**
   ```bash
   git clone https://github.com/prirai/nira-browser.git
   cd nira-browser
   ```

2. **Build the project**
   ```bash
   ./gradlew assembleDebug
   ```
   *On Windows, use `gradlew.bat assembleDebug`*

3. **Install on device**
   
   The build creates architecture-specific APKs:
   ```bash
   # For ARM64 devices (most modern Android phones)
   adb install app/build/outputs/apk/debug/app-arm64-v8a-debug.apk
   
   # For ARMv7 devices (older phones)
   adb install app/build/outputs/apk/debug/app-armeabi-v7a-debug.apk
   
   # For x86_64 devices (emulators and chromebooks)
   adb install app/build/outputs/apk/debug/app-x86_64-debug.apk
   ```

---

## Contributing

We welcome contributions! Here's how you can help:

### Ways to Contribute

- **Report Bugs** - Found an issue? [Open an issue](https://github.com/prirai/nira-browser/issues/new). Make sure to include your device model, Android version, and steps to reproduce.
- **Suggest Features** - Have an idea? We'd love to hear it!
- **Translate** - Help translate Nira into your language
- **Code** - Submit pull requests to improve the browser
- **Documentation** - Improve docs and help others

### Development Guidelines

1. **Fork the repository**
2. **Create a feature branch** (`git checkout -b feature/amazing-feature`)
3. **Follow the code style** - Kotlin coding conventions
4. **Test your changes** - Ensure everything works
5. **Commit with clear messages** - Describe what and why
6. **Open a Pull Request** - We'll review it promptly

---

## Roadmap

### Recently Added
- [x] Multi-profile system with cookie isolation
- [x] Progressive Web App (PWA) support including management and settings interface
- [x] UI/UX refinements
- [x] Tab/Bookmark/History search
- [x] Material 3 Theming
- [x] Amoled theming support
- [x] Edge-to-edge design
- [x] Sending tabs and groups to other profiles
- [x] QR scanning for opening URLs

### Coming Soon
- [ ] Profile context passing
- [ ] Better and more curated PWA suggestion interface
- [ ] Sync across devices
- [ ] Profile import/export
- [ ] More customization options (never ends?)
- [ ] Accessibility improvements
- [ ] User js inspired by arkenfox, betterfox and co
- [ ] Download management interface
- [ ] New icon representing the project

## Support

Need help? Have questions?

- [Open an Issue](https://github.com/prirai/nira-browser/issues)
- [Discuss here](https://t.me/nirafoss)
- Discussions tab in github can be used as a high priority entry point for discussing a feature or general usability comments.

## Star History

[![Star History Chart](https://api.star-history.com/svg?repos=prirai/nira-browser&type=date&legend=top-left)](https://www.star-history.com/#prirai/nira-browser&type=date&legend=top-left)

## License

This project is licensed under the **Mozilla Public License Version 2.0**.

See [LICENSE](LICENSE) file for details.

## Disclaimer

This is an open-source project intended to provide freedom of web browser configuration on mobile. It is worked on in free time and there are **no guarantees of stability or updates**.

### Warranty

```
THERE IS NO WARRANTY FOR THE PROGRAM, TO THE EXTENT PERMITTED BY
APPLICABLE LAW. EXCEPT WHEN OTHERWISE STATED IN WRITING THE COPYRIGHT
HOLDERS AND/OR OTHER PARTIES PROVIDE THE PROGRAM "AS IS" WITHOUT WARRANTY
OF ANY KIND, EITHER EXPRESSED OR IMPLIED, INCLUDING, BUT NOT LIMITED TO,
THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR
PURPOSE. THE ENTIRE RISK AS TO THE QUALITY AND PERFORMANCE OF THE PROGRAM
IS WITH YOU. SHOULD THE PROGRAM PROVE DEFECTIVE, YOU ASSUME THE COST OF
ALL NECESSARY SERVICING, REPAIR OR CORRECTION.
```

### Liability

```
IN NO EVENT UNLESS REQUIRED BY APPLICABLE LAW OR AGREED TO IN WRITING
WILL ANY COPYRIGHT HOLDER, OR ANY OTHER PARTY WHO MODIFIES AND/OR CONVEYS
THE PROGRAM AS PERMITTED ABOVE, BE LIABLE TO YOU FOR DAMAGES, INCLUDING ANY
GENERAL, SPECIAL, INCIDENTAL OR CONSEQUENTIAL DAMAGES ARISING OUT OF THE
USE OR INABILITY TO USE THE PROGRAM (INCLUDING BUT NOT LIMITED TO LOSS OF
DATA OR DATA BEING RENDERED INACCURATE OR LOSSES SUSTAINED BY YOU OR THIRD
PARTIES OR A FAILURE OF THE PROGRAM TO OPERATE WITH ANY OTHER PROGRAMS),
EVEN IF SUCH HOLDER OR OTHER PARTY HAS BEEN ADVISED OF THE POSSIBILITY OF
SUCH DAMAGES.
```

---

## Acknowledgments

- **SmartCookieWeb browser** - The browser which started it all at [github.com/CookieJarApps/SmartCookieWeb/](https://github.com/CookieJarApps/SmartCookieWeb/)
- **Mozilla** - For the incredible GeckoView engine and Android Components
- **All Contributors** - Thank you for making Nira better
- **Open Source Community** - For the tools and libraries that make this possible

---

<div align="center">

**Built with care for privacy and freedom**

Copyright © prirai

[Back to Top](#nira-browser)

</div>
