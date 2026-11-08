package de.freimarken.kern;

import java.util.Locale;

/**
 * Eine mietbare Ladeneinheit in der Markthalle - ein Raum und seine Truhe.
 *
 * <p>Teil A von Schritt 8. Die Einheit ist ein ORT und noch kein Vertrag: Wer sie mietet
 * und wie lange, kommt mit Teil B.
 *
 * <p><b>Warum ein Quader und kein Rasterfeld.</b> Die Markthalle steht in Altfurt, und
 * Altfurt hat kein Raster - es ist gewachsenes Gelaende mit von Hand gebauten Haeusern
 * (Plan 2.1). Plan 6.1 hat genau diesen Fall vorgesehen: "Fuer Besitz, der nicht am Raster
 * haengt ... kommt der Quader spaeter als eigene Spalte, die nur dort gefuellt wird."
 *
 * <p><b>Warum die Truhe dazugehoert.</b> In Altfurt stehen alle Spieler im
 * Abenteuermodus, und der verbietet das Setzen von Bloecken - ein Mieter kann sich keine
 * Truhe hinstellen. Die Einheit kommt darum mit ihrer Truhe aus dem Bauwerk, und der
 * Mieter fuellt sie nur. Eine Einheit ohne Truhe waere ein Laden, in dem nichts liegen
 * kann; darum ist die Truhe Pflicht und muss IN der Einheit stehen.
 *
 * @param name    der Name in der {@code config.yml} und im Befehl - klein, ohne Umlaut
 * @param bereich der Raum der Einheit, beide Ecken eingeschlossen
 * @param truheX  wo die Ladentruhe steht
 * @param truheY  "
 * @param truheZ  "
 */
public record Ladeneinheit(String name, Quader bereich, int truheX, int truheY,
                           int truheZ) {

    /**
     * Hoechstens 16 x 16 x 16 Bloecke.
     *
     * <p>Eine Schranke mit Grund und keine Zierde: Eine Einheit ist ein Marktstand, und
     * ein Marktstand ist klein (fuenf mal fuenf mal fuenf sind 125 Bloecke). Wer aus
     * Versehen zwei Nullen anhaengt, vermietet sonst halb Altfurt - und bemerkt es erst,
     * wenn ein Mieter dort Truhen leerraeumt, die ihm nicht gehoeren.
     *
     * <p>Die Zahl ist gerechnet und nicht geraten: 4.096 Bloecke sind ein Raum von 16
     * Kantenlaenge, also mehr, als ein Laden je braucht, und klein genug, dass eine
     * vertippte Ecke auffaellt.
     */
    public static final long HOECHSTE_BLOECKE = 16L * 16L * 16L;

    public Ladeneinheit {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Eine Ladeneinheit ohne Namen ist keine");
        }
        name = name.trim().toLowerCase(Locale.ROOT);
        if (!brauchbarerName(name)) {
            throw new IllegalArgumentException("\"" + name + "\" ist kein brauchbarer"
                    + " Name fuer eine Ladeneinheit - erlaubt sind kleine Buchstaben,"
                    + " Ziffern und Striche. Der Name steht in der config.yml UND im"
                    + " Befehl, also darf er kein Leerzeichen tragen.");
        }
        if (bereich == null) {
            throw new IllegalArgumentException("Die Ladeneinheit \"" + name + "\" hat"
                    + " keinen Bereich - ein Laden ohne Raum ist keiner");
        }
        if (bereich.bloecke() > HOECHSTE_BLOECKE) {
            throw new IllegalArgumentException("Die Ladeneinheit \"" + name + "\" umfasst "
                    + bereich.bloecke() + " Bloecke und damit mehr als "
                    + HOECHSTE_BLOECKE + ". Das ist kein Marktstand mehr, sondern ein"
                    + " Stadtteil - pruefe die Ecken " + bereich.alsText() + ".");
        }
        // Die Truhe MUSS drin stehen. Eine Truhe daneben waere eine, die der Mieter nicht
        // geschuetzt bekommt - und die der Nachbar leerraeumt.
        if (!bereich.enthaelt(truheX, truheY, truheZ)) {
            throw new IllegalArgumentException("Die Truhe der Ladeneinheit \"" + name
                    + "\" steht bei " + truheX + "/" + truheY + "/" + truheZ
                    + " und damit ausserhalb ihres Bereichs " + bereich.alsText()
                    + ". Sie waere ungeschuetzt.");
        }
    }

    /** Ob dieser Punkt in der Einheit liegt - fuer den Truhenschutz. */
    public boolean enthaelt(int x, int y, int z) {
        return bereich.enthaelt(x, y, z);
    }

    /** Ob dort die Ladentruhe steht. */
    public boolean istTruhe(int x, int y, int z) {
        return truheX == x && truheY == y && truheZ == z;
    }

    /** Der Ort der Truhe als Text - fuer Klagen und Berichte. */
    public String truheAlsText() {
        return truheX + "/" + truheY + "/" + truheZ;
    }

    /**
     * Ob aus diesem Text ein Name werden kann.
     *
     * <p>Dieselbe Form wie bei den Berufen und den Lebenszeichen: Der Name ist zugleich
     * ein Schluessel in der Datei des Betreibers und ein Wort in einem Befehl. Ein
     * Leerzeichen darin macht {@code /laden mieten halle 1} zu zwei Argumenten, und ein
     * Umlaut verschwindet unter einer anderen Kodierung.
     */
    public static boolean brauchbarerName(String name) {
        return name != null && name.matches("[a-z0-9-]{1,32}");
    }
}
