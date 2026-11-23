package de.freimarken.kern;

/**
 * Welche Stelle eines Baus was wird - die Form eines Hansehauses, und zwar ohne Bukkit.
 *
 * <p>Hier steht die Geometrie und kein Block: Welcher {@code Material}-Wert zu einem
 * {@link Stueck} gehoert, sagt die Tabelle im Plugin. Dieselbe Trennung wie bei
 * {@link Wolkenplan} und {@code Wolkenboden} - und sie ist der Grund, warum diese Form
 * ohne laufenden Server zu pruefen ist.
 *
 * <p><b>GIEBELSTAENDIG</b> [23.11.2026]: Ein Hansehaus steht mit dem Giebel zur Gasse,
 * und der Giebel ist ein TREPPENGIEBEL - das Kennzeichen schlechthin. Daraus folgt die
 * ganze Rechnung dieser Klasse: Die Hoehe des Giebels ist die halbe Front, denn er zieht
 * sich je Ebene um einen Block auf jeder Seite ein, bis oben ein Block uebrig ist - der
 * First. Eine Front von sieben Bloecken gibt drei Giebelstufen, eine von siebzehn gibt
 * acht.
 *
 * <p><b>Gefragt wird mit ABSOLUTEN Koordinaten, und eine Stelle ausserhalb ist ein
 * Programmfehler</b>: Wer den Quader abschreitet, kommt nie daneben. Ein {@code LUFT} als
 * Antwort auf eine Stelle ausserhalb waere die bequeme Luege - sie machte einen Zaehler um
 * eins daneben unsichtbar, und genau dieser Fehler ist in diesem Haus dreimal in einer
 * Spaltenlogik gesessen (siehe {@code Weltgriff}).
 *
 * <p><b>Die Reihenfolge der Fragen ist Teil der Form</b> und nicht Geschmack: Die TUER
 * gewinnt ueber die Wand, denn sie liegt in ihr; der GIEBEL gewinnt ueber alles, was
 * darunter waere, denn oberhalb des Dachansatzes gibt es keine Wand mehr.
 */
public final class Bauplan {

    /**
     * So hoch ist ein Geschoss: Schwelle, zwei Ebenen Gefach mit Fenster, Rahm.
     *
     * <p>Vier, und das ist keine runde Zahl, sondern die kleinste, in der ein Fenster
     * zwischen zwei Balkenbaendern Platz hat. Bei drei saesse der Rahm auf dem Fenster.
     */
    public static final int GESCHOSSHOEHE = 4;

    /**
     * Jede wievielte Spalte der Wand ein stehender Balken ist.
     *
     * <p>Drei: Balken, zwei Gefach, Balken. Bei zwei waere die Wand ein Gitter, bei vier
     * verliert das Fachwerk seinen Takt.
     */
    public static final int BALKENTAKT = 4;

    /**
     * Wie viele Ebenen das Dach steigt, bis es sich einen Block eingezogen hat.
     *
     * <p><b>ZWEI, also steil</b> - und das ist an Vorbildern gemessen und keine Laune. Ein
     * Dach mit der Neigung eins zu eins ist bei sieben Bloecken Front drei Ebenen hoch;
     * ein norddeutscher Staffelgiebel ist doppelt so hoch wie breit angelegt, weil ein
     * steiles Dach den Schnee abwirft und das Dachgeschoss nutzbar macht. Gemessen an
     * Mojangs eigenen Haeusern: Dort ist das Dach MEHR als die halbe Bauhoehe
     * ({@code plains_butcher_shop_1}: drei Ebenen Wand, vier Ebenen Dach).
     *
     * <p>Passt ein so steiles Dach nicht in die Hoehe des Quaders, wird es flacher -
     * siehe {@link #giebelstufen}. Flacher als eins zu eins wird es nie, denn dann waere
     * es oben offen.
     */
    public static final int DACHNEIGUNG = 2;

    /**
     * Wie viele Ebenen ueber dem Fussboden aus Stein sind - das Erdgeschoss.
     *
     * <p><b>Das ganze Erdgeschoss, nicht eine Schwelle</b>: Gemessen an
     * {@code plains_big_house_1} sind 74 von 340 Bloecken Bruchstein, und sie stehen alle
     * unten. Ein Fachwerkhaus steht auf einem steinernen Sockelgeschoss - das schuetzt das
     * Holz vor Spritzwasser, und genau so sehen die Haeuser aus, die als Vorbild dienen.
     */
    public static final int SOCKELEBENEN = 3;

