package org.ungoogled.patches.samsungemail.misc

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstruction
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.patch.PatchException
import java.util.logging.Logger

/**
 * Targets verified against Samsung Email 6.2.16.5 (build 621600005).
 * Preserve original ActionMode menu action IDs and rich HTML editor handlers.
 */
internal val samsungEmailSelectionAttachmentV62165Patch = bytecodePatch(
    description = "Uses native Android text selection in received mail and Android document picking.",
) {
    execute {
        val selectionMenus = listOf(
            "Lcom/samsung/android/email/ui/messageview/customwidget/webView/SemActionModeCallBack;",
            "Lcom/samsung/android/email/composer/htmleditor/HESelectActionModeCallback2;",
        )
        for (type in selectionMenus) {
            val method = Fingerprint(
                definingClass = type,
                name = "onCreateActionMode",
                returnType = "Z",
                parameters = listOf("Landroid/view/ActionMode;", "Landroid/view/Menu;"),
            ).method
            val implementation = method.implementation
                ?: throw PatchException("ActionMode method has no code: $type")
            val position = implementation.instructions.size - 2
            if (position < 0) throw PatchException("Unexpected ActionMode instructions: $type")
            val methodName = if (type.contains("messageview")) "repairReceived" else "repair"
            method.addInstruction(
                position,
                "invoke-static { p2 }, Lorg/ungoogled/samsungemail/SelectionMenus;->$methodName(Landroid/view/Menu;)V",
            )
        }

        val picker = Fingerprint(
            definingClass = "Lcom/samsung/android/email/composer/dialogs/AppPickerDialog;",
            name = "onCreate",
            returnType = "V",
            parameters = listOf("Landroid/os/Bundle;"),
        ).method
        val code = picker.implementation
            ?: throw PatchException("Attachment picker dialog has no onCreate code")
        picker.addInstruction(
            code.instructions.size - 1,
            "invoke-static { p0 }, Lorg/ungoogled/samsungemail/AttachmentPicker;->install(Landroid/app/Dialog;)V",
        )
        Logger.getLogger("SamsungEmail62165UI").info(
            "Received message native Android selection: 1; composer selection: 1; platform document picker retained",
        )
    }
}
