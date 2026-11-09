package de.freimarken.paper;

import de.freimarken.kern.Mietvertrag;
import java.util.ArrayList;
import java.util.List;
import net.kyori.adventure.text.Component;

/**
 * Die Zeilen der Ladenmiete - reine Rechnung, kein Bukkit, kein Senden.
 *
 * <p><b>Das ist DIE NAHT dieses Bereichs</b>, wie {@link Ladentext} fuer die Liste und
 * {@link Bonustext} fuer den Tagesbonus: Jeder Zweig von {@link LadenBefehle} holt sich
 * hier seine Zeilen und schickt sie nur hinaus. Dadurch ist jedes Wort, jede Farbe, jede
 * Verzweigung und jede Zahl ohne Server pruefbar.
 *
 * <p>Kein Wort steht hier: Der Wortlaut kommt aus {@code sprache-vorlage.yml} ueber
 * {@link MietSatz}. Was hier bleibt, ist die WAHL - welcher Satz zu welchem Bescheid
 * gehoert - und die eine Rechnung, die ein Spieler sonst selbst machen muesste: wie viel
 * ihm fehlt.
 *
 * <p><b>Absagen gehen ueber {@link Sprache#bau} und Zusagen ueber
 * {@link Sprache#eineZeile}</b>, und das ist eine Regel und kein Zufall: Drei Absagen
 * tragen absichtlich einen Hinweis in einer zweiten Zeile ("/laden zeigt, welche es
 * gibt"), und ein Betreiber darf jeder weiteren einen anhaengen. Bei einer ZUSAGE waere
 * ein Umbruch dagegen eine stille Verschiebung des Bildes - dort sichert
 * {@code eineZeile} zu, dass genau eine Zeile hinausgeht. Deshalb liefert auch das
 * Verlaengern eine Liste und keine einzelne Zeile.
 *
 * <p><b>Die Zuordnung Bescheid zu Satz steht HIER und nicht am Sendeort.</b> Sie wird hier
 * entschieden, und eine Probe kann sie unmittelbar aufrufen - dieselbe Begruendung wie bei
 * {@code Sprachschluessel.bescheid}. Jeder {@code switch} nennt jeden Bescheid, den seine
 * Tat zurueckgeben kann, OHNE {@code default}: Ein neuer Bescheid bricht damit den Bau und
 * nicht erst das Spielbild.
 */
final class Miettext {

    private Miettext() {
    }

    /**
     * Die Antwort auf {@code /laden mieten} - eine bis drei Zeilen.
     *
     * <p>Drei beim Erfolg: was gemietet wurde, was es gekostet hat, was taeglich faellig
     * wird. Drei und nicht eine, weil ein Abschluss drei verschiedene Dinge bindet - Ort,
     * Geld jetzt, Geld taeglich -, und weil die dritte Zeile den Befehl zum Verlaengern
     * nennt. Eine bei jeder Absage.
     */
    static List<Component> mietenZeilen(Sprache sprache, Mietbuch.Abschluss abschluss,
                                        String getippt) {
        // Ein switch als AUSDRUCK und nicht als Anweisung, und das ist der Grund, warum
        // hier kein default steht: Nur der Ausdruck verlangt Vollstaendigkeit. Mit einem
        // default fiele ein neuer Bescheid stumm hinein, statt den Bau zu brechen.
        return switch (abschluss.bescheid()) {
            case GEMIETET -> erfolgZeilen(sprache, abschluss);
            case KEINE_EINHEIT -> absage(sprache, MietSatz.ABSAGE_KEINE_EINHEIT,
                    Einsatz.von("laden", getippt));
            case SCHON_VERMIETET -> absage(sprache, MietSatz.ABSAGE_SCHON_VERMIETET,
                    Einsatz.von("laden", getippt));
            // SEIN Laden und nicht der gewuenschte - sonst sucht er, welchen er hat.
            case SCHON_EIN_LADEN -> absage(sprache, MietSatz.ABSAGE_SCHON_EIN_LADEN,
                    Einsatz.von("laden", abschluss.vertrag().einheit()));
            case ZU_WENIG_GELD -> absage(sprache, MietSatz.ABSAGE_ZU_WENIG_GELD, Einsatz
                    .von("kosten", sprache.betrag(abschluss.kosten()))
                    .mit("guthaben", sprache.betrag(abschluss.guthaben()))
                    // Die eine Rechnung, die das Programm dem Spieler abnimmt.
                    .mit("fehlt", sprache.betrag(
                            abschluss.kosten() - abschluss.guthaben())));
            case GESTOERT -> absage(sprache, MietSatz.ABSAGE_GESTOERT, Einsatz.keine());
            // Die Bescheide der anderen beiden Taten koennen hier nicht ankommen -
            // genannt, weil der Ausdruck jeden nennen muss.
            case VERLAENGERT, GEKUENDIGT, KEIN_LADEN, NOCH_ZU_FRUEH ->
                    throw new IllegalArgumentException(abschluss.bescheid()
                            + " kommt nicht aus mieten()");
        };
    }

