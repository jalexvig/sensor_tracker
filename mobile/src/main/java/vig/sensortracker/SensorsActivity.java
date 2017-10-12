package vig.sensortracker;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.text.InputType;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.PendingResult;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.wearable.CapabilityApi;
import com.google.android.gms.wearable.DataApi;
import com.google.android.gms.wearable.MessageApi;
import com.google.android.gms.wearable.MessageEvent;
import com.google.android.gms.wearable.Node;
import com.google.android.gms.wearable.PutDataRequest;
import com.google.android.gms.wearable.Wearable;

import org.apache.commons.lang3.SerializationUtils;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Set;

import vig.sensortracker.Constants.SensorAttrs;

public class SensorsActivity extends Activity implements GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener, MessageApi.MessageListener {

    private static final String TAG = "SensorsActivity";

    private static final String FILENAME_SENSOR_ATTRS = "sensor_attrs";

    private GoogleApiClient mGoogleApiClient;
    private String mSensorNodeId = null;

    private Button mRefreshSensorsButton;
    private Button mSaveTolerancesButton;
    private Button mCancelButton;

    SensorArrayAdapter mAdapter;
    List<SensorAttrs> mSensorAttrs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_sensors);

        mRefreshSensorsButton = (Button) findViewById(R.id.refresh_sensors_button);
        mRefreshSensorsButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                updateSensors();
            }
        });

        mSaveTolerancesButton = (Button) findViewById(R.id.save_tolerances_button);
        mSaveTolerancesButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                saveSensorAttrs();
                finish();
            }
        });

        mCancelButton = (Button) findViewById(R.id.cancel_button);
        mCancelButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                finish();
            }
        });

        mSensorAttrs = getStoredSensorAttrs();

        mAdapter = new SensorArrayAdapter(this);

        setupListView();

        setGoogleApiClient();

        if (mSensorAttrs.isEmpty()) {
            updateSensors();
        }

