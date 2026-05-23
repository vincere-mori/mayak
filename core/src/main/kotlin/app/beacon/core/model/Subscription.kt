package app.beacon.core.model

import kotlinx.serialization.Serializable

@Serializable
data class Subscription(
    val id: String,
    val name: String,
    val url: String,
    val profiles: List<ProxyProfile> = emptyList(),
    val updatedAtMillis: Long = 0L
)
