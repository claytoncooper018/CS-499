---
layout: default
title: "Enhancement Two: Algorithms & Data Structures"
---

# Enhancement Two: Algorithms and Data Structure

**Source:** [`InventorySearchUtils.java`](src/main/java/com/example/inventoryapp/InventorySearchUtils.java) · [`InventorySorter.java`](src/main/java/com/example/inventoryapp/InventorySorter.java) · [`InventoryGrouper.java`](src/main/java/com/example/inventoryapp/InventoryGrouper.java)

## Artifact Description

The Inventory App, first developed for CS 360 and then improved for the software engineering and security category in Milestone Two, is the artifact improved in this milestone. It is a native Android application, written in Java against the Android SDK, that allows users to create an account, log in, and manage an inventory of items — adding items, changing quantities, removing items, and getting an SMS alert when an item's supply runs out. Every time the Dashboard panel is displayed, a `DatabaseHelper` class backed by SQLite loads all inventory records into memory as a `List<InventoryItem>`.

This improvement targets the inventory list itself rather than the authentication layer. In the original version, the Dashboard simply displayed whatever `DatabaseHelper.getAllItems()` returned, in whatever order SQLite supplied — there was no way to search, re-sort, or surface which items were running low without manually scrolling the full list.

## Justification for Inclusion

The inventory list is exactly the type of dataset these techniques are meant for — a collection of records a user must swiftly search, arrange, and summarize, and one that will only grow more cumbersome over time. I built a small but complete toolkit, each component chosen because it's the best structural match for a specific operation rather than a one-size-fits-all approach:

- **Sorting (`InventorySorter`)** — Comparator-based sorting by name, category, or quantity (ascending or descending), backed by `Collections.sort()`.
- **Searching (`InventorySearchUtils`)** — a binary search for exact-name lookups against the alphabetically sorted list SQLite already returns, and a linear substring filter for the free-text search box, since a partial match can't be resolved by binary search.
- **Hashing (`InventoryGrouper.groupByCategory`)** — a `HashMap<String, List<InventoryItem>>` built in one pass so items in a category can be retrieved in O(1) average time instead of rescanning the full list.
- **Heap selection (`InventoryGrouper.getLowestStockItems`)** — a bounded max-heap (`PriorityQueue`) that finds the N lowest-quantity items for a "Low Stock Report" without sorting the entire inventory first.

Each of these is fully wired into the Dashboard: `AddItemActivity` now uses binary search to reject duplicate item names in O(log n) rather than an O(n) scan or a silent duplicate insert; the search box filters in real time as the user types; a sort Spinner reorders the grid by four different keys; and a new "Low Stock Report" button shows the five lowest-stock items on demand. Every new class ships with a local JUnit test suite following the same approach as `PasswordUtilsTest` from Milestone Two.

## Course Outcomes

The main outcome this enhancement demonstrates is the ability to design and assess computing solutions using algorithmic principles and suitable data structures, while managing the trade-offs involved in different design choices.

The trade-off reasoning was as intentional as the code itself. Exact-name duplicate checks use binary search (O(log n)) rather than a linear scan (O(n)) because `getAllItems()` already returns items sorted by name — but free-text substring search still requires a linear scan, since "contains" can't be resolved by a sorted key. The Low Stock Report maintains a bounded max-heap sized to exactly the number of results needed (roughly O(n log N) for N results) rather than sorting the entire inventory (O(n log n)) just to show five items. Grouping by category uses a HashMap so repeated category look-ups run in O(1) average case instead of an O(n) scan each time. Each of the three small utility classes could be covered with quick local JUnit tests, since none of them depend on the Android framework.

## Reflection

The most difficult design choice was knowing when to stop reusing the same algorithm everywhere. My initial instinct was to build one all-purpose "search" method for the search box, duplicate-name checks, and any future lookups. Looking at the actual access patterns showed that was wrong: only the exact-match scenario benefits from binary search's O(log n) cost, and an exact-name duplicate check has a precondition (the list is already sorted by name) that a substring search doesn't share. Keeping `filterByKeyword()` and `findByNameSorted()` as two distinct, separately documented methods felt like more code up front, but it lets each be reasoned about, tested, and reused without the caller needing to know the other method's internal shortcuts.

The other real challenge was building several features at once. Once search and sort were both live on the same screen, the Dashboard needed a single source of truth — the unfiltered list from SQLite — from which the filtered, sorted, on-screen list was re-derived every time the sort mode or search phrase changed. Without that separation, applying a sort would have silently discarded an active search filter (or vice versa). Introducing a single `applyFilterAndSort()` pipeline that always filters first and sorts second resolved this cleanly and made the two features composable instead of incompatible.

Writing the JUnit suites for `InventorySorter`, `InventorySearchUtils`, and `InventoryGrouper` reinforced a lesson from Milestone Two: because none of these classes touch Android APIs, every edge case — an empty list, a single-item list, a keyword with no matches, a `topN` greater than the inventory itself — could be tested immediately on the local JVM. Writing those edge-case tests actually caught a real bug: an earlier version of `getLowestStockItems()` didn't guard against `topN` exceeding the list size, and a test written specifically for that case caught it before it ever reached the Dashboard.

Overall, this improvement showed that algorithms-and-data-structures work in a real application is less about implementing a textbook algorithm in isolation, and more about matching each access pattern — exact lookup, partial match, grouping, top-N selection — to the structure that best suits it, and being able to explain why.
