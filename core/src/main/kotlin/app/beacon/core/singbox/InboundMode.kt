package app.beacon.core.singbox

import kotlinx.serialization.Serializable

@Serializable
enum class InboundMode(val title: String, val hint: String) {
    Mixed(
        "Только браузер и приложения",
        "Системный прокси Windows на 127.0.0.1:2080. Через VPN пойдёт только то, что слушает прокси: Chrome/Edge/Firefox и большинство программ. Игры, торренты и приложения, игнорирующие системный прокси, пойдут напрямую. Не требует прав администратора."
    ),
    Tun(
        "Весь трафик через VPN",
        "Виртуальный сетевой адаптер перехватывает весь трафик компьютера — браузер, игры, мессенджеры, всё. Требует запуск от имени администратора, Beacon сам предложит перезапуститься."
    );

    override fun toString(): String = title
}
