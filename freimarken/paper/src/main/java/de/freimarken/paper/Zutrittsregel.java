package de.freimarken.paper;

import de.freimarken.kern.Rangwerte;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Wer in welche Welt darf - und wenn nicht, woran es liegt.
 *
 * <p><b>Eine Rechtefrage und kein Blick in die Datenbank</b> [25.11.2026]: Der erreichte
 * Rang steht in der Datenbank und wird beim Beitritt EINMAL gelesen
 * ({@link Rangwache#vergibNeu}); daraus macht {@link Rechtebuendel} eine Rechtemenge.
 * Diese Regel fragt nur noch die Menge. Das hat drei gemessene Folgen: Ein Teleport
 * kostet keinen Lesevorgang, eine Ausnahme fuer EINEN Spieler ist eine Rechtezeile und
 * keine Aenderung an der Rangleiter, und die Regel laeuft in einer Probe ohne Server
 * durch - {@link Rechtefrage} ist genau dafuer da.
 *
 * <p><b>Warum {@code freimarken.stufe.} und nicht {@code freimarken.rang.}</b>: Jenes ist
 * schon das Recht des Befehls {@code /rang} und in der {@code plugin.yml} angemeldet. Eine
 * Vorsilbe darueber liesse sich von {@code GrenzeTest} nicht mehr von ihm trennen - die
 * Tafel laesst alles durch, was mit einem angemeldeten Recht plus Punkt beginnt. Zwei
 * Dinge mit demselben Anfang sind ausserdem fuer den Betreiber zwei Dinge, die er
 * verwechselt.
 *
 * <p><b>Eine Welt, die nicht in der Liste steht, hat keine Schranke.</b> Das ist Absicht
 * und keine Luecke: Die Schranke ist eine Eigenschaft des Eintrags in der
 * {@code config.yml}. Eine fremde Welt - von einem anderen Plugin, aus einer Sicherung -
 * gehoert nicht diesem Plugin, und es waere falsch, sie im Namen von Raengen zu sperren,
 * die es dort nicht gibt.
 */
final class Zutrittsregel {

    /**
     * Die Vorsilbe des Rechts, das eine Premiumwelt verlangt.
     *
     * <p>Als EIN Literal und nicht zusammengesetzt: {@code GrenzeTest} 31al sucht
     * {@code freimarken.<etwas>} in Zeichenketten und haelt gegen die angemeldeten
     * Rechte. Aus Teilen gebaut findet die Tafel die Vorsilbe nicht, und der Schutz ist
     * weg, ohne dass es auffaellt. Sie steht darum auch in {@code paper} und nicht im
     * Kern - {@code Quellen.alle()} liest nur dieses Modul.
     */
    static final String VORSILBE_WELT = "freimarken.welt.";

    /** Die Vorsilbe des Rechts, das eine erreichte Rangstufe nachweist. */
    static final String VORSILBE_STUFE = "freimarken.stufe.";

    private Zutrittsregel() {
    }

    /**
     * Woran ein Zutritt scheitert - ohne ein einziges Wort.
     *
     * <p>Dieselbe Trennung wie bei {@link Reise.Hindernis}: Die Regel entscheidet, WAS im
     * Weg ist, und genau eine Stelle sagt es ({@link Reise#zutrittZeilen}). Eine
     * Aufzaehlung bringt denselben Zeugen mit - eine Probe laeuft ueber {@code values()}
     * und faengt einen weggefallenen Zweig.
     */
    enum Grund {
        /** Die Welt steht nur Mitgliedern offen. */
        NUR_FUER_MITGLIEDER,
        /** Die Welt verlangt eine Rangstufe, die dieser Spieler nicht hat. */
        RANG_ZU_NIEDRIG
    }

    /**
     * Die Absage samt dem Wort, das in ihren Satz gehoert.
     *
     * <p><b>Warum ein Satz mit Platzhalter und nicht zwei feste Saetze:</b> Die Stufen
     * sind frei benannte Schluessel der {@code config.yml}
     * ({@code raenge.leiter.<name>}). Ein Satz je Stufe waere eine Zeile, die der
     * Betreiber beim Umbenennen vergisst - und dann liest der Spieler den Pfad.
     *
     * @param grund woran es liegt
     * @param rang  die verlangte Stufe, oder {@code ""} - gefuellt nur bei
     *              {@link Grund#RANG_ZU_NIEDRIG}
     */
    record Absage(Grund grund, String rang) {

        Absage {
            if (grund == null) {
                throw new IllegalArgumentException("Eine Absage ohne Grund waere eine"
                        + " leere Zeile beim Spieler.");
            }
            rang = rang == null ? "" : rang;
        }
    }

    /** Das Recht, das eine Premiumwelt verlangt - an EINER Stelle zusammengesetzt. */
    static String weltrecht(String welt) {
        return VORSILBE_WELT + welt;
    }

    /** Das Recht, das eine erreichte Rangstufe nachweist. */
    static String stufenrecht(String stufe) {
        return VORSILBE_STUFE + stufe;
    }

    /**
     * Darf dieser Leser in diese Welt?
     *
     * <p>Beide Bedingungen gelten UND: Eine Premiumwelt mit {@code ab-rang} verlangt
     * beides. Gefragt wird zuerst die Mitgliedschaft, weil sie die groebere Absage ist -
     * wer gar nicht hineindarf, braucht nicht zu erfahren, welchen Rang er braeuchte.
     *
     * @param welt   die Welt aus der Liste, oder {@code null} fuer eine fremde Welt
     * @param rechte {@code wer::hasPermission}, oder in einer Probe eine Menge
     */
    static Optional<Absage> pruefe(Weltangabe welt, Rechtefrage rechte) {
        if (welt == null) {
            return Optional.empty();
        }
        if (welt.premium() && !rechte.hat(weltrecht(welt.name()))) {
            return Optional.of(new Absage(Grund.NUR_FUER_MITGLIEDER, ""));
        }
        if (welt.hatRangschranke() && !rechte.hat(stufenrecht(welt.abRang()))) {
            return Optional.of(new Absage(Grund.RANG_ZU_NIEDRIG, welt.abRang()));
        }
        return Optional.empty();
    }

    /**
     * Was beim Start ueber die Schranken zu sagen ist - eine Zeile oder keine.
     *
     * <p><b>Warum das ins Startprotokoll gehoert:</b> Eine gesperrte Welt sieht im
     * Spiel genauso aus wie eine kaputte. Wer nach einem halben Jahr liest
     * "warum komme ich nicht in den Nether", soll die Antwort im Protokoll finden
     * und nicht in 4000 Zeilen {@code config.yml} suchen. Dieselbe Begruendung wie
     * bei {@link Rangliste#rechenprobe}: Was eine Einstellung BEWIRKT, steht beim
     * Start da, nicht nur, dass sie gelesen wurde.
     *
     * <p>Leer, wenn keine Welt eine Schranke fuehrt - eine Zeile "keine Schranken"
     * bei jedem Start waere eine Zeile, die niemand mehr liest.
     */
    static List<String> bericht(List<Weltangabe> welten) {
        if (welten == null) {
            return List.of();
        }
        List<String> teile = new ArrayList<>();
        for (Weltangabe welt : welten) {
            if (welt == null) {
                continue;
            }
            if (welt.premium() && welt.hatRangschranke()) {
                teile.add(welt.name() + " nur mit " + weltrecht(welt.name())
                        + " UND ab Stufe " + welt.abRang());
            } else if (welt.premium()) {
                teile.add(welt.name() + " nur mit " + weltrecht(welt.name()));
            } else if (welt.hatRangschranke()) {
                teile.add(welt.name() + " ab Stufe " + welt.abRang() + " ("
                        + stufenrecht(welt.abRang()) + ")");
            }
        }
        if (teile.isEmpty()) {
            return List.of();
        }
        return List.of("Zugang je Welt: " + String.join("; ", teile)
                + ". Jede andere Welt steht jedem offen. Die Rechte vergibt die"
                + " Rechteverwaltung des Servers; das Stufenrecht gibt das Plugin"
                + " beim Beitritt fuer die erreichte Stufe und jede darunter.");
    }

    /**
     * Welche {@code ab-rang}-Zeile auf eine Stufe zeigt, die es nicht gibt.
     *
     * <p><b>Diese Klage ist der Grund, warum es sie gibt.</b> Eine erfundene Stufe wirkt
     * stumm und in die gefaehrliche Richtung: {@code Rangwerte.nummer} gibt -1,
     * {@code buendel} bleibt leer, das Recht {@code freimarken.stufe.<erfunden>} vergibt
     * niemand - und damit ist die Welt fuer JEDEN gesperrt. Im Spiel sieht das genau aus
     * wie eine kaputte Welt.
     *
     * <p>Hier geprueft und nicht beim Leser der Welten: Dort liegt die Rangleiter nicht
     * vor. Dieselbe Aufteilung wie bei {@link Rangliste#unbekannteRechte} - der Leser
     * nimmt das Wort, und die Pruefung gegen eine ANDERE Quelle steht da, wo beide
     * Quellen beisammen sind.
     */
    static List<String> unbekannteStufen(List<Weltangabe> welten, Rangwerte raenge) {
        List<String> klagen = new ArrayList<>();
        if (welten == null) {
            return klagen;
        }
        for (Weltangabe welt : welten) {
            if (welt == null || !welt.hatRangschranke()) {
                continue;
            }
            if (raenge == null || !raenge.greift()) {
                klagen.add("welten." + welt.name() + ".ab-rang ist " + welt.abRang()
                        + ", aber dieser Server fuehrt keine Rangleiter (raenge.an)."
                        + " Niemand kann " + stufenrecht(welt.abRang()) + " bekommen -"
                        + " diese Welt bleibt fuer JEDEN gesperrt.");
                continue;
            }
            if (!raenge.schluessel().contains(welt.abRang())) {
                klagen.add("welten." + welt.name() + ".ab-rang ist " + welt.abRang()
                        + ", und diese Stufe gibt es in raenge.leiter nicht. Dort stehen: "
                        + String.join(", ", raenge.schluessel()) + ". Niemand bekommt"
                        + " damit " + stufenrecht(welt.abRang()) + " - diese Welt bleibt"
                        + " fuer JEDEN gesperrt.");
            }
        }
        return klagen;
    }
}
