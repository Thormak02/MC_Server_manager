package de.freimarken.kern;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Der Angelwettbewerb in Altfurt - anmelden, Punkte zaehlen, abrechnen (Plan 14).
 *
 * <p>Reine Rechnerei ohne Bukkit: Die Uhr kommt als {@link Instant} herein, der Fang als
 * Gegenstandsname. Dadurch ist jeder Zweig ohne laufenden Server durchlaufbar - auch der
 * teuerste, die Abrechnung.
 *
 * <p><b>ZWEI RIEGEL, und beide liegen in der Ablage</b>, nicht hier:
 * <ul>
 *   <li>Die ANMELDUNG gibt {@code false}, wenn sie schon dastand. Erst danach wird
 *       abgebucht - zwei Klicks kosten also nur einmal Startgeld.</li>
 *   <li>Der ABSCHLUSS gibt {@code false}, wenn der Lauf schon abgerechnet war. Das ist
 *       der wichtigere: Ohne ihn zahlte jeder weitere Takt denselben Topf erneut aus, und
 *       das ist eine Geldquelle, die mit jedem Takt waechst.</li>
 * </ul>
 * Beide sind FRAGEN an die Ablage und keine Lesungen davor: Ein Lesen-Pruefen-Schreiben
 * aus zwei Faeden laesst beide durch.
 *
 * <p><b>Die Reihenfolge bei der Anmeldung ist Geld ZUERST, dann die Zeile</b> - und damit
 * umgekehrt zur Lotterie. Der Grund ist derselbe, nur spiegelbildlich: Beim Los erzeugt
 * eine fehlende Zaehlung ein zweites gratis Los, also Geld. Hier erzeugt eine Zeile ohne
 * Zahlung einen Teilnehmer, der nichts eingezahlt hat - und der nimmt aus dem Topf, in
 * den er nichts gegeben hat. Von zwei Fehlern nimmt man den, der nichts erzeugt: ein
 * abgebuchtes Startgeld ohne Zeile kostet den Spieler seinen Einsatz, erzeugt aber kein
 * Geld. Dass er dann NICHT angemeldet ist, sagt der Bescheid.
 */
public final class Wettbewerbsbuch {

    private final Kasse kasse;
    private final Wettbewerbsablage ablage;
    private final Wirtschaftsstand wirtschaft;

    /**
     * @param kasse      das Markkonto - daraus kommt das Startgeld, dorthin der Preis
     * @param ablage     Anmeldungen und Punkte; die einzige Stelle, die beides weiss
     * @param wirtschaft der GETEILTE Stand, damit {@code /freimarken neuladen} das
     *                   Fenster erreicht - ein eigener machte das Neuladen stumm
     */
    public Wettbewerbsbuch(Kasse kasse, Wettbewerbsablage ablage,
                           Wirtschaftsstand wirtschaft) {
        if (kasse == null || ablage == null || wirtschaft == null) {
            throw new IllegalArgumentException("Der Wettbewerb braucht Kasse, Ablage und"
                    + " Wirtschaftsstand");
        }
        this.kasse = kasse;
        this.ablage = ablage;
        this.wirtschaft = wirtschaft;
    }

    /**
     * Die GELTENDEN Werte - beim Gebrauch gefragt und nicht beim Bauen gemerkt.
     *
     * <p>Sonst erreichte {@code /freimarken neuladen} das Fenster nicht: Der Wettbewerb
     * liefe dann noch zur Uhrzeit des Serverstarts.
     */
    public Wettbewerbswerte werte() {
        return wirtschaft.werte().wettbewerb();
    }

    /**
     * Die eingestellte Zone - beim Gebrauch geholt, nie gemerkt.
     *
     * <p>Oeffentlich, weil der Befehl das Ende eines Fensters als ORTSZEIT anzeigen
     * muss. Ohne diese Methode holte er sich die Zone selbst - und nahm die der JVM, denn
     * den Wirtschaftsstand hat er nicht. Dann stuende im Chat eine andere Uhrzeit, als
     * das Fenster hat.
     */
    public ZoneId zone() {
        return wirtschaft.werte().zeit().zone();
    }

