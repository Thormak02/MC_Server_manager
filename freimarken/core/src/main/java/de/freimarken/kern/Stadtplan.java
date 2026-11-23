package de.freimarken.kern;

import java.util.ArrayList;
import java.util.List;

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
 * laeuft gerade. Eine runde Gasse klingt schoener, aber ein gerechneter Kreis setzt
 * Haeuser auf Treppen aus halben Bloecken - das sieht aus wie ein Fehler und nicht wie
 * eine Kurve. Gewachsen wirkt die Stadt stattdessen durch das, was man im Vorbeigehen
 * wirklich sieht: wechselnde Hausbreiten, Tiefen, Hoehen, Baustoffe und ein um einen
 * Block zurueckgesetztes Haus mitten in der Zeile.
 *
 * <p>Die Baender von der Mitte nach aussen, bei zwei Quartieren:
 *
 * <pre>
 *     0 ..  12   MARKT         der gepflasterte Platz samt Markthallen
 *    13 ..  25   KERN          die neun Bauten der config.yml - unberuehrt
 *    26 ..  30   RINGGASSE     erste Ringgasse, fuenf breit
 *    31 ..  41   ZEILE         Haeuser mit der Tuer zur Ringgasse
 *    42 ..  52   ZEILE         Haeuser mit der Tuer zur naechsten Gasse
 *    53 ..  57   RINGGASSE     zweite Ringgasse
 *    58 ..  68   ZEILE
 *    69 ..  79   ZEILE
 *    80 ..  84   RINGGASSE     die Gasse am Wehrgang
 *    85 ..  87   MAUER         drei Bloecke dick
 *    88 ..        AUSSEN       offenes Land
 * </pre>
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
        /** Der gepflasterte Marktplatz in der Mitte. */
        MARKT,
        /** Die neun Bauten der {@code config.yml} und ihre Gassen - Handarbeit. */
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

    /** Der aeussere Ring des Marktes: x 60..84, z 1..25. */
    public static final int MARKT_RING = 12;

    /**
     * Der aeussere Ring des Kerns - alles darunter ruehrt dieser Plan nicht an.
     *
     * <p>Fuenfundzwanzig, und das ist der groesste Abstand, den ein handgebauter Quader
     * von der Mitte hat: Das Bundesratshaus reicht bis z 38, also 25 ueber die Mitte 13.
     * Das Muenzhaus reicht bis x 96, also 24. Wer hier kleiner rechnet, setzt die erste
     * Zeile in ein Haus, das schon steht.
     */
    public static final int KERN_RING = 25;

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

    /** Die Hausfhoehen in Ebenen - vierzehn ist das Mass der Flankenhaeuser. */
    public static final List<Integer> HOEHEN = List.of(12, 13, 14, 15, 16);

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
    private final List<Platzhalterbau> haeuser;

    /**
     * @param quartiere wie viele Quartiere um den Kern liegen, mindestens 1
     */
    public Stadtplan(int quartiere) {
        if (quartiere < 1) {
            throw new IllegalArgumentException(
                    "Eine Stadt braucht mindestens ein Quartier, nicht " + quartiere);
        }
        this.quartiere = quartiere;
        this.haeuser = List.copyOf(rechneHaeuser());
    }

    /** Altfurt: zwei Quartiere um den Kern, Mauer bei Ring 85. */
    public static Stadtplan altfurt() {
        return new Stadtplan(2);
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
        int weite = aussenRing();
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

    /** Der Schachabstand einer Stelle von der Mitte - der Ring, auf dem sie liegt. */
    public static int ring(int x, int z) {
        return Math.max(Math.abs(x - MITTE_X), Math.abs(z - MITTE_Z));
    }

    /** Was an dieser Stelle der Ebene liegt. */
    public Lage lageBei(int x, int z) {
        int r = ring(x, z);
        if (r <= MARKT_RING) {
            return Lage.MARKT;
        }
        if (r <= KERN_RING) {
            return Lage.KERN;
        }
        if (r > aussenRing()) {
            return Lage.AUSSEN;
        }
        // DIE RADIALGASSE ZUERST, und zwar auch vor der Mauer: Ihr Durchstich IST das
        // Stadttor. Wer die Mauer vorher fragt, baut eine Stadt ohne Ausgang.
        if (Math.abs(x - MITTE_X) <= RADIAL_HALB || Math.abs(z - MITTE_Z) <= RADIAL_HALB) {
            return Lage.RADIALGASSE;
        }
        if (r >= mauerVon()) {
            return Lage.MAUER;
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
                int bandVon = r < zeileAussenVon(q) ? zeileInnenVon(q)
                        : zeileAussenVon(q);
                return inDerEcke(x, z, bandVon) ? Lage.ECKPLATZ : Lage.ZEILE;
            }
        }
        // Unerreichbar: Die Schleife deckt jeden Ring von quartierAnfang(0) bis
        // quartierAnfang(quartiere) - 1 ab, und darueber haben die Riegel oben gegriffen.
        throw new IllegalStateException("Ring " + r + " liegt in keinem Band");
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
     * <p>Die RADIALGASSE bekommt keine Mauer: Ihr Durchstich ist das Stadttor, und ein
     * Tor mit Mauer darin ist kein Tor.
     */
    public int mauerhoehe(int x, int z) {
        if (lageBei(x, z) != Lage.MAUER) {
            return 0;
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

    private List<Platzhalterbau> rechneHaeuser() {
        List<Platzhalterbau> ziel = new ArrayList<>();
        for (int q = 0; q < quartiere; q++) {
            for (Richtung seite : Richtung.values()) {
                legeZeile(ziel, q, true, seite);
                legeZeile(ziel, q, false, seite);
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
    private void legeZeile(List<Platzhalterbau> ziel, int quartier, boolean innen,
            Richtung seite) {
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
            int breite = waehle(BREITEN, streu(quartier, seite.ordinal(), laengs, 1));
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
            int streuung = streu(quartier, seite.ordinal(), laengs, 2);
            int tiefe = waehle(TIEFEN, streuung);
            int hoehe = waehle(HOEHEN, streu(quartier, seite.ordinal(), laengs, 3));
            // DER VERSATZ MUSS IN DAS BAND PASSEN: Bei voller Tiefe bleibt kein Block
            // uebrig, um das Haus zurueckzusetzen.
            int spiel = ZEILE_TIEFE - tiefe;
            int versatz = Math.min(spiel, streuung % 2);
            int querVon = innen ? von + versatz : bis - versatz - tiefe + 1;
            int querBis = querVon + tiefe - 1;

            ziel.add(new Platzhalterbau(
                    name(seite, quartier, innen, nummer),
                    quader(seite, laengs, ende, querVon, querBis, hoehe),
                    tuer,
                    waehle(STILE, streu(quartier, seite.ordinal(), laengs, 4)),
                    streu(quartier, seite.ordinal(), laengs, 5) % KUPFER_TAKT == 0
                            ? Dachart.KUPFER : Dachart.ZIEGEL));
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
