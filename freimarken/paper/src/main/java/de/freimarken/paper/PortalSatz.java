package de.freimarken.paper;

import java.util.Set;

/**
 * Die Saetze von {@code /portal} - dem Werkzeug, mit dem ein Tor entsteht.
 *
 * <p><b>Der wichtigste Satz ist {@link #SETZEN_ABGELEHNT}</b>, und er traegt den
 * GRUND als Wort: {@code Torbuch.pruefe} klagt in vier Faellen, und jede Klage nennt
 * Zahlen - welche Welt es nicht gibt, welche zwei Tore sich Bloecke teilen, wo die
 * Grenze liegt. Ein Satz, der nur „geht nicht" sagt, liesse den Betreiber raten und
 * dann so lange klicken, bis zufaellig eines sitzt. Die Klage kommt aus dem Programm
 * und nicht aus dieser Datei - sie nennt gemessene Zahlen, und die kann kein
 * Betreiber vorformulieren.
 *
 * <p><b>Warum zwei Ecken und nicht ein Block</b>: Ein Tor ist drei hoch und drei breit.
 * Aus einem Block waere der Rest geraten, und ein geratenes Tor steht halb in der Wand.
 * Darum merkt {@code /portal ecke} zwei Ecken, und beide Saetze dazu nennen die Zahlen,
 * die gemerkt wurden - wer sich verlaufen hat, sieht es, bevor er setzt.
 */
enum PortalSatz implements Satz {

    // --- Die Ecken -------------------------------------------------------------

    /**
     * Die erste Ecke ist gemerkt.
     *
     * <p>Mit den Zahlen, und zwar nackt: Wer sie liest, kann sie mit dem vergleichen,
     * was die F3-Anzeige sagt. Ein „gemerkt" ohne Zahlen waere eine Zusage, die man
     * erst beim Setzen prueft.
     */
    ERSTE_ECKE("portal.ecke.erste", Art.SATZ, "x", "y", "z"),

    /** Die zweite Ecke ist gemerkt - der Quader steht. */
    ZWEITE_ECKE("portal.ecke.zweite", Art.SATZ, "von", "bis", "bloecke"),

    /**
     * Er steht nicht in der Ankunftswelt.
     *
     * <p>Ein eigener Riegel und keine Klage beim Setzen: Die Tore stehen ALLE in einer
     * Welt, und wer in Kornmark eine Ecke merkt, hat sich verlaufen - das soll er beim
     * ersten Klick erfahren und nicht nach dem zweiten.
     */
    ECKE_FALSCHE_WELT("portal.ecke.falsche-welt", Art.SATZ, "welt", "ankunftswelt"),

    // --- Setzen ----------------------------------------------------------------

    /** Es sind nicht zwei Ecken gemerkt. */
    OHNE_ECKEN("portal.setzen.ohne-ecken", Art.SATZ),

    /**
     * Die Welt steht nicht in {@code welten:}.
     *
     * <p>Schon hier abgewiesen, obwohl {@code Torbuch.pruefe} es auch faengt: Die
     * Vervollstaendigung bietet nur bekannte Welten an, und wer trotzdem etwas anderes
     * tippt, hat sich vertippt. Dafuer ist ein kurzer Satz die bessere Antwort als eine
     * Klage mit Zahlen.
     */
    UNBEKANNTE_WELT("portal.setzen.unbekannte-welt", Art.SATZ, "welt"),

    /** Die Pruefung hat etwas gefunden - {@code {grund}} ist die Klage aus dem Programm. */
    ABGELEHNT("portal.setzen.abgelehnt", Art.SATZ, "grund"),

    /** Gesetzt und geschrieben. */
    GESETZT("portal.setzen.gesetzt", Art.SATZ, "welt", "von", "bis"),

    /**
     * Die Datei liess sich nicht schreiben.
     *
     * <p>Der Satz, den man beim Bauen am wenigsten erwartet und am dringendsten braucht:
     * Ohne ihn waere ein Tor gesetzt, das nach dem naechsten Start weg ist. Darum nennt
     * er den Dateinamen - der Betreiber muss die Rechte des Ordners ansehen.
     */
    NICHT_GESCHRIEBEN("portal.setzen.nicht-geschrieben", Art.SATZ, "datei"),

    // --- Liste und Entfernen ---------------------------------------------------

    /** Der Kopf der Liste - {@code {anzahl}} ZAEHLT. */
    LISTE_KOPF("portal.liste.kopf", Art.GEZAEHLT, "anzahl"),

    /** Eine Zeile der Liste. */
    LISTE_ZEILE("portal.liste.zeile", Art.SATZ, "welt", "von", "bis", "wo"),

    /** Es steht kein Tor. */
    LISTE_LEER("portal.liste.leer", Art.SATZ),

    /** Entfernt und geschrieben. */
    ENTFERNT("portal.entfernen.entfernt", Art.SATZ, "welt"),

    /** In diese Welt fuehrt gar kein Tor. */
    KEIN_TOR("portal.entfernen.kein-tor", Art.SATZ, "welt"),

    // --- Neuladen --------------------------------------------------------------

    /**
     * Die Datei ist neu gelesen - {@code {anzahl}} ZAEHLT die Tore.
     *
     * <p>Warum es dieses Neuladen gibt: Die Datei laesst sich von Hand aendern, und ein
     * Betreiber, der dafuer den Server stoppen muss, aendert sie lieber nicht. Die
     * Torwache fragt das Buch bei jedem Schritt - ein neues Lesen wirkt also sofort.
     */
    NEU_GELESEN("portal.neuladen.gelesen", Art.GEZAEHLT, "anzahl"),

    /** Beim Neuladen gab es Klagen - sie stehen im Serverprotokoll. */
    NEULADEN_KLAGT("portal.neuladen.klagt", Art.GEZAEHLT, "anzahl"),

    // --- Wer tippt -------------------------------------------------------------

    /**
     * Von der Konsole geht es nicht.
     *
     * <p>{@code /portal ecke} braucht einen Ort, und die Konsole steht nirgends. Die
     * anderen Zweige koennten von dort laufen; sie tun es trotzdem nicht, denn ein Tor
     * setzt man, wo man es sieht.
     */
    NUR_SPIELER("portal.nur-spieler", Art.SATZ);

    private final String pfad;
    private final Art art;
    private final Set<String> platzhalter;

    PortalSatz(String pfad, Art art, String... platzhalter) {
        this.pfad = pfad;
        this.art = art;
        this.platzhalter = Set.of(platzhalter);
    }

    @Override
    public String pfad() {
        return pfad;
    }

    @Override
    public Art art() {
        return art;
    }

    @Override
    public Set<String> platzhalter() {
        return platzhalter;
    }

    /**
     * KEIN Satz dieses Bereichs darf leer sein.
     *
     * <p>Jeder von ihnen ist die ganze Antwort auf einen getippten Befehl. Eine
     * leere Zeile hiesse hier: Der Betreiber tippt {@code /portal setzen}, und es
     * geschieht nichts Sichtbares - weder Zusage noch Absage. Dann setzt er noch
     * einmal.
     */
    @Override
    public boolean darfLeerSein() {
        return false;
    }
}
