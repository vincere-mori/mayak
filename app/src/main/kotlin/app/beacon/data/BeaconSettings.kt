package app.beacon.data

import app.beacon.core.model.DnsMode
import kotlinx.serialization.Serializable

@Serializable
data class BeaconSettings(
    val dnsMode: DnsMode = DnsMode.Cloudflare,
    val ipv6Enabled: Boolean = false
)
