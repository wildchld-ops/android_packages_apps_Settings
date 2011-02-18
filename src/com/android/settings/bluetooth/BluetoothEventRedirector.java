/*
 * Copyright (C) 2008 The Android Open Source Project
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

import com.android.settings.R;
import com.android.settings.bluetooth.LocalBluetoothProfileManager.Profile;

import android.bluetooth.BluetoothA2dp;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothClass;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothHeadset;
import android.bluetooth.BluetoothInputDevice;
import android.bluetooth.BluetoothPan;
import android.bluetooth.BluetoothProfile;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.Handler;
import android.util.Log;

import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * BluetoothEventRedirector receives broadcasts and callbacks from the Bluetooth
 * API and dispatches the event on the UI thread to the right class in the
 * Settings.
 */
class BluetoothEventRedirector {
    private static final String TAG = "BluetoothEventRedirector";

    /* package */ final LocalBluetoothManager mManager;

    private final ThreadPoolExecutor mSerialExecutor = new ThreadPoolExecutor(
        0, 1, 1000L, TimeUnit.MILLISECONDS, new LinkedBlockingQueue<Runnable>());

    private final Handler mHandler = new Handler();

    private final BroadcastReceiver mBroadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            Log.v(TAG, "Received " + intent.getAction());

            String action = intent.getAction();
            BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);

            if (action.equals(BluetoothAdapter.ACTION_STATE_CHANGED)) {
                int state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE,
                                        BluetoothAdapter.ERROR);
                mManager.setBluetoothStateInt(state);
            } else if (action.equals(BluetoothAdapter.ACTION_DISCOVERY_STARTED)) {
                PendingResult pr = goAsync();  // so loading shared prefs doesn't kill animation
                persistDiscoveringTimestamp(pr, true);
            } else if (action.equals(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)) {
                PendingResult pr = goAsync();  // so loading shared prefs doesn't kill animation
                persistDiscoveringTimestamp(pr, false);
            } else if (action.equals(BluetoothDevice.ACTION_FOUND)) {
                short rssi = intent.getShortExtra(BluetoothDevice.EXTRA_RSSI, Short.MIN_VALUE);
                BluetoothClass btClass = intent.getParcelableExtra(BluetoothDevice.EXTRA_CLASS);
                String name = intent.getStringExtra(BluetoothDevice.EXTRA_NAME);
                // TODO Pick up UUID. They should be available for 2.1 devices.
                // Skip for now, there's a bluez problem and we are not getting uuids even for 2.1.
                mManager.getCachedDeviceManager().onDeviceAppeared(device, rssi, btClass, name);

            } else if (action.equals(BluetoothDevice.ACTION_DISAPPEARED)) {
                mManager.getCachedDeviceManager().onDeviceDisappeared(device);

            } else if (action.equals(BluetoothDevice.ACTION_NAME_CHANGED)) {
                mManager.getCachedDeviceManager().onDeviceNameUpdated(device);

            } else if (action.equals(BluetoothDevice.ACTION_BOND_STATE_CHANGED)) {
                int bondState = intent.getIntExtra(BluetoothDevice.EXTRA_BOND_STATE,
                                                   BluetoothDevice.ERROR);
                CachedBluetoothDeviceManager cachedDeviceMgr = mManager.getCachedDeviceManager();
                cachedDeviceMgr.onBondingStateChanged(device, bondState);
                if (bondState == BluetoothDevice.BOND_NONE) {
                    if (device.isBluetoothDock()) {
                        // After a dock is unpaired, we will forget the
                        // settings
                        mManager.removeDockAutoConnectSetting(device.getAddress());

                        // if the device is undocked, remove it from the list as
                        // well
                        if (!device.getAddress().equals(getDockedDeviceAddress(context))) {
                            cachedDeviceMgr.onDeviceDisappeared(device);
                        }
                    }
                    int reason = intent.getIntExtra(BluetoothDevice.EXTRA_REASON,
                            BluetoothDevice.ERROR);
                    cachedDeviceMgr.showUnbondMessage(device, reason);
                }

            } else if (action.equals(BluetoothHeadset.ACTION_CONNECTION_STATE_CHANGED)) {
                int newState = intent.getIntExtra(BluetoothProfile.EXTRA_STATE, 0);
                int oldState = intent.getIntExtra(BluetoothProfile.EXTRA_PREVIOUS_STATE, 0);
                if (newState == BluetoothProfile.STATE_DISCONNECTED &&
                        oldState == BluetoothProfile.STATE_CONNECTING) {
                    Log.i(TAG, "Failed to connect BT headset");
                }

                mManager.getCachedDeviceManager().onProfileStateChanged(device,
                    Profile.HEADSET, newState);
            } else if (action.equals(BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED)) {
                int newState = intent.getIntExtra(BluetoothProfile.EXTRA_STATE, 0);
                int oldState = intent.getIntExtra(BluetoothProfile.EXTRA_PREVIOUS_STATE, 0);
                if (newState == BluetoothProfile.STATE_DISCONNECTED &&
                        oldState == BluetoothProfile.STATE_CONNECTING) {
                    Log.i(TAG, "Failed to connect BT A2DP");
                }

                mManager.getCachedDeviceManager().onProfileStateChanged(device,
                        Profile.A2DP, newState);
            } else if (action.equals(BluetoothInputDevice.ACTION_CONNECTION_STATE_CHANGED)) {
                final int newState = intent.getIntExtra(BluetoothProfile.EXTRA_STATE, 0);
                final int oldState = intent.getIntExtra(BluetoothProfile.EXTRA_PREVIOUS_STATE, 0);
                if (newState == BluetoothProfile.STATE_DISCONNECTED &&
                        oldState == BluetoothProfile.STATE_CONNECTING) {
                    Log.i(TAG, "Failed to connect BT HID");
                }

                mManager.getCachedDeviceManager().onProfileStateChanged(device,
                        Profile.HID, newState);

            } else if (action.equals(BluetoothPan.ACTION_PAN_STATE_CHANGED)) {
                final int role = intent.getIntExtra(
                        BluetoothPan.EXTRA_LOCAL_ROLE, 0);
                if (role == BluetoothPan.LOCAL_PANU_ROLE) {
                    final int newState = intent.getIntExtra(
                            BluetoothPan.EXTRA_PAN_STATE, 0);
                    final int oldState = intent.getIntExtra(
                            BluetoothPan.EXTRA_PREVIOUS_PAN_STATE, 0);
                    if (newState == BluetoothPan.STATE_DISCONNECTED &&
                            oldState == BluetoothPan.STATE_CONNECTING) {
                        Log.i(TAG, "Failed to connect BT PAN");
                    }
                    mManager.getCachedDeviceManager().onProfileStateChanged(device,
                            Profile.PAN, newState);
                }
            } else if (action.equals(BluetoothDevice.ACTION_CLASS_CHANGED)) {
                mManager.getCachedDeviceManager().onBtClassChanged(device);

            } else if (action.equals(BluetoothDevice.ACTION_UUID)) {
                mManager.getCachedDeviceManager().onUuidChanged(device);

            } else if (action.equals(BluetoothDevice.ACTION_PAIRING_CANCEL)) {
                int errorMsg = R.string.bluetooth_pairing_error_message;
                mManager.showError(device, errorMsg);

            } else if (action.equals(Intent.ACTION_DOCK_EVENT)) {
                // Remove if unpair device upon undocking
                int anythingButUnDocked = Intent.EXTRA_DOCK_STATE_UNDOCKED + 1;
                int state = intent.getIntExtra(Intent.EXTRA_DOCK_STATE, anythingButUnDocked);
                if (state == Intent.EXTRA_DOCK_STATE_UNDOCKED) {
                    if (device != null && device.getBondState() == BluetoothDevice.BOND_NONE) {
                        mManager.getCachedDeviceManager().onDeviceDisappeared(device);
                    }
                }
            }
        }
    };

    public BluetoothEventRedirector(LocalBluetoothManager localBluetoothManager) {
        mManager = localBluetoothManager;
    }

    public void registerReceiver() {
        IntentFilter filter = new IntentFilter();

        // Bluetooth on/off broadcasts
        filter.addAction(BluetoothAdapter.ACTION_STATE_CHANGED);

        // Discovery broadcasts
        filter.addAction(BluetoothAdapter.ACTION_DISCOVERY_STARTED);
        filter.addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED);
        filter.addAction(BluetoothDevice.ACTION_DISAPPEARED);
        filter.addAction(BluetoothDevice.ACTION_FOUND);
        filter.addAction(BluetoothDevice.ACTION_NAME_CHANGED);

        // Pairing broadcasts
        filter.addAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED);
        filter.addAction(BluetoothDevice.ACTION_PAIRING_CANCEL);

        // Fine-grained state broadcasts
        filter.addAction(BluetoothPan.ACTION_PAN_STATE_CHANGED);
        filter.addAction(BluetoothInputDevice.ACTION_CONNECTION_STATE_CHANGED);
        filter.addAction(BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED);
        filter.addAction(BluetoothHeadset.ACTION_CONNECTION_STATE_CHANGED);
        filter.addAction(BluetoothDevice.ACTION_CLASS_CHANGED);
        filter.addAction(BluetoothDevice.ACTION_UUID);

        // Dock event broadcasts
        filter.addAction(Intent.ACTION_DOCK_EVENT);

        mManager.getContext().registerReceiver(mBroadcastReceiver, filter);
    }

    public void stop() {
        mManager.getContext().unregisterReceiver(mBroadcastReceiver);
    }

    // This can't be called from a broadcast receiver where the filter is set in the Manifest.
    /* package */ String getDockedDeviceAddress(Context context) {
        // This works only because these broadcast intents are "sticky"
        Intent i = context.registerReceiver(null, new IntentFilter(Intent.ACTION_DOCK_EVENT));
        if (i != null) {
            int state = i.getIntExtra(Intent.EXTRA_DOCK_STATE, Intent.EXTRA_DOCK_STATE_UNDOCKED);
            if (state != Intent.EXTRA_DOCK_STATE_UNDOCKED) {
                BluetoothDevice device = i.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                if (device != null) {
                    return device.getAddress();
                }
            }
        }
        return null;
    }

    /* package */ void persistDiscoveringTimestamp(
        final BroadcastReceiver.PendingResult pr, final boolean newState) {
        // Load the shared preferences and edit it on a background
        // thread (but serialized!), but then post back to the main
        // thread to run the onScanningStateChanged callbacks which
        // update the UI...
        mSerialExecutor.submit(new Runnable() {
                public void run() {
                    SharedPreferences.Editor editor = mManager.getSharedPreferences().edit();
                    editor.putLong(
                        LocalBluetoothManager.SHARED_PREFERENCES_KEY_DISCOVERING_TIMESTAMP,
                        System.currentTimeMillis());
                    editor.apply();
                    mHandler.post(new Runnable() {
                            public void run() {
                                mManager.onScanningStateChanged(newState);
                                pr.finish();
                            }
                        });
                }
            });
    }
}
