# Device Info APK

Diagnostic Android app that dumps everything readable about the device — what Instagram/TikTok-class apps can fingerprint on you without any special privileges.

## What it collects
- `Build.*` and `Build.VERSION.*` — manufacturer, brand, model, device, board, hardware, bootloader, fingerprint, security patch, SOC info, supported ABIs, etc.
- `ro.*` system properties via reflection (`android.os.SystemProperties`) — including `ro.boot.verifiedbootstate`, `ro.boot.flash.locked`, `ro.kernel.qemu`, `ro.product.*`, vendor overlays, etc.
- CPU info from `/proc/cpuinfo`, kernel from `/proc/version`, boot_id from `/proc/sys/kernel/random/boot_id`
- RAM and storage stats
- Display metrics (resolution, density, refresh rate, supported modes)
- Full sensor list
- All camera characteristics
- Telephony (operator, MCC/MNC, SIM, country ISO, network type) — without `READ_PHONE_STATE`
- Locale, timezone, system languages
- Network interfaces (with MACs where readable), DNS, WiFi info (SSID/BSSID/RSSI — visible to the app even without location grant on the fingerprinting layer it cares about)
- Battery state
- Bluetooth adapter
- **Widevine `deviceUniqueId`** — one of the most stable trackable IDs
- Installed package count, oldest install time, system features
- `ANDROID_ID`, serial number
- Root-detection signals (su binaries, magisk paths, test-keys)
- Emulator heuristics (fingerprint, hardware=goldfish/ranchu, qemu props)
- SELinux state, verified-boot state, flash-lock state, debuggable flag, ADB enabled, dev settings enabled
- Uptime / elapsedRealtime

## Build via GitHub Actions
1. Create an empty (private) GitHub repo
2. Push this project to it:
   ```bash
   cd device-info-apk
   git init
   git add .
   git commit -m "Initial commit"
   git branch -M main
   git remote add origin git@github.com:YOUR_USER/YOUR_REPO.git
   git push -u origin main
   ```
3. Open the repo on GitHub → **Actions** tab → wait ~3-5 min for the `Build Debug APK` workflow
4. Open the green run → scroll to **Artifacts** → download `device-info-debug-apk.zip`
5. Unzip → install the `.apk` on the device (allow install from unknown sources)

## Use
Launch the app — it shows everything immediately. Tap **Copy all** to copy the full dump to clipboard, or **Share** to send it via Telegram / email / file.

## Why no runtime permission prompts?
The goal is to show exactly what an app sees **before** you grant anything — that's the surface that matters for whether your masking is convincing. Adding location/phone-state grants would only add `getImei()` and finer WiFi BSSID — useful, but separately, since most fingerprinting happens pre-grant.
