/*******************************************************************************
 * Copyright (c) 2016 Grzegorz Sygieda
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 *******************************************************************************/

package com.sygmi;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Context;
import android.util.Log;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.util.Set;
import java.util.UUID;

/* This is simple BT driver that fetches string data from device
 * Device shall be paired using Android settings menu.
 * Only RX path is available */
public final class BluetoothDriver extends PortDriver implements ComLink.ComLinkObserver {

    private static final String TAG = "BluetoothDriver";

    /* Serial port profile ??? */
    private final static UUID SSP_UUID = UUID.fromString("00001101-0000-1000-8000-00805f9b34fb");
    public final static String DEVICE_NAME = "IOT_HUB";

    private BluetoothAdapter mAdapter = null;
    private BluetoothSocket mSocket = null;
    private BluetoothDevice mDevice = null;

    private InputStream mInput = null;
    private OutputStream mOutput = null;

    private ComLink mLink = null;

    private PortDriverMonitor mMonitor = null;

    private boolean mWasEnabled = false;

    public BluetoothDriver(Context context, boolean enabled) {
        super(context);

        /* Controller service context */
        mMonitor = (PortDriverMonitor)context;

        mWasEnabled = enabled;

        Log.i(TAG, "mWasEnabled : " + mWasEnabled);

        mAdapter = BluetoothAdapter.getDefaultAdapter();

        setName(DEVICE_NAME);
    }

    private void setBluetooth(boolean enable) {

        if (!mWasEnabled) {
            if (enable) {
                mAdapter.enable();
                while (!mAdapter.isEnabled()) ;
            }
            else {
                mAdapter.disable();
            }
        }
    }

    @Override
    public void onException(int code) {
        /* Just forward the exception code */
        mMonitor.onException(code);
    }

    @Override
    public void onReconnect() {
        // empty
    }

    @Override
    public boolean initiate() {
        super.initiate();

        if (mAdapter == null) {
            Log.e(TAG, "No bluetooth adapter found ....");
            return false;
        }

        setBluetooth(true);

        Set<BluetoothDevice> bondedDevices = mAdapter.getBondedDevices();
        if (bondedDevices.size() == 0) {
            Log.e(TAG, "No paired devices found. Please pair with any " + DEVICE_NAME);
            return false;
        }

        for (BluetoothDevice dev : bondedDevices) {
            if (dev.getName().endsWith(DEVICE_NAME)) {
                mDevice = dev;
            }
        }

        if (mDevice == null) {
            Log.e(TAG, "No " + DEVICE_NAME + " device");
            return false;
        }

        try {
            mSocket = mDevice.createRfcommSocketToServiceRecord(SSP_UUID);
            mSocket.connect();
            mOutput = mSocket.getOutputStream();
            mInput = mSocket.getInputStream();
        } catch (IOException e) {
            Log.e(TAG, "Problem when connecting with bluetooth device " + e.toString());
            return false;
        }

        mLink = new ComLink(this, mInput, mOutput);

        mIsConnected = true;

        return true;
    }

    @Override
    public void destroy() {

        if (mLink != null) {
            mLink.destroy();
        }

        try {
            if (mSocket != null) {
                mSocket.close();
            }
        } catch (IOException e) {
            Log.e(TAG, "Problem when closing bluetooth socket " + e.toString());
        }

        setBluetooth(false);
        mIsConnected = false;

        super.destroy();
    }

    @Override
    public boolean poll(DataItem frame) {
        if (mLink != null) {
            return mLink.receive(frame);
        }

        return false;
    }
}

