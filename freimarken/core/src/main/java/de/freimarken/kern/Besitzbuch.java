package de.freimarken.kern;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Die Regeln rund um Parzellenbesitz: nehmen, schuetzen, Mitbauer.
 *
 * <p>Hier steht das Spiel, nicht die Technik. Kein Bukkit, keine Datenbank - nur
 * {@link Stadtgrenze} fuer die Geometrie, {@link BesitzLager} fuer die Ablage und
 * {@link Kasse} fuer das Geld. Dadurch laesst sich jede Regel ohne Server pruefen.
 *
 * <p>Bezahlt wird bewusst hier und nicht beim Aufrufer: Es gibt genau einen Weg, auf dem
 * eine Parzelle den Besitzer wechselt, und auf genau diesem Weg wird abgebucht. Wer das
 * Pruefen des Guthabens und das Abbuchen trennt, hat zwei Wege zur selben Tat - und einer
 * von beiden vergisst irgendwann das Abbuchen.
 *
 * <p>Die Grundregel des Schutzes ist absichtlich streng: Gebaut wird nur auf einer
 * Parzelle, die einem gehoert oder auf der man Mitbauer ist. Wege und Mauern gehoeren
 * niemandem, freie Parzellen auch nicht. Wer anfangen will, nimmt sich erst eine - und
 * die erste ist gratis.
 */
public final class Besitzbuch {

    /** Ausgang eines Versuchs. */
    public enum Bescheid {
        ERTEILT,
        /** Hier ist gar keine Parzelle - Weg, Mauer, andere Welt oder vor der Stadt. */
        KEINE_PARZELLE,
        /** Bauland in der Stadt, aber noch niemandem vergeben. Nicht KEINE_PARZELLE. */
        NOCH_FREI,
        /** Die Parzelle gehoert schon jemandem. */
        SCHON_VERGEBEN,
        /** Jenseits der Stadtgrenze - nicht zu verwechseln mit GRENZE_ERREICHT. */
        AUSSERHALB,
        /** Die Rangobergrenze ist erreicht. */
        GRENZE_ERREICHT,
        /** Das Guthaben reicht nicht. */
        ZU_WENIG_MARK,
        /** Der Handelnde ist nicht Eigentuemer. */
        NICHT_EIGENTUEMER,
        /** Der Spieler steht schon auf der Liste. */
        SCHON_EINGETRAGEN,
        /** Der Spieler stand gar nicht auf der Liste. */
        NICHT_EINGETRAGEN,
        /** Die Parzelle gehoert zu einer Gruppe - einzeln geht hier nichts. */
        TEIL_EINER_GRUPPE,
        /** Nebenan ist keine Parzelle vergeben. Nicht KEINE_PARZELLE - das meint den eigenen Stand. */
        NACHBAR_FREI,
        /** Die Nachbarparzelle gehoert einem anderen Spieler. */
        NACHBAR_FREMD,
        /** Die beiden Felder stecken in ZWEI VERSCHIEDENEN Gruppen. */
        SCHON_IN_GRUPPE,
        /** Die beiden Felder sind schon dasselbe Grundstueck. Nicht SCHON_IN_GRUPPE. */
        SCHON_ZUSAMMEN,
        /** Die Gruppe waere groesser, als der Rang erlaubt. Nicht GRENZE_ERREICHT - das zaehlt Parzellen. */
        GRUPPE_ZU_GROSS,
        /** Hier ist keine Gruppe, die sich auftrennen liesse. */
        KEINE_GRUPPE,
        /** Der Spieler steht auf dem gefuellten Weg seiner Gruppe, braucht aber eine Rasterzelle. */
        AUF_DEM_WEG,
        /** Ohne dieses Feld laege das Grundstueck in zwei Teilen. */
        HAELT_ZUSAMMEN
    }

    /**
     * Antwort auf einen Versuch.
     *
     * @param bescheid was herausgekommen ist
     * @param kosten   was es gekostet hat bzw. gekostet haette, in Mark
     * @param parzelle die betroffene Parzelle, falls es eine gibt
     */
    public record Entscheid(Bescheid bescheid, long kosten, Optional<Parzelle> parzelle) {

