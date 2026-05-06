# ESP Remote Control — Android

Android port of [ESPRemoteControl](https://github.com/KoStard/ESPRemoteControl) by [@KoStard](https://github.com/KoStard).

> Original project by KoStard — iOS app + ESP32-S3 firmware for using your phone as a wireless keyboard via Bluetooth LE.  
> This repository contains an Android version of the app. The ESP32-S3 firmware is used **without any changes**.

---

## What's new in the Android version

- ⌨️ Keyboard with EN / RU (ЙЦУКЕН) layout support
- 🖱️ Trackpad — move cursor, tap to click, scroll
- ⚡ Hotkeys — editing, window management, admin shortcuts, media controls
- 🔄 Automatic Bluetooth reconnection

---

## Requirements

- **ESP32-S3** board (tested on Super Mini)
- Android 6.0 or higher with Bluetooth LE support

---

## How to use

### 1. Flash ESP32-S3 firmware

Download the original firmware from the original repository:  
👉 **[KoStard/ESPRemoteControl](https://github.com/KoStard/ESPRemoteControl)**

- Open `sketch_uid_keyboard_ble.ino` in Arduino IDE
- Install library: **NimBLE-Arduino** (via Library Manager)
- Tools → USB Mode → **USB-OTG (TinyUSB)**
- Board: **ESP32S3 Dev Module** (or Super Mini)
- Flash the board

### 2. Install Android app

- Open this project in **Android Studio**
- Click `Run` → install on your phone
- Grant Bluetooth permission when prompted

Or download the APK from [Releases](../../releases).

### 3. Connect ESP32 to your PC via USB

### 4. Use it!

- The app will automatically find `KBBridge-ESP32S3` and connect
- Type in the text field — text appears on your PC in real time
- Switch between Keyboard / Trackpad / Hotkeys tabs
- For Russian input: switch PC layout to RU first (Win+Space), then select 🇷🇺 RU in the app

---

## BLE Protocol

Exact port of the v2 protocol from the original:

- Packet header: `[0xAA, 0x01]`
- Frames: `[cmd, len, payload...]`
- Service UUID: `2D2A0001-8A5A-4E76-A2E3-1E57D9A1B001`
- Characteristic UUID: `2D2A0002-8A5A-4E76-A2E3-1E57D9A1B001`

---

## Credits

- Original iOS app and ESP32 firmware: **[KoStard](https://github.com/KoStard/ESPRemoteControl)**
- Android port: **[snake9x](https://github.com/snake9x)**

## License

This project is licensed under the **MIT License** — see the [LICENSE](LICENSE) file for details.  
Original project © KoStard — MIT License.
