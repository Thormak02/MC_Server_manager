package de.freimarken.kern;

import java.util.Set;

/**
 * Wie die Rechte heissen, die {@link Strafbuch} fragt (Schritt 16a Teil B).
 *
 * <p><b>Die NAMEN kommen von aussen, die Riegel bleiben im Kern</b>, und das ist eine
 * Messung und keine Vorliebe: {@code GrenzeTest} 31al liest die
 * {@code "freimarken.*"}-Literale des <i>Plugin</i>-Moduls und haelt sie gegen die
 * {@code plugin.yml}. Ein Literal im Kern sieht sie NICHT - und dann steht in der
 * plugin.yml ein angemeldetes Recht, das nach ihrer Rechnung kein Zweig fragt. Gemessen
 * am 25.11.2026 beim Meldewesen, genau mit diesem Fehlerbild.
 *
 * <p><b>Sechs Namen und nicht ein zusammengesetzter.</b> Die Versuchung ist,
 * {@code wurzel + "." + art.kuerzel()} zu rechnen - das waere ein Name weniger und die
 * Tafel waere blind: Ein zusammengesetztes Recht steht nirgends als Literal, also meldet
 * sie jedes davon als "angemeldet, und kein Zweig fragt es". Dieselbe Falle, eine Ebene
 * tiefer.
 *
 * @param stumm     stummschalten
 * @param rauswurf  hinauswerfen
 * @param bann      sperren
 * @param dauerhaft laenger als die Hoechstdauer oder ganz ohne Ende - nach Plan 12c.2 der
 *                  Unterschied zwischen Moderator und Verwalter
 * @param aufheben  eine Strafe zuruecknehmen
 * @param team      jemanden strafen, der selbst im Team ist
 */
public record Strafrechte(String stumm, String rauswurf, String bann, String dauerhaft,
                          String aufheben, String team) {

    public Strafrechte {
        pruefe(stumm, "stumm");
        pruefe(rauswurf, "rauswurf");
        pruefe(bann, "bann");
        pruefe(dauerhaft, "dauerhaft");
        pruefe(aufheben, "aufheben");
        pruefe(team, "team");
    }

    private static void pruefe(String name, String wofuer) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Der Name des Rechtes fuer \"" + wofuer
                    + "\" fehlt. Ohne ihn waere der Riegel stumm immer zu - keine"
                    + " Rechtemenge enthaelt einen leeren Namen");
        }
    }

    /**
     * Das Recht fuer diese Art.
     *
     * <p>Ein {@code switch} ohne {@code default} ueber die Aufzaehlung: So wird eine
     * vierte Art ein Uebersetzungsfehler und keine stille Luecke, durch die jeder
     * strafen darf.
     */
    public String fuer(Strafart art) {
        if (art == null) {
            throw new IllegalArgumentException("Ohne Art gibt es kein Recht");
        }
        return switch (art) {
            case STUMM -> stumm;
            case RAUSWURF -> rauswurf;
            case BANN -> bann;
        };
    }

    /**
     * Ob diese Rechtemenge ueberhaupt eine Strafe verhaengen darf.
     *
     * <p>Fuer die Frage, ob {@code /strafe} dem Leser angezeigt wird, und fuer die
     * Entscheidung, ob jemand selbst als Teammitglied geschuetzt ist.
     *
     * <p>{@code null} heisst DIE KONSOLE und darf alles - dieselbe Festlegung wie bei
     * {@link Teambuch} und {@link Meldebuch}, und aus demselben Grund: Wer an der Konsole
     * steht, hat Dateizugriff. Eine LEERE Menge ist etwas anderes, naemlich ein Spieler
     * ohne jedes Recht.
     */
    public boolean darfUeberhauptStrafen(Set<String> rechte) {
        if (rechte == null) {
            return true;
        }
        for (Strafart art : Strafart.values()) {
            if (rechte.contains(fuer(art))) {
                return true;
            }
        }
        return false;
    }
}
