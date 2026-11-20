package app.template.patches.apkmirrorinstaller

import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.resourcePatch
import org.w3c.dom.Element

/**
 * DISPLAY-ONLY: replaces APKMirror's marketing message "2 months free!" with
 * "Unlimited". Does not change Google Play subscription, entitlement, terms,
 * billing or recurring price.
 */
internal val apkMirrorUnlimitedCosmeticV302Patch = resourcePatch {
    execute {
        document("res/values/strings.xml").use { document ->
            val nodes = document.getElementsByTagName("string")
            var found = false
            for (i in 0 until nodes.length) {
                val node = nodes.item(i) as? Element ?: continue
                if (node.getAttribute("name") == "subscription_description_price") {
                    node.textContent = "Unlimited"
                    found = true
                    break
                }
            }
            if (!found) {
                throw PatchException("Could not find APKMirror v3.0.2 marketing resource subscription_description_price")
            }
        }
    }
}
