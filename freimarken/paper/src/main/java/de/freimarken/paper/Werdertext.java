package de.freimarken.paper;

import de.freimarken.kern.Inselbuch;
import de.freimarken.kern.Inselplan;
import de.freimarken.kern.Parzelle;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import net.kyori.adventure.text.Component;

/**
 * Die Zeilen der Meerinseln - reine Rechnung, kein Bukkit, kein Senden.
 *
 * <p><b>Das ist DIE NAHT dieses Bereichs</b>, wie {@link Freistadttext} fuer die
 * Freistaedte: {@link WerderBefehle} holt sich hier seine Zeilen und schickt sie nur
 * hinaus. Dadurch ist jedes Wort, jede Farbe und jede Reihenfolge ohne Server pruefbar -
 * und {@code BildGleichTest} haengt genau an diesen Methoden.
 *
 * <p>Kein Wort steht hier: Der Wortlaut kommt aus {@code sprache-vorlage.yml} ueber
 * {@link WerderSatz}.
 *
 * <p><b>Die ADRESSE einer Insel entsteht an EINER Stelle</b>, in {@link #feld}: Sie steht
 * in sechs Saetzen, und zwei Schreibweisen fuer dieselbe Insel - "2/-3" hier und
 * "2 / -3" dort - saehen aus wie zwei Inseln.
 */
final class Werdertext {

    private Werdertext() {
    }

    /**
     * Die Adresse einer Insel: Feldnummern mit Schraegstrich.
     *
     * <p>Dieselbe Form wie bei der Freistadt ({@code freistadt.zeile} nennt "Feld x/z"),
     * und dieselbe Form wie eine Parzellennummer. Ein Spieler soll nicht lernen muessen,
     * dass dieselbe Zahl in drei Welten dreimal anders geschrieben wird.
     */
    static String feld(Parzelle insel) {
        return insel.x() + "/" + insel.z();
    }

    /** Dieselbe Adresse aus zwei Feldnummern - fuer eine Stelle, an der noch nichts liegt. */
    static String feld(int feldX, int feldZ) {
        return feldX + "/" + feldZ;
    }

    /**
     * Die Liste der eigenen Inseln.
     *
     * @param preis was eine Insel kostet - er steht in der Zeile fuer den, der keine hat
     */
    static List<Component> listenZeilen(Sprache sprache, List<Parzelle> inseln,
                                        long preis) {
        List<Component> zeilen = new ArrayList<>();
        if (inseln.isEmpty()) {
            // KEIN Kopf darueber: Eine Ueberschrift ueber einer leeren Liste sieht aus
            // wie ein Fehler. Der Satz sagt alles, was jetzt zu sagen ist - und nennt
            // den Preis, denn das ist die naechste Frage.
            zeilen.add(sprache.eineZeile(WerderSatz.LEER,
                    Einsatz.von("preis", sprache.betrag(preis))));
            return List.copyOf(zeilen);
        }
        zeilen.addAll(sprache.bau(WerderSatz.KOPF, inseln.size(),
                Einsatz.von("anzahl", inseln.size())));
        for (Parzelle insel : inseln) {
            zeilen.add(sprache.eineZeile(WerderSatz.ZEILE,
                    Einsatz.von("feld", feld(insel))));
        }
        return List.copyOf(zeilen);
    }

    /**
     * Was an dieser Stelle liegt - die Antwort von {@code /werder hafen}.
     *
     * <p>Eine LISTE und keine Zeile: {@code werder.hier.frei} hat zwei Zeilen, und die
     * zweite ist der Ausweg. Dass alle fuenf Lagen durch denselben Weg gehen, ist dabei
     * Absicht - sonst entscheidet die Gestalt des Satzes, welche Methode ihn holt, und
     * die naechste Zeile in der Vorlage macht den Zweig stumm.
     *
     * <p>Fuenf Lagen, und die Reihenfolge der Fragen ist dieselbe wie im Buch: erst die
     * Geometrie (Meer, Ankunftsinsel), dann die Ablage (frei, eigen, fremd). Wer sie
     * anders ordnet, bekommt im offenen Meer die Antwort "frei" - denn
     * {@code feldNummer} nennt dort das Feld DAVOR.
     *
     * @param insel     was an dieser Stelle vergeben ist, oder leer
     * @param wer       der Fragende - fuer die Unterscheidung eigen gegen fremd
     * @param namen     wie aus einer Kennung ein Name wird; aus Bukkit, darum hereingegeben
     */
    static List<Component> hierZeilen(Sprache sprache, Inselplan plan, int weltX,
                                      int weltZ, Optional<Parzelle> insel, UUID wer,
                                      long preis, Function<UUID, String> namen) {
        if (plan.feldBei(weltX, weltZ) != Inselplan.Feld.INSELFELD) {
            return sprache.bau(WerderSatz.HIER_MEER, 1L, Einsatz.keine());
        }
        int feldX = plan.feldNummer(weltX);
        int feldZ = plan.feldNummer(weltZ);
        if (plan.istAnkunftsinsel(feldX, feldZ)) {
            return sprache.bau(WerderSatz.HIER_ANKUNFT, 1L, Einsatz.keine());
        }
        if (insel.isEmpty()) {
            // UEBER bau und nicht ueber eineZeile, und das ist nicht Geschmack: Dieser
            // Satz hat ZWEI Zeilen - die zweite ist der Ausweg ("nimm sie mit ..."), und
            // eineZeile WIRFT bei einem mehrzeiligen Satz. Im Spiel haette der Zweig
            // damit gar nichts gesendet. Gefunden hat es Probe 10h, keine
            // Verhaltensprobe; darum geht die ganze Methode jetzt ueber Listen.
            return sprache.bau(WerderSatz.HIER_FREI, 1L,
                    Einsatz.von("feld", feld(feldX, feldZ))
                            .mit("preis", sprache.betrag(preis)));
        }
        Parzelle da = insel.get();
        if (da.gehoert(wer)) {
            return sprache.bau(WerderSatz.HIER_EIGENE, 1L,
                    Einsatz.von("feld", feld(da)));
        }
        return sprache.bau(WerderSatz.HIER_FREMD, 1L,
                Einsatz.von("feld", feld(da))
                        .mit("besitzer", namen.apply(da.eigentuemer())));
    }

