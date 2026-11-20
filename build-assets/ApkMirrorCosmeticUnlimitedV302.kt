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
        // Explicit language resources override the default Android string.
        // German is required for the user's locale; modifying only values/
        // leaves the original "2 Monate gratis!" displayed.
        for (path in listOf(
            "res/values/strings.xml",
            "res/values-de/strings.xml",
        )) {
            document(path).use { document ->
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
                    throw PatchException(
                        "Missing subscription_description_price in APKMirror 3.0.2 resource: $path",
                    )
                }
            }
        }
    }
}
