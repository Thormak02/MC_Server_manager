package de.freimarken.paper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.freimarken.kern.Art;
import de.freimarken.kern.Besitzbuch;
import de.freimarken.kern.Buchung;
import de.freimarken.kern.Buchungsgrund;
import de.freimarken.kern.Parzelle;
import de.freimarken.kern.Richtung;
import de.freimarken.kern.Wirtschaftswerte;
import de.freimarken.kern.Zustand;
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
        var beide = new TreeSet<String>(ausDenBescheiden);
        for (Buchungsgrund grund : Buchungsgrund.values()) {
            beide.add(Sprachschluessel.grund(grund).pfad());
        }
        assertEquals(beide, new TreeSet<>(Sprachschluessel.familienDeckung()),
                "genau die Menge, die GrenzeTest als selbstbescheinigend durchlaesst");
    }

    @Test
    @DisplayName("33d - Zweig /mark: Stand und Hinweis, ganze Liste")
    void markZweig() {
        Tafel tafel = new Tafel();
        tafel.fall("stand-25000", ParzellenBefehle.markZeilen(sprache, 25_000L));
        tafel.fall("stand-0", ParzellenBefehle.markZeilen(sprache, 0L));

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
}
