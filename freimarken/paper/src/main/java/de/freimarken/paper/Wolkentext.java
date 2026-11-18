package de.freimarken.paper;

import de.freimarken.kern.Parzelle;
import de.freimarken.kern.Wolkenbuch;
import java.util.ArrayList;
import java.util.List;
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
     * @param hoechstzahl was sein Rang erlaubt - steht in der Absage "du hast genug"
     * @param guthaben    sein Stand VOR dem Versuch; danach ist abgebucht
     */
    static List<Component> nehmenZeilen(Sprache sprache, Wolkenbuch.Entscheid entscheid,
                                        int hoechstzahl, long guthaben) {
        return switch (entscheid.bescheid()) {
            case ERTEILT -> genommenZeilen(sprache, entscheid, guthaben);
            case GRENZE_ERREICHT -> sprache.bau(WolkeSatz.ABSAGE_GRENZE, hoechstzahl,
                    Einsatz.von("hoechstzahl", hoechstzahl));
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
}
