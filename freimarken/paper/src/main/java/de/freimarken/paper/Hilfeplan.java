package de.freimarken.paper;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * WAS {@code /hilfe} zeigt: welche Gruppen, welcher Befehl darin, und wer ihn sehen darf.
 *
 * <p>Dieselbe Teilung wie bei {@link Menuetafel} - reine Daten, kein Paper. Was hier
 * steht, laeuft durch Proben; was daraus wird, ist eine Liste von Chatzeilen.
 *
 * <p><b>Die BESCHREIBUNG ist ein Verweis und keine Abschrift.</b> Jeder Eintrag zeigt auf
 * den {@code befehl.beschreibung.*}-Satz, den Paper schon fuer seine eigene
 * Befehlsliste bekommt. Damit gibt es je Befehl genau EINE Beschreibung, und wer sie
 * aendert, aendert beide Listen. Dass keine fehlt und keine doppelt steht, haelt
 * {@code HilfeplanTest} gegen {@link BundSatz} - in beide Richtungen.
 *
 * <p><b>Das RECHT ist dasselbe, das der Befehl selbst fragt</b>, und es steht als Verweis
 * auf dessen Konstante da ({@code LadenBefehle.RECHT}) und nicht als abgeschriebene
 * Zeichenkette. Eine Liste, die einen Befehl anbietet, den der Spieler dann nicht
 * ausfuehren darf, waere schlimmer als keine Liste: Sie macht aus einer fehlenden
 * Erlaubnis einen scheinbaren Fehler.
 *
 * @param gruppen die Bereiche in der Reihenfolge, in der sie im Chat erscheinen
 */
record Hilfeplan(List<Gruppe> gruppen) {

    /**
     * Das Recht fuer einen Befehl, den JEDER tippen darf.
     *
     * <p>Drei Befehle sind so: {@code /parzelle} und {@code /mark} fragen an der Wurzel
     * nur, ob dort ein Spieler steht, und {@code /freimarken} hat sein Recht an den
     * Zweigen (die Verwaltung) und nicht an der Wurzel. Ein erfundenes Recht fuer sie
     * waere eine Zeile, die nie etwas verbietet - und beim ersten Rangbau haette jemand
     * sie vergessen zu vergeben.
     */
    static final String JEDER = "";

    Hilfeplan {
        gruppen = List.copyOf(gruppen);
        if (gruppen.isEmpty()) {
            throw new IllegalArgumentException("Eine Hilfe ohne Gruppen waere eine"
                    + " Ueberschrift ohne Liste");
        }
        Set<String> gesehen = new LinkedHashSet<>();
        for (Gruppe gruppe : gruppen) {
            for (Eintrag eintrag : gruppe.eintraege()) {
                if (!gesehen.add(eintrag.befehl())) {
                    throw new IllegalArgumentException("/" + eintrag.befehl() + " steht"
                            + " in zwei Gruppen. Ein Spieler, der eine Liste zweimal"
                            + " denselben Befehl lesen sieht, sucht den Unterschied.");
                }
            }
        }
    }

    /**
     * Ein Bereich der Liste.
     *
     * @param name      der Satz mit der Ueberschrift - der Wortlaut gehoert dem Betreiber
     * @param eintraege die Befehle dieses Bereichs, in der Reihenfolge der Anzeige
     */
    record Gruppe(Satz name, List<Eintrag> eintraege) {

        Gruppe {
            if (name == null) {
                throw new IllegalArgumentException("Eine Gruppe braucht eine Ueberschrift");
            }
            eintraege = List.copyOf(eintraege);
            if (eintraege.isEmpty()) {
                throw new IllegalArgumentException(name.pfad() + " hat keinen Eintrag."
                        + " Eine Ueberschrift ohne Zeilen darunter sieht wie ein Fehler"
                        + " aus.");
            }
        }
    }

    /**
     * Ein Befehl in der Liste.
     *
     * @param befehl       der Name OHNE Schraegstrich - der Satz setzt ihn davor
     * @param recht        das Recht, das der Befehl selbst fragt, oder {@link #JEDER}
     * @param beschreibung der {@code befehl.beschreibung.*}-Satz, ein {@code Art.WORT}
     */
    record Eintrag(String befehl, String recht, Satz beschreibung) {

        Eintrag {
            if (befehl == null || befehl.isBlank()) {
                throw new IllegalArgumentException("Ein Eintrag ohne Befehl");
            }
            if (befehl.startsWith("/")) {
                throw new IllegalArgumentException(befehl + " traegt den Schraegstrich"
                        + " schon. Er gehoert in den Satz und nicht in die Daten - sonst"
                        + " steht er zweimal da.");
            }
            if (recht == null) {
                throw new IllegalArgumentException("/" + befehl + " braucht ein Recht"
                        + " oder Hilfeplan.JEDER");
            }
            if (beschreibung == null || beschreibung.art() != Satz.Art.WORT) {
                throw new IllegalArgumentException("/" + befehl + " braucht eine"
                        + " Beschreibung vom Typ WORT - sie wird in die Zeile EINGESETZT,"
                        + " und ein eingesetzter Wert ist ein Blatt ohne Farbtag.");
            }
        }

