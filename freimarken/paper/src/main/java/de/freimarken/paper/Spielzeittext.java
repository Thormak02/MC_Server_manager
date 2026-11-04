package de.freimarken.paper;

import de.freimarken.kern.Spielzeitablage;
import de.freimarken.kern.Spielzeitregel;
import de.freimarken.kern.Spielzeitstand;
import de.freimarken.kern.Tagesgrenze;
import de.freimarken.kern.Zeitwerte;
import java.time.Duration;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Die gemessene Spielzeit in Worte - fuer die Anzeige.
 *
 * <p>Eine eigene Klasse und keine Zeilen im Befehl, nach dem Muster von
 * {@link Verlaufszeile}: So laesst sich die Anzeige ohne laufenden Server pruefen, und das
 * ist hier noetig, weil darin gerechnet wird. Die offene Sitzung muss dazukommen, und sie
 * kann ueber Mitternacht liegen - dann gehoert nur ihr heutiger Anteil zu "heute".
 *
 * <p><b>Alle Saetze der ANZEIGE stehen hier</b>, auch der Hinweis zum Einbau. Keiner steckt
 * tief in einer Verzweigung oder mitten in einer Rechnung. Nicht hierher gehoeren die drei
 * Absagen des Befehls - Konsole, abgemeldeter Spieler, Ablage antwortet nicht -, weil in
 * ihnen nichts zu rechnen ist; sie stehen gesammelt am Kopf von {@link SpielzeitBefehle},
 * also auch dort an EINER Stelle und nicht in den Verzweigungen, die sie senden. Die
 * Sprachdatei aus Plan Abschnitt 13 gibt es noch nicht; wenn sie kommt, ist der Umzug an
 * beiden Stellen ein Verschieben und keine Suche.
 */
final class Spielzeittext {

    /** Der Tag im Klartext - deutsch, wie im ganzen Plugin. */
    private static final DateTimeFormatter TAG =
            DateTimeFormatter.ofPattern("dd.MM.yyyy", Locale.GERMANY);

    /**
     * Keine Einstellungen, sondern die Definition von Stunde und Minute.
     *
     * <p>Sie stehen als Konstante da, damit in der Rechnung keine nackte Zahl steht - nicht,
     * weil jemand sie aendern koennte.
     */
    private static final long SEKUNDEN_JE_STUNDE = 3600L;

    private static final long SEKUNDEN_JE_MINUTE = 60L;

    /**
     * Der Satz, ohne den ein alter Spieler einen Fehler meldet.
     *
     * <p>Wer seit Monaten auf dem Server ist, liest hier eine kleine Zahl - und das ist
     * richtig: Uebernommen wird nichts. Die Minecraft-Statistik waere die einzige Quelle,
     * und sie ist fuer jedes Plugin schreibbar, nicht an eine Welt gebunden und von einer
     * Weltkopie verdoppelt.
     *
     * <p>Sichtbar als Konstante, damit die Probe die ausgegebene Zeile dagegen halten kann -
     * gesendet wird der Satz als letzte Zeile von {@link #werte} und nicht vom Befehl.
     */
    static final String HINWEIS_KEIN_ALTBESTAND = "Gezaehlt wird erst seit dem Einbau dieser"
            + " Messung - Spielstunden von davor stehen nirgends, jeder faengt bei 0 an.";

    /**
     * Wie die Zustandszeile endet, solange nach einem Fehlschlag nicht geschrieben wird.
     *
     * <p>Ohne diesen Satz nennte die Anzeige genau in dem Augenblick einen Abstand, den es
     * gerade nicht gibt: Nach einem Fehlschlag setzt der Takt das Schreiben mehrere
     * Taktlaengen lang aus, und "das geschieht alle 60 s" ist dann um das Mehrfache
     * falsch - vorgelesen dem Spieler, der gerade nachsieht, ob seine Zeit gesichert wird.
     * Die angezeigte MENGE stimmt dabei schon (der wartende Stapel steht darin), nur der
     * Abstand nicht.
     *
     * <p>Keine Zahl darin, und zwar absichtlich: Wie viele Schlaege ausgesetzt werden,
     * leitet {@link Spielzeitbuch} aus {@code Spielzeitregel.KLEMME_IN_TAKTEN} ab. Eine
     * abgeleitete Zahl in diesem Satz waere beim naechsten Aendern dort still falsch, und
     * keine Probe wuerde rot. Was der Spieler wissen muss, ist ohnehin nicht die Zahl,
     * sondern dass weitergezaehlt und nachgeschrieben wird.
     */
    static final String SCHREIBZUG_WARTET = "der naechste Schreibzug wartet auf die"
            + " Datenbank - gezaehlt wird weiter, nachgeschrieben wird alles.";

