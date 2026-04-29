package io.github.managernotmoney;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public class StashCommand implements CommandExecutor, TabCompleter {

    private final Badmain plugin;
    private final StashManager stashManager;
    private final NamespacedKey OFFER_INDEX_KEY;

    public StashCommand(Badmain plugin, StashManager stashManager) {
        this.plugin = plugin;
        this.stashManager = stashManager;
        this.OFFER_INDEX_KEY = new NamespacedKey(plugin, "offer_index");
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cЭту команду может выполнить только игрок.");
            return true;
        }

        if (!player.hasPermission("badcourier.use")) {
            player.sendMessage("§cУ вас недостаточно прав.");
            return true;
        }

        if (args.length == 0) {
            showHelp(player);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "order" -> openOfferGUI(player);
            case "cancel" -> {
                boolean cancelled = stashManager.cancelOrder(player);
                if (!cancelled) player.sendMessage("§cУ вас нет активного заказа.");
            }
            case "status" -> showStatus(player);
            default -> showHelp(player);
        }
        return true;
    }
    private void openOfferGUI(Player player) {
        if (stashManager.getActiveOrder(player) != null) {
            player.sendMessage("§cУ вас уже есть активный заказ. Сначала отмените его: /bc cancel");
            return;
        }
        List<StashManager.OrderTemplate> offers = stashManager.getCurrentOffers();
        if (offers == null || offers.isEmpty()) {
            player.sendMessage("§cПредложения временно недоступны. Попробуйте позже.");
            return;
        }
        Inventory inv = Bukkit.createInventory(null, 27, "Выберите заказ");
        ItemStack pane = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        for (int i = 0; i < 27; i++) inv.setItem(i, pane);
        int[] slots;
        int size = offers.size();
        switch (size) {
            case 1 -> slots = new int[]{13};
            case 2 -> slots = new int[]{12, 14};
            case 3 -> slots = new int[]{11, 13, 15};
            default -> slots = new int[]{10, 12, 14, 16}; // 4
        }
        for (int i = 0; i < size; i++) {
            StashManager.OrderTemplate offer = offers.get(i);
            ItemStack displayItem = offer.getRequiredItem().clone();
            ItemMeta meta = displayItem.getItemMeta();
            List<String> lore = new ArrayList<>();
            lore.add("§7Требуется: " + offer.getDescription());
            if (offer.getMoneyReward() > 0) {
                lore.add("§7Награда: §6" + (int) offer.getMoneyReward() + " франков");
            } else if (offer.getReward() != null) {
                lore.add("§7Награда: " + offer.getRewardDescription());
            }
            lore.add("§eНажмите, чтобы принять");
            meta.setLore(lore);
            meta.getPersistentDataContainer().set(OFFER_INDEX_KEY, PersistentDataType.INTEGER, i);
            displayItem.setItemMeta(meta);
            inv.setItem(slots[i], displayItem);
        }
        player.openInventory(inv);
    }
    private void showStatus(Player player) {
        StashManager.Order order = stashManager.getActiveOrder(player);
        if (order == null) {
            player.sendMessage("§cУ вас нет активного заказа.");
            return;
        }
        String desc = order.getTemplate().getDescription();
        Location loc = order.getBarrelLocation();
        long remainingSec = stashManager.getRemainingTime(player);
        player.sendMessage("§6--- Статус заказа ---");
        player.sendMessage("§7Требуется: §e" + desc);
        player.sendMessage("§7Координаты: §eX:" + loc.getBlockX() + " Z:" + loc.getBlockZ());
        player.sendMessage("§7Осталось времени: §e" + formatTime(remainingSec));
        player.sendMessage("§7Для отмены: §e/bc cancel");
    }

    private String formatTime(long seconds) {
        if (seconds <= 0) return "0с";
        long mins = seconds / 60;
        long secs = seconds % 60;
        if (mins > 0) return mins + "м " + secs + "с";
        return secs + "с";
    }

    private void showHelp(Player player) {
        player.sendMessage("§e--- BadCourier (закладки) ---");
        player.sendMessage("§7/bc order §8- §7получить новый заказ");
        player.sendMessage("§7/bc cancel §8- §7отменить текущий заказ");
        player.sendMessage("§7/bc status §8- §7показать информацию о заказе");
        player.sendMessage("§7/bc help §8- §7эта справка");
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                      @NotNull String alias, @NotNull String[] args) {
        if (!(sender instanceof Player)) return List.of();
        if (!sender.hasPermission("badcourier.use")) return List.of();

        if (args.length == 1) {
            List<String> options = new ArrayList<>();
            options.add("order");
            options.add("cancel");
            options.add("status");
            options.add("help");
            return filterStarting(options, args[0]);
        }
        return List.of();
    }

    private List<String> filterStarting(List<String> options, String prefix) {
        String lower = prefix.toLowerCase();
        List<String> result = new ArrayList<>();
        for (String opt : options) {
            if (opt.toLowerCase().startsWith(lower)) result.add(opt);
        }
        return result;
    }
}