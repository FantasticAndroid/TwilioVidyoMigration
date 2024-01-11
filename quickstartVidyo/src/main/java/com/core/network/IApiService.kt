package com.core.network

import com.core.network.services.autoconnectservice.model.AutoServiceResponseDTO
import com.core.network.services.tokenservice.model.AuthServiceRequestDTO
import com.core.network.services.tokenservice.model.AuthServiceResponseDTO
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.Url


interface IApiService {

    @POST
    suspend fun getToken(
        @Url url: String,
        @Body authServiceRequestDTO: AuthServiceRequestDTO,
    ): AuthServiceResponseDTO

    @POST
    suspend fun getAutoConnectRoomInfo(@Url url:String = Constant.AUTO_CONNECT_ROOM_URL) : AutoServiceResponseDTO
}