        public boolean erteilt() {
            return bescheid == Bescheid.ERTEILT;
        }

        static Entscheid nein(Bescheid grund) {
            return new Entscheid(grund, 0L, Optional.empty());
        }

        static Entscheid nein(Bescheid grund, long kosten) {
            return new Entscheid(grund, kosten, Optional.empty());
        }
    }

    private final Stadtgrenze grenze;
    private final Rasterplan plan;
    private final BesitzLager lager;
    private final Kasse kasse;

    public Besitzbuch(Stadtgrenze grenze, BesitzLager lager, Kasse kasse) {
        this.grenze = grenze;
        this.plan = grenze.plan();
        this.lager = lager;
        this.kasse = kasse;
    }

    /** Wie weit die Stadt reicht. */
    public Stadtgrenze grenze() {
        return grenze;
    }

    public Rasterplan plan() {
        return plan;
    }

    public Kasse kasse() {
        return kasse;
    }

    /**
     * Alle Parzellen dieses Spielers in dieser Welt, nach Lage sortiert.
     *
     * <p>Geht ueber das Buch und nicht am Lager vorbei: Sonst haette ein Aufrufer zwei
     * Ansprechpartner, und der Grund, warum das Lager hinter dem Buch liegt, waere hin.
     */
    public List<Parzelle> parzellenVon(String welt, UUID spieler) {
        return lager.vonSpieler(welt, spieler);
    }

    /** Die Parzelle an dieser WELTkoordinate, falls dort ueberhaupt Bauland ist. */
    public Optional<Parzelle> parzelleAn(String welt, int weltX, int weltZ) {
        if (plan.feldBei(weltX, weltZ) != Rasterplan.Feld.PARZELLE) {
            return Optional.empty();
        }
        return lager.finde(welt, plan.parzelleX(weltX), plan.parzelleZ(weltZ));
    }

    /**
     * Darf dieser Spieler an dieser Stelle bauen oder abbauen?
     *
     * <p>Bewusst ohne Sonderfall fuer das Team: Wer mehr darf, bekommt das ueber ein Recht
     * und nicht dadurch, dass diese Regel aufgeweicht wird. Die Regel hier beantwortet
     * genau eine Frage, und sie beantwortet sie fuer alle gleich.
     */
    public boolean darfBauen(String welt, UUID spieler, int weltX, int weltZ) {
        return zustaendigerBesitz(welt, weltX, weltZ)
                .map(parzelle -> parzelle.darfBauen(spieler))
                .orElse(false);
    }

    /**
     * Der Besitz, der an dieser Stelle das Bauen regelt - Bauland oder Gruppeninneres.
     *
     * <p>Getrennt von {@link #parzelleAn}, und das ist der ganze Punkt: Jene Methode
     * beantwortet "auf welcher Rasterzelle stehe ich", und auf dem Band zwischen zwei
     * Zellen liefert {@code floorDiv} dafuer immer die KLEINERE der beiden. Ein
     * Schreibbefehl, der daraus seine Zeile zieht, traefe unter Umstaenden das
     * Nachbargrundstueck. Hier geht es um die andere Frage: Wem gehoert dieser Block?
     *
     * <p>Zugeschlagen wird nur das INNERE einer Gruppe. Der Aussenring bleibt Weg - sonst
     * waere die Gruppe von aussen nicht mehr abgegrenzt, und zwei benachbarte Gruppen
     * haetten keine Strasse mehr zwischen sich.
     *
     * <p>Zurueckgegeben wird die Ankerzelle und nicht die Gruppenzeile: Jedes Kind traegt
     * Eigentuemer, Mitbauer und Zustand vollstaendig (Plan 6.1), also ist jedes Kind eine
     * gueltige Antwort auf "wer darf hier bauen".
     *
     * <p>Was das im Hauptfaden kostet: auf Bauland EIN Nachsehen wie bisher. Auf Weg und
     * Mauer bisher keines, jetzt eines - und nur im Inneren einer Gruppe zwei bis vier.
     * Das ist der ehrliche Preis dafuer, dass ein gefuellter Weg bebaubar ist; ohne ihn
     * zahlte der Spieler 20.000 Mark fuer einen Grasstreifen, den er nicht anfassen darf.
     * Alle diese Abfragen gehen durch den Zwischenspeicher, der auch leere Antworten
     * einlagert.
     */
    public Optional<Parzelle> zustaendigerBesitz(String welt, int weltX, int weltZ) {
        Optional<Parzelle> anker =
                lager.finde(welt, plan.parzelleX(weltX), plan.parzelleZ(weltZ));
        if (plan.feldBei(weltX, weltZ) == Rasterplan.Feld.PARZELLE) {
            return anker;
        }
        // Der Kurzschluss steht VOR dem Bau der Lambda: Steckt die Ankerzelle in keiner
        // Gruppe, ist die Antwort nach einem Nachsehen fertig und es wird nichts belegt.
        if (anker.isEmpty() || !anker.get().hatElternteil()) {
            return Optional.empty();
        }
        long gruppe = anker.get().elternId();
        return plan.imInneren(weltX, weltZ,
                (zx, zz) -> lager.finde(welt, zx, zz)
                        .filter(kind -> kind.elternId() == gruppe)
                        .isPresent())
                ? anker
                : Optional.empty();
    }

