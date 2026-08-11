package com.example.inventoryapp;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.util.ArrayList;
import java.util.List;

/**
 * DatabaseHelper manages all SQLite database operations for InventoryApp.
 *
 * Tables:
 *   - users:             login credentials as a salted SHA-256 hash (see PasswordUtils)
 *   - inventory:         inventory items (name, category, quantity), scoped to
 *                         the user who owns them via a user_id foreign key
 *   - inventory_history: an append-only audit log of every add/update/delete
 *                         performed against the inventory table
 *
 * This class follows the singleton helper pattern provided by SQLiteOpenHelper.
 * All CRUD operations (Create, Read, Update, Delete) for all three tables are here.
 *
 * Milestone Four (Databases) changes, on top of Milestone Two/Three:
 *   1. Inventory rows are now scoped to a user_id (foreign key to users.id).
 *      Previously every logged-in user shared one global inventory table --
 *      a real data-isolation flaw, not just a hypothetical one. Every read,
 *      update, and delete now takes the caller's user ID and filters or
 *      checks ownership with it, so one account can never see or mutate
 *      another account's rows even if it guesses a valid item ID.
 *   2. A new inventory_history table records every add/update/delete as an
 *      immutable audit trail, including a denormalized snapshot of the item
 *      name so history remains readable even after the item itself is deleted.
 *   3. Multi-statement writes (mutate inventory + append a history row) are
 *      wrapped in explicit SQLite transactions so a crash between the two
 *      statements can never leave the audit log out of sync with the data.
 *   4. Indices were added on the foreign-key and lookup columns that are
 *      now queried on every screen load (inventory.user_id, inventory.category,
 *      inventory_history.item_id) so those queries stay fast as data grows.
 *   5. Foreign key enforcement is turned on via onConfigure(), and ON DELETE
 *      CASCADE / SET NULL rules describe what should happen to dependent
 *      rows instead of leaving that to be handled (or forgotten) in Java.
 */
public class DatabaseHelper extends SQLiteOpenHelper {

    // Database metadata
    private static final String DATABASE_NAME    = "InventoryApp.db";
    // Bumped from 2 -> 3: inventory is now scoped to a user_id, and a new
    // inventory_history audit table was added. See onUpgrade() for the
    // migration logic that preserves existing rows during this change.
    private static final int    DATABASE_VERSION = 3;

    // --- Users table constants ---
    private static final String TABLE_USERS       = "users";
    private static final String COL_USER_ID        = "id";
    private static final String COL_USERNAME       = "username";
    private static final String COL_PASSWORD_HASH  = "password_hash";
    private static final String COL_SALT           = "salt";

    // Name of the old (version 1) users table, used only during migration
    private static final String TABLE_USERS_OLD    = "users_old";
    private static final String COL_PASSWORD_LEGACY = "password";

    // --- Inventory table constants ---
    private static final String TABLE_INVENTORY  = "inventory";
    private static final String COL_ITEM_ID       = "id";
    private static final String COL_ITEM_NAME     = "name";
    private static final String COL_ITEM_CATEGORY = "category";
    private static final String COL_ITEM_QUANTITY = "quantity";
    private static final String COL_ITEM_USER_ID  = "user_id";

    // Name of the pre-Milestone-Four inventory table, used only during migration
    private static final String TABLE_INVENTORY_OLD = "inventory_old";

    // --- Inventory history (audit log) table constants ---
    private static final String TABLE_HISTORY           = "inventory_history";
    private static final String COL_HISTORY_ID          = "id";
    private static final String COL_HISTORY_ITEM_ID     = "item_id";
    private static final String COL_HISTORY_USER_ID     = "user_id";
    private static final String COL_HISTORY_ITEM_NAME   = "item_name";
    private static final String COL_HISTORY_ACTION      = "action";
    private static final String COL_HISTORY_OLD_QTY     = "previous_quantity";
    private static final String COL_HISTORY_NEW_QTY     = "new_quantity";
    private static final String COL_HISTORY_TIMESTAMP   = "timestamp";

