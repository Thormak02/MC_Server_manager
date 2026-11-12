package de.freimarken.kern;

/**
 * Was ein Handel am Kistenshop kostet und einbringt - die ganze Rechnung, an einer Stelle.
 *
 * <p><b>Hier liegen die drei Raender, an denen die Vorbild-Plugins Geld erzeugt haben</b>
 * (Plan 7.4). Jede Regel dieser Klasse ist die Antwort auf einen belegten Fehler:
 *
 * <ul>
 *   <li><b>Kein Teilhandel.</b> ChestShop PR #522 "Fix zero partial price exploit": Bei
 *       Teilhandel wurde der skalierte Preis auf zwei Stellen gerundet und konnte 0
 *       werden - kostenlos kaufen. Hier geht ein Handel GANZ oder gar nicht, und darum
 *       gibt es keinen skalierten Preis, der runden koennte.</li>
 *   <li><b>Ganze Mark, keine Bruchteile.</b> Der Preis steht als {@code long} in der
 *       Zeile und wird mit {@code Math.multiplyExact} vervielfacht. Was nicht teilbar
 *       ist, wird nicht geteilt.</li>
 *   <li><b>Die Gebuehr wird vom VERKAEUFER abgezogen und nie aufgeschlagen.</b> Der Preis
 *       auf dem Schild ist, was der Kunde zahlt. Ein Aufschlag an der Kasse waere die
 *       haeufigste Beschwerde - und bei einem Aufschlag muesste der Kunde eine Zahl
 *       aufbringen, die nirgends steht.</li>
 * </ul>
 *
 * <p><b>Die Gebuehr wird ABGERUNDET, und das ist die sichere Richtung.</b> Bei 5 % von 99
 * Mark sind es 4 und nicht 5: Der Staat bekommt weniger, der Verkaeufer mehr. Die andere
 * Rundung koennte bei kleinen Betraegen den ganzen Erloes verschlucken - 5 % von 1 Mark
 * aufgerundet ist 1, und der Verkaeufer bekaeme 0 fuer seine Ware. Das ist genau die
 * Form, in der ChestShops Rundung zum Nulltarif wurde, nur in der anderen Richtung.
 *
 * <p><b>Vorrat und Platz kommen als ZAHLEN herein, in STUECK.</b> Was wirklich in einer
 * Kiste liegt und was in einen Rucksack passt, kann nur die Bukkit-Schicht sehen; wie
 * daraus Stufen werden, steht hier und ist ohne Server pruefbar. Die Umrechnung von
 * Faechern in Stueck ist deshalb auch hier - {@link #platzInStueck} -, und nicht droben:
 * Ob ein angefangener Stapel mitzaehlt, ist eine Regel, die falsch sein kann.
 */
public final class Shopregel {

    /**
     * Die hoechste Gebuehr: 50 Prozent.
     *
     * <p>Keine 100: Eine Gebuehr, die den ganzen Erloes nimmt, ist keine Gebuehr, sondern
     * eine Enteignung - und ein Betreiber, der sie aus Versehen einstellt, merkt es erst,
     * wenn die ersten Spieler aufhoeren zu handeln. Die Haelfte ist schon schmerzhaft
     * hoch und laesst den Vertipper auffallen.
     */
    public static final int HOECHSTE_GEBUEHR_PROZENT = 50;

    /**
     * Die groesste Stapelgroesse, die Minecraft kennt - 99 seit 1.21.
     *
     * <p>Als Schranke gegen den Programmfehler und nicht als Spielregel: Gemessen wird
     * die wirkliche Groesse am Gegenstand, denn seit 1.20.5 kann jeder Gegenstand seine
     * eigene tragen. Eine Zahl ueber dieser waere ein Lesefehler und nicht ein Stapel.
     */
    public static final int GROESSTER_STAPEL = 99;

    private Shopregel() {
    }

    /**
     * Was ein Handel bewegt.
     *
     * @param preis   was der Kunde zahlt (beim Kauf) oder was der Shop ansetzt (beim
     *                Ankauf) - immer die volle Summe fuer die ganze Menge
     * @param gebuehr was davon an den Staat geht
     * @param erloes  was beim Verkaeufer ankommt: {@code preis - gebuehr}
     * @param stueck  wie viele Gegenstaende den Besitzer wechseln
     */
    public record Handel(long preis, long gebuehr, long erloes, int stueck) {

