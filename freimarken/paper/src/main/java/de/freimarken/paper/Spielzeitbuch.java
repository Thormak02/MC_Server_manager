package de.freimarken.paper;

import de.freimarken.kern.Spielzeitablage;
import de.freimarken.kern.Spielzeitregel;
import de.freimarken.kern.Tagesgrenze;
import de.freimarken.kern.Wirtschaftsstand;
import de.freimarken.kern.Zeitwerte;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Logger;

/**
 * Wie weit die Spielzeit jedes anwesenden Spielers fortgeschrieben ist - und das Abrechnen.
 *
 * <p>Die ganze Rechnung liegt hier und kennt kein Bukkit. Derselbe Schnitt wie bei
 * {@link Abklingzeit} und {@link Anfragen}: Was ohne Server pruefbar ist, soll es auch
 * sein. Das Spiel liefert nur zwei Zahlen herein - wer da ist und wie lange er schon nichts
 * getan hat -, und das besorgt {@link Spielzeitwache}.
 *
 * <p>Nicht im Kern, obwohl nichts von Bukkit darin steht: Der Kern haelt keinen Zustand
 * einer laufenden Sitzung. Die Marke ist genau das - sie entsteht beim Beitritt und endet
 * beim Abmelden. Was davon dauerhaft wird, geht durch {@link Spielzeitablage}.
 *
 * <p><b>Die Marke liegt im Arbeitsspeicher und braucht kein {@code volatile}.</b> Alles
 * hier laeuft im Hauptfaden des Servers: der Zeitgeber, der Beitritt, das Abmelden, der
 * Befehl. Das ist keine Bequemlichkeit, sondern Pflicht - die Datenschicht haelt EINE
 * JDBC-Verbindung, und MariaKasse und MariaLager schalten darauf {@code autoCommit} um. Ein
 * zweiter Faden committete dort die halbe Geldbuchung des Hauptfadens mit, und damit faellt
 * die Zusage "Summe aller Buchungen gleich Kontostand". Eine verlorene Spielminute ist
 * dagegen harmlos.
 *
 * <p><b>Es wird nie wiederholt.</b> Fortschreiben ist additiv: Ein zweiter Schreibzug mit
 * derselben Liste zaehlt doppelt. Nach einem Fehler steht deshalb die verlorene
 * Sekundenzahl im Protokoll, und sonst geschieht nichts - zu viel Spielzeit gibt einen Rang
 * zu frueh, zu wenig nur zu spaet. Anders als beim Geld gibt es hier keine Summenprobe, an
 * der ein Doppel auffiele.
 *
 * <p><b>Nach einem Fehlschlag setzt der Takt das SCHREIBEN aus - nicht das Rechnen.</b> Ein
 * Schreibzug in eine unerreichbare Datenbank haelt den Hauptfaden an, solange die Verbindung
 * ihre Fristen abwartet - erst die Pruefung der alten Verbindung, dann der Aufbau einer
 * neuen, zusammen rund fuenf Sekunden je Versuch. Ohne Sperre waere das bei JEDEM Takt
 * erneut so, ohne dass ein Spieler etwas tut; beim kuerzesten erlaubten Takt von 10 s stuende
 * der Server die halbe Zeit. Schaerfer noch die Massenabmeldung: Ein Proxy-Neustart meldet
 * vierzig Spieler in EINEM Tick ab, und vierzig solche Fristen hintereinander sind mehr als
 * die Frist des Serverwaechters - der Server wird mitten im Abrechnen abgeschossen, und dann
 * ist die Zeit aller vierzig weg statt der einer.
 *
 * <p><b>Verloren geht durch die Sperre nichts.</b> Ein ausgesetzter Taktschlag rechnet wie
 * jeder andere und rueckt die Marken vor; nur sein Schreibzug bleibt aus, und seine
 * Zuschlaege warten im Stapel ({@link #wartend}) auf den naechsten wirklichen Schreibzug.
 * Ein Abgang waehrend der Sperre legt seine Zuschlaege in denselben Stapel - sein Spieler
 * ist weg, seine gemessenen Sekunden sind es nicht.
 *
 * <p>Dass der ausgesetzte Schlag RECHNET, ist dabei nicht Bequemlichkeit, sondern der Kern
 * der Sache. Liesse er die Marke stehen, waechse die Spanne mit jedem ausgesetzten Schlag
 * auf bis zu sechs Taktlaengen - und die Untaetigkeitsgrenze ist eine STUFE: Ein einziger
 * untaetiger Schlag am Ende des Fensters warf diese ganze Spanne weg, auch die aktiv
 * gespielte Zeit darin, und zwar stumm, weil ein verworfener Takt Alltag ist und keine
 * Protokollzeile schreibt. So rechnet jeder Schlag ueber genau eine Taktlaenge, und die
 * Zusage von {@link Zeitwerte#brauchbareGrenzeZumTakt} - die Stufe trifft hoechstens EINE
 * Taktlaenge - ist auch waehrend der Sperre wahr.
 *
 * <p>Das Abschalten setzt die Sperre ausdruecklich beiseite: Es ist der letzte Schreibzug,
 * und ist die Datenbank dann wieder da, waere eine Sperre genau der Verlust, den sie
 * verhindern soll.
 */
