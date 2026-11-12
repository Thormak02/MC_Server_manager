package de.freimarken.kern;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Wer eine Freistadt gruenden darf, was sie kostet und welches Feld er bekommt.
 *
 * <p>Das Geschwister von {@link Besitzbuch}, und eine EIGENE Klasse aus einem Grund, der
 * im Besitzbuch selbst steht: Jenes haelt eine {@link Stadtgrenze} und einen
 * {@link Rasterplan} als Felder. Es sieht aus, als koenne es mehrere Welten - es kann es
 * nicht, denn {@code welt} ist nur ein Parameter, waehrend die Geometrie fest verdrahtet
 * ist. Wer das Gruenden durch das Besitzbuch fuehrte, bekaeme die Marktstadt-Geometrie
 * auf die Freistaedtewelt angewendet.
 *
 * <p><b>Bezahlt wird hier und nicht im Befehl</b>, wie ueberall in diesem Projekt: Bei
 * Erfolg ist der Preis abgebucht, bei jedem anderen Bescheid ist nichts passiert - auch
 * nicht an der Kasse. Ein Befehl, der selbst abbucht, haette die Gegenbuchung bei einem
 * Fehlschlag irgendwo zwischen Brigadier und der Ablage zu erledigen.
 *
 * <p><b>Nichts hiervon wirft nach aussen</b> - das ist die Lehre aus dem Besitzbuch, das
 * seine Ausnahme weiterreicht, bis sie in Brigadier landet und der Spieler eine rote
 * Zeile ohne Sinn liest. Hier faengt {@link #gruende} und antwortet mit
 * {@link Bescheid#GESTOERT}.
 */
public final class Freistadtbuch {

    /** Ausgang eines Gruendungsversuchs. */
    public enum Bescheid {
        /** Gegruendet, das Feld ist zugeteilt, der Preis ist abgebucht. */
        GEGRUENDET,
        /** Der Name ist zu kurz, zu lang oder leer. */
        NAME_ZU_LANG,
        /** Der Name traegt Zeichen, die es nicht geben darf. */
        NAME_FALSCH,
        /** Der Name gehoert einer Welt - {@code /warp kornmark} ginge dann ins Leere. */
        NAME_RESERVIERT,
        /** Diesen Stadtnamen gibt es schon. */
        NAME_VERGEBEN,
        /** Der Spieler hat so viele Staedte, wie sein Rang erlaubt. */
        GRENZE_ERREICHT,
        /** Alle Felder der Welt sind vergeben. Nicht GRENZE_ERREICHT - das zaehlt Staedte. */
        KEIN_FREIES_FELD,
        /** Das Guthaben reicht nicht. */
        ZU_WENIG_MARK,
        /** Die Ablage hat gestoert. Das Geld ist zurueck, gegruendet ist nichts. */
        GESTOERT
    }

    /**
     * Antwort auf einen Versuch.
     *
     * <p>Der Preis steht AUCH bei einer Absage drin, und zwar genau bei
     * {@link Bescheid#ZU_WENIG_MARK}: Der Spieler soll lesen, was ihm fehlt. Bei allen
     * anderen Absagen ist er 0, denn dann war der Preis nie im Spiel.
     *
     * @param bescheid was herausgekommen ist
     * @param kosten   was es gekostet hat oder gekostet haette
     * @param stadt    die neue Stadt, nur bei {@link Bescheid#GEGRUENDET}
     */
    public record Entscheid(Bescheid bescheid, long kosten, Optional<Parzelle> stadt) {

        static Entscheid nein(Bescheid grund) {
            return new Entscheid(grund, 0L, Optional.empty());
        }

        static Entscheid nein(Bescheid grund, long kosten) {
            return new Entscheid(grund, kosten, Optional.empty());
        }

        /** Ob gegruendet wurde - fuer den Aufrufer, der nur das wissen will. */
        public boolean erfolgreich() {
            return bescheid == Bescheid.GEGRUENDET;
        }
    }

    /**
     * Was an einer Stelle der Stadtfelderwelt gilt.
     *
     * <p>NEUN Lagen und nicht zwei, weil der Spieler den Unterschied LESEN soll. Jede
     * traegt ihren eigenen Handbalken; welchen, entscheidet {@code Sprachschluessel} und
     * nicht die Wache.
     *
     * <p><b>Jedes Urteil sagt SELBST, ob es erlaubt</b> [12.11.2026, Teil D4]. Vorher
     * fuehrte die {@code Stadtwache} eine Positivliste zweier Urteile, und die war der
     * stille Weg: Ein neues Urteil fiel dort auf VERBOTEN zurueck, ohne dass etwas
     * geklagt haette - ein Buergerrecht waere gebaut, gruen geprueft und wirkungslos
     * gewesen. Jetzt verlangt die Aufzaehlung die Antwort beim Anlegen.
     */
    public enum Bauurteil {
        /** Die Stadt gehoert ihm, oder er steht als Mitbauer darin. */
        ERLAUBT(true),
        /**
         * Zwischen den Staedten - oder auf der Barriere.
         *
         * <p>Fuer JEDEN verboten, auch fuer den Besitzer des Feldes nebenan: Ein Block
         * in der Leere ist der erste Block einer Bruecke.
         */
        LEERE(false),
        /** Jenseits der Weltgrenze - dort wird nie eine Stadt stehen. */
        AUSSERHALB(false),
        /** Das Ankunftsfeld. Gehoert niemandem und ist nicht zu gruenden. */
        ANKUNFTSFELD(false),
        /** Ein Stadtfeld, auf dem noch keine Stadt steht. */
        FREIES_FELD(false),
        /** Die Stadt eines anderen. */
        FREMDE_STADT(false),
        /**
         * Ein vergebenes Grundstueck, das ihm gehoert (oder auf dem er Mitbauer ist).
         *
         * <p>Ein eigenes Urteil neben {@link #ERLAUBT}, obwohl beide ja sagen: Der
         * Unterschied zaehlt fuer Teil C3. Wer auf seinem GEKAUFTEN Grundstueck baut,
         * steht dort anders als der Stadtbesitzer auf seinem Feld - und eine Wache, die
         * beides nicht unterscheiden kann, koennte spaeter auch nicht melden, WARUM
         * jemand darf.
         */
        ERLAUBT_GRUNDSTUECK(true),
        /**
         * Ein vergebenes Grundstueck eines ANDEREN - mitten in der eigenen Stadt.
         *
         * <p><b>Das Urteil, das Teil C2 ueberhaupt erst wirksam macht</b>: Es sticht
         * {@link #ERLAUBT}. Der Stadtbesitzer darf auf seinem Feld bauen - aber nicht
         * mehr dort, wo er ein Grundstueck vergeben hat. Ohne diesen Fall waere ein
         * verkauftes Grundstueck eine Zeile in der Datenbank ohne jede Wirkung, und der
         * Kaeufer haette fuer eine Flaeche gezahlt, auf der der Verkaeufer
         * weiterbaut.
         */
        FREMDES_GRUNDSTUECK(false),
        /**
         * Ein STADTBUERGER, und seine Stadt gibt diese Tat her [Teil D4].
         *
         * <p>Stadtbuerger ist, wem in der Stadt ein Grundstueck gehoert - nicht der
         * Stadtbesitzer und kein Mitbauer; die Regel steht in
         * {@link Stadtbuergerschaft}. Was er im GEMEINSCHAFTSGEBIET darf, stellt der
         * Stadtbesitzer je Stadt ein, und darum haengt dieses Urteil an der {@link Tat}:
         * Es kann an derselben Stelle fuer eine Tuer gelten und fuer einen Block nicht.
         *
         * <p>Ein eigenes Urteil neben {@link #ERLAUBT} und {@link #ERLAUBT_GRUNDSTUECK},
         * obwohl alle drei ja sagen - aus demselben Grund wie jene beide: Eine Wache,
         * die sie nicht unterscheiden kann, koennte spaeter nicht melden, WARUM jemand
         * darf. Und ein Buergerrecht, das wie Besitz aussieht, waere die Stelle, an der
         * jemand die Verfallsregel aus Plan 16 falsch rechnet.
         *
         * <p>Es sticht {@link #FREMDE_STADT}, aber NICHT
         * {@link #FREMDES_GRUNDSTUECK}: Auf dem vergebenen Grundstueck eines anderen
         * darf ein Buerger nichts - sonst waere Teil C2 fuer jeden Buerger der Stadt
         * aufgehoben, und der Kaeufer haette fuer eine Flaeche gezahlt, auf der seine
         * Nachbarn weiterbauen.
         */
        ERLAUBT_STADTBUERGER(true);

        private final boolean erlaubt;

        Bauurteil(boolean erlaubt) {
            this.erlaubt = erlaubt;
        }

        /**
         * Ob die Hand durchgeht.
         *
         * <p>Die EINE Stelle, an der das steht. Wer ein Urteil hinzufuegt, muss sich hier
         * entscheiden - der Konstruktor verlangt es -, und keine Wache kann es mehr
         * uebersehen.
         */
        public boolean erlaubt() {
            return erlaubt;
        }
    }

    private final Feldgrenze grenze;
    private final Stadtfeldplan plan;
    private final BesitzLager lager;
    private final Kasse kasse;
    private final Warpverzeichnis warps;

    /**
     * Woher der Preis kommt.
     *
     * <p>Wie beim Besitzbuch darf das Feld {@code final} bleiben, weil nicht der WERT
     * eingefroren wird, sondern der Zugang zu ihm: Der Preis wird in dem Augenblick
     * geholt, in dem abgebucht wird. Ein Neuladen erreicht ihn damit.
     */
    private final Wirtschaftsstand wirtschaft;

    /**
     * Die Namen der Staedte fuer den Vorschlagsgeber - siehe {@link Stadtverzeichnis}.
     *
     * <p>Das Buch BESITZT es und bekommt es nicht herein, und das ist der Grund:
     * Hier ist die einzige Stelle, an der eine Stadt entsteht. Ein Verzeichnis, das
     * von aussen hereingereicht wird, laesst sich vergessen - und dann fehlte der
     * Vorschlag zu jeder Stadt, die nach dem Start gegruendet wurde.
     */
    private final Stadtverzeichnis verzeichnis = new Stadtverzeichnis();

    /**
     * Die Grundstuecke in den Staedten - {@code null}, solange es keine gibt.
     *
     * <p><b>Null-faehig und das mit Absicht</b>: Teil A und B haben ohne Grundstuecke
     * gelebt, und ein Server, dessen Grundstuecksablage beim Start ausfaellt, soll
     * weiter Staedte gruenden und schuetzen koennen. Ist sie {@code null}, endet
     * {@link #darfBauen} bei der Stadt - also genau beim Stand von Teil C1.
     *
     * <p>Hereingereicht und nicht selbst gebaut, anders als das
     * {@link Stadtverzeichnis} darueber: Jenes ist eine Liste von Namen, die dieses
     * Buch selbst fuellt. Die Grundstuecke sind eine ABLAGE mit eigener Tabelle und
     * eigenem Zwischenspeicher - wer die baut, entscheidet der Start.
     */
    private Grundstuecksablage grundstuecke;

    public Freistadtbuch(Feldgrenze grenze, BesitzLager lager, Kasse kasse,
                         Warpverzeichnis warps, Wirtschaftsstand wirtschaft) {
        this.grenze = grenze;
        this.plan = grenze.plan();
        this.lager = lager;
        this.kasse = kasse;
        this.warps = warps;
        this.wirtschaft = wirtschaft;
    }

    /** Wie weit die Welt reicht - fuer die Zuteilung und fuer die Auskunft. */
    public Feldgrenze grenze() {
        return grenze;
    }

    public Stadtfeldplan plan() {
        return plan;
    }

    /** Was eine Stadt heute kostet - fuer die Rueckfrage vor dem Gruenden. */
    public long preis() {
        return wirtschaft.werte().freistaedte().gruendungspreis();
    }

    /** Wie viele Staedte dieser Spieler in dieser Welt haelt. */
    public int staedteVon(String welt, UUID spieler) {
        int gezaehlt = 0;
        for (Parzelle stadt : lager.staedteIn(welt)) {
            if (stadt.gehoert(spieler)) {
                gezaehlt++;
            }
        }
        return gezaehlt;
    }

    /** Alle Staedte dieser Welt, nach Namen - fuer die Liste und fuer den Warp. */
    public List<Parzelle> staedte(String welt) {
        return lager.staedteIn(welt);
    }

    /** Die Namen fuer den Vorschlagsgeber - siehe {@link Stadtverzeichnis}. */
    public Stadtverzeichnis verzeichnis() {
        return verzeichnis;
    }

    /**
     * Die Grundstuecke hereingeben - einmal beim Start, nach dem Fuellen.
     *
     * <p>Ein Setzer und kein Konstruktorparameter, und der Grund ist die Reihenfolge im
     * Start: Das Buch entsteht, damit {@code /freistadt} und {@code /warp} es haben;
     * die Grundstuecksablage braucht erst ihren Zwischenspeicher, und der will gefuellt
     * werden. Ein Parameter haette beides in eine Zeile gezwungen - dieselbe Lage wie
     * beim spaet gelesenen Feld der Stadtwache.
     *
     * <p>Bis dahin urteilt {@link #darfBauen} ohne Grundstuecke, also wie in Teil C1.
     * Das ist die sichere Richtung: Es schuetzt weniger fein, aber es schuetzt.
     */
    public void gibGrundstuecke(Grundstuecksablage ablage) {
        this.grundstuecke = ablage;
    }

    /**
     * Das Verzeichnis aus der Ablage fuellen - einmal beim Start.
     *
     * <p>Gibt die Zahl zurueck, damit der Startbericht sie nennen kann. Das ist kein
     * Zierrat: Eine Methode, die nur fuellt, liesse sich vergessen zu rufen, und kein
     * Probenlauf meldete das. So haengt der Aufruf an einer Zeile, die der Betreiber
     * bei jedem Start liest.
     *
     * <p><b>Gezaehlt wird im VERZEICHNIS und nicht in der Ablage</b>, und das ist ein
     * Nachzug: Zuerst stand hier {@code vorhandene.size()}. Damit war die Startzeile
     * kein Beleg fuer irgendetwas - sie haette dieselbe Zahl genannt, wenn das Fuellen
     * ganz weggefallen waere. Jetzt nennt sie, was wirklich im Verzeichnis steht, und
     * eine Null neben "davon 7 gegruendet" waere sofort sichtbar.
     */
    public int erneuereVerzeichnis(String welt) {
        List<String> gefunden = new ArrayList<>();
        for (Parzelle stadt : lager.staedteIn(welt)) {
            gefunden.add(stadt.name());
        }
        verzeichnis.erneuere(gefunden);
        return verzeichnis.anzahl();
    }

    /**
     * Wer an dieser Stelle bauen darf - und wenn nicht, warum nicht.
     *
     * <p>Das Gegenstueck zu {@link Besitzbuch#darfBauen}, und es musste eines geben:
     * Bis zum 11.11.2026 griff in der Welt der Stadtfelder <b>gar keine Wache</b>. Jeder
     * konnte in jedem Feld jeder fremden Stadt bauen und abbauen - 400.000 Mark fuer ein
     * Feld, das niemandem gehoerte. Und die Zusage im Javadoc von {@link Stadtfeldplan},
     * bruecken koenne niemand, "weil ausserhalb eines Feldes das Bauen verboten ist",
     * stand auf einem Verbot, das es nicht gab: Die Barriere ist vier Bloecke hoch, wer
     * auf ihre Oberkante steigt, baut von dort aus weiter.
     *
     * <p><b>Mehr als ja und nein</b>, weil der Spieler lesen soll, WARUM: Zwischen den
     * Staedten ist etwas anderes verboten als auf einem fremden Feld, und auf einem
     * freien Feld steht ein Weg offen, den er sonst nicht findet. Dieselbe Entscheidung
     * wie bei den Parzellen, wo {@code Schutztext} vier verschiedene Handbalken kennt.
     *
     * <p><b>Wirft</b>, wenn die Ablage stumm ist - wie {@link Besitzbuch#darfBauen} und
     * aus demselben Grund: Die Wache faengt das und antwortet NEIN. Ein stilles Ja waere
     * eine Welt, die bei jeder Netzstoerung offensteht.
     *
     * @param welt    Weltschluessel der Stadtfelderwelt
     * @param spieler wer bauen will - {@code null} ergibt nie ERLAUBT
     * @param blockX  BLOCKkoordinate, nicht Feldnummer
     * @param blockZ  BLOCKkoordinate, nicht Feldnummer
     */
    public Bauurteil darfBauen(String welt, UUID spieler, Tat tat, int blockX,
                               int blockZ) {
        // Die LEERE zuerst, und zwar ohne jede Frage an die Ablage: Sie ist der
        // haeufigste Fall am Feldrand, sie kostet nichts, und sie gilt fuer JEDEN -
        // auch fuer den Besitzer des Feldes daneben. Wer in der Leere baut, baut eine
        // Bruecke, und genau die soll es nicht geben.
        if (plan.feldBei(blockX, blockZ) != Stadtfeldplan.Feld.STADTFELD) {
            return Bauurteil.LEERE;
        }
        int feldX = plan.feldNummer(blockX);
        int feldZ = plan.feldNummer(blockZ);
        if (!grenze.enthaeltFeld(feldX, feldZ)) {
            return Bauurteil.AUSSERHALB;
        }
        // Das Ankunftsfeld VOR dem Nachsehen: Es gehoert niemandem und wird nie
        // vergeben, also waere "frei - gruende hier" dort eine Luege.
        if (plan.istAnkunftsfeld(feldX, feldZ)) {
            return Bauurteil.ANKUNFTSFELD;
        }
        Optional<Parzelle> stadt = lager.finde(welt, feldX, feldZ);
        if (stadt.isEmpty()) {
            return Bauurteil.FREIES_FELD;
        }
        // DAS GRUNDSTUECK STICHT DIE STADT, und erst das macht Teil C2 wirksam: Wer ein
        // Grundstueck vergeben hat, darf dort selbst nicht mehr bauen. Gefragt wird es
        // NACH der Stadt, weil die Stadtfrage billiger ist (eine Punktabfrage auf den
        // eindeutigen Schluessel) und auf einem freien Feld schon alles entscheidet - und
        // weil es ohne Stadt auch kein Grundstueck geben kann.
        if (grundstuecke != null) {
            Optional<Grundstueck> darauf = grundstuecke.anZelle(welt,
                    plan.zelleVon(blockX), plan.zelleVon(blockZ));
            if (darauf.isPresent()) {
                return spieler != null && darauf.get().darfBauen(spieler)
                        ? Bauurteil.ERLAUBT_GRUNDSTUECK
                        : Bauurteil.FREMDES_GRUNDSTUECK;
            }
        }
        if (spieler != null && stadt.get().darfBauen(spieler)) {
            return Bauurteil.ERLAUBT;
        }
        // DER STADTBUERGER [Teil D4], und die Reihenfolge der drei Fragen ist die des
        // heissen Pfades: zuerst das Recht IN DER ZEILE, die hier schon in der Hand
        // liegt - gibt die Stadt diese Tat gar nicht her, faellt alles Weitere weg. Erst
        // dann die Frage an die Ablage, ob ihm hier Grund gehoert, und sie ist die
        // einzige, die etwas kostet (eine Karte, siehe besitztIn).
        //
        // Die REGEL selbst steht in Stadtbuergerschaft und nicht hier: Dass der
        // Stadtbesitzer nicht als Buerger zaehlt, ist dieselbe Entscheidung, nach der
        // auch die Liste und Teil E rechnen - und eine zweite Bedingung an dieser Stelle
        // waere der zweite Weg zu derselben Tat.
        if (tat != null && stadt.get().buergerDuerfen(tat)
                && istStadtbuerger(welt, stadt.get(), spieler)) {
            return Bauurteil.ERLAUBT_STADTBUERGER;
        }
        return Bauurteil.FREMDE_STADT;
    }

    /**
     * Das Grundstueck an dieser Stelle - fuer die Begruendung.
     *
     * <p>Getrennt von {@link #darfBauen} und erst DANACH gefragt, aus demselben Grund
     * wie bei {@link #stadtAn}: Der Handbalken wird hoechstens alle paar Sekunden
     * gebaut, die Bauregel bei jedem gesetzten Block. Wer beides in einem Zug
     * beantwortete, zahlte die Suche nach dem Besitzernamen auf dem heissen Pfad mit.
     */
    public Optional<Grundstueck> grundstueckAn(String welt, int blockX, int blockZ) {
        if (grundstuecke == null) {
            return Optional.empty();
        }
        return grundstuecke.anZelle(welt, plan.zelleVon(blockX), plan.zelleVon(blockZ));
    }

    /**
     * Ob dieser Spieler Stadtbuerger DIESER Stadt ist.
     *
     * <p><b>Die EINE Stelle, an der die Frage gestellt wird</b> [Teil D4], und sie hat
     * zwei Rufer mit sehr verschiedenem Takt: {@link #darfBauen} bei jedem gesetzten
     * Block, und die {@code Stadtwache} fuer den Handbalken hoechstens alle zwei
     * Sekunden je Spieler. Zwei eigene Bedingungen waeren zwei Wege zu derselben Tat -
     * und der eine koennte jemanden sehen, den der andere nicht sieht.
     *
     * <p>Die REGEL liegt noch eine Stelle tiefer: {@link Stadtbuergerschaft#istBuerger}
     * sagt, dass der Stadtbesitzer nicht als Buerger seiner eigenen Stadt zaehlt - und
     * dieselbe Methode rechnet die Liste und (ab Teil E) die Verfallsregel.
     *
     * <p><b>Ohne Grundstuecksablage ist niemand Buerger</b>: Dann gibt es keine
     * Grundstuecke, also auch keinen, dem eines gehoert. Das ist der Stand vor Teil C2
     * und in jeder Probe, die das Buch ohne Ablage baut.
     *
     * @param stadt die Stadtzeile - der Rufer hat sie schon in der Hand, und ein
     *              zweites Nachsehen hier waere auf dem heissen Pfad ein zweiter Gang
     */
    public boolean istStadtbuerger(String welt, Parzelle stadt, UUID spieler) {
        if (stadt == null) {
            throw new IllegalArgumentException("Ohne Stadt gibt es keine"
                    + " Buergerschaft - ein freies Feld hat keine Buerger");
        }
        return spieler != null && grundstuecke != null
                && Stadtbuergerschaft.istBuerger(stadt, spieler,
                        grundstuecke.besitztIn(welt, stadt.id(), spieler));
    }

    /**
     * Die Stadt an dieser Stelle - fuer die Begruendung, die der Spieler liest.
     *
     * <p>Getrennt von {@link #darfBauen} und erst DANACH gefragt: Der Handbalken wird
     * hoechstens alle paar Sekunden gebaut, die Bauregel bei jedem gesetzten Block. Wer
     * beides in einem Zug beantwortete, zahlte die Suche nach dem Besitzernamen auf dem
     * heissen Pfad mit.
     */
    public Optional<Parzelle> stadtAn(String welt, int blockX, int blockZ) {
        if (plan.feldBei(blockX, blockZ) != Stadtfeldplan.Feld.STADTFELD) {
            return Optional.empty();
        }
        return lager.finde(welt, plan.feldNummer(blockX), plan.feldNummer(blockZ));
    }

    /**
     * Eine Freistadt gruenden.
     *
     * <p><b>Die Reihenfolge der Absagen ist nach KOSTEN sortiert</b>, nicht nach
     * Wichtigkeit: zuerst, was gar nichts braucht (die Form des Namens), dann der Blick
     * in die Welten, dann die Ablage, zuletzt das Geld. Wer sich vertippt, soll das nicht
     * erst erfahren, nachdem die Datenbank dreimal gefragt wurde.
     *
     * @param welt        Weltschluessel der Stadtfelderwelt
     * @param spieler     wer gruenden will
     * @param roherName   was er getippt hat, ungeprueft
     * @param hoechstzahl wie viele Staedte sein Rang erlaubt
     */
    public Entscheid gruende(String welt, UUID spieler, String roherName,
                             int hoechstzahl) {
        if (!Stadtname.brauchbareLaenge(roherName)) {
            return Entscheid.nein(Bescheid.NAME_ZU_LANG);
        }
        if (!Stadtname.brauchbareZeichen(roherName)) {
            return Entscheid.nein(Bescheid.NAME_FALSCH);
        }
        String name = Stadtname.genormt(roherName);
        // Vor der Ablage und nicht danach: Diese Frage kostet nichts, und ein Stadtname,
        // der wie eine Welt heisst, machte /warp kornmark mehrdeutig. Warpverzeichnis
        // haelt die Regel schon bereit - der Aufrufer aendert sich, die Regel nicht.
        if (warps.reserviert(name)) {
            return Entscheid.nein(Bescheid.NAME_RESERVIERT);
        }

        List<Parzelle> vorhandene = lager.staedteIn(welt);
        int eigene = 0;
        for (Parzelle stadt : vorhandene) {
            if (stadt.name().equals(name)) {
                return Entscheid.nein(Bescheid.NAME_VERGEBEN);
            }
            if (stadt.gehoert(spieler)) {
                eigene++;
            }
        }
        if (eigene >= hoechstzahl) {
            return Entscheid.nein(Bescheid.GRENZE_ERREICHT);
        }

        Optional<int[]> feld = freiesFeld(vorhandene);
        if (feld.isEmpty()) {
            return Entscheid.nein(Bescheid.KEIN_FREIES_FELD);
        }

        long preis = preis();
        if (!kasse.abbuchen(spieler, preis, Buchungsgrund.FREISTADT_GEGRUENDET)) {
            return Entscheid.nein(Bescheid.ZU_WENIG_MARK, preis);
        }

        try {
            Parzelle neu = lager.freistadtAnlegen(welt, feld.get()[0], feld.get()[1],
                    spieler, preis, name);
            // DIE VORGABE-BUERGERRECHTE, und nur wenn es welche gibt [12.11.2026]. Sie
            // steht in freistaedte.buergerrechte und ist ab Werk leer - dann faellt
            // dieser Zug ganz weg, und eine frische Stadt gibt nichts her.
            //
            // HIER und nicht im Lager: Das Lager kennt keine Konfiguration, und ein
            // Vorgabewert dort waere eine zweite Quelle fuer dieselbe Frage. Dass es ein
            // zweiter Schreibzug ist, ist vertretbar - scheitert er, faengt ihn derselbe
            // Fang darunter, und die Gruendung gilt als nicht geschehen.
            Set<Tat> vorgabe = wirtschaft.werte().freistaedte().buergerrechte();
            if (!vorgabe.isEmpty()) {
                lager.buergerrechteSetzen(neu.id(), vorgabe);
                // Die Zeile MIT den Rechten weitergeben und nicht die von vorher: Der
                // Entscheid traegt das Grundstueck, wie es NACHHER ist - derselbe Fund
                // wie bei biete() in Teil C3.
                neu = neu.mitBuergerrechten(vorgabe);
            }
            // ERST die Ablage, DANN das Verzeichnis - dieselbe Reihenfolge wie bei
            // den Kistenshops. Scheitert die Ablage, steht hier kein Name, und das
            // ist die sichere Richtung: ein Vorschlag zu einer Stadt, die es nicht
            // gibt, endete in der Absage "Die Freistadt ... gibt es nicht".
            verzeichnis.nimmAuf(name);
            return new Entscheid(Bescheid.GEGRUENDET, preis, Optional.of(neu));
        } catch (RuntimeException fehler) {
            // Das Geld ist weg, die Stadt nicht gekommen - also zurueck damit. Und zwar
            // mit eigenem Fang: Scheitert auch die Gutschrift, ersetzte ihre Ausnahme die
            // urspruengliche, und 400.000 Mark waeren still verschwunden.
            erstatte(spieler, preis, fehler);
            return Entscheid.nein(Bescheid.GESTOERT, preis);
        }
    }

    // --- Teil D3: die Buergerrechte --------------------------------------------

    /** Was beim Setzen eines Buergerrechts herauskam. */
    public enum Rechtebescheid {
        /** Das Recht ist dazugekommen oder weggenommen - die Zeile steht neu. */
        GEAENDERT,
        /**
         * Es war schon so eingestellt.
         *
         * <p>Ein eigener Bescheid und kein stilles GEAENDERT: Ein Stadtbesitzer, der
         * zweimal dasselbe tippt, soll lesen, dass nichts zu tun war - und nicht
         * glauben, er habe gerade etwas umgestellt.
         */
        SCHON_SO,
        /** Er steht nicht in einer Stadt - auf einem freien Feld oder in der Leere. */
        KEINE_STADT,
        /** Er steht in der Stadt eines ANDEREN. */
        FREMDE_STADT,
        /** Die Ablage war stumm. */
        GESTOERT
    }

    /**
     * Das Ergebnis eines Rechtezuges.
     *
     * @param stadt  die Stadt, um die es ging - bei KEINE_STADT leer
     * @param rechte die Rechte, wie sie NACHHER sind. Zurueckgegeben und nicht vom
     *               Aufrufer nachgebaut: derselbe Grund wie bei {@code biete} in Teil
     *               C3, wo ein nachgebauter Stand die Naht verkehrt lesen liess
     */
    public record Rechteentscheid(Rechtebescheid bescheid, Optional<Parzelle> stadt,
                                  Set<Tat> rechte) {
    }

    /**
     * Ein Buergerrecht an- oder abschalten - am STANDORT des Spielers.
     *
     * <p><b>Am Standort und nicht an seinem Besitz</b>, wie der ganze
     * Grundstueckszweig: Eine Regel, die fuer ein Gebiet gilt, stellt man im Gebiet ein.
     * Es gibt in diesem Projekt kein "meine Stadt" im Befehl, und einen zweiten Weg
     * dafuer zu bauen hiesse, zwei Wege zu derselben Tat zu haben.
     *
     * <p><b>Nur der Stadtbesitzer</b>, und nicht einmal ein Mitbauer der Stadt: Wer die
     * Rechte aendert, aendert, was FREMDE in seinem Gebiet duerfen - das ist eine
     * Entscheidung des Eigentuemers.
     *
     * @param an {@code true} gibt das Recht, {@code false} nimmt es
     */
    public Rechteentscheid setzeRecht(String welt, UUID spieler, int blockX, int blockZ,
                                      Tat recht, boolean an) {
        if (recht == null) {
            throw new IllegalArgumentException("Ohne Recht ist nichts zu setzen - ein"
                    + " unbekanntes Wort faengt der Befehl ab, bevor er hierher kommt");
        }
        Optional<Parzelle> stadt;
        try {
            stadt = stadtAn(welt, blockX, blockZ);
        } catch (RuntimeException fehler) {
            return new Rechteentscheid(Rechtebescheid.GESTOERT, Optional.empty(),
                    Set.of());
        }
        if (stadt.isEmpty()) {
            return new Rechteentscheid(Rechtebescheid.KEINE_STADT, Optional.empty(),
                    Set.of());
        }
        Parzelle hier = stadt.get();
        if (spieler == null || !hier.gehoert(spieler)) {
            // Die Stadt kommt MIT: Der Satz nennt ihren Namen und ihren Besitzer, und
            // beides steht in dieser Zeile.
            return new Rechteentscheid(Rechtebescheid.FREMDE_STADT, stadt,
                    hier.buergerrechte());
        }
        if (hier.buergerDuerfen(recht) == an) {
            return new Rechteentscheid(Rechtebescheid.SCHON_SO, stadt,
                    hier.buergerrechte());
        }
        // Die neue Menge wird GERECHNET und nicht geaendert: Die alte liegt
        // unveraenderlich in der Zeile, und ein Hinzufuegen daran wuerfe.
        Set<Tat> neue = new java.util.LinkedHashSet<>(hier.buergerrechte());
        if (an) {
            neue.add(recht);
        } else {
            neue.remove(recht);
        }
        try {
            lager.buergerrechteSetzen(hier.id(), neue);
        } catch (RuntimeException fehler) {
            return new Rechteentscheid(Rechtebescheid.GESTOERT, stadt,
                    hier.buergerrechte());
        }
        return new Rechteentscheid(Rechtebescheid.GEAENDERT,
                Optional.of(hier.mitBuergerrechten(neue)), neue);
    }

    /**
     * Das Geld zurueck, und zwar auch dann, wenn dabei etwas schiefgeht.
     *
     * <p>Die Ausnahme der Gutschrift wird an die urspruengliche GEHAENGT und nicht an
     * ihre Stelle gesetzt. Sonst stuende im Logbuch der zweite Fehler, und der erste -
     * der, der die Gruendung gekostet hat - waere weg.
     */
    private void erstatte(UUID spieler, long preis, RuntimeException ursache) {
        try {
            kasse.gutschreiben(spieler, preis, Buchungsgrund.FREISTADT_ERSTATTET);
        } catch (RuntimeException auchDas) {
            ursache.addSuppressed(auchDas);
        }
    }

    /**
     * Das naechste freie Stadtfeld - oder keins mehr.
     *
     * <p>Von innen nach aussen vergeben, Ring um Ring um das Ankunftsfeld: Die ersten
     * Staedte liegen dann beieinander statt verstreut ueber 5.792 Bloecke. Das kostet
     * nichts - man kann ohnehin nicht hinuebersehen - und spart der Welt die Chunks
     * dazwischen, denn erzeugt wird nur, wo jemand ist.
     *
     * <p>Gefragt wird die {@link Feldgrenze} und nicht die Zahl aus der
     * Einstellungsdatei. Die ist ein WUNSCH: Aus 6000 werden 20 Felder je Achse. Wer das
     * ueberspringt, verkauft eine Stadt, deren Mitte vor der eigenen Weltgrenze liegt -
     * der Besitzer kaeme nie hinein.
     *
     * <p>In Java gesucht und nicht mit wiederholten Schreibversuchen: Bei dreihundert
     * vergebenen Feldern waeren das dreihundert fehlgeschlagene INSERTs, jeder mit
     * seinem eigenen Rundlauf zur Datenbank. Der eindeutige Schluessel bleibt trotzdem
     * der Riegel - diese Suche ist die Abkuerzung, nicht die Sicherung.
     */
    private Optional<int[]> freiesFeld(List<Parzelle> vorhandene) {
        boolean[][] belegt = new boolean[grenze.felderJeAchse()][grenze.felderJeAchse()];
        for (Parzelle stadt : vorhandene) {
            if (grenze.enthaeltFeld(stadt.x(), stadt.z())) {
                belegt[stadt.x() - grenze.von()][stadt.z() - grenze.von()] = true;
            }
        }
        int weiteste = Math.max(grenze.bis(), -grenze.von());
        for (int ring = 0; ring <= weiteste; ring++) {
            for (int x = -ring; x <= ring; x++) {
                for (int z = -ring; z <= ring; z++) {
                    // Nur der RAND des Rings - was weiter innen liegt, war schon dran.
                    if (Math.max(Math.abs(x), Math.abs(z)) != ring) {
                        continue;
                    }
                    if (!grenze.enthaeltFeld(x, z) || plan.istAnkunftsfeld(x, z)) {
                        continue;
                    }
                    if (!belegt[x - grenze.von()][z - grenze.von()]) {
                        return Optional.of(new int[] {x, z});
                    }
                }
            }
        }
        return Optional.empty();
    }
}
