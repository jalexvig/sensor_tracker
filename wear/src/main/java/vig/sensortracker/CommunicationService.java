package vig.sensortracker;

import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.hardware.Sensor;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.os.IBinder;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.Log;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.wearable.CapabilityApi;
import com.google.android.gms.wearable.DataEvent;
import com.google.android.gms.wearable.DataEventBuffer;
import com.google.android.gms.wearable.DataItem;
import com.google.android.gms.wearable.MessageApi;
import com.google.android.gms.wearable.MessageEvent;
import com.google.android.gms.wearable.Node;
import com.google.android.gms.wearable.PutDataRequest;
import com.google.android.gms.wearable.Wearable;
import com.google.android.gms.wearable.WearableListenerService;

import org.apache.commons.lang3.SerializationUtils;

import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import vig.sensortracker.Constants.SensorAttrs;

public class CommunicationService extends WearableListenerService implements GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener {

    private static final String TAG = "CommunicationService";

    private GoogleApiClient mGoogleApiClient;
    private String mSensorReceiverNodeId = null;

    @Override
    public void onMessageReceived(MessageEvent messageEvent) {
        if (messageEvent.getPath().equals(Constants.SENSOR_MESSAGE_PATH)) {
            mSensorReceiverNodeId = messageEvent.getSourceNodeId();
            SensorManager sm = (SensorManager) getSystemService(SENSOR_SERVICE);
            sendSensorList(sm.getSensorList(Sensor.TYPE_ALL));
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        setGoogleApiClient();
        mGoogleApiClient.connect();
    }

    private void setGoogleApiClient() {
        mGoogleApiClient = new GoogleApiClient.Builder(this)
                .addApi(Wearable.API)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .build();
    }

    @Override
    public void onDataChanged(DataEventBuffer dataEventBuffer) {

        Log.d(TAG, "onDataChanged: Something changed on phone");

        for (DataEvent event : dataEventBuffer) {
            DataItem item = event.getDataItem();
            if (item.getUri().getPath().equals(Constants.SENSOR_TOLERANCES_PATH)) {
                byte[] serializedSensorAttrs = item.getData();
                if (storeSensorAttrs(serializedSensorAttrs)) {
                    refreshTolerances();
                }
            }
        }
    }

    private void refreshTolerances() {
        Intent i = new Intent(this, MainService.class);

        bindService(i, mServerConn, BIND_AUTO_CREATE);
    }

    private ServiceConnection mServerConn = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder binder) {
            Log.d(TAG, "onServiceConnected");
            MainService s = ((MainService.LocalBinder) binder).getService();
            s.setupSensors();
            unbindService(this);
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            Log.d(TAG, "onServiceDisconnected");
        }
    };

    private boolean storeSensorAttrs(byte[] data) {

        Log.d(TAG, "storeSensorAttrs: Storing new sensor tolerances.");

        try {
            FileOutputStream fos = openFileOutput(Settings.FILENAME_SENSOR_ATTRS, MODE_PRIVATE);
            fos.write(data);
            return true;
        } catch (IOException e) {
            Log.e(TAG, "storeSensorAttrs: Error serializing sensor attrs", e);
        }

        return false;
    }

    private void sendSensorList(List<Sensor> sensors) {

        byte[] toSend = serializeSensors(sensors);

        if (mSensorReceiverNodeId == null) {
            return;
        }

        Wearable.MessageApi.sendMessage(mGoogleApiClient, mSensorReceiverNodeId,
                Constants.SENSOR_RECEIVER_MESSAGE_PATH, toSend).setResultCallback(
                new ResultCallback<MessageApi.SendMessageResult>() {
                    @Override
                    public void onResult(MessageApi.SendMessageResult sendMessageResult) {
                        if (!sendMessageResult.getStatus().isSuccess()) {
                            Log.w(TAG, "onResult: Failed to send SensorAttrs list.", null);
                        } else {
                            Log.d(TAG, "onResult: Message sent.");
                        }
                    }

                }
        );
    }

    private byte[] serializeSensors(List<Sensor> sensors) {
        ArrayList<SensorAttrs> sensorAttrs = new ArrayList<>(sensors.size());

        for (Sensor sensor : sensors) {
            sensorAttrs.add(new SensorAttrs(sensor.getType(), sensor.getName()));
        }

        byte[] result = SerializationUtils.serialize(sensorAttrs);

        return result;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        mGoogleApiClient.disconnect();
    }

    @Override
    public void onConnected(@Nullable Bundle bundle) {

    }

    @Override
    public void onConnectionSuspended(int i) {

    }

    @Override
    public void onConnectionFailed(@NonNull ConnectionResult connectionResult) {

    }
}
