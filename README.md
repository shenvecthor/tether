# 💞 Tether

**A private, end-to-end encrypted location-sharing app for two people — built for couples who want Life360-style features without handing their lives to a company.**

You host it yourselves on your own free Firebase project. Every location, message, photo and love note is encrypted on the phone before it leaves, with a key that only your two phones hold. The server only ever stores scrambled data.

> Android only · Kotlin + Jetpack Compose · OpenStreetMap (no Google Maps key) · Firebase free "Spark" plan

👉 **New here? Follow the [setup guide](docs/SETUP.md)** — about 15 minutes from zero to two paired phones.

---

## Contents

- [Why Tether](#why-tether)
- [Features](#features)
  - [Live map](#-live-map)
  - [Couple features — the "Us" tab](#-couple-features--the-us-tab)
  - [Places & alerts](#-places--alerts)
  - [History, trips & driving](#-history-trips--driving)
  - [Safety](#-safety)
  - [Chat & activity](#-chat--activity)
  - [Look & feel](#-look--feel)
  - [Privacy & security](#-privacy--security)
  - [Backup & restore](#-backup--restore)
  - [Reliability](#-reliability)
- [How it works](#how-it-works)
- [Tech stack](#tech-stack)
- [Project structure](#project-structure)
- [Data sources & credits](#data-sources--credits)
- [FAQ](#faq)

---

## Why Tether

| | Life360-style apps | Tether |
|---|---|---|
| Who hosts your data | The company | **You** (your own Firebase project) |
| Who can read it | The company and its partners | **Only your two phones** (end-to-end encrypted) |
| Cost | Subscription for most features | **Free** (Firebase Spark plan) |
| Ads / data selling | Common | **None** |
| Couple features | Basic | Love taps, moods, daily questions, anniversaries, recaps… |

---

## Features

### 🗺️ Live map
- **Both of you on one map** with round avatar pins showing your **profile photo** (or initial) and **current mood**, each with a softly pulsing halo.
- **Heart link** — a dashed line connects you with a beating 💗 in the middle.
- **Distance pill** — a glassy pill with both avatars, a beating heart and the distance in gradient text ("3.2 km apart" / "Together · right now").
- **Instant refresh** — opening the map (or tapping Refresh) pings your partner's phone, which switches to 5-second high-accuracy updates for 3 minutes.
- **Smooth movement** — pins glide to new positions instead of jumping.
- **Map styles** — 🗺️ Standard, 🛰️ Satellite and ⛰️ Terrain, plus an automatic dark map in dark mode.
- **Partner card** — name, photo, mood, live status (driving speed / at a place / address), "updated x min ago", accuracy, battery level & charging, plus one-tap **Refresh, Directions, Call** and **Send a heart**.
- **Comfortable framing** — auto-zoom never goes closer than a neighbourhood view.
- **Offline indicator** — "☁️ Offline · 3 updates waiting" or "Syncing…" until everything reaches the server.

### 💞 Couple features — the "Us" tab
- **Greeting card** — "Good evening, Name 🌆" with live date & time and little tidbits: today's national day, date-night countdown, milestones, partner's mood, distance apart, weekday nudges, and a sweet line of the day.
- **Days together counter** with the next milestone ("💯 300 days in 12 days", "🎂 1-year anniversary in 40 days").
- **Anniversary celebrations** — on 100-day marks, yearly anniversaries and monthly anniversaries (first 2 years), the app opens with **confetti** and a **look-back card**: first shared location, total km travelled, love taps and messages sent. (Tap the big days number to preview it any time.)
- **Love taps** — ❤️ heart, 😘 kiss, 🤗 hug, 🥺 miss you, 💭 thinking of you, 🌹 rose, 🥰 love you, 😉 wink. Sending one makes **hearts float up** your screen; your partner gets floating hearts + a banner if the app is open, or a notification with a **heartbeat vibration**.
- **Moods** — 24 moods (8 up front, more behind "More moods"); tap again to clear. Your mood shows on your map pin and partner card.
- **Question of the day** — one shared question daily (Paired-style). Your partner's answer stays 🔒 locked until you answer too. Rolls over at midnight.
- **Next date countdown** — "Dinner at the seawall 🌅 · in 3 days".
- **Weekly recap** — time together (when your phones were within 150 m), km travelled, places visited, love taps, drives, top speed and your favourite spot. Retitled "Your week together 🎁" on Sundays.
- **Celebrate today** — every **National Day** (1,931 from National Day Calendar, bundled & offline), worldwide **holidays & observances** (Wikipedia, collapsible), and **Guyana public holidays** with a countdown — each with a 💡 *celebrate together* idea. Browse any day of the year.
- **Love notes feed** — your recent taps and check-ins.
- **Profile photos** — tap your circle to add a photo; it appears on your map pin, the Us card, the partner card and chat.

### 📍 Places & alerts
- **Unlimited places** (Home, Work, School, Gym…) with a custom radius (50–500 m). Add one where you stand, or **long-press anywhere on the map**.
- **Editable** — rename, resize, or move to your current location.
- **Arrive / leave alerts** for both of you, with hysteresis so GPS jitter doesn't cause false alerts, and a soft "ding-dong" chime.
- See who's at each place right now and how far you are from it.

### 🚗 History, trips & driving
- **30 days of history** for each of you.
- **Timeline sheet** — swipe down to see the full map, swipe up for details. Newest first, grouped by 🌅 Morning / ☀️ Afternoon / 🌆 Evening / 🌙 Night.
  - **Drives** with time range, distance, average and top speed.
  - **Stops** (10+ minutes between two drives) in amber, with duration and street name.
  - **Stays** with your saved place name ("At Home") or address.
  - Tap any item to show just that part on the map.
- **Speed-coloured routes** — purple < 15, green 15–40, amber 40–70, red 70+ km/h (with legend).
- **Road-snapped paths** — finished drives are cleaned (inaccurate fixes, spikes and jitter removed), **map-matched to real roads** via OSRM, fall back to **road routing between waypoints** when matching is weak, then **post-corrected** to remove side-street spurs. Cached per trip.
- **Stop markers** ("☕ 25m") plus green start / red end dots; labels lift above a person's pin so they're never hidden.
- **Summary** — distance travelled, driving time, top speed and number of stops.
- **Live driving** — when your partner is driving you see their drive so far in speed colours, a pulsing **LIVE · 54 km/h** badge and a **Follow** mode that keeps the camera on them. Their phone stays in fast-update mode while you watch.
- **Heatmap** — where time was spent over 7 / 14 / 30 days (you, your partner or both), shown as 🟢 / 🟠 / 🔴 zones, with a "Hotspots" breakdown per saved place.

### 🛟 Safety
- **SOS** — a loud, insistent alarm notification on your partner's phone with your exact location.
- **Crash detection** — while driving, a hard impact (4 g+) starts a 30-second full-screen "I'm OK" countdown, then alerts your partner with your location.
- **Low-battery alert** at 15%, and **battery-dying last location** at 10% — a fresh, precise fix is sent before the phone dies. Also tries to send a final location when the phone switches off.
- **Check-in** — "I'm safe ✅" in one tap.
- **Pause sharing** — your partner sees "paused" instead of your location.
- **Stale indicator** — "Updated 2 h ago" turns red if a phone stops reporting.

### 💬 Chat & activity
- Encrypted chat with gradient bubbles, small **profile photos**, and quick replies ("Love you ❤️", "On my way 🚗", "Goodnight 🌙"…).
- **Activity feed** — arrivals, departures, check-ins, battery alerts, SOS and love taps; tap one to open its location.

### 🎨 Look & feel
- **9 themes** — 6 pinks (Blush, Bubblegum, Sakura, Rose Gold, Peachy Keen, Berry Kiss) and 3 blues (Ocean, Sky, Midnight), each with its own gradient. Quick pink ↔ blue toggle on the map.
- **Dark mode** — System / Light / Dark, including dark maps and status bar handling.
- **Animations** — floating hearts, confetti, beating hearts, bouncy buttons, gliding pins, pulsing halos, smooth tab and theme transitions.
- **Soft chimes** — original generated sounds for sending love, receiving love and arrivals (toggle in Settings).
- **Seasonal decorations** (optional, off by default) — Halloween, Christmas, New Year, Valentine's, Mashramani, Arrival, Independence and Emancipation Day, plus moveable holidays (Diwali, Phagwah, Eid, Easter) from Guyana's official holiday list.
- **Icons** throughout for stats (travelled, driving, top speed, stops…).

### 🔒 Privacy & security
- **End-to-end encryption** — AES-256-GCM. The 256-bit pair key is created on one phone and travels **only inside the pairing QR code**. Firebase stores ciphertext only.
- **Key protected at rest** with the hardware-backed Android Keystore.
- **Locked-down database rules** — each "circle" admits exactly two anonymous accounts (slots `a` and `b`); nobody else can read or write it.
- **No accounts, emails or phone numbers** required — Firebase anonymous auth.
- **Your infrastructure** — your Firebase project, your data, delete it whenever you want.
- `allowBackup="false"` — Android cloud backup can't copy the key off the phone.

### 💾 Backup & restore
- Save profile, theme, settings, **all saved places** and **anniversary / next date** to a file.
- **Optional password** — encrypts the backup (PBKDF2-SHA256 → AES-256-GCM).
- **Include pairing key** (password required) — reinstall and restore **without re-pairing**. A secret per-phone token proves to the database that the slot is yours.
- Restore skips duplicate places and only fills blank dates. Restoring before pairing queues places until you pair.

### ⚙️ Reliability
- Foreground service with adaptive accuracy: every 60 s normally, 15 s while driving, 5 s in live mode.
- Restarts after reboot and app updates.
- Offline writes are queued and replayed automatically.
- **Built-in crash reporter** — after a crash, the next launch offers a **Copy report** button with the exact error and device details.
- **Preview mode** — "Skip for now" lets you explore the whole UI with a demo partner before pairing.

---

## How it works

```
 Phone A ──(AES-256-GCM)──▶  Firebase Realtime DB  ◀──(AES-256-GCM)── Phone B
   │                        circles/{id}/...                           │
   └─ key from QR code ─────── (ciphertext only) ─────── key from QR ──┘
```

- **Pairing** — one phone shows a QR code containing a random circle ID and a 256-bit key; the other scans it. Database rules lock the circle to those two anonymous accounts.
- **Background** — a foreground service (the small "Tether is sharing" notification) uses Google's fused location provider and adapts its rate to what you're doing.
- **Alerts without a server** — each phone listens to the shared database and raises its own notifications. No Cloud Functions, so the free Spark plan is enough.
- **Trips** — a day of points is split into *stays* (within ~120 m for 10+ min) and *trips* between them; a trip is a drive if it went fast enough.

Database layout (every value except `members`/`tokens`/`proof`/`ping` is ciphertext):

```
circles/{circleId}/
  members/{a|b}            Firebase uid (used by the security rules)
  profiles/{slot}          name, phone, mood
  live/{slot}              latest location, battery, driving state
  history/{slot}/{day}/…   location points (30 days kept)
  places/{id}              saved places
  events/…                 arrivals, SOS, love taps, check-ins…
  messages/…               chat
  couple                   anniversary, next date
  daily/{day}/{slot}       question-of-the-day answers
  avatars/{slot}           profile photo thumbnail
  stats/{slot}             lifetime km / taps / messages
  ping/{slot}              "send me a fresh location"
  tokens/{slot}, proof/…   lets a restored backup re-claim its slot
```

---

## Tech stack

- **Kotlin**, **Jetpack Compose**, Material 3
- **MapLibre Android** + OpenFreeMap / CARTO / Esri tiles (no API keys)
- **Firebase** Realtime Database + Anonymous Auth (Spark plan)
- **Google Play services location** (fused provider)
- **ZXing** for QR codes
- **OSRM** for road snapping
- `javax.crypto` + Android Keystore for encryption

Min Android 8.0 (API 26), target API 35.

---

## Project structure

```
app/src/main/java/com/shane/tether/
  TetherApp.kt        app start-up (channels, theme, sounds, crash log)
  MainActivity.kt     onboarding → pairing → home
  LocationService.kt  background sharing, places, pings, crash detection, alerts
  Cloud.kt            Firebase data layer (encrypt/decrypt, flows, sync status)
  Crypto.kt           AES-GCM + Android Keystore
  Prefs.kt            local settings
  Trips.kt            stay/trip/drive detection
  Snap.kt             road snapping + post-correction (OSRM)
  Backup.kt           encrypted backup & restore
  Holidays.kt         Wikipedia observances + Guyana holidays
  NationalDays.kt     bundled National Day Calendar list
  Avatars.kt          photo thumbnails
  Sounds.kt, Notify.kt, CrashLog.kt, Love.kt, Models.kt, Perms.kt, BootReceiver.kt
  ui/                 Compose screens: Home (map, settings), Us, Tabs (places, history, chat,
                      activity), TetherMap, themes, effects, celebrations, recap, seasonal…
app/src/main/assets/national_days.json   1,931 national days (offline)
app/src/main/res/raw/                    generated chime sounds
database.rules.json                      Firebase security rules
docs/SETUP.md                            step-by-step setup guide
```

---

## Data sources & credits

- Map data © [OpenStreetMap](https://www.openstreetmap.org/copyright) contributors; tiles by [OpenFreeMap](https://openfreemap.org), [CARTO](https://carto.com), and [Esri](https://www.esri.com) (satellite & terrain).
- Road matching: [OSRM](https://project-osrm.org) public demo server (fair use — please be gentle).
- National days: [National Day Calendar](https://nationaldaycalendar.com) (bundled snapshot).
- Holidays & observances: [Wikipedia](https://www.wikipedia.org) "On this day" feed.
- Guyana public holidays: [Nager.Date](https://date.nager.at).
- Chime sounds are original, generated for this project.

---

## FAQ

**Is it really free?** Yes, for two people. The Firebase Spark plan's limits are far above what two phones use. See the [setup guide](docs/SETUP.md#free-plan-limits).

**Can more than two people join?** No — Tether is deliberately built for exactly two.

**iPhone?** Not currently; Android only.

**Can whoever runs the Firebase project read our locations?** No. They can see that data exists and roughly when it changes, but not its contents — everything is encrypted with a key that never leaves your phones.

**Location stopped updating — why?** Almost always battery optimisation. See [Keeping it running](docs/SETUP.md#5-keep-it-running-in-the-background).

**We reinstalled and lost pairing.** Pair again with a new QR code, or restore a backup made with "Include pairing key".
