package org.ungoogled.samsungemail;

import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.text.SpannableString;
import android.text.style.ForegroundColorSpan;
import android.view.Menu;
import android.view.MenuItem;

/**
 * Normalise Samsung's custom selection menu for non-One UI devices.
 *
 * The proprietary Samsung action-mode callbacks add drawable-only actions
 * whose foreground/icons are unreadable on some OxygenOS dark themes.
 * Keep all existing action IDs and onActionItemClicked() handlers intact.
 * Only present the existing actions as Android text entries, using system
 * localized titles and a readable text color.
 */
@SuppressWarnings("unused")
public final class SelectionMenus {
    private SelectionMenus() {}

    /**
     * Received messages use Samsung's own SemActionModeCallBack instead of the
     * composition editor. Replace its proprietary icon toolbar with regular
     * Android floating ActionMode menu actions. These IDs are verified against
     * SemActionModeCallBack.onActionItemClicked() in Samsung Email 6.2.16.5:
     *   android.R.id.copy / android.R.id.selectAll -> Samsung's copy/selectAll
     *   1 -> Samsung's shareVia; 3 -> Samsung's webSearch.
     *
     * Read-only received content must not expose Cut/Paste actions.
     * The framework still renders the native Android selection ActionMode;
     * no custom toolbar or clipboard implementation is introduced.
     */
    public static void repairReceived(Menu menu) {
        if (menu == null) return;
        final Resources resources = Resources.getSystem();
        menu.clear();

        final int primary = MenuItem.SHOW_AS_ACTION_IF_ROOM
                | MenuItem.SHOW_AS_ACTION_WITH_TEXT;
        MenuItem copy = menu.add(Menu.NONE, android.R.id.copy, 0,
                resources.getString(android.R.string.copy));
        copy.setShowAsAction(primary);

        MenuItem selectAll = menu.add(Menu.NONE, android.R.id.selectAll, 1,
                resources.getString(android.R.string.selectAll));
        selectAll.setShowAsAction(primary);

        int shareResource = resources.getIdentifier("share", "string", "android");
        String share = shareResource == 0
                ? (java.util.Locale.getDefault().getLanguage().equals("de")
                    ? "Teilen" : "Share")
                : resources.getString(shareResource);
        MenuItem shareItem = menu.add(Menu.NONE, 1, 2, share);
        shareItem.setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER);

        int searchResource = resources.getIdentifier("websearch", "string", "android");
        String webSearch = searchResource == 0
                ? (java.util.Locale.getDefault().getLanguage().equals("de")
                    ? "Websuche" : "Web search")
                : resources.getString(searchResource);
        MenuItem searchItem = menu.add(Menu.NONE, 3, 3, webSearch);
        searchItem.setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER);
    }

    public static void repair(Menu menu) {
        if (menu == null) return;
        final Resources resources = Resources.getSystem();
        final boolean dark = (resources.getConfiguration().uiMode
                & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
        final int color = dark ? Color.WHITE : Color.BLACK;
        final int primary = MenuItem.SHOW_AS_ACTION_IF_ROOM | MenuItem.SHOW_AS_ACTION_WITH_TEXT;

        for (int index = 0, count = menu.size(); index < count; index++) {
            MenuItem item = menu.getItem(index);
            if (item == null || !item.isVisible()) continue;
            char shortcut = Character.toLowerCase(item.getAlphabeticShortcut());
            String title;
            boolean important;
            switch (shortcut) {
                case 'c':
                    title = resources.getString(android.R.string.copy);
                    important = true;
                    break;
                case 'x':
                    title = resources.getString(android.R.string.cut);
                    important = true;
                    break;
                case 'v':
                    title = resources.getString(android.R.string.paste);
                    important = true;
                    break;
                case 'a':
                    title = resources.getString(android.R.string.selectAll);
                    important = true;
                    break;
                default:
                    title = item.getTitle() == null ? "" : item.getTitle().toString();
                    important = false;
                    break;
            }
            if (title.isEmpty()) continue;
            SpannableString visibleTitle = new SpannableString(title);
            visibleTitle.setSpan(new ForegroundColorSpan(color), 0, title.length(), 0);
            item.setTitle(visibleTitle);
            item.setIcon((Drawable) null);
            item.setShowAsAction(important ? primary : MenuItem.SHOW_AS_ACTION_NEVER);
        }
    }
}
