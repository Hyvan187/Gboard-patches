/*
 * Copyright 2026 OpenAI adaptation for Samsung Email 6.2.16.5.
 *
 * Installer source logic adapted from:
 * https://github.com/MorpheApp/morphe-patches-library/blob/main/patch-library/src/main/kotlin/app/morphe/patches/all/misc/fix/changepackageinstaller/ChangePackageInstallerPatch.kt
 * (Copyright 2026 Morphe, GPLv3 with section 7 additional terms).
 *
 * Version-code logic adapted from:
 * https://github.com/MorpheApp/morphe-patches/pull/2470
 * (Copyright 2026 Morphe, GPLv3 Section 7).
 *
 * Preserve upstream copyright and license notices in derivative distributions.
 *
 * Neither patch changes Android's actual installer-of-record. The first patch
 * spoofs app-visible PackageManager results and the second modifies the APK's
 * manifest versionCode to prevent regular Google Play updates.
 */
package org.ungoogled.patches.samsungemail.misc

import app.morphe.patcher.extensions.InstructionExtensions.addInstruction
import app.morphe.patcher.extensions.InstructionExtensions.replaceInstruction
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.patch.resourcePatch
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.Method
import com.android.tools.smali.dexlib2.iface.instruction.FiveRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.instruction.RegisterRangeInstruction
import com.android.tools.smali.dexlib2.iface.instruction.TwoRegisterInstruction
import com.android.tools.smali.dexlib2.iface.reference.FieldReference
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
import org.w3c.dom.Element
import java.util.logging.Logger

private val extraLogger = Logger.getLogger("SamsungEmail62165Extras")
private const val VERSION_COMPAT = "Lorg/ungoogled/samsungemail/VersionCodeCompat;"
private const val GOOGLE_PLAY = "com.android.vending"

/**
 * Implements Morphe's default Change installer source option (Google Play).
 * Replaces move-result instructions for the four package-source API getters.
 * This cannot change what Android's PackageManager returns to other apps.
 */
internal val samsungEmailGooglePlaySourcePatch = bytecodePatch(
    description = "Spoofs Google Play as the installer source inside Samsung Email.",
) {
    execute {
        val sourceApis = setOf(
            "getInstallerPackageName",
            "getInstallingPackageName",
            "getInitiatingPackageName",
            "getPackageSource",
        )
        fun sourceGetter(ref: MethodReference): Boolean {
            return when (ref.definingClass) {
                "Landroid/content/pm/PackageManager;" ->
                    ref.name == "getInstallerPackageName" &&
                        ref.returnType == "Ljava/lang/String;"
                "Landroid/content/pm/InstallSourceInfo;" ->
                    ref.name in sourceApis
                else -> false
            }
        }

        val sites = mutableListOf<Pair<String, Method>>()
        classDefForEach { c ->
            if (c.type.startsWith("Lorg/ungoogled/")) return@classDefForEach
            for (m in c.methods) {
                if (m.implementation?.instructions?.any {
                    val ref = (it as? ReferenceInstruction)?.reference as? MethodReference
                    ref != null && sourceGetter(ref)
                } == true) sites += c.type to m
            }
        }
        var changed = 0
        for ((type, origin) in sites) {
            val mutable = mutableClassDefBy(type).methods.first {
                it.name == origin.name &&
                    it.parameterTypes == origin.parameterTypes &&
                    it.returnType == origin.returnType
            }
            val instructions = mutable.implementation!!.instructions
            // No additions here, but reverse order is safe for future extensions.
            for (index in instructions.indices.reversed()) {
                val ref = (instructions[index] as? ReferenceInstruction)?.reference as? MethodReference
                    ?: continue
                if (!sourceGetter(ref) || index + 1 >= instructions.size) continue
                val isInt = ref.name == "getPackageSource"
                val expectedResult = if (isInt) Opcode.MOVE_RESULT else Opcode.MOVE_RESULT_OBJECT
                val result = instructions[index + 1]
                if (result.opcode != expectedResult || result !is OneRegisterInstruction) continue
                val register = result.registerA
                if (register !in 0..255) continue
                mutable.replaceInstruction(
                    index + 1,
                    if (isInt) "const/16 v$register, 0x2"
                    else "const-string v$register, \"$GOOGLE_PLAY\"",
                )
                changed++
            }
        }
        extraLogger.info("Samsung Email Google Play installer source getters patched: $changed")
    }
}

