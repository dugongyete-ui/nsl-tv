package com.nsl.tv

import android.app.Application
import android.content.Context
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class NslApp : Application() {

    override fun onCreate() {
        super.onCreate()
        instance = this
        installCrashLogger()
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        // Memori menipis → buang cache logo agar aplikasi tidak dibunuh sistem.
        if (level >= TRIM_MEMORY_RUNNING_LOW) {
            runCatching { com.nsl.tv.ui.trimLogoCache() }
        }
    }

    /**
     * Pencatat crash global: setiap error fatal ditulis ke file sebelum aplikasi berhenti,
     * sehingga bisa ditinjau di menu Setelan → Laporan error. Membantu diagnosa force-close.
     */
    private fun installCrashLogger() {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            runCatching {
                val sw = StringWriter()
                throwable.printStackTrace(PrintWriter(sw))
                val entry = buildString {
                    append("\n==== CRASH ====\n")
                    append("Waktu : ")
                    append(SimpleDateFormat("dd MMM yyyy HH:mm:ss", Locale("id")).format(Date()))
                    append("\nThread: ").append(thread.name).append('\n')
                    append("Versi : ").append(com.nsl.tv.data.Auth.APP_VERSION).append('\n')
                    append(sw.toString())
                }
                val f = File(filesDir, "crash_log.txt")
                // Jaga ukuran log: simpan maksimal ~96 KB terakhir
                if (f.length() > 96_000) f.writeText(entry.takeLast(48_000))
                else f.appendText(entry)
            }
            // Lanjutkan perilaku normal Android (dialog crash / matikan proses)
            previous?.uncaughtException(thread, throwable)
        }
    }

    companion object {
        @Volatile private var instance: NslApp? = null

        /** Aman: null bila instance belum siap (jangan pernah crash karena ini). */
        fun app(): NslApp? = instance

        /** Laporan crash terakhir bila ada. */
        fun lastCrashReport(ctx: Context): String? =
            try {
                val f = File(ctx.filesDir, "crash_log.txt")
                if (f.exists() && f.length() > 0) f.readText().trim() else null
            } catch (_: Throwable) { null }

        /** Hapus laporan crash. */
        fun clearCrashReport(ctx: Context) {
            runCatching { File(ctx.filesDir, "crash_log.txt").delete() }
        }
    }
}
