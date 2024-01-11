package com.twilio.video.quickstart.kotlin

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.preference.PreferenceManager
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.EditText
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.material.snackbar.Snackbar
import com.koushikdutta.ion.Ion
import com.twilio.audioswitch.AudioDevice
import com.twilio.audioswitch.AudioDevice.BluetoothHeadset
import com.twilio.audioswitch.AudioDevice.Earpiece
import com.twilio.audioswitch.AudioDevice.Speakerphone
import com.twilio.audioswitch.AudioDevice.WiredHeadset
import com.twilio.audioswitch.AudioSwitch
import com.twilio.video.AudioCodec
import com.twilio.video.EncodingParameters
import com.twilio.video.G722Codec
import com.twilio.video.H264Codec
import com.twilio.video.IsacCodec
import com.twilio.video.LocalAudioTrack
import com.twilio.video.LocalParticipant
import com.twilio.video.LocalVideoTrack
import com.twilio.video.OpusCodec
import com.twilio.video.PcmaCodec
import com.twilio.video.PcmuCodec
import com.twilio.video.RemoteAudioTrack
import com.twilio.video.RemoteAudioTrackPublication
import com.twilio.video.RemoteDataTrack
import com.twilio.video.RemoteDataTrackPublication
import com.twilio.video.RemoteParticipant
import com.twilio.video.RemoteVideoTrack
import com.twilio.video.RemoteVideoTrackPublication
import com.twilio.video.Room
import com.twilio.video.TwilioException
import com.twilio.video.VideoCodec
import com.twilio.video.VideoTrack
import com.twilio.video.Vp8Codec
import com.twilio.video.Vp9Codec
import com.twilio.video.ktx.Video.connect
import com.twilio.video.ktx.createLocalAudioTrack
import com.twilio.video.ktx.createLocalVideoTrack
import com.twilio.video.quickstart.kotlin.databinding.ActivityVideoBinding
import com.twilio.video.quickstart.kotlin.databinding.ContentVideoBinding
import tvi.webrtc.VideoSink
import java.util.UUID
import kotlin.collections.ArrayList
import kotlin.properties.Delegates

class VideoActivity : AppCompatActivity() {

    private val videoViewModel by viewModels<VideoViewModel>()
    private lateinit var binding : ActivityVideoBinding


    private val CAMERA_MIC_PERMISSION_REQUEST_CODE = 1
    private val TAG = "VideoActivity"
    private val CAMERA_PERMISSION_INDEX = 0
    private val MIC_PERMISSION_INDEX = 1

    /*
     * You must provide a Twilio Access Token to connect to the Video service
     */
    //private val TWILIO_ACCESS_TOKEN = BuildConfig.TWILIO_ACCESS_TOKEN
    private val ACCESS_TOKEN_SERVER = BuildConfig.TWILIO_ACCESS_TOKEN_SERVER

    /*
     * Access token used to connect. This field will be set either from the console generated token
     * or the request to the token server.
     */
    private lateinit var accessToken: String

    /*
     * A Room represents communication between a local participant and one or more participants.
     */
    private var room: Room? = null
    private var localParticipant: LocalParticipant? = null

    /*
     * AudioCodec and VideoCodec represent the preferred codec for encoding and decoding audio and
     * video.
     */
    private val audioCodec: AudioCodec
        get() {
            val audioCodecName = sharedPreferences.getString(
                SettingsActivity.PREF_AUDIO_CODEC,
                SettingsActivity.PREF_AUDIO_CODEC_DEFAULT,
            )

            return when (audioCodecName) {
                IsacCodec.NAME -> IsacCodec()
                OpusCodec.NAME -> OpusCodec()
                PcmaCodec.NAME -> PcmaCodec()
                PcmuCodec.NAME -> PcmuCodec()
                G722Codec.NAME -> G722Codec()
                else -> OpusCodec()
            }
        }
    private val videoCodec: VideoCodec
        get() {
            val videoCodecName = sharedPreferences.getString(
                SettingsActivity.PREF_VIDEO_CODEC,
                SettingsActivity.PREF_VIDEO_CODEC_DEFAULT,
            )

            return when (videoCodecName) {
                Vp8Codec.NAME -> {
                    val simulcast = sharedPreferences.getBoolean(
                        SettingsActivity.PREF_VP8_SIMULCAST,
                        SettingsActivity.PREF_VP8_SIMULCAST_DEFAULT,
                    )
                    Vp8Codec(simulcast)
                }
                H264Codec.NAME -> H264Codec()
                Vp9Codec.NAME -> Vp9Codec()
                else -> Vp8Codec()
            }
        }

    private val enableAutomaticSubscription: Boolean
        get() {
            return sharedPreferences.getBoolean(
                SettingsActivity.PREF_ENABLE_AUTOMATIC_SUBSCRIPTION,
                SettingsActivity.PREF_ENABLE_AUTOMATIC_SUBCRIPTION_DEFAULT,
            )
        }