        /** Ob diesen Befehl jeder sehen darf. */
        boolean fuerJeden() {
            return JEDER.equals(recht);
        }
    }

    /**
     * Die Liste, die {@code /hilfe} zeigt.
     *
     * <p><b>Die Reihenfolge ist die eines Neulings und nicht die des Alphabets:</b> Zuerst
     * der Grund, auf dem er steht, dann das Geld, dann er selbst, dann die Wege, dann die
     * Welten, und zuletzt der Bund mit seinen Auskuenften. Wer eine alphabetische Liste
     * will, liest Papers eigene mit {@code /help}.
     */
    static final Hilfeplan ALLE = new Hilfeplan(List.of(
            new Gruppe(HilfeSatz.GRUPPE_GRUND, List.of(
                    new Eintrag("parzelle", JEDER, BundSatz.BEFEHL_PARZELLE),
                    new Eintrag("laden", LadenBefehle.RECHT, BundSatz.BEFEHL_LADEN))),
            new Gruppe(HilfeSatz.GRUPPE_GELD, List.of(
                    new Eintrag("mark", JEDER, BundSatz.BEFEHL_MARK),
                    new Eintrag("markt", MarktBefehle.RECHT, BundSatz.BEFEHL_MARKT),
                    new Eintrag("shop", ShopBefehle.RECHT, BundSatz.BEFEHL_SHOP))),
            new Gruppe(HilfeSatz.GRUPPE_SELBST, List.of(
                    new Eintrag("rang", RangBefehle.RECHT, BundSatz.BEFEHL_RANG),
                    new Eintrag("beruf", BerufeBefehle.RECHT, BundSatz.BEFEHL_BERUF))),
            new Gruppe(HilfeSatz.GRUPPE_REISEN, List.of(
                    new Eintrag("warp", WarpBefehle.RECHT_REISEN, BundSatz.BEFEHL_WARP),
                    new Eintrag("zurueck", RueckwegBefehle.RECHT_ZURUECK,
                            BundSatz.BEFEHL_ZURUECK),
                    new Eintrag("tpa", AnfragenBefehle.RECHT_TPA, BundSatz.BEFEHL_TPA),
                    new Eintrag("tpahere", AnfragenBefehle.RECHT_TPA,
                            BundSatz.BEFEHL_TPAHERE),
                    new Eintrag("tpaccept", AnfragenBefehle.RECHT_TPA,
                            BundSatz.BEFEHL_TPACCEPT),
                    new Eintrag("tpdeny", AnfragenBefehle.RECHT_TPA,
                            BundSatz.BEFEHL_TPDENY))),
            new Gruppe(HilfeSatz.GRUPPE_WELTEN, List.of(
                    new Eintrag("freistadt", FreistadtBefehle.RECHT,
                            BundSatz.BEFEHL_FREISTADT),
                    new Eintrag("werder", WerderBefehle.RECHT, BundSatz.BEFEHL_WERDER),
                    new Eintrag("wolke", WolkeBefehle.RECHT, BundSatz.BEFEHL_WOLKE))),
            new Gruppe(HilfeSatz.GRUPPE_SPIELE, List.of(
                    new Eintrag("lotterie", LotterieBefehle.RECHT,
                            BundSatz.BEFEHL_LOTTERIE),
                    new Eintrag("wettbewerb", WettbewerbBefehle.RECHT,
                            BundSatz.BEFEHL_WETTBEWERB))),
            new Gruppe(HilfeSatz.GRUPPE_BUND, List.of(
                    new Eintrag("menu", MenueBefehle.RECHT, BundSatz.BEFEHL_MENU),
                    new Eintrag("hilfe", HilfeBefehle.RECHT, BundSatz.BEFEHL_HILFE),
                    new Eintrag("regeln", RegelnBefehle.RECHT, BundSatz.BEFEHL_REGELN),
                    new Eintrag("freimarken", JEDER, BundSatz.BEFEHL_FREIMARKEN),
                    // /team steht in derselben Gruppe wie /freimarken und aus demselben
                    // Grund: Es ist eine Auskunft ueber den Bund und keine Taetigkeit
                    // eines Spielers. Mit dem Recht davor bleibt die Zeile einem
                    // gewoehnlichen Spieler verborgen - dafuer ist der Rechtefilter da.
                    //
                    // HIERHER GEHOERT ES ABER WIRKLICH: Tafel "Der Hilfeplan 1" haelt
                    // fest, dass jeder Befehl mit einer Beschreibung auch im Plan steht.
                    // Ein angemeldeter Befehl, den /hilfe nicht kennt, ist einer, den
                    // niemand findet - und zwar gerade der Betreiber, der ihn braucht.
                    new Eintrag("team", TeamBefehle.RECHT, BundSatz.BEFEHL_TEAM),
                    // /melden steht fuer JEDEN in der Liste [Schritt 16a Teil C] - das
                    // ist der Sinn der Einrichtung: Wer einen Vorfall sieht, soll den
                    // Befehl finden, ohne jemanden zu fragen. Darum JEDER und nicht
                    // MeldeBefehle.RECHT: Das Recht steht auf default true, aber die
                    // Zeile soll auch dann stehen, wenn ein Betreiber es umhaengt - wer
                    // es wirklich entzieht, nimmt den Eintrag hier mit heraus.
                    new Eintrag("melden", JEDER, BundSatz.BEFEHL_MELDEN),
                    new Eintrag("meldung", MeldungBefehle.RECHT,
                            BundSatz.BEFEHL_MELDUNG),
                    // /strafe mit dem Recht fuer die SPERRE davor [Teil B]: Es ist
                    // das schwerste der drei Artrechte, und wer es hat, hat die
                    // anderen beiden auch. Ein Helfer, der nur rauswerfen darf, sieht
                    // die Zeile damit nicht - das ist der Preis dafuer, dass ein
                    // Eintrag genau EIN Recht nennen kann. Er findet den Befehl ueber
                    // die Vervollstaendigung, denn das requires auf /strafe fragt
                    // JEDES Artrecht.
                    new Eintrag("strafe", StrafBefehle.RECHTE.bann(),
                            BundSatz.BEFEHL_STRAFE),
                    // /bannliste steht fuer JEDEN in der Liste, wie /melden - Plan
                    // 12c.4 macht sie ausdruecklich oeffentlich: Sie zwingt zu
                    // Begruendungen, die standhalten, und genau deshalb gehoert sie
                    // dazu. Eine Liste, die niemand findet, zwingt zu nichts.
                    new Eintrag("bannliste", JEDER,
                            BundSatz.BEFEHL_BANNLISTE),
                    // /einsehen [Teil D] mit seinem Recht davor: Es ist ein Zugriff
                    // auf fremdes Eigentum, und wer ihn nicht hat, soll die Zeile
                    // nicht lesen - sonst tippt er den Befehl und liest eine Absage.
                    new Eintrag("einsehen", EinsichtBefehle.RECHT,
                            BundSatz.BEFEHL_EINSEHEN),
                    // /portal [25.11.2026] mit seinem Recht davor - dasselbe
                    // Muster wie bei /einsehen: Wer keine Tore setzen darf, soll
                    // die Zeile nicht lesen. Sie steht hier und nicht im
                    // Hauptmenue (Menuetafel.HAUPT): Ein Menuefach fuer ein
                    // Werkzeug, das elf von elf Spielern nie brauchen, nimmt
                    // einem anderen den Platz.
                    new Eintrag("portal", PortalBefehle.RECHT,
                            BundSatz.BEFEHL_PORTAL)))));

