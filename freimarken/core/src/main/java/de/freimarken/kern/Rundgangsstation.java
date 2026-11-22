package de.freimarken.kern;

import java.util.Locale;

/**
 * EINE Station des Rundgangs durch Altfurt - ein Name und ein Bereich (Plan 10.2).
 *
 * <p>Plan 10.2 nennt sechs: Buergeramt, Zunfthaus, Markthalle mit der Freien Truhe,
 * Muenzhaus, Bundesratshaus und das Stadttor in die Marktstadt. Welche es WIRKLICH gibt,
 * steht in der {@code config.yml} - und ab Werk steht dort keine: Die Gebaeude in Altfurt
 * sind Handarbeit, und ein Bereich auf einem Platz, auf dem nichts steht, waere eine
 * Station, die niemand findet.
 *
 * <p><b>Der Name ist ein Schluessel und kein Text.</b> Wie die Station HEISST, steht in
 * der Sprachdatei unter {@code rundgang.station.<name>.name}; was man dort tut, unter
 * {@code .text}. Dieselbe Trennung wie bei den Berufen, den Herausforderungen und den
 * Katalogposten: Was es gibt, steht in der {@code config.yml}, wie es heisst, in der
 * {@code sprache.yml}.
 *
 * <p><b>Der Bereich ist ein {@link Quader}</b> - die EINE Bereichsform dieses Projekts.
 * Kein Radius und kein Mittelpunkt mit Abstand: Ein Gebaeude ist ein Raum, und zwei
 * Bereichsformen waeren zwei Rechnungen fuer dieselbe Frage.
 *
 * <p><b>Die Hoehe gehoert dazu und ist nicht gleichgueltig.</b> Altfurt ist eine FLACHE
 * Welt (Boden y=-61, erster freier Block y=-60); ein Quader, der nur in der Ebene liegt,
 * zaehlte einen Spieler auch dann, wenn er zwanzig Bloecke darueber fliegt. Wie hoch ein
 * Raum ist, entscheidet der Betreiber - und darum steht es in seiner Datei.
 *
 * @param schluessel der Name der Station, kleingeschrieben wie in der Datei
 * @param bereich    wo sie liegt. Betritt ein Spieler ihn, ist die Station gesehen
 */
public record Rundgangsstation(String schluessel, Quader bereich) {

    /** So lang darf ein Stationsname sein - so lang ist die Spalte in der Ablage. */
    public static final int HOECHSTE_SCHLUESSELLAENGE = 32;

    /**
     * Welche Zeichen ein Stationsname tragen darf.
     *
     * <p>Dasselbe Muster wie bei einem Rangschluessel und einem Katalogposten: Der Name
     * steht in der Datei, in der Sprachdatei und in der Datenbank, und was an einer
     * dieser drei Stellen nicht wiederzuerkennen ist, ist ein Fehler, den niemand findet.
     */
    public static final String MUSTER = "[a-z][a-z0-9_-]*";

    /**
     * So viele Bloecke darf eine Station hoechstens umfassen.
     *
     * <p>64x64x64, also das Mass eines Freistadt-Grundstuecks in der Hoehe begrenzt. Eine
     * Station ist ein GEBAEUDE, kein Stadtteil: Ein Bereich von der Groesse Altfurts
     * waere eine Station, die jeder beim Herumlaufen trifft, und der Rundgang waere mit
     * dem ersten Schritt fertig. Die Grenze steht hier und nicht im Leser, weil sie zur
     * Sache gehoert und nicht zur Datei.
     */
    public static final int HOECHSTE_BLOECKE = 64 * 64 * 64;

    public Rundgangsstation {
        if (schluessel == null || schluessel.isBlank()) {
            throw new IllegalArgumentException("Eine Station ohne Namen - der Name ist"
                    + " ihr Schluessel in der Sprachdatei und in der Ablage");
        }
        if (bereich == null) {
            throw new IllegalArgumentException("Die Station \"" + schluessel + "\" hat"
                    + " keinen Bereich. Ohne ihn kann sie niemand betreten.");
        }
        schluessel = schluessel.trim().toLowerCase(Locale.ROOT);
        if (!schluessel.matches(MUSTER)) {
            throw new IllegalArgumentException("Der Stationsname \"" + schluessel
                    + "\" taugt nicht: erlaubt sind Kleinbuchstaben, Ziffern,"
                    + " Unterstrich und Bindestrich, und beginnen muss er mit einem"
                    + " Buchstaben.");
        }
        if (schluessel.length() > HOECHSTE_SCHLUESSELLAENGE) {
            throw new IllegalArgumentException("Der Stationsname \"" + schluessel
                    + "\" ist laenger als " + HOECHSTE_SCHLUESSELLAENGE + " Zeichen - so"
                    + " lang ist die Spalte in der Ablage.");
        }
        if (bereich.bloecke() > HOECHSTE_BLOECKE) {
            throw new IllegalArgumentException("Die Station \"" + schluessel + "\" hat "
                    + bereich.bloecke() + " Bloecke, erlaubt sind " + HOECHSTE_BLOECKE
                    + ". Eine Station ist ein Gebaeude und kein Stadtteil: Ein Bereich"
                    + " von der Groesse Altfurts waere mit dem ersten Schritt gesehen.");
        }
    }

    /** Ob dieser Ort in der Station liegt. */
    public boolean enthaelt(int x, int y, int z) {
        return bereich.enthaelt(x, y, z);
    }
}
