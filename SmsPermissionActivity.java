package com.example.inventoryapp;

import android.Manifest;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.widget.Button;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

/**
 * SmsPermissionActivity is shown once after the user's first successful login.
 *
 * It explains why the app wants SMS permission (low-stock alerts) and
 * gives the user a clear choice to allow or deny. Either way, the app
 * continues to function normally — SMS alerts are simply suppressed if denied.
 */
public class SmsPermissionActivity extends AppCompatActivity {

    // Android runtime permission request code (any unique int)
    private static final int SMS_PERMISSION_REQUEST_CODE = 101;

    // SharedPreferences keys shared with MainActivity
    private static final String PREFS_NAME      = "InventoryAppPrefs";
    private static final String PREFS_SMS_ASKED = "smsPermissionAsked";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_sms_permission);

        Button buttonAllow = findViewById(R.id.buttonAllowSMS);
        Button buttonDeny  = findViewById(R.id.buttonDenySMS);

        // User taps "Allow" — trigger the Android system permission dialog
        buttonAllow.setOnClickListener(v -> requestSmsPermission());

        // User taps "No Thanks" — proceed without SMS; app still fully functional
        buttonDeny.setOnClickListener(v -> proceedWithoutSms());
    }

    /**
     * Triggers the Android system permission dialog for SEND_SMS.
     * The result is delivered to onRequestPermissionsResult().
     */
    private void requestSmsPermission() {
        // Check whether the permission is already granted (e.g., re-install scenario)
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.SEND_SMS)
                == PackageManager.PERMISSION_GRANTED) {
            // Already granted — mark as asked and proceed
            markSmsAskedAndProceed(true);
        } else {
            // Request the runtime permission from the OS
            ActivityCompat.requestPermissions(
                this,
                new String[]{Manifest.permission.SEND_SMS},
                SMS_PERMISSION_REQUEST_CODE
            );
        }
    }

    /**
     * Skips SMS permission and navigates directly to the inventory dashboard.
     * The app will function fully; it just will not send SMS alerts.
     */
    private void proceedWithoutSms() {
        markSmsAskedAndProceed(false);
        Toast.makeText(this, "SMS notifications disabled. You can still use all inventory features.",
                       Toast.LENGTH_LONG).show();
    }

    /**
     * Called by the Android system after the user responds to the permission dialog.
     *
     * @param requestCode  Identifies which permission request this is
     * @param permissions  The permissions that were requested
     * @param grantResults Whether each permission was granted or denied
     */
    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == SMS_PERMISSION_REQUEST_CODE) {
            boolean granted = grantResults.length > 0
                              && grantResults[0] == PackageManager.PERMISSION_GRANTED;

            if (granted) {
                Toast.makeText(this, "SMS notifications enabled. You'll be alerted when stock hits zero.",
                               Toast.LENGTH_LONG).show();
            } else {
                Toast.makeText(this, "SMS notifications denied. App will still work normally.",
                               Toast.LENGTH_LONG).show();
            }

            // Whether granted or denied, record that we asked and move on
            markSmsAskedAndProceed(granted);
        }
    }

    /**
     * Saves the SMS-asked flag to SharedPreferences and navigates to the Dashboard.
     * The Dashboard checks the actual runtime permission before sending any SMS,
     * so we only need to record that this screen was shown to avoid re-showing it.
     *
     * @param granted Whether permission was granted (logged but not stored separately
     *                because the Dashboard checks the live permission at runtime)
     */
    private void markSmsAskedAndProceed(boolean granted) {
        // Persist that we have already shown the SMS permission screen
        SharedPreferences.Editor editor =
            getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit();
        editor.putBoolean(PREFS_SMS_ASKED, true);
        editor.apply();

        // Navigate to the inventory dashboard, clearing this activity from the back stack
        Intent intent = new Intent(this, DashboardActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
    }
}
