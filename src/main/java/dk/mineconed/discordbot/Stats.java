package dk.mineconed.discordbot;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.metadata.MetadataValue;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Made by Mineconed
 *
 * Collects the server numbers: players, TPS, staff, uptime and version.
 * Works on 1.8 up to 1.21 (Spigot and Paper).
 */
public final class Stats {

    private final Discordbot plugin;
    private final long start = System.currentTimeMillis();
    private long sidsteMaaling = System.nanoTime();
    private double egenTps = 20.0;

    public Stats(Discordbot plugin) {
        this.plugin = plugin;
    }

    /** Own TPS measurement, used if the server has no TPS method. */
    public void startTpsMaaling(Plugin p) {
        Bukkit.getScheduler().runTaskTimer(p, new Runnable() {
            @Override
            public void run() {
                long nu = System.nanoTime();
                double sek = Math.subtractExact(nu, sidsteMaaling) / 1.0E9;
                sidsteMaaling = nu;
                if (sek > 0) {
                    egenTps = Math.min(20.0, 100.0 / sek);
                }
            }
        }, 100L, 100L);
    }

    public double tps() {
        double t = egenTps;
        try {
            // Paper
            Method m = Bukkit.getServer().getClass().getMethod("getTPS");
            t = ((double[]) m.invoke(Bukkit.getServer()))[0];
        } catch (Throwable paper) {
            try {
                // Spigot
                Object ms = Bukkit.getServer().getClass().getMethod("getServer").invoke(Bukkit.getServer());
                Field f = felt(ms.getClass(), "recentTps");
                if (f != null) {
                    t = ((double[]) f.get(ms))[0];
                }
            } catch (Throwable spigot) {
                t = egenTps;
            }
        }
        return Math.round(Math.min(20.0, t) * 10.0) / 10.0;
    }

    private static Field felt(Class<?> c, String navn) {
        while (c != null) {
            try {
                Field f = c.getDeclaredField(navn);
                f.setAccessible(true);
                return f;
            } catch (NoSuchFieldException e) {
                c = c.getSuperclass();
            }
        }
        return null;
    }

    public boolean usynlig(Player p) {
        if (!plugin.getConfig().getBoolean("staff.hide_vanished", true)) {
            return false;
        }
        for (MetadataValue v : p.getMetadata("vanished")) {
            if (v.asBoolean()) {
                return true;
            }
        }
        return false;
    }

    /** Visible players, sorted by name. */
    public List<String> spillere() {
        List<String> l = new ArrayList<String>();
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (!usynlig(p)) {
                l.add(p.getName());
            }
        }
        Collections.sort(l, String.CASE_INSENSITIVE_ORDER);
        return l;
    }

    public List<String> staff() {
        String perm = plugin.getConfig().getString("staff.permission", "discordbot.staff");
        List<String> l = new ArrayList<String>();
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (p.hasPermission(perm) && !usynlig(p)) {
                l.add(p.getName());
            }
        }
        Collections.sort(l, String.CASE_INSENSITIVE_ORDER);
        return l;
    }

    public int maks() {
        return Bukkit.getMaxPlayers();
    }

    public String oppetid() {
        long s = Math.subtractExact(System.currentTimeMillis(), start) / 1000L;
        long d = s / 86400L;
        long t = (s % 86400L) / 3600L;
        long m = (s % 3600L) / 60L;
        if (d > 0) {
            return d + "d " + t + "h " + m + "m";
        }
        if (t > 0) {
            return t + "h " + m + "m";
        }
        return m + "m";
    }

    /** "1.8.8", "1.21.1" ... */
    public String version() {
        String v = Bukkit.getBukkitVersion();
        int i = v.indexOf('\u002D');
        return i > 0 ? v.substring(0, i) : v;
    }
}
