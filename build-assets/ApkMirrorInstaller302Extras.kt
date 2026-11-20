/*
 * APKMirror Installer 3.0.2 (44-17ea75d) combined compatibility helpers.
 *
 * Installer-source spoof logic adapted from MorpheApp/morphe-patches-library,
 * ChangePackageInstallerPatch.kt (Copyright 2026 Morphe).
 * See https://github.com/MorpheApp/morphe-patches-library/blob/main/NOTICE
 * and the upstream GNU General Public License v3 + section 7 notices.
 *
 * NOTE: Spoofing affects in-app Android PackageManager query results only;
 * it does not rewrite Android's actual install-source record.
 * The Play Store update block changes AndroidManifest versionCode only.
 */
package app.template.patches.apkmirrorinstaller

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.methodCall
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.extensions.InstructionExtensions.replaceInstruction
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.patch.resourcePatch
import app.template.patches.shared.matchAllMethodIndicesForEach
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction

// Matches the default "Google Play" option of Morphe's Change installer source patch.
internal val apkMirrorGooglePlayInstallerV302Patch = bytecodePatch {
    execute {
        val getters = listOf(
            Triple(
                "Landroid/content/pm/PackageManager;",
                "getInstallerPackageName",
                false,
            ),
            Triple(
                "Landroid/content/pm/InstallSourceInfo;",
                "getInstallingPackageName",
                false,
            ),
            Triple(
                "Landroid/content/pm/InstallSourceInfo;",
                "getInitiatingPackageName",
                false,
            ),
            Triple(
                "Landroid/content/pm/InstallSourceInfo;",
                "getPackageSource",
                true,
            ),
        )
        getters.forEach { (definingClass, methodName, isInt) ->
            Fingerprint(
                filters = listOf(
                    methodCall(
                        definingClass = definingClass,
                        name = methodName,
                    ),
                ),
            ).matchAllMethodIndicesForEach(requireMatches = false) { index ->
                val resultIndex = index + 1
                val result = getInstruction(resultIndex)
                val expected = if (isInt) Opcode.MOVE_RESULT else Opcode.MOVE_RESULT_OBJECT
                if (result.opcode != expected) return@matchAllMethodIndicesForEach
                val register = (result as OneRegisterInstruction).registerA
                val instruction = if (isInt) {
                    // Android PackageInstaller.PACKAGE_SOURCE_STORE == 2.
                    "const/16 v$register, 0x2"
                } else {
                    "const-string v$register, \"com.android.vending\""
                }
                replaceInstruction(resultIndex, instruction)
            }
        }
    }
}

// Same core manifest approach as Morphe's Disable Play Store updates patch.
// Deliberately scoped to APKMirror Installer 3.0.2 via the sole public depender.
internal val apkMirrorBlockPlayStoreUpdatesV302Patch = resourcePatch {
    execute {
        document("AndroidManifest.xml").use { document ->
            val androidNs = "http://schemas.android.com/apk/res/android"
            document.documentElement.setAttributeNS(
                androidNs,
                "android:versionCode",
                Int.MAX_VALUE.toString(),
            )
        }
    }
}
