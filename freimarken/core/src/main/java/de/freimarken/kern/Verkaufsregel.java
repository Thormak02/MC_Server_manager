package de.freimarken.kern;

/**
 * Was der Bundesmarkt von einem Angebot wirklich nimmt - und was er dafuer zahlt.
 *
 * <p><b>Reine Rechnung, kein Zustand.</b> Anders als beim Verdienen gibt es hier keinen
 * Sammler: Ein Verkauf ist eine Tat des Spielers und wird sofort gebucht. Ein Befehl darf
 * das - er laeuft nicht in einem Blockereignis.
 *
 * <p><b>Es werden nur STUECKE genommen, die eine ganze Mark ergeben.</b> Das ist die
 * Entscheidung, die diese Klasse traegt, und sie fiel gegen zwei schlechtere:
 *
 * <ul>
 *   <li><b>Alles nehmen und abrunden</b> hiesse, dass sieben Stein zu 40 Pfennigen zwei
 *       Mark bringen und 80 Pfennige verschwinden. Der Spieler sieht nur, dass seine Ware
 *       weg ist, und rechnet nach.</li>
 *   <li><b>Einen Pfennigrest aufsammeln</b> wie beim Verdienen braeuchte Zustand je
 *       Spieler, der einen Neustart ueberlebt - also eine Spalte in der Datenbank fuer
 *       weniger als eine Mark.</li>
 * </ul>
 *
 * <p>Also: Von sieben Stein nimmt der Bund fuenf und zahlt zwei Mark, zwei bleiben im
 * Inventar. Nichts geht verloren, und die Rechnung laesst sich nachvollziehen.
 */
public final class Verkaufsregel {

    /**
     * Der hoechste Ankaufpreis je Stueck.
     *
     * <p>Nicht Vorsicht, sondern Rechnung: Ein Angebot kann die ganze Last eines
     * Inventars sein, und {@code anzahl * preis} muss in einen {@code long} passen, ohne
     * ueberzulaufen. Ein uebergelaufener Betrag ist NEGATIV, und ein negativer Betrag auf
     * dem Weg einer Einnahme ist eine Abbuchung. Hundertausend Mark je Stueck ist jenseits
     * alles Sinnvollen und noch weit von der Grenze entfernt.
     */
    public static final long HOECHSTER_PREIS = 10_000_000L;

    /**
     * Was ein Verkauf einbringt.
     *
     * @param stuecke wie viele Stueck der Bund nimmt - 0, wenn nichts zusammenkommt
     * @param mark    was er dafuer zahlt, in ganzen Mark
     */
    public record Verkauf(int stuecke, long mark) {

        /** Ob gar nichts zustande kam - dann bleibt das Inventar unberuehrt. */
        public boolean nichts() {
            return stuecke <= 0;
        }
    }

    private Verkaufsregel() {
    }

    /**
     * Rechnet ein Angebot ab.
     *
     * @param werte      die Marktzahlen samt Sortiment
     * @param material   was angeboten wird, kleingeschrieben
     * @param angeboten  wie viele Stueck der Spieler hat
     * @param heuteSchon was er heute schon aus Verkaeufen bekommen hat
     */
    public static Verkauf rechne(Marktwerte werte, String material, int angeboten,
                                 long heuteSchon) {
        if (werte == null) {
            throw new IllegalArgumentException("Ohne Marktwerte gibt es keinen Verkauf");
        }
        if (angeboten < 0) {
            throw new IllegalArgumentException("Es koennen nicht " + angeboten
                    + " Stueck angeboten werden");
        }
        long preis = werte.ankauf().pfennige(material);
        if (preis <= 0L || angeboten == 0) {
            // Der Bund kauft das nicht. Keine Ausnahme: Der Befehl sagt es dem Spieler,
            // und "nichts" ist eine Antwort.
            return new Verkauf(0, 0L);
        }
        if (preis > HOECHSTER_PREIS) {
            throw new IllegalArgumentException("Der Ankaufpreis fuer " + material
                    + " ist " + preis + " Pfennige und damit ueber " + HOECHSTER_PREIS
                    + " - dort laeuft die Rechnung ueber, und ein uebergelaufener Betrag"
                    + " ist negativ");
        }

        // Erst der Wunsch in ganzen Mark: Der Deckel haengt am Gebuchten, und gebucht
        // werden nur ganze Mark. Weniger als eine Mark ergibt hier 0, und das traegt
        // durch die ganze Rechnung - DREI Tore stehen davor, und das ist kein Versehen:
        //
        //   erlaubt <= 0  faengt den Deckel UND den Wunsch von null (min(0, frei) ist 0)
        //   mark <= 0     faengt, was nach der Rueckrechnung keine ganze Mark mehr ist
        //
        // Eine vierte Pruefung "wunsch <= 0" stand hier bis zum 7.11.2026 und ist
        // entfernt: Sie war von keiner Einspritzung zu treffen, weil der Deckelzweig
        // denselben Fall schon faengt. Ungeprueften Code zu behalten heisst, eine
        // Zusicherung zu behaupten, die niemand haelt.
        long wunsch = angeboten * preis / 100L;
        long erlaubt = Deckelregel.erlaubt(werte.tagesdeckel(), heuteSchon, wunsch);
        if (erlaubt <= 0L) {
            // Am Deckel - oder der ganze Stapel ist weniger als eine Mark wert. Nicht
            // nehmen und nicht zahlen: Sonst ist die Ware weg und der Spieler hat nichts.
            return new Verkauf(0, 0L);
        }

        // Und jetzt zurueck auf die STUECKE, die genau diese Mark ergeben. Nie mehr als
        // angeboten: Bei einem Preis, der nicht aufgeht, waere die Rueckrechnung sonst um
        // ein Stueck zu grosszuegig.
        long stuecke = Math.min(angeboten, erlaubt * 100L / preis);
        long mark = stuecke * preis / 100L;
        if (mark <= 0L) {
            return new Verkauf(0, 0L);
        }
        return new Verkauf((int) stuecke, mark);
    }
}
