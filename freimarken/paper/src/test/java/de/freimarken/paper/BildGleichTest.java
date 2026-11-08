package de.freimarken.paper;

import static org.junit.jupiter.api.Assertions.assertEquals;

import de.freimarken.kern.Besitzbuch;
import de.freimarken.kern.Richtung;
import de.freimarken.kern.Wirtschaftswerte;
import de.freimarken.kern.Zeitwerte;
import java.time.LocalDate;
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

    static List<Zeile> tabelle() {
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
                                + "u hast 25.000 Mark\n&7  Der Weg dazwischen wird dein Bauland.\n&7"
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
                                + "Feldern kommen zurueck. Geld gibt es keines zurueck.\n&7  Fuer ei"
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
                                + "es allein geht nicht heraus, also wird\n&7  es ganz aufgetrennt."),
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

                // --- Der Bundesmarkt: dreizehn Zeilen ----------------------------
                // Die MATERIALNAMEN stehen mit Absicht nicht in der Sprachdatei - ein
                // Sortiment aus vierzig Materialien waere dort eine zweite Abschrift der
                // config.yml. Gezeigt wird der technische Name, und genau den prueeft
                // diese Tafel mit.
                new Zeile(MarktSatz.KOPF, 1L, Einsatz.keine(),
                        "&6Der Bundesmarkt kauft"),
                new Zeile(MarktSatz.SORTIMENT, 1L,
                        Einsatz.von("liste", "stone 0,20 Mark, cod 4,00 Mark"),
                        "&7stone 0,20 Mark, cod 4,00 Mark"),
                new Zeile(MarktSatz.FREI, 1L,
                        Einsatz.von("betrag", sprache.betrag(5_000L)),
                        "&7Heute kannst du noch &65.000 Mark&7 aus Verkaeufen"
                                + " bekommen."),
                new Zeile(MarktSatz.LEER, 1L, Einsatz.keine(),
                        "&eDer Bundesmarkt kauft derzeit nichts."),

                // GEZAEHLT: eins und viele. Die Null steht mit dabei, weil ein Verkauf
                // von null Stueck gar nicht gebucht wird - die Zeile darf es trotzdem
                // geben, und ihr Bild soll festliegen.
                // {anzahl} wird MITGEGEBEN und nicht von bau() eingesetzt - gemessen:
                // Fehlt es, faellt Sprache auf den rohen Wortlaut zurueck, und im Bild
                // stehen die Klammern. Die "eins"-Form nennt es nicht, darum faellt das
                // erst bei zwei und null auf.
                new Zeile(MarktSatz.VERKAUFT, 1L,
                        Einsatz.von("anzahl", "1").mit("material", "stone")
                                .mit("betrag", sprache.betrag(1L)),
                        "&a1 stone verkauft - &61 Mark&a."),
                new Zeile(MarktSatz.VERKAUFT, 2L,
                        Einsatz.von("anzahl", "2").mit("material", "stone")
                                .mit("betrag", sprache.betrag(1L)),
                        "&a2 stone verkauft - &61 Mark&a."),
                new Zeile(MarktSatz.VERKAUFT, 0L,
                        Einsatz.von("anzahl", "0").mit("material", "stone")
                                .mit("betrag", sprache.betrag(0L)),
                        "&a0 stone verkauft - &60 Mark&a."),

                new Zeile(MarktSatz.ABSAGE_FALSCHE_WELT, 1L, Einsatz.keine(),
                        "&cDer Bundesmarkt steht in Altfurt - dort kauft der Bund."),
                new Zeile(MarktSatz.ABSAGE_LEERE_HAND, 1L, Einsatz.keine(),
                        "&cDu haeltst nichts in der Hand."),
                new Zeile(MarktSatz.ABSAGE_KAUFT_NICHT, 1L,
                        Einsatz.von("material", "dirt"),
                        "&cDer Bund kauft dirt nicht.\n&7/markt zeigt, was er kauft."),
                new Zeile(MarktSatz.ABSAGE_ZU_WENIG, 1L,
                        Einsatz.von("material", "stone"),
                        "&eFuer so wenig stone gibt es keine ganze Mark - der Bund zahlt"
                                + " nur in ganzen Mark."),
                new Zeile(MarktSatz.ABSAGE_DECKEL, 1L, Einsatz.keine(),
                        "&eHeute ist genug verkauft - morgen kauft der Bund wieder."),
                new Zeile(MarktSatz.ABSAGE_NICHT_BUCHBAR, 1L, Einsatz.keine(),
                        "&cDer Verkauf ist gerade nicht buchbar - deine Ware hast du"
                                + " noch. Im Serverprotokoll steht, warum."),

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
                new Zeile(MietSatz.STAND, 1L,
                        Einsatz.von("laden", "halle-1").mit("bis", "06.12.2026")
                                .mit("tage", "29").mit("miete", "500 Mark"),
                        "&7Dein Laden: halle-1, bis 06.12.2026 (29 Tage), 500 Mark"
                                + " je Tag."),
                new Zeile(MietSatz.STAND_KEINER, 1L, Einsatz.keine(),
                        "&7Du hast noch keinen Laden - mit /laden mieten und dem Namen"
                                + " eines Standes nimmst du einen."),
                // GRUEN fuer die Zusage, GRAU fuer das, was sie kostet: Die Farbe steht
                // in der Datei, und diese Zeile haelt sie fest.
                new Zeile(MietSatz.GEMIETET_KOPF, 1L,
                        Einsatz.von("laden", "halle-1").mit("bis", "06.12.2026")
                                .mit("tage", "30"),
                        "&aDu hast halle-1 gemietet.&7 Der Vertrag laeuft bis"
                                + " 06.12.2026 - 30 Tage."),
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
                new Zeile(MietSatz.VERLAENGERT, 1L,
                        Einsatz.von("laden", "halle-1").mit("bis", "05.01.2027")
                                .mit("tage", "59"),
                        "&ahalle-1 ist verlaengert - bis 05.01.2027, das sind"
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
                        Einsatz.von("laden", "halle-1").mit("tage", "40")
                                .mit("dauer", "30"),
                        "&ehalle-1 laeuft noch 40 Tage - verlaengern geht, sobald"
                                + " weniger als 30 Tage uebrig sind."),
                new Zeile(MietSatz.ABSAGE_KEIN_LADEN, 1L, Einsatz.keine(),
                        "&cDu hast keinen Laden.\n&7/laden zeigt, welche Staende frei"
                                + " sind."),
                new Zeile(MietSatz.ABSAGE_FALSCHE_WELT, 1L, Einsatz.keine(),
                        "&cDie Markthalle steht in Altfurt - dort wird gemietet."),
                new Zeile(MietSatz.ABSAGE_GESTOERT, 1L, Einsatz.keine(),
                        "&cDas hat gerade nicht geklappt - versuch es noch einmal."));
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
