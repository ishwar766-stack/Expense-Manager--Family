package com.family.expense.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow
import java.security.MessageDigest

@Entity(tableName = "users")
data class UserEntity(
    @PrimaryKey val id: String,
    val name: String,
    val role: String,                 // HEAD or MEMBER
    val relationshipToHead: String,
    val deviceId: String,
    val approvalStatus: String        // PENDING or APPROVED
)

@Entity(tableName = "expenses", indices = [Index(value = ["hashSignature"], unique = true)])
data class ExpenseEntity(
    @PrimaryKey val id: String,
    val amount: Double,
    val timestamp: Long,
    val merchant: String,
    val category: String,
    val isInvestment: Boolean,
    val createdByUserId: String,
    val hashSignature: String,
    val syncStatus: String            // LOCAL_ONLY or SYNCED
)

@Dao
interface AppDao {
    @Query("SELECT COUNT(*) FROM expenses WHERE hashSignature = :hash")
    suspend fun countByHash(hash: String): Int
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertExpense(e: ExpenseEntity): Long
    @Query("SELECT * FROM expenses ORDER BY timestamp DESC")
    fun observeExpenses(): Flow<List<ExpenseEntity>>
    @Query("SELECT * FROM expenses")
    suspend fun allExpenses(): List<ExpenseEntity>
    @Query("UPDATE expenses SET syncStatus='SYNCED' WHERE syncStatus='LOCAL_ONLY'")
    suspend fun markAllSynced()
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertUser(u: UserEntity)
    @Query("SELECT * FROM users")
    fun observeUsers(): Flow<List<UserEntity>>
}

@Database(entities = [UserEntity::class, ExpenseEntity::class], version = 1, exportSchema = false)
abstract class AppDb : RoomDatabase() { abstract fun dao(): AppDao }

object Hasher {
    fun sha256(s: String): String =
        MessageDigest.getInstance("SHA-256").digest(s.toByteArray()).joinToString("") { "%02x".format(it) }
    /** SHA-256 of (amount + timestamp truncated to minute + merchant) */
    fun expenseHash(amount: Double, ts: Long, merchant: String) =
        sha256("%.2f|%d|%s".format(amount, ts / 60_000, merchant.trim().lowercase()))
}
