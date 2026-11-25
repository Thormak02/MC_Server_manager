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
     * Um wie viel WEITER der Balkentakt eines Hauses sein kann als {@link #BALKENTAKT}.
     *
     * <p><b>Nur nach oben</b> [24.11.2026]: Ein engerer Takt gibt MEHR Fenster, denn ein
     * Fenster sitzt in der Mitte jedes Wandfelds ({@code laengs % takt == 2}). Bei einem
     * Takt von drei saesse in jeder dritten Spalte eines - gemessen 24 Fenster gegen 72
     * Gefachbloecke, und die Probe "Jede Stelle des Quaders hat genau ein Stueck" hat es
     * gefangen: "ein Fensterband sieht nach Kaufhaus aus, nicht nach Hanse". Die
     * Begruendung steht dort seit dem 23.11.2026 und ist richtig.
     *
     * <p>Also vier oder fuenf: vier ein dichtes, altes Fachwerk, fuenf ein weites,
     * neueres. Sechs waere ein Feld, in dem das Fenster keinen Rahmen mehr hat.
     */
    public static final int BALKEN_SPIEL = 1;

    /**
     * Welchen Balkentakt DIESES Haus hat.
     *
     * <p>Gestreut aus seiner Ecke, mit ANDEREN Argumenten als der Sockel: Mit denselben
     * haette jedes Haus mit flachem Sockel auch dichtes Fachwerk, und aus zwei Zuegen
     * wuerde einer.
     */
    public static int balkentakt(Platzhalterbau bau) {
        if (bau == null) {
            throw new IllegalArgumentException("Ohne Bau kein Balkentakt");
        }
        Quader q = bau.bereich();
        return BALKENTAKT
                + Stadtplan.mischung(q.bisX(), q.bisZ(), BALKEN_SPIEL + 1);
    }

    /**
     * Wie viele Ebenen das Dach steigt, bis es sich einen Block eingezogen hat.
     *
     * <p><b>EINS, also 45 Grad</b> [berichtigt 24.11.2026] - und das ist an ZWEI
     * unabhaengigen Vorbildern nachgemessen.
     *
     * <p><b>Vorher stand hier ZWEI, und die Begruendung trug die Zahl nicht.</b> Sie
     * nannte als Messung {@code plains_butcher_shop_1} mit "drei Ebenen Wand, vier
     * Ebenen Dach". Bei sieben Bloecken Front ist das ein Lauf von drei und ein Anstieg
     * von vier, also eine Neigung von 1,33 - aber die Konstante ZWEI machte daraus
     * sieben Dachebenen und damit 2,33. Die gemessene Zahl war richtig und ihre
     * Uebersetzung falsch: Das Dach war damit fast doppelt so steil wie das Vorbild, auf
     * das sich die Begruendung berief.
     *
     * <p><b>Die zweite Messung</b> [24.11.2026]: An einem fremden, gut gebauten
     * Fachwerkhaus (Blueprint eines Strukturpakets, nur AUSGEMESSEN - die Datei wurde
     * nicht uebernommen) laufen die Dachebenen in der Breite 9, 7, 5, 3, 1. Je Ebene
     * zwei Bloecke enger, also einen je Seite: genau 45 Grad. Fuenf Dachebenen bei neun
     * Bloecken Front, und das Dach ist 38 Prozent der Bauhoehe.
     *
     * <p>Mit EINS kommen beide Vorbilder genau heraus: Front 7 gibt vier Dachebenen
     * (wie Mojangs Haus), Front 9 gibt fuenf (wie das Fachwerkhaus).
     *
     * <p>Ein steileres Dach waere kein Fehler der Geometrie, sondern eines des Bildes:
     * Es macht aus einem Haus einen Turm mit Hut, und genau das hat der Betreiber an den
     * alten Tortuermen beklagt - "zu hoch und zu duenn".
     *
     * <p>Passt das Dach nicht in die Hoehe des Quaders, wird es flacher - siehe
     * {@link #giebelstufen}.
     *
     * <p><b>WER DIESE ZAHL ERHOEHT, MUSS EIN STUECK ZURUECKBRINGEN</b>: Bei 2:1
     * zieht sich nicht jede Ebene ein, sondern jede zweite - und dann stehen zwei
     * Treppen uebereinander und lassen die Aussenseite offen. Dafuer gab es bis zum
     * 24.11.2026 das Stueck DACHFUSS, einen vollen Block unter jeder Treppe. Bei
     * 45 Grad zieht sich JEDE Ebene um genau einen Block ein, der Fall kann nie
     * auftreten, und ein Stueck, das nie vorkommt, ist eine Form, die nie gebaut
     * wird - darum ist es entfallen.
     */
    public static final int DACHNEIGUNG = 1;

    /**
     * Wie viele Ebenen ueber dem Fussboden aus Stein sind - das Erdgeschoss.
     *
     * <p><b>Das ganze Erdgeschoss, nicht eine Schwelle</b>: Gemessen an
     * {@code plains_big_house_1} sind 74 von 340 Bloecken Bruchstein, und sie stehen alle
     * unten. Ein Fachwerkhaus steht auf einem steinernen Sockelgeschoss - das schuetzt das
     * Holz vor Spritzwasser, und genau so sehen die Haeuser aus, die als Vorbild dienen.
     */
    public static final int SOCKELEBENEN = 3;

    /**
     * Um wie viel die Sockelhoehe eines Hauses von {@link #SOCKELEBENEN} abweichen kann.
     *
     * <p><b>Warum die Haeuser sonst gleich aussehen</b> [24.11.2026]: Der Betreiber
     * sagte "es soll ausserdem mehr variation bei den haeusern geben". Breite, Tiefe,
     * Hoehe, Baustoff und seit heute Firstrichtung und Baulinie wechseln schon - die
     * FORM aber nicht: Jedes Haus hatte genau drei Ebenen steinernen Sockel und seine
     * Balken genau jede vierte Spalte. Zwei Haeuser aus demselben Stein sahen darum
     * identisch aus, auch wenn ihre Masse verschieden waren.
     *
     * <p>Eins: Zwei Ebenen Sockel sind ein Kaufmannshaus mit flachem Laden, vier ein
     * Speicher auf hohem Fundament. Null waere ein Haus ohne steinernen Fuss, und das
     * gibt es an einer hansischen Gasse nicht.
     */
    public static final int SOCKEL_SPIEL = 1;

    /**
     * Wie viele Ebenen steinernen Sockel DIESES Haus hat.
     *
     * <p>Gestreut aus seiner Ecke, damit ein zweites Aufsetzen dieselbe Stadt legt -
     * und je HAUS und nicht nestweise: Der Sockel ist das, was man als Erstes sieht,
     * wenn man die Gasse hinabgeht.
     */
    public static int sockelebenen(Platzhalterbau bau) {
        if (bau == null) {
            throw new IllegalArgumentException("Ohne Bau kein Sockel");
        }
        Quader q = bau.bereich();
        return SOCKELEBENEN - SOCKEL_SPIEL
                + Stadtplan.mischung(q.vonX(), q.vonZ(), 2 * SOCKEL_SPIEL + 1);
    }

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
        /** Der First ganz oben. */
        FIRST,
        /**
         * Eine GESCHOSSDECKE - der Fussboden des Geschosses darueber.
         *
         * <p><b>Warum es sie gibt</b> [24.11.2026]: Die 265 Zeilenhaeuser Altfurts waren
         * innen ein leerer Schacht von zwoelf bis zwanzig Ebenen. Von aussen unsichtbar;
         * wer eine Tuer aufmachte, stand in einem Turm.
         *
         * <p>Sie liegt GENAU dort, wo auf der Fassade das liegende Band sitzt - siehe
         * {@link #istLiegenderBalken}. Das Band war von Anfang an der Rahm ueber einem
         * Geschoss; es war nur nie etwas dahinter. Eine zweite Hoehenrechnung gibt es
         * darum nicht.
         */
        DECKE,
        /**
         * Eine Sprosse der Leiter im Treppenschacht.
         *
         * <p>Eine Leiter und keine Treppe: Eine Treppe braucht zwei Bloecke Grundflaeche
         * je Ebene und eine Wendung, und bei einem Haus von fuenf Bloecken Breite bliebe
         * daneben kein Zimmer. Die Leiter steht in einer Spalte, und die Decken lassen
         * genau diese Spalte offen.
         */
        LEITER,
        /**
         * Ein Block des Schornsteins - gemauert, auch bei einem Fachwerkhaus.
         *
         * <p>Er gehoert zur SCHALE und nicht zu einem Nachtrag: Nur so wird jede Stelle
         * des Quaders genau einmal beschrieben, und nur so ist das Setzen vollstaendig
         * umkehrbar. Der erste Entwurf setzte ihn hinterher obendrauf - das rief drei
         * Stellen zweimal und ragte aus dem Quader heraus.
         */
        SCHORNSTEIN,
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
     * Jedes wievielte Haus einen Schornstein bekommt.
     *
     * <p><b>Warum es Schornsteine gibt</b> [24.11.2026]: Altfurt hat 265 Zeilenhaeuser,
     * und ihre Daecher waren eine Folge gleicher Dreiecke. Ein Schornstein ist der
     * billigste Zug, der eine Dachlandschaft gewachsen aussehen laesst - er bricht die
     * Firstlinie, und zwar an jedem dritten Haus an anderer Stelle.
     *
     * <p>Jedes DRITTE und nicht jedes: Eine Reihe, in der jedes Haus einen traegt, ist
     * wieder eine Reihe. In einer gewachsenen Stadt hat das Haus daneben eine kalte
     * Kueche.
     *
     * <p><b>Etwa jedes dritte, nicht genau</b>: Gemessen am 24.11.2026 tragen 109 von
     * 265 Altfurter Zeilenhaeusern einen Schornstein - also eher jedes zweieinhalbte.
     * Die Streuung laeuft ueber die Hausecke, und die Hausecken liegen auf einem Raster;
     * ein Rest modulo drei ist darauf nicht gleichverteilt. Das ist kein Mangel, sondern
     * der Grund, warum hier eine gemessene Zahl steht und keine gerechnete.
     */
    public static final int SCHORNSTEIN_TAKT = 3;

    /**
     * Wie hoch der Durchgang ist, mit dem ein Kreuzarm durch einen Bau hindurchgeht.
     *
     * <p><b>Warum es die Laube gibt</b> [24.11.2026]: Der Betreiber wollte Wege, die
     * "direkt zu den toren fuehren". Gemessen war jede der vier Achsen von einem
     * eingetragenen Bau ueberspannt - die Stadtkirche quer im Nordarm, das
     * Bundesratshaus im Suedarm, Zunfthaus und Kaufmannskontor in Ost und West. Zwei
     * Antworten waren moeglich: die Bauten wegschieben, oder den Weg durch sie
     * hindurchfuehren. Fuer die Kirche und das Rathaus ist das Zweite das Richtige: Sie
     * stehen MITTIG auf ihrem Platz, und ein Weg, der um sie herum knickt, ist kein
     * Weg zum Tor. Eine Laube, durch die die Strasse laeuft, hat jede Hansestadt.
     *
     * <p>Fuenf Ebenen - dasselbe Mass wie der Torhausdurchgang. Ein Durchgang, durch den
     * man sich buecken muss, ist eine Tuer und keine Strasse.
     */
    public static final int LAUBE_HOEHE = 5;

    /**
     * Wie weit der Treppenschacht von der Hausecke entfernt liegt, in Bloecken.
     *
     * <p>Eins: Die Leiter braucht eine Wand im Ruecken, und die innerste Spalte neben
     * der Ecke hat zwei davon. Weiter innen haengt sie an der Luft.
     */
    public static final int SCHACHT_AB_ECKE = 1;

    /**
     * Das kleinste Aussenmass, bei dem ein Haus einen Treppenschacht bekommt.
     *
     * <p>Fuenf: Davon gehen zwei Waende ab, es bleiben drei innere Spalten. Eine davon
     * ist der Schacht, zwei bleiben Zimmer. Bei vier bliebe eine, bei drei keine.
     */
    public static final int SCHACHT_WENIGSTENS = 5;

    /**
     * Wie weit ein Schornstein hoechstens ueber die Dachflaeche ragt.
     *
     * <p><b>Ueber die FLAeCHE und nicht ueber den First</b> [24.11.2026]: Der erste
     * Entwurf setzte den Schornstein AUF den First und zwei Ebenen darueber - also
     * jenseits des Hausquaders. Das riss eine Zusage ein, die mehr wert ist als ein
     * Schornstein: Ein Haus fasst nichts ausserhalb seines Quaders an. Erst dadurch ist
     * das Setzen VOLLSTAeNDIG umkehrbar - wer ein Haus neu setzt, bekaeme sonst den
     * Schornstein des alten dazu, wenn das neue keinen hat.
     *
     * <p>Jetzt steht er neben dem First, stoesst durch die Dachflaeche und reicht bis zur
     * Firsthoehe. Bei einem Dach von 45 Grad liegt die Flaeche einen Block neben dem
     * First genau eine Ebene tiefer - der Schornstein ragt also so weit heraus, wie er
     * vom First entfernt steht. Das ist zugleich die haeufigste Stelle eines wirklichen
     * Schornsteins: Er sitzt im Dach und nicht auf dem Grat.
     */
    public static final int SCHORNSTEIN_HOCH = 2;

    /**
     * Die kuerzeste Firstlaenge, die noch einen Schornstein traegt.
     *
     * <p>Drei: Der Schornstein steht nie auf dem aeussersten Firstblock - dort ist er
     * die Spitze des Giebels und nicht ein Schornstein. Es braucht also einen Block
     * Rand auf jeder Seite und einen fuer ihn selbst.
     */
    public static final int FIRST_WENIGSTENS = 3;

    /**
     * Ob dieser Bau einen Kreuzarm GANZ ueberspannt.
     *
     * <p>Nur dann bekommt er eine Laube. Ein Bau, der nur zur HAELFTE in den Arm
     * hineinreicht, wuerde von einem Durchgang abgeschnitten - bei ihm ist die richtige
     * Antwort, ihn zu verschieben, und das sagt der Start dem Betreiber. Diese Frage ist
     * damit auch der Riegel, der eine Laube an der falschen Stelle verhindert.
     *
     * <p>Gefragt wird gegen {@link Stadtplan#KREUZ_HALB}, also samt Kante: Ein Bau, der
     * nur die Fahrbahn ueberspannt, haette keine Pfeiler - siehe {@link #inDerLaube}.
     */
    public static boolean ueberspanntKreuzarm(Platzhalterbau bau) {
        if (bau == null) {
            throw new IllegalArgumentException("Ohne Bau keine Laube");
        }
        Quader q = bau.bereich();
        boolean inX = q.vonX() <= Stadtplan.MITTE_X - Stadtplan.KREUZ_HALB
                && q.bisX() >= Stadtplan.MITTE_X + Stadtplan.KREUZ_HALB;
        boolean inZ = q.vonZ() <= Stadtplan.MITTE_Z - Stadtplan.KREUZ_HALB
                && q.bisZ() >= Stadtplan.MITTE_Z + Stadtplan.KREUZ_HALB;
        return inX || inZ;
    }

    /**
     * Ob diese Stelle in der Laube liegt - dem Durchgang des Kreuzarms durch den Bau.
     *
     * <p>Durchgang ist nur die FAHRBAHN, nicht der ganze Arm: Die beiden Kantenspalten
     * bleiben Mauerwerk und sind damit die PFEILER der Laube. Eine neun Bloecke weite
     * Oeffnung ohne Pfeiler waere ein Loch in der Wand, kein Bogen.
     *
     * <p>Der FUSSBODEN bleibt: Man laeuft durch die Laube ueber den Boden des Baus, und
     * ein Durchgang ohne Boden waere ein Graben. Darueber beginnt der Bau wieder - samt
     * seiner Geschossdecke, die dann die Decke der Laube ist.
     */
    public static boolean inDerLaube(Platzhalterbau bau, int x, int y, int z) {
        if (bau == null) {
            throw new IllegalArgumentException("Ohne Bau keine Laube");
        }
        if (!ueberspanntKreuzarm(bau)) {
            return false;
        }
        if (Stadtplan.kreuzBei(x, z) != Stadtplan.Lage.RADIALGASSE) {
            return false;
        }
        int vonY = bau.bereich().vonY();
        return y > vonY && y <= vonY + LAUBE_HOEHE;
    }

    /**
     * Ob diese Stelle die WAND der Laube ist.
     *
     * <p><b>Warum die Laube Waende braucht</b> [24.11.2026]: Ein Bau ist innen HOHL.
     * Ohne diese Regel oeffnet sich der Durchgang nach beiden Seiten in den leeren
     * Innenraum - man laeuft durch die Laube und steht im Gebaeude, ohne eine Tuer
     * genommen zu haben. Gefunden hat es die Laubenprobe selbst: Sie verlangte an der
     * Kante des Arms einen Pfeiler und fand Luft.
     *
     * <p>Die Wand steht auf der KANTE des Arms, also auf den beiden Spalten, die
     * {@link Stadtplan#kreuzBei} {@link Stadtplan.Lage#BORDSTEIN} nennt - je eine links
     * und rechts. Damit ist der Bordstein draussen die Kante der Strasse und drinnen der
     * Pfeiler der Laube: EINE Linie, zwei Bedeutungen, und beide aus derselben Rechnung.
     *
     * <p><b>Diese Frage sagt nur SPALTE und HOEHE</b>, nicht "hier steht wirklich
     * Mauerwerk": Dass die AUSSENWAND des Baus vorgeht - mit ihren Fenstern und ihrer
     * Tuer -, entscheidet {@link #stueckBei}, und zwar daran, dass die Laubenwand erst
     * im Innenraum gefragt wird. Eine Laubenwand, die ein Fenster verdraengt, waere eine
     * zweite Wand vor der ersten.
     */
    public static boolean anDerLaube(Platzhalterbau bau, int x, int y, int z) {
        if (bau == null) {
            throw new IllegalArgumentException("Ohne Bau keine Laubenwand");
        }
        if (!ueberspanntKreuzarm(bau)) {
            return false;
        }
        if (Stadtplan.kreuzBei(x, z) != Stadtplan.Lage.BORDSTEIN) {
            return false;
        }
        int vonY = bau.bereich().vonY();
        return y > vonY && y <= vonY + LAUBE_HOEHE;
    }

    /**
     * Der Schornstein dieses Baus - ein Quader von einem Block Grundflaeche, oder
     * {@code null}.
     *
     * <p>Er steht NEBEN dem First, stoesst durch die Dachflaeche und reicht bis zur
     * Firsthoehe - und bleibt damit ganz im Hausquader. Warum das wichtiger ist als ein
     * hoeherer Schornstein, steht bei {@link #SCHORNSTEIN_HOCH}.
     *
     * <p><b>Die Stelle laengs des Firstes ist gestreut</b>, und zwar aus der Ecke des
     * Hauses: Dieselbe Stadt entsteht beim zweiten Aufsetzen wieder. Gefragt wird
     * {@link Stadtplan#mischung} mit VERTAUSCHTEN Achsen - mit denselben Argumenten wie
     * die Frage, OB es einen Schornstein gibt, waere die Stelle an das Ob gekoppelt und
     * jeder Schornstein staende an derselben relativen Stelle.
     *
     * <p><b>Ein schmales Dach traegt keinen</b>: Der Schornstein braucht einen Block
     * Abstand zum First UND einen zur Traufe - an der Traufe sitzt er nicht im Dach,
     * sondern haengt an der Wand. Bei einer Dachbreite von fuenf bleibt dafuer genau
     * eine Stelle, darunter keine.
     */
    public static Quader schornstein(Platzhalterbau bau) {
        if (bau == null) {
            throw new IllegalArgumentException("Ohne Bau kein Schornstein");
        }
        Quader q = bau.bereich();
        if (Stadtplan.mischung(q.vonX(), q.vonZ(), SCHORNSTEIN_TAKT) != 0) {
            return null;
        }
        // WIE WEIT NEBEN DEM FIRST: so weit wie moeglich, aber nicht an die Traufe. Der
        // Abstand IST die Hoehe, um die er heraussteht - bei 45 Grad faellt das Dach je
        // Block um eine Ebene.
        int abstand = Math.min(SCHORNSTEIN_HOCH, (dachbreite(bau) - 1) / 2 - 1);
        if (abstand < 1) {
            return null;
        }
        // DIE FALLENDE ACHSE ist die der dachbreite; der FIRST laeuft ueber die andere.
        boolean frontIstX = bau.tuer() == Richtung.NORD || bau.tuer() == Richtung.SUED;
        boolean fallendIstX = traufstaendig(bau) ? !frontIstX : frontIstX;
        int firstVon = fallendIstX ? q.vonZ() : q.vonX();
        int firstBis = fallendIstX ? q.bisZ() : q.bisX();
        int laenge = firstBis - firstVon + 1;
        if (laenge < FIRST_WENIGSTENS) {
            return null;
        }
        int stelle = firstVon + 1
                + Stadtplan.mischung(q.vonZ(), q.vonX(), laenge - 2);
        int mitte = fallendIstX ? (q.vonX() + q.bisX()) / 2
                : (q.vonZ() + q.bisZ()) / 2;
        // AUF WELCHE SEITE des Firstes - sonst stehen alle Schornsteine der Zeile auf
        // derselben Dachhaelfte.
        int quer = mitte + (Stadtplan.mischung(q.bisX(), q.bisZ(), 2) == 0
                ? abstand : -abstand);
        int x = fallendIstX ? quer : stelle;
        int z = fallendIstX ? stelle : quer;
        // DER FIRST LIEGT AUF DER OBERKANTE DES QUADERS: dachansatz ist
        // bisY - giebelstufen, und darueber liegen genau giebelstufen Ebenen Dach.
        int first = q.bisY();
        return new Quader(x, first - abstand, z, x, first, z);
    }

    /**
     * Die Spanne, ueber die das Dach FAELLT - die kuerzere der beiden waagerechten.
     *
     * <p><b>Warum die kuerzere</b> [24.11.2026]: Ein Satteldach faellt immer ueber die
     * kurze Seite, und sein First laeuft ueber die lange. Alles andere ergibt ein Dach,
     * das hoeher ist als das Haus.
     *
     * <p><b>Vorher fiel das Dach immer ueber die FRONT</b>, also ueber die Tuerseite. Bei
     * einem schmalen Buergerhaus ist das richtig - die Front IST die kurze Seite. Beim
     * Bundesratshaus (33 breit, 11 tief, 16 hoch) war es falsch: Die halbe Front sind 16
     * Bloecke, das Dach brauchte 17 Ebenen, und der Dachansatz landete auf y -63 - zwei
     * Ebenen UNTER dem Boden. Das Haus hatte dadurch keine einzige Wand, keine Tuer und
     * kein Fenster: ein Haufen Giebelstufen von 33 Bloecken Breite. Die
     * {@code config.yml} sagt ueber dasselbe Haus "DAS RATHAUS ist traufstaendig und
     * breit - sein First laeuft parallel zum Platz". Jetzt stimmt das auch.
     */
    public static int dachbreite(Platzhalterbau bau) {
        return Math.min(bau.front(), bau.tiefe());
    }

    /**
     * Ob der Bau TRAUFstaendig steht - die lange Seite zur Gasse, der First parallel
     * dazu.
     *
     * <p>Das Gegenteil ist giebelstaendig: die Schmalseite zur Gasse, der Giebel zur
     * Front. So steht ein Hansehaus in einer Zeile, und so stehen alle gerechneten
     * Zeilenhaeuser - ihre Front ist 5, 7 oder 9 breit und ihre Tiefe 9 bis 11.
     *
     * <p>Bei GLEICHER Breite und Tiefe gilt giebelstaendig: Dann ist die Front die
     * Seite, auf die es ankommt, denn dort ist die Tuer.
     */
    public static boolean traufstaendig(Platzhalterbau bau) {
        return bau.front() > bau.tiefe();
    }

    /**
     * Wie viele Ebenen hoch der Giebel ist.
     *
     * <p><b>45 Grad, soweit die Hoehe es zulaesst</b>: Gewuenscht ist die halbe
     * {@link #dachbreite} mal {@link #DACHNEIGUNG}, plus die Firstreihe. Passt das nicht
     * in den Quader, wird das Dach FLACHER - lieber ein flaches Dach als ein Haus ohne
     * Wand.
     *
     * <p>Stehen bleiben muessen der Fussboden, das steinerne Erdgeschoss und mindestens
     * eine Ebene darueber - ein Haus, das nur aus Dach besteht, ist ein Zelt. Genau das
     * war es bis zum 24.11.2026: Eine Untergrenze von {@code haelfte + 1} erzwang 45
     * Grad auch dann, wenn der Quader sie nicht traegt, und beim Bundesratshaus rutschte
     * der Dachansatz dadurch zwei Ebenen UNTER den Boden.
     */
    public static int giebelstufen(Platzhalterbau bau) {
        int haelfte = (dachbreite(bau) - 1) / 2;
        int hoechstens = bau.hoehe() - 1 - sockelebenen(bau) - 1;
        // Das PLUS EINS ist die Firstreihe: Die Einzuege brauchen je DACHNEIGUNG Ebenen,
        // und darauf sitzt die eine Reihe, in der sich beide Seiten treffen. Ohne sie
        // haette die unterste Dachstufe nur eine Ebene und alle anderen zwei - von der
        // Seite eine Treppe mit einer zu kurzen Stufe.
        //
        // DIE HOEHE DES QUADERS IST DIE HARTE GRENZE [berichtigt 24.11.2026]: Vorher
        // stand hier max(haelfte + 1, ...) und erzwang mindestens 45 Grad, auch wenn der
        // Quader das nicht traegt. Bei einem breiten, niedrigen Bau wurde das Dach damit
        // hoeher als das Haus, und der Dachansatz rutschte unter den Boden - das Haus
        // bestand dann ganz aus Dach. Lieber ein flacheres Dach als ein Haus ohne Wand.
        return Math.max(1, Math.min(haelfte * DACHNEIGUNG + 1, hoechstens));
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
        int haelfte = (dachbreite(bau) - 1) / 2;
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
        // BERGAUF heisst: von der Traufe zum First, also in Richtung der WACHSENDEN
        // Koordinate auf der fallenden Achse - und von der oberen Kante dagegen.
        // Gefragt wird fallendIstX und nicht die Tuerseite: siehe dort.
        boolean ersteSeite = fallend(bau, x, z) == fallendVon(bau) + einzug;
        if (fallendIstX(bau)) {
            return ersteSeite ? Richtung.OST : Richtung.WEST;
        }
        return ersteSeite ? Richtung.SUED : Richtung.NORD;
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
        // DIE LAUBE VOR ALLEM UEBRIGEN: Durch sie laeuft die Strasse, und was dort
        // sonst stuende - Wand, Fenster, Geschossdecke, Leiter - stuende ihr im Weg.
        // Nach dem BODEN, denn man laeuft darauf.
        if (inDerLaube(bau, x, y, z)) {
            return Stueck.LUFT;
        }
        // DER SCHORNSTEIN VOR DEM DACH: Er stoesst durch die Dachflaeche, seine Stellen
        // waeren sonst Dachtreppe und Luft. Vor dem Boden steht er nicht - ein
        // Schornstein, der den Fussboden ersetzt, waere ein Loch im Zimmer.
        Quader schornstein = schornstein(bau);
        if (schornstein != null && schornstein.enthaelt(x, y, z)) {
            return Stueck.SCHORNSTEIN;
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
        // DIE FALLENDE ACHSE ist die kuerzere, und die andere traegt den First - die
        // Wahl steht in fallendIstX und NICHT hier, weil dachzeigt sie auch braucht.
        int quer = fallend(bau, x, z) ;
        int querVon = fallendVon(bau) + einzug;
        int querBis = fallendBis(bau) - einzug;
        if (quer < querVon || quer > querBis) {
            return Stueck.LUFT;
        }
        // AM GIEBEL steht man, wenn man am Ende der FIRSTachse ist - also der anderen.
        int laengs = fallendIstX(bau) ? z : x;
        Quader b = bau.bereich();
        boolean amGiebel = fallendIstX(bau)
                ? laengs == b.vonZ() || laengs == b.bisZ()
                : laengs == b.vonX() || laengs == b.bisX();
        if (querVon == querBis) {
            // Oben ist EIN Block uebrig - und der ist First, auch an der Giebelwand: Ein
            // Firstbalken, der an zwei Stellen abbricht, saehe aus wie ein Loch.
            return Stueck.FIRST;
        }
        if (amGiebel) {
            return Stueck.GIEBELSTUFE;
        }
        if (quer == querVon || quer == querBis) {
            // JEDE Dachebene ist eine Treppe: Bei 45 Grad zieht sich das Dach je
            // Ebene um genau einen Block ein, also sitzt je Ebene und Seite genau
            // eine Treppe. Ein Fuellblock darunter waere nur bei einem steileren
            // Dach noetig - siehe DACHNEIGUNG.
            return Stueck.DACH;
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
            // DIE WAND DER LAUBE ZUERST: Sie trennt die Strasse vom Haus. Ohne sie
            // oeffnet sich der Durchgang in den hohlen Innenraum - siehe anDerLaube.
            if (anDerLaube(bau, x, y, z)) {
                return Stueck.GEFACH;
            }
            // IM TREPPENSCHACHT HAENGT DIE LEITER, und die Decken lassen ihn offen -
            // sonst fuehrte die Leiter gegen einen Fussboden.
            if (imSchacht(bau, x, z)) {
                return Stueck.LEITER;
            }
            // DIE DECKE LIEGT, WO AUSSEN DAS BAND SITZT: eine Hoehenrechnung, zwei
            // Seiten derselben Wand. Das oberste Band ist die Wandkrone am Dachansatz -
            // dort liegt der Dachboden.
            return istLiegenderBalken(bau, y) ? Stueck.DECKE : Stueck.LUFT;
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
        if (y <= bereich.vonY() + sockelebenen(bau)) {
            return Stueck.SOCKEL;
        }
        if (istStehenderBalken(bau, x, z)) {
            return Stueck.BALKEN;
        }
        return Stueck.GEFACH;
    }

    /**
     * Ob diese Spalte der Treppenschacht ist.
     *
     * <p>Eine Spalte, {@link #SCHACHT_AB_ECKE} Bloecke von der Ecke bei
     * {@code vonX / vonZ} - dort hat die Leiter zwei Waende im Ruecken. Welche Ecke, ist
     * NICHT gestreut: Ein Schacht, der je Haus anderswo liegt, ist beim Betreten eine
     * Suche, und ein Haus ist kein Irrgarten.
     *
     * <p><b>Ein zu kleines Haus bekommt keinen</b>: Bei drei Bloecken Aussenmass ist die
     * Spalte neben der Ecke die EINZIGE innere - eine Leiter darin fuellte das ganze
     * Zimmer. Dann bleibt das Haus innen offen, und das ist die bessere von zwei
     * schlechten Antworten.
     */
    public static boolean imSchacht(Platzhalterbau bau, int x, int z) {
        if (bau == null) {
            throw new IllegalArgumentException("Ohne Bau kein Schacht");
        }
        Quader bereich = bau.bereich();
        if (bau.front() < SCHACHT_WENIGSTENS || bau.tiefe() < SCHACHT_WENIGSTENS) {
            return false;
        }
        return x == bereich.vonX() + SCHACHT_AB_ECKE
                && z == bereich.vonZ() + SCHACHT_AB_ECKE;
    }

    /**
     * Wohin die Leiter an dieser Stelle zeigt - oder {@code null}, wenn dort keine ist.
     *
     * <p>Eine Leiter zeigt VON der Wand WEG, an der sie haengt: Ihr {@code facing} ist
     * die Richtung, in die der Kletterer schaut. Der Schacht liegt an der Ecke
     * {@code vonX / vonZ}, die Wand im Ruecken ist also die bei {@code vonX} - die
     * Leiter zeigt nach OST.
     *
     * <p><b>Warum diese Frage ueberhaupt gestellt wird</b>: Der Weltgriff setzt einen
     * Block OHNE Blockdaten. Eine Leiter bekommt dann die Vorgabe und haengt an der
     * falschen Wand oder faellt ab - dieselbe Falle wie bei Dachtreppe, Glasscheibe und
     * liegendem Balken.
     */
    public static Richtung leiterzeigt(Platzhalterbau bau, int x, int y, int z) {
        if (bau == null) {
            throw new IllegalArgumentException("Ohne Bau keine Leiter");
        }
        if (!bau.enthaelt(x, y, z) || stueckBei(bau, x, y, z) != Stueck.LEITER) {
            return null;
        }
        return Richtung.OST;
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
        int ueberSockel = y - (bau.bereich().vonY() + sockelebenen(bau));
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
        return ecke || laengs(bau, x, z) % balkentakt(bau) == 0;
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
        if (laengs(bau, x, z) % balkentakt(bau) != 2) {
            return false;
        }
        Quader bereich = bau.bereich();
        if (y == bereich.vonY() + 2) {
            return true;
        }
        int ueberSockel = y - (bereich.vonY() + sockelebenen(bau));
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

    /**
     * Ob die Achse, ueber die das Dach FAELLT, die x-Achse ist.
     *
     * <p><b>Die eine Stelle</b> [24.11.2026]: Diese Frage wird zweimal gebraucht -
     * {@link #imGiebel} baut daraus die Dachflaeche, {@link #dachzeigt} die Richtung
     * ihrer Treppen. Sie stand zweimal da, und zwar VERSCHIEDEN: imGiebel fragte
     * {@link #traufstaendig}, dachzeigt nur die Tuerseite. Bei einem traufstaendigen Bau
     * liefen sie darum auseinander, und gemessen am Bundesratshaus (17 breit, 13 tief)
     * lagen ALLE 179 Dachtreppen quer statt bergauf - keine einzige richtig. Ein
     * giebelstaendiges Zeilenhaus war zu hundert Prozent richtig, darum fiel es nie auf:
     * Alle 265 Zeilenhaeuser sind giebelstaendig.
     */
    private static boolean fallendIstX(Platzhalterbau bau) {
        boolean querIstX = bau.tuer() == Richtung.NORD || bau.tuer() == Richtung.SUED;
        return traufstaendig(bau) ? !querIstX : querIstX;
    }

    /** Die Koordinate entlang der Achse, ueber die das Dach faellt. */
    private static int fallend(Platzhalterbau bau, int x, int z) {
        return fallendIstX(bau) ? x : z;
    }

    /** Die untere Kante der fallenden Achse - dort setzt die Traufe an. */
    private static int fallendVon(Platzhalterbau bau) {
        Quader b = bau.bereich();
        return fallendIstX(bau) ? b.vonX() : b.vonZ();
    }

    /** Die obere Kante der fallenden Achse. */
    private static int fallendBis(Platzhalterbau bau) {
        Quader b = bau.bereich();
        return fallendIstX(bau) ? b.bisX() : b.bisZ();
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
