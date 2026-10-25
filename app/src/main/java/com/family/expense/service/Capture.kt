package com.family.expense.service

import android.app.Notification
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.family.expense.ExpenseApp
import com.family.expense.engine.RuleEngine
import kotlinx.coroutines.launch

class SmsReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return
        val app = ctx.applicationContext as ExpenseApp
        val body = Telephony.Sms.Intents.getMessagesFromIntent(intent).joinToString("") { it.messageBody ?: "" }
        val pending = goAsync()
        app.scope.launch {
            try { RuleEngine.parse(body)?.let { app.repo.addParsed(it) } } finally { pending.finish() }
        }
    }
}

class TxnNotificationListener : NotificationListenerService() {
    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (sbn.packageName == packageName) return
        val ex = sbn.notification.extras
        val text = listOfNotNull(ex.getCharSequence(Notification.EXTRA_TITLE), ex.getCharSequence(Notification.EXTRA_BIG_TEXT)
            ?: ex.getCharSequence(Notification.EXTRA_TEXT)).joinToString(" ")
        if (text.isBlank()) return
        val app = applicationContext as ExpenseApp
        app.scope.launch { RuleEngine.parse(text)?.let { app.repo.addParsed(it) } }
    }
}
