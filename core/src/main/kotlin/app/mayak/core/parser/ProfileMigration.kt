package app.mayak.core.parser

import app.mayak.core.model.ProxyProfile

fun ProxyProfile.restoreTransport(): ProxyProfile = runCatching {
    ProfileInputParser().parse(source).copy(id = id, name = name, createdAtMillis = createdAtMillis)
}.getOrDefault(this)
