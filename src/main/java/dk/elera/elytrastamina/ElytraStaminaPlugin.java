package dk.elera.elytrastamina;

import org.bukkit.Bukkit;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityToggleGlideEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.*;

public class ElytraStaminaPlugin extends JavaPlugin implements Listener, TabExecutor {
    private final Map<UUID, Double> stamina = new HashMap<>();
    private final Map<UUID, BossBar> bossBars = new HashMap<>();
    private double maxStamina;
    private double glideDrainPerTick;
    private double rocketCost;
    private double regenPerTick;
    private BukkitRunnable staminaTask;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        FileConfiguration config = getConfig();
        maxStamina = config.getDouble("max-stamina", 60.0);
        glideDrainPerTick = config.getDouble("glide-drain-per-second", 1.0) / 20.0;
        rocketCost = config.getDouble("rocket-cost", 10.0);
        regenPerTick = config.getDouble("regen-per-second", 0.3) / 20.0;

        getServer().getPluginManager().registerEvents(this, this);
        getCommand("elytrastamina").setExecutor(this);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        stamina.putIfAbsent(event.getPlayer().getUniqueId(), maxStamina);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        BossBar bar = bossBars.remove(uuid);
        if (bar != null) bar.removeAll();
    }

    @EventHandler
    public void onRocketUse(PlayerInteractEvent event) {
        if (event.getItem() != null && event.getItem().getType().toString().contains("FIREWORK_ROCKET")) {
            UUID uuid = event.getPlayer().getUniqueId();
            stamina.putIfAbsent(uuid, maxStamina);
            double current = stamina.get(uuid);
            current -= rocketCost;
            stamina.put(uuid, Math.max(0, current));
        }
    }

    @EventHandler
    public void onToggleGlide(EntityToggleGlideEvent event) {
        if (!(event.getEntity() instanceof Player)) return;
        if (event.isGliding()) {
            startStaminaTask();
        }
    }

    private void startStaminaTask() {
        if (staminaTask != null) return;
        staminaTask = new BukkitRunnable() {
            @Override
            public void run() {
                boolean anyNotFull = false;
                for (Player player : Bukkit.getOnlinePlayers()) {
                    UUID uuid = player.getUniqueId();
                    stamina.putIfAbsent(uuid, maxStamina);
                    double current = stamina.get(uuid);
                    boolean isFlying = player.isGliding();
                    boolean onGround = player.isOnGround();

                    if (isFlying) {
                        current -= glideDrainPerTick;
                    } else if (onGround) {
                        current += regenPerTick;
                    }

                    current = Math.max(0, Math.min(maxStamina, current));
                    stamina.put(uuid, current);

                    if (current < maxStamina) {
                        anyNotFull = true;
                    }

                    if (current <= 0 && player.isGliding()) {
                        player.setGliding(false);
                        player.setVelocity(new Vector(0, -1, 0));
                    }

                    updateBossBar(player, current);
                }

                if (!anyNotFull) {
                    this.cancel();
                    staminaTask = null;
                }
            }
        };
        staminaTask.runTaskTimer(this, 0L, 1L);
    }

    private void updateBossBar(Player player, double value) {
        UUID uuid = player.getUniqueId();
        BossBar bar = bossBars.computeIfAbsent(uuid, u -> {
            BossBar b = Bukkit.createBossBar("Elytra Stamina", BarColor.GREEN, BarStyle.SEGMENTED_10);
            b.addPlayer(player);
            return b;
        });
        double progress = value / maxStamina;
        bar.setProgress(Math.max(0.0, Math.min(1.0, progress)));
        bar.setVisible(progress < 1.0);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!command.getName().equalsIgnoreCase("elytrastamina")) return false;
        if (!sender.hasPermission("elytrastamina.set")) {
            sender.sendMessage("§cYou don't have permission to do that.");
            return true;
        }
        if (args.length < 2) {
            sender.sendMessage("§cUsage: /elytrastamina <player> <amount>");
            return true;
        }

        Player target = Bukkit.getPlayer(args[0]);
        if (target == null) {
            sender.sendMessage("§cPlayer not found.");
            return true;
        }

        try {
            double amount = Double.parseDouble(args[1]);
            UUID uuid = target.getUniqueId();
            amount = Math.max(0, Math.min(maxStamina, amount));
            stamina.put(uuid, amount);
            sender.sendMessage("§aSet stamina of " + target.getName() + " to " + amount);
        } catch (NumberFormatException e) {
            sender.sendMessage("§cInvalid number: " + args[1]);
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return Bukkit.getOnlinePlayers().stream().map(Player::getName).toList();
        }
        return Collections.emptyList();
    }
}