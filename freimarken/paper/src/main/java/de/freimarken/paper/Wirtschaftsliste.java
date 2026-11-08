package de.freimarken.paper;

import de.freimarken.kern.Berufsverzeichnis;
import de.freimarken.kern.Preisstaffel;
import de.freimarken.kern.Sprungregel;
import de.freimarken.kern.Wirtschaftsstand;
import de.freimarken.kern.Wirtschaftswerte;
import de.freimarken.kern.Zeitwerte;
import java.io.File;
import java.io.IOException;
import java.time.Duration;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;

/**
 * Liest die Stammwerte der Wirtschaft aus der {@code config.yml} - der eine Leser.
 *
 * <p>Nach demselben Muster wie {@link Weltenliste}: reine Dateiarbeit, deshalb ohne Server
 * pruefbar, und mit gesammelten KLAGEN statt Ausnahmen. Ein Tippfehler in einer Zahl darf
 * keinen Serverstart mitreissen; er darf aber auch nicht still gelten.
 *
 * <p>Der Unterschied zwischen den beiden Listen ist genau die Entscheidungsgrundlage fuer
 * das Neuladen:
 * <ul>
 *   <li>KLAGE heisst: Was du getippt hast, gilt NICHT. Beim Start gilt dann die Vorgabe,
 *       beim Neuladen wird gar nichts uebernommen.
 *   <li>BERICHT heisst: Es ist nichts dagegen zu sagen, aber du sollst es gelesen haben -
 *       eine fehlende Zeile, ein Grundpreis von 0, und die Staffel im Klartext.
 * </ul>
 *
 * <p>Was GILT, sagt dieser Leser nur ueber einen neuladbaren Schluessel. Fuer die aus
 * {@link Wirtschaftsstand#NUR_BEIM_START} weiss es allein der Halter: Beim Neuladen werden
 * sie gelesen und gemeldet, aber nicht uebernommen. Diese Trennung sitzt an EINER Stelle,
 * naemlich in {@link #fehltText}, und die Liste dazu an EINER Stelle, naemlich in der
 * Klasse, die es entscheidet.
 *
 * <p><b>Sieben Helfer sind paketweit und nicht privat</b>: {@code betrag},
 * {@code ganzeZahl}, beide {@code esBleibt}, {@code wieGetippt} und {@code fehltText}
 * benutzen auch {@link Berufsliste} und {@link Verdienstliste}. Sie sind nicht abgeschrieben, sondern geteilt -
 * {@code ganzeZahl} faengt den Punkt, der in YAML keine Tausender trennt, und
 * {@code fehltText} entscheidet je Schluessel, ob ein "es gilt" dazu stimmt. Zweimal
 * gebaut wichen die beiden eines Tages ab, und dann klagte die eine Haelfte der Datei
 * anders als die andere. {@code nenneFehlende} bleibt privat: Zwei Berichte, die dieselbe
 * Zahl verschieden nennen, sind schlimmer als zwei Zeilen, also zaehlt Berufsliste selbst.
 *
 * <p>Gelesen wird ROH ueber {@code get}. Bei einer Weltkante faengt das einen fehlenden
 * Rand; bei einem Preis faengt es ein tausendfach zu billiges Grundstueck:
 * {@code getLong} macht aus {@code grundpreis: 25.000} eine 25 und aus {@code "25000"}
 * eine 0 - beides in die Richtung, die sich nicht zurueckholen laesst, weil das
 * entstandene Geld binnen Minuten auf vielen Konten liegt.
 */
final class Wirtschaftsliste {

    /** Alle Schluessel, die diese Klasse liest - fuer die Meldung ueber fehlende Zeilen. */
    private static final List<String> SCHLUESSEL = List.of(
            Wirtschaftswerte.PFAD_GRUNDPREIS,
            Wirtschaftswerte.PFAD_ZUSAMMENLEGUNG,
            Wirtschaftswerte.PFAD_HOECHSTZAHL,
            Wirtschaftswerte.PFAD_GRUPPEN_FELDER,
            Wirtschaftswerte.PFAD_RANG_WERBUNG,
            Wirtschaftswerte.PFAD_STARTGUTHABEN,
            Zeitwerte.PFAD_ZONE,
            Zeitwerte.PFAD_TAKT,
            Zeitwerte.PFAD_UNTAETIG);

    /** Wie viele Preisstufen die Rechenprobe im Protokoll nennt. */
    private static final int STUFEN_IM_BERICHT = 4;

    /** Was in der Datei stand - und woran es gehapert hat. */
    record Ergebnis(Wirtschaftswerte werte, List<String> klagen, List<String> bericht) {
    }

    private Wirtschaftsliste() {
    }