        public Handel {
            if (preis < 1L) {
                throw new IllegalArgumentException("Ein Handel ueber " + preis
                        + " Mark ist keiner - wo nichts gezahlt wird, wird nicht"
                        + " gehandelt");
            }
            // Eine Gebuehr ueber dem Preis liesse den Erloes negativ werden - der
            // Verkaeufer zahlte fuer seinen Verkauf. Erreichbar ist das bei einer
            // Gebuehr bis 50 Prozent nicht; die Schranke steht gegen den
            // Programmierfehler, nicht gegen die Einstellung.
            if (gebuehr < 0L || gebuehr > preis) {
                throw new IllegalArgumentException("Eine Gebuehr von " + gebuehr
                        + " auf einen Preis von " + preis + " ist keine - sie liesse"
                        + " den Verkaeufer fuer seinen Verkauf zahlen");
            }
            if (erloes != preis - gebuehr) {
                throw new IllegalArgumentException("Die Rechnung geht nicht auf: " + preis
                        + " minus " + gebuehr + " ist nicht " + erloes);
            }
            if (stueck < 1) {
                throw new IllegalArgumentException("Ein Handel ueber " + stueck
                        + " Stueck ist keiner");
            }
        }

        /**
         * Ob dieser Handel eine Gebuehrzeile im Buch bekommt.
         *
         * <p><b>Eine benannte Frage und nicht ein Vergleich an der Buchungsstelle</b>, und
         * das ist eine Zusage, die an zwei Stellen gebraucht wird: Die Buchung laesst die
         * Gebuehrzeile aus, und der Satz am Schild laesst die Gebuehrzeile aus. Zwei
         * Vergleiche daneben liefen auseinander, und dann sieht ein Spieler eine Zeile,
         * die nicht gebucht wurde - oder umgekehrt.
         *
         * <p>Gefragt wird die GERECHNETE Gebuehr und nicht der Satz: Bei 5 Prozent auf 19
         * Mark ist die Gebuehr abgerundet 0, und eine Zeile ueber 0 Mark ist genau das,
         * was {@code Kasse} verbietet ("Keine Zeile im Buch ohne Geldbewegung").
         */
        public boolean kostetGebuehr() {
            return gebuehr > 0L;
        }
    }

    /**
     * Die zwei Mengen, nach denen ein Klick gestuft wird.
     *
     * <p>Beide gehoeren zusammen und werden darum zusammen uebergeben: Die eine kommt vom
     * SHOP, die andere von der WARE, und erst aus beiden ergibt sich, was ein Sneak-Klick
     * hoechstens bewegt. Als zwei weitere Zahlen in einer Argumentliste von sechs waeren
     * sie die naechste Gelegenheit, zwei gleich aussehende Werte zu vertauschen.
     *
     * <p><b>Warum {@link #rechne} sie nicht nimmt:</b> Dort geht es um Preise, und ein
     * Preis weiss von Stapeln nichts. Die Stapelgroesse begrenzt den KLICK und nicht den
     * Handel.
     *
     * @param shopmenge    wie viele Stueck eine Stufe umfasst - steht in der Shopzeile
     * @param stapelgroesse wie viele Stueck dieser Ware auf einen Platz gehen. Gemessen
     *                     am Gegenstand und nicht geraten: Enderperlen stapeln zu 16,
     *                     Werkzeug und jedes verzauberte Stueck zu 1 - und verzauberte
     *                     Ausruestung ist seit dem 08.11.2026 der Normalfall
     */
    public record Stufung(int shopmenge, int stapelgroesse) {

        public Stufung {
            if (shopmenge < 1 || shopmenge > Kistenshop.GROESSTE_MENGE) {
                throw new IllegalArgumentException("Eine Shopmenge von " + shopmenge
                        + " ist keine - erlaubt ist 1 bis " + Kistenshop.GROESSTE_MENGE);
            }
            if (stapelgroesse < 1 || stapelgroesse > GROESSTER_STAPEL) {
                throw new IllegalArgumentException("Eine Stapelgroesse von "
                        + stapelgroesse + " ist keine - erlaubt ist 1 bis "
                        + GROESSTER_STAPEL + ". Wer sie nicht messen konnte, handelt"
                        + " nicht: Ein geratener Stapel macht aus einem unbekannten"
                        + " Gegenstand den groesstmoeglichen Handel.");
            }
        }
    }

