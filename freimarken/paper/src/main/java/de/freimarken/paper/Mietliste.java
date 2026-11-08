package de.freimarken.paper;

import de.freimarken.kern.Mietregel;
import de.freimarken.kern.Mietwerte;
import java.util.ArrayList;
import java.util.List;
import org.bukkit.configuration.ConfigurationSection;

/**
 * Liest die Zahlen der Ladenmiete aus der {@code config.yml}.
 *
 * <p>Dieselbe Bauart wie {@link Bonusliste} und {@link Messliste}: ein Ergebnis mit
 * Werten, Klagen und Bericht, jede kaputte Zeile beklagt und die uebrigen stehen gelassen.
 *
 * <p>Der ZWEITE Leser im Abschnitt {@code laeden:} - {@link Ladenliste} liest dort die
 * Einheiten und die Welt. Getrennt, weil beide verschiedene Dinge koennen muessen: Die
 * Einheiten brauchen die Weltgrenzen, die Zahlen brauchen den Sprungschutz. Ihre
 * Berichtszeilen fangen darum verschieden an ("Laeden:" und "Ladenmiete:"); zwei Zeilen,
 * die gleich anfangen, liest niemand als zwei.
 *
 * <p><b>Die Reihenfolge der Lesungen ist nicht beliebig.</b> Erst die Miete, dann die
 * Kaution: Die zweite wird gegen die erste geprueft, weil beide je einzeln im Bereich
 * liegen und zusammen trotzdem das Gegenteil bewirken koennen - eine Kaution unter der
 * Tagesmiete macht das Weglaufen billiger als das Kuendigen. Die Regel selbst steht im
 * Konstruktor von {@link Mietwerte}, und dieser Leser darf nicht werfen.
 */
final class Mietliste {

    /** Jede Zeile, die das Plugin mitbringt - fuer den Bericht ueber Fehlende. */
    private static final List<String> SCHLUESSEL = List.of(
            Mietwerte.PFAD_MIETE, Mietwerte.PFAD_KAUTION, Mietwerte.PFAD_LIZENZ,
            Mietwerte.PFAD_DAUER);

    record Ergebnis(Mietwerte werte, List<String> klagen, List<String> bericht) {
    }

    private Mietliste() {
    }

    /**
     * Die vier Zahlen aus dem Abschnitt {@code laeden:}.
     *
     * @param wurzel die ganze Konfiguration, darf {@code null} sein
     */
    static Ergebnis ausKonfiguration(ConfigurationSection wurzel) {
        List<String> klagen = new ArrayList<>();
        List<String> bericht = new ArrayList<>();
        if (wurzel == null) {
            return new Ergebnis(Mietwerte.VORGABE, klagen, bericht);
        }
        nenneFehlende(wurzel, bericht);

        long miete = miete(wurzel, klagen, bericht);
        long kaution = kaution(wurzel, miete, klagen, bericht);
        long lizenz = Wirtschaftsliste.betrag(wurzel, Mietwerte.PFAD_LIZENZ,
                "laden-lizenz", Mietwerte.VORGABE_LIZENZ, Mietwerte.HOECHSTER_BETRAG,
                "Mark einmalig", klagen, bericht);
        int dauer = dauer(wurzel, klagen, bericht);

        Mietwerte werte = new Mietwerte(miete, kaution, lizenz, dauer);
        bericht.add(rechenprobe(werte));
        return new Ergebnis(werte, klagen, bericht);
    }

