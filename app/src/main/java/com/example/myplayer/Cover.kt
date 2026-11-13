package com.example.myplayer

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.LruCache
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

val CoverShape = RoundedCornerShape(8.dp)

object CoverCache {
    val cache = object : LruCache<String, Bitmap>(48 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
    }
}

private fun decodeSampled(bytes: ByteArray): Bitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
    var sample = 1
    while (maxOf(bounds.outWidth, bounds.outHeight) / sample > 800) sample *= 2
    return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
}

suspend fun loadCover(ctx: Context, uri: Uri?): Bitmap? {
    if (uri == null) return null
    val k = uri.toString()
    CoverCache.cache.get(k)?.let { return it }
    return withContext(Dispatchers.IO) {
        try {
            val bytes: ByteArray? =
                if (uri.scheme == "http" || uri.scheme == "https") {
                    val c = java.net.URL(k).openConnection() as java.net.HttpURLConnection
                    try {
                        c.connectTimeout = 8000; c.readTimeout = 15000
                        if (c.responseCode in 200..299) c.inputStream.use { it.readBytes() } else null
                    } finally { c.disconnect() }
                } else ctx.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            bytes?.let { decodeSampled(it) }?.also { CoverCache.cache.put(k, it) }
        } catch (e: Throwable) { null }
    }
}

@Composable
fun Cover(uri: Uri?, modifier: Modifier, shape: Shape = CoverShape) {
    val ctx = LocalContext.current
    val p = pal
    val crop = LocalCrop.current
    val bmp by produceState<Bitmap?>(uri?.let { CoverCache.cache.get(it.toString()) }, uri) {
        value = loadCover(ctx, uri)
    }
    Box(modifier.clip(shape).background(p.card), contentAlignment = Alignment.Center) {
        Crossfade(bmp, animationSpec = tween(300), label = "cover") { b ->
            if (b != null) {
                val img = remember(b) { b.asImageBitmap() }
                Image(img, null, Modifier.fillMaxSize(), contentScale = if (crop) ContentScale.Crop else ContentScale.Fit)
            } else Ico(Icons.Rounded.MusicNote, tint = p.sub)
        }
    }
}

@Composable
fun rememberCoverColor(uri: Uri?, fallback: Color): Color {
    val ctx = LocalContext.current
    val c by produceState(fallback, uri) {
        val b = loadCover(ctx, uri)
        value = if (b == null) fallback else withContext(Dispatchers.Default) {
            try { Color(Bitmap.createScaledBitmap(b, 1, 1, true).getPixel(0, 0)) } catch (e: Throwable) { fallback }
        }
    }
    return c
}
