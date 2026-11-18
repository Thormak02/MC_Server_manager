package de.freimarken.kern;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Der Zustand der Wolkeninseln im Arbeitsspeicher - die Fassung ohne Datenbank.
 *
 * <p>Dieselbe Rolle wie {@link SpeicherLager} und {@link SpeicherTruhe}: Sie haelt den
 * Vertrag vollstaendig, ueberlebt aber keinen Neustart. Gebraucht wird sie an zwei
 * Stellen - in den Proben des Kerns, und als Rueckfall auf einem Server ohne
 * Datenbankzugang, damit das Plugin dort nicht ausbleibt, sondern nur vergisst.
 *
 * <p><b>{@code synchronized} wie bei den Geschwistern</b>: Gelesen wird aus dem
 * Hauptfaden, geschrieben aus Befehlen - und bei einer Insel, deren Stufe gerade waechst,
 * liest der Kobbelsteingenerator im selben Augenblick.
 */
public final class SpeicherWolken implements Wolkenablage {

    private final Map<Long, Inselstand> staende = new LinkedHashMap<>();

    @Override
    public synchronized Inselstand stand(long inselId) {
        Inselstand gefunden = staende.get(inselId);
        return gefunden != null ? gefunden : Inselstand.leer(inselId);
    }

    @Override
    public synchronized List<Inselstand> alle() {
        return List.copyOf(new ArrayList<>(staende.values()));
    }

    @Override
    public synchronized void biomSetzen(long inselId, String biom) {
        staende.put(inselId, stand(inselId).mitBiom(biom));
    }

    @Override
    public synchronized void punkteSetzen(long inselId, long punkte, Instant wann) {
        staende.put(inselId, stand(inselId).mitPunkten(punkte, wann));
    }

    @Override
    public synchronized boolean erfuellen(long inselId, String schluessel) {
        if (schluessel == null || schluessel.isBlank()) {
            // Dieselbe Pruefung wie in MariaWolken, und sie steht in BEIDEN Fassungen:
            // Der Vertrag verlangt sie, und eine Herausforderung ohne Schluessel waere
            // eine Zeile, die keine Belohnung je wiederfindet.
            throw new IllegalArgumentException("Eine Herausforderung ohne Schluessel gibt"
                    + " es nicht");
        }
        Inselstand vorher = stand(inselId);
        if (vorher.hatErfuellt(schluessel)) {
            // Die Antwort, an der die Belohnung haengt: Wer zweimal abgibt, bekommt sie
            // nicht zweimal bezahlt.
            return false;
        }
        staende.put(inselId, vorher.mitErfuellt(schluessel));
        return true;
    }

    @Override
    public synchronized void entfernen(long inselId) {
        staende.remove(inselId);
    }
}
