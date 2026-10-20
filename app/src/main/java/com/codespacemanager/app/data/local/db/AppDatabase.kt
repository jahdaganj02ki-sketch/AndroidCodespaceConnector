package com.codespacemanager.app.data.local.db

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface AccountDao {

    @Query("SELECT * FROM accounts ORDER BY createdAt ASC")
    fun observeAll(): Flow<List<AccountEntity>>

    @Query("SELECT * FROM accounts WHERE id = :id")
    suspend fun findById(id: String): AccountEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(account: AccountEntity)

    @Query("DELETE FROM accounts WHERE id = :id")
    suspend fun delete(id: String)

    @Query("DELETE FROM accounts")
    suspend fun deleteAll()
}

@Dao
interface CodespaceDao {

    @Query("SELECT * FROM codespaces WHERE accountId = :accountId ORDER BY name ASC")
    fun observeForAccount(accountId: String): Flow<List<CodespaceEntity>>

    @Query("SELECT * FROM codespaces WHERE name = :name")
    suspend fun findByName(name: String): CodespaceEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(codespace: CodespaceEntity)

    @Update
    suspend fun update(codespace: CodespaceEntity)

    @Query("UPDATE codespaces SET portPublic = :public WHERE name = :name")
    suspend fun setPortPublic(name: String, public: Boolean)

    @Query("DELETE FROM codespaces WHERE name = :name")
    suspend fun delete(name: String)
}

@Database(
    entities = [AccountEntity::class, CodespaceEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun accountDao(): AccountDao
    abstract fun codespaceDao(): CodespaceDao
}
