package de.freimarken.kern;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Eine Strafe (Plan 12c.3, Schritt 16a Teil B) - was einem Spieler geschieht und warum.
 *
 * <p><b>Der GRUND ist kein Zusatzfeld, sondern der Zweck des Datensatzes.</b> Plan 12c.3:
 * <i>"jeweils mit Dauer und Grund, und der Grund wird dem Betroffenen angezeigt. Eine
 * Strafe, die niemand erklaert, erzeugt nur Wiederkehr."</i> Darum ist er Pflicht, darum
 * steht er in der oeffentlichen Bannliste (Plan 12c.4), und darum wird er geprueft und
 * nicht stillschweigend zurechtgebogen - was ein Moderator tippt, liest der Betroffene.
 *
 * <p><b>Ein ENDE und keine Dauer.</b> Gespeichert wird der Zeitpunkt, an dem die Strafe
 * ablaeuft, denn nur danach laesst sich fragen: <i>Wer ist jetzt gesperrt?</i> Mit einer
 * Spanne muesste jede Zeile gelesen und gerechnet werden, und die Abfrage ginge ueber die
 * ganze Tabelle. {@code null} heisst unbegrenzt - {@link Strafdauer} ist die Form, in der
 * ein Mensch darueber redet.
 *
 * <p><b>Aufgehoben wird nicht geloescht.</b> Eine aufgehobene Strafe bleibt stehen, mit
 * Zeitpunkt, Aufheber und Grund. Ohne das waere die Vorgeschichte eines Spielers eine
 * Liste, aus der sich Zeilen nehmen lassen - und genau die Zeile, die ein zweiter
 * Moderator braucht, ist die, die der erste zurueckgenommen hat.
 *
 * <p><b>Es gibt kein Feld "aufgehoben".</b> Die Wahrheit ist {@code aufgehobenAm != null},
 * und mehr als eine Wahrheit waeren zwei, die auseinanderlaufen koennen. Ein Schalter
 * neben dem Zeitpunkt haette genau einen Zweck: eines Tages {@code true} zu sein, waehrend
 * kein Zeitpunkt dasteht.
 *
 * @param nummer           die Anrede, von der Ablage vergeben; positiv
 * @param betroffener      wen es trifft
 * @param art              was geschieht
 * @param grund            warum - Pflicht, wird dem Betroffenen gezeigt
 * @param vonWem           wer sie verhaengt hat; {@code null} heisst die KONSOLE
 * @param verhaengtAm      wann - UTC, wie jede Zeit dieses Projekts
 * @param laeuftAbAm       wann sie endet; {@code null} heisst unbegrenzt. Bei
 *                         {@link Strafart#RAUSWURF} derselbe Zeitpunkt wie
 *                         {@code verhaengtAm} - ein Rauswurf ist vorbei, sobald er
 *                         geschieht
 * @param aufgehobenAm     wann sie zurueckgenommen wurde; {@code null} heisst: gar nicht
 * @param aufgehobenVon    wer sie zurueckgenommen hat; {@code null} heisst die KONSOLE
 *                         (oder: gar nicht - das sagt {@code aufgehobenAm})
 * @param aufhebungsgrund  warum zurueckgenommen; leer, solange sie gilt
 */
public record Strafe(long nummer, UUID betroffener, Strafart art, String grund,
                     UUID vonWem, Instant verhaengtAm, Instant laeuftAbAm,
                     Instant aufgehobenAm, UUID aufgehobenVon, String aufhebungsgrund) {

    /**
     * Wie kurz ein Grund sein darf.
     *
     * <p>Drei Zeichen, dieselbe Grenze wie bei einer Meldung - "hax" und "afk" sind
     * brauchbare Gruende. Eine hoehere Grenze erzieht niemanden zu besseren Begruendungen;
     * sie fuehrt dazu, dass "Beleidigungxx" dasteht.
     */
    public static final int KUERZESTER_GRUND = 3;

    /**
     * Wie lang ein Grund sein darf.
     *
     * <p>256 Zeichen, und die Grenze steht hier UND in der Datenbankspalte. Wer sie hier
     * aendert, aendert sie dort mit - sonst bricht der strict mode von MariaDB mitten im
     * Verhaengen ab, und der Moderator glaubt, der Spieler sei gesperrt.
     */
    public static final int LAENGSTER_GRUND = 256;

    /** Wie lang ein Aufhebungsgrund sein darf - dieselbe Grenze, dieselbe Spaltenlaenge. */
    public static final int LAENGSTER_AUFHEBUNGSGRUND = 256;

    public Strafe {
        if (nummer <= 0L) {
            throw new IllegalArgumentException("Eine Strafe ohne Nummer: " + nummer
                    + ". Die Nummer vergibt die Ablage, und sie ist die Anrede fuer"
                    + " /strafe aufheben - eine 0 waere eine, die niemand nennen kann");
        }
        if (betroffener == null) {
            throw new IllegalArgumentException("Eine Strafe ohne Betroffenen");
        }
        if (art == null) {
            throw new IllegalArgumentException("Eine Strafe ohne Art");
        }
        grund = Klartext.geprueft(grund, LAENGSTER_GRUND, "Grund");
        if (grund.length() < KUERZESTER_GRUND) {
            throw new IllegalArgumentException("Ein Grund braucht mindestens "
                    + KUERZESTER_GRUND + " Zeichen, nicht \"" + grund + "\". Plan 12c.3:"
                    + " Eine Strafe, die niemand erklaert, erzeugt nur Wiederkehr");
        }
        if (verhaengtAm == null) {
            throw new IllegalArgumentException("Eine Strafe ohne Zeitpunkt");
        }
        if (!art.giltFort()) {
            if (laeuftAbAm == null) {
                throw new IllegalArgumentException("Ein " + art + " ohne Ende waere ein"
                        + " Bann mit anderem Namen. Was nicht fortwirkt, endet in dem"
                        + " Augenblick, in dem es geschieht");
            }
            if (laeuftAbAm.isAfter(verhaengtAm)) {
                throw new IllegalArgumentException("Ein " + art + " mit Dauer ("
                        + verhaengtAm + " bis " + laeuftAbAm + ") ist ein Bann mit"
                        + " anderem Namen");
            }
        } else if (laeuftAbAm != null && !laeuftAbAm.isAfter(verhaengtAm)) {
            throw new IllegalArgumentException("Eine Strafe der Art " + art + " endet"
                    + " nicht, bevor sie beginnt (" + verhaengtAm + " bis " + laeuftAbAm
                    + "). Eine Dauer von null ist keine Strafe, sondern eine Zeile im"
                    + " Protokoll, die nie gewirkt hat");
        }
        aufhebungsgrund = aufhebungsgrund == null ? ""
                : Klartext.geprueft(aufhebungsgrund, LAENGSTER_AUFHEBUNGSGRUND,
                        "Aufhebungsgrund");
        if (aufgehobenAm != null) {
            if (!art.giltFort()) {
                throw new IllegalArgumentException("Ein " + art + " laesst sich nicht"
                        + " aufheben - er ist vorbei. Aufheben kann man nur, was noch"
                        + " wirkt");
            }
            if (aufgehobenAm.isBefore(verhaengtAm)) {
                throw new IllegalArgumentException("Aufgehoben (" + aufgehobenAm
                        + "), bevor sie verhaengt wurde (" + verhaengtAm + ")");
            }
            if (aufhebungsgrund.length() < KUERZESTER_GRUND) {
                throw new IllegalArgumentException("Eine Aufhebung ohne Grund. Wer"
                        + " entsperrt, sagt warum - sonst steht in der Vorgeschichte"
                        + " eine zurueckgenommene Strafe, und niemand weiss, ob der"
                        + " Vorwurf falsch war oder der Moderator nachgegeben hat");
            }
        } else if (!aufhebungsgrund.isEmpty()) {
            throw new IllegalArgumentException("Ein Aufhebungsgrund (\""
                    + aufhebungsgrund + "\") ohne Aufhebung");
        }
        if (aufgehobenAm == null && aufgehobenVon != null) {
            throw new IllegalArgumentException("Ein Aufheber ohne Aufhebung: "
                    + aufgehobenVon);
        }
    }

    /** Wer sie verhaengt hat - leer heisst: die Konsole. */
    public Optional<UUID> verhaengtVon() {
        return Optional.ofNullable(vonWem);
    }

    /**
     * Wann sie endet - leer heisst: <b>nie</b>.
     *
     * <p>Dieselbe Festlegung wie bei {@link Strafdauer#endeAb}: Das leere
     * {@link Optional} bedeutet hier kein Nichtwissen, sondern eine Strafe ohne Ende.
     */
    public Optional<Instant> endet() {
        return Optional.ofNullable(laeuftAbAm);
    }

    /** Wann sie zurueckgenommen wurde - leer, solange sie steht. */
    public Optional<Instant> aufhebung() {
        return Optional.ofNullable(aufgehobenAm);
    }

    /** Wer sie zurueckgenommen hat - leer heisst: die Konsole oder gar niemand. */
    public Optional<UUID> aufhebungDurch() {
        return Optional.ofNullable(aufgehobenVon);
    }

    /** Ob sie zurueckgenommen wurde. */
    public boolean istAufgehoben() {
        return aufgehobenAm != null;
    }

    /** Ob sie kein Ende hat. */
    public boolean unbegrenzt() {
        return art.giltFort() && laeuftAbAm == null;
    }

    /**
     * Ob diese Strafe in diesem Augenblick wirkt.
     *
     * <p><b>Drei Fragen in einer</b>, und alle drei muessen hier stehen und nicht an den
     * Aufrufstellen: Wirkt die Art ueberhaupt fort, ist sie nicht aufgehoben, und ist ihr
     * Ende noch nicht erreicht. Eine Wache, die nur das Ende prueft, sperrt einen
     * entsperrten Spieler aus; eine, die nur die Aufhebung prueft, sperrt ihn fuer immer.
     *
     * <p>Der Vergleich ist {@code isAfter} und nicht {@code !isBefore}: Eine Strafe, die
     * in diesem Augenblick ablaeuft, ist abgelaufen. Beim Grenzfall entscheidet die
     * Richtung zugunsten des Betroffenen - er hat sie abgesessen.
     */
    public boolean giltNoch(Instant jetzt) {
        if (jetzt == null) {
            throw new IllegalArgumentException("Ohne Zeitpunkt ist nicht zu sagen, was"
                    + " noch gilt");
        }
        return art.giltFort() && aufgehobenAm == null
                && (laeuftAbAm == null || laeuftAbAm.isAfter(jetzt));
    }

    /**
     * Wie lange sie gilt - als Dauer, zum Anzeigen.
     *
     * <p>Zum Vergleichen mit der Hoechstdauer taugt sie NICHT: Siehe
     * {@link Strafdauer#zwischen}. Dafuer nimmt man die Dauer, die der Moderator getippt
     * hat.
     */
    public Strafdauer dauer() {
        return Strafdauer.zwischen(verhaengtAm, laeuftAbAm);
    }

    /**
     * Wie viele Sekunden sie noch gilt - 0, wenn sie nicht mehr gilt.
     *
     * <p>Fuer die Zeile, die der Betroffene liest. Bei einer unbegrenzten Strafe ist die
     * Antwort {@link Long#MAX_VALUE}, und der Aufrufer fragt vorher
     * {@link #unbegrenzt()} - eine Zahl waere dort eine Zeitangabe, die ihn hoffen laesst.
     */
    public long restSekunden(Instant jetzt) {
        if (!giltNoch(jetzt)) {
            return 0L;
        }
        if (laeuftAbAm == null) {
            return Long.MAX_VALUE;
        }
        return Math.max(0L, laeuftAbAm.getEpochSecond() - jetzt.getEpochSecond());
    }

    /**
     * Dieselbe Strafe, zurueckgenommen.
     *
     * @param wer   wer sie zurueckgenommen hat; {@code null} heisst die Konsole
     * @param grund warum - Pflicht, siehe den Konstruktor
     */
    public Strafe aufgehoben(UUID wer, String grund, Instant jetzt) {
        if (jetzt == null) {
            throw new IllegalArgumentException("Ohne Zeitpunkt wird nichts aufgehoben");
        }
        return new Strafe(nummer, betroffener, art, this.grund, vonWem, verhaengtAm,
                laeuftAbAm, jetzt, wer, grund);
    }

    /**
     * Eine frische Strafe - der einzige Weg zu einer, die noch gilt.
     *
     * <p>Eine Fabrikmethode und kein Konstruktoraufruf an sechs Stellen: Die Felder der
     * Aufhebung haben beim Verhaengen genau einen erlaubten Wert, und wer sie hinschreiben
     * muss, schreibt irgendwann einen anderen hin. Und das ENDE wird hier gerechnet - aus
     * der Dauer, die der Moderator getippt hat.
     *
     * @param dauer wie lange; <b>{@code null} genau dann, wenn die Art nicht
     *              fortwirkt</b>. Beides wird geprueft, in beide Richtungen: Eine Dauer
     *              fuer einen Rauswurf ist genauso falsch wie ein Bann ohne Dauer, und
     *              von den zwei Fehlern faellt der zweite sonst nie auf - er ergaebe
     *              stillschweigend einen dauerhaften Bann
     */
    public static Strafe neu(long nummer, UUID betroffener, Strafart art, String grund,
                             UUID vonWem, Instant verhaengtAm, Strafdauer dauer) {
        if (art == null) {
            throw new IllegalArgumentException("Eine Strafe ohne Art");
        }
        if (verhaengtAm == null) {
            throw new IllegalArgumentException("Eine Strafe ohne Zeitpunkt");
        }
        if (art.brauchtDauer() && dauer == null) {
            throw new IllegalArgumentException("Die Art " + art + " braucht eine Dauer."
                    + " Ohne sie entstuende stillschweigend eine unbegrenzte Strafe -"
                    + " und die braucht nach Plan 12c.2 ein eigenes Recht");
        }
        if (!art.brauchtDauer() && dauer != null) {
            throw new IllegalArgumentException("Die Art " + art + " hat keine Dauer, es"
                    + " wurde aber " + dauer + " angegeben. Ein " + art + " mit Dauer"
                    + " waere ein Bann mit anderem Namen");
        }
        Instant ende = art.brauchtDauer()
                ? dauer.endeAb(verhaengtAm).orElse(null)
                : verhaengtAm;
        return new Strafe(nummer, betroffener, art, grund, vonWem, verhaengtAm, ende,
                null, null, "");
    }

    /** Ob aus diesem Grund eine Strafe werden kann - fuer die Stelle VOR dem Anlegen. */
    public static boolean brauchbarerGrund(String grund) {
        return Klartext.brauchbar(grund, KUERZESTER_GRUND, LAENGSTER_GRUND);
    }
}
