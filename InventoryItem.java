package com.example.inventoryapp;

/**
 * InventoryItem is a simple data model representing a single inventory record.
 * Each item has a unique database ID, a name, a category, and a quantity.
 */
public class InventoryItem {

    // Unique identifier from SQLite database (auto-incremented primary key)
    private int id;

    // Human-readable name of the inventory item
    private String name;

    // Category used to group related items (e.g., "Electronics", "Tools")
    private String category;

    // Current stock quantity; zero triggers a low-stock SMS alert
    private int quantity;

    /**
     * Constructor used when creating a new item before saving to the database.
     * The id will be assigned by SQLite upon insertion.
     *
     * @param name     Name of the item
     * @param category Category label for grouping
     * @param quantity Starting stock quantity
     */
    public InventoryItem(String name, String category, int quantity) {
        this.name = name;
        this.category = category;
        this.quantity = quantity;
    }

    /**
     * Constructor used when loading an existing item from the database.
     *
     * @param id       SQLite row ID
     * @param name     Name of the item
     * @param category Category label
     * @param quantity Current stock quantity
     */
    public InventoryItem(int id, String name, String category, int quantity) {
        this.id = id;
        this.name = name;
        this.category = category;
        this.quantity = quantity;
    }

    // --- Getters ---

    public int getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getCategory() {
        return category;
    }

    public int getQuantity() {
        return quantity;
    }

    // --- Setters ---

    public void setId(int id) {
        this.id = id;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public void setQuantity(int quantity) {
        this.quantity = quantity;
    }
}
