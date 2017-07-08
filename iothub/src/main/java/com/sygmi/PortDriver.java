/*******************************************************************************
 * Copyright (c) 2016 Grzegorz Sygieda
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 *******************************************************************************/

package com.sygmi;

import android.content.Context;

public abstract class PortDriver {

    protected String mName = null;

    protected Context mContext = null;

    protected boolean mIsConnected = false;

    public interface PortDriverMonitor {
        void onException(int code);
    }

    public PortDriver(Context context) {
        mContext = context;
    }
    
    protected void setName(String str) {
        mName = str;
    }

    public String getProduct() {
        return mName;
    }

    public boolean initiate() {
        return false;
    }

    public void destroy() {
        mName = null;
    }

    public boolean poll(DataItem item) {
        return false;
    }

    public static class DataItem {

        public String key = null;
        public double value;

	    public DataItem() {

	    }

        public DataItem(DataItem item) {

            clone(item);
        }

        public void clone(DataItem item) {

            this.key = item.key;
            this.value = item.value; // todo: copy ?
        }

    }

}
