package com.family.expense.ui

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.family.expense.ExpenseApp
import com.family.expense.data.ExpenseEntity
import com.family.expense.data.UserEntity
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val app = application as ExpenseApp
        setContent { MaterialTheme { Surface { Root(app) } } }
    }
}

private fun perms(): Array<String> {
    val l = mutableListOf(Manifest.permission.RECEIVE_SMS, Manifest.permission.READ_SMS)
    if (Build.VERSION.SDK_INT >= 31) l += listOf(Manifest.permission.BLUETOOTH_ADVERTISE, Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_SCAN)
    else l += Manifest.permission.ACCESS_FINE_LOCATION
    if (Build.VERSION.SDK_INT >= 33) l += Manifest.permission.NEARBY_WIFI_DEVICES
    return l.toTypedArray()
}

@Composable
fun Root(app: ExpenseApp) {
    var role by remember { mutableStateOf(app.role) }
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {}
    LaunchedEffect(Unit) { permLauncher.launch(perms()) }

    val expenses by app.repo.expenses.collectAsState(emptyList())
    val status by app.nearby.status.collectAsState()
    val pending by app.nearby.pending.collectAsState()
    var name by remember { mutableStateOf(app.name) }
    var rel by remember { mutableStateOf("Spouse") }

    pending?.let { req ->
        AlertDialog(onDismissRequest = {},
            title = { Text("Approve ${req.user.name} as ${req.user.relationshipToHead}?") },
            confirmButton = { Button(onClick = { app.nearby.resolve(req, true) }) { Text("Approve") } },
            dismissButton = { TextButton(onClick = { app.nearby.resolve(req, false) }) { Text("Reject") } })
    }

    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Family Expense Manager", style = MaterialTheme.typography.headlineSmall)
        Text("Owner: Ishwar Dayal", style = MaterialTheme.typography.bodyMedium)
        TextButton(onClick = {
            ctx.startActivity(Intent(Intent.ACTION_SENDTO, android.net.Uri.parse("mailto:ishwar766@gmail.com")))
        }, contentPadding = PaddingValues(0.dp)) { Text("Contact: ishwar766@gmail.com") }
        if (role == null) {
            OutlinedTextField(name, { name = it }, label = { Text("Your name") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(rel, { rel = it }, label = { Text("Relationship to Head (members only)") }, modifier = Modifier.fillMaxWidth())
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(enabled = name.isNotBlank(), onClick = {
                    app.name = name; app.role = "HEAD"; role = "HEAD"
                    kotlinx.coroutines.runBlocking { app.repo.saveUser(UserEntity(app.userId, name, "HEAD", "Self", Build.MODEL, "APPROVED")) }
                }) { Text("I'm the Family Head") }
                Button(enabled = name.isNotBlank(), onClick = { app.name = name; app.role = "MEMBER"; role = "MEMBER" }) { Text("I'm a Member") }
            }
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (role == "HEAD") Button(onClick = { app.nearby.startAdvertising(app.name) }) { Text("Host / Accept members") }
                else Button(onClick = {
                    app.nearby.startDiscovery(UserEntity(app.userId, app.name, "MEMBER", rel, Build.MODEL, "PENDING"))
                }) { Text("Join / Sync with Head") }
                OutlinedButton(onClick = { ctx.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) }) { Text("Notification access") }
            }
            Text(status, style = MaterialTheme.typography.bodySmall)
            val total = expenses.filter { !it.isInvestment }.sumOf { it.amount }
            Text("Total family spend: ₹%.2f".format(total), style = MaterialTheme.typography.titleMedium)
            Text("By category", style = MaterialTheme.typography.titleSmall)
            expenses.groupBy { it.category }.mapValues { e -> e.value.sumOf { it.amount } }.forEach { (c, v) -> Text("$c: ₹%.0f".format(v)) }
            HorizontalDivider()
            LazyColumn { items(expenses, key = { it.id }) { ExpenseRow(it) } }
        }
    }
}

@Composable
fun ExpenseRow(e: ExpenseEntity) {
    val fmt = remember { SimpleDateFormat("dd MMM, HH:mm", Locale.getDefault()) }
    ListItem(headlineContent = { Text(e.merchant) },
        supportingContent = { Text("${e.category} • ${fmt.format(Date(e.timestamp))}") },
        trailingContent = { Text("₹%.2f".format(e.amount)) })
}
