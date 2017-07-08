/*******************************************************************************
 * Copyright (c) 2016 Grzegorz Sygieda
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 *******************************************************************************/

package com.sygmi;

import android.app.Service;
import android.content.Intent;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.util.Log;

import org.json.JSONException;
import org.json.JSONObject;

public class ControllerService extends Service implements PortDriver.PortDriverMonitor,Watchdog.WatchdogMaster {

    private static final String TAG = "ControllerService";

    public static final int DEVICE_WIFI = 0;
    public static final int DEVICE_BLUETOOTH = 1;
    public static final int DEVICE_FAKE = 0xFF;

    private static final int MSG_CONTROLLER_ATTACHED = 0;
    private static final int MSG_CONTROLLER_DETACHED = 1;
    private static final int MSG_CONTROLLER_DATA_RECEIVED = 2;
    private static final int MSG_CONTROLLER_TIMEOUT = 3;
    private static final int MSG_CONTROLLER_ERROR = 0xFF;

    private static final int MAX_CONNECTION_ATTEMPTS = 3;

    private final LocalBinder mBinder = new LocalBinder();

    public static final int TYPE_DEFAULT = DEVICE_WIFI;
    public static final int TIMEOUT_DEFAULT = 0;

    public static final String EXTRA_TYPE = "extra.TYPE";
    public static final String EXTRA_ONESHOT = "extra.ONESHOT";
    public static final String EXTRA_TIMEOUT = "extra.TIMEOUT";
    public static final String EXTRA_WAS_ENABLED = "extra.WAS_ENABLED";
    public static final String EXTRA_AUX_DATA = "extra.AUX_DATA";

    private IControllerObserver mObserver = null;
    private int mType = -1;
    private boolean mOneShot = false;
    private int mScanPeriod = 10;  // 10ms
    private String mAuxData = null;
    private boolean mWasEnabled = false;

    private boolean mAttached = false;

    private Thread mServiceThread = null;
    private Watchdog mWatchdog = null;

    private int mTimeout = -1;

    private int mConnectAttempt = MAX_CONNECTION_ATTEMPTS;

    /* CAN device */
    private PortDriver mDevice = null;