    private Spielzeittext() {
    }

    /**
     * Wie die Zustandszeile endet, solange die Ablage antwortet - mit dem Takt.
     *
     * <p>Eine Methode und keine Konstante, weil der eingestellte Takt darin steht; dieselbe
     * Abwaegung wie bei {@code nichtDa(name)} in {@link SpielzeitBefehle}. Der Satz steht
     * damit bei den anderen und nicht in der Verzweigung, die ihn waehlt.
     */
    static String taktAbstand(long taktSekunden) {
        return "das geschieht alle " + taktSekunden + " s.";
    }

    /** Die Ueberschrift fuer den eigenen Stand. */
    static String kopfSelbst() {
        return "Deine Spielzeit";
    }

    /** Die Ueberschrift fuer einen anderen Spieler. */
    static String kopfFuer(String name) {
        return "Spielzeit von " + name;
    }

    /**
     * Die vier Zeilen: heute, gesamt, ob gerade gezaehlt wird, und der Hinweis zum Einbau.
     *
     * <p>Die offene Sitzung wird dazugerechnet. {@code lies()} kennt nur, was GESCHRIEBEN
     * ist - wer beitritt und sofort nachsieht, laese sonst 0 neben "wird gezaehlt" und
     * meldete das als Fehler.
     *
     * <p>Der Hinweis zum Einbau haengt HIER an und wird nicht vom Befehl einzeln gesendet.
     * Sonst pruefte keine Probe, dass er den Spieler erreicht: Wer die eine Sendezeile im
     * Befehl verliert, haette eine vollstaendig gruene Suite, und genau dann liest ein
     * Spieler mit 200 Stunden eine kleine Zahl ohne Erklaerung. So prueft dieselbe Probe wie
     * fuer die drei anderen Zeilen, dass er da ist.
     *
     * <p>Der Stapel {@code wartend} muss mit hinein, sonst zeigte die Anzeige nach einem
     * Fehlschlag der Datenbank eine Zahl, die um bis zu fuenf Taktlaengen zu klein ist:
     * {@code lies()} kennt nur das Geschriebene, {@code offen} nur die Spanne seit dem
     * letzten Takt, und dazwischen liegen die gemessenen Zuschlaege, die hinter der Sperre
     * auf ihren Schreibzug warten. Was gemessen ist, soll der Spieler lesen.
     *
     * <p>Und {@code gesperrt} muss mit hinein, weil sonst die MENGE richtig und der
     * ABSTAND falsch waere: Steht die Sperre, wird eben nicht alle {@code takt} Sekunden
     * geschrieben. Siehe {@link #SCHREIBZUG_WARTET}.
     *
     * @param stand    was in der Ablage steht, fuer den Tag, der hier "heute" heisst
     * @param offen    was seit dem letzten Takt aufgelaufen ist, noch nicht geschrieben
     * @param wartend  schon gerechnete Zuschlaege dieses Spielers, noch nicht geschrieben -
     *                 bereits nach Tagen geteilt, hier wird nur addiert
     * @param gesperrt ob der Takt nach einem Fehlschlag gerade nicht schreibt
     *                 ({@link Spielzeitbuch#gesperrt()})
     * @param untaetig wie lange der Spieler schon nichts getan hat
     */
    static List<String> werte(Spielzeitstand stand, Spielzeitregel.Urteil offen,
                              List<Spielzeitablage.Zuschlag> wartend, boolean gesperrt,
                              Duration untaetig, Zeitwerte zeit) {
        long heuteDazu = 0L;
        long gesamtDazu = 0L;
        // Geteilt wie beim Schreiben, mit derselben Regel: Eine Sitzung ueber Mitternacht
        // gehoert zu zwei Tagen, und nur der heutige Anteil darf in die Zeile "heute".
        for (Tagesgrenze.Tagesanteil anteil
                : Tagesgrenze.teile(offen.von(), offen.bis(), zeit.zone())) {
            gesamtDazu += anteil.sekunden();
            if (anteil.tag().equals(stand.tag())) {
                heuteDazu += anteil.sekunden();
            }
        }
        for (Spielzeitablage.Zuschlag zuschlag : wartend) {
            gesamtDazu += zuschlag.sekunden();
            if (zuschlag.tag().equals(stand.tag())) {
                heuteDazu += zuschlag.sekunden();
            }
        }
        List<String> zeilen = new ArrayList<>();
        zeilen.add("heute   " + dauer(stand.heute().plusSeconds(heuteDazu))
                + "   (" + TAG.format(stand.tag()) + ", Tagesgrenze "
                + zeit.zone().getId() + ")");
        zeilen.add("gesamt  " + dauer(stand.gesamt().plusSeconds(gesamtDazu)));
        zeilen.add(zustand(offen, Duration.ofSeconds(gesamtDazu), gesperrt, untaetig,
                zeit));
        zeilen.add(HINWEIS_KEIN_ALTBESTAND);
        return List.copyOf(zeilen);
    }

