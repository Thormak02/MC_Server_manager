package de.freimarken.paper;

import static org.junit.jupiter.api.Assertions.assertEquals;

import de.freimarken.kern.Besitzbuch;
import de.freimarken.kern.Richtung;
import de.freimarken.kern.Wirtschaftswerte;
import de.freimarken.kern.Zeitwerte;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Das Fundament, zeichengenau: Die ausgelieferte Vorlage erzeugt das HEUTIGE Bild.
 *
 * <p>Das ist der Preis fuer die Entscheidung des Betreibers, Farbe und Einzug mitten in den
 * Satz zu schreiben - und er wird hier bezahlt. Verglichen wird die ALT-Serialisierung und
 * nicht {@code Component.equals}: Die Baumform unterscheidet sich (ein Elternteil mit
 * Kindern gegen eine Kette von {@code append}), und nur das Bild zaehlt.
 *
 * <p><b>Das erwartete Bild entstand MASCHINELL und nicht aus einer zweiten Lesung.</b>
 * Jede Zeile wurde mit der Erwartung "ROT" geschrieben, der Probenlauf war rot, und der
 * tatsaechliche Wert kam aus der Fehlermeldung. Danach wurde jeder Wert gegen die vor dem
 * Umzug gemessene Tafel des Zweiges gegengelesen. Haette eine Hand das Bild aus der
 * Quellzeile abgeschrieben und daraus auch die Vorlagenzeile, verglichen zwei Kopien
 * desselben Irrtums - ein verlorenes fuehrendes Leerzeichen waere in beiden.
 *
 * <p>Sie faengt: ein verlorenes Leerzeichen, eine verschobene Spalte, eine falsche Farbe,
 * einen vertauschten Wert und zwei gegeneinander vertauschte Saetze - dann sind zwei
 * Zeilen rot.
 *
 * <p><b>Die Werte, die eingesetzt werden, kommen aus der Datei und nicht aus dieser
 * Probe</b>, wo es einen Schluessel dafuer gibt: {@code parzelle.gratis} und die beiden
 * Wortstuecke der Liste werden ueber {@link Sprache#wort} geholt. Damit haengen auch die
 * {@code Art.WORT}-Schluessel an einer Bildprobe, obwohl sie selbst keine Zeile haben
 * koennen.
 */
class BildGleichTest {

    /**
     * Eine Zeile der Tabelle.
     *
     * @param bild das {@code &}-Bild, wie es heute im Spiel ankommt
     */
    record Zeile(Satz schluessel, long anzahl, Einsatz werte, String bild) {

        /** Der Name, unter dem Probe 11b sie wiederfindet: Pfad und Anzahl. */
        String marke() {
            return schluessel.pfad() + "|" + anzahl;
        }

        @Override
        public String toString() {
            return marke();
        }
    }

    /** Die fuenf Werte einer Listenzeile - zwei davon duerfen LEER sein. */
    private static Einsatz listenzeile(Sprache sprache, String x, String z, String preis,
                                       String mitbauer, String gruppe) {
        return Einsatz.von("x", x)
                .mit("z", z)
                .mit("preis", preis)
                .mit("mitbauer", mitbauer)
                .mit("gruppe", gruppe);
    }

    /**
     * Die Zeit in den Zeilen des Blockbuchs.
     *
     * <p>Mit Uhrzeit, denn {@code blockbuch.zeit-muster} traegt Tag UND Stunde - anders
     * als {@code spielzeit.tag-muster}. Eine Protokollzeile ohne Uhrzeit beantwortet
     * "wann" nicht, und bei zwanzig Taten an einem Tag ist genau die Stunde die Angabe,
     * auf die es ankommt.
     */
    private static final LocalDateTime BLOCKBUCH_ZEIT =
            LocalDateTime.of(2026, 11, 9, 14, 30);

    private static List<Zeile> tabelle() {
        Sprache sprache = Sprachprobe.spracheAusDemJar();
        return List.of(
                // ParzellenBefehle:225-226 vor dem Umzug, gemessen am alten Zweig.
                new Zeile(MarkSatz.VERLAUF_ZEILE, 1L, Einsatz.keine()
                        .mit("zeit", "02.11. 14:37")
                        .mit("betrag", "-25.000 Mark")
                        .mit("grund", "Parzelle genommen")
                        .mit("stand", "25.000 Mark"),
                        "&#a5b4bf  02.11. 14:37  -25.000 Mark  Parzelle genommen"
                                + "  (Stand 25.000 Mark)"),

                // geteilt
                new Zeile(ParzellenSatz.BEZAHLT, 1L, Einsatz.von("betrag", sprache.betrag(25_000L)).mit("rest", sprache.betrag(75_000L)),
                        "&#a5b4bf  Bezahlt: 25.000 Mark · Rest: 75.000 Mark"),
                new Zeile(ParzellenSatz.RANG_WERBUNG, 1L, Einsatz.von("werbung", Wirtschaftswerte.VORGABE_RANG_WERBUNG),
                        "&#e3c57eMehr erlaubt ein hoeherer Rang."),

                // /parzelle info
                new Zeile(ParzellenSatz.INFO_GRUPPENBODEN, 1L, Einsatz.keine(),
                        "&#c6a15bGruppenboden - war einmal Weg"),
                new Zeile(ParzellenSatz.INFO_WEG, 1L, Einsatz.keine(),
                        "&#a5b4bfDu stehst auf einem Weg - der gehoert allen."),
                new Zeile(ParzellenSatz.INFO_MAUER, 1L, Einsatz.keine(),
                        "&#a5b4bfDu stehst auf einer Mauer - die gehoert zur Strasse."),
                new Zeile(ParzellenSatz.INFO_KOPF, 1L, Einsatz.von("x", "3").mit("z", "-4"),
                        "&#c6a15bParzelle 3/-4"),
                new Zeile(ParzellenSatz.INFO_AUSSERHALB, 1L, Einsatz.keine(),
                        "&#a5b4bf  ausserhalb der Marktstadt - wird nicht vergeben"),
                new Zeile(ParzellenSatz.INFO_FREI, 1L, Einsatz.von("preis", sprache.betrag(25_000L)),
                        "&#a5b4bf  frei · &#73bf9625.000 Mark&#6e7a82 · /parzelle nehmen"),
                new Zeile(ParzellenSatz.INFO_FREI_AM_LIMIT, 1L, Einsatz.von("preis", sprache.betrag(25_000L)),
                        "&#a5b4bf  frei · 25.000 Mark"),
                new Zeile(ParzellenSatz.INFO_AM_LIMIT, 1L, Einsatz.von("anzahl", "1"),
                        "&#d9564d  Dein Rang erlaubt 1 Parzelle, und so viele hast du schon."),
                new Zeile(ParzellenSatz.INFO_AM_LIMIT, 2L, Einsatz.von("anzahl", "2"),
                        "&#d9564d  Dein Rang erlaubt 2 Parzellen, und so viele hast du schon."),
                new Zeile(ParzellenSatz.INFO_AM_LIMIT, 0L, Einsatz.von("anzahl", "0"),
                        "&#d9564d  Dein Rang erlaubt 0 Parzellen, und so viele hast du schon."),
                new Zeile(ParzellenSatz.INFO_EIGENTUEMER, 1L, Einsatz.von("name", "Anna"),
                        "&#a5b4bf  Eigentuemer: &#edf1f3Anna"),
                new Zeile(ParzellenSatz.INFO_GEKAUFT_FUER, 1L, Einsatz.von("preis", sprache.betrag(25_000L)),
                        "&#a5b4bf  Gekauft fuer: &#edf1f325.000 Mark"),
                new Zeile(ParzellenSatz.INFO_MITBAUER, 1L, Einsatz.von("namen", "Bert, Cara"),
                        "&#a5b4bf  Mitbauer: &#edf1f3Bert, Cara"),
                new Zeile(ParzellenSatz.INFO_GRUPPE, 1L, Einsatz.von("anzahl", "3"),
                        "&#edf1f3  Teil einer Gruppe aus 3 Feldern"),
                new Zeile(ParzellenSatz.INFO_AUFTRENNEN_HINWEIS, 1L, Einsatz.keine(),
                        "&#6e7a82  /parzelle auftrennen        dieses Feld herausloesen"),
                new Zeile(ParzellenSatz.INFO_ZUSAMMENLEGEN_HINWEIS, 1L, Einsatz.keine(),
                        "&#6e7a82  /parzelle zusammenlegen <nord|ost|sued|west>"),

                // /parzelle nehmen
                new Zeile(ParzellenSatz.NEHMEN_ERTEILT, 1L, Einsatz.von("x", "3").mit("z", "-4"),
                        "&#73bf96Parzelle 3/-4 gehoert jetzt dir"),
                new Zeile(ParzellenSatz.NEHMEN_ERSTE_GRATIS, 1L, Einsatz.keine(),
                        "&#a5b4bf  Die erste ist gratis."),
                new Zeile(ParzellenSatz.NEHMEN_KEIN_BAULAND, 1L, Einsatz.keine(),
                        "&#d9564dHier ist kein Bauland - stell dich auf eine Parzelle."),
                new Zeile(ParzellenSatz.NEHMEN_SCHON_VERGEBEN, 1L, Einsatz.von("name", "Anna"),
                        "&#d9564dDie gehoert schon &#edf1f3Anna"),
                new Zeile(ParzellenSatz.NEHMEN_GRENZE, 1L, Einsatz.von("anzahl", "1"),
                        "&#d9564dDu hast schon 1 Parzelle - mehr erlaubt dein Rang nicht."),
                new Zeile(ParzellenSatz.NEHMEN_GRENZE, 2L, Einsatz.von("anzahl", "2"),
                        "&#d9564dDu hast schon 2 Parzellen - mehr erlaubt dein Rang nicht."),
                new Zeile(ParzellenSatz.NEHMEN_GRENZE, 0L, Einsatz.von("anzahl", "0"),
                        "&#d9564dDu hast schon 0 Parzellen - mehr erlaubt dein Rang nicht."),
                new Zeile(ParzellenSatz.NEHMEN_ZU_WENIG, 1L, Einsatz.von("betrag", sprache.betrag(25_000L)).mit("guthaben", sprache.betrag(1_000L)),
                        "&#d9564dDie kostet &#c6a15b25.000 Mark&#d9564d, du hast 1.000 Mark."),
                new Zeile(ParzellenSatz.NEHMEN_AUSSERHALB, 1L, Einsatz.von("von", "-8").mit("bis", "8"),
                        "&#d9564dHier endet die Marktstadt. Parzellen gibt es von -8/-8 bis 8"
                                + "/8."),
                new Zeile(ParzellenSatz.NEHMEN_SONST, 1L, Einsatz.von("bescheid", Besitzbuch.Bescheid.SCHON_IN_GRUPPE.name()),
                        "&#d9564dDas ging nicht: SCHON_IN_GRUPPE"),

                // /parzelle liste
                new Zeile(ParzellenSatz.LISTE_LEER, 1L, Einsatz.keine(),
                        "&#e3c57eDu hast noch keine Parzelle. Die erste ist gratis - /parzell"
                                + "e nehmen"),
                new Zeile(ParzellenSatz.LISTE_KOPF, 1L, Einsatz.von("anzahl", "1").mit("hoechstzahl", "4"),
                        "&#c6a15bDeine Parzellen (1 von 4)"),
                new Zeile(ParzellenSatz.LISTE_ZEILE, 1L, listenzeile(sprache, "0", "0", sprache.wort(ParzellenSatz.GRATIS, Einsatz.keine()), "", ""),
                        "&#a5b4bf  0/0 · gratis"),
                new Zeile(ParzellenSatz.LISTE_ZEILE, 1L, listenzeile(sprache, "1", "0", sprache.betrag(25_000L),
                        sprache.wort(ParzellenSatz.LISTE_MITBAUER,
                                Einsatz.von("anzahl", "1")), ""),
                        "&#a5b4bf  1/0 · 25.000 Mark · 1 Mitbauer"),
                new Zeile(ParzellenSatz.LISTE_ZEILE, 1L, listenzeile(sprache, "2", "0", sprache.betrag(50_000L), "",
                        sprache.wort(ParzellenSatz.LISTE_GRUPPE, Einsatz.keine())),
                        "&#a5b4bf  2/0 · 50.000 Mark · Gruppe"),
                new Zeile(ParzellenSatz.LISTE_NAECHSTE, 1L, Einsatz.von("betrag", sprache.betrag(25_000L)),
                        "&#6e7a82Die naechste kostet 25.000 Mark"),

                // Die neunzehn Bescheide
                new Zeile(ParzellenSatz.BESCHEID_ERTEILT, 1L, Einsatz.keine(),
                        "&#d9564dErledigt."),
                new Zeile(ParzellenSatz.BESCHEID_KEINE_PARZELLE, 1L, Einsatz.keine(),
                        "&#d9564dHier ist keine Parzelle."),
                new Zeile(ParzellenSatz.BESCHEID_NOCH_FREI, 1L, Einsatz.keine(),
                        "&#d9564dDiese Parzelle gehoert noch niemandem - nimm sie mit /parzel"
                                + "le nehmen."),
                new Zeile(ParzellenSatz.BESCHEID_NOCH_FREI_AM_LIMIT, 1L, Einsatz.von("anzahl", "1"),
                        "&#d9564dDiese Parzelle gehoert noch niemandem - aber dein Rang erlau"
                                + "bt 1 Parzelle, und so viele hast du schon."),
                new Zeile(ParzellenSatz.BESCHEID_NOCH_FREI_AM_LIMIT, 2L, Einsatz.von("anzahl", "2"),
                        "&#d9564dDiese Parzelle gehoert noch niemandem - aber dein Rang erlau"
                                + "bt 2 Parzellen, und so viele hast du schon."),
                new Zeile(ParzellenSatz.BESCHEID_NOCH_FREI_AM_LIMIT, 0L, Einsatz.von("anzahl", "0"),
                        "&#d9564dDiese Parzelle gehoert noch niemandem - aber dein Rang erlau"
                                + "bt 0 Parzellen, und so viele hast du schon."),
                new Zeile(ParzellenSatz.BESCHEID_SCHON_VERGEBEN, 1L, Einsatz.keine(),
                        "&#d9564dDie gehoert schon jemandem."),
                new Zeile(ParzellenSatz.BESCHEID_AUSSERHALB, 1L, Einsatz.keine(),
                        "&#d9564dDas liegt ausserhalb der Marktstadt."),
                new Zeile(ParzellenSatz.BESCHEID_GRENZE_ERREICHT, 1L, Einsatz.von("anzahl", "1"),
                        "&#d9564dDein Rang erlaubt 1 Parzelle, und so viele hast du schon."),
                new Zeile(ParzellenSatz.BESCHEID_GRENZE_ERREICHT, 2L, Einsatz.von("anzahl", "2"),
                        "&#d9564dDein Rang erlaubt 2 Parzellen, und so viele hast du schon."),
                new Zeile(ParzellenSatz.BESCHEID_GRENZE_ERREICHT, 0L, Einsatz.von("anzahl", "0"),
                        "&#d9564dDein Rang erlaubt 0 Parzellen, und so viele hast du schon."),
                new Zeile(ParzellenSatz.BESCHEID_ZU_WENIG_MARK, 1L, Einsatz.keine(),
                        "&#d9564dDein Guthaben reicht nicht."),
                new Zeile(ParzellenSatz.BESCHEID_NICHT_EIGENTUEMER, 1L, Einsatz.keine(),
                        "&#d9564dDas darf nur der Eigentuemer."),
                new Zeile(ParzellenSatz.BESCHEID_SCHON_EINGETRAGEN, 1L, Einsatz.keine(),
                        "&#d9564dSteht schon auf der Liste."),
                new Zeile(ParzellenSatz.BESCHEID_NICHT_EINGETRAGEN, 1L, Einsatz.keine(),
                        "&#d9564dStand gar nicht auf der Liste."),
                new Zeile(ParzellenSatz.BESCHEID_TEIL_EINER_GRUPPE, 1L, Einsatz.keine(),
                        "&#d9564dDie Parzelle gehoert zu einer Gruppe - das geht nur ueber di"
                                + "e ganze Gruppe."),
                new Zeile(ParzellenSatz.BESCHEID_NACHBAR_FREI, 1L, Einsatz.keine(),
                        "&#d9564dDort drueben ist keine Parzelle vergeben."),
                new Zeile(ParzellenSatz.BESCHEID_NACHBAR_FREMD, 1L, Einsatz.keine(),
                        "&#d9564dDie Nachbarparzelle gehoert jemand anderem."),
                new Zeile(ParzellenSatz.BESCHEID_SCHON_IN_GRUPPE, 1L, Einsatz.keine(),
                        "&#d9564dDie beiden gehoeren zu zwei verschiedenen Grundstuecken. Tre"
                                + "nne eines auf und haenge die Felder einzeln an."),
                new Zeile(ParzellenSatz.BESCHEID_SCHON_ZUSAMMEN, 1L, Einsatz.keine(),
                        "&#d9564dDie beiden sind schon dasselbe Grundstueck."),
                new Zeile(ParzellenSatz.BESCHEID_GRUPPE_ZU_GROSS, 1L, Einsatz.von("anzahl", "1"),
                        "&#d9564dDein Rang erlaubt hoechstens 1 Feld je Gruppe."),
                new Zeile(ParzellenSatz.BESCHEID_GRUPPE_ZU_GROSS, 2L, Einsatz.von("anzahl", "2"),
                        "&#d9564dDein Rang erlaubt hoechstens 2 Felder je Gruppe."),
                new Zeile(ParzellenSatz.BESCHEID_GRUPPE_ZU_GROSS, 0L, Einsatz.von("anzahl", "0"),
                        "&#d9564dDein Rang erlaubt hoechstens 0 Felder je Gruppe."),
                new Zeile(ParzellenSatz.BESCHEID_KEINE_GRUPPE, 1L, Einsatz.keine(),
                        "&#d9564dHier ist keine Gruppe."),
                new Zeile(ParzellenSatz.BESCHEID_AUF_DEM_WEG, 1L, Einsatz.keine(),
                        "&#d9564dStell dich auf das Feld, das du meinst - nicht auf den Weg."),
                new Zeile(ParzellenSatz.BESCHEID_HAELT_ZUSAMMEN, 1L, Einsatz.keine(),
                        "&#d9564dDieses Feld haelt dein Grundstueck zusammen. Loese erst eine"
                                + "s am Rand heraus."),

                // /mark - Stand und Verlauf
                new Zeile(MarkSatz.STAND, 1L, Einsatz.von("betrag", sprache.betrag(25_000L)),
                        "&#a5b4bfDu hast &#c6a15b25.000 Mark&#a5b4bf."),
                new Zeile(MarkSatz.VERLAUF_HINWEIS, 1L, Einsatz.keine(),
                        "&#6e7a82/mark verlauf zeigt die letzten Buchungen."),
                new Zeile(MarkSatz.VERLAUF_LEER, 1L, Einsatz.keine(),
                        "&#a5b4bfNoch keine Buchungen."),
                new Zeile(MarkSatz.VERLAUF_KOPF, 1L, Einsatz.keine(),
                        "&#c6a15bDeine letzten Buchungen"),

                // Was mehrere Zweige von /parzelle teilen. Die vier Taten kommen
                // ueber Sprache.wort aus der Datei - damit haengen auch sie an
                // einer Bildprobe, obwohl ein Art.WORT selbst keine Zeile hat.
                new Zeile(ParzellenSatz.NICHT_IN_DER_MARKTSTADT, 1L, Einsatz.keine(),
                        "&#d9564dParzellen gibt es nur in der Marktstadt."),
                new Zeile(ParzellenSatz.STEHT_NICHT_AUF, 1L, Einsatz.von("x", "3").mit("z", "-4").mit("tat",
                        sprache.wort(ParzellenSatz.TAT_NICHTS_AUFGEGEBEN, Einsatz.keine())),
                        "&#d9564dDu stehst nicht auf Parzelle 3/-4. Nichts aufgegeben."),
                new Zeile(ParzellenSatz.STEHT_NICHT_AUF, 1L, Einsatz.von("x", "3").mit("z", "-4").mit("tat",
                        sprache.wort(ParzellenSatz.TAT_NICHTS_ZUSAMMENGELEGT, Einsatz.keine())),
                        "&#d9564dDu stehst nicht auf Parzelle 3/-4. Nichts zusammengelegt."),
                new Zeile(ParzellenSatz.STEHT_NICHT_AUF, 1L, Einsatz.von("x", "3").mit("z", "-4").mit("tat",
                        sprache.wort(ParzellenSatz.TAT_NICHTS_HERAUSGELOEST, Einsatz.keine())),
                        "&#d9564dDu stehst nicht auf Parzelle 3/-4. Nichts herausgeloest."),
                new Zeile(ParzellenSatz.STEHT_NICHT_AUF, 1L, Einsatz.von("x", "3").mit("z", "-4").mit("tat",
                        sprache.wort(ParzellenSatz.TAT_NICHTS_AUFGETRENNT, Einsatz.keine())),
                        "&#d9564dDu stehst nicht auf Parzelle 3/-4. Nichts aufgetrennt."),

                // /parzelle mitbauer
                new Zeile(ParzellenSatz.MITBAUER_KEINE_PARZELLE, 1L, Einsatz.keine(),
                        "&#d9564dHier ist keine vergebene Parzelle."),
                new Zeile(ParzellenSatz.MITBAUER_KOPF, 1L, Einsatz.von("x", "3").mit("z", "-4").mit("namen",
                        sprache.wort(ParzellenSatz.MITBAUER_NIEMAND, Einsatz.keine())),
                        "&#c6a15bMitbauer auf 3/-4: &#edf1f3niemand"),
                new Zeile(ParzellenSatz.MITBAUER_BEFEHLE, 1L, Einsatz.keine(),
                        "&#6e7a82/parzelle mitbauer hinzu <name> · weg <name>"),
                new Zeile(ParzellenSatz.MITBAUER_UNBEKANNT, 1L, Einsatz.keine(),
                        "&#d9564dDen kenne ich nicht. Er muss einmal auf dem Server gewesen sein."),
                new Zeile(ParzellenSatz.MITBAUER_HINZU_ERTEILT, 1L, Einsatz.von("name", "Bert"),
                        "&#73bf96Bert darf hier jetzt bauen."),
                new Zeile(ParzellenSatz.MITBAUER_SCHON_DRIN, 1L, Einsatz.von("name", "Bert"),
                        "&#e3c57eBert darf hier ohnehin bauen."),
                new Zeile(ParzellenSatz.MITBAUER_NIEMAND_MIT_DEM_NAMEN, 1L, Einsatz.keine(),
                        "&#d9564dSteht hier niemand mit dem Namen - /parzelle mitbauer zeigt die Liste."),
                new Zeile(ParzellenSatz.MITBAUER_WEG_ERTEILT, 1L, Einsatz.von("name", "Bert"),
                        "&#73bf96Bert darf hier nicht mehr bauen."),
                new Zeile(ParzellenSatz.MITBAUER_NICHT_DRIN, 1L, Einsatz.von("name", "Bert"),
                        "&#e3c57eBert stand gar nicht auf der Liste."),

                // /parzelle aufgeben - die Warnung ist EIN Satz mit drei Zeilen
                new Zeile(ParzellenSatz.AUFGEBEN_NICHT_DEINE, 1L, Einsatz.keine(),
                        "&#d9564dDas ist nicht deine Parzelle."),
                new Zeile(ParzellenSatz.AUFGEBEN_GRUPPE, 1L, Einsatz.keine(),
                        "&#d9564dDie Parzelle gehoert zu einer Gruppe - erst /parzelle auftrennen."),
                new Zeile(ParzellenSatz.AUFGEBEN_WARNUNG, 1L, Einsatz.von("x", "3").mit("z", "-4"),
                        "&#e3c57eParzelle 3/-4 aufgeben?\n&#a5b4bf  Es gibt kein Geld zurueck, und gebaut blei"
                                + "bt stehen.\n&#d9564d  /parzelle aufgeben bestaetigen 3 -4"),
                new Zeile(ParzellenSatz.AUFGEBEN_ERTEILT, 1L, Einsatz.von("x", "3").mit("z", "-4"),
                        "&#73bf96Parzelle 3/-4 ist wieder frei."),

                // /parzelle zusammenlegen - die Warnung ist EIN Satz mit fuenf Zeilen
                new Zeile(ParzellenSatz.ZUSAMMENLEGEN_WARNUNG, 1L, Einsatz.von("x", "3").mit("z", "-4").mit("nachbar-x", "3")
                        .mit("nachbar-z", "-5")
                        .mit("betrag", sprache.betrag(10_000L))
                        .mit("guthaben", sprache.betrag(25_000L)),
                        "&#e3c57eParzelle 3/-4 mit 3/-5 zusammenlegen?\n&#a5b4bf  Gebuehr: &#c6a15b10.000 Mark&#a5b4bf · d"
                                + "u hast 25.000 Mark\n"
                                        + "&#a5b4bf  Der Weg dazwischen wird dein Bauland.\n&#a5b4bf"
                                + "  Beim Auftrennen kommt er zurueck - und raeumt alles weg, was da"
                                + "nn\n&#a5b4bf  darauf steht."),
                new Zeile(ParzellenSatz.ZUSAMMENLEGEN_ZU_WENIG, 1L, Einsatz.keine(),
                        "&#d9564d  Dein Guthaben reicht nicht."),
                new Zeile(ParzellenSatz.ZUSAMMENLEGEN_BESTAETIGEN, 1L, Einsatz.von("richtung", Richtung.NORD.wort()).mit("x", "3")
                        .mit("z", "-4"),
                        "&#e3c57e  /parzelle zusammenlegen nord bestaetigen 3 -4"),
                new Zeile(ParzellenSatz.ZUSAMMENLEGEN_ERTEILT, 1L, Einsatz.von("anzahl", "2"),
                        "&#73bf96Ein Grundstueck aus 2 Feldern."),

                // /parzelle auftrennen - ganz und feldweise
                new Zeile(ParzellenSatz.AUFTRENNEN_ALLES_WARNUNG, 1L, Einsatz.von("anzahl", "3"),
                        "&#e3c57eGrundstueck aus 3 Feldern ganz auftrennen?\n&#a5b4bf  ALLE Wege zwischen den "
                                + "Feldern kommen zurueck. Geld gibt es keines zurueck.\n"
                                        + "&#a5b4bf  Fuer ei"
                                + "n einzelnes Feld: stell dich darauf und nimm /parzelle auftrennen"
                                + " ohne \"alles\"."),
                new Zeile(ParzellenSatz.AUFTRENNEN_ALLES_BESTAETIGEN, 1L, Einsatz.von("x", "3").mit("z", "-4"),
                        "&#d9564d  /parzelle auftrennen alles bestaetigen 3 -4"),
                new Zeile(ParzellenSatz.AUFTRENNEN_ERTEILT, 1L, Einsatz.von("anzahl", "3"),
                        "&#73bf96Aufgetrennt - 3 eigenstaendige Parzellen."),
                new Zeile(ParzellenSatz.AUFTRENNEN_WEG_KOMMT_ZURUECK, 1L, Einsatz.keine(),
                        "&#a5b4bf  Der Weg dorthin kommt zurueck. Geld gibt es keines zurueck."),
                new Zeile(ParzellenSatz.AUFTRENNEN_FELD_WARNUNG_ZWEI, 1L, Einsatz.von("anzahl", "2"),
                        "&#e3c57eGrundstueck aus 2 Feldern auftrennen?\n&#a5b4bf  Es hat nur zwei Felder - ein"
                                + "es allein geht nicht heraus, also wird\n"
                                        + "&#a5b4bf  es ganz aufgetrennt."),
                new Zeile(ParzellenSatz.AUFTRENNEN_FELD_WARNUNG, 1L, Einsatz.von("x", "3").mit("z", "-4").mit("anzahl", "3"),
                        "&#e3c57eDas Feld 3/-4 aus dem Grundstueck herausloesen?\n&#a5b4bf  Die anderen 3 Feld"
                                + "er bleiben ein Grundstueck."),
                new Zeile(ParzellenSatz.AUFTRENNEN_FELD_BESTAETIGEN, 1L, Einsatz.von("x", "3").mit("z", "-4"),
                        "&#d9564d  /parzelle auftrennen bestaetigen 3 -4"),
                new Zeile(ParzellenSatz.AUFTRENNEN_FELD_ERTEILT, 1L, Einsatz.von("x", "3").mit("z", "-4").mit("anzahl", "3"),
                        "&#73bf96Feld 3/-4 herausgeloest - das Grundstueck hat noch 3 Felder."),

                // Die Abschaetzung. Die drei gezaehlten Saetze brauchen je drei
                // Zeilen - eins, viele und die NULL. Bei 0 sendet der Sendeort gar
                // nichts; dass der Satz selbst dann die Mehrzahlform mit einer 0
                // ergaebe, haelt diese Zeile fest.
                new Zeile(ParzellenSatz.AUFWAND_VERSCHWINDET, 1L, Einsatz.von("anzahl", "1"),
                        "&#d9564d  Dabei verschwindet 1 Block von der Flaeche."),
                new Zeile(ParzellenSatz.AUFWAND_VERSCHWINDET, 2L, Einsatz.von("anzahl", "2"),
                        "&#d9564d  Dabei verschwinden 2 Bloecke von der Flaeche."),
                new Zeile(ParzellenSatz.AUFWAND_VERSCHWINDET, 0L, Einsatz.von("anzahl", "0"),
                        "&#d9564d  Dabei verschwinden 0 Bloecke von der Flaeche."),
                new Zeile(ParzellenSatz.AUFWAND_VERFUELLT, 1L, Einsatz.von("anzahl", "1"),
                        "&#a5b4bf  1 Fehlstelle wird wieder gefuellt."),
                new Zeile(ParzellenSatz.AUFWAND_VERFUELLT, 2L, Einsatz.von("anzahl", "2"),
                        "&#a5b4bf  2 Fehlstellen werden wieder gefuellt."),
                new Zeile(ParzellenSatz.AUFWAND_VERFUELLT, 0L, Einsatz.von("anzahl", "0"),
                        "&#a5b4bf  0 Fehlstellen werden wieder gefuellt."),
                new Zeile(ParzellenSatz.AUFWAND_NICHTS, 1L, Einsatz.keine(),
                        "&#a5b4bf  Auf der Wegflaeche steht nichts - es geht nichts verloren."),
                new Zeile(ParzellenSatz.ABKLINGZEIT, 1L, Einsatz.von("anzahl", "1"),
                        "&#a5b4bfEinen Augenblick - die Abschaetzung liest jeden Block ueber dem Weg. No"
                                + "ch 1 Sekunde."),
                new Zeile(ParzellenSatz.ABKLINGZEIT, 2L, Einsatz.von("anzahl", "2"),
                        "&#a5b4bfEinen Augenblick - die Abschaetzung liest jeden Block ueber dem Weg. No"
                                + "ch 2 Sekunden."),
                new Zeile(ParzellenSatz.ABKLINGZEIT, 0L, Einsatz.von("anzahl", "0"),
                        "&#a5b4bfEinen Augenblick - die Abschaetzung liest jeden Block ueber dem Weg. No"
                                + "ch 0 Sekunden."),

                // Die Bodenarbeit. Zwei Zeilen zu EINEM Schluessel, je eine fuer
                // eines der beiden Wortstuecke - sonst haengt eines von ihnen an
                // keiner Bildprobe.
                new Zeile(ParzellenSatz.BODEN_GELUNGEN, 1L, Einsatz.von("gelungen",
                        sprache.wort(ParzellenSatz.BODEN_WEG_IST_BAULAND, Einsatz.keine())).mit("anzahl", "623"),
                        "&#a5b4bf  Der Weg dazwischen ist Bauland (623 Bloecke geaendert)."),
                new Zeile(ParzellenSatz.BODEN_GELUNGEN, 1L, Einsatz.von("gelungen",
                        sprache.wort(ParzellenSatz.BODEN_WEG_IST_ZURUECK, Einsatz.keine())).mit("anzahl", "448"),
                        "&#a5b4bf  Der Weg ist zurueck (448 Bloecke geaendert)."),
                new Zeile(ParzellenSatz.BODEN_FEHLER, 1L, Einsatz.keine(),
                        "&#d9564d  Der Boden liess sich nicht aendern. Die Parzellen stimmen - sag einem"
                                + " Operator Bescheid."),

                // Bauschritt 3: /tpa, /tpahere, /tpaccept, /tpdeny. ANNA fragt, BERT
                // wird gefragt - zwei verschiedene Namen, weil jeder Satz dieses
                // Bereichs den des ANDEREN traegt.
                new Zeile(AnfragenSatz.NIEMAND, 1L, Einsatz.keine(),
                        "&#a5b4bfEs fragt niemand."),
                new Zeile(AnfragenSatz.FRAGE_NICHT_DA, 1L, Einsatz.von("name", "Bert"),
                        "&#d9564dBert ist nicht da."),
                new Zeile(AnfragenSatz.FRAGE_BEI_DIR, 1L, Einsatz.keine(),
                        "&#a5b4bfDu bist schon bei dir."),
                new Zeile(AnfragenSatz.FRAGE_GEBRAUCH_ZU_DIR, 1L, Einsatz.keine(),
                        "&#a5b4bfMit /tpa und dem Namen eines Spielers fragst du, ob du zu ihm kommen darfst."),
                new Zeile(AnfragenSatz.FRAGE_GEBRAUCH_HIERHER, 1L, Einsatz.keine(),
                        "&#a5b4bfMit /tpahere und dem Namen eines Spielers laedst du ihn zu dir ein."),
                new Zeile(AnfragenSatz.FRAGE_STEHT_NOCH, 1L, Einsatz.von("name", "Bert"),
                        "&#a5b4bfDiese Anfrage an Bert steht noch."),
                new Zeile(AnfragenSatz.FRAGE_GESTELLT, 1L, Einsatz.von("name", "Bert"),
                        "&#a5b4bfAngefragt bei Bert - er oder sie muss zustimmen."),
                new Zeile(AnfragenSatz.FRAGE_EINGELADEN, 1L, Einsatz.von("name", "Anna"),
                        "&#c6a15bAnna laedt dich ein zu kommen."),
                new Zeile(AnfragenSatz.FRAGE_MOECHTE_KOMMEN, 1L,
                        Einsatz.von("name", "Anna"),
                        "&#c6a15bAnna moechte zu dir kommen."),
                new Zeile(AnfragenSatz.FRAGE_HINWEIS, 1L, Einsatz.keine(),
                        "&#a5b4bf/tpaccept zum Annehmen, /tpdeny zum Ablehnen. Die Anfrage laeuft nach einer Minute ab."),
                new Zeile(AnfragenSatz.ANNEHMEN_FRAGENDER_WEG, 1L, Einsatz.keine(),
                        "&#d9564dWer gefragt hatte, ist nicht mehr da."),
                new Zeile(AnfragenSatz.ANNEHMEN_DU_KOMMST, 1L, Einsatz.von("name", "Anna"),
                        "&#73bf96Du kommst zu Anna."),
                new Zeile(AnfragenSatz.ANNEHMEN_ER_KOMMT, 1L, Einsatz.von("name", "Anna"),
                        "&#73bf96Anna kommt zu dir."),
                new Zeile(AnfragenSatz.ANNEHMEN_QUITTUNG_DU_KOMMST, 1L,
                        Einsatz.von("name", "Bert"),
                        "&#73bf96Bert hat angenommen - du kommst."),
                new Zeile(AnfragenSatz.ANNEHMEN_QUITTUNG_ER_KOMMT, 1L,
                        Einsatz.von("name", "Bert"),
                        "&#73bf96Bert hat angenommen und kommt zu dir."),
                // Die Zahlen sind NACKT, ohne Tausenderpunkt - darum steht in der
                // Mehrzahlform die 1000 und nicht noch eine 2.
                new Zeile(AnfragenSatz.ANNEHMEN_WEITERE, 1L, Einsatz.von("anzahl", "1"),
                        "&#a5b4bfEs warten noch 1 Anfrage."),
                new Zeile(AnfragenSatz.ANNEHMEN_WEITERE, 2L, Einsatz.von("anzahl", "1000"),
                        "&#a5b4bfEs warten noch 1000 Anfragen."),
                new Zeile(AnfragenSatz.ANNEHMEN_WEITERE, 0L, Einsatz.von("anzahl", "0"),
                        "&#a5b4bfEs warten noch 0 Anfragen."),
                new Zeile(AnfragenSatz.ABLEHNEN_ABGELEHNT, 1L, Einsatz.von("anzahl", "1"),
                        "&#a5b4bf1 Anfrage abgelehnt."),
                new Zeile(AnfragenSatz.ABLEHNEN_ABGELEHNT, 2L,
                        Einsatz.von("anzahl", "1000"),
                        "&#a5b4bf1000 Anfragen abgelehnt."),
                new Zeile(AnfragenSatz.ABLEHNEN_ABGELEHNT, 0L, Einsatz.von("anzahl", "0"),
                        "&#a5b4bf0 Anfragen abgelehnt."),
                // /freimarken spielzeit - Einzug, Spaltenbreite und Farbe stehen seit dem
                // 04.11.2026 IM Satz. Die erwarteten Bilder kommen aus dem ROTEN Lauf von
                // SpielzeitBefehleTest 33a-33h, der VOR dem Umzug gegen den committeten
                // Stand lief. Die eingesetzten Werte holt diese Tafel ueber Sprache#wort
                // und Sprache#muster aus derselben Datei - damit haengen auch die acht
                // Art.WORT und das Art.MUSTER dieses Bereichs an einer Bildprobe.,
                new Zeile(SpielzeitSatz.KOPF_SELBST, 1L,
                        Einsatz.keine(),
                        "&#c6a15bDeine Spielzeit"),
                new Zeile(SpielzeitSatz.KOPF_FREMD, 1L,
                        Einsatz.von("name", "Anna"),
                        "&#c6a15bSpielzeit von Anna"),
                new Zeile(SpielzeitSatz.HEUTE, 1L,
                        Einsatz.von("dauer", sprache.wort(SpielzeitSatz.DAUER_MINUTEN,
                                Einsatz.von("minuten", "10")))
                        .mit("tag", sprache.muster(SpielzeitSatz.TAG_MUSTER)
                                .format(LocalDate.of(2026, 11, 3)))
                        .mit("zone", Zeitwerte.VORGABE_ZONE.getId()),
                        "&#a5b4bf  heute   10 min   (03.11.2026, Tagesgrenze Europe/Berlin)"),
                new Zeile(SpielzeitSatz.GESAMT, 1L,
                        Einsatz.von("dauer", sprache.wort(SpielzeitSatz.DAUER_MINUTEN,
                                Einsatz.von("minuten", "10"))),
                        "&#a5b4bf  gesamt  10 min"),
                // Die Spalte: "taetig" ist wie "gesamt" sechs Zeichen lang, also ZWEI
                // Leerzeichen - die Dauer beginnt in allen drei Zeilen in Spalte 11.
                // Genau das haelt diese Zeile fest; eine Zaehlung der Zeilen wuerde ein
                // verrutschtes Leerzeichen nie sehen.
                new Zeile(SpielzeitSatz.TAETIG, 1L,
                        Einsatz.von("dauer", sprache.wort(SpielzeitSatz.DAUER_MINUTEN,
                                Einsatz.von("minuten", "6")))
                        .mit("heute", sprache.wort(SpielzeitSatz.DAUER_MINUTEN,
                                Einsatz.von("minuten", "4")))
                        .mit("prozent", "60"),
                        "&#a5b4bf  taetig  6 min   (4 min heute, 60 % der Zeit)"),
                new Zeile(SpielzeitSatz.ZUSTAND_UNTAETIG, 1L,
                        Einsatz.von("dauer", sprache.wort(SpielzeitSatz.DAUER_MINUTEN,
                                Einsatz.von("minuten", "12")))
                        .mit("grenze", "10"),
                        "&#a5b4bf  wird gerade NICHT gezaehlt - untaetig seit 12 min, gezaehlt wird bis 10 min. Ein Schritt oder ein Tastendruck zaehlt wieder."),
                new Zeile(SpielzeitSatz.ZUSTAND_GEZAEHLT, 1L,
                        Einsatz.von("menge", sprache.wort(SpielzeitSatz.DAUER_SEKUNDEN,
                                Einsatz.von("sekunden", "0")))
                        .mit("abstand", sprache.wort(
                                SpielzeitSatz.ZUSTAND_ABSTAND_TAKT,
                                Einsatz.von("takt", "60")))
                        .mit("abwesenheit", sprache.wort(
                                SpielzeitSatz.ZUSTAND_ABWESENHEIT,
                                Einsatz.von("pfad", Zeitwerte.PFAD_UNTAETIG))),
                        "&#a5b4bf  wird gezaehlt - die letzten 0 s sind noch nicht geschrieben, das geschieht alle 60 s. Abwesenheit zaehlt auf diesem Server mit (zeit.untaetig-minuten: 0)."),
                new Zeile(SpielzeitSatz.HINWEIS, 1L,
                        Einsatz.keine(),
                        "&#a5b4bf  Gezaehlt wird erst seit dem Einbau dieser Messung - Spielstunden von davor stehen nirgends, jeder faengt bei 0 an."),
                new Zeile(SpielzeitSatz.NICHT_LESBAR, 1L,
                        Einsatz.keine(),
                        "&#a5b4bf  Die Spielzeit ist gerade nicht lesbar - gezaehlt wird trotzdem. Sag einem Operator Bescheid."),
                new Zeile(SpielzeitSatz.KEINE_KONSOLE, 1L,
                        Einsatz.keine(),
                        "&#a5b4bfDie Konsole spielt nicht. /freimarken spielzeit <spieler> nennt die Zeit eines anwesenden Spielers."),
                new Zeile(SpielzeitSatz.NICHT_DA, 1L,
                        Einsatz.von("name", "Anna"),
                        "&#d9564dAnna ist nicht da. Gezeigt werden kann nur die Spielzeit eines anwesenden Spielers."),

                // Bauschritt 5, erster Teil: das Reisen selbst - Reise und
                // Aufwaermzeit. Die erwarteten Bilder kommen aus dem ROTEN Lauf von
                // SendewegTest 33ac bis 33ae, der VOR dem Umzug gegen den committeten
                // Stand lief, und sind dort mit denselben Zeichen noch einmal
                // festgenagelt.
                //
                // {ziel} ist ZWEIERLEI gefuellt, und das ist Absicht: "kornmark" dort,
                // wo nur ein Weltname ankommen kann, und "Bert" dort, wo auch ein
                // SPIELERNAME ankommt (/tpaccept - dann liest der Reisende den Namen
                // des anderen). Ein Satz, der nur zu einem Weltnamen passte, faellt so
                // auf.
                new Zeile(ReiseSatz.SCHON_DA, 1L, Einsatz.von("ziel", "kornmark"),
                        "&#a5b4bfDu bist schon in kornmark."),
                new Zeile(ReiseSatz.KEIN_PLATZ_AM_ERSCHEINUNGSPUNKT, 1L,
                        Einsatz.von("ziel", "kornmark"),
                        "&#d9564dIn kornmark ist am Erscheinungspunkt kein Platz zum Stehen. Das ist nichts, was du beheben kannst - melde es."),
                // Die fuenf Hindernisse. Zwei von ihnen unterscheiden sich in GENAU
                // EINEM Wort (Welthoehe gegen Weltgrenze), und eines sagt "nicht MEHR"
                // statt "nicht" - drei Stellen, an denen ein Abschreiben ohne Zeugen
                // stillschweigend angleicht.
                new Zeile(ReiseSatz.HINDERNIS_WELT_NICHT_GELADEN, 1L, Einsatz.keine(),
                        "&#d9564dDorthin geht es nicht mehr - die Welt ist nicht geladen."),
                new Zeile(ReiseSatz.HINDERNIS_ZU_PFERD, 1L, Einsatz.keine(),
                        "&#d9564dNicht zu Pferd - absteigen, dann reisen. Das Reittier bleibt zurueck."),
                new Zeile(ReiseSatz.HINDERNIS_AUSSERHALB_DER_WELTHOEHE, 1L,
                        Einsatz.keine(),
                        "&#d9564dDorthin geht es nicht - der Ort liegt ausserhalb der Welthoehe."),
                new Zeile(ReiseSatz.HINDERNIS_AUSSERHALB_DER_WELTGRENZE, 1L,
                        Einsatz.keine(),
                        "&#d9564dDorthin geht es nicht - der Ort liegt ausserhalb der Weltgrenze."),
                new Zeile(ReiseSatz.HINDERNIS_KEIN_PLATZ_MEHR, 1L, Einsatz.keine(),
                        "&#d9564dDort ist kein Platz zum Stehen mehr."),
                // Die zwei Zutrittssaetze. Sie gehoeren NICHT zu den fuenf
                // Hindernissen darueber: Jene sagen, dass es dort gerade nicht
                // geht, diese, dass es fuer DICH nicht geht. Beide tragen {ziel},
                // und {ziel} ist bei /tpa ein SPIELERname - darum steht hier Bert
                // und nicht kornmark.
                new Zeile(ReiseSatz.ZUTRITT_NUR_FUER_MITGLIEDER, 1L,
                        Einsatz.von("ziel", "Bert"),
                        "&#d9564dDie Reise nach Bert geht nicht - diese Welt steht nur Mitgliedern offen."),
                new Zeile(ReiseSatz.ZUTRITT_RANG_ZU_NIEDRIG, 1L,
                        Einsatz.von("ziel", "Bert").mit("rang", "siedler"),
                        "&#d9564dDie Reise nach Bert geht noch nicht - diese Welt verlangt den Rang siedler."),
                new Zeile(ReiseSatz.LEINE_REISST, 1L, Einsatz.keine(),
                        "&#e3c57eAngeleinte Tiere bleiben zurueck - eine Leine reisst beim Weltwechsel."),
                new Zeile(ReiseSatz.NICHT_GEKLAPPT, 1L, Einsatz.von("ziel", "Bert"),
                        "&#d9564dDie Reise nach Bert hat nicht geklappt."),
                // Die Abklingzeit ist der EINZIGE gezaehlte Satz dieses Bereichs. Die
                // Mehrzahlform steht mit der DREI da und nicht mit einer groesseren
                // Zahl: ZWISCHEN_ZWEI_REISEN sind drei Sekunden, mehr kann der
                // aufgerundete Rest nicht sein. Die Null kann im Spiel nicht ankommen -
                // ihre Wache sitzt in Reise.zuSchnellHintereinander -, aber sie steht
                // hier, weil Probe 11b sie verlangt und weil sie festhaelt, was eine 0
                // ergaebe: die Mehrzahlform, und nicht etwa eine erfundene dritte.
                new Zeile(ReiseSatz.ABKLINGZEIT, 1L, Einsatz.von("anzahl", "1"),
                        "&#a5b4bfEinen Augenblick - noch 1 Sekunde."),
                new Zeile(ReiseSatz.ABKLINGZEIT, 2L, Einsatz.von("anzahl", "3"),
                        "&#a5b4bfEinen Augenblick - noch 3 Sekunden."),
                new Zeile(ReiseSatz.ABKLINGZEIT, 0L, Einsatz.von("anzahl", "0"),
                        "&#a5b4bfEinen Augenblick - noch 0 Sekunden."),
                // Die Wartezeit traegt ZWEI Werte. Die Sekunden kommen aus
                // Aufwaermzeit.DAUER und nicht aus der Datei - darum stehen sie hier als
                // eingesetzter Wert und nicht als Wort im erwarteten Bild.
                new Zeile(ReiseSatz.WARTEZEIT, 1L,
                        Einsatz.von("sekunden", "3").mit("ziel", "Bert"),
                        "&#a5b4bfNach 3 Sekunden geht es nach Bert - bis dahin stehen bleiben."),
                new Zeile(ReiseSatz.ABBRUCH_BEWEGT, 1L, Einsatz.von("ziel", "Bert"),
                        "&#d9564dBewegt - die Reise nach Bert ist abgebrochen."),
                new Zeile(ReiseSatz.ABBRUCH_SCHADEN, 1L, Einsatz.von("ziel", "Bert"),
                        "&#d9564dSchaden genommen - die Reise nach Bert ist abgebrochen."),

                // Bauschritt 5, zweiter Teil: die zwei BEFEHLE des Reisens. Die
                // erwarteten Bilder kommen aus dem ROTEN Lauf von SendewegTest 33af und
                // 33ag, der VOR dem Umzug gegen den committeten Stand lief.
                //
                // ZWEI dieser vier Zeilen sind MEHRZEILIG, und das ist der Gewinn dieses
                // Zuges: Bei warp.ziele stehen drei Chatzeilen in einem Schluessel, bei
                // warp.kein-ziel zwei. Damit ist der ZEILENBAU festgenagelt und nicht nur
                // die Farbe - wer eine Umbruchmarke aus der Vorlage nimmt, bekommt hier
                // eine rote Zeile und nicht im Spiel eine zusammengeschobene Auskunft.
                //
                // {liste} kommt hier FERTIG herein, so wie der Zweig sie uebergibt, und
                // die zwei Trenner sind darin verschieden: zwei Leerzeichen in der
                // Auskunft, Komma und Leerzeichen in der Absage. Dass die Trenner selbst
                // aus der Datei kommen, haelt SendewegTest 33af - es ruft die reine
                // Methode, die sie holt.
                new Zeile(ReiseSatz.WARP_ZIELE, 1L,
                        Einsatz.von("liste", "marktstadt  kornmark  nether"),
                        "&#c6a15bReiseziele\n&#edf1f3marktstadt  kornmark  nether\n&#a5b4bf/warp <ziel> - "
                                + "oder durch ein Tor am Bundesmarkt."),
                // {eingabe} ist der GETIPPTE Name. Hier steht ein Farbtag darin, und
                // genau das ist die Zusicherung: Er kommt als TEXT an. Wuerde der Wert
                // vor dem Parsen in die Zeichenkette gespleisst, stuende hier ein roter
                // Name ohne die Zeichen - und ein Spieler koennte seinen eigenen Chat
                // faerben.
                new Zeile(ReiseSatz.WARP_KEIN_ZIEL, 1L,
                        Einsatz.von("eingabe", "<red>boese</red>")
                        .mit("liste", "marktstadt, kornmark, nether"),
                        "&#d9564dDorthin geht keine Reise: <red>boese</red>\n&#a5b4bfMoeglich sind: "
                                + "marktstadt, kornmark, nether"),
                // KLEIN geschrieben, und das ist Absicht: Hier kommt der AUFGELOESTE Name
                // an und nicht die Eingabe. Dass der Zweig wirklich den aufgeloesten
                // uebergibt, haelt AufrufstelleTest 34r mit /warp KORNMARK.
                new Zeile(ReiseSatz.WARP_WELT_FEHLT, 1L, Einsatz.von("welt", "kornmark"),
                        "&#d9564dDie Welt kornmark gibt es gerade nicht."),
                new Zeile(ReiseSatz.ZURUECK_KEIN_ORT_GEMERKT, 1L, Einsatz.keine(),
                        "&#a5b4bfEs ist kein Ort gemerkt - du bist noch nirgends gewesen, wohin"
                                + " es zurueckgeht."),

                // Bauschritt 6, erster Zug: der Handbalken der Schutzwache. Die
                // erwarteten Bilder kommen aus dem ROTEN Lauf von SendewegTest
                // 33aj, der VOR dem Umzug gegen den committeten Stand lief - dort
                // stehen dieselben Zeichen ein zweites Mal.
                //
                // Die ERSTEN VIER Art.HANDBALKEN des Projekts, und damit die
                // ersten Zeilen dieser Tabelle, bei denen ein zweiter Zeilenbau
                // gar nicht moeglich ist: Eine Umbruchmarke darin ist schon beim
                // Laden eine Klage.
                new Zeile(WacheSatz.SCHUTZ_WEG_UND_MAUER, 1L, Einsatz.keine(),
                        "&#e3c57eWeg und Mauer gehoeren allen"),
                new Zeile(WacheSatz.SCHUTZ_PARZELLE_FREI, 1L, Einsatz.keine(),
                        "&#e3c57eDiese Parzelle ist frei - /parzelle nehmen"),
                new Zeile(WacheSatz.SCHUTZ_AUSSERHALB, 1L, Einsatz.keine(),
                        "&#e3c57eHier endet die Marktstadt"),
                // Der Farbwechsel MITTEN im Satz, und das Leerzeichen davor: Es
                // gehoert zum roten Stueck. Hinter dem Wechsel saehe der Spieler
                // dasselbe, diese Zeile aber nicht - genau dafuer vergleicht sie
                // die Alt-Serialisierung und nicht den Baum.
                new Zeile(WacheSatz.SCHUTZ_FREMDE_PARZELLE, 1L,
                        Einsatz.von("eigentuemer", "Bert"),
                        "&#d9564dDiese Parzelle gehoert &#edf1f3Bert"),

                // Teil C: die drei Handbalken des Ladenschutzes. DERSELBE Aufbau wie
                // fremde-parzelle darueber - Absage rot, der fremde Name weiss: Ein
                // Spieler soll die zwei als dieselbe Art von Auskunft lesen.
                //
                // BEIDE Werte in der ersten Zeile, und der Name des Standes steht
                // VORN: In einer Markthalle stehen die Staende nebeneinander, und wer
                // nur "der Laden von Anna" liest, raet, an welchem er gerade greift.
                new Zeile(WacheSatz.SCHUTZ_FREMDER_LADEN, 1L,
                        Einsatz.von("laden", "halle-1").mit("mieter", "Anna"),
                        "&#d9564dhalle-1 ist der Laden von &#edf1f3Anna"),
                // GELB wie parzelle-frei und mit dem Weg darin: Hier darf AUCH niemand
                // bauen, aber es gibt etwas zu tun. Eine Absage ohne Ausweg liest sich
                // wie ein Fehler.
                new Zeile(WacheSatz.SCHUTZ_LADEN_FREI, 1L, Einsatz.keine(),
                        "&#e3c57eDieser Stand ist frei - /laden mieten"),
                // ROT und OHNE Behauptung darueber, wem der Stand gehoert: Bei einer
                // stummen Datenbank weiss das niemand, und beide anderen Zeilen waeren
                // erfunden.
                new Zeile(WacheSatz.SCHUTZ_LADEN_GESTOERT, 1L, Einsatz.keine(),
                        "&#d9564dDie Markthalle ist gerade nicht nachsehbar - versuch es noch"
                                + " einmal."),
                // ROT und OHNE Ausweg, anders als laden-frei: Diese Kiste ist nicht zu
                // mieten und nicht freizugeben. Ein "frag das Team" stuende hier als
                // Aufforderung, die das Team nicht erfuellen kann.
                new Zeile(WacheSatz.SCHUTZ_EINRICHTUNG, 1L, Einsatz.keine(),
                        "&#d9564dDiese Kiste gehoert zur Stadt"),

                // --- Die Stadtwache [11.11.2026] ----------------------------------
                // GELB fuer die vier Lagen, in denen niemand etwas falsch gemacht hat,
                // ROT fuer die beiden, die eine fremde Sache oder einen Ausfall nennen -
                // dieselbe Teilung wie oben bei Parzelle und Laden.
                //
                // Zwei davon tragen KEINEN Ausweg, und das ist Absicht: In der Leere
                // wird nie jemand bauen duerfen, und das Ankunftsfeld ist nicht zu
                // gruenden. Ein erfundener Ausweg waere dort schlimmer als keiner.
                new Zeile(WacheSatz.SCHUTZ_STADT_LEERE, 1L, Einsatz.keine(),
                        "&#e3c57eZwischen den Staedten gehoert nichts niemandem - und gebaut"
                                + " wird hier nicht"),
                new Zeile(WacheSatz.SCHUTZ_STADT_AUSSERHALB, 1L, Einsatz.keine(),
                        "&#e3c57eHier enden die Freistaedte"),
                new Zeile(WacheSatz.SCHUTZ_ANKUNFTSFELD, 1L, Einsatz.keine(),
                        "&#e3c57eDas Ankunftsfeld gehoert allen - deine eigene Stadt gruendest"
                                + " du mit /freistadt gruenden"),
                // Die verfallene Stadt [Teil E5]. EIN EIGENER Balken und nicht
                // stadt-frei: Jener sagt "hier ist noch nichts, gruende" - eine Luege
                // ueber ein Feld, das vergeben ist.
                new Zeile(WacheSatz.SCHUTZ_STADT_VERFALLEN, 1L, Einsatz.keine(),
                        "&#d9564dDiese Stadt ist verfallen - hier baut niemand.&#a5b4bf Gehoert"
                                + " sie dir, bleib einen Augenblick: Sie wacht von"
                                + " selbst wieder auf."),
                new Zeile(WacheSatz.SCHUTZ_STADT_FREI, 1L, Einsatz.keine(),
                        "&#e3c57eDieses Stadtfeld ist frei - /freistadt gruenden"),
                // ZWEI Werte und zwei Farbwechsel - derselbe Aufbau wie
                // fremde-parzelle, nur dass hier auch der NAME der Stadt steht. Mit ihm
                // findet ein Besucher den Weg zurueck: /warp freistadt neustadt.
                new Zeile(WacheSatz.SCHUTZ_FREMDE_STADT, 1L,
                        Einsatz.von("stadt", "neustadt").mit("eigentuemer", "Bert"),
                        "&#d9564dFreistadt &#edf1f3neustadt&#d9564d gehoert &#edf1f3Bert"),
                // Teil C2: ein vergebenes Grundstueck. DERSELBE Aufbau wie
                // fremde-parzelle - rot mit weissem Namen -, und ohne Stadtnamen: Wer
                // hier steht, ist schon in der Stadt und sucht nur den Besitzer.
                new Zeile(WacheSatz.SCHUTZ_FREMDES_GRUNDSTUECK, 1L,
                        Einsatz.von("eigentuemer", "Bert"),
                        "&#d9564dDieses Grundstueck gehoert &#edf1f3Bert"),
                // Teil D4: der Buerger, dem seine Stadt diese Tat nicht hergibt. Das
                // {recht} ist das WORT aus freistadt.rechte.wort - derselbe Wortlaut,
                // den /freistadt rechte liest.
                new Zeile(WacheSatz.SCHUTZ_BUERGER_DARF_NICHT, 1L,
                        Einsatz.von("recht", "Truhen und Oefen oeffnen"),
                        "&#d9564dStadtbuerger duerfen hier nicht &#edf1f3Truhen und Oefen"
                                + " oeffnen"),
                new Zeile(WacheSatz.SCHUTZ_STADT_GESTOERT, 1L, Einsatz.keine(),
                        "&#d9564dDie Freistaedte sind gerade nicht nachsehbar - versuch es noch"
                                + " einmal."),

                // Bauschritt 6, zweiter Zug: die Ankunftswache und die Portalwache.
                // Die erwarteten Bilder kommen aus dem ROTEN Lauf von SendewegTest
                // 33ak und 33al, der VOR dem Umzug gegen den committeten Stand lief.
                //
                // Die ZWEI ersten Zeilen sind einzeilig, obwohl ihr Wortlaut im
                // Programm ueber drei bzw. zwei Quellzeilen mit + zusammengesetzt war:
                // Ein Umbruch im Quelltext ist keiner im Chat. Genau diese
                // Unterscheidung ist hier festgenagelt - wer eine Umbruchmarke
                // hineinschreibt, bekommt eine rote Zeile.
                new Zeile(WacheSatz.ANKUNFT_KEIN_SPAWNPUNKT, 1L, Einsatz.keine(),
                        "&#a5b4bfHier wird kein Spawnpunkt gesetzt - gestorben wird immer nach"
                                + " Altfurt. Schlafen geht weiter."),
                new Zeile(WacheSatz.ANKUNFT_ABENTEUER, 1L, Einsatz.keine(),
                        "&#a5b4bfHier ist Abenteuer: ansehen und benutzen, aber nicht abbauen."
                                + " Gebaut wird in der Marktstadt."),
                // Die Rettung: EIN Satz fuer beide Gruende, und darum nennt er
                // keinen. Welcher es war, steht im Serverprotokoll.
                new Zeile(WacheSatz.ANKUNFT_HEIMGEHOLT, 1L,
                        Einsatz.von("welt", "freimarken:vogtei")
                                .mit("ziel", "freimarken:altfurt"),
                        "&#d9564dfreimarken:vogtei steht dir nicht mehr offen - du bist jetzt in freimarken:altfurt."),
                // Und der Gegenfall: ZWEI Chatzeilen aus EINEM Schluessel, rot und
                // grau. Der Schwanz " - oder durch ein Tor am Bundesmarkt." steht
                // damit zum zweiten Mal in dieser Tabelle - oben in
                // ReiseSatz.WARP_ZIELE. Die zwei duerfen ab jetzt auseinanderlaufen;
                // bis zu diesem Zug kam dieser hier aus dem Programm, und darum trug
                // warp.ziele die Marke HALB IN KRAFT (Torhinweis).
                new Zeile(WacheSatz.PORTAL_KEIN_AUSGANG, 1L, Einsatz.keine(),
                        "&#d9564dHier fuehrt kein Portal hinaus.\n&#a5b4bfNether und Ende erreichst"
                                + " du mit /warp nether und /warp end - oder durch ein"
                                + " Tor am Bundesmarkt."),

                // Bauschritt 7, der letzte Textumzug: /freimarken und
                // /freimarken preis. Die neun erwarteten Bilder kommen aus dem
                // ROTEN Lauf dieser Tabelle gegen den committeten Stand c8bd801;
                // abgeschrieben wurde keines.
                //
                // Die drei Werte von stand.version sind PROBEWERTE und nicht die
                // des laufenden Servers: getPluginMeta(), Runtime.version() und
                // getServer() sind ohne Server nicht zu haben. Was diese Zeile
                // festnagelt, ist der Rahmen um sie herum - Stand, die zwei
                // Mittelpunkte und das Wort Java.
                new Zeile(BundSatz.STAND_KOPF, 1L, Einsatz.keine(),
                        "&#c6a15bDer Freimarkenbund"),
                new Zeile(BundSatz.STAND_VERSION, 1L, Einsatz.von("version", "0.1.0")
                        .mit("java", "25").mit("minecraft", "1.21.4"),
                        "&#a5b4bfStand 0.1.0 · Java 25 · 1.21.4"),
                new Zeile(BundSatz.STAND_ABLAGE_FLUECHTIG, 1L, Einsatz.keine(),
                        "&#d9564dAblage fluechtig - nichts ueberlebt den Neustart"),
                new Zeile(BundSatz.STAND_ABLAGE_MARIADB, 1L, Einsatz.keine(),
                        "&#6e7a82Ablage MariaDB"),
                // Die zwei Zeilen mit woertlichen spitzen Klammern. In der Vorlage
                // stehen sie maskiert; ankommen muss die blanke Klammer.
                new Zeile(BundSatz.STAND_BEFEHLE, 1L, Einsatz.keine(),
                        "&#a5b4bf/parzelle · /mark · /freimarken preis <anzahl>"
                                + " · /freimarken spielzeit"),
                new Zeile(BundSatz.STAND_BEFEHLE_VERWALTUNG, 1L, Einsatz.keine(),
                        "&#6e7a82/freimarken welt · parzelle <x> <z> · neuladen"
                                + " · messstelle · blockbuch · blockbuch"
                                + " zurueck <radius> <minuten> · geld <spieler>"
                                + " <betrag> · goldgeld <spieler> <betrag>"
                                + " · besitz uebertragen <x> <z> <spieler>"
                                + " · besitz freigeben <x> <z> bestaetigen"
                                + " · altfurt sichern <bau>"),
                new Zeile(BundSatz.PREIS_GRATIS, 1L, Einsatz.keine(),
                        "&#73bf96Die erste Parzelle ist gratis."),
                // Der zweite Farbwechsel MITTEN im Satz nach der Schutzwache - und
                // der erste, bei dem die Farbe danach wieder zurueckschlaegt: grau,
                // gold, grau. Das Leerzeichen gehoert zum grauen Kopf, der Punkt
                // dahinter wieder dem grauen Schwanz.
                new Zeile(BundSatz.PREIS_KOSTET, 1L, Einsatz.von("nummer", "2")
                        .mit("betrag", sprache.betrag(25_000L)),
                        "&#a5b4bfParzelle Nr. 2 kostet &#c6a15b25.000 Mark&#a5b4bf."),
                new Zeile(BundSatz.PREIS_AUSGEGEBEN, 1L,
                        Einsatz.von("betrag", sprache.betrag(25_000L)),
                        "&#6e7a82Bisher ausgegeben: 25.000 Mark"),
                // --- /beruf ------------------------------------------------------
                // Die Berufsnamen kommen aus beruf.name.<schluessel> und werden hier als
                // fertiges Wort eingesetzt - genau so, wie Beruftext es tut. Der
                // Schluessel erscheint nur in der Befehlszeile der Warnung.
                new Zeile(BerufSatz.LISTE_KOPF, 1L, Einsatz.keine(),
                        "&#c6a15bBerufe im Freimarkenbund"),
                new Zeile(BerufSatz.LISTE_BERUF, 1L, Einsatz.von("name", "Bergmann"),
                        "&#a5b4bf  &#edf1f3Bergmann"),
                new Zeile(BerufSatz.LISTE_BERUF_MIT, 1L, Einsatz.von("name", "Bergmann")
                        .mit("spezialisierungen", "Steinmetz"),
                        "&#a5b4bf  &#edf1f3Bergmann&#a5b4bf · dazu: Steinmetz"),
                new Zeile(BerufSatz.LISTE_LEER, 1L, Einsatz.keine(),
                        "&#a5b4bfEs sind keine Berufe eingetragen."),
                // Die woertliche spitze Klammer - in der Vorlage maskiert, hier blank.
                new Zeile(BerufSatz.LISTE_FUSS, 1L, Einsatz.keine(),
                        "&#a5b4bf  /beruf waehlen <beruf> · /beruf stand"),
                new Zeile(BerufSatz.STAND_KOPF, 1L, Einsatz.keine(),
                        "&#c6a15bDein Beruf"),
                new Zeile(BerufSatz.STAND_KEINER, 1L, Einsatz.keine(),
                        "&#a5b4bfDu hast noch keinen Beruf gewaehlt."),
                new Zeile(BerufSatz.STAND_GRUNDBERUF, 1L, Einsatz.von("name", "Bergmann"),
                        "&#a5b4bf  &#edf1f3Bergmann"),
                new Zeile(BerufSatz.STAND_SPEZIALISIERUNG, 1L,
                        Einsatz.von("name", "Steinmetz").mit("grundberuf", "Bergmann"),
                        "&#a5b4bf  &#edf1f3Steinmetz&#a5b4bf · baut auf Bergmann auf"),
                // Die zwei Zeilen halten mit sieben und mit einem Leerzeichen dieselbe
                // Spalte. Die Zahl steht in der DATEI und nicht im Programm - wie bei
                // spielzeit.heute und spielzeit.gesamt. Zwei Zeilen muessen gemeinsam
                // geaendert werden, und nur diese Bildprobe faengt es, wenn nicht.
                new Zeile(BerufSatz.STAND_PLAETZE_GRUNDBERUFE, 1L,
                        Einsatz.von("gehalten", "1").mit("erlaubt", "2"),
                        "&#a5b4bf  Grundberufe:       1 von 2"),
                new Zeile(BerufSatz.STAND_PLAETZE_SPEZIALISIERUNGEN, 1L,
                        Einsatz.von("gehalten", "0").mit("erlaubt", "1"),
                        "&#a5b4bf  Spezialisierungen: 0 von 1"),
                new Zeile(BerufSatz.STAND_FRIST, 1L,
                        Einsatz.von("dauer", "23 h 00 min"),
                        "&#e3c57e  Du kannst in 23 h 00 min wieder wechseln."),
                new Zeile(BerufSatz.STAND_GEBUEHR, 1L,
                        Einsatz.von("betrag", sprache.betrag(5_000L)),
                        "&#a5b4bf  Ein Beruf aufgeben kostet 5.000 Mark."),
                new Zeile(BerufSatz.GEWAEHLT, 1L, Einsatz.von("name", "Bergmann"),
                        "&#73bf96Du bist jetzt Bergmann."),
                // Farbwechsel mitten im Satz und zurueck: grau, gold, grau.
                new Zeile(BerufSatz.VERLASSEN, 1L, Einsatz.von("name", "Bergmann")
                        .mit("betrag", sprache.betrag(5_000L)),
                        "&#a5b4bfDu hast Bergmann aufgegeben. Gebucht: &#c6a15b5.000 Mark&#a5b4bf."),
                // Drei Zeilen in EINEM Satz, und der Beruf zweimal darin: als Wort im
                // Text und als Schluessel in der Befehlszeile zum Abschreiben.
                new Zeile(BerufSatz.VERLASSEN_WARNUNG, 1L, Einsatz.von("name", "Bergmann")
                        .mit("schluessel", "bergmann")
                        .mit("betrag", sprache.betrag(5_000L))
                        .mit("dauer", "24 h 00 min"),
                        "&#e3c57eBergmann aufgeben?\n&#a5b4bf  Es kostet &#c6a15b5.000 Mark&#a5b4bf, und danach"
                                + " kannst du 24 h 00 min lang nicht wechseln.\n&#d9564d  /beruf"
                                + " verlassen bergmann bestaetigen"),
                // Die ROHE Eingabe, nicht kleingeschrieben: Der Spieler soll lesen, was
                // er getippt hat.
                new Zeile(BerufSatz.ABSAGE_UNBEKANNT, 1L,
                        Einsatz.von("eingabe", "Holzfaeler"),
                        "&#d9564dDen Beruf \"Holzfaeler\" gibt es nicht. /beruf liste zeigt"
                                + " alle."),
                new Zeile(BerufSatz.ABSAGE_SCHON_GEHALTEN, 1L,
                        Einsatz.von("name", "Bergmann"),
                        "&#a5b4bfDu bist schon Bergmann."),
                new Zeile(BerufSatz.ABSAGE_NICHT_GEHALTEN, 1L,
                        Einsatz.von("name", "Bergmann"),
                        "&#a5b4bfDu bist nicht Bergmann."),
                // Die Zahl in der Klammer und nicht an einem gezaehlten Hauptwort: So
                // liest sich der Satz auch bei 1 richtig, ohne zweite Dateizeile.
                new Zeile(BerufSatz.ABSAGE_GRUNDBERUFE_VOLL, 1L,
                        Einsatz.von("erlaubt", "2"),
                        "&#d9564dDu hast keinen Platz mehr fuer einen Grundberuf (erlaubt: 2)."
                                + " Gib einen auf, dann geht ein neuer."),
                new Zeile(BerufSatz.ABSAGE_SPEZIALISIERUNG_VOLL, 1L,
                        Einsatz.von("erlaubt", "1"),
                        "&#d9564dDu hast keinen Platz mehr fuer eine Spezialisierung (erlaubt:"
                                + " 1)."),
                new Zeile(BerufSatz.ABSAGE_OHNE_RECHT, 1L, Einsatz.keine(),
                        "&#d9564dSpezialisierungen sind noch nicht fuer dich freigegeben."),
                new Zeile(BerufSatz.ABSAGE_GRUNDBERUF_FEHLT, 1L,
                        Einsatz.von("name", "Bergmann"),
                        "&#d9564dDafuer musst du zuerst Bergmann sein."),
                new Zeile(BerufSatz.ABSAGE_TRAEGT_SPEZIALISIERUNG, 1L,
                        Einsatz.von("name", "Steinmetz"),
                        "&#d9564dDafuer musst du zuerst Steinmetz aufgeben."),
                new Zeile(BerufSatz.ABSAGE_SPERRFRIST, 1L,
                        Einsatz.von("dauer", "23 h 00 min"),
                        "&#d9564dDu kannst erst in 23 h 00 min wieder wechseln."),
                new Zeile(BerufSatz.ABSAGE_GELD_FEHLT, 1L,
                        Einsatz.von("betrag", sprache.betrag(5_000L))
                                .mit("guthaben", sprache.betrag(1_200L)),
                        "&#d9564dAufgeben kostet 5.000 Mark, du hast 1.200 Mark."),
                new Zeile(BerufSatz.ABSAGE_KEINE_KONSOLE, 1L, Einsatz.keine(),
                        "&#a5b4bfEin Beruf gehoert zu einem Spieler - die Konsole hat keinen."),
                new Zeile(BerufSatz.ABSAGE_GLEICHZEITIG, 1L, Einsatz.keine(),
                        "&#e3c57eDein Berufsstand hat sich eben geaendert - versuch es noch"
                                + " einmal."),
                new Zeile(BerufSatz.ABSAGE_NICHT_LESBAR, 1L, Einsatz.keine(),
                        "&#a5b4bfDeine Berufe sind gerade nicht lesbar - im Serverprotokoll"
                                + " steht, warum."),

                // --- Der Verdienst: zwei Handbalken ------------------------------
                // Sie stehen ueber dem Gegenstandsgurt und nicht im Chat, und sie tragen
                // GENAU EINE Zeile - sendActionBar nimmt ein Component und keine Liste.
                new Zeile(BerufSatz.VERDIENST_HANDBALKEN, 1L,
                        Einsatz.von("betrag", sprache.pfennige(240L)),
                        "&#73bf96+2,40 Mark"),
                new Zeile(BerufSatz.VERDIENST_DECKEL, 1L, Einsatz.keine(),
                        "&#e3c57eHeute ist genug verdient - morgen zahlt die Arbeit wieder."),

                // Die Grenze EINER Taetigkeit, nicht die des Tages. Das eingesetzte Wort
                // kommt ueber Sprache#wort aus derselben Datei - damit haengt auch die
                // Wortfamilie beruf.taetigkeit an einer Bildprobe, obwohl ein Art.WORT
                // selbst keine Zeile haben kann.
                new Zeile(BerufSatz.VERDIENST_STUNDENMENGE, 1L,
                        Einsatz.von("taetigkeit", sprache.wort(
                                BerufSatz.TAETIGKEIT_ABBAUEN, Einsatz.keine())),
                        "&#e3c57eMit Graben ist fuer diese Stunde genug - etwas anderes zahlt"
                                + " weiter."),

                // --- Der Bundesmarkt: NEUN Zeilen -------------------------------
                //
                // Zweimal umgebaut am 10.11.2026. Erst fielen neun Zeilen mit
                // /markt verkaufen (markt.verkauft in drei Formen und sechs Absagen).
                // Dann fiel das SORTIMENT: markt.sortiment, markt.zeile und
                // markt.zeilen-trenner zeigten eine Liste aus der config.yml, und die
                // gibt es nicht mehr - "es kann ja nur ge- und verkauft werden, wofuer
                // es ein Schild gibt".
                //
                // Was jetzt hier steht, ist eine LISTE AUF SEITEN wie shop.liste: Kopf,
                // Zeile je Stand, zwei Preisstuecke, Weiter-Hinweis, keine-Seite, und
                // die zwei Formen der letzten Zeile (Deckel oder kein Deckel).
                new Zeile(MarktSatz.KOPF, 1L,
                        Einsatz.von("anzahl", 7).mit("seite", 2).mit("seiten", 4),
                        "&#c6a15bDie Staende des Bundes (7) - Seite 2 von 4"),
                new Zeile(MarktSatz.ZEILE, 1L,
                        Einsatz.von("welt", "freimarken:altfurt").mit("x", 100)
                                .mit("y", -60).mit("z", 19).mit("menge", 5)
                                .mit("ware", "STONE")
                                .mit("kauf", " · Kauf 2 Mark")
                                .mit("verkauf", " · Ankauf 1 Mark"),
                        "&#a5b4bf  freimarken:altfurt 100/-60/19 · 5x STONE · Kauf"
                                + " 2 Mark · Ankauf 1 Mark"),
                // Die zwei PREISSTUECKE (markt.kauf, markt.verkauf) stehen hier
                // NICHT, und das ist die Regel dieser Tafel: Ein Satz der Art WORT
                // traegt kein eigenes Bild - er wird eingesetzt, und Probe 11b
                // verlangt darum ausdruecklich keine Zeile fuer ihn. Dass die zwei
                // Stuecke richtig eingesetzt werden, prueeft MarktBefehlTest an der
                // fertigen Zeile ("nennt NUR den Ankauf"), und ein LEERES Stueck
                // verschwindet dort rueckstandslos.
                new Zeile(MarktSatz.WEITER, 1L, Einsatz.von("naechste", 3),
                        "&#6e7a82/markt liste 3 zeigt die naechste Seite"),
                new Zeile(MarktSatz.KEINE_SEITE, 1L,
                        Einsatz.von("seite", 9).mit("seiten", 4),
                        "&#a5b4bfSeite 9 gibt es nicht - die Staende stehen auf 4 Seiten."),
                new Zeile(MarktSatz.FREI, 1L,
                        Einsatz.von("betrag", sprache.betrag(5_000L)),
                        "&#a5b4bfHeute kannst du noch &#c6a15b5.000 Mark&#a5b4bf aus Verkaeufen"
                                + " bekommen."),
                new Zeile(MarktSatz.OHNE_DECKEL, 1L, Einsatz.keine(),
                        "&#a5b4bfEs gibt keinen Tagesdeckel - du kannst beliebig viel an den"
                                + " Bund verkaufen."),
                new Zeile(MarktSatz.DECKEL_UNLESBAR, 1L, Einsatz.keine(),
                        "&#a5b4bfWie viel du heute noch bekommen kannst, liess sich nicht"
                                + " lesen&#d9564d - verkaufe lieber erst, wenn es wieder"
                                + " dasteht."),
                new Zeile(MarktSatz.LEER, 1L, Einsatz.keine(),
                        "&#e3c57eDer Bund hat noch keinen Stand. Gehandelt wird nur, wofuer"
                                + " ein Schild steht."),
                new Zeile(MarktSatz.GESTOERT, 1L, Einsatz.keine(),
                        "&#d9564dDie Staende des Bundes liessen sich nicht lesen&#a5b4bf - sie sind"
                                + " deswegen NICHT weg. Der Grund steht im"
                                + " Serverprotokoll."),


                // --- Der Tagesbonus [Teil H] ---------------------------------
                //
                // Drei Zahlungssaetze, und sie unterscheiden sich NICHT im Betrag,
                // sondern in dem, was sie ueber die Serie sagen. Darum stehen in diesen
                // drei Zeilen DERSELBE Betrag und VERSCHIEDENE Worte: Wer zwei von ihnen
                // vertauscht, bekommt hier eine rote Probe und nicht eine Nachricht, die
                // einem neuen Spieler seine Bestmarke meldet.
                new Zeile(BonusSatz.GEZAHLT_ERSTER, 1L,
                        Einsatz.von("betrag", "500 Mark").mit("morgen", "600 Mark"),
                        "&#73bf96Tagesbonus: +500 Mark&#a5b4bf - komm morgen wieder, dann sind es"
                                + " 600 Mark."),
                new Zeile(BonusSatz.GEZAHLT_SERIE, 1L,
                        Einsatz.von("betrag", "900 Mark").mit("serie", "5"),
                        "&#73bf96Tagesbonus: +900 Mark&#a5b4bf - 5 Tage in Folge."),
                // EIN HINWEIS und nicht Fliesstext: Die Bestmarke ist der Augenblick,
                // in dem die Serie etwas bedeutet. Seit der Farbwelt [21.11.2026] ist
                // sie ein hinweis und nicht geld - sie steht neben einem gruenen
                // Betrag und soll nicht wie eine zweite Zahl aussehen. Die Farbe steht
                // in der Datei, und diese Zeile haelt sie fest.
                new Zeile(BonusSatz.GEZAHLT_BESTMARKE, 1L,
                        Einsatz.von("betrag", "900 Mark").mit("serie", "5"),
                        "&#73bf96Tagesbonus: +900 Mark&#e3c57e - 5 Tage in Folge, so lange wie noch"
                                + " nie!"),
                new Zeile(BonusSatz.STAND_SERIE, 1L,
                        Einsatz.von("serie", "5").mit("laengste", "12")
                                .mit("morgen", "1.000 Mark"),
                        "&#a5b4bfTagesbonus: 5 Tage in Folge (am laengsten 12), morgen"
                                + " 1.000 Mark."),
                new Zeile(BonusSatz.STAND_OFFEN, 1L,
                        Einsatz.von("betrag", "500 Mark").mit("serie", "1"),
                        "&#a5b4bfTagesbonus: heute noch offen - Tag 1 bringt 500 Mark."),
                new Zeile(BonusSatz.STAND_WARTET, 1L,
                        Einsatz.von("minuten", "8").mit("betrag", "600 Mark")
                                .mit("serie", "2"),
                        "&#a5b4bfTagesbonus: noch 8 taetige Minuten, dann bringt Tag 2"
                                + " 600 Mark."),

                // --- Die Markthalle [Schritt 8 Teil A] -----------------------
                //
                // Der NAME der Einheit kommt aus der config.yml und nicht aus der
                // Sprachdatei - dieselbe Entscheidung wie bei den Materialnamen des
                // Bundesmarktes. Diese Zeile haelt darum fest, dass er UNVERAENDERT
                // durchgeht: Wer ihn eines Tages uebersetzen laesst, bekommt hier eine
                // rote Probe und nicht einen Befehl, der seinen eigenen Namen nicht
                // mehr findet.
                new Zeile(LadenSatz.KOPF, 1L, Einsatz.keine(),
                        "&#c6a15bDie Markthalle"),
                new Zeile(LadenSatz.ZEILE, 1L,
                        Einsatz.von("name", "halle-1").mit("ort", "62/71/6"),
                        "&#a5b4bf  halle-1&#6e7a82 - Truhe bei 62/71/6"),
                new Zeile(LadenSatz.LEER, 1L, Einsatz.keine(),
                        "&#a5b4bf  Es gibt noch keine Ladeneinheiten - die Markthalle wird"
                                + " noch gebaut."),

                // Teil B: die Ladenmiete. Jeder Betrag kommt als FERTIGER Text herein -
                // er ist schon durch waehrung.betrag gegangen, und diese Tafel haelt
                // nur, wie der Satz ihn einsetzt.
                // NOCH 29 und nicht 29: Hier steht die RESTLAUFZEIT, in
                // laden.gemietet.kopf die Vertragslaenge - am Abschlusstag zwei
                // verschiedene Zahlen fuer dasselbe Datum. Bis zum 09.11.2026 hiess
                // beides {tage}, und im Spiel sah es wie ein Fehler aus.
                new Zeile(MietSatz.STAND, 1L,
                        Einsatz.von("laden", "halle-1").mit("bis", "06.12.2026")
                                .mit("resttage", "29").mit("miete", "500 Mark"),
                        "&#a5b4bfDein Laden: halle-1, bis 06.12.2026 (noch 29 Tage),"
                                + " 500 Mark je Tag."),
                new Zeile(MietSatz.STAND_KEINER, 1L, Einsatz.keine(),
                        "&#a5b4bfDu hast noch keinen Laden - mit /laden mieten und dem Namen"
                                + " eines Standes nimmst du einen."),
                // GRUEN fuer die Zusage, GRAU fuer das, was sie kostet: Die Farbe steht
                // in der Datei, und diese Zeile haelt sie fest.
                new Zeile(MietSatz.GEMIETET_KOPF, 1L,
                        Einsatz.von("laden", "halle-1").mit("bis", "06.12.2026")
                                .mit("miettage", "30"),
                        "&#73bf96Du hast halle-1 gemietet.&#a5b4bf Der Vertrag laeuft"
                                + " 30 Tage, bis zum 06.12.2026."),
                new Zeile(MietSatz.GEMIETET_KOSTEN, 1L,
                        Einsatz.von("betrag", "5.000 Mark"),
                        "&#a5b4bfAbgebucht: 5.000 Mark Kaution."),
                // BEIDE Zahlen, und das ist der Zweck dieses zweiten Satzes: Wer 55.000
                // abgebucht sieht und 5.000 Kaution erwartet hat, soll in DERSELBEN
                // Zeile lesen, warum. Wer die zwei Werte vertauscht, bekommt hier eine
                // rote Probe und nicht eine Zeile, die sich selbst widerspricht.
                new Zeile(MietSatz.GEMIETET_ERSTMALS, 1L,
                        Einsatz.von("betrag", "55.000 Mark")
                                .mit("lizenz", "50.000 Mark"),
                        "&#a5b4bfAbgebucht: 55.000 Mark - darin die einmalige Ladenlizenz"
                                + " ueber 50.000 Mark."),
                new Zeile(MietSatz.GEMIETET_TAEGLICH, 1L,
                        Einsatz.von("miete", "500 Mark"),
                        "&#a5b4bfTaeglich faellig: 500 Mark. Verlaengern mit"
                                + " /laden verlaengern."),
                // "das sind 59 Tage" stand hier und war das schiefste von allen:
                // Die Zahl ist die RESTLAUFZEIT, klingt aber nach Laenge. Wer frueh
                // verlaengert, las "das sind 59 Tage" nach einer Verlaengerung um
                // 30.
                new Zeile(MietSatz.VERLAENGERT, 1L,
                        Einsatz.von("laden", "halle-1").mit("bis", "05.01.2027")
                                .mit("resttage", "59"),
                        "&#73bf96halle-1 ist verlaengert - bis 05.01.2027, noch"
                                + " 59 Tage."),
                // GELB und nicht gruen: Kuendigen ist keine Zusage, sondern ein
                // Verlust - der Laden ist weg. Die Kaution darunter ist gruen, denn sie
                // kommt zurueck.
                new Zeile(MietSatz.GEKUENDIGT, 1L, Einsatz.von("laden", "halle-1"),
                        "&#e3c57eDu hast halle-1 gekuendigt."),
                new Zeile(MietSatz.GEKUENDIGT_KAUTION, 1L,
                        Einsatz.von("betrag", "5.000 Mark"),
                        "&#73bf96Kaution zurueck: +5.000 Mark"),
                // ===== Teil C: der taegliche Lauf =====
                // GELB fuer die Warnung und GRAU fuer die Folge: Die erste Haelfte ist
                // die Nachricht, die zweite sagt, was daraus wird. Die Ausfallzahl steht
                // als ORDNUNGSZAHL da ("Ausfall 1 von 3") - eine Mehrzahl liesse sich in
                // MiniMessage nicht beugen, und "noch 1 Tage" waere der Fehler, der in
                // Teil A schon einmal entstanden ist.
                new Zeile(MietSatz.MIETE_GEWARNT, 1L,
                        Einsatz.von("laden", "halle-1").mit("betrag", "500 Mark")
                                .mit("ausfall", "1").mit("schranke", "3"),
                        "&#e3c57eDie Miete fuer halle-1 (500 Mark) konnte nicht abgebucht"
                                + " werden.&#a5b4bf Das war Ausfall 1 von 3 - beim letzten wird"
                                + " geraeumt, und die Kaution verfaellt."),
                // ROT: Der Laden ist weg. Die Kaution STEHT in der Zeile - sie soll das
                // Weglaufen teurer machen als das Kuendigen, und das wirkt nur, wenn der
                // Spieler die Zahl sieht.
                new Zeile(MietSatz.MIETE_GERAEUMT, 1L,
                        Einsatz.von("laden", "halle-1").mit("ausfall", "3")
                                .mit("schranke", "3").mit("kaution", "5.000 Mark"),
                        "&#d9564dhalle-1 ist geraeumt - Ausfall 3 von 3.&#a5b4bf Die Kaution ueber"
                                + " 5.000 Mark ist verfallen."),
                // GELB und nicht rot: Wer abgelaufen ist, hat jeden Tag gezahlt und
                // nichts falsch gemacht. Seine Kaution kommt zurueck, und zwar mit
                // DERSELBEN Zeile wie beim Kuendigen (GEKUENDIGT_KAUTION oben).
                new Zeile(MietSatz.MIETE_ABGELAUFEN, 1L,
                        Einsatz.von("laden", "halle-1").mit("bis", "14.11.2026"),
                        "&#e3c57eDein Vertrag ueber halle-1 ist abgelaufen - er lief bis"
                                + " 14.11.2026.&#a5b4bf Mit /laden mieten nimmst du wieder"
                                + " einen Stand."),
                // Die Zahl steht NACH dem Doppelpunkt und ohne Verb, damit die Zeile
                // auch bei einem einzigen Stapel richtig steht.
                new Zeile(MietSatz.MIETE_INS_FACH, 1L, Einsatz.von("stapel", "5"),
                        "&#a5b4bfDie Ladentruhe ist ins Abholfach gewandert: 5 Stapel. Hol sie"
                                + " mit /laden abholfach."),
                new Zeile(MietSatz.MIETE_VERZUG, 1L,
                        Einsatz.von("laden", "halle-1").mit("betrag", "500 Mark")
                                .mit("ausfall", "2").mit("schranke", "3"),
                        "&#e3c57eDein Laden halle-1 ist in Verzug - Ausfall 2 von 3.&#a5b4bf Sorge"
                                + " fuer 500 Mark auf dem Konto, sonst wird geraeumt."),
                // ===== Teil C: das Abholfach =====
                new Zeile(MietSatz.ABHOLFACH_STAND, 1L, Einsatz.von("stapel", "5"),
                        "&#a5b4bfAbholfach: 5 Stapel. /laden abholfach holt sie."),
                new Zeile(MietSatz.ABHOLFACH_LEER, 1L, Einsatz.keine(),
                        "&#a5b4bfDein Abholfach ist leer."),
                // GRUEN: Das Einzige in diesem Bereich, bei dem der Spieler etwas
                // BEKOMMT.
                new Zeile(MietSatz.ABHOLFACH_GEHOLT, 1L, Einsatz.von("stapel", "3"),
                        "&#73bf963 Stapel aus dem Abholfach geholt."),
                new Zeile(MietSatz.ABHOLFACH_BLEIBT, 1L, Einsatz.von("stapel", "2"),
                        "&#a5b4bfNoch im Fach: 2 Stapel - dein Inventar hat keinen Platz"
                                + " mehr."),
                new Zeile(MietSatz.ABHOLFACH_KEIN_PLATZ, 1L, Einsatz.von("stapel", "5"),
                        "&#d9564dDein Inventar ist voll. Im Abholfach: 5 Stapel."),
                // ROT, obwohl nichts verloren ist: Der Spieler soll es dem Team sagen,
                // und dafuer muss die Zeile auffallen.
                new Zeile(MietSatz.ABHOLFACH_UNLESBAR, 1L, Einsatz.von("stapel", "2"),
                        "&#d9564dNicht herausgegeben: 2 Stapel. Das bleibt liegen - bitte sag"
                                + " es dem Team."),
                new Zeile(MietSatz.ABSAGE_KEINE_EINHEIT, 1L,
                        Einsatz.von("laden", "halle-9"),
                        "&#d9564dEinen Stand namens halle-9 gibt es nicht.\n&#a5b4bf/laden zeigt,"
                                + " welche es gibt."),
                new Zeile(MietSatz.ABSAGE_SCHON_VERMIETET, 1L,
                        Einsatz.von("laden", "halle-1"),
                        "&#d9564dhalle-1 ist schon vermietet."),
                // {laden} ist SEIN Laden und nicht der gewuenschte. Wer die zwei
                // vertauscht, laesst einen Spieler suchen, welchen er eigentlich hat -
                // und diese Zeile allein sieht das nicht; dafuer steht MiettextTest.
                new Zeile(MietSatz.ABSAGE_SCHON_EIN_LADEN, 1L,
                        Einsatz.von("laden", "halle-1"),
                        "&#d9564dDu hast schon einen Laden: halle-1.\n&#a5b4bfMit /laden kuendigen"
                                + " gibst du ihn zurueck."),
                // DREI Zahlen: was es kostet, was da ist, was fehlt. Die dritte rechnet
                // das Programm - ein Spieler soll nicht subtrahieren muessen.
                new Zeile(MietSatz.ABSAGE_ZU_WENIG_GELD, 1L,
                        Einsatz.von("kosten", "55.000 Mark")
                                .mit("guthaben", "50.000 Mark")
                                .mit("fehlt", "5.000 Mark"),
                        "&#d9564dDer Abschluss kostet 55.000 Mark, du hast 50.000 Mark - es"
                                + " fehlen 5.000 Mark."),
                new Zeile(MietSatz.ABSAGE_NOCH_ZU_FRUEH, 1L,
                        Einsatz.von("laden", "halle-1").mit("resttage", "40")
                                .mit("dauer", "30"),
                        "&#e3c57ehalle-1 laeuft noch 40 Tage - verlaengern geht, sobald"
                                + " weniger als 30 Tage uebrig sind."),
                new Zeile(MietSatz.ABSAGE_KEIN_LADEN, 1L, Einsatz.keine(),
                        "&#d9564dDu hast keinen Laden.\n&#a5b4bf/laden zeigt, welche Staende frei"
                                + " sind."),
                new Zeile(MietSatz.ABSAGE_FALSCHE_WELT, 1L, Einsatz.keine(),
                        "&#d9564dDie Markthalle steht in Altfurt - dort wird gemietet."),
                new Zeile(MietSatz.ABSAGE_GESTOERT, 1L, Einsatz.keine(),
                        "&#d9564dDas hat gerade nicht geklappt - versuch es noch einmal."),

                // ===== Plan 7.4: die Kistenshops =====
                // {ware} ist der KURZNAME aus Shopware.kurzname, also dasselbe Wort, das
                // auf der vierten Schildzeile steht. Ein Plus davor heisst: die Ware ist
                // mehr als ihr Material. Diese Tafel setzt ihn als fertigen Text ein -
                // dass er wirklich so entsteht, ist eine andere Frage und steht in
                // ShopwareTafel.
                new Zeile(ShopSatz.SHOP_ANGELEGT, 1L,
                        Einsatz.von("menge", 16L).mit("ware", "STONE"),
                        "&#73bf96Shop angelegt: 16x STONE"),
                // GOLD und nicht gruen: Die Zeile ist keine Zusage, sondern eine
                // Eigenschaft - und zwar eine, die auffallen soll. Sie kommt HINTER den
                // anderen und ersetzt keine.
                new Zeile(ShopSatz.SHOP_ANGELEGT_BUND, 1L, Einsatz.keine(),
                        "&#c6a15bBundshop: unbegrenzter Vorrat, unbegrenzter Platz,"
                                + " unbegrenztes Geld."),
                // GRAU mit WEISSEM Betrag, und der Punkt gehoert wieder zum grauen
                // Stueck: Derselbe Aufbau wie bei den Preiszeilen der Ladenmiete.
                new Zeile(ShopSatz.SHOP_PREIS_KAUF, 1L,
                        Einsatz.von("preis", "100 Mark"),
                        "&#a5b4bfKunden kaufen fuer &#edf1f3100 Mark&#a5b4bf."),
                new Zeile(ShopSatz.SHOP_PREIS_VERKAUF, 1L,
                        Einsatz.von("preis", "80 Mark"),
                        "&#a5b4bfKunden verkaufen fuer &#edf1f380 Mark&#a5b4bf."),
                // GELB und nicht rot: Ein abgebauter Shop ist kein Fehler. Die zweite
                // Haelfte ist die wichtigere - dass die WARE IN DER KISTE BLEIBT, weiss
                // nur, wer es gesagt bekommt.
                new Zeile(ShopSatz.SHOP_ABGEBAUT, 1L, Einsatz.von("ware", "STONE"),
                        "&#e3c57eShop abgebaut: STONE&#a5b4bf - die Ware bleibt in der Kiste."),

                // DIE ACHT SCHILDABSAGEN. Jede hat ZWEI Zeilen: rot, was falsch ist,
                // grau, wie es richtig geht. Faellt die zweite weg, bleibt ein Spieler
                // am Schild stehen und weiss nicht, was er tippen soll - und genau das
                // ist der Zustand, in dem die Vorbilder ihre Nutzer lassen.
                // {zeile} ist eine Zahl AUS DEM PROGRAMM: Welche Schildzeile falsch ist,
                // weiss nur Shopschild.lies.
                new Zeile(ShopSatz.SHOP_MENGE_KEINE_ZAHL, 1L, Einsatz.von("zeile", 2L),
                        "&#d9564dZeile 2 ist keine Zahl.\n&#a5b4bfDort gehoert die Menge hin,"
                                + " die ein Kunde je Klick bekommt - zum Beispiel 16."),
                new Zeile(ShopSatz.SHOP_MENGE_AUSSERHALB, 1L,
                        Einsatz.von("zeile", 2L).mit("hoechstmenge", 3_456L),
                        "&#d9564dZeile 2: die Menge geht von 1 bis 3.456.\n&#a5b4bfMehr als"
                                + " eine volle Kiste kann ein Handel nicht hergeben."),
                // DIE ABSAGE, DIE AM MEISTEN ERKLAEREN MUSS: Die Preisform ist nicht zu
                // erraten. Darum stehen alle DREI Formen in der Zeile, und zwar weiss
                // hervorgehoben - ein Spieler soll sie abtippen koennen.
                new Zeile(ShopSatz.SHOP_PREIS_FORM, 1L, Einsatz.von("zeile", 3L),
                        "&#d9564dZeile 3 ist keine Preiszeile.\n&#a5b4bfSo geht es: &#edf1f3B 100"
                                + "&#a5b4bf (Kunden kaufen), &#edf1f3S 80&#a5b4bf (Kunden verkaufen) oder"
                                + " &#edf1f3B 100 : S 80&#a5b4bf (beides)."),
                // NENNT, WAS NICHT GEHT: Punkt, Komma und ein angehaengtes Wort sind die
                // drei Versuche, die jeder macht. Das Programm nimmt NUR Ziffern, und
                // zwar weil die Vorbilder mehr angenommen haben - ChestShop PR #522 zum
                // Nullpreis und #30 zum E als Exponent.
                new Zeile(ShopSatz.SHOP_PREIS_KEINE_ZAHL, 1L, Einsatz.von("zeile", 3L),
                        "&#d9564dZeile 3: der Preis ist keine ganze Zahl.\n&#a5b4bfNur"
                                + " Ziffern - kein Punkt, kein Komma, kein k und kein"
                                + " Mio."),
                new Zeile(ShopSatz.SHOP_PREIS_AUSSERHALB, 1L,
                        Einsatz.von("zeile", 3L).mit("hoechstpreis", 10_000_000L),
                        "&#d9564dZeile 3: der Preis geht von 0 bis 10.000.000.\n&#a5b4bfEine"
                                + " 0 bedeutet: diese Richtung gibt es nicht."),
                // EINE EIGENE ABSAGE und nicht die darueber: Null ist fuer EINE der
                // beiden Richtungen erlaubt, nur beide zusammen sind der Fehler.
                new Zeile(ShopSatz.SHOP_PREIS_BEIDE_NULL, 1L, Einsatz.von("zeile", 3L),
                        "&#d9564dZeile 3: beide Preise sind 0.\n&#a5b4bfEiner von beiden"
                                + " genuegt - aber einer muss es sein, sonst ist es ein"
                                + " Geschenk und kein Shop."),
                // DIE ABSAGE, DIE AM MEISTEN GELD RETTET, und sie sagt WARUM. Ein
                // Spieler, der nur "nicht erlaubt" liest, haelt es fuer Willkuer.
                new Zeile(ShopSatz.SHOP_PREIS_VERDREHT, 1L, Einsatz.von("zeile", 3L),
                        "&#d9564dZeile 3: du kaufst teurer an, als du verkaufst.\n"
                                + "&#a5b4bfDas ist eine Maschine, die dein Guthaben an jeden"
                                + " Kunden ausschuettet. S darf nicht ueber B liegen."),
                new Zeile(ShopSatz.SHOP_WARE_FEHLT, 1L, Einsatz.von("zeile", 4L),
                        "&#d9564dZeile 4 ist leer.\n&#a5b4bfDort gehoert die Ware hin - ihr"
                                + " Name, oder ein &#edf1f3?&#a5b4bf fuer das, was in der Kiste"
                                + " liegt."),

                // DIE WELT SAGT NEIN. Dieselbe zweiteilige Form.
                // NENNT DIE DREI ERLAUBTEN BEHAELTER: Ein Ofen, ein Spender und eine
                // Shulkerkiste sehen aus wie Behaelter und sind ausgeschlossen. Ohne
                // diese Zeile sucht der Spieler den Fehler bei seinem Schild.
                new Zeile(ShopSatz.SHOP_KEINE_KISTE, 1L, Einsatz.keine(),
                        "&#d9564dNeben dem Schild steht keine Kiste.\n&#a5b4bfEs geht eine"
                                + " Kiste, eine Redstonetruhe oder ein Fass - direkt"
                                + " daneben, darueber oder darunter, nicht diagonal."),
                // DER FALL, DEN KEIN VORBILD SELBST SCHLIESST: Das Schild auf eigenem
                // Grund, die Kiste einen Block weiter auf fremdem.
                new Zeile(ShopSatz.SHOP_FREMDE_KISTE, 1L, Einsatz.keine(),
                        "&#d9564dAn dieser Kiste darfst du nicht.\n&#a5b4bfSie steht auf"
                                + " fremdem Grund - ein Shop braucht eine Kiste, an die"
                                + " du selbst herankommst."),
                // KOMMT AUCH DEM BESITZER: Preise aendert man durch Abbauen und neu
                // Setzen, denn nur so geht die Aenderung durch die Datenbank.
                // DIE ANDERE HAELFTE DES KISTENSCHUTZES [08.11.2026]: Wer die Kiste
                // nicht oeffnen kann, haengt sonst ein eigenes Schild daneben. Sagt
                // ausdruecklich NICHT "fremder Grund" - draussen gehoert kein Ort
                // jemandem, und der Spieler suchte eine Parzelle, die es nicht gibt.
                new Zeile(ShopSatz.SHOP_FREMDER_SHOP, 1L, Einsatz.keine(),
                        "&#d9564dAn dieser Kiste haengt schon ein fremder Shop.\n&#a5b4bfZwei"
                                + " Schilder an einer Kiste handeln mit derselben"
                                + " Ware - nimm eine eigene Kiste."),
                // KOMMT AUCH DEM BESITZER: Preise aendert man durch Abbauen und neu
                // Setzen, denn nur so geht die Aenderung durch die Datenbank.
                new Zeile(ShopSatz.SHOP_SCHON_EIN_SHOP, 1L, Einsatz.keine(),
                        "&#d9564dHier haengt schon ein Shop.\n&#a5b4bfZum Aendern bau das"
                                + " Schild ab und setz ein neues - so bleibt das Schild"
                                + " bei dem, was der Shop wirklich tut."),
                // NENNT BEIDE FAELLE - leer und mehrere Arten -, weil der Spieler von
                // aussen nicht sieht, welcher vorliegt. UND dass verzaubert eine eigene
                // Art ist: Das ist die Folge der Entscheidung vom 08.11.2026 und von
                // aussen nicht zu erraten.
                new Zeile(ShopSatz.SHOP_WARE_UNKLAR, 1L, Einsatz.keine(),
                        "&#d9564dDie Kiste sagt nicht, was du handeln willst.\n&#a5b4bfFuer"
                                + " ein &#edf1f3?&#a5b4bf muss genau EINE Art darin liegen. Eine"
                                + " verzauberte und eine gewoehnliche Spitzhacke sind"
                                + " zwei Arten."),
                // GIBT IHM SEINE EIGENE EINGABE ZURUECK, weiss hervorgehoben: Oft ist es
                // ein Leerzeichen zu viel, und das sieht nur, wer liest, was das
                // Programm gelesen hat.
                new Zeile(ShopSatz.SHOP_WARE_UNBEKANNT, 1L,
                        Einsatz.von("getippt", "STEIN"),
                        "&#d9564dDen Gegenstand &#edf1f3STEIN&#d9564d gibt es nicht.\n&#a5b4bfLeg die"
                                + " Ware in die Kiste und schreib ein &#edf1f3?&#a5b4bf - das geht"
                                + " immer, auch bei verzauberten Sachen."),
                new Zeile(ShopSatz.SHOP_KEIN_BUNDRECHT, 1L, Einsatz.keine(),
                        "&#d9564dBundshops darfst du nicht anlegen.\n&#a5b4bfLass die erste"
                                + " Zeile leer, dann wird es dein eigener Shop."),
                // DAS LADENRECHT [Plan 16, Teil E3]. {was} steht hier von Hand, denn
                // diese Tafel haelt das BILD des Satzes fest - im Spiel kommt es als
                // Wort aus freistadt.auflage. Dass der RICHTIGE Wert ankommt, prueft
                // ShoptextTest durch die echte Naht; eine Bildtafel kann das nie.
                // Sein Laden ist weg, weil die TAGESGEBUEHR nicht zu zahlen war
                // [Teil E6]. SAGT AUSDRUECKLICH, DASS DIE WARE NOCH DA IST - ohne das
                // sucht er sein Lager.
                new Zeile(ShopSatz.SHOP_LADENGEBUEHR_WEG, 1L,
                        Einsatz.von("stadt", "annastadt").mit("gebuehr", "10 Mark"),
                        "&#d9564dDein Laden in annastadt ist abgebaut.\n&#a5b4bfDie"
                                + " Tagesgebuehr von 10 Mark war nicht zu zahlen."
                                + " Deine Ware liegt noch in der Kiste - setz ein neues"
                                + " Schild, wenn du wieder zahlen kannst."),
                // Die Ladengebuehr, die er nicht zahlen kann [Teil E6]. EIN
                // ANDERER Satz als kein-ladenrecht: Dort darf NIEMAND einen Laden
                // anlegen, hier darf er nur gerade nicht.
                new Zeile(ShopSatz.SHOP_LADENGEBUEHR_ZU_TEUER, 1L,
                        Einsatz.von("gebuehr", "250 Mark"),
                        "&#d9564dEin Laden in dieser Stadt kostet 250 Mark - das hast du"
                                + " nicht.\n&#a5b4bfDie Gebuehr geht in die Stadtkasse; sie"
                                + " stellt der Betreiber ein."),
                new Zeile(ShopSatz.SHOP_KEIN_LADENRECHT, 1L,
                        Einsatz.von("stadt", "Annastadt")
                                .mit("was", "ein Regeltext"),
                        "&#d9564dAnnastadt hat noch kein Ladenrecht\n&#a5b4bfEs fehlt: ein"
                                + " Regeltext. Bestehende Laeden handeln weiter."),
                // HIER GEHT GAR KEIN Spielershop [Chat 27.11.2026]. OHNE Platzhalter,
                // und die Zeile nennt die zwei Orte, an denen es GEHT: Wer "nicht
                // hier" liest, fragt "wo denn" - die Antwort muss mitkommen.
                new Zeile(ShopSatz.SHOP_KEIN_HANDELSPLATZ, 1L, Einsatz.keine(),
                        "&#d9564dHier darf kein Shop stehen\n&#a5b4bfKistenshops gehen"
                                + " in einer Ladeneinheit der Markthalle und in einer"
                                + " Freistadt mit Stadtrecht."),
                // SAGT AUSDRUECKLICH "NOCH EINMAL VERSUCHEN": Eine Absage ohne Ausweg
                // liest ein Spieler als Verbot, baut sein Schild ab und kommt nicht
                // wieder. Dieselbe Linie wie wache.schutz.laden-gestoert.
                new Zeile(ShopSatz.SHOP_GESTOERT, 1L, Einsatz.keine(),
                        "&#d9564dDer Shop liess sich nicht anlegen.\n&#a5b4bfDas Schild"
                                + " bleibt stehen - versuch es noch einmal. Bleibt es"
                                + " dabei, sieh im Serverprotokoll nach."),

                // DIE VIER HANDBALKEN. Genau EINE Zeile - keine Umbruchmarke, denn
                // sendActionBar nimmt ein Component. Derselbe Aufbau wie
                // wache.schutz.fremde-parzelle: Absage rot, der fremde Name weiss, und
                // das Leerzeichen davor gehoert zum ROTEN Stueck.
                new Zeile(ShopSatz.SHOP_SCHUTZ_SCHILD, 1L,
                        Einsatz.von("besitzer", "Anna"),
                        "&#d9564dDieser Shop gehoert &#edf1f3Anna"),
                // EIN EIGENER SATZ und nicht derselbe wie beim Schild: Der Spieler soll
                // lesen, WARUM eine gewoehnlich aussehende Kiste geschuetzt ist.
                new Zeile(ShopSatz.SHOP_SCHUTZ_KISTE, 1L,
                        Einsatz.von("besitzer", "Anna"),
                        "&#d9564dDiese Kiste ist der Shop von &#edf1f3Anna"),
                // DER DRITTE SATZ OHNE PLATZHALTER: Ein Bundshop hat keinen Besitzer, den
                // man nennen koennte, und ein Wert, in dem manchmal ein Mensch und
                // manchmal eine Einrichtung steht, zwingt zu einem Satz fuer beide.
                new Zeile(ShopSatz.SHOP_SCHUTZ_BUND, 1L, Einsatz.keine(),
                        "&#d9564dDas ist ein Shop des Bundes"),
                // DIE UEBERRASCHENDSTE DER FUENF: Der Traegerblock sieht nach nichts aus -
                // ein Stein unter einem Stehschild. Der Satz muss den Grund nennen, sonst
                // haelt der Spieler ihn fuer kaputt.
                new Zeile(ShopSatz.SHOP_SCHUTZ_TRAEGER, 1L, Einsatz.keine(),
                        "&#d9564dDaran haengt ein fremdes Shopschild"),
                // DER FUENFTE [08.11.2026], fuer den einen Fall, in dem die zwei
                // Richtungen der Ablage auseinandergehen: geschuetzt ja, Besitzer
                // unbekannt. Ohne ihn stuende hier "der Shop von -".
                new Zeile(ShopSatz.SHOP_SCHUTZ_UNLESBAR, 1L, Einsatz.keine(),
                        "&#d9564dDiese Kiste ist geschuetzt - versuch es noch einmal"),

                // DER HANDEL [08.11.2026]. Alles Handbalken, genau EINE Zeile -
                // ein Spieler, der zwanzig Stapel kauft, klickt zwanzigmal, und
                // zwanzig Chatzeilen waeren ein Regen.
                //
                // KEINE GEBUEHR IN DER KAUFZEILE: Beim Kauf zahlt sie der
                // SHOPBESITZER. Der Kunde zahlt den Preis vom Schild und nichts
                // darueber - eine Zeile, die ihm den Abzug eines anderen nennt,
                // liest sich wie ein Abzug bei ihm.
                new Zeile(ShopSatz.SHOP_KAUF_GELUNGEN, 1L, Einsatz
                        .von("menge", 16L)
                        .mit("ware", "STONE")
                        .mit("preis", "100 Mark"),
                        "&#73bf96Gekauft: 16x STONE&#a5b4bf fuer &#edf1f3100 Mark"),
                new Zeile(ShopSatz.SHOP_VERKAUF_GELUNGEN, 1L, Einsatz
                        .von("menge", 16L)
                        .mit("ware", "STONE")
                        .mit("preis", "80 Mark"),
                        "&#73bf96Verkauft: 16x STONE&#a5b4bf fuer &#edf1f380 Mark"),
                // DREI ZAHLEN IN EINER ZEILE, und das ist Absicht: Wer 100 am
                // Schild liest und 95 bekommt, sucht den Fehler beim Shop. Eine
                // Zusage, die eine ihrer Zahlen nicht nennt, traegt keine.
                new Zeile(ShopSatz.SHOP_VERKAUF_GEBUEHR, 1L, Einsatz
                        .von("menge", 16L)
                        .mit("ware", "STONE")
                        .mit("preis", "100 Mark")
                        .mit("gebuehr", "7 Mark")
                        .mit("erloes", "93 Mark"),
                        "&#73bf96Verkauft: 16x STONE&#a5b4bf fuer &#edf1f393 Mark&#a5b4bf von 100 Mark,"
                                + " 7 Mark Marktgebuehr"),

                // DIE ELF ABSAGEN DES HANDELS. Jede nennt, WAS fehlt - "das geht
                // nicht" laesst einen Kunden dreimal klicken und dann aufhoeren.
                //
                // Die zwei Richtungsabsagen sagen, was STATTDESSEN geht: Ein Shop
                // mit nur einem Preis sieht aus wie jeder andere, und ohne den
                // Hinweis probiert niemand die andere Maustaste.
                new Zeile(ShopSatz.SHOP_VERKAUFT_NICHT, 1L, Einsatz.keine(),
                        "&#d9564dDieser Shop verkauft nichts&#a5b4bf - mit Linksklick kannst"
                                + " du ihm etwas verkaufen."),
                new Zeile(ShopSatz.SHOP_KAUFT_NICHT_AN, 1L, Einsatz.keine(),
                        "&#d9564dDieser Shop kauft nichts an&#a5b4bf - mit Rechtsklick kaufst"
                                + " du bei ihm."),
                // NICHT ROT: Das ist kein Fehler des Spielers, sondern eine
                // Auskunft. Und sie kommt nur beim Rechtsklick - beim Linksklick
                // baut der Besitzer sein Schild ab, und zwei Antworten auf eine
                // Tat liest niemand als zwei.
                new Zeile(ShopSatz.SHOP_EIGENER_SHOP, 1L, Einsatz.keine(),
                        "&#a5b4bfDas ist dein eigener Shop - nimm die Ware aus der"
                                + " Kiste."),
                // DER EINZIGE FALL, DEN NUR DER BESITZER BEHEBEN KANN - darum
                // sagt der Satz, dass man es ihm sagen soll.
                new Zeile(ShopSatz.SHOP_WARE_UNLESBAR, 1L, Einsatz.keine(),
                        "&#d9564dDieser Shop handelt nicht mehr&#a5b4bf - sag es seinem"
                                + " Besitzer."),
                new Zeile(ShopSatz.SHOP_AUSVERKAUFT, 1L,
                        Einsatz.von("ware", "STONE"),
                        "&#d9564dAusverkauft: STONE"),
                // MIT DER MENGE DES SHOPS: Ein Shop, der 16 Stueck je Handel
                // nimmt, lehnt auch 15 ab - ohne die Zahl sieht der Kunde seine
                // Ware im Rucksack und die Absage auf dem Schirm.
                new Zeile(ShopSatz.SHOP_KEINE_WARE, 1L, Einsatz
                        .von("menge", 16L)
                        .mit("ware", "STONE"),
                        "&#d9564dDu brauchst 16x STONE"),
                new Zeile(ShopSatz.SHOP_KEIN_PLATZ, 1L, Einsatz.keine(),
                        "&#d9564dIn deinem Rucksack ist kein Platz"),
                // EIN EIGENER SATZ und nicht derselbe wie beim Rucksack: Der
                // Kunde kann daran nichts aendern, und ein Satz, der ihn zum
                // Aufraeumen auffordert, schickt ihn ins Leere.
                new Zeile(ShopSatz.SHOP_KISTE_VOLL, 1L, Einsatz.keine(),
                        "&#d9564dDie Kiste dieses Shops ist voll"),
                new Zeile(ShopSatz.SHOP_KEIN_GELD, 1L,
                        Einsatz.von("preis", "100 Mark"),
                        "&#d9564dDafuer brauchst du 100 Mark"),
                // NENNT DEN BESITZER NICHT: Wer wie viel Geld hat, ist nicht die
                // Sache eines Kunden, und der Name steht am Schild.
                new Zeile(ShopSatz.SHOP_PLEITE, 1L, Einsatz.keine(),
                        "&#d9564dDieser Shop kann das nicht zahlen"),
                // SAGT AUSDRUECKLICH, DASS KEIN GELD WEG IST. Das ist die Zusage,
                // die der Handel wirklich haelt: Was nach dem Zahlen scheitert,
                // wird erstattet.
                new Zeile(ShopSatz.SHOP_HANDEL_GESTOERT, 1L, Einsatz.keine(),
                        "&#d9564dDer Handel ist nicht zustande gekommen&#a5b4bf - dein Geld"
                                + " ist nicht weg."),

                // Das Blockbuch (Moderation). Die ZEIT kommt aus dem eigenen Muster und
                // die TAT aus dem eigenen Wort, nicht als getippte Zeichenkette: Beide
                // haben keine eigene Tafelzeile - ein Art.MUSTER und ein Art.WORT
                // koennen keine haben -, und so haengen sie trotzdem an einer Bildprobe.
                new Zeile(BlockSatz.KOPF_ORT, 1L,
                        Einsatz.von("ort", "altfurt 62/-60/8").mit("anzahl", 3L),
                        "&#c6a15bBlockbuch&#a5b4bf an altfurt 62/-60/8 - 3 Eintraege:"),
                new Zeile(BlockSatz.KOPF_SPIELER, 1L,
                        Einsatz.von("spieler", "Anna").mit("anzahl", 2L),
                        "&#c6a15bBlockbuch&#a5b4bf von Anna - 2 Eintraege:"),
                new Zeile(BlockSatz.ZEILE, 1L,
                        Einsatz.von("zeit", sprache.muster(BlockSatz.ZEIT_MUSTER)
                                        .format(BLOCKBUCH_ZEIT))
                                .mit("spieler", "Anna")
                                .mit("tat", sprache.wort(BlockSatz.TAT_ABBAUEN,
                                        Einsatz.keine()))
                                .mit("block", "oak_log"),
                        "&#a5b4bf  09.11.2026 14:30  Anna baute ab oak_log"),
                // DER FUND, BEI DEM NICHTS PASSIERT IST. Ein eigener Satz und nicht ein
                // eingesetztes Wort: Ein Dutzend dieser Zeilen an einer fremden Parzelle
                // soll beim Durchlesen auffallen, und eine Zeile, die sich nur in einem
                // Wort unterscheidet, tut das nicht.
                new Zeile(BlockSatz.ZEILE_ABGEWIESEN, 1L,
                        Einsatz.von("zeit", sprache.muster(BlockSatz.ZEIT_MUSTER)
                                        .format(BLOCKBUCH_ZEIT))
                                .mit("spieler", "Bert")
                                .mit("tat", sprache.wort(BlockSatz.TAT_SETZEN,
                                        Einsatz.keine()))
                                .mit("block", "stone"),
                        "&#6e7a82  09.11.2026 14:30&#d9564d  x Bert setzte stone"),
                new Zeile(BlockSatz.ZEILE_MIT_ORT, 1L,
                        Einsatz.von("zeit", sprache.muster(BlockSatz.ZEIT_MUSTER)
                                        .format(BLOCKBUCH_ZEIT))
                                .mit("ort", "altfurt 62/-60/8")
                                .mit("tat", sprache.wort(BlockSatz.TAT_BENUTZEN,
                                        Einsatz.keine()))
                                .mit("block", "chest"),
                        "&#a5b4bf  09.11.2026 14:30  altfurt 62/-60/8 benutzte chest"),
                new Zeile(BlockSatz.ZEILE_MIT_ORT_ABGEWIESEN, 1L,
                        Einsatz.von("zeit", sprache.muster(BlockSatz.ZEIT_MUSTER)
                                        .format(BLOCKBUCH_ZEIT))
                                .mit("ort", "marktstadt 7/71/13")
                                .mit("tat", sprache.wort(BlockSatz.TAT_BENUTZEN,
                                        Einsatz.keine()))
                                .mit("block", "chest"),
                        "&#6e7a82  09.11.2026 14:30&#d9564d  x marktstadt 7/71/13 benutzte chest"),
                // SAGT, WARUM nichts da sein kann. Ohne den Hinweis sucht der Moderator
                // den Fehler im Plugin, obwohl die Welt abgewaehlt oder die Frist um
                // sein kann.
                new Zeile(BlockSatz.LEER_ORT, 1L,
                        Einsatz.von("ort", "altfurt 62/-60/8"),
                        "&#a5b4bfZu altfurt 62/-60/8 steht nichts im Blockbuch - die Welt kann"
                                + " abgewaehlt sein, oder die Frist hat es geholt."),
                new Zeile(BlockSatz.LEER_SPIELER, 1L, Einsatz.von("spieler", "Anna"),
                        "&#a5b4bfZu Anna steht nichts im Blockbuch."),
                // NENNT DIE REICHWEITE: Wer einen Block weit weg anschaut, soll nicht
                // raten, ob das Buch leer ist oder der Blick zu weit ging.
                new Zeile(BlockSatz.KEIN_BLOCK, 1L, Einsatz.von("reichweite", 6L),
                        "&#a5b4bfDu siehst keinen Block an&#6e7a82 (Reichweite 6 Bloecke). Nach"
                                + " einem Spieler fragt /freimarken blockbuch"
                                + " spieler <name>."),
                // "Nichts aufgeschrieben" ist NICHT "nichts geschehen" - genau den
                // Unterschied muss ein Moderator kennen, bevor er jemanden freispricht.
                new Zeile(BlockSatz.AUS, 1L, Einsatz.keine(), "&#e3c57eIn dieser Welt schreibt das Blockbuch nichts&#a5b4bf - was hier"
                                + " geschah, steht nirgends. Einstellbar unter"
                                + " blockbuch.welten in der config.yml."),
                // Und ein Netzfehler darf nicht aussehen wie ein Freispruch.
                new Zeile(BlockSatz.GESTOERT, 1L, Einsatz.keine(), "&#d9564dDas Blockbuch antwortet nicht&#a5b4bf - der Grund steht im"
                                + " Serverprotokoll. Das ist KEIN Freispruch."),
                new Zeile(BlockSatz.UNBEKANNT, 1L, Einsatz.von("name", "annna"),
                        "&#d9564dDen Spieler annna kennt dieser Server nicht."),
                // DER RUECKWEG [26.11.2026]. Zweistufig, und die Zeilen sind die
                // Stufen: Die Vorschau nennt Kasten und Zahlen UND den fertigen
                // Befehl - ohne ihn muesste der Moderator den Ort abtippen, an dem
                // er gerade steht, und ein Schritt dazwischen traefe den falschen
                // Kasten.
                new Zeile(BlockSatz.RUECKWEG_KOPF, 1L,
                        Einsatz.von("ort", "altfurt 100/70/200").mit("radius", 10L).mit("minuten", 30L),
                        "&#c6a15bRueckweg&#a5b4bf um altfurt 100/70/200,"
                                + " Radius 10, letzte 30 Minuten:"),
                new Zeile(BlockSatz.RUECKWEG_ZAHLEN, 1L,
                        Einsatz.von("zeilen", 2L).mit("wegnehmen", 1L).mit("herstellen", 1L),
                        "&#a5b4bf  Zeilen: 2 - wegnehmen: 1, herstellen: 1"),
                new Zeile(BlockSatz.RUECKWEG_ROH, 1L,
                        Einsatz.von("anzahl", 3L),
                        "&#e3c57e  Ohne Vorzustand: 3&#6e7a82 - dort kommt der"
                                + " Block zurueck, aber nicht seine Blickrichtung; wo in"
                                + " Wasser gebaut wurde, kommt Luft."),
                new Zeile(BlockSatz.RUECKWEG_BEFEHL, 1L,
                        Einsatz.von("befehl", "/freimarken blockbuch zurueck 10 30 bei altfurt 100 70 200 bestaetigen"),
                        "&#a5b4bf  Ausfuehren mit: &#e3c57e/freimarken"
                                + " blockbuch zurueck 10 30 bei altfurt 100 70 200"
                                + " bestaetigen"),
                new Zeile(BlockSatz.RUECKWEG_LEER, 1L,
                        Einsatz.von("ort", "altfurt 100/70/200").mit("minuten", 30L),
                        "&#a5b4bfUm altfurt 100/70/200 steht in den letzten 30"
                                + " Minuten nichts, was zurueckzunehmen waere."),
                new Zeile(BlockSatz.RUECKWEG_ZU_VIELE, 1L,
                        Einsatz.von("zeilen", 2L).mit("grenze", 5000L),
                        "&#d9564dMindestens 2 Zeilen&#a5b4bf - die Grenze"
                                + " liegt bei 5.000, und NICHTS wurde getan. Ein Rueckweg"
                                + " in Haeppchen waere nicht nur unvollstaendig, er"
                                + " waere falsch: Er nimmt die aelteste Zeile je Block,"
                                + " und ein Schnitt mitten hindurch stellte den"
                                + " Zwischenstand her. Nimm einen kleineren Radius oder"
                                + " ein kuerzeres Fenster."),
                new Zeile(BlockSatz.RUECKWEG_ZU_WEIT, 1L,
                        Einsatz.von("radius", 10L).mit("hoechstens", 50L),
                        "&#d9564dRadius 10 ist zu gross&#a5b4bf - hoechstens"
                                + " 50. Einstellbar unter"
                                + " blockbuch.rueckweg.hoechster-radius."),
                new Zeile(BlockSatz.RUECKWEG_ZU_LANG, 1L,
                        Einsatz.von("minuten", 30L).mit("hoechstens", 50L),
                        "&#d9564d30 Minuten sind zu viel&#a5b4bf - hoechstens"
                                + " 50. Einstellbar unter"
                                + " blockbuch.rueckweg.hoechste-minuten."),
                new Zeile(BlockSatz.RUECKWEG_UNBEKANNTE_WELT, 1L,
                        Einsatz.von("welt", "hochwald").mit("welten", "altfurt, nether"),
                        "&#d9564dDie Welt hochwald gibt es nicht.&#6e7a82 Es"
                                + " gibt: altfurt, nether"),
                new Zeile(BlockSatz.RUECKWEG_FERTIG, 1L,
                        Einsatz.von("gesetzt", 7L).mit("zeilen", 2L),
                        "&#73bf96Rueckweg gelaufen&#a5b4bf - 7 Bloecke"
                                + " gesetzt, 2 Zeilen gemerkt."),
                new Zeile(BlockSatz.RUECKWEG_MISSLUNGEN, 1L,
                        Einsatz.von("anzahl", 3L),
                        "&#d9564d  Nicht gesetzt: 3&#6e7a82 - diesen Block"
                                + " kennt der Server nicht (mehr). Die Zeilen sind"
                                + " trotzdem gemerkt, sonst versuchte jeder weitere Lauf"
                                + " dasselbe wieder."),
                new Zeile(BlockSatz.RUECKWEG_KEIN_ORT, 1L,
                        Einsatz.keine(),
                        "&#a5b4bfDu hast keinen Ort&#6e7a82 - von der Konsole"
                                + " geht /freimarken blockbuch zurueck <radius>"
                                + " <minuten> bei <welt> <x> <y> <z>."),
                new Zeile(BlockSatz.RUECKWEG_KEINE_HOEHE, 1L,
                        Einsatz.von("hoehe", -70L).mit("unten", -64L).mit("oben", 319L),
                        "&#d9564dDie Hoehe -70 gibt es in dieser Welt"
                                + " nicht&#a5b4bf - sie reicht von -64 bis 319."),

                // /shop freigeben - der SCHLUESSEL zu einer fremden Shopkiste.
                // Die Zusage-Zeile nennt AUSDRUECKLICH, was die Freigabe erlaubt und
                // was nicht: Sonst muesste der Besitzer raten, wie weit er gerade
                // Vertrauen verschenkt hat.
                new Zeile(ShopSatz.SHOP_FREIGEGEBEN, 1L,
                        Einsatz.von("spieler", "Bert"), "&#73bf96Bert darf jetzt an diesen Shop&#a5b4bf - Kiste oeffnen und abbauen."
                                + " Ein eigenes Schild daran haengen kann er nicht."),
                new Zeile(ShopSatz.SHOP_GESPERRT, 1L,
                        Einsatz.von("spieler", "Bert"), "&#e3c57eBert darf nicht mehr an diesen Shop."),
                new Zeile(ShopSatz.SHOP_SCHON_FREI, 1L,
                        Einsatz.von("spieler", "Bert"), "&#a5b4bfBert darf schon an diesen Shop."),
                new Zeile(ShopSatz.SHOP_NICHT_FREI, 1L,
                        Einsatz.von("spieler", "Bert"), "&#a5b4bfBert durfte ohnehin nicht an diesen Shop."),
                new Zeile(ShopSatz.SHOP_FREIGABEN_KOPF, 1L,
                        Einsatz.von("anzahl", 2L), "&#c6a15bAn diesem Shop duerfen 2 ausser dir:"),
                new Zeile(ShopSatz.SHOP_FREIGABEN_ZEILE, 1L,
                        Einsatz.von("spieler", "Bert"), "&#a5b4bf  Bert"),
                new Zeile(ShopSatz.SHOP_FREIGABEN_LEER, 1L, Einsatz.keine(), "&#a5b4bfAusser dir darf niemand an diesen Shop."),
                new Zeile(ShopSatz.SHOP_KEIN_SCHILD, 1L,
                        Einsatz.von("reichweite", 5L), "&#a5b4bfDu siehst kein Shopschild an&#6e7a82 (Reichweite 5 Bloecke). Stell"
                                + " dich vor dein Schild und tippe /shop freigeben"
                                + " <name>."),
                // Freigeben darf NUR der Besitzer - nicht das Team, nicht ein schon
                // Freigegebener. Sonst waere der Schluessel selbst weitergebbar.
                new Zeile(ShopSatz.SHOP_NICHT_DEINER, 1L, Einsatz.keine(), "&#d9564dDieser Shop gehoert dir nicht.&#a5b4bf Freigeben darf nur, wem der"
                                + " Laden gehoert."),
                new Zeile(ShopSatz.SHOP_BUND_NICHT_FREIGEBBAR, 1L, Einsatz.keine(),
                        "&#a5b4bfEin Bundshop gehoert niemandem - freigeben kann ihn darum"
                                + " auch niemand. Wer an ihn darf, entscheidet das"
                                + " Recht freimarken.shop.bund."),
                new Zeile(ShopSatz.SHOP_SELBST, 1L, Einsatz.keine(), "&#a5b4bfAn deinen eigenen Shop darfst du ohnehin."),
                new Zeile(ShopSatz.SHOP_UNBEKANNT, 1L, Einsatz.von("name", "bertt"),
                        "&#d9564dDen Spieler bertt kennt dieser Server nicht."),
                // SAGT, DASS SICH NICHTS GEAENDERT HAT. Beim Sperren ist das die
                // wichtigere Haelfte: Wer glaubt, jemanden ausgesperrt zu haben, und
                // der kommt weiter hinein, merkt es womoeglich nie.
                new Zeile(ShopSatz.SHOP_FREIGABE_GESTOERT, 1L, Einsatz.keine(), "&#d9564dDie Freigabe liess sich nicht speichern&#a5b4bf - es hat sich"
                                + " NICHTS geaendert. Der Grund steht im"
                                + " Serverprotokoll."),
                // /shop liste - die eigenen Laeden, auf Seiten. Die Zeile traegt die
                // STELLE vorn: Das ist der Zweck dieser Liste, nicht die Ware.
                new Zeile(ShopSatz.SHOP_LISTE_LEER, 1L, Einsatz.keine(), "&#a5b4bfDu hast noch keinen Kistenshop. Einer entsteht aus einem"
                                + " Schild an einer Kiste - die erste Zeile bleibt leer, dann"
                                + " Menge, Preis und Ware."),
                new Zeile(ShopSatz.SHOP_LISTE_KOPF, 1L,
                        Einsatz.von("anzahl", 3L).mit("seite", 1L).mit("seiten", 2L),
                        "&#c6a15bDeine Kistenshops (3) - Seite 1 von 2"),
                // Die beiden Preisstuecke sind Art.WORT und stehen darum NICHT in
                // dieser Tafel - sie werden nie allein gesendet. Eingesetzt werden sie
                // hier fertig, genau wie der Zweig sie baut.
                new Zeile(ShopSatz.SHOP_LISTE_ZEILE, 1L,
                        Einsatz.von("welt", "freimarken:altfurt").mit("x", 60L)
                                .mit("y", 71L).mit("z", 6L).mit("menge", 16L)
                                .mit("ware", "Stein")
                                .mit("kauf", " \u00b7 Kauf 100 Mark")
                                .mit("verkauf", " \u00b7 Ankauf 80 Mark"),
                        "&#a5b4bf  freimarken:altfurt 60/71/6 · 16x Stein · Kauf 100 Mark"
                                + " · Ankauf 80 Mark"),
                new Zeile(ShopSatz.SHOP_LISTE_WEITER, 1L,
                        Einsatz.von("naechste", 2L), "&#6e7a82/shop liste 2 zeigt die naechste Seite"),
                new Zeile(ShopSatz.SHOP_LISTE_KEINE_SEITE, 1L,
                        Einsatz.von("seite", 9L).mit("seiten", 2L), "&#a5b4bfSeite 9 gibt es nicht - deine Shops stehen auf 2 Seiten."),
                new Zeile(ShopSatz.SHOP_LISTE_GESTOERT, 1L, Einsatz.keine(),
                        "&#d9564dDeine Shopliste liess sich nicht lesen&#a5b4bf - deine Laeden"
                                + " sind deswegen NICHT weg. Der Grund steht im"
                                + " Serverprotokoll."),

                // --- Der ANKAUF DES BUNDES am Schild [10.11.2026] ---------------
                //
                // SIEBEN Zeilen standen hier am Mittag und sechs sind am Nachmittag
                // wieder gegangen - mit der Tafel bundesmarkt.ankauf. Sie sagten, was an
                // einer Tafel schiefgeht; ohne Tafel gibt es keinen dieser Faelle.
                // Geblieben ist der DECKEL, und der ist der Grund des ganzen Schrittes.
                new Zeile(ShopSatz.SHOP_BUND_DECKEL, 1L, Einsatz.keine(),
                        "&#e3c57eHeute ist genug verkauft - morgen kauft der Bund wieder."),

                // --- Die Freie Truhe [Teil D]: ACHT Zeilen -------------------
                //
                // ZWEI PAARE MIT ZWEI BLICKWINKELN, und in beiden Paaren stehen hier
                // darum verschiedene WORTE und nicht verschiedene Zahlen: offen gegen
                // offen-frei (mit Deckel eine Zahl, ohne Deckel ein eigener Satz) und
                // zurueck gegen behalten (was dableibt, was mitkommt). Wer zwei von
                // ihnen vertauscht, bekommt hier eine rote Probe - und nicht einen
                // Spieler, der "nimm, was du brauchst" liest und am Deckel steht.
                new Zeile(TruheSatz.OFFEN, 1L, Einsatz.von("frei", 11),
                        "&#a5b4bfFreie Truhe - heute kannst du noch &#edf1f311&#a5b4bf Gegenstaende"
                                + " behalten. Hineinlegen darfst du immer."),
                new Zeile(TruheSatz.OFFEN_FREI, 1L, Einsatz.keine(),
                        "&#a5b4bfFreie Truhe - nimm, was du brauchst, und lege hinein, was du"
                                + " nicht brauchst."),
                new Zeile(TruheSatz.DECKEL, 1L, Einsatz.von("deckel", 16),
                        "&#e3c57eHeute hast du deine 16 Gegenstaende aus der Freien"
                                + " Truhe&#a5b4bf - morgen ist sie wieder voll."),
                new Zeile(TruheSatz.BESETZT, 1L, Einsatz.keine(),
                        "&#e3c57eJemand sieht gerade in die Freie Truhe&#a5b4bf - einen"
                                + " Augenblick."),
                new Zeile(TruheSatz.GESTOERT, 1L, Einsatz.keine(),
                        "&#d9564dDie Freie Truhe antwortet gerade nicht&#a5b4bf - dein Tagesstand"
                                + " liess sich nicht lesen. Der Grund steht im"
                                + " Serverprotokoll."),
                new Zeile(TruheSatz.BEHALTEN, 1L,
                        Einsatz.von("stueck", 9).mit("frei", 7),
                        "&#a5b4bf9 Gegenstaende behalten - heute noch &#edf1f37&#a5b4bf frei."),
                new Zeile(TruheSatz.ZURUECK, 1L,
                        Einsatz.von("stueck", 14).mit("deckel", 16),
                        "&#e3c57e14 Gegenstaende gehen zurueck in die Truhe&#a5b4bf - mehr als 16"
                                + " am Tag nimmt niemand mit."),
                new Zeile(TruheSatz.VERBOTEN, 1L, Einsatz.von("ware", "WRITTEN_BOOK"),
                        "&#e3c57eWRITTEN_BOOK gehoert nicht in die Freie Truhe&#a5b4bf - du"
                                + " bekommst es zurueck."),

                // --- /freistadt [11.11.2026] ----------------------------------------
                new Zeile(ReiseSatz.WARP_STADT_UNBEKANNT, 1L,
                        Einsatz.von("name", "neustadt"),
                        "&#d9564dDie Freistadt neustadt gibt es nicht.\n"
                                + "&#a5b4bfWelche es gibt, zeigt /freistadt."),
                new Zeile(ReiseSatz.WARP_STADT_OHNE_NAMEN, 1L, Einsatz.keine(),
                        "&#a5b4bfWelche Freistadt? Die Namen zeigt /freistadt."),
                new Zeile(ReiseSatz.WARP_STADT_KEINE_WELT, 1L, Einsatz.keine(),
                        "&#d9564dDie Welt der Freistaedte fehlt - sag es dem Betreiber."),

                new Zeile(FreistadtSatz.KOPF, 1L, Einsatz.keine(), "&#c6a15bDie Freistaedte"),
                new Zeile(FreistadtSatz.ZEILE, 1L,
                        Einsatz.von("name", "neustadt").mit("besitzer", "Anna")
                                .mit("feld", "3/-7"), "&#a5b4bf  neustadt&#6e7a82 - von Anna, Feld 3/-7"),
                // --- Teil E3: die Auflage am Teleporter --------------------------
                new Zeile(ReiseSatz.WARP_STADT_OHNE_AUFLAGE, 1L,
                        Einsatz.von("name", "annastadt").mit("was",
                                "ein Regeltext und ein vergebenes Grundstueck"),
                        "&#d9564dannastadt hat noch keinen Teleporter\n"
                                + "&#a5b4bfEs fehlt: ein Regeltext und ein vergebenes Grundstueck."
                                + " Das stellt der Stadtbesitzer ein."),
                new Zeile(FreistadtSatz.LEER, 1L, Einsatz.keine(), "&#a5b4bf  Es gibt noch keine Freistadt - macht die erste: /freistadt gruenden deinname"),
                // Der Betrag kommt als WORT herein und nicht als Zahl [Teil C3]:
                // Seit die Einheit aus waehrung.betrag stammt, setzt die Naht
                // sprache.betrag ein. Das Bild ist dasselbe wie vorher - und genau
                // das ist die Zusage: Der Spieler liest unveraendert weiter.
                new Zeile(FreistadtSatz.FRAGE, 1L,
                        Einsatz.von("name", "neustadt")
                                .mit("preis", "400.000 Mark")
                                .mit("danach", "123.456 Mark"),
                        "&#e3c57eneustadt gruenden kostet 400.000 Mark. Danach"
                                + " haettest du 123.456 Mark.\n"
                                + "&#a5b4bfZum Gruenden: /freistadt gruenden neustadt"
                                + " bestaetigen"),
                new Zeile(FreistadtSatz.GEGRUENDET, 1L,
                        Einsatz.von("name", "neustadt").mit("feld", "3/-7"), "&#73bf96neustadt ist gegruendet - Feld 3/-7.\n"
                        + "&#a5b4bfHin kommst du mit /warp freistadt neustadt.\n"
                        + "&#a5b4bfBis die Stadt Regeln hat und ein Grundstueck"
                        + " vergeben ist, kommen keine Fremden - und es entstehen"
                        + " keine Laeden."),
                new Zeile(FreistadtSatz.NAME_ZU_LANG, 1L,
                        Einsatz.von("kleinstens", 3L).mit("groesstens", 32L), "&#d9564dEin Stadtname hat 3 bis 32 Zeichen."),
                new Zeile(FreistadtSatz.NAME_FALSCH, 1L, Einsatz.keine(), "&#d9564dEin Stadtname traegt nur kleine Buchstaben, Ziffern und"
                        + " Bindestriche und faengt mit einem Buchstaben an."),
                new Zeile(FreistadtSatz.NAME_RESERVIERT, 1L,
                        Einsatz.von("name", "kornmark"), "&#d9564dkornmark ist der Name einer Welt - so darf keine Stadt heissen."),
                new Zeile(FreistadtSatz.NAME_VERGEBEN, 1L,
                        Einsatz.von("name", "neustadt"), "&#d9564dDie Freistadt neustadt gibt es schon."),
                new Zeile(FreistadtSatz.GRENZE_ERREICHT, 1L,
                        Einsatz.von("hoechstzahl", 1L), "&#d9564dDu hast schon so viele Freistaedte, wie dein Rang erlaubt (1)."),
                new Zeile(FreistadtSatz.KEIN_FREIES_FELD, 1L, Einsatz.keine(), "&#d9564dIn dieser Welt ist kein Stadtfeld mehr frei."),
                // Die Werbezeile unter der Absage [Teil H]. Derselbe Wortlaut wie bei
                // der Parzelle - er steht EINMAL in der config.yml -, nur die Farbe
                // ist je Bereich eigen.
                new Zeile(FreistadtSatz.RANG_WERBUNG, 1L,
                        Einsatz.von("werbung", Wirtschaftswerte.VORGABE_RANG_WERBUNG),
                        "&#e3c57eMehr erlaubt ein hoeherer Rang."),
                // Der FEHLBETRAG traegt die Einheit jetzt mit [Teil C3]. Vorher
                // stand sie nur am Preis, weil sie im Satz stand - "dir fehlen" 
                // 276.544 liess offen, in was.
                new Zeile(FreistadtSatz.ZU_WENIG_MARK, 1L,
                        Einsatz.von("preis", "400.000 Mark")
                                .mit("fehlt", "276.544 Mark"),
                        "&#d9564dEine Freistadt kostet 400.000 Mark - dir fehlen"
                                + " 276.544 Mark."),
                new Zeile(FreistadtSatz.GESTOERT, 1L, Einsatz.keine(), "&#d9564dDas Gruenden hat nicht geklappt - dein Geld ist zurueck."
                        + " Versuch es noch einmal."),
                new Zeile(FreistadtSatz.KEINE_WELT, 1L, Einsatz.keine(),
                        "&#d9564dDie Welt der Freistaedte fehlt - sag es dem Betreiber."),

                // --- Die Grundstuecke [Teil C2, 12.11.2026] ----------------------
                // Die Eckensaetze nennen BEIDES, Blockstelle und Zelle: Wer drei
                // Bloecke neben seiner Marke steht, trifft dieselbe Zelle und bekommt
                // deshalb kein breiteres Grundstueck. Ohne die Zellnummer koennte er
                // das nicht nachvollziehen.
                new Zeile(FreistadtSatz.ECKE, 1L,
                        Einsatz.von("nummer", "1").mit("x", "600").mit("z", "312")
                                .mit("zelle", "37/19"),
                        "&#73bf96Ecke 1 steht&#a5b4bf bei 600/312, Zelle 37/19."),
                new Zeile(FreistadtSatz.ECKE_NEU, 1L,
                        Einsatz.von("x", "600").mit("z", "312").mit("zelle", "37/19"),
                        "&#e3c57eNeue Absteckung&#a5b4bf - Ecke 1 steht bei 600/312, Zelle 37/19."
                                + " Die vorige ist weg."),
                // GEZAEHLT: Bei einer Zelle steht die Eins im Satz, nicht als Wert.
                // DAS ENDE EINER STADT [Plan 16, Teil E6]. Beide Rueckfragen zeigen
                // ALLE DREI Zahlen - ein Besitzer soll nachrechnen koennen, woher die
                // Summe kommt.
                new Zeile(FreistadtSatz.AUFGABE_FRAGE, 1L,
                        Einsatz.von("stadt", "annastadt")
                                .mit("erstattung", "200.000 Mark")
                                .mit("kasse", "1.250 Mark")
                                .mit("zusammen", "201.250 Mark")
                                .mit("danach", "/freistadt aufgeben bestaetigen"),
                        "&#c6a15bannastadt aufgeben?\n&#a5b4bfZurueck: 200.000 Mark vom"
                                + " Gruendungspreis und 1.250 Mark aus der Stadtkasse,"
                                + " zusammen &#edf1f3201.250 Mark&#a5b4bf.\n&#a5b4bfDann:"
                                + " /freistadt aufgeben bestaetigen"),
                new Zeile(FreistadtSatz.RAEUMUNG_FRAGE, 1L,
                        Einsatz.von("stadt", "annastadt")
                                .mit("erstattung", "200.000 Mark")
                                .mit("kasse", "1.250 Mark")
                                .mit("zusammen", "201.250 Mark")
                                .mit("danach",
                                        "/freistadt raeumen annastadt bestaetigen"),
                        "&#c6a15bannastadt raeumen?\n&#a5b4bfIhr Besitzer bekommt 200.000"
                                + " Mark vom Gruendungspreis und 1.250 Mark aus der"
                                + " Stadtkasse, zusammen &#edf1f3201.250 Mark&#a5b4bf.\n&#a5b4bfDann:"
                                + " /freistadt raeumen annastadt bestaetigen"),
                new Zeile(FreistadtSatz.AUFGABE_NAME_UNBEKANNT, 1L,
                        Einsatz.von("name", "geisterstadt"),
                        "&#d9564dEine Freistadt geisterstadt gibt es nicht.&#a5b4bf Die Namen"
                                + " zeigt /freistadt."),
                new Zeile(FreistadtSatz.AUFGABE_BEENDET, 1L,
                        Einsatz.von("stadt", "annastadt")
                                .mit("zusammen", "201.250 Mark"),
                        "&#73bf96annastadt ist aufgeloest.&#a5b4bf Ausgezahlt: 201.250 Mark. Das"
                                + " Feld ist wieder frei."),
                // DER SATZ, OHNE DEN DIESER BEFEHL EIN DIEBSTAHL WAERE: Ein
                // Stadtbuerger hat sein Grundstueck bezahlt.
                new Zeile(FreistadtSatz.AUFGABE_NOCH_BUERGER, 1L,
                        Einsatz.von("stadt", "annastadt"),
                        "&#d9564dIn annastadt gehoert noch jemand anderem ein"
                                + " Grundstueck.\n&#a5b4bfKauf es zurueck, dann laesst sich"
                                + " die Stadt aufloesen - sie verfaellt ohnehin nicht,"
                                + " solange dort jemand wohnt."),
                new Zeile(FreistadtSatz.AUFGABE_GELD_HAENGT, 1L,
                        Einsatz.von("stadt", "annastadt")
                                .mit("zusammen", "201.250 Mark"),
                        "&#d9564dannastadt ist aufgeloest, aber die Auszahlung von 201.250"
                                + " Mark ging nicht durch.\n&#a5b4bfSag es dem Betreiber -"
                                + " der Betrag steht im Serverprotokoll."),

                // DER VERFALL [Plan 16, Teil E5]. Die zwei Warnungen sind GEZAEHLT,
                // also braucht jede zwei Zeilen: die Einzahl fuer den letzten Tag und
                // die Mehrzahl. Dass es zwei verschiedene Saetze sind, ist der ganze
                // Sinn von zwei Stufen - wer zwei Wochen vorher denselben Ton liest
                // wie zwei Monate davor, liest ihn nicht mehr.
                new Zeile(FreistadtSatz.VERFALL_WARNUNG_FRUEH, 2L,
                        Einsatz.von("stadt", "annastadt").mit("tage", 2L),
                        "&#a5b4bfDeine Freistadt annastadt verfaellt in 2 Tagen, wenn"
                                + " niemand sie betritt."),
                new Zeile(FreistadtSatz.VERFALL_WARNUNG_FRUEH, 1L,
                        Einsatz.von("stadt", "annastadt").mit("tage", 1L),
                        "&#a5b4bfDeine Freistadt annastadt verfaellt morgen, wenn niemand"
                                + " sie betritt."),
                // DIE NULL GEHOERT DAZU, auch wo sie im Spiel nicht vorkommt: Bei 0
                // Resttagen ist die Stadt FAELLIG, und der taegliche Lauf macht daraus
                // VERFALLEN. Ohne diese Zeile bliebe die Mehrzahlform fuer 0
                // ungeprueft - dieselbe Begruendung wie bei ECKEN_FERTIG weiter unten.
                new Zeile(FreistadtSatz.VERFALL_WARNUNG_FRUEH, 0L,
                        Einsatz.von("stadt", "annastadt").mit("tage", 0L),
                        "&#a5b4bfDeine Freistadt annastadt verfaellt in 0 Tagen, wenn"
                                + " niemand sie betritt."),
                new Zeile(FreistadtSatz.VERFALL_WARNUNG_SPAET, 2L,
                        Einsatz.von("stadt", "annastadt").mit("tage", 2L),
                        "&#d9564dDeine Freistadt annastadt verfaellt in 2 Tagen!\n&#a5b4bfGeh"
                                + " hin, oder vergib ein Grundstueck - ein"
                                + " Stadtbuerger haelt sie dauerhaft."),
                new Zeile(FreistadtSatz.VERFALL_WARNUNG_SPAET, 1L,
                        Einsatz.von("stadt", "annastadt").mit("tage", 1L),
                        "&#d9564dDeine Freistadt annastadt verfaellt MORGEN!\n&#a5b4bfGeh hin,"
                                + " oder vergib ein Grundstueck - ein Stadtbuerger"
                                + " haelt sie dauerhaft."),
                // DIESE ZEILE IST IM SPIEL NICHT MEHR ZU SEHEN [23.11.2026], und sie
                // steht nur hier, weil die Tafel jede Zaehlform verlangt: Bis dahin gab
                // Freistadttext den Stand FAELLIG an diesen Satz weiter, und resttage ist
                // dort immer 0 - der Besitzer las genau das. Die FORM war nie falsch;
                // falsch war, FAELLIG hineinzugeben. FAELLIG hat jetzt den Satz darunter.
                new Zeile(FreistadtSatz.VERFALL_WARNUNG_SPAET, 0L,
                        Einsatz.von("stadt", "annastadt").mit("tage", 0L),
                        "&#d9564dDeine Freistadt annastadt verfaellt in 0 Tagen!\n&#a5b4bfGeh"
                                + " hin, oder vergib ein Grundstueck - ein"
                                + " Stadtbuerger haelt sie dauerhaft."),
                // DER DRITTE ZUSTAND: Die Frist ist um, der taegliche Lauf hat die Stadt
                // aber noch nicht abgeraeumt. Ungezaehlt - es gibt hier keine Zahl.
                new Zeile(FreistadtSatz.VERFALL_FAELLIG, 1L,
                        Einsatz.von("stadt", "annastadt"),
                        "&#d9564dDeine Freistadt annastadt verfaellt JETZT!\n&#a5b4bfGeh sofort"
                                + " hin, oder vergib ein Grundstueck - ein Stadtbuerger"
                                + " haelt sie dauerhaft."),
                // SAGT AUSDRUECKLICH, DASS NICHTS VERLOREN IST: Ohne diese Haelfte
                // hielte ein Besitzer seine 400.000 Mark fuer verbrannt.
                new Zeile(FreistadtSatz.VERFALL_VERFALLEN, 1L,
                        Einsatz.von("stadt", "annastadt"),
                        "&#d9564dDeine Freistadt annastadt ist verfallen.\n&#a5b4bfFeld, Name"
                                + " und Guthaben hat sie noch - betritt sie, dann"
                                + " wacht sie wieder auf."),
                new Zeile(FreistadtSatz.VERFALL_AUFGEWACHT, 1L,
                        Einsatz.von("stadt", "annastadt"),
                        "&#73bf96annastadt ist wieder aktiv.&#a5b4bf Hier baut wieder, wer darf."),
                new Zeile(FreistadtSatz.ECKEN_FERTIG, 1L, Einsatz.von("zellen", "1"),
                        "&#a5b4bf  Das waere 1 Zelle. Anlegen mit /freistadt grundstueck"
                                + " anlegen"),
                new Zeile(FreistadtSatz.ECKEN_FERTIG, 2L, Einsatz.von("zellen", "2"),
                        "&#a5b4bf  Das waeren 2 Zellen. Anlegen mit /freistadt grundstueck"
                                + " anlegen"),
                // Die NULL gehoert dazu, auch wo sie im Spiel nicht vorkommt:
                // Ein Zuschnitt hat immer mindestens eine Zelle. Die Tafel verlangt sie
                // trotzdem - sonst bliebe die Mehrzahlform fuer 0 ungeprueft, und ein
                // Betreiber, der sie in der Datei aendert, merkte es nirgends.
                new Zeile(FreistadtSatz.ECKEN_FERTIG, 0L, Einsatz.von("zellen", "0"),
                        "&#a5b4bf  Das waeren 0 Zellen. Anlegen mit /freistadt grundstueck"
                                + " anlegen"),
                new Zeile(FreistadtSatz.KEINE_ECKEN, 1L, Einsatz.keine(),
                        "&#d9564dDu hast nichts abgesteckt.\n&#a5b4bfStell dich in eine Ecke und"
                                + " tippe /freistadt grundstueck ecke, dann in die"
                                + " andere."),
                new Zeile(FreistadtSatz.EINE_ECKE, 1L, Einsatz.keine(),
                        "&#d9564dEs fehlt die zweite Ecke.\n&#a5b4bfGeh zur anderen Ecke und tippe"
                                + " /freistadt grundstueck ecke."),
                new Zeile(FreistadtSatz.ZUGESCHNITTEN, 1L,
                        Einsatz.von("zuschnitt", "37/19 bis 37/19").mit("zellen", "1")
                                .mit("bloecke", "256"),
                        "&#73bf96Grundstueck zugeschnitten&#a5b4bf - Zellen 37/19 bis 37/19, 1 Zelle,"
                                + " 256 Bloecke."),
                new Zeile(FreistadtSatz.ZUGESCHNITTEN, 2L,
                        Einsatz.von("zuschnitt", "37/19 bis 38/19").mit("zellen", "2")
                                .mit("bloecke", "512"),
                        "&#73bf96Grundstueck zugeschnitten&#a5b4bf - Zellen 37/19 bis 38/19, 2"
                                + " Zellen, 512 Bloecke."),
                new Zeile(FreistadtSatz.ZUGESCHNITTEN, 0L,
                        Einsatz.von("zuschnitt", "37/19 bis 38/19").mit("zellen", "0")
                                .mit("bloecke", "0"),
                        "&#73bf96Grundstueck zugeschnitten&#a5b4bf - Zellen 37/19 bis 38/19, 0"
                                + " Zellen, 0 Bloecke."),
                new Zeile(FreistadtSatz.AUFGELOEST, 1L,
                        Einsatz.von("zuschnitt", "37/19 bis 39/20"),
                        "&#73bf96Grundstueck aufgeloest&#a5b4bf - die Zellen 37/19 bis 39/20"
                                + " gehoeren wieder der Stadt."),
                new Zeile(FreistadtSatz.GRUNDSTUECK_KOPF, 1L,
                        Einsatz.von("stadt", "neustadt"),
                        "&#c6a15bGrundstuecke in neustadt"),
                new Zeile(FreistadtSatz.GRUNDSTUECK_ZEILE, 1L,
                        Einsatz.von("zuschnitt", "37/19 bis 37/19")
                                .mit("besitzer", "Bert").mit("zellen", "1"),
                        "&#a5b4bf  37/19 bis 37/19 \u00b7 &#edf1f3Bert&#a5b4bf \u00b7 1 Zelle"),
                new Zeile(FreistadtSatz.GRUNDSTUECK_ZEILE, 2L,
                        Einsatz.von("zuschnitt", "37/19 bis 38/19")
                                .mit("besitzer", "Bert").mit("zellen", "2"),
                        "&#a5b4bf  37/19 bis 38/19 \u00b7 &#edf1f3Bert&#a5b4bf \u00b7 2 Zellen"),
                new Zeile(FreistadtSatz.GRUNDSTUECK_ZEILE, 0L,
                        Einsatz.von("zuschnitt", "37/19 bis 38/19")
                                .mit("besitzer", "Bert").mit("zellen", "0"),
                        "&#a5b4bf  37/19 bis 38/19 \u00b7 &#edf1f3Bert&#a5b4bf \u00b7 0 Zellen"),
                new Zeile(FreistadtSatz.GRUNDSTUECK_LEER, 1L, Einsatz.keine(),
                        "&#a5b4bf  noch keines - stell dich in eine Ecke und tippe /freistadt"
                                + " grundstueck ecke"),
                new Zeile(FreistadtSatz.NICHT_EIN_FELD, 1L, Einsatz.keine(),
                        "&#d9564dBeide Ecken muessen im SELBEN Stadtfeld stehen.\n&#a5b4bfZwischen"
                                + " den Feldern liegt Leere, und auf dem Ankunftsfeld"
                                + " wird nicht zugeschnitten."),
                new Zeile(FreistadtSatz.KEINE_STADT, 1L, Einsatz.keine(),
                        "&#d9564dAuf diesem Feld steht keine Stadt.\n&#a5b4bfErst gruenden - der"
                                + " Befehl dazu heisst /freistadt gruenden, dahinter der"
                                + " Name."),
                new Zeile(FreistadtSatz.NICHT_DEINE_STADT, 1L, Einsatz.keine(),
                        "&#d9564dDas ist nicht deine Stadt.\n&#a5b4bfZuschneiden darf nur, wem die"
                                + " Stadt gehoert - auch ein Mitbauer nicht."),
                new Zeile(FreistadtSatz.UEBERSCHNEIDUNG, 1L, Einsatz.keine(),
                        "&#d9564dHier liegt schon ein Grundstueck.\n&#a5b4bfWelche es gibt, zeigt"
                                + " /freistadt grundstueck."),
                // Die Absage nennt BEIDE Zahlen - ohne die eigene weiss der Spieler
                // nicht, wie weit er darueber ist.
                new Zeile(FreistadtSatz.ZU_GROSS, 1L,
                        Einsatz.von("zellen", "1").mit("hoechstens", "4"),
                        "&#d9564dEin Grundstueck darf hoechstens 4 Zellen haben - dein"
                                + " Zuschnitt hat 1."),
                new Zeile(FreistadtSatz.ZU_GROSS, 2L,
                        Einsatz.von("zellen", "2").mit("hoechstens", "1"),
                        "&#d9564dEin Grundstueck darf hoechstens 1 Zellen haben - dein"
                                + " Zuschnitt hat 2."),
                new Zeile(FreistadtSatz.ZU_GROSS, 0L,
                        Einsatz.von("zellen", "0").mit("hoechstens", "4"),
                        "&#d9564dEin Grundstueck darf hoechstens 4 Zellen haben - dein"
                                + " Zuschnitt hat 0."),
                new Zeile(FreistadtSatz.KEIN_GRUNDSTUECK, 1L, Einsatz.keine(),
                        "&#d9564dHier liegt kein Grundstueck.\n&#a5b4bfStell dich darauf und tippe"
                                + " den Befehl noch einmal."),
                new Zeile(FreistadtSatz.NICHT_DEIN_GRUNDSTUECK, 1L, Einsatz.keine(),
                        "&#d9564dDieses Grundstueck gehoert jemand anderem.\n&#a5b4bfAufloesen darf"
                                + " nur sein Besitzer - auch der Stadtbesitzer nicht."),
                new Zeile(FreistadtSatz.GRUNDSTUECK_GESTOERT, 1L, Einsatz.keine(),
                        "&#d9564dDas hat gerade nicht geklappt - versuch es noch einmal."),

                // --- Angebot und Verkauf [Teil C3, 12.11.2026] -------------
                //
                // DREI PAARE, und jedes zweite gilt dem GESCHENK. Beide Formen
                // stehen hier, weil der Unterschied nur in der Sprachdatei liegt:
                // Wer eines Tages beide Saetze gleich schreibt, merkt es an keiner
                // anderen Probe.
                new Zeile(FreistadtSatz.ANGEBOTEN, 1L,
                        Einsatz.von("zuschnitt", "37/19 bis 38/19")
                                .mit("preis", "1.500 Mark"),
                        "&#73bf96Zum Verkauf gestellt&#a5b4bf - Zellen 37/19 bis 38/19 fuer"
                                + " 1.500 Mark.\n&#a5b4bfWer darauf steht, kauft mit"
                                + " /freistadt grundstueck kaufen."),
                new Zeile(FreistadtSatz.ANGEBOTEN_GRATIS, 1L,
                        Einsatz.von("zuschnitt", "37/19 bis 38/19"),
                        "&#73bf96Zu verschenken&#a5b4bf - Zellen 37/19 bis 38/19, kostenlos."
                                + "\n&#a5b4bfWer darauf steht, nimmt es mit /freistadt"
                                + " grundstueck kaufen."),
                new Zeile(FreistadtSatz.ZURUECKGEZOGEN, 1L,
                        Einsatz.von("zuschnitt", "37/19 bis 38/19"),
                        "&#e3c57eAngebot zurueckgezogen&#a5b4bf - die Zellen 37/19 bis 38/19"
                                + " stehen nicht mehr zum Verkauf."),
                new Zeile(FreistadtSatz.GEKAUFT, 1L,
                        Einsatz.von("zuschnitt", "37/19 bis 38/19")
                                .mit("preis", "1.500 Mark")
                                .mit("verkaeufer", "Bert"),
                        "&#73bf96Gekauft!&#a5b4bf Die Zellen 37/19 bis 38/19 gehoeren jetzt"
                                + " dir - fuer 1.500 Mark von &#edf1f3Bert&#a5b4bf."),
                new Zeile(FreistadtSatz.GEKAUFT_GRATIS, 1L,
                        Einsatz.von("zuschnitt", "37/19 bis 38/19")
                                .mit("verkaeufer", "Bert"),
                        "&#73bf96Bekommen!&#a5b4bf Die Zellen 37/19 bis 38/19 gehoeren jetzt"
                                + " dir - geschenkt von &#edf1f3Bert&#a5b4bf."),
                new Zeile(FreistadtSatz.VERKAUFT, 1L,
                        Einsatz.von("zuschnitt", "37/19 bis 38/19")
                                .mit("preis", "1.500 Mark")
                                .mit("kaeufer", "Bert"),
                        "&#73bf96Verkauft!&#a5b4bf &#edf1f3Bert&#a5b4bf hat die Zellen 37/19 bis 38/19"
                                + " fuer 1.500 Mark gekauft."),
                new Zeile(FreistadtSatz.VERKAUFT_GRATIS, 1L,
                        Einsatz.von("zuschnitt", "37/19 bis 38/19")
                                .mit("kaeufer", "Bert"),
                        "&#73bf96Uebergeben!&#a5b4bf &#edf1f3Bert&#a5b4bf hat die Zellen 37/19 bis 38/19"
                                + " von dir bekommen."),
                // Die Gebuehrzeile steht EINGERUECKT unter dem Erloes - sie
                // gehoert zur Zeile darueber und ist keine eigene Meldung.
                new Zeile(FreistadtSatz.VERKAUFT_GEBUEHR, 1L,
                        Einsatz.von("gebuehr", "75 Mark").mit("prozent", "5"),
                        "&#a5b4bf  Abzueglich 75 Mark Marktgebuehr (5 Prozent)."),
                new Zeile(FreistadtSatz.KEIN_ANGEBOT, 1L, Einsatz.keine(),
                        "&#d9564dDieses Grundstueck steht nicht zum Verkauf.\n&#a5b4bfWelche"
                                + " zu haben sind, zeigt /freistadt angebote."),
                new Zeile(FreistadtSatz.EIGENES_GRUNDSTUECK, 1L, Einsatz.keine(),
                        "&#d9564dDas ist dein eigenes Grundstueck.\n&#a5b4bfZurueckziehen"
                                + " kannst du das Angebot mit /freistadt"
                                + " grundstueck zurueckziehen."),
                new Zeile(FreistadtSatz.GRUNDSTUECK_ZU_WENIG, 1L,
                        Einsatz.von("preis", "1.500 Mark")
                                .mit("fehlt", "400 Mark"),
                        "&#d9564dDas Grundstueck kostet 1.500 Mark - dir fehlen 400 Mark."),
                new Zeile(FreistadtSatz.PREIS_UNGUELTIG, 1L, Einsatz.keine(),
                        "&#d9564dEin Preis unter null ist keiner.\n&#a5b4bfEine 0 ist"
                                + " erlaubt und heisst: zu verschenken."),
                new Zeile(FreistadtSatz.ANGEBOT_KOPF, 1L, Einsatz.keine(),
                        "&#c6a15bGrundstuecke zu haben"),
                new Zeile(FreistadtSatz.ANGEBOT_ZEILE, 1L,
                        Einsatz.von("zuschnitt", "37/19 bis 38/19")
                                .mit("preis", "1.500 Mark")
                                .mit("besitzer", "Bert"),
                        "&#a5b4bf  37/19 bis 38/19 \u00b7 &#73bf961.500 Mark&#a5b4bf \u00b7 &#edf1f3Bert"),
                new Zeile(FreistadtSatz.ANGEBOT_ZEILE_GRATIS, 1L,
                        Einsatz.von("zuschnitt", "37/19 bis 38/19")
                                .mit("besitzer", "Bert"),
                        "&#a5b4bf  37/19 bis 38/19 \u00b7 &#73bf96kostenlos&#a5b4bf \u00b7 &#edf1f3Bert"),
                new Zeile(FreistadtSatz.ANGEBOT_LEER, 1L, Einsatz.keine(),
                        "&#a5b4bf  keines - frag in den Staedten nach, wer etwas abgibt"),
                // --- Teil D3: die Buergerrechte ---------------------------------
                //
                // Die drei RECHTEWORTE stehen hier NICHT: Sie sind Art.WORT und tragen
                // kein Bild - sie gehen als Wert in die Saetze darunter. Dass jedes von
                // ihnen durch die echte Zeile der Vorlage geht, haelt
                // FreistadtBefehlTest.
                new Zeile(FreistadtSatz.RECHTE_KOPF, 1L,
                        Einsatz.von("stadt", "Annastadt"),
                        "&#c6a15bWas die Stadtbuerger von Annastadt duerfen"),
                new Zeile(FreistadtSatz.RECHT_ERLAUBT, 1L,
                        Einsatz.von("recht", "Tueren und Knoepfe benutzen"),
                        "&#a5b4bf  Tueren und Knoepfe benutzen \u00b7 &#73bf96erlaubt"),
                new Zeile(FreistadtSatz.RECHT_GESPERRT, 1L,
                        Einsatz.von("recht", "bauen"),
                        "&#a5b4bf  bauen \u00b7 &#d9564dgesperrt"),
                new Zeile(FreistadtSatz.RECHTE_HINWEIS, 1L, Einsatz.keine(),
                        "&#a5b4bfAendern: /freistadt rechte an [recht] oder /freistadt rechte"
                                + " aus [recht]"),
                new Zeile(FreistadtSatz.RECHT_GEAENDERT, 1L,
                        Einsatz.von("recht", "Truhen und Oefen oeffnen"),
                        "&#73bf96Stadtbuerger duerfen jetzt Truhen und Oefen oeffnen."),
                new Zeile(FreistadtSatz.RECHT_GENOMMEN, 1L,
                        Einsatz.von("recht", "bauen"),
                        "&#e3c57eStadtbuerger duerfen nicht mehr bauen."),
                new Zeile(FreistadtSatz.RECHT_SCHON_SO, 1L,
                        Einsatz.von("recht", "bauen"),
                        "&#a5b4bfDas war schon eingestellt: bauen."),
                new Zeile(FreistadtSatz.RECHTE_NICHT_DEINE, 1L,
                        Einsatz.von("stadt", "Annastadt").mit("besitzer", "Anna"),
                        "&#d9564dAnnastadt gehoert Anna.\n&#a5b4bfDie Buergerrechte stellt ein, wem"
                                + " die Stadt gehoert."),
                // --- Teil D5: die Stadtbuergerliste ------------------------------
                new Zeile(FreistadtSatz.STADTBUERGER_KOPF, 1L,
                        Einsatz.von("stadt", "annastadt"),
                        "&#c6a15bDie Stadtbuerger von annastadt"),
                new Zeile(FreistadtSatz.STADTBUERGER_BESITZER, 1L,
                        Einsatz.von("besitzer", "Anna"),
                        "&#a5b4bf  Stadtbesitzer: &#edf1f3Anna"),
                // DREI Zeilen fuer den gezaehlten Satz - 1, 2 UND 0: Mit nur einer
                // bliebe die zweite Form der Vorlage ungeprueft, und die 0 ist der
                // Fall, der in keiner anderen Probe vorkommt.
                new Zeile(FreistadtSatz.STADTBUERGER_ZEILE, 1L,
                        Einsatz.von("name", "Bert").mit("anzahl", "1"),
                        "&#a5b4bf  &#edf1f3Bert&#a5b4bf \u00b7 1 Grundstueck"),
                new Zeile(FreistadtSatz.STADTBUERGER_ZEILE, 2L,
                        Einsatz.von("name", "Bert").mit("anzahl", "2"),
                        "&#a5b4bf  &#edf1f3Bert&#a5b4bf \u00b7 2 Grundstuecke"),
                new Zeile(FreistadtSatz.STADTBUERGER_ZEILE, 0L,
                        Einsatz.von("name", "Bert").mit("anzahl", "0"),
                        "&#a5b4bf  &#edf1f3Bert&#a5b4bf \u00b7 0 Grundstuecke"),
                new Zeile(FreistadtSatz.STADTBUERGER_LEER, 1L, Einsatz.keine(),
                        "&#a5b4bf  noch keine - verkauf oder verschenk ein Grundstueck"
                                + " mit /freistadt grundstueck anbieten"),
                // --- Teil E1: die Stadtkasse -------------------------------------
                // Die Bilder sind ZEICHENGLEICH aus dem roten Lauf abgeschrieben -
                // erfundene Bilder pruefen die Vorlage nicht, sondern nur die eigene
                // Erinnerung daran.
                new Zeile(FreistadtSatz.KASSE_KOPF, 1L,
                        Einsatz.von("stadt", "annastadt")
                                .mit("stand", "2.500 Mark"),
                        "&#c6a15bDie Kasse von annastadt&#a5b4bf - &#edf1f32.500 Mark"),
                new Zeile(FreistadtSatz.KASSE_LEER, 1L, Einsatz.keine(),
                        "&#a5b4bf  noch nichts eingenommen - die Marktgebuehr steht ab Werk"
                                + " auf 0 Prozent"),
                new Zeile(FreistadtSatz.KASSE_ABGEHOBEN, 1L,
                        Einsatz.von("betrag", "1.000 Mark")
                                .mit("rest", "1.500 Mark"),
                        "&#73bf961.000 Mark aus der Stadtkasse genommen.&#a5b4bf Es bleiben"
                                + " &#edf1f31.500 Mark&#a5b4bf."),
                new Zeile(FreistadtSatz.KASSE_ZU_WENIG, 1L,
                        Einsatz.von("stand", "500 Mark"),
                        "&#d9564dSo viel liegt nicht in der Kasse - darin sind &#edf1f3500 Mark"),
                new Zeile(FreistadtSatz.KASSE_NICHT_DEINE, 1L,
                        Einsatz.von("stadt", "Annastadt").mit("besitzer", "Anna"),
                        "&#d9564dDie Kasse von Annastadt gehoert &#edf1f3Anna"),
                // DAS STADTRECHT [Chat 27.11.2026]. Vier Zeilen, und drei Lagen
                // fehlen mit Absicht: keine Stadt, fremde Stadt und Stoerung teilen
                // ihren Satz mit der Kasse - ein zweiter Satz fuer dieselbe Lage
                // waere ein zweites Wort dafuer.
                new Zeile(FreistadtSatz.STADTRECHT_GEKAUFT, 1L,
                        Einsatz.von("preis", "25.000 Mark")
                                .mit("rest", "5.000 Mark"),
                        "&#73bf96Stadtrecht gekauft fuer 25.000 Mark.\n&#a5b4bfAb jetzt darf in"
                                + " dieser Stadt gehandelt werden. In der Kasse bleiben"
                                + " &#edf1f35.000 Mark&#a5b4bf."),
                new Zeile(FreistadtSatz.STADTRECHT_SCHON, 1L, Einsatz.keine(),
                        "&#a5b4bfDiese Stadt hat das Stadtrecht schon - hier darf"
                                + " gehandelt werden."),
                new Zeile(FreistadtSatz.STADTRECHT_NICHT_NOETIG, 1L, Einsatz.keine(),
                        "&#a5b4bfAuf diesem Server kostet das Stadtrecht nichts - in"
                                + " jeder Stadt darf gehandelt werden."),
                new Zeile(FreistadtSatz.STADTRECHT_ZU_WENIG, 1L,
                        Einsatz.von("preis", "25.000 Mark")
                                .mit("stand", "500 Mark"),
                        "&#d9564dDas Stadtrecht kostet 25.000 Mark - in der Stadtkasse"
                                + " sind &#edf1f3500 Mark&#d9564d.\n&#a5b4bfEinzahlen geht mit"
                                + " /freistadt kasse."),
                // --- Teil E2: der Regeltext --------------------------------------
                // Bilder ZEICHENGLEICH aus dem roten Lauf
                // abgeschrieben.
                new Zeile(FreistadtSatz.REGELN_KOPF, 1L,
                        Einsatz.von("stadt", "annastadt"),
                        "&#c6a15bDie Regeln von annastadt"),
                // DER SPIELERTEXT MIT EINEM TAG DARIN, und das ist die wichtigste
                // Zeile dieser Gruppe: Sie haelt zeichengleich fest, dass <red> aus
                // einer Spielereingabe TEXT bleibt. Wuerde der Wert in den Wortlaut
                // gespleisst statt eingesetzt, waere er wirklich rot - und ein
                // Stadtbesitzer koennte sich eine Servermeldung bauen.
                new Zeile(FreistadtSatz.REGELN_ZEILE, 1L,
                        Einsatz.von("text", "<red>Kein PvP"),
                        "&#a5b4bf  <red>Kein PvP"),
                new Zeile(FreistadtSatz.REGELN_LEER, 1L,
                        Einsatz.von("stadt", "annastadt"),
                        "&#a5b4bf  annastadt hat noch keine Regeln - der Besitzer setzt sie"
                                + " mit /freistadt regeln <text>"),
                new Zeile(FreistadtSatz.REGELN_GESETZT, 1L, Einsatz.keine(),
                        "&#73bf96Die Regeln stehen.&#a5b4bf Wer in deiner Stadt steht, liest"
                                + " sie mit /freistadt regeln."),
                new Zeile(FreistadtSatz.REGELN_GELOESCHT, 1L, Einsatz.keine(),
                        "&#73bf96Die Regeln sind weg.&#a5b4bf Ohne Regeln hat diese Stadt weder"
                                + " Teleporter noch Ladenrecht."),
                new Zeile(FreistadtSatz.REGELN_SCHON_SO, 1L, Einsatz.keine(),
                        "&#a5b4bfGenau das stand schon da."),
                new Zeile(FreistadtSatz.REGELN_WAR_LEER, 1L, Einsatz.keine(),
                        "&#a5b4bfDiese Stadt hatte gar keine Regeln."),
                new Zeile(FreistadtSatz.REGELN_ZU_LANG, 1L,
                        Einsatz.von("groesstens", "256").mit("hat", "300"),
                        "&#d9564dDie Regeln duerfen hoechstens 256 Zeichen haben - dein Text"
                                + " hat &#edf1f3300"),
                new Zeile(FreistadtSatz.REGELN_NICHT_DEINE, 1L,
                        Einsatz.von("stadt", "Annastadt").mit("besitzer", "Anna"),
                        "&#d9564dDie Regeln von Annastadt setzt &#edf1f3Anna"),
                new Zeile(FreistadtSatz.RECHT_UNBEKANNT, 1L,
                        Einsatz.von("wort", "fliegen"),
                        "&#d9564dfliegen ist kein Buergerrecht.\n&#a5b4bfDie Vorschlagsliste nennt,"
                                + " was moeglich ist."),

                // --- Die Meerinseln von Hafenwerder [Schritt 10] -----------------
                // Die fuenf Handbalken der Inselwache: Was ein Spieler liest, waehrend
                // er irgendwo in Hafenwerder baut.
                new Zeile(WacheSatz.SCHUTZ_INSEL_MEER, 1L, Einsatz.keine(), "&#e3c57eOffenes Meer - hier gehoert nichts niemandem, und gebaut wird hier"
                                + " nicht"),
                new Zeile(WacheSatz.SCHUTZ_INSEL_ANKUNFT, 1L, Einsatz.keine(), "&#e3c57eDie Ankunftsinsel gehoert allen - deine eigene nimmst du mit"
                                + " /werder hafen nehmen"),
                new Zeile(WacheSatz.SCHUTZ_INSEL_FREI, 1L, Einsatz.keine(), "&#e3c57eDiese Insel ist frei - /werder hafen nehmen"),
                new Zeile(WacheSatz.SCHUTZ_FREMDE_INSEL, 1L,
                        Einsatz.von("eigentuemer", "Anna"), "&#d9564dDiese Insel gehoert &#edf1f3Anna"),
                new Zeile(WacheSatz.SCHUTZ_INSEL_GESTOERT, 1L, Einsatz.keine(), "&#d9564dDie Inseln sind gerade nicht nachsehbar - versuch es noch einmal."),
                // Die Liste der eigenen Inseln. DIE NULL GEHOERT DAZU, obwohl sie im
                // Spiel nie erscheint - wer keine Insel hat, liest werder.leer. Die
                // Tafel haelt fest, welche Form dann herauskaeme.
                new Zeile(WerderSatz.KOPF, 0L, Einsatz.von("anzahl", "0"), "&#c6a15bDeine Meerinseln: 0"),
                new Zeile(WerderSatz.KOPF, 1L, Einsatz.von("anzahl", "1"), "&#c6a15bDeine Meerinsel"),
                new Zeile(WerderSatz.KOPF, 2L, Einsatz.von("anzahl", "3"), "&#c6a15bDeine Meerinseln: 3"),
                new Zeile(WerderSatz.ZEILE, 1L, Einsatz.von("feld", "2/-3"), "&#a5b4bf  Insel 2/-3"),
                new Zeile(WerderSatz.LEER, 1L, Einsatz.von("preis", "75.000 Mark"),
                        "&#a5b4bf  Du hast keine Meerinsel - eine kostet 75.000 Mark. Fahr nach"
                                + " Hafenwerder und nimm eine mit /werder hafen nehmen."),
                // Die fuenf Lagen als Chatzeile - dieselben drei, die auch eine Absage
                // begruenden.
                new Zeile(WerderSatz.HIER_MEER, 1L, Einsatz.keine(), "&#a5b4bfHier ist offenes Meer&#6e7a82 - es gehoert niemandem, und gebaut wird"
                                + " hier nicht."),
                new Zeile(WerderSatz.HIER_ANKUNFT, 1L, Einsatz.keine(), "&#a5b4bfDas ist die Ankunftsinsel&#6e7a82 - sie gehoert allen und bleibt frei."
                                + " Deine eigene nimmst du auf einer anderen."),
                new Zeile(WerderSatz.HIER_FREI, 1L,
                        Einsatz.von("feld", "2/-3").mit("preis", "75.000 Mark"), "&#e3c57eInsel 2/-3 ist frei.\n&#a5b4bfNimm sie mit /werder hafen nehmen - sie"
                                + " kostet 75.000 Mark."),
                new Zeile(WerderSatz.HIER_EIGENE, 1L, Einsatz.von("feld", "2/-3"),
                        "&#73bf96Insel 2/-3 gehoert dir."),
                new Zeile(WerderSatz.HIER_FREMD, 1L,
                        Einsatz.von("feld", "2/-3").mit("besitzer", "Anna"), "&#a5b4bfInsel 2/-3 gehoert &#edf1f3Anna&#a5b4bf."),
                // Genommen - mit Preis und ohne.
                new Zeile(WerderSatz.GENOMMEN, 1L,
                        Einsatz.von("feld", "2/-3").mit("preis", "75.000 Mark")
                                .mit("danach", "25.000 Mark"), "&#73bf96Insel 2/-3 gehoert jetzt dir - 75.000 Mark abgebucht."
                                + "\n&#a5b4bfEs bleiben 25.000 Mark."),
                new Zeile(WerderSatz.GENOMMEN_GRATIS, 1L, Einsatz.von("feld", "2/-3"),
                        "&#73bf96Insel 2/-3 gehoert jetzt dir."),
                // Die Absagen.
                new Zeile(WerderSatz.ABSAGE_AUSSERHALB, 1L, Einsatz.keine(), "&#d9564dHier enden die Inseln von Hafenwerder."),
                new Zeile(WerderSatz.ABSAGE_GENUG, 0L,
                        Einsatz.von("hoechstzahl", "0"), "&#d9564dDu hast schon 0 Meerinseln - mehr erlaubt dein Rang nicht."),
                new Zeile(WerderSatz.ABSAGE_GENUG, 1L,
                        Einsatz.von("hoechstzahl", "1"), "&#d9564dDu hast schon eine Meerinsel - mehr erlaubt dein Rang nicht."),
                new Zeile(WerderSatz.ABSAGE_GENUG, 2L,
                        Einsatz.von("hoechstzahl", "3"), "&#d9564dDu hast schon 3 Meerinseln - mehr erlaubt dein Rang nicht."),
                // Die Werbezeile unter der Absage an der Grenze [Teil H].
                new Zeile(WerderSatz.RANG_WERBUNG, 1L,
                        Einsatz.von("werbung", Wirtschaftswerte.VORGABE_RANG_WERBUNG),
                        "&#e3c57eMehr erlaubt ein hoeherer Rang."),
                new Zeile(WerderSatz.ABSAGE_ZU_WENIG, 1L,
                        Einsatz.von("preis", "75.000 Mark").mit("fehlt", "5.000 Mark"),
                        "&#d9564dEine Insel kostet 75.000 Mark - dir fehlen 5.000 Mark."),
                new Zeile(WerderSatz.ABSAGE_GESTOERT, 1L, Einsatz.keine(), "&#d9564dDie Inseln sind gerade nicht nachsehbar - versuch es noch einmal."),
                new Zeile(WerderSatz.ABSAGE_KEINE_WELT, 1L, Einsatz.keine(), "&#d9564dEs gibt keine Inselwelt&#a5b4bf - der Betreiber findet den Grund im"
                                + " Startprotokoll."),
                new Zeile(WerderSatz.ABSAGE_FALSCHE_WELT, 1L,
                        Einsatz.von("welt", "hafenwerder"), "&#a5b4bfMeerinseln gibt es nur in hafenwerder&#6e7a82 - hin kommst du durch den"
                                + " Hafen in Altfurt oder mit /warp hafenwerder."),
                // --- Wolkenwerder [Schritt 11] ---------------------------------
                // Die drei Handbalken der Wolkenwache. Die fremde Insel und das
                // Gestoert-Wort stehen oben bei Hafenwerder - sie gelten fuer BEIDE
                // Inselwelten.
                new Zeile(WacheSatz.SCHUTZ_WOLKE_LEERE, 1L, Einsatz.keine(), "&#e3c57eHier ist nichts - zwischen den Inseln baut niemand"),
                new Zeile(WacheSatz.SCHUTZ_WOLKE_ANKUNFT, 1L, Einsatz.keine(), "&#e3c57eDie Ankunftsplattform gehoert allen - deine eigene Insel nimmst du mit /wolke nehmen"),
                new Zeile(WacheSatz.SCHUTZ_WOLKE_FREI, 1L, Einsatz.keine(), "&#e3c57eDieses Feld ist frei - mit /wolke nehmen bekommst du eine Insel"),
                // Die Liste der eigenen Inseln. DIE NULL GEHOERT DAZU, obwohl sie im
                // Spiel nie erscheint - wer keine Insel hat, liest wolke.leer.
                new Zeile(WolkeSatz.KOPF, 0L, Einsatz.von("anzahl", "0"), "&#c6a15bDeine Wolkeninseln: 0"),
                new Zeile(WolkeSatz.KOPF, 1L, Einsatz.von("anzahl", "1"), "&#c6a15bDeine Wolkeninsel"),
                new Zeile(WolkeSatz.KOPF, 2L, Einsatz.von("anzahl", "3"), "&#c6a15bDeine Wolkeninseln: 3"),
                new Zeile(WolkeSatz.ZEILE, 1L, Einsatz.von("feld", "2/-3"), "&#a5b4bf  Insel 2/-3"),
                new Zeile(WolkeSatz.ZEILE_MITBAUER, 1L,
                        Einsatz.von("feld", "2/-3").mit("mitbauer", "Anna, Bert"), "&#a5b4bf  Insel 2/-3 · Mitbauer: &#edf1f3Anna, Bert"),
                new Zeile(WolkeSatz.LEER, 1L, Einsatz.keine(), "&#a5b4bf  Du hast keine Wolkeninsel - hol dir eine mit /wolke nehmen. Sie kostet nichts."),
                new Zeile(WolkeSatz.NOCH_FREI, 0L, Einsatz.von("hoechstzahl", "0"), "&#a5b4bf  Du darfst 0 Inseln halten - /wolke nehmen"),
                new Zeile(WolkeSatz.NOCH_FREI, 1L, Einsatz.von("hoechstzahl", "1"), "&#a5b4bf  Du darfst eine Insel halten - /wolke nehmen"),
                new Zeile(WolkeSatz.NOCH_FREI, 2L, Einsatz.von("hoechstzahl", "3"), "&#a5b4bf  Du darfst 3 Inseln halten - /wolke nehmen"),
                new Zeile(WolkeSatz.VOLL, 0L, Einsatz.von("hoechstzahl", "0"), "&#a5b4bf  Mehr als 0 Inseln erlaubt dein Rang nicht."),
                new Zeile(WolkeSatz.VOLL, 1L, Einsatz.von("hoechstzahl", "1"), "&#a5b4bf  Mehr als eine Insel erlaubt dein Rang nicht."),
                new Zeile(WolkeSatz.VOLL, 2L, Einsatz.von("hoechstzahl", "3"), "&#a5b4bf  Mehr als 3 Inseln erlaubt dein Rang nicht."),
                // Genommen - mit Preis und ohne. Der zweite ist der Normalfall.
                new Zeile(WolkeSatz.GENOMMEN, 1L,
                        Einsatz.von("feld", "2/-3").mit("preis", "5.000 Mark")
                                .mit("danach", "25.000 Mark"), "&#73bf96Insel 2/-3 gehoert jetzt dir - 5.000 Mark abgebucht.\n&#a5b4bfEs bleiben 25.000 Mark."),
                new Zeile(WolkeSatz.GENOMMEN_GRATIS, 1L, Einsatz.von("feld", "2/-3"), "&#73bf96Insel 2/-3 gehoert jetzt dir.\n&#a5b4bfIn der Truhe liegt, was du zum Anfangen brauchst - Lava und Eis machen den Steingenerator."),
                // Die Insel gehoert ihm, die Bloecke stehen nicht - diesen Satz gibt es
                // in keinem anderen Bereich.
                new Zeile(WolkeSatz.AUFBAU_GESTOERT, 1L, Einsatz.von("feld", "2/-3"), "&#d9564dInsel 2/-3 gehoert dir, aber sie liess sich nicht aufsetzen.\n&#a5b4bfSag es dem Betreiber - mit /wolke zuruecksetzen ist es zu beheben."),
                // Die Absagen.
                new Zeile(WolkeSatz.ABSAGE_GRENZE, 0L, Einsatz.von("hoechstzahl", "0"), "&#d9564dDu hast schon 0 Wolkeninseln - mehr erlaubt dein Rang nicht."),
                new Zeile(WolkeSatz.ABSAGE_GRENZE, 1L, Einsatz.von("hoechstzahl", "1"), "&#d9564dDu hast schon eine Wolkeninsel - mehr erlaubt dein Rang nicht."),
                new Zeile(WolkeSatz.ABSAGE_GRENZE, 2L, Einsatz.von("hoechstzahl", "3"), "&#d9564dDu hast schon 3 Wolkeninseln - mehr erlaubt dein Rang nicht."),
                // Die Werbezeile unter der Absage an der Grenze [Teil H].
                new Zeile(WolkeSatz.RANG_WERBUNG, 1L,
                        Einsatz.von("werbung", Wirtschaftswerte.VORGABE_RANG_WERBUNG),
                        "&#e3c57eMehr erlaubt ein hoeherer Rang."),
                new Zeile(WolkeSatz.ABSAGE_WELT_VOLL, 1L, Einsatz.keine(), "&#d9564dIn Wolkenwerder ist kein Feld mehr frei.\n&#a5b4bfSag es dem Betreiber - die Welt laesst sich vergroessern."),
                new Zeile(WolkeSatz.ABSAGE_ZU_WENIG, 1L,
                        Einsatz.von("preis", "5.000 Mark").mit("fehlt", "500 Mark"), "&#d9564dEine Wolkeninsel kostet 5.000 Mark - dir fehlen 500 Mark."),
                new Zeile(WolkeSatz.ABSAGE_GESTOERT, 1L, Einsatz.keine(), "&#d9564dDie Wolkeninseln sind gerade nicht nachsehbar - versuch es noch einmal."),
                new Zeile(WolkeSatz.ABSAGE_KEINE_WELT, 1L, Einsatz.keine(), "&#d9564dEs gibt keine Wolkenwelt&#a5b4bf - der Betreiber findet den Grund im Startprotokoll."),
                new Zeile(WolkeSatz.KEINE_INSEL, 1L, Einsatz.keine(), "&#a5b4bfDu hast keine Wolkeninsel&#6e7a82 - hol dir eine mit /wolke nehmen."),
                // Die Mitbauer.
                new Zeile(WolkeSatz.MITBAUER_DAZU, 1L, Einsatz.von("spieler", "Anna"), "&#73bf96Anna darf jetzt auf deiner Insel bauen."),
                new Zeile(WolkeSatz.MITBAUER_WEG, 1L, Einsatz.von("spieler", "Anna"), "&#e3c57eAnna darf nicht mehr auf deiner Insel bauen."),
                new Zeile(WolkeSatz.MITBAUER_SCHON, 1L, Einsatz.von("spieler", "Anna"), "&#a5b4bfAnna darf dort schon bauen."),
                new Zeile(WolkeSatz.MITBAUER_NICHT_DABEI, 1L, Einsatz.von("spieler", "Anna"), "&#a5b4bfAnna ist auf dieser Insel kein Mitbauer."),
                new Zeile(WolkeSatz.MITBAUER_UNBEKANNT, 1L, Einsatz.von("spieler", "Anna"), "&#d9564dDen Spieler Anna kennt dieser Server nicht."),
                new Zeile(WolkeSatz.MITBAUER_SELBST, 1L, Einsatz.keine(), "&#a5b4bfDich selbst musst du nicht eintragen - dir gehoert die Insel."),
                new Zeile(WolkeSatz.MITBAUER_FREMD, 1L, Einsatz.von("eigentuemer", "Anna"), "&#d9564dDiese Insel gehoert &#edf1f3Anna&#d9564d - Mitbauer setzt nur der Eigentuemer."),
                // Die Herausforderungen [Teil D]. Die Tafel, dann die Abgabe.
                new Zeile(WolkeSatz.AUFGABEN_KOPF, 1L,
                        Einsatz.von("erfuellt", "3").mit("gesamt", "10"), "&#c6a15bHerausforderungen auf dieser Insel: 3 von 10"),
                new Zeile(WolkeSatz.AUFGABEN_OFFEN, 1L,
                        Einsatz.von("name", "Erster Stein")
                                .mit("verlangt", "64x cobblestone")
                                .mit("belohnung", "50 Mark"), "&#a5b4bf  &#edf1f3Erster Stein&#a5b4bf · 64x cobblestone &#6e7a82· 50 Mark"),
                new Zeile(WolkeSatz.AUFGABEN_OFFEN_OHNE_LOHN, 1L,
                        Einsatz.von("name", "Erster Stein")
                                .mit("verlangt", "64x cobblestone"), "&#a5b4bf  &#edf1f3Erster Stein&#a5b4bf · 64x cobblestone"),
                new Zeile(WolkeSatz.AUFGABEN_FERTIG, 1L,
                        Einsatz.von("name", "Erster Stein"), "&#6e7a82  Erster Stein · erfuellt"),
                new Zeile(WolkeSatz.AUFGABEN_AUS, 1L, Einsatz.keine(), "&#a5b4bfIn Wolkenwerder gibt es keine Herausforderungen&#6e7a82 - der Betreiber schaltet sie in der config.yml ein."),
                new Zeile(WolkeSatz.ABGABE_ERTEILT, 1L,
                        Einsatz.von("name", "Erster Stein").mit("belohnung", "50 Mark")
                                .mit("stufe", "1"), "&#73bf96Erster Stein erfuellt - 50 Mark gutgeschrieben.\n&#a5b4bfDeine Insel ist jetzt auf Stufe 1 - der Steingenerator gibt mehr und besseres Erz."),
                new Zeile(WolkeSatz.ABGABE_ERTEILT_OHNE_LOHN, 1L,
                        Einsatz.von("name", "Erster Stein").mit("stufe", "1"), "&#73bf96Erster Stein erfuellt.\n&#a5b4bfDeine Insel ist jetzt auf Stufe 1 - der Steingenerator gibt mehr und besseres Erz."),
                new Zeile(WolkeSatz.ABGABE_FEHLT, 1L,
                        Einsatz.von("name", "Erster Stein")
                                .mit("fehlt", "24x cobblestone"), "&#d9564dFuer Erster Stein fehlt dir noch: &#edf1f324x cobblestone"),
                new Zeile(WolkeSatz.ABGABE_SCHON, 1L,
                        Einsatz.von("name", "Erster Stein"), "&#a5b4bfErster Stein ist auf dieser Insel schon erfuellt."),
                new Zeile(WolkeSatz.ABGABE_UNBEKANNT, 1L,
                        Einsatz.von("name", "erster-stien"), "&#d9564dDie Herausforderung erster-stien gibt es nicht&#a5b4bf - /wolke aufgaben zeigt alle."),
                new Zeile(WolkeSatz.ABGABE_FREMD, 1L,
                        Einsatz.von("eigentuemer", "Anna"), "&#d9564dDiese Insel gehoert &#edf1f3Anna&#d9564d - abgeben darf nur, wer dort bauen darf."),
                new Zeile(WolkeSatz.ABGABE_WEGGEKOMMEN, 1L,
                        Einsatz.von("name", "Erster Stein"), "&#d9564dBei Erster Stein ist unterwegs etwas abhandengekommen - nichts ist abgegeben.\n&#a5b4bfSieh in deinem Rucksack nach und versuch es noch einmal."),
                // Punkte und Rangliste [Teil E].
                new Zeile(WolkeSatz.PUNKTE_LAEUFT, 1L, Einsatz.von("chunks", "64"), "&#a5b4bfDeine Insel wird gezaehlt - 64 Chunks. Das Ergebnis kommt gleich."),
                new Zeile(WolkeSatz.PUNKTE_FERTIG, 1L,
                        Einsatz.von("punkte", "4711"), "&#c6a15bDeine Insel bringt 4711 Punkte.\n&#a5b4bfWie du stehst, zeigt /wolke rangliste."),
                new Zeile(WolkeSatz.PUNKTE_ZU_FRUEH, 1L,
                        Einsatz.von("sekunden", "90"), "&#e3c57eDiese Insel wurde gerade gezaehlt - in 90 Sekunden geht es wieder."),
                new Zeile(WolkeSatz.PUNKTE_LAEUFT_SCHON, 1L, Einsatz.keine(), "&#e3c57eAuf dieser Insel zaehlt gerade schon jemand - warte, bis es durch ist."),
                new Zeile(WolkeSatz.PUNKTE_AUS, 1L, Einsatz.keine(), "&#a5b4bfIn Wolkenwerder werden keine Punkte gezaehlt&#6e7a82 - der Betreiber fuellt dafuer wolken.punkte.tabelle."),
                new Zeile(WolkeSatz.RANGLISTE_KOPF, 1L, Einsatz.keine(), "&#c6a15bDie besten Wolkeninseln"),
                new Zeile(WolkeSatz.RANGLISTE_ZEILE, 1L,
                        Einsatz.von("rang", "1").mit("spieler", "Anna")
                                .mit("feld", "2/-3").mit("punkte", "4711")
                                .mit("stufe", "3"), "&#a5b4bf  1. &#edf1f3Anna&#a5b4bf · 4711 Punkte · Stufe 3 &#6e7a82· Insel 2/-3"),
                new Zeile(WolkeSatz.RANGLISTE_LEER, 1L, Einsatz.keine(), "&#a5b4bfIn Wolkenwerder hat noch niemand eine Insel - die erste kannst du mit /wolke nehmen haben."),
                // Biom und Zuruecksetzen [Teil F].
                new Zeile(WolkeSatz.BIOM_STAND, 1L,
                        Einsatz.von("biom", "plains").mit("preis", "2.000 Mark"), "&#a5b4bfDiese Insel liegt im Biom &#edf1f3plains&#a5b4bf · ein Wechsel kostet 2.000 Mark"),
                new Zeile(WolkeSatz.BIOM_WAHL, 1L,
                        Einsatz.von("biome", "plains, swamp, taiga"), "&#6e7a82  Zur Wahl: plains, swamp, taiga - /wolke biom <name>"),
                new Zeile(WolkeSatz.BIOM_GESETZT, 1L,
                        Einsatz.von("biom", "swamp").mit("preis", "2.000 Mark")
                                .mit("danach", "8.000 Mark"), "&#73bf96Das Biom deiner Insel ist jetzt swamp - 2.000 Mark abgebucht.\n&#a5b4bfEs bleiben 8.000 Mark. Sichtbar wird es, wenn du die Gegend neu betrittst."),
                new Zeile(WolkeSatz.BIOM_GESETZT_GRATIS, 1L,
                        Einsatz.von("biom", "swamp"), "&#73bf96Das Biom deiner Insel ist jetzt swamp.\n&#a5b4bfSichtbar wird es, wenn du die Gegend neu betrittst."),
                new Zeile(WolkeSatz.BIOM_NICHT_ANGEBOTEN, 1L,
                        Einsatz.von("biom", "nether")
                                .mit("biome", "plains, swamp, taiga"), "&#d9564dDas Biom nether gibt es hier nicht.\n&#a5b4bfZur Wahl: plains, swamp, taiga"),
                new Zeile(WolkeSatz.BIOM_KEINE_WAHL, 1L, Einsatz.keine(), "&#a5b4bfIn Wolkenwerder gibt es keine Biomwahl&#6e7a82 - der Betreiber fuellt dafuer wolken.biome."),
                new Zeile(WolkeSatz.BIOM_ZU_WENIG, 1L,
                        Einsatz.von("preis", "2.000 Mark").mit("fehlt", "500 Mark"),
                        "&#d9564dEin Biomwechsel kostet 2.000 Mark - dir fehlen 500 Mark."),
                new Zeile(WolkeSatz.BIOM_NICHT_DEINE, 1L,
                        Einsatz.von("eigentuemer", "Anna"), "&#d9564dDiese Insel gehoert &#edf1f3Anna&#d9564d - das Biom aendert nur der Eigentuemer."),
                new Zeile(WolkeSatz.ZURUECK_WARNUNG, 1L, Einsatz.keine(), "&#e3c57eDas setzt die Startinsel wieder hin - Wiese, Baum und eine neue Truhe.\n&#a5b4bfWas an ihrer Stelle steht, ist danach weg. Dein Fortschritt bleibt. Noch einmal mit /wolke zuruecksetzen bestaetigen"),
                new Zeile(WolkeSatz.ZURUECK_ERTEILT, 1L,
                        Einsatz.von("feld", "2/-3"), "&#73bf96Die Startinsel auf Feld 2/-3 steht wieder.\n&#a5b4bfIn der Truhe liegt, was du zum Anfangen brauchst."),
                // Die Rangleiter [Schritt 12 Teil D]. {rang} traegt den SCHLUESSEL aus
                // der config.yml - hier die der ausgelieferten Leiter, damit die Tafel
                // dasselbe Bild zeigt, das ein Spieler auf diesem Server liest.
                new Zeile(RangSatz.AUFGESTIEGEN, 1L,
                        Einsatz.von("rang", "siedler"), "&#73bf96Du bist jetzt siedler!"),
                new Zeile(RangSatz.NAECHSTER, 1L,
                        Einsatz.von("rang", "buerger"), "&#a5b4bfAls naechstes: buerger."),
                new Zeile(RangSatz.HOECHSTE, 1L, Einsatz.keine(),
                        "&#c6a15bDamit stehst du auf der hoechsten Stufe."),
                new Zeile(RangSatz.SCHAU_KOPF, 1L, Einsatz.keine(), "&#c6a15bDein Rang"),
                new Zeile(RangSatz.SCHAU_JETZT, 1L, Einsatz.von("rang", "siedler"),
                        "&#a5b4bfDu bist &#edf1f3siedler&#a5b4bf."),
                new Zeile(RangSatz.SCHAU_UNBEKANNT, 1L,
                        Einsatz.von("rang", "stifter").mit("gilt", "reisender"),
                        "&#d9564dEingetragen ist stifter - diese Stufe gibt es nicht mehr."
                                + " Es gilt reisender; bitte melde das dem Team."),
                new Zeile(RangSatz.SCHAU_NAECHSTE, 1L, Einsatz.von("rang", "buerger"),
                        "&#a5b4bfAls naechstes: &#edf1f3buerger&#a5b4bf. Dafuer zaehlt:"),
                new Zeile(RangSatz.SCHAU_STAND_ERFUELLT, 1L, Einsatz.von("stand", "12"),
                        "&#73bf96    du hast 12 - geschafft"),
                new Zeile(RangSatz.SCHAU_STAND_OFFEN, 1L, Einsatz.von("stand", "1"),
                        "&#a5b4bf    du hast 1"),
                new Zeile(RangSatz.SCHAU_FUSS, 1L, Einsatz.keine(),
                        "&#6e7a82/rang leiter zeigt alle Stufen."),
                new Zeile(RangSatz.LEITER_KOPF, 1L, Einsatz.keine(),
                        "&#c6a15bDie Rangleiter"),
                new Zeile(RangSatz.LEITER_ERREICHT, 1L,
                        Einsatz.von("rang", "reisender"),
                        "&#a5b4bf  reisender - erreicht"),
                new Zeile(RangSatz.LEITER_JETZT, 1L, Einsatz.von("rang", "siedler"),
                        "&#edf1f3  siedler - hier stehst du"),
                new Zeile(RangSatz.LEITER_OFFEN, 1L, Einsatz.von("rang", "buerger"),
                        "&#6e7a82  buerger - offen"),
                new Zeile(RangSatz.AUS, 1L, Einsatz.keine(),
                        "&#a5b4bfAuf diesem Server gibt es keine Raenge."),
                new Zeile(RangSatz.GESTOERT, 1L, Einsatz.keine(),
                        "&#d9564dDein Rang liess sich nicht lesen&#a5b4bf - versuche es noch"
                                + " einmal. Der Grund steht im Serverprotokoll."),
                new Zeile(RangSatz.KEINE_KONSOLE, 1L, Einsatz.keine(),
                        "&#a5b4bf/rang zeigt den Rang eines Spielers - auf der Konsole gibt"
                                + " es keinen."),



                // DIE REGELN UND DIE BEFEHLSLISTE [Schritt 13 Teil B]. Der
                // Regeltext ist der erste MEHRZEILIGE Satz des Projekts: Fuenf
                // Zeilen in einem Wert, getrennt durch die Umbruchmarke. Diese
                // Tafel haelt ihn als EIN Bild mit vier Umbruechen - genau das,
                // was der Spieler im Chat und als Bloecke im Fenster sieht.

                new Zeile(RegelnSatz.KOPF, 1L, Einsatz.keine(),
                        "&#c6a15bDie Regeln des Freimarkenbundes"),
                new Zeile(RegelnSatz.TEXT, 1L, Einsatz.keine(),
                        "&#a5b4bf- Baue nur auf eigenem Grund. Eine Parzelle gehoert dem, der sie "
                        + "genommen hat.\n&#a5b4bf- Nimm nichts, was dir nicht gehoert. Diebstahl "
                        + "aus Kisten und Laeden wird geahndet.\n&#a5b4bf- Kein Angriff auf andere "
                        + "Spieler in den geschuetzten Welten.\n&#a5b4bf- Keine Hilfsmittel, die dir "
                        + "Vorteile verschaffen - kein Durchblick durch Stein, kein Fliegen, keine "
                        + "Makros.\n&#a5b4bf- Sei anstaendig im Chat. Beleidigung und fremde Werbung "
                        + "haben hier keinen Platz."),
                new Zeile(RegelnSatz.FUSS, 1L, Einsatz.keine(),
                        "&#6e7a82Wer dagegen handelt, verliert Rang, Grund oder den Zugang."),
                new Zeile(RegelnSatz.KNOPF, 1L, Einsatz.keine(),
                        "&#edf1f3Gelesen"),
                // DIE ANMELDUNG [Schritt 13 Teil C, 21.11.2026]. Der Regeltext steht
                // oben und nicht hier - das Formular zeigt jenen, und ein zweiter waere
                // derselbe Text an zwei Orten.
                new Zeile(AnmeldeSatz.KOPF, 1L, Einsatz.keine(),
                        "&#c6a15bAufnahme in den Freimarkenbund"),
                new Zeile(AnmeldeSatz.FRAGE, 1L, Einsatz.keine(),
                        "&#e3c57eNimmst du diese Regeln an? Mit der Annahme wirst du "
                        + "Buerger des Bundes."),
                new Zeile(AnmeldeSatz.ANNEHMEN, 1L, Einsatz.keine(),
                        "&#73bf96Ich nehme an"),
                new Zeile(AnmeldeSatz.SPAETER, 1L, Einsatz.keine(),
                        "&#6e7a82Spaeter"),
                new Zeile(AnmeldeSatz.ANGENOMMEN, 1L, Einsatz.keine(),
                        "&#73bf96Angenommen - willkommen im Freimarkenbund!"
                        + "\n&#a5b4bfMit /parzelle nehmen bekommst du deine erste "
                        + "Parzelle, und sie ist gratis."),
                new Zeile(AnmeldeSatz.SCHON_ANGENOMMEN, 1L, Einsatz.keine(),
                        "&#a5b4bfDu hast die Regeln schon angenommen."),
                new Zeile(AnmeldeSatz.HINWEIS, 1L, Einsatz.keine(),
                        "&#e3c57eTippe /regeln annehmen, um die Regeln anzunehmen."),
                new Zeile(AnmeldeSatz.NUR_SPIELER, 1L, Einsatz.keine(),
                        "&#a5b4bfAnnehmen kann nur ein Spieler - die Konsole stimmt fuer "
                        + "niemanden zu."),
                new Zeile(AnmeldeSatz.NICHT_VERLANGT, 1L, Einsatz.keine(),
                        "&#a5b4bfAuf diesem Server ist keine Anmeldung noetig."),
                // Die Absage am BUNDstand - sie steht bei den Handelssaetzen in der
                // Datei und darum hier bei den uebrigen Bescheiden.
                new Zeile(ShopSatz.SHOP_NICHT_ANGEMELDET, 1L, Einsatz.keine(),
                        "&#d9564dErst annehmen: /regeln"),
                // DER RUNDGANG [Schritt 13 Teil D]. Die Saetze der einzelnen Stationen
                // stehen hier nicht - welche es gibt, bestimmt der Betreiber, und ab
                // Werk gibt es keine.
                new Zeile(RundgangSatz.STATION, 1L,
                        Einsatz.von("station", "Das Zunfthaus").mit("gesehen", 2L)
                                .mit("alle", 6L),
                        "&#c6a15bDas Zunfthaus&#6e7a82 - Station 2 von 6"),
                new Zeile(RundgangSatz.ABGESCHLOSSEN, 1L, Einsatz.keine(),
                        "&#73bf96Rundgang geschafft - du kennst Altfurt!"
                        + "\n&#a5b4bfJetzt fehlt nur noch deine erste Parzelle:"
                        + " /parzelle nehmen, und sie ist gratis."),
                // SCHRITT 14: die Lotterie in Altfurt [22.11.2026]. Die Zahlen sind
                // klein und VERSCHIEDEN voneinander: Bei zweimal derselben Zahl waere
                // ein vertauschter Platzhalter nicht zu sehen.
                new Zeile(LotterieSatz.KOPF, 1L, Einsatz.keine(),
                        "&#c6a15bDie Lotterie von Altfurt"),
                new Zeile(LotterieSatz.STAND_GRATIS, 1L,
                        Einsatz.von("gezogen", 0L).mit("hoechste", 20L),
                        "&#73bf96Dein naechstes Los ist gratis. &#6e7a82Heute gezogen: 0 von 20."),
                new Zeile(LotterieSatz.STAND_PREIS, 1L,
                        Einsatz.von("preis", "500 Mark").mit("gezogen", 1L)
                                .mit("hoechste", 20L),
                        "&#a5b4bfDein naechstes Los kostet &#c6a15b500 Mark&#a5b4bf. &#6e7a82Heute gezogen: 1 von 20."),
                new Zeile(LotterieSatz.STAND_GENUG, 1L,
                        Einsatz.von("hoechste", 20L),
                        "&#e3c57eDu hast heute alle 20 Lose gezogen - morgen ist wieder eines gratis."),
                new Zeile(LotterieSatz.STAND_STAFFEL, 1L,
                        Einsatz.von("staffel", "1. gratis - 2. 500 - 3. 1000"),
                        "&#6e7a82Die Staffel in Mark: 1. gratis - 2. 500 - 3. 1000"),
                new Zeile(LotterieSatz.STAND_HINWEIS, 1L, Einsatz.keine(),
                        "&#6e7a82Ziehen mit /lotterie ziehen"),
                new Zeile(LotterieSatz.GEZOGEN_MARK, 1L,
                        Einsatz.von("gewinn", "Handvoll Mark")
                                .mit("menge", "200 Mark")
                                .mit("preis", "500 Mark"),
                        "&#73bf96Handvoll Mark: &#c6a15b200 Mark&#73bf96 &#6e7a82(Los: 500 Mark)"),
                new Zeile(LotterieSatz.GEZOGEN_GOLDMARK, 0L,
                        Einsatz.von("gewinn", "Goldmark").mit("menge", "0 Goldmark")
                                .mit("preis", "500 Mark"),
                        "&#73bf96Goldmark: &#c6a15b0 Goldmark&#73bf96 &#6e7a82(Los: 500 Mark)"),
                new Zeile(LotterieSatz.GEZOGEN_GOLDMARK, 1L,
                        Einsatz.von("gewinn", "Goldmark").mit("menge", "1 Goldmark")
                                .mit("preis", "500 Mark"),
                        "&#73bf96Goldmark: &#c6a15b1 Goldmark&#73bf96 &#6e7a82(Los: 500 Mark)"),
                new Zeile(LotterieSatz.GEZOGEN_GOLDMARK, 2L,
                        Einsatz.von("gewinn", "Goldmark").mit("menge", "2 Goldmark")
                                .mit("preis", "500 Mark"),
                        "&#73bf96Goldmark: &#c6a15b2 Goldmark&#73bf96 &#6e7a82(Los: 500 Mark)"),
                new Zeile(LotterieSatz.GEZOGEN_GEGENSTAND, 0L,
                        Einsatz.von("gewinn", "Diamant").mit("menge", 0L)
                                .mit("preis", "500 Mark"),
                        "&#73bf96Gewonnen: 0 x Diamant &#6e7a82(Los: 500 Mark)"),
                new Zeile(LotterieSatz.GEZOGEN_GEGENSTAND, 1L,
                        Einsatz.von("gewinn", "Fischerhut von Altfurt").mit("menge", 1L)
                                .mit("preis", "gratis"),
                        "&#73bf96Gewonnen: Fischerhut von Altfurt &#6e7a82(Los: gratis)"),
                new Zeile(LotterieSatz.GEZOGEN_GEGENSTAND, 2L,
                        Einsatz.von("gewinn", "Diamant").mit("menge", 2L)
                                .mit("preis", "500 Mark"),
                        "&#73bf96Gewonnen: 2 x Diamant &#6e7a82(Los: 500 Mark)"),
                new Zeile(LotterieSatz.ZU_WENIG, 1L,
                        Einsatz.von("preis", "1.000 Mark")
                                .mit("guthaben", "300 Mark"),
                        "&#d9564dDas naechste Los kostet 1.000 Mark - du hast 300 Mark."),
                new Zeile(LotterieSatz.HEUTE_GENUG, 1L,
                        Einsatz.von("hoechste", 20L),
                        "&#e3c57eDu hast heute alle 20 Lose gezogen - morgen ist wieder eines gratis."),
                new Zeile(LotterieSatz.KEIN_PLATZ, 0L, Einsatz.von("fehlt", 0L),
                        "&#d9564dIn deinem Rucksack fehlt Platz fuer 0 Stueck - dein Los ist nicht verbraucht."),
                new Zeile(LotterieSatz.KEIN_PLATZ, 1L, Einsatz.von("fehlt", 1L),
                        "&#d9564dIn deinem Rucksack fehlt Platz fuer 1 Stueck - dein Los ist nicht verbraucht."),
                new Zeile(LotterieSatz.KEIN_PLATZ, 2L, Einsatz.von("fehlt", 4L),
                        "&#d9564dIn deinem Rucksack fehlt Platz fuer 4 Stueck - dein Los ist nicht verbraucht."),
                new Zeile(LotterieSatz.ERSTATTET, 1L,
                        Einsatz.von("preis", "500 Mark"),
                        "&#d9564dDer Gewinn kam nicht an - deine 500 Mark sind zurueck."),
                new Zeile(LotterieSatz.GESTOERT, 1L, Einsatz.keine(),
                        "&#d9564dDie Lotterie antwortet gerade nicht - es ist nichts passiert."),
                new Zeile(LotterieSatz.AUS, 1L, Einsatz.keine(),
                        "&#e3c57eIn Altfurt wird gerade nicht gezogen."),
                new Zeile(LotterieSatz.NUR_SPIELER, 1L, Einsatz.keine(),
                        "&#d9564dEin Los zieht ein Spieler. Von der Konsole geht das nicht."),
                // SCHRITT 14: der Angelwettbewerb [22.11.2026]. Die Geldwerte sind
                // TEXTE mit ihrer Einheit - genau so setzt der Befehl sie ein, naemlich
                // ueber sprache.betrag. Eine nackte Zahl hier waere eine Tafel, die ein
                // Bild festhaelt, das im Spiel nie entsteht.
                new Zeile(WettbewerbSatz.KOPF, 1L, Einsatz.keine(),
                        "&#c6a15bDer Angelwettbewerb von Altfurt"),
                new Zeile(WettbewerbSatz.OFFEN, 1L,
                        Einsatz.von("bis", "19:00").mit("topf", "2.000 Mark")
                                .mit("teilnehmer", 8L),
                        "&#73bf96Der Wettbewerb laeuft bis 19:00. &#a5b4bfIm Topf: &#c6a15b2.000 Mark&#a5b4bf von 8 Teilnehmern."),
                new Zeile(WettbewerbSatz.GESCHLOSSEN, 1L,
                        Einsatz.von("beginn", "18:00"),
                        "&#e3c57eGerade laeuft kein Wettbewerb. Der naechste beginnt um 18:00."),
                new Zeile(WettbewerbSatz.EIGENER_STAND, 0L,
                        Einsatz.von("punkte", 0L).mit("platz", 8L),
                        "&#a5b4bfDu hast 0 Punkte und liegst auf Platz 8."),
                new Zeile(WettbewerbSatz.EIGENER_STAND, 1L,
                        Einsatz.von("punkte", 1L).mit("platz", 7L),
                        "&#a5b4bfDu hast 1 Punkt und liegst auf Platz 7."),
                new Zeile(WettbewerbSatz.EIGENER_STAND, 2L,
                        Einsatz.von("punkte", 42L).mit("platz", 3L),
                        "&#a5b4bfDu hast 42 Punkte und liegst auf Platz 3."),
                new Zeile(WettbewerbSatz.MITMACHEN, 1L,
                        Einsatz.von("startgeld", "250 Mark"),
                        "&#6e7a82Mitmachen mit /wettbewerb anmelden (250 Mark)"),
                new Zeile(WettbewerbSatz.RANGLISTE_KOPF, 1L, Einsatz.keine(),
                        "&#edf1f3Die Rangliste"),
                new Zeile(WettbewerbSatz.PLATZ, 1L,
                        Einsatz.von("platz", 1L).mit("name", "Anna")
                                .mit("punkte", 120L),
                        "&#a5b4bf  1. Anna - 120"),
                new Zeile(WettbewerbSatz.RANGLISTE_LEER, 1L, Einsatz.keine(),
                        "&#6e7a82  Noch ist niemand dabei."),
                new Zeile(WettbewerbSatz.ANGEMELDET, 1L,
                        Einsatz.von("startgeld", "250 Mark")
                                .mit("topf", "1.250 Mark"),
                        "&#73bf96Du bist dabei - 250 Mark gezahlt. &#a5b4bfIm Topf: &#c6a15b1.250 Mark"),
                new Zeile(WettbewerbSatz.SCHON_DABEI, 1L, Einsatz.keine(),
                        "&#e3c57eDu bist schon angemeldet."),
                new Zeile(WettbewerbSatz.KEIN_LAUF, 1L,
                        Einsatz.von("beginn", "18:00 (Freitag/Samstag)"),
                        "&#e3c57eGerade laeuft kein Wettbewerb. Der naechste beginnt um 18:00 (Freitag/Samstag)."),
                new Zeile(WettbewerbSatz.ZU_WENIG, 1L,
                        Einsatz.von("startgeld", "250 Mark")
                                .mit("guthaben", "100 Mark"),
                        "&#d9564dDas Startgeld ist 250 Mark - du hast 100 Mark."),
                new Zeile(WettbewerbSatz.AUS, 1L, Einsatz.keine(),
                        "&#e3c57eIn Altfurt wird gerade nicht gewertet."),
                new Zeile(WettbewerbSatz.GESTOERT, 1L, Einsatz.keine(),
                        "&#d9564dDer Wettbewerb antwortet gerade nicht - es ist nichts passiert."),
                new Zeile(WettbewerbSatz.NUR_SPIELER, 1L, Einsatz.keine(),
                        "&#d9564dAnmelden kann ein Spieler. Von der Konsole geht das nicht."),
                new Zeile(WettbewerbSatz.ANSAGE_BEGINN, 1L,
                        Einsatz.von("bis", "19:00").mit("startgeld", "250 Mark"),
                        "&#c6a15bDer Angelwettbewerb hat begonnen! &#a5b4bfBis 19:00, Startgeld 250 Mark - /wettbewerb anmelden"),
                new Zeile(WettbewerbSatz.ANSAGE_ENDE, 1L,
                        Einsatz.von("teilnehmer", 8L).mit("topf", "2.000 Mark"),
                        "&#c6a15bDer Angelwettbewerb ist zu Ende. &#a5b4bf8 Teilnehmer, &#c6a15b2.000 Mark&#a5b4bf im Topf:"),
                new Zeile(WettbewerbSatz.ANSAGE_SIEGER, 1L,
                        Einsatz.von("platz", 1L).mit("name", "Anna")
                                .mit("punkte", 120L).mit("preis", "1.000 Mark"),
                        "&#a5b4bf  1. Anna - 120 Punkte, &#c6a15b1.000 Mark"),
                new Zeile(WettbewerbSatz.ANSAGE_NIEMAND, 1L, Einsatz.keine(),
                        "&#6e7a82Niemand war dabei."),
                new Zeile(WettbewerbSatz.GEWONNEN, 1L,
                        Einsatz.von("platz", 1L).mit("punkte", 120L)
                                .mit("preis", "1.000 Mark"),
                        "&#73bf96Platz 1 im Angelwettbewerb mit 120 Punkten: &#c6a15b1.000 Mark"),
                new Zeile(HilfeSatz.GRUPPE_SPIELE, 1L, Einsatz.keine(),
                        "&#edf1f3Kleine Spiele in Altfurt"),
                new Zeile(HilfeSatz.KOPF, 1L, Einsatz.keine(),
                        "&#c6a15bAlle Befehle des Freimarkenbundes"),
                new Zeile(HilfeSatz.GRUPPE_GRUND, 1L, Einsatz.keine(),
                        "&#edf1f3Grund und Bauen"),
                new Zeile(HilfeSatz.GRUPPE_GELD, 1L, Einsatz.keine(),
                        "&#edf1f3Geld und Handel"),
                new Zeile(HilfeSatz.GRUPPE_SELBST, 1L, Einsatz.keine(),
                        "&#edf1f3Rang und Beruf"),
                new Zeile(HilfeSatz.GRUPPE_REISEN, 1L, Einsatz.keine(),
                        "&#edf1f3Reisen und Treffen"),
                new Zeile(HilfeSatz.GRUPPE_WELTEN, 1L, Einsatz.keine(),
                        "&#edf1f3Eigene Welten"),
                new Zeile(HilfeSatz.GRUPPE_BUND, 1L, Einsatz.keine(),
                        "&#edf1f3Der Bund"),
                new Zeile(HilfeSatz.ZEILE, 1L, Einsatz.von("befehl", "parzelle")
                        .mit("beschreibung", "Parzellen nehmen, ansehen und verwalten"),
                        "&#e3c57e/parzelle&#6e7a82 - &#a5b4bfParzellen nehmen, ansehen und verwalten"),
                new Zeile(HilfeSatz.FUSS, 1L, Einsatz.keine(),
                        "&#6e7a82Jeder Befehl ohne Zusatz zeigt, was er kann."),

                // DAS HAUPTMENUE [Schritt 13 Teil B]. Jeder dieser Saetze geht
                // in einen GEGENSTAND und nicht in den Chat - der Anzeigename
                // und die Zeile darunter. Das Bild ist trotzdem dasselbe: Es
                // ist derselbe Component, nur an einer anderen Stelle des
                // Fensters. Paper uebersetzt Gegenstandsnamen nicht (Plan
                // 11.3), darum loest Menuehand jeden hier auf.

                new Zeile(MenueSatz.TITEL, 1L, Einsatz.keine(),
                        "&#c6a15bDer Freimarkenbund"),
                new Zeile(MenueSatz.KLICK, 1L, Einsatz.von("befehl", "parzelle"),
                        "&#6e7a82Klick: /parzelle"),
                new Zeile(MenueSatz.KEINE_KONSOLE, 1L, Einsatz.keine(),
                        "&#a5b4bf/menu zeigt ein Menue - auf der Konsole gibt es keines."),
                new Zeile(MenueSatz.GESTOERT, 1L, Einsatz.keine(),
                        "&#d9564dDas Menue liess sich nicht oeffnen&#a5b4bf - der Grund steht im "
                        + "Serverprotokoll."),
                new Zeile(MenueSatz.PARZELLE_NAME, 1L, Einsatz.keine(),
                        "&#c6a15bDeine Parzellen"),
                new Zeile(MenueSatz.PARZELLE_ERKLAERUNG, 1L, Einsatz.keine(),
                        "&#a5b4bfGrund nehmen, ansehen, Mitbauer eintragen"),
                new Zeile(MenueSatz.MARK_NAME, 1L, Einsatz.keine(),
                        "&#c6a15bDein Guthaben"),
                new Zeile(MenueSatz.MARK_ERKLAERUNG, 1L, Einsatz.keine(),
                        "&#a5b4bfKontostand, Verlauf, Goldmark tauschen"),
                new Zeile(MenueSatz.RANG_NAME, 1L, Einsatz.keine(),
                        "&#c6a15bDein Rang"),
                new Zeile(MenueSatz.RANG_ERKLAERUNG, 1L, Einsatz.keine(),
                        "&#a5b4bfWelche Stufe du hast - und was zur naechsten fehlt"),
                new Zeile(MenueSatz.BERUF_NAME, 1L, Einsatz.keine(),
                        "&#c6a15bDein Beruf"),
                new Zeile(MenueSatz.BERUF_ERKLAERUNG, 1L, Einsatz.keine(),
                        "&#a5b4bfBeruf waehlen, ansehen, aufgeben"),
                new Zeile(MenueSatz.MARKT_NAME, 1L, Einsatz.keine(),
                        "&#c6a15bDer Bundesmarkt"),
                new Zeile(MenueSatz.MARKT_ERKLAERUNG, 1L, Einsatz.keine(),
                        "&#a5b4bfWas der Bund ankauft, und zu welchem Preis"),
                new Zeile(MenueSatz.LADEN_NAME, 1L, Einsatz.keine(),
                        "&#c6a15bDie Markthalle"),
                new Zeile(MenueSatz.LADEN_ERKLAERUNG, 1L, Einsatz.keine(),
                        "&#a5b4bfEinen Laden mieten, verlaengern, kuendigen"),
                new Zeile(MenueSatz.FREISTADT_NAME, 1L, Einsatz.keine(),
                        "&#c6a15bDie Freistaedte"),
                new Zeile(MenueSatz.FREISTADT_ERKLAERUNG, 1L, Einsatz.keine(),
                        "&#a5b4bfStaedte ansehen, gruenden, Grundstuecke kaufen"),
                new Zeile(MenueSatz.WERDER_NAME, 1L, Einsatz.keine(),
                        "&#c6a15bHafenwerder"),
                new Zeile(MenueSatz.WERDER_ERKLAERUNG, 1L, Einsatz.keine(),
                        "&#a5b4bfEine Meerinsel nehmen und sichern"),
                new Zeile(MenueSatz.WOLKE_NAME, 1L, Einsatz.keine(),
                        "&#c6a15bWolkenwerder"),
                new Zeile(MenueSatz.WOLKE_ERKLAERUNG, 1L, Einsatz.keine(),
                        "&#a5b4bfDeine Wolkeninsel, ihr Biom und die Herausforderungen"),
                new Zeile(MenueSatz.HILFE_NAME, 1L, Einsatz.keine(),
                        "&#c6a15bAlle Befehle"),
                new Zeile(MenueSatz.HILFE_ERKLAERUNG, 1L, Einsatz.keine(),
                        "&#a5b4bfDie ganze Liste, nach Bereichen geordnet"),
                new Zeile(MenueSatz.REGELN_NAME, 1L, Einsatz.keine(),
                        "&#c6a15bDie Regeln"),
                new Zeile(MenueSatz.REGELN_ERKLAERUNG, 1L, Einsatz.keine(),
                        "&#a5b4bfWas auf diesem Server gilt"),

                // DIE WECHSELSTUBE [Schritt 12 Teil F]. Die Betraege kommen ueber
                // sprache.betrag und sprache.goldmark aus der Datei - und das ist hier
                // mehr als Bequemlichkeit: Die zwei Waehrungen sind im Programm beide
                // ein long, und eine verwechselte Zeile zeigte "12 Mark", wo einer 12
                // Goldmark hat. Steht in einem Bild unten die falsche Einheit, ist diese
                // Probe rot.
                new Zeile(MarkSatz.TAUSCH_KOPF, 1L, Einsatz.keine(), "&#c6a15bWechselstube"),
                new Zeile(MarkSatz.TAUSCH_STAND, 1L,
                        Einsatz.von("betrag", sprache.goldmark(12L)), "&#a5b4bfDu hast &#c6a15b12 Goldmark&#a5b4bf."),
                new Zeile(MarkSatz.TAUSCH_KURSE, 1L,
                        Einsatz.von("kauf", sprache.betrag(1_000L))
                                .mit("verkauf", sprache.betrag(800L)), "&#a5b4bfEine Goldmark kostet &#c6a15b1.000 Mark&#a5b4bf und bringt &#c6a15b800 Mark&#a5b4bf zurueck."),
                new Zeile(MarkSatz.TAUSCH_FREI, 1L,
                        Einsatz.von("anzahl", sprache.goldmark(12L))
                                .mit("betrag", sprache.betrag(10_000L)), "&#a5b4bfHeute kannst du noch &#c6a15b12 Goldmark&#a5b4bf zurueckwechseln (10.000 Mark)."),
                new Zeile(MarkSatz.TAUSCH_ZU, 1L, Einsatz.keine(), "&#a5b4bfDer Rueckweg in Mark ist geschlossen."),
                new Zeile(MarkSatz.TAUSCH_HILFE, 1L, Einsatz.keine(), "&#6e7a82/mark tauschen kaufen 5 oder /mark tauschen verkaufen 5"),
                new Zeile(MarkSatz.TAUSCH_GEKAUFT, 1L,
                        Einsatz.von("goldmark", sprache.goldmark(3L))
                                .mit("mark", sprache.betrag(3_000L))
                                .mit("stand", sprache.goldmark(3L)), "&#73bf963 Goldmark fuer 3.000 Mark gekauft. Du hast jetzt 3 Goldmark."),
                new Zeile(MarkSatz.TAUSCH_VERKAUFT, 1L,
                        Einsatz.von("goldmark", sprache.goldmark(5L))
                                .mit("mark", sprache.betrag(4_000L))
                                .mit("stand", sprache.goldmark(0L)), "&#73bf965 Goldmark fuer 4.000 Mark zurueckgewechselt. Du hast jetzt 0 Goldmark."),
                new Zeile(MarkSatz.TAUSCH_ZU_WENIG_MARK, 1L,
                        Einsatz.von("preis", sprache.betrag(3_000L))
                                .mit("fehlt", sprache.betrag(1L)), "&#d9564dDas kostet 3.000 Mark - dir fehlen 1 Mark."),
                new Zeile(MarkSatz.TAUSCH_ZU_WENIG_GOLDMARK, 1L,
                        Einsatz.von("anzahl", sprache.goldmark(3L))
                                .mit("stand", sprache.goldmark(2L)), "&#d9564d3 Goldmark hast du nicht - du hast 2 Goldmark."),
                new Zeile(MarkSatz.TAUSCH_DECKEL_ERREICHT, 1L,
                        Einsatz.von("anzahl", sprache.goldmark(12L))
                                .mit("betrag", sprache.betrag(9_600L)), "&#d9564dHeute gehen nur noch 12 Goldmark (9.600 Mark) - der Wechseldeckel."),
                new Zeile(MarkSatz.TAUSCH_DECKEL_VOLL, 1L,
                        Einsatz.von("betrag", sprache.betrag(10_000L)), "&#d9564dHeute ist der Wechseldeckel von 10.000 Mark erreicht. Morgen wieder."),
                new Zeile(MarkSatz.TAUSCH_GESTOERT, 1L, Einsatz.keine(), "&#d9564dDie Kasse hat nicht geantwortet. Es ist nichts passiert."),

                // DAS MUENZHAUS [Schritt 12 Teil G]. Dieselbe Strenge wie bei der
                // Wechselstube darueber, und aus demselben Grund: Die Preise sind
                // GOLDMARK, die Kurse sind MARK, und beides ist im Programm ein long.
                // Steht in einem Bild unten die falsche Einheit, ist diese Probe rot.
                //
                // Die NAMEN der Posten kommen ueber Muenzname aus der Datei - sie sind
                // der dritte Satz, der keinen Platz in einer Aufzaehlung hat, und damit
                // haengt auch die offene Familie an einer Bildprobe.
                //
                // Die GEZAEHLTEN Saetze stehen dreimal: mit 1, mit 2 und mit 0. Die Null
                // ist dabei kein Zierrat - sie waehlt die viele-Zeile, und ein Satz, der
                // nur mit 1 geprueft waere, koennte in seiner zweiten Zeile alles sagen.,
                new Zeile(MarkSatz.MUENZHAUS_KOPF, 1L, Einsatz.keine(),
                        "&#c6a15bMuenzhaus"),
                new Zeile(MarkSatz.MUENZHAUS_AUS, 1L, Einsatz.keine(),
                        "&#a5b4bfIm Muenzhaus wird gerade nichts verkauft."),
                new Zeile(MarkSatz.MUENZHAUS_LEER, 1L, Einsatz.keine(),
                        "&#a5b4bfDer Katalog ist leer."),
                new Zeile(MarkSatz.MUENZHAUS_ZEILE, 1L,
                        Einsatz.von("name", sprache.wort(new Muenzname("kuerbishut"), Einsatz.keine()))
                                .mit("preis", sprache.goldmark(2L))
                                .mit("inhalt", "1x carved_pumpkin"), "&#a5b4bf- &#edf1f3Kuerbishut&#a5b4bf fuer &#c6a15b2 Goldmark&#a5b4bf: 1x carved_pumpkin"),
                new Zeile(MarkSatz.MUENZHAUS_ZEILE_ZU_TEUER, 1L,
                        Einsatz.von("name", sprache.wort(new Muenzname("kuerbishut"), Einsatz.keine()))
                                .mit("preis", sprache.goldmark(2L))
                                .mit("inhalt", "1x carved_pumpkin"), "&#6e7a82- Kuerbishut fuer 2 Goldmark: 1x carved_pumpkin"),
                new Zeile(MarkSatz.MUENZHAUS_ZEILE_GESPERRT, 1L,
                        Einsatz.von("name", sprache.wort(new Muenzname("blumenkiste"), Einsatz.keine()))
                                .mit("preis", sprache.goldmark(2L))
                                .mit("inhalt", "16x rose_bush, 16x peony"), "&#6e7a82- Blumenkiste fuer 2 Goldmark: 16x rose_bush, 16x peony (noch nicht frei)"),
                new Zeile(MarkSatz.MUENZHAUS_HILFE, 1L, Einsatz.keine(),
                        "&#6e7a82/mark muenzhaus kaufen [posten] - Verlauf: /mark muenzhaus verlauf"),
                new Zeile(MarkSatz.MUENZHAUS_FRAGE, 1L,
                        Einsatz.von("name", sprache.wort(new Muenzname("kuerbishut"), Einsatz.keine()))
                                .mit("posten", "kuerbishut")
                                .mit("preis", sprache.goldmark(2L))
                                .mit("inhalt", "1x carved_pumpkin")
                                .mit("danach", sprache.goldmark(8L)), "&#e3c57eKuerbishut kostet 2 Goldmark und bringt 1x carved_pumpkin. Danach haettest du 8 Goldmark.\n&#a5b4bfZum Kaufen: /mark muenzhaus kaufen kuerbishut bestaetigen"),
                new Zeile(MarkSatz.MUENZHAUS_GEKAUFT, 1L,
                        Einsatz.von("name", sprache.wort(new Muenzname("kuerbishut"), Einsatz.keine()))
                                .mit("inhalt", "1x carved_pumpkin")
                                .mit("preis", sprache.goldmark(2L))
                                .mit("stand", sprache.goldmark(8L)), "&#73bf96Kuerbishut gekauft fuer 2 Goldmark: 1x carved_pumpkin. Du hast jetzt 8 Goldmark."),
                new Zeile(MarkSatz.MUENZHAUS_UNBEKANNT, 1L, Einsatz.von("posten", "kuerbishu"),
                        "&#d9564dDen Posten \"kuerbishu\" gibt es im Katalog nicht."),
                new Zeile(MarkSatz.MUENZHAUS_BEDINGUNG_FEHLT, 1L, Einsatz.von("name", sprache.wort(new Muenzname("blumenkiste"), Einsatz.keine())),
                        "&#d9564dBlumenkiste ist noch nicht frei fuer dich. Dafuer brauchst du:"),
                new Zeile(MarkSatz.MUENZHAUS_ZU_WENIG, 1L,
                        Einsatz.von("preis", sprache.goldmark(5L))
                                .mit("stand", sprache.goldmark(2L))
                                .mit("fehlt", sprache.goldmark(3L)), "&#d9564dDas kostet 5 Goldmark - du hast 2 Goldmark, dir fehlen 3 Goldmark."),
                new Zeile(MarkSatz.MUENZHAUS_GESTOERT, 1L, Einsatz.keine(),
                        "&#d9564dDas Muenzhaus hat nicht geantwortet - es ist nichts passiert."),
                new Zeile(MarkSatz.MUENZHAUS_ERSTATTET, 1L, Einsatz.keine(),
                        "&#d9564dDas Muenzhaus hat nicht geantwortet. Dein Geld ist zurueck."),
                new Zeile(MarkSatz.MUENZHAUS_VERLAUF_KOPF, 1L, Einsatz.von("betrag", sprache.goldmark(12L)),
                        "&#c6a15bGoldmark: 12 Goldmark"),
                new Zeile(MarkSatz.MUENZHAUS_VERLAUF_LEER, 1L, Einsatz.keine(),
                        "&#a5b4bfNoch keine Goldmark bewegt. Mit /mark tauschen kaufen 5 geht es los."),
                new Zeile(MarkSatz.MUENZHAUS_KEIN_PLATZ, 1L, Einsatz.von("stueck", "1"),
                        "&#d9564dIn deinem Rucksack fehlt Platz fuer 1 Stueck - es ist nichts passiert."),
                new Zeile(MarkSatz.MUENZHAUS_KEIN_PLATZ, 2L, Einsatz.von("stueck", "2"),
                        "&#d9564dIn deinem Rucksack fehlt Platz fuer 2 Stueck - es ist nichts passiert."),
                new Zeile(MarkSatz.MUENZHAUS_KEIN_PLATZ, 0L, Einsatz.von("stueck", "0"),
                        "&#d9564dIn deinem Rucksack fehlt Platz fuer 0 Stueck - es ist nichts passiert."),
                new Zeile(MarkSatz.MUENZHAUS_BEDINGUNG_TAETIGE_STUNDEN, 1L, Einsatz.von("zahl", "1"),
                        "&#a5b4bf- 1 Stunde taetige Zeit"),
                new Zeile(MarkSatz.MUENZHAUS_BEDINGUNG_TAETIGE_STUNDEN, 2L, Einsatz.von("zahl", "2"),
                        "&#a5b4bf- 2 Stunden taetige Zeit"),
                new Zeile(MarkSatz.MUENZHAUS_BEDINGUNG_TAETIGE_STUNDEN, 0L, Einsatz.von("zahl", "0"),
                        "&#a5b4bf- 0 Stunden taetige Zeit"),
                new Zeile(MarkSatz.MUENZHAUS_BEDINGUNG_ANWESENHEIT_STUNDEN, 1L, Einsatz.von("zahl", "1"),
                        "&#a5b4bf- 1 Stunde auf dem Server"),
                new Zeile(MarkSatz.MUENZHAUS_BEDINGUNG_ANWESENHEIT_STUNDEN, 2L, Einsatz.von("zahl", "2"),
                        "&#a5b4bf- 2 Stunden auf dem Server"),
                new Zeile(MarkSatz.MUENZHAUS_BEDINGUNG_ANWESENHEIT_STUNDEN, 0L, Einsatz.von("zahl", "0"),
                        "&#a5b4bf- 0 Stunden auf dem Server"),
                new Zeile(MarkSatz.MUENZHAUS_BEDINGUNG_PARZELLEN, 1L, Einsatz.von("zahl", "1"),
                        "&#a5b4bf- 1 eigene Parzelle"),
                new Zeile(MarkSatz.MUENZHAUS_BEDINGUNG_PARZELLEN, 2L, Einsatz.von("zahl", "2"),
                        "&#a5b4bf- 2 eigene Parzellen"),
                new Zeile(MarkSatz.MUENZHAUS_BEDINGUNG_PARZELLEN, 0L, Einsatz.von("zahl", "0"),
                        "&#a5b4bf- 0 eigene Parzellen"),
                new Zeile(MarkSatz.MUENZHAUS_BEDINGUNG_BERUF, 1L, Einsatz.von("zahl", "1"),
                        "&#a5b4bf- 1 gewaehlter Beruf"),
                new Zeile(MarkSatz.MUENZHAUS_BEDINGUNG_BERUF, 2L, Einsatz.von("zahl", "2"),
                        "&#a5b4bf- 2 gewaehlte Berufe"),
                new Zeile(MarkSatz.MUENZHAUS_BEDINGUNG_BERUF, 0L, Einsatz.von("zahl", "0"),
                        "&#a5b4bf- 0 gewaehlte Berufe"),
                new Zeile(MarkSatz.MUENZHAUS_BEDINGUNG_GUTHABEN, 1L, Einsatz.von("zahl", sprache.betrag(1L)),
                        "&#a5b4bf- 1 Mark auf dem Konto"),
                new Zeile(MarkSatz.MUENZHAUS_BEDINGUNG_GUTHABEN, 2L, Einsatz.von("zahl", sprache.betrag(2L)),
                        "&#a5b4bf- 2 Mark auf dem Konto"),
                new Zeile(MarkSatz.MUENZHAUS_BEDINGUNG_GUTHABEN, 0L, Einsatz.von("zahl", sprache.betrag(0L)),
                        "&#a5b4bf- 0 Mark auf dem Konto"),
                // Der Rundgang [21.11.2026] - eine Frage mit ja oder nein. Die
                // Einzahlform nennt die 1 ausgeschrieben, wie beim Beruf; die Mehrzahl
                // liest im Betrieb niemand, muss aber dastehen.
                new Zeile(MarkSatz.MUENZHAUS_BEDINGUNG_RUNDGANG, 1L, Einsatz.von("zahl", 1L),
                        "&#a5b4bf- 1 abgeschlossener Rundgang durch Altfurt"),
                new Zeile(MarkSatz.MUENZHAUS_BEDINGUNG_RUNDGANG, 2L, Einsatz.von("zahl", 2L),
                        "&#a5b4bf- 2 abgeschlossene Rundgaenge"),
                new Zeile(MarkSatz.MUENZHAUS_BEDINGUNG_RUNDGANG, 0L, Einsatz.von("zahl", 0L),
                        "&#a5b4bf- 0 abgeschlossene Rundgaenge"),

                // ------------------------------------------------------------
                // SCHRITT 16a TEIL A: DIE TEAMROLLEN
                //
                // Betreibertexte, und darum ist diese Tafel hier besonders wichtig:
                // Ein Spieler meldet eine verrutschte Spalte, ein Betreiber sieht sie
                // einmal und tippt weiter.
                // ------------------------------------------------------------
                new Zeile(TeamSatz.KOPF, 1L, Einsatz.keine(), "&#c6a15bDas Team des Freimarkenbunds"),
                new Zeile(TeamSatz.ZEILE, 1L, Einsatz.von("spieler", "Anna")
                        .mit("rollen", "Moderator, Baumeister"), "&#a5b4bf  Anna - &#edf1f3Moderator, Baumeister"),
                new Zeile(TeamSatz.LEER, 1L, Einsatz.keine(), "&#6e7a82Niemand hat eine Teamrolle. Vergeben wird eine mit /team gib - welche es gibt, zeigt /team rollen."),
                new Zeile(TeamSatz.ROLLEN_KOPF, 1L, Einsatz.keine(), "&#c6a15bDie Teamrollen dieses Servers"),
                // GEZAEHLT: ein Recht ist nicht zwei. Die Null steht mit dabei, wie
                // ueberall in dieser Tafel - eine Rolle ohne Rechte ist erlaubt, und
                // Teamwerte.ungereimtheiten meldet sie, statt sie zu verbieten.
                new Zeile(TeamSatz.ROLLEN_ZEILE, 1L, Einsatz.von("rolle", "Helfer")
                        .mit("rechte", 1L), "&#a5b4bf  &#edf1f3Helfer&#a5b4bf &#6e7a82- 1 Recht"),
                new Zeile(TeamSatz.ROLLEN_ZEILE, 2L, Einsatz.von("rolle", "Moderator")
                        .mit("rechte", 4L), "&#a5b4bf  &#edf1f3Moderator&#a5b4bf &#6e7a82- 4 Rechte"),
                new Zeile(TeamSatz.ROLLEN_ZEILE, 0L, Einsatz.von("rolle", "Pfoertner")
                        .mit("rechte", 0L), "&#a5b4bf  &#edf1f3Pfoertner&#a5b4bf &#6e7a82- 0 Rechte"),
                new Zeile(TeamSatz.ROLLEN_LEER, 1L, Einsatz.keine(), "&#e3c57eDieser Server hat keine Teamrollen eingetragen. Sie stehen in der config.yml unter team.rollen."),
                new Zeile(TeamSatz.ZEIGEN_KOPF, 1L, Einsatz.von("spieler", "Anna"),
                        "&#c6a15bDie Teamrollen von Anna"),
                new Zeile(TeamSatz.ZEIGEN_ZEILE, 1L, Einsatz.von("rolle", "Moderator")
                        .mit("seit", "24.11.2026 12:00").mit("von", "Bert"), "&#a5b4bf  &#edf1f3Moderator&#a5b4bf &#6e7a82- seit 24.11.2026 12:00, von Bert"),
                // Die Zeile, die den Befehl rechtfertigt: Die Rolle steht in der
                // Datenbank, aber nicht mehr in der config.yml - sie gibt KEINE Rechte.
                new Zeile(TeamSatz.ZEIGEN_UNBEKANNT, 1L,
                        Einsatz.von("rolle", "hausmeister"), "&#d9564d    Die config.yml kennt \"hausmeister\" nicht mehr - diese Rolle gibt KEINE Rechte. Entweder wieder eintragen oder mit /team nimm abnehmen."),
                new Zeile(TeamSatz.ZEIGEN_LEER, 1L, Einsatz.von("spieler", "Anna"),
                        "&#6e7a82Anna hat keine Teamrolle."),
                new Zeile(TeamSatz.VERGEBEN, 1L, Einsatz.von("spieler", "Anna")
                        .mit("rolle", "Moderator"), "&#73bf96Anna ist jetzt Moderator."),
                new Zeile(TeamSatz.HATTE_SCHON, 1L, Einsatz.von("spieler", "Anna")
                        .mit("rolle", "Moderator"), "&#e3c57eAnna ist schon Moderator - es ist nichts passiert."),
                new Zeile(TeamSatz.GENOMMEN, 1L, Einsatz.von("spieler", "Anna")
                        .mit("rolle", "Moderator"), "&#73bf96Anna ist nicht mehr Moderator."),
                new Zeile(TeamSatz.HATTE_NICHT, 1L, Einsatz.von("spieler", "Anna")
                        .mit("rolle", "Moderator"), "&#e3c57eAnna ist nicht Moderator - es ist nichts passiert."),
                new Zeile(TeamSatz.GENOMMEN_UNBEKANNT, 1L, Einsatz.von("spieler", "Anna")
                        .mit("rolle", "hausmeister"), "&#73bf96Anna ist nicht mehr hausmeister. &#6e7a82Diese Rolle stand nur noch in der Datenbank."),
                new Zeile(TeamSatz.UNBEKANNTE_ROLLE, 1L,
                        Einsatz.von("rolle", "moderaator")
                                .mit("rollen", "Helfer, Moderator, Verwalter"), "&#d9564dDie Rolle \"moderaator\" gibt es nicht. &#6e7a82Es gibt: Helfer, Moderator, Verwalter"),
                // DER RIEGEL: Die Absage nennt die fehlenden Rechte VOLLSTAENDIG - wer
                // eine Vollmacht weitergeben will, die er nicht hat, soll lesen, welche
                // das ist.
                new Zeile(TeamSatz.ZU_WENIG_RECHTE, 1L, Einsatz.von("rolle", "Verwalter")
                        .mit("fehlende", "freimarken.besitz, freimarken.verwalten"),
                        "&#d9564dDu kannst \"Verwalter\" nicht vergeben oder entziehen - dir fehlen Rechte, die diese Rolle gibt: &#6e7a82freimarken.besitz, freimarken.verwalten"),
                new Zeile(TeamSatz.KEIN_TEAM, 1L, Einsatz.keine(), "&#e3c57eDieser Server hat keine Teamrollen eingetragen - es gibt nichts zu vergeben. Sie stehen in der config.yml unter team.rollen."),
                new Zeile(TeamSatz.UNBEKANNTER_SPIELER, 1L,
                        Einsatz.von("name", "Annna"), "&#d9564dDen Spieler \"Annna\" kennt dieser Server nicht."),
                // ------------------------------------------------------------
                // SCHRITT 16a TEIL C: MELDEN UND BEARBEITEN
                //
                // ZWEI Leserschaften, und darum ist diese Tafel hier doppelt wichtig:
                // Die Saetze unter melden. liest ein Spieler im Augenblick eines
                // Vorfalls, die unter meldung. ein Moderator in einer Liste. Eine
                // verrutschte Spalte in der Liste sieht er einmal und tippt weiter; eine
                // kaputte Absage an den Melder kostet die naechste Meldung.
                // ------------------------------------------------------------
                new Zeile(MeldeSatz.ANGELEGT, 1L, Einsatz.von("nummer", 7L)
                        .mit("spieler", "Bert"), "&#73bf96Deine Meldung zu Bert ist aufgenommen (Nummer 7). &#6e7a82Ein Moderator sieht sie sich an - du bekommst Bescheid."),
                new Zeile(MeldeSatz.AUS, 1L, Einsatz.keine(), "&#e3c57eAuf diesem Server wird nicht gemeldet."),
                // GEZAEHLT: eine Sekunde ist nicht zwei. Die Null steht mit dabei, wie
                // ueberall in dieser Tafel - sie kann nicht eintreten (Meldebuch gibt
                // ABKLINGZEIT nur mit Restzeit > 0, und sein Entscheid klagt sonst), und
                // genau darum ist sie aufzuschreiben: Wer den Riegel herausnimmt, sieht
                // hier, was der Spieler dann liest.
                new Zeile(MeldeSatz.ABKLINGZEIT, 1L, Einsatz.von("sekunden", 1L), "&#e3c57eNoch 1 Sekunde, dann kannst du wieder melden."),
                new Zeile(MeldeSatz.ABKLINGZEIT, 2L, Einsatz.von("sekunden", 40L),
                        "&#e3c57eNoch 40 Sekunden, dann kannst du wieder melden."),
                new Zeile(MeldeSatz.ABKLINGZEIT, 0L, Einsatz.von("sekunden", 0L), "&#e3c57eNoch 0 Sekunden, dann kannst du wieder melden."),
                new Zeile(MeldeSatz.ZU_VIELE_OFFENE, 1L, Einsatz.von("grenze", 1L),
                        "&#e3c57eDu hast schon 1 unerledigte Meldung. Sobald sie bearbeitet ist, kannst du wieder melden."),
                new Zeile(MeldeSatz.ZU_VIELE_OFFENE, 2L, Einsatz.von("grenze", 3L),
                        "&#e3c57eDu hast schon 3 unerledigte Meldungen. Sobald eine davon bearbeitet ist, kannst du wieder melden."),
                new Zeile(MeldeSatz.ZU_VIELE_OFFENE, 0L, Einsatz.von("grenze", 0L),
                        "&#e3c57eDu hast schon 0 unerledigte Meldungen. Sobald eine davon bearbeitet ist, kannst du wieder melden."),
                new Zeile(MeldeSatz.SELBST, 1L, Einsatz.keine(), "&#d9564dDich selbst kannst du nicht melden."),
                new Zeile(MeldeSatz.GRUND_ZU_KURZ, 1L, Einsatz.von("mindestens", 1L),
                        "&#d9564dSchreib dazu, was passiert ist - mindestens 1 Zeichen."),
                new Zeile(MeldeSatz.GRUND_ZU_KURZ, 2L, Einsatz.von("mindestens", 3L),
                        "&#d9564dSchreib dazu, was passiert ist - mindestens 3 Zeichen."),
                new Zeile(MeldeSatz.GRUND_ZU_KURZ, 0L, Einsatz.von("mindestens", 0L),
                        "&#d9564dSchreib dazu, was passiert ist - mindestens 0 Zeichen."),
                new Zeile(MeldeSatz.GRUND_ZU_LANG, 1L, Einsatz.von("hoechstens", 1L),
                        "&#d9564dDas ist zu lang - hoechstens 1 Zeichen."),
                new Zeile(MeldeSatz.GRUND_ZU_LANG, 2L, Einsatz.von("hoechstens", 256L),
                        "&#d9564dDas ist zu lang - hoechstens 256 Zeichen."),
                new Zeile(MeldeSatz.GRUND_ZU_LANG, 0L, Einsatz.von("hoechstens", 0L),
                        "&#d9564dDas ist zu lang - hoechstens 0 Zeichen."),
                new Zeile(MeldeSatz.GRUND_ZEICHEN, 1L, Einsatz.keine(), "&#d9564dIn deinem Grund steht ein Zeichen, das dort nicht hingehoert. Schreib ihn in gewoehnlichen Worten."),
                new Zeile(MeldeSatz.UNBEKANNTER_SPIELER, 1L,
                        Einsatz.von("name", "Berrt"), "&#d9564dDen Spieler Berrt kennt dieser Server nicht."),
                new Zeile(MeldeSatz.NUR_SPIELER, 1L, Einsatz.keine(), "&#d9564dMelden kann ein Spieler - eine Meldung haelt den Ort fest, und die Konsole steht nirgends."),
                new Zeile(MeldeSatz.GESTOERT, 1L, Einsatz.keine(), "&#d9564dDie Meldung konnte nicht aufgenommen werden - versuch es noch einmal."),
                new Zeile(MeldeSatz.ANSAGE, 1L, Einsatz.von("nummer", 7L)
                        .mit("melder", "Anna").mit("betroffener", "Bert")
                        .mit("grund", "baut auf meiner Parzelle"), "&#e3c57eNeue Meldung 7: &#a5b4bfAnna meldet Bert - baut auf meiner Parzelle&#e3c57e &#6e7a82/meldung 7"),
                new Zeile(MeldeSatz.ANTWORT_KOPF, 1L, Einsatz.von("anzahl", 1L), "&#c6a15bEs gibt eine Antwort auf deine Meldung:"),
                new Zeile(MeldeSatz.ANTWORT_KOPF, 2L, Einsatz.von("anzahl", 3L), "&#c6a15bEs gibt 3 Antworten auf deine Meldungen:"),
                new Zeile(MeldeSatz.ANTWORT_KOPF, 0L, Einsatz.von("anzahl", 0L), "&#c6a15bEs gibt 0 Antworten auf deine Meldungen:"),
                new Zeile(MeldeSatz.ANTWORT_ABGESCHLOSSEN, 1L, Einsatz.von("nummer", 7L)
                        .mit("betroffener", "Bert").mit("vermerk", "angesprochen"),
                        "&#73bf96  Meldung 7 (Bert): bearbeitet. &#a5b4bfangesprochen"),
                new Zeile(MeldeSatz.ANTWORT_ABGEWIESEN, 1L, Einsatz.von("nummer", 7L)
                        .mit("betroffener", "Bert").mit("vermerk", "nichts gefunden"),
                        "&#e3c57e  Meldung 7 (Bert): kein Verstoss. &#a5b4bfnichts gefunden"),
                new Zeile(MeldeSatz.LISTE_KOPF, 1L, Einsatz.von("anzahl", 1L), "&#c6a15b1 offene Meldung"),
                new Zeile(MeldeSatz.LISTE_KOPF, 2L, Einsatz.von("anzahl", 4L), "&#c6a15b4 offene Meldungen"),
                new Zeile(MeldeSatz.LISTE_KOPF, 0L, Einsatz.von("anzahl", 0L), "&#c6a15b0 offene Meldungen"),
                new Zeile(MeldeSatz.LISTE_ZEILE, 1L, Einsatz.von("nummer", 7L)
                        .mit("melder", "Anna").mit("betroffener", "Bert")
                        .mit("grund", "baut auf meiner Parzelle")
                        .mit("alter", "vor 3 Std.").mit("stand", "offen"), "&#a5b4bf  &#edf1f37&#a5b4bf Anna an Bert: baut auf meiner Parzelle &#6e7a82(vor 3 Std., offen)"),
                new Zeile(MeldeSatz.LISTE_LEER, 1L, Einsatz.keine(), "&#6e7a82Es liegt keine Meldung an."),
                new Zeile(MeldeSatz.LISTE_HINWEIS, 1L, Einsatz.keine(), "&#6e7a82/meldung NUMMER zeigt sie, dahinter annehmen, abschliessen, abweisen oder hin"),
                new Zeile(MeldeSatz.ZEIGEN_KOPF, 1L, Einsatz.von("nummer", 7L)
                        .mit("stand", "in Arbeit"), "&#c6a15bMeldung 7 &#6e7a82(in Arbeit)"),
                new Zeile(MeldeSatz.ZEIGEN_WER, 1L, Einsatz.von("melder", "Anna")
                        .mit("betroffener", "Bert").mit("alter", "vor 3 Std."), "&#a5b4bf  Anna meldet Bert &#6e7a82- vor 3 Std."),
                new Zeile(MeldeSatz.ZEIGEN_GRUND, 1L,
                        Einsatz.von("grund", "baut auf meiner Parzelle"), "&#a5b4bf  baut auf meiner Parzelle"),
                new Zeile(MeldeSatz.ZEIGEN_ORT, 1L, Einsatz.von("welt", "altfurt")
                        .mit("x", 10).mit("y", -60).mit("z", -20).mit("nummer", 7L),
                        "&#6e7a82  Ort: altfurt 10/-60/-20 - /meldung 7 hin"),
                new Zeile(MeldeSatz.ZEIGEN_BEARBEITER, 1L,
                        Einsatz.von("bearbeiter", "Chef"), "&#6e7a82  In Arbeit bei Chef"),
                new Zeile(MeldeSatz.ZEIGEN_VERMERK, 1L,
                        Einsatz.von("vermerk", "angesprochen"), "&#6e7a82  Vermerk: angesprochen"),
                new Zeile(MeldeSatz.GENOMMEN, 1L, Einsatz.von("nummer", 7L), "&#73bf96Meldung 7 ist jetzt bei dir."),
                new Zeile(MeldeSatz.SCHON_GENOMMEN, 1L, Einsatz.von("nummer", 7L)
                        .mit("bearbeiter", "Chef"), "&#e3c57eMeldung 7 hat schon Chef."),
                new Zeile(MeldeSatz.ABGESCHLOSSEN, 1L, Einsatz.von("nummer", 7L)
                        .mit("melder", "Anna"), "&#73bf96Meldung 7 ist abgeschlossen - Anna bekommt Bescheid."),
                new Zeile(MeldeSatz.ABGEWIESEN, 1L, Einsatz.von("nummer", 7L)
                        .mit("melder", "Anna"), "&#73bf96Meldung 7 ist abgewiesen - Anna bekommt Bescheid."),
                new Zeile(MeldeSatz.SCHON_ERLEDIGT, 1L, Einsatz.von("nummer", 7L)
                        .mit("stand", "abgeschlossen"), "&#e3c57eMeldung 7 ist schon abgeschlossen."),
                new Zeile(MeldeSatz.UNBEKANNT, 1L, Einsatz.von("nummer", 99L), "&#d9564dEine Meldung mit der Nummer 99 gibt es nicht."),
                new Zeile(MeldeSatz.VERMERK_ZU_LANG, 1L,
                        Einsatz.von("hoechstens", 1L), "&#d9564dDer Vermerk ist zu lang - hoechstens 1 Zeichen."),
                new Zeile(MeldeSatz.VERMERK_ZU_LANG, 2L,
                        Einsatz.von("hoechstens", 256L), "&#d9564dDer Vermerk ist zu lang - hoechstens 256 Zeichen."),
                new Zeile(MeldeSatz.VERMERK_ZU_LANG, 0L,
                        Einsatz.von("hoechstens", 0L), "&#d9564dDer Vermerk ist zu lang - hoechstens 0 Zeichen."),
                new Zeile(MeldeSatz.HIN_UNTERWEGS, 1L, Einsatz.von("nummer", 7L)
                        .mit("welt", "altfurt"), "&#73bf96Unterwegs zum Ort von Meldung 7 in altfurt."),
                new Zeile(MeldeSatz.HIN_WELT_FEHLT, 1L, Einsatz.von("welt", "kornmark"),
                        "&#d9564dDie Welt kornmark gibt es nicht mehr - dorthin fuehrt kein Weg."),
                new Zeile(MeldeSatz.NUR_SPIELER_MODERATION, 1L, Einsatz.keine(), "&#d9564dDas kann ein Spieler - von der Konsole geht es nicht."),
                new Zeile(MeldeSatz.MODERATION_GESTOERT, 1L, Einsatz.keine(), "&#d9564dDie Meldungen antworten gerade nicht - es ist nichts passiert."),
                new Zeile(TeamSatz.GESTOERT, 1L, Einsatz.keine(), "&#d9564dDie Teamrollen antworten gerade nicht - es ist nichts passiert."),
                new Zeile(StrafSatz.VERHAENGT, 1L, Einsatz.von("nummer", 7L).mit("spieler", "Bert").mit("art", "Sperre").mit("dauer", "1 Std.").mit("grund", "Grief in Altfurt"),
                        "&#73bf96Bert: Sperre - 1 Std. (Nummer 7). &#a5b4bfGrief in Altfurt"),
                new Zeile(StrafSatz.VERHAENGT_OHNE_DAUER, 1L, Einsatz.von("nummer", 7L).mit("spieler", "Bert").mit("art", "Rauswurf").mit("grund", "Spam im Chat"),
                        "&#73bf96Bert: Rauswurf (Nummer 7). &#a5b4bfSpam im Chat"),
                new Zeile(StrafSatz.ANSAGE_OHNE_DAUER, 1L, Einsatz.von("spieler", "Bert").mit("art", "Rauswurf").mit("grund", "Spam im Chat"),
                        "&#e3c57eBert: Rauswurf. &#a5b4bfSpam im Chat"),
                new Zeile(StrafSatz.OHNE_RECHT, 1L, Einsatz.von("art", "Sperre"),
                        "&#d9564dFuer Sperre fehlt dir das Recht."),
                new Zeile(StrafSatz.OHNE_RECHT_DAUERHAFT, 1L, Einsatz.von("art", "Sperre").mit("hoechstens", "30 Tg."),
                        "&#d9564dFuer Sperre darfst du hoechstens 30 Tg. verhaengen. &#6e7a82Laenger oder dauerhaft kann ein Verwalter."),
                new Zeile(StrafSatz.OHNE_RECHT_TEAM, 1L, Einsatz.von("spieler", "Bert"),
                        "&#d9564dBert ist selbst im Team. &#6e7a82Das kann ein Verwalter entscheiden."),
                new Zeile(StrafSatz.OHNE_RECHT_AUFHEBEN, 1L, Einsatz.keine(),
                        "&#d9564dZum Aufheben fehlt dir das Recht."),
                new Zeile(StrafSatz.OHNE_RECHT_DAUERHAFT_AUFHEBEN, 1L, Einsatz.von("nummer", 7L),
                        "&#d9564dStrafe 7 ist zu schwer fuer dich - sie aufzuheben kann ein Verwalter."),
                new Zeile(StrafSatz.SELBST_AUFHEBEN, 1L, Einsatz.keine(),
                        "&#d9564dDeine eigene Strafe kannst du nicht aufheben."),
                new Zeile(StrafSatz.OHNE_RECHT_TEAM_AUFHEBEN, 1L, Einsatz.von("nummer", 7L),
                        "&#d9564dStrafe 7 trifft ein Teammitglied - sie aufzuheben kann ein Verwalter."),
                new Zeile(StrafSatz.SELBST, 1L, Einsatz.keine(),
                        "&#d9564dDich selbst kannst du nicht bestrafen."),
                new Zeile(StrafSatz.GRUND_ZU_KURZ, 1L, Einsatz.von("mindestens", 3),
                        "&#d9564dSchreib dazu, warum - mindestens 1 Zeichen."),
                new Zeile(StrafSatz.GRUND_ZU_KURZ, 2L, Einsatz.von("mindestens", 3),
                        "&#d9564dSchreib dazu, warum - mindestens 3 Zeichen."),
                new Zeile(StrafSatz.GRUND_ZU_KURZ, 0L, Einsatz.von("mindestens", 3),
                        "&#d9564dSchreib dazu, warum - mindestens 3 Zeichen."),
                new Zeile(StrafSatz.GRUND_ZU_LANG, 1L, Einsatz.von("hoechstens", 256),
                        "&#d9564dDer Grund ist zu lang - hoechstens 1 Zeichen."),
                new Zeile(StrafSatz.GRUND_ZU_LANG, 2L, Einsatz.von("hoechstens", 256),
                        "&#d9564dDer Grund ist zu lang - hoechstens 256 Zeichen."),
                new Zeile(StrafSatz.GRUND_ZU_LANG, 0L, Einsatz.von("hoechstens", 256),
                        "&#d9564dDer Grund ist zu lang - hoechstens 256 Zeichen."),
                new Zeile(StrafSatz.GRUND_ZEICHEN, 1L, Einsatz.keine(),
                        "&#d9564dIm Grund steht ein Zeichen, das dort nicht hingehoert. Schreib ihn in gewoehnlichen Worten."),
                new Zeile(StrafSatz.UNBEKANNTER_SPIELER, 1L, Einsatz.von("name", "Zora"),
                        "&#d9564dDen Spieler Zora kennt dieser Server nicht."),
                new Zeile(StrafSatz.UNBEKANNTE_DAUER, 1L, Einsatz.von("wort", "10").mit("beispiele", "10m, 1h, dauerhaft"),
                        "&#d9564d10 ist keine Dauer. &#6e7a82Zahl und Einheit, zum Beispiel: 10m, 1h, dauerhaft"),
                new Zeile(StrafSatz.SCHON_GESTRAFT, 1L, Einsatz.von("spieler", "Bert").mit("art", "Sperre").mit("nummer", 7L).mit("bis", "26.11.2026 12:00"),
                        "&#d9564dBert hat schon Sperre bis 26.11.2026 12:00 (Nummer 7). &#6e7a82Erst aufheben, dann neu verhaengen."),
                new Zeile(StrafSatz.UNBEKANNT, 1L, Einsatz.von("nummer", 99L),
                        "&#d9564dEine Strafe mit der Nummer 99 gibt es nicht."),
                new Zeile(StrafSatz.NICHT_FORTWIRKEND, 1L, Einsatz.von("nummer", 7L).mit("art", "Rauswurf"),
                        "&#e3c57eStrafe 7 ist ein Rauswurf - daran ist nichts aufzuheben."),
                new Zeile(StrafSatz.AUFGEHOBEN, 1L, Einsatz.von("nummer", 7L).mit("spieler", "Bert").mit("art", "Sperre"),
                        "&#73bf96Strafe 7 gegen Bert (Sperre) ist aufgehoben."),
                new Zeile(StrafSatz.SCHON_AUFGEHOBEN, 1L, Einsatz.von("nummer", 7L).mit("wer", "Anna"),
                        "&#e3c57eStrafe 7 hat Anna schon aufgehoben."),
                new Zeile(StrafSatz.GESTOERT, 1L, Einsatz.keine(),
                        "&#d9564dDas konnte nicht geschrieben werden - versuch es noch einmal."),
                new Zeile(StrafSatz.HINWEIS, 1L, Einsatz.von("arten", "stumm, rauswurf, bann"),
                        "&#c6a15b/strafe ARTEN SPIELER DAUER GRUND\n&#a5b4bfArten: stumm, rauswurf, bann\n&#6e7a82/strafe aufheben NUMMER GRUND - /strafe verlauf SPIELER - /bannliste"),
                new Zeile(StrafSatz.ANSAGE, 1L, Einsatz.von("spieler", "Bert").mit("art", "Sperre").mit("dauer", "1 Std.").mit("grund", "Grief in Altfurt"),
                        "&#e3c57eBert: Sperre - 1 Std.. &#a5b4bfGrief in Altfurt"),
                new Zeile(StrafSatz.ANSAGE_AUFHEBUNG, 1L, Einsatz.von("spieler", "Bert").mit("art", "Sperre").mit("grund", "Einspruch angenommen"),
                        "&#73bf96Bert ist wieder frei (Sperre). &#a5b4bfEinspruch angenommen"),
                new Zeile(StrafSatz.GETROFFEN_RAUSWURF, 1L, Einsatz.von("grund", "Spam im Chat").mit("wer", "Anna"),
                        "&#d9564dDu wurdest hinausgeworfen.\n&#a5b4bfSpam im Chat\n&#6e7a82von Anna"),
                new Zeile(StrafSatz.GETROFFEN_BANN, 1L, Einsatz.von("grund", "Grief in Altfurt").mit("bis", "26.11.2026 12:00").mit("dauer", "1 Std."),
                        "&#d9564dDu bist gesperrt bis 26.11.2026 12:00 (noch 1 Std.).\n&#a5b4bfGrief in Altfurt"),
                new Zeile(StrafSatz.GETROFFEN_BANN_DAUERHAFT, 1L, Einsatz.von("grund", "Grief in Altfurt"),
                        "&#d9564dDu bist dauerhaft gesperrt.\n&#a5b4bfGrief in Altfurt\n&#6e7a82Wenn du das fuer falsch haeltst, schreib uns."),
                new Zeile(StrafSatz.GETROFFEN_STUMM, 1L, Einsatz.von("grund", "Beleidigung").mit("dauer", "10 Min."),
                        "&#d9564dDu bist stummgeschaltet - noch 10 Min.. &#a5b4bfBeleidigung"),
                new Zeile(StrafSatz.GETROFFEN_STUMM_DAUERHAFT, 1L, Einsatz.von("grund", "Beleidigung"),
                        "&#d9564dDu bist dauerhaft stummgeschaltet. &#a5b4bfBeleidigung"),
                new Zeile(StrafSatz.GETROFFEN_BEFEHL, 1L, Einsatz.von("befehl", "msg").mit("grund", "Beleidigung").mit("dauer", "10 Min."),
                        "&#d9564dmsg ist gesperrt, solange du stumm bist - noch 10 Min.. &#a5b4bfBeleidigung"),
                new Zeile(StrafSatz.VERLAUF_KOPF, 1L, Einsatz.von("anzahl", 1).mit("spieler", "Bert"),
                        "&#c6a15b1 Strafe gegen Bert"),
                new Zeile(StrafSatz.VERLAUF_KOPF, 2L, Einsatz.von("anzahl", 2).mit("spieler", "Bert"),
                        "&#c6a15b2 Strafen gegen Bert"),
                new Zeile(StrafSatz.VERLAUF_KOPF, 0L, Einsatz.von("anzahl", 0).mit("spieler", "Bert"),
                        "&#c6a15b0 Strafen gegen Bert"),
                new Zeile(StrafSatz.VERLAUF_ZEILE, 1L, Einsatz.von("nummer", 7L).mit("art", "Sperre").mit("stand", "gilt").mit("wann", "25.11.2026 12:00").mit("grund", "Grief in Altfurt"),
                        "&#a5b4bf  7 Sperre (gilt) 25.11.2026 12:00 - Grief in Altfurt"),
                new Zeile(StrafSatz.VERLAUF_LEER, 1L, Einsatz.von("spieler", "Bert"),
                        "&#a5b4bfGegen Bert steht nichts."),
                new Zeile(StrafSatz.VERLAUF_HINWEIS, 1L, Einsatz.keine(),
                        "&#6e7a82/strafe aufheben NUMMER GRUND"),
                new Zeile(StrafSatz.BANNLISTE_KOPF, 1L, Einsatz.von("anzahl", 1),
                        "&#c6a15b1 Spieler ist gesperrt"),
                new Zeile(StrafSatz.BANNLISTE_KOPF, 2L, Einsatz.von("anzahl", 2),
                        "&#c6a15b2 Spieler sind gesperrt"),
                new Zeile(StrafSatz.BANNLISTE_KOPF, 0L, Einsatz.von("anzahl", 0),
                        "&#c6a15b0 Spieler sind gesperrt"),
                new Zeile(StrafSatz.BANNLISTE_ZEILE, 1L, Einsatz.von("spieler", "Bert").mit("bis", "26.11.2026 12:00").mit("grund", "Grief in Altfurt"),
                        "&#a5b4bf  Bert bis 26.11.2026 12:00 - Grief in Altfurt"),
                new Zeile(StrafSatz.BANNLISTE_LEER, 1L, Einsatz.keine(),
                        "&#73bf96Niemand ist gesperrt."),
                new Zeile(StrafSatz.BANNLISTE_OHNE_RECHT, 1L, Einsatz.keine(),
                        "&#d9564dDie Bannliste ist auf diesem Server nicht oeffentlich."),
                new Zeile(StrafSatz.BANNLISTE_GESTOERT, 1L, Einsatz.keine(),
                        "&#d9564dDie Bannliste ist gerade nicht lesbar - versuch es noch einmal."),
                new Zeile(EinsichtSatz.AN, 1L, Einsatz.von("bis", "13:05:00").mit("dauer", "5 Min."),
                        "&#73bf96Du siehst jetzt in fremde Behaelter - bis 13:05:00 (noch 5 Min.). &#6e7a82Nehmen kannst du nichts."),
                new Zeile(EinsichtSatz.AUS, 1L, Einsatz.keine(),
                        "&#73bf96Du siehst nicht mehr ein."),
                new Zeile(EinsichtSatz.WAR_AUS, 1L, Einsatz.keine(),
                        "&#e3c57eDu hast gar nicht eingesehen."),
                new Zeile(EinsichtSatz.STAND_AN, 1L, Einsatz.von("bis", "13:05:00").mit("dauer", "5 Min."),
                        "&#a5b4bfDu siehst ein bis 13:05:00 (noch 5 Min.)."),
                new Zeile(EinsichtSatz.STAND_AUS, 1L, Einsatz.keine(),
                        "&#a5b4bfDu siehst nicht ein."),
                new Zeile(EinsichtSatz.NUR_SEHEN, 1L, Einsatz.keine(),
                        "&#d9564dEinsehen heisst hinsehen - nehmen kannst du hier nichts."),
                new Zeile(EinsichtSatz.NUR_SPIELER, 1L, Einsatz.keine(),
                        "&#d9564dEinsehen kann ein Spieler - die Konsole klickt nicht in Kisten."),
                // /portal [25.11.2026]. Die Zahlen sind NACKT und ohne
                // Tausenderpunkt - sie werden mit der F3-Anzeige verglichen, und
                // die zeigt auch keinen. Darum stehen hier echte Koordinaten aus
                // Altfurt (y -60 bis -58) und keine runden Zahlen.
                new Zeile(PortalSatz.ERSTE_ECKE, 1L,
                        Einsatz.von("x", "106").mit("y", "-60").mit("z", "-4"),
                        "&#e3c57eErste Ecke gemerkt bei 106/-60/-4. Jetzt an die andere stellen und /portal ecke noch einmal."),
                // DER SATZ MIT DER MASKIERTEN KLAMMER: In der Vorlage steht
                // \\<welt>, im Chat steht <welt>. Ohne die Maskierung laedt die
                // ganze Datei nicht - MiniMessage liest <welt> als Farbrolle und
                // reisst jede Probe mit, die die Vorlage laedt (gemessen am
                // 25.11.2026: 930 rote Proben aus EINER Zeile).
                new Zeile(PortalSatz.ZWEITE_ECKE, 1L,
                        Einsatz.von("von", "106/-60/-4").mit("bis", "108/-58/-4")
                                .mit("bloecke", 9L),
                        "&#e3c57eQuader gemerkt: 106/-60/-4 bis 108/-58/-4, 9 Bloecke. Jetzt /portal setzen <welt>."),
                new Zeile(PortalSatz.ECKE_FALSCHE_WELT, 1L,
                        Einsatz.von("welt", "kornmark").mit("ankunftswelt", "altfurt"),
                        "&#d9564dHier nicht - du stehst in kornmark, die Tore stehen in altfurt."),
                new Zeile(PortalSatz.OHNE_ECKEN, 1L, Einsatz.keine(),
                        "&#d9564dErst zwei Ecken merken: stell dich in die eine, /portal ecke, dann in die andere und noch einmal."),
                new Zeile(PortalSatz.UNBEKANNTE_WELT, 1L, Einsatz.von("welt", "vogtei"),
                        "&#d9564dDie Welt vogtei gibt es nicht - unter welten: in der config.yml steht kein solcher Eintrag."),
                // {grund} ist die Klage des PROGRAMMS - hier gekuerzt, im Spiel
                // nennt sie Zahlen. Genau darum steht sie nicht in der Vorlage.
                new Zeile(PortalSatz.ABGELEHNT, 1L,
                        Einsatz.von("grund", "Tor nach nebenan."),
                        "&#d9564dDas Tor wird nicht gesetzt: Tor nach nebenan."),
                new Zeile(PortalSatz.GESETZT, 1L,
                        Einsatz.von("welt", "vogtei").mit("von", "106/-60/-4")
                                .mit("bis", "108/-58/-4"),
                        "&#e3c57eTor nach vogtei gesetzt: 106/-60/-4 bis 108/-58/-4. Es wirkt sofort."),
                new Zeile(PortalSatz.NICHT_GESCHRIEBEN, 1L,
                        Einsatz.von("datei", "tore.yml"),
                        "&#d9564dtore.yml liess sich nicht schreiben - das Tor gilt bis zum naechsten Start und danach nicht mehr. Der Grund steht im Serverprotokoll."),
                // DREI Zeilen je gezaehltem Satz - 1, 2 UND 0. Die Null faengt
                // den Fall, in dem die Mehrzahlform mit 0 ankommt: "0 Tore" ist
                // richtig, "0 Tor" waere es nicht. Im Spiel kann /portal liste
                // die Null nicht erreichen (dann kommt liste.leer), aber die
                // Tafel prueft den SATZ und nicht den Weg dorthin.
                new Zeile(PortalSatz.LISTE_KOPF, 1L, Einsatz.von("anzahl", 1L),
                        "&#a5b4bfAm Bundesmarkt steht 1 Tor:"),
                new Zeile(PortalSatz.LISTE_KOPF, 2L, Einsatz.von("anzahl", 2L),
                        "&#a5b4bfAm Bundesmarkt stehen 2 Tore:"),
                new Zeile(PortalSatz.LISTE_KOPF, 0L, Einsatz.von("anzahl", 0L),
                        "&#a5b4bfAm Bundesmarkt stehen 0 Tore:"),
                new Zeile(PortalSatz.LISTE_ZEILE, 1L,
                        Einsatz.von("welt", "marktstadt").mit("von", "106/-60/-4")
                                .mit("bis", "108/-58/-4")
                                .mit("wo", "Osttorhaus, nordliche Nische"),
                        "&#a5b4bfmarktstadt&#6e7a82 - 106/-60/-4 bis 108/-58/-4 (Osttorhaus, nordliche Nische)"),
                new Zeile(PortalSatz.LISTE_LEER, 1L, Einsatz.keine(),
                        "&#a5b4bfAm Bundesmarkt steht kein Tor - gereist wird nur mit /warp."),
                new Zeile(PortalSatz.ENTFERNT, 1L, Einsatz.von("welt", "vogtei"),
                        "&#e3c57eTor nach vogtei entfernt. Dorthin kommt man jetzt nur mit /warp."),
                new Zeile(PortalSatz.KEIN_TOR, 1L, Einsatz.von("welt", "vogtei"),
                        "&#d9564dNach vogtei fuehrt kein Tor."),
                new Zeile(PortalSatz.NEU_GELESEN, 1L, Einsatz.von("anzahl", 1L),
                        "&#e3c57etore.yml neu gelesen - 1 Tor."),
                new Zeile(PortalSatz.NEU_GELESEN, 2L, Einsatz.von("anzahl", 2L),
                        "&#e3c57etore.yml neu gelesen - 2 Tore."),
                // Die Null IST hier erreichbar: Wer das letzte Tor entfernt und
                // dann neu laedt, liest sie.
                new Zeile(PortalSatz.NEU_GELESEN, 0L, Einsatz.von("anzahl", 0L),
                        "&#e3c57etore.yml neu gelesen - 0 Tore."),
                new Zeile(PortalSatz.NEULADEN_KLAGT, 1L, Einsatz.von("anzahl", 1L),
                        "&#d9564dtore.yml neu gelesen, aber 1 Klage steht im Serverprotokoll."),
                new Zeile(PortalSatz.NEULADEN_KLAGT, 2L, Einsatz.von("anzahl", 2L),
                        "&#d9564dtore.yml neu gelesen, aber 2 Klagen stehen im Serverprotokoll."),
                new Zeile(PortalSatz.NEULADEN_KLAGT, 0L, Einsatz.von("anzahl", 0L),
                        "&#d9564dtore.yml neu gelesen, aber 0 Klagen stehen im Serverprotokoll."),
                new Zeile(PortalSatz.NUR_SPIELER, 1L, Einsatz.keine(),
                        "&#d9564d/portal geht nur im Spiel - die Konsole steht nirgends."));
    }

    @ParameterizedTest(name = "11 - {0}")
    @MethodSource("tabelle")
    @DisplayName("11 - jede Zeile der Tabelle ist zeichengleich dem heutigen Bild")
    void bildIstZeichengleich(Zeile zeile) {
        Sprache sprache = Sprachprobe.spracheAusDemJar();
        assertEquals(zeile.bild(), Sprachprobe.bild(
                sprache.bau(zeile.schluessel(), zeile.anzahl(), zeile.werte())));
    }

    @Test
    @DisplayName("11b - jeder sendbare Schluessel hat seine Tabellenzeile")
    void jederSchluesselHatSeineTabellenzeile() {
        Set<String> erwartet = new LinkedHashSet<>();
        for (Satz satz : Sprachschluessel.alle()) {
            switch (satz.art()) {
                case SATZ, HANDBALKEN -> erwartet.add(satz.pfad() + "|1");
                case GEZAEHLT -> {
                    erwartet.add(satz.pfad() + "|1");
                    erwartet.add(satz.pfad() + "|2");
                    // Auch die Null: Gemessen an ParzellenBefehle:804-827 hat nenneAufwand
                    // drei Faelle und nicht zwei - bei 0 wird GAR NICHT gesendet. Wer die
                    // Zeile mit Anzahl 0 nicht aufschreibt, merkt nicht, wenn dem Spieler
                    // eines Tages "Dabei verschwinden 0 Bloecke" neben "Es steht nichts
                    // darauf" steht.
                    erwartet.add(satz.pfad() + "|0");
                }
                default -> {
                    // WORT und MUSTER tragen kein Bild - sie werden eingesetzt.
                }
            }
        }
        Set<String> aufgeschrieben = new LinkedHashSet<>();
        tabelle().forEach(zeile -> aufgeschrieben.add(zeile.marke()));
        // assertEquals auf die MENGE und nicht contains: Rot, wenn ein Schluessel keine
        // Zeile hat (Menge zu klein) UND wenn eine Zeile zu einem Schluessel steht, den es
        // nicht mehr gibt (Menge zu gross). Ohne das rutscht im Spiel eine Spalte, und
        // kein Probenlauf ist rot.
        assertEquals(erwartet, aufgeschrieben);
    }
}
