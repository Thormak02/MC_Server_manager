package de.freimarken.paper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import java.util.List;
import java.util.logging.Logger;
import org.bukkit.Location;
import org.bukkit.World;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * {@code /zurueck} GETIPPT - der kuerzeste Befehl des Projekts, mit zwei Lagen.
 *
 * <p>Eine Sendestelle, ein Satz, und trotzdem drei Dinge, die eine Tafel nicht sieht: das
 * doppelte {@code requires} (Spieler UND {@code freimarken.zurueck}), die Weiche zwischen
 * "kein Ort gemerkt" und der Reise, und dass der gemerkte Ort ueberhaupt gelesen wird.
 *
 * <p><b>Die Reise bleibt aussen vor, und hier faellt die Grenze mit dem Zweig zusammen:</b>
 * Mit einem gemerkten Ort geht es sofort in {@code Reise.fuehre(Player, Location, String)},
 * und das fragt als ERSTES die Aufwaermzeit. Die braucht eine {@code JavaPlugin}-Instanz.
 * Geprueft ist darum die Absage - und, als Gegenstueck, dass mit einem gemerkten Ort eben
 * NICHT die Absage kommt. Zwei Lagen, eine davon bis zum Ende, die andere bis zur Schwelle.
 */
class ZurueckBefehlTest {

    private static final String WELT = "minecraft:marktstadt";

    private World welt;
    private Rueckweg rueckweg;
    private Befehlsprobe baum;

    @BeforeAll
    static void serverStellen() {
        Serverprobe.einmalSetzen();
    }

    @BeforeEach
    void vorher() {
        welt = Serverprobe.welt(WELT);
        rueckweg = new Rueckweg();
        Reise reise = new Reise(Logger.getLogger("ZurueckBefehlTest"), rueckweg, null,
                Sprachprobe.spracheAusDemJar());
        RueckwegBefehle befehle = new RueckwegBefehle(rueckweg, reise,
                Sprachprobe.spracheAusDemJar());
        baum = new Befehlsprobe(List.of(befehle.zurueck()));
    }

    private Spielerprobe heimkehrer(int zahl) {
        return new Spielerprobe("Cara", Spielerprobe.kennungAus(zahl),
                RueckwegBefehle.RECHT_ZURUECK).inWelt(welt);
    }

    @Test
    @DisplayName("42 - ohne gemerkten Ort sagt /zurueck es dem Spieler")
    void ohneGemerktenOrtKommtDieAbsage() throws CommandSyntaxException {
        Spielerprobe spieler = heimkehrer(91);

        assertEquals(1, baum.tippe("zurueck", spieler));

        // Aus dem ROTEN Lauf, und gegengelesen gegen die Tafel von BildGleichTest,
        // die denselben Satz ueber den Schluessel holt statt ueber den Befehl.
        assertEquals(List.of("&7Es ist kein Ort gemerkt - du bist noch nirgends"
                + " gewesen, wohin es zurueckgeht."), spieler.chatBild());
    }

    @Test
    @DisplayName("42b - mit gemerktem Ort kommt die Absage NICHT, sondern die Reise")
    void mitGemerktemOrtGehtEsInDieReise() {
        Spielerprobe spieler = heimkehrer(92);
        rueckweg.merken(spieler.kennung(), new Location(welt, 10, 70, 20));

        // Das Gegenstueck zu 42, und es ist die haertere Haelfte: Liest der Zweig den
        // gemerkten Ort NICHT, kaeme hier wieder die Absage - und keine Zahltafel saehe
        // es, weil die eine Sendestelle unveraendert dasteht.
        //
        // Beobachtet wird der Weg an seiner Schwelle: Reise.fuehre fragt als erstes die
        // Aufwaermzeit, und die ist hier mit Absicht nicht da. Der Wurf von dort IST der
        // Beleg, dass der Zweig in die Reise gegangen ist statt abzusagen. Eine echte
        // Aufwaermzeit braeuchte eine JavaPlugin-Instanz; das steht im TODO.
        assertThrows(NullPointerException.class, () -> baum.tippe("zurueck", spieler));
        assertEquals(List.of(), spieler.chatBild(),
                "und zwar OHNE dem Spieler vorher abzusagen");
    }

    @Test
    @DisplayName("42c - ohne freimarken.zurueck gibt es den Befehl nicht")
    void ohneRechtGibtEsDenBefehlNicht() {
        Spielerprobe ohneRecht = new Spielerprobe("Dora", Spielerprobe.kennungAus(93))
                .inWelt(welt);

        assertThrows(CommandSyntaxException.class, () -> baum.tippe("zurueck", ohneRecht));
        assertEquals(List.of(), ohneRecht.chatBild());
    }

    @Test
    @DisplayName("42d - die Konsole hat keinen Ort, an den sie zurueckkehren koennte")
    void dieKonsoleKehrtNichtZurueck() {
        assertThrows(CommandSyntaxException.class,
                () -> baum.tippeAufDerKonsole("zurueck"));
    }
}
