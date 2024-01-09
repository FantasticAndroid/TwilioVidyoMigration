package com.core.network.services.tokenservice.model

import com.google.gson.annotations.SerializedName

data class AuthServiceResponseDTO(
    val token: String? = null,
    //@SerializedName("room_type") val topology: Topology? = null,
)