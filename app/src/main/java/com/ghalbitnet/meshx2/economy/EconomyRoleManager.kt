package com.ghalbitnet.meshx2.economy

import android.content.Context
import com.ghalbitnet.meshx2.R

object EconomyRoleManager {

    enum class Role {
        USER,
        BUILDER
    }

    fun classify(globalId: String): Role {
        return if (globalId.trim().uppercase() == "BUILDER_FOUNDATION") {
            Role.BUILDER
        } else {
            Role.USER
        }
    }

    fun displayName(
        context: Context,
        roleName: String
    ): String {
        return when (roleName.trim().uppercase()) {
            Role.BUILDER.name -> context.getString(R.string.economy_role_builder)
            else -> context.getString(R.string.economy_role_user)
        }
    }
}
