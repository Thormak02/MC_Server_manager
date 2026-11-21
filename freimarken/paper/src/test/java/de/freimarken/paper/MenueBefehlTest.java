package de.freimarken.paper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * {@code /menu} GETIPPT - durch den echten Befehlsbaum.
 *
 * <p>Dieselbe Bauart wie {@link RangBefehlTest}, und bis zum 21.11.2026 fehlte sie: Das
 * Menue hatte nur Tafeln, und eine Tafel zaehlt Aufrufe im Quelltext - sie weiss nicht,
 * ob der Befehl, den ein Mensch tippt, dort ankommt.
 *
 * <p>Das OEFFNEN ist eine Naht ({@code Menueoeffner}), und diese Probe haengt eine
 * Mitschrift daran: Sie sieht, WELCHE Tafel geoeffnet worden waere und fuer wen.
 */
@DisplayName("/menu getippt")
class MenueBefehlTest {

    private static final UUID FINN = Spielerprobe.kennungAus(215);

    /** Die Absage, wenn das Fenster nicht aufgeht. */
    private static final String GESTOERT =
            "&#d9564dDas Menue liess sich nicht oeffnen&#a5b4bf - der Grund steht im"
                    + " Serverprotokoll.";

    /** Die Absage auf der Konsole - ein Menue ist ein Inventar. */
    private static final String KEINE_KONSOLE =
            "&#a5b4bf/menu zeigt ein Menue - auf der Konsole gibt es keines.";

    /** Die Mitschrift an der Naht - schreibt VOR dem Zurueckgeben mit. */
    private static final class Oeffnerprobe implements MenueBefehle.Menueoeffner {

        private final boolean geht;
        private final List<String> geoeffnet = new ArrayList<>();

        Oeffnerprobe(boolean geht) {
            this.geht = geht;
        }

        @Override
        public boolean oeffne(Player wer, Menuetafel tafel) {
            geoeffnet.add(wer.getName() + " -> " + tafel.titel().pfad());
            return geht;
        }
    }

    private Spielerprobe finn;

    @BeforeAll
    static void serverStellen() {
        Serverprobe.einmalSetzen();
    }

    @BeforeEach
    void vorher() {
        finn = new Spielerprobe("Finn", FINN, MenueBefehle.RECHT);
    }

    private Befehlsprobe baum(Oeffnerprobe oeffner) {
        return new Befehlsprobe(List.of(
                new MenueBefehle(oeffner::oeffne, Sprachprobe.spracheAusDemJar()).menu()));
    }

    @Test
    @DisplayName("1 - ein Spieler bekommt das Hauptmenue und keine Chatzeile")
    void spielerBekommtDasHauptmenue() throws CommandSyntaxException {
        var oeffner = new Oeffnerprobe(true);

        assertEquals(1, baum(oeffner).tippe("menu", finn));

        assertEquals(List.of("Finn -> menue.titel"), oeffner.geoeffnet);
        assertEquals(List.of(), finn.chatBild());
    }

    @Test
    @DisplayName("2 - geht das Fenster nicht auf, liest der Spieler EINE Zeile")
    void ohneFensterLiestDerSpielerEineZeile() throws CommandSyntaxException {
        // Ein unbekannter Gegenstand in der Tafel ist ein Baufehler. Der Spieler soll
        // einen Satz lesen und nicht in ein leeres Fenster sehen - der Grund steht im
        // Serverprotokoll.
        var oeffner = new Oeffnerprobe(false);

        assertEquals(0, baum(oeffner).tippe("menu", finn));

        assertEquals(List.of(GESTOERT), finn.chatBild());
    }

    @Test
    @DisplayName("3 - die Konsole bekommt ihre eigene Absage")
    void konsoleBekommtIhreAbsage() throws CommandSyntaxException {
        // Ein Menue ist ein Inventar, und die Konsole hat keines. Eine eigene Zeile und
        // nicht Brigadiers englische: Auch ein Spieler kann auf der Konsole tippen lassen.
        var oeffner = new Oeffnerprobe(true);
        var baum = baum(oeffner);

        assertEquals(0, baum.tippeAufDerKonsole("menu"));

        assertEquals(List.of(KEINE_KONSOLE), baum.konsoleBild());
        assertEquals(List.of(), oeffner.geoeffnet);
    }

    @Test
    @DisplayName("4 - ohne das Recht gibt es den Befehl nicht")
    void ohneRechtGibtEsDenBefehlNicht() {
        var ohne = new Spielerprobe("Finn", FINN);
        var baum = baum(new Oeffnerprobe(true));

        assertThrows(CommandSyntaxException.class, () -> baum.tippe("menu", ohne));
    }
}
