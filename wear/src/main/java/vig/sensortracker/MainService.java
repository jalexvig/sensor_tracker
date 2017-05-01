package vig.sensortracker;

import android.app.Service;
import android.content.Intent;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Binder;
import android.os.IBinder;
import android.support.annotation.Nullable;
import android.util.Log;

import org.apache.commons.lang3.SerializationUtils;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.util.List;

import vig.sensortracker.Constants.SensorAttrs;

public class MainService extends Service implements SensorEventListener {

    private static final String TAG = "MainService";
    private SensorManager mSensorManager;
    private SensorValueManagers.SensorValueManager mSensorValueManager;

    private IBinder mBinder;

    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY;
    }

    @Override
    public void onCreate() {
        super.onCreate();

        mBinder = new LocalBinder();

        mSensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);

        setupSensors();
    }

    protected void setupSensors() {

        mSensorValueManager = new SensorValueManagers.SensorValueManagerSP(this);

        mSensorManager.unregisterListener(this);

        List<SensorAttrs> storedSensorAttrs = getStoredSensorAttrs();

        if (storedSensorAttrs == null) {
            return;
        }

        for (SensorAttrs sa : storedSensorAttrs) {
            setupSensor(sa);
        }
    }

    private void setupSensor(SensorAttrs sa) {

        if (sa.getTolerance() < 0) {
            return;
        }

        mSensorValueManager.addSensorTolerance(sa.getSensorType(), sa.getTolerance());
        mSensorManager.registerListener(this, mSensorManager.getDefaultSensor(sa.getSensorType()), SensorManager.SENSOR_DELAY_NORMAL);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        mSensorManager.unregisterListener(this);
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    public class LocalBinder extends Binder {
        MainService getService() {
            return MainService.this;
        }
    }

    @Override
    public void onSensorChanged(SensorEvent sensorEvent) {
        mSensorValueManager.storeValues(sensorEvent);
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int i) {

    }

    private List<SensorAttrs> getStoredSensorAttrs() {
        List<SensorAttrs> result = null;

        try {
            FileInputStream fis = openFileInput(Settings.FILENAME_SENSOR_ATTRS);
            result = SerializationUtils.deserialize(fis);
        } catch (FileNotFoundException e) {
            Log.d(TAG, "getStoredSensorAttrs: No stored sensor data exist.");
        }

        return result;
    }
}
