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
`server.js`-এর সর্বশেষ root ফাইলটিই ব্যবহার করুন (আগে পুরোনো ফাইলের ব্যাকআপ নিন)। এতে FCM message push ও incoming-call signaling যুক্ত আছে। সার্ভার ফোল্ডারে চালান:

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

## Latest social/profile features
- Long-press a sent or received message/file to delete it. Sender deletion removes it for both participants; recipient deletion hides it only for that recipient.
- Use **বন্ধু চাই** to opt into the public discovery list; users in that list can receive friend requests.
- Set a profile image from the home screen; images are limited to 5 MB.
- Android screenshots and screen recording are blocked inside the app window using `FLAG_SECURE` (external cameras cannot be blocked).
- The server file at the project root is `server.js`; configure Firebase Admin service-account credentials before enabling FCM.


## End-to-End Encryption (Android ↔ Android) এবং Secure Me

এই সংস্করণে নতুন Android ক্লায়েন্টের টেক্সট মেসেজ এবং অ্যাটাচমেন্টের কনটেন্ট **ডিভাইসে এনক্রিপ্ট** হয়:
- AES-256-GCM দিয়ে মেসেজ/ফাইলের কনটেন্ট এনক্রিপ্ট করা হয়।
- AES key প্রেরক ও প্রাপকের Android Keystore RSA-OAEP public key দিয়ে আলাদাভাবে wrap করা হয়।
- RSA private key Android Keystore-এ থাকে; সার্ভারে পাঠানো হয় না।
- সার্ভার `text` ciphertext, encrypted file bytes এবং wrapped-key envelope রাখে। FCM নোটিফিকেশনে মেসেজের লেখা/ফাইলের নাম পাঠানো হয় না।
- প্রথমবার বন্ধুর public key পেলে সেটি local SharedPreferences-এ pin করা হয়; পরে key বদলে গেলে অ্যাপ মেসেজ পাঠানো বন্ধ করে সতর্ক করে (trust-on-first-use; প্রথমবার fingerprint আলাদা মাধ্যমে যাচাই করাই নিরাপদ)।
- Secure Me checkbox চালু থাকলে ১–৬০ সেকেন্ড বেছে নেওয়া যায়; ডিফল্ট ৩ সেকেন্ড। প্রাপকের Android অ্যাপে কনটেন্ট decrypt/display হওয়ার পর timer শুরু হয়। সময় শেষে দুই পক্ষের UI থেকে সরানো হয় এবং সার্ভারের সংরক্ষিত রেকর্ড/এনক্রিপ্টেড ফাইল মুছে ফেলা হয়। না-খোলা Secure Me কনটেন্ট সার্ভার থেকে সর্বোচ্চ ২৪ ঘণ্টা পরে fallback cleanup হয়।
- অ্যাপ উইন্ডোতে `FLAG_SECURE` চালু; Secure Me ফাইলের ডাউনলোড বাটন/বাইরের অ্যাপে খোলা বন্ধ করা হয়েছে।

### সার্ভার আপডেট
প্রজেক্টের root-এ থাকা **`server.js`**-ই ব্যবহার করুন। এতে E2EE public-key API, encrypted upload, Secure Me expiry এবং আগের FCM incoming-call offer/candidate replay—সব একত্র করা আছে। পুরোনো সার্ভারের ব্যাকআপ নিয়ে এই `server.js` deploy করুন; `server-package.json`-এর dependency install করুন এবং Firebase Admin service-account environment variables আগের মতো সেট রাখুন।

### গুরুত্বপূর্ণ সীমাবদ্ধতা
- E2EE-এর জন্য দুই পক্ষকেই এই Android সংস্করণ ব্যবহার করতে হবে এবং উভয়ের public key নিবন্ধিত থাকতে হবে। Web/Electron-এর পুরোনো plaintext ক্লায়েন্ট এই encrypted Android মেসেজ পড়তে পারবে না; ওই ক্লায়েন্ট থেকে পাঠানো পুরোনো/নতুন plaintext মেসেজ E2EE হবে না।
- পুরোনো `messages.json`-এর মেসেজ স্বয়ংক্রিয়ভাবে এনক্রিপ্ট হয় না। নতুন Android মেসেজই ডিভাইস থেকে এনক্রিপ্ট হয়ে যায়।
- সার্ভার এখনও routing-এর জন্য প্রেরক/প্রাপক ID, message ID, সময়, secure timer, file size/URL এবং online/friend metadata দেখতে পারে। Username, public profile photo ও friend/discovery তথ্যও এই পরিবর্তনে E2EE করা হয়নি।
- এই সংস্করণে প্রতি অ্যাকাউন্টে এক Android keypair ধরে নেওয়া হয়েছে। অ্যাপ ডেটা মুছে ফেললে/অ্যাপ আনইনস্টল করলে private key হারিয়ে পুরোনো encrypted মেসেজ আর খোলা যাবে না। Key recovery বা multi-device key sync নেই।
- Secure Me screenshot/recording প্রতিরোধ Android-এর স্বাভাবিক capture path-এ কাজ করে; অন্য ফোনের ক্যামেরা, rooted/compromised device, modified OS বা receiver নিজে কনটেন্ট কপি করা শতভাগ আটকানো যায় না।
- এই পরিবর্তনের জন্য Kotlin/Gradle source staticভাবে পর্যালোচনা করা হয়েছে; এখানে Android APK build ও দুইটি বাস্তব ফোনে end-to-end test চালানো সম্ভব হয়নি। Deploy করার আগে Android Studio/GitHub Actions-এ build করে দুইটি Android account দিয়ে test করুন।


