package dk.mineconed.discordbot;

import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;

/**
 * Made by Mineconed
 *
 * Loads messages_en.yml / messages_da.yml (or any messages_xx.yml you make).
 */
public final class Beskeder {

    private final Discordbot plugin;
    private YamlConfiguration y;

    public Beskeder(Discordbot plugin) {
        this.plugin = plugin;
    }

    public void indlaes() {
        for (String s : new String[]{"messages_en.yml", "messages_da.yml"}) {
            if (!new File(plugin.getDataFolder(), s).exists()) {
                plugin.saveResource(s, false);
            }
        }
        String sprog = plugin.getConfig().getString("language", "en");
        File f = new File(plugin.getDataFolder(), "messages_" + sprog + ".yml");
        if (!f.exists()) {
            plugin.getLogger().warning("messages_" + sprog + ".yml not found, using English.");
            f = new File(plugin.getDataFolder(), "messages_en.yml");
        }
        y = YamlConfiguration.loadConfiguration(f);
    }

    /** Message with placeholders: get("key", "{a}", "1", "{b}", "2") */
    public String get(String noegle, String... ph) {
        String s = y == null ? null : y.getString(noegle);
        if (s == null) {
            s = noegle;
        }
        for (int i = 0; i + 1 < ph.length; i += 2) {
            s = s.replace(ph[i], ph[i + 1]);
        }
        return s;
    }
}
