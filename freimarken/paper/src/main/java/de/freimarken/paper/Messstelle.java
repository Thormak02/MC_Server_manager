package de.freimarken.paper;

import de.freimarken.kern.Lebenszeichen;
import de.freimarken.kern.Messregel;
import de.freimarken.kern.Messwerte;
import de.freimarken.kern.Wirtschaftsstand;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Welche Lebenszeichen jeder anwesende Spieler im laufenden Taktschlag gegeben hat.
 *
 * <p>Die Messstelle aus Plan 8.8, Teil G. Sie sammelt und urteilt, und sie GREIFT NICHT
 * EIN: kein Verdienst wird gesperrt, keine Zeit verworfen, kein Spieler gemeldet. Was sie
 * liefert, ist eine zweite Zahl neben der Anwesenheit - damit Schritt 12 eine Rangschwelle
 * daran haengen kann, die nicht im Stehen erreichbar ist. Die Begruendung steht in
 * {@link Messregel}.
 *
 * <p>Dieselbe Bauart und derselbe Schnitt wie {@link Spielzeitbuch}: kein Bukkit, alles im
 * Hauptfaden, Zustand nur fuer die laufende Sitzung. Was von Bukkit gebraucht wird, kommt
 * durch {@link Ortsquelle} herein - genau eine Frage, genau wie
 * {@code Verdienstwache.Taktquelle}.
 *
 * <p><b>Das Bewegen wird GEMESSEN und nicht gemeldet.</b> Alle anderen Arten kommen als
 * Ereignis herein ({@link #melde}); das Bewegen nicht, denn
 * {@code PlayerMoveEvent} feuert bei jeder Kopfdrehung, und eine gewackelte Maus erzeugt
 * davon tausende, ohne den Spieler einen Block weit zu tragen. Gemessen wird deshalb der
 * ABSTAND zwischen Anfang und Ende des Schlages, und zwar erst dann, wenn der Schlag
 * abgerechnet wird - siehe {@link #nimmUndLeere}.
 *
 * <p><b>Es wird je SCHLAG gesammelt und nicht je Stunde.</b> Das Fenster ist dasselbe, das
 * das Spielzeitbuch abrechnet, und das ist kein Zufall: Ein Schlag ist ganz taetig oder gar
 * nicht, weil genau er die Sekunden traegt, die die Messung verteilt. Ein eigenes Fenster
 * hier waere ein zweiter Takt neben dem einen, den Plan 8.8 ausdruecklich als einzigen
 * zulaesst.
 */
final class Messstelle implements Spielzeitbuch.Taetigkeitsquelle {

    /**
     * Wo ein Spieler steht - als Zahlen und ohne Bukkit.
     *
     * <p>Der Weltname und nicht die Welt: Ein {@code World} waere Bukkit in einer Klasse,
     * die ohne auskommt, und verglichen wird ohnehin nur auf Gleichheit.
     */
    record Ort(String welt, double x, double y, double z) {

        Ort {
            if (welt == null) {
                throw new IllegalArgumentException("Ein Ort ohne Welt ist keiner");
            }
        }

        /** Ob dieser Ort in einer anderen Welt liegt als jener. */
        boolean andereWeltAls(Ort jener) {
            return !welt.equals(jener.welt);
        }

        /**
         * Der Abstand zu jenem Ort in Bloecken - ueber alle drei Achsen.
         *
         * <p>Die Hoehe zaehlt mit: Wer einen Aufzug acht Bloecke hochfaehrt, hat sich
         * bewegt, und wer in ein Loch faellt, auch. Ein Makro an einer Wand tut beides
         * nicht wiederholt.
         *
         * <p>Bei verschiedenen Welten ist die Zahl bedeutungslos - gleiche Koordinaten im
         * Nether und in Altfurt gaeben 0. Darum fragt {@link Messregel#kamWeitGenug} zuerst
         * nach der Welt und erst dann nach dieser Zahl.
         */
        double abstandZu(Ort jener) {
            double dx = x - jener.x;
            double dy = y - jener.y;
            double dz = z - jener.z;
            return Math.sqrt(dx * dx + dy * dy + dz * dz);
        }
    }

    /**
     * Woher der Ort eines Spielers kommt - die EINE Frage ans Spiel.
     *
     * <p>Darf {@code null} geben: Ein Spieler, der zwischen Takt und Abrechnung die Welt
     * verlaesst, hat keinen Ort mehr, und das ist kein Fehler. Ohne Ort zaehlt das Bewegen
     * nicht - die sichere Richtung.
     */
    interface Ortsquelle {
        Ort ortVon(UUID wer);
    }

    /**
     * Was jeder Spieler in diesem Schlag schon gegeben hat.
     *
     * <p>Ein {@link EnumSet} je Spieler: Gesammelt wird die VIELFALT, also genuegt ein Bit
     * je Art. Eine Zaehlung je Art waere eine Menge, und eine Menge laesst sich hochdrehen
     * (siehe {@link Messregel}).
     *
     * <p>Eine nackte {@link HashMap} genuegt, wie beim Spielzeitbuch: Es gibt nur einen
     * Faden. Ein Eintrag entsteht beim ersten Zeichen und verschwindet beim Abrechnen - die
     * Karte waechst also nicht ueber die Zahl der anwesenden Spieler hinaus.
     */
    private final Map<UUID, Set<Lebenszeichen>> zeichen = new HashMap<>();

    /**
     * Wo jeder Spieler am ANFANG des laufenden Schlages stand.
     *
     * <p>Gesetzt beim Abrechnen und nicht beim Beitritt: So gibt es genau eine Stelle, die
     * sie setzt, und sie ist dieselbe, die sie liest. Der erste Schlag nach dem Beitritt
     * findet darum keinen Anfang und zaehlt kein Bewegen - das kostet einen Spieler
     * hoechstens diesen einen Schlag und nur dann, wenn er darin genau ein anderes Zeichen
     * gegeben hat. Die andere Richtung - beim Beitritt setzen - braeuchte einen zweiten
     * Aufrufer und koennte ihn vergessen.
     */
    private final Map<UUID, Ort> anfaenge = new HashMap<>();

    private final Wirtschaftsstand wirtschaft;
    private final Ortsquelle orte;

    /**
     * @param wirtschaft woher die Messwerte kommen - beim GEBRAUCH gefragt und nicht in ein
     *                   Feld kopiert, damit ein Neuladen sie erreicht. Dieselbe
     *                   Entscheidung wie beim Spielzeitbuch
     */
    Messstelle(Wirtschaftsstand wirtschaft, Ortsquelle orte) {
        this.wirtschaft = wirtschaft;
        this.orte = orte;
    }

    /**
     * Ein Lebenszeichen eintragen - aus einem Ereignis des Spiels.
     *
     * <p>Nimmt JEDE Art an, auch eine, die der Betreiber nicht zaehlen laesst. Gefiltert
     * wird erst beim Urteil ({@link Messregel#zaehlende}), und das ist Absicht: Ein
     * Neuladen mitten im Schlag soll die Liste erreichen, ohne dass die schon gesammelten
     * Zeichen neu bewertet werden muessen. Ausserdem nennt der Bericht an den Betreiber
     * dann auch die Arten, die er abgeschaltet hat - sonst sieht er nicht, was er
     * wegnimmt.
     *
     * <p>{@link Lebenszeichen#BEWEGEN} darf hier NICHT hereinkommen: Es wird gemessen und
     * nicht gemeldet, siehe Klassenkopf. Ein Aufruf damit ist ein Fehler im Programm und
     * keine Fehleingabe, also wirft er.
     */
    void melde(UUID wer, Lebenszeichen art) {
        if (wer == null || art == null) {
            return;
        }
        if (art == Lebenszeichen.BEWEGEN) {
            throw new IllegalArgumentException("Das Bewegen wird aus dem Ortsabstand"
                    + " gemessen und nicht gemeldet - ein Ereignis je Kopfdrehung waere"
                    + " keine Strecke. Siehe Messstelle.nimmUndLeere.");
        }
        zeichen.computeIfAbsent(wer, unbenutzt -> EnumSet.noneOf(Lebenszeichen.class))
                .add(art);
    }

    /**
     * Ob dieser Spieler im abgelaufenen Schlag taetig war - und BEGINNT den naechsten.
     *
     * <p>EINE Methode, die fragt und zuruecksetzt, und das ist bewusst keine Trennung in
     * zwei: Zwei Aufrufe muesste jeder Aufrufer paaren, und wer das Zuruecksetzen vergisst,
     * baut eine Messstelle, in der ein einziges Zeichen den ganzen Abend taetig faerbt -
     * stumm, denn mehr Taetigkeit sieht nach einem gut gespielten Abend aus. Dieselbe
     * Begruendung wie bei der Marke des Spielzeitbuchs, die auch von genau einer Stelle
     * vorgerueckt wird.
     *
     * <p>Darum ruft nur {@code Spielzeitbuch.rechneFort} hier herein - die eine Stelle, die
     * auch die Marke vorrueckt. Die Anzeige fragt {@link #bericht} und veraendert nichts.
     *
     * <p>Aufgerufen wird AUCH fuer einen Schlag, der gar nicht zaehlt (ein untaetiger
     * Ausgang, ein Schlag unter einer Sekunde). Das ist notwendig: Sonst truege ein Zeichen
     * aus einem verworfenen Schlag in den naechsten hinein, und die Untaetigkeitsgrenze aus
     * Teil C waere ueber diesen Weg zu umgehen.
     */
    @Override
    public boolean nimmUndLeere(UUID wer) {
        Set<Lebenszeichen> gegeben = zeichen.remove(wer);
        if (gegeben == null) {
            gegeben = EnumSet.noneOf(Lebenszeichen.class);
        }
        Ort jetzt = orte.ortVon(wer);
        Ort vorher = jetzt == null ? anfaenge.remove(wer) : anfaenge.put(wer, jetzt);
        if (jetzt != null && vorher != null && Messregel.kamWeitGenug(
                jetzt.andereWeltAls(vorher), jetzt.abstandZu(vorher), werte())) {
            gegeben.add(Lebenszeichen.BEWEGEN);
        }
        return Messregel.taetig(gegeben, werte());
    }

    /**
     * Was dieser Spieler im LAUFENDEN Schlag bisher gegeben hat - fuer den Bericht.
     *
     * <p>Veraendert nichts: kein Leeren, kein Ort, kein Urteil ueber das Bewegen. Das
     * Bewegen fehlt darin mit Absicht, denn es entsteht erst beim Abrechnen; der Bericht
     * sagt das mit seiner eigenen Zeile und behauptet nicht, der Spieler habe sich nicht
     * bewegt.
     *
     * <p>Die Reihenfolge ist die der Aufzaehlung {@link Lebenszeichen} und nicht die, in
     * der die Zeichen kamen: Zwei Spieler nebeneinander im Bericht sollen vergleichbar
     * sein.
     */
    Set<Lebenszeichen> bericht(UUID wer) {
        Set<Lebenszeichen> gegeben = zeichen.get(wer);
        if (gegeben == null) {
            return Set.of();
        }
        Set<Lebenszeichen> geordnet = new LinkedHashSet<>();
        for (Lebenszeichen art : Lebenszeichen.values()) {
            if (gegeben.contains(art)) {
                geordnet.add(art);
            }
        }
        return java.util.Collections.unmodifiableSet(geordnet);
    }

    /**
     * Wie viele der bisher gegebenen Zeichen ZAEHLEN - ohne das Bewegen.
     *
     * <p>Fuer den Bericht: "eins von zwei" sagt dem Betreiber, wie knapp es ist. Ohne das
     * Bewegen und darum eine Untergrenze - das steht im Satz des Berichts.
     */
    int zaehlendeBisher(UUID wer) {
        return Messregel.zaehlende(bericht(wer), werte());
    }

    /**
     * Vergisst einen Spieler - beim Abmelden.
     *
     * <p>Muss sein, und zwar aus demselben Grund wie beim Spielzeitbuch: Ein Ort, der eine
     * Sitzung ueberlebt, waere beim naechsten Beitritt der Anfang eines Schlages, der nie
     * stattgefunden hat - und zwischen zwei Sitzungen liegt beliebig viel Strecke. Das
     * Bewegen zaehlte dann einmal geschenkt.
     *
     * <p>Gerufen wird das NACH dem Abrechnen des Abgangs, nicht davor: Der letzte Schlag
     * eines Spielers soll noch sein Urteil bekommen.
     */
    @Override
    public void vergiss(UUID wer) {
        zeichen.remove(wer);
        anfaenge.remove(wer);
    }

    /** Wie viele Spieler gerade gesammelt werden - damit das Abraeumen pruefbar ist. */
    int beobachtete() {
        return Math.max(zeichen.size(), anfaenge.size());
    }

    private Messwerte werte() {
        return wirtschaft.werte().messung();
    }
}