### QR Friend Add (Android)
- Home screen has “আমার QR দেখান” and “QR স্ক্যান করুন”.
- QR contains only the account UID in an `adda://friend/` payload; no password, token, or encryption key is included.
- A successful scan immediately adds both accounts as friends via `/api/friends/qr-add`.
- Camera permission is requested only when scanning.

### Video upload transcoding

Selected videos are checked against the 5-minute limit, then transcoded before E2EE encryption/upload to MP4 with H.265/HEVC video, AAC audio, a maximum 1280x720 frame size, and 30 fps. This uses the FFmpegKit full build (including libx265). WebM is intentionally not used for HEVC because HEVC is not a standard WebM codec; MP4 is the compatible container for H.265. Transcoded output remains subject to the server's existing 50 MB upload limit. Device/build verification is required before release.


## v6 — নতুন পরিবর্তন

- **প্রোফাইল ছবি:** বন্ধু ট্যাবের ওপরে নিজের বড় প্রোফাইল ছবি (নামসহ) হেডারের ওপর ভেসে থাকে। যে কারও প্রোফাইল ছবিতে (বন্ধু তালিকা, আগ্রহী তালিকা, চ্যাটের হেডার) ট্যাপ করলে নাম সহ বড় পপআপ কার্ডে ছবি দেখা যায় (`ui/ProfilePopup.kt`)।
- **নিচের ট্যাব:** *বন্ধু* = বন্ধু তালিকা, *আগ্রহী* = ফ্রেন্ড রিকুয়েস্ট + যারা “বন্ধু চাই” চালু করেছেন তাদের তালিকা।
- **মেসেজ/ফাইল ডিলেট:** মেসেজ বা ফাইলে ট্যাপ (ছবি/ভিডিওতে লং-প্রেস) করলে মেনু খোলে — *শুধু আমার থেকে ডিলেট* অথবা (নিজের পাঠানো হলে) *উভয় দিক থেকে ডিলেট*। সার্ভার: `DELETE /api/messages/:id?scope=me|everyone`।
- **বড় ইমোজি:** শুধু ১–৩টি ইমোজি পাঠালে বাবল ছাড়া বড় করে দেখায়।
- **আগের ছবি/ভিডিও ব্লার:** চ্যাট খুললে আগের মেসেজের ছবি/ভিডিও ডাউনলোড হয় না, ব্লার কার্ড দেখায়; ট্যাপ করলে লোড হয়। (নতুন আসা/পাঠানো মিডিয়া সাথে সাথে দেখায়।)
- **৩ দিনের অটো-ডিলেট:** ৩ দিনের পুরনো মেসেজ ও ফাইল সার্ভার থেকে (প্রতি ১০ মিনিটে + সার্ভার চালুর সময়) এবং ডিভাইস থেকে (লোকাল ক্যাশসহ) মুছে যায়। প্রোফাইল ছবি মোছে না।
- **একটাই APK:** ABI split বন্ধ; একটি `app-release.apk` তে arm64-v8a ও armeabi-v7a দুটোই আছে, তাই সব মোবাইলে একই ফাইল ইনস্টল হয়। (সাইজ আগের একেকটি APK-র চেয়ে বেশি হবে।)

**সার্ভার আপডেট:** নতুন `server.js` ডিপ্লয় করুন (আগে ব্যাকআপ নিন)। নতুন অ্যাপে ডিলেটের scope ও ৩ দিনের নিয়ম সার্ভারের সাথে মিলে কাজ করে।


## v7 — নতুন ডিজাইন (১৬–২২ বয়সীদের জন্য)

- **রঙ:** ইলেকট্রিক ভায়োলেট → হট-পিংক গ্রেডিয়েন্ট (`ui/Theme.kt`: `Accent`, `BubbleBrush`, `StoryRing`)। পুরো অ্যাপে একই থিম, তাই রঙ বদলাতে শুধু Theme.kt বদলালেই হবে।
- **লগইন স্ক্রিন:** গ্রেডিয়েন্ট ব্যাকগ্রাউন্ড, ভাসমান ইমোজি, সাদা কার্ড, গ্রেডিয়েন্ট বাটন।
- **হেডার:** হোম ও চ্যাট দুই জায়গাতেই গোলাকার-নিচ গ্রেডিয়েন্ট হেডার; স্ট্যাটাস বারের আইকন সাদা।
- **নিচের নেভিগেশন:** ভাসমান পিল; সিলেক্ট করা ট্যাব গ্রেডিয়েন্টে জ্বলে ওঠে, আনরিড/রিকুয়েস্ট ব্যাজসহ।
- **স্টোরি-স্টাইল অনলাইন বন্ধু:** বন্ধু ট্যাবের উপরে অনলাইনে থাকা বন্ধুদের রঙিন রিংসহ সারি; ট্যাপ করলে চ্যাট খোলে।
- **অ্যাভাটার:** ছবি না থাকলে নাম অনুযায়ী আলাদা রঙিন গ্রেডিয়েন্টে নামের প্রথম অক্ষর।
- **চ্যাট:** বড় গোল বাবল (নিজের মেসেজ গ্রেডিয়েন্টে), হালকা রঙিন ব্যাকগ্রাউন্ড, উপরের দিকে গোল কম্পোজার।
- **কার্ড/লিস্ট:** বেশি গোল কোণ ও নরম ছায়া, গ্রেডিয়েন্ট আনরিড ব্যাজ।


## Adda app download QR screen

The bottom navigation now includes **ডাউনলোড**. It creates a QR code for the configured public APK URL. The QR code does not host the APK; first publish the signed APK at a stable public HTTPS download URL. Configure it in GitHub Actions/build environment as `DOWNLOAD_URL=https://your-domain.example/adda.apk`, or enter the actual URL in the screen. Leave it blank until a real APK URL is available. Test the URL from a different device before sharing the QR.
