package de.freimarken.paper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.freimarken.kern.Anmeldewerte;
import de.freimarken.kern.Anmeldung;
import de.freimarken.kern.SpeicherAnmeldungen;
import de.freimarken.kern.Wirtschaftsstand;
import de.freimarken.kern.Wirtschaftswerte;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerJoinEvent;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Die Wache, die beim Beitritt das Anmeldeformular zeigt (Plan 10.1).
 *
 * <p>Was hier geprueft wird, kann keine andere Probe sehen: <b>ob das Formular ueberhaupt
 * kommt</b>. Die Kernregel ({@code AnmeldungTest}) sagt, WER annehmen muss; der Befehl
 * ({@code RegelnBefehlTest}) zeigt es auf Tippen. Dass es auch ohne Tippen kommt - beim
 * ersten Beitritt, und genau darum geht es dem Plan -, haengt an dieser Wache.
 *
 * <p><b>Das Formular selbst laeuft nicht:</b> Es geht durch die Dialog-API, und die stirbt
 * auf dem Probenklassenpfad im Klasseninitialisierer (gemessen, siehe
 * {@link Formularhand}). Darum steht hier eine Mitschrift an der Naht - sie sieht, WEM ein
 * Formular gezeigt worden waere.
 */
@DisplayName("Die Anmeldewache")
class AnmeldewacheTest {

    private static final Instant MITTAGS = Instant.parse("2026-11-21T12:00:00Z");
    private static final UUID EMIL = Spielerprobe.kennungAus(301);

    /**
     * Die Mitschrift an der Naht - sie sammelt, wem das Formular gezeigt wurde.
     *
     * <p>Schreibt VOR dem Zurueckgeben mit: Eine Attrappe, die erst danach mitschreibt,
     * sieht einen werfenden Aufruf nie.
     */
    private static final class Frageprobe implements Anmeldeformular.Fragezeiger {

        private final List<Player> empfaenger = new ArrayList<>();

        @Override
        public boolean frage(Player wer, Component titel, List<Component> text,
                Component jaKnopf, Component neinKnopf, String befehl) {
            empfaenger.add(wer);
            return true;
        }
    }

    @BeforeAll
    static void serverStellen() {
        Serverprobe.einmalSetzen();
    }

    private static Anmeldung anmeldung(Anmeldewerte werte, SpeicherAnmeldungen ablage) {
        return new Anmeldung(ablage,
                new Wirtschaftsstand(Wirtschaftswerte.VORGABE.mitAnmeldung(werte)),
                Clock.fixed(MITTAGS, ZoneOffset.UTC));
    }

    private static Anmeldewache wache(Anmeldung anmeldung, Frageprobe frage) {
        return new Anmeldewache(anmeldung,
                new Anmeldeformular(frage::frage, Sprachprobe.spracheAusDemJar()));
    }

    @Test
    @DisplayName("1 - wer noch nicht angenommen hat, bekommt beim Beitritt das Formular")
    void beimBeitrittKommtDasFormular() {
        Spielerprobe emil = new Spielerprobe("Emil", EMIL);
        var frage = new Frageprobe();

        wache(anmeldung(Anmeldewerte.VORGABE, new SpeicherAnmeldungen()), frage)
                .beimBeitritt(new PlayerJoinEvent(emil.spieler(), Component.empty()));

        assertEquals(List.of(emil.spieler()), frage.empfaenger);
    }

    @Test
    @DisplayName("2 - wer angenommen hat, bekommt KEIN Formular")
    void werAngenommenHatBekommtKeines() {
        // Ohne diese Haelfte waere auch eine Wache gruen, die JEDEM bei JEDEM Beitritt
        // das Fenster zeigt - und das waere im Spiel ein Formular bei jedem Einloggen.
        Spielerprobe emil = new Spielerprobe("Emil", EMIL);
        var ablage = new SpeicherAnmeldungen();
        Anmeldung anmeldung = anmeldung(Anmeldewerte.VORGABE, ablage);
        anmeldung.nimmAn(EMIL);
        var frage = new Frageprobe();

        wache(anmeldung, frage)
                .beimBeitritt(new PlayerJoinEvent(emil.spieler(), Component.empty()));

        assertEquals(List.of(), frage.empfaenger);
    }

