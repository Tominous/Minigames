package au.com.mineauz.minigames;

import au.com.mineauz.minigames.config.MinigameSave;
import au.com.mineauz.minigames.managers.MinigameManager;
import au.com.mineauz.minigames.managers.MinigamePlayerManager;
import au.com.mineauz.minigames.managers.ResourcePackManager;
import au.com.mineauz.minigames.objects.MinigamePlayer;
import au.com.mineauz.minigames.objects.ResourcePack;
import com.google.common.io.Closeables;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.ListenableFuture;

import au.com.mineauz.minigames.backend.BackendManager;
import au.com.mineauz.minigames.blockRecorder.BasicRecorder;
import au.com.mineauz.minigames.commands.CommandDispatcher;
import au.com.mineauz.minigames.display.DisplayManager;
import au.com.mineauz.minigames.gametypes.MinigameType;
import au.com.mineauz.minigames.gametypes.MultiplayerType;
import au.com.mineauz.minigames.gametypes.SingleplayerType;
import au.com.mineauz.minigames.mechanics.TreasureHuntMechanic;
import au.com.mineauz.minigames.minigame.Minigame;
import au.com.mineauz.minigames.minigame.reward.RewardsModule;
import au.com.mineauz.minigames.signs.SignBase;
import au.com.mineauz.minigames.stats.MinigameStats;
import au.com.mineauz.minigames.stats.StatValueField;
import au.com.mineauz.minigames.stats.StoredGameStats;

import io.papermc.lib.PaperLib;
import net.milkbowl.vault.economy.Economy;

import org.bstats.bukkit.Metrics;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.serialization.ConfigurationSerialization;
import org.bukkit.entity.Player;
import org.bukkit.plugin.PluginDescriptionFile;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.plugin.java.JavaPluginLoader;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.jetbrains.annotations.NotNull;

import java.io.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;

public class Minigames extends JavaPlugin {

    private static final Pattern COMPILE = Pattern.compile("[-]?[0-9]+");
    public static Logger log = Logger.getLogger("Minecraft");
    private static Minigames plugin;
    private static Economy econ;
    private static SignBase minigameSigns;
    private static ComparableVersion VERSION;
    private static ComparableVersion SPIGOT_VERSION;
    public DisplayManager display;
    private ResourcePackManager resourceManager;
    private MinigamePlayerManager playerManager;
    private MinigameManager minigameManager;
    private FileConfiguration lang;
    private FileConfiguration defLang;
    private boolean debug;
    private long lastUpdateCheck;
    private BackendManager backend;
    private Metrics metrics;

    public Minigames() {
        super();
    }

    protected Minigames(final JavaPluginLoader loader, final PluginDescriptionFile description, final File dataFolder, final File file) {
        super(loader, description, dataFolder, file);
    }

    public static ComparableVersion getVERSION() {
        return VERSION;
    }

    public static Minigames getPlugin() {
        return plugin;
    }

    public static Logger log() {
        return log;
    }

    public static void log(final Level level, final String message) {
        log.log(level, message);
    }

    public static void debugMessage(final String message) {
        if (Minigames.getPlugin().debug) {
            log(Level.INFO, "[MINIGAMAES DEBUG] " + message);
        }
    }

    public void setLog(final Logger log) {
        Minigames.log = log;
    }

    public void onDisable() {
        if (getPlugin() == null) {
            log().info("Minigames is disabled");
            return;
        }
        final PluginDescriptionFile desc = this.getDescription();

        for (final Player p : this.getServer().getOnlinePlayers()) {
            if (this.playerManager.getMinigamePlayer(p).isInMinigame()) {
                this.playerManager.quitMinigame(this.playerManager.getMinigamePlayer(p), true);
            }
        }
        for (final Minigame minigame : this.minigameManager.getAllMinigames().values()) {
            if (minigame.getType() == MinigameType.GLOBAL &&
                    "treasure_hunt".equals(minigame.getMechanicName())
                    && minigame.isEnabled()) {
                if (minigame.getMinigameTimer() != null) {
                    minigame.getMinigameTimer().stopTimer();
                }
                TreasureHuntMechanic.removeTreasure(minigame);
            }
        }
        for (final Minigame mg : this.minigameManager.getAllMinigames().values()) {
            mg.saveMinigame();
        }

        this.backend.shutdown();
        this.playerManager.saveDeniedCommands();

        final MinigameSave globalLoadouts = new MinigameSave("globalLoadouts");
        if (this.minigameManager.hasLoadouts()) {
            for (final String loadout : this.minigameManager.getLoadouts()) {
                for (final Integer slot : this.minigameManager.getLoadout(loadout).getItems()) {
                    globalLoadouts.getConfig().set(loadout + '.' + slot, this.minigameManager.getLoadout(loadout).getItem(slot));
                }
                if (!this.minigameManager.getLoadout(loadout).getAllPotionEffects().isEmpty()) {
                    for (final PotionEffect eff : this.minigameManager.getLoadout(loadout).getAllPotionEffects()) {
                        globalLoadouts.getConfig().set(loadout + ".potions." + eff.getType().getName() + ".amp", eff.getAmplifier());
                        globalLoadouts.getConfig().set(loadout + ".potions." + eff.getType().getName() + ".dur", eff.getDuration());
                    }
                } else {
                    globalLoadouts.getConfig().set(loadout + ".potions", null);
                }
                if (this.minigameManager.getLoadout(loadout).getUsePermissions()) {
                    globalLoadouts.getConfig().set(loadout + ".usepermissions", true);
                } else {
                    globalLoadouts.getConfig().set(loadout + ".usepermissions", null);
                }
            }
        } else {
            globalLoadouts.getConfig().set("globalloadouts", null);
        }
        globalLoadouts.saveConfig();
        this.minigameManager.saveRewardSigns();
        resourceManager.saveResources();
        log().info(desc.getName() + " successfully disabled.");
    }

