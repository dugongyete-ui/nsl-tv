package com.nsl.tv

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.nsl.tv.data.Prefs

/** Auto-start di TV Box bila diaktifkan di Setelan. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED && Prefs.autoStartBoot(context)) {
            val i = Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(i)
        }
    }
}
