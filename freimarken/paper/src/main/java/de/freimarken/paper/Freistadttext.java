package de.freimarken.paper;

import de.freimarken.kern.Absteckbuch;
import de.freimarken.kern.Blockecke;
import de.freimarken.kern.Freistadtbuch;
import de.freimarken.kern.Grundstueck;
import de.freimarken.kern.Grundstuecksbuch;
import de.freimarken.kern.Parzelle;
import de.freimarken.kern.Stadtfeldplan;
import de.freimarken.kern.Stadtname;
import de.freimarken.kern.Zellquader;
import de.freimarken.kern.Stadtbuergerschaft;
import de.freimarken.kern.Tat;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.Set;
import java.util.function.Function;
import net.kyori.adventure.text.Component;

/**
 * Die Zeilen von {@code /freistadt} - reine Rechnung, kein Bukkit, kein Senden.
 *
 * <p><b>Das ist DIE NAHT dieses Bereichs</b>, wie {@link Ladentext} fuer die Markthalle:
 * Jeder Zweig von {@code FreistadtBefehle} holt sich hier seine Zeilen und schickt sie
 * nur hinaus. Dadurch ist jedes Wort, jede Farbe und jede Reihenfolge ohne Server
 * pruefbar - und die Bildtafel haengt genau an diesen Methoden.
 *
 * <p>Kein Wort steht hier: Der Wortlaut kommt aus {@code sprache-vorlage.yml} ueber
 * {@link FreistadtSatz}. Und gesendet wird hier NICHT - diese Datei steht ausdruecklich
 * nicht unter den sendenden; sie baut Zeilen und gibt sie zurueck.
 */
final class Freistadttext {

    private Freistadttext() {
    }

    /**
     * Die Feldnummer als Adresse, etwa {@code 3/-7}.
     *
     * <p>An EINER Stelle, weil drei Saetze sie nennen: die Liste, die Gruendungsmeldung
     * und die Absage. Drei Abschriften schrieben dieselbe Zahl auf drei Arten, und ein
     * Spieler haelt {@code 3/-7} und {@code 3, -7} fuer zwei verschiedene Orte.
     */
    static String feldAlsText(Parzelle stadt) {
        return stadt.x() + "/" + stadt.z();
    }

    /**
     * Die Liste der Freistaedte - Ueberschrift und je Stadt eine Zeile.
     *
     * <p>Die REIHENFOLGE ist die der Ablage, also nach Namen. Das ist eine Zusage von
     * {@code BesitzLager.staedteIn} und keine Laune dieser Methode: Eine Liste, die mal
     * so und mal anders kommt, ist im Spiel nicht zu lesen.
     *
     * @param namenVon loest eine Spielernummer in einen Namen auf - im Spiel
     *                 {@code Spielername::von}. Als NAHT hereingereicht und nicht
     *                 unmittelbar gerufen: Jene Methode fragt Bukkit, und damit waere
     *                 diese Liste ohne laufenden Server nicht mehr pruefbar.
     */
    static List<Component> listeZeilen(Sprache sprache, List<Parzelle> staedte,
                                       Function<UUID, String> namenVon) {
        List<Component> zeilen = new ArrayList<>();
        zeilen.add(sprache.eineZeile(FreistadtSatz.KOPF, Einsatz.keine()));
        if (staedte.isEmpty()) {
            zeilen.add(sprache.eineZeile(FreistadtSatz.LEER, Einsatz.keine()));
            return List.copyOf(zeilen);
        }
        for (Parzelle stadt : staedte) {
            zeilen.add(sprache.eineZeile(FreistadtSatz.ZEILE, Einsatz
                    .von("name", stadt.name())
                    .mit("besitzer", namenVon.apply(stadt.eigentuemer()))
                    .mit("feld", feldAlsText(stadt))));
        }
        return List.copyOf(zeilen);
    }

    /**
     * Die Rueckfrage vor dem Gruenden.
     *
     * <p>Mit dem Guthaben DANACH und nicht nur mit dem Preis: 400.000 Mark sind eine
     * Zahl, die man liest, ohne sie zu fuehlen. Was danach uebrig bleibt, fuehlt man.
     *
     * @param guthaben was der Spieler heute hat
     */
    static List<Component> frageZeilen(Sprache sprache, String name, long preis,
                                       long guthaben) {
        return sprache.bau(FreistadtSatz.FRAGE, 1L, Einsatz
                .von("name", name)
                .mit("preis", sprache.betrag(preis))
                .mit("danach", sprache.betrag(guthaben - preis)));
    }

