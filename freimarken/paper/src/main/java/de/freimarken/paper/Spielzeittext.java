package de.freimarken.paper;

import de.freimarken.kern.Spielzeitablage;
import de.freimarken.kern.Spielzeitregel;
import de.freimarken.kern.Spielzeitstand;
import de.freimarken.kern.Tagesgrenze;
import de.freimarken.kern.Zeitwerte;
import java.time.Duration;
import java.util.List;
import net.kyori.adventure.text.Component;

/**
 * Die gemessene Spielzeit in Worte - fuer die Anzeige.
 *
 * <p>Eine eigene Klasse und keine Zeilen im Befehl, nach dem Muster von
 * {@link Verlaufszeile}: So laesst sich die Anzeige ohne laufenden Server pruefen, und das
 * ist hier noetig, weil darin gerechnet wird. Die offene Sitzung muss dazukommen, und sie
 * kann ueber Mitternacht liegen - dann gehoert nur ihr heutiger Anteil zu "heute".
 *
 * <p><b>Kein Wort steht mehr hier.</b> Seit dem 04.11.2026 holt jede Methode ihren Satz bei
 * {@link Sprache}; was bleibt, ist die RECHNUNG und die Wahl, WELCHER Satz an welche Stelle
 * gehoert. Alle Saetze dieses Befehls stehen unter {@code spielzeit:} in
 * {@code sprache-vorlage.yml}, der Vertrag dazu in {@link SpielzeitSatz}.
 *
 * <p><b>Farbe UND Einzug stehen jetzt IM Satz</b>, und das war eine Entscheidung des
 * Nutzers: In die Sprachdatei gehoeren Worte, Farben und Aufbau - Einzug, Spaltenbreite und
 * Umbruch -, aehnlich zu einem MOTD-Editor. Das frueher hier verwendete
 * {@code Component.text(..., GRAY)} gibt es darum nicht mehr; der Sendeort faerbt nichts.
 *
 * <p><b>Was im PROGRAMM bleibt, und warum:</b>
 * <ul>
 *   <li>die zweistellige Auffuellung der Minuten hinter einer Stunde ({@link #dauer}) -
 *       eine Rechnung ueber dem Wert und kein Wort. "3 h 5 min" ist von "3 h 50 min" auf
 *       einen Blick nicht zu unterscheiden.
 *   <li>die Wahl zwischen den fuenf Zahlformen und zwischen {@link #dauer} und
 *       {@link #genaueDauer} - eine Entscheidung ueber die GENAUIGKEIT, nicht ueber den
 *       Wortlaut.
 *   <li>die Verzweigung der Zustandszeile - welcher Satz zutrifft, weiss nur das Programm.
 * </ul>
 * Dieselbe Trennung traegt {@code MarkSatz.WAEHRUNG_BETRAG}: Das WORT ist des Betreibers,
 * die Zahlform bleibt hier.
 *
 * <p>Jede Methode gibt genau EINE Zeile zurueck und keine Liste, darum
 * {@link Sprache#eineZeile}: Die Spalte von {@link #werte} haelt nur in einer Zeile, und
 * zwei Proben nageln fest, dass die Anzeige GENAU VIER Zeilen hat. Eine Umbruchmarke in
 * einem dieser Saetze ist deshalb ein Wurf, der den Pfad nennt, und kein stumm verschobenes
 * Bild. Was der Betreiber hier gewinnt, ist die Farbe JE ZEILE - die war vorher nicht
 * moeglich.
 */
final class Spielzeittext {

    /**
     * Keine Einstellungen, sondern die Definition von Stunde und Minute.
     *
     * <p>Sie stehen als Konstante da, damit in der Rechnung keine nackte Zahl steht - nicht,
     * weil jemand sie aendern koennte.
     */
    private static final long SEKUNDEN_JE_STUNDE = 3600L;

    private static final long SEKUNDEN_JE_MINUTE = 60L;

    private Spielzeittext() {
    }

    /**
     * Wie die Zustandszeile endet, solange die Ablage antwortet - mit dem Takt.
     *
     * <p>Eine Methode und kein Schluessel am Sendeort, weil der eingestellte Takt
     * eingesetzt werden muss; der Satz steht damit bei den anderen und nicht in der
     * Verzweigung, die ihn waehlt.
     *
     * <p>{@code String.valueOf} und nicht {@code Einsatz.mit(name, long)}: Der Takt ist
     * eine NACKTE Zahl. Den Tausenderpunkt tragen nur Geldbetraege, und ein Takt von 3600
     * stuende sonst als "3.600" da - das waere eine Aenderung am Spielbild.
     */
    static String taktAbstand(Sprache sprache, long taktSekunden) {
        return sprache.wort(SpielzeitSatz.ZUSTAND_ABSTAND_TAKT,
                Einsatz.von("takt", String.valueOf(taktSekunden)));
    }

