package com.example.inventoryapp;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * HistoryEntry is a read-only data model representing one row of the
 * inventory_history audit log: what happened, to which item, and when.
 *
 * previousQuantity / newQuantity are nullable because they don't both apply
 * to every action: an ADD has no previous quantity, and a DELETE has no new
 * quantity.
 */
public class HistoryEntry {

    private final String itemName;
    private final String action;
    private final Integer previousQuantity;
    private final Integer newQuantity;
    private final long timestamp;

    public HistoryEntry(String itemName, String action, Integer previousQuantity,
                         Integer newQuantity, long timestamp) {
        this.itemName = itemName;
        this.action = action;
        this.previousQuantity = previousQuantity;
        this.newQuantity = newQuantity;
        this.timestamp = timestamp;
    }

    public String getItemName() {
        return itemName;
    }

    public String getAction() {
        return action;
    }

    public Integer getPreviousQuantity() {
        return previousQuantity;
    }

    public Integer getNewQuantity() {
        return newQuantity;
    }

    public long getTimestamp() {
        return timestamp;
    }

    /**
     * Builds a single human-readable summary line for this entry, e.g.:
     *   "Jul 31, 3:12 PM - ADD \"Hammer\" (qty 12)"
     *   "Jul 31, 3:14 PM - UPDATE \"Hammer\" (12 -> 8)"
     *   "Jul 31, 3:15 PM - DELETE \"Wrench\" (was 3)"
     */
    public String toDisplayString() {
        SimpleDateFormat formatter = new SimpleDateFormat("MMM d, h:mm a", Locale.US);
        String when = formatter.format(new Date(timestamp));

        switch (action) {
            case DatabaseHelper.ACTION_ADD:
                return when + " - ADD \"" + itemName + "\" (qty " + newQuantity + ")";
            case DatabaseHelper.ACTION_UPDATE:
                return when + " - UPDATE \"" + itemName + "\" (" +
                       previousQuantity + " -> " + newQuantity + ")";
            case DatabaseHelper.ACTION_DELETE:
                return when + " - DELETE \"" + itemName + "\" (was " + previousQuantity + ")";
            default:
                return when + " - " + action + " \"" + itemName + "\"";
        }
    }
}
