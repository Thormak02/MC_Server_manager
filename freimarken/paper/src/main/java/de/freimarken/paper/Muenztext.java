package de.freimarken.paper;

import de.freimarken.kern.Bedingungsart;
import de.freimarken.kern.Buchung;
import de.freimarken.kern.Buchungsgrund;
import de.freimarken.kern.Muenzhaus;
import de.freimarken.kern.Muenzwerte;
import de.freimarken.kern.Rangregel;
import de.freimarken.kern.Wechselbuch;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.kyori.adventure.text.Component;

/**
 * Die Zeilen des Muenzhauses - reine Rechnung, kein Bukkit, kein Senden.
 *
 * <p>Dieselbe Naht wie {@link Tauschtext} fuer die Wechselstube: Wer eine Zeile braucht,
 * holt sie hier und schickt sie nur hinaus. Dadurch ist jedes Wort, jede Farbe und jede
 * Verzweigung ohne Server pruefbar.
 *
 * <p>Kein Wort steht hier und keine Farbe: Der Wortlaut kommt aus
 * {@code sprache-vorlage.yml} ueber {@link MarkSatz}. Was hier bleibt, ist die WAHL -
 * welcher Satz zu welchem Zustand gehoert, und welche Zahl in welcher Waehrung daneben
 * steht.
 *
 * <p><b>Die Einheit haengt an jeder Zahl einzeln</b>, und das ist in diesem Haus die
 * gefaehrlichste Stelle: Preise sind GOLDMARK, Kurse sind MARK, und beides sind
 * {@code long}. Darum nennt jede Zeile ihre Methode beim Namen -
 * {@code sprache.goldmark} oder {@code sprache.betrag} -, und die Proben vergleichen
 * nicht Zahlen, sondern das fertige Bild.
 */
final class Muenztext {

    private Muenztext() {
    }

    /**
     * Die Tafel des Muenzhauses - Kontostand, Wechseldeckel und Katalog in einem Blick.
     *
     * <p><b>Die drei mittleren Zeilen kommen von der Wechselstube</b>
     * ({@link Tauschtext#standUndKurse}): der Goldmarkstand, die zwei Kurse und der
     * sichtbare Wechseldeckel. Das ist genau das, was Plan 2.1 unter "Wechseldeckel
     * sichtbar" nennt, und es steht dort schon - eine zweite Fassung derselben Auskunft
     * waere die, die der naechste Umbau vergisst.
     *
     * <p><b>Je Posten EINE Zeile</b>, mit Name, Preis und Inhalt darin. Das ist kein
     * Platzsparen: {@code fehlend} ist je Posten eine LISTE, und als eigene Zeilen
     * ausgegeben waere diese Tafel bei vier Posten zwischen sieben und
     * siebenundzwanzig Zeilen lang - je nachdem, was der Betreiber eingetragen hat.
     *
     * <p>Drei Gestalten je Zeile, und die Reihenfolge der Fragen ist die des Kaufs: Erst
     * die BEDINGUNG (die kann er heute nicht aendern), dann das GELD (das kann er sich
     * holen). Ein Posten, der an beidem haengt, zeigt die Bedingung - sie ist die
     * groessere Huerde.
     */
    static List<Component> tafelZeilen(Sprache sprache, Muenzhaus.Tafel tafel,
                                       Wechselbuch.Auskunft auskunft) {
        List<Component> zeilen = new ArrayList<>(sprache.bau(MarkSatz.MUENZHAUS_KOPF, 1L,
                Einsatz.keine()));
        zeilen.addAll(Tauschtext.standUndKurse(sprache, auskunft));
        if (!tafel.werte().an()) {
            zeilen.addAll(sprache.bau(MarkSatz.MUENZHAUS_AUS, 1L, Einsatz.keine()));
        } else if (tafel.zeilen().isEmpty()) {
            zeilen.addAll(sprache.bau(MarkSatz.MUENZHAUS_LEER, 1L, Einsatz.keine()));
        } else {
            for (Muenzhaus.Zeile zeile : tafel.zeilen()) {
                zeilen.addAll(sprache.bau(satzFuer(zeile), 1L,
                        Einsatz.von("name", name(sprache, zeile.posten()))
                                .mit("preis", sprache.goldmark(zeile.posten().preis()))
                                .mit("inhalt", inhalt(zeile.posten()))));
            }
        }
        zeilen.addAll(sprache.bau(MarkSatz.MUENZHAUS_HILFE, 1L, Einsatz.keine()));
        return List.copyOf(zeilen);
    }

