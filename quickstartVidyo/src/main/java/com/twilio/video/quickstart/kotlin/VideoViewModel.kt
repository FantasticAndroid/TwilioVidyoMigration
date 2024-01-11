package com.twilio.video.quickstart.kotlin

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.core.network.NetworkModule
import com.core.network.services.autoconnectservice.repo.AutoConnectRepo
import com.core.network.services.tokenservice.repo.TokenRepo
import com.twilio.video.quickstart.kotlin.vidyo.RoomInfo
import kotlinx.coroutines.launch

private const val TAG = "VideoViewModel"
class VideoViewModel  : ViewModel() {

    private var _tokenLiveData = MutableLiveData<String>()

    val tokenLiveData : LiveData<String> by lazy {
        _tokenLiveData
    }

    private var _autoRoomInfoLiveData = MutableLiveData<RoomInfo?>()

    val autoRoomInfoLiveData : LiveData<RoomInfo?> by lazy {
        _autoRoomInfoLiveData
    }

    private val retrofit = NetworkModule.provideRetrofit(NetworkModule.provideOkHttpClient())
    private val tokenRepo = TokenRepo(retrofit)
    private val autoConnectRepo by lazy { AutoConnectRepo(retrofit)  }

    fun getToken(identity: String?, roomName: String?, passcode: String){
        Log.d(TAG, "identity: $identity, roomName: $roomName, passcode: $passcode")
        viewModelScope.launch {
            _tokenLiveData.postValue(tokenRepo.getToken(identity, roomName, passcode))
        }
    }

    fun getAutoConnectRoomInfo(roomName: String){
        Log.d(TAG, "getAutoConnectRoomInfo roomName: $roomName")
        viewModelScope.launch {
            _autoRoomInfoLiveData.postValue(autoConnectRepo.getRoomInfo(roomName))

        }
    }
}