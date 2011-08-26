/*
 * Copyright (C) 2011 The Android Open Source Project
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

import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.PreferenceScreen;
import android.util.Log;
import android.view.textservice.SpellCheckerInfo;
import android.view.textservice.SpellCheckerSubtype;
import android.view.textservice.TextServicesManager;

import java.util.ArrayList;

public class SpellCheckersSettings extends SettingsPreferenceFragment
        implements Preference.OnPreferenceClickListener {
    private static final String TAG = SpellCheckersSettings.class.getSimpleName();
    private static final boolean DBG = false;

    private SpellCheckerInfo mCurrentSci;
    private SpellCheckerInfo[] mEnabledScis;
    private TextServicesManager mTsm;
    private final ArrayList<SingleSpellCheckerPreference> mSpellCheckers =
            new ArrayList<SingleSpellCheckerPreference>();

    @Override
    public void onCreate(Bundle icicle) {
        super.onCreate(icicle);
        mTsm = (TextServicesManager) getSystemService(Context.TEXT_SERVICES_MANAGER_SERVICE);
        addPreferencesFromResource(R.xml.spellchecker_prefs);
        updateScreen();
    }

    @Override
    public boolean onPreferenceTreeClick(PreferenceScreen screen, Preference preference) {
        return false;
    }

    @Override
    public void onResume() {
        super.onResume();
        updateScreen();
    }

    @Override
    public void onPause() {
        super.onPause();
        saveState();
    }

    private void saveState() {
        SpellCheckerUtils.setCurrentSpellChecker(mTsm, mCurrentSci);
    }

    private void updateScreen() {
        getPreferenceScreen().removeAll();
        updateEnabledSpellCheckers();
    }

    private void updateEnabledSpellCheckers() {
        final PackageManager pm = getPackageManager();
        mCurrentSci = SpellCheckerUtils.getCurrentSpellChecker(mTsm);
        mEnabledScis = SpellCheckerUtils.getEnabledSpellCheckers(mTsm);
        if (mCurrentSci == null || mEnabledScis == null) {
            return;
        }
        mSpellCheckers.clear();
        for (int i = 0; i < mEnabledScis.length; ++i) {
            final SpellCheckerInfo sci = mEnabledScis[i];
            final SingleSpellCheckerPreference scPref = new SingleSpellCheckerPreference(
                    this, null, sci, mTsm);
            mSpellCheckers.add(scPref);
            scPref.setTitle(sci.loadLabel(pm));
            scPref.setSelected(mCurrentSci != null && mCurrentSci.getId().equals(sci.getId()));
            getPreferenceScreen().addPreference(scPref);
        }
    }

    @Override
    public boolean onPreferenceClick(Preference arg0) {
        for (SingleSpellCheckerPreference scp : mSpellCheckers) {
            if (arg0.equals(scp)) {
                mTsm.setCurrentSpellChecker(scp.getSpellCheckerInfo());
            }
        }
        if (DBG) {
            Log.d(TAG, "Current spell check is "
                        + SpellCheckerUtils.getCurrentSpellChecker(mTsm).getId());
        }
        updateScreen();
        return true;
    }
}