    /**
     * Die Antwort auf {@code /werder hafen nehmen} - jeder Bescheid des Buches.
     *
     * <p><b>Ein {@code switch} ueber ALLE Bescheide ohne {@code default}</b>, und das ist
     * der Riegel dieser Methode: Ein neuer Bescheid uebersetzt sich nicht, solange hier
     * kein Satz dazu steht. Ein {@code default} machte ihn stumm - der Spieler bekaeme
     * eine Absage, die eine andere Lage beschreibt.
     *
     * @param hoechstzahl was sein Rang erlaubt - steht in der Absage "du hast genug"
     * @param guthaben    sein Stand VOR dem Versuch; danach ist abgebucht
     * @param namen       wie aus einer Kennung ein Name wird
     */
    static List<Component> nehmenZeilen(Sprache sprache, Inselbuch.Entscheid entscheid,
                                        int hoechstzahl, long guthaben,
                                        Function<UUID, String> namen) {
        return switch (entscheid.bescheid()) {
            case ERTEILT -> genommenZeilen(sprache, entscheid, guthaben);
            case KEIN_INSELFELD -> sprache.bau(WerderSatz.HIER_MEER, 1L,
                    Einsatz.keine());
            case ANKUNFTSINSEL -> sprache.bau(WerderSatz.HIER_ANKUNFT, 1L,
                    Einsatz.keine());
            case SCHON_VERGEBEN -> sprache.bau(WerderSatz.HIER_FREMD, 1L,
                    Einsatz.von("feld", feld(entscheid.insel().orElseThrow()))
                            .mit("besitzer", namen.apply(
                                    entscheid.insel().orElseThrow().eigentuemer())));
            case AUSSERHALB -> sprache.bau(WerderSatz.ABSAGE_AUSSERHALB, 1L,
                    Einsatz.keine());
            case GRENZE_ERREICHT -> sprache.bau(WerderSatz.ABSAGE_GENUG, hoechstzahl,
                    Einsatz.von("hoechstzahl", hoechstzahl));
            case ZU_WENIG_MARK -> sprache.bau(WerderSatz.ABSAGE_ZU_WENIG, 1L,
                    Einsatz.von("preis", sprache.betrag(entscheid.kosten()))
                            .mit("fehlt", sprache.betrag(
                                    entscheid.kosten() - guthaben)));
            case GESTOERT -> sprache.bau(WerderSatz.ABSAGE_GESTOERT, 1L,
                    Einsatz.keine());
        };
    }

    /**
     * Die Erfolgszeile - mit oder ohne Preis.
     *
     * <p>Die Fallunterscheidung steht HIER und nicht beim Befehl: Sonst faellt sie
     * zweimal, und eine der beiden Stellen entscheidet sie eines Tages anders.
     */
    private static List<Component> genommenZeilen(Sprache sprache,
                                                  Inselbuch.Entscheid entscheid,
                                                  long guthaben) {
        Parzelle insel = entscheid.insel().orElseThrow();
        if (entscheid.kosten() == 0L) {
            return sprache.bau(WerderSatz.GENOMMEN_GRATIS, 1L,
                    Einsatz.von("feld", feld(insel)));
        }
        // Zwei Zeilen, und die zweite nennt den Rest - siehe den Fund bei hierZeilen.
        return sprache.bau(WerderSatz.GENOMMEN, 1L,
                Einsatz.von("feld", feld(insel))
                        .mit("preis", sprache.betrag(entscheid.kosten()))
                        // Der Stand von VORHER minus dem Preis und nicht ein zweites
                        // Mal gefragt: Zwischen Abbuchung und Antwort kann ein
                        // Tagesbonus liegen, und dann nennte die Zeile eine Zahl, die
                        // sich nicht aus ihren eigenen Teilen ergibt.
                        .mit("danach", sprache.betrag(guthaben - entscheid.kosten())));
    }

    /** Es gibt keine Inselwelt - {@code inseln.welt} zeigt ins Leere. */
    static List<Component> keineWeltZeilen(Sprache sprache) {
        return sprache.bau(WerderSatz.ABSAGE_KEINE_WELT, 1L, Einsatz.keine());
    }

    /**
     * Der Spieler steht nicht in Hafenwerder.
     *
     * @param welt der Name der Inselwelt OHNE Namensraum - er steht im Weg dorthin
     */
    static List<Component> falscheWeltZeilen(Sprache sprache, String welt) {
        return sprache.bau(WerderSatz.ABSAGE_FALSCHE_WELT, 1L,
                Einsatz.von("welt", welt));
    }
}
