package pl.dawcou.astrars.system;

import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import pl.dawcou.astrars.AstraRS;

public class NoticeManager {

    private final AstraRS plugin;

    public NoticeManager(AstraRS plugin) {
        this.plugin = plugin;
    }

    // --- POMOCNICZE METODY DO PREFIXU I JĘZYKA ---

    private String getLang() {
        return plugin.getLanguageManager().getLang();
    }

    private String getPrefix(CommandSender sender) {
        String rawPrefix = (sender instanceof ConsoleCommandSender) ? AstraRS.PREFIX2 : AstraRS.PREFIX;
        return plugin.getLanguageManager().parseToLegacy(rawPrefix);
    }

    private String getConsolePrefix() {
        return getPrefix(Bukkit.getConsoleSender());
    }

    // --- METODY POWIADOMIEŃ KOMEND I POMOCY ---

    public void sendHelp(CommandSender sender) {
        String p = getPrefix(sender) + " ";
        boolean isPl = getLang().equalsIgnoreCase("pl");

        sender.sendMessage(p + "§b§l=== " + (isPl ? "AstraRedstoneSystems System Pomocy" : "AstraRedstoneSystems Help System") + " ===");
        sender.sendMessage(p + "§7Wersja: §f" + plugin.getDescription().getVersion() + " §7| Autor: §f" + plugin.getAuthor());
        sender.sendMessage(p + " ");
        sender.sendMessage(p + "§e§l" + (isPl ? "Komendy Gracza:" : "Player Commands:"));
        sender.sendMessage(p + " §f/gate <kategoria> <typ> [parametr] §7- " + (isPl ? "Przyznaje blok bramki logicznej" : "Gives a logic gate block"));
        sender.sendMessage(p + " §f/ars help | /ars pomoc §7- " + (isPl ? "Wyświetla to menu pomocy" : "Displays this help menu"));
        sender.sendMessage(p + " §f/ars info §7- " + (isPl ? "Informacje o autorze i wersji" : "Information about author and version"));

        if (sender.hasPermission("astrars.admin") || sender.hasPermission("astrars.reload")) {
            sender.sendMessage(p + " ");
            sender.sendMessage(p + "§c§l" + (isPl ? "Komendy Administracji:" : "Admin Commands:"));
            sender.sendMessage(p + " §f/ars reload §7- " + (isPl ? "Przeładowanie konfiguracji pluginu" : "Reloads plugin configuration"));

            if (sender.hasPermission("astrars.admin")) {
                sender.sendMessage(p + " §f/ars selector §7- " + (isPl ? "Daje różdżkę do zaznaczania obszaru" : "Gives the selection tool wand"));
                sender.sendMessage(p + " §f/ars cut §7- " + (isPl ? "Wycina zaznaczony obszar do schowka" : "Cuts selected area into clipboard"));
                sender.sendMessage(p + " §f/ars copy §7- " + (isPl ? "Kopiuje zaznaczony obszar do schowka" : "Copies selected area to clipboard"));
                sender.sendMessage(p + " §f/ars paste §7- " + (isPl ? "Wkleja obszar ze schowka" : "Pastes area from clipboard"));
                sender.sendMessage(p + " §f/ars undo §7- " + (isPl ? "Cofa ostatnie wklejenie" : "Reverts the last paste action"));
                sender.sendMessage(p + " §f/ars redo §7- " + (isPl ? "Przywraca cofnięte wklejenie" : "Restores the undone paste action"));
                sender.sendMessage(p + " §f/ars rotate <90|-90|180> §7- " + (isPl ? "Obraca strukturę w schowku" : "Rotates circuit in clipboard"));
                sender.sendMessage(p + " §f/ars schematic save <nazwa> §7- " + (isPl ? "Zapisuje schowek do pliku" : "Saves clipboard to file"));
                sender.sendMessage(p + " §f/ars schematic load <nazwa> §7- " + (isPl ? "Wczytuje schemat do schowka" : "Loads schematic to clipboard"));
                sender.sendMessage(p + " §f/ars schematic delete <nazwa> §7- " + (isPl ? "Usuwa plik schematu" : "Deletes a schematic file"));
            }
        }

        sender.sendMessage(p + "§b§l=================================");
    }

    // --- METODY POWIADOMIEŃ PLIKÓW I KONFIGURACJI ---

    public void sendConfigUpdateNotice() {
        String msg = getLang().equalsIgnoreCase("pl")
                ? "§aPomyślnie dopisano brakujące klucze do pliku konfiguracyjnego"
                : "§aMissing keys were successfully added to the configuration file";
        Bukkit.getConsoleSender().sendMessage(getConsolePrefix() + " " + msg);
    }

    public void sendConfigErrorNotice(String error) {
        String msg = getLang().equalsIgnoreCase("pl")
                ? "Błąd podczas zapisu pliku konfiguracyjnego:"
                : "Error while saving config:";
        plugin.getLogger().severe(msg + " " + error);
    }

