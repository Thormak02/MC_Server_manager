package de.freimarken.paper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * {@code /regeln} GETIPPT - beide Formen und der Rueckfall.
 *
 * <p><b>Das FENSTER selbst laeuft in keiner Probe</b>, und das ist gemessen: Jeder
 * statische Einstieg der Dialog-API geht durch einen {@code ServiceLoader}, der auf dem
 * Probenklassenpfad keine Umsetzung findet. Darum steht hinter dem Befehl eine Naht
 * ({@code Formularzeiger}), und diese Probe haengt eine Mitschrift daran: Sie sieht, WAS
 * ins Fenster gegangen waere - Titel, jede Zeile, der Knopf.
 *
 * <p><b>Der Rueckfall ist der Kern dieser Datei.</b> Gibt die Naht {@code false} zurueck
 * (ein alter Client ueber ViaProxy kennt keine Fenster), muss derselbe Text im Chat
 * ankommen. Ohne diese Probe waere der haeufigste Fall des Befehls der ungepruefte.
 */
@DisplayName("/regeln getippt")
class RegelnBefehlTest {

    private static final UUID EMIL = Spielerprobe.kennungAus(214);

    /** Die Regeln im Chat: Kopf, die Zeilen des Textes, der Fuss. */
    private static final List<String> IM_CHAT = List.of(
                "&#c6a15bDie Regeln des Freimarkenbundes",
                        "&#a5b4bf- Baue nur auf eigenem Grund. Eine Parzelle gehoert dem, der "
                                + "sie genommen hat.",
                        "&#a5b4bf- Nimm nichts, was dir nicht gehoert. Diebstahl aus Kisten und "
                                + "Laeden wird geahndet.",
                        "&#a5b4bf- Kein Angriff auf andere Spieler in den geschuetzten Welten.",
                        "&#a5b4bf- Keine Hilfsmittel, die dir Vorteile verschaffen - kein "
                                + "Durchblick durch Stein, kein Fliegen, keine Makros.",
                        "&#a5b4bf- Sei anstaendig im Chat. Beleidigung und fremde Werbung haben "
                                + "hier keinen Platz.",
                        "&#6e7a82Wer dagegen handelt, verliert Rang, Grund oder den Zugang.");

    /** Was im Fenster steht - nur der Text, ohne Kopf und ohne Fuss. */
    private static final List<String> IM_FENSTER = List.of(
                "&#a5b4bf- Baue nur auf eigenem Grund. Eine Parzelle gehoert dem, der sie "
                                + "genommen hat.",
                        "&#a5b4bf- Nimm nichts, was dir nicht gehoert. Diebstahl aus Kisten und "
                                + "Laeden wird geahndet.",
                        "&#a5b4bf- Kein Angriff auf andere Spieler in den geschuetzten Welten.",
                        "&#a5b4bf- Keine Hilfsmittel, die dir Vorteile verschaffen - kein "
                                + "Durchblick durch Stein, kein Fliegen, keine Makros.",
                        "&#a5b4bf- Sei anstaendig im Chat. Beleidigung und fremde Werbung haben "
                                + "hier keinen Platz.");

    /**
     * Die Mitschrift an der Naht.
     *
     * <p>Schreibt VOR dem Zurueckgeben mit: Eine Attrappe, die erst danach mitschreibt,
     * sieht einen werfenden Aufruf nie.
     */
    private static final class Fensterprobe implements RegelnBefehle.Formularzeiger {

        private final boolean geht;
        private final List<Component> titel = new ArrayList<>();
        private final List<Component> knopf = new ArrayList<>();
        /** WEM das Fenster gezeigt wurde - bei /execute as sind es zwei Kandidaten. */
        private final List<Player> empfaenger = new ArrayList<>();
        private List<Component> text = List.of();

        Fensterprobe(boolean geht) {
            this.geht = geht;
        }

        @Override
        public boolean zeige(Player wer, Component titel, List<Component> text,
                Component knopf) {
            this.empfaenger.add(wer);
            this.titel.add(titel);
            this.text = List.copyOf(text);
            this.knopf.add(knopf);
            return geht;
        }

        List<String> textBild() {
            return text.stream().map(Sprachprobe::bild).toList();
        }
    }

    private Spielerprobe emil;

    @BeforeAll
    static void serverStellen() {
        Serverprobe.einmalSetzen();
    }

    @BeforeEach
    void vorher() {
        emil = new Spielerprobe("Emil", EMIL, RegelnBefehle.RECHT);
    }

    private Befehlsprobe baum(Fensterprobe fenster) {
        return new Befehlsprobe(List.of(
                new RegelnBefehle(fenster::zeige, Sprachprobe.spracheAusDemJar())
                        .regeln()));
    }

    @Test
    @DisplayName("1 - ein Spieler bekommt das FENSTER und keine Chatzeile")
    void spielerBekommtDasFenster() throws CommandSyntaxException {
        var fenster = new Fensterprobe(true);

        assertEquals(1, baum(fenster).tippe("regeln", emil));

        assertEquals(IM_FENSTER, fenster.textBild());
        assertEquals(List.of("&#c6a15bDie Regeln des Freimarkenbundes"),
                fenster.titel.stream().map(Sprachprobe::bild).toList());
        assertEquals(List.of("&#edf1f3Gelesen"),
                fenster.knopf.stream().map(Sprachprobe::bild).toList());
        // KEINE Chatzeile daneben: Wer das Fenster bekommt, soll den Text nicht zweimal
        // haben.
        assertEquals(List.of(), emil.chatBild());
    }

