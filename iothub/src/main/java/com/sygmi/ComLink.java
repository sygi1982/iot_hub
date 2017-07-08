/*******************************************************************************
 * Copyright (c) 2016 Grzegorz Sygieda
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 *******************************************************************************/

package com.sygmi;

import android.util.Log;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/* This is simple communication link driver that fetches string data from custom device
 * Only RX path is available */
public final class ComLink {

    private static final String TAG = "ComLink";

    private static final int GET_PERIOD = 5000;

    private InputStream mInput = null;
    private OutputStream mOutput = null;

    private Thread mPollThread = null;

    private ArrayList<PortDriver.DataItem> mItems = null;

    private Lock mLock = new ReentrantLock();
    private Condition mCondition = mLock.newCondition();

    private ComLinkObserver mObserver = null;

    public interface ComLinkObserver {
        void onException(int code);

        void onReconnect();
    }

    public ComLink(ComLinkObserver observer, InputStream input, OutputStream output) {

        mObserver = observer;
        mInput = input;
        mOutput = output;

        mItems = new ArrayList<PortDriver.DataItem>();

        mPollThread = new Thread(new PollThread());
        mPollThread.start();
    }

    public void updateIO(InputStream input, OutputStream output) {
        mInput = input;
        mOutput = output;
    }

    public void destroy() {

        mLock.lock();
        mCondition.signalAll();
        mLock.unlock();

        try {
            if (mPollThread != null) {
                mPollThread.interrupt();
                mPollThread.join();
            }
        } catch (InterruptedException e) {
        }
    }

    public boolean receive(PortDriver.DataItem frame) {
        boolean status = false;

        mLock.lock();
        try {
            if (mItems.isEmpty()) {
                Log.d(TAG, "waiting ... ");
                mCondition.await();
            }

            if (!mItems.isEmpty()) {
                Log.d(TAG, "receive ... ");
                PortDriver.DataItem tmp = mItems.remove(0);
                frame.clone(tmp);
                status = true;
            }
        } catch (InterruptedException e) {

        } finally {
            mLock.unlock();
        }

        return status;
    }

    private class PollThread implements Runnable {

        @Override
        public void run() {

            while (!Thread.currentThread().isInterrupted()) {

                SimpleHttpReq requestor = new SimpleHttpReq(mOutput, mInput);
                List<String> response;

                try {
                    response = requestor.get();
                    requestor = null;
                } catch (SimpleHttpReq.SimpleHttpReqException e) {
                    Log.e(TAG, "Http error " + e.getReason());
                    mObserver.onException(-1);
                    break;
                }

                mLock.lock();
                if (response == null) {
                    Log.e(TAG, "Response is null");
                    mObserver.onException(-1);
                }

                for (int i = 0; i < response.size(); i++) {
                    if (response.get(i).length() == 0)
                        break;

                    String line = new String(response.get(i));
                    line = line.substring(line.indexOf(">") + 1);
                    line = line.substring(0, line.indexOf("<"));
                    String values[] = line.split(" ");

                    Log.d(TAG, "RX DATA " + line);
                    for (int v = 0; v < values.length; v++) {
                        PortDriver.DataItem item = new PortDriver.DataItem();
                        item.key = values[v].substring(0, 1);
                        int len = values[v].length();
                        if (item.key.contains("T") || item.key.contains("H"))
                            len--;
                        item.value = Double.parseDouble(values[v].substring(2, len));
                        mItems.add(item);
                    }

                    mCondition.signal();
                }
                mLock.unlock();

                // try to reconnect when needed - reset connection socket
                mObserver.onReconnect();

                try {
                    Thread.sleep(GET_PERIOD);
                } catch (InterruptedException e) {
                    break;
                }

            }
        }
    }
}

