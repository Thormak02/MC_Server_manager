package de.freimarken.paper;

import de.freimarken.kern.Freistadtwerte;
import de.freimarken.kern.Wechselwerte;
import java.util.ArrayList;
import java.util.List;
import org.bukkit.configuration.ConfigurationSection;

/**
 * Liest die Wechselstube aus der {@code config.yml} - zwei Kurse und den Deckel.
 *
 * <p>Nach demselben Muster wie {@link Marktliste}: reine Dateiarbeit, gesammelte KLAGEN
 * statt Ausnahmen, und der Rueckfall steht im Programm. Ein Tippfehler in einem Kurs darf
 * keinen Serverstart mitreissen; er darf aber auch nicht still gelten.
 *
 * <p><b>Das PAAR ist hier das Besondere.</b> Jeder der zwei Kurse kann fuer sich im
 * erlaubten Bereich liegen, und zusammen trotzdem Geld erzeugen: Bringt der Rueckweg mehr,
 * als der Hinweg kostet, ist jeder Rundlauf ein Gewinn. Darum prueft dieser Leser die
 * beiden GEGENEINANDER - und faellt im Streitfall auf BEIDE Vorgaben zurueck. Nur eine zu
 * verwerfen koennte das Paar wieder brauchbar machen und waere trotzdem nicht, was der
 * Betreiber getippt hat.
 */
final class Wechselliste {

    /** Alle Schluessel, die diese Klasse liest - fuer die Meldung ueber fehlende Zeilen. */
    private static final List<String> SCHLUESSEL = List.of(
            Wechselwerte.PFAD_HIN,
            Wechselwerte.PFAD_ZURUECK,
            Wechselwerte.PFAD_DECKEL);

    /** Was in der Datei stand - und woran es gehapert hat. */
    record Ergebnis(Wechselwerte werte, List<String> klagen, List<String> bericht) {
    }

    private Wechselliste() {
    }

    /**
     * Der ganze Abschnitt {@code wechsel:}.
     *
     * @param wurzel die ganze Konfiguration, darf {@code null} sein
     */
    static Ergebnis ausKonfiguration(ConfigurationSection wurzel) {
        List<String> klagen = new ArrayList<>();
        List<String> bericht = new ArrayList<>();
        if (wurzel == null) {
            return new Ergebnis(Wechselwerte.VORGABE, klagen, bericht);
        }
        nenneFehlende(wurzel, bericht);
        // DIE SCHLUESSEL DER SPRUNGBESTAETIGUNG STEHEN HIER UND NICHT IM HELFER, und das
        // ist gemessen [20.11.2026]: Tafel 31am liest die GESTALT des Aufrufs
        // (betrag(wurzel, ... "schluessel")) und haelt sie gegen den Abschnitt
        // sprung-bestaetigt der ausgelieferten config.yml. Durch einen Parameter gereicht,
        // waren "wechsel-hin" und "wechsel-zurueck" fuer sie unsichtbar - die Probe war
        // rot und meldete, in der Datei stehe eine Zeile zu viel. Der Waechter sieht nur
        // diese eine Gestalt; also behaelt sie jeder Geldwert.
        long hin = kurs(Wechselwerte.PFAD_HIN, Wirtschaftsliste.betrag(wurzel,
                        Wechselwerte.PFAD_HIN, "wechsel-hin", Wechselwerte.VORGABE_HIN,
                        Wechselwerte.HOECHSTER_KURS, "Mark", klagen, bericht),
                Wechselwerte.VORGABE_HIN, klagen);
        long zurueck = kurs(Wechselwerte.PFAD_ZURUECK, Wirtschaftsliste.betrag(wurzel,
                        Wechselwerte.PFAD_ZURUECK, "wechsel-zurueck",
                        Wechselwerte.VORGABE_ZURUECK, Wechselwerte.HOECHSTER_KURS, "Mark",
                        klagen, bericht),
                Wechselwerte.VORGABE_ZURUECK, klagen);
        if (zurueck > hin) {
            // DIE PAARPRUEFUNG, und sie ist der Grund, warum dieser Leser mehr tut als
            // drei Zahlen zu holen: 1.000 Mark hinein, eine Goldmark, 1.200 Mark heraus -
            // beliebig oft, jede Buchung richtig, und die Summenprobe des Buchungsbuchs
            // bestaetigt jeden Stand. Der Konstruktor von Wechselwerte verweigert das
            // Paar; dieser Leser darf nicht werfen, also klagt er und nimmt beide
            // Vorgaben.
            klagen.add(Wechselwerte.PFAD_ZURUECK + " ist " + zurueck + " und "
                    + Wechselwerte.PFAD_HIN + " ist " + hin + " - verworfen, BEIDE."
                    + " Damit waere jeder Rundlauf ein Gewinn von " + (zurueck - hin)
                    + " Mark je Goldmark, beliebig oft wiederholbar. Der Rueckweg darf"
                    + " nie mehr bringen als der Hinweg kostet. "
                    + Wirtschaftsliste.esBleibt(Wechselwerte.VORGABE_HIN) + " fuer den"
                    + " Hinweg und "
                    + Wirtschaftsliste.esBleibt(Wechselwerte.VORGABE_ZURUECK)
                    + " fuer den Rueckweg.");
            hin = Wechselwerte.VORGABE_HIN;
            zurueck = Wechselwerte.VORGABE_ZURUECK;
        }
        long deckel = deckel(wurzel, klagen, bericht);
        Wechselwerte werte = new Wechselwerte(hin, zurueck, deckel);
        // DIE RECHENPROBE: die einzige Schranke gegen Zahlen, die im erlaubten Bereich
        // liegen und trotzdem falsch sind. Wer "eine Goldmark kostet 10 Mark" liest, sieht
        // den Vertipper, den keine Zahlengrenze sieht - und wer liest, dass eine Freistadt
        // auf diesem Weg in zwei Tagen zu haben ist, sieht, dass er die Zusage des Plans
        // gerade abgeschafft hat.
        bericht.add(rechenprobe(werte));
        return new Ergebnis(werte, klagen, bericht);
    }