    public void onEnable() {
        try {
            plugin = this;
            switch (this.checkVersion()) {
                case -1:
                    log().warning("This version of Minigames (" + VERSION.getCanonical() + ") is designed for Bukkit Version: " + SPIGOT_VERSION.getCanonical());
                    log().warning("Your version is newer: " + Bukkit.getBukkitVersion());
                    log().warning("Please check for an updated");

                    break;
                case 0:
                    break;
                case 1:
                    if (!this.getConfig().getBoolean("forceload", true)) {
                        log().warning("This version of Minigames (" + VERSION.getCanonical() + ") " +
                                "is designed for Bukkit Version: " + SPIGOT_VERSION.getCanonical());
                        log().warning("Your version is " + Bukkit.getVersion());
                        log().warning(" Bypass this by setting forceload: true in the config");

                        log().warning("DISABLING MINIGAMES....");
                        plugin = null;
                        this.onDisable();
                        return;
                    } else {
                        log().warning("Version incompatible - Force Loading Minigames.");
                    }
            }
            final PluginDescriptionFile desc = this.getDescription();
            ConfigurationSerialization.registerClass(ResourcePack.class);
            final MinigameSave sv = new MinigameSave("lang/" + this.getConfig().getString("lang"));
            this.lang = sv.getConfig();
            this.loadLang();
            try {
                this.lang.setDefaults(this.defLang);
            } catch (final IllegalArgumentException e) {
                log().info("Language defaults were NULL");
            }
            this.checkVersion();
            this.getLogger().info("Using lang " + this.getConfig().getString("lang"));
            this.loadPresets();
            this.setupMinigames();
            if (!this.setupEconomy()) {
                this.getLogger().info("No Vault plugin found! You may only reward items.");
            }
            this.backend = new BackendManager(this.getLogger());
            if (!this.backend.initialize(this.getConfig())) {
                this.getServer().getPluginManager().disablePlugin(this);
                return;
            }
            this.getConfig().options().copyDefaults(true);
            this.saveConfig();
            //        playerManager.loadDCPlayers();
            this.playerManager.loadDeniedCommands();

            final MinigameSave globalLoadouts = new MinigameSave("globalLoadouts");
            final Set<String> keys = globalLoadouts.getConfig().getKeys(false);
            for (final String loadout : keys) {
                this.minigameManager.addLoadout(loadout);
                final Set<String> items = globalLoadouts.getConfig().getConfigurationSection(loadout).getKeys(false);
                //            for(int i = 0; i < items.size(); i++){
                //                if(globalLoadouts.getConfig().contains(loadout + "." + i))
                //                    minigameManager.getLoadout(loadout).addItemToLoadout(globalLoadouts.getConfig().getItemStack(loadout + "." + i));
                //            }
                for (final String slot : items) {
                    if (COMPILE.matcher(slot).matches()) {
                        this.minigameManager.getLoadout(loadout).addItem(globalLoadouts.getConfig().getItemStack(loadout + '.' + slot), Integer.parseInt(slot));
                    }
                }
                if (globalLoadouts.getConfig().contains(loadout + ".potions")) {
                    final Set<String> pots = globalLoadouts.getConfig().getConfigurationSection(loadout + ".potions").getKeys(false);
                    for (final String eff : pots) {
                        if (PotionEffectType.getByName(eff) != null) {
                            final PotionEffect effect = new PotionEffect(PotionEffectType.getByName(eff),
                                    globalLoadouts.getConfig().getInt(loadout + ".potions." + eff + ".dur"),
                                    globalLoadouts.getConfig().getInt(loadout + ".potions." + eff + ".amp"));
                            this.minigameManager.getLoadout(loadout).addPotionEffect(effect);
                        }
                    }
                }
                if (globalLoadouts.getConfig().contains(loadout + ".usepermissions")) {
                    this.minigameManager.getLoadout(loadout).setUsePermissions(globalLoadouts.getConfig().getBoolean(loadout + ".usepermissions"));
                }
            }

            minigameSigns = new SignBase();
            this.minigameManager.loadRewardSigns();

            final CommandDispatcher disp = new CommandDispatcher();
            this.getCommand("minigame").setExecutor(disp);
            this.getCommand("minigame").setTabCompleter(disp);

            for (final Player player : this.getServer().getOnlinePlayers()) {
                this.playerManager.addMinigamePlayer(player);
            }
            try {
                this.initMetrics();
            } catch (final IllegalStateException | NoClassDefFoundError | ExceptionInInitializerError e) {
                log().log(Level.INFO, "Metrics will not be available(enabled debug for more details): " + e.getMessage());
                if (this.debug) {
                    e.printStackTrace();
                }
            }
            PaperLib.suggestPaper(this);
            log().info(desc.getName() + " successfully enabled.");
        } catch (final Throwable e) {
            plugin = null;
            log().log(Level.SEVERE, "Failed to enable Minigames " + this.getDescription().getVersion() + ": " + e.getMessage());
            e.printStackTrace();
            this.getPluginLoader().disablePlugin(this);
        }
    }

