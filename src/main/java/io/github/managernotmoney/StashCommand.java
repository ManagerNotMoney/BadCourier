package io.github.managernotmoney;

import io.github.potaseval.GreatWeeb;
import net.milkbowl.vault.economy.Economy;
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
    private final GreatWeeb corePlugin;
    private final NamespacedKey OFFER_INDEX_KEY;
    private final NamespacedKey SHOP_ITEM_KEY;
    private final NamespacedKey ORDER_TYPE_KEY;
    private final Economy economy;

    public StashCommand(Badmain plugin, StashManager stashManager, Economy economy, GreatWeeb corePlugin) {
        this.plugin = plugin;
        this.stashManager = stashManager;
        this.economy = economy;
        this.corePlugin = corePlugin;
        this.OFFER_INDEX_KEY = new NamespacedKey(plugin, "offer_index");
        this.SHOP_ITEM_KEY = new NamespacedKey(plugin, "shop_item");
        this.ORDER_TYPE_KEY = new NamespacedKey(plugin, "order_type");
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
            case "order" -> openOrderTypeSelection(player);
            case "cancel" -> {
                boolean cancelled = stashManager.cancelOrder(player);
                if (!cancelled) player.sendMessage("§cУ вас нет активного заказа.");
            }
            case "status" -> showStatus(player);
            case "shop" -> openShopGUI(player);
            case "restock" -> {
                if (!player.isOp()) {
                    player.sendMessage("§cУ вас недостаточно прав.");
                    return true;
                }
                stashManager.restockOffers();
                player.sendMessage("§aПредложения обновлены.");
            }
            case "setrating" -> {
                if (!player.isOp()) {
                    player.sendMessage("§cТолько операторы могут устанавливать рейтинг.");
                    return true;
                }
                if (args.length < 3) {
                    player.sendMessage("§cИспользование: /bc setrating <игрок> <значение>");
                    return true;
                }
                Player target = Bukkit.getPlayer(args[1]);
                if (target == null) {
                    player.sendMessage("§cИгрок не найден или не в сети.");
                    return true;
                }
                double value;
                try {
                    value = Double.parseDouble(args[2]);
                } catch (NumberFormatException e) {
                    player.sendMessage("§cНеверное число.");
                    return true;
                }
                stashManager.setRating(target.getUniqueId(), value);
                player.sendMessage("§aРейтинг игрока " + target.getName() + " установлен на " + value);
            }
            default -> showHelp(player);
        }
        return true;
    }

    private void openOrderTypeSelection(Player player) {
        if (stashManager.getActiveOrder(player) != null) {
            player.sendMessage("§cУ вас уже есть активный заказ. Сначала отмените его: /bc cancel");
            return;
        }
        if (stashManager.hasOpenGui(player.getUniqueId())) {
            player.sendMessage("§cУ вас уже открыто меню выбора заказа.");
            return;
        }

        Inventory inv = Bukkit.createInventory(null, 9, "Выберите тип заказа");

        ItemStack normalItem = new ItemStack(Material.BOOK);
        ItemMeta normalMeta = normalItem.getItemMeta();
        normalMeta.setDisplayName("§aОбычные заказы");
        normalMeta.setLore(List.of("§7Доступны всем игрокам"));
        normalMeta.getPersistentDataContainer().set(ORDER_TYPE_KEY, PersistentDataType.STRING, "normal");
        normalItem.setItemMeta(normalMeta);
        inv.setItem(2, normalItem);

        ItemStack premiumItem = new ItemStack(Material.GOLDEN_APPLE);
        ItemMeta premiumMeta = premiumItem.getItemMeta();
        premiumMeta.setDisplayName("§6Премиальные заказы");
        List<String> premiumLore = new ArrayList<>();
        premiumLore.add("§7Требуется рейтинг §c≥ 70");
        premiumLore.add("§7Особые награды");
        premiumMeta.setLore(premiumLore);
        premiumMeta.getPersistentDataContainer().set(ORDER_TYPE_KEY, PersistentDataType.STRING, "premium");
        premiumItem.setItemMeta(premiumMeta);
        inv.setItem(6, premiumItem);

        ItemStack pane = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        for (int i = 0; i < inv.getSize(); i++) {
            if (inv.getItem(i) == null) {
                inv.setItem(i, pane);
            }
        }

        player.openInventory(inv);
    }

    public void openOfferGUI(Player player) {
        if (stashManager.getActiveOrder(player) != null) {
            player.sendMessage("§cУ вас уже есть активный заказ. Сначала отмените его: /bc cancel");
            return;
        }
        if (stashManager.hasOpenGui(player.getUniqueId())) {
            player.sendMessage("§cУ вас уже открыто меню выбора заказа.");
            return;
        }

        List<StashManager.OrderTemplate> offers = stashManager.getCurrentOffers();
        if (offers == null || offers.isEmpty()) {
            player.sendMessage("§cПредложения временно недоступны. Попробуйте позже.");
            return;
        }

        Inventory inv = Bukkit.createInventory(null, 27, "Выберите заказ");

        ItemStack bell = new ItemStack(Material.BELL);
        ItemMeta bellMeta = bell.getItemMeta();
        bellMeta.setDisplayName("§eУведомления о рестоке");
        boolean subscribed = stashManager.isSubscribedToNotifications(player.getUniqueId());
        List<String> bellLore = new ArrayList<>();
        bellLore.add(subscribed ? "§aВы подписаны" : "§7Вы не подписаны");
        bellLore.add("§eНажмите, чтобы переключить");
        bellMeta.setLore(bellLore);
        bellMeta.getPersistentDataContainer().set(
                new NamespacedKey(plugin, "bell"), PersistentDataType.BYTE, (byte)1);
        bell.setItemMeta(bellMeta);
        inv.setItem(0, bell);

        ItemStack star = new ItemStack(Material.NETHER_STAR);
        ItemMeta starMeta = star.getItemMeta();
        starMeta.setDisplayName("§d★ Рейтинг: " + String.format("%.1f", stashManager.getRating(player)));
        List<String> starLore = new ArrayList<>();
        starLore.add("§7За денежный заказ: §a+1.0");
        starLore.add("§7За заказ с алмазами: §a+0.5");
        starLore.add("§7За заказ с паками: §a+1.5");
        starLore.add("§7За отмену или просрочку: §c-10");
        starLore.add("");
        starLore.add("§6Бонус к деньгам от рейтинга:");
        starLore.add("§6  §a30+  §7→ §e+5 франков");
        starLore.add("§6  §a70+  §7→ §e+10 франков");
        starLore.add("§6  §a100+ §7→ §e+20 франков");
        starMeta.setLore(starLore);
        star.setItemMeta(starMeta);
        inv.setItem(8, star);

        ItemStack pane = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        for (int i = 0; i < 27; i++) {
            if (i == 0 || i == 8) continue;
            inv.setItem(i, pane);
        }

        int[] offerSlots = {11, 12, 13, 14, 15};
        for (int i = 0; i < offerSlots.length; i++) {
            if (i < offers.size()) {
                StashManager.OrderTemplate offer = offers.get(i);
                ItemStack displayItem = offer.getRequiredItem().clone();
                ItemMeta meta = displayItem.getItemMeta();
                List<String> lore = new ArrayList<>();
                lore.add("§7Требуется: " + offer.getDescription());
                if (offer.getMoneyReward() > 0) {
                    lore.add("§7Награда: §6" + String.format("%.2f", offer.getMoneyReward()) + " франков");
                } else if (offer.getReward() != null) {
                    lore.add("§7Награда: " + offer.getRewardDescription());
                }
                lore.add("§eНажмите, чтобы принять");
                meta.setLore(lore);
                meta.getPersistentDataContainer().set(OFFER_INDEX_KEY, PersistentDataType.INTEGER, i);
                displayItem.setItemMeta(meta);
                inv.setItem(offerSlots[i], displayItem);
            }
        }
        stashManager.storeGuiOffers(player.getUniqueId(), new ArrayList<>(offers));
        player.openInventory(inv);
    }

    public void openPremiumOrders(Player player) {
        double rating = stashManager.getRating(player);
        if (rating < 70) {
            player.sendMessage("§cНедостаточный рейтинг! Требуется: 70+");
            return;
        }
        if (stashManager.getActiveOrder(player) != null) {
            player.sendMessage("§cУ вас уже есть активный заказ. Сначала отмените его: /bc cancel");
            return;
        }
        List<StashManager.OrderTemplate> offers = stashManager.getPremiumOffers();
        if (offers == null || offers.isEmpty()) {
            player.sendMessage("§cПредложения временно недоступны. Попробуйте позже.");
            return;
        }

        Inventory inv = Bukkit.createInventory(null, 27, "Выберите премиум заказ");

        ItemStack star = new ItemStack(Material.NETHER_STAR);
        ItemMeta starMeta = star.getItemMeta();
        starMeta.setDisplayName("§d★ Рейтинг: " + String.format("%.1f", stashManager.getRating(player)));
        List<String> starLore = new ArrayList<>();
        starLore.add("§7За денежный заказ: §a+1.0");
        starLore.add("§7За заказ с алмазами: §a+0.5");
        starLore.add("§7За заказ с паками: §a+1.5");
        starLore.add("§7За отмену или просрочку: §c-10");
        starLore.add("");
        starLore.add("§6Бонус к деньгам от рейтинга:");
        starLore.add("§6  §a30+  §7→ §e+5 франков");
        starLore.add("§6  §a70+  §7→ §e+10 франков");
        starLore.add("§6  §a100+ §7→ §e+20 франков");
        starMeta.setLore(starLore);
        star.setItemMeta(starMeta);
        inv.setItem(8, star);

        ItemStack pane = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        for (int i = 0; i < 27; i++) {
            if (i == 8) continue;
            inv.setItem(i, pane);
        }

        int[] offerSlots = {11, 12, 13, 14, 15};
        for (int i = 0; i < offerSlots.length; i++) {
            if (i < offers.size()) {
                StashManager.OrderTemplate offer = offers.get(i);
                ItemStack displayItem = offer.getRequiredItem().clone();
                ItemMeta meta = displayItem.getItemMeta();
                List<String> lore = new ArrayList<>();
                lore.add("§7Требуется: " + offer.getDescription());
                if (offer.getMoneyReward() > 0) {
                    lore.add("§7Награда: §6" + String.format("%.2f", offer.getMoneyReward()) + " франков");
                } else if (offer.getReward() != null) {
                    lore.add("§7Награда: " + offer.getRewardDescription());
                }
                lore.add("§eНажмите, чтобы принять");
                meta.setLore(lore);
                meta.getPersistentDataContainer().set(OFFER_INDEX_KEY, PersistentDataType.INTEGER, i);
                displayItem.setItemMeta(meta);
                inv.setItem(offerSlots[i], displayItem);
            }
        }
        stashManager.storeGuiOffers(player.getUniqueId(), new ArrayList<>(offers));
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
        player.sendMessage("§7/bc shop §8- §7магазин товаров");
        player.sendMessage("§7/bc restock §8- §7обновить список предложений (админ)");
        player.sendMessage("§7/bc help §8- §7эта справка");
    }

    private void openShopGUI(Player player) {
        Inventory inv = Bukkit.createInventory(null, 9, "Магазин");

        ItemStack tobaccoSeed = corePlugin.getTobaccoItems().createSeed();
        tobaccoSeed = addShopMeta(tobaccoSeed, 200.0, "tobacco_seed");
        inv.setItem(1, tobaccoSeed);

        ItemStack sativaSeed = corePlugin.getSativaItems().createSeed();
        sativaSeed = addShopMeta(sativaSeed, 250.0, "sativa_seed");
        inv.setItem(2, sativaSeed);

        ItemStack indicaSeed = corePlugin.getIndicaItems().createSeed();
        indicaSeed = addShopMeta(indicaSeed, 250.0, "indica_seed");
        inv.setItem(4, indicaSeed);

        ItemStack fertilizer = corePlugin.getFertilizerItems().createFertilizer();
        fertilizer = addShopMeta(fertilizer, 100.0, "fertilizer");
        inv.setItem(6, fertilizer);

        ItemStack wateringCan = corePlugin.getSekatorItems().createEmptyWateringCan();
        wateringCan = addShopMeta(wateringCan, 150.0, "watering_can");
        inv.setItem(7, wateringCan);

        ItemStack pane = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        for (int i = 0; i < inv.getSize(); i++) {
            if (inv.getItem(i) == null) {
                inv.setItem(i, pane);
            }
        }
        player.openInventory(inv);
    }

    private ItemStack addShopMeta(ItemStack item, double price, String type) {
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            List<String> lore = meta.getLore() != null ? meta.getLore() : new ArrayList<>();
            lore.add("§6Цена: " + String.format("%.2f", price) + " франков");
            lore.add("§eНажмите, чтобы купить");
            meta.getPersistentDataContainer().set(SHOP_ITEM_KEY, PersistentDataType.DOUBLE, price);
            NamespacedKey typeKey = new NamespacedKey(corePlugin, "custom_item_type");
            meta.getPersistentDataContainer().set(typeKey, PersistentDataType.STRING, type);
            meta.setLore(lore);
            item.setItemMeta(meta);
        }
        return item;
    }

    public void handleShopClick(Player player, ItemStack clicked) {
        if (clicked == null || !clicked.hasItemMeta()) return;
        ItemMeta meta = clicked.getItemMeta();
        Double price = meta.getPersistentDataContainer().get(SHOP_ITEM_KEY, PersistentDataType.DOUBLE);
        if (price == null) return;
        NamespacedKey typeKey = new NamespacedKey(corePlugin, "custom_item_type");
        String type = meta.getPersistentDataContainer().get(typeKey, PersistentDataType.STRING);
        if (type == null) {
            player.sendMessage("§cТовар не распознан.");
            return;
        }
        if (economy == null) {
            player.sendMessage("§cЭкономика не доступна.");
            return;
        }
        if (!economy.has(player, price)) {
            player.sendMessage("§cНедостаточно средств. Нужно: " + String.format("%.2f", price) + " франков.");
            return;
        }
        ItemStack product = switch (type) {
            case "sativa_seed" -> corePlugin.getSativaItems().createSeed();
            case "indica_seed" -> corePlugin.getIndicaItems().createSeed();
            case "fertilizer" -> corePlugin.getFertilizerItems().createFertilizer();
            case "tobacco_seed" -> corePlugin.getTobaccoItems().createSeed();
            case "watering_can" -> corePlugin.getSekatorItems().createEmptyWateringCan();
            default -> null;
        };
        if (product == null) {
            player.sendMessage("§cНеизвестный тип товара.");
            return;
        }
        String desc = switch (type) {
            case "sativa_seed" -> "Семена сативы";
            case "indica_seed" -> "Семена индики";
            case "fertilizer" -> "Удобрение";
            case "tobacco_seed" -> "Семена табака";
            case "watering_can" -> "Лейка";
            default -> "Товар";
        };
        boolean created = stashManager.createShopOrder(player, product, desc);
        if (!created) return;
        economy.withdrawPlayer(player, price);
        player.sendMessage("§aПокупка совершена! С баланса снято " + String.format("%.2f", price) + " франков.");
        player.closeInventory();
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                      @NotNull String alias, @NotNull String[] args) {
        if (!(sender instanceof Player)) return List.of();
        if (!sender.hasPermission("badcourier.use")) return List.of();

        if (args.length == 1) {
            List<String> options = new ArrayList<>(List.of("order", "cancel", "status", "shop", "help"));
            if (sender.isOp()) {
                options.add("restock");
                options.add("setrating");
            }
            return filterStarting(options, args[0]);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("setrating")) {
            String prefix = args[1].toLowerCase();
            List<String> names = new ArrayList<>();
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (p.getName().toLowerCase().startsWith(prefix))
                    names.add(p.getName());
            }
            return names;
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