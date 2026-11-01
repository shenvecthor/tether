package com.shane.tether

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import kotlin.math.min

/**
 * Profile photos as tiny square JPEG thumbnails (~5–8 KB), stored encrypted in the database
 * next to everything else, so no Firebase Storage (paid) is needed.
 */
object Avatars {
    private const val SIZE = 192

    private val _mine = MutableStateFlow<Bitmap?>(null)
    val mine: StateFlow<Bitmap?> = _mine

    fun loadLocal() {
        _mine.value = Prefs.myAvatar?.let { decode(it) }
    }

    /** Crops [uri] to a centred square thumbnail, saves it locally and uploads it (encrypted) if paired. */
    suspend fun setFromUri(ctx: Context, uri: Uri): Boolean = withContext(Dispatchers.Default) {
        val src = runCatching { load(ctx, uri) }.getOrNull() ?: return@withContext false
        val side = min(src.width, src.height)
        val square = Bitmap.createBitmap(src, (src.width - side) / 2, (src.height - side) / 2, side, side)
        val thumb = Bitmap.createScaledBitmap(square, SIZE, SIZE, true)
        val out = ByteArrayOutputStream()
        thumb.compress(Bitmap.CompressFormat.JPEG, 80, out)
        val b64 = Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
        Prefs.myAvatar = b64
        _mine.value = thumb
        withContext(Dispatchers.Main) { Cloud.saveAvatar(b64) }
        true
    }

    fun clear() {
        Prefs.myAvatar = null
        _mine.value = null
        Cloud.saveAvatar(null)
    }

    /** Re-upload after pairing (e.g. photo chosen in preview mode). */
    fun syncToCloud() {
        Prefs.myAvatar?.let { Cloud.saveAvatar(it) }
    }

    fun decode(b64: String): Bitmap? = runCatching {
        val bytes = Base64.decode(b64, Base64.NO_WRAP)
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
    }.getOrNull()

    private fun load(ctx: Context, uri: Uri): Bitmap {
        return if (Build.VERSION.SDK_INT >= 28) {
            ImageDecoder.decodeBitmap(ImageDecoder.createSource(ctx.contentResolver, uri)) { decoder, info, _ ->
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                val s = info.size
                val scale = (min(s.width, s.height) / (SIZE * 2)).coerceAtLeast(1)
                decoder.setTargetSize(s.width / scale, s.height / scale)
            }
        } else {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            var sample = 1
            while (min(bounds.outWidth, bounds.outHeight) / (sample * 2) >= SIZE * 2) sample *= 2
            val opts = BitmapFactory.Options().apply { inSampleSize = sample }
            ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
                ?: throw IllegalStateException("Couldn't read image")
        }
    }
}
