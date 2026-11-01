# Tether setup guide

This guide takes you from nothing to two paired phones on **your own** free Firebase project, in about 15 minutes. One of you does steps 1–3 on a computer; both of you do step 4 on your phones.

You'll need:
- A Google account (for Firebase)
- A computer with [Android Studio](https://developer.android.com/studio) (free)
- Two Android phones (Android 8.0 or newer)

---

## 1. Create your Firebase project (free Spark plan)

Firebase is where your encrypted data lives. You own the project and the data, and you can delete it any time.

1. Go to <https://console.firebase.google.com> and click **Create a project**.
2. Name it (for example `tether-ourname`). You can **turn off Google Analytics**, since it isn't needed.
3. When it's ready, check that the plan shown at the bottom-left is **Spark (no-cost)**. Don't upgrade. Tether needs no paid features.

### 1a. Turn on anonymous sign-in
1. **Build → Authentication → Get started**.
2. On the **Sign-in method** tab, click **Anonymous**, switch **Enable** on, and click **Save**.

> Tether never asks for an email or phone number. Each phone gets an anonymous ID, and the database rules only let your two IDs in.

### 1b. Create the Realtime Database
1. **Build → Realtime Database → Create Database**.
2. Pick the location closest to you (for the Caribbean and the Americas, `us-central1` is fine).
3. Choose **Start in locked mode** and click **Enable**.

### 1c. Publish the security rules
1. In Realtime Database, open the **Rules** tab.
2. Delete everything there and paste the full contents of [`database.rules.json`](../database.rules.json) from this repository.
3. Click **Publish**.

These rules:
- lock each couple's "circle" to exactly two anonymous accounts
- block everyone else from reading or writing anything
- allow restoring a pairing from a backup only with the secret token stored in that backup

> If you update Tether later and the release notes mention new rules, repeat this step.

### 1d. Register the Android app and download its config
1. Click the ⚙️ gear → **Project settings**. Under **Your apps**, click the **Android** icon.
2. **Android package name:** `com.shane.tether`.
   - Want your own package name? Change `applicationId` in `app/build.gradle.kts` first, and use the same value here. (Leave `namespace` as it is.)
3. App nickname is optional. **Skip the SHA-1**, because anonymous auth doesn't need it.
4. Click **Register app**, then **Download google-services.json**.
5. Skip the remaining wizard steps (the SDK is already set up in the code).

> ⚠️ Do step 1b **before** downloading `google-services.json`, so the file includes your database URL. If you created the database afterwards, just download the file again.

---

## 2. Get the code and add your config

```bash
git clone https://github.com/shenvecthor/tether.git
```

Copy your `google-services.json` into the **`app/`** folder (next to `app/build.gradle.kts`).

> `google-services.json` is git-ignored. It identifies *your* Firebase project, so keep it out of public repos.

---

## 3. Build the APK

1. Open Android Studio → **File → Open** → select the `tether` folder (the one containing `settings.gradle.kts`, not a parent folder).
2. Click **Trust Project** and wait for **Gradle sync** to finish (the first time downloads a few hundred MB).
   - If Studio offers to upgrade the Android Gradle Plugin, you can decline.
   - If sync complains about the JDK: **Settings → Build, Execution, Deployment → Build Tools → Gradle → Gradle JDK** → choose the bundled **jbr-21**.
3. **Build → Build App Bundle(s) / APK(s) → Build APK(s)**.
4. The APK is at `app/build/outputs/apk/debug/app-debug.apk`. Rename it to `Tether.apk` if you like.

### 3a. (Recommended) Use your own signing key
Without a key, the build uses Android Studio's default debug key. That works, but that key is tied to the computer it was built on. If you build an update on another computer, phones will refuse to install it over the old version.

To sign every build with one stable key:

```bash
cd app
keytool -genkeypair -v -keystore tether-release.jks -alias tether -keyalg RSA -keysize 3072 -validity 10000
cp keystore.properties.example keystore.properties   # then edit the passwords
```

`keytool` comes with Android Studio's JDK (`<Android Studio>/jbr/bin/keytool`). Both files are git-ignored. **Back them up somewhere private.** If you lose the key, you'll have to uninstall and re-pair to update.

---

## 4. Install and pair

On **each** phone:
1. Send yourself the APK (WhatsApp, Google Drive, USB…) and open it. Allow **Install unknown apps** when Android asks.
2. Enter your name and phone number (used for one-tap calling).
3. Grant the permissions:
   - **Location → Allow all the time** (Android shows this on a separate settings page)
   - **Notifications**
   - **Battery → Unrestricted**

Then pair:
- Phone 1: **Show my pairing code**.
- Phone 2: **Scan my partner's code**.

That's it 💞. The QR code holds your shared encryption key, so only show it to your partner.

> Just want to look around first? Tap **Skip for now**. Preview mode shows a demo partner, and you can pair later from **Settings**.

---

## 5. Keep it running in the background

Android phone makers aggressively kill background apps. If your partner's location says "Updated 2 h ago", this is almost always why.

| Phone | What to do |
|---|---|
| **Samsung** | Settings → Battery → Background usage limits → **Never sleeping apps** → add Tether |
| **Xiaomi / Redmi / POCO** | App info → **Autostart: on**; Battery saver → **No restrictions** |
| **Tecno / Infinix / itel** | Phone Master → App launch / Auto-start → allow Tether; Battery → app → **Unrestricted** |
| **Oppo / Realme / OnePlus** | App info → Battery → **Allow background activity**; also allow Auto-launch |
| **Huawei** | Battery → App launch → Tether → **Manage manually**, enable all three |
| **Pixel / others** | App info → Battery → **Unrestricted** |

For your exact model, see <https://dontkillmyapp.com>.

---

## 6. Updating

1. Pull the latest code (`git pull`) and build again.
2. Install the new APK over the old one. **No need to uninstall or re-pair**, as long as you use the same signing key (step 3a).
3. If the release notes mention new database rules, re-publish them (step 1c).

---

## Free plan limits

Tether runs entirely on Firebase's **Spark (no-cost)** plan. At the time of writing, the Realtime Database allows roughly **1 GB stored**, **10 GB downloaded per month** and **100 simultaneous connections**. Check the current numbers at <https://firebase.google.com/pricing>.

What two phones actually use:
- **Storage:** history is pruned to 30 days. Location points, messages and small photo thumbnails come to a few MB per month.
- **Downloads:** mostly live updates while the app is open, typically well under 1 GB per month.
- **Connections:** 2.

No Cloud Functions, Storage buckets or paid APIs are used.

---

## Backups

**Settings → Backup & restore → Back up 💾** saves your profile, theme, settings, saved places and special dates to a file.
- Add a **password** to encrypt it.
- Turn on **Include pairing key** (password required) to restore after a reinstall **without re-pairing**.

Keep backup files private: with the password, they can unlock your shared data.

---

## Privacy notes

- All personal data is encrypted on the phone (AES-256-GCM) before upload. The project owner sees only ciphertext, plus metadata like *when* something changed.
- The encryption key never touches the server. It lives in each phone's Android Keystore and inside the pairing QR code.
- To delete everything: in Firebase, **Realtime Database → Data**, delete the `circles` node, or delete the whole project.
- Some features call free public services directly from the phone:
  - map tiles (OpenFreeMap, CARTO, Esri)
  - road snapping (OSRM)
  - holidays (Wikipedia, Nager.Date)
  - address lookup (Android's built-in geocoder)

  OSRM receives the coordinates of finished drives so it can match them to roads. If you'd rather it didn't, you can point `Snap.kt` at your own OSRM server or remove the snapping call.

---

## Troubleshooting

| Problem | Fix |
|---|---|
| Build is greyed out / everything is red in the manifest | Gradle sync hasn't finished or failed. Check you opened the folder that contains `settings.gradle.kts`. |
| Build error mentioning `google-services.json` | The file is missing from `app/`, or its package name doesn't match `applicationId`. |
| "Couldn't reach the server" when pairing | Anonymous sign-in not enabled, rules not published, or no internet. |
| "This code has already been used" | Make a fresh QR code on the first phone and scan again. |
| Partner shows "Updated 2 h ago" | Battery optimisation. See [section 5](#5-keep-it-running-in-the-background). |
| Restore says the server refused the pairing | Re-publish the latest `database.rules.json`. |
| App crashed | Reopen it and tap **Copy report**, then share the report with whoever builds your copy, or open a GitHub issue. |
