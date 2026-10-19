package com.chargelens.app.data.battery

import android.annotation.SuppressLint
import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.roundToInt

/**
 * Reads the battery design capacity from the hidden `com.android.internal.os.PowerProfile`.
 * This is best effort: hidden-API restrictions or OEM changes can make it fail, in which case the
 * user can enter the value in Settings.
 */
@Singleton
class DesignCapacityReader @Inject constructor(@ApplicationContext private val context: Context) {

    val powerProfileCapacityMah: Int? by lazy { read() }

    @SuppressLint("PrivateApi")
    private fun read(): Int? = try {
        val cls = Class.forName("com.android.internal.os.PowerProfile")
        val profile = cls.getConstructor(Context::class.java).newInstance(context)
        val value = runCatching { cls.getMethod("getBatteryCapacity").invoke(profile) as Double }
            .getOrElse {
                cls.getMethod("getAveragePower", String::class.java).invoke(profile, "battery.capacity") as Double
            }
        value.roundToInt().takeIf { it in 500..20_000 }
    } catch (_: Throwable) {
        null
    }
}