    /**
     * Eine Parzelle nehmen.
     *
     * <p>Bei Erfolg ist der Preis bereits abgebucht. Bei jedem anderen Bescheid ist
     * nichts passiert - auch nicht an der Kasse.
     *
     * @param welt            Weltschluessel
     * @param spieler         wer sie haben will
     * @param weltX           Weltkoordinate, an der er steht
     * @param weltZ           Weltkoordinate, an der er steht
     * @param hoechstzahl     wie viele Parzellen sein Rang erlaubt
     */
    public Entscheid nehmen(String welt, UUID spieler, int weltX, int weltZ,
                            int hoechstzahl) {
        if (plan.feldBei(weltX, weltZ) != Rasterplan.Feld.PARZELLE) {
            return Entscheid.nein(Bescheid.KEINE_PARZELLE);
        }
        int px = plan.parzelleX(weltX);
        int pz = plan.parzelleZ(weltZ);

        Optional<Parzelle> vorhanden = lager.finde(welt, px, pz);
        if (vorhanden.isPresent()) {
            return new Entscheid(Bescheid.SCHON_VERGEBEN, 0L, vorhanden);
        }

        if (!grenze.enthaeltParzelle(px, pz)) {
            return Entscheid.nein(Bescheid.AUSSERHALB);
        }

        int bereits = lager.anzahlVon(welt, spieler);
        if (bereits >= hoechstzahl) {
            return Entscheid.nein(Bescheid.GRENZE_ERREICHT);
        }

        long preis = Preisstaffel.preisFuerNaechste(bereits);
        if (!kasse.abbuchen(spieler, preis)) {
            return Entscheid.nein(Bescheid.ZU_WENIG_MARK, preis);
        }

        try {
            Parzelle neu = lager.anlegen(welt, px, pz, spieler, preis);
            return new Entscheid(Bescheid.ERTEILT, preis, Optional.of(neu));
        } catch (RuntimeException fehler) {
            // Das Geld ist weg, die Parzelle nicht gekommen - also zurueck damit. Der Fall
            // ist nicht theoretisch: Nehmen zwei Spieler im selben Augenblick dieselbe
            // Parzelle, laesst der eindeutige Schluessel in der Datenbank den zweiten
            // auflaufen. Ohne diese Gegenbuchung zahlte er fuer nichts.
            kasse.gutschreiben(spieler, preis);
            throw fehler;
        }
    }

