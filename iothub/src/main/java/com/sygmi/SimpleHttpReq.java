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
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

public class SimpleHttpReq {

    private static final String TAG = "SimpleHttpReq";

    private InputStream mIn = null;
    private OutputStream mOut = null;

    SimpleHttpReq(OutputStream out, InputStream in)
    {
        mIn = in;
        mOut = out;
    }

    public List<String> get() throws SimpleHttpReqException
    {
        try {
            List<String> response = new ArrayList<String>();
            String httpGet = "GET / HTTP/1.0\r\n";

            BufferedReader reader = new BufferedReader(new InputStreamReader(mIn));

            mOut.write(httpGet.getBytes());
            mOut.flush();

            Log.d(TAG, "GET");

            while (!reader.ready());

            Log.d(TAG, "RESPONSE ... ");

            while(reader.ready()) {
                response.add(reader.readLine());
            }

            Log.d(TAG, "RESPONSE READY");

            return response;

        } catch (Exception e) {
            throw new SimpleHttpReqException("ESOCKET");
        }
    }

    public class SimpleHttpReqException extends Exception {

        private String mReason;

        public SimpleHttpReqException(String reason) {
            mReason = reason;
        }

        public String getReason() {
            return mReason;
        }
    }

}
