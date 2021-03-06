/*******************************************************************************
 * Copyright (c) 2015 Grzegorz Sygieda
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 *******************************************************************************/

package com.sygmi.iothub;

import android.os.Bundle;
import android.preference.PreferenceActivity;

import com.sygmi.iothub.dash.R;

public class SettingsActivity extends PreferenceActivity{

    public static final String ATTR_DEV_TYPE = "prefDevType";
    public static final String ATTR_START_DEMO = "prefStartDemo";
    public static final String ATTR_REFRESH_RATE = "prefRefreshRate";
    public static final String ATTR_ENDPOINT_TIMEOUT = "prefEndpointTimeout";
    public static final String ATTR_WIFI_ENDPOINT_ADDR = "prefWifiAddress";

    public static final String ATTR_DEV_TYPE_DEFAULT_VAL = "255";
    public static final String ATTR_REFRESH_RATE_DEFAULT_VAL = "100";
    public static final String ATTR_ENDPOINT_TIMEOUT_DEFAULT_VAL = "1000";
    public static final String ATTR_WIFI_ENDPOINT_ADDR_DEFAULT_VAL = "127.0.0.1";

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        addPreferencesFromResource(R.xml.settings);
    }
}