    /** Jeder Befehl des Plans, in der Reihenfolge der Anzeige - fuer die Tafeln. */
    List<String> befehle() {
        List<String> namen = new ArrayList<>();
        for (Gruppe gruppe : gruppen) {
            for (Eintrag eintrag : gruppe.eintraege()) {
                namen.add(eintrag.befehl());
            }
        }
        return List.copyOf(namen);
    }

    /**
     * Das Recht, das dieser Befehl fragt - oder {@code null}, wenn der Plan ihn nicht
     * kennt.
     *
     * <p>Die Stelle, an der das Recht EINEN Ort behaelt [21.11.2026]. Das Hauptmenue
     * zeigt auf dieselben Befehle wie diese Liste und muss dieselbe Frage stellen
     * ({@link Menuetafel#fuerRechte}); schriebe es seine Rechte selbst ab, liefen zwei
     * Tafeln auseinander, und die eine liesse einen Befehl weg, den die andere anbietet.
     *
     * <p>{@code null} und keine Ausnahme: Der Aufrufer entscheidet, was ein unbekannter
     * Befehl bedeutet - fuer einen Menueeintrag ist er ein Baufehler und wird beim Bauen
     * abgewiesen.
     */
    String recht(String befehl) {
        for (Gruppe gruppe : gruppen) {
            for (Eintrag eintrag : gruppe.eintraege()) {
                if (eintrag.befehl().equals(befehl)) {
                    return eintrag.recht();
                }
            }
        }
        return null;
    }

    /** Jeder Eintrag des Plans, ueber die Gruppen hinweg - fuer die Tafeln. */
    List<Eintrag> eintraege() {
        List<Eintrag> alle = new ArrayList<>();
        for (Gruppe gruppe : gruppen) {
            alle.addAll(gruppe.eintraege());
        }
        return List.copyOf(alle);
    }
}
