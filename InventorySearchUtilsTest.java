package com.example.inventoryapp;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * Local JUnit tests for InventorySearchUtils. These run on the host JVM
 * since InventorySearchUtils only depends on java.util, not android.* APIs.
 */
public class InventorySearchUtilsTest {

    /** Builds a list already sorted ascending by name, matching the
     * precondition getAllItems() guarantees via its ORDER BY clause. */
    private List<InventoryItem> buildSortedSampleItems() {
        List<InventoryItem> items = new ArrayList<>();
        items.add(new InventoryItem(1, "Batteries", "Electronics", 12));
        items.add(new InventoryItem(2, "Hammer", "Tools", 4));
        items.add(new InventoryItem(3, "Notebook", "Office", 0));
        items.add(new InventoryItem(4, "Screwdriver", "Tools", 7));
        items.add(new InventoryItem(5, "Wrench", "Tools", 2));
        return items;
    }

    @Test
    public void findByNameSorted_findsItemInMiddleOfList() {
        List<InventoryItem> items = buildSortedSampleItems();
        InventoryItem found = InventorySearchUtils.findByNameSorted(items, "Notebook");
        assertEquals("Notebook", found.getName());
    }

    @Test
    public void findByNameSorted_findsFirstItem() {
        List<InventoryItem> items = buildSortedSampleItems();
        InventoryItem found = InventorySearchUtils.findByNameSorted(items, "Batteries");
        assertEquals("Batteries", found.getName());
    }

    @Test
    public void findByNameSorted_findsLastItem() {
        List<InventoryItem> items = buildSortedSampleItems();
        InventoryItem found = InventorySearchUtils.findByNameSorted(items, "Wrench");
        assertEquals("Wrench", found.getName());
    }

    @Test
    public void findByNameSorted_isCaseInsensitive() {
        List<InventoryItem> items = buildSortedSampleItems();
        InventoryItem found = InventorySearchUtils.findByNameSorted(items, "hammer");
        assertEquals("Hammer", found.getName());
    }

    @Test
    public void findByNameSorted_returnsNullWhenNotPresent() {
        List<InventoryItem> items = buildSortedSampleItems();
        InventoryItem found = InventorySearchUtils.findByNameSorted(items, "Ladder");
        assertNull(found);
    }

    @Test
    public void findByNameSorted_returnsNullOnEmptyList() {
        InventoryItem found = InventorySearchUtils.findByNameSorted(new ArrayList<>(), "Anything");
        assertNull(found);
    }

    @Test
    public void filterByKeyword_matchesOnName() {
        List<InventoryItem> items = buildSortedSampleItems();
        List<InventoryItem> results = InventorySearchUtils.filterByKeyword(items, "wrench");
        assertEquals(1, results.size());
        assertEquals("Wrench", results.get(0).getName());
    }

    @Test
    public void filterByKeyword_matchesOnCategoryAcrossMultipleItems() {
        List<InventoryItem> items = buildSortedSampleItems();
        List<InventoryItem> results = InventorySearchUtils.filterByKeyword(items, "tools");
        assertEquals(3, results.size());
        for (InventoryItem item : results) {
            assertEquals("Tools", item.getCategory());
        }
    }

    @Test
    public void filterByKeyword_matchesPartialSubstring() {
        List<InventoryItem> items = buildSortedSampleItems();
        List<InventoryItem> results = InventorySearchUtils.filterByKeyword(items, "screw");
        assertEquals(1, results.size());
        assertEquals("Screwdriver", results.get(0).getName());
    }

    @Test
    public void filterByKeyword_emptyKeywordReturnsAllItems() {
        List<InventoryItem> items = buildSortedSampleItems();
        List<InventoryItem> results = InventorySearchUtils.filterByKeyword(items, "");
        assertEquals(items.size(), results.size());
    }

    @Test
    public void filterByKeyword_noMatchReturnsEmptyList() {
        List<InventoryItem> items = buildSortedSampleItems();
        List<InventoryItem> results = InventorySearchUtils.filterByKeyword(items, "xyz123");
        assertTrue(results.isEmpty());
    }
}
