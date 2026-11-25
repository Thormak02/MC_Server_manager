package de.freimarken.kern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Die Teamrollen aus der Datei - was eine Rolle sein darf und was sie gibt. */
@DisplayName("Teamrollen aus der config.yml")
class TeamwerteTest {

    @Test
    @DisplayName("Die Vorgabe nennt die fuenf Rollen aus Plan 12c.2")
    void vorgabeNenntFuenfRollen() {
        assertEquals(List.of("helfer", "moderator", "verwalter", "eventleiter",
                        "baumeister"), Teamwerte.VORGABE.schluessel(),
                "die fuenf des Plans, in der Reihenfolge der Datei");
        assertTrue(Teamwerte.VORGABE.greift());
    }

    @Test
    @DisplayName("Kein Recht der Vorgabe traegt die Vorsilbe")
    void vorgabeOhneVorsilbe() {
        // Mit Vorsilbe in der Datei stuende am Ende freimarken.freimarken.blockbuch -
        // ein Recht, das niemand anmeldet und nach dem niemand fragt. Der Konstruktor
        // klagt darueber; diese Probe haelt fest, dass die eigene Vorgabe daran nicht
        // schon scheitert.
        for (Teamwerte.Rolle rolle : Teamwerte.VORGABE_ROLLEN) {
            for (String recht : rolle.rechte()) {
                assertFalse(recht.startsWith(Teamwerte.VORSILBE),
                        rolle.schluessel() + " vergibt " + recht + " mit Vorsilbe");
            }
        }
    }

    @Test
    @DisplayName("Die Rechte mehrerer Rollen werden VEREINIGT - mit Vorsilbe")
    void rechteWerdenVereinigt() {
        Teamwerte werte = new Teamwerte(List.of(
                new Teamwerte.Rolle("eine", List.of("alpha", "beta")),
                new Teamwerte.Rolle("andere", List.of("beta", "gamma"))));

        Set<String> rechte = werte.rechteFuer(List.of("eine", "andere"));

        // Die Erwartung ist AUSGESCHRIEBEN und nicht aus rechteFuer geholt: Eine Probe,
        // die ihre Erwartung aus der gepruefen Funktion nimmt, faengt kein Vertauschen.
        assertEquals(new LinkedHashSet<>(List.of("freimarken.alpha", "freimarken.beta",
                "freimarken.gamma")), rechte, "drei Rechte, nicht vier - beta nur einmal");
    }

    @Test
    @DisplayName("Eine Rolle, die dieser Server nicht kennt, gibt keine Rechte")
    void unbekannteRolleGibtNichts() {
        Teamwerte werte = new Teamwerte(List.of(
                new Teamwerte.Rolle("eine", List.of("alpha"))));

        // Sie steht in der Ablage, weil der Betreiber ihre Zeile aus der Datei genommen
        // hat. Ein Wurf hier waere ein Beitritt, der abbricht - und zwar beim Moderator,
        // also bei dem, der es melden koennte.
        assertEquals(Set.of("freimarken.alpha"),
                werte.rechteFuer(List.of("eine", "weggefallene")));
        assertEquals(Set.of(), werte.rechteFuer(List.of("weggefallene")));
        assertEquals(Set.of(), werte.rechteFuer(null));
    }

    @Test
    @DisplayName("Eine Rolle mit Vorsilbe in der Rechteliste wird abgewiesen")
    void vorsilbeInDerDateiKlagt() {
        IllegalArgumentException klage = assertThrows(IllegalArgumentException.class,
                () -> new Teamwerte.Rolle("eine", List.of("freimarken.blockbuch")));

        // Die Klage muss die Vorsilbe NENNEN: Ein Betreiber, der nur "ungueltiges Recht"
        // liest, nimmt den Punkt heraus und hat denselben Fehler.
        assertTrue(klage.getMessage().contains("freimarken."),
                "die Klage sagt, was zu tun ist: " + klage.getMessage());
    }

