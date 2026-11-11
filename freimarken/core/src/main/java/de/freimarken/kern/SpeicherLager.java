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
    /** Gruppen liegen getrennt: Sie haben keine Rasterzelle, also keinen Ortsschluessel. */
    private final Map<Long, Gruppe> gruppen = new LinkedHashMap<>();
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
        // Art, Elternteil und Zustand sind hier fest: Dieses Lager hat genau einen
        // Schreibweg, und der legt eine eigenstaendige, laufende Rasterparzelle an.
        Parzelle neu = new Parzelle(naechsteId.getAndIncrement(), welt, x, z, eigentuemer,
                Set.of(), preis, uhr.instant(),
                Art.PARZELLE, Parzelle.KEIN_ELTERNTEIL, Zustand.AKTIV);
        nachOrt.put(schluessel, neu);
        orteNachId.put(neu.id(), schluessel);
        return neu;
    }

    @Override
    public synchronized Parzelle freistadtAnlegen(String welt, int feldX, int feldZ,
                                                  UUID eigentuemer, long preis,
                                                  String name) {
        String schluessel = ort(welt, feldX, feldZ);
        if (nachOrt.containsKey(schluessel)) {
            throw new IllegalStateException("Das Stadtfeld ist bereits vergeben: "
                    + schluessel);
        }
        // Die Namenspruefung steht im SELBEN synchronized wie das Einhaengen. Zwei
        // getrennte Bloecke waeren genau das Fenster, das der eindeutige Schluessel in
        // der Datenbank nicht hat - und dann pruefte diese Fassung etwas anderes als
        // jene, obwohl beide denselben Vertrag erfuellen sollen.
        for (Parzelle stadt : staedteIn(welt)) {
            if (stadt.name().equals(name)) {
                throw new IllegalStateException("Der Stadtname ist vergeben: " + name);
            }
        }
        Parzelle neu = new Parzelle(naechsteId.getAndIncrement(), welt, feldX, feldZ,
                eigentuemer, Set.of(), preis, uhr.instant(),
                Art.FREISTADT, Parzelle.KEIN_ELTERNTEIL, Zustand.AKTIV, name);
        nachOrt.put(schluessel, neu);
        orteNachId.put(neu.id(), schluessel);
        return neu;
    }

    @Override
    public synchronized List<Parzelle> staedteIn(String welt) {
        List<Parzelle> treffer = new ArrayList<>();
        for (Parzelle vorhanden : nachOrt.values()) {
            if (vorhanden.welt().equals(welt) && vorhanden.istFreistadt()) {
                treffer.add(vorhanden);
            }
        }
        treffer.sort(Comparator.comparing(Parzelle::name));
        return List.copyOf(treffer);
    }

    @Override
    public synchronized void mitbauerSetzen(long id, Set<UUID> mitbauer) {
        Gruppe gruppe = gruppen.get(id);
        if (gruppe != null) {
            // Durchschreiben: Die Gruppe und jedes ihrer Kinder bekommen dieselbe Liste.
            // Nur so bleibt darfBauen am Kind eine Abfrage ohne Umweg.
            gruppen.put(id, new Gruppe(gruppe.id(), gruppe.welt(), gruppe.eigentuemer(),
                    Set.copyOf(mitbauer), gruppe.preis(), gruppe.erworbenAm()));
            for (Parzelle kind : kinderVon(id)) {
                schreibeMitbauer(kind.id(), mitbauer);
            }
            return;
        }
        schreibeMitbauer(id, mitbauer);
    }

    private void schreibeMitbauer(long id, Set<UUID> mitbauer) {
        String schluessel = orteNachId.get(id);
        if (schluessel == null) {
            throw new IllegalStateException("Keine Parzelle mit der Nummer " + id);
        }
        Parzelle alt = nachOrt.get(schluessel);
        nachOrt.put(schluessel, new Parzelle(alt.id(), alt.welt(), alt.x(), alt.z(),
                alt.eigentuemer(), mitbauer, alt.preis(), alt.erworbenAm(),
                // Durchtragen, nicht neu setzen: Feste Werte loeschten hier bei jeder
                // Mitbauer-Aenderung still den Gruppenbezug und den Zustand.
                //
                // Der NAME gehoert dazu, und sein Fehlen war ein Fehler [11.11.2026]:
                // Hier stand der verkuerzte Konstruktor, der KEIN_NAME setzt - und eine
                // Freistadt ohne Namen WIRFT. Ein Stadtbesitzer konnte damit keinen
                // einzigen Mitbauer eintragen. Teil B hat das zwoelfte Feld angelegt
                // und genau diese Stelle uebersehen; gefunden hat es die erste Probe,
                // die Mitbauer auf einer FREISTADT setzt.
                alt.art(), alt.elternId(), alt.zustand(), alt.name()));
    }

    @Override
    public synchronized void entfernen(long id) {
        if (gruppen.containsKey(id)) {
            throw new IllegalStateException(
                    "Nummer " + id + " ist eine Gruppe - erst auftrennen");
        }
        String schluessel = orteNachId.remove(id);
        if (schluessel != null) {
            nachOrt.remove(schluessel);
        }
    }

    @Override
    public synchronized List<Parzelle> kinderVon(long gruppeId) {
        List<Parzelle> treffer = new ArrayList<>();
        for (Parzelle parzelle : nachOrt.values()) {
            if (parzelle.elternId() == gruppeId) {
                treffer.add(parzelle);
            }
        }
        treffer.sort(Comparator.comparingInt(Parzelle::x).thenComparingInt(Parzelle::z));
        return List.copyOf(treffer);
    }

    @Override
    public synchronized long gruppeSetzen(long gruppeId, String welt, UUID eigentuemer,
                                          Set<UUID> mitbauer, long gebuehr,
                                          List<Long> felder) {
        if (felder.isEmpty()) {
            throw new IllegalStateException("Kein Feld zum Anhaengen angegeben");
        }
        // ERST alles pruefen, DANN alles schreiben. Ohne die Vorpruefung haette ein Fehler
        // beim dritten Feld die ersten beiden schon angehaengt - und "alles oder nichts"
        // waere eine Zusage, die diese Umsetzung nur meistens einhaelt.
        for (long feldId : felder) {
            String schluessel = orteNachId.get(feldId);
            Parzelle feld = schluessel == null ? null : nachOrt.get(schluessel);
            if (feld == null) {
                throw new IllegalStateException("Keine Parzelle mit der Nummer " + feldId);
            }
            if (!feld.welt().equals(welt) || !feld.gehoert(eigentuemer)) {
                throw new IllegalStateException(
                        "Feld " + feldId + " gehoert nicht zu dieser Gruppe");
            }
            if (feld.hatElternteil()) {
                throw new IllegalStateException(
                        "Feld " + feldId + " gehoert schon zu einer Gruppe");
            }
        }

        long id = gruppeId;
        if (id == Parzelle.KEIN_ELTERNTEIL) {
            id = naechsteId.getAndIncrement();
            gruppen.put(id, new Gruppe(id, welt, eigentuemer, Set.copyOf(mitbauer),
                    gebuehr, uhr.instant()));
        } else {
            Gruppe alt = gruppen.get(id);
            if (alt == null) {
                throw new IllegalStateException("Keine Gruppe mit der Nummer " + id);
            }
            // erworbenAm bleibt: Die Gruppe entsteht einmal. Eine Gruppe, die bei jedem
            // Wachsen juenger wird als ihre Kinder, setzt jede kuenftige Verfallsrechnung
            // still zurueck.
            gruppen.put(id, new Gruppe(id, alt.welt(), alt.eigentuemer(),
                    Set.copyOf(mitbauer), alt.preis() + gebuehr, alt.erworbenAm()));
        }

        for (long feldId : felder) {
            String schluessel = orteNachId.get(feldId);
            Parzelle alt = nachOrt.get(schluessel);
            nachOrt.put(schluessel, new Parzelle(alt.id(), alt.welt(), alt.x(), alt.z(),
                    alt.eigentuemer(), mitbauer, alt.preis(), alt.erworbenAm(),
                    alt.art(), id, alt.zustand()));
        }
        // Auch die schon vorhandenen Kinder bekommen die neue Liste - beim Erweitern ist
        // sie die Vereinigung und kann gewachsen sein.
        for (Parzelle kind : kinderVon(id)) {
            schreibeMitbauer(kind.id(), mitbauer);
        }
        return id;
    }

    @Override
    public synchronized void ausGruppeLoesen(long feldId) {
        String schluessel = orteNachId.get(feldId);
        Parzelle feld = schluessel == null ? null : nachOrt.get(schluessel);
        if (feld == null || !feld.hatElternteil()) {
            throw new IllegalStateException("Nummer " + feldId + " steckt in keiner Gruppe");
        }
        nachOrt.put(schluessel, new Parzelle(feld.id(), feld.welt(), feld.x(), feld.z(),
                feld.eigentuemer(), feld.mitbauer(), feld.preis(), feld.erworbenAm(),
                feld.art(), Parzelle.KEIN_ELTERNTEIL, feld.zustand()));
    }

    @Override
    public synchronized void auftrennen(long gruppeId) {
        if (gruppen.remove(gruppeId) == null) {
            throw new IllegalStateException("Keine Gruppe mit der Nummer " + gruppeId);
        }
        for (Parzelle kind : kinderVon(gruppeId)) {
            String schluessel = orteNachId.get(kind.id());
            nachOrt.put(schluessel, new Parzelle(kind.id(), kind.welt(), kind.x(), kind.z(),
                    kind.eigentuemer(), kind.mitbauer(), kind.preis(), kind.erworbenAm(),
                    kind.art(), Parzelle.KEIN_ELTERNTEIL, kind.zustand()));
        }
    }

    /**
     * Eine Gruppe im Arbeitsspeicher.
     *
     * <p>Bewusst privat und bewusst kein {@link Parzelle}: Eine Gruppe hat keine
     * Rasterzelle, und ein Parzelle-Objekt ohne x und z gibt es nicht.
     */
    private record Gruppe(long id, String welt, UUID eigentuemer, Set<UUID> mitbauer,
                          long preis, java.time.Instant erworbenAm) {
    }

    /** Wie viele Parzellen insgesamt vergeben sind - fuer Tests und Anzeige. */
    public synchronized int anzahl() {
        return nachOrt.size();
    }
}
