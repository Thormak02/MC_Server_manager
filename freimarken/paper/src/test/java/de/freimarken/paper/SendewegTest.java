package de.freimarken.paper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.freimarken.kern.Buchung;
import de.freimarken.kern.Buchungsgrund;
import java.time.Instant;
import java.time.ZoneId;
import java.util.TreeSet;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Zahl, Reihenfolge und Werte je Zweig - auf der NAHT.
 *
 * <p>Die Naht ist je Zweig eine reine Methode, die aus einfachen Werten ein Component oder
 * eine Liste baut, ohne {@code Player} und ohne Bukkit. In Bauschritt 1 ist das
 * {@link Verlaufszeile#zeile}. Das ist kein neues Muster, sondern das Muster dieses
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
 */
class SendewegTest {

    private static final UUID ANNA = UUID.nameUUIDFromBytes("anna".getBytes());
    private static final ZoneId BERLIN = ZoneId.of("Europe/Berlin");

    /** 02.11.2026, 13:37 UTC - in Berlin ist das 14:37 (Winterzeit). */
    private static final Instant MITTAG = Instant.parse("2026-11-02T13:37:00Z");

    private static Buchung buchung(Buchungsgrund grund, long betrag, long nachher) {
        return new Buchung(1L, MITTAG, ANNA, grund, betrag, nachher);
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
        assertEquals(new TreeSet<>(Sprachschluessel.familienDeckung()), ausDenGruenden,
                "dieselbe Menge, die GrenzeTest als selbstbescheinigend durchlaesst");
    }
}
