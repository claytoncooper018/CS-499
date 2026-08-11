package com.example.inventoryapp;

import android.content.Context;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * Local JUnit tests for DatabaseHelper, run against a real, in-memory-backed
 * SQLite database via Robolectric (no emulator required). These cover the
 * Milestone Four changes: per-user data isolation, the inventory_history
 * audit log, and the transactional writes that keep the two in sync.
 *
 * Every test creates its own DatabaseHelper against a fresh app-under-test
 * context, so tests never see each other's data.
 */
@RunWith(RobolectricTestRunner.class)
public class DatabaseHelperTest {

    private DatabaseHelper dbHelper;

    @Before
    public void setUp() {
        Context context = RuntimeEnvironment.getApplication();
        dbHelper = new DatabaseHelper(context);
    }

    // =========================================================================
    // User CRUD
    // =========================================================================

    @Test
    public void createUser_returnsPositiveRowId() {
        long userId = dbHelper.createUser("alice", "Password123");
        assertTrue(userId > 0);
    }

    @Test
    public void createUser_rejectsDuplicateUsername() {
        dbHelper.createUser("alice", "Password123");
        long secondAttempt = dbHelper.createUser("alice", "DifferentPassword");
        assertEquals(-1, secondAttempt);
    }

    @Test
    public void validateUser_returnsUserId_onCorrectCredentials() {
        long createdId = dbHelper.createUser("alice", "Password123");
        long validatedId = dbHelper.validateUser("alice", "Password123");
        assertEquals(createdId, validatedId);
    }

    @Test
    public void validateUser_returnsNegativeOne_onWrongPassword() {
        dbHelper.createUser("alice", "Password123");
        assertEquals(-1, dbHelper.validateUser("alice", "WrongPassword"));
    }

    @Test
    public void validateUser_returnsNegativeOne_onUnknownUsername() {
        assertEquals(-1, dbHelper.validateUser("nobody", "whatever"));
    }

    // =========================================================================
    // Inventory CRUD is scoped per-user
    // =========================================================================

    @Test
    public void getAllItems_onlyReturnsItemsOwnedByThatUser() {
        long userA = dbHelper.createUser("alice", "pw");
        long userB = dbHelper.createUser("bob", "pw");

        dbHelper.addItem(new InventoryItem("Hammer", "Tools", 10), userA);
        dbHelper.addItem(new InventoryItem("Wrench", "Tools", 5), userB);

        List<InventoryItem> aliceItems = dbHelper.getAllItems(userA);
        List<InventoryItem> bobItems = dbHelper.getAllItems(userB);

        assertEquals(1, aliceItems.size());
        assertEquals("Hammer", aliceItems.get(0).getName());

        assertEquals(1, bobItems.size());
        assertEquals("Wrench", bobItems.get(0).getName());
    }

    @Test
    public void twoUsers_canEachHaveAnItemWithTheSameName() {
        // Confirms the fix actually removed the old global-uniqueness
        // behavior: "Hammer" is only unique per-user now, not app-wide.
        long userA = dbHelper.createUser("alice", "pw");
        long userB = dbHelper.createUser("bob", "pw");

        long rowIdA = dbHelper.addItem(new InventoryItem("Hammer", "Tools", 10), userA);
        long rowIdB = dbHelper.addItem(new InventoryItem("Hammer", "Tools", 3), userB);

        assertNotEquals(-1, rowIdA);
        assertNotEquals(-1, rowIdB);
        assertEquals(1, dbHelper.getAllItems(userA).size());
        assertEquals(1, dbHelper.getAllItems(userB).size());
    }

    @Test
    public void updateItemQuantity_failsWhenCalledByNonOwner() {
        long userA = dbHelper.createUser("alice", "pw");
        long userB = dbHelper.createUser("bob", "pw");

        long itemId = dbHelper.addItem(new InventoryItem("Hammer", "Tools", 10), userA);

        // Bob tries to update Alice's item by guessing its row ID.
        int rowsAffected = dbHelper.updateItemQuantity((int) itemId, 999, userB);

        assertEquals(0, rowsAffected);
        // Alice's copy is untouched.
        assertEquals(10, dbHelper.getAllItems(userA).get(0).getQuantity());
    }

    @Test
    public void updateItemQuantity_succeedsForTheOwner() {
        long userA = dbHelper.createUser("alice", "pw");
        long itemId = dbHelper.addItem(new InventoryItem("Hammer", "Tools", 10), userA);

        int rowsAffected = dbHelper.updateItemQuantity((int) itemId, 4, userA);

        assertEquals(1, rowsAffected);
        assertEquals(4, dbHelper.getAllItems(userA).get(0).getQuantity());
    }