    /**
     * Rechnet einen Handel - die eine Stelle, an der Preis und Gebuehr entstehen.
     *
     * <p><b>Die Menge ist ein VIELFACHES der Shopmenge.</b> Ein Shop, der 16 Stueck fuer
     * 100 Mark anbietet, handelt 16, 32, 48 - nicht 17. Das ist nicht Strenge, sondern
     * die Vermeidung des Teilpreises: Sobald eine Menge zwischen zwei Stufen liegt, muss
     * ein Preis geteilt werden, und genau dort hat ChestShop sein kostenloses Kaufen
     * gehabt.
     *
     * @param shopmenge   wie viele Stueck eine Stufe umfasst
     * @param stufenpreis was eine Stufe kostet
     * @param stufen      wie viele Stufen gehandelt werden - 1 beim einfachen Klick,
     *                    mehr beim Sneak-Klick
     * @param gebuehrProzent wie viel Prozent an den Staat gehen, 0 bis
     *                    {@link #HOECHSTE_GEBUEHR_PROZENT}
     * @throws IllegalArgumentException wenn eine Zahl keine ist oder der Preis ueberlaeuft
     */
    public static Handel rechne(int shopmenge, long stufenpreis, int stufen,
            int gebuehrProzent) {
        if (shopmenge < 1 || shopmenge > Kistenshop.GROESSTE_MENGE) {
            throw new IllegalArgumentException("Eine Shopmenge von " + shopmenge
                    + " ist keine - erlaubt ist 1 bis " + Kistenshop.GROESSTE_MENGE);
        }
        if (stufenpreis < 1L || stufenpreis > Kistenshop.HOECHSTER_PREIS) {
            throw new IllegalArgumentException("Ein Stufenpreis von " + stufenpreis
                    + " ist keiner - erlaubt ist 1 bis " + Kistenshop.HOECHSTER_PREIS);
        }
        if (stufen < 1) {
            throw new IllegalArgumentException("Ein Handel ueber " + stufen
                    + " Stufen ist keiner");
        }
        if (!brauchbareGebuehr(gebuehrProzent)) {
            throw new IllegalArgumentException("Eine Gebuehr von " + gebuehrProzent
                    + " Prozent ist keine - erlaubt ist 0 bis "
                    + HOECHSTE_GEBUEHR_PROZENT);
        }
        // multiplyExact und nicht *, an BEIDEN Stellen - aber nur eine davon kann
        // wirklich ueberlaufen, und das ist beim Schreiben der Probe gemessen worden:
        //
        // Der PREIS kann nicht. Bei hoechstens 10 Millionen je Stufe und hoechstens 2,1
        // Milliarden Stufen sind es 2,1 mal 10^16, und ein long haelt 9,2 mal 10^18. Die
        // Grenzen oben schliessen den Fall aus; das multiplyExact steht hier als
        // Guertel, falls eine der zwei Grenzen eines Tages steigt.
        //
        // Die STUECKZAHL kann: 3.456 mal 2,1 Milliarden laeuft als int ueber, und ein
        // uebergelaufener int ist NEGATIV. Geworfen wird dann hier, wo der Fall
        // entsteht - nicht erst im Satz Handel, dessen Meldung vom Ueberlauf nichts
        // sagt.
        long preis = Math.multiplyExact(stufenpreis, (long) stufen);
        int stueck = Math.multiplyExact(shopmenge, stufen);
        // Abgerundet - siehe den Klassenkopf. Erst vervielfachen, dann teilen: Die andere
        // Reihenfolge rundete je Stufe und verloere bei 20 Stufen bis zu 20 Mark.
        long gebuehr = gebuehrAuf(preis, gebuehrProzent);
        return new Handel(preis, gebuehr, preis - gebuehr, stueck);
    }

