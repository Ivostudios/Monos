package com.example.myplayer

import android.content.Context
import android.util.Log
import java.io.File

object CrashLog {
    fun install(ctx: Context) {
        val old = Thread.getDefaultUncaughtExceptionHandler()
        val app = ctx.applicationContext
        Thread.setDefaultUncaughtExceptionHandler { t, e ->
            try { File(app.filesDir, "crash.txt").writeText(Log.getStackTraceString(e).take(7000)) } catch (x: Throwable) { }
            old?.uncaughtException(t, e)
        }
    }
    fun read(ctx: Context): String? = try { File(ctx.filesDir, "crash.txt").takeIf { it.exists() }?.readText() } catch (e: Throwable) { null }
    fun clear(ctx: Context) { try { File(ctx.filesDir, "crash.txt").delete() } catch (e: Throwable) { } }
}
