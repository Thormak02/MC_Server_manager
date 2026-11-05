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
                                + " es zurueckgeht."));
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
