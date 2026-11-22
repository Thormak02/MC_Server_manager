package de.freimarken.paper;

import de.freimarken.kern.Rundgang;
import de.freimarken.kern.Rundgangsstation;
import java.util.ArrayList;
import java.util.List;
import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;

/**
 * Wer eine Station des Rundgangs betritt, erfaehrt, was man dort tut (Plan 10.2).
 *
 * <p>Plan 10.2 verlangt "ein Ort, kein Textfenster" - und darum haengt diese Wache am
 * SCHRITT und nicht an einem Befehl. Ein Spieler laeuft durch Altfurt, und die Stationen
 * erklaeren sich beim Betreten.
 *
 * <p><b>Dieselbe Bauart wie {@link Torwache}, Zeile fuer Zeile</b>, und die erste ist die
 * wichtigste: CraftBukkit feuert {@code PlayerMoveEvent} auch dann, wenn sich nur die
 * Blickrichtung um zehn Grad aendert - bei voellig unveraenderten Koordinaten.
 * {@code hasExplicitlyChangedBlock} sind drei Vergleiche und sortiert die ueberwaeltigende
 * Mehrheit der Ereignisse aus, bevor irgendetwas gerechnet wird. Danach die Welt: Die
 * Stationen stehen alle in einer, und ein Spieler in Kornmark braucht die Quader nicht
 * durchsucht zu bekommen.
 *
 * <p><b>Die DRITTE Bewegungswache des Projekts</b> - nach {@link Torwache} (Eintritt in
 * einen Quader) und {@link Aufwaermzeit} (Austritt). Eine eigene und nicht in der
 * Torwache mit: Jene entscheidet ueber eine REISE, diese ueber eine Auskunft, und ein
 * Torquader ist kein Stationsbereich. Zusammengelegt haette die Torwache zwei Aufgaben
 * und zwei Quaderlisten, und ein Umbau an der Reise fasste den Rundgang mit an.
 *
 * <p><b>Angemeldet wird sie nur, wenn es Stationen GIBT</b> - dieselbe Entscheidung wie
 * bei der Torwache ({@code if hatTore()}). Ab Werk gibt es keine; dann hoert hier niemand
 * zu, und kein Schritt kostet etwas.
 *
 * <p><b>Der haeufigste Fall ist der billigste.</b> Ein Spieler laeuft durch dieselben
 * Gebaeude immer wieder - {@code Rundgang.betreten} antwortet dann
 * {@code SCHON_GESEHEN}, und es wird nichts gesendet und nichts geschrieben. Gelesen wird
 * dafuer eine Zeile aus der Ablage, und das ist der Preis dieser Wache: eine Abfrage auf
 * den Hauptschluessel, aber nur solange der Spieler wirklich in einem Bereich steht.
 */
final class Rundgangswache implements Listener {

    private final Rundgang rundgang;
    private final World welt;
    private final Sprache sprache;

    /**
     * @param welt die Welt, in der die Stationen liegen - darf {@code null} sein, wenn es
     *             keine Stationen gibt. Dann wird diese Wache ohnehin nicht angemeldet
     */
    Rundgangswache(Rundgang rundgang, World welt, Sprache sprache) {
        if (rundgang == null || sprache == null) {
            throw new IllegalArgumentException("Die Rundgangswache braucht den Rundgang"
                    + " und Sprache");
        }
        this.rundgang = rundgang;
        this.welt = welt;
        this.sprache = sprache;
    }

    /** Ob es ueberhaupt eine Station gibt - sonst braucht sich der Zuhoerer nicht
     * anzumelden. */
    boolean hatStationen() {
        return rundgang.gibtEs() && welt != null;
    }

    @EventHandler(ignoreCancelled = true)
    public void beimSchritt(PlayerMoveEvent ereignis) {
        if (!ereignis.hasExplicitlyChangedBlock()) {
            return;
        }
        Location zu = ereignis.getTo();
        if (welt == null || !welt.equals(zu.getWorld())) {
            return;
        }
        Rundgangsstation station = rundgang.verzeichnis()
                .anDemOrt(zu.getBlockX(), zu.getBlockY(), zu.getBlockZ());
        if (station == null) {
            return;
        }
        melde(ereignis.getPlayer(), station);
    }

    /**
     * Traegt die Station ein und sendet, was daraus wurde.
     *
     * <p>Die Entscheidung liegt in {@link Rundgang#betreten} und nicht hier: Ob eine
     * Station neu ist und ob sie die letzte war, ist eine Rechnung ueber Ablage und
     * Verzeichnis - und die laeuft ohne Server.
     */
    private void melde(Player wer, Rundgangsstation station) {
        Rundgang.Bescheid bescheid = rundgang.betreten(wer.getUniqueId(),
                station.schluessel());
        if (bescheid == Rundgang.Bescheid.SCHON_GESEHEN) {
            // Der haeufigste Fall: nichts senden, nichts schreiben.
            return;
        }
        sende(wer, zeilen(station, rundgang.gesehen(wer.getUniqueId()),
                bescheid == Rundgang.Bescheid.ABGESCHLOSSEN));
    }

    /**
     * Was ein Spieler beim ersten Betreten liest.
     *
     * <p>Paketweit sichtbar, damit die Probe sie ohne Server bauen kann - dieselbe Form
     * wie bei {@link HilfeBefehle#zeilen}.
     *
     * <p>Jede Zeile ueber {@code bau} und nicht ueber {@code eineZeile}: Der Stationstext
     * DARF mehrzeilig sein, und eine Umbruchmarke in der Kopfzeile waere sonst ein Wurf
     * mitten im Schritt eines Spielers.
     */
    List<Component> zeilen(Rundgangsstation station, int gesehen, boolean fertig) {
        List<Component> zeilen = new ArrayList<>();
        // ZAEHLZAHL 1 und nicht die Zahl der gesehenen Stationen: Jene gehoert in den
        // EINSATZ ({gesehen}), und die Zaehlzahl waehlt bei Art.GEZAEHLT zwischen Einzahl
        // und Mehrzahl - dieser Satz ist keine. Mit der falschen Zahl stuende derselbe
        // Satz in der Bildtafel unter einem Schluessel, den es nicht gibt.
        zeilen.addAll(sprache.bau(RundgangSatz.STATION, 1L,
                Einsatz.von("station", sprache.wort(
                                new Stationssatz(station.schluessel(),
                                        Stationssatz.Teil.NAME), Einsatz.keine()))
                        .mit("gesehen", gesehen)
                        .mit("alle", rundgang.stationen())));
        zeilen.addAll(sprache.bau(new Stationssatz(station.schluessel(),
                Stationssatz.Teil.TEXT), 1L, Einsatz.keine()));
        if (fertig) {
            zeilen.addAll(sprache.bau(RundgangSatz.ABGESCHLOSSEN, 1L, Einsatz.keine()));
        }
        return List.copyOf(zeilen);
    }

    /** Die EINE Sendestelle dieser Klasse. */
    private static void sende(Player wer, List<Component> zeilen) {
        zeilen.forEach(wer::sendMessage);
    }
}
