package com.example.inventoryapp;

import android.os.Bundle;
import android.text.TextUtils;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import java.util.List;

/**
 * AddItemActivity provides a form for the user to create a new inventory item.
 *
 * Fields:
 *   - Item Name  (required, text)
 *   - Category   (required, text)
 *   - Quantity   (required, numeric)
 *
 * On save, the item is inserted into the SQLite inventory table and the user
 * is returned to the DashboardActivity, which refreshes its list on resume.
 *
 * On cancel, no changes are made to the database.
 */
public class AddItemActivity extends AppCompatActivity {

    // Database helper for inserting new items
    private DatabaseHelper dbHelper;

    // Form input fields
    private EditText editTextItemName;
    private EditText editTextCategory;
    private EditText editTextQuantity;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_add_item);

        // Initialize database helper
        dbHelper = new DatabaseHelper(this);

        // Bind form fields to their view IDs
        editTextItemName = findViewById(R.id.editTextItemName);
        editTextCategory = findViewById(R.id.editTextCategory);
        editTextQuantity = findViewById(R.id.editTextQuantity);

        Button buttonSaveItem = findViewById(R.id.buttonSaveItem);
        Button buttonCancel   = findViewById(R.id.buttonCancel);

        // Save button: validate and insert into database
        buttonSaveItem.setOnClickListener(v -> handleSaveItem());

        // Cancel button: discard input and return to Dashboard
        buttonCancel.setOnClickListener(v -> {
            finish();  // Pops this activity off the back stack
        });
    }

    /**
     * Validates all form fields, builds an InventoryItem, and saves it to the database.
     * On success, finishes this activity so the Dashboard is shown (and refreshes).
     * On validation failure, shows a descriptive error Toast and stays on the form.
     */
    private void handleSaveItem() {
        // Read raw input from each field
        String name     = editTextItemName.getText().toString().trim();
        String category = editTextCategory.getText().toString().trim();
        String qtyText  = editTextQuantity.getText().toString().trim();

        // --- Validation ---

        if (TextUtils.isEmpty(name)) {
            Toast.makeText(this, "Please enter an item name.", Toast.LENGTH_SHORT).show();
            editTextItemName.requestFocus();
            return;
        }

        if (TextUtils.isEmpty(category)) {
            Toast.makeText(this, "Please enter a category.", Toast.LENGTH_SHORT).show();
            editTextCategory.requestFocus();
            return;
        }

        if (TextUtils.isEmpty(qtyText)) {
            Toast.makeText(this, "Please enter a quantity.", Toast.LENGTH_SHORT).show();
            editTextQuantity.requestFocus();
            return;
        }

        // Parse quantity safely
        int quantity;
        try {
            quantity = Integer.parseInt(qtyText);
        } catch (NumberFormatException e) {
            Toast.makeText(this, "Quantity must be a whole number.", Toast.LENGTH_SHORT).show();
            editTextQuantity.requestFocus();
            return;
        }

        if (quantity < 0) {
            Toast.makeText(this, "Quantity cannot be negative.", Toast.LENGTH_SHORT).show();
            editTextQuantity.requestFocus();
            return;
        }

        // --- Duplicate check ---
        // getAllItems() returns this user's items sorted ascending by name
        // (see the ORDER BY clause in DatabaseHelper), so a binary search --
        // O(log n) -- can confirm uniqueness without a full O(n) linear scan.
        // Scoped to the current user: two different accounts are allowed to
        // each have their own item named, say, "Hammer".
        long currentUserId = SessionManager.getCurrentUserId();
        List<InventoryItem> existingItems = dbHelper.getAllItems(currentUserId);
        InventoryItem duplicate = InventorySearchUtils.findByNameSorted(existingItems, name);
        if (duplicate != null) {
            Toast.makeText(this,
                "An item named \"" + name + "\" already exists. Try a different name.",
                Toast.LENGTH_LONG).show();
            editTextItemName.requestFocus();
            return;
        }

        // --- Insert into database ---

        InventoryItem newItem = new InventoryItem(name, category, quantity);
        long rowId = dbHelper.addItem(newItem, currentUserId);

        if (rowId != -1) {
            // Successful insert
            Toast.makeText(this, "\"" + name + "\" added to inventory.", Toast.LENGTH_SHORT).show();
            finish();  // Return to Dashboard; onResume() will reload the list
        } else {
            // This should rarely happen — only if the disk is full or DB is corrupted
            Toast.makeText(this, "Error saving item. Please try again.", Toast.LENGTH_SHORT).show();
        }
    }
}
