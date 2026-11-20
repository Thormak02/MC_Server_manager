package de.freimarken.paper;

import java.util.Optional;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

/**
 * Der Name hinter einer Kennung - die EINE Stelle, die ihn holt.
 *
 * <p>Angelegt am 08.11.2026, weil es dieselbe Regel schon ZWEIMAL gab:
 * {@code Schutzwache.namen} und {@code Ladenschutz.namen} standen Zeichen fuer Zeichen
 * gleich da, und mit dem Shopschutz waere eine dritte Kopie dazugekommen. Das ist der
 * Nutzergrundsatz "alle Wege zur selben Tat": erst alle Pfade suchen, dann die Regel an
 * EINER Stelle bauen.
 *
 * <p>Drei Aufrufer haetten sonst drei Ruecksicherungen gehabt, und eine Aenderung - etwa
 * acht Zeichen auf zwoelf - waere an zwei von drei Stellen hereingegangen. Der
 * Unterschied faellt bei einem Spieler auf, den der Server noch nie gesehen hat, also
 * selten und beim Falschen.
 *
 * <p><b>Eine eigene Datei und keine Methode in {@link Schutzwache}.</b> Der
 * Klasseninitialisierer der Wache baut {@code HEIKLE_GRUPPEN} aus {@code Tag.FLOWER_POTS}
 * und fragt damit {@code Bukkit.server}; ein Aufruf von dort riss jede Probe ohne Server
 * in einen {@code ExceptionInInitializerError}, bevor sie ein Zeichen vergleichen kann.
 * Derselbe Grund, aus dem es {@link Schutztext} gibt. Hier wird {@code Bukkit} erst IN
 * der Methode gefragt, also traegt die Klasse ohne Server.
 */
final class Spielername {

    /**
     * So viele Zeichen der Kennung treten an die Stelle eines fehlenden Namens.
     *
     * <p>Acht sind die erste Gruppe einer UUID. Sie genuegen, damit ein Betreiber im
     * Protokoll denselben Spieler wiedererkennt, und sind kurz genug fuer einen
     * Handbalken.
     */
    static final int ZEICHEN_DER_KENNUNG = 8;

    private Spielername() {
    }

    /**
     * Die Kennung zu einem NAMEN - die Gegenrichtung, und sie geht nie ins Netz.
     *
     * <p>Hergezogen am 18.11.2026 aus {@code ParzellenBefehle.findeSpieler}, weil
     * {@code WolkeBefehle} dieselbe Frage stellt: Wer einen Mitbauer eintraegt, nennt
     * einen Namen. Zwei Abschriften waeren zwei Ruecksicherungen - und die Begruendung
     * darunter ist zu teuer erkauft, um sie zu verdoppeln:
     *
     * <p><b>Absichtlich NICHT {@code Bukkit.getOfflinePlayer(String)}</b>: Das fragt im
     * Zweifel bei Mojang nach und haelt dabei den ganzen Server an.
     *
     * <p><b>Und absichtlich nicht {@code Bukkit.getOfflinePlayers()}</b>: Das baut die
     * Liste aller je angemeldeten Spieler bei JEDEM Aufruf neu auf, mit einem
     * {@code getName()} je Eintrag. Ein Spieler, der einen solchen Befehl mit
     * Zufallsnamen wiederholt, haelt damit den ganzen Server an - ohne Recht und aus
     * jeder Welt heraus.
     *
     * <p>{@code getOfflinePlayerIfCached} schaut nur in den Zwischenspeicher; der Preis
     * ist, dass ein lange abwesender Spieler nicht gefunden wird, solange ihn niemand
     * nachgeschlagen hat. Der Aufrufer macht daraus eine Absage MIT Namen - ein stilles
     * Nichtstun waere die schlimmere Antwort.
     */
    static Optional<UUID> kennung(String name) {
        if (name == null || name.isBlank()) {
            return Optional.empty();
        }
        Player anwesend = Bukkit.getPlayerExact(name);
        if (anwesend != null) {
            return Optional.of(anwesend.getUniqueId());
        }
        OfflinePlayer bekannt = Bukkit.getOfflinePlayerIfCached(name);
        return bekannt != null ? Optional.of(bekannt.getUniqueId()) : Optional.empty();
    }

    /**
     * Die Kennung zu einem Namen - AUCH fuer einen lange abwesenden Spieler.
     *
     * <p><b>Der Unterschied zu {@link #kennung} ist der ganze Zweck:</b> Jene schaut nur
     * in den Zwischenspeicher und findet einen lange Abwesenden nicht. Fuer einen
     * Mitbauer ist das richtig - der Eintrag kostet nichts und laesst sich wiederholen.
     * Fuer eine Verwaltungstat ist es falsch: Wer einem Spieler Geld gutschreiben oder
     * ein Blockbuch nachsehen will, meint oft genau den, der gerade nicht da ist.
     *
     * <p>{@code hasPlayedBefore()} ist die Schranke, und sie ist Pflicht:
     * {@code getOfflinePlayer(String)} gibt NIEMALS {@code null}, sondern erfindet bei
     * einem unbekannten Namen ein Exemplar mit frisch gewuerfelter Kennung. Ohne die
     * Frage wuerde ein Tippfehler zu einer Buchung auf ein Konto, das es nie gab - die
     * Kasse legt es beim Gutschreiben samt Startguthaben an, und niemand findet es
     * wieder.
     *
     * <p>Der anwesende Spieler wird ZUERST gefragt: Er hat sicher eine Kennung, und
     * {@code hasPlayedBefore()} ist auf manchen Servern fuer den gerade Beigetretenen
     * noch falsch.
     *
     * <p><b>Diese Methode stand bis zum 20.11.2026 zweimal im Projekt</b> - wortgleich
     * privat in {@code BlockBefehle} und in {@code ShopBefehle}, mit derselben
     * Begruendung in zwei Javadocs. Aufgefallen ist es, als der Betreiberbefehl fuer Geld
     * sie zum dritten Mal brauchte. Zwei Abschriften derselben Schranke laufen
     * auseinander, und die eine, die ihr {@code hasPlayedBefore} verliert, bucht dann auf
     * Geisterkonten.
     */
    static UUID kennungAuchAbwesend(String name) {
        if (name == null || name.isBlank()) {
            return null;
        }
        Player anwesend = Bukkit.getPlayerExact(name);
        if (anwesend != null) {
            return anwesend.getUniqueId();
        }
        OfflinePlayer bekannt = Bukkit.getOfflinePlayer(name);
        return bekannt.hasPlayedBefore() ? bekannt.getUniqueId() : null;
    }

    /**
     * Der Name zu dieser Kennung, oder acht Zeichen der Kennung selbst.
     *
     * <p>Siehe den Klassenkopf. Die Gegenrichtung steht in {@link #kennung}.
     */
    static String von(UUID wer) {
        if (wer == null) {
            // Nur fuer den Fall, dass ein Aufrufer die Besitzerfrage nicht vorher stellt.
            // Der Shopschutz tut es (ein Bundshop bekommt einen eigenen Satz ohne Namen),
            // aber ein Wurf an dieser Stelle waere eine Wache, die beim Schuetzen stirbt.
            return "-";
        }
        String name = Bukkit.getOfflinePlayer(wer).getName();
        return name != null ? name : wer.toString().substring(0, ZEICHEN_DER_KENNUNG);
    }
}
