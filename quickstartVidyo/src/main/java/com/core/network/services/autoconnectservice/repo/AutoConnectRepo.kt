package com.core.network.services.autoconnectservice.repo

import android.util.Log
import com.core.network.Constant
import com.core.network.IApiService
import com.twilio.video.quickstart.kotlin.vidyo.RoomInfo
import com.twilio.video.quickstart.kotlin.vidyo.mapToRoomInfo
import retrofit2.HttpException

private const val TAG = "AutoConnectRepo"
class AutoConnectRepo(private val apiService: IApiService) {

    suspend fun getRoomInfo(roomName:String): RoomInfo? {

        return try {
            val autoServiceResponseDTO = apiService.getAutoConnectRoomInfo()
            Log.d(TAG, "getRoomInfo roomInfo: $autoServiceResponseDTO")
            autoServiceResponseDTO.mapToRoomInfo(roomName)



        } catch (httpException: HttpException) {
            Log.e(TAG, "getRoomInfo httpException: ${httpException.message()}")
            httpException.printStackTrace()
            null
        }
    }
}