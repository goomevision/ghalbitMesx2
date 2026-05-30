package com.ghalbitnet.meshx2.profile

import android.content.Context
import android.graphics.Bitmap
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.FrameLayout
import com.ghalbitnet.meshx2.R

class MyNameCardView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : FrameLayout(context, attrs) {
    init {
        LayoutInflater.from(context).inflate(R.layout.view_my_name_card, this, true)
    }

    fun render(profile: CommunityProfile, routeBadge: String, qrBitmap: Bitmap?) {
        ContactCardRenderer.bind(this, profile, routeBadge, qrBitmap)
    }
}
