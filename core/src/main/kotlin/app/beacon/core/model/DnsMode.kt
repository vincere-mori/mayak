package app.beacon.core.model

import kotlinx.serialization.Serializable

@Serializable
enum class DnsMode(
    val title: String,
    val server: String,
    val path: String = "/dns-query",
    val needsDomainResolver: Boolean = false
) {
    Cloudflare("Cloudflare", "1.1.1.1"),
    Google("Google", "dns.google", needsDomainResolver = true)
}
