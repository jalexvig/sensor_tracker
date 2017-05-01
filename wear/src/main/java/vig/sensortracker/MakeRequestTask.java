package vig.sensortracker;

import android.content.Intent;
import android.os.AsyncTask;
import android.util.Log;

import com.google.api.client.extensions.android.http.AndroidHttp;
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential;
import com.google.api.client.googleapis.extensions.android.gms.auth.UserRecoverableAuthIOException;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.services.sheets.v4.model.CellData;
import com.google.api.services.sheets.v4.model.ExtendedValue;
import com.google.api.services.sheets.v4.model.GridData;
import com.google.api.services.sheets.v4.model.RowData;
import com.google.api.services.sheets.v4.model.Spreadsheet;
import com.google.api.services.sheets.v4.model.SpreadsheetProperties;
import com.google.api.services.sheets.v4.model.ValueRange;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import static vig.sensortracker.Settings.SPREADSHEET_TITLE;

public class MakeRequestTask extends AsyncTask<Void, Void, Boolean> {
    private static final String TAG = "MakeRequestAsyncTask";
    private com.google.api.services.sheets.v4.Sheets mService = null;

    private Map<String, String> mData;
    private String mSpreadsheetId;
    private Intent mOauthIntent;
    private MakeRequestListener mListener;

    MakeRequestTask(GoogleAccountCredential credential, Map toSend, MakeRequestListener listener) {
        this(credential, toSend, null, listener);
    }

    MakeRequestTask(GoogleAccountCredential credential, Map toSend, String spreadsheetId, MakeRequestListener listener) {
        HttpTransport transport = AndroidHttp.newCompatibleTransport();
        JsonFactory jsonFactory = JacksonFactory.getDefaultInstance();
        mService = new com.google.api.services.sheets.v4.Sheets.Builder(
                transport, jsonFactory, credential)
                .setApplicationName("Sensor Tracker")
                .build();
        mData = toSend;
        mSpreadsheetId = spreadsheetId;
        mListener = listener;
    }

    @Override
    protected void onPreExecute() {
        super.onPreExecute();
        mSpreadsheetId = mListener.getSpreadsheetId();
    }

    @Override
    protected Boolean doInBackground(Void... params) {
        try {
            if (mSpreadsheetId == null) {
                createSpreadsheet();
                return true;
            } else {
                sendData();
            }
        } catch (UserRecoverableAuthIOException e) {
            mOauthIntent = e.getIntent();
        } catch (Exception e) {
            Log.e(TAG, "doInBackground: ", e);
            cancel(true);
        }
        return false;
    }

    private void createSpreadsheet() throws IOException {

        Spreadsheet requestBody = new Spreadsheet();
        requestBody.setProperties(new SpreadsheetProperties().setTitle(SPREADSHEET_TITLE));
        Spreadsheet s = mService.spreadsheets().create(requestBody).execute();
        mSpreadsheetId = s.getSpreadsheetId();
    }

    private void sendData() throws IOException {
        ValueRange vr = new ValueRange();
        List<List<Object>> data = new LinkedList<>();
        for (String k: mData.keySet()) {
            Object[] a = (k + '_' + mData.get(k)).split("_");
            data.add(Arrays.asList(a));
        }
        vr.setValues(data);
        mService.spreadsheets().values().append(mSpreadsheetId, "A:B", vr).setValueInputOption("USER_ENTERED").execute();
    }


    @Override
    protected void onPostExecute(Boolean retry) {
        if (mOauthIntent != null) {
            mListener.oauthRequested(mOauthIntent);
        } else if (retry) {
            mListener.setSpreadsheetId(mSpreadsheetId);
            mListener.retry();
        } else {
            mListener.submittedValues(mData.keySet());
        }
    }

    @Override
    protected void onCancelled() {
        Log.w(TAG, "onCancelled: Request cancelled", null);
    }

    public interface MakeRequestListener {

        String getSpreadsheetId();

        void setSpreadsheetId(String spreadsheetId);

        void oauthRequested(Intent oauthIntent);

        void retry();

        void submittedValues(Collection keysSubmitted);
    }
}