    /*
     * Encoding parameters represent the sender side bandwidth constraints.
     */
    private val encodingParameters: EncodingParameters
        get() {
            val defaultMaxAudioBitrate = SettingsActivity.PREF_SENDER_MAX_AUDIO_BITRATE_DEFAULT
            val defaultMaxVideoBitrate = SettingsActivity.PREF_SENDER_MAX_VIDEO_BITRATE_DEFAULT
            val maxAudioBitrate = Integer.parseInt(
                sharedPreferences.getString(
                    SettingsActivity.PREF_SENDER_MAX_AUDIO_BITRATE,
                    defaultMaxAudioBitrate,
                ) ?: defaultMaxAudioBitrate,
            )
            val maxVideoBitrate = Integer.parseInt(
                sharedPreferences.getString(
                    SettingsActivity.PREF_SENDER_MAX_VIDEO_BITRATE,
                    defaultMaxVideoBitrate,
                ) ?: defaultMaxVideoBitrate,
            )

            return EncodingParameters(maxAudioBitrate, maxVideoBitrate)
        }

    /*
     * Room events listener
     */
    private val roomListener = object : Room.Listener {
        @SuppressLint("SetTextI18n")
        override fun onConnected(room: Room) {
            localParticipant = room.localParticipant
            contentBinding.videoStatusTextView.text = "Connected to ${room.name}"
            title = room.name

            // Only one participant is supported
            room.remoteParticipants.firstOrNull()?.let { addRemoteParticipant(it) }
        }

        @SuppressLint("SetTextI18n")
        override fun onReconnected(room: Room) {
            contentBinding.videoStatusTextView.text = "Connected to ${room.name}"
            contentBinding.reconnectingProgressBar.visibility = View.GONE
        }

        @SuppressLint("SetTextI18n")
        override fun onReconnecting(room: Room, twilioException: TwilioException) {
            contentBinding.videoStatusTextView.text = "Reconnecting to ${room.name}"
            contentBinding.reconnectingProgressBar.visibility = View.VISIBLE
        }

        @SuppressLint("SetTextI18n")
        override fun onConnectFailure(room: Room, e: TwilioException) {
            contentBinding.videoStatusTextView.text = "Failed to connect"
            audioSwitch.deactivate()
            initializeUI()
        }

        @SuppressLint("SetTextI18n")
        override fun onDisconnected(room: Room, e: TwilioException?) {
            localParticipant = null
            contentBinding.videoStatusTextView.text = "Disconnected from ${room.name}"
            contentBinding.reconnectingProgressBar.visibility = View.GONE
            this@VideoActivity.room = null
            // Only reinitialize the UI if disconnect was not called from onDestroy()
            if (!disconnectedFromOnDestroy) {
                audioSwitch.deactivate()
                initializeUI()
                moveLocalVideoToPrimaryView()
            }
        }

        override fun onParticipantConnected(room: Room, participant: RemoteParticipant) {
            addRemoteParticipant(participant)
        }

        override fun onParticipantDisconnected(room: Room, participant: RemoteParticipant) {
            removeRemoteParticipant(participant)
        }

        override fun onRecordingStarted(room: Room) {
            /*
             * Indicates when media shared to a Room is being recorded. Note that
             * recording is only available in our Group Rooms developer preview.
             */
            Log.d(TAG, "onRecordingStarted")
        }

        override fun onRecordingStopped(room: Room) {
            /*
             * Indicates when media shared to a Room is no longer being recorded. Note that
             * recording is only available in our Group Rooms developer preview.
             */
            Log.d(TAG, "onRecordingStopped")
        }
    }

