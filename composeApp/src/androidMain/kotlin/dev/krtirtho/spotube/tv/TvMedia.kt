/*
 * Copyright (C) 2026 Kingkor Roy Tirtho and Spotube Contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package dev.krtirtho.spotube.tv

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import coil3.compose.AsyncImage
import dev.krtirtho.plugin_interfaces.plugin_apis.metadata.album.MetadataAlbum
import dev.krtirtho.plugin_interfaces.plugin_apis.metadata.album.MetadataAlbumType
import dev.krtirtho.plugin_interfaces.plugin_apis.metadata.artist.MetadataArtist
import dev.krtirtho.plugin_interfaces.plugin_apis.metadata.browse.MetadataBrowseItem
import dev.krtirtho.plugin_interfaces.plugin_apis.metadata.common.Thumbnail
import dev.krtirtho.plugin_interfaces.plugin_apis.metadata.playlist.MetadataPlaylist
import dev.krtirtho.plugin_interfaces.plugin_apis.metadata.track.MetadataTrack
import dev.krtirtho.plugin_interfaces.plugin_apis.metadata.user.MetadataUser
import dev.krtirtho.spotube.resources.iconsax.Iconsax
import dev.krtirtho.spotube.resources.iconsax.IconsaxAdd
import dev.krtirtho.spotube.resources.iconsax.IconsaxArrowDown4
import dev.krtirtho.spotube.resources.iconsax.IconsaxCd
import dev.krtirtho.spotube.resources.iconsax.IconsaxHeart
import dev.krtirtho.spotube.resources.iconsax.IconsaxHeart2
import dev.krtirtho.spotube.resources.iconsax.IconsaxHome
import dev.krtirtho.spotube.resources.iconsax.IconsaxMusic
import dev.krtirtho.spotube.resources.iconsax.IconsaxNext
import dev.krtirtho.spotube.resources.iconsax.IconsaxPause
import dev.krtirtho.spotube.resources.iconsax.IconsaxPlay
import dev.krtirtho.spotube.resources.iconsax.IconsaxPrevious
import dev.krtirtho.spotube.resources.iconsax.IconsaxRepeatMusic
import dev.krtirtho.spotube.resources.iconsax.IconsaxRepeateMusic
import dev.krtirtho.spotube.resources.iconsax.IconsaxRepeateOne
import dev.krtirtho.spotube.resources.iconsax.IconsaxSearch
import dev.krtirtho.spotube.resources.iconsax.IconsaxSetting2
import dev.krtirtho.spotube.resources.iconsax.IconsaxShuffle
import dev.krtirtho.spotube.resources.iconsax.FluentMaximize
import dev.krtirtho.spotube.resources.iconsax.User

object TvIcons {
    val Home: ImageVector get() = Iconsax.IconsaxHome
    val Search: ImageVector get() = Iconsax.IconsaxSearch
    val Settings: ImageVector get() = Iconsax.IconsaxSetting2
    val Play: ImageVector get() = Iconsax.IconsaxPlay
    val Pause: ImageVector get() = Iconsax.IconsaxPause
    val Next: ImageVector get() = Iconsax.IconsaxNext
    val Previous: ImageVector get() = Iconsax.IconsaxPrevious
    val Shuffle: ImageVector get() = Iconsax.IconsaxShuffle
    val RepeatOff: ImageVector get() = Iconsax.IconsaxRepeateMusic
    val RepeatOne: ImageVector get() = Iconsax.IconsaxRepeateOne
    val RepeatAll: ImageVector get() = Iconsax.IconsaxRepeatMusic
    val Heart: ImageVector get() = Iconsax.IconsaxHeart
    val HeartFilled: ImageVector get() = Iconsax.IconsaxHeart2
    val Add: ImageVector get() = Iconsax.IconsaxAdd
    val Music: ImageVector get() = Iconsax.IconsaxMusic
    val Album: ImageVector get() = Iconsax.IconsaxCd
    val Artist: ImageVector get() = Iconsax.User
    val FullScreen: ImageVector get() = Iconsax.FluentMaximize
    val Down: ImageVector get() = Iconsax.IconsaxArrowDown4
}

/** Picks a thumbnail that is sharp at [targetPx] without downloading the largest one. */
fun List<Thumbnail>?.bestUrl(targetPx: Int = 300): String? {
    if (this.isNullOrEmpty()) return null
    val sized = filter { it.width > 0 }
    if (sized.isEmpty()) return first().url
    return sized.filter { it.width >= targetPx }.minByOrNull { it.width }?.url
        ?: sized.maxByOrNull { it.width }?.url
}