    /**
     * @param wurzel die ganze Konfiguration, darf {@code null} sein - die Schluessel
     *               liegen in fuenf verschiedenen Abschnitten
     * @param gilt   das Berufsverzeichnis, das JETZT gilt. Es gehoert nicht in diesen
     *               Leser und wird deshalb hereingegeben: Die Liste der Berufe ist nicht
     *               neuladbar, aber die Platzzahlen muessen gegen sie geprueft werden -
     *               und zwar gegen die LAUFENDE, nicht gegen die in der Datei. Dieselbe
     *               Trennung wie bei Takt und Untaetigkeitsgrenze
     */
    static Ergebnis ausKonfiguration(ConfigurationSection wurzel,
                                     Berufsverzeichnis gilt) {
        List<String> klagen = new ArrayList<>();
        List<String> bericht = new ArrayList<>();
        if (wurzel == null) {
            // Kein "es gelten die Vorgaben": Fuer die Schluessel aus
            // Wirtschaftsstand.NUR_BEIM_START waere das beim Neuladen falsch - dort gilt
            // weiter der Wert vom Start. Dieser Leser kennt nur die Datei.
            bericht.add("Keine Einstellungen zu lesen - gelesen wurden die Vorgaben des"
                    + " Plugins.");
            return new Ergebnis(Wirtschaftswerte.VORGABE, klagen, bericht);
        }
        nenneFehlende(wurzel, bericht);

        long grundpreis = betrag(wurzel, Wirtschaftswerte.PFAD_GRUNDPREIS, "grundpreis",
                Preisstaffel.VORGABE_GRUNDPREIS, Preisstaffel.HOECHSTER_GRUNDPREIS,
                "Mark", klagen, bericht);
        long jeFeld = betrag(wurzel, Wirtschaftswerte.PFAD_ZUSAMMENLEGUNG,
                "zusammenlegung-je-feld", Preisstaffel.VORGABE_ZUSAMMENLEGUNG_JE_FELD,
                Preisstaffel.HOECHSTE_GEBUEHR, "Mark je Feld", klagen, bericht);
        long startguthaben = betrag(wurzel, Wirtschaftswerte.PFAD_STARTGUTHABEN,
                "startguthaben", Wirtschaftswerte.VORGABE_STARTGUTHABEN,
                Wirtschaftswerte.HOECHSTES_STARTGUTHABEN, "Mark", klagen, bericht);
        int hoechstzahl = anzahl(wurzel, Wirtschaftswerte.PFAD_HOECHSTZAHL,
                Wirtschaftswerte.VORGABE_HOECHSTZAHL, klagen, bericht);
        int gruppenFelder = anzahl(wurzel, Wirtschaftswerte.PFAD_GRUPPEN_FELDER,
                Wirtschaftswerte.VORGABE_GRUPPEN_FELDER, klagen, bericht);
        String werbung = werbung(wurzel, klagen, bericht);
        // Der Takt VOR der Untaetigkeitsgrenze, und einzeln: Die Grenze wird gegen ihn
        // geprueft, weil die beiden Zahlen je einzeln im Bereich liegen und zusammen
        // trotzdem gemessene Spielzeit vernichten koennen. Der Konstruktor von Zeitwerte
        // verweigert das Paar - und dieser Leser darf nicht werfen.
        ZoneId zone = zone(wurzel, klagen, bericht);
        long taktSekunden = taktSekunden(wurzel, klagen, bericht);
        long untaetigMinuten = untaetigMinuten(wurzel, taktSekunden, klagen, bericht);
        Zeitwerte zeit = new Zeitwerte(zone, Duration.ofSeconds(taktSekunden),
                Duration.ofMinutes(untaetigMinuten));

        // Die Berufe liest ein eigener Leser: Er hat eigene Schranken, eine eigene
        // Querpruefung gegen die Liste und einen eigenen Zaehlsatz fuer fehlende Zeilen.
        // Seine Klagen und sein Bericht kommen hier dazu, denn der Aufrufer soll EINE
        // Antwort bekommen und nicht zwei.
        Berufsliste.Werte beruf = Berufsliste.werteAus(wurzel, gilt);
        klagen.addAll(beruf.klagen());
        bericht.addAll(beruf.bericht());

        // Der Verdienst ebenso: eigene Schranken, eigene Querpruefung gegen das
        // Verzeichnis, eigener Zaehlsatz - und EINE Antwort fuer den Aufrufer.
        Verdienstliste.Ergebnis verdienst = Verdienstliste.ausKonfiguration(wurzel, gilt);
        klagen.addAll(verdienst.klagen());
        bericht.addAll(verdienst.bericht());
        // Der Bundesmarkt braucht das Berufsverzeichnis NICHT: Was der Bund kauft, haengt
        // an keinem Beruf - jeder darf alles verkaufen, was im Sortiment steht.
        Marktliste.Ergebnis markt = Marktliste.ausKonfiguration(wurzel);
        klagen.addAll(markt.klagen());
        bericht.addAll(markt.bericht());
        // Die Messstelle braucht es ebenso nicht: Sie zaehlt Lebenszeichen und fragt
        // weder nach einem Beruf noch nach einem Lohn. Dass sie trotzdem einen eigenen
        // Leser hat, liegt an ihrem Wertepaar - die verlangte Artenzahl wird gegen die
        // Laenge der Artenliste geprueft.
        Messliste.Ergebnis messung = Messliste.ausKonfiguration(wurzel);
        klagen.addAll(messung.klagen());
        bericht.addAll(messung.bericht());
        // Der Tagesbonus ebenso: eigene Schranken, ein eigenes Wertepaar (der
        // Hoechstbetrag wird gegen den Grundbetrag geprueft) und eine eigene Rechenprobe,
        // die die Staffel im Klartext nennt.
        Bonusliste.Ergebnis bonus = Bonusliste.ausKonfiguration(wurzel);
        klagen.addAll(bonus.klagen());
        bericht.addAll(bonus.bericht());
        // Die Ladenmiete zuletzt: eigene Schranken, ein eigenes Wertepaar (die Kaution
        // wird gegen die Tagesmiete geprueft) und eine eigene Rechenprobe, die den
        // vollen Mietzeitraum ausrechnet. Die EINHEITEN im selben Abschnitt liest
        // Ladenliste - sie braucht die Weltgrenzen, die der Aufrufer hat, und dieser
        // Leser nicht.
        Mietliste.Ergebnis laeden = Mietliste.ausKonfiguration(wurzel);
        klagen.addAll(laeden.klagen());
        bericht.addAll(laeden.bericht());

        // Und der Handel: ein eigener Abschnitt (handel:), eine eigene Schranke fuer
        // beide Gebuehren und ein Wort, das auf ein Schild passen muss. Ein eigener
        // Leser und nicht drei Zeilen hier, aus demselben Grund wie bei der Ladenmiete:
        // Seine drei Zeilen haben eine gemeinsame Rechenprobe, und die gehoert zu ihnen.
        Shopliste.Ergebnis handel = Shopliste.ausKonfiguration(wurzel);
        klagen.addAll(handel.klagen());
        bericht.addAll(handel.bericht());

        Preisstaffel preise = new Preisstaffel(grundpreis, jeFeld);
        if (grundpreis == 0L) {
            bericht.add(Wirtschaftswerte.PFAD_GRUNDPREIS + " ist 0 - jede weitere Parzelle"
                    + " ist damit gratis, und die Bremse gegen Flaechenhorten ist aus.");
        }
        if (jeFeld == 0L) {
            bericht.add(Wirtschaftswerte.PFAD_ZUSAMMENLEGUNG + " ist 0 - Zusammenlegen"
                    + " kostet nichts.");
        }
        // Die Rechenprobe: Die einzige Schranke gegen den Wert, der im erlaubten Bereich
        // liegt und trotzdem falsch ist. Wer "2. Parzelle 25" liest, sieht den Vertipper,
        // den keine Zahlengrenze sieht.
        bericht.add("Preisstaffel: " + preise.alsText(STUFEN_IM_BERICHT)
                + " - Gruppe aus " + gruppenFelder + " Feldern "
                + preise.zusammenlegungsGebuehr(Math.max(gruppenFelder, 2)));
        // Das Startguthaben steht hier mit ABSICHT nicht. Es stand einmal hier, und im
        // Spiel sah man die Folge: zwei Zeilen untereinander nannten denselben
        // Schluessel mit derselben Zahl - "in der Datei: 50000" und darunter "50000 -
        // wird nur beim Start gelesen". Dieser Leser kennt nur die Datei; was GILT, sagt
        // der Aufrufer, beim Start das Protokoll und beim Neuladen der Block darunter.
        // Dass eine fehlende oder unbrauchbare Zeile genannt wird, besorgen ganzeZahl
        // und betrag ohnehin.

        // Die Tageswechselzeile zur Zone steht mit ABSICHT nicht im Bericht - sie kommt aus
        // Tagesgrenze.tageswechselText und wird beim Start vom Aufrufer protokolliert.
        // Derselbe Grund wie beim Startguthaben: Dieser Leser kennt nur die Datei, die Zone
        // ist aber nicht neuladbar. Beim Neuladen nennte eine Zeile hier die Zone aus der
        // Datei, waehrend eine andere darunter sagt, dass die alte weiter gilt.
        return new Ergebnis(new Wirtschaftswerte(preise, startguthaben, hoechstzahl,
                gruppenFelder, werbung, zeit, beruf.werte(), verdienst.werte(),
                markt.werte(), messung.werte(), bonus.werte(), laeden.werte(),
                handel.werte()), klagen, bericht);
    }