    /**
     * Die Antwort auf einen Gruendungsversuch - genau eine Zeile je Bescheid.
     *
     * <p>Ein {@code switch} als AUSDRUCK und ohne {@code default}: Kommt ein Bescheid
     * dazu, meldet der Uebersetzer die fehlende Zeile. Ein {@code default} machte daraus
     * eine Zeile, die im Spiel das Falsche sagt.
     *
     * @param guthaben was der Spieler hat - nur fuer ZU_WENIG_MARK gebraucht
     */
    static List<Component> antwortZeilen(Sprache sprache,
                                         Freistadtbuch.Entscheid entscheid, String name,
                                         int hoechstzahl, long guthaben) {
        return switch (entscheid.bescheid()) {
            case GEGRUENDET -> sprache.bau(FreistadtSatz.GEGRUENDET, 1L, Einsatz
                    .von("name", entscheid.stadt().orElseThrow().name())
                    .mit("feld", feldAlsText(entscheid.stadt().orElseThrow())));
            case NAME_ZU_LANG -> sprache.bau(FreistadtSatz.NAME_ZU_LANG, 1L, Einsatz
                    .von("kleinstens", Stadtname.MINDESTENS_ZEICHEN)
                    .mit("groesstens", Stadtname.HOECHSTENS_ZEICHEN));
            case NAME_FALSCH -> sprache.bau(FreistadtSatz.NAME_FALSCH, 1L,
                    Einsatz.keine());
            case NAME_RESERVIERT -> sprache.bau(FreistadtSatz.NAME_RESERVIERT, 1L,
                    Einsatz.von("name", name));
            case NAME_VERGEBEN -> sprache.bau(FreistadtSatz.NAME_VERGEBEN, 1L,
                    Einsatz.von("name", name));
            case GRENZE_ERREICHT -> sprache.bau(FreistadtSatz.GRENZE_ERREICHT, 1L,
                    Einsatz.von("hoechstzahl", hoechstzahl));
            case KEIN_FREIES_FELD -> sprache.bau(FreistadtSatz.KEIN_FREIES_FELD, 1L,
                    Einsatz.keine());
            case ZU_WENIG_MARK -> sprache.bau(FreistadtSatz.ZU_WENIG_MARK, 1L, Einsatz
                    .von("preis", sprache.betrag(entscheid.kosten()))
                    .mit("fehlt", sprache.betrag(entscheid.kosten()
                            - guthaben)));
            case GESTOERT -> sprache.bau(FreistadtSatz.GESTOERT, 1L, Einsatz.keine());
        };
    }

    // --- Die Grundstuecke [Teil C2, 12.11.2026] ---------------------------------

    /**
     * Der Stand nach einem Abstecken.
     *
     * <p>Nennt die BLOCKstelle und die ZELLE: Der Spieler steht auf einem Block, aber
     * zugeschnitten wird in Zellen - und ohne die Zellnummer koennte er nicht
     * nachvollziehen, warum zwei Ecken, die drei Bloecke auseinanderliegen, dieselbe
     * Zelle treffen.
     */
    static List<Component> eckeZeilen(Sprache sprache, Absteckbuch.Stand stand,
                                      Stadtfeldplan plan) {
        Blockecke letzte = stand.letzte();
        Einsatz wo = Einsatz.von("x", String.valueOf(letzte.x()))
                .mit("z", String.valueOf(letzte.z()))
                .mit("zelle", plan.zelleVon(letzte.x()) + "/" + plan.zelleVon(letzte.z()));
        List<Component> zeilen = new ArrayList<>(stand.neuBegonnen()
                ? sprache.bau(FreistadtSatz.ECKE_NEU, 1L, wo)
                : sprache.bau(FreistadtSatz.ECKE, 1L,
                        wo.mit("nummer", String.valueOf(stand.gesetzte().size()))));
        if (stand.fertig()) {
            // Die Groesse SCHON JETZT, nicht erst beim Anlegen: Wer sieht, dass sein
            // Zuschnitt 240 Zellen hat, legt ihn nicht erst an und liest dann die
            // Absage.
            Zellquader vorschau = Zellquader.zwischen(
                    plan.zelleVon(stand.gesetzte().get(0).x()),
                    plan.zelleVon(stand.gesetzte().get(0).z()),
                    plan.zelleVon(letzte.x()), plan.zelleVon(letzte.z()));
            zeilen.addAll(sprache.bau(FreistadtSatz.ECKEN_FERTIG, vorschau.zellen(),
                    Einsatz.von("zellen", String.valueOf(vorschau.zellen()))));
        }
        return zeilen;
    }

