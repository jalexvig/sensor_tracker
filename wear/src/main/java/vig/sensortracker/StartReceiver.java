package vig.sensortracker;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.util.Log;

import java.util.Calendar;

public class StartReceiver extends BroadcastReceiver {

    private static final String TAG = "StartReceiver";

    private AlarmManager mAlarmManager;
    private PendingIntent mAlarmIntent;


    @Override
    public void onReceive(Context context, Intent intent) {
        Log.d(TAG, "onReceive: Received");

        boolean start = getStartValue(context);

        setupSensorService(context, start);

        setupAPICalls(context, start);
    }

    private boolean getStartValue(Context context) {
        SharedPreferences sp = context.getSharedPreferences(Settings.SP_NAME_SETTINGS, Context.MODE_PRIVATE);

        return sp.getBoolean(Settings.RUN_BOOLEAN, true);
    }


    private void setupSensorService(Context context, boolean start) {

        Intent i = new Intent(context, MainService.class);

        context.stopService(i);
        Log.d(TAG, "setupAPICalls: Canceled sensor service.");

        if (start) {
            context.startService(i);
            Log.d(TAG, "setupAPICalls: Started sensor service.");
        }
    }

    private void setupAPICalls(Context c, boolean start) {

        mAlarmManager = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
        Intent intent = new Intent(c, APICallsReceiver.class);
        mAlarmIntent = PendingIntent.getBroadcast(c, 0, intent, 0);

        mAlarmManager.cancel(mAlarmIntent);
        Log.d(TAG, "setupAPICalls: Canceled API calls");

        if (start) {
            mAlarmManager.setRepeating(AlarmManager.RTC_WAKEUP, Calendar.getInstance().getTimeInMillis(),
                    1000 * 60, mAlarmIntent);
            Log.d(TAG, "setupAPICalls: Setup API calls");
        }
    }

}
