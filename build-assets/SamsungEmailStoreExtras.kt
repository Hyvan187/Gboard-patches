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
 * spoofs app-visible PackageManager results only if an app actually calls them.
 * Samsung Email 6.2.16.5 contains none of the four getter names, so this
 * remains a safe no-op until the app uses these APIs. The second patch
 * updates the manifest versionCode to prevent Google Play updates.
 */
package org.ungoogled.patches.samsungemail.misc

import app.morphe.patcher.extensions.InstructionExtensions.replaceInstruction
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.patch.resourcePatch
import com.android.tools.smali.dexlib2.iface.Method
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
import org.w3c.dom.Element
import java.util.logging.Logger

private val extraLogger = Logger.getLogger("SamsungEmail62165Extras")
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
        extraLogger.info("Samsung Email installer source getters patched: $changed (0 expected on 6.2.16.5)")
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

