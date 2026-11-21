package org.ungoogled.patches.samsungemail.misc

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstruction
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.bytecodePatch
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.FiveRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
import java.util.logging.Logger

/**
 * Samsung Email 6.2.16.5 / 621600005.
 * Unlike v4/v5, this patch DOES NOT rewrite onCreateActionMode or MenuItem.
 * SemWebView stores the original framework WebView ActionMode callback in
 * mOriginActionModeCallback, then replaces it with a Samsung-specific callback.
 * Restore the original callback just before the invoke-super instruction.
 * Keep all other WebView lifecycle, composer, and attachment behavior intact.
 */
internal val samsungEmailSelectionAttachmentV62165Patch = bytecodePatch(
    description = "Restore Android text selection in received messages and retain document picking.",
) {
    execute {
        val method = Fingerprint(
            definingClass = "Lcom/samsung/android/email/ui/messageview/customwidget/webView/SemWebView;",
            name = "startActionMode",
            returnType = "Landroid/view/ActionMode;",
            parameters = listOf("Landroid/view/ActionMode\$Callback;", "I"),
        ).method
        val code = method.implementation
            ?: throw PatchException("SemWebView.startActionMode has no implementation")

        val calls = code.instructions.withIndex().filter { (_, instruction) ->
            val ref = (instruction as? ReferenceInstruction)?.reference as? MethodReference
            instruction.opcode == Opcode.INVOKE_SUPER &&
                ref?.definingClass == "Landroid/view/View;" &&
                ref.name == "startActionMode" &&
                ref.returnType == "Landroid/view/ActionMode;" &&
                ref.parameterTypes.size == 2
        }
        if (calls.size != 1) {
            throw PatchException("Expected one SemWebView invoke-super; found " + calls.size)
        }
        val (index, instruction) = calls.single()
        val invoke = instruction as? FiveRegisterInstruction
            ?: throw PatchException("Unsupported SemWebView invoke-super instruction")
        val callbackReg = invoke.registerD
        if (callbackReg !in 0..15) {
            throw PatchException("Unsupported ActionMode callback register: v$callbackReg")
        }
        method.addInstruction(
            index,
            "iget-object v$callbackReg, p0, " +
                "Lcom/samsung/android/email/ui/messageview/customwidget/webView/SemWebView;" +
                "->mOriginActionModeCallback:Landroid/view/ActionMode\$Callback;",
        )

        // Composer is intentionally NOT touched. v4/v5 modified its menu
        // and broke cut/copy/paste. This reverts that intrusion completely.
        val picker = Fingerprint(
            definingClass = "Lcom/samsung/android/email/composer/dialogs/AppPickerDialog;",
            name = "onCreate",
            returnType = "V",
            parameters = listOf("Landroid/os/Bundle;"),
        ).method
        val pickerCode = picker.implementation
            ?: throw PatchException("Attachment picker has no onCreate implementation")
        picker.addInstruction(
            pickerCode.instructions.size - 1,
            "invoke-static { p0 }, Lorg/ungoogled/samsungemail/AttachmentPicker;->install(Landroid/app/Dialog;)V",
        )
        Logger.getLogger("SamsungEmail62165UI").info(
            "Android ActionMode restored in received mail; composer menu untouched; " +
                "single Android document picker retained",
        )
    }
}
