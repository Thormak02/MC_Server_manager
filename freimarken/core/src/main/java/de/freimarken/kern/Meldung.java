package de.freimarken.kern;

import java.time.Instant;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

/**
 * Eine Meldung (Plan 12c.3, Schritt 16a Teil C) - was ein Spieler gemeldet hat und was
 * daraus wurde.
 *
 * <p><b>Der TATORT steht mit darin</b>, und das ist die Entscheidung, an der dieser
 * Datensatz haengt: Der Plan sagt, Moderatoren <i>"springen hin"</i>. Das geht nur mit
 * einem Ort, und der muss der Ort sein, an dem GEMELDET wurde - nicht der, an dem der
 * Betroffene jetzt steht. Wer eine Stunde spaeter zum Betroffenen springt, findet den
 * Spieler, aber nicht die Tat. Der Ort ist der des MELDERS: Was er gesehen hat, hat er
 * von dort gesehen.
 *
 * <p><b>Die Nummer ist die Anrede.</b> Ein Moderator tippt {@code /meldung 7 annehmen};
 * ohne kurze Nummer muesste er eine UUID oder einen Zeitstempel abschreiben. Sie kommt
 * von der Ablage und nicht von hier - wer sie selbst vergaebe, braeuchte dafuer eine
 * zweite Wahrheit darueber, welche schon vergeben ist.
 *
 * <p><b>Was NICHT hier steht:</b> welche Strafe verhaengt wurde. Eine Strafe ist eine
 * eigene Tat mit eigenem Protokoll (Teil B), und sie kann auch ohne Meldung fallen. Eine
 * Spalte dafuer waere eine Verbindung, die in beide Richtungen unvollstaendig ist - und
 * dann steht in der Meldung "gebannt", obwohl der Bann laengst abgelaufen ist.
 *
 * @param nummer      die Anrede, von der Ablage vergeben; positiv
 * @param melder      wer gemeldet hat
 * @param betroffener wer gemeldet wurde
 * @param grund       was der Melder geschrieben hat, schon beschnitten und geprueft
 * @param welt        der Weltname des Tatorts (Bukkit-Name, ohne Namensraum)
 * @param x           Tatort
 * @param y           Tatort
 * @param z           Tatort
 * @param gemeldetAm  wann - UTC, wie jede Zeit dieses Projekts
 * @param stand       wo die Meldung steht
 * @param bearbeiter  wer sie genommen hat; {@code null} solange {@link Meldestand#OFFEN}
 * @param vermerk     was der Bearbeiter dem Melder sagt; leer solange offen
 * @param erledigtAm  wann abgeschlossen oder abgewiesen; {@code null} solange offen
 * @param gelesen     ob der Melder die Antwort schon gelesen hat
 */
