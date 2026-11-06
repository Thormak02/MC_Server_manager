package de.freimarken.kern;

import java.util.ArrayList;
import java.util.List;

/**
 * Die einzige veraenderliche Zelle des Plugins - was die Wirtschaft gerade kostet.
 *
 * <p>Jede Stelle, die mit Geld rechnet, haelt diesen Stand als unveraenderliches Feld und
 * fragt ihn im Augenblick des Gebrauchs. Darin liegt der ganze Trick des Neuladens: Ein
 * neuer Wert erreicht jede Stelle, ohne dass ein einziges Objekt neu gebaut wird. EIN
 * NEULADEN meldet keinen Zuhoerer an, stellt keinen Zeitgeber, haengt keinen Befehl neu ein
 * und tauscht keine Verbindung - also gibt es auch nichts abzumelden, und nichts kann doppelt
 * dastehen. Das ist der belegte Fehler bei einem Neuladen, das Objekte neu baut.
 *
 * <p>"Stellt keinen Zeitgeber" gilt fuer das NEULADEN und nicht fuer den Start: Der
 * Spielzeittakt wird in {@code onEnable} gestellt und in {@code onDisable} abgemeldet. Genau
 * deshalb darf sein Abstand ({@link Zeitwerte#PFAD_TAKT}) nicht neuladbar sein - nach zwei
 * Neuladen liefen sonst drei Zeitgeber und schrieben dieselbe Spielzeit dreimal.
 *
 * <p>{@code volatile} auf einem unveraenderlichen {@code record}: Die Vervollstaendigung
 * von Befehlen und das Senden des Befehlsbaums laufen ausdruecklich nebenlaeufig, und ein
 * Spieler kann mitten im Tausch kaufen. So sieht jeder Leser entweder den alten oder den
 * neuen Satz - nie eine Mischung und nie einen halb gefuellten.
 */
public final class Wirtschaftsstand {

    /**
     * Was ein Neuladen bewirkt hat - und was es ausdruecklich nicht angefasst hat.
     *
     * <p>Beide Listen sind fertige Klartextzeilen. Der zweite Teil ist das Gegenmittel
     * gegen das haeufigste Fehlerbild eines Neuladens: Der Befehl meldet Erfolg, ein Teil
     * wirkt aber erst nach dem Neustart. Er erscheint deshalb auch dann, wenn sich nichts
     * geaendert hat.
     */
    public record Uebernahme(List<String> geaendert, List<String> nichtAngefasst) {
    }

    /**
     * Die Schluessel, die ein Neuladen NICHT uebernimmt - die EINE Liste davon.
     *
     * <p>Sie steht in der Klasse, die es entscheidet: {@link #uebernimm} laesst genau diese
     * drei stehen, und {@link #nichtAngefasst} nennt genau diese drei. Der Leser der
     * Einstellungsdatei fragt dieselbe Liste, damit er fuer eine FEHLENDE Zeile den
     * richtigen Satz schreibt - fuer einen neuladbaren Schluessel "es gilt 4", fuer einen
     * von diesen hier nicht, denn was gerade gilt, weiss nur dieser Halter. Gab es die
     * Liste zweimal, sagten Leser und Halter in derselben Antwort Verschiedenes; genau das
     * ist einmal passiert ("Sie gelten aus den Vorgaben des Plugins" neben "es gilt weiter
     * America/New_York").
     *
     * <p>{@link Zeitwerte#PFAD_UNTAETIG} steht hier mit Absicht NICHT: Die
     * Untaetigkeitsgrenze ist neuladbar. Nur das eine Paar, das zum laufenden Takt nicht
     * passt, bleibt stehen - und das ist keine Eigenschaft des Schluessels, sondern eine
     * des Augenblicks, deshalb entsteht die Zeile dazu in {@link #nichtAngefasst} und nicht
     * aus dieser Liste.
     */
    public static final List<String> NUR_BEIM_START = List.of(
            Wirtschaftswerte.PFAD_STARTGUTHABEN, Zeitwerte.PFAD_ZONE, Zeitwerte.PFAD_TAKT);

    private volatile Wirtschaftswerte stand;

    public Wirtschaftsstand(Wirtschaftswerte anfang) {
        if (anfang == null) {
            throw new IllegalArgumentException("Ohne Anfangsstand gibt es nichts zu halten");
        }
        this.stand = anfang;
    }