final class Spielzeitbuch {

    /**
     * Woher das Urteil kommt, ob ein Schlag TAETIG war - die Messstelle aus Teil G.
     *
     * <p>Eine Schnittstelle und nicht die Klasse selbst, aus demselben Grund wie bei
     * {@code Verdienstwache.Taktquelle}: Was dieses Buch rechnet, soll ohne Server pruefbar
     * bleiben, und eine Probe soll "taetig" und "nicht taetig" setzen koennen, ohne Orte und
     * Ereignisse nachzustellen.
     *
     * <p>Der Name sagt beides, weil die Methode beides tut: Sie antwortet UND beginnt den
     * naechsten Schlag. Siehe {@code Messstelle.nimmUndLeere} - zwei getrennte Aufrufe
     * muesste jeder Aufrufer paaren, und wer das Zuruecksetzen vergisst, faerbt mit einem
     * Zeichen den ganzen Abend taetig.
     */
    interface Taetigkeitsquelle {

        /**
         * Ob dieser Spieler im abgelaufenen Schlag taetig war, und beginnt den naechsten.
         *
         * <p>Wird fuer JEDEN abgerechneten Schlag genau einmal gefragt - auch fuer einen,
         * der nicht zaehlt. Sonst truege ein Zeichen aus einem verworfenen Schlag in den
         * naechsten hinein.
         */
        boolean nimmUndLeere(UUID wer);

        /**
         * Vergisst diesen Spieler - seine Sitzung ist zu Ende.
         *
         * <p>Gehoert in diese Schnittstelle und nicht in die Wache, damit es nur EINE
         * Stelle gibt, an der eine Sitzung endet: {@link Spielzeitbuch#schliesse} nimmt
         * die Marke weg und sagt es im selben Zug hierher. Haenge das Vergessen an einem
         * zweiten Zuhoerer auf {@code PlayerQuitEvent}, entschiede die Reihenfolge zweier
         * Prioritaeten darueber, ob der letzte Schlag noch sein Urteil bekommt.
         */
        void vergiss(UUID wer);

        /**
         * Eine Quelle, die nie taetig meldet - fuer Proben, die von Teil G nichts wissen.
         *
         * <p>Gibt immer {@code false} und nicht immer {@code true}: Untermessen ist die
         * sichere Richtung, und eine Probe, die versehentlich an dieser Quelle haengt, soll
         * keine taetige Zeit erfinden.
         *
         * <p>Eine ausgeschriebene Fassung und kein Lambda, obwohl eines kuerzer waere: Eine
         * Schnittstelle mit zwei Methoden laesst sich nicht als Lambda schreiben, und ein
         * {@code default}-Rumpf fuer das Vergessen waere eine Falle - eine echte Umsetzung
         * koennte ihn erben und stumm nichts vergessen.
         */
        Taetigkeitsquelle KEINE = new Taetigkeitsquelle() {

            @Override
            public boolean nimmUndLeere(UUID wer) {
                return false;
            }

            @Override
            public void vergiss(UUID wer) {
            }
        };
    }

