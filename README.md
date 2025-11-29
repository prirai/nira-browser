<div align="center">

# 🌊 Nira Browser

### A Privacy-Focused Android Browser Built on Mozilla's GeckoView

[![Issues](https://img.shields.io/github/issues-raw/prirai/nira-browser)](https://github.com/prirai/nira-browser/issues)
[![Release](https://img.shields.io/github/v/release/prirai/nira-browser)](https://github.com/prirai/nira-browser/releases)
[![Stars](https://img.shields.io/github/stars/prirai/nira-browser?style=social)](https://github.com/prirai/nira-browser/stargazers)
[![License](https://img.shields.io/badge/License-MPL%202.0-blue.svg)](LICENSE)

*Experience the web with power, privacy, and performance*

[Features](#-features) • [Installation](#-installation) • [Screenshots](#-screenshots) • [Contributing](#-contributing)

</div>

---

## ✨ Features

### 🎯 Core Browsing Experience

- **🦎 GeckoView Engine** - Powered by Mozilla's Gecko engine, the same technology behind Firefox
- **🎨 Material 3 Design** - Modern, beautiful UI that follows the latest Android design guidelines
- **🌓 Dark Mode** - Full dark theme support with web content darkening for comfortable night browsing
- **🔒 Private Browsing** - Built-in private browsing mode with no history or cookie tracking

### 🛡️ Privacy & Security

- **🚫 Ad Blocking** - Built-in ad and tracker blocking to keep your browsing clean and fast
- **🔐 Enhanced Tracking Protection** - Comprehensive protection against cross-site tracking
- **🛡️ Safe Browsing** - Protection against malicious websites and phishing attempts
- **🔓 SSL Certificate Info** - View detailed connection security information with one tap

### 📑 Tab Management

- **🏝️ Tab Groups** (Opera-style) - Organize tabs into groups with beautiful tab islands
- **🎴 Modern Tab Pills** - Sleek, modern tab interface with smooth animations
- **📊 Tab Tray** - Multiple view modes: grid, list, and islands with groups
- **🔄 Tab Islands** - Visual grouping of tabs for better organization

### 🔖 Bookmarks & History

- **📚 Advanced Bookmark Management** - Create folders, organize hierarchically, and sort by multiple criteria
- **🔍 Bookmark Search** - Quickly find bookmarks with instant search
- **📁 Folder Organization** - Nested folder support with visual path navigation
- **📜 Comprehensive History** - Full browsing history with search and filtering
- **💾 Import/Export** - Backup and restore bookmarks easily
- **⭐ Quick Bookmarking** - Add bookmarks with a single tap

### 🧩 Extensions & Add-ons

- **🧩 WebExtension Support** - Full support for Firefox-compatible extensions
- **📦 Sideloading** - Install extensions from URLs or files (XPI support)
- **🔧 Extension Management** - Enable, disable, configure, and update extensions
- **⚙️ Extension Settings** - Per-extension settings and permissions management
- **🎯 Extension Toolbar Actions** - Quick access to extension actions from the toolbar
- **🔄 Auto-Updates** - Automatic extension updates to keep you secure

### 🎯 Customization

- **🎨 Theme Options** - Light, dark, and system-following themes
- **📱 Toolbar Positioning** - Choose top or bottom toolbar placement
- **🎭 Contextual Bottom Toolbar** - Smart toolbar that adapts based on context
- **🌈 Status Bar Effects** - Beautiful blur effects on Android 12+ devices
- **🔤 Custom Font Sizing** - Adjust text size for comfortable reading
- **🏠 Customizable Homepage** - Configure your perfect start page with shortcuts and feeds
- **🎯 Toolbar Customization** - Show/hide URL bar while scrolling

### 🔍 Search

- **🔍 Multiple Search Engines** - Google, DuckDuckGo, Bing, Baidu, Brave, Naver, Qwant, Startpage, Yandex
- **🎯 Custom Search Engines** - Add your own search engines with custom URLs
- **💡 Search Suggestions** - Real-time search suggestions as you type
- **🚀 Awesome Bar** - Unified search and URL bar with shortcuts suggestions
- **🔐 Private Search** - Option to disable search suggestions in private mode

### 🎪 Advanced Features

- **📖 Reader Mode** - Distraction-free reading with customizable fonts and colors
- **🔎 Find in Page** - Powerful in-page search with keyboard-aware positioning
- **📄 Save as PDF** - Convert any webpage to PDF with one tap
- **🔗 Custom Tabs** - Seamless integration when opening links from other apps
- **📱 PWA Support** - Install Progressive Web Apps as standalone applications
- **📥 Download Management** - Built-in download manager with external downloader support
- **📋 Smart Clipboard** - Intelligent clipboard handling for URLs and text
- **🎵 Media Playback** - Background audio/video with media session controls
- **🖼️ Favicon Cache** - Smart favicon caching for faster page loading

### 🌍 Internationalization

- **🌐 Multiple Languages** - Support for Arabic, French, Italian, Japanese, Polish, Portuguese (BR & PT), Russian, Turkish, Vietnamese, Chinese (CN)
- **🔄 RTL Support** - Full right-to-left language support

---

## 📸 Screenshots

*Screenshots will be updated here.*

---

## 🚀 Installation

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
   ```

---

## 🎯 Why Nira Browser?

### For Privacy Enthusiasts
- No telemetry or data collection
- Built-in ad and tracker blocking
- Enhanced tracking protection
- Private browsing mode

### For Power Users
- Tab groups for organization
- Extensive customization options
- WebExtension support
- Advanced bookmark management

### For Everyone
- Fast and lightweight
- Beautiful Material 3 design
- Constantly improving
- Free and open source

---

## 🤝 Contributing

We welcome contributions! Here's how you can help:

### Ways to Contribute

- 🐛 **Report Bugs** - Found an issue? [Open an issue](https://github.com/prirai/nira-browser/issues/new)
- ✨ **Suggest Features** - Have an idea? We'd love to hear it!
- 🌍 **Translate** - Help translate Nira into your language
- 💻 **Code** - Submit pull requests to improve the browser
- 📖 **Documentation** - Improve docs and help others

### Development Guidelines

1. **Fork the repository**
2. **Create a feature branch** (`git checkout -b feature/amazing-feature`)
3. **Follow the code style** - Kotlin coding conventions
4. **Test your changes** - Ensure everything works
5. **Commit with clear messages** - Describe what and why
6. **Open a Pull Request** - We'll review it promptly

For detailed development guidelines, see our [AI Agent Development Prompts](prompts/README.md) for structured workflows.

---

## 🏗️ Architecture

Nira Browser is built on:
- **Mozilla GeckoView** - Powerful rendering engine
- **Mozilla Android Components** - Modular browser components
- **Kotlin** - Modern, safe programming language
- **Material 3** - Latest Android design system
- **Room Database** - For bookmarks, tab groups, and shortcuts
- **Kotlin Coroutines** - Asynchronous operations

---

## 📋 Roadmap

### Coming Soon
- [ ] Sync across devices
- [ ] Enhanced PWA capabilities
- [ ] More customization options
- [ ] Additional language support

### In Progress
- [ ] UI/UX refinements
- [ ] Extension ecosystem expansion
- [ ] Accessibility improvements

---

## 💬 Support

Need help? Have questions?

- 📝 [Open an Issue](https://github.com/prirai/nira-browser/issues)
- 💬 Social links will be updated

---

## ⚖️ License

This project is licensed under the **Mozilla Public License Version 2.0**.

See [LICENSE](LICENSE) file for details.

### What this means:
- ✅ Use for personal or commercial purposes
- ✅ Modify and distribute
- ✅ Patent grant included
- ⚠️ Must disclose source for modifications
- ⚠️ License and copyright notice required

---

## ⚠️ Disclaimer

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

## 🙏 Acknowledgments

- **Mozilla** - For the incredible GeckoView engine and Android Components
- **All Contributors** - Thank you for making Nira better
- **Open Source Community** - For the tools and libraries that make this possible

---

<div align="center">

**Built with ❤️ for privacy and freedom**

Copyright © prirai

[⬆ Back to Top](#-nira-browser)

</div>
