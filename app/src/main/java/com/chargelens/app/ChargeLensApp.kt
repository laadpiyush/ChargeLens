package com.chargelens.app

import android.app.Application
import com.chargelens.app.service.Notifications
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class ChargeLensApp : Application() {

    @Inject lateinit var notifications: Notifications

    override fun onCreate() {
        super.onCreate()
        notifications.createChannels()
    }
}
