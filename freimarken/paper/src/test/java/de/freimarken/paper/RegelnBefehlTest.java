package de.freimarken.paper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
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

    /** Die Tipperin aus Probe 1b - fuer /execute as braucht es zwei verschiedene. */
    private static final UUID THEA = Spielerprobe.kennungAus(215);

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

    /**
     * Die Mitschrift am Fenster mit ZWEI Knoepfen [21.11.2026].
     *
     * <p>Eine eigene Attrappe und nicht dieselbe: Die Naht hat eine andere Gestalt (zwei
     * Knoepfe und einen Befehl), und genau der BEFEHL und die REIHENFOLGE der Knoepfe
     * sind das, was hier geprueft werden muss. Sie schreibt VOR dem Zurueckgeben mit -
     * eine Attrappe, die erst danach mitschreibt, sieht einen werfenden Aufruf nie.
     */
    private static final class Frageprobe implements Anmeldeformular.Fragezeiger {

        private final boolean geht;
        private final List<Player> empfaenger = new ArrayList<>();
        private final List<Component> titel = new ArrayList<>();
        private final List<Component> knoepfe = new ArrayList<>();
        private final List<String> befehle = new ArrayList<>();
        private List<Component> text = List.of();

        Frageprobe(boolean geht) {
            this.geht = geht;
        }

        @Override
        public boolean frage(Player wer, Component titel, List<Component> text,
                Component jaKnopf, Component neinKnopf, String befehl) {
            this.empfaenger.add(wer);
            this.titel.add(titel);
            this.text = List.copyOf(text);
            // IN DIESER REIHENFOLGE: erst ja, dann nein. Ein vertauschtes Paar waere ein
            // Fenster, das auf "Spaeter" zustimmt, und eine Mitschrift als MENGE saehe
            // es nicht.
            this.knoepfe.add(jaKnopf);
            this.knoepfe.add(neinKnopf);
            this.befehle.add(befehl);
            return geht;
        }

        List<String> textBild() {
            return text.stream().map(Sprachprobe::bild).toList();
        }

        List<String> knopfBild() {
            return knoepfe.stream().map(Sprachprobe::bild).toList();
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
        // EMIL HAT ANGENOMMEN. Ohne das zeigte /regeln ihm das Anmeldeformular, und
        // jede der Leseproben waere rot aus dem falschen Grund. Die Proben der ANNAHME
        // stellen den anderen Zustand her - das ist ihr Gegenstand.
        return baum(fenster, new Frageprobe(true), angenommen(EMIL));
    }

    private Befehlsprobe baum(Fensterprobe fenster, Frageprobe frage,
            Anmeldung anmeldung) {
        Sprache sprache = Sprachprobe.spracheAusDemJar();
        return new Befehlsprobe(List.of(
                new RegelnBefehle(fenster::zeige, sprache, anmeldung,
                        new Anmeldeformular(frage::frage, sprache)).regeln()));
    }

    /**
     * Die Anmeldung ist AN, und die genannten Spieler haben die geltende Fassung
     * angenommen.
     *
     * <p>Mit Spielern und nicht pauschal "aus": Die Lage auf einem Server ist "Anmeldung
     * an, dieser Spieler hat zugestimmt". Eine ausgeschaltete Anmeldung pruefte eine
     * Lage, die ab Werk nicht vorkommt - und jede Probe hiesse dann stillschweigend "so
     * ist es ohne Anmeldung".
     */
    private static Anmeldung angenommen(UUID... wer) {
        Anmeldung anmeldung = offen();
        for (UUID einer : wer) {
            anmeldung.nimmAn(einer);
        }
        return anmeldung;
    }

    /** Die Anmeldung ist AN, und niemand hat angenommen - die Lage eines Neulings. */
    private static Anmeldung offen() {
        return new Anmeldung(new SpeicherAnmeldungen(),
                new Wirtschaftsstand(Wirtschaftswerte.VORGABE),
                Clock.fixed(Instant.parse("2026-11-21T12:00:00Z"), ZoneOffset.UTC));
    }

    /** Die Anmeldung ist AUS - dann verlangt sie von niemandem etwas. */
    private static Anmeldung aus() {
        return new Anmeldung(new SpeicherAnmeldungen(),
                new Wirtschaftsstand(Wirtschaftswerte.VORGABE.mitAnmeldung(
                        new Anmeldewerte(false, Anmeldewerte.VORGABE_FASSUNG))),
                Clock.fixed(Instant.parse("2026-11-21T12:00:00Z"), ZoneOffset.UTC));
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
        Spielerprobe tipperin = new Spielerprobe("Thea", THEA, RegelnBefehle.RECHT);
        var fenster = new Fensterprobe(true);
        // BEIDE haben angenommen: Sonst bekaeme Thea das Anmeldeformular statt des
        // Lesefensters, und diese Probe waere rot aus dem falschen Grund. Gemessen am
        // 21.11.2026 - genau so ist sie es gewesen.
        var baum = baum(fenster, new Frageprobe(true), angenommen(EMIL, THEA));

        baum.tippeAls("regeln", tipperin, emil);

        assertEquals(List.of(tipperin.spieler()), fenster.empfaenger,
                "das Fenster ging an den Ausgefuehrten und nicht an den Tippenden");

        // UND DER RUECKFALL an denselben: Sonst lase je nach Lage ein anderer.
        var zu = new Fensterprobe(false);
        tipperin.vergessen();
        emil.vergessen();

        baum(zu, new Frageprobe(true), angenommen(EMIL, THEA))
                .tippeAls("regeln", tipperin, emil);

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
                new RegelnBefehle(new Fensterprobe(false)::zeige, sprache,
                        angenommen(EMIL),
                        new Anmeldeformular(new Frageprobe(false)::frage, sprache))
                        .regeln()));

        baum.tippe("regeln", emil);

        assertEquals(IM_CHAT.subList(0, IM_CHAT.size() - 1), emil.chatBild());
    }

    // --- Die ANNAHME [Schritt 13 Teil C, 21.11.2026] ---------------------------

    @Test
    @DisplayName("8 - wer nicht angenommen hat, bekommt das Formular mit ZWEI Knoepfen")
    void ohneAnnahmeKommtDasFormular() throws CommandSyntaxException {
        var fenster = new Fensterprobe(true);
        var frage = new Frageprobe(true);

        baum(fenster, frage, offen()).tippe("regeln", emil);

        // DAS LESEFENSTER DARF NICHT KOMMEN: Es hat einen Knopf ohne Tat, und damit
        // koennte er nicht zustimmen - er sieht die Regeln und hat keinen Weg.
        assertEquals(List.of(), fenster.titel.stream().map(Sprachprobe::bild).toList(),
                "das Lesefenster darf nicht kommen, solange er annehmen muss");
        assertEquals(List.of("&#c6a15bAufnahme in den Freimarkenbund"),
                frage.titel.stream().map(Sprachprobe::bild).toList());
        // DER TEXT IST DER REGELTEXT PLUS DIE FRAGE - fuenf Zeilen und eine. Ein eigener
        // Text waere derselbe an zwei Orten.
        assertEquals(6, frage.text.size(),
                "fuenf Regelzeilen und die Frage darunter");
        assertEquals(IM_FENSTER, frage.textBild().subList(0, 5));
        assertEquals("&#e3c57eNimmst du diese Regeln an? Mit der Annahme wirst du"
                + " Buerger des Bundes.", frage.textBild().get(5));
        assertEquals(List.of(), emil.chatBild(), "wer das Fenster hat, braucht den"
                + " Chat nicht");
    }

    @Test
    @DisplayName("9 - die Knoepfe stehen in der Reihenfolge ja, nein - und ja tippt den"
            + " Befehl")
    void knoepfeInDerRichtigenReihenfolge() throws CommandSyntaxException {
        // DIE WICHTIGSTE PROBE DIESES TEILS. Vertauscht man die zwei Argumente von
        // DialogType.confirmation, stimmt der Knopf "Spaeter" zu und "Ich nehme an"
        // schliesst nur - ein Fenster, das das Gegenteil tut, und keine Zahl sieht es.
        var frage = new Frageprobe(true);

        baum(new Fensterprobe(true), frage, offen()).tippe("regeln", emil);

        assertEquals(List.of("&#73bf96Ich nehme an", "&#6e7a82Spaeter"),
                frage.knopfBild());
        // UND DER BEFEHL, den der erste Knopf tippt - OHNE Schraegstrich, wie
        // performCommand ihn nimmt. Eine zweite getippte Zeichenkette liefe auseinander,
        // darum baut das Formular ihn aus den Konstanten des Befehls.
        assertEquals(List.of("regeln annehmen"), frage.befehle);
    }

    @Test
    @DisplayName("10 - ohne Fenster kommen die Regeln und der HINWEIS in den Chat")
    void ohneFensterKommtDerHinweis() throws CommandSyntaxException {
        // DIE BRUECKE FUER EINEN ALTEN CLIENT. Ohne die letzte Zeile stuende er vor
        // einer Zustimmungspflicht, die er nicht erfuellen kann - showDialog ist ein
        // Senden und kein Fragen.
        baum(new Fensterprobe(false), new Frageprobe(false), offen())
                .tippe("regeln", emil);

        List<String> gelesen = emil.chatBild();
        assertEquals("&#c6a15bAufnahme in den Freimarkenbund", gelesen.get(0));
        assertEquals("&#e3c57eTippe /regeln annehmen, um die Regeln anzunehmen.",
                gelesen.get(gelesen.size() - 1));
        assertEquals(8, gelesen.size(), gelesen.toString());
    }

    @Test
    @DisplayName("11 - /regeln annehmen traegt die Annahme ein und begruesst")
    void annehmenTraegtEin() throws CommandSyntaxException {
        Anmeldung anmeldung = offen();
        var baum = baum(new Fensterprobe(true), new Frageprobe(true), anmeldung);

        baum.tippe("regeln annehmen", emil);

        assertFalse(anmeldung.verlangt(EMIL), "danach verlangt sie nichts mehr");
        assertEquals(List.of(
                        "&#73bf96Angenommen - willkommen im Freimarkenbund!",
                        "&#a5b4bfMit /parzelle nehmen bekommst du deine erste Parzelle,"
                                + " und sie ist gratis."),
                emil.chatBild());
    }

    @Test
    @DisplayName("12 - ein zweites Annehmen sagt es und begruesst NICHT erneut")
    void zweitesAnnehmenSagtEs() throws CommandSyntaxException {
        Anmeldung anmeldung = offen();
        var baum = baum(new Fensterprobe(true), new Frageprobe(true), anmeldung);
        baum.tippe("regeln annehmen", emil);
        emil.vergessen();

        baum.tippe("regeln annehmen", emil);

        // Ein eigener Satz und nicht derselbe: Eine zweite Begruessung sieht aus, als
        // haette die erste nicht gewirkt.
        assertEquals(List.of("&#a5b4bfDu hast die Regeln schon angenommen."),
                emil.chatBild());
    }

    @Test
    @DisplayName("13 - ist die Anmeldung AUS, sagt /regeln annehmen es - und wirft nicht")
    void ausSagtEs() throws CommandSyntaxException {
        // Ohne diesen Zweig wuerfe die Kernregel, und der Spieler lase eine rote
        // englische Zeile fuer eine Lage, die der Betreiber eingestellt hat.
        var baum = baum(new Fensterprobe(true), new Frageprobe(true), aus());

        baum.tippe("regeln annehmen", emil);

        assertEquals(List.of("&#a5b4bfAuf diesem Server ist keine Anmeldung noetig."),
                emil.chatBild());
    }

    @Test
    @DisplayName("14 - die Konsole kann nicht annehmen, und der Zweig ist ihr sichtbar")
    void konsoleKannNichtAnnehmen() throws CommandSyntaxException {
        // GELESEN und nicht verborgen: Ein requires auf "ist ein Spieler" liesse den
        // Zweig fuer die Konsole verschwinden, und ein Betreiber suchte den Fehler bei
        // sich. Die Probe haelt beides - dass er erreichbar ist UND was er antwortet.
        var baum = baum(new Fensterprobe(true), new Frageprobe(true), offen());

        baum.tippeAufDerKonsole("regeln annehmen");

        assertEquals(List.of("&#a5b4bfAnnehmen kann nur ein Spieler - die Konsole"
                + " stimmt fuer niemanden zu."), baum.konsoleBild());
    }

    @Test
    @DisplayName("15 - /regeln chat zeigt dem Unangemeldeten dieselben Zeilen")
    void chatzweigZeigtDasFormular() throws CommandSyntaxException {
        // Der Chatzweig ist nicht der Rueckfall, sondern die zweite Form - und er muss
        // dem Unangemeldeten denselben Weg nennen. Ohne diese Probe koennte /regeln chat
        // die Regeln OHNE den Hinweis zeigen, und wer ihn braucht, faende ihn nicht.
        baum(new Fensterprobe(true), new Frageprobe(true), offen())
                .tippe("regeln chat", emil);

        List<String> gelesen = emil.chatBild();
        assertEquals("&#e3c57eTippe /regeln annehmen, um die Regeln anzunehmen.",
                gelesen.get(gelesen.size() - 1));
    }
}
