# Security

Beacon хранит VPN-ключи локально.

## Сейчас

- Android: ключи шифруются через Android Keystore.
- Windows: файл профилей защищается DPAPI текущего пользователя.
- Импортируется только VLESS Reality ключ, произвольный sing-box JSON не принимается.
- Windows installer скачивает sing-box и WiX фиксированных версий, SHA-256 проверяется.
- Desktop запускает только bundled/local `sing-box.exe`, PATH не используется.
- Релизы собираются в GitHub Actions.
- В репозитории не должно быть реальных ключей, подписок, доменов или IP.

## Ограничения

- Windows поддерживает Proxy и TUN. Для TUN нужны права администратора.
- WARP-регистрация создаёт отдельные Cloudflare WireGuard credentials и хранит их только локально через DPAPI.
- Android APK пока debug-signed. Для внешней раздачи нужен release signing.

## Если нашёл проблему

Пиши владельцу репозитория напрямую. Не публикуй рабочие ключи, логи с токенами и реальные VPS-адреса в issue.
