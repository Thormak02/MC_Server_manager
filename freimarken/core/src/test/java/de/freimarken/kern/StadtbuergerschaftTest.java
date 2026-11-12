package de.freimarken.kern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Wer Stadtbuerger ist - die Regel von Teil D, gemessen.
 *
 * <p>Die zwei Festlegungen, an denen alles haengt, sind in dieser Datei EINZELN
 * geprueft: Der Stadtbesitzer zaehlt nicht, und ein Mitbauer zaehlt nicht. Beide sind
 * Entscheidungen und keine Folgen - der Quelltext haette sie auch anders treffen
 * koennen -, und beide sind von aussen nicht zu sehen, solange niemand nachzaehlt.
 */
class StadtbuergerschaftTest {

    private static final String WELT = "freimarken:freistaedte";
    private static final long STADT = 42L;

    private static final UUID ANNA = UUID.randomUUID();
    private static final UUID BERT = UUID.randomUUID();
    private static final UUID CARA = UUID.randomUUID();

    /** Die Stadt, deren Besitzerin Anna ist - und Bert ihr Mitbauer. */
    private static Parzelle annastadt() {
        return new Parzelle(STADT, WELT, 3, 7, ANNA, Set.of(BERT), 400_000L,
                Instant.parse("2026-01-01T00:00:00Z"), Art.FREISTADT, Parzelle.KEIN_ELTERNTEIL,
                Zustand.AKTIV, "Annastadt", Parzelle.KEINE_RECHTE);
    }

    /** Ein Grundstueck in der Stadt, Nummer und Erwerbszeitpunkt frei waehlbar. */
    private static Grundstueck grundstueck(long id, UUID wer, String zeitpunkt) {
        return new Grundstueck(id, WELT, STADT, wer, Set.of(),
                Zellquader.eineZelle((int) id, 0), 0L, Instant.parse(zeitpunkt),
                Grundstueck.KEIN_ANGEBOT);
    }

    @Test
    @DisplayName("Wem ein Grundstueck in der Stadt gehoert, ist Stadtbuerger")
    void grundbesitzMachtBuerger() {
        assertTrue(Stadtbuergerschaft.istBuerger(annastadt(), BERT, true));
        assertFalse(Stadtbuergerschaft.istBuerger(annastadt(), BERT, false),
                "ohne Grund dort ist er keiner - die Buergerschaft ist ABGELEITET und"
                        + " keine Mitgliedschaft");
        assertFalse(Stadtbuergerschaft.istBuerger(annastadt(), null, true),
                "niemand ist kein Buerger");
    }

    @Test
    @DisplayName("DER STADTBESITZER IST KEIN BUERGER - auch mit eigenem Grundstueck")
    void derStadtbesitzerZaehltNicht() {
        // DIE FESTLEGUNG, VON DER TEIL E ABHAENGT. Jedes frisch zugeschnittene
        // Grundstueck gehoert zunaechst dem Stadtbesitzer. Zaehlte er mit, haette jede
        // Stadt nach dem ersten Zuschnitt fuer immer einen Buerger - und die
        // Verfallsregel aus Plan 16 ("zwoelf Monate ohne Besuch und ohne Buerger")
        // koennte nie greifen. Kein anderer Probenlauf dieses Projekts saehe das: Die
        // Liste waere einfach um einen Namen laenger.
        assertFalse(Stadtbuergerschaft.istBuerger(annastadt(), ANNA, true),
                "Anna ist die Stadtbesitzerin. Ihr gehoert jedes Grundstueck, das sie"
                        + " zugeschnitten und noch nicht verkauft hat - das macht sie"
                        + " nicht zur Buergerin ihrer eigenen Stadt.");

        assertEquals(List.of(), Stadtbuergerschaft.aus(annastadt(),
                        List.of(grundstueck(1L, ANNA, "2026-02-01T00:00:00Z"),
                                grundstueck(2L, ANNA, "2026-03-01T00:00:00Z"))),
                "eine Stadt mit zwei zugeschnittenen, aber unverkauften Grundstuecken"
                        + " hat KEINE Buerger - genau das muss Teil E lesen koennen");
    }

