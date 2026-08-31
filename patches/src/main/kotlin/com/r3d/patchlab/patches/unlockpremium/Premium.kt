@file:Suppress("DEPRECATION")

package com.r3d.patchlab.patches.unlockpremium

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.fieldAccess
import app.morphe.patcher.methodCall
import app.morphe.patcher.opcode
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.ClassDef
import com.android.tools.smali.dexlib2.iface.Method
import com.android.tools.smali.dexlib2.iface.instruction.Instruction
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.reference.FieldReference
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
import com.android.tools.smali.dexlib2.iface.reference.TypeReference

private const val OBJECT = "Ljava/lang/Object;"
private const val BOOLEAN = "Ljava/lang/Boolean;"

private val CONDITIONAL_BRANCH_OPCODES = setOf(
    Opcode.IF_EQ,
    Opcode.IF_NE,
    Opcode.IF_LT,
    Opcode.IF_GE,
    Opcode.IF_GT,
    Opcode.IF_LE,
    Opcode.IF_EQZ,
    Opcode.IF_NEZ,
    Opcode.IF_LTZ,
    Opcode.IF_GEZ,
    Opcode.IF_GTZ,
    Opcode.IF_LEZ
)

private fun Instruction.fieldReferenceOrNull(): FieldReference? =
    (this as? ReferenceInstruction)?.reference as? FieldReference

private fun Instruction.methodReferenceOrNull(): MethodReference? =
    (this as? ReferenceInstruction)?.reference as? MethodReference

private fun Instruction.typeReferenceOrNull(): TypeReference? =
    (this as? ReferenceInstruction)?.reference as? TypeReference

private fun MethodReference.hasParameters(vararg types: String): Boolean =
    parameterTypes.map { it.toString() } == types.toList()

private fun Method.hasParameters(vararg types: String): Boolean =
    parameterTypes.map { it.toString() } == types.toList()

private fun MethodReference.isBooleanUnbox(): Boolean =
    definingClass == BOOLEAN &&
            name == "booleanValue" &&
            hasParameters() &&
            returnType == "Z"

private fun MethodReference.isBooleanBox(): Boolean =
    definingClass == BOOLEAN &&
            name == "valueOf" &&
            hasParameters("Z") &&
            returnType == BOOLEAN

private fun ClassDef.hasExpectedCaptureLayout(): Boolean {
    val fields = instanceFields.toList()

    return fields.size == 3 &&
            fields.count { it.type == "Z" } == 2 &&
            fields.count { it.type == BOOLEAN } == 1
}

private fun Method.isBooleanCoroutineBridge(owner: String): Boolean {
    if (returnType != OBJECT) return false

    if (!hasParameters(
            OBJECT,
            OBJECT,
            OBJECT,
            OBJECT
        )
    ) {
        return false
    }

    val instructions = implementation?.instructions?.toList()
        ?: return false

    val booleanCastCount = instructions.count {
        it.opcode == Opcode.CHECK_CAST &&
                it.typeReferenceOrNull()?.type == BOOLEAN
    }

    val booleanUnboxCount = instructions
        .mapNotNull { it.methodReferenceOrNull() }
        .count { it.isBooleanUnbox() }

    val selfNewInstanceCount = instructions.count {
        it.opcode == Opcode.NEW_INSTANCE &&
                it.typeReferenceOrNull()?.type == owner
    }

    val booleanWrites = instructions.mapNotNull { instruction ->
        if (instruction.opcode != Opcode.IPUT_BOOLEAN) {
            null
        } else {
            instruction.fieldReferenceOrNull()
        }
    }.filter {
        it.definingClass == owner &&
                it.type == "Z"
    }

    val nullableBooleanWrites = instructions.mapNotNull { instruction ->
        if (instruction.opcode != Opcode.IPUT_OBJECT) {
            null
        } else {
            instruction.fieldReferenceOrNull()
        }
    }.filter {
        it.definingClass == owner &&
                it.type == BOOLEAN
    }

    val selfInvokeSuspendCount = instructions
        .mapNotNull { it.methodReferenceOrNull() }
        .count {
            it.definingClass == owner &&
                    it.name == "invokeSuspend" &&
                    it.hasParameters(OBJECT) &&
                    it.returnType == OBJECT
        }

    return booleanCastCount == 3 &&
            booleanUnboxCount == 2 &&
            selfNewInstanceCount == 1 &&
            booleanWrites.size == 2 &&
            booleanWrites.map { it.name }.distinct().size == 2 &&
            nullableBooleanWrites.size == 1 &&
            selfInvokeSuspendCount == 1
}

private fun Method.hasExpectedInvokeSuspendBody(owner: String): Boolean {
    val instructions = implementation?.instructions?.toList()
        ?: return false

    val booleanReads = instructions.mapNotNull { instruction ->
        if (instruction.opcode != Opcode.IGET_BOOLEAN) {
            null
        } else {
            instruction.fieldReferenceOrNull()
        }
    }.filter {
        it.definingClass == owner &&
                it.type == "Z"
    }

    val nullableBooleanReads = instructions.mapNotNull { instruction ->
        if (instruction.opcode != Opcode.IGET_OBJECT) {
            null
        } else {
            instruction.fieldReferenceOrNull()
        }
    }.filter {
        it.definingClass == owner &&
                it.type == BOOLEAN
    }

    val calledMethods = instructions.mapNotNull {
        it.methodReferenceOrNull()
    }

    val booleanUnboxCount =
        calledMethods.count { it.isBooleanUnbox() }

    val booleanBoxCount =
        calledMethods.count { it.isBooleanBox() }

    val conditionalBranchCount =
        instructions.count { it.opcode in CONDITIONAL_BRANCH_OPCODES }

    val returnObjectCount =
        instructions.count { it.opcode == Opcode.RETURN_OBJECT }

    return booleanReads.size == 2 &&
            booleanReads.map { it.name }.distinct().size == 2 &&
            nullableBooleanReads.size == 1 &&
            booleanUnboxCount == 1 &&
            booleanBoxCount == 1 &&
            conditionalBranchCount >= 3 &&
            returnObjectCount == 1
}

internal val booleanCoroutineFingerprint = Fingerprint(
    name = "invokeSuspend",
    returnType = OBJECT,
    parameters = listOf(OBJECT),
    filters = listOf(
        fieldAccess(
            definingClass = "this",
            type = "Z",
            opcode = Opcode.IGET_BOOLEAN
        ),
        fieldAccess(
            definingClass = "this",
            type = "Z",
            opcode = Opcode.IGET_BOOLEAN
        ),
        fieldAccess(
            definingClass = "this",
            type = BOOLEAN,
            opcode = Opcode.IGET_OBJECT
        ),
        methodCall(
            definingClass = BOOLEAN,
            name = "booleanValue",
            parameters = emptyList(),
            returnType = "Z",
            opcodes = listOf(
                Opcode.INVOKE_VIRTUAL,
                Opcode.INVOKE_VIRTUAL_RANGE
            )
        ),
        methodCall(
            definingClass = BOOLEAN,
            name = "valueOf",
            parameters = listOf("Z"),
            returnType = BOOLEAN,
            opcodes = listOf(
                Opcode.INVOKE_STATIC,
                Opcode.INVOKE_STATIC_RANGE
            )
        ),
        opcode(Opcode.RETURN_OBJECT)
    ),
    custom = { method, classDef ->
        val owner = classDef.type

        classDef.hasExpectedCaptureLayout() &&
                method.hasExpectedInvokeSuspendBody(owner) &&
                classDef.methods.count {
                    it.isBooleanCoroutineBridge(owner)
                } == 1
    }
)
