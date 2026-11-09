package de.freimarken.paper;

import de.freimarken.kern.Blockort;
import de.freimarken.kern.Blockzeile;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.kyori.adventure.text.Component;

/**
 * Die Zeilen des Blockbuchs - reine Rechnung, kein Bukkit, kein Senden.
 *
 * <p>Dieselbe Bauart wie {@link Ladentext} und {@link Verlaufszeile}: nur statische
 * Methoden, die {@link Component} bauen. Das ist die Naht, an der {@code BildGleichTest}
 * haengt - und der Grund, warum diese Zeilen ohne laufenden Server pruefbar sind.
 *
 * <p><b>Die Spielernamen kommen ueber eine Naht herein.</b> {@link Spielername#von} fragt
 * {@code Bukkit.getOfflinePlayer}, und das braucht einen Server. Bei einer EINZELNEN Zeile
 * loest das Projekt es anders - {@code Shoptext} bekommt den Namen fertig als Zeichenkette
 * -, aber hier sind es zwanzig Namen aus zwanzig Zeilen, und die von Hand
 * vorzuaufloesen hiesse, die Schleife zweimal zu schreiben. Darum ein
 * {@link Namensgeber}: Der Befehl gibt {@code Spielername::von}, die Probe gibt eine
 * Karte.
 *
 * <p><b>Der Ort wird je Zeile nur dort gezeigt, wo er sich unterscheidet.</b> Bei der
 * Abfrage an einem Block steht er in der Ueberschrift - zwanzig Zeilen mit derselben
 * Koordinate waeren zwanzigmal dasselbe und verdraengten die Angaben, auf die es ankommt.
 * Bei der Abfrage nach einem Spieler ist er umgekehrt die wichtigste Angabe: "wo war
 * der" ist die ganze Frage.
 */
final class Blocktext {

    /**
     * Woher ein Spielername kommt.
     *
     * <p>Ein Fingerbreit Naht und kein Rahmenwerk, aus demselben Grund wie
     * {@link Verdienstwache.Taktquelle}: Ohne sie braeuchte jede Probe dieser Zeilen
     * einen laufenden Server, und die Zeilen des Blockbuchs waeren die einzigen
     * ungepruefen Spielertexte des Projekts.
     */
    interface Namensgeber {

        /**
         * Der Name zu dieser Kennung - nie {@code null} und nie leer.
         *
         * <p>Was ein unbekannter Spieler ergibt, entscheidet der Geber;
         * {@link Spielername#von} nimmt die ersten acht Zeichen der Kennung.
         */
        String von(UUID wer);
    }

    private Blocktext() {
    }

    /**
     * Was an diesem Block geschehen ist.
     *
     * @param zeilen die Zeilen, neueste zuerst - wie die Ablage sie liefert
     * @param zone   die eingestellte Zone; die Zeitpunkte liegen in UTC
     */
    static List<Component> amOrtZeilen(Sprache sprache, Blockort ort,
                                       List<Blockzeile> zeilen, ZoneId zone,
                                       Namensgeber namen) {
        List<Component> gebaut = new ArrayList<>();
        if (zeilen.isEmpty()) {
            // EINE Zeile und nicht eine leere Liste: Wer nur die Ueberschrift sieht,
            // haelt es fuer einen Fehler.
            gebaut.add(sprache.eineZeile(BlockSatz.LEER_ORT,
                    Einsatz.von("ort", ortText(ort))));
            return List.copyOf(gebaut);
        }
        gebaut.add(sprache.eineZeile(BlockSatz.KOPF_ORT,
                Einsatz.von("ort", ortText(ort)).mit("anzahl", zeilen.size())));
        for (Blockzeile zeile : zeilen) {
            gebaut.add(sprache.eineZeile(
                    zeile.gelungen() ? BlockSatz.ZEILE : BlockSatz.ZEILE_ABGEWIESEN,
                    Einsatz.von("zeit", zeit(sprache, zeile, zone))
                            .mit("spieler", namen.von(zeile.spieler()))
                            .mit("tat", sprache.wort(
                                    BlockSatz.tatwort(zeile.tat()), Einsatz.keine()))
                            .mit("block", zeile.block())));
        }
        return List.copyOf(gebaut);
    }

    /**
     * Was dieser Spieler getan hat.
     *
     * @param spielername der Name, schon aufgeloest - er steht in der Ueberschrift, also
     *                    genau einmal, und braucht darum keine Naht
     */
    static List<Component> vonSpielerZeilen(Sprache sprache, String spielername,
                                            List<Blockzeile> zeilen, ZoneId zone) {
        List<Component> gebaut = new ArrayList<>();
        if (zeilen.isEmpty()) {
            gebaut.add(sprache.eineZeile(BlockSatz.LEER_SPIELER,
                    Einsatz.von("spieler", spielername)));
            return List.copyOf(gebaut);
        }
        gebaut.add(sprache.eineZeile(BlockSatz.KOPF_SPIELER,
                Einsatz.von("spieler", spielername).mit("anzahl", zeilen.size())));
        for (Blockzeile zeile : zeilen) {
            gebaut.add(sprache.eineZeile(
                    zeile.gelungen() ? BlockSatz.ZEILE_MIT_ORT
                            : BlockSatz.ZEILE_MIT_ORT_ABGEWIESEN,
                    Einsatz.von("zeit", zeit(sprache, zeile, zone))
                            .mit("ort", ortText(zeile.ort()))
                            .mit("tat", sprache.wort(
                                    BlockSatz.tatwort(zeile.tat()), Einsatz.keine()))
                            .mit("block", zeile.block())));
        }
        return List.copyOf(gebaut);
    }

    /**
     * Der Zeitpunkt in der eingestellten Zone.
     *
     * <p>{@code atZone} ist Pflicht: Die Ablage schreibt UTC, und ohne die Umrechnung
     * stuende im Chat eine Zeit, die der Spieler nicht wiedererkennt - im Sommer zwei
     * Stunden daneben. Dasselbe tut {@link Verlaufszeile} fuer die Buchungen.
     */
    private static String zeit(Sprache sprache, Blockzeile zeile, ZoneId zone) {
        return sprache.muster(BlockSatz.ZEIT_MUSTER)
                .format(zeile.zeitpunkt().atZone(zone));
    }

    /**
     * Der Ort in Kurzform: {@code altfurt 62/-60/8}.
     *
     * <p>Der Namensraum wird abgeschnitten, und das ist mehr als Platzsparen: Im Spiel
     * heisst die Welt {@code altfurt}, so steht sie in der {@code config.yml}, und so
     * tippt sie der Betreiber. Das {@code freimarken:} davor ist eine Angabe der
     * Laufzeit und hat in einer Chatzeile nichts zu suchen - es waere bei jeder Welt
     * dasselbe Wort.
     *
     * <p>Paketweit sichtbar, weil der Befehl denselben Text fuer seine Absagen braucht.
     * Zwei Abschriften liefen auseinander, und dann nennte die Ueberschrift einen anderen
     * Ort als die Absage darunter.
     */
    static String ortText(Blockort ort) {
        String welt = ort.welt();
        int doppelpunkt = welt.indexOf(':');
        String kurz = doppelpunkt < 0 ? welt : welt.substring(doppelpunkt + 1);
        return kurz + " " + ort.x() + "/" + ort.y() + "/" + ort.z();
    }
}