    /** Was an einer Stelle des Baus steht. */
    public enum Stueck {
        /** Die unterste Ebene - der Fussboden, er liegt auf dem Boden der Ebene. */
        BODEN,
        /** Der Sockel: die erste Ebene ueber dem Boden, rundum. */
        SOCKEL,
        /** Ein stehender oder liegender Balken des Fachwerks. */
        BALKEN,
        /** Das Gefach zwischen den Balken - die Flaeche der Wand. */
        GEFACH,
        /** Eine Fensteroeffnung. */
        FENSTER,
        /** Die Oeffnung in der Wand, auf der Seite, die der Bau nennt. */
        TUER,
        /** Die Laterne neben der Tuer. */
        LATERNE,
        /** Eine Stufe des Treppengiebels - die zwei schmalen Seiten ueber dem Dachansatz. */
        GIEBELSTUFE,
        /** Die Dachflaeche - die zwei langen Seiten ueber dem Dachansatz. Eine TREPPE. */
        DACH,
        /**
         * Die untere Ebene einer Dachstufe - ein VOLLER Block.
         *
         * <p>Bei einem steilen Dach steigt die Haut zwei Ebenen, bevor sie sich einen
         * Block einzieht. Zwei Treppen uebereinander liessen aussen die oberen Haelften
         * offen - von der Gasse aus ein Zickzack statt einer Flaeche. Darum ist die
         * untere Ebene jeder Stufe voll und nur die obere eine Treppe.
         */
        DACHFUSS,
        /** Der First ganz oben. */
        FIRST,
        /** Luft. Sie wird MITGESETZT, damit Bauen und Zuruecksetzen dasselbe sind. */
        LUFT
    }

    private Bauplan() {
    }

    /**
     * Die Himmelsrichtung, in die eine GESPEICHERTE Struktur gebaut wurde.
     *
     * <p>Eine Festlegung und keine Messung: Wer ein Haus von Hand baut und sichert, baut
     * es mit der Tuer nach NORDEN. Daraus rechnet {@link #drehungsgrade} fuer jeden Bau
     * seine Drehung. Ohne eine solche Festlegung braeuchte jedes Haus vier Dateien.
     */
    public static final Richtung STRUKTUR_BLICKT = Richtung.NORD;

    /**
     * Um wie viel Grad eine Struktur der NORMLAGE gedreht werden muss, damit ihre Tuer in
     * diese Richtung zeigt - 0, 90, 180 oder 270.
     *
     * <p>NUR die Zuordnung Richtung zu Grad. Ob ein Bau sich ueberhaupt dreht,
     * entscheidet {@link #dateilage} - eine eigene Datei tut es nicht.
     *
     * <p>Im Kern und als ZAHL: {@code StructureRotation} ist eine Bukkit-Sache, die
     * Rechnung ist keine. Gezaehlt wird im Uhrzeigersinn, denn so zaehlt auch Minecraft.
     */
    public static int drehungsgrade(Richtung tuer) {
        if (tuer == null) {
            throw new IllegalArgumentException("Ohne Tuerseite gibt es keine Drehung");
        }
        return switch (tuer) {
            case NORD -> 0;
            case OST -> 90;
            case SUED -> 180;
            case WEST -> 270;
        };
    }

    /**
     * Welche Datei ein Bau setzt und um wie viel sie sich dreht.
     *
     * @param datei der Name OHNE Endung - die Naht haengt sie an
     * @param grad  0, 90, 180 oder 270, im Uhrzeigersinn
     */
    public record Dateilage(String datei, int grad) {
    }

    /**
     * BEIDE Fragen an einer Stelle: welche Datei, und wie gedreht.
     *
     * <p><b>Sie haengen zusammen, darum eine Methode</b> [23.11.2026]: Eine EIGENE Datei
     * ({@code vorlage} fehlt) entstand aus genau diesem Quader - sie steht schon richtig
     * in der Welt und darf sich NICHT drehen. Eine VORLAGE ist in der Normlage gebaut
     * (Tuer nach {@link #STRUKTUR_BLICKT}) und muss sich drehen. Wer den Namen aus der
     * einen Regel und den Grad aus der anderen nimmt, baut ein verdrehtes Haus - und
     * genau das ist am laufenden Server passiert, als beides noch getrennt war: Vier von
     * neun Haeusern passten nach der Drehung nicht mehr in ihren eigenen Quader und
     * wurden gerechnet.
     */
    public static Dateilage dateilage(Platzhalterbau bau) {
        if (bau == null) {
            throw new IllegalArgumentException("Ohne Bau gibt es keine Datei");
        }
        if (bau.vorlage() == null) {
            return new Dateilage(bau.schluessel(), 0);
        }
        return new Dateilage(bau.vorlage(), drehungsgrade(bau.tuer()));
    }

    /**
     * Ob eine Struktur dieser Groesse in den Quader passt - oder was daran fehlt.
     *
     * <p>Gibt {@code null}, wenn sie passt, und sonst den Satz fuer den Betreiber. Die
     * Groesse kommt UNGEDREHT herein, so wie sie in der Datei steht; bei 90 und 270 Grad
     * tauschen Breite und Tiefe, und genau dieser Tausch ist der Fehler, den man sonst
     * erst im Spiel sieht.
     */
    public static String passtNicht(Platzhalterbau bau, int breite, int hoehe,
            int tiefe) {
        if (bau == null) {
            throw new IllegalArgumentException("Ohne Bau passt nichts");
        }
        int grad = dateilage(bau).grad();
        int nachX = (grad == 90 || grad == 270) ? tiefe : breite;
        int nachZ = (grad == 90 || grad == 270) ? breite : tiefe;
        Quader b = bau.bereich();
        int hatX = b.bisX() - b.vonX() + 1;
        int hatY = b.bisY() - b.vonY() + 1;
        int hatZ = b.bisZ() - b.vonZ() + 1;
        if (nachX <= hatX && hoehe <= hatY && nachZ <= hatZ) {
            return null;
        }
        return "Die Struktur ist " + breite + "x" + hoehe + "x" + tiefe
                + " und liegt nach " + grad + " Grad Drehung als " + nachX + "x" + hoehe
                + "x" + nachZ + " - der Quader von \"" + bau.schluessel() + "\" ist aber"
                + " nur " + hatX + "x" + hatY + "x" + hatZ + ".";
    }

