package de.freimarken.paper;

import de.freimarken.kern.Aufgabenbuch;
import de.freimarken.kern.Biombuch;
import de.freimarken.kern.Parzelle;
import de.freimarken.kern.Punktebuch;
import de.freimarken.kern.Wolkenbuch;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import net.kyori.adventure.text.Component;

/**
 * Die Zeilen der Wolkeninseln - reine Rechnung, kein Bukkit, kein Senden.
 *
 * <p><b>Das ist DIE NAHT dieses Bereichs</b>, wie {@link Werdertext} fuer Hafenwerder:
 * {@code WolkeBefehle} holt sich hier seine Zeilen und schickt sie nur hinaus. Dadurch
 * ist jedes Wort, jede Farbe und jede Reihenfolge ohne Server pruefbar - und
 * {@code BildGleichTest} haengt genau an diesen Methoden.
 *
 * <p>Kein Wort steht hier: Der Wortlaut kommt aus {@code sprache-vorlage.yml} ueber
 * {@link WolkeSatz}.
 *
 * <p><b>Die Adresse eines Feldes kommt aus {@link Werdertext#feld}</b> und wird hier
 * nicht ein zweites Mal geschrieben. Das ist die Regel, die jener Klasse selbst im Kopf
 * steht: Zwei Schreibweisen fuer dieselbe Zahl - "2/-3" hier und "2 / -3" dort - saehen
 * fuer einen Spieler aus wie zwei verschiedene Orte, und er muesste lernen, dass seine
 * Meerinsel und seine Wolkeninsel anders geschrieben werden.
 */
final class Wolkentext {

    /** Welche Richtung ein Mitbauer-Vorgang hatte - sie entscheidet den Erfolgssatz. */
    enum Richtung {
        /** Eingetragen. */
        DAZU,
        /** Ausgetragen. */
        WEG
    }

    private Wolkentext() {
    }

    /**
     * Die Liste der eigenen Inseln.
     *
     * @param hoechstzahl was sein Rang erlaubt - sie steht in der letzten Zeile
     * @param namen       wie aus einer Kennung ein Name wird; fuer die Mitbauer
     */
    static List<Component> listenZeilen(Sprache sprache, List<Parzelle> inseln,
                                        int hoechstzahl, Function<UUID, String> namen) {
        List<Component> zeilen = new ArrayList<>();
        if (inseln.isEmpty()) {
            // KEIN Kopf darueber: Eine Ueberschrift ueber einer leeren Liste sieht aus
            // wie ein Fehler, und am ersten Tag ist das der Normalfall.
            zeilen.addAll(sprache.bau(WolkeSatz.LEER, 1L, Einsatz.keine()));
            return zeilen;
        }
        zeilen.addAll(sprache.bau(WolkeSatz.KOPF, inseln.size(),
                Einsatz.von("anzahl", inseln.size())));
        for (Parzelle insel : inseln) {
            zeilen.addAll(inselZeile(sprache, insel, namen));
        }
        zeilen.addAll(inseln.size() < hoechstzahl
                ? sprache.bau(WolkeSatz.NOCH_FREI, hoechstzahl,
                        Einsatz.von("hoechstzahl", hoechstzahl))
                : sprache.bau(WolkeSatz.VOLL, hoechstzahl,
                        Einsatz.von("hoechstzahl", hoechstzahl)));
        return zeilen;
    }

    /**
     * Eine Zeile je Insel - mit den Mitbauern, falls welche eingetragen sind.
     *
     * <p>Zwei Saetze und kein Platzhalter, der manchmal leer ist: Ein "Mitbauer: " ohne
     * Namen dahinter ist eine Zeile, die aussieht wie ein Fehler.
     */
    private static List<Component> inselZeile(Sprache sprache, Parzelle insel,
                                              Function<UUID, String> namen) {
        if (insel.mitbauer().isEmpty()) {
            return sprache.bau(WolkeSatz.ZEILE, 1L,
                    Einsatz.von("feld", Werdertext.feld(insel)));
        }
        return sprache.bau(WolkeSatz.ZEILE_MITBAUER, 1L,
                Einsatz.von("feld", Werdertext.feld(insel))
                        .mit("mitbauer", namenListe(insel, namen)));
    }