    /**
     * Die Antwort auf {@code /laden verlaengern} - eine Zeile, bei einer Absage mit
     * Hinweis auch zwei.
     *
     * <p>Bei {@link Mietbuch.Bescheid#NOCH_ZU_FRUEH} stehen ZWEI Zahlen in der Zeile: wie
     * viele Tage noch laufen und wie lang eine Mietdauer ist. Nur mit beiden ist die
     * Absage zu verstehen - "noch 13 Tage" allein sagt nicht, dass bei 7 Tagen Mietdauer
     * erst unter 7 Resttagen verlaengert werden kann.
     */
    static List<Component> verlaengernZeilen(Sprache sprache,
                                             Mietbuch.Verlaengerung ergebnis,
                                             int dauerTage) {
        return switch (ergebnis.bescheid()) {
            case VERLAENGERT -> List.of(sprache.eineZeile(MietSatz.VERLAENGERT, Einsatz
                    .von("laden", ergebnis.vertrag().einheit())
                    .mit("bis", tag(sprache, ergebnis.vertrag()))
                    .mit("resttage", String.valueOf(ergebnis.restTage()))));
            case NOCH_ZU_FRUEH -> absage(sprache, MietSatz.ABSAGE_NOCH_ZU_FRUEH, Einsatz
                    .von("laden", ergebnis.vertrag().einheit())
                    .mit("resttage", String.valueOf(ergebnis.restTage()))
                    .mit("dauer", String.valueOf(dauerTage)));
            case KEIN_LADEN -> absage(sprache, MietSatz.ABSAGE_KEIN_LADEN,
                    Einsatz.keine());
            case GESTOERT -> absage(sprache, MietSatz.ABSAGE_GESTOERT, Einsatz.keine());
            case GEMIETET, GEKUENDIGT, KEINE_EINHEIT, SCHON_VERMIETET, SCHON_EIN_LADEN,
                 ZU_WENIG_GELD -> throw new IllegalArgumentException(ergebnis.bescheid()
                    + " kommt nicht aus verlaengern()");
        };
    }

    /**
     * Die Antwort auf {@code /laden kuendigen} - eine oder zwei Zeilen.
     *
     * <p>Zwei, wenn eine Kaution zurueckgeht. Eine, wenn keine hinterlegt war - "0 Mark
     * zurueck" sieht nach einem Fehler aus.
     */
    static List<Component> kuendigenZeilen(Sprache sprache,
                                           Mietbuch.Kuendigung ergebnis) {
        // Ein AUSDRUCK, siehe mietenZeilen: Nur er verlangt, dass jeder Bescheid
        // genannt ist.
        return switch (ergebnis.bescheid()) {
            case GEKUENDIGT -> {
                List<Component> zeilen = new ArrayList<>();
                zeilen.add(sprache.eineZeile(MietSatz.GEKUENDIGT,
                        Einsatz.von("laden", ergebnis.einheit())));
                if (ergebnis.kautionZurueck() > 0L) {
                    zeilen.add(sprache.eineZeile(MietSatz.GEKUENDIGT_KAUTION,
                            Einsatz.von("betrag",
                                    sprache.betrag(ergebnis.kautionZurueck()))));
                }
                yield List.copyOf(zeilen);
            }
            case KEIN_LADEN -> absage(sprache, MietSatz.ABSAGE_KEIN_LADEN,
                    Einsatz.keine());
            case GESTOERT -> absage(sprache, MietSatz.ABSAGE_GESTOERT, Einsatz.keine());
            case GEMIETET, VERLAENGERT, KEINE_EINHEIT, SCHON_VERMIETET, SCHON_EIN_LADEN,
                 NOCH_ZU_FRUEH, ZU_WENIG_GELD ->
                    throw new IllegalArgumentException(ergebnis.bescheid()
                            + " kommt nicht aus kuendigen()");
        };
    }

