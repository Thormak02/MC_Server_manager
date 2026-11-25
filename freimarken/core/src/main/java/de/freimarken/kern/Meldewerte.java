package de.freimarken.kern;

import java.util.ArrayList;
import java.util.List;

/**
 * Was am Meldewesen einstellbar ist (Plan 12c.3, Schritt 16a Teil C).
 *
 * <p><b>Drei Zahlen, und alle drei sind Bremsen gegen denselben Missbrauch:</b> Ein
 * Spieler, der im Streit zwanzig Meldungen gegen denselben Gegner schreibt, macht die
 * Liste unlesbar - und damit die echten Meldungen unsichtbar. Das ist der Angriff, gegen
 * den dieser Teil gebaut ist, und er braucht keine boese Absicht: Zwei Leute im Streit
 * tun das von selbst.
 *
 * <p><b>Ein {@code an}-Schalter, anders als bei {@link Teamwerte}.</b> Hier ist "aus" ein
 * brauchbarer Zustand: Ein kleiner Server, auf dem sich alle kennen, braucht kein
 * Meldewesen, und dann soll {@code /melden} nicht als Befehl erscheinen, der ins Leere
 * laeuft. Bei den Teamrollen hiesse "aus" dagegen, allen Moderatoren die Rechte zu
 * entziehen - ein Schalter, dessen einziger Zweck ein Unfall ist.
 *
 * <p><b>Was NICHT einstellbar ist: ob der Melder eine Rueckmeldung bekommt.</b> Der Plan
 * nennt sie als den Grund, aus dem das Meldewesen ueberhaupt funktioniert
 * (<i>"sonst meldet beim zweiten Mal niemand mehr"</i>). Ein Schalter dafuer waere die
 * Einstellung, mit der ein Betreiber sein eigenes Meldewesen stilllegt, ohne es zu
 * merken.
 *
 * @param an                    ob gemeldet werden kann
 * @param abklingzeitSekunden   wie lange ein Spieler nach einer Meldung warten muss;
 *                              0 heisst: keine Wartezeit
 * @param hoechsteOffeneJeMelder wie viele unerledigte Meldungen ein Spieler gleichzeitig
 *                              haben darf
 * @param zeilenJeSeite         wie viele Meldungen {@code /meldung liste} auf einmal zeigt
 */
