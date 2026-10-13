package id.deathban.enchants;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
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
        player.sendMessage(color(
                getConfig().getString("settings.prefix", "&d[DeathbanEnchants] ")
                + text
        ));
    }

    private ItemStack createStarterBook() {
        ItemStack book = new ItemStack(Material.ENCHANTED_BOOK);
        ItemMeta meta = book.getItemMeta();

        if (meta == null) return book;

        meta.setDisplayName(color("&d&lArcane Strike"));

        List<String> lore = new ArrayList<>();
        lore.add(color("&7Buku custom enchantment"));
        lore.add(color("&eTaruh buku ke slot pedang di inventory!"));
        lore.add(color("&8ID: starter"));

        meta.setLore(lore);
        meta.getPersistentDataContainer().set(
                bookKey, PersistentDataType.STRING, "starter"
        );

        book.setItemMeta(meta);
        return book;
    }

    private boolean isSword(Material material) {
        return switch (material) {
            case WOODEN_SWORD, STONE_SWORD, IRON_SWORD,
                 GOLDEN_SWORD, DIAMOND_SWORD, NETHERITE_SWORD -> true;
            default -> false;
        };
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;

        // Hanya inventory pemain, bukan peti atau inventory lain.
        if (event.getClickedInventory() != player.getInventory()) return;

        ItemStack cursor = event.getCursor();
        ItemStack target = event.getCurrentItem();

        if (cursor == null || cursor.getType() != Material.ENCHANTED_BOOK) return;
        if (target == null || !isSword(target.getType())) return;

        ItemMeta bookMeta = cursor.getItemMeta();
        if (bookMeta == null) return;

        String bookId = bookMeta.getPersistentDataContainer().get(
                bookKey, PersistentDataType.STRING
        );

        // Buku biasa tidak bisa digunakan.
        if (!"starter".equals(bookId)) return;

        event.setCancelled(true);

        ItemStack upgradedSword = target.clone();
        ItemMeta swordMeta = upgradedSword.getItemMeta();

        if (swordMeta == null) return;

        if (swordMeta.getPersistentDataContainer().has(
                swordKey, PersistentDataType.BYTE
        )) {
            message(player, "&cPedang ini sudah memiliki Arcane Strike!");
            return;
        }

        swordMeta.getPersistentDataContainer().set(
                swordKey, PersistentDataType.BYTE, (byte) 1
        );

        List<String> lore = swordMeta.hasLore()
                ? new ArrayList<>(swordMeta.getLore())
                : new ArrayList<>();

        lore.add(color("&d✦ Arcane Strike I"));
        swordMeta.setLore(lore);
        upgradedSword.setItemMeta(swordMeta);

        // Kurangi satu buku dari cursor pemain.
        ItemStack remainingBook = cursor.clone();
        remainingBook.setAmount(remainingBook.getAmount() - 1);

        event.setCurrentItem(upgradedSword);
        event.setCursor(
                remainingBook.getAmount() > 0
                        ? remainingBook
                        : new ItemStack(Material.AIR)
        );

        message(player, "&aBerhasil! &dArcane Strike I &atelah masuk ke pedang!");
    }

    @EventHandler
    public void onDamage(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player player)) return;

        ItemStack weapon = player.getInventory().getItemInMainHand();
        if (weapon.getType() == Material.AIR) return;

        ItemMeta meta = weapon.getItemMeta();
        if (meta == null) return;

        if (!meta.getPersistentDataContainer().has(
                swordKey, PersistentDataType.BYTE
        )) return;

        double bonus = getConfig().getDouble("settings.bonus-damage", 2.0);
        event.setDamage(event.getDamage() + bonus);
    }

    @Override
    public boolean onCommand(
            CommandSender sender,
            Command command,
            String label,
            String[] args
    ) {
        if (!command.getName().equalsIgnoreCase("denchants")) return false;

        if (!(sender instanceof Player player)) {
            sender.sendMessage("Command ini hanya untuk pemain.");
            return true;
        }

        if (!player.hasPermission("deathbanenchants.admin")) {
            message(player, "&cLu gak punya permission!");
            return true;
        }

        if (args.length == 0 || args[0].equalsIgnoreCase("help")) {
            message(player, "&e/denchants book starter &7- Dapat buku Arcane Strike");
            return true;
        }

        if (args[0].equalsIgnoreCase("book")
                && args.length >= 2
                && args[1].equalsIgnoreCase("starter")) {
            player.getInventory().addItem(createStarterBook());
            message(player, "&aBuku Arcane Strike diberikan!");
            return true;
        }

        message(player, "&cPerintah tidak dikenal. Gunakan /denchants help");
        return true;
    }
}