    /**
     * Welcher Lauf gerade offen ist - leer, wenn keiner.
     *
     * <p>Die Zone wird beim GEBRAUCH geholt und nie in ein Feld kopiert, wie bei
     * {@link Lotteriebuch#tagVon}: Sonst hielte der Wettbewerb den Stand des
     * Serverstarts, und ein Neuladen wirkte nie.
     */
    public Optional<Wettbewerbswerte.Lauf> offenerLauf(Instant jetzt) {
        ZoneId zone = wirtschaft.werte().zeit().zone();
        return werte().laufAm(jetzt, zone);
    }

    /**
     * Der Lauf, der eben zu Ende gegangen ist - oder leer.
     *
     * <p>Fuer den Takt der Spielzeitwache: Sie fragt jede Minute, ob etwas abzurechnen
     * ist. Geantwortet wird mit dem Lauf, dessen Fenster SCHON ZU ist - gepruefte werden
     * zwei Tage, denn ein Fenster, das um 23:30 aufgeht, endet am naechsten.
     *
     * <p><b>Diese Frage steht hier und nicht in der Wache</b>, und das ist mehr als
     * Ordnung: Sie braucht die eingestellte Zone, und die holt nur der Wirtschaftsstand.
     * Eine Wache, die sich ihre Zone selbst nimmt, nahm die der JVM - und dann rechnete
     * der Wettbewerb in einer anderen Zone ab als der, in der er laeuft.
     *
     * <p>Dass derselbe Lauf bei jedem Takt erneut genannt wird, ist richtig und kein
     * Mangel: Ob er schon abgerechnet IST, entscheidet der Riegel in der Ablage, nicht
     * diese Frage. Eine zweite Antwort darauf waere eine zweite Wahrheit.
     */
    public Optional<Wettbewerbswerte.Lauf> faelligerLauf(Instant jetzt) {
        if (jetzt == null) {
            throw new IllegalArgumentException("Ohne Zeitpunkt gibt es keinen Lauf");
        }
        Wettbewerbswerte w = werte();
        if (!w.greift()) {
            return Optional.empty();
        }
        ZoneId zone = wirtschaft.werte().zeit().zone();
        LocalDate heute = Tagesgrenze.tagVon(jetzt, zone);
        for (LocalDate tag : List.of(heute, heute.minusDays(1))) {
            Optional<Wettbewerbswerte.Lauf> lauf = w.laufFuer(tag, zone);
            if (lauf.isPresent() && !jetzt.isBefore(lauf.get().ende())) {
                return lauf;
            }
        }
        return Optional.empty();
    }

    /**
     * Meldet einen Spieler zum laufenden Wettbewerb an.
     *
     * <p>Bei {@link Bescheid#ANGEMELDET} ist gezahlt und die Zeile steht. Bei
     * {@link Bescheid#SCHON_DABEI}, {@link Bescheid#KEIN_LAUF},
     * {@link Bescheid#ZU_WENIG_MARK} und {@link Bescheid#AUS} ist nichts passiert; bei
     * {@link Bescheid#GESTOERT} KANN das Startgeld abgebucht sein, ohne dass die Zeile
     * steht - der bewusste Verlust aus dem Klassenkopf, und der Bescheid sagt es.
     */
    public Entscheid meldeAn(UUID spieler, Instant jetzt) {
        if (spieler == null || jetzt == null) {
            throw new IllegalArgumentException("Ohne Spieler und Zeitpunkt gibt es keine"
                    + " Anmeldung");
        }
        Wettbewerbswerte w = werte();
        if (!w.greift()) {
            return Entscheid.nein(Bescheid.AUS);
        }
        Optional<Wettbewerbswerte.Lauf> lauf = offenerLauf(jetzt);
        if (lauf.isEmpty()) {
            return Entscheid.nein(Bescheid.KEIN_LAUF);
        }
        LocalDate schluessel = lauf.get().schluessel();
        try {
            if (ablage.zeile(spieler, schluessel).isPresent()) {
                // Die LESUNG ist fuer die Absage, nicht der Riegel - der ist meldeAn
                // selbst. Sie steht hier, damit "du bist schon dabei" ohne eine
                // Geldbewegung zu haben ist.
                return Entscheid.nein(Bescheid.SCHON_DABEI);
            }
        } catch (RuntimeException fehler) {
            return Entscheid.nein(Bescheid.GESTOERT);
        }
        long startgeld = w.startgeld();
        if (kasse.guthaben(spieler) < startgeld) {
            return new Entscheid(Bescheid.ZU_WENIG_MARK, startgeld, 0, lauf, List.of(),
                    0L);
        }
        if (!kasse.abbuchen(spieler, startgeld, Buchungsgrund.WETTBEWERB_START)) {
            // Der RIEGEL fuer das Geld - und nicht die Lesung darueber.
            return new Entscheid(Bescheid.ZU_WENIG_MARK, startgeld, 0, lauf, List.of(),
                    0L);
        }
        boolean neu;
        try {
            neu = ablage.meldeAn(spieler, schluessel, startgeld);
        } catch (RuntimeException fehler) {
            // Gezahlt, aber keine Zeile. ERSTATTET waere hier falsch: Die Ablage ist
            // gerade nicht erreichbar, also koennte die Zeile auch GESCHRIEBEN und nur
            // die Antwort verloren sein - dann waere eine Erstattung ein Teilnehmer, der
            // nichts eingezahlt hat. Der Einsatz bleibt im Topf, der Spieler erfaehrt es.
            return Entscheid.nein(Bescheid.GESTOERT);
        }
        if (!neu) {
            // Zwischen Lesung und Schreibzug ist eine zweite Anmeldung durchgekommen -
            // im Hauptfaden unmoeglich, aus zwei Faeden nicht. Das Geld ist weg und die
            // Zeile ist nicht seine: erstattet, denn hier ist die Lage EINDEUTIG.
            kasse.gutschreiben(spieler, startgeld,
                    Buchungsgrund.WETTBEWERB_ERSTATTET);
            return Entscheid.nein(Bescheid.SCHON_DABEI);
        }
        return new Entscheid(Bescheid.ANGEMELDET, startgeld, 0, lauf, List.of(), 0L);
    }