    public void sendLangUpdateSuccess(String fileName) {
        String msg = getLang().equalsIgnoreCase("pl")
                ? "§aDodano brakujące klucze w pliku językowym:"
                : "§aAdded missing keys in language file:";
        Bukkit.getConsoleSender().sendMessage(getConsolePrefix() + " " + msg + " §e" + fileName);
    }

    public void sendLangUpdateError(String fileName, String error) {
        String msg = getLang().equalsIgnoreCase("pl")
                ? "Nie udało się zaktualizować pliku językowego (" + fileName + "):"
                : "Failed to update language file (" + fileName + "):";
        plugin.getLogger().severe(msg + " " + error);
    }

    // --- MIGRACJA ---

    public void sendMigrationNotice(String oldName, String newName) {
        String msg = getLang().equalsIgnoreCase("pl")
                ? "§6Migracja: §f" + oldName + " §7-> §f" + newName + "..."
                : "§6Migration: §f" + oldName + " §7-> §f" + newName + "...";
        Bukkit.getConsoleSender().sendMessage(getConsolePrefix() + " " + msg);
    }

    public void sendSuccessNotice(String oldName) {
        String msg = getLang().equalsIgnoreCase("pl")
                ? "§aPlik/Sekcja §f" + oldName + " §azostała pomyślnie przeniesiona!"
                : "§aFile/Section §f" + oldName + " §ahas been successfully migrated!";
        Bukkit.getConsoleSender().sendMessage(getConsolePrefix() + " " + msg);
    }

    public void sendErrorNotice(String action) {
        String msg = getLang().equalsIgnoreCase("pl")
                ? "§cBłąd podczas migracji pliku: §f" + action
                : "§cError during file migration: §f" + action;
        Bukkit.getConsoleSender().sendMessage(getConsolePrefix() + " " + msg);
    }

    // --- LOGO STARTOWE I KOŃCOWE ---

    public void sendStartupLogo() {
        String v = plugin.getDescription().getVersion();
        String version = getLang().equalsIgnoreCase("pl") ? "   §6Wersja: " : "   §6Version: ";
        String status = getLang().equalsIgnoreCase("pl") ? "§aWłączony" : "§aEnabled";
        String author = getLang().equalsIgnoreCase("pl") ? "   §6Autor: §e" : "   §6Author: §e";
        String statusLabel = getLang().equalsIgnoreCase("pl") ? "   §6Status: " : "   §6Status: ";

        String review = getLang().equalsIgnoreCase("pl")
                ? "§bPodoba Ci się plugin? Zostaw opinię na Discordzie!"
                : "§bLike the plugin? Leave a review on Discord!";

        String prefix = getConsolePrefix();

        Bukkit.getConsoleSender().sendMessage("");
        Bukkit.getConsoleSender().sendMessage("§7------------ " + prefix + " §7------------");
        Bukkit.getConsoleSender().sendMessage(version + "§ev" + v);
        Bukkit.getConsoleSender().sendMessage(statusLabel + status);
        Bukkit.getConsoleSender().sendMessage(author + plugin.getAuthor());
        Bukkit.getConsoleSender().sendMessage("");
        Bukkit.getConsoleSender().sendMessage(review);
        Bukkit.getConsoleSender().sendMessage("§7-------------------------------------------");
        Bukkit.getConsoleSender().sendMessage("");
    }

    public void sendShutdownLogo() {
        String status = getLang().equalsIgnoreCase("pl") ? "§cWyłączony" : "§cDisabled";
        String farewell = getLang().equalsIgnoreCase("pl") ? "§eDziękujemy, że z nas korzystasz! Do zobaczenia!" : "§eThanks for choosing us! See you next time!";

        String prefix = getConsolePrefix();

        Bukkit.getConsoleSender().sendMessage("");
        Bukkit.getConsoleSender().sendMessage("§7------------ " + prefix + " §7------------");
        Bukkit.getConsoleSender().sendMessage("§6   Status: " + status + " §7- " + farewell);
        Bukkit.getConsoleSender().sendMessage("§7-------------------------------------------");
        Bukkit.getConsoleSender().sendMessage("");
    }

    // --- METODY POWIADOMIEŃ WERSJI I AKTUALIZACJI ---

    public void sendVersionOk(CommandSender target) {
        String prefix = getPrefix(target);
        String msg = getLang().equalsIgnoreCase("pl")
                ? "§aAstraRedstoneSystems jest aktualny §f(§ev" + plugin.getDescription().getVersion() + "§f)"
                : "§aAstraRedstoneSystems is up to date §f(§ev" + plugin.getDescription().getVersion() + "§f)";

        target.sendMessage(prefix + " " + msg);
    }

    public void sendExperimentalNotice(CommandSender target) {
        String devTitle = getLang().equalsIgnoreCase("pl")
                ? "§bUżywasz eksperymentalnej wersji: §fv"
                : "§bYou are using an experimental version: §fv";
        String warning = getLang().equalsIgnoreCase("pl")
                ? "§cUżywaj tylko dla testów! Kod jest w fazie rozwoju!"
                : "§cUse only for testing! The code is in development!";

        String prefix = getPrefix(target);

        target.sendMessage("");
        target.sendMessage("§7------------ " + prefix + " §7------------");
        target.sendMessage(devTitle + plugin.getDescription().getVersion());
        target.sendMessage(warning);
        target.sendMessage("§7-------------------------------------------");
        target.sendMessage("");
    }