    /**
     * Was einer Aenderung an dieser Stelle entgegensteht - leer heisst: nichts.
     *
     * <p>Die eine Stelle fuer den Dreischritt, den Mitbauer-Eintragen, Mitbauer-Austragen
     * und Aufgeben vorher jeweils fuer sich erledigt haben. Sobald es Kindzeilen gibt,
     * braucht jeder dieser Wege dieselbe Pruefung - und einer von dreien wuerde sie
     * vergessen. Genau das ist hier schon einmal passiert: Zwei von sieben Befehlen hatten
     * die Weltpruefung nicht, bis sie in {@code ortInMarktstadt} zusammengezogen wurde.
     *
     * <p>Gibt nur das HINDERNIS zurueck, nie eine Erlaubnis. Ein durchgereichter Entscheid
     * mit {@code ERTEILT} meldete dem Spieler "Erledigt.", ohne dass etwas geschehen waere.
     */
    /** Ob eine Tat auf einem Gruppenkind erlaubt ist oder dort verweigert wird. */
    private enum AufGruppe {
        /** Die Tat wirkt auf die ganze Gruppe - das Lager schreibt auf alle Kinder durch. */
        ERLAUBT,
        /** Die Tat meint ein einzelnes Feld und waere auf einer Gruppe missverstaendlich. */
        VERWEIGERN
    }

    /**
     * Die Zeile, auf die eine Aenderung geschrieben wird - bei einem Gruppenkind die Gruppe.
     *
     * <p>Das Umlenken an einer Stelle. Geprueft wird am Kind, weil es Eigentuemer und
     * Mitbauer vollstaendig traegt; geschrieben wird an der Gruppe, und von dort schreibt
     * das Lager auf alle Kinder durch.
     */
    private static long schreibZiel(Parzelle parzelle) {
        return parzelle.hatElternteil() ? parzelle.elternId() : parzelle.id();
    }

    private Optional<Entscheid> hindernis(Optional<Parzelle> gefunden, UUID handelnder,
                                          AufGruppe aufGruppe, int weltX, int weltZ) {
        if (gefunden.isEmpty()) {
            // "Hier ist keine Parzelle" stimmt auf dem Weg und vor der Stadt - auf freiem
            // Bauland ist es falsch. Dort GIBT es die Parzelle, sie gehoert nur noch
            // niemandem, und /parzelle info sagt an derselben Stelle "frei". Ein Spieler,
            // der zwei verschiedene Antworten auf denselben Block bekommt, sucht den
            // Fehler bei sich.
            //
            // Die Unterscheidung steht hier und nicht bei den sieben Aufrufern: Sie
            // brauchen alle dieselbe, und sechs davon kommen ueber zustaendigerBesitz,
            // kennen das Feld unter sich also gar nicht.
            boolean aufBauland = plan.feldBei(weltX, weltZ) == Rasterplan.Feld.PARZELLE
                    && grenze.enthaeltParzelle(plan.parzelleX(weltX), plan.parzelleZ(weltZ));
            return Optional.of(Entscheid.nein(
                    aufBauland ? Bescheid.NOCH_FREI : Bescheid.KEINE_PARZELLE));
        }
        Parzelle parzelle = gefunden.get();
        if (!parzelle.gehoert(handelnder)) {
            return Optional.of(new Entscheid(Bescheid.NICHT_EIGENTUEMER, 0L, gefunden));
        }
        if (aufGruppe == AufGruppe.VERWEIGERN && parzelle.hatElternteil()) {
            // Eigentuemer und Mitbauer stehen am Kind zwar vollstaendig - nur so bleibt
            // darfBauen eine Abfrage ohne Umweg -, geschrieben wird aber ausschliesslich
            // ueber die Gruppe. Verweigern ist die rueckholbare Antwort: Ein halb
            // durchgeschriebener Besitzerwechsel waere es nicht.
            return Optional.of(new Entscheid(Bescheid.TEIL_EINER_GRUPPE, 0L, gefunden));
        }
        return Optional.empty();
    }

    // --- Zusammenlegen und Auftrennen ------------------------------------------

    /**
     * Was ein Zusammenlegen kosten wuerde und ob es ginge - ohne etwas zu tun.
     *
     * <p>Fuer die Warnung vor der Bestaetigung. Schreibt nichts, auch nicht an der Kasse.
     */
    public Entscheid pruefeZusammenlegen(String welt, UUID eigentuemer, int weltX, int weltZ,
                                         Richtung richtung, int hoechsteFelder) {
        return zusammenlegenWeg(welt, eigentuemer, weltX, weltZ, richtung, hoechsteFelder,
                false);
    }

