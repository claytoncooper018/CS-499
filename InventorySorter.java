package com.example.inventoryapp;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * InventorySorter centralizes every ordering the Dashboard can apply to the
 * in-memory inventory list, so DashboardActivity only has to pick a mode
 * rather than hand-roll comparison logic per button.
 *
 * Collections.sort() (backed by a Timsort-style merge sort on the JVM) is used
 * for every mode. Its worst-case cost is O(n log n), but it is adaptive: runs
 * that are already partially ordered -- which is the common case here, since
 * the list is only ever perturbed by a single add/delete/quantity-edit
 * between sorts -- are merged in close to O(n). That adaptiveness, plus its
 * guaranteed stability (equal-key items keep their relative order), makes it
 * a better fit here than a plain quicksort, which offers neither guarantee.
 */
public class InventorySorter {

    public enum SortMode {
        NAME_ASC,
        CATEGORY_ASC,
        QUANTITY_ASC,
        QUANTITY_DESC
    }

    /**
     * Sorts the given list in place according to the requested mode.
     *
     * @param items list to sort (mutated in place)
     * @param mode  which key/direction to sort by
     */
    public static void sort(List<InventoryItem> items, SortMode mode) {
        if (items == null || items.size() < 2) {
            return;
        }

        Comparator<InventoryItem> comparator;

        switch (mode) {
            case CATEGORY_ASC:
                comparator = Comparator
                        .comparing(InventoryItem::getCategory, String.CASE_INSENSITIVE_ORDER)
                        .thenComparing(InventoryItem::getName, String.CASE_INSENSITIVE_ORDER);
                break;
            case QUANTITY_ASC:
                comparator = Comparator.comparingInt(InventoryItem::getQuantity);
                break;
            case QUANTITY_DESC:
                comparator = Comparator.comparingInt(InventoryItem::getQuantity).reversed();
                break;
            case NAME_ASC:
            default:
                comparator = Comparator.comparing(InventoryItem::getName, String.CASE_INSENSITIVE_ORDER);
                break;
        }

        Collections.sort(items, comparator);
    }
}