    /**
     * Zaehlt einen Fang - oder tut nichts.
     *
     * <p>Diese Methode wird bei JEDEM Fang im Hauptfaden gerufen und ist darum der eine
     * Weg, der nicht werfen darf: Ein Wurf hier liesse das Angeln selbst abbrechen. Sie
     * gibt deshalb nur, wie viele Punkte dazukamen - 0 fuer alles andere, und zwar auch
     * fuer einen Fehler der Ablage.
     *
     * @return die neuen Gesamtpunkte, oder leer, wenn nichts gezaehlt wurde
     */
    public Optional<Integer> zaehleFang(UUID spieler, Instant jetzt, String gegenstand) {
        if (spieler == null || jetzt == null || gegenstand == null) {
            return Optional.empty();
        }
        Wettbewerbswerte w = werte();
        if (!w.greift()) {
            return Optional.empty();
        }
        int punkte = w.punkteFuer(gegenstand);
        if (punkte <= 0) {
            return Optional.empty();
        }
        Optional<Wettbewerbswerte.Lauf> lauf = offenerLauf(jetzt);
        if (lauf.isEmpty()) {
            return Optional.empty();
        }
        try {
            return ablage.zaehlePunkte(spieler, lauf.get().schluessel(), punkte);
        } catch (RuntimeException fehler) {
            // STUMM, und das ist die Ausnahme von der Hausregel: Ein Wurf aus einem
            // Fangereignis nimmt dem Spieler seinen Fang. Ein verlorener Punkt ist
            // dagegen ein Punkt - kein Geld. Der Aufrufer protokolliert.
            return Optional.empty();
        }
    }

