# Changelog

## v0.2.0

- Поддержка подписок: ссылка подписки разворачивается в список серверов.
- Парсинг подписки понимает base64 и обычный текст, пропускает неподдерживаемые строки.
- Замер задержки (ping) по каждому серверу и по всей подписке сразу.
- Определение страны сервера по имени, флаги в списке серверов.
- Windows: отдельный диалог «Подписки» с выбором лучшего сервера по пингу.
- Android: вкладка «Подписки» со списком серверов и флагами.

## v0.1.5

- Fixed WARP startup on sing-box 1.13 by migrating WireGuard to `endpoints`.
- Moved WARP from Settings to the main Proxy/TUN bar and added a Gemini tooltip.
- WARP registration now stores Cloudflare reserved bytes.
- Updated lighthouse hero beam, horizon haze and sea transition.
- Added WARP endpoint test coverage.

## v0.1.4

- Desktop dev launcher now downloads checked `sing-box.exe` automatically.
- VLESS import now tolerates copied line breaks and stores Reality `spx` / `pqv` fields.
- Added Windows desktop modes: `Proxy` for partial traffic and `TUN` for full traffic.
- TUN mode checks administrator rights and can relaunch packaged Beacon elevated.

## v0.1.3

- Added Beacon lighthouse icon for Android, Windows window and Windows installer.
- README rewritten for normal users.
- Security hardening: bundled sing-box is required, old plaintext Android prefs are migrated on read, temp config is deleted before each run and on shutdown.

## v0.1.2

- sing-box config migrated from legacy DNS servers to the new DNS server format.
- Removed legacy inbound sniff fields; route actions now handle sniff and DNS hijack.
- Windows desktop UI redesigned with clearer status, cards and actions.

## v0.1.1

- Windows desktop UI обновлён под нормальное приложение.
- Windows-релизы теперь собираются installer `.exe`, без zip.

## v0.1.0

- Android MVP.
- Windows desktop MVP.
- VLESS Reality import.
- sing-box config generation.
- Android VPN через `VpnService`.
- Windows system proxy mode.
- GitHub Actions CI and release builds.
