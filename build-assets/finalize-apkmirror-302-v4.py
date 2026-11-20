#!/usr/bin/env python3
"""Finalize the isolated APKMirror 3.0.2 patch bundle after applying v3 diffs."""
from pathlib import Path

root = Path.cwd()
patchdir = root / "patches/src/main/kotlin/app/template/patches/apkmirrorinstaller"
main = patchdir / "RemoveAdsPatch.kt"
fps = patchdir / "Fingerprints.kt"
constants = root / "patches/src/main/kotlin/app/template/patches/shared/Constants.kt"
metadata = root / "patches/build.gradle.kts"

code = main.read_text()
legacy_start = "private val stripAdManifestPatch = resourcePatch {"
modern_start = "/**\n * APKMirror Installer 3.0.2 no longer"
assert code.count(legacy_start) == 1, "Unexpected original patch start"
assert code.count(modern_start) == 1, "Missing standalone 3.0.2 patch anchor"

# Drop the entire 2.0.3 source patch and its unused manifest helper.
# The new 3.0.2 implementation and the only retained alias remain intact.
code = code[:code.index(legacy_start)] + code[code.index(modern_start):]
legacy_import = "import app.template.patches.shared.Constants.APKMIRROR_INSTALLER_COMPATIBILITY\n"
assert code.count(legacy_import) == 1
code = code.replace(legacy_import, "")

needle = '    dependsOn(legacyInstallerActivityAliasV302Patch)\n'
assert code.count(needle) == 1
code = code.replace(needle, needle +
    '    dependsOn(apkMirrorGooglePlayInstallerV302Patch)\n' +
    '    dependsOn(apkMirrorBlockPlayStoreUpdatesV302Patch)\n'
)
assert 'val removeAdsPatch =' not in code, "Old patch still exposed"
assert code.count("val unlockUltimateV302Patch =") == 1
code = code.replace(
    'description = "Experimental compatibility with APKMirror Installer 3.0.2 beta; untested on-device.",',
    'description = "Experimental APKMirror 3.0.2: Ultimate, Google Play install-source spoof and blocked Play Store updates.",',
)
main.write_text(code)

# Remove obsolete obfuscated class fingerprints that only matched version 2.0.3.
fp = fps.read_text()
modern_fp = "// APKMirror Installer 3.0.2 beta (Build 44): billing state resolver."
assert fp.count(modern_fp) == 1
fps.write_text(
    "package app.template.patches.apkmirrorinstaller\n\n" +
    "import app.morphe.patcher.Fingerprint\n\n" +
    fp[fp.index(modern_fp):]
)
assert "RewardedInstallHandlerFingerprint" not in fps.read_text()

# Remove compatibility declaration for 2.0.3. 3.0.2 remains the only APKMirror target.
c = constants.read_text()
begin = c.index("val APKMIRROR_INSTALLER_COMPATIBILITY = Compatibility(")
keep = c.index("val APKMIRROR_INSTALLER_V302_COMPATIBILITY = Compatibility(", begin)
assert begin < keep
c = c[:begin] + c[keep:]
constants.write_text(c)
assert "APKMIRROR_INSTALLER_COMPATIBILITY" not in c

# Unique Morphe source name prevents confusion with the older v2/v3 .mpp.
b = metadata.read_text()
old_name = 'name = "Doom\'s Morphe Patches"'
assert b.count(old_name) == 1
b = b.replace(old_name, 'name = "APKMirror 3.0.2 Unified v6"')
old_desc = 'description = "New mask, same task. All patches answer to Doom."'
assert b.count(old_desc) == 1
b = b.replace(old_desc,
    'description = "Experimental APKMirror Installer 3.0.2: Ultimate, install-source spoof, update blocking, localized cosmetic Unlimited."')
metadata.write_text(b)

helper = root.parent / "build-assets/ApkMirrorInstaller302Extras.kt"
assert helper.exists()
destination = patchdir / "InstallerAndUpdatesV302.kt"
destination.write_text(helper.read_text())
cosmetic = root.parent / "build-assets/ApkMirrorCosmeticUnlimitedV302.kt"
assert cosmetic.exists()
(patchdir / "CosmeticUnlimitedV302.kt").write_text(cosmetic.read_text())

source = main.read_text()
needle = "    dependsOn(apkMirrorBlockPlayStoreUpdatesV302Patch)\n"
assert source.count(needle) == 1, "Missing v4 dependencies"
source = source.replace(needle, needle + "    dependsOn(apkMirrorUnlimitedCosmeticV302Patch)\n")
main.write_text(source)


print("Standalone APKMirror 3.0.2 ready.")
print("Old APKMirror 2.0.3 unlock declaration: removed.")
print("New dependencies: Google Play installer-source spoof + Play Store update blocker.")
print("Source metadata: APKMirror 3.0.2 Unified v6.")
