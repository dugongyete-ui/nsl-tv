package com.nsl.tv

import android.app.Application

class NslApp : Application() {
    override fun onCreate() {
        super.onCreate()
        instance = this
    }

    companion object {
        @Volatile private var instance: NslApp? = null
        fun app(): NslApp = instance!!
    }
}