    /**
     * Die Antwort auf einen Kauf oder seine Pruefung - je Bescheid eine eigene.
     *
     * <p><b>Ein Schalter ohne {@code default}</b>, wie bei {@link Tauschtext}: Wer dem
     * {@link Muenzhaus.Bescheid} einen neunten Wert hinzufuegt, wird vom Uebersetzer
     * gefragt, was der Spieler dann liest.
     *
     * <p>{@link Muenzhaus.Bescheid#BEREIT} ist die RUECKFRAGE und keine Absage: Sie nennt
     * Name, Preis, Inhalt und den Stand danach, und darunter steht der Befehl mit
     * {@code bestaetigen}.
     *
     * @param getippt was der Spieler als Posten geschrieben hat - gebraucht wird es nur
     *                fuer {@link Muenzhaus.Bescheid#UNBEKANNT}, denn dort gibt es keinen
     *                Posten. Eine Absage, die das Getippte nicht zeigt, laesst den
     *                Spieler nach seinem Tippfehler suchen
     */
    static List<Component> entscheidZeilen(Sprache sprache, String getippt,
                                           Muenzhaus.Entscheid entscheid) {
        return switch (entscheid.bescheid()) {
            case GEKAUFT -> sprache.bau(MarkSatz.MUENZHAUS_GEKAUFT, 1L,
                    Einsatz.von("name", name(sprache, posten(entscheid)))
                            .mit("inhalt", inhalt(posten(entscheid)))
                            .mit("preis", sprache.goldmark(entscheid.preis()))
                            .mit("stand", sprache.goldmark(entscheid.stand())));
            case BEREIT -> sprache.bau(MarkSatz.MUENZHAUS_FRAGE, 1L,
                    Einsatz.von("name", name(sprache, posten(entscheid)))
                            .mit("posten", posten(entscheid).schluessel())
                            .mit("preis", sprache.goldmark(entscheid.preis()))
                            .mit("inhalt", inhalt(posten(entscheid)))
                            .mit("danach", sprache.goldmark(
                                    entscheid.stand() - entscheid.preis())));
            case AUS -> sprache.bau(MarkSatz.MUENZHAUS_AUS, 1L, Einsatz.keine());
            case UNBEKANNT -> sprache.bau(MarkSatz.MUENZHAUS_UNBEKANNT, 1L,
                    Einsatz.von("posten", getippt));
            case BEDINGUNG_FEHLT -> bedingungZeilen(sprache, entscheid);
            case ZU_WENIG_GOLDMARK -> sprache.bau(MarkSatz.MUENZHAUS_ZU_WENIG, 1L,
                    Einsatz.von("preis", sprache.goldmark(entscheid.preis()))
                            .mit("stand", sprache.goldmark(entscheid.stand()))
                            .mit("fehlt", sprache.goldmark(entscheid.fehlt())));
            case KEIN_PLATZ -> sprache.bau(MarkSatz.MUENZHAUS_KEIN_PLATZ,
                    entscheid.fehlenderPlatz(),
                    Einsatz.von("stueck", String.valueOf(entscheid.fehlenderPlatz())));
            case GESTOERT -> sprache.bau(MarkSatz.MUENZHAUS_GESTOERT, 1L,
                    Einsatz.keine());
            // ZWEI STOERUNGEN, ZWEI SAETZE: Nur hier war wirklich abgebucht. Vorher
            // hatten beide denselben Satz, und der sprach vom Geld.
            case ERSTATTET -> sprache.bau(MarkSatz.MUENZHAUS_ERSTATTET, 1L,
                    Einsatz.keine());
        };
    }

    /**
     * Der Verlauf des GOLDbuchs - Kopf, Stand und die letzten Buchungen.
     *
     * <p><b>Eigener Kopf, eigene Leerzeile, dieselbe Zeilenform</b> - wie beim Verlauf der
     * Stadtkasse: Eine Buchung soll ueberall gleich aussehen, und eine zweite Form
     * bekaeme die naechste Verbesserung nicht mit. Was hier anders ist als beim Markbuch,
     * ist allein die WAEHRUNG, und die sagt das Buch, das {@link Verlaufszeile} mitbekommt.
     *
     * <p>Die Wache auf die leere Liste steht HIER und nicht im Zweig: In beiden Faellen
     * wird gesendet, sie entscheidet also WAS und nicht OB.
     */
    static List<Component> verlaufZeilen(Sprache sprache, Muenzhaus.Konto konto,
                                         ZoneId zone) {
        List<Component> zeilen = new ArrayList<>(sprache.bau(
                MarkSatz.MUENZHAUS_VERLAUF_KOPF, 1L,
                Einsatz.von("betrag", sprache.goldmark(konto.goldmark()))));
        if (konto.buchungen().isEmpty()) {
            zeilen.addAll(sprache.bau(MarkSatz.MUENZHAUS_VERLAUF_LEER, 1L,
                    Einsatz.keine()));
            return List.copyOf(zeilen);
        }
        for (Buchung buchung : konto.buchungen()) {
            zeilen.add(Verlaufszeile.zeile(sprache, buchung, zone,
                    Buchungsgrund.Buch.GOLDMARK));
        }
        return List.copyOf(zeilen);
    }

