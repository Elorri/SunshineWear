/*
 * Copyright (C) 2014 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.example.android.sunshine;


import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.wearable.watchface.CanvasWatchFaceService;
import android.support.wearable.watchface.WatchFaceStyle;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.WindowInsets;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.wearable.DataApi;
import com.google.android.gms.wearable.DataEvent;
import com.google.android.gms.wearable.DataEventBuffer;
import com.google.android.gms.wearable.DataMapItem;
import com.google.android.gms.wearable.Wearable;

import java.lang.ref.WeakReference;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;
import java.util.concurrent.TimeUnit;

import com.google.android.gms.common.api.GoogleApiClient.ConnectionCallbacks;
import com.google.android.gms.common.api.GoogleApiClient.OnConnectionFailedListener;

/**
 * Digital watch face with seconds. In ambient mode, the seconds aren't displayed. On devices with
 * low-bit ambient mode, the text is drawn without anti-aliasing in ambient mode.
 */
public class MyWatchFace extends CanvasWatchFaceService {
    private static final Typeface NORMAL_TYPEFACE =
            Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL);


    private static final Typeface BOLD_TYPEFACE =
            Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD);

    /**
     * Update rate in milliseconds for interactive mode. We update once a second since seconds are
     * displayed in interactive mode.
     */
    private static final long INTERACTIVE_UPDATE_RATE_MS = TimeUnit.SECONDS.toMillis(1);

    /**
     * Handler message id for updating the time periodically in interactive mode.
     */
    private static final int MSG_UPDATE_TIME = 0;

    @Override
    public Engine onCreateEngine() {
        return new Engine();
    }

    private static class EngineHandler extends Handler {
        private final WeakReference<MyWatchFace.Engine> mWeakReference;

        public EngineHandler(MyWatchFace.Engine reference) {
            mWeakReference = new WeakReference<>(reference);
        }

        @Override
        public void handleMessage(Message msg) {
            MyWatchFace.Engine engine = mWeakReference.get();
            if (engine != null) {
                switch (msg.what) {
                    case MSG_UPDATE_TIME:
                        engine.handleUpdateTimeMessage();
                        break;
                }
            }
        }
    }

    private class Engine extends CanvasWatchFaceService.Engine
            implements ConnectionCallbacks, OnConnectionFailedListener, DataApi.DataListener{

        final String WEARABLE_PATH = "/wearable";
        final String SUNSHINE_TEMP_HIGH_KEY = "sunshine_temp_high_key";
        final String SUNSHINE_TEMP_LOW_KEY = "sunshine_temp_low_key";
        final String SUNSHINE_WEATHER_ID_KEY = "sunshine_weather_id_key";

        private final Handler mUpdateTimeHandler = new EngineHandler(this);
        private boolean mRegisteredTimeZoneReceiver = false;
        private  Paint mBackgroundInteractivePaint;
        private  Paint mBackgroundAmbientPaint;
        private Paint mTimeHourPaint;
        private Paint mTimeMinPaint;
        private Paint mTimeSecPaint;
        private Paint mDatePaint;
        private  Paint mTemperatureMinPaint;
        private  Paint mTemperatureMaxPaint;
        private  Paint mWeatherInfoNotAvailablePaint;
        private Paint mLinePaint;
        private Rect mTextBounds=new Rect();
        private Bitmap mWeatherInteractiveIcon;
        private Bitmap mWeatherAmbientIcon;
        /**
         * Whether the display supports fewer bits for each color in ambient mode. When true, we
         * disable anti-aliasing in ambient mode.
         */
        boolean mLowBitAmbient;
        boolean mAmbient;



        final BroadcastReceiver mTimeZoneReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                Log.e("Sunshinewear", Thread.currentThread().getStackTrace()[2] + "");
                mDateFormat.setTimeZone(TimeZone.getDefault());
                mHourFormat.setTimeZone(TimeZone.getDefault());
                mMinFormat.setTimeZone(TimeZone.getDefault());
                invalidate();
            }
        };


        private Date mToday;
        private SimpleDateFormat mDateFormat;
        private SimpleDateFormat mHourFormat;
        private SimpleDateFormat mMinFormat;
        private String mHighTemperature;
        private String mLowTemperature;
        private String mWeatherIcon;
        private GoogleApiClient mGoogleApiClient;



        @Override
        public void onCreate(SurfaceHolder holder) {
            super.onCreate(holder);

            Log.e("Sunshinewear", Thread.currentThread().getStackTrace()[2] + "");

            setWatchFaceStyle(new WatchFaceStyle.Builder(MyWatchFace.this)
                    .setCardPeekMode(WatchFaceStyle.PEEK_MODE_VARIABLE)
                    .setBackgroundVisibility(WatchFaceStyle.BACKGROUND_VISIBILITY_INTERRUPTIVE)
                    .setShowSystemUiTime(false)
                    .build());
            Resources resources = MyWatchFace.this.getResources();

            mGoogleApiClient = new GoogleApiClient.Builder(getApplicationContext())
                    .addApi(Wearable.API)
                    .addConnectionCallbacks(this)
                    .addOnConnectionFailedListener(this)
                    .build();

            mBackgroundAmbientPaint = new Paint();
            mBackgroundAmbientPaint.setColor(resources.getColor(R.color.ambient_background));

            mBackgroundInteractivePaint = new Paint();
            mBackgroundInteractivePaint.setColor(resources.getColor(R.color.interactive_background));


            mTimeHourPaint = new Paint();
            mTimeHourPaint = createBoldTextPaint(resources.getColor(R.color.interactive_primary_text));
            mTimeMinPaint = new Paint();
            mTimeMinPaint = createTextPaint(resources.getColor(R.color.interactive_primary_text));
            mTimeSecPaint = new Paint();
            mTimeSecPaint = createTextPaint(resources.getColor(R.color.interactive_primary_text));

            mDatePaint = new Paint();
            mDatePaint = createTextPaint(resources.getColor(R.color.interactive_secondary_text));

            mTemperatureMaxPaint = new Paint();
            mTemperatureMaxPaint = createBoldTextPaint(resources.getColor(R.color.interactive_primary_text));
            mTemperatureMinPaint = new Paint();
            mTemperatureMinPaint = createTextPaint(resources.getColor(R.color.interactive_secondary_text));
            mWeatherInfoNotAvailablePaint = new Paint();
            mWeatherInfoNotAvailablePaint = createTextPaint(resources.getColor(R.color.interactive_secondary_text));


            mLinePaint=new Paint();
            mLinePaint.setColor(getResources().getColor(R.color.interactive_secondary_text));
            mLinePaint.setStrokeWidth(0.8f);
            mLinePaint.setAntiAlias(true);

            mToday=Calendar.getInstance().getTime();
            mDateFormat=new SimpleDateFormat("EEE, d MMM yyyy", Locale.getDefault());
            mHourFormat=new SimpleDateFormat("HH:", Locale.getDefault());
            mMinFormat=new SimpleDateFormat("mm", Locale.getDefault());

            mDateFormat.setTimeZone(TimeZone.getDefault());
            mHourFormat.setTimeZone(TimeZone.getDefault());
            mMinFormat.setTimeZone(TimeZone.getDefault());


        }

        private Bitmap setWeatherInteractiveIcon(String weatherIcon) {
            int resID = getResources().getIdentifier("ic_" + weatherIcon , "drawable", getPackageName());
            return BitmapFactory.decodeResource(getResources(), resID);
        }

        private Bitmap setWeatherAmbientIcon(String weatherIcon) {
            int resIDBW = getResources().getIdentifier("ic_" + weatherIcon + "_bw" , "drawable", getPackageName());
            return BitmapFactory.decodeResource(getResources(), resIDBW);
        }



        @Override
        public void onDestroy() {
            mUpdateTimeHandler.removeMessages(MSG_UPDATE_TIME);
            super.onDestroy();
        }

        private Paint createTextPaint(int textColor) {
            Paint paint = new Paint();
            paint.setColor(textColor);
            paint.setTypeface(NORMAL_TYPEFACE);
            paint.setAntiAlias(true);
            return paint;
        }

        private Paint createBoldTextPaint(int textColor) {
            Paint paint = new Paint();
            paint.setColor(textColor);
            paint.setTypeface(BOLD_TYPEFACE);
            paint.setAntiAlias(true);
            return paint;
        }

        @Override
        public void onVisibilityChanged(boolean visible) {
            super.onVisibilityChanged(visible);
            Log.e("Sunshinewear", Thread.currentThread().getStackTrace()[2]+"");
            if (visible) {
                registerReceiver();
                // Update time zone in case it changed while we weren't visible.
                mDateFormat.setTimeZone(TimeZone.getDefault());
                mHourFormat.setTimeZone(TimeZone.getDefault());
                mMinFormat.setTimeZone(TimeZone.getDefault());
                mGoogleApiClient.connect();
                Log.e("Sunshinewear", Thread.currentThread().getStackTrace()[2] + "google api is connected");
            } else {
                unregisterReceiver();
                if(mGoogleApiClient!=null&&mGoogleApiClient.isConnected()){
                    Wearable.DataApi.removeListener(mGoogleApiClient, this);
                    mGoogleApiClient.disconnect();
                }
            }

            // Whether the timer should be running depends on whether we're visible (as well as
            // whether we're in ambient mode), so we may need to start or stop the timer.
            updateTimer();
        }

        private void registerReceiver() {
            if (mRegisteredTimeZoneReceiver) {
                return;
            }
            mRegisteredTimeZoneReceiver = true;
            IntentFilter filter = new IntentFilter(Intent.ACTION_TIMEZONE_CHANGED);
            MyWatchFace.this.registerReceiver(mTimeZoneReceiver, filter);
        }

        private void unregisterReceiver() {
            if (!mRegisteredTimeZoneReceiver) {
                return;
            }
            mRegisteredTimeZoneReceiver = false;
            MyWatchFace.this.unregisterReceiver(mTimeZoneReceiver);
        }

        @Override
        public void onApplyWindowInsets(WindowInsets insets) {
            super.onApplyWindowInsets(insets);
            Log.e("Sunshinewear", Thread.currentThread().getStackTrace()[2] + "");
            Resources resources = MyWatchFace.this.getResources();

            mTimeHourPaint.setTextSize(resources.getDimension(R.dimen.time_text_size));
            mTimeMinPaint.setTextSize(resources.getDimension(R.dimen.time_text_size));
            mTimeSecPaint.setTextSize(resources.getDimension(R.dimen.time_text_size));
            mDatePaint.setTextSize(resources.getDimension(R.dimen.date_text_size));
            mTemperatureMinPaint.setTextSize(resources.getDimension(R.dimen.temp_text_size));
            mTemperatureMaxPaint.setTextSize(resources.getDimension(R.dimen.temp_text_size));
            mWeatherInfoNotAvailablePaint.setTextSize(resources.getDimension(R.dimen.date_text_size));
        }

        @Override
        public void onPropertiesChanged(Bundle properties) {
            super.onPropertiesChanged(properties);
            mLowBitAmbient = properties.getBoolean(PROPERTY_LOW_BIT_AMBIENT, false);
        }

        @Override
        public void onTimeTick() {
            super.onTimeTick();
            Log.e("Sunshinewear", Thread.currentThread().getStackTrace()[2] + "");
            invalidate();
        }

        @Override
        public void onAmbientModeChanged(boolean inAmbientMode) {
            super.onAmbientModeChanged(inAmbientMode);
            Log.e("Sunshinewear", Thread.currentThread().getStackTrace()[2] + "");
            if (mAmbient != inAmbientMode) {

                mAmbient = inAmbientMode;
                if (mLowBitAmbient) {
                    mTimeHourPaint.setAntiAlias(!inAmbientMode);
                    mTimeMinPaint.setAntiAlias(!inAmbientMode);
                    mTimeSecPaint.setAntiAlias(!inAmbientMode);
                    mDatePaint.setAntiAlias(!inAmbientMode);
                    mTemperatureMaxPaint.setAntiAlias(!inAmbientMode);
                    mTemperatureMinPaint.setAntiAlias(!inAmbientMode);
                    mWeatherInfoNotAvailablePaint.setAntiAlias(!inAmbientMode);
                }
                if(mAmbient){
                    mBackgroundInteractivePaint.setColor(getResources().getColor(R.color.ambient_background));
                    mTimeHourPaint.setColor(getResources().getColor(R.color.ambient_primary_secondary_text));
                    mTimeMinPaint.setColor(getResources().getColor(R.color.ambient_primary_secondary_text));
                    mDatePaint.setColor(getResources().getColor(R.color.ambient_primary_secondary_text));
                    mTemperatureMaxPaint.setColor(getResources().getColor(R.color.ambient_primary_secondary_text));
                    mTemperatureMinPaint.setColor(getResources().getColor(R.color.ambient_primary_secondary_text));
                    mWeatherInfoNotAvailablePaint.setColor(getResources().getColor(R.color.ambient_primary_secondary_text));
                    mTimeHourPaint.setTypeface(NORMAL_TYPEFACE);
                    mTemperatureMaxPaint.setTypeface(NORMAL_TYPEFACE);
                } else{
                    mBackgroundInteractivePaint.setColor(getResources().getColor(R.color.interactive_background));
                    mTimeHourPaint.setColor(getResources().getColor(R.color.interactive_primary_text));
                    mTimeMinPaint.setColor(getResources().getColor(R.color.interactive_primary_text));
                    mDatePaint.setColor(getResources().getColor(R.color.interactive_secondary_text));
                    mTemperatureMaxPaint.setColor(getResources().getColor(R.color.interactive_primary_text));
                    mTemperatureMinPaint.setColor(getResources().getColor(R.color.interactive_secondary_text));
                    mWeatherInfoNotAvailablePaint.setColor(getResources().getColor(R.color.interactive_secondary_text));
                    mTimeHourPaint.setTypeface(BOLD_TYPEFACE);
                    mTemperatureMaxPaint.setTypeface(BOLD_TYPEFACE);
                }
                invalidate();
            }

            // Whether the timer should be running depends on whether we're visible (as well as
            // whether we're in ambient mode), so we may need to start or stop the timer.
            //TODO if we don't display the sec, I don't think we need that, the 1 per min refresh caused by the onTick method should be enough
            updateTimer();
        }



        @Override
        public void onDraw(Canvas canvas, Rect bounds) {
            Paint backgroundPaint;
            Bitmap weatherIcon;
            Log.e("Sunshinewear", Thread.currentThread().getStackTrace()[2] + "" );
            if (isInAmbientMode()) {
                backgroundPaint=mBackgroundAmbientPaint;
                weatherIcon=mWeatherAmbientIcon;
            } else {
                backgroundPaint=mBackgroundInteractivePaint;
                weatherIcon=mWeatherInteractiveIcon;
            }


            Log.e("Sunshinewear", Thread.currentThread().getStackTrace()[2] + "" );
            canvas.drawRect(0, 0, bounds.width(), bounds.height(), backgroundPaint);

            int spaceY = 20;
            int spaceX = 10;
            int mTextDatePaintHeight;


            int centerX = bounds.width() / 2;
            int centerY = bounds.height() / 2;

            mToday.setTime(System.currentTimeMillis());
            String text;
            text = mDateFormat.format(mToday);
            mDatePaint.getTextBounds(text, 0, text.length(), mTextBounds);
            canvas.drawText(text, centerX - mTextBounds.width() / 2, centerY, mDatePaint);


            mTextDatePaintHeight= mTextBounds.height();

            text = mHourFormat.format(mToday);
            mTimeHourPaint.getTextBounds(text, 0, text.length(), mTextBounds);
            canvas.drawText(text, centerX - mTextBounds.width(), centerY - spaceY-mTextDatePaintHeight, mTimeHourPaint);

            text = mMinFormat.format(mToday);
            mTimeMinPaint.getTextBounds(text, 0, text.length(), mTextBounds);
            canvas.drawText(text, centerX + 4, centerY - spaceY - mTextDatePaintHeight, mTimeMinPaint);


            canvas.drawLine(centerX - 20, centerY + spaceY, centerX + 20, centerY + spaceY, mLinePaint);

            if(mHighTemperature !=null && mLowTemperature !=null && weatherIcon!=null) {
                mTemperatureMaxPaint.getTextBounds(mHighTemperature, 0, mHighTemperature.length(), mTextBounds);
                canvas.drawText(mHighTemperature, centerX - mTextBounds.width() / 2, centerY + spaceY + spaceY + mTextBounds.height(), mTemperatureMaxPaint);

                canvas.drawText(mLowTemperature, centerX + mTextBounds.width() / 2 + spaceX, centerY + spaceY + spaceY + mTextBounds.height(), mTemperatureMinPaint);

                canvas.drawBitmap(weatherIcon,
                        centerX - mTextBounds.width() / 2 - spaceX - weatherIcon.getWidth(),
                        centerY + spaceY + spaceY + mTextBounds.height() / 2 - weatherIcon.getHeight() / 2,
                        null);
            }else{
                text=getResources().getString(R.string.weather_info_not_available);
                mWeatherInfoNotAvailablePaint.getTextBounds(text, 0, text.length(), mTextBounds);
                canvas.drawText(text, centerX - mTextBounds.width() / 2, centerY + spaceY + spaceY + mTextBounds.height(), mWeatherInfoNotAvailablePaint);
            }


        }



        /**
         * Starts the {@link #mUpdateTimeHandler} timer if it should be running and isn't currently
         * or stops it if it shouldn't be running but currently is.
         */
        private void updateTimer() {
            mUpdateTimeHandler.removeMessages(MSG_UPDATE_TIME);
            if (shouldTimerBeRunning()) {
                mUpdateTimeHandler.sendEmptyMessage(MSG_UPDATE_TIME);
            }
        }

        /**
         * Returns whether the {@link #mUpdateTimeHandler} timer should be running. The timer should
         * only run when we're visible and in interactive mode.
         */
        private boolean shouldTimerBeRunning() {
            return isVisible() && !isInAmbientMode();
        }

        /**
         * Handle updating the time periodically in interactive mode.
         */
        private void handleUpdateTimeMessage() {
            invalidate();
            if (shouldTimerBeRunning()) {
                long timeMs = System.currentTimeMillis();
                long delayMs = INTERACTIVE_UPDATE_RATE_MS
                        - (timeMs % INTERACTIVE_UPDATE_RATE_MS);
                mUpdateTimeHandler.sendEmptyMessageDelayed(MSG_UPDATE_TIME, delayMs);
            }
        }

        @Override //GoogleApiClient.ConnectionCallbacks
        public void onConnected(Bundle bundle) {
            Log.v("Handheld log", "Handheld connected to wearable device");
                Wearable.DataApi.addListener(mGoogleApiClient, this);
            Log.e("Sunshinewear", Thread.currentThread().getStackTrace()[2] + "");
        }

        @Override //GoogleApiClient.ConnectionCallbacks
        public void onConnectionSuspended(int i) {
            Log.v("Handheld Log", "Handheld connection to wearable device is suspended");
            Log.e("Sunshinewear", Thread.currentThread().getStackTrace()[2] + "");
        }


        @Override //GoogleApiClient.OnConnectionFailedListener
        public void onConnectionFailed(ConnectionResult connectionResult) {
            Log.e("Handheld Log", "Handheld connection failed");
            Log.e("Sunshinewear", Thread.currentThread().getStackTrace()[2] + "");
        }

        @Override //DataApi.DataListener
        public void onDataChanged(DataEventBuffer dataEvents) {
            Log.e("Sunshinewear", Thread.currentThread().getStackTrace()[2] + "");
            for (DataEvent event : dataEvents) {
                Log.e("Sunshinewear", Thread.currentThread().getStackTrace()[2] + "");
                if (event.getType() == DataEvent.TYPE_CHANGED) {
                    String path = event.getDataItem().getUri().getPath();
                    Log.e("Sunshinewear", Thread.currentThread().getStackTrace()[2] + "path "+path);
                    if (WEARABLE_PATH.equals(path)) {
                        Log.e("Sunshinewear", Thread.currentThread().getStackTrace()[2] + "");
                        DataMapItem dataMapItem = DataMapItem.fromDataItem(event.getDataItem());
                        mHighTemperature = dataMapItem.getDataMap().getString(SUNSHINE_TEMP_HIGH_KEY);
                        mLowTemperature = dataMapItem.getDataMap().getString(SUNSHINE_TEMP_LOW_KEY);
                        mWeatherIcon = Utility.getArtUrlForWeatherCondition((long)dataMapItem.getDataMap().getInt(SUNSHINE_WEATHER_ID_KEY));
                        mWeatherInteractiveIcon = setWeatherInteractiveIcon(mWeatherIcon);
                        mWeatherAmbientIcon = setWeatherAmbientIcon(mWeatherIcon);
                        Log.e("Sunshinewear", Thread.currentThread().getStackTrace()[2] + "" + mHighTemperature + "/"+ mLowTemperature+" "+ mWeatherIcon);
                        invalidate();
                    }else{
                        Log.e("Watch Log", "Unrecognized path: " + path);
                    }
                }
            }
        }
    }
}
