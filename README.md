# TensorIMS

> **聚焦中国大陆运营商网络下的 Google Pixel IMS 配置，由 Mystery00 独立维护。**
> 原 **Mystery00/TurboIMS**，现更名为 **TensorIMS**。仅支持搭载 Google Tensor 芯片的 Pixel 设备，需 Android 13+ 和 Shizuku。
>
> 主要适配和维护范围为中国移动、中国联通、中国电信网络下的使用场景。其他运营商因缺少相应设备、SIM 卡及网络测试条件，不作为主要适配和维护对象；这不表示其他网络一定无法使用，实际兼容性需自行验证。

[下载最新版](https://github.com/Pixel-Tailor-CN/TensorIMS/releases/latest) · [项目主页](https://pixel.mystery0.app) · [中文使用说明](README_CN.md) · [问题反馈](https://github.com/Pixel-Tailor-CN/TensorIMS/issues)

<p align="center">
  <img src="app/src/main/ic_launcher-playstore.png" width="200" alt="TensorIMS Logo"/>
</p>

<p align="center">
  <strong>Enable VoLTE, VoWiFi, and other IMS features on Google Pixel devices.</strong>
</p>

<p align="center">
    <a href="https://github.com/Pixel-Tailor-CN/TensorIMS/releases"><img src="https://img.shields.io/github/v/release/Pixel-Tailor-CN/TensorIMS" alt="GitHub release"></a>
    <a href="LICENSE"><img src="https://img.shields.io/github/license/Pixel-Tailor-CN/TensorIMS" alt="License"></a>
    <a href="https://apps.obtainium.imranr.dev/redirect.html?r=obtainium://add/https://github.com/Pixel-Tailor-CN/TensorIMS"><img src="https://img.shields.io/badge/Obtainium-Import-blue?logo=obtainium&logoColor=white" alt="Obtainium"></a>
</p>

[简体中文](README_CN.md)

## Screenshots

<p align="center">
  <img src="docs/home_en.png" width="260" alt="Home Screen"/>
  <img src="docs/ims_en.png" width="260" alt="IMS Configuration"/>
  <img src="docs/advanced_en.png" width="260" alt="Advanced Tools"/>
</p>

## About

TensorIMS is a tool that allows you to enable or disable IMS features like Voice over LTE (VoLTE), Wi-Fi Calling (VoWiFi), Video Calling (VT), and 5G Voice (VoNR) on Google Pixel phones. It requires [Shizuku](https://shizuku.rikka.app/) to work.

## Features

- **Structured Information Architecture**:
    - **Home Screen**: Overview of device and Shizuku status, SIM selector, navigation to core modules, and the toggle for "Apply configs when Shizuku is ready".
    - **IMS Configuration**: Draft mode configuration organized into "Calling", "Network", "Display", and "Advanced Overrides", with one-tap quick presets and manual fine-tuning.
    - **System Network**: Dedicated device-level system network settings, offering Captive Portal probe endpoint configuration with popular presets.
    - **Advanced Tools**: Segregated into "Diagnostics & Maintenance" (IMS status snapshot, restart IMS, application logs) and "Danger Zone" (reset CarrierConfig overrides), alongside Persistent VoLTE management.
- **One-Tap Quick Presets**:
    - **Recommended**: Enables essential 4G/5G calling and network capabilities (VoLTE, VoNR, 5G NR, 5G signal threshold alignment, VT) while keeping VoWiFi disabled.
    - **China 5G**: Full 5G experience for Chinese mainland carriers with VoNR, 5GA/5G+ icon, and 5G signal threshold alignment.
    - **China LTE**: Pure 4G mode with 5G NR & VoNR explicitly disabled for stable cellular connectivity, battery saving, and thermal reduction.
    - **Enable all**: Enables all available toggleable features at once.
- **Automation & Persistence**:
    - **Apply configs when Shizuku is ready**: Automatically restores saved configurations when the device reboots and Shizuku connects with permission granted.
    - **Persistent VoLTE (Experimental)**: Leverages system-level VoIMS opt-in to keep VoLTE active across device reboots without needing Shizuku on every restart.
    - **Configuration Persistence**: Automatically saves configuration history per SIM card or for all SIMs.
- **Customizable IMS Features**:
    - **Carrier Name**: Override the carrier name displayed on your device.
    - **IMS User Agent**: Override the IMS User Agent string.
    - **VoLTE (Voice over LTE)**: Enable high-definition voice calls over 4G.
    - **VoWiFi (Wi-Fi Calling)**: Make calls over Wi-Fi networks, with options for Wi-Fi only mode.
    - **VT (Video Calling)**: Enable IMS-based video calls.
    - **VoNR (Voice over 5G)**: Enable high-definition voice calls over 5G (Requires Android 14+).
    - **Cross-SIM Calling**: Enable dual-SIM interconnection features.
    - **UT (Supplementary Services)**: Enable call forwarding, call waiting, and other supplementary services over UT.
    - **5G NR**: Enable 5G NSA (Non-Standalone) and SA (Standalone) networks.
    - **5G Signal Strength Thresholds**: Option to apply custom 5G signal strength thresholds.
    - **5G+/5GA Icon**: Configure the carrier thresholds used by Android to expose the enhanced 5G icon.
    - **Enhanced 4G LTE (LTE+)**: Enable the system carrier configuration used for LTE+/4G+ support.
    - **Hide Enhanced Data Icon**: Optionally hide the LTE+/4G+ data icon; actual LTE+/4G+ availability still depends on the device, carrier, and current network.
    - **Show 4G for LTE**: Display the 4G label for LTE data.
- **System Network / Captive Portal**: Read, override, or remove SettingsProvider values for Android's `captive_portal_http_url` and `captive_portal_https_url`, with built-in presets (Google, V2EX, Xiaomi, vivo, Huawei).
- **Logcat Viewer**: View and export application logs for troubleshooting.

> **Captive Portal limitation:** TensorIMS verifies the values written to SettingsProvider, but some Android/NetworkStack builds may prefer resource overlays over those values. A successful write therefore does **not** prove that the active network probe URL changed. Reconnect the network and verify behavior on the target device.

## Requirements

- **Supported Devices**: Google Pixel devices with Google Tensor chips.
    - Pixel 6, 6 Pro, 6a
    - Pixel 7, 7 Pro, 7a
    - Pixel 8, 8 Pro, 8a
    - Pixel 9, 9 Pro, 9 Pro XL, 9 Pro Fold, 9a
    - Pixel 10, 10 Pro, 10 Pro XL, 10 Pro Fold, 10a
    - Pixel 11, 11 Pro, 11 Pro XL, 11 Pro Fold
    - Pixel Fold, Pixel Tablet
    - **Note:** Devices with Qualcomm Snapdragons (Pixel 5 and older) are NOT supported.
- Android 13 or higher
- [Shizuku](https://shizuku.rikka.app/) installed and running

## Installation

<a href="https://apps.obtainium.imranr.dev/redirect.html?r=obtainium://add/https://github.com/Pixel-Tailor-CN/TensorIMS"><img src="https://raw.githubusercontent.com/ImranR98/Obtainium/refs/heads/main/assets/graphics/badge_obtainium.png" alt="Obtainium" height="96"></a>

1. Download the latest APK from the [Releases](https://github.com/Pixel-Tailor-CN/TensorIMS/releases) page.
2. Install the APK on your device.
3. Open the app and grant Shizuku permission.

## Usage

1. **Check Status**: Ensure Shizuku is running and the app has permission.
2. **Select SIM**: Choose the single SIM card or "All SIM" to configure on the home screen.
3. **IMS Configuration**: Open **IMS configuration**, pick a quick preset ("Recommended", "China 5G", or "China LTE") or adjust switches by category, then tap **Apply changes**.
4. **Auto-Restore (Optional)**: Enable "Apply configs when Shizuku is ready" on the home screen to automatically restore configurations after reboots once Shizuku is active.
5. **System Network**: Open **System network** to manage Captive Portal detection URLs with one-tap presets; changes apply globally across the device.
6. **Advanced Tools**: Use the advanced page to inspect real-time IMS capability snapshots, restart IMS if needed, or reset CarrierConfig overrides in the Danger Zone.

## About this Project

This project originated as a fork of [Turbo1123/TurboIMS](https://github.com/Turbo1123/TurboIMS). However, due to various stability issues encountered during usage, the codebase has undergone a complete refactoring. This includes rewriting the core logic for SIM card reading and carrier configuration, as well as redesigning the UI and icons.

本项目此前以 **Mystery00/TurboIMS** 的名称发布，现以 **TensorIMS** 继续独立维护，后续不计划合并上游代码。更名仅针对本维护版本；GitHub 上保留原有 fork 关系及来源说明。

应用包名与签名配置保持不变，已安装本维护版本的用户可通过相同签名的新版本覆盖升级。使用 Obtainium 的用户可将来源更新为本仓库；如设置了 APK 文件名过滤条件，请同步调整为 TensorIMS。

## Credits

- **[vvb2060/Ims](https://github.com/vvb2060/Ims)**
- **[nullbytepl/CarrierVanityName](https://github.com/nullbytepl/CarrierVanityName)**: Carrier name modification logic is derived from this project.
- **[kyujin-cho/pixel-volte-patch](https://github.com/kyujin-cho/pixel-volte-patch)**
- The app icon is based on an original design from [iconfont](https://www.iconfont.cn/collections/detail?cid=28924), modified for use in this project.

## Disclaimer

This application modifies your device's carrier configuration and, when requested, selected device-level network settings. Use it at your own risk. The developers are not responsible for any damage or loss of functionality.

## License

This project is licensed under the Apache License 2.0. See the [LICENSE](LICENSE) file for details.
