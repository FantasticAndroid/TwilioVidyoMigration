package com.twilio.video.quickstart.kotlin.vidyo

import android.net.Uri
import com.core.network.services.autoconnectservice.model.AutoServiceResponseDTO

data class RoomInfo(
    val portal: String = "",
    val name: String = "",
    val roomKey: String = "",
    val roomPin: String = "",
    val roomUrl: String = "",
    val extension: String = "",
    val inviteContent: String = ""
)

fun AutoServiceResponseDTO.mapToRoomInfo(roomName: String): RoomInfo {

    val uri = Uri.parse(this.roomUrl)

    return RoomInfo(
        portal = uri.authority.orEmpty(),
        name = roomName,
        roomKey = uri.lastPathSegment.orEmpty(),
        roomPin = this.pin,
        roomUrl = this.roomUrl,
        extension = this.extension,
        inviteContent = this.inviteContent
    )
}