    /**
     * Wie viele Taktschlaege nach einem Fehlschlag nicht geschrieben werden.
     *
     * <p>Keine Einstellung in der {@code config.yml}, sondern eine Eigenschaft dieses
     * Schutzes. Die Zahl tauscht Schreiblast gegen das ALTER der Messung: Waehrend der
     * Sperre wird weitergerechnet, aber nicht geschrieben, ein Absturz in diesem Fenster
     * kostet also nicht eine Taktlaenge je Spieler, sondern bis zu dieser Zahl plus eins.
     * Darum nicht beliebig gross - und darum abgeleitet statt getippt: Die Klemme in
     * {@link Spielzeitregel#KLEMME_IN_TAKTEN} ist die andere Zahl des Programms, die sagt,
     * ueber wie viele Taktlaengen eine Messung noch glaubwuerdig ist. Liefen die beiden
     * auseinander, stuende hier eine Zahl ohne Bezug; die Haelfte laesst Luft nach oben.
     * Ein Schalter in der Einstellungsdatei waere sie auch nicht: Daran hat der Betreiber
     * nichts einzustellen, was er nicht schon mit {@link Zeitwerte#PFAD_TAKT} einstellt.
     *
     * <p><b>Wer diese Zahl aendert, aendert zwei Zusagen mit.</b> Was ein Absturz kostet,
     * steht an den zwei Stellen, die der Betreiber LIEST, und nicht hier: im Javadoc von
     * {@link Zeitwerte#LAENGSTER_TAKT_SEKUNDEN} und im Absatz zu {@link Zeitwerte#PFAD_TAKT}
     * der ausgelieferten {@code config.yml}. Beide nennen heute den Fehlschlag als Bedingung;
     * die Zahl selbst steht dort absichtlich nicht, sondern nur in der Verlustzeile, die sie
     * ohnehin ausrechnet ({@link #melde}).
     */
    private static final int AUSSETZER_NACH_FEHLSCHLAG = Spielzeitregel.KLEMME_IN_TAKTEN / 2;

    /**
     * Bis wohin die Zeit jedes anwesenden Spielers fortgeschrieben ist.
     *
     * <p>Eine nackte {@link HashMap} genuegt, siehe Klassenkopf: Es gibt nur einen Faden.
     * Ein Eintrag entsteht beim Beitritt und verschwindet beim Abmelden - die Karte waechst
     * also nicht ueber die Zahl der anwesenden Spieler hinaus.
     */
    private final Map<UUID, Instant> marken = new HashMap<>();

    /**
     * Gemessene Sekunden, die noch nicht geschrieben sind - der Stapel hinter der Sperre.
     *
     * <p>Im Normalfall lebt er einen Augenblick: Ein Taktschlag legt seine Zuschlaege hier
     * ab und schreibt sie im selben Atemzug weg. Steht die Sperre, waechst der Stapel um
     * einen Schlag je Taktlaenge, und der naechste wirkliche Schreibzug nimmt ihn mit - in
     * EINEM Aufruf, wie {@link Spielzeitablage#schreibeFort} es zusichert. Er waechst
     * dadurch nicht ueber (Aussetzer + 1) Schlaege je anwesendem Spieler hinaus, denn jeder
     * Versuch leert ihn, ob er gelingt oder nicht.
     *
     * <p>Geleert wird VOR dem Schreibzug. So kann kein Zuschlag zweimal in die Ablage
     * kommen; scheitert der Zug, ist der Stapel verloren und steht in der Verlustzeile.
     */
    private final List<Spielzeitablage.Zuschlag> wartend = new ArrayList<>();

    private final Wirtschaftsstand wirtschaft;
    private final Spielzeitablage ablage;
    private final Taetigkeitsquelle taetigkeit;
    private final Clock uhr;
    private final Logger protokoll;

    /**
     * Wie viele Taktschlaege noch nicht schreiben - 0, solange die Ablage antwortet.
     *
     * <p>Ein {@code int} und kein zweiter Faden, kein Zeitgeber, keine eigene Klasse: Die
     * Zahl wird im Hauptfaden gesetzt und im Hauptfaden gelesen, wie alles hier.
     */
    private int aussetzerUebrig;

