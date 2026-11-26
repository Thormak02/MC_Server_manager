package de.freimarken.kern;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;

/**
 * Das Blockbuch im Arbeitsspeicher - fuer Proben und fuer einen Server ohne Datenbank.
 *
 * <p>Haelt denselben {@code BlockbuchVertrag} wie {@code MariaBlockbuch}.
 *
 * <p><b>Die Nummern steigen und werden nie wiederverwendet</b> - anders als bei
 * {@code AUTO_INCREMENT} in MariaDB, dessen Zaehler einen Neustart nicht zuverlaessig
 * ueberlebt. Darum steht diese Zusage NICHT im geteilten Vertrag, sondern nur hier. Fuer
 * das Blockbuch ist das harmlos: Niemand holt eine Zeile nach ihrer Nummer: sie ist nur
 * der Tiebreaker der Reihenfolge.
 *
 * <p><b>Die Reihenfolge ist Zeitpunkt absteigend, dann Nummer absteigend</b>, und der
 * zweite Teil ist keine Zugabe: Die Spalte in MariaDB ist {@code DATETIME(3)}, also
 * millisekundengenau, und ein Sammler schreibt ohne Weiteres ein Dutzend Zeilen mit
 * derselben Millisekunde - zwanzig Bloecke in einem Tick sind ein normaler Abbau mit
 * Effizienz V. Ohne den Tiebreaker waere ihre Reihenfolge beliebig, und eine Abfrage mit
 * Grenze liefe zweimal verschieden aus.
 *
 * <p><b>Nicht fadensicher</b>, wie die uebrigen Speicherfassungen: Das Blockbuch wird im
 * Hauptfaden des Spielservers geschrieben und gelesen.
 */
public final class SpeicherBlockbuch implements Blockbuchablage {

    /**
     * Die Reihenfolge, in der gelesen wird - neueste zuerst.
     *
     * <p>Als Konstante und nicht an drei Lesestellen getippt: Zwei Lesemethoden, die
     * verschieden sortieren, sind der Fehler, den keine Probe sieht, solange sie je nur
     * eine davon fragt.
     */
    private static final Comparator<Blockzeile> NEUESTE_ZUERST =
            Comparator.comparing(Blockzeile::zeitpunkt)
                    .thenComparingLong(Blockzeile::id)
                    .reversed();

    private final List<Blockzeile> zeilen = new ArrayList<>();

    /**
     * Die Nummern, die schon zurueckgenommen wurden.
     *
     * <p>Eine eigene Menge und kein Feld an der Zeile: {@link Blockzeile} ist ein
     * Record und damit unveraenderlich - ein Merker darin muesste die Zeile
     * ersetzen, und dann waeren zwei Fassungen derselben Zeile im Umlauf. In der
     * Datenbank ist es eine Spalte; hier ist die Menge das Gegenstueck dazu.
     */
    private final java.util.Set<Long> zurueckgenommen = new java.util.HashSet<>();

    private long naechsteNummer = 1L;

    @Override
    public int schreibe(List<Blockzeile> neue) {
        if (neue == null) {
            throw new IllegalArgumentException("Nichts ist keine Liste von Zeilen - eine"
                    + " LEERE Liste ist erlaubt und tut nichts");
        }
        for (Blockzeile zeile : neue) {
            if (zeile == null) {
                throw new IllegalArgumentException("Eine Zeile, die nichts ist, gehoert"
                        + " nicht ins Blockbuch");
            }
        }
        // ZUERST pruefen, dann schreiben: Sonst stuenden die ersten Zeilen schon im Buch,
        // waehrend der Aufrufer eine IllegalArgumentException bekommt und annimmt, es sei
        // nichts geschehen.
        for (Blockzeile zeile : neue) {
            zeilen.add(zeile.mitNummer(naechsteNummer++));
        }
        return neue.size();
    }

    @Override
    public List<Blockzeile> amOrt(Blockort ort, int hoechstens) {
        if (ort == null) {
            throw new IllegalArgumentException("Ohne Ort gibt es keine Frage an das"
                    + " Blockbuch");
        }
        pruefeGrenze(hoechstens);
        return gefiltert(zeile -> zeile.ort().equals(ort), hoechstens);
    }

    @Override
    public List<Blockzeile> vonSpieler(UUID spieler, int hoechstens) {
        if (spieler == null) {
            throw new IllegalArgumentException("Ohne Spieler gibt es keine Frage an das"
                    + " Blockbuch");
        }
        pruefeGrenze(hoechstens);
        return gefiltert(zeile -> zeile.spieler().equals(spieler), hoechstens);
    }

