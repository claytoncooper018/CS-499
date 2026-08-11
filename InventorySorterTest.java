package com.example.inventoryapp;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;

/**
 * Local JUnit tests for InventorySorter. These run on the host JVM since
 * InventorySorter only depends on java.util, not android.* APIs.
 */
public class InventorySorterTest {

    private List<InventoryItem> buildUnsortedSampleItems() {
        List<InventoryItem> items = new ArrayList<>();
        items.add(new InventoryItem(1, "Wrench", "Tools", 2));
        items.add(new InventoryItem(2, "Batteries", "Electronics", 12));
        items.add(new InventoryItem(3, "Screwdriver", "Tools", 7));
        items.add(new InventoryItem(4, "Notebook", "Office", 0));
        return items;
    }

    @Test
    public void sort_nameAsc_ordersAlphabetically() {
        List<InventoryItem> items = buildUnsortedSampleItems();
        InventorySorter.sort(items, InventorySorter.SortMode.NAME_ASC);

        assertEquals("Batteries", items.get(0).getName());
        assertEquals("Notebook", items.get(1).getName());
        assertEquals("Screwdriver", items.get(2).getName());
        assertEquals("Wrench", items.get(3).getName());
    }

    @Test
    public void sort_quantityAsc_ordersLowestFirst() {
        List<InventoryItem> items = buildUnsortedSampleItems();
        InventorySorter.sort(items, InventorySorter.SortMode.QUANTITY_ASC);

        assertEquals(0, items.get(0).getQuantity());
        assertEquals(2, items.get(1).getQuantity());
        assertEquals(7, items.get(2).getQuantity());
        assertEquals(12, items.get(3).getQuantity());
    }

    @Test
    public void sort_quantityDesc_ordersHighestFirst() {
        List<InventoryItem> items = buildUnsortedSampleItems();
        InventorySorter.sort(items, InventorySorter.SortMode.QUANTITY_DESC);

        assertEquals(12, items.get(0).getQuantity());
        assertEquals(7, items.get(1).getQuantity());
        assertEquals(2, items.get(2).getQuantity());
        assertEquals(0, items.get(3).getQuantity());
    }

    @Test
    public void sort_categoryAsc_groupsMatchingCategoriesTogether() {
        List<InventoryItem> items = buildUnsortedSampleItems();
        InventorySorter.sort(items, InventorySorter.SortMode.CATEGORY_ASC);

        // Electronics, Office, Tools, Tools
        assertEquals("Electronics", items.get(0).getCategory());
        assertEquals("Office", items.get(1).getCategory());
        assertEquals("Tools", items.get(2).getCategory());
        assertEquals("Tools", items.get(3).getCategory());
    }

    @Test
    public void sort_categoryAsc_breaksTiesByName() {
        List<InventoryItem> items = buildUnsortedSampleItems();
        InventorySorter.sort(items, InventorySorter.SortMode.CATEGORY_ASC);

        // Both Tools items should be present, with Screwdriver before Wrench
        assertEquals("Screwdriver", items.get(2).getName());
        assertEquals("Wrench", items.get(3).getName());
    }

    @Test
    public void sort_emptyList_doesNotThrow() {
        List<InventoryItem> items = new ArrayList<>();
        InventorySorter.sort(items, InventorySorter.SortMode.NAME_ASC);
        assertEquals(0, items.size());
    }

    @Test
    public void sort_singleItemList_remainsUnchanged() {
        List<InventoryItem> items = new ArrayList<>();
        items.add(new InventoryItem(1, "OnlyItem", "Misc", 3));
        InventorySorter.sort(items, InventorySorter.SortMode.QUANTITY_DESC);
        assertEquals("OnlyItem", items.get(0).getName());
    }
}
