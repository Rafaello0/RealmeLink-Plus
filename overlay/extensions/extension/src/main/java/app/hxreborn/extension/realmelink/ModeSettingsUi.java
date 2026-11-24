/*
 * SPDX-License-Identifier: GPL-3.0-only
 */
package app.hxreborn.extension.realmelink;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Application;
import android.app.job.JobInfo;
import android.app.job.JobParameters;
import android.app.job.JobScheduler;
import android.app.job.JobService;
import android.content.ComponentName;
import android.content.IntentSender;
import android.content.pm.PackageManager;
import android.companion.AssociationInfo;
import android.companion.AssociationRequest;
import android.companion.BluetoothDeviceFilter;
import android.companion.CompanionDeviceManager;
import android.companion.CompanionDeviceService;
import android.companion.DevicePresenceEvent;
import android.companion.ObservingDevicePresenceRequest;
import java.lang.ref.WeakReference;
import android.os.Handler;
import android.os.Looper;
import android.os.PersistableBundle;
import android.os.SystemClock;
import java.util.HashMap;
import java.util.Map;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;
import java.util.Locale;

/** Process-scoped lifecycle integration. No activity or vendor device is held globally. */
public final class ModeSettingsUi {
    private static final String TAG = "RLM-Modes";
    private static final String BUTTON_TAG = "rlm_mode_settings_button";
    private static final String SCREEN = "com.realme.iot.headset.activity.device.HeadsetDeviceActivity";
    private static boolean installed;
    private static final String PRESENCE_STORE = "rlm_companion_presence_v1";
    private static final int CONSENT_REQUEST = 0x524c;
    private ModeSettingsUi() {}

    public static synchronized void install(Context context) {
        if (installed || !(context instanceof Application)) return;
        Application app = (Application) context;
        if (Build.VERSION.SDK_INT >= 28 && !app.getPackageName().equals(Application.getProcessName())) return;
        installed = true;
        app.registerActivityLifecycleCallbacks(new Application.ActivityLifecycleCallbacks() {
            @Override public void onActivityResumed(Activity a) {
                if (SCREEN.equals(a.getClass().getName())) attach(a, null);
            }
            @Override public void onActivityCreated(Activity a, Bundle b) {}
            @Override public void onActivityStarted(Activity a) {}
            @Override public void onActivityPaused(Activity a) {}
            @Override public void onActivityStopped(Activity a) {}
            @Override public void onActivitySaveInstanceState(Activity a, Bundle b) {}
            @Override public void onActivityDestroyed(Activity a) {}
        });
    }

