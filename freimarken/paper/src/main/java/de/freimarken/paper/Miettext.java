package de.freimarken.paper;

import de.freimarken.kern.Mietvertrag;
import java.util.ArrayList;
import java.util.List;
import net.kyori.adventure.text.Component;

/**
 * Die Zeilen der Ladenmiete - reine Rechnung, kein Bukkit, kein Senden.
 *
 * <p><b>Das ist DIE NAHT dieses Bereichs</b>, wie {@link Ladentext} fuer die Liste und
 * {@link Bonustext} fuer den Tagesbonus: Jeder Zweig von {@link LadenBefehle} holt sich
 * hier seine Zeilen und schickt sie nur hinaus. Dadurch ist jedes Wort, jede Farbe, jede
 * Verzweigung und jede Zahl ohne Server pruefbar.
 *
 * <p>Kein Wort steht hier: Der Wortlaut kommt aus {@code sprache-vorlage.yml} ueber
 * {@link MietSatz}. Was hier bleibt, ist die WAHL - welcher Satz zu welchem Bescheid
 * gehoert - und die eine Rechnung, die ein Spieler sonst selbst machen muesste: wie viel
 * ihm fehlt.
 *
 * <p><b>Absagen gehen ueber {@link Sprache#bau} und Zusagen ueber
 * {@link Sprache#eineZeile}</b>, und das ist eine Regel und kein Zufall: Drei Absagen
 * tragen absichtlich einen Hinweis in einer zweiten Zeile ("/laden zeigt, welche es
 * gibt"), und ein Betreiber darf jeder weiteren einen anhaengen. Bei einer ZUSAGE waere
 * ein Umbruch dagegen eine stille Verschiebung des Bildes - dort sichert
 * {@code eineZeile} zu, dass genau eine Zeile hinausgeht. Deshalb liefert auch das
 * Verlaengern eine Liste und keine einzelne Zeile.
 *
 * <p><b>Die Zuordnung Bescheid zu Satz steht HIER und nicht am Sendeort.</b> Sie wird hier
 * entschieden, und eine Probe kann sie unmittelbar aufrufen - dieselbe Begruendung wie bei
 * {@code Sprachschluessel.bescheid}. Jeder {@code switch} nennt jeden Bescheid, den seine
 * Tat zurueckgeben kann, OHNE {@code default}: Ein neuer Bescheid bricht damit den Bau und
 * nicht erst das Spielbild.
 */
final class Miettext {

    private Miettext() {
    }

    /**
     * Die Antwort auf {@code /laden mieten} - eine bis drei Zeilen.
     *
     * <p>Drei beim Erfolg: was gemietet wurde, was es gekostet hat, was taeglich faellig
     * wird. Drei und nicht eine, weil ein Abschluss drei verschiedene Dinge bindet - Ort,
     * Geld jetzt, Geld taeglich -, und weil die dritte Zeile den Befehl zum Verlaengern
     * nennt. Eine bei jeder Absage.
     */
    static List<Component> mietenZeilen(Sprache sprache, Mietbuch.Abschluss abschluss,
                                        String getippt) {
        // Ein switch als AUSDRUCK und nicht als Anweisung, und das ist der Grund, warum
        // hier kein default steht: Nur der Ausdruck verlangt Vollstaendigkeit. Mit einem
        // default fiele ein neuer Bescheid stumm hinein, statt den Bau zu brechen.
        return switch (abschluss.bescheid()) {
            case GEMIETET -> erfolgZeilen(sprache, abschluss);
            case KEINE_EINHEIT -> absage(sprache, MietSatz.ABSAGE_KEINE_EINHEIT,
                    Einsatz.von("laden", getippt));
            case SCHON_VERMIETET -> absage(sprache, MietSatz.ABSAGE_SCHON_VERMIETET,
                    Einsatz.von("laden", getippt));
            // SEIN Laden und nicht der gewuenschte - sonst sucht er, welchen er hat.
            case SCHON_EIN_LADEN -> absage(sprache, MietSatz.ABSAGE_SCHON_EIN_LADEN,
                    Einsatz.von("laden", abschluss.vertrag().einheit()));
            case ZU_WENIG_GELD -> absage(sprache, MietSatz.ABSAGE_ZU_WENIG_GELD, Einsatz
                    .von("kosten", sprache.betrag(abschluss.kosten()))
                    .mit("guthaben", sprache.betrag(abschluss.guthaben()))
                    // Die eine Rechnung, die das Programm dem Spieler abnimmt.
                    .mit("fehlt", sprache.betrag(
                            abschluss.kosten() - abschluss.guthaben())));
            case GESTOERT -> absage(sprache, MietSatz.ABSAGE_GESTOERT, Einsatz.keine());
            // Die Bescheide der anderen beiden Taten koennen hier nicht ankommen -
            // genannt, weil der Ausdruck jeden nennen muss.
            case VERLAENGERT, GEKUENDIGT, KEIN_LADEN, NOCH_ZU_FRUEH ->
                    throw new IllegalArgumentException(abschluss.bescheid()
                            + " kommt nicht aus mieten()");
        };
    }

