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
 * <p><b>Welche WAEHRUNG die Zahlen haben, sagt das BUCH</b> [20.11.2026, Teil G]: Seit es
 * ein Goldbuch gibt, stehen in derselben Zeilenform zwei Waehrungen. Der Satz
 * {@code mark.verlauf.zeile} traegt beide, denn das Wort kommt nicht aus ihm, sondern aus
 * {@code Sprache.betrag} bzw. {@code Sprache.goldmark} - und eine Buchung soll ueberall
 * gleich AUSSEHEN, auch wenn sie ein anderes Konto beschreibt.
 *
 * <p>Hereingegeben wird darum das {@link Buchungsgrund.Buch} und nicht ein Formatierer.
 * Ein {@code LongFunction<String>} haette genau den Riegel aufgehoben, den
 * {@code Sprache.goldmark} ausdruecklich dafuer gebaut hat ("jede Aufrufstelle nennt sie
 * beim Namen"): Beide Zahlen sind {@code long}, beide Methoden passen auf dieselbe
 * Signatur, und ein vertauschter Formatierer zeigte dem Spieler das Tausendfache. Mit dem
 * Buch ist es ein erschoepfender Schalter - und die Zeile prueft ausserdem, dass der GRUND
 * in dieses Buch gehoert. Eine Markzeile im Goldverlauf wirft dann, statt zu luegen.
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
     * @param buch welches Konto diese Zeile beschreibt - davon haengt das Waehrungswort
     *             an BEIDEN Zahlen ab
     * @throws IllegalArgumentException wenn der Grund nicht in dieses Buch gehoert. Eine
     *                                  Markbuchung im Goldverlauf ist kein Zustand, auf
     *                                  den ein Spieler eine Antwort bekommen soll
     */
    static Component zeile(Sprache sprache, Buchung buchung, ZoneId zone,
                           Buchungsgrund.Buch buch) {
        if (!buchung.grund().gehoertIn(buch)) {
            throw new IllegalArgumentException("Die Buchung mit dem Grund "
                    + buchung.grund() + " gehoert in das " + buchung.grund().buch()
                    + "-Buch und wird hier als " + buch + " angezeigt - die Zahl waere"
                    + " richtig und die Waehrung falsch.");
        }
        return sprache.eineZeile(MarkSatz.VERLAUF_ZEILE, Einsatz.keine()
                .mit("zeit", sprache.muster(MarkSatz.ZEIT_VERLAUF_MUSTER)
                        .format(buchung.zeitpunkt().atZone(zone)))
                .mit("betrag", mitVorzeichen(sprache, buchung, buch))
                .mit("grund", sprache.wort(Sprachschluessel.grund(buchung.grund()),
                        Einsatz.keine()))
                .mit("stand", geld(sprache, buchung.standNachher(), buch)));
    }

    /**
     * Die Zahl mit ihrem Waehrungswort - der erschoepfende Schalter.
     *
     * <p>{@link Buchungsgrund.Buch#JEDES} wirft: Es ist die Antwort auf "in welches Buch
     * gehoert dieser Grund" und keine Waehrung. Eine Zeile kann nicht in beiden Buechern
     * gleichzeitig stehen.
     */
    private static String geld(Sprache sprache, long betrag, Buchungsgrund.Buch buch) {
        return switch (buch) {
            case MARK -> sprache.betrag(betrag);
            case GOLDMARK -> sprache.goldmark(betrag);
            case JEDES -> throw new IllegalArgumentException("JEDES ist keine Waehrung,"
                    + " sondern die Antwort auf die Frage nach dem Buch");
        };
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
    private static String mitVorzeichen(Sprache sprache, Buchung buchung,
                                        Buchungsgrund.Buch buch) {
        if (buchung.betrag() == 0L) {
            return geld(sprache, 0L, buch);
        }
        String zeichen = buchung.betrag() > 0L ? "+" : "-";
        return zeichen + geld(sprache, buchung.betragsgroesse(), buch);
    }
}
