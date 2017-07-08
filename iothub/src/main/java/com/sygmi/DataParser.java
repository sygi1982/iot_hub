/*******************************************************************************
 * Copyright (c) 2016 Grzegorz Sygieda
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 *******************************************************************************/

package com.sygmi;

import android.util.Log;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.Map;

public class DataParser {

    private static final String TAG = "DataParser";

    public final static double MIN_VAL = 0.0;
    public final static double MAX_VAL = 100.0;

    private IDataCallback mCallback = null;

    private Map<String, Parser> mEvents = new HashMap<String, Parser>();

    // keys supported
    private static final String[] gKeys = new String[]{ "T",    // Temperature
                                                        "H",    // Humidity
                                                        "B",    // Time from boot (sec)
                                                        "R",    // Time of last read (sec)
                                                        "E",    // Error indicator
                                                          };

    public static String[] getKeys() {
        return gKeys;
    }

    public DataParser() {

        mEvents.put(gKeys[0], new Temperature());
        mEvents.put(gKeys[1], new Humidity());
        mEvents.put(gKeys[2], new BootWatcher());
        mEvents.put(gKeys[3], new ReadWatcher());
    }

    public void setCallback(IDataCallback callback) {
        if (mCallback != null) {
            return;
        }
        mCallback = callback;
    }

    private abstract class Parser {

        protected double mValue = 0.0;

        abstract void job(double value);
    }   

    public void exec(String json) throws DataParserException {

        if (mCallback == null) {
            throw new DataParserException("Callback not set!");
        }

        try {
            JSONObject data = new JSONObject(json);

            Log.d(TAG, "item val " + data.get("VALUE"));

            Parser parser = mEvents.get(data.get("KEY"));

            if (parser != null) {
                parser.job(Double.parseDouble(data.get("VALUE").toString()));
            } else {
                mCallback.onError("key not mapped " + data.get("KEY"));
            }
        } catch (JSONException e) {
            throw new DataParserException("Wrong JSON string!");
        }
    }

    private class Temperature extends Parser {

        @Override
        public void job(double value) {

            mCallback.onDebug("debug temperature " + value);

            if (value >= MIN_VAL && value < MAX_VAL && value != mValue) {
                mCallback.onTemperatureChanged(mValue, value);
                mValue = value;
            }
        }
    }

    private class Humidity extends Parser {

        @Override
        public void job(double value) {

            mCallback.onDebug("debug humidity " + value);

            if (value >= MIN_VAL && value < MAX_VAL && value != mValue) {
                mCallback.onHumidityChanged(mValue, value);
                mValue = value;
            }
        }
    }

    private class BootWatcher extends Parser {

        @Override
        public void job(double value) {

            mCallback.onDebug("debug time from boot " + value);

            if (value < mValue)
                mCallback.onError("Inconsistent value !");
            else
                mCallback.onBootTimeChanged(value);

            mValue = value;
        }
    }

    private class ReadWatcher extends Parser {

        @Override
        public void job(double value) {

            mCallback.onDebug("debug read time " + value);

            if (value < mValue)
                mCallback.onError("Inconsistent value !");
            else
                mCallback.onReadTimeChanged(value);

            mValue = value;
        }
    }

    public interface IDataCallback {

        void onTemperatureChanged(double oldTemperature, double newTemperature);

        void onHumidityChanged(double oldHumidity, double newHumidity);

        void onBootTimeChanged(double newTime);

        void onReadTimeChanged(double newTime);

        void onDebug(String msg);

        void onError(String msg);
    }

    public class DataParserException extends Exception {

        private String mReason;

        public DataParserException(String reason) {
            mReason = reason;
        }

        public String getReason() {
            return mReason;
        }
    }
}