    /**
     * Die Stelle, an der eine gedrehte Struktur GESETZT werden muss - ihr X-Wert.
     *
     * <p><b>Warum es diese Rechnung gibt</b>: Minecraft dreht eine Struktur um die Stelle,
     * an der sie gesetzt wird, und sie waechst danach in eine andere Richtung. Setzt man
     * sie stur auf die genannte Ecke des Quaders, steht ein gedrehtes Haus NEBEN seinem
     * Quader.
     *
     * <p><b>Die vier Faelle sind am laufenden Server GEMESSEN</b> [23.11.2026], mit einer
     * absichtlich unsymmetrischen Probestruktur - 3 breit, 5 tief, drei verschiedene
     * Markenbloecke an drei Ecken -, denn ein quadratisches Haus verbirgt jeden Versatz.
     * Gemessen wurde: lokal {@code +X} zeigt nach 90 Grad nach {@code +Z}, nach 180 Grad
     * nach {@code -X}, nach 270 Grad nach {@code -Z}; lokal {@code +Z} entsprechend eine
     * Vierteldrehung weiter. Daraus folgt, welche Ecke des Quaders die Setzstelle ist: bei
     * 0 Grad die genannte, sonst die gegenueberliegende in einer oder in beiden Achsen.
     *
     * <p>Im Kern und mit ZAHLEN, nicht hinter der Bukkit-Naht: Eine Rechnung, die nur ein
     * laufender Server ausfuehren kann, ist eine ungepruefte Rechnung. Die vier gemessenen
     * Zahlenpaare stehen darum in {@code BauplanTest} und halten diese Methode fest.
     *
     * <p>Breite und Tiefe kommen UNGEDREHT herein, so wie sie in der Datei stehen.
     */
    public static int setzstelleX(Quader bereich, int grad, int breite, int tiefe) {
        pruefeSetzstelle(bereich, grad, breite, tiefe);
        return switch (grad) {
            case 90 -> bereich.vonX() + tiefe - 1;
            case 180 -> bereich.vonX() + breite - 1;
            default -> bereich.vonX();
        };
    }

    /**
     * Die Stelle, an der eine gedrehte Struktur GESETZT werden muss - ihr Z-Wert.
     *
     * <p>Dieselbe Messung wie bei {@link #setzstelleX}; nur die betroffenen Grade sind
     * andere, weil die beiden Achsen bei einer Vierteldrehung nicht gleichzeitig kippen.
     */
    public static int setzstelleZ(Quader bereich, int grad, int breite, int tiefe) {
        pruefeSetzstelle(bereich, grad, breite, tiefe);
        return switch (grad) {
            case 180 -> bereich.vonZ() + tiefe - 1;
            case 270 -> bereich.vonZ() + breite - 1;
            default -> bereich.vonZ();
        };
    }

    /**
     * Die gemeinsame Eingangspruefung beider Setzstellen.
     *
     * <p>Ein Grad, der keine Vierteldrehung ist, fiele durch das {@code default} sonst
     * stumm auf die ungedrehte Ecke - und ein Haus stuende schief, ohne dass es jemand
     * meldet.
     */
    private static void pruefeSetzstelle(Quader bereich, int grad, int breite, int tiefe) {
        if (bereich == null) {
            throw new IllegalArgumentException("Ohne Quader gibt es keine Setzstelle");
        }
        if (grad != 0 && grad != 90 && grad != 180 && grad != 270) {
            throw new IllegalArgumentException(
                    "Nur 0, 90, 180 und 270 Grad sind Drehungen, nicht " + grad);
        }
        if (breite < 1 || tiefe < 1) {
            throw new IllegalArgumentException("Eine Struktur ist mindestens 1x1 gross,"
                    + " nicht " + breite + "x" + tiefe);
        }
    }

    /**
     * Die unterste Ebene des Giebels - dort beginnt das Dach.
     *
     * <p>So gerechnet, dass der First genau auf der obersten Ebene des Quaders sitzt: Der
     * Giebel braucht {@code (front - 1) / 2} Stufen, und die zieht man von oben ab.
     * Oeffentlich, weil eine Probe die Zahl sonst nachrechnen muesste - und eine
     * nachgerechnete Erwartung ist keine.
     */
    public static int dachansatz(Platzhalterbau bau) {
        return bau.bereich().bisY() - giebelstufen(bau);
    }