    /**
     * Zwei benachbarte Felder zu einer Gruppe machen - oder ein Feld an eine Gruppe
     * anhaengen.
     *
     * <p>Bei Erfolg ist die Gebuehr abgebucht und die Ablage geaendert. Bei jedem anderen
     * Bescheid ist nichts passiert, auch nicht an der Kasse.
     *
     * <p>Die Welt wird hier NICHT angefasst. Das ist Absicht: Die Ablage fuehrt, die Welt
     * folgt. Gefuellte Bloecke ohne Recht darauf waeren das Fehlerbild, das wie ein
     * Rechtefehler aussieht und keiner ist.
     *
     * @param hoechsteFelder wie viele Felder eine Gruppe bei seinem Rang haben darf
     */
    public Entscheid zusammenlegen(String welt, UUID eigentuemer, int weltX, int weltZ,
                                   Richtung richtung, int hoechsteFelder) {
        return zusammenlegenWeg(welt, eigentuemer, weltX, weltZ, richtung, hoechsteFelder,
                true);
    }

    /**
     * Pruefen und auf Wunsch tun - damit die Warnung dieselbe Rechnung liest wie die Tat.
     *
     * <p>Zwei getrennte Methoden haetten zwei Gebuehrenformeln, und eine davon wuerde
     * irgendwann nicht mitgepflegt. Dann nennt die Warnung einen anderen Betrag als den,
     * der abgebucht wird.
     *
     * <p>Die Reihenfolge der Pruefungen ist Absicht: erst der Ort, dann die Person, dann
     * der Rang, zuletzt das Geld. Rang VOR Geld, weil "dir fehlen 30.000" ihn verdienen
     * schickt - und wenn er wiederkommt, sagt die Rangobergrenze trotzdem nein. Geld
     * zuletzt auch deshalb, weil es die einzige Pruefung ist, die bei Erfolg schreibt.
     */
    private Entscheid zusammenlegenWeg(String welt, UUID eigentuemer, int weltX, int weltZ,
                                       Richtung richtung, int hoechsteFelder,
                                       boolean wirklich) {
        // 1. Wo steht er? Auf dem gefuellten Weg seiner eigenen Gruppe braucht dieser
        // Befehl trotzdem eine bestimmte Rasterzelle - floorDiv naehme dort die kleinere
        // Nachbarzelle, und das waere womoeglich das falsche Feld.
        if (plan.feldBei(weltX, weltZ) != Rasterplan.Feld.PARZELLE) {
            return Entscheid.nein(zustaendigerBesitz(welt, weltX, weltZ).isPresent()
                    ? Bescheid.AUF_DEM_WEG
                    : Bescheid.KEINE_PARZELLE);
        }

        int px = plan.parzelleX(weltX);
        int pz = plan.parzelleZ(weltZ);
        Optional<Parzelle> hier = lager.finde(welt, px, pz);
        Optional<Entscheid> stehtEntgegen = hindernis(hier, eigentuemer, AufGruppe.ERLAUBT, weltX, weltZ);
        if (stehtEntgegen.isPresent()) {
            return stehtEntgegen.get();
        }

        int nx = px + richtung.dx();
        int nz = pz + richtung.dz();
        if (!grenze.enthaeltParzelle(nx, nz)) {
            return Entscheid.nein(Bescheid.AUSSERHALB);
        }
        Optional<Parzelle> drueben = lager.finde(welt, nx, nz);
        if (drueben.isEmpty()) {
            return Entscheid.nein(Bescheid.NACHBAR_FREI);
        }
        if (!drueben.get().gehoert(eigentuemer)) {
            return new Entscheid(Bescheid.NACHBAR_FREMD, 0L, drueben);
        }

        Parzelle a = hier.get();
        Parzelle b = drueben.get();
        if (a.hatElternteil() && b.hatElternteil()) {
            // Zwei Faelle, die sich fuer den Spieler voellig verschieden anfuehlen, und
            // deshalb zwei Bescheide. Wer zweimal in dieselbe Richtung zusammenlegt - weil
            // er nicht gesehen hat, dass es beim ersten Mal geklappt hat -, soll nicht
            // lesen, seine Felder gehoerten "zu einer Gruppe", als waere da etwas im Weg.
            // Es IST seine Gruppe.
            if (a.elternId() == b.elternId()) {
                return new Entscheid(Bescheid.SCHON_ZUSAMMEN, 0L, hier);
            }
            // Zwei Gruppen zu vereinigen wuerde aus dem Zusammenhang eine Graphensuche
            // machen. Wer das will, trennt eine davon auf und haengt die Felder einzeln an.
            return new Entscheid(Bescheid.SCHON_IN_GRUPPE, 0L, hier);
        }

        // Welches der beiden die Gruppe ist, entscheidet die Lage und nicht, wo der
        // Spieler zufaellig steht - sonst scheiterte das voellig legale "loses Feld an
        // die eigene Gruppe anhaengen" allein an seinem Standort.
        long gruppeId = a.hatElternteil() ? a.elternId()
                : b.hatElternteil() ? b.elternId()
                : Parzelle.KEIN_ELTERNTEIL;
        Parzelle beitreter = a.hatElternteil() ? b : a;

        int bisher = gruppeId == Parzelle.KEIN_ELTERNTEIL ? 1 : lager.kinderVon(gruppeId).size();
        int nachher = bisher + 1;
        long gebuehr = Preisstaffel.zusammenlegungsGebuehr(nachher);
        if (nachher > hoechsteFelder) {
            return Entscheid.nein(Bescheid.GRUPPE_ZU_GROSS, gebuehr);
        }

        // Die Vereinigung der Mitbauer. Leeren waere stiller Rechteverlust, "die des
        // ersten" haenge davon ab, wo der Spieler steht.
        Set<UUID> zusammen = new LinkedHashSet<>(a.mitbauer());
        zusammen.addAll(b.mitbauer());

        if (!wirklich) {
            return new Entscheid(Bescheid.ERTEILT, gebuehr, hier);
        }
        if (!kasse.abbuchen(eigentuemer, gebuehr)) {
            return Entscheid.nein(Bescheid.ZU_WENIG_MARK, gebuehr);
        }
        List<Long> neueFelder = gruppeId == Parzelle.KEIN_ELTERNTEIL
                ? List.of(a.id(), b.id())
                : List.of(beitreter.id());
        long id;
        // Die Klammer liegt eng um den EINEN Schreibvorgang. Alles, was danach kommt,
        // gehoert nicht mehr hinein: Die Gruppe ist dann festgeschrieben, und eine
        // Gutschrift wuerde sie verschenken - Grundstueck da, Geld zurueck. Das ist der
        // schlimmere Ausgang von beiden, weil ihn niemand bemerkt.
        try {
            id = lager.gruppeSetzen(gruppeId, welt, eigentuemer, zusammen, gebuehr,
                    neueFelder);
        } catch (RuntimeException fehler) {
            // Dasselbe Muster wie in nehmen(): Das Geld ist weg, die Gruppe nicht
            // gekommen. Der Fall ist nicht theoretisch - der Wettlaufriegel im Lager
            // laesst einen zweiten Spieler auflaufen, der dasselbe Feld gerade anhaengt.
            kasse.gutschreiben(eigentuemer, gebuehr);
            throw fehler;
        }
        return new Entscheid(Bescheid.ERTEILT, gebuehr,
                lager.finde(welt, px, pz).or(() -> lager.kinderVon(id).stream().findFirst()));
    }