    /**
     * Derselbe Schluss, solange der Takt nach einem Fehlschlag nicht schreibt.
     *
     * <p>Ohne diesen Satz nennte die Anzeige genau in dem Augenblick einen Abstand, den es
     * gerade nicht gibt: Nach einem Fehlschlag setzt der Takt das Schreiben mehrere
     * Taktlaengen lang aus, und "das geschieht alle 60 s" ist dann um das Mehrfache
     * falsch - vorgelesen dem Spieler, der gerade nachsieht, ob seine Zeit gesichert wird.
     * Die angezeigte MENGE stimmt dabei schon, nur der Abstand nicht.
     *
     * <p>Eine Methode und nicht nur ein Schluessel: Sie ist der Griff, an dem zwei Proben
     * die Zustandszeile festhalten, ohne ihren Wortlaut zu kennen.
     */
    static String schreibzugWartet(Sprache sprache) {
        return sprache.wort(SpielzeitSatz.ZUSTAND_ABSTAND_WARTET, Einsatz.keine());
    }

    /** Die Ueberschrift fuer den eigenen Stand - ohne Namen, siehe {@link SpielzeitSatz}. */
    static Component kopfSelbst(Sprache sprache) {
        return sprache.eineZeile(SpielzeitSatz.KOPF_SELBST, Einsatz.keine());
    }

    /** Die Ueberschrift fuer einen anderen Spieler. */
    static Component kopfFuer(Sprache sprache, String name) {
        return sprache.eineZeile(SpielzeitSatz.KOPF_FREMD, Einsatz.von("name", name));
    }

