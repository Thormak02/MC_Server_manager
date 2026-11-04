package de.freimarken.paper;

import static org.junit.jupiter.api.Assertions.assertEquals;

import de.freimarken.kern.Besitzbuch;
import de.freimarken.kern.Richtung;
import de.freimarken.kern.Wirtschaftswerte;
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
                new Zeile(ParzellenSatz.BEZAHLT, 1L, Einsatz.von("betrag", Betraege.mark(25_000L)).mit("rest", Betraege.mark(75_000L)),
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
                new Zeile(ParzellenSatz.INFO_FREI, 1L, Einsatz.von("preis", Betraege.mark(25_000L)),
                        "&7  frei · &a25.000 Mark&8 · /parzelle nehmen"),
                new Zeile(ParzellenSatz.INFO_FREI_AM_LIMIT, 1L, Einsatz.von("preis", Betraege.mark(25_000L)),
                        "&7  frei · 25.000 Mark"),
                new Zeile(ParzellenSatz.INFO_AM_LIMIT, 1L, Einsatz.von("anzahl", "1"),
                        "&c  Dein Rang erlaubt 1 Parzelle, und so viele hast du schon."),
                new Zeile(ParzellenSatz.INFO_AM_LIMIT, 2L, Einsatz.von("anzahl", "2"),
                        "&c  Dein Rang erlaubt 2 Parzellen, und so viele hast du schon."),
                new Zeile(ParzellenSatz.INFO_AM_LIMIT, 0L, Einsatz.von("anzahl", "0"),
                        "&c  Dein Rang erlaubt 0 Parzellen, und so viele hast du schon."),
                new Zeile(ParzellenSatz.INFO_EIGENTUEMER, 1L, Einsatz.von("name", "Anna"),
                        "&7  Eigentuemer: &fAnna"),
                new Zeile(ParzellenSatz.INFO_GEKAUFT_FUER, 1L, Einsatz.von("preis", Betraege.mark(25_000L)),
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
                new Zeile(ParzellenSatz.NEHMEN_ZU_WENIG, 1L, Einsatz.von("betrag", Betraege.mark(25_000L)).mit("guthaben", Betraege.mark(1_000L)),
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
                new Zeile(ParzellenSatz.LISTE_ZEILE, 1L, listenzeile(sprache, "1", "0", Betraege.mark(25_000L),
                        sprache.wort(ParzellenSatz.LISTE_MITBAUER,
                                Einsatz.von("anzahl", "1")), ""),
                        "&7  1/0 · 25.000 Mark · 1 Mitbauer"),
                new Zeile(ParzellenSatz.LISTE_ZEILE, 1L, listenzeile(sprache, "2", "0", Betraege.mark(50_000L), "",
                        sprache.wort(ParzellenSatz.LISTE_GRUPPE, Einsatz.keine())),
                        "&7  2/0 · 50.000 Mark · Gruppe"),
                new Zeile(ParzellenSatz.LISTE_NAECHSTE, 1L, Einsatz.von("betrag", Betraege.mark(25_000L)),
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
                new Zeile(MarkSatz.STAND, 1L, Einsatz.von("betrag", Betraege.mark(25_000L)),
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
                        .mit("betrag", Betraege.mark(10_000L))
                        .mit("guthaben", Betraege.mark(25_000L)),
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
                                + " Operator Bescheid."));
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
