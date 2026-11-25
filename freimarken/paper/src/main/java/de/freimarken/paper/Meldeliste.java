package de.freimarken.paper;

import de.freimarken.kern.Meldewerte;
import java.util.ArrayList;
import java.util.List;
import org.bukkit.configuration.ConfigurationSection;

/**
 * Liest den Abschnitt {@code moderation.melden:} (Plan 12c.3, Schritt 16a Teil C).
 *
 * <p>Ein kleiner Leser - ein Schalter und drei Zahlen, keine Liste. Er steht trotzdem in
 * einer eigenen Datei und nicht in {@link Wirtschaftsliste}: Dort liegen die Werte der
 * WIRTSCHAFT, und ein Meldewesen ist keine. Dieselbe Trennung wie bei
 * {@link Blockbuchliste} und {@link Teamliste}.
 *
 * <p><b>Jeder unbrauchbare Wert faellt EINZELN auf die Vorgabe zurueck</b> - anders als
 * bei einer Rangstufe, die ganz wegfaellt. Der Grund ist der Unterschied der Gattung: Eine
 * Rangstufe mit fehlender Bedingung waere eine LEICHTERE, also eine, die Rechte verschenkt.
 * Eine Abklingzeit, die auf 60 zurueckfaellt, ist dagegen genau das, was ein Server ohne
 * Eintrag bekommt - niemand wird dadurch bevorteilt, und das Meldewesen bleibt offen.
 */
final class Meldeliste {

    /** Die Zeilen, die das Plugin mitbringt - fuer den Bericht ueber Fehlende. */
    static final List<String> SCHLUESSEL = List.of(Meldewerte.PFAD_AN,
            Meldewerte.PFAD_ABKLINGZEIT, Meldewerte.PFAD_HOECHSTE_OFFENE,
            Meldewerte.PFAD_ZEILEN_JE_SEITE);

    record Ergebnis(Meldewerte werte, List<String> klagen, List<String> bericht) {
    }

    private Meldeliste() {
    }

    /**
     * Der Abschnitt {@code moderation.melden:}.
     *
     * @param wurzel die ganze Konfiguration, darf {@code null} sein
     */
    static Ergebnis ausKonfiguration(ConfigurationSection wurzel) {
        List<String> klagen = new ArrayList<>();
        List<String> bericht = new ArrayList<>();
        if (wurzel == null) {
            return fertig(Meldewerte.VORGABE, klagen, bericht);
        }
        nenneFehlende(wurzel, bericht);
        boolean an = jaNein(wurzel, Meldewerte.PFAD_AN, Meldewerte.VORGABE.an(), klagen);
        long abkling = zahl(wurzel, Meldewerte.PFAD_ABKLINGZEIT,
                Meldewerte.VORGABE.abklingzeitSekunden(),
                Meldewerte::brauchbareAbklingzeit,
                "0 bis " + Meldewerte.LAENGSTE_ABKLINGZEIT + " Sekunden", klagen);
        long offene = zahl(wurzel, Meldewerte.PFAD_HOECHSTE_OFFENE,
                Meldewerte.VORGABE.hoechsteOffeneJeMelder(), Meldewerte::brauchbareOffene,
                "1 bis " + Meldewerte.HOECHSTE_GRENZE, klagen);
        long zeilen = zahl(wurzel, Meldewerte.PFAD_ZEILEN_JE_SEITE,
                Meldewerte.VORGABE.zeilenJeSeite(), Meldewerte::brauchbareZeilen,
                "1 bis " + Meldewerte.HOECHSTE_ZEILEN, klagen);
        try {
            return fertig(new Meldewerte(an, abkling, (int) offene, (int) zeilen),
                    klagen, bericht);
        } catch (IllegalArgumentException unbrauchbar) {
            // Kommt aus einer Zusage, die dieser Leser nicht selbst prueft. Heute kann
            // das nicht eintreten - jede der drei Zahlen ist einzeln geprueft -, und
            // genau darum steht der Zweig hier: Wer eine vierte Zahl hinzufuegt und die
            // Pruefung vergisst, bekommt eine Klage und keine Ausnahme beim Start.
            klagen.add("moderation.melden: " + unbrauchbar.getMessage() + " "
                    + Wirtschaftsliste.esBleibt("die Vorgaben des Programms"));
            return fertig(Meldewerte.VORGABE, klagen, bericht);
        }
    }

    /**
     * Die Rechenprobe fuer den Startbericht.
     *
     * <p>Sie nennt BEIDE Bremsen in einem Satz, weil sie nur zusammen wirken: Eine
     * Abklingzeit ohne Hoechstzahl laesst beliebig viele Meldungen zu, nur langsamer; eine
     * Hoechstzahl ohne Abklingzeit laesst drei auf einmal zu. Wer nur eine Zahl liest,
     * haelt die andere fuer gegeben.
     */
    static String rechenprobe(Meldewerte werte) {
        if (werte == null || !werte.greift()) {
            return "Melden: aus - /melden erscheint nicht. Was in der Datenbank steht,"
                    + " bleibt stehen und ist ueber /meldung weiter zu bearbeiten.";
        }
        return "Melden: an - " + werte.hoechsteOffeneJeMelder()
                + " offene Meldungen je Spieler, dazwischen "
                + werte.abklingzeitSekunden() + " Sekunden Wartezeit; /meldung zeigt "
                + werte.zeilenJeSeite() + " Zeilen.";
    }

    /** Das Ergebnis - und JEDER Rueckweg geht hier durch. */
    private static Ergebnis fertig(Meldewerte werte, List<String> klagen,
                                   List<String> bericht) {
        // Dieselbe Bauart wie in Teamliste.fertig, und aus demselben am 25.11.2026
        // gemessenen Grund: Ein frueher Rueckweg bei fehlendem Abschnitt uebersprang dort
        // die Ungereimtheiten, und der Server, dem die Auskunft am meisten hilft - der
        // ohne Eintrag - bekam sie nicht.
        bericht.addAll(werte.ungereimtheiten());
        return new Ergebnis(werte, klagen, bericht);
    }

    /**
     * Ein Ja/Nein aus der Datei.
     *
     * <p><b>Bukkit liest jedes Wort ausser {@code true} stumm als {@code false}</b> -
     * derselbe Grund, aus dem das Blockbuch seine Weissliste als Namen fuehrt und nicht
     * als Schalter. Darum wird der ROHWERT geprueft und nicht {@code getBoolean}
     * genommen: Aus einem {@code an: ja} waere sonst ein abgeschaltetes Meldewesen ohne
     * eine Zeile Klage geworden.
     */
    private static boolean jaNein(ConfigurationSection wurzel, String pfad,
                                  boolean vorgabe, List<String> klagen) {
        if (!wurzel.contains(pfad, true)) {
            return vorgabe;
        }
        Object roh = wurzel.get(pfad);
        if (roh instanceof Boolean wahrheit) {
            return wahrheit;
        }
        klagen.add(pfad + " ist " + Wirtschaftsliste.wieGetippt(roh)
                + " - erwartet wird true oder false. "
                + Wirtschaftsliste.esBleibt(vorgabe ? "true" : "false"));
        return vorgabe;
    }

    /**
     * Welche Schluessel dieses Abschnitts in der Datei fehlen - fuer den Bericht.
     *
     * <p>Dieselbe Bauart wie in {@link Anmeldeliste}. <b>Die Liste war gebaut und nicht
     * angeschlossen</b> [gefunden am 25.11.2026 beim Bau von Teil B]: Ein Betreiber, der
     * eine Zeile aus der Datei nimmt, bekommt die Vorgabe - und erfuhr es nicht. Genau
     * die Art Luecke, die dieses Projekt schon mehrfach getroffen hat: ein gebautes,
     * gruen gepruefbares Stueck, das niemand verdrahtet hat.
     */
    private static void nenneFehlende(ConfigurationSection wurzel,
                                      List<String> bericht) {
        List<String> fehlen = new ArrayList<>();
        for (String schluessel : SCHLUESSEL) {
            if (!wurzel.isSet(schluessel)) {
                fehlen.add(schluessel);
            }
        }
        if (!fehlen.isEmpty()) {
            bericht.add("Nicht in der Datei, es gilt die Vorgabe: "
                    + String.join(", ", fehlen));
        }
    }

    /** Eine ganze Zahl aus der Datei, gegen ihre eigene Regel geprueft. */
    private static long zahl(ConfigurationSection wurzel, String pfad, long vorgabe,
                             java.util.function.LongPredicate brauchbar,
                             String erwartet, List<String> klagen) {
        if (!wurzel.contains(pfad, true)) {
            return vorgabe;
        }
        Object roh = wurzel.get(pfad);
        // Die Zahl wird als long geprueft, obwohl zwei der drei Felder int sind - genau
        // aus dem Grund, der an Wirtschaftswerte.brauchbareAnzahl steht: Wer vorher auf
        // int verkuerzt, prueft nicht mehr den getippten Wert, sondern dessen untere 32
        // Bit, und aus -2.147.483.649 wird dabei 2.147.483.647.
        if (!(roh instanceof Number zahl) || zahl.doubleValue() != Math.floor(
                zahl.doubleValue()) || !brauchbar.test(zahl.longValue())) {
            klagen.add(pfad + " ist " + Wirtschaftsliste.wieGetippt(roh)
                    + " - erwartet wird eine ganze Zahl von " + erwartet + ". "
                    + Wirtschaftsliste.esBleibt(vorgabe));
            return vorgabe;
        }
        return zahl.longValue();
    }
}
