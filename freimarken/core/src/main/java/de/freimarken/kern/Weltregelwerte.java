package de.freimarken.kern;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Welche Weltregeln in welcher Welt greifen.
 *
 * <p>Dieselbe Bauart wie {@link Blockbuchwerte}, und aus denselben Gruenden: eine Vorgabe,
 * je Welt eine eigene Auswahl, Weisslisten von Namen statt Wahrheitswerten. Die
 * Begruendung fuer die Namen steht an {@link Weltregel}.
 *
 * <p><b>Die Vorgabe ist LEER, und das ist die wichtigste Entscheidung hier.</b> Eine neue
 * Welt bekommt also keinen Schutz, bis der Betreiber ihn eintraegt - genau umgekehrt wie
 * beim Blockbuch, das ab Werk schreibt.
 *
 * <p>Der Grund ist die Art des Schadens, den die falsche Richtung anrichtet: Ein
 * Blockbuch, das zu viel aufschreibt, kostet Platz. Ein Weltschutz, der ungefragt
 * ueberall greift, nimmt dem Spieler das Spiel - in einer Farmwelt ist TNT das Werkzeug,
 * mit dem gearbeitet wird, und in einer Abenteuerwelt gehoert das Feuer dazu. Wer eine
 * neue Welt anlegt, weiss, was sie sein soll; wer sie geschuetzt haben will, traegt es
 * ein, und die ausgelieferte {@code config.yml} tut das fuer die Marktstadt.
 *
 * <p><b>Eine LEERE Auswahl ist erlaubt und heisst etwas</b>: In dieser Welt gilt keine
 * Regel. Das ist kein Rueckfall auf die Vorgabe - wer eine leere Liste eintraegt, meint
 * nicht die Vorgabe, sondern "hier nicht". Dieselbe Entscheidung wie bei der
 * Blockbuchauswahl und bei der Saatgutliste.
 */
public final class Weltregelwerte {

    /** Die Vorgabe: welche Regeln eine Welt hat, die nichts eigenes sagt. */
    public static final String PFAD_REGELN = "weltregeln.regeln";

    /** Darunter je Welt eine eigene Liste. */
    public static final String PFAD_WELTEN = "weltregeln.welten";

    /**
     * KEINE Regel ab Werk - siehe den Kopf der Klasse.
     *
     * <p>Die ausgelieferte {@code config.yml} traegt sie fuer die Marktstadt ein; eine
     * Welt, die niemand nennt, bleibt ungeschuetzt. Das ist der Unterschied zwischen
     * einer Einstellung, die der Betreiber trifft, und einer, die ihm das Programm
     * aufdraengt.
     */
    public static final Set<Weltregel> VORGABE_REGELN = Set.of();

    private final Set<Weltregel> vorgabe;
    private final Map<String, Set<Weltregel>> jeWelt;

    /**
     * @param vorgabe was eine Welt ohne eigene Liste hat
     * @param jeWelt  die Welten mit eigener Auswahl, nach WELTSCHLUESSEL
     *                ({@code freimarken:marktstadt}) - nicht nach Namen, denn der
     *                Vergleich im Ereignis fragt {@code World.key().asString()}
     */
    public Weltregelwerte(Set<Weltregel> vorgabe, Map<String, Set<Weltregel>> jeWelt) {
        if (vorgabe == null) {
            throw new IllegalArgumentException("Nichts ist keine Vorgabe - eine LEERE"
                    + " Menge ist erlaubt und heisst \"keine Regel\"");
        }
        if (jeWelt == null) {
            throw new IllegalArgumentException("Nichts ist keine Karte von Welten - eine"
                    + " LEERE ist erlaubt und heisst \"ueberall die Vorgabe\"");
        }
        this.vorgabe = unveraenderlich(vorgabe);
        Map<String, Set<Weltregel>> sauber = new LinkedHashMap<>();
        for (Map.Entry<String, Set<Weltregel>> eintrag : jeWelt.entrySet()) {
            if (eintrag.getKey() == null || eintrag.getKey().isBlank()) {
                throw new IllegalArgumentException("Eine Auswahl ohne Welt");
            }
            if (eintrag.getValue() == null) {
                throw new IllegalArgumentException("Die Welt \"" + eintrag.getKey()
                        + "\" hat keine Auswahl - fuer \"nichts\" steht die leere Liste");
            }
            String schluessel = eintrag.getKey().trim().toLowerCase(Locale.ROOT);
            // EIN SCHLUESSEL OHNE NAMENSRAUM IST KEIN SCHLUESSEL, und dieser Wurf ist
            // derselbe Wall wie bei Blockbuchwerte - dort steht die lange Begruendung.
            // Kurz: Die config.yml schreibt "marktstadt", die Welt traegt
            // "freimarken:marktstadt", und wer den rohen Namen hierher gibt, baut eine
            // Karte, die im Ereignis NIE trifft. Nichts bricht, nichts wird rot; der
            // Schutz bliebe fuer diese Welt einfach aus, und niemand koennte sagen,
            // warum. Bei einem SCHUTZ ist das der teuerste aller stillen Fehler.
            if (schluessel.indexOf(':') < 0) {
                throw new IllegalArgumentException("\"" + schluessel + "\" ist ein"
                        + " WeltNAME und kein Weltschluessel - erwartet wird die Form"
                        + " \"namensraum:name\", wie World.key().asString() sie liefert"
                        + " (etwa \"freimarken:" + schluessel + "\"). Eine Karte nach"
                        + " Namen trifft im Ereignis NIE, und der Schutz bliebe fuer"
                        + " diese Welt stumm aus.");
            }
            sauber.put(schluessel, unveraenderlich(eintrag.getValue()));
        }
        this.jeWelt = Collections.unmodifiableMap(sauber);
    }

