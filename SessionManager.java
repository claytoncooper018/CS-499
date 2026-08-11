package com.example.inventoryapp;

/**
 * SessionManager holds the identity of the currently logged-in user for the
 * lifetime of the app process.
 *
 * Why this exists (Milestone Four): prior to this milestone, every screen
 * that touched the database (Dashboard, AddItem) called DatabaseHelper
 * methods that operated on the *entire* inventory table with no concept of
 * which user was logged in. That meant any authenticated user could see,
 * edit, or delete any other user's items simply by being logged in -- the
 * users table existed for login, but it had no relationship to the data it
 * was meant to protect. SessionManager is the small piece of plumbing that
 * lets every DAO call be scoped to "the inventory rows that belong to the
 * user who is currently logged in."
 *
 * This is intentionally a simple in-memory holder (no persistence) since a
 * fresh login is required each time the process restarts, which is the
 * correct behavior for this app.
 */
public final class SessionManager {

    private static long currentUserId = -1L;
    private static String currentUsername = null;

    private SessionManager() {
        // Static utility class; not instantiable.
    }

    /**
     * Called once, immediately after a successful login or account creation.
     *
     * @param userId   The database row ID of the authenticated user
     * @param username The authenticated user's username
     */
    public static void setCurrentUser(long userId, String username) {
        currentUserId = userId;
        currentUsername = username;
    }

    /**
     * @return the row ID of the currently logged-in user, or -1 if nobody
     *         is currently logged in.
     */
    public static long getCurrentUserId() {
        return currentUserId;
    }

    /** @return the currently logged-in username, or null if none. */
    public static String getCurrentUsername() {
        return currentUsername;
    }

    /** @return true if a user is currently logged in. */
    public static boolean isLoggedIn() {
        return currentUserId != -1L;
    }

    /** Clears the session (not currently wired to a "Log Out" button, but
     *  available so one can be added without touching DatabaseHelper). */
    public static void clear() {
        currentUserId = -1L;
        currentUsername = null;
    }
}
