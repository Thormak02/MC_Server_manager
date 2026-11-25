package de.freimarken.kern;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Wer strafen darf, wie lange, und wen nicht (Plan 12c.3, Schritt 16a Teil B).
 *
 * <p><b>Der tragende Riegel: die DAUER ist eine Rechtefrage.</b> Plan 12c.2 gibt dem
 * Moderator das <i>zeitliche</i> Bannen und dem Verwalter das <i>dauerhafte</i>. Diese
 * Unterscheidung laesst sich nur halten, wenn beides geprueft wird - das fehlende Ende
 * UND die zu lange Dauer. Wer nur auf "unbegrenzt" prueft, hat einen Moderator, der
 * {@code 3650d} tippt und damit genau das tut, was ihm der Plan verwehrt. Beide Faenger
 * gehoeren in denselben Zweig; der dritte Zustand - eine erlaubte, befristete Dauer -
 * braucht seinen eigenen Satz.
 *
 * <p><b>Der zweite Riegel: wer im Team ist, wird nur vom Team gestraft.</b> Ohne ihn
 * bannen zwei Moderatoren im Streit einander, und wer schneller tippt, hat gewonnen. Das
 * ist die Entsprechung zu dem Riegel aus Teil A, der verhindert, dass ein Moderator sich
 * einen Komplizen zum Verwalter macht - dieselbe Gefahr aus der anderen Richtung.
 *
 * <p><b>Ob jemand im Team ist, sagt die TEAMABLAGE und nicht Bukkit.</b> Der Betroffene
 * kann offline sein, und offline hat niemand Rechte - mit einer Rechtemenge als Grundlage
 * waere jeder Abwesende ungeschuetzt, und das ist genau der Augenblick, in dem man einen
 * Kollegen bannt. Darum kommt die Antwort als {@code boolean} herein: Der Aufrufer fragt
 * die Ablage, die auch ueber Abwesende Auskunft gibt.
 *
 * <p><b>Was dieses Buch NICHT tut: wirken.</b> Es schreibt die Strafe auf und sagt, ob
 * sie gilt. Wer den Spieler hinauswirft, ihm die Zeile zeigt und ihn an der Tuer abweist,
 * ist die {@code Strafwache} im Plugin. Eine Ablage, die selbst hinauswirft, liesse sich
 * ohne Server nicht pruefen - und gepruefte Riegel sind der Sinn dieser Teilung.
 *
 * <p>Die RECHTE kommen als Menge herein und nicht als Spieler - dieselbe Bauart wie
 * {@link Teambuch} und {@link Meldebuch}: So laeuft jede Rechtelage ohne Server durch
 * eine Probe.
 */
public final class Strafbuch {

    /** Was aus einem Versuch wurde. */
    public enum Bescheid {
        /** Die Strafe steht. */
        VERHAENGT,
        /** Dem Absender fehlt das Recht fuer diese Art. */
        OHNE_RECHT,
        /**
         * Die Dauer ist zu lang oder fehlt - dafuer braucht es das Recht fuer
         * dauerhafte Strafen.
         *
         * <p>Der Bescheid, der die Unterscheidung aus Plan 12c.2 ueberhaupt erst zu
         * einer macht. Die Absage NENNT die Grenze: Wer "zu lang" liest, ohne zu
         * erfahren, was kurz genug waere, tippt als naechstes dieselbe Zahl.
         */
        OHNE_RECHT_DAUERHAFT,
        /** Der Betroffene ist selbst im Team - dafuer braucht es ein eigenes Recht. */
        OHNE_RECHT_TEAM,
        /** Niemand straft sich selbst. */
        SELBST,
        /** Der Grund ist zu kurz, zu lang oder enthaelt Steuerzeichen. */
        GRUND_UNBRAUCHBAR,
        /**
         * Gegen diesen Spieler gilt schon eine Strafe dieser Art.
         *
         * <p>Abgewiesen und nicht verlaengert: Zwei geltende Banne machen die Frage
         * <i>"wann ist er wieder frei"</i> unbeantwortbar, und ein stilles Verlaengern
         * waere eine Strafe, die der Moderator nicht gewollt hat. Der Weg steht in der
         * Absage - aufheben, dann neu verhaengen.
         */
        SCHON_GESTRAFT,
        /** Zurueckgenommen. */
        AUFGEHOBEN,
        /** Sie war schon aufgehoben - ein anderer war schneller. */
        SCHON_AUFGEHOBEN,
        /** Sie wirkt nicht fort, es gibt also nichts aufzuheben. */
        NICHT_FORTWIRKEND,
        /** Diese Nummer gibt es nicht. */
        UNBEKANNT,
        /** Eine Ablage hat geworfen - nichts ist passiert. */
        GESTOERT
    }

