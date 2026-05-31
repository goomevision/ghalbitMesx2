package com.ghalbitnet.meshx2.verified.ui

object VerifiedBadgeRenderer {
    fun render(verified:Boolean):String {
        return if (verified) "VERIFIED ✓" else "UNVERIFIED"
    }
}
