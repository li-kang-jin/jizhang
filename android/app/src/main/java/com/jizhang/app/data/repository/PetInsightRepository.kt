package com.jizhang.app.data.repository

import com.jizhang.app.ui.pet.PetAdvice
import com.jizhang.app.ui.pet.PetInsight
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOf
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 宠物「看完账」的结论（建议 + 心情）。
 *
 * 之所以抽成仓库：账单页、我的页、设置页都要显示同一只宠物，
 * 如果各自算一遍，同一时刻三个页面可能给出三种心情 —— 那比不智能更糟。
 * 这里只做编排，真正的规则在 [PetAdvice]（纯函数，可单测）。
 *
 * 观察范围是**整本账**（不受账单页的筛选条件影响），并且只读本地 Room，
 * 断网 / 未登录一样有结论。
 */
@Singleton
class PetInsightRepository @Inject constructor(
    private val transactionRepository: TransactionRepository,
    private val ledgerRepository: LedgerRepository,
    private val petRepository: PetRepository
) {

    /**
     * 跟随账本实时变化的结论。
     *
     * 记账、删除、导入、同步、切账本、连续天数变化都会触发重算；
     * [PetInsight] 是 data class，内容没变时 [distinctUntilChanged] 会挡掉重复渲染。
     *
     * @param bookId 账本 id；未就绪（<= 0）时直接给空结论，不查库
     */
    fun observe(bookId: Long): Flow<PetInsight> {
        if (bookId <= 0L) {
            return flowOf(PetInsight.EMPTY)
        }
        return combine(
            transactionRepository.observeTransactions(bookId),
            ledgerRepository.observeCategories(bookId),
            petRepository.state
        ) { transactions, categories, pet ->
            PetAdvice.analyze(
                PetAdvice.factsOf(
                    transactions = transactions,
                    categoryNames = categories.associate { it.id to it.name },
                    today = LocalDate.now(ZoneId.systemDefault()),
                    streakDays = pet.streakDays
                )
            )
        }.distinctUntilChanged()
    }
}
