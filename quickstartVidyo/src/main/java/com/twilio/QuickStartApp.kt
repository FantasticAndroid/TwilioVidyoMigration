package com.twilio

import android.app.Application
import com.vidyo.VidyoClient.Connector.ConnectorPkg

class QuickStartApp : Application() {

    override fun onCreate() {
        super.onCreate()
        ConnectorPkg.setApplicationUIContext(this)
        ConnectorPkg.initialize()
    }
}