    private final Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {

            switch (msg.what) {
                case MSG_CONTROLLER_ATTACHED:
                    String label = (String) msg.obj;
                    Log.d(TAG, "Controller attached !");
                    mAttached = true;
                    if (mObserver != null) {
                        mObserver.onConnected(label);
                    }
                    break;
                case MSG_CONTROLLER_DETACHED:
                    Log.d(TAG, "Controller detached !");
                    if (mObserver != null) {
                        mObserver.onDisconnected();
                    }
                    mAttached = false;
                    break;
                case MSG_CONTROLLER_DATA_RECEIVED:
                    String data = (String) msg.obj;
                    //Log.d(TAG, "Controller received data: " + data);
                    if (mObserver != null && mAttached) {
                        mObserver.onDataReceived(data);
                    }
                    break;
                case MSG_CONTROLLER_TIMEOUT:
                    Log.w(TAG, "Controller timeout !");
                    if (mObserver != null) {
                        mObserver.onTimeout();
                    }
                    mAttached = false;
                    break;
                case MSG_CONTROLLER_ERROR:
                    String err = (String) msg.obj;
                    Log.e(TAG, "Controller error occured: " + err);
                    if (mObserver != null) {
                        mObserver.onError(err);
                    }
                    mAttached = false;
                    break;
            }
        }
    };

    public interface IControllerObserver {
        void onConnected(String label);
        void onDisconnected();
        void onDataReceived(String event);
        void onTimeout();
        void onError(String error);
    }

    public class LocalBinder extends Binder {
        public ControllerService getService() {
            return ControllerService.this;
        }
    }

    public boolean isAttached() {
        return mAttached;
    }

    public void registerObserver(IControllerObserver observer) {
        if (mObserver != null) {
            return;
        }
        mObserver = observer;
    }

    public void setScanPeriod(int scanPeriod) {
        if (scanPeriod > 10) {  // prevent too small values
            mScanPeriod = scanPeriod;
        }
    }

    @Override
    public void onCreate() {

        super.onCreate();

        mServiceThread = new Thread(new ServiceThread());
    }

    public void startPoll() {

        Log.d(TAG, "Starting service poll thread !");
        mWatchdog = new Watchdog(mTimeout);
        mWatchdog.setMaster(this);

        if (mServiceThread.getState() == Thread.State.NEW) {
            mServiceThread.start();
        }
    }

    public void stopPoll() {
        stopSelf();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {

        if (intent == null) {
            return START_NOT_STICKY;
        }

        mType = intent.getIntExtra(EXTRA_TYPE, TYPE_DEFAULT);
        mOneShot = intent.getBooleanExtra(EXTRA_ONESHOT, false);
        mTimeout = intent.getIntExtra(EXTRA_TIMEOUT, TIMEOUT_DEFAULT);
        mWasEnabled = intent.getBooleanExtra(EXTRA_WAS_ENABLED, false);
        mAuxData = intent.getStringExtra(EXTRA_AUX_DATA);

        Log.d(TAG, "Starting service");

        return START_NOT_STICKY;
    }

    @Override
    public void onDestroy() {

        Log.d(TAG, "Stopping service");

        mDevice.destroy();

        try {
            mServiceThread.interrupt();
            mServiceThread.join();
        } catch (InterruptedException e) {
        }

        mDevice = null;
        mWatchdog.hug();

        super.onDestroy();

        Log.d(TAG, "Service stopped !");
    }

    @Override
    public IBinder onBind(Intent dummy) {
        return mBinder;
    }

    @Override
    public void onException(int code) {
        Log.e(TAG, "Got device exception:  " + code);
        String reason = "Device exception occured !";
        mHandler.obtainMessage(MSG_CONTROLLER_ERROR, reason).sendToTarget();
    }

    @Override
    public void onHauu() {
        mHandler.obtainMessage(MSG_CONTROLLER_TIMEOUT).sendToTarget();
        Log.e(TAG, "Got watchdog bark !");
    }

    private class ServiceThread implements Runnable {

        @Override
        public void run() {

            boolean status;

            switch (mType) {
                default:
                case DEVICE_WIFI:
                    mDevice = new WifiDriver(ControllerService.this, mWasEnabled, mAuxData);
                    break;
                case DEVICE_BLUETOOTH:
                    mDevice = new BluetoothDriver(ControllerService.this, mWasEnabled);
                    break;
                case DEVICE_FAKE:
                    mDevice = new FakeDevice(ControllerService.this);
                    break;
            }

            status = mDevice.initiate();
            mWatchdog.giveMeat(mTimeout);

            if (status) {
                String label = mDevice.getProduct();

                mHandler.obtainMessage(MSG_CONTROLLER_ATTACHED, label).sendToTarget();
            } else {
                String reason = "Problem when connecting to device !";
                mHandler.obtainMessage(MSG_CONTROLLER_ERROR, reason).sendToTarget();
                return;
            }

            PortDriver.DataItem item = new PortDriver.DataItem();

            while (!Thread.currentThread().isInterrupted()) {
                boolean lastStatus = true;

                mWatchdog.giveMeat(mTimeout);
                status = mDevice.poll(item);

                String data = null;

                if (status) {
                    try {
                        JSONObject json = new JSONObject();
                        json.put("KEY", item.key);
                        json.put("VALUE", item.value);
                        Log.w(TAG, "item val " + item.value);
                        data = json.toString();
                    } catch (JSONException e) {
                    }

                    mHandler.obtainMessage(MSG_CONTROLLER_DATA_RECEIVED, data).sendToTarget();
                } else if (!lastStatus) {
                    mHandler.obtainMessage(MSG_CONTROLLER_TIMEOUT).sendToTarget();
                }

                lastStatus = status;

                try {
                    Thread.sleep(mScanPeriod);
                } catch (InterruptedException e) {
                    break;
                }

                if (mOneShot)
                    break;
            }

            mHandler.obtainMessage(MSG_CONTROLLER_DETACHED).sendToTarget();
        }
    }

}