    /** Was JETZT gilt. Beim Gebrauch zu fragen und nicht in ein Feld zu kopieren. */
    public Wirtschaftswerte werte() {
        return stand;
    }

    /**
     * Uebernimmt, was neu gelesen wurde - und laesst stehen, was ein Neuladen nicht
     * aendern darf.
     *
     * <p>Das Startguthaben bleibt mit Absicht aus dem Tausch heraus: Die Kassen geben es
     * bei jeder Abfrage als Vorgabe fuer ein noch nicht eroeffnetes Konto zurueck, und ein
     * neuer Wert wuerde damit rueckwirkend aendern, was ein Spieler mitbringt, der noch
     * nie gebucht hat. Es hier stehen zu lassen ist billiger als ein Bestandsschutz, und
     * die Begruendung steht an derselben Stelle wie die Entscheidung.
     *
     * <p>Von den {@link Zeitwerte} wird nur die Untaetigkeitsgrenze getauscht. Die ZONE und
     * der TAKT bleiben stehen, jede aus eigenem Grund:
     * <ul>
     *   <li>Die Zone verschiebt die Tagesgrenze. Heute waere ein Tausch im Betrieb noch
     *       harmlos - zwei Zeilen fuer einen Kalendertag summieren sich richtig. Mit dem
     *       ersten Verbraucher der Grenze (Tagesbonus, Ladenmiete, Verdienstdeckel) rechnete
     *       eine Umstellung mitten am Tag denselben Tag zweimal ab oder liesse ihn ausfallen.
     *       Neuladbar zu machen, was es mit dem naechsten Schritt nicht mehr sein darf, heisst
     *       die Regel zweimal zu bauen.</li>
     *   <li>Der Takt steckt im gestellten Zeitgeber. Ihn beim Neuladen neu zu stellen waere
     *       genau der Fehler, gegen den diese Klasse gebaut ist: Nach zwei Neuladen liefen
     *       drei Zeitgeber und schrieben dieselbe Spielzeit dreimal.</li>
     *   <li>Die Untaetigkeitsgrenze dagegen wird beim Gebrauch gefragt. Kein Objekt wird neu
     *       gebaut, nichts angemeldet - also ist sie neuladbar, mit genau einer Ausnahme.</li>
     * </ul>
     *
     * <p><b>Die eine Ausnahme bei der Grenze.</b> Hier entsteht das Paar (LAUFENDER Takt,
     * NEUE Grenze), und das kann unbrauchbar sein, obwohl das Paar der Datei stimmt: Start
     * mit {@code takt-sekunden: 600} und {@code untaetig-minuten: 10}, dann beides auf 60
     * und 1 geaendert (ein gueltiges Paar) und neu geladen - der Takt bleibt bei 600, und
     * 1 Minute Grenze wuerde dazu jeden Takt ganz wegwerfen, auch die aktiv gespielte Zeit
     * darin ({@link Zeitwerte#brauchbareGrenzeZumTakt}). Vorher nahm dieser Weg die
     * Kombination stumm an; den Konstruktor werfen zu lassen waere nicht besser, denn das
     * brach den Befehl mitten im Lauf ab. Also bleibt die alte Grenze stehen, und
     * {@link #nichtAngefasst} sagt es in einer Zeile mit dem Grund. Das ist kein Bruch der
     * Zusage "alles oder nichts": Eine unbrauchbare Zeile faengt der Leser, und bei einer
     * Klage kommt es hier gar nicht an; dieses Paar ist keine unbrauchbare Zeile, sondern
     * eine, die erst mit dem Neustart passt - also derselbe Fall wie Zone und Takt.
     *
     * @param ausDerDatei ein vollstaendig gelesener und gepruefter Satz
     * @return welche Zeilen sich geaendert haben und welche nicht angefasst wurden
     */
    public Uebernahme uebernimm(Wirtschaftswerte ausDerDatei) {
        if (ausDerDatei == null) {
            throw new IllegalArgumentException("Nichts gelesen heisst nichts uebernehmen");
        }
        Wirtschaftswerte alt = stand;
        // Gefragt wird am LAUFENDEN Takt, nicht am Takt der Datei - der steckt im
        // gestellten Zeitgeber und wechselt erst mit dem Neustart.
        boolean grenzePasst = Zeitwerte.brauchbareGrenzeZumTakt(alt.zeit().taktSekunden(),
                ausDerDatei.zeit().untaetigMinuten());
        Zeitwerte zeit = new Zeitwerte(alt.zeit().zone(), alt.zeit().takt(),
                grenzePasst ? ausDerDatei.zeit().untaetigNach()
                        : alt.zeit().untaetigNach());
        Wirtschaftswerte neu = new Wirtschaftswerte(ausDerDatei.preise(),
                alt.startguthaben(), ausDerDatei.hoechstzahl(), ausDerDatei.gruppenFelder(),
                ausDerDatei.rangWerbung(), zeit, ausDerDatei.berufe());

        List<String> geaendert = new ArrayList<>();
        nenneZahl(geaendert, Wirtschaftswerte.PFAD_GRUNDPREIS,
                alt.preise().grundpreis(), neu.preise().grundpreis());
        nenneZahl(geaendert, Wirtschaftswerte.PFAD_ZUSAMMENLEGUNG,
                alt.preise().zusammenlegungJeFeld(), neu.preise().zusammenlegungJeFeld());
        nenneZahl(geaendert, Wirtschaftswerte.PFAD_HOECHSTZAHL,
                alt.hoechstzahl(), neu.hoechstzahl());
        nenneZahl(geaendert, Wirtschaftswerte.PFAD_GRUPPEN_FELDER,
                alt.gruppenFelder(), neu.gruppenFelder());
        nenneZahl(geaendert, Zeitwerte.PFAD_UNTAETIG,
                alt.zeit().untaetigMinuten(), neu.zeit().untaetigMinuten());
        // Die vier Berufszahlen werden VOLLSTAENDIG getauscht, keine steht in
        // NUR_BEIM_START: Alle vier werden beim Gebrauch gefragt, und es wird nichts
        // gebaut und nichts angemeldet. Eine kuerzere Frist gilt sofort, eine kleinere
        // Platzzahl nimmt niemandem einen Beruf weg - er kann nur keinen neuen mehr
        // waehlen, bis er unter der Zahl liegt.
        nenneZahl(geaendert, Berufswerte.PFAD_WECHSELGEBUEHR,
                alt.berufe().wechselgebuehr(), neu.berufe().wechselgebuehr());
        nenneZahl(geaendert, Berufswerte.PFAD_SPERRFRIST,
                alt.berufe().sperrfristStunden(), neu.berufe().sperrfristStunden());
        nenneZahl(geaendert, Berufswerte.PFAD_GRUNDBERUFE,
                alt.berufe().grundberufe(), neu.berufe().grundberufe());
        nenneZahl(geaendert, Berufswerte.PFAD_SPEZIALISIERUNGEN,
                alt.berufe().spezialisierungen(), neu.berufe().spezialisierungen());
        if (!alt.rangWerbung().equals(neu.rangWerbung())) {
            geaendert.add(Wirtschaftswerte.PFAD_RANG_WERBUNG + ": \"" + alt.rangWerbung()
                    + "\" -> \"" + neu.rangWerbung() + "\"");
        }

        List<String> nichtAngefasst = nichtAngefasst(ausDerDatei);
        stand = neu;
        return new Uebernahme(geaendert, nichtAngefasst);
    }

