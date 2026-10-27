package net.tavernmc.verification;

import java.io.File;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.util.List;
import java.util.Map;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import su.nexmedia.engine.Version;
import su.nexmedia.engine.api.config.JYML;
import su.nexmedia.engine.utils.ItemUtil;
import su.nightexpress.excellentcrates.ExcellentCratesPlugin;
import su.nightexpress.excellentcrates.crate.CrateManager;
import su.nightexpress.excellentcrates.crate.impl.Crate;
import su.nightexpress.excellentcrates.crate.impl.OpenSettings;
import su.nightexpress.excellentcrates.crate.impl.Reward;

public class CratesStackProbe extends JavaPlugin implements Listener {
    private ExcellentCratesPlugin crates;
    private JavaPlugin booster;
    private Object service;
    private boolean secondBoot;

    @Override
    public void onEnable() {
        getDataFolder().mkdirs();
        secondBoot = new File(getDataFolder(), "first-pass.txt").isFile();
        Bukkit.getPluginManager().registerEvents(this, this);
        Bukkit.getScheduler().runTaskLater(this, () -> safely(this::checkStartup), 10);
    }

    private void checkStartup() throws Exception {
        crates = (ExcellentCratesPlugin) Bukkit.getPluginManager().getPlugin("ExcellentCrates");
        booster = (JavaPlugin) Bukkit.getPluginManager().getPlugin("TavernCrateBooster");
        check("all-three-enabled", crates.isEnabled() && booster.isEnabled()
            && Bukkit.getPluginManager().getPlugin("NexEngine").isEnabled());
        check("version-26.3", Version.getCurrent() == Version.V26_3 && !Version.getCurrent().isDeprecated());
        check("35-existing-crates", crates.getCrateManager().getCrates().size() == 35);
        check("4-existing-rarities", crates.getCrateManager().getRarities().size() == 4);
        int rewards = 0;
        int itemCount = 0;
        for (Crate crate : crates.getCrateManager().getCrates()) {
            for (Reward reward : crate.getRewards()) {
                rewards++;
                verifyItem(reward.getPreview());
                for (ItemStack item : reward.getItems()) {
                    verifyItem(item);
                    itemCount++;
                }
            }
        }
        getLogger().info("ITEM COUNTS: " + rewards + " reward previews; " + itemCount + " reward items");
        check("all-existing-items-roundtrip", rewards > 0);
        ItemStack head = ItemUtil.createCustomHead("eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHBzOi8vdGV4dHVyZXMubWluZWNyYWZ0Lm5ldC90ZXh0dXJlL2FiYyJ9fX0=");
        check("skull-texture-roundtrip", ItemUtil.getSkullTexture(head) != null);
        verifyItem(head);
        Field field = booster.getClass().getDeclaredField("boostService");
        field.setAccessible(true);
        service = field.get(booster);
        if (secondBoot) {
            check("process-restart-resumed-boost", (boolean) call("isActive"));
            check("process-restart-reapplied-weights", crates.getCrateManager().getRarity("rare").getChance() == 15.0);
        }
        getLogger().info("STARTUP READY FOR CLIENT");
    }