    @Test
    @DisplayName("Ein Rollenname muss schon kleingeschrieben hereinkommen")
    void grossgeschriebenerNameKlagt() {
        // Und nicht normalisiert: Daran haengt der Satzpfad team.rolle.<name>, und ein
        // Pfad wird woertlich nachgeschlagen. Ein "Moderator" in der Datei ergaebe eine
        // Sprachzeile, die nie gefragt wird - also einen Namen, der im Spiel als Pfad
        // dasteht.
        assertThrows(IllegalArgumentException.class,
                () -> new Teamwerte.Rolle("Moderator", List.of("alpha")));
        assertThrows(IllegalArgumentException.class,
                () -> new Teamwerte.Rolle(" moderator", List.of("alpha")));
    }

    @Test
    @DisplayName("Dieselbe Rolle zweimal in der Liste wird abgewiesen")
    void doppelteRolleKlagt() {
        // Welche der beiden Rechtelisten gilt, entschiede die Reihenfolge - und die ist
        // bei Rollen ausdruecklich bedeutungslos. Ein Betreiber, der eine Rolle zweimal
        // eintraegt, hat eine umbenannt und das Original vergessen.
        assertThrows(IllegalArgumentException.class, () -> new Teamwerte(List.of(
                new Teamwerte.Rolle("eine", List.of("alpha")),
                new Teamwerte.Rolle("eine", List.of("beta")))));
    }

    @Test
    @DisplayName("Doppelte Rechte in EINER Rolle fallen weg, die Reihenfolge bleibt")
    void doppelteRechteFallenWeg() {
        Teamwerte.Rolle rolle = new Teamwerte.Rolle("eine",
                List.of("gamma", "alpha", "gamma"));

        assertEquals(List.of("gamma", "alpha"), rolle.rechte(),
                "ohne Doppelte, aber in der Reihenfolge der Datei - sie steht im"
                        + " Startbericht, und der soll zwischen zwei Starts vergleichbar"
                        + " sein");
    }

    @Test
    @DisplayName("Eine leere Rollenliste ist erlaubt und heisst: kein Team")
    void leereListeIstErlaubt() {
        // Der Aus-Zustand, und er braucht keinen Schalter daneben. Ein an/aus wie bei
        // den Raengen hiesse hier "allen Moderatoren sind die Rechte entzogen", und das
        // waere ein Schalter, dessen einziger Zweck ein Unfall ist.
        Teamwerte leer = new Teamwerte(List.of());

        assertFalse(leer.greift());
        assertEquals(List.of(), leer.schluessel());
        assertEquals(Set.of(), leer.rechteFuer(List.of("moderator")));
    }

    @Test
    @DisplayName("Mehr als sechzehn Rollen werden abgewiesen")
    void zuVieleRollenKlagen() {
        List<Teamwerte.Rolle> zuViele = new java.util.ArrayList<>();
        for (int nummer = 0; nummer <= Teamwerte.HOECHSTE_ROLLEN; nummer++) {
            zuViele.add(new Teamwerte.Rolle("rolle-" + nummer, List.of("alpha")));
        }

        // Eine verrutschte Einrueckung in der Datei wird sonst zu zweihundert Rollen,
        // von denen jede eine Sprachzeile verlangt - und ohne sie verweigert der Start.
        assertThrows(IllegalArgumentException.class, () -> new Teamwerte(zuViele));
    }

    @Test
    @DisplayName("Eine Rolle ohne Rechte steht im Bericht")
    void rolleOhneRechteImBericht() {
        Teamwerte werte = new Teamwerte(List.of(
                new Teamwerte.Rolle("leere", List.of())));

        List<String> saetze = werte.ungereimtheiten();

        assertEquals(1, saetze.size(), "genau ein Satz: " + saetze);
        assertTrue(saetze.get(0).contains("leere"),
                "und er nennt die Rolle: " + saetze.get(0));
        assertTrue(saetze.get(0).startsWith(Teamwerte.PFAD_ROLLEN),
                "mit dem Pfad vorn, damit der Betreiber die Zeile findet");
    }

