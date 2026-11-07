package de.freimarken.paper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.freimarken.kern.Berufswerte;
import de.freimarken.kern.Verdienstwerte;
import de.freimarken.kern.Besitzbuch;
import de.freimarken.kern.Buchungsgrund;
import de.freimarken.kern.Parzelle;
import de.freimarken.kern.Preisstaffel;
import de.freimarken.kern.Rasterplan;
import de.freimarken.kern.Richtung;
import de.freimarken.kern.SpeicherKasse;
import de.freimarken.kern.SpeicherLager;
import de.freimarken.kern.Stadtgrenze;
import de.freimarken.kern.Wirtschaftsstand;
import de.freimarken.kern.Warpverzeichnis;
import de.freimarken.kern.Wirtschaftswerte;
import de.freimarken.kern.Zeitwerte;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Logger;
import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Die AUFRUFSTELLE, mitgeschrieben - was ein Zweig waehlt und was er uebergibt.
 *
 * <p>{@link SendewegTest} ruft die reinen Methoden unmittelbar auf und prueft ihr Bild.
 * Damit war die halbe Naht gedeckt und die andere nicht: WELCHE Methode ein Zweig waehlt,
 * WAS er ihr uebergibt und in welcher Reihenfolge er sendet, prueft eine Tafel ueber die
 * reine Methode nicht. Gemessen blieben alle vier dieser Eingriffe gruen - 356 Proben,
 * Rueckgabewert 0:
 *
 * <ul>
 *   <li>{@code infoKopfZeilen(sprache, pz, px)}: {@code /parzelle info} auf 3/-4 zeigte
 *       "Parzelle -4/3".</li>
 *   <li>Hoechstzahl und Gruppenfelder an {@code absageZeilen} getauscht: Die Absage nannte
 *       die Zahl der jeweils ANDEREN Grenze, und der Spieler richtete sich nach ihr.</li>
 *   <li>{@code aufgebenGruppeZeilen} durch {@code nichtDeineParzelleZeilen} ersetzt: Auf
 *       dem eigenen Gruppenkind las der Spieler "Das ist nicht deine Parzelle." - eine
 *       Luege, und er suchte den Eigentuemer.</li>
 *   <li>Ein Sendeaufruf ganz weg oder verdoppelt: {@code /mark} antwortete gar nicht, oder
 *       "ist wieder frei" stand zweimal da.</li>
 * </ul>
 *
 * <p><b>Das Muster ist nicht neu.</b> {@code SpielzeitBefehleTest.derSendewegGibtAllesAus}
 * schreibt mit einer {@code Audience} mit und sagt in seinem eigenen Kommentar genau diese
 * Fehlerklasse: "Geprueft war bisher nur, WAS in den Zeilen steht, nicht DASS sie
 * ankommen." {@code SendewegTest} zitiert {@code SpielzeitBefehle.sende} als Vorbild, hat
 * aber nur die reine Haelfte uebernommen.
 *
 * <p><b>Was hier NICHT geprueft wird, ist der Weg vom getippten Wort zum Zweig.</b>
 * Diese Probe ruft die Zweige unmittelbar auf - darum sind sie paketweit sichtbar.
 * Ob {@code /parzelle liste} wirklich bei {@code zeigeListe} landet, stand bis zum
 * 06.11.2026 nirgends; seitdem sagen es {@code MarkBefehlTest} und
 * {@code ParzelleBefehlTest}, die den echten Befehlsbaum ueber einen eigenen
 * {@code CommandDispatcher} laufen lassen. Zwei Fragen, zwei Proben: Diese hier
 * faengt die falsche Methode am richtigen Wort, jene die richtige Methode am
 * falschen Wort.
 *
 * <p><b>Ohne Server, ohne Bukkit-Laufzeit.</b> Das Besitzbuch ist das ECHTE, ueber
 * {@code SpeicherLager} und {@code SpeicherKasse}; die Welt ist {@link Probewelt},
 * dieselbe Marktstadt in einer Map, mit der {@code WegbauTest} rechnet. Nur
 * {@code World}, {@code Block} und {@code Player} sind Stellvertreter: Es sind
 * Schnittstellen, und die paar Methoden, die diese Zweige von ihnen brauchen,
 * stehen in {@link Serverprobe} und {@link Spielerprobe}. Ein Aufruf, der dort
 * nicht steht, wirft mit seinem Namen - eine stumme Null waere schlimmer als eine
 * rote Probe.
 *
 * <p><b>Hier standen bis zum 06.11.2026 eigene private Doppel</b> - gebaut mit
 * demselben {@code Proxy}-Gedanken wie {@link Serverprobe}, nur frueher. Als die
 * Wachen und die Befehle ihre Durchlaufproben bekamen, entstand derselbe Satz ein
 * zweites Mal, und erst danach fiel es auf. Jetzt gibt es einen Satz: Die drei
 * Methoden unten geben nur noch weiter, und was sie beantworten, steht dort.
 * Dass der Tausch nichts verschoben hat, sagen die vierunddreissig Proben dieser
 * Datei - sie liefen vorher und nachher gruen durch.
 *
 * <p><b>Hoechstzahl 7 und Gruppenfelder 3 sind ABSICHTLICH verschieden</b>, aus demselben
 * Grund wie in {@code SendewegTest} 33t: Mit zwei gleichen Zahlen waere ein Vertauschen der
 * Quellen gruen. Die Vorgabe des Kerns hat fuer beide die 4.
 *
 * <p><b>Seit dem 05.11.2026 stehen die drei Zweige von {@code /tpa} daneben</b> (34m bis
 * 34o). Dieselbe Luecke, nur schaerfer: Dort hat EIN Zug ZWEI Empfaenger, und
 * {@code GrenzeTest} 31e nagelt mit seiner Tafel der Sendepaare fest, welche
 * Zeilen-Methode an welche EMPFAENGERVARIABLE geht - nicht, WAS sie als Wert bekommt.
 * Gemessen blieben darum alle 418 Proben des Moduls gruen bei:
 *
 * <ul>
 *   <li>Die zwei Namen in {@code sendeAnnahme} getauscht: Gespielt las Bert nach
 *       {@code /tpaccept} "Bert kommt zu dir." - seinen eigenen Namen -, und Anna las
 *       "Anna hat angenommen - du kommst."</li>
 *   <li>Dasselbe in {@code sendeStellen}: Die Fragende las ihren eigenen Namen, und der
 *       Angefragte den seinen.</li>
 *   <li>Die Wache {@code weitere > 0} auf {@code weitere > 1} geaendert: Ein Spieler mit
 *       GENAU EINER weiteren offenen Anfrage erfuhr nichts mehr von ihr und liess sie
 *       ablaufen.</li>
 * </ul>
 *
 * <p>Fuer diese drei Proben gilt beides, was die zwoelf darueber stark macht: ZWEI
 * absichtlich verschiedene Namen (Anna und Bert - jeder Satz dieses Zweiges traegt genau
 * einen, und zwar den des ANDEREN) und JE SPIELER eine EIGENE Mitschrift. Mit einer
 * gemeinsamen Mitschrift waere ein vertauschter Empfaenger gruen, weil die Vereinigung
 * beider Zeilen dieselbe bleibt.
 */