@Composable
fun TvArtwork(
    url: String?,
    modifier: Modifier = Modifier,
    size: Dp,
    shape: Shape = TvCardShape,
    placeholder: ImageVector = TvIcons.Music,
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(shape)
            .background(TvColors.Highlight, shape),
        contentAlignment = Alignment.Center,
    ) {
        if (url.isNullOrBlank()) {
            Icon(placeholder, contentDescription = null, tint = TvColors.TextMuted, modifier = Modifier.size(size * 0.4f))
        } else {
            AsyncImage(
                model = url,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(size),
            )
        }
    }
}

fun artworkShape(circle: Boolean): Shape = if (circle) CircleShape else TvCardShape

/** One card/row of media, whatever its source (home feed, library, search...). */
data class TvItem(
    val key: String,
    val title: String,
    val subtitle: String,
    val imageUrl: String?,
    val circle: Boolean = false,
    val route: TvRoute? = null,
    val track: MetadataTrack? = null,
)

private fun List<MetadataArtist.Basic>.names() = joinToString(", ") { it.name }

private fun MetadataAlbumType.label() = when (this) {
    MetadataAlbumType.Single -> "Single"
    MetadataAlbumType.Album -> "Album"
    MetadataAlbumType.Collection -> "Compilation"
}

fun MetadataPlaylist.toTvItem(): TvItem {
    val owner = owner?.let { it.displayName ?: it.username }
    val subtitle = description?.takeIf { it.isNotBlank() }
        ?: listOfNotNull("Playlist", owner).joinToString(" • ")
    return TvItem(
        key = "playlist:$id",
        title = title,
        subtitle = subtitle,
        imageUrl = thumbnails.bestUrl(),
        route = TvRoute.Playlist(id),
    )
}

/** Library rows always show "Playlist • owner", like the web player's sidebar. */
fun MetadataPlaylist.toLibraryItem(): TvItem = toTvItem().copy(
    subtitle = listOfNotNull("Playlist", owner?.let { it.displayName ?: it.username }).joinToString(" • "),
)

fun MetadataAlbum.toTvItem(): TvItem = TvItem(
    key = "album:$id",
    title = title,
    subtitle = listOf(albumType.label(), artists.names()).filter { it.isNotBlank() }.joinToString(" • "),
    imageUrl = thumbnails.bestUrl(),
    route = TvRoute.Album(id),
)

fun MetadataArtist.toTvItem(): TvItem = TvItem(
    key = "artist:$id",
    title = name,
    subtitle = "Artist",
    imageUrl = thumbnails.bestUrl(),
    circle = true,
    route = TvRoute.Artist(id),
)

fun MetadataUser.toTvItem(): TvItem = TvItem(
    key = "user:$id",
    title = displayName ?: username,
    subtitle = "Profile",
    imageUrl = thumbnails.bestUrl(),
    circle = true,
)

fun MetadataTrack.toTvItem(): TvItem = TvItem(
    key = "track:$id",
    title = title,
    subtitle = artists.names(),
    imageUrl = (album?.thumbnails ?: thumbnails).bestUrl(),
    track = this,
)

fun MetadataBrowseItem.toTvItem(): TvItem = when (this) {
    is MetadataBrowseItem.Album -> data.toTvItem()
    is MetadataBrowseItem.Artist -> data.toTvItem()
    is MetadataBrowseItem.Playlist -> data.toTvItem()
    is MetadataBrowseItem.Track -> data.toTvItem()
    is MetadataBrowseItem.User -> data.toTvItem()
}

fun formatDuration(ms: Long): String {
    val totalSeconds = (ms / 1000).coerceAtLeast(0)
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        "%d:%02d:%02d".format(hours, minutes, seconds)
    } else {
        "%d:%02d".format(minutes, seconds)
    }
}
