package com.example.inventoryapp;

import android.Manifest;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.telephony.SmsManager;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

/**
 * DashboardActivity is the main screen of the app.
 *
 * It displays all inventory items in a scrollable RecyclerView "grid" and
 * allows the user to:
 *   - Add new items  (navigates to AddItemActivity)
 *   - Delete items   (via inline Delete button in each row)
 *   - Update qty     (via tap-to-edit on the Qty cell)
 *   - View all items (loaded fresh from the SQLite database on each resume)
 *
 * When an item's quantity reaches zero, the app attempts to send an SMS alert
 * if the SEND_SMS permission was granted. If permission was denied, the rest of
 * the app continues working without sending an SMS.
 */
public class DashboardActivity extends AppCompatActivity
        implements InventoryAdapter.OnItemActionListener {

    // Database helper handles all SQLite operations
    private DatabaseHelper    dbHelper;

    // Adapter and backing list for the RecyclerView
    private InventoryAdapter  adapter;
    private List<InventoryItem> itemList;

    // Unfiltered copy of the inventory as loaded from SQLite. itemList (above)
    // is the filtered/sorted view actually shown in the RecyclerView; keeping
    // both means a new search keyword never has to re-hit the database.
    private List<InventoryItem> fullItemList = new ArrayList<>();

    // Current sort mode applied to the displayed list
    private InventorySorter.SortMode currentSortMode = InventorySorter.SortMode.NAME_ASC;

    // Current search keyword typed into the search box
    private String currentSearchKeyword = "";

    // UI references
    private RecyclerView recyclerViewInventory;
    private TextView     textViewEmpty;
    private EditText     editTextSearch;
    private Spinner      spinnerSort;
    private Button       buttonLowStockReport;
    private Button       buttonViewHistory;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_dashboard);

        // Initialize the database connection
        dbHelper = new DatabaseHelper(this);

        // Bind UI elements
        recyclerViewInventory = findViewById(R.id.recyclerViewInventory);
        textViewEmpty         = findViewById(R.id.textViewEmpty);
        Button buttonAddItem  = findViewById(R.id.buttonAddItem);
        editTextSearch        = findViewById(R.id.editTextSearch);
        spinnerSort           = findViewById(R.id.spinnerSort);
        buttonLowStockReport  = findViewById(R.id.buttonLowStockReport);
        buttonViewHistory     = findViewById(R.id.buttonViewHistory);

        // Navigate to AddItemActivity when the user taps "+ Add Item"
        buttonAddItem.setOnClickListener(v -> {
            Intent intent = new Intent(DashboardActivity.this, AddItemActivity.class);
            startActivity(intent);
        });

        // Set up the RecyclerView with a LinearLayoutManager (vertical list)
        recyclerViewInventory.setLayoutManager(new LinearLayoutManager(this));

        setUpSortSpinner();
        setUpSearchBox();
        setUpLowStockReportButton();
        setUpViewHistoryButton();

        // Load and display the inventory
        loadInventory();
    }

    /**
     * Populates the sort Spinner with each InventorySorter.SortMode and
     * re-applies the display list whenever the user picks a new mode.
     */
    private void setUpSortSpinner() {
        String[] sortLabels = {
            "Name (A-Z)", "Category (A-Z)", "Quantity (Low-High)", "Quantity (High-Low)"
        };
        ArrayAdapter<String> spinnerAdapter = new ArrayAdapter<>(
            this, android.R.layout.simple_spinner_dropdown_item, sortLabels);
        spinnerSort.setAdapter(spinnerAdapter);

        spinnerSort.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                InventorySorter.SortMode[] modes = InventorySorter.SortMode.values();
                currentSortMode = modes[position];
                applyFilterAndSort();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                // No-op: a sort mode is always selected by default
            }
        });
    }

    /**
     * Filters the displayed list as the user types, using an O(n) substring
     * scan over the in-memory list (InventorySearchUtils.filterByKeyword) so
     * every keystroke stays fast without re-querying SQLite.
     */
    private void setUpSearchBox() {
        editTextSearch.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) { }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                currentSearchKeyword = s.toString();
                applyFilterAndSort();
            }

            @Override
            public void afterTextChanged(Editable s) { }
        });
    }

    /**
     * Wires the "Low Stock Report" button to a heap-based lookup
     * (InventoryGrouper.getLowestStockItems) that finds the five lowest
     * quantity items without sorting the whole inventory first.
     */
    private void setUpLowStockReportButton() {
        buttonLowStockReport.setOnClickListener(v -> showLowStockReport());
    }

    /**
     * Re-derives the displayed itemList from fullItemList by first applying
     * the current search keyword, then the current sort mode, and finally
     * refreshing the adapter. Called after every load, search change, or
     * sort change so the two features compose correctly together.
     */
    private void applyFilterAndSort() {
        itemList = InventorySearchUtils.filterByKeyword(fullItemList, currentSearchKeyword);
        InventorySorter.sort(itemList, currentSortMode);

        if (adapter == null) {
            adapter = new InventoryAdapter(this, itemList, this);
            recyclerViewInventory.setAdapter(adapter);
        } else {
            adapter.updateItems(itemList);
        }

        toggleEmptyState();
    }

    /**
     * Wires the "View History" button to a query against the new
     * inventory_history audit-log table (Milestone Four), showing the most
     * recent add/update/delete actions for the logged-in user's inventory.
     */
    private void setUpViewHistoryButton() {
        buttonViewHistory.setOnClickListener(v -> showHistoryReport());
    }

    /**
     * Builds and displays a dialog listing the 20 most recent inventory
     * changes (add, update, delete) for the logged-in user, newest first.
     * This reads directly from the inventory_history table rather than the
     * in-memory fullItemList, since history must still be visible for items
     * that have since been deleted.
     */
    private void showHistoryReport() {
        List<HistoryEntry> history =
            dbHelper.getRecentHistory(SessionManager.getCurrentUserId(), 20);

        if (history.isEmpty()) {
            Toast.makeText(this, "No activity recorded yet.", Toast.LENGTH_SHORT).show();
            return;
        }

        StringBuilder message = new StringBuilder();
        for (HistoryEntry entry : history) {
            message.append(entry.toDisplayString()).append("\n");
        }

        new AlertDialog.Builder(this)
            .setTitle("Recent Activity")
            .setMessage(message.toString().trim())
            .setPositiveButton("Close", (dialog, which) -> dialog.dismiss())
            .show();
    }

    /**
     * Builds and displays a dialog listing the five items with the lowest
     * quantity across the full (unfiltered) inventory.
     */
    private void showLowStockReport() {
        List<InventoryItem> lowStockItems = InventoryGrouper.getLowestStockItems(fullItemList, 5);

        if (lowStockItems.isEmpty()) {
            Toast.makeText(this, "No inventory items to report on.", Toast.LENGTH_SHORT).show();
            return;
        }

        StringBuilder message = new StringBuilder();
        for (InventoryItem item : lowStockItems) {
            message.append(item.getName())
                   .append(" (")
                   .append(item.getCategory())
                   .append("): ")
                   .append(item.getQuantity())
                   .append(" in stock\n");
        }

        new AlertDialog.Builder(this)
            .setTitle("Low Stock Report - Top 5")
            .setMessage(message.toString().trim())
            .setPositiveButton("Close", (dialog, which) -> dialog.dismiss())
            .show();
    }

    /** Shows or hides the empty-state message based on the current display list. */
    private void toggleEmptyState() {
        if (itemList.isEmpty()) {
            textViewEmpty.setVisibility(View.VISIBLE);
            recyclerViewInventory.setVisibility(View.GONE);
        } else {
            textViewEmpty.setVisibility(View.GONE);
            recyclerViewInventory.setVisibility(View.VISIBLE);
        }
    }

    /**
     * Refresh the inventory list every time this screen comes back into focus.
     * This ensures data added or changed elsewhere is always reflected here.
     */
    @Override
    protected void onResume() {
        super.onResume();
        loadInventory();
    }

    /**
     * Fetches all inventory items from the database and attaches them to the adapter.
     * Shows an empty-state message if the inventory has no items yet.
     */
    private void loadInventory() {
        // Pull the latest data from SQLite for the logged-in user only
        // (already ordered by name ASC, which is what
        // InventorySearchUtils.findByNameSorted relies on).
        fullItemList = dbHelper.getAllItems(SessionManager.getCurrentUserId());

        // Re-derive the displayed list so any active search keyword or
        // sort mode still applies after a reload.
        applyFilterAndSort();
    }

    // =========================================================================
    // InventoryAdapter.OnItemActionListener callbacks
    // =========================================================================

    /**
     * Called by the adapter when the user confirms deletion of an item.
     * Removes the item from the database and refreshes the list.
     *
     * @param item The inventory item to delete
     */
    @Override
    public void onDelete(InventoryItem item) {
        int rowsDeleted = dbHelper.deleteItem(item.getId(), SessionManager.getCurrentUserId());

        if (rowsDeleted > 0) {
            Toast.makeText(this, "\"" + item.getName() + "\" deleted.", Toast.LENGTH_SHORT).show();
            loadInventory();  // Refresh the grid to reflect the deletion
        } else {
            Toast.makeText(this, "Error deleting item.", Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * Called by the adapter when the user saves a new quantity for an item.
     * Updates the database and, if the new quantity is zero, sends an SMS alert
     * (only if the SEND_SMS permission was previously granted).
     *
     * @param item        The inventory item being updated
     * @param newQuantity The new quantity value entered by the user
     */
    @Override
    public void onQuantityUpdated(InventoryItem item, int newQuantity) {
        int rowsUpdated = dbHelper.updateItemQuantity(
            item.getId(), newQuantity, SessionManager.getCurrentUserId());

        if (rowsUpdated > 0) {
            Toast.makeText(this,
                "Quantity for \"" + item.getName() + "\" updated to " + newQuantity + ".",
                Toast.LENGTH_SHORT).show();

            // Trigger a low-stock SMS alert if the item hit zero
            if (newQuantity == 0) {
                sendLowStockAlert(item.getName());
            }

            loadInventory();  // Refresh grid so the new value is displayed
        } else {
            Toast.makeText(this, "Error updating quantity.", Toast.LENGTH_SHORT).show();
        }
    }

    // =========================================================================
    // SMS Alert Logic
    // =========================================================================

    /**
     * Sends an SMS alert to the device's own number when an item reaches zero stock.
     *
     * Permission check: if the user denied SEND_SMS, this method does nothing
     * and the app continues functioning normally without any crash or error.
     *
     * @param itemName Name of the inventory item that reached zero
     */
    private void sendLowStockAlert(String itemName) {
        // Check whether the user granted SMS permission at runtime
        boolean smsGranted = ContextCompat.checkSelfPermission(
            this, Manifest.permission.SEND_SMS) == PackageManager.PERMISSION_GRANTED;

        if (!smsGranted) {
            // Permission was denied — silently skip sending SMS, app keeps running
            return;
        }

        try {
            // Use the device's loopback number so the alert goes to the user themselves
            String phoneNumber  = "5554"; // Emulator loopback; real device uses own number
            String alertMessage = "⚠️ Low Stock Alert: \"" + itemName
                                  + "\" is out of stock (quantity = 0). Please reorder!";

            SmsManager smsManager = SmsManager.getDefault();
            smsManager.sendTextMessage(phoneNumber, null, alertMessage, null, null);

            Toast.makeText(this,
                "Low-stock SMS alert sent for \"" + itemName + "\".",
                Toast.LENGTH_SHORT).show();

        } catch (Exception e) {
            // Log the error but don't crash the app — SMS is a non-critical feature
            Toast.makeText(this, "Could not send SMS alert: " + e.getMessage(),
                           Toast.LENGTH_SHORT).show();
        }
    }
}