class AufrufstelleTest {

    private static final UUID ANNA = UUID.nameUUIDFromBytes("anna".getBytes());
    private static final UUID BERT = UUID.nameUUIDFromBytes("bert".getBytes());

    /** Wie viele Parzellen der Rang erlaubt - nicht dieselbe Zahl wie die Felderzahl. */
    private static final int HOECHSTZAHL = 7;

    /** Wie viele Felder eine Gruppe haben darf. */
    private static final int GRUPPENFELDER = 3;

    private static final long REICH = 10_000_000L;

    /** Die Weltunterkante, wie in {@code WegbauTest}. */
    private static final int UNTEN = -64;

    private final Rasterplan plan = Rasterplan.marktstadt();
    private final Probewelt boden = new Probewelt(plan, UNTEN);

    private final List<Component> gesendet = new ArrayList<>();

    /**
     * Zwei Namen und zwei Mitschriften - fuer die Zweige mit ZWEI Empfaengern.
     *
     * <p>Getrennt und nicht in {@link #gesendet}: Nur so prueft eine Probe, WER welche
     * Zeile liest, und nicht bloss, dass alle Zeilen irgendwo angekommen sind.
     */
    private static final String NAME_ANNA = "Anna";

    private static final String NAME_BERT = "Bert";

    private final List<Component> annaLiest = new ArrayList<>();

    private final List<Component> bertLiest = new ArrayList<>();

    private World welt;
    private SpeicherKasse kasse;
    private Besitzbuch buch;
    private ParzellenBefehle befehle;

    @BeforeEach
    void vorher() {
        welt = weltDoppel();
        kasse = new SpeicherKasse(REICH);
        buch = new Besitzbuch(Stadtgrenze.marktstadt(), new SpeicherLager(), kasse,
                new Wirtschaftsstand(Wirtschaftswerte.VORGABE
                        .mitStartguthaben(REICH).mitHoechstzahl(HOECHSTZAHL)
                        .mitGruppenFeldern(GRUPPENFELDER)));
        befehle = new ParzellenBefehle(buch, welt,
                new Wirtschaftsstand(Wirtschaftswerte.VORGABE
                        .mitStartguthaben(REICH).mitHoechstzahl(HOECHSTZAHL)
                        .mitGruppenFeldern(GRUPPENFELDER)),
                Logger.getLogger("probe"), Sprachprobe.spracheAusDemJar(),
                // Keine Bonuszeile: Diese Proben halten /mark ohne Teil H.
                // Dass die Zeile dazukommt, prueft BonusBefehlTest.
                wer -> null);
        kasse.eroeffne(ANNA);
        kasse.eroeffne(BERT);
    }

    // --- Die Stellvertreter ----------------------------------------------------

    /**
     * Die Welt, soweit diese Zweige sie anfassen - aus dem geteilten Stellvertreter.
     *
     * <p>Vier Dinge: ihr Schluessel (daraus wird der Weltname der Ablage), der
     * Vergleich mit dem Ort des Spielers, und die zwei Blockfragen, die
     * {@code Weltgriff.AufDerWelt} weitergibt. Letztere laufen in die
     * {@link Probewelt} - damit rechnet die Bodenarbeit mit derselben Stadt, mit der
     * {@code WegbauTest} rechnet.
     *
     * <p><b>Bis zum 06.11.2026 stand hier ein eigenes Doppel</b>, gebaut mit demselben
     * {@code Proxy}-Gedanken wie {@link Serverprobe}. Zwei Orte fuer eine Sache waren
     * einer zu viel: Wer dem einen eine Antwort ergaenzte, musste an den anderen
     * denken. Jetzt gibt es nur noch einen, und was diese Probe braucht, steht dort.
     */
    private World weltDoppel() {
        return Serverprobe.welt("freimarken:marktstadt", boden);
    }

    /**
     * Ein Spieler, der mitschreibt statt zu lesen.
     *
     * <p>{@code getEffectivePermissions} gibt NICHTS zurueck - die {@link Spielerprobe}
     * baut die Eintraege aus den uebergebenen Rechten, und hier werden keine
     * uebergeben. Das ist die Sache selbst: Die Grenzen kommen damit aus der
     * Wirtschaft und nicht aus einem Recht, und das sind die zwei verschiedenen Zahlen
     * oben. Ein Recht hier waere eine zweite Quelle fuer dieselbe Zahl.
     *
     * <p>{@code schreibtNach(gesendet)}: Diese Probe liest die REIHENFOLGE ueber
     * mehrere Empfaenger hinweg, darum eine gemeinsame Mitschrift.
     */
    private Player spielerDoppel(UUID wer, Location wo) {
        return new Spielerprobe("Spielerdoppel", wer)
                .anOrt(wo)
                .schreibtNach(gesendet)
                .spieler();
    }

    /**
     * Ein Spieler mit NAMEN und eigener Mitschrift - fuer die drei Zweige von
     * {@code /tpa}.
     *
     * <p>Die drei Sendemethoden von {@code AnfragenBefehle} fragen ein Spielerobjekt
     * nach seinem NAMEN und schreiben ihm, nichts weiter. Die UUID wird aus dem Namen
     * gerechnet und von keiner dieser Stellen gelesen - sie steht nur da, weil eine
     * {@link Spielerprobe} nicht ohne auskommt, und sie ist dabei bei jedem Lauf
     * dieselbe.
     */
    private static Player spielerDoppel(String name, List<Component> mitschrift) {
        return new Spielerprobe(name, UUID.nameUUIDFromBytes(name.getBytes(
                        java.nio.charset.StandardCharsets.UTF_8)))
                .schreibtNach(mitschrift)
                .spieler();
    }