    /**
     * @param wirtschaft woher Zone, Takt und Untaetigkeitsgrenze kommen - beim GEBRAUCH
     *                   gefragt und nicht in ein Feld kopiert, damit ein Neuladen die
     *                   Untaetigkeitsgrenze erreicht
     * @param uhr        die Wanduhr. Nie {@code System.nanoTime()}: die hat ueber einen
     *                   Neustart keinen Bezugspunkt. Gegen eine verstellte Wanduhr steht die
     *                   Klemme in {@link Spielzeitregel}
     */
    Spielzeitbuch(Wirtschaftsstand wirtschaft, Spielzeitablage ablage,
                  Taetigkeitsquelle taetigkeit, Clock uhr, Logger protokoll) {
        this.wirtschaft = wirtschaft;
        this.ablage = ablage;
        this.taetigkeit = taetigkeit;
        this.uhr = uhr;
        this.protokoll = protokoll;
    }

    /**
     * Setzt die Marke eines Spielers auf jetzt - beim Beitritt und beim Start des Plugins.
     *
     * <p>Ein zweiter Aufruf setzt die Marke neu, und das ist die sichere Richtung.
     * {@code putIfAbsent} waere die andere: Ueberlebte eine Marke je ihre Sitzung, rechnete
     * der erste Takt nach dem Wiedereinstieg ab der alten Marke, und der Abstand zwischen
     * zwei Sitzungen zaehlte als Spielzeit - bis zu zehn Taktlaengen, als Ausgang GEZAEHLT
     * und damit ohne jede Protokollzeile. Dass eine Marke heute nicht ueberleben kann
     * ({@code PlayerQuitEvent} ist nicht abbrechbar, und ein Wiedereinschalten im Betrieb
     * baut ein neues Buch), macht die Frage nur unwahrscheinlich und nicht unmoeglich.
     *
     * <p>Zurueckgesetzt wird dabei nichts, was zaehlt: Beim Start ist die Karte leer und
     * wird aus den anwesenden Spielern gefuellt; tritt einer in derselben Sekunde bei, ist
     * seit seiner Marke nichts verstrichen. Ein Zuschlag, der noch auf dem Stapel wartet,
     * bleibt dort und geht mit dem naechsten Schreibzug mit - er haengt an einer UUID und
     * nicht an einer laufenden Sitzung.
     */
    void beginne(UUID wer) {
        marken.put(wer, uhr.instant());
    }

    /**
     * Rechnet die genannten Spieler ab und schreibt alles in EINEM Aufruf - der Takt.
     *
     * <p>Die Marken bleiben stehen, nur vorgerueckt. Ein Spieler ohne Marke bekommt eine auf
     * jetzt und zaehlt ab dem naechsten Takt; so kann niemand durch das Raster fallen, der
     * zwischen zwei Takten dazugekommen ist.
     *
     * <p>Ist die Sperre gesetzt, wird GERECHNET und nicht geschrieben: Die Zuschlaege legen
     * sich auf den Stapel und gehen mit dem naechsten wirklichen Schreibzug mit. Warum
     * gerechnet wird und nicht einfach die Marke stehen bleibt, steht im Klassenkopf - eine
     * ueber mehrere Taktlaengen gewachsene Spanne wirft die Untaetigkeitsstufe als GANZES
     * weg.
     *
     * @param untaetigkeiten wer anwesend ist und wie lange er schon untaetig ist
     */
    void schreibeFort(Map<UUID, Duration> untaetigkeiten) {
        Zeitwerte zeit = wirtschaft.werte().zeit();
        Instant jetzt = uhr.instant();
        untaetigkeiten.forEach((wer, untaetig) -> rechneFort(wer, untaetig, jetzt, zeit));
        if (aussetzerUebrig > 0) {
            // Keine Protokollzeile je ausgesetztem Schlag: Die Verlustzeile hat schon
            // gesagt, dass und wie lange nicht geschrieben wird.
            aussetzerUebrig--;
            return;
        }
        schreibe();
    }

