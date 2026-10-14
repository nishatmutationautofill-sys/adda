# আড্ডা — নেটিভ Android অ্যাপ (Kotlin + Jetpack Compose)

HTML/WebView নয় — পুরোপুরি নেটিভ Kotlin অ্যাপ। আগের ওয়েব ভার্সনের একই সার্ভার (REST + Socket.IO + Firestore লিংক) ব্যবহার করে,
তাই ওয়েব ও নেটিভ ব্যবহারকারীরা একে অপরকে মেসেজ ও কল করতে পারবে।

## GitHub দিয়ে বিল্ড
1. এই ফোল্ডারের সব ফাইল (`.github` সহ) নতুন GitHub repository তে `main` ব্রাঞ্চে আপলোড করুন।
2. **Actions → Build Android APK (Kotlin)** চলবে (না চললে **Run workflow**)।
3. শেষ হলে রান পেজের **Artifacts → adda-native-debug-apk** ডাউনলোড করে `app-debug.apk` ইনস্টল করুন।

`app/debug.keystore` একই থাকে, তাই নতুন বিল্ড পুরনোটার ওপর আপডেট হিসেবে ইনস্টল হবে। (এই APK ও আগের Capacitor APK আলাদা অ্যাপ: `com.adda.chatkt`)

## ফিচার
- লগইন / সাইন আপ (টোকেন সংরক্ষিত), সার্ভার লিংক Firestore `appConfig/runtime` থেকে স্বয়ংক্রিয়
- বন্ধু যোগ, রিকুয়েস্ট গ্রহণ/বাতিল, অনলাইন স্ট্যাটাস, আনরিড কাউন্ট
- রিয়েলটাইম চ্যাট (Socket.IO), ছবি/ফাইল পাঠানো (ছবি পাঠানোর আগে imo-র মতো কমপ্রেস: সর্বোচ্চ 1280px, JPEG 70%)
- অডিও/ভিডিও কল (WebRTC): নয়েজ সাপ্রেশন + ইকো ক্যান্সেলেশন + অটো গেইন, ভিডিও 480×360 @20fps ~350kbps, অডিও ~32kbps
- কলে মিউট, স্পিকার, ক্যামেরা অন/অফ, ক্যামেরা বদল, ইনকামিং কল স্ক্রিন + রিংটোন
- অ্যাপ ব্যাকগ্রাউন্ডে (প্রসেস চালু থাকলে) নোটিফিকেশন
- **সবসময় চালু (Foreground Service):** অ্যাপ বন্ধ করলে/রিসেন্ট থেকে সরালেও সার্ভারের সাথে সংযুক্ত থাকে; নেট ফিরলে নিজে থেকে রিকানেক্ট; ফোন রিস্টার্টের পরও চালু হয় (`service/ConnectionService.kt`, `service/BootReceiver.kt`)
- **ইনকামিং কল:** ফুল-স্ক্রিন কল স্ক্রিন (লক স্ক্রিনেও), রিসিভ/রিজেক্ট বাটন, রিংটোন (লুপ) + ভাইব্রেশন; ৫০ সেকেন্ডে না ধরলে মিসড কল নোটিফিকেশন
- **আউটগোয়িং কল:** ডায়ালিং/রিংব্যাক টোন (কানের স্পিকারে), ৪৫ সেকেন্ডে না ধরলে অটো কল শেষ, কল শেষে ছোট বিপ/ব্যস্ত টোন

## কোড কাঠামো
- `data/Api.kt` — REST ও Firestore লিংক, `data/Session.kt` — অবস্থা, লগইন, সকেট, চ্যাট
- `call/CallManager.kt` — WebRTC কল
- `util/FileUpload.kt` — ছবি কমপ্রেস ও আপলোড, `util/Notifier.kt` — নোটিফিকেশন
- `ui/*` — Compose স্ক্রিন

## মনে রাখবেন
- সার্ভারে Firestore নিয়ম `appConfig/runtime` পড়ার অনুমতি দিতে হবে (ওয়েব ভার্সনে যেমন আছে)।
- প্রথমবার লগইনের পর অ্যাপ "ব্যাটারি অপ্টিমাইজেশন" ও (Android 14+) "ফুল-স্ক্রিন নোটিফিকেশন" অনুমতি চাইবে — দুটোই দিন।
- Xiaomi/Redmi/Realme/Oppo/Vivo/Samsung এ অতিরিক্ত সেটিং লাগে: Settings → Apps → আড্ডা → **Autostart চালু** + Battery → **No restrictions**। রিসেন্ট অ্যাপ লিস্টে আড্ডা **লক (🔒)** করে রাখুন।
- ফোন ফোর্স-স্টপ করলে (Settings থেকে) Android সার্ভিস বন্ধ করে দেয়; আবার অ্যাপ খুললে চালু হবে।
- অ্যাপ সম্পূর্ণ মেরে ফেলা অবস্থায় (OEM কিল) নির্ভরযোগ্য কল/মেসেজের জন্য Firebase Cloud Messaging (সার্ভারে হাই-প্রায়োরিটি পুশ সহ) যোগ করা সবচেয়ে ভালো।
- সার্ভারের বাইরে TURN সার্ভার না থাকলে কিছু মোবাইল নেটওয়ার্কে কল সংযোগ নাও হতে পারে।
- `io.getstream:stream-webrtc-android:1.1.1` ডিপেন্ডেন্সি না পেলে `app/build.gradle.kts` এ সর্বশেষ ভার্সন বসান।
- Android Studio তে খুললে Gradle ওয়্যাপার না থাকায় Studio নিজের Gradle ব্যবহার করতে বলবে (Gradle 8.9 / JDK 17)।


