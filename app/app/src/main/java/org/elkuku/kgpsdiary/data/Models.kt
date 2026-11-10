package org.elkuku.kgpsdiary.data

import java.time.LocalDate
import kotlinx.serialization.Serializable

/** A category as the API embeds it in a waypoint, and lists it. [color] is `#rrggbb`. */
@Serializable
data class Category(val id: Int, val name: String, val color: String = DEFAULT_COLOR, val icon: String? = null) {
    val iri: String get() = "/api/categories/$id"

    companion object {
        /** Markers of waypoints without a category, as on the web map. */
        const val DEFAULT_COLOR = "#d84315"
    }
}

@Serializable
data class Tag(val id: Int, val name: String) {
    val iri: String get() = "/api/tags/$id"
}

/** A photo of a waypoint; [url] and [thumbnailUrl] are relative to the server and need the token. */
@Serializable
data class Photo(
    val id: Int,
    val width: Int = 0,
    val height: Int = 0,
    val url: String,
    val thumbnailUrl: String,
    val createdAt: String = "",
)

/** A waypoint of `GET /api/waypoints`; [visitedAt] is `yyyy-MM-dd`. */
@Serializable
data class Waypoint(
    val id: Int,
    val title: String,
    val description: String = "",
    val latitude: Double,
    val longitude: Double,
    val visitedAt: String? = null,
    val category: Category? = null,
    val tags: List<Tag> = emptyList(),
    val photos: List<Photo> = emptyList(),
    val createdAt: String = "",
    val updatedAt: String = "",
) {
    val visitedOn: LocalDate? get() = visitedAt?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
}

/** The body of `POST` and `PATCH /api/waypoints`: nulls are sent, they clear a field. */
@Serializable
data class WaypointInput(
    val title: String,
    val description: String,
    val latitude: Double,
    val longitude: Double,
    val visitedAt: String?,
    /** The category's IRI. */
    val category: String?,
    /** The tags' IRIs. */
    val tags: List<String>,
)

/**
 * A recorded track of `GET /api/tracks`. [points] are [latitude, longitude, time] with the time
 * in Unix seconds; [distance] is in meters.
 */
@Serializable
data class Track(
    val id: Int,
    val name: String,
    val points: List<List<Double>> = emptyList(),
    val startedAt: String = "",
    val endedAt: String = "",
    val distance: Double = 0.0,
    val pointCount: Int = 0,
)

/** The body of `PATCH /api/tracks/{id}`: only the name changes. */
@Serializable
data class TrackRename(val name: String)

/** The body of `POST /api/tracks`: each point as [latitude, longitude, time], the time a whole number. */
@Serializable
data class TrackInput(val name: String, val points: List<kotlinx.serialization.json.JsonArray>)

/** One page of a Hydra collection. */
@Serializable
internal data class CollectionPage<T>(val member: List<T> = emptyList(), val view: CollectionView? = null)

@Serializable
internal data class CollectionView(val next: String? = null)

/** The body of `POST` and `PATCH /api/categories`. */
@Serializable
data class CategoryInput(val name: String, val color: String, val icon: String?)

/** The body of `POST` and `PATCH /api/tags`. */
@Serializable
data class TagInput(val name: String)

/**
 * The [waypoints] of [categoryId] (null: any) with [tagId] (null: any), for the map's filters.
 */
fun filterWaypoints(waypoints: List<Waypoint>, categoryId: Int?, tagId: Int?): List<Waypoint> = waypoints.filter { waypoint ->
    (categoryId == null || waypoint.category?.id == categoryId) && (tagId == null || waypoint.tags.any { it.id == tagId })
}

/**
 * The [waypoints] that contain every word of [query] in their title, description, category or
 * tags; ignoring case and accents ("basilica" finds "Basílica").
 */
fun searchWaypoints(waypoints: List<Waypoint>, query: String): List<Waypoint> {
    val words = searchable(query).split(' ').filter { it.isNotBlank() }
    if (words.isEmpty()) return waypoints

    return waypoints.filter { waypoint ->
        val text = searchable(
            listOfNotNull(waypoint.title, waypoint.description, waypoint.category?.name, *waypoint.tags.map { it.name }.toTypedArray())
                .joinToString(" "),
        )
        words.all { it in text }
    }
}

private val diacritics = Regex("\\p{Mn}+")

private fun searchable(text: String): String =
    java.text.Normalizer.normalize(text.lowercase(), java.text.Normalizer.Form.NFD).replace(diacritics, "")