    @Test
    public void deleteItem_failsWhenCalledByNonOwner() {
        long userA = dbHelper.createUser("alice", "pw");
        long userB = dbHelper.createUser("bob", "pw");

        long itemId = dbHelper.addItem(new InventoryItem("Hammer", "Tools", 10), userA);

        int rowsDeleted = dbHelper.deleteItem((int) itemId, userB);

        assertEquals(0, rowsDeleted);
        assertEquals(1, dbHelper.getAllItems(userA).size());
    }

    @Test
    public void deleteItem_succeedsForTheOwner() {
        long userA = dbHelper.createUser("alice", "pw");
        long itemId = dbHelper.addItem(new InventoryItem("Hammer", "Tools", 10), userA);

        int rowsDeleted = dbHelper.deleteItem((int) itemId, userA);

        assertEquals(1, rowsDeleted);
        assertTrue(dbHelper.getAllItems(userA).isEmpty());
    }

    // =========================================================================
    // Audit log (inventory_history)
    // =========================================================================

    @Test
    public void addItem_writesAnAddEntryToHistory() {
        long userA = dbHelper.createUser("alice", "pw");
        dbHelper.addItem(new InventoryItem("Hammer", "Tools", 10), userA);

        List<HistoryEntry> history = dbHelper.getRecentHistory(userA, 10);

        assertEquals(1, history.size());
        assertEquals(DatabaseHelper.ACTION_ADD, history.get(0).getAction());
        assertEquals("Hammer", history.get(0).getItemName());
        assertNull(history.get(0).getPreviousQuantity());
        assertEquals(Integer.valueOf(10), history.get(0).getNewQuantity());
    }

    @Test
    public void updateItemQuantity_writesAnUpdateEntryWithOldAndNewQuantity() {
        long userA = dbHelper.createUser("alice", "pw");
        long itemId = dbHelper.addItem(new InventoryItem("Hammer", "Tools", 10), userA);

        dbHelper.updateItemQuantity((int) itemId, 4, userA);

        List<HistoryEntry> history = dbHelper.getRecentHistory(userA, 10);
        // Newest first: the UPDATE entry should be at index 0, the ADD at index 1.
        assertEquals(2, history.size());
        HistoryEntry updateEntry = history.get(0);
        assertEquals(DatabaseHelper.ACTION_UPDATE, updateEntry.getAction());
        assertEquals(Integer.valueOf(10), updateEntry.getPreviousQuantity());
        assertEquals(Integer.valueOf(4), updateEntry.getNewQuantity());
    }

    @Test
    public void deleteItem_writesADeleteEntryAndSurvivesTheItemBeingGone() {
        long userA = dbHelper.createUser("alice", "pw");
        long itemId = dbHelper.addItem(new InventoryItem("Hammer", "Tools", 10), userA);

        dbHelper.deleteItem((int) itemId, userA);

        List<HistoryEntry> history = dbHelper.getRecentHistory(userA, 10);
        HistoryEntry deleteEntry = history.get(0);

        assertEquals(DatabaseHelper.ACTION_DELETE, deleteEntry.getAction());
        assertEquals("Hammer", deleteEntry.getItemName());
        assertEquals(Integer.valueOf(10), deleteEntry.getPreviousQuantity());
        // The item row is gone, but its name lives on in the history
        // snapshot -- this is the point of denormalizing item_name.
        assertTrue(dbHelper.getAllItems(userA).isEmpty());
    }

    @Test
    public void failedUpdate_byNonOwner_writesNoHistoryEntry() {
        // A rejected write (wrong owner) should not create a misleading
        // audit trail entry, since the transaction never reaches
        // setTransactionSuccessful() with a row actually changed.
        long userA = dbHelper.createUser("alice", "pw");
        long userB = dbHelper.createUser("bob", "pw");
        long itemId = dbHelper.addItem(new InventoryItem("Hammer", "Tools", 10), userA);

        dbHelper.updateItemQuantity((int) itemId, 999, userB);

        // Bob's history should be empty; he never touched anything of his own.
        assertTrue(dbHelper.getRecentHistory(userB, 10).isEmpty());
        // Alice's history should only have her original ADD entry.
        assertEquals(1, dbHelper.getRecentHistory(userA, 10).size());
    }

    @Test
    public void getRecentHistory_respectsTheLimitAndNewestFirstOrdering() {
        long userA = dbHelper.createUser("alice", "pw");
        long itemId = dbHelper.addItem(new InventoryItem("Hammer", "Tools", 10), userA);

        dbHelper.updateItemQuantity((int) itemId, 8, userA);
        dbHelper.updateItemQuantity((int) itemId, 6, userA);
        dbHelper.updateItemQuantity((int) itemId, 4, userA);

        List<HistoryEntry> limited = dbHelper.getRecentHistory(userA, 2);

        assertEquals(2, limited.size());
        // Most recent update (6 -> 4) should come first.
        assertEquals(Integer.valueOf(4), limited.get(0).getNewQuantity());
        assertEquals(Integer.valueOf(6), limited.get(1).getNewQuantity());
    }
}