    /**
     * Wie viele STUFEN in diesen Handel passen - fuer JEDEN Klick.
     *
     * <p>Lumia handelte beim Sneak-Klick "einen ganzen Stack". Was das genau ist, haengt
     * an drei Grenzen: wie viel der Zahler bezahlen kann, wie viel der Gebende hat und
     * wie viel beim Nehmenden noch Platz findet. Diese Rechnung nimmt die KLEINSTE davon
     * - und nie mehr als ein Stapel, weil mehr in keinen Klick gehoert.
     *
     * <p><b>Auch der EINFACHE Klick geht hier durch</b> und wird danach auf eine Stufe
     * begrenzt. Ein eigenes "reicht der Vorrat?" fuer den haeufigeren Weg waere die
     * zweite Wahrheit: Zwei Pruefungen derselben Frage laufen auseinander, und dann
     * verkauft der einfache Klick, was der Sneak-Klick abgelehnt haette.
     *
     * <p>Gibt 0 zurueck, wenn nicht einmal eine Stufe geht. Der Aufrufer macht daraus
     * eine Absage, die sagt, WAS fehlt; diese Rechnung sagt nur, wie viel geht.
     *
     * @param stufenpreis was eine Stufe kostet
     * @param guthaben    was der Zahler hat
     * @param vorrat      wie viele STUECK der Gebende hat
     * @param platz       wie viele STUECK der Nehmende aufnehmen kann - siehe
     *                    {@link #platzInStueck}
     * @param stufung     die zwei Mengen des Klicks
     */
    public static int passendeStufen(long stufenpreis, long guthaben, int vorrat,
            int platz, Stufung stufung) {
        if (stufenpreis < 1L) {
            throw new IllegalArgumentException("Ohne Stufenpreis gibt es keine Stufen");
        }
        if (stufung == null) {
            throw new IllegalArgumentException("Ohne Stufung gibt es keine Stufen");
        }
        int shopmenge = stufung.shopmenge();
        long nachGeld = guthaben / stufenpreis;
        int nachVorrat = Math.max(0, vorrat) / shopmenge;
        int nachPlatz = Math.max(0, platz) / shopmenge;
        long moeglich = Math.min(nachGeld, Math.min(nachVorrat, nachPlatz));
        // Nie mehr als ein Stapel je Klick. Ohne diese Grenze raeumte ein Sneak-Klick
        // eine volle Doppelkiste in einem Zug - und der Kunde haette 54 Stapel, von denen
        // 18 in seinem Inventar Platz finden und der Rest auf dem Boden laege.
        return (int) Math.min(moeglich, einStapelInStufen(stufung));
    }

    /**
     * Wie viele STUECK dieser Ware noch hineinpassen - die Platzzahl des Handels.
     *
     * <p><b>Angefangene Stapel zaehlen MIT, und das ist der Unterschied zum
     * Abholfach.</b> {@code Ladenware.freiePlaetze} zaehlt nur ganz freie Plaetze und
     * unterschaetzt absichtlich: Dort ist jedes Abholgut ein beliebiger ganzer Stapel
     * unbekannter Art, und es laesst sich nicht vorhersagen, worauf er sich legen liesse.
     * Hier ist die Ware EINE bekannte Art - die genaue Zahl ist also berechenbar, und
     * eine Unterschaetzung waere keine Vorsicht, sondern eine falsche Absage.
     *
     * <p>Das ist der haeufigste Kauffall ueberhaupt: Wer Erde kauft, traegt meistens
     * schon Erde. Mit nur ganz freien Plaetzen gezaehlt bekaeme ein Spieler mit 35 vollen
     * Erdeplaetzen und einem halben die Absage "kein Platz", obwohl 32 Stueck hineingehen.
     *
     * <p><b>Zwei verschieden benannte Zaehler und kein zusammengelegter</b> - die Frage
     * ist eine andere, nicht dieselbe in zwei Fassungen. Wer sie eines Tages
     * vereinheitlicht, nimmt dem Abholfach seine sichere Richtung und verliert
     * Gegenstaende.
     *
     * @param freieFaecher wie viele Plaetze ganz leer sind
     * @param stapelgroesse wie viele Stueck auf einen Platz gehen
     * @param angefangene  die Mengen der Plaetze, auf denen schon DIESE Ware liegt
     */
    public static int platzInStueck(int freieFaecher, int stapelgroesse,
            int[] angefangene) {
        if (freieFaecher < 0) {
            throw new IllegalArgumentException("Es gibt keine " + freieFaecher
                    + " freien Faecher");
        }
        if (stapelgroesse < 1 || stapelgroesse > GROESSTER_STAPEL) {
            throw new IllegalArgumentException("Eine Stapelgroesse von " + stapelgroesse
                    + " ist keine - erlaubt ist 1 bis " + GROESSTER_STAPEL);
        }
        long stueck = (long) freieFaecher * stapelgroesse;
        for (int menge : nichtNull(angefangene)) {
            if (menge < 1) {
                throw new IllegalArgumentException("Ein angefangener Stapel ueber "
                        + menge + " Stueck ist keiner - leere Plaetze gehoeren in"
                        + " freieFaecher");
            }
            // Was noch darauf passt. Ein Stapel, der schon ueber der Groesse liegt, gibt
            // 0 und nicht eine negative Zahl: Eine solche Kiste gibt es nur nach einem
            // Spielupdate, das die Groesse gesenkt hat, und dann ist kein Platz die
            // sichere Antwort.
            stueck += Math.max(0, stapelgroesse - menge);
        }
        return gedeckelt(stueck);
    }

