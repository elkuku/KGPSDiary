package org.elkuku.kgpsdiary.ui.manage

import java.io.File
import org.elkuku.kgpsdiary.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The web's tests compare the symbols' paths with the web's own (web/src/Category). */
class CategorySymbolsTest {
    private val names = CategorySymbols.all.map { it.first }

    @Test
    fun namesAreUniqueMaterialSymbolsNames() {
        assertEquals(names.distinct(), names)
        assertTrue(names.all { it.matches(Regex("[a-z0-9_]+")) })
    }

    @Test
    fun eachSymbolHasItsDrawable() {
        val drawables = File("src/main/res/drawable").list().orEmpty()
            .filter { it.startsWith("sym_") }
            .map { it.removePrefix("sym_").removeSuffix(".xml") }
        assertEquals(drawables.sorted(), names.sorted())
        assertEquals(R.drawable.sym_museum, CategorySymbols.drawable("museum"))
    }

    @Test
    fun noneOrUnknownHasNoSymbol() {
        assertNull(CategorySymbols.drawable(null))
        assertNull(CategorySymbols.drawable("hiking"))
    }
}