public record Meldung(long nummer, UUID melder, UUID betroffener, String grund,
                      String welt, int x, int y, int z, Instant gemeldetAm,
                      Meldestand stand, UUID bearbeiter, String vermerk,
                      Instant erledigtAm, boolean gelesen) {

    /**
     * Wie kurz ein Grund sein darf.
     *
     * <p>Drei Zeichen, und das ist bewusst niedrig: "afk" und "hax" sind brauchbare
     * Meldungen. Eine hoehere Grenze erzieht niemanden zu besseren Gruenden, sie
     * verhindert nur die Meldung - und eine nicht abgegebene Meldung ist das, was dieser
     * ganze Abschnitt vermeiden soll.
     */
    public static final int KUERZESTER_GRUND = 3;

    /**
     * Wie lang ein Grund sein darf.
     *
     * <p>256 Zeichen, also etwa drei Chatzeilen. Die Grenze steht hier und in der
     * Datenbankspalte; wer sie hier aendert, aendert sie dort mit - sonst bricht der
     * strict mode von MariaDB mitten in einer Meldung ab, und der Melder glaubt, es sei
     * nichts passiert.
     */
    public static final int LAENGSTER_GRUND = 256;

    /** Wie lang ein Vermerk sein darf - dieselbe Grenze, dieselbe Begruendung. */
    public static final int LAENGSTER_VERMERK = 256;

    public Meldung {
        if (nummer <= 0L) {
            throw new IllegalArgumentException("Eine Meldung ohne Nummer: " + nummer
                    + ". Die Nummer vergibt die Ablage, und sie ist die Anrede fuer"
                    + " /meldung - eine 0 waere eine, die niemand nennen kann");
        }
        if (melder == null) {
            throw new IllegalArgumentException("Eine Meldung ohne Melder");
        }
        if (betroffener == null) {
            throw new IllegalArgumentException("Eine Meldung ohne Betroffenen");
        }
        if (melder.equals(betroffener)) {
            // Hier und nicht nur im Buch: Eine Selbstmeldung ist kein Zustand, den die
            // Ablage halten darf. Haette sie ihn, liesse sich der Riegel im Buch
            // herausnehmen, ohne dass etwas rot wird.
            throw new IllegalArgumentException("Niemand meldet sich selbst: " + melder);
        }
        grund = Klartext.geprueft(grund, LAENGSTER_GRUND, "Grund");
        if (grund.length() < KUERZESTER_GRUND) {
            throw new IllegalArgumentException("Ein Grund braucht mindestens "
                    + KUERZESTER_GRUND + " Zeichen, nicht \"" + grund + "\"");
        }
        if (welt == null || welt.isBlank()) {
            throw new IllegalArgumentException("Eine Meldung ohne Welt - ohne sie kann"
                    + " kein Moderator hinspringen, und das Hinspringen ist der Zweck"
                    + " des Ortes");
        }
        welt = welt.trim().toLowerCase(Locale.ROOT);
        if (gemeldetAm == null) {
            throw new IllegalArgumentException("Eine Meldung ohne Zeitpunkt");
        }
        if (stand == null) {
            throw new IllegalArgumentException("Eine Meldung ohne Stand");
        }
        vermerk = vermerk == null ? ""
                : Klartext.geprueft(vermerk, LAENGSTER_VERMERK, "Vermerk");
        if (stand == Meldestand.OFFEN && bearbeiter != null) {
            throw new IllegalArgumentException("Eine OFFENE Meldung hat keinen"
                    + " Bearbeiter - wer sie genommen hat, steht IN_ARBEIT");
        }
        if (stand != Meldestand.OFFEN && bearbeiter == null) {
            throw new IllegalArgumentException("Der Stand " + stand + " braucht einen"
                    + " Bearbeiter. Ohne ihn ist nicht zu sagen, wer angesprochen werden"
                    + " kann, wenn der Melder nachfragt");
        }
        if (stand.beantwortet() && erledigtAm == null) {
            throw new IllegalArgumentException("Der Stand " + stand + " braucht einen"
                    + " Erledigungszeitpunkt - er ist die Antwort auf die Frage, wie"
                    + " lange eine Meldung gelegen hat");
        }
        if (!stand.beantwortet() && erledigtAm != null) {
            throw new IllegalArgumentException("Eine Meldung im Stand " + stand
                    + " ist nicht erledigt und hat keinen Erledigungszeitpunkt");
        }
        if (!stand.beantwortet() && gelesen) {
            throw new IllegalArgumentException("Eine unbeantwortete Meldung kann nicht"
                    + " gelesen sein - es gibt noch nichts zu lesen");
        }
    }

    /** Wer sie bearbeitet - leer, solange sie offen ist. */
    public Optional<UUID> wirdBearbeitetVon() {
        return Optional.ofNullable(bearbeiter);
    }

    /** Wann sie erledigt wurde - leer, solange sie es nicht ist. */
    public Optional<Instant> erledigt() {
        return Optional.ofNullable(erledigtAm);
    }

    /** Ob dieser Spieler sie gemeldet hat. */
    public boolean gemeldetVon(UUID wer) {
        return wer != null && melder.equals(wer);
    }

    /**
     * Ob der Melder eine Antwort hat, die er noch nicht gelesen hat.
     *
     * <p>Das ist die Frage, die beim Beitritt gestellt wird. <b>Ohne sie verschwindet die
     * Rueckmeldung</b>: Ein Moderator schliesst eine Meldung ab, waehrend der Melder
     * offline ist, und die Chatzeile geht an niemanden. Genau dann meldet er beim zweiten
     * Mal nicht mehr - und der Plan nennt das als den Grund, aus dem es die Rueckmeldung
     * ueberhaupt gibt.
     */
    public boolean warteteAufLesen() {
        return stand.beantwortet() && !gelesen;
    }

    /** Dieselbe Meldung, von einem Moderator genommen. */
    public Meldung inArbeitVon(UUID wer, Instant jetzt) {
        if (wer == null) {
            throw new IllegalArgumentException("Ohne Bearbeiter wird nichts genommen");
        }
        return new Meldung(nummer, melder, betroffener, grund, welt, x, y, z, gemeldetAm,
                Meldestand.IN_ARBEIT, wer, vermerk, null, false);
    }

    /**
     * Dieselbe Meldung, erledigt - mit Vermerk und Zeitpunkt.
     *
     * @param stand muss {@link Meldestand#ABGESCHLOSSEN} oder
     *              {@link Meldestand#ABGEWIESEN} sein
     */
    public Meldung erledigt(Meldestand stand, UUID wer, String vermerk, Instant jetzt) {
        if (stand == null || !stand.beantwortet()) {
            throw new IllegalArgumentException("Erledigt wird nach ABGESCHLOSSEN oder"
                    + " ABGEWIESEN, nicht nach " + stand);
        }
        if (jetzt == null) {
            throw new IllegalArgumentException("Ohne Zeitpunkt wird nichts erledigt");
        }
        // gelesen = false, auch wenn es vorher true war: Eine erneut erledigte Meldung
        // hat eine neue Antwort, und die hat der Melder nicht gelesen.
        return new Meldung(nummer, melder, betroffener, grund, welt, x, y, z, gemeldetAm,
                stand, wer, vermerk, jetzt, false);
    }

    /** Dieselbe Meldung, als gelesen vermerkt. */
    public Meldung alsGelesen() {
        if (!stand.beantwortet()) {
            throw new IllegalArgumentException("Eine unbeantwortete Meldung kann nicht"
                    + " gelesen werden - es gibt noch nichts zu lesen");
        }
        return new Meldung(nummer, melder, betroffener, grund, welt, x, y, z, gemeldetAm,
                stand, bearbeiter, vermerk, erledigtAm, true);
    }

    /**
     * Eine frische Meldung - der einzige Weg zu einer OFFENEN.
     *
     * <p>Eine Fabrikmethode und kein Konstruktoraufruf an sieben Stellen: Die Felder
     * {@code stand}, {@code bearbeiter}, {@code vermerk}, {@code erledigtAm} und
     * {@code gelesen} haben beim Anlegen genau einen erlaubten Wert, und wer sie
     * hinschreiben muss, schreibt irgendwann einen anderen hin.
     */
    public static Meldung neu(long nummer, UUID melder, UUID betroffener, String grund,
                              String welt, int x, int y, int z, Instant jetzt) {
        return new Meldung(nummer, melder, betroffener, grund, welt, x, y, z, jetzt,
                Meldestand.OFFEN, null, "", null, false);
    }
}
