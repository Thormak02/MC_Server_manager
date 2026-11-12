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
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
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
                .mit("preis", preis)
                .mit("danach", guthaben - preis));
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
                    .von("preis", entscheid.kosten())
                    .mit("fehlt", entscheid.kosten() - guthaben));
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
                                           int hoechstzellen) {
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
        };
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
                                            int hoechstzellen) {
        if (!entscheid.erfolgreich()) {
            return zuschnittZeilen(sprache, entscheid, hoechstzellen);
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
}
