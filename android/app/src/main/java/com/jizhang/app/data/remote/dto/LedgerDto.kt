package com.jizhang.app.data.remote.dto

import kotlinx.serialization.Serializable

@Serializable
data class BookDto(
    val id: Long,
    val name: String,
    val icon: String? = null,
    val currency: String = "CNY",
    val type: Int = 0,
    val ownerId: Long? = null,
    val remark: String? = null,
    val myRole: Int? = null
)

@Serializable
data class AccountDto(
    val id: Long,
    val bookId: Long,
    val name: String,
    val type: Int,
    val balance: Long = 0,
    val initialBalance: Long = 0,
    val currency: String = "CNY",
    val icon: String? = null,
    val sort: Int = 0,
    val includeInTotal: Int = 1
)

@Serializable
data class CategoryDto(
    val id: Long,
    val bookId: Long = 0,
    val parentId: Long = 0,
    val name: String,
    val type: Int,
    val icon: String? = null,
    val color: String? = null,
    val sort: Int = 0,
    val system: Boolean = false,
    val children: List<CategoryDto> = emptyList()
)