    /** Manifest receiver can wake a normally killed app; force-stop remains an OS restriction. */
    public static final class ReconnectReceiver extends BroadcastReceiver {
        @Override public void onReceive(Context context, Intent intent) {
            if (intent == null) return;
            String action = intent.getAction();
            if (!BluetoothDevice.ACTION_ACL_CONNECTED.equals(action)
                    && !BluetoothDevice.ACTION_ACL_DISCONNECTED.equals(action)) return;
            try {
                BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice.class);
                if (device == null) return;
                dispatchReconnect(context, device.getAddress(), BluetoothDevice.ACTION_ACL_CONNECTED.equals(action));
            } catch (RuntimeException e) {
                android.util.Log.w(TAG, "Bluetooth reconnect unavailable", e);
            }
        }
    }

    private static void dispatchReconnect(Context context, String raw, boolean connected) {
        if (raw == null) return;
        String address = raw.toUpperCase(Locale.ROOT);
        if (!ModeKeeper.activeProfile(address)) return;
        try {
            JobScheduler scheduler = context.getSystemService(JobScheduler.class);
            if (scheduler == null) {
                ModeKeeper.onBluetoothEvent(address, connected);
                return;
            }
            ComponentName service = new ComponentName(context, ReconnectJob.class);
            int id = 0x52000000 | (address.hashCode() & 0x00ffffff);
            JobInfo old = scheduler.getPendingJob(id);
            if (old != null && (!service.equals(old.getService())
                    || !address.equals(old.getExtras().getString("address")))) {
                android.util.Log.w(TAG, "Reconnect job ID collision");
                return;
            }
            if (!connected) {
                if (old != null) scheduler.cancel(id);
                ModeKeeper.onBluetoothEvent(address, false);
                return;
            }
            if (old != null) return;
            PersistableBundle extras = new PersistableBundle();
            extras.putString("address", address);
            extras.putLong("eventAt", SystemClock.elapsedRealtime());
            JobInfo job = new JobInfo.Builder(id, service).setExtras(extras)
                .setMinimumLatency(0).setOverrideDeadline(10000L).build();
            if (scheduler.schedule(job) != JobScheduler.RESULT_SUCCESS)
                android.util.Log.w(TAG, "Could not schedule headset reconnect");
            ModeKeeper.onBluetoothEvent(address, true);
        } catch (RuntimeException e) {
            android.util.Log.w(TAG, "Bluetooth reconnect unavailable", e);
        }
    }

    private static boolean presenceEnabled(Context c, String address) {
        try {
            return c.getSharedPreferences(PRESENCE_STORE, Context.MODE_PRIVATE)
                .getBoolean(address.toUpperCase(Locale.ROOT), false);
        } catch (ClassCastException e) { return false; }
    }

    private static AssociationInfo findAssociation(CompanionDeviceManager manager, String address) {
        for (AssociationInfo info : manager.getMyAssociations()) {
            if (info.getDeviceMacAddress() != null &&
                    address.equalsIgnoreCase(info.getDeviceMacAddress().toString())) return info;
        }
        return null;
    }

    private static boolean observe(Context c, CompanionDeviceManager manager, AssociationInfo info, boolean enabled) {
        if (info.getDeviceMacAddress() == null) return false;
        String address = info.getDeviceMacAddress().toString().toUpperCase(Locale.ROOT);
        try {
            if (Build.VERSION.SDK_INT >= 36) {
                ObservingDevicePresenceRequest request = new ObservingDevicePresenceRequest.Builder()
                    .setAssociationId(info.getId()).build();
                if (enabled) manager.startObservingDevicePresence(request);
                else manager.stopObservingDevicePresence(request);
            } else {
                if (enabled) manager.startObservingDevicePresence(address);
                else manager.stopObservingDevicePresence(address);
            }
            c.getSharedPreferences(PRESENCE_STORE, Context.MODE_PRIVATE).edit()
                .putBoolean(address, enabled).apply();
            return true;
        } catch (Exception | LinkageError e) {
            android.util.Log.w(TAG, "Companion presence unavailable", e);
            return false;
        }
    }

    private static void message(Context c, String ua, String ru, String en) {
        Toast.makeText(c, tr(ua, ru, en), Toast.LENGTH_LONG).show();
    }

    private static void configurePresence(Activity a, String raw) {
        String address = formatMac(raw).toUpperCase(Locale.ROOT);
        Context app = a.getApplicationContext();
        CompanionDeviceManager manager = app.getSystemService(CompanionDeviceManager.class);
        if (manager == null || !app.getPackageManager().hasSystemFeature(PackageManager.FEATURE_COMPANION_DEVICE_SETUP)) {
            message(app, "Companion API недоступний", "Companion API недоступен", "Companion API unavailable");
            return;
        }
        try {
            AssociationInfo existing = findAssociation(manager, address);
            if (existing != null) {
                boolean enabled = !presenceEnabled(app, address);
                if (observe(app, manager, existing, enabled)) {
                    if (enabled) {
                        message(app, "Фонове відновлення увімкнено", "Фоновое восстановление включено", "Background restoration enabled");
                    } else {
                        message(app, "Companion-пробудження вимкнено", "Companion-пробуждение отключено", "Companion wake disabled");
                    }
                } else message(app, "Не вдалося змінити спостереження", "Не удалось изменить наблюдение", "Could not change presence observation");
                return;
            }
            if (!ModeKeeper.activeProfile(address)) {
                message(app, "Спочатку увімкніть підтримку режиму", "Сначала включите сохранение режима", "Enable mode maintenance first");
                return;
            }
            WeakReference<Activity> owner = new WeakReference<>(a);
            AssociationRequest request = new AssociationRequest.Builder().setSingleDevice(true)
                .addDeviceFilter(new BluetoothDeviceFilter.Builder().setAddress(address).build()).build();
            manager.associate(request, app.getMainExecutor(), new CompanionDeviceManager.Callback() {
                @Override public void onAssociationPending(IntentSender sender) {
                    Activity current = owner.get();
                    if (current == null || current.isFinishing() || current.isDestroyed()) return;
                    try {
                        current.startIntentSenderForResult(sender, CONSENT_REQUEST, null, 0, 0, 0);
                    } catch (IntentSender.SendIntentException | RuntimeException e) {
                        android.util.Log.w(TAG, "Cannot open companion consent", e);
                    }
                }
                @Override public void onAssociationCreated(AssociationInfo info) {
                    if (info.getDeviceMacAddress() != null &&
                            address.equalsIgnoreCase(info.getDeviceMacAddress().toString()) &&
                            ModeKeeper.activeProfile(address) && observe(app, manager, info, true)) {
                        message(app, "Фонове відновлення увімкнено", "Фоновое восстановление включено", "Background restoration enabled");
                    } else message(app, "Не вдалося активувати комплект", "Не удалось активировать комплект", "Could not activate headset");
                }
                @Override public void onFailure(CharSequence error) {
                    message(app, "Прив’язку не завершено", "Привязка не завершена", "Association not completed");
                }
            });
        } catch (RuntimeException e) {
            android.util.Log.w(TAG, "Companion setup failed", e);
            message(app, "Прив’язка недоступна", "Привязка недоступна", "Association unavailable");
        }
    }

    /** OS-managed presence is an independent wake source; all sources share the same job. */
    public static final class PresenceService extends CompanionDeviceService {
        private void event(AssociationInfo info, boolean connected) {
            if (info == null || info.getDeviceMacAddress() == null) return;
            String address = info.getDeviceMacAddress().toString().toUpperCase(Locale.ROOT);
            if (presenceEnabled(this, address)) dispatchReconnect(this, address, connected);
        }
        @Override public void onDeviceAppeared(AssociationInfo info) { event(info, true); }
        @Override public void onDeviceDisappeared(AssociationInfo info) { event(info, false); }
        @Override public void onDevicePresenceEvent(DevicePresenceEvent event) {
            if (Build.VERSION.SDK_INT < 36 || event == null) return;
            int kind = event.getEvent();
            if (kind != DevicePresenceEvent.EVENT_BT_CONNECTED && kind != DevicePresenceEvent.EVENT_BT_DISCONNECTED &&
                    kind != DevicePresenceEvent.EVENT_BLE_APPEARED && kind != DevicePresenceEvent.EVENT_BLE_DISAPPEARED) return;
            CompanionDeviceManager manager = getSystemService(CompanionDeviceManager.class);
            if (manager == null) return;
            try {
                for (AssociationInfo info : manager.getMyAssociations()) {
                    if (info.getId() == event.getAssociationId()) {
                        event(info, kind == DevicePresenceEvent.EVENT_BT_CONNECTED || kind == DevicePresenceEvent.EVENT_BLE_APPEARED);
                        break;
                    }
                }
            } catch (RuntimeException e) { android.util.Log.w(TAG, "Presence event unavailable", e); }
        }
    }

    /** Short bounded JobService keeps cold-start work alive beyond receiver lifetime. */
    public static final class ReconnectJob extends JobService {
        private final Handler main = new Handler(Looper.getMainLooper());
        private final Map<Integer, Work> jobs = new HashMap<>();
        private static final class Work {
            final JobParameters params;
            Thread worker;
            Runnable timeout;
            Work(JobParameters params) { this.params = params; }
        }
        @Override public boolean onStartJob(JobParameters params) {
            String address = params.getExtras().getString("address");
            long age = SystemClock.elapsedRealtime() - params.getExtras().getLong("eventAt", -1);
            if (age < 0 || age > 30000L || !ModeKeeper.activeProfile(address)) return false;
            if (jobs.containsKey(params.getJobId())) return false;
            Work work = new Work(params);
            jobs.put(params.getJobId(), work);
            work.timeout = () -> finish(work);
            work.worker = new Thread(() -> {
                Object device = ModeKeeper.resolveSavedDevice(address);
                main.post(() -> {
                    if (jobs.get(params.getJobId()) != work) return;
                    if (device == null || !ModeKeeper.activeProfile(address)) {
                        finish(work);
                        return;
                    }
                    ModeKeeper.resumeSavedDevice(device);
                });
            }, "RLM-device-lookup");
            main.postDelayed(work.timeout, 35000L);
            work.worker.start();
            return true;
        }
        private void finish(Work work) {
            if (jobs.get(work.params.getJobId()) != work) return;
            jobs.remove(work.params.getJobId());
            main.removeCallbacks(work.timeout);
            work.worker.interrupt();
            jobFinished(work.params, false);
        }
        @Override public boolean onStopJob(JobParameters params) {
            Work work = jobs.remove(params.getJobId());
            if (work != null) {
                main.removeCallbacks(work.timeout);
                work.worker.interrupt();
            }
            return false;
        }
        @Override public void onDestroy() {
            for (Work work : jobs.values()) {
                main.removeCallbacks(work.timeout);
                work.worker.interrupt();
            }
            jobs.clear();
            super.onDestroy();
        }
    }

    private static String tr(String ua, String ru, String en) {
        String l = Locale.getDefault().getLanguage();
        return "uk".equals(l) ? ua : "ru".equals(l) ? ru : en;
    }
    private static String title() {
        return tr("Підтримка режимів", "Сохранение режимов", "Keep modes");
    }
    public static void attach(Context context, Object device) {
        if (!(context instanceof Activity)) return;
        Activity a = (Activity) context;
        if (a.isFinishing() || a.isDestroyed()) return;
        try {
            FrameLayout root = a.findViewById(android.R.id.content);
            if (root == null) return;
            Button button = root.findViewWithTag(BUTTON_TAG);
            if (button == null) {
                button = new Button(a);
                button.setTag(BUTTON_TAG);
                button.setText(title());
                FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                    Gravity.BOTTOM | Gravity.END);
                int margin = (int)(12 * a.getResources().getDisplayMetrics().density);
                lp.setMargins(margin, margin, margin, margin);
                root.addView(button, lp);
            }
            // Never bind a newly opened screen to whichever headset connected last.
            button.setOnClickListener(v -> showProfiles(a));
        } catch (RuntimeException e) {
            android.util.Log.w(TAG, "Cannot attach mode settings", e);
        }
    }

    private static void showProfiles(Activity a) {
        if (a.isFinishing() || a.isDestroyed()) return;
        String[] profiles = ModeKeeper.savedProfiles();
        if (profiles.length == 0) {
            new AlertDialog.Builder(a).setTitle(title()).setMessage(tr(
                "Спочатку виберіть потрібні режими штатними кнопками навушників. Після цього комплект буде доступний тут.",
                "Сначала выберите нужные режимы штатными кнопками наушников. После этого комплект будет доступен здесь.",
                "Select the desired modes using the normal headset controls first. The headset will then appear here."))
                .setPositiveButton(android.R.string.ok, null).show();
            return;
        }
        String[] labels = new String[profiles.length];
        for (int i = 0; i < profiles.length; i++) labels[i] = formatMac(profiles[i]);
        new AlertDialog.Builder(a).setTitle(tr("Виберіть комплект", "Выберите комплект", "Choose headset"))
            .setItems(labels, (dialog, index) -> show(a, profiles[index]))
            .setNegativeButton(android.R.string.cancel, null).show();
    }
    private static String formatMac(String address) {
        return address.replaceAll("(?<=\\G..)(?!$)", ":");
    }
    private static void show(Activity a, String address) {
        if (a.isFinishing() || a.isDestroyed()) return;
        LinearLayout content = new LinearLayout(a);
        content.setOrientation(LinearLayout.VERTICAL);
        int pad = (int)(20 * a.getResources().getDisplayMetrics().density);
        content.setPadding(pad, pad, pad, pad);
        TextView detail = new TextView(a);
        detail.setText(formatMac(address) + "\n" + tr(
            "Виберіть потрібний режим штатними кнопками навушників у realme Link. Тут задається його автоматичне відновлення після підключення.",
            "Выберите нужный режим штатными кнопками наушников в realme Link. Здесь задаётся его автоматическое восстановление после подключения.",
            "Select the desired mode using the normal realme Link controls. Enable automatic restoration after reconnect here."));
        content.addView(detail);
        addSwitch(a, content, address, "anc", tr("Підтримувати режим шумозаглушення", "Сохранять режим шумоподавления", "Keep noise control mode"));
        addSwitch(a, content, address, "game", tr("Підтримувати ігровий режим", "Сохранять игровой режим", "Keep game mode"));
        Button companion = new Button(a);
        boolean enabled = presenceEnabled(a, formatMac(address));
        companion.setText(enabled
            ? tr("Вимкнути Companion-пробудження", "Отключить Companion-пробуждение", "Disable companion wake")
            : tr("Дозволити фонове відновлення", "Разрешить фоновое восстановление", "Allow background restoration"));
        companion.setOnClickListener(v -> configurePresence(a, address));
        content.addView(companion);
        TextView hint = new TextView(a);
        hint.setText(tr("Одноразове системне підтвердження для цього комплекту. Резервне відновлення через Bluetooth працює незалежно.",
            "Однократное системное подтверждение для этого комплекта. Резервное восстановление через Bluetooth работает независимо.",
            "One-time system consent for this headset. Bluetooth fallback remains independent."));
        content.addView(hint);
        ScrollView scroll = new ScrollView(a);
        scroll.addView(content);
        new AlertDialog.Builder(a).setTitle(title()).setView(scroll)
            .setPositiveButton(android.R.string.ok, null).show();
    }
    private static void addSwitch(Activity a, LinearLayout content, String address, String mode, String title) {
        if (!ModeKeeper.modeAvailable(mode)) return;
        Switch control = new Switch(a);
        boolean learned = ModeKeeper.hasSelectionFor(address, mode);
        control.setText(learned ? title : title + "\n" + tr("Спочатку виберіть режим", "Сначала выберите режим", "Select a mode first"));
        control.setEnabled(learned);
        control.setChecked(learned && ModeKeeper.isMaintainedFor(address, mode));
        control.setOnCheckedChangeListener((button, checked) -> {
            if (!ModeKeeper.setMaintainedFor(address, mode, checked)) {
                button.setOnCheckedChangeListener(null);
                button.setChecked(ModeKeeper.isMaintainedFor(address, mode));
                button.setEnabled(false);
                Toast.makeText(a, tr("Не вдалося зберегти", "Не удалось сохранить", "Could not save"), Toast.LENGTH_SHORT).show();
            }
        });
        content.addView(control);
    }
}
