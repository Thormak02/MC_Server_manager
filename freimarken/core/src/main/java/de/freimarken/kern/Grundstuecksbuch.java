package de.freimarken.kern;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Wer ein Grundstueck zuschneiden darf, und wo.
 *
 * <p>Das dritte Buch neben {@link Besitzbuch} (Parzellen in der Marktstadt) und
 * {@link Freistadtbuch} (Staedte auf Feldern). Es ist ein eigenes aus demselben Grund,
 * aus dem jenes eines wurde: Die GEOMETRIE ist eine andere. Hier wird in Zellen
 * gerechnet, nicht in Feldern und nicht in Rasterparzellen.
 *
 * <p><b>Nichts hiervon wirft nach aussen</b> - die Lehre aus dem Besitzbuch, das seine
 * Ausnahme weiterreicht, bis sie in Brigadier landet und der Spieler eine rote Zeile
 * ohne Sinn liest. Jeder Weg hier antwortet mit einem {@link Bescheid}.
 *
 * <p><b>Kostet in Teil C2 nichts.</b> Der Stadtbesitzer teilt sein eigenes Feld, fuer
 * das er schon 400.000 Mark gezahlt hat; ein zweites Mal zu zahlen waere eine Gebuehr
 * auf den eigenen Besitz. Geld bewegt sich erst mit Teil C3, wenn ein ANDERER kauft.
 */
public final class Grundstuecksbuch {

    /** Was bei einem Zuschnitt oder einem Aufloesen herauskommt. */
    public enum Bescheid {
        /** Erteilt. */
        ERTEILT,
        /** Die Welt traegt gar keine Stadtfelder, oder die Zellen gehen nicht auf. */
        KEINE_STADTWELT,
        /** Die beiden Ecken liegen nicht im selben Stadtfeld. */
        NICHT_EIN_FELD,
        /** Auf diesem Feld steht keine Stadt. */
        KEINE_STADT,
        /** Die Stadt gehoert einem anderen - nur ihr Besitzer schneidet zu. */
        NICHT_DEINE_STADT,
        /** Die Flaeche ueberschneidet sich mit einem vorhandenen Grundstueck. */
        UEBERSCHNEIDUNG,
        /** Mehr Zellen, als die Einstellung je Grundstueck erlaubt. */
        ZU_GROSS,
        /** Hier liegt kein Grundstueck - beim Aufloesen. */
        KEIN_GRUNDSTUECK,
        /** Es gehoert einem anderen - beim Aufloesen. */
        NICHT_DEIN_GRUNDSTUECK,
        /** Angeboten oder zurueckgezogen - der Preis steht (oder steht nicht mehr). */
        ANGEBOTEN,
        /** Gekauft. Das Geld ist geflossen, das Grundstueck hat einen neuen Besitzer. */
        GEKAUFT,
        /** Dieses Grundstueck steht nicht zum Verkauf. */
        KEIN_ANGEBOT,
        /**
         * Am EIGENEN Grundstueck wird nicht gekauft.
         *
         * <p>Dieselbe Sperre wie am eigenen Kistenshop ({@code Shophandel}, Bescheid
         * EIGENER_SHOP), und hier mit einem zweiten Grund: Ein Kauf bei sich selbst
         * waere eine Gebuehrzahlung ohne Gegenleistung - bei 0 Prozent folgenlos, bei
         * 5 Prozent eine Geldvernichtung, die niemand beabsichtigt.
         */
        EIGENES_GRUNDSTUECK,
        /** Das Guthaben reicht nicht fuer den Angebotspreis. */
        ZU_WENIG_MARK,
        /** Ein negativer Preis ist keiner. */
        PREIS_UNGUELTIG,
        /** Die Ablage hat gestoert. Nichts ist geschehen. */
        GESTOERT
    }

