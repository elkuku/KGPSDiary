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
