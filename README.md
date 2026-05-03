# ESP Remote Control — Android

Android-порт iOS-приложения ESPRemoteControl.

## Как использовать

### 1. Прошивка ESP32-S3 (без изменений от оригинала)
- Открой `sketch_uid_keyboard_ble.ino` в Arduino IDE
- Установи библиотеку **NimBLE-Arduino**
- В Tools → USB Mode → **USB-OTG (TinyUSB)**
- Выбери плату: **ESP32S3 Dev Module** (или Super Mini)
- Прошей

### 2. Android-приложение
- Открой папку `ESPRemoteControl` в **Android Studio**
- Нажми `Run` → установи на телефон
- Разреши доступ к Bluetooth

### 3. Подключи ESP32 к компьютеру по USB

### 4. Пользуйся!
- Приложение автоматически найдёт устройство `KBBridge-ESP32S3`
- Пиши в поле — текст появится на компьютере в реальном времени
- Кнопки: Enter, Backspace, Tab, Esc, стрелки

## Протокол
Точный порт протокола v2 из оригинала:
- Заголовок пакета: `[0xAA, 0x01]`
- Фреймы: `[cmd, len, payload...]`
- UUID сервиса: `2D2A0001-8A5A-4E76-A2E3-1E57D9A1B001`
- UUID характеристики: `2D2A0002-8A5A-4E76-A2E3-1E57D9A1B001`