    /**
     * Antwort auf einen Versuch.
     *
     * @param bescheid    was herausgekommen ist
     * @param grundstueck das neue oder aufgeloeste Grundstueck - nur bei
     *                    {@link Bescheid#ERTEILT}
     * @param zellen      wie viele Zellen die Flaeche umfasst. Auch bei
     *                    {@link Bescheid#ZU_GROSS} gesetzt, denn dort soll der Spieler
     *                    lesen, wie weit er darueber ist
     */
    public record Entscheid(Bescheid bescheid, Optional<Grundstueck> grundstueck,
                            int zellen, long preis, long gebuehr) {

        static Entscheid nein(Bescheid grund) {
            return new Entscheid(grund, Optional.empty(), 0, 0L, 0L);
        }

        static Entscheid nein(Bescheid grund, int zellen) {
            return new Entscheid(grund, Optional.empty(), zellen, 0L, 0L);
        }

        /**
         * Eine Absage, die den PREIS nennt.
         *
         * <p>Genau fuer {@link Bescheid#ZU_WENIG_MARK}: Der Spieler soll lesen, was
         * ihm fehlt - und das rechnet sich nur gegen den Preis, den er nicht hat.
         */
        static Entscheid nein(Bescheid grund, int zellen, long preis) {
            return new Entscheid(grund, Optional.empty(), zellen, preis, 0L);
        }

        /** Der Kurzweg fuer alles, was kein Geld bewegt. */
        static Entscheid ja(Bescheid grund, Grundstueck eines, int zellen) {
            return new Entscheid(grund, Optional.of(eines), zellen, 0L, 0L);
        }

        /**
         * NUR {@link Bescheid#ERTEILT} - der Zuschnitt und das Aufloesen.
         *
         * <p><b>Ein geglueckter KAUF ist hier falsch</b>, und das ist Absicht.
         * {@link Bescheid#GEKAUFT} und {@link Bescheid#ANGEBOTEN} sind ebenfalls
         * geglueckt, haben aber jeder eine eigene Folge: Beim Kauf erfaehrt es auch
         * der VERKAEUFER, beim Angebot niemand sonst. Ein Wort fuer alle drei hiesse,
         * dass ein Zweig die falsche Folge zieht - genau der Fehler, den FreistadtBefehle
         * am 12.11.2026 trug: Er fragte hier und schickte dem Verkaeufer darum nie
         * etwas. Wer die drei zusammenfassen will, muss vorher alle drei Folgen
         * zusammenfuehren.
         */
        public boolean erfolgreich() {
            return bescheid == Bescheid.ERTEILT;
        }
    }

    private final Stadtfeldplan plan;
    private final Feldgrenze grenze;
    private final BesitzLager lager;
    private final Grundstuecksablage ablage;

    /**
     * Die Kasse - erst mit Teil C3 gebraucht.
     *
     * <p>Teil C2 bewegte kein Geld: Der Stadtbesitzer teilt sein eigenes Feld, fuer das
     * er schon gezahlt hat. Erst der KAUF braucht zwei Konten.
     */
    private final Kasse kasse;

    /**
     * Woher die Hoechstzahl der Zellen kommt.
     *
     * <p>Wie beim {@link Freistadtbuch} darf das Feld {@code final} bleiben, weil nicht
     * der WERT eingefroren wird, sondern der Zugang zu ihm: Die Zahl wird in dem
     * Augenblick geholt, in dem sie gebraucht wird. Ein Neuladen erreicht sie damit.
     */
    private final Wirtschaftsstand wirtschaft;

    /**
     * @param grenze     die Weltgrenze der Stadtfelder - aus ihr kommt auch der Plan
     * @param lager      wo die STAEDTE liegen; daraus folgt, wem ein Feld gehoert
     * @param ablage     wo die GRUNDSTUECKE liegen
     * @throws IllegalArgumentException wenn die Zellen im Mass dieses Plans nicht
     *                                  aufgehen - dann gehoerte eine Zelle am Feldrand
     *                                  zwei Staedten, und kein Zuschnitt waere eindeutig
     */
    public Grundstuecksbuch(Feldgrenze grenze, BesitzLager lager,
                            Grundstuecksablage ablage, Kasse kasse,
                            Wirtschaftsstand wirtschaft) {
        this.grenze = grenze;
        this.plan = grenze.plan();
        // DER RIEGEL AM ENGSTEN VERBRAUCHER: Dieses Buch ist die einzige Stelle, die in
        // Zellen rechnet. Ein Plan, in dem sie nicht aufgehen, ist fuer die Welt gueltig
        // (die Barriere rechnet in Bloecken) - nur Grundstuecke kann er nicht tragen.
        // Darum steht die Pruefung hier und nicht im Konstruktor des Plans.
        if (!this.plan.zellenGehenAuf()) {
            throw new IllegalArgumentException("In diesem Mass gehen die"
                    + " Grundstueckszellen nicht auf: Feldkante " + this.plan.feldKante()
                    + ", Rasterweite " + this.plan.rasterWeite() + ", Zellkante "
                    + Stadtfeldplan.ZELLE_KANTE + ". Eine Zelle am Feldrand gehoerte"
                    + " dann zwei Staedten.");
        }
        this.lager = lager;
        this.ablage = ablage;
        this.kasse = kasse;
        this.wirtschaft = wirtschaft;
    }

