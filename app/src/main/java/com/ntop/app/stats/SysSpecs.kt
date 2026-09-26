package com.ntop.app.stats

import android.app.Activity
import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.os.Build
import android.util.DisplayMetrics

// ---------------------------------------------------------------------------
// ntop SYS spec data: everything here is readable via public APIs, no
// permissions. Deliberately honest — specs the OS hides (camera MP/OIS,
// panel type, Nothing OS version) are not guessed.
// ---------------------------------------------------------------------------

/** Marketing name for known SoCs; raw model string otherwise. */
fun chipsetName(): String {
    val soc = try {
        if (Build.VERSION.SDK_INT >= 31) Build.SOC_MODEL ?: Build.HARDWARE else Build.HARDWARE
    } catch (_: Exception) {
        Build.HARDWARE
    } ?: "—"
    val k = soc.uppercase()
    return when {
        "SM8735" in k -> "Snapdragon 8s Gen 4"
        "SM8650" in k -> "Snapdragon 8 Gen 3"
        "SM8550" in k -> "Snapdragon 8 Gen 2"
        "SM8475" in k -> "Snapdragon 8+ Gen 1"
        "SM4375" in k -> "Snapdragon 7s Gen 3"
        "MT6877" in k -> "Dimensity 7200 Pro"
        "MT6879" in k -> "Dimensity 7350 Pro"
        "MT6833" in k -> "Dimensity 7300"
        else -> soc
    }
}

fun chipsetVendor(): String = when {
    chipsetName().startsWith("Snapdragon") -> "Qualcomm®"
    chipsetName().startsWith("Dimensity") -> "MediaTek"
    else -> Build.MANUFACTURER.ifEmpty { "—" }
}

/** Rear/front camera counts. MP, OIS and telephoto are NOT exposed. */
fun cameraCounts(context: Context): String = try {
    val cm = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
    var rear = 0
    var front = 0
    for (id in cm.cameraIdList) {
        when (cm.getCameraCharacteristics(id).get(CameraCharacteristics.LENS_FACING)) {
            CameraCharacteristics.LENS_FACING_BACK -> rear++
            CameraCharacteristics.LENS_FACING_FRONT -> front++
        }
    }
    if (rear == 0 && front == 0) "—" else "$rear REAR · $front FRONT"
} catch (_: Exception) {
    "—"
}

/** Diagonal inches from physical pixels + xdpi/ydpi, plus refresh rate. */
fun screenSpec(context: Context): String {
    return try {
        val metrics: DisplayMetrics = context.resources.displayMetrics
        val wIn = metrics.widthPixels / metrics.xdpi
        val hIn = metrics.heightPixels / metrics.ydpi
        if (!wIn.isFinite() || !hIn.isFinite() || wIn <= 0 || hIn <= 0) return "—"
        val inches = kotlin.math.sqrt((wIn * wIn + hIn * hIn).toDouble())
        val hz = (context as? Activity)?.windowManager?.defaultDisplay?.refreshRate
            ?: context.display?.refreshRate ?: 0f
        val size = "%.2f".format(java.util.Locale.getDefault(), inches).trimEnd('0').trimEnd('.') + "\""
        if (hz > 0) "$size · ${hz.toInt()}HZ" else size
    } catch (_: Exception) {
        "—"
    }
}

fun osVersionText(): String =
    "ANDROID ${Build.VERSION.RELEASE} · SDK ${Build.VERSION.SDK_INT}"

fun abiText(): String = Build.SUPPORTED_ABIS.firstOrNull() ?: "—"
