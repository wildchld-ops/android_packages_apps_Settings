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

package com.android.settings.inputmethod;

import com.android.settings.R;
import com.android.settings.SettingsPreferenceFragment;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.os.Bundle;
import android.preference.CheckBoxPreference;
import android.preference.Preference;
import android.preference.PreferenceCategory;
import android.preference.PreferenceScreen;
import android.provider.Settings;
import android.text.TextUtils;
import android.view.inputmethod.InputMethodInfo;
import android.view.inputmethod.InputMethodManager;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class InputMethodConfig extends SettingsPreferenceFragment {

    private static final String KEY_PHYSICALKEYBOARD_CATEGORY = "hardkeyboard_category";
    private static final String PHYSICALKEYBOARD_SETTINGS_FRAGMENT
            = "com.android.settings.PhysicalKeyboardSettings";

    private AlertDialog mDialog = null;
    private boolean mHaveHardKeyboard;
    // Map of imi and its preferences
    final private HashMap<String, List<Preference>> mInputMethodPrefsMap =
        new HashMap<String, List<Preference>>();
    private List<InputMethodInfo> mInputMethodProperties;


    @Override
    public void onCreate(Bundle icicle) {
        super.onCreate(icicle);
        Configuration config = getResources().getConfiguration();
        mHaveHardKeyboard = (config.keyboard == Configuration.KEYBOARD_QWERTY);
        InputMethodManager imm = (InputMethodManager) getSystemService(
                Context.INPUT_METHOD_SERVICE);

        // TODO: Change mInputMethodProperties to Map
        mInputMethodProperties = imm.getInputMethodList();
        setPreferenceScreen(createPreferenceHierarchy());
    }

    @Override
    public void onResume() {
        super.onResume();
        InputMethodAndSubtypeUtil.loadInputMethodSubtypeList(
                this, getContentResolver(), mInputMethodProperties, mInputMethodPrefsMap);
    }

    @Override
    public void onPause() {
        super.onPause();
        InputMethodAndSubtypeUtil.saveInputMethodSubtypeList(this, getContentResolver(),
                mInputMethodProperties, mHaveHardKeyboard);
    }

    private void showSecurityWarnDialog(InputMethodInfo imi, final CheckBoxPreference chkPref,
            final String imiId) {
        if (mDialog == null) {
            mDialog = (new AlertDialog.Builder(getActivity()))
                    .setTitle(android.R.string.dialog_alert_title)
                    .setIcon(android.R.drawable.ic_dialog_alert)
                    .setCancelable(true)
                    .setPositiveButton(android.R.string.ok,
                            new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int which) {
                            chkPref.setChecked(true);
                            for (Preference pref: mInputMethodPrefsMap.get(imiId)) {
                                pref.setEnabled(true);
                            }
                        }
                    })
                    .setNegativeButton(android.R.string.cancel,
                            new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int which) {
                        }
                    })
                    .create();
        } else {
            if (mDialog.isShowing()) {
                mDialog.dismiss();
            }
        }
        mDialog.setMessage(getResources().getString(R.string.ime_security_warning,
                imi.getServiceInfo().applicationInfo.loadLabel(getPackageManager())));
        mDialog.show();
    }

    private InputMethodInfo getInputMethodInfoFromImiId(String imiId) {
        final int N = mInputMethodProperties.size();
        for (int i = 0; i < N; ++i) {
            InputMethodInfo imi = mInputMethodProperties.get(i);
            if (imiId.equals(imi.getId())) {
                return imi;
            }
        }
        return null;
    }

    @Override
    public boolean onPreferenceTreeClick(
            PreferenceScreen preferenceScreen, Preference preference) {

        if (preference instanceof CheckBoxPreference) {
            final CheckBoxPreference chkPref = (CheckBoxPreference) preference;
            final String imiId = chkPref.getKey();
            if (chkPref.isChecked()) {
                InputMethodInfo selImi = getInputMethodInfoFromImiId(imiId);
                if (selImi != null) {
                    if (InputMethodAndSubtypeUtil.isSystemIme(selImi)) {
                        // This is a built-in IME, so no need to warn.
                        return super.onPreferenceTreeClick(preferenceScreen, preference);
                    }
                } else {
                    return super.onPreferenceTreeClick(preferenceScreen, preference);
                }
                chkPref.setChecked(false);
                showSecurityWarnDialog(selImi, chkPref, imiId);
            } else {
                for (Preference pref: mInputMethodPrefsMap.get(imiId)) {
                    pref.setEnabled(false);
                }
            }
        }
        return super.onPreferenceTreeClick(preferenceScreen, preference);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (mDialog != null) {
            mDialog.dismiss();
            mDialog = null;
        }
    }

    private void addHardKeyboardPreference(PreferenceScreen root) {
        PreferenceCategory keyboardSettingsCategory = new PreferenceCategory(getActivity());
        keyboardSettingsCategory.setTitle(R.string.builtin_keyboard_settings_title);
        root.addPreference(keyboardSettingsCategory);
        PreferenceScreen prefScreen = new PreferenceScreen(getActivity(), null);
        prefScreen.setKey(KEY_PHYSICALKEYBOARD_CATEGORY);
        prefScreen.setTitle(R.string.builtin_keyboard_settings_title);
        prefScreen.setSummary(R.string.builtin_keyboard_settings_summary);
        prefScreen.setFragment(PHYSICALKEYBOARD_SETTINGS_FRAGMENT);
    }

    private void addInputMethodPreference(PreferenceScreen root, InputMethodInfo imi,
            final int imiSize) {
        PreferenceCategory keyboardSettingsCategory = new PreferenceCategory(getActivity());
        root.addPreference(keyboardSettingsCategory);
        final String imiId = imi.getId();
        mInputMethodPrefsMap.put(imiId, new ArrayList<Preference>());

        PackageManager pm = getPackageManager();
        CharSequence label = imi.loadLabel(pm);
        keyboardSettingsCategory.setTitle(label);

        final boolean isSystemIME = InputMethodAndSubtypeUtil.isSystemIme(imi);
        // Add a check box for enabling/disabling IME
        CheckBoxPreference chkbxPref = new CheckBoxPreference(getActivity());
        chkbxPref.setKey(imiId);
        chkbxPref.setTitle(label);
        keyboardSettingsCategory.addPreference(chkbxPref);
        // Disable the toggle if it's the only keyboard in the system, or it's a system IME.
        if (!mHaveHardKeyboard && (imiSize <= 1 || isSystemIME)) {
            chkbxPref.setEnabled(false);
        }

        Intent intent;
        // Add subtype settings when this IME has two or more subtypes.
        PreferenceScreen prefScreen = new PreferenceScreen(getActivity(), null);
        prefScreen.setTitle(R.string.active_input_method_subtypes);
        if (imi.getSubtypes().size() > 1) {
            intent = new Intent(Settings.ACTION_INPUT_METHOD_AND_SUBTYPE_ENABLER);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                    | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED
                    | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            intent.putExtra(InputMethodAndSubtypeEnabler.EXTRA_INPUT_METHOD_ID, imiId);
            prefScreen.setIntent(intent);
            keyboardSettingsCategory.addPreference(prefScreen);
            mInputMethodPrefsMap.get(imiId).add(prefScreen);
        }

        // Add IME settings
        String settingsActivity = imi.getSettingsActivity();
        if (!TextUtils.isEmpty(settingsActivity)) {
            prefScreen = new PreferenceScreen(getActivity(), null);
            prefScreen.setTitle(R.string.input_method_settings);
            intent = new Intent(Intent.ACTION_MAIN);
            intent.setClassName(imi.getPackageName(), settingsActivity);
            prefScreen.setIntent(intent);
            keyboardSettingsCategory.addPreference(prefScreen);
            mInputMethodPrefsMap.get(imiId).add(prefScreen);
        }
    }

    private PreferenceScreen createPreferenceHierarchy() {
        // Root
        PreferenceScreen root = getPreferenceManager().createPreferenceScreen(getActivity());
        if (mHaveHardKeyboard) {
            addHardKeyboardPreference(root);
        }

        final int N = (mInputMethodProperties == null ? 0 : mInputMethodProperties.size());
        for (int i = 0; i < N; ++i) {
            addInputMethodPreference(root, mInputMethodProperties.get(i), N);
        }
        return root;
    }
}
