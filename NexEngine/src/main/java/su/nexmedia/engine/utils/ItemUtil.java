package su.nexmedia.engine.utils;

import com.destroystokyo.paper.profile.PlayerProfile;
import com.destroystokyo.paper.profile.ProfileProperty;
import com.google.common.base.Splitter;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInput;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.UnaryOperator;
import java.util.zip.GZIPInputStream;
import me.clip.placeholderapi.PlaceholderAPI;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import su.nexmedia.engine.Version;
import su.nexmedia.engine.lang.LangManager;

public class ItemUtil {

    private static final int LEGACY_DATA_VERSION = 3700;

    @NotNull
    public static String getItemName(@NotNull ItemStack item) {
        ItemMeta meta = item.getItemMeta();
        return meta != null && meta.hasDisplayName() ? meta.getDisplayName() : LangManager.getMaterial(item.getType());
    }

    public static void mapMeta(@NotNull ItemStack item, @NotNull Consumer<ItemMeta> function) {
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            function.accept(meta);
            item.setItemMeta(meta);
        }
    }

    @NotNull
    public static List<String> getLore(@NotNull ItemStack item) {
        ItemMeta meta = item.getItemMeta();
        return meta != null && meta.getLore() != null ? meta.getLore() : new ArrayList<>();
    }

    @NotNull
    public static ItemStack createCustomHead(@NotNull String texture) {
        ItemStack item = new ItemStack(Material.PLAYER_HEAD);
        setSkullTexture(item, texture);
        return item;
    }

    public static void setSkullTexture(@NotNull ItemStack item, @NotNull String value) {
        if (item.getType() != Material.PLAYER_HEAD || !(item.getItemMeta() instanceof SkullMeta meta)) {
            return;
        }
        PlayerProfile profile = Bukkit.createProfile(UUID.nameUUIDFromBytes(value.getBytes(StandardCharsets.UTF_8)));
        profile.setProperty(new ProfileProperty("textures", value));
        meta.setPlayerProfile(profile);
        item.setItemMeta(meta);
    }

    @Nullable
    public static String getSkullTexture(@NotNull ItemStack item) {
        if (item.getType() != Material.PLAYER_HEAD || !(item.getItemMeta() instanceof SkullMeta meta)) {
            return null;
        }
        PlayerProfile profile = meta.getPlayerProfile();
        if (profile == null) {
            return null;
        }
        return profile.getProperties().stream().filter(property -> property.getName().equalsIgnoreCase("textures")).map(ProfileProperty::getValue).findFirst().orElse(null);
    }

    @Deprecated
    public static void setPlaceholderAPI(@NotNull Player player, @NotNull ItemStack item) {
        if (EngineUtils.hasPlaceholderAPI()) {
            replace(item, str -> Colorizer.apply(PlaceholderAPI.setPlaceholders(player, str)));
        }
    }

    @Deprecated
    public static void replace(@NotNull ItemStack item, @NotNull UnaryOperator<String> replacer) {
        mapMeta(item, meta -> replace(meta, replacer));
    }

    @Deprecated
    public static void replace(@NotNull ItemMeta meta, @NotNull UnaryOperator<String> replacer) {
        if (meta.hasDisplayName()) {
            meta.setDisplayName(replacer.apply(meta.getDisplayName()));
        }
        List<String> loreHas = meta.getLore();
        if (loreHas != null) {
            String single = replacer.apply(String.join("\n", loreHas));
            meta.setLore(StringUtil.stripEmpty(Splitter.on("\n").splitToList(single)));
        }
    }

    @Deprecated
    public static void replaceLore(@NotNull ItemStack item, @NotNull String placeholder, @NotNull List<String> replacer) {
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            List<String> loreHas = meta.getLore();
            if (loreHas != null) {
                List<String> loreReplaced = new ArrayList<>();
                for (String lineHas : loreHas) {
                    if (lineHas.contains(placeholder)) {
                        replacer.forEach(lineRep -> loreReplaced.add(lineHas.replace(placeholder, lineRep)));
                    } else {
                        loreReplaced.add(lineHas);
                    }
                }
                meta.setLore(StringUtil.stripEmpty(loreReplaced));
                item.setItemMeta(meta);
            }
        }
    }

    @Deprecated
    public static boolean isWeapon(@NotNull ItemStack item) {
        return isSword(item) || isAxe(item) || isTrident(item);
    }

    public static boolean isTool(@NotNull ItemStack item) {
        return isAxe(item) || isHoe(item) || isPickaxe(item) || isShovel(item);
    }

    public static boolean isArmor(@NotNull ItemStack item) {
        return isHelmet(item) || isChestplate(item) || isLeggings(item) || isBoots(item);
    }

    public static boolean isBow(@NotNull ItemStack item) {
        return item.getType() == Material.BOW || item.getType() == Material.CROSSBOW;
    }

    public static boolean isSword(@NotNull ItemStack item) {
        if (Version.isAtLeast(Version.V1_19_R3)) {
            return Tag.ITEMS_SWORDS.isTagged(item.getType());
        }
        Material material = item.getType();
        return material == Material.DIAMOND_SWORD || material == Material.GOLDEN_SWORD || material == Material.IRON_SWORD || material == Material.NETHERITE_SWORD || material == Material.STONE_SWORD || material == Material.WOODEN_SWORD;
    }

    public static boolean isAxe(@NotNull ItemStack item) {
        if (Version.isAtLeast(Version.V1_19_R3)) {
            return Tag.ITEMS_AXES.isTagged(item.getType());
        }
        Material material = item.getType();
        return material == Material.DIAMOND_AXE || material == Material.GOLDEN_AXE || material == Material.IRON_AXE || material == Material.NETHERITE_AXE || material == Material.STONE_AXE || material == Material.WOODEN_AXE;
    }

    public static boolean isTrident(@NotNull ItemStack item) {
        return item.getType() == Material.TRIDENT;
    }

    public static boolean isPickaxe(@NotNull ItemStack item) {
        if (Version.isAtLeast(Version.V1_19_R3)) {
            return Tag.ITEMS_PICKAXES.isTagged(item.getType());
        }
        Material material = item.getType();
        return material == Material.DIAMOND_PICKAXE || material == Material.GOLDEN_PICKAXE || material == Material.IRON_PICKAXE || material == Material.NETHERITE_PICKAXE || material == Material.STONE_PICKAXE || material == Material.WOODEN_PICKAXE;
    }

    public static boolean isShovel(@NotNull ItemStack item) {
        if (Version.isAtLeast(Version.V1_19_R3)) {
            return Tag.ITEMS_SHOVELS.isTagged(item.getType());
        }
        Material material = item.getType();
        return material == Material.DIAMOND_SHOVEL || material == Material.GOLDEN_SHOVEL || material == Material.IRON_SHOVEL || material == Material.NETHERITE_SHOVEL || material == Material.STONE_SHOVEL || material == Material.WOODEN_SHOVEL;
    }

    public static boolean isHoe(@NotNull ItemStack item) {
        if (Version.isAtLeast(Version.V1_19_R3)) {
            return Tag.ITEMS_HOES.isTagged(item.getType());
        }
        Material material = item.getType();
        return material == Material.DIAMOND_HOE || material == Material.GOLDEN_HOE || material == Material.IRON_HOE || material == Material.NETHERITE_HOE || material == Material.STONE_HOE || material == Material.WOODEN_HOE;
    }

    public static boolean isElytra(@NotNull ItemStack item) {
        return item.getType() == Material.ELYTRA;
    }

    public static boolean isFishingRod(@NotNull ItemStack item) {
        return item.getType() == Material.FISHING_ROD;
    }

    public static boolean isHelmet(@NotNull ItemStack item) {
        return getEquipmentSlot(item) == EquipmentSlot.HEAD;
    }

    public static boolean isChestplate(@NotNull ItemStack item) {
        return getEquipmentSlot(item) == EquipmentSlot.CHEST;
    }

    public static boolean isLeggings(@NotNull ItemStack item) {
        return getEquipmentSlot(item) == EquipmentSlot.LEGS;
    }

    public static boolean isBoots(@NotNull ItemStack item) {
        return getEquipmentSlot(item) == EquipmentSlot.FEET;
    }

    @NotNull
    public static EquipmentSlot getEquipmentSlot(@NotNull ItemStack item) {
        Material material = item.getType();
        return material.isItem() ? material.getEquipmentSlot() : EquipmentSlot.HAND;
    }

    @Nullable
    public static String compress(@NotNull ItemStack item) {
        try (GZIPInputStream input = new GZIPInputStream(new ByteArrayInputStream(item.serializeAsBytes()))) {
            return new BigInteger(1, input.readAllBytes()).toString(32);
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to encode item without losing metadata", exception);
        }
    }

    @Nullable
    public static ItemStack decompress(@NotNull String compressed) {
        try {
            byte[] bytes = new BigInteger(compressed, 32).toByteArray();
            Class<?> nbtIo = Class.forName("net.minecraft.nbt.NbtIo");
            Class<?> compoundTag = Class.forName("net.minecraft.nbt.CompoundTag");
            Class<?> accounter = Class.forName("net.minecraft.nbt.NbtAccounter");
            Object budget = accounter.getMethod("create", long.class).invoke(null, 16777216L);
            Object tag = nbtIo.getMethod("read", DataInput.class, accounter).invoke(null, new DataInputStream(new ByteArrayInputStream(bytes)), budget);
            if (tag == null) {
                throw new IllegalArgumentException("Encoded item has no compound tag");
            }
            if (!(boolean) compoundTag.getMethod("contains", String.class).invoke(tag, "DataVersion")) {
                compoundTag.getMethod("putInt", String.class, int.class).invoke(tag, "DataVersion", LEGACY_DATA_VERSION);
            }
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            nbtIo.getMethod("writeCompressed", compoundTag, OutputStream.class).invoke(null, tag, output);
            return ItemStack.deserializeBytes(output.toByteArray());
        } catch (ReflectiveOperationException | RuntimeException exception) {
            throw new IllegalArgumentException("Failed to decode legacy item; refusing to discard its data", exception);
        }
    }

    @NotNull
    public static List<String> compress(@NotNull ItemStack[] items) {
        return compress(Arrays.asList(items));
    }

    @NotNull
    public static List<String> compress(@NotNull List<ItemStack> items) {
        return new ArrayList<>(items.stream().map(ItemUtil::compress).filter(Objects::nonNull).toList());
    }

    public static ItemStack[] decompress(@NotNull List<String> list) {
        List<ItemStack> items = list.stream().map(ItemUtil::decompress).filter(Objects::nonNull).toList();
        return items.toArray(new ItemStack[list.size()]);
    }
}