    @Test
    @DisplayName("3 - nach einer neuen FASSUNG kommt es wieder")
    void neueFassungZeigtEsWieder() {
        // Der Fall, fuer den die Fassungsspalte gebaut ist: Der Betreiber aendert seine
        // Regeln, und jeder wird beim naechsten Beitritt erneut gefragt - auch wer seit
        // Monaten dabei ist.
        Spielerprobe emil = new Spielerprobe("Emil", EMIL);
        var ablage = new SpeicherAnmeldungen();
        anmeldung(Anmeldewerte.VORGABE, ablage).nimmAn(EMIL);
        var frage = new Frageprobe();

        wache(anmeldung(new Anmeldewerte(true, "2026-12-01"), ablage), frage)
                .beimBeitritt(new PlayerJoinEvent(emil.spieler(), Component.empty()));

        assertEquals(List.of(emil.spieler()), frage.empfaenger);
    }

    @Test
    @DisplayName("4 - ist die Anmeldung AUS, kommt nie ein Formular")
    void ausZeigtNie() {
        // Die Wache wird dann ohnehin nicht angemeldet (FreimarkenPlugin, Tafel 31di) -
        // aber sie muss auch dann stillhalten, wenn jemand sie doch ruft. Eine Wache,
        // die den Schalter nur draussen hat, wirkt beim ersten Umbau wieder.
        Spielerprobe emil = new Spielerprobe("Emil", EMIL);
        var frage = new Frageprobe();

        wache(anmeldung(new Anmeldewerte(false, Anmeldewerte.VORGABE_FASSUNG),
                new SpeicherAnmeldungen()), frage)
                .beimBeitritt(new PlayerJoinEvent(emil.spieler(), Component.empty()));

        assertEquals(List.of(), frage.empfaenger);
    }

    @Test
    @DisplayName("5 - ohne Anmeldung oder Formular wird abgewiesen")
    void pflichtenWerdenGeprueft() {
        var formular = new Anmeldeformular(new Frageprobe()::frage,
                Sprachprobe.spracheAusDemJar());
        Anmeldung anmeldung = anmeldung(Anmeldewerte.VORGABE, new SpeicherAnmeldungen());

        assertThrows(IllegalArgumentException.class,
                () -> new Anmeldewache(null, formular));
        assertThrows(IllegalArgumentException.class,
                () -> new Anmeldewache(anmeldung, null));
        assertThrows(IllegalArgumentException.class,
                () -> formular.zeige(null));
    }

    @Test
    @DisplayName("6 - geht das Fenster nicht auf, kommen die Zeilen in den Chat")
    void ohneFensterKommtDerChat() {
        // DIE BRUECKE FUER EINEN ALTEN CLIENT, hier beim BEITRITT - das ist der Fall,
        // in dem er noch gar keinen Befehl getippt hat. Ohne diesen Weg saehe er beim
        // Einloggen nichts und wuesste nicht, warum der Bundesmarkt ihn abweist.
        Spielerprobe emil = new Spielerprobe("Emil", EMIL);
        var formular = new Anmeldeformular(
                (wer, titel, text, ja, nein, befehl) -> false,
                Sprachprobe.spracheAusDemJar());

        new Anmeldewache(anmeldung(Anmeldewerte.VORGABE, new SpeicherAnmeldungen()),
                formular)
                .beimBeitritt(new PlayerJoinEvent(emil.spieler(), Component.empty()));

        List<String> gelesen = emil.chatBild();
        assertEquals("&#c6a15bAufnahme in den Freimarkenbund", gelesen.get(0));
        assertTrue(gelesen.get(gelesen.size() - 1).contains("/regeln annehmen"),
                "die letzte Zeile muss den Weg nennen: " + gelesen);
    }
}
