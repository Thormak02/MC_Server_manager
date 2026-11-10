package de.freimarken.kern;

/**
 * Wie eine lange Liste auf Seiten faellt - die EINE Rechnung dafuer.
 *
 * <p><b>Warum sie eine eigene Klasse ist:</b> Seit dem 10.11.2026 blaettern ZWEI Listen -
 * {@code /shop liste} zeigt die eigenen Laeden, {@code /markt liste} die Staende des
 * Bundes. Beide rechnen dasselbe, und zwei Abschriften derselben Rechnung laufen
 * auseinander: Die eine bekommt eine Schranke nachgezogen, die andere nicht.
 *
 * <p><b>Und es sind genau die Zeilen, an denen es schiefgeht.</b> Die
 * Einspritzungsrunde zu {@code /shop liste} (10.11.2026) hat vier ihrer zwoelf Faelle aus
 * diesen drei Zeilen geholt: die Seitenzahl ABGERUNDET statt aufgerundet (dann ist der
 * letzte Laden unerreichbar), der Seitenanfang um eine Seite verschoben, die letzte Seite
 * als nicht vorhanden abgewiesen, und der Schnitt ueber das Ende hinaus. Alle vier wurden
 * gefangen - aber je Liste muessten sie neu gefangen werden, und die zweite Liste kommt
 * ohne die Proben der ersten.
 *
 * <p>Gerechnet wird in SEITEN ab 1, wie ein Mensch zaehlt. Die Schnittstellen in die
 * Liste sind {@link #ersteZeile} und {@link #letzteZeile}, beide als Index ab 0 - die
 * Umrechnung ist der Teil, der sich verrechnen laesst, und sie steht nur hier.
 */
public final class Seitenregel {

    private Seitenregel() {
    }

    /**
     * Wie viele Seiten diese Liste hat - mindestens eine.
     *
     * <p><b>AUFGERUNDET</b>, und das ist die Zeile, die am teuersten falsch ist: Bei drei
     * Laeden auf zwei je Seite gibt es ZWEI Seiten, und abgerundet waere es eine - der
     * dritte Laden waere ueber den Befehl nie zu sehen, und genau dafuer gibt es die
     * Liste.
     *
     * <p>Mindestens eine Seite auch bei einer LEEREN Liste: Ein Kopf "Seite 1 von 0"
     * waere eine Auskunft, die niemand lesen kann. Der Aufrufer faengt den leeren Fall
     * vorher ab - und wenn er es vergisst, liest ein Spieler "Seite 1 von 1" und keine
     * Zeile, was wahr ist.
     */
    public static int seiten(int anzahl, int jeSeite) {
        pruefe(anzahl, jeSeite);
        return Math.max(1, (anzahl + jeSeite - 1) / jeSeite);
    }

    /**
     * Ob es diese Seite gibt - 1 bis {@link #seiten}.
     *
     * <p>Eine benannte Frage und kein Vergleich beim Aufrufer: {@code seite > seiten}
     * und {@code seite >= seiten} sehen gleich aus, und das zweite weist die LETZTE Seite
     * ab. Auch das war eine Einspritzung.
     */
    public static boolean gibtEs(int seite, int anzahl, int jeSeite) {
        return seite >= 1 && seite <= seiten(anzahl, jeSeite);
    }

    /**
     * Der Index der ersten Zeile dieser Seite - ab 0.
     *
     * <p>{@code (seite - 1) * jeSeite} und nicht {@code seite * jeSeite}: Seiten zaehlen
     * ab 1, Listen ab 0. Ohne das Minus zeigt Seite 1 die zweite Seite.
     */
    public static int ersteZeile(int seite, int anzahl, int jeSeite) {
        if (!gibtEs(seite, anzahl, jeSeite)) {
            throw new IllegalArgumentException("Seite " + seite + " gibt es nicht - "
                    + anzahl + " Zeilen zu " + jeSeite + " je Seite sind "
                    + seiten(anzahl, jeSeite) + " Seiten. Wer blaettert, fragt vorher"
                    + " gibtEs - diese Rechnung soll nicht raten.");
        }
        return (seite - 1) * jeSeite;
    }

    /**
     * Der Index HINTER der letzten Zeile dieser Seite - fuer {@code subList}.
     *
     * <p>Gedeckelt auf die Laenge: Die letzte Seite ist meistens nicht voll, und ein
     * Schnitt ueber das Ende hinaus ist ein Wurf mitten in der Antwort.
     */
    public static int letzteZeile(int seite, int anzahl, int jeSeite) {
        return Math.min(anzahl, ersteZeile(seite, anzahl, jeSeite) + jeSeite);
    }

    /**
     * Ob es nach dieser Seite noch eine gibt - fuer den Weiter-Hinweis.
     *
     * <p>{@code <} und nicht {@code <=}: Auf der letzten Seite darf kein Hinweis stehen,
     * der auf eine Seite zeigt, die es nicht gibt.
     */
    public static boolean gehtWeiter(int seite, int anzahl, int jeSeite) {
        return seite < seiten(anzahl, jeSeite);
    }

    private static void pruefe(int anzahl, int jeSeite) {
        if (anzahl < 0) {
            throw new IllegalArgumentException("Eine Liste mit " + anzahl
                    + " Zeilen gibt es nicht");
        }
        if (jeSeite < 1) {
            throw new IllegalArgumentException("Eine Seite mit " + jeSeite
                    + " Zeilen ist keine - dann zeigte die Liste nie etwas, und der"
                    + " Befehl waere stumm abgeschaltet");
        }
    }
}