//        TODO: Should only get list of sensors currently available... no need to store on mobile (also get their tolerances this way as well)
    }

    private void setupListView() {

        ListView listView = (ListView) findViewById(R.id.list_view);
        listView.setAdapter(mAdapter);

        listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> adapterView, View view, int i, long l) {

                final SensorAttrs sa = mSensorAttrs.get(i);

                AlertDialog.Builder alert = new AlertDialog.Builder(SensorsActivity.this);
                alert.setTitle("Tolerance for " + sa.getName());

                final EditText inputET = new EditText(SensorsActivity.this);
                inputET.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
                String sVal = Float.toString(sa.getTolerance());
                inputET.setText(sVal);
                inputET.setSelection(sVal.length());

                alert.setView(inputET);

                alert.setPositiveButton("Ok", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int whichButton) {
                        String sVal = inputET.getText().toString();
                        float val = Float.parseFloat(sVal);
                        sa.setTolerance(val);
                        mAdapter.notifyDataSetChanged();
                    }
                });

                alert.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int whichButton) {
                        //Put actions for CANCEL button here, or leave in blank
                    }
                });

                AlertDialog a = alert.create();
                a.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE);

                a.show();
            }
        });
    }

    private class SensorArrayAdapter extends ArrayAdapter<SensorAttrs> {

        public SensorArrayAdapter(Context context) {
            super(context, 0, mSensorAttrs);
        }

        @NonNull
        @Override
        public View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
            SensorAttrs sensor = getItem(position);

            if (convertView == null) {
                convertView = LayoutInflater.from(getContext()).inflate(R.layout.list_item_sensor, parent, false);
            }

            TextView sensorNameTV = (TextView) convertView.findViewById(R.id.sensor_name_textview);
            TextView sensorToleranceTV = (TextView) convertView.findViewById(R.id.tolerance_textview);

            sensorNameTV.setText(sensor.getName());
            sensorToleranceTV.setText(Float.toString(sensor.getTolerance()));

            return convertView;
        }
    }

    private List<SensorAttrs> getStoredSensorAttrs() {
        List<SensorAttrs> result = new ArrayList<>();

        try {
            FileInputStream fis = openFileInput(FILENAME_SENSOR_ATTRS);
            result = SerializationUtils.deserialize(fis);
        } catch (FileNotFoundException e) {
            Log.d(TAG, "SensorAttrs local file doesn't exist.");
        }

        return result;
    }

    private void saveSensorAttrs() {
        byte[] serializedSensorAttrs = storeSensorAttrs();
        if (serializedSensorAttrs != null) {
            sendSensorAttrs(serializedSensorAttrs);
        }
    }

    private void sendSensorAttrs(byte[] data) {
        PutDataRequest pdr = PutDataRequest.create("/sensor_tolerances");
        pdr.setUrgent();
        pdr.setData(data);
        PendingResult<DataApi.DataItemResult> pendingResult =
                Wearable.DataApi.putDataItem(mGoogleApiClient, pdr);
        pendingResult.setResultCallback(new ResultCallback<DataApi.DataItemResult>() {
            @Override
            public void onResult(@NonNull DataApi.DataItemResult dataItemResult) {
                if (!dataItemResult.getStatus().isSuccess()) {
                    Log.d(TAG, "onResult: Failed to send tolerances.");
                }
            }
        });
    }

    private byte[] storeSensorAttrs() {

        try {
            FileOutputStream fos = openFileOutput(FILENAME_SENSOR_ATTRS, MODE_PRIVATE);
            byte[] result = SerializationUtils.serialize((Serializable) mSensorAttrs);
            fos.write(result);
            return result;
        } catch (IOException e) {
            Log.e(TAG, "storeSensorAttrs: Error serializing sensor attrs", e);
        }

        return null;
    }

    @Override
    public void onMessageReceived(MessageEvent messageEvent) {
        if (messageEvent.getPath().equals(Constants.SENSOR_RECEIVER_MESSAGE_PATH)) {
            byte[] data = messageEvent.getData();
            List<SensorAttrs> sensorAttrs = SerializationUtils.deserialize(data);

            setCombinedSensorAttrs(sensorAttrs);
        }
    }

    private void setCombinedSensorAttrs(List<SensorAttrs> newSensorAttrs) {

        HashMap<Integer, SensorAttrs> hm = new HashMap<>();
        if (mSensorAttrs != null) {
            for (SensorAttrs sa : mSensorAttrs) {
                hm.put(sa.getSensorType(), sa);
            }
        }

        List<SensorAttrs> sensorAttrs = new ArrayList<>(newSensorAttrs.size());

        for (SensorAttrs sa : newSensorAttrs) {
            SensorAttrs saOld = hm.get(sa.getSensorType());
            float tolerance = (saOld != null) ? saOld.getTolerance() : SensorAttrs.DEFAULT_TOLERANCE;
            sensorAttrs.add(new SensorAttrs(sa.getSensorType(), sa.getName(), tolerance));
        }

        mSensorAttrs.clear();
        mSensorAttrs.addAll(sensorAttrs);

        mAdapter.notifyDataSetChanged();
    }

    private void requestSensorList() {
        if (mSensorNodeId == null) {
            return;
        }
        Wearable.MessageApi.sendMessage(mGoogleApiClient, mSensorNodeId,
                Constants.SENSOR_MESSAGE_PATH, null).setResultCallback(
                new ResultCallback<MessageApi.SendMessageResult>() {
                    @Override
                    public void onResult(MessageApi.SendMessageResult sendMessageResult) {
                        if (!sendMessageResult.getStatus().isSuccess()) {
                            Log.w(TAG, "onResult: Failed to send sensor sync message to wearable", null);
                        }
                    }

                }
        );
    }

    private void setGoogleApiClient() {
        mGoogleApiClient = new GoogleApiClient.Builder(this)
                .addApi(Wearable.API)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .build();
    }

    private void updateSensors() {

        Wearable.CapabilityApi.getCapability(mGoogleApiClient, Constants.SENSOR_CAPABILITY,
                CapabilityApi.FILTER_REACHABLE).setResultCallback(
                new ResultCallback<CapabilityApi.GetCapabilityResult>() {
                    @Override
                    public void onResult(@NonNull CapabilityApi.GetCapabilityResult getCapabilityResult) {
                        if (getCapabilityResult.getStatus().isSuccess()) {
                            Log.d(TAG, "onResult: Got capability results");
                            mSensorNodeId = getBestNodeId(getCapabilityResult.getCapability().getNodes());
                            if (mSensorNodeId == null) {
                                Toast.makeText(SensorsActivity.this, "Couldn't find wearable app to connect to.", Toast.LENGTH_SHORT).show();
                            } else {
                                requestSensorList();
                            }
                        } else {
                            Log.w(TAG, "onResult: Didn't get capability results.", null);
                        }
                    }
                }
        );
    }

    private String getBestNodeId(Set<Node> nodes) {
        String bestNodeId = null;
        for (Node node : nodes) {
            if (node.isNearby()) {
                return node.getId();
            }
            bestNodeId = node.getId();
        }
        return bestNodeId;
    }

    @Override
    public void onConnected(@Nullable Bundle bundle) {
        Log.d(TAG, "onConnected: Google APIs");

        Wearable.MessageApi.addListener(mGoogleApiClient, this);
    }

    @Override
    public void onConnectionSuspended(int i) {
        Log.d(TAG, "onConnectionSuspended: Google APIs");
        Toast.makeText(this, "Google API connection suspended", Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onConnectionFailed(@NonNull ConnectionResult connectionResult) {
        Log.d(TAG, "onConnectionFailed: Google APIs");
        Toast.makeText(this, "Couldn't connect to Google APIs", Toast.LENGTH_SHORT).show();
    }

    @Override
    protected void onStart() {
        super.onStart();
        mGoogleApiClient.connect();
    }

    @Override
    protected void onStop() {
        super.onStop();
        Wearable.MessageApi.removeListener(mGoogleApiClient, this);
        mGoogleApiClient.disconnect();
    }
}