    // --- Kleinteile ------------------------------------------------------------

    /** Ein Ort INNERHALB der Parzelle - aus dem Plan gerechnet, nicht geraten. */
    private Location auf(int px, int pz) {
        return new Location(welt, plan.ursprungX(px) + 1, 70, plan.ursprungZ(pz) + 1);
    }

    /** Ein Ort auf dem Weg zwischen zwei Parzellen. */
    private Location aufDemWeg(int px, int pz) {
        return new Location(welt,
                plan.ursprungX(px) + plan.parzellenKante() + plan.wegBreite() / 2,
                70, plan.ursprungZ(pz) + 1);
    }

    private Player anna(Location wo) {
        return spielerDoppel(ANNA, wo);
    }

    /** Was der Spieler seit dem letzten Abruf gelesen hat - als &-Bild, Zeile fuer Zeile. */
    private String mitschrift() {
        return mitschrift(gesendet);
    }

    /**
     * Dasselbe fuer EINE von mehreren Mitschriften.
     *
     * <p>Geleert wird beim Abruf, wie oben: So ist jede Zusicherung die Zeilenfolge SEIT
     * dem letzten Abruf, und eine Probe mit zwei Zuegen kann beide trennen.
     */
    private static String mitschrift(List<Component> eigene) {
        String bild = Sprachprobe.bild(List.copyOf(eigene));
        eigene.clear();
        return bild.isEmpty() ? "" : bild + "\n";
    }

    /** Nimmt die Parzelle fuer Anna, ohne durch den Zweig zu gehen. */
    private Parzelle nimm(UUID wer, int px, int pz) {
        var entscheid = buch.nehmen(welt.key().asString(), wer, plan.ursprungX(px) + 1,
                plan.ursprungZ(pz) + 1, HOECHSTZAHL);
        assertTrue(entscheid.erteilt(), "die Vorbereitung muss durchgehen: "
                + entscheid.bescheid());
        return entscheid.parzelle().orElseThrow();
    }

    // --- Die Proben ------------------------------------------------------------

    @Test
    @DisplayName("34 - die Welt wird geprueft, und zwar BEVOR irgendetwas geschieht")
    void derZweigPrueftDieWeltZuerst() {
        // Eine Fremdwelt OHNE Bloecke, und das ist die Zusicherung selbst: Liest
        // der Zweig hier doch den Boden, wirft der Stellvertreter mit Grund statt
        // eine Antwort zu erfinden - die Weltpruefung kommt also wirklich zuerst.
        World andere = Serverprobe.welt("freimarken:anderswo");
        Player draussen = spielerDoppel(ANNA, new Location(andere, 1, 70, 1));

        befehle.zeigeInfo(draussen);

        // Genau EINE Zeile, und zwar die aus der Sprachdatei. Ein handgeschriebenes
        // Component.text(...) ohne Farbe laesst sich hier senden, ohne dass eines der
        // Tore in GrenzeTest rot wird - dieser Vergleich faengt es, weil er das Bild
        // nennt und nicht nur die Zahl.
        assertEquals("&cParzellen gibt es nur in der Marktstadt.\n", mitschrift());
    }

    @Test
    @DisplayName("34b - Zweig /mark: beide Zeilen gehen wirklich hinaus, Stand zuerst")
    void markSendetBeideZeilen() {
        kasse.gutschreiben(ANNA, 25_000L, Buchungsgrund.EROEFFNUNG);
        long stand = kasse.guthaben(ANNA);

        ParzellenBefehle.markZeilen(Sprachprobe.spracheAusDemJar(), stand, null)
                .forEach(anna(auf(0, 0))::sendMessage);

        assertEquals("""
                &7Du hast &610.025.000 Mark&7.
                &8/mark verlauf zeigt die letzten Buchungen.
                """, mitschrift());
    }

    @Test
    @DisplayName("34c - Zweig info: der Kopf nennt die Nummer in DIESER Reihenfolge")
    void infoNenntDieNummerRichtigHerum() {
        befehle.zeigeInfo(anna(auf(3, -4)));

        // 3/-4 und nicht -4/3: Die zwei Zahlen kommen aus zwei verschiedenen Methoden des
        // Rasterplans, und ein Tausch an dieser Aufrufstelle war fuer den Compiler in
        // Ordnung. Zwei verschiedene Zahlen, und eine davon negativ - mit 0/0 waere der
        // Tausch unsichtbar.
        assertEquals("""
                &6Parzelle 3/-4
                &7  frei · &agratis&8 · /parzelle nehmen
                """, mitschrift());
    }

    @Test
    @DisplayName("34d - Zweig info auf dem Weg und auf einer fremden Parzelle")
    void infoAufWegUndFremd() {
        befehle.zeigeInfo(anna(aufDemWeg(0, 0)));
        assertEquals("&7Du stehst auf einem Weg - der gehoert allen.\n", mitschrift());
    }

    @Test
    @DisplayName("34e - Zweig nehmen: die erste ist gratis, die zweite wird gerechnet")
    void nehmenErstGratisDannBezahlt() {
        befehle.nehmen(anna(auf(1, 1)));
        assertEquals("""
                &aParzelle 1/1 gehoert jetzt dir
                &7  Die erste ist gratis.
                """, mitschrift());

        befehle.nehmen(anna(auf(2, 1)));
        // Der Rest kommt aus der Kasse und nicht aus einer Null am Sendeort: Wer die
        // Wache in nehmenErteiltZeilen fasst, liest hier eine 0.
        assertEquals("""
                &aParzelle 2/1 gehoert jetzt dir
                &7  Bezahlt: 25.000 Mark · Rest: 9.975.000 Mark
                """, mitschrift());
    }