    /**
     * Rechnet einen Spieler ab und ENTFERNT seine Marke - das Abmelden.
     *
     * <p>Gerechnet wird ab derselben Marke, die auch der Takt benutzt, und die Marke
     * verschwindet danach. Darum kann dasselbe Zeitstueck nicht zweimal gezaehlt werden,
     * egal in welcher Reihenfolge Takt und Abmelden kommen.
     *
     * <p>Entfernt wird AUCH, wenn das Schreiben scheitert oder wegen der Sperre ausbleibt:
     * Eine Marke eines abgemeldeten Spielers waere sonst ein Eintrag, den niemand mehr
     * abraeumt, und beim naechsten Beitritt zaehlte die Zeit zwischen den Sitzungen mit.
     *
     * <p>Bei gesetzter Sperre wird NICHT geschrieben - und nichts gemeldet, denn nichts ist
     * verloren: Die Zuschlaege warten auf dem Stapel und gehen mit dem naechsten wirklichen
     * Schreibzug mit, spaetestens beim Abschalten. Dass hier nicht geschrieben wird, ist die
     * Haelfte der Massenabmeldung: Vierzig Abgaenge in einem Tick waeren vierzig
     * Schreibzuege, und mit toter Datenbank wartet jeder davon die Aufbaufrist ab - zusammen
     * mehr, als der Serverwaechter zulaesst.
     */
    void schliesse(UUID wer, Duration untaetig) {
        Zeitwerte zeit = wirtschaft.werte().zeit();
        rechneFort(wer, untaetig, uhr.instant(), zeit);
        marken.remove(wer);
        // NACH dem Abrechnen: Der letzte Schlag soll noch sein Urteil bekommen, und
        // rechneFort fragt die Quelle. Davor vergessen hiesse, die Zeichen der letzten
        // Minute wegzuwerfen.
        taetigkeit.vergiss(wer);
        if (aussetzerUebrig > 0) {
            return;
        }
        schreibe();
    }

    /**
     * Rechnet JEDE offene Marke ab und leert die Karte - das Abschalten des Plugins.
     *
     * <p>Geht ueber die Marken und nicht ueber die anwesenden Spieler: Beim Herunterfahren
     * ist nicht gesagt, dass es zu jeder Marke noch ein Spielerobjekt gibt. Fehlt die
     * Untaetigkeitsdauer eines Spielers, gilt er als aktiv - ein Messfehler beim Abschalten
     * darf ihn nicht seine letzte Minute kosten, und mehr als eine Taktlaenge ist es nie.
     *
     * <p>Die Sperre gilt hier ausdruecklich NICHT: Das ist der letzte Schreibzug, und ist
     * die Datenbank inzwischen wieder da, waere ein ausgesetzter Versuch genau der Verlust,
     * den die Sperre verhindern soll. Teuer ist das nicht - es ist EIN Schreibzug fuer alle
     * offenen Sitzungen UND den Stapel, also hoechstens eine Aufbaufrist.
     *
     * @param untaetigkeiten was noch zu messen war; darf Spieler nennen, die keine Marke
     *                       haben, und Marken auslassen
     * @return fuer wie viele der offenen Sitzungen wirklich Sekunden in der Ablage gelandet
     *         sind. Die Zahl ist kleiner als {@link #offeneMarken()} vorher, wenn das
     *         Schreiben scheiterte ODER wenn fuer einen Spieler nichts zu schreiben war -
     *         sein letzter Takt war untaetig oder kuerzer als eine Sekunde. Gezaehlt werden
     *         nur die offenen Sitzungen und nicht alles Geschriebene: Auf dem Stapel koennen
     *         Zuschlaege von Spielern liegen, die sich waehrend der Sperre abgemeldet haben;
     *         sie gehen mit, sind aber keine offene Sitzung, und die Zeile des Abschaltens
     *         darf nicht "3 von 2" sagen. Sie steht auf der Abnahmeliste als Beweis fuer
     *         "0 Verlust"
     */
    int schliesseAlles(Map<UUID, Duration> untaetigkeiten) {
        Zeitwerte zeit = wirtschaft.werte().zeit();
        Instant jetzt = uhr.instant();
        Set<UUID> offene = new LinkedHashSet<>(marken.keySet());
        for (UUID wer : offene) {
            rechneFort(wer, untaetigkeiten.getOrDefault(wer, Duration.ZERO), jetzt, zeit);
        }
        marken.clear();
        // Auch hier nach dem Abrechnen, und fuer JEDE offene Sitzung: Beim Wiedereinschalten
        // im Betrieb entsteht ein neues Buch, aber die Messstelle koennte dieselbe sein -
        // dann waere ein stehengebliebener Ort der Anfang eines Schlages, den es nie gab.
        for (UUID wer : offene) {
            taetigkeit.vergiss(wer);
        }
        Set<UUID> geschrieben = schreibe();
        geschrieben.retainAll(offene);
        return geschrieben.size();
    }