    /** Die Geometrie - fuer die Befehle, die Bloecke in Zellen umrechnen. */
    public Stadtfeldplan plan() {
        return plan;
    }

    /** Wie viele Zellen ein Grundstueck hoechstens umfassen darf. */
    public int hoechstzellen() {
        return wirtschaft.werte().freistaedte().hoechstzellen();
    }

    /** Was der Staat von einem Verkauf nimmt - beim GEBRAUCH gefragt, nicht kopiert. */
    public int verkaufsgebuehrProzent() {
        return wirtschaft.werte().freistaedte().verkaufsgebuehrProzent();
    }

    /**
     * Ein Grundstueck zuschneiden - aus zwei abgesteckten Ecken.
     *
     * <p><b>Die Reihenfolge der Absagen ist nach KOSTEN sortiert</b>, wie im
     * {@link Freistadtbuch}: zuerst, was nur Rechnen ist (liegen beide Ecken im selben
     * Feld, ist die Flaeche zu gross), dann der Blick in die Staedte, zuletzt der in die
     * Grundstuecke. Wer sich verlaufen hat, soll das nicht erst erfahren, nachdem die
     * Datenbank zweimal gefragt wurde.
     *
     * @param welt    Weltschluessel der Stadtfelderwelt
     * @param spieler wer zuschneiden will
     * @param eine    die eine abgesteckte Ecke, in BLOCKkoordinaten
     * @param andere  die andere Ecke, in BLOCKkoordinaten
     */
    public Entscheid schneideZu(String welt, UUID spieler, Blockecke eine,
                                Blockecke andere) {
        if (welt == null || welt.isBlank() || spieler == null) {
            return Entscheid.nein(Bescheid.KEINE_STADTWELT);
        }
        // Beide Ecken im SELBEN Feld: Das ist die erste Frage, weil sie alles Weitere
        // erst sinnvoll macht - ein Zuschnitt ueber zwei Felder hinweg hat keinen
        // Eigentuemer, sondern zwei, und dazwischen liegt Leere, die niemandem gehoert.
        int feldX = plan.feldNummer(eine.x());
        int feldZ = plan.feldNummer(eine.z());
        if (feldX != plan.feldNummer(andere.x()) || feldZ != plan.feldNummer(andere.z())) {
            return Entscheid.nein(Bescheid.NICHT_EIN_FELD);
        }
        if (!grenze.enthaeltFeld(feldX, feldZ)
                || plan.istAnkunftsfeld(feldX, feldZ)) {
            return Entscheid.nein(Bescheid.NICHT_EIN_FELD);
        }
        Zellquader flaeche = Zellquader.zwischen(plan.zelleVon(eine.x()),
                plan.zelleVon(eine.z()), plan.zelleVon(andere.x()),
                plan.zelleVon(andere.z()));
        // Jede Zelle muss im BAULAND liegen. Bei zwei Ecken im selben Feld folgt das
        // schon aus der Geometrie - geprueft wird es trotzdem, denn die Ecken koennen in
        // der Leere stehen, und dort nennt feldNummer das Feld DAVOR.
        if (!plan.istBauzelle(flaeche.vonX(), flaeche.vonZ())
                || !plan.istBauzelle(flaeche.bisX(), flaeche.bisZ())) {
            return Entscheid.nein(Bescheid.NICHT_EIN_FELD);
        }
        int hoechstens = hoechstzellen();
        if (hoechstens > 0 && flaeche.zellen() > hoechstens) {
            return Entscheid.nein(Bescheid.ZU_GROSS, flaeche.zellen());
        }

        Optional<Parzelle> stadt;
        List<Grundstueck> vorhandene;
        try {
            stadt = lager.finde(welt, feldX, feldZ);
            if (stadt.isEmpty()) {
                return Entscheid.nein(Bescheid.KEINE_STADT, flaeche.zellen());
            }
            if (!stadt.get().gehoert(spieler)) {
                return Entscheid.nein(Bescheid.NICHT_DEINE_STADT, flaeche.zellen());
            }
            vorhandene = ablage.inStadt(stadt.get().id());
        } catch (RuntimeException fehler) {
            return Entscheid.nein(Bescheid.GESTOERT, flaeche.zellen());
        }
        for (Grundstueck eines : vorhandene) {
            if (eines.flaeche().schneidet(flaeche)) {
                return Entscheid.nein(Bescheid.UEBERSCHNEIDUNG, flaeche.zellen());
            }
        }

        try {
            // Dem STADTBESITZER, nicht niemandem: Ein Grundstueck ohne Eigentuemer waere
            // eine Flaeche, auf der niemand bauen darf - also gesperrt, bis sie verkauft
            // ist. So kann er sie bebauen, bis ein Kaeufer kommt.
            Grundstueck neu = ablage.anlegen(welt, stadt.get().id(), flaeche, spieler,
                    0L);
            return Entscheid.ja(Bescheid.ERTEILT, neu, flaeche.zellen());
        } catch (RuntimeException fehler) {
            // Nichts zurueckzunehmen: Es ist kein Geld geflossen, und geschrieben wurde
            // entweder alles oder nichts. Das ist der Vorteil daran, dass C2 nichts
            // kostet.
            return Entscheid.nein(Bescheid.GESTOERT, flaeche.zellen());
        }
    }