    @Test
    @DisplayName("34f - Zweig nehmen: eine fremde Parzelle, und die Absage am Limit")
    void nehmenSchonVergebenUndAmLimit() {
        nimm(BERT, 0, 1);

        // Dass die Absage am Limit die HOECHSTZAHL nennt und nicht die Felderzahl, haelt
        // diese Zeile: Die zwei Zahlen sind hier absichtlich verschieden.
        for (int zelle = 0; zelle < HOECHSTZAHL; zelle++) {
            nimm(ANNA, zelle, 3);
        }
        befehle.nehmen(anna(auf(1, 4)));
        assertEquals("""
                &cDu hast schon 7 Parzellen - mehr erlaubt dein Rang nicht.
                &eMehr erlaubt ein hoeherer Rang.
                """, mitschrift());
    }

    @Test
    @DisplayName("34g - Zweig aufgeben: das Gruppenkind bekommt SEINE Absage")
    void aufgebenAufDemGruppenkind() {
        nimm(ANNA, 0, 0);
        nimm(ANNA, 1, 0);
        var zusammen = buch.zusammenlegen(welt.key().asString(), ANNA,
                plan.ursprungX(0) + 1, plan.ursprungZ(0) + 1, Richtung.OST, GRUPPENFELDER);
        assertTrue(zusammen.erteilt(), "die Vorbereitung muss durchgehen: "
                + zusammen.bescheid());

        // Auf dem Gruppenkind - nicht auf dem Anker. Dort verweigert das Aufgeben, und
        // zwar mit dem Satz UEBER die Gruppe. Wurde hier nichtDeineParzelleZeilen
        // gewaehlt, las der Eigentuemer "Das ist nicht deine Parzelle." und suchte den
        // Eigentuemer seiner eigenen Parzelle.
        befehle.warneVorAufgeben(anna(auf(1, 0)));
        assertEquals("&cDie Parzelle gehoert zu einer Gruppe - erst /parzelle"
                + " auftrennen.\n", mitschrift());

        // Und die fremde Parzelle bekommt den anderen Satz - sonst waere die Zeile
        // darueber auch mit dem falschen Schluessel gruen.
        nimm(BERT, 5, 5);
        befehle.warneVorAufgeben(anna(auf(5, 5)));
        assertEquals("&cDas ist nicht deine Parzelle.\n", mitschrift());
    }

    @Test
    @DisplayName("34h - Zweig aufgeben: Warnung, falsche Nummer, und die Tat")
    void aufgebenWarnungUndTat() {
        nimm(ANNA, 3, -4);

        befehle.warneVorAufgeben(anna(auf(3, -4)));
        assertEquals("""
                &eParzelle 3/-4 aufgeben?
                &7  Es gibt kein Geld zurueck, und gebaut bleibt stehen.
                &c  /parzelle aufgeben bestaetigen 3 -4
                """, mitschrift());

        // Die Nummer im Befehl zeigt woandershin - dann geschieht nichts, und die Tat,
        // die ausblieb, ist die dieses Zweiges.
        befehle.aufgeben(anna(auf(3, -4)), 3, -5);
        assertEquals("&cDu stehst nicht auf Parzelle 3/-5. Nichts aufgegeben.\n",
                mitschrift());

        befehle.aufgeben(anna(auf(3, -4)), 3, -4);
        assertEquals("&aParzelle 3/-4 ist wieder frei.\n", mitschrift());
    }

    @Test
    @DisplayName("34i - Zweig zusammenlegen: die Gebuehr, der Boden und sein Wortstueck")
    void zusammenlegenNenntDenBodenRichtig() {
        nimm(ANNA, 0, 0);
        nimm(ANNA, 1, 0);

        befehle.warneVorZusammenlegen(anna(auf(0, 0)), Richtung.OST);
        assertEquals("""
                &eParzelle 0/0 mit 1/0 zusammenlegen?
                &7  Gebuehr: &620.000 Mark&7 · du hast 9.975.000 Mark
                &7  Der Weg dazwischen wird dein Bauland.
                &7  Beim Auftrennen kommt er zurueck - und raeumt alles weg, was dann
                &7  darauf steht.
                &e  /parzelle zusammenlegen ost bestaetigen 0 0
                """, mitschrift());

        befehle.zusammenlegen(anna(auf(0, 0)), Richtung.OST, 0, 0);
        // Die letzte Zeile ist der Beweis fuer das Wortstueck: Beim Zusammenlegen WIRD
        // der Weg Bauland, beim Auftrennen kommt er zurueck. Gegenseitig vertauscht las
        // der Spieler hier das Gegenteil dessen, was gerade geschah - und keine Probe
        // auf eine reine Methode faellt darueber, weil beide Woerter dort als Wert
        // hereinkommen.
        assertEquals("""
                &aEin Grundstueck aus 2 Feldern.
                &7  Bezahlt: 20.000 Mark · Rest: 9.955.000 Mark
                &7  Der Weg dazwischen ist Bauland (596 Bloecke geaendert).
                """, mitschrift());
    }

    @Test
    @DisplayName("34j - Zweig auftrennen: die Abschaetzung, und der Weg kommt zurueck")
    void auftrennenNenntAufwandUndBoden() {
        nimm(ANNA, 0, 0);
        nimm(ANNA, 1, 0);
        // Durch den ZWEIG zusammenlegen und nicht am Besitzbuch vorbei: Nur so fuellt die
        // Bodenarbeit den Weg wirklich, und nur dann hat der Rueckbau etwas zu tun.
        befehle.zusammenlegen(anna(auf(0, 0)), Richtung.OST, 0, 0);
        gesendet.clear();

        befehle.warneVorAuftrennen(anna(auf(0, 0)));
        assertEquals("""
                &eGrundstueck aus 2 Feldern ganz auftrennen?
                &7  ALLE Wege zwischen den Feldern kommen zurueck. Geld gibt es keines zurueck.
                &7  Fuer ein einzelnes Feld: stell dich darauf und nimm /parzelle auftrennen ohne "alles".
                &7  Auf der Wegflaeche steht nichts - es geht nichts verloren.
                &c  /parzelle auftrennen alles bestaetigen 0 0
                """, mitschrift());

        befehle.auftrennen(anna(auf(0, 0)), 0, 0);
        assertEquals("""
                &aAufgetrennt - 2 eigenstaendige Parzellen.
                &7  Der Weg ist zurueck (596 Bloecke geaendert).
                """, mitschrift());
    }