    /**
     * Die letzte Zeile von {@code /laden} - der eigene Laden.
     *
     * <p>Auch ohne Laden eine Zeile: Wer die Liste sieht, soll erfahren, wie man mietet.
     *
     * @param vertrag  der eigene Vertrag, oder {@code null}
     * @param restTage wie viele Tage er noch laeuft - gerechnet in der eingestellten
     *                 Zone, also NICHT hier
     */
    static Component standZeile(Sprache sprache, Mietvertrag vertrag, long restTage) {
        if (vertrag == null) {
            return sprache.eineZeile(MietSatz.STAND_KEINER, Einsatz.keine());
        }
        return sprache.eineZeile(MietSatz.STAND, Einsatz
                .von("laden", vertrag.einheit())
                .mit("bis", tag(sprache, vertrag))
                .mit("resttage", String.valueOf(restTage))
                // Die VEREINBARTE Miete und nicht die aus der config.yml - der
                // Bestandsschutz wird hier sichtbar, und genau hier muss er es sein.
                .mit("miete", sprache.betrag(vertrag.miete())));
    }

    /**
     * Die Zeilen zu einem Posten des taeglichen Laufs - fuer den Mieter, wenn er da ist.
     *
     * <p>Eine LEERE Liste fuer alles, was ihn nichts angeht: Wer bezahlt hat, bekommt
     * keine Nachricht. Das ist eine Entscheidung und keine Sparsamkeit - eine taegliche
     * Zeile "500 Mark Ladenmiete abgebucht" liest beim dritten Mal niemand mehr, und
     * dann liest er auch die Warnung nicht. Was zaehlt, steht in {@code /mark verlauf}.
     *
     * <p>Ein {@code switch} als AUSDRUCK ohne {@code default}, wie in dieser Datei
     * ueberall: Ein neuer Ausgang bricht den Bau und nicht erst das Spielbild.
     */
    static List<Component> laufZeilen(Sprache sprache, Mietlauf.Posten posten) {
        return switch (posten.ausgang()) {
            // Drei Ausgaenge, die den Spieler nichts angehen. GESTOERT ausdruecklich
            // auch nicht: Was dort schiefging, ist seine Sache nicht, es steht im
            // Protokoll, und der naechste Lauf versucht es erneut. Eine Zeile darueber
            // waere eine Sorge, die er nicht ausraeumen kann.
            case BEZAHLT, SCHON_BEZAHLT, GESTOERT -> List.of();
            case GEWARNT -> List.of(sprache.eineZeile(MietSatz.MIETE_GEWARNT, Einsatz
                    .von("laden", posten.vertrag().einheit())
                    .mit("betrag", sprache.betrag(posten.betrag()))
                    .mit("ausfall", String.valueOf(posten.ausfaelle()))
                    .mit("schranke", String.valueOf(
                            posten.ausfaelle() + posten.bisRaeumung()))));
            case GERAEUMT -> endeZeilen(sprache, posten,
                    sprache.eineZeile(MietSatz.MIETE_GERAEUMT, Einsatz
                            .von("laden", posten.vertrag().einheit())
                            .mit("ausfall", String.valueOf(posten.ausfaelle()))
                            .mit("schranke", String.valueOf(posten.ausfaelle()))
                            .mit("kaution",
                                    sprache.betrag(posten.vertrag().kaution()))));
            case ABGELAUFEN -> endeZeilen(sprache, posten,
                    sprache.eineZeile(MietSatz.MIETE_ABGELAUFEN, Einsatz
                            .von("laden", posten.vertrag().einheit())
                            .mit("bis", tag(sprache, posten.vertrag()))));
        };
    }

    /**
     * Die Zeile beim Beitritt, wenn der Laden in Verzug ist - oder keine.
     *
     * <p>Die einzige Stelle, an der ein Spieler von einem Ausfall erfaehrt, den er
     * verschlafen hat: Der taegliche Lauf laeuft, waehrend er offline ist. Ohne diese
     * Zeile kaeme er zurueck und faende seinen Laden weg, ohne je gewarnt worden zu sein.
     *
     * @param vertrag der eigene Vertrag, oder {@code null}
     */
    static List<Component> verzugZeilen(Sprache sprache, Mietvertrag vertrag,
                                        int schranke) {
        if (vertrag == null || !vertrag.inVerzug()) {
            return List.of();
        }
        return List.of(sprache.eineZeile(MietSatz.MIETE_VERZUG, Einsatz
                .von("laden", vertrag.einheit())
                .mit("betrag", sprache.betrag(vertrag.miete()))
                .mit("ausfall", String.valueOf(vertrag.ausfaelle()))
                .mit("schranke", String.valueOf(schranke))));
    }

