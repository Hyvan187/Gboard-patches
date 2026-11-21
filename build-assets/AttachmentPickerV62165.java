package org.ungoogled.samsungemail;

import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.Toast;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * Adds platform-backed file picking to Samsung Email's proprietary
 * AppPickerDialog. The original One UI app picker is preserved.
 *
 * The existing third-party attachment callback is used for URI results.
 * Attach mode uses request code 1011, inline insertion mode 30009,
 * matching AppPickerDialog.handle3rdPartyMediaClick in 6.2.16.5.
 */
@SuppressWarnings("unused")
public final class AttachmentPicker {
    private AttachmentPicker() {}

    private static int dp(Context context, int value) {
        return Math.round(context.getResources().getDisplayMetrics().density * value);
    }

    private static Object callback(Dialog dialog) {
        Class<?> type = dialog.getClass();
        while (type != null) {
            for (Field field : type.getDeclaredFields()) {
                if (!field.getType().getName().endsWith("$IAppPickerCallback")) continue;
                try {
                    field.setAccessible(true);
                    return field.get(dialog);
                } catch (ReflectiveOperationException | RuntimeException ignored) {
                    return null;
                }
            }
            type = type.getSuperclass();
        }
        return null;
    }

    private static boolean isAttachmentMode(Dialog dialog) {
        Class<?> type = dialog.getClass();
        while (type != null) {
            for (Field field : type.getDeclaredFields()) {
                if (field.getType() != boolean.class) continue;
                try {
                    field.setAccessible(true);
                    return field.getBoolean(dialog);
                } catch (ReflectiveOperationException | RuntimeException ignored) {
                    return true;
                }
            }
            type = type.getSuperclass();
        }
        return true;
    }

    private static void select(Dialog dialog, boolean systemDocuments) {
        Context context = dialog.getContext();
        Intent intent = new Intent(systemDocuments
                ? Intent.ACTION_OPEN_DOCUMENT : Intent.ACTION_GET_CONTENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType(isAttachmentMode(dialog) ? "*/*" : "image/*");
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        if (isAttachmentMode(dialog)) {
            intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        }

        // The same callback and request code used by Samsung Email for
        // third-party document/media providers.
        Object target = callback(dialog);
        if (target == null) {
            Toast.makeText(context, "Dateiauswahl momentan nicht verfügbar", Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            Method selected = null;
            for (Method method : target.getClass().getMethods()) {
                if (method.getName().equals("selectedApp")
                        && method.getParameterTypes().length == 2
                        && method.getParameterTypes()[0] == Intent.class
                        && method.getParameterTypes()[1] == int.class) {
                    selected = method;
                    break;
                }
            }
            if (selected == null) {
                // The callback method may be exposed only through its interface.
                for (Class<?> iface : target.getClass().getInterfaces()) {
                    try {
                        selected = iface.getMethod("selectedApp", Intent.class, int.class);
                        break;
                    } catch (NoSuchMethodException ignored) {}
                }
            }
            if (selected == null) throw new NoSuchMethodException("selectedApp");
            selected.setAccessible(true);
            selected.invoke(target, intent, isAttachmentMode(dialog) ? 1011 : 30009);
            dialog.dismiss();
        } catch (ReflectiveOperationException | RuntimeException error) {
            Toast.makeText(context, "Dateiauswahl konnte nicht geöffnet werden",
                    Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * Called after AppPickerDialog.setAppPickerDialog() created its content.
     * Adds compact platform selectors below the existing Samsung picker grid.
     */
    public static void install(Dialog dialog) {
        if (dialog == null) return;
        Window window = dialog.getWindow();
        if (window == null) return;
        View content = window.getDecorView().findViewById(android.R.id.content);
        if (!(content instanceof ViewGroup)) return;

        Context context = dialog.getContext();
        ViewGroup host = (ViewGroup) content;
        if (host.getChildCount() == 1 && host.getChildAt(0) instanceof LinearLayout) {
            host = (ViewGroup) host.getChildAt(0);
        }

        LinearLayout buttons = new LinearLayout(context);
        buttons.setOrientation(LinearLayout.HORIZONTAL);
        buttons.setPadding(dp(context, 12), dp(context, 5),
                dp(context, 12), dp(context, 8));

        Button documents = new Button(context);
        documents.setAllCaps(false);
        documents.setText("Dateien auswählen");
        documents.setOnClickListener(view -> select(dialog, true));
        buttons.addView(documents,
                new LinearLayout.LayoutParams(0, dp(context, 48), 1f));

        Button otherApps = new Button(context);
        otherApps.setAllCaps(false);
        otherApps.setText("Weitere Apps");
        otherApps.setOnClickListener(view -> select(dialog, false));
        buttons.addView(otherApps,
                new LinearLayout.LayoutParams(0, dp(context, 48), 1f));

        if (host instanceof LinearLayout) {
            ((LinearLayout) host).addView(buttons,
                    new LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT));
        } else if (host instanceof FrameLayout) {
            FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, dp(context, 64));
            params.gravity = android.view.Gravity.BOTTOM;
            ((FrameLayout) host).addView(buttons, params);
        }
    }
}