    /**
     * Eine unveraenderliche Kopie als {@code EnumSet}.
     *
     * <p>{@code addAll} und NICHT {@code EnumSet.copyOf}: Jenes wirft bei einer leeren
     * Sammlung, die selbst kein {@code EnumSet} ist ("Collection is empty") - und die
     * leere Auswahl ist hier der erlaubte Fall, der "hier keine Regel" heisst. Ein Wurf
     * traefe genau den Betreiber, der eine Welt bewusst ausnimmt. Dieselbe Falle wie bei
     * {@code Blockbuchwerte.Auswahl}.
     */
    private static Set<Weltregel> unveraenderlich(Set<Weltregel> roh) {
        Set<Weltregel> kopie = EnumSet.noneOf(Weltregel.class);
        kopie.addAll(roh);
        return Collections.unmodifiableSet(kopie);
    }

    /** Die Werte, die ohne eine Zeile in der {@code config.yml} gelten. */
    public static final Weltregelwerte VORGABE =
            new Weltregelwerte(VORGABE_REGELN, Map.of());

    /**
     * Welche Regeln in dieser Welt gelten.
     *
     * <p>Eine Welt ohne eigene Zeile bekommt die Vorgabe - und die ist leer. Eine neue
     * Welt steht damit ohne Schutz da, bis sie eingetragen wird; siehe den Kopf.
     *
     * @param weltschluessel wie {@code World.key().asString()} ihn liefert
     */
    public Set<Weltregel> fuerWelt(String weltschluessel) {
        if (weltschluessel == null) {
            return vorgabe;
        }
        return jeWelt.getOrDefault(weltschluessel.trim().toLowerCase(Locale.ROOT),
                vorgabe);
    }

    /**
     * Greift diese Regel in dieser Welt?
     *
     * <p><b>Die EINE Frage, die jeder Zuhoerer stellt</b> - ein Aufruf und keine zwei.
     * So liegt die Vererbung ("keine eigene Liste heisst Vorgabe") an derselben Stelle
     * wie die Mitgliedschaft in der Auswahl. Zwei getrennte Fragen waeren zwei
     * Verzweigungen an jedem der acht Ereigniszweige - und beim neunten vergessen.
     */
    public boolean greift(String weltschluessel, Weltregel regel) {
        return regel != null && fuerWelt(weltschluessel).contains(regel);
    }

    /** Ob irgendwo irgendeine Regel greift - fuer den Bericht beim Start. */
    public boolean greiftIrgendwo() {
        if (!vorgabe.isEmpty()) {
            return true;
        }
        for (Set<Weltregel> auswahl : jeWelt.values()) {
            if (!auswahl.isEmpty()) {
                return true;
            }
        }
        return false;
    }

    /** Was eine Welt ohne eigene Liste hat. */
    public Set<Weltregel> vorgabe() {
        return vorgabe;
    }

    /** Die Welten mit eigener Auswahl, nach Weltschluessel. */
    public Map<String, Set<Weltregel>> jeWelt() {
        return jeWelt;
    }

    /**
     * Wie eine Auswahl in der Datei stuende - fuer den Bericht beim Start.
     *
     * <p>Damit der Betreiber im Protokoll sieht, was WIRKLICH gilt, und nicht nur, was
     * er glaubt eingetragen zu haben. Aus den Daten gebaut, nicht abgeschrieben - und in
     * der Reihenfolge der Aufzaehlung, damit zwei Starts sich vergleichen lassen.
     */
    public static String wieEingetragen(Set<Weltregel> auswahl) {
        List<String> teile = new ArrayList<>();
        for (Weltregel regel : Weltregel.values()) {
            if (auswahl.contains(regel)) {
                teile.add(regel.schluessel());
            }
        }
        return teile.isEmpty() ? "[]" : "[" + String.join(", ", teile) + "]";
    }
}