    @Test
    @DisplayName("Ein MITBAUER ist kein Stadtbuerger - weder am Grundstueck noch an"
            + " der Stadt")
    void mitbauerZaehlenNicht() {
        // Bert ist Mitbauer der STADT (siehe annastadt), Cara Mitbauerin des
        // GRUNDSTUECKS. Beide duerfen dort bauen, und keiner von beiden besitzt etwas.
        // Mit der anderen Entscheidung koennte ein Stadtbesitzer seine Stadt gegen den
        // Verfall immunisieren, indem er einen Freund eintraegt: ein Wort, und die Stadt
        // lebt zwoelf Monate laenger.
        Grundstueck mitCara = new Grundstueck(1L, WELT, STADT, ANNA, Set.of(CARA),
                Zellquader.eineZelle(1, 0), 0L, Instant.parse("2026-02-01T00:00:00Z"),
                Grundstueck.KEIN_ANGEBOT);
        assertTrue(mitCara.darfBauen(CARA), "sie darf bauen - sonst prueft das nichts");

        assertEquals(List.of(), Stadtbuergerschaft.aus(annastadt(), List.of(mitCara)),
                "weder die Mitbauerin des Grundstuecks noch der Mitbauer der Stadt"
                        + " stehen in der Liste");
    }

    @Test
    @DisplayName("Zwei Grundstuecke, EIN Buerger - mit der Zahl dahinter")
    void entdoppeltMitZahl() {
        List<Stadtbuergerschaft.Buerger> buerger = Stadtbuergerschaft.aus(annastadt(),
                List.of(grundstueck(1L, BERT, "2026-05-01T00:00:00Z"),
                        grundstueck(2L, BERT, "2026-06-01T00:00:00Z"),
                        grundstueck(3L, CARA, "2026-07-01T00:00:00Z")));

        assertEquals(2, buerger.size(), "zwei Leute und nicht drei Grundstuecke");
        assertEquals(new Stadtbuergerschaft.Buerger(BERT, 2), buerger.get(0));
        assertEquals(new Stadtbuergerschaft.Buerger(CARA, 1), buerger.get(1));
    }

    @Test
    @DisplayName("Geordnet nach dem AELTESTEN Grundstueck - wer laenger da ist, steht"
            + " oben")
    void geordnetNachDemAeltesten() {
        // Caras Grundstueck ist das aelteste, aber es steht an zweiter Stelle der
        // Eingabe. Ohne Ordnung haengt die Reihenfolge an der Laune der Ablage, und die
        // Liste sieht nach jedem Neustart anders aus.
        List<Stadtbuergerschaft.Buerger> buerger = Stadtbuergerschaft.aus(annastadt(),
                List.of(grundstueck(1L, BERT, "2026-06-01T00:00:00Z"),
                        grundstueck(2L, CARA, "2026-02-01T00:00:00Z")));

        assertEquals(List.of(new Stadtbuergerschaft.Buerger(CARA, 1),
                        new Stadtbuergerschaft.Buerger(BERT, 1)), buerger,
                "Cara zuerst - ihr Grundstueck ist vier Monate aelter");
    }

    @Test
    @DisplayName("Bei MEHREREN zaehlt das aelteste, nicht das zuletzt gelesene")
    void dasAeltesteVonMehreren() {
        // Bert hat ein altes und ein junges; Cara eines dazwischen. Rechnete die Regel
        // mit dem ZULETZT gelesenen Grundstueck, stuende Bert hinter Cara - obwohl er
        // laenger in der Stadt wohnt als sie.
        List<Stadtbuergerschaft.Buerger> buerger = Stadtbuergerschaft.aus(annastadt(),
                List.of(grundstueck(1L, BERT, "2026-01-15T00:00:00Z"),
                        grundstueck(2L, CARA, "2026-03-01T00:00:00Z"),
                        grundstueck(3L, BERT, "2026-09-01T00:00:00Z")));

        assertEquals(List.of(new Stadtbuergerschaft.Buerger(BERT, 2),
                        new Stadtbuergerschaft.Buerger(CARA, 1)), buerger);
    }

    @Test
    @DisplayName("Ein Buerger ohne Grundstueck ist keiner - und null ist nicht leer")
    void grenzfaelle() {
        assertThrows(IllegalArgumentException.class,
                () -> new Stadtbuergerschaft.Buerger(BERT, 0),
                "null Grundstuecke und Buerger sein widerspricht sich - und ein"
                        + " solcher Eintrag wuerde in Teil E als Buerger gezaehlt");
        assertThrows(IllegalArgumentException.class,
                () -> Stadtbuergerschaft.aus(annastadt(), null),
                "eine unbekannte Liste ist nicht dasselbe wie eine leere: Das eine"
                        + " heisst, die Ablage war stumm, das andere heisst, es gibt"
                        + " keine Buerger - und auf dem zweiten laesst Teil E eine"
                        + " Stadt verfallen");
        assertThrows(IllegalArgumentException.class,
                () -> Stadtbuergerschaft.istBuerger(null, BERT, true),
                "ohne Stadt ist das kein Fall, sondern ein Programmierfehler");
        assertEquals(List.of(), Stadtbuergerschaft.aus(annastadt(), List.of()),
                "eine Stadt ohne Grundstuecke hat keine Buerger");
    }
}
