package app.mayak.data

import app.mayak.core.model.DnsMode
import app.mayak.core.model.RoutingSettings
import kotlinx.serialization.Serializable

@Serializable
data class MayakSettings(
    val dnsMode: DnsMode = DnsMode.Cloudflare,
    val customDnsServers: List<String> = emptyList(),
    val ipv6Enabled: Boolean = false,
    val routing: RoutingSettings = RoutingSettings.defaults()
)
