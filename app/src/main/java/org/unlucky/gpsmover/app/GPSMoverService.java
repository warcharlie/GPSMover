package org.unlucky.gpsmover.app;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.BitmapFactory;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.Criteria;
import android.location.Location;
import android.location.LocationManager;
import android.location.LocationProvider;
import android.os.Binder;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.SystemClock;
import android.support.v4.app.NotificationCompat;

import com.google.android.gms.maps.model.LatLng;

import org.unlucky.gpsmover.app.util.Common;

public class GPSMoverService extends Service
        implements SensorEventListener {
    private static final int UPDATE_INTERVAL_TIME = 1000;
    private static final int NOTIFICATION_ID = 0x1234;
    private static final float MAX_GRAVITY = 9.8f / 2;
    private static final float MIN_GRAVITY = -9.8f / 2;
    private static final double UPDATE_STEP = 0.000001;

    private double current_lat, current_lng;
    private SensorManager mSensorManager;
    private LocationManager mLocationManager;
    private NotificationManager mNotificationManager;

    public static GPSMoverService instance = null;

    public GPSMoverService() {
    }

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;
        mSensorManager = (SensorManager)getSystemService(Context.SENSOR_SERVICE);
        mLocationManager = (LocationManager)getSystemService(Context.LOCATION_SERVICE);
        mNotificationManager = (NotificationManager)getSystemService(Context.NOTIFICATION_SERVICE);
        mLocationManager.addTestProvider(LocationManager.GPS_PROVIDER, false, false, false, false,
                false, false, false, Criteria.POWER_LOW, Criteria.ACCURACY_FINE);
        // test part
        this.current_lng = 120.0;
        this.current_lat = 30.0;

        Common.log("service created!");
    }

    @Override
    public void onDestroy() {
        instance = null;
        mSensorManager.unregisterListener(this);
        stopForeground(true);
        Common.log("service destroyed!");
        super.onDestroy();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        super.onStartCommand(intent, flags, startId);
        mSensorManager.registerListener(this, mSensorManager.getDefaultSensor(Sensor.TYPE_GRAVITY),
                SensorManager.SENSOR_DELAY_UI);
        startForeground(NOTIFICATION_ID, createNotification(createLocation()));
        handler.post(updateGpsThread);
        Common.log("service started!");
        return Service.START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        MyBinder mBinder = new MyBinder();
        return mBinder;
    }

    /**
     * Create customized notification
     * @return customized notification
     */
    private Notification createNotification(Location location) {
        NotificationCompat.Builder mBuilder = new NotificationCompat.Builder(this);
        mBuilder.setLargeIcon(BitmapFactory.decodeResource(getResources(), R.drawable.ic_launcher))
                .setSmallIcon(R.drawable.ic_launcher)
                .setContentTitle(getResources().getString(R.string.app_name))
                .setContentText(String.format(getResources()
                        .getString(R.string.msg_fake_gps), location.getLongitude(), location.getLatitude()))
                .setAutoCancel(false);

        Intent intent = new Intent(getApplicationContext(), MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(getApplicationContext(), 0,
                intent, PendingIntent.FLAG_UPDATE_CURRENT);//can be clicked repeatedly

        Notification notification = mBuilder.setContentIntent(pendingIntent).build();
        notification.flags |= Notification.FLAG_NO_CLEAR;//don't clear notification

        return notification;
    }

    private Location createLocation() {
        Location location = new Location(LocationManager.GPS_PROVIDER);
        location.setLatitude(current_lat);
        location.setLongitude(current_lng);
        //location.setAltitude(0.0);
        location.setAccuracy(1.0f);
        if (Build.VERSION.SDK_INT >= 17) {
            location.setElapsedRealtimeNanos(SystemClock.elapsedRealtimeNanos());
        }
        location.setTime(System.currentTimeMillis());

        return location;
    }

    Handler handler = new Handler();

    Runnable updateGpsThread = new Runnable() {
        @Override
        public void run() {
            Location location = createLocation();
            mLocationManager.setTestProviderEnabled(LocationManager.GPS_PROVIDER, true);
            mLocationManager.setTestProviderStatus(LocationManager.GPS_PROVIDER, LocationProvider.AVAILABLE,
                    null, System.currentTimeMillis());
            mLocationManager.setTestProviderLocation(LocationManager.GPS_PROVIDER, location);// update location
            startForeground(NOTIFICATION_ID, createNotification(location));// update notification
            handler.postDelayed(updateGpsThread, UPDATE_INTERVAL_TIME);
        }
    };

    public class MyBinder extends Binder {
        /**
         * Get current location
         * @return current location
         */
        public LatLng getLocation() {
            return new LatLng(current_lat, current_lng);
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {

    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event.sensor.getType() == Sensor.TYPE_GRAVITY) {
            float x = event.values[0];
            x = Math.min(MAX_GRAVITY, x);
            x = Math.max(MIN_GRAVITY, x);
            // change longitude, move toward west when x > 0.0, move toward east when x < 0.0
            current_lng -= UPDATE_STEP * x;
            // when longitude overflow
            if (current_lng > 180.0) {
                current_lng = current_lng - 360.0;
            } else if (current_lng < -180.0) {
                current_lng = 360.0 - current_lng;
            }

            float y = event.values[1];
            y = Math.min(MAX_GRAVITY, y);
            y = Math.max(MIN_GRAVITY, y);
            current_lat -= UPDATE_STEP * y;
            // when latitude overflow
            if (current_lat > 90.0) {
                current_lat = 90.0;
            } else if (current_lat < -90.0) {
                current_lat = -90.0;
            }
        }
    }
}