    /**
     * Ein Geldbetrag: roh gelesen, am Typ geprueft, im Bereich geprueft und gegen einen
     * grossen Sprung gesichert.
     *
     * @param name    der Schluessel im Abschnitt {@code sprung-bestaetigt}
     * @param vorgabe der ausgelieferte Wert - zugleich der Rueckfall UND der Vergleichswert
     *                fuer den Sprung
     */
    static long betrag(ConfigurationSection wurzel, String pfad, String name,
                       long vorgabe, long hoechstens, String einheit,
                       List<String> klagen, List<String> bericht) {
        Long gelesen = ganzeZahl(wurzel, pfad, vorgabe, klagen, bericht);
        if (gelesen == null) {
            return vorgabe;
        }
        long wert = gelesen;
        if (wert < 0 || wert > hoechstens) {
            klagen.add(pfad + " ist " + wert + " - verworfen. Erlaubt sind 0 bis "
                    + hoechstens + " " + einheit + "; darueber laeuft die Rechnung ueber,"
                    + " und ein uebergelaufener Preis ist NEGATIV - also eine Gutschrift"
                    + " beim Kauf. " + esBleibt(vorgabe));
            return vorgabe;
        }
        if (wert != vorgabe && Sprungregel.grosserSprung(vorgabe, wert)) {
            String schluessel = Wirtschaftswerte.PFAD_BESTAETIGT + "." + name;
            if (!Sprungregel.bestaetigt(wurzel.getString(schluessel), vorgabe, wert)) {
                klagen.add(pfad + " ist " + wert + ", ausgeliefert sind " + vorgabe
                        + " - das ist mehr als Faktor 2, und bei einem Geldwert braucht"
                        + " das eine zweite Bestaetigung: Was zu billig verkauft oder zu"
                        + " reichlich ausgegeben wurde, liegt binnen Minuten auf vielen"
                        + " Konten und ist nur mit einer Ruecksicherung des ganzen"
                        + " Servers zurueckzuholen. " + esBleibt(vorgabe) + " Fuer den"
                        + " Sprung: " + schluessel + ": \""
                        + Sprungregel.sprungText(vorgabe, wert) + "\"");
                return vorgabe;
            }
            // Kein "gilt": Fuer kasse.startguthaben waere das beim Neuladen falsch, denn
            // uebernimm() laesst diesen Wert ausdruecklich stehen - zwei Zeilen derselben
            // Antwort widersprachen sich. Was gilt, sagt der Aufrufer; hier steht nur,
            // dass die Bestaetigung zur Datei passt.
            bericht.add("Bestaetigung erkannt: " + schluessel + " deckt " + pfad + " "
                    + Sprungregel.sprungText(vorgabe, wert) + ".");
        }
        return wert;
    }

