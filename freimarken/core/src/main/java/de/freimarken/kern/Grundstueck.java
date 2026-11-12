package de.freimarken.kern;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

/**
 * Ein Grundstueck in einer Freistadt - die Flaeche, die ein Stadtbesitzer vergibt.
 *
 * <p><b>Eine EIGENE Tabelle und nicht {@code parzellen}</b>, und das ist eine Abweichung
 * von Plan 6.1, die aufgeschrieben gehoert. Jener Plan sagt: "eltern_id ist der Trick -
 * ein Grundstueck in einer Freistadt ist derselbe Datensatz wie eine Parzelle in der
 * Marktstadt, nur mit Elternteil." Das traegt nicht mehr, sobald der Zuschnitt FREI ist
 * (entschieden am 11.11.2026), und zwar aus fuenf gemessenen Gruenden:
 *
 * <ul>
 *   <li>{@code Parzelle.x} und {@code .z} sind primitive {@code int}; ein Besitz ohne
 *       Rasterzelle laesst sich gar nicht als {@code Parzelle} lesen, und
 *       {@code MariaLager.lies} wirft dann ausdruecklich.</li>
 *   <li>Der eindeutige Schluessel {@code ort (welt, x, z)} laesst je Zelle genau EINE
 *       Zeile - und die Freistadt selbst belegt dort schon ihre Feldnummern.</li>
 *   <li>{@code mitbauerSetzen} schreibt vom Elternteil auf ALLE Kinder durch. Ein
 *       Stadtbesitzer ueberschriebe damit die Mitbauer jedes verkauften
 *       Grundstuecks.</li>
 *   <li>Alle vorhandenen Elternteil-Wege sind auf {@code art='gruppe'} festgenagelt -
 *       {@code ausGruppeLoesen}, {@code auftrennen}, {@code erweitereGruppe},
 *       {@code istGruppe}.</li>
 *   <li>{@code entfernen} verweigert jede Zeile, auf die {@code eltern_id} zeigt
 *       ({@code ON DELETE RESTRICT}) - eine Stadt mit verkauften Grundstuecken liesse
 *       sich nicht mehr aufloesen, und die Meldung dazu heisst "erst auftrennen".</li>
 * </ul>
 *
 * <p><b>Der Grund, aus dem Plan 6.1 auf einer Tabelle bestand, bleibt trotzdem
 * gewahrt:</b> Es ging um den heissen Pfad - {@code darfBauen} laeuft bei jedem
 * gesetzten Block, und ein Quadervergleich ist dort teurer als eine Punktabfrage.
 * Deshalb rechnet {@link #flaeche} in ZELLEN und nicht in Bloecken, und deshalb liegt
 * vor dieser Ablage ein vollstaendiger Zwischenspeicher ohne Verfall. Die Frage "wem
 * gehoert dieser Block" fasst die Datenbank nie an. Die Ladeneinheit hat uebrigens aus
 * demselben Grund schon ihre eigene Tabelle.
 *
 * <p><b>Wem ein Grundstueck gehoert: sich selbst.</b> Jede Zeile traegt Eigentuemer und
 * Mitbauer vollstaendig - das ist die Zusage aus Plan 6.1, die hier weiter gilt.
 * {@link #stadtId} ist eine Schicht darueber und kein Verweis zum Nachschlagen; sonst
 * wuerde aus jeder Blockpruefung ein zweiter Gang.
 *
 * @param welt        Weltschluessel der Stadtfelderwelt
 * @param stadtId     die Nummer der Freistadt in {@code parzellen} - das Elternteil
 * @param eigentuemer wem es gehoert. Anfangs der Stadtbesitzer; mit Teil C3 wechselt er
 * @param flaeche     der Zuschnitt in ZELLEN
 * @param preis       was es gekostet hat, in Mark. Beim Zuschneiden 0 - der Stadtbesitzer
 *                    zahlt fuer sein eigenes Feld nicht zweimal
 */
public record Grundstueck(long id, String welt, long stadtId, UUID eigentuemer,
                          Set<UUID> mitbauer, Zellquader flaeche, long preis,
                          Instant erworbenAm) {

    /** Noch nicht geschrieben - die Nummer vergibt erst die Ablage. */
    public static final long OHNE_NUMMER = 0L;

    public Grundstueck {
        if (welt == null || welt.isBlank()) {
            throw new IllegalArgumentException("Ein Grundstueck ohne Welt gibt es nicht");
        }
        if (stadtId <= 0L) {
            throw new IllegalArgumentException("Ein Grundstueck haengt immer an einer"
                    + " Freistadt - Stadtnummer " + stadtId + " ist keine");
        }
        if (eigentuemer == null) {
            throw new IllegalArgumentException("Ein Grundstueck ohne Eigentuemer gibt es"
                    + " nicht: Wem nichts gehoert, darf auch niemand bauen, und dann"
                    + " waere es eine Flaeche, die fuer immer gesperrt ist");
        }
        if (flaeche == null) {
            throw new IllegalArgumentException("Ein Grundstueck ohne Flaeche gibt es"
                    + " nicht");
        }
        if (preis < 0L) {
            throw new IllegalArgumentException("Ein Grundstueck kann nichts kosten,"
                    + " aber nicht weniger als nichts: " + preis);
        }
        if (erworbenAm == null) {
            throw new IllegalArgumentException("Ohne Zeitpunkt gibt es kein Grundstueck -"
                    + " die Verfallsregel aus Plan 16 rechnet damit");
        }
        // Wie bei Parzelle: null wird still zur leeren Menge, statt zu werfen. Eine
        // Ablage, die keine Mitbauer gefunden hat, meint genau das.
        mitbauer = mitbauer == null ? Set.of() : Set.copyOf(mitbauer);
    }

    /**
     * Ob dieser Spieler hier bauen darf.
     *
     * <p>Wortgleich zu {@link Parzelle#darfBauen}, und das ist Absicht: Die Frage ist
     * dieselbe, und zwei verschiedene Antworten darauf waeren der Fehler, den niemand
     * sucht. {@code null} darf nie bauen - die Wache fragt mit {@code null}, wenn sie
     * nur wissen will, WAS hier liegt.
     */
    public boolean darfBauen(UUID spieler) {
        return spieler != null
                && (spieler.equals(eigentuemer) || mitbauer.contains(spieler));
    }

    /** Ob dieses Grundstueck diesem Spieler gehoert - ein Mitbauer ist nicht Eigentuemer. */
    public boolean gehoert(UUID spieler) {
        return spieler != null && spieler.equals(eigentuemer);
    }

    /** Ob diese Zelle dazugehoert - die Frage des heissen Pfades. */
    public boolean enthaelt(int zelleX, int zelleZ) {
        return flaeche.enthaelt(zelleX, zelleZ);
    }
}
