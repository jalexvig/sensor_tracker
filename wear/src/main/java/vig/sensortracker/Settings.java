package vig.sensortracker;

import com.google.api.services.sheets.v4.SheetsScopes;

public class Settings {
    static final String SP_NAME_SETTINGS = "Settings";

    static final String RUN_BOOLEAN = "Run";

    static final String SP_ACCOUNT_NAME = "Account Name";
    static final String SP_SPREADSHEET_ID = "SSID";

    static final String[] OAUTH_SCOPES = { SheetsScopes.SPREADSHEETS };

    static final String SPREADSHEET_TITLE = "Sensor Tracker Data";
    static final String FILENAME_SENSOR_ATTRS = "sensor_attrs";

    static final int MILLIS_BETWEEN_SUBMISSIONS = 1000 * 60;
}
