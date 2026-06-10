package app.mayak.core.model

import kotlinx.serialization.Serializable

@Serializable
data class VlessRealityProfile(
    val uuid: String,
    val server: String,
    val port: Int,
    val serverName: String,
    val publicKey: String,
    val shortId: String?,
    val fingerprint: String,
    val flow: String?,
    val spiderX: String? = null,
    val postQuantumVerify: String? = null,
    val displayName: String
)
