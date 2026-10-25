# Family Expense Manager

An offline-first Android app that tracks family spending automatically. Bank SMS and UPI/merchant
notifications are parsed on-device, categorised, and synced between family phones over
Wi-Fi/Bluetooth (Google Nearby Connections) — **no internet or cloud server needed**.

**Owner:** Ishwar Dayal · **Contact:** [ishwar766@gmail.com](mailto:ishwar766@gmail.com)

## Features
- Auto-detects expenses from bank SMS and UPI / Amazon / Swiggy / etc. notifications
- Auto-categorises: Shopping, Groceries & Essentials, Food & Dining, Medical & Healthcare, Travel & Commute, Other
- Ignores investments (SIP, mutual fund, Groww, Zerodha, NPS, PPF) and self/family transfers
- De-duplicates the same payment seen via SMS + notification (SHA-256 of amount + minute + merchant)
- Family Head approves each member; data syncs peer-to-peer (`P2P_STAR`)
- Kotlin, Jetpack Compose, Room

## Setup
1. Clone: `git clone https://github.com/<your-username>/FamilyExpenseManager.git`
2. Open in Android Studio (Koala or newer) and let Gradle sync (it creates the Gradle wrapper).
3. Run on 2+ phones (Android 8.0 / API 26+).
4. Grant SMS + Nearby permissions and enable **Notification access** for the app.

## How it works
| Role | Action |
|------|--------|
| Head | "I'm the Family Head" → *Host / Accept members* → approve requests |
| Member | "I'm a Member" → enter relationship → *Join / Sync with Head* |

## Project structure
```
app/src/main/java/com/family/expense/
├── data/      Room entities, DAO, repository, hashing
├── engine/    RuleEngine (parsing, filtering, categories)
├── service/   SmsReceiver, TxnNotificationListener
├── p2p/       NearbyManager (advertise, discover, approve, sync)
└── ui/        Compose screens
```

## Privacy
All data stays on family devices. Nothing is uploaded to any server. SMS/notification text is
parsed locally and only the extracted expense fields are stored and synced.

## Known limitations
- Merchant naming differences between SMS and notification can cause duplicates
- Bank SMS formats vary; tune rules in `RuleEngine.kt`
- No edit/delete screen yet

## Contributing
Issues and pull requests are welcome. Please email ishwar766@gmail.com for questions.

## License
MIT — see [LICENSE](LICENSE).
