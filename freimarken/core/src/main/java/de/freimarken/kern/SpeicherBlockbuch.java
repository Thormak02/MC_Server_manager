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
