package de.freimarken.kern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Was JEDE Umsetzung von {@link Wolkenablage} halten muss.
 *
 * <p>Geteilt zwischen der Fassung im Arbeitsspeicher und der auf MariaDB, und hier hat das
 * zwei handfeste Gruende: Das Erfuellen ist dort ein {@code Set.add}, hier ein
 * {@code INSERT IGNORE} mit Zeilenzaehlung - und das Lesen eines Standes ist dort ein
 * Kartengriff, hier eine Zusammenfuehrung aus ZWEI Tabellen. An genau solchen Stellen
 * laufen zwei Fassungen auseinander, ohne dass eine Probe rot wird.
 *
 * <p><b>Die tragende Zusage ist die Antwort von {@link Wolkenablage#erfuellen}.</b> Daran
 * haengt die Belohnung einer Herausforderung: Wer zweimal abgibt, bekommt sie nicht
 * zweimal bezahlt. Der Riegel gehoert der Ablage - eine Pruefung im Programm davor haette
 * zwischen Fragen und Schreiben eine Luecke.
 */
public abstract class WolkenVertrag {

    /** Ein Zeitpunkt mit Millisekunden, damit die Spalte ihn ungeschnitten halten muss. */
    protected static final Instant GEZAEHLT = Instant.parse("2026-11-18T12:34:56.789Z");

    /** Eine frische, leere Ablage. */
    protected abstract Wolkenablage ablage();

    /**
     * Die Nummer einer Insel, die es WIRKLICH gibt.
     *
     * <p>Noetig, weil die Fassung auf MariaDB einen Fremdschluessel auf
     * {@code parzellen(id)} traegt - und das ist kein Umstand, sondern die Zusage, auf der
     * das Loeschen beruht: Wer seine Insel freigibt, gibt ihren Fortschritt mit auf. Die
     * Fassung im Arbeitsspeicher gibt einfach die naechste Zahl.
     */
    protected abstract long frischeInsel();

    @Test
    @DisplayName("Eine Insel ohne Zeile hat den LEEREN Stand - und ist kein Fehler")
    void unbekannteInselIstLeer() {
        // Jede frisch genommene Insel steht so da, und darum legt das Nehmen keine Zeile
        // an. Ein Wurf hier waere ein Generator, der bei der ersten Lava stirbt.
        Wolkenablage ablage = ablage();
        long insel = frischeInsel();

        Inselstand stand = ablage.stand(insel);

        assertEquals(insel, stand.inselId());
        assertEquals(0, stand.stufe());
        assertFalse(stand.hatBiom());
        assertFalse(stand.schonGezaehlt());
        assertEquals(0L, stand.punkte());
        assertEquals(Set.of(), stand.erfuellt());
    }

    @Test
    @DisplayName("Das Biom wird gesetzt, gelesen und ERSETZT")
    void biomSetzenUndErsetzen() {
        Wolkenablage ablage = ablage();
        long insel = frischeInsel();

        ablage.biomSetzen(insel, "swamp");
        assertEquals("swamp", ablage.stand(insel).biom());
        assertTrue(ablage.stand(insel).hatBiom());

        ablage.biomSetzen(insel, "taiga");

        assertEquals("taiga", ablage.stand(insel).biom(),
                "setzend und nicht anfuegend: Eine Insel hat EIN Biom");
    }

    @Test
    @DisplayName("Die Punkte kommen mit ihrem Zeitpunkt zurueck - auf die Millisekunde")
    void punkteUndZeitpunkt() {
        // Beides in EINEM Schreibzug: Eine Punktzahl ohne Zeitpunkt waere eine Zahl, von
        // der niemand weiss, wie alt sie ist. Und die Millisekunden sind der Grund fuer
        // DATETIME(3) - eine Spalte ohne sie schnitte sie ab, und das faellt sonst erst
        // auf, wenn jemand zwei Zaehlungen in derselben Sekunde unterscheidet.
        Wolkenablage ablage = ablage();
        long insel = frischeInsel();

        ablage.punkteSetzen(insel, 4_711L, GEZAEHLT);

        Inselstand stand = ablage.stand(insel);
        assertEquals(4_711L, stand.punkte());
        assertEquals(GEZAEHLT, stand.gezaehltAm());
        assertTrue(stand.schonGezaehlt());
    }

    @Test
    @DisplayName("Die Punkte werden SETZEND geschrieben, nicht aufschlagend")
    void punkteSindEinZustand() {
        // Der Unterschied zur Truhenablage, und er ist die Sache selbst: Eine Insel hat
        // nicht mehr Punkte, weil sie oefter gezaehlt wurde.
        Wolkenablage ablage = ablage();
        long insel = frischeInsel();

        ablage.punkteSetzen(insel, 100L, GEZAEHLT);
        ablage.punkteSetzen(insel, 40L, GEZAEHLT.plusSeconds(60));

        assertEquals(40L, ablage.stand(insel).punkte(),
                "aufschlagend geschrieben stuenden hier 140 - und die Punktzahl waere"
                        + " eine Zahl, die nur wachsen kann");
        assertEquals(GEZAEHLT.plusSeconds(60), ablage.stand(insel).gezaehltAm());
    }

    @Test
    @DisplayName("Biom und Punkte stoeren sich nicht")
    void biomUndPunkteStoerenSichNicht() {
        // Die Probe gegen das naheliegende Schreiben mit zwei vollen Zeilen: Wer die
        // Punkte setzt und dabei das Biom mitschreibt, loescht es - und zwar lautlos.
        Wolkenablage ablage = ablage();
        long insel = frischeInsel();

        ablage.biomSetzen(insel, "swamp");
        ablage.punkteSetzen(insel, 7L, GEZAEHLT);

        assertEquals("swamp", ablage.stand(insel).biom());
        assertEquals(7L, ablage.stand(insel).punkte());

        ablage.biomSetzen(insel, "taiga");

        assertEquals(7L, ablage.stand(insel).punkte(), "und umgekehrt genauso");
    }

    @Test
    @DisplayName("Erfuellen sagt NUR beim ersten Mal ja")
    void erfuellenSagtNurEinmalJa() {
        // DIE TRAGENDE PROBE DIESER ABLAGE. An dieser Antwort haengt die Belohnung: Wer
        // zweimal abgibt, bekommt sie nicht zweimal bezahlt.
        Wolkenablage ablage = ablage();
        long insel = frischeInsel();

        assertTrue(ablage.erfuellen(insel, "erster_stein"));
        assertFalse(ablage.erfuellen(insel, "erster_stein"),
                "sonst zahlt jede Wiederholung noch einmal");
        assertEquals(Set.of("erster_stein"), ablage.stand(insel).erfuellt());
        assertTrue(ablage.stand(insel).hatErfuellt("erster_stein"));
    }

    @Test
    @DisplayName("Die STUFE ist die Zahl der erfuellten Herausforderungen")
    void stufeIstDieZahl() {
        // Gezaehlt und nicht gespeichert: Eine eigene Spalte daneben waere eine zweite
        // Wahrheit, und die erste Zeile, die beim Erfuellen nicht mitgeschrieben wird,
        // liesse die Erzausbeute hinter den Herausforderungen zurueckbleiben.
        Wolkenablage ablage = ablage();
        long insel = frischeInsel();

        ablage.erfuellen(insel, "erster_stein");
        ablage.erfuellen(insel, "erstes_brot");
        ablage.erfuellen(insel, "erstes_eisen");

        assertEquals(3, ablage.stand(insel).stufe());
    }

    @Test
    @DisplayName("Zwei Inseln teilen nichts")
    void zweiInselnTeilenNichts() {
        Wolkenablage ablage = ablage();
        long eine = frischeInsel();
        long andere = frischeInsel();

        ablage.biomSetzen(eine, "swamp");
        ablage.erfuellen(eine, "erster_stein");
        ablage.punkteSetzen(eine, 99L, GEZAEHLT);

        assertEquals(0, ablage.stand(andere).stufe());
        assertFalse(ablage.stand(andere).hatBiom());
        assertEquals(0L, ablage.stand(andere).punkte());
        assertTrue(ablage.erfuellen(andere, "erster_stein"),
                "dieselbe Herausforderung auf einer anderen Insel ist NEU");
    }

    @Test
    @DisplayName("alle() nennt jede Insel mit Zeile - auch eine mit NUR Herausforderungen")
    void alleNenntAuchDieOhneStandzeile() {
        // Der Fall, den die Fassung auf MariaDB nur mit einer zweiten Abfrage trifft:
        // Das Erfuellen schreibt NUR in die Herausforderungstabelle. Eine Insel, auf der
        // jemand etwas erfuellt und sonst nichts getan hat, hat damit eine STUFE und
        // keine Standzeile - und ohne sie fehlte beim Start genau die Stufe, an der die
        // Erzausbeute haengt.
        Wolkenablage ablage = ablage();
        long mitPunkten = frischeInsel();
        long nurErfuellt = frischeInsel();
        ablage.punkteSetzen(mitPunkten, 10L, GEZAEHLT);
        ablage.erfuellen(nurErfuellt, "erster_stein");

        var alle = ablage.alle();

        assertEquals(2, alle.size(), "bekommen: " + alle);
        assertEquals(1, alle.stream().filter(s -> s.inselId() == nurErfuellt)
                .findFirst().orElseThrow().stufe());
        assertEquals(10L, alle.stream().filter(s -> s.inselId() == mitPunkten)
                .findFirst().orElseThrow().punkte());
    }

    @Test
    @DisplayName("Eine Ablage ohne Zeilen gibt eine leere Liste und keinen Fehler")
    void alleIstLeer() {
        assertEquals(0, ablage().alle().size());
    }

    @Test
    @DisplayName("Entfernen loescht den Stand UND die Herausforderungen")
    void entfernenLoeschtBeides() {
        // Wer seine Insel freigibt, gibt ihren Fortschritt mit auf. Ohne diese Zusage
        // erbte der naechste Spieler auf diesem Feld die Erzausbeute eines Fremden - und
        // die Herausforderungen waeren fuer ihn alle schon erfuellt.
        Wolkenablage ablage = ablage();
        long insel = frischeInsel();
        ablage.biomSetzen(insel, "swamp");
        ablage.punkteSetzen(insel, 99L, GEZAEHLT);
        ablage.erfuellen(insel, "erster_stein");

        ablage.entfernen(insel);

        Inselstand danach = ablage.stand(insel);
        assertEquals(0, danach.stufe(), "der Fortschritt ist weg");
        assertFalse(danach.hatBiom());
        assertEquals(0L, danach.punkte());
        assertEquals(0, ablage.alle().size());
        assertTrue(ablage.erfuellen(insel, "erster_stein"),
                "und dieselbe Herausforderung ist wieder neu");
    }

    @Test
    @DisplayName("Entfernen einer Insel ohne Zeile tut nichts und ist kein Fehler")
    void entfernenOhneZeile() {
        Wolkenablage ablage = ablage();

        ablage.entfernen(frischeInsel());

        assertEquals(0, ablage.alle().size());
    }

    @Test
    @DisplayName("Eine Insel ohne Nummer und negative Punkte sind Programmfehler")
    void riegel() {
        Wolkenablage ablage = ablage();
        long insel = frischeInsel();

        assertThrows(IllegalArgumentException.class, () -> ablage.stand(0L));
        assertThrows(IllegalArgumentException.class, () -> ablage.stand(-1L));
        assertThrows(IllegalArgumentException.class,
                () -> ablage.punkteSetzen(insel, -1L, GEZAEHLT));
        assertThrows(IllegalArgumentException.class,
                () -> ablage.erfuellen(insel, " "));
    }
}