    /**
     * Das Ergebnis eines Versuchs.
     *
     * @param bescheid       was wurde
     * @param strafe         die betroffene Strafe, falls es eine gibt - bei
     *                       {@link Bescheid#SCHON_GESTRAFT} die, die schon gilt
     * @param grenzeStunden  bei {@link Bescheid#OHNE_RECHT_DAUERHAFT} die Hoechstdauer,
     *                       die ohne das Recht noch erlaubt waere
     */
    public record Entscheid(Bescheid bescheid, Optional<Strafe> strafe,
                            long grenzeStunden) {

        public Entscheid {
            if (bescheid == null) {
                throw new IllegalArgumentException("Ein Entscheid ohne Bescheid");
            }
            if (strafe == null) {
                throw new IllegalArgumentException("Die Strafe darf leer sein, aber"
                        + " nicht fehlen");
            }
            if (bescheid == Bescheid.SCHON_GESTRAFT && strafe.isEmpty()) {
                throw new IllegalArgumentException("SCHON_GESTRAFT ohne die geltende"
                        + " Strafe. Die Absage muss sie NENNEN - sonst weiss der"
                        + " Moderator nicht, welche Nummer er aufheben soll, und der"
                        + " Weg aus der Absage heraus ist verschlossen");
            }
            if (grenzeStunden < 0L) {
                throw new IllegalArgumentException("Eine Grenze von " + grenzeStunden
                        + " Stunden");
            }
        }

        static Entscheid von(Bescheid bescheid, Strafe strafe) {
            return new Entscheid(bescheid, Optional.ofNullable(strafe), 0L);
        }

        static Entscheid ohne(Bescheid bescheid) {
            return new Entscheid(bescheid, Optional.empty(), 0L);
        }

        /** Ob sich dabei etwas geaendert hat - das entscheidet ueber das Protokoll. */
        public boolean geaendert() {
            return bescheid == Bescheid.VERHAENGT || bescheid == Bescheid.AUFGEHOBEN;
        }
    }

    private final Strafablage ablage;

    /**
     * Die Werte - als Lieferant und nicht als Wert.
     *
     * <p>Die {@code config.yml} laesst sich im Betrieb neu laden, und ein hier
     * festgehaltenes Exemplar waere nach dem ersten Neuladen die alte Datei. Dieselbe
     * Bauart wie bei {@link Meldebuch} und {@link Teambuch}. Hier wiegt es schwerer als
     * dort: Der Wert, der sich aendert, ist die Grenze zwischen dem, was ein Moderator
     * darf, und dem, was ein Verwalter darf.
     */
    private final Supplier<Strafwerte> werte;

    /** Wie die Rechte heissen - siehe {@link Strafrechte}. */
    private final Strafrechte rechtenamen;

    public Strafbuch(Strafablage ablage, Supplier<Strafwerte> werte,
                     Strafrechte rechtenamen) {
        if (ablage == null || werte == null) {
            throw new IllegalArgumentException("Das Strafbuch braucht die Ablage und die"
                    + " Werte");
        }
        if (rechtenamen == null) {
            throw new IllegalArgumentException("Das Strafbuch braucht die Namen der"
                    + " Rechte. Ohne sie waere jeder Riegel stumm immer zu");
        }
        this.ablage = ablage;
        this.werte = werte;
        this.rechtenamen = rechtenamen;
    }

    /** Was gilt. */
    public Strafwerte werte() {
        Strafwerte gelesen = werte.get();
        return gelesen == null ? Strafwerte.VORGABE : gelesen;
    }

    /** Wie die Rechte heissen - durchgereicht, damit der Aufrufer EINE Naht hat. */
    public Strafrechte rechtenamen() {
        return rechtenamen;
    }