    @Test
    @DisplayName("1b - bei /execute as liest DER, der getippt hat")
    void beiExecuteAsLiestDerTipper() throws CommandSyntaxException {
        // ZWEI WEGE, EIN LESER [21.11.2026]. Hier stand das Fenster am EXECUTOR und der
        // Rueckfall am SENDER: Bei /execute as Emil run regeln bekam Emil das Fenster
        // und der Tippende nichts - ging es aber nicht auf, bekam der Tippende die
        // Zeilen und Emil nichts. Je nachdem, ob ein Fenster aufgeht, lase ein anderer.
        //
        // Es gilt die Hausregel des Projekts (sie steht beim Blockbuch): Lesen will es
        // der, der getippt hat.
        // KEIN Serverprobe.anmelden: /regeln loest keinen Namen auf, und die Tabelle
        // der Anwesenden ist STATISCH - ein Eintrag hier machte WolkeBefehlTest rot,
        // weil jene Datei ihre eigene Anna hat und den getippten Namen ueber dieselbe
        // Tabelle aufloest. Gemessen am 21.11.2026; Tafel 31dh haelt die Regel.
        Spielerprobe tipperin = new Spielerprobe("Thea", Spielerprobe.kennungAus(215),
                RegelnBefehle.RECHT);
        var fenster = new Fensterprobe(true);

        baum(fenster).tippeAls("regeln", tipperin, emil);

        assertEquals(List.of(tipperin.spieler()), fenster.empfaenger,
                "das Fenster ging an den Ausgefuehrten und nicht an den Tippenden");

        // UND DER RUECKFALL an denselben: Sonst lase je nach Lage ein anderer.
        var zu = new Fensterprobe(false);
        tipperin.vergessen();
        emil.vergessen();

        baum(zu).tippeAls("regeln", tipperin, emil);

        assertEquals(IM_CHAT, tipperin.chatBild());
        assertEquals(List.of(), emil.chatBild());
    }

    @Test
    @DisplayName("2 - der Text im Fenster ist MEHRZEILIG, eine Zeile je Umbruchmarke")
    void derTextImFensterIstMehrzeilig() throws CommandSyntaxException {
        // DER ERSTE MEHRZEILIGE SATZ DES PROJEKTS, und das wird hier gemessen und nicht
        // angenommen: In der Sprachdatei steht EIN Wert, im Fenster werden daraus
        // einzelne Bloecke. Ginge die Umbruchmarke verloren, waere es ein Block mit einer
        // sehr langen Zeile - und keine Bildtafel wuerde rot, weil das Bild dasselbe ist.
        var fenster = new Fensterprobe(true);

        baum(fenster).tippe("regeln", emil);

        assertEquals(5, fenster.text.size());
    }

    @Test
    @DisplayName("3 - geht das Fenster nicht auf, kommen die Zeilen in den Chat")
    void ohneFensterKommenDieZeilenInDenChat() throws CommandSyntaxException {
        // DER FALL EINES ALTEN CLIENTS ueber ViaProxy. Niemand soll ohne Regeln
        // dastehen, weil sein Client keine Fenster kennt.
        var fenster = new Fensterprobe(false);

        assertEquals(1, baum(fenster).tippe("regeln", emil));

        assertEquals(IM_CHAT, emil.chatBild());
    }

    @Test
    @DisplayName("4 - /regeln chat fragt das Fenster gar nicht erst")
    void regelnChatFragtDasFensterNicht() throws CommandSyntaxException {
        var fenster = new Fensterprobe(true);

        assertEquals(1, baum(fenster).tippe("regeln chat", emil));

        assertEquals(IM_CHAT, emil.chatBild());
        assertEquals(List.of(), fenster.titel);
    }

    @Test
    @DisplayName("5 - die Konsole bekommt die Zeilen im Chat")
    void konsoleBekommtDieZeilen() throws CommandSyntaxException {
        var fenster = new Fensterprobe(true);
        var baum = baum(fenster);

        assertEquals(1, baum.tippeAufDerKonsole("regeln"));

        assertEquals(IM_CHAT, baum.konsoleBild());
        assertEquals(List.of(), fenster.titel);
    }

    @Test
    @DisplayName("6 - ohne das Recht gibt es den Befehl nicht")
    void ohneRechtGibtEsDenBefehlNicht() {
        var ohne = new Spielerprobe("Emil", EMIL);
        var baum = baum(new Fensterprobe(true));

        assertThrows(CommandSyntaxException.class, () -> baum.tippe("regeln", ohne));
    }

    @Test
    @DisplayName("7 - ein leerer Fuss sendet keine leere Zeile")
    void leererFussSendetKeineLeereZeile() throws CommandSyntaxException {
        var sprache = Sprachprobe.mitZeilen(Map.of(RegelnSatz.FUSS.pfad(), ""));
        var baum = new Befehlsprobe(List.of(
                new RegelnBefehle(new Fensterprobe(false)::zeige, sprache).regeln()));

        baum.tippe("regeln", emil);

        assertEquals(IM_CHAT.subList(0, IM_CHAT.size() - 1), emil.chatBild());
    }
}
