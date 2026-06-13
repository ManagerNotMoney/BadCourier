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
import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

public class StashManager {

    private final JavaPlugin plugin;
    private final GreatWeeb corePlugin;
    private final NamespacedKey orderKey;
    private final Map<Location, Order> ordersByLocation;
    private final Map<UUID, Order> ordersByPlayer;
    private final Map<UUID, BukkitTask> orderTimers;
    private BukkitTask refreshTask;
    private final OrderGenerator orderGenerator;
    private final Map<UUID, List<OrderTemplate>> guiOffers = new HashMap<>();
    private final Economy economy;
    private final Set<UUID> notificationSubs = new HashSet<>();
    private final Map<UUID, Double> playerRatings = new HashMap<>();
    private List<OrderTemplate> currentNormalOffers;
    private List<OrderTemplate> currentPremiumOffers;

    private int maxDistance = 1000;
    private int lifetimeMinutes = 6;

    public StashManager(JavaPlugin plugin, GreatWeeb corePlugin, Economy economy) {
        this.plugin = plugin;
        this.corePlugin = corePlugin;
        this.orderKey = new NamespacedKey(plugin, "stash_order");
        this.ordersByLocation = new HashMap<>();
        this.ordersByPlayer = new HashMap<>();
        this.orderTimers = new HashMap<>();
        this.orderGenerator = new OrderGenerator(corePlugin);
        this.economy = economy;
        this.currentNormalOffers = new ArrayList<>();
        this.currentPremiumOffers = new ArrayList<>();

        plugin.saveDefaultConfig();
        if (plugin.getConfig().isConfigurationSection("ratings")) {
            for (String key : plugin.getConfig().getConfigurationSection("ratings").getKeys(false)) {
                try {
                    UUID uuid = UUID.fromString(key);
                    double rating = plugin.getConfig().getDouble("ratings." + key);
                    playerRatings.put(uuid, rating);
                } catch (IllegalArgumentException e) {
                    plugin.getLogger().warning("Invalid UUID in ratings: " + key);
                }
            }
        }
        maxDistance = plugin.getConfig().getInt("radius", 1000);
        lifetimeMinutes = plugin.getConfig().getInt("lifetime-minutes", 6);

        cleanupOrphanedBarrels();
        refreshOffers();
        refreshTask = new BukkitRunnable() {
            @Override
            public void run() {
                refreshOffers();
            }
        }.runTaskTimer(plugin, 20 * 60 * 20L, 20 * 60 * 20L);
    }

    public void setRating(UUID playerId, double value) {
        playerRatings.put(playerId, value);
        plugin.getConfig().set("ratings." + playerId.toString(), value);
        plugin.saveConfig();
    }

    public boolean toggleNotificationSubscription(UUID playerId) {
        if (notificationSubs.contains(playerId)) {
            notificationSubs.remove(playerId);
            return false;
        } else {
            notificationSubs.add(playerId);
            return true;
        }
    }

    public double getRating(Player player) {
        return playerRatings.getOrDefault(player.getUniqueId(), 0.0);
    }

    public void addRating(UUID playerId, double amount) {
        playerRatings.merge(playerId, amount, Double::sum);
        double newRating = playerRatings.get(playerId);
        if (newRating < -50.0) {
            playerRatings.put(playerId, -50.0);
            newRating = -50.0;
        }
        plugin.getConfig().set("ratings." + playerId.toString(), newRating);
        plugin.saveConfig();
    }

    public boolean isSubscribedToNotifications(UUID playerId) {
        return notificationSubs.contains(playerId);
    }

