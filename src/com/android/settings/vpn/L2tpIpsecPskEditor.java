/*
 * Copyright (C) 2009 The Android Open Source Project
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

package com.android.settings.vpn;

import com.android.settings.R;

import android.content.Context;
import android.net.vpn.L2tpIpsecPskProfile;
import android.preference.EditTextPreference;
import android.preference.Preference;
import android.preference.PreferenceGroup;

/**
 * The class for editing {@link L2tpIpsecPskProfile}.
 */
class L2tpIpsecPskEditor extends L2tpEditor {
    private EditTextPreference mPresharedKey;

    public L2tpIpsecPskEditor(L2tpIpsecPskProfile p) {
        super(p);
    }

    @Override
    protected void loadExtraPreferencesTo(PreferenceGroup subpanel) {
        Context c = subpanel.getContext();
        subpanel.addPreference(createPresharedKeyPreference(c));
        super.loadExtraPreferencesTo(subpanel);
    }

    @Override
    public String validate() {
        String result = super.validate();

        return ((result != null)
                ? result
                : validate(mPresharedKey, R.string.vpn_ipsec_presharedkey));
    }

    @Override
    public void saveSecrets(String originalProfileName) {
        L2tpIpsecPskProfile profile = (L2tpIpsecPskProfile) getProfile();
        profile.getPresharedKey();
        // TODO: fill up the implementation after keystore is available
    }

    private Preference createPresharedKeyPreference(Context c) {
        final L2tpIpsecPskProfile profile = (L2tpIpsecPskProfile) getProfile();
        mPresharedKey = createSecretPreference(c,
                R.string.vpn_ipsec_presharedkey_title,
                R.string.vpn_ipsec_presharedkey,
                profile.getPresharedKey(),
                new Preference.OnPreferenceChangeListener() {
                    public boolean onPreferenceChange(
                            Preference pref, Object newValue) {
                        profile.setPresharedKey((String) newValue);
                        setSecretSummary(mPresharedKey,
                                R.string.vpn_ipsec_presharedkey,
                                (String) newValue);
                        return true;
                    }
                });
        return mPresharedKey;
    }
}
