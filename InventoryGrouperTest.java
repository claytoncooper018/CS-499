package com.example.inventoryapp;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Local JUnit tests for InventoryGrouper. These run on the host JVM since
 * InventoryGrouper only depends on java.util, not android.* APIs.
 */
public class InventoryGrouperTest {

    private List<InventoryItem> buildSampleItems() {
        List<InventoryItem> items = new ArrayList<>();
        items.add(new InventoryItem(1, "Batteries", "Electronics", 12));
        items.add(new InventoryItem(2, "Hammer", "Tools", 4));
        items.add(new InventoryItem(3, "Notebook", "Office", 0));
        items.add(new InventoryItem(4, "Screwdriver", "Tools", 7));
        items.add(new InventoryItem(5, "Wrench", "Tools", 2));
        return items;
    }

    @Test
    public void groupByCategory_createsOneBucketPerDistinctCategory() {
        Map<String, List<InventoryItem>> grouped = InventoryGrouper.groupByCategory(buildSampleItems());
        assertEquals(3, grouped.size());
        assertTrue(grouped.containsKey("Electronics"));
        assertTrue(grouped.containsKey("Office"));
        assertTrue(grouped.containsKey("Tools"));
    }

    @Test
    public void groupByCategory_placesAllMatchingItemsInSameBucket() {
        Map<String, List<InventoryItem>> grouped = InventoryGrouper.groupByCategory(buildSampleItems());
        assertEquals(3, grouped.get("Tools").size());
    }

    @Test
    public void groupByCategory_emptyListReturnsEmptyMap() {
        Map<String, List<InventoryItem>> grouped = InventoryGrouper.groupByCategory(new ArrayList<>());
        assertTrue(grouped.isEmpty());
    }

    @Test
    public void getLowestStockItems_returnsCorrectCountOrderedAscending() {
        List<InventoryItem> lowest = InventoryGrouper.getLowestStockItems(buildSampleItems(), 3);
        assertEquals(3, lowest.size());
        assertEquals(0, lowest.get(0).getQuantity());
        assertEquals(2, lowest.get(1).getQuantity());
        assertEquals(4, lowest.get(2).getQuantity());
    }

    @Test
    public void getLowestStockItems_topNGreaterThanListSizeReturnsAllItems() {
        List<InventoryItem> lowest = InventoryGrouper.getLowestStockItems(buildSampleItems(), 100);
        assertEquals(5, lowest.size());
    }

    @Test
    public void getLowestStockItems_zeroOrNegativeTopNReturnsEmptyList() {
        List<InventoryItem> lowest = InventoryGrouper.getLowestStockItems(buildSampleItems(), 0);
        assertTrue(lowest.isEmpty());
    }

    @Test
    public void getLowestStockItems_emptyInventoryReturnsEmptyList() {
        List<InventoryItem> lowest = InventoryGrouper.getLowestStockItems(new ArrayList<>(), 5);
        assertTrue(lowest.isEmpty());
    }
}
