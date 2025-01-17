/*
 * Copyright (C) 2014 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.settings.notification;

import android.app.NotificationManager;
import android.app.admin.DevicePolicyManager;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.content.pm.ServiceInfo;
import android.database.ContentObserver;
import android.database.Cursor;
import android.database.sqlite.SQLiteException;
import android.media.AudioManager;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.FileUtils;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.UserHandle;
import android.os.UserManager;
import android.os.Vibrator;
import android.preference.Preference;
import android.preference.Preference.OnPreferenceChangeListener;
import android.preference.PreferenceCategory;
import android.preference.SeekBarVolumizer;
import android.preference.TwoStatePreference;
import android.provider.MediaStore;
import android.provider.OpenableColumns;
import android.provider.SearchIndexableResource;
import android.provider.Settings;
import android.util.Log;

import com.android.internal.logging.MetricsLogger;
import com.android.internal.widget.LockPatternUtils;
import com.android.settings.DropDownPreference;
import com.android.settings.R;
import com.android.settings.SettingsPreferenceFragment;
import com.android.settings.Utils;
import com.android.settings.search.BaseSearchIndexProvider;
import com.android.settings.search.Indexable;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

import android.os.SystemProperties;

public class NotificationSettings extends SettingsPreferenceFragment implements Indexable {
    private static final String TAG = "NotificationSettings";

    private static final String KEY_SOUND = "sound";
    private static final String KEY_MEDIA_VOLUME = "media_volume";
    private static final String KEY_ALARM_VOLUME = "alarm_volume";
    private static final String KEY_RING_VOLUME = "ring_volume";
    private static final String KEY_NOTIFICATION_VOLUME = "notification_volume";
    private static final String KEY_PHONE_RINGTONE = "ringtone";
    private static final String KEY_NOTIFICATION_RINGTONE = "notification_ringtone";
    private static final String KEY_VIBRATE_WHEN_RINGING = "vibrate_when_ringing";
    private static final String KEY_WIFI_DISPLAY = "wifi_display";
    private static final String KEY_NOTIFICATION = "notification";
    private static final String KEY_NOTIFICATION_PULSE = "notification_pulse";
    private static final String KEY_LOCK_SCREEN_NOTIFICATIONS = "lock_screen_notifications";
    private static final String KEY_NOTIFICATION_ACCESS = "manage_notification_access";
    private static final String KEY_ZEN_ACCESS = "manage_zen_access";
    private static final String KEY_ZEN_MODE = "zen_mode";
    private static final String KEY_BOOT_MUSIC = "boot_music";

    private static final String KEY_AUDIO_3D_SURROUND = "audio_3d_surround";

    private static final String[] RESTRICTED_KEYS = {
        KEY_MEDIA_VOLUME,
        KEY_ALARM_VOLUME,
        KEY_RING_VOLUME,
        KEY_NOTIFICATION_VOLUME,
        KEY_ZEN_ACCESS,
        KEY_ZEN_MODE,
    };

    private static final int SAMPLE_CUTOFF = 2000;  // manually cap sample playback at 2 seconds

    private final VolumePreferenceCallback mVolumeCallback = new VolumePreferenceCallback();
    private final H mHandler = new H();
    private final SettingsObserver mSettingsObserver = new SettingsObserver();
    private final Receiver mReceiver = new Receiver();
    private final ArrayList<VolumeSeekBarPreference> mVolumePrefs = new ArrayList<>();

    private Context mContext;
    private PackageManager mPM;
    private boolean mVoiceCapable;
    private Vibrator mVibrator;
    private AudioManager mAudioManager;
    private VolumeSeekBarPreference mRingOrNotificationPreference;

    private Preference mPhoneRingtonePreference;
    private Preference mNotificationRingtonePreference;
    private TwoStatePreference mVibrateWhenRinging;
    private TwoStatePreference mNotificationPulse;
    private TwoStatePreference mBootMusic;
    private TwoStatePreference mAudio3dSurround;
    private DropDownPreference mLockscreen;
    private Preference mNotificationAccess;
    private Preference mZenAccess;
    private boolean mSecure;
    private int mLockscreenSelectedValue;
    private ComponentName mSuppressor;
    private int mRingerMode = -1;

    private UserManager mUserManager;

    @Override
    protected int getMetricsCategory() {
        return MetricsLogger.NOTIFICATION;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mContext = getActivity();
        mPM = mContext.getPackageManager();
        mUserManager = UserManager.get(getContext());
        mVoiceCapable = Utils.isVoiceCapable(mContext);
        mSecure = new LockPatternUtils(getActivity()).isSecure(UserHandle.myUserId());

        mAudioManager = (AudioManager) mContext.getSystemService(Context.AUDIO_SERVICE);
        mVibrator = (Vibrator) getActivity().getSystemService(Context.VIBRATOR_SERVICE);
        if (mVibrator != null && !mVibrator.hasVibrator()) {
            mVibrator = null;
        }

        addPreferencesFromResource(R.xml.notification_settings);

        final PreferenceCategory sound = (PreferenceCategory) findPreference(KEY_SOUND);
        initVolumePreference(KEY_MEDIA_VOLUME, AudioManager.STREAM_MUSIC,
                com.android.internal.R.drawable.ic_audio_media_mute);
        initVolumePreference(KEY_ALARM_VOLUME, AudioManager.STREAM_ALARM,
                com.android.internal.R.drawable.ic_audio_alarm_mute);
        if (mVoiceCapable) {
            mRingOrNotificationPreference =
                    initVolumePreference(KEY_RING_VOLUME, AudioManager.STREAM_RING,
                            com.android.internal.R.drawable.ic_audio_ring_notif_mute);
            sound.removePreference(sound.findPreference(KEY_NOTIFICATION_VOLUME));
        } else {
            mRingOrNotificationPreference =
                    initVolumePreference(KEY_NOTIFICATION_VOLUME, AudioManager.STREAM_NOTIFICATION,
                            com.android.internal.R.drawable.ic_audio_ring_notif_mute);
            sound.removePreference(sound.findPreference(KEY_RING_VOLUME));
        }
        initRingtones(sound);
        initVibrateWhenRinging(sound);
        initUpdateBootMusic(sound);
        initUpdateAudio3dSurround(sound);

        final PreferenceCategory notification = (PreferenceCategory)
                findPreference(KEY_NOTIFICATION);
        initPulse(notification);
        initLockscreenNotifications(notification);

        mNotificationAccess = findPreference(KEY_NOTIFICATION_ACCESS);
        refreshNotificationListeners();
        mZenAccess = findPreference(KEY_ZEN_ACCESS);
        refreshZenAccess();
        updateRingerMode();
        updateEffectsSuppressor();
    }

    @Override
    public void onResume() {
        super.onResume();
        refreshNotificationListeners();
        refreshZenAccess();
        lookupRingtoneNames();
        mSettingsObserver.register(true);
        mReceiver.register(true);
        updateRingOrNotificationPreference();
        updateEffectsSuppressor();
        for (VolumeSeekBarPreference volumePref : mVolumePrefs) {
            volumePref.onActivityResume();
        }
        boolean isRestricted = mUserManager.hasUserRestriction(UserManager.DISALLOW_ADJUST_VOLUME);
        for (String key : RESTRICTED_KEYS) {
            Preference pref = findPreference(key);
            if (pref != null) {
                pref.setEnabled(!isRestricted);
            }
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        mVolumeCallback.stopSample();
        mSettingsObserver.register(false);
        mReceiver.register(false);
    }

    // === Volumes ===

    private VolumeSeekBarPreference initVolumePreference(String key, int stream, int muteIcon) {
        final VolumeSeekBarPreference volumePref = (VolumeSeekBarPreference) findPreference(key);
        volumePref.setCallback(mVolumeCallback);
        volumePref.setStream(stream);
        mVolumePrefs.add(volumePref);
        volumePref.setMuteIcon(muteIcon);
        return volumePref;
    }

    private void updateRingOrNotificationPreference() {
        mRingOrNotificationPreference.showIcon(mSuppressor != null
                ? com.android.internal.R.drawable.ic_audio_ring_notif_mute
                : mRingerMode == AudioManager.RINGER_MODE_VIBRATE || wasRingerModeVibrate()
                ? com.android.internal.R.drawable.ic_audio_ring_notif_vibrate
                : com.android.internal.R.drawable.ic_audio_ring_notif);
    }

    private boolean wasRingerModeVibrate() {
        return mVibrator != null && mRingerMode == AudioManager.RINGER_MODE_SILENT
                && mAudioManager.getLastAudibleStreamVolume(AudioManager.STREAM_RING) == 0;
    }

    private void updateRingerMode() {
        final int ringerMode = mAudioManager.getRingerModeInternal();
        if (mRingerMode == ringerMode) return;
        mRingerMode = ringerMode;
        updateRingOrNotificationPreference();
    }

    private void updateEffectsSuppressor() {
        final ComponentName suppressor = NotificationManager.from(mContext).getEffectsSuppressor();
        if (Objects.equals(suppressor, mSuppressor)) return;
        mSuppressor = suppressor;
        if (mRingOrNotificationPreference != null) {
            final String text = suppressor != null ?
                    mContext.getString(com.android.internal.R.string.muted_by,
                            getSuppressorCaption(suppressor)) : null;
            mRingOrNotificationPreference.setSuppressionText(text);
        }
        updateRingOrNotificationPreference();
    }

    private String getSuppressorCaption(ComponentName suppressor) {
        final PackageManager pm = mContext.getPackageManager();
        try {
            final ServiceInfo info = pm.getServiceInfo(suppressor, 0);
            if (info != null) {
                final CharSequence seq = info.loadLabel(pm);
                if (seq != null) {
                    final String str = seq.toString().trim();
                    if (str.length() > 0) {
                        return str;
                    }
                }
            }
        } catch (Throwable e) {
            Log.w(TAG, "Error loading suppressor caption", e);
        }
        return suppressor.getPackageName();
    }

    private final class VolumePreferenceCallback implements VolumeSeekBarPreference.Callback {
        private SeekBarVolumizer mCurrent;

        @Override
        public void onSampleStarting(SeekBarVolumizer sbv) {
            if (mCurrent != null && mCurrent != sbv) {
                mCurrent.stopSample();
            }
            mCurrent = sbv;
            if (mCurrent != null) {
                mHandler.removeMessages(H.STOP_SAMPLE);
                mHandler.sendEmptyMessageDelayed(H.STOP_SAMPLE, SAMPLE_CUTOFF);
            }
        }

        @Override
        public void onStreamValueChanged(int stream, int progress) {
            // noop
        }

        public void stopSample() {
            if (mCurrent != null) {
                mCurrent.stopSample();
            }
        }
    };


    // === Phone & notification ringtone ===

    private void initRingtones(PreferenceCategory root) {
        mPhoneRingtonePreference = root.findPreference(KEY_PHONE_RINGTONE);
        if (mPhoneRingtonePreference != null && !mVoiceCapable) {
            root.removePreference(mPhoneRingtonePreference);
            mPhoneRingtonePreference = null;
        }
        mNotificationRingtonePreference = root.findPreference(KEY_NOTIFICATION_RINGTONE);
    }

    private void lookupRingtoneNames() {
        AsyncTask.execute(mLookupRingtoneNames);
    }

    private final Runnable mLookupRingtoneNames = new Runnable() {
        @Override
        public void run() {
            if (mPhoneRingtonePreference != null) {
                final CharSequence summary = updateRingtoneName(
                        mContext, RingtoneManager.TYPE_RINGTONE);
                if (summary != null) {
                    mHandler.obtainMessage(H.UPDATE_PHONE_RINGTONE, summary).sendToTarget();
                }
            }
            if (mNotificationRingtonePreference != null) {
                final CharSequence summary = updateRingtoneName(
                        mContext, RingtoneManager.TYPE_NOTIFICATION);
                if (summary != null) {
                    mHandler.obtainMessage(H.UPDATE_NOTIFICATION_RINGTONE, summary).sendToTarget();
                }
            }
        }
    };

    private static CharSequence updateRingtoneName(Context context, int type) {
        if (context == null) {
            Log.e(TAG, "Unable to update ringtone name, no context provided");
            return null;
        }
        Uri ringtoneUri = RingtoneManager.getActualDefaultRingtoneUri(context, type);
        CharSequence summary = context.getString(com.android.internal.R.string.ringtone_unknown);
        // Is it a silent ringtone?
        if (ringtoneUri == null) {
            summary = context.getString(com.android.internal.R.string.ringtone_silent);
        } else {
            Cursor cursor = null;
            try {
                if (MediaStore.AUTHORITY.equals(ringtoneUri.getAuthority())) {
                    // Fetch the ringtone title from the media provider
                    cursor = context.getContentResolver().query(ringtoneUri,
                            new String[] { MediaStore.Audio.Media.TITLE }, null, null, null);
                } else if (ContentResolver.SCHEME_CONTENT.equals(ringtoneUri.getScheme())) {
                    cursor = context.getContentResolver().query(ringtoneUri,
                            new String[] { OpenableColumns.DISPLAY_NAME }, null, null, null);
                }
                if (cursor != null) {
                    if (cursor.moveToFirst()) {
                        summary = cursor.getString(0);
                    }
                }
            } catch (SQLiteException sqle) {
                // Unknown title for the ringtone
            } catch (IllegalArgumentException iae) {
                // Some other error retrieving the column from the provider
            } finally {
                if (cursor != null) {
                    cursor.close();
                }
            }
        }
        return summary;
    }

    // === Vibrate when ringing ===

    private void initVibrateWhenRinging(PreferenceCategory root) {
        mVibrateWhenRinging = (TwoStatePreference) root.findPreference(KEY_VIBRATE_WHEN_RINGING);
        if (mVibrateWhenRinging == null) {
            Log.i(TAG, "Preference not found: " + KEY_VIBRATE_WHEN_RINGING);
            return;
        }
        if (!mVoiceCapable) {
            root.removePreference(mVibrateWhenRinging);
            mVibrateWhenRinging = null;
            return;
        }
        mVibrateWhenRinging.setPersistent(false);
        updateVibrateWhenRinging();
        mVibrateWhenRinging.setOnPreferenceChangeListener(new OnPreferenceChangeListener() {
            @Override
            public boolean onPreferenceChange(Preference preference, Object newValue) {
                final boolean val = (Boolean) newValue;
                return Settings.System.putInt(getContentResolver(),
                        Settings.System.VIBRATE_WHEN_RINGING,
                        val ? 1 : 0);
            }
        });
    }

    private void updateVibrateWhenRinging() {
        if (mVibrateWhenRinging == null) return;
        mVibrateWhenRinging.setChecked(Settings.System.getInt(getContentResolver(),
                Settings.System.VIBRATE_WHEN_RINGING, 0) != 0);
    }

    private void initUpdateBootMusic(PreferenceCategory root) {
        mBootMusic = (TwoStatePreference) root.findPreference(KEY_BOOT_MUSIC);
        if (mBootMusic == null) {
            Log.i(TAG, "Preference not found: " + KEY_BOOT_MUSIC);
            return;
        }
        mBootMusic.setPersistent(false);
        final File musicFile = new File("/cache/nobootmusic");
        mBootMusic.setChecked(!musicFile.exists());
        //mBootMusic.setChecked(SystemProperties.getInt("persist.sys.nobootmusic", 0) == 0);
        mBootMusic.setOnPreferenceChangeListener(new OnPreferenceChangeListener() {
            @Override
            public boolean onPreferenceChange(Preference preference, Object newValue) {
                final boolean val = (Boolean) newValue;
                if (val) {
                    musicFile.delete();
                } else {
                    try {
                        musicFile.createNewFile();
                        FileUtils.setPermissions(musicFile, 0660, -1, -1);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
                //SystemProperties.set("persist.sys.nobootmusic", val? "0" : "1");
                return true;
            }
        });
    }

    // === 3D audio switch ===
    private void initUpdateAudio3dSurround(PreferenceCategory root) {
        mAudio3dSurround = (TwoStatePreference) root.findPreference(KEY_AUDIO_3D_SURROUND);
        if (mAudio3dSurround == null) {
            Log.i(TAG, "Preference not found: " + KEY_AUDIO_3D_SURROUND);
            return;
        }

        mAudio3dSurround.setPersistent(false);
        mAudio3dSurround.setChecked(SystemProperties.getInt("persist.sys.audio_3d_surround", 0) != 0);

        mAudio3dSurround.setOnPreferenceChangeListener(new OnPreferenceChangeListener() {
            @Override
            public boolean onPreferenceChange(Preference preference, Object newValue) {
                final boolean val = (Boolean) newValue;
                SystemProperties.set("persist.sys.audio_3d_surround", val ? "1" : "0");
                return true;
            }
        });
        boolean isDisplay3dSurround = this.getResources().getBoolean(
        		com.android.internal.R.bool.config_3d_surround);
        if(!isDisplay3dSurround){
               root.removePreference(mAudio3dSurround);
        }
    }

    // === Pulse notification light ===

    private void initPulse(PreferenceCategory parent) {
        mNotificationPulse = (TwoStatePreference) parent.findPreference(KEY_NOTIFICATION_PULSE);
        if (mNotificationPulse == null) {
            Log.i(TAG, "Preference not found: " + KEY_NOTIFICATION_PULSE);
            return;
        }
        if (!getResources()
                .getBoolean(com.android.internal.R.bool.config_intrusiveNotificationLed)) {
            parent.removePreference(mNotificationPulse);
        } else {
            updatePulse();
            mNotificationPulse.setOnPreferenceChangeListener(new OnPreferenceChangeListener() {
                @Override
                public boolean onPreferenceChange(Preference preference, Object newValue) {
                    final boolean val = (Boolean)newValue;
                    return Settings.System.putInt(getContentResolver(),
                            Settings.System.NOTIFICATION_LIGHT_PULSE,
                            val ? 1 : 0);
                }
            });
        }
    }

    private void updatePulse() {
        if (mNotificationPulse == null) {
            return;
        }
        try {
            mNotificationPulse.setChecked(Settings.System.getInt(getContentResolver(),
                    Settings.System.NOTIFICATION_LIGHT_PULSE) == 1);
        } catch (Settings.SettingNotFoundException snfe) {
            Log.e(TAG, Settings.System.NOTIFICATION_LIGHT_PULSE + " not found");
        }
    }

    // === Lockscreen (public / private) notifications ===

    private void initLockscreenNotifications(PreferenceCategory parent) {
        mLockscreen = (DropDownPreference) parent.findPreference(KEY_LOCK_SCREEN_NOTIFICATIONS);
        if (mLockscreen == null) {
            Log.i(TAG, "Preference not found: " + KEY_LOCK_SCREEN_NOTIFICATIONS);
            return;
        }

        boolean isSecureNotificationsDisabled = isSecureNotificationsDisabled();
        boolean isUnredactedNotificationsDisabled = isUnredactedNotificationsDisabled();
        if (!isSecureNotificationsDisabled && !isUnredactedNotificationsDisabled) {
            mLockscreen.addItem(R.string.lock_screen_notifications_summary_show,
                    R.string.lock_screen_notifications_summary_show);
        }
        if (mSecure && !isSecureNotificationsDisabled) {
            mLockscreen.addItem(R.string.lock_screen_notifications_summary_hide,
                    R.string.lock_screen_notifications_summary_hide);
        }
        mLockscreen.addItem(R.string.lock_screen_notifications_summary_disable,
                R.string.lock_screen_notifications_summary_disable);
        updateLockscreenNotifications();
        if (mLockscreen.getItemCount() > 1) {
            mLockscreen.setCallback(new DropDownPreference.Callback() {
                @Override
                public boolean onItemSelected(int pos, Object value) {
                    final int val = (Integer) value;
                    if (val == mLockscreenSelectedValue) {
                        return true;
                    }
                    final boolean enabled =
                            val != R.string.lock_screen_notifications_summary_disable;
                    final boolean show = val == R.string.lock_screen_notifications_summary_show;
                    Settings.Secure.putInt(getContentResolver(),
                            Settings.Secure.LOCK_SCREEN_ALLOW_PRIVATE_NOTIFICATIONS, show ? 1 : 0);
                    Settings.Secure.putInt(getContentResolver(),
                            Settings.Secure.LOCK_SCREEN_SHOW_NOTIFICATIONS, enabled ? 1 : 0);
                    mLockscreenSelectedValue = val;
                    return true;
                }
            });
        } else {
            // There is one or less option for the user, disable the drop down.
            mLockscreen.setEnabled(false);
        }
    }

    private boolean isSecureNotificationsDisabled() {
        final DevicePolicyManager dpm =
                (DevicePolicyManager) getSystemService(Context.DEVICE_POLICY_SERVICE);
        return dpm != null && (dpm.getKeyguardDisabledFeatures(null)
                & DevicePolicyManager.KEYGUARD_DISABLE_SECURE_NOTIFICATIONS) != 0;
    }

    private boolean isUnredactedNotificationsDisabled() {
        final DevicePolicyManager dpm =
                (DevicePolicyManager) getSystemService(Context.DEVICE_POLICY_SERVICE);
        return dpm != null && (dpm.getKeyguardDisabledFeatures(null)
                & DevicePolicyManager.KEYGUARD_DISABLE_UNREDACTED_NOTIFICATIONS) != 0;
    }

    private void updateLockscreenNotifications() {
        if (mLockscreen == null) {
            return;
        }
        final boolean enabled = getLockscreenNotificationsEnabled();
        final boolean allowPrivate = !mSecure || getLockscreenAllowPrivateNotifications();
        mLockscreenSelectedValue = !enabled ? R.string.lock_screen_notifications_summary_disable :
                allowPrivate ? R.string.lock_screen_notifications_summary_show :
                R.string.lock_screen_notifications_summary_hide;
        mLockscreen.setSelectedValue(mLockscreenSelectedValue);
    }

    private boolean getLockscreenNotificationsEnabled() {
        return Settings.Secure.getInt(getContentResolver(),
                Settings.Secure.LOCK_SCREEN_SHOW_NOTIFICATIONS, 0) != 0;
    }

    private boolean getLockscreenAllowPrivateNotifications() {
        return Settings.Secure.getInt(getContentResolver(),
                Settings.Secure.LOCK_SCREEN_ALLOW_PRIVATE_NOTIFICATIONS, 0) != 0;
    }

    // === Notification listeners ===

    private void refreshNotificationListeners() {
        if (mNotificationAccess != null) {
            final int n = NotificationAccessSettings.getEnabledListenersCount(mContext);
            if (n == 0) {
                mNotificationAccess.setSummary(getResources().getString(
                        R.string.manage_notification_access_summary_zero));
            } else {
                mNotificationAccess.setSummary(String.format(getResources().getQuantityString(
                        R.plurals.manage_notification_access_summary_nonzero,
                        n, n)));
            }
        }
    }

    // === Zen access ===

    private void refreshZenAccess() {
        // noop for now
    }

    // === Callbacks ===

    private final class SettingsObserver extends ContentObserver {
        private final Uri VIBRATE_WHEN_RINGING_URI =
                Settings.System.getUriFor(Settings.System.VIBRATE_WHEN_RINGING);
        private final Uri NOTIFICATION_LIGHT_PULSE_URI =
                Settings.System.getUriFor(Settings.System.NOTIFICATION_LIGHT_PULSE);
        private final Uri LOCK_SCREEN_PRIVATE_URI =
                Settings.Secure.getUriFor(Settings.Secure.LOCK_SCREEN_ALLOW_PRIVATE_NOTIFICATIONS);
        private final Uri LOCK_SCREEN_SHOW_URI =
                Settings.Secure.getUriFor(Settings.Secure.LOCK_SCREEN_SHOW_NOTIFICATIONS);

        public SettingsObserver() {
            super(mHandler);
        }

        public void register(boolean register) {
            final ContentResolver cr = getContentResolver();
            if (register) {
                cr.registerContentObserver(VIBRATE_WHEN_RINGING_URI, false, this);
                cr.registerContentObserver(NOTIFICATION_LIGHT_PULSE_URI, false, this);
                cr.registerContentObserver(LOCK_SCREEN_PRIVATE_URI, false, this);
                cr.registerContentObserver(LOCK_SCREEN_SHOW_URI, false, this);
            } else {
                cr.unregisterContentObserver(this);
            }
        }

        @Override
        public void onChange(boolean selfChange, Uri uri) {
            super.onChange(selfChange, uri);
            if (VIBRATE_WHEN_RINGING_URI.equals(uri)) {
                updateVibrateWhenRinging();
            }
            if (NOTIFICATION_LIGHT_PULSE_URI.equals(uri)) {
                updatePulse();
            }
            if (LOCK_SCREEN_PRIVATE_URI.equals(uri) || LOCK_SCREEN_SHOW_URI.equals(uri)) {
                updateLockscreenNotifications();
            }
        }
    }

    private final class H extends Handler {
        private static final int UPDATE_PHONE_RINGTONE = 1;
        private static final int UPDATE_NOTIFICATION_RINGTONE = 2;
        private static final int STOP_SAMPLE = 3;
        private static final int UPDATE_EFFECTS_SUPPRESSOR = 4;
        private static final int UPDATE_RINGER_MODE = 5;

        private H() {
            super(Looper.getMainLooper());
        }

        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case UPDATE_PHONE_RINGTONE:
                    mPhoneRingtonePreference.setSummary((CharSequence) msg.obj);
                    break;
                case UPDATE_NOTIFICATION_RINGTONE:
                    mNotificationRingtonePreference.setSummary((CharSequence) msg.obj);
                    break;
                case STOP_SAMPLE:
                    mVolumeCallback.stopSample();
                    break;
                case UPDATE_EFFECTS_SUPPRESSOR:
                    updateEffectsSuppressor();
                    break;
                case UPDATE_RINGER_MODE:
                    updateRingerMode();
                    break;
            }
        }
    }

    private class Receiver extends BroadcastReceiver {
        private boolean mRegistered;

        public void register(boolean register) {
            if (mRegistered == register) return;
            if (register) {
                final IntentFilter filter = new IntentFilter();
                filter.addAction(NotificationManager.ACTION_EFFECTS_SUPPRESSOR_CHANGED);
                filter.addAction(AudioManager.INTERNAL_RINGER_MODE_CHANGED_ACTION);
                mContext.registerReceiver(this, filter);
            } else {
                mContext.unregisterReceiver(this);
            }
            mRegistered = register;
        }

        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();
            if (NotificationManager.ACTION_EFFECTS_SUPPRESSOR_CHANGED.equals(action)) {
                mHandler.sendEmptyMessage(H.UPDATE_EFFECTS_SUPPRESSOR);
            } else if (AudioManager.INTERNAL_RINGER_MODE_CHANGED_ACTION.equals(action)) {
                mHandler.sendEmptyMessage(H.UPDATE_RINGER_MODE);
            }
        }
    }

    // === Indexing ===

    public static final BaseSearchIndexProvider SEARCH_INDEX_DATA_PROVIDER =
            new BaseSearchIndexProvider() {

        public List<SearchIndexableResource> getXmlResourcesToIndex(
                Context context, boolean enabled) {
            final SearchIndexableResource sir = new SearchIndexableResource(context);
            sir.xmlResId = R.xml.notification_settings;
            return Arrays.asList(sir);
        }

        public List<String> getNonIndexableKeys(Context context) {
            final ArrayList<String> rt = new ArrayList<String>();
            if (Utils.isVoiceCapable(context)) {
                rt.add(KEY_NOTIFICATION_VOLUME);
            } else {
                rt.add(KEY_RING_VOLUME);
                rt.add(KEY_PHONE_RINGTONE);
                rt.add(KEY_WIFI_DISPLAY);
                rt.add(KEY_VIBRATE_WHEN_RINGING);
                rt.add(KEY_BOOT_MUSIC);
                rt.add(KEY_AUDIO_3D_SURROUND);
            }
            return rt;
        }
    };
}