    /*
     * RemoteParticipant events listener
     */
    private val participantListener = object : RemoteParticipant.Listener {
        @SuppressLint("SetTextI18n")
        override fun onAudioTrackPublished(
            remoteParticipant: RemoteParticipant,
            remoteAudioTrackPublication: RemoteAudioTrackPublication,
        ) {
            Log.i(
                TAG,
                "onAudioTrackPublished: " +
                    "[RemoteParticipant: identity=${remoteParticipant.identity}], " +
                    "[RemoteAudioTrackPublication: sid=${remoteAudioTrackPublication.trackSid}, " +
                    "enabled=${remoteAudioTrackPublication.isTrackEnabled}, " +
                    "subscribed=${remoteAudioTrackPublication.isTrackSubscribed}, " +
                    "name=${remoteAudioTrackPublication.trackName}]",
            )
            contentBinding.videoStatusTextView.text = "onAudioTrackAdded"
        }

        @SuppressLint("SetTextI18n")
        override fun onAudioTrackUnpublished(
            remoteParticipant: RemoteParticipant,
            remoteAudioTrackPublication: RemoteAudioTrackPublication,
        ) {
            Log.i(
                TAG,
                "onAudioTrackUnpublished: " +
                    "[RemoteParticipant: identity=${remoteParticipant.identity}], " +
                    "[RemoteAudioTrackPublication: sid=${remoteAudioTrackPublication.trackSid}, " +
                    "enabled=${remoteAudioTrackPublication.isTrackEnabled}, " +
                    "subscribed=${remoteAudioTrackPublication.isTrackSubscribed}, " +
                    "name=${remoteAudioTrackPublication.trackName}]",
            )
            contentBinding.videoStatusTextView.text = "onAudioTrackRemoved"
        }

        @SuppressLint("SetTextI18n")
        override fun onDataTrackPublished(
            remoteParticipant: RemoteParticipant,
            remoteDataTrackPublication: RemoteDataTrackPublication,
        ) {
            Log.i(
                TAG,
                "onDataTrackPublished: " +
                    "[RemoteParticipant: identity=${remoteParticipant.identity}], " +
                    "[RemoteDataTrackPublication: sid=${remoteDataTrackPublication.trackSid}, " +
                    "enabled=${remoteDataTrackPublication.isTrackEnabled}, " +
                    "subscribed=${remoteDataTrackPublication.isTrackSubscribed}, " +
                    "name=${remoteDataTrackPublication.trackName}]",
            )
            contentBinding.videoStatusTextView.text = "onDataTrackPublished"
        }

        @SuppressLint("SetTextI18n")
        override fun onDataTrackUnpublished(
            remoteParticipant: RemoteParticipant,
            remoteDataTrackPublication: RemoteDataTrackPublication,
        ) {
            Log.i(
                TAG,
                "onDataTrackUnpublished: " +
                    "[RemoteParticipant: identity=${remoteParticipant.identity}], " +
                    "[RemoteDataTrackPublication: sid=${remoteDataTrackPublication.trackSid}, " +
                    "enabled=${remoteDataTrackPublication.isTrackEnabled}, " +
                    "subscribed=${remoteDataTrackPublication.isTrackSubscribed}, " +
                    "name=${remoteDataTrackPublication.trackName}]",
            )
            contentBinding.videoStatusTextView.text = "onDataTrackUnpublished"
        }

        @SuppressLint("SetTextI18n")
        override fun onVideoTrackPublished(
            remoteParticipant: RemoteParticipant,
            remoteVideoTrackPublication: RemoteVideoTrackPublication,
        ) {
            Log.i(
                TAG,
                "onVideoTrackPublished: " +
                    "[RemoteParticipant: identity=${remoteParticipant.identity}], " +
                    "[RemoteVideoTrackPublication: sid=${remoteVideoTrackPublication.trackSid}, " +
                    "enabled=${remoteVideoTrackPublication.isTrackEnabled}, " +
                    "subscribed=${remoteVideoTrackPublication.isTrackSubscribed}, " +
                    "name=${remoteVideoTrackPublication.trackName}]",
            )
            contentBinding.videoStatusTextView.text = "onVideoTrackPublished"
        }

        @SuppressLint("SetTextI18n")
        override fun onVideoTrackUnpublished(
            remoteParticipant: RemoteParticipant,
            remoteVideoTrackPublication: RemoteVideoTrackPublication,
        ) {
            Log.i(
                TAG,
                "onVideoTrackUnpublished: " +
                    "[RemoteParticipant: identity=${remoteParticipant.identity}], " +
                    "[RemoteVideoTrackPublication: sid=${remoteVideoTrackPublication.trackSid}, " +
                    "enabled=${remoteVideoTrackPublication.isTrackEnabled}, " +
                    "subscribed=${remoteVideoTrackPublication.isTrackSubscribed}, " +
                    "name=${remoteVideoTrackPublication.trackName}]",
            )
            contentBinding.videoStatusTextView.text = "onVideoTrackUnpublished"
        }

        @SuppressLint("SetTextI18n")
        override fun onAudioTrackSubscribed(
            remoteParticipant: RemoteParticipant,
            remoteAudioTrackPublication: RemoteAudioTrackPublication,
            remoteAudioTrack: RemoteAudioTrack,
        ) {
            Log.i(
                TAG,
                "onAudioTrackSubscribed: " +
                    "[RemoteParticipant: identity=${remoteParticipant.identity}], " +
                    "[RemoteAudioTrack: enabled=${remoteAudioTrack.isEnabled}, " +
                    "playbackEnabled=${remoteAudioTrack.isPlaybackEnabled}, " +
                    "name=${remoteAudioTrack.name}]",
            )
            contentBinding.videoStatusTextView.text = "onAudioTrackSubscribed"
        }

        @SuppressLint("SetTextI18n")
        override fun onAudioTrackUnsubscribed(
            remoteParticipant: RemoteParticipant,
            remoteAudioTrackPublication: RemoteAudioTrackPublication,
            remoteAudioTrack: RemoteAudioTrack,
        ) {
            Log.i(
                TAG,
                "onAudioTrackUnsubscribed: " +
                    "[RemoteParticipant: identity=${remoteParticipant.identity}], " +
                    "[RemoteAudioTrack: enabled=${remoteAudioTrack.isEnabled}, " +
                    "playbackEnabled=${remoteAudioTrack.isPlaybackEnabled}, " +
                    "name=${remoteAudioTrack.name}]",
            )
            contentBinding.videoStatusTextView.text = "onAudioTrackUnsubscribed"
        }

        @SuppressLint("SetTextI18n")
        override fun onAudioTrackSubscriptionFailed(
            remoteParticipant: RemoteParticipant,
            remoteAudioTrackPublication: RemoteAudioTrackPublication,
            twilioException: TwilioException,
        ) {
            Log.i(
                TAG,
                "onAudioTrackSubscriptionFailed: " +
                    "[RemoteParticipant: identity=${remoteParticipant.identity}], " +
                    "[RemoteAudioTrackPublication: sid=${remoteAudioTrackPublication.trackSid}, " +
                    "name=${remoteAudioTrackPublication.trackName}]" +
                    "[TwilioException: code=${twilioException.code}, " +
                    "message=${twilioException.message}]",
            )
            contentBinding.videoStatusTextView.text = "onAudioTrackSubscriptionFailed"
        }

        @SuppressLint("SetTextI18n")
        override fun onDataTrackSubscribed(
            remoteParticipant: RemoteParticipant,
            remoteDataTrackPublication: RemoteDataTrackPublication,
            remoteDataTrack: RemoteDataTrack,
        ) {
            Log.i(
                TAG,
                "onDataTrackSubscribed: " +
                    "[RemoteParticipant: identity=${remoteParticipant.identity}], " +
                    "[RemoteDataTrack: enabled=${remoteDataTrack.isEnabled}, " +
                    "name=${remoteDataTrack.name}]",
            )
            contentBinding.videoStatusTextView.text = "onDataTrackSubscribed"
        }

        @SuppressLint("SetTextI18n")
        override fun onDataTrackUnsubscribed(
            remoteParticipant: RemoteParticipant,
            remoteDataTrackPublication: RemoteDataTrackPublication,
            remoteDataTrack: RemoteDataTrack,
        ) {
            Log.i(
                TAG,
                "onDataTrackUnsubscribed: " +
                    "[RemoteParticipant: identity=${remoteParticipant.identity}], " +
                    "[RemoteDataTrack: enabled=${remoteDataTrack.isEnabled}, " +
                    "name=${remoteDataTrack.name}]",
            )
            contentBinding.videoStatusTextView.text = "onDataTrackUnsubscribed"
        }

        @SuppressLint("SetTextI18n")
        override fun onDataTrackSubscriptionFailed(
            remoteParticipant: RemoteParticipant,
            remoteDataTrackPublication: RemoteDataTrackPublication,
            twilioException: TwilioException,
        ) {
            Log.i(
                TAG,
                "onDataTrackSubscriptionFailed: " +
                    "[RemoteParticipant: identity=${remoteParticipant.identity}], " +
                    "[RemoteDataTrackPublication: sid=${remoteDataTrackPublication.trackSid}, " +
                    "name=${remoteDataTrackPublication.trackName}]" +
                    "[TwilioException: code=${twilioException.code}, " +
                    "message=${twilioException.message}]",
            )
            contentBinding.videoStatusTextView.text = "onDataTrackSubscriptionFailed"
        }

        @SuppressLint("SetTextI18n")
        override fun onVideoTrackSubscribed(
            remoteParticipant: RemoteParticipant,
            remoteVideoTrackPublication: RemoteVideoTrackPublication,
            remoteVideoTrack: RemoteVideoTrack,
        ) {
            Log.i(
                TAG,
                "onVideoTrackSubscribed: " +
                    "[RemoteParticipant: identity=${remoteParticipant.identity}], " +
                    "[RemoteVideoTrack: enabled=${remoteVideoTrack.isEnabled}, " +
                    "name=${remoteVideoTrack.name}]",
            )
            contentBinding.videoStatusTextView.text = "onVideoTrackSubscribed"
            addRemoteParticipantVideo(remoteVideoTrack)
        }

        @SuppressLint("SetTextI18n")
        override fun onVideoTrackUnsubscribed(
            remoteParticipant: RemoteParticipant,
            remoteVideoTrackPublication: RemoteVideoTrackPublication,
            remoteVideoTrack: RemoteVideoTrack,
        ) {
            Log.i(
                TAG,
                "onVideoTrackUnsubscribed: " +
                    "[RemoteParticipant: identity=${remoteParticipant.identity}], " +
                    "[RemoteVideoTrack: enabled=${remoteVideoTrack.isEnabled}, " +
                    "name=${remoteVideoTrack.name}]",
            )
            contentBinding.videoStatusTextView.text = "onVideoTrackUnsubscribed"
            removeParticipantVideo(remoteVideoTrack)
        }

        @SuppressLint("SetTextI18n")
        override fun onVideoTrackSubscriptionFailed(
            remoteParticipant: RemoteParticipant,
            remoteVideoTrackPublication: RemoteVideoTrackPublication,
            twilioException: TwilioException,
        ) {
            Log.i(
                TAG,
                "onVideoTrackSubscriptionFailed: " +
                    "[RemoteParticipant: identity=${remoteParticipant.identity}], " +
                    "[RemoteVideoTrackPublication: sid=${remoteVideoTrackPublication.trackSid}, " +
                    "name=${remoteVideoTrackPublication.trackName}]" +
                    "[TwilioException: code=${twilioException.code}, " +
                    "message=${twilioException.message}]",
            )
            contentBinding.videoStatusTextView.text = "onVideoTrackSubscriptionFailed"
            Snackbar.make(
                binding.connectActionFab,
                "Failed to subscribe to ${remoteParticipant.identity}",
                Snackbar.LENGTH_LONG,
            )
                .show()
        }

        override fun onAudioTrackEnabled(
            remoteParticipant: RemoteParticipant,
            remoteAudioTrackPublication: RemoteAudioTrackPublication,
        ) {
        }

        override fun onVideoTrackEnabled(
            remoteParticipant: RemoteParticipant,
            remoteVideoTrackPublication: RemoteVideoTrackPublication,
        ) {
        }

        override fun onVideoTrackDisabled(
            remoteParticipant: RemoteParticipant,
            remoteVideoTrackPublication: RemoteVideoTrackPublication,
        ) {
        }

        override fun onAudioTrackDisabled(
            remoteParticipant: RemoteParticipant,
            remoteAudioTrackPublication: RemoteAudioTrackPublication,
        ) {
        }
    }