    /**
     * Verhaengt eine Strafe - oder sagt, warum nicht.
     *
     * <p><b>Die Reihenfolge der Fragen ist die Zusage</b>, und sie geht von der billigsten
     * zur teuersten: erst die Selbstbestrafung, dann der Grund, dann die drei
     * Rechtefragen, dann - und nur dann - ein Lesezugriff auf die geltende Strafe. Wer
     * sich selbst bestrafen will, soll das ohne Datenbank erfahren.
     *
     * <p><b>Die Rechtefragen in dieser Reihenfolge:</b> das Recht fuer die ART zuerst, weil
     * seine Absage die allgemeinste ist. Wer gar nicht bannen darf, soll nicht lesen, dass
     * ihm das Recht fuer <i>dauerhafte</i> Banne fehlt - das klaenge, als fehlte ihm nur
     * eines.
     *
     * @param betroffener       wen es trifft
     * @param betroffenerImTeam ob er selbst im Team ist - fragt die Teamablage, NICHT
     *                          Bukkit; siehe den Klassenkopf
     * @param art               was geschieht
     * @param grund             warum - Pflicht
     * @param dauer             wie lange; {@code null} genau dann, wenn die Art nicht
     *                          fortwirkt
     * @param vonWem            wer; {@code null} heisst die Konsole
     * @param rechte            seine Rechte; {@code null} heisst die Konsole und darf
     *                          alles
     * @param jetzt             der Zeitpunkt, UTC
     */
    public Entscheid verhaenge(UUID betroffener, boolean betroffenerImTeam,
                               Strafart art, String grund, Strafdauer dauer,
                               UUID vonWem, Set<String> rechte, Instant jetzt) {
        if (betroffener == null) {
            throw new IllegalArgumentException("Ohne Betroffenen gibt es keine Strafe");
        }
        if (art == null) {
            throw new IllegalArgumentException("Ohne Art gibt es keine Strafe");
        }
        if (jetzt == null) {
            throw new IllegalArgumentException("Ohne Zeitpunkt gibt es keine Strafe");
        }
        if (betroffener.equals(vonWem)) {
            return Entscheid.ohne(Bescheid.SELBST);
        }
        if (!Strafe.brauchbarerGrund(grund)) {
            return Entscheid.ohne(Bescheid.GRUND_UNBRAUCHBAR);
        }
        if (!darf(rechte, rechtenamen.fuer(art))) {
            return Entscheid.ohne(Bescheid.OHNE_RECHT);
        }
        if (betroffenerImTeam && !darf(rechte, rechtenamen.team())) {
            return Entscheid.ohne(Bescheid.OHNE_RECHT_TEAM);
        }
        Strafwerte gueltig = werte();
        if (art.brauchtDauer()) {
            if (dauer == null) {
                throw new IllegalArgumentException("Die Art " + art + " braucht eine"
                        + " Dauer - ohne sie entstuende stillschweigend eine unbegrenzte"
                        + " Strafe");
            }
            long grenze = gueltig.hoechsteStundenFuer(art);
            // BEIDE Faenger im selben Zweig: das fehlende Ende UND die zu lange Dauer.
            // Wer nur das fehlende Ende prueft, hat einen Moderator, der 3650d tippt.
            if (dauer.unbegrenzt() || dauer.stunden() > grenze) {
                if (!darf(rechte, rechtenamen.dauerhaft())) {
                    return new Entscheid(Bescheid.OHNE_RECHT_DAUERHAFT,
                            Optional.empty(), grenze);
                }
            }
        } else if (dauer != null) {
            throw new IllegalArgumentException("Die Art " + art + " hat keine Dauer, es"
                    + " wurde aber " + dauer + " angegeben");
        }
        try {
            if (art.giltFort()) {
                Optional<Strafe> schon = ablage.geltende(betroffener, art, jetzt);
                if (schon.isPresent()) {
                    return Entscheid.von(Bescheid.SCHON_GESTRAFT, schon.get());
                }
            }
            return Entscheid.von(Bescheid.VERHAENGT,
                    ablage.lege(betroffener, art, grund, vonWem, jetzt, dauer));
        } catch (IllegalStateException gestoert) {
            // Durchgereicht als Bescheid und nicht als Wurf: Der Aufrufer ist ein
            // Befehl, und ein Wurf dort ist eine rote englische Zeile fuer eine Lage,
            // die der Moderator versteht, sobald sie auf Deutsch dasteht.
            return Entscheid.ohne(Bescheid.GESTOERT);
        }
    }