    /** Ob hier eine Gruppe liegt, die sich auftrennen liesse - ohne etwas zu tun. */
    public Entscheid pruefeAuftrennen(String welt, UUID eigentuemer, int weltX, int weltZ) {
        return auftrennenWeg(welt, eigentuemer, weltX, weltZ, false);
    }

    /**
     * Eine Gruppe aufloesen - die Felder werden wieder eigenstaendig.
     *
     * <p>Kostet nichts und zahlt nichts zurueck, wie das Aufgeben. Plan 6.3 verlangt den
     * Weg ausdruecklich: "sonst ist jeder Fehlgriff endgueltig."
     */
    public Entscheid auftrennen(String welt, UUID eigentuemer, int weltX, int weltZ) {
        return auftrennenWeg(welt, eigentuemer, weltX, weltZ, true);
    }

    private Entscheid auftrennenWeg(String welt, UUID eigentuemer, int weltX, int weltZ,
                                    boolean wirklich) {
        Optional<Parzelle> gefunden = zustaendigerBesitz(welt, weltX, weltZ);
        Optional<Entscheid> stehtEntgegen = hindernis(gefunden, eigentuemer, AufGruppe.ERLAUBT, weltX, weltZ);
        if (stehtEntgegen.isPresent()) {
            return stehtEntgegen.get();
        }
        Parzelle parzelle = gefunden.get();
        if (!parzelle.hatElternteil()) {
            return new Entscheid(Bescheid.KEINE_GRUPPE, 0L, gefunden);
        }
        if (wirklich) {
            lager.auftrennen(parzelle.elternId());
        }
        return new Entscheid(Bescheid.ERTEILT, 0L, gefunden);
    }

