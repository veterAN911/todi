package com.todi.app.backup

class BackupException(
    val kind: Kind,
    val code: Int? = null,
    val detail: String? = null,
    cause: Throwable? = null,
) : Exception(kind.name, cause) {
    enum class Kind {
        AUTH,

        NOT_FOUND,

        NETWORK,
        RATE_LIMITED,

        CONFLICT,

        MALFORMED,
        SERVER,
    }
}
