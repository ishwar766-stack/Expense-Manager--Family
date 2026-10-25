package com.family.expense.engine

data class ParsedExpense(val amount: Double, val merchant: String, val category: String)

object RuleEngine {
    private val ignoreWords = listOf("sip", "mutual fund", "groww", "zerodha", "nps", "ppf",
        "self transfer", "sent to family", "family group", "self-transfer", "own account")
    private val ignoreRegex = ignoreWords.map { Regex("\\b" + Regex.escape(it) + "\\b", RegexOption.IGNORE_CASE) }
    private val spendTriggers = listOf("debited", "spent", "paid to", "paid via upi", "paid", "transferred to", "purchase of", "sent rs")
    private val amountRx = Regex("(?:rs\\.?|inr|₹)\\s*([0-9][0-9,]*(?:\\.[0-9]{1,2})?)", RegexOption.IGNORE_CASE)
    private val merchantRx = Regex("(?:\\bat\\b|\\bto\\b|\\bpaid to\\b|\\bfor\\b)\\s+([A-Za-z0-9][A-Za-z0-9 &.@_'-]{1,30}?)(?=\\s+(?:on|via|ref|upi|using|from|a/c|avl|\\.|,)|[.,]|$)", RegexOption.IGNORE_CASE)

    private val rules: List<Pair<String, List<String>>> = listOf(
        "Groceries & Essentials" to listOf("bigbasket", "blinkit", "zepto", "instamart", "sabji", "vegetable", "kirana", "supermart", "provision"),
        "Food & Dining" to listOf("swiggy", "zomato", "dhaba", "hotel", "sweets", "bakery", "restaurant"),
        "Medical & Healthcare" to listOf("apollo", "pharmeasy", "1mg", "medplus", "hospital", "clinic", "health", "medicals", "chemist", "pharma"),
        "Travel & Commute" to listOf("uber", "ola", "irctc", "makemytrip", "goibibo", "indigo", "rapido", "redbus", "auto", "rickshaw", "yatri", "metro", "parking", "toll"),
        "Shopping" to listOf("amazon", "flipkart", "myntra", "meesho")
    )

    fun isIgnored(text: String) = ignoreRegex.any { it.containsMatchIn(text) }

    /** Returns null for investments, family/self transfers, credits and non-spend text. */
    fun parse(raw: String): ParsedExpense? {
        val text = raw.replace("\n", " ").trim()
        val lower = text.lowercase()
        if (isIgnored(lower)) return null
        if (Regex("\\b(credited|received|refund|otp)\\b").containsMatchIn(lower) && !lower.contains("debited")) return null
        if (spendTriggers.none { lower.contains(it) }) return null
        val amount = amountRx.find(text)?.groupValues?.get(1)?.replace(",", "")?.toDoubleOrNull() ?: return null
        val merchant = merchantRx.findAll(text).map { it.groupValues[1].trim() }
            .firstOrNull { !it.matches(Regex("(?i)rs.*|inr.*|a/c.*|your.*|the.*")) } ?: "Unknown"
        return ParsedExpense(amount, merchant, categorize(lower))
    }

    fun categorize(lower: String): String =
        rules.firstOrNull { (_, kws) -> kws.any { k -> Regex("\\b" + Regex.escape(k)).containsMatchIn(lower) } }?.first ?: "Other Expense"
}
