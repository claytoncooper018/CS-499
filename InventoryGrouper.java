package com.example.inventoryapp;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;

/**
 * InventoryGrouper implements two data-structure-driven operations that would
 * be noticeably more expensive if written as repeated linear scans.
 *
 * groupByCategory() builds a HashMap<String, List<InventoryItem>> in a single
 * O(n) pass. Once built, looking up every item in a given category is O(1)
 * average case, versus re-filtering the whole inventory (O(n)) every single
 * time a category's contents are needed.
 *
 * getLowestStockItems() uses a bounded max-heap (PriorityQueue) to find the
 * N lowest-quantity items without sorting the entire inventory. Maintaining a
 * heap of size N costs O(n log N) overall; when N is small and fixed (e.g., a
 * "top 5 lowest stock" report) that beats sorting everything first, which
 * costs O(n log n) regardless of how many results are actually needed.
 */
public class InventoryGrouper {

    /**
     * Groups items by category into a HashMap for O(1) average-case lookup.
     *
     * @param items items to group
     * @return map of category name to the list of items in that category
     */
    public static Map<String, List<InventoryItem>> groupByCategory(List<InventoryItem> items) {
        Map<String, List<InventoryItem>> grouped = new HashMap<>();
        if (items == null) {
            return grouped;
        }

        for (InventoryItem item : items) {
            String category = item.getCategory();
            List<InventoryItem> bucket = grouped.get(category);
            if (bucket == null) {
                bucket = new ArrayList<>();
                grouped.put(category, bucket);
            }
            bucket.add(item);
        }

        return grouped;
    }

    /**
     * Finds the topN items with the lowest quantity using a bounded
     * max-heap: the heap only ever holds the smallest-quantity items seen so
     * far, evicting its current largest member whenever a smaller one is
     * found. This avoids sorting the full inventory when only a handful of
     * low-stock items are needed for a report.
     *
     * @param items full inventory list
     * @param topN  number of lowest-stock items to return
     * @return up to topN items, ordered lowest quantity first
     */
    public static List<InventoryItem> getLowestStockItems(List<InventoryItem> items, int topN) {
        List<InventoryItem> result = new ArrayList<>();
        if (items == null || items.isEmpty() || topN <= 0) {
            return result;
        }

        // Max-heap ordered by quantity descending, so the current largest
        // quantity held in the heap is always at the head and can be
        // evicted first when a smaller-quantity item comes along.
        PriorityQueue<InventoryItem> heap = new PriorityQueue<>(
                Math.min(topN, items.size()),
                Comparator.comparingInt(InventoryItem::getQuantity).reversed());

        for (InventoryItem item : items) {
            if (heap.size() < topN) {
                heap.offer(item);
            } else if (heap.peek() != null && item.getQuantity() < heap.peek().getQuantity()) {
                heap.poll();
                heap.offer(item);
            }
        }

        result.addAll(heap);
        // Heap order is not fully sorted -- only its head is guaranteed to
        // be the max -- so sort the small result set for a readable report.
        result.sort(Comparator.comparingInt(InventoryItem::getQuantity));
        return result;
    }
}