    /**
     * Wie viele Ebenen hoch der Giebel ist.
     *
     * <p><b>Steil, soweit die Hoehe es zulaesst</b>: Gewuenscht ist die halbe Front mal
     * {@link #DACHNEIGUNG}. Passt das nicht - ein breiter Bau wie das Bundesratshaus
     * braeuchte sonst sechzehn Ebenen Dach -, wird das Dach flacher, aber nie flacher als
     * eins zu eins: Darunter waere es oben offen, denn das Dach muss sich bis zum First
     * schliessen.
     *
     * <p>Stehen bleiben muessen der Fussboden, das steinerne Erdgeschoss und mindestens
     * eine Ebene darueber - ein Haus, das nur aus Dach besteht, ist ein Zelt.
     */
    public static int giebelstufen(Platzhalterbau bau) {
        int haelfte = (bau.front() - 1) / 2;
        int hoechstens = bau.hoehe() - 1 - SOCKELEBENEN - 1;
        // Das PLUS EINS ist die Firstreihe: Die Einzuege brauchen je DACHNEIGUNG Ebenen,
        // und darauf sitzt die eine Reihe, in der sich beide Seiten treffen. Ohne sie
        // haette die unterste Dachstufe nur eine Ebene und alle anderen zwei - von der
        // Seite eine Treppe mit einer zu kurzen Stufe.
        return Math.max(haelfte + 1,
                Math.min(haelfte * DACHNEIGUNG + 1, hoechstens));
    }

    /**
     * Um wie viel sich das Dach auf dieser Dachebene von JEDER Seite eingezogen hat.
     *
     * <p>Die Neigung steht hier und nicht als Zahl in der Geometrie: Bei einem steilen
     * Dach zieht sich nicht jede Ebene ein, sondern jede zweite - und genau daraus
     * entsteht die Stufung, die ein steiles Ziegeldach hat.
     *
     * @param ebene 0 ist der Dachansatz
     */
    public static int dacheinzug(Platzhalterbau bau, int ebene) {
        int haelfte = (bau.front() - 1) / 2;
        int stufen = giebelstufen(bau);
        if (ebene <= 1) {
            // Die erste Dachebene liegt noch OHNE Einzug auf der Wandkrone - sie ist die
            // Traufe. Ebene 0 ist die Krone selbst und hat gar kein Dach.
            return 0;
        }
        if (stufen <= 1) {
            // Einem Bau, dem nur eine Dachebene bleibt, wird sie zum First.
            return haelfte;
        }
        // Von der Traufe aus verteilen sich die Einzuege gleichmaessig auf die restlichen
        // Ebenen; die letzte traegt den First.
        return Math.min(haelfte, (int) ((long) (ebene - 1) * haelfte / (stufen - 1)));
    }

    /**
     * Die oberste Ebene der Tuer - zwei Bloecke hoch und nie in den Giebel.
     *
     * <p>Ein Bau, dem dafuer der Platz fehlt, bekommt keine Tuer:
     * {@link #tuerHoehe} gibt dann weniger als 2, und {@link #tuer} gibt
     * {@code null}. Ein einzelnes Tuerblatt ohne obere Haelfte waere kaputt und
     * nicht knapp.
     */
    public static int tuerObersteEbene(Platzhalterbau bau) {
        return Math.min(tuerUntersteEbene(bau) + 1, dachansatz(bau) - 1);
    }

    /**
     * Die unterste Ebene der Tuer - die Ebene, auf der man LAEUFT.
     *
     * <p><b>Am laufenden Server gemessen</b> [23.11.2026]: Die unterste Ebene eines
     * Quaders ERSETZT den Boden. Innen und auf der Gasse liegt sie auf derselben
     * Hoehe, und wer darauf steht, hat die Fuesse eine Ebene darueber - genau dort
     * muss die Tuer beginnen.
     *
     * <p><b>Vorher stand hier {@code vonY + 2}</b>, und das war richtig, solange der
     * Sockel EINE Ebene hatte: Dann war {@code vonY} der Boden, {@code vonY + 1} der
     * Sockelstein und die Tuer sass darueber. Mit {@link #SOCKELEBENEN} von 3 ist
     * daraus eine SCHWELLE geworden: In der Tueroeffnung stand auf der Lauf-Ebene
     * Sockelstein, und wer hinein wollte, musste springen. Gemessen am Probeserver
     * an {@code 55/-60/-18}: Kopfstein statt Luft, bei Tuerhaelften auf -59 und -58.
     *
     * <p>Der Block UNTER der Tuer bleibt der Boden - ein Tuerblatt braucht eine
     * Unterlage, und die unterste Quaderebene ist sie.
     */
    public static int tuerUntersteEbene(Platzhalterbau bau) {
        return bau.bereich().vonY() + 1;
    }

    /** Wie viele Bloecke hoch die Oeffnung ist - null, eins oder zwei. */
    public static int tuerHoehe(Platzhalterbau bau) {
        return Math.max(0, tuerObersteEbene(bau) - tuerUntersteEbene(bau) + 1);
    }

    /**
     * Eine Stelle mit einer Richtung - wohin ein Block zeigt, der eine Richtung traegt.
     *
     * <p>Im Kern und ohne Bukkit: {@code BlockFace} ist eine Bukkit-Sache, und die
     * Zuordnung dazu steht im Plugin. Hier steht nur, WO etwas sitzt und WOHIN es weist.
     */
    public record Stelle(int x, int y, int z, Richtung zeigt) {
    }