    /** Eine Obergrenze: dieselbe Pruefung, aber als {@code int} und mindestens 1. */
    private static int anzahl(ConfigurationSection wurzel, String pfad, int vorgabe,
                              List<String> klagen, List<String> bericht) {
        Long gelesen = ganzeZahl(wurzel, pfad, vorgabe, klagen, bericht);
        if (gelesen == null) {
            return vorgabe;
        }
        long wert = gelesen;
        // Geprueft wird der long und NICHT der verkuerzte int: (int) -2147483649 ist
        // 2147483647, und eine negative Zahl gaelte so als unbegrenzte Parzellenzahl.
        if (!Wirtschaftswerte.brauchbareAnzahl(wert)) {
            klagen.add(pfad + " ist " + wert + " - verworfen. Erlaubt ist eine ganze Zahl"
                    + " von 1 bis " + Integer.MAX_VALUE + "; eine 0 hiesse, niemand darf"
                    + " eine Parzelle. " + esBleibt(vorgabe)
                    + " Dieser Wert ist ohnehin nur die Untergrenze - mehr erlauben die"
                    + " Rechte.");
            return vorgabe;
        }
        return (int) wert;
    }

    /**
     * Die Rangwerbung: Text, leer erlaubt.
     *
     * <p>Keine Zahl und kein Bereich - die einzige Falle ist eine Zeile, die gar kein Text
     * ist. Eine Liste an dieser Stelle kommt als etwas an, dessen {@code toString} dem
     * Spieler ins Gesicht faellt.
     */
    private static String werbung(ConfigurationSection wurzel, List<String> klagen,
                                  List<String> bericht) {
        String pfad = Wirtschaftswerte.PFAD_RANG_WERBUNG;
        Object roh = wurzel.get(pfad);
        if (roh == null) {
            bericht.add(fehltText(pfad, "\"" + Wirtschaftswerte.VORGABE_RANG_WERBUNG + "\""));
            return Wirtschaftswerte.VORGABE_RANG_WERBUNG;
        }
        if (!(roh instanceof String text)) {
            klagen.add(pfad + " ist kein Text (" + wieGetippt(roh) + ") - verworfen."
                    + " Es bleibt bei dem Text, der bisher galt (ausgeliefert: \""
                    + Wirtschaftswerte.VORGABE_RANG_WERBUNG + "\"). Mehrere Zeilen"
                    + " gehoeren in Anfuehrungszeichen, nicht in eine Liste.");
            return Wirtschaftswerte.VORGABE_RANG_WERBUNG;
        }
        if (text.isBlank()) {
            bericht.add(pfad + " ist leer - es wird kein Hinweis angehaengt.");
        }
        return text;
    }