    private void refreshOffers() {
        currentNormalOffers = orderGenerator.generateNormalPool(5);
        currentPremiumOffers = orderGenerator.generatePremiumPool(5);

        for (UUID uuid : notificationSubs) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null && p.isOnline()) {
                p.sendMessage("§aПоявились новые заказы! Используйте /bc order.");
                p.playSound(p.getLocation(), Sound.BLOCK_BELL_USE, 1.0f, 1.0f);
            }
        }
    }

    public void storeGuiOffers(UUID playerId, List<OrderTemplate> offers) {
        guiOffers.put(playerId, new ArrayList<>(offers));
    }

    public void removeGuiOffers(UUID playerId) {
        guiOffers.remove(playerId);
    }

    public List<OrderTemplate> getCurrentOffers() {
        return currentNormalOffers;
    }

    public Order acceptPremiumOffer(Player player, int index) {
        List<OrderTemplate> playerOffers = guiOffers.get(player.getUniqueId());
        if (playerOffers == null || index < 0 || index >= playerOffers.size()) {
            return null;
        }
        OrderTemplate chosen = playerOffers.get(index);
        if (currentPremiumOffers == null || !currentPremiumOffers.contains(chosen)) {
            player.sendMessage("§cЭто предложение уже недоступно.");
            guiOffers.remove(player.getUniqueId());
            return null;
        }
        Order order = createOrder(player, chosen);
        if (order != null) {
            currentPremiumOffers.remove(chosen);
            guiOffers.remove(player.getUniqueId());
        } else {
            player.sendMessage("§cНе удалось создать заказ. Попробуйте позже.");
            guiOffers.remove(player.getUniqueId());
        }
        return order;
    }

    public boolean hasOpenGui(UUID playerId) {
        return guiOffers.containsKey(playerId);
    }

    public List<OrderTemplate> getNormalOffers() {
        return currentNormalOffers;
    }

    public List<OrderTemplate> getPremiumOffers() {
        return currentPremiumOffers;
    }

    public Order acceptGlobalOffer(Player player, int index) {
        List<OrderTemplate> playerOffers = guiOffers.get(player.getUniqueId());
        if (playerOffers == null || index < 0 || index >= playerOffers.size()) {
            return null;
        }
        OrderTemplate chosen = playerOffers.get(index);
        if (currentNormalOffers == null || !currentNormalOffers.contains(chosen)) {
            player.sendMessage("§cЭто предложение уже недоступно.");
            guiOffers.remove(player.getUniqueId());
            return null;
        }
        Order order = createOrder(player, chosen);
        if (order != null) {
            currentNormalOffers.remove(chosen);
            guiOffers.remove(player.getUniqueId());
        } else {
            player.sendMessage("§cНе удалось создать заказ. Попробуйте позже.");
            guiOffers.remove(player.getUniqueId());
        }
        return order;
    }

    public boolean createShopOrder(Player player, ItemStack product, String description) {
        if (ordersByPlayer.containsKey(player.getUniqueId())) {
            player.sendMessage("§cУ вас уже есть активный заказ. Сначала отмените его.");
            return false;
        }
        Location playerLoc = player.getLocation();
        World world = player.getWorld();
        Location barrelLoc = findSafeLocation(world, playerLoc);
        if (barrelLoc == null) {
            player.sendMessage("§cНе удалось найти подходящее место для бочки.");
            return false;
        }
        Block block = barrelLoc.getBlock();
        Material originalMaterial = block.getType();
        block.setType(Material.BARREL);
        if (!(block.getState() instanceof Container container)) {
            player.sendMessage("§cОшибка размещения бочки.");
            block.setType(originalMaterial);
            return false;
        }
        container.update();
        OrderTemplate template = new OrderTemplate(
                new ItemStack(Material.AIR),
                null,
                description,
                "Магазин",
                0.0,
                true
        );
        Order order = new Order(
                UUID.randomUUID().toString(),
                player.getUniqueId(),
                barrelLoc,
                template,
                System.currentTimeMillis(),
                originalMaterial,
                product.clone()
        );
        scheduleExpiry(order);
        if (block.getState() instanceof TileState tileState) {
            tileState.getPersistentDataContainer().set(orderKey, PersistentDataType.STRING, order.getId());
            tileState.update();
        }
        ordersByLocation.put(barrelLoc, order);
        ordersByPlayer.put(player.getUniqueId(), order);
        saveBarrelToConfig(order);
        player.sendMessage("§aВаш заказ создан!");
        player.sendMessage("§aКоординаты: §eX:" + barrelLoc.getBlockX() + " Z:" + barrelLoc.getBlockZ());
        player.sendMessage("§aУ вас есть " + lifetimeMinutes + " минут, чтобы найти её.");
        return true;
    }

    public void finishShopOrder(Order order, Player player) {
        cancelTimer(order.getPlayerId());
        Location loc = order.getBarrelLocation();
        loc.getBlock().setType(order.getOriginalMaterial());
        ordersByLocation.remove(order.getBarrelLocation());
        ordersByPlayer.remove(order.getPlayerId());
        removeBarrelFromConfig(order);
        loc.getWorld().playSound(loc, Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);
    }

    private void returnOfferToPool(OrderTemplate template) {
        if (template.isShopOrder()) return;
        boolean isPremium = corePlugin.getTobaccoItems().isCigaretteBlock(template.getRequiredItem()) || template.isPack();
        if (isPremium) {
            currentPremiumOffers.add(template);
        } else {
            currentNormalOffers.add(template);
        }
        while (currentNormalOffers.size() > 5) currentNormalOffers.remove(0);
        while (currentPremiumOffers.size() > 5) currentPremiumOffers.remove(0);
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
        saveBarrelToConfig(order);

        player.sendMessage("§aНовый заказ: §e" + template.getDescription());
        player.sendMessage("§aБочка спрятана где-то в радиусе " + maxDistance + " блоков.");
        player.sendMessage("§aКоординаты: §eX:" + barrelLoc.getBlockX() + " Z:" + barrelLoc.getBlockZ());

        return order;
    }

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
        }.runTaskLater(plugin, lifetimeMinutes * 60 * 20L);
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
        removeBarrelFromConfig(order);

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
        removeBarrelFromConfig(order);
        if (!order.getTemplate().isShopOrder()) {
            addRating(playerId, -10);
            Player player = Bukkit.getPlayer(playerId);
            if (player != null && player.isOnline()) {
                player.sendMessage("§cВы потеряли 10 очков рейтинга из-за просрочки заказа.");
            }
        }
        Player player = Bukkit.getPlayer(playerId);
        if (player != null && player.isOnline()) {
            if (order.getTemplate().isShopOrder()) {
                player.sendMessage("§eВремя магазинной закладки истекло. Товар выпал.");
            } else {
                player.sendMessage("§eВремя вашего заказа истекло. Заказ отменён.");
            }
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
                    dropAllItems(barrelLocation, inv);
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
        returnOfferToPool(order.getTemplate());
        removeBarrelFromConfig(order);
        if (!order.getTemplate().isShopOrder()) {
            addRating(player.getUniqueId(), -10.0);
            player.sendMessage("§cВы потеряли 10 очков рейтинга.");
        }
        if (order.getTemplate().isShopOrder()) {
            player.sendMessage("§eМагазинная закладка отменена. Деньги не возвращаются.");
        } else {
            player.sendMessage("§eВаш заказ отменён.");
        }

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
        if (plugin.getConfig().isConfigurationSection("active-barrels")) {
            plugin.getConfig().set("active-barrels", null);
            plugin.saveConfig();
        }
    }

    private void completeOrder(Order order, Player player) {
        cancelTimer(order.getPlayerId());
        Location loc = order.getBarrelLocation();
        World world = loc.getWorld();
        loc.getBlock().setType(order.getOriginalMaterial());
        OrderTemplate template = order.getTemplate();
        if (template.getReward() != null) {
            world.dropItemNaturally(loc.clone().add(0.5, 0.5, 0.5), template.getReward().clone());
        }
        double money = template.getMoneyReward();
        double rating = getRating(player);
        double bonus = 0;
        if (money > 0) {
            if (rating >= 100) bonus = 20;
            else if (rating >= 70) bonus = 10;
            else if (rating >= 30) bonus = 5;
            money += bonus;
        }
        if (money > 0 && economy != null) {
            EconomyResponse response = economy.depositPlayer(player, money);
            if (!response.transactionSuccess()) {
                player.sendMessage("§cОшибка выдачи денег: " + response.errorMessage);
                plugin.getLogger().warning("[BadCourier] Ошибка Economy для " + player.getName() + ": " + response.errorMessage);
            }
        } else if (money > 0 && economy == null) {
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(),
                    "eco give " + player.getName() + " " + money);
        }
        double ratingGain = 0;
        if (template.getReward() != null) {
            ratingGain = 0.5;
        } else if (template.getMoneyReward() > 0) {
            ratingGain = template.isPack() ? 1.5 : 1.0;
        }
        if (ratingGain > 0) {
            addRating(player.getUniqueId(), ratingGain);
        }
        world.playSound(loc, Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);
        String rewardMsg = template.getRewardDescription();
        if (bonus > 0) {
            rewardMsg += " (+" + (int)bonus + " за рейтинг)";
        }
        player.sendMessage("§aЗаказ выполнен! Вы получили: §e" + rewardMsg);
        if (ratingGain > 0) {
            player.sendMessage("§aПолучено очков рейтинга: §e" + ratingGain);
        }

        ordersByLocation.remove(order.getBarrelLocation());
        ordersByPlayer.remove(order.getPlayerId());
        removeBarrelFromConfig(order);
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
            int dx = rand.nextInt(-maxDistance, maxDistance + 1);
            int dz = rand.nextInt(-maxDistance, maxDistance + 1);
            int x = origin.getBlockX() + dx;
            int z = origin.getBlockZ() + dz;
            int y = world.getHighestBlockYAt(x, z);
            Location candidate = new Location(world, x, y + 1, z);
            Block block = world.getBlockAt(x, y + 1, z);
            Block below = world.getBlockAt(x, y, z);
            if (block.getType() == Material.AIR && below.getType().isSolid()
                    && !below.isLiquid() && below.getType() != Material.BEDROCK
                    && !isDangerous(below.getType())) {
                return candidate;
            }
        }
        return null;
    }

    private boolean isDangerous(Material type) {
        return type == Material.LAVA || type == Material.FIRE || type == Material.CACTUS
                || type == Material.SWEET_BERRY_BUSH || type == Material.WITHER_ROSE;
    }

    private String locToString(Location loc) {
        return loc.getWorld().getName() + ";" + loc.getBlockX() + ";" + loc.getBlockY() + ";" + loc.getBlockZ();
    }

    private Location locFromString(String str) {
        if (str == null) return null;
        String[] parts = str.split(";");
        if (parts.length != 4) return null;
        World world = Bukkit.getWorld(parts[0]);
        if (world == null) return null;
        try {
            return new Location(world, Integer.parseInt(parts[1]), Integer.parseInt(parts[2]), Integer.parseInt(parts[3]));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private void saveBarrelToConfig(Order order) {
        plugin.getConfig().set("active-barrels." + order.getId(), locToString(order.getBarrelLocation()));
        plugin.saveConfig();
    }

    private void removeBarrelFromConfig(Order order) {
        plugin.getConfig().set("active-barrels." + order.getId(), null);
        plugin.saveConfig();
    }

    public void cleanupOrphanedBarrels() {
        if (plugin.getConfig().isConfigurationSection("active-barrels")) {
            for (String key : plugin.getConfig().getConfigurationSection("active-barrels").getKeys(false)) {
                String locStr = plugin.getConfig().getString("active-barrels." + key);
                Location loc = locFromString(locStr);
                if (loc != null) {
                    Block block = loc.getBlock();
                    if (block.getType() == Material.BARREL) {
                        if (block.getState() instanceof Container container) {
                            dropAllItems(loc, container.getInventory());
                        }
                        block.setType(Material.AIR);
                        plugin.getLogger().info("[BadCourier] Удалена осиротевшая бочка из конфига на " +
                                loc.getBlockX() + ", " + loc.getBlockY() + ", " + loc.getBlockZ());
                    }
                }
            }
            plugin.getConfig().set("active-barrels", null);
            plugin.saveConfig();
        }

        for (World world : Bukkit.getWorlds()) {
            for (org.bukkit.Chunk chunk : world.getLoadedChunks()) {
                for (org.bukkit.block.BlockState state : chunk.getTileEntities()) {
                    if (state instanceof org.bukkit.block.Barrel barrel) {
                        if (barrel.getPersistentDataContainer().has(orderKey, PersistentDataType.STRING)) {
                            Location loc = barrel.getLocation();
                            if (barrel instanceof Container container) {
                                dropAllItems(loc, container.getInventory());
                            }
                            loc.getBlock().setType(Material.AIR);
                            plugin.getLogger().info("[BadCourier] Удалена осиротевшая бочка на " +
                                    loc.getBlockX() + ", " + loc.getBlockY() + ", " + loc.getBlockZ());
                        }
                    }
                }
            }
        }
    }

    public void restockOffers() {
        refreshOffers();
    }

    public static class Order {
        private final String id;
        private final UUID playerId;
        private final Location barrelLocation;
        private final OrderTemplate template;
        private final long createdAt;
        private final Material originalMaterial;
        private final ItemStack shopProduct;
        private boolean shopProductPlaced = false;

        public Order(String id, UUID playerId, Location barrelLocation, OrderTemplate template,
                     long createdAt, Material originalMaterial) {
            this(id, playerId, barrelLocation, template, createdAt, originalMaterial, null);
        }

        public ItemStack getShopProduct() {
            return shopProduct != null ? shopProduct.clone() : null;
        }

        public boolean isShopProductPlaced() {
            return shopProductPlaced;
        }

        public void markShopProductPlaced() {
            this.shopProductPlaced = true;
        }

        public Order(String id, UUID playerId, Location barrelLocation, OrderTemplate template,
                     long createdAt, Material originalMaterial, ItemStack shopProduct) {
            this.id = id;
            this.playerId = playerId;
            this.barrelLocation = barrelLocation.clone();
            this.template = template;
            this.createdAt = createdAt;
            this.originalMaterial = originalMaterial;
            this.shopProduct = shopProduct != null ? shopProduct.clone() : null;
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
        return orderGenerator.generateOfferPool(count);
    }

    public static class OrderTemplate {
        private final ItemStack requiredItem;
        private final ItemStack reward;
        private final String description;
        private final String rewardDescription;
        private final double moneyReward;
        private final boolean shopOrder;
        private final boolean isPack;

        public OrderTemplate(ItemStack requiredItem, ItemStack reward,
                             String description, String rewardDescription,
                             double moneyReward) {
            this(requiredItem, reward, description, rewardDescription, moneyReward, false, false);
        }

        public OrderTemplate(ItemStack requiredItem, ItemStack reward,
                             String description, String rewardDescription,
                             double moneyReward, boolean shopOrder) {
            this(requiredItem, reward, description, rewardDescription, moneyReward, shopOrder, false);
        }

        public OrderTemplate(ItemStack requiredItem, ItemStack reward,
                             String description, String rewardDescription,
                             double moneyReward, boolean shopOrder, boolean isPack) {
            this.requiredItem = requiredItem.clone();
            this.reward = reward != null ? reward.clone() : null;
            this.description = description;
            this.rewardDescription = rewardDescription;
            this.moneyReward = moneyReward;
            this.shopOrder = shopOrder;
            this.isPack = isPack;
        }

        public boolean isShopOrder() {
            return shopOrder;
        }

        public boolean isPack() {
            return isPack;
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
            if (corePlugin.getGashItems().isSpice(requiredItem) && corePlugin.getGashItems().isSpice(item))
                return item.getAmount() >= requiredItem.getAmount();
            if (corePlugin.getGashItems().isSpiceBriquette(requiredItem) && corePlugin.getGashItems().isSpiceBriquette(item))
                return item.getAmount() >= requiredItem.getAmount();
            if (corePlugin.getGashItems().isSpicePack(requiredItem) && corePlugin.getGashItems().isSpicePack(item))
                return item.getAmount() >= requiredItem.getAmount();
            if (corePlugin.getGashItems().isGashBriquette(requiredItem) && corePlugin.getGashItems().isGashBriquette(item))
                return item.getAmount() >= requiredItem.getAmount();
            if (corePlugin.getGashItems().isGashPack(requiredItem) && corePlugin.getGashItems().isGashPack(item))
                return item.getAmount() >= requiredItem.getAmount();
            if (corePlugin.getTobaccoItems().isCigaretteBlock(requiredItem) && corePlugin.getTobaccoItems().isCigaretteBlock(item))
                return item.getAmount() >= requiredItem.getAmount();
            if (corePlugin.getTobaccoItems().isCigarettePack(requiredItem) && corePlugin.getTobaccoItems().isCigarettePack(item))
                return item.getAmount() >= requiredItem.getAmount();
            return false;
        }
    }
}
