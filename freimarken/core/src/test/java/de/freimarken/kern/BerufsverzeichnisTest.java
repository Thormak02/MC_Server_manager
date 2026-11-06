package de.freimarken.kern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Welche Berufe es gibt und wer auf wem aufbaut - gelesen aus der Einstellungsdatei.
 *
 * <p>Das Verzeichnis ist die EINZIGE Stelle, die die Rolle eines Berufes kennt. Die Ablage
 * kennt sie nicht und darf sie nicht kennen - sonst stuende sie zweimal da, und bei einer
 * Aenderung in der Datei waere die in der Datenbank die falsche.
 *
 * <p>Darum wirft dieses Verzeichnis bei einer kaputten Zeile NICHT, sondern laesst sie
 * liegen: Ein Server, der wegen eines Tippfehlers in einem von zwanzig Berufen nicht mehr
 * startet, ist schlimmer als ein Server mit neunzehn Berufen. Der Leser der Datei meldet,
 * was er weggelassen hat.
 */
class BerufsverzeichnisTest {

    private static Berufsverzeichnis verzeichnis(Berufsangabe... angaben) {
        return Berufsverzeichnis.ausAngaben(Arrays.asList(angaben));
    }

    private static Berufsangabe grund(String schluessel) {
        return new Berufsangabe(schluessel, null);
    }

    private static Berufsangabe spezial(String schluessel, String grundberuf) {
        return new Berufsangabe(schluessel, grundberuf);
    }

    @Test
    @DisplayName("Ein leeres Verzeichnis ist erlaubt und heisst: keine Berufe")
    void leeresVerzeichnis() {
        Berufsverzeichnis leer = verzeichnis();

        assertEquals(List.of(), leer.berufe());
        assertEquals(List.of(), leer.grundberufe());
        assertEquals(List.of(), leer.spezialisierungen());
        assertEquals(0, leer.anzahlDerRolle(false));
        assertEquals(Optional.empty(), leer.aufgeloest("bergmann"));
    }

    @Test
    @DisplayName("Ohne Angaben gibt es kein Verzeichnis")
    void nullIstKeineLeereListe() {
        // null heisst "nicht gelesen", eine leere Liste heisst "keine Berufe". Wer das
        // gleichsetzt, startet einen Server ohne Berufe und haelt es fuer die Einstellung.
        assertThrows(IllegalArgumentException.class,
                () -> Berufsverzeichnis.ausAngaben(null));
    }

    @Test
    @DisplayName("Die Reihenfolge ist die der Datei, nicht die des Alphabets")
    void reihenfolgeDerDatei() {
        Berufsverzeichnis buch = verzeichnis(grund("schmied"), grund("bauer"),
                spezial("steinmetz", "schmied"));

        // Sie ist das, was /beruf liste zeigt. Der Betreiber hat die Datei sortiert;
        // alphabetisch waere eine zweite Entscheidung, die ihm niemand gesagt hat.
        assertEquals(List.of("schmied", "bauer", "steinmetz"), buch.berufe());
        assertEquals(List.of("schmied", "bauer"), buch.grundberufe());
        assertEquals(List.of("steinmetz"), buch.spezialisierungen());
    }

    @Test
    @DisplayName("Ein zweites Mal derselbe Schluessel - der erste gilt")
    void doppelteWerdenUebergangen() {
        Berufsverzeichnis buch = verzeichnis(grund("bergmann"), grund("bauer"),
                spezial("bergmann", "bauer"));

        assertEquals(List.of("bergmann", "bauer"), buch.berufe());
        assertFalse(buch.istSpezialisierung("bergmann"),
                "die zweite Zeile hat die erste nicht umgeschrieben");
    }

