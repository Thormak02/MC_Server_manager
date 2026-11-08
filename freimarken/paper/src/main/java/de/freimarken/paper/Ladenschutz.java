package de.freimarken.paper;

import de.freimarken.kern.Ladeneinheit;
import de.freimarken.kern.Ladenverzeichnis;
import de.freimarken.kern.Mietablage;
import de.freimarken.kern.Mietregel;
import de.freimarken.kern.Mietvertrag;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;
import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;

/**
 * Der Schutz der Ladeneinheiten - nur der Mieter darf in seinem Laden.
 *
 * <p>Die Antwort auf eine Luecke, die seit Teil A offen stand: Die Ladeneinheiten waren
 * reine Geometrie. Jeder konnte in jedem Stand bauen und jede Ladentruhe leerraeumen -
 * die {@link Schutzwache} gilt nur fuer die Marktstadt, und Altfurt ist eine andere Welt.
 * Damit war das Mieten eine Buchung ohne Wirkung.
 *
 * <p><b>Eine {@link Schutzwache.Ortsregel} und keine eigene Wache.</b> Das ist die
 * tragende Entscheidung: Die Schutzwache deckt fuenfzehn Ereignisarten ab - Bloecke
 * setzen und abbauen, Eimer, Feuer, Bilderrahmen, Ruestungsstaender, Fahrzeuge, Leinen,
 * Tuerklinken, Behaelter. Dreizehn davon laufen durch dasselbe {@code unterbinden()}.
 * Eine zweite Wache muesste jede dieser Arten noch einmal auffuehren, und eine davon
 * waere eines Tages anders - genau davor warnt die Doku der Schutzwache in ihrem ersten
 * Absatz.
 *
 * <p><b>Was hier NICHT entschieden wird, ist das Handeln.</b> Handeln darf jeder, immer
 * und ueberall (entschieden 08.11.2026), auch im Laden eines anderen - sonst haette ein
 * Kistenshop keine Kunden. Dafuer steht {@link Schutzwache.Handelsklick}, und die Frage
 * wird dort gestellt, BEVOR diese Regel gefragt wird. Hier kann sie nicht stehen: Ein
 * Handelsklick ist an einem BLOCK zu erkennen und nicht an einem Ort, und diese Regel
 * sieht nur Orte.
 *
 * <p><b>Im Zweifel VERBOTEN.</b> Antwortet die Datenbank nicht, bleibt der Laden
 * geschlossen. Dieselbe Richtung wie bei {@code Schutzwache.darfHandeln}: Ein stilles Ja
 * waere eine Markthalle, die bei jeder Netzstoerung offen steht, und ein leergeraeumtes
 * Warenlager bekommt niemand zurueck.
 */
final class Ladenschutz implements Schutzwache.Ortsregel {

    /** So selten wird ein Ausfall der Ablage ins Protokoll geschrieben. */
    private static final long KLAGEPAUSE_MS = 30_000L;

    private final Ladenverzeichnis verzeichnis;
    private final Mietablage ablage;
    private final String weltschluessel;
    private final Sprache sprache;
    private final Logger protokoll;

    /** Wann zuletzt ueber eine stumme Ablage geklagt wurde - 0 heisst: noch nie. */
    private long letzteKlage;

    /**
     * @param verzeichnis    die Einheiten aus der {@code config.yml} - beim Start
     *                       gelesen und danach unveraenderlich
     * @param weltschluessel die Welt der Markthalle, aus {@code laeden.welt}. DERSELBE
     *                       Text wie bei {@link LadenBefehle} und {@link Ladenware}: Drei
     *                       Einstellungen daneben waeren eine Welt, in der gemietet, eine
     *                       andere, in der geraeumt, und eine dritte, in der geschuetzt
     *                       wird
     */
    Ladenschutz(Ladenverzeichnis verzeichnis, Mietablage ablage, String weltschluessel,
                Sprache sprache, Logger protokoll) {
        if (weltschluessel == null || weltschluessel.isBlank()) {
            throw new IllegalArgumentException("Ohne Welt ist der Ladenschutz eine Regel,"
                    + " die in jeder Welt nach Ladeneinheiten sucht");
        }
        this.verzeichnis = verzeichnis;
        this.ablage = ablage;
        this.weltschluessel = weltschluessel;
        this.sprache = sprache;
        this.protokoll = protokoll;
    }

