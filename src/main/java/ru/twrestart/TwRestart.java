package ru.twrestart;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.List;

public final class TwRestart extends JavaPlugin implements CommandExecutor, Listener {

    private BossBar activeBossBar;
    private boolean isRestarting = false;
    private BukkitTask restartTask;

    private String color(String text) {
        return ChatColor.translateAlternateColorCodes('&', text != null ? text : "");
    }

    @Override
    public void onEnable() {
        saveDefaultConfig();

        getCommand("resser").setExecutor(this);
        getCommand("twrestart").setExecutor(this);
        getServer().getPluginManager().registerEvents(this, this);
        getLogger().info("TwRestart включен!");
    }

    @Override
    public void onDisable() {
        cancelRestartTimer();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        FileConfiguration config = getConfig();

        if (command.getName().equalsIgnoreCase("resser")) {
            if (!sender.hasPermission("twrestart.restart")) {
                sender.sendMessage(color(config.getString("messages.no-permission")));
                return true;
            }

            if (args.length > 0 && args[0].equalsIgnoreCase("cancel")) {
                if (!isRestarting) {
                    sender.sendMessage(color(config.getString("messages.not-running")));
                    return true;
                }

                cancelRestartTimer();
                Bukkit.broadcastMessage(color(config.getString("messages.restart-cancelled")));
                sender.sendMessage(color(config.getString("messages.restart-cancelled")));
                return true;
            }

            if (isRestarting) {
                sender.sendMessage(color(config.getString("messages.already-started")));
                return true;
            }

            int seconds = config.getInt("settings.default-time");

            if (args.length > 0) {
                try {
                    seconds = Integer.parseInt(args[0]);
                } catch (NumberFormatException e) {
                    sender.sendMessage(color(config.getString("messages.invalid-number")));
                }
            }

            startRestartTimer(seconds);
            return true;
        }

        if (command.getName().equalsIgnoreCase("twrestart")) {
            if (!sender.hasPermission("twrestart.admin")) {
                sender.sendMessage(color(config.getString("messages.no-permission")));
                return true;
            }

            if (args.length > 0 && args[0].equalsIgnoreCase("reload")) {
                reloadConfig();
                sender.sendMessage(color(config.getString("messages.reload-success")));
                return true;
            }

            sender.sendMessage(color(config.getString("messages.reload-usage")));
            return true;
        }

        return false;
    }

    private void cancelRestartTimer() {
        if (this.restartTask != null) {
            this.restartTask.cancel();
            this.restartTask = null;
        }
        if (this.activeBossBar != null) {
            this.activeBossBar.removeAll();
            this.activeBossBar = null;
        }
        this.isRestarting = false;
    }

    private void startRestartTimer(int totalSeconds) {
        isRestarting = true;
        FileConfiguration config = getConfig();

        BarColor barColor;
        try {
            barColor = BarColor.valueOf(config.getString("bossbar.color").toUpperCase());
        } catch (IllegalArgumentException e) {
            barColor = BarColor.RED;
            getLogger().warning("Ошибка в config.yml: Неверный цвет боссбара! Используем RED.");
        }

        BarStyle barStyle;
        try {
            barStyle = BarStyle.valueOf(config.getString("bossbar.style").toUpperCase());
        } catch (IllegalArgumentException e) {
            barStyle = BarStyle.SOLID;
            getLogger().warning("Ошибка в config.yml: Неверный стиль боссбара! Используем SOLID.");
        }

        String initialTitle = color(config.getString("bossbar.title").replace("{time}", String.valueOf(totalSeconds)));
        activeBossBar = Bukkit.createBossBar(initialTitle, barColor, barStyle);

        for (Player player : Bukkit.getOnlinePlayers()) {
            activeBossBar.addPlayer(player);
        }

        int countdownStart = config.getInt("settings.countdown-start");

        BukkitRunnable task = new BukkitRunnable() {
            int timeLeft = totalSeconds;

            @Override
            public void run() {
                double progress = (double) timeLeft / totalSeconds;
                if (progress < 0) progress = 0;
                if (progress > 1) progress = 1;
                activeBossBar.setProgress(progress);

                String bossBarTitle = color(config.getString("bossbar.title")
                        .replace("{time}", String.valueOf(timeLeft)));
                activeBossBar.setTitle(bossBarTitle);

                if (timeLeft <= countdownStart && timeLeft > 0) {

                    String titleHeader = color(config.getString("messages.title-header"));
                    String titleFooter = color(config.getString("messages.title-footer")
                            .replace("{time}", String.valueOf(timeLeft)));

                    for (Player player : Bukkit.getOnlinePlayers()) {
                        player.sendTitle(titleHeader, titleFooter, 0, 25, 5);
                    }

                    String chatMsg = color(config.getString("messages.chat-countdown")
                            .replace("{time}", String.valueOf(timeLeft)));
                    Bukkit.broadcastMessage(chatMsg);

                }

                if (timeLeft <= 0) {
                    executeEndCommands();
                    cancelRestartTimer();
                }

                timeLeft--;
            }
        };

        this.restartTask = task.runTaskTimer(this, 0L, 20L);
    }

    private void executeEndCommands() {
        List<String> commands = getConfig().getStringList("commands-on-finish");

        getLogger().info("Выполнение команд завершения...");
        for (String cmd : commands) {
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd);
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        if (isRestarting && activeBossBar != null) {
            activeBossBar.addPlayer(event.getPlayer());
        }
    }
}