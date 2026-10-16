@file:Suppress("MagicNumber") // Stored pressure bounds in kPa.

package com.masselis.tpmsadvanced.data.vehicle.model

import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
public data class TyreSpecification(
    val id: String = UUID.randomUUID().toString(), val load: String,
    val frontSize: String, val rearSize: String = "", val frontKpa: Float, val rearKpa: Float,
    val source: String, val notes: String = "",
) {
    public fun validate() {
        require(UUID.fromString(id).toString() == id)
        require(load.isNotBlank() && load.length <= 80)
        require(frontSize.isNotBlank() && frontSize.length <= 80 && rearSize.length <= 80)
        require(frontKpa > 0 && frontKpa <= 2000 && rearKpa > 0 && rearKpa <= 2000)
        require(source.isNotBlank() && source.length <= 300 && notes.length <= 1000)
    }
}

/** Advisory only. A recommended cold target is not itself a low/high alarm threshold. */
public fun specificationComparison(spec: TyreSpecification, profile: PressureProfile): List<String> = buildList {
    if (spec.frontKpa !in profile.low..profile.high) add("Front cold recommendation is outside this profile's limits")
    val rearLow = if (profile.separateRear) profile.rearLow else profile.low
    val rearHigh = if (profile.separateRear) profile.rearHigh else profile.high
    if (spec.rearKpa !in rearLow..rearHigh) add("Rear cold recommendation is outside this profile's limits")
    if (isEmpty()) add("Cold recommendations are within this profile's alert limits")
}
