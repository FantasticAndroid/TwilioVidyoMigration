package com.twilio.video.quickstart.kotlin.vidyo

import android.content.Context
import android.util.Log
import android.widget.FrameLayout
import com.vidyo.VidyoClient.Connector.Connector


private const val TAG = "VidyoManager"
class VidyoManager(context : Context, viewId: FrameLayout) {

    private val mVidyoConnector: Connector
    private val remoteParticipants = 15

    init {
        mVidyoConnector = Connector(
            viewId,
            Connector.ConnectorViewStyle.VIDYO_CONNECTORVIEWSTYLE_Default,
            remoteParticipants,
            "warning all@VidyoConnector info@VidyoClient",
            "",
            0
        )
    }

    /**
     *
     * @param viewId FrameLayout
     * @param roomInfo RoomInfo
     */
    fun connectToRoom(viewId: FrameLayout, roomInfo: RoomInfo) {

        mVidyoConnector.showViewAt(viewId, 0, 0, viewId.width, viewId.height)
        mVidyoConnector.connectToRoomAsGuest(roomInfo.portal, roomInfo.name, roomInfo.roomKey, roomInfo.roomPin, connectListener)
    }


    private val connectListener = object : Connector.IConnect {
        override fun onSuccess() {
            Log.d(TAG, "ConnectListener onSuccess")
        }

        override fun onFailure(reason: Connector.ConnectorFailReason) {
            Log.d(TAG, "ConnectListener onFailure reason: ${reason.name}")
        }

        override fun onDisconnected(reason: Connector.ConnectorDisconnectReason) {
            Log.d(TAG, "ConnectListener onDisconnected reason: ${reason.name}")

        }
    }
}


