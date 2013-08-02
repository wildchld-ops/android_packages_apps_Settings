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

package com.android.settings;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.UserManager;

/**
 * Base class for settings activities that should be pin protected when in restricted mode.
 * The constructor for this class will take the restriction key that this screen should be
 * locked by.  If {@link UserManager.hasRestrictionsPin()} and {@link UserManager.hasUserRestriction(String)} returns true for the
 * restriction key, then the user will hav
 *
 */
public class RestrictedSettingsFragment extends SettingsPreferenceFragment {

    // Should be unique across all settings screens that use this.
    private static final int REQUEST_PIN_CHALLENGE = 12309;

    private static final String KEY_CHALLENGE_SUCCEEDED = "chsc";
    private static final String KEY_CHALLENGE_REQUESTED = "chrq";

    // If the restriction PIN is entered correctly.
    private boolean mChallengeSucceeded;
    private boolean mChallengeRequested;

    private UserManager mUserManager;

    private final String mRestrictionKey;

    public RestrictedSettingsFragment(String restrictedFlag) {
        mRestrictionKey = restrictedFlag;
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        mUserManager = (UserManager) getSystemService(Context.USER_SERVICE);
    }

    @Override
    public void onCreate(Bundle icicle) {
        super.onCreate(icicle);

        if (icicle != null) {
            mChallengeSucceeded = icicle.getBoolean(KEY_CHALLENGE_SUCCEEDED, false);
            mChallengeRequested = icicle.getBoolean(KEY_CHALLENGE_REQUESTED, false);
        }
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);

        outState.putBoolean(KEY_CHALLENGE_REQUESTED, mChallengeRequested);
        if (getActivity().isChangingConfigurations()) {
            outState.putBoolean(KEY_CHALLENGE_SUCCEEDED, mChallengeSucceeded);
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        if (mUserManager.hasUserRestriction(mRestrictionKey)
                && mUserManager.hasRestrictionsPin()) {
            ensurePin();
        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQUEST_PIN_CHALLENGE) {
            mChallengeRequested = false;
            if (resultCode == Activity.RESULT_OK) {

                mChallengeSucceeded = true;
            } else if (!isDetached()) {
                finishFragment();
            }
            return;
        }

        super.onActivityResult(requestCode, resultCode, data);
    }

    private void ensurePin() {
        if (!mChallengeSucceeded) {
            final UserManager um = UserManager.get(getActivity());
            if (!mChallengeRequested) {
                if (um.hasRestrictionsPin()) {
                    Intent requestPin =
                            new Intent(Intent.ACTION_RESTRICTIONS_PIN_CHALLENGE);
                    startActivityForResult(requestPin, REQUEST_PIN_CHALLENGE);
                    mChallengeRequested = true;
                }
            }
        }
        mChallengeSucceeded = false;
    }

    /**
     * Returns true if this activity is restricted, but no restriction pin has been set.
     * Used to determine if the settings UI should disable UI.
     */
    protected boolean isRestrictedAndNotPinProtected() {
        return mUserManager.hasUserRestriction(mRestrictionKey)
                && !mUserManager.hasRestrictionsPin();
    }
}
