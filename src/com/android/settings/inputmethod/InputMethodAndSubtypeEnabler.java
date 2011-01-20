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
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.os.Bundle;
import android.preference.CheckBoxPreference;
import android.preference.Preference;
import android.preference.PreferenceCategory;
import android.preference.PreferenceScreen;
import android.text.TextUtils;
import android.view.inputmethod.InputMethodInfo;
import android.view.inputmethod.InputMethodManager;
import android.view.inputmethod.InputMethodSubtype;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class InputMethodAndSubtypeEnabler extends SettingsPreferenceFragment {
    private AlertDialog mDialog = null;
    private boolean mHaveHardKeyboard;
    final private HashMap<String, List<Preference>> mInputMethodAndSubtypePrefsMap =
            new HashMap<String, List<Preference>>();
    final private HashMap<String, CheckBoxPreference> mSubtypeAutoSelectionCBMap =
            new HashMap<String, CheckBoxPreference>();
    private InputMethodManager mImm;
    private List<InputMethodInfo> mInputMethodProperties;
    private String mInputMethodId;

    @Override
    public void onCreate(Bundle icicle) {
        super.onCreate(icicle);
        mImm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        Configuration config = getResources().getConfiguration();
        mHaveHardKeyboard = (config.keyboard == Configuration.KEYBOARD_QWERTY);
        mInputMethodId = getActivity().getIntent().getStringExtra(
                android.provider.Settings.EXTRA_INPUT_METHOD_ID);
        onCreateIMM();
        setPreferenceScreen(createPreferenceHierarchy());
    }

    @Override
    public void onResume() {
        super.onResume();
        InputMethodAndSubtypeUtil.loadInputMethodSubtypeList(
                this, getContentResolver(), mInputMethodProperties, mInputMethodAndSubtypePrefsMap);
        updateAutoSelectionCB();
    }

    @Override
    public void onPause() {
        super.onPause();
        // Clear all subtypes of all IMEs to make sure
        clearImplicitlyEnabledSubtypes(null);
        InputMethodAndSubtypeUtil.saveInputMethodSubtypeList(this, getContentResolver(),
                mInputMethodProperties, mHaveHardKeyboard);
    }

    @Override
    public boolean onPreferenceTreeClick(
            PreferenceScreen preferenceScreen, Preference preference) {

        if (preference instanceof CheckBoxPreference) {
            final CheckBoxPreference chkPref = (CheckBoxPreference) preference;

            for (String imiId: mSubtypeAutoSelectionCBMap.keySet()) {
                if (mSubtypeAutoSelectionCBMap.get(imiId) == chkPref) {
                    // We look for the first preference item in subtype enabler.
                    // The first item is used for turning on/off subtype auto selection.
                    // We are in the subtype enabler and trying selecting subtypes automatically.
                    setSubtypeAutoSelectionEnabled(imiId, chkPref.isChecked());
                    return super.onPreferenceTreeClick(preferenceScreen, preference);
                }
            }

            final String id = chkPref.getKey();
            if (chkPref.isChecked()) {
                InputMethodInfo selImi = null;
                final int N = mInputMethodProperties.size();
                for (int i = 0; i < N; i++) {
                    InputMethodInfo imi = mInputMethodProperties.get(i);
                    if (id.equals(imi.getId())) {
                        selImi = imi;
                        if (InputMethodAndSubtypeUtil.isSystemIme(imi)) {
                            InputMethodAndSubtypeUtil.setSubtypesPreferenceEnabled(
                                    this, mInputMethodProperties, id, true);
                            // This is a built-in IME, so no need to warn.
                            return super.onPreferenceTreeClick(preferenceScreen, preference);
                        }
                        break;
                    }
                }
                if (selImi == null) {
                    return super.onPreferenceTreeClick(preferenceScreen, preference);
                }
                chkPref.setChecked(false);
                if (mDialog == null) {
                    mDialog = (new AlertDialog.Builder(getActivity()))
                            .setTitle(android.R.string.dialog_alert_title)
                            .setIcon(android.R.drawable.ic_dialog_alert)
                            .setCancelable(true)
                            .setPositiveButton(android.R.string.ok,
                                    new DialogInterface.OnClickListener() {
                                        public void onClick(DialogInterface dialog, int which) {
                                            chkPref.setChecked(true);
                                            InputMethodAndSubtypeUtil.setSubtypesPreferenceEnabled(
                                                    InputMethodAndSubtypeEnabler.this,
                                                    mInputMethodProperties, id, true);
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
                mDialog.setMessage(getResources().getString(
                        R.string.ime_security_warning,
                        selImi.getServiceInfo().applicationInfo.loadLabel(getPackageManager())));
                mDialog.show();
            } else {
                InputMethodAndSubtypeUtil.setSubtypesPreferenceEnabled(
                        this, mInputMethodProperties, id, false);
                updateAutoSelectionCB();
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

    private void onCreateIMM() {
        InputMethodManager imm = (InputMethodManager) getSystemService(
                Context.INPUT_METHOD_SERVICE);

        // TODO: Change mInputMethodProperties to Map
        mInputMethodProperties = imm.getInputMethodList();
    }

    private PreferenceScreen createPreferenceHierarchy() {
        // Root
        PreferenceScreen root = getPreferenceManager().createPreferenceScreen(getActivity());

        int N = (mInputMethodProperties == null ? 0 : mInputMethodProperties.size());

        for (int i = 0; i < N; ++i) {
            final InputMethodInfo imi = mInputMethodProperties.get(i);
            final int subtypeCount = imi.getSubtypeCount();
            if (subtypeCount <= 1) continue;
            final String imiId = imi.getId();
            // Add this subtype to the list when no IME is specified or when the IME of this
            // subtype is the specified IME.
            if (!TextUtils.isEmpty(mInputMethodId) && !mInputMethodId.equals(imiId)) {
                continue;
            }
            PreferenceCategory keyboardSettingsCategory = new PreferenceCategory(getActivity());
            root.addPreference(keyboardSettingsCategory);
            PackageManager pm = getPackageManager();
            CharSequence label = imi.loadLabel(pm);

            keyboardSettingsCategory.setTitle(label);
            keyboardSettingsCategory.setKey(imiId);
            // TODO: Use toggle Preference if images are ready.
            CheckBoxPreference autoCB = new CheckBoxPreference(getActivity());
            autoCB.setTitle(R.string.use_system_language_to_select_input_method_subtypes);
            mSubtypeAutoSelectionCBMap.put(imiId, autoCB);
            keyboardSettingsCategory.addPreference(autoCB);

            PreferenceCategory activeInputMethodsCategory = new PreferenceCategory(getActivity());
            activeInputMethodsCategory.setTitle(R.string.active_input_method_subtypes);
            root.addPreference(activeInputMethodsCategory);

            ArrayList<Preference> subtypePreferences = new ArrayList<Preference>();
            if (subtypeCount > 0) {
                for (int j = 0; j < subtypeCount; ++j) {
                    InputMethodSubtype subtype = imi.getSubtypeAt(j);
                    CharSequence subtypeLabel;
                    int nameResId = subtype.getNameResId();
                    if (nameResId != 0) {
                        subtypeLabel = pm.getText(imi.getPackageName(), nameResId,
                                imi.getServiceInfo().applicationInfo);
                    } else {
                        String mode = subtype.getMode();
                        CharSequence language = subtype.getLocale();
                        subtypeLabel = (mode == null ? "" : mode) + ","
                                + (language == null ? "" : language);
                    }
                    CheckBoxPreference chkbxPref = new CheckBoxPreference(getActivity());
                    chkbxPref.setKey(imiId + subtype.hashCode());
                    chkbxPref.setTitle(subtypeLabel);
                    activeInputMethodsCategory.addPreference(chkbxPref);
                    subtypePreferences.add(chkbxPref);
                }
                mInputMethodAndSubtypePrefsMap.put(imiId, subtypePreferences);
            }
        }
        return root;
    }

    private boolean isNoSubtypesExplicitlySelected(String imiId) {
        boolean allSubtypesOff = true;
        final List<Preference> subtypePrefs = mInputMethodAndSubtypePrefsMap.get(imiId);
        for (Preference subtypePref: subtypePrefs) {
            if (subtypePref instanceof CheckBoxPreference
                    && ((CheckBoxPreference)subtypePref).isChecked()) {
                allSubtypesOff = false;
                break;
            }
        }
        return allSubtypesOff;
    }

    private void setSubtypeAutoSelectionEnabled(String imiId, boolean autoSelectionEnabled) {
        CheckBoxPreference autoSelectionCB = mSubtypeAutoSelectionCBMap.get(imiId);
        if (autoSelectionCB == null) return;
        autoSelectionCB.setChecked(autoSelectionEnabled);
        final List<Preference> subtypePrefs = mInputMethodAndSubtypePrefsMap.get(imiId);
        for (Preference subtypePref: subtypePrefs) {
            if (subtypePref instanceof CheckBoxPreference) {
                // When autoSelectionEnabled is true, all subtype prefs need to be disabled with
                // implicitly checked subtypes. In case of false, all subtype prefs need to be
                // enabled.
                subtypePref.setEnabled(!autoSelectionEnabled);
                if (autoSelectionEnabled) {
                    ((CheckBoxPreference)subtypePref).setChecked(false);
                }
            }
        }
        if (autoSelectionEnabled) {
            InputMethodAndSubtypeUtil.saveInputMethodSubtypeList(this, getContentResolver(),
                    mInputMethodProperties, mHaveHardKeyboard);
            setCheckedImplicitlyEnabledSubtypes(imiId);
        }
    }

    private void setCheckedImplicitlyEnabledSubtypes(String targetImiId) {
        updateImplicitlyEnabledSubtypes(targetImiId, true);
    }

    private void clearImplicitlyEnabledSubtypes(String targetImiId) {
        updateImplicitlyEnabledSubtypes(targetImiId, false);
    }

    private void updateImplicitlyEnabledSubtypes(String targetImiId, boolean check) {
        // When targetImiId is null, apply to all subtypes of all IMEs
        for (InputMethodInfo imi: mInputMethodProperties) {
            String imiId = imi.getId();
            if (targetImiId != null && !targetImiId.equals(imiId)) continue;
            final CheckBoxPreference autoCB = mSubtypeAutoSelectionCBMap.get(imiId);
            // No need to update implicitly enabled subtypes when the user has unchecked the
            // "subtype auto selection".
            if (autoCB == null || !autoCB.isChecked()) continue;
            final List<Preference> subtypePrefs = mInputMethodAndSubtypePrefsMap.get(imiId);
            final List<InputMethodSubtype> implicitlyEnabledSubtypes =
                    mImm.getEnabledInputMethodSubtypeList(imi, true);
            if (subtypePrefs == null || implicitlyEnabledSubtypes == null) continue;
            for (Preference subtypePref: subtypePrefs) {
                if (subtypePref instanceof CheckBoxPreference) {
                    CheckBoxPreference cb = (CheckBoxPreference)subtypePref;
                    cb.setChecked(false);
                    if (check) {
                        for (InputMethodSubtype subtype: implicitlyEnabledSubtypes) {
                            String implicitlyEnabledSubtypePrefKey = imiId + subtype.hashCode();
                            if (cb.getKey().equals(implicitlyEnabledSubtypePrefKey)) {
                                cb.setChecked(true);
                                break;
                            }
                        }
                    }
                }
            }
        }
    }

    private void updateAutoSelectionCB() {
        for (String imiId: mInputMethodAndSubtypePrefsMap.keySet()) {
            setSubtypeAutoSelectionEnabled(imiId, isNoSubtypesExplicitlySelected(imiId));
        }
        setCheckedImplicitlyEnabledSubtypes(null);
    }
}
