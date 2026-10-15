package com.jizhang.app.ui.book

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jizhang.app.data.local.entity.LocalBook
import com.jizhang.app.data.prefs.TokenStore
import com.jizhang.app.data.remote.dto.BookInviteDto
import com.jizhang.app.data.remote.dto.BookMemberDto
import com.jizhang.app.data.repository.BookRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 账本管理：新建 / 切换 / 成员 / 邀请码。
 */
@HiltViewModel
class BookViewModel @Inject constructor(
    private val bookRepository: BookRepository,
    private val tokenStore: TokenStore
) : ViewModel() {

    data class UiState(
        val busy: Boolean = false,
        val loggedIn: Boolean = false,
        val myUserId: Long = 0L,
        /** 正在管理成员的账本 */
        val memberBook: LocalBook? = null,
        val members: List<BookMemberDto> = emptyList(),
        val invites: List<BookInviteDto> = emptyList(),
        val message: String? = null
    ) {
        /** 我是不是该账本的所有者（只有所有者能管成员与邀请码） */
        val isOwner: Boolean get() = memberBook?.ownerId == myUserId
    }

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            _state.update { it.copy(loggedIn = !tokenStore.accessToken().isNullOrBlank()) }
        }
        viewModelScope.launch {
            _state.update { it.copy(myUserId = tokenStore.userIdFlow.first()) }
        }
    }

    fun setLoggedIn(loggedIn: Boolean) {
        _state.update { it.copy(loggedIn = loggedIn) }
    }

    fun refreshMyUserId() {
        viewModelScope.launch {
            _state.update { it.copy(myUserId = tokenStore.userIdFlow.first()) }
        }
    }

    /* ---------------- 账本 ---------------- */

    fun createBook(name: String, type: Int, onDone: () -> Unit) {
        if (name.isBlank()) {
            _state.update { it.copy(message = "请输入账本名称") }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(busy = true) }
            bookRepository.createBook(name = name, type = type)
                .onSuccess { book ->
                    onDone()
                    _state.update {
                        it.copy(busy = false, message = "已创建「${book.name}」并切换过去")
                    }
                    bookRepository.switchBook(book.id)
                }
                .onFailure { e ->
                    _state.update { it.copy(busy = false, message = "创建失败：${e.message}") }
                }
        }
    }

    fun renameBook(bookId: Long, name: String, onDone: () -> Unit) {
        if (name.isBlank()) {
            _state.update { it.copy(message = "请输入账本名称") }
            return
        }
        viewModelScope.launch {
            bookRepository.renameBook(bookId, name)
                .onSuccess {
                    onDone()
                    _state.update { it.copy(message = "已重命名") }
                }
                .onFailure { e -> _state.update { it.copy(message = "重命名失败：${e.message}") } }
        }
    }

    fun deleteBook(bookId: Long) {
        viewModelScope.launch {
            bookRepository.deleteBook(bookId)
                .onSuccess { _state.update { it.copy(message = "账本已删除") } }
                .onFailure { e -> _state.update { it.copy(message = "删除失败：${e.message}") } }
        }
    }

    /** 凭邀请码加入家庭账本 */
    fun joinByCode(code: String, onDone: () -> Unit) {
        if (code.isBlank()) {
            _state.update { it.copy(message = "请输入邀请码") }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(busy = true) }
            bookRepository.joinByCode(code)
                .onSuccess { book ->
                    onDone()
                    _state.update {
                        it.copy(busy = false, message = "已加入「${book.name}」")
                    }
                }
                .onFailure { e ->
                    _state.update { it.copy(busy = false, message = "加入失败：${e.message}") }
                }
        }
    }

    /* ---------------- 成员 ---------------- */

    fun openMembers(book: LocalBook) {
        _state.update { it.copy(memberBook = book, members = emptyList(), invites = emptyList()) }
        reloadMembers()
    }

    fun closeMembers() {
        _state.update { it.copy(memberBook = null, members = emptyList(), invites = emptyList()) }
    }

    fun reloadMembers() {
        val book = _state.value.memberBook ?: return
        viewModelScope.launch {
            _state.update { it.copy(busy = true) }
            val myId = tokenStore.userIdFlow.first()
            val members = bookRepository.members(book.id).getOrDefault(emptyList())
            val invites = if (book.ownerId == myId) {
                bookRepository.invites(book.id).getOrDefault(emptyList())
            } else {
                emptyList()
            }
            _state.update {
                it.copy(busy = false, myUserId = myId, members = members, invites = invites)
            }
        }
    }

    fun updateMember(userId: Long, role: Int? = null, alias: String? = null) {
        val book = _state.value.memberBook ?: return
        viewModelScope.launch {
            bookRepository.updateMember(book.id, userId, role, alias)
                .onSuccess { reloadMembers() }
                .onFailure { e -> _state.update { it.copy(message = "修改失败：${e.message}") } }
        }
    }

    fun removeMember(userId: Long) {
        val book = _state.value.memberBook ?: return
        viewModelScope.launch {
            bookRepository.removeMember(book.id, userId)
                .onSuccess {
                    _state.update { it.copy(message = "已移除该成员") }
                    reloadMembers()
                }
                .onFailure { e -> _state.update { it.copy(message = "移除失败：${e.message}") } }
        }
    }

    /* ---------------- 邀请码 ---------------- */

    fun createInvite(role: Int = 1, expireDays: Int = 7, maxUses: Int = 1) {
        val book = _state.value.memberBook ?: return
        viewModelScope.launch {
            bookRepository.createInvite(book.id, role, expireDays, maxUses)
                .onSuccess { invite ->
                    _state.update {
                        it.copy(message = "邀请码：${invite.code}", invites = listOf(invite) + it.invites)
                    }
                }
                .onFailure { e -> _state.update { it.copy(message = "生成失败：${e.message}") } }
        }
    }

    fun revokeInvite(code: String) {
        val book = _state.value.memberBook ?: return
        viewModelScope.launch {
            bookRepository.revokeInvite(book.id, code)
                .onSuccess {
                    _state.update { st ->
                        st.copy(message = "邀请码已作废", invites = st.invites.filterNot { it.code == code })
                    }
                }
                .onFailure { e -> _state.update { it.copy(message = "作废失败：${e.message}") } }
        }
    }

    fun consumeMessage() {
        _state.update { it.copy(message = null) }
    }
}
