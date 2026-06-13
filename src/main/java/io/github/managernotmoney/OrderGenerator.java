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

    public StashManager.OrderTemplate generateNormalTemplate() {
        ThreadLocalRandom rand = ThreadLocalRandom.current();
        if (rand.nextDouble() < 0.4) {
            ItemStack required = corePlugin.getTobaccoItems().createCigarettePack();
            int amount = rand.nextInt(2, 6);
            double perPiece = randomPrice(4.55, 7.35);
            double moneyReward = amount * perPiece;
            required.setAmount(amount);
            String desc = amount + " пачек сигарет за " + String.format("%.2f", moneyReward)
                    + " франков (по " + String.format("%.2f", perPiece) + " за штуку)";
            String rewardDesc = String.format("%.2f", moneyReward) + " франков";
            return new StashManager.OrderTemplate(required, null, desc, rewardDesc, moneyReward);
        }
        for (int i = 0; i < 100; i++) {
            StashManager.OrderTemplate t = generateRandomTemplate();
            if (!t.isPack() && !corePlugin.getTobaccoItems().isCigaretteBlock(t.getRequiredItem())
                    && !corePlugin.getTobaccoItems().isCigarettePack(t.getRequiredItem())) {
                return t;
            }
        }
        ItemStack required = corePlugin.getSativaItems().createBoshka();
        int amount = 5;
        double moneyReward = 10.0;
        required.setAmount(amount);
        String desc = amount + " бошек сативы за " + String.format("%.2f", moneyReward) + " франков";
        String rewardDesc = String.format("%.2f", moneyReward) + " франков";
        return new StashManager.OrderTemplate(required, null, desc, rewardDesc, moneyReward, false, false);
    }

    public StashManager.OrderTemplate generatePremiumTemplate() {
        ThreadLocalRandom rand = ThreadLocalRandom.current();
        if (rand.nextBoolean()) {
            int itemType = switch (rand.nextInt(4)) {
                case 0 -> 4;
                case 1 -> 5;
                case 2 -> 9;
                case 3 -> 11;
                default -> 4;
            };
            return generatePackTemplate(itemType);
        } else {
            ItemStack required = corePlugin.getTobaccoItems().createCigaretteBlock();
            int amount = rand.nextInt(1, 11);
            double perPiece = randomPrice(60.0, 110.0);
            double moneyReward = amount * perPiece;
            required.setAmount(amount);
            String desc = amount + " блоков сигарет за " + String.format("%.2f", moneyReward)
                    + " франков (по " + String.format("%.2f", perPiece) + " за штуку)";
            String rewardDesc = String.format("%.2f", moneyReward) + " франков";
            return new StashManager.OrderTemplate(required, null, desc, rewardDesc, moneyReward, false, false);
        }
    }

    public StashManager.OrderTemplate generateRandomTemplate() {
        ThreadLocalRandom rand = ThreadLocalRandom.current();
        int itemType = rand.nextInt(12);

        ItemStack required;
        String itemName;
        boolean isBoshka = false;
        boolean isPack = false;

        switch (itemType) {
            case 0 -> {
                required = corePlugin.getSativaItems().createBoshka();
                itemName = "сативы";
                isBoshka = true;
            }
            case 1 -> {
                required = corePlugin.getIndicaItems().createBoshka();
                itemName = "индики";
                isBoshka = true;
            }
            case 2 -> {
                required = corePlugin.getSativaItems().createBriquette();
                itemName = "сативы";
            }
            case 3 -> {
                required = corePlugin.getIndicaItems().createBriquette();
                itemName = "индики";
            }
            case 4 -> {
                required = corePlugin.getSativaItems().createPack();
                itemName = "сативы";
                isPack = true;
            }
            case 5 -> {
                required = corePlugin.getIndicaItems().createPack();
                itemName = "индики";
                isPack = true;
            }
            case 6 -> {
                required = corePlugin.getGashItems().createGash();
                itemName = "гашиша";
                isBoshka = true;
            }
            case 7 -> {
                required = corePlugin.getGashItems().createSpice();
                itemName = "спайса";
                isBoshka = true;
            }
            case 8 -> {
                required = corePlugin.getGashItems().createSpiceBriquette();
                itemName = "спайса";
            }
            case 9 -> {
                required = corePlugin.getGashItems().createSpicePack();
                itemName = "спайса";
                isPack = true;
            }
            case 10 -> {
                required = corePlugin.getGashItems().createGashBriquette();
                itemName = "гашиша";
            }
            case 11 -> {
                required = corePlugin.getGashItems().createGashPack();
                itemName = "гашиша";
                isPack = true;
            }
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
                double perPiece = randomPrice(1.40, 2.25);
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
            double perPiece = randomPrice(35.90, 45.60);
            moneyReward = amount * perPiece;
            desc = amount + " паков " + itemName + " за " + String.format("%.2f", moneyReward) + " франков (по " + String.format("%.2f", perPiece) + " за штуку)";
            rewardDesc = String.format("%.2f", moneyReward) + " франков";
        } else {
            amount = rand.nextInt(1, 36);
            double perPiece = randomPrice(4.50, 6.10);
            moneyReward = amount * perPiece;
            desc = amount + " брикетов " + itemName + " за " + String.format("%.2f", moneyReward) + " франков (по " + String.format("%.2f", perPiece) + " за штуку)";
            rewardDesc = String.format("%.2f", moneyReward) + " франков";
        }
        required.setAmount(amount);
        return new StashManager.OrderTemplate(required, reward, desc, rewardDesc, moneyReward, false, isPack);
    }

    private StashManager.OrderTemplate generatePackTemplate(int itemType) {
        ThreadLocalRandom rand = ThreadLocalRandom.current();
        ItemStack required;
        String itemName;
        switch (itemType) {
            case 4 -> {
                required = corePlugin.getSativaItems().createPack();
                itemName = "сативы";
            }
            case 5 -> {
                required = corePlugin.getIndicaItems().createPack();
                itemName = "индики";
            }
            case 9 -> {
                required = corePlugin.getGashItems().createSpicePack();
                itemName = "спайса";
            }
            case 11 -> {
                required = corePlugin.getGashItems().createGashPack();
                itemName = "гашиша";
            }
            default -> throw new IllegalArgumentException();
        }
        int amount = rand.nextInt(5, 16);
        double perPiece = randomPrice(8.50, 12.60);
        double moneyReward = amount * perPiece;
        required.setAmount(amount);
        String desc = amount + " паков " + itemName + " за " + String.format("%.2f", moneyReward) + " франков (по " + String.format("%.2f", perPiece) + " за штуку)";
        String rewardDesc = String.format("%.2f", moneyReward) + " франков";
        return new StashManager.OrderTemplate(required, null, desc, rewardDesc, moneyReward, false, true);
    }

    public List<StashManager.OrderTemplate> generateNormalPool(int count) {
        List<StashManager.OrderTemplate> pool = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            pool.add(generateNormalTemplate());
        }
        return pool;
    }

    public List<StashManager.OrderTemplate> generatePremiumPool(int count) {
        List<StashManager.OrderTemplate> pool = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            pool.add(generatePremiumTemplate());
        }
        return pool;
    }

    public List<StashManager.OrderTemplate> generateOfferPool(int count) {
        List<StashManager.OrderTemplate> pool = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            pool.add(generateRandomTemplate());
        }
        return pool;
    }
}
