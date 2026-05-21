# Beacon

Beacon - небольшой клиент для своих VLESS Reality ключей.

Проект не пытается заменить Hiddify. Идея проще: есть свой сервер, есть ключ, нужно быстро подключиться на Android или Windows без лишнего меню.

## Что умеет

- импортирует `vless://` Reality ключ
- хранит несколько профилей
- подключает Android через системный `VpnService`
- подключает Windows через локальный `sing-box` и system proxy
- в Windows есть режимы `Proxy` и `TUN`
- умеет включать WARP для Google / Gemini
- даёт выбор DNS: Cloudflare или Google
- умеет включать IPv6
- хранит ключи локально в зашифрованном виде
- собирает Windows installer `.exe`

## Скачать

Актуальные сборки лежат в [Releases](https://github.com/vincere-mori/beacon/releases).

Для Windows нужен файл:

```text
Beacon-Windows-v0.1.5.exe
```

Для Android:

```text
Beacon-Android-v0.1.5.apk
```

## Windows

Windows-версия ставится как обычное приложение.

Как работает:

- внутри installer уже лежит `sing-box`
- Beacon запускает локальный mixed proxy
- Windows system proxy переключается на `127.0.0.1:2080`
- при отключении proxy возвращается обратно

Режимы:

- `Proxy` - частичный режим через системный proxy Windows. Работают браузеры и приложения, которые уважают proxy.
- `TUN` - весь трафик через sing-box. Нужен запуск Beacon от имени администратора.
- `WARP` - отдельная кнопка рядом с режимами. Нужна для Google / Gemini.

## Android

Android-версия использует:

- `VpnService`
- `libbox`
- Android Keystore для локального хранения ключей

APK сейчас debug-signed. Для широкой раздачи нужен нормальный release signing.

## Безопасность

- реальные ключи не кладутся в репозиторий
- Android хранит профили через AES-GCM ключ из Android Keystore
- Windows хранит профили через DPAPI текущего пользователя
- произвольный sing-box JSON не импортируется
- релизный `sing-box` скачивается фиксированной версии и проверяется по SHA-256
- Windows installer не подписан сертификатом, поэтому SmartScreen может ругаться

## Сборка

Полная локальная проверка:

```powershell
.\gradlew.bat test assembleDebug :desktop:installDist
.\dev\package-windows.ps1 -Version 0.1.5 -SkipBuild
```

Быстрый запуск desktop перед сборкой installer:

```bat
dev\run-desktop-dev.bat
```

Скрипт сам скачает проверенный `sing-box.exe` в корень проекта.

Android APK:

```text
app/build/outputs/apk/debug/app-debug.apk
```

Windows installer:

```text
build/release/Beacon-Windows-v0.1.5.exe
```

## Релиз

Релиз создаётся тегом:

```powershell
git tag v0.1.5
git push origin v0.1.5
```

GitHub Actions должен собрать:

- `Beacon-Android-v0.1.5.apk`
- `Beacon-Windows-v0.1.5.exe`

Сейчас Actions на аккаунте могут не стартовать из-за billing. В таком случае релиз можно собрать локально и загрузить через `gh release create`.

## Структура

```text
core      парсинг ключей и генерация sing-box config
app       Android UI + VpnService
desktop   Windows UI + sing-box process + system proxy
dev       сборка installer и генерация иконок
```
