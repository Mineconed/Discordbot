package dk.mineconed.discordbot;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.Activity;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.channel.middleman.GuildMessageChannel;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.session.ReadyEvent;
import net.dv8tion.jda.api.events.session.ShutdownEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.requests.CloseCode;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.awt.Color;
import java.io.File;
import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Made by Mineconed
 *
 * Live server status in Discord: one embed that updates itself
 * (IP, players, TPS, staff online, uptime, version) and the slash
 * commands /online, /status and /ip.
 */
public final class Discordbot extends JavaPlugin implements Listener {

    private JDA jda;
    private Stats stats;
    private Beskeder msg;
    private BukkitTask opdatering;
    private File dataFil;
    private YamlConfiguration data;
    private volatile boolean klar;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        msg = new Beskeder(this);
        msg.indlaes();
        stats = new Stats(this);
        stats.startTpsMaaling(this);
        dataFil = new File(getDataFolder(), "data.yml");
        data = YamlConfiguration.loadConfiguration(dataFil);
        getServer().getPluginManager().registerEvents(this, this);

        String token = getConfig().getString("bot.token", "");
        if (token == null || token.trim().isEmpty() || token.contains("PUT_YOUR")) {
            getLogger().severe("No bot token! Put your bot token in plugins/Discordbot/config.yml and restart.");
        } else {
            startBot(token.trim());
        }
        startOpdatering();
        getLogger().info("Discordbot is enabled. Made by Mineconed.");
    }

    @Override
    public void onDisable() {
        if (opdatering != null) {
            opdatering.cancel();
        }
        if (jda != null) {
            if (klar) {
                GuildMessageChannel k = kanal();
                String id = data.getString("status_message_id");
                if (k != null && id != null) {
                    try {
                        k.editMessageEmbedsById(id, offlineEmbed()).complete();
                    } catch (Throwable ignore) {
                        // shutting down anyway
                    }
                }
            }
            jda.shutdown();
        }
    }

    // ═══════════════ BOT ═══════════════

    private void startBot(String token) {
        getLogger().info("Connecting to Discord ...");
        try {
            jda = JDABuilder.createLight(token)
                    .setActivity(Activity.playing(aktivitet()))
                    .addEventListeners(new Lytter())
                    .build();
        } catch (Throwable t) {
            getLogger().severe("The bot could not start: " + t.getMessage());
        }
    }

    private String aktivitet() {
        return msg.get("bot_activity", "{online}", String.valueOf(stats.spillere().size()), "{max}", String.valueOf(stats.maks()));
    }

    private final class Lytter extends ListenerAdapter {

        @Override
        public void onReady(ReadyEvent e) {
            klar = true;
            getLogger().info("The bot is online as " + e.getJDA().getSelfUser().getName() + "!");
            registrerKommandoer();
            Bukkit.getScheduler().runTask(Discordbot.this, new Runnable() {
                @Override
                public void run() {
                    opdater();
                }
            });
        }

        @Override
        public void onShutdown(ShutdownEvent e) {
            klar = false;
            CloseCode c = e.getCloseCode();
            if (c == CloseCode.AUTHENTICATION_FAILED) {
                getLogger().severe("Discord said the token is wrong. Check bot.token in config.yml.");
            } else if (c != null) {
                getLogger().warning("The bot was closed: " + c.name() + " (" + c.getMeaning() + ")");
            }
        }

        @Override
        public void onSlashCommandInteraction(final SlashCommandInteractionEvent e) {
            final String n = e.getName();
            boolean skjult = getConfig().getBoolean("slash_commands.hidden_replies", false);
            e.deferReply(skjult).queue();
            Bukkit.getScheduler().runTask(Discordbot.this, new Runnable() {
                @Override
                public void run() {
                    MessageEmbed svar;
                    if (n.equals("online")) {
                        svar = onlineEmbed();
                    } else if (n.equals("ip")) {
                        svar = ipEmbed();
                    } else {
                        svar = statusEmbed(true);
                    }
                    e.getHook().sendMessageEmbeds(svar).queue();
                }
            });
        }
    }

    private void registrerKommandoer() {
        List<CommandData> l = new ArrayList<CommandData>();
        if (getConfig().getBoolean("slash_commands.online", true)) {
            l.add(Commands.slash("online", msg.get("slash_online")));
        }
        if (getConfig().getBoolean("slash_commands.status", true)) {
            l.add(Commands.slash("status", msg.get("slash_status")));
        }
        if (getConfig().getBoolean("slash_commands.ip", true)) {
            l.add(Commands.slash("ip", msg.get("slash_ip")));
        }
        String gid = getConfig().getString("bot.guild_id", "");
        Guild g = null;
        if (gid != null && !gid.trim().isEmpty()) {
            try {
                g = jda.getGuildById(gid.trim());
            } catch (Exception ignore) {
                g = null;
            }
        }
        Consumer<Throwable> fejl = fejl("register slash commands");
        if (g != null) {
            g.updateCommands().addCommands(l).queue(null, fejl);
        } else {
            jda.updateCommands().addCommands(l).queue(null, fejl);
        }
    }

    private Consumer<Throwable> fejl(final String hvad) {
        return new Consumer<Throwable>() {
            @Override
            public void accept(Throwable t) {
                getLogger().warning("Discord (" + hvad + "): " + t.getMessage());
            }
        };
    }

    private GuildMessageChannel kanal() {
        String id = getConfig().getString("status.channel_id", "");
        if (jda == null || id == null || id.trim().isEmpty()) {
            return null;
        }
        try {
            return jda.getChannelById(GuildMessageChannel.class, Long.parseLong(id.trim()));
        } catch (Exception e) {
            return null;
        }
    }

    // ═══════════════ EMBEDS ═══════════════

    private static Color farve(String hex, Color standard) {
        try {
            return Color.decode(hex.startsWith("#") ? hex : "#" + hex);
        } catch (Exception e) {
            return standard;
        }
    }

    private EmbedBuilder ny(String titel, boolean online) {
        EmbedBuilder b = new EmbedBuilder();
        b.setTitle(titel);
        b.setColor(online ? farve(getConfig().getString("status.color_online", "#2ECC71"), Color.GREEN)
                : farve(getConfig().getString("status.color_offline", "#E74C3C"), Color.RED));
        String ikon = getConfig().getString("server.icon_url", "");
        if (ikon != null && ikon.startsWith("http")) {
            b.setThumbnail(ikon);
        }
        b.setTimestamp(Instant.now());
        return b;
    }

    private String server() {
        return getConfig().getString("server.name", "Minecraft");
    }

    private String ip() {
        return getConfig().getString("server.ip", "play.example.com");
    }

    MessageEmbed statusEmbed(boolean liste) {
        List<String> spillere = stats.spillere();
        List<String> staff = stats.staff();
        double tps = stats.tps();
        String ikon = tps >= 18 ? "🟢" : tps >= 15 ? "🟡" : "🔴";
        EmbedBuilder b = ny(msg.get("status_title_online", "{server}", server()), true);
        b.addField(msg.get("field_ip"), "`" + ip() + "`", true);
        b.addField(msg.get("field_players"), "`" + spillere.size() + "/" + stats.maks() + "`", true);
        b.addField(msg.get("field_tps"), ikon + " `" + tps + "`", true);
        b.addField(msg.get("field_staff"), "`" + staff.size() + "`", true);
        b.addField(msg.get("field_uptime"), "`" + stats.oppetid() + "`", true);
        b.addField(msg.get("field_version"), "`" + stats.version() + "`", true);
        if (liste && getConfig().getBoolean("status.show_player_list", true)) {
            b.addField(msg.get("field_online_now"), navne(spillere), false);
        }
        b.setFooter(msg.get("status_footer", "{seconds}", String.valueOf(sekunder())));
        return b.build();
    }

    MessageEmbed offlineEmbed() {
        EmbedBuilder b = ny(msg.get("status_title_offline", "{server}", server()), false);
        b.setDescription(msg.get("status_offline_text"));
        b.addField(msg.get("field_ip"), "`" + ip() + "`", true);
        b.setFooter(msg.get("status_footer_offline"));
        return b.build();
    }

    MessageEmbed onlineEmbed() {
        List<String> spillere = stats.spillere();
        List<String> staff = stats.staff();
        EmbedBuilder b = ny(msg.get("online_title", "{server}", server()), true);
        b.setDescription(msg.get("online_text", "{online}", String.valueOf(spillere.size()), "{max}", String.valueOf(stats.maks())));
        b.addField(msg.get("field_players") + " (" + spillere.size() + ")", navne(spillere), false);
        b.addField(msg.get("field_staff") + " (" + staff.size() + ")", navne(staff), false);
        return b.build();
    }

    MessageEmbed ipEmbed() {
        EmbedBuilder b = ny(msg.get("ip_title", "{server}", server()), true);
        b.setDescription(msg.get("ip_text", "{ip}", ip(), "{version}", stats.version()));
        return b.build();
    }

    private String navne(List<String> l) {
        if (l.isEmpty()) {
            return msg.get("nobody_online");
        }
        int maks = Math.max(1, getConfig().getInt("status.max_names", 25));
        StringBuilder b = new StringBuilder();
        int i = 0;
        for (String s : l) {
            if (i >= maks) {
                b.append(" ").append(msg.get("and_more", "{count}", String.valueOf(Math.subtractExact(l.size(), maks))));
                break;
            }
            if (i > 0) {
                b.append(", ");
            }
            b.append("`").append(s).append("`");
            i++;
        }
        String s = b.toString();
        return s.length() > 1000 ? s.substring(0, 1000) + " ..." : s;
    }

    // ═══════════════ LIVE STATUS ═══════════════

    private int sekunder() {
        return Math.max(15, getConfig().getInt("status.update_seconds", 60));
    }

    private void startOpdatering() {
        if (opdatering != null) {
            opdatering.cancel();
        }
        long t = sekunder() * 20L;
        opdatering = Bukkit.getScheduler().runTaskTimer(this, new Runnable() {
            @Override
            public void run() {
                opdater();
            }
        }, t, t);
    }

    /** Edits the status message, or posts a new one if it is gone. Main thread. */
    public void opdater() {
        if (jda == null || !klar) {
            return;
        }
        jda.getPresence().setActivity(Activity.playing(aktivitet()));
        final GuildMessageChannel k = kanal();
        if (k == null) {
            return;
        }
        final MessageEmbed e = statusEmbed(true);
        String id = data.getString("status_message_id");
        if (id == null) {
            postNy(k, e);
            return;
        }
        k.editMessageEmbedsById(id, e).queue(null, new Consumer<Throwable>() {
            @Override
            public void accept(Throwable t) {
                postNy(k, e);
            }
        });
    }

    private void postNy(GuildMessageChannel k, MessageEmbed e) {
        k.sendMessageEmbeds(e).queue(new Consumer<Message>() {
            @Override
            public void accept(final Message m) {
                Bukkit.getScheduler().runTask(Discordbot.this, new Runnable() {
                    @Override
                    public void run() {
                        data.set("status_message_id", m.getId());
                        gemData();
                    }
                });
            }
        }, fejl("post status in channel " + k.getId()));
    }

    private void gemData() {
        try {
            data.save(dataFil);
        } catch (IOException e) {
            getLogger().warning("Could not save data.yml: " + e.getMessage());
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        opdaterAktivitetSenere();
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        opdaterAktivitetSenere();
    }

    private void opdaterAktivitetSenere() {
        Bukkit.getScheduler().runTaskLater(this, new Runnable() {
            @Override
            public void run() {
                if (jda != null && klar) {
                    jda.getPresence().setActivity(Activity.playing(aktivitet()));
                }
            }
        }, 2L);
    }

    // ═══════════════ /discordbot ═══════════════

    private static String f(String s) {
        return ChatColor.translateAlternateColorCodes('&', s);
    }

    @Override
    public boolean onCommand(CommandSender s, Command cmd, String label, String[] a) {
        if (!s.hasPermission("discordbot.admin")) {
            s.sendMessage(f("&cYou do not have permission."));
            return true;
        }
        String a1 = a.length > 0 ? a[0].toLowerCase() : "";
        if (a1.equals("reload")) {
            reloadConfig();
            msg.indlaes();
            startOpdatering();
            opdater();
            s.sendMessage(f("&9&lDiscordbot &8» &7Config reloaded. &8(A new token needs a restart)"));
            return true;
        }
        if (a1.equals("update")) {
            opdater();
            s.sendMessage(f("&9&lDiscordbot &8» &7Status updated in Discord."));
            return true;
        }
        if (a1.equals("new")) {
            data.set("status_message_id", null);
            gemData();
            opdater();
            s.sendMessage(f("&9&lDiscordbot &8» &7A new status message is posted."));
            return true;
        }
        s.sendMessage(f("&9&lDiscordbot &8» &7Made by &fMineconed"));
        s.sendMessage(f("&7Bot: &f" + (jda == null ? "not started" : jda.getStatus().name())
                + " &8| &7Channel: &f" + (kanal() == null ? "&cnot found" : "#" + kanal().getName())));
        s.sendMessage(f("&f/" + label + " reload &8• &7reload config and messages"));
        s.sendMessage(f("&f/" + label + " update &8• &7update the status now"));
        s.sendMessage(f("&f/" + label + " new &8• &7post a new status message"));
        return true;
    }
}
