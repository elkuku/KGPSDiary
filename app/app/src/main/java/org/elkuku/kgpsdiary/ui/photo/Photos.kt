package org.elkuku.kgpsdiary.ui.photo

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil3.compose.AsyncImage
import org.elkuku.kgpsdiary.BuildConfig
import org.elkuku.kgpsdiary.R
import org.elkuku.kgpsdiary.data.Photo

/** A server path of a photo as a full URL; the image loader adds the token. */
fun serverUrl(path: String): String = BuildConfig.SERVER_BASE_URL.trimEnd('/') + path

/** One image in the row: a server photo or a picked one that is not uploaded yet. */
data class PhotoItem(val key: String, val model: Any, val onDelete: (() -> Unit)? = null, val onOpen: (() -> Unit)? = null)

/** Square thumbnails in a row, with a delete button where [PhotoItem.onDelete] is set. */
@Composable
fun PhotoRow(items: List<PhotoItem>, modifier: Modifier = Modifier, size: Dp = 96.dp) {
    LazyRow(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(vertical = 4.dp)) {
        items(items, key = { it.key }) { item ->
            Box(Modifier.size(size)) {
                AsyncImage(
                    model = item.model,
                    contentDescription = stringResource(R.string.photo),
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .then(if (item.onOpen != null) Modifier.clickable(onClick = item.onOpen) else Modifier),
                )
                item.onDelete?.let { onDelete ->
                    // A small round button in the corner; the photo stays visible
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(4.dp)
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(Color(0x99000000))
                            .clickable(onClickLabel = stringResource(R.string.photo_delete), onClick = onDelete),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            painterResource(R.drawable.ic_delete),
                            stringResource(R.string.photo_delete),
                            tint = Color.White,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            }
        }
    }
}

/** A photo over the whole screen; a tap closes it. */
@Composable
fun PhotoViewer(photo: Photo, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize().background(Color.Black).clickable(onClick = onDismiss), contentAlignment = Alignment.Center) {
            AsyncImage(
                model = serverUrl(photo.url),
                contentDescription = stringResource(R.string.photo),
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}