    /**
     * Die Zeile, die die drei Zahlen im Zusammenhang nennt.
     *
     * <p>Eigene Methode, damit die Probe sie ohne Datei bekommt - dasselbe Muster wie
     * {@code Rangliste.rechenprobe}.
     */
    static String rechenprobe(Wechselwerte werte) {
        StringBuilder satz = new StringBuilder("Wechselstube: 1 Goldmark kostet "
                + werte.markJeGoldmark() + " Mark und bringt " + werte.markZurueck()
                + " Mark zurueck");
        if (!werte.rueckwegOffen()) {
            return satz.append(" - aber der Rueckweg ist ZU (")
                    .append(Wechselwerte.PFAD_DECKEL).append(": ")
                    .append(Wechselwerte.RUECKWEG_ZU)
                    .append("). Goldmark laesst sich kaufen und nicht zurueckwechseln.")
                    .toString();
        }
        long jeTag = werte.nochMoeglichHeute(0L);
        satz.append(" - hoechstens ").append(jeTag).append(" Goldmark am Tag (")
                .append(werte.tagesdeckel()).append(" Mark). ");
        // Gerechnet gegen den AUSGELIEFERTEN Gruendungspreis und nicht gegen den
        // eingestellten: Die Zusage des Plans (mindestens 40 Tage) haengt an diesem Paar
        // von Zahlen, und der Preis steht in einem anderen Abschnitt mit einem anderen
        // Leser. Wer beides verstellt, liest zwei Zeilen und rechnet selbst.
        long tage = Freistadtwerte.VORGABE_GRUENDUNGSPREIS / werte.tagesdeckel();
        satz.append("Eine Freistadt zu ").append(Freistadtwerte.VORGABE_GRUENDUNGSPREIS)
                .append(" Mark braucht auf diesem Weg ").append(tage)
                .append(" Tage; der Plan nennt 40.");
        return satz.toString();
    }

    /**
     * Die Schranke eines Kurses - gelesen hat ihn schon der Aufrufer.
     *
     * <p>Das LESEN steht beim Aufrufer und nicht hier, mit dem Grund, der dort
     * aufgeschrieben ist: Tafel 31am sieht den Schluessel der Sprungbestaetigung nur in
     * der Gestalt {@code betrag(wurzel, ... "schluessel")}. Was hier bleibt, ist die
     * Pruefung - sie ist fuer beide Kurse dieselbe, und zweimal gebaut wiche eine
     * Haelfte eines Tages ab.
     *
     * <p>Der Sprungschutz greift schon in {@link Wirtschaftsliste#betrag}: Wer einen Kurs
     * um mehr als Faktor 2 verstellt, muss das bestaetigen. Ein Kurs, der aus einem
     * Vertipper ein Zehntel wird, macht aus jeder Goldmark den zehnfachen Gegenwert - und
     * das Geld dahinter liegt binnen Minuten auf vielen Konten.
     */
    private static long kurs(String pfad, long gelesen, long vorgabe,
                             List<String> klagen) {
        if (!Wechselwerte.brauchbarerKurs(gelesen)) {
            klagen.add(pfad + " ist " + gelesen + " - verworfen. Erlaubt ist 1 bis "
                    + Wechselwerte.HOECHSTER_KURS + " Mark je Goldmark; eine 0 waere ein"
                    + " Tausch ohne Gegenwert. Wer den Rueckweg schliessen will, setzt "
                    + Wechselwerte.PFAD_DECKEL + " auf " + Wechselwerte.RUECKWEG_ZU
                    + ". " + Wirtschaftsliste.esBleibt(vorgabe));
            return vorgabe;
        }
        return gelesen;
    }

