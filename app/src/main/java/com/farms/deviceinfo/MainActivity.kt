package com.farms.deviceinfo

import android.app.ActivityManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorManager
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.MediaDrm
import android.net.ConnectivityManager
import android.net.wifi.WifiManager
import android.os.BatteryManager
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.StatFs
import android.os.SystemClock
import android.provider.Settings
import android.telephony.TelephonyManager
import android.util.DisplayMetrics
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import java.io.File
import java.net.NetworkInterface
import java.util.Locale
import java.util.TimeZone
import java.util.UUID

class MainActivity : AppCompatActivity() {

    private val sb = StringBuilder()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val tv = findViewById<TextView>(R.id.tvInfo)
        val btnCopy = findViewById<Button>(R.id.btnCopy)
        val btnShare = findViewById<Button>(R.id.btnShare)

        collectAll()
        val text = sb.toString()
        tv.text = text

        btnCopy.setOnClickListener {
            val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            cm.setPrimaryClip(ClipData.newPlainText("device-info", text))
            Toast.makeText(this, "Copied", Toast.LENGTH_SHORT).show()
        }
        btnShare.setOnClickListener {
            val i = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, text)
            }
            startActivity(Intent.createChooser(i, "Share device info"))
        }
    }

    private fun collectAll() {
        section("BUILD") {
            kv("MANUFACTURER", Build.MANUFACTURER)
            kv("BRAND", Build.BRAND)
            kv("MODEL", Build.MODEL)
            kv("DEVICE", Build.DEVICE)
            kv("PRODUCT", Build.PRODUCT)
            kv("BOARD", Build.BOARD)
            kv("HARDWARE", Build.HARDWARE)
            kv("BOOTLOADER", Build.BOOTLOADER)
            kv("HOST", Build.HOST)
            kv("USER", Build.USER)
            kv("ID", Build.ID)
            kv("DISPLAY", Build.DISPLAY)
            kv("TAGS", Build.TAGS)
            kv("TYPE", Build.TYPE)
            kv("FINGERPRINT", Build.FINGERPRINT)
            kv("SUPPORTED_ABIS", Build.SUPPORTED_ABIS.joinToString())
            kv("SUPPORTED_32_BIT_ABIS", Build.SUPPORTED_32_BIT_ABIS.joinToString())
            kv("SUPPORTED_64_BIT_ABIS", Build.SUPPORTED_64_BIT_ABIS.joinToString())
            kv("RADIO_VERSION", safe { Build.getRadioVersion() })
            kv("TIME (build epoch ms)", Build.TIME.toString())
            if (Build.VERSION.SDK_INT >= 31) {
                kv("SOC_MANUFACTURER", Build.SOC_MANUFACTURER)
                kv("SOC_MODEL", Build.SOC_MODEL)
                kv("SKU", Build.SKU)
                kv("ODM_SKU", Build.ODM_SKU)
            }
        }

        section("VERSION") {
            kv("RELEASE", Build.VERSION.RELEASE)
            kv("SDK_INT", Build.VERSION.SDK_INT.toString())
            kv("INCREMENTAL", Build.VERSION.INCREMENTAL)
            kv("CODENAME", Build.VERSION.CODENAME)
            kv("BASE_OS", safe { Build.VERSION.BASE_OS })
            kv("SECURITY_PATCH", Build.VERSION.SECURITY_PATCH)
            if (Build.VERSION.SDK_INT >= 30) {
                kv("RELEASE_OR_CODENAME", Build.VERSION.RELEASE_OR_CODENAME)
                kv("MEDIA_PERFORMANCE_CLASS", Build.VERSION.MEDIA_PERFORMANCE_CLASS.toString())
            }
            kv("java.vm.version", System.getProperty("java.vm.version") ?: "")
            kv("os.version", System.getProperty("os.version") ?: "")
            kv("os.arch", System.getProperty("os.arch") ?: "")
        }

        section("SYSTEM PROPERTIES (ro.*)") {
            val keys = listOf(
                "ro.product.manufacturer", "ro.product.model", "ro.product.brand",
                "ro.product.name", "ro.product.device", "ro.product.board",
                "ro.product.cpu.abi", "ro.product.cpu.abilist",
                "ro.build.version.release", "ro.build.version.sdk",
                "ro.build.version.incremental", "ro.build.version.security_patch",
                "ro.build.fingerprint", "ro.build.description", "ro.build.display.id",
                "ro.build.host", "ro.build.user", "ro.build.tags", "ro.build.type",
                "ro.build.date", "ro.build.date.utc", "ro.build.id",
                "ro.bootloader", "ro.boot.bootloader", "ro.boot.serialno", "ro.serialno",
                "ro.boot.hardware", "ro.hardware", "ro.hardware.chipname",
                "ro.boot.verifiedbootstate", "ro.boot.veritymode", "ro.boot.flash.locked",
                "ro.boot.warranty_bit", "ro.warranty_bit",
                "ro.boot.selinux", "ro.kernel.qemu", "ro.kernel.qemu.gles",
                "ro.boot.qemu", "ro.boot.serialconsole",
                "ro.vendor.product.manufacturer", "ro.vendor.product.model",
                "ro.opengles.version",
                "ro.build.characteristics", "ro.build.flavor",
                "ro.miui.ui.version.name", "ro.build.version.emui", "ro.build.version.opporom",
                "ro.vivo.os.version", "ro.build.version.oneui",
                "ro.crypto.state", "ro.crypto.type",
                "persist.sys.timezone", "persist.sys.locale"
            )
            for (k in keys) {
                val v = sysProp(k)
                if (v.isNotEmpty()) kv(k, v)
            }
        }

        section("CPU / HARDWARE") {
            kv("availableProcessors", Runtime.getRuntime().availableProcessors().toString())
            val cpuinfo = readFile("/proc/cpuinfo", maxBytes = 4096)
            // extract a few useful lines
            val picked = cpuinfo.lineSequence()
                .filter {
                    it.startsWith("Hardware") || it.startsWith("Processor") ||
                    it.startsWith("model name") || it.startsWith("Features") ||
                    it.startsWith("CPU implementer") || it.startsWith("CPU architecture") ||
                    it.startsWith("CPU variant") || it.startsWith("CPU part") ||
                    it.startsWith("CPU revision") || it.startsWith("Revision") ||
                    it.startsWith("Serial")
                }
                .distinct()
                .joinToString("\n")
            if (picked.isNotEmpty()) kv("/proc/cpuinfo (filtered)", "\n$picked")
            kv("/proc/version", readFile("/proc/version", maxBytes = 1024).trim())
            kv("/proc/sys/kernel/random/boot_id", readFile("/proc/sys/kernel/random/boot_id", maxBytes = 64).trim())
        }

        section("MEMORY / STORAGE") {
            val am = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            val mi = ActivityManager.MemoryInfo()
            am.getMemoryInfo(mi)
            kv("totalMem (MB)", (mi.totalMem / 1024 / 1024).toString())
            kv("availMem (MB)", (mi.availMem / 1024 / 1024).toString())
            kv("lowMemory", mi.lowMemory.toString())
            kv("threshold (MB)", (mi.threshold / 1024 / 1024).toString())
            val meminfo = readFile("/proc/meminfo", maxBytes = 2048)
            val memPick = meminfo.lineSequence()
                .filter { it.startsWith("MemTotal") || it.startsWith("MemAvailable") || it.startsWith("SwapTotal") }
                .joinToString("\n")
            if (memPick.isNotEmpty()) kv("/proc/meminfo", "\n$memPick")

            val intStat = StatFs(Environment.getDataDirectory().absolutePath)
            kv("internal total (MB)", (intStat.totalBytes / 1024 / 1024).toString())
            kv("internal free (MB)", (intStat.availableBytes / 1024 / 1024).toString())
            try {
                val ext = Environment.getExternalStorageDirectory()
                if (ext != null) {
                    val s = StatFs(ext.absolutePath)
                    kv("external total (MB)", (s.totalBytes / 1024 / 1024).toString())
                    kv("external free (MB)", (s.availableBytes / 1024 / 1024).toString())
                }
            } catch (_: Throwable) {}
        }

        section("DISPLAY") {
            val dm = DisplayMetrics()
            @Suppress("DEPRECATION")
            windowManager.defaultDisplay.getRealMetrics(dm)
            kv("widthPixels", dm.widthPixels.toString())
            kv("heightPixels", dm.heightPixels.toString())
            kv("density", dm.density.toString())
            kv("densityDpi", dm.densityDpi.toString())
            kv("scaledDensity", dm.scaledDensity.toString())
            kv("xdpi", dm.xdpi.toString())
            kv("ydpi", dm.ydpi.toString())
            @Suppress("DEPRECATION")
            val d = windowManager.defaultDisplay
            kv("refreshRate", d.refreshRate.toString())
            kv("supportedModes", d.supportedModes.joinToString { "${it.physicalWidth}x${it.physicalHeight}@${it.refreshRate}" })
            kv("rotation", d.rotation.toString())
            kv("name", d.name)
            kv("displayId", d.displayId.toString())
            val cfg = resources.configuration
            kv("screenLayout", cfg.screenLayout.toString())
            kv("smallestScreenWidthDp", cfg.smallestScreenWidthDp.toString())
            kv("uiMode", cfg.uiMode.toString())
            kv("fontScale", cfg.fontScale.toString())
        }

        section("SENSORS") {
            val sm = getSystemService(Context.SENSOR_SERVICE) as SensorManager
            val list = sm.getSensorList(Sensor.TYPE_ALL)
            kv("count", list.size.toString())
            val unique = list.joinToString("\n") { "  ${it.type}: ${it.name} (${it.vendor})" }
            kv("list", "\n$unique")
        }

        section("CAMERAS") {
            try {
                val cm = getSystemService(Context.CAMERA_SERVICE) as CameraManager
                kv("cameraIdList", cm.cameraIdList.joinToString())
                for (id in cm.cameraIdList) {
                    val cc = cm.getCameraCharacteristics(id)
                    val facing = cc.get(CameraCharacteristics.LENS_FACING)
                    val orient = cc.get(CameraCharacteristics.SENSOR_ORIENTATION)
                    val sens = cc.get(CameraCharacteristics.SENSOR_INFO_PIXEL_ARRAY_SIZE)
                    val fl = cc.get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS)?.joinToString()
                    kv("camera $id", "facing=$facing orient=$orient sensor=$sens focal=$fl")
                }
            } catch (e: Throwable) {
                kv("error", e.javaClass.simpleName + ": " + e.message)
            }
        }

        section("TELEPHONY") {
            try {
                val tm = getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
                kv("networkOperatorName", tm.networkOperatorName ?: "")
                kv("networkOperator (MCCMNC)", tm.networkOperator ?: "")
                kv("simOperatorName", tm.simOperatorName ?: "")
                kv("simOperator (MCCMNC)", tm.simOperator ?: "")
                kv("networkCountryIso", tm.networkCountryIso ?: "")
                kv("simCountryIso", tm.simCountryIso ?: "")
                kv("phoneType", tm.phoneType.toString())
                kv("simState", tm.simState.toString())
                if (Build.VERSION.SDK_INT >= 26) {
                    kv("dataNetworkType", safe { tm.dataNetworkType.toString() })
                }
                if (Build.VERSION.SDK_INT >= 29) {
                    kv("subscriptionId", safe { tm.subscriptionId.toString() })
                }
                if (Build.VERSION.SDK_INT >= 30) {
                    kv("activeModemCount", tm.activeModemCount.toString())
                    kv("supportedModemCount", tm.supportedModemCount.toString())
                }
                kv("isNetworkRoaming", tm.isNetworkRoaming.toString())
            } catch (e: Throwable) {
                kv("error", e.javaClass.simpleName + ": " + e.message)
            }
        }

        section("LOCALE / TIME") {
            kv("Locale.getDefault", Locale.getDefault().toString())
            kv("Locale.country", Locale.getDefault().country)
            kv("Locale.language", Locale.getDefault().language)
            kv("Locale.ISO3Country", safe { Locale.getDefault().isO3Country })
            kv("Locale.ISO3Language", safe { Locale.getDefault().isO3Language })
            val tz = TimeZone.getDefault()
            kv("TimeZone.id", tz.id)
            kv("TimeZone.displayName", tz.displayName)
            kv("TimeZone.rawOffset (ms)", tz.rawOffset.toString())
            kv("currentTimeMillis", System.currentTimeMillis().toString())
            kv("config.locales", resources.configuration.locales.toLanguageTags())
        }

        section("NETWORK") {
            try {
                val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
                val active = cm.activeNetwork
                kv("activeNetwork", active.toString())
                val caps = active?.let { cm.getNetworkCapabilities(it) }
                kv("capabilities", caps?.toString() ?: "")
                val link = active?.let { cm.getLinkProperties(it) }
                kv("interfaceName", link?.interfaceName ?: "")
                kv("domains", link?.domains ?: "")
                kv("DNS servers", link?.dnsServers?.joinToString() ?: "")
                kv("link addresses", link?.linkAddresses?.joinToString() ?: "")
                kv("routes", link?.routes?.joinToString() ?: "")
            } catch (e: Throwable) {
                kv("ConnectivityManager error", e.message ?: "")
            }

            try {
                val ifaces = NetworkInterface.getNetworkInterfaces()
                if (ifaces != null) {
                    for (nif in ifaces.toList()) {
                        val mac = try { nif.hardwareAddress?.joinToString(":") { "%02x".format(it) } } catch (_: Throwable) { null }
                        val addrs = nif.inetAddresses.toList().joinToString { it.hostAddress ?: "" }
                        kv("iface ${nif.name}", "mac=$mac up=${nif.isUp} mtu=${safe { nif.mtu.toString() }} addrs=[$addrs]")
                    }
                }
            } catch (e: Throwable) {
                kv("NetworkInterface error", e.message ?: "")
            }

            try {
                @Suppress("DEPRECATION")
                val wm = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
                @Suppress("DEPRECATION")
                val info = wm.connectionInfo
                kv("WiFi SSID", info?.ssid ?: "")
                kv("WiFi BSSID", info?.bssid ?: "")
                kv("WiFi linkSpeed", (info?.linkSpeed ?: 0).toString())
                kv("WiFi rssi", (info?.rssi ?: 0).toString())
                kv("WiFi frequency", (info?.frequency ?: 0).toString())
                kv("WiFi hiddenSSID", (info?.hiddenSSID ?: false).toString())
                kv("WiFi 5GHz supported", wm.is5GHzBandSupported.toString())
                if (Build.VERSION.SDK_INT >= 30) {
                    kv("WiFi 6GHz supported", safe { wm.is6GHzBandSupported.toString() })
                }
            } catch (e: Throwable) {
                kv("WiFi error", e.message ?: "")
            }
        }

        section("BATTERY") {
            try {
                val bm = getSystemService(Context.BATTERY_SERVICE) as BatteryManager
                kv("level (%)", bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY).toString())
                kv("status", bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_STATUS).toString())
                kv("isCharging", bm.isCharging.toString())
                kv("energyCounter (nWh)", safe { bm.getLongProperty(BatteryManager.BATTERY_PROPERTY_ENERGY_COUNTER).toString() })
                kv("chargeCounter (uAh)", safe { bm.getLongProperty(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER).toString() })
                kv("currentNow (uA)", safe { bm.getLongProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW).toString() })
            } catch (e: Throwable) {
                kv("error", e.message ?: "")
            }
        }

        section("BLUETOOTH") {
            try {
                @Suppress("DEPRECATION")
                val ad = android.bluetooth.BluetoothAdapter.getDefaultAdapter()
                if (ad == null) {
                    kv("adapter", "null (no BT)")
                } else {
                    kv("name", safe { ad.name ?: "" })
                    kv("address", safe { ad.address ?: "" })
                    kv("isEnabled", safe { ad.isEnabled.toString() })
                    kv("state", safe { ad.state.toString() })
                }
            } catch (e: Throwable) {
                kv("error", e.message ?: "")
            }
        }

        section("DRM / WIDEVINE ID") {
            // Widevine device unique ID — heavily used for fingerprinting
            val widevine = UUID(-0x121074568629b532L, -0x5c37d8232ae2de13L)
            try {
                val drm = MediaDrm(widevine)
                val idBytes = drm.getPropertyByteArray(MediaDrm.PROPERTY_DEVICE_UNIQUE_ID)
                kv("widevine deviceUniqueId (hex)", idBytes.joinToString("") { "%02x".format(it) })
                kv("widevine vendor", safe { drm.getPropertyString(MediaDrm.PROPERTY_VENDOR) })
                kv("widevine version", safe { drm.getPropertyString(MediaDrm.PROPERTY_VERSION) })
                kv("widevine description", safe { drm.getPropertyString(MediaDrm.PROPERTY_DESCRIPTION) })
                kv("widevine algorithms", safe { drm.getPropertyString(MediaDrm.PROPERTY_ALGORITHMS) })
                kv("widevine securityLevel", safe { drm.getPropertyString("securityLevel") })
                kv("widevine systemId", safe { drm.getPropertyString("systemId") })
                kv("widevine oemCryptoApiVersion", safe { drm.getPropertyString("oemCryptoApiVersion") })
                if (Build.VERSION.SDK_INT >= 28) drm.close() else @Suppress("DEPRECATION") drm.release()
            } catch (e: Throwable) {
                kv("error", e.javaClass.simpleName + ": " + e.message)
            }
        }

        section("PACKAGES / FEATURES") {
            try {
                val pm = packageManager
                @Suppress("DEPRECATION")
                val all = pm.getInstalledPackages(0)
                kv("installed packages count", all.size.toString())
                val systemCount = all.count { (it.applicationInfo?.flags ?: 0) and android.content.pm.ApplicationInfo.FLAG_SYSTEM != 0 }
                kv("system packages count", systemCount.toString())
                kv("user packages count", (all.size - systemCount).toString())

                // Sample install times — first install on device (oldest install) can be telling
                val oldest = all.minByOrNull { it.firstInstallTime }
                kv("oldest install (ms)", oldest?.firstInstallTime?.toString() ?: "")
                kv("oldest install package", oldest?.packageName ?: "")

                val feats = pm.systemAvailableFeatures
                kv("system features count", feats.size.toString())
                val keyFeats = feats.mapNotNull { it.name }
                    .filter {
                        it.contains("nfc") || it.contains("telephony") || it.contains("camera") ||
                        it.contains("bluetooth") || it.contains("wifi") || it.contains("sensor") ||
                        it.contains("touchscreen") || it.contains("fingerprint") || it.contains("biometrics")
                    }
                    .sorted()
                    .joinToString("\n  ", prefix = "\n  ")
                kv("key features", keyFeats)
            } catch (e: Throwable) {
                kv("error", e.message ?: "")
            }
        }

        section("IDENTIFIERS") {
            @Suppress("HardwareIds")
            val androidId = Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID)
            kv("ANDROID_ID", androidId ?: "")
            kv("Build.SERIAL", safe { @Suppress("DEPRECATION") Build.SERIAL })
            if (Build.VERSION.SDK_INT >= 26) {
                kv("Build.getSerial() (requires READ_PHONE_STATE)", safe { Build.getSerial() })
            }
            kv("packageName (self)", packageName)
            kv("installer (self)", safe {
                if (Build.VERSION.SDK_INT >= 30) packageManager.getInstallSourceInfo(packageName).installingPackageName ?: ""
                else @Suppress("DEPRECATION") packageManager.getInstallerPackageName(packageName) ?: ""
            })
        }

        section("ROOT / EMULATOR DETECTION") {
            val suPaths = listOf(
                "/system/app/Superuser.apk", "/sbin/su", "/system/bin/su", "/system/xbin/su",
                "/data/local/xbin/su", "/data/local/bin/su", "/system/sd/xbin/su",
                "/system/bin/failsafe/su", "/data/local/su", "/su/bin/su",
                "/system/etc/init.d/99SuperSUDaemon", "/dev/com.koushikdutta.superuser.daemon/"
            )
            val magiskPaths = listOf(
                "/sbin/.magisk", "/cache/.disable_magisk", "/dev/.magisk.unblock",
                "/cache/magisk.log", "/data/adb/magisk", "/data/adb/modules"
            )
            kv("su files found", suPaths.filter { File(it).exists() }.joinToString().ifEmpty { "none" })
            kv("magisk files found", magiskPaths.filter { File(it).exists() }.joinToString().ifEmpty { "none" })
            kv("TAGS contains test-keys", (Build.TAGS?.contains("test-keys") == true).toString())

            // emulator heuristics
            val emu = buildList {
                if (Build.FINGERPRINT.startsWith("generic")) add("FINGERPRINT=generic")
                if (Build.FINGERPRINT.startsWith("unknown")) add("FINGERPRINT=unknown")
                if (Build.MODEL.contains("google_sdk")) add("MODEL=google_sdk")
                if (Build.MODEL.contains("Emulator")) add("MODEL=Emulator")
                if (Build.MODEL.contains("Android SDK built for x86")) add("MODEL=sdk_x86")
                if (Build.MANUFACTURER.contains("Genymotion")) add("MANUFACTURER=Genymotion")
                if (Build.BRAND.startsWith("generic") && Build.DEVICE.startsWith("generic")) add("BRAND+DEVICE=generic")
                if (Build.PRODUCT == "google_sdk") add("PRODUCT=google_sdk")
                if (Build.HARDWARE.contains("goldfish")) add("HARDWARE=goldfish")
                if (Build.HARDWARE.contains("ranchu")) add("HARDWARE=ranchu")
                if (sysProp("ro.kernel.qemu") == "1") add("ro.kernel.qemu=1")
                if (sysProp("ro.boot.qemu") == "1") add("ro.boot.qemu=1")
                if (sysProp("ro.hardware").contains("goldfish")) add("ro.hardware=goldfish")
            }
            kv("emulator hits", emu.joinToString().ifEmpty { "none" })
        }

        section("SECURITY / SELINUX") {
            kv("selinux enforce (/sys/fs/selinux/enforce)", readFile("/sys/fs/selinux/enforce", maxBytes = 4).trim())
            kv("ro.boot.selinux", sysProp("ro.boot.selinux"))
            kv("ro.boot.verifiedbootstate", sysProp("ro.boot.verifiedbootstate"))
            kv("ro.boot.flash.locked", sysProp("ro.boot.flash.locked"))
            kv("ro.boot.veritymode", sysProp("ro.boot.veritymode"))
            kv("ro.boot.warranty_bit", sysProp("ro.boot.warranty_bit"))
            kv("ro.crypto.state", sysProp("ro.crypto.state"))
            kv("Debuggable build", ((applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0).toString())
            kv("isAdbEnabled", safe {
                Settings.Global.getInt(contentResolver, Settings.Global.ADB_ENABLED, 0).toString()
            })
            kv("developmentSettingsEnabled", safe {
                Settings.Global.getInt(contentResolver, Settings.Global.DEVELOPMENT_SETTINGS_ENABLED, 0).toString()
            })
            kv("installNonMarketApps (legacy)", safe {
                @Suppress("DEPRECATION")
                Settings.Secure.getInt(contentResolver, Settings.Secure.INSTALL_NON_MARKET_APPS, 0).toString()
            })
        }

        section("UPTIME") {
            kv("uptimeMillis (since boot, no sleep)", SystemClock.uptimeMillis().toString())
            kv("elapsedRealtime (since boot, incl sleep)", SystemClock.elapsedRealtime().toString())
            kv("currentThreadTimeMillis", SystemClock.currentThreadTimeMillis().toString())
        }
    }

    // ---- helpers ----

    private inline fun section(title: String, body: () -> Unit) {
        sb.append("\n=== ").append(title).append(" ===\n")
        body()
    }

    private fun kv(k: String, v: String?) {
        sb.append(k).append(": ").append(v ?: "").append('\n')
    }

    private inline fun safe(block: () -> String?): String = try {
        block() ?: ""
    } catch (e: Throwable) {
        "<err: ${e.javaClass.simpleName}: ${e.message}>"
    }

    private fun sysProp(key: String): String {
        return try {
            val cls = Class.forName("android.os.SystemProperties")
            val m = cls.getMethod("get", String::class.java)
            (m.invoke(null, key) as? String) ?: ""
        } catch (_: Throwable) {
            ""
        }
    }

    private fun readFile(path: String, maxBytes: Int = 2048): String {
        return try {
            val f = File(path)
            if (!f.exists() || !f.canRead()) return ""
            f.inputStream().use {
                val buf = ByteArray(maxBytes)
                val n = it.read(buf)
                if (n <= 0) "" else String(buf, 0, n)
            }
        } catch (_: Throwable) {
            ""
        }
    }
}