    /**
     * Die Antwort auf {@code /laden verlaengern} - eine Zeile, bei einer Absage mit
     * Hinweis auch zwei.
     *
     * <p>Bei {@link Mietbuch.Bescheid#NOCH_ZU_FRUEH} stehen ZWEI Zahlen in der Zeile: wie
     * viele Tage noch laufen und wie lang eine Mietdauer ist. Nur mit beiden ist die
     * Absage zu verstehen - "noch 59 Tage" allein sagt nicht, dass bei 30 Tagen Mietdauer
     * erst unter 30 Resttagen verlaengert werden kann.
     */
    static List<Component> verlaengernZeilen(Sprache sprache,
                                             Mietbuch.Verlaengerung ergebnis,
                                             int dauerTage) {
        return switch (ergebnis.bescheid()) {
            case VERLAENGERT -> List.of(sprache.eineZeile(MietSatz.VERLAENGERT, Einsatz
                    .von("laden", ergebnis.vertrag().einheit())
                    .mit("bis", tag(sprache, ergebnis.vertrag()))
                    .mit("tage", String.valueOf(ergebnis.restTage()))));
            case NOCH_ZU_FRUEH -> absage(sprache, MietSatz.ABSAGE_NOCH_ZU_FRUEH, Einsatz
                    .von("laden", ergebnis.vertrag().einheit())
                    .mit("tage", String.valueOf(ergebnis.restTage()))
                    .mit("dauer", String.valueOf(dauerTage)));
            case KEIN_LADEN -> absage(sprache, MietSatz.ABSAGE_KEIN_LADEN,
                    Einsatz.keine());
            case GESTOERT -> absage(sprache, MietSatz.ABSAGE_GESTOERT, Einsatz.keine());
            case GEMIETET, GEKUENDIGT, KEINE_EINHEIT, SCHON_VERMIETET, SCHON_EIN_LADEN,
                 ZU_WENIG_GELD -> throw new IllegalArgumentException(ergebnis.bescheid()
                    + " kommt nicht aus verlaengern()");
        };
    }

    /**
     * Die Antwort auf {@code /laden kuendigen} - eine oder zwei Zeilen.
     *
     * <p>Zwei, wenn eine Kaution zurueckgeht. Eine, wenn keine hinterlegt war - "0 Mark
     * zurueck" sieht nach einem Fehler aus.
     */
    static List<Component> kuendigenZeilen(Sprache sprache,
                                           Mietbuch.Kuendigung ergebnis) {
        // Ein AUSDRUCK, siehe mietenZeilen: Nur er verlangt, dass jeder Bescheid
        // genannt ist.
        return switch (ergebnis.bescheid()) {
            case GEKUENDIGT -> {
                List<Component> zeilen = new ArrayList<>();
                zeilen.add(sprache.eineZeile(MietSatz.GEKUENDIGT,
                        Einsatz.von("laden", ergebnis.einheit())));
                if (ergebnis.kautionZurueck() > 0L) {
                    zeilen.add(sprache.eineZeile(MietSatz.GEKUENDIGT_KAUTION,
                            Einsatz.von("betrag",
                                    sprache.betrag(ergebnis.kautionZurueck()))));
                }
                yield List.copyOf(zeilen);
            }
            case KEIN_LADEN -> absage(sprache, MietSatz.ABSAGE_KEIN_LADEN,
                    Einsatz.keine());
            case GESTOERT -> absage(sprache, MietSatz.ABSAGE_GESTOERT, Einsatz.keine());
            case GEMIETET, VERLAENGERT, KEINE_EINHEIT, SCHON_VERMIETET, SCHON_EIN_LADEN,
                 NOCH_ZU_FRUEH, ZU_WENIG_GELD ->
                    throw new IllegalArgumentException(ergebnis.bescheid()
                            + " kommt nicht aus kuendigen()");
        };
    }