    /**
     * Die Antwort auf einen Zuschnitt oder ein Aufloesen.
     *
     * <p>Ein {@code switch} ohne {@code default}, wie in {@link #antwortZeilen}: Kommt
     * ein Bescheid dazu, uebersetzt diese Datei nicht mehr, statt still zu schweigen.
     */
    static List<Component> zuschnittZeilen(Sprache sprache,
                                           Grundstuecksbuch.Entscheid entscheid,
                                           int hoechstzellen, long guthaben) {
        return switch (entscheid.bescheid()) {
            case ERTEILT -> erteiltZeilen(sprache, entscheid);
            case KEINE_STADTWELT -> keineWeltZeilen(sprache);
            case NICHT_EIN_FELD -> sprache.bau(FreistadtSatz.NICHT_EIN_FELD, 1L,
                    Einsatz.keine());
            case KEINE_STADT -> sprache.bau(FreistadtSatz.KEINE_STADT, 1L,
                    Einsatz.keine());
            case NICHT_DEINE_STADT -> sprache.bau(FreistadtSatz.NICHT_DEINE_STADT, 1L,
                    Einsatz.keine());
            case UEBERSCHNEIDUNG -> sprache.bau(FreistadtSatz.UEBERSCHNEIDUNG, 1L,
                    Einsatz.keine());
            case ZU_GROSS -> sprache.bau(FreistadtSatz.ZU_GROSS, entscheid.zellen(),
                    Einsatz.von("zellen", String.valueOf(entscheid.zellen()))
                            .mit("hoechstens", String.valueOf(hoechstzellen)));
            case KEIN_GRUNDSTUECK -> sprache.bau(FreistadtSatz.KEIN_GRUNDSTUECK, 1L,
                    Einsatz.keine());
            case NICHT_DEIN_GRUNDSTUECK -> sprache.bau(
                    FreistadtSatz.NICHT_DEIN_GRUNDSTUECK, 1L, Einsatz.keine());
            case GESTOERT -> sprache.bau(FreistadtSatz.GRUNDSTUECK_GESTOERT, 1L,
                    Einsatz.keine());
            // --- Angebot und Verkauf [Teil C3] ---------------------------------
            case ANGEBOTEN -> angebotZeilen(sprache, entscheid);
            case KEIN_ANGEBOT -> sprache.bau(FreistadtSatz.KEIN_ANGEBOT, 1L,
                    Einsatz.keine());
            case EIGENES_GRUNDSTUECK -> sprache.bau(FreistadtSatz.EIGENES_GRUNDSTUECK,
                    1L, Einsatz.keine());
            case ZU_WENIG_MARK -> sprache.bau(FreistadtSatz.GRUNDSTUECK_ZU_WENIG, 1L,
                    Einsatz.von("preis", sprache.betrag(entscheid.preis()))
                            .mit("fehlt", sprache.betrag(entscheid.preis()
                                    - guthaben)));
            case PREIS_UNGUELTIG -> sprache.bau(FreistadtSatz.PREIS_UNGUELTIG, 1L,
                    Einsatz.keine());
            // GEKAUFT fehlt hier mit Absicht und WIRFT: Dieser Satz nennt den
            // VERKAEUFER beim Namen, und ein Name kommt nicht aus dem Kern - er kommt
            // ueber Spielername.von von aussen herein. Dafuer gibt es kaufZeilen. Ein
            // Platzhalter an dieser Stelle waere ein Fragezeichen im Chatfenster.
            case GEKAUFT -> throw new IllegalArgumentException("GEKAUFT geht ueber"
                    + " kaufZeilen - dieser Satz nennt den Verkaeufer beim Namen, und"
                    + " den kennt der Kern nicht");
        };
    }

