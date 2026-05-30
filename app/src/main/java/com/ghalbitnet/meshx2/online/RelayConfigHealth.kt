package com.ghalbitnet.meshx2.online

enum class RelayConfigHealth {
    MISSING,
    INVALID,
    READY,
    FAILED;

    companion object {
        fun from(validation: RelayConfigValidation): RelayConfigHealth {
            return when (validation.state) {
                RelayConfigValidation.State.INTERNET_RELAY_READY -> READY
                RelayConfigValidation.State.INTERNET_RELAY_NOT_CONFIGURED -> MISSING
                RelayConfigValidation.State.INTERNET_RELAY_UNREACHABLE ->
                    if (validation.detail.contains("localhost", ignoreCase = true)) INVALID else FAILED
            }
        }
    }
}