    /**
     * Die Tagesgrenze als Zonenname - nach dem Muster von {@link #werbung}: roh gelesen, am
     * Typ geprueft, Klage statt Wurf.
     *
     * <p>Angenommen wird nur ein Name aus {@link ZoneId#getAvailableZoneIds()}. Diese eine
     * Regel weist zwei verschiedene Eingaben ab: den Tippfehler ({@code Europe/Berln}) und
     * den festen Versatz ({@code UTC+2}). Der Versatz ist die gefaehrlichere von beiden,
     * denn {@code ZoneId.of("UTC+2")} nimmt ihn an - und ein Versatz kennt keine Sommerzeit.
     * Die Tagesgrenze laege damit vom letzten Sonntag im Maerz an um 01:00 Ortszeit und
     * bliebe den ganzen Sommer dort. {@code UTC} selbst steht in der Liste und ist erlaubt;
     * dagegen hilft keine Pruefung, sondern nur die Tageswechselzeile im Startprotokoll.
     *
     * <p>Kein {@code try/catch} um {@code ZoneId.of}: Fuer einen Namen aus dieser Liste
     * wirft es nicht. Die Liste IST die Pruefung, und ein gefangener Wurf daneben waere eine
     * zweite Regel fuer dieselbe Frage.
     */
    private static ZoneId zone(ConfigurationSection wurzel, List<String> klagen,
                               List<String> bericht) {
        String pfad = Zeitwerte.PFAD_ZONE;
        Object roh = wurzel.get(pfad);
        if (roh == null) {
            bericht.add(fehltText(pfad, Zeitwerte.VORGABE_ZONE.getId()));
            return Zeitwerte.VORGABE_ZONE;
        }
        if (!(roh instanceof String text)) {
            klagen.add(pfad + " ist kein Text (" + wieGetippt(roh) + ") - verworfen. "
                    + esBleibt(Zeitwerte.VORGABE_ZONE.getId()));
            return Zeitwerte.VORGABE_ZONE;
        }
        String name = text.trim();
        if (!ZoneId.getAvailableZoneIds().contains(name)) {
            klagen.add(pfad + " ist \"" + name + "\" - verworfen. Erlaubt ist ein Zonenname"
                    + " wie Europe/Berlin oder UTC. " + esBleibt(Zeitwerte.VORGABE_ZONE.getId())
                    + " Ein fester Versatz wie \"UTC+2\" wird ebenfalls abgewiesen: Er kennt"
                    + " keine Sommerzeit, und die Tagesgrenze laege vom letzten Sonntag im"
                    + " Maerz an um 01:00 Ortszeit.");
            return Zeitwerte.VORGABE_ZONE;
        }
        return ZoneId.of(name);
    }

    /**
     * Der Taktabstand in Sekunden.
     *
     * <p>Geprueft wird die ROH gelesene Zahl an {@link Zeitwerte#brauchbarerTakt}, nicht am
     * Konstruktor von {@link Zeitwerte}: Der wirft, und dieser Leser darf nicht werfen. Dass
     * beide dieselbe Regel benutzen, ist der Grund, warum sie oeffentlich dort steht.
     */
    private static long taktSekunden(ConfigurationSection wurzel, List<String> klagen,
                                     List<String> bericht) {
        String pfad = Zeitwerte.PFAD_TAKT;
        Long gelesen = ganzeZahl(wurzel, pfad, Zeitwerte.VORGABE_TAKT_SEKUNDEN, klagen,
                bericht);
        if (gelesen == null) {
            return Zeitwerte.VORGABE_TAKT_SEKUNDEN;
        }
        if (!Zeitwerte.brauchbarerTakt(gelesen)) {
            klagen.add(pfad + " ist " + gelesen + " - verworfen. Erlaubt sind "
                    + Zeitwerte.KUERZESTER_TAKT_SEKUNDEN + " bis "
                    + Zeitwerte.LAENGSTER_TAKT_SEKUNDEN + " Sekunden. "
                    + esBleibt(Zeitwerte.VORGABE_TAKT_SEKUNDEN) + " Darunter schreibt der"
                    + " Takt oefter, als jede Anzeige unterscheidet; darueber kostet ein"
                    + " Absturz mehr als zehn Minuten je anwesendem Spieler.");
            return Zeitwerte.VORGABE_TAKT_SEKUNDEN;
        }
        return gelesen;
    }

