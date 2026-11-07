package de.freimarken.kern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Die Zahlen der Messstelle und ihre Grenzen. */
@DisplayName("Die Messwerte")
class MesswerteTest {

    private static Set<Lebenszeichen> liste(Lebenszeichen... arten) {
        return new LinkedHashSet<>(List.of(arten));
    }

    @Test
    @DisplayName("Die Vorgabe traegt sechs Arten - benutzen ist AUS")
    void vorgabeOhneBenutzen() {
        // Der WORTLAUT der Liste und nicht ihre Laenge: Eine Zaehlung von sechs waere auch
        // gruen, wenn jemand benutzen hineinnimmt und kaempfen herausnimmt - und dann
        // gaebe ein Setzmakro wieder zwei Zeichen aus einer Taste.
        assertEquals(List.of("bewegen", "abbauen", "setzen", "oeffnen", "kaempfen",
                        "sprechen"),
                schluessel(Messwerte.VORGABE.arten()));
        assertFalse(Messwerte.VORGABE.zaehlt(Lebenszeichen.BENUTZEN),
                "benutzen ist die halbe Art zum Setzen und darf ab Werk nicht zaehlen");
        assertEquals(2, Messwerte.VORGABE.verlangteArten());
        assertEquals(8, Messwerte.VORGABE.mindestStrecke());
    }

    @Test
    @DisplayName("Die REIHENFOLGE der Liste bleibt - sie ist die Reihenfolge im Bericht")
    void reihenfolgeBleibt() {
        // Ohne diese Zusage stuende der Bericht von Lauf zu Lauf anders da, und das sieht
        // aus wie eine geaenderte Einstellung. Darum LinkedHashSet und nicht Set.of.
        Messwerte eigene = Messwerte.VORGABE.mitArten(
                liste(Lebenszeichen.SPRECHEN, Lebenszeichen.ABBAUEN,
                        Lebenszeichen.BEWEGEN), 2);

        assertEquals(List.of("sprechen", "abbauen", "bewegen"),
                schluessel(eigene.arten()));
    }

    @Test
    @DisplayName("Die Artenmenge ist eine eigene Kopie und nicht veraenderbar")
    void eigeneKopie() {
        Set<Lebenszeichen> aussen = liste(Lebenszeichen.ABBAUEN, Lebenszeichen.BEWEGEN);
        Messwerte werte = Messwerte.VORGABE.mitArten(aussen, 2);

        aussen.add(Lebenszeichen.BENUTZEN);

        assertEquals(2, werte.arten().size(),
                "ein Satz, dessen Liste von aussen waechst, haelt seine eigene Pruefung"
                        + " nicht mehr - verlangteArten koennte dann daneben liegen");
        assertThrows(UnsupportedOperationException.class,
                () -> werte.arten().add(Lebenszeichen.BENUTZEN));
    }

    @Test
    @DisplayName("Mehr verlangte Arten als vorhandene kommt nicht zustande")
    void unmoeglichesPaar() {
        // Zwei Werte, die JEDER FUER SICH erlaubt sind: 3 ist eine gueltige Zahl, zwei
        // Arten sind eine gueltige Liste. Zusammen waere kein Schlag jemals taetig.
        IllegalArgumentException wurf = assertThrows(IllegalArgumentException.class,
                () -> new Messwerte(3, 8,
                        liste(Lebenszeichen.ABBAUEN, Lebenszeichen.BEWEGEN)));

        assertTrue(wurf.getMessage().contains("3") && wurf.getMessage().contains("2"),
                "der Wurf muss BEIDE Zahlen nennen, sonst sucht der Leser die zweite: "
                        + wurf.getMessage());
    }

    @Test
    @DisplayName("Eine leere Artenliste kommt nicht zustande - das waere der Ausschalter")
    void keineLeereListe() {
        assertThrows(IllegalArgumentException.class, () -> new Messwerte(1, 8, liste()));
        assertThrows(IllegalArgumentException.class, () -> new Messwerte(1, 8, null));
    }

    @Test
    @DisplayName("Eine 0 in der Liste ist ein Programmfehler")
    void keinNullEintrag() {
        Set<Lebenszeichen> mitLuecke = new LinkedHashSet<>(
                Arrays.asList(Lebenszeichen.ABBAUEN, null));

        assertThrows(IllegalArgumentException.class, () -> new Messwerte(1, 8, mitLuecke));
    }