    /**
     * Ein Angebot oder sein Rueckzug.
     *
     * <p><b>Drei Saetze fuer einen Bescheid</b>, und das ist eine Entscheidung ueber die
     * SPRACHE: "zu 0 Mark angeboten" liest sich wie ein Fehler, und "zurueckgezogen" ist
     * ueberhaupt kein Angebot. Ein Satz mit eingesetzter Null haette alle drei Faelle
     * gleich klingen lassen.
     *
     * <p>Unterschieden wird am GRUNDSTUECK und nicht am Preis im Entscheid: Nach einem
     * Rueckzug steht dort eine 0, und die heisst beim Anbieten "zu verschenken". Nur das
     * Grundstueck selbst weiss, ob es noch ausliegt.
     */
    private static List<Component> angebotZeilen(Sprache sprache,
                                                 Grundstuecksbuch.Entscheid entscheid) {
        Grundstueck eines = entscheid.grundstueck().orElseThrow();
        String zuschnitt = eines.flaeche().alsText();
        if (!eines.stehtZumVerkauf()) {
            return sprache.bau(FreistadtSatz.ZURUECKGEZOGEN, 1L,
                    Einsatz.von("zuschnitt", zuschnitt));
        }
        return eines.angebotspreis() == 0L
                ? sprache.bau(FreistadtSatz.ANGEBOTEN_GRATIS, 1L,
                        Einsatz.von("zuschnitt", zuschnitt))
                : sprache.bau(FreistadtSatz.ANGEBOTEN, 1L,
                        Einsatz.von("zuschnitt", zuschnitt)
                                .mit("preis",
                                        sprache.betrag(eines.angebotspreis())));
    }

    /**
     * Was der KAEUFER liest - mit dem Namen des Verkaeufers.
     *
     * <p>Ein eigener Weg neben {@link #zuschnittZeilen}, weil der NAME von aussen kommt:
     * Der Kern kennt nur Kennungen, und {@code Spielername.von} ist Bukkit. Dieselbe
     * Naht wie bei {@link #listeZeilen}.
     *
     * <p>Jede ABSAGE geht weiter durch {@code zuschnittZeilen} - sonst muesste dieser
     * Weg alle zwoelf Bescheide ein zweites Mal kennen.
     */
    static List<Component> kaufZeilen(Sprache sprache,
                                      Grundstuecksbuch.Entscheid entscheid,
                                      String verkaeufer, long guthaben) {
        if (entscheid.bescheid() != Grundstuecksbuch.Bescheid.GEKAUFT) {
            return zuschnittZeilen(sprache, entscheid, 0, guthaben);
        }
        Grundstueck eines = entscheid.grundstueck().orElseThrow();
        return entscheid.preis() == 0L
                ? sprache.bau(FreistadtSatz.GEKAUFT_GRATIS, 1L,
                        Einsatz.von("zuschnitt", eines.flaeche().alsText())
                                .mit("verkaeufer", verkaeufer))
                : sprache.bau(FreistadtSatz.GEKAUFT, 1L,
                        Einsatz.von("zuschnitt", eines.flaeche().alsText())
                                .mit("preis",
                                        sprache.betrag(entscheid.preis()))
                                .mit("verkaeufer", verkaeufer));
    }

    /**
     * Was der VERKAEUFER liest, wenn er online ist.
     *
     * <p>Die Gebuehrzeile kommt NUR, wenn die gerechnete Gebuehr ueber 0 liegt - nicht
     * schon, wenn ein Satz ueber 0 steht. Bei 5 Prozent auf 19 Mark ist sie abgerundet
     * 0, und eine Zeile ueber 0 Mark Gebuehr waere eine Zeile ohne Geldbewegung.
     */
    static List<Component> verkaufZeilen(Sprache sprache,
                                         Grundstuecksbuch.Entscheid entscheid,
                                         String kaeufer, int prozent) {
        Grundstueck eines = entscheid.grundstueck().orElseThrow();
        String zuschnitt = eines.flaeche().alsText();
        List<Component> zeilen = new ArrayList<>(entscheid.preis() == 0L
                ? sprache.bau(FreistadtSatz.VERKAUFT_GRATIS, 1L,
                        Einsatz.von("zuschnitt", zuschnitt).mit("kaeufer", kaeufer))
                : sprache.bau(FreistadtSatz.VERKAUFT, 1L,
                        Einsatz.von("zuschnitt", zuschnitt)
                                .mit("preis",
                                        sprache.betrag(entscheid.preis()))
                                .mit("kaeufer", kaeufer)));
        if (entscheid.gebuehr() > 0L) {
            zeilen.addAll(sprache.bau(FreistadtSatz.VERKAUFT_GEBUEHR, 1L,
                    Einsatz.von("gebuehr", sprache.betrag(entscheid.gebuehr()))
                            .mit("prozent", String.valueOf(prozent))));
        }
        return zeilen;
    }

