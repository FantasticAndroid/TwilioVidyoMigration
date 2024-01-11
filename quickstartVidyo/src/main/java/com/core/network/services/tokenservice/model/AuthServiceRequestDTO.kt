package com.core.network.services.tokenservice.model

data class AuthServiceRequestDTO(
	val passcode: String? = null,
	val user_identity: String? = null,
	val room_name: String? = null,
	val create_room: Boolean = false,
)
