package com.codespacemanager.app.data.local.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "accounts")
data class AccountEntity(
    @PrimaryKey val id: String,
    val login: String,
    val avatarUrl: String,
    val accessTokenEnc: String?,
    val scopes: String,
    val createdAt: Long,
)

@Entity(tableName = "codespaces")
data class CodespaceEntity(
    @PrimaryKey val name: String,
    val accountId: String,
    val repoFullName: String,
    val state: String,
    val machine: String?,
    val location: String?,
    val webUrl: String?,
    val portPublic: Boolean = false,
    val lastOpenedAt: Long? = null,
)