    @Test
    @DisplayName("Die Mindeststrecke liegt zwischen 1 und 1000")
    void streckeImBereich() {
        assertTrue(Messwerte.brauchbareStrecke(1));
        assertTrue(Messwerte.brauchbareStrecke(8));
        assertTrue(Messwerte.brauchbareStrecke(Messwerte.HOECHSTE_STRECKE));
        // Eine 0 hiesse, dass Stillstehen als Bewegung zaehlt - dann waere das Bewegen
        // ein Freizeichen fuer jeden, der ueberhaupt anwesend ist.
        assertFalse(Messwerte.brauchbareStrecke(0));
        assertFalse(Messwerte.brauchbareStrecke(-1));
        // Darueber waere sie nur noch im Flug zu schaffen, also stumm nie.
        assertFalse(Messwerte.brauchbareStrecke(Messwerte.HOECHSTE_STRECKE + 1));
        assertThrows(IllegalArgumentException.class,
                () -> new Messwerte(1, 0, liste(Lebenszeichen.ABBAUEN)));
    }

    @Test
    @DisplayName("Die Abwandler lassen die anderen Werte stehen")
    void abwandlerAendernNurEins() {
        Messwerte andere = Messwerte.VORGABE.mitVerlangtenArten(3);

        assertEquals(3, andere.verlangteArten());
        assertEquals(Messwerte.VORGABE.mindestStrecke(), andere.mindestStrecke());
        assertEquals(Messwerte.VORGABE.arten(), andere.arten());

        Messwerte weiter = Messwerte.VORGABE.mitStrecke(40);

        assertEquals(40, weiter.mindestStrecke());
        assertEquals(Messwerte.VORGABE.verlangteArten(), weiter.verlangteArten());
        assertEquals(Messwerte.VORGABE.arten(), weiter.arten());
    }

    @Test
    @DisplayName("mitArten nimmt die Zahl MIT, weil sie sonst nicht passen koennte")
    void mitArtenNimmtDieZahlMit() {
        // Von sechs Arten auf eine herunter: Mit der alten 2 waere der Satz unmoeglich.
        // Haette mitArten nur einen Parameter, muesste der Aufrufer es in zwei Schritten
        // tun, von denen der erste wirft.
        Messwerte eine = Messwerte.VORGABE.mitArten(liste(Lebenszeichen.ABBAUEN), 1);

        assertEquals(1, eine.verlangteArten());
        assertEquals(1, eine.arten().size());
    }

    @Test
    @DisplayName("Zwei Saetze mit gleichem Inhalt sind gleich - einer mit anderer Liste nicht")
    void gleichheitHaengtAmInhalt() {
        Messwerte a = new Messwerte(2, 8,
                liste(Lebenszeichen.ABBAUEN, Lebenszeichen.BEWEGEN));
        Messwerte b = new Messwerte(2, 8,
                liste(Lebenszeichen.ABBAUEN, Lebenszeichen.BEWEGEN));

        assertEquals(a, b);
        // Der Unterschied ist NUR die Liste: gleiche Zahl, gleiche Strecke, gleiche
        // Laenge - und doch eine andere Messung. Daran haengt die Zeile des Neuladens.
        assertNotEquals(a, new Messwerte(2, 8,
                liste(Lebenszeichen.ABBAUEN, Lebenszeichen.SETZEN)));
    }

    @Test
    @DisplayName("Die Pfade sind die Schluessel der config.yml und stehen genau einmal")
    void pfade() {
        // Abgeschrieben wuerde hier nichts gesichert; der Wert liegt darin, dass die
        // Pfade im Abschnitt messstelle liegen und nicht versehentlich unter zeit.
        assertEquals("messstelle.verlangte-arten", Messwerte.PFAD_VERLANGTE_ARTEN);
        assertEquals("messstelle.mindest-strecke", Messwerte.PFAD_STRECKE);
        assertEquals("messstelle.arten", Messwerte.PFAD_ARTEN);
    }

    private static List<String> schluessel(Set<Lebenszeichen> arten) {
        List<String> namen = new ArrayList<>();
        for (Lebenszeichen art : arten) {
            namen.add(art.schluessel());
        }
        return namen;
    }
}