    /**
     * Zum Beispiel {@code "12 h 05 min"}, {@code "7 min"} oder {@code "unter 1 min"}.
     *
     * <p>Minuten zweistellig, sobald Stunden davor stehen: "3 h 5 min" liest sich wie drei
     * Stunden und fuenf Minuten, ist aber von "3 h 50 min" auf einen Blick nicht zu
     * unterscheiden.
     *
     * <p>Eine negative Dauer wird als 0 gelesen. Es gibt keinen Weg dorthin - weder
     * {@link Spielzeitstand} noch {@link Spielzeitregel} laesst sie entstehen -, aber eine
     * Anzeige, die mit einer Ausnahme abbricht, waere die schlechtere Antwort.
     */
    static String dauer(Duration dauer) {
        if (dauer == null) {
            throw new IllegalArgumentException("Ohne Dauer gibt es nichts anzuzeigen");
        }
        long sekunden = Math.max(0L, dauer.toSeconds());
        long stunden = sekunden / SEKUNDEN_JE_STUNDE;
        long minuten = (sekunden % SEKUNDEN_JE_STUNDE) / SEKUNDEN_JE_MINUTE;
        if (stunden > 0L) {
            return stunden + " h " + (minuten < 10L ? "0" : "") + minuten + " min";
        }
        if (minuten > 0L) {
            return minuten + " min";
        }
        return sekunden > 0L ? "unter 1 min" : "0 min";
    }

    /**
     * Wie {@link #dauer}, aber unter einer Minute in Sekunden.
     *
     * <p>Fuer die laufende Sitzung und die Untaetigkeit: Dort sind es meist Sekunden, und
     * "unter 1 min" waere genau an der Stelle unbrauchbar, an der ein Spieler zusieht, ob
     * die Zahl waechst.
     */
    static String genaueDauer(Duration dauer) {
        if (dauer == null) {
            throw new IllegalArgumentException("Ohne Dauer gibt es nichts anzuzeigen");
        }
        long sekunden = Math.max(0L, dauer.toSeconds());
        return sekunden < SEKUNDEN_JE_MINUTE ? sekunden + " s" : dauer(dauer);
    }

    /**
     * Ob gerade gezaehlt wird - die Zeile, an der man Takt und Untaetigkeitsregel am Server
     * ablesen kann.
     *
     * <p>Welcher Satz den Abstand nennt, entscheidet {@code gesperrt}; die Saetze selbst
     * stehen oben bei den anderen ({@link #taktAbstand} und {@link #SCHREIBZUG_WARTET}).
     *
     * @param ungeschrieben alles Gemessene, was noch nicht in der Ablage steht: die offene
     *                      Spanne UND der Stapel hinter der Sperre. Nicht
     *                      {@code offen.sekunden()}, denn das waere nach einem Fehlschlag
     *                      der Datenbank nur der letzte Takt davon
     * @param gesperrt      ob der Takt gerade nicht schreibt - dann ist der Abstand nicht
     *                      der eingestellte Takt
     */
    private static String zustand(Spielzeitregel.Urteil offen, Duration ungeschrieben,
                                  boolean gesperrt, Duration untaetig, Zeitwerte zeit) {
        if (offen.ausgang() == Spielzeitregel.Ausgang.UNTAETIG) {
            return "wird gerade NICHT gezaehlt - untaetig seit " + genaueDauer(untaetig)
                    + ", gezaehlt wird bis " + zeit.untaetigMinuten() + " min. Ein Schritt"
                    + " oder ein Tastendruck zaehlt wieder.";
        }
        String zeile = "wird gezaehlt - die letzten " + genaueDauer(ungeschrieben)
                + " sind noch nicht geschrieben, "
                + (gesperrt ? SCHREIBZUG_WARTET : taktAbstand(zeit.taktSekunden()));
        if (zeit.untaetigkeitZaehltMit()) {
            zeile = zeile + " Abwesenheit zaehlt auf diesem Server mit ("
                    + Zeitwerte.PFAD_UNTAETIG + ": 0).";
        }
        return zeile;
    }
}
