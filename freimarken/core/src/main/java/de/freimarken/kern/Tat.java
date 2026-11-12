package de.freimarken.kern;

/**
 * WAS jemand an einer Stelle tut - und damit auch, welches RECHT er dafuer braucht.
 *
 * <p><b>Eine Aufzaehlung fuer beides</b> [12.11.2026], und das ist der ganze Entwurf von
 * Teil D: Die Schutzwache fragt "was geschieht hier", der Stadtbesitzer einer Freistadt
 * stellt ein "was duerfen meine Stadtbuerger". Das ist dieselbe Dreiteilung, und zwei
 * Aufzaehlungen mit einer Abbildung dazwischen waeren zwei Wahrheiten darueber, was ein
 * Behaelter ist. Eine vierte Tat ist damit ohne Zutun auch ein viertes Recht.
 *
 * <p><b>Im Kern und nicht bei der Wache</b>, obwohl die Wache sie zuerst gebraucht hat:
 * Die Rechte einer Stadt stehen in der Datenbank, und der Kern ist die Schicht, die
 * davon weiss. Umgekehrt koennte das Paperteil seine eigene Aufzaehlung nicht speichern.
 *
 * <p><b>Drei und nicht fuenfzehn</b>: Die Schutzwache hat fuenfzehn Bukkit-Zugaenge,
 * aber nur drei Arten von Eingriff, die ein Grundbesitzer verschieden beantworten
 * koennte. Die Unterscheidung steckte schon in {@code Schutzwache.istHeikel} und war
 * dort nur nicht benannt: {@code Openable} und {@code Powerable} sind Tueren, Klinken,
 * Knoepfe und Druckplatten, ein {@code InventoryHolder} ist ein Behaelter, alles andere
 * veraendert den Block selbst. Welcher Block welche Tat ist, entscheidet
 * {@code Schutzwache.tatAn} - und {@code TatTest} misst es.
 *
 * <p><b>Keine Rangfolge</b>: Die drei stehen nebeneinander und nicht aufeinander. Wer
 * glaubt, Bauen schliesse Benutzen ein, baut eine Ordnung in eine Aufzaehlung, die keine
 * hat - und ein Stadtbesitzer, der eine Gemeinschaftstruhe freigibt und das Bauen sperrt,
 * hat seine Gruende.
 *
 * <p><b>Der Schluessel gehoert dazu</b>: Er steht in der Datenbank und in der
 * {@code config.yml}, und er ist darum kleingeschrieben und fest. Wer den Namen der
 * Konstante aendert, aendert nicht den Schluessel - sonst waeren alle eingetragenen
 * Rechte eines Servers nach einem Umbenennen verloren.
 */
public enum Tat {

    /** Der Block selbst wird veraendert - gesetzt, abgebaut, angezuendet, begossen. */
    BAUEN("bauen"),

    /** Tuer, Klinke, Knopf, Hebel, Druckplatte - der Block bleibt, was er ist. */
    BENUTZEN("benutzen"),

    /** Ein Behaelter wird geoeffnet - Truhe, Ofen, Trichter, Kistenlore. */
    BEHAELTER("behaelter");

    private final String schluessel;

    Tat(String schluessel) {
        this.schluessel = schluessel;
    }

    /** Der Schluessel in Datenbank und Konfiguration - niemals der Name der Konstante. */
    public String schluessel() {
        return schluessel;
    }

    /**
     * Die Tat zu diesem Schluessel.
     *
     * <p>Wirft bei einem unbekannten, statt {@code null} oder eine Vorgabe zu liefern:
     * Ein Schluessel, den niemand kennt, steht entweder falsch in der
     * {@code config.yml} - dann soll es der Betreiber lesen - oder er kommt aus einer
     * Datenbank, die ein neueres Freimarken geschrieben hat. Eine stille Vorgabe waere
     * in beiden Faellen ein Recht, das niemand gegeben hat.
     */
    public static Tat ausSchluessel(String schluessel) {
        for (Tat tat : values()) {
            if (tat.schluessel.equals(schluessel)) {
                return tat;
            }
        }
        throw new IllegalArgumentException("Keine Tat heisst " + schluessel
                + ". Moeglich sind: bauen, benutzen, behaelter");
    }
}
