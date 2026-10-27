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

suspend fun loadCover(ctx: Context, uri: Uri?): Bitmap? {
    if (uri == null) return null
    val k = uri.toString()
    CoverCache.cache.get(k)?.let { return it }
    return withContext(Dispatchers.IO) {
        try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            var sample = 1
            while (maxOf(bounds.outWidth, bounds.outHeight) / sample > 800) sample *= 2
            val opts = BitmapFactory.Options().apply { inSampleSize = sample }
            ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
                ?.also { CoverCache.cache.put(k, it) }
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
