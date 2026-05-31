package com.ghalbitnet.meshx2.verified.ui

data class ProfessionalCardUiModel(
    val globalId:String,
    val displayName:String,
    val role:String,
    val community:String,
    val trustScore:Int,
    val verified:Boolean,
    val profilePhotoUri:String? = null
)