    /**
     * Rechnet einen Lauf ab - Topf bilden, Sieger ermitteln, auszahlen.
     *
     * <p>Bei {@link Bescheid#ABGERECHNET} ist ausgezahlt. Bei
     * {@link Bescheid#SCHON_ABGERECHNET} war es schon getan und NICHTS ist passiert -
     * das ist der Riegel, auf dem die ganze Abrechnung steht. Bei
     * {@link Bescheid#NIEMAND_DABEI} gab es keine Teilnehmer; dann wird auch der
     * Zuschuss nicht gezahlt, denn es gibt niemanden, der ihn bekaeme.
     *
     * <p><b>Gemerkt wird ZUERST, dann gezahlt</b>, und das ist die Spiegelung der
     * Lotterie: Von zwei Fehlern nimmt man den, der kein Geld erzeugt. Bricht es zwischen
     * Merken und Zahlung ab, bleibt ein Topf unverteilt - ein Verlust fuer die Sieger.
     * Umgekehrt zahlte jeder Takt den Topf erneut aus.
     */
    public Entscheid rechneAb(Wettbewerbswerte.Lauf lauf, Instant jetzt) {
        if (lauf == null || jetzt == null) {
            throw new IllegalArgumentException("Ohne Lauf und Zeitpunkt gibt es nichts abzurechnen");
        }
        Wettbewerbswerte w = werte();
        LocalDate schluessel = lauf.schluessel();
        List<Punktzeile> zeilen;
        try {
            if (ablage.istAbgeschlossen(schluessel)) {
                return Entscheid.nein(Bescheid.SCHON_ABGERECHNET);
            }
            zeilen = ablage.zeilen(schluessel);
        } catch (RuntimeException fehler) {
            return Entscheid.nein(Bescheid.GESTOERT);
        }
        if (zeilen.isEmpty()) {
            // KEIN Merken: Ein Lauf ohne Teilnehmer hat nichts auszuzahlen, und ein
            // Merker darauf waere eine Zeile je Tag fuer nichts. Beim naechsten Takt
            // kommt dieselbe Antwort.
            return Entscheid.nein(Bescheid.NIEMAND_DABEI);
        }
        List<Punktzeile> rangliste = Wettbewerbsregel.rangliste(zeilen);
        long topf = Wettbewerbsregel.topf(zeilen, w.zuschuss());
        Map<UUID, Long> preise =
                Wettbewerbsregel.preise(rangliste, topf, w.verteilungProzent());

        boolean neu;
        try {
            neu = ablage.merkeAbgeschlossen(schluessel, jetzt);
        } catch (RuntimeException fehler) {
            return Entscheid.nein(Bescheid.GESTOERT);
        }
        if (!neu) {
            // Ein zweiter Takt war schneller. NICHTS auszahlen - genau dafuer ist der
            // Riegel da.
            return Entscheid.nein(Bescheid.SCHON_ABGERECHNET);
        }

        List<Gewinner> gewinner = new ArrayList<>();
        for (int platz = 0; platz < rangliste.size(); platz++) {
            Punktzeile zeile = rangliste.get(platz);
            Long preis = preise.get(zeile.spieler());
            if (preis == null) {
                continue;
            }
            try {
                kasse.gutschreiben(zeile.spieler(), preis,
                        Buchungsgrund.WETTBEWERB_PREIS);
                gewinner.add(new Gewinner(zeile.spieler(), platz + 1, zeile.punkte(),
                        preis));
            } catch (RuntimeException fehler) {
                // JE SIEGER getrennt: Ein Konto, das nicht erreichbar ist, darf die
                // uebrigen Preise nicht mitnehmen. Wer fehlt, fehlt in der Liste - und
                // der Aufrufer protokolliert die Luecke.
                gewinner.add(new Gewinner(zeile.spieler(), platz + 1, zeile.punkte(),
                        0L));
            }
        }
        return new Entscheid(Bescheid.ABGERECHNET, 0L, zeilen.size(),
                Optional.of(lauf), List.copyOf(gewinner), topf);
    }

    /** Der Stand eines Spielers im laufenden Wettbewerb - fuer die Auskunft. */
    public Auskunft frage(UUID spieler, Instant jetzt) {
        if (spieler == null || jetzt == null) {
            throw new IllegalArgumentException("Ohne Spieler und Zeitpunkt gibt es keine"
                    + " Auskunft");
        }
        Wettbewerbswerte w = werte();
        Optional<Wettbewerbswerte.Lauf> lauf = offenerLauf(jetzt);
        if (lauf.isEmpty()) {
            return new Auskunft(w.greift(), Optional.empty(), Optional.empty(),
                    List.of(), 0L, w.startgeld(), kasse.guthaben(spieler));
        }
        LocalDate schluessel = lauf.get().schluessel();
        List<Punktzeile> zeilen = ablage.zeilen(schluessel);
        return new Auskunft(w.greift(), lauf,
                ablage.zeile(spieler, schluessel),
                Wettbewerbsregel.rangliste(zeilen),
                Wettbewerbsregel.topf(zeilen, w.zuschuss()), w.startgeld(),
                kasse.guthaben(spieler));
    }

    /** Wie eine Tat ausgegangen ist. */
    public enum Bescheid {

        /** Gezahlt und angemeldet. */
        ANGEMELDET,

        /** Er war schon dabei - es ist nichts passiert. */
        SCHON_DABEI,

        /** Gerade laeuft kein Wettbewerb. */
        KEIN_LAUF,

        /** Das Startgeld reicht nicht. */
        ZU_WENIG_MARK,

