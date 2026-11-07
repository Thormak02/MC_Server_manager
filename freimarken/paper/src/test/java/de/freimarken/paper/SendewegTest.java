package de.freimarken.paper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mojang.brigadier.StringReader;
import de.freimarken.kern.Art;
import de.freimarken.kern.Berufsregel;
import de.freimarken.kern.Besitzbuch;
import de.freimarken.kern.Buchung;
import de.freimarken.kern.Buchungsgrund;
import de.freimarken.kern.Taetigkeit;
import de.freimarken.kern.Parzelle;
import de.freimarken.kern.Richtung;
import de.freimarken.kern.Wirtschaftswerte;
import de.freimarken.kern.Zustand;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Zahl, Reihenfolge und Werte je Zweig - auf der NAHT.
 *
 * <p>Die Naht ist je Zweig eine reine Methode, die aus einfachen Werten ein Component oder
 * eine Liste baut, ohne {@code Player} und ohne Bukkit. In Bauschritt 1 ist das
 * {@link Verlaufszeile#zeile}, in Bauschritt 2 sind es die {@code ...Zeilen}-Methoden von
 * {@link ParzellenBefehle}. Das ist kein neues Muster, sondern das Muster dieses
 * Projekts: {@code SpielzeitBefehle.sende(Audience, String, List)} (:194-200) macht es
 * schon, und sein Javadoc nennt den Grund - "damit eine Probe eine Audience mitschreiben
 * lassen kann: Geprueft ist damit nicht nur, WAS in den Zeilen steht, sondern auch, DASS
 * sie gesendet werden".
 *
 * <p>Hier haengt ausserdem die Deckung der einen mechanischen Familie, die
 * {@code GrenzeTest.jederSchluesselWirdGesendet} nicht per Textsuche findet: Die sechs
 * Buchungsgruende werden nirgends namentlich getippt, ihr Pfad entsteht aus dem
 * Aufzaehlungsnamen. {@link #jederBuchungsgrundHatSeinWortInDerZeile} schickt jeden von
 * ihnen durch die ECHTE Zeile - faellt die Zuordnung weg, ist diese Probe rot.
 *
 * <p><b>Verglichen wird je Zweig die GANZE Tafel</b>, nicht Zeile fuer Zeile: Zahl der
 * Zeilen, ihre Reihenfolge und je Zeile das {@code &}-Bild. Eine fehlende Zeile, zwei
 * vertauschte Zeilen und ein nicht uebergebener Wert sind damit derselbe rote Vergleich.
 *
 * <p><b>Kein erwartetes Bild in dieser Datei ist aus dem Quelltext abgeschrieben.</b> Jede
 * Tafel wurde mit der Erwartung "ROT" geschrieben, der Probenlauf war rot, und der
 * tatsaechliche Wert kam aus der Fehlermeldung. Danach wurde jede Zeile gegen das Inventar
 * gegengelesen. Deshalb kann kein Verlesen gleichzeitig in der Vorlage und in der Probe
 * landen - das war das Loch, an dem die erste Fassung des Bauplans hing.
 */
class SendewegTest {

    private static final UUID ANNA = UUID.nameUUIDFromBytes("anna".getBytes());
    private static final UUID BERT = UUID.nameUUIDFromBytes("bert".getBytes());
    private static final ZoneId BERLIN = ZoneId.of("Europe/Berlin");

    /** 02.11.2026, 13:37 UTC - in Berlin ist das 14:37 (Winterzeit). */
    private static final Instant MITTAG = Instant.parse("2026-11-02T13:37:00Z");

    /**
     * Die Werbezeile - aus der Konstante des Kerns, nicht abgeschrieben.
     *
     * <p>{@code Wirtschaftswerte.VORGABE_RANG_WERBUNG} ist genau der Satz, den die
     * Werbung sendet, wenn die Zeile in der {@code config.yml} fehlt. Wer ihn dort
     * aendert, aendert ihn hier mit, und die Tafeln werden rot, bis das gemessene Bild
     * nachgezogen ist. Das ist gewollt: Der Satz steht im Kern und nicht in der Probe.
     */
    private static final String WERBUNG = Wirtschaftswerte.VORGABE_RANG_WERBUNG;

    private final Sprache sprache = Sprachprobe.spracheAusDemJar();

    private static Buchung buchung(Buchungsgrund grund, long betrag, long nachher) {
        return new Buchung(1L, MITTAG, ANNA, grund, betrag, nachher);
    }

    /** Eine Parzelle fuer die Liste - ein Satz aus dem Kern, kein Serverobjekt. */
    private static Parzelle feld(int x, int z, long preis, Set<UUID> mitbauer,
                                 boolean inGruppe) {
        return new Parzelle(1L, "marktstadt", x, z, ANNA, mitbauer, preis, Instant.EPOCH,
                Art.PARZELLE, inGruppe ? 7L : Parzelle.KEIN_ELTERNTEIL, Zustand.AKTIV);
    }

    /**
     * Die Tafel eines Zweiges: je Fall eine Marke und darunter das Bild seiner Zeilen.
     *
     * <p>Eine Marke je Fall und EIN Vergleich je Zweig - nicht ein Vergleich je Fall:
     * Tauschen zwei Faelle ihre Saetze, waeren zwei einzelne Vergleiche beide rot, aber
     * erst die ganze Tafel zeigt, WELCHE zwei. Ein Fall ohne Zeile steht als Marke ohne
     * Bild darunter; das ist der Fall "es wird gar nichts gesendet" (Bauplan 5).
     */
    private static final class Tafel {

        private final StringBuilder text = new StringBuilder();

        Tafel fall(String name, List<Component> zeilen) {
            text.append("== ").append(name).append('\n');
            String bild = Sprachprobe.bild(zeilen);
            if (!bild.isEmpty()) {
                text.append(bild).append('\n');
            }
            return this;
        }

        String ganz() {
            return text.toString();
        }
    }

    @Test
    @DisplayName("33 - die Verlaufszeile traegt alle vier Werte, in dieser Reihenfolge")
    void verlaufszeileTraegtAlleVierWerte() {
        Sprache sprache = Sprachprobe.spracheAusDemJar();

        var zeile = Verlaufszeile.zeile(sprache,
                buchung(Buchungsgrund.PARZELLE_GEKAUFT, -25_000L, 25_000L), BERLIN);

        // Die ganze Zeile und nicht vier einzelne contains: Vertauscht jemand zwei Werte,
        // waeren vier contains alle vier gruen.
        assertEquals("&7  02.11. 14:37  -25.000 Mark  Parzelle genommen"
                + "  (Stand 25.000 Mark)", Sprachprobe.bild(zeile));
    }

    @Test
    @DisplayName("33b - jeder Buchungsgrund hat sein Wort in der echten Zeile")
    void jederBuchungsgrundHatSeinWortInDerZeile() {
        Sprache sprache = Sprachprobe.spracheAusDemJar();
        for (Buchungsgrund grund : Buchungsgrund.values()) {
            // Das Vorzeichen muss zur Richtung des Grundes passen - die Regel steht in
            // Buchung und wirft sonst (Buchung.java:39-43).
            long betrag = grund.istAusgabe() ? -1_000L : 1_000L;
            String wort = sprache.wort(Sprachschluessel.grund(grund), Einsatz.keine());
            String zeile = Sprachprobe.nurText(Verlaufszeile.zeile(sprache,
                    buchung(grund, betrag, 2_000L), BERLIN));

            assertTrue(wort != null && !wort.isBlank(), "ohne Wort: " + grund);
            assertTrue(zeile.contains("  " + wort + "  "), grund + " steht nicht in der"
                    + " Zeile: " + zeile);
            assertTrue(!wort.equals(grund.schluessel()), "der Datenbankschluessel ist kein"
                    + " Wort fuer Spieler: " + grund);
        }
    }

    @Test
    @DisplayName("33c - die Familie der Buchungsgruende ist vollstaendig, in beide Richtungen")
    void grundIstVollstaendig() {
        var ausDenGruenden = new TreeSet<String>();
        for (Buchungsgrund grund : Buchungsgrund.values()) {
            ausDenGruenden.add(Sprachschluessel.grund(grund).pfad());
        }
        var ausDerAufzaehlung = new TreeSet<String>();
        for (MarkSatz satz : MarkSatz.values()) {
            if (satz.pfad().startsWith("mark.verlauf.grund.")) {
                ausDerAufzaehlung.add(satz.pfad());
            }
        }
        // In BEIDE Richtungen: Rot, wenn ein Grund kein Wort hat, UND rot, wenn ein Wort
        // zu keinem Grund mehr gehoert - dann steht es in der Datei und wirkt nie.
        assertEquals(ausDerAufzaehlung, ausDenGruenden);
        // ENTHAELT und nicht GLEICH, und das ist am 04.11.2026 geaendert worden: Seit
        // Bauschritt 2 gibt es eine ZWEITE mechanische Familie, die Bescheide. Die
        // Deckungsmenge ist damit die Vereinigung, und dass sie GENAU die Vereinigung
        // ist - nichts mehr und nichts weniger - sichert die Probe 33x darunter zu.
        // Vorher stand hier assertEquals; das war fuer einen einzigen Bereich richtig
        // und waere jetzt eine Zusicherung, die kein Bauschritt mehr halten kann.
        assertTrue(new TreeSet<>(Sprachschluessel.familienDeckung())
                        .containsAll(ausDenGruenden),
                "GrenzeTest laesst nur die Pfade aus familienDeckung als"
                        + " selbstbescheinigend durch - fehlt einer darin, ist Probe 30"
                        + " rot, obwohl nichts kaputt ist");
    }

    @Test
    @DisplayName("33x - die Familie der Bescheide ist vollstaendig, in beide Richtungen")
    void bescheidIstVollstaendig() {
        var ausDenBescheiden = new TreeSet<String>();
        for (Besitzbuch.Bescheid bescheid : Besitzbuch.Bescheid.values()) {
            // BEIDE Formen von amLimit: NOCH_FREI hat zwei Saetze, und nur ein Durchlauf
            // liesse genau den aus, der die Zahl des Rangs nennt.
            ausDenBescheiden.add(Sprachschluessel.bescheid(bescheid, false).pfad());
            ausDenBescheiden.add(Sprachschluessel.bescheid(bescheid, true).pfad());
        }
        var ausDerAufzaehlung = new TreeSet<String>();
        for (ParzellenSatz satz : ParzellenSatz.values()) {
            if (satz.pfad().startsWith("parzelle.bescheid.")) {
                ausDerAufzaehlung.add(satz.pfad());
            }
        }
        // In BEIDE Richtungen: Rot, wenn ein Bescheid keinen Satz hat, UND rot, wenn ein
        // Satz zu keinem Bescheid mehr gehoert - dann steht er in der Vorlage, der
        // Betreiber aendert ihn, und kein Spieler liest ihn je.
        assertEquals(ausDerAufzaehlung, ausDenBescheiden);
        // Und die GENAUE Deckungsmenge, als assertEquals auf die Vereinigung der zwei
        // Familien: Wird ein dritter Satz selbstbescheinigend, ohne dass jemand es hier
        // aufschreibt, ist diese Probe rot - und nicht erst Probe 30 mit einer Meldung,
        // die niemand mehr zuordnet.
        var alle = new TreeSet<String>(ausDenBescheiden);
        for (Buchungsgrund grund : Buchungsgrund.values()) {
            alle.add(Sprachschluessel.grund(grund).pfad());
        }
        // DRITTE Familie seit Teil E2: die Worte der Taetigkeiten. Sie werden von keinem
        // Zweig namentlich geholt, sondern ueber BerufSatz.taetigkeitswort - und gedeckt
        // sind sie nicht hier, sondern in VerdienstwacheTest: Dort geht JEDE Taetigkeit
        // durch die echte Zeile der ausgelieferten Vorlage. Hier steht sie, weil DIESE
        // Probe die Liste der Ausnahmen fuehrt; wer eine vierte Familie baut, laeuft
        // hier auf und nicht erst bei Probe 30 mit einer Meldung, die niemand zuordnet.
        for (Taetigkeit taetigkeit : Taetigkeit.values()) {
            alle.add(BerufSatz.taetigkeitswort(taetigkeit).pfad());
        }
        assertEquals(alle, new TreeSet<>(Sprachschluessel.familienDeckung()),
                "genau die Menge, die GrenzeTest als selbstbescheinigend durchlaesst");
    }

    @Test
    @DisplayName("33d - Zweig /mark: Stand und Hinweis, ganze Liste")
    void markZweig() {
        Tafel tafel = new Tafel();
        tafel.fall("stand-25000", ParzellenBefehle.markZeilen(sprache, 25_000L, null));
        tafel.fall("stand-0", ParzellenBefehle.markZeilen(sprache, 0L, null));

        assertEquals("""
                == stand-25000
                &7Du hast &625.000 Mark&7.
                &8/mark verlauf zeigt die letzten Buchungen.
                == stand-0
                &7Du hast &60 Mark&7.
                &8/mark verlauf zeigt die letzten Buchungen.
                """, tafel.ganz());
    }

    @Test
    @DisplayName("33e - Zweig /mark verlauf: leer gegen Kopf und Zeilen")
    void verlaufZweig() {
        Tafel tafel = new Tafel();
        tafel.fall("leer", ParzellenBefehle.verlaufZeilen(sprache, List.of(), BERLIN));
        tafel.fall("zwei-buchungen", ParzellenBefehle.verlaufZeilen(sprache, List.of(
                buchung(Buchungsgrund.PARZELLE_GEKAUFT, -25_000L, 25_000L),
                buchung(Buchungsgrund.EROEFFNUNG, 1_000L, 26_000L)), BERLIN));

        assertEquals("""
                == leer
                &7Noch keine Buchungen.
                == zwei-buchungen
                &6Deine letzten Buchungen
                &7  02.11. 14:37  -25.000 Mark  Parzelle genommen  (Stand 25.000 Mark)
                &7  02.11. 14:37  +1.000 Mark  Startguthaben  (Stand 26.000 Mark)
                """, tafel.ganz());
    }

    @Test
    @DisplayName("33f - Zweig info: alle acht Faelle, mit und ohne Gruppe")
    void infoZweig() {
        Tafel tafel = new Tafel();
        tafel.fall("gruppenboden", ParzellenBefehle.gruppenbodenZeilen(sprache, "Anna"));
        tafel.fall("ohne-parzelle-weg", ParzellenBefehle.ohneParzelleZeilen(sprache, true));
        tafel.fall("ohne-parzelle-mauer", ParzellenBefehle.ohneParzelleZeilen(sprache, false));
        tafel.fall("kopf", ParzellenBefehle.infoKopfZeilen(sprache, 3, -4));
        tafel.fall("ausserhalb", ParzellenBefehle.ausserhalbZeilen(sprache));
        // Der Preis bleibt am Limit stehen, die Einladung "/parzelle nehmen" nicht.
        tafel.fall("frei-gratis", ParzellenBefehle.freiZeilen(sprache, 0L, false, 4, WERBUNG));
        tafel.fall("frei-mit-preis",
                ParzellenBefehle.freiZeilen(sprache, 25_000L, false, 4, WERBUNG));
        tafel.fall("frei-am-limit-viele",
                ParzellenBefehle.freiZeilen(sprache, 25_000L, true, 4, WERBUNG));
        tafel.fall("frei-am-limit-eins",
                ParzellenBefehle.freiZeilen(sprache, 25_000L, true, 1, WERBUNG));
        tafel.fall("frei-am-limit-ohne-werbung",
                ParzellenBefehle.freiZeilen(sprache, 25_000L, true, 4, ""));
        tafel.fall("vergeben-fremd", ParzellenBefehle.vergebenZeilen(sprache, "Anna",
                25_000L, null, false, 0, false));
        tafel.fall("vergeben-gratis", ParzellenBefehle.vergebenZeilen(sprache, "Anna",
                0L, null, false, 0, false));
        tafel.fall("vergeben-mit-mitbauern", ParzellenBefehle.vergebenZeilen(sprache,
                "Anna", 25_000L, "Bert, Cara", false, 0, false));
        tafel.fall("vergeben-meine", ParzellenBefehle.vergebenZeilen(sprache, "Anna",
                25_000L, null, false, 0, true));
        tafel.fall("vergeben-gruppe-fremd", ParzellenBefehle.vergebenZeilen(sprache,
                "Anna", 25_000L, null, true, 3, false));
        tafel.fall("vergeben-gruppe-meine", ParzellenBefehle.vergebenZeilen(sprache,
                "Anna", 25_000L, null, true, 3, true));

        assertEquals("""
                == gruppenboden
                &6Gruppenboden - war einmal Weg
                &7  Eigentuemer: &fAnna
                == ohne-parzelle-weg
                &7Du stehst auf einem Weg - der gehoert allen.
                == ohne-parzelle-mauer
                &7Du stehst auf einer Mauer - die gehoert zur Strasse.
                == kopf
                &6Parzelle 3/-4
                == ausserhalb
                &7  ausserhalb der Marktstadt - wird nicht vergeben
                == frei-gratis
                &7  frei · &agratis&8 · /parzelle nehmen
                == frei-mit-preis
                &7  frei · &a25.000 Mark&8 · /parzelle nehmen
                == frei-am-limit-viele
                &7  frei · 25.000 Mark
                &c  Dein Rang erlaubt 4 Parzellen, und so viele hast du schon.
                &eMehr erlaubt ein hoeherer Rang.
                == frei-am-limit-eins
                &7  frei · 25.000 Mark
                &c  Dein Rang erlaubt 1 Parzelle, und so viele hast du schon.
                &eMehr erlaubt ein hoeherer Rang.
                == frei-am-limit-ohne-werbung
                &7  frei · 25.000 Mark
                &c  Dein Rang erlaubt 4 Parzellen, und so viele hast du schon.
                == vergeben-fremd
                &7  Eigentuemer: &fAnna
                &7  Gekauft fuer: &f25.000 Mark
                == vergeben-gratis
                &7  Eigentuemer: &fAnna
                &7  Gekauft fuer: &fgratis (die erste)
                == vergeben-mit-mitbauern
                &7  Eigentuemer: &fAnna
                &7  Gekauft fuer: &f25.000 Mark
                &7  Mitbauer: &fBert, Cara
                == vergeben-meine
                &7  Eigentuemer: &fAnna
                &7  Gekauft fuer: &f25.000 Mark
                &8  /parzelle zusammenlegen <nord|ost|sued|west>
                == vergeben-gruppe-fremd
                &7  Eigentuemer: &fAnna
                &7  Gekauft fuer: &f25.000 Mark
                &f  Teil einer Gruppe aus 3 Feldern
                == vergeben-gruppe-meine
                &7  Eigentuemer: &fAnna
                &7  Gekauft fuer: &f25.000 Mark
                &f  Teil einer Gruppe aus 3 Feldern
                &8  /parzelle auftrennen        dieses Feld herausloesen
                """, tafel.ganz());
    }

    @Test
    @DisplayName("33g - Zweig nehmen: jeder Bescheid dieses Zweiges")
    void nehmenZweig() {
        Tafel tafel = new Tafel();
        tafel.fall("erteilt-erste-gratis",
                ParzellenBefehle.nehmenErteiltZeilen(sprache, 3, -4, 0L, 0L));
        // Derselbe Gratisfall mit einem HOHEN Guthaben. Der Sendeort gibt bei kosten == 0
        // eine 0 herein, die es in der Kasse nie gab - dass sie nicht in den Satz kommt,
        // haengt allein an der Wache in der Methode. Mit 0 als Guthaben stimmte die
        // falsche Zahl zufaellig; faellt die Wache, liest der Spieler hier
        // "Bezahlt: 0 Mark - Rest: 0 Mark", obwohl er 99.000 hat.
        tafel.fall("erteilt-gratis-trotz-guthaben",
                ParzellenBefehle.nehmenErteiltZeilen(sprache, 3, -4, 0L, 99_000L));
        tafel.fall("erteilt-bezahlt",
                ParzellenBefehle.nehmenErteiltZeilen(sprache, 3, -4, 25_000L, 75_000L));
        tafel.fall("kein-bauland", ParzellenBefehle.nehmenKeinBaulandZeilen(sprache));
        tafel.fall("schon-vergeben", ParzellenBefehle.nehmenSchonVergebenZeilen(sprache, "Anna"));
        tafel.fall("grenze-viele", ParzellenBefehle.nehmenGrenzeZeilen(sprache, 4, WERBUNG));
        tafel.fall("grenze-eins", ParzellenBefehle.nehmenGrenzeZeilen(sprache, 1, WERBUNG));
        tafel.fall("grenze-ohne-werbung", ParzellenBefehle.nehmenGrenzeZeilen(sprache, 4, ""));
        tafel.fall("zu-wenig", ParzellenBefehle.nehmenZuWenigZeilen(sprache, 25_000L, 1_000L));
        tafel.fall("ausserhalb", ParzellenBefehle.nehmenAusserhalbZeilen(sprache, -8, 8));
        tafel.fall("sonst", ParzellenBefehle.nehmenSonstZeilen(sprache,
                Besitzbuch.Bescheid.SCHON_IN_GRUPPE));

        assertEquals("""
                == erteilt-erste-gratis
                &aParzelle 3/-4 gehoert jetzt dir
                &7  Die erste ist gratis.
                == erteilt-gratis-trotz-guthaben
                &aParzelle 3/-4 gehoert jetzt dir
                &7  Die erste ist gratis.
                == erteilt-bezahlt
                &aParzelle 3/-4 gehoert jetzt dir
                &7  Bezahlt: 25.000 Mark · Rest: 75.000 Mark
                == kein-bauland
                &cHier ist kein Bauland - stell dich auf eine Parzelle.
                == schon-vergeben
                &cDie gehoert schon &fAnna
                == grenze-viele
                &cDu hast schon 4 Parzellen - mehr erlaubt dein Rang nicht.
                &eMehr erlaubt ein hoeherer Rang.
                == grenze-eins
                &cDu hast schon 1 Parzelle - mehr erlaubt dein Rang nicht.
                &eMehr erlaubt ein hoeherer Rang.
                == grenze-ohne-werbung
                &cDu hast schon 4 Parzellen - mehr erlaubt dein Rang nicht.
                == zu-wenig
                &cDie kostet &625.000 Mark&c, du hast 1.000 Mark.
                == ausserhalb
                &cHier endet die Marktstadt. Parzellen gibt es von -8/-8 bis 8/8.
                == sonst
                &cDas ging nicht: SCHON_IN_GRUPPE
                """, tafel.ganz());
    }

    @Test
    @DisplayName("33h - Zweig liste: leer, unter der Grenze, am Limit")
    void listeZweig() {
        Tafel tafel = new Tafel();
        tafel.fall("leer", ParzellenBefehle.listeZeilen(sprache, List.of(), 4, 25_000L));
        tafel.fall("eine-unter-der-grenze", ParzellenBefehle.listeZeilen(sprache,
                List.of(feld(0, 0, 0L, Set.of(), false)), 4, 25_000L));
        // Drei Felder und hoechstzahl 3: Die Zeile "Die naechste kostet" faellt weg, und
        // die zwei Anhaenge (Mitbauer, Gruppe) stehen je an ihrer Zeile.
        tafel.fall("drei-am-limit", ParzellenBefehle.listeZeilen(sprache, List.of(
                feld(0, 0, 0L, Set.of(), false),
                feld(1, 0, 25_000L, Set.of(BERT), false),
                feld(2, 0, 50_000L, Set.of(), true)), 3, 100_000L));

        assertEquals("""
                == leer
                &eDu hast noch keine Parzelle. Die erste ist gratis - /parzelle nehmen
                == eine-unter-der-grenze
                &6Deine Parzellen (1 von 4)
                &7  0/0 · gratis
                &8Die naechste kostet 25.000 Mark
                == drei-am-limit
                &6Deine Parzellen (3 von 3)
                &7  0/0 · gratis
                &7  1/0 · 25.000 Mark · 1 Mitbauer
                &7  2/0 · 50.000 Mark · Gruppe
                """, tafel.ganz());
    }

    @Test
    @DisplayName("33i - Zweig mitbauer (zeigen): niemand, Namen, Hinweis")
    void mitbauerZeigenZweig() {
        Tafel tafel = new Tafel();
        tafel.fall("keine-parzelle", ParzellenBefehle.mitbauerKeineParzelleZeilen(sprache));
        tafel.fall("niemand-fremd", ParzellenBefehle.mitbauerZeilen(sprache, 3, -4, null, false));
        tafel.fall("namen-meine",
                ParzellenBefehle.mitbauerZeilen(sprache, 3, -4, "Bert, Cara", true));

        assertEquals("""
                == keine-parzelle
                &cHier ist keine vergebene Parzelle.
                == niemand-fremd
                &6Mitbauer auf 3/-4: &fniemand
                == namen-meine
                &6Mitbauer auf 3/-4: &fBert, Cara
                &8/parzelle mitbauer hinzu <name> · weg <name>
                """, tafel.ganz());
    }

    @Test
    @DisplayName("33j - Zweig mitbauer hinzu: drei Antworten")
    void mitbauerHinzuZweig() {
        Tafel tafel = new Tafel();
        tafel.fall("unbekannt", ParzellenBefehle.unbekannterSpielerZeilen(sprache));
        tafel.fall("erteilt", ParzellenBefehle.mitbauerErteiltZeilen(sprache, "Bert"));
        tafel.fall("schon-drin", ParzellenBefehle.mitbauerSchonDrinZeilen(sprache, "Bert"));

        assertEquals("""
                == unbekannt
                &cDen kenne ich nicht. Er muss einmal auf dem Server gewesen sein.
                == erteilt
                &aBert darf hier jetzt bauen.
                == schon-drin
                &eBert darf hier ohnehin bauen.
                """, tafel.ganz());
    }

    @Test
    @DisplayName("33k - Zweig mitbauer weg: drei Antworten")
    void mitbauerWegZweig() {
        Tafel tafel = new Tafel();
        tafel.fall("niemand", ParzellenBefehle.niemandMitDemNamenZeilen(sprache));
        tafel.fall("erteilt", ParzellenBefehle.mitbauerWegErteiltZeilen(sprache, "Bert"));
        tafel.fall("nicht-drin", ParzellenBefehle.mitbauerNichtDrinZeilen(sprache, "Bert"));

        assertEquals("""
                == niemand
                &cSteht hier niemand mit dem Namen - /parzelle mitbauer zeigt die Liste.
                == erteilt
                &aBert darf hier nicht mehr bauen.
                == nicht-drin
                &eBert stand gar nicht auf der Liste.
                """, tafel.ganz());
    }

    @Test
    @DisplayName("33l - Zweig aufgeben: Warnung in drei Zeilen und die Tat")
    void aufgebenZweig() {
        Tafel tafel = new Tafel();
        tafel.fall("nicht-deine", ParzellenBefehle.nichtDeineParzelleZeilen(sprache));
        tafel.fall("gruppe", ParzellenBefehle.aufgebenGruppeZeilen(sprache));
        tafel.fall("warnung", ParzellenBefehle.aufgebenWarnungZeilen(sprache, 3, -4));
        tafel.fall("erteilt", ParzellenBefehle.aufgegebenZeilen(sprache, 3, -4));

        assertEquals("""
                == nicht-deine
                &cDas ist nicht deine Parzelle.
                == gruppe
                &cDie Parzelle gehoert zu einer Gruppe - erst /parzelle auftrennen.
                == warnung
                &eParzelle 3/-4 aufgeben?
                &7  Es gibt kein Geld zurueck, und gebaut bleibt stehen.
                &c  /parzelle aufgeben bestaetigen 3 -4
                == erteilt
                &aParzelle 3/-4 ist wieder frei.
                """, tafel.ganz());
    }

    @Test
    @DisplayName("33m - Zweig zusammenlegen: vier Richtungen, Geld genug und nicht")
    void zusammenlegenZweig() {
        Tafel tafel = new Tafel();
        tafel.fall("gruppe-zu-gross-viele",
                ParzellenBefehle.gruppeZuGrossZeilen(sprache, 4, WERBUNG));
        tafel.fall("gruppe-zu-gross-eins",
                ParzellenBefehle.gruppeZuGrossZeilen(sprache, 1, WERBUNG));
        tafel.fall("gruppe-zu-gross-ohne-werbung",
                ParzellenBefehle.gruppeZuGrossZeilen(sprache, 4, ""));
        // Je Richtung ein Fall: Der Nachbar steht im Satz, und dx/dz sind das Einzige,
        // was ihn bestimmt. Ein vertauschtes Vorzeichen ist hier rot und sonst nirgends.
        for (Richtung richtung : Richtung.values()) {
            tafel.fall("warnung-" + richtung.wort(), ParzellenBefehle.zusammenlegenWarnungZeilen(
                    sprache, 3, -4, richtung, 10_000L, 25_000L));
        }
        tafel.fall("warnung-zu-wenig", ParzellenBefehle.zusammenlegenWarnungZeilen(
                sprache, 3, -4, Richtung.NORD, 10_000L, 1_000L));
        tafel.fall("erteilt", ParzellenBefehle.zusammengelegtZeilen(sprache, 2, 10_000L, 15_000L));

        assertEquals("""
                == gruppe-zu-gross-viele
                &cDein Rang erlaubt hoechstens 4 Felder je Gruppe.
                &eMehr erlaubt ein hoeherer Rang.
                == gruppe-zu-gross-eins
                &cDein Rang erlaubt hoechstens 1 Feld je Gruppe.
                &eMehr erlaubt ein hoeherer Rang.
                == gruppe-zu-gross-ohne-werbung
                &cDein Rang erlaubt hoechstens 4 Felder je Gruppe.
                == warnung-nord
                &eParzelle 3/-4 mit 3/-5 zusammenlegen?
                &7  Gebuehr: &610.000 Mark&7 · du hast 25.000 Mark
                &7  Der Weg dazwischen wird dein Bauland.
                &7  Beim Auftrennen kommt er zurueck - und raeumt alles weg, was dann
                &7  darauf steht.
                &e  /parzelle zusammenlegen nord bestaetigen 3 -4
                == warnung-ost
                &eParzelle 3/-4 mit 4/-4 zusammenlegen?
                &7  Gebuehr: &610.000 Mark&7 · du hast 25.000 Mark
                &7  Der Weg dazwischen wird dein Bauland.
                &7  Beim Auftrennen kommt er zurueck - und raeumt alles weg, was dann
                &7  darauf steht.
                &e  /parzelle zusammenlegen ost bestaetigen 3 -4
                == warnung-sued
                &eParzelle 3/-4 mit 3/-3 zusammenlegen?
                &7  Gebuehr: &610.000 Mark&7 · du hast 25.000 Mark
                &7  Der Weg dazwischen wird dein Bauland.
                &7  Beim Auftrennen kommt er zurueck - und raeumt alles weg, was dann
                &7  darauf steht.
                &e  /parzelle zusammenlegen sued bestaetigen 3 -4
                == warnung-west
                &eParzelle 3/-4 mit 2/-4 zusammenlegen?
                &7  Gebuehr: &610.000 Mark&7 · du hast 25.000 Mark
                &7  Der Weg dazwischen wird dein Bauland.
                &7  Beim Auftrennen kommt er zurueck - und raeumt alles weg, was dann
                &7  darauf steht.
                &e  /parzelle zusammenlegen west bestaetigen 3 -4
                == warnung-zu-wenig
                &eParzelle 3/-4 mit 3/-5 zusammenlegen?
                &7  Gebuehr: &610.000 Mark&7 · du hast 1.000 Mark
                &7  Der Weg dazwischen wird dein Bauland.
                &7  Beim Auftrennen kommt er zurueck - und raeumt alles weg, was dann
                &7  darauf steht.
                &c  Dein Guthaben reicht nicht.
                == erteilt
                &aEin Grundstueck aus 2 Feldern.
                &7  Bezahlt: 10.000 Mark · Rest: 15.000 Mark
                """, tafel.ganz());
    }

    @Test
    @DisplayName("33n - Zweig auftrennen alles: Warnung, Bestaetigung, Tat")
    void auftrennenAllesZweig() {
        Tafel tafel = new Tafel();
        tafel.fall("warnung", ParzellenBefehle.auftrennenWarnungZeilen(sprache, 3));
        tafel.fall("bestaetigen", ParzellenBefehle.auftrennenBestaetigenZeilen(sprache, 3, -4));
        tafel.fall("erteilt", ParzellenBefehle.aufgetrenntZeilen(sprache, 3));

        assertEquals("""
                == warnung
                &eGrundstueck aus 3 Feldern ganz auftrennen?
                &7  ALLE Wege zwischen den Feldern kommen zurueck. Geld gibt es keines zurueck.
                &7  Fuer ein einzelnes Feld: stell dich darauf und nimm /parzelle auftrennen ohne "alles".
                == bestaetigen
                &c  /parzelle auftrennen alles bestaetigen 3 -4
                == erteilt
                &aAufgetrennt - 3 eigenstaendige Parzellen.
                """, tafel.ganz());
    }

    @Test
    @DisplayName("33o - Zweig auftrennen/herausloesen: zwei Felder gegen mehr")
    void herausloesenZweig() {
        Tafel tafel = new Tafel();
        // Unter zwei bleibenden Feldern wird ganz aufgetrennt - in der Warnung UND in der
        // Tat. Dass der zweite Fall woertlich der des ganzen Auftrennens ist, haelt diese
        // Probe fest: erteilt-ganz muss dasselbe Bild tragen wie auftrennen alles.
        tafel.fall("warnung-zwei-felder",
                ParzellenBefehle.herausloesenWarnungZeilen(sprache, 2, 3, -4, 1));
        tafel.fall("warnung-feld",
                ParzellenBefehle.herausloesenWarnungZeilen(sprache, 4, 3, -4, 3));
        tafel.fall("bestaetigen",
                ParzellenBefehle.herausloesenBestaetigenZeilen(sprache, 3, -4));
        tafel.fall("erteilt-feld", ParzellenBefehle.herausgeloestZeilen(sprache, 4, 3, -4, 3));
        tafel.fall("erteilt-ganz", ParzellenBefehle.herausgeloestZeilen(sprache, 2, 3, -4, 1));

        assertEquals("""
                == warnung-zwei-felder
                &eGrundstueck aus 2 Feldern auftrennen?
                &7  Es hat nur zwei Felder - eines allein geht nicht heraus, also wird
                &7  es ganz aufgetrennt.
                &7  Der Weg dorthin kommt zurueck. Geld gibt es keines zurueck.
                == warnung-feld
                &eDas Feld 3/-4 aus dem Grundstueck herausloesen?
                &7  Die anderen 3 Felder bleiben ein Grundstueck.
                &7  Der Weg dorthin kommt zurueck. Geld gibt es keines zurueck.
                == bestaetigen
                &c  /parzelle auftrennen bestaetigen 3 -4
                == erteilt-feld
                &aFeld 3/-4 herausgeloest - das Grundstueck hat noch 3 Felder.
                == erteilt-ganz
                &aAufgetrennt - 2 eigenstaendige Parzellen.
                """, tafel.ganz());
    }

    @Test
    @DisplayName("33p - Zweig aufwand: anzahl 0 sendet GAR NICHTS, 1 gegen viele")
    void aufwandZweig() {
        Tafel tafel = new Tafel();
        // Der ganze Aufwand kommt herein und nicht drei Werte: {@code istNichts} stammt
        // seit dem 04.11.2026 aus demselben Satz wie die Zahlen. Damit ist "0 und 0, aber
        // istNichts false" kein Fall mehr, den eine Aufrufstelle bauen koennte - und der
        // alte Fall null-sendet-nichts ist derselbe wie nur-nichts geworden. Dass bei
        // einer einzelnen Null die betreffende Zeile WEGFAELLT und nicht "0 Bloecke"
        // sagt, halten die zwei letzten Faelle fest.
        tafel.fall("null-und-null", ParzellenBefehle.aufwandZeilen(sprache,
                new Wegbau.Aufwand(0, 0)));
        tafel.fall("eins-und-eins", ParzellenBefehle.aufwandZeilen(sprache,
                new Wegbau.Aufwand(1, 1)));
        tafel.fall("viele-und-viele", ParzellenBefehle.aufwandZeilen(sprache,
                new Wegbau.Aufwand(4_928, 12)));
        tafel.fall("nur-verschwindet", ParzellenBefehle.aufwandZeilen(sprache,
                new Wegbau.Aufwand(3, 0)));
        tafel.fall("nur-verfuellt", ParzellenBefehle.aufwandZeilen(sprache,
                new Wegbau.Aufwand(0, 3)));

        assertEquals("""
                == null-und-null
                &7  Auf der Wegflaeche steht nichts - es geht nichts verloren.
                == eins-und-eins
                &c  Dabei verschwindet 1 Block von der Flaeche.
                &7  1 Fehlstelle wird wieder gefuellt.
                == viele-und-viele
                &c  Dabei verschwinden 4928 Bloecke von der Flaeche.
                &7  12 Fehlstellen werden wieder gefuellt.
                == nur-verschwindet
                &c  Dabei verschwinden 3 Bloecke von der Flaeche.
                == nur-verfuellt
                &7  3 Fehlstellen werden wieder gefuellt.
                """, tafel.ganz());
    }

    @Test
    @DisplayName("33q - geteilt: Bodenarbeit, beide Wortstuecke der Zweige")
    void bodenZweig() {
        Tafel tafel = new Tafel();
        // Die zwei Wortstuecke stehen an den Aufrufstellen und kommen hier als Wert
        // herein - genau so, wie der Zweig sie mitgibt. Aber ueber ihren SCHLUESSEL und
        // nicht als Zeichenkette: Vorher gab diese Tafel das Wort selbst herein, dessen
        // Einsetzen sie prueft, und ihre Erwartung stammte an dieser Stelle aus derselben
        // Hand wie der Satz. Jetzt haengt das Wort an der Datei und nur die Zuordnung an
        // der Probe. Welcher Zweig welches Wort mitgibt, deckt AufrufstelleTest 34i/34j.
        tafel.fall("weg-ist-bauland", ParzellenBefehle.bodenZeilen(sprache,
                sprache.wort(ParzellenSatz.BODEN_WEG_IST_BAULAND, Einsatz.keine()), 623));
        tafel.fall("weg-ist-zurueck", ParzellenBefehle.bodenZeilen(sprache,
                sprache.wort(ParzellenSatz.BODEN_WEG_IST_ZURUECK, Einsatz.keine()), 448));
        tafel.fall("fehler", ParzellenBefehle.bodenFehlerZeilen(sprache));

        assertEquals("""
                == weg-ist-bauland
                &7  Der Weg dazwischen ist Bauland (623 Bloecke geaendert).
                == weg-ist-zurueck
                &7  Der Weg ist zurueck (448 Bloecke geaendert).
                == fehler
                &c  Der Boden liess sich nicht aendern. Die Parzellen stimmen - sag einem Operator Bescheid.
                """, tafel.ganz());
    }

    @Test
    @DisplayName("33r - geteilt: Abklingzeit, eine Sekunde gegen viele")
    void abklingzeitZweig() {
        Tafel tafel = new Tafel();
        // Null Sekunden sendet GAR NICHTS - dieselbe Art Wache wie bei aufwandZeilen, und
        // sie steht aus demselben Grund in der reinen Methode: Am Sendeort allein war sie
        // unbewacht. Aus "rest <= 0" ein "rest < 0" zu machen hielt jede Probe gruen, und
        // der Spieler las "Noch 0 Sekunden" und wartete auf etwas, das nicht kommt.
        tafel.fall("null-sekunden", ParzellenBefehle.zuSchnellZeilen(sprache, 0L));
        tafel.fall("eine-sekunde", ParzellenBefehle.zuSchnellZeilen(sprache, 1L));
        tafel.fall("drei-sekunden", ParzellenBefehle.zuSchnellZeilen(sprache, 3L));

        assertEquals("""
                == null-sekunden
                == eine-sekunde
                &7Einen Augenblick - die Abschaetzung liest jeden Block ueber dem Weg. Noch 1 Sekunde.
                == drei-sekunden
                &7Einen Augenblick - die Abschaetzung liest jeden Block ueber dem Weg. Noch 3 Sekunden.
                """, tafel.ganz());
    }

    @Test
    @DisplayName("33s - geteilt: falscher Stand, alle vier Wortstuecke")
    void stehtNichtAufZweig() {
        Tafel tafel = new Tafel();
        // Vier Zweige geben vier verschiedene Wortstuecke mit. Ein vertauschtes Paar
        // liest sich dann als die falsche Tat, die nicht geschah. Die Woerter kommen
        // ueber ihren SCHLUESSEL aus der Datei und nicht als Zeichenkette aus dieser
        // Probe - sonst zeigte die Tafel nur, dass ein uebergebener Wert eingesetzt wird.
        // Dass JEDER Zweig sein eigenes mitgibt, deckt AufrufstelleTest 34h.
        tafel.fall("nichts-aufgegeben", ParzellenBefehle.stehtNichtAufZeilen(sprache, 3, -4,
                sprache.wort(ParzellenSatz.TAT_NICHTS_AUFGEGEBEN, Einsatz.keine())));
        tafel.fall("nichts-zusammengelegt", ParzellenBefehle.stehtNichtAufZeilen(sprache, 3, -4,
                sprache.wort(ParzellenSatz.TAT_NICHTS_ZUSAMMENGELEGT, Einsatz.keine())));
        tafel.fall("nichts-herausgeloest", ParzellenBefehle.stehtNichtAufZeilen(sprache, 3, -4,
                sprache.wort(ParzellenSatz.TAT_NICHTS_HERAUSGELOEST, Einsatz.keine())));
        tafel.fall("nichts-aufgetrennt", ParzellenBefehle.stehtNichtAufZeilen(sprache, 3, -4,
                sprache.wort(ParzellenSatz.TAT_NICHTS_AUFGETRENNT, Einsatz.keine())));

        assertEquals("""
                == nichts-aufgegeben
                &cDu stehst nicht auf Parzelle 3/-4. Nichts aufgegeben.
                == nichts-zusammengelegt
                &cDu stehst nicht auf Parzelle 3/-4. Nichts zusammengelegt.
                == nichts-herausgeloest
                &cDu stehst nicht auf Parzelle 3/-4. Nichts herausgeloest.
                == nichts-aufgetrennt
                &cDu stehst nicht auf Parzelle 3/-4. Nichts aufgetrennt.
                """, tafel.ganz());
    }

    @Test
    @DisplayName("33t - Zweig absage: JEDER Bescheid, nicht am Limit")
    void absageZweigNichtAmLimit() {
        Tafel tafel = new Tafel();
        // Die ganze Aufzaehlung und nicht eine Auswahl: Faellt ein Zweig des switch weg
        // oder tauschen zwei ihre Saetze, ist genau diese Tafel rot.
        //
        // hoechstzahl 7 und gruppenfelder 3 sind ABSICHTLICH verschieden. Drei Bescheide
        // setzen eine Zahl in den Satz, und zwar aus zwei verschiedenen Quellen (Bauplan
        // 2.3): NOCH_FREI am Limit und GRENZE_ERREICHT nennen die Hoechstzahl,
        // GRUPPE_ZU_GROSS die Felderzahl. Mit zwei gleichen Zahlen waere ein Vertauschen
        // der Quellen gruen.
        for (Besitzbuch.Bescheid bescheid : Besitzbuch.Bescheid.values()) {
            tafel.fall(bescheid.name(), ParzellenBefehle.absageZeilen(sprache, bescheid,
                    false, 7, 3, WERBUNG));
        }

        assertEquals("""
                == ERTEILT
                &cErledigt.
                == KEINE_PARZELLE
                &cHier ist keine Parzelle.
                == NOCH_FREI
                &cDiese Parzelle gehoert noch niemandem - nimm sie mit /parzelle nehmen.
                == SCHON_VERGEBEN
                &cDie gehoert schon jemandem.
                == AUSSERHALB
                &cDas liegt ausserhalb der Marktstadt.
                == GRENZE_ERREICHT
                &cDein Rang erlaubt 7 Parzellen, und so viele hast du schon.
                &eMehr erlaubt ein hoeherer Rang.
                == ZU_WENIG_MARK
                &cDein Guthaben reicht nicht.
                == NICHT_EIGENTUEMER
                &cDas darf nur der Eigentuemer.
                == SCHON_EINGETRAGEN
                &cSteht schon auf der Liste.
                == NICHT_EINGETRAGEN
                &cStand gar nicht auf der Liste.
                == TEIL_EINER_GRUPPE
                &cDie Parzelle gehoert zu einer Gruppe - das geht nur ueber die ganze Gruppe.
                == NACHBAR_FREI
                &cDort drueben ist keine Parzelle vergeben.
                == NACHBAR_FREMD
                &cDie Nachbarparzelle gehoert jemand anderem.
                == SCHON_IN_GRUPPE
                &cDie beiden gehoeren zu zwei verschiedenen Grundstuecken. Trenne eines auf und haenge die Felder einzeln an.
                == SCHON_ZUSAMMEN
                &cDie beiden sind schon dasselbe Grundstueck.
                == GRUPPE_ZU_GROSS
                &cDein Rang erlaubt hoechstens 3 Felder je Gruppe.
                &eMehr erlaubt ein hoeherer Rang.
                == KEINE_GRUPPE
                &cHier ist keine Gruppe.
                == AUF_DEM_WEG
                &cStell dich auf das Feld, das du meinst - nicht auf den Weg.
                == HAELT_ZUSAMMEN
                &cDieses Feld haelt dein Grundstueck zusammen. Loese erst eines am Rand heraus.
                """, tafel.ganz());
    }

    @Test
    @DisplayName("33u - Zweig absage: JEDER Bescheid, am Limit")
    void absageZweigAmLimit() {
        Tafel tafel = new Tafel();
        // Dieselbe Tafel mit amLimit true. Genau EIN Bescheid darf sich unterscheiden -
        // NOCH_FREI. Wer die Wache weiter fasst, macht hier mehrere Zeilen rot.
        for (Besitzbuch.Bescheid bescheid : Besitzbuch.Bescheid.values()) {
            tafel.fall(bescheid.name(), ParzellenBefehle.absageZeilen(sprache, bescheid,
                    true, 7, 3, WERBUNG));
        }

        assertEquals("""
                == ERTEILT
                &cErledigt.
                == KEINE_PARZELLE
                &cHier ist keine Parzelle.
                == NOCH_FREI
                &cDiese Parzelle gehoert noch niemandem - aber dein Rang erlaubt 7 Parzellen, und so viele hast du schon.
                &eMehr erlaubt ein hoeherer Rang.
                == SCHON_VERGEBEN
                &cDie gehoert schon jemandem.
                == AUSSERHALB
                &cDas liegt ausserhalb der Marktstadt.
                == GRENZE_ERREICHT
                &cDein Rang erlaubt 7 Parzellen, und so viele hast du schon.
                &eMehr erlaubt ein hoeherer Rang.
                == ZU_WENIG_MARK
                &cDein Guthaben reicht nicht.
                == NICHT_EIGENTUEMER
                &cDas darf nur der Eigentuemer.
                == SCHON_EINGETRAGEN
                &cSteht schon auf der Liste.
                == NICHT_EINGETRAGEN
                &cStand gar nicht auf der Liste.
                == TEIL_EINER_GRUPPE
                &cDie Parzelle gehoert zu einer Gruppe - das geht nur ueber die ganze Gruppe.
                == NACHBAR_FREI
                &cDort drueben ist keine Parzelle vergeben.
                == NACHBAR_FREMD
                &cDie Nachbarparzelle gehoert jemand anderem.
                == SCHON_IN_GRUPPE
                &cDie beiden gehoeren zu zwei verschiedenen Grundstuecken. Trenne eines auf und haenge die Felder einzeln an.
                == SCHON_ZUSAMMEN
                &cDie beiden sind schon dasselbe Grundstueck.
                == GRUPPE_ZU_GROSS
                &cDein Rang erlaubt hoechstens 3 Felder je Gruppe.
                &eMehr erlaubt ein hoeherer Rang.
                == KEINE_GRUPPE
                &cHier ist keine Gruppe.
                == AUF_DEM_WEG
                &cStell dich auf das Feld, das du meinst - nicht auf den Weg.
                == HAELT_ZUSAMMEN
                &cDieses Feld haelt dein Grundstueck zusammen. Loese erst eines am Rand heraus.
                """, tafel.ganz());
    }

    @Test
    @DisplayName("33v - Zweig absage: Einzahl, und die Werbung nur an drei Stellen")
    void absageEinzahlUndOhneWerbung() {
        Tafel tafel = new Tafel();
        // Die drei Bescheide mit einer Zahl, einmal in der Einzahl.
        tafel.fall("noch-frei-am-limit-eins", ParzellenBefehle.absageZeilen(sprache,
                Besitzbuch.Bescheid.NOCH_FREI, true, 1, 1, WERBUNG));
        tafel.fall("grenze-erreicht-eins", ParzellenBefehle.absageZeilen(sprache,
                Besitzbuch.Bescheid.GRENZE_ERREICHT, true, 1, 1, WERBUNG));
        tafel.fall("gruppe-zu-gross-eins", ParzellenBefehle.absageZeilen(sprache,
                Besitzbuch.Bescheid.GRUPPE_ZU_GROSS, true, 1, 1, WERBUNG));
        // Ohne Zeile in der config.yml bleibt die Werbung stumm - dieselben drei Faelle
        // haben dann nur noch eine Zeile.
        tafel.fall("noch-frei-am-limit-stumm", ParzellenBefehle.absageZeilen(sprache,
                Besitzbuch.Bescheid.NOCH_FREI, true, 7, 3, ""));
        tafel.fall("grenze-erreicht-stumm", ParzellenBefehle.absageZeilen(sprache,
                Besitzbuch.Bescheid.GRENZE_ERREICHT, true, 7, 3, ""));
        tafel.fall("gruppe-zu-gross-stumm", ParzellenBefehle.absageZeilen(sprache,
                Besitzbuch.Bescheid.GRUPPE_ZU_GROSS, true, 7, 3, ""));

        assertEquals("""
                == noch-frei-am-limit-eins
                &cDiese Parzelle gehoert noch niemandem - aber dein Rang erlaubt 1 Parzelle, und so viele hast du schon.
                &eMehr erlaubt ein hoeherer Rang.
                == grenze-erreicht-eins
                &cDein Rang erlaubt 1 Parzelle, und so viele hast du schon.
                &eMehr erlaubt ein hoeherer Rang.
                == gruppe-zu-gross-eins
                &cDein Rang erlaubt hoechstens 1 Feld je Gruppe.
                &eMehr erlaubt ein hoeherer Rang.
                == noch-frei-am-limit-stumm
                &cDiese Parzelle gehoert noch niemandem - aber dein Rang erlaubt 7 Parzellen, und so viele hast du schon.
                == grenze-erreicht-stumm
                &cDein Rang erlaubt 7 Parzellen, und so viele hast du schon.
                == gruppe-zu-gross-stumm
                &cDein Rang erlaubt hoechstens 3 Felder je Gruppe.
                """, tafel.ganz());
    }

    @Test
    @DisplayName("33w - geteilt: Werbung und die eine Weltpruefung")
    void werbungUndMarktstadtZweig() {
        Tafel tafel = new Tafel();
        tafel.fall("werbung-gesetzt", ParzellenBefehle.werbungZeilen(sprache, WERBUNG));
        tafel.fall("werbung-leer", ParzellenBefehle.werbungZeilen(sprache, ""));
        tafel.fall("nicht-in-der-marktstadt", ParzellenBefehle.nichtInDerMarktstadtZeilen(sprache));

        assertEquals("""
                == werbung-gesetzt
                &eMehr erlaubt ein hoeherer Rang.
                == werbung-leer
                == nicht-in-der-marktstadt
                &cParzellen gibt es nur in der Marktstadt.
                """, tafel.ganz());
    }

    // --- Bauschritt 3: /tpa, /tpahere, /tpaccept, /tpdeny ----------------------
    //
    // ANNA fragt, BERT wird gefragt - zwei verschiedene Namen, und das ist hier keine
    // Zierde: Jeder Satz dieses Zweiges traegt GENAU EINEN Namen, und zwar den des
    // ANDEREN. Mit einem Namen fuer beide waere ein vertauschter Empfaenger gruen.
    //
    // Je Empfaenger ein eigener Fall und nicht die Vereinigung: "an-frager" und
    // "an-angefragten" stehen getrennt in der Tafel, weil beide Zeilen in einer
    // gemeinsamen Liste auch dann vollstaendig waeren, wenn sie an die falschen Spieler
    // gehen. WELCHE Methode an WEN geht, nagelt GrenzeTest 31e fest; dass jede von ihnen
    // das Richtige SAGT, nagelt diese Tafel fest.

    /** Wer fragt. */
    private static final String FRAGER = "Anna";

    /** Wer gefragt wird und entscheidet. */
    private static final String ANGEFRAGTER = "Bert";

    @Test
    @DisplayName("33y - Zweig stellen: je Empfaenger eine eigene Liste, beide Richtungen")
    void stellenZweig() {
        Tafel tafel = new Tafel();
        tafel.fall("nicht-da", AnfragenBefehle.nichtDaZeilen(sprache, ANGEFRAGTER));
        tafel.fall("bei-dir", AnfragenBefehle.beiDirZeilen(sprache));
        tafel.fall("steht-noch", AnfragenBefehle.stehtNochZeilen(sprache, ANGEFRAGTER));
        // Der Fragende liest den Namen des Angefragten, der Angefragte den des
        // Fragenden - gegenlaeufig, und genau darum stehen hier zwei Namen.
        tafel.fall("zu-dir/an-frager",
                AnfragenBefehle.stellenFragerZeilen(sprache, ANGEFRAGTER));
        tafel.fall("zu-dir/an-angefragten", AnfragenBefehle.stellenAngefragtZeilen(
                sprache, FRAGER, Anfragen.Richtung.ZU_DIR));
        tafel.fall("hierher/an-frager",
                AnfragenBefehle.stellenFragerZeilen(sprache, ANGEFRAGTER));
        tafel.fall("hierher/an-angefragten", AnfragenBefehle.stellenAngefragtZeilen(
                sprache, FRAGER, Anfragen.Richtung.HIERHER));

        assertEquals("""
                == nicht-da
                &cBert ist nicht da.
                == bei-dir
                &7Du bist schon bei dir.
                == steht-noch
                &7Diese Anfrage an Bert steht noch.
                == zu-dir/an-frager
                &7Angefragt bei Bert - er oder sie muss zustimmen.
                == zu-dir/an-angefragten
                &6Anna moechte zu dir kommen.
                &7/tpaccept zum Annehmen, /tpdeny zum Ablehnen. Die Anfrage laeuft nach einer Minute ab.
                == hierher/an-frager
                &7Angefragt bei Bert - er oder sie muss zustimmen.
                == hierher/an-angefragten
                &6Anna laedt dich ein zu kommen.
                &7/tpaccept zum Annehmen, /tpdeny zum Ablehnen. Die Anfrage laeuft nach einer Minute ab.
                """, tafel.ganz());
    }

    @Test
    @DisplayName("33z - Zweig nimmAn: die zwei Paare, und was danach noch wartet")
    void nimmAnZweig() {
        Tafel tafel = new Tafel();
        tafel.fall("niemand", AnfragenBefehle.niemandFragtZeilen(sprache));
        tafel.fall("fragender-weg", AnfragenBefehle.fragenderWegZeilen(sprache));
        // Das PAAR von /tpa: Der Fragende hatte gefragt, also wandert er - der Annehmer
        // bleibt stehen. annehmerWandert ist hier false.
        tafel.fall("zu-dir/an-annehmer",
                AnfragenBefehle.nimmAnAnnehmerZeilen(sprache, FRAGER, false));
        tafel.fall("zu-dir/an-frager",
                AnfragenBefehle.nimmAnFragerZeilen(sprache, ANGEFRAGTER, false));
        // Das PAAR von /tpahere: Der Eingeladene nimmt an und wandert selbst.
        tafel.fall("hierher/an-annehmer",
                AnfragenBefehle.nimmAnAnnehmerZeilen(sprache, FRAGER, true));
        tafel.fall("hierher/an-frager",
                AnfragenBefehle.nimmAnFragerZeilen(sprache, ANGEFRAGTER, true));
        tafel.fall("weitere-eins", AnfragenBefehle.weitereZeilen(sprache, 1L));
        tafel.fall("weitere-drei", AnfragenBefehle.weitereZeilen(sprache, 3L));
        // Eine NACKTE Zahl ohne Tausenderpunkt - so ist es heute im Spiel, und nur
        // Geldbetraege tragen ihn. Ohne diesen Fall waere der Weg ueber Einsatz.mit(long)
        // gruen, und der Spieler lese "Es warten noch 1.000 Anfragen."
        tafel.fall("weitere-tausend", AnfragenBefehle.weitereZeilen(sprache, 1_000L));

        assertEquals("""
                == niemand
                &7Es fragt niemand.
                == fragender-weg
                &cWer gefragt hatte, ist nicht mehr da.
                == zu-dir/an-annehmer
                &aAnna kommt zu dir.
                == zu-dir/an-frager
                &aBert hat angenommen - du kommst.
                == hierher/an-annehmer
                &aDu kommst zu Anna.
                == hierher/an-frager
                &aBert hat angenommen und kommt zu dir.
                == weitere-eins
                &7Es warten noch 1 Anfrage.
                == weitere-drei
                &7Es warten noch 3 Anfragen.
                == weitere-tausend
                &7Es warten noch 1000 Anfragen.
                """, tafel.ganz());
    }

    @Test
    @DisplayName("33aa - Zweig lehneAb: nichts da, eins, viele - und die nackte Zahl")
    void lehneAbZweig() {
        Tafel tafel = new Tafel();
        // Null ist KEINE Zaehlform, sondern derselbe Satz wie bei /tpaccept ohne
        // Anfrage: Art.GEZAEHLT kennt nur eins und viele.
        tafel.fall("nichts-da", AnfragenBefehle.lehneAbZeilen(sprache, 0L));
        tafel.fall("eins", AnfragenBefehle.lehneAbZeilen(sprache, 1L));
        tafel.fall("vier", AnfragenBefehle.lehneAbZeilen(sprache, 4L));
        tafel.fall("tausend", AnfragenBefehle.lehneAbZeilen(sprache, 1_000L));

        assertEquals("""
                == nichts-da
                &7Es fragt niemand.
                == eins
                &71 Anfrage abgelehnt.
                == vier
                &74 Anfragen abgelehnt.
                == tausend
                &71000 Anfragen abgelehnt.
                """, tafel.ganz());
    }

    @Test
    @DisplayName("33ab - die zwei Paare sind wirklich vier verschiedene Saetze")
    void dieZweiPaareSindVierSaetze() {
        // Gemessen traegt die Zeile an den Annehmer in BEIDEN Richtungen genau 20
        // Zeichen - Zufall, und kein Halt. Darum vergleicht diese Probe die Bilder und
        // nicht ihre Laenge: Wer die Richtung dreht, bekommt dasselbe Mass und einen
        // anderen Satz.
        //
        // Und darum steht hier KEINE Zusicherung ueber die Laenge. Dass keine andere
        // Probe die zwei Formen an der Laenge auseinanderhaelt, ist eine Aussage ueber
        // die ANDEREN Proben und gehoert in diesen Kommentar - geprueft am 05.11.2026:
        // keine Probe im Baum vergleicht diese zwei Bilder ueber ihre Laenge. Stuende
        // die Gleichheit als assertEquals da, machte schon eine erlaubte
        // Betreiberaenderung - du-kommst von "Du kommst zu {name}." auf "Du reist zu
        // {name}." - diese Probe rot, mit einer Meldung ueber die Probe statt ueber den
        // Fehler, und der Betreiber, der die Datei aendern DARF, schloesse daraus, er
        // muesse sein Wort zuruecknehmen. Gemessen: Genau diese Aenderung macht 33z,
        // BildGleichTest 11 und AufrufstelleTest 34n rot - die drei Proben, die wirklich
        // ueber das Bild sprechen. Sie geht also nicht unbemerkt durch.
        var zuDirAnnehmer = AnfragenBefehle.nimmAnAnnehmerZeilen(sprache, FRAGER, false);
        var hierherAnnehmer = AnfragenBefehle.nimmAnAnnehmerZeilen(sprache, FRAGER, true);
        var zuDirFrager = AnfragenBefehle.nimmAnFragerZeilen(sprache, ANGEFRAGTER, false);
        var hierherFrager = AnfragenBefehle.nimmAnFragerZeilen(sprache, ANGEFRAGTER, true);

        Set<String> bilder = new TreeSet<>(List.of(Sprachprobe.bild(zuDirAnnehmer),
                Sprachprobe.bild(hierherAnnehmer), Sprachprobe.bild(zuDirFrager),
                Sprachprobe.bild(hierherFrager)));
        assertEquals(4, bilder.size(), "vier Faelle, vier Saetze - zwei gleiche hiessen,"
                + " dass eine Richtung oder ein Empfaenger keinen eigenen Satz hat: "
                + bilder);
    }

    // --- Bauschritt 5, erster Teil: Reise und Aufwaermzeit ---------------------
    //
    // Die Lage dieser zwei Dateien ist das GEGENTEIL von /tpa. Dort lesen zwei Spieler je
    // eine Zeile; hier liest GENAU EINER - der Reisende -, aber der Name im Satz kann
    // einem ANDEREN gehoeren, der selbst nichts liest. Bei /tpaccept reist Anna und liest
    // Berts Namen. Dass nur einer liest, nagelt AufrufstelleTest 34p/34q mit zwei
    // getrennten Mitschriften fest; dass jeder Satz das Richtige SAGT, diese drei Tafeln.
    //
    // DIE ZWEI ZIELWERTE SIND ABSICHTLICH VERSCHIEDEN GEARTET. Dieselbe Stelle bekommt
    // heute fuenferlei: einen kleingeschriebenen Weltnamen (/warp, Tor), einen
    // Spielernamen (/tpaccept), die Wendung "deinen letzten Ort" (/zurueck) und die
    // Aufschrift "Parzelle 3/-4" (/freimarken parzelle). Mit der Wendung liest ein
    // Spieler heute "geht es nach deinen letzten Ort" - grammatisch schief, aber das ist
    // der AUSGELIEFERTE Wortlaut. Der Massstab ist zeichengleich, also steht die
    // Schiefheit hier als Zusicherung: Wer sie beim Umzug stillschweigend begradigt,
    // aendert das Spielbild, und ohne diese Zeile haette er dafuer keinen Zeugen.

    /** Ein Weltname, wie {@code /warp} und das Tor ihn durchgeben - aus der config.yml. */
    private static final String ZIEL_WELT = "kornmark";

    /** Das Satzstueck, das {@code /zurueck} durchgibt. Heute schief, und so bleibt es. */
    private static final String ZIEL_WENDUNG = "deinen letzten Ort";

    @Test
    @DisplayName("33ac - Zweig Reise: JEDES Hindernis, nicht eine Auswahl")
    void hindernisZweig() {
        Tafel tafel = new Tafel();
        // Die ganze Aufzaehlung und nicht eine Auswahl - dasselbe Muster wie 33t: Faellt
        // ein Zweig des switch weg oder tauschen zwei ihre Saetze, ist genau diese Tafel
        // rot. Zwei der fuenf unterscheiden sich in GENAU EINEM Wort (Welthoehe gegen
        // Weltgrenze), und einer sagt "nicht MEHR" statt "nicht" - drei Stellen, an denen
        // ein Abschreiben ohne Zeugen stillschweigend angleicht.
        for (Reise.Hindernis hindernis : Reise.Hindernis.values()) {
            tafel.fall(hindernis.name(), Reise.hindernisZeilen(sprache, hindernis));
        }

        assertEquals("""
                == WELT_NICHT_GELADEN
                &cDorthin geht es nicht mehr - die Welt ist nicht geladen.
                == ZU_PFERD
                &cNicht zu Pferd - absteigen, dann reisen. Das Reittier bleibt zurueck.
                == AUSSERHALB_DER_WELTHOEHE
                &cDorthin geht es nicht - der Ort liegt ausserhalb der Welthoehe.
                == AUSSERHALB_DER_WELTGRENZE
                &cDorthin geht es nicht - der Ort liegt ausserhalb der Weltgrenze.
                == KEIN_PLATZ_MEHR
                &cDort ist kein Platz zum Stehen mehr.
                """, tafel.ganz());
    }

    @Test
    @DisplayName("33ad - Zweig Reise: die Saetze mit Ziel, die Leine und die Abklingzeit")
    void reiseZweig() {
        Tafel tafel = new Tafel();
        // Je Satz mit {ziel} ZWEI Faelle, mit den zwei verschiedenen Arten von Fuellung.
        tafel.fall("schon-da/welt", Reise.schonDaZeilen(sprache, ZIEL_WELT));
        tafel.fall("schon-da/wendung", Reise.schonDaZeilen(sprache, ZIEL_WENDUNG));
        tafel.fall("kein-platz-am-erscheinungspunkt", Reise
                .keinPlatzAmErscheinungspunktZeilen(sprache, ZIEL_WELT));
        tafel.fall("leine-reisst", Reise.leineReisstZeilen(sprache));
        tafel.fall("nicht-geklappt/welt", Reise.nichtGeklapptZeilen(sprache, ZIEL_WELT));
        tafel.fall("nicht-geklappt/wendung",
                Reise.nichtGeklapptZeilen(sprache, ZIEL_WENDUNG));
        // Eins gegen viele, und die Drei als obere Grenze: ZWISCHEN_ZWEI_REISEN sind drei
        // Sekunden, der aufgerundete Rest liegt also zwischen 1 und 3. Die Null steht
        // nicht in dieser Tafel, weil sie hier nicht ankommen kann - ihre Wache sitzt in
        // zuSchnellHintereinander und entscheidet dort auch, ob die Reise weiterlaeuft.
        // Was eine 0 ergaebe, haelt BildGleichTest fest.
        tafel.fall("abklingzeit/eine", Reise.zuSchnellZeilen(sprache, 1L));
        tafel.fall("abklingzeit/drei", Reise.zuSchnellZeilen(sprache, 3L));

        assertEquals("""
                == schon-da/welt
                &7Du bist schon in kornmark.
                == schon-da/wendung
                &7Du bist schon in deinen letzten Ort.
                == kein-platz-am-erscheinungspunkt
                &cIn kornmark ist am Erscheinungspunkt kein Platz zum Stehen. Das ist nichts, was du beheben kannst - melde es.
                == leine-reisst
                &eAngeleinte Tiere bleiben zurueck - eine Leine reisst beim Weltwechsel.
                == nicht-geklappt/welt
                &cDie Reise nach kornmark hat nicht geklappt.
                == nicht-geklappt/wendung
                &cDie Reise nach deinen letzten Ort hat nicht geklappt.
                == abklingzeit/eine
                &7Einen Augenblick - noch 1 Sekunde.
                == abklingzeit/drei
                &7Einen Augenblick - noch 3 Sekunden.
                """, tafel.ganz());
    }

    @Test
    @DisplayName("33ae - Zweig Aufwaermzeit: die Wartezeit und JEDER Abbruchgrund")
    void aufwaermzeitZweig() {
        Tafel tafel = new Tafel();
        // Die Sekunden kommen als WERT herein, nicht als Wort im Satz: Dieselbe Konstante
        // DAUER rechnet in beginne() die Takte aus. Darum stehen hier ZWEI Dauern - mit
        // einer einzigen waere eine hart in die Datei geschriebene 3 gruen.
        tafel.fall("wartezeit/drei-welt",
                Aufwaermzeit.wartezeitZeilen(sprache, 3L, ZIEL_WELT));
        tafel.fall("wartezeit/drei-wendung",
                Aufwaermzeit.wartezeitZeilen(sprache, 3L, ZIEL_WENDUNG));
        tafel.fall("wartezeit/zehn-welt",
                Aufwaermzeit.wartezeitZeilen(sprache, 10L, ZIEL_WELT));
        // Die ganze Aufzaehlung, wie in 33ac. Die zwei Saetze teilen die letzten 39
        // Zeichen - ein gemeinsamer Satz mit einem {grund}-Wortstueck waere die bequeme,
        // vom Projekt aber verworfene Loesung, und ein Tausch der zwei ist hier rot.
        for (Aufwaermzeit.Abbruchgrund grund : Aufwaermzeit.Abbruchgrund.values()) {
            tafel.fall("abbruch/" + grund.name(),
                    Aufwaermzeit.abbruchZeilen(sprache, grund, ZIEL_WELT));
        }

        assertEquals("""
                == wartezeit/drei-welt
                &7Nach 3 Sekunden geht es nach kornmark - bis dahin stehen bleiben.
                == wartezeit/drei-wendung
                &7Nach 3 Sekunden geht es nach deinen letzten Ort - bis dahin stehen bleiben.
                == wartezeit/zehn-welt
                &7Nach 10 Sekunden geht es nach kornmark - bis dahin stehen bleiben.
                == abbruch/BEWEGT
                &cBewegt - die Reise nach kornmark ist abgebrochen.
                == abbruch/SCHADEN
                &cSchaden genommen - die Reise nach kornmark ist abgebrochen.
                """, tafel.ganz());
    }

    /**
     * Die Weltenliste, wie sie aus der {@code config.yml} kommt.
     *
     * <p>DREI Namen und die Marktstadt zuerst, denn die Reihenfolge der Datei ist eine
     * Zusage ({@code Warpverzeichnis}, Plan 4.3) und bleibt im Programm. Mit nur einem
     * Namen waere jeder Trenner gruen, auch ein falscher - darum drei.
     */
    private static final List<String> WELTEN = List.of("marktstadt", "kornmark", "nether");

    @Test
    @DisplayName("33af - Zweig /warp: die drei Antworten, mit beiden Trennern")
    void warpZweig() {
        Tafel tafel = new Tafel();
        // Die Auskunft auf das nackte /warp - DREI Chatzeilen aus einem Griff, in drei
        // Farben. Der Trenner ist hier ZWEI LEERZEICHEN; eines allein oder ein Komma ist
        // in dieser Tafel rot.
        tafel.fall("ziele", WarpBefehle.zieleZeilen(sprache, WELTEN));
        // Dieselbe Liste in der ANDEREN Lage: zwei Chatzeilen, rot und grau, und der
        // Trenner ist KOMMA UND LEERZEICHEN. Dass die zwei Trenner verschieden sind, ist
        // das heutige Bild und eine Entscheidung - hier steht sie als Zusicherung.
        tafel.fall("kein-ziel", WarpBefehle.keinZielZeilen(sprache, "mordor", WELTEN));
        // Der aufgeloeste Name, nicht die Eingabe: immer klein, immer aus der Datei.
        tafel.fall("welt-fehlt", WarpBefehle.weltFehltZeilen(sprache, "kornmark"));

        assertEquals("""
                == ziele
                &6Reiseziele
                &bmarktstadt  kornmark  nether
                &7/warp <ziel> - oder durch ein Tor am Bundesmarkt.
                == kein-ziel
                &cDorthin geht keine Reise: mordor
                &7Moeglich sind: marktstadt, kornmark, nether
                == welt-fehlt
                &cDie Welt kornmark gibt es gerade nicht.
                """, tafel.ganz());
    }

    @Test
    @DisplayName("33ag - Zweig /zurueck: die Absage, und die Aufschrift als Wortstueck")
    void rueckwegZweig() {
        Tafel tafel = new Tafel();
        tafel.fall("kein-ort-gemerkt", RueckwegBefehle.keinOrtGemerktZeilen(sprache));

        // Das Wortstueck steht MIT in dieser Tafel und nicht in einem zweiten Vergleich:
        // Es ist der einzige Wert, der diese Datei verlaesst, und ein roter Lauf soll
        // beide Haelften auf einmal zeigen.
        assertEquals("""
                == kein-ort-gemerkt
                &7Es ist kein Ort gemerkt - du bist noch nirgends gewesen, wohin es \
                zurueckgeht.
                == letzter-ort
                deinen letzten Ort
                """, tafel.ganz() + "== letzter-ort\n"
                + RueckwegBefehle.letzterOrtWort(sprache) + "\n");

        // Und die Verbindung zu 33ad und 33ae: Genau diese Wendung schicken sie durch die
        // vier Saetze unter reise, die {ziel} einsetzen. Weicht sie ab, liest ein Spieler
        // nach /zurueck vier Saetze, die keine Probe je gesehen hat.
        assertEquals(ZIEL_WENDUNG, RueckwegBefehle.letzterOrtWort(sprache),
                "die Aufschrift von /zurueck ist die, mit der 33ad und 33ae pruefen");
    }

    @Test
    @DisplayName("33ah - der getippte Name kommt als TEXT an, nicht als Farbe")
    void getippterNameBleibtText() {
        Tafel tafel = new Tafel();
        // Der EINZIGE Wert dieser zwei Dateien, der vom Spieler kommt. Er geht ROH in den
        // Satz, und das ist gefahrlos, weil zuerst der WORTLAUT geparst wird und der Wert
        // DANACH als Blatt eingesetzt wird (Sprache.ersetze). Haette man ihn vorher in
        // die Zeichenkette gespleisst, faerbte ein Spieler seinen eigenen Chat - und mit
        // einem <click> liefe ein Befehl in seinem Namen.
        tafel.fall("farbtag",
                WarpBefehle.keinZielZeilen(sprache, "<red>boese</red>", WELTEN));
        // Dasselbe fuer eine Wertmarke: Sie darf nicht ein zweites Mal gelesen werden,
        // sonst nennt ein Spieler mit {liste} den Inhalt eines anderen Platzhalters.
        tafel.fall("wertmarke", WarpBefehle.keinZielZeilen(sprache, "{liste}", WELTEN));

        assertEquals("""
                == farbtag
                &cDorthin geht keine Reise: <red>boese</red>
                &7Moeglich sind: marktstadt, kornmark, nether
                == wertmarke
                &cDorthin geht keine Reise: {liste}
                &7Moeglich sind: marktstadt, kornmark, nether
                """, tafel.ganz());

        // Die zweite Haelfte, und sie ist ein eigener Riegel: Brigadier laesst die zwei
        // Zeichen schon gar nicht durch. word() liest nur Buchstaben, Ziffern und
        // _ - . + - die spitze Klammer beendet das Wort, und die Umbruchmarke kann gar
        // nicht getippt werden. Das ist wichtig, weil Sprache.teile am Zeilenumbruch IM
        // WERT teilen WUERDE: Gegen Farbe schuetzt die Sprachmaschine, gegen eine zweite
        // Chatzeile schuetzt hier der Befehlsleser.
        assertEquals("", gelesenesWort("<red>boese</red>"),
                "eine spitze Klammer darf gar nicht in das Befehlsargument kommen");
        assertEquals("", gelesenesWort("{liste}"),
                "eine geschweifte Klammer ebenso");
        assertEquals("kornmark", gelesenesWort("kornmark\nzweite Zeile"),
                "und das Wort endet VOR der Umbruchmarke");
    }

    /** Was {@code StringArgumentType.word()} aus einer Eingabe wirklich liest. */
    private static String gelesenesWort(String getippt) {
        return new StringReader(getippt).readUnquotedString();
    }

    @Test
    @DisplayName("33ai - die leere Weltenliste: eine leere Chatzeile, wie bisher")
    void leereWeltenliste() {
        // Der entartete Fall, und er wird beim Umzug NICHT repariert: Sind alle Eintraege
        // der Weltenliste leer oder fehlen sie, ueberspringt Warpverzeichnis.ausWelten sie
        // still, und /warp zeigt eine Ueberschrift mit einer leeren Zeile darunter.
        // Geklagt wird darueber beim Lesen der config.yml und nicht hier.
        //
        // Eine EIGENE Probe und keine Zeile in 33af, weil dieser entartete Fall die
        // einzige Stelle ist, an der eine Umbruchmarke eine LEERE Chatzeile erzeugt -
        // und weil der Verzicht auf einen Textblock das abschliessende Leerzeichen
        // hinter "Moeglich sind: " sichert, das ein Editor sonst wegputzt.
        //
        // Das Alt-Bild IST hier gleich, nachgemessen: Component.text("", AQUA) und
        // Component.empty() werden beide zur leeren Zeichenkette - der Farbcode
        // entfaellt bei leerem Inhalt. Diese Probe war vor dem Umzug gruen und ist es
        // danach; sie haelt den Fall fest, sie repariert ihn nicht.
        Tafel tafel = new Tafel();
        tafel.fall("ziele/leer", WarpBefehle.zieleZeilen(sprache, List.of()));
        tafel.fall("kein-ziel/leer",
                WarpBefehle.keinZielZeilen(sprache, "mordor", List.of()));

        // KEIN Textblock, und das ist der Grund, warum diese Probe ueberhaupt etwas
        // zusichert: Ein Textblock streicht abschliessende Leerzeichen, und genau eines
        // steht hier hinter "Moeglich sind: ". Mit einem Textblock waere die Zusicherung
        // stumm um ein Zeichen schwaecher.
        assertEquals("== ziele/leer\n"
                + "&6Reiseziele\n"
                + "\n"
                + "&7/warp <ziel> - oder durch ein Tor am Bundesmarkt.\n"
                + "== kein-ziel/leer\n"
                + "&cDorthin geht keine Reise: mordor\n"
                + "&7Moeglich sind: \n", tafel.ganz());
    }

    /** Der Name eines Eigentuemers, wie der Benutzerzwischenspeicher ihn liefert. */
    private static final String EIGENTUEMER = "Bert";

    /** Was statt des Namens steht, wenn der Server ihn nicht kennt: acht Hexzeichen. */
    private static final String EIGENTUEMER_UNBEKANNT = "0f1e2d3c";

    @Test
    @DisplayName("33aj - Handbalken der Schutzwache: alle VIER Wortlaute")
    void schutzwacheZweig() {
        Tafel tafel = new Tafel();
        tafel.fall("weg-und-mauer",
                List.of(Schutztext.wegUndMauerBalken(sprache)));
        tafel.fall("parzelle-frei",
                List.of(Schutztext.parzelleFreiBalken(sprache)));
        tafel.fall("ausserhalb", List.of(Schutztext.ausserhalbBalken(sprache)));
        tafel.fall("fremde-parzelle/name",
                List.of(Schutztext.fremdeParzelleBalken(sprache, EIGENTUEMER)));
        tafel.fall("fremde-parzelle/unbekannt",
                List.of(Schutztext.fremdeParzelleBalken(sprache,
                        EIGENTUEMER_UNBEKANNT)));

        // Der Textblock schadet hier nicht: Keine dieser Zeilen endet auf einem
        // Leerzeichen, anders als in 33ai.
        assertEquals("""
                == weg-und-mauer
                &eWeg und Mauer gehoeren allen
                == parzelle-frei
                &eDiese Parzelle ist frei - /parzelle nehmen
                == ausserhalb
                &eHier endet die Marktstadt
                == fremde-parzelle/name
                &cDiese Parzelle gehoert &fBert
                == fremde-parzelle/unbekannt
                &cDiese Parzelle gehoert &f0f1e2d3c
                """, tafel.ganz());
    }

    @Test
    @DisplayName("33ak - die zwei Saetze der Ankunftswache, je EINE Chatzeile")
    void ankunftswacheZweige() {
        // Beide Saetze stehen im Quelltext ueber mehrere Zeilen, mit +
        // zusammengesetzt - und das ist KEIN Umbruch im Chat. Dass wirklich je eine
        // Zeile herauskommt, steht nirgends sonst: Eine Tafel mit zwei Faellen und
        // je einer Zeile sagt es, und eine Umbruchmarke in der Vorlage macht sie rot.
        Tafel tafel = new Tafel();
        tafel.fall("kein-spawnpunkt", Ankunftswache.keinSpawnpunktZeilen(sprache));
        tafel.fall("abenteuer", Ankunftswache.abenteuerZeilen(sprache));

        assertEquals("""
                == kein-spawnpunkt
                &7Hier wird kein Spawnpunkt gesetzt - gestorben wird immer nach Altfurt. Schlafen geht weiter.
                == abenteuer
                &7Hier ist Abenteuer: ansehen und benutzen, aber nicht abbauen. Gebaut wird in der Marktstadt.
                """, tafel.ganz());
    }

    @Test
    @DisplayName("33al - die Absage am Portal: ZWEI Chatzeilen aus EINEM Griff")
    void portalwacheZweig() {
        // Der Gegenfall zu 33ak, und der Grund fuer die Umbruchmarke: Hier sind es
        // zwei Chatzeilen mit zwei Farben, und sie gehen immer zusammen hinaus.
        // Dieselbe Lage wie warp.kein-ziel - rote Absage, grauer Weg darunter.
        //
        // Der Schwanz " - oder durch ein Tor am Bundesmarkt." steht hier zum zweiten
        // Mal in dieser Datei: 33af traegt ihn in warp.ziele. Bis zu diesem Zug kam
        // er dort aus der Vorlage und hier aus dem Programm; danach aus zwei
        // Blaettern, die der Betreiber auseinanderentwickeln darf.
        Tafel tafel = new Tafel();
        tafel.fall("kein-ausgang", Portalwache.keinAusgangZeilen(sprache));

        assertEquals("""
                == kein-ausgang
                &cHier fuehrt kein Portal hinaus.
                &7Nether und Ende erreichst du mit /warp nether und /warp end - oder durch ein Tor am Bundesmarkt.
                """, tafel.ganz());
    }

    /** Der Stand des Plugins, wie {@code getPluginMeta()} ihn liefern wuerde. */
    private static final String VERSION = "0.1.0";

    /** Die Hauptnummer der Java-Ausgabe, wie {@code Runtime.version()} sie nennt. */
    private static final String JAVA = "25";

    /** Die Fassung des Servers, wie {@code getServer()} sie nennt. */
    private static final String MINECRAFT = "1.21.4";

    @Test
    @DisplayName("33an - JEDER Ausgang des Berufsurteils hat seine eigene Absage")
    void jederAusgangHatSeineAbsage() {
        // Die dritte mechanische Familie dieses Projekts, und die erste, die NICHT in
        // Sprachschluessel steht: Beruftext.absageZeilen waehlt mit einem switch ueber
        // die Aufzaehlung, und ein switch ohne default muss vollstaendig sein. Kommt ein
        // Ausgang hinzu, bricht der BAU - und nicht das Spiel.
        //
        // Was diese Probe darueber hinaus faengt: dass jeder Ausgang eine Zeile ERGIBT,
        // und zwar eine andere als die anderen. Ein switch, der zweimal auf denselben
        // Satz zeigt, ist vollstaendig und trotzdem falsch - der Spieler liest dann bei
        // zwei verschiedenen Absagen dasselbe und sucht den Fehler bei sich.
        Sprache sprache = Sprachprobe.spracheAusDemJar();
        var bilder = new TreeSet<String>();
        for (Berufsregel.Ausgang ausgang : Berufsregel.Ausgang.values()) {
            if (ausgang == Berufsregel.Ausgang.ANGENOMMEN) {
                // Ein angenommenes Urteil hat keine Absage - es hat eine Zusage, und die
                // prueft BerufBefehlTest am getippten Befehl.
                continue;
            }
            var urteil = new Berufsregel.Urteil(ausgang, "bergmann", 5_000L,
                    Duration.ofHours(3L), 2);
            List<Component> zeilen = Beruftext.absageZeilen(sprache, urteil, 1_200L);

            assertEquals(1, zeilen.size(), ausgang + " muss GENAU eine Zeile ergeben");
            String bild = Sprachprobe.bild(zeilen.getFirst());
            assertFalse(bild.isBlank(), ausgang + " ergibt eine leere Zeile - dann sieht"
                    + " der Spieler, dass etwas geschieht, aber nicht was");
            assertTrue(bilder.add(bild), ausgang + " sagt dasselbe wie eine andere"
                    + " Absage: " + bild);
        }
        assertEquals(Berufsregel.Ausgang.values().length - 1, bilder.size(),
                "zehn Ausgaenge, zehn verschiedene Saetze: " + bilder);
    }

    @Test
    @DisplayName("33ao - ein angenommenes Urteil hat keine Absage, und das wirft")
    void angenommenHatKeineAbsage() {
        // Die Gegenprobe zu 33an. Ein stilles leeres Ergebnis waere hier das Schlimmste:
        // Der Zweig haelt sein Urteil fuer eine Absage, sendet nichts, und der Spieler
        // tippt /beruf waehlen und liest GAR NICHTS.
        Sprache sprache = Sprachprobe.spracheAusDemJar();
        var angenommen = new Berufsregel.Urteil(Berufsregel.Ausgang.ANGENOMMEN,
                "bergmann", 0L, Duration.ZERO, 2);

        assertThrows(IllegalArgumentException.class,
                () -> Beruftext.absageZeilen(sprache, angenommen, 0L));
    }

    @Test
    @DisplayName("33am - /freimarken: alle VIER Lagen von stand und BEIDE von preis")
    void bundZweige() {
        // Die drei Werte sind PROBEWERTE: getPluginMeta(), Runtime.version() und
        // getServer() gibt es ohne laufenden Server nicht. Festgenagelt ist damit
        // nicht ihr Inhalt, sondern der RAHMEN um sie - das Wort Stand, das Wort
        // Java und die zwei Mittelpunkte dazwischen.
        //
        // Alle vier Lagen von standZeilen, und nicht drei: Die zwei Wahrheitswerte
        // sind unabhaengig voneinander, und eine Naht, die sie vertauscht, bliebe
        // bei drei Faellen in einem davon gruen.
        Tafel tafel = new Tafel();
        tafel.fall("stand/fluechtig-ohne-recht",
                Bundtext.standZeilen(sprache, VERSION, JAVA, MINECRAFT, true,
                        false));
        tafel.fall("stand/fluechtig-mit-recht",
                Bundtext.standZeilen(sprache, VERSION, JAVA, MINECRAFT, true,
                        true));
        tafel.fall("stand/mariadb-ohne-recht",
                Bundtext.standZeilen(sprache, VERSION, JAVA, MINECRAFT, false,
                        false));
        tafel.fall("stand/mariadb-mit-recht",
                Bundtext.standZeilen(sprache, VERSION, JAVA, MINECRAFT, false,
                        true));
        // Die gratis-Lage und die Preislage. Der Betrag der zweiten Zeile ist in
        // beiden derselbe Wert, und das ist kein Zufall: Wer noch keine Parzelle
        // hat, hat auch noch nichts ausgegeben - darum steht in der gratis-Lage
        // eine Null.
        tafel.fall("preis/gratis",
                Bundtext.preisZeilen(sprache, 0, 0L, 0L));
        tafel.fall("preis/kostet",
                Bundtext.preisZeilen(sprache, 1, 50_000L, 25_000L));

        assertEquals("""
                == stand/fluechtig-ohne-recht
                &6Der Freimarkenbund
                &7Stand 0.1.0 · Java 25 · 1.21.4
                &cAblage fluechtig - nichts ueberlebt den Neustart
                &7/parzelle · /mark · /freimarken preis <anzahl> · /freimarken spielzeit
                == stand/fluechtig-mit-recht
                &6Der Freimarkenbund
                &7Stand 0.1.0 · Java 25 · 1.21.4
                &cAblage fluechtig - nichts ueberlebt den Neustart
                &7/parzelle · /mark · /freimarken preis <anzahl> · /freimarken spielzeit
                &8/freimarken welt · parzelle <x> <z> · neuladen
                == stand/mariadb-ohne-recht
                &6Der Freimarkenbund
                &7Stand 0.1.0 · Java 25 · 1.21.4
                &8Ablage MariaDB
                &7/parzelle · /mark · /freimarken preis <anzahl> · /freimarken spielzeit
                == stand/mariadb-mit-recht
                &6Der Freimarkenbund
                &7Stand 0.1.0 · Java 25 · 1.21.4
                &8Ablage MariaDB
                &7/parzelle · /mark · /freimarken preis <anzahl> · /freimarken spielzeit
                &8/freimarken welt · parzelle <x> <z> · neuladen
                == preis/gratis
                &aDie erste Parzelle ist gratis.
                &8Bisher ausgegeben: 0 Mark
                == preis/kostet
                &7Parzelle Nr. 2 kostet &650.000 Mark&7.
                &8Bisher ausgegeben: 25.000 Mark
                """, tafel.ganz());
    }
}