    /**
     * Die Mitbauer einer Insel als EINE Zeichenkette.
     *
     * <p>Mit Komma und Leerzeichen, wie jede andere Namensliste des Projekts. Die
     * Reihenfolge ist die der Ablage - sie bleibt gleich, solange niemand etwas aendert,
     * und eine Sortierung nach Namen waere eine zweite Meinung darueber, in welcher
     * Reihenfolge Mitbauer stehen.
     */
    private static String namenListe(Parzelle insel, Function<UUID, String> namen) {
        List<String> alle = new ArrayList<>();
        for (UUID wer : insel.mitbauer()) {
            alle.add(namen.apply(wer));
        }
        return String.join(", ", alle);
    }

    /**
     * Die Antwort auf {@code /wolke nehmen} - jeder Bescheid des Buches.
     *
     * <p><b>Ein {@code switch} ueber ALLE Bescheide ohne {@code default}</b>, und das ist
     * der Riegel dieser Methode: Ein neuer Bescheid uebersetzt sich nicht, solange hier
     * kein Satz dazu steht. Ein {@code default} machte ihn stumm - der Spieler bekaeme
     * eine Absage, die eine andere Lage beschreibt.
     *
     * @param hoechstzahl  was sein Rang erlaubt - steht in der Absage "du hast genug"
     * @param guthaben     sein Stand VOR dem Versuch; danach ist abgebucht
     * @param rangWerbung  die Zeile aus {@code hinweise.rang-werbung}, moeglicherweise
     *                     leer. Sie haengt NUR unter der Absage an der Grenze - bei
     *                     "die Welt ist voll" hilft kein Rang
     */
    static List<Component> nehmenZeilen(Sprache sprache, Wolkenbuch.Entscheid entscheid,
                                        int hoechstzahl, long guthaben,
                                        String rangWerbung) {
        return switch (entscheid.bescheid()) {
            case ERTEILT -> genommenZeilen(sprache, entscheid, guthaben);
            case GRENZE_ERREICHT -> Rangwerbung.darunter(sprache,
                    WolkeSatz.RANG_WERBUNG, rangWerbung,
                    sprache.bau(WolkeSatz.ABSAGE_GRENZE, hoechstzahl,
                            Einsatz.von("hoechstzahl", hoechstzahl)));
            case WELT_VOLL -> sprache.bau(WolkeSatz.ABSAGE_WELT_VOLL, 1L,
                    Einsatz.keine());
            case ZU_WENIG_MARK -> sprache.bau(WolkeSatz.ABSAGE_ZU_WENIG, 1L,
                    Einsatz.von("preis", sprache.betrag(entscheid.kosten()))
                            .mit("fehlt", sprache.betrag(
                                    entscheid.kosten() - guthaben)));
            case GESTOERT -> sprache.bau(WolkeSatz.ABSAGE_GESTOERT, 1L, Einsatz.keine());
        };
    }

    /**
     * Die Erfolgszeile - mit oder ohne Preis.
     *
     * <p>Die Fallunterscheidung steht HIER und nicht beim Befehl: Sonst faellt sie
     * zweimal, und eine der beiden Stellen entscheidet sie eines Tages anders. Ab Werk
     * ist eine Wolkeninsel gratis, der zweite Zweig also der Normalfall - und gerade
     * deshalb muss der erste stehen und geprueft sein.
     */
    private static List<Component> genommenZeilen(Sprache sprache,
                                                  Wolkenbuch.Entscheid entscheid,
                                                  long guthaben) {
        Parzelle insel = entscheid.insel().orElseThrow();
        if (entscheid.kosten() == 0L) {
            return sprache.bau(WolkeSatz.GENOMMEN_GRATIS, 1L,
                    Einsatz.von("feld", Werdertext.feld(insel)));
        }
        return sprache.bau(WolkeSatz.GENOMMEN, 1L,
                Einsatz.von("feld", Werdertext.feld(insel))
                        .mit("preis", sprache.betrag(entscheid.kosten()))
                        .mit("danach", sprache.betrag(guthaben - entscheid.kosten())));
    }

