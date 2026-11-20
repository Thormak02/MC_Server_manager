package de.freimarken.kern;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Welcher Rang einem Spieler ZUSTEHT - die Rechnung, ohne Welt und ohne Uhr.
 *
 * <p>Die Leiter kommt aus {@link Rangwerte}, die Messwerte von aussen: je
 * {@link Bedingungsart} eine Zahl in der Einheit, in der gemessen wird (Sekunden fuer die
 * Zeitarten, Stueck fuer den Rest - {@link Bedingungsart#inMesseinheit} rechnet die Datei
 * dorthin um). Dass diese Klasse nicht weiss, WOHER die Zahlen kommen, ist der Grund,
 * warum sie ohne Server zu pruefen ist - dieselbe Naht wie beim Ankunftspunkt.
 *
 * <p><b>Gestiegen wird in der Reihenfolge der Leiter.</b> Der zustehende Rang ist der
 * hoechste, fuer den diese Stufe UND jede darunter erfuellt ist. Wer die obere Bedingung
 * erfuellt und die untere nicht, bleibt unten stehen: Eine Leiter, auf der man Sprossen
 * ueberspringt, ist keine - und der Betreiber, der Ueberspringen will, schreibt die
 * Bedingung der unteren Stufe einfach in die obere.
 *
 * <p><b>Kein Abstieg</b> ({@link #aufstieg}): Was einmal erreicht ist, bleibt. Sonst
 * verloere ein Buerger seinen Rang, weil er seine Parzelle verkauft hat - und damit die
 * Obergrenze, unter der sein uebriger Besitz steht. Die Rechnung selbst
 * ({@link #zustehend}) sagt davon nichts; sie beantwortet nur, was die Zahlen hergeben.
 */
public final class Rangregel {

    /**
     * Eine Bedingung einer Stufe mit dem Stand daneben - erfuellt oder nicht.
     *
     * <p><b>Hiess bis zum 20.11.2026 {@code Fehlend}</b> und wurde nur fuer die offenen
     * Bedingungen gebaut. {@code /rang} braucht dieselben drei Werte auch fuer die
     * erfuellten: Was ein Spieler schon hat, ist die Haelfte der Auskunft. Ein zweiter
     * record mit derselben Gestalt waere eine Abschrift gewesen - und Abschriften laufen
     * auseinander, sobald eine von beiden eine Rechnung dazubekommt.
     *
     * @param art      woran es haengt
     * @param verlangt wie viel noetig ist, in der Messeinheit
     * @param gemessen wie viel der Spieler hat, in der Messeinheit
     */
    public record Bedingungsstand(Bedingungsart art, long verlangt, long gemessen) {

        public Bedingungsstand {
            if (art == null) {
                throw new IllegalArgumentException("Eine Bedingung ohne Art");
            }
        }

        /** Ob diese Bedingung erfuellt ist - dieselbe Frage wie in {@link #erfuellt}. */
        public boolean erfuellt() {
            return gemessen >= verlangt;
        }

        /** Wie viel noch fehlt - nie negativ. */
        public long rest() {
            return Math.max(0L, verlangt - gemessen);
        }
    }

    private Rangregel() {
    }

    /**
     * Ob diese Stufe mit diesen Messwerten erfuellt ist.
     *
     * <p>Eine Stufe ohne Bedingungen ist immer erfuellt - so ist der unterste Rang
     * gebaut. Eine Art, zu der gar nichts gemessen wurde, zaehlt als 0 und nicht als
     * "egal": Ein fehlender Messwert darf keinen Rang verschenken.
     */
    public static boolean erfuellt(Rangwerte.Stufe stufe,
                                   Map<Bedingungsart, Long> gemessen) {
        if (stufe == null) {
            throw new IllegalArgumentException("Ohne Stufe gibt es nichts zu pruefen");
        }
        return erfuellt(stufe.bedingungen(), gemessen);
    }

    /**
     * Ob diese BEDINGUNGEN mit diesen Messwerten erfuellt sind - ohne Rangstufe.
     *
     * <p><b>Warum es diese Tuer gibt</b> [20.11.2026, Teil G]: Ein Katalogposten im
     * {@link Muenzhaus} haengt an denselben Bedingungen, ist aber keine Rangstufe - er hat
     * keine Zahlen und keine Rechte, und sein Schluessel steht in keiner Rangablage. Ohne
     * diese Tuer blieben drei Wege, und jeder war schlechter: eine gefaelschte Stufe je
     * Posten, eine zweite Vergleichsschleife im Muenzhaus (die dann
     * {@link Bedingungsart#inMesseinheit} selbst rufen muesste - und genau dagegen steht
     * dessen Begruendung), oder zwei Urteile ueber dieselbe Frage.
     *
     * <p>Eine leere Bedingungskarte ist immer erfuellt - das ist der Posten ohne
     * Bedingung und der unterste Rang, und beide sind erlaubt. Eine Art, zu der gar nichts
     * gemessen wurde, zaehlt als 0 und nicht als "egal".
     */
    public static boolean erfuellt(Map<Bedingungsart, Long> verlangt,
                                   Map<Bedingungsart, Long> gemessen) {
        if (verlangt == null) {
            throw new IllegalArgumentException("Ohne Bedingungen gibt es nichts zu"
                    + " pruefen - eine LEERE Karte ist erlaubt, null nicht");
        }
        for (var zeile : verlangt.entrySet()) {
            if (wert(gemessen, zeile.getKey())
                    < zeile.getKey().inMesseinheit(zeile.getValue())) {
                return false;
            }
        }
        return true;
    }

    /**
     * Was dieser Stufe noch fehlt - in der Reihenfolge der Aufzaehlung, damit zwei
     * Anzeigen dieselbe Reihenfolge haben.
     */
    public static List<Bedingungsstand> fehlend(Rangwerte.Stufe stufe,
                                                Map<Bedingungsart, Long> gemessen) {
        if (stufe == null) {
            throw new IllegalArgumentException("Ohne Stufe gibt es nichts zu pruefen");
        }
        return fehlend(stufe.bedingungen(), gemessen);
    }

    /**
     * Was diesen BEDINGUNGEN noch fehlt - ohne Rangstufe, dieselbe Reihenfolge.
     *
     * <p>Die Tuer fuer den Katalogposten, aus demselben Grund wie bei
     * {@link #erfuellt(Map, Map)}. Die Reihenfolge kommt weiter aus der Aufzaehlung und
     * nicht aus der Karte, damit die Tafel des Muenzhauses und die Absage eines Kaufs
     * dieselbe Reihenfolge zeigen wie die Rangleiter.
     */
    public static List<Bedingungsstand> fehlend(Map<Bedingungsart, Long> verlangt,
                                                Map<Bedingungsart, Long> gemessen) {
        // GEFILTERT und nicht zweimal gerechnet: Ein zweiter Durchlauf ueber dieselben
        // Karten waere eine zweite Stelle, an der die Messeinheit umgerechnet wird - und
        // dann sagte eines Tages die Absage etwas anderes als die Auskunft.
        List<Bedingungsstand> offen = new ArrayList<>();
        for (Bedingungsstand eine : stand(verlangt, gemessen)) {
            if (!eine.erfuellt()) {
                offen.add(eine);
            }
        }
        return List.copyOf(offen);
    }

    /**
     * JEDE Bedingung dieser Stufe mit dem Stand daneben - die Tuer fuer {@code /rang}.
     *
     * <p>Dieselbe Zwillingstuer wie bei {@link #erfuellt} und {@link #fehlend}: einmal
     * mit der Stufe, einmal mit ihrer Bedingungskarte.
     */
    public static List<Bedingungsstand> stand(Rangwerte.Stufe stufe,
                                              Map<Bedingungsart, Long> gemessen) {
        if (stufe == null) {
            throw new IllegalArgumentException("Ohne Stufe gibt es nichts zu zeigen");
        }
        return stand(stufe.bedingungen(), gemessen);
    }

    /**
     * Die Rechnung dahinter - EINE Stelle, die die Messeinheit umrechnet.
     *
     * <p>Genannt wird nur, was in der Datei steht: Eine Art ohne Zeile ist keine
     * Bedingung dieser Stufe und gehoert auch nicht in die Auskunft. Die REIHENFOLGE
     * kommt aus {@code Bedingungsart.values()} und nicht aus der Karte - eine HashMap
     * gibt je Lauf eine andere, und dann stuenden die Zeilen von {@code /rang} jedes Mal
     * anders.
     */
    public static List<Bedingungsstand> stand(Map<Bedingungsart, Long> verlangt,
                                              Map<Bedingungsart, Long> gemessen) {
        if (verlangt == null) {
            throw new IllegalArgumentException("Ohne Bedingungen gibt es nichts zu"
                    + " pruefen - eine LEERE Karte ist erlaubt, null nicht");
        }
        List<Bedingungsstand> alle = new ArrayList<>();
        for (Bedingungsart art : Bedingungsart.values()) {
            Long ausDerDatei = verlangt.get(art);
            if (ausDerDatei == null) {
                continue;
            }
            alle.add(new Bedingungsstand(art, art.inMesseinheit(ausDerDatei),
                    wert(gemessen, art)));
        }
        return List.copyOf(alle);
    }

    /**
     * Der hoechste Rang, der mit diesen Messwerten zusteht.
     *
     * <p>Leer, wenn die Leiter leer ist oder abgeschaltet - dann vergibt das Programm
     * keine Rechte, und es gilt, was die {@code config.yml} ohnehin erlaubt.
     */
    public static Optional<Rangwerte.Stufe> zustehend(Rangwerte werte,
                                                      Map<Bedingungsart, Long> gemessen) {
        if (werte == null) {
            throw new IllegalArgumentException("Ohne Werte gibt es keine Leiter");
        }
        if (!werte.greift()) {
            return Optional.empty();
        }
        Rangwerte.Stufe erreicht = null;
        for (Rangwerte.Stufe stufe : werte.leiter()) {
            if (!erfuellt(stufe, gemessen)) {
                // Hier und nicht weiter: Die naechste Sprosse zaehlt nur, wenn diese
                // steht.
                break;
            }
            erreicht = stufe;
        }
        return Optional.ofNullable(erreicht);
    }

    /**
     * Der neue Rang, wenn es einen gibt - sonst leer.
     *
     * <p>Nur nach oben. {@code jetziger} ist der aufgeschriebene Rang; ein Name, den die
     * Leiter nicht mehr kennt (der Betreiber hat die Stufe aus der Datei genommen), zaehlt
     * wie "noch keiner" - dann steigt der Spieler auf das, was er heute erreicht, statt
     * ohne jeden Rang dazustehen.
     */
    public static Optional<Rangwerte.Stufe> aufstieg(Rangwerte werte, String jetziger,
                                                     Map<Bedingungsart, Long> gemessen) {
        Optional<Rangwerte.Stufe> steht = zustehend(werte, gemessen);
        if (steht.isEmpty()) {
            return Optional.empty();
        }
        int bisher = werte.nummer(jetziger);
        int neu = werte.nummer(steht.get().schluessel());
        return neu > bisher ? steht : Optional.empty();
    }

    /**
     * Die naechste Sprosse ueber dem aufgeschriebenen Rang - fuer die Anzeige
     * "so geht es weiter".
     *
     * <p>Gefragt wird nach dem AUFGESCHRIEBENEN Rang und nicht nach dem zustehenden: Wer
     * durch einen Verkauf unter die Bedingung seines eigenen Ranges gefallen ist, soll
     * nicht lesen, er muesse ihn sich noch einmal verdienen.
     */
    public static Optional<Rangwerte.Stufe> naechste(Rangwerte werte, String jetziger) {
        if (werte == null || !werte.greift()) {
            return Optional.empty();
        }
        if (werte.nummer(jetziger) < 0) {
            return werte.unterste();
        }
        return werte.naechste(jetziger);
    }

    /**
     * Die Messwerte, die fuer eine Leiter ueberhaupt gebraucht werden.
     *
     * <p>Wer misst, soll nicht alles messen muessen: Steht in keiner Stufe eine Bedingung
     * auf das Guthaben, braucht niemand die Kasse zu fragen. Das spart im Takt nicht nur
     * Zeit, sondern auch die Abhaengigkeit - eine Messstelle, die nie gefragt wird, darf
     * auch fehlen.
     */
    public static List<Bedingungsart> gebraucht(Rangwerte werte) {
        if (werte == null || !werte.greift()) {
            return List.of();
        }
        Map<Bedingungsart, Boolean> gesehen = new EnumMap<>(Bedingungsart.class);
        for (Rangwerte.Stufe stufe : werte.leiter()) {
            for (Bedingungsart art : stufe.bedingungen().keySet()) {
                gesehen.put(art, Boolean.TRUE);
            }
        }
        List<Bedingungsart> arten = new ArrayList<>();
        for (Bedingungsart art : Bedingungsart.values()) {
            if (gesehen.containsKey(art)) {
                arten.add(art);
            }
        }
        return List.copyOf(arten);
    }

    private static long wert(Map<Bedingungsart, Long> gemessen, Bedingungsart art) {
        if (gemessen == null) {
            return 0L;
        }
        Long hat = gemessen.get(art);
        return hat == null ? 0L : hat;
    }
}