    private void loadPresets() {
        final String prespath = this.getDataFolder() + "/presets/";
        final String[] presets = {"spleef", "lms", "ctf", "infection"};
        File pres;
        for (final String preset : presets) {
            pres = new File(prespath + preset + ".yml");
            if (!pres.exists()) {
                this.saveResource("presets/" + preset + ".yml", false);
            }
        }
    }

    private void setupMinigames() {
        this.minigameManager = new MinigameManager();
        this.playerManager = new MinigamePlayerManager();
        this.display = new DisplayManager();

        this.resourceManager = new ResourcePackManager();
        final MinigameSave resources = new MinigameSave("resources");
        this.minigameManager.addConfigurationFile("resources", resources.getConfig());
        this.resourceManager.initialize(resources);
        this.minigameManager.addMinigameType(new SingleplayerType());
        this.minigameManager.addMinigameType(new MultiplayerType());

        final MinigameSave completion = new MinigameSave("completion");
        this.minigameManager.addConfigurationFile("completion", completion.getConfig());

        this.getServer().getPluginManager().registerEvents(new Events(), this);
        this.getServer().getPluginManager().registerEvents(new BasicRecorder(), this);

        try {
            this.getConfig().load(this.getDataFolder() + "/config.yml");
            List<String> mgs = new ArrayList<>();
            if (this.getConfig().contains("minigames")) {
                mgs = this.getConfig().getStringList("minigames");
            }
            this.debug = this.getConfig().getBoolean("debug", false);
            final List<String> allMGS = new ArrayList<>(mgs);

            if (!mgs.isEmpty()) {
                Bukkit.getScheduler().scheduleSyncDelayedTask(plugin, () -> {
                    for (final String minigame : allMGS) {
                        final Minigame game = new Minigame(minigame);
                        try {
                            game.loadMinigame();
                            this.minigameManager.addMinigame(game);
                        } catch (final Exception e) {
                            this.getLogger().severe(ChatColor.RED + "Failed to load \"" + minigame + "\"! The configuration file may be corrupt or missing!");
                            e.printStackTrace();
                        }
                    }
                }, 1L);
            }
        } catch (final FileNotFoundException ex) {
            log().info("Failed to load config, creating one.");
            try {
                this.getConfig().save(this.getDataFolder() + "/config.yml");
            } catch (final IOException e) {
                log().log(Level.SEVERE, "Could not save config.yml!");
                e.printStackTrace();
            }
        } catch (final Exception e) {
            log().log(Level.SEVERE, "Failed to load config!");
            e.printStackTrace();
        }

    }

    private boolean setupEconomy() {
        if (this.getServer().getPluginManager().getPlugin("Vault") == null) {
            return false;
        }
        final RegisteredServiceProvider<Economy> rsp = this.getServer().getServicesManager().getRegistration(Economy.class);
        if (rsp == null) {
            return false;
        }
        econ = rsp.getProvider();
        return econ != null;
    }

    public boolean hasEconomy() {
        return econ != null;
    }

    public Economy getEconomy() {
        return econ;
    }

