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
                        "&7  02.11. 14:37  -25.000 Mark  Parzelle genommen"
                                + "  (Stand 25.000 Mark)"),

                // geteilt
                new Zeile(ParzellenSatz.BEZAHLT, 1L, Einsatz.von("betrag", sprache.betrag(25_000L)).mit("rest", sprache.betrag(75_000L)),
                        "&7  Bezahlt: 25.000 Mark · Rest: 75.000 Mark"),
                new Zeile(ParzellenSatz.RANG_WERBUNG, 1L, Einsatz.von("werbung", Wirtschaftswerte.VORGABE_RANG_WERBUNG),
                        "&eMehr erlaubt ein hoeherer Rang."),

                // /parzelle info
                new Zeile(ParzellenSatz.INFO_GRUPPENBODEN, 1L, Einsatz.keine(),
                        "&6Gruppenboden - war einmal Weg"),
                new Zeile(ParzellenSatz.INFO_WEG, 1L, Einsatz.keine(),
                        "&7Du stehst auf einem Weg - der gehoert allen."),
                new Zeile(ParzellenSatz.INFO_MAUER, 1L, Einsatz.keine(),
                        "&7Du stehst auf einer Mauer - die gehoert zur Strasse."),
                new Zeile(ParzellenSatz.INFO_KOPF, 1L, Einsatz.von("x", "3").mit("z", "-4"),
                        "&6Parzelle 3/-4"),
                new Zeile(ParzellenSatz.INFO_AUSSERHALB, 1L, Einsatz.keine(),
                        "&7  ausserhalb der Marktstadt - wird nicht vergeben"),
                new Zeile(ParzellenSatz.INFO_FREI, 1L, Einsatz.von("preis", sprache.betrag(25_000L)),
                        "&7  frei · &a25.000 Mark&8 · /parzelle nehmen"),
                new Zeile(ParzellenSatz.INFO_FREI_AM_LIMIT, 1L, Einsatz.von("preis", sprache.betrag(25_000L)),
                        "&7  frei · 25.000 Mark"),
                new Zeile(ParzellenSatz.INFO_AM_LIMIT, 1L, Einsatz.von("anzahl", "1"),
                        "&c  Dein Rang erlaubt 1 Parzelle, und so viele hast du schon."),
                new Zeile(ParzellenSatz.INFO_AM_LIMIT, 2L, Einsatz.von("anzahl", "2"),
                        "&c  Dein Rang erlaubt 2 Parzellen, und so viele hast du schon."),
                new Zeile(ParzellenSatz.INFO_AM_LIMIT, 0L, Einsatz.von("anzahl", "0"),
                        "&c  Dein Rang erlaubt 0 Parzellen, und so viele hast du schon."),
                new Zeile(ParzellenSatz.INFO_EIGENTUEMER, 1L, Einsatz.von("name", "Anna"),
                        "&7  Eigentuemer: &fAnna"),
                new Zeile(ParzellenSatz.INFO_GEKAUFT_FUER, 1L, Einsatz.von("preis", sprache.betrag(25_000L)),
                        "&7  Gekauft fuer: &f25.000 Mark"),
                new Zeile(ParzellenSatz.INFO_MITBAUER, 1L, Einsatz.von("namen", "Bert, Cara"),
                        "&7  Mitbauer: &fBert, Cara"),
                new Zeile(ParzellenSatz.INFO_GRUPPE, 1L, Einsatz.von("anzahl", "3"),
                        "&f  Teil einer Gruppe aus 3 Feldern"),
                new Zeile(ParzellenSatz.INFO_AUFTRENNEN_HINWEIS, 1L, Einsatz.keine(),
                        "&8  /parzelle auftrennen        dieses Feld herausloesen"),
                new Zeile(ParzellenSatz.INFO_ZUSAMMENLEGEN_HINWEIS, 1L, Einsatz.keine(),
                        "&8  /parzelle zusammenlegen <nord|ost|sued|west>"),

                // /parzelle nehmen
                new Zeile(ParzellenSatz.NEHMEN_ERTEILT, 1L, Einsatz.von("x", "3").mit("z", "-4"),
                        "&aParzelle 3/-4 gehoert jetzt dir"),
                new Zeile(ParzellenSatz.NEHMEN_ERSTE_GRATIS, 1L, Einsatz.keine(),
                        "&7  Die erste ist gratis."),
                new Zeile(ParzellenSatz.NEHMEN_KEIN_BAULAND, 1L, Einsatz.keine(),
                        "&cHier ist kein Bauland - stell dich auf eine Parzelle."),
                new Zeile(ParzellenSatz.NEHMEN_SCHON_VERGEBEN, 1L, Einsatz.von("name", "Anna"),
                        "&cDie gehoert schon &fAnna"),
                new Zeile(ParzellenSatz.NEHMEN_GRENZE, 1L, Einsatz.von("anzahl", "1"),
                        "&cDu hast schon 1 Parzelle - mehr erlaubt dein Rang nicht."),
                new Zeile(ParzellenSatz.NEHMEN_GRENZE, 2L, Einsatz.von("anzahl", "2"),
                        "&cDu hast schon 2 Parzellen - mehr erlaubt dein Rang nicht."),
                new Zeile(ParzellenSatz.NEHMEN_GRENZE, 0L, Einsatz.von("anzahl", "0"),
                        "&cDu hast schon 0 Parzellen - mehr erlaubt dein Rang nicht."),
                new Zeile(ParzellenSatz.NEHMEN_ZU_WENIG, 1L, Einsatz.von("betrag", sprache.betrag(25_000L)).mit("guthaben", sprache.betrag(1_000L)),
                        "&cDie kostet &625.000 Mark&c, du hast 1.000 Mark."),
                new Zeile(ParzellenSatz.NEHMEN_AUSSERHALB, 1L, Einsatz.von("von", "-8").mit("bis", "8"),
                        "&cHier endet die Marktstadt. Parzellen gibt es von -8/-8 bis 8"
                                + "/8."),
                new Zeile(ParzellenSatz.NEHMEN_SONST, 1L, Einsatz.von("bescheid", Besitzbuch.Bescheid.SCHON_IN_GRUPPE.name()),
                        "&cDas ging nicht: SCHON_IN_GRUPPE"),

                // /parzelle liste
                new Zeile(ParzellenSatz.LISTE_LEER, 1L, Einsatz.keine(),
                        "&eDu hast noch keine Parzelle. Die erste ist gratis - /parzell"
                                + "e nehmen"),
                new Zeile(ParzellenSatz.LISTE_KOPF, 1L, Einsatz.von("anzahl", "1").mit("hoechstzahl", "4"),
                        "&6Deine Parzellen (1 von 4)"),
                new Zeile(ParzellenSatz.LISTE_ZEILE, 1L, listenzeile(sprache, "0", "0", sprache.wort(ParzellenSatz.GRATIS, Einsatz.keine()), "", ""),
                        "&7  0/0 · gratis"),
                new Zeile(ParzellenSatz.LISTE_ZEILE, 1L, listenzeile(sprache, "1", "0", sprache.betrag(25_000L),
                        sprache.wort(ParzellenSatz.LISTE_MITBAUER,
                                Einsatz.von("anzahl", "1")), ""),
                        "&7  1/0 · 25.000 Mark · 1 Mitbauer"),
                new Zeile(ParzellenSatz.LISTE_ZEILE, 1L, listenzeile(sprache, "2", "0", sprache.betrag(50_000L), "",
                        sprache.wort(ParzellenSatz.LISTE_GRUPPE, Einsatz.keine())),
                        "&7  2/0 · 50.000 Mark · Gruppe"),
                new Zeile(ParzellenSatz.LISTE_NAECHSTE, 1L, Einsatz.von("betrag", sprache.betrag(25_000L)),
                        "&8Die naechste kostet 25.000 Mark"),

                // Die neunzehn Bescheide
                new Zeile(ParzellenSatz.BESCHEID_ERTEILT, 1L, Einsatz.keine(),
                        "&cErledigt."),
                new Zeile(ParzellenSatz.BESCHEID_KEINE_PARZELLE, 1L, Einsatz.keine(),
                        "&cHier ist keine Parzelle."),
                new Zeile(ParzellenSatz.BESCHEID_NOCH_FREI, 1L, Einsatz.keine(),
                        "&cDiese Parzelle gehoert noch niemandem - nimm sie mit /parzel"
                                + "le nehmen."),
                new Zeile(ParzellenSatz.BESCHEID_NOCH_FREI_AM_LIMIT, 1L, Einsatz.von("anzahl", "1"),
                        "&cDiese Parzelle gehoert noch niemandem - aber dein Rang erlau"
                                + "bt 1 Parzelle, und so viele hast du schon."),
                new Zeile(ParzellenSatz.BESCHEID_NOCH_FREI_AM_LIMIT, 2L, Einsatz.von("anzahl", "2"),
                        "&cDiese Parzelle gehoert noch niemandem - aber dein Rang erlau"
                                + "bt 2 Parzellen, und so viele hast du schon."),
                new Zeile(ParzellenSatz.BESCHEID_NOCH_FREI_AM_LIMIT, 0L, Einsatz.von("anzahl", "0"),
                        "&cDiese Parzelle gehoert noch niemandem - aber dein Rang erlau"
                                + "bt 0 Parzellen, und so viele hast du schon."),
                new Zeile(ParzellenSatz.BESCHEID_SCHON_VERGEBEN, 1L, Einsatz.keine(),
                        "&cDie gehoert schon jemandem."),
                new Zeile(ParzellenSatz.BESCHEID_AUSSERHALB, 1L, Einsatz.keine(),
                        "&cDas liegt ausserhalb der Marktstadt."),
                new Zeile(ParzellenSatz.BESCHEID_GRENZE_ERREICHT, 1L, Einsatz.von("anzahl", "1"),
                        "&cDein Rang erlaubt 1 Parzelle, und so viele hast du schon."),
                new Zeile(ParzellenSatz.BESCHEID_GRENZE_ERREICHT, 2L, Einsatz.von("anzahl", "2"),
                        "&cDein Rang erlaubt 2 Parzellen, und so viele hast du schon."),
                new Zeile(ParzellenSatz.BESCHEID_GRENZE_ERREICHT, 0L, Einsatz.von("anzahl", "0"),
                        "&cDein Rang erlaubt 0 Parzellen, und so viele hast du schon."),
                new Zeile(ParzellenSatz.BESCHEID_ZU_WENIG_MARK, 1L, Einsatz.keine(),
                        "&cDein Guthaben reicht nicht."),
                new Zeile(ParzellenSatz.BESCHEID_NICHT_EIGENTUEMER, 1L, Einsatz.keine(),
                        "&cDas darf nur der Eigentuemer."),
                new Zeile(ParzellenSatz.BESCHEID_SCHON_EINGETRAGEN, 1L, Einsatz.keine(),
                        "&cSteht schon auf der Liste."),
                new Zeile(ParzellenSatz.BESCHEID_NICHT_EINGETRAGEN, 1L, Einsatz.keine(),
                        "&cStand gar nicht auf der Liste."),
                new Zeile(ParzellenSatz.BESCHEID_TEIL_EINER_GRUPPE, 1L, Einsatz.keine(),
                        "&cDie Parzelle gehoert zu einer Gruppe - das geht nur ueber di"
                                + "e ganze Gruppe."),
                new Zeile(ParzellenSatz.BESCHEID_NACHBAR_FREI, 1L, Einsatz.keine(),
                        "&cDort drueben ist keine Parzelle vergeben."),
                new Zeile(ParzellenSatz.BESCHEID_NACHBAR_FREMD, 1L, Einsatz.keine(),
                        "&cDie Nachbarparzelle gehoert jemand anderem."),
                new Zeile(ParzellenSatz.BESCHEID_SCHON_IN_GRUPPE, 1L, Einsatz.keine(),
                        "&cDie beiden gehoeren zu zwei verschiedenen Grundstuecken. Tre"
                                + "nne eines auf und haenge die Felder einzeln an."),
                new Zeile(ParzellenSatz.BESCHEID_SCHON_ZUSAMMEN, 1L, Einsatz.keine(),
                        "&cDie beiden sind schon dasselbe Grundstueck."),
                new Zeile(ParzellenSatz.BESCHEID_GRUPPE_ZU_GROSS, 1L, Einsatz.von("anzahl", "1"),
                        "&cDein Rang erlaubt hoechstens 1 Feld je Gruppe."),
                new Zeile(ParzellenSatz.BESCHEID_GRUPPE_ZU_GROSS, 2L, Einsatz.von("anzahl", "2"),
                        "&cDein Rang erlaubt hoechstens 2 Felder je Gruppe."),
                new Zeile(ParzellenSatz.BESCHEID_GRUPPE_ZU_GROSS, 0L, Einsatz.von("anzahl", "0"),
                        "&cDein Rang erlaubt hoechstens 0 Felder je Gruppe."),
                new Zeile(ParzellenSatz.BESCHEID_KEINE_GRUPPE, 1L, Einsatz.keine(),
                        "&cHier ist keine Gruppe."),
                new Zeile(ParzellenSatz.BESCHEID_AUF_DEM_WEG, 1L, Einsatz.keine(),
                        "&cStell dich auf das Feld, das du meinst - nicht auf den Weg."),
                new Zeile(ParzellenSatz.BESCHEID_HAELT_ZUSAMMEN, 1L, Einsatz.keine(),
                        "&cDieses Feld haelt dein Grundstueck zusammen. Loese erst eine"
                                + "s am Rand heraus."),

                // /mark - Stand und Verlauf
                new Zeile(MarkSatz.STAND, 1L, Einsatz.von("betrag", sprache.betrag(25_000L)),
                        "&7Du hast &625.000 Mark&7."),
                new Zeile(MarkSatz.VERLAUF_HINWEIS, 1L, Einsatz.keine(),
                        "&8/mark verlauf zeigt die letzten Buchungen."),
                new Zeile(MarkSatz.VERLAUF_LEER, 1L, Einsatz.keine(),
                        "&7Noch keine Buchungen."),
                new Zeile(MarkSatz.VERLAUF_KOPF, 1L, Einsatz.keine(),
                        "&6Deine letzten Buchungen"),

                // Was mehrere Zweige von /parzelle teilen. Die vier Taten kommen
                // ueber Sprache.wort aus der Datei - damit haengen auch sie an
                // einer Bildprobe, obwohl ein Art.WORT selbst keine Zeile hat.
                new Zeile(ParzellenSatz.NICHT_IN_DER_MARKTSTADT, 1L, Einsatz.keine(),
                        "&cParzellen gibt es nur in der Marktstadt."),
                new Zeile(ParzellenSatz.STEHT_NICHT_AUF, 1L, Einsatz.von("x", "3").mit("z", "-4").mit("tat",
                        sprache.wort(ParzellenSatz.TAT_NICHTS_AUFGEGEBEN, Einsatz.keine())),
                        "&cDu stehst nicht auf Parzelle 3/-4. Nichts aufgegeben."),
                new Zeile(ParzellenSatz.STEHT_NICHT_AUF, 1L, Einsatz.von("x", "3").mit("z", "-4").mit("tat",
                        sprache.wort(ParzellenSatz.TAT_NICHTS_ZUSAMMENGELEGT, Einsatz.keine())),
                        "&cDu stehst nicht auf Parzelle 3/-4. Nichts zusammengelegt."),
                new Zeile(ParzellenSatz.STEHT_NICHT_AUF, 1L, Einsatz.von("x", "3").mit("z", "-4").mit("tat",
                        sprache.wort(ParzellenSatz.TAT_NICHTS_HERAUSGELOEST, Einsatz.keine())),
                        "&cDu stehst nicht auf Parzelle 3/-4. Nichts herausgeloest."),
                new Zeile(ParzellenSatz.STEHT_NICHT_AUF, 1L, Einsatz.von("x", "3").mit("z", "-4").mit("tat",
                        sprache.wort(ParzellenSatz.TAT_NICHTS_AUFGETRENNT, Einsatz.keine())),
                        "&cDu stehst nicht auf Parzelle 3/-4. Nichts aufgetrennt."),

                // /parzelle mitbauer
                new Zeile(ParzellenSatz.MITBAUER_KEINE_PARZELLE, 1L, Einsatz.keine(),
                        "&cHier ist keine vergebene Parzelle."),
                new Zeile(ParzellenSatz.MITBAUER_KOPF, 1L, Einsatz.von("x", "3").mit("z", "-4").mit("namen",
                        sprache.wort(ParzellenSatz.MITBAUER_NIEMAND, Einsatz.keine())),
                        "&6Mitbauer auf 3/-4: &fniemand"),
                new Zeile(ParzellenSatz.MITBAUER_BEFEHLE, 1L, Einsatz.keine(),
                        "&8/parzelle mitbauer hinzu <name> · weg <name>"),
                new Zeile(ParzellenSatz.MITBAUER_UNBEKANNT, 1L, Einsatz.keine(),
                        "&cDen kenne ich nicht. Er muss einmal auf dem Server gewesen sein."),
                new Zeile(ParzellenSatz.MITBAUER_HINZU_ERTEILT, 1L, Einsatz.von("name", "Bert"),
                        "&aBert darf hier jetzt bauen."),
                new Zeile(ParzellenSatz.MITBAUER_SCHON_DRIN, 1L, Einsatz.von("name", "Bert"),
                        "&eBert darf hier ohnehin bauen."),
                new Zeile(ParzellenSatz.MITBAUER_NIEMAND_MIT_DEM_NAMEN, 1L, Einsatz.keine(),
                        "&cSteht hier niemand mit dem Namen - /parzelle mitbauer zeigt die Liste."),
                new Zeile(ParzellenSatz.MITBAUER_WEG_ERTEILT, 1L, Einsatz.von("name", "Bert"),
                        "&aBert darf hier nicht mehr bauen."),
                new Zeile(ParzellenSatz.MITBAUER_NICHT_DRIN, 1L, Einsatz.von("name", "Bert"),
                        "&eBert stand gar nicht auf der Liste."),

                // /parzelle aufgeben - die Warnung ist EIN Satz mit drei Zeilen
                new Zeile(ParzellenSatz.AUFGEBEN_NICHT_DEINE, 1L, Einsatz.keine(),
                        "&cDas ist nicht deine Parzelle."),
                new Zeile(ParzellenSatz.AUFGEBEN_GRUPPE, 1L, Einsatz.keine(),
                        "&cDie Parzelle gehoert zu einer Gruppe - erst /parzelle auftrennen."),
                new Zeile(ParzellenSatz.AUFGEBEN_WARNUNG, 1L, Einsatz.von("x", "3").mit("z", "-4"),
                        "&eParzelle 3/-4 aufgeben?\n&7  Es gibt kein Geld zurueck, und gebaut blei"
                                + "bt stehen.\n&c  /parzelle aufgeben bestaetigen 3 -4"),
                new Zeile(ParzellenSatz.AUFGEBEN_ERTEILT, 1L, Einsatz.von("x", "3").mit("z", "-4"),
                        "&aParzelle 3/-4 ist wieder frei."),

                // /parzelle zusammenlegen - die Warnung ist EIN Satz mit fuenf Zeilen
                new Zeile(ParzellenSatz.ZUSAMMENLEGEN_WARNUNG, 1L, Einsatz.von("x", "3").mit("z", "-4").mit("nachbar-x", "3")
                        .mit("nachbar-z", "-5")
                        .mit("betrag", sprache.betrag(10_000L))
                        .mit("guthaben", sprache.betrag(25_000L)),
                        "&eParzelle 3/-4 mit 3/-5 zusammenlegen?\n&7  Gebuehr: &610.000 Mark&7 · d"
                                + "u hast 25.000 Mark\n"
                                        + "&7  Der Weg dazwischen wird dein Bauland.\n&7"
                                + "  Beim Auftrennen kommt er zurueck - und raeumt alles weg, was da"
                                + "nn\n&7  darauf steht."),
                new Zeile(ParzellenSatz.ZUSAMMENLEGEN_ZU_WENIG, 1L, Einsatz.keine(),
                        "&c  Dein Guthaben reicht nicht."),
                new Zeile(ParzellenSatz.ZUSAMMENLEGEN_BESTAETIGEN, 1L, Einsatz.von("richtung", Richtung.NORD.wort()).mit("x", "3")
                        .mit("z", "-4"),
                        "&e  /parzelle zusammenlegen nord bestaetigen 3 -4"),
                new Zeile(ParzellenSatz.ZUSAMMENLEGEN_ERTEILT, 1L, Einsatz.von("anzahl", "2"),
                        "&aEin Grundstueck aus 2 Feldern."),

                // /parzelle auftrennen - ganz und feldweise
                new Zeile(ParzellenSatz.AUFTRENNEN_ALLES_WARNUNG, 1L, Einsatz.von("anzahl", "3"),
                        "&eGrundstueck aus 3 Feldern ganz auftrennen?\n&7  ALLE Wege zwischen den "
                                + "Feldern kommen zurueck. Geld gibt es keines zurueck.\n"
                                        + "&7  Fuer ei"
                                + "n einzelnes Feld: stell dich darauf und nimm /parzelle auftrennen"
                                + " ohne \"alles\"."),
                new Zeile(ParzellenSatz.AUFTRENNEN_ALLES_BESTAETIGEN, 1L, Einsatz.von("x", "3").mit("z", "-4"),
                        "&c  /parzelle auftrennen alles bestaetigen 3 -4"),
                new Zeile(ParzellenSatz.AUFTRENNEN_ERTEILT, 1L, Einsatz.von("anzahl", "3"),
                        "&aAufgetrennt - 3 eigenstaendige Parzellen."),
                new Zeile(ParzellenSatz.AUFTRENNEN_WEG_KOMMT_ZURUECK, 1L, Einsatz.keine(),
                        "&7  Der Weg dorthin kommt zurueck. Geld gibt es keines zurueck."),
                new Zeile(ParzellenSatz.AUFTRENNEN_FELD_WARNUNG_ZWEI, 1L, Einsatz.von("anzahl", "2"),
                        "&eGrundstueck aus 2 Feldern auftrennen?\n&7  Es hat nur zwei Felder - ein"
                                + "es allein geht nicht heraus, also wird\n"
                                        + "&7  es ganz aufgetrennt."),
                new Zeile(ParzellenSatz.AUFTRENNEN_FELD_WARNUNG, 1L, Einsatz.von("x", "3").mit("z", "-4").mit("anzahl", "3"),
                        "&eDas Feld 3/-4 aus dem Grundstueck herausloesen?\n&7  Die anderen 3 Feld"
                                + "er bleiben ein Grundstueck."),
                new Zeile(ParzellenSatz.AUFTRENNEN_FELD_BESTAETIGEN, 1L, Einsatz.von("x", "3").mit("z", "-4"),
                        "&c  /parzelle auftrennen bestaetigen 3 -4"),
                new Zeile(ParzellenSatz.AUFTRENNEN_FELD_ERTEILT, 1L, Einsatz.von("x", "3").mit("z", "-4").mit("anzahl", "3"),
                        "&aFeld 3/-4 herausgeloest - das Grundstueck hat noch 3 Felder."),

                // Die Abschaetzung. Die drei gezaehlten Saetze brauchen je drei
                // Zeilen - eins, viele und die NULL. Bei 0 sendet der Sendeort gar
                // nichts; dass der Satz selbst dann die Mehrzahlform mit einer 0
                // ergaebe, haelt diese Zeile fest.
                new Zeile(ParzellenSatz.AUFWAND_VERSCHWINDET, 1L, Einsatz.von("anzahl", "1"),
                        "&c  Dabei verschwindet 1 Block von der Flaeche."),
                new Zeile(ParzellenSatz.AUFWAND_VERSCHWINDET, 2L, Einsatz.von("anzahl", "2"),
                        "&c  Dabei verschwinden 2 Bloecke von der Flaeche."),
                new Zeile(ParzellenSatz.AUFWAND_VERSCHWINDET, 0L, Einsatz.von("anzahl", "0"),
                        "&c  Dabei verschwinden 0 Bloecke von der Flaeche."),
                new Zeile(ParzellenSatz.AUFWAND_VERFUELLT, 1L, Einsatz.von("anzahl", "1"),
                        "&7  1 Fehlstelle wird wieder gefuellt."),
                new Zeile(ParzellenSatz.AUFWAND_VERFUELLT, 2L, Einsatz.von("anzahl", "2"),
                        "&7  2 Fehlstellen werden wieder gefuellt."),
                new Zeile(ParzellenSatz.AUFWAND_VERFUELLT, 0L, Einsatz.von("anzahl", "0"),
                        "&7  0 Fehlstellen werden wieder gefuellt."),
                new Zeile(ParzellenSatz.AUFWAND_NICHTS, 1L, Einsatz.keine(),
                        "&7  Auf der Wegflaeche steht nichts - es geht nichts verloren."),
                new Zeile(ParzellenSatz.ABKLINGZEIT, 1L, Einsatz.von("anzahl", "1"),
                        "&7Einen Augenblick - die Abschaetzung liest jeden Block ueber dem Weg. No"
                                + "ch 1 Sekunde."),
                new Zeile(ParzellenSatz.ABKLINGZEIT, 2L, Einsatz.von("anzahl", "2"),
                        "&7Einen Augenblick - die Abschaetzung liest jeden Block ueber dem Weg. No"
                                + "ch 2 Sekunden."),
                new Zeile(ParzellenSatz.ABKLINGZEIT, 0L, Einsatz.von("anzahl", "0"),
                        "&7Einen Augenblick - die Abschaetzung liest jeden Block ueber dem Weg. No"
                                + "ch 0 Sekunden."),

                // Die Bodenarbeit. Zwei Zeilen zu EINEM Schluessel, je eine fuer
                // eines der beiden Wortstuecke - sonst haengt eines von ihnen an
                // keiner Bildprobe.
                new Zeile(ParzellenSatz.BODEN_GELUNGEN, 1L, Einsatz.von("gelungen",
                        sprache.wort(ParzellenSatz.BODEN_WEG_IST_BAULAND, Einsatz.keine())).mit("anzahl", "623"),
                        "&7  Der Weg dazwischen ist Bauland (623 Bloecke geaendert)."),
                new Zeile(ParzellenSatz.BODEN_GELUNGEN, 1L, Einsatz.von("gelungen",
                        sprache.wort(ParzellenSatz.BODEN_WEG_IST_ZURUECK, Einsatz.keine())).mit("anzahl", "448"),
                        "&7  Der Weg ist zurueck (448 Bloecke geaendert)."),
                new Zeile(ParzellenSatz.BODEN_FEHLER, 1L, Einsatz.keine(),
                        "&c  Der Boden liess sich nicht aendern. Die Parzellen stimmen - sag einem"
                                + " Operator Bescheid."),

                // Bauschritt 3: /tpa, /tpahere, /tpaccept, /tpdeny. ANNA fragt, BERT
                // wird gefragt - zwei verschiedene Namen, weil jeder Satz dieses
                // Bereichs den des ANDEREN traegt.
                new Zeile(AnfragenSatz.NIEMAND, 1L, Einsatz.keine(),
                        "&7Es fragt niemand."),
                new Zeile(AnfragenSatz.FRAGE_NICHT_DA, 1L, Einsatz.von("name", "Bert"),
                        "&cBert ist nicht da."),
                new Zeile(AnfragenSatz.FRAGE_BEI_DIR, 1L, Einsatz.keine(),
                        "&7Du bist schon bei dir."),
                new Zeile(AnfragenSatz.FRAGE_STEHT_NOCH, 1L, Einsatz.von("name", "Bert"),
                        "&7Diese Anfrage an Bert steht noch."),
                new Zeile(AnfragenSatz.FRAGE_GESTELLT, 1L, Einsatz.von("name", "Bert"),
                        "&7Angefragt bei Bert - er oder sie muss zustimmen."),
                new Zeile(AnfragenSatz.FRAGE_EINGELADEN, 1L, Einsatz.von("name", "Anna"),
                        "&6Anna laedt dich ein zu kommen."),
                new Zeile(AnfragenSatz.FRAGE_MOECHTE_KOMMEN, 1L,
                        Einsatz.von("name", "Anna"),
                        "&6Anna moechte zu dir kommen."),
                new Zeile(AnfragenSatz.FRAGE_HINWEIS, 1L, Einsatz.keine(),
                        "&7/tpaccept zum Annehmen, /tpdeny zum Ablehnen. Die Anfrage laeuft nach einer Minute ab."),
                new Zeile(AnfragenSatz.ANNEHMEN_FRAGENDER_WEG, 1L, Einsatz.keine(),
                        "&cWer gefragt hatte, ist nicht mehr da."),
                new Zeile(AnfragenSatz.ANNEHMEN_DU_KOMMST, 1L, Einsatz.von("name", "Anna"),
                        "&aDu kommst zu Anna."),
                new Zeile(AnfragenSatz.ANNEHMEN_ER_KOMMT, 1L, Einsatz.von("name", "Anna"),
                        "&aAnna kommt zu dir."),
                new Zeile(AnfragenSatz.ANNEHMEN_QUITTUNG_DU_KOMMST, 1L,
                        Einsatz.von("name", "Bert"),
                        "&aBert hat angenommen - du kommst."),
                new Zeile(AnfragenSatz.ANNEHMEN_QUITTUNG_ER_KOMMT, 1L,
                        Einsatz.von("name", "Bert"),
                        "&aBert hat angenommen und kommt zu dir."),
                // Die Zahlen sind NACKT, ohne Tausenderpunkt - darum steht in der
                // Mehrzahlform die 1000 und nicht noch eine 2.
                new Zeile(AnfragenSatz.ANNEHMEN_WEITERE, 1L, Einsatz.von("anzahl", "1"),
                        "&7Es warten noch 1 Anfrage."),
                new Zeile(AnfragenSatz.ANNEHMEN_WEITERE, 2L, Einsatz.von("anzahl", "1000"),
                        "&7Es warten noch 1000 Anfragen."),
                new Zeile(AnfragenSatz.ANNEHMEN_WEITERE, 0L, Einsatz.von("anzahl", "0"),
                        "&7Es warten noch 0 Anfragen."),
                new Zeile(AnfragenSatz.ABLEHNEN_ABGELEHNT, 1L, Einsatz.von("anzahl", "1"),
                        "&71 Anfrage abgelehnt."),
                new Zeile(AnfragenSatz.ABLEHNEN_ABGELEHNT, 2L,
                        Einsatz.von("anzahl", "1000"),
                        "&71000 Anfragen abgelehnt."),
                new Zeile(AnfragenSatz.ABLEHNEN_ABGELEHNT, 0L, Einsatz.von("anzahl", "0"),
                        "&70 Anfragen abgelehnt."),
                // /freimarken spielzeit - Einzug, Spaltenbreite und Farbe stehen seit dem
                // 04.11.2026 IM Satz. Die erwarteten Bilder kommen aus dem ROTEN Lauf von
                // SpielzeitBefehleTest 33a-33h, der VOR dem Umzug gegen den committeten
                // Stand lief. Die eingesetzten Werte holt diese Tafel ueber Sprache#wort
                // und Sprache#muster aus derselben Datei - damit haengen auch die acht
                // Art.WORT und das Art.MUSTER dieses Bereichs an einer Bildprobe.,
                new Zeile(SpielzeitSatz.KOPF_SELBST, 1L,
                        Einsatz.keine(),
                        "&6Deine Spielzeit"),
                new Zeile(SpielzeitSatz.KOPF_FREMD, 1L,
                        Einsatz.von("name", "Anna"),
                        "&6Spielzeit von Anna"),
                new Zeile(SpielzeitSatz.HEUTE, 1L,
                        Einsatz.von("dauer", sprache.wort(SpielzeitSatz.DAUER_MINUTEN,
                                Einsatz.von("minuten", "10")))
                        .mit("tag", sprache.muster(SpielzeitSatz.TAG_MUSTER)
                                .format(LocalDate.of(2026, 11, 3)))
                        .mit("zone", Zeitwerte.VORGABE_ZONE.getId()),
                        "&7  heute   10 min   (03.11.2026, Tagesgrenze Europe/Berlin)"),
                new Zeile(SpielzeitSatz.GESAMT, 1L,
                        Einsatz.von("dauer", sprache.wort(SpielzeitSatz.DAUER_MINUTEN,
                                Einsatz.von("minuten", "10"))),
                        "&7  gesamt  10 min"),
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
                        "&7  taetig  6 min   (4 min heute, 60 % der Zeit)"),
                new Zeile(SpielzeitSatz.ZUSTAND_UNTAETIG, 1L,
                        Einsatz.von("dauer", sprache.wort(SpielzeitSatz.DAUER_MINUTEN,
                                Einsatz.von("minuten", "12")))
                        .mit("grenze", "10"),
                        "&7  wird gerade NICHT gezaehlt - untaetig seit 12 min, gezaehlt wird bis 10 min. Ein Schritt oder ein Tastendruck zaehlt wieder."),
                new Zeile(SpielzeitSatz.ZUSTAND_GEZAEHLT, 1L,
                        Einsatz.von("menge", sprache.wort(SpielzeitSatz.DAUER_SEKUNDEN,
                                Einsatz.von("sekunden", "0")))
                        .mit("abstand", sprache.wort(
                                SpielzeitSatz.ZUSTAND_ABSTAND_TAKT,
                                Einsatz.von("takt", "60")))
                        .mit("abwesenheit", sprache.wort(
                                SpielzeitSatz.ZUSTAND_ABWESENHEIT,
                                Einsatz.von("pfad", Zeitwerte.PFAD_UNTAETIG))),
                        "&7  wird gezaehlt - die letzten 0 s sind noch nicht geschrieben, das geschieht alle 60 s. Abwesenheit zaehlt auf diesem Server mit (zeit.untaetig-minuten: 0)."),
                new Zeile(SpielzeitSatz.HINWEIS, 1L,
                        Einsatz.keine(),
                        "&7  Gezaehlt wird erst seit dem Einbau dieser Messung - Spielstunden von davor stehen nirgends, jeder faengt bei 0 an."),
                new Zeile(SpielzeitSatz.NICHT_LESBAR, 1L,
                        Einsatz.keine(),
                        "&7  Die Spielzeit ist gerade nicht lesbar - gezaehlt wird trotzdem. Sag einem Operator Bescheid."),
                new Zeile(SpielzeitSatz.KEINE_KONSOLE, 1L,
                        Einsatz.keine(),
                        "&7Die Konsole spielt nicht. /freimarken spielzeit <spieler> nennt die Zeit eines anwesenden Spielers."),
                new Zeile(SpielzeitSatz.NICHT_DA, 1L,
                        Einsatz.von("name", "Anna"),
                        "&cAnna ist nicht da. Gezeigt werden kann nur die Spielzeit eines anwesenden Spielers."),

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
                        "&7Du bist schon in kornmark."),
                new Zeile(ReiseSatz.KEIN_PLATZ_AM_ERSCHEINUNGSPUNKT, 1L,
                        Einsatz.von("ziel", "kornmark"),
                        "&cIn kornmark ist am Erscheinungspunkt kein Platz zum Stehen. Das ist nichts, was du beheben kannst - melde es."),
                // Die fuenf Hindernisse. Zwei von ihnen unterscheiden sich in GENAU
                // EINEM Wort (Welthoehe gegen Weltgrenze), und eines sagt "nicht MEHR"
                // statt "nicht" - drei Stellen, an denen ein Abschreiben ohne Zeugen
                // stillschweigend angleicht.
                new Zeile(ReiseSatz.HINDERNIS_WELT_NICHT_GELADEN, 1L, Einsatz.keine(),
                        "&cDorthin geht es nicht mehr - die Welt ist nicht geladen."),
                new Zeile(ReiseSatz.HINDERNIS_ZU_PFERD, 1L, Einsatz.keine(),
                        "&cNicht zu Pferd - absteigen, dann reisen. Das Reittier bleibt zurueck."),
                new Zeile(ReiseSatz.HINDERNIS_AUSSERHALB_DER_WELTHOEHE, 1L,
                        Einsatz.keine(),
                        "&cDorthin geht es nicht - der Ort liegt ausserhalb der Welthoehe."),
                new Zeile(ReiseSatz.HINDERNIS_AUSSERHALB_DER_WELTGRENZE, 1L,
                        Einsatz.keine(),
                        "&cDorthin geht es nicht - der Ort liegt ausserhalb der Weltgrenze."),
                new Zeile(ReiseSatz.HINDERNIS_KEIN_PLATZ_MEHR, 1L, Einsatz.keine(),
                        "&cDort ist kein Platz zum Stehen mehr."),
                new Zeile(ReiseSatz.LEINE_REISST, 1L, Einsatz.keine(),
                        "&eAngeleinte Tiere bleiben zurueck - eine Leine reisst beim Weltwechsel."),
                new Zeile(ReiseSatz.NICHT_GEKLAPPT, 1L, Einsatz.von("ziel", "Bert"),
                        "&cDie Reise nach Bert hat nicht geklappt."),
                // Die Abklingzeit ist der EINZIGE gezaehlte Satz dieses Bereichs. Die
                // Mehrzahlform steht mit der DREI da und nicht mit einer groesseren
                // Zahl: ZWISCHEN_ZWEI_REISEN sind drei Sekunden, mehr kann der
                // aufgerundete Rest nicht sein. Die Null kann im Spiel nicht ankommen -
                // ihre Wache sitzt in Reise.zuSchnellHintereinander -, aber sie steht
                // hier, weil Probe 11b sie verlangt und weil sie festhaelt, was eine 0
                // ergaebe: die Mehrzahlform, und nicht etwa eine erfundene dritte.
                new Zeile(ReiseSatz.ABKLINGZEIT, 1L, Einsatz.von("anzahl", "1"),
                        "&7Einen Augenblick - noch 1 Sekunde."),
                new Zeile(ReiseSatz.ABKLINGZEIT, 2L, Einsatz.von("anzahl", "3"),
                        "&7Einen Augenblick - noch 3 Sekunden."),
                new Zeile(ReiseSatz.ABKLINGZEIT, 0L, Einsatz.von("anzahl", "0"),
                        "&7Einen Augenblick - noch 0 Sekunden."),
                // Die Wartezeit traegt ZWEI Werte. Die Sekunden kommen aus
                // Aufwaermzeit.DAUER und nicht aus der Datei - darum stehen sie hier als
                // eingesetzter Wert und nicht als Wort im erwarteten Bild.
                new Zeile(ReiseSatz.WARTEZEIT, 1L,
                        Einsatz.von("sekunden", "3").mit("ziel", "Bert"),
                        "&7Nach 3 Sekunden geht es nach Bert - bis dahin stehen bleiben."),
                new Zeile(ReiseSatz.ABBRUCH_BEWEGT, 1L, Einsatz.von("ziel", "Bert"),
                        "&cBewegt - die Reise nach Bert ist abgebrochen."),
                new Zeile(ReiseSatz.ABBRUCH_SCHADEN, 1L, Einsatz.von("ziel", "Bert"),
                        "&cSchaden genommen - die Reise nach Bert ist abgebrochen."),

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
                        "&6Reiseziele\n&bmarktstadt  kornmark  nether\n&7/warp <ziel> - "
                                + "oder durch ein Tor am Bundesmarkt."),
                // {eingabe} ist der GETIPPTE Name. Hier steht ein Farbtag darin, und
                // genau das ist die Zusicherung: Er kommt als TEXT an. Wuerde der Wert
                // vor dem Parsen in die Zeichenkette gespleisst, stuende hier ein roter
                // Name ohne die Zeichen - und ein Spieler koennte seinen eigenen Chat
                // faerben.
                new Zeile(ReiseSatz.WARP_KEIN_ZIEL, 1L,
                        Einsatz.von("eingabe", "<red>boese</red>")
                        .mit("liste", "marktstadt, kornmark, nether"),
                        "&cDorthin geht keine Reise: <red>boese</red>\n&7Moeglich sind: "
                                + "marktstadt, kornmark, nether"),
                // KLEIN geschrieben, und das ist Absicht: Hier kommt der AUFGELOESTE Name
                // an und nicht die Eingabe. Dass der Zweig wirklich den aufgeloesten
                // uebergibt, haelt AufrufstelleTest 34r mit /warp KORNMARK.
                new Zeile(ReiseSatz.WARP_WELT_FEHLT, 1L, Einsatz.von("welt", "kornmark"),
                        "&cDie Welt kornmark gibt es gerade nicht."),
                new Zeile(ReiseSatz.ZURUECK_KEIN_ORT_GEMERKT, 1L, Einsatz.keine(),
                        "&7Es ist kein Ort gemerkt - du bist noch nirgends gewesen, wohin"
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
                        "&eWeg und Mauer gehoeren allen"),
                new Zeile(WacheSatz.SCHUTZ_PARZELLE_FREI, 1L, Einsatz.keine(),
                        "&eDiese Parzelle ist frei - /parzelle nehmen"),
                new Zeile(WacheSatz.SCHUTZ_AUSSERHALB, 1L, Einsatz.keine(),
                        "&eHier endet die Marktstadt"),
                // Der Farbwechsel MITTEN im Satz, und das Leerzeichen davor: Es
                // gehoert zum roten Stueck. Hinter dem Wechsel saehe der Spieler
                // dasselbe, diese Zeile aber nicht - genau dafuer vergleicht sie
                // die Alt-Serialisierung und nicht den Baum.
                new Zeile(WacheSatz.SCHUTZ_FREMDE_PARZELLE, 1L,
                        Einsatz.von("eigentuemer", "Bert"),
                        "&cDiese Parzelle gehoert &fBert"),

                // Teil C: die drei Handbalken des Ladenschutzes. DERSELBE Aufbau wie
                // fremde-parzelle darueber - Absage rot, der fremde Name weiss: Ein
                // Spieler soll die zwei als dieselbe Art von Auskunft lesen.
                //
                // BEIDE Werte in der ersten Zeile, und der Name des Standes steht
                // VORN: In einer Markthalle stehen die Staende nebeneinander, und wer
                // nur "der Laden von Anna" liest, raet, an welchem er gerade greift.
                new Zeile(WacheSatz.SCHUTZ_FREMDER_LADEN, 1L,
                        Einsatz.von("laden", "halle-1").mit("mieter", "Anna"),
                        "&challe-1 ist der Laden von &fAnna"),
                // GELB wie parzelle-frei und mit dem Weg darin: Hier darf AUCH niemand
                // bauen, aber es gibt etwas zu tun. Eine Absage ohne Ausweg liest sich
                // wie ein Fehler.
                new Zeile(WacheSatz.SCHUTZ_LADEN_FREI, 1L, Einsatz.keine(),
                        "&eDieser Stand ist frei - /laden mieten"),
                // ROT und OHNE Behauptung darueber, wem der Stand gehoert: Bei einer
                // stummen Datenbank weiss das niemand, und beide anderen Zeilen waeren
                // erfunden.
                new Zeile(WacheSatz.SCHUTZ_LADEN_GESTOERT, 1L, Einsatz.keine(),
                        "&cDie Markthalle ist gerade nicht nachsehbar - versuch es noch"
                                + " einmal."),
                // ROT und OHNE Ausweg, anders als laden-frei: Diese Kiste ist nicht zu
                // mieten und nicht freizugeben. Ein "frag das Team" stuende hier als
                // Aufforderung, die das Team nicht erfuellen kann.
                new Zeile(WacheSatz.SCHUTZ_EINRICHTUNG, 1L, Einsatz.keine(),
                        "&cDiese Kiste gehoert zur Stadt"),

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
                        "&7Hier wird kein Spawnpunkt gesetzt - gestorben wird immer nach"
                                + " Altfurt. Schlafen geht weiter."),
                new Zeile(WacheSatz.ANKUNFT_ABENTEUER, 1L, Einsatz.keine(),
                        "&7Hier ist Abenteuer: ansehen und benutzen, aber nicht abbauen."
                                + " Gebaut wird in der Marktstadt."),
                // Und der Gegenfall: ZWEI Chatzeilen aus EINEM Schluessel, rot und
                // grau. Der Schwanz " - oder durch ein Tor am Bundesmarkt." steht
                // damit zum zweiten Mal in dieser Tabelle - oben in
                // ReiseSatz.WARP_ZIELE. Die zwei duerfen ab jetzt auseinanderlaufen;
                // bis zu diesem Zug kam dieser hier aus dem Programm, und darum trug
                // warp.ziele die Marke HALB IN KRAFT (Torhinweis).
                new Zeile(WacheSatz.PORTAL_KEIN_AUSGANG, 1L, Einsatz.keine(),
                        "&cHier fuehrt kein Portal hinaus.\n&7Nether und Ende erreichst"
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
                        "&6Der Freimarkenbund"),
                new Zeile(BundSatz.STAND_VERSION, 1L, Einsatz.von("version", "0.1.0")
                        .mit("java", "25").mit("minecraft", "1.21.4"),
                        "&7Stand 0.1.0 · Java 25 · 1.21.4"),
                new Zeile(BundSatz.STAND_ABLAGE_FLUECHTIG, 1L, Einsatz.keine(),
                        "&cAblage fluechtig - nichts ueberlebt den Neustart"),
                new Zeile(BundSatz.STAND_ABLAGE_MARIADB, 1L, Einsatz.keine(),
                        "&8Ablage MariaDB"),
                // Die zwei Zeilen mit woertlichen spitzen Klammern. In der Vorlage
                // stehen sie maskiert; ankommen muss die blanke Klammer.
                new Zeile(BundSatz.STAND_BEFEHLE, 1L, Einsatz.keine(),
                        "&7/parzelle · /mark · /freimarken preis <anzahl>"
                                + " · /freimarken spielzeit"),
                new Zeile(BundSatz.STAND_BEFEHLE_VERWALTUNG, 1L, Einsatz.keine(),
                        "&8/freimarken welt · parzelle <x> <z> · neuladen"),
                new Zeile(BundSatz.PREIS_GRATIS, 1L, Einsatz.keine(),
                        "&aDie erste Parzelle ist gratis."),
                // Der zweite Farbwechsel MITTEN im Satz nach der Schutzwache - und
                // der erste, bei dem die Farbe danach wieder zurueckschlaegt: grau,
                // gold, grau. Das Leerzeichen gehoert zum grauen Kopf, der Punkt
                // dahinter wieder dem grauen Schwanz.
                new Zeile(BundSatz.PREIS_KOSTET, 1L, Einsatz.von("nummer", "2")
                        .mit("betrag", sprache.betrag(25_000L)),
                        "&7Parzelle Nr. 2 kostet &625.000 Mark&7."),
                new Zeile(BundSatz.PREIS_AUSGEGEBEN, 1L,
                        Einsatz.von("betrag", sprache.betrag(25_000L)),
                        "&8Bisher ausgegeben: 25.000 Mark"),
                // --- /beruf ------------------------------------------------------
                // Die Berufsnamen kommen aus beruf.name.<schluessel> und werden hier als
                // fertiges Wort eingesetzt - genau so, wie Beruftext es tut. Der
                // Schluessel erscheint nur in der Befehlszeile der Warnung.
                new Zeile(BerufSatz.LISTE_KOPF, 1L, Einsatz.keine(),
                        "&6Berufe im Freimarkenbund"),
                new Zeile(BerufSatz.LISTE_BERUF, 1L, Einsatz.von("name", "Bergmann"),
                        "&7  &fBergmann"),
                new Zeile(BerufSatz.LISTE_BERUF_MIT, 1L, Einsatz.von("name", "Bergmann")
                        .mit("spezialisierungen", "Steinmetz"),
                        "&7  &fBergmann&7 · dazu: Steinmetz"),
                new Zeile(BerufSatz.LISTE_LEER, 1L, Einsatz.keine(),
                        "&7Es sind keine Berufe eingetragen."),
                // Die woertliche spitze Klammer - in der Vorlage maskiert, hier blank.
                new Zeile(BerufSatz.LISTE_FUSS, 1L, Einsatz.keine(),
                        "&7  /beruf waehlen <beruf> · /beruf stand"),
                new Zeile(BerufSatz.STAND_KOPF, 1L, Einsatz.keine(),
                        "&6Dein Beruf"),
                new Zeile(BerufSatz.STAND_KEINER, 1L, Einsatz.keine(),
                        "&7Du hast noch keinen Beruf gewaehlt."),
                new Zeile(BerufSatz.STAND_GRUNDBERUF, 1L, Einsatz.von("name", "Bergmann"),
                        "&7  &fBergmann"),
                new Zeile(BerufSatz.STAND_SPEZIALISIERUNG, 1L,
                        Einsatz.von("name", "Steinmetz").mit("grundberuf", "Bergmann"),
                        "&7  &fSteinmetz&7 · baut auf Bergmann auf"),
                // Die zwei Zeilen halten mit sieben und mit einem Leerzeichen dieselbe
                // Spalte. Die Zahl steht in der DATEI und nicht im Programm - wie bei
                // spielzeit.heute und spielzeit.gesamt. Zwei Zeilen muessen gemeinsam
                // geaendert werden, und nur diese Bildprobe faengt es, wenn nicht.
                new Zeile(BerufSatz.STAND_PLAETZE_GRUNDBERUFE, 1L,
                        Einsatz.von("gehalten", "1").mit("erlaubt", "2"),
                        "&7  Grundberufe:       1 von 2"),
                new Zeile(BerufSatz.STAND_PLAETZE_SPEZIALISIERUNGEN, 1L,
                        Einsatz.von("gehalten", "0").mit("erlaubt", "1"),
                        "&7  Spezialisierungen: 0 von 1"),
                new Zeile(BerufSatz.STAND_FRIST, 1L,
                        Einsatz.von("dauer", "23 h 00 min"),
                        "&e  Du kannst in 23 h 00 min wieder wechseln."),
                new Zeile(BerufSatz.STAND_GEBUEHR, 1L,
                        Einsatz.von("betrag", sprache.betrag(5_000L)),
                        "&7  Ein Beruf aufgeben kostet 5.000 Mark."),
                new Zeile(BerufSatz.GEWAEHLT, 1L, Einsatz.von("name", "Bergmann"),
                        "&aDu bist jetzt Bergmann."),
                // Farbwechsel mitten im Satz und zurueck: grau, gold, grau.
                new Zeile(BerufSatz.VERLASSEN, 1L, Einsatz.von("name", "Bergmann")
                        .mit("betrag", sprache.betrag(5_000L)),
                        "&7Du hast Bergmann aufgegeben. Gebucht: &65.000 Mark&7."),
                // Drei Zeilen in EINEM Satz, und der Beruf zweimal darin: als Wort im
                // Text und als Schluessel in der Befehlszeile zum Abschreiben.
                new Zeile(BerufSatz.VERLASSEN_WARNUNG, 1L, Einsatz.von("name", "Bergmann")
                        .mit("schluessel", "bergmann")
                        .mit("betrag", sprache.betrag(5_000L))
                        .mit("dauer", "24 h 00 min"),
                        "&eBergmann aufgeben?\n&7  Es kostet &65.000 Mark&7, und danach"
                                + " kannst du 24 h 00 min lang nicht wechseln.\n&c  /beruf"
                                + " verlassen bergmann bestaetigen"),
                // Die ROHE Eingabe, nicht kleingeschrieben: Der Spieler soll lesen, was
                // er getippt hat.
                new Zeile(BerufSatz.ABSAGE_UNBEKANNT, 1L,
                        Einsatz.von("eingabe", "Holzfaeler"),
                        "&cDen Beruf \"Holzfaeler\" gibt es nicht. /beruf liste zeigt"
                                + " alle."),
                new Zeile(BerufSatz.ABSAGE_SCHON_GEHALTEN, 1L,
                        Einsatz.von("name", "Bergmann"),
                        "&7Du bist schon Bergmann."),
                new Zeile(BerufSatz.ABSAGE_NICHT_GEHALTEN, 1L,
                        Einsatz.von("name", "Bergmann"),
                        "&7Du bist nicht Bergmann."),
                // Die Zahl in der Klammer und nicht an einem gezaehlten Hauptwort: So
                // liest sich der Satz auch bei 1 richtig, ohne zweite Dateizeile.
                new Zeile(BerufSatz.ABSAGE_GRUNDBERUFE_VOLL, 1L,
                        Einsatz.von("erlaubt", "2"),
                        "&cDu hast keinen Platz mehr fuer einen Grundberuf (erlaubt: 2)."
                                + " Gib einen auf, dann geht ein neuer."),
                new Zeile(BerufSatz.ABSAGE_SPEZIALISIERUNG_VOLL, 1L,
                        Einsatz.von("erlaubt", "1"),
                        "&cDu hast keinen Platz mehr fuer eine Spezialisierung (erlaubt:"
                                + " 1)."),
                new Zeile(BerufSatz.ABSAGE_OHNE_RECHT, 1L, Einsatz.keine(),
                        "&cSpezialisierungen sind noch nicht fuer dich freigegeben."),
                new Zeile(BerufSatz.ABSAGE_GRUNDBERUF_FEHLT, 1L,
                        Einsatz.von("name", "Bergmann"),
                        "&cDafuer musst du zuerst Bergmann sein."),
                new Zeile(BerufSatz.ABSAGE_TRAEGT_SPEZIALISIERUNG, 1L,
                        Einsatz.von("name", "Steinmetz"),
                        "&cDafuer musst du zuerst Steinmetz aufgeben."),
                new Zeile(BerufSatz.ABSAGE_SPERRFRIST, 1L,
                        Einsatz.von("dauer", "23 h 00 min"),
                        "&cDu kannst erst in 23 h 00 min wieder wechseln."),
                new Zeile(BerufSatz.ABSAGE_GELD_FEHLT, 1L,
                        Einsatz.von("betrag", sprache.betrag(5_000L))
                                .mit("guthaben", sprache.betrag(1_200L)),
                        "&cAufgeben kostet 5.000 Mark, du hast 1.200 Mark."),
                new Zeile(BerufSatz.ABSAGE_KEINE_KONSOLE, 1L, Einsatz.keine(),
                        "&7Ein Beruf gehoert zu einem Spieler - die Konsole hat keinen."),
                new Zeile(BerufSatz.ABSAGE_GLEICHZEITIG, 1L, Einsatz.keine(),
                        "&eDein Berufsstand hat sich eben geaendert - versuch es noch"
                                + " einmal."),
                new Zeile(BerufSatz.ABSAGE_NICHT_LESBAR, 1L, Einsatz.keine(),
                        "&7Deine Berufe sind gerade nicht lesbar - im Serverprotokoll"
                                + " steht, warum."),

                // --- Der Verdienst: zwei Handbalken ------------------------------
                // Sie stehen ueber dem Gegenstandsgurt und nicht im Chat, und sie tragen
                // GENAU EINE Zeile - sendActionBar nimmt ein Component und keine Liste.
                new Zeile(BerufSatz.VERDIENST_HANDBALKEN, 1L,
                        Einsatz.von("betrag", sprache.pfennige(240L)),
                        "&a+2,40 Mark"),
                new Zeile(BerufSatz.VERDIENST_DECKEL, 1L, Einsatz.keine(),
                        "&eHeute ist genug verdient - morgen zahlt die Arbeit wieder."),

                // Die Grenze EINER Taetigkeit, nicht die des Tages. Das eingesetzte Wort
                // kommt ueber Sprache#wort aus derselben Datei - damit haengt auch die
                // Wortfamilie beruf.taetigkeit an einer Bildprobe, obwohl ein Art.WORT
                // selbst keine Zeile haben kann.
                new Zeile(BerufSatz.VERDIENST_STUNDENMENGE, 1L,
                        Einsatz.von("taetigkeit", sprache.wort(
                                BerufSatz.TAETIGKEIT_ABBAUEN, Einsatz.keine())),
                        "&eMit Graben ist fuer diese Stunde genug - etwas anderes zahlt"
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
                        "&6Die Staende des Bundes (7) - Seite 2 von 4"),
                new Zeile(MarktSatz.ZEILE, 1L,
                        Einsatz.von("welt", "freimarken:altfurt").mit("x", 100)
                                .mit("y", -60).mit("z", 19).mit("menge", 5)
                                .mit("ware", "STONE")
                                .mit("kauf", " · Kauf 2 Mark")
                                .mit("verkauf", " · Ankauf 1 Mark"),
                        "&7  freimarken:altfurt 100/-60/19 · 5x STONE · Kauf"
                                + " 2 Mark · Ankauf 1 Mark"),
                // Die zwei PREISSTUECKE (markt.kauf, markt.verkauf) stehen hier
                // NICHT, und das ist die Regel dieser Tafel: Ein Satz der Art WORT
                // traegt kein eigenes Bild - er wird eingesetzt, und Probe 11b
                // verlangt darum ausdruecklich keine Zeile fuer ihn. Dass die zwei
                // Stuecke richtig eingesetzt werden, prueeft MarktBefehlTest an der
                // fertigen Zeile ("nennt NUR den Ankauf"), und ein LEERES Stueck
                // verschwindet dort rueckstandslos.
                new Zeile(MarktSatz.WEITER, 1L, Einsatz.von("naechste", 3),
                        "&8/markt liste 3 zeigt die naechste Seite"),
                new Zeile(MarktSatz.KEINE_SEITE, 1L,
                        Einsatz.von("seite", 9).mit("seiten", 4),
                        "&7Seite 9 gibt es nicht - die Staende stehen auf 4 Seiten."),
                new Zeile(MarktSatz.FREI, 1L,
                        Einsatz.von("betrag", sprache.betrag(5_000L)),
                        "&7Heute kannst du noch &65.000 Mark&7 aus Verkaeufen"
                                + " bekommen."),
                new Zeile(MarktSatz.OHNE_DECKEL, 1L, Einsatz.keine(),
                        "&7Es gibt keinen Tagesdeckel - du kannst beliebig viel an den"
                                + " Bund verkaufen."),
                new Zeile(MarktSatz.LEER, 1L, Einsatz.keine(),
                        "&eDer Bund hat noch keinen Stand. Gehandelt wird nur, wofuer"
                                + " ein Schild steht."),
                new Zeile(MarktSatz.GESTOERT, 1L, Einsatz.keine(),
                        "&cDie Staende des Bundes liessen sich nicht lesen&7 - sie sind"
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
                        "&aTagesbonus: +500 Mark&7 - komm morgen wieder, dann sind es"
                                + " 600 Mark."),
                new Zeile(BonusSatz.GEZAHLT_SERIE, 1L,
                        Einsatz.von("betrag", "900 Mark").mit("serie", "5"),
                        "&aTagesbonus: +900 Mark&7 - 5 Tage in Folge."),
                // GOLD und nicht grau: Die Bestmarke ist der Augenblick, in dem die
                // Serie etwas bedeutet. Die Farbe steht in der Datei, und diese Zeile
                // haelt sie fest.
                new Zeile(BonusSatz.GEZAHLT_BESTMARKE, 1L,
                        Einsatz.von("betrag", "900 Mark").mit("serie", "5"),
                        "&aTagesbonus: +900 Mark&6 - 5 Tage in Folge, so lange wie noch"
                                + " nie!"),
                new Zeile(BonusSatz.STAND_SERIE, 1L,
                        Einsatz.von("serie", "5").mit("laengste", "12")
                                .mit("morgen", "1.000 Mark"),
                        "&7Tagesbonus: 5 Tage in Folge (am laengsten 12), morgen"
                                + " 1.000 Mark."),
                new Zeile(BonusSatz.STAND_OFFEN, 1L,
                        Einsatz.von("betrag", "500 Mark").mit("serie", "1"),
                        "&7Tagesbonus: heute noch offen - Tag 1 bringt 500 Mark."),
                new Zeile(BonusSatz.STAND_WARTET, 1L,
                        Einsatz.von("minuten", "8").mit("betrag", "600 Mark")
                                .mit("serie", "2"),
                        "&7Tagesbonus: noch 8 taetige Minuten, dann bringt Tag 2"
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
                        "&6Die Markthalle"),
                new Zeile(LadenSatz.ZEILE, 1L,
                        Einsatz.von("name", "halle-1").mit("ort", "62/71/6"),
                        "&7  halle-1&8 - Truhe bei 62/71/6"),
                new Zeile(LadenSatz.LEER, 1L, Einsatz.keine(),
                        "&7  Es gibt noch keine Ladeneinheiten - die Markthalle wird"
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
                        "&7Dein Laden: halle-1, bis 06.12.2026 (noch 29 Tage),"
                                + " 500 Mark je Tag."),
                new Zeile(MietSatz.STAND_KEINER, 1L, Einsatz.keine(),
                        "&7Du hast noch keinen Laden - mit /laden mieten und dem Namen"
                                + " eines Standes nimmst du einen."),
                // GRUEN fuer die Zusage, GRAU fuer das, was sie kostet: Die Farbe steht
                // in der Datei, und diese Zeile haelt sie fest.
                new Zeile(MietSatz.GEMIETET_KOPF, 1L,
                        Einsatz.von("laden", "halle-1").mit("bis", "06.12.2026")
                                .mit("miettage", "30"),
                        "&aDu hast halle-1 gemietet.&7 Der Vertrag laeuft"
                                + " 30 Tage, bis zum 06.12.2026."),
                new Zeile(MietSatz.GEMIETET_KOSTEN, 1L,
                        Einsatz.von("betrag", "5.000 Mark"),
                        "&7Abgebucht: 5.000 Mark Kaution."),
                // BEIDE Zahlen, und das ist der Zweck dieses zweiten Satzes: Wer 55.000
                // abgebucht sieht und 5.000 Kaution erwartet hat, soll in DERSELBEN
                // Zeile lesen, warum. Wer die zwei Werte vertauscht, bekommt hier eine
                // rote Probe und nicht eine Zeile, die sich selbst widerspricht.
                new Zeile(MietSatz.GEMIETET_ERSTMALS, 1L,
                        Einsatz.von("betrag", "55.000 Mark")
                                .mit("lizenz", "50.000 Mark"),
                        "&7Abgebucht: 55.000 Mark - darin die einmalige Ladenlizenz"
                                + " ueber 50.000 Mark."),
                new Zeile(MietSatz.GEMIETET_TAEGLICH, 1L,
                        Einsatz.von("miete", "500 Mark"),
                        "&7Taeglich faellig: 500 Mark. Verlaengern mit"
                                + " /laden verlaengern."),
                // "das sind 59 Tage" stand hier und war das schiefste von allen:
                // Die Zahl ist die RESTLAUFZEIT, klingt aber nach Laenge. Wer frueh
                // verlaengert, las "das sind 59 Tage" nach einer Verlaengerung um
                // 30.
                new Zeile(MietSatz.VERLAENGERT, 1L,
                        Einsatz.von("laden", "halle-1").mit("bis", "05.01.2027")
                                .mit("resttage", "59"),
                        "&ahalle-1 ist verlaengert - bis 05.01.2027, noch"
                                + " 59 Tage."),
                // GELB und nicht gruen: Kuendigen ist keine Zusage, sondern ein
                // Verlust - der Laden ist weg. Die Kaution darunter ist gruen, denn sie
                // kommt zurueck.
                new Zeile(MietSatz.GEKUENDIGT, 1L, Einsatz.von("laden", "halle-1"),
                        "&eDu hast halle-1 gekuendigt."),
                new Zeile(MietSatz.GEKUENDIGT_KAUTION, 1L,
                        Einsatz.von("betrag", "5.000 Mark"),
                        "&aKaution zurueck: +5.000 Mark"),
                // ===== Teil C: der taegliche Lauf =====
                // GELB fuer die Warnung und GRAU fuer die Folge: Die erste Haelfte ist
                // die Nachricht, die zweite sagt, was daraus wird. Die Ausfallzahl steht
                // als ORDNUNGSZAHL da ("Ausfall 1 von 3") - eine Mehrzahl liesse sich in
                // MiniMessage nicht beugen, und "noch 1 Tage" waere der Fehler, der in
                // Teil A schon einmal entstanden ist.
                new Zeile(MietSatz.MIETE_GEWARNT, 1L,
                        Einsatz.von("laden", "halle-1").mit("betrag", "500 Mark")
                                .mit("ausfall", "1").mit("schranke", "3"),
                        "&eDie Miete fuer halle-1 (500 Mark) konnte nicht abgebucht"
                                + " werden.&7 Das war Ausfall 1 von 3 - beim letzten wird"
                                + " geraeumt, und die Kaution verfaellt."),
                // ROT: Der Laden ist weg. Die Kaution STEHT in der Zeile - sie soll das
                // Weglaufen teurer machen als das Kuendigen, und das wirkt nur, wenn der
                // Spieler die Zahl sieht.
                new Zeile(MietSatz.MIETE_GERAEUMT, 1L,
                        Einsatz.von("laden", "halle-1").mit("ausfall", "3")
                                .mit("schranke", "3").mit("kaution", "5.000 Mark"),
                        "&challe-1 ist geraeumt - Ausfall 3 von 3.&7 Die Kaution ueber"
                                + " 5.000 Mark ist verfallen."),
                // GELB und nicht rot: Wer abgelaufen ist, hat jeden Tag gezahlt und
                // nichts falsch gemacht. Seine Kaution kommt zurueck, und zwar mit
                // DERSELBEN Zeile wie beim Kuendigen (GEKUENDIGT_KAUTION oben).
                new Zeile(MietSatz.MIETE_ABGELAUFEN, 1L,
                        Einsatz.von("laden", "halle-1").mit("bis", "14.11.2026"),
                        "&eDein Vertrag ueber halle-1 ist abgelaufen - er lief bis"
                                + " 14.11.2026.&7 Mit /laden mieten nimmst du wieder"
                                + " einen Stand."),
                // Die Zahl steht NACH dem Doppelpunkt und ohne Verb, damit die Zeile
                // auch bei einem einzigen Stapel richtig steht.
                new Zeile(MietSatz.MIETE_INS_FACH, 1L, Einsatz.von("stapel", "5"),
                        "&7Die Ladentruhe ist ins Abholfach gewandert: 5 Stapel. Hol sie"
                                + " mit /laden abholfach."),
                new Zeile(MietSatz.MIETE_VERZUG, 1L,
                        Einsatz.von("laden", "halle-1").mit("betrag", "500 Mark")
                                .mit("ausfall", "2").mit("schranke", "3"),
                        "&eDein Laden halle-1 ist in Verzug - Ausfall 2 von 3.&7 Sorge"
                                + " fuer 500 Mark auf dem Konto, sonst wird geraeumt."),
                // ===== Teil C: das Abholfach =====
                new Zeile(MietSatz.ABHOLFACH_STAND, 1L, Einsatz.von("stapel", "5"),
                        "&7Abholfach: 5 Stapel. /laden abholfach holt sie."),
                new Zeile(MietSatz.ABHOLFACH_LEER, 1L, Einsatz.keine(),
                        "&7Dein Abholfach ist leer."),
                // GRUEN: Das Einzige in diesem Bereich, bei dem der Spieler etwas
                // BEKOMMT.
                new Zeile(MietSatz.ABHOLFACH_GEHOLT, 1L, Einsatz.von("stapel", "3"),
                        "&a3 Stapel aus dem Abholfach geholt."),
                new Zeile(MietSatz.ABHOLFACH_BLEIBT, 1L, Einsatz.von("stapel", "2"),
                        "&7Noch im Fach: 2 Stapel - dein Inventar hat keinen Platz"
                                + " mehr."),
                new Zeile(MietSatz.ABHOLFACH_KEIN_PLATZ, 1L, Einsatz.von("stapel", "5"),
                        "&cDein Inventar ist voll. Im Abholfach: 5 Stapel."),
                // ROT, obwohl nichts verloren ist: Der Spieler soll es dem Team sagen,
                // und dafuer muss die Zeile auffallen.
                new Zeile(MietSatz.ABHOLFACH_UNLESBAR, 1L, Einsatz.von("stapel", "2"),
                        "&cNicht herausgegeben: 2 Stapel. Das bleibt liegen - bitte sag"
                                + " es dem Team."),
                new Zeile(MietSatz.ABSAGE_KEINE_EINHEIT, 1L,
                        Einsatz.von("laden", "halle-9"),
                        "&cEinen Stand namens halle-9 gibt es nicht.\n&7/laden zeigt,"
                                + " welche es gibt."),
                new Zeile(MietSatz.ABSAGE_SCHON_VERMIETET, 1L,
                        Einsatz.von("laden", "halle-1"),
                        "&challe-1 ist schon vermietet."),
                // {laden} ist SEIN Laden und nicht der gewuenschte. Wer die zwei
                // vertauscht, laesst einen Spieler suchen, welchen er eigentlich hat -
                // und diese Zeile allein sieht das nicht; dafuer steht MiettextTest.
                new Zeile(MietSatz.ABSAGE_SCHON_EIN_LADEN, 1L,
                        Einsatz.von("laden", "halle-1"),
                        "&cDu hast schon einen Laden: halle-1.\n&7Mit /laden kuendigen"
                                + " gibst du ihn zurueck."),
                // DREI Zahlen: was es kostet, was da ist, was fehlt. Die dritte rechnet
                // das Programm - ein Spieler soll nicht subtrahieren muessen.
                new Zeile(MietSatz.ABSAGE_ZU_WENIG_GELD, 1L,
                        Einsatz.von("kosten", "55.000 Mark")
                                .mit("guthaben", "50.000 Mark")
                                .mit("fehlt", "5.000 Mark"),
                        "&cDer Abschluss kostet 55.000 Mark, du hast 50.000 Mark - es"
                                + " fehlen 5.000 Mark."),
                new Zeile(MietSatz.ABSAGE_NOCH_ZU_FRUEH, 1L,
                        Einsatz.von("laden", "halle-1").mit("resttage", "40")
                                .mit("dauer", "30"),
                        "&ehalle-1 laeuft noch 40 Tage - verlaengern geht, sobald"
                                + " weniger als 30 Tage uebrig sind."),
                new Zeile(MietSatz.ABSAGE_KEIN_LADEN, 1L, Einsatz.keine(),
                        "&cDu hast keinen Laden.\n&7/laden zeigt, welche Staende frei"
                                + " sind."),
                new Zeile(MietSatz.ABSAGE_FALSCHE_WELT, 1L, Einsatz.keine(),
                        "&cDie Markthalle steht in Altfurt - dort wird gemietet."),
                new Zeile(MietSatz.ABSAGE_GESTOERT, 1L, Einsatz.keine(),
                        "&cDas hat gerade nicht geklappt - versuch es noch einmal."),

                // ===== Plan 7.4: die Kistenshops =====
                // {ware} ist der KURZNAME aus Shopware.kurzname, also dasselbe Wort, das
                // auf der vierten Schildzeile steht. Ein Plus davor heisst: die Ware ist
                // mehr als ihr Material. Diese Tafel setzt ihn als fertigen Text ein -
                // dass er wirklich so entsteht, ist eine andere Frage und steht in
                // ShopwareTafel.
                new Zeile(ShopSatz.SHOP_ANGELEGT, 1L,
                        Einsatz.von("menge", 16L).mit("ware", "STONE"),
                        "&aShop angelegt: 16x STONE"),
                // GOLD und nicht gruen: Die Zeile ist keine Zusage, sondern eine
                // Eigenschaft - und zwar eine, die auffallen soll. Sie kommt HINTER den
                // anderen und ersetzt keine.
                new Zeile(ShopSatz.SHOP_ANGELEGT_BUND, 1L, Einsatz.keine(),
                        "&6Bundshop: unbegrenzter Vorrat, unbegrenzter Platz,"
                                + " unbegrenztes Geld."),
                // GRAU mit WEISSEM Betrag, und der Punkt gehoert wieder zum grauen
                // Stueck: Derselbe Aufbau wie bei den Preiszeilen der Ladenmiete.
                new Zeile(ShopSatz.SHOP_PREIS_KAUF, 1L,
                        Einsatz.von("preis", "100 Mark"),
                        "&7Kunden kaufen fuer &f100 Mark&7."),
                new Zeile(ShopSatz.SHOP_PREIS_VERKAUF, 1L,
                        Einsatz.von("preis", "80 Mark"),
                        "&7Kunden verkaufen fuer &f80 Mark&7."),
                // GELB und nicht rot: Ein abgebauter Shop ist kein Fehler. Die zweite
                // Haelfte ist die wichtigere - dass die WARE IN DER KISTE BLEIBT, weiss
                // nur, wer es gesagt bekommt.
                new Zeile(ShopSatz.SHOP_ABGEBAUT, 1L, Einsatz.von("ware", "STONE"),
                        "&eShop abgebaut: STONE&7 - die Ware bleibt in der Kiste."),

                // DIE ACHT SCHILDABSAGEN. Jede hat ZWEI Zeilen: rot, was falsch ist,
                // grau, wie es richtig geht. Faellt die zweite weg, bleibt ein Spieler
                // am Schild stehen und weiss nicht, was er tippen soll - und genau das
                // ist der Zustand, in dem die Vorbilder ihre Nutzer lassen.
                // {zeile} ist eine Zahl AUS DEM PROGRAMM: Welche Schildzeile falsch ist,
                // weiss nur Shopschild.lies.
                new Zeile(ShopSatz.SHOP_MENGE_KEINE_ZAHL, 1L, Einsatz.von("zeile", 2L),
                        "&cZeile 2 ist keine Zahl.\n&7Dort gehoert die Menge hin,"
                                + " die ein Kunde je Klick bekommt - zum Beispiel 16."),
                new Zeile(ShopSatz.SHOP_MENGE_AUSSERHALB, 1L,
                        Einsatz.von("zeile", 2L).mit("hoechstmenge", 3_456L),
                        "&cZeile 2: die Menge geht von 1 bis 3.456.\n&7Mehr als"
                                + " eine volle Kiste kann ein Handel nicht hergeben."),
                // DIE ABSAGE, DIE AM MEISTEN ERKLAEREN MUSS: Die Preisform ist nicht zu
                // erraten. Darum stehen alle DREI Formen in der Zeile, und zwar weiss
                // hervorgehoben - ein Spieler soll sie abtippen koennen.
                new Zeile(ShopSatz.SHOP_PREIS_FORM, 1L, Einsatz.von("zeile", 3L),
                        "&cZeile 3 ist keine Preiszeile.\n&7So geht es: &fB 100"
                                + "&7 (Kunden kaufen), &fS 80&7 (Kunden verkaufen) oder"
                                + " &fB 100 : S 80&7 (beides)."),
                // NENNT, WAS NICHT GEHT: Punkt, Komma und ein angehaengtes Wort sind die
                // drei Versuche, die jeder macht. Das Programm nimmt NUR Ziffern, und
                // zwar weil die Vorbilder mehr angenommen haben - ChestShop PR #522 zum
                // Nullpreis und #30 zum E als Exponent.
                new Zeile(ShopSatz.SHOP_PREIS_KEINE_ZAHL, 1L, Einsatz.von("zeile", 3L),
                        "&cZeile 3: der Preis ist keine ganze Zahl.\n&7Nur"
                                + " Ziffern - kein Punkt, kein Komma, kein k und kein"
                                + " Mio."),
                new Zeile(ShopSatz.SHOP_PREIS_AUSSERHALB, 1L,
                        Einsatz.von("zeile", 3L).mit("hoechstpreis", 10_000_000L),
                        "&cZeile 3: der Preis geht von 0 bis 10.000.000.\n&7Eine"
                                + " 0 bedeutet: diese Richtung gibt es nicht."),
                // EINE EIGENE ABSAGE und nicht die darueber: Null ist fuer EINE der
                // beiden Richtungen erlaubt, nur beide zusammen sind der Fehler.
                new Zeile(ShopSatz.SHOP_PREIS_BEIDE_NULL, 1L, Einsatz.von("zeile", 3L),
                        "&cZeile 3: beide Preise sind 0.\n&7Einer von beiden"
                                + " genuegt - aber einer muss es sein, sonst ist es ein"
                                + " Geschenk und kein Shop."),
                // DIE ABSAGE, DIE AM MEISTEN GELD RETTET, und sie sagt WARUM. Ein
                // Spieler, der nur "nicht erlaubt" liest, haelt es fuer Willkuer.
                new Zeile(ShopSatz.SHOP_PREIS_VERDREHT, 1L, Einsatz.von("zeile", 3L),
                        "&cZeile 3: du kaufst teurer an, als du verkaufst.\n"
                                + "&7Das ist eine Maschine, die dein Guthaben an jeden"
                                + " Kunden ausschuettet. S darf nicht ueber B liegen."),
                new Zeile(ShopSatz.SHOP_WARE_FEHLT, 1L, Einsatz.von("zeile", 4L),
                        "&cZeile 4 ist leer.\n&7Dort gehoert die Ware hin - ihr"
                                + " Name, oder ein &f?&7 fuer das, was in der Kiste"
                                + " liegt."),

                // DIE WELT SAGT NEIN. Dieselbe zweiteilige Form.
                // NENNT DIE DREI ERLAUBTEN BEHAELTER: Ein Ofen, ein Spender und eine
                // Shulkerkiste sehen aus wie Behaelter und sind ausgeschlossen. Ohne
                // diese Zeile sucht der Spieler den Fehler bei seinem Schild.
                new Zeile(ShopSatz.SHOP_KEINE_KISTE, 1L, Einsatz.keine(),
                        "&cNeben dem Schild steht keine Kiste.\n&7Es geht eine"
                                + " Kiste, eine Redstonetruhe oder ein Fass - direkt"
                                + " daneben, darueber oder darunter, nicht diagonal."),
                // DER FALL, DEN KEIN VORBILD SELBST SCHLIESST: Das Schild auf eigenem
                // Grund, die Kiste einen Block weiter auf fremdem.
                new Zeile(ShopSatz.SHOP_FREMDE_KISTE, 1L, Einsatz.keine(),
                        "&cAn dieser Kiste darfst du nicht.\n&7Sie steht auf"
                                + " fremdem Grund - ein Shop braucht eine Kiste, an die"
                                + " du selbst herankommst."),
                // KOMMT AUCH DEM BESITZER: Preise aendert man durch Abbauen und neu
                // Setzen, denn nur so geht die Aenderung durch die Datenbank.
                // DIE ANDERE HAELFTE DES KISTENSCHUTZES [08.11.2026]: Wer die Kiste
                // nicht oeffnen kann, haengt sonst ein eigenes Schild daneben. Sagt
                // ausdruecklich NICHT "fremder Grund" - draussen gehoert kein Ort
                // jemandem, und der Spieler suchte eine Parzelle, die es nicht gibt.
                new Zeile(ShopSatz.SHOP_FREMDER_SHOP, 1L, Einsatz.keine(),
                        "&cAn dieser Kiste haengt schon ein fremder Shop.\n&7Zwei"
                                + " Schilder an einer Kiste handeln mit derselben"
                                + " Ware - nimm eine eigene Kiste."),
                // KOMMT AUCH DEM BESITZER: Preise aendert man durch Abbauen und neu
                // Setzen, denn nur so geht die Aenderung durch die Datenbank.
                new Zeile(ShopSatz.SHOP_SCHON_EIN_SHOP, 1L, Einsatz.keine(),
                        "&cHier haengt schon ein Shop.\n&7Zum Aendern bau das"
                                + " Schild ab und setz ein neues - so bleibt das Schild"
                                + " bei dem, was der Shop wirklich tut."),
                // NENNT BEIDE FAELLE - leer und mehrere Arten -, weil der Spieler von
                // aussen nicht sieht, welcher vorliegt. UND dass verzaubert eine eigene
                // Art ist: Das ist die Folge der Entscheidung vom 08.11.2026 und von
                // aussen nicht zu erraten.
                new Zeile(ShopSatz.SHOP_WARE_UNKLAR, 1L, Einsatz.keine(),
                        "&cDie Kiste sagt nicht, was du handeln willst.\n&7Fuer"
                                + " ein &f?&7 muss genau EINE Art darin liegen. Eine"
                                + " verzauberte und eine gewoehnliche Spitzhacke sind"
                                + " zwei Arten."),
                // GIBT IHM SEINE EIGENE EINGABE ZURUECK, weiss hervorgehoben: Oft ist es
                // ein Leerzeichen zu viel, und das sieht nur, wer liest, was das
                // Programm gelesen hat.
                new Zeile(ShopSatz.SHOP_WARE_UNBEKANNT, 1L,
                        Einsatz.von("getippt", "STEIN"),
                        "&cDen Gegenstand &fSTEIN&c gibt es nicht.\n&7Leg die"
                                + " Ware in die Kiste und schreib ein &f?&7 - das geht"
                                + " immer, auch bei verzauberten Sachen."),
                new Zeile(ShopSatz.SHOP_KEIN_BUNDRECHT, 1L, Einsatz.keine(),
                        "&cBundshops darfst du nicht anlegen.\n&7Lass die erste"
                                + " Zeile leer, dann wird es dein eigener Shop."),
                // SAGT AUSDRUECKLICH "NOCH EINMAL VERSUCHEN": Eine Absage ohne Ausweg
                // liest ein Spieler als Verbot, baut sein Schild ab und kommt nicht
                // wieder. Dieselbe Linie wie wache.schutz.laden-gestoert.
                new Zeile(ShopSatz.SHOP_GESTOERT, 1L, Einsatz.keine(),
                        "&cDer Shop liess sich nicht anlegen.\n&7Das Schild"
                                + " bleibt stehen - versuch es noch einmal. Bleibt es"
                                + " dabei, sieh im Serverprotokoll nach."),

                // DIE VIER HANDBALKEN. Genau EINE Zeile - keine Umbruchmarke, denn
                // sendActionBar nimmt ein Component. Derselbe Aufbau wie
                // wache.schutz.fremde-parzelle: Absage rot, der fremde Name weiss, und
                // das Leerzeichen davor gehoert zum ROTEN Stueck.
                new Zeile(ShopSatz.SHOP_SCHUTZ_SCHILD, 1L,
                        Einsatz.von("besitzer", "Anna"),
                        "&cDieser Shop gehoert &fAnna"),
                // EIN EIGENER SATZ und nicht derselbe wie beim Schild: Der Spieler soll
                // lesen, WARUM eine gewoehnlich aussehende Kiste geschuetzt ist.
                new Zeile(ShopSatz.SHOP_SCHUTZ_KISTE, 1L,
                        Einsatz.von("besitzer", "Anna"),
                        "&cDiese Kiste ist der Shop von &fAnna"),
                // DER DRITTE SATZ OHNE PLATZHALTER: Ein Bundshop hat keinen Besitzer, den
                // man nennen koennte, und ein Wert, in dem manchmal ein Mensch und
                // manchmal eine Einrichtung steht, zwingt zu einem Satz fuer beide.
                new Zeile(ShopSatz.SHOP_SCHUTZ_BUND, 1L, Einsatz.keine(),
                        "&cDas ist ein Shop des Bundes"),
                // DIE UEBERRASCHENDSTE DER FUENF: Der Traegerblock sieht nach nichts aus -
                // ein Stein unter einem Stehschild. Der Satz muss den Grund nennen, sonst
                // haelt der Spieler ihn fuer kaputt.
                new Zeile(ShopSatz.SHOP_SCHUTZ_TRAEGER, 1L, Einsatz.keine(),
                        "&cDaran haengt ein fremdes Shopschild"),
                // DER FUENFTE [08.11.2026], fuer den einen Fall, in dem die zwei
                // Richtungen der Ablage auseinandergehen: geschuetzt ja, Besitzer
                // unbekannt. Ohne ihn stuende hier "der Shop von -".
                new Zeile(ShopSatz.SHOP_SCHUTZ_UNLESBAR, 1L, Einsatz.keine(),
                        "&cDiese Kiste ist geschuetzt - versuch es noch einmal"),

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
                        "&aGekauft: 16x STONE&7 fuer &f100 Mark"),
                new Zeile(ShopSatz.SHOP_VERKAUF_GELUNGEN, 1L, Einsatz
                        .von("menge", 16L)
                        .mit("ware", "STONE")
                        .mit("preis", "80 Mark"),
                        "&aVerkauft: 16x STONE&7 fuer &f80 Mark"),
                // DREI ZAHLEN IN EINER ZEILE, und das ist Absicht: Wer 100 am
                // Schild liest und 95 bekommt, sucht den Fehler beim Shop. Eine
                // Zusage, die eine ihrer Zahlen nicht nennt, traegt keine.
                new Zeile(ShopSatz.SHOP_VERKAUF_GEBUEHR, 1L, Einsatz
                        .von("menge", 16L)
                        .mit("ware", "STONE")
                        .mit("preis", "100 Mark")
                        .mit("gebuehr", "7 Mark")
                        .mit("erloes", "93 Mark"),
                        "&aVerkauft: 16x STONE&7 fuer &f93 Mark&7 von 100 Mark,"
                                + " 7 Mark Marktgebuehr"),

                // DIE ELF ABSAGEN DES HANDELS. Jede nennt, WAS fehlt - "das geht
                // nicht" laesst einen Kunden dreimal klicken und dann aufhoeren.
                //
                // Die zwei Richtungsabsagen sagen, was STATTDESSEN geht: Ein Shop
                // mit nur einem Preis sieht aus wie jeder andere, und ohne den
                // Hinweis probiert niemand die andere Maustaste.
                new Zeile(ShopSatz.SHOP_VERKAUFT_NICHT, 1L, Einsatz.keine(),
                        "&cDieser Shop verkauft nichts&7 - mit Linksklick kannst"
                                + " du ihm etwas verkaufen."),
                new Zeile(ShopSatz.SHOP_KAUFT_NICHT_AN, 1L, Einsatz.keine(),
                        "&cDieser Shop kauft nichts an&7 - mit Rechtsklick kaufst"
                                + " du bei ihm."),
                // NICHT ROT: Das ist kein Fehler des Spielers, sondern eine
                // Auskunft. Und sie kommt nur beim Rechtsklick - beim Linksklick
                // baut der Besitzer sein Schild ab, und zwei Antworten auf eine
                // Tat liest niemand als zwei.
                new Zeile(ShopSatz.SHOP_EIGENER_SHOP, 1L, Einsatz.keine(),
                        "&7Das ist dein eigener Shop - nimm die Ware aus der"
                                + " Kiste."),
                // DER EINZIGE FALL, DEN NUR DER BESITZER BEHEBEN KANN - darum
                // sagt der Satz, dass man es ihm sagen soll.
                new Zeile(ShopSatz.SHOP_WARE_UNLESBAR, 1L, Einsatz.keine(),
                        "&cDieser Shop handelt nicht mehr&7 - sag es seinem"
                                + " Besitzer."),
                new Zeile(ShopSatz.SHOP_AUSVERKAUFT, 1L,
                        Einsatz.von("ware", "STONE"),
                        "&cAusverkauft: STONE"),
                // MIT DER MENGE DES SHOPS: Ein Shop, der 16 Stueck je Handel
                // nimmt, lehnt auch 15 ab - ohne die Zahl sieht der Kunde seine
                // Ware im Rucksack und die Absage auf dem Schirm.
                new Zeile(ShopSatz.SHOP_KEINE_WARE, 1L, Einsatz
                        .von("menge", 16L)
                        .mit("ware", "STONE"),
                        "&cDu brauchst 16x STONE"),
                new Zeile(ShopSatz.SHOP_KEIN_PLATZ, 1L, Einsatz.keine(),
                        "&cIn deinem Rucksack ist kein Platz"),
                // EIN EIGENER SATZ und nicht derselbe wie beim Rucksack: Der
                // Kunde kann daran nichts aendern, und ein Satz, der ihn zum
                // Aufraeumen auffordert, schickt ihn ins Leere.
                new Zeile(ShopSatz.SHOP_KISTE_VOLL, 1L, Einsatz.keine(),
                        "&cDie Kiste dieses Shops ist voll"),
                new Zeile(ShopSatz.SHOP_KEIN_GELD, 1L,
                        Einsatz.von("preis", "100 Mark"),
                        "&cDafuer brauchst du 100 Mark"),
                // NENNT DEN BESITZER NICHT: Wer wie viel Geld hat, ist nicht die
                // Sache eines Kunden, und der Name steht am Schild.
                new Zeile(ShopSatz.SHOP_PLEITE, 1L, Einsatz.keine(),
                        "&cDieser Shop kann das nicht zahlen"),
                // SAGT AUSDRUECKLICH, DASS KEIN GELD WEG IST. Das ist die Zusage,
                // die der Handel wirklich haelt: Was nach dem Zahlen scheitert,
                // wird erstattet.
                new Zeile(ShopSatz.SHOP_HANDEL_GESTOERT, 1L, Einsatz.keine(),
                        "&cDer Handel ist nicht zustande gekommen&7 - dein Geld"
                                + " ist nicht weg."),

                // Das Blockbuch (Moderation). Die ZEIT kommt aus dem eigenen Muster und
                // die TAT aus dem eigenen Wort, nicht als getippte Zeichenkette: Beide
                // haben keine eigene Tafelzeile - ein Art.MUSTER und ein Art.WORT
                // koennen keine haben -, und so haengen sie trotzdem an einer Bildprobe.
                new Zeile(BlockSatz.KOPF_ORT, 1L,
                        Einsatz.von("ort", "altfurt 62/-60/8").mit("anzahl", 3L),
                        "&6Blockbuch&7 an altfurt 62/-60/8 - 3 Eintraege:"),
                new Zeile(BlockSatz.KOPF_SPIELER, 1L,
                        Einsatz.von("spieler", "Anna").mit("anzahl", 2L),
                        "&6Blockbuch&7 von Anna - 2 Eintraege:"),
                new Zeile(BlockSatz.ZEILE, 1L,
                        Einsatz.von("zeit", sprache.muster(BlockSatz.ZEIT_MUSTER)
                                        .format(BLOCKBUCH_ZEIT))
                                .mit("spieler", "Anna")
                                .mit("tat", sprache.wort(BlockSatz.TAT_ABBAUEN,
                                        Einsatz.keine()))
                                .mit("block", "oak_log"),
                        "&7  09.11.2026 14:30  Anna baute ab oak_log"),
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
                        "&8  09.11.2026 14:30&c  x Bert setzte stone"),
                new Zeile(BlockSatz.ZEILE_MIT_ORT, 1L,
                        Einsatz.von("zeit", sprache.muster(BlockSatz.ZEIT_MUSTER)
                                        .format(BLOCKBUCH_ZEIT))
                                .mit("ort", "altfurt 62/-60/8")
                                .mit("tat", sprache.wort(BlockSatz.TAT_BENUTZEN,
                                        Einsatz.keine()))
                                .mit("block", "chest"),
                        "&7  09.11.2026 14:30  altfurt 62/-60/8 benutzte chest"),
                new Zeile(BlockSatz.ZEILE_MIT_ORT_ABGEWIESEN, 1L,
                        Einsatz.von("zeit", sprache.muster(BlockSatz.ZEIT_MUSTER)
                                        .format(BLOCKBUCH_ZEIT))
                                .mit("ort", "marktstadt 7/71/13")
                                .mit("tat", sprache.wort(BlockSatz.TAT_BENUTZEN,
                                        Einsatz.keine()))
                                .mit("block", "chest"),
                        "&8  09.11.2026 14:30&c  x marktstadt 7/71/13 benutzte chest"),
                // SAGT, WARUM nichts da sein kann. Ohne den Hinweis sucht der Moderator
                // den Fehler im Plugin, obwohl die Welt abgewaehlt oder die Frist um
                // sein kann.
                new Zeile(BlockSatz.LEER_ORT, 1L,
                        Einsatz.von("ort", "altfurt 62/-60/8"),
                        "&7Zu altfurt 62/-60/8 steht nichts im Blockbuch - die Welt kann"
                                + " abgewaehlt sein, oder die Frist hat es geholt."),
                new Zeile(BlockSatz.LEER_SPIELER, 1L, Einsatz.von("spieler", "Anna"),
                        "&7Zu Anna steht nichts im Blockbuch."),
                // NENNT DIE REICHWEITE: Wer einen Block weit weg anschaut, soll nicht
                // raten, ob das Buch leer ist oder der Blick zu weit ging.
                new Zeile(BlockSatz.KEIN_BLOCK, 1L, Einsatz.von("reichweite", 6L),
                        "&7Du siehst keinen Block an&8 (Reichweite 6 Bloecke). Nach"
                                + " einem Spieler fragt /freimarken blockbuch"
                                + " spieler <name>."),
                // "Nichts aufgeschrieben" ist NICHT "nichts geschehen" - genau den
                // Unterschied muss ein Moderator kennen, bevor er jemanden freispricht.
                new Zeile(BlockSatz.AUS, 1L, Einsatz.keine(), "&eIn dieser Welt schreibt das Blockbuch nichts&7 - was hier"
                                + " geschah, steht nirgends. Einstellbar unter"
                                + " blockbuch.welten in der config.yml."),
                // Und ein Netzfehler darf nicht aussehen wie ein Freispruch.
                new Zeile(BlockSatz.GESTOERT, 1L, Einsatz.keine(), "&cDas Blockbuch antwortet nicht&7 - der Grund steht im"
                                + " Serverprotokoll. Das ist KEIN Freispruch."),
                new Zeile(BlockSatz.UNBEKANNT, 1L, Einsatz.von("name", "annna"),
                        "&cDen Spieler annna kennt dieser Server nicht."),

                // /shop freigeben - der SCHLUESSEL zu einer fremden Shopkiste.
                // Die Zusage-Zeile nennt AUSDRUECKLICH, was die Freigabe erlaubt und
                // was nicht: Sonst muesste der Besitzer raten, wie weit er gerade
                // Vertrauen verschenkt hat.
                new Zeile(ShopSatz.SHOP_FREIGEGEBEN, 1L,
                        Einsatz.von("spieler", "Bert"), "&aBert darf jetzt an diesen Shop&7 - Kiste oeffnen und abbauen."
                                + " Ein eigenes Schild daran haengen kann er nicht."),
                new Zeile(ShopSatz.SHOP_GESPERRT, 1L,
                        Einsatz.von("spieler", "Bert"), "&eBert darf nicht mehr an diesen Shop."),
                new Zeile(ShopSatz.SHOP_SCHON_FREI, 1L,
                        Einsatz.von("spieler", "Bert"), "&7Bert darf schon an diesen Shop."),
                new Zeile(ShopSatz.SHOP_NICHT_FREI, 1L,
                        Einsatz.von("spieler", "Bert"), "&7Bert durfte ohnehin nicht an diesen Shop."),
                new Zeile(ShopSatz.SHOP_FREIGABEN_KOPF, 1L,
                        Einsatz.von("anzahl", 2L), "&6An diesem Shop duerfen 2 ausser dir:"),
                new Zeile(ShopSatz.SHOP_FREIGABEN_ZEILE, 1L,
                        Einsatz.von("spieler", "Bert"), "&7  Bert"),
                new Zeile(ShopSatz.SHOP_FREIGABEN_LEER, 1L, Einsatz.keine(), "&7Ausser dir darf niemand an diesen Shop."),
                new Zeile(ShopSatz.SHOP_KEIN_SCHILD, 1L,
                        Einsatz.von("reichweite", 5L), "&7Du siehst kein Shopschild an&8 (Reichweite 5 Bloecke). Stell"
                                + " dich vor dein Schild und tippe /shop freigeben"
                                + " <name>."),
                // Freigeben darf NUR der Besitzer - nicht das Team, nicht ein schon
                // Freigegebener. Sonst waere der Schluessel selbst weitergebbar.
                new Zeile(ShopSatz.SHOP_NICHT_DEINER, 1L, Einsatz.keine(), "&cDieser Shop gehoert dir nicht.&7 Freigeben darf nur, wem der"
                                + " Laden gehoert."),
                new Zeile(ShopSatz.SHOP_BUND_NICHT_FREIGEBBAR, 1L, Einsatz.keine(),
                        "&7Ein Bundshop gehoert niemandem - freigeben kann ihn darum"
                                + " auch niemand. Wer an ihn darf, entscheidet das"
                                + " Recht freimarken.shop.bund."),
                new Zeile(ShopSatz.SHOP_SELBST, 1L, Einsatz.keine(), "&7An deinen eigenen Shop darfst du ohnehin."),
                new Zeile(ShopSatz.SHOP_UNBEKANNT, 1L, Einsatz.von("name", "bertt"),
                        "&cDen Spieler bertt kennt dieser Server nicht."),
                // SAGT, DASS SICH NICHTS GEAENDERT HAT. Beim Sperren ist das die
                // wichtigere Haelfte: Wer glaubt, jemanden ausgesperrt zu haben, und
                // der kommt weiter hinein, merkt es womoeglich nie.
                new Zeile(ShopSatz.SHOP_FREIGABE_GESTOERT, 1L, Einsatz.keine(), "&cDie Freigabe liess sich nicht speichern&7 - es hat sich"
                                + " NICHTS geaendert. Der Grund steht im"
                                + " Serverprotokoll."),
                // /shop liste - die eigenen Laeden, auf Seiten. Die Zeile traegt die
                // STELLE vorn: Das ist der Zweck dieser Liste, nicht die Ware.
                new Zeile(ShopSatz.SHOP_LISTE_LEER, 1L, Einsatz.keine(), "&7Du hast noch keinen Kistenshop. Einer entsteht aus einem"
                                + " Schild an einer Kiste - die erste Zeile bleibt leer, dann"
                                + " Menge, Preis und Ware."),
                new Zeile(ShopSatz.SHOP_LISTE_KOPF, 1L,
                        Einsatz.von("anzahl", 3L).mit("seite", 1L).mit("seiten", 2L),
                        "&6Deine Kistenshops (3) - Seite 1 von 2"),
                // Die beiden Preisstuecke sind Art.WORT und stehen darum NICHT in
                // dieser Tafel - sie werden nie allein gesendet. Eingesetzt werden sie
                // hier fertig, genau wie der Zweig sie baut.
                new Zeile(ShopSatz.SHOP_LISTE_ZEILE, 1L,
                        Einsatz.von("welt", "freimarken:altfurt").mit("x", 60L)
                                .mit("y", 71L).mit("z", 6L).mit("menge", 16L)
                                .mit("ware", "Stein")
                                .mit("kauf", " \u00b7 Kauf 100 Mark")
                                .mit("verkauf", " \u00b7 Ankauf 80 Mark"),
                        "&7  freimarken:altfurt 60/71/6 · 16x Stein · Kauf 100 Mark"
                                + " · Ankauf 80 Mark"),
                new Zeile(ShopSatz.SHOP_LISTE_WEITER, 1L,
                        Einsatz.von("naechste", 2L), "&8/shop liste 2 zeigt die naechste Seite"),
                new Zeile(ShopSatz.SHOP_LISTE_KEINE_SEITE, 1L,
                        Einsatz.von("seite", 9L).mit("seiten", 2L), "&7Seite 9 gibt es nicht - deine Shops stehen auf 2 Seiten."),
                new Zeile(ShopSatz.SHOP_LISTE_GESTOERT, 1L, Einsatz.keine(),
                        "&cDeine Shopliste liess sich nicht lesen&7 - deine Laeden"
                                + " sind deswegen NICHT weg. Der Grund steht im"
                                + " Serverprotokoll."),

                // --- Der ANKAUF DES BUNDES am Schild [10.11.2026] ---------------
                //
                // SIEBEN Zeilen standen hier am Mittag und sechs sind am Nachmittag
                // wieder gegangen - mit der Tafel bundesmarkt.ankauf. Sie sagten, was an
                // einer Tafel schiefgeht; ohne Tafel gibt es keinen dieser Faelle.
                // Geblieben ist der DECKEL, und der ist der Grund des ganzen Schrittes.
                new Zeile(ShopSatz.SHOP_BUND_DECKEL, 1L, Einsatz.keine(),
                        "&eHeute ist genug verkauft - morgen kauft der Bund wieder."),

                // --- Die Freie Truhe [Teil D]: ACHT Zeilen -------------------
                //
                // ZWEI PAARE MIT ZWEI BLICKWINKELN, und in beiden Paaren stehen hier
                // darum verschiedene WORTE und nicht verschiedene Zahlen: offen gegen
                // offen-frei (mit Deckel eine Zahl, ohne Deckel ein eigener Satz) und
                // zurueck gegen behalten (was dableibt, was mitkommt). Wer zwei von
                // ihnen vertauscht, bekommt hier eine rote Probe - und nicht einen
                // Spieler, der "nimm, was du brauchst" liest und am Deckel steht.
                new Zeile(TruheSatz.OFFEN, 1L, Einsatz.von("frei", 11),
                        "&7Freie Truhe - heute kannst du noch &611&7 Gegenstaende"
                                + " behalten. Hineinlegen darfst du immer."),
                new Zeile(TruheSatz.OFFEN_FREI, 1L, Einsatz.keine(),
                        "&7Freie Truhe - nimm, was du brauchst, und lege hinein, was du"
                                + " nicht brauchst."),
                new Zeile(TruheSatz.DECKEL, 1L, Einsatz.von("deckel", 16),
                        "&eHeute hast du deine 16 Gegenstaende aus der Freien"
                                + " Truhe&7 - morgen ist sie wieder voll."),
                new Zeile(TruheSatz.BESETZT, 1L, Einsatz.keine(),
                        "&eJemand sieht gerade in die Freie Truhe&7 - einen"
                                + " Augenblick."),
                new Zeile(TruheSatz.GESTOERT, 1L, Einsatz.keine(),
                        "&cDie Freie Truhe antwortet gerade nicht&7 - dein Tagesstand"
                                + " liess sich nicht lesen. Der Grund steht im"
                                + " Serverprotokoll."),
                new Zeile(TruheSatz.BEHALTEN, 1L,
                        Einsatz.von("stueck", 9).mit("frei", 7),
                        "&79 Gegenstaende behalten - heute noch &67&7 frei."),
                new Zeile(TruheSatz.ZURUECK, 1L,
                        Einsatz.von("stueck", 14).mit("deckel", 16),
                        "&e14 Gegenstaende gehen zurueck in die Truhe&7 - mehr als 16"
                                + " am Tag nimmt niemand mit."),
                new Zeile(TruheSatz.VERBOTEN, 1L, Einsatz.von("ware", "WRITTEN_BOOK"),
                        "&eWRITTEN_BOOK gehoert nicht in die Freie Truhe&7 - du"
                                + " bekommst es zurueck."),

                // --- /freistadt [11.11.2026] ----------------------------------------
                new Zeile(ReiseSatz.WARP_STADT_UNBEKANNT, 1L,
                        Einsatz.von("name", "neustadt"),
                        "&cDie Freistadt neustadt gibt es nicht.\n"
                                + "&7Welche es gibt, zeigt /freistadt."),
                new Zeile(ReiseSatz.WARP_STADT_OHNE_NAMEN, 1L, Einsatz.keine(),
                        "&7Welche Freistadt? Die Namen zeigt /freistadt."),
                new Zeile(ReiseSatz.WARP_STADT_KEINE_WELT, 1L, Einsatz.keine(),
                        "&cDie Welt der Freistaedte fehlt - sag es dem Betreiber."),

                new Zeile(FreistadtSatz.KOPF, 1L, Einsatz.keine(), "&6Die Freistaedte"),
                new Zeile(FreistadtSatz.ZEILE, 1L,
                        Einsatz.von("name", "neustadt").mit("besitzer", "Anna")
                                .mit("feld", "3/-7"), "&7  neustadt&8 - von Anna, Feld 3/-7"),
                new Zeile(FreistadtSatz.LEER, 1L, Einsatz.keine(), "&7  Es gibt noch keine Freistadt - macht die erste: /freistadt gruenden deinname"),
                new Zeile(FreistadtSatz.FRAGE, 1L,
                        Einsatz.von("name", "neustadt").mit("preis", 400_000L)
                                .mit("danach", 123_456L), "&eneustadt gruenden kostet 400.000 Mark. Danach haettest du 123.456 Mark.\n"
                        + "&7Zum Gruenden: /freistadt gruenden neustadt bestaetigen"),
                new Zeile(FreistadtSatz.GEGRUENDET, 1L,
                        Einsatz.von("name", "neustadt").mit("feld", "3/-7"), "&aneustadt ist gegruendet - Feld 3/-7.\n"
                        + "&7Hin kommst du mit /warp freistadt neustadt."),
                new Zeile(FreistadtSatz.NAME_ZU_LANG, 1L,
                        Einsatz.von("kleinstens", 3L).mit("groesstens", 32L), "&cEin Stadtname hat 3 bis 32 Zeichen."),
                new Zeile(FreistadtSatz.NAME_FALSCH, 1L, Einsatz.keine(), "&cEin Stadtname traegt nur kleine Buchstaben, Ziffern und"
                        + " Bindestriche und faengt mit einem Buchstaben an."),
                new Zeile(FreistadtSatz.NAME_RESERVIERT, 1L,
                        Einsatz.von("name", "kornmark"), "&ckornmark ist der Name einer Welt - so darf keine Stadt heissen."),
                new Zeile(FreistadtSatz.NAME_VERGEBEN, 1L,
                        Einsatz.von("name", "neustadt"), "&cDie Freistadt neustadt gibt es schon."),
                new Zeile(FreistadtSatz.GRENZE_ERREICHT, 1L,
                        Einsatz.von("hoechstzahl", 1L), "&cDu hast schon so viele Freistaedte, wie dein Rang erlaubt (1)."),
                new Zeile(FreistadtSatz.KEIN_FREIES_FELD, 1L, Einsatz.keine(), "&cIn dieser Welt ist kein Stadtfeld mehr frei."),
                new Zeile(FreistadtSatz.ZU_WENIG_MARK, 1L,
                        Einsatz.von("preis", 400_000L).mit("fehlt", 276_544L), "&cEine Freistadt kostet 400.000 Mark - dir fehlen 276.544."),
                new Zeile(FreistadtSatz.GESTOERT, 1L, Einsatz.keine(), "&cDas Gruenden hat nicht geklappt - dein Geld ist zurueck."
                        + " Versuch es noch einmal."),
                new Zeile(FreistadtSatz.KEINE_WELT, 1L, Einsatz.keine(), "&cDie Welt der Freistaedte fehlt - sag es dem Betreiber."));
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