    @Test
    @DisplayName("34k - jeder Bescheid nennt die Zahl SEINER Grenze, auch durch absage()")
    void absageNenntDieZahlDerRichtigenGrenze() {
        for (int zelle = 0; zelle <= GRUPPENFELDER; zelle++) {
            nimm(ANNA, zelle, 0);
        }
        // Eine Gruppe aus allen erlaubten Feldern, dann eines mehr: Das ist der Bescheid
        // GRUPPE_ZU_GROSS, und er nennt die GRUPPENFELDER. Nennt er stattdessen die
        // Hoechstzahl, richtet sich der Spieler nach der falschen Grenze.
        //
        // Von Feld zu Feld und immer vom RAND aus: Wer zweimal von derselben Zelle nach
        // Osten legt, bekommt beim zweiten Mal SCHON_ZUSAMMEN - der Nachbar ist dann
        // schon dieselbe Gruppe.
        for (int zelle = 0; zelle + 1 < GRUPPENFELDER; zelle++) {
            var schritt = buch.zusammenlegen(welt.key().asString(), ANNA,
                    plan.ursprungX(zelle) + 1, plan.ursprungZ(0) + 1, Richtung.OST,
                    GRUPPENFELDER);
            assertTrue(schritt.erteilt(), "die Vorbereitung muss durchgehen: "
                    + schritt.bescheid());
        }
        gesendet.clear();

        befehle.warneVorZusammenlegen(anna(auf(GRUPPENFELDER - 1, 0)), Richtung.OST);
        assertEquals("""
                &cDein Rang erlaubt hoechstens 3 Felder je Gruppe.
                &eMehr erlaubt ein hoeherer Rang.
                """, mitschrift());

        // UND DERSELBE BESCHEID DURCH absage(). Der Zweig darueber faengt
        // GRUPPE_ZU_GROSS selbst ab; die BESTAETIGUNG geht durch die gemeinsame Absage,
        // und genau dort kommen beide Grenzen zusammen herein. Gemessen war dieser Ort von
        // keiner Probe erreicht - 34k lief ueber gruppeZuGrossZeilen, 34f ueber
        // nehmenGrenzeZeilen, und beide umgehen absage(). Ein Tausch der zwei Zahlen GENAU
        // DORT blieb darum gruen, und der Spieler richtet sich nach der falschen Grenze.
        befehle.zusammenlegen(anna(auf(GRUPPENFELDER - 1, 0)), Richtung.OST,
                GRUPPENFELDER - 1, 0);
        assertEquals("""
                &cDein Rang erlaubt hoechstens 3 Felder je Gruppe.
                &eMehr erlaubt ein hoeherer Rang.
                """, mitschrift());

        // Und die andere Haelfte derselben Zahlenwahl: derselbe Weg mit einem Bescheid,
        // der die PARZELLENZAHL nennt - hier 7 und nicht 3. Erst mit beiden Zeilen ist ein
        // Tausch in jeder Richtung rot, denn eine davon allein liesse die Vertauschung
        // zufaellig stimmen.
        for (int zelle = GRUPPENFELDER + 1; zelle < HOECHSTZAHL; zelle++) {
            nimm(ANNA, zelle, 0);
        }
        gesendet.clear();
        befehle.aufgeben(anna(auf(0, 5)), 0, 5);
        assertEquals("""
                &cDiese Parzelle gehoert noch niemandem - aber dein Rang erlaubt 7 Parzellen, und so viele hast du schon.
                &eMehr erlaubt ein hoeherer Rang.
                """, mitschrift());
    }

    @Test
    @DisplayName("34l - Zweig liste: der Kopf nennt Anzahl und Hoechstzahl, nicht umgekehrt")
    void listeNenntAnzahlUndHoechstzahl() {
        nimm(ANNA, 0, 0);
        nimm(ANNA, 1, 2);
        gesendet.clear();

        befehle.zeigeListe(anna(auf(0, 0)));
        assertEquals("""
                &6Deine Parzellen (2 von 7)
                &7  0/0 · gratis
                &7  1/2 · 25.000 Mark
                &8Die naechste kostet 50.000 Mark
                """, mitschrift());
    }

    // --- Die drei Zweige von /tpa: ZWEI Empfaenger in EINEM Zug -----------------
    //
    // Anna fragt, Bert entscheidet. Der Zweig selbst ist hier nicht aufrufbar - er haengt
    // an Bukkit.getPlayerExact, Bukkit.getPlayer und Reise, und beides gibt es ohne
    // laufenden Server nicht. Genau darum sind die drei Sendestellen reine Methoden ueber
    // zwei Spielerobjekten geworden; die Begruendung steht im Javadoc von
    // AnfragenBefehle.sendeStellen.
    //
    // Was in diesem Zweig NEBEN den drei Sendestellen steht, bleibt damit ausserhalb
    // dieser drei Proben: die Weiterreise, die nimmAn danach ausloest. Sie bekommt den
    // einen Spieler als Empfaenger und den Namen des ANDEREN mitgegeben, und auch das ist
    // vertauschbar - festgenagelt am Quelltext von GrenzeTest 31m, seit das Reisen seine
    // Saetze aus der Sprachdatei liest.
    //
    // Was diese drei Proben halten und SendewegTest 33y bis 33ab NICHT halten koennen:
    // dort werden die Zeilen-Methoden unmittelbar aufgerufen, der Empfaenger kommt gar
    // nicht vor, und der Name wird als Wert hereingegeben. Hier kommt der Name aus dem
    // Spielerobjekt, und jeder der beiden hat seine eigene Mitschrift.

