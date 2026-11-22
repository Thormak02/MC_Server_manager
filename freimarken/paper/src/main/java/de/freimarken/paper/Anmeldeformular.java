package de.freimarken.paper;

import de.freimarken.kern.Anmeldung;
import java.util.ArrayList;
import java.util.List;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;

/**
 * Das Anmeldeformular - EIN Fenster, zwei Anlaesse (Plan 10.1).
 *
 * <p><b>Zwei Wege zeigen es</b>, und darum steht es hier und nicht zweimal: die
 * {@link Anmeldewache} beim Beitritt und {@code /regeln}, solange der Spieler noch nicht
 * angenommen hat. Zwei Fassungen desselben Fensters wichen eines Tages ab - und dann
 * stuende im einen eine Frage, die im anderen fehlt.
 *
 * <p><b>Der Text ist der REGELTEXT</b> ({@link RegelnSatz#TEXT}) und keine zweite
 * Abschrift. Plan 10.1 verlangt die Annahme der Regeln; ein eigener Text hier waere
 * derselbe Text an zwei Orten, und der Betreiber aenderte einen. Darunter steht die
 * FRAGE ({@link AnmeldeSatz#FRAGE}) - sie ist der ganze Unterschied zwischen Lesen und
 * Annehmen.
 *
 * <p><b>Der Chatweg ist nicht der Rueckfall, sondern die zweite Form.</b> Wer kein Fenster
 * bekommt, liest dieselben Zeilen und dazu {@link AnmeldeSatz#HINWEIS} - die Zeile, die
 * {@code /regeln annehmen} nennt. Ohne sie stuende ein Spieler mit altem Client vor einer
 * Zustimmungspflicht, die er nicht erfuellen kann: {@code showDialog} ist ein Senden und
 * kein Fragen, ein verworfenes Paket merkt der Server nicht (gemessen, siehe
 * {@link Formularhand}).
 *
 * <p><b>Kein Zustand, keine Entscheidung.</b> Ob jemand annehmen MUSS, beantwortet
 * {@link Anmeldung#verlangt} - die eine Stelle, die alle drei Wege fragen. Diese Klasse
 * zeigt nur.
 */
final class Anmeldeformular {

    /**
     * Was ein Formular mit ZWEI Knoepfen zeigt - die Naht, siehe {@link Formularhand}.
     *
     * <p>FERTIGE Components und keine Saetze, aus demselben Grund wie bei
     * {@link RegelnBefehle.Formularzeiger}: Das Holen bleibt dort, wo jede Probe es sieht.
     * Der {@code befehl} steht OHNE Schraegstrich da und wird vom ersten Knopf getippt.
     */
    interface Fragezeiger {
        boolean frage(Player wer, Component titel, List<Component> text,
                Component jaKnopf, Component neinKnopf, String befehl);
    }

    /**
     * Der Befehl, den der Annehmen-Knopf tippt - OHNE Schraegstrich.
     *
     * <p>Hier und nicht in der Hand: Welcher Befehl annimmt, ist eine Entscheidung dieser
     * Klasse, und sie muss zu dem Zweig passen, den {@link RegelnBefehle} baut. Dass
     * beide dasselbe meinen, haelt eine Tafel fest - zwei getippte Zeichenketten liefen
     * sonst auseinander, und der Knopf endete in Brigadiers englischer Meldung.
     */
    static final String BEFEHL_ANNEHMEN = RegelnBefehle.ARGUMENT + " "
            + RegelnBefehle.ANNEHMEN;

    private final Fragezeiger zeiger;
    private final Sprache sprache;

    Anmeldeformular(Fragezeiger zeiger, Sprache sprache) {
        if (zeiger == null || sprache == null) {
            throw new IllegalArgumentException("Das Anmeldeformular braucht einen Zeiger"
                    + " und Sprache");
        }
        this.zeiger = zeiger;
        this.sprache = sprache;
    }

    /**
     * Zeigt das Formular - als Fenster, sonst im Chat.
     *
     * <p>Gefragt wird nie, OB der Spieler annehmen muss; das hat der Aufrufer schon
     * entschieden. Hier wird gezeigt.
     *
     * @return ob es als Fenster kam. {@code false} heisst: Die Zeilen sind im Chat
     */
    boolean zeige(Player wer) {
        if (wer == null) {
            throw new IllegalArgumentException("Ohne Spieler gibt es kein Formular");
        }
        List<Component> text = new ArrayList<>(
                sprache.bau(RegelnSatz.TEXT, 1L, Einsatz.keine()));
        text.addAll(sprache.bau(AnmeldeSatz.FRAGE, 1L, Einsatz.keine()));
        if (zeiger.frage(wer,
                sprache.eineZeile(AnmeldeSatz.KOPF, Einsatz.keine()),
                text,
                sprache.eineZeile(AnmeldeSatz.ANNEHMEN, Einsatz.keine()),
                sprache.eineZeile(AnmeldeSatz.SPAETER, Einsatz.keine()),
                BEFEHL_ANNEHMEN)) {
            return true;
        }
        sende(wer, zeilen());
        return false;
    }

    /**
     * Dieselben Zeilen fuer den Chat - mit dem HINWEIS, wie man ohne Fenster annimmt.
     *
     * <p>Jede Zeile ueber {@code bau} und nicht ueber {@code eineZeile}: Der Regeltext IST
     * mehrzeilig, und die Frage darf es werden, ohne dass der Beitritt abbricht. Der KOPF
     * geht ebenfalls ueber {@code bau}, obwohl er einzeilig sein MUSS - der Riegel dafuer
     * liegt beim Laden ({@link Einzeiler#CHATZEILEN}), und ein zweiter Wurf hier waere
     * ein Wurf mitten im Beitritt.
     */
    List<Component> zeilen() {
        List<Component> zeilen = new ArrayList<>();
        zeilen.addAll(sprache.bau(AnmeldeSatz.KOPF, 1L, Einsatz.keine()));
        zeilen.addAll(sprache.bau(RegelnSatz.TEXT, 1L, Einsatz.keine()));
        zeilen.addAll(sprache.bau(AnmeldeSatz.FRAGE, 1L, Einsatz.keine()));
        zeilen.addAll(sprache.bau(AnmeldeSatz.HINWEIS, 1L, Einsatz.keine()));
        return List.copyOf(zeilen);
    }

    /** Die EINE Sendestelle dieser Klasse. */
    private static void sende(Player wer, List<Component> zeilen) {
        zeilen.forEach(wer::sendMessage);
    }
}
