package de.freimarken.kern;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Welche Teamrollen ein Spieler hat - mit Vergabezeit und Vergeber (Schritt 16a Teil A).
 *
 * <p><b>Warum die Vergabe mit im Stand steht</b> und nicht in einer zweiten Abfrage: Plan
 * 12c.3 verlangt "alles protokolliert, mit Grund und Bearbeiter", und eine Rollenvergabe
 * ist der Eingriff, bei dem diese Frage am haeufigsten gestellt wird - <i>wer hat dem
 * Verwalterrechte gegeben?</i>. Stuende sie in einer eigenen Abfrage, braeuchte
 * {@code /team zeigen} zwei Lesezuege fuer eine Antwort, und die zweite waere die, die
 * man beim Bauen vergisst.
 *
 * <p><b>Der Vergeber darf fehlen.</b> Die Konsole hat keine UUID, und ein Server, der
 * seine erste Rolle einrichtet, vergibt sie von dort - das ist der NORMALFALL beim
 * Aufsetzen und kein Sonderweg. Darum ein {@link Optional} und kein Platzhalter: Eine
 * erfundene Null-UUID stuende spaeter in der Auskunft als Spieler, den es nicht gibt.
 *
 * @param spieler wessen Rollen
 * @param zeilen  je Rolle eine Zeile, nach Rollennamen sortiert
 */
public record Teamstand(UUID spieler, List<Teamstand.Zeile> zeilen) {

    /**
     * Eine vergebene Rolle.
     *
     * @param rolle  der Name, kleingeschrieben wie in der Datei
     * @param seit   wann sie vergeben wurde - UTC, wie jede Zeit dieses Projekts
     * @param vonWem wer sie vergeben hat; {@code null} heisst: die Konsole
     */
    public record Zeile(String rolle, Instant seit, UUID vonWem) {

        public Zeile {
            if (rolle == null || rolle.isBlank()) {
                throw new IllegalArgumentException("Eine Teamzeile ohne Rolle");
            }
            String klein = rolle.trim().toLowerCase(Locale.ROOT);
            if (!klein.equals(rolle)) {
                throw new IllegalArgumentException("Der Rollenname muss schon"
                        + " kleingeschrieben hereinkommen: \"" + rolle + "\" - sonst"
                        + " findet Teamwerte.rolle ihn nicht wieder");
            }
            if (seit == null) {
                throw new IllegalArgumentException("Eine Teamzeile ohne Vergabezeit:"
                        + " \"" + klein + "\". Ohne sie beantwortet die Auskunft die"
                        + " Frage seit wann nicht, und genau die wird gestellt");
            }
        }

        /** Wer sie vergeben hat - leer heisst: die Konsole. */
        public Optional<UUID> vergeber() {
            return Optional.ofNullable(vonWem);
        }
    }

    public Teamstand {
        if (spieler == null) {
            throw new IllegalArgumentException("Ohne Spieler gibt es keine Teamrollen");
        }
        if (zeilen == null) {
            throw new IllegalArgumentException("Die Zeilenliste darf leer sein, aber"
                    + " nicht fehlen - leer heisst: dieser Spieler ist nicht im Team");
        }
        Set<String> gesehen = new LinkedHashSet<>();
        for (Zeile zeile : zeilen) {
            if (zeile == null) {
                throw new IllegalArgumentException("Eine fehlende Teamzeile bei "
                        + spieler);
            }
            if (!gesehen.add(zeile.rolle())) {
                throw new IllegalArgumentException("Die Rolle \"" + zeile.rolle()
                        + "\" steht zweimal bei " + spieler + ". Zweimal dieselbe Rolle"
                        + " gibt keine zweiten Rechte, aber zwei Vergabezeiten - und"
                        + " dann nennt die Auskunft die, die zufaellig vorn steht");
            }
        }
        // NACH ROLLENNAMEN sortiert und nicht nach Zeit: Die Auskunft soll bei zwei
        // Aufrufen dieselbe Reihenfolge zeigen, und eine Ablage gibt ihre Zeilen in der
        // Reihenfolge zurueck, die ihr Index gerade nahelegt. Zwei Fassungen hinter
        // derselben Schnittstelle sortierten sonst verschieden, und eine Probe, die beide
        // vergleicht, waere mal gruen und mal rot.
        List<Zeile> sortiert = new ArrayList<>(zeilen);
        sortiert.sort(Comparator.comparing(Zeile::rolle));
        zeilen = List.copyOf(sortiert);
    }

    /** Ein Spieler, der nicht im Team ist. */
    public static Teamstand leer(UUID spieler) {
        return new Teamstand(spieler, List.of());
    }

    /** Ob dieser Spieler ueberhaupt eine Rolle hat. */
    public boolean imTeam() {
        return !zeilen.isEmpty();
    }

    /** Die Rollennamen allein - fuer {@link Teamwerte#rechteFuer}. */
    public List<String> rollen() {
        List<String> namen = new ArrayList<>(zeilen.size());
        for (Zeile zeile : zeilen) {
            namen.add(zeile.rolle());
        }
        return List.copyOf(namen);
    }

    /** Ob dieser Spieler diese Rolle hat. */
    public boolean hat(String rolle) {
        if (rolle == null) {
            return false;
        }
        String klein = rolle.trim().toLowerCase(Locale.ROOT);
        for (Zeile zeile : zeilen) {
            if (zeile.rolle().equals(klein)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Die Rollen, die dieser Server nicht mehr kennt.
     *
     * <p>Sie entstehen, wenn der Betreiber eine Zeile aus der {@code config.yml} nimmt,
     * waehrend jemand die Rolle hat. Der Betroffene verliert damit still ihre Rechte -
     * im Spiel sieht das aus, als waere die Rolle nie vergeben worden. Darum gehoert sie
     * gemeldet, und darum steht die Frage hier und nicht in der Ablage: Die Ablage weiss
     * nicht, was in der Datei steht.
     */
    public List<String> unbekannteRollen(Teamwerte werte) {
        if (werte == null) {
            return rollen();
        }
        List<String> fremd = new ArrayList<>();
        for (Zeile zeile : zeilen) {
            if (!werte.kennt(zeile.rolle())) {
                fremd.add(zeile.rolle());
            }
        }
        return List.copyOf(fremd);
    }
}
