package com.jizhang.app.data.repository

import com.jizhang.app.data.local.JizhangDatabase
import com.jizhang.app.data.local.dao.BookDao
import com.jizhang.app.data.local.dao.BookMemberDao
import com.jizhang.app.data.local.entity.LocalBook
import com.jizhang.app.data.local.entity.LocalBookMember
import com.jizhang.app.data.prefs.TokenStore
import com.jizhang.app.data.remote.ApiService
import com.jizhang.app.data.remote.dto.BookInviteDto
import com.jizhang.app.data.remote.dto.BookMemberDto
import com.jizhang.app.data.remote.dto.BookSaveDto
import com.jizhang.app.data.remote.dto.InviteCreateDto
import com.jizhang.app.data.remote.dto.JoinBookDto
import com.jizhang.app.data.remote.dto.MemberUpdateDto
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 账本仓库。
 *
 * 「账本即团队」：个人账本成员只有自己，家庭账本用邀请码把家人拉进来。
 * 本仓库负责账本的本地缓存、切换、新建与成员 / 邀请码的云端操作。
 *
 * 注意：成员与邀请码不做离线缓存 —— 它们强依赖登录态，且是低频操作。
 */
@Singleton
class BookRepository @Inject constructor(
    private val api: ApiService,
    private val tokenStore: TokenStore,
    private val bookDao: BookDao,
    private val bookMemberDao: BookMemberDao,
    private val ledgerRepository: LedgerRepository
) {

    /** 账本切换器数据源 */
    fun observeBooks(): Flow<List<LocalBook>> = bookDao.observeAll()

    /** 家庭成员名单（本地缓存），用于流水里的「谁记的」与按成员统计 */
    fun observeMembers(bookId: Long): Flow<List<LocalBookMember>> =
        bookMemberDao.observeByBook(bookId)

    suspend fun cachedMembers(bookId: Long): List<LocalBookMember> =
        bookMemberDao.listByBook(bookId)

    /** 保证「本地账本」这一行存在（id = 0），否则未登录时切换器是空的 */
    suspend fun ensureLocalBookRow() {
        if (bookDao.exists(JizhangDatabase.LOCAL_BOOK_ID) > 0) {
            return
        }
        bookDao.upsert(
            LocalBook(
                id = JizhangDatabase.LOCAL_BOOK_ID,
                name = "本地账本",
                icon = "book",
                type = 0,
                ownerId = 0L,
                myRole = 2,
                memberCount = 1,
                personal = true,
                remote = false,
                sortOrder = 0
            )
        )
    }

    suspend fun currentBookId(): Long = tokenStore.currentBookId()

    /** 切换当前账本；所有页面会跟随这个全局状态 */
    suspend fun switchBook(bookId: Long) {
        tokenStore.saveCurrentBookId(bookId)
    }

    /**
     * 拉取服务端账本列表并落库。
     * <p>
     * 用「先清空服务端账本再重建」的方式保持与服务端一致；
     * 本地账本（id = 0）不受影响。
     */
    suspend fun refreshBooks(): Result<List<LocalBook>> = runCatching {
        val myId = tokenStore.userIdFlow.first()
        val remote = api.books().data ?: error("账本列表获取失败")
        val books = remote.mapIndexed { index, dto -> dto.toLocal(myId, index) }

        bookDao.clearRemote()
        bookDao.upsertAll(books)

        // 当前账本若已不可见（被移出家庭 / 被删除），自动回退到个人账本
        val current = tokenStore.currentBookId()
        if (current != JizhangDatabase.LOCAL_BOOK_ID && books.none { it.id == current }) {
            val fallback = books.firstOrNull { it.personal }?.id
                ?: books.firstOrNull()?.id
                ?: JizhangDatabase.LOCAL_BOOK_ID
            tokenStore.saveCurrentBookId(fallback)
        }

        // 家庭账本的成员名单要缓存下来，流水里才能显示「谁记的」
        books.filter { it.shared }.forEach { book ->
            runCatching { refreshMembers(book.id) }
        }
        books
    }

    /** 拉取成员名单并写入本地缓存 */
    suspend fun refreshMembers(bookId: Long): List<LocalBookMember> {
        val members = api.bookMembers(bookId).data.orEmpty()
        bookMemberDao.upsertAll(
            members.map {
                LocalBookMember(
                    bookId = bookId,
                    userId = it.userId,
                    username = it.username,
                    nickname = it.nickname,
                    alias = it.alias,
                    role = it.role,
                    owner = it.owner
                )
            }
        )
        return bookMemberDao.listByBook(bookId)
    }

    /**
     * 新建账本。
     *
     * @param type 0=个人（仅自己可见） 1=家庭 2=共享
     */
    suspend fun createBook(name: String, type: Int, icon: String? = null): Result<LocalBook> =
        runCatching {
            val myId = tokenStore.userIdFlow.first()
            val dto = api.createBook(BookSaveDto(name = name.trim(), type = type, icon = icon)).data
                ?: error("创建账本失败")
            val local = dto.toLocal(myId, bookDao.listAll().size)
            bookDao.upsert(local)
            local
        }

    suspend fun renameBook(bookId: Long, name: String, icon: String? = null): Result<Unit> =
        runCatching {
            val current = bookDao.findById(bookId)
            val dto = api.updateBook(
                bookId,
                BookSaveDto(
                    name = name.trim(),
                    type = current?.type ?: 0,
                    icon = icon ?: current?.icon
                )
            ).data ?: error("修改账本失败")
            val myId = tokenStore.userIdFlow.first()
            bookDao.upsert(dto.toLocal(myId, current?.sortOrder ?: 0))
        }

    /** 删除账本（仅所有者）；本地只摘掉账本行，交易数据保留便于误删恢复 */
    suspend fun deleteBook(bookId: Long): Result<Unit> = runCatching {
        api.deleteBook(bookId)
        bookDao.deleteByIds(listOf(bookId))
        if (tokenStore.currentBookId() == bookId) {
            val fallback = bookDao.listAll().firstOrNull()?.id ?: JizhangDatabase.LOCAL_BOOK_ID
            tokenStore.saveCurrentBookId(fallback)
        }
    }

    /** 退出登录：清掉服务端账本与成员缓存，并回到本地账本 */
    suspend fun onLogout() {
        bookDao.clearRemote()
        bookMemberDao.clear()
        tokenStore.saveCurrentBookId(JizhangDatabase.LOCAL_BOOK_ID)
    }

    /* ---------------- 成员 ---------------- */

    suspend fun members(bookId: Long): Result<List<BookMemberDto>> = runCatching {
        val members = api.bookMembers(bookId).data ?: error("成员列表获取失败")
        bookMemberDao.upsertAll(
            members.map {
                LocalBookMember(
                    bookId = bookId,
                    userId = it.userId,
                    username = it.username,
                    nickname = it.nickname,
                    alias = it.alias,
                    role = it.role,
                    owner = it.owner
                )
            }
        )
        members
    }

    suspend fun updateMember(
        bookId: Long,
        userId: Long,
        role: Int? = null,
        alias: String? = null
    ): Result<Unit> = runCatching {
        api.updateBookMember(bookId, userId, MemberUpdateDto(role = role, alias = alias))
    }

    suspend fun removeMember(bookId: Long, userId: Long): Result<Unit> = runCatching {
        api.removeBookMember(bookId, userId)
    }

    /* ---------------- 邀请码 ---------------- */

    suspend fun invites(bookId: Long): Result<List<BookInviteDto>> = runCatching {
        api.bookInvites(bookId).data ?: error("邀请码获取失败")
    }

    suspend fun createInvite(
        bookId: Long,
        role: Int = 1,
        expireDays: Int = 7,
        maxUses: Int = 1
    ): Result<BookInviteDto> = runCatching {
        api.createBookInvite(bookId, InviteCreateDto(role = role, expireDays = expireDays, maxUses = maxUses))
            .data ?: error("邀请码生成失败")
    }

    suspend fun revokeInvite(bookId: Long, code: String): Result<Unit> = runCatching {
        api.revokeBookInvite(bookId, code)
    }

    /** 凭邀请码加入家庭账本，加入后自动切到该账本并拉一次数据 */
    suspend fun joinByCode(code: String): Result<LocalBook> = runCatching {
        val normalized = code.trim().uppercase()
        require(normalized.isNotEmpty()) { "请输入邀请码" }
        val myId = tokenStore.userIdFlow.first()
        val dto = api.joinBook(JoinBookDto(normalized)).data ?: error("邀请码无效或已过期")
        val local = dto.toLocal(myId, bookDao.listAll().size)
        bookDao.upsert(local)
        tokenStore.saveCurrentBookId(local.id)
        // 拉一次该账本的账户 / 分类 / 交易，加入后立刻能用
        runCatching { ledgerRepository.pull(local.id) }
        local
    }
}
