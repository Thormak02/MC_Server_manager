package de.freimarken.kern;

import java.time.LocalDate;
import java.time.ZoneId;
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
        ERLAUBT_STADTBUERGER(true),
        /**
         * Die Stadt ist VERFALLEN - hier baut niemand mehr [Plan 16, Teil E5].
         *
         * <p><b>Es sticht ALLE DREI Ja-Urteile</b>, auch das des Besitzers und das des
         * Grundstuecksbesitzers. Das ist der Zweck: Eine verfallene Stadt soll
         * aussehen, als haette sie aufgehoert - sonst merkt niemand, dass sie es tat,
         * und die Regel aus Plan 16 waere eine Zeile in der Datenbank.
         *
         * <p><b>Der Besitzer belebt sie durch BETRETEN wieder</b>, nicht durch Bauen.
         * Darum trifft dieses Urteil auch ihn, und zwar hoechstens einen Taktschlag
         * lang: Der Besuchsstempel aus Teil E4 sieht ihn in der Stadt stehen und setzt
         * sie im naechsten Minutentakt auf aktiv. Der Satz dazu sagt es ihm.
         *
         * <p><b>Gefragt wird EINMAL und nicht in den drei Ja-Zweigen</b>: Drei Riegel
         * waeren drei Stellen, an denen der vierte vergessen wird. Die Antwort kommt
         * aus {@link Zustand#erlaubtBauen()} - der einen Stelle, die weiss, welcher
         * Zustand Bauen erlaubt.
         */
        VERFALLENE_STADT(false);

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
                         Stadtkasse stadtkasse, Warpverzeichnis warps,
                         Wirtschaftsstand wirtschaft) {
        this.grenze = grenze;
        this.plan = grenze.plan();
        this.lager = lager;
        this.kasse = kasse;
        // IM KONSTRUKTOR und nicht nachgereicht wie die Grundstuecksablage: Die
        // Stadtkasse entsteht zusammen mit der Spielerkasse, lange vor dem ersten
        // Befehl. Ein Buch, das sie nachgereicht bekommt, haette einen Zustand, in dem
        // es halb arbeitet - und der Abhebezweig fiele darin lautlos aus.
        this.stadtkasse = stadtkasse;
        this.warps = warps;
        this.wirtschaft = wirtschaft;
    }

    /** Die Kasse der STAEDTE - siehe {@link Stadtkasse}. */
    private final Stadtkasse stadtkasse;

    /**
     * Was in der Kasse dieser Stadt liegt - fuer die Auskunft.
     *
     * <p>Wirft bei einer Stoerung, wie {@link #darfBauen}: Der Aufrufer faengt das und
     * sagt GESTOERT.
     */
    public long kassenstand(Parzelle stadt) {
        return stadtkasse.stand(stadt);
    }

    /** Die letzten Buchungen dieser Stadt - fuer die Auskunft. */
    public List<Buchung> kassenverlauf(Parzelle stadt, int hoechstens) {
        return stadtkasse.verlauf(stadt, hoechstens);
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
        for (Parzelle stadt : lager.besitzIn(welt, Art.FREISTADT)) {
            if (stadt.gehoert(spieler)) {
                gezaehlt++;
            }
        }
        return gezaehlt;
    }

    /** Alle Staedte dieser Welt, nach Namen - fuer die Liste und fuer den Warp. */
    public List<Parzelle> staedte(String welt) {
        return lager.besitzIn(welt, Art.FREISTADT);
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
        for (Parzelle stadt : lager.besitzIn(welt, Art.FREISTADT)) {
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
        // DER ZUSTAND, und zwar VOR allen drei Ja-Zweigen [Teil E5]. Hier und nicht
        // dreimal weiter unten: Drei Riegel waeren drei Stellen, an denen der vierte
        // vergessen wird - und die Stadtzeile liegt nach lager.finde schon in der Hand,
        // also kostet die Frage nichts.
        //
        // Gefragt wird Zustand.erlaubtBauen() und nicht auf VERFALLEN verglichen: Jene
        // Methode ist die EINE Stelle, die weiss, welcher Zustand Bauen erlaubt, und
        // ihr Kopf sagt seit Schritt 8, warum es genau eine sein muss.
        if (!stadt.get().zustand().erlaubtBauen()) {
            return Bauurteil.VERFALLENE_STADT;
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
     *
     * <p><b>Und WIRKLICH eine Stadt</b> [Fund vom 17.11.2026]: Das Lager filtert
     * absichtlich nicht nach Art - {@code finde} muss jeden Belegten einer Rasterzelle
     * melden, sonst hielte eine Suche die Flaeche fuer frei, das Besitzbuch buchte den
     * Preis ab und liefe in den eindeutigen Schluessel. Marktstadt und Freistaedte
     * teilen aber dasselbe Lager, und die Feldnummern der zwei Geometrien
     * ueberschneiden sich: Block 150/150 der Marktstadt liegt in Stadtfeld 0/0. Ohne
     * diesen Filter gab die Methode dort eine gewoehnliche Parzelle zurueck, und jeder
     * Aufrufer lief anschliessend in {@link #auflage}, die fuer eine Nicht-Freistadt
     * wirft.
     *
     * <p>HIER und nicht beim Aufrufer, obwohl der Riegel in Teil E3b erst in
     * {@code Shopbuch} stand: Mit dem Besuchsstempel aus Teil E4 gibt es einen ZWEITEN
     * Aufrufer, der eine beliebige Welt hereingibt. Zwei Riegel fuer dieselbe Frage
     * laufen auseinander - und diese Methode heisst "stadtAn" und verspricht eine
     * STADT.
     */
    public Optional<Parzelle> stadtAn(String welt, int blockX, int blockZ) {
        if (plan.feldBei(blockX, blockZ) != Stadtfeldplan.Feld.STADTFELD) {
            return Optional.empty();
        }
        return lager.finde(welt, plan.feldNummer(blockX), plan.feldNummer(blockZ))
                .filter(Parzelle::istFreistadt);
    }

    /**
     * Stempelt den heutigen Tag als letzten Besuch dieser Stadt [Teil E4].
     *
     * <p><b>Der Tag kommt HEREIN.</b> Dieses Buch hat keine Uhr und keine Zone, und das
     * soll so bleiben: "heute" ist eine Rechnung aus Zeitpunkt UND Zone
     * ({@code Tagesgrenze.tagVon}), und wer sie hier anstellte, braeuchte beide als
     * Feld - zwei weitere Dinge, die bei jedem Neuladen aktuell sein muessen.
     *
     * <p><b>Geschrieben wird nur, wenn der Tag sich AENDERT.</b> Das ist die Wahrheit
     * hinter dem billigen Vortest des Laufs: Jener merkt sich im Arbeitsspeicher,
     * welche Stadt heute schon gestempelt ist, und ueberlebt keinen Neustart. Diese
     * Zeile ueberlebt ihn - nach einem Neustart mitten am Tag wird nicht ein zweites
     * Mal geschrieben.
     *
     * <p><b>Ein Tag in der ZUKUNFT wird ueberschrieben</b>, anders als bei der
     * Tagesserie: Dort haelt ein kuenftiger Tag die Zahlung an, weil sonst eine
     * verstellte Uhr Geld verschenkte. Hier gibt es nichts zu verschenken, und der
     * Besuch findet JETZT statt - ein Stempel von morgen ist ein kaputter Wert, und
     * dieser Zweig heilt ihn.
     *
     * @return {@code true}, wenn wirklich geschrieben wurde
     */
    public boolean stempleBesuch(Parzelle stadt, LocalDate heute) {
        if (stadt == null || !stadt.istFreistadt()) {
            throw new IllegalArgumentException("Besucht wird eine Freistadt");
        }
        if (heute == null) {
            throw new IllegalArgumentException("Ohne Tag gibt es keinen Stempel - wer"
                    + " stempelt, hat heute ueber Tagesgrenze.tagVon bestimmt");
        }
        if (heute.equals(stadt.besucht())) {
            return false;
        }
        lager.besuchSetzen(stadt.id(), heute);
        return true;
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

        List<Parzelle> vorhandene = lager.besitzIn(welt, Art.FREISTADT);
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

    // --- Teil E3: die Auflage -------------------------------------------------

    /** Warum eine Stadt die Auflage nicht erfuellt - oder dass sie es tut. */
    public enum Auflage {

        /** Beides da: Regeln und mindestens ein Stadtbuerger. */
        ERFUELLT,

        /** Es fehlt der Regeltext. */
        OHNE_REGELN,

        /** Es fehlt ein vergebenes Grundstueck - also ein Stadtbuerger. */
        OHNE_BUERGER,

        /** Beides fehlt - der Zustand jeder frisch gegruendeten Stadt. */
        OHNE_BEIDES;

        /** Ob die Auflage erfuellt ist. */
        public boolean erfuellt() {
            return this == ERFUELLT;
        }
    }

    /**
     * Ob diese Stadt die Auflage aus Plan 16 erfuellt - die EINE Stelle.
     *
     * <p>Zwei Bedingungen, beide noetig: ein REGELTEXT und mindestens ein vergebenes
     * Grundstueck. Das zweite ist nach Teil D dasselbe wie "mindestens ein
     * Stadtbuerger" - ein Grundstueck, das nicht dem Stadtbesitzer gehoert, macht
     * genau einen Buerger. Darum fragt diese Methode nicht zwei verschiedene Dinge,
     * sondern dieselbe Frage einmal.
     *
     * <p><b>Warum sie den GRUND zurueckgibt und nicht nur ja oder nein:</b> Der Satz an
     * den Stadtbesitzer soll sagen, was ihm fehlt. Mit einem {@code boolean} muesste
     * der Aufrufer beide Bedingungen erneut fragen, um das herauszufinden - zwei Wege
     * zur selben Frage, und der zweite bekommt die naechste Verschaerfung nicht mit.
     *
     * <p><b>Ohne Grundstuecksablage ist die Auflage NICHT erfuellbar</b>, und das ist
     * die unangenehme, aber richtige Antwort: Ohne sie weiss niemand, ob ein Grundstueck
     * vergeben ist. Dass daraus keine Sperre wird, entscheidet der AUFRUFER - und das
     * gilt fuer JEDEN: {@link #darfReisen} laesst dann jeden hinein, {@link #darfLaden}
     * laesst dann jeden Laden entstehen, und der Startbericht sagt es. Eine
     * Wirtschaftsregel, die ausfaellt, darf nicht den Zugang kosten.
     *
     * <p><b>Wer einen dritten Riegel an diese Methode haengt, braucht denselben
     * Zweig</b> und darf {@code auflage} nicht unmittelbar fragen: Sie gibt bei
     * stummer Grundstuecksablage in JEDER Freistadt {@code OHNE_BEIDES} - also die
     * Vollsperre, die die zwei vorhandenen Riegel ausdruecklich abfangen.
     */
    public Auflage auflage(Parzelle stadt) {
        if (stadt == null || !stadt.istFreistadt()) {
            throw new IllegalArgumentException("Eine Auflage haben nur Freistaedte");
        }
        boolean regeln = stadt.hatRegeln();
        boolean buerger = hatStadtbuerger(stadt);
        if (regeln && buerger) {
            return Auflage.ERFUELLT;
        }
        if (!regeln && !buerger) {
            return Auflage.OHNE_BEIDES;
        }
        return regeln ? Auflage.OHNE_BUERGER : Auflage.OHNE_REGELN;
    }

    /**
     * Ob diese Stadt mindestens einen Stadtbuerger hat.
     *
     * <p>Gefragt wird die ABLAGE und nicht eine Zahl an der Stadtzeile: Buergerschaft
     * ist nach Teil D abgeleitet und nie gespeichert, damit die Liste nicht veralten
     * kann. Hier zahlt sich das aus - es gibt keinen Zaehler, der falsch stehen koennte.
     *
     * <p>Ohne Ablage ist niemand Buerger, wie in {@link #istStadtbuerger} und mit
     * derselben Begruendung: Dann gibt es keine Grundstuecke, also auch keinen, dem
     * eines gehoert.
     */
    private boolean hatStadtbuerger(Parzelle stadt) {
        if (grundstuecke == null) {
            return false;
        }
        for (Grundstueck eines : grundstuecke.inStadt(stadt.id())) {
            if (Stadtbuergerschaft.istBuerger(stadt, eines.eigentuemer(), true)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Ob dieser Spieler in diese Stadt reisen darf.
     *
     * <p><b>Vier Wege hinein, und drei davon gelten immer:</b> Der EIGENTUEMER muss
     * immer hinein - sonst ist die Gruendung eine 400.000-Mark-Falle, denn eine frische
     * Stadt hat nie ein vergebenes Grundstueck, und er kaeme nicht hinein, um eines
     * zuzuschneiden. Ein MITBAUER wurde genau zum Bauen eingetragen. Und ein
     * STADTBUERGER hat sein Grundstueck bezahlt; ein bezahltes Grundstueck, das niemand
     * mehr betreten kann, weil ein Dritter das seine zurueckgekauft hat, waere der
     * schwerere Fehler. Fuer alle anderen gilt die Auflage.
     *
     * <p><b>Ohne Grundstuecksablage darf JEDER</b>, und der Startbericht sagt es. Die
     * sichere Richtung heisst hier nicht "weniger Schaden": Sie hiesse, dass in diesem
     * Lauf niemand in irgendeine Freistadt kommt - und der Riegel schuetzt dabei
     * nichts, denn der Bauschutz haengt nicht an ihm. Eine Auflage ist eine
     * Wirtschaftsregel und keine Schutzwache.
     *
     * @return {@link Auflage#ERFUELLT}, wenn er hinein darf; sonst der GRUND, der ihn
     *         draussen haelt - der Satz an den Spieler nennt ihn
     */
    public Auflage darfReisen(Parzelle stadt, UUID spieler) {
        if (!wirtschaft.werte().freistaedte().auflageTeleporter()) {
            return Auflage.ERFUELLT;
        }
        if (grundstuecke == null) {
            // Die Auflage ist in diesem Lauf nicht pruefbar - siehe das Javadoc. Der
            // Startbericht nennt das ausdruecklich; ohne diese Zeile waere es eine
            // Regel, die halb wirkt und nichts davon sagt.
            return Auflage.ERFUELLT;
        }
        if (spieler != null && (stadt.gehoert(spieler)
                || stadt.mitbauer().contains(spieler)
                || istStadtbuerger(stadt.welt(), stadt, spieler))) {
            return Auflage.ERFUELLT;
        }
        return auflage(stadt);
    }

    /**
     * Ob in dieser Stadt ein NEUER Kistenshop entstehen darf - der zweite Riegel.
     *
     * <p><b>Der Unterschied zu {@link #darfReisen} ist der wichtigste Satz hier: Es
     * gibt KEINE Ausnahme, auch nicht fuer den Stadtbesitzer.</b> Beim Teleporter muss
     * er hinein, sonst ist die Gruendung eine 400.000-Mark-Falle - er kaeme nicht in
     * seine eigene Stadt, um das erste Grundstueck zuzuschneiden. Hier sperrt ihn
     * nichts ein: Einen Regeltext setzt er mit einem Befehl, ein Grundstueck schneidet
     * er zu und bietet es an, und beides geht ohne einen einzigen Laden. Liesse man
     * ihn aus, duerfte er seine ganze Markthalle bauen und die Auflage nie erfuellen -
     * dann waere der Riegel ein Riegel gegen seine Buerger und nicht gegen die
     * Geisterstadt.
     *
     * <p><b>Ohne Grundstuecksablage faellt die Regel AUS und sperrt nicht</b>, genau wie
     * bei {@link #darfReisen} und aus einem schaerferen Grund: Ohne sie ist
     * {@link #auflage} nie {@code ERFUELLT}, denn Buergerschaft ist aus den
     * Grundstuecken abgeleitet. Ein Riegel, der das nicht abfaengt, verweigert bei
     * stummer Datenbank in JEDER Freistadt jeden neuen Kistenshop - eine
     * Wirtschaftsregel, die beim Ausfall zur Vollsperre wird.
     *
     * @param stadt die Stadt, in der die KISTE steht - nie {@code null}
     * @return {@link Auflage#ERFUELLT}, wenn der Shop entstehen darf; sonst der GRUND,
     *         den der Satz an den Spieler nennt
     */
    public Auflage darfLaden(Parzelle stadt) {
        if (!wirtschaft.werte().freistaedte().auflageLadenrecht()) {
            return Auflage.ERFUELLT;
        }
        if (grundstuecke == null) {
            // Siehe das Javadoc: Hier faellt die Regel aus, und der Startbericht sagt
            // es. Ohne diesen Zweig waere es die Vollsperre.
            return Auflage.ERFUELLT;
        }
        return auflage(stadt);
    }

    // --- Teil E5: der Verfall -------------------------------------------------

    /**
     * Wie weit diese Stadt auf dem Weg zum Verfall ist.
     *
     * <p>Die EINE Frage fuer alle drei Leser - den taeglichen Lauf, den Beitritt und die
     * Liste. Dieses Buch fuegt der Rechnung in {@link Verfallsregel} genau eine Angabe
     * hinzu, die jene nicht holen kann: ob die Stadt einen Stadtbuerger hat. Dafuer
     * braucht es die Grundstuecke.
     *
     * <p><b>Ohne Grundstuecksablage gilt die Stadt als BEWOHNT</b> - und damit als
     * aktiv. Das ist die umgekehrte Richtung als bei {@link #auflage}, und es ist
     * dieselbe Lesart: Eine Wirtschaftsregel, die ausfaellt, darf nicht schaden. Dort
     * hiesse "niemand ist Buerger" eine Vollsperre, hier hiesse es, dass der taegliche
     * Lauf JEDE unbesuchte Stadt abraeumt, obwohl in der Haelfte Leute wohnen. Der
     * Startbericht sagt es.
     */
    public Verfallsregel.Stand verfallsstand(Parzelle stadt, LocalDate heute,
            ZoneId zone) {
        boolean bewohnt = grundstuecke == null || hatStadtbuerger(stadt);
        return Verfallsregel.stand(stadt, bewohnt,
                wirtschaft.werte().freistaedte().verfall(), heute, zone);
    }

    /** Wie viele Tage es noch bis zum Verfall sind - fuer den Satz an den Besitzer. */
    public long resttage(Parzelle stadt, LocalDate heute, ZoneId zone) {
        return Verfallsregel.resttage(stadt,
                wirtschaft.werte().freistaedte().verfall(), heute, zone);
    }

    /**
     * Setzt diese Stadt auf VERFALLEN - gerufen vom taeglichen Lauf.
     *
     * <p>Schreibt nur, wenn sie es noch nicht ist. Jeder Schreibvorgang leert den
     * Zwischenspeicher aller Parzellen, und ein Lauf, der taeglich denselben Zustand
     * neu setzt, leerte ihn fuer nichts.
     *
     * @return {@code true}, wenn wirklich geschrieben wurde
     */
    public boolean lasseVerfallen(Parzelle stadt) {
        if (stadt == null || !stadt.istFreistadt()) {
            throw new IllegalArgumentException("Verfallen koennen nur Freistaedte");
        }
        if (stadt.zustand() == Zustand.VERFALLEN) {
            return false;
        }
        lager.zustandSetzen(stadt.id(), Zustand.VERFALLEN);
        return true;
    }

    /**
     * Belebt eine verfallene Stadt wieder - gerufen, wenn ihr BESITZER darin steht.
     *
     * <p><b>Nur der Besitzer</b>, und das ist die Entscheidung: Ein Fremder, der durch
     * eine verfallene Stadt laeuft, belebte sie sonst fuer ihren abwesenden Besitzer
     * mit - und die Geisterstadt bliebe stehen, solange irgendjemand sie als Abkuerzung
     * benutzt. Ein Mitbauer ebenfalls nicht: Er wurde zum Bauen eingetragen, nicht zum
     * Erhalten.
     *
     * <p><b>Der Besuchsstempel faellt trotzdem fuer jeden</b> - das ist Teil E4 und
     * eine andere Frage. Eine verfallene Stadt, die jemand betritt, bekommt also ihren
     * Tag; aufwachen tut sie erst, wenn ihr Besitzer kommt. Ohne die Trennung waere der
     * Stempel eine Wiederbelebung durch Dritte auf Umwegen.
     *
     * @return {@code true}, wenn sie wirklich aufgewacht ist
     */
    public boolean beleb(Parzelle stadt, UUID spieler) {
        if (stadt == null || !stadt.istFreistadt()) {
            throw new IllegalArgumentException("Beleben kann man nur eine Freistadt");
        }
        if (stadt.zustand() != Zustand.VERFALLEN || spieler == null
                || !stadt.gehoert(spieler)) {
            return false;
        }
        lager.zustandSetzen(stadt.id(), Zustand.AKTIV);
        return true;
    }

    // --- Teil E6: die Ladengebuehr --------------------------------------------

    /**
     * Was ein Laden dieser Stadt zahlt - und ob derjenige es kann.
     *
     * @param betrag     0, wenn die Gebuehr abgeschaltet ist
     * @param bezahlbar  ob sein Guthaben reicht. Bei Betrag 0 immer {@code true}
     */
    public record Ladengebuehr(long betrag, boolean bezahlbar) {

        /** Ob ueberhaupt etwas zu erheben ist. */
        public boolean faellt() {
            return betrag > 0L;
        }
    }

    /**
     * Prueft die EINMALIGE Ladengebuehr, ohne etwas zu buchen [Teil E6].
     *
     * <p><b>Getrennt vom Erheben</b>, und das ist die ganze Reihenfolge: Wer nicht
     * zahlen kann, soll keinen Shop bekommen UND kein Geld verlieren - also muss das
     * Nachsehen vor dem Schreiben kommen und das Abbuchen danach. Eine Methode, die
     * beides tut, liesse dem Aufrufer nur die Wahl zwischen "zahlt fuer einen Shop, der
     * nicht entsteht" und "bekommt einen Shop, ohne zu zahlen".
     */
    public Ladengebuehr pruefeLadengebuehr(Parzelle stadt, UUID wer) {
        long betrag = wirtschaft.werte().freistaedte().ladengebuehrAnlage();
        if (betrag <= 0L || wer == null) {
            return new Ladengebuehr(0L, true);
        }
        return new Ladengebuehr(betrag, kasse.guthaben(wer) >= betrag);
    }

    /**
     * Erhebt sie wirklich - vom Spieler in die Stadtkasse.
     *
     * <p>ZWEI Buchungen fuer eine Bewegung, wie ueberall in diesem Projekt: Ein Konto
     * traegt nur seine eigenen Zeilen. Beim Spieler eine Ausgabe, in der Stadt eine
     * Einnahme.
     *
     * <p><b>Abbuchen zuerst.</b> Scheitert es, ist nichts geschehen; scheiterte
     * umgekehrt die Gutschrift, haette die Stadt Geld, das niemand bezahlt hat.
     *
     * @return {@code false}, wenn das Abbuchen nicht ging - dann ist NICHTS gebucht
     */
    public boolean erhebeLadengebuehr(Parzelle stadt, UUID wer, long betrag) {
        if (betrag <= 0L) {
            return true;
        }
        if (!kasse.abbuchen(wer, betrag, Buchungsgrund.FREISTADT_LADENGEBUEHR)) {
            return false;
        }
        stadtkasse.einzahlen(stadt, betrag, Buchungsgrund.STADTKASSE_LADENGEBUEHR);
        return true;
    }

    // --- Teil E6: das Ende einer Stadt ----------------------------------------

    /** Wie ein Ende ausgegangen ist. */
    public enum Endebescheid {

        /** Die Stadt ist weg, das Geld ist ausgezahlt. */
        BEENDET,

        /** Hier steht keine Stadt (beim Aufgeben) oder den Namen gibt es nicht. */
        KEINE_STADT,

        /** Die Stadt eines anderen - nur ihr Besitzer gibt sie auf. */
        FREMDE_STADT,

        /**
         * In der Stadt gehoert noch jemand anderem ein Grundstueck.
         *
         * <p><b>Der Riegel, ohne den dieser Befehl ein Diebstahl waere</b>: Ein
         * Stadtbuerger hat sein Grundstueck BEZAHLT. Wer die Stadt darueber hinweg
         * aufloeste, naehme ihm Grund und Bauten und gaebe ihm nichts - und zwar fuer
         * eine Entscheidung, die ein anderer getroffen hat.
         *
         * <p>Es ist auch kein Verlust fuer den Besitzer: Eine Stadt mit Buergern
         * verfaellt nie, blockiert also kein Feld unnoetig, und wer sie wirklich
         * beenden will, kauft die Grundstuecke zuvor zurueck - das kann er.
         */
        NOCH_BUERGER,

        /**
         * Die Stadt ist weg, aber das Geld ist NICHT angekommen.
         *
         * <p>Ein eigener Bescheid und nicht {@link #GESTOERT}: Hier ist etwas
         * Unumkehrbares schon geschehen, und der Spieler soll das lesen - samt der
         * Aufforderung, sich an den Betreiber zu wenden. Der Betrag und sein Name
         * stehen im Protokoll.
         */
        GELD_HAENGT,

        /** Die Datenbank hat geschwiegen - es ist NICHTS geschehen. */
        GESTOERT
    }

    /**
     * Das Ergebnis eines Endes.
     *
     * @param erstattung was aus dem Gruendungspreis zurueckkam
     * @param kasse      was in der Stadtkasse lag und mit ausgezahlt wurde. Getrennt
     *                   genannt, damit der Satz beide Zahlen zeigen kann: Sonst liest
     *                   ein Besitzer eine Summe, die er nicht nachrechnen kann
     */
    public record Endeentscheid(Endebescheid bescheid, Optional<Parzelle> stadt,
                                long erstattung, long kasse) {

        /** Was zusammen ausgezahlt wurde. */
        public long zusammen() {
            return erstattung + kasse;
        }
    }

    /**
     * Was diese Stadt bei ihrem Ende erstatten wuerde - fuer die Rueckfrage.
     *
     * <p>DIESELBE Rechnung, die {@code beende} anstellt, und darum eine Methode: Zeigte
     * die Rueckfrage eine andere Zahl als die Tat, waere das Bestaetigen eine
     * Unterschrift unter einen Betrag, der nicht gilt.
     *
     * <p>Vom EINGESETZTEN Preis der Zeile und nicht vom heutigen aus der Datei - die
     * Begruendung steht bei {@code Freistadtwerte.VORGABE_ERSTATTUNG}.
     */
    public long erstattungFuer(Parzelle stadt) {
        if (stadt == null || !stadt.istFreistadt()) {
            throw new IllegalArgumentException("Erstattet wird fuer eine Freistadt");
        }
        return Shopregel.gebuehrAuf(stadt.preis(),
                wirtschaft.werte().freistaedte().erstattungProzent());
    }

    /**
     * Der Besitzer gibt seine Stadt auf - am STANDORT, wie jeder Stadtbefehl.
     *
     * <p>Am Standort und nicht nach Namen: Was ein Gebiet betrifft, entscheidet man im
     * Gebiet. Und es ist der schaerfere Weg - wer vor seiner Stadt steht, verwechselt
     * sie nicht mit einer anderen gleichen Namens.
     */
    public Endeentscheid gibAuf(String welt, UUID spieler, int blockX, int blockZ) {
        Optional<Parzelle> stadt;
        try {
            stadt = stadtAn(welt, blockX, blockZ);
        } catch (RuntimeException fehler) {
            return nichts(Endebescheid.GESTOERT);
        }
        if (stadt.isEmpty()) {
            return nichts(Endebescheid.KEINE_STADT);
        }
        if (spieler == null || !stadt.get().gehoert(spieler)) {
            return new Endeentscheid(Endebescheid.FREMDE_STADT, stadt, 0L, 0L);
        }
        return beende(stadt.get());
    }

    /**
     * Das Team raeumt eine Stadt - nach NAMEN, von irgendwo.
     *
     * <p>Nach Namen und nicht am Standort, anders als {@link #gibAuf}: Eine verfallene
     * Stadt zu betreten, um sie zu raeumen, waere der eine Weg, der sie vorher
     * aufwecken wuerde - der Besuchsstempel sieht jeden, der darin steht. Und ein
     * Teammitglied raeumt nicht dort, wo es gerade baut.
     *
     * <p><b>Die ERSTATTUNG geht an den BESITZER</b>, nicht an das Teammitglied. Das ist
     * der ganze Unterschied zum Aufgeben: Geraeumt wird fremdes Eigentum, und wer es
     * verliert, bekommt dafuer, was ihm zusteht - auch wenn er gerade nicht da ist.
     */
    public Endeentscheid raeume(String welt, String name) {
        Optional<Parzelle> stadt;
        try {
            stadt = staedte(welt).stream()
                    .filter(eine -> eine.name().equalsIgnoreCase(name))
                    .findFirst();
        } catch (RuntimeException fehler) {
            return nichts(Endebescheid.GESTOERT);
        }
        if (stadt.isEmpty()) {
            return nichts(Endebescheid.KEINE_STADT);
        }
        return beende(stadt.get());
    }

    /**
     * Die eine Tat hinter beiden Wegen.
     *
     * <p><b>ERST ENTFERNEN, DANN ZAHLEN</b>, und das ist die gefaehrlichste Entscheidung
     * dieser Klasse. Umgekehrt - zahlen, dann entfernen - machte aus einem
     * gescheiterten Entfernen eine GELDQUELLE: Die Stadt stuende noch, der Spieler
     * tippt den Befehl erneut und wird ein zweites Mal bezahlt. So dagegen ist der
     * schlimmste Fall eine Schuld, die mit Namen und Betrag im Protokoll steht und die
     * ein Betreiber von Hand begleichen kann.
     *
     * <p><b>Das Guthaben der Stadtkasse geht MIT.</b> Ohne diese Zeile waere es
     * verloren: Das Konto bleibt bestehen - seine Nummer ist eine UUID und wird nie
     * wieder vergeben -, aber niemand kaeme mehr daran, denn der Weg dorthin fuehrt
     * ueber die Stadtzeile. Das Geld lag also fuer immer unerreichbar auf einem Konto,
     * das zu keiner Stadt mehr gehoert.
     *
     * <p><b>Die Grundstuecke des BESITZERS verschwinden mit</b>, und nur seine - fremde
     * halten den Befehl vorher an ({@link Endebescheid#NOCH_BUERGER}). Sie zu lassen
     * waere schlimmer als sie zu loeschen: Zeilen, die auf eine Stadt zeigen, die es
     * nicht mehr gibt, und in deren Feld irgendwann eine neue entsteht.
     */
    private Endeentscheid beende(Parzelle stadt) {
        long erstattung;
        long kassenstand;
        try {
            if (fremdesGrundstueckIn(stadt)) {
                return new Endeentscheid(Endebescheid.NOCH_BUERGER,
                        Optional.of(stadt), 0L, 0L);
            }
            // Beide Zahlen VOR dem Entfernen holen: Danach gibt es die Zeile nicht
            // mehr, und der Preis stand in ihr.
            erstattung = erstattungFuer(stadt);
            kassenstand = stadtkasse.stand(stadt);
            if (kassenstand > 0L && !stadtkasse.abbuchen(stadt, kassenstand,
                    Buchungsgrund.STADTKASSE_ABGEHOBEN)) {
                // Darf nicht vorkommen - wir haben den Stand gerade gelesen. Wenn
                // doch, ist NICHTS geschehen, und das ist die richtige Antwort.
                return nichts(Endebescheid.GESTOERT);
            }
            if (grundstuecke != null) {
                for (Grundstueck eigenes : grundstuecke.inStadt(stadt.id())) {
                    grundstuecke.entfernen(eigenes.id());
                }
            }
            lager.entfernen(stadt.id());
        } catch (RuntimeException fehler) {
            return nichts(Endebescheid.GESTOERT);
        }
        // AB HIER IST DIE STADT WEG. Was jetzt noch schiefgeht, kostet kein Feld mehr -
        // nur Geld, und das steht mit Namen und Betrag im Protokoll.
        try {
            long zusammen = erstattung + kassenstand;
            if (erstattung > 0L) {
                kasse.gutschreiben(stadt.eigentuemer(), erstattung,
                        Buchungsgrund.FREISTADT_AUFGEGEBEN);
            }
            if (kassenstand > 0L) {
                kasse.gutschreiben(stadt.eigentuemer(), kassenstand,
                        Buchungsgrund.STADTKASSE_AUSGEZAHLT);
            }
            if (zusammen == 0L) {
                // Nichts zu zahlen ist kein Fehler: Bei erstattung-prozent 0 und leerer
                // Kasse ist das der Normalfall.
                return new Endeentscheid(Endebescheid.BEENDET, Optional.of(stadt),
                        0L, 0L);
            }
        } catch (RuntimeException fehler) {
            return new Endeentscheid(Endebescheid.GELD_HAENGT, Optional.of(stadt),
                    erstattung, kassenstand);
        }
        return new Endeentscheid(Endebescheid.BEENDET, Optional.of(stadt), erstattung,
                kassenstand);
    }

    /**
     * Ob in dieser Stadt ein Grundstueck jemand ANDEREM als dem Besitzer gehoert.
     *
     * <p>Ohne Ablage gilt: NEIN - und das ist hier die sichere Richtung nach unten.
     * Wer nicht wissen kann, ob jemand ein Grundstueck hat, soll keine Stadt
     * aufloesen... aber ohne Ablage gibt es auch keine Grundstuecke, die verloren gehen
     * koennten: Jede Zeile darueber ist unlesbar, also kann der Befehl keine zerstoeren.
     * Die Stadt selbst gehoert dem, der sie aufgibt.
     */
    private boolean fremdesGrundstueckIn(Parzelle stadt) {
        if (grundstuecke == null) {
            return false;
        }
        for (Grundstueck eines : grundstuecke.inStadt(stadt.id())) {
            if (!stadt.gehoert(eines.eigentuemer())) {
                return true;
            }
        }
        return false;
    }

    private static Endeentscheid nichts(Endebescheid bescheid) {
        return new Endeentscheid(bescheid, Optional.empty(), 0L, 0L);
    }

    // --- Teil E2: der Regeltext -----------------------------------------------

    /** Wie ein Regelzug ausgegangen ist. */
    public enum Regelbescheid {

        /** Die Regeln stehen jetzt. */
        GESETZT,

        /** Die Regeln sind weg - der Weg zurueck in den Anfangszustand. */
        GELOESCHT,

        /** Genau dieser Text stand schon da. */
        SCHON_SO,

        /** Es gab gar keine Regeln, und geloescht werden sollte. */
        WAR_SCHON_LEER,

        /** Der Text ist laenger als erlaubt. */
        ZU_LANG,

        /** Hier steht keine Stadt. */
        KEINE_STADT,

        /** Die Stadt gehoert einem anderen. */
        FREMDE_STADT,

        /** Die Ablage war stumm. */
        GESTOERT
    }

    /**
     * Das Ergebnis eines Regelzuges.
     *
     * @param stadt die Stadt, um die es ging - bei KEINE_STADT leer. Sie traegt den
     *              Text, wie er NACHHER ist; ein vom Aufrufer nachgebauter Stand war in
     *              Teil C3 schon einmal der Fund
     */
    public record Regelentscheid(Regelbescheid bescheid, Optional<Parzelle> stadt) {
    }

    /**
     * Den Regeltext einer Stadt setzen oder loeschen - am STANDORT des Spielers.
     *
     * <p>Am Standort und nur fuer den Eigentuemer, wie {@link #setzeRecht}: Was in einem
     * Gebiet gilt, stellt man im Gebiet ein, und was Besucher dort lesen, entscheidet,
     * wem es gehoert. Nicht einmal ein Mitbauer - er wurde zum Bauen eingetragen.
     *
     * <p><b>Die Laenge wird HIER geprueft und nicht erst in {@link Parzelle}</b>, obwohl
     * der Riegel dort auch steht: Dieser Weg fuehrt zu einem Spieler, und ein Spieler
     * soll einen Satz lesen und keine Ausnahme ausloesen. Der Riegel in Parzelle bleibt
     * trotzdem - er faengt jeden anderen Weg, auch den, den heute noch niemand gebaut
     * hat.
     *
     * @param text der neue Text; {@code null} oder leer LOESCHT die Regeln. Das ist der
     *             Weg zurueck in den Anfangszustand - ohne ihn koennte ein
     *             Stadtbesitzer eine Regel aufstellen und nie zurueckziehen
     */
    public Regelentscheid setzeRegeln(String welt, UUID spieler, int blockX, int blockZ,
                                      String text) {
        boolean loeschen = text == null || text.isBlank();
        if (!loeschen && text.length() > Parzelle.REGELN_GROESSTENS) {
            // VOR dem Blick in die Ablage: Wer sich verschreibt, soll das nicht erst
            // erfahren, nachdem die Datenbank gefragt wurde - dieselbe Sortierung nach
            // Kosten wie bei gruende().
            return new Regelentscheid(Regelbescheid.ZU_LANG, Optional.empty());
        }
        Optional<Parzelle> stadt;
        try {
            stadt = stadtAn(welt, blockX, blockZ);
        } catch (RuntimeException fehler) {
            return new Regelentscheid(Regelbescheid.GESTOERT, Optional.empty());
        }
        if (stadt.isEmpty()) {
            return new Regelentscheid(Regelbescheid.KEINE_STADT, Optional.empty());
        }
        Parzelle hier = stadt.get();
        if (spieler == null || !hier.gehoert(spieler)) {
            return new Regelentscheid(Regelbescheid.FREMDE_STADT, stadt);
        }
        if (loeschen && !hier.hatRegeln()) {
            return new Regelentscheid(Regelbescheid.WAR_SCHON_LEER, stadt);
        }
        if (!loeschen && text.equals(hier.regeln())) {
            // Kein Schreibzug fuer nichts - dieselbe Entscheidung wie SCHON_SO bei den
            // Buergerrechten. Und ein Satz darueber, damit der Besitzer nicht glaubt,
            // sein Text sei angekommen und etwas anderes stehe da.
            return new Regelentscheid(Regelbescheid.SCHON_SO, stadt);
        }
        String neu = loeschen ? Parzelle.KEINE_REGELN : text;
        try {
            lager.regelnSetzen(hier.id(), neu);
        } catch (RuntimeException fehler) {
            return new Regelentscheid(Regelbescheid.GESTOERT, stadt);
        }
        return new Regelentscheid(
                loeschen ? Regelbescheid.GELOESCHT : Regelbescheid.GESETZT,
                Optional.of(hier.mitRegeln(neu)));
    }

    // --- Teil E1: die Stadtkasse ----------------------------------------------

    /** Wie ein Abhebezug ausgegangen ist. */
    public enum Kassenbescheid {

        /** Abgehoben - das Geld ist beim Spieler. */
        ABGEHOBEN,

        /** Hier steht keine Stadt. */
        KEINE_STADT,

        /** Die Stadt gehoert einem anderen. */
        FREMDE_STADT,

        /** So viel liegt nicht in der Kasse. */
        ZU_WENIG,

        /** Die Ablage oder die Kasse war stumm. */
        GESTOERT
    }

    /**
     * Das Ergebnis eines Abhebezuges.
     *
     * @param stadt   die Stadt, um die es ging - bei KEINE_STADT leer
     * @param betrag  was BEWEGT wurde; bei jedem Bescheid ausser ABGEHOBEN eine 0
     * @param stand   was NACHHER in der Kasse liegt. Zurueckgegeben und nicht vom
     *                Aufrufer nachgefragt: Ein zweiter Blick in die Kasse koennte
     *                etwas anderes sehen, und der Satz an den Spieler wuerde dann eine
     *                Zahl nennen, die zu seiner Buchung nicht passt
     */
    public record Kassenentscheid(Kassenbescheid bescheid, Optional<Parzelle> stadt,
                                  long betrag, long stand) {
    }

    /**
     * Geld aus der Stadtkasse nehmen - am STANDORT des Spielers.
     *
     * <p>Am Standort und nur fuer den Eigentuemer, wie {@link #setzeRecht}: Die Kasse
     * gehoert der Stadt, und ueber ihr Geld entscheidet, wem die Stadt gehoert. Nicht
     * einmal ein Mitbauer darf das - er wurde zum Bauen eingetragen, nicht zum
     * Wirtschaften.
     *
     * <p><b>Die Reihenfolge ist ABBUCHEN, dann GUTSCHREIBEN</b>, und der Rueckweg ist
     * hier anders als beim Grundstueckskauf WIRKLICH sicher: Scheitert die Gutschrift,
     * hat der Spieler nichts bekommen - ein Zurueckschreiben an die Stadt kann also
     * nicht dazu fuehren, dass beide Geld haben. Beim Kauf war genau das der Grund, auf
     * den Rueckweg zu verzichten; hier ist er strikt besser als nichts, denn
     * schlimmstenfalls scheitert er und das Geld ist weg - derselbe Zustand wie ohne
     * ihn.
     *
     * @param betrag was abgehoben werden soll, ohne Vorzeichen
     */
    public Kassenentscheid hebeAb(String welt, UUID spieler, int blockX, int blockZ,
                                  long betrag) {
        Kasse.pruefeBetrag(betrag);
        Optional<Parzelle> stadt;
        try {
            stadt = stadtAn(welt, blockX, blockZ);
        } catch (RuntimeException fehler) {
            return new Kassenentscheid(Kassenbescheid.GESTOERT, Optional.empty(), 0L,
                    0L);
        }
        if (stadt.isEmpty()) {
            return new Kassenentscheid(Kassenbescheid.KEINE_STADT, Optional.empty(), 0L,
                    0L);
        }
        Parzelle hier = stadt.get();
        long stand;
        try {
            stand = stadtkasse.stand(hier);
        } catch (RuntimeException fehler) {
            return new Kassenentscheid(Kassenbescheid.GESTOERT, stadt, 0L, 0L);
        }
        // DIE BESITZFRAGE NACH dem Stand, und das ist Absicht: Der Satz an einen
        // Fremden nennt den Namen der Stadt und ihren Besitzer, nicht ihr Guthaben -
        // aber der Stand wird fuer den Zweig darunter ohnehin gebraucht, und zwei
        // Abfragen waeren zwei Antworten auf dieselbe Frage.
        if (spieler == null || !hier.gehoert(spieler)) {
            return new Kassenentscheid(Kassenbescheid.FREMDE_STADT, stadt, 0L, 0L);
        }
        if (betrag > stand) {
            // Die Zahl kommt MIT: Ein Spieler soll ausrechnen koennen, was fehlt -
            // dieselbe Entscheidung wie bei ZU_WENIG_MARK im Grundstueckskauf.
            return new Kassenentscheid(Kassenbescheid.ZU_WENIG, stadt, 0L, stand);
        }
        try {
            if (!stadtkasse.abbuchen(hier, betrag,
                    Buchungsgrund.STADTKASSE_ABGEHOBEN)) {
                // Zwischen dem Lesen und dem Abbuchen hat sich der Stand geaendert.
                // Nicht unmoeglich: Ein Grundstueckskauf in dieser Stadt zahlt ein, ein
                // zweiter Befehl hebt ab. Die Antwort ist dieselbe wie oben.
                return new Kassenentscheid(Kassenbescheid.ZU_WENIG, stadt, 0L,
                        stadtkasse.stand(hier));
            }
        } catch (RuntimeException fehler) {
            return new Kassenentscheid(Kassenbescheid.GESTOERT, stadt, 0L, stand);
        }
        try {
            kasse.gutschreiben(spieler, betrag, Buchungsgrund.STADTKASSE_AUSGEZAHLT);
        } catch (RuntimeException fehler) {
            legeZurueck(hier, betrag, fehler);
            return new Kassenentscheid(Kassenbescheid.GESTOERT, stadt, 0L, stand);
        }
        return new Kassenentscheid(Kassenbescheid.ABGEHOBEN, stadt, betrag,
                stand - betrag);
    }

    /**
     * Das Geld zurueck in die Stadtkasse, wenn die Gutschrift scheiterte.
     *
     * <p>Die Ausnahme wird an die urspruengliche GEHAENGT und nicht an ihre Stelle
     * gesetzt - derselbe Grund wie bei {@link #erstatte}: Sonst stuende im Logbuch der
     * zweite Fehler, und der erste waere weg.
     */
    private void legeZurueck(Parzelle stadt, long betrag, RuntimeException ursache) {
        try {
            stadtkasse.einzahlen(stadt, betrag, Buchungsgrund.STADTKASSE_ZURUECK);
        } catch (RuntimeException auchDas) {
            ursache.addSuppressed(auchDas);
            throw ursache;
        }
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
        // Die Ringsuche selbst steht seit dem 18.11.2026 in Feldfenster - das Wolkenbuch
        // braucht sie Zeile fuer Zeile genauso, und zwei Abschriften waeren zwei Orte,
        // an denen die Reihenfolge der Ringe auseinanderlaeuft.
        return grenze.fenster().freiesFeld(
                vorhandene.stream().map(stadt -> new int[] {stadt.x(), stadt.z()})
                        .toList(),
                Stadtfeldplan.ANKUNFTSFELD);
    }
}
