package de.freimarken.paper;

import java.util.Set;

/**
 * Die Saetze von {@code /freimarken spielzeit}.
 *
 * <p><b>Noch leer - diese Aufzaehlung gehoert Bauschritt 4.</b> Sie steht schon hier,
 * damit {@link Sprachschluessel} sie vom ersten Schritt an fuehrt und
 * {@code VorlageTest.alleBereicheStehenImVerzeichnis} von Anfang an etwas zusichert.
 * Sieben Dateien statt einer, eine je Bauschritt: So teilen zwei gleichzeitig arbeitende
 * Haende nie dieselbe Datei. Die Dateien dieses Schrittes sind {@code Spielzeittext} und {@code SpielzeitBefehle}.
 */
enum SpielzeitSatz implements Satz {

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
