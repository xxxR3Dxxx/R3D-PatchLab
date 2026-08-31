@file:Suppress("DEPRECATION")

package com.r3d.patchlab.patches.unlockpremium

import app.morphe.patcher.extensions.InstructionExtensions.addInstruction
import app.morphe.patcher.patch.ApkFileType
import app.morphe.patcher.patch.AppTarget
import app.morphe.patcher.patch.Compatibility
import app.morphe.patcher.patch.bytecodePatch
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.r3d.patchlab.patches.pairip.disableLicenseCheckPatch

@Suppress("unused")
val fingerprintProbePatch = bytecodePatch(
    name = "Fingerprint Probe",
    description = "Tests structural fingerprint matching."
) {
    dependsOn(disableLicenseCheckPatch)

    compatibleWith(
        Compatibility(
            packageName = "br.com.zetabit.ios_standby",
            name = "StandBy Mode",
            apkFileType = ApkFileType.APKM,
            targets = listOf(
                AppTarget(
                    version = "2.1.22.561"
                ),
                AppTarget(
                    version = null,
                    isExperimental = true
                )
            )
        )
    )

    execute {
        val match = booleanCoroutineFingerprint
            .matchAll(1..1)
            .single()

        val returnMatch = match.instructionMatches[5]

        val returnRegister =
            returnMatch.getInstruction<OneRegisterInstruction>().registerA

        match.method.addInstruction(
            returnMatch.index,
            "sget-object v$returnRegister, Ljava/lang/Boolean;->TRUE:Ljava/lang/Boolean;"
        )

        println(
            "PATCHED: ${match.originalClassDef.type}->" +
                    match.originalMethod.name +
                    " forcing returned Boolean = TRUE"
        )
    }
}
