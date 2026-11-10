package org.elkuku.kgpsdiary

import org.junit.Assert.assertTrue
import org.junit.Test

class BuildConfigTest {
    @Test
    fun debugBuildHasServerBaseUrl() {
        assertTrue(BuildConfig.SERVER_BASE_URL.startsWith("http"))
    }
}
