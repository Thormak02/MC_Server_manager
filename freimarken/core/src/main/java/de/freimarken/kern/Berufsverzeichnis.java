package de.freimarken.kern;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Die Berufe, die es gibt - und welcher zu welchem gehoert.
 *
 * <p>Gebaut aus den Eintraegen der Einstellungsdatei, nicht aus einer Aufzaehlung im
 * Programm: Ein dreizehnter Beruf ist ein Eintrag und kein Umbau (Plan 9.1). Dieselbe
 * Bauart wie {@link Warpverzeichnis}, und aus demselben Grund - dort sind es Weltnamen,
 * hier Berufsnamen, und in beiden Faellen entscheidet die Datei, was es gibt.
 *
 * <p><b>Die Reihenfolge der Datei bleibt die Reihenfolge der Liste.</b> Sie ist das, was
 * ein Spieler in {@code /beruf liste} liest, und der Betreiber soll sie bestimmen
 * koennen - alphabetisch sortiert waere eine zweite Entscheidung, die ihm niemand gesagt
 * hat.
 *
 * <p><b>Milde gegenueber einem unbrauchbaren Eintrag</b>, wie {@link Warpverzeichnis}:
 * Doppelte und solche, deren Grundberuf es nicht gibt, werden UEBERSPRUNGEN und nicht
 * geworfen. Der Leser der Datei hat darueber schon geklagt und nennt die Zeile; ein Wurf
 * hier liesse den Server gar nicht erst hochkommen, und zwar wegen eines Tippfehlers in
 * einem von zwoelf Eintraegen.
 *
 * <p><b>Was ein Beruf EINBRINGT, steht hier nicht.</b> Das kommt mit Teil E.
 */
public final class Berufsverzeichnis {

    /** Schluessel auf Angabe - fuer die Frage nach einem einzelnen. */
    private final Map<String, Berufsangabe> nachSchluessel;

    /**
     * Die Schluessel in der Reihenfolge der Datei.
     *
     * <p>Eigenes Feld, weil {@link Map#copyOf} die Reihenfolge NICHT erhaelt: Sie
     * ist das, was ein Spieler in {@code /beruf liste} liest, und sie laesst sich
     * aus der kopierten Abbildung nicht zurueckholen.
     */
    private final List<String> reihenfolge;

    private Berufsverzeichnis(LinkedHashMap<String, Berufsangabe> geordnet) {
        this.nachSchluessel = Map.copyOf(geordnet);
        this.reihenfolge = List.copyOf(geordnet.keySet());
    }

    /**
     * Baut das Verzeichnis aus den gelesenen Angaben.
     *
     * <p>Zwei Pruefungen, und beide ueberspringen statt zu werfen (siehe Klassenkopf):
     * ein Schluessel zweimal, und eine Spezialisierung, deren Grundberuf fehlt. Die
     * zweite braucht zwei Durchgaenge - der Grundberuf darf in der Datei UNTER seiner
     * Spezialisierung stehen, und der Betreiber soll die Reihenfolge nicht raten muessen.
     */
    public static Berufsverzeichnis ausAngaben(List<Berufsangabe> angaben) {
        if (angaben == null) {
            throw new IllegalArgumentException("Ohne Angaben gibt es kein Verzeichnis;"
                    + " eine leere Liste ist erlaubt und heisst: keine Berufe");
        }
        Map<String, Berufsangabe> ohneDoppelte = new LinkedHashMap<>();
        for (Berufsangabe angabe : angaben) {
            if (angabe != null) {
                ohneDoppelte.putIfAbsent(angabe.schluessel(), angabe);
            }
        }
        // Zweiter Durchgang: Erst jetzt stehen alle Schluessel fest, und nur jetzt laesst
        // sich sagen, ob der genannte Grundberuf einer ist.
        LinkedHashMap<String, Berufsangabe> gueltig = new LinkedHashMap<>();
        for (Map.Entry<String, Berufsangabe> eintrag : ohneDoppelte.entrySet()) {
            Berufsangabe angabe = eintrag.getValue();
            if (!angabe.istSpezialisierung()) {
                gueltig.put(eintrag.getKey(), angabe);
                continue;
            }
            Berufsangabe genannt = ohneDoppelte.get(angabe.grundberuf());
            // Nicht nur "vorhanden", sondern "selbst ein Grundberuf": Eine
            // Spezialisierung einer Spezialisierung waere eine Kette, und die Regel
            // zaehlt nur eine Stufe. Entwerten statt ueberspringen waere hier falsch -
            // aus einem Tippfehler wuerde ein siebter Grundberuf.
            if (genannt != null && !genannt.istSpezialisierung()) {
                gueltig.put(eintrag.getKey(), angabe);
            }
        }
        return new Berufsverzeichnis(gueltig);
    }