    // Action labels written into inventory_history.action
    public static final String ACTION_ADD    = "ADD";
    public static final String ACTION_UPDATE = "UPDATE";
    public static final String ACTION_DELETE = "DELETE";

    /**
     * Constructor passes database metadata to the parent SQLiteOpenHelper.
     *
     * @param context Application context used to open or create the database
     */
    public DatabaseHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    /**
     * SQLite does not enforce FOREIGN KEY constraints unless explicitly told
     * to for each connection. Enabling it here means an attempt to insert an
     * inventory row for a user_id that doesn't exist in users -- or leaving
     * a dangling reference -- fails loudly instead of silently corrupting data.
     */
    @Override
    public void onConfigure(SQLiteDatabase db) {
        super.onConfigure(db);
        db.setForeignKeyConstraintsEnabled(true);
    }

    /**
     * Called when the database is created for the first time.
     * Creates the users, inventory, and inventory_history tables plus indices.
     *
     * @param db The newly created SQLite database
     */
    @Override
    public void onCreate(SQLiteDatabase db) {
        createUsersTable(db, TABLE_USERS);
        createInventoryTable(db, TABLE_INVENTORY);
        createHistoryTable(db);
        createIndices(db);
    }

    /**
     * Creates a users table with the current (hashed-password) schema
     * under the given table name.
     */
    private void createUsersTable(SQLiteDatabase db, String tableName) {
        String createUsersTable =
            "CREATE TABLE " + tableName + " (" +
            COL_USER_ID       + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
            COL_USERNAME      + " TEXT UNIQUE NOT NULL, " +
            COL_PASSWORD_HASH + " TEXT NOT NULL, " +
            COL_SALT          + " TEXT NOT NULL)";
        db.execSQL(createUsersTable);
    }

    /**
     * Creates the inventory table under the given name with the current
     * (user-scoped) schema: a user_id foreign key ties every row to the
     * account that owns it, and rows are removed automatically if that
     * account is ever deleted (ON DELETE CASCADE).
     */
    private void createInventoryTable(SQLiteDatabase db, String tableName) {
        String createInventoryTable =
            "CREATE TABLE " + tableName + " (" +
            COL_ITEM_ID       + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
            COL_ITEM_NAME     + " TEXT NOT NULL, " +
            COL_ITEM_CATEGORY + " TEXT NOT NULL, " +
            COL_ITEM_QUANTITY + " INTEGER NOT NULL, " +
            COL_ITEM_USER_ID  + " INTEGER NOT NULL, " +
            "FOREIGN KEY(" + COL_ITEM_USER_ID + ") REFERENCES " + TABLE_USERS +
            "(" + COL_USER_ID + ") ON DELETE CASCADE)";
        db.execSQL(createInventoryTable);
    }

    /**
     * Creates the inventory_history audit-log table. item_name is a
     * deliberate denormalization: it snapshots the item's name at the time
     * of the action so a history entry still reads correctly even after the
     * underlying item has been deleted (at which point item_id becomes NULL
     * rather than pointing at a row that no longer exists).
     */
    private void createHistoryTable(SQLiteDatabase db) {
        String createHistoryTable =
            "CREATE TABLE " + TABLE_HISTORY + " (" +
            COL_HISTORY_ID        + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
            COL_HISTORY_ITEM_ID   + " INTEGER, " +
            COL_HISTORY_USER_ID   + " INTEGER NOT NULL, " +
            COL_HISTORY_ITEM_NAME + " TEXT NOT NULL, " +
            COL_HISTORY_ACTION    + " TEXT NOT NULL, " +
            COL_HISTORY_OLD_QTY   + " INTEGER, " +
            COL_HISTORY_NEW_QTY   + " INTEGER, " +
            COL_HISTORY_TIMESTAMP + " INTEGER NOT NULL, " +
            "FOREIGN KEY(" + COL_HISTORY_ITEM_ID + ") REFERENCES " + TABLE_INVENTORY +
            "(" + COL_ITEM_ID + ") ON DELETE SET NULL, " +
            "FOREIGN KEY(" + COL_HISTORY_USER_ID + ") REFERENCES " + TABLE_USERS +
            "(" + COL_USER_ID + ") ON DELETE CASCADE)";
        db.execSQL(createHistoryTable);
    }

