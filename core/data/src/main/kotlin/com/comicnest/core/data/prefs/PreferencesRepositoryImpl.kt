package com.comicnest.core.data.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.comicnest.core.domain.repository.PreferencesRepository
import com.comicnest.core.model.ReaderMode
import com.comicnest.core.model.SortMode
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "comic_nest_preferences")

/** 偏好仓储实现（DataStore，DESIGN §3.5）。以枚举 ordinal 存储，缺省/越界回退默认值。 */
@Singleton
class PreferencesRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
) : PreferencesRepository {

    private object Keys {
        val SORT_MODE = intPreferencesKey("sort_mode")
        val READER_MODE = intPreferencesKey("reader_mode")
    }

    private val store: DataStore<Preferences> get() = context.dataStore

    override fun observeSortMode(): Flow<SortMode> =
        store.data.recoverOnError().map { prefs -> sortFrom(prefs[Keys.SORT_MODE]) }

    override suspend fun setSortMode(mode: SortMode) {
        store.edit { it[Keys.SORT_MODE] = mode.ordinal }
    }

    override fun observeReaderMode(): Flow<ReaderMode> =
        store.data.recoverOnError().map { prefs -> readerFrom(prefs[Keys.READER_MODE]) }

    override suspend fun setReaderMode(mode: ReaderMode) {
        store.edit { it[Keys.READER_MODE] = mode.ordinal }
    }

    private fun sortFrom(ord: Int?): SortMode = SortMode.entries.getOrNull(ord ?: -1) ?: SortMode.NAME

    private fun readerFrom(ord: Int?): ReaderMode = ReaderMode.entries.getOrNull(ord ?: -1) ?: ReaderMode.WEBTOON
}

/** 读盘异常（如损坏）降级为空偏好，避免 Flow 终止（§8.2）。 */
private fun Flow<Preferences>.recoverOnError(): Flow<Preferences> =
    catch { throwable ->
        if (throwable is IOException) emit(emptyPreferences()) else throw throwable
    }
