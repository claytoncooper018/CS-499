package com.example.inventoryapp;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

/**
 * MainActivity is the Login screen, the first screen the user sees.
 *
 * Functionality:
 *   - Allows existing users to log in with their stored credentials.
 *   - Allows new users to create an account, which is saved to the SQLite database.
 *   - After a successful login, navigates to SmsPermissionActivity (first time)
 *     or directly to DashboardActivity.
 */
public class MainActivity extends AppCompatActivity {

    // SharedPreferences key used to track whether SMS permission was already asked
    private static final String PREFS_NAME     = "InventoryAppPrefs";
    private static final String PREFS_SMS_ASKED = "smsPermissionAsked";

    // Database helper for user authentication queries
    private DatabaseHelper dbHelper;

    // UI references
    private EditText editTextUsername;
    private EditText editTextPassword;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Initialize the database helper (creates DB file if it doesn't exist)
        dbHelper = new DatabaseHelper(this);

        // Bind UI elements
        editTextUsername = findViewById(R.id.editTextUsername);
        editTextPassword = findViewById(R.id.editTextPassword);
        Button buttonLogin         = findViewById(R.id.buttonLogin);
        Button buttonCreateAccount = findViewById(R.id.buttonCreateAccount);

        // Set up Login button click handler
        buttonLogin.setOnClickListener(v -> handleLogin());

        // Set up Create Account button click handler
        buttonCreateAccount.setOnClickListener(v -> handleCreateAccount());
    }

    /**
     * Handles the login flow:
     *   1. Validates that fields are not empty.
     *   2. Checks credentials against the database.
     *   3. On success, navigates to the next appropriate screen.
     */
    private void handleLogin() {
        String username = editTextUsername.getText().toString().trim();
        String password = editTextPassword.getText().toString().trim();

        // Validate that neither field is empty before querying the database
        if (TextUtils.isEmpty(username) || TextUtils.isEmpty(password)) {
            Toast.makeText(this, "Please enter both username and password.", Toast.LENGTH_SHORT).show();
            return;
        }

        // Verify credentials against the users table
        long userId = dbHelper.validateUser(username, password);
        if (userId != -1) {
            // Record who is logged in so every DatabaseHelper call for the
            // rest of the session can be scoped to this user's own data.
            SessionManager.setCurrentUser(userId, username);
            Toast.makeText(this, "Welcome back, " + username + "!", Toast.LENGTH_SHORT).show();
            navigateAfterLogin();
        } else {
            Toast.makeText(this, "Invalid username or password. Try again.", Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * Handles the account creation flow:
     *   1. Validates that both fields are filled in.
     *   2. Attempts to insert the new user into the database.
     *   3. On success, automatically logs the user in.
     */
    private void handleCreateAccount() {
        String username = editTextUsername.getText().toString().trim();
        String password = editTextPassword.getText().toString().trim();

        // Both fields are required to create an account
        if (TextUtils.isEmpty(username) || TextUtils.isEmpty(password)) {
            Toast.makeText(this, "Please enter a username and password to create an account.",
                           Toast.LENGTH_SHORT).show();
            return;
        }

        // Attempt to save the new user to the database
        long newUserId = dbHelper.createUser(username, password);

        if (newUserId != -1) {
            // Record who is logged in so every DatabaseHelper call for the
            // rest of the session can be scoped to this user's own data.
            SessionManager.setCurrentUser(newUserId, username);
            Toast.makeText(this, "Account created! Welcome, " + username + "!",
                           Toast.LENGTH_SHORT).show();
            navigateAfterLogin();
        } else {
            // createUser returns false when the username is already taken
            Toast.makeText(this, "Username already exists. Please choose a different one.",
                           Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * Routes the user to either the SMS permission screen (first launch)
     * or directly to the Dashboard (subsequent logins).
     * Clears the back stack so pressing Back from the Dashboard does not return here.
     */
    private void navigateAfterLogin() {
        // Check whether we have already prompted for SMS permission
        boolean smsAsked = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                           .getBoolean(PREFS_SMS_ASKED, false);

        Intent intent;
        if (!smsAsked) {
            // First login: ask for SMS permission before showing the dashboard
            intent = new Intent(this, SmsPermissionActivity.class);
        } else {
            // Returning user: go straight to the inventory dashboard
            intent = new Intent(this, DashboardActivity.class);
        }

        // Remove MainActivity from the back stack so Back exits the app
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
    }
}
