package com.family.expense.data

import com.family.expense.engine.ParsedExpense
import java.util.UUID

class Repository(private val dao: AppDao, private val currentUserId: () -> String) {
    var onNewLocalExpense: ((ExpenseEntity) -> Unit)? = null
    val expenses = dao.observeExpenses()
    val users = dao.observeUsers()
    suspend fun allExpenses() = dao.allExpenses()
    suspend fun saveUser(u: UserEntity) = dao.upsertUser(u)
    suspend fun markSynced() = dao.markAllSynced()

    /** De-duplicating insert: drops entry if same hashSignature already exists. */
    suspend fun safeInsertExpense(e: ExpenseEntity): Boolean {
        if (dao.countByHash(e.hashSignature) > 0) return false
        return dao.insertExpense(e) != -1L
    }

    suspend fun addParsed(p: ParsedExpense, ts: Long = System.currentTimeMillis()): Boolean {
        val e = ExpenseEntity(UUID.randomUUID().toString(), p.amount, ts, p.merchant, p.category,
            false, currentUserId(), Hasher.expenseHash(p.amount, ts, p.merchant), "LOCAL_ONLY")
        val ok = safeInsertExpense(e)
        if (ok) onNewLocalExpense?.invoke(e)
        return ok
    }
}
