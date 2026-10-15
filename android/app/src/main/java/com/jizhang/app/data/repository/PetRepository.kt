package com.jizhang.app.data.repository

import com.jizhang.app.data.prefs.PetStore
import com.jizhang.app.ui.pet.PetState
import com.jizhang.app.ui.pet.PetType
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 宠物业务逻辑：记账喂养、连续天数、改名与换形象。
 */
@Singleton
class PetRepository @Inject constructor(
    private val petStore: PetStore
) {

    val state: Flow<PetState> = petStore.state

    /** 记账成功后调用：获得金币并维护连续记账天数 */
    suspend fun onTransactionRecorded(): PetState {
        val current = petStore.current()
        val today = LocalDate.now().toString()
        val yesterday = LocalDate.now().minusDays(1).toString()

        val streak = when (current.lastRecordDate) {
            today -> current.streakDays                 // 今天已经记过，不重复累加
            yesterday -> current.streakDays + 1         // 连续记账
            else -> 1                                   // 中断后重新开始
        }

        val next = current.copy(
            coins = current.coins + PetState.COINS_PER_RECORD,
            streakDays = streak,
            lastRecordDate = today
        )
        petStore.save(next)
        return next
    }

    suspend fun rename(name: String) {
        val trimmed = name.trim().take(8)
        if (trimmed.isEmpty()) {
            return
        }
        petStore.save(petStore.current().copy(name = trimmed))
    }

    suspend fun changeType(type: PetType) {
        petStore.save(petStore.current().copy(type = type))
    }
}
