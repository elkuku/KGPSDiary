package org.elkuku.kgpsdiary.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import java.io.ByteArrayOutputStream
import java.io.IOException
import kotlin.math.max
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Photos for the upload: at most [MAX_SIZE] pixels as JPEG, so a phone photo of several MB
 * becomes a few hundred KB. The server would scale them too, but this saves mobile data.
 */
object PhotoScaler {
    const val MAX_SIZE = 2048

    /** The longer side scaled to [max] at most, keeping the aspect ratio. */
    fun targetSize(width: Int, height: Int, max: Int = MAX_SIZE): Pair<Int, Int> {
        val factor = minOf(1.0, max.toDouble() / max(width, height))
        return (width * factor).roundToInt().coerceAtLeast(1) to (height * factor).roundToInt().coerceAtLeast(1)
    }

    /**
     * The image at [uri] as JPEG. Android 9 and newer decode, turn (EXIF) and scale it here;
     * older versions send the original, which the server turns and scales.
     *
     * @throws IOException if the image cannot be read
     */
    suspend fun jpeg(context: Context, uri: Uri): ByteArray = withContext(Dispatchers.IO) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
            return@withContext context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                ?: throw IOException("Cannot open $uri")
        }

        val bitmap = ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri)) { decoder, info, _ ->
            val (width, height) = targetSize(info.size.width, info.size.height)
            decoder.setTargetSize(width, height)
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
        }
        ByteArrayOutputStream().use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 85, out)
            bitmap.recycle()
            out.toByteArray()
        }
    }
}
