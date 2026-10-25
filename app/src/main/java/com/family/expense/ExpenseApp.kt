package com.family.expense

import android.app.Application
import androidx.room.Room
import com.family.expense.data.*
import com.family.expense.p2p.NearbyManager
import kotlinx.coroutines.*

class ExpenseApp : Application() {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    lateinit var repo: Repository
    lateinit var nearby: NearbyManager
    private val prefs by lazy { getSharedPreferences("fam", MODE_PRIVATE) }

    val userId: String get() = prefs.getString("uid", null) ?: java.util.UUID.randomUUID().toString().also { prefs.edit().putString("uid", it).apply() }
    var role: String? get() = prefs.getString("role", null); set(v) = prefs.edit().putString("role", v).apply()
    var name: String get() = prefs.getString("name", "") ?: ""; set(v) = prefs.edit().putString("name", v).apply()

    override fun onCreate() {
        super.onCreate()
        val db = Room.databaseBuilder(this, AppDb::class.java, "family.db").build()
        repo = Repository(db.dao()) { userId }
        nearby = NearbyManager(this, repo, scope)
        repo.onNewLocalExpense = { nearby.broadcast(listOf(it)) }
    }
}
