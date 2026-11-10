package org.elkuku.kgpsdiary.di

import android.content.Context
import androidx.room.Room
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import org.elkuku.kgpsdiary.auth.AccountStore
import org.elkuku.kgpsdiary.auth.SharedPreferencesAccountStore
import org.elkuku.kgpsdiary.data.cache.DiaryCache
import org.elkuku.kgpsdiary.data.cache.DiaryDatabase
import org.elkuku.kgpsdiary.data.cache.RoomDiaryCache
import org.elkuku.kgpsdiary.track.HiddenTrackStore
import org.elkuku.kgpsdiary.track.SharedPreferencesHiddenTrackStore

@Module
@InstallIn(SingletonComponent::class)
abstract class DatabaseModule {
    @Binds
    abstract fun diaryCache(cache: RoomDiaryCache): DiaryCache

    @Binds
    abstract fun accountStore(store: SharedPreferencesAccountStore): AccountStore

    @Binds
    abstract fun hiddenTrackStore(store: SharedPreferencesHiddenTrackStore): HiddenTrackStore

    companion object {
        @Provides
        @Singleton
        fun database(@ApplicationContext context: Context): DiaryDatabase =
            Room.databaseBuilder(context, DiaryDatabase::class.java, DiaryDatabase.NAME)
                // It is only a cache: a new version starts empty and is loaded again
                .fallbackToDestructiveMigration(dropAllTables = true)
                .build()
    }
}