    /**
     * Was von diesem Stand ein Neuladen nicht anfasst - auch wenn gar nichts uebernommen
     * wurde.
     *
     * <p>Oeffentlich und ohne Nebenwirkung, weil der Block in JEDER Antwort stehen muss:
     * auch dann, wenn eine Klage das Uebernehmen verhindert hat. Sonst erfaehrt gerade
     * der Betreiber, bei dem etwas schiefging, nicht, dass Startguthaben, Tagesgrenze und
     * Takt ohnehin nicht neuladbar sind - und sucht den Fehler an der falschen Zeile.
     *
     * <p>Jede Zeile nennt zuerst, was GILT, und erst dann, was neu gelesen wurde. Der Leser
     * der Einstellungsdatei darf das nicht tun: Er kennt nur die Datei.
     *
     * @param ausDerDatei was gelesen wurde, oder {@code null}, wenn nichts gilt - dann
     *                    nennt die Zeile nur, was gerade gilt
     */
    public List<String> nichtAngefasst(Wirtschaftswerte ausDerDatei) {
        Wirtschaftswerte jetzt = stand;
        List<String> zeilen = new ArrayList<>();
        nurBeimStart(zeilen, Wirtschaftswerte.PFAD_STARTGUTHABEN,
                String.valueOf(jetzt.startguthaben()),
                ausDerDatei == null ? null : String.valueOf(ausDerDatei.startguthaben()),
                "Ein neuer Wert im Betrieb aenderte rueckwirkend das Guthaben jedes"
                        + " Spielers, der noch nie gebucht hat.");
        nurBeimStart(zeilen, Zeitwerte.PFAD_ZONE, jetzt.zeit().zone().getId(),
                ausDerDatei == null ? null : ausDerDatei.zeit().zone().getId(),
                "Eine neue Zone im Betrieb verschoebe die Tagesgrenze mitten am Tag;"
                        + " derselbe Tag wuerde dann zweimal abgerechnet oder fiele aus.");
        nurBeimStart(zeilen, Zeitwerte.PFAD_TAKT, jetzt.zeit().taktSekunden() + " s",
                ausDerDatei == null ? null : ausDerDatei.zeit().taktSekunden() + " s",
                "Der Abstand steckt im gestellten Zeitgeber; ihn neu zu stellen liesse zwei"
                        + " Zeitgeber dieselbe Spielzeit doppelt schreiben.");
        // Die vierte Zeile entsteht nur im Ausnahmefall von uebernimm(): Die Grenze IST
        // neuladbar, dieses eine Paar passt aber nicht zum laufenden Takt. Dieselbe Frage
        // wie dort, an derselben einen Regel - nur einmal fuer die Tat und einmal fuer das
        // Wort, denn nichtAngefasst() wird auch ohne Uebernahme gerufen.
        if (ausDerDatei != null && !Zeitwerte.brauchbareGrenzeZumTakt(
                jetzt.zeit().taktSekunden(), ausDerDatei.zeit().untaetigMinuten())) {
            zeilen.add(Zeitwerte.PFAD_UNTAETIG + ": es gilt weiter "
                    + jetzt.zeit().untaetigMinuten() + " min, neu gelesen wurde "
                    + ausDerDatei.zeit().untaetigMinuten() + " min - NICHT uebernommen. Die"
                    + " Grenze ist eine Stufe und kein Schnitt: Zum laufenden Takt von "
                    + jetzt.zeit().taktSekunden() + " s wuerde sie den GANZEN Takt"
                    + " wegwerfen, auch die aktiv gespielte Zeit darin. Mindestens "
                    + Math.ceilDiv(jetzt.zeit().taktSekunden(), 60L) + " min passen zu"
                    + " diesem Takt, 0 schaltet die Regel ab - oder " + Zeitwerte.PFAD_TAKT
                    + " kuerzen und neu starten, dann uebernimmt der Start beides zusammen.");
        }
        return zeilen;
    }

