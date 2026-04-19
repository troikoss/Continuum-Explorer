package com.troikoss.continuum_explorer.utils

import java.io.IOException

class NetworkProviderException(
    message: String,
    val kind: Kind = Kind.SERVER_ERROR,
    cause: Throwable? = null,
) : IOException(message, cause) {
    enum class Kind { AUTH, UNREACHABLE, TIMEOUT, SERVER_ERROR, UNSUPPORTED }
}
