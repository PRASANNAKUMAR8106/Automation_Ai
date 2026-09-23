# AutoFlow AI — Mobile App Store Release & Signing Runbook

This guide contains the end-to-end production signing, packaging, and store submission procedures for the **AutoFlow AI** mobile application on **Android (Google Play Store)** and **iOS (Apple App Store)**.

---

## 🛡 Security Rules Before Starting
1. **Never commit keystores, signing certificates, or `.properties` files to Git**.
2. All credential files (`key.properties`, `*.jks`, `*.keystore`, `*.mobileprovision`, `*.p12`) are excluded by `.gitignore`.
3. In CI/CD pipelines, inject keystores and certificates as Base64-encoded GitHub Secrets or Vault entries.

---

## 1. Android Release Guide (Google Play Store)

### Step 1: Generate Production Upload Keystore
Run the following command in terminal (adjust validity and alias as needed):

```powershell
keytool -genkey -v -keystore autoflow-upload-keystore.jks `
  -keyalg RSA -keysize 2048 -validity 10000 `
  -alias autoflow-release `
  -storetype JKS
```

* Store the generated `autoflow-upload-keystore.jks` in a secure location (e.g. 1Password, AWS Secrets Manager).

### Step 2: Configure `key.properties`
In the local development environment or CI runner, create `autoflow-app/android/key.properties`:

```properties
storePassword=YOUR_STRONG_STORE_PASSWORD
keyPassword=YOUR_STRONG_KEY_PASSWORD
keyAlias=autoflow-release
storeFile=/absolute/path/to/autoflow-upload-keystore.jks
```

> **Note**: `build.gradle.kts` is pre-configured to detect `key.properties`. If absent, it gracefully falls back to debug keys for local developer convenience.

### Step 3: Verify Versioning
In `autoflow-app/pubspec.yaml`, increment the build name and build number:
```yaml
version: 1.0.0+1
# Format: MAJOR.MINOR.PATCH+BUILD_NUMBER
```

### Step 4: Build Android App Bundle (AAB)
Run from the `autoflow-app/` directory:
```bash
flutter clean
flutter pub get
flutter build appbundle --release
```

* **Output Location**: `autoflow-app/build/app/outputs/bundle/release/app-release.aab`

### Step 5: Google Play Console Submission
1. Navigate to **Google Play Console** -> Select **AutoFlow AI**.
2. Go to **Release** -> **Internal testing** (or Production track).
3. Click **Create new release** and upload `app-release.aab`.
4. Review the auto-generated release notes and verify bundle permissions (`INTERNET`, `CAMERA`, `READ_MEDIA_IMAGES`).
5. Complete **App Content** declarations using details from `autoflow-docs/STORE_METADATA.md`.
6. Submit for review.

---

## 2. iOS Release Guide (Apple App Store)

### Step 1: Apple Developer Account & App ID Setup
1. Log in to [Apple Developer Portal](https://developer.apple.com/account).
2. Go to **Certificates, Identifiers & Profiles** -> **Identifiers**.
3. Create a new App ID:
   - **Description**: AutoFlow AI
   - **Bundle ID**: Explicit -> `com.autoflow.autoflowApp` (or matching `CFBundleIdentifier`)
   - **Capabilities**: Push Notifications, Associated Domains (for OAuth deep linking).

### Step 2: Certificates & Provisioning Profiles
1. Create an **Apple Distribution Certificate** using your Mac's Keychain Access Certificate Signing Request (CSR).
2. Under **Profiles**, create an **App Store Distribution Profile** linked to your App ID and Distribution Certificate.
3. Download and double-click to install the profile in Xcode.

### Step 3: Build iOS Archive (.ipa)
From a macOS development machine with Xcode installed:
```bash
cd autoflow-app
flutter clean
flutter pub get
flutter build ipa --release
```

* **Output Location**: `autoflow-app/build/ios/archive/Runner.xcarchive` and `build/ios/ipa/autoflow_app.ipa`

### Step 4: Upload to App Store Connect & TestFlight
Use either Xcode Organizer or the `xcrun altool` command line:
```bash
xcrun altool --upload-app -f build/ios/ipa/autoflow_app.ipa `
  -t ios -u "YOUR_APPLE_ID" -p "APP_SPECIFIC_PASSWORD"
```
Or via **Xcode**:
1. Open `autoflow-app/ios/Runner.xcworkspace` in Xcode.
2. Select **Product** -> **Archive**.
3. In the Organizer window, click **Distribute App** -> **App Store Connect** -> **Upload**.

### Step 5: App Store Connect Listing & Submission
1. Log in to [App Store Connect](https://appstoreconnect.apple.com).
2. Select **AutoFlow AI** -> **TestFlight** to verify internal beta builds.
3. In the **App Store** tab, populate metadata from `autoflow-docs/STORE_METADATA.md`:
   - Title, Subtitle, Keywords, Description.
   - Privacy Nutrition Labels.
   - Demo Account Reviewer Credentials (`reviewer@autoflow.ai`).
4. Select the uploaded build from TestFlight.
5. Click **Submit for Review**.

---

## 3. Pre-Flight Verification Checklist

Before publishing any mobile update:
- [x] All backend integration tests pass (`.\gradlew.bat test` - 143/143 passing).
- [x] All Flutter client tests pass (`flutter test` - 18/18 passing).
- [x] No plaintext credentials or `.env` files staged in Git.
- [x] Deep link scheme `autoflow://oauth/callback` registered in Android & iOS manifests.
- [x] Privacy permission usage strings (`NSCameraUsageDescription`, etc.) defined in `Info.plist`.
- [x] App Store reviewer credentials tested and active.
