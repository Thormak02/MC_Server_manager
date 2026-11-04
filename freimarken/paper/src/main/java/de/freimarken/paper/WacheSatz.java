package de.freimarken.paper;

import java.util.Set;

/**
 * Die Saetze der Wachen - Bauschutz, Ankunft und Portale.
 *
 * <p><b>Noch leer - diese Aufzaehlung gehoert Bauschritt 6.</b> Sie steht schon hier,
 * damit {@link Sprachschluessel} sie vom ersten Schritt an fuehrt und
 * {@code VorlageTest.alleBereicheStehenImVerzeichnis} von Anfang an etwas zusichert.
 * Sieben Dateien statt einer, eine je Bauschritt: So teilen zwei gleichzeitig arbeitende
 * Haende nie dieselbe Datei. Die Dateien dieses Schrittes sind {@code Schutzwache}, {@code Ankunftswache} und {@code Portalwache}.
 */
enum WacheSatz implements Satz {

    ;

    @Override
    public String pfad() {
        throw new IllegalStateException("kein Satz vorhanden");
    }

    @Override
    public Art art() {
        throw new IllegalStateException("kein Satz vorhanden");
    }

    @Override
    public Set<String> platzhalter() {
        throw new IllegalStateException("kein Satz vorhanden");
    }

    @Override
    public boolean darfLeerSein() {
        throw new IllegalStateException("kein Satz vorhanden");
    }
}
