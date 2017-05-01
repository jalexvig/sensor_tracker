package vig.sensortracker;

import java.io.Serializable;

public class Constants {
    public static final String SENSOR_RECEIVER_MESSAGE_PATH = "/sensors_receiver";

    public static final String SENSOR_MESSAGE_PATH = "/sensing";
    public static final String SENSOR_CAPABILITY = "sensing";

    public static final String SENSOR_TOLERANCES_PATH = "/sensor_tolerances";

    public static class SensorAttrs implements Serializable {

        public static final int DEFAULT_TOLERANCE = -1;

        private int mSensorType;
        private String mName;
        private float mTolerance;

        SensorAttrs(int sensorType, String name) {
            this(sensorType, name, DEFAULT_TOLERANCE);
        }

        SensorAttrs(int sensorType, String name, float tolerance) {
            mSensorType = sensorType;
            mName = name;
            mTolerance = tolerance;
        }

        public int getSensorType() {
            return mSensorType;
        }

        public String getName() {
            return mName;
        }

        public float getTolerance() {
            return mTolerance;
        }

        public void setTolerance(float tolerance) {
            mTolerance = tolerance;
        }
    }

}
