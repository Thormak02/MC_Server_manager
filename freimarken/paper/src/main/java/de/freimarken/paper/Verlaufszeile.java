package de.freimarken.paper;

import de.freimarken.kern.Buchung;
import de.freimarken.kern.Buchungsgrund;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * Eine Buchung in Worte - fuer {@code /mark verlauf}.
 *
 * <p>Eine eigene Klasse und keine Zeilen im Befehl, damit sie ohne laufenden Server
 * geprueft werden kann. Drei Dinge gehen hier schief, und alle drei sind reine Rechnerei:
 * das Vorzeichen, die Zeitzone und die Wortwahl.
 *
 * <p><b>Die Zone kommt von aussen.</b> Der Zeitpunkt einer Buchung steht in UTC - so wie
 * jeder Zeitpunkt in dieser Ablage. Ein Spieler liest aber Ortszeit, und welche Zone das
 * ist, weiss diese Klasse nicht. Heute kommt sie als Systemzone herein; sobald die
 * Tagesgrenze aus der Einstellungsdatei kommt, aus derselben Stelle - ohne dass sich hier
 * etwas aendert.
 *
 * <p><b>Die deutschen Woerter stehen hier und nicht in der Datenbank.</b> Dort liegt der
 * ASCII-Schluessel des {@link Buchungsgrund}. Wer die Wortwahl in die Spalte schriebe,
 * koennte sie spaeter nicht mehr aendern, ohne alte Zeilen unlesbar zu machen.
 */
final class Verlaufszeile {

    /**
     * Tag und Uhrzeit ohne Jahr.
     *
     * <p>Das Jahr fehlt mit Absicht: Ein Verlauf zeigt die letzten Buchungen, und bei zehn
     * Zeilen ist die Jahresangabe zehnmal dieselbe. Wer weiter zurueck will, bekommt
     * spaeter die Chronik aus Plan 12b.
     */
    private static final DateTimeFormatter ZEIT =
            DateTimeFormatter.ofPattern("dd.MM. HH:mm", Locale.GERMANY);

    private Verlaufszeile() {
    }

    /**
     * Zum Beispiel {@code "02.11. 14:37  -25.000 Mark  Parzelle genommen  (Stand 25.000)"}.
     *
     * @param zone die Zone, in der der Spieler liest
     */
    static String text(Buchung buchung, ZoneId zone) {
        return ZEIT.format(buchung.zeitpunkt().atZone(zone))
                + "  " + mitVorzeichen(buchung)
                + "  " + wort(buchung.grund())
                + "  (Stand " + Betraege.mark(buchung.standNachher()) + ")";
    }

    /**
     * Der Betrag mit sichtbarem Vorzeichen.
     *
     * <p>Das Plus steht ausdruecklich da. Ohne es muesste ein Spieler aus dem Grund
     * erraten, in welche Richtung das Geld floss - und genau bei den Erstattungen, wo es
     * darauf ankommt, heissen Hin und Zurueck fast gleich.
     *
     * <p>Null bekommt kein Vorzeichen: Die erste Parzelle ist gratis, und „+0 Mark" oder
     * „-0 Mark" waere beides falsch.
     */
    private static String mitVorzeichen(Buchung buchung) {
        if (buchung.betrag() == 0L) {
            return Betraege.mark(0L);
        }
        String zeichen = buchung.betrag() > 0L ? "+" : "-";
        return zeichen + Betraege.mark(buchung.betragsgroesse());
    }

    /** Was ein Spieler statt des Schluessels liest. */
    static String wort(Buchungsgrund grund) {
        return switch (grund) {
            case EROEFFNUNG -> "Startguthaben";
            case BESTAND -> "Stand bei Einfuehrung des Kontobuchs";
            case PARZELLE_GEKAUFT -> "Parzelle genommen";
            case PARZELLE_ERSTATTET -> "Parzelle nicht eingetragen - erstattet";
            case ZUSAMMENLEGUNG -> "Grundstueck zusammengelegt";
            case ZUSAMMENLEGUNG_ERSTATTET -> "Zusammenlegen gescheitert - erstattet";
        };
    }
}