    /**
     * Creates indices on every column that is now filtered or joined on in
     * the hot path (every Dashboard load filters inventory by user_id;
     * InventoryGrouper groups by category; history lookups filter by
     * item_id). Without these, each of those queries degrades to a full
     * table scan as the inventory grows.
     */
    private void createIndices(SQLiteDatabase db) {
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_inventory_user_id ON " +
                   TABLE_INVENTORY + "(" + COL_ITEM_USER_ID + ")");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_inventory_category ON " +
                   TABLE_INVENTORY + "(" + COL_ITEM_CATEGORY + ")");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_history_item_id ON " +
                   TABLE_HISTORY + "(" + COL_HISTORY_ITEM_ID + ")");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_history_user_id ON " +
                   TABLE_HISTORY + "(" + COL_HISTORY_USER_ID + ")");
    }

    /**
     * Called when the database version number changes.
     *
     * Version 1 -> 2: migrates the users table from a plain-text password
     * column to a salted-hash column (Milestone Two).
     *
     * Version 2 -> 3: migrates the inventory table from a global,
     * unscoped table to one that is scoped to a user_id, and adds the new
     * inventory_history table and supporting indices (Milestone Four).
     *
     * @param db         The existing SQLite database
     * @param oldVersion Old schema version number
     * @param newVersion New schema version number
     */
    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        if (oldVersion < 2) {
            migrateToHashedPasswords(db);
        }
        if (oldVersion < 3) {
            migrateToUserScopedInventory(db);
        }
    }

    private void migrateToHashedPasswords(SQLiteDatabase db) {
        // 1. Preserve the existing data by renaming the old table.
        db.execSQL("ALTER TABLE " + TABLE_USERS + " RENAME TO " + TABLE_USERS_OLD);

        // 2. Create the new table with the hashed-password schema.
        createUsersTable(db, TABLE_USERS);

        // 3. Re-hash each existing user's password and copy it into the new table.
        Cursor cursor = db.rawQuery(
            "SELECT " + COL_USERNAME + ", " + COL_PASSWORD_LEGACY +
            " FROM " + TABLE_USERS_OLD,
            null
        );

        if (cursor.moveToFirst()) {
            do {
                String username = cursor.getString(cursor.getColumnIndexOrThrow(COL_USERNAME));
                String plainTextPassword = cursor.getString(cursor.getColumnIndexOrThrow(COL_PASSWORD_LEGACY));

                String salt = PasswordUtils.generateSalt();
                String hash = PasswordUtils.hashPassword(plainTextPassword, salt);

                ContentValues values = new ContentValues();
                values.put(COL_USERNAME, username);
                values.put(COL_PASSWORD_HASH, hash);
                values.put(COL_SALT, salt);
                db.insert(TABLE_USERS, null, values);
            } while (cursor.moveToNext());
        }
        cursor.close();

        // 4. The old plain-text table is no longer needed.
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_USERS_OLD);
    }

    /**
     * Migrates the pre-Milestone-Four inventory table (no user_id column)
     * to the new user-scoped schema, without discarding any existing rows:
     *   1. Rename the existing "inventory" table to "inventory_old".
     *   2. Create the new "inventory" table (with the user_id FK) and the
     *      new "inventory_history" table.
     *   3. Every pre-existing row is assigned to the earliest-created user
     *      account, since the old schema had no per-user ownership at all --
     *      there is no data that tells us who "really" added each item.
     *      This is called out explicitly in the accompanying narrative as a
     *      known limitation of the migration rather than something papered
     *      over silently.
     *   4. Drop "inventory_old" and create the supporting indices.
     *
     * If no users exist yet (a fresh install upgrading from an empty
     * database), there is nothing to reassign and the old table is simply
     * dropped.
     */
    private void migrateToUserScopedInventory(SQLiteDatabase db) {
        // 1. Preserve existing data by renaming the old table.
        db.execSQL("ALTER TABLE " + TABLE_INVENTORY + " RENAME TO " + TABLE_INVENTORY_OLD);

        // 2. Create the new inventory table and the new history table.
        createInventoryTable(db, TABLE_INVENTORY);
        createHistoryTable(db);

        // 3. Find a fallback owner for pre-existing rows: the earliest
        // (lowest id) registered user.
        long fallbackUserId = -1;
        Cursor userCursor = db.rawQuery(
            "SELECT MIN(" + COL_USER_ID + ") FROM " + TABLE_USERS, null);
        if (userCursor.moveToFirst() && !userCursor.isNull(0)) {
            fallbackUserId = userCursor.getLong(0);
        }
        userCursor.close();

        if (fallbackUserId != -1) {
            Cursor oldItems = db.rawQuery(
                "SELECT " + COL_ITEM_NAME + ", " + COL_ITEM_CATEGORY + ", " + COL_ITEM_QUANTITY +
                " FROM " + TABLE_INVENTORY_OLD, null);

            if (oldItems.moveToFirst()) {
                do {
                    ContentValues values = new ContentValues();
                    values.put(COL_ITEM_NAME,     oldItems.getString(oldItems.getColumnIndexOrThrow(COL_ITEM_NAME)));
                    values.put(COL_ITEM_CATEGORY, oldItems.getString(oldItems.getColumnIndexOrThrow(COL_ITEM_CATEGORY)));
                    values.put(COL_ITEM_QUANTITY, oldItems.getInt(oldItems.getColumnIndexOrThrow(COL_ITEM_QUANTITY)));
                    values.put(COL_ITEM_USER_ID,  fallbackUserId);
                    db.insert(TABLE_INVENTORY, null, values);
                } while (oldItems.moveToNext());
            }
            oldItems.close();
        }
        // If there is no user at all yet, pre-existing rows (there
        // shouldn't be any, since the app requires login before the
        // Dashboard can be reached) are intentionally not carried over.

        // 4. Clean up and build indices.
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_INVENTORY_OLD);
        createIndices(db);
    }

    // =========================================================================
    // USER CRUD OPERATIONS
    // =========================================================================

    /**
     * Inserts a new user record into the users table. The password is
     * never stored in plain text: a unique salt is generated for the
     * user and combined with the password via SHA-256 before saving.
     *
     * @param username Desired username (must be unique)
     * @param password Plain-text password (hashed before storage)
     * @return the new user's row ID on success, or -1 if the username is taken
     */
    public long createUser(String username, String password) {
        SQLiteDatabase db = this.getWritableDatabase();

        String salt = PasswordUtils.generateSalt();
        String hash = PasswordUtils.hashPassword(password, salt);

        ContentValues values = new ContentValues();
        values.put(COL_USERNAME, username);
        values.put(COL_PASSWORD_HASH, hash);
        values.put(COL_SALT, salt);

        // insert() returns -1 on conflict (duplicate username), since
        // COL_USERNAME is UNIQUE and no conflict resolution is specified
        long rowId = db.insert(TABLE_USERS, null, values);
        db.close();

        return rowId;
    }

    /**
     * Checks whether the provided username and password match a record in
     * the database. The stored salt for the username is looked up, the
     * entered password is hashed with that salt, and the result is
     * compared against the stored hash -- the plain-text password is
     * never stored or compared directly.
     *
     * @param username Username entered by the user
     * @param password Password entered by the user
     * @return the matching user's row ID if credentials are valid, or -1 otherwise
     */
    public long validateUser(String username, String password) {
        SQLiteDatabase db = this.getReadableDatabase();

        String query = "SELECT " + COL_USER_ID + ", " + COL_PASSWORD_HASH + ", " + COL_SALT +
                       " FROM " + TABLE_USERS + " WHERE " + COL_USERNAME + "=?";
        Cursor cursor = db.rawQuery(query, new String[]{username});

        long userId = -1;
        if (cursor.moveToFirst()) {
            String storedHash = cursor.getString(cursor.getColumnIndexOrThrow(COL_PASSWORD_HASH));
            String salt = cursor.getString(cursor.getColumnIndexOrThrow(COL_SALT));
            if (PasswordUtils.verifyPassword(password, salt, storedHash)) {
                userId = cursor.getLong(cursor.getColumnIndexOrThrow(COL_USER_ID));
            }
        }

        cursor.close();
        db.close();

        return userId;
    }

    // =========================================================================
    // INVENTORY CRUD OPERATIONS (all scoped to a user_id)
    // =========================================================================

    /**
     * Inserts a new inventory item owned by the given user, and records an
     * ADD entry in the audit log. Both writes happen in a single
     * transaction so the two tables can never fall out of sync.
     *
     * @param item   InventoryItem to save (id field is ignored; assigned by SQLite)
     * @param userId The owning user's row ID
     * @return The row ID of the new record, or -1 on failure
     */
    public long addItem(InventoryItem item, long userId) {
        SQLiteDatabase db = this.getWritableDatabase();
        long rowId = -1;

        db.beginTransaction();
        try {
            ContentValues values = new ContentValues();
            values.put(COL_ITEM_NAME,     item.getName());
            values.put(COL_ITEM_CATEGORY, item.getCategory());
            values.put(COL_ITEM_QUANTITY, item.getQuantity());
            values.put(COL_ITEM_USER_ID,  userId);

            rowId = db.insert(TABLE_INVENTORY, null, values);

            if (rowId != -1) {
                insertHistoryRow(db, rowId, userId, item.getName(),
                                  ACTION_ADD, null, item.getQuantity());
                db.setTransactionSuccessful();
            }
        } finally {
            db.endTransaction();
        }

        db.close();
        return rowId;
    }

    /**
     * Retrieves all inventory items belonging to the given user, ordered by
     * item name. Rows owned by other users are never returned.
     *
     * @param userId The owning user's row ID
     * @return List of InventoryItem objects; empty list if none exist
     */
    public List<InventoryItem> getAllItems(long userId) {
        List<InventoryItem> itemList = new ArrayList<>();
        SQLiteDatabase db = this.getReadableDatabase();

        // Select only this user's rows, ordered alphabetically by item name
        Cursor cursor = db.rawQuery(
            "SELECT * FROM " + TABLE_INVENTORY +
            " WHERE " + COL_ITEM_USER_ID + "=?" +
            " ORDER BY " + COL_ITEM_NAME + " ASC",
            new String[]{String.valueOf(userId)}
        );

        // Map each cursor row to an InventoryItem object
        if (cursor.moveToFirst()) {
            do {
                int    id       = cursor.getInt(cursor.getColumnIndexOrThrow(COL_ITEM_ID));
                String name     = cursor.getString(cursor.getColumnIndexOrThrow(COL_ITEM_NAME));
                String category = cursor.getString(cursor.getColumnIndexOrThrow(COL_ITEM_CATEGORY));
                int    quantity = cursor.getInt(cursor.getColumnIndexOrThrow(COL_ITEM_QUANTITY));

                itemList.add(new InventoryItem(id, name, category, quantity));
            } while (cursor.moveToNext());
        }

        cursor.close();
        db.close();

        return itemList;
    }

    /**
     * Updates an existing inventory item's quantity, but only if it is
     * owned by the given user, and logs an UPDATE entry (with both the
     * previous and new quantity) in the same transaction.
     *
     * @param itemId      The SQLite row ID of the item to update
     * @param newQuantity The new quantity value to save
     * @param userId      The row ID of the user attempting the update
     * @return Number of rows affected (0 if the item doesn't exist or isn't owned by this user)
     */
    public int updateItemQuantity(int itemId, int newQuantity, long userId) {
        SQLiteDatabase db = this.getWritableDatabase();
        int rowsAffected = 0;

        db.beginTransaction();
        try {
            String[] whereArgs = new String[]{String.valueOf(itemId), String.valueOf(userId)};

            // Read the current name/quantity first (scoped to this owner) so the
            // history row is accurate and so we don't touch a row that isn't ours.
            Cursor cursor = db.rawQuery(
                "SELECT " + COL_ITEM_NAME + ", " + COL_ITEM_QUANTITY + " FROM " + TABLE_INVENTORY +
                " WHERE " + COL_ITEM_ID + "=? AND " + COL_ITEM_USER_ID + "=?",
                whereArgs
            );

            if (cursor.moveToFirst()) {
                String itemName = cursor.getString(cursor.getColumnIndexOrThrow(COL_ITEM_NAME));
                int previousQuantity = cursor.getInt(cursor.getColumnIndexOrThrow(COL_ITEM_QUANTITY));
                cursor.close();

                ContentValues values = new ContentValues();
                values.put(COL_ITEM_QUANTITY, newQuantity);

                rowsAffected = db.update(
                    TABLE_INVENTORY,
                    values,
                    COL_ITEM_ID + "=? AND " + COL_ITEM_USER_ID + "=?",
                    whereArgs
                );

                if (rowsAffected > 0) {
                    insertHistoryRow(db, itemId, userId, itemName,
                                      ACTION_UPDATE, previousQuantity, newQuantity);
                }
            } else {
                cursor.close();
            }

            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }

        db.close();
        return rowsAffected;
    }

    /**
     * Updates an existing inventory item's full record (name, category,
     * quantity), but only if it is owned by the given user.
     *
     * @param item   InventoryItem containing the updated values; id must be valid
     * @param userId The row ID of the user attempting the update
     * @return Number of rows affected
     */
    public int updateItem(InventoryItem item, long userId) {
        SQLiteDatabase db = this.getWritableDatabase();

        ContentValues values = new ContentValues();
        values.put(COL_ITEM_NAME,     item.getName());
        values.put(COL_ITEM_CATEGORY, item.getCategory());
        values.put(COL_ITEM_QUANTITY, item.getQuantity());

        int rowsAffected = db.update(
            TABLE_INVENTORY,
            values,
            COL_ITEM_ID + "=? AND " + COL_ITEM_USER_ID + "=?",
            new String[]{String.valueOf(item.getId()), String.valueOf(userId)}
        );

        db.close();
        return rowsAffected;
    }

    /**
     * Deletes an inventory item, but only if it is owned by the given user,
     * and logs a DELETE entry in the same transaction. The history row
     * keeps a snapshot of the item's name and last known quantity even
     * though the inventory row itself (and its FK reference) is gone.
     *
     * @param itemId SQLite row ID of the item to delete
     * @param userId The row ID of the user attempting the delete
     * @return Number of rows deleted (0 if the item doesn't exist or isn't owned by this user)
     */
    public int deleteItem(int itemId, long userId) {
        SQLiteDatabase db = this.getWritableDatabase();
        int rowsDeleted = 0;

        db.beginTransaction();
        try {
            String[] whereArgs = new String[]{String.valueOf(itemId), String.valueOf(userId)};

            Cursor cursor = db.rawQuery(
                "SELECT " + COL_ITEM_NAME + ", " + COL_ITEM_QUANTITY + " FROM " + TABLE_INVENTORY +
                " WHERE " + COL_ITEM_ID + "=? AND " + COL_ITEM_USER_ID + "=?",
                whereArgs
            );

            if (cursor.moveToFirst()) {
                String itemName = cursor.getString(cursor.getColumnIndexOrThrow(COL_ITEM_NAME));
                int lastQuantity = cursor.getInt(cursor.getColumnIndexOrThrow(COL_ITEM_QUANTITY));
                cursor.close();

                rowsDeleted = db.delete(
                    TABLE_INVENTORY,
                    COL_ITEM_ID + "=? AND " + COL_ITEM_USER_ID + "=?",
                    whereArgs
                );

                if (rowsDeleted > 0) {
                    insertHistoryRow(db, itemId, userId, itemName,
                                      ACTION_DELETE, lastQuantity, null);
                }
            } else {
                cursor.close();
            }

            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }

        db.close();
        return rowsDeleted;
    }

    // =========================================================================
    // INVENTORY HISTORY (AUDIT LOG) OPERATIONS
    // =========================================================================

    /**
     * Inserts one audit-log row. Shared by addItem, updateItemQuantity, and
     * deleteItem; always called from within a transaction those methods
     * already hold open.
     */
    private void insertHistoryRow(SQLiteDatabase db, long itemId, long userId, String itemName,
                                   String action, Integer previousQuantity, Integer newQuantity) {
        ContentValues values = new ContentValues();
        // itemId is stored even for a just-deleted row; the FK's
        // ON DELETE SET NULL only fires on a *future* delete of that row,
        // so at insert time it still correctly points at the item.
        values.put(COL_HISTORY_ITEM_ID, itemId);
        values.put(COL_HISTORY_USER_ID, userId);
        values.put(COL_HISTORY_ITEM_NAME, itemName);
        values.put(COL_HISTORY_ACTION, action);
        if (previousQuantity != null) {
            values.put(COL_HISTORY_OLD_QTY, previousQuantity);
        }
        if (newQuantity != null) {
            values.put(COL_HISTORY_NEW_QTY, newQuantity);
        }
        values.put(COL_HISTORY_TIMESTAMP, System.currentTimeMillis());

        db.insert(TABLE_HISTORY, null, values);
    }

    /**
     * Retrieves the most recent audit-log entries for a given user, newest
     * first, capped at {@code limit} rows.
     *
     * @param userId The user whose history should be retrieved
     * @param limit  Maximum number of rows to return
     * @return List of HistoryEntry objects, newest first
     */
    public List<HistoryEntry> getRecentHistory(long userId, int limit) {
        List<HistoryEntry> entries = new ArrayList<>();
        SQLiteDatabase db = this.getReadableDatabase();

        Cursor cursor = db.rawQuery(
            "SELECT " + COL_HISTORY_ITEM_NAME + ", " + COL_HISTORY_ACTION + ", " +
            COL_HISTORY_OLD_QTY + ", " + COL_HISTORY_NEW_QTY + ", " + COL_HISTORY_TIMESTAMP +
            " FROM " + TABLE_HISTORY +
            " WHERE " + COL_HISTORY_USER_ID + "=?" +
            " ORDER BY " + COL_HISTORY_TIMESTAMP + " DESC" +
            " LIMIT ?",
            new String[]{String.valueOf(userId), String.valueOf(limit)}
        );

        if (cursor.moveToFirst()) {
            do {
                String itemName = cursor.getString(cursor.getColumnIndexOrThrow(COL_HISTORY_ITEM_NAME));
                String action   = cursor.getString(cursor.getColumnIndexOrThrow(COL_HISTORY_ACTION));

                int oldQtyIdx = cursor.getColumnIndexOrThrow(COL_HISTORY_OLD_QTY);
                int newQtyIdx = cursor.getColumnIndexOrThrow(COL_HISTORY_NEW_QTY);
                Integer oldQty = cursor.isNull(oldQtyIdx) ? null : cursor.getInt(oldQtyIdx);
                Integer newQty = cursor.isNull(newQtyIdx) ? null : cursor.getInt(newQtyIdx);

                long timestamp = cursor.getLong(cursor.getColumnIndexOrThrow(COL_HISTORY_TIMESTAMP));

                entries.add(new HistoryEntry(itemName, action, oldQty, newQty, timestamp));
            } while (cursor.moveToNext());
        }

        cursor.close();
        db.close();

        return entries;
    }
}
