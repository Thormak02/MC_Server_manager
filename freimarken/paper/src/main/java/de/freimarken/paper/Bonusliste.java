package de.freimarken.paper;

import de.freimarken.kern.Bonusregel;
import de.freimarken.kern.Bonuswerte;
import de.freimarken.kern.Serienregel;
import java.util.ArrayList;
import java.util.List;
import org.bukkit.configuration.ConfigurationSection;

/**
 * Liest den Tagesbonus aus der {@code config.yml}.
 *
 * <p>Dieselbe Bauart wie {@link Marktliste} und {@link Messliste}: ein Ergebnis mit
 * Werten, Klagen und Bericht, jede kaputte Zeile beklagt und die uebrigen stehen gelassen.
 *
 * <p><b>Die Reihenfolge der Lesungen ist nicht beliebig.</b> Erst der Grundbetrag, dann
 * der Hoechstbetrag: Der zweite wird gegen den ersten geprueft, weil beide je einzeln im
 * Bereich liegen und zusammen trotzdem eine Zeile ohne Wirkung ergeben koennen - ein
 * Hoechstbetrag unter dem Grundbetrag heisst, dass der Grundbetrag nie gilt. Die Regel
 * selbst steht im Konstruktor von {@link Bonuswerte}, und dieser Leser darf nicht werfen.
 */
final class Bonusliste {

    /** Jede Zeile, die das Plugin mitbringt - fuer den Bericht ueber Fehlende. */
    private static final List<String> SCHLUESSEL = List.of(
            Bonuswerte.PFAD_GRUNDBETRAG, Bonuswerte.PFAD_JE_SERIENTAG,
            Bonuswerte.PFAD_HOECHSTBETRAG, Bonuswerte.PFAD_LUECKE,
            Bonuswerte.PFAD_MINDEST_MINUTEN);

    record Ergebnis(Bonuswerte werte, List<String> klagen, List<String> bericht) {
    }

    private Bonusliste() {
    }

    /**
     * Der ganze Abschnitt {@code tagesbonus:}.
     *
     * @param wurzel die ganze Konfiguration, darf {@code null} sein
     */
    static Ergebnis ausKonfiguration(ConfigurationSection wurzel) {
        List<String> klagen = new ArrayList<>();
        List<String> bericht = new ArrayList<>();
        if (wurzel == null) {
            return new Ergebnis(Bonuswerte.VORGABE, klagen, bericht);
        }
        nenneFehlende(wurzel, bericht);

        long grundbetrag = pflichtbetrag(wurzel, Bonuswerte.PFAD_GRUNDBETRAG,
                "bonus-grundbetrag", Bonuswerte.VORGABE_GRUNDBETRAG, klagen, bericht);
        long jeSerientag = Wirtschaftsliste.betrag(wurzel, Bonuswerte.PFAD_JE_SERIENTAG,
                "bonus-je-serientag", Bonuswerte.VORGABE_JE_SERIENTAG,
                Bonuswerte.HOECHSTER_BETRAG, "Mark je Serientag", klagen, bericht);
        long hoechstbetrag = hoechstbetrag(wurzel, grundbetrag, klagen, bericht);
        int luecke = luecke(wurzel, klagen, bericht);
        long mindestMinuten = mindestMinuten(wurzel, klagen, bericht);

        Bonuswerte werte = new Bonuswerte(grundbetrag, jeSerientag, hoechstbetrag,
                luecke, mindestMinuten);
        // Die Rechenprobe, dieselbe Antwort wie bei der Preisstaffel: Die einzige
        // Schranke gegen den Wert, der im erlaubten Bereich liegt und trotzdem falsch
        // ist. Wer hier "Tag 1: 50, Tag 2: 50" liest, sieht den vergessenen Zuschlag -
        // keine Zahlengrenze sieht ihn.
        // EINE Zeile und nicht zwei: Die Startmeldung des Plugins nannte dieselbe
        // Staffel ein zweites Mal, und der Betreiber las im Protokoll zweimal
        // dasselbe - gesehen im Startlauf vom 07.11.2026. Diese Zeile steht hier,
        // weil der Bericht des Lesers beim Start UND bei /freimarken neuladen
        // gedruckt wird; eine Zeile im Plugin kaeme nur beim Start.
        bericht.add("Tagesbonus: " + staffelText(werte)
                + " Gezahlt wird hoechstens einmal am Tag; darueber wacht das"
                + " Buchungsbuch und nicht die Serienzeile.");
        return new Ergebnis(werte, klagen, bericht);
    }

    /**
     * Die Staffel im Klartext - was der erste, zweite und letzte wachsende Tag bringen.
     *
     * <p>Paketprivat, damit die Startmeldung denselben Text nimmt: Zwei Stellen, die eine
     * Staffel verschieden schreiben, sehen nach zwei verschiedenen Staffeln aus.
     */
    static String staffelText(Bonuswerte werte) {
        int hoechsterTag = Bonusregel.tagDesHoechstbetrags(werte);
        StringBuilder satz = new StringBuilder();
        satz.append("Tag 1 gibt ").append(Bonusregel.betrag(1, werte)).append(" Mark");
        if (hoechsterTag > 1) {
            satz.append(", Tag 2 ").append(Bonusregel.betrag(2, werte)).append(" Mark");
            satz.append(", ab Tag ").append(hoechsterTag).append(" dann ")
                    .append(werte.hoechstbetrag()).append(" Mark");
        } else {
            // Ohne Zuschlag je Serientag waechst nichts, und dann waere "ab Tag 1 dann
            // dasselbe" eine Zeile, die sich selbst widerspricht.
            satz.append(" - und jeder weitere Tag dasselbe, denn ")
                    .append(Bonuswerte.PFAD_JE_SERIENTAG).append(" ist 0");
        }
        satz.append(". Die Serie reisst, wer ")
                .append(werte.reisstAbLuecke() - 1)
                .append(werte.reisstAbLuecke() == 2 ? " Tag" : " Tage")
                .append(" verpasst");
        if (werte.wartetAufTaetigkeit()) {
            satz.append("; gezahlt wird nach ").append(werte.mindestMinuten())
                    .append(" taetigen Minuten des Tages");
        } else {
            satz.append("; gezahlt wird beim Einloggen");
        }
        return satz.append(".").toString();
    }

    /**
     * Ein Betrag, der nicht 0 sein darf.
     *
     * <p>{@link Wirtschaftsliste#betrag} laesst die 0 zu - bei einem Grundpreis heisst sie
     * etwas ("jede weitere Parzelle ist gratis"). Hier heisst sie "es gibt keinen Bonus",
     * und das ist keine Einstellung, sondern eine stumme Abschaltung: Der Spieler bekaeme
     * eine Nachricht ueber 0 Mark. Wer den Bonus nicht will, soll das sagen muessen.
     */
    private static long pflichtbetrag(ConfigurationSection wurzel, String pfad,
                                      String name, long vorgabe, List<String> klagen,
                                      List<String> bericht) {
        long gelesen = Wirtschaftsliste.betrag(wurzel, pfad, name, vorgabe,
                Bonuswerte.HOECHSTER_BETRAG, "Mark", klagen, bericht);
        if (!Bonuswerte.brauchbarerBetrag(gelesen)) {
            klagen.add(pfad + " ist " + gelesen + " - verworfen. Eine 0 hiesse, dass der"
                    + " Bonus eine Nachricht ueber 0 Mark schickt. "
                    + Wirtschaftsliste.esBleibt(vorgabe));
            return vorgabe;
        }
        return gelesen;
    }

    /**
     * Der Hoechstbetrag - geprueft GEGEN den Grundbetrag.
     *
     * <p>Zurueck kommt im Fehlerfall der Grundbetrag und nicht die Vorgabe des Programms:
     * Wer einen kleinen Hoechstbetrag eingetragen hat, wollte einen kleinen Bonus, und der
     * Grundbetrag ist das Kleinste, was mit seinem eigenen Wert vereinbar ist. Die Vorgabe
     * 2.000 waere bei einem Grundbetrag von 5.000 wieder unmoeglich.
     */
    private static long hoechstbetrag(ConfigurationSection wurzel, long grundbetrag,
                                      List<String> klagen, List<String> bericht) {
        String pfad = Bonuswerte.PFAD_HOECHSTBETRAG;
        long gelesen = Wirtschaftsliste.betrag(wurzel, pfad, "bonus-hoechstbetrag",
                Bonuswerte.VORGABE_HOECHSTBETRAG, Bonuswerte.HOECHSTER_BETRAG, "Mark",
                klagen, bericht);
        if (!Bonuswerte.brauchbarerBetrag(gelesen) || gelesen < grundbetrag) {
            klagen.add(pfad + " ist " + gelesen + " und liegt nicht zwischen dem"
                    + " Grundbetrag " + grundbetrag + " und "
                    + Bonuswerte.HOECHSTER_BETRAG + " - verworfen. Ein Hoechstbetrag"
                    + " unter dem Grundbetrag hiesse, dass der Grundbetrag nie gilt. Es"
                    + " gilt " + grundbetrag + ", also kein Wachstum.");
            return grundbetrag;
        }
        return gelesen;
    }

    /** Ab welcher Luecke die Serie reisst. */
    private static int luecke(ConfigurationSection wurzel, List<String> klagen,
                              List<String> bericht) {
        String pfad = Bonuswerte.PFAD_LUECKE;
        Long gelesen = Wirtschaftsliste.ganzeZahl(wurzel, pfad, Bonuswerte.VORGABE_LUECKE,
                klagen, bericht);
        if (gelesen == null) {
            return Bonuswerte.VORGABE_LUECKE;
        }
        long wert = gelesen;
        // Der LONG wird geprueft und nicht der verkuerzte int: Aus (int) 2147483650 wird
        // 2, und das waere zufaellig die Vorgabe - der Betreiber bekaeme keine Klage.
        if (wert < 2L || wert > Serienregel.HOECHSTE_LUECKE) {
            klagen.add(pfad + " ist " + wert + " - verworfen. Erlaubt ist 2 bis "
                    + Serienregel.HOECHSTE_LUECKE + ". Eine 1 liesse die Serie fuer"
                    + " JEDEN reissen, auch fuer den, der gestern da war; darueber waere"
                    + " sie keine Serie mehr. "
                    + Wirtschaftsliste.esBleibt(Bonuswerte.VORGABE_LUECKE));
            return Bonuswerte.VORGABE_LUECKE;
        }
        return (int) wert;
    }

    /** Wie viele taetige Minuten vor dem Bonus liegen - 0 heisst beim Einloggen. */
    private static long mindestMinuten(ConfigurationSection wurzel, List<String> klagen,
                                       List<String> bericht) {
        String pfad = Bonuswerte.PFAD_MINDEST_MINUTEN;
        Long gelesen = Wirtschaftsliste.ganzeZahl(wurzel, pfad,
                Bonuswerte.VORGABE_MINDEST_MINUTEN, klagen, bericht);
        if (gelesen == null) {
            return Bonuswerte.VORGABE_MINDEST_MINUTEN;
        }
        long wert = gelesen;
        if (!Bonuswerte.brauchbareMindestMinuten(wert)) {
            klagen.add(pfad + " ist " + wert + " - verworfen. Erlaubt ist 0 bis "
                    + Bonuswerte.HOECHSTE_MINDEST_MINUTEN + " Minuten. Einen ganzen Tag"
                    + " lang zu warten hiesse, dass der Bonus genau am Tageswechsel"
                    + " faellt - also nie. "
                    + Wirtschaftsliste.esBleibt(Bonuswerte.VORGABE_MINDEST_MINUTEN));
            return Bonuswerte.VORGABE_MINDEST_MINUTEN;
        }
        return wert;
    }

    /**
     * Nennt jede Zeile, die das Plugin mitbringt, die aber in der Datei fehlt.
     *
     * <p>Eigene Liste und eigener Zaehlsatz, wie bei {@link Marktliste} und
     * {@link Messliste}: Vier Berichte, die dieselbe Zahl verschieden nennen, sind
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
            bericht.add(fehlen.size() + " von " + SCHLUESSEL.size() + " Zeilen des"
                    + " Tagesbonus fehlen in deiner config.yml: "
                    + String.join(", ", fehlen)
                    + ". Dafuer gelten die Vorgaben des Programms.");
        }
    }
}
