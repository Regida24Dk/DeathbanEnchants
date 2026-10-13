package id.deathban.enchants;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class DeathbanEnchants extends JavaPlugin implements Listener {

    private NamespacedKey bookKey;
    private NamespacedKey swordKey;

    private final Map<UUID, String> selectedBooks = new HashMap<>();

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
                getConfig().getString(
                        "settings.prefix",
                        "&8[&dDeathbanEnchants&8] &r"
                ) + text
        ));
    }

    private ItemStack createStarterBook() {
        ItemStack book = new ItemStack(Material.ENCHANTED_BOOK);
        ItemMeta meta = book.getItemMeta();

        if (meta == null) return book;

        meta.setDisplayName(color("&d&lArcane Strike"));

        List<String> lore = new ArrayList<>();
        lore.add(color("&7Buku custom enchantment"));
        lore.add(color("&eKetuk buku, lalu ketuk pedang!"));
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

    private boolean isStarterBook(ItemStack item) {
        if (item == null || item.getType() != Material.ENCHANTED_BOOK) {
            return false;
        }

        ItemMeta meta = item.getItemMeta();
        if (meta == null) return false;

        String id = meta.getPersistentDataContainer().get(
                bookKey,
                PersistentDataType.STRING
        );

        return "starter".equals(id);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;

        // Hanya menangani klik pada inventory pemain.
        if (event.getClickedInventory() != player.getInventory()) return;

        // Abaikan aksi khusus yang dapat memindahkan item secara otomatis.
        if (event.getClick().isShiftClick()) return;
        if (event.getClick().isKeyboardClick()) return;
        if (event.getClick().isCreativeAction()) return;

        ItemStack clicked = event.getCurrentItem();

        // Tahap pertama: pemain memilih buku.
        if (isStarterBook(clicked)) {
            event.setCancelled(true);

            selectedBooks.put(player.getUniqueId(), "starter");

            message(
                    player,
                    "&dBuku Arcane Strike dipilih! &eSekarang ketuk pedang."
            );
            return;
        }

        // Tahap kedua: pemain memilih pedang.
        String selected = selectedBooks.get(player.getUniqueId());

        if (!"starter".equals(selected)) return;

        if (clicked == null || !isSword(clicked.getType())) {
            return;
        }

        ItemMeta swordMeta = clicked.getItemMeta();

        if (swordMeta == null) return;

        if (swordMeta.getPersistentDataContainer().has(
                swordKey,
                PersistentDataType.BYTE
        )) {
            event.setCancelled(true);
            selectedBooks.remove(player.getUniqueId());

            message(player, "&cPedang ini sudah memiliki Arcane Strike!");
            return;
        }

        // Pastikan buku yang dipilih masih ada di inventory.
        int bookSlot = -1;

        for (int i = 0; i < player.getInventory().getSize(); i++) {
            ItemStack item = player.getInventory().getItem(i);

            if (isStarterBook(item)) {
                bookSlot = i;
                break;
            }
        }

        if (bookSlot == -1) {
            selectedBooks.remove(player.getUniqueId());
            message(player, "&cBuku enchant tidak ditemukan!");
            return;
        }

        event.setCancelled(true);

        final int swordSlot = event.getSlot();
        final int finalBookSlot = bookSlot;

        // Proses setelah event klik selesai untuk mengurangi risiko duplikasi.
        getServer().getScheduler().runTask(this, () -> {
            if (!player.isOnline()) return;

            ItemStack book = player.getInventory().getItem(finalBookSlot);
            ItemStack sword = player.getInventory().getItem(swordSlot);

            if (!isStarterBook(book)
                    || sword == null
                    || !isSword(sword.getType())) {
                selectedBooks.remove(player.getUniqueId());
                return;
            }

            ItemMeta meta = sword.getItemMeta();

            if (meta == null || meta.getPersistentDataContainer().has(
                    swordKey,
                    PersistentDataType.BYTE
            )) {
                selectedBooks.remove(player.getUniqueId());
                message(player, "&cPedang tidak dapat diberi enchant!");
                return;
            }

            // Terapkan enchant pada pedang.
            meta.getPersistentDataContainer().set(
                    swordKey,
                    PersistentDataType.BYTE,
                    (byte) 1
            );

            List<String> lore = meta.hasLore()
                    ? new ArrayList<>(meta.getLore())
                    : new ArrayList<>();

            lore.add(color("&d✦ Arcane Strike I"));

            meta.setLore(lore);
            sword.setItemMeta(meta);

            player.getInventory().setItem(swordSlot, sword);

            // Kurangi satu buku setelah enchant berhasil.
            if (book.getAmount() > 1) {
                book.setAmount(book.getAmount() - 1);
                player.getInventory().setItem(finalBookSlot, book);
            } else {
                player.getInventory().setItem(finalBookSlot, null);
            }

            selectedBooks.remove(player.getUniqueId());

            player.updateInventory();

            message(
                    player,
                    "&aBerhasil! &dArcane Strike I &atelah masuk ke pedang!"
            );
        });
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        selectedBooks.remove(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        selectedBooks.remove(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onDamage(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player player)) return;

        ItemStack weapon = player.getInventory().getItemInMainHand();

        if (!isSword(weapon.getType())) return;

        ItemMeta meta = weapon.getItemMeta();
        if (meta == null) return;

        if (!meta.getPersistentDataContainer().has(
                swordKey,
                PersistentDataType.BYTE
        )) return;

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
            message(player, "&cLu gak punya permission!");
            return true;
        }

        if (args.length == 0 || args[0].equalsIgnoreCase("help")) {
            message(
                    player,
                    "&e/denchants book starter &7- Dapat buku Arcane Strike"
            );
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
