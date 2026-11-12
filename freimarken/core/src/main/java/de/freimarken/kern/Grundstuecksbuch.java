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
                            int zellen) {

        static Entscheid nein(Bescheid grund) {
            return new Entscheid(grund, Optional.empty(), 0);
        }

        static Entscheid nein(Bescheid grund, int zellen) {
            return new Entscheid(grund, Optional.empty(), zellen);
        }

        /** Ob es geklappt hat - fuer den Aufrufer, der nur das wissen will. */
        public boolean erfolgreich() {
            return bescheid == Bescheid.ERTEILT;
        }
    }

    private final Stadtfeldplan plan;
    private final Feldgrenze grenze;
    private final BesitzLager lager;
    private final Grundstuecksablage ablage;

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
                            Grundstuecksablage ablage, Wirtschaftsstand wirtschaft) {
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
            return new Entscheid(Bescheid.ERTEILT, Optional.of(neu), flaeche.zellen());
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
        return new Entscheid(Bescheid.ERTEILT, Optional.of(eines),
                eines.flaeche().zellen());
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
