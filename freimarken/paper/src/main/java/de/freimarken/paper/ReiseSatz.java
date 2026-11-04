package de.freimarken.paper;

import java.util.Set;

/**
 * Die Saetze des Reisens - {@code /warp}, {@code /zurueck} und die Aufwaermzeit.
 *
 * <p><b>Noch leer - diese Aufzaehlung gehoert Bauschritt 5.</b> Sie steht schon hier,
 * damit {@link Sprachschluessel} sie vom ersten Schritt an fuehrt und
 * {@code VorlageTest.alleBereicheStehenImVerzeichnis} von Anfang an etwas zusichert.
 * Sieben Dateien statt einer, eine je Bauschritt: So teilen zwei gleichzeitig arbeitende
 * Haende nie dieselbe Datei. Die Dateien dieses Schrittes sind {@code Reise}, {@code Aufwaermzeit}, {@code RueckwegBefehle} und {@code WarpBefehle}.
 */
enum ReiseSatz implements Satz {

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
