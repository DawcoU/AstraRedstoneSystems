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
        String lang = getLang().toLowerCase();

        String helpTitle;
        String versionLabel;
        String authorLabel;
        String playerCmds;
        String gateSyntax;
        String adminCmds;
        String rotateSyntax;
        String schemSaveSyntax;
        String schemLoadSyntax;
        String schemDelSyntax;

        switch (lang) {
            case "pl":
                helpTitle = "AstraRedstoneSystems System Pomocy";
                versionLabel = "Wersja:";
                authorLabel = "Autor:";
                playerCmds = "Komendy Gracza:";
                gateSyntax = "<category> <type> [parameter]";
                adminCmds = "Komendy Administracji:";
                rotateSyntax = "<90|-90|180>";
                schemSaveSyntax = "save <name>";
                schemLoadSyntax = "load <name>";
                schemDelSyntax = "delete <name>";
                break;
            case "de":
                helpTitle = "AstraRedstoneSystems Hilfesystem";
                versionLabel = "Version:";
                authorLabel = "Autor:";
                playerCmds = "Spieler-Befehle:";
                gateSyntax = "<category> <type> [parameter]";
                adminCmds = "Admin-Befehle:";
                rotateSyntax = "<90|-90|180>";
                schemSaveSyntax = "save <name>";
                schemLoadSyntax = "load <name>";
                schemDelSyntax = "delete <name>";
                break;
            case "es":
                helpTitle = "AstraRedstoneSystems Sistema de Ayuda";
                versionLabel = "Versión:";
                authorLabel = "Autor:";
                playerCmds = "Comandos de Jugador:";
                gateSyntax = "<category> <type> [parameter]";
                adminCmds = "Comandos de Administración:";
                rotateSyntax = "<90|-90|180>";
                schemSaveSyntax = "save <name>";
                schemLoadSyntax = "load <name>";
                schemDelSyntax = "delete <name>";
                break;
            default:
                helpTitle = "AstraRedstoneSystems Help System";
                versionLabel = "Version:";
                authorLabel = "Author:";
                playerCmds = "Player Commands:";
                gateSyntax = "<category> <type> [parameter]";
                adminCmds = "Admin Commands:";
                rotateSyntax = "<90|-90|180>";
                schemSaveSyntax = "save <name>";
                schemLoadSyntax = "load <name>";
                schemDelSyntax = "delete <name>";
                break;
        }

        sender.sendMessage(p + "§b§l=== " + helpTitle + " ===");
        sender.sendMessage(p + "§7" + versionLabel + " §f" + plugin.getDescription().getVersion() + " §7| " + authorLabel + " §f" + plugin.getAuthor());
        sender.sendMessage(p + " ");
        sender.sendMessage(p + "§e§l" + playerCmds);
        sender.sendMessage(p + " §f/gate " + gateSyntax);
        sender.sendMessage(p + " §f/ars help | /ars pomoc");
        sender.sendMessage(p + " §f/ars info");

        if (sender.hasPermission("astrars.admin") || sender.hasPermission("astrars.reload")) {
            sender.sendMessage(p + " ");
            sender.sendMessage(p + "§c§l" + adminCmds);
            sender.sendMessage(p + " §f/ars reload");

            if (sender.hasPermission("astrars.admin")) {
                sender.sendMessage(p + " §f/ars selector");
                sender.sendMessage(p + " §f/ars cut");
                sender.sendMessage(p + " §f/ars copy");
                sender.sendMessage(p + " §f/ars paste");
                sender.sendMessage(p + " §f/ars undo");
                sender.sendMessage(p + " §f/ars redo");
                sender.sendMessage(p + " §f/ars rotate " + rotateSyntax);
                sender.sendMessage(p + " §f/ars schematic " + schemSaveSyntax);
                sender.sendMessage(p + " §f/ars schematic " + schemLoadSyntax);
                sender.sendMessage(p + " §f/ars schematic " + schemDelSyntax);
            }
        }

        sender.sendMessage(p + "§b§l=================================");
    }

    // --- METODY POWIADOMIEŃ PLIKÓW I KONFIGURACJI ---

    public void sendConfigUpdateNotice() {
        String msg = switch (getLang().toLowerCase()) {
            case "pl" -> "§aZaktualizowano plik konfiguracyjny";
            case "de" -> "§aAktualisierte Konfigurationsdatei";
            case "es" -> "§aArchivo de configuración actualizado";
            default -> "§aUpdated configuration file";
        };
        Bukkit.getConsoleSender().sendMessage(getConsolePrefix() + " " + msg);
    }

    public void sendConfigErrorNotice(String error) {
        String msg = switch (getLang().toLowerCase()) {
            case "pl" -> "Błąd podczas zapisu pliku konfiguracyjnego. Błąd:";
            case "de" -> "Fehler beim Speichern der Konfigurationsdatei. Fehler:";
            case "es" -> "Error al guardar el archivo de configuración. Error:";
            default -> "Error while saving config. Error:";
        };
        plugin.getLogger().severe(msg + " " + error);
    }

    public void sendLangUpdateSuccess(String fileName) {
        String msg = switch (getLang().toLowerCase()) {
            case "pl" -> "§aZaktualizowano plik językowy:";
            case "de" -> "§aAktualisierte Sprachdatei:";
            case "es" -> "§aArchivo de idioma actualizado:";
            default -> "§aUpdated language file:";
        };
        Bukkit.getConsoleSender().sendMessage(getConsolePrefix() + " " + msg + " §e" + fileName);
    }

    public void sendLangUpdateError(String fileName, String error) {
        String msg = switch (getLang().toLowerCase()) {
            case "pl" -> "Nie udało się zaktualizować pliku językowego (" + fileName + "). Błąd:";
            case "de" -> "Sprachdatei konnte nicht aktualisiert werden (" + fileName + "). Fehler:";
            case "es" -> "Error al actualizar el archivo de idioma (" + fileName + "). Error:";
            default -> "Failed to update language file (" + fileName + "). Error:";
        };
        plugin.getLogger().severe(msg + " " + error);
    }

    // --- MIGRACJA ---

    public void sendMigrationNotice(String oldName, String newName) {
        String msg = switch (getLang().toLowerCase()) {
            case "pl" -> "§6Migracja: §f" + oldName + " §7-> §f" + newName + "...";
            case "de" -> "§6Migration: §f" + oldName + " §7-> §f" + newName + "...";
            case "es" -> "§6Migración: §f" + oldName + " §7-> §f" + newName + "...";
            default -> "§6Migration: §f" + oldName + " §7-> §f" + newName + "...";
        };
        Bukkit.getConsoleSender().sendMessage(getConsolePrefix() + " " + msg);
    }

    public void sendSuccessMigrationNotice(String name) {
        String msg = switch (getLang().toLowerCase()) {
            case "pl" -> "§aPlik/Sekcja §f" + name + " §azostała pomyślnie przeniesiona!";
            case "de" -> "§aDatei/Abschnitt §f" + name + " §awurde erfolgreich migriert!";
            case "es" -> "§a¡Archivo/Sección §f" + name + " §amigrado con éxito!";
            default -> "§aFile/Section §f" + name + " §ahas been successfully migrated!";
        };
        Bukkit.getConsoleSender().sendMessage(getConsolePrefix() + " " + msg);
    }

    public void sendErrorMigrationNotice(String action) {
        String msg = switch (getLang().toLowerCase()) {
            case "pl" -> "§cBłąd podczas migracji: §f" + action;
            case "de" -> "§cFehler bei der Migration: §f" + action;
            case "es" -> "§cError durante la migración: §f" + action;
            default -> "§cError during migration: §f" + action;
        };
        Bukkit.getConsoleSender().sendMessage(getConsolePrefix() + " " + msg);
    }

    // --- LOGO STARTOWE I KOŃCOWE ---

    public void sendStartupLogo() {
        String v = plugin.getDescription().getVersion();
        String lang = getLang().toLowerCase();

        String versionLabel;
        String status;
        String authorLabel;
        String statusLabel;
        String review;

        switch (lang) {
            case "pl":
                versionLabel = "   §6Wersja: ";
                status = "§aWłączony";
                authorLabel = "   §6Autor: §e";
                statusLabel = "   §6Status: ";
                review = "§bPodoba Ci się plugin? Zostaw opinię na Discordzie!";
                break;
            case "de":
                versionLabel = "   §6Version: ";
                status = "§aAktiviert";
                authorLabel = "   §6Autor: §e";
                statusLabel = "   §6Status: ";
                review = "§bGefällt dir das Plugin? Hinterlasse eine Bewertung auf Discord!";
                break;
            case "es":
                versionLabel = "   §6Versión: ";
                status = "§aActivado";
                authorLabel = "   §6Autor: §e";
                statusLabel = "   §6Estado: ";
                review = "§b¿Te gusta el plugin? ¡Deja una reseña en Discord!";
                break;
            default:
                versionLabel = "   §6Version: ";
                status = "§aEnabled";
                authorLabel = "   §6Author: §e";
                statusLabel = "   §6Status: ";
                review = "§bLike the plugin? Leave a review on Discord!";
                break;
        }

        String prefix = getConsolePrefix();

        Bukkit.getConsoleSender().sendMessage("");
        Bukkit.getConsoleSender().sendMessage("§7------------ " + prefix + " §7------------");
        Bukkit.getConsoleSender().sendMessage(versionLabel + "§ev" + v);
        Bukkit.getConsoleSender().sendMessage(statusLabel + status);
        Bukkit.getConsoleSender().sendMessage(authorLabel + plugin.getAuthor());
        Bukkit.getConsoleSender().sendMessage("");
        Bukkit.getConsoleSender().sendMessage(review);
        Bukkit.getConsoleSender().sendMessage("§7-------------------------------------------");
        Bukkit.getConsoleSender().sendMessage("");
    }

    public void sendShutdownLogo() {
        String lang = getLang().toLowerCase();
        String status;
        String farewell = switch (lang) {
            case "pl" -> {
                status = "§cWyłączony";
                yield "§eDziękujemy, że z nas korzystasz! Do zobaczenia!";
            }
            case "de" -> {
                status = "§cDeaktiviert";
                yield "§eVielen Dank für die Nutzung! Bis zum nächsten Mal!";
            }
            case "es" -> {
                status = "§cDesactivado";
                yield "§e¡Gracias por elegirnos! ¡Hasta la próxima!";
            }
            default -> {
                status = "§cDisabled";
                yield "§eThanks for choosing us! See you next time!";
            }
        };

        String prefix = getConsolePrefix();

        Bukkit.getConsoleSender().sendMessage("");
        Bukkit.getConsoleSender().sendMessage("§7------------ " + prefix + " §7------------");
        Bukkit.getConsoleSender().sendMessage("§6   Status: " + status + " §7- " + farewell);
        Bukkit.getConsoleSender().sendMessage("§7-------------------------------------------");
        Bukkit.getConsoleSender().sendMessage("");
    }

    // --- METODY POWIADOMIEŃ WERSJI I AKTUALIZACJI ---

    public void sendLatestVersionNotice(CommandSender target) {
        String prefix = getPrefix(target);
        String msg = switch (getLang().toLowerCase()) {
            case "pl" ->
                    "§aAstraRedstoneSystems jest w najnowszej wersji §f(§ev" + plugin.getDescription().getVersion() + "§f)";
            case "de" ->
                    "§aAstraRedstoneSystems ist in der neuesten Version enthalten §f(§ev" + plugin.getDescription().getVersion() + "§f)";
            case "es" ->
                    "§aAstraRedstoneSystems está en la última versión §f(§ev" + plugin.getDescription().getVersion() + "§f)";
            default ->
                    "§aAstraRedstoneSystems is in the latest version §f(§ev" + plugin.getDescription().getVersion() + "§f)";
        };

        target.sendMessage(prefix + " " + msg);
    }

    public void sendExperimentalNotice(CommandSender target, String latestStable) {
        String lang = getLang().toLowerCase();
        String devTitle;
        String warning;
        String stableInfo;
        String download;

        switch (lang) {
            case "pl":
                devTitle = "§bUżywasz eksperymentalnej wersji: §fv";
                warning = "§cUżywaj tylko dla testów! Kod jest w fazie rozwoju!";
                stableInfo = "§eNajnowsza stabilna wersja AstraRedstoneSystems: §fv";
                download = "§aPobierz: ";
                break;
            case "de":
                devTitle = "§bDu verwendest eine experimentelle Version: §fv";
                warning = "§cNur für Testzwecke verwenden! Der Code befindet sich in der Entwicklung!";
                stableInfo = "§eNeueste stabile AstraRedstoneSystems-Version: §fv";
                download = "§aHerunterladen: ";
                break;
            case "es":
                devTitle = "§bEstás usando una versión experimental: §fv";
                warning = "§c¡Usa solo para pruebas! ¡El código está en desarrollo!";
                stableInfo = "§eÚltima versión estable de AstraRedstoneSystems: §fv";
                download = "§aDescargar: ";
                break;
            default:
                devTitle = "§bYou are using an experimental version: §fv";
                warning = "§cUse only for testing! The code is in development!";
                stableInfo = "§eLatest stable AstraRedstoneSystems version: §fv";
                download = "§aDownload: ";
                break;
        }

        String prefix = getPrefix(target);

        target.sendMessage("");
        target.sendMessage("§7------------ " + prefix + " §7------------");
        target.sendMessage(devTitle + plugin.getDescription().getVersion());
        target.sendMessage(warning);
        target.sendMessage(stableInfo + latestStable);
        target.sendMessage(download + "§f§nhttps://modrinth.com/plugin/astraredstonesystems/version/" + latestStable);
        target.sendMessage("§7-------------------------------------------");
        target.sendMessage("");
    }

    public void sendVersionPreReleaseNotice(CommandSender target, String version) {
        String lang = getLang().toLowerCase();
        String title;
        String info;
        String download;

        switch (lang) {
            case "pl":
                title = "§6[Pre-Release] §eDostępna jest wersja testowa: §b" + version;
                info = "§cUwaga: Wersja wyłącznie do celów testowych! Może zawierać błędy.";
                download = "§aPobierz: ";
                break;
            case "de":
                title = "§6[Pre-Release] §eTestversion verfügbar: §b" + version;
                info = "§cHinweis: Nur für Testzwecke! Kann Fehler enthalten.";
                download = "§aHerunterladen: ";
                break;
            case "es":
                title = "§6[Pre-Release] §eVersión de prueba disponible: §b" + version;
                info = "§cAviso: ¡Solo para fines de prueba! Puede contener errores.";
                download = "§aDescargar: ";
                break;
            default:
                title = "§6[Pre-Release] §eTest version available: §b" + version;
                info = "§cNotice: For testing purposes only! May contain bugs.";
                download = "§aDownload: ";
                break;
        }

        String prefix = getPrefix(target);

        target.sendMessage("");
        target.sendMessage("§7------------ " + prefix + " §7------------");
        target.sendMessage(title);
        target.sendMessage(info);
        target.sendMessage(download + "§f§nhttps://modrinth.com/plugin/astraredstonesystems/version/" + version);
        target.sendMessage("§7-------------------------------------------");
        target.sendMessage("");
    }

    public void sendVersionDevNotice(CommandSender target, String latestPublicVersion) {
        String lang = getLang().toLowerCase();
        String devTitle;
        String publicInfo;
        String download;

        switch (lang) {
            case "pl":
                devTitle = "§bUżywasz nowszej wersji: §fv";
                publicInfo = "§eNajnowsza publiczna wersja AstraRedstoneSystems to: §fv";
                download = "§aPobierz: ";
                break;
            case "de":
                devTitle = "§bSie verwenden eine neuere Version: §fv";
                publicInfo = "§eDie neueste öffentliche Version von AstraRedstoneSystems ist: §fv";
                download = "§aHerunterladen: ";
                break;
            case "es":
                devTitle = "§bEstás utilizando una versión más reciente: §fv";
                publicInfo = "§eLa última versión pública de AstraRedstoneSystems es: §fv";
                download = "§aDescargar: ";
                break;
            default:
                devTitle = "§bYou are using a newer version: §fv";
                publicInfo = "§eThe latest public version of AstraRedstoneSystems is: §fv";
                download = "§aDownload: ";
                break;
        }

        String prefix = getPrefix(target);

        target.sendMessage("");
        target.sendMessage("§7------------ " + prefix + " §7------------");
        target.sendMessage(devTitle + plugin.getDescription().getVersion());
        target.sendMessage(publicInfo + latestPublicVersion);
        target.sendMessage(download + "§f§nhttps://modrinth.com/plugin/astraredstonesystems/version/" + latestPublicVersion);
        target.sendMessage("§7-------------------------------------------");
        target.sendMessage("");
    }

    public void sendStableReleaseNotice(CommandSender target, String version) {
        String lang = getLang().toLowerCase();
        String title;
        String info;
        String download;

        switch (lang) {
            case "pl":
                title = "§aDostępna jest stabilna wersja AstraRedstoneSystems: §f";
                info = "§7Zalecana aktualizacja z wersji testowej.";
                download = "§aPobierz: ";
                break;
            case "de":
                title = "§aEine stabile AstraRedstoneSystems-Version ist verfügbar: §f";
                info = "§7Ein Update von der Testversion wird empfohlen.";
                download = "§aHerunterladen: ";
                break;
            case "es":
                title = "§aYa está disponible una versión estable de AstraRedstoneSystems: §f";
                info = "§7Se recomienda actualizar desde la versión de prueba.";
                download = "§aDescargar: ";
                break;
            default:
                title = "§aA stable AstraRedstoneSystems release is available: §f";
                info = "§7Updating from the test version is recommended.";
                download = "§aDownload: ";
                break;
        }

        String prefix = getPrefix(target);

        target.sendMessage("");
        target.sendMessage("§7------------ " + prefix + " §7------------");
        target.sendMessage(title + version);
        target.sendMessage(info);
        target.sendMessage(download + "§f§nhttps://modrinth.com/plugin/astraredstonesystems/version/" + version);
        target.sendMessage("§7-------------------------------------------");
        target.sendMessage("");
    }

    public void sendMajorUpdateNotice(CommandSender target, String version) {
        String lang = getLang().toLowerCase();
        String title;
        String download = switch (lang) {
            case "pl" -> {
                title = "§cDostępna jest WIELKA aktualizacja AstraRedstoneSystems: §fv";
                yield "§aPobierz: ";
            }
            case "de" -> {
                title = "§cEin GROSSES AstraRedstoneSystems-Update ist verfügbar: §fv";
                yield "§aHerunterladen: ";
            }
            case "es" -> {
                title = "§cUna actualización MAYOR de AstraRedstoneSystems está disponible: §fv";
                yield "§aDescargar: ";
            }
            default -> {
                title = "§cA MAJOR AstraRedstoneSystems update is available: §fv";
                yield "§aDownload: ";
            }
        };

        String prefix = getPrefix(target);

        target.sendMessage("");
        target.sendMessage("§7------------ " + prefix + " §7------------");
        target.sendMessage(title + version);
        target.sendMessage(download + "§f§nhttps://modrinth.com/plugin/astraredstonesystems/version/" + version);
        target.sendMessage("§7-------------------------------------------");
        target.sendMessage("");
    }

    public void sendMinorUpdateNotice(CommandSender target, String version) {
        String lang = getLang().toLowerCase();
        String title;
        String download = switch (lang) {
            case "pl" -> {
                title = "§eDostępna jest nowa aktualizacja AstraRedstoneSystems: §fv";
                yield "§aPobierz: ";
            }
            case "de" -> {
                title = "§eEin neues AstraRedstoneSystems-Update ist verfügbar: §fv";
                yield "§aHerunterladen: ";
            }
            case "es" -> {
                title = "§eUna nueva actualización de AstraRedstoneSystems está disponible: §fv";
                yield "§aDescargar: ";
            }
            default -> {
                title = "§eA new AstraRedstoneSystems update is available: §fv";
                yield "§aDownload: ";
            }
        };

        String prefix = getPrefix(target);

        target.sendMessage("");
        target.sendMessage("§7------------ " + prefix + " §7------------");
        target.sendMessage(title + version);
        target.sendMessage(download + "§f§nhttps://modrinth.com/plugin/astraredstonesystems/version/" + version);
        target.sendMessage("§7-------------------------------------------");
        target.sendMessage("");
    }

    public void sendPatchUpdateNotice(CommandSender target, String version) {
        String lang = getLang().toLowerCase();
        String title;
        String download = switch (lang) {
            case "pl" -> {
                title = "§bDostępna jest mała aktualizacja AstraRedstoneSystems: §fv";
                yield "§aPobierz: ";
            }
            case "de" -> {
                title = "§bEin kleineres AstraRedstoneSystems-Update ist verfügbar: §fv";
                yield "§aHerunterladen: ";
            }
            case "es" -> {
                title = "§bUna pequeña actualización de AstraRedstoneSystems está disponible: §fv";
                yield "§aDescargar: ";
            }
            default -> {
                title = "§bA minor AstraRedstoneSystems update is available: §fv";
                yield "§aDownload: ";
            }
        };

        String prefix = getPrefix(target);

        target.sendMessage("");
        target.sendMessage("§7------------ " + prefix + " §7------------");
        target.sendMessage(title + version);
        target.sendMessage(download + "§f§nhttps://modrinth.com/plugin/astraredstonesystems/version/" + version);
        target.sendMessage("§7-------------------------------------------");
        target.sendMessage("");
    }

    public void sendUpdateCheckError() {
        String msg = switch (getLang().toLowerCase()) {
            case "pl" -> "Nie udało się sprawdzić aktualizacji";
            case "de" -> "Fehler beim Überprüfen auf Updates";
            case "es" -> "Error al buscar actualizaciones";
            default -> "Failed to check for updates";
        };
        plugin.getLogger().warning(msg);
    }
}