    /**
     * Ein Grundstueck aufloesen - die Flaeche faellt an die Stadt zurueck.
     *
     * <p>Der Besitzer des GRUNDSTUECKS loest auf, nicht der der Stadt. Sonst koennte ein
     * Stadtbesitzer ein verkauftes Grundstueck wieder einziehen, und der Kaeufer haette
     * fuer nichts gezahlt. Was mit einer Stadt geschieht, deren Grundstuecke verkauft
     * sind, entscheidet die Verfallsregel aus Plan 16 - und nicht ihr Besitzer.
     *
     * @param blockX eine Blockkoordinate auf dem Grundstueck
     */
    public Entscheid loeseAuf(String welt, UUID spieler, int blockX, int blockZ) {
        if (welt == null || welt.isBlank() || spieler == null) {
            return Entscheid.nein(Bescheid.KEINE_STADTWELT);
        }
        Optional<Grundstueck> gefunden;
        try {
            gefunden = ablage.anZelle(welt, plan.zelleVon(blockX), plan.zelleVon(blockZ));
        } catch (RuntimeException fehler) {
            return Entscheid.nein(Bescheid.GESTOERT);
        }
        if (gefunden.isEmpty()) {
            return Entscheid.nein(Bescheid.KEIN_GRUNDSTUECK);
        }
        Grundstueck eines = gefunden.get();
        if (!eines.gehoert(spieler)) {
            return Entscheid.nein(Bescheid.NICHT_DEIN_GRUNDSTUECK,
                    eines.flaeche().zellen());
        }
        try {
            ablage.entfernen(eines.id());
        } catch (RuntimeException fehler) {
            return Entscheid.nein(Bescheid.GESTOERT, eines.flaeche().zellen());
        }
        return Entscheid.ja(Bescheid.ERTEILT, eines, eines.flaeche().zellen());
    }

