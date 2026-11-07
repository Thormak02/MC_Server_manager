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

/**
 * Die eine Stelle, die entscheidet, ob ein Taktschlag TAETIG war.
 *
 * <p>Die Proben sind nach den Faellen geordnet, die die Regel treffen SOLL - ein Makro am
 * Pflastersteingenerator, eine Angelfarm, ein beschwertes Tastenbrett - und nach denen, die
 * sie nicht treffen darf: ein Mensch, der genau dasselbe tut und sich dabei bewegt.
 */
@DisplayName("Die Messregel")
class MessregelTest {

    /** Die ausgelieferten Werte: zwei von sechs Arten, Bewegen ab acht Bloecken. */
    private static final Messwerte WERTE = Messwerte.VORGABE;

    private static Set<Lebenszeichen> gegeben(Lebenszeichen... arten) {
        return new LinkedHashSet<>(List.of(arten));
    }

    @Test
    @DisplayName("Ein Makro am Generator gibt EIN Zeichen und ist nicht taetig")
    void einZeichenIstNichtTaetig() {
        // Der Fall, gegen den die ganze Messstelle gebaut ist: Wer die linke Taste an
        // einem Pflastersteingenerator festklemmt, gibt abbauen - und sonst nichts.
        assertFalse(Messregel.taetig(gegeben(Lebenszeichen.ABBAUEN), WERTE));
        // Eine Angelfarm ebenso; sie haelt die rechte Taste.
        assertFalse(Messregel.taetig(gegeben(Lebenszeichen.BENUTZEN), WERTE));
        // Ein beschwertes Tastenbrett im Startbildschirm gibt gar nichts.
        assertFalse(Messregel.taetig(gegeben(), WERTE));
    }

    @Test
    @DisplayName("Ein Mensch am selben Generator bewegt sich dabei und IST taetig")
    void zweiZeichenSindTaetig() {
        // Derselbe Block, dieselbe Taste - aber er geht hin und her oder raeumt ein.
        assertTrue(Messregel.taetig(
                gegeben(Lebenszeichen.ABBAUEN, Lebenszeichen.BEWEGEN), WERTE));
        assertTrue(Messregel.taetig(
                gegeben(Lebenszeichen.ABBAUEN, Lebenszeichen.OEFFNEN), WERTE));
    }

    @Test
    @DisplayName("Gezaehlt wird VIELFALT und nicht Menge - dieselbe Art zweimal ist eine")
    void vielfaltUndNichtMenge() {
        // Die tragende Entscheidung. Eine Menge liesse sich hochdrehen; dass hier ein
        // Set hereinkommt, ist genau der Grund. Diese Probe haelt die Zusage fest, auch
        // wenn jemand die Schnittstelle spaeter auf eine Liste umstellt.
        Set<Lebenszeichen> einmal = gegeben(Lebenszeichen.ABBAUEN);
        einmal.add(Lebenszeichen.ABBAUEN);
        einmal.add(Lebenszeichen.ABBAUEN);

        assertEquals(1, Messregel.zaehlende(einmal, WERTE));
        assertFalse(Messregel.taetig(einmal, WERTE));
    }

    @Test
    @DisplayName("Eine Art, die der Betreiber herausgenommen hat, zaehlt nicht mit")
    void abgeschalteteArtZaehltNicht() {
        // benutzen steht ab Werk NICHT in der Liste - es ist die halbe Art zum Setzen.
        assertFalse(WERTE.zaehlt(Lebenszeichen.BENUTZEN));
        // Ein Setzmakro gibt setzen UND benutzen aus EINER Taste. Mit der Vorgabe zaehlt
        // davon eines, und es ist nicht taetig. Genau das ist der Zweck der Vorgabe.
        assertEquals(1, Messregel.zaehlende(
                gegeben(Lebenszeichen.SETZEN, Lebenszeichen.BENUTZEN), WERTE));
        assertFalse(Messregel.taetig(
                gegeben(Lebenszeichen.SETZEN, Lebenszeichen.BENUTZEN), WERTE));
    }