/**
 * Same default versionCode as the Morphe Disable Play Store updates patch:
 * Int.MAX_VALUE=2147483647. This makes normal store updates non-upgrades.
 */
internal val samsungEmailBlockStoreUpdatesPatch = resourcePatch(
    description = "Sets versionCode to 2147483647 to block Play Store updates.",
) {
    execute {
        document("AndroidManifest.xml").use { document ->
            val manifest = document.documentElement as Element
            manifest.setAttributeNS(
                "http://schemas.android.com/apk/res/android",
                "android:versionCode",
                Int.MAX_VALUE.toString(),
            )
        }
    }
}

/**
 * Mirroring Morphe's anti-version-conflict behavior: reads of PackageInfo's
 * version code by Samsung Email are forwarded to a shim returning 621600005
 * when the queried package has the artificially inflated versionCode.
 */
internal val samsungEmailVersionCodeCompatPatch = bytecodePatch(
    description = "Preserves the original Samsung Email version for in-app version checks.",
) {
    execute {
        val sites = mutableListOf<Pair<String, Method>>()
        classDefForEach { c ->
            if (c.type.startsWith("Lorg/ungoogled/")) return@classDefForEach
            for (m in c.methods) {
                if (m.implementation?.instructions?.any { instruction ->
                    when (val ref = (instruction as? ReferenceInstruction)?.reference) {
                        is FieldReference ->
                            instruction.opcode == Opcode.IGET &&
                                ref.definingClass == "Landroid/content/pm/PackageInfo;" &&
                                ref.name == "versionCode" && ref.type == "I"
                        is MethodReference ->
                            (instruction.opcode == Opcode.INVOKE_VIRTUAL ||
                                instruction.opcode == Opcode.INVOKE_VIRTUAL_RANGE) &&
                                ref.definingClass == "Landroid/content/pm/PackageInfo;" &&
                                ref.name == "getLongVersionCode" &&
                                ref.returnType == "J"
                        else -> false
                    }
                } == true) sites += c.type to m
            }
        }
        var reads = 0
        for ((type, origin) in sites) {
            val mutable = mutableClassDefBy(type).methods.first {
                it.name == origin.name &&
                    it.parameterTypes == origin.parameterTypes &&
                    it.returnType == origin.returnType
            }
            val instructions = mutable.implementation!!.instructions
            // Reverse order keeps indexes stable when an IGET becomes two instructions.
            for (index in instructions.indices.reversed()) {
                val instruction = instructions[index]
                val ref = (instruction as? ReferenceInstruction)?.reference
                if (ref is FieldReference &&
                    instruction.opcode == Opcode.IGET &&
                    ref.definingClass == "Landroid/content/pm/PackageInfo;" &&
                    ref.name == "versionCode" && ref.type == "I"
                ) {
                    val pair = instruction as? TwoRegisterInstruction ?: continue
                    val dest = pair.registerA
                    val receiver = pair.registerB
                    if (dest > 255) continue
                    mutable.replaceInstruction(
                        index,
                        "invoke-static/range { v$receiver .. v$receiver }, " +
                            "$VERSION_COMPAT->getVersionCode(Landroid/content/pm/PackageInfo;)I",
                    )
                    mutable.addInstruction(index + 1, "move-result v$dest")
                    reads++
                } else if (ref is MethodReference &&
                    ref.definingClass == "Landroid/content/pm/PackageInfo;" &&
                    ref.name == "getLongVersionCode" &&
                    ref.returnType == "J" &&
                    (instruction.opcode == Opcode.INVOKE_VIRTUAL ||
                        instruction.opcode == Opcode.INVOKE_VIRTUAL_RANGE) &&
                    index + 1 < instructions.size &&
                    instructions[index + 1].opcode == Opcode.MOVE_RESULT_WIDE
                ) {
                    val recv = when (instruction) {
                        is FiveRegisterInstruction -> instruction.registerC
                        is RegisterRangeInstruction -> instruction.startRegister
                        else -> continue
                    }
                    mutable.replaceInstruction(
                        index,
                        "invoke-static/range { v$recv .. v$recv }, " +
                            "$VERSION_COMPAT->getLongVersionCode(Landroid/content/pm/PackageInfo;)J",
                    )
                    reads++
                }
            }
        }
        extraLogger.info("Samsung Email versionCode reads patched: $reads")
    }
}