    /**
     * Der Hinweis zum Einbau - die letzte Zeile der Anzeige.
     *
     * <p>Eine eigene Methode, damit zwei Proben "der Hinweis ist die letzte Zeile" gegen
     * den Satz selbst halten koennen, ohne seinen Wortlaut zu kennen. Ein abgeschriebener
     * Wortlaut in der Probe waere eine zweite Quelle desselben Satzes; das BILD haelt die
     * festgenagelte Tafel in {@code SpielzeitBefehleTest}.
     */
    static Component hinweisZeile(Sprache sprache) {
        return sprache.eineZeile(SpielzeitSatz.HINWEIS, Einsatz.keine());
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
     * geschrieben. Siehe {@link #schreibzugWartet}.
     *
     * <p>Der TAG kommt ueber {@link Sprache#muster} aus derselben Datei wie die Saetze;
     * {@code Locale.GERMANY} bleibt dabei im Programm, sonst wechselte ein Monatsname die
     * Sprache. Die ZONE ist eine technische Kennung und kein Wort.
     *
     * @param stand    was in der Ablage steht, fuer den Tag, der hier "heute" heisst
     * @param offen    was seit dem letzten Takt aufgelaufen ist, noch nicht geschrieben
     * @param wartend  schon gerechnete Zuschlaege dieses Spielers, noch nicht geschrieben -
     *                 bereits nach Tagen geteilt, hier wird nur addiert
     * @param gesperrt ob der Takt nach einem Fehlschlag gerade nicht schreibt
     *                 ({@link Spielzeitbuch#gesperrt()})
     * @param untaetig wie lange der Spieler schon nichts getan hat
     */
    static List<Component> werte(Sprache sprache, Spielzeitstand stand,
                                 Spielzeitregel.Urteil offen,
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
        Component heute = sprache.eineZeile(SpielzeitSatz.HEUTE, Einsatz
                .von("dauer", dauer(sprache, stand.heute().plusSeconds(heuteDazu)))
                .mit("tag", sprache.muster(SpielzeitSatz.TAG_MUSTER).format(stand.tag()))
                .mit("zone", zeit.zone().getId()));
        Component gesamt = sprache.eineZeile(SpielzeitSatz.GESAMT, Einsatz
                .von("dauer", dauer(sprache, stand.gesamt().plusSeconds(gesamtDazu))));
        return List.of(heute, gesamt,
                zustandZeile(sprache, offen, Duration.ofSeconds(gesamtDazu), gesperrt,
                        untaetig, zeit),
                hinweisZeile(sprache));
    }

    /**
     * Zum Beispiel {@code "12 h 05 min"}, {@code "7 min"} oder {@code "unter 1 min"}.
     *
     * <p>Vier der fuenf Formen stehen in der Sprachdatei; WELCHE genommen wird, entscheidet
     * diese Rechnung. Minuten zweistellig, sobald Stunden davor stehen: "3 h 5 min" liest
     * sich wie drei Stunden und fuenf Minuten, ist aber von "3 h 50 min" auf einen Blick
     * nicht zu unterscheiden. Diese Auffuellung bleibt darum im Programm - sie ist kein
     * Wort, sondern eine Zahlform, und eine Einstellzeile dazu koennte nur schaden.
     *
     * <p>Eine negative Dauer wird als 0 gelesen. Es gibt keinen Weg dorthin - weder
     * {@link Spielzeitstand} noch {@link Spielzeitregel} laesst sie entstehen -, aber eine
     * Anzeige, die mit einer Ausnahme abbricht, waere die schlechtere Antwort.
     */
    static String dauer(Sprache sprache, Duration dauer) {
        if (dauer == null) {
            throw new IllegalArgumentException("Ohne Dauer gibt es nichts anzuzeigen");
        }
        long sekunden = Math.max(0L, dauer.toSeconds());
        long stunden = sekunden / SEKUNDEN_JE_STUNDE;
        long minuten = (sekunden % SEKUNDEN_JE_STUNDE) / SEKUNDEN_JE_MINUTE;
        if (stunden > 0L) {
            return sprache.wort(SpielzeitSatz.DAUER_STUNDEN_MINUTEN, Einsatz
                    .von("stunden", String.valueOf(stunden))
                    .mit("minuten", (minuten < 10L ? "0" : "") + minuten));
        }
        if (minuten > 0L) {
            return sprache.wort(SpielzeitSatz.DAUER_MINUTEN,
                    Einsatz.von("minuten", String.valueOf(minuten)));
        }
        return sekunden > 0L
                ? sprache.wort(SpielzeitSatz.DAUER_UNTER_EINER_MINUTE, Einsatz.keine())
                : sprache.wort(SpielzeitSatz.DAUER_NICHTS, Einsatz.keine());
    }

    /**
     * Wie {@link #dauer}, aber unter einer Minute in Sekunden.
     *
     * <p>Fuer die laufende Sitzung und die Untaetigkeit: Dort sind es meist Sekunden, und
     * "unter 1 min" waere genau an der Stelle unbrauchbar, an der ein Spieler zusieht, ob
     * die Zahl waechst.
     */
    static String genaueDauer(Sprache sprache, Duration dauer) {
        if (dauer == null) {
            throw new IllegalArgumentException("Ohne Dauer gibt es nichts anzuzeigen");
        }
        long sekunden = Math.max(0L, dauer.toSeconds());
        return sekunden < SEKUNDEN_JE_MINUTE
                ? sprache.wort(SpielzeitSatz.DAUER_SEKUNDEN,
                        Einsatz.von("sekunden", String.valueOf(sekunden)))
                : dauer(sprache, dauer);
    }

    /**
     * Ob gerade gezaehlt wird - die Zeile, an der man Takt und Untaetigkeitsregel am Server
     * ablesen kann.
     *
     * <p>Welcher der zwei Saetze zutrifft, entscheidet der Ausgang des Urteils; wie der
     * gezaehlte endet, entscheidet {@code gesperrt}. Die Saetze selbst stehen in der Datei.
     *
     * <p>Der Zusatz zur Abwesenheit wird mit einem LEEREN Wert eingesetzt, solange die
     * Untaetigkeitsregel an ist - ein leerer WERT und kein fehlender Schluessel. Er kann
     * ausserdem nie an den untaetigen Satz geraten: {@code Spielzeitregel} urteilt nur
     * {@code UNTAETIG}, wenn die Grenze nicht null ist, und {@code untaetigkeitZaehltMit()}
     * ist genau "die Grenze ist null". Darum steht die Verzweigung so und nicht
     * "aufgeraeumt" einheitlich - sonst behauptete die Vorlage eine Kombination, die es
     * nicht gibt.
     *
     * @param ungeschrieben alles Gemessene, was noch nicht in der Ablage steht: die offene
     *                      Spanne UND der Stapel hinter der Sperre. Nicht
     *                      {@code offen.sekunden()}, denn das waere nach einem Fehlschlag
     *                      der Datenbank nur der letzte Takt davon
     * @param gesperrt      ob der Takt gerade nicht schreibt - dann ist der Abstand nicht
     *                      der eingestellte Takt
     */
    private static Component zustandZeile(Sprache sprache, Spielzeitregel.Urteil offen,
                                          Duration ungeschrieben, boolean gesperrt,
                                          Duration untaetig, Zeitwerte zeit) {
        if (offen.ausgang() == Spielzeitregel.Ausgang.UNTAETIG) {
            return sprache.eineZeile(SpielzeitSatz.ZUSTAND_UNTAETIG, Einsatz
                    .von("dauer", genaueDauer(sprache, untaetig))
                    .mit("grenze", String.valueOf(zeit.untaetigMinuten())));
        }
        String abstand = gesperrt
                ? schreibzugWartet(sprache)
                : taktAbstand(sprache, zeit.taktSekunden());
        String abwesenheit = zeit.untaetigkeitZaehltMit()
                ? sprache.wort(SpielzeitSatz.ZUSTAND_ABWESENHEIT,
                        Einsatz.von("pfad", Zeitwerte.PFAD_UNTAETIG))
                : "";
        return sprache.eineZeile(SpielzeitSatz.ZUSTAND_GEZAEHLT, Einsatz
                .von("menge", genaueDauer(sprache, ungeschrieben))
                .mit("abstand", abstand)
                .mit("abwesenheit", abwesenheit));
    }
}
