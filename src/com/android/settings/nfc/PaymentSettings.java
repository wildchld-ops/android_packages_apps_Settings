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

package com.android.settings.nfc;

import android.content.Context;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.PreferenceManager;
import android.preference.PreferenceScreen;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.RadioButton;
import android.widget.TextView;

import com.android.settings.R;
import com.android.settings.SettingsPreferenceFragment;
import com.android.settings.nfc.PaymentBackend.PaymentAppInfo;

import java.util.List;

public class PaymentSettings extends SettingsPreferenceFragment implements
        OnClickListener {
    public static final String TAG = "PaymentSettings";
    private LayoutInflater mInflater;
    private PaymentBackend mPaymentBackend;


    @Override
    public void onCreate(Bundle icicle) {
        super.onCreate(icicle);

        setHasOptionsMenu(false);
        mPaymentBackend = new PaymentBackend(getActivity());
        mInflater = (LayoutInflater) getSystemService(Context.LAYOUT_INFLATER_SERVICE);
    }

    public void refresh() {
        PreferenceManager manager = getPreferenceManager();
        PreferenceScreen screen = manager.createPreferenceScreen(getActivity());

        // Get all payment services
        List<PaymentAppInfo> appInfos = mPaymentBackend.getPaymentAppInfos();
        if (appInfos != null && appInfos.size() > 0) {
            // Add all payment apps
            for (PaymentAppInfo appInfo : appInfos) {
                PaymentAppPreference preference =
                        new PaymentAppPreference(getActivity(), appInfo, this);
                preference.setTitle(appInfo.caption);
                if (appInfo.banner != null) {
                    screen.addPreference(preference);
                } else {
                    // Ignore, no banner
                    Log.e(TAG, "Couldn't load banner drawable of service " + appInfo.componentName);
                }
            }
        }
        TextView emptyText = (TextView) getView().findViewById(R.id.nfc_payment_empty_text);
        ImageView emptyImage = (ImageView) getView().findViewById(R.id.nfc_payment_tap_image);
        if (screen.getPreferenceCount() == 0) {
            emptyText.setVisibility(View.VISIBLE);
            emptyImage.setVisibility(View.VISIBLE);
        } else {
            emptyText.setVisibility(View.GONE);
            emptyImage.setVisibility(View.GONE);
            setPreferenceScreen(screen);
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState) {
        super.onCreateView(inflater, container, savedInstanceState);

        View v = mInflater.inflate(R.layout.nfc_payment, container, false);

        return v;
    }

    @Override
    public void onClick(View v) {
        if (v.getTag() instanceof PaymentAppInfo) {
            PaymentAppInfo appInfo = (PaymentAppInfo) v.getTag();
            if (appInfo.componentName != null) {
                mPaymentBackend.setDefaultPaymentApp(appInfo.componentName);
            }
            refresh();
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        refresh();
    }

    public static class PaymentAppPreference extends Preference {
        private final OnClickListener listener;
        private final PaymentAppInfo appInfo;

        public PaymentAppPreference(Context context, PaymentAppInfo appInfo,
                OnClickListener listener) {
            super(context);
            setLayoutResource(R.layout.nfc_payment_option);
            this.appInfo = appInfo;
            this.listener = listener;
        }

        @Override
        protected void onBindView(View view) {
            super.onBindView(view);

            view.setOnClickListener(listener);
            view.setTag(appInfo);

            RadioButton radioButton = (RadioButton) view.findViewById(android.R.id.button1);
            radioButton.setChecked(appInfo.isDefault);

            ImageView banner = (ImageView) view.findViewById(R.id.banner);
            banner.setImageDrawable(appInfo.banner);
        }
    }
}