package de.freimarken.kern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Die Namen der Rechte - und dass fuer jede Art einer dabei ist.
 *
 * <p>Die Probe {@link #jedeArtHatIhrRecht()} ist die, die wartet: Sie wird rot, sobald
 * eine vierte Art dazukommt, fuer die niemand ein Recht angelegt hat. Ohne sie waere der
 * Riegel fuer die neue Art der {@code switch} in {@link Strafrechte#fuer} - und der
 * waere ein Uebersetzungsfehler, also gefunden. Diese Probe faengt den anderen Fall: ein
 * Name, der dasteht, aber derselbe ist wie ein anderer.
 */
class StrafrechteTest {

    /** Die Namen, mit denen die Proben arbeiten - wie im Plugin aufgebaut. */
    private static final Strafrechte NAMEN = new Strafrechte(
            "freimarken.strafen.stumm", "freimarken.strafen.rauswurf",
            "freimarken.strafen.bann", "freimarken.strafen.dauerhaft",
            "freimarken.strafen.aufheben", "freimarken.strafen.team");

    @Test
    @DisplayName("Jede Art hat ihr Recht, und jedes Recht gilt nur fuer eine Art")
    void jedeArtHatIhrRecht() {
        Set<String> gesehen = new HashSet<>();

        for (Strafart art : Strafart.values()) {
            String recht = NAMEN.fuer(art);
            assertFalse(recht == null || recht.isBlank(), art + " hat kein Recht");
            assertTrue(gesehen.add(recht),
                    "das Recht \"" + recht + "\" gilt fuer zwei Arten - dann darf, wer"
                            + " kicken darf, auch bannen, und die Trennung aus Plan"
                            + " 12c.2 ist keine");
        }
    }

    @Test
    @DisplayName("Die Rechte der Arten sind andere als dauerhaft, aufheben und team")
    void sonderrechteSindEigene() {
        // Ohne diese Zusage waere eines der drei Sonderrechte dasselbe wie ein
        // Artrecht - und dann duerfte jeder, der bannen darf, auch dauerhaft bannen.
        // Das ist genau der Zustand, gegen den dieser Teil gebaut ist.
        for (Strafart art : Strafart.values()) {
            assertFalse(NAMEN.fuer(art).equals(NAMEN.dauerhaft()),
                    art + ": das Artrecht ist dasselbe wie das Recht fuer dauerhafte"
                            + " Strafen");
            assertFalse(NAMEN.fuer(art).equals(NAMEN.aufheben()),
                    art + ": das Artrecht ist dasselbe wie das Recht zum Aufheben");
            assertFalse(NAMEN.fuer(art).equals(NAMEN.team()),
                    art + ": das Artrecht ist dasselbe wie das Recht gegen Teammitglieder");
        }
        assertFalse(NAMEN.dauerhaft().equals(NAMEN.aufheben()));
        assertFalse(NAMEN.dauerhaft().equals(NAMEN.team()));
        assertFalse(NAMEN.aufheben().equals(NAMEN.team()));
    }

    @Test
    @DisplayName("Ein leerer Name wirft - ein Riegel, der stumm immer zu ist")
    void leererNameWirft() {
        assertThrows(IllegalArgumentException.class, () -> new Strafrechte("", "b", "c",
                "d", "e", "f"));
        assertThrows(IllegalArgumentException.class, () -> new Strafrechte("a", null,
                "c", "d", "e", "f"));
        assertThrows(IllegalArgumentException.class, () -> new Strafrechte("a", "b",
                "   ", "d", "e", "f"));
        assertThrows(IllegalArgumentException.class, () -> new Strafrechte("a", "b", "c",
                "", "e", "f"));
        assertThrows(IllegalArgumentException.class, () -> new Strafrechte("a", "b", "c",
                "d", "", "f"));
        assertThrows(IllegalArgumentException.class, () -> new Strafrechte("a", "b", "c",
                "d", "e", ""));
    }

    @Test
    @DisplayName("Ohne Art gibt es kein Recht")
    void ohneArtWirft() {
        assertThrows(IllegalArgumentException.class, () -> NAMEN.fuer(null));
    }

    @Test
    @DisplayName("Die KONSOLE darf ueberhaupt strafen - eine leere Menge nicht")
    void konsoleDarfLeereMengeNicht() {
        // Der Unterschied IST der Riegel: null heisst die Konsole und hat
        // Dateizugriff; eine leere Menge ist ein Spieler ohne jedes Recht.
        assertTrue(NAMEN.darfUeberhauptStrafen(null));
        assertFalse(NAMEN.darfUeberhauptStrafen(Set.of()));
    }

    @Test
    @DisplayName("Ein EINZIGES Artrecht genuegt, um ueberhaupt strafen zu duerfen")
    void einArtrechtGenuegt() {
        for (Strafart art : Strafart.values()) {
            assertTrue(NAMEN.darfUeberhauptStrafen(Set.of(NAMEN.fuer(art))),
                    art + ": wer diese Art darf, darf ueberhaupt strafen");
        }
    }

    @Test
    @DisplayName("Die drei Sonderrechte allein machen niemanden zum Strafenden")
    void sonderrechteAlleinGenuegenNicht() {
        // Wer nur aufheben darf, straft nicht - und ist damit auch nicht als
        // Teammitglied davor geschuetzt, selbst gestraft zu werden. Das waere sonst
        // die Luecke, durch die jemand mit einem harmlosen Recht unantastbar wird.
        assertFalse(NAMEN.darfUeberhauptStrafen(Set.of(NAMEN.aufheben())));
        assertFalse(NAMEN.darfUeberhauptStrafen(Set.of(NAMEN.dauerhaft())));
        assertFalse(NAMEN.darfUeberhauptStrafen(Set.of(NAMEN.team())));
        assertFalse(NAMEN.darfUeberhauptStrafen(Set.of("freimarken.heim")));
    }

    @Test
    @DisplayName("Die Namen kommen unveraendert wieder heraus")
    void namenKommenUnveraendertZurueck() {
        // Kein Kleinschreiben, kein Beschneiden: Ein Bukkit-Rechtename ist genau der
        // Text aus der plugin.yml, und eine Veraenderung hier waere ein Riegel, der
        // auf ein Recht hoert, das es nicht gibt.
        assertEquals("freimarken.strafen.bann", NAMEN.fuer(Strafart.BANN));
        assertEquals("freimarken.strafen.dauerhaft", NAMEN.dauerhaft());
    }
}
