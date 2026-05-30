package com.ghalbitnet.meshx2.profile

import com.ghalbitnet.meshx2.R

enum class ContactCardTheme(
    val themeId: String,
    val cardBackgroundRes: Int,
    val accentColor: String
) {
    OCEAN(
        themeId = "ocean",
        cardBackgroundRes = R.drawable.bg_name_card_ocean,
        accentColor = "#48D6E8"
    ),
    MIDNIGHT_GOLD(
        themeId = "midnight_gold",
        cardBackgroundRes = R.drawable.bg_name_card_midnight_gold,
        accentColor = "#F2C464"
    ),
    FOREST(
        themeId = "forest",
        cardBackgroundRes = R.drawable.bg_name_card_forest,
        accentColor = "#55D6A3"
    ),
    AURORA(
        themeId = "aurora",
        cardBackgroundRes = R.drawable.bg_name_card_aurora,
        accentColor = "#9B6BFF"
    ),
    LIGHT(
        themeId = "light",
        cardBackgroundRes = R.drawable.bg_name_card_light,
        accentColor = "#3EA6FF"
    );

    companion object {
        fun fromId(value: String?): ContactCardTheme {
            return entries.firstOrNull { it.themeId.equals(value, ignoreCase = true) } ?: OCEAN
        }
    }
}
