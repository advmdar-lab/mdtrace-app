# MDTrace Android APK — Build & Install Guide

## APK क्या करती है?
यह एक **native Android app** है जो:
- ✅ Background में GPS चलाती है (फ़ोन lock हो, screen बंद हो — फर्क नहीं)
- ✅ Phone restart होने पर अपने आप शुरू होती है (BOOT_COMPLETED)
- ✅ हर 30 सेकंड में location सर्वर को भेजती है
- ✅ बच्चे के पास **बंद करने का कोई बटन नहीं** दिखता
- ✅ Battery optimization को खुद bypass करती है

---

## Method 1: Android Studio से Build करें (सबसे आसान)

### ज़रूरी चीज़ें:
- Windows/Mac/Linux PC
- Android Studio (free): https://developer.android.com/studio

### Steps:
1. Android Studio install करें
2. इस folder को खोलें: File → Open → MDTrace-Android folder
3. Gradle sync होने दीजिए (5-10 मिनट, internet चाहिए पहली बार)
4. Build → Build Bundle(s)/APK(s) → Build APK(s)
5. APK मिलेगी: `app/build/outputs/apk/debug/app-debug.apk`
6. वो APK बच्चे के phone पर भेजें और install करें

---

## Method 2: Online Build Service (PC नहीं है तो)

GitHub Actions से free build:

1. GitHub.com पर free account बनाएँ
2. New repository बनाएँ — सारी files upload करें
3. `.github/workflows/build.yml` file बनाएँ:

```yaml
name: Build APK
on: [push]
jobs:
  build:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v3
      - uses: actions/setup-java@v3
        with: { java-version: '17', distribution: 'temurin' }
      - name: Build APK
        run: chmod +x gradlew && ./gradlew assembleDebug
      - uses: actions/upload-artifact@v3
        with:
          name: MDTrace-APK
          path: app/build/outputs/apk/debug/app-debug.apk
```

4. Push करने पर Actions tab में APK download मिलेगी

---

## Method 3: Appetize.io / Buildozer (Advanced)

बाद में ज़रूरत पड़े तो।

---

## APK Install कैसे करें (बच्चे के phone पर):

1. APK file WhatsApp/Google Drive से phone पर भेजें
2. Phone Settings → Security → **Unknown Sources: ON** (या Install Unknown Apps: Allow)
3. APK file पर click करें → Install
4. App खुलेगी → Setup screen आएगी
5. **Admin (आप)** बच्चे के phone में:
   - आपका invite code डालें (kwix.shop/login.html के Admin tab से)
   - बच्चे का नाम और नंबर डालें
   - "Join Family & Start" दबाएँ
6. ✅ बस! अब GPS हमेशा चलता रहेगा

---

## Important Notes:

- **Android 10+** पर: Location permission → "Allow all the time" चुनें
- **Battery Optimization**: App खुद dismiss करने का popup दिखाएगी — Allow करें
- **Xiaomi/MIUI phones**: Settings → Battery → Power Plan → No restrictions → MDTrace को allow करें
- **Samsung phones**: Device Care → Battery → Background usage limits → MDTrace को never sleep में रखें

---

## Invite Code कहाँ मिलेगा?
kwix.shop पर Login → Admin tab → "Your Family Invite Code" में दिखेगा
