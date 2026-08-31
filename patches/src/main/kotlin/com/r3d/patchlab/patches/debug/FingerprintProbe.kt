@file:Suppress("DEPRECATION")

package com.r3d.patchlab.patches.debug

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.patch.ApkFileType
import app.morphe.patcher.patch.Compatibility
import app.morphe.patcher.patch.bytecodePatch

@Suppress("unused")
val fingerprintProbePatch = bytecodePatch(
    name = "Fingerprint Probe",
    description = "Tests structural fingerprint matching."
) {
    compatibleWith(
        Compatibility(
            packageName = "br.com.zetabit.ios_standby",
            name = "StandBy Mode",
            apkFileType = ApkFileType.APKM
        )
    )

    execute {
        val fingerprint = Fingerprint(
            name = "invokeSuspend",
            returnType = "Ljava/lang/Object;",
            parameters = listOf("Ljava/lang/Object;"),
            strings = listOf(
                "UserActivityDetector: Disabled - resetting state",
                "UserActivityDetector: Enabled - resetting state and waiting 3000ms before detection",
                "UserActivityDetector: Initialized and ready to detect"
            )
        )

        fingerprint.methodOrNull
            ?: throw Exception("Structural fingerprint not found")
    }
}