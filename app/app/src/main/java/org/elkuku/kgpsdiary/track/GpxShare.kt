package org.elkuku.kgpsdiary.track

import android.content.ClipData
import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import java.io.File
import org.elkuku.kgpsdiary.data.Gpx
import org.elkuku.kgpsdiary.data.Track

/**
 * Exports a track as a GPX file through Android's share menu: to Files or Drive, a messenger, or
 * a map app like OsmAnd. The file lives in the app's cache, shared by a content URI.
 */
object GpxShare {
    /** The FileProvider's authority, as in the manifest. */
    private fun authority(context: Context) = "${context.packageName}.exports"

    fun intent(context: Context, track: Track, title: String): Intent {
        val directory = File(context.cacheDir, "exports").apply { mkdirs() }
        // One export at a time: the old files are not needed anymore
        directory.listFiles()?.forEach { it.delete() }
        val file = File(directory, Gpx.fileName(track)).apply { writeText(Gpx.of(track)) }
        val uri = FileProvider.getUriForFile(context, authority(context), file)

        val send = Intent(Intent.ACTION_SEND)
            .setType("application/gpx+xml")
            .putExtra(Intent.EXTRA_STREAM, uri)
            .putExtra(Intent.EXTRA_SUBJECT, track.name)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        send.clipData = ClipData.newRawUri(track.name, uri)

        return Intent.createChooser(send, title)
    }
}
