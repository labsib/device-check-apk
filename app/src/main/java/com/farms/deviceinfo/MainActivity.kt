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
import android.net.Uri
import android.net.wifi.WifiManager
import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.GLES20
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
import com.google.android.gms.ads.identifier.AdvertisingIdClient
import java.io.File
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.Socket
import java.util.Locale
import java.util.TimeZone
import java.util.UUID

class MainActivity : AppCompatActivity() {

    private val sb = StringBuilder()
    private lateinit var tv: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        tv = findViewById(R.id.tvInfo)
        val btnCopy = findViewById<Button>(R.id.btnCopy)
        val btnShare = findViewById<Button>(R.id.btnShare)

        collectAll()
        tv.text = sb.toString()

        btnCopy.setOnClickListener {
            val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            cm.setPrimaryClip(ClipData.newPlainText("device-info", tv.text))
            Toast.makeText(this, "Copied", Toast.LENGTH_SHORT).show()
        }
        btnShare.setOnClickListener {
            val i = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, tv.text)
            }
            startActivity(Intent.createChooser(i, "Share device info"))
        }

        // async pieces: GAID + Frida socket scan
        Thread { collectAsync() }.start()
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
            kv("RADIO_VERSION (baseband)", safe { Build.getRadioVersion() })
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
                "ro.board.platform", "ro.boot.boardid", "ro.boot.hardware.platform",
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
                "persist.sys.timezone", "persist.sys.locale",
                "gsm.version.baseband", "gsm.sim.operator.numeric", "gsm.sim.operator.alpha",
                "gsm.operator.numeric", "gsm.operator.alpha", "gsm.operator.iso-country"
            )
            for (k in keys) {
                val v = sysProp(k)
                if (v.isNotEmpty()) kv(k, v)
            }
        }

        section("CPU / HARDWARE") {
            kv("availableProcessors", Runtime.getRuntime().availableProcessors().toString())
            val cpuinfo = readFile("/proc/cpuinfo", maxBytes = 4096)
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

        section("GPU / OPENGL") {
            try {
                val gl = collectGlInfo()
                kv("GL_RENDERER", gl["renderer"] ?: "")
                kv("GL_VENDOR", gl["vendor"] ?: "")
                kv("GL_VERSION", gl["version"] ?: "")
                kv("GL_SHADING_LANGUAGE_VERSION", gl["shading"] ?: "")
                kv("GL_EXTENSIONS (count)", gl["ext_count"] ?: "")
                kv("GL_EXTENSIONS (first 600 chars)", (gl["extensions"] ?: "").take(600))
                kv("EGL_VENDOR", gl["egl_vendor"] ?: "")
                kv("EGL_VERSION", gl["egl_version"] ?: "")
                kv("EGL_CLIENT_APIS", gl["egl_apis"] ?: "")
            } catch (e: Throwable) {
                kv("error", e.javaClass.simpleName + ": " + e.message)
            }
            kv("reqGlEsVersion (ConfigurationInfo)", safe {
                val am = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
                "0x" + java.lang.Integer.toHexString(am.deviceConfigurationInfo.reqGlEsVersion)
            })
        }

        section("SENSORS") {
            val sm = getSystemService(Context.SENSOR_SERVICE) as SensorManager
            val list = sm.getSensorList(Sensor.TYPE_ALL)
            kv("count", list.size.toString())
            val unique = list.joinToString("\n") { "  ${it.type}: ${it.name} (${it.vendor})" }
            kv("list", "\n$unique")
        }

        section("SENSORS DETAIL (key sensors)") {
            val sm = getSystemService(Context.SENSOR_SERVICE) as SensorManager
            val keyTypes = listOf(
                Sensor.TYPE_ACCELEROMETER to "accelerometer",
                Sensor.TYPE_GYROSCOPE to "gyroscope",
                Sensor.TYPE_MAGNETIC_FIELD to "magnetometer",
                Sensor.TYPE_GRAVITY to "gravity",
                Sensor.TYPE_LINEAR_ACCELERATION to "linear_accel",
                Sensor.TYPE_ROTATION_VECTOR to "rotation_vector",
                Sensor.TYPE_LIGHT to "light",
                Sensor.TYPE_PROXIMITY to "proximity",
                Sensor.TYPE_PRESSURE to "pressure",
                Sensor.TYPE_AMBIENT_TEMPERATURE to "ambient_temp",
                Sensor.TYPE_RELATIVE_HUMIDITY to "humidity",
                Sensor.TYPE_STEP_COUNTER to "step_counter",
                Sensor.TYPE_STEP_DETECTOR to "step_detector"
            )
            for ((t, label) in keyTypes) {
                val s = sm.getDefaultSensor(t)
                if (s == null) {
                    kv(label, "MISSING")
                } else {
                    kv(label, "name='${s.name}' vendor='${s.vendor}' ver=${s.version} " +
                        "res=${s.resolution} power=${s.power} maxRange=${s.maximumRange} " +
                        "minDelay=${s.minDelay} reportMode=${s.reportingMode}")
                }
            }
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
                kv("networkOperatorName (carrier)", tm.networkOperatorName ?: "")
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
                    kv("activeModemCount (sim slots)", tm.activeModemCount.toString())
                    kv("supportedModemCount (sim slots max)", tm.supportedModemCount.toString())
                } else {
                    kv("phoneCount (legacy sim slots)", safe { @Suppress("DEPRECATION") tm.phoneCount.toString() })
                }
                kv("isNetworkRoaming", tm.isNetworkRoaming.toString())
                kv("IMEI (requires READ_PHONE_STATE)", safe {
                    if (Build.VERSION.SDK_INT >= 26) tm.imei ?: "" else @Suppress("DEPRECATION") tm.deviceId ?: ""
                })
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

            // wlan0 / eth0 MAC explicitly
            kv("wifi.mac (wlan0)", safe {
                NetworkInterface.getByName("wlan0")?.hardwareAddress
                    ?.joinToString(":") { "%02x".format(it) } ?: ""
            })
            kv("eth0.mac", safe {
                NetworkInterface.getByName("eth0")?.hardwareAddress
                    ?.joinToString(":") { "%02x".format(it) } ?: ""
            })

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
                kv("WiFi.macAddress (API)", safe { @Suppress("DEPRECATION") info?.macAddress ?: "" })
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
                    kv("address (BluetoothAdapter.getAddress, API)", safe { ad.address ?: "" })
                    kv("address (Settings.Secure bluetooth_address)", safe {
                        Settings.Secure.getString(contentResolver, "bluetooth_address") ?: ""
                    })
                    kv("isEnabled", safe { ad.isEnabled.toString() })
                    kv("state", safe { ad.state.toString() })
                }
            } catch (e: Throwable) {
                kv("error", e.message ?: "")
            }
        }

        section("DRM / WIDEVINE") {
            val widevine = UUID(-0x121074568629b532L, -0x5c37d8232ae2de13L)
            try {
                val drm = MediaDrm(widevine)
                val idBytes = drm.getPropertyByteArray(MediaDrm.PROPERTY_DEVICE_UNIQUE_ID)
                kv("widevine deviceUniqueId (hex)", idBytes.joinToString("") { "%02x".format(it) })
                kv("widevine vendor", safe { drm.getPropertyString(MediaDrm.PROPERTY_VENDOR) })
                kv("widevine version", safe { drm.getPropertyString(MediaDrm.PROPERTY_VERSION) })
                kv("widevine description", safe { drm.getPropertyString(MediaDrm.PROPERTY_DESCRIPTION) })
                kv("widevine algorithms", safe { drm.getPropertyString(MediaDrm.PROPERTY_ALGORITHMS) })
                kv("widevine securityLevel (L1/L2/L3)", safe { drm.getPropertyString("securityLevel") })
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

        section("INSTALLED APPS DETAIL (user apps)") {
            try {
                val pm = packageManager
                @Suppress("DEPRECATION")
                val all = pm.getInstalledPackages(0)
                val user = all.filter { (it.applicationInfo?.flags ?: 0) and android.content.pm.ApplicationInfo.FLAG_SYSTEM == 0 }
                kv("user apps count", user.size.toString())
                val lines = user
                    .sortedBy { it.firstInstallTime }
                    .joinToString("\n") {
                        "  ${it.firstInstallTime}  ${it.lastUpdateTime}  ${it.packageName} (v${it.versionName})"
                    }
                kv("user apps (firstInstall  lastUpdate  pkg)", "\n$lines")
            } catch (e: Throwable) {
                kv("error", e.message ?: "")
            }
        }

        section("IDENTIFIERS") {
            @Suppress("HardwareIds")
            val androidId = Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID)
            kv("ANDROID_ID", androidId ?: "")
            kv("Build.SERIAL (legacy)", safe { @Suppress("DEPRECATION") Build.SERIAL })
            if (Build.VERSION.SDK_INT >= 26) {
                kv("Build.getSerial() (requires READ_PHONE_STATE)", safe { Build.getSerial() })
            }
            kv("GSF ID (Google Services Framework)", safe { gsfId() ?: "" })
            kv("packageName (self)", packageName)
            kv("installer (self)", safe {
                if (Build.VERSION.SDK_INT >= 30) packageManager.getInstallSourceInfo(packageName).installingPackageName ?: ""
                else @Suppress("DEPRECATION") packageManager.getInstallerPackageName(packageName) ?: ""
            })
        }

        section("ADS ID (GAID)") {
            kv("GAID", "[loading async...]")
            kv("isLimitAdTrackingEnabled", "[loading async...]")
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
                "/cache/magisk.log", "/data/adb/magisk", "/data/adb/modules",
                "/data/adb/magisk.db", "/system/xbin/magisk"
            )
            kv("su files found (root.status)", suPaths.filter { File(it).exists() }.joinToString().ifEmpty { "none" })
            kv("magisk files found (magisk.status)", magiskPaths.filter { File(it).exists() }.joinToString().ifEmpty { "none" })
            kv("TAGS contains test-keys", (Build.TAGS?.contains("test-keys") == true).toString())

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

        section("FRIDA DETECTION") {
            val files = listOf(
                "/data/local/tmp/frida-server",
                "/data/local/tmp/re.frida.server",
                "/data/local/tmp/frida-agent.so",
                "/system/lib/libfrida-gum.so",
                "/system/lib64/libfrida-gum.so"
            )
            kv("frida files found", files.filter { File(it).exists() }.joinToString().ifEmpty { "none" })

            val maps = readFile("/proc/self/maps", maxBytes = 65536)
            val mapsHits = buildList {
                if (maps.contains("frida")) add("frida")
                if (maps.contains("gum-js-loop")) add("gum-js-loop")
                if (maps.contains("gmain")) add("gmain")
                if (maps.contains("linjector")) add("linjector")
            }
            kv("/proc/self/maps hits", mapsHits.joinToString().ifEmpty { "none" })
            kv("frida.status (port scan)", "[loading async...]")
        }

        section("PLAY INTEGRITY / SAFETYNET") {
            kv("note", "Both APIs return a signed token that MUST be verified on a server " +
                "(Google's attestation endpoint). On-device this app cannot tell you the verdict — " +
                "but you can wire up a server to call: Play Integrity API for " +
                "deviceIntegrity / strongIntegrity, and (deprecated) SafetyNet for cts/basicIntegrity.")
            // detect Google Play Services presence (a precondition for both)
            kv("Google Play Services installed", safe {
                try {
                    packageManager.getPackageInfo("com.google.android.gms", 0)
                    "yes"
                } catch (_: PackageManager.NameNotFoundException) {
                    "no"
                }
            })
            kv("GMS version", safe {
                @Suppress("DEPRECATION")
                packageManager.getPackageInfo("com.google.android.gms", 0).versionName ?: ""
            })
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

    // ----------- async section -----------

    private fun collectAsync() {
        // 1. GAID
        var gaid = "<error>"
        var lat = "<error>"
        try {
            val info = AdvertisingIdClient.getAdvertisingIdInfo(this)
            gaid = info.id ?: ""
            lat = info.isLimitAdTrackingEnabled.toString()
        } catch (e: Throwable) {
            gaid = "<err: ${e.javaClass.simpleName}: ${e.message}>"
        }

        // 2. Frida port scan (don't do on UI thread — uses sockets)
        val fridaPorts = mutableListOf<Int>()
        for (port in listOf(27042, 27043)) {
            try {
                Socket().use { s ->
                    s.connect(InetSocketAddress("127.0.0.1", port), 200)
                    fridaPorts.add(port)
                }
            } catch (_: Throwable) {}
        }
        val fridaStatus = if (fridaPorts.isEmpty()) "no open ports"
            else "OPEN: ${fridaPorts.joinToString()}"

        runOnUiThread {
            val txt = tv.text.toString()
                .replace("GAID: [loading async...]", "GAID: $gaid")
                .replace("isLimitAdTrackingEnabled: [loading async...]", "isLimitAdTrackingEnabled: $lat")
                .replace("frida.status (port scan): [loading async...]", "frida.status (port scan): $fridaStatus")
            tv.text = txt
        }
    }

    // ----------- helpers -----------

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

    private fun gsfId(): String? {
        return try {
            val uri = Uri.parse("content://com.google.android.gsf.gservices")
            contentResolver.query(uri, null, null, arrayOf("android_id"), null)?.use { c ->
                if (c.moveToFirst() && c.columnCount >= 2) {
                    val v = c.getString(1)
                    try { java.lang.Long.toHexString(v.toLong()) } catch (_: Throwable) { v }
                } else null
            }
        } catch (_: Throwable) {
            null
        }
    }

    private fun collectGlInfo(): Map<String, String> {
        val result = mutableMapOf<String, String>()
        val display = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
        if (display == EGL14.EGL_NO_DISPLAY) return result
        val ver = IntArray(2)
        if (!EGL14.eglInitialize(display, ver, 0, ver, 1)) return result
        try {
            result["egl_vendor"] = EGL14.eglQueryString(display, EGL14.EGL_VENDOR) ?: ""
            result["egl_version"] = EGL14.eglQueryString(display, EGL14.EGL_VERSION) ?: ""
            result["egl_apis"] = EGL14.eglQueryString(display, EGL14.EGL_CLIENT_APIS) ?: ""

            val cfg = arrayOfNulls<EGLConfig>(1)
            val numCfg = IntArray(1)
            val attribs = intArrayOf(
                EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
                EGL14.EGL_SURFACE_TYPE, EGL14.EGL_PBUFFER_BIT,
                EGL14.EGL_BLUE_SIZE, 8,
                EGL14.EGL_GREEN_SIZE, 8,
                EGL14.EGL_RED_SIZE, 8,
                EGL14.EGL_NONE
            )
            if (!EGL14.eglChooseConfig(display, attribs, 0, cfg, 0, 1, numCfg, 0) || numCfg[0] == 0) {
                return result
            }
            val ctxAttribs = intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, 2, EGL14.EGL_NONE)
            val ctx = EGL14.eglCreateContext(display, cfg[0], EGL14.EGL_NO_CONTEXT, ctxAttribs, 0)
            val surfAttribs = intArrayOf(EGL14.EGL_WIDTH, 1, EGL14.EGL_HEIGHT, 1, EGL14.EGL_NONE)
            val surf = EGL14.eglCreatePbufferSurface(display, cfg[0], surfAttribs, 0)
            if (EGL14.eglMakeCurrent(display, surf, surf, ctx)) {
                result["renderer"] = GLES20.glGetString(GLES20.GL_RENDERER) ?: ""
                result["vendor"] = GLES20.glGetString(GLES20.GL_VENDOR) ?: ""
                result["version"] = GLES20.glGetString(GLES20.GL_VERSION) ?: ""
                result["shading"] = GLES20.glGetString(GLES20.GL_SHADING_LANGUAGE_VERSION) ?: ""
                val ext = GLES20.glGetString(GLES20.GL_EXTENSIONS) ?: ""
                result["extensions"] = ext
                result["ext_count"] = ext.split(" ").count { it.isNotBlank() }.toString()
                EGL14.eglMakeCurrent(display, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT)
            }
            EGL14.eglDestroySurface(display, surf)
            EGL14.eglDestroyContext(display, ctx)
        } finally {
            EGL14.eglTerminate(display)
        }
        return result
    }
}
