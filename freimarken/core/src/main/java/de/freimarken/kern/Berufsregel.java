package de.freimarken.kern;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

/**
 * Wer welchen Beruf waehlen und ablegen darf - die Regel, ohne Ablage und ohne Spieler.
 *
 * <p>Alle Groessen stehen in der Signatur: Verzeichnis, Werte, Stand, Guthaben und
 * {@code jetzt}. Dieselbe Bauart wie {@link Spielzeitregel} und aus demselben Grund -
 * eine Regel, die sich ihre Zahlen selbst holt, laesst sich nur mit allem drumherum
 * pruefen.
 *
 * <p><b>Die Reihenfolge der Faelle ist die Sache selbst</b>, nicht Geschmack: Jeder Fall
 * steht dort, wo seine Meldung dem Spieler am meisten sagt, und jede Stufe nennt ihren
 * Grund im Kommentar.
 */
public final class Berufsregel {

    private Berufsregel() {
    }

    /** Wie ein Antrag ausgeht. */
    public enum Ausgang {

        /** Alles in Ordnung - der Zug darf laufen. */
        ANGENOMMEN,

        /** Diesen Beruf gibt es in der Einstellungsdatei nicht. */
        UNBEKANNTER_BERUF,

        /** Er haelt ihn schon. */
        SCHON_GEHALTEN,

        /** Er haelt ihn nicht - es gibt nichts abzulegen. */
        NICHT_GEHALTEN,

        /** Alle Plaetze fuer Grundberufe sind besetzt. */
        GRUNDBERUFE_VOLL,

        /** Alle Plaetze fuer Spezialisierungen sind besetzt. */
        SPEZIALISIERUNG_VOLL,

        /** Ihm fehlt das Recht, ueberhaupt eine Spezialisierung zu halten. */
        SPEZIALISIERUNG_OHNE_RECHT,

        /** Ihm fehlt der Grundberuf, den diese Spezialisierung voraussetzt. */
        GRUNDBERUF_FEHLT,

        /**
         * Er haelt die Spezialisierung dieses Grundberufes - der Grundberuf bleibt.
         *
         * <p>Sonst haengt die Spezialisierung in der Luft und verdient in Teil E ohne
         * Grundlage.
         */
        TRAEGT_SPEZIALISIERUNG,

        /** Die Sperrfrist seit dem letzten Ablegen laeuft noch. */
        SPERRFRIST,

        /** Das Guthaben reicht nicht fuer die Gebuehr. */
        GELD_FEHLT
    }

    /**
     * Das Urteil.
     *
     * @param ausgang   wie es ausgeht
     * @param beruf     der aufgeloeste Beruf, oder die rohe Eingabe bei
     *                  {@link Ausgang#UNBEKANNTER_BERUF}
     * @param kosten    was der Zug kostet - beim Waehlen immer 0, beim Ablegen die Gebuehr
     * @param restfrist wie lange noch zu warten ist; {@link Duration#ZERO}, wenn nicht
     * @param erlaubt   die Zahl, die der Satz einsetzt - die Platzgrenze, sonst 0
     */
    public record Urteil(Ausgang ausgang, String beruf, long kosten, Duration restfrist,
                         int erlaubt) {

        public Urteil {
            if (ausgang == null || beruf == null || restfrist == null) {
                throw new IllegalArgumentException("Ausgang, Beruf und Restfrist sind"
                        + " Pflicht");
            }
        }

        public boolean angenommen() {
            return ausgang == Ausgang.ANGENOMMEN;
        }

        public boolean kostenpflichtig() {
            return kosten > 0L;
        }
    }