    /**
     * Die untere Haelfte der Tuer samt der Richtung, in die sie zeigt - oder
     * {@code null}, wenn die Oeffnung fuer eine Tuer zu niedrig ist.
     *
     * <p>Sie zeigt NACH AUSSEN, in die Seite, die der Bau nennt: Eine Tuer, die nach
     * innen zeigt, sieht von der Gasse aus falsch herum aus, und ihr Griff saesse auf der
     * falschen Seite.
     */
    public static Stelle tuer(Platzhalterbau bau) {
        if (tuerHoehe(bau) < 2) {
            return null;
        }
        Quader bereich = bau.bereich();
        int mitte = querMitte(bau);
        int unten = tuerUntersteEbene(bau);
        return switch (bau.tuer()) {
            case NORD -> new Stelle(mitte, unten, bereich.vonZ(), Richtung.NORD);
            case SUED -> new Stelle(mitte, unten, bereich.bisZ(), Richtung.SUED);
            case WEST -> new Stelle(bereich.vonX(), unten, mitte, Richtung.WEST);
            case OST -> new Stelle(bereich.bisX(), unten, mitte, Richtung.OST);
        };
    }

    /**
     * Wo das Namensschild haengt samt der Richtung, in die es zeigt - oder {@code null},
     * wenn der Bau zu niedrig dafuer ist.
     *
     * <p><b>INNEN, und das ist eine Entscheidung mit Flaechenfolge</b> [23.11.2026]:
     * Aussen laege es einen Block JENSEITS des Quaders - und damit ausserhalb von allem,
     * was gegen die Tore, die Ladeneinheiten und die Nachbarbauten geprueft ist. Ein
     * Schild, das in ein fremdes Haus ragt, waere genau der Fehler, den die
     * Ueberschneidungspruefung verhindert.
     *
     * <p>Es sitzt ueber der Tuer, einen Block in den Raum hinein, und zeigt INS Zimmer.
     */
    public static Stelle schild(Platzhalterbau bau) {
        Stelle tuer = tuer(bau);
        if (tuer == null) {
            return null;
        }
        Quader bereich = bau.bereich();
        int hoehe = tuerObersteEbene(bau) + 1;
        if (hoehe > dachansatz(bau) - 1) {
            return null;
        }
        Richtung zeigt = gegenrichtung(bau.tuer());
        return switch (bau.tuer()) {
            case NORD -> new Stelle(tuer.x(), hoehe, bereich.vonZ() + 1, zeigt);
            case SUED -> new Stelle(tuer.x(), hoehe, bereich.bisZ() - 1, zeigt);
            case WEST -> new Stelle(bereich.vonX() + 1, hoehe, tuer.z(), zeigt);
            case OST -> new Stelle(bereich.bisX() - 1, hoehe, tuer.z(), zeigt);
        };
    }

    /**
     * Die Gegenrichtung - im Kern und nicht im Plugin.
     *
     * <p>Sie steht hier und nicht in {@link Richtung}, weil sie als Rechnung ueber die
     * FORM eines Baus entstanden ist: Das Schild zeigt in die Gegenrichtung der Tuer.
     *
     * <p><b>Seit dem 23.11.2026 oeffentlich</b>, denn sie hat einen zweiten Leser: Eine
     * Glasscheibe verbindet sich nach BEIDEN Seiten ihrer Wandachse, und die zweite Seite
     * ist die Gegenrichtung der ersten. Zwei eigene Zuordnungen waeren zwei Stellen, die
     * auseinanderlaufen.
     */
    public static Richtung gegenrichtung(Richtung richtung) {
        if (richtung == null) {
            throw new IllegalArgumentException("Nichts hat keine Gegenrichtung");
        }
        return switch (richtung) {
            case NORD -> Richtung.SUED;
            case SUED -> Richtung.NORD;
            case WEST -> Richtung.OST;
            case OST -> Richtung.WEST;
        };
    }

    /**
     * Wohin eine Dachtreppe an dieser Stelle zeigt - oder {@code null}, wenn hier kein
     * Dach ist.
     *
     * <p><b>BERGAUF, also zum First</b> - und das ist an Mojangs eigenen Daechern
     * GEMESSEN [23.11.2026] und nicht geraten: In {@code plains_butcher_shop_1} traegt die
     * Traufenreihe im Norden {@code facing=south}, die im Sueden {@code facing=north}.
     * Dieselbe Richtung wie bei einer Innentreppe, die nach Norden hochsteigt: Sie traegt
     * {@code facing=north}.
     *
     * <p><b>Warum es diese Methode ueberhaupt gibt</b>: Der Weltgriff setzt einen Block
     * OHNE Blockdaten. Eine Treppe bekommt dann die Vorgabe {@code facing=north} - alle
     * Dachtreppen zeigten in dieselbe Richtung, und eine der zwei Dachseiten lag falsch
     * herum. Auf den Bildern des ersten Versuchs war das der groesste Mangel.
     *
     * <p>Getrennt von {@link #stueckBei} und nicht als zweites Feld: Jene Methode wird von
     * ueber tausend Proben als Zeichenbild gelesen. Eine Probe haelt dafuer, dass die
     * beiden nie auseinanderlaufen - wo Dach ist, gibt es eine Richtung, und sonst keine.
     */
    public static Richtung dachzeigt(Platzhalterbau bau, int x, int y, int z) {
        if (stueckBei(bau, x, y, z) != Stueck.DACH) {
            return null;
        }
        int einzug = dacheinzug(bau, y - dachansatz(bau));
        boolean ersteSeite = quer(bau, x, z) == querVon(bau) + einzug;
        return switch (bau.tuer()) {
            case NORD, SUED -> ersteSeite ? Richtung.OST : Richtung.WEST;
            case OST, WEST -> ersteSeite ? Richtung.SUED : Richtung.NORD;
        };
    }

