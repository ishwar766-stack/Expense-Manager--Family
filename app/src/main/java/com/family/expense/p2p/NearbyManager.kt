package com.family.expense.p2p

import android.content.Context
import com.family.expense.data.*
import com.google.android.gms.nearby.Nearby
import com.google.android.gms.nearby.connection.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

data class PendingRequest(val endpointId: String, val user: UserEntity)

class NearbyManager(ctx: Context, private val repo: Repository, private val scope: CoroutineScope) {
    private val client = Nearby.getConnectionsClient(ctx)
    private val SERVICE_ID = "com.family.expense.SERVICE"
    private val strategy = Strategy.P2P_STAR

    val status = MutableStateFlow("Idle")
    val pending = MutableStateFlow<PendingRequest?>(null)
    private val approved = mutableSetOf<String>()     // endpoints allowed to sync
    private var isHead = false
    private var myProfile: UserEntity? = null

    // ---------- HEAD ----------
    fun startAdvertising(headName: String) {
        isHead = true
        client.startAdvertising(headName, SERVICE_ID, connCb, AdvertisingOptions.Builder().setStrategy(strategy).build())
            .addOnSuccessListener { status.value = "Hosting as $headName. Waiting for family…" }
            .addOnFailureListener { status.value = "Advertise failed: ${it.message}" }
    }

    /** Called from the Compose approval dialog. */
    fun resolve(req: PendingRequest, approve: Boolean) {
        pending.value = null
        if (!approve) { send(req.endpointId, JSONObject().put("t", "REJECTED")); client.disconnectFromEndpoint(req.endpointId); return }
        scope.launch {
            repo.saveUser(req.user.copy(approvalStatus = "APPROVED"))
            approved += req.endpointId
            send(req.endpointId, JSONObject().put("t", "APPROVED"))
            send(req.endpointId, expensesMsg(repo.allExpenses()))
            status.value = "${req.user.name} approved"
        }
    }

    // ---------- MEMBER ----------
    fun startDiscovery(profile: UserEntity) {
        isHead = false; myProfile = profile
        client.startDiscovery(SERVICE_ID, object : EndpointDiscoveryCallback() {
            override fun onEndpointFound(id: String, info: DiscoveredEndpointInfo) {
                status.value = "Found ${info.endpointName}, connecting…"
                client.requestConnection(profile.name, id, connCb)
            }
            override fun onEndpointLost(id: String) {}
        }, DiscoveryOptions.Builder().setStrategy(strategy).build())
            .addOnSuccessListener { status.value = "Searching for Family Head…" }
            .addOnFailureListener { status.value = "Discovery failed: ${it.message}" }
    }

    // ---------- COMMON ----------
    private val connCb = object : ConnectionLifecycleCallback() {
        override fun onConnectionInitiated(id: String, info: ConnectionInfo) { client.acceptConnection(id, payloadCb) }
        override fun onConnectionResult(id: String, r: ConnectionResolution) {
            if (!r.status.isSuccess) { status.value = "Connection failed"; return }
            if (!isHead) {
                client.stopDiscovery()
                val u = myProfile!!
                send(id, JSONObject().put("t", "REGISTER").put("id", u.id).put("name", u.name)
                    .put("rel", u.relationshipToHead).put("device", u.deviceId))
                status.value = "Connected. Waiting for Head's approval…"
            }
        }
        override fun onDisconnected(id: String) { approved -= id }
    }

    private val payloadCb = object : PayloadCallback() {
        override fun onPayloadReceived(id: String, p: Payload) {
            val m = JSONObject(String(p.asBytes() ?: return))
            when (m.getString("t")) {
                "REGISTER" -> if (isHead) pending.value = PendingRequest(id,
                    UserEntity(m.getString("id"), m.getString("name"), "MEMBER", m.getString("rel"), m.getString("device"), "PENDING"))
                "APPROVED" -> { approved += id; status.value = "Approved! Syncing…"
                    scope.launch { send(id, expensesMsg(repo.allExpenses())) } }
                "REJECTED" -> status.value = "Head rejected the request"
                "EXPENSES" -> if (id in approved) scope.launch {
                    val arr = m.getJSONArray("data")
                    val incoming = (0 until arr.length()).map { fromJson(arr.getJSONObject(it)) }
                    incoming.forEach { repo.safeInsertExpense(it) }
                    if (isHead) approved.filter { it != id }.forEach { send(it, expensesMsg(incoming)) } // relay to other members
                    status.value = "Synced ${incoming.size} records"
                }
            }
        }
        override fun onPayloadTransferUpdate(id: String, u: PayloadTransferUpdate) {}
    }

    fun broadcast(list: List<ExpenseEntity>) { approved.forEach { send(it, expensesMsg(list)) } }
    private fun send(id: String, m: JSONObject) { client.sendPayload(id, Payload.fromBytes(m.toString().toByteArray())) }
    fun stop() { client.stopAllEndpoints(); client.stopAdvertising(); client.stopDiscovery(); approved.clear() }

    private fun expensesMsg(l: List<ExpenseEntity>) = JSONObject().put("t", "EXPENSES").put("data", JSONArray(l.map {
        JSONObject().put("id", it.id).put("amt", it.amount).put("ts", it.timestamp).put("m", it.merchant)
            .put("c", it.category).put("inv", it.isInvestment).put("by", it.createdByUserId).put("h", it.hashSignature)
    }))
    private fun fromJson(j: JSONObject) = ExpenseEntity(j.getString("id"), j.getDouble("amt"), j.getLong("ts"), j.getString("m"),
        j.getString("c"), j.getBoolean("inv"), j.getString("by"), j.getString("h"), "SYNCED")
}
