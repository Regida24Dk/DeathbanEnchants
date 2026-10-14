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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class DeathbanEnchants extends JavaPlugin implements Listener {

    private NamespacedKey bookKey;
    private NamespacedKey swordKey;
    private NamespacedKey resultKey;

    private static final int BOOK_SLOT = 10;
    private static final int SWORD_SLOT = 13;
    private static final int RESULT_SLOT = 16;
    private static final int INFO_SLOT = 4;
    private static final int CLOSE_SLOT = 22;

    private final Map<UUID, Boolean> closing = new HashMap<>();

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
        resultKey = new NamespacedKey(this, "enchant_result");

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
            String... lines
    ) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();

        if (meta != null) {
            meta.setDisplayName(color(name));

            List<String> lore = new ArrayList<>();
            for (String line : lines) {
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
        lore.add(color("&7Memberikan bonus damage"));
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

        ItemStack filler = namedItem(
                Material.GRAY_STAINED_GLASS_PANE,
                "&7 "
        );

        for (int i = 0; i < inventory.getSize(); i++) {
            inventory.setItem(i, filler.clone());
        }

        // Slot input harus kosong.
        inventory.setItem(BOOK_SLOT, null);
        inventory.setItem(SWORD_SLOT, null);
        inventory.setItem(RESULT_SLOT, null);

        inventory.setItem(
                INFO_SLOT,
                namedItem(
                        Material.BOOK,
                        "&d&lARCANE STRIKE I",
                        "&7Bonus damage: &e"
                                + getConfig().getDouble(
                                        "settings.bonus-damage",
                                        2.0
                                ),
                        "",
                        "&7Buku + pedang = hasil enchant"
                )
        );

        inventory.setItem(
                CLOSE_SLOT,
                namedItem(
                        Material.BARRIER,
                        "&cTUTUP MENU"
                )
        );

        player.openInventory(inventory);
    }

    private ItemStack createResult(ItemStack sword) {
        if (sword == null || !isSword(sword.getType())
                || hasArcaneStrike(sword)) {
            return null;
        }

        ItemStack result = sword.clone();
        ItemMeta meta = result.getItemMeta();

        if (meta == null) {
            return null;
        }

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

        // Penanda hasil agar item preview tidak bisa diambil
        // sebagai item biasa tanpa melewati proses validasi.
        meta.getPersistentDataContainer().set(
                resultKey,
                PersistentDataType.BYTE,
                (byte) 1
        );

        result.setItemMeta(meta);
        return result;
    }

    private void updateResult(Inventory inventory) {
        ItemStack book = inventory.getItem(BOOK_SLOT);
        ItemStack sword = inventory.getItem(SWORD_SLOT);

        if (isStarterBook(book)
                && sword != null
                && isSword(sword.getType())
                && !hasArcaneStrike(sword)) {

            inventory.setItem(RESULT_SLOT, createResult(sword));
        } else {
            inventory.setItem(RESULT_SLOT, null);
        }
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        Inventory top = event.getView().getTopInventory();

        if (!isOurMenu(top)) {
            return;
        }

        if (!(event.getWhoClicked() instanceof Player player)) {
            event.setCancelled(true);
            return;
        }

        int rawSlot = event.getRawSlot();

        // Jangan izinkan shift-click atau hotkey memindahkan
        // item melewati aturan GUI.
        if (event.isShiftClick()
                || event.getClick().isKeyboardClick()
                || event.getClick().isCreativeAction()) {
            event.setCancelled(true);
            return;
        }

        // Klik inventory pemain bagian bawah.
        if (rawSlot < 0 || rawSlot >= top.getSize()) {
            event.setCancelled(true);
            return;
        }

        // Semua klik di menu dikendalikan plugin.
        event.setCancelled(true);

        if (rawSlot == CLOSE_SLOT) {
            player.closeInventory();
            return;
        }

        if (rawSlot == INFO_SLOT) {
            message(player,
                    "&dArcane Strike I &7memberikan bonus damage.");
            return;
        }

        // Mengambil hasil enchant.
        if (rawSlot == RESULT_SLOT) {
            ItemStack book = top.getItem(BOOK_SLOT);
            ItemStack sword = top.getItem(SWORD_SLOT);
            ItemStack preview = top.getItem(RESULT_SLOT);

            if (!isStarterBook(book)
                    || sword == null
                    || !isSword(sword.getType())
                    || hasArcaneStrike(sword)
                    || preview == null) {
                updateResult(top);
                return;
            }

            // Pastikan inventory pemain punya ruang untuk hasil.
            if (player.getInventory().firstEmpty() == -1) {
                message(player,
                        "&cSediakan satu slot kosong di inventory!");
                return;
            }

            ItemStack result = createResult(sword);

            if (result == null) {
                message(player, "&cGagal membuat hasil enchant!");
                return;
            }

            // Hapus penanda preview sebelum memberikan hasil.
            ItemMeta resultMeta = result.getItemMeta();

            if (resultMeta != null) {
                resultMeta.getPersistentDataContainer().remove(resultKey);
                result.setItemMeta(resultMeta);
            }

            // Hapus input sebelum memberikan hasil agar
            // item yang sama tidak bisa diklaim dua kali.
            top.setItem(BOOK_SLOT, null);
            top.setItem(SWORD_SLOT, null);
            top.setItem(RESULT_SLOT, null);

            if (book.getAmount() > 1) {
                ItemStack remainingBook = book.clone();
                remainingBook.setAmount(book.getAmount() - 1);
                player.getInventory().addItem(remainingBook);
            }

            player.getInventory().addItem(result);

            message(player,
                    "&aBerhasil! &dArcane Strike I &atelah dipasang!");

            updateResult(top);
            player.updateInventory();
            return;
        }

        // Slot input buku dan pedang.
        if (rawSlot != BOOK_SLOT && rawSlot != SWORD_SLOT) {
            return;
        }

        ItemStack cursor = event.getCursor();
        ItemStack current = top.getItem(rawSlot);

        boolean cursorEmpty = cursor == null || cursor.getType().isAir();
        boolean currentEmpty = current == null || current.getType().isAir();

        if (cursorEmpty) {
            if (currentEmpty) {
                return;
            }

            // Ambil item dari GUI.
            top.setItem(rawSlot, null);
            player.setItemOnCursor(current);
            updateResult(top);
            return;
        }

        boolean valid = rawSlot == BOOK_SLOT
                ? isStarterBook(cursor)
                : isSword(cursor.getType()) && !hasArcaneStrike(cursor);

        if (!valid) {
            message(player, rawSlot == BOOK_SLOT
                    ? "&cSlot ini hanya menerima buku Arcane Strike!"
                    : "&cSlot ini hanya menerima pedang tanpa Arcane Strike!");
            return;
        }

        if (!currentEmpty) {
            // Jangan menimpa item lama.
            message(player,
                    "&cKosongkan slot ini terlebih dahulu!");
            return;
        }

        // Pindahkan satu item dari cursor ke slot GUI.
        ItemStack inserted = cursor.clone();
        inserted.setAmount(1);

        top.setItem(rawSlot, inserted);

        if (cursor.getAmount() > 1) {
            ItemStack remainder = cursor.clone();
            remainder.setAmount(cursor.getAmount() - 1);
            player.setItemOnCursor(remainder);
        } else {
            player.setItemOnCursor(null);
        }

        updateResult(top);
        player.updateInventory();
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        if (isOurMenu(event.getView().getTopInventory())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        Inventory inventory = event.getInventory();

        if (!isOurMenu(inventory)
                || !(event.getPlayer() instanceof Player player)) {
            return;
        }

        // Kosongkan slot GUI dahulu, lalu kembalikan input.
        ItemStack book = inventory.getItem(BOOK_SLOT);
        ItemStack sword = inventory.getItem(SWORD_SLOT);

        inventory.setItem(BOOK_SLOT, null);
        inventory.setItem(SWORD_SLOT, null);
        inventory.setItem(RESULT_SLOT, null);

        returnItem(player, book);
        returnItem(player, sword);
    }

    private void returnItem(Player player, ItemStack item) {
        if (item == null || item.getType().isAir()) {
            return;
        }

        Map<Integer, ItemStack> leftovers =
                player.getInventory().addItem(item);

        for (ItemStack leftover : leftovers.values()) {
            player.getWorld().dropItemNaturally(
                    player.getLocation(),
                    leftover
            );
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        closing.remove(event.getPlayer().getUniqueId());
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
            message(player, "&e/denchants menu &7- Buka GUI enchant");
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