## Friend search and background connection

- The friend list has a search field that filters by friend username or UID.
- A foreground connection service, persistent notification, wake lock, network callbacks,
  Socket.IO reconnection, and a 10-second connection watchdog are used to improve background reliability.
- Android cannot guarantee a process or network socket will remain alive under every condition.
  Force Stop, vendor battery managers, revoked permissions, no network, and server downtime can still disconnect it.
  For best results, allow notifications and set the app battery usage to Unrestricted / Don't optimize.


## Chat media features added

- In-chat download button uses Android Download Manager and saves attachments to Downloads.
- Camera button opens the device camera app and requests a maximum 60-second clip; selected/recorded clip is sent through the existing `/api/messages/file` endpoint.
- Microphone button records AAC/M4A voice notes and uploads them as attachments.
- Emoji picker includes a built-in starter set and imports JSON packs. Sample pack: `sample-emoji-pack/adda-starter-emoji-pack.json`.
- Emoji pack JSON schema: `{ "name": "My pack", "version": 1, "emojis": ["😀", "❤️"] }`.

Media sending depends on the current server's `/api/messages/file` endpoint and its 50 MB upload limit. Video capture limits are also subject to the camera app/device. Voice recording requires microphone permission.


## Firebase Cloud Messaging (FCM) — নতুন সেটআপ

এই সংস্করণে Android অ্যাপ FCM token সংগ্রহ করে লগইন করা ব্যবহারকারীর সঙ্গে সার্ভারে নিবন্ধন করে। সার্ভার ব্যবহারকারীর Socket.IO সংযোগ না থাকলে নতুন টেক্সট/ফাইল মেসেজ ও ফ্রেন্ড রিকুয়েস্টের push পাঠানোর চেষ্টা করে। এটি Socket.IO বা Firestore থেকে সার্ভার URL নেওয়ার ব্যবস্থা বদলায় না।

### 1) সঠিক `google-services.json`
বর্তমানে পাওয়া Firebase ফাইলটি `com.nishat.ghostmessenger` প্যাকেজের; Adda-এর `applicationId` হলো `com.adda.chatkt`। **ভুল প্যাকেজের ফাইল ব্যবহার করবেন না।**
Firebase Console → Project settings → Your apps → Add app (Android) থেকে একই Firebase project `ghostmessenger-1bcaf`-এ `com.adda.chatkt` প্যাকেজের Android app নিবন্ধন করুন, তারপর সেই অ্যাপের `google-services.json` ডাউনলোড করে `app/google-services.json`-এ রাখুন। এতে Google Services Gradle plugin স্বয়ংক্রিয়ভাবে চালু হবে। ফাইলটি না থাকলে প্রজেক্ট কম্পাইল হতে পারে, কিন্তু FCM কার্যকর হবে না।

### 2) সার্ভার সেটআপ
`server-fcm.js`-এর কোড বর্তমান `server.js`-এর জায়গায় ব্যবহার করুন (আগে ব্যাকআপ নিন)। সার্ভার ফোল্ডারে চালান:

```bash
npm install express socket.io multer cors bcryptjs jsonwebtoken firebase-admin
```

Firebase Console → Project settings → Service accounts থেকে Admin SDK service-account key তৈরি করুন। সার্ভার মেশিনে ফাইলটি নিরাপদ স্থানে রাখুন এবং environment variable সেট করুন:

```bash
# Linux/macOS
export GOOGLE_APPLICATION_CREDENTIALS="/secure/path/firebase-admin-key.json"
export FIREBASE_PROJECT_ID="ghostmessenger-1bcaf"
export JWT_SECRET="replace-with-a-long-random-secret"
node server.js
```

Windows PowerShell:
```powershell
$env:GOOGLE_APPLICATION_CREDENTIALS="C:\secure\path\firebase-admin-key.json"
$env:FIREBASE_PROJECT_ID="ghostmessenger-1bcaf"
$env:JWT_SECRET="replace-with-a-long-random-secret"
node server.js
```

সার্ভিস অ্যাকাউন্ট JSON কখনও GitHub repository, APK, বা চ্যাটে আপলোড করবেন না। পুরোনো `data/users.json` ফাইলে FCM token নতুন করে লগইন/অ্যাপ চালুর পর যুক্ত হবে। আগে থেকে থাকা ব্যবহারকারীদের token ম্যানুয়ালি তৈরি করতে হবে না।

### 3) আচরণ ও সীমাবদ্ধতা
- অনলাইন Socket.IO সংযোগ থাকলে বিদ্যমান লাইভ ইভেন্ট ব্যবহার হবে; সংযোগ না থাকলে সার্ভার FCM push পাঠানোর চেষ্টা করবে।
- প্রথমে অ্যাপ খুলে লগইন করতে হবে, যাতে ডিভাইসের FCM token সার্ভারে নিবন্ধিত হয়।
- Android 13+ এ নোটিফিকেশন অনুমতি চালু থাকতে হবে।
- Force Stop অবস্থায় Android সাধারণত push/ব্যাকগ্রাউন্ড কার্যক্রম স্থগিত রাখে; ব্যবহারকারী আবার অ্যাপ চালু না করা পর্যন্ত নিশ্চয়তা নেই।
- এই সংযোজন মেসেজ ও ফ্রেন্ড রিকুয়েস্টের push কভার করে; WebRTC কলের FCM invite/signaling এতে অন্তর্ভুক্ত নয়।
