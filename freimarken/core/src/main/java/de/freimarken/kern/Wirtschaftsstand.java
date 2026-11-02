package de.freimarken.kern;

import java.util.ArrayList;
import java.util.List;

/**
 * Die einzige veraenderliche Zelle des Plugins - was die Wirtschaft gerade kostet.
 *
 * <p>Jede Stelle, die mit Geld rechnet, haelt diesen Stand als unveraenderliches Feld und
 * fragt ihn im Augenblick des Gebrauchs. Darin liegt der ganze Trick des Neuladens: Ein
 * neuer Wert erreicht jede Stelle, ohne dass ein einziges Objekt neu gebaut wird. Es wird
 * kein Zuhoerer angemeldet, kein Zeitgeber gestellt, kein Befehl neu eingehaengt und keine
 * Verbindung getauscht - also gibt es auch nichts abzumelden, und nichts kann doppelt
 * dastehen. Das ist der belegte Fehler bei einem Neuladen, das Objekte neu baut.
 *
 * <p>{@code volatile} auf einem unveraenderlichen {@code record}: Die Vervollstaendigung
 * von Befehlen und das Senden des Befehlsbaums laufen ausdruecklich nebenlaeufig, und ein
 * Spieler kann mitten im Tausch kaufen. So sieht jeder Leser entweder den alten oder den
 * neuen Satz - nie eine Mischung und nie einen halb gefuellten.
 */
public final class Wirtschaftsstand {

    /**
     * Was ein Neuladen bewirkt hat - und was es ausdruecklich nicht angefasst hat.
     *
     * <p>Beide Listen sind fertige Klartextzeilen. Der zweite Teil ist das Gegenmittel
     * gegen das haeufigste Fehlerbild eines Neuladens: Der Befehl meldet Erfolg, ein Teil
     * wirkt aber erst nach dem Neustart. Er erscheint deshalb auch dann, wenn sich nichts
     * geaendert hat.
     */
    public record Uebernahme(List<String> geaendert, List<String> nichtAngefasst) {
    }

    private volatile Wirtschaftswerte stand;

    public Wirtschaftsstand(Wirtschaftswerte anfang) {
        if (anfang == null) {
            throw new IllegalArgumentException("Ohne Anfangsstand gibt es nichts zu halten");
        }
        this.stand = anfang;
    }

    /** Was JETZT gilt. Beim Gebrauch zu fragen und nicht in ein Feld zu kopieren. */
    public Wirtschaftswerte werte() {
        return stand;
    }

    /**
     * Uebernimmt, was neu gelesen wurde - und laesst stehen, was ein Neuladen nicht
     * aendern darf.
     *
     * <p>Das Startguthaben bleibt mit Absicht aus dem Tausch heraus: Die Kassen geben es
     * bei jeder Abfrage als Vorgabe fuer ein noch nicht eroeffnetes Konto zurueck, und ein
     * neuer Wert wuerde damit rueckwirkend aendern, was ein Spieler mitbringt, der noch
     * nie gebucht hat. Es hier stehen zu lassen ist billiger als ein Bestandsschutz, und
     * die Begruendung steht an derselben Stelle wie die Entscheidung.
     *
     * @param ausDerDatei ein vollstaendig gelesener und gepruefter Satz
     * @return welche Zeilen sich geaendert haben und welche nicht angefasst wurden
     */
    public Uebernahme uebernimm(Wirtschaftswerte ausDerDatei) {
        if (ausDerDatei == null) {
            throw new IllegalArgumentException("Nichts gelesen heisst nichts uebernehmen");
        }
        Wirtschaftswerte alt = stand;
        Wirtschaftswerte neu = new Wirtschaftswerte(ausDerDatei.preise(),
                alt.startguthaben(), ausDerDatei.hoechstzahl(), ausDerDatei.gruppenFelder(),
                ausDerDatei.rangWerbung());

        List<String> geaendert = new ArrayList<>();
        nenneZahl(geaendert, Wirtschaftswerte.PFAD_GRUNDPREIS,
                alt.preise().grundpreis(), neu.preise().grundpreis());
        nenneZahl(geaendert, Wirtschaftswerte.PFAD_ZUSAMMENLEGUNG,
                alt.preise().zusammenlegungJeFeld(), neu.preise().zusammenlegungJeFeld());
        nenneZahl(geaendert, Wirtschaftswerte.PFAD_HOECHSTZAHL,
                alt.hoechstzahl(), neu.hoechstzahl());
        nenneZahl(geaendert, Wirtschaftswerte.PFAD_GRUPPEN_FELDER,
                alt.gruppenFelder(), neu.gruppenFelder());
        if (!alt.rangWerbung().equals(neu.rangWerbung())) {
            geaendert.add(Wirtschaftswerte.PFAD_RANG_WERBUNG + ": \"" + alt.rangWerbung()
                    + "\" -> \"" + neu.rangWerbung() + "\"");
        }

        List<String> nichtAngefasst = nichtAngefasst(ausDerDatei);
        stand = neu;
        return new Uebernahme(geaendert, nichtAngefasst);
    }

    /**
     * Was von diesem Stand ein Neuladen nicht anfasst - auch wenn gar nichts uebernommen
     * wurde.
     *
     * <p>Oeffentlich und ohne Nebenwirkung, weil der Block in JEDER Antwort stehen muss:
     * auch dann, wenn eine Klage das Uebernehmen verhindert hat. Sonst erfaehrt gerade
     * der Betreiber, bei dem etwas schiefging, nicht, dass das Startguthaben ohnehin
     * nicht neuladbar ist - und sucht den Fehler an der falschen Zeile.
     *
     * @param ausDerDatei was gelesen wurde, oder {@code null}, wenn nichts gilt - dann
     *                    nennt die Zeile nur, was gerade gilt
     */
    public List<String> nichtAngefasst(Wirtschaftswerte ausDerDatei) {
        Wirtschaftswerte jetzt = stand;
        List<String> zeilen = new ArrayList<>();
        if (ausDerDatei == null || ausDerDatei.startguthaben() == jetzt.startguthaben()) {
            zeilen.add(Wirtschaftswerte.PFAD_STARTGUTHABEN + ": " + jetzt.startguthaben()
                    + " - wird nur beim Start gelesen.");
        } else {
            zeilen.add(Wirtschaftswerte.PFAD_STARTGUTHABEN + ": es gilt weiter "
                    + jetzt.startguthaben() + ", in der Datei stehen "
                    + ausDerDatei.startguthaben() + " - erst ein Neustart uebernimmt das."
                    + " Ein neuer Wert im Betrieb aenderte rueckwirkend das Guthaben jedes"
                    + " Spielers, der noch nie gebucht hat.");
        }
        return zeilen;
    }

    private static void nenneZahl(List<String> hinein, String pfad, long alt, long neu) {
        if (alt != neu) {
            hinein.add(pfad + ": " + alt + " -> " + neu);
        }
    }
}
