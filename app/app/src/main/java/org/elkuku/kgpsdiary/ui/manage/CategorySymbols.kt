package org.elkuku.kgpsdiary.ui.manage

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.graphics.createBitmap
import org.elkuku.kgpsdiary.R
import org.elkuku.kgpsdiary.ui.map.parseColor

/**
 * The symbols a category can show in its markers, by Material Symbols name: the same set as the
 * web (web/src/Category/CategorySymbols.php, whose tests compare both).
 */
object CategorySymbols {
    val all: List<Pair<String, Int>> = listOf(
        "restaurant" to R.drawable.sym_restaurant,
        "local_cafe" to R.drawable.sym_local_cafe,
        "local_bar" to R.drawable.sym_local_bar,
        "hotel" to R.drawable.sym_hotel,
        "home" to R.drawable.sym_home,
        "shopping_cart" to R.drawable.sym_shopping_cart,
        "local_hospital" to R.drawable.sym_local_hospital,
        "directions_car" to R.drawable.sym_directions_car,
        "local_parking" to R.drawable.sym_local_parking,
        "directions_walk" to R.drawable.sym_directions_walk,
        "terrain" to R.drawable.sym_terrain,
        "park" to R.drawable.sym_park,
        "beach_access" to R.drawable.sym_beach_access,
        "water_drop" to R.drawable.sym_water_drop,
        "museum" to R.drawable.sym_museum,
        "account_balance" to R.drawable.sym_account_balance,
        "photo_camera" to R.drawable.sym_photo_camera,
        "visibility" to R.drawable.sym_visibility,
        "star" to R.drawable.sym_star,
        "favorite" to R.drawable.sym_favorite,
        "flag" to R.drawable.sym_flag,
        "place" to R.drawable.sym_place,
    )

    /** The symbol's drawable; null for none or a name the app doesn't know. */
    @DrawableRes
    fun drawable(name: String?): Int? = all.firstOrNull { it.first == name }?.second
}

/** A category's color as a dot, with its symbol in white. */
@Composable
fun CategoryBadge(color: String, icon: String?, size: Int, modifier: Modifier = Modifier) {
    Box(
        modifier.size(size.dp).clip(CircleShape).background(Color(parseColor(color))),
        contentAlignment = Alignment.Center,
    ) {
        CategorySymbols.drawable(icon)?.let {
            Icon(painterResource(it), contentDescription = null, tint = Color.White, modifier = Modifier.size((size * SYMBOL_SHARE).dp))
        }
    }
}

/**
 * A waypoint's marker for the map: a circle in the category's color with a white border, and the
 * symbol in it; bigger with a symbol.
 */
fun markerBitmap(context: Context, color: String?, icon: String?): Bitmap {
    val symbol = CategorySymbols.drawable(icon)?.let { ContextCompat.getDrawable(context, it) }
    val density = context.resources.displayMetrics.density
    val diameter = ((if (symbol != null) 26 else 20) * density).toInt()
    val border = 2 * density
    val bitmap = createBitmap(diameter, diameter)
    val canvas = Canvas(bitmap)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    val radius = diameter / 2f

    paint.color = android.graphics.Color.WHITE
    canvas.drawCircle(radius, radius, radius, paint)
    paint.color = parseColor(color)
    canvas.drawCircle(radius, radius, radius - border, paint)

    symbol?.let {
        val inset = ((diameter - diameter * SYMBOL_SHARE) / 2).toInt()
        it.mutate().setTint(android.graphics.Color.WHITE)
        it.setBounds(inset, inset, diameter - inset, diameter - inset)
        it.draw(canvas)
    }
    return bitmap
}

/** How much of the circle the symbol fills. */
private const val SYMBOL_SHARE = 0.62f