    /** Ob sich das Feld hier aus seiner Gruppe loesen liesse - ohne etwas zu tun. */
    public Entscheid pruefeHerausloesen(String welt, UUID eigentuemer, int weltX, int weltZ) {
        return herausloesenWeg(welt, eigentuemer, weltX, weltZ, false);
    }

    /**
     * Ein einzelnes Feld aus seiner Gruppe loesen - der Rest bleibt ein Grundstueck.
     *
     * <p>Gemeint ist das Feld, auf dem der Spieler steht. Der ganze Weg bleibt daneben
     * bestehen ({@link #auftrennen}): Wer vier Felder verbunden hat und eines abgeben will,
     * verlor sonst alle Wege dazwischen und musste dreimal neu zusammenlegen, Gebuehr
     * inbegriffen.
     *
     * <p>Bei einer Gruppe aus zwei Feldern loest sich nichts einzeln: Was uebrig bliebe,
     * waere ein einzelnes Feld, und eine Gruppe aus einem Feld gibt es nicht - sie
     * veraendert in der Welt nichts und waere nur eine Zeile, die niemand mehr los wird.
     * Dann wird ganz aufgetrennt, und das Ergebnis ist dasselbe.
     *
     * <p>Kostet nichts und zahlt nichts zurueck, wie das Auftrennen.
     */
    public Entscheid herausloesen(String welt, UUID eigentuemer, int weltX, int weltZ) {
        return herausloesenWeg(welt, eigentuemer, weltX, weltZ, true);
    }

    private Entscheid herausloesenWeg(String welt, UUID eigentuemer, int weltX, int weltZ,
                                      boolean wirklich) {
        // Wie beim Zusammenlegen: Auf dem gefuellten Weg ist nicht entschieden, welches
        // Feld gemeint ist - floorDiv naehme dort die kleinere Nachbarzelle.
        if (plan.feldBei(weltX, weltZ) != Rasterplan.Feld.PARZELLE) {
            return Entscheid.nein(zustaendigerBesitz(welt, weltX, weltZ).isPresent()
                    ? Bescheid.AUF_DEM_WEG
                    : Bescheid.KEINE_PARZELLE);
        }
        Optional<Parzelle> hier = lager.finde(welt, plan.parzelleX(weltX),
                plan.parzelleZ(weltZ));
        Optional<Entscheid> stehtEntgegen = hindernis(hier, eigentuemer, AufGruppe.ERLAUBT, weltX, weltZ);
        if (stehtEntgegen.isPresent()) {
            return stehtEntgegen.get();
        }
        Parzelle feld = hier.get();
        if (!feld.hatElternteil()) {
            return new Entscheid(Bescheid.KEINE_GRUPPE, 0L, hier);
        }
        List<Parzelle> gruppe = lager.kinderVon(feld.elternId());
        // Erst ab drei Feldern kann ueberhaupt etwas zerreissen, und erst ab drei bleibt
        // nach dem Loesen eine Gruppe uebrig.
        if (gruppe.size() > 2
                && !Gruppenflaeche.aus(gruppe).bleibtGanzOhne(feld.x(), feld.z())) {
            return new Entscheid(Bescheid.HAELT_ZUSAMMEN, 0L, hier);
        }
        if (wirklich) {
            if (gruppe.size() > 2) {
                lager.ausGruppeLoesen(feld.id());
            } else {
                lager.auftrennen(feld.elternId());
            }
        }
        return new Entscheid(Bescheid.ERTEILT, 0L, hier);
    }

