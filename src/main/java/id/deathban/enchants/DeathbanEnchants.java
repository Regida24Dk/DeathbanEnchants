package id.deathban.enchants;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;

public class DeathbanEnchants extends JavaPlugin implements Listener {

    private NamespacedKey bookKey;
    private NamespacedKey swordKey;

    @Override
    public void onEnable() {
        bookKey = new NamespacedKey(this, "enchant_book");
        swordKey = new NamespacedKey(this, "arcane_strike");

        saveDefaultConfig();

        getServer().getPluginManager().registerEvents(this, this);

        getLogger().info("DeathbanEnchants aktif!");
    }

    private String color(String text) {
        return ChatColor.translateAlternateColorCodes('&', text);
    }

    private void message(Player player, String text) {
        String prefix = getConfig().getString(
                "settings.prefix",
                "&8[&dDeathbanEnchants&8] &r"
        );

        player.sendMessage(color(prefix + text));
    }

    private boolean isSword(Material material) {
        return switch (material) {
            case WOODEN_SWORD,
                 STONE_SWORD,
                 IRON_SWORD,
                 GOLDEN_SWORD,
                 DIAMOND_SWORD,
                 NETHERITE_SWORD -> true;
            default -> false;
        };
    }

    private ItemStack createStarterBook() {
        ItemStack book = new ItemStack(Material.ENCHANTED_BOOK);
        ItemMeta meta = book.getItemMeta();

        meta.setDisplayName(color("&d&lArcane Strike I"));

        List<String> lore = new ArrayList<>();
        lore.add(color("&7Buku custom enchantment"));
        lore.add(color("&eGunakan /denchants apply starter"));
        lore.add(color("&8ID: starter"));

        meta.setLore(lore);

        meta.getPersistentDataContainer().set(
                bookKey,
                PersistentDataType.STRING,
                "starter"
        );

        book.setItemMeta(meta);

        return book;
    }

    private boolean isStarterBook(ItemStack item) {
        if (item == null || item.getType() != Material.ENCHANTED_BOOK) {
            return false;
        }

        ItemMeta meta = item.getItemMeta();

        if (meta == null) {
            return false;
        }

        String id = meta.getPersistentDataContainer().get(
                bookKey,
                PersistentDataType.STRING
        );

        return "starter".equals(id);
    }

    private boolean hasArcaneStrike(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return false;
        }

        ItemMeta meta = item.getItemMeta();

        return meta != null
                && meta.getPersistentDataContainer().has(
                        swordKey,
                        PersistentDataType.BYTE
                );
    }

    private int findStarterBook(Player player) {
        ItemStack[] contents = player.getInventory().getStorageContents();

        for (int slot = 0; slot < contents.length; slot++) {
            if (isStarterBook(contents[slot])) {
                return slot;
            }
        }

        return -1;
    }

    private void giveStarterBook(Player player) {
        ItemStack book = createStarterBook();

        var leftovers = player.getInventory().addItem(book);

        leftovers.values().forEach(item ->
                player.getWorld().dropItemNaturally(
                        player.getLocation(),
                        item
                )
        );

        message(player, "&aBuku Arcane Strike I berhasil diberikan!");
    }

    private void applyStarter(Player player) {
        ItemStack sword = player.getInventory().getItemInMainHand();

        if (!isSword(sword.getType())) {
            message(player, "&cPegang pedang di tangan utama terlebih dahulu!");
            return;
        }

        if (hasArcaneStrike(sword)) {
            message(player, "&cPedang ini sudah memiliki Arcane Strike!");
            return;
        }

        int bookSlot = findStarterBook(player);

        if (bookSlot == -1) {
            message(player, "&cBuku Arcane Strike tidak ditemukan di inventory!");
            return;
        }

        ItemStack book = player.getInventory().getItem(bookSlot);

        ItemMeta swordMeta = sword.getItemMeta();

        if (swordMeta == null) {
            message(player, "&cPedang tidak dapat diberi enchant!");
            return;
        }

        List<String> lore = swordMeta.hasLore()
                ? new ArrayList<>(swordMeta.getLore())
                : new ArrayList<>();

        lore.add(color("&d✦ Arcane Strike I"));

        swordMeta.setLore(lore);

        swordMeta.getPersistentDataContainer().set(
                swordKey,
                PersistentDataType.BYTE,
                (byte) 1
        );

        // Perubahan dilakukan hanya setelah validasi berhasil.
        sword.setItemMeta(swordMeta);

        if (book.getAmount() > 1) {
            book.setAmount(book.getAmount() - 1);
            player.getInventory().setItem(bookSlot, book);
        } else {
            player.getInventory().setItem(bookSlot, null);
        }

        player.updateInventory();

        message(player, "&aBerhasil memasang &dArcane Strike I&a!");
    }

    @EventHandler
    public void onDamage(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player player)) {
            return;
        }

        ItemStack weapon = player.getInventory().getItemInMainHand();

        if (!isSword(weapon.getType()) || !hasArcaneStrike(weapon)) {
            return;
        }

        double bonus = getConfig().getDouble(
                "settings.bonus-damage",
                2.0
        );

        event.setDamage(event.getDamage() + bonus);
    }

    @Override
    public boolean onCommand(
            CommandSender sender,
            Command command,
            String label,
            String[] args
    ) {
        if (!command.getName().equalsIgnoreCase("denchants")) {
            return false;
        }

        if (!(sender instanceof Player player)) {
            sender.sendMessage("Command ini hanya untuk pemain.");
            return true;
        }

        if (!player.hasPermission("deathbanenchants.admin")) {
            message(player, "&cLu tidak punya permission!");
            return true;
        }

        if (args.length == 0 || args[0].equalsIgnoreCase("help")) {
            message(player, "&e/denchants book starter &7- Mendapatkan buku");
            message(player, "&e/denchants apply starter &7- Memasang enchant");
            return true;
        }

        if (args[0].equalsIgnoreCase("book")
                && args.length == 2
                && args[1].equalsIgnoreCase("starter")) {
            giveStarterBook(player);
            return true;
        }

        if (args[0].equalsIgnoreCase("apply")
                && args.length == 2
                && args[1].equalsIgnoreCase("starter")) {
            applyStarter(player);
            return true;
        }

        message(player, "&cPerintah tidak dikenal. Gunakan /denchants help");
        return true;
    }
}
