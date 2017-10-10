package vig.sensortracker;

import android.Manifest;
import android.app.IntentService;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.support.annotation.Nullable;
import android.util.Log;

import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential;
import com.google.api.client.util.ExponentialBackOff;

import java.util.Arrays;
import java.util.Collection;
import java.util.Map;

public class APIService extends IntentService implements MakeRequestTask.MakeRequestListener {

    private static final String TAG = "APIService";

    private SensorValueManagers.SensorValueManager mSensorValueManager;

    GoogleAccountCredential mCredential;

    public APIService() {
        super("APIService");
    }


    @Override
    public void onCreate() {
        super.onCreate();
        mSensorValueManager = new SensorValueManagers.SensorValueManagerSP(this);

        mCredential = GoogleAccountCredential.usingOAuth2(
                getApplicationContext(), Arrays.asList(Settings.OAUTH_SCOPES))
                .setBackOff(new ExponentialBackOff());
    }

    public void sendData() {
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

        if (mCredential.getSelectedAccountName() == null) {
            if (checkSelfPermission(Manifest.permission.GET_ACCOUNTS) != PackageManager.PERMISSION_GRANTED) {
                logErrorMessage("Open app to allow permissions.");
                return false;
            }

            String accountName = getSettingsSP().getString(Settings.SP_ACCOUNT_NAME, null);
            if (accountName != null) {
                mCredential.setSelectedAccountName(accountName);
            } else {
                logErrorMessage("Open app to select account.");
                return false;
            }
        }
        return true;
    }

    private void logErrorMessage(String s) {

        Log.w(TAG, "logErrorMessage: " + s, null);
    }

    @Override
    protected void onHandleIntent(@Nullable Intent intent) {
        sendData();
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
        logErrorMessage("Start app and sync to OAuthorize");
    }

    @Override
    public void retry() {
        sendData();
    }
    
    @Override
    public void submittedValues(Collection keysSubmitted) {
        mSensorValueManager.removeValues(keysSubmitted);
    }
}
