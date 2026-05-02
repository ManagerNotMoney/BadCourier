package io.github.managernotmoney;

import io.github.potaseval.GreatWeeb;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

public class OrderGenerator {

    private final GreatWeeb corePlugin;

    public OrderGenerator(GreatWeeb corePlugin) {
        this.corePlugin = corePlugin;
    }
    private double randomPrice(double min, double max) {
        double value = ThreadLocalRandom.current().nextDouble(min, max);
        return Math.round(value * 100.0) / 100.0;
    }
    public StashManager.OrderTemplate generateRandomTemplate() {
        ThreadLocalRandom rand = ThreadLocalRandom.current();
        int itemType = rand.nextInt(12);

        ItemStack required;
        String itemName;
        boolean isBoshka = false;
        boolean isPack = false;

        switch (itemType) {
            case 0 -> { required = corePlugin.getSativaItems().createBoshka();itemName = "сативы"; isBoshka = true; }
            case 1 -> { required = corePlugin.getIndicaItems().createBoshka();itemName = "индики"; isBoshka = true; }
            case 2 -> { required = corePlugin.getSativaItems().createBriquette();itemName = "сативы"; }
            case 3 -> { required = corePlugin.getIndicaItems().createBriquette();itemName = "индики"; }
            case 4 -> { required = corePlugin.getSativaItems().createPack();itemName = "сативы"; isPack = true; }
            case 5 -> { required = corePlugin.getIndicaItems().createPack();itemName = "индики"; isPack = true; }
            case 6 -> { required = corePlugin.getGashItems().createGash();itemName = "гашиша"; isBoshka = true; }
            case 7 -> { required = corePlugin.getGashItems().createSpice(); itemName = "спайса"; isBoshka = true; }
            case 8 -> { required = corePlugin.getGashItems().createSpiceBriquette();itemName = "спайса"; }
            case 9 -> { required = corePlugin.getGashItems().createSpicePack();itemName = "спайса"; isPack = true; }
            case 10-> { required = corePlugin.getGashItems().createGashBriquette();itemName = "гашиша"; }
            case 11-> { required = corePlugin.getGashItems().createGashPack();itemName = "гашиша"; isPack = true; }
            default -> throw new IllegalStateException();
        }
        int amount;
        double moneyReward = 0;
        ItemStack reward = null;
        String desc, rewardDesc;

        if (itemType == 6) {
            int currencyType = rand.nextInt(2);
            if (currencyType == 0) {
                amount = rand.nextInt(5, 21);
                double perPiece = randomPrice(2.15, 2.90);
                moneyReward = amount * perPiece;
                desc = amount + " гашиша за " + String.format("%.2f", moneyReward) + " франков (по " + String.format("%.2f", perPiece) + " за штуку)";
                rewardDesc = String.format("%.2f", moneyReward) + " франков";
            } else {
                amount = rand.nextInt(1, 6);
                int perPiece = rand.nextInt(1, 3);
                int totalDiamonds = amount * perPiece;
                reward = new ItemStack(Material.DIAMOND, totalDiamonds);
                desc = amount + " гашиша за " + totalDiamonds + " алмазов (по " + perPiece + " за штуку)";
                rewardDesc = totalDiamonds + " алмазов";
            }
        } else if (itemType == 7) {
            int currencyType = rand.nextInt(2);
            if (currencyType == 0) {
                amount = rand.nextInt(5, 21);
                double perPiece = randomPrice(2.70, 3.95);
                moneyReward = amount * perPiece;
                desc = amount + " спайса за " + String.format("%.2f", moneyReward) + " франков (по " + String.format("%.2f", perPiece) + " за штуку)";
                rewardDesc = String.format("%.2f", moneyReward) + " франков";
            } else {
                amount = rand.nextInt(1, 6);
                int perPiece = rand.nextInt(2, 5);
                int totalDiamonds = amount * perPiece;
                reward = new ItemStack(Material.DIAMOND, totalDiamonds);
                desc = amount + " спайса за " + totalDiamonds + " алмазов (по " + perPiece + " за штуку)";
                rewardDesc = totalDiamonds + " алмазов";
            }
        } else if (isBoshka) {
            int currencyType = rand.nextInt(2);
            if (currencyType == 0) {
                amount = rand.nextInt(5, 21);
                double perPiece = randomPrice(1.40, 2.25);;
                moneyReward = amount * perPiece;
                desc = amount + " бошек " + itemName + " за " + String.format("%.2f", moneyReward) + " франков (по " + String.format("%.2f", perPiece) + " за штуку)";
                rewardDesc = String.format("%.2f", moneyReward) + " франков";
            } else {
                amount = rand.nextInt(1, 6);
                int perPiece = rand.nextInt(1, 3);
                int totalDiamonds = amount * perPiece;
                reward = new ItemStack(Material.DIAMOND, totalDiamonds);
                desc = amount + " бошек " + itemName + " за " + totalDiamonds + " алмазов (по " + perPiece + " за штуку)";
                rewardDesc = totalDiamonds + " алмазов";
            }
        } else if (isPack) {
            amount = rand.nextInt(5, 16);
            double perPiece = randomPrice(4.50, 7.40);
            moneyReward = amount * perPiece;
            desc = amount + " паков " + itemName + " за " + String.format("%.2f", moneyReward) + " франков (по " + String.format("%.2f", perPiece) + " за штуку)";
            rewardDesc = String.format("%.2f", moneyReward) + " франков";
        } else {
            int currencyType = rand.nextInt(2);
            if (currencyType == 0) {
                amount = rand.nextInt(3, 31);
                double perPiece = randomPrice(3.11, 4.50);
                moneyReward = amount * perPiece;
                desc = amount + " брикетов " + itemName + " за " + String.format("%.2f", moneyReward) + " франков (по " + String.format("%.2f", perPiece) + " за штуку)";
                rewardDesc = String.format("%.2f", moneyReward) + " франков";
            } else {
                amount = rand.nextInt(1, 4);
                int perPiece = rand.nextInt(3, 5);
                int totalDiamonds = amount * perPiece;
                reward = new ItemStack(Material.DIAMOND, totalDiamonds);
                desc = amount + " брикетов " + itemName + " за " + totalDiamonds + " алмазов (по " + perPiece + " за штуку)";
                rewardDesc = totalDiamonds + " алмазов";
            }
        }
        required.setAmount(amount);
        return new StashManager.OrderTemplate(required, reward, desc, rewardDesc, moneyReward);
    }

    public List<StashManager.OrderTemplate> generateOfferPool(int count) {
        List<StashManager.OrderTemplate> pool = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            pool.add(generateRandomTemplate());
        }
        return pool;
    }
}