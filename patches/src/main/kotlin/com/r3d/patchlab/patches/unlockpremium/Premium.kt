@file:Suppress("DEPRECATION")

package com.r3d.patchlab.patches.unlockpremium

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.AppTarget
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.patch.ApkFileType
import app.morphe.patcher.patch.Compatibility
import com.r3d.patchlab.patches.pairip.disableLicenseCheckPatch

@Suppress("unused")
val unlockPremiumPatch = bytecodePatch(
    name = "Unlock Premium",
    description = "Unlock Premium Features."
) {
    dependsOn(disableLicenseCheckPatch)

    compatibleWith(
        Compatibility(
            packageName = "br.com.zetabit.ios_standby",
            name = "StandBy Mode",
            apkFileType = ApkFileType.APKM,
            targets = listOf(
                AppTarget(version = "2.1.19.558")
            )
        )
    )
    execute {
        // lt8.i(Map) is the aggregate premium check: true iff any entitlement in the
        // map is a premium entitlement that is active. Its result is written into the
        // premium StateFlow (ih8.i) that drives the UI. Forcing it to return true
        // unlocks premium without touching the coroutine/Flow machinery.
        val fingerprint = Fingerprint(
            definingClass = "Llt8;",
            name = "i",
            returnType = "Z",
            parameters = listOf("Ljava/util/Map;")
        )

        fingerprint.methodOrNull?.apply {
            addInstructions(
                0,
                """
                    const/4 v0, 0x1
                    return v0
                """.trimIndent()
            )
        } ?: throw Exception("Could not find lt8.i(Map) — app obfuscation changed; re-extract from current APK.")
    }
}