    /**
     * Entlang welcher Achse die Wand verlaeuft, in der dieses Fenster sitzt - oder
     * {@code null}, wenn hier kein Fenster ist.
     *
     * <p><b>Warum eine Glasscheibe das braucht</b> [23.11.2026]: Der Weltgriff setzt
     * einen Block OHNE Blockdaten, und eine Glasscheibe bekommt dann die Vorgabe "keine
     * Seite verbunden". Sie steht als PFOSTEN mitten im Wandfeld, statt die Fuge zwischen
     * den Gefachbloecken zu fuellen - auf den Bildern des Betreibers der letzte
     * Blockfehler, nachdem Dach und Stoffe stimmten. Mit Physik waere es von selbst
     * richtig, aber Physik waehrend des Bauens laesst Tueren und Schilder fallen.
     *
     * <p>Zurueckgegeben wird EINE Richtung, und zwar die Achse: {@link Richtung#OST} heisst
     * "die Wand laeuft in x", {@link Richtung#SUED} heisst "sie laeuft in z". Die zweite
     * Seite ist deren {@link #gegenrichtung} - so gibt es nur eine Zuordnung und nicht
     * zwei, die auseinanderlaufen koennen.
     *
     * <p>Eine Scheibe in einer ECKE gibt es nicht: Fenster sitzen in der Mitte eines
     * Wandfelds ({@code laengs % BALKENTAKT == 2}), Ecken sind {@code laengs == 0}. Darum
     * ist die Frage nach der einen Achse hier immer eindeutig beantwortbar.
     */
    public static Richtung fensterachse(Platzhalterbau bau, int x, int y, int z) {
        if (stueckBei(bau, x, y, z) != Stueck.FENSTER) {
            return null;
        }
        Quader bereich = bau.bereich();
        if (z == bereich.vonZ() || z == bereich.bisZ()) {
            return Richtung.OST;
        }
        return Richtung.SUED;
    }

    /**
     * Entlang welcher Achse ein LIEGENDER Balken verlaeuft - oder {@code null}, wenn der
     * Balken steht oder hier gar keiner ist.
     *
     * <p><b>Am laufenden Server gemessen</b> [23.11.2026]: Ein Balken aus
     * {@code stripped_dark_oak_wood} bekommt ohne Blockdaten {@code axis=y}. Beim
     * Eckpfosten ist das richtig - er steht. Beim Rahm und bei der Wandkrone ist es
     * falsch: Sie LIEGEN, und die geschaelte Laengstextur laeuft dann senkrecht ueber
     * einen waagerechten Balken. Von der Gasse sieht das aus wie gestapelte Pfosten
     * statt wie ein Band.
     *
     * <p>{@code null} fuer stehende Balken, denn deren Vorgabe ist schon richtig - eine
     * Naht, die auch sie anfasst, waere Arbeit ohne Wirkung. Auch die ECKE bleibt
     * senkrecht: Dort laeuft der Eckpfosten durch, und ein liegender Block in der Ecke
     * zerschnitte ihn.
     *
     * <p>Dieselbe Bauart wie {@link #fensterachse}: EINE Richtung als Achse,
     * {@link Richtung#OST} fuer x und {@link Richtung#SUED} fuer z.
     */
    public static Richtung balkenachse(Platzhalterbau bau, int x, int y, int z) {
        if (stueckBei(bau, x, y, z) != Stueck.BALKEN || !istLiegenderBalken(bau, y)) {
            return null;
        }
        Quader bereich = bau.bereich();
        boolean ecke = (x == bereich.vonX() || x == bereich.bisX())
                && (z == bereich.vonZ() || z == bereich.bisZ());
        if (ecke) {
            return null;
        }
        if (z == bereich.vonZ() || z == bereich.bisZ()) {
            return Richtung.OST;
        }
        return Richtung.SUED;
    }

    /** Was an dieser Stelle des Baus steht.
     *
     * @throws IllegalArgumentException wenn die Stelle nicht im Bau liegt
     */
    public static Stueck stueckBei(Platzhalterbau bau, int x, int y, int z) {
        if (bau == null) {
            throw new IllegalArgumentException("Ohne Bau gibt es keine Stelle");
        }
        if (!bau.enthaelt(x, y, z)) {
            throw new IllegalArgumentException("Die Stelle " + x + "/" + y + "/" + z
                    + " liegt nicht im Bau \"" + bau.schluessel() + "\" ("
                    + bau.bereich().alsText() + ") - wer den Quader abschreitet, kommt"
                    + " nie daneben.");
        }
        Quader bereich = bau.bereich();
        if (y == bereich.vonY()) {
            return Stueck.BODEN;
        }
        if (y > dachansatz(bau)) {
            return imGiebel(bau, x, y, z);
        }
        return inDerWand(bau, x, y, z);
    }