    private var localAudioTrack: LocalAudioTrack? = null
    private var localVideoTrack: LocalVideoTrack? = null
    private var alertDialog: AlertDialog? = null
    private val cameraCapturerCompat by lazy {
        CameraCapturerCompat(this, CameraCapturerCompat.Source.FRONT_CAMERA)
    }
    private val sharedPreferences by lazy {
        PreferenceManager.getDefaultSharedPreferences(this@VideoActivity)
    }

    /*
     * Audio management
     */
    private val audioSwitch by lazy {
        AudioSwitch(
            applicationContext,
            preferredDeviceList = listOf(
                BluetoothHeadset::class.java,
                WiredHeadset::class.java,
                Speakerphone::class.java,
                Earpiece::class.java,
            ),
        )
    }
    private var savedVolumeControlStream by Delegates.notNull<Int>()
    private var audioDeviceMenuItem: MenuItem? = null

    private var participantIdentity: String? = null
    private lateinit var localVideoView: VideoSink
    private var disconnectedFromOnDestroy = false
    private var isSpeakerPhoneEnabled = true

    private lateinit var contentBinding : ContentVideoBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityVideoBinding.inflate(layoutInflater)
        setContentView(binding.root)

        /*
         * Set local video view to primary view
         */
        contentBinding = ContentVideoBinding.bind(binding.root)

