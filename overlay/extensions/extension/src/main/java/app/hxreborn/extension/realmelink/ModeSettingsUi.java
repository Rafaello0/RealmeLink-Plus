package app.hxreborn.extension.realmelink;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;
import java.util.Locale;

/** In-app per-pair controls, bound only to the device of an observed manual selection. */
public final class ModeSettingsUi {
    private static final String BUTTON_TAG = "rlm_mode_settings_button";
    private ModeSettingsUi() {}
    private static String tr(String ua, String ru, String en) {
        String l = Locale.getDefault().getLanguage();
        return "uk".equals(l) ? ua : "ru".equals(l) ? ru : en;
    }
    public static void attach(Context context, Object device) {
        if (!(context instanceof Activity) || ModeKeeper.deviceIdentity(device).isEmpty()) return;
        Activity a = (Activity) context;
        if (a.isFinishing() || a.isDestroyed()) return;
        try {
            FrameLayout root = a.findViewById(android.R.id.content);
            if (root == null) return;
            Button button = root.findViewWithTag(BUTTON_TAG);
            if (button == null) {
                button = new Button(a);
                button.setTag(BUTTON_TAG);
                button.setText(tr("Підтримка режимів", "Сохранение режимов", "Keep modes"));
                FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                    Gravity.BOTTOM | Gravity.END);
                int margin = (int)(12 * a.getResources().getDisplayMetrics().density);
                lp.setMargins(margin, margin, margin, margin);
                root.addView(button, lp);
            }
            button.setOnClickListener(v -> show(a, device));
        } catch (RuntimeException e) {
            android.util.Log.w("RLM-Modes", "Cannot attach mode settings", e);
        }
    }
    private static void show(Activity a, Object device) {
        if (a.isFinishing() || a.isDestroyed()) return;
        LinearLayout content = new LinearLayout(a);
        content.setOrientation(LinearLayout.VERTICAL);
        int pad = (int)(20 * a.getResources().getDisplayMetrics().density);
        content.setPadding(pad, pad, pad, pad);
        TextView detail = new TextView(a);
        detail.setText(ModeKeeper.deviceIdentity(device) + "\n" + tr(
            "Виберіть потрібний режим штатними кнопками навушників у realme Link. Тут задається його автоматичне відновлення після підключення.",
            "Выберите нужный режим штатными кнопками наушников в realme Link. Здесь задаётся его автоматическое восстановление после подключения.",
            "Select the desired mode using the normal realme Link controls. Enable automatic restoration after reconnect here."));
        content.addView(detail);
        addSwitch(a, content, device, "anc", tr("Підтримувати режим шумозаглушення", "Сохранять режим шумоподавления", "Keep noise control mode"));
        addSwitch(a, content, device, "game", tr("Підтримувати ігровий режим", "Сохранять игровой режим", "Keep game mode"));
        new AlertDialog.Builder(a).setTitle(tr("Підтримка режимів", "Сохранение режимов", "Keep modes"))
            .setView(content).setPositiveButton(android.R.string.ok, null).show();
    }
    private static void addSwitch(Activity a, LinearLayout content, Object device, String mode, String title) {
        Switch control = new Switch(a);
        boolean learned = ModeKeeper.hasSelection(device, mode);
        control.setText(learned ? title : title + "\n" + tr("Спочатку виберіть режим", "Сначала выберите режим", "Select a mode first"));
        control.setEnabled(learned);
        control.setChecked(learned && ModeKeeper.isMaintained(device, mode));
        control.setOnCheckedChangeListener((button, checked) -> {
            if (!ModeKeeper.setMaintained(device, mode, checked)) {
                button.setOnCheckedChangeListener(null);
                button.setChecked(ModeKeeper.isMaintained(device, mode));
                button.setEnabled(false);
                Toast.makeText(a, tr("Не вдалося зберегти", "Не удалось сохранить", "Could not save"), Toast.LENGTH_SHORT).show();
            }
        });
        content.addView(control);
    }
}