    /**
     * Ueber dem Dachansatz: Treppengiebel an den schmalen Seiten, Dachflaeche an den
     * langen, Luft dazwischen, First oben.
     *
     * <p>Je Ebene zieht sich der Bau um einen Block auf JEDER Seite der Front ein. Was
     * davon uebrig bleibt, ist an den Giebelwaenden eine Stufe und an den Laengsseiten die
     * Dachflaeche; der Raum dazwischen ist der Dachboden und bleibt leer.
     */
    private static Stueck imGiebel(Platzhalterbau bau, int x, int y, int z) {
        int einzug = dacheinzug(bau, y - dachansatz(bau));
        int quer = quer(bau, x, z);
        int querVon = querVon(bau) + einzug;
        int querBis = querBis(bau) - einzug;
        if (quer < querVon || quer > querBis) {
            return Stueck.LUFT;
        }
        boolean amGiebel = tief(bau, x, z) == tiefVon(bau)
                || tief(bau, x, z) == tiefBis(bau);
        if (querVon == querBis) {
            // Oben ist EIN Block uebrig - und der ist First, auch an der Giebelwand: Ein
            // Firstbalken, der an zwei Stellen abbricht, saehe aus wie ein Loch.
            return Stueck.FIRST;
        }
        if (amGiebel) {
            return Stueck.GIEBELSTUFE;
        }
        if (quer == querVon || quer == querBis) {
            // Die obere Ebene einer Stufe wird die Treppe, die untere ein voller Block.
            // Erkannt wird sie daran, dass die NAECHSTE Ebene sich weiter einzieht.
            boolean obersteDerStufe =
                    dacheinzug(bau, (y - dachansatz(bau)) + 1) > einzug;
            return obersteDerStufe ? Stueck.DACH : Stueck.DACHFUSS;
        }
        return Stueck.LUFT;
    }

    /**
     * Unterhalb des Dachansatzes: Sockel, Fachwerk, Fenster, Tuer - und innen Luft.
     *
     * <p>Die Reihenfolge der Fragen ist die Form: Tuer vor Laterne vor Balken vor Fenster
     * vor Gefach. Wer sie dreht, bekommt eine Laterne in der Tuer oder ein Fenster im
     * Balken.
     */
    private static Stueck inDerWand(Platzhalterbau bau, int x, int y, int z) {
        Quader bereich = bau.bereich();
        boolean aussen = x == bereich.vonX() || x == bereich.bisX()
                || z == bereich.vonZ() || z == bereich.bisZ();
        if (!aussen) {
            return Stueck.LUFT;
        }
        if (istTuer(bau, x, y, z)) {
            return Stueck.TUER;
        }
        if (istLaterne(bau, x, y, z)) {
            return Stueck.LATERNE;
        }
        // DIE LIEGENDEN BAENDER VOR DEM FENSTER: Ein Fenster, das den Rahm oder die
        // Wandkrone durchbricht, nimmt dem Dach seine Auflage - und es sah auf der
        // Zeichnung aus wie ein Loch im Balken.
        if (istLiegenderBalken(bau, y)) {
            return Stueck.BALKEN;
        }
        // DAS FENSTER VOR DEM SOCKEL: Sonst waere das steinerne Erdgeschoss blind. Ein
        // Haus ohne Fenster unten sieht aus wie ein Speicher, und davon hat eine Stadt
        // nicht neun.
        if (istFenster(bau, x, y, z)) {
            return Stueck.FENSTER;
        }
        // DER SOCKEL VOR DEN STEHENDEN BALKEN: Ein steinernes Erdgeschoss hat keine
        // hoelzernen Ecksaeulen.
        if (y <= bereich.vonY() + SOCKELEBENEN) {
            return Stueck.SOCKEL;
        }
        if (istStehenderBalken(bau, x, z)) {
            return Stueck.BALKEN;
        }
        return Stueck.GEFACH;
    }

    /**
     * Die LIEGENDEN Baender: der Rahm ueber jedem Geschoss und die Wandkrone.
     *
     * <p>Sie haengen nur an der Hoehe und nicht an der Stelle in der Wand - darum
     * braucht diese Frage kein x und kein z. Die Krone am Dachansatz ist das oberste
     * Band: Darauf sitzt das Dach.
     *
     * <p>Gezaehlt wird ab dem steinernen Erdgeschoss. Im Sockel gibt es keinen Rahm, denn
     * dort gibt es kein Fachwerk.
     */
    private static boolean istLiegenderBalken(Platzhalterbau bau, int y) {
        if (y == dachansatz(bau)) {
            return true;
        }
        int ueberSockel = y - (bau.bereich().vonY() + SOCKELEBENEN);
        return ueberSockel > 0 && ueberSockel % GESCHOSSHOEHE == 0;
    }

    /**
     * Die STEHENDEN Balken: die vier Ecksaeulen und jede {@link #BALKENTAKT}-te Spalte.
     *
     * <p>Sie haengen nur an der Stelle in der Wand und nicht an der Hoehe. Der Takt ist
     * VIER und nicht drei - bei drei war die Wand ueberwiegend Balken. Gemessen an
     * {@code plains_big_house_1}: 57 Staemme auf 340 Bloecke, also eher ein Sechstel.
     */
    private static boolean istStehenderBalken(Platzhalterbau bau, int x, int z) {
        Quader bereich = bau.bereich();
        boolean ecke = (x == bereich.vonX() || x == bereich.bisX())
                && (z == bereich.vonZ() || z == bereich.bisZ());
        return ecke || laengs(bau, x, z) % BALKENTAKT == 0;
    }

