/*******************************************************************************
 * Copyright (c) 2016 Grzegorz Sygieda
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 *******************************************************************************/

package com.sygmi.iothub;

import com.sygmi.DataParser;
import com.sygmi.ControllerService;
import com.sygmi.EndpointStateService;
import com.sygmi.FaderEffect;
import com.sygmi.iothub.dash.R;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.graphics.Color;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.WindowManager;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends Activity implements ControllerService.IControllerObserver {

    private static final String TAG = MainActivity.class.getSimpleName();

    private final static int DEFAULT_VISUAL_DELAY = 1000;  // ms

    private static final int SETTINGS_RESULT = 1;

    private TextView mTempText = null;
    private GradientView mTempGradientView = null;
    private TextView mHumidText = null;
    private GradientView mHumidGradientView = null;
    private View mConnStatusView = null;
    private View mReadStatusView = null;

    private ControllerService mControllerService = null;
    private DataParser mParser = null;

    private TempTasker mTempTasker = new TempTasker();
    private HumidTasker mHumidTasker = new HumidTasker();

    private int mTimeFromBoot = 0;
    private int mTimeOfLastRead = 0;

    private boolean mConnected = false;
    private int mConnectionType = -1;
    private boolean mStartDemo = false;
    private int mRefreshRate = -1;
    private int mEndpointTimeout = -1;
    private String mWifiIpAddress = null;

    private boolean mIsVisible = false;

    private BroadcastReceiver mLocalReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {

            String action = intent.getAction();

            if (action.equals(Intent.ACTION_SCREEN_OFF)) {
                Log.d(TAG, "SCREEN OFF");
                stopService();
            } else if (action.equals(Intent.ACTION_USER_PRESENT)) {
                Log.d(TAG, "SCREEN ON");
                new Handler().postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        startService();
                    }
                }, 500);
            } else if (action.equals(EndpointStateService.ENDPOINT_DISCOVERED)) {
                String endpoint = intent.getStringExtra(EndpointStateService.ENDPOINT_TYPE);
                if (mapDevType2String(mConnectionType).equals(endpoint)) {
                    showPopup("Endpoint discovered: " + endpoint);
                    startService();
                }
            } else if (action.equals(EndpointStateService.ENDPOINT_LOST)) {
                String endpoint = intent.getStringExtra(EndpointStateService.ENDPOINT_TYPE);
                if (mapDevType2String(mConnectionType).equals(endpoint)) {
                    showPopup("Endpoint lost: " + endpoint);
                    stopService();
                }
            }
        }
    };

    private final ServiceConnection mConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName className, IBinder service) {
            mControllerService = ((ControllerService.LocalBinder) service).getService();

            Log.i(TAG, "Connected to Controller service !");

            if (mControllerService != null) {
                mControllerService.registerObserver(MainActivity.this);
                mControllerService.startPoll();
                mControllerService.setScanPeriod(100);
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName className) {
            mControllerService = null;

            Log.i(TAG, "Disconnected from Controller service !");
            mConnected = false;
        }
    };

    private class UiTasker implements FaderEffect.IFaderObserver {

        FaderEffect mActualFader = null;
        double lastValue = 0;

        public void setFader(FaderEffect f) {
            if (mActualFader != null) {
                mActualFader.stop();
                mActualFader = null;
            }
            // update fader
            mActualFader = f;
        }

        FaderEffect getFader() {
            return mActualFader;
        }

        public double getLastValue() {
            return lastValue;
        }

        @Override
        public void onStep(final double val) {
            lastValue = val;
        }

        @Override
        public void onFinish(final double val) {
            lastValue = val;
            setFader(null);
        }
    }

    private class TempTasker extends UiTasker {

        @Override
        public void onStep(final double val) {
            final String temp = String.format("%.1f", val);
            //Log.d(TAG, "New temperature value " + temp);
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    mTempText.setText("T = " + temp + " [C]");
                    mTempGradientView.setValue((int) val);
                }
            });
            super.onStep(val);
        }
    }

    private class HumidTasker extends UiTasker {

        @Override
        public void onStep(final double val) {
            final String humid = String.format("%.1f", val);
            //Log.d(TAG, "New humidity value " + humid);
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    mHumidText.setText("H = " + humid + " [%]");
                    mHumidGradientView.setValue((int) val);
                }
            });
            super.onStep(val);
        }
    }

    private void resetVisualControls() {
        FaderEffect f;

        f = new FaderEffect(mTempTasker, mTempTasker.getLastValue(), 0, DEFAULT_VISUAL_DELAY);
        mTempTasker.setFader(f);

        f = new FaderEffect(mHumidTasker, mHumidTasker.getLastValue(), 0, DEFAULT_VISUAL_DELAY);
        mHumidTasker.setFader(f);
    }

    private void restoreVisualControls() {
        FaderEffect f;

        f = mTempTasker.getFader();
        if (f == null) {
            f = new FaderEffect(mTempTasker, mTempTasker.getLastValue(), mTempTasker.getLastValue(), DEFAULT_VISUAL_DELAY);
            mTempTasker.setFader(f);
        } else {
            mTempTasker.onStep(mTempTasker.getLastValue());
        }

        f = mHumidTasker.getFader();
        if (f == null) {
            f = new FaderEffect(mHumidTasker, mHumidTasker.getLastValue(), mHumidTasker.getLastValue(), DEFAULT_VISUAL_DELAY);
            mHumidTasker.setFader(f);
        } else {
            mHumidTasker.onStep(mHumidTasker.getLastValue());
        }
    }

    private void startDemo4VisualControls() {
        FaderEffect f;

        f = new FaderEffect(mTempTasker, mTempTasker.getLastValue(), DataParser.MAX_VAL, DEFAULT_VISUAL_DELAY);
        mTempTasker.setFader(f);

        f = new FaderEffect(mHumidTasker, mHumidTasker.getLastValue(), DataParser.MAX_VAL, DEFAULT_VISUAL_DELAY);
        mHumidTasker.setFader(f);

        new Handler().postDelayed(new Runnable() {
            @Override
            public void run() {
                resetVisualControls();
            }
        }, DEFAULT_VISUAL_DELAY * 2);
    }

    @Override
    public void onConnected(String label) {
        showPopup("Controller connected: " + label);
        mParser = new DataParser();
        mParser.setCallback(new DataParser.IDataCallback() {

            public void onTemperatureChanged(double oldVal, double newVal) {
                Log.d(TAG, "Got new temperature " + newVal);
                if (newVal >= 0.0) {
                    FaderEffect f = new FaderEffect(mTempTasker, oldVal, newVal, DEFAULT_VISUAL_DELAY);
                    mTempTasker.setFader(f);  // store fader reference
                }
            }

            public void onHumidityChanged(double oldVal, double newVal) {
                Log.d(TAG, "Got new humidity " + newVal);
                if (newVal >= 0.0) {
                    FaderEffect f = new FaderEffect(mHumidTasker, oldVal, newVal, DEFAULT_VISUAL_DELAY);
                    mHumidTasker.setFader(f);  // store fader reference
                }
            }

            public void onBootTimeChanged(double newVal) {
                Log.d(TAG, "Got new time from boot " + newVal);
                mTimeFromBoot = (int)newVal;
                updateReadStatusIndicator();
            }

            public void onReadTimeChanged(double newVal) {
                Log.d(TAG, "Got new read time " + newVal);
                mTimeOfLastRead = (int)newVal;
                updateReadStatusIndicator();
            }

            @Override
            public void onDebug(String info) {
                Log.d(TAG, "[Debug] " + info);
            }

            @Override
            public void onError(String error) {
                Log.e(TAG, "Got parser error: " + error);
            }

        });

        mConnStatusView.setBackgroundColor(Color.GREEN);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }

    @Override
    public void onDisconnected() {
        showPopup("Controller disconnected !");
        mParser = null;
        mConnStatusView.setBackgroundColor(Color.RED);
        resetVisualControls();
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }

    @Override
    public void onDataReceived(String data) {
        //Log.d(TAG, "CAN Controller data received !");
        try {
            mParser.exec(data);
        } catch (DataParser.DataParserException excp) {
            Log.e(TAG, "Parser exception : " + excp.getReason());
        }
    }

    @Override
    public void onTimeout() {
        showPopup("Controller timeout - connection with bus lost !");
        stopService();
    }

    @Override
    public void onError(String error) {
        showPopup("Controller unexpected error: " + error);
        stopService();
    }

    private void startService() {

        if (mConnected ==  true || mIsVisible == false)
            return;

        Intent startIntent = new Intent(MainActivity.this, ControllerService.class);
        startIntent.putExtra(ControllerService.EXTRA_TYPE, mConnectionType);
        startIntent.putExtra(ControllerService.EXTRA_ONESHOT, false);
        startIntent.putExtra(ControllerService.EXTRA_TIMEOUT, mEndpointTimeout);
        if (mConnectionType == ControllerService.DEVICE_WIFI) {
            startIntent.putExtra(ControllerService.EXTRA_WAS_ENABLED,
                    ((WifiManager)this.getSystemService(Context.WIFI_SERVICE)).isWifiEnabled());
            startIntent.putExtra(ControllerService.EXTRA_AUX_DATA, mWifiIpAddress);
        }
        if (mConnectionType == ControllerService.DEVICE_BLUETOOTH) {
            startIntent.putExtra(ControllerService.EXTRA_WAS_ENABLED,
                    BluetoothAdapter.getDefaultAdapter().isEnabled());
        }
        startService(startIntent);
        bindService(startIntent, mConnection, Context.BIND_AUTO_CREATE);
        mConnected = true;
    }

    private void stopService() {

        if (mConnected == false)
            return;

        mControllerService = null;
        Intent stopIntent = new Intent(MainActivity.this, ControllerService.class);
        stopService(stopIntent);
        unbindService(mConnection);
        mConnected = false;
    }

    private String mapDevType2String(int type) {
        String result;
        switch (type) {
            default:
            case ControllerService.DEVICE_WIFI:
                result = EndpointStateService.DEVICE_WIFI;
                break;
            case ControllerService.DEVICE_BLUETOOTH:
                result = EndpointStateService.DEVICE_BLUETOOTH;
                break;
            case ControllerService.DEVICE_FAKE:
                result = EndpointStateService.DEVICE_FAKE;
                break;
        }

        return result;
    }

    private void updateConnStatusIndicator(int type) {
        String status = mapDevType2String(type);

        ((TextView) mConnStatusView).setText(status);
        ((TextView) mConnStatusView).setTextColor(Color.BLACK);

        if (mControllerService != null && mControllerService.isAttached()) {
            mConnStatusView.setBackgroundColor(Color.GREEN);
        } else {
            mConnStatusView.setBackgroundColor(Color.RED);
        }
    }

    private void updateReadStatusIndicator() {

        if (mTimeFromBoot >= mTimeOfLastRead) {

            int seconds = mTimeFromBoot - mTimeOfLastRead;

            String time = String.valueOf(seconds) + " seconds ago";

            ((TextView) mReadStatusView).setText(time);
            ((TextView) mReadStatusView).setTextColor(Color.BLACK);
        }

    }

    private void setupWidgets() {
        mTempGradientView = (GradientView) findViewById(R.id.tempGradientView);
        mTempGradientView.setMaxRange((int)DataParser.MAX_VAL);
        mTempText = (TextView) findViewById(R.id.temp_content);
        mHumidGradientView = (GradientView) findViewById(R.id.humidGradientView);
        mHumidGradientView.setMaxRange((int)DataParser.MAX_VAL);
        mHumidText = (TextView) findViewById(R.id.humid_content);
        mConnStatusView = findViewById(R.id.connection_status);
        mConnStatusView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                startService();
            }
        });
        mReadStatusView = findViewById(R.id.read_status);
    }

    private void getPrefs() {
        // Restore preferences
        SharedPreferences sharedPrefs = PreferenceManager.getDefaultSharedPreferences(this);
        mConnectionType = Integer.parseInt(sharedPrefs.getString(SettingsActivity.ATTR_DEV_TYPE,
                SettingsActivity.ATTR_DEV_TYPE_DEFAULT_VAL));
        mStartDemo = sharedPrefs.getBoolean(SettingsActivity.ATTR_START_DEMO, false);
        mRefreshRate = Integer.parseInt(sharedPrefs.getString(SettingsActivity.ATTR_REFRESH_RATE,
                SettingsActivity.ATTR_REFRESH_RATE_DEFAULT_VAL));
        mEndpointTimeout = Integer.parseInt(sharedPrefs.getString(SettingsActivity.ATTR_ENDPOINT_TIMEOUT,
                SettingsActivity.ATTR_ENDPOINT_TIMEOUT_DEFAULT_VAL));
        mWifiIpAddress = sharedPrefs.getString(SettingsActivity.ATTR_WIFI_ENDPOINT_ADDR,
                SettingsActivity.ATTR_WIFI_ENDPOINT_ADDR_DEFAULT_VAL);
    }

    private void showPopup(String text) {
        Log.d(TAG, text);
        Toast.makeText(getApplicationContext(), text, Toast.LENGTH_SHORT).show();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.d(TAG, "onCreate");

        setContentView(R.layout.activity_main);

        // hide action bar components
        getActionBar().setDisplayShowHomeEnabled(false);
        getActionBar().setDisplayShowTitleEnabled(false);

        setupWidgets();
        getPrefs();

        mIsVisible = true;

        updateConnStatusIndicator(mConnectionType);
        if (mStartDemo) {
            startDemo4VisualControls();
        } else {
            startService();  // try to start service
        }

        IntentFilter filter = new IntentFilter(Intent.ACTION_USER_PRESENT);
        filter.addAction(Intent.ACTION_SCREEN_OFF);
        filter.addAction(EndpointStateService.ENDPOINT_DISCOVERED);
        filter.addAction(EndpointStateService.ENDPOINT_LOST);
        registerReceiver(mLocalReceiver, filter);

        Intent intent = new Intent(getApplicationContext(), EndpointStateService.class);
        startService(intent);
    }

    @Override
    protected void onDestroy() {
        // only called when app is killed
        stopService();
        Log.d(TAG, "onDestroy");
        unregisterReceiver(mLocalReceiver);
        super.onDestroy();
    }

    @Override
    protected void onPause() {
        Log.d(TAG, "onPause");
        mIsVisible = false;
        super.onPause();
    }

    @Override
    protected void onResume() {
        Log.d(TAG, "onResume");
        mIsVisible = true;
        super.onResume();
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig)
    {
        Log.d(TAG, "onConfigurationChanged");
        setContentView(R.layout.activity_main);
        setupWidgets();
        updateConnStatusIndicator(mConnectionType);
        restoreVisualControls();
        super.onConfigurationChanged(newConfig);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        new MenuInflater(this).inflate(R.menu.options_menu, menu);
        return (super.onCreateOptionsMenu(menu));
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {

        switch (item.getItemId()) {
            case R.id.settings_menu:
                Intent i = new Intent(getApplicationContext(), SettingsActivity.class);
                startActivityForResult(i, SETTINGS_RESULT);
                break;
        }

        return false;
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data)
    {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == SETTINGS_RESULT) {
            getPrefs();
            updateConnStatusIndicator(mConnectionType);
            stopService();
        }
    }
}