    /**
     * Die Insel ist eingetragen, aber die Bloecke stehen nicht.
     *
     * <p>Diese Zeile gibt es in keinem anderen Bereich des Projekts, und sie hat einen
     * Grund: In jeder anderen Welt macht der Weltgenerator das Gelaende, hier das Plugin.
     * Schlaegt das Aufsetzen fehl, gehoert dem Spieler ein Feld aus Luft - und er muss es
     * erfahren, denn von selbst merkt er es erst, wenn er hinreist und faellt.
     */
    static List<Component> aufbauGestoertZeilen(Sprache sprache, Parzelle insel) {
        return sprache.bau(WolkeSatz.AUFBAU_GESTOERT, 1L,
                Einsatz.von("feld", Werdertext.feld(insel)));
    }

    /** Es gibt keine Wolkenwelt - {@code wolken.welt} zeigt ins Leere. */
    static List<Component> keineWeltZeilen(Sprache sprache) {
        return sprache.bau(WolkeSatz.ABSAGE_KEINE_WELT, 1L, Einsatz.keine());
    }

    /** Du hast keine Insel - fuer {@code /wolke heim} und die Mitbauer. */
    static List<Component> keineInselZeilen(Sprache sprache) {
        return sprache.bau(WolkeSatz.KEINE_INSEL, 1L, Einsatz.keine());
    }

    /** Sich selbst eintragen geht nicht. */
    static List<Component> selbstZeilen(Sprache sprache) {
        return sprache.bau(WolkeSatz.MITBAUER_SELBST, 1L, Einsatz.keine());
    }

    /** Diesen Spieler kennt der Server nicht. */
    static List<Component> unbekannterSpielerZeilen(Sprache sprache, String name) {
        return sprache.bau(WolkeSatz.MITBAUER_UNBEKANNT, 1L,
                Einsatz.von("spieler", name));
    }

    /**
     * Die Antwort auf einen Mitbauer-Vorgang - jeder Bescheid des Buches.
     *
     * <p>Wieder ein {@code switch} ohne {@code default}, aus demselben Grund wie bei
     * {@link #nehmenZeilen}. Zwei Faelle reichen durch: {@code GESTOERT} nimmt denselben
     * Satz wie beim Nehmen (die Ablage schweigt, und das ist dieselbe Nachricht), und die
     * fremde Insel nennt ihren Besitzer.
     *
     * @param richtung ob eingetragen oder ausgetragen wurde - nur der Erfolgssatz
     *                 unterscheidet sich, die fuenf Absagen sind dieselben
     * @param name     der Name, um den es ging
     * @param namen    wie aus einer Kennung ein Name wird; fuer die fremde Insel
     */
    static List<Component> mitbauerZeilen(Sprache sprache,
                                          Wolkenbuch.Mitentscheid entscheid,
                                          Richtung richtung, String name,
                                          Function<UUID, String> namen) {
        return switch (entscheid.bescheid()) {
            case ERTEILT -> sprache.bau(richtung == Richtung.DAZU
                            ? WolkeSatz.MITBAUER_DAZU
                            : WolkeSatz.MITBAUER_WEG,
                    1L, Einsatz.von("spieler", name));
            case KEINE_INSEL -> sprache.bau(WolkeSatz.KEINE_INSEL, 1L, Einsatz.keine());
            case NICHT_EIGENTUEMER -> sprache.bau(WolkeSatz.MITBAUER_FREMD, 1L,
                    Einsatz.von("eigentuemer", namen.apply(
                            entscheid.insel().orElseThrow().eigentuemer())));
            case SCHON_EINGETRAGEN -> sprache.bau(WolkeSatz.MITBAUER_SCHON, 1L,
                    Einsatz.von("spieler", name));
            case NICHT_EINGETRAGEN -> sprache.bau(WolkeSatz.MITBAUER_NICHT_DABEI, 1L,
                    Einsatz.von("spieler", name));
            case GESTOERT -> sprache.bau(WolkeSatz.ABSAGE_GESTOERT, 1L, Einsatz.keine());
        };
    }