    /**
     * Fenster: WENIGE und KLEINE - je Wandfeld eines, zwei Ebenen hoch.
     *
     * <p><b>Das ist die Lehre aus dem ersten Versuch</b> [23.11.2026]: Dort war in jedem
     * Geschoss die ganze Wand zwischen den Baendern Fenster - ein durchlaufendes Band,
     * das eher nach Kaufhaus als nach Hanse aussah. Gemessen an
     * {@code plains_big_house_1}: 12 Glasscheiben auf 340 Bloecke, also drei von hundert.
     *
     * <p>Darum steht ein Fenster nur dort, wo {@code laengs % BALKENTAKT == 2} ist -
     * genau in der Mitte zwischen zwei stehenden Balken, mit einem Block Gefach als
     * Rahmen auf jeder Seite.
     *
     * <p>Im steinernen Erdgeschoss gibt es EINE Fensterebene, auf Augenhoehe neben der
     * Tuer. Ein Erdgeschoss ohne Fenster saehe aus wie ein Speicher.
     */
    private static boolean istFenster(Platzhalterbau bau, int x, int y, int z) {
        if (laengs(bau, x, z) % BALKENTAKT != 2) {
            return false;
        }
        Quader bereich = bau.bereich();
        if (y == bereich.vonY() + 2) {
            return true;
        }
        int ueberSockel = y - (bereich.vonY() + SOCKELEBENEN);
        if (ueberSockel <= 0) {
            return false;
        }
        int imGeschoss = ueberSockel % GESCHOSSHOEHE;
        return imGeschoss == 2 || imGeschoss == 3;
    }

    /** Die Tuer: mittig in der Front, ueber dem Sockel, hoechstens zwei Bloecke hoch. */
    private static boolean istTuer(Platzhalterbau bau, int x, int y, int z) {
        if (tuerHoehe(bau) <= 0 || y < tuerUntersteEbene(bau)
                || y > tuerObersteEbene(bau)) {
            return false;
        }
        Quader bereich = bau.bereich();
        return anDerFront(bau, x, z) && quer(bau, x, z) == querMitte(bau);
    }

    /** Die Laterne: in der Front, einen Block neben der Tuer, auf Augenhoehe. */
    private static boolean istLaterne(Platzhalterbau bau, int x, int y, int z) {
        if (tuerHoehe(bau) <= 0 || y != tuerObersteEbene(bau)) {
            return false;
        }
        if (!anDerFront(bau, x, z)) {
            return false;
        }
        int abstand = quer(bau, x, z) - querMitte(bau);
        return abstand == -2 || abstand == 2;
    }

    /** Ob diese Stelle in der Wand mit der Tuer liegt. */
    private static boolean anDerFront(Platzhalterbau bau, int x, int z) {
        Quader bereich = bau.bereich();
        return switch (bau.tuer()) {
            case NORD -> z == bereich.vonZ();
            case SUED -> z == bereich.bisZ();
            case WEST -> x == bereich.vonX();
            case OST -> x == bereich.bisX();
        };
    }

    /** Die Koordinate QUER zur Tiefe - also entlang der Front. */
    private static int quer(Platzhalterbau bau, int x, int z) {
        return switch (bau.tuer()) {
            case NORD, SUED -> x;
            case OST, WEST -> z;
        };
    }

    /** Die Koordinate ENTLANG der Tiefe - von der Front nach hinten. */
    private static int tief(Platzhalterbau bau, int x, int z) {
        return switch (bau.tuer()) {
            case NORD, SUED -> z;
            case OST, WEST -> x;
        };
    }

    private static int querVon(Platzhalterbau bau) {
        Quader b = bau.bereich();
        return switch (bau.tuer()) {
            case NORD, SUED -> b.vonX();
            case OST, WEST -> b.vonZ();
        };
    }

    private static int querBis(Platzhalterbau bau) {
        Quader b = bau.bereich();
        return switch (bau.tuer()) {
            case NORD, SUED -> b.bisX();
            case OST, WEST -> b.bisZ();
        };
    }

    private static int querMitte(Platzhalterbau bau) {
        return Math.floorDiv(querVon(bau) + querBis(bau), 2);
    }

    private static int tiefVon(Platzhalterbau bau) {
        Quader b = bau.bereich();
        return switch (bau.tuer()) {
            case NORD, SUED -> b.vonZ();
            case OST, WEST -> b.vonX();
        };
    }

    private static int tiefBis(Platzhalterbau bau) {
        Quader b = bau.bereich();
        return switch (bau.tuer()) {
            case NORD, SUED -> b.bisZ();
            case OST, WEST -> b.bisX();
        };
    }

    /**
     * Der Abstand von der naechsten Ecke ENTLANG der Wand, in der die Stelle liegt.
     *
     * <p>Von der Ecke aus gezaehlt und nicht vom Weltnullpunkt: Sonst haette ein Haus bei
     * x=48 einen anderen Balkentakt als eines bei x=49, und zwei gleich gebaute Haeuser
     * saehen verschieden aus.
     */
    private static int laengs(Platzhalterbau bau, int x, int z) {
        Quader b = bau.bereich();
        if (x == b.vonX() || x == b.bisX()) {
            return Math.min(z - b.vonZ(), b.bisZ() - z);
        }
        return Math.min(x - b.vonX(), b.bisX() - x);
    }
}
