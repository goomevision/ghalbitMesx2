package com.ghalbitnet.meshx2.verified

object VerifiedCardBinder {
    fun buildDisplaySummary(state: VerifiedCardViewState): String {
        return "${state.nameText} | ${state.roleText} | ${state.statusText}"
    }
}
