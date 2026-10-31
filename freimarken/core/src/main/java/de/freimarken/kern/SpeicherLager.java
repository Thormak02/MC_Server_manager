package de.freimarken.kern;

import java.time.Clock;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Ein Lager im Arbeitsspeicher - fuer Tests und fuer den ersten Start ohne Datenbank.
 *
 * <p>Ausdruecklich NICHT fuer den Betrieb: Was hier liegt, ist nach einem Neustart weg.
 * Es existiert, damit die Regeln in {@link Besitzbuch} ohne Datenbank pruefbar sind - und
 * damit beim Umstieg auf die Datenbank derselbe Testsatz beide Umsetzungen prueft.
 */
public final class SpeicherLager implements BesitzLager {

    private final Map<String, Parzelle> nachOrt = new LinkedHashMap<>();
    private final Map<Long, String> orteNachId = new LinkedHashMap<>();
    private final AtomicLong naechsteId = new AtomicLong(1L);
    private final Clock uhr;

    public SpeicherLager() {
        this(Clock.systemUTC());
    }

    /** Mit eigener Uhr - damit Tests ueber Zeitpunkte nicht vom Wanduhrstand abhaengen. */
    public SpeicherLager(Clock uhr) {
        this.uhr = uhr;
    }

    private static String ort(String welt, int x, int z) {
        return welt + "/" + x + "/" + z;
    }

    @Override
    public synchronized Optional<Parzelle> finde(String welt, int x, int z) {
        return Optional.ofNullable(nachOrt.get(ort(welt, x, z)));
    }

    @Override
    public synchronized List<Parzelle> vonSpieler(String welt, UUID spieler) {
        List<Parzelle> treffer = new ArrayList<>();
        for (Parzelle parzelle : nachOrt.values()) {
            if (parzelle.welt().equals(welt) && parzelle.gehoert(spieler)) {
                treffer.add(parzelle);
            }
        }
        treffer.sort(Comparator.comparingInt(Parzelle::x).thenComparingInt(Parzelle::z));
        return List.copyOf(treffer);
    }

    @Override
    public synchronized int anzahlVon(String welt, UUID spieler) {
        return vonSpieler(welt, spieler).size();
    }

    @Override
    public synchronized Parzelle anlegen(String welt, int x, int z, UUID eigentuemer,
                                         long preis) {
        String schluessel = ort(welt, x, z);
        if (nachOrt.containsKey(schluessel)) {
            throw new IllegalStateException("Parzelle ist bereits vergeben: " + schluessel);
        }
        Parzelle neu = new Parzelle(naechsteId.getAndIncrement(), welt, x, z, eigentuemer,
                Set.of(), preis, uhr.instant());
        nachOrt.put(schluessel, neu);
        orteNachId.put(neu.id(), schluessel);
        return neu;
    }

    @Override
    public synchronized Parzelle mitbauerSetzen(long id, Set<UUID> mitbauer) {
        String schluessel = orteNachId.get(id);
        if (schluessel == null) {
            throw new IllegalStateException("Keine Parzelle mit der Nummer " + id);
        }
        Parzelle alt = nachOrt.get(schluessel);
        Parzelle neu = new Parzelle(alt.id(), alt.welt(), alt.x(), alt.z(), alt.eigentuemer(),
                mitbauer, alt.preis(), alt.erworbenAm());
        nachOrt.put(schluessel, neu);
        return neu;
    }

    @Override
    public synchronized void entfernen(long id) {
        String schluessel = orteNachId.remove(id);
        if (schluessel != null) {
            nachOrt.remove(schluessel);
        }
    }

    /** Wie viele Parzellen insgesamt vergeben sind - fuer Tests und Anzeige. */
    public synchronized int anzahl() {
        return nachOrt.size();
    }
}