    public void sendPreReleaseNotice(CommandSender target, String version) {
        String title = getLang().equalsIgnoreCase("pl")
                ? "§6[Pre-Release] §eDostępna jest wersja testowa: §b" + version
                : "§6[Pre-Release] §eTest version available: §b" + version;
        String info = getLang().equalsIgnoreCase("pl")
                ? "§cUwaga: Wersja wyłącznie do celów testowych! Może zawierać błędy."
                : "§cNotice: For testing purposes only! May contain bugs.";

        String download = getLang().equalsIgnoreCase("pl")
                ? "§aPobierz: "
                : "§aDownload: ";

        String prefix = getPrefix(target);

        target.sendMessage("");
        target.sendMessage("§7------------ " + prefix + " §7------------");
        target.sendMessage(title);
        target.sendMessage(info);
        target.sendMessage(download + "§f§nhttps://modrinth.com/plugin/astraredstonesystems/version/" + version);
        target.sendMessage("§7-------------------------------------------");
        target.sendMessage("");
    }

    public void sendVersionDevNotice(CommandSender target, String latestStable) {
        String devTitle = getLang().equalsIgnoreCase("pl")
                ? "§bUżywasz nowszej wersji niepublicznej: §fv"
                : "§bYou are using a newer, non-public version: §fv";
        String stableInfo = getLang().equalsIgnoreCase("pl")
                ? "§eNajnowsza publiczna wersja AstraRedstoneSystems to: §fv"
                : "§eThe latest public version of AstraRedstoneSystems is: §fv";
        String warning = getLang().equalsIgnoreCase("pl")
                ? "§cUważaj na błędy, kod jest w fazie rozwoju!"
                : "§cWatch out for bugs, the code is in development!";

        String prefix = getPrefix(target);

        target.sendMessage("");
        target.sendMessage("§7------------ " + prefix + " §7------------");
        target.sendMessage(devTitle + plugin.getDescription().getVersion());
        target.sendMessage(stableInfo + latestStable);
        target.sendMessage(warning);
        target.sendMessage("§7-------------------------------------------");
        target.sendMessage("");
    }

    public void sendMajorUpdateNotice(CommandSender target, String version) {
        String title = getLang().equalsIgnoreCase("pl")
                ? "§cDostępna jest WIELKA aktualizacja AstraRedstoneSystems: §fv"
                : "§cA MAJOR AstraRedstoneSystems update is available: §fv";

        String download = getLang().equalsIgnoreCase("pl")
                ? "§aPobierz: "
                : "§aDownload: ";

        String prefix = getPrefix(target);

        target.sendMessage("");
        target.sendMessage("§7------------ " + prefix + " §7------------");
        target.sendMessage(title + version);
        target.sendMessage(download + "§f§nhttps://modrinth.com/plugin/astraredstonesystems/version/" + version);
        target.sendMessage("§7-------------------------------------------");
        target.sendMessage("");
    }

    public void sendMinorUpdateNotice(CommandSender target, String version) {
        String title = getLang().equalsIgnoreCase("pl")
                ? "§eDostępna jest nowa aktualizacja AstraRedstoneSystems: §fv"
                : "§eA new AstraRedstoneSystems update is available: §fv";

        String download = getLang().equalsIgnoreCase("pl")
                ? "§aPobierz: "
                : "§aDownload: ";

        String prefix = getPrefix(target);

        target.sendMessage("");
        target.sendMessage("§7------------ " + prefix + " §7------------");
        target.sendMessage(title + version);
        target.sendMessage(download + "§f§nhttps://modrinth.com/plugin/astraredstonesystems/version/" + version);
        target.sendMessage("§7-------------------------------------------");
        target.sendMessage("");
    }

    public void sendPatchUpdateNotice(CommandSender target, String version) {
        String title = getLang().equalsIgnoreCase("pl")
                ? "§bDostępna jest poprawka AstraRedstoneSystems: §fv"
                : "§bAn AstraRedstoneSystems bug fix is available: §fv";

        String download = getLang().equalsIgnoreCase("pl")
                ? "§aPobierz: "
                : "§aDownload: ";

        String prefix = getPrefix(target);

        target.sendMessage("");
        target.sendMessage("§7------------ " + prefix + " §7------------");
        target.sendMessage(title + version);
        target.sendMessage(download + "§f§nhttps://modrinth.com/plugin/astraredstonesystems/version/" + version);
        target.sendMessage("§7-------------------------------------------");
        target.sendMessage("");
    }

    public void sendUpdateCheckError() {
        String msg = getLang().equalsIgnoreCase("pl")
                ? "Nie udało się sprawdzić aktualizacji"
                : "Failed to check for updates";
        plugin.getLogger().warning(msg);
    }
}