    private void verifyItem(ItemStack item) {
        if (item == null) {
            throw new IllegalStateException("Null stored reward item");
        }
        ItemStack restored = ItemUtil.decompress(ItemUtil.compress(item));
        if (!item.isSimilar(restored) || item.getAmount() != restored.getAmount()) {
            throw new IllegalStateException("Item metadata changed during round trip: " + item.getType());
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        if (event.getPlayer().getName().equals("CratesProbe")) {
            Bukkit.getScheduler().runTaskLater(this, () -> safely(() -> checkPlayer(event.getPlayer())), 20);
        }
    }

    private void checkPlayer(Player player) throws Exception {
        check("test-client-online", player.isOnline());
        player.setOp(true);
        player.setInvulnerable(true);
        CrateManager manager = crates.getCrateManager();
        if (secondBoot) {
            check("persisted-synthetic-openings", crates.getUserManager().getUserData(player).getOpeningsAmount("compatibility_probe") == 1);
            Bukkit.dispatchCommand(player, "cb stop");
            check("restart-stop-restored", !(boolean) call("isActive") && manager.getRarity("rare").getChance() == 7.5);
            pass("second");
            return;
        }
        player.getInventory().clear();
        crates.getUserManager().getUserData(player).getOpeningsAmountMap().remove("compatibility_probe");
        Bukkit.dispatchCommand(player, "crateboost start 2 2");
        check("player-command-start", (boolean) call("isActive") && manager.getRarity("rare").getChance() == 15.0);
        Bukkit.dispatchCommand(player, "crateboost start 1 99");
        check("player-command-extend", manager.getRarity("legendary").getChance() == 2.0);
        Crate existing = manager.getCrateById("afk");
        manager.previewCrate(player, existing);
        check("existing-preview-gui", player.getOpenInventory().getTopInventory().getSize() > 9
            && java.util.Arrays.stream(player.getOpenInventory().getTopInventory().getContents()).anyMatch(item -> item != null && !item.isEmpty()));
        player.closeInventory();
        ItemStack item = new ItemStack(Material.DIAMOND, 4);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName("Compatibility reward");
        meta.setCustomModelData(12345);
        meta.getPersistentDataContainer().set(new NamespacedKey(this, "proof"), PersistentDataType.STRING, "retained");
        item.setItemMeta(meta);
        JYML config = new JYML(new File(getDataFolder(), "compatibility_probe.yml"));
        config.set("Name", "Compatibility probe");
        config.set("Rewards.List.proof.Name", "Proof");
        config.set("Rewards.List.proof.Rarity", "rare");
        config.set("Rewards.List.proof.Chance", 1.0);
        config.set("Rewards.List.proof.Preview", ItemUtil.compress(item));
        config.set("Rewards.List.proof.Items", List.of(ItemUtil.compress(item)));
        Crate synthetic = new Crate(crates, config);
        check("synthetic-crate-load", synthetic.load());
        check("real-crate-opening", manager.openCrate(player, synthetic, new OpenSettings().setForce(true)));
        check("actual-reward-delivery", player.getInventory().containsAtLeast(item, 4));
        check("opening-counter-increment", crates.getUserManager().getUserData(player).getOpeningsAmount(synthetic) == 1);
        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "crates reload");
        Bukkit.getScheduler().runTaskLater(this, () -> safely(() -> {
            check("client-online-after-reload", player.isOnline());
            check("reload-keeps-stack-enabled", crates.isEnabled() && booster.isEnabled());
            check("reenforcement-after-crates-reload", crates.getCrateManager().getRarity("rare").getChance() == 15.0);
            Bukkit.dispatchCommand(player, "crateboost stop");
            check("stop-restores-weights", crates.getCrateManager().getRarity("rare").getChance() == 7.5 && crates.getCrateManager().getRarity("legendary").getChance() == 1.0);
            call("startOrExtend", new Class<?>[]{long.class, double.class}, 1L, 2.0);
            Bukkit.getScheduler().runTaskLater(this, () -> safely(() -> {
                check("scheduled-expiry", !(boolean) call("isActive") && crates.getCrateManager().getRarity("rare").getChance() == 7.5);
                Bukkit.dispatchCommand(player, "cb start 5 2");
                check("persist-active-for-restart", new File(booster.getDataFolder(), "data.yml").isFile());
                pass("first");
            }), 35);
        }), 35);
    }

    private Object call(String name) throws Exception {
        return call(name, new Class<?>[0]);
    }

    private Object call(String name, Class<?>[] types, Object... values) throws Exception {
        return service.getClass().getMethod(name, types).invoke(service, values);
    }

    private void check(String label, boolean success) {
        if (!success) {
            throw new IllegalStateException(label);
        }
        getLogger().info("CHECK PASS: " + label);
    }

    private void pass(String phase) throws Exception {
        Files.writeString(new File(getDataFolder(), phase + "-pass.txt").toPath(), "passed");
        getLogger().info("PROBE PASS: " + phase);
        Bukkit.shutdown();
    }

    private void safely(CheckedRunnable runnable) {
        try {
            runnable.run();
        } catch (Throwable failure) {
            getLogger().severe("PROBE FAIL: " + failure);
            failure.printStackTrace();
            Bukkit.shutdown();
        }
    }

    private interface CheckedRunnable {
        void run() throws Exception;
    }
}
