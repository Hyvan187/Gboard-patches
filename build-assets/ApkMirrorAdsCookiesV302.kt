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
import org.w3c.dom.Element

/**
 * APKMirror Installer 3.0.2 (44-17ea75d) advertising manifest cleanup.
 *
 * Exact components confirmed from the original 3.0.2 AndroidManifest.xml,
 * not the old 2.0.3 Google Mobile Ads class names.
 *
 * Only advertising components and advertising-ID permissions are touched;
 * Firebase app infrastructure and purchase/download features are left intact.
 */
internal val apkMirrorAdsManifestV302Patch = resourcePatch {
    execute {
        document("AndroidManifest.xml").use { document ->
            val manifest = document.documentElement
            val androidNs = "http://schemas.android.com/apk/res/android"
            val adsPermissions = setOf(
                "android.permission.ACCESS_ADSERVICES_AD_ID",
                "android.permission.ACCESS_ADSERVICES_ATTRIBUTION",
                "com.google.android.gms.permission.AD_ID",
            )
            val permissions = document.getElementsByTagName("uses-permission")
            for (index in permissions.length - 1 downTo 0) {
                val node = permissions.item(index) as? Element ?: continue
                val name = node.getAttributeNS(androidNs, "name")
                    .ifBlank { node.getAttribute("android:name") }
                if (name in adsPermissions) manifest.removeChild(node)
            }

            val adsComponents = setOf(
                "com.google.android.libraries.ads.mobile.sdk.common.AdActivity",
                "com.vungle.ads.VungleProvider",
                "com.vungle.ads.internal.ui.VungleActivity",
            )
            for (tag in listOf("provider", "service", "activity")) {
                val nodes = document.getElementsByTagName(tag)
                for (index in 0 until nodes.length) {
                    val node = nodes.item(index) as? Element ?: continue
                    val name = node.getAttributeNS(androidNs, "name")
                        .ifBlank { node.getAttribute("android:name") }
                    if (name in adsComponents) {
                        node.setAttributeNS(androidNs, "android:enabled", "false")
                    }
                }
            }
        }
    }
}

/**
 * Prevent advertising SDK WebViews from persisting advertising cookies or
 * explicitly enabling third-party cookies. Do not clear the app's functional
 * WebView/session cookies and do not affect non-advertising CookieManager users.
 *
 * These exact Android WebKit calls were found in the 3.0.2 ads_mobile_sdk DEX.
 * Existing ad cookies (created before patching) are not erased retroactively.
 */
internal val apkMirrorAdsCookiesV302Patch = bytecodePatch {
    execute {
        Fingerprint(
            filters = listOf(
                methodCall(
                    definingClass = "Landroid/webkit/CookieManager;",
                    name = "setCookie",
                ),
            ),
            custom = { _, clazz -> clazz.type.startsWith("Lads_mobile_sdk/") },
        ).matchAllMethodIndicesForEach(requireMatches = false) { index ->
            // setCookie(String, String) is void and has no move-result.
            replaceInstruction(index, "nop")
        }

        Fingerprint(
            filters = listOf(
                methodCall(
                    definingClass = "Landroid/webkit/CookieManager;",
                    name = "setAcceptThirdPartyCookies",
                ),
            ),
            custom = { _, clazz -> clazz.type.startsWith("Lads_mobile_sdk/") },
        ).matchAllMethodIndicesForEach(requireMatches = false) { index ->
            // Android 5+ defaults to blocking third-party cookies for apps
            // targeting API 21+, so do not let the ads SDK enable them.
            replaceInstruction(index, "nop")
        }
    }
}
