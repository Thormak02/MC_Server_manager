package de.freimarken.kern;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Die Geometrie Altfurts: Wo ist Markt, wo Gasse, wo Haeuserzeile, wo Mauer.
 *
 * <p>Reine Rechnerei, bewusst ohne Minecraft - dasselbe Vorgehen wie bei
 * {@link Stadtfeldplan} und {@link Inselplan}. Derselbe Plan beantwortet zwei Fragen,
 * die sonst auseinanderliefen: Was liegt an einer Stelle (das braucht das Pflaster), und
 * welche Haeuser stehen in der Stadt (das braucht der Aufbau).
 *
 * <p><b>Warum gerechnet und nicht eingetragen</b> [entschieden 23.11.2026]: Die neun
 * Bauten der {@code config.yml} sind Handarbeit und bleiben es - sie haben Namen,
 * Aufgaben und eigene Baustoffe. Eine STADT hat das nicht. Dieser Plan legt rund
 * zweihundert Haeuser, und zweihundert Quader von Hand einzutragen waere nicht nur
 * Arbeit, sondern eine Fehlerquelle: Der Betreiber muesste jede Zeile gegen jede andere
 * auf ueberlappende Bloecke pruefen. Gerechnet ist die Ueberschneidungsfreiheit eine
 * Zusage, die eine Probe halten kann.
 *
 * <p><b>Die Mitte ist der Markt, und die ist gemessen und nicht gewaehlt</b>: Die
 * Flankenhaeuser stehen bei x 48..58 und x 86..96, ihre inneren Wandflaechen also auf
 * x 58 und x 86. Im Norden steht die Torwand auf z 0, im Sueden das Bundesratshaus ab
 * z 26. Der freie Platz dazwischen ist x 59..85 und z 1..25 - seine Mitte ist
 * {@code 72 / 13}. Dort kommt auch an, wer die Welt betritt.
 *
 * <p><b>Der Ringabstand ist der Schachabstand</b> ({@code max(|dx|, |dz|)}) und nicht der
 * Kreisradius: Ein Ring ist dann ein Quadrat, und eine Haeuserzeile an einer Seite
 * ist ein Quadrat. Eine runde Gasse klingt schoener, aber ein gerechneter Kreis setzt
 * Haeuser auf Treppen aus halben Bloecken - das sieht aus wie ein Fehler und nicht wie
 * eine Kurve.
 *
 * <p><b>Gerade sind die Gassen trotzdem nicht</b> [24.11.2026]: Ueber die Welle in
 * {@link #versatzBei} biegen sie um bis zu {@link #KRUMM_TIEFE} Bloecke aus und wieder
 * ein. Dazu kommt, was man im Vorbeigehen ohnehin sieht: wechselnde Hausbreiten, Tiefen,
 * Hoehen und Baustoffe, nestweise zusammengehoerende Reihen, Hoefe zwischen den Haeusern
 * und ein Hoehengefaelle vom Markt zur Mauer.
 *
 * <p>Die Baender von der Mitte nach aussen, bei zwei Quartieren. Die Grenze zwischen
 * Gasse und Zeile ist die der GERADEN Gasse - die Welle verschiebt sie je Stelle um bis
 * zu zwei Bloecke zur Zeile hin:
 *
 * <pre>
 *     0 ..  40   die drei PLAETZE (siehe unten), 81 x 81 Bloecke
 *    41 ..  51   KERN          die eingetragenen Bauten - sie RAHMEN den Platz
 *    52 ..  56   RINGGASSE     erste Ringgasse, fuenf breit
 *    57 ..  67   ZEILE         Haeuser mit der Tuer zur Ringgasse
 *    68 ..  78   ZEILE         Haeuser mit der Tuer zur naechsten Gasse
 *    79 ..  83   RINGGASSE     zweite Ringgasse
 *    84 ..  94   ZEILE
 *    95 .. 105   ZEILE
 *   106 .. 110   RINGGASSE     die Gasse am Wehrgang
 *   111 .. 113   MAUER         drei Bloecke dick, GESCHLOSSEN
 *   114 .. 115   MAUER nur in den TUERMEN, sonst offenes Land
 *   116 ..        AUSSEN       offenes Land
 * </pre>
 *
 * <p><b>Der Platz ist in DREI geteilt</b> [entschieden 23.11.2026]: Ein Platz von
 * 81 x 81 ist als eine Flaeche ein Feld und kein Platz. Von Nord nach Sued liegen
 * darum hintereinander:
 *
 * <pre>
 *   -27 .. -10   KIRCHPLATZ    vor der Stadtkirche, 18 tief
 *    -9 ..  -7   PLATZKANTE    Baumreihe und Brunnen, 3 breit
 *    -6 ..  26   MARKT         der Marktplatz selbst, 33 tief - hier kommt man an
 *    27 ..  29   PLATZKANTE    Baumreihe und Brunnen
 *    30 ..  53   RATHAUSPLATZ  vor dem Bundesratshaus, 24 tief
 * </pre>
 *
 * <p>Die Kanten sind keine Mauern, sondern Bepflanzung: Man geht hindurch, aber man
 * sieht, dass der Platz hier aufhoert und der naechste anfaengt. Die drei Tiefen und
 * die zwei Kanten ergeben zusammen genau {@code 2 * MARKT_RING + 1} - eine Probe
 * haelt das, denn eine Luecke darin waere ein Streifen ohne Lage.
 *
 * <p><b>Die Mauer ist GESCHLOSSEN</b> [23.11.2026]: Vorher durchstiess die
 * Radialgasse sie, und der Durchstich war das Stadttor. Der Betreiber will eine
 * geschlossene Mauer und die Weltportale IN den Torhaeusern - also gewinnt die Mauer
 * jetzt vor der Gasse. Ein Torhaus ist ein BAU und steht in der
 * {@code config.yml}: Es wird nach dem Pflaster gesetzt und ersetzt dabei das Stueck
 * Mauer, auf dem es steht. Darum braucht es hier keine eigene Lage - eine Lage, die
 * nie gepflastert wird, weil dort ein Bau steht, waere ein Schluessel ohne Wirkung.
 *
 * <p><b>Zwei Zeilen Ruecken an Ruecken</b> ist kein Zufall, sondern der Grundriss einer
 * Hansestadt: Ein Quartier ist ein Block von Haeusern, die mit der Front auf zwei
 * verschiedene Gassen zeigen und sich hinten die Hoefe teilen. Darum traegt ein Quartier
 * eine Gasse und ZWEI Zeilen, und darum wechselt die Tuerseite zwischen ihnen.
 *
 * <p><b>Die Ecken bleiben frei</b> ({@link #ECKE_FREI}): Dort stiessen die Zeile der
 * Nordseite und die der Westseite aufeinander, und zwei Bauten, die sich Bloecke teilen,
 * werden beide verworfen. Statt die Ecke zu verzahnen, bleibt sie ein kleiner Platz -
 * das loest die Ueberschneidung UND gibt der Stadt die Winkel, die eine gewachsene
 * Altstadt hat.
 *
 * <p><b>Vier Radialgassen</b> schneiden jede Zeile und durchstossen die Mauer: Ohne sie
 * waere ein Quartier ein geschlossener Ring, den niemand durchqueren kann. Sie liegen auf
 * den Achsen der Mitte und sind damit die Verlaengerung des Weges, auf dem ein Spieler
 * ankommt.
 */
public final class Stadtplan {

    /** Was an einer Stelle der Ebene liegt. */
    public enum Lage {
        /** Der Marktplatz in der Mitte - dort kommt man an. */
        MARKT,
        /** Der Platz vor der Stadtkirche, noerdlich des Marktes. */
        KIRCHPLATZ,
        /** Der Platz vor dem Bundesratshaus, suedlich des Marktes. */
        RATHAUSPLATZ,
        /** Der bepflanzte Streifen zwischen zwei Plaetzen. */
        PLATZKANTE,
        /** Die eingetragenen Bauten und der Boden um sie - Handarbeit. */
        KERN,
        /** Eine Gasse rund um die Stadt. */
        RINGGASSE,
        /** Eine Gasse von der Mitte nach aussen, durch die Mauer hindurch. */
        RADIALGASSE,
        /** Der kleine Platz in einer Quartiersecke. */
        ECKPLATZ,
        /** Bauland einer Haeuserzeile - Haus oder Hof dahinter. */
        ZEILE,
        /** Die Stadtmauer. */
        MAUER,
        /** Offenes Land jenseits der Mauer. */
        AUSSEN
    }

    /** Die Mitte des Marktes - gemessen zwischen Flankenhaeusern, Torwand und Rathaus. */
    public static final int MITTE_X = 72;

    /** Die Mitte des Marktes auf der z-Achse. */
    public static final int MITTE_Z = 13;

    /**
     * Der Boden Altfurts.
     *
     * <p>Altfurt ist {@code art: flach}: Der Boden liegt ueberall auf y -61, der erste
     * freie Block auf y -60. Jeder Quader dieses Plans beginnt darum bei -61 - seine
     * unterste Ebene ERSETZT den Boden und liegt nicht darauf.
     */
    public static final int BODEN_Y = -61;

    /**
     * Der aeussere Ring der Plaetze - zusammen 81 x 81 Bloecke.
     *
     * <p>Vierzig, und das ist eine Ansage des Betreibers: Die erste Fassung hatte
     * 25 x 25, und er nannte den Platz "zu klein und zu eng". 81 x 81 ist die
     * Groesse eines echten Hansemarktes - und zu gross fuer EINEN Platz, darum die
     * Dreiteilung.
     */
    public static final int MARKT_RING = 40;

    /** Die Tiefe des Kirchplatzes, noerdlich des Marktes. */
    public static final int KIRCHPLATZ_TIEFE = 18;

    /** Die Breite eines bepflanzten Streifens zwischen zwei Plaetzen. */
    public static final int PLATZKANTE_BREITE = 3;

    /** Die Tiefe des Marktplatzes selbst - hier kommt man an. */
    public static final int MARKT_TIEFE = 33;

    /** Die Tiefe des Rathausplatzes, suedlich des Marktes. */
    public static final int RATHAUSPLATZ_TIEFE = 24;

    /**
     * Der aeussere Ring des Kerns - dort stehen die eingetragenen Bauten, und dieser
     * Plan ruehrt sie nicht an.
     *
     * <p>Der Kern ist ein BAND von elf Bloecken um die Plaetze (41..51), und das ist
     * seine Aufgabe: Die eingetragenen Bauten RAHMEN den Platz, sie stehen nicht
     * mehr irgendwo darin. Buergeramt und Zunfthaus im Westen, Muenzhaus und
     * Kaufmannskontor im Osten, das Bundesratshaus im Sueden am Rathausplatz, die
     * Stadtkirche im Norden am Kirchplatz.
     *
     * <p>Elf tief, weil ein giebelstaendiges Buergerhaus elf tief ist - dieselbe
     * Zahl wie {@link #ZEILE_TIEFE}, und aus demselben Grund.
     *
     * <p><b>Wer einen Bau weiter draussen eintraegt, kostet ein Zeilenhaus</b>: Der
     * Aufbau laesst jedes gerechnete Haus weg, das Handarbeit trifft, und meldet es.
     */
    public static final int KERN_RING = 51;

    /** Die Breite einer Gasse - fuenf, damit zwei Spieler und ein Marktstand passen. */
    public static final int GASSE_BREITE = 5;

    /** Die Tiefe einer Haeuserzeile samt Hof. */
    public static final int ZEILE_TIEFE = 11;

    /** Die Dicke der Stadtmauer. */
    public static final int MAUER_DICKE = 3;

    /**
     * Die Hoehe der Stadtmauer in Ebenen, Boden eingerechnet.
     *
     * <p>Sechs, und das ist die kleinste Hoehe, die wirkt: Ein Spieler ist zwei Bloecke
     * hoch und springt einen, ein Bloecke-Turm aus dem Lauf bringt ihn nicht hinauf.
     * Fuenf stehen ueber dem Boden, die sechste traegt die Zinnen.
     */
    public static final int MAUER_HOEHE = 6;

    /** Wie weit eine Zeile vor der Quartiersecke endet. */
    public static final int ECKE_FREI = 6;

    /** Die halbe Breite einer Radialgasse - fuenf Bloecke wie eine Ringgasse. */
    public static final int RADIAL_HALB = 2;

    /** Die schmalste Hausbreite zur Gasse. */
    public static final int BREITE_KLEIN = 5;

    /**
     * Die Hausbreiten zur Gasse, alle UNGERADE.
     *
     * <p>Ungerade, weil ein Giebel eine Mitte braucht: {@code Bauplan.giebelstufen}
     * rechnet mit {@code (front - 1) / 2}, und bei gerader Breite bleibt oben ein First
     * von zwei Bloecken statt einer Kante. Von der Gasse sieht das aus wie ein
     * abgesaegtes Dach.
     */
    public static final List<Integer> BREITEN = List.of(5, 7, 9);

    /** Die Haustiefen - hoechstens so tief wie die Zeile. */
    public static final List<Integer> TIEFEN = List.of(9, 10, 11);

    /**
     * Wie lang ein NEST ist, in Bloecken laengs der Gasse.
     *
     * <p><b>Warum es Nester gibt</b> [24.11.2026]: Der Betreiber sah die erste Stadt an
     * und sagte "aktuell ist alles noch recht gleichmaessig und repetetativ. es sollte ja
     * eher eine gewachsene stadt sein." Die Ursache war die STREUUNG JE HAUS: Jedes Haus
     * wuerfelte Breite, Hoehe und Baustoff fuer sich, und das Ergebnis sieht nicht
     * gewachsen aus, sondern GESPRENKELT - wie ein Rauschen, nicht wie eine Stadt.
     *
     * <p>Eine gewachsene Stadt entsteht in Nestern: Ein Bauherr baut drei Haeuser in
     * einem Jahrzehnt, aus demselben Stein, in derselben Hoehe. Daneben steht ein
     * aelterer Hof, dahinter eine Reihe aus anderer Zeit. Darum teilen Nachbarhaeuser
     * jetzt ihr Nest - Breite, Hoehe und Baustoff kommen daraus -, und der Sprung liegt
     * zwischen den Nestern.
     *
     * <p>Siebzehn, eine Primzahl: Die Hausbreiten sind 5, 7 und 9, und ein Nesttakt, der
     * ein Vielfaches davon waere, legte Nestgrenze und Hausgrenze immer auf dieselbe
     * Stelle. Siebzehn traegt zwei bis drei Haeuser.
     */
    public static final int NEST_TAKT = 17;

    /**
     * Die Bauhoehe am inneren Rand der Stadt, am Kern.
     *
     * <p><b>Ein Hoehengefaelle</b> [24.11.2026]: Am Markt ist der Grund teuer, dort baut
     * man hoch; draussen an der Mauer stehen die niedrigen Hoefe. Ohne das Gefaelle ist
     * jede Zeile so hoch wie jede andere, und von oben sieht die Stadt aus wie ein
     * Kamm.
     *
     * <p><b>Warum die Spanne so weit ist</b>: Gemessen am 24.11.2026 lagen die Mittel
     * der zwei Quartiere bei 15,74 und 14,78 - noch nicht ein Block Unterschied, und
     * daneben wuerfelt jedes Haus +-2. Das Gefaelle war damit unsichtbar. Sieben Ebenen
     * Spanne traegt durch die Streuung hindurch.
     */
    public static final int HOEHE_INNEN = 18;

    /** Die Bauhoehe an der aeussersten Haeuserzeile, kurz vor dem Wehrgang. */
    public static final int HOEHE_AUSSEN = 11;

    /**
     * Jede wievielte Parzelle leer bleibt - als Hof.
     *
     * <p>Eine Zeile ohne jede Luecke ist eine Mauer aus Haeusern. In einer gewachsenen
     * Stadt steht dazwischen ein Hof, ein Garten, eine abgebrannte Stelle, die niemand
     * neu bebaut hat. Jede neunte Parzelle bleibt darum frei - gezaehlt wird fest aus
     * der Stelle, also liegt derselbe Hof nach einem Neustart wieder dort.
     *
     * <p>Die Luecke bleibt {@link Lage#ZEILE} und wird als Hof gepflastert. Sie ist kein
     * Loch in der Zeile: Links und rechts steht ein Haus mit eigener Giebelwand.
     */
    public static final int HOF_TAKT = 9;

    /**
     * Die Laenge einer Gassenwelle in Bloecken - so weit laeuft die Gasse, bis sie
     * einmal ausgebogen und zurueckgekommen ist.
     *
     * <p><b>Warum die Gassen krumm sind</b> [24.11.2026]: Eine schnurgerade Gasse ist
     * das Zeichen einer GEPLANTEN Stadt - einer Gruendung am Zeichenbrett. Altfurt soll
     * gewachsen aussehen, und in einer gewachsenen Stadt folgt die Gasse dem, was zuerst
     * dastand: Sie wird eng, wo einer vorgebaut hat, und weit, wo ein Hof lag.
     *
     * <p>Neunundzwanzig, wieder eine Primzahl, und bewusst NICHT {@link #NEST_TAKT}:
     * Lagen Wellenkamm und Nestgrenze zusammen, fiele der Stilwechsel immer auf die
     * engste Stelle, und das Auge liest daraus wieder ein Raster.
     */
    public static final int KRUMM_WELLE = 29;

    /**
     * Wie weit die Gasse hoechstens ausbiegt, in Bloecken.
     *
     * <p>Zwei von elf Bloecken Bandtiefe. Mehr traegt das Band nicht: Die flachsten
     * Haeuser sind neun tief, und was uebrig bleibt, ist genau dieser Spielraum.
     *
     * <p>Die Welle ist ein Dreieck und kein Wuerfel - benachbarte Stellen haben fast
     * denselben Versatz. Gewuerfelt je Stelle waere die Gassenkante eine Saege, und das
     * sieht nicht nach gewachsen aus, sondern nach kaputt.
     */
    public static final int KRUMM_TIEFE = 2;

    /**
     * Wie weit ein Mauerturm VOR die Mauer tritt, in Bloecken.
     *
     * <p><b>Warum es Tuerme gibt</b> [24.11.2026]: Eine Stadtmauer ohne Tuerme ist ein
     * Band und liest sich wie eine Gartenmauer. Tuerme sind das, was eine Mauer wehrhaft
     * macht, und sie sind von jedem Punkt der Stadt zu sehen - Altfurts Mauer ist sechs
     * Ebenen hoch, die Tuerme ragen darueber hinaus.
     *
     * <p>Sie treten VOR die Mauer, weil genau das ihr Zweck ist: Von einem Turm sieht
     * man die Mauer entlang. Ein Turm, der in der Mauerflucht bleibt, ist ein Erker.
     */
    public static final int TURM_VOR = 2;

    /**
     * Die Breite eines Mauerturms laengs der Mauer.
     *
     * <p>Fuenf ist nicht gewaehlt, sondern {@link #MAUER_DICKE} plus {@link #TURM_VOR}:
     * Damit ist ein Turm so breit wie tief, und der ECKTURM - der Quader, in dem zwei
     * Mauerschenkel zusammenstossen - wird von selbst quadratisch. Waeren die Zahlen
     * verschieden, waere der Eckturm ein Rechteck und faellt aus dem Bild.
     */
    public static final int TURM_BREITE = MAUER_DICKE + TURM_VOR;

    /** Wie hoch ein Mauerturm ist - fuenf Ebenen ueber der Mauerkrone. */
    public static final int TURM_HOEHE = 11;

    /**
     * Die Laenge eines Mauerabschnitts, auf den EIN Turm kommt.
     *
     * <p>Einundreissig, wieder eine Primzahl und bewusst keine der anderen
     * ({@link #NEST_TAKT} 17, {@link #KRUMM_WELLE} 29): Fielen zwei Takte zusammen,
     * stuende jeder Turm an derselben Stelle einer Welle oder eines Nestes.
     *
     * <p><b>Gleich viele, aber nicht gleichmaessig</b>: Je Abschnitt steht GENAU ein
     * Turm - so kann keine Luecke von sechzig Bloecken entstehen und keine zwei Tuerme
     * koennen sich ueberschneiden. WO im Abschnitt er steht, ist fest aus der Stelle
     * gestreut. Das ist das Bild einer gewachsenen Mauer: Die Tuerme stehen dort, wo man
     * sie brauchte.
     */
    public static final int TURM_TAKT = 31;

    /**
     * Wie viel Mauer um jede Radialachse turmfrei bleibt.
     *
     * <p>Dort steht das TORHAUS, und ein Turm darf es nicht anschneiden. Die Zahl muss
     * die halbe Torhausbreite samt der halben Turmbreite ueberdecken - das haelt eine
     * Tafel in {@code TorhausplanTest} fest, damit hier und dort nicht zwei Zahlen
     * auseinanderlaufen.
     */
    public static final int TOR_FREI = 13;

    /**
     * Was auf der {@link Lage#PLATZKANTE} steht.
     *
     * <p><b>Warum es das gibt</b> [24.11.2026]: Die {@code config.yml} nannte die
     * Platzkante von Anfang an "Stein mit Erdinseln, in die Baeume und Brunnen kommen" -
     * und gebaut war nichts. Eine Zusage in der Einstellungsdatei, der kein Bau
     * entspricht, ist schlimmer als eine fehlende: Der Betreiber liest sie, sucht die
     * Baeume und findet Pflaster.
     *
     * <p>Altfurt war bis dahin durchweg Stein. Eine Stadt ohne einen Baum sieht nicht
     * karg aus, sondern unfertig.
     */
    public enum Kantenbau {
        /** Nur Pflaster - der Regelfall. */
        KEINER,
        /** Ein Baum, aus der Mitte des Streifens gewachsen. */
        BAUM,
        /** Ein Brunnen mit Wasser darin. */
        BRUNNEN
    }

    /**
     * Die Laenge eines Abschnitts der Platzkante, auf den EIN Standort kommt.
     *
     * <p>Neun Bloecke: Ein Baum mit zwei Bloecken Laub nach jeder Seite braucht fuenf,
     * und dazwischen soll man noch gehen koennen.
     *
     * <p>WO im Abschnitt er steht, ist gestreut - dieselbe Linie wie bei den Nestern und
     * den Mauertuermen. Eine Baumreihe in gleichen Abstaenden ist eine Allee, und eine
     * Allee gehoert vor ein Schloss und nicht auf einen gewachsenen Marktplatz.
     */
    public static final int KANTE_TAKT = 9;

    /**
     * Wie weit das Laub einer Krone ueber den Stamm hinausreicht, in Bloecken.
     *
     * <p><b>Diese Zahl gehoert hierher und nicht in den Bau</b> [24.11.2026]: Aus ihr
     * folgt der Abstand der Standorte - zwei Kronen duerfen nicht ineinander wachsen.
     * Stand sie nur im Bau, konnte sie wachsen, ohne dass die Standorte auseinander
     * gingen. Genau das war am 24.11.2026 der Fall: Die Streuung im Abschnitt lief von 1
     * bis 7, und zwei Standorte benachbarter Abschnitte lagen dadurch drei Bloecke
     * auseinander - bei zwei Bloecken Kronenweite waeren sie verwachsen.
     *
     * <p>{@link #kantenstandort} laesst darum an jedem Abschnittsrand genau so viel frei,
     * dass der schlechteste Fall noch {@code 2 * KRONE_WEITE + 1} Bloecke Abstand hat -
     * die Kronen beruehren sich dann, ohne sich zu durchdringen.
     */
    public static final int KRONE_WEITE = 2;

    /**
     * Wie viel Platzkante um die Mittelachse frei bleibt.
     *
     * <p>Dort kommt an, wer die Welt betritt, und dort geht er nach Norden zur Kirche
     * und nach Sueden zum Rathaus. Eine Baumreihe ueber die ganzen 81 Bloecke waere eine
     * Hecke quer durch den Hauptweg.
     */
    public static final int KANTE_FREI = 5;

    /**
     * Jeder wievielte Standort ein Brunnen ist statt eines Baumes.
     *
     * <p>Vier: Auf 81 Bloecken Kante liegen neun Abschnitte, davon zwei im freien
     * Streifen - es bleiben also rund zwei Brunnen je Kante.
     */
    public static final int BRUNNEN_TAKT = 4;

    /**
     * Die Baustile der Zeilenhaeuser.
     *
     * <p>{@link Baustil#GOLD} ist NICHT dabei: Gold ist der Stil des Muenzhauses und
     * seine Auszeichnung. Eine ganze Gasse aus Gold nimmt ihm diese.
     */
    public static final List<Baustil> STILE =
            List.of(Baustil.BACKSTEIN, Baustil.FACHWERK, Baustil.SCHIEFER, Baustil.FORST);

    /**
     * Jedes wievielte Haus ein Kupferdach bekommt.
     *
     * <p>Kupfer ist das gruenspanige Dach der Hanse und teuer - an jedem Haus waere es
     * keine Auszeichnung mehr. Jedes siebte setzt gruene Flecken in ein ziegelrotes Dach,
     * so wie eine Stadt ueber Jahrhunderte zu ihren Kupferdaechern kommt.
     */
    public static final int KUPFER_TAKT = 7;

    /** Die Tiefe eines Quartiers: eine Gasse und zwei Zeilen. */
    private static final int QUARTIER_TIEFE = GASSE_BREITE + 2 * ZEILE_TIEFE;

    private final int quartiere;
    private final List<Vorlage> vorlagen;
    private final List<Platzhalterbau> haeuser;

    /**
     * Wie weit das Haus an einer Stelle von der Gassenkante zurueckliegt.
     *
     * <p>Schluessel ist {@link #frontschluessel}, also Seite, Band und Laengsstelle;
     * der Wert ist die Bandtiefe, ab der das Haus steht. Wo kein Haus steht - in einem
     * Hof, an der Radialgasse, am Zeilenende -, fehlt der Eintrag, und dann gilt die
     * Welle aus {@link #versatzBei}.
     *
     * <p><b>Warum die Gasse die Haeuser fragt</b> [24.11.2026]: Die Gassenkante und die
     * Hausfront MUESSEN dieselbe Linie sein. Zuerst las jede Seite die Welle fuer sich:
     * die Gasse Block fuer Block, das Haus einmal fuer seine ganze Breite. Ein Haus ist
     * aber ein starrer Kasten und kann einer Welle nicht folgen - es liegt als SEHNE am
     * Bogen. Daraus entstanden zwei Fehler, beide gemessen am 24.11.2026: Nahm das Haus
     * die Welle an seiner Mitte, ragte es an beiden Enden in die Gasse (201 Bloecke);
     * nahm es den tiefsten Punkt unter sich, blieb vor einem Drittel der Haustueren ein
     * Streifen Hofboden stehen.
     *
     * <p>Beides verschwindet, wenn die Gasse nicht rechnet, sondern NACHSIEHT: Vor einem
     * Haus hoert sie genau an dessen Front auf. Die Welle bleibt die Vorgabe - sie
     * entscheidet, wie weit das Haus zurueckgeht - und ist nur dort noch die Antwort, wo
     * gar kein Haus steht.
     */
    private final Map<Long, Integer> hausfront;

    /**
     * @param quartiere wie viele Quartiere um den Kern liegen, mindestens 1
     */
    public Stadtplan(int quartiere) {
        this(quartiere, List.of());
    }

    /**
     * Eine Stadt aus GEGEBENEN Vorlagen.
     *
     * <p><b>Das ist der Unterschied zwischen Rechnen und Einpassen</b>
     * [24.11.2026]: Ohne Vorlagen erfindet der Plan die Masse jedes Hauses selbst
     * (Breite 5/7/9, Tiefe 9..11, Hoehe 12..16) und laesst den Bauplan die Form
     * rechnen. Mit Vorlagen nimmt er die Masse der DATEIEN und legt Parzellen, die
     * genau passen - gesetzt wird dann die Datei, gedreht nach der Tuerseite.
     *
     * <p>Der Rechenweg bleibt der RUECKFALL und wird nicht entfernt: Eine Datei
     * kann fehlen oder kaputt sein, und dann steht lieber ein gerechnetes Haus als
     * ein Loch in der Zeile. Das entscheidet {@code Altfurtaufbau.baue} je Haus.
     *
     * <p>Vorlagen, die TIEFER sind als ein Band, werden uebergangen - sie passen in
     * keine Zeile. Wer sie trotzdem eingetragen hat, bekommt es vom Aufrufer
     * gesagt; {@link #passtInEineZeile} ist die EINE Stelle dieser Frage.
     *
     * @param quartiere wie viele Quartiere um den Kern liegen, mindestens 1
     * @param vorlagen  die Grundrisse der gespeicherten Bauten; leer heisst rechnen
     */
    public Stadtplan(int quartiere, List<Vorlage> vorlagen) {
        if (quartiere < 1) {
            throw new IllegalArgumentException(
                    "Eine Stadt braucht mindestens ein Quartier, nicht " + quartiere);
        }
        if (vorlagen == null) {
            throw new IllegalArgumentException("Ohne Liste keine Vorlagen - eine"
                    + " leere Liste heisst rechnen");
        }
        this.quartiere = quartiere;
        this.vorlagen = vorlagen.stream().filter(Stadtplan::passtInEineZeile)
                .toList();
        Map<Long, Integer> fronten = new HashMap<>();
        this.haeuser = List.copyOf(rechneHaeuser(fronten));
        this.hausfront = Map.copyOf(fronten);
    }

    /** Altfurt: zwei Quartiere um den Kern, gerechnete Haeuser. */
    public static Stadtplan altfurt() {
        return new Stadtplan(2);
    }

    /** Altfurt mit diesen Vorlagen. */
    public static Stadtplan altfurt(List<Vorlage> vorlagen) {
        return new Stadtplan(2, vorlagen);
    }

    /**
     * Ob eine Vorlage ueberhaupt in eine Haeuserzeile passt.
     *
     * <p>Die EINE Stelle dieser Frage: Der Plan uebergeht eine zu tiefe Vorlage, und
     * der Aufrufer klagt darueber - beide muessen dasselbe meinen, sonst klagt er
     * ueber etwas, das gesetzt wird, oder schweigt zu etwas, das fehlt.
     *
     * <p>Die BREITE wird hier nicht geprueft: Sie muss nur in die Reihe passen, und
     * wie lang die ist, haengt am Band. Das entscheidet {@link #legeZeile}.
     *
     * <p><b>{@link #KRUMM_TIEFE} bleibt frei</b> [24.11.2026]: Seit die Gassen ausbiegen,
     * braucht JEDES Haus diesen Spielraum, um der Welle zu folgen. Eine Vorlage ist ein
     * starrer Grundriss - sie laesst sich nicht duennermachen. Waere sie so tief wie das
     * ganze Band, stuende sie an der ausgebogenen Stelle in der Gasse, und die Gasse
     * waere dort keine mehr.
     */
    public static boolean passtInEineZeile(Vorlage vorlage) {
        return vorlage != null && vorlage.tiefe() <= ZEILE_TIEFE - KRUMM_TIEFE
                && vorlage.breite() <= 2 * zeilenreichweite(KERN_RING + 1) + 1;
    }

    /** Die Vorlagen, die dieser Plan einpasst - leer heisst gerechnet. */
    public List<Vorlage> vorlagen() {
        return vorlagen;
    }

    /** Wie viele Quartiere um den Kern liegen. */
    public int quartiere() {
        return quartiere;
    }

    /** Der erste Ring eines Quartiers - dort beginnt seine Gasse. */
    public int quartierAnfang(int quartier) {
        return KERN_RING + 1 + quartier * QUARTIER_TIEFE;
    }

    /** Der erste Ring der inneren Zeile eines Quartiers. */
    public int zeileInnenVon(int quartier) {
        return quartierAnfang(quartier) + GASSE_BREITE;
    }

    /** Der erste Ring der aeusseren Zeile eines Quartiers. */
    public int zeileAussenVon(int quartier) {
        return zeileInnenVon(quartier) + ZEILE_TIEFE;
    }

    /** Der erste Ring der Gasse am Wehrgang. */
    public int abschlussgasseVon() {
        return quartierAnfang(quartiere);
    }

    /** Der erste Ring der Stadtmauer. */
    public int mauerVon() {
        return abschlussgasseVon() + GASSE_BREITE;
    }

    /** Der letzte Ring, der noch zur Stadt gehoert. */
    public int aussenRing() {
        return mauerVon() + MAUER_DICKE - 1;
    }

    /**
     * Der Umriss der Stadt in der Ebene - genau die Flaeche, die das Pflaster abgeht.
     *
     * <p>Nur die Bodenebene: y von {@link #BODEN_Y} bis {@link #BODEN_Y}. Was darueber
     * steht, sind Haeuser und Mauer und kommt aus {@link #haeuser()}.
     */
    public Quader umriss() {
        // MIT DEN TUERMEN: Sie treten vor die Mauer, und was nicht im Umriss liegt,
        // wird nie abgegangen - der Turm stuende im Plan und nicht in der Welt.
        int weite = aussenRing() + TURM_VOR;
        return new Quader(MITTE_X - weite, BODEN_Y, MITTE_Z - weite,
                MITTE_X + weite, BODEN_Y, MITTE_Z + weite);
    }

    /**
     * Wie weit eine Zeile dieses Bandes laengs der Gasse reicht, von der Mitte
     * gemessen.
     *
     * <p><b>Die EINE Stelle dieser Rechnung</b> [berichtigt 23.11.2026]: Sie wird
     * zweimal gebraucht - {@link #legeZeile} spannt damit die Reihe auf, und
     * {@link #inDerEcke} entscheidet damit, wo kein Haus mehr stehen kann. Vorher
     * rechnete die Ecke mit dem Ring der GEFRAGTEN STELLE statt mit dem inneren
     * Ring des Bandes. Beide stimmten nur auf dem innersten Ring ueberein und
     * liefen nach aussen je Ring um einen Block auseinander: 1760 Bloecke hiessen
     * ZEILE, obwohl dort nie ein Haus stehen kann - der Eckplatz lief als zehn
     * Bloecke breiter Erdkeil aus, und das sah man am Boden.
     *
     * <p>Gerechnet wird vom INNEREN Ring: Die Ecke waechst mit dem Ring, und eine
     * Zeile, die bis zur Ecke des AEUSSEREN Rings laeuft, trifft die Zeile der
     * Nachbarseite.
     *
     * @param bandVon der innere Ring des Bandes
     */
    public static int zeilenreichweite(int bandVon) {
        return bandVon - ECKE_FREI;
    }

    /**
     * Welchen Block einer Mischung diese Stelle bekommt - 0 bis {@code wieViele - 1}.
     *
     * <p><b>Warum ueberhaupt eine Mischung</b> [23.11.2026]: Eine Gasse aus einem
     * einzigen Block sieht aus wie eine Flaeche und nicht wie ein Weg. Der Betreiber
     * nannte die erste Fassung "etwas unschoen" und verlangte "texturiert, also mit
     * gemischten zusammenpassenden bloecken".
     *
     * <p><b>Die Gewichtung steckt in der LISTE und nicht hier</b>: Wer Kopfstein
     * dreimal und Kies einmal eintraegt, bekommt Kopfstein dreimal so oft. Das braucht
     * keine zweite Zahl je Block, keine Prozentrechnung und keine Reihenfolge - und der
     * Betreiber sieht die Mischung in der Datei, wie er sie meint.
     *
     * <p><b>Je Block und nicht in Flecken</b>: Zwei Bloecke grosse Flecken ergeben ein
     * Schachbrett, und das sieht man von weitem. Streuung je Block ist das, was ein
     * Bauender von Hand macht, wenn er einen Weg "mischt".
     *
     * <p>Fest wie alles hier: Dieselbe Stelle bekommt immer denselben Block, auch nach
     * einem Neustart.
     */
    public static int mischung(int x, int z, int wieViele) {
        if (wieViele < 1) {
            throw new IllegalArgumentException(
                    "Eine Mischung ohne Bloecke: " + wieViele);
        }
        return streu(x, z, 0, 9) % wieViele;
    }

    /**
     * Die Nummer des NESTES, in dem eine Stelle einer Zeile liegt.
     *
     * <p>Nachbarhaeuser teilen sie, und daraus kommen Breite, Hoehe und Baustoff - siehe
     * {@link #NEST_TAKT}. Die Nestgrenzen sind JE ZEILE verschoben, damit nicht alle
     * vier Seiten an derselben Stelle springen.
     */
    public static int nest(int quartier, Richtung seite, boolean innen, int laengs) {
        int versatz = streu(quartier, seite.ordinal(), innen ? 0 : 1, 11) % NEST_TAKT;
        return Math.floorDiv(laengs + versatz, NEST_TAKT);
    }

    /**
     * Wie weit die Gasse an dieser Stelle AUSBIEGT - der gemeinsame Versatz.
     *
     * <p><b>Die eine Stelle</b>: Diese Rechnung wird zweimal gebraucht, und darum steht
     * sie hier und nicht zweimal. {@link #legeZeile} setzt das Haus um so viele Bloecke
     * von der Gassenkante zurueck, und {@link #lageBei} gibt genau dieselben Bloecke als
     * {@link Lage#RINGGASSE} aus. Stuende die Rechnung zweimal, waere die Gasse an der
     * einen Stelle breit und das Haus doch davor - genau der Fehler, der am 23.11.2026
     * 1760 Bloecke der Eckplaetze falsch gepflastert hatte.
     *
     * <p>Ein Dreieck ueber {@link #KRUMM_WELLE} Bloecke, hoechstens
     * {@link #KRUMM_TIEFE} tief, mit einer Phase je Band: Die innere Zeile des ersten
     * Quartiers biegt an anderer Stelle aus als die aeussere.
     *
     * @param bandVon der innere Ring des Zeilenbandes - er waehlt die Phase
     * @param laengs die Stelle entlang der Gasse
     */
    public static int versatzBei(int bandVon, int laengs) {
        int halb = KRUMM_WELLE / 2;
        int phase = Math.floorMod(laengs + streu(bandVon, 0, 0, 12), KRUMM_WELLE);
        int dreieck = phase <= halb ? phase : KRUMM_WELLE - phase;
        // EINE TREPPE UND KEINE SPITZE [24.11.2026]: Gerechnet als
        // {@code dreieck * KRUMM_TIEFE / halb} wurde die volle Tiefe an genau EINEM
        // Block erreicht - das ist keine ausgebogene Gasse, sondern eine Kerbe. Jede
        // Stufe muss mehrere Bloecke lang sein, damit man im Vorbeigehen eine Weitung
        // sieht. Bei Welle 29 und Tiefe 2 sind es fuenf Bloecke je Stufe.
        return Math.min(KRUMM_TIEFE, dreieck * (KRUMM_TIEFE + 1) / (halb + 1));
    }

    /**
     * Wo im Abschnitt {@code abschnitt} der Platzkante der Standort liegt - gemessen von
     * der Mittelachse.
     *
     * <p>Der Rand des Abschnitts bleibt frei, damit das Laub zweier Baeume benachbarter
     * Abschnitte nicht ineinander waechst.
     */
    public static int kantenstandort(int abschnitt) {
        // DER RAND BLEIBT UM EINE KRONENWEITE FREI - siehe KRONE_WEITE. Damit ist der
        // kleinste Abstand zweier Standorte 2 * KRONE_WEITE + 1, und das ist genau die
        // Breite, bei der sich zwei Kronen beruehren, ohne sich zu durchdringen.
        int spiel = KANTE_TAKT - 2 * KRONE_WEITE;
        if (spiel < 1) {
            throw new IllegalStateException("Ein Abschnitt von " + KANTE_TAKT
                    + " Bloecken traegt keine Krone von " + KRONE_WEITE
                    + " Bloecken Weite");
        }
        return abschnitt * KANTE_TAKT + KRONE_WEITE
                + streu(abschnitt, 0, 0, 14) % spiel;
    }

    /**
     * Was an dieser Stelle der Platzkante steht.
     *
     * <p>Nur die MITTLERE Reihe des Streifens traegt etwas: Ein Baum braucht links und
     * rechts Pflaster, sonst steht er in der Kante und nicht auf ihr. Erkannt wird die
     * Mitte daran, dass auch ihre beiden Nachbarn Platzkante sind - gefragt wird dafuer
     * {@link #platzBei} selbst, damit hier keine zweite Rechnung der Streifenlage
     * entsteht.
     */
    public static Kantenbau kantenbauBei(int x, int z) {
        if (!kantenmitte(z) || ring(x, z) > MARKT_RING) {
            return Kantenbau.KEINER;
        }
        int laengs = x - MITTE_X;
        if (Math.abs(laengs) <= KANTE_FREI) {
            return Kantenbau.KEINER;
        }
        int abschnitt = Math.floorDiv(laengs, KANTE_TAKT);
        if (laengs != kantenstandort(abschnitt)) {
            return Kantenbau.KEINER;
        }
        return Math.floorMod(abschnitt, BRUNNEN_TAKT) == 0
                ? Kantenbau.BRUNNEN : Kantenbau.BAUM;
    }

    /**
     * Ob {@code z} die Mittelreihe eines Platzkantenstreifens ist.
     *
     * <p>Bei einem drei Bloecke breiten Streifen ist das genau die mittlere Reihe. Waere
     * {@link #PLATZKANTE_BREITE} fuenf, waeren es die drei inneren - dann stuenden die
     * Baeume drei Reihen tief, und das ist gewollt: Ein breiterer Streifen traegt mehr.
     */
    private static boolean kantenmitte(int z) {
        return platzBei(z) == Lage.PLATZKANTE
                && platzBei(z - 1) == Lage.PLATZKANTE
                && platzBei(z + 1) == Lage.PLATZKANTE;
    }

    /**
     * Wo im Abschnitt {@code abschnitt} einer Seite der Turm steht - die Laengsstelle
     * seiner Mitte, von der Stadtmitte gemessen.
     *
     * <p>Je Abschnitt genau einer, die Stelle darin gestreut - siehe {@link #TURM_TAKT}.
     * Der Rand des Abschnitts bleibt frei, damit zwei Tuerme benachbarter Abschnitte
     * sich nicht beruehren.
     */
    public static int turmMitte(Richtung seite, int abschnitt) {
        int spiel = TURM_TAKT - TURM_BREITE;
        int darin = streu(seite.ordinal(), abschnitt, 0, 13) % spiel;
        return abschnitt * TURM_TAKT + TURM_BREITE / 2 + darin;
    }

    /**
     * Ob an dieser Stelle ein Mauerturm steht.
     *
     * <p>Ein Turm reicht von {@link #mauerVon()} bis {@link #TURM_VOR} Bloecke VOR die
     * Mauer, ist {@link #TURM_BREITE} breit und {@link #TURM_HOEHE} hoch. An den vier
     * Ecken steht immer einer: Dort stossen zwei Mauerschenkel zusammen, und eine Ecke
     * ohne Turm ist die schwaechste Stelle einer Mauer.
     *
     * <p>Um jede Radialachse bleibt {@link #TOR_FREI} frei - dort steht das Torhaus.
     */
    public boolean imTurm(int x, int z) {
        int r = ring(x, z);
        if (r < mauerVon() || r > aussenRing() + TURM_VOR) {
            return false;
        }
        int dx = Math.abs(x - MITTE_X);
        int dz = Math.abs(z - MITTE_Z);
        // DER ECKTURM: Beide Achsen liegen im Mauerabstand, also stossen hier zwei
        // Schenkel zusammen. Der Quader ist von selbst quadratisch - siehe TURM_BREITE.
        if (Math.min(dx, dz) >= mauerVon()) {
            return true;
        }
        boolean laengsIstX = dz >= dx;
        Richtung seite = laengsIstX
                ? (z < MITTE_Z ? Richtung.NORD : Richtung.SUED)
                : (x < MITTE_X ? Richtung.WEST : Richtung.OST);
        int laengs = (laengsIstX ? x - MITTE_X : z - MITTE_Z);
        // DAS TORHAUS BLEIBT GANZ: Es steht auf der Radialachse.
        if (Math.abs(laengs) <= TOR_FREI) {
            return false;
        }
        // DER ECKTURM STEHT ALLEIN: Ein Seitenturm, dessen Breite bis in die Ecke
        // reicht, verschmilze mit ihm zu einer zehn Bloecke langen Bastion. Der letzte
        // Abschnitt einer Seite bleibt darum frei - die Luecke schliesst der Eckturm.
        if (Math.abs(laengs) + TURM_BREITE / 2 >= mauerVon()) {
            return false;
        }
        int abschnitt = Math.floorDiv(laengs, TURM_TAKT);
        return Math.abs(laengs - turmMitte(seite, abschnitt)) <= TURM_BREITE / 2;
    }

    /**
     * Ob diese Turmstelle am RAND des Turms liegt - dort steht die Brustwehr.
     *
     * <p>Gefragt werden die vier Nachbarn: Gehoert einer nicht mehr zum Turm, ist dies
     * eine Randstelle. So umlaeuft die Brustwehr den Turm, statt als Schachbrett ueber
     * seine ganze Decke zu laufen.
     */
    private boolean amTurmrand(int x, int z) {
        return !imTurm(x - 1, z) || !imTurm(x + 1, z)
                || !imTurm(x, z - 1) || !imTurm(x, z + 1);
    }

    /**
     * Die Grundhoehe eines Hauses auf diesem Ring - innen hoch, aussen niedrig.
     *
     * <p>Gerechnet und nicht gewuerfelt: Zwischen {@link #HOEHE_INNEN} an der ersten
     * Haeuserzeile und {@link #HOEHE_AUSSEN} an der letzten laeuft das Gefaelle
     * gleichmaessig. Die Abweichung je Nest und je Haus kommt obendrauf.
     *
     * <p><b>Die Spanne sind die HAUSBAENDER, nicht die Stadt</b> [24.11.2026]: Zuerst
     * lief sie von {@link #KERN_RING} bis {@link #aussenRing()}, also bis zur
     * Aussenkante der Mauer. Haeuser stehen aber nur auf den Zeilenbaendern dazwischen -
     * gemessen von Ring 57 bis Ring 100 bei einer Spanne von 51 bis 113. Von vier Ebenen
     * Gefaelle kamen so zwei in der Stadt an, und die verschwanden in der Streuung.
     * Jetzt liegt {@link #HOEHE_INNEN} auf der ersten Zeile und
     * {@link #HOEHE_AUSSEN} auf der letzten, und das ganze Gefaelle ist sichtbar.
     *
     * <p>Ausserhalb der Baender wird gedeckelt: Der Markt bekommt die innere Hoehe, die
     * Mauer die aeussere. Niemand baut dort ein Zeilenhaus, aber die Rechnung bleibt so
     * fuer jeden Ring beantwortbar.
     */
    public int grundhoehe(int ring) {
        int innen = zeileInnenVon(0);
        int aussen = zeileAussenVon(quartiere - 1) + ZEILE_TIEFE - 1;
        int spanne = aussen - innen;
        int weg = Math.max(0, Math.min(spanne, ring - innen));
        return HOEHE_INNEN - (HOEHE_INNEN - HOEHE_AUSSEN) * weg / Math.max(1, spanne);
    }

    /** Der Schachabstand einer Stelle von der Mitte - der Ring, auf dem sie liegt. */
    public static int ring(int x, int z) {
        return Math.max(Math.abs(x - MITTE_X), Math.abs(z - MITTE_Z));
    }

    /** Was an dieser Stelle der Ebene liegt. */
    public Lage lageBei(int x, int z) {
        int r = ring(x, z);
        if (r <= MARKT_RING) {
            return platzBei(z);
        }
        if (r <= KERN_RING) {
            return Lage.KERN;
        }
        // DER TURM TRITT VOR DIE MAUER [24.11.2026]: Seine vordersten Bloecke liegen
        // JENSEITS von aussenRing - also muss er GEFRAGT werden, bevor hier offenes Land
        // gemeldet wird. Stuende dieser Riegel vorher, waere der Turm unsichtbar: Der
        // Plan kennte ihn, und gesetzt wuerde er nie.
        if (r > aussenRing()) {
            return imTurm(x, z) ? Lage.MAUER : Lage.AUSSEN;
        }
        // DIE MAUER ZUERST [23.11.2026]: Sie ist GESCHLOSSEN. Vorher gewann hier die
        // Radialgasse und durchstiess sie - das war das Stadttor. Die Portale in die
        // anderen Welten stehen jetzt in den Torhaeusern, und ein Torhaus ist ein
        // BAU: Es wird nach dem Pflaster gesetzt und ersetzt das Stueck Mauer, auf
        // dem es steht.
        if (r >= mauerVon()) {
            return Lage.MAUER;
        }
        if (Math.abs(x - MITTE_X) <= RADIAL_HALB || Math.abs(z - MITTE_Z) <= RADIAL_HALB) {
            return Lage.RADIALGASSE;
        }
        if (r >= abschlussgasseVon()) {
            return Lage.RINGGASSE;
        }
        for (int q = 0; q < quartiere; q++) {
            if (r < zeileInnenVon(q)) {
                return Lage.RINGGASSE;
            }
            if (r < quartierAnfang(q + 1)) {
                // DER INNERE RING DES BANDES, nicht der Ring dieser Stelle: Daran
                // haengt die Reichweite der Zeile, und an der die Ecke.
                boolean innen = r < zeileAussenVon(q);
                int bandVon = innen ? zeileInnenVon(q) : zeileAussenVon(q);
                if (inDerEcke(x, z, bandVon)) {
                    return Lage.ECKPLATZ;
                }
                // DIE GASSE BIEGT AUS [24.11.2026]: Wo die Welle von versatzBei
                // ausschlaegt, gehoeren die vordersten Bloecke des Bandes zur Gasse und
                // nicht zur Zeile - und genau um diese Bloecke setzt legeZeile das Haus
                // zurueck. DIE INNERE Zeile hat ihre Gasse innen, die AEUSSERE aussen;
                // der Versatz zaehlt darum von der jeweils eigenen Kante.
                // WELCHE SEITE: Auf der Nord- und Suedseite ist der Ring der
                // z-Abstand, und laengs der Gasse laeuft x; auf der Ost- und Westseite
                // umgekehrt. Auf der Diagonale waere beides gleich - dort hat aber
                // inDerEcke schon gegriffen.
                boolean laengsIstX = Math.abs(z - MITTE_Z) == r;
                Richtung seite = laengsIstX
                        ? (z < MITTE_Z ? Richtung.NORD : Richtung.SUED)
                        : (x < MITTE_X ? Richtung.WEST : Richtung.OST);
                int laengs = laengsIstX ? x : z;
                int tiefeImBand = innen ? r - bandVon
                        : bandVon + ZEILE_TIEFE - 1 - r;
                if (tiefeImBand < gassenkante(seite, bandVon, laengs)) {
                    return Lage.RINGGASSE;
                }
                return Lage.ZEILE;
            }
        }
        // Unerreichbar: Die Schleife deckt jeden Ring von quartierAnfang(0) bis
        // quartierAnfang(quartiere) - 1 ab, und darueber haben die Riegel oben gegriffen.
        throw new IllegalStateException("Ring " + r + " liegt in keinem Band");
    }

    /**
     * Welcher der drei Plaetze an dieser z-Stelle liegt - gefragt nur innerhalb von
     * {@link #MARKT_RING}.
     *
     * <p>Von Norden nach Sueden abgeschritten und nicht gerechnet: Die Tiefen sind
     * verschieden (18, 33, 24), weil eine gewachsene Stadt keine gleichen Plaetze
     * hat. Eine Rechnung mit Dritteln waere kuerzer und falsch.
     */
    public static Lage platzBei(int z) {
        int ab = MITTE_Z - MARKT_RING;
        if (z < ab + KIRCHPLATZ_TIEFE) {
            return Lage.KIRCHPLATZ;
        }
        ab += KIRCHPLATZ_TIEFE;
        if (z < ab + PLATZKANTE_BREITE) {
            return Lage.PLATZKANTE;
        }
        ab += PLATZKANTE_BREITE;
        if (z < ab + MARKT_TIEFE) {
            return Lage.MARKT;
        }
        ab += MARKT_TIEFE;
        if (z < ab + PLATZKANTE_BREITE) {
            return Lage.PLATZKANTE;
        }
        return Lage.RATHAUSPLATZ;
    }

    /** Alle Haeuser der Stadt - gerechnet, nicht eingetragen. */
    public List<Platzhalterbau> haeuser() {
        return haeuser;
    }

    /**
     * Wie hoch die Mauer an dieser Stelle wird - 0, wo keine Mauer ist.
     *
     * <p>Die oberste Ebene traegt nur die ZINNEN und nur am aeusseren Rand: Eine Mauer,
     * die oben durchgehend geschlossen ist, sieht aus wie eine Kiste. Die Zinnen sitzen
     * aussen, weil ein Wehrgang dahinter liegt - wer oben steht, hat die Brustwehr vor
     * sich und nicht unter sich.
     *
     * <p><b>Auch auf den Achsen steht Mauer</b> [23.11.2026]: Die Mauer ist
     * geschlossen. Das Torhaus ist ein Bau, der nach dem Pflaster gesetzt wird und
     * das Stueck Mauer ersetzt, auf dem es steht - es braucht hier kein Loch.
     */
    public int mauerhoehe(int x, int z) {
        if (lageBei(x, z) != Lage.MAUER) {
            return 0;
        }
        // DER TURM ZUERST: Er ist hoeher als die Mauer, und seine Brustwehr sitzt auf
        // SEINEM Rand - nicht auf dem der Mauer.
        if (imTurm(x, z)) {
            return amTurmrand(x, z) && zinne(x, z) ? TURM_HOEHE : TURM_HOEHE - 1;
        }
        if (ring(x, z) < aussenRing()) {
            return MAUER_HOEHE - 1;
        }
        return zinne(x, z) ? MAUER_HOEHE : MAUER_HOEHE - 1;
    }

    /**
     * Ob auf dem aeusseren Mauerrand an dieser Stelle ein Zahn der Brustwehr steht.
     *
     * <p>Jeder zweite Block, und {@code x + z} statt {@code x} allein: Auf der Nord- und
     * Suedseite laeuft die Mauer entlang x, auf der Ost- und Westseite entlang z. Eine
     * Regel, die nur x fragt, gaebe den Seitenwaenden eine geschlossene Brustwehr - und
     * den Zahn, der gerade an der Ecke sitzt, zweimal.
     */
    public static boolean zinne(int x, int z) {
        return ((x + z) & 1) == 0;
    }

    /**
     * Liegt die Stelle so nah an der Quartiersecke, dass dort kein Haus stehen KANN.
     *
     * <p>Ein Haus der Nordseite belegt {@code |dz|} im Band und {@code |dx|} innerhalb
     * der {@link #zeilenreichweite}; bei der Ostseite ist es umgekehrt. Eine Stelle,
     * deren KLEINERER Abstand schon ueber der Reichweite liegt, wird darum von keiner
     * der beiden Seiten erreicht - dort ist Platz und kein Bauland.
     *
     * @param bandVon der innere Ring des Bandes, in dem die Stelle liegt
     */
    private static boolean inDerEcke(int x, int z, int bandVon) {
        int kleiner = Math.min(Math.abs(x - MITTE_X), Math.abs(z - MITTE_Z));
        return kleiner > zeilenreichweite(bandVon);
    }

    /**
     * Der Schluessel einer Laengsstelle in {@link #hausfront}.
     *
     * <p>Gerechnet und nicht gestueckelt: Seite, Band und Laengsstelle in EINER Zahl.
     * Die Laengsstelle wird um eine halbe Million gehoben, weil sie negativ sein kann -
     * Altfurt liegt um {@code 72 / 13}, und westlich davon zaehlt x nach unten.
     */
    private static long frontschluessel(Richtung seite, int bandVon, int laengs) {
        return ((long) seite.ordinal() * 1_000_000L + bandVon) * 1_000_000L
                + laengs + 500_000L;
    }

    /**
     * Wo die Gasse an dieser Stelle aufhoert - die Bandtiefe der Kante.
     *
     * <p>Vor einem Haus ist das dessen Front, sonst der Ausschlag der Welle. Siehe
     * {@link #hausfront}.
     */
    private int gassenkante(Richtung seite, int bandVon, int laengs) {
        Integer vomHaus = hausfront.get(frontschluessel(seite, bandVon, laengs));
        return vomHaus != null ? vomHaus : versatzBei(bandVon, laengs);
    }

    private List<Platzhalterbau> rechneHaeuser(Map<Long, Integer> fronten) {
        List<Platzhalterbau> ziel = new ArrayList<>();
        for (int q = 0; q < quartiere; q++) {
            for (Richtung seite : Richtung.values()) {
                legeZeile(ziel, fronten, q, true, seite);
                legeZeile(ziel, fronten, q, false, seite);
            }
        }
        return ziel;
    }

    /**
     * Legt eine Haeuserzeile an EINE Seite EINES Bandes.
     *
     * <p>Gerechnet wird in zwei oertlichen Achsen und erst am Ende in x und z
     * uebersetzt - sonst stuenden hier vier fast gleiche Bloecke, und drei von ihnen
     * waeren falsch, ohne dass es auffiele.
     *
     * <ul>
     *   <li><b>laengs</b> laeuft entlang der Gasse: x an der Nord- und Suedseite, z an
     *       der Ost- und Westseite.
     *   <li><b>quer</b> ist der Ring selbst, also der Abstand von der Mitte. Er waechst
     *       nach AUSSEN, auf jeder Seite.
     * </ul>
     */
    private void legeZeile(List<Platzhalterbau> ziel, Map<Long, Integer> fronten,
            int quartier, boolean innen, Richtung seite) {
        int von = innen ? zeileInnenVon(quartier) : zeileAussenVon(quartier);
        int bis = von + ZEILE_TIEFE - 1;
        boolean laengsIstX = seite == Richtung.NORD || seite == Richtung.SUED;
        int laengsMitte = laengsIstX ? MITTE_X : MITTE_Z;
        // DIESELBE REICHWEITE, die auch den Eckplatz bestimmt - eine Rechnung, eine
        // Stelle.
        int laengsVon = laengsMitte - zeilenreichweite(von);
        int laengsBis = laengsMitte + zeilenreichweite(von);
        Richtung tuer = innen ? Bauplan.gegenrichtung(seite) : seite;

        int nummer = 0;
        int laengs = laengsVon;
        while (laengs <= laengsBis) {
            // DAS NEST entscheidet, was Nachbarn teilen: Breite, Hoehe, Baustoff.
            // Je Haus bleibt nur die Tiefe und ein Block Hoehenspiel - so entsteht
            // eine Reihe, die zusammengehoert, und daneben eine andere.
            int nest = nest(quartier, seite, innen, laengs);
            // DIE VORLAGE ENTSCHEIDET DIE MASSE, wenn es eine gibt - sonst das
            // Nest. Gewaehlt wird fest aus der Stelle, damit ein zweites Aufsetzen
            // dieselbe Stadt legt.
            Vorlage vorlage = vorlagen.isEmpty() ? null
                    : waehle(vorlagen, streu(quartier, seite.ordinal(), nest, 6));
            int breite = vorlage != null ? vorlage.breite()
                    : waehle(BREITEN, streu(quartier, seite.ordinal(), nest, 1));
            int ende = laengs + breite - 1;
            if (ende > laengsBis) {
                return;
            }
            // DIE RADIALGASSE BLEIBT FREI: Ein Haus, das sie beruehrt, wird nicht
            // schmaler gemacht, sondern weggelassen - hinter der Gasse faengt die Zeile
            // neu an, und genau das sieht man einer gewachsenen Stadt an.
            if (ende >= laengsMitte - RADIAL_HALB && laengs <= laengsMitte + RADIAL_HALB) {
                laengs = laengsMitte + RADIAL_HALB + 1;
                continue;
            }
            // ERST DIE WELLE, DANN DIE TIEFE [24.11.2026]: Der Versatz ist KEIN
            // Spiel, das uebrig bleibt, sondern die Vorgabe - er gehoert der Gasse, und
            // lageBei gibt genau diese Bloecke als RINGGASSE aus. Ein Haus, das ihm
            // nicht folgen koennte, stuende in der Gasse; gemessen am 24.11.2026 waren
            // das 569 Bloecke. Also wird das Haus flacher, nicht die Gasse schmaler.
            //
            // Genommen wird der Ausschlag an der MITTE des Hauses. Das Haus legt sich
            // als Sehne an den Bogen, und die Gasse folgt ihm - siehe hausfront.
            int versatz = versatzBei(von, (laengs + ende) / 2);
            // DIE TIEFE bleibt je Haus verschieden - daran sieht man die Hoefe
            // hinter der Zeile.
            int streuung = streu(quartier, seite.ordinal(), laengs, 2);
            // EINE VORLAGE WIRD NICHT GESTUTZT: Sie ist ein fester Grundriss. Dass der
            // Versatz neben ihr Platz hat, haelt passtInEineZeile frei.
            int tiefe = vorlage != null ? vorlage.tiefe()
                    : Math.min(waehle(TIEFEN, streuung), ZEILE_TIEFE - versatz);
            // DIE HOEHE kommt aus dem Gefaelle, dem Nest und einem Block Spiel je
            // Haus: innen hoch, aussen niedrig, und innerhalb eines Nestes aehnlich.
            int hoehe = vorlage != null ? vorlage.hoehe()
                    : grundhoehe(von)
                            + streu(quartier, seite.ordinal(), nest, 3) % 3 - 1
                            + streu(quartier, seite.ordinal(), laengs, 8) % 2;
            int querVon = innen ? von + versatz : bis - versatz - tiefe + 1;
            int querBis = querVon + tiefe - 1;

            // JEDE NEUNTE PARZELLE BLEIBT EIN HOF: Eine Zeile ohne Luecke ist eine
            // Mauer aus Haeusern. Gezaehlt wird aus der Stelle, nicht aus dem Nest -
            // sonst stuende ein ganzes Nest leer.
            boolean hof = streu(quartier, seite.ordinal(), laengs, 10) % HOF_TAKT
                    == 0;
            if (hof) {
                laengs = ende + 1;
                continue;
            }
            // DER STIL KOMMT AUS DEM NEST: Eine Reihe aus einem Stein, daneben eine
            // aus einem anderen - so baut ein Bauherr, und so liest man es.
            // Er BLEIBT gesetzt, auch bei einer Vorlage: Er ist der RUECKFALL.
            ziel.add(new Platzhalterbau(
                    name(seite, quartier, innen, nummer),
                    quader(seite, laengs, ende, querVon, querBis, hoehe),
                    tuer,
                    waehle(STILE, streu(quartier, seite.ordinal(), nest, 4)),
                    streu(quartier, seite.ordinal(), nest, 5) % KUPFER_TAKT == 0
                            ? Dachart.KUPFER : Dachart.ZIEGEL,
                    vorlage == null ? null : vorlage.datei()));
            // UND DIE GASSE ERFAEHRT, WO DIESES HAUS STEHT: Von hier bis zu seiner Front
            // ist Gasse, ab der Front ist Haus. Eingetragen wird JEDE Laengsstelle des
            // Hauses, denn lageBei fragt Block fuer Block.
            for (int stelle = laengs; stelle <= ende; stelle++) {
                fronten.put(frontschluessel(seite, von, stelle), versatz);
            }
            nummer++;
            laengs = ende + 1;
        }
    }

    /** Der Quader eines Hauses aus oertlichen Achsen in x und z. */
    private static Quader quader(Richtung seite, int laengsVon, int laengsBis,
            int querVon, int querBis, int hoehe) {
        int bisY = BODEN_Y + hoehe - 1;
        return switch (seite) {
            case NORD -> new Quader(laengsVon, BODEN_Y, MITTE_Z - querBis,
                    laengsBis, bisY, MITTE_Z - querVon);
            case SUED -> new Quader(laengsVon, BODEN_Y, MITTE_Z + querVon,
                    laengsBis, bisY, MITTE_Z + querBis);
            case WEST -> new Quader(MITTE_X - querBis, BODEN_Y, laengsVon,
                    MITTE_X - querVon, bisY, laengsBis);
            case OST -> new Quader(MITTE_X + querVon, BODEN_Y, laengsVon,
                    MITTE_X + querBis, bisY, laengsBis);
        };
    }

    /** Der Name eines Zeilenhauses - eindeutig und lesbar, etwa {@code haus-n0i-03}. */
    private static String name(Richtung seite, int quartier, boolean innen, int nummer) {
        return "haus-" + seite.wort().charAt(0) + quartier + (innen ? "i" : "a")
                + "-" + (nummer < 10 ? "0" : "") + nummer;
    }

    private static <T> T waehle(List<T> aus, int streuung) {
        return aus.get(streuung % aus.size());
    }

    /**
     * Eine feste Streuung aus vier Zahlen - immer dieselbe fuer dieselbe Stelle.
     *
     * <p><b>Kein Zufall</b>, und das ist die ganze Absicht: Ein zweites Aufsetzen muss
     * dieselbe Stadt setzen, sonst stehen nach einem Neustart andere Haeuser als die, die
     * jemand kennt - und keine Probe koennte eine Hausbreite nachrechnen.
     */
    private static int streu(int a, int b, int c, int d) {
        int h = 17;
        h = h * 31 + a;
        h = h * 31 + b;
        h = h * 31 + c;
        h = h * 31 + d;
        h ^= h >>> 13;
        h *= 0x5bd1e995;
        h ^= h >>> 15;
        return h & 0x7fffffff;
    }
}