    /**
     * Hebt eine Strafe auf - oder sagt, warum nicht.
     *
     * <p><b>Eine unbegrenzte Strafe aufzuheben braucht dasselbe Recht wie sie zu
     * verhaengen.</b> Sonst nimmt ein Moderator den dauerhaften Bann zurueck, den ein
     * Verwalter verhaengt hat - und die Unterscheidung aus Plan 12c.2 waere in der einen
     * Richtung gesichert und in der anderen offen. Eine BEFRISTETE Strafe darf jeder
     * aufheben, der aufheben darf: Sie laeuft ohnehin ab.
     *
     * @param grund warum - Pflicht, siehe {@link Strafe}
     */
    public Entscheid hebeAuf(long nummer, UUID wer, String grund, Instant jetzt,
                             Set<String> rechte) {
        if (jetzt == null) {
            throw new IllegalArgumentException("Ohne Zeitpunkt wird nichts aufgehoben");
        }
        if (!darf(rechte, rechtenamen.aufheben())) {
            return Entscheid.ohne(Bescheid.OHNE_RECHT);
        }
        if (!Strafe.brauchbarerGrund(grund)) {
            return Entscheid.ohne(Bescheid.GRUND_UNBRAUCHBAR);
        }
        try {
            Optional<Strafe> gefunden = ablage.lies(nummer);
            if (gefunden.isEmpty()) {
                return Entscheid.ohne(Bescheid.UNBEKANNT);
            }
            Strafe eine = gefunden.get();
            if (!eine.art().giltFort()) {
                return Entscheid.von(Bescheid.NICHT_FORTWIRKEND, eine);
            }
            if (eine.istAufgehoben()) {
                return Entscheid.von(Bescheid.SCHON_AUFGEHOBEN, eine);
            }
            if (eine.unbegrenzt() && !darf(rechte, rechtenamen.dauerhaft())) {
                return new Entscheid(Bescheid.OHNE_RECHT_DAUERHAFT,
                        Optional.of(eine), 0L);
            }
            Strafe frei = eine.aufgehoben(wer, grund, jetzt);
            if (!ablage.hebeAuf(frei)) {
                // In diesem Augenblick war ein anderer schneller. Neu gelesen, damit die
                // Antwort NENNEN kann, wer sie aufgehoben hat.
                return Entscheid.von(Bescheid.SCHON_AUFGEHOBEN,
                        ablage.lies(nummer).orElse(eine));
            }
            return Entscheid.von(Bescheid.AUFGEHOBEN, frei);
        } catch (IllegalStateException gestoert) {
            return Entscheid.ohne(Bescheid.GESTOERT);
        }
    }

    /**
     * Die Strafe dieser Art, die gegen diesen Spieler wirkt - die Frage der Wache.
     *
     * <p>Ohne Rechteprüfung: Eine Wache fragt, sie entscheidet nicht. Und sie fragt bei
     * JEDEM Beitritt und bei JEDER Chatzeile, also geht die Frage durch keinen weiteren
     * Riegel.
     *
     * <p>Eine Stoerung der Ablage wird hier NICHT verschluckt, sondern durchgereicht -
     * anders als bei {@link #verhaenge}. Der Grund ist die Richtung des Fehlers: Wer
     * nicht weiss, ob ein Spieler gebannt ist, darf ihn nicht stillschweigend
     * hereinlassen. Die Wache faengt den Wurf und entscheidet selbst, was sie dann tut -
     * das ist eine Entscheidung ueber den Serverbetrieb und keine ueber eine Strafe.
     */
    public Optional<Strafe> geltende(UUID betroffener, Strafart art, Instant jetzt) {
        return ablage.geltende(betroffener, art, jetzt);
    }

    /** Die Vorgeschichte dieses Spielers - die neuesten zuerst, so viele wie gesetzt. */
    public List<Strafe> vorgeschichte(UUID betroffener) {
        return ablage.von(betroffener, werte().zeilenJeSeite());
    }

    /**
     * Die geltenden Banne - fuer die oeffentliche Liste aus Plan 12c.4.
     *
     * <p>Nur die BANNE und nicht alle Arten: Eine oeffentliche Liste der
     * Stummgeschalteten waere ein Pranger fuer eine Strafe, die in zehn Minuten vorbei
     * ist. Der Plan nennt ausdruecklich die Bannliste.
     */
    public List<Strafe> bannliste(Instant jetzt) {
        return ablage.geltendeAlle(Strafart.BANN, werte().zeilenJeSeite(), jetzt);
    }

    /** Wie viele Strafen dieser Art dieser Spieler schon hat. */
    public int zaehle(UUID betroffener, Strafart art) {
        return ablage.zaehle(betroffener, art);
    }

    /** Eine einzelne Strafe - durchgereicht, damit der Aufrufer EINE Naht hat. */
    public Optional<Strafe> lies(long nummer) {
        return ablage.lies(nummer);
    }

    /**
     * Ob diese Rechtemenge dieses Recht hat.
     *
     * <p>{@code null} heisst DIE KONSOLE und darf alles - dieselbe Festlegung wie bei
     * {@link Teambuch} und {@link Meldebuch}. Eine LEERE Menge ist etwas anderes, naemlich
     * ein Spieler ohne jedes Recht: Der Unterschied IST der Riegel.
     */
    private static boolean darf(Set<String> rechte, String recht) {
        return rechte == null || rechte.contains(recht);
    }
}