    /**
     * Die letzte Zeile von {@code /laden} - der eigene Laden.
     *
     * <p>Auch ohne Laden eine Zeile: Wer die Liste sieht, soll erfahren, wie man mietet.
     *
     * @param vertrag  der eigene Vertrag, oder {@code null}
     * @param restTage wie viele Tage er noch laeuft - gerechnet in der eingestellten
     *                 Zone, also NICHT hier
     */
    static Component standZeile(Sprache sprache, Mietvertrag vertrag, long restTage) {
        if (vertrag == null) {
            return sprache.eineZeile(MietSatz.STAND_KEINER, Einsatz.keine());
        }
        return sprache.eineZeile(MietSatz.STAND, Einsatz
                .von("laden", vertrag.einheit())
                .mit("bis", tag(sprache, vertrag))
                .mit("tage", String.valueOf(restTage))
                // Die VEREINBARTE Miete und nicht die aus der config.yml - der
                // Bestandsschutz wird hier sichtbar, und genau hier muss er es sein.
                .mit("miete", sprache.betrag(vertrag.miete())));
    }

    /**
     * Die Absage an den, der nicht in der Markthalle steht.
     *
     * <p>Auch dieser Satz wird HIER gewaehlt und nicht im Befehl, obwohl der Befehl den
     * Ort prueft: Sonst waere er die einzige Zeile dieses Bereichs, die eine Probe nicht
     * ohne Server holen kann - und genau solche Zeilen bleiben beim Umbenennen stehen.
     */
    static List<Component> falscheWeltZeilen(Sprache sprache) {
        return absage(sprache, MietSatz.ABSAGE_FALSCHE_WELT, Einsatz.keine());
    }

    /**
     * Eine Absage - ueber {@code bau} und nicht ueber {@code eineZeile}.
     *
     * <p>Die EINE Stelle, an der das geschieht: Sonst entscheidet jeder Zweig fuer sich,
     * ob eine Absage einen Hinweis tragen darf, und einer von ihnen wirft eines Tages,
     * weil der Betreiber genau dort eine zweite Zeile gewollt hat.
     */
    private static List<Component> absage(Sprache sprache, MietSatz satz, Einsatz werte) {
        return sprache.bau(satz, 1L, werte);
    }

    private static List<Component> erfolgZeilen(Sprache sprache,
                                                Mietbuch.Abschluss abschluss) {
        Mietvertrag vertrag = abschluss.vertrag();
        List<Component> zeilen = new ArrayList<>();
        zeilen.add(sprache.eineZeile(MietSatz.GEMIETET_KOPF, Einsatz
                .von("laden", vertrag.einheit())
                .mit("bis", tag(sprache, vertrag))
                .mit("tage", String.valueOf(
                        vertrag.laeuftBis().toEpochDay() - vertrag.beginn().toEpochDay()
                                + 1L))));
        if (abschluss.lizenz()) {
            // Beide Zahlen: Wer 55.000 abgebucht sieht und 5.000 erwartet hat, soll in
            // derselben Zeile lesen, warum.
            zeilen.add(sprache.eineZeile(MietSatz.GEMIETET_ERSTMALS, Einsatz
                    .von("betrag", sprache.betrag(abschluss.kosten()))
                    .mit("lizenz", sprache.betrag(
                            abschluss.kosten() - vertrag.kaution()))));
        } else {
            zeilen.add(sprache.eineZeile(MietSatz.GEMIETET_KOSTEN,
                    Einsatz.von("betrag", sprache.betrag(abschluss.kosten()))));
        }
        zeilen.add(sprache.eineZeile(MietSatz.GEMIETET_TAEGLICH,
                Einsatz.von("miete", sprache.betrag(vertrag.miete()))));
        return List.copyOf(zeilen);
    }

    private static String tag(Sprache sprache, Mietvertrag vertrag) {
        return sprache.muster(MietSatz.TAG_MUSTER).format(vertrag.laeuftBis());
    }
}