    /**
     * Was fuer diesen Spieler seit dem letzten Takt aufgelaufen ist - fuer die Anzeige.
     *
     * <p>Faehrt die Marke NICHT vor und schreibt nicht. Die Anzeige braucht das, weil
     * {@link Spielzeitablage#lies} nur kennt, was geschrieben ist: Wer beitritt und sofort
     * nachsieht, laese sonst 0 neben "wird gezaehlt".
     *
     * <p>Das ist nur die Spanne seit dem letzten Takt. Was zwischen Ablage und dieser Spanne
     * noch fehlt, nennt {@link #wartende}; die Anzeige braucht beides.
     */
    Spielzeitregel.Urteil offeneSitzung(UUID wer, Duration untaetig) {
        return urteile(wer, untaetig, uhr.instant(), wirtschaft.werte().zeit());
    }

    /**
     * Die gemessenen Zuschlaege dieses Spielers, die noch nicht geschrieben sind - fuer die
     * Anzeige.
     *
     * <p>Ohne sie waere die angezeigte Zahl waehrend der Sperre um bis zu
     * {@code AUSSETZER_NACH_FEHLSCHLAG} Taktlaengen zu klein: {@link Spielzeitablage#lies}
     * kennt nur, was geschrieben ist, und {@link #offeneSitzung} nur, was seit dem letzten
     * Takt dazugekommen ist. Gemessen ist beides und der Stapel dazwischen - und was
     * gemessen ist, soll der Spieler lesen.
     *
     * <p>Die Zuschlaege sind schon nach Tagen geteilt, die Anzeige muss also nichts mehr
     * rechnen, sondern nur noch addieren.
     */
    List<Spielzeitablage.Zuschlag> wartende(UUID wer) {
        List<Spielzeitablage.Zuschlag> seine = new ArrayList<>();
        for (Spielzeitablage.Zuschlag zuschlag : wartend) {
            if (zuschlag.spieler().equals(wer)) {
                seine.add(zuschlag);
            }
        }
        return List.copyOf(seine);
    }

    /** Wie viele Sitzungen offen sind - damit das Abraeumen pruefbar ist. */
    int offeneMarken() {
        return marken.size();
    }

    /**
     * Ob der letzte Schreibzug gescheitert ist und darum Schlaege nicht schreiben.
     *
     * <p>Zwei Leser, und beide braucht es. Die Proben: Eine Sperre, die niemand von aussen
     * sehen kann, waere eine Zusage ohne Probe - dieselbe Begruendung wie bei
     * {@link #offeneMarken()}. Und die ANZEIGE: Sie nennt dem Spieler, in welchem Abstand
     * geschrieben wird, und waehrend der Sperre ist das nicht der eingestellte Takt. Ein
     * oeffentlicher Zustand, den nur Proben lesen, waere eine Falle fuer den naechsten;
     * den Satz dazu holt {@code Spielzeittext.schreibzugWartet(Sprache)} aus der
     * Sprachdatei. Ein pruefbarer Verweis - link-Tag statt code-Tag - wuerde hier nichts
     * sichern: gesperrt() ist paketprivat, javadoc sieht mit der Vorgabe -protected gar
     * nicht hierher, und der Bau ruft javadoc ohnehin nicht. Beides gemessen am
     * 05.11.2026, mit einem absichtlich falschen link-Tag an genau dieser Stelle: Bau
     * und javadoc-Aufgabe blieben still, erst javadoc mit -private meldete
     * "Referenz nicht gefunden". Wer den Namen aendert, muss diesen Satz von Hand
     * nachziehen.
     */
    boolean gesperrt() {
        return aussetzerUebrig > 0;
    }

