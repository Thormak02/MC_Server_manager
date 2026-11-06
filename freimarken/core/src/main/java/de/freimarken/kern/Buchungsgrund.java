package de.freimarken.kern;

import java.util.Locale;

/**
 * Warum Geld geflossen ist.
 *
 * <p>Eine Aufzaehlung und kein Freitext. Der Grund muss gruppierbar sein, sonst kann der
 * Wirtschaftsbericht aus Plan 12b nie eine Senkensumme bilden - ein Freitext spaltet
 * dieselbe Senke in Tippfehler auf, und zwei Jahre spaeter steht im Buch dreimal fast
 * dasselbe.
 *
 * <p><b>Die Richtung gehoert an den Grund</b>, nicht an das Vorzeichen allein. Die erste
 * Parzelle ist gratis: Das ist eine Buchung ueber 0 Mark, also ohne Vorzeichen - und
 * trotzdem eine Ausgabe. Nur so bleibt sie im Buch von einer Gutschrift ueber 0
 * unterscheidbar.
 *
 * <p>Der Schluessel ist ASCII und kleingeschrieben, denn er landet in der Datenbank und
 * muss die heutige Wortwahl ueberleben. Was ein Spieler liest, steht bei den uebrigen
 * Meldungen im Paper-Teil; wer die deutschen Woerter hier einfrieren wuerde, koennte sie
 * spaeter nicht mehr aendern, ohne alte Zeilen unlesbar zu machen.
 *
 * <p>Neun Gruende und nicht mehr. Ladenmiete, Marktgebuehr und Tagesbonus kommen mit
 * ihrem eigenen Bauschritt - sie hier schon einzutragen waere Vorbauen, und ein Grund,
 * den niemand bucht, sieht im Bericht aus wie eine Senke, die nicht funktioniert.
 */
public enum Buchungsgrund {

    /** Das Startguthaben, im Augenblick der Eroeffnung. */
    EROEFFNUNG("eroeffnung", Richtung.EINNAHME),

    /**
     * Der Stand, den ein Konto beim Einfuehren des Buchungsbuchs schon hatte.
     *
     * <p>Heisst ausdruecklich nicht {@code eroeffnung}: Was vor dem Buch geschah, wissen
     * wir nicht. Diese Zeile behauptet nur etwas ueber das Jetzt und erfindet keine
     * Geschichte.
     */
    BESTAND("bestand", Richtung.EINNAHME),

    /** Eine Parzelle genommen. */
    PARZELLE_GEKAUFT("parzelle_gekauft", Richtung.AUSGABE),

    /** Eine Parzelle konnte nicht eingetragen werden - der Preis kommt zurueck. */
    PARZELLE_ERSTATTET("parzelle_erstattet", Richtung.EINNAHME),

    /** Zwei Parzellen zusammengelegt. */
    ZUSAMMENLEGUNG("zusammenlegung", Richtung.AUSGABE),

    /** Das Zusammenlegen ist gescheitert - die Gebuehr kommt zurueck. */
    ZUSAMMENLEGUNG_ERSTATTET("zusammenlegung_erstattet", Richtung.EINNAHME),

    /**
     * Einen Beruf abgelegt - die Wechselgebuehr.
     *
     * <p>Das WAEHLEN bucht nicht und hat darum keinen Grund: Ein freier Platz kostet
     * nichts. Bezahlt wird das Verlassen, denn andersherum waere die Gebuehr umgehbar -
     * erst aufgeben, einen Tag warten, dann gratis etwas anderes waehlen.
     */
    BERUF_VERLASSEN("beruf_verlassen", Richtung.AUSGABE),

    /** Das Ablegen ist gescheitert - die Gebuehr kommt zurueck. */
    BERUF_ERSTATTET("beruf_erstattet", Richtung.EINNAHME),

    /**
     * Was ein Beruf eingebracht hat - die erste und wichtigste QUELLE dieser Wirtschaft.
     *
     * <p><b>Gesammelt und nicht je Block.</b> Eine Buchung je abgebautem Block waere bei
     * fuenfzig Spielern eine Viertelmillion Zeilen in der Stunde und 30 bis 150 Prozent
     * des Hauptfadens nur fuer das Buchen - gerechnet am 6.11.2026. Gebucht wird darum,
     * was ein Zeitgeber gesammelt hat; die einzelne Taetigkeit steht in keiner Zeile.
     *
     * <p>Wer wissen will, WAS ein Spieler getan hat, fragt das Logbuch - es ist ein
     * austauschbarer Nachbar, und dieses Buch ist das Geldbuch. Hier steht nur, dass Geld
     * aus Arbeit kam, und genau das braucht der Verdienstdeckel: Er rechnet seine
     * Tagessumme aus DIESEN Zeilen, statt eine zweite Wahrheit daneben zu fuehren.
     */
    BERUF_VERDIENST("beruf_verdienst", Richtung.EINNAHME);

    /** Ob Geld zum Spieler hin oder von ihm weg fliesst. */
    public enum Richtung {
        EINNAHME,
        AUSGABE
    }

    private final String schluessel;
    private final Richtung richtung;

    Buchungsgrund(String schluessel, Richtung richtung) {
        this.schluessel = schluessel;
        this.richtung = richtung;
    }

    /** Wie der Grund in der Datenbank steht. */
    public String schluessel() {
        return schluessel;
    }

    public Richtung richtung() {
        return richtung;
    }

    /** Ob Geld vom Spieler weg fliesst. */
    public boolean istAusgabe() {
        return richtung == Richtung.AUSGABE;
    }

    /**
     * Der Grund zu einem Schluessel aus der Datenbank.
     *
     * <p>Wirft bei einem unbekannten Schluessel, statt etwas zu raten. Eine Zeile, deren
     * Grund niemand kennt, kommt aus einer neueren Fassung des Plugins - und dann ist das
     * Buch nicht lesbar, sondern falsch lesbar, und das ist schlimmer.
     */
    public static Buchungsgrund ausSchluessel(String text) {
        if (text != null) {
            String klein = text.trim().toLowerCase(Locale.ROOT);
            for (Buchungsgrund grund : values()) {
                if (grund.schluessel.equals(klein)) {
                    return grund;
                }
            }
        }
        throw new IllegalArgumentException("Unbekannter Buchungsgrund: " + text);
    }
}
