/*******************************************************************************
 * Copyright (c) 2016 Grzegorz Sygieda
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 *******************************************************************************/

package com.sygmi;

import android.util.Log;

import java.util.Timer;
import java.util.TimerTask;

public class FaderEffect {

    private static final String TAG = "FaderEffect";

    private Timer mTimer = new Timer();
    private double mFrom = 0;
    private double mTo = 0;
    private double mValStep = 0;
    private int mTimeStep = 0;

    private IFaderObserver mObserver = null;

    public interface IFaderObserver {
        void onStep(double val);
        void onFinish(double val);
    }

    private final TimerTask mTask = new TimerTask() {
        @Override
        public void run() {

            if (mFrom > mTo) {
                // prevent drift
                if (mFrom - mValStep < mTo) {
                    mFrom = mTo;
                } else {
                    mFrom -= mValStep;
                }
            } else if (mFrom < mTo) {
                // prevent drift
                if (mFrom + mValStep > mTo) {
                    mFrom = mTo;
                } else {
                    mFrom += mValStep;
                }
            } else {  // stop fader task
                mTimer.cancel();
                mObserver.onFinish(mTo);
                return;
            }
            mObserver.onStep(mFrom);
        }
    };

    public void stop() {
        mTimer.cancel();
    }

    public FaderEffect(IFaderObserver observer, double from, double to, int timeMs) {

        mObserver = observer;
        mFrom = from;
        mTo = to;

        if (mObserver == null) {
            return;
        }

        // Linear fading
        double range = 0.0;
        if (mFrom > mTo) {
            range = mFrom - mTo;
        } else if (mFrom < mTo) {
            range = mTo - mFrom;
        } else {
            mObserver.onStep(mTo);
            mObserver.onFinish(mTo);
            return;
        }

        if (timeMs > range && range > 1.0) {
            mTimeStep = timeMs / (int) range;
            mValStep = 1.0;
        } else {
            mTimeStep = 1;
            mValStep = (range / timeMs) + 1.0;
        }

        Log.d(TAG, "from " + mFrom + " to " + mTo + " step " + mValStep + " timeStep " + mTimeStep);

        if (mTimeStep > 0 ) {
            mTimer.scheduleAtFixedRate(mTask, 0, mTimeStep);
        } else {
            mObserver.onStep(mTo);
            mObserver.onFinish(mTo);
        }
    }

}