    /**
     * Die Untaetigkeitsgrenze in Minuten; 0 ist erlaubt und heisst: Abwesenheit zaehlt mit.
     *
     * <p>Zwei Pruefungen und nicht eine: der eigene Bereich, und das VERHAELTNIS zum Takt.
     * Beide Zahlen koennen einzeln erlaubt sein und zusammen Spielzeit vernichten - die
     * Grenze ist eine Stufe und kein Schnitt, also faellt bei {@code takt-sekunden: 600}
     * und {@code untaetig-minuten: 1} der GANZE Takt weg, sobald der Spieler die letzte
     * Minute stillsteht. 540 aktiv gespielte Sekunden, ohne Protokollzeile, bei jedem Takt
     * erneut. Die Regel dazu steht EINMAL, in
     * {@link Zeitwerte#brauchbareGrenzeZumTakt} - derselbe Satz, den der Konstruktor
     * fordert; hier wird er auf den roh gelesenen Zahlen gefragt, damit geklagt und
     * zurueckgefallen wird, statt den Konstruktor werfen zu lassen.
     *
     * <p>Geprueft wird gegen den Takt AUS DER DATEI, denn mehr kennt dieser Leser nicht.
     * Beim Neuladen kann der LAUFENDE Takt ein anderer sein (er wird nur beim Start
     * gelesen); dagegen prueft {@link Wirtschaftsstand#uebernimm} ein zweites Mal und laesst
     * die alte Grenze stehen.
     *
     * @param taktSekunden der Takt, wie er aus derselben Datei gelesen wurde - schon mit
     *                     seinem eigenen Rueckfall, also immer ein brauchbarer Wert
     */
    private static long untaetigMinuten(ConfigurationSection wurzel, long taktSekunden,
                                        List<String> klagen, List<String> bericht) {
        String pfad = Zeitwerte.PFAD_UNTAETIG;
        Long gelesen = ganzeZahl(wurzel, pfad, Zeitwerte.VORGABE_UNTAETIG_MINUTEN, klagen,
                bericht);
        if (gelesen == null) {
            return Zeitwerte.VORGABE_UNTAETIG_MINUTEN;
        }
        if (!Zeitwerte.brauchbareUntaetigkeit(gelesen)) {
            klagen.add(pfad + " ist " + gelesen + " - verworfen. Erlaubt sind 0 bis "
                    + Zeitwerte.LAENGSTE_UNTAETIGKEIT_MINUTEN + " Minuten; 0 heisst"
                    + " ausdruecklich, dass Abwesenheit mitzaehlt. "
                    + esBleibt(Zeitwerte.VORGABE_UNTAETIG_MINUTEN));
            return Zeitwerte.VORGABE_UNTAETIG_MINUTEN;
        }
        if (!Zeitwerte.brauchbareGrenzeZumTakt(taktSekunden, gelesen)) {
            // Der Rueckfall ist hier IMMER gueltig, und das ist nachgerechnet: Die Vorgabe
            // sind 10 Minuten, also 600 Sekunden, und das deckt auch den laengsten
            // erlaubten Takt (600 s). Es braucht also keine zweite Rettung.
            klagen.add(pfad + " ist " + gelesen + " - verworfen. Zu einem Takt von "
                    + taktSekunden + " s ist das zu kurz: Die Grenze ist eine STUFE und"
                    + " kein Schnitt, also faellt der GANZE Takt weg, sobald sie reisst -"
                    + " auch die aktiv gespielte Zeit darin, und zwar ohne Protokollzeile. "
                    + esBleibt(Zeitwerte.VORGABE_UNTAETIG_MINUTEN) + " Zu diesem Takt"
                    + " passen mindestens " + Math.ceilDiv(taktSekunden, 60L) + " Minuten;"
                    + " 0 schaltet die Regel ganz ab, oder " + Zeitwerte.PFAD_TAKT
                    + " kuerzen.");
            return Zeitwerte.VORGABE_UNTAETIG_MINUTEN;
        }
        if (gelesen == 0L) {
            bericht.add(pfad + " ist 0 - Abwesenheit zaehlt mit. Wer ueber Nacht im"
                    + " Startbildschirm stehen bleibt, sammelt Spielzeit.");
        }
        return gelesen;
    }

