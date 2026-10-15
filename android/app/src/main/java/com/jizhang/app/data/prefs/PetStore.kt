package com.jizhang.app.data.prefs

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.jizhang.app.ui.pet.PetState
import com.jizhang.app.ui.pet.PetType
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.petDataStore by preferencesDataStore(name = "pet_prefs")

private object PetKeys {
    val NAME = stringPreferencesKey("pet_name")
    val TYPE = stringPreferencesKey("pet_type")
    val COINS = intPreferencesKey("pet_coins")
    val STREAK = intPreferencesKey("pet_streak")
    val LAST_DATE = stringPreferencesKey("pet_last_date")
}

private fun Preferences.toPetState(): PetState = PetState(
    name = this[PetKeys.NAME] ?: PetState.DEFAULT_NAME,
    type = PetType.fromKey(this[PetKeys.TYPE]),
    coins = this[PetKeys.COINS] ?: 0,
    streakDays = this[PetKeys.STREAK] ?: 0,
    lastRecordDate = this[PetKeys.LAST_DATE].orEmpty()
)

/**
 * 宠物状态的本地存储。
 */
@Singleton
class PetStore @Inject constructor(
    @ApplicationContext private val context: Context
) {

    val state: Flow<PetState> = context.petDataStore.data.map { it.toPetState() }

    suspend fun current(): PetState = state.first()

    suspend fun save(state: PetState) {
        context.petDataStore.edit { prefs ->
            prefs[PetKeys.NAME] = state.name
            prefs[PetKeys.TYPE] = state.type.key
            prefs[PetKeys.COINS] = state.coins
            prefs[PetKeys.STREAK] = state.streakDays
            prefs[PetKeys.LAST_DATE] = state.lastRecordDate
        }
    }
}