    @Test
    @DisplayName("Eine Spezialisierung ohne ihren Grundberuf bleibt liegen")
    void spezialisierungOhneGrundberuf() {
        // Sonst stuende ein Beruf in der Liste, den niemand waehlen kann: Die Regel sucht
        // den Grundberuf und findet ihn nie. Die Absage hiesse "dir fehlt der Grundberuf
        // schmied" - und schmied gibt es gar nicht.
        Berufsverzeichnis buch = verzeichnis(grund("bergmann"),
                spezial("steinmetz", "schmied"));

        assertEquals(List.of("bergmann"), buch.berufe());
        assertEquals(Optional.empty(), buch.aufgeloest("steinmetz"));
    }

    @Test
    @DisplayName("Eine Spezialisierung auf eine Spezialisierung bleibt liegen")
    void keineKette() {
        // Es gibt genau zwei Rollen. Eine Kette waere eine dritte, und die Platzzaehlung
        // der Regel kennt nur zwei Toepfe - der Beruf in der Mitte zaehlte in beiden.
        Berufsverzeichnis buch = verzeichnis(grund("bergmann"),
                spezial("steinmetz", "bergmann"), spezial("bildhauer", "steinmetz"));

        assertEquals(List.of("bergmann", "steinmetz"), buch.berufe());
        assertEquals(Optional.empty(), buch.aufgeloest("bildhauer"));
    }

    @Test
    @DisplayName("Der Grundberuf darf in der Datei weiter unten stehen")
    void grundberufDarfSpaeterStehen() {
        // Sonst muesste der Betreiber seine Datei in einer Reihenfolge halten, die ihm
        // niemand genannt hat - und der weggelassene Beruf saehe aus wie ein Tippfehler.
        Berufsverzeichnis buch = verzeichnis(spezial("steinmetz", "bergmann"),
                grund("bergmann"));

        assertEquals(List.of("steinmetz", "bergmann"), buch.berufe());
        assertEquals(Optional.of("bergmann"), buch.grundberufVon("steinmetz"));
    }

    @Test
    @DisplayName("Eine fehlende Zeile in der Liste bringt nichts zum Stehen")
    void nullEintraegeWerdenUebersprungen() {
        // Eine Zeile in der Datei, die kein Abschnitt ist, liest der Leser als null.
        Berufsverzeichnis buch = verzeichnis(grund("bergmann"), null, grund("bauer"));

        assertEquals(List.of("bergmann", "bauer"), buch.berufe());
    }

    @Test
    @DisplayName("Ein getippter Name wird aufgeloest, auch gross und mit Leerraum")
    void aufloesen() {
        Berufsverzeichnis buch = verzeichnis(grund("bergmann"));

        assertEquals(Optional.of("bergmann"), buch.aufgeloest("BERGMANN"));
        assertEquals(Optional.of("bergmann"), buch.aufgeloest(" Bergmann "));
        assertEquals(Optional.empty(), buch.aufgeloest("bergman"));
        assertEquals(Optional.empty(), buch.aufgeloest(""));
        assertEquals(Optional.empty(), buch.aufgeloest(null));
    }

    @Test
    @DisplayName("Die Vorschlaege sind die Berufe, die so anfangen")
    void vorschlaege() {
        Berufsverzeichnis buch = verzeichnis(grund("bergmann"), grund("bauer"),
                spezial("bergfuehrer", "bergmann"), grund("schmied"));

        assertEquals(List.of("bergmann", "bergfuehrer"), buch.vorschlaege("berg"));
        assertEquals(List.of("bergmann", "bergfuehrer"), buch.vorschlaege(" BERG"));
        // Ohne Eingabe alles - die Vervollstaendigung zeigt dann die ganze Liste, und
        // zwar in der Reihenfolge der Datei.
        assertEquals(List.of("bergmann", "bauer", "bergfuehrer", "schmied"),
                buch.vorschlaege(""));
        assertEquals(List.of("bergmann", "bauer", "bergfuehrer", "schmied"),
                buch.vorschlaege(null));
        assertEquals(List.of(), buch.vorschlaege("holz"));
    }

