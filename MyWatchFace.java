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
 *
 * 2015/11/11
 *
 *      http://candyapple.dip.jp
 *
 * kzy.nishi@me.com
 *
 */

package com.candyapple.mywatch04;

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
import android.graphics.RectF;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.v4.content.ContextCompat;
import android.support.wearable.watchface.CanvasWatchFaceService;
import android.support.wearable.watchface.WatchFaceStyle;
import android.view.SurfaceHolder;
import java.lang.ref.WeakReference;
import java.util.Calendar;
import java.util.TimeZone;
import java.util.concurrent.TimeUnit;
import static java.lang.String.valueOf;

/**
 * Analog watch face with a ticking second hand. In ambient mode, the second hand isn't shown. On
 * devices with low-bit ambient mode, the hands are drawn without anti-aliasing in ambient mode.
 */
public class MyWatchFace extends CanvasWatchFaceService {
    /**
     * Update rate in milliseconds for interactive mode. We update once a second to advance the
     * second hand.
     */
    private static final long INTERACTIVE_UPDATE_RATE_MS = TimeUnit.SECONDS.toMillis(1);    // 1秒毎に
    /**
     * Handler message id for updating the time periodically in interactive mode.
     */
    private static final int MSG_UPDATE_TIME = 0;
    //public Calendar calendar = Calendar.getInstance();

    @Override
    public Engine onCreateEngine() {
        return new Engine();
    }

    private class Engine extends CanvasWatchFaceService.Engine {
        private Paint mBackgroundPaint;
        private Paint mTextPaint, mTimePaint;
        private boolean mAmbient;
        private final int mTextSize = 50;
        private final int mTimeSize = 25;
        private final int txtPosX = 100;
        private final int txtPosY = 80;
        private final int timPosX = 150;
        private final int timPosY = 30;
        private Calendar mCalendar;
        private final Handler mUpdateTimeHandler = new EngineHandler(this);
        private Typeface tf;
        private String textFont = "SpecialElite.ttf";
//        private Context context;
        private Bitmap bmp;
        private int bWidth, bHeight;

