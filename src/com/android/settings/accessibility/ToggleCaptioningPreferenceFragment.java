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

package com.android.settings.accessibility;

import android.app.ActionBar;
import android.app.Activity;
import android.app.Fragment;
import android.content.ContentResolver;
import android.content.Context;
import android.os.Bundle;
import android.preference.PreferenceFrameLayout;
import android.provider.Settings;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.accessibility.CaptioningManager;
import android.view.accessibility.CaptioningManager.CaptionStyle;

import com.android.settings.R;
import com.android.settings.accessibility.ToggleSwitch.OnBeforeCheckedChangeListener;

import java.util.Locale;

public class ToggleCaptioningPreferenceFragment extends Fragment {
    private CaptionPropertiesFragment mPropsFragment;
    private CaptioningTextView mPreviewText;

    @Override
    public View onCreateView(
            LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        final View rootView = inflater.inflate(R.layout.captioning_preview, container, false);

        // We have to do this now because PreferenceFrameLayout looks at it
        // only when the view is added.
        if (container instanceof PreferenceFrameLayout) {
            ((PreferenceFrameLayout.LayoutParams) rootView.getLayoutParams()).removeBorders = true;
        }

        return rootView;
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        mPropsFragment = ((CaptionPropertiesFragment) getFragmentManager()
                .findFragmentById(R.id.properties_fragment));
        mPropsFragment.setParent(this);

        mPreviewText = (CaptioningTextView) view.findViewById(R.id.preview_text);

        installActionBarToggleSwitch();
        refreshPreviewText();
    }

    public void refreshPreviewText() {
        final CaptioningTextView preview = mPreviewText;
        if (preview != null) {
            final Activity activity = getActivity();
            final ContentResolver cr = activity.getContentResolver();
            final int styleId = CaptionStyle.getRawPreset(cr);
            applyCaptionProperties(preview, styleId);

            final Locale locale = CaptioningManager.getLocale(cr);
            if (locale != null) {
                final CharSequence localizedText = AccessibilityUtils.getTextForLocale(
                        activity, locale, R.string.captioning_preview_text);
                preview.setText(localizedText);
            }
        }
    }

    public static void applyCaptionProperties(CaptioningTextView previewText, int styleId) {
        previewText.setStyle(styleId);

        final Context context = previewText.getContext();
        final ContentResolver cr = context.getContentResolver();
        final float fontSize = CaptioningManager.getFontSize(cr);
        previewText.setTextSize(fontSize);

        final Locale locale = CaptioningManager.getLocale(cr);
        if (locale != null) {
            final CharSequence localizedText = AccessibilityUtils.getTextForLocale(
                    context, locale, R.string.captioning_preview_characters);
            previewText.setText(localizedText);
        }
    }

    private void installActionBarToggleSwitch() {
        final Activity activity = getActivity();
        final ToggleSwitch toggleSwitch = new ToggleSwitch(activity);

        final int padding = getResources().getDimensionPixelSize(
                R.dimen.action_bar_switch_padding);
        toggleSwitch.setPaddingRelative(0, 0, padding, 0);

        final ActionBar actionBar = activity.getActionBar();
        actionBar.setDisplayOptions(ActionBar.DISPLAY_SHOW_CUSTOM, ActionBar.DISPLAY_SHOW_CUSTOM);

        final ActionBar.LayoutParams params = new ActionBar.LayoutParams(
                ActionBar.LayoutParams.WRAP_CONTENT, ActionBar.LayoutParams.WRAP_CONTENT,
                        Gravity.CENTER_VERTICAL | Gravity.END);
        actionBar.setCustomView(toggleSwitch, params);

        final boolean enabled = CaptioningManager.isEnabled(getActivity().getContentResolver());
        mPropsFragment.getPreferenceScreen().setEnabled(enabled);
        mPreviewText.setVisibility(enabled ? View.VISIBLE : View.INVISIBLE);
        toggleSwitch.setCheckedInternal(enabled);
        toggleSwitch.setOnBeforeCheckedChangeListener(new OnBeforeCheckedChangeListener() {
            @Override
            public boolean onBeforeCheckedChanged(ToggleSwitch toggleSwitch, boolean checked) {
                toggleSwitch.setCheckedInternal(checked);
                Settings.Secure.putInt(getActivity().getContentResolver(),
                        Settings.Secure.ACCESSIBILITY_CAPTIONING_ENABLED, checked ? 1 : 0);
                mPropsFragment.getPreferenceScreen().setEnabled(checked);
                mPreviewText.setVisibility(checked ? View.VISIBLE : View.INVISIBLE);
                return false;
            }
        });
    }
}
