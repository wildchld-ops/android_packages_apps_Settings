/*
 * Copyright (C) 2013 The Android Open Source Project
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

package com.android.settings.users;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.UserHandle;
import android.os.UserManager;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;

import com.android.settings.R;

import java.util.List;

/**
 * Used for restricting regular users, including single-user devices.
 */
public class RestrictionSettings extends AppRestrictionsFragment {

    private static final int REQUEST_PIN_CHALLENGE = 10;

    private static final int MENU_RESET = Menu.FIRST + 1;
    private static final int MENU_CHANGE_PIN = Menu.FIRST + 2;

    private static final String KEY_CHALLENGE_SUCCEEDED = "chsc";
    private static final String KEY_CHALLENGE_REQUESTED = "chrq";

    private boolean mChallengeSucceeded;
    private boolean mChallengeRequested;
    private boolean mDisableSelf;

    public void onCreate(Bundle icicle) {
        super.onCreate(icicle);
        if (UserManager.get(getActivity()).hasUserRestriction(
                UserManager.DISALLOW_APP_RESTRICTIONS)) {
            mDisableSelf = true;
            return;
        }
        init(icicle);
        if (icicle != null) {
            mChallengeSucceeded = icicle.getBoolean(KEY_CHALLENGE_SUCCEEDED, false);
            mChallengeRequested = icicle.getBoolean(KEY_CHALLENGE_REQUESTED, false);
        }
        setHasOptionsMenu(true);
    }

    public void onResume() {
        super.onResume();
        if (!mDisableSelf) {
            ensurePin();
        }
    }

    private void ensurePin() {
        if (!mChallengeSucceeded) {
            getListView().setEnabled(false);
            final UserManager um = UserManager.get(getActivity());
            if (!mChallengeRequested) {
                if (um.hasRestrictionsPin()) {
                    Intent requestPin =
                            new Intent(Intent.ACTION_RESTRICTIONS_PIN_CHALLENGE);
                    startActivityForResult(requestPin, REQUEST_PIN_CHALLENGE);
                } else {
                    Intent requestPin =
                            new Intent("android.intent.action.RESTRICTIONS_PIN_CREATE");
                    startActivityForResult(requestPin, REQUEST_PIN_CHALLENGE);
                }
                mChallengeRequested = true;
            }
        }
        mChallengeSucceeded = false;
    }

    private void resetAndRemovePin() {
        final UserManager um = UserManager.get(getActivity());
        um.removeRestrictions();
        clearSelectedApps();
        finishFragment();
    }

    private void changePin() {
        final UserManager um = UserManager.get(getActivity());
        Intent requestPin = new Intent("android.intent.action.RESTRICTIONS_PIN_CREATE");
        startActivityForResult(requestPin, REQUEST_PIN_CHALLENGE);
    }

    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQUEST_PIN_CHALLENGE) {
            mChallengeRequested = false;
            if (resultCode == Activity.RESULT_OK) {
                getListView().setEnabled(true);
                mChallengeSucceeded = true;
            } else if (!isDetached()) {
                finishFragment();
            }
            return;
        }

        super.onActivityResult(requestCode, resultCode, data);
    }

    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);

        outState.putBoolean(KEY_CHALLENGE_REQUESTED, mChallengeRequested);
        if (getActivity().isChangingConfigurations()) {
            outState.putBoolean(KEY_CHALLENGE_SUCCEEDED, mChallengeSucceeded);
        }
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        if (!mDisableSelf) {
            menu.add(0, MENU_RESET, 0, R.string.restriction_menu_reset);
            menu.add(0, MENU_CHANGE_PIN, 0, R.string.restriction_menu_change_pin);
        }
        super.onCreateOptionsMenu(menu, inflater);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
        case MENU_RESET:
            resetAndRemovePin();
            return true;
        case MENU_CHANGE_PIN:
            changePin();
            return true;
        }

        return super.onOptionsItemSelected(item);
    }
}
