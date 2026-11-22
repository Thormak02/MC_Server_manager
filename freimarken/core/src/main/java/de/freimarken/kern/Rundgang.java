package de.freimarken.kern;

import java.time.Clock;
import java.util.UUID;

/**
 * Der Rundgang durch Altfurt - die EINE Stelle, die seinen Stand kennt (Plan 10.2).
 *
 * <p><b>Drei Wege fragen ihn</b>, und darum steht er hier und nicht dreimal: die Wache
 * beim Betreten einer Station (sie traegt ein und sagt, was zu lesen ist), der
 * Aufstiegslauf (ist er abgeschlossen?) und {@code /rang} ueber dieselbe Messung. Drei
 * Fassungen derselben Rechnung wichen eines Tages ab - und dann waere ein Spieler
 * aufgestiegen, dem {@code /rang} noch eine fehlende Station anzeigt.
 *
 * <p><b>Das Verzeichnis kommt aus dem START und der Stand aus der Ablage.</b> Beides ist
 * hier zusammengefuehrt, weil keine der beiden Haelften die Frage allein beantworten kann:
 * Die Ablage weiss nicht, welche Stationen es GIBT, und das Verzeichnis nicht, wer wo war.
 *
 * <p><b>Die Uhr kommt von aussen</b>, wie bei jeder Stelle dieses Moduls: Eine eigene
 * {@code Instant.now()} liesse sich nicht pruefen, ohne zu warten.
 *
 * <p><b>Was hier NICHT steht:</b> eine Belohnung. Plan 10.2 nennt am Ende des Rundgangs
 * "die erste Parzelle und ein Startguthaben" - beides gibt es schon und ohne ihn (die
 * erste Parzelle ist gratis, das Startguthaben kommt mit dem Konto). Der Rundgang zahlt
 * also nichts aus; was er bewirkt, ist die Rangbedingung und das, was ein Spieler auf dem
 * Weg erfaehrt. Eine erfundene Belohnung waere eine Zusage, die in keinem Plan steht.
 */
public final class Rundgang {

    /** Was aus einem Schritt in eine Station wird. */
    public enum Bescheid {

        /** Er hat die Station zum ersten Mal betreten - sie ist jetzt eingetragen. */
        NEU,

        /**
         * Er war schon da.
         *
         * <p>Der haeufigste Fall im Betrieb, denn ein Spieler laeuft durch dieselben
         * Gebaeude immer wieder. Darum geschieht hier NICHTS - kein Satz, kein
         * Schreibzug. Ein Stationstext bei jedem Betreten waere eine Plage.
         */
        SCHON_GESEHEN,

        /**
         * Er hat mit dieser Station die LETZTE gesehen - der Rundgang ist fertig.
         *
         * <p>Ein eigener Bescheid und nicht {@code NEU} mit einer Zusatzfrage: Der
         * Abschluss ist der Augenblick, in dem die Rangbedingung umschlaegt, und er darf
         * genau einmal gemeldet werden. Haette der Aufrufer selbst gezaehlt, meldete er
         * ihn bei jedem spaeteren Schritt erneut.
         */
        ABGESCHLOSSEN
    }

    private final Rundgangsablage ablage;
    private final Rundgangsverzeichnis verzeichnis;
    private final Clock uhr;

    public Rundgang(Rundgangsablage ablage, Rundgangsverzeichnis verzeichnis, Clock uhr) {
        if (ablage == null || verzeichnis == null || uhr == null) {
            throw new IllegalArgumentException("Der Rundgang braucht Ablage, Verzeichnis"
                    + " und Uhr - das Verzeichnis sagt, welche Stationen es gibt, und die"
                    + " Ablage, wer wo war");
        }
        this.ablage = ablage;
        this.verzeichnis = verzeichnis;
        this.uhr = uhr;
    }

    /** Ob es ueberhaupt einen Rundgang gibt - ab Werk nicht. */
    public boolean gibtEs() {
        return !verzeichnis.leer();
    }

    /** Wie viele Stationen der Weg hat. */
    public int stationen() {
        return verzeichnis.anzahl();
    }

    /** Das Verzeichnis - fuer die Wache, die den Ort einer Station braucht. */
    public Rundgangsverzeichnis verzeichnis() {
        return verzeichnis;
    }

    /**
     * Wie viele Stationen dieser Spieler gesehen hat - von denen, die es HEUTE gibt.
     *
     * <p>Gezaehlt wird gegen das Verzeichnis: Nimmt der Betreiber eine Station heraus,
     * zaehlt eine Zeile, die auf sie zeigt, nicht mehr mit. Fuegt er eine hinzu, fehlt
     * sie jedem - und das ist richtig, denn ein Weg mit einer neuen Station ist ein neuer
     * Weg.
     */
    public int gesehen(UUID spieler) {
        return verzeichnis.davonVorhanden(ablage.gesehen(spieler));
    }

    /**
     * Ob der Rundgang abgeschlossen ist.
     *
     * <p><b>Die Frage, die der Aufstiegslauf stellt.</b> Ohne Stationen ist sie
     * {@code false} - und zwar fuer jeden: Ein Rundgang, den es nicht gibt, ist nicht
     * abgeschlossen, sondern gar nicht vorhanden. Waere er mit null Stationen "fertig",
     * waere eine Rangbedingung darauf ab Werk fuer jeden erfuellt, und der Betreiber
     * haette eine Stufe verschenkt, ohne eine Zeile zu schreiben.
     */
    public boolean abgeschlossen(UUID spieler) {
        return gibtEs() && gesehen(spieler) >= verzeichnis.anzahl();
    }

    /**
     * Traegt eine betretene Station ein und sagt, was daraus wurde.
     *
     * <p>Gerufen von der Wache bei jedem Schritt in einen Stationsbereich - also oft.
     * Darum ist der haeufigste Fall der billigste: Wer schon da war, bekommt
     * {@link Bescheid#SCHON_GESEHEN}, und es wird nichts geschrieben.
     *
     * @param station der Name der Station, wie das Verzeichnis ihn fuehrt
     */
    public Bescheid betreten(UUID spieler, String station) {
        if (verzeichnis.mitNamen(station) == null) {
            throw new IllegalArgumentException("Die Station \"" + station + "\" steht"
                    + " nicht im Verzeichnis. Wer hierher kommt, hat sie nicht ueber"
                    + " anDemOrt gefunden - und dann traegt er etwas ein, das es nicht"
                    + " gibt.");
        }
        if (!ablage.merke(spieler, station, uhr.instant())) {
            return Bescheid.SCHON_GESEHEN;
        }
        return abgeschlossen(spieler) ? Bescheid.ABGESCHLOSSEN : Bescheid.NEU;
    }
}
