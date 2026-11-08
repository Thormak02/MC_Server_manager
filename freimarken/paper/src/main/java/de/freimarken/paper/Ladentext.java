package de.freimarken.paper;

import de.freimarken.kern.Ladeneinheit;
import de.freimarken.kern.Ladenverzeichnis;
import java.util.ArrayList;
import java.util.List;
import net.kyori.adventure.text.Component;

/**
 * Die Zeilen von {@code /laden} - reine Rechnung, kein Bukkit, kein Senden.
 *
 * <p><b>Das ist DIE NAHT dieses Bereichs</b>, wie {@link Markttext} fuer den Bundesmarkt
 * und {@link Bonustext} fuer den Tagesbonus: Jeder Zweig von {@code LadenBefehle} holt
 * sich hier seine Zeilen und schickt sie nur hinaus. Dadurch ist jedes Wort, jede Farbe
 * und jede Reihenfolge ohne Server pruefbar - und {@code BildGleichTest} haengt genau an
 * diesen Methoden.
 *
 * <p>Kein Wort steht hier: Der Wortlaut kommt aus {@code sprache-vorlage.yml} ueber
 * {@link LadenSatz}.
 */
final class Ladentext {

    private Ladentext() {
    }

    /**
     * Die Liste der Ladeneinheiten - Ueberschrift und je Einheit eine Zeile.
     *
     * <p>Eine Zeile JE Einheit und nicht eine Zeile mit allen, anders als beim Sortiment
     * des Bundesmarktes. Der Grund ist die Zahl: Vierzig Materialien in einer Zeile sind
     * eine Aufzaehlung, zwanzig Staende mit Koordinate waeren eine Wand. Hier traegt jede
     * Zeile einen Ort, zu dem ein Spieler hingehen kann - dafuer braucht sie ihre eigene
     * Zeile.
     *
     * <p>Die REIHENFOLGE ist die der {@code config.yml}: Dort ordnet der Betreiber, und
     * das ist seine Entscheidung. Dieselbe Zusage wie beim Sortiment.
     *
     * <p><b>Die eigene Mietzeile steht ZULETZT und kommt von aussen herein</b> - gebaut
     * von {@link Miettext#standZeile}. Zwei Gruende: Sie braucht den Mietvertrag aus der
     * Datenbank, und dieser Leser soll weiter ohne Ablage pruefbar bleiben; und sie steht
     * am Ende, weil ein Spieler erst sucht, was es gibt, und dann liest, was ihm gehoert.
     *
     * <p>Sie steht AUCH unter der Leerzeile: Eine Halle ohne Staende ist der Normalfall ab
     * Werk, und wer dort schon einen Laden hat, soll ihn trotzdem sehen - das kann nur
     * geschehen, wenn ein Betreiber eine Einheit aus der Datei nimmt, und genau dann will
     * man es wissen.
     *
     * @param standzeile die eigene Mietzeile - nie {@code null}; wer keinen Laden hat,
     *                   bekommt von {@link Miettext#standZeile} den Hinweis, wie man
     *                   mietet
     */
    static List<Component> listeZeilen(Sprache sprache, Ladenverzeichnis verzeichnis,
                                       Component standzeile) {
        if (standzeile == null) {
            throw new IllegalArgumentException("Ohne Standzeile ist die Liste nicht"
                    + " vollstaendig - wer keinen Laden hat, bekommt den Hinweis");
        }
        List<Component> zeilen = new ArrayList<>();
        zeilen.add(sprache.eineZeile(LadenSatz.KOPF, Einsatz.keine()));
        if (verzeichnis.anzahl() == 0) {
            // Eine eigene Zeile und nicht eine leere Liste: Wer nur die Ueberschrift
            // sieht, haelt es fuer einen Fehler. Ab Werk ist das der Normalfall - die
            // Markthalle ist von Hand zu bauen.
            zeilen.add(sprache.eineZeile(LadenSatz.LEER, Einsatz.keine()));
            zeilen.add(standzeile);
            return List.copyOf(zeilen);
        }
        for (Ladeneinheit einheit : verzeichnis.alle()) {
            zeilen.add(sprache.eineZeile(LadenSatz.ZEILE, Einsatz
                    .von("name", einheit.name())
                    .mit("ort", einheit.truheAlsText())));
        }
        zeilen.add(standzeile);
        return List.copyOf(zeilen);
    }
}
