package com.core.network.services.tokenservice.repo

import android.util.Log
import com.core.network.Constant
import com.core.network.IApiService
import com.core.network.services.tokenservice.model.AuthServiceRequestDTO
import retrofit2.HttpException

private const val TAG = "TokenRepo"
class TokenRepo(private val apiService: IApiService) {

    suspend fun getToken(identity: String?, roomName: String?, passcode: String): String? {

        return try {
            val (requestBody, url) = buildRequest(passcode, identity, roomName)
            apiService.getToken(url, requestBody).let { response ->

                Log.d(TAG, "getToken Token Found: ${response.token}")
                response.token
//                    return handleResponse(response)
//                        ?: throw AuthServiceException(message = "Token cannot be null")
            }
        } catch (httpException: HttpException) {
            Log.e(TAG, "getToken httpException: ${httpException.message()}")
            httpException.printStackTrace()
            //handleException(httpException)
            httpException.message()
        }
    }

    private fun buildRequest(
        passcode: String,
        identity: String?,
        roomName: String?,
    ): Pair<AuthServiceRequestDTO, String> {
        val requestBody = roomName?.let { roomName ->
            AuthServiceRequestDTO(passcode, identity, roomName, true)
        } ?: AuthServiceRequestDTO(passcode, identity)
        val appId = passcode.substring(6, 10)
        val serverlessId = passcode.substring(10)
        val url = if (passcode.length == Constant.PASSCODE_SIZE) {
            "${Constant.URL_PREFIX}$appId-$serverlessId${Constant.URL_SUFFIX}"
        } else {
            "${Constant.URL_PREFIX}$appId${Constant.URL_SUFFIX}"
        }
        return Pair(requestBody, url)
    }
}