    /**
     * Die Angebote einer Welt - ueber alle Staedte.
     *
     * <p>Wie {@link #listeZeilen}: Ueberschrift immer, dann je Angebot eine Zeile oder
     * der eine Satz "keines". Ein Geschenk bekommt seine eigene Zeile, aus demselben
     * Grund wie bei {@link #angebotZeilen}.
     */
    static List<Component> angeboteZeilen(Sprache sprache, List<Grundstueck> angebote,
                                          Function<UUID, String> namenVon) {
        List<Component> zeilen = new ArrayList<>(sprache.bau(
                FreistadtSatz.ANGEBOT_KOPF, 1L, Einsatz.keine()));
        if (angebote.isEmpty()) {
            zeilen.addAll(sprache.bau(FreistadtSatz.ANGEBOT_LEER, 1L, Einsatz.keine()));
            return zeilen;
        }
        for (Grundstueck eines : angebote) {
            Einsatz werte = Einsatz.von("zuschnitt", eines.flaeche().alsText())
                    .mit("besitzer", namenVon.apply(eines.eigentuemer()));
            zeilen.addAll(eines.angebotspreis() == 0L
                    ? sprache.bau(FreistadtSatz.ANGEBOT_ZEILE_GRATIS, 1L, werte)
                    : sprache.bau(FreistadtSatz.ANGEBOT_ZEILE, 1L,
                            werte.mit("preis",
                                    sprache.betrag(eines.angebotspreis()))));
        }
        return zeilen;
    }

    /**
     * Ein geglueckter Zuschnitt oder ein geglicktes Aufloesen.
     *
     * <p>Welcher von beiden, entscheidet die FLAECHE: Nach einem Aufloesen gibt es das
     * Grundstueck nicht mehr, nach einem Zuschnitt schon - und nur dort traegt der
     * Entscheid eines mit Nummer.
     */
    private static List<Component> erteiltZeilen(Sprache sprache,
                                                 Grundstuecksbuch.Entscheid entscheid) {
        Grundstueck eines = entscheid.grundstueck().orElseThrow(
                () -> new IllegalStateException("ERTEILT ohne Grundstueck - dann weiss"
                        + " niemand, was zugeschnitten wurde"));
        return sprache.bau(FreistadtSatz.ZUGESCHNITTEN, entscheid.zellen(),
                Einsatz.von("zuschnitt", eines.flaeche().alsText())
                        .mit("zellen", String.valueOf(entscheid.zellen()))
                        .mit("bloecke", String.valueOf(eines.flaeche().bloecke())));
    }

    /** Die Antwort auf ein Aufloesen - eigener Satz, weil die Flaeche zurueckfaellt. */
    static List<Component> aufgeloestZeilen(Sprache sprache,
                                            Grundstuecksbuch.Entscheid entscheid,
                                            int hoechstzellen, long guthaben) {
        if (!entscheid.erfolgreich()) {
            return zuschnittZeilen(sprache, entscheid, hoechstzellen, guthaben);
        }
        return sprache.bau(FreistadtSatz.AUFGELOEST, 1L, Einsatz.von("zuschnitt",
                entscheid.grundstueck().orElseThrow().flaeche().alsText()));
    }

    /**
     * Die Grundstuecke einer Stadt.
     *
     * <p>Wie {@link #listeZeilen}: Ueberschrift immer, dann je Grundstueck eine Zeile
     * oder der eine Satz "noch keines". Der Name des Besitzers kommt von aussen herein -
     * siehe dort.
     */
    static List<Component> grundstueckZeilen(Sprache sprache, String stadt,
                                             List<Grundstueck> grundstuecke,
                                             Function<UUID, String> namenVon) {
        List<Component> zeilen = new ArrayList<>(sprache.bau(
                FreistadtSatz.GRUNDSTUECK_KOPF, 1L, Einsatz.von("stadt", stadt)));
        if (grundstuecke.isEmpty()) {
            zeilen.addAll(sprache.bau(FreistadtSatz.GRUNDSTUECK_LEER, 1L,
                    Einsatz.keine()));
            return zeilen;
        }
        for (Grundstueck eines : grundstuecke) {
            zeilen.addAll(sprache.bau(FreistadtSatz.GRUNDSTUECK_ZEILE,
                    eines.flaeche().zellen(),
                    Einsatz.von("zuschnitt", eines.flaeche().alsText())
                            .mit("besitzer", namenVon.apply(eines.eigentuemer()))
                            .mit("zellen",
                                    String.valueOf(eines.flaeche().zellen()))));
        }
        return zeilen;
    }

    /** Anlegen ohne oder mit nur einer Ecke. */
    static List<Component> fehlendeEckenZeilen(Sprache sprache, int gesetzte) {
        return sprache.bau(gesetzte == 0
                ? FreistadtSatz.KEINE_ECKEN
                : FreistadtSatz.EINE_ECKE, 1L, Einsatz.keine());
    }

    /** Die Welt der Freistaedte fehlt - dann kann dieser Befehl gar nichts. */
    static List<Component> keineWeltZeilen(Sprache sprache) {
        return sprache.bau(FreistadtSatz.KEINE_WELT, 1L, Einsatz.keine());
    }

    // --- Teil D3: die Buergerrechte --------------------------------------------

    /**
     * Die Rechteliste einer Stadt - Kopf, JEDES Recht, Fusszeile.
     *
     * <p><b>Jedes Recht steht da, auch das gesperrte</b>, und die Liste laeuft ueber
     * {@code Tat.values()}: Eine Liste, die nur die erlaubten nennt, laesst den Besitzer
     * raten, was es noch gibt - und ein viertes Recht waere darin unsichtbar.
     *
     * <p>Kein "leer"-Satz wie bei den anderen Listen: Diese Liste ist NIE leer. Es gibt
     * genau drei Rechte, und jedes ist entweder erlaubt oder gesperrt.
     */
    static List<Component> rechteZeilen(Sprache sprache, String stadt,
                                        Set<Tat> rechte) {
        List<Component> zeilen = new ArrayList<>(sprache.bau(
                FreistadtSatz.RECHTE_KOPF, 1L, Einsatz.von("stadt", stadt)));
        for (Tat recht : Tat.values()) {
            zeilen.addAll(sprache.bau(rechte.contains(recht)
                            ? FreistadtSatz.RECHT_ERLAUBT
                            : FreistadtSatz.RECHT_GESPERRT, 1L,
                    Einsatz.von("recht", wortZu(sprache, recht))));
        }
        zeilen.addAll(sprache.bau(FreistadtSatz.RECHTE_HINWEIS, 1L, Einsatz.keine()));
        return zeilen;
    }

    /**
     * Die Antwort auf einen Rechtezug.
     *
     * <p>Ein {@code switch} OHNE Vorgabezweig, wie jede Antwortnaht dieses Bereichs: Ein
     * neuer Bescheid bricht die Uebersetzung mit Zeilennummer, statt stillschweigend in
     * einen fremden Satz zu laufen.
     *
     * @param an was gewollt war - der Satz unterscheidet Geben und Nehmen
     */
    static List<Component> rechteAntwortZeilen(Sprache sprache,
                                               Freistadtbuch.Rechteentscheid entscheid,
                                               Tat recht, boolean an,
                                               Function<UUID, String> namenVon) {
        return switch (entscheid.bescheid()) {
            case GEAENDERT -> sprache.bau(an
                            ? FreistadtSatz.RECHT_GEAENDERT
                            : FreistadtSatz.RECHT_GENOMMEN, 1L,
                    Einsatz.von("recht", wortZu(sprache, recht)));
            case SCHON_SO -> sprache.bau(FreistadtSatz.RECHT_SCHON_SO, 1L,
                    Einsatz.von("recht", wortZu(sprache, recht)));
            case FREMDE_STADT -> sprache.bau(FreistadtSatz.RECHTE_NICHT_DEINE, 1L,
                    Einsatz.von("stadt", entscheid.stadt().orElseThrow().name())
                            .mit("besitzer", namenVon.apply(
                                    entscheid.stadt().orElseThrow().eigentuemer())));
            // Die zwei Faelle, die der Grundstueckszweig schon hat: Dort stehen ihre
            // Saetze, und ein zweiter Satz fuer dasselbe waere ein zweites Wort fuer
            // dieselbe Lage. GRUNDSTUECK_GESTOERT und nicht GESTOERT - jener Satz nennt
            // ausdruecklich, dass das Geld zurueck ist, und hier war gar keines im Spiel.
            case KEINE_STADT -> sprache.bau(FreistadtSatz.KEINE_STADT, 1L,
                    Einsatz.keine());
            case GESTOERT -> sprache.bau(FreistadtSatz.GRUNDSTUECK_GESTOERT, 1L,
                    Einsatz.keine());
        };
    }

    /** Ein Wort, das kein Buergerrecht ist. */
    static List<Component> unbekanntesRechtZeilen(Sprache sprache, String wort) {
        return sprache.bau(FreistadtSatz.RECHT_UNBEKANNT, 1L,
                Einsatz.von("wort", wort));
    }

    /**
     * Das WORT zu einem Recht - aus der Sprachdatei und nicht aus dem Quelltext.
     *
     * <p>Ueber {@link FreistadtSatz#rechtewort}: Dass der Spieler "Truhen und Oefen
     * oeffnen" liest und nicht "BEHAELTER", ist eine Sache der Vorlage. Ein Wort hier im
     * Programm waere genau die Stelle, an der ein Betreiber es nicht aendern kann.
     */
    private static String wortZu(Sprache sprache, Tat recht) {
        return sprache.wort(FreistadtSatz.rechtewort(recht), Einsatz.keine());
    }

    // --- Teil D5: die Stadtbuergerliste ----------------------------------------

    /**
     * Die Stadtbuerger einer Stadt - Kopf, der Besitzer, dann je Buerger eine Zeile.
     *
     * <p><b>Der Stadtbesitzer steht in einer EIGENEN Zeile</b> und nicht in der Liste:
     * Er ist kein Buerger seiner eigenen Stadt (siehe {@code Stadtbuergerschaft}), und
     * genau daran haengt die Verfallsregel aus Plan 16. Ihn weglassen waere trotzdem
     * falsch - wer die Liste liest, will wissen, wen er fragen muss.
     *
     * <p>Wie {@link #grundstueckZeilen}: Ueberschrift immer, dann die Zeilen oder der
     * eine Satz "noch keine". Der leere Fall ist hier der Normalfall einer frischen
     * Stadt, nicht die Ausnahme.
     */
    static List<Component> stadtbuergerZeilen(Sprache sprache, String stadt,
                                              UUID besitzer,
                                              List<Stadtbuergerschaft.Buerger> buerger,
                                              Function<UUID, String> namenVon) {
        List<Component> zeilen = new ArrayList<>(sprache.bau(
                FreistadtSatz.STADTBUERGER_KOPF, 1L, Einsatz.von("stadt", stadt)));
        zeilen.addAll(sprache.bau(FreistadtSatz.STADTBUERGER_BESITZER, 1L,
                Einsatz.von("besitzer", namenVon.apply(besitzer))));
        if (buerger.isEmpty()) {
            zeilen.addAll(sprache.bau(FreistadtSatz.STADTBUERGER_LEER, 1L,
                    Einsatz.keine()));
            return zeilen;
        }
        for (Stadtbuergerschaft.Buerger einer : buerger) {
            zeilen.addAll(sprache.bau(FreistadtSatz.STADTBUERGER_ZEILE,
                    einer.grundstuecke(),
                    Einsatz.von("name", namenVon.apply(einer.wer()))
                            .mit("anzahl", String.valueOf(einer.grundstuecke()))));
        }
        return zeilen;
    }
}