    /**
     * Darf er diesen Beruf waehlen?
     *
     * <p>Waehlen ist immer kostenlos: Bezahlt wird das VERLASSEN. Ein freier Platz kostet
     * nichts, und ein Neuling zahlt fuer seine ersten drei Berufe nichts. Andersherum
     * waere es umgehbar - erst aufgeben, einen Tag warten, dann gratis etwas anderes
     * waehlen -, und beide Zahlen wuerden nur noch bremsen statt zu bremsen.
     *
     * @param darfSpezialisieren ob er das Recht {@code freimarken.beruf.spezialisierung}
     *                           hat. Eine Rangzahl wird nicht gefragt: Raenge sind auf
     *                           diesem Server Rechte, und eine zweite Quelle dafuer waere
     *                           eine zweite Wahrheit
     */
    public static Urteil pruefeWahl(Berufsverzeichnis verzeichnis, Berufswerte werte,
                                    Berufsstand stand, String gewuenscht,
                                    boolean darfSpezialisieren, Instant jetzt) {
        pflichten(verzeichnis, werte, stand, jetzt);
        // Zuerst: Gibt es den Beruf ueberhaupt? Jede weitere Pruefung urteilte sonst
        // ueber einen Beruf, den es nicht gibt, und die Meldung nennte eine Regel statt
        // des Tippfehlers.
        Optional<String> aufgeloest = verzeichnis.aufgeloest(gewuenscht);
        if (aufgeloest.isEmpty()) {
            return absage(Ausgang.UNBEKANNTER_BERUF, roh(gewuenscht));
        }
        String beruf = aufgeloest.get();
        if (stand.haelt(beruf)) {
            return absage(Ausgang.SCHON_GEHALTEN, beruf);
        }
        boolean spezialisierung = verzeichnis.istSpezialisierung(beruf);
        if (spezialisierung) {
            // Das Recht VOR der Beziehung: Wer es nicht hat, soll nicht zuerst lernen,
            // welchen Grundberuf er braeuchte. Das Recht ist die Schranke, nicht die
            // Beziehung.
            if (!darfSpezialisieren) {
                return absage(Ausgang.SPEZIALISIERUNG_OHNE_RECHT, beruf);
            }
            Optional<String> gebraucht = verzeichnis.grundberufVon(beruf);
            if (gebraucht.isPresent() && !stand.haelt(gebraucht.get())) {
                return absage(Ausgang.GRUNDBERUF_FEHLT, gebraucht.get());
            }
        }
        // Die Sperrfrist VOR den Plaetzen: Sie gilt fuer jeden Zug und ist der Grund, aus
        // dem der Spieler jetzt gar nichts tun kann. Ein voller Platz liesse sich durch
        // Ablegen loesen - nur eben nicht, solange die Frist laeuft.
        Duration rest = restfrist(werte, stand, jetzt);
        if (!rest.isZero()) {
            return new Urteil(Ausgang.SPERRFRIST, beruf, 0L, rest, 0);
        }
        int belegt = belegte(verzeichnis, stand, spezialisierung);
        int plaetze = spezialisierung ? werte.spezialisierungen() : werte.grundberufe();
        if (belegt >= plaetze) {
            return new Urteil(spezialisierung ? Ausgang.SPEZIALISIERUNG_VOLL
                    : Ausgang.GRUNDBERUFE_VOLL, beruf, 0L, Duration.ZERO, plaetze);
        }
        return new Urteil(Ausgang.ANGENOMMEN, beruf, 0L, Duration.ZERO, plaetze);
    }

    /**
     * Darf er diesen Beruf ablegen - und was kostet es?
     *
     * @param guthaben was in seiner Kasse steht; die Gebuehr wird daran geprueft, aber
     *                 nicht hier abgebucht
     */
    public static Urteil pruefeVerlassen(Berufsverzeichnis verzeichnis, Berufswerte werte,
                                         Berufsstand stand, String abzulegen,
                                         long guthaben, Instant jetzt) {
        pflichten(verzeichnis, werte, stand, jetzt);
        Optional<String> aufgeloest = verzeichnis.aufgeloest(abzulegen);
        if (aufgeloest.isEmpty()) {
            return absage(Ausgang.UNBEKANNTER_BERUF, roh(abzulegen));
        }
        String beruf = aufgeloest.get();
        if (!stand.haelt(beruf)) {
            return absage(Ausgang.NICHT_GEHALTEN, beruf);
        }
        // Ein Grundberuf, dessen Spezialisierung er haelt, bleibt. Sonst haengt sie in
        // der Luft, und in Teil E verdient sie ohne Grundlage. Die ABLAGE kann das nicht
        // fangen: Sie kennt die Rollen nicht und darf sie nicht kennen.
        for (String spezialisierung : verzeichnis.spezialisierungenZu(beruf)) {
            if (stand.haelt(spezialisierung)) {
                return absage(Ausgang.TRAEGT_SPEZIALISIERUNG, spezialisierung);
            }
        }
        // Die Frist VOR dem Geld: Geld laesst sich jetzt verdienen, die Frist nicht. Wer
        // beides nicht hat, soll die Zahl erfahren, die er nicht aendern kann.
        Duration rest = restfrist(werte, stand, jetzt);
        if (!rest.isZero()) {
            return new Urteil(Ausgang.SPERRFRIST, beruf, werte.wechselgebuehr(), rest, 0);
        }
        if (guthaben < werte.wechselgebuehr()) {
            return new Urteil(Ausgang.GELD_FEHLT, beruf, werte.wechselgebuehr(),
                    Duration.ZERO, 0);
        }
        return new Urteil(Ausgang.ANGENOMMEN, beruf, werte.wechselgebuehr(),
                Duration.ZERO, 0);
    }

