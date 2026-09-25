package pl.dawcou.astrars;

import net.kyori.adventure.platform.bukkit.BukkitAudiences;
import org.bstats.bukkit.Metrics;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabCompleter;
import org.bukkit.plugin.java.JavaPlugin;

import pl.dawcou.astrars.commands.AstraRSCommand;
import pl.dawcou.astrars.commands.GateCommand;
import pl.dawcou.astrars.gates.gui.GateGUI;
import pl.dawcou.astrars.gates.listener.GateListeners;
import pl.dawcou.astrars.file.FilesConverter;
import pl.dawcou.astrars.file.FilesUpdater;
import pl.dawcou.astrars.file.GateDataManager;
import pl.dawcou.astrars.gates.types.*;
import pl.dawcou.astrars.listeners.GateCleanupListener;
import pl.dawcou.astrars.listeners.UpdateNotifyListener;
import pl.dawcou.astrars.system.*;
import pl.dawcou.astrars.gates.data.GateValidator;
import pl.dawcou.astrars.selection.SelectionManager;

import java.util.concurrent.TimeUnit;

public class AstraRS extends JavaPlugin {

    public static final String PREFIX = "<#3277e6>[</#3277e6><gradient:#F2F2F2:#F2F2F2:#FF2E2E:#FF2E2E>AstraRS</gradient><#3277e6>]</#3277e6>";
    public static final String PREFIX2 = "§9[§fAstra§4RS§9]";
    public static final String DEBUG_PREFIX = PREFIX2 + " §b[§eDebug§b] ";

    private boolean textDisplaySupported;
    public boolean isTextDisplaySupported() { return textDisplaySupported; }

    private static AstraRS instance;
    private BukkitAudiences adventure;

    public boolean debugMode;

    private GateDataManager gateDataManager;

    private GateValidator gateValidator;

    private BasicGates basicGates;
    private MemoryGates memoryGates;
    private TimeGates timeGates;
    private NumberGates numberGates;
    private StringGates stringGates;
    private DataGates dataGates;
    private SpaceGates spaceGates;

    private GateGUI gateGUI;

    // ----------------------------------------------------------------------------------------------------
    // MANAGEROWIE SYSTEMOWI I DANYCH
    // ----------------------------------------------------------------------------------------------------

    private SchedulerManager schedulerManager;
    private LanguageManager languageManager;
    private NoticeManager noticeManager;
    private FilesUpdater filesUpdater;
    private UpdateChecker updateChecker;

    // ----------------------------------------------------------------------------------------------------
    // GETTERY - SYSTEMOWE I GLOWNE
    // ----------------------------------------------------------------------------------------------------

    public static AstraRS getInstance() { return instance; }
    public BukkitAudiences getAdventure() { return this.adventure; }
    public boolean isDebugMode() { return debugMode; }

    public SchedulerManager getSchedulerManager() { return schedulerManager; }

    public LanguageManager getLanguageManager() {
        return languageManager;
    }

    public NoticeManager getNoticeManager() {
        return noticeManager;
    }

    public UpdateChecker getUpdateChecker() { return updateChecker; }

    public GateDataManager getGateDataManager() {
        return gateDataManager;
    }

    // ----------------------------------------------------------------------------------------------------
    // GETTERY - SYSTEMOWE I GLOWNE
    // ----------------------------------------------------------------------------------------------------

    public GateGUI getGateGUI() { return gateGUI; }