    private int checkVersion() {
        final InputStream stream = this.getResource("minigame.properties");
        final Properties p = new Properties();
        try {
            p.load(stream);
        } catch (final NullPointerException | IOException e) {
            this.getLogger().warning(e.getMessage());
        } finally {
            Closeables.closeQuietly(stream);
        }

        if (p.containsKey("version")) {
            VERSION = new ComparableVersion(p.getProperty("version"));
            SPIGOT_VERSION = new ComparableVersion(p.getProperty("spigot_version"));
            final ComparableVersion serverversion = new ComparableVersion(this.getServer().getBukkitVersion());
            return SPIGOT_VERSION.compareTo(serverversion);
        } else {
            return 1;
        }
    }

    /**
     * use {@link #getPlayerManager()}
     *
     * @return MinigamePlayeManager
     */
    @Deprecated
    public MinigamePlayerManager getPlayerData() {
        return this.playerManager;
    }

    /**
     * use {@link #minigameManager}
     *
     * @return MinigameManager
     */
    @Deprecated
    public MinigameManager getMinigameData() {
        return this.minigameManager;
    }

    public BackendManager getBackend() {
        return this.backend;
    }

    @Deprecated
    public long getLastUpdateCheck() {
        return this.lastUpdateCheck;
    }

    @Deprecated
    public void setLastUpdateCheck(final long time) {
        this.lastUpdateCheck = time;
    }

    public SignBase getMinigameSigns() {
        return minigameSigns;
    }

    private void initMetrics() {
        this.metrics = new Metrics(this);
        final Metrics.MultiLineChart chart = new Metrics.MultiLineChart("Players_in_Minigames", () -> {
            final Map<String, Integer> result = new HashMap<>();
            int count = 0;
            result.put("Total_Players", this.playerManager.getAllMinigamePlayers().size());
            for (final MinigamePlayer pl : this.playerManager.getAllMinigamePlayers()) {
                if (pl.isInMinigame()) {
                    count = result.getOrDefault(pl.getMinigame().getType().getName(), 0);
                    result.put(pl.getMinigame().getType().getName(), count + 1);
                }
            }
            return result;
        });
        final Metrics.SimpleBarChart barChart = new Metrics.SimpleBarChart("Modules_v_Servers", () -> {
            final Map<String, Integer> result = new HashMap<>();
            for (final Class module : this.minigameManager.getModules()) {
                result.put(module.getCanonicalName(), 1);
            }
            return result;
        });
        this.metrics.addCustomChart(chart);
        this.metrics.addCustomChart(barChart);
    }

    public void addMetric(final Metrics.CustomChart chart) {
        this.metrics.addCustomChart(chart);
    }


    public FileConfiguration getLang() {
        return this.lang;
    }

    private void loadLang() {
        final InputStream is = this.getClassLoader().getResourceAsStream("lang/en_AU.yml");
        final OutputStream os;
        try {
            os = new FileOutputStream(this.getDataFolder() + "/lang/en_AU.yml");
        } catch (final FileNotFoundException e) {
            log().warning(e.getMessage());
            return;
        }
        final byte[] buffer = new byte[4096];
        int length;

        try {
            assert is != null;
            while ((length = is.read(buffer)) > 0) {
                os.write(buffer, 0, length);
            }

            os.close();
            is.close();
        } catch (final IOException e) {
            e.printStackTrace();
        }

        final MinigameSave svb = new MinigameSave("lang/en_AU");
        this.defLang = svb.getConfig();
    }

    public void queueStatSave(final StoredGameStats saveData, final boolean winner) {
        MinigameUtils.debugMessage("Scheduling SQL data save for " + saveData);

        final ListenableFuture<Long> winCountFuture = this.backend.loadSingleStat(saveData.getMinigame(), MinigameStats.Wins, StatValueField.Total, saveData.getPlayer().getUUID());
        this.backend.saveStats(saveData);

        this.backend.addServerThreadCallback(winCountFuture, new FutureCallback<Long>() {
            @Override
            public void onFailure(final @NotNull Throwable t) {
            }

            @Override
            public void onSuccess(final Long winCount) {
                final Minigame minigame = saveData.getMinigame();
                final MinigamePlayer player = saveData.getPlayer();

                // Do rewards
                if (winner) {
                    RewardsModule.getModule(minigame).awardPlayer(player, saveData, minigame, winCount == 0);
                } else {
                    //TODO: RewardsModule reward on loss
                }
            }
        });
    }

    public void toggleDebug() {
        this.debug = !this.debug;
        this.backend.toggleDebug();
        if (this.backend.isDebugging() && !this.debug) this.backend.toggleDebug();
    }

    public boolean isDebugging() {
        return this.debug;
    }

    public MinigamePlayerManager getPlayerManager() {
        return this.playerManager;
    }

    public MinigameManager getMinigameManager() {
        return this.minigameManager;
    }

    public ResourcePackManager getResourceManager() {
        return this.resourceManager;
    }
}