    /**
     * Die Zeile ueber das Abholfach - oder keine, wenn es leer ist.
     *
     * <p>Gerufen beim Beitritt und von der Liste. Ein leeres Fach sagt NICHTS: Der
     * haeufigste Fall ist, dass noch nie etwas drin lag, und eine Zeile "dein Abholfach
     * ist leer" bei jedem Beitritt waere Laerm. Wer ausdruecklich fragt
     * ({@code /laden abholfach}), bekommt die Antwort - das ist {@link #abholZeilen}.
     */
    static List<Component> fachstandZeilen(Sprache sprache, int stapel) {
        if (stapel < 1) {
            return List.of();
        }
        return List.of(sprache.eineZeile(MietSatz.ABHOLFACH_STAND,
                Einsatz.von("stapel", String.valueOf(stapel))));
    }

    /**
     * Die Antwort auf {@code /laden abholfach} - eine bis drei Zeilen.
     *
     * <p>Drei im schlechtesten Fall: was geholt wurde, was noch wartet, und was sich
     * nicht zurueckverwandeln liess. Jede davon ist eine andere Nachricht, und
     * zusammengelegt waere keine davon zu verstehen.
     */
    static List<Component> abholZeilen(Sprache sprache, Abholbuch.Griff griff) {
        return switch (griff.bescheid()) {
            case GEHOLT -> {
                List<Component> zeilen = new ArrayList<>();
                zeilen.add(sprache.eineZeile(MietSatz.ABHOLFACH_GEHOLT,
                        Einsatz.von("stapel", String.valueOf(griff.geholt()))));
                // Nur, wenn wirklich was uebrig ist - "noch 0 Stapel" sieht nach einem
                // Fehler aus.
                if (griff.bleibt() > 0) {
                    zeilen.add(sprache.eineZeile(MietSatz.ABHOLFACH_BLEIBT,
                            Einsatz.von("stapel", String.valueOf(griff.bleibt()))));
                }
                unlesbar(sprache, griff, zeilen);
                yield List.copyOf(zeilen);
            }
            case FACH_LEER -> List.of(sprache.eineZeile(MietSatz.ABHOLFACH_LEER,
                    Einsatz.keine()));
            case KEIN_PLATZ -> List.of(sprache.eineZeile(MietSatz.ABHOLFACH_KEIN_PLATZ,
                    Einsatz.von("stapel", String.valueOf(griff.bleibt()))));
            case GESTOERT -> {
                // Bei einer Stoerung ist der unlesbare Stapel der WAHRSCHEINLICHE Grund,
                // und dann soll die Zeile ihn nennen statt "das hat nicht geklappt". Nur
                // wenn auch das nicht zutrifft, bleibt die allgemeine Absage.
                List<Component> zeilen = new ArrayList<>();
                unlesbar(sprache, griff, zeilen);
                if (zeilen.isEmpty()) {
                    yield absage(sprache, MietSatz.ABSAGE_GESTOERT, Einsatz.keine());
                }
                yield List.copyOf(zeilen);
            }
        };
    }

    /** Die Zeile ueber unlesbare Stapel - an EINER Stelle, weil zwei Zweige sie brauchen. */
    private static void unlesbar(Sprache sprache, Abholbuch.Griff griff,
                                 List<Component> hinein) {
        if (griff.unlesbar() > 0) {
            hinein.add(sprache.eineZeile(MietSatz.ABHOLFACH_UNLESBAR,
                    Einsatz.von("stapel", String.valueOf(griff.unlesbar()))));
        }
    }

