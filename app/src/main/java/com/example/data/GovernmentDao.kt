package com.example.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface GovernmentDao {

    // --- Account Queries ---
    @Query("SELECT * FROM sec_accounts WHERE id = 1 LIMIT 1")
    fun getAccountFlow(): Flow<AccountEntity?>

    @Query("SELECT * FROM sec_accounts WHERE id = 1 LIMIT 1")
    suspend fun getAccountSync(): AccountEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAccount(account: AccountEntity)

    // --- Government Task Queries ---
    @Query("SELECT * FROM gov_tasks ORDER BY creationTime DESC")
    fun getAllTasksFlow(): Flow<List<GovTaskEntity>>

    @Query("SELECT * FROM gov_tasks WHERE id = :id LIMIT 1")
    suspend fun getTaskById(id: Int): GovTaskEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTask(task: GovTaskEntity)

    @Query("DELETE FROM gov_tasks WHERE id = :id")
    suspend fun deleteTaskById(id: Int)

    @Query("DELETE FROM gov_tasks")
    suspend fun clearHistory()
}