    @Test
    @DisplayName("34m - Zweig frage: die zwei Namen gehen GEGENLAEUFIG hinaus")
    void frageSetztDieNamenGegenlaeufigEin() {
        Sprache sprache = Sprachprobe.spracheAusDemJar();
        Player anna = spielerDoppel(NAME_ANNA, annaLiest);
        Player bert = spielerDoppel(NAME_BERT, bertLiest);

        AnfragenBefehle.sendeStellen(sprache, anna, bert, Anfragen.Richtung.ZU_DIR);

        // Jeder liest den Namen des ANDEREN. Wer die zwei Werte an der Aufrufstelle
        // tauscht, laesst Anna "Angefragt bei Anna" lesen und Bert "Bert moechte zu dir
        // kommen" - die Empfaenger bleiben dabei richtig, und das ist alles, was
        // GrenzeTest 31e sieht.
        assertEquals("&7Angefragt bei Bert - er oder sie muss zustimmen.\n",
                mitschrift(annaLiest));
        assertEquals("""
                &6Anna moechte zu dir kommen.
                &7/tpaccept zum Annehmen, /tpdeny zum Ablehnen. Die Anfrage laeuft nach einer Minute ab.
                """, mitschrift(bertLiest));

        // /tpahere: EIN anderer Satz an den Angefragten, und der Fragende liest weiter
        // genau seine eine Quittung. Waehlt die Aufrufstelle die Richtung nach Augenmass,
        // ist eine der zwei Zeilen hier rot.
        AnfragenBefehle.sendeStellen(sprache, anna, bert, Anfragen.Richtung.HIERHER);

        assertEquals("&7Angefragt bei Bert - er oder sie muss zustimmen.\n",
                mitschrift(annaLiest));
        assertEquals("""
                &6Anna laedt dich ein zu kommen.
                &7/tpaccept zum Annehmen, /tpdeny zum Ablehnen. Die Anfrage laeuft nach einer Minute ab.
                """, mitschrift(bertLiest));
    }

    @Test
    @DisplayName("34n - Zweig nimmAn: beide Richtungen, und die Wache ueber die uebrigen")
    void nimmAnSetztDieNamenEinUndMeldetDieEineWeitere() {
        Sprache sprache = Sprachprobe.spracheAusDemJar();
        Player anna = spielerDoppel(NAME_ANNA, annaLiest);
        Player bert = spielerDoppel(NAME_BERT, bertLiest);

        // /tpa: Anna hatte gefragt, also wandert SIE - Bert tippt /tpaccept und bleibt
        // stehen. annehmerWandert ist damit false. Und weitere == 0: Es wartet nichts
        // mehr, und dann gibt es die dritte Zeile ueberhaupt nicht.
        AnfragenBefehle.sendeAnnahme(sprache, bert, anna, false, 0L);

        assertEquals(1, bertLiest.size(), "bei Null wartenden Anfragen liest der Annehmer"
                + " GENAU seine eine Zeile - eine zweite waere ein Hinweis auf nichts");
        assertEquals(1, annaLiest.size(), "der Fragende liest genau seine Quittung");
        assertEquals("&aAnna kommt zu dir.\n", mitschrift(bertLiest));
        assertEquals("&aBert hat angenommen - du kommst.\n", mitschrift(annaLiest));

        // /tpahere: dieselben zwei Spieler, dieselben zwei Empfaenger - aber der Annehmer
        // wandert selbst. Vier Faelle, vier Saetze; dass es wirklich vier verschiedene
        // sind, haelt SendewegTest 33ab.
        AnfragenBefehle.sendeAnnahme(sprache, bert, anna, true, 0L);

        assertEquals("&aDu kommst zu Anna.\n", mitschrift(bertLiest));
        assertEquals("&aBert hat angenommen und kommt zu dir.\n", mitschrift(annaLiest));

        // GENAU EINE weitere Anfrage - der Fall, den die Wache festhalten muss. Auf
        // "weitere > 1" geaendert erfaehrt Bert nichts von ihr und laesst sie ablaufen,
        // und ohne diese Zeilen bleiben 418 Proben gruen. Der Hinweis geht an den
        // ANNEHMER und nicht an den Fragenden: Es sind Anfragen an Bert.
        AnfragenBefehle.sendeAnnahme(sprache, bert, anna, false, 1L);

        assertEquals(2, bertLiest.size(), "der Annehmer liest seine Zeile UND den Hinweis"
                + " auf die eine Anfrage, die noch wartet");
        assertEquals(1, annaLiest.size(), "der Fragende liest nur seine Quittung - wie"
                + " viele Anfragen an BERT noch offen sind, geht ihn nichts an");
        assertEquals("""
                &aAnna kommt zu dir.
                &7Es warten noch 1 Anfrage.
                """, mitschrift(bertLiest));
        assertEquals("&aBert hat angenommen - du kommst.\n", mitschrift(annaLiest));
    }

    @Test
    @DisplayName("34o - Zweig lehneAb: an den Ablehnenden, und an niemanden sonst")
    void lehneAbSendetNurAnDenAblehnenden() {
        Sprache sprache = Sprachprobe.spracheAusDemJar();
        Player anna = spielerDoppel(NAME_ANNA, annaLiest);
        Player bert = spielerDoppel(NAME_BERT, bertLiest);

        // Zwei weggenommene Anfragen - und Anna, die eine davon gestellt hatte, liest
        // NICHTS. Das ist Absicht: Wer ablehnt, muss das nicht rechtfertigen, und eine
        // Zeile an den Fragenden waere ein neuer Satz und kein Umzug.
        AnfragenBefehle.sendeAblehnung(sprache, bert, 2L);

        assertEquals("&72 Anfragen abgelehnt.\n", mitschrift(bertLiest));
        assertEquals("", mitschrift(annaLiest));

        // Die Null ist KEIN stummer Fall: Gesendet wird in beiden Faellen, nur ein
        // anderer Satz - die Wache dafuer steht in lehneAbZeilen und nicht hier.
        AnfragenBefehle.sendeAblehnung(sprache, bert, 0L);

        assertEquals("&7Es fragt niemand.\n", mitschrift(bertLiest));
        assertEquals("", mitschrift(annaLiest));

        // Und derselbe Weg mit dem ANDEREN Spieler als Ablehnendem. Das prueft das
        // Werkzeug dieser drei Proben: Fuellten beide Stellvertreter in Wahrheit
        // DIESELBE Liste, waere die leere Mitschrift darueber kein Befund - hier liest
        // nun Anna und Bert nichts, also sind sie wirklich getrennt.
        AnfragenBefehle.sendeAblehnung(sprache, anna, 1L);

        assertEquals("&71 Anfrage abgelehnt.\n", mitschrift(annaLiest));
        assertEquals("", mitschrift(bertLiest));
    }

