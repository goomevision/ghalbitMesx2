package com.ghalbitnet.meshx2.verified

object VerifyLinkOpener {
    fun build(link: UniversalVerifyLink): String = link.toUrl()
}
