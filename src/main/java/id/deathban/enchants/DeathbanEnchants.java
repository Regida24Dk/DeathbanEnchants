package id.deathban.enchants;

import org.bukkit.Bukkit;
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
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.UUID;

public class DeathbanEnchants extends JavaPlugin implements Listener {

    private NamespacedKey bookKey;
    private NamespacedKey swordKey;

    private static final int BOOK_SLOT = 10;
    private static final int APPLY_SLOT = 13;
    private static final int SWORD_SLOT = 16;
    private static final int INFO_SLOT = 4;
    private static final int CLOSE_SLOT = 22;

    private final Map<UUID, Boolean> processing = new HashMap<>();

    private class MenuHolder implements InventoryHolder {

        private Inventory inventory;

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }

    @Override
    public void onEnable() {
        bookKey = new NamespacedKey(this, "enchant_book");
        swordKey = new NamespacedKey(this, "arcane_strike");

        saveDefaultConfig();

        getServer().getPluginManager().registerEvents(this, this);

        getLogger().info("DeathbanEnchants GUI aktif!");
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

    private ItemStack namedItem(
            Material material,
            String name,
            String... loreLines
    ) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();

        if (meta != null) {
            meta.setDisplayName(color(name));

            List<String> lore = new ArrayList<>();

            for (String line : loreLines) {
                lore.add(color(line));
            }

            meta.setLore(lore);
            item.setItemMeta(meta);
        }

        return item;
    }

    private ItemStack createStarterBook() {
        ItemStack book = new ItemStack(Material.ENCHANTED_BOOK);
        ItemMeta meta = book.getItemMeta();

        if (meta == null) {
            return book;
        }

        meta.setDisplayName(color("&d&lArcane Strike I"));

        List<String> lore = new ArrayList<>();
        lore.add(color("&7Buku custom enchantment"));
        lore.add(color("&7Menambahkan bonus damage"));
        lore.add(color("&eMasukkan buku dan pedang ke GUI"));
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
        if (item == null
                || item.getType() != Material.ENCHANTED_BOOK
                || !item.hasItemMeta()) {
            return false;
        }

        ItemMeta meta = item.getItemMeta();

        return meta != null
                && "starter".equals(
                        meta.getPersistentDataContainer().get(
                                bookKey,
                                PersistentDataType.STRING
                        )
                );
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

    private boolean isOurMenu(Inventory inventory) {
        return inventory != null
                && inventory.getHolder() instanceof MenuHolder;
    }

    private void openMenu(Player player) {
        MenuHolder holder = new MenuHolder();

        Inventory inventory = Bukkit.createInventory(
                holder,
                27,
                color("&8Deathban &dEnchants")
        );

        holder.inventory = inventory;

        // Latar menu.
        ItemStack filler = namedItem(
                Material.GRAY_STAINED_GLASS_PANE,
                "&7 "
        );

        for (int i = 0; i < inventory.getSize(); i++) {
            inventory.setItem(i, filler.clone());
        }

        // Slot input dikosongkan.
        inventory.setItem(BOOK_SLOT, null);
        inventory.setItem(SWORD_SLOT, null);

        inventory.setItem(
                4,
                namedItem(
                        Material.ENCHANTED_BOOK,
                        "&d&lARCANE STRIKE I",
                        "&7Tambahan damage pada pedang",
                        "&7Bonus: &e"
                                + getConfig().getDouble(
                                        "settings.bonus-damage",
                                        2.0
                                ),
                        "",
                        "&eMasukkan buku dan pedang"
                )
        );

        inventory.setItem(
                APPLY_SLOT,
                namedItem(
                        Material.ANVIL,
                        "&a&lPASANG ENCHANT",
                        "&7Masukkan buku di slot kiri",
                        "&7dan pedang di slot kanan.",
                        "",
                        "&eKlik untuk memasang enchant!"
                )
        );

        inventory.setItem(
                CLOSE_SLOT,
                namedItem(
                        Material.BARRIER,
                        "&c&lTUTUP MENU",
                        "&7Tutup dan kembalikan item."
                )
        );

        player.openInventory(inventory);
    }

    /*
     * Menangani klik di dalam GUI.
     * Item input hanya dapat dimasukkan ke slot yang sesuai.
     */
    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        Inventory top = event.getView().getTopInventory();

        if (!isOurMenu(top)) {
            return;
        }

        int rawSlot = event.getRawSlot();

        // Cegah pemindahan otomatis dari inventory pemain
        // ke menu, termasuk shift-click.
        if (rawSlot < 0 || rawSlot >= top.getSize()) {
            event.setCancelled(true);
            return;
        }

        event.setCancelled(true);

        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        if (processing.getOrDefault(player.getUniqueId(), false)) {
            return;
        }

        ItemStack cursor = event.getCursor();
        ItemStack current = top.getItem(rawSlot);

        // Tombol informasi.
        if (rawSlot == INFO_SLOT) {
            message(
                    player,
                    "&dArcane Strike I &7memberikan bonus damage."
            );
            return;
        }

        // Tombol tutup.
        if (rawSlot == CLOSE_SLOT) {
            player.closeInventory();
            return;
        }

        // Tombol pasang enchant.
        if (rawSlot == APPLY_SLOT) {
            applyEnchant(player, top);
            return;
        }

        // Hanya slot buku dan pedang yang dapat menerima item.
        if (rawSlot != BOOK_SLOT && rawSlot != SWORD_SLOT) {
            return;
        }

        // Klik kosong dengan cursor kosong: tidak melakukan apa-apa.
        if (cursor == null || cursor.getType().isAir()) {
            if (current == null || current.getType().isAir()) {
                return;
            }

            // Ambil item dari slot GUI.
            player.setItemOnCursor(current.clone());
            top.setItem(rawSlot, null);
            return;
        }

        // Validasi item sesuai slot.
        boolean valid = rawSlot == BOOK_SLOT
                ? isStarterBook(cursor)
                : isSword(cursor.getType());

        if (!valid) {
            message(
                    player,
                    "&cSlot ini hanya menerima "
                            + (rawSlot == BOOK_SLOT
                            ? "buku Arcane Strike!"
                            : "pedang!")
            );
            return;
        }

        if (rawSlot == SWORD_SLOT && hasArcaneStrike(cursor)) {
            message(player, "&cPedang ini sudah memiliki Arcane Strike!");
            return;
        }

        // Masukkan item ke slot kosong.
        if (current == null || current.getType().isAir()) {
            top.setItem(rawSlot, cursor.clone());
            player.setItemOnCursor(null);
            return;
        }

        // Jika slot sudah berisi item, tukar hanya jika
        // item lama cocok dengan slot inventory pemain.
        boolean oldItemValid = rawSlot == BOOK_SLOT
                ? isStarterBook(current)
                : isSword(current.getType());

        if (!oldItemValid) {
            return;
        }

        top.setItem(rawSlot, cursor.clone());
        player.setItemOnCursor(current.clone());
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        if (isOurMenu(event.getView().getTopInventory())) {
            // Drag dibatalkan untuk mencegah item masuk ke slot
            // yang tidak diizinkan. Gunakan klik biasa.
            event.setCancelled(true);
        }
    }