    /** Alle Berufe, in der Reihenfolge der Datei. */
    public List<String> berufe() {
        return reihenfolge;
    }

    /** Nur die Grundberufe, in der Reihenfolge der Datei. */
    public List<String> grundberufe() {
        return nurMit(false);
    }

    /** Nur die Spezialisierungen, in der Reihenfolge der Datei. */
    public List<String> spezialisierungen() {
        return nurMit(true);
    }

    private List<String> nurMit(boolean spezialisierung) {
        List<String> gefunden = new ArrayList<>();
        for (String schluessel : reihenfolge) {
            if (nachSchluessel.get(schluessel).istSpezialisierung() == spezialisierung) {
                gefunden.add(schluessel);
            }
        }
        return List.copyOf(gefunden);
    }

    /**
     * Der Beruf zu einer Eingabe, ohne Ruecksicht auf Gross- und Kleinschreibung.
     *
     * <p>Wie {@link Warpverzeichnis#aufgeloest}: Ein Spieler tippt {@code Bergmann}, und
     * der Befehl soll ihn nicht belehren.
     */
    public Optional<String> aufgeloest(String eingabe) {
        if (eingabe == null || eingabe.isBlank()) {
            return Optional.empty();
        }
        String gesucht = eingabe.trim().toLowerCase(Locale.ROOT);
        return nachSchluessel.containsKey(gesucht) ? Optional.of(gesucht)
                : Optional.empty();
    }

    /** Die Berufe, die mit diesem Anfang beginnen - fuer die Vervollstaendigung. */
    public List<String> vorschlaege(String angefangen) {
        String anfang = angefangen == null ? ""
                : angefangen.trim().toLowerCase(Locale.ROOT);
        List<String> gefunden = new ArrayList<>();
        for (String schluessel : reihenfolge) {
            if (schluessel.startsWith(anfang)) {
                gefunden.add(schluessel);
            }
        }
        return List.copyOf(gefunden);
    }

    /** Ob es diesen Beruf gibt und er ein Grundberuf ist. */
    public boolean istGrundberuf(String schluessel) {
        Berufsangabe angabe = nachSchluessel.get(schluessel);
        return angabe != null && !angabe.istSpezialisierung();
    }

    /** Ob es diesen Beruf gibt und er eine Spezialisierung ist. */
    public boolean istSpezialisierung(String schluessel) {
        Berufsangabe angabe = nachSchluessel.get(schluessel);
        return angabe != null && angabe.istSpezialisierung();
    }

    /** Der Grundberuf, den diese Spezialisierung voraussetzt. */
    public Optional<String> grundberufVon(String spezialisierung) {
        Berufsangabe angabe = nachSchluessel.get(spezialisierung);
        return angabe == null || !angabe.istSpezialisierung() ? Optional.empty()
                : Optional.of(angabe.grundberuf());
    }

    /** Die Spezialisierungen, die zu diesem Grundberuf gehoeren. */
    public List<String> spezialisierungenZu(String grundberuf) {
        List<String> gefunden = new ArrayList<>();
        for (String schluessel : reihenfolge) {
            Berufsangabe angabe = nachSchluessel.get(schluessel);
            if (angabe.istSpezialisierung()
                    && angabe.grundberuf().equals(grundberuf)) {
                gefunden.add(schluessel);
            }
        }
        return List.copyOf(gefunden);
    }

    /**
     * Wie viele Berufe dieser Rolle es gibt - fuer die Querpruefung der Platzzahlen.
     *
     * @param spezialisierung {@code true} fuer die Spezialisierungen, sonst die
     *                        Grundberufe
     */
    public int anzahlDerRolle(boolean spezialisierung) {
        return nurMit(spezialisierung).size();
    }
}
