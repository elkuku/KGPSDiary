package org.elkuku.kgpsdiary.track

import android.content.Context
import androidx.core.content.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import org.elkuku.kgpsdiary.data.Track

/** Where the hidden track IDs are kept. */
interface HiddenTrackStore {
    fun load(): Set<Int>

    fun save(ids: Set<Int>)
}

class SharedPreferencesHiddenTrackStore @Inject constructor(@ApplicationContext context: Context) : HiddenTrackStore {
    private val preferences = context.getSharedPreferences("tracks", Context.MODE_PRIVATE)

    override fun load(): Set<Int> = preferences.getStringSet(HIDDEN, emptySet()).orEmpty().mapNotNull { it.toIntOrNull() }.toSet()

    override fun save(ids: Set<Int>) = preferences.edit { putStringSet(HIDDEN, ids.map { it.toString() }.toSet()) }

    private companion object {
        const val HIDDEN = "hidden"
    }
}

/**
 * Which tracks the map leaves out. A setting of this phone only: the web map shows them all.
 */
@Singleton
class TrackVisibility @Inject constructor(private val store: HiddenTrackStore) {
    private val _hidden = MutableStateFlow(store.load())
    val hidden: StateFlow<Set<Int>> = _hidden.asStateFlow()

    fun setVisible(id: Int, visible: Boolean) {
        _hidden.update { if (visible) it - id else it + id }
        store.save(_hidden.value)
    }

    fun toggle(id: Int) = setVisible(id, id in _hidden.value)


    companion object {
        /** The tracks the map shows. */
        fun visible(tracks: List<Track>, hidden: Set<Int>): List<Track> = tracks.filter { it.id !in hidden }
    }
}
