package de.freimarken.kern;

import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Ein Kistenshop - ein Schild an einer Kiste, das handelt.
 *
 * <p>Die Standardform des Verkaufs (Plan 7.4), dem alten Lumia nachgebaut: Rechtsklick
 * kauft, Linksklick verkauft, Sneak handelt einen ganzen Stapel. Kein Befehl, kein
 * Menue - man geht hin und klickt.
 *
 * <p><b>Das Schild ist die Bedienung, DIESE Zeile ist die Wahrheit.</b> Was auf dem Schild
 * steht, kann ein Spieler lesen; was gehandelt wird, steht hier. Der Grund ist die
 * {@link Warenprobe}: Verzauberte Ausruestung passt nicht auf ein Schild, und ChestShop
 * fuehrt dafuer vier offene Fehler.
 *
 * <p><b>Zwei Preise auf einem Shop</b>, wie bei Lumia ({@code B 100 : S 80}). QuickShop
 * verlangt fuer Kauf und Ankauf zwei getrennte Shops; das waere eine Doppelkiste je Ware
 * und in einer Markthalle mit wenigen Staenden nicht tragbar.
 *
 * <p><b>Der Verkaufspreis darf NIE ueber dem Kaufpreis liegen.</b> Das ist keine
 * Vorsicht, sondern eine Schranke gegen eine Gelddruckmaschine aus Versehen: Waere S
 * ueber B, kauft man im selben Shop billig und verkauft teuer zurueck. ChestShop fuehrt
 * dieselbe Sperre und begruendet sie in seiner eigenen Config - "This prevents newbies
 * from creating shops that would be exploited".
 *
 * @param schildWelt  die Welt des Schildes, als Schluesseltext. Shop und Kiste liegen
 *                    immer in DERSELBEN Welt - ein Schild, das in eine andere zeigt,
 *                    waere ein Shop ohne Kiste
 * @param schildX     wo das Schild haengt. Es ist der SCHLUESSEL des Shops: Ein Schild
 *                    traegt genau einen Shop, und eine Kiste kann mehrere Schilder haben
 *                    (eine Doppelkiste mit zwei Waren)
 * @param schildY     "
 * @param schildZ     "
 * @param kisteX      wo die Kiste steht. Eigene Angabe und nicht gerechnet: Das Schild
 *                    darf an, ueber, unter oder neben der Kiste haengen, und welche von
 *                    sechs Richtungen gilt, entscheidet die Schicht darueber einmal beim
 *                    Anlegen - nicht bei jedem Klick neu
 * @param kisteY      "
 * @param kisteZ      "
 * @param besitzer    wem der Shop gehoert, oder {@code null} fuer einen BUNDSHOP. Der
 *                    Bund hat unbegrenzten Bestand und unbegrenzten Platz; sein Geld
 *                    kommt aus dem Nichts und verschwindet dorthin. Dieselbe Loesung wie
 *                    ChestShops Adminshop, nur ueber ein fehlendes Feld statt ueber einen
 *                    Namen in der ersten Schildzeile - ein Spieler, der sich "Admin Shop"
 *                    nennt, kann damit nichts anfangen
 * @param menge       wie viele Stueck ein Handel umfasst
 * @param kaufpreis   was ein Kunde zahlt, um die {@code menge} zu KAUFEN. 0 heisst: Es
 *                    wird nicht verkauft
 * @param verkaufpreis was der Shop einem Kunden zahlt, der die {@code menge} ANBIETET.
 *                    0 heisst: Es wird nicht angekauft
 * @param ware        was gehandelt wird - EIN Exemplar, siehe {@link Warenprobe}
 * @param freigegeben wer ausser dem Besitzer an die Kiste darf - die Mitbenutzerliste
 *                    aus {@code /shop freigeben} (gebaut 09.11.2026). Sie REIST MIT der
 *                    Zeile, wie die Mitbauer einer {@link Parzelle}: Wer einen Shop
 *                    liest, hat seine Freigaben schon dabei, und
 *                    {@link #offenFuer} braucht keine zweite Abfrage - jene Methode
 *                    laeuft bei jedem Trichtertakt und jeder Explosion.
 *                    <p>Nie {@code null}; eine leere Menge heisst "niemand". Ein
 *                    BUNDSHOP hat immer eine leere - er gehoert niemandem, also kann
 *                    niemand ihn freigeben
 */
public record Kistenshop(String schildWelt, int schildX, int schildY, int schildZ,
                         int kisteX, int kisteY, int kisteZ, UUID besitzer, int menge,
                         long kaufpreis, long verkaufpreis, Warenprobe ware,
                         Set<UUID> freigegeben) {

    /**
     * Die groesste Menge je Handel: eine volle Doppelkiste.
     *
     * <p>54 Plaetze mal 64 Stueck - dieselbe Rechnung wie ChestShops
     * {@code MAX_SHOP_AMOUNT: 3456}, dessen Config-Kommentar sie nennt ("Default 3456 is
     * a double chest of 64 stacks"). Mehr kann kein Handel bewegen, weil mehr nicht in
     * die Kiste passt.
     */
    public static final int GROESSTE_MENGE = 54 * 64;

    /**
     * Der hoechste Preis - dieselbe Obergrenze wie bei allen Geldzahlen des Projekts.
     *
     * <p>Die Zahl ist die Schranke gegen den Vertipper, nicht gegen den Reichen: Ein
     * Preis von einer Milliarde ist in einer Wirtschaft mit 5.000 Mark Tagesdeckel kein
     * Angebot, sondern ein verrutschtes Komma.
     */
    public static final long HOECHSTER_PREIS = Verkaufsregel.HOECHSTER_PREIS;

    public Kistenshop {
        if (schildWelt == null || schildWelt.isBlank()) {
            throw new IllegalArgumentException("Ein Kistenshop ohne Welt ist keiner -"
                    + " Schild und Kiste liegen immer in derselben");
        }
        schildWelt = schildWelt.trim();
        if (ware == null) {
            throw new IllegalArgumentException("Ein Kistenshop ohne Ware ist keiner");
        }
        if (freigegeben == null) {
            throw new IllegalArgumentException("Nichts ist keine Freigabeliste - eine"
                    + " LEERE Menge heisst \"niemand ausser dem Besitzer\", und genau"
                    + " die traegt jeder neue Shop");
        }
        // Eine EIGENE unveraenderliche Kopie, und das ist hier mehr als Ordnung: Diese
        // Menge wird in offenFuer bei jedem Trichtertakt und jeder Explosion gefragt.
        // Gaebe der Aufrufer eine Menge herein, die er danach weiter aendert, entschiede
        // ueber den Zutritt zu einer fremden Kiste ein Wert, den niemand mehr ueberblickt.
        freigegeben = Set.copyOf(freigegeben);
        if (besitzer == null && !freigegeben.isEmpty()) {
            throw new IllegalArgumentException("Ein BUNDSHOP hat " + freigegeben.size()
                    + " Freigaben - er gehoert aber niemandem, also kann ihn auch"
                    + " niemand freigeben. Wer an einen Bundshop darf, entscheidet das"
                    + " Recht freimarken.shop.bund.");
        }
        if (menge < 1 || menge > GROESSTE_MENGE) {
            throw new IllegalArgumentException("Eine Handelsmenge von " + menge
                    + " ist keine - erlaubt ist 1 bis " + GROESSTE_MENGE
                    + " (eine volle Doppelkiste). Mehr kann kein Handel bewegen, weil"
                    + " mehr nicht in die Kiste passt.");
        }
        pruefePreis("Der Kaufpreis", kaufpreis);
        pruefePreis("Der Verkaufspreis", verkaufpreis);
        // Ein Shop, der weder kauft noch verkauft, ist ein Schild an einer Kiste. Er
        // faellt hier auf und nicht beim ersten Klick, bei dem ein Kunde nichts
        // geschieht und nichts erfaehrt.
        if (kaufpreis == 0L && verkaufpreis == 0L) {
            throw new IllegalArgumentException("Ein Kistenshop ohne Kauf- UND ohne"
                    + " Verkaufspreis handelt nichts. Mindestens eine der zwei Zahlen"
                    + " muss ueber 0 liegen.");
        }
        // Die Schranke gegen die Gelddruckmaschine aus Versehen - siehe den Klassenkopf.
        // NUR wenn beide Preise gesetzt sind: Ein Shop, der bloss ankauft (Kaufpreis 0),
        // ist kein Rundlauf, sondern ein Ankaufstand.
        if (kaufpreis > 0L && verkaufpreis > kaufpreis) {
            throw new IllegalArgumentException("Der Verkaufspreis " + verkaufpreis
                    + " liegt ueber dem Kaufpreis " + kaufpreis + ". Dann kauft man im"
                    + " selben Shop billig und verkauft teuer zurueck - das ist eine"
                    + " Gelddruckmaschine aus Versehen. Erlaubt ist hoechstens "
                    + kaufpreis + ".");
        }
        // Die Kiste MUSS neben dem Schild liegen. Geprueft wird der Abstand und nicht die
        // Richtung: Lumia erlaubte an, ueber, unter und neben - also alle sechs Seiten -
        // und ausdruecklich nicht diagonal.
        if (!istNachbar(schildX, schildY, schildZ, kisteX, kisteY, kisteZ)) {
            throw new IllegalArgumentException("Das Schild bei " + schildX + "/" + schildY
                    + "/" + schildZ + " liegt nicht neben der Kiste bei " + kisteX + "/"
                    + kisteY + "/" + kisteZ + ". Erlaubt ist genau ein Block Abstand auf"
                    + " einer Achse - an, ueber, unter oder neben der Kiste, nicht"
                    + " diagonal.");
        }
    }

    /** Ob dieser Shop dem Bund gehoert - unbegrenzt in Bestand, Platz und Geld. */
    public boolean demBund() {
        return besitzer == null;
    }

    /** Ob dieser Shop diesem Spieler gehoert. Ein Bundshop gehoert niemandem. */
    public boolean gehoert(UUID wer) {
        return besitzer != null && besitzer.equals(wer);
    }

    /**
     * Ob dieser Shop fuer diesen Menschen offen ist - die EINE Zutrittsregel.
     *
     * <p><b>Drei Stufen, und jede ist die Antwort auf einen Fall, der ohne sie
     * schieflaeuft.</b> Das Teamrecht zuerst: Wer ueberall bauen darf, kommt auch an
     * den Shop eines Spielers, der nicht mehr spielt - ohne das stuende ein
     * verlassener Shop fuer immer in der Welt, denn niemand koennte ihn abbauen. Dann
     * der Bundshop, und zwar an {@link #demBund()} und NICHT am Besitzerfeld: Ein
     * Bundshop hat keinen Besitzer, {@link #gehoert} gibt dort fuer JEDEN false, und
     * eine Frage nach dem Besitz allein schloesse jeden Bundshop fuer alle zu.
     *
     * <p><b>Die Regel steht hier und nicht in der Spielschicht</b>, weil sie an DREI
     * Stellen gebraucht wird: beim Abbauen von Schild, Kiste und Traeger, beim
     * OEFFNEN der Kiste und beim Anlegen eines zweiten Schildes an derselben Kiste.
     * Drei Kopien derselben Rechtefrage laufen auseinander - und dann ist eine Kiste
     * gegen das eine geschuetzt und gegen das andere offen. Hier ist sie ausserdem
     * ohne laufenden Server pruefbar; die zwei Rechte kommen als Wahrheitswerte
     * herein, weil nur die Spielschicht sie erfragen kann.
     *
     * <p><b>Der VIERTE Fall ist die Freigabe</b> - der Schluessel, der als
     * {@code /shop freigeben <spieler>} geplant war (entschieden 08.11.2026, gebaut
     * 09.11.2026). Er steht hier und nirgends sonst; daran haengt, dass er nicht ein
     * Schloss offen laesst.
     *
     * <p><b>Er gilt NICHT fuer alle drei Wege</b>, und das ist die eine Stelle, an der
     * dieses Javadoc von seiner frueheren Fassung abweicht. Dort stand, ein vierter Fall
     * gelte "sofort fuer alle drei Wege" - gebaut wurde es anders, weil der dritte Weg
     * das Loch vom 08.11.2026 ist: Wer ein ZWEITES Schild an die Kiste haengen darf,
     * verkauft fremde Ware auf sein eigenes Konto, und der Besitzer sieht nur, dass seine
     * Kiste leerer wird. Die Stelle bleibt EINE; sie gibt nur je Weg eine eigene Antwort,
     * und welcher Weg fragt, sagt {@link Shopweg}.
     *
     * <p>Oeffnen und Abbauen sind dem Freigegebenen erlaubt: Das Oeffnen ist der ganze
     * Zweck (Plan 7.4: "dass ein Fremder einem Shop auch nichts schenken kann"), das
     * Abbauen deshalb, weil Preise zu aendern heute nur ueber Schild abbauen und neu
     * beschriften geht. Die WARE bleibt dabei in der Kiste - ein aufgeloester Shop kostet
     * den Laden, nicht den Bestand.
     *
     * @param wer       wer an den Shop will
     * @param weg       auf welchem Weg - die Freigabe traegt nicht jeden
     * @param teamrecht ob er {@code freimarken.bauen.ueberall} hat
     * @param bundrecht ob er {@code freimarken.shop.bund} hat
     */
    public boolean offenFuer(UUID wer, Shopweg weg, boolean teamrecht,
            boolean bundrecht) {
        if (weg == null) {
            throw new IllegalArgumentException("Ohne Weg gibt es keine Zutrittsfrage -"
                    + " die Freigabe traegt nicht jeden, und ein null hiesse, dass der"
                    + " Aufrufer nicht weiss, wonach er fragt");
        }
        if (teamrecht) {
            return true;
        }
        if (demBund()) {
            return bundrecht;
        }
        if (gehoert(wer)) {
            return true;
        }
        // DER VIERTE FALL. Das zweite Schild ist ausgenommen - siehe oben und
        // Shopweg.ZWEITES_SCHILD. Die Reihenfolge ist Absicht: erst der Weg, dann die
        // Liste. So kostet der dritte Weg gar keinen Nachschlag.
        return weg != Shopweg.ZWEITES_SCHILD && freigegeben.contains(wer);
    }

    /**
     * Ob dieser Spieler fuer diesen Shop freigegeben ist.
     *
     * <p>Die rohe Frage nach der Liste, ohne Besitz und ohne Rechte - fuer den Befehl,
     * der eine Freigabe anzeigt oder zuruecknimmt. Die ENTSCHEIDUNG, ob jemand an den
     * Shop darf, trifft immer {@link #offenFuer}: Ein Besitzer steht nicht in seiner
     * eigenen Freigabeliste, und ein Bundshop hat gar keine.
     */
    public boolean istFreigegeben(UUID wer) {
        return wer != null && freigegeben.contains(wer);
    }

    /**
     * Der erste dieser Shops, der fuer diesen Menschen NICHT offen ist - oder
     * {@code null}.
     *
     * <p><b>ALLE muessen offen sein, nicht einer.</b> An einer Doppelkiste koennen
     * zwei Schilder zweier Menschen haengen, und die beiden Haelften teilen EIN
     * Inventar: Wer seine eigene Haelfte abbaut oder oeffnet, kommt an die Ware des
     * anderen.
     *
     * <p>Zurueck kommt der Shop und kein {@code boolean}, damit die Absage den nennen
     * kann, dem der Spieler in die Quere kommt. Eine LEERE Liste gibt {@code null},
     * also "offen" - die Unterscheidung zwischen "hier ist gar keine Shopkiste" und
     * "hier ist eine fremde" trifft der Aufrufer, denn nur er weiss, ob er
     * ueberhaupt an einer Kiste steht.
     */
    public static Kistenshop ersterFremder(List<Kistenshop> shops, UUID wer,
            Shopweg weg, boolean teamrecht, boolean bundrecht) {
        for (Kistenshop shop : shops) {
            if (!shop.offenFuer(wer, weg, teamrecht, bundrecht)) {
                return shop;
            }
        }
        return null;
    }

    /**
     * Derselbe Shop mit einer anderen Freigabeliste.
     *
     * <p>Ein record ist unveraenderlich, und das soll er bleiben: Diese Zeile ist die
     * Wahrheit ueber einen Shop, und eine Zeile, die sich unter der Hand aendert, waere
     * genau an der Stelle unzuverlaessig, an der ueber fremde Ware entschieden wird.
     * Wer freigibt, baut also einen neuen Shop und schreibt ihn.
     *
     * <p>Alle zwoelf uebrigen Angaben wandern unveraendert mit - auch der Besitzer.
     * Freigeben aendert nie, wem der Laden gehoert.
     */
    public Kistenshop mitFreigaben(Set<UUID> neue) {
        return new Kistenshop(schildWelt, schildX, schildY, schildZ, kisteX, kisteY,
                kisteZ, besitzer, menge, kaufpreis, verkaufpreis, ware, neue);
    }

    /** Ob hier gekauft werden kann - also ob der Shop verkauft. */
    public boolean verkauftWare() {
        return kaufpreis > 0L;
    }

    /** Ob hier verkauft werden kann - also ob der Shop ankauft. */
    public boolean kauftWare() {
        return verkaufpreis > 0L;
    }

    /**
     * Der Schluessel dieses Shops: die Schildstelle als Text.
     *
     * <p>Das SCHILD und nicht die Kiste, und das ist eine Entscheidung: An einer
     * Doppelkiste koennen zwei Schilder haengen, die zwei verschiedene Waren handeln -
     * eines auf jeder Haelfte. Mit der Kiste als Schluessel waere das unmoeglich, und
     * ein Laden mit einer Ware je Kiste braeuchte doppelt so viel Platz.
     */
    public String schluessel() {
        return schluesselVon(schildWelt, schildX, schildY, schildZ);
    }

    /**
     * Die Ordnung jeder Shopliste: Welt, dann x, dann y, dann z - und zwar als ZAHL.
     *
     * <p>Sie steht hier und nicht in den Ablagen, weil es sonst drei Fassungen davon
     * gaebe. Gemessen am 10.11.2026 war es genau so: {@link #schluessel()} ist TEXT, und
     * beide Speicher-Ablagen gaben ihre Listen ueber eine {@code TreeMap} darueber
     * heraus. Lexikographisch steht dann {@code 100} vor {@code 9} und {@code -20} vor
     * {@code 8} - waehrend {@code MariaShops} in allen drei Listenabfragen numerisch
     * ordnet. Dieselbe Frage gab also je nach Ablage eine andere Reihenfolge, und die
     * Zusage in {@link Shopablage#vonSpieler} beschrieb nur die eine Haelfte.
     *
     * <p>Numerisch und nicht als Text, weil ein Spieler seine Laeden an den Koordinaten
     * wiederfindet: Eine Liste, in der der Shop bei x=100 vor dem bei x=9 steht, liest
     * sich wie Unordnung - und bei einer Liste auf Seiten entscheidet die Ordnung auch
     * darueber, WAS auf Seite eins landet.
     *
     * <p>Die Welt bleibt Text; dort ist die Buchstabenfolge die einzige Ordnung, die es
     * gibt.
     */
    public static final Comparator<Kistenshop> NACH_STELLE =
            Comparator.comparing(Kistenshop::schildWelt)
                    .thenComparingInt(Kistenshop::schildX)
                    .thenComparingInt(Kistenshop::schildY)
                    .thenComparingInt(Kistenshop::schildZ);

    /** Derselbe Schluessel, ohne einen Shop in der Hand zu haben. */
    public static String schluesselVon(String welt, int x, int y, int z) {
        if (welt == null || welt.isBlank()) {
            throw new IllegalArgumentException("Ohne Welt gibt es keine Stelle");
        }
        return welt.trim() + " " + x + " " + y + " " + z;
    }

    /**
     * Ob zwei Stellen genau einen Block voneinander entfernt sind - nicht diagonal.
     *
     * <p>Die Summe der drei Abstaende ist genau 1. Das trifft die sechs Nachbarn und
     * schliesst die Diagonalen aus, ohne sie einzeln aufzuzaehlen - und es schliesst auch
     * aus, dass Schild und Kiste dieselbe Stelle sind.
     */
    public static boolean istNachbar(int ax, int ay, int az, int bx, int by, int bz) {
        return Math.abs(ax - bx) + Math.abs(ay - by) + Math.abs(az - bz) == 1;
    }

    private static void pruefePreis(String was, long preis) {
        if (preis < 0L || preis > HOECHSTER_PREIS) {
            throw new IllegalArgumentException(was + " ist " + preis + " und liegt nicht"
                    + " zwischen 0 und " + HOECHSTER_PREIS + ". Eine 0 heisst: in dieser"
                    + " Richtung wird nicht gehandelt.");
        }
    }
}