    /**
     * Was von der Sperrfrist noch uebrig ist.
     *
     * <p>Oeffentlich, weil {@code /beruf stand} dieselbe Zahl anzeigt - zwei Rechnungen
     * fuer dieselbe Frist liefen eines Tages auseinander.
     */
    public static Duration restfrist(Berufswerte werte, Berufsstand stand, Instant jetzt) {
        if (!werte.sperrfristGilt() || stand.letztesAblegen() == null) {
            return Duration.ZERO;
        }
        Duration seit = Duration.between(stand.letztesAblegen(), jetzt);
        // Eine negative Differenz heisst, dass die Uhr zurueckgestellt wurde oder die
        // Ablage aus der Zukunft liest. Dann gilt die GANZE Frist und nicht mehr: Alles
        // andere waere eine Sperre ueber Tage, und zwar wegen einer Zeitumstellung.
        if (seit.isNegative()) {
            return werte.sperrfrist();
        }
        Duration rest = werte.sperrfrist().minus(seit);
        return rest.isNegative() || rest.isZero() ? Duration.ZERO : rest;
    }

    /**
     * Wie viele Plaetze einer ROLLE belegt sind.
     *
     * <p>Oeffentlich aus demselben Grund wie {@link #restfrist}: {@code /beruf stand}
     * zeigt dieselbe Zahl. Zweimal gebaut zaehlte die Anzeige eines Tages anders als die
     * Regel, und ein Spieler las "1 von 2" und bekaeme trotzdem eine Absage.
     */
    public static int belegte(Berufsverzeichnis verzeichnis, Berufsstand stand,
                              boolean spezialisierung) {
        int belegt = 0;
        for (String gehalten : stand.berufe()) {
            // JE ROLLE gezaehlt und nicht alles zusammen: Zaehlte der Platzzaehler alle
            // gehaltenen Berufe, koennte ein Spieler mit zwei Grundberufen keine
            // Spezialisierung mehr nehmen - und die Absage waere dieselbe, also meldet es
            // niemand als Fehler.
            if (verzeichnis.istSpezialisierung(gehalten) == spezialisierung) {
                belegt++;
            }
        }
        return belegt;
    }

    private static Urteil absage(Ausgang ausgang, String beruf) {
        return new Urteil(ausgang, beruf, 0L, Duration.ZERO, 0);
    }

    /** Die rohe Eingabe fuer die Meldung - sie steht im Satz, damit der Spieler sie sieht. */
    private static String roh(String eingabe) {
        return eingabe == null ? "" : eingabe.trim();
    }

    private static void pflichten(Berufsverzeichnis verzeichnis, Berufswerte werte,
                                  Berufsstand stand, Instant jetzt) {
        if (verzeichnis == null || werte == null || stand == null || jetzt == null) {
            throw new IllegalArgumentException("Verzeichnis, Werte, Stand und Zeitpunkt"
                    + " sind Pflicht - eine Regel, die sich ihre Zahlen selbst holt,"
                    + " laesst sich nur mit allem drumherum pruefen");
        }
    }
}
