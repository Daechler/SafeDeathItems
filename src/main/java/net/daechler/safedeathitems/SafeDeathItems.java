package net.daechler.safedeathitems;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class SafeDeathItems extends JavaPlugin implements Listener {

    private final Map<UUID, ItemStack[]> deathInventories = new HashMap<>();
    private final Map<UUID, Long> deathTimestamps = new HashMap<>();

    @Override
    public void onEnable() {
        Bukkit.getConsoleSender().sendMessage(ChatColor.GREEN + getName() + " has been enabled!");
        getServer().getPluginManager().registerEvents(this, this);
    }

    @Override
    public void onDisable() {
        Bukkit.getConsoleSender().sendMessage(ChatColor.RED + getName() + " has been disabled!");
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        // Save the player's inventory and clear the drops
        Player player = event.getEntity();
        deathInventories.put(player.getUniqueId(), player.getInventory().getContents());
        event.getDrops().clear();
        deathTimestamps.put(player.getUniqueId(), System.currentTimeMillis());
    }

    @EventHandler
    public void onPlayerCommand(PlayerCommandPreprocessEvent event) {
        Player player = event.getPlayer();
        String[] args = event.getMessage().substring(1).split(" ");

        if (args[0].equalsIgnoreCase("deathinv")) {
            event.setCancelled(true);
            if (!deathInventories.containsKey(player.getUniqueId()) || !canClaimItems(player)) {
                player.sendMessage(ChatColor.RED + "You cannot claim your inventory yet!");
                return;
            }
            // If a second argument is provided, check if it's a valid player's name and if the player was hit recently
            if (args.length > 1) {
                Player target = Bukkit.getPlayerExact(args[1]);
                if (target == null || !deathTimestamps.containsKey(target.getUniqueId()) || !wasHitRecently(target)) {
                    player.sendMessage(ChatColor.RED + "You cannot claim that player's inventory.");
                    return;
                }
                openInventory(player, deathInventories.get(target.getUniqueId()));
            } else {
                openInventory(player, deathInventories.get(player.getUniqueId()));
            }
        } else if (args[0].equalsIgnoreCase("deathinvall")) {
            event.setCancelled(true);
            if (!deathInventories.containsKey(player.getUniqueId()) || !canClaimItems(player)) {
                player.sendMessage(ChatColor.RED + "You cannot recover your items yet!");
                return;
            }
            recoverAllItems(player);
        }
    }

    private boolean canClaimItems(Player player) {
        // Ensure 15 seconds have passed since death
        long deathTime = deathTimestamps.getOrDefault(player.getUniqueId(), 0L);
        return (System.currentTimeMillis() - deathTime) >= 15000;
    }

    private boolean wasHitRecently(Player player) {
        // Check if the player was hit in the last 60 seconds
        long lastHitTime = deathTimestamps.getOrDefault(player.getUniqueId(), 0L);
        return (System.currentTimeMillis() - lastHitTime) <= 60000;
    }

    private void openInventory(Player player, ItemStack[] contents) {
        Inventory inventory = Bukkit.createInventory(null, 54, "Death Inventory");
        inventory.setContents(contents);
        player.openInventory(inventory);
    }

    private void recoverAllItems(Player player) {
        ItemStack[] contents = deathInventories.get(player.getUniqueId());
        Inventory playerInv = player.getInventory();

        for (ItemStack item : contents) {
            if (item != null) {
                HashMap<Integer, ItemStack> couldNotStore = playerInv.addItem(item);
                if (!couldNotStore.isEmpty()) {
                    // If some items could not be stored, put them back into the death inventory
                    deathInventories.put(player.getUniqueId(), couldNotStore.values().toArray(new ItemStack[0]));
                    player.sendMessage(ChatColor.RED + "Your inventory is full. Some items remain in your death inventory.");
                    break;
                }
            }
        }

        // Clear the death inventory after all items have been recovered
        deathInventories.remove(player.getUniqueId());
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (event.getView().getTitle().equals("Death Inventory")) {
            // Prevent the moving of items not belonging to the player or from a different inventory
            if (event.getClickedInventory() != event.getView().getTopInventory()) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (event.getView().getTitle().equals("Death Inventory")) {
            // Update the saved inventory when the death inventory is closed
            Inventory closedInventory = event.getInventory();
            Player player = (Player) event.getPlayer();
            UUID playerUUID = player.getUniqueId();

            // Clone the inventory contents to prevent reference issues
            ItemStack[] itemsToSave = new ItemStack[closedInventory.getSize()];
            ItemStack[] contents = closedInventory.getContents();
            for (int i = 0; i < contents.length; i++) {
                if (contents[i] != null) {
                    itemsToSave[i] = new ItemStack(contents[i]);
                }
            }

            // Update the inventory for the player
            deathInventories.put(playerUUID, itemsToSave);
        }
    }
}
