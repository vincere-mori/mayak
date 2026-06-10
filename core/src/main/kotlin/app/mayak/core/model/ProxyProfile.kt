package app.mayak.core.model

import kotlinx.serialization.Serializable

@Serializable
data class ProxyProfile(
    val id: String,
    val name: String,
    val kind: ProfileKind,
    val source: String,
    val host: String,
    val port: Int,
    val createdAtMillis: Long,
    val vless: VlessRealityProfile? = null
)