    /**
     * Ein Spieler: Urteil holen, Marke vorruecken, Zuschlaege auf den Stapel legen.
     *
     * <p>Die Marke wird in JEDEM Fall gesetzt, auch bei einem verworfenen Takt - sonst
     * sparte ein abwesender Spieler seine Stunden an und kassierte sie mit einem Schritt.
     * Wie weit sie rueckt, entscheidet {@link Spielzeitregel} und nicht diese Stelle: bei
     * einem gezaehlten Takt bis auf die ganze Sekunde (der Rest bleibt stehen und zaehlt
     * beim naechsten Mal mit), bei einem verworfenen auf jetzt.
     */
    private void rechneFort(UUID wer, Duration untaetig, Instant jetzt, Zeitwerte zeit) {
        Spielzeitregel.Urteil urteil = urteile(wer, untaetig, jetzt, zeit);
        marken.put(wer, urteil.neueMarke());
        if (urteil.meldepflichtig()) {
            // Untaetigkeit ist Alltag und schweigt; eine verstellte Uhr muss nachlesbar
            // sein, weil es fuer Spielzeit keine Summenprobe gibt.
            protokoll.warning("Spielzeit von " + wer + ": " + urteil.ausgang()
                    + " - gezaehlt wurden " + urteil.sekunden() + " s, die Marke steht"
                    + " jetzt auf " + urteil.neueMarke() + ". Die Wanduhr des Servers"
                    + " wurde verstellt oder der Takt lief lange nicht.");
        }
        // Die Messstelle wird BEDINGUNGSLOS gefragt, auch wenn dieser Schlag nichts
        // zaehlt: Sie beginnt mit derselben Antwort den naechsten Schlag, und ein Zeichen,
        // das aus einem verworfenen Schlag in den naechsten hineintruege, waere ein Weg um
        // die Untaetigkeitsgrenze aus Teil C herum. Vor der Teilung und nicht darin -
        // gefragt wird einmal je Schlag und nicht einmal je Tageshaelfte.
        boolean warTaetig = taetigkeit.nimmUndLeere(wer);
        // Ein Takt ueber Mitternacht wird GETEILT und nicht ganz dem neuen Tag gegeben.
        for (Tagesgrenze.Tagesanteil anteil
                : Tagesgrenze.teile(urteil.von(), urteil.bis(), zeit.zone())) {
            // Ein Schlag ist ganz taetig oder gar nicht; ueber Mitternacht erbt jede
            // Haelfte ihren eigenen Anteil. Darum anteil.sekunden() und nicht
            // urteil.sekunden() - sonst stuende in der ersten Zeile mehr taetige Zeit als
            // gemessene, und der Zuschlag verweigert genau das.
            wartend.add(new Spielzeitablage.Zuschlag(wer, anteil.tag(), anteil.sekunden(),
                    warTaetig ? anteil.sekunden() : 0L));
        }
    }

    /** Dieselbe Rechnung fuer Takt, Abmelden und Anzeige - damit es nur eine gibt. */
    private Spielzeitregel.Urteil urteile(UUID wer, Duration untaetig, Instant jetzt,
                                          Zeitwerte zeit) {
        Instant marke = marken.getOrDefault(wer, jetzt);
        return Spielzeitregel.pruefe(marke, jetzt, untaetig, zeit.untaetigNach(),
                zeit.takt());
    }

    /**
     * Schreibt den Stapel - und faengt den Fehlschlag, ohne zu wiederholen.
     *
     * <p>Bedingungslos aufgerufen, auch mit leerem Stapel: Die Ablage verspricht, dann
     * nichts zu tun - keine Netzrunde, keine leere Transaktion. Eine Pruefung hier waere
     * dieselbe Regel ein zweites Mal.
     *
     * <p>Der Stapel wird VOR dem Zug geleert und nicht erst danach: So kann kein Zuschlag
     * ein zweites Mal in die Ablage kommen, auch nicht auf einem Weg, der erst gefunden
     * wird. Fortschreiben ist additiv, und es gibt keine Summenprobe, an der ein Doppel
     * auffiele.
     *
     * @return wessen Sekunden angekommen sind - gezaehlt werden die geschriebenen
     *         Zuschlaege und nicht die Marken. Wer den ganzen Takt untaetig war, hat keinen
     *         Zuschlag und steht nicht darin; nach einem Fehlschlag ist die Menge leer.
     *         Daran haengt die Erfolgszeile des Abschaltens
     */
    private Set<UUID> schreibe() {
        List<Spielzeitablage.Zuschlag> stapel = List.copyOf(wartend);
        wartend.clear();
        try {
            ablage.schreibeFort(stapel);
        } catch (RuntimeException fehler) {
            // Die Sperre VOR der Meldung, damit die Meldung sie nennen kann.
            aussetzerUebrig = AUSSETZER_NACH_FEHLSCHLAG;
            melde(stapel, fehler.getMessage());
            return new LinkedHashSet<>();
        }
        // Die Ablage antwortet wieder - eine stehende Sperre ist damit beantwortet.
        aussetzerUebrig = 0;
        return betroffene(stapel);
    }