    private boolean checkTextDisplaySupport() {
        try {
            Class.forName("org.bukkit.entity.TextDisplay", false, Bukkit.class.getClassLoader());
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    @Override
    public void onEnable() {
        textDisplaySupported = checkTextDisplaySupport();

        if (!textDisplaySupported) {
            getLogger().warning("TextDisplay is not available on this Minecraft version.");
            getLogger().warning("DISPLAY gates will be unavailable.");
        }

        instance = this;

        // 1. Podstawowe narzędzia
        this.adventure = BukkitAudiences.create(this);
        saveDefaultConfig();

        int pluginId = 31505;
        Metrics metrics = new Metrics(this, pluginId);

        schedulerManager = new SchedulerManager(this);

        // 2. Menedżer języka i jego natychmiastowy BOOTSTRAP (aby getLang() i wiadomości nie były null)
        languageManager = new LanguageManager(this);
        languageManager.bootstrap();

        // 3. NoticeManager (teraz ma już bezpieczny dostęp do zainicjalizowanego LanguageManagera)
        noticeManager = new NoticeManager(this);

        // 4. Migracje i aktualizacje plików (mogą bezpiecznie wysyłać logi przez NoticeManager)
        new FilesConverter(this).runAllMigrations();

        updateChecker = new UpdateChecker(this);

        filesUpdater = new FilesUpdater(this);
        filesUpdater.check();

        // 5. Pełne przeładowanie języka (po ewentualnej aktualizacji plików lang przez FilesUpdater)
        languageManager.reload();

        // 6. Menedżery danych i logiki bramek
        this.gateValidator = new GateValidator();
        this.gateDataManager = new GateDataManager(this);
        this.gateDataManager.load(); // Najpierw ładujemy dane!

        this.basicGates = new BasicGates(this, gateValidator);
        this.memoryGates = new MemoryGates(this, gateValidator);
        this.timeGates = new TimeGates(this, gateValidator);
        this.numberGates = new NumberGates(this, gateValidator);
        this.stringGates = new StringGates(this, gateValidator);
        this.dataGates = new DataGates(this, gateValidator);
        this.spaceGates = new SpaceGates(this, gateValidator);

        this.gateGUI = new GateGUI(this);

        // 7. Komendy i selekcje
        SelectionManager selectionManager = new SelectionManager(this, gateDataManager);
        AstraRSCommand astraRSCommand = new AstraRSCommand(this, selectionManager);
        GateCommand gateCommand = new GateCommand(this);

        debugMode = getConfig().getBoolean("settings.debug-mode", false);

        // Rejestrujemy eventy
        org.bukkit.plugin.PluginManager pm = getServer().getPluginManager();

        pm.registerEvents(new GateListeners(this), this);
        pm.registerEvents(new GateCleanupListener(this), this);
        pm.registerEvents(new UpdateNotifyListener(this), this);
        pm.registerEvents(new GateGUI(this), this);
        pm.registerEvents(selectionManager, this);

        registerCommand("bramka", gateCommand, gateCommand);
        registerCommand("astraredstonesystems", astraRSCommand, astraRSCommand);

        // Pętla bramek - synchronicznie co 1 tick
        schedulerManager.runSyncRepeating(() -> {
            numberGates.runNumberGates();
            stringGates.runStringGates();
            dataGates.runDataGates();

            basicGates.runBasicGates();
            memoryGates.runMemoryGates();
            timeGates.runTimeGates();
            spaceGates.runSpaceGates();
        }, 1L, 1L);

        // Zapis bramek - asynchronicznie co 5 minut (6000 ticków)
        schedulerManager.runAsyncRepeating(this::saveGates, 5, 5, TimeUnit.MINUTES);

        // LOGO STARTOWE I SPRAWDZANIE WERSJI
        schedulerManager.runAsync(() -> {
            // Logo zawsze przy starcie
            noticeManager.sendStartupLogo();

            // Sprawdzanie aktualizacji
            if (getConfig().getBoolean("settings.check-updates", true)) {
                updateChecker.checkForUpdates(Bukkit.getConsoleSender());
            } else {
                noticeManager.sendVersionOk(getServer().getConsoleSender());
            }
        });
    }

    @Override
    public void onDisable() {
        // Zapisanie danych bramek z pamięci RAM na dysk
        saveGates();

        languageManager.printMissingKeys();

        noticeManager.sendShutdownLogo();
    }

    public void saveGates() {
        if (gateDataManager != null) {
            gateDataManager.save();
        }
    }

    private void registerCommand(String name, CommandExecutor executor) {
        PluginCommand cmd = getCommand(name);
        if (cmd != null) {
            cmd.setExecutor(executor);
        }
    }

    private void registerCommand(String name, CommandExecutor executor, TabCompleter completer) {
        PluginCommand cmd = getCommand(name);
        if (cmd != null) {
            cmd.setExecutor(executor);
            cmd.setTabCompleter(completer);
        }
    }

    public String getAuthor() {
        return "DawcoU";
    }
}