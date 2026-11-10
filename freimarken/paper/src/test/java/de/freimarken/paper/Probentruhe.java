package de.freimarken.paper;

import de.freimarken.kern.Abholgut;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BooleanSupplier;

/**
 * Die Ladentruhe als Attrappe - lesen, leeren, mitschreiben.
 *
 * <p><b>Eine eigene Datei und keine Klasse in einer Probe</b> [10.11.2026]: Seit dem
 * Vermieten dieselbe Truhe leert, die der taegliche Lauf raeumt, brauchen sie VIER
 * Probenklassen. Vier Abschriften derselben Attrappe laufen auseinander - und dann
 * prueft die eine Probe einen Griff, den die andere nie sieht.
 *
 * <p><b>Sie wirft nicht von sich aus.</b> Die echte {@code Ladenware} wirft auch nicht:
 * Ein Wurf dort liesse einen Laden fuer immer unraeumbar. Wer den Wurf PRUEFEN will -
 * eine nicht geladene Welt, eine Kiste, die keine mehr ist -, schaltet ihn mit
 * {@link #wirftBeiJedemGriff()} ein; das ist der Fall, gegen den die Faenger stehen.
 */
final class Probentruhe implements Mietlauf.Truhe {

    private final Map<String, List<String>> inhalte = new LinkedHashMap<>();
    private final List<String> griffe = new ArrayList<>();
    private BooleanSupplier vertragDa;
    private List<String> beobachtet;
    private boolean wirft;

    void lege(String einheit, String... beschriftungen) {
        inhalte.put(einheit, new ArrayList<>(List.of(beschriftungen)));
    }

    List<String> inhalt(String einheit) {
        return inhalte.getOrDefault(einheit, List.of());
    }

    /** Jeder Griff in der Reihenfolge - fuer die Probe, die das AUSBLEIBEN prueft. */
    List<String> griffe() {
        return List.copyOf(griffe);
    }

    /** Von jetzt an wirft jeder Griff - die nicht geladene Welt. */
    Probentruhe wirftBeiJedemGriff() {
        this.wirft = true;
        return this;
    }

    /** Von jetzt an wird jeder Griff samt Vertragslage aufgeschrieben. */
    void merkeVertragslage(BooleanSupplier lage, List<String> hinein) {
        this.vertragDa = lage;
        this.beobachtet = hinein;
    }

    @Override
    public List<Abholgut> lese(String einheit) {
        notiere("lese " + einheit);
        List<Abholgut> gut = new ArrayList<>();
        for (String name : inhalt(einheit)) {
            gut.add(new Abholgut(name, "inhalt-von-" + name));
        }
        return gut;
    }

    @Override
    public void leere(String einheit) {
        notiere("leere " + einheit);
        inhalte.remove(einheit);
    }

    private void notiere(String was) {
        griffe.add(was);
        if (beobachtet != null) {
            beobachtet.add(was.split(" ")[0] + ", Vertrag da: " + vertragDa.getAsBoolean());
        }
        if (wirft) {
            throw new IllegalStateException("Die Welt der Ladentruhe ist nicht geladen"
                    + " (Probe) - " + was);
        }
    }
}
