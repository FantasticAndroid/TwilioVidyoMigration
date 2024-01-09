package com.twilio.video.quickstart.kotlin

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.core.network.NetworkModule
import com.core.network.services.tokenservice.repo.TokenRepo
import kotlinx.coroutines.launch

class VideoViewModel  : ViewModel() {

    private var _tokenLiveData = MutableLiveData<String>()

    val tokenLiveData : LiveData<String> by lazy {
        _tokenLiveData
    }

    private val retrofit = NetworkModule.provideRetrofit(NetworkModule.provideOkHttpClient())
    private val tokenRepo = TokenRepo(retrofit)

    fun getToken(identity: String?, roomName: String?, passcode: String){

        viewModelScope.launch {
            _tokenLiveData.postValue(tokenRepo.getToken(identity, roomName, passcode))
        }
    }
}