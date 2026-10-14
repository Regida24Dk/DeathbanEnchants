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

    private static class Selection {
        final int slot;
        final String type;

        Selection(int slot, String type) {
            this.slot = slot;
            this.type = type;
        }
    }

    private final Map<UUID, Selection> selections = new HashMap<>();

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

        if (meta == null) {
            return book;
        }

        meta.setDisplayName(color("&d&lArcane Strike"));

        List<String> lore = new ArrayList<>();
        lore.add(color("&7Buku custom enchantment"));
        lore.add(color("&eKlik buku dan pedang secara bergantian!"));
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

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        // Hanya proses slot inventory milik pemain.
        if (event.getClickedInventory() != player.getInventory()) {
            return;
        }

        // Jangan mengganggu aksi pindah item otomatis.
        if (event.getClick().isShiftClick()
                || event.getClick().isKeyboardClick()
                || event.getClick().isCreativeAction()) {
            return;
        }

        ItemStack clicked = event.getCurrentItem();

        if (clicked == null || clicked.getType().isAir()) {
            return;
        }

        boolean clickedBook = isStarterBook(clicked);
        boolean clickedSword = isSword(clicked.getType());

        // Item lain tidak diproses.
        if (!clickedBook && !clickedSword) {
            return;
        }

        // Cegah klik biasa memindahkan buku atau pedang
        // ketika sistem enchant sedang memilih item.
        event.setCancelled(true);

        int clickedSlot = event.getSlot();

        String clickedType = clickedBook ? "book" : "sword";

        Selection previous = selections.get(player.getUniqueId());

        // Pilihan pertama: bisa buku ATAU pedang.
        if (previous == null) {
            if (clickedSword && hasArcaneStrike(clicked)) {
                message(player, "&cPedang ini sudah memiliki Arcane Strike!");
                return;
            }

            selections.put(
                    player.getUniqueId(),
                    new Selection(clickedSlot, clickedType)
            );

            if (clickedBook) {
                message(player,
                        "&dBuku dipilih! &eSekarang ketuk pedang.");
            } else {
                message(player,
                        "&dPedang dipilih! &eSekarang ketuk buku enchant.");
            }

            return;
        }

        // Klik item sejenis: ganti pilihan ke item yang baru.
        if (previous.type.equals(clickedType)) {
            if (clickedSword && hasArcaneStrike(clicked)) {
                message(player, "&cPedang ini sudah memiliki Arcane Strike!");
                return;
            }

            selections.put(
                    player.getUniqueId(),
                    new Selection(clickedSlot, clickedType)
            );

            message(player, clickedBook
                    ? "&dBuku dipilih! &eSekarang ketuk pedang."
                    : "&dPedang dipilih! &eSekarang ketuk buku enchant.");

            return;
        }

        // Dua item berbeda sudah dipilih.
        // Tentukan posisi buku dan pedang tanpa bergantung
        // pada urutan klik pemain.
        final int bookSlot = previous.type.equals("book")
                ? previous.slot : clickedSlot;

        final int swordSlot = previous.type.equals("sword")
                ? previous.slot : clickedSlot;

        selections.remove(player.getUniqueId());

        getServer().getScheduler().runTask(this, () -> {
            if (!player.isOnline()) {
                return;
            }

            ItemStack book = player.getInventory().getItem(bookSlot);
            ItemStack sword = player.getInventory().getItem(swordSlot);

            // Validasi ulang kedua item sebelum memproses.
            if (!isStarterBook(book)
                    || sword == null
                    || !isSword(sword.getType())) {
                message(player,
                        "&cGagal! Buku atau pedang sudah berubah.");
                return;
            }

            if (hasArcaneStrike(sword)) {
                message(player,
                        "&cPedang ini sudah memiliki Arcane Strike!");
                return;
            }

            ItemMeta meta = sword.getItemMeta();

            if (meta == null) {
                message(player, "&cPedang tidak dapat diberi enchant!");
                return;
            }

            // Pasang identitas enchant pada pedang.
            meta.getPersistentDataContainer().set(
                    swordKey,
                    PersistentDataType.BYTE,
                    (byte) 1
            );

            // Tambahkan lore tanpa menghapus lore lama.
            List<String> lore = meta.hasLore()
                    ? new ArrayList<>(meta.getLore())
                    : new ArrayList<>();

            lore.add(color("&d✦ Arcane Strike I"));

            meta.setLore(lore);
            sword.setItemMeta(meta);

            player.getInventory().setItem(swordSlot, sword);

            // Kurangi tepat satu buku setelah berhasil.
            if (book.getAmount() > 1) {
                book.setAmount(book.getAmount() - 1);
                player.getInventory().setItem(bookSlot, book);
            } else {
                player.getInventory().setItem(bookSlot, null);
            }

            player.updateInventory();

            message(player,
                    "&aBerhasil! &dArcane Strike I &atelah terpasang!");
        });
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        selections.remove(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        selections.remove(event.getPlayer().getUniqueId());
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

            Map<Integer, ItemStack> leftover =
                    player.getInventory().addItem(createStarterBook());

            leftover.values().forEach(item ->
                    player.getWorld().dropItemNaturally(
                            player.getLocation(),
                            item
                    )
            );

            message(player, "&aBuku Arcane Strike diberikan!");
            return true;
        }

        message(player, "&cPerintah tidak dikenal. Gunakan /denchants help");
        return true;
    }
            }
