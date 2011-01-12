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

package com.android.settings.bluetooth;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.util.Log;

import com.android.settings.R;

/**
 * Fragment to scan and show the discoverable devices.
 */
public class BluetoothFindNearby extends DeviceListPreferenceFragment {

    private static final String TAG = "BluetoothFindNearby";

    void addPreferencesForActivity(Activity activity) {
        addPreferencesFromResource(R.xml.device_picker);
    }

    @Override
    public void onResume() {
        super.onResume();
        if (mSelectedDevice != null) {
            CachedBluetoothDevice device =
                    mLocalManager.getCachedDeviceManager().findDevice(mSelectedDevice);
            if (device.getBondState() == BluetoothDevice.BOND_BONDED) {
                // selected device was paired, so return from this screen
                finish();
                return;
            }
        }
        mLocalManager.startScanning(true);
    }

    void onDevicePreferenceClick(BluetoothDevicePreference btPreference) {
        mLocalManager.stopScanning();
        super.onDevicePreferenceClick(btPreference);
    }

    public void onDeviceBondStateChanged(CachedBluetoothDevice cachedDevice,
            int bondState) {
        if (bondState == BluetoothDevice.BOND_BONDED) {
            // return from scan screen after successful auto-pairing
            finish();
        }
    }

    void onBluetoothStateChanged(int bluetoothState) {
        super.onBluetoothStateChanged(bluetoothState);

        if (bluetoothState == BluetoothAdapter.STATE_ON) {
                mLocalManager.startScanning(false);
        }
    }
}
