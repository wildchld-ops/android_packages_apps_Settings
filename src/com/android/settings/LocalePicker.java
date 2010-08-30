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
import android.app.ActivityManagerNative;
import android.app.IActivityManager;
import android.app.ListFragment;
import android.app.backup.BackupManager;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.os.Bundle;
import android.os.RemoteException;
import android.util.Log;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.ListView;

import java.text.Collator;
import java.util.Arrays;
import java.util.Locale;

public class LocalePicker extends ListFragment {
    private static final String TAG = "LocalePicker";
    private static final boolean DEBUG = false;

    Loc[] mLocales;
    String[] mSpecialLocaleCodes;
    String[] mSpecialLocaleNames;

    Activity mActivity;

    private static class Loc implements Comparable<Loc> {
        static Collator sCollator = Collator.getInstance();

        String label;
        Locale locale;

        public Loc(String label, Locale locale) {
            this.label = label;
            this.locale = locale;
        }

        @Override
        public String toString() {
            return this.label;
        }

        @Override
        public int compareTo(Loc another) {
            return sCollator.compare(this.label, another.label);
        }
    }

    private void setUpLocaleList() {
        final Resources resources = mActivity.getResources();
        mSpecialLocaleCodes = resources.getStringArray(R.array.special_locale_codes);
        mSpecialLocaleNames = resources.getStringArray(R.array.special_locale_names);

        final String[] locales = mActivity.getAssets().getLocales();
        Arrays.sort(locales);
        final int origSize = locales.length;
        Loc[] preprocess = new Loc[origSize];
        int finalSize = 0;
        for (int i = 0 ; i < origSize; i++ ) {
            String s = locales[i];
            int len = s.length();
            if (len == 5) {
                String language = s.substring(0, 2);
                String country = s.substring(3, 5);
                Locale l = new Locale(language, country);

                if (finalSize == 0) {
                    if (DEBUG) {
                        Log.v(TAG, "adding initial "+ toTitleCase(l.getDisplayLanguage(l)));
                    }
                    preprocess[finalSize++] =
                            new Loc(toTitleCase(l.getDisplayLanguage(l)), l);
                } else {
                    // check previous entry:
                    //  same lang and a country -> upgrade to full name and
                    //    insert ours with full name
                    //  diff lang -> insert ours with lang-only name
                    if (preprocess[finalSize-1].locale.getLanguage().equals(
                            language)) {
                        if (DEBUG) {
                            Log.v(TAG, "backing up and fixing "+
                                    preprocess[finalSize-1].label+" to "+
                                    getDisplayName(preprocess[finalSize-1].locale));
                        }
                        preprocess[finalSize-1].label = toTitleCase(
                                getDisplayName(preprocess[finalSize-1].locale));
                        if (DEBUG) {
                            Log.v(TAG, "  and adding "+ toTitleCase(getDisplayName(l)));
                        }
                        preprocess[finalSize++] =
                                new Loc(toTitleCase(getDisplayName(l)), l);
                    } else {
                        String displayName;
                        if (s.equals("zz_ZZ")) {
                            displayName = "Pseudo...";
                        } else {
                            displayName = toTitleCase(l.getDisplayLanguage(l));
                        }
                        if (DEBUG) {
                            Log.v(TAG, "adding "+displayName);
                        }
                        preprocess[finalSize++] = new Loc(displayName, l);
                    }
                }
            }
        }
        mLocales = new Loc[finalSize];
        for (int i = 0; i < finalSize ; i++) {
            mLocales[i] = preprocess[i];
        }
        Arrays.sort(mLocales);
        final int layoutId = R.layout.locale_picker_item;
        final int fieldId = R.id.locale;
        final ArrayAdapter<Loc> adapter =
                new ArrayAdapter<Loc>(mActivity, layoutId, fieldId, mLocales);
        setListAdapter(adapter);
    }

    @Override
    public void onActivityCreated(final Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        mActivity = getActivity();
        setUpLocaleList();
    }

    private static String toTitleCase(String s) {
        if (s.length() == 0) {
            return s;
        }

        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    private String getDisplayName(Locale l) {
        String code = l.toString();

        for (int i = 0; i < mSpecialLocaleCodes.length; i++) {
            if (mSpecialLocaleCodes[i].equals(code)) {
                return mSpecialLocaleNames[i];
            }
        }

        return l.getDisplayName(l);
    }

    @Override
    public void onResume() {
        super.onResume();
        getListView().requestFocus();
    }

    @Override
    public void onListItemClick(ListView l, View v, int position, long id) {
        try {
            IActivityManager am = ActivityManagerNative.getDefault();
            Configuration config = am.getConfiguration();

            Loc loc = mLocales[position];
            config.locale = loc.locale;

            // indicate this isn't some passing default - the user wants this remembered
            config.userSetLocale = true;

            am.updateConfiguration(config);
            // Trigger the dirty bit for the Settings Provider.
            BackupManager.dataChanged("com.android.providers.settings");
        } catch (RemoteException e) {
            // Intentionally left blank
        }

        mActivity.finish();
    }
}