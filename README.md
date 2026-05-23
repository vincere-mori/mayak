# Beacon

Клиент для VLESS Reality — минималистичный, без лишних настроек. Работает на Windows и Android.

Суть проста: у тебя есть свой сервер и ключ от него. Beacon берёт этот ключ, поднимает туннель и не лезет куда не просят. Никаких аккаунтов, облаков, телеметрии и подписок.

---

## Что внутри

**Windows** — нативное приложение с анимированным интерфейсом. Два режима:

- `Proxy` — туннель только для браузеров и приложений, которые умеют работать через системный прокси. Не нужны права администратора.
- `TUN` — весь трафик системы идёт через сервер, включая игры и мессенджеры. Нужен запуск от администратора.

Кнопка `WARP` рядом с режимами — отдельный маршрут для Google и Gemini через Cloudflare WireGuard, если они плохо работают через основной сервер.

**Android** — использует системный `VpnService`. Работает в фоне, ключи хранятся в Android Keystore.

---

## Как начать

### Нужен свой сервер

Beacon — клиент, а не сервис. Сервер нужно поднять самому: обычный VPS + Xray или sing-box с VLESS Reality. Хороший старт — [официальная документация Xray](https://xtls.github.io) или любой гайд по «VLESS Reality selfhosted».

### Скачать

Актуальные сборки — в [Releases](https://github.com/vincere-mori/beacon/releases).

| Платформа | Файл |
|-----------|------|
| Windows | `Beacon-Windows-v0.2.0.exe` |
| Android | `Beacon-Android-v0.2.0.apk` |

### Подключение

1. Скопируй ключ вида `vless://...` из конфига своего сервера.
2. Открой Beacon → «Управление ключами» → вставь ключ.
3. Выбери режим (Proxy или TUN) и нажми «Подключить».

На Windows при первом запуске в TUN-режиме приложение само попросит права администратора.

---

## Безопасность

Ключи нигде не передаются — хранятся только локально:
- Windows: через DPAPI текущего пользователя.
- Android: через AES-GCM ключ из Android Keystore.

`sing-box.exe` в installer — фиксированная версия, проверяется по SHA-256 при сборке. Произвольный JSON в Beacon не импортируется, только `vless://` ссылки.

Windows installer не имеет подписи сертификатом, поэтому SmartScreen при установке может предупредить — это ожидаемо.

---

## Сборка

Для сборки нужны JDK 21+ и Android SDK.

Запуск desktop без сборки installer:

```bat
dev\run-desktop-dev.bat
```

Полная сборка Windows installer:

```bat
dev\build-windows.bat 0.2.0
```

Android APK:

```powershell
.\gradlew.bat assembleDebug
# app/build/outputs/apk/debug/app-debug.apk
```

---

## Структура

```
core       парсинг VLESS-ключей, генерация sing-box config
app        Android: UI + VpnService
desktop    Windows: UI + sing-box process + system proxy
dev        сборка installer, генерация иконок
```

---

## Стек

- Kotlin Multiplatform (JVM + Android)
- Jetpack Compose — Android UI
- Swing + FlatLaf — Windows UI
- [sing-box](https://github.com/SagerNet/sing-box) — ядро туннеля