    // ================================================== Die Herausforderungen [Teil D]

    /**
     * Die Tafel der Herausforderungen - jede Aufgabe mit ihrem Stand.
     *
     * <p>Ein {@code switch} ueber alle vier Bescheide ohne {@code default}, aus demselben
     * Grund wie bei {@link #nehmenZeilen}.
     *
     * @param namen wie ein Aufgabenschluessel zu seinem Wort kommt - herein gegeben, weil
     *              der Name aus der SPRACHDATEI kommt und nicht aus der Liste: Was es
     *              gibt, steht in der {@code config.yml}, wie es heisst, in
     *              {@code sprache.yml}
     */
    static List<Component> aufgabenZeilen(Sprache sprache, Aufgabenbuch.Tafel tafel,
                                          Function<String, String> namen) {
        return switch (tafel.bescheid()) {
            case ERTEILT -> tafelZeilen(sprache, tafel, namen);
            case AUS -> sprache.bau(WolkeSatz.AUFGABEN_AUS, 1L, Einsatz.keine());
            case KEINE_INSEL -> sprache.bau(WolkeSatz.KEINE_INSEL, 1L, Einsatz.keine());
            case GESTOERT -> sprache.bau(WolkeSatz.ABSAGE_GESTOERT, 1L, Einsatz.keine());
            // Die fuenf Bescheide, die nur beim Abgeben vorkommen. Sie stehen hier
            // namentlich und nicht als default: Kaeme einer von ihnen doch aus der Tafel,
            // waere das ein Programmfehler - und eine Absage, die eine andere Lage
            // beschreibt, waere die unauffaelligste Art, ihn zu verbergen.
            case GIBT_ES_NICHT, NICHT_BERECHTIGT, SCHON_ERFUELLT, FEHLT, NICHT_GENOMMEN ->
                    throw new IllegalStateException("Die Tafel kann nicht "
                            + tafel.bescheid() + " sein - diesen Bescheid gibt es nur"
                            + " beim Abgeben");
        };
    }

    /** Kopf, eine Zeile je Aufgabe, in der Reihenfolge der Datei. */
    private static List<Component> tafelZeilen(Sprache sprache, Aufgabenbuch.Tafel tafel,
                                               Function<String, String> namen) {
        List<Component> zeilen = new ArrayList<>(sprache.bau(WolkeSatz.AUFGABEN_KOPF, 1L,
                Einsatz.von("erfuellt", tafel.stufe())
                        .mit("gesamt", tafel.zeilen().size())));
        for (Aufgabenbuch.Zeile zeile : tafel.zeilen()) {
            String name = namen.apply(zeile.aufgabe().schluessel());
            if (zeile.erfuellt()) {
                zeilen.addAll(sprache.bau(WolkeSatz.AUFGABEN_FERTIG, 1L,
                        Einsatz.von("name", name)));
                continue;
            }
            zeilen.addAll(zeile.aufgabe().belohnung() == 0L
                    ? sprache.bau(WolkeSatz.AUFGABEN_OFFEN_OHNE_LOHN, 1L,
                            Einsatz.von("name", name)
                                    .mit("verlangt", mengen(zeile.aufgabe().verlangt())))
                    : sprache.bau(WolkeSatz.AUFGABEN_OFFEN, 1L,
                            Einsatz.von("name", name)
                                    .mit("verlangt", mengen(zeile.aufgabe().verlangt()))
                                    .mit("belohnung",
                                            sprache.betrag(zeile.aufgabe().belohnung()))));
        }
        return zeilen;
    }

