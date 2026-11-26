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
        return kurzeWelt(ort.welt()) + " " + ort.x() + "/" + ort.y() + "/" + ort.z();
    }

    /**
     * Was die Vorschau zeigt - oder warum es keine gibt.
     *
     * <p><b>Die ganze Verzweigung steht hier</b>, und das ist der Zweck dieser
     * Methode: Welche Zeilen ein Moderator liest, BEVOR er in eine fertige Welt
     * eingreift, ist die Entscheidung dieses Teils - und sie muss ohne laufenden
     * Server pruefbar sein. Im Befehl stuende sie hinter einem Brigadier-Zweig, den
     * keine Probe erreicht.
     *
     * <p>Drei Faelle, und sie sind nach Gewissheit geordnet: zuerst die ABSAGE (zu
     * viele - dann wird nichts getan), dann das leere Ergebnis (haeufig und kein
     * Fehler), dann die Vorschau. So liest der Moderator nie Zahlen, auf die er sich
     * verlassen koennte, wenn der Lauf gar nicht laufen wird.
     *
     * @param mitte   der Mittelpunkt des Kastens
     * @param radius  wie weit, in Bloecken
     * @param minuten wie weit zurueck
     * @param befehl  die fertige Zeile zum Bestaetigen, aus {@link #bestaetigung}
     */
    static List<Component> schauZeilen(Sprache sprache, Blockort mitte, int radius,
                                       int minuten, Rueckrollung.Schau schau,
                                       String befehl) {
        if (schau.zuViele()) {
            return sprache.bau(BlockSatz.RUECKWEG_ZU_VIELE, 1L,
                    Einsatz.von("zeilen", schau.gelesen())
                            .mit("grenze", schau.gelesen() - 1));
        }
        if (schau.leer()) {
            return sprache.bau(BlockSatz.RUECKWEG_LEER, 1L,
                    Einsatz.von("ort", ortText(mitte)).mit("minuten", minuten));
        }
        List<Component> gebaut = new java.util.ArrayList<>(sprache.bau(
                BlockSatz.RUECKWEG_KOPF, 1L,
                Einsatz.von("ort", ortText(mitte)).mit("radius", radius)
                        .mit("minuten", minuten)));
        gebaut.addAll(sprache.bau(BlockSatz.RUECKWEG_ZAHLEN, 1L,
                Einsatz.von("zeilen", schau.gelesen())
                        .mit("wegnehmen", schau.wegnehmen())
                        .mit("herstellen", schau.herstellen())));
        // Nur wenn es welche gibt. Eine Zeile "Ohne Vorzustand: 0" waere eine
        // Warnung ohne Gegenstand, und wer sie oft liest, liest sie bald nicht mehr.
        if (schau.ohneVorzustand() > 0) {
            gebaut.addAll(sprache.bau(BlockSatz.RUECKWEG_ROH, 1L,
                    Einsatz.von("anzahl", schau.ohneVorzustand())));
        }
        gebaut.addAll(sprache.bau(BlockSatz.RUECKWEG_BEFEHL, 1L,
                Einsatz.von("befehl", befehl)));
        return List.copyOf(gebaut);
    }

    /**
     * Was der Lauf getan hat - und was er nicht konnte.
     *
     * <p>Die misslungenen Bloecke bekommen eine EIGENE Zeile, und nur wenn es welche
     * gab. Dann ist sie die wichtigste des Ergebnisses: Dort steht noch, was stehen
     * geblieben ist. In der Erfolgszeile mitgezaehlt waere sie eine Zahl unter
     * anderen.
     */
    static List<Component> laufZeilen(Sprache sprache, Rueckrollung.Lauf lauf) {
        List<Component> gebaut = new java.util.ArrayList<>(sprache.bau(
                BlockSatz.RUECKWEG_FERTIG, 1L,
                Einsatz.von("gesetzt", lauf.gesetzt())
                        .mit("zeilen", lauf.gemerkt())));
        if (lauf.misslungen() > 0) {
            gebaut.addAll(sprache.bau(BlockSatz.RUECKWEG_MISSLUNGEN, 1L,
                    Einsatz.von("anzahl", lauf.misslungen())));
        }
        return List.copyOf(gebaut);
    }

    /**
     * Die fertige Zeile zum Bestaetigen - mit Welt und Koordinaten.
     *
     * <p><b>Der Ort steht darin, und das ist die Sicherung</b>, nicht Bequemlichkeit:
     * Ohne ihn rollte die Bestaetigung den Kasten um die Stelle zurueck, an der der
     * Moderator JETZT steht - ein Schritt waehrend des Lesens traefe den falschen
     * Kasten, und zurueck gibt es dann nichts. Dieselbe Bauart und derselbe Grund wie
     * bei {@code /freimarken parzelle aufgeben bestaetigen \u003Cx\u003E \u003Cz\u003E}.
     *
     * <p>Die WELT steht als kurzer Name darin, ohne Namensraum - so, wie sie der
     * Betreiber in seiner config.yml geschrieben hat. Der Befehl nimmt beide Formen.
     *
     * <p><b>Diese Zeile und der Befehlsbaum muessen zusammenpassen</b>, und das haelt
     * keine Tafel, sondern die Probe {@code bestaetigungszeilePasstZumBaum}: Sie
     * tippt genau diese Zeile in den gebauten Baum. Ein umbenanntes Literal macht sie
     * rot - ohne sie waere eine Vorschau denkbar, die eine Zeile vorgibt, die es
     * nicht mehr gibt.
     */
    static String bestaetigung(Blockort mitte, int radius, int minuten) {
        return "/freimarken blockbuch " + RueckrollBefehle.ZURUECK + " " + radius
                + " " + minuten + " " + RueckrollBefehle.BEI + " "
                + kurzeWelt(mitte.welt()) + " " + mitte.x() + " " + mitte.y()
                + " " + mitte.z() + " " + RueckrollBefehle.BESTAETIGEN;
    }

    /**
     * Der Weltname ohne Namensraum.
     *
     * <p>Herausgezogen aus {@link #ortText}, weil die Bestaetigungszeile ihn ohne die
     * Koordinaten braucht - zwei Stellen, die denselben Doppelpunkt suchen, suchen
     * ihn eines Tages verschieden.
     */
    static String kurzeWelt(String weltschluessel) {
        int doppelpunkt = weltschluessel.indexOf(':');
        return doppelpunkt < 0 ? weltschluessel
                : weltschluessel.substring(doppelpunkt + 1);
    }

    /**
     * Meint dieser getippte Name diese Welt?
     *
     * <p><b>Herausgezogen, weil sie sonst unpruefbar waere</b> [26.11.2026]: Die
     * Weltsuche steht in {@code Rueckrollung.ueberBukkit}, also hinter einem
     * Aufruf, den keine Probe ohne laufenden Server erreicht - eine Einspritzung
     * (Fall V) hat gezeigt, dass dort ein Griff gegen KEINE Probe laeuft. Was
     * reine Textarbeit ist, gehoert darum hierher.
     *
     * <p>BEIDE Formen, und das ist kein Komfort: Der Befehl nimmt den kurzen
     * Namen (Brigadiers {@code word()} kennt keinen Doppelpunkt), die Vorschau am
     * eigenen Standort gibt dagegen {@code World.key().asString()} herein - also
     * die lange. Eine Suche, die nur eine Form kennt, laesst genau einen der
     * beiden Wege nie eine Welt finden.
     *
     * <p>Gross und Klein wird nicht unterschieden: Der Name kommt aus einer
     * Befehlszeile, und ein Betreiber, der {@code Altfurt} tippt, hat sich nicht
     * vertippt.
     *
     * @param weltschluessel wie {@code World.key().asString()} ihn liefert
     * @param getippt        was in der Befehlszeile stand; {@code null} und leer
     *                       meinen keine Welt
     */
    static boolean istDieseWelt(String weltschluessel, String getippt) {
        if (weltschluessel == null || getippt == null || getippt.isBlank()) {
            return false;
        }
        String sauber = getippt.trim();
        return weltschluessel.equalsIgnoreCase(sauber)
                || kurzeWelt(weltschluessel).equalsIgnoreCase(sauber);
    }
}