    /**
     * Der Wechseldeckel - in MARK bemessen (Plan 8.1).
     *
     * <p><b>Eine 0 heisst hier: der Rueckweg ist ZU</b>, und nicht "kein Deckel" wie bei
     * {@code bundesmarkt.tagesdeckel}. Die Begruendung steht bei
     * {@link Wechselwerte#RUECKWEG_ZU}; dieser Leser nennt sie dem Betreiber in einer
     * Berichtszeile, damit der Unterschied zwischen den zwei Deckeln nicht nur im
     * Quelltext steht.
     *
     * <p><b>Der Sprungschutz greift bei der 0</b>, und das ist angebracht: Von 10.000 auf
     * 0 ist mehr als Faktor 2, also verlangt {@link Wirtschaftsliste#betrag} eine
     * Bestaetigung. Wer den Rueckweg schliesst, traegt sie ein - es ist die Aenderung, die
     * jeder Spieler mit Goldmark sofort merkt.
     */
    private static long deckel(ConfigurationSection wurzel, List<String> klagen,
                               List<String> bericht) {
        long gelesen = Wirtschaftsliste.betrag(wurzel, Wechselwerte.PFAD_DECKEL,
                "wechsel-tagesdeckel", Wechselwerte.VORGABE_DECKEL,
                Wechselwerte.HOECHSTER_DECKEL, "Mark", klagen, bericht);
        if (!Wechselwerte.brauchbarerDeckel(gelesen)) {
            klagen.add(Wechselwerte.PFAD_DECKEL + " ist " + gelesen
                    + " - verworfen. Erlaubt ist " + Wechselwerte.RUECKWEG_ZU + " bis "
                    + Wechselwerte.HOECHSTER_DECKEL + ", und eine "
                    + Wechselwerte.RUECKWEG_ZU + " heisst: der Rueckweg ist zu. "
                    + Wirtschaftsliste.esBleibt(Wechselwerte.VORGABE_DECKEL));
            return Wechselwerte.VORGABE_DECKEL;
        }
        if (gelesen == Wechselwerte.RUECKWEG_ZU) {
            // Ein BERICHT und keine Klage: Es ist eine Einstellung. Aber eine, die man
            // lesen soll - und zwar mit dem Unterschied zum anderen Deckel daneben, denn
            // dort heisst dieselbe 0 das Gegenteil.
            bericht.add(Wechselwerte.PFAD_DECKEL + " ist " + Wechselwerte.RUECKWEG_ZU
                    + ": der RUECKWEG IST ZU. Goldmark laesst sich kaufen und nicht mehr"
                    + " in Mark zurueckwechseln. Achtung, hier heisst die 0 etwas anderes"
                    + " als bei bundesmarkt.tagesdeckel - dort bedeutet sie KEIN Deckel.");
        }
        return gelesen;
    }

    /**
     * Nennt jede Wechselzeile, die das Plugin mitbringt, die aber in der Datei fehlt.
     *
     * <p>Eigene Liste und eigener Zaehlsatz, nicht die von {@link Wirtschaftsliste} oder
     * {@link Marktliste}: Vier Berichte, die dieselbe Zahl verschieden nennen, sind
     * schlimmer als vier Zeilen.
     */
    private static void nenneFehlende(ConfigurationSection wurzel, List<String> bericht) {
        List<String> fehlen = new ArrayList<>();
        for (String pfad : SCHLUESSEL) {
            if (!wurzel.contains(pfad, true)) {
                fehlen.add(pfad);
            }
        }
        if (!fehlen.isEmpty()) {
            bericht.add(fehlen.size() + " von " + SCHLUESSEL.size()
                    + " Wechselzeilen stehen nicht in deiner config.yml: "
                    + String.join(", ", fehlen) + ". Sichtbar und aenderbar sind sie erst,"
                    + " wenn sie in der Datei stehen; was das Plugin ohne die Zeile"
                    + " mitbringt, nennt die Rechenprobe darunter.");
        }
    }
}
