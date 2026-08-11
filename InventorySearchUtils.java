package com.example.inventoryapp;

import java.util.ArrayList;
import java.util.List;

/**
 * InventorySearchUtils provides search operations over an in-memory list of
 * InventoryItem objects.
 *
 * Two distinct algorithms are used depending on the shape of the query:
 *
 *   - findByNameSorted(): a binary search, O(log n), used for exact-name
 *     lookups. It relies on the precondition that the list is already sorted
 *     alphabetically by name, which is guaranteed by
 *     DatabaseHelper.getAllItems() (its SQL query includes ORDER BY name ASC).
 *
 *   - filterByKeyword(): a linear scan, O(n), used for partial/substring
 *     matching across both name and category. A substring match cannot be
 *     resolved with binary search because "contains" is not a total order
 *     over the sorted key, so every element must be inspected once.
 *
 * Keeping the two operations separate -- rather than always doing a linear
 * scan -- lets exact lookups (e.g., duplicate-name checks before an insert)
 * run in logarithmic time instead of linear time as the inventory grows.
 */
public class InventorySearchUtils {

    /**
     * Performs a binary search for an item whose name exactly matches
     * (case-insensitively) the given name.
     *
     * Precondition: sortedItems must already be sorted ascending by name.
     * Passing an unsorted list will produce undefined results, since binary
     * search depends on the list's ordering to eliminate half the remaining
     * search space on each comparison.
     *
     * @param sortedItems list of items sorted ascending by name
     * @param name        exact name to search for
     * @return the matching InventoryItem, or null if not found
     */
    public static InventoryItem findByNameSorted(List<InventoryItem> sortedItems, String name) {
        if (sortedItems == null || name == null) {
            return null;
        }

        int low = 0;
        int high = sortedItems.size() - 1;

        while (low <= high) {
            int mid = low + (high - low) / 2;
            String midName = sortedItems.get(mid).getName();
            int comparison = midName.compareToIgnoreCase(name);

            if (comparison == 0) {
                return sortedItems.get(mid);
            } else if (comparison < 0) {
                low = mid + 1;
            } else {
                high = mid - 1;
            }
        }

        return null;
    }

    /**
     * Filters a list of items down to those whose name or category contains
     * the given keyword (case-insensitive substring match).
     *
     * This is an O(n) linear scan by necessity: unlike an exact match,
     * "contains" cannot be short-circuited by a sorted order, so every item
     * must be checked once. An empty or null keyword returns a copy of the
     * full list unchanged.
     *
     * @param items   items to search across
     * @param keyword text to match against name and category
     * @return a new list containing only the matching items, in original order
     */
    public static List<InventoryItem> filterByKeyword(List<InventoryItem> items, String keyword) {
        List<InventoryItem> results = new ArrayList<>();
        if (items == null) {
            return results;
        }

        if (keyword == null || keyword.trim().isEmpty()) {
            results.addAll(items);
            return results;
        }

        String lowerKeyword = keyword.trim().toLowerCase();
        for (InventoryItem item : items) {
            boolean nameMatches = item.getName().toLowerCase().contains(lowerKeyword);
            boolean categoryMatches = item.getCategory().toLowerCase().contains(lowerKeyword);
            if (nameMatches || categoryMatches) {
                results.add(item);
            }
        }

        return results;
    }
}
