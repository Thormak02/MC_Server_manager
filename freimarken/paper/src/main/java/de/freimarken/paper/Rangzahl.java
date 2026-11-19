package de.freimarken.paper;

import de.freimarken.kern.Rangwerte;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;
import org.bukkit.entity.Player;
import org.bukkit.permissions.PermissionAttachmentInfo;

/**
 * Eine Obergrenze aus den RECHTEN eines Spielers - die eine Stelle, an der sie entsteht.
 *
 * <p>Ein Rang vergibt {@code freimarken.parzellen.5}, und die Zahl dahinter kennt nur der
 * Betreiber. Solche Knoten sind keine angemeldeten Rechte, sondern WERTE: Das Programm
 * liest sie aus den Rechten des Spielers heraus, statt zu fragen, ob er eines hat. Damit
 * bleiben die Rangzahlen in der Rechteverwaltung und nicht im Programm.
 *
 * <p><b>Warum das eine eigene Klasse ist</b> [18.11.2026]: Bis zu den Meerinseln stand
 * diese Rechnung in {@code ParzellenBefehle} und galt fuer Parzellen und Gruppenfelder.
 * Mit Schritt 10 kam eine dritte Grenze derselben Bauart dazu - wie viele Inseln ein Rang
 * erlaubt -, und zwei Abschriften waeren zwei Orte, an denen der ZUSCHLAG vergessen wird.
 * Genau das ist die Art Fehler, die niemand meldet: Der Spieler sieht nur, dass seine
 * Mitgliedschaft nichts bringt, und haelt es fuer die Regel.
 *
 * <p><b>Und seit Schritt 12 Teil C steht hier auch der Weg HINEIN</b> [19.11.2026]:
 * {@link #knoten} macht aus einem Rangbuendel die Rechtenamen, die {@link #ausRecht}
 * gleich darauf wieder ausliest. Beide Richtungen in EINER Datei, und zwar aus dem
 * Grund, der in diesem Projekt schon mehrfach zugeschlagen hat: Ein Schreiber und ein
 * Leser derselben Gestalt an zwei Stellen laufen eines Tages auseinander - geschrieben
 * wird {@code freimarken.parzelle.2}, gelesen {@code freimarken.parzellen.}, und der
 * Rang wirkt einfach nicht. Rot wird dabei nichts.
 */
final class Rangzahl {

    private Rangzahl() {
    }

    /**
     * Aus einem Rangbuendel die Rechtenamen, die es vergibt.
     *
     * <p>Zwei Gestalten, und beide liest dieselbe Datei wieder:
     * <ul>
     *   <li>eine Grenze wird zu {@code freimarken.<name>.<zahl>} - das ist genau die
     *       Vorsilbe, die {@link #ausRecht} an den vier Grenzen durchsucht;</li>
     *   <li>ein Recht wird zu {@code freimarken.<recht>} - die Vorsilbe kommt vom
     *       Programm, darum verbietet {@code Rangwerte.Stufe} sie in der Datei.</li>
     * </ul>
     *
     * <p><b>Die ZUSCHLAEGE stehen hier nicht</b> und duerfen es nicht: Sie kommen aus der
     * Rechteverwaltung und gehoeren einer Mitgliedschaft, nicht einem Rang. Wer sie hier
     * mit vergaebe, haette sie zweimal - und {@link #mitZuschlag} zaehlte sie doppelt.
     *
     * <p>Ein leeres Buendel gibt eine leere Menge. Das ist der Fall "Raenge sind aus"
     * und der Fall "dieser Server hat gar keine Leiter"; beide sind erlaubt.
     */
    static Set<String> knoten(Rangwerte.Buendel buendel) {
        if (buendel == null) {
            return Set.of();
        }
        // LinkedHashSet und nicht HashSet: Die Reihenfolge steht im Startbericht und in
        // den Proben. Eine Menge ohne Ordnung stuende je Lauf der Maschine anders da.
        var namen = new LinkedHashSet<String>();
        for (var zeile : buendel.zahlen().entrySet()) {
            namen.add(Rangwerte.VORSILBE + zeile.getKey() + "."
                    + zeile.getValue());
        }
        for (String recht : buendel.rechte()) {
            namen.add(Rangwerte.VORSILBE + recht);
        }
        return Collections.unmodifiableSet(namen);
    }

    /**
     * Eine Grenze aus den Rechten: die hoechste erlaubte Zahl plus den Zuschlag.
     *
     * <p>Eine Methode fuer beide Grenzen, damit der Zuschlag nicht an einer von mehreren
     * Stellen vergessen werden kann.
     *
     * @param recht          die Vorsilbe der Grundgrenze, etwa
     *                       {@code freimarken.parzellen.}
     * @param zuschlagsrecht die Vorsilbe des Zuschlags, etwa
     *                       {@code freimarken.extra.parzellen.}
     * @param vorgabe        was ohne jedes Recht gilt - der Wert aus der config.yml
     */
    static int ausRecht(Player spieler, String recht, String zuschlagsrecht,
                        int vorgabe) {
        return mitZuschlag(hoechsteAus(spieler, recht, vorgabe),
                hoechsteAus(spieler, zuschlagsrecht, 0));
    }

    /**
     * Grundlage plus Zuschlag, ohne ueberzulaufen.
     *
     * <p>Beide Zahlen koennen bis {@link Integer#MAX_VALUE} gehen - die Auswertung der
     * Rechte kappt jede einzelne dort. Zusammengezaehlt liefe ein {@code int} ueber und
     * waere danach NEGATIV, und eine negative Obergrenze heisst: gar nichts mehr,
     * ausgerechnet fuer den, der am meisten hat.
     */
    static int mitZuschlag(int grundlage, int zuschlag) {
        return (int) Math.min((long) grundlage + Math.max(zuschlag, 0),
                Integer.MAX_VALUE);
    }

    /**
     * Die hoechste Zahl, die ein Recht dieser Vorsilbe hergibt - oder die Vorgabe.
     *
     * <p>Gelesen werden die WIRKSAMEN Rechte: Ein Spieler kann dieselbe Vorsilbe aus
     * mehreren Gruppen haben, und dann gilt die hoechste Zahl. Was keine Zahl ist
     * ({@code freimarken.parzellen.alle} oder ein Tippfehler), hebt nichts - und wirft
     * auch nicht.
     */
    private static int hoechsteAus(Player spieler, String recht, int vorgabe) {
        int hoechste = vorgabe;
        for (PermissionAttachmentInfo eintrag : spieler.getEffectivePermissions()) {
            if (!eintrag.getValue() || !eintrag.getPermission().startsWith(recht)) {
                continue;
            }
            try {
                // long und dann gekappt: Integer.parseInt haette auch bei einer gueltigen,
                // nur sehr grossen Zahl geworfen - und die waere dann wie ein Tippfehler
                // behandelt worden, also stillschweigend als "keine Erhoehung".
                long gewuenscht = Long.parseLong(
                        eintrag.getPermission().substring(recht.length()));
                hoechste = (int) Math.max(hoechste,
                        Math.min(gewuenscht, Integer.MAX_VALUE));
            } catch (NumberFormatException keineZahl) {
                // Kein Grund fuer einen Abbruch, nur kein Anlass, die Obergrenze zu
                // heben.
            }
        }
        return hoechste;
    }
}