        localVideoView = contentBinding.primaryVideoView

        /*
         * Enable changing the volume using the up/down keys during a conversation
         */
        savedVolumeControlStream = volumeControlStream
        volumeControlStream = AudioManager.STREAM_VOICE_CALL

        /*
         * Set access token
         */
        setAccessToken()

        /*
         * Check camera and microphone permissions. Also, request for bluetooth
         * permissions for enablement of bluetooth audio routing.
         */
        if (!checkPermissionForCameraAndMicrophone()) {
            requestPermissionForCameraMicrophoneAndBluetooth()
        } else {
            audioSwitch.start { audioDevices, audioDevice -> updateAudioDeviceIcon(audioDevice) }
            createAudioAndVideoTracks()
        }
        /*
         * Set the initial state of the UI
         */
        initializeUI()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray,
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == CAMERA_MIC_PERMISSION_REQUEST_CODE) {
            /*
             * The first two permissions are Camera & Microphone, bluetooth isn't required but
             * enabling it enables bluetooth audio routing functionality.
             */
            val cameraAndMicPermissionGranted =
                (
                    (PackageManager.PERMISSION_GRANTED == grantResults[CAMERA_PERMISSION_INDEX])
                        and (PackageManager.PERMISSION_GRANTED == grantResults[MIC_PERMISSION_INDEX])
                    )

            /*
             * Due to bluetooth permissions being requested at the same time as camera and mic
             * permissions, AudioSwitch should be started after providing the user the option
             * to grant the necessary permissions for bluetooth.
             */
            audioSwitch.start { audioDevices, audioDevice -> updateAudioDeviceIcon(audioDevice) }

            if (cameraAndMicPermissionGranted) {
                createAudioAndVideoTracks()
            } else {
                Toast.makeText(
                    this,
                    R.string.permissions_needed,
                    Toast.LENGTH_LONG,
                ).show()
            }
        }
    }

    @SuppressLint("SetTextI18n")
    override fun onResume() {
        super.onResume()
        /*
         * If the local video track was released when the app was put in the background, recreate.
         */
        localVideoTrack = if (localVideoTrack == null && checkPermissionForCameraAndMicrophone()) {
            createLocalVideoTrack(
                this,
                true,
                cameraCapturerCompat,
            )
        } else {
            localVideoTrack
        }
        localVideoTrack?.addSink(localVideoView)

        /*
         * If connected to a Room then share the local video track.
         */
        localVideoTrack?.let { localParticipant?.publishTrack(it) }

        /*
         * Update encoding parameters if they have changed.
         */
        localParticipant?.setEncodingParameters(encodingParameters)

        /*
         * Update reconnecting UI
         */
        room?.let {
            contentBinding.reconnectingProgressBar.visibility = if (it.state != Room.State.RECONNECTING) {
                View.GONE
            } else
                View.VISIBLE
            if (it.state != Room.State.DISCONNECTED) contentBinding.videoStatusTextView.text = "Connected to ${it.name}"
        }
    }

    override fun onPause() {
        /*
         * If this local video track is being shared in a Room, remove from local
         * participant before releasing the video track. Participants will be notified that
         * the track has been removed.
         */
        localVideoTrack?.let { localParticipant?.unpublishTrack(it) }

        /*
         * Release the local video track before going in the background. This ensures that the
         * camera can be used by other applications while this app is in the background.
         */
        localVideoTrack?.release()
        localVideoTrack = null
        super.onPause()
    }

    override fun onDestroy() {
        /*
         * Tear down audio management and restore previous volume stream
         */
        audioSwitch.stop()
        volumeControlStream = savedVolumeControlStream

        /*
         * Always disconnect from the room before leaving the Activity to
         * ensure any memory allocated to the Room resource is freed.
         */
        room?.disconnect()
        disconnectedFromOnDestroy = true

        /*
         * Release the local audio and video tracks ensuring any memory allocated to audio
         * or video is freed.
         */
        localAudioTrack?.release()
        localVideoTrack?.release()

        super.onDestroy()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        val inflater = menuInflater
        inflater.inflate(R.menu.menu, menu)
        audioDeviceMenuItem = menu.findItem(R.id.menu_audio_device)

        // AudioSwitch has already started and thus notified of the initial selected device
        // so we need to updates the UI
        updateAudioDeviceIcon(audioSwitch.selectedAudioDevice)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.menu_settings -> startActivity(Intent(this, SettingsActivity::class.java))
            R.id.menu_audio_device -> showAudioDevices()
        }
        return true
    }

    private fun checkPermissions(permissions: Array<String>): Boolean {
        var shouldCheck = true
        for (permission in permissions) {
            shouldCheck = shouldCheck and (
                PackageManager.PERMISSION_GRANTED ==
                    ContextCompat.checkSelfPermission(this, permission)
                )
        }
        return shouldCheck
    }

    private fun requestPermissions(permissions: Array<String>) {
        var displayRational = false
        for (permission in permissions) {
            displayRational =
                displayRational or ActivityCompat.shouldShowRequestPermissionRationale(
                    this,
                    permission,
                )
        }
        if (displayRational) {
            Toast.makeText(this, R.string.permissions_needed, Toast.LENGTH_LONG).show()
        } else {
            ActivityCompat.requestPermissions(
                this,
                permissions,
                CAMERA_MIC_PERMISSION_REQUEST_CODE,
            )
        }
    }

    private fun checkPermissionForCameraAndMicrophone(): Boolean {
        return checkPermissions(
            arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO),
        )
    }

    private fun requestPermissionForCameraMicrophoneAndBluetooth() {
        val permissionsList: Array<String> = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(
                Manifest.permission.CAMERA,
                Manifest.permission.RECORD_AUDIO,
                Manifest.permission.BLUETOOTH_CONNECT,
            )
        } else {
            arrayOf(
                Manifest.permission.CAMERA,
                Manifest.permission.RECORD_AUDIO,
            )
        }
        requestPermissions(permissionsList)
    }

    private fun createAudioAndVideoTracks() {
        // Share your microphone
        localAudioTrack = createLocalAudioTrack(this, true)

        // Share your camera
        localVideoTrack = createLocalVideoTrack(
            this,
            true,
            cameraCapturerCompat,
        )
    }

    private fun setAccessToken() {
        retrieveAccessTokenfromServer()
//        if (!BuildConfig.USE_TOKEN_SERVER) {
//            /*
//             * OPTION 1 - Generate an access token from the getting started portal
//             * https://www.twilio.com/console/video/dev-tools/testing-tools and add
//             * the variable TWILIO_ACCESS_TOKEN setting it equal to the access token
//             * string in your local.properties file.
//             */
//            this.accessToken = TWILIO_ACCESS_TOKEN
//        } else {
//            /*
//             * OPTION 2 - Retrieve an access token from your own web app.
//             * Add the variable ACCESS_TOKEN_SERVER assigning it to the url of your
//             * token server and the variable USE_TOKEN_SERVER=true to your
//             * local.properties file.
//             */
//            retrieveAccessTokenfromServer()
//        }
    }

    private fun connectToRoom(roomName: String) {
        Log.d(TAG, "connectToRoom roomName: $roomName")
        audioSwitch.activate()

        room = connect(this, accessToken, roomListener) {
            roomName(roomName)
            /*
             * Add local audio track to connect options to share with participants.
             */
            audioTracks(listOf(localAudioTrack))
            /*
             * Add local video track to connect options to share with participants.
             */
            videoTracks(listOf(localVideoTrack))

            /*
             * Set the preferred audio and video codec for media.
             */
            preferAudioCodecs(listOf(audioCodec))
            preferVideoCodecs(listOf(videoCodec))

            /*
             * Set the sender side encoding parameters.
             */
            encodingParameters(encodingParameters)

            /*
             * Toggles automatic track subscription. If set to false, the LocalParticipant will receive
             * notifications of track publish events, but will not automatically subscribe to them. If
             * set to true, the LocalParticipant will automatically subscribe to tracks as they are
             * published. If unset, the default is true. Note: This feature is only available for Group
             * Rooms. Toggling the flag in a P2P room does not modify subscription behavior.
             */
            enableAutomaticSubscription(enableAutomaticSubscription)
        }
        setDisconnectAction()
    }

    /*
     * The initial state when there is no active room.
     */
    private fun initializeUI() {
        binding.connectActionFab.setImageDrawable(
            ContextCompat.getDrawable(
                this,
                R.drawable.ic_video_call_white_24dp,
            ),
        )
        binding.connectActionFab.show()
        binding.connectActionFab.setOnClickListener(connectActionClickListener())
        binding.switchCameraActionFab.show()
        binding.switchCameraActionFab.setOnClickListener(switchCameraClickListener())
        binding.localVideoActionFab.show()
        binding.localVideoActionFab.setOnClickListener(localVideoClickListener())
        binding.muteActionFab.show()
        binding.muteActionFab.setOnClickListener(muteClickListener())
    }

    /*
     * Show the current available audio devices.
     */
    private fun showAudioDevices() {
        val availableAudioDevices = audioSwitch.availableAudioDevices

        audioSwitch.selectedAudioDevice?.let { selectedDevice ->
            val selectedDeviceIndex = availableAudioDevices.indexOf(selectedDevice)
            val audioDeviceNames = ArrayList<String>()

            for (a in availableAudioDevices) {
                audioDeviceNames.add(a.name)
            }

            AlertDialog.Builder(this)
                .setTitle(R.string.room_screen_select_device)
                .setSingleChoiceItems(
                    audioDeviceNames.toTypedArray<CharSequence>(),
                    selectedDeviceIndex,
                ) { dialog, index ->
                    dialog.dismiss()
                    val selectedAudioDevice = availableAudioDevices[index]
                    updateAudioDeviceIcon(selectedAudioDevice)
                    audioSwitch.selectDevice(selectedAudioDevice)
                }.create().show()
        }
    }

    /*
     * Update the menu icon based on the currently selected audio device.
     */
    private fun updateAudioDeviceIcon(selectedAudioDevice: AudioDevice?) {
        val audioDeviceMenuIcon = when (selectedAudioDevice) {
            is BluetoothHeadset -> R.drawable.ic_bluetooth_white_24dp
            is WiredHeadset -> R.drawable.ic_headset_mic_white_24dp
            is Speakerphone -> R.drawable.ic_volume_up_white_24dp
            else -> R.drawable.ic_phonelink_ring_white_24dp
        }

        audioDeviceMenuItem?.setIcon(audioDeviceMenuIcon)
    }

    /*
     * The actions performed during disconnect.
     */
    private fun setDisconnectAction() {
        binding.connectActionFab.setImageDrawable(
            ContextCompat.getDrawable(
                this,
                R.drawable.ic_call_end_white_24px,
            ),
        )
        binding.connectActionFab.show()
        binding.connectActionFab.setOnClickListener(disconnectClickListener())
    }

    /*
     * Creates an connect UI dialog
     */
    private fun showConnectDialog() {
        val roomEditText = EditText(this)
        alertDialog = createConnectDialog(
            roomEditText,
            connectClickListener(roomEditText),
            cancelConnectDialogClickListener(),
            this,
        )
        alertDialog?.show()
    }

    /*
     * Called when participant joins the room
     */
    @SuppressLint("SetTextI18n")
    private fun addRemoteParticipant(remoteParticipant: RemoteParticipant) {
        /*
         * This app only displays video for one additional participant per Room
         */
        if (contentBinding.thumbnailVideoView.visibility == View.VISIBLE) {
            Snackbar.make(
                binding.connectActionFab,
                "Multiple participants are not currently support in this UI",
                Snackbar.LENGTH_LONG,
            )
                .setAction("Action", null).show()
            return
        }
        participantIdentity = remoteParticipant.identity
        contentBinding.videoStatusTextView.text = "Participant $participantIdentity joined"

        /*
         * Add participant renderer
         */
        remoteParticipant.remoteVideoTracks.firstOrNull()?.let { remoteVideoTrackPublication ->
            if (remoteVideoTrackPublication.isTrackSubscribed) {
                remoteVideoTrackPublication.remoteVideoTrack?.let { addRemoteParticipantVideo(it) }
            }
        }

        /*
         * Start listening for participant events
         */
        remoteParticipant.setListener(participantListener)
    }

    /*
     * Set primary view as renderer for participant video track
     */
    private fun addRemoteParticipantVideo(videoTrack: VideoTrack) {
        moveLocalVideoToThumbnailView()
        contentBinding.primaryVideoView.mirror = false
        videoTrack.addSink(contentBinding.primaryVideoView)
    }

    private fun moveLocalVideoToThumbnailView() {
        if (contentBinding.thumbnailVideoView.visibility == View.GONE) {
            contentBinding.thumbnailVideoView.visibility = View.VISIBLE
            with(localVideoTrack) {
                this?.removeSink(contentBinding.primaryVideoView)
                this?.addSink(contentBinding.thumbnailVideoView)
            }
            localVideoView = contentBinding.thumbnailVideoView
            contentBinding.thumbnailVideoView.mirror = cameraCapturerCompat.cameraSource ==
                CameraCapturerCompat.Source.FRONT_CAMERA
        }
    }

    /*
     * Called when participant leaves the room
     */
    @SuppressLint("SetTextI18n")
    private fun removeRemoteParticipant(remoteParticipant: RemoteParticipant) {
        contentBinding.videoStatusTextView.text = "Participant $remoteParticipant.identity left."
        if (remoteParticipant.identity != participantIdentity) {
            return
        }

        /*
         * Remove participant renderer
         */
        remoteParticipant.remoteVideoTracks.firstOrNull()?.let { remoteVideoTrackPublication ->
            if (remoteVideoTrackPublication.isTrackSubscribed) {
                remoteVideoTrackPublication.remoteVideoTrack?.let { removeParticipantVideo(it) }
            }
        }
        moveLocalVideoToPrimaryView()
    }

    private fun removeParticipantVideo(videoTrack: VideoTrack) {
        videoTrack.removeSink(contentBinding.primaryVideoView)
    }

    private fun moveLocalVideoToPrimaryView() {
        if (contentBinding.thumbnailVideoView.visibility == View.VISIBLE) {
            contentBinding.thumbnailVideoView.visibility = View.GONE
            with(localVideoTrack) {
                this?.removeSink(contentBinding.thumbnailVideoView)
                this?.addSink(contentBinding.primaryVideoView)
            }
            localVideoView = contentBinding.primaryVideoView
            contentBinding.primaryVideoView.mirror = cameraCapturerCompat.cameraSource ==
                CameraCapturerCompat.Source.FRONT_CAMERA
        }
    }

    private fun connectClickListener(roomEditText: EditText): DialogInterface.OnClickListener {
        return DialogInterface.OnClickListener { _, _ ->
            /*
             * Connect to room
             */
            connectToRoom(roomEditText.text.toString())
        }
    }

    private fun disconnectClickListener(): View.OnClickListener {
        return View.OnClickListener {
            /*
             * Disconnect from room
             */
            room?.disconnect()
            initializeUI()
        }
    }

    private fun connectActionClickListener(): View.OnClickListener {
        return View.OnClickListener { showConnectDialog() }
    }

    private fun cancelConnectDialogClickListener(): DialogInterface.OnClickListener {
        return DialogInterface.OnClickListener { _, _ ->
            initializeUI()
            alertDialog?.dismiss()
        }
    }

    private fun switchCameraClickListener(): View.OnClickListener {
        return View.OnClickListener {
            val cameraSource = cameraCapturerCompat.cameraSource
            cameraCapturerCompat.switchCamera()
            if (contentBinding.thumbnailVideoView.visibility == View.VISIBLE) {
                contentBinding.thumbnailVideoView.mirror = cameraSource == CameraCapturerCompat.Source.BACK_CAMERA
            } else {
                contentBinding.primaryVideoView.mirror = cameraSource == CameraCapturerCompat.Source.BACK_CAMERA
            }
        }
    }

    private fun localVideoClickListener(): View.OnClickListener {
        return View.OnClickListener {
            /*
             * Enable/disable the local video track
             */
            localVideoTrack?.let {
                val enable = !it.isEnabled
                it.enable(enable)
                val icon: Int
                if (enable) {
                    icon = R.drawable.ic_videocam_white_24dp
                    binding.switchCameraActionFab.show()
                } else {
                    icon = R.drawable.ic_videocam_off_black_24dp
                    binding.switchCameraActionFab.hide()
                }
                binding.localVideoActionFab.setImageDrawable(
                    ContextCompat.getDrawable(this@VideoActivity, icon),
                )
            }
        }
    }

    private fun muteClickListener(): View.OnClickListener {
        return View.OnClickListener {
            /*
             * Enable/disable the local audio track. The results of this operation are
             * signaled to other Participants in the same Room. When an audio track is
             * disabled, the audio is muted.
             */
            localAudioTrack?.let {
                val enable = !it.isEnabled
                it.enable(enable)
                val icon = if (enable) {
                    R.drawable.ic_mic_white_24dp
                } else
                    R.drawable.ic_mic_off_black_24dp
                binding.muteActionFab.setImageDrawable(
                    ContextCompat.getDrawable(
                        this@VideoActivity,
                        icon,
                    ),
                )
            }
        }
    }

    private fun retrieveAccessTokenfromServer() {
        Log.d(TAG, "retrieveAccessTokenfromServer")
        videoViewModel.tokenLiveData.observe(this){
            this@VideoActivity.accessToken = it
            Log.d(TAG, "retrieveAccessTokenfromServer token: $it")
        }

        videoViewModel.getToken("jain","Wed10Jan", "21269871406554")


//        Ion.with(this)
//            .load("$ACCESS_TOKEN_SERVER?identity=${UUID.randomUUID()}")
//            .asString()
//            .setCallback { e, token ->
//                if (e == null) {
//                    this@VideoActivity.accessToken = token
//                } else {
//                    Toast.makeText(
//                        this@VideoActivity,
//                        R.string.error_retrieving_access_token,
//                        Toast.LENGTH_LONG,
//                    )
//                        .show()
//                }
//            }
    }

    private fun createConnectDialog(
        participantEditText: EditText,
        callParticipantsClickListener: DialogInterface.OnClickListener,
        cancelClickListener: DialogInterface.OnClickListener,
        context: Context,
    ): AlertDialog {
        val alertDialogBuilder = AlertDialog.Builder(context).apply {
            setIcon(R.drawable.ic_video_call_white_24dp)
            setTitle("Connect to a room")
            setPositiveButton("Connect", callParticipantsClickListener)
            setNegativeButton("Cancel", cancelClickListener)
            setCancelable(false)
        }

        setRoomNameFieldInDialog(participantEditText, alertDialogBuilder, context)

        return alertDialogBuilder.create()
    }

    @SuppressLint("RestrictedApi")
    private fun setRoomNameFieldInDialog(
        roomNameEditText: EditText,
        alertDialogBuilder: AlertDialog.Builder,
        context: Context,
    ) {
        roomNameEditText.hint = "room name"
        val horizontalPadding =
            context.resources.getDimensionPixelOffset(R.dimen.activity_horizontal_margin)
        val verticalPadding =
            context.resources.getDimensionPixelOffset(R.dimen.activity_vertical_margin)
        alertDialogBuilder.setView(
            roomNameEditText,
            horizontalPadding,
            verticalPadding,
            horizontalPadding,
            0,
        )
    }
}