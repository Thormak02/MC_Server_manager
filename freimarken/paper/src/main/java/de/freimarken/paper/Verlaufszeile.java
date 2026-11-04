package de.freimarken.paper;

import de.freimarken.kern.Buchung;
import de.freimarken.kern.Buchungsgrund;
import java.time.ZoneId;
import net.kyori.adventure.text.Component;

/**
 * Eine Buchung in Worte - fuer {@code /mark verlauf}.
 *
 * <p>Eine eigene Klasse und keine Zeilen im Befehl, damit sie ohne laufenden Server
 * geprueft werden kann. Drei Dinge gehen hier schief, und alle drei sind reine Rechnerei:
 * das Vorzeichen, die Zeitzone und die Wortwahl.
 *
 * <p><b>Die Zone kommt von aussen.</b> Der Zeitpunkt einer Buchung steht in UTC - so wie
 * jeder Zeitpunkt in dieser Ablage. Ein Spieler liest aber Ortszeit, und welche Zone das
 * ist, weiss diese Klasse nicht. Sie kommt aus {@code zeit.zone}, also aus derselben
 * Stelle, in der auch die Tagesgrenze liegt - am 03.11.2026 eingeloest, und zwar ohne eine
 * Zeile hier. Dass die Zone ein Parameter ist und kein Feld, ist genau der Grund dafuer.
 *
 * <p><b>Die deutschen Woerter stehen ab dem 04.11.2026 in der Sprachdatei.</b> Dort liegt
 * der Wortlaut, hier die Rechnerei. Der Datenbankschluessel des {@link Buchungsgrund}
 * kommt dabei nicht vor: Der Pfad in der Datei entsteht aus dem AUFZAEHLUNGSNAMEN
 * ({@link MarkSatz#grundpfad}), damit eine Umbenennung in der Spalte keine Betreiberzeile
 * stumm entwertet - genau das verlangt {@code Buchungsgrund} im eigenen Javadoc.
 *
 * <p><b>Und sie gibt ein {@link Component} zurueck, nicht eine Zeichenkette.</b> Bis zum
 * 04.11.2026 baute {@code ParzellenBefehle:225-226} aus dem Text
 * {@code Component.text("  " + text, GRAY)} - die zwei Leerzeichen Einzug und die Farbe
 * standen also am Sendeort. Beides steht jetzt im Satz {@code mark.verlauf.zeile}, und das
 * geht nur mit einem Component: Waere die Zeile eine Zeichenkette geblieben, haette ein
 * {@code <gold>} darin dem Spieler woertlich im Chat gestanden, und keine Probe waere rot
 * geworden - denn ein Farbtag ist nur in einem {@code Art.WORT} verboten.
 */
final class Verlaufszeile {

    private Verlaufszeile() {
    }

    /**
     * Zum Beispiel {@code "  02.11. 14:37  -25.000 Mark  Parzelle genommen  (Stand 25.000
     * Mark)"} in Grau.
     *
     * <p>Eine reine Methode: Sprache und Zone kommen herein, ein Component geht hinaus -
     * kein {@code Player}, kein Bukkit. Nur so kann eine Probe nachsehen, WAS in der Zeile
     * steht, und nicht bloss, dass etwas gesendet wird. Dasselbe Muster macht
     * {@code SpielzeitBefehle.sende(Audience, String, List)} (:194-200) im Projekt schon
     * vor.
     *
     * @param zone die Zone, in der der Spieler liest
     */
    static Component zeile(Sprache sprache, Buchung buchung, ZoneId zone) {
        return sprache.eineZeile(MarkSatz.VERLAUF_ZEILE, Einsatz.keine()
                .mit("zeit", sprache.muster(MarkSatz.ZEIT_VERLAUF_MUSTER)
                        .format(buchung.zeitpunkt().atZone(zone)))
                .mit("betrag", mitVorzeichen(sprache, buchung))
                .mit("grund", sprache.wort(Sprachschluessel.grund(buchung.grund()),
                        Einsatz.keine()))
                .mit("stand", sprache.betrag(buchung.standNachher())));
    }

    /**
     * Der Betrag mit sichtbarem Vorzeichen.
     *
     * <p>Das Plus steht ausdruecklich da. Ohne es muesste ein Spieler aus dem Grund
     * erraten, in welche Richtung das Geld floss - und genau bei den Erstattungen, wo es
     * darauf ankommt, heissen Hin und Zurueck fast gleich.
     *
     * <p>Null bekommt kein Vorzeichen: Die erste Parzelle ist gratis, und ein "+0 Mark"
     * oder "-0 Mark" waere beides falsch.
     *
     * <p>Das Vorzeichen bleibt im PROGRAMM und zieht nicht in die Datei. Es ist ein
     * Rechenzeichen und keine Aussage - wer es aendert, aendert nicht die Wortwahl,
     * sondern die Rechnung.
     */
    private static String mitVorzeichen(Sprache sprache, Buchung buchung) {
        if (buchung.betrag() == 0L) {
            return sprache.betrag(0L);
        }
        String zeichen = buchung.betrag() > 0L ? "+" : "-";
        return zeichen + sprache.betrag(buchung.betragsgroesse());
    }
}
