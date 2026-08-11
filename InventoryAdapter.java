package com.example.inventoryapp;

import android.app.AlertDialog;
import android.content.Context;
import android.text.InputType;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

/**
 * InventoryAdapter binds a list of InventoryItem objects to the RecyclerView
 * on the DashboardActivity. Each row displays item name, category, and quantity,
 * with buttons to delete a row or tap the quantity to edit it inline.
 */
public class InventoryAdapter extends RecyclerView.Adapter<InventoryAdapter.ViewHolder> {

    // Callback interface so DashboardActivity can react to user actions
    public interface OnItemActionListener {
        /** Called when the user confirms deletion of an item */
        void onDelete(InventoryItem item);

        /** Called when the user saves a new quantity for an item */
        void onQuantityUpdated(InventoryItem item, int newQuantity);
    }

    private final Context              context;
    private       List<InventoryItem>  items;
    private final OnItemActionListener listener;

    /**
     * @param context  Activity context (for inflating dialogs)
     * @param items    Live list of inventory items to display
     * @param listener Callback to handle delete and update actions
     */
    public InventoryAdapter(Context context, List<InventoryItem> items,
                            OnItemActionListener listener) {
        this.context  = context;
        this.items    = items;
        this.listener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        // Inflate the row layout for each inventory item
        View view = LayoutInflater.from(context)
                .inflate(R.layout.item_inventory_row, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        InventoryItem item = items.get(position);

        // Populate text fields from the item data
        holder.textViewName.setText(item.getName());
        holder.textViewCategory.setText(item.getCategory());
        holder.textViewQuantity.setText(String.valueOf(item.getQuantity()));

        // Highlight zero-quantity items in red as a visual warning
        if (item.getQuantity() == 0) {
            holder.textViewQuantity.setTextColor(context.getResources().getColor(
                android.R.color.holo_red_dark, null));
        } else {
            holder.textViewQuantity.setTextColor(context.getResources().getColor(
                android.R.color.holo_blue_dark, null));
        }

        // Tapping the quantity cell opens an edit dialog (UPDATE operation)
        holder.textViewQuantity.setOnClickListener(v -> showEditQuantityDialog(item));

        // Delete button triggers a confirmation dialog before removing the item
        holder.buttonDelete.setOnClickListener(v -> showDeleteConfirmDialog(item));
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    /**
     * Replaces the backing list (e.g., after a search or sort change) and
     * notifies the RecyclerView so it redraws with the new contents.
     *
     * @param newItems the filtered/sorted list to display
     */
    public void updateItems(List<InventoryItem> newItems) {
        this.items = newItems;
        notifyDataSetChanged();
    }

    /**
     * Shows an AlertDialog with an EditText pre-filled with the current quantity.
     * On confirmation the listener is called with the new value.
     *
     * @param item The inventory item whose quantity is being edited
     */
    private void showEditQuantityDialog(InventoryItem item) {
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle("Update Quantity: " + item.getName());

        // EditText for the user to type the new quantity
        final EditText input = new EditText(context);
        input.setInputType(InputType.TYPE_CLASS_NUMBER);
        input.setText(String.valueOf(item.getQuantity()));
        input.selectAll();  // Pre-select so user can overwrite immediately
        builder.setView(input);

        builder.setPositiveButton("Save", (dialog, which) -> {
            String raw = input.getText().toString().trim();
            if (!raw.isEmpty()) {
                int newQty = Integer.parseInt(raw);
                listener.onQuantityUpdated(item, newQty);
            }
        });

        builder.setNegativeButton("Cancel", (dialog, which) -> dialog.cancel());

        builder.show();
    }

    /**
     * Shows a confirmation AlertDialog before permanently deleting an item.
     *
     * @param item The inventory item to delete
     */
    private void showDeleteConfirmDialog(InventoryItem item) {
        new AlertDialog.Builder(context)
            .setTitle("Delete Item")
            .setMessage("Are you sure you want to delete \"" + item.getName() + "\"?")
            .setPositiveButton("Delete", (dialog, which) -> listener.onDelete(item))
            .setNegativeButton("Cancel", (dialog, which) -> dialog.cancel())
            .show();
    }

    /**
     * ViewHolder caches view references to avoid repeated findViewById calls.
     */
    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView textViewName;
        TextView textViewCategory;
        TextView textViewQuantity;
        Button   buttonDelete;

        ViewHolder(View itemView) {
            super(itemView);
            textViewName     = itemView.findViewById(R.id.textViewName);
            textViewCategory = itemView.findViewById(R.id.textViewCategory);
            textViewQuantity = itemView.findViewById(R.id.textViewQuantity);
            buttonDelete     = itemView.findViewById(R.id.buttonDelete);
        }
    }
}
