package io.github.managernotmoney;

import io.github.potaseval.GreatWeeb;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.Container;
import org.bukkit.block.TileState;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

public class StashManager {

    private final JavaPlugin plugin;
    private final GreatWeeb corePlugin;
    private final NamespacedKey orderKey;
    private final Map<Location, Order> ordersByLocation;
    private final Map<UUID, Order> ordersByPlayer;
    private final Map<UUID, BukkitTask> orderTimers;
    private List<OrderTemplate> currentOffers;
    private BukkitTask refreshTask;

    private int maxDistance = 1000;
    private int lifetimeMinutes = 6;

    public StashManager(JavaPlugin plugin, GreatWeeb corePlugin) {
        this.plugin = plugin;
        this.corePlugin = corePlugin;
        this.orderKey = new NamespacedKey(plugin, "stash_order");
        this.ordersByLocation = new HashMap<>();
        this.ordersByPlayer = new HashMap<>();
        this.orderTimers = new HashMap<>();

        plugin.saveDefaultConfig();
        maxDistance = plugin.getConfig().getInt("radius", 1000);
        lifetimeMinutes = plugin.getConfig().getInt("lifetime-minutes", 6);

        refreshOffers();
        refreshTask = new BukkitRunnable() {
            @Override
            public void run() {
                refreshOffers();
            }
        }.runTaskTimer(plugin, 20 * 60 * 20L, 20 * 60 * 20L);
    }
    private void refreshOffers() {
        List<OrderTemplate> newOffers = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            newOffers.add(generateRandomOrder());
        }
        currentOffers = newOffers;
    }
    public List<OrderTemplate> getCurrentOffers() {
        return currentOffers;
    }
    public Order acceptGlobalOffer(Player player, int index) {
        if (currentOffers == null || index < 0 || index >= currentOffers.size()) return null;
        OrderTemplate chosen = currentOffers.get(index);
        currentOffers.remove(index);
        return createOrder(player, chosen);
    }
    private void returnOfferToPool(OrderTemplate template) {
        if (template != null && currentOffers != null) {
            currentOffers.add(template);
        }
    }
    private Order createOrder(Player player, OrderTemplate template) {
        if (ordersByPlayer.containsKey(player.getUniqueId())) {
            player.sendMessage("§cУ вас уже есть активный заказ. Сначала отмените его.");
            return null;
        }

        Location playerLoc = player.getLocation();
        World world = player.getWorld();
        Location barrelLoc = findSafeLocation(world, playerLoc);
        if (barrelLoc == null) {
            player.sendMessage("§cНе удалось найти подходящее место для бочки.");
            return null;
        }

        Block block = barrelLoc.getBlock();
        Material originalMaterial = block.getType();
        block.setType(Material.BARREL);

        if (!(block.getState() instanceof TileState tileState)) {
            player.sendMessage("§cОшибка размещения бочки. Попробуйте снова.");
            block.setType(originalMaterial);
            return null;
        }

        Order order = new Order(
                UUID.randomUUID().toString(),
                player.getUniqueId(),
                barrelLoc,
                template,
                System.currentTimeMillis(),
                originalMaterial
        );

        scheduleExpiry(order);

        tileState.getPersistentDataContainer().set(orderKey, PersistentDataType.STRING, order.getId());
        tileState.update();

        ordersByLocation.put(barrelLoc, order);
        ordersByPlayer.put(player.getUniqueId(), order);

        player.sendMessage("§aНовый заказ: §e" + template.getDescription());
        player.sendMessage("§aБочка спрятана где-то в радиусе " + maxDistance + " блоков.");
        player.sendMessage("§aКоординаты: §eX:" + barrelLoc.getBlockX() + " Z:" + barrelLoc.getBlockZ());

        return order;
    }

    // Оставшееся время в секундах (для /bc status)
    public long getRemainingTime(Player player) {
        Order order = ordersByPlayer.get(player.getUniqueId());
        if (order == null) return -1;
        long elapsed = System.currentTimeMillis() - order.getCreatedAt();
        long total = lifetimeMinutes * 60_000L;
        long remaining = total - elapsed;
        return Math.max(0, remaining / 1000);
    }

    private void scheduleExpiry(Order order) {
        UUID playerId = order.getPlayerId();
        BukkitTask task = new BukkitRunnable() {
            @Override
            public void run() {
                if (ordersByPlayer.containsKey(playerId)) {
                    cancelOrderByPlayerId(playerId);
                }
            }
        }.runTaskLater(plugin, 6 * 60 * 20L); // 6 минут
        orderTimers.put(playerId, task);
    }

    private void cancelTimer(UUID playerId) {
        BukkitTask task = orderTimers.remove(playerId);
        if (task != null) {
            task.cancel();
        }
    }

    public void cancelOrderByLocation(Location loc) {
        Order order = ordersByLocation.get(loc);
        if (order == null) return;

        UUID playerId = order.getPlayerId();
        cancelTimer(playerId);
        ordersByLocation.remove(loc);
        ordersByPlayer.remove(playerId);

        Block block = loc.getBlock();
        if (block.getType() == Material.BARREL && block.getState() instanceof Container container) {
            dropAllItems(loc, container.getInventory());
        }
        if (block.getType() == Material.BARREL) {
            block.setType(order.getOriginalMaterial());
        }

        returnOfferToPool(order.getTemplate());

        Player player = Bukkit.getPlayer(playerId);
        if (player != null && player.isOnline()) {
            player.sendMessage("§eВаш заказ отменён (бочка разрушена).");
        }
    }

    private void cancelOrderByPlayerId(UUID playerId) {
        Order order = ordersByPlayer.remove(playerId);
        if (order == null) return;

        cancelTimer(playerId);
        ordersByLocation.remove(order.getBarrelLocation());

        Block block = order.getBarrelLocation().getBlock();
        if (block.getType() == Material.BARREL && block.getState() instanceof Container container) {
            dropAllItems(order.getBarrelLocation(), container.getInventory());
        }
        if (block.getType() == Material.BARREL) {
            block.setType(order.getOriginalMaterial());
        }

        returnOfferToPool(order.getTemplate());

        Player player = Bukkit.getPlayer(playerId);
        if (player != null && player.isOnline()) {
            player.sendMessage("§eВремя вашего заказа истекло. Заказ отменён.");
        }
    }

    public boolean tryCompleteOrder(Player player, Location barrelLocation, Inventory inv) {
        Order order = ordersByLocation.get(barrelLocation);
        if (order == null) return false;
        if (!player.getUniqueId().equals(order.getPlayerId())) return false;

        OrderTemplate template = order.getTemplate();
        for (int i = 0; i < inv.getSize(); i++) {
            ItemStack item = inv.getItem(i);
            if (item != null && template.isRequiredItem(item, corePlugin)) {
                int requiredAmount = template.getRequiredItem().getAmount();
                if (item.getAmount() >= requiredAmount) {
                    item.setAmount(item.getAmount() - requiredAmount);
                    if (item.getAmount() == 0) {
                        inv.clear(i);
                    }

                    // Выбрасываем все оставшиеся предметы из инвентаря
                    dropAllItems(barrelLocation, inv);

                    // Выполняем заказ (бочка удаляется, выдаётся награда)
                    completeOrder(order, player);
                    return true;
                }
            }
        }
        return false;
    }

    public boolean cancelOrder(Player player) {
        Order order = ordersByPlayer.remove(player.getUniqueId());
        if (order == null) return false;

        ordersByLocation.remove(order.getBarrelLocation());
        cancelTimer(player.getUniqueId());

        Block block = order.getBarrelLocation().getBlock();
        if (block.getType() == Material.BARREL && block.getState() instanceof Container container) {
            dropAllItems(order.getBarrelLocation(), container.getInventory());
        }
        if (block.getType() == Material.BARREL) {
            block.setType(order.getOriginalMaterial());
        }

        returnOfferToPool(order.getTemplate());   // <-- возвращаем шаблон

        player.sendMessage("§eВаш заказ отменён.");
        return true;
    }

    public Order getActiveOrder(Player player) {
        return ordersByPlayer.get(player.getUniqueId());
    }

    public JavaPlugin getPlugin() {
        return plugin;
    }

    public Order getOrder(Location loc) {
        return ordersByLocation.get(loc);
    }

    public void shutdown() {
        if (refreshTask != null) refreshTask.cancel();
        for (BukkitTask task : orderTimers.values()) {
            task.cancel();
        }
        orderTimers.clear();
        for (Order order : ordersByLocation.values()) {
            Location loc = order.getBarrelLocation();
            Block block = loc.getBlock();
            if (block.getType() == Material.BARREL) {
                if (block.getState() instanceof Container container) {
                    dropAllItems(loc, container.getInventory());
                }
                block.setType(order.getOriginalMaterial());
            }
        }
        ordersByLocation.clear();
        ordersByPlayer.clear();
    }

    // ---------- внутренние методы ----------

    private void completeOrder(Order order, Player player) {
        cancelTimer(order.getPlayerId());
        Location loc = order.getBarrelLocation();
        World world = loc.getWorld();

        // Удаляем бочку и восстанавливаем исходный блок
        loc.getBlock().setType(order.getOriginalMaterial());

        OrderTemplate template = order.getTemplate();

        // Выдаём предметную награду, если есть
        if (template.getReward() != null) {
            world.dropItemNaturally(loc.clone().add(0.5, 0.5, 0.5), template.getReward().clone());
        }

        // Выдаём денежную награду, если есть
        if (template.getMoneyReward() > 0) {
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(),
                    "eco give " + player.getName() + " " + template.getMoneyReward());
        }

        world.playSound(loc, Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);
        player.sendMessage("§aЗаказ выполнен! Вы получили: §e" + template.getRewardDescription());

        ordersByLocation.remove(order.getBarrelLocation());
        ordersByPlayer.remove(order.getPlayerId());
    }

    private void dropAllItems(Location barrelLocation, Inventory inv) {
        Location dropLoc = barrelLocation.clone().add(0.5, 0.5, 0.5);
        World world = dropLoc.getWorld();
        for (int i = 0; i < inv.getSize(); i++) {
            ItemStack item = inv.getItem(i);
            if (item != null && item.getType() != Material.AIR) {
                world.dropItemNaturally(dropLoc, item);
                inv.clear(i);
            }
        }
    }

    private Location findSafeLocation(World world, Location origin) {
        ThreadLocalRandom rand = ThreadLocalRandom.current();
        for (int attempt = 0; attempt < 20; attempt++) {
            int dx = rand.nextInt(-1000, 1001);
            int dz = rand.nextInt(-1000, 1001);
            int x = origin.getBlockX() + dx;
            int z = origin.getBlockZ() + dz;
            int y = world.getHighestBlockYAt(x, z);
            Location candidate = new Location(world, x, y + 1, z);
            Block block = candidate.getBlock();
            Block below = candidate.subtract(0, 1, 0).getBlock();
            if (block.getType() == Material.AIR && below.getType().isSolid()) {
                return candidate;
            }
        }
        return null;
    }

    private OrderTemplate generateRandomOrder() {
        ThreadLocalRandom rand = ThreadLocalRandom.current();
        int itemType = rand.nextInt(6);

        ItemStack required;
        String itemName;
        boolean isBoshka;
        boolean isPack = false;

        switch (itemType) {
            case 0 -> { required = corePlugin.getSativaItems().createBoshka(); itemName = "сативы"; isBoshka = true; }
            case 1 -> { required = corePlugin.getIndicaItems().createBoshka(); itemName = "индики"; isBoshka = true; }
            case 2 -> { required = corePlugin.getSativaItems().createBriquette(); itemName = "сативы"; isBoshka = false; }
            case 3 -> { required = corePlugin.getIndicaItems().createBriquette(); itemName = "индики"; isBoshka = false; }
            case 4 -> { required = corePlugin.getSativaItems().createPack(); itemName = "сативы"; isBoshka = false; isPack = true; }
            case 5 -> { required = corePlugin.getIndicaItems().createPack(); itemName = "индики"; isBoshka = false; isPack = true; }
            default -> throw new IllegalStateException();
        }

        int amount;
        double moneyReward = 0;
        ItemStack reward = null;
        String desc, rewardDesc;

        if (isBoshka) {
            int currencyType = rand.nextInt(2);
            if (currencyType == 0) {  // франки
                amount = rand.nextInt(5, 21);       // 5-20 бошек
                int perPiece = rand.nextInt(2, 5);  // 2-4 франка за штуку (исправлено, было 1-2)
                moneyReward = amount * perPiece;
                desc = amount + " бошек " + itemName + " за " + (int) moneyReward + " франков (по " + perPiece + " за штуку)";
                rewardDesc = (int) moneyReward + " франков";
            } else {                  // алмазы
                amount = rand.nextInt(1, 6);        // 1-5 бошек
                int perPiece = rand.nextInt(1, 3);  // 1-2 алмаза за штуку (исправлено)
                int totalDiamonds = amount * perPiece;
                reward = new ItemStack(Material.DIAMOND, totalDiamonds);
                desc = amount + " бошек " + itemName + " за " + totalDiamonds + " алмазов (по " + perPiece + " за штуку)";
                rewardDesc = totalDiamonds + " алмазов";
            }
        } else if (isPack) {
            // Паки - только франки, цены не трогали
            amount = rand.nextInt(5, 16);          // 5-15 паков
            int perPiece = rand.nextInt(12, 22);  // 12-21 франков за штуку
            moneyReward = amount * perPiece;
            desc = amount + " паков " + itemName + " за " + (int) moneyReward + " франков (по " + perPiece + " за штуку)";
            rewardDesc = (int) moneyReward + " франков";
        } else {
            // Брикеты (алмазы или франки)
            int currencyType = rand.nextInt(2);
            if (currencyType == 0) {  // франки
                amount = rand.nextInt(3, 31);       // 3-30 брикетов
                int perPiece = rand.nextInt(7, 12); // 7-11 франков за штуку
                moneyReward = amount * perPiece;
                desc = amount + " брикетов " + itemName + " за " + (int) moneyReward + " франков (по " + perPiece + " за штуку)";
                rewardDesc = (int) moneyReward + " франков";
            } else {                  // алмазы
                amount = rand.nextInt(1, 4);        // 1-3 брикета
                int perPiece = rand.nextInt(3, 5);  // 3-4 алмаза за штуку (правильно)
                int totalDiamonds = amount * perPiece;
                reward = new ItemStack(Material.DIAMOND, totalDiamonds);
                desc = amount + " брикетов " + itemName + " за " + totalDiamonds + " алмазов (по " + perPiece + " за штуку)";
                rewardDesc = totalDiamonds + " алмазов";
            }
        }
        required.setAmount(amount);
        return new OrderTemplate(required, reward, desc, rewardDesc, moneyReward);
    }

    public static class Order {
        private final String id;
        private final UUID playerId;
        private final Location barrelLocation;
        private final OrderTemplate template;
        private final long createdAt;
        private final Material originalMaterial;

        public Order(String id, UUID playerId, Location barrelLocation, OrderTemplate template,
                     long createdAt, Material originalMaterial) {
            this.id = id;
            this.playerId = playerId;
            this.barrelLocation = barrelLocation.clone();
            this.template = template;
            this.createdAt = createdAt;
            this.originalMaterial = originalMaterial;
        }

        public String getId() {
            return id;
        }

        public UUID getPlayerId() {
            return playerId;
        }

        public Location getBarrelLocation() {
            return barrelLocation.clone();
        }

        public OrderTemplate getTemplate() {
            return template;
        }

        public long getCreatedAt() {
            return createdAt;
        }

        public Material getOriginalMaterial() {
            return originalMaterial;
        }
    }
    public List<OrderTemplate> generateOfferList(int count) {
        List<OrderTemplate> offers = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            offers.add(generateRandomOrder());
        }
        return offers;
    }
    public static class OrderTemplate {
        private final ItemStack requiredItem;
        private final ItemStack reward;
        private final String description;
        private final String rewardDescription;
        private final double moneyReward;

        public OrderTemplate(ItemStack requiredItem, ItemStack reward,
                             String description, String rewardDescription,
                             double moneyReward) {
            this.requiredItem = requiredItem.clone();
            this.reward = reward != null ? reward.clone() : null;
            this.description = description;
            this.rewardDescription = rewardDescription;
            this.moneyReward = moneyReward;
        }

        public String getDescription() {
            return description;
        }

        public String getRewardDescription() {
            return rewardDescription;
        }

        public ItemStack getRequiredItem() {
            return requiredItem.clone();
        }

        public ItemStack getReward() {
            return reward != null ? reward.clone() : null;
        }
        public double getMoneyReward() {
            return moneyReward;
        }

        public boolean isRequiredItem(ItemStack item, GreatWeeb corePlugin) {
            if (item == null || item.getType() == Material.AIR) return false;
            if (corePlugin.getSativaItems().isBoshka(requiredItem) && corePlugin.getSativaItems().isBoshka(item))
                return item.getAmount() >= requiredItem.getAmount();
            if (corePlugin.getIndicaItems().isBoshka(requiredItem) && corePlugin.getIndicaItems().isBoshka(item))
                return item.getAmount() >= requiredItem.getAmount();
            if (corePlugin.getSativaItems().isBriquette(requiredItem) && corePlugin.getSativaItems().isBriquette(item))
                return item.getAmount() >= requiredItem.getAmount();
            if (corePlugin.getIndicaItems().isBriquette(requiredItem) && corePlugin.getIndicaItems().isBriquette(item))
                return item.getAmount() >= requiredItem.getAmount();
            if (corePlugin.getSativaItems().isPack(requiredItem) && corePlugin.getSativaItems().isPack(item))
                return item.getAmount() >= requiredItem.getAmount();
            if (corePlugin.getIndicaItems().isPack(requiredItem) && corePlugin.getIndicaItems().isPack(item))
                return item.getAmount() >= requiredItem.getAmount();
            if (corePlugin.getGashItems().isGash(requiredItem) && corePlugin.getGashItems().isGash(item))
                return item.getAmount() >= requiredItem.getAmount();
            return false;
        }
    }
}