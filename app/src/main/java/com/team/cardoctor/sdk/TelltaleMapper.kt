package com.team.cardoctor.sdk

import com.team.cardoctor.data.Part

/**
 * Maps Pleos Safety telltale types (by enum name, so this stays unit-testable without the SDK)
 * to the parts our rule engine judges. Telltales not listed are status lamps, not faults.
 */
object TelltaleMapper {
    private val map = mapOf(
        "TPMS" to Part.TPMS,
        "ABS" to Part.ABS,
        "ESC" to Part.ESC,
        "MDPS" to Part.STEERING_MDPS,
        "LOW_VOLT_BATTERY" to Part.BATTERY_12V,
        "AIRBAG" to Part.AIRBAG,
        "ENGINE_OIL_PRESSURE" to Part.ENGINE_OIL_PRESSURE,
        "ENGINE_TEMPERATURE" to Part.COOLANT_OVERHEAT,
        "ENGINE" to Part.ENGINE_CHECK,
        "EV_SYSTEM" to Part.EV_SYSTEM,
        "HEV_SYSTEM" to Part.EV_SYSTEM,
        "LOW_EV_BATTERY" to Part.EV_BATTERY_LOW,
        "LOW_FUEL" to Part.FUEL_LOW,
        "LED_HEADLAMP" to Part.HEADLAMP_SYSTEM,
        "ELECTRONIC_PARKING_BRAKE" to Part.BRAKE_WARNING,
    )

    /**
     * @param isDriving the red PARKING_BRAKE lamp is normal while parked; while driving it means
     * the parking brake is still on or brake fluid is low, so it becomes a brake warning.
     */
    fun partFor(typeName: String, isDriving: Boolean): Part? = when (typeName) {
        "PARKING_BRAKE" -> if (isDriving) Part.BRAKE_WARNING else null
        else -> map[typeName]
    }
}