    @Test
    @DisplayName("Die Rollen werden beantwortet, auch fuer unbekannte Namen")
    void rollen() {
        Berufsverzeichnis buch = verzeichnis(grund("bergmann"),
                spezial("steinmetz", "bergmann"));

        assertTrue(buch.istGrundberuf("bergmann"));
        assertFalse(buch.istSpezialisierung("bergmann"));
        assertTrue(buch.istSpezialisierung("steinmetz"));
        assertFalse(buch.istGrundberuf("steinmetz"));
        // Ein unbekannter Name ist KEINE von beiden Rollen. Ein Wurf waere hier falsch:
        // Die Regel fragt die Rolle auch fuer Berufe, die ein Spieler laut Ablage haelt,
        // die aber aus der Datei verschwunden sind.
        assertFalse(buch.istGrundberuf("holzfaeller"));
        assertFalse(buch.istSpezialisierung("holzfaeller"));
        // Nach null wird hier NICHT gefragt, und das ist die Grenze zwischen zwei Arten
        // von Methoden: aufgeloest und vorschlaege lesen, was der Spieler getippt hat,
        // und halten darum auch nichts aus. Diese beiden bekommen einen fertigen
        // Schluessel - aus einer Angabe, aus einem Antrag oder aus einem Stand, und alle
        // drei lassen kein null durch. Ein null hier kaeme nur aus einem Rechenfehler,
        // und dann ist ein Wurf die ehrlichere Antwort als ein false.
    }

    @Test
    @DisplayName("Der Grundberuf einer Spezialisierung, und sonst nichts")
    void grundberufVon() {
        Berufsverzeichnis buch = verzeichnis(grund("bergmann"),
                spezial("steinmetz", "bergmann"));

        assertEquals(Optional.of("bergmann"), buch.grundberufVon("steinmetz"));
        assertEquals(Optional.empty(), buch.grundberufVon("bergmann"));
        assertEquals(Optional.empty(), buch.grundberufVon("holzfaeller"));
    }

    @Test
    @DisplayName("Die Spezialisierungen zu einem Grundberuf - die Regel braucht sie beide")
    void spezialisierungenZu() {
        Berufsverzeichnis buch = verzeichnis(grund("bergmann"), grund("bauer"),
                spezial("steinmetz", "bergmann"), spezial("juwelier", "bergmann"),
                spezial("gaertner", "bauer"));

        // Das ist der Weg, auf dem pruefeVerlassen erkennt, dass ein Grundberuf nicht
        // weg darf: Es sind MEHRERE, und es genuegt eine gehaltene.
        assertEquals(List.of("steinmetz", "juwelier"),
                buch.spezialisierungenZu("bergmann"));
        assertEquals(List.of("gaertner"), buch.spezialisierungenZu("bauer"));
        assertEquals(List.of(), buch.spezialisierungenZu("steinmetz"));
        assertEquals(List.of(), buch.spezialisierungenZu("holzfaeller"));
    }

    @Test
    @DisplayName("Die Zahl je Rolle ist die Zahl, die der Leser gegen die Plaetze haelt")
    void anzahlDerRolle() {
        Berufsverzeichnis buch = verzeichnis(grund("bergmann"), grund("bauer"),
                spezial("steinmetz", "bergmann"));

        assertEquals(2, buch.anzahlDerRolle(false));
        assertEquals(1, buch.anzahlDerRolle(true));
        // Gezaehlt wird, was UEBRIG BLIEB: Eine weggelassene Zeile darf nicht als Platz
        // mitzaehlen, sonst stimmt die Gegenprobe des Lesers nicht.
        assertEquals(1, verzeichnis(grund("bergmann"),
                spezial("steinmetz", "schmied")).anzahlDerRolle(false));
        assertEquals(0, verzeichnis(grund("bergmann"),
                spezial("steinmetz", "schmied")).anzahlDerRolle(true));
    }
}
