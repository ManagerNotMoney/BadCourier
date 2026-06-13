package io.github.managernotmoney;

import org.bukkit.Bukkit;
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
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.HashMap;
import java.util.List;

public class StashListener implements Listener {

    private final StashManager stashManager;
    private final NamespacedKey orderKey;
    private final NamespacedKey OFFER_INDEX_KEY;
    private final StashCommand stashCommand;

    public StashListener(StashManager stashManager, StashCommand stashCommand) {
        this.stashManager = stashManager;
        this.stashCommand = stashCommand;
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
        Location loc = block.getLocation();
        StashManager.Order order = stashManager.getOrder(loc);
        Player player = event.getPlayer();
        if (order == null) {
            stashManager.cancelOrderByLocation(loc);
            return;
        }
        if (player.getUniqueId().equals(order.getPlayerId())) {
            event.setCancelled(true);
            stashManager.cancelOrder(player);
        } else {
            event.setCancelled(false);
            stashManager.cancelOrderByLocation(loc);
            stashManager.addRating(order.getPlayerId(), -10.0);
            Player owner = Bukkit.getPlayer(order.getPlayerId());
            if (owner != null && owner.isOnline()) {
                owner.sendMessage("§cВашу закладку разрушили! Вы потеряли 10 очков рейтинга.");
            }
        }
    }

    @EventHandler
    public void onGuiClose(InventoryCloseEvent event) {
        String title = event.getView().getTitle();
        if (title.equals("Выберите заказ") || title.equals("Выберите премиум заказ")) {
            Player player = (Player) event.getPlayer();
            stashManager.removeGuiOffers(player.getUniqueId());
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        stashManager.removeGuiOffers(event.getPlayer().getUniqueId());
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
        StashManager.Order order = stashManager.getOrder(loc);
        if (order == null || !player.getUniqueId().equals(order.getPlayerId())) return;
        if (order.getTemplate().isShopOrder()) {
            for (ItemStack item : inv.getContents()) {
                if (item != null && item.getType() != Material.AIR) {
                    player.getInventory().addItem(item).forEach((slot, overflow) ->
                            player.getWorld().dropItem(player.getLocation(), overflow));
                }
            }
            inv.clear();
            stashManager.finishShopOrder(order, player);
            player.sendMessage("§aВы забрали товар из магазинной закладки!");
            return;
        }
        stashManager.tryCompleteOrder(player, loc, inv);
    }

    @EventHandler
    public void onInventoryOpen(InventoryOpenEvent event) {
        Inventory inv = event.getInventory();
        Location loc = inv.getLocation();
        if (loc == null) return;

        StashManager.Order order = stashManager.getOrder(loc);
        if (order == null) return;
        if (!order.getTemplate().isShopOrder()) return;

        if (order.isShopProductPlaced()) return;

        ItemStack product = order.getShopProduct();
        if (product != null) {
            HashMap<Integer, ItemStack> leftover = inv.addItem(product);
            if (!leftover.isEmpty()) {
                Location dropLoc = order.getBarrelLocation().clone().add(0.5, 0.5, 0.5);
                for (ItemStack drop : leftover.values()) {
                    dropLoc.getWorld().dropItemNaturally(dropLoc, drop);
                }
                ((Player) event.getPlayer()).sendMessage("§eБочка заполнена, остаток выпал рядом.");
            }
            order.markShopProductPlaced();
            ((Player) event.getPlayer()).sendMessage("§aТовар в бочке!");
        }
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        Inventory inv = event.getInventory();
        String title = event.getView().getTitle();
        if (title.equals("Выберите заказ")) {
            event.setCancelled(true);
            ItemStack clicked = event.getCurrentItem();
            if (clicked == null || !clicked.hasItemMeta()) return;
            if (event.getSlot() == 0 && clicked.hasItemMeta()) {
                ItemMeta meta = clicked.getItemMeta();
                if (meta.getPersistentDataContainer().has(
                        new NamespacedKey(stashManager.getPlugin(), "bell"), PersistentDataType.BYTE)) {
                    Player player = (Player) event.getWhoClicked();
                    boolean newStatus = stashManager.toggleNotificationSubscription(player.getUniqueId());
                    List<String> lore = meta.getLore();
                    if (lore != null && lore.size() >= 2) {
                        lore.set(0, newStatus ? "§aВы подписаны" : "§7Вы не подписаны");
                        meta.setLore(lore);
                        clicked.setItemMeta(meta);
                    }
                    return;
                }
            }
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
        } else if (title.equals("Выберите тип заказа")) {
            event.setCancelled(true);
            ItemStack clicked = event.getCurrentItem();
            if (clicked == null || !clicked.hasItemMeta()) return;
            ItemMeta meta = clicked.getItemMeta();
            String type = meta.getPersistentDataContainer().get(
                    new NamespacedKey(stashManager.getPlugin(), "order_type"), PersistentDataType.STRING);
            if (type == null) return;
            Player player = (Player) event.getWhoClicked();
            if (type.equals("normal")) {
                player.closeInventory();
                stashCommand.openOfferGUI(player);
            } else if (type.equals("premium")) {
                player.closeInventory();
                stashCommand.openPremiumOrders(player);
            }
        } else if (title.equals("Выберите премиум заказ")) {
            event.setCancelled(true);
            ItemStack clicked = event.getCurrentItem();
            if (clicked == null || !clicked.hasItemMeta()) return;
            ItemMeta meta = clicked.getItemMeta();
            Integer index = meta.getPersistentDataContainer().get(OFFER_INDEX_KEY, PersistentDataType.INTEGER);
            if (index == null) return;
            Player player = (Player) event.getWhoClicked();
            StashManager.Order order = stashManager.acceptPremiumOffer(player, index);
            if (order != null) {
                player.closeInventory();
            } else {
                player.sendMessage("§cНе удалось принять заказ. Возможно, у вас уже есть активный заказ.");
                player.closeInventory();
            }
        } else if (title.equals("Магазин")) {
            event.setCancelled(true);
            ItemStack clicked = event.getCurrentItem();
            if (clicked == null) return;
            Player player = (Player) event.getWhoClicked();
            stashCommand.handleShopClick(player, clicked);
        }
    }
}
