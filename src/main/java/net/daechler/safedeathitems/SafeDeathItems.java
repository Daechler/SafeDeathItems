package net.daechler.safedeathitems;

import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.CommandExecutor;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class SafeDeathItems extends JavaPlugin implements Listener, CommandExecutor {

    private final Map<UUID, ItemStack[]> savedInventories = new HashMap<>();
    private final Map<UUID, Long> lastDamageTimes = new HashMap<>();

    @Override
    public void onEnable() {
        // Register the events and commands
        getServer().getPluginManager().registerEvents(this, this);
        getCommand("deathinv").setExecutor(this);
        getCommand("deathinvall").setExecutor(this);
        // Display enable message
        getServer().getConsoleSender().sendMessage(ChatColor.GREEN + getName() + " has been enabled!");
    }

    @Override
    public void onDisable() {
        // Display disable message
        getServer().getConsoleSender().sendMessage(ChatColor.RED + getName() + " has been disabled!");
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        // Store the player's items in a virtual inventory and clear the dropped items
        Player player = event.getEntity();
        savedInventories.put(player.getUniqueId(), event.getDrops().toArray(new ItemStack[0]));
        lastDamageTimes.put(player.getUniqueId(), System.currentTimeMillis());
        event.getDrops().clear();
    }

    @EventHandler
    public void onPlayerRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        // If the player has items in the virtual inventory, notify them
        if (savedInventories.containsKey(player.getUniqueId())) {
            player.sendMessage(ChatColor.YELLOW + "You have items waiting in your /deathinv.");
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("Only players can use this command.");
            return true;
        }

        Player player = (Player) sender;
        if (cmd.getName().equalsIgnoreCase("deathinv")) {
            // Check if the player has a saved inventory
            UUID targetUUID = args.length > 0 ? getServer().getPlayer(args[0]).getUniqueId() : player.getUniqueId();
            ItemStack[] items = savedInventories.get(targetUUID);

            if (items == null) {
                player.sendMessage(ChatColor.RED + "No items found in your death inventory.");
                return true;
            }

            if (args.length > 0 && !canClaim(player, targetUUID)) {
                player.sendMessage(ChatColor.RED + "You cannot claim this player's inventory.");
                return true;
            }

            // Restore items to the player's inventory or keep in the virtual inv if full
            giveItemsToPlayer(player, items);
            savedInventories.remove(targetUUID);
        } else if (cmd.getName().equalsIgnoreCase("deathinvall")) {
            // Claim all items and armor from the virtual inventory
            UUID playerUUID = player.getUniqueId();
            ItemStack[] items = savedInventories.get(playerUUID);

            if (items == null) {
                player.sendMessage(ChatColor.RED + "No items found in your death inventory.");
                return true;
            }

            // Restore items and apply armor to the player
            giveItemsToPlayer(player, items);
            savedInventories.remove(playerUUID);
        }

        return true;
    }

    private void giveItemsToPlayer(Player player, ItemStack[] items) {
        HashMap<Integer, ItemStack> notStored = player.getInventory().addItem(items);
        if (!notStored.isEmpty()) {
            notStored.values().forEach(item -> player.getWorld().dropItemNaturally(player.getLocation(), item));
        }
    }

    private boolean canClaim(Player claimer, UUID deadPlayerUUID) {
        long lastDamageTime = lastDamageTimes.getOrDefault(deadPlayerUUID, 0L);
        // Allow claim if the claimer is the dead player or if the dead player was damaged in the last minute
        return claimer.getUniqueId().equals(deadPlayerUUID) || (System.currentTimeMillis() - lastDamageTime) <= 60000;
    }
}