public record Meldewerte(boolean an, long abklingzeitSekunden,
                         int hoechsteOffeneJeMelder, int zeilenJeSeite) {

    /** Ob ueberhaupt gemeldet werden kann. */
    public static final String PFAD_AN = "moderation.melden.an";

    /** Die Wartezeit zwischen zwei Meldungen desselben Spielers. */
    public static final String PFAD_ABKLINGZEIT = "moderation.melden.abklingzeit-sekunden";

    /** Wie viele unerledigte Meldungen ein Spieler haben darf. */
    public static final String PFAD_HOECHSTE_OFFENE =
            "moderation.melden.hoechste-offene-je-melder";

    /** Wie viele Zeilen die Liste auf einmal zeigt. */
    public static final String PFAD_ZEILEN_JE_SEITE = "moderation.melden.zeilen-je-seite";

    /**
     * Die hoechste erlaubte Wartezeit - eine Stunde.
     *
     * <p>Eine Grenze, weil ein vertippter Wert sonst das Meldewesen abschaltet, ohne dass
     * der Schalter {@code an} auf {@code false} steht: Wer {@code 3600000} statt
     * {@code 60} tippt, hat ein Meldewesen, das einmal in 41 Tagen eine Meldung annimmt -
     * und niemand findet die Ursache, weil {@code an: true} dasteht.
     */
    public static final long LAENGSTE_ABKLINGZEIT = 3600L;

    /** Wie viele offene Meldungen hoechstens erlaubt sein duerfen. */
    public static final int HOECHSTE_GRENZE = 100;

    /** Wie viele Zeilen eine Seite hoechstens haben darf. */
    public static final int HOECHSTE_ZEILEN = 50;

    /**
     * Die Vorgabe des Programms.
     *
     * <p><b>60 Sekunden und drei offene Meldungen</b>, und beide Zahlen sind gegen den
     * Streitfall gewaehlt, nicht gegen den Angreifer: Wer wirklich drei verschiedene
     * Leute zu melden hat, kommt damit durch; wer im Streit dieselbe Person bombardiert,
     * haengt nach drei Versuchen. Die vierte Meldung wird nicht verweigert, sondern
     * verschoben - sie geht, sobald eine der drei bearbeitet ist, und das ist die
     * Botschaft, die der Melder liest.
     */
    public static final Meldewerte VORGABE = new Meldewerte(true, 60L, 3, 10);

    public Meldewerte {
        if (abklingzeitSekunden < 0L || abklingzeitSekunden > LAENGSTE_ABKLINGZEIT) {
            throw new IllegalArgumentException("Die Abklingzeit liegt zwischen 0 und "
                    + LAENGSTE_ABKLINGZEIT + " Sekunden, nicht " + abklingzeitSekunden);
        }
        if (hoechsteOffeneJeMelder < 1 || hoechsteOffeneJeMelder > HOECHSTE_GRENZE) {
            throw new IllegalArgumentException("Erlaubt sind 1 bis " + HOECHSTE_GRENZE
                    + " offene Meldungen je Melder, nicht " + hoechsteOffeneJeMelder
                    + ". Eine 0 waere ein abgeschaltetes Meldewesen mit an: true - wer"
                    + " keines will, nimmt den Schalter");
        }
        if (zeilenJeSeite < 1 || zeilenJeSeite > HOECHSTE_ZEILEN) {
            throw new IllegalArgumentException("Eine Seite hat 1 bis " + HOECHSTE_ZEILEN
                    + " Zeilen, nicht " + zeilenJeSeite);
        }
    }

    /** Ob gemeldet werden kann. */
    public boolean greift() {
        return an;
    }

    /**
     * Was an diesen Werten auffaellt, ohne dass es ein Fehler waere.
     *
     * <p>Ein Bericht und keine Klage - dieselbe Linie wie bei
     * {@link Teamwerte#ungereimtheiten()}: Alles hier ist erlaubt, und im Spiel merkt es
     * niemand, bis es zu spaet ist.
     */
    public List<String> ungereimtheiten() {
        List<String> saetze = new ArrayList<>();
        if (!an) {
            saetze.add(PFAD_AN + " ist aus - /melden erscheint nicht, und eine Meldung"
                    + " laesst sich nicht abgeben. Was schon in der Datenbank steht,"
                    + " bleibt stehen und ist ueber /meldung weiter zu bearbeiten.");
        }
        if (an && abklingzeitSekunden == 0L) {
            saetze.add(PFAD_ABKLINGZEIT + " ist 0 - ein Spieler kann beliebig schnell"
                    + " melden. Die Bremse gegen zwanzig Meldungen im Streit ist dann"
                    + " allein " + PFAD_HOECHSTE_OFFENE + ".");
        }
        if (an && abklingzeitSekunden > 600L) {
            saetze.add(PFAD_ABKLINGZEIT + " ist " + abklingzeitSekunden + " Sekunden ("
                    + (abklingzeitSekunden / 60L) + " Minuten). Wer waehrend eines"
                    + " laufenden Vorfalls ein zweites Mal melden will, kann es nicht -"
                    + " und ein Vorfall dauert selten zehn Minuten.");
        }
        if (an && hoechsteOffeneJeMelder == 1) {
            saetze.add(PFAD_HOECHSTE_OFFENE + " ist 1 - wer eine unerledigte Meldung hat,"
                    + " kann nichts weiter melden, auch nicht ueber eine andere Person."
                    + " Bei langsamer Bearbeitung ist das Meldewesen damit fuer den"
                    + " Melder zu.");
        }
        return List.copyOf(saetze);
    }

    /** Dieselben Werte mit anderem Schalter - fuer das Neuladen und fuer Proben. */
    public Meldewerte mitAn(boolean anders) {
        return new Meldewerte(anders, abklingzeitSekunden, hoechsteOffeneJeMelder,
                zeilenJeSeite);
    }

    /** Dieselben Werte mit anderer Abklingzeit. */
    public Meldewerte mitAbklingzeit(long sekunden) {
        return new Meldewerte(an, sekunden, hoechsteOffeneJeMelder, zeilenJeSeite);
    }

    /** Dieselben Werte mit anderer Hoechstzahl offener Meldungen. */
    public Meldewerte mitHoechsteOffene(int anzahl) {
        return new Meldewerte(an, abklingzeitSekunden, anzahl, zeilenJeSeite);
    }

    /** Dieselben Werte mit anderer Seitenlaenge. */
    public Meldewerte mitZeilenJeSeite(int zeilen) {
        return new Meldewerte(an, abklingzeitSekunden, hoechsteOffeneJeMelder, zeilen);
    }

    /** Ob aus dieser Zahl eine Abklingzeit werden kann - fuer den Leser der Datei. */
    public static boolean brauchbareAbklingzeit(long sekunden) {
        return sekunden >= 0L && sekunden <= LAENGSTE_ABKLINGZEIT;
    }

    /** Ob aus dieser Zahl eine Hoechstzahl offener Meldungen werden kann. */
    public static boolean brauchbareOffene(long anzahl) {
        return anzahl >= 1L && anzahl <= HOECHSTE_GRENZE;
    }

    /** Ob aus dieser Zahl eine Seitenlaenge werden kann. */
    public static boolean brauchbareZeilen(long zeilen) {
        return zeilen >= 1L && zeilen <= HOECHSTE_ZEILEN;
    }
}
