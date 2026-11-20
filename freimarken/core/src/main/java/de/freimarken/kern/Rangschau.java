package de.freimarken.kern;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Was ein Spieler ueber seinen Rang wissen will - an EINER Stelle zusammengestellt.
 *
 * <p><b>Warum es diese Klasse gibt:</b> {@code /rang} braucht fuenf Dinge, die in vier
 * verschiedenen Ecken liegen - die abgelegte Stufe, die Leiter aus der Einstellungsdatei,
 * die naechste Stufe, jede ihrer Bedingungen mit dem Stand daneben und die ganze Leiter
 * aus der Sicht dieses Spielers. Im Befehl zusammengesucht waere jede dieser Fragen eine
 * Stelle, die ohne Server nicht pruefbar ist; hier ist es eine Rechnung ohne Bukkit.
 *
 * <p><b>Sie rechnet und urteilt NICHT selbst</b>: Ob eine Bedingung erfuellt ist,
 * entscheidet {@link Rangregel}, und gemessen wird ueber die {@link Bedingungsmessung}.
 * Ein eigenes Urteil hier waere ein zweites - und dann zeigte {@code /rang} einen
 * Aufstieg, den der Aufstiegslauf nicht gibt.
 *
 * <p><b>Gemessen wird nur, was die NAECHSTE Stufe verlangt.</b> Jede Messung ausser dem
 * Beruf ist eine Datenbankabfrage im Hauptfaden; wer auf der hoechsten Stufe steht,
 * loest mit {@code /rang} keine einzige aus.
 */
public final class Rangschau {

    /**
     * Eine Stufe der Leiter aus der Sicht EINES Spielers.
     *
     * @param erreicht ob er sie hat - seine eigene Stufe zaehlt dazu
     * @param jetzt    ob er genau hier steht
     */
    public record Sprosse(Rangwerte.Stufe stufe, boolean erreicht, boolean jetzt) {
    }

    /**
     * Die ganze Auskunft.
     *
     * @param jetzt              die Stufe, auf der er steht - nie {@code null}, denn
     *                           die unterste gilt auch fuer den, von dem die Ablage
     *                           nichts weiss
     * @param unbekannterEintrag der Schluessel AUS DER ABLAGE, wenn die Leiter ihn nicht
     *                           mehr kennt; sonst leer. Dann zeigt {@code jetzt} die
     *                           unterste Stufe, und das darf nicht wie ein normaler
     *                           Anfang aussehen - sonst meldet es niemand. EIN Feld und
     *                           nicht ein Wahrheitswert neben dem Schluessel: Zwei
     *                           Felder koennten sich widersprechen, und die Zeile fuer
     *                           den Spieler braucht den Schluessel selbst
     * @param naechste           die Stufe darueber, oder leer auf der hoechsten
     * @param stand              JEDE Bedingung der naechsten Stufe mit dem Stand
     *                           daneben; leer, wenn es keine naechste gibt
     * @param leiter             die ganze Leiter von unten nach oben
     */
    public record Tafel(Rangwerte.Stufe jetzt, Optional<String> unbekannterEintrag,
                        Optional<Rangwerte.Stufe> naechste,
                        List<Rangregel.Bedingungsstand> stand,
                        List<Sprosse> leiter) {
    }

    private final Rangablage ablage;
    private final Wirtschaftsstand wirtschaft;
    private final Bedingungsmessung messung;

    public Rangschau(Rangablage ablage, Wirtschaftsstand wirtschaft,
                     Bedingungsmessung messung) {
        if (ablage == null || wirtschaft == null || messung == null) {
            throw new IllegalArgumentException("Die Rangschau braucht Ablage,"
                    + " Wirtschaftsstand und Bedingungsmessung");
        }
        this.ablage = ablage;
        this.wirtschaft = wirtschaft;
        this.messung = messung;
    }

    /**
     * Die Tafel fuer diesen Spieler - oder LEER, wenn es keine Rangleiter gibt.
     *
     * <p>Die Leiter kommt bei JEDEM Aufruf frisch aus dem Wirtschaftsstand und nicht aus
     * einem Feld: {@code /freimarken neuladen} tauscht sie aus, und ein gemerkter Stand
     * nannte danach Stufen, die es nicht mehr gibt.
     *
     * <p>Wirft weiter, wenn die Ablage schweigt - der Befehl sagt es dem Spieler. Ein
     * Rueckfall auf die unterste Stufe waere die schlechteste Antwort: Sie sieht aus wie
     * eine Auskunft und ist eine Vermutung.
     */
    public Optional<Tafel> tafel(UUID wer) {
        Rangwerte werte = wirtschaft.werte().raenge();
        if (!werte.greift()) {
            return Optional.empty();
        }
        Rangstand gespeichert = ablage.lies(wer);
        Rangwerte.Stufe jetzt = gespeichert.stufeIn(werte);
        Optional<Rangwerte.Stufe> naechste = Rangregel.naechste(werte, jetzt.schluessel());
        List<Rangregel.Bedingungsstand> stand = naechste
                .map(stufe -> Rangregel.stand(stufe,
                        messung.fuer(wer, arten(stufe))))
                .orElseGet(List::of);
        Optional<String> unbekannt = gespeichert.bekanntIn(werte)
                ? Optional.empty()
                : Optional.ofNullable(gespeichert.rang());
        return Optional.of(new Tafel(jetzt, unbekannt, naechste, stand,
                leiter(werte, jetzt)));
    }

    /**
     * Die Arten, die GENAU DIESE Stufe verlangt - in der Reihenfolge der Aufzaehlung.
     *
     * <p>Nicht {@link Rangregel#gebraucht}: Jenes nennt alles, was die ganze Leiter
     * irgendwo verlangt, und waere hier eine Abfrage zu viel. Die Reihenfolge kommt aus
     * {@code Bedingungsart.values()}, damit zwei Laeufe dieselbe Liste geben.
     */
    private static List<Bedingungsart> arten(Rangwerte.Stufe stufe) {
        List<Bedingungsart> arten = new ArrayList<>();
        for (Bedingungsart art : Bedingungsart.values()) {
            if (stufe.bedingungen().containsKey(art)) {
                arten.add(art);
            }
        }
        return List.copyOf(arten);
    }

    /**
     * Die Leiter von unten nach oben, je Stufe mit zwei Wahrheitswerten.
     *
     * <p>ERREICHT heisst "an oder unter meiner Stufe" und wird aus der NUMMER gerechnet,
     * nicht aus den Bedingungen: Wer befoerdert wurde, behaelt seinen Rang auch dann,
     * wenn der Betreiber die Bedingungen danach heraufsetzt. Die Leiter zeigt, wo er
     * steht, und nicht, was er heute wieder schaffen wuerde.
     */
    private static List<Sprosse> leiter(Rangwerte werte, Rangwerte.Stufe jetzt) {
        int meine = werte.nummer(jetzt.schluessel());
        List<Sprosse> sprossen = new ArrayList<>();
        for (Rangwerte.Stufe stufe : werte.leiter()) {
            int nummer = werte.nummer(stufe.schluessel());
            sprossen.add(new Sprosse(stufe, nummer <= meine, nummer == meine));
        }
        return List.copyOf(sprossen);
    }
}
