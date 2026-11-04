package de.freimarken.paper;

import de.freimarken.kern.Buchungsgrund;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Das eine Verzeichnis aller Saetze - die Entsprechung zu {@code Wirtschaftsliste.SCHLUESSEL}.
 *
 * <p>Sieben Bereichsaufzaehlungen, EINE Liste. Die Liste der Bereiche steht einmal und
 * wird zweimal gebraucht: fuer {@link #alle()} und fuer die Probe, die nachsieht, ob ein
 * Bauschritt seinen Bereich auch angemeldet hat. Gab es sie zweimal, koennte ein Bereich in
 * der einen stehen und in der anderen fehlen.
 *
 * <p>Die Familienfunktionen ({@link #grund}) stehen hier und nicht am Sendeort. Das ist
 * derselbe Grund wie bei {@code Wirtschaftsstand.NUR_BEIM_START}: Die Zuordnung gehoert
 * dorthin, wo sie entschieden wird, nicht in jeden Zweig, der sie braucht.
 */
final class Sprachschluessel {

    /**
     * Die sieben Bereiche, in der Reihenfolge der Bauschritte.
     *
     * <p>Als {@code Class} und nicht als {@code values()}: So braucht ein noch leerer
     * Bereich keinen Platzhalterschluessel, und die Probe kann trotzdem nachsehen, dass
     * alle sieben gefuehrt werden.
     */
    private static final List<Class<? extends Satz>> BEREICHE = List.of(
            BundSatz.class, MarkSatz.class, ParzellenSatz.class, AnfragenSatz.class,
            SpielzeitSatz.class, ReiseSatz.class, WacheSatz.class);

    /** Alle Saetze, aus allen sieben Bereichen - einmal gebaut, unveraenderlich. */
    private static final List<Satz> ALLE = sammle();

    /** Jeder Pfad der Datei auf seinen Satz - auch {@code .eins} und {@code .viele}. */
    private static final Map<String, Satz> NACH_PFAD = verzeichne();

    private Sprachschluessel() {
    }

    private static List<Satz> sammle() {
        List<Satz> alle = new ArrayList<>();
        for (Class<? extends Satz> bereich : BEREICHE) {
            Satz[] saetze = bereich.getEnumConstants();
            if (saetze == null) {
                throw new IllegalStateException(bereich.getName()
                        + " ist keine Aufzaehlung - ein Bereich muss values() haben,"
                        + " damit man keinen Satz vergessen kann.");
            }
            alle.addAll(List.of(saetze));
        }
        return List.copyOf(alle);
    }

    private static Map<String, Satz> verzeichne() {
        Map<String, Satz> nachPfad = new LinkedHashMap<>();
        for (Satz satz : ALLE) {
            for (String pfad : satz.pfade()) {
                Satz vorher = nachPfad.put(pfad, satz);
                if (vorher != null) {
                    throw new IllegalStateException(pfad + " steht zweimal im Verzeichnis:"
                            + " " + vorher + " und " + satz + ". Zwei Saetze mit demselben"
                            + " Pfad wuerden einander stumm ueberschreiben.");
                }
            }
            if (satz.art() == Satz.Art.GEZAEHLT) {
                // Der Grundpfad selbst ist kein Blatt, aber zuPfad soll ihn kennen - sonst
                // meldete der Leser den Elternknoten als ueberzaehlig.
                nachPfad.putIfAbsent(satz.pfad(), satz);
            }
        }
        return Map.copyOf(nachPfad);
    }

    /** Alle Saetze, aus allen sieben Bereichen - die EINE Liste. */
    static List<Satz> alle() {
        return ALLE;
    }

    /** Die sieben Bereiche - fuer die Probe, die ihre Vollstaendigkeit zusichert. */
    static List<Class<? extends Satz>> bereiche() {
        return BEREICHE;
    }

    /** Der Satz zu einem Pfad aus der Datei, oder {@code null} fuer einen unbekannten. */
    static Satz zuPfad(String pfad) {
        return NACH_PFAD.get(pfad);
    }

    /** Alle Pfade, die in der Datei stehen duerfen - mit {@code .eins}/{@code .viele}. */
    static List<String> allePfade() {
        List<String> pfade = new ArrayList<>();
        for (Satz satz : ALLE) {
            pfade.addAll(satz.pfade());
        }
        return List.copyOf(pfade);
    }

    /**
     * Statt des {@code switch} in {@code Verlaufszeile:73-79} - das Wort zu einem
     * Buchungsgrund.
     *
     * <p>Der Pfad wird aus dem Aufzaehlungsnamen gebildet ({@link MarkSatz#grundpfad}),
     * nicht aus {@code Buchungsgrund.schluessel()}. Wirft, wenn dazu kein Satz steht -
     * ein fehlender Satz ist ein Bauunfall und kein Betriebsfall, und
     * {@code SendewegTest.grundIstVollstaendig} faengt ihn beim Bauen.
     */
    static Satz grund(Buchungsgrund grund) {
        String pfad = MarkSatz.grundpfad(grund);
        Satz satz = zuPfad(pfad);
        if (satz == null) {
            throw new IllegalStateException("Zu " + grund + " gehoert der Satz " + pfad
                    + ", und den gibt es nicht. Entweder fehlt er in MarkSatz, oder ein"
                    + " neuer Buchungsgrund wurde ohne Wort angelegt.");
        }
        return satz;
    }

    /**
     * Der aehnlichste bekannte Pfad zu einem unbekannten - fuer "meintest du ...?".
     *
     * <p>Die Levenshtein-Abstandsrechnung, und zwar auf dem GANZEN Pfad: Ein Tippfehler
     * steckt genauso oft in der Ebene wie im letzten Wort. Es gibt immer eine Antwort,
     * auch eine schlechte - sie steht in einem Bericht und nicht in einer Klage, also
     * kostet eine schlechte Antwort nichts.
     *
     * @return {@code null}, wenn es ueberhaupt keinen Pfad gibt
     */
    static String aehnlichster(String unbekannt) {
        String besser = null;
        int abstand = Integer.MAX_VALUE;
        for (String pfad : allePfade()) {
            int jetzt = abstand(unbekannt, pfad);
            if (jetzt < abstand) {
                abstand = jetzt;
                besser = pfad;
            }
        }
        return besser;
    }

    /** Levenshtein, mit zwei Zeilen statt einer Tabelle. */
    private static int abstand(String a, String b) {
        int[] vorher = new int[b.length() + 1];
        int[] jetzt = new int[b.length() + 1];
        for (int j = 0; j <= b.length(); j++) {
            vorher[j] = j;
        }
        for (int i = 1; i <= a.length(); i++) {
            jetzt[0] = i;
            for (int j = 1; j <= b.length(); j++) {
                int kosten = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
                jetzt[j] = Math.min(Math.min(jetzt[j - 1] + 1, vorher[j] + 1),
                        vorher[j - 1] + kosten);
            }
            int[] tausch = vorher;
            vorher = jetzt;
            jetzt = tausch;
        }
        return vorher[b.length()];
    }

    /**
     * Die Pfade, deren Deckung eine FAMILIENPROBE traegt und keine Textsuche.
     *
     * <p>Diese Saetze werden nirgends namentlich getippt: Ihr Pfad entsteht mechanisch aus
     * einem Aufzaehlungsnamen ({@link #grund}). Eine Textsuche nach dem Namen der
     * Konstante fand sie darum nur in {@code Sprachschluessel.java} selbst - und ein
     * Schluessel, der sich selbst bescheinigt, bescheinigt nichts.
     *
     * <p>{@code GrenzeTest.jederSchluesselWirdGesendet} vergleicht diese Menge mit
     * {@code assertEquals} gegen die tatsaechlich nur hier gefundenen Schluessel. Wird ein
     * weiterer Satz selbstbescheinigend, ist die Probe rot; bekommt einer von diesen eine
     * Nennung draussen, ebenfalls. Was sie wirklich deckt, ist
     * {@code SendewegTest} - dort wird jeder Buchungsgrund durch die echte Zeile geschickt.
     */
    static List<String> familienDeckung() {
        List<String> pfade = new ArrayList<>();
        for (Buchungsgrund grund : Buchungsgrund.values()) {
            pfade.add(MarkSatz.grundpfad(grund));
        }
        return List.copyOf(pfade);
    }

    /** Nur fuer die Proben: ein Verzeichnis aus einer eigenen Satzliste. */
    static Map<String, Satz> verzeichnisVon(List<Satz> saetze) {
        Map<String, Satz> nachPfad = new HashMap<>();
        for (Satz satz : saetze) {
            for (String pfad : satz.pfade()) {
                nachPfad.put(pfad, satz);
            }
            if (satz.art() == Satz.Art.GEZAEHLT) {
                nachPfad.putIfAbsent(satz.pfad(), satz);
            }
        }
        return nachPfad;
    }
}