    /** Die Felder der Gruppe an dieser Stelle - leer, wenn dort keine Gruppe liegt. */
    public List<Parzelle> gruppenFelder(String welt, int weltX, int weltZ) {
        return zustaendigerBesitz(welt, weltX, weltZ)
                .filter(Parzelle::hatElternteil)
                .map(parzelle -> lager.kinderVon(parzelle.elternId()))
                .orElseGet(List::of);
    }

    /** Einen Mitbauer eintragen. Nur der Eigentuemer darf das. */
    public Entscheid mitbauerHinzufuegen(String welt, UUID eigentuemer, int weltX, int weltZ,
                                         UUID neuerMitbauer) {
        Optional<Parzelle> gefunden = zustaendigerBesitz(welt, weltX, weltZ);
        Optional<Entscheid> stehtEntgegen = hindernis(gefunden, eigentuemer, AufGruppe.ERLAUBT, weltX, weltZ);
        if (stehtEntgegen.isPresent()) {
            return stehtEntgegen.get();
        }
        Parzelle parzelle = gefunden.get();
        if (parzelle.mitbauer().contains(neuerMitbauer) || parzelle.gehoert(neuerMitbauer)) {
            return new Entscheid(Bescheid.SCHON_EINGETRAGEN, 0L, gefunden);
        }
        Set<UUID> neu = new LinkedHashSet<>(parzelle.mitbauer());
        neu.add(neuerMitbauer);
        lager.mitbauerSetzen(schreibZiel(parzelle), neu);
        return new Entscheid(Bescheid.ERTEILT, 0L,
                lager.finde(welt, parzelle.x(), parzelle.z()));
    }

    /** Einen Mitbauer austragen. Nur der Eigentuemer darf das. */
    public Entscheid mitbauerEntfernen(String welt, UUID eigentuemer, int weltX, int weltZ,
                                       UUID bisherigerMitbauer) {
        Optional<Parzelle> gefunden = zustaendigerBesitz(welt, weltX, weltZ);
        Optional<Entscheid> stehtEntgegen = hindernis(gefunden, eigentuemer, AufGruppe.ERLAUBT, weltX, weltZ);
        if (stehtEntgegen.isPresent()) {
            return stehtEntgegen.get();
        }
        Parzelle parzelle = gefunden.get();
        if (!parzelle.mitbauer().contains(bisherigerMitbauer)) {
            return new Entscheid(Bescheid.NICHT_EINGETRAGEN, 0L, gefunden);
        }
        Set<UUID> neu = new LinkedHashSet<>(parzelle.mitbauer());
        neu.remove(bisherigerMitbauer);
        lager.mitbauerSetzen(schreibZiel(parzelle), neu);
        return new Entscheid(Bescheid.ERTEILT, 0L,
                lager.finde(welt, parzelle.x(), parzelle.z()));
    }

    /**
     * Eine Parzelle aufgeben.
     *
     * <p>Ohne Rueckzahlung: Sonst waere Kaufen und Aufgeben ein Weg, die Preisstaffel
     * zurueckzusetzen - man kauft die teure sechste, gibt die billige zweite auf und steht
     * guenstiger da als vorher.
     */
    public Entscheid aufgeben(String welt, UUID eigentuemer, int weltX, int weltZ) {
        Optional<Parzelle> gefunden = zustaendigerBesitz(welt, weltX, weltZ);
        // VERWEIGERN, nicht ERLAUBT: "/parzelle aufgeben bestaetigen 3 7" nennt EIN Feld.
        // Durchgeschrieben loeschte es die ganze Gruppe - und genau diese Bestaetigung
        // ist die Sicherung, die so etwas verhindern soll.
        Optional<Entscheid> stehtEntgegen = hindernis(gefunden, eigentuemer, AufGruppe.VERWEIGERN, weltX, weltZ);
        if (stehtEntgegen.isPresent()) {
            return stehtEntgegen.get();
        }
        Parzelle parzelle = gefunden.get();
        lager.entfernen(parzelle.id());
        return new Entscheid(Bescheid.ERTEILT, 0L, gefunden);
    }
}
