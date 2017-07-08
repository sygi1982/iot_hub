/*******************************************************************************
 * Copyright (c) 2016 Grzegorz Sygieda
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 *******************************************************************************/

package com.sygmi;

import android.app.Service;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.NetworkInfo;
import android.net.wifi.WifiManager;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.util.Log;

public class EndpointStateService extends Service {

    private static final String TAG = "EndpointStateService";

    public static final String ENDPOINT_DISCOVERED = ".endpointDiscovered";
    public static final String ENDPOINT_LOST = ".endpointLost";
    public static final String ENDPOINT_TYPE = ".eventType";

    public static final String DEVICE_WIFI = "WIFI";
    public static final String DEVICE_BLUETOOTH = "BT";
    public static final String DEVICE_FAKE = "FAKE";

    private static final int MSG_ENDPOINT_DISCOVERED = 0;
    private static final int MSG_ENDPOINT_LOST = 1;

    private String mEndpointActive = null;

    @Override
    public void onCreate() {

        super.onCreate();

        Log.d(TAG, "onCreate");

        IntentFilter devFilter = new IntentFilter();
        devFilter.addAction(BluetoothDevice.ACTION_FOUND);
        devFilter.addAction(BluetoothDevice.ACTION_ACL_CONNECTED);
        devFilter.addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED);
        devFilter.addAction(WifiManager.NETWORK_STATE_CHANGED_ACTION);

        registerReceiver(mReceiver, devFilter);
    }

    @Override
    public void onDestroy() {

        Log.d(TAG, "onDestroy");

        unregisterReceiver(mReceiver);

        super.onDestroy();
    }

    private final Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {

            String data = (String)msg.obj;

            Intent intent = new Intent();

            switch(msg.what) {
                case MSG_ENDPOINT_DISCOVERED:
                    Log.d(TAG, "Endpoint discovered " + data);
                    intent.setAction(ENDPOINT_DISCOVERED);
                    mEndpointActive = data;
                    break;
                case MSG_ENDPOINT_LOST:
                    Log.d(TAG, "Endpoint lost " + data);
                    if (mEndpointActive != null &&
                            mEndpointActive.equals(data)) {
                        intent.setAction(ENDPOINT_LOST);
                        mEndpointActive = null;
                    }
                    break;
            }

            if (intent.getAction() != null) {
                intent.putExtra(ENDPOINT_TYPE, data);
                sendBroadcast(intent);
            }
        }
    };

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();

            if (BluetoothDevice.ACTION_ACL_CONNECTED.equals(action)) {
                BluetoothDevice btDevice = (BluetoothDevice) intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                if (btDevice != null) {
                    if (btDevice.getName() != null &&
                            btDevice.getName().compareTo(BluetoothDriver.DEVICE_NAME) == 0) {
                        Log.d(TAG, "Bluetoooth device attached");
                        mHandler.obtainMessage(MSG_ENDPOINT_DISCOVERED, DEVICE_BLUETOOTH).sendToTarget();
                    }
                }
            }

            if (BluetoothDevice.ACTION_ACL_DISCONNECTED.equals(action)) {
                BluetoothDevice btDevice = (BluetoothDevice) intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                if (btDevice != null) {
                    if (btDevice.getName() != null &&
                            btDevice.getName().compareTo(BluetoothDriver.DEVICE_NAME) == 0) {
                        Log.d(TAG, "Bluetoooth device dettached");
                        mHandler.obtainMessage(MSG_ENDPOINT_LOST, DEVICE_BLUETOOTH).sendToTarget();
                    }
                }
            }

            if (WifiManager.NETWORK_STATE_CHANGED_ACTION.equals(action)) {
                NetworkInfo info = intent.getParcelableExtra(WifiManager.EXTRA_NETWORK_INFO);

                if (info.isConnected() &&
                        info.getExtraInfo().contains(WifiDriver.DEVICE_NAME)) {
                    Log.d(TAG, "Wifi device attached");
                    mHandler.obtainMessage(MSG_ENDPOINT_DISCOVERED, DEVICE_WIFI).sendToTarget();
                } else if (info.getExtraInfo().contains("unknown")) {
                        Log.d(TAG, "Wifi device dettached");
                        mHandler.obtainMessage(MSG_ENDPOINT_LOST, DEVICE_WIFI).sendToTarget();
                }
            }
        }
    };
}
