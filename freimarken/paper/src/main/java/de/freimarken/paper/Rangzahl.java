package de.freimarken.paper;

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
 */
final class Rangzahl {

    private Rangzahl() {
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