    /**
     * Roh lesen und am TYP pruefen - oder {@code null}, wenn daraus keine Zahl wird.
     *
     * <p>Am Typ und nicht am Nachkommarest: YAML macht aus {@code 25.000} eine 25.0, und
     * die ist ganzzahlig. Gemeint waren 25000 - die Kommentare dieser Datei schreiben
     * Tausender selbst mit Punkt. Eine Fliesskommazahl entsteht in YAML nur dort, wo ein
     * Punkt oder ein Exponent steht; als Betrag war sie nie gemeint.
     */
    static Long ganzeZahl(ConfigurationSection wurzel, String pfad, long vorgabe,
                          List<String> klagen, List<String> bericht) {
        Object roh = wurzel.get(pfad);
        if (roh == null) {
            bericht.add(fehltText(pfad, String.valueOf(vorgabe)));
            return null;
        }
        if (!(roh instanceof Number zahl)) {
            klagen.add(pfad + " ist keine Zahl (" + wieGetippt(roh) + ") - verworfen. "
                    + esBleibt(vorgabe) + " Anfuehrungszeichen machen aus einer Zahl Text,"
                    + " und getLong liest daraus stillschweigend eine 0.");
            return null;
        }
        double wert = zahl.doubleValue();
        if (zahl instanceof Double || zahl instanceof Float || wert != Math.floor(wert)) {
            klagen.add(pfad + " ist keine ganze Zahl (" + roh + ") - verworfen. "
                    + esBleibt(vorgabe) + " Ein Punkt trennt in YAML keine Tausender,"
                    + " sondern die Nachkommastellen: aus 25.000 wird 25.");
            return null;
        }
        return zahl.longValue();
    }

    /**
     * Was statt des verworfenen Wertes gilt - in einem Satz, der auf BEIDEN Wegen stimmt.
     *
     * <p>Vorher stand hier "Es gilt 25000". Beim Start war das richtig, beim Neuladen
     * nicht: Dort bleibt der laufende Wert stehen, und der kann 40.000 sein. Dann
     * widersprachen sich zwei Zeilen derselben Antwort. Dieser Leser weiss nicht, was
     * gerade gilt - er kennt nur die Datei und die Vorgabe -, also sagt er es auch nicht.
     * Was gilt, sagt der Aufrufer: beim Start die Rechenprobe, beim Neuladen die Zeile
     * "Es gilt unveraendert weiter".
     */
    static String esBleibt(long vorgabe) {
        return esBleibt(String.valueOf(vorgabe));
    }

    /** Dasselbe fuer einen Wert, der keine Zahl ist - die Zone. */
    static String esBleibt(String vorgabe) {
        return "Es bleibt bei dem Wert, der bisher galt (ausgeliefert: " + vorgabe + ").";
    }

    /**
     * Den gelesenen Wert so zeigen, wie er in der Datei steht.
     *
     * <p>Ein Text in Anfuehrungszeichen kam im Protokoll als {@code (10000)} an - also
     * genau so, wie eine Zahl aussieht. Der Unterschied, um den die ganze Klage geht,
     * war damit unsichtbar.
     */
    static String wieGetippt(Object roh) {
        return roh instanceof String text ? "\"" + text + "\"" : String.valueOf(roh);
    }

    /**
     * Eine Zeile, die das Plugin mitbringt und die Datei nicht hat.
     *
     * <p>Zwei Saetze, und zwar genau nach {@link Wirtschaftsstand#NUR_BEIM_START} getrennt:
     * <ul>
     *   <li>Ein NEULADBARER Schluessel bekommt wieder "es gilt 4". Das ist richtig und auf
     *       beiden Wegen: Beim Start wirkt der Rueckfall, beim Neuladen wird er uebernommen.
     *       Eine Zeit lang stand hier fuer alle neun nur, was ausgeliefert ist - damit
     *       erfuhr der Betreiber bei fehlendem {@code parzellen.hoechstzahl} in der ganzen
     *       Antwort nicht mehr, welche Hoechstzahl gilt: {@code uebernimm} nennt nur
     *       GEAENDERTE Zeilen, und im Block darunter steht die Hoechstzahl nicht.</li>
     *   <li>Ein Schluessel aus {@code NUR_BEIM_START} bekommt kein "gilt". Dort wird gelesen
     *       und gemeldet, aber NICHT uebernommen - es gilt weiter der Wert vom Start, und
     *       welcher das ist, weiss dieser Leser nicht. Er verweist auf den Block, der es
     *       weiss. Derselbe Grund wie bei {@link #esBleibt}.</li>
     * </ul>
     *
     * @param wert der ausgelieferte Wert - fuer einen neuladbaren Schluessel zugleich der,
     *             der gilt
     */
    static String fehltText(String pfad, String wert) {
        String nachsatz = " Eine vorhandene Datei wird beim Update nie ueberschrieben; die"
                + " Zeile muss von Hand hinein, sonst ist der Wert nicht einstellbar.";
        if (Wirtschaftsstand.NUR_BEIM_START.contains(pfad)) {
            return pfad + " steht nicht in deiner config.yml - ausgeliefert ist " + wert
                    + ". Diese Zeile wird ohnehin nur beim Start gelesen; was der Server"
                    + " JETZT benutzt, steht im Block \"Nur beim Start gelesen\"."
                    + nachsatz;
        }
        return pfad + " steht nicht in deiner config.yml - es gilt " + wert + "."
                + nachsatz;
    }