    @Test
    @DisplayName("Wer benutzen dazunimmt, bekommt die mildere Messung - und dieses Paar")
    void mitBenutzenIstEsMilder() {
        // Die Gegenprobe zur vorigen: Dass das Paar ueberhaupt entstehen KANN, haengt
        // allein an der Liste. Ohne diese Probe stuende die Begruendung der Vorgabe nur
        // im Kommentar.
        Messwerte mitBenutzen = Messwerte.VORGABE.mitArten(
                new LinkedHashSet<>(List.of(Lebenszeichen.SETZEN, Lebenszeichen.BENUTZEN,
                        Lebenszeichen.BEWEGEN)), 2);

        assertTrue(Messregel.taetig(
                gegeben(Lebenszeichen.SETZEN, Lebenszeichen.BENUTZEN), mitBenutzen));
    }

    @Test
    @DisplayName("Mit verlangte-arten 1 genuegt EIN Zeichen - die milde Fassung")
    void eineArtGenuegt() {
        // Der Hebel anstelle eines Ausschalters: Wem die Messung zu streng ist, setzt
        // die Zahl auf 1. Dann heisst taetig noch "hat ueberhaupt etwas getan".
        Messwerte milde = WERTE.mitVerlangtenArten(1);

        assertTrue(Messregel.taetig(gegeben(Lebenszeichen.ABBAUEN), milde));
        // Und auch dann ist NICHTS nicht taetig - es gibt keinen Weg zu "immer taetig".
        assertFalse(Messregel.taetig(gegeben(), milde));
    }

    @Test
    @DisplayName("Eine fehlende Menge gilt als leer und wirft nicht")
    void nullIstLeer() {
        // Der Aufrufer ist eine Karte, aus der ein Spieler fehlen kann, der in diesem
        // Schlag nichts getan hat. Ein Wurf dort waere ein Abbruch mitten im Takt - und
        // damit die Spielzeit aller anwesenden Spieler dieses Schlages.
        assertFalse(Messregel.taetig(null, WERTE));
        assertEquals(0, Messregel.zaehlende(null, WERTE));
    }

    @Test
    @DisplayName("Ohne Werte gibt es kein Urteil - das ist ein Programmfehler")
    void ohneWerte() {
        assertThrows(IllegalArgumentException.class,
                () -> Messregel.taetig(gegeben(Lebenszeichen.ABBAUEN), null));
        assertThrows(IllegalArgumentException.class,
                () -> Messregel.zaehlende(gegeben(Lebenszeichen.ABBAUEN), null));
        assertThrows(IllegalArgumentException.class,
                () -> Messregel.kamWeitGenug(false, 100.0, null));
    }

    @Test
    @DisplayName("Die Strecke zaehlt ab der Mindeststrecke - darunter nicht")
    void streckeAbDerGrenze() {
        assertFalse(Messregel.kamWeitGenug(false, 0.0, WERTE));
        // Rueckstoss und Rutschen auf Eis tragen ein paar Bloecke weit; acht nicht.
        assertFalse(Messregel.kamWeitGenug(false, 7.999, WERTE));
        // Genau die Grenze zaehlt - "ab" und nicht "ueber".
        assertTrue(Messregel.kamWeitGenug(false, 8.0, WERTE));
        assertTrue(Messregel.kamWeitGenug(false, 40.0, WERTE));
    }

    @Test
    @DisplayName("Ein Weltwechsel zaehlt immer - auch bei Abstand 0")
    void weltwechselZaehltImmer() {
        // Gleiche Koordinaten im Nether und in Altfurt geben den Abstand 0, und der waere
        // falsch in der unsicheren Richtung: Wer die Welt wechselt, lehnt nicht an einer
        // Wand. Darum fragt die Regel ZUERST nach der Welt.
        assertTrue(Messregel.kamWeitGenug(true, 0.0, WERTE));
    }

    @Test
    @DisplayName("Verlangte Arten passen nur bis zur Zahl der vorhandenen")
    void artenzahlPasstZurListe() {
        assertTrue(Messregel.brauchbareArtenzahl(1, 1));
        assertTrue(Messregel.brauchbareArtenzahl(2, 6));
        assertTrue(Messregel.brauchbareArtenzahl(6, 6));
        // Mehr verlangt als vorhanden: Damit waere kein Schlag jemals taetig, und zwar
        // STUMM - eine Null sieht in jeder Anzeige aus wie ein leerer Server.
        assertFalse(Messregel.brauchbareArtenzahl(3, 2));
        assertFalse(Messregel.brauchbareArtenzahl(7, 6));
        // Und eine 0 oder weniger gaebe "immer taetig" - das waere keine Messung.
        assertFalse(Messregel.brauchbareArtenzahl(0, 6));
        assertFalse(Messregel.brauchbareArtenzahl(-1, 6));
    }
}