    /**
     * Die Antwort auf {@code /wolke abgeben} - jeder Bescheid des Aufgabenbuchs.
     *
     * <p>Wieder ohne {@code default}. Vier Bescheide teilen ihren Satz mit anderen
     * Zweigen: {@code AUS} und {@code KEINE_INSEL} wie in der Tafel, {@code GESTOERT} wie
     * beim Nehmen - die Ablage schweigt, und das ist dieselbe Nachricht.
     *
     * @param getippt was der Spieler geschrieben hat - fuer die Absage "gibt es nicht".
     *                Aus dem Entscheid kaeme sie nicht: Dort steht keine Aufgabe, denn es
     *                gibt sie ja nicht
     */
    static List<Component> abgebenZeilen(Sprache sprache, Aufgabenbuch.Entscheid entscheid,
                                         String getippt, Function<String, String> namen,
                                         Function<UUID, String> spielernamen,
                                         Parzelle insel) {
        return switch (entscheid.bescheid()) {
            case ERTEILT -> erteiltZeilen(sprache, entscheid, namen);
            case AUS -> sprache.bau(WolkeSatz.AUFGABEN_AUS, 1L, Einsatz.keine());
            case GIBT_ES_NICHT -> sprache.bau(WolkeSatz.ABGABE_UNBEKANNT, 1L,
                    Einsatz.von("name", getippt));
            case KEINE_INSEL -> sprache.bau(WolkeSatz.KEINE_INSEL, 1L, Einsatz.keine());
            case NICHT_BERECHTIGT -> sprache.bau(WolkeSatz.ABGABE_FREMD, 1L,
                    Einsatz.von("eigentuemer",
                            spielernamen.apply(insel.eigentuemer())));
            case SCHON_ERFUELLT -> sprache.bau(WolkeSatz.ABGABE_SCHON, 1L,
                    Einsatz.von("name", namen.apply(schluessel(entscheid))));
            case FEHLT -> sprache.bau(WolkeSatz.ABGABE_FEHLT, 1L,
                    Einsatz.von("name", namen.apply(schluessel(entscheid)))
                            .mit("fehlt", mengen(entscheid.fehlt())));
            case NICHT_GENOMMEN -> sprache.bau(WolkeSatz.ABGABE_WEGGEKOMMEN, 1L,
                    Einsatz.von("name", namen.apply(schluessel(entscheid))));
            case GESTOERT -> sprache.bau(WolkeSatz.ABSAGE_GESTOERT, 1L, Einsatz.keine());
        };
    }

    /** Die Zusage - mit oder ohne Lohn, wie beim Nehmen. */
    private static List<Component> erteiltZeilen(Sprache sprache,
                                                 Aufgabenbuch.Entscheid entscheid,
                                                 Function<String, String> namen) {
        String name = namen.apply(schluessel(entscheid));
        if (entscheid.belohnung() == 0L) {
            return sprache.bau(WolkeSatz.ABGABE_ERTEILT_OHNE_LOHN, 1L,
                    Einsatz.von("name", name).mit("stufe", entscheid.stufe()));
        }
        return sprache.bau(WolkeSatz.ABGABE_ERTEILT, 1L,
                Einsatz.von("name", name)
                        .mit("belohnung", sprache.betrag(entscheid.belohnung()))
                        .mit("stufe", entscheid.stufe()));
    }

    private static String schluessel(Aufgabenbuch.Entscheid entscheid) {
        return entscheid.aufgabe().orElseThrow().schluessel();
    }

    /**
     * Gegenstaende und Mengen in EINER Zeichenkette - "64x cobblestone, 1x furnace".
     *
     * <p>Die technischen Namen und keine Uebersetzung, und zwar bewusst: Genau diese Namen
     * stehen in der {@code config.yml}, in der Angeltabelle, in der Erztabelle und auf den
     * Shopschildern. Ein uebersetzter Name hier waere der erste Ort im Spiel, an dem ein
     * Gegenstand anders heisst als in jeder Einstellung - und ein Spieler, der danach
     * fragt, bekaeme von seinem Betreiber die falsche Antwort.
     *
     * <p>Die Reihenfolge ist die der Aufgabe, also die der Datei: Was der Betreiber zuerst
     * getippt hat, steht zuerst - und die fehlenden Mengen stehen in derselben Folge wie
     * die verlangten.
     */
    private static String mengen(Map<String, Integer> gegenstaende) {
        List<String> stuecke = new ArrayList<>();
        for (var zeile : gegenstaende.entrySet()) {
            stuecke.add(zeile.getValue() + "x " + zeile.getKey());
        }
        return String.join(", ", stuecke);
    }