    /**
     * Ein Grundstueck zum Verkauf stellen - oder das Angebot zuruecknehmen.
     *
     * <p><b>Kein Preisdeckel</b>, und das ist die Entscheidung aus Plan 16 im Klartext:
     * "Der Stadtbesitzer setzt seine Preise frei; Wucher regelt sich von selbst, weil
     * daneben die Marktstadt mit gratis erster Parzelle liegt." Die einzige Grenze ist
     * die des Geldes selbst.
     *
     * <p>Eine 0 ist erlaubt und heisst ZU VERSCHENKEN - ein Stadtbesitzer, der Buerger
     * anwirbt, gibt die ersten Grundstuecke gratis. Nur {@code null} heisst "nicht zu
     * haben", und das ist der Weg zum Zuruecknehmen.
     *
     * @param angebotspreis der Preis, oder {@link Grundstueck#KEIN_ANGEBOT} zum
     *                      Zuruecknehmen
     */
    public Entscheid biete(String welt, UUID spieler, int blockX, int blockZ,
                           Long angebotspreis) {
        if (welt == null || welt.isBlank() || spieler == null) {
            return Entscheid.nein(Bescheid.KEINE_STADTWELT);
        }
        if (angebotspreis != null && angebotspreis < 0L) {
            return Entscheid.nein(Bescheid.PREIS_UNGUELTIG);
        }
        Optional<Grundstueck> gefunden;
        try {
            gefunden = ablage.anZelle(welt, plan.zelleVon(blockX), plan.zelleVon(blockZ));
        } catch (RuntimeException fehler) {
            return Entscheid.nein(Bescheid.GESTOERT);
        }
        if (gefunden.isEmpty()) {
            return Entscheid.nein(Bescheid.KEIN_GRUNDSTUECK);
        }
        Grundstueck eines = gefunden.get();
        if (!eines.gehoert(spieler)) {
            return Entscheid.nein(Bescheid.NICHT_DEIN_GRUNDSTUECK,
                    eines.flaeche().zellen());
        }
        try {
            ablage.anbieten(eines.id(), angebotspreis);
        } catch (RuntimeException fehler) {
            return Entscheid.nein(Bescheid.GESTOERT, eines.flaeche().zellen());
        }
        // Das Grundstueck NACH der Schreibung und nicht davor: Der Entscheid sagt
        // zu, das neue zu tragen (siehe dort), und die Naht unterscheidet an ihm,
        // ob angeboten oder zurueckgezogen wurde. Mit dem alten Stand las sie
        // beides vertauscht.
        return new Entscheid(Bescheid.ANGEBOTEN,
                Optional.of(eines.mitAngebot(angebotspreis)),
                eines.flaeche().zellen(),
                angebotspreis == null ? 0L : angebotspreis, 0L);
    }

