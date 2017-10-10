package vig.sensortracker;

import android.Manifest;
import android.accounts.AccountManager;
import android.app.Activity;
import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.util.Log;
import android.view.View;
import android.widget.ImageButton;
import android.widget.Toast;

import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential;
import com.google.api.client.util.ExponentialBackOff;

import java.util.Arrays;
import java.util.Collection;
import java.util.Map;

public class MainActivity extends Activity implements MakeRequestTask.MakeRequestListener {

    private static final String TAG = "MainActivity";

    private static final int RC_PICK_ACCOUNT = 100;
    private static final int RC_OAUTH_ACCOUNT = 101;
    private static final int RCP_AUTH_CRED = 200;

    GoogleAccountCredential mCredential;

    private ImageButton mAPIButton;
    private ImageButton mExecutionButton;

    private boolean mServiceRunning;
    private SensorValueManagers.SensorValueManager mSensorValueManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mSensorValueManager = new SensorValueManagers.SensorValueManagerSP(this);

        mCredential = GoogleAccountCredential.usingOAuth2(
                getApplicationContext(), Arrays.asList(Settings.OAUTH_SCOPES))
                .setBackOff(new ExponentialBackOff());

        ensureServiceRunning();

        setupSyncButton();

        setupStopPlayButton();
    }

    private void setupStopPlayButton() {

        mExecutionButton = (ImageButton) findViewById(R.id.execution_button);
        mExecutionButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent i = new Intent(MainActivity.this, StartReceiver.class);
                updateRunningSP(!mServiceRunning);
                sendBroadcast(i);
                mServiceRunning = !mServiceRunning;
                updateRunningSP(mServiceRunning);
                int idDrawable = mServiceRunning ? R.drawable.ic_stop_white_48dp : R.drawable.ic_play_arrow_white_48dp;
                mExecutionButton.setImageResource(idDrawable);
            }
        });

    }

    private void setupSyncButton() {
        mAPIButton = (ImageButton) findViewById(R.id.sync_button);
        mAPIButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                mAPIButton.setEnabled(false);
                sendData();
                mAPIButton.setEnabled(true);
            }
        });
    }

    private void ensureServiceRunning() {
        if (!isMyServiceRunning(MainService.class)) {
            Intent i = new Intent(this, StartReceiver.class);
            sendBroadcast(i);
        }
        updateRunningSP(true);
        mServiceRunning = true;
    }

    private void updateRunningSP(boolean running) {
        SharedPreferences sp = getSharedPreferences(Settings.SP_NAME_SETTINGS, Context.MODE_PRIVATE);

        sp.edit().putBoolean(Settings.RUN_BOOLEAN, running).apply();
    }

    private boolean isMyServiceRunning(Class<?> serviceClass) {
        ActivityManager manager = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
        for (ActivityManager.RunningServiceInfo s : manager.getRunningServices(Integer.MAX_VALUE)) {
            if (serviceClass.getName().equals(s.service.getClassName())) {
                return true;
            }
        }
        return false;
    }

    private void sendData() {
        if (!setCredentialAccount()) return;

        Map<String, String> toSend = mSensorValueManager.getStoredValues();
        if (!toSend.isEmpty()) {
            Log.d(TAG, "sendData: account name " + mCredential.getSelectedAccountName());
            new MakeRequestTask(mCredential, toSend, this).execute();
        } else {
            Log.d(TAG, "No data to send.");
        }
    }

    private boolean setCredentialAccount() {

        if (mCredential.getSelectedAccountName() != null) {
            return true;
        } else {
            String accountName = getSettingsSP().getString(Settings.SP_ACCOUNT_NAME, null);

            if (accountName != null) {
                mCredential.setSelectedAccountName(accountName);
                return true;
            }
        }

        if (checkSelfPermission(Manifest.permission.GET_ACCOUNTS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.GET_ACCOUNTS}, RCP_AUTH_CRED);
        } else {
            startActivityForResult(
                    mCredential.newChooseAccountIntent(),
                    RC_PICK_ACCOUNT);
        }
        return false;
    }

    private SharedPreferences getSettingsSP() {
        return getSharedPreferences(Settings.SP_NAME_SETTINGS, Context.MODE_PRIVATE);
    }

    @Override
    public String getSpreadsheetId() {
        return getSettingsSP().getString(Settings.SP_SPREADSHEET_ID, null);
    }

    @Override
    public void setSpreadsheetId(String spreadsheetId) {
        getSettingsSP().edit().putString(Settings.SP_SPREADSHEET_ID, spreadsheetId).apply();
    }

    @Override
    public void oauthRequested(Intent oauthIntent) {
        startActivityForResult(oauthIntent, RC_OAUTH_ACCOUNT);
    }

    @Override
    public void retry() {
        sendData();
    }

    @Override
    public void submittedValues(Collection keysSubmitted) {
        mSensorValueManager.removeValues(keysSubmitted);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        switch (requestCode) {
            case RC_PICK_ACCOUNT:
                if (resultCode == RESULT_OK && data != null) {
                    String accountName =
                            data.getStringExtra(AccountManager.KEY_ACCOUNT_NAME);
                    getSettingsSP().edit().putString(Settings.SP_ACCOUNT_NAME, accountName).apply();
                    mCredential.setSelectedAccountName(accountName);
                    sendData();
                } else if (resultCode == RESULT_CANCELED) {
                    Toast.makeText(this, "User account selection canceled or no accounts found.", Toast.LENGTH_LONG).show();
                }
                break;
            case RC_OAUTH_ACCOUNT:
                if (resultCode == RESULT_OK) {
                    sendData();
                }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        switch (requestCode) {
            case RCP_AUTH_CRED:
                if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    sendData();
                } else {
                    Log.d(TAG, "onRequestPermissionsResult: " + Integer.toString(grantResults[0]));
                    Toast.makeText(this, "READ_CONTACTS Denied", Toast.LENGTH_SHORT).show();
                }
                break;
            default:
                break;
        }
    }
}
