/*******************************************************************************
 * Copyright (c) 2016 Grzegorz Sygieda
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 *******************************************************************************/

package com.sygmi;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiManager;
import android.util.Log;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.List;


/* This is simple Wifi driver that fetches string data from device
 * Only RX path is available */
public final class WifiDriver extends PortDriver implements ComLink.ComLinkObserver {

    private static final String TAG = "WifiDriver";

    public final static String DEVICE_NAME = "IOT_HUB";

    private final static int DEVICE_PORT = 80;

    private final static int AP_ENABLE_DELAY = 1000;
    private final static int AP_TIMEOUT = 5000;

    private ComLink mLink = null;

    private InputStream mInput = null;
    private OutputStream mOutput = null;

    private PortDriverMonitor mMonitor = null;

    private boolean mWasEnabled = false;

    private Socket mSocket;

    private String mIpAddress;

    private WifiManager mWifiMgr = null;
    ConnectivityManager mConnMgr = null;

    public WifiDriver(Context context, boolean enabled, String ipAddress) {
        super(context);

        /* Controller service context */
        mMonitor = (PortDriverMonitor)context;

        mWasEnabled = enabled;

        mWifiMgr = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
        mConnMgr = (ConnectivityManager)context.getSystemService(Context.CONNECTIVITY_SERVICE);

        mIpAddress = ipAddress;

        Log.i(TAG, "IP address : " + mIpAddress);

        setName(DEVICE_NAME);
    }

    public static boolean validateHost(String address) {

        if (address == null || address.isEmpty()) {
            return false;
        }

        try {
            Object obj = InetAddress.getByName(address);
            return obj instanceof Inet4Address;
        } catch (final UnknownHostException ex) {
            return false;
        }
    }

    private void setWifi(boolean enable) {

        if (!mWasEnabled) {
            if (enable) {
                mWifiMgr.setWifiEnabled(true);
                while (!mWifiMgr.isWifiEnabled()) ;
            }
            else {
                mWifiMgr.setWifiEnabled(false);
            }
        }
    }

    public boolean isOnline()
    {
        NetworkInfo networkInfo = mConnMgr.getActiveNetworkInfo();
        return (networkInfo != null && networkInfo.isConnected());
    }

    private boolean connectAP(String ssid)
    {
        List<WifiConfiguration> list = mWifiMgr.getConfiguredNetworks();
        for( WifiConfiguration ap : list ) {
            if(ap.SSID != null && ap.SSID.startsWith("\"" + ssid)) {
                mWifiMgr.disconnect();
                mWifiMgr.enableNetwork(ap.networkId, true);
                mWifiMgr.reconnect();
                Log.i(TAG, "Connecting to AP ... " + ssid);
                return true;
            }
        }

        Log.e(TAG, "No AP found !");
        return false;
    }

    @Override
    public void onException(int code) {
        /* Just forward the exception code */
        mMonitor.onException(code);
    }

    public void open() {
        try {
            if (mSocket == null || !mSocket.isConnected()) {
                mSocket = new Socket(mIpAddress, DEVICE_PORT);
                mSocket.setSoTimeout(AP_TIMEOUT);
                mOutput = mSocket.getOutputStream();
                mInput = mSocket.getInputStream();
                if (mLink != null)
                    mLink.updateIO(mInput, mOutput);
            }
        } catch (IOException e) {
            Log.e(TAG, "Problem when connecting " + e.toString());
        }
    }

    public void close() {
        try {
            if (mSocket != null && mSocket.isConnected()) {
                mSocket.close();
                mSocket = null;
            }
        } catch (IOException e) {
            Log.e(TAG, "Problem when reconnecting " + e.toString());
        }
    }

    @Override
    public void onReconnect() {
        Log.d(TAG, "onReconnect " + mSocket.isConnected());

        close();
        open();
    }

    @Override
    public boolean initiate() {
        super.initiate();

        if (validateHost(mIpAddress) == false) {
            Log.e(TAG, "Incorrect host address found ....");
            return false;
        }

        setWifi(true);

        if (!connectAP(DEVICE_NAME))
            return false;

        do {
            try {
                Thread.sleep(AP_ENABLE_DELAY);
            } catch (InterruptedException e) {
            }
        }while(!isOnline());

        open();

        mLink = new ComLink(this, mInput, mOutput);

        mIsConnected = true;

        return mLink != null;
    }

    @Override
    public void destroy() {

        if (mLink != null) {
            mLink.destroy();
        }

        close();

        mIsConnected = false;

        setWifi(false);

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