        /**
         * ブロードキャストレシーバ（mTimeZoneReceiver）を生成する。
         * ブロードキャストレシーバはシステムから送信されるブロードキャストインテントを受信。
         *　（時刻が変わった等のシステムのイベントを知る事ができる。）
         */
        final BroadcastReceiver mTimeZoneReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                // time-zoneの設定
                mCalendar.setTimeZone(TimeZone.getTimeZone(intent.getStringExtra("time-zone")));
            }
        };
        boolean mRegisteredTimeZoneReceiver = false;

        /**
         * Whether the display supports fewer bits for each color in ambient mode. When true, we
         * disable anti-aliasing in ambient mode.
         */
        boolean mLowBitAmbient;

        /**
         * onCreateはプログラム起動の際に一回だけ実行される。
         * ここで使用する画像を読み込んでおく。色の設定などのここで行う。
         * 各クラスからインスタンスの生成もここで実行
         */
        @Override
        public void onCreate(SurfaceHolder holder) {
            super.onCreate(holder);

            setWatchFaceStyle(new WatchFaceStyle.Builder(MyWatchFace.this)
                    .setCardPeekMode(WatchFaceStyle.PEEK_MODE_SHORT)
                    .setBackgroundVisibility(WatchFaceStyle.BACKGROUND_VISIBILITY_INTERRUPTIVE)
                    .setShowSystemUiTime(false)
                    .build());

            // 文字盤背景
            mBackgroundPaint = new Paint();
            mBackgroundPaint.setColor(ContextCompat.getColor(getApplicationContext(), R.color.analog_background));

            /**
             * 組み込みフォント以外のフォントを使う。
             * フォントファイルは\app\src\main\assets\Fontfile.ttf に置いておく。
             */
            tf = Typeface.createFromAsset(getBaseContext().getAssets(), textFont);  // 外部フォントの読み込み。
            mTextPaint = new Paint();
            mTextPaint.setTypeface(tf);             // フォントの設定
            mTextPaint.setTextSize(mTextSize);      // テキストサイズの設定
            mTextPaint.setColor(ContextCompat.getColor(getApplicationContext(), R.color.analog_sec));

            //　画面下部の日付表示（全情報）
            mTimePaint = new Paint();
            mTimePaint.setTextSize(mTimeSize);
            mTimePaint.setColor(ContextCompat.getColor(getApplicationContext(), R.color.analog_sec));

            // 画像ファイルの読み込み
            Resources r = MyWatchFace.this.getResources();
//            bmp = BitmapFactory.decodeResource(r, R.drawable.vect_or);
            bmp = BitmapFactory.decodeResource(r, R.drawable.badapple);
            bWidth = bmp.getWidth();
            bHeight = bmp.getHeight();
        }

        @Override
        public void onDestroy() {
            mUpdateTimeHandler.removeMessages(MSG_UPDATE_TIME);
            super.onDestroy();
        }

        @Override
        public void onPropertiesChanged(Bundle properties) {
            super.onPropertiesChanged(properties);
            mLowBitAmbient = properties.getBoolean(PROPERTY_LOW_BIT_AMBIENT, false);
        }

        /**
         * AmbientModeがONの時に、1分ごとに1回呼ばれる。
         * AmbientModeでないときは、呼ばれない。
         */
        @Override
        public void onTimeTick() {
            super.onTimeTick();
            invalidate();
        }

        /**
         * 時計にしばらく触れていないとAmbientModeに移行し、省電力モードに入る。
         * AmbientModeのときは、ウォッチフェイスを白黒表示にするのが好ましい。
         */
        @Override
        public void onAmbientModeChanged(boolean inAmbientMode) {
            super.onAmbientModeChanged(inAmbientMode);
            if (mAmbient != inAmbientMode) {
                mAmbient = inAmbientMode;
                if (mLowBitAmbient) {
                    // 省電力モードでの処理
                    System.out.print("ambient mode now!!");
                }
                invalidate();
            }

            // Whether the timer should be running depends on whether we're visible (as well as
            // whether we're in ambient mode), so we may need to start or stop the timer.
            updateTimer();
        }

        /**
         * ここでウォッチフェイスの描画を行う
         */
        @Override
        public void onDraw(Canvas canvas, Rect bounds) {
            int width = bounds.width();
            int height = bounds.height();

            //ここで現在時刻を設定する。
            mCalendar = Calendar.getInstance();
            mCalendar.setTimeInMillis(System.currentTimeMillis());
            int year = mCalendar.get(Calendar.YEAR);
            int month = mCalendar.get(Calendar.MONTH) + 1;
            int day = mCalendar.get(Calendar.DATE);
            float hour = mCalendar.get(Calendar.HOUR_OF_DAY);
            float minute = mCalendar.get(Calendar.MINUTE);
            float second = mCalendar.get(Calendar.SECOND);
//            float msecond = mCalendar.get(Calendar.MILLISECOND);     // 1000分の1秒

            //
            String sTime = (int)hour + ":" + (int)minute + ":" + (int)second;
            String ambTime = (int)hour + ":" + (int)minute;
            String lTime = year + "/" + month + "/" + day;

            Paint.FontMetrics fm = mTextPaint.getFontMetrics();      //　表示する文字列のサイズを取得
            float textHight = fm.ascent - fm.descent;

            // mAmbienが false（偽）の時に実行
            if (!mAmbient) {
                // 背景を描画
                Rect src = new Rect(0, 0, bWidth, bHeight);     // 描画元領域
                Rect dst = new Rect(0, 0, width, height);       // 描画先領域（拡大、縮小される）
                canvas.drawBitmap(bmp, src, dst, mBackgroundPaint);
                //
                canvas.drawText(lTime, timPosX, timPosY, mTimePaint);
                canvas.drawText(sTime, txtPosX, txtPosY, mTextPaint);
            } else {
                // Draw the background.
                canvas.drawRect(0, 0, canvas.getWidth(), canvas.getHeight(), mBackgroundPaint);
                canvas.drawText(ambTime, txtPosX, txtPosY, mTextPaint);
            }
        }

        /**
         * ウォッチフェイスの表示/非表示に合わせて描画処理を開始/停止する
         */
        @Override
        public void onVisibilityChanged(boolean visible) {
            super.onVisibilityChanged(visible);

            if (visible) {
                registerReceiver();
                // Update time zone in case it changed while we weren't visible.
                mCalendar.clear();          // TODO

            } else {
                unregisterReceiver();
            }
            // Whether the timer should be running depends on whether we're visible (as well as
            // whether we're in ambient mode), so we may need to start or stop the timer.
            updateTimer();
        }

        // ACTION_TIMEZONE_CHANGEDを受信するレシーバー（mTimeZoneReceiver）を登録する。
        // mRegisteredTimeZoneReceiver　＝＞　mTimeZoneReceiverが登録されているかどうかのフラグ
        private void registerReceiver() {
            if (mRegisteredTimeZoneReceiver) {
                return;
            }
            mRegisteredTimeZoneReceiver = true;
            IntentFilter filter = new IntentFilter(Intent.ACTION_TIMEZONE_CHANGED);
            MyWatchFace.this.registerReceiver(mTimeZoneReceiver, filter);
        }

        // mTimeZoneReceiverの登録を解除する。
        private void unregisterReceiver() {
            if (!mRegisteredTimeZoneReceiver) {
                return;
            }
            mRegisteredTimeZoneReceiver = false;
            MyWatchFace.this.unregisterReceiver(mTimeZoneReceiver);
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
}
