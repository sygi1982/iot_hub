/*******************************************************************************
 * Copyright (c) 2016 Grzegorz Sygieda
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 *******************************************************************************/

package com.sygmi;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import java.util.ArrayList;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class FakeDevice extends PortDriver implements Runnable {

    private static final String TAG = "FakeDevice";

    public final static String DEVICE_NAME = "FAKEDEVICE";

    private Handler mFeedHandler;
    private Thread mFeedThread;
    private Lock mLock = new ReentrantLock();
    private Condition mCondition = mLock.newCondition();
    private ArrayList<DataItem> mItems = new ArrayList<DataItem>();

    public FakeDevice(Context context) {
        super(context);

        mFeedHandler = new Handler(Looper.getMainLooper());
        mFeedThread = new Thread(this);
        mFeedThread.start();
        mIsConnected = true;

        setName(DEVICE_NAME);
    }

    @Override
    public void run() {
        if (mIsConnected) {
            mLock.lock();

            String fakeResp = "B=100 R=96 E=1 T=25.3C H=37.7%"; // example
            String values[] = fakeResp.split(" ");

            for (int v = 0; v < values.length; v++) {
                PortDriver.DataItem item = new PortDriver.DataItem();
                item.key = values[v].substring(0, 1);
                int len = values[v].length();
                if (item.key.contains("T") || item.key.contains("H"))
                    len--;
                item.value = Double.parseDouble(values[v].substring(2, len));
                mItems.add(item);
                mCondition.signal();
            }
            mLock.unlock();
        }
        mFeedHandler.postDelayed(this, 1000);
    }

    @Override
    public boolean initiate() {
        super.initiate();
        return true;
    }

    @Override
    public void destroy() {
        mIsConnected = false;
        mLock.lock();
        mCondition.signalAll();
        mLock.unlock();
        mFeedThread.interrupt();
        super.destroy();
    }

    @Override
    public boolean poll(DataItem frame) {
        boolean status = false;

        mLock.lock();
        try {
            if (mItems.isEmpty()) {
                //Log.d(TAG, "waiting ... ");
                mCondition.await();
            }

            if (!mItems.isEmpty()) {
                //Log.d(TAG, "receive ... ");
                DataItem tmp = mItems.remove(0);
                frame.clone(tmp);
                status = true;
            }
        } catch (InterruptedException e) {
        } finally {
            mLock.unlock();
        }

        return status;
    }
}

