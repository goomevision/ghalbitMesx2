package com.ghalbitnet.meshx2.profile

enum class CommunityStatusType(
    val wireValue: String
) {
    AVAILABLE("available"),
    BUSY("busy"),
    EMERGENCY_HELPER("emergency_helper"),
    RELAY_OPERATOR("relay_operator"),
    OFFLINE("offline"),
    CUSTOM("custom");

    companion object {
        fun fromWireValue(value: String?): CommunityStatusType {
            return entries.firstOrNull { it.wireValue.equals(value, ignoreCase = true) } ?: AVAILABLE
        }
    }
}