        /** Der Wettbewerb laeuft gar nicht. */
        AUS,

        /** Abgerechnet und ausgezahlt. */
        ABGERECHNET,

        /** Dieser Lauf war schon abgerechnet - der Riegel hat gehalten. */
        SCHON_ABGERECHNET,

        /** Niemand war dabei; es gibt nichts zu verteilen. */
        NIEMAND_DABEI,

        /**
         * Eine Ablage oder eine Kasse hat geworfen.
         *
         * <p>Bei der Anmeldung KANN das Startgeld dabei abgebucht sein, ohne dass die
         * Zeile steht - siehe den Klassenkopf.
         */
        GESTOERT
    }

    /**
     * Ein Sieger mit seinem Platz, seinen Punkten und seinem Preis.
     *
     * @param preis was er bekommen hat; 0, wenn seine Kasse nicht erreichbar war
     */
    public record Gewinner(UUID spieler, int platz, int punkte, long preis) {

        public Gewinner {
            if (spieler == null) {
                throw new IllegalArgumentException("Ein Gewinner ohne Spieler ist keiner");
            }
            if (platz < 1) {
                throw new IllegalArgumentException("Der erste Platz ist 1, nicht "
                        + platz);
            }
            if (punkte < 0 || preis < 0L) {
                throw new IllegalArgumentException("Weder Punkte noch Preis koennen"
                        + " negativ sein: " + punkte + " / " + preis);
            }
        }
    }

    /**
     * Was gerade laeuft - ohne etwas zu tun.
     *
     * @param laeuft     ob der Wettbewerb ueberhaupt etwas tut
     * @param lauf       der offene Lauf, oder leer
     * @param eigene     die eigene Zeile, oder leer, wenn nicht angemeldet
     * @param rangliste  alle Teilnehmer, sortiert
     * @param topf       was im Topf liegt
     * @param startgeld  was die Teilnahme kostet
     * @param guthaben   was der Spieler hat - fuer die Absage. "Es kostet 250, du hast
     *                   100" ist eine Antwort, "zu wenig" ist keine
     */
    public record Auskunft(boolean laeuft, Optional<Wettbewerbswerte.Lauf> lauf,
                           Optional<Punktzeile> eigene, List<Punktzeile> rangliste,
                           long topf, long startgeld, long guthaben) {


        /** Ob dieser Spieler mitmacht. */
        public boolean dabei() {
            return eigene.isPresent();
        }
    }

    /**
     * Wie eine Tat ausgegangen ist, mit allem, was die Meldung braucht.
     *
     * @param bescheid    der Ausgang
     * @param startgeld   was gezahlt wurde beziehungsweise gekostet haette
     * @param teilnehmer  wie viele dabei waren - nur bei der Abrechnung
     * @param lauf        welcher Lauf, oder leer
     * @param gewinner    wer was bekommen hat - nur bei der Abrechnung
     * @param topf        was verteilt wurde - nur bei der Abrechnung
     */
    public record Entscheid(Bescheid bescheid, long startgeld, int teilnehmer,
                            Optional<Wettbewerbswerte.Lauf> lauf, List<Gewinner> gewinner,
                            long topf) {

        public Entscheid {
            if (bescheid == null) {
                throw new IllegalArgumentException("Ohne Bescheid ist nichts entschieden");
            }
            if (lauf == null) {
                throw new IllegalArgumentException("Kein Lauf ist Optional.empty und"
                        + " nicht null");
            }
            if (gewinner == null) {
                throw new IllegalArgumentException("Keine Gewinner sind eine leere Liste"
                        + " und nicht null");
            }
            if (startgeld < 0L || teilnehmer < 0 || topf < 0L) {
                throw new IllegalArgumentException("Startgeld, Teilnehmerzahl und Topf"
                        + " koennen nicht negativ sein: " + startgeld + " / "
                        + teilnehmer + " / " + topf);
            }
            if (bescheid == Bescheid.ABGERECHNET && teilnehmer < 1) {
                throw new IllegalArgumentException("Ein abgerechneter Lauf hat"
                        + " Teilnehmer - sonst waere es NIEMAND_DABEI.");
            }
            gewinner = List.copyOf(gewinner);
        }

        static Entscheid nein(Bescheid bescheid) {
            return new Entscheid(bescheid, 0L, 0, Optional.empty(), List.of(), 0L);
        }

    }
}
