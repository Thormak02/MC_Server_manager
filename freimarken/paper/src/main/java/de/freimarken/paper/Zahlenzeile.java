package de.freimarken.paper;

import org.bukkit.configuration.ConfigurationSection;

/**
 * Drei ganze Zahlen aus einer Zeile wie {@code von: [10, -60, 10]} - die PRUEFUNG an
 * EINER Stelle.
 *
 * <p>Diese Klasse entsteht an der Stelle, die {@code Rundgangsliste.drei} selbst
 * angekuendigt hat: "Dieselbe Pruefung steht in {@code Ladenliste.drei} und
 * {@code Weltenliste.ecke}. Drei Fassungen sind zwei zu viel, und das ist bekannt -
 * zusammengelegt werden sie, wenn der VIERTE Leser kommt." Mit den Platzhalterbauten
 * Altfurts ist er gekommen.
 *
 * <p><b>Die Pruefung wandert, die WORTE bleiben beim Rufer</b>, und das ist die tragende
 * Entscheidung: Jeder Leser sagt dem Betreiber etwas anderes - eine Einheit, eine
 * Station, ein Tor, ein Bau wird uebersprungen, und das Beispiel im Satz nennt die Hoehen
 * SEINER Welt. Ein gemeinsamer Satz waere fuer jeden Leser der falsche, und die Klagen
 * sind in Proben festgehalten. Darum gibt diese Klasse einen {@link Mangel} zurueck und
 * keinen Text.
 *
 * <p><b>Am TYP geprueft und nicht am Nachkommarest</b>: YAML macht aus {@code 1.000} eine
 * 1.0, und die ist ganzzahlig - gemeint waren 1000. Ein {@code Double} oder {@code Float}
 * wird darum abgewiesen, auch wenn die Zahl darin rund ist.
 *
 * <p><b>Die Grenze von 30 Millionen ist keine Zierde</b>: Paper stuerzt bei einem
 * Teleport jenseits der Weltkoordinaten ab (PaperMC/Paper#13186), und ein vertippter
 * Quader reicht dafuer. Sie stand bis zum 23.11.2026 nur in {@code Weltenliste.ecke} -
 * also nur bei den Toren, obwohl eine Ladeneinheit und eine Station genauso ein Ziel
 * sind.
 */
final class Zahlenzeile {

    /** Jenseits davon stuerzt Paper beim Teleport ab - siehe Klassenkopf. */
    static final long JENSEITS_DER_WELT = 30_000_000L;

    /** Was an der Zeile nicht taugt. */
    enum Mangel {
        /** Nichts - die drei Zahlen stehen da. */
        KEINER,
        /** Der Schluessel fehlt ganz. */
        FEHLT,
        /** Keine Liste, oder nicht genau drei Werte. */
        NICHT_DREI,
        /** An einer Stelle steht keine ganze Zahl. */
        KEINE_GANZE_ZAHL,
        /** Eine Zahl liegt jenseits der Weltkoordinaten. */
        JENSEITS
    }

    /**
     * Was aus der Zeile zu holen war.
     *
     * @param zahlen  die drei Zahlen, oder {@code null} bei jedem Mangel
     * @param mangel  was nicht taugte
     * @param stelle  bei {@code KEINE_GANZE_ZAHL} und {@code JENSEITS} die Stelle 1..3,
     *                sonst 0
     * @param getippt was dort stand - fuer die Klage des Rufers
     */
    record Drei(int[] zahlen, Mangel mangel, int stelle, Object getippt) {

        /** Ob die drei Zahlen zu gebrauchen sind. */
        boolean taugt() {
            return mangel == Mangel.KEINER;
        }
    }

    private Zahlenzeile() {
    }

    /**
     * Liest {@code schluessel} aus {@code zeilen} als drei ganze Zahlen.
     *
     * <p>Wirft NIE. Wer liest, klagt - und klagen kann nur, wer den Betreiber kennt.
     */
    static Drei lies(ConfigurationSection zeilen, String schluessel) {
        if (zeilen == null || !zeilen.contains(schluessel, true)) {
            return new Drei(null, Mangel.FEHLT, 0, null);
        }
        java.util.List<?> gelesen = zeilen.getList(schluessel);
        if (gelesen == null || gelesen.size() != 3) {
            return new Drei(null, Mangel.NICHT_DREI, 0, zeilen.get(schluessel));
        }
        int[] zahlen = new int[3];
        for (int stelle = 0; stelle < 3; stelle++) {
            Object roh = gelesen.get(stelle);
            if (!(roh instanceof Number zahl) || roh instanceof Double
                    || roh instanceof Float
                    || zahl.doubleValue() != Math.floor(zahl.doubleValue())) {
                return new Drei(null, Mangel.KEINE_GANZE_ZAHL, stelle + 1, roh);
            }
            long wert = zahl.longValue();
            if (Math.abs(wert) > JENSEITS_DER_WELT) {
                return new Drei(null, Mangel.JENSEITS, stelle + 1, roh);
            }
            zahlen[stelle] = (int) wert;
        }
        return new Drei(zahlen, Mangel.KEINER, 0, null);
    }
}