    @Override
    public int raeumeAuf(Instant aelterAls, int hoechstens) {
        if (aelterAls == null) {
            throw new IllegalArgumentException("Ohne Grenze wuerde alles geloescht - der"
                    + " Zeitpunkt kommt herein und wird nie hier gerechnet");
        }
        pruefeGrenze(hoechstens);
        // Die AELTESTEN zuerst loeschen, nicht die ersten, die der Durchlauf findet: Was
        // ein Happen nicht schafft, nimmt der naechste, und dann soll der Rest der
        // juengere sein. Sonst blieben bei dauerhaft knappem Happen gerade die aeltesten
        // Zeilen fuer immer liegen.
        List<Blockzeile> aelteste = new ArrayList<>(zeilen);
        aelteste.sort(NEUESTE_ZUERST.reversed());
        int weg = 0;
        for (Blockzeile zeile : aelteste) {
            if (weg >= hoechstens) {
                break;
            }
            if (zeile.zeitpunkt().isBefore(aelterAls)) {
                entferne(zeile.id());
                weg++;
            }
        }
        return weg;
    }

    @Override
    public List<Blockzeile> imBereich(String welt, Quader kasten, Instant von,
                                      Instant bis, int hoechstens) {
        if (welt == null || welt.isBlank()) {
            throw new IllegalArgumentException("Ohne Welt gibt es keinen Bereich");
        }
        if (kasten == null) {
            throw new IllegalArgumentException("Ohne Kasten gibt es keinen Bereich");
        }
        if (von == null || bis == null) {
            throw new IllegalArgumentException("Ein Rueckweg ohne Zeitfenster waere"
                    + " die ganze Geschichte der Welt");
        }
        if (bis.isBefore(von)) {
            throw new IllegalArgumentException("Das Fenster liegt verkehrt: " + von
                    + " bis " + bis);
        }
        pruefeGrenze(hoechstens);
        List<Blockzeile> gefunden = new ArrayList<>();
        for (Blockzeile zeile : zeilen) {
            if (zurueckgenommen.contains(zeile.id())) {
                continue;
            }
            if (!zeile.ort().welt().equals(welt)) {
                continue;
            }
            if (!kasten.enthaelt(zeile.ort().x(), zeile.ort().y(), zeile.ort().z())) {
                continue;
            }
            if (zeile.zeitpunkt().isBefore(von) || zeile.zeitpunkt().isAfter(bis)) {
                continue;
            }
            gefunden.add(zeile);
        }
        // AELTESTE zuerst - die Umkehrung der Hausregel, und zwar begruendet im
        // Vertrag. Erst sortieren, dann abschneiden, wie bei gefiltert().
        gefunden.sort(NEUESTE_ZUERST.reversed());
        return gefunden.size() <= hoechstens
                ? gefunden
                : new ArrayList<>(gefunden.subList(0, hoechstens));
    }

    @Override
    public int merkeZurueckgenommen(List<Long> nummern) {
        if (nummern == null) {
            throw new IllegalArgumentException("Ohne Liste gibt es nichts zu merken");
        }
        int gemerkt = 0;
        for (Long nummer : nummern) {
            if (nummer == null) {
                continue;
            }
            // Nur was es GIBT und noch nicht gemerkt ist - sonst zaehlte ein
            // zweiter Aufruf dieselbe Zeile erneut, und die Rueckgabe log.
            boolean dabei = zeilen.stream().anyMatch(z -> z.id() == nummer);
            if (dabei && zurueckgenommen.add(nummer)) {
                gemerkt++;
            }
        }
        return gemerkt;
    }

    /** Wie viele Zeilen im Buch stehen - fuer Proben. */
    public int anzahl() {
        return zeilen.size();
    }

    private void entferne(long id) {
        Iterator<Blockzeile> laeufer = zeilen.iterator();
        while (laeufer.hasNext()) {
            if (laeufer.next().id() == id) {
                laeufer.remove();
                return;
            }
        }
    }

    /** Was eine Zeile sein muss, um in die Antwort zu kommen. */
    private interface Filter {
        boolean passt(Blockzeile zeile);
    }

    private List<Blockzeile> gefiltert(Filter filter, int hoechstens) {
        List<Blockzeile> gefunden = new ArrayList<>();
        for (Blockzeile zeile : zeilen) {
            if (filter.passt(zeile)) {
                gefunden.add(zeile);
            }
        }
        gefunden.sort(NEUESTE_ZUERST);
        // ERST sortieren, DANN abschneiden. Die andere Reihenfolge gaebe die ersten
        // zwanzig gefundenen Zeilen zurueck und sortierte die - also die AELTESTEN
        // zwanzig in der Anzeige "neueste zuerst", und das ist die Antwort auf eine
        // andere Frage als die gestellte.
        return gefunden.size() <= hoechstens
                ? gefunden
                : new ArrayList<>(gefunden.subList(0, hoechstens));
    }

    private static void pruefeGrenze(int hoechstens) {
        if (hoechstens < 1) {
            throw new IllegalArgumentException("Eine Grenze von " + hoechstens
                    + " Zeilen ist keine - erwartet wird mindestens 1. Die Grenze ist"
                    + " Pflicht, weil dies die groesste Tabelle des Projekts ist.");
        }
    }
}