    /**
     * Ein angebotenes Grundstueck KAUFEN - das Abnahmekriterium aus Plan 15.
     *
     * <p><b>Der erste Kauf dieses Projekts, bei dem Geld von einem Spieler zu einem
     * anderen fliesst.</b> Bisher kauft immer jemand vom Bund, und dessen Geld kommt aus
     * dem Nichts: Ein Fehlschlag kostet dort nur eine Gegenbuchung. Hier gibt es zwei
     * Konten und eine Ablage, und keine gemeinsame Transaktion darueber - Kasse und
     * Grundstuecke sind zwei Schichten, und das soll so bleiben.
     *
     * <p><b>DIE REIHENFOLGE IST DIE GANZE ENTSCHEIDUNG</b>, und sie ist nach dem
     * SCHLIMMSTEN Fall gewaehlt, nicht nach dem haeufigsten:
     *
     * <ol>
     *   <li><b>Abbuchen beim Kaeufer.</b> Scheitert es, ist nichts geschehen - das ist
     *       der Normalfall einer Absage.</li>
     *   <li><b>Besitzerwechsel.</b> Scheitert er, kommt das Geld des Kaeufers zurueck.
     *       Hier liegt auch der Riegel gegen den Doppelkauf: Die Ablage nimmt den
     *       Wechsel nur an, solange das Angebot steht.</li>
     *   <li><b>Gutschreiben beim Verkaeufer.</b> Scheitert es, ist der Kauf GESCHEHEN
     *       und der Verkaeufer hat sein Geld nicht. Das ist der schlimmste Fall, und er
     *       wird NICHT zurueckgenommen - siehe unten.</li>
     *   <li><b>Gebuehr vom Verkaeufer.</b> Kann nach Schritt 3 nicht mehr scheitern: Die
     *       Gebuehr ist nie groesser als der Preis, den er gerade bekommen hat.</li>
     * </ol>
     *
     * <p><b>Warum Schritt 3 nicht zurueckgenommen wird</b>, obwohl das moeglich waere:
     * Ein Rueckweg muesste den Besitzerwechsel aufheben UND das Kaeufergeld
     * zurueckbuchen. Scheitert dabei das Zuruecknehmen beim Verkaeufer - weil er sein
     * Geld in den Millisekunden dazwischen ausgegeben hat -, dann haben BEIDE Geld und
     * niemand das Grundstueck: Geld aus dem Nichts. Der Verlust eines Betrages ist
     * schlimm und vom Betreiber mit einer Gutschrift zu heilen; eine Geldschoepfung ist
     * es nicht, denn sie ist nachher niemandem mehr zuzuordnen. Darum bleibt der Kauf
     * stehen, und der Aufrufer protokolliert laut. Dieselbe Abwaegung wie bei
     * {@code Shophandel.bezahle}, wo die Ware schon uebergeben ist.
     *
     * <p><b>Das Konto des Verkaeufers wird VOR Schritt 1 eroeffnet.</b> Damit kann
     * Schritt 3 nur noch an einer stummen Ablage scheitern und nicht daran, dass es
     * sein Konto nicht gibt - der haeufigere der beiden Gruende faellt weg.
     *
     * @param meldeVerlorenenErloes wird gerufen, wenn Schritt 3 scheitert. Der Aufrufer
     *                              protokolliert dann; die Entscheidung, was das heisst,
     *                              gehoert nicht in den Kern
     */
    public Entscheid kaufe(String welt, UUID kaeufer, int blockX, int blockZ,
                           VerlorenerErloes meldeVerlorenenErloes) {
        if (welt == null || welt.isBlank() || kaeufer == null) {
            return Entscheid.nein(Bescheid.KEINE_STADTWELT);
        }
        Optional<Grundstueck> gefunden;
        try {
            gefunden = ablage.anZelle(welt, plan.zelleVon(blockX), plan.zelleVon(blockZ));
        } catch (RuntimeException fehler) {
            return Entscheid.nein(Bescheid.GESTOERT);
        }
        if (gefunden.isEmpty()) {
            return Entscheid.nein(Bescheid.KEIN_GRUNDSTUECK);
        }
        Grundstueck eines = gefunden.get();
        int zellen = eines.flaeche().zellen();
        if (!eines.stehtZumVerkauf()) {
            return Entscheid.nein(Bescheid.KEIN_ANGEBOT, zellen);
        }
        if (eines.gehoert(kaeufer)) {
            return Entscheid.nein(Bescheid.EIGENES_GRUNDSTUECK, zellen);
        }
        long preis = eines.angebotspreis();
        UUID verkaeufer = eines.eigentuemer();

        // Das Konto des Verkaeufers ZUERST - siehe Javadoc.
        try {
            kasse.eroeffne(verkaeufer);
        } catch (RuntimeException fehler) {
            return Entscheid.nein(Bescheid.GESTOERT, zellen, preis);
        }

        // Schritt 1: abbuchen beim Kaeufer.
        try {
            if (!kasse.abbuchen(kaeufer, preis, Buchungsgrund.GRUNDSTUECK_GEKAUFT)) {
                return Entscheid.nein(Bescheid.ZU_WENIG_MARK, zellen, preis);
            }
        } catch (RuntimeException fehler) {
            return Entscheid.nein(Bescheid.GESTOERT, zellen, preis);
        }

        // Schritt 2: der Besitzerwechsel. Hier liegt der Riegel gegen den Doppelkauf.
        Grundstueck neu;
        try {
            neu = ablage.eigentuemerSetzen(eines.id(), kaeufer, preis);
        } catch (RuntimeException fehler) {
            erstatte(kaeufer, preis, fehler);
            return Entscheid.nein(Bescheid.GESTOERT, zellen, preis);
        }

        // Schritt 3: gutschreiben beim Verkaeufer. Ab hier GILT der Kauf.
        try {
            kasse.gutschreiben(verkaeufer, preis,
                    Buchungsgrund.GRUNDSTUECK_VERKAUFT);
        } catch (RuntimeException fehler) {
            meldeVerlorenenErloes.melde(verkaeufer, preis, fehler);
            return gekauft(neu, zellen, preis, 0L);
        }

        // Schritt 4: die Gebuehr. Nur, wenn die GERECHNETE ueber 0 liegt - nicht schon,
        // wenn ein Satz ueber 0 steht. Bei 5 Prozent auf 19 Mark ist sie abgerundet 0,
        // und eine Zeile ueber 0 Mark waere eine Zeile ohne Geldbewegung.
        long gebuehr = Shopregel.gebuehrAuf(preis,
                wirtschaft.werte().freistaedte().verkaufsgebuehrProzent());
        if (gebuehr > 0L) {
            try {
                kasse.abbuchen(verkaeufer, gebuehr, Buchungsgrund.GRUNDSTUECK_GEBUEHR);
            } catch (RuntimeException fehler) {
                meldeVerlorenenErloes.melde(verkaeufer, gebuehr, fehler);
            }
        }
        return gekauft(neu, zellen, preis, gebuehr);
    }

