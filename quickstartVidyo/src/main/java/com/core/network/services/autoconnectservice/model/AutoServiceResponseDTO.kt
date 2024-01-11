package com.core.network.services.autoconnectservice.model
data class AutoServiceResponseDTO(
    val pin: String = "",
    val roomUrl: String = "",
    val extension: String = "",
    val inviteContent: String = "",
)