    /**
     * Die Absage mit der fehlenden Bedingung - eine Kopfzeile und je Bedingung eine Zeile.
     *
     * <p><b>Je Bedingung eine eigene GEZAEHLTE Zeile</b>, und das ist keine
     * Platzverschwendung, sondern Grammatik: "1 eigene Parzellen" waere falsches Deutsch,
     * und genau diese Zahl steht in der ausgelieferten Datei. Mit
     * {@code Art.GEZAEHLT} waehlt die Sprachdatei zwischen {@code .eins} und
     * {@code .viele}.
     *
     * <p><b>Die Zahl ist die aus der {@code config.yml}</b>, zurueckgerechnet mit
     * {@link Bedingungsart#inDerDatei}: {@code Rangregel.Fehlend} rechnet in der
     * Messeinheit, also in Sekunden, und ungerechnet gezeigt stuende hier "noch 14.400
     * Stunden".
     *
     * <p>Genannt wird, was VERLANGT ist, und nicht der Rest: Ein Rest von 90 Minuten
     * waere abwaerts gerundet "noch 0 Stunden" - und das liest sich wie ein Fehler.
     */
    private static List<Component> bedingungZeilen(Sprache sprache,
                                                   Muenzhaus.Entscheid entscheid) {
        List<Component> zeilen = new ArrayList<>(sprache.bau(
                MarkSatz.MUENZHAUS_BEDINGUNG_FEHLT, 1L,
                Einsatz.von("name", name(sprache, posten(entscheid)))));
        for (Rangregel.Bedingungsstand fehlend : entscheid.fehlend()) {
            long ausDerDatei = fehlend.art().inDerDatei(fehlend.verlangt());
            // EIN GELDBETRAG KOMMT AUS DER WAEHRUNG, jede andere Zahl nackt. Vorher
            // stand hier jede Zahl nackt, und das Wort "Mark" fest in der Sprachdatei:
            // Derselbe Betrag hiess zwei Zeilen hoeher "50.000 Mark" und in dieser einen
            // Zeile "50000 Mark" - und wer seine Waehrung umbenannte, erreichte diese
            // Zeile nicht. Welche Art Geld ist, sagt die Art selbst.
            String zahl = fehlend.art().istGeld()
                    ? sprache.betrag(ausDerDatei)
                    : String.valueOf(ausDerDatei);
            zeilen.addAll(sprache.bau(Sprachschluessel.bedingung(fehlend.art()),
                    ausDerDatei, Einsatz.von("zahl", zahl)));
        }
        return List.copyOf(zeilen);
    }

    /** Welche der drei Gestalten eine Katalogzeile hat - die Bedingung zuerst. */
    private static MarkSatz satzFuer(Muenzhaus.Zeile zeile) {
        if (!zeile.erfuellt()) {
            return MarkSatz.MUENZHAUS_ZEILE_GESPERRT;
        }
        return zeile.bezahlbar() ? MarkSatz.MUENZHAUS_ZEILE
                : MarkSatz.MUENZHAUS_ZEILE_ZU_TEUER;
    }

    /** Der Posten eines Entscheids, der einen haben MUSS - sonst ist es ein Bauunfall. */
    private static Muenzwerte.Posten posten(Muenzhaus.Entscheid entscheid) {
        return entscheid.posten().orElseThrow(() -> new IllegalArgumentException(
                "Der Bescheid " + entscheid.bescheid() + " braucht seinen Posten"));
    }

    /**
     * Der Name eines Postens aus der Sprachdatei - oder sein Pfad.
     *
     * <p>Ueber {@link Muenzname}, also ueber die offene Familie: Welche Posten es gibt,
     * entscheidet die {@code config.yml}, und was der Betreiber morgen eintraegt, kann
     * keine Aufzaehlung kennen.
     */
    private static String name(Sprache sprache, Muenzwerte.Posten posten) {
        return sprache.wort(new Muenzname(posten.schluessel()), Einsatz.keine());
    }

    /**
     * Gegenstaende und Mengen in EINER Zeichenkette - "16x rose_bush, 16x peony".
     *
     * <p>Dieselbe Form und derselbe Grund wie bei den Herausforderungen
     * ({@code Wolkentext.mengen}): die technischen Namen und keine Uebersetzung. Genau
     * diese Namen stehen in der {@code config.yml}, und ein Spieler, der nach einem fragt,
     * bekaeme von seinem Betreiber sonst die falsche Antwort.
     *
     * <p>Die Reihenfolge ist die des Postens, also die der Datei - dafuer haelt
     * {@code Muenzwerte.Posten} seine Gegenstaende in einer geordneten Karte.
     */
    private static String inhalt(Muenzwerte.Posten posten) {
        List<String> stuecke = new ArrayList<>();
        for (Map.Entry<String, Integer> zeile : posten.gegenstaende().entrySet()) {
            stuecke.add(zeile.getValue() + "x " + zeile.getKey());
        }
        return String.join(", ", stuecke);
    }
}