    private void applyEnchant(Player player, Inventory inventory) {
        UUID uuid = player.getUniqueId();

        if (processing.getOrDefault(uuid, false)) {
            return;
        }

        ItemStack book = inventory.getItem(BOOK_SLOT);
        ItemStack sword = inventory.getItem(SWORD_SLOT);

        if (!isStarterBook(book)) {
            message(player, "&cMasukkan buku Arcane Strike ke slot buku!");
            return;
        }

        if (sword == null || !isSword(sword.getType())) {
            message(player, "&cMasukkan pedang ke slot senjata!");
            return;
        }

        if (hasArcaneStrike(sword)) {
            message(player, "&cPedang ini sudah memiliki Arcane Strike!");
            return;
        }

        ItemMeta meta = sword.getItemMeta();

        if (meta == null) {
            message(player, "&cPedang tidak dapat diberi enchant!");
            return;
        }

        processing.put(uuid, true);

        try {
            // Tambahkan identitas enchant ke pedang.
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

            // Simpan pedang yang telah diberi enchant.
            inventory.setItem(SWORD_SLOT, sword);

            // Konsumsi satu buku setelah enchant berhasil.
            if (book.getAmount() > 1) {
                ItemStack remaining = book.clone();
                remaining.setAmount(book.getAmount() - 1);
                inventory.setItem(BOOK_SLOT, remaining);
            } else {
                inventory.setItem(BOOK_SLOT, null);
            }

            player.updateInventory();

            message(
                    player,
                    "&aBerhasil! &dArcane Strike I &atelah terpasang!"
            );

        } finally {
            processing.remove(uuid);
        }
    }

    /*
     * Saat GUI ditutup, kembalikan item input.
     * Jika inventory penuh, item sisanya dijatuhkan
     * di lokasi pemain agar tidak hilang.
     */
    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        Inventory inventory = event.getInventory();

        if (!isOurMenu(inventory)) {
            return;
        }

        if (!(event.getPlayer() instanceof Player player)) {
            return;
        }

        returnItem(player, inventory, BOOK_SLOT);
        returnItem(player, inventory, SWORD_SLOT);
    }

    private void returnItem(
            Player player,
            Inventory inventory,
            int slot
    ) {
        ItemStack item = inventory.getItem(slot);

        if (item == null || item.getType().isAir()) {
            return;
        }

        inventory.setItem(slot, null);

        Map<Integer, ItemStack> leftovers =
                player.getInventory().addItem(item.clone());

        for (ItemStack leftover : leftovers.values()) {
            player.getWorld().dropItemNaturally(
                    player.getLocation(),
                    leftover
            );
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        processing.remove(event.getPlayer().getUniqueId());
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
            message(player, "&e/denchants menu &7- Buka menu enchant");
            message(player, "&e/denchants book starter &7- Dapat buku enchant");
            return true;
        }

        if (args[0].equalsIgnoreCase("menu")) {
            openMenu(player);
            return true;
        }

        if (args[0].equalsIgnoreCase("book")
                && args.length >= 2
                && args[1].equalsIgnoreCase("starter")) {

            Map<Integer, ItemStack> leftovers =
                    player.getInventory().addItem(createStarterBook());

            for (ItemStack leftover : leftovers.values()) {
                player.getWorld().dropItemNaturally(
                        player.getLocation(),
                        leftover
                );
            }

            message(player, "&aBuku Arcane Strike diberikan!");
            return true;
        }

        message(player, "&cPerintah tidak dikenal. Gunakan /denchants help");
        return true;
    }
}
