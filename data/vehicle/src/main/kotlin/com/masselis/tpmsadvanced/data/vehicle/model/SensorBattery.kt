package com.masselis.tpmsadvanced.data.vehicle.model

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import java.util.Locale

/** Keep the reported unit: voltage cannot reliably be converted to a charge percentage. */
public sealed interface SensorBattery : Parcelable {
    public val isLow: Boolean
    public fun label(): String

    @Parcelize
    public data class Voltage(val volts: Float) : SensorBattery {
        override val isLow: Boolean get() = volts < LOW_VOLTS
        override fun label(): String = String.format(Locale.getDefault(), "%.2f V", volts)
    }

    @Parcelize
    public data class Percentage(val percent: Int) : SensorBattery {
        override val isLow: Boolean get() = percent <= LOW_PERCENT
        override fun label(): String = "$percent%"
    }

    public companion object {
        private const val LOW_VOLTS = 2.6f
        private const val LOW_PERCENT = 10
        private const val FULL_PERCENT = 100

        /** Old database rows have no unit metadata, so their battery remains unknown. */
        public fun fromStored(voltage: Double?, percent: Long?): SensorBattery? = when {
            voltage != null && voltage.isFinite() && voltage >= 0 -> Voltage(voltage.toFloat())
            percent != null && percent in 0..FULL_PERCENT -> Percentage(percent.toInt())
            else -> null
        }
    }
}