    // ================================================== Punkte und Rangliste [Teil E]

    /**
     * Die Antwort auf {@code /wolke punkte} - jeder Bescheid des Punktebuchs.
     *
     * <p>Ein {@code switch} ueber alle sechs ohne {@code default}, wie ueberall in dieser
     * Klasse. Die FERTIG-Zeile steht nicht darin: Sie kommt Sekunden spaeter, wenn die
     * Zaehlung durch ist - siehe {@link #punkteFertigZeilen}.
     */
    static List<Component> punkteZeilen(Sprache sprache, Punktebuch.Entscheid entscheid,
                                        Function<UUID, String> namen) {
        return switch (entscheid.bescheid()) {
            case ANGEFANGEN -> sprache.bau(WolkeSatz.PUNKTE_LAEUFT, 1L,
                    Einsatz.von("chunks",
                            entscheid.auftrag().orElseThrow().chunkzahl()));
            case AUS -> sprache.bau(WolkeSatz.PUNKTE_AUS, 1L, Einsatz.keine());
            case KEINE_INSEL -> sprache.bau(WolkeSatz.KEINE_INSEL, 1L, Einsatz.keine());
            case ZU_FRUEH -> sprache.bau(WolkeSatz.PUNKTE_ZU_FRUEH, 1L,
                    Einsatz.von("sekunden", entscheid.wartesekunden()));
            case LAEUFT_SCHON -> sprache.bau(WolkeSatz.PUNKTE_LAEUFT_SCHON, 1L,
                    Einsatz.keine());
            case GESTOERT -> sprache.bau(WolkeSatz.ABSAGE_GESTOERT, 1L, Einsatz.keine());
        };
    }

    /** Die Zeile, wenn die Zaehlung durch ist. */
    static List<Component> punkteFertigZeilen(Sprache sprache, long punkte) {
        return sprache.bau(WolkeSatz.PUNKTE_FERTIG, 1L, Einsatz.von("punkte", punkte));
    }

    /**
     * Die Rangliste - Kopf und je Platz eine Zeile.
     *
     * <p>Leer heisst: Es gibt noch keine Insel. Das ist am ersten Tag der Normalfall, und
     * eine Ueberschrift ueber einer leeren Liste sieht aus wie ein Fehler - dieselbe
     * Antwort wie bei {@link #listenZeilen}.
     */
    static List<Component> ranglistenZeilen(Sprache sprache,
                                            List<Punktebuch.Platz> plaetze,
                                            Function<UUID, String> namen) {
        if (plaetze.isEmpty()) {
            return sprache.bau(WolkeSatz.RANGLISTE_LEER, 1L, Einsatz.keine());
        }
        List<Component> zeilen = new ArrayList<>(
                sprache.bau(WolkeSatz.RANGLISTE_KOPF, 1L, Einsatz.keine()));
        for (Punktebuch.Platz platz : plaetze) {
            zeilen.addAll(sprache.bau(WolkeSatz.RANGLISTE_ZEILE, 1L,
                    Einsatz.von("rang", platz.rang())
                            .mit("spieler", namen.apply(platz.insel().eigentuemer()))
                            .mit("feld", Werdertext.feld(platz.insel()))
                            .mit("punkte", platz.punkte())
                            .mit("stufe", platz.stufe())));
        }
        return zeilen;
    }

    // ================================================== Biom und Zuruecksetzen [Teil F]

