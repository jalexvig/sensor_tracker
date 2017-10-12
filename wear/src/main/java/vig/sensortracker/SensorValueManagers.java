package vig.sensortracker;

import android.content.Context;
import android.content.SharedPreferences;
import android.hardware.SensorEvent;

import org.apache.commons.lang3.StringUtils;

import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;


class SensorValueManagers {

    static class SensorValueManagerSP extends SensorValueManager {

        private static final String SP_NAME_SENSOR_VALUES = "SPSensorValues";

        private SharedPreferences mSharedPref;

        SensorValueManagerSP(Context c) {
            super();
            setSP(c);
        }

        SensorValueManagerSP(Context c, HashMap<Integer, SensorTolerance> tolerances) {
            super(tolerances);
            setSP(c);
        }

        private void setSP(Context c) {
            mSharedPref = c.getSharedPreferences(SP_NAME_SENSOR_VALUES, Context.MODE_PRIVATE);
        }

        @Override
        protected void storeInDB(long timestamp, int sensorType, float[] values) {
            String k = Long.toString(timestamp) + "_" + Integer.toString(sensorType);
            String v = StringUtils.join(values, '_');

            mSharedPref.edit().putString(k, v).apply();
        }

        @Override
        protected Map<String, String> getStoredValues() {

            return (Map<String, String>) mSharedPref.getAll();
        }

        @Override
        protected void removeValues(Collection collection) {
            SharedPreferences.Editor editor = mSharedPref.edit();
            for (Object k : collection) {
                editor.remove((String) k);
            }
            editor.apply();
        }
    }

    static abstract class SensorValueManager {

        private static final String TAG = "SensorValueManager";
        private HashMap<Integer, SensorTolerance> mTolerances;

        SensorValueManager() {
            this(new HashMap<Integer, SensorTolerance>());
        }

        SensorValueManager(HashMap<Integer, SensorTolerance> tolerances) {
            mTolerances = tolerances;
        }

        void addSensorTolerance(int sensorType, float tolerance, boolean cumulative) {
            mTolerances.put(sensorType, new SensorTolerance(tolerance, cumulative));
        }

        void addSensorTolerance(int sensorType, float tolerance) {
            mTolerances.put(sensorType, new SensorTolerance(tolerance));
        }

        void storeValues(SensorEvent sensorEvent) {

            int sensorType = sensorEvent.sensor.getType();
            float[] values = sensorEvent.values;

            if (mTolerances.get(sensorType).toleranceIsExceeded(values)) {
                long timeInMillis = (new Date()).getTime() + (sensorEvent.timestamp - System.nanoTime()) / 1000000L;
                storeInDB(timeInMillis, sensorType, values);
            }
        }

        protected abstract void storeInDB(long timestamp, int sensorType, float[] values);

        protected abstract Map<String, String> getStoredValues();

        protected abstract void removeValues(Collection collection);

        class SensorTolerance {

            private float mTolerance;
            private boolean mCumulative;

            private float[] prevValues = null;

            SensorTolerance(float tol, boolean cumulative) {
                mTolerance = tol;
                mCumulative = cumulative;
            }

            SensorTolerance(float difference) {
                this(difference, false);
            }

            boolean toleranceIsExceeded(float[] values) {

                if (prevValues == null) {
                    prevValues = values.clone();
                    return true;
                }

                float toCompare = 0;

                for (int i=0; i < values.length; i++) {
                    float candidate = Math.abs(prevValues[i] - values[i]);
                    if (mCumulative) {
                        toCompare += candidate;
                    } else {
                        if (candidate > toCompare) {
                            toCompare = candidate;
                        }
                    }

                    if (toCompare > mTolerance) {
                        prevValues = values.clone();
                        return true;
                    }
                }

                return false;
            }
        }
    }

}

