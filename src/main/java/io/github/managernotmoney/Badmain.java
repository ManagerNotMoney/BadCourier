package io.github.managernotmoney;

import io.github.potaseval.GreatWeeb;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.plugin.RegisteredServiceProvider;

public final class Badmain extends JavaPlugin {
    private GreatWeeb corePlugin;
    private StashManager stashManager;
    private Economy economy;

    @Override
    public void onEnable() {
        if (!Bukkit.getPluginManager().isPluginEnabled("GreatWeeb")) {
            getLogger().severe("GreatWeeb не найден или не включён! Дополнение отключается.");
            Bukkit.getPluginManager().disablePlugin(this);
            return;
        }
        corePlugin = (GreatWeeb) Bukkit.getPluginManager().getPlugin("GreatWeeb");
        if (corePlugin == null) {
            getLogger().severe("Не удалось получить экземпляр GreatWeeb.");
            Bukkit.getPluginManager().disablePlugin(this);
            return;
        }

        getLogger().info("BadCourier успешно подключился к GreatWeeb v" +
                corePlugin.getDescription().getVersion());
        if (!setupEconomy()) {
            getLogger().warning("Vault или экономический плагин не найден! Выдача денег будет отключена.");
        }
        stashManager = new StashManager(this, corePlugin, economy);
        stashManager.cleanupOrphanedBarrels();
        StashCommand stashCommand = new StashCommand(this, stashManager);
        getCommand("bc").setExecutor(stashCommand);
        getCommand("bc").setTabCompleter(stashCommand);

        getServer().getPluginManager().registerEvents(new StashListener(stashManager), this);
    }
    private boolean setupEconomy() {
        if (getServer().getPluginManager().getPlugin("Vault") == null) {
            return false;
        }
        RegisteredServiceProvider<Economy> rsp = getServer().getServicesManager().getRegistration(Economy.class);
        if (rsp == null) {
            return false;
        }
        economy = rsp.getProvider();
        return economy != null;
    }
    @Override
    public void onDisable() {
        if (stashManager != null) {
            stashManager.shutdown();
        }
        getLogger().info("BadCourier выгружен.");
    }
}