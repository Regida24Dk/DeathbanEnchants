package id.deathban.enchants;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class DeathbanEnchants extends JavaPlugin {
    private NamespacedKey bookIdKey;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        bookIdKey = new NamespacedKey(this, "custom_book_id");
        getLogger().info("DeathbanEnchants foundation enabled.");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!command.getName().equalsIgnoreCase("denchants")) return false;
        if (args.length == 0 || args[0].equalsIgnoreCase("help")) {
            sender.sendMessage(color("&d&lDeathbanEnchants &7v" + getDescription().getVersion()));
            sender.sendMessage(color("&f/denchants help &7- show commands"));
            sender.sendMessage(color("&f/denchants book <id> &7- give a test enchantment book"));
            return true;
        }
        if (args[0].equalsIgnoreCase("book")) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage("This command can only be used in-game.");
                return true;
            }
            if (!player.hasPermission("deathbanenchants.admin")) {
                player.sendMessage(color("&cYou don't have permission."));
                return true;
            }
            if (args.length < 2) {
                player.sendMessage(color("&cUsage: /denchants book <id>"));
                return true;
            }
            ItemStack book = createBook(args[1]);
            if (book == null) {
                player.sendMessage(color("&cUnknown book ID. Check config.yml."));
                return true;
            }
            Map<Integer, ItemStack> leftovers = player.getInventory().addItem(book);
            leftovers.values().forEach(item -> player.getWorld().dropItemNaturally(player.getLocation(), item));
            player.sendMessage(color("&aTest book given: &f" + args[1]));
            return true;
        }
        sender.sendMessage(color("&cUnknown subcommand. Use /denchants help"));
        return true;
    }

    private ItemStack createBook(String id) {
        ConfigurationSection section = getConfig().getConfigurationSection("books." + id);
        if (section == null) return null;
        ItemStack item = new ItemStack(Material.ENCHANTED_BOOK);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return null;
        meta.setDisplayName(color(section.getString("name", "&dCustom Enchantment Book")));
        List<String> lore = new ArrayList<>();
        lore.add(color("&8Custom Enchantment"));
        lore.add(color("&7Rarity: &d" + section.getString("rarity", "COMMON")));
        lore.add(color("&7Level: &f" + section.getInt("level", 1)));
        for (String line : section.getStringList("description")) lore.add(color(line));
        lore.add(color("&8Book ID: " + id));
        meta.setLore(lore);
        meta.getPersistentDataContainer().set(bookIdKey, PersistentDataType.STRING, id);
        item.setItemMeta(meta);
        return item;
    }

    private String color(String input) {
        return ChatColor.translateAlternateColorCodes('&', input);
    }
}

