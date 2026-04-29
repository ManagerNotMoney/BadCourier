package io.github.managernotmoney;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.block.TileState;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

public class StashListener implements Listener {

    private final StashManager stashManager;
    private final NamespacedKey orderKey;
    private final NamespacedKey OFFER_INDEX_KEY;

    public StashListener(StashManager stashManager) {
        this.stashManager = stashManager;
        this.orderKey = new NamespacedKey(stashManager.getPlugin(), "stash_order");
        this.OFFER_INDEX_KEY = new NamespacedKey(stashManager.getPlugin(), "offer_index");
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        if (block.getType() != Material.BARREL) return;
        if (!(block.getState() instanceof TileState tileState)) return;

        String orderId = tileState.getPersistentDataContainer().get(orderKey, PersistentDataType.STRING);
        if (orderId == null) return;

        event.setCancelled(true);
        stashManager.cancelOrderByLocation(block.getLocation());
    }

    @EventHandler
    public void onRightClick(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (event.getHand() != EquipmentSlot.HAND) return;

        Block block = event.getClickedBlock();
        if (block == null || block.getType() != Material.BARREL) return;
        if (!(block.getState() instanceof TileState tileState)) return;

        String orderId = tileState.getPersistentDataContainer().get(orderKey, PersistentDataType.STRING);
        if (orderId == null) return;

        Player player = event.getPlayer();
        Location loc = block.getLocation();
        StashManager.Order order = stashManager.getOrder(loc);
        if (order != null && !player.getUniqueId().equals(order.getPlayerId())) {
            event.setCancelled(true);
            player.sendMessage("§cЭто чужая закладка. Вы не можете её открыть.");
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        Inventory inv = event.getInventory();
        Location loc = inv.getLocation();
        if (loc == null) return;

        Block block = loc.getBlock();
        if (block.getType() != Material.BARREL) return;
        if (!(block.getState() instanceof TileState tileState)) return;

        String orderId = tileState.getPersistentDataContainer().get(orderKey, PersistentDataType.STRING);
        if (orderId == null) return;

        Player player = (Player) event.getPlayer();
        stashManager.tryCompleteOrder(player, loc, inv);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        Inventory inv = event.getInventory();
        if (!event.getView().getTitle().equals("Выберите заказ")) return;

        event.setCancelled(true);
        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || !clicked.hasItemMeta()) return;
        ItemMeta meta = clicked.getItemMeta();
        Integer index = meta.getPersistentDataContainer().get(OFFER_INDEX_KEY, PersistentDataType.INTEGER);
        if (index == null) return;

        Player player = (Player) event.getWhoClicked();
        StashManager.Order order = stashManager.acceptGlobalOffer(player, index);
        if (order != null) {
            player.closeInventory();
        } else {
            player.sendMessage("§cНе удалось принять заказ. Возможно, у вас уже есть активный заказ.");
            player.closeInventory();
        }
    }
}