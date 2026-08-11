---
layout: default
title: "Enhancement One: Software Design & Engineering"
---

# Enhancement One: Software Design and Engineering

**Source:** [`PasswordUtils.java`](src/main/java/com/example/inventoryapp/PasswordUtils.java) · [`DatabaseHelper.java`](src/main/java/com/example/inventoryapp/DatabaseHelper.java) · [`PasswordUtilsTest.java`](src/test/java/com/example/inventoryapp/PasswordUtilsTest.java)

## Artifact Description

The Inventory App, a native Android application created for CS 360 (Mobile Architecture and Programming), is the artifact I chose for this improvement. In that course, the project was finished in two stages: Project Two produced the UI/UX design, and Project Three finished the completely functional application. A user can register an account, log in, view and manage an inventory of things on a dashboard, add new items, and receive an automated SMS text alert when an item's stock level drops to zero. It was first turned in as part of my CS 360 Module Eight and is built in Java using Android Studio with SQLite serving as the local data storage.

## Justification for Inclusion

I selected this artifact because it shows a complete, functional, end-to-end mobile application rather than a standalone exercise. It features a device-integration feature (SMS), a persistence layer, and a user interface layer, all of which must function properly. My ability to construct a clear separation of concerns is also evident in the `DatabaseHelper` class, which exposes straightforward, well-documented CRUD methods (`createUser`, `validateUser`, `addItem`, `updateItemQuantity`, `deleteItem`) so that the Activity classes never have to write raw SQL.

I concentrated especially on the authentication system's security for this improvement. The old version used a direct string comparison between the entered password and the stored value to validate login — user passwords were kept in the SQLite database as plain text. Although this works in theory, there is a significant security vulnerability, because every user's password could be read in plain text by anyone who managed to obtain access to the app's underlying database file, which does not need root access on many Android devices and emulators.

To solve this, I created a new `PasswordUtils` class that, before anything is saved to the database, creates a distinct, cryptographically random salt for every user and combines it with their password using SHA-256 hashing. Only a `password_hash` and a `salt` column are now stored in the users table; the plain-text password is no longer stored or queried. I also rewrote the schema migration logic (`onUpgrade`) so that the upgrade path re-hashes each existing user's password and safely migrates their account into the new schema, instead of the original method of dropping and recreating tables — which would have silently deleted every existing account. Lastly, I added a new `PasswordUtilsTest` suite with local JUnit tests confirming that hashing is deterministic for a given salt, that per-user salting causes identical passwords to generate different hashes for different users, and that verification accurately accepts valid passwords and rejects invalid ones. This significantly increased the project's test coverage, since the original code had only one placeholder unit test.

## Course Outcomes

I identified two outcomes I wanted to demonstrate for this category: the capacity to develop a security mindset that anticipates adversarial exploits and mitigates design flaws in software architecture, and the ability to use well-founded techniques and tools to implement a computing solution that delivers real value.

This improvement achieves both. The security outcome is straightforward: I found a real-world, concrete vulnerability (plain-text password storage) and replaced it with an industry-standard mitigation (per-user salted hashing), while ensuring the remedy would not covertly erase user data during the schema update. The software-engineering outcome shows in how the change was integrated: no other classes in the application needed to change, because the public method signatures on `DatabaseHelper` (`createUser`, `validateUser`) remained the same — a thoughtful, low-risk approach to changing a shared component the rest of the application depends on. Adding actual unit tests for the new logic also demonstrates good engineering practice rather than treating the fix as a one-time patch.

## Reflection

Ensuring the safety of the upgrade path for current users was the primary technical difficulty. Since the original `onUpgrade` method would have simply dropped the old users table, it would have been easy to bump the database version and remove it — but that would have silently erased every account that existed before the improvement. Working through a real migration (renaming the old table, reading out each existing plain-text password, hashing it with a new salt, inserting it into the new schema, and only then discarding the old table) forced me to consider the improvement not just as "add better password storage" but as "change a live schema without losing data" — a far more practical engineering problem.

While building `PasswordUtilsTest`, I also gained some knowledge about testing scope. Android's built-in `Base64` class was my initial choice, but it only functions with an emulator or a framework like Robolectric and is stubbed out in local JVM unit tests. Moving to `java.util.Base64` instead allowed me to keep the hashing logic in plain Java, which is completely testable with quick local unit tests and doesn't require an emulator — a helpful reminder that separating code from framework-specific APIs directly improves the testability of that logic.

Overall, this improvement demonstrated how significantly a single design choice, such as the storage of credentials, can affect an application's actual security posture, and how crucial it is to carefully consider data migration whenever a live schema changes.