    @Test
    @DisplayName("Zwei Rollen mit derselben Rechteliste stehen im Bericht")
    void gleicheRechtelistenImBericht() {
        Teamwerte werte = new Teamwerte(List.of(
                new Teamwerte.Rolle("eine", List.of("alpha", "beta")),
                // Dieselben Rechte, ANDERE Reihenfolge: Sie sollen trotzdem als gleich
                // erkannt werden - sonst meldete der Bericht die Vorgabe nicht, bei der
                // Helfer und Eventleiter heute gleich viel duerfen.
                new Teamwerte.Rolle("andere", List.of("beta", "alpha"))));

        List<String> saetze = werte.ungereimtheiten();

        assertEquals(1, saetze.size(), "genau ein Satz: " + saetze);
        assertTrue(saetze.get(0).contains("eine") && saetze.get(0).contains("andere"),
                "und er nennt beide: " + saetze.get(0));
    }

    @Test
    @DisplayName("Zwei LEERE Rollen gelten nicht als gleiche Rechtelisten")
    void zweiLeereRollenSindKeinePaarmeldung() {
        Teamwerte werte = new Teamwerte(List.of(
                new Teamwerte.Rolle("eine", List.of()),
                new Teamwerte.Rolle("andere", List.of())));

        // Sonst stuenden drei Saetze da, wo zwei genuegen: Beide sind schon einzeln als
        // wirkungslos gemeldet, und "diese zwei geben dasselbe, naemlich nichts" ist
        // keine Auskunft, sondern Rauschen.
        List<String> saetze = werte.ungereimtheiten();
        assertEquals(2, saetze.size(), "zwei Saetze, einer je leerer Rolle: " + saetze);
    }

    @Test
    @DisplayName("Die Vorgabe des Programms meldet GAR NICHTS mehr")
    void vorgabeMeldetNichts() {
        List<String> saetze = Teamwerte.VORGABE.ungereimtheiten();

        // BIS ZUM 25.11.2026 stand hier "nur das bekannte Paar": Helfer und Eventleiter
        // hatten dieselbe Liste, weil ihre Werkzeuge fehlten (Meldungen in Teil C,
        // Events in Plan 15). Teil C hat das Werkzeug gebracht - der Helfer hat jetzt
        // freimarken.meldungen, der Eventleiter nicht. Damit ist die Vorgabe ohne
        // Ungereimtheit, und der Satz, der die Trennung vorhergesagt hat, ist eingeloest.
        //
        // Die Probe bleibt scharf: Taucht wieder ein Paar auf, hat jemand eine
        // Rechtezeile vergessen - und eine Rolle, die versehentlich so viel darf wie eine
        // andere, faellt im Spiel niemandem auf.
        assertEquals(List.of(), saetze, "die Vorgabe ist sauber: " + saetze);
    }

    @Test
    @DisplayName("Die Rolle wird unabhaengig von der Schreibweise gefunden")
    void rolleWirdGefunden() {
        Teamwerte werte = Teamwerte.VORGABE;

        assertTrue(werte.kennt("moderator"));
        assertTrue(werte.kennt("MODERATOR"), "der Befehl gibt herein, was getippt wurde");
        assertTrue(werte.kennt(" Moderator "));
        assertFalse(werte.kennt("hausmeister"));
        assertFalse(werte.kennt(null));
        assertEquals("moderator", werte.rolle("Moderator").orElseThrow().schluessel());
    }

    @Test
    @DisplayName("Die Rollenliste laesst sich von aussen nicht veraendern")
    void listeIstUnveraenderlich() {
        List<Teamwerte.Rolle> eingang = new java.util.ArrayList<>(
                List.of(new Teamwerte.Rolle("eine", List.of("alpha"))));
        Teamwerte werte = new Teamwerte(eingang);

        eingang.clear();

        assertEquals(List.of("eine"), werte.schluessel(),
                "die Werte halten eine Abschrift, kein Fenster in die Liste des Lesers");
        assertThrows(UnsupportedOperationException.class, () -> werte.rollen().add(null));
    }
}
