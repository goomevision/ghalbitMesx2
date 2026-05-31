package com.ghalbitnet.meshx2.verified

data class ReceiptPayload(val transactionId:String,val senderId:String,val receiverId:String,val amount:String,val timestamp:Long)