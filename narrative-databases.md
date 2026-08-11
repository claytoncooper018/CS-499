---
layout: default
title: "Enhancement Three: Databases"
---

# Enhancement Three: Databases

**Source:** [`DatabaseHelper.java`](src/main/java/com/example/inventoryapp/DatabaseHelper.java) · [`SessionManager.java`](src/main/java/com/example/inventoryapp/SessionManager.java) · [`HistoryEntry.java`](src/main/java/com/example/inventoryapp/HistoryEntry.java) · [`DatabaseHelperTest.java`](src/test/java/com/example/inventoryapp/DatabaseHelperTest.java)

## Artifact Description

The Inventory App, originally developed for CS 360, has now been improved twice in this capstone: for Software Design and Engineering in Milestone Two (salted-hash authentication) and for Algorithms and Data Structures in Milestone Three (searching, sorting, grouping, and heap-based low-stock reporting). It's a native Android application, written in Java, that lets a user register, log in, and manage an inventory of products, including adding, modifying, and deleting items, and getting an SMS alert when an item's amount drops to zero. A single `DatabaseHelper` class, based on `SQLiteOpenHelper`, handles all persistence.

This milestone addresses that persistence layer directly. Before this milestone, the database design had two structural issues. First, there was no connection between the inventory table and the users table — every entry was global, so any logged-in account could view, modify, or remove any other account's items. Second, the database had no record of its own history; if a quantity was overwritten or an item deleted, there was no way to determine what changed or when.

## Justification for Inclusion

I improved the Inventory App a third time in the Databases category because a single artifact examined through three distinct lenses — security, algorithms, and now database design — tells a more cohesive story about my growth than three unconnected artifacts would. This improvement demonstrates:

- **Relational integrity** — the inventory table now carries a `user_id` foreign key back to `users`, with an explicit `ON DELETE CASCADE` rule. `PRAGMA foreign_keys` is enabled for every connection via `onConfigure()`, so the constraint is enforced in practice, not just on paper.
- **Schema migration** — `onUpgrade()` carries every pre-existing inventory row forward into the new schema (assigning ownerless legacy rows to the earliest registered account) instead of dropping and recreating the table, following the same non-destructive migration pattern used for the users table in Milestone Two.
- **A new audit-log table (`inventory_history`)** — tracks every addition, update, and deletion with a timestamp and, where relevant, the old and new quantity, plus a denormalized snapshot of the item's name so a history entry stays readable even after the item itself is deleted.
- **Transactions** — every write that touches both the history table and the inventory table is wrapped in `beginTransaction()` / `setTransactionSuccessful()` / `endTransaction()`, so the audit log can never fall out of sync with the data it describes due to a crash mid-write.
- **Indexing** — indices on `inventory.user_id`, `inventory.category`, and `inventory_history.item_id` / `user_id`, since each is now filtered on every screen load instead of scanned only at startup.

A new `SessionManager` class, set at login time, lets `DashboardActivity` and `AddItemActivity` read the logged-in user's ID on the application side. Every `DatabaseHelper` call (`getAllItems`, `addItem`, `updateItemQuantity`, `deleteItem`) now takes that ID as a parameter and uses it to filter or verify ownership. The audit log is directly visible to the user through a new "View History" button on the Dashboard, built the same way as the "Low Stock Report" button from Milestone Three.

## Course Outcomes

This enhancement targets the outcome of applying well-founded, innovative computing techniques and tools to build solutions that deliver real value in database work, software engineering, and design. The migration path shows the same respect for existing data as the Milestone Two password migration; the audit log and transactional writes are genuine database techniques, not just more Java; and the user-scoping fix closes a real data-isolation gap rather than a cosmetic one.

This improvement also significantly advances the security-mindset outcome I designated as this category's secondary goal. Because every `UPDATE` and `DELETE` statement now includes `user_id` in its `WHERE` clause — not just `SELECT` statements — scoping every read and write to the authenticated user's ID is an authorization fix, not merely a filtering convenience. Even a logged-in user who guesses the row ID of another item can no longer read, modify, or delete a row they don't own.

## Reflection

The hardest design choice wasn't the schema itself — it was the migration: how to add a `NOT NULL`, foreign-keyed `user_id` column to a table that had been storing unscoped data with no ownership information at all. Because SQLite's `ALTER TABLE` can't add a foreign-key-constrained column after the fact, the schema change had to follow the same rename-recreate-repopulate-drop pattern used for the users table migration in Milestone Two. The genuinely unpleasant part was deciding what to do with rows that already existed but had no recorded owner. Rather than silently deleting them, I assigned them to the earliest-registered account, and documented that assumption explicitly in both this narrative and the migration method's own comments — a migration that's honest about its limitations is better than one that silently loses or reassigns data without saying so.

The other significant problem was keeping the new `inventory_history` table useful after the item it describes is deleted. My first instinct was to join `inventory_history` to `inventory` on `item_id`, but that breaks the moment an item is deleted, since the join would return nothing for that item's own deletion entry. Storing a denormalized snapshot of the item's name directly on the history row solves this, at the cost of the usual normalization trade-off — the name could theoretically drift out of sync with a renamed item. I decided that trade-off was correct, because an audit log exists to document events as they happened, not to reflect the current state of the table it's auditing; a history entry that quietly rewrites its own past would defeat the point of keeping it.

Writing the JUnit test suite for `DatabaseHelper` also taught me something specific about testing database code. Since `DatabaseHelper` extends `SQLiteOpenHelper`, it couldn't be tested the way Milestone Three's pure-Java utility classes were. I introduced Robolectric, which runs a real SQLite database against a simulated Android environment directly on the local JVM, instead of relying on a slow, emulator-based instrumented test — letting me write standard, fast JUnit tests that verify one user's items are invisible to another, that a non-owner's update or delete attempt is silently rejected rather than silently succeeding, and that every add, update, and delete produces exactly the audit-log row it should.

Altogether, this improvement reaffirmed that database work in a real application rarely stops at the schema diagram. The columns here are simple; the hard choices were what a migration owes to data it can no longer fully explain, and what a history table is allowed to assume about the future of the rows it covers.