    /**
     * Wie viele STUECK insgesamt da liegen - die Vorratszahl des Handels.
     *
     * <p>Die Gegenrichtung zu {@link #platzInStueck} und in derselben Bauart: Droben wird
     * nur abgelesen, welche Plaetze diese Ware halten und wie viel darauf liegt;
     * gerechnet wird hier. Dass es die Methode ueberhaupt gibt und nicht eine Summe im
     * Bukkit-Zweig, ist der Grund: So steht der Vorrat in derselben Schicht wie der Platz
     * und wird von derselben Probe gelesen.
     *
     * @param mengen die Mengen der Plaetze, auf denen DIESE Ware liegt
     */
    public static int vorratInStueck(int[] mengen) {
        long stueck = 0L;
        for (int menge : nichtNull(mengen)) {
            if (menge < 1) {
                throw new IllegalArgumentException("Ein Stapel ueber " + menge
                        + " Stueck ist keiner - leere Plaetze kommen nicht herein");
            }
            stueck += menge;
        }
        return gedeckelt(stueck);
    }

    /** Ob diese Gebuehr eine ist - 0 bis {@link #HOECHSTE_GEBUEHR_PROZENT}. */
    public static boolean brauchbareGebuehr(int prozent) {
        return prozent >= 0 && prozent <= HOECHSTE_GEBUEHR_PROZENT;
    }

    /**
     * Die Marktgebuehr auf einen Betrag - ABGERUNDET.
     *
     * <p><b>Hier und nicht zweimal</b>: Seit dem 12.11.2026 rechnet auch der
     * Grundstuecksverkauf eine Gebuehr ({@code Grundstuecksbuch.kaufe}), und zwei
     * Abschriften derselben Zeile liefen beim naechsten Rundungsstreit auseinander. Die
     * REGEL steht im Kopf dieser Klasse: abgerundet, auf die ganze Summe, und wer
     * rundet, rundet zugunsten des Spielers.
     *
     * <p>Bei 5 Prozent auf 19 Mark sind das 0 - und wo die gerechnete Gebuehr 0 ist,
     * faellt auch keine Gebuehrzeile im Buchungsbuch. Das ist der Grund, aus dem der
     * Aufrufer die ZAHL braucht und nicht nur den Satz.
     */
    public static long gebuehrAuf(long betrag, int prozent) {
        if (betrag < 0L) {
            throw new IllegalArgumentException("Eine Gebuehr auf einen negativen Betrag"
                    + " gibt es nicht: " + betrag);
        }
        if (!brauchbareGebuehr(prozent)) {
            throw new IllegalArgumentException("Eine Gebuehr von " + prozent
                    + " Prozent ist keine - erlaubt ist 0 bis "
                    + HOECHSTE_GEBUEHR_PROZENT);
        }
        return betrag * prozent / 100L;
    }

    private static int[] nichtNull(int[] mengen) {
        return mengen == null ? new int[0] : mengen;
    }

    /**
     * Kein Vorrat und kein Platz ueber einer Doppelkiste.
     *
     * <p>Gedeckelt und nicht geworfen: Die Zahl ist eine MESSUNG und keine Einstellung,
     * und ein voller Rucksack samt voller Doppelkiste liegt schon in der Groessenordnung.
     * Der Deckel ist die sichere Richtung - er kann einen Handel kleiner machen, nie
     * groesser.
     */
    private static int gedeckelt(long stueck) {
        return (int) Math.min(stueck, Kistenshop.GROESSTE_MENGE);
    }

    /**
     * Wie viele Stufen ein Stapel umfasst - mindestens eine.
     *
     * <p><b>Die Stapelgroesse der WARE und nicht die 64.</b> Enderperle und Schneeball
     * stapeln zu 16, Werkzeug, Ruestung und jedes verzauberte Stueck zu 1 - und
     * verzauberte Ausruestung ist seit der Entscheidung vom 08.11.2026 der Normalfall.
     * Mit einer festen 64 forderte ein Sneak-Klick an einem Spitzhacken-Shop mit Menge 1
     * vierundsechzig Stufen an, und die Zusage "nie mehr als ein Stapel je Klick" waere
     * falsch.
     *
     * <p>Bei einer Shopmenge ueber der Stapelgroesse ist eine Stufe schon mehr als ein
     * Stapel. Dann ist die Antwort 1 und nicht 0: Ein Sneak-Klick soll nie WENIGER
     * handeln als ein einfacher.
     *
     * <p>Bei Stapelgroesse 1 und Shopmenge 1 handelt der Sneak-Klick genau eine Stufe -
     * also so viel wie ein einfacher Klick. Das ist richtig und soll nicht als Fehler
     * gesucht werden: Ein Stapel Spitzhacken IST eine Spitzhacke.
     */
    private static int einStapelInStufen(Stufung stufung) {
        return Math.max(1, stufung.stapelgroesse() / stufung.shopmenge());
    }
}