    // --- Reise und Aufwaermzeit: EIN Leser, und der Name gehoert einem ANDEREN --
    //
    // Die gespiegelte Lage zu /tpa, und sie braucht dasselbe Werkzeug. Bei /tpaccept
    // berechnet AnfragenBefehle zwei Spieler - "wandert" und "bleibt" - und ruft dann
    // reise.fuehre(wandert, bleibt.getLocation(), bleibt.getName()): Gesendet wird an den
    // WANDERNDEN, genannt wird der Name des BLEIBENDEN. Anna reist und liest "geht es
    // nach Bert"; Bert liest aus diesen zwei Dateien NICHTS.
    //
    // Vertauscht man Empfaenger und Name dort, liest der Reisende seinen EIGENEN Namen -
    // und wird ausserdem der falsche Spieler teleportiert. Diese zwei Proben halten die
    // eine Haelfte: dass GENAU EINER liest und dass der uebergebene Wert im Satz steht.
    // Die andere Haelfte - dass die Aufrufstelle die zwei nicht vertauscht - nagelt
    // GrenzeTest 31m am Quelltext fest, denn der Zweig selbst ist ohne laufenden Server
    // nicht aufrufbar (Bukkit.getPlayer, und fuer Reise gibt es keinen Stellvertreter:
    // sie braucht eine Aufwaermzeit, und die einen JavaPlugin - eine KLASSE, kein
    // Stellvertreter).
    //
    // JE SPIELER EINE EIGENE MITSCHRIFT, und das ist hier der ganze Punkt: Mit einer
    // gemeinsamen Liste waere "Bert liest nichts" kein Befund, weil die Vereinigung
    // beider Zeilen dieselbe bleibt.

    @Test
    @DisplayName("34p - Reise sendet an den Reisenden, und nennt den Namen des anderen")
    void reiseSendetNurAnDenReisenden() {
        Sprache sprache = Sprachprobe.spracheAusDemJar();
        Player anna = spielerDoppel(NAME_ANNA, annaLiest);
        Player bert = spielerDoppel(NAME_BERT, bertLiest);

        // Anna liest, Berts Name steht im Satz - die gespiegelte Lage von /tpaccept,
        // hier mit dem Satz nachgestellt, den die Probe ohne Server erreichen kann.
        // Aus /tpaccept ist DIESE Zeile nicht erreichbar: Sie haengt an der
        // WELT-Fassung von fuehre, und die rufen nur WarpBefehle und Torwache. Darum
        // sagt ReiseSatz.SCHON_DA auch, "Du bist schon in Bert." koenne es im Spiel
        // nicht geben - was diese Probe hier nachstellt, ist der Empfaengerweg und
        // nicht der Spielfall.
        Reise.sendeSchonDa(sprache, anna, NAME_BERT);

        assertEquals("&7Du bist schon in Bert.\n", mitschrift(annaLiest));
        assertEquals("", mitschrift(bertLiest), "aus diesen zwei Dateien liest der"
                + " Bleibende NICHTS - eine Zeile an ihn waere ein neuer Satz");

        // Diese Zeile ist nur aus der WELT-Fassung erreichbar, der Wert also immer ein
        // Weltname. Dass der Satz auch mit einem Spielernamen uebersetzt, ist genau der
        // Grund, warum der Platzhalter nicht {welt} heisst.
        Reise.sendeKeinPlatzAmErscheinungspunkt(sprache, anna, "kornmark");

        assertEquals("&cIn kornmark ist am Erscheinungspunkt kein Platz zum Stehen. Das"
                + " ist nichts, was du beheben kannst - melde es.\n",
                mitschrift(annaLiest));
        assertEquals("", mitschrift(bertLiest));

        // Aus dem Rueckruf von teleportAsync - aus allen fuenf Aufrufern erreichbar, und
        // darum mit der Wendung von /zurueck geprueft.
        Reise.sendeNichtGeklappt(sprache, anna, "deinen letzten Ort");

        assertEquals("&cDie Reise nach deinen letzten Ort hat nicht geklappt.\n",
                mitschrift(annaLiest));
        assertEquals("", mitschrift(bertLiest));

        // Und derselbe Weg mit dem ANDEREN als Reisendem. Das prueft das Werkzeug:
        // Fuellten beide Stellvertreter in Wahrheit DIESELBE Liste, waeren die leeren
        // Mitschriften darueber kein Befund.
        Reise.sendeSchonDa(sprache, bert, NAME_ANNA);

        assertEquals("&7Du bist schon in Anna.\n", mitschrift(bertLiest));
        assertEquals("", mitschrift(annaLiest));
    }

    @Test
    @DisplayName("34q - Aufwaermzeit sendet an den Reisenden, Wartezeit und Abbruch")
    void aufwaermzeitSendetNurAnDenReisenden() {
        Sprache sprache = Sprachprobe.spracheAusDemJar();
        Player anna = spielerDoppel(NAME_ANNA, annaLiest);
        Player bert = spielerDoppel(NAME_BERT, bertLiest);

        // Die drei Sekunden kommen aus Aufwaermzeit.DAUER und sind ein WERT. Wer sie als
        // Wort in die Sprachdatei schreibt, laesst den Satz an dem Tag luegen, an dem die
        // Konstante sich aendert - darum geht hier eine andere Zahl durch denselben Satz.
        Aufwaermzeit.sendeWartezeit(sprache, anna, Aufwaermzeit.DAUER.toSeconds(),
                NAME_BERT);

        assertEquals("&7Nach 3 Sekunden geht es nach Bert - bis dahin stehen bleiben.\n",
                mitschrift(annaLiest));
        assertEquals("", mitschrift(bertLiest));

        Aufwaermzeit.sendeWartezeit(sprache, anna, 10L, NAME_BERT);

        assertEquals("&7Nach 10 Sekunden geht es nach Bert - bis dahin stehen bleiben.\n",
                mitschrift(annaLiest));

        // Beide Abbruchgruende, und beide an den Reisenden. Der Bleibende erfaehrt nichts
        // davon, dass die Reise zu ihm abgebrochen ist - heute so, und das bleibt so.
        Aufwaermzeit.sendeAbbruch(sprache, anna, Aufwaermzeit.Abbruchgrund.BEWEGT,
                NAME_BERT);

        assertEquals("&cBewegt - die Reise nach Bert ist abgebrochen.\n",
                mitschrift(annaLiest));
        assertEquals("", mitschrift(bertLiest));

        Aufwaermzeit.sendeAbbruch(sprache, anna, Aufwaermzeit.Abbruchgrund.SCHADEN,
                NAME_BERT);

        assertEquals("&cSchaden genommen - die Reise nach Bert ist abgebrochen.\n",
                mitschrift(annaLiest));
        assertEquals("", mitschrift(bertLiest));

        // Das Werkzeug gegengeprueft, wie in 34p.
        Aufwaermzeit.sendeAbbruch(sprache, bert, Aufwaermzeit.Abbruchgrund.SCHADEN,
                NAME_ANNA);

        assertEquals("&cSchaden genommen - die Reise nach Anna ist abgebrochen.\n",
                mitschrift(bertLiest));
        assertEquals("", mitschrift(annaLiest));
    }

