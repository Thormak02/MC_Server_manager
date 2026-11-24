package de.freimarken.kern;

/**
 * Der Grundriss einer gespeicherten Bauvorlage - Name und Masse, ohne Bloecke.
 *
 * <p><b>Wofuer</b> [24.11.2026]: Die Stadt soll GEGEBENE Haeuser einpassen statt eigene
 * Masse zu erfinden. Der Betreiber nennt in der {@code config.yml} die Namen seiner
 * Vorlagen; das Plugin fragt die Strukturverwaltung nach deren Masse und gibt sie als
 * diese Liste in den {@link Stadtplan}. Der legt dann Parzellen, die GENAU passen - und
 * setzt je Parzelle die Datei statt der Rechnung.
 *
 * <p><b>Ohne Minecraft</b>, wie alles im Kern: Hier stehen drei Zahlen und ein Name. Wo
 * die Datei liegt und wie man sie laedt, weiss die Paper-Seite.
 *
 * <p><b>Die Masse sind die UNGEDREHTEN</b>, so wie die Strukturverwaltung sie nennt:
 * {@code breite} laeuft in x, {@code hoehe} in y, {@code tiefe} in z. Eine Vorlage steht
 * in der NORMLAGE - die Tuer zeigt nach Norden, die Fassade laeuft entlang x. Gedreht
 * wird beim Setzen, und zwar von {@link Bauplan#dateilage}: EINE Stelle entscheidet
 * Dateiname und Drehung zusammen.
 *
 * @param datei  der Name der Strukturdatei, ohne Endung
 * @param breite die Ausdehnung in x - die Fassade zur Gasse
 * @param hoehe  die Ausdehnung in y
 * @param tiefe  die Ausdehnung in z - in das Band hinein
 */
public record Vorlage(String datei, int breite, int hoehe, int tiefe) {

    public Vorlage {
        if (datei == null || datei.isBlank()) {
            throw new IllegalArgumentException("Eine Vorlage ohne Namen gibt es nicht");
        }
        if (!datei.matches(Platzhalterbau.MUSTER)) {
            throw new IllegalArgumentException("Der Vorlagenname \"" + datei + "\" passt"
                    + " nicht zu " + Platzhalterbau.MUSTER + " - er wird ein Dateiname");
        }
        if (datei.length() > Platzhalterbau.HOECHSTE_SCHLUESSELLAENGE) {
            throw new IllegalArgumentException("Der Vorlagenname \"" + datei + "\" ist"
                    + " laenger als " + Platzhalterbau.HOECHSTE_SCHLUESSELLAENGE
                    + " Zeichen");
        }
        if (breite < Platzhalterbau.KLEINSTE_KANTE || hoehe < Platzhalterbau.KLEINSTE_KANTE
                || tiefe < Platzhalterbau.KLEINSTE_KANTE) {
            throw new IllegalArgumentException("Die Vorlage \"" + datei + "\" ist "
                    + breite + "x" + hoehe + "x" + tiefe + " - unter "
                    + Platzhalterbau.KLEINSTE_KANTE + " hat ein Haus kein Inneres");
        }
    }

    /**
     * Ob diese Vorlage einen GIEBEL zur Gasse haben kann.
     *
     * <p>Giebelstaendig heisst: Die Schmalseite zeigt zur Gasse. Eine Vorlage, die
     * breiter als tief ist, steht TRAUFstaendig - sie passt an einen Platz, aber nicht in
     * eine Hansezeile. Die Stadt nimmt sie trotzdem, denn der Betreiber darf das wollen;
     * gemeldet wird es, damit er nicht raetselt, warum eine Zeile anders aussieht.
     */
    public boolean giebelstaendig() {
        return tiefe >= breite;
    }

    /** Die Flaeche, die diese Vorlage in der Ebene braucht. */
    public int grundflaeche() {
        return breite * tiefe;
    }

    /** Eine Zeile fuer den Startbericht. */
    public String alsText() {
        return datei + " " + breite + "x" + hoehe + "x" + tiefe;
    }
}