    /**
     * Die EINE Verlustzeile - ein Wortlaut fuer die eine Stelle, an der etwas verloren geht.
     *
     * <p>Gemeldet wird allein der wirkliche Fehlschlag. Ein ausgesetzter Schlag und ein
     * Abgang hinter der Sperre verlieren nichts und schweigen deshalb; eine Zeile, die
     * "verloren" sagt und im naechsten Satz "gar nicht erst versucht", liesse den Betreiber
     * raten, ob die Datenbank geantwortet hat.
     *
     * <p>Die Meldung nennt Sekunden UND Spielerzahl, damit die Zeile vollstaendig ist: Wer
     * sie spaeter liest, kann ausrechnen, was fehlt, ohne den Takt zu kennen.
     *
     * <p>Ein leerer Stapel schweigt. Sonst stuende "0 Sekunden von 0 Spieler(n) sind
     * verloren" im Protokoll - eine Fehlermeldung ueber nichts, und genau an der Stelle, an
     * der ein Betreiber nach einem Verlust sucht.
     *
     * <p>Die Zusage zur Sperre nennt den Absturz ausdruecklich, und das ist keine Floskel:
     * Eine unbedingte Zeile ("gehen nicht verloren") liesse den Betreiber, der mitten im
     * Ausfall nach seinem Schaden sucht, aufhoeren zu suchen - und gerade in diesem Fenster
     * liegen die Sekunden MEHRERER Taktlaengen je Spieler nur im Arbeitsspeicher. Das ist
     * die Stelle, an der diese Zahl entsteht; {@link #AUSSETZER_NACH_FEHLSCHLAG} nennt die
     * zwei anderen Stellen, die dieselbe Zusage machen.
     */
    private void melde(List<Spielzeitablage.Zuschlag> stapel, String grund) {
        if (stapel.isEmpty()) {
            return;
        }
        long sekunden = 0L;
        for (Spielzeitablage.Zuschlag zuschlag : stapel) {
            sekunden += zuschlag.sekunden();
        }
        protokoll.severe("Spielzeit nicht geschrieben: " + sekunden + " Sekunden von "
                + betroffene(stapel).size() + " Spieler(n) sind verloren. Es wird NICHT"
                + " erneut geschrieben - Fortschreiben ist additiv, ein zweiter Versuch mit"
                + " derselben Liste zaehlte doppelt. Die naechsten " + aussetzerUebrig
                + " Taktschlaege rechnen weiter, schreiben aber nicht, damit ein Schreibzug"
                + " in eine unerreichbare Datenbank den Server nicht jede Taktlaenge erneut"
                + " anhaelt; die in dieser Zeit gemessenen Sekunden gehen auf jedem"
                + " geordneten Weg nicht verloren, sie werden nur spaeter geschrieben."
                + " Stuerzt der Server in diesem Fenster ab, sind sie weg - dann nicht eine"
                + " Taktlaenge je Spieler, sondern alle ausgesetzten Schlaege dazu."
                + " Grund: " + grund);
    }

    /** Wessen Sekunden in dieser Liste stehen - jeder Spieler einmal. */
    private static Set<UUID> betroffene(List<Spielzeitablage.Zuschlag> zuschlaege) {
        Set<UUID> wer = new LinkedHashSet<>();
        for (Spielzeitablage.Zuschlag zuschlag : zuschlaege) {
            wer.add(zuschlag.spieler());
        }
        return wer;
    }
}
