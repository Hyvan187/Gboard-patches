#!/usr/bin/env python3
"""Version-specific, isolated 6.2.16.5 port of bearinmindcat's Samsung Email patch.
No changes are made to the user's main branch.
"""
from pathlib import Path

root = Path.cwd()
target = root / "patches/src/main/kotlin/org/ungoogled/patches/samsungemail"
patch = target / "misc/EnableNonOneUiPatch.kt"
constant = target / "SamsungEmailConstants.kt"
meta = root / "patches/build.gradle.kts"
props = root / "gradle.properties"

src = patch.read_text()
old = '''private object ConsistentDeviceIdFingerprint : Fingerprint(
    returnType = "Ljava/lang/String;",
    parameters = listOf("Landroid/content/Context;"),
    strings = listOf("get deviceId for phone model"),
)'''
new = '''private object ConsistentDeviceIdFingerprint : Fingerprint(
    // Both APK 6.2.13.1 and 6.2.16.5 define this private static method.
    // The old debug string is gone in 6.2.16.5, but the method signature
    // and owning class are confirmed identical in both provided APKs.
    definingClass = "Lcom/samsung/android/emailcommon/basic/crypto/Device;",
    name = "getConsistentDeviceId",
    returnType = "Ljava/lang/String;",
    parameters = listOf("Landroid/content/Context;"),
)'''
assert src.count(old) == 1, "Old string-based fingerprint not found or changed"
patch.write_text(src.replace(old, new))

# Bundle the two previous external Morphe patches as private dependencies.
# They therefore execute whenever the Samsung Email compatibility patch does,
# without loading the second Morphe Patches source in the manager.
main = patch.read_text()
deps = "        calendarProviderPatch,\n"
assert main.count(deps) == 1, "Samsung Email patch dependency block changed"
main = main.replace(deps, deps +
    "        samsungEmailGooglePlaySourcePatch, samsungEmailBlockStoreUpdatesPatch,\n"
)
patch.write_text(main)

extras = root.parent / "build-assets/SamsungEmailStoreExtras.kt"
assert extras.exists(), "Integrated Samsung Email patch code missing"
(target / "misc/StoreExtrasV62165.kt").write_text(extras.read_text())

# Samsung Email 6.2.16.5 UX compatibility additions.
ux_patch = root.parent / "build-assets/SamsungEmailUiFixesV62165.kt"
assert ux_patch.exists(), "Samsung Email UX patch source missing"
(target / "misc/UiCompatibilityV62165.kt").write_text(ux_patch.read_text())

extension_dir = root / "extensions/samsungemail/src/main/java/org/ungoogled/samsungemail"
for source_name, dest_name in (
    ("SelectionMenusV62165.java", "SelectionMenus.java"),
    ("AttachmentPickerV62165.java", "AttachmentPicker.java"),
):
    source_file = root.parent / "build-assets" / source_name
    assert source_file.exists(), "Required Android UI extension missing: " + source_name
    (extension_dir / dest_name).write_text(source_file.read_text())

main = patch.read_text()
needle = "        samsungEmailGooglePlaySourcePatch, samsungEmailBlockStoreUpdatesPatch," + chr(10)
assert main.count(needle) == 1, "Missing integrated store dependencies"
main = main.replace(needle, needle + "        samsungEmailSelectionAttachmentV62165Patch," + chr(10))
# Morphe selection dialog: short, accurate description of the single unified patch.
old_description = 'description = "Makes Samsung Email usable on non-One UI ROMs."'
new_description = (
    'description = "Samsung Email 6.2.16.5 for non-One UI devices: '
    'Samsung API and icon compatibility, Exchange/calendar sync, Android text '
    'selection and document picker, and automatic store update blocking."'
)
assert main.count(old_description) == 1, "Upstream Morphe patch description changed"
main = main.replace(old_description, new_description)
patch.write_text(main)


c = constant.read_text()
assert c.count('AppTarget(version = "6.2.13.1")') == 1
c = c.replace('AppTarget(version = "6.2.13.1")', 'AppTarget(version = "6.2.16.5", versionCode = 621600005)')
c = c.replace('Pinned to 6.2.13.1: R8 renames its classes every build.', 'Pinned to 6.2.16.5 (build 621600005): verified on the supplied APK.')
constant.write_text(c)

# Distinguish this local source from bearinmind patches 2.10.0 in Morphe.
m = meta.read_text()
assert m.count('name = "bearinmind patches"') == 1
m = m.replace('name = "bearinmind patches"', 'name = "Samsung Email 6.2.16.5 - Unified v5.2"')
meta.write_text(m)

p = props.read_text()
assert p.count("version = 2.10.0") == 1
p = p.replace("version = 2.10.0", "version = 2.10.0-samsung-email-62165-unified-v5.2")
props.write_text(p)

print("Samsung Email target: 6.2.16.5 / 621600005")
print("Fingerprints: updated to concrete Device.getConsistentDeviceId(Context)")
print("Six original non-One UI functional subpatches and extension retained.")
print("Google Play source getter shim retained as a safe no-op (no matching getters in 6.2.16.5).")
print("Store update blocker sets manifest versionCode to 2147483647; no speculative version-code bytecode rewrites.")
print("Static APK checks: SPR 54/54, method exists and remains private static.")
print("UX v5: message-view Samsung ActionMode replaced with framework entries; composer selection kept.")
print("Attachment picker: only Android ACTION_OPEN_DOCUMENT button retained; no More Apps.")

print("Morphe patch selection text: English summary of all included functions.")