    /**
     * Der Stand: welches Biom die Insel hat, was ein Wechsel kostet, was zur Wahl steht.
     *
     * <p>Die Antwort auf {@code /wolke biom} OHNE Namen. Zwei Zeilen und nicht eine: Die
     * Liste der Biome ist lang, und ein Spieler, der nur wissen will, was er hat, soll es
     * in der ersten Zeile lesen.
     */
    static List<Component> biomStandZeilen(Sprache sprache, String biom, long preis,
                                           List<String> angebot) {
        List<Component> zeilen = new ArrayList<>(sprache.bau(WolkeSatz.BIOM_STAND, 1L,
                Einsatz.von("biom", biom).mit("preis", sprache.betrag(preis))));
        if (angebot.isEmpty()) {
            zeilen.addAll(sprache.bau(WolkeSatz.BIOM_KEINE_WAHL, 1L, Einsatz.keine()));
            return zeilen;
        }
        zeilen.addAll(sprache.bau(WolkeSatz.BIOM_WAHL, 1L,
                Einsatz.von("biome", String.join(", ", angebot))));
        return zeilen;
    }

    /**
     * Die Antwort auf {@code /wolke biom <name>} - jeder Bescheid des Biombuchs.
     *
     * <p>Wieder ohne {@code default}. Die Absage "nicht angeboten" nennt die ganze Liste:
     * Ein Spieler, der sich vertippt hat, soll nicht erst einen zweiten Befehl brauchen.
     *
     * @param guthaben sein Stand VOR dem Versuch - die Erfolgszeile nennt, was bleibt
     * @param angebot  die Biome zur Wahl; sie stehen in der Absage
     */
    static List<Component> biomZeilen(Sprache sprache, Biombuch.Entscheid entscheid,
                                      long guthaben, List<String> angebot,
                                      Function<UUID, String> namen) {
        return switch (entscheid.bescheid()) {
            case ERTEILT -> entscheid.kosten() == 0L
                    ? sprache.bau(WolkeSatz.BIOM_GESETZT_GRATIS, 1L,
                            Einsatz.von("biom", entscheid.biom()))
                    : sprache.bau(WolkeSatz.BIOM_GESETZT, 1L,
                            Einsatz.von("biom", entscheid.biom())
                                    .mit("preis", sprache.betrag(entscheid.kosten()))
                                    .mit("danach", sprache.betrag(
                                            guthaben - entscheid.kosten())));
            case KEINE_WAHL -> sprache.bau(WolkeSatz.BIOM_KEINE_WAHL, 1L,
                    Einsatz.keine());
            case NICHT_ANGEBOTEN -> sprache.bau(WolkeSatz.BIOM_NICHT_ANGEBOTEN, 1L,
                    Einsatz.von("biom", entscheid.biom())
                            .mit("biome", String.join(", ", angebot)));
            case KEINE_INSEL -> sprache.bau(WolkeSatz.KEINE_INSEL, 1L, Einsatz.keine());
            case NICHT_EIGENTUEMER -> sprache.bau(WolkeSatz.BIOM_NICHT_DEINE, 1L,
                    Einsatz.von("eigentuemer", namen.apply(
                            entscheid.insel().orElseThrow().eigentuemer())));
            case ZU_WENIG_MARK -> sprache.bau(WolkeSatz.BIOM_ZU_WENIG, 1L,
                    Einsatz.von("preis", sprache.betrag(entscheid.kosten()))
                            .mit("fehlt", sprache.betrag(
                                    entscheid.kosten() - guthaben)));
            case GESTOERT -> sprache.bau(WolkeSatz.ABSAGE_GESTOERT, 1L, Einsatz.keine());
        };
    }

    /** Die Warnung vor dem Zuruecksetzen - sie verlangt das zweite Wort. */
    static List<Component> zuruecksetzenWarnungZeilen(Sprache sprache) {
        return sprache.bau(WolkeSatz.ZURUECK_WARNUNG, 1L, Einsatz.keine());
    }

    /** Die Startinsel steht wieder. */
    static List<Component> zuruecksetzenZeilen(Sprache sprache, Parzelle insel) {
        return sprache.bau(WolkeSatz.ZURUECK_ERTEILT, 1L,
                Einsatz.von("feld", Werdertext.feld(insel)));
    }
}