    /** Der Name der Einstellungsdatei - einmal, damit kein zweiter Name entsteht. */
    static final String DATEI = "config.yml";

    /**
     * Ob die Einstellungsdatei ueberhaupt gelesen werden kann - die Luecke, die kein
     * Pfad in dieser Klasse sieht.
     *
     * <p>{@code reloadConfig()} verschluckt jeden Lesefehler: Bei kaputtem YAML loggt
     * Bukkit selbst eine Zeile und liefert eine LEERE Konfiguration, bei fehlender Datei
     * schweigt es ganz. Danach haengt es die Vorgaben aus der Jar an - und weil
     * {@code get} auf die Vorgabe zurueckfaellt, liest dieser Leser brav lauter
     * ausgelieferte Werte, findet nichts zu klagen und meldet Erfolg. Aus einem
     * Tabulator in der Datei wurde so ein gruenes "Uebernommen: grundpreis
     * 200000 -&gt; 25000" - und ein zu billiger Preis macht Mark, die nur eine
     * Ruecksicherung des ganzen Servers zurueckholt.
     *
     * <p>Deshalb wird die Datei hier ein zweites Mal geladen, ohne Vorgaben und mit
     * gesehener Ausnahme. Das kostet eine Dateiarbeit je Neuladen und ist der einzige
     * Weg, "Datei unlesbar" von "Datei leer" zu trennen.
     *
     * @param ordner der Datenordner des Plugins
     * @return die Meldung im Klartext, oder {@code null}, wenn die Datei gelesen wurde
     */
    static String lesefehler(File ordner) {
        File datei = new File(ordner, DATEI);
        if (!datei.isFile()) {
            return DATEI + " liegt nicht in " + ordner.getPath() + " - ohne Datei liefert"
                    + " Bukkit stumm die ausgelieferten Werte, und die waeren hier als"
                    + " deine gemeldet.";
        }
        try {
            new YamlConfiguration().load(datei);
            return null;
        } catch (IOException | InvalidConfigurationException fehler) {
            // SnakeYAML nennt die Quelle 'reader' - wer das liest, sucht nach einer Datei
            // dieses Namens. Die Zeilennummer darin ist das Brauchbare an der Meldung.
            return DATEI + " ist nicht lesbar: "
                    + String.join(" ", String.valueOf(fehler.getMessage())
                            .lines().map(String::trim).toList())
                            .replace("'reader'", DATEI)
                    + " - solange das so ist, kennt der Server deine Werte nicht.";
        }
    }

    /**
     * Nennt jede Zeile, die das Plugin mitbringt, die aber in der Datei des Betreibers
     * nicht steht.
     *
     * <p>Am 02.11.2026 gemessen: Auf dem Probeserver fehlten 16 von 42 Schluesseln, und
     * alle wirkten trotzdem - {@code get} fuer einen einzelnen Schluessel holt die Vorgabe
     * stumm aus der Jar. Genau das ist die Falle: Was der Betreiber nicht sieht, kann er
     * nicht aendern, und Plan 12a.6 verlangt, dass alles Anpassbare anpassbar ist. Kein
     * Grund zur Klage - es wirkt ja das Richtige - aber einer, es zu lesen.
     *
     * <p>Diese Zeile nennt ALLE fehlenden Schluessel auf einmal und behauptet deshalb von
     * KEINEM, dass er gelte. Hier stand "Sie gelten aus den Vorgaben des Plugins", und das
     * war fuer {@code zeit.zone} und {@code zeit.takt-sekunden} beim Neuladen falsch: In
     * derselben Antwort stand zwei Zeilen tiefer "zeit.zone: es gilt weiter
     * America/New_York", also zwei verschiedene Tagesgrenzen. Was fuer den einzelnen
     * Schluessel gilt, sagt {@link #fehltText} - dort ist es je Schluessel entschieden und
     * nicht fuer alle neun zusammen geraten.
     */
    private static void nenneFehlende(ConfigurationSection wurzel, List<String> bericht) {
        List<String> fehlen = new ArrayList<>();
        for (String pfad : SCHLUESSEL) {
            if (!wurzel.contains(pfad, true)) {
                fehlen.add(pfad);
            }
        }
        if (!fehlen.isEmpty()) {
            bericht.add(fehlen.size() + " von " + SCHLUESSEL.size() + " Wirtschaftszeilen"
                    + " stehen nicht in deiner config.yml: " + String.join(", ", fehlen)
                    + ". Sichtbar und aenderbar sind sie erst, wenn sie in der Datei"
                    + " stehen; was das Plugin ohne die Zeile mitbringt, nennt die eigene"
                    + " Zeile zu jedem Schluessel.");
        }
    }
}