    // --- /warp und /zurueck: hier wird der ZWEIG wirklich durchlaufen -----------
    //
    // Der Unterschied zu 34p und 34q, und er ist der ganze Gewinn des zweiten Zuges von
    // Schritt 5: Dort erreichen die Proben nur die Sendewege, weil Reise und Aufwaermzeit
    // ohne Server nicht zu bauen sind. Diese zwei Befehle dagegen sind in ihren ABSAGEN
    // vollstaendig ohne Server erreichbar - keiner der vier Zweige, die etwas sagen,
    // fasst Reise an, und was sie sonst brauchen (Warpverzeichnis, Rueckweg) sind reine
    // Klassen ohne Bukkit. Darum geht hier der ECHTE Zweig durch, mit dem echten
    // Warpverzeichnis und dem echten Rueckweg.

    @Test
    @DisplayName("34r - Zweig /warp: der Tipper liest die drei Antworten, und zwar diese")
    void warpZweigeSendenAnDenTipper() {
        Sprache sprache = Sprachprobe.spracheAusDemJar();
        var verzeichnis = Warpverzeichnis.ausWelten(
                List.of("marktstadt", "kornmark", "nether"));
        // Die Weltenkarte ist LEER, die Liste nicht: Genau die Lage des dritten Zweiges -
        // der Name steht in der config.yml, die Welt ist nicht entstanden. reise bleibt
        // null, weil kein Zweig dieser Probe sie anfasst; ein Stellvertreter fuer eine
        // KLASSE ist ohne Bukkit.setServer nicht baubar. Seit dem 06.11.2026 gibt
        // es einen Server-Stellvertreter (Serverprobe), und der Befehlsbaum von
        // /freimarken steht in BundBefehle - FreimarkenBefehlTest tippt ihn.
        // Die Aufwaermzeit bleibt davon unberuehrt: Sie braucht eine
        // JavaPlugin-INSTANZ, und die prueft im Konstruktor den Klassenlader.
        var befehle = new WarpBefehle(verzeichnis, Map.of(), null, sprache);
        Player anna = spielerDoppel(ANNA, auf(0, 0));

        befehle.zeigeZiele(anna);

        assertEquals("&6Reiseziele\n"
                + "&bmarktstadt  kornmark  nether\n"
                + "&7/warp <ziel> - oder durch ein Tor am Bundesmarkt.\n",
                mitschrift());

        befehle.reiseAn(anna, "mordor");

        assertEquals("&cDorthin geht keine Reise: mordor\n"
                + "&7Moeglich sind: marktstadt, kornmark, nether\n", mitschrift());

        // GROSS getippt, und das ist der Zweck dieser Zeile: aufgeloest() nimmt jede
        // Schreibweise an und gibt den KLEINEN Namen aus der config.yml zurueck. Im
        // Zweig von WarpBefehle.reiseAn sind die getippte Eingabe und der aufgeloeste
        // Name gleichzeitig im Geltungsbereich und vom selben Typ - wer die Eingabe in
        // die Absage gibt, uebersetzt anstandslos, und keine Zaehltafel und keine
        // Platzhalterprobe sieht es. SICHTBAR wird es allein an der Schreibweise, und
        // darum steht hier KORNMARK und nicht kornmark.
        befehle.reiseAn(anna, "KORNMARK");

        assertEquals("&cDie Welt kornmark gibt es gerade nicht.\n", mitschrift(),
                "KLEIN geschrieben, obwohl GROSS getippt wurde - genannt wird der Name"
                        + " aus der config.yml und nicht die Eingabe. Steht hier"
                        + " dagegen GAR NICHTS, dann sendet der Zweig den Satz"
                        + " ueberhaupt nicht mehr - zwei Fehler, eine Probe, und der"
                        + " Vergleich darunter sagt, welcher es ist.");

        // Die drei stillen Rueckkehren: Ein Nicht-Spieler liest nichts. Durch requires
        // praktisch unerreichbar, aber vorhanden - und eine Probe, die den Zweig ruft,
        // soll nicht an einer Ausnahme scheitern.
        befehle.zeigeZiele("die Konsole");
        befehle.reiseAn("die Konsole", "mordor");

        assertEquals("", mitschrift(), "ein Nicht-Spieler bekommt keine Zeile");
    }

    @Test
    @DisplayName("34s - Zweig /zurueck: ohne gemerkten Ort liest der Tipper die Absage")
    void rueckwegZweigSendetAnDenTipper() {
        Sprache sprache = Sprachprobe.spracheAusDemJar();
        // Der ECHTE Rueckweg und keine Attrappe: Die Klasse ist eine Karte im Speicher
        // und braucht keinen Server. Leer, also greift der Zweig der Absage - und der
        // fasst reise nicht an.
        var befehle = new RueckwegBefehle(new Rueckweg(), null, sprache);
        Player anna = spielerDoppel(ANNA, auf(0, 0));

        befehle.fuehreZurueck(anna);

        assertEquals("&7Es ist kein Ort gemerkt - du bist noch nirgends gewesen, wohin"
                + " es zurueckgeht.\n", mitschrift());

        befehle.fuehreZurueck("die Konsole");

        assertEquals("", mitschrift(), "ein Nicht-Spieler bekommt keine Zeile");
    }
}