    /**
     * Die zwei bis drei Zeilen zu einem beendeten Vertrag - Raeumung und Ablauf.
     *
     * <p>EINE Stelle fuer beide, weil beide dasselbe anhaengen: die Kaution (nur beim
     * Ablauf) und das Abholfach (nur, wenn etwas darin landete). Zwei Stellen liefen
     * auseinander, und dann erfuehre ein Spieler nach der einen Art von Ende nicht, wo
     * seine Ware liegt.
     */
    private static List<Component> endeZeilen(Sprache sprache, Mietlauf.Posten posten,
                                              Component kopf) {
        List<Component> zeilen = new ArrayList<>();
        zeilen.add(kopf);
        // DERSELBE Satz wie beim Kuendigen, und das ist richtig: Es ist dieselbe Tat -
        // die hinterlegte Kaution kommt zurueck. Ein eigener Satz daneben waere ein
        // zweiter Wortlaut fuer dieselbe Gutschrift.
        if (posten.kautionZurueck() > 0L) {
            zeilen.add(sprache.eineZeile(MietSatz.GEKUENDIGT_KAUTION, Einsatz
                    .von("betrag", sprache.betrag(posten.kautionZurueck()))));
        }
        if (posten.insFach() > 0) {
            zeilen.add(sprache.eineZeile(MietSatz.MIETE_INS_FACH,
                    Einsatz.von("stapel", String.valueOf(posten.insFach()))));
        }
        return List.copyOf(zeilen);
    }

    /**
     * Die Absage an den, der nicht in der Markthalle steht.
     *
     * <p>Auch dieser Satz wird HIER gewaehlt und nicht im Befehl, obwohl der Befehl den
     * Ort prueft: Sonst waere er die einzige Zeile dieses Bereichs, die eine Probe nicht
     * ohne Server holen kann - und genau solche Zeilen bleiben beim Umbenennen stehen.
     */
    static List<Component> falscheWeltZeilen(Sprache sprache) {
        return absage(sprache, MietSatz.ABSAGE_FALSCHE_WELT, Einsatz.keine());
    }

    /**
     * Eine Absage - ueber {@code bau} und nicht ueber {@code eineZeile}.
     *
     * <p>Die EINE Stelle, an der das geschieht: Sonst entscheidet jeder Zweig fuer sich,
     * ob eine Absage einen Hinweis tragen darf, und einer von ihnen wirft eines Tages,
     * weil der Betreiber genau dort eine zweite Zeile gewollt hat.
     */
    private static List<Component> absage(Sprache sprache, MietSatz satz, Einsatz werte) {
        return sprache.bau(satz, 1L, werte);
    }

    private static List<Component> erfolgZeilen(Sprache sprache,
                                                Mietbuch.Abschluss abschluss) {
        Mietvertrag vertrag = abschluss.vertrag();
        List<Component> zeilen = new ArrayList<>();
        zeilen.add(sprache.eineZeile(MietSatz.GEMIETET_KOPF, Einsatz
                .von("laden", vertrag.einheit())
                .mit("bis", tag(sprache, vertrag))
                // Die LAENGE des Vertrags, beide Tage eingeschlossen - nicht die
                // Restlaufzeit. Am Abschlusstag sind das zwei verschiedene Zahlen
                // fuer dasselbe Datum, und bis zum 09.11.2026 hiessen beide {tage}:
                // "laeuft bis 15.11. - 7 Tage" neben "bis 15.11. (6 Tage)" sah aus
                // wie ein Fehler und war keiner.
                .mit("miettage", String.valueOf(
                        vertrag.laeuftBis().toEpochDay() - vertrag.beginn().toEpochDay()
                                + 1L))));
        if (abschluss.lizenz()) {
            // Beide Zahlen: Wer 55.000 abgebucht sieht und 5.000 erwartet hat, soll in
            // derselben Zeile lesen, warum.
            zeilen.add(sprache.eineZeile(MietSatz.GEMIETET_ERSTMALS, Einsatz
                    .von("betrag", sprache.betrag(abschluss.kosten()))
                    .mit("lizenz", sprache.betrag(
                            abschluss.kosten() - vertrag.kaution()))));
        } else {
            zeilen.add(sprache.eineZeile(MietSatz.GEMIETET_KOSTEN,
                    Einsatz.von("betrag", sprache.betrag(abschluss.kosten()))));
        }
        zeilen.add(sprache.eineZeile(MietSatz.GEMIETET_TAEGLICH,
                Einsatz.von("miete", sprache.betrag(vertrag.miete()))));
        return List.copyOf(zeilen);
    }

    private static String tag(Sprache sprache, Mietvertrag vertrag) {
        return sprache.muster(MietSatz.TAG_MUSTER).format(vertrag.laeuftBis());
    }
}