    /**
     * Die Zahlen im Klartext - die Rechenprobe.
     *
     * <p>Dieselbe Antwort wie bei der Preisstaffel und beim Tagesbonus: die einzige
     * Schranke gegen den Wert, der im erlaubten Bereich liegt und trotzdem falsch ist. Wer
     * hier "30 Tage je Abschluss (150 Mark voller Zeitraum)" liest, sieht die Miete von 5
     * Mark - keine Zahlengrenze sieht sie.
     *
     * <p>Paketprivat, damit eine Probe denselben Text nehmen kann: Zwei Stellen, die
     * dieselben Zahlen verschieden schreiben, sehen nach zwei verschiedenen Zahlen aus.
     */
    static String rechenprobe(Mietwerte werte) {
        StringBuilder satz = new StringBuilder("Ladenmiete: ");
        satz.append(werte.miete()).append(" Mark je Tag, ")
                .append(tage(werte.dauerTage()))
                .append(" je Abschluss (").append(werte.vollerZeitraum())
                .append(" Mark voller Zeitraum). ");
        if (werte.mitKaution()) {
            satz.append("Kaution ").append(werte.kaution())
                    .append(" Mark - zurueck nur bei ordentlicher Kuendigung, nicht beim"
                            + " Raeumen. ");
        } else {
            satz.append("Ohne Kaution - das Weglaufen kostet dann nichts. ");
        }
        if (werte.kostetLizenz()) {
            satz.append("Lizenz ").append(werte.lizenz()).append(" Mark einmalig. ");
        } else {
            satz.append("Ohne Lizenzgebuehr - wer das Recht hat, darf mieten. ");
        }
        // Die Verlaengerungsschranke braucht keine eigene Zeile in der Datei (sie faellt
        // aus der Mietdauer), aber sie muss im Bericht stehen: Sonst erfaehrt der
        // Betreiber erst von einem Spieler, dass Verlaengern "noch nicht" geht.
        satz.append("Verlaengert wird, sobald weniger als ")
                .append(tage(werte.dauerTage()))
                .append(" uebrig sind; weiter als ")
                // ZWEI Mietdauern MINUS den heutigen Tag, weil laeuftBis eingeschlossen
                // ist - gemessen in MietregelTest.nieWeiterAlsZweiDauern. Hier stand
                // einmal 2 x Dauer, und im gelesenen Startlauf vom 08.11.2026 fiel auf,
                // dass diese Zahl nie erreicht wird: Ein Betreiber, der nachrechnet,
                // findet 59 und haelt die Zeile fuer falsch.
                .append(tage(2L * werte.dauerTage() - 1L))
                .append(" voraus liegt das Ende nie.");
        return satz.toString();
    }

    /**
     * Eine Anzahl Tage mit der richtigen Mehrzahl.
     *
     * <p>EINE Stelle fuer drei Vorkommen, und das ist ein Befund: Die dritte Zahl bekam
     * ihre Fallunterscheidung nicht, und bei einer Mietdauer von einem Tag stand
     * "weiter als 1 Tage voraus" im Bericht. Drei Stellen mit derselben Regel sind drei
     * Gelegenheiten, sie zu vergessen.
     *
     * <p>Die Mehrzahl steht im Programm und nicht in der Sprachdatei, weil diese Zeile an
     * den BETREIBER geht und in die Konsole - nicht an einen Spieler. Dann muss sie aber
     * auch stimmen: "1 Tage" liest sich wie ein Fehler, und wer das sieht, sucht ihn an
     * der falschen Stelle.
     */
    private static String tage(long anzahl) {
        return anzahl + (anzahl == 1L ? " Tag" : " Tage");
    }

    /**
     * Die Tagesmiete - sie darf nicht 0 sein.
     *
     * <p>{@link Wirtschaftsliste#betrag} laesst die 0 zu; bei einem Grundpreis heisst sie
     * etwas. Hier hiesse sie, dass der taegliche Lauf aus Teil C den Vertrag nie
     * beruehrt - also ein Laden, der nie wegen Zahlungsausfall frei wird. Wer Laeden
     * verschenken will, nimmt eine 1.
     */
    private static long miete(ConfigurationSection wurzel, List<String> klagen,
                              List<String> bericht) {
        String pfad = Mietwerte.PFAD_MIETE;
        long gelesen = Wirtschaftsliste.betrag(wurzel, pfad, "laden-miete",
                Mietwerte.VORGABE_MIETE, Mietwerte.HOECHSTER_BETRAG, "Mark je Tag",
                klagen, bericht);
        if (gelesen < 1L) {
            klagen.add(pfad + " ist " + gelesen + " - verworfen. Eine 0 waere ein Laden,"
                    + " den der taegliche Lauf nie beruehrt, der also nie wegen"
                    + " Zahlungsausfall frei wird. Wer Laeden verschenken will, nimmt"
                    + " eine 1. " + Wirtschaftsliste.esBleibt(Mietwerte.VORGABE_MIETE));
            return Mietwerte.VORGABE_MIETE;
        }
        return gelesen;
    }

    /**
     * Die Kaution - geprueft GEGEN die Tagesmiete.
     *
     * <p>Zurueck kommt im Fehlerfall die Tagesmiete und nicht die Vorgabe des Programms,
     * aus demselben Grund wie beim Hoechstbetrag des Bonus: Wer eine kleine Kaution
     * eingetragen hat, wollte eine kleine Kaution, und die Tagesmiete ist die kleinste,
     * die noch wirkt. Die Vorgabe 5.000 waere bei einer Miete von 10.000 wieder
     * unmoeglich.
     *
     * <p>Die 0 geht ausdruecklich durch: "ohne Kaution" ist eine Entscheidung und kein
     * Versehen.
     */
    private static long kaution(ConfigurationSection wurzel, long miete,
                                List<String> klagen, List<String> bericht) {
        String pfad = Mietwerte.PFAD_KAUTION;
        long gelesen = Wirtschaftsliste.betrag(wurzel, pfad, "laden-kaution",
                Mietwerte.VORGABE_KAUTION, Mietwerte.HOECHSTER_BETRAG, "Mark", klagen,
                bericht);
        if (!Mietwerte.brauchbaresPaar(gelesen, miete)) {
            klagen.add(pfad + " ist " + gelesen + " und liegt unter der Tagesmiete "
                    + miete + " - verworfen. Dann kostet das Weglaufen weniger als ein"
                    + " Miettag, und die Raeumung wird der billigere Weg; die Kaution"
                    + " bewirkt das Gegenteil von dem, wofuer sie da ist. Erlaubt ist 0"
                    + " (ohne Kaution) oder mindestens " + miete + ". Es gilt " + miete
                    + ", die kleinste Kaution, die wirkt.");
            return miete;
        }
        return gelesen;
    }

    /** Wie viele Tage ein Abschluss gilt. */
    private static int dauer(ConfigurationSection wurzel, List<String> klagen,
                             List<String> bericht) {
        String pfad = Mietwerte.PFAD_DAUER;
        Long gelesen = Wirtschaftsliste.ganzeZahl(wurzel, pfad, Mietwerte.VORGABE_DAUER,
                klagen, bericht);
        if (gelesen == null) {
            return Mietwerte.VORGABE_DAUER;
        }
        long wert = gelesen;
        // Der LONG wird geprueft und nicht der verkuerzte int: Aus (int) 4294967326 wird
        // 30, und das waere zufaellig die Vorgabe - der Betreiber bekaeme keine Klage.
        if (wert < 1L || wert > Mietregel.HOECHSTE_DAUER) {
            klagen.add(pfad + " ist " + wert + " - verworfen. Erlaubt ist 1 bis "
                    + Mietregel.HOECHSTE_DAUER + " Tage. Eine 0 waere ein Vertrag, der am"
                    + " Tag seines Abschlusses schon abgelaufen ist. "
                    + Wirtschaftsliste.esBleibt(Mietwerte.VORGABE_DAUER));
            return Mietwerte.VORGABE_DAUER;
        }
        return (int) wert;
    }

    /**
     * Nennt jede Zeile, die das Plugin mitbringt, die aber in der Datei fehlt.
     *
     * <p>Eigene Liste und eigener Zaehlsatz, wie bei {@link Bonusliste} und
     * {@link Messliste}: Fuenf Berichte, die dieselbe Zahl verschieden nennen, sind
     * schlimmer als fuenf Zeilen.
     */
    private static void nenneFehlende(ConfigurationSection wurzel, List<String> bericht) {
        List<String> fehlen = new ArrayList<>();
        for (String pfad : SCHLUESSEL) {
            if (!wurzel.contains(pfad, true)) {
                fehlen.add(pfad);
            }
        }
        if (!fehlen.isEmpty()) {
            bericht.add(fehlen.size() + " von " + SCHLUESSEL.size() + " Zeilen der"
                    + " Ladenmiete fehlen in deiner config.yml: "
                    + String.join(", ", fehlen)
                    + ". Dafuer gelten die Vorgaben des Programms.");
        }
    }
}
