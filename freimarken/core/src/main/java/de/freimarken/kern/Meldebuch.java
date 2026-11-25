package de.freimarken.kern;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Wer melden darf, wer bearbeiten darf, und was der Melder davon erfaehrt
 * (Plan 12c.3, Schritt 16a Teil C).
 *
 * <p><b>Die tragende Regel: der Melder bekommt eine Antwort, auch wenn er nicht da
 * ist.</b> Der Plan nennt sie als den Grund, aus dem das Meldewesen ueberhaupt
 * funktioniert - <i>"sonst meldet beim zweiten Mal niemand mehr"</i>. Gebaut ist das
 * nicht als Chatzeile beim Abschliessen, sondern als ZUSTAND an der Meldung
 * ({@link Meldung#gelesen()}): Ein Moderator erledigt eine Meldung um drei Uhr nachts,
 * und der Melder liest sie beim naechsten Beitritt. Eine Zeile, die an einen Abwesenden
 * geht, geht an niemanden - und niemand merkt es.
 *
 * <p><b>Drei Bremsen, und alle drei gelten dem Streitfall, nicht dem Angreifer:</b>
 * <ul>
 *   <li>Niemand meldet sich selbst - das prueft {@link Meldung} schon im Konstruktor.</li>
 *   <li>Eine Abklingzeit zwischen zwei Meldungen desselben Melders.</li>
 *   <li>Eine Hoechstzahl UNERLEDIGTER Meldungen je Melder. Die vierte wird nicht
 *       verweigert, sondern verschoben: Sie geht, sobald eine der drei bearbeitet ist,
 *       und genau das liest der Melder.</li>
 * </ul>
 * Keine dieser Bremsen zaehlt, wie oft jemand DENSELBEN gemeldet hat. Das ist Absicht:
 * Wer dreimal denselben meldet, hat vielleicht dreimal etwas gesehen, und ein Riegel
 * darauf waere der, der den Wiederholungstaeter schuetzt.
 *
 * <p><b>Was dieses Buch NICHT tut: strafen.</b> Eine Meldung bearbeiten heisst, sie
 * anzusehen und zu beantworten. Was der Moderator dabei TUT - stummschalten, bannen,
 * zuruecksetzen - ist eine eigene Tat mit eigenem Protokoll (Teil B). Eine Verbindung
 * waere in beide Richtungen unvollstaendig: Eine Strafe kann ohne Meldung fallen, und
 * eine Meldung kann ohne Strafe enden.
 *
 * <p>Die RECHTE kommen als Menge herein und nicht als Spieler - dieselbe Bauart wie
 * {@link Teambuch}: So laeuft jede Rechtelage ohne Server durch eine Probe.
 */
public final class Meldebuch {

    /** Was aus einem Versuch wurde. */
    public enum Bescheid {
        /** Die Meldung ist angelegt. */
        ANGELEGT,
        /** Auf diesem Server wird nicht gemeldet. */
        AUS,
        /** Der Melder muss noch warten. */
        ABKLINGZEIT,
        /** Er hat schon so viele unerledigte Meldungen, wie erlaubt sind. */
        ZU_VIELE_OFFENE,
        /** Niemand meldet sich selbst. */
        SELBST,
        /** Der Grund ist zu kurz, zu lang oder enthaelt Steuerzeichen. */
        GRUND_UNBRAUCHBAR,
        /** Genommen - der Bearbeiter steht jetzt daran. */
        GENOMMEN,
        /** Erledigt, mit Vermerk. */
        ERLEDIGT,
        /** Diese Nummer gibt es nicht. */
        UNBEKANNT,
        /**
         * Ein anderer war schneller.
         *
         * <p>Der Bescheid, den es ohne den Riegel in {@link Meldeablage#schreibe} nicht
         * gaebe - und dann sprechen zwei Moderatoren denselben Spieler an.
         */
        SCHON_GENOMMEN,
        /** Die Meldung ist schon erledigt - daran ist nichts mehr zu tun. */
        SCHON_ERLEDIGT,
        /** Dem Bearbeiter fehlt das Recht. */
        OHNE_RECHT,
        /** Eine Ablage hat geworfen - nichts ist passiert. */
        GESTOERT
    }

    /**
     * Das Ergebnis eines Versuchs.
     *
     * @param bescheid  was wurde
     * @param meldung   die betroffene Meldung, falls es eine gibt
     * @param wartezeit bei {@link Bescheid#ABKLINGZEIT} die restlichen Sekunden
     * @param grenze    bei {@link Bescheid#ZU_VIELE_OFFENE} die Hoechstzahl
     */
    public record Entscheid(Bescheid bescheid, Optional<Meldung> meldung,
                            long wartezeit, int grenze) {

        public Entscheid {
            if (bescheid == null) {
                throw new IllegalArgumentException("Ein Entscheid ohne Bescheid");
            }
            if (meldung == null) {
                throw new IllegalArgumentException("Die Meldung darf leer sein, aber"
                        + " nicht fehlen");
            }
            if (bescheid == Bescheid.ABKLINGZEIT && wartezeit <= 0L) {
                throw new IllegalArgumentException("ABKLINGZEIT ohne Restzeit: Dann"
                        + " liest der Melder \"warte noch 0 Sekunden\", und das ist"
                        + " keine Absage, sondern ein Fehler");
            }
            if (bescheid == Bescheid.ZU_VIELE_OFFENE && grenze <= 0) {
                throw new IllegalArgumentException("ZU_VIELE_OFFENE ohne Grenze - die"
                        + " Absage muss die Zahl nennen, sonst weiss der Melder nicht,"
                        + " wann er wieder darf");
            }
        }

        static Entscheid von(Bescheid bescheid, Meldung meldung) {
            return new Entscheid(bescheid, Optional.ofNullable(meldung), 0L, 0);
        }

        static Entscheid ohne(Bescheid bescheid) {
            return new Entscheid(bescheid, Optional.empty(), 0L, 0);
        }

        /** Ob sich dabei etwas geaendert hat - das entscheidet ueber das Protokoll. */
        public boolean geaendert() {
            return bescheid == Bescheid.ANGELEGT || bescheid == Bescheid.GENOMMEN
                    || bescheid == Bescheid.ERLEDIGT;
        }
    }

    private final Meldeablage ablage;

    /**
     * Wie das Recht zum Bearbeiten heisst - hereingegeben, nicht hier festgelegt.
     *
     * <p><b>Der Riegel bleibt hier, der NAME kommt von aussen</b>, und das ist eine
     * Messung und keine Vorliebe: {@code GrenzeTest} 31al liest die
     * {@code "freimarken.*"}-Literale des Plugin-Moduls und haelt sie gegen die
     * {@code plugin.yml}. Ein Literal hier im Kern sieht sie NICHT - und dann steht
     * in der plugin.yml ein angemeldetes Recht, das nach ihrer Rechnung kein Zweig
     * fragt. Gemessen am 25.11.2026, genau mit diesem Fehlerbild.
     *
     * <p>Der Kern kennt damit keinen Bukkit-Rechtenamen - dieselbe Linie wie
     * {@link Teambuch}, das nur die Vorsilbe kennt und sonst mit Mengen arbeitet.
     */
    private final String rechtBearbeiten;

    /**
     * Die Werte - als Lieferant und nicht als Wert.
     *
     * <p>Die {@code config.yml} laesst sich im Betrieb neu laden, und ein hier
     * festgehaltenes Exemplar waere nach dem ersten Neuladen die alte Datei. Dieselbe
     * Bauart wie bei {@link Teambuch} und {@code Lotteriebuch}.
     */
    private final Supplier<Meldewerte> werte;

    /**
     * @param rechtBearbeiten der Name des Rechtes zum Bearbeiten, mit Vorsilbe -
     *                        siehe das Feld. Er muss brauchbar sein: Ein leerer
     *                        Name liesse {@code darfBearbeiten} fuer JEDE Menge
     *                        wahr werden, sobald sie ihn enthaelt - und das tut
     *                        keine, also waere der Riegel stumm immer zu.
     */
    public Meldebuch(Meldeablage ablage, Supplier<Meldewerte> werte,
                     String rechtBearbeiten) {
        if (ablage == null || werte == null) {
            throw new IllegalArgumentException("Das Meldebuch braucht die Ablage und die"
                    + " Werte");
        }
        if (rechtBearbeiten == null || rechtBearbeiten.isBlank()) {
            throw new IllegalArgumentException("Das Meldebuch braucht den Namen des"
                    + " Rechtes zum Bearbeiten. Ohne ihn waere der Riegel stumm"
                    + " immer zu - keine Rechtemenge enthaelt einen leeren Namen.");
        }
        this.ablage = ablage;
        this.werte = werte;
        this.rechtBearbeiten = rechtBearbeiten;
    }

    /** Was gilt. */
    public Meldewerte werte() {
        Meldewerte gelesen = werte.get();
        return gelesen == null ? Meldewerte.VORGABE : gelesen;
    }

    /**
     * Legt eine Meldung an - oder sagt, warum nicht.
     *
     * <p><b>Die Reihenfolge der Fragen ist die Zusage</b>, und sie geht von der
     * billigsten zur teuersten: erst der Schalter, dann die Selbstmeldung, dann der
     * Grund, dann die Abklingzeit, dann die Zahl der offenen. Die letzten zwei kosten je
     * einen Lesezugriff; wer sich selbst melden will, soll das ohne Datenbank erfahren.
     *
     * <p>Der GRUND wird hier geprueft und nicht erst im Konstruktor: Jener wirft, und ein
     * Wurf ist fuer einen Spieler, der einen zu kurzen Grund getippt hat, die falsche
     * Antwort. Geprueft wird mit denselben Grenzen - sie stehen in {@link Meldung}, also
     * gibt es nur eine Wahrheit darueber, was brauchbar ist.
     */
    public Entscheid melde(UUID melder, UUID betroffener, String grund, String welt,
                           int x, int y, int z, Instant jetzt) {
        Meldewerte gueltig = werte();
        if (!gueltig.greift()) {
            return Entscheid.ohne(Bescheid.AUS);
        }
        if (melder == null || betroffener == null) {
            throw new IllegalArgumentException("Ohne Melder und Betroffenen gibt es"
                    + " keine Meldung");
        }
        if (melder.equals(betroffener)) {
            return Entscheid.ohne(Bescheid.SELBST);
        }
        if (!brauchbarerGrund(grund)) {
            return Entscheid.ohne(Bescheid.GRUND_UNBRAUCHBAR);
        }
        try {
            long rest = restzeit(melder, gueltig, jetzt);
            if (rest > 0L) {
                return new Entscheid(Bescheid.ABKLINGZEIT, Optional.empty(), rest, 0);
            }
            int offene = ablage.offeneVon(melder);
            if (offene >= gueltig.hoechsteOffeneJeMelder()) {
                return new Entscheid(Bescheid.ZU_VIELE_OFFENE, Optional.empty(), 0L,
                        gueltig.hoechsteOffeneJeMelder());
            }
            return Entscheid.von(Bescheid.ANGELEGT,
                    ablage.lege(melder, betroffener, grund, welt, x, y, z, jetzt));
        } catch (IllegalStateException gestoert) {
            // Durchgereicht als Bescheid und nicht als Wurf: Der Aufrufer ist ein
            // Befehl, und ein Wurf dort ist eine rote englische Zeile fuer eine Lage,
            // die der Spieler versteht, sobald sie auf Deutsch dasteht.
            return Entscheid.ohne(Bescheid.GESTOERT);
        }
    }

    /**
     * Nimmt eine Meldung in Arbeit.
     *
     * <p><b>Der Riegel gegen zwei Moderatoren liegt in der Ablage</b>
     * ({@link Meldeablage#schreibe} mit erwartetem Stand), nicht hier: Zwischen dem Lesen
     * und dem Schreiben liegt ein Augenblick, und in dem kann ein anderer schneller sein.
     * Eine Pruefung hier waere eine, die genau diesen Augenblick nicht sieht.
     */
    public Entscheid nimm(long nummer, UUID bearbeiter, Instant jetzt,
                          java.util.Set<String> rechte) {
        if (bearbeiter == null) {
            throw new IllegalArgumentException("Ohne Bearbeiter wird nichts genommen");
        }
        if (!darfBearbeiten(rechte)) {
            return Entscheid.ohne(Bescheid.OHNE_RECHT);
        }
        try {
            Optional<Meldung> gefunden = ablage.lies(nummer);
            if (gefunden.isEmpty()) {
                return Entscheid.ohne(Bescheid.UNBEKANNT);
            }
            Meldung eine = gefunden.get();
            if (eine.stand().beantwortet()) {
                return Entscheid.von(Bescheid.SCHON_ERLEDIGT, eine);
            }
            if (eine.stand() == Meldestand.IN_ARBEIT) {
                return Entscheid.von(Bescheid.SCHON_GENOMMEN, eine);
            }
            Meldung genommen = eine.inArbeitVon(bearbeiter, jetzt);
            if (!ablage.schreibe(genommen, Meldestand.OFFEN)) {
                // In diesem Augenblick war ein anderer schneller. Neu gelesen, damit die
                // Antwort NENNEN kann, wer sie hat - ein blosses "zu spaet" liesse den
                // Moderator ratlos.
                return Entscheid.von(Bescheid.SCHON_GENOMMEN,
                        ablage.lies(nummer).orElse(eine));
            }
            return Entscheid.von(Bescheid.GENOMMEN, genommen);
        } catch (IllegalStateException gestoert) {
            return Entscheid.ohne(Bescheid.GESTOERT);
        }
    }

    /**
     * Erledigt eine Meldung - abgeschlossen oder abgewiesen, mit Vermerk.
     *
     * <p><b>Eine OFFENE Meldung laesst sich unmittelbar erledigen</b>, ohne sie vorher zu
     * nehmen. Das ist Absicht: Die Haelfte aller Meldungen ist auf einen Blick
     * beantwortet, und ein Pflichtschritt dazwischen waere einer, den man umgeht - indem
     * man gar nicht bearbeitet.
     *
     * @param ziel {@link Meldestand#ABGESCHLOSSEN} oder {@link Meldestand#ABGEWIESEN}
     */
    public Entscheid erledige(long nummer, Meldestand ziel, UUID bearbeiter,
                              String vermerk, Instant jetzt,
                              java.util.Set<String> rechte) {
        if (ziel == null || !ziel.beantwortet()) {
            throw new IllegalArgumentException("Erledigt wird nach ABGESCHLOSSEN oder"
                    + " ABGEWIESEN, nicht nach " + ziel);
        }
        if (bearbeiter == null) {
            throw new IllegalArgumentException("Ohne Bearbeiter wird nichts erledigt");
        }
        if (!darfBearbeiten(rechte)) {
            return Entscheid.ohne(Bescheid.OHNE_RECHT);
        }
        if (vermerk != null && vermerk.trim().length() > Meldung.LAENGSTER_VERMERK) {
            return Entscheid.ohne(Bescheid.GRUND_UNBRAUCHBAR);
        }
        try {
            Optional<Meldung> gefunden = ablage.lies(nummer);
            if (gefunden.isEmpty()) {
                return Entscheid.ohne(Bescheid.UNBEKANNT);
            }
            Meldung eine = gefunden.get();
            if (eine.stand().beantwortet()) {
                return Entscheid.von(Bescheid.SCHON_ERLEDIGT, eine);
            }
            Meldung fertig = eine.erledigt(ziel, bearbeiter,
                    vermerk == null ? "" : vermerk, jetzt);
            if (!ablage.schreibe(fertig, eine.stand())) {
                return Entscheid.von(Bescheid.SCHON_GENOMMEN,
                        ablage.lies(nummer).orElse(eine));
            }
            return Entscheid.von(Bescheid.ERLEDIGT, fertig);
        } catch (IllegalStateException gestoert) {
            return Entscheid.ohne(Bescheid.GESTOERT);
        }
    }

    /**
     * Die Antworten, die dieser Spieler noch nicht gelesen hat - und sie gelten danach
     * als gelesen.
     *
     * <p><b>Gelesen wird ZUERST, geschrieben danach</b>, und die Reihenfolge ist die
     * Zusage: Bricht das Schreiben ab, liest der Spieler die Antwort beim naechsten
     * Beitritt noch einmal. Andersherum - erst als gelesen vermerken, dann senden - waere
     * eine Antwort, die niemand gesehen hat und die nie wiederkommt. Von zwei Fehlern
     * nimmt man den, der nichts verliert.
     *
     * <p>Ein Fehler beim Vermerken wird VERSCHLUCKT und nicht gemeldet: Der Spieler hat
     * seine Antwort, und eine Fehlerzeile beim Beitritt hilft ihm nicht. Der Aufrufer
     * schreibt sie ins Protokoll.
     *
     * @return die Antworten in der Reihenfolge ihres Entstehens; leer, wenn keine wartet
     */
    public List<Meldung> holeAntworten(UUID melder) {
        if (melder == null) {
            throw new IllegalArgumentException("Ohne Spieler gibt es keine Antworten");
        }
        List<Meldung> warten = ablage.ungeleseneVon(melder);
        for (Meldung eine : warten) {
            try {
                ablage.schreibe(eine.alsGelesen(), eine.stand());
            } catch (IllegalStateException | IllegalArgumentException gestoert) {
                // Siehe den Methodenkopf: Der Spieler hat seine Antwort schon. Eine
                // nicht vermerkte Antwort kommt beim naechsten Beitritt wieder, und das
                // ist die harmlose Richtung.
                break;
            }
        }
        return warten;
    }

    /** Die unerledigten Meldungen, die aeltesten zuerst - so viele wie eingestellt. */
    public List<Meldung> offene() {
        return ablage.offene(werte().zeilenJeSeite());
    }

    /** Eine einzelne Meldung - durchgereicht, damit der Aufrufer EINE Naht hat. */
    public Optional<Meldung> lies(long nummer) {
        return ablage.lies(nummer);
    }

    /**
     * Ob dieser Grund brauchbar ist - mit denselben Grenzen wie {@link Meldung}.
     *
     * <p>Oeffentlich, weil der Befehl ihn VOR dem Anlegen fragen soll: Ein Spieler, der
     * zu kurz getippt hat, soll eine Absage lesen und keinen Wurf.
     */
    public static boolean brauchbarerGrund(String grund) {
        return Klartext.brauchbar(grund, Meldung.KUERZESTER_GRUND,
                Meldung.LAENGSTER_GRUND);
    }

    /**
     * Wie viele Sekunden dieser Melder noch warten muss.
     *
     * <p>0 heisst: Er darf. Gerechnet wird AUFGERUNDET, damit nie "warte noch 0
     * Sekunden" dasteht - eine Absage, die keine ist, liest sich wie ein Fehler.
     */
    private long restzeit(UUID melder, Meldewerte gueltig, Instant jetzt) {
        if (gueltig.abklingzeitSekunden() <= 0L || jetzt == null) {
            return 0L;
        }
        Optional<Instant> letzte = ablage.letzteVon(melder);
        if (letzte.isEmpty()) {
            return 0L;
        }
        long vergangen = jetzt.getEpochSecond() - letzte.get().getEpochSecond();
        if (vergangen < 0L) {
            // Die letzte Meldung liegt in der Zukunft. Das ist eine verstellte Uhr oder
            // ein Probenwert; die sichere Richtung ist, die volle Zeit zu verlangen -
            // sonst liesse sich die Bremse mit einer Zeitverstellung aushebeln.
            return gueltig.abklingzeitSekunden();
        }
        return Math.max(0L, gueltig.abklingzeitSekunden() - vergangen);
    }

    /**
     * Ob diese Rechtemenge zum Bearbeiten reicht.
     *
     * <p>{@code null} heisst DIE KONSOLE und darf alles - dieselbe Festlegung wie bei
     * {@link Teambuch}, und aus demselben Grund: Wer an der Konsole steht, hat
     * Dateizugriff. Eine LEERE Menge ist etwas anderes, naemlich ein Spieler ohne jedes
     * Recht.
     */
    private boolean darfBearbeiten(java.util.Set<String> rechte) {
        return rechte == null || rechte.contains(rechtBearbeiten);
    }
}