    /**
     * Eine Zeile des Blocks - fuer alle drei Schluessel dieselbe Form und dasselbe
     * Vergleichen.
     *
     * <p>Sie sagt bewusst NICHT "in der Datei steht", sondern "neu gelesen wurde". Der
     * Unterschied ist gemessen und nicht Geschmack: Fehlt die Zeile in der Datei, holt
     * {@code get} die Vorgabe stumm aus der Jar, und der Leser reicht sie hier herein. Die
     * Antwort lautete dann "es gilt weiter America/New_York, in der Datei steht
     * Europe/Berlin" - in der Datei stand keine Zeile, und der Betreiber suchte sie. Ob ein
     * Wert gelesen oder zurueckgefallen ist, sieht allein der Leser; er nennt die fehlenden
     * Zeilen selbst und schreibt fuer sie keinen Geltungssatz (siehe {@link #NUR_BEIM_START}).
     *
     * @param gilt    was gerade gilt - Klartext, damit der Takt seine Einheit behaelt
     * @param gelesen was der Leser mitgebracht hat, oder {@code null}, wenn nichts gilt
     * @param grund   warum ein Neuladen das nicht anfasst
     */
    private static void nurBeimStart(List<String> zeilen, String pfad, String gilt,
                                     String gelesen, String grund) {
        if (gelesen == null || gelesen.equals(gilt)) {
            zeilen.add(pfad + ": " + gilt + " - wird nur beim Start gelesen.");
            return;
        }
        zeilen.add(pfad + ": es gilt weiter " + gilt + ", neu gelesen wurde " + gelesen
                + " - erst ein Neustart uebernimmt das. " + grund);
    }

    private static void nenneZahl(List<String> hinein, String pfad, long alt, long neu) {
        if (alt != neu) {
            hinein.add(pfad + ": " + alt + " -> " + neu);
        }
    }
}
