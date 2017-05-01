package vig.sensortracker;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

public class APICallsReceiver extends BroadcastReceiver {

    private static final String TAG = "APICallsReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        Log.d(TAG, "onReceive: Received");
        Intent i = new Intent(context, APIService.class);
        context.startService(i);
    }
}