    /**
     * Das verkaufte Grundstueck, wie es DANACH aussieht - fuer die Meldung.
     *
     * <p>Der Stand kommt aus der ABLAGE und wird nicht hier zusammengesetzt. Vorher
     * baute diese Stelle die Zeile selbst nach, und damit raet sie zwei Dinge: dass die
     * Mitbauer geleert wurden und welchen Zeitpunkt {@code erworbenAm} jetzt traegt. Das
     * zweite war falsch - sie trug den des Vorbesitzers.
     */
    private Entscheid gekauft(Grundstueck neu, int zellen, long preis, long gebuehr) {
        return new Entscheid(Bescheid.GEKAUFT, Optional.of(neu), zellen, preis,
                gebuehr);
    }

    /**
     * Das Geld zurueck, und zwar auch dann, wenn dabei etwas schiefgeht.
     *
     * <p>Wortgleich zu {@code Freistadtbuch.erstatte}, und aus demselben Grund: Die
     * Ausnahme der Gutschrift wird an die urspruengliche GEHAENGT und nicht an ihre
     * Stelle gesetzt. Sonst stuende im Logbuch der zweite Fehler, und der erste - der,
     * der den Kauf gekostet hat - waere weg.
     */
    private void erstatte(UUID wer, long preis, RuntimeException ursache) {
        try {
            kasse.gutschreiben(wer, preis, Buchungsgrund.GRUNDSTUECK_ERSTATTET);
        } catch (RuntimeException auchDas) {
            ursache.addSuppressed(auchDas);
        }
    }

    /**
     * Was geschieht, wenn ein Erloes nicht ankommt.
     *
     * <p>Eine Naht und kein Protokoll im Kern: WAS eine verlorene Gutschrift bedeutet -
     * eine Zeile im Serverprotokoll, eine Nachricht an das Team, ein Eintrag in einer
     * Wiedervorlage - entscheidet der Betreiber und nicht die Regel. Der Kern weiss nur,
     * DASS es geschehen ist.
     */
    public interface VerlorenerErloes {

        /**
         * @param wer     wem das Geld fehlt
         * @param betrag  wie viel
         * @param fehler  warum es nicht ankam
         */
        void melde(UUID wer, long betrag, RuntimeException fehler);
    }

    /** Die Grundstuecke dieser Welt, die zum Verkauf stehen. */
    public List<Grundstueck> angebote(String welt) {
        return ablage.angebote(welt);
    }

    /**
     * Das Grundstueck an dieser BLOCKstelle - fuer die Wache und fuer die Auskunft.
     *
     * <p>Wirft bei einer Stoerung, wie {@link Freistadtbuch#darfBauen} und aus demselben
     * Grund: Die Wache faengt das und antwortet NEIN.
     */
    public Optional<Grundstueck> anStelle(String welt, int blockX, int blockZ) {
        return ablage.anZelle(welt, plan.zelleVon(blockX), plan.zelleVon(blockZ));
    }

    /** Die Grundstuecke einer Stadt - fuer die Liste ihres Besitzers. */
    public List<Grundstueck> inStadt(long stadtId) {
        return ablage.inStadt(stadtId);
    }
}