    @Override
    public Urteil pruefe(Player spieler, Location ort) {
        Ladeneinheit einheit = einheitAn(ort);
        if (einheit == null) {
            return Urteil.NICHT_ZUSTAENDIG;
        }
        // Die EINHEIT entscheidet, nicht die Welt: Steht an dieser Stelle eine, dann ist
        // sie zustaendig - auch wenn darunter eine Parzelle liegt. Die Begruendung steht
        // an Schutzwache.Ortsregel.
        UUID wer = spieler == null ? null : spieler.getUniqueId();
        if (wer == null) {
            // Kommt nur aus einem Programmierfehler. VERBOTEN und nicht geworfen: Diese
            // Regel laeuft bei jedem Blockereignis, und ein Wurf hier waere ein Spieler,
            // der gar nichts mehr tun kann.
            klage(new IllegalStateException("pruefe ohne Spieler"));
            return Urteil.VERBOTEN;
        }
        try {
            return Mietregel.darfInDerEinheit(ablage.fuerEinheit(einheit.name()), wer)
                    ? Urteil.ERLAUBT
                    : Urteil.VERBOTEN;
        } catch (RuntimeException fehler) {
            klage(fehler);
            return Urteil.VERBOTEN;
        }
    }

    @Override
    public Component grund(Location ort) {
        Ladeneinheit einheit = einheitAn(ort);
        if (einheit == null) {
            // Zwischen dem Urteil und dieser Frage kann ein Neuladen die Einheiten
            // getauscht haben. Dann steht kein Laden mehr da, und der allgemeine Satz
            // ist die ehrlichere Auskunft als eine erfundene Einheit.
            return Schutztext.ladenFreiBalken(sprache);
        }
        Mietvertrag vertrag;
        try {
            vertrag = ablage.fuerEinheit(einheit.name());
        } catch (RuntimeException fehler) {
            klage(fehler);
            // Die Ablage schweigt - dann ist "frei" falsch und "gehoert X" erfunden.
            // Die Stoerungszeile sagt, was wirklich los ist.
            return Schutztext.ladenGestoertBalken(sprache);
        }
        if (vertrag == null) {
            return Schutztext.ladenFreiBalken(sprache);
        }
        return Schutztext.fremderLadenBalken(sprache, einheit.name(),
                Spielername.von(vertrag.mieter()));
    }

    /**
     * Die Einheit an dieser Stelle - oder {@code null}.
     *
     * <p>Die Weltpruefung steht HIER und nicht in {@link #pruefe}, damit auch
     * {@link #grund} sie hat. Sie ist die billigste der drei Fragen (ein
     * Zeichenkettenvergleich) und muss die erste sein: Diese Regel laeuft bei jedem
     * Blockereignis des Servers, und in jeder anderen Welt soll sie nach dem ersten
     * Vergleich fertig sein.
     */
    private Ladeneinheit einheitAn(Location ort) {
        World welt = ort.getWorld();
        if (welt == null || !weltschluessel.equals(welt.key().asString())) {
            return null;
        }
        // Der Nachschlag geht ueber das Verzeichnis im Arbeitsspeicher - kein Netz. Erst
        // wenn hier eine Einheit steht, wird die Datenbank gefragt.
        return verzeichnis.anDerStelle(ort.getBlockX(), ort.getBlockY(),
                ort.getBlockZ());
    }

    /**
     * Meldet einen Ausfall der Ablage - hoechstens einmal je Pause.
     *
     * <p>Gedrosselt, und zwar aus demselben gemessenen Grund wie bei der Schutzwache:
     * Ohne Drossel schreibt ein ausgefallener Datenbankrechner in Sekunden ein Protokoll
     * voll und verdeckt damit jede andere Meldung. Die Zeit kommt aus
     * {@code System.nanoTime()} - die Wanduhr ist nicht monoton.
     */
    private void klage(RuntimeException fehler) {
        long jetzt = System.nanoTime();
        if (letzteKlage != 0L && (jetzt - letzteKlage) / 1_000_000L < KLAGEPAUSE_MS) {
            return;
        }
        letzteKlage = jetzt;
        protokoll.log(Level.WARNING, "Mietvertrag nicht nachsehbar - in der Markthalle"
                + " wird bis auf Weiteres jede Hand abgewiesen. Der Laden bleibt zu,"
                + " solange die Datenbank schweigt.", fehler);
    }
}
