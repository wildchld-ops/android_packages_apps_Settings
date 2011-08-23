/*
 * Copyright (C) 2010 The Android Open Source Project
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

package com.android.settings;

import android.app.Activity;
import android.app.admin.DevicePolicyManager;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceCategory;
import android.preference.PreferenceScreen;
import android.security.KeyStore;

import com.android.internal.widget.LockPatternUtils;

public class ChooseLockGeneric extends PreferenceActivity {

    @Override
    public Intent getIntent() {
        Intent modIntent = new Intent(super.getIntent());
        modIntent.putExtra(EXTRA_SHOW_FRAGMENT, ChooseLockGenericFragment.class.getName());
        modIntent.putExtra(EXTRA_NO_HEADERS, true);
        return modIntent;
    }

    public static class ChooseLockGenericFragment extends SettingsPreferenceFragment {
        private static final int MIN_PASSWORD_LENGTH = 4;
        private static final String KEY_UNLOCK_SET_OFF = "unlock_set_off";
        private static final String KEY_UNLOCK_SET_NONE = "unlock_set_none";
        private static final String KEY_UNLOCK_SET_PIN = "unlock_set_pin";
        private static final String KEY_UNLOCK_SET_PASSWORD = "unlock_set_password";
        private static final String KEY_UNLOCK_SET_PATTERN = "unlock_set_pattern";
        private static final int CONFIRM_EXISTING_REQUEST = 100;
        private static final String PASSWORD_CONFIRMED = "password_confirmed";
        private static final String CONFIRM_CREDENTIALS = "confirm_credentials";
        public static final String MINIMUM_QUALITY_KEY = "minimum_quality";

        private ChooseLockSettingsHelper mChooseLockSettingsHelper;
        private DevicePolicyManager mDPM;
        private KeyStore mKeyStore;
        private boolean mPasswordConfirmed = false;

        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);

            mDPM = (DevicePolicyManager) getSystemService(Context.DEVICE_POLICY_SERVICE);
            mKeyStore = KeyStore.getInstance();
            mChooseLockSettingsHelper = new ChooseLockSettingsHelper(this.getActivity());

            if (savedInstanceState != null) {
                mPasswordConfirmed = savedInstanceState.getBoolean(PASSWORD_CONFIRMED);
            }

            if (!mPasswordConfirmed) {
                ChooseLockSettingsHelper helper =
                        new ChooseLockSettingsHelper(this.getActivity(), this);
                if (!helper.launchConfirmationActivity(CONFIRM_EXISTING_REQUEST, null, null)) {
                    mPasswordConfirmed = true; // no password set, so no need to confirm
                    updatePreferencesOrFinish();
                }
            } else {
                updatePreferencesOrFinish();
            }
        }

        @Override
        public boolean onPreferenceTreeClick(PreferenceScreen preferenceScreen,
                Preference preference) {
            final String key = preference.getKey();
            boolean handled = true;
            if (KEY_UNLOCK_SET_OFF.equals(key)) {
                updateUnlockMethodAndFinish(
                        DevicePolicyManager.PASSWORD_QUALITY_UNSPECIFIED, true);
            } else if (KEY_UNLOCK_SET_NONE.equals(key)) {
                updateUnlockMethodAndFinish(
                        DevicePolicyManager.PASSWORD_QUALITY_UNSPECIFIED, false);
            } else if (KEY_UNLOCK_SET_PATTERN.equals(key)) {
                updateUnlockMethodAndFinish(
                        DevicePolicyManager.PASSWORD_QUALITY_SOMETHING, false);
            } else if (KEY_UNLOCK_SET_PIN.equals(key)) {
                updateUnlockMethodAndFinish(
                        DevicePolicyManager.PASSWORD_QUALITY_NUMERIC, false);
            } else if (KEY_UNLOCK_SET_PASSWORD.equals(key)) {
                updateUnlockMethodAndFinish(
                        DevicePolicyManager.PASSWORD_QUALITY_ALPHABETIC, false);
            } else {
                handled = false;
            }
            return handled;
        }

        @Override
        public void onActivityResult(int requestCode, int resultCode, Intent data) {
            super.onActivityResult(requestCode, resultCode, data);
            if (requestCode == CONFIRM_EXISTING_REQUEST && resultCode == Activity.RESULT_OK) {
                mPasswordConfirmed = true;
                updatePreferencesOrFinish();
            } else {
                getActivity().setResult(Activity.RESULT_CANCELED);
                finish();
            }
        }

        @Override
        public void onSaveInstanceState(Bundle outState) {
            super.onSaveInstanceState(outState);
            // Saved so we don't force user to re-enter their password if configuration changes
            outState.putBoolean(PASSWORD_CONFIRMED, mPasswordConfirmed);
        }

        private void updatePreferencesOrFinish() {
            int quality = getActivity().getIntent()
                    .getIntExtra(LockPatternUtils.PASSWORD_TYPE_KEY, -1);
            if (quality == -1) {
                // If caller didn't specify password quality, show UI and allow the user to choose.
                quality = getActivity().getIntent().getIntExtra(MINIMUM_QUALITY_KEY, -1);
                quality = upgradeQuality(quality);
                final PreferenceScreen prefScreen = getPreferenceScreen();
                if (prefScreen != null) {
                    prefScreen.removeAll();
                }
                addPreferencesFromResource(R.xml.security_settings_picker);
                disableUnusablePreferences(quality);
            } else {
                updateUnlockMethodAndFinish(quality, false);
            }
        }

        private int upgradeQuality(int quality) {
            quality = upgradeQualityForDPM(quality);
            quality = upgradeQualityForEncryption(quality);
            quality = upgradeQualityForKeyStore(quality);
            return quality;
        }

        private int upgradeQualityForDPM(int quality) {
            // Compare min allowed password quality
            int minQuality = mDPM.getPasswordQuality(null);
            if (quality < minQuality) {
                quality = minQuality;
            }
            return quality;
        }

        /**
         * Mix in "encryption minimums" to any given quality value.  This prevents users
         * from downgrading the pattern/pin/password to a level below the minimums.
         *
         * ASSUMPTION:  Setting quality is sufficient (e.g. minimum lengths will be set
         * appropriately.)
         */
        private int upgradeQualityForEncryption(int quality) {
            int encryptionStatus = mDPM.getStorageEncryptionStatus();
            boolean encrypted = (encryptionStatus == DevicePolicyManager.ENCRYPTION_STATUS_ACTIVE)
                    || (encryptionStatus == DevicePolicyManager.ENCRYPTION_STATUS_ACTIVATING);
            if (encrypted) {
                if (quality < CryptKeeperSettings.MIN_PASSWORD_QUALITY) {
                    quality = CryptKeeperSettings.MIN_PASSWORD_QUALITY;
                }
            }
            return quality;
        }

        private int upgradeQualityForKeyStore(int quality) {
            if (!mKeyStore.isEmpty()) {
                if (quality < CredentialStorage.MIN_PASSWORD_QUALITY) {
                    quality = CredentialStorage.MIN_PASSWORD_QUALITY;
                }
            }
            return quality;
        }

        /***
         * Disables preferences that are less secure than required quality.
         *
         * @param quality the requested quality.
         */
        private void disableUnusablePreferences(final int quality) {
            final Preference picker =
                    getPreferenceScreen().findPreference("security_picker_category");
            final PreferenceCategory cat = (PreferenceCategory) picker;
            final int preferenceCount = cat.getPreferenceCount();
            for (int i = 0; i < preferenceCount; i++) {
                Preference pref = cat.getPreference(i);
                if (pref instanceof PreferenceScreen) {
                    final String key = ((PreferenceScreen) pref).getKey();
                    boolean enabled = true;
                    if (KEY_UNLOCK_SET_OFF.equals(key)) {
                        enabled = quality <= DevicePolicyManager.PASSWORD_QUALITY_UNSPECIFIED;
                    } else if (KEY_UNLOCK_SET_NONE.equals(key)) {
                        enabled = quality <= DevicePolicyManager.PASSWORD_QUALITY_UNSPECIFIED;
                    } else if (KEY_UNLOCK_SET_PATTERN.equals(key)) {
                        enabled = quality <= DevicePolicyManager.PASSWORD_QUALITY_SOMETHING;
                    } else if (KEY_UNLOCK_SET_PIN.equals(key)) {
                        enabled = quality <= DevicePolicyManager.PASSWORD_QUALITY_NUMERIC;
                    } else if (KEY_UNLOCK_SET_PASSWORD.equals(key)) {
                        enabled = quality <= DevicePolicyManager.PASSWORD_QUALITY_COMPLEX;
                    }
                    if (!enabled) {
                        pref.setSummary(R.string.unlock_set_unlock_disabled_summary);
                        pref.setEnabled(false);
                    }
                }
            }
        }

        /**
         * Invokes an activity to change the user's pattern, password or PIN based on given quality
         * and minimum quality specified by DevicePolicyManager. If quality is
         * {@link DevicePolicyManager#PASSWORD_QUALITY_UNSPECIFIED}, password is cleared.
         *
         * @param quality the desired quality. Ignored if DevicePolicyManager requires more security
         * @param disabled whether or not to show LockScreen at all. Only meaningful when quality is
         * {@link DevicePolicyManager#PASSWORD_QUALITY_UNSPECIFIED}
         */
        void updateUnlockMethodAndFinish(int quality, boolean disabled) {
            // Sanity check. We should never get here without confirming user's existing password.
            if (!mPasswordConfirmed) {
                throw new IllegalStateException("Tried to update password without confirming it");
            }

            quality = upgradeQuality(quality);
            if (quality >= DevicePolicyManager.PASSWORD_QUALITY_NUMERIC) {
                int minLength = mDPM.getPasswordMinimumLength(null);
                if (minLength < MIN_PASSWORD_LENGTH) {
                    minLength = MIN_PASSWORD_LENGTH;
                }
                final int maxLength = mDPM.getPasswordMaximumLength(quality);
                Intent intent = new Intent().setClass(getActivity(), ChooseLockPassword.class);
                intent.putExtra(LockPatternUtils.PASSWORD_TYPE_KEY, quality);
                intent.putExtra(ChooseLockPassword.PASSWORD_MIN_KEY, minLength);
                intent.putExtra(ChooseLockPassword.PASSWORD_MAX_KEY, maxLength);
                intent.putExtra(CONFIRM_CREDENTIALS, false);
                intent.addFlags(Intent.FLAG_ACTIVITY_FORWARD_RESULT);
                startActivity(intent);
            } else if (quality == DevicePolicyManager.PASSWORD_QUALITY_SOMETHING) {
                boolean showTutorial = !mChooseLockSettingsHelper.utils().isPatternEverChosen();
                Intent intent = new Intent();
                intent.setClass(getActivity(), showTutorial
                        ? ChooseLockPatternTutorial.class
                        : ChooseLockPattern.class);
                intent.addFlags(Intent.FLAG_ACTIVITY_FORWARD_RESULT);
                intent.putExtra("key_lock_method", "pattern");
                intent.putExtra(CONFIRM_CREDENTIALS, false);
                startActivity(intent);
            } else if (quality == DevicePolicyManager.PASSWORD_QUALITY_UNSPECIFIED) {
                mChooseLockSettingsHelper.utils().clearLock();
                mChooseLockSettingsHelper.utils().setLockScreenDisabled(disabled);
                getActivity().setResult(Activity.RESULT_OK);
            }
            finish();
        }
    }
}
