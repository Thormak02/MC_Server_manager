package de.freimarken.paper;

import de.freimarken.kern.Besitzbuch;
import de.freimarken.kern.Buchungsgrund;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Das eine Verzeichnis aller Saetze - die Entsprechung zu {@code Wirtschaftsliste.SCHLUESSEL}.
 *
 * <p>Acht Bereichsaufzaehlungen, EINE Liste. Die Liste der Bereiche steht einmal und
 * wird zweimal gebraucht: fuer {@link #alle()} und fuer die Probe, die nachsieht, ob ein
 * Bauschritt seinen Bereich auch angemeldet hat. Gab es sie zweimal, koennte ein Bereich in
 * der einen stehen und in der anderen fehlen.
 *
 * <p>Die Familienfunktionen ({@link #grund}) stehen hier und nicht am Sendeort. Das ist
 * derselbe Grund wie bei {@code Wirtschaftsstand.NUR_BEIM_START}: Die Zuordnung gehoert
 * dorthin, wo sie entschieden wird, nicht in jeden Zweig, der sie braucht.
 */
final class Sprachschluessel {

    /**
     * Die acht Bereiche, in der Reihenfolge der Bauschritte.
     *
     * <p>Als {@code Class} und nicht als {@code values()}: So braucht ein noch leerer
     * Bereich keinen Platzhalterschluessel, und die Probe kann trotzdem nachsehen, dass
     * alle acht gefuehrt werden.
     *
     * <p><b>{@link Berufsname} steht hier NICHT</b>, und das ist der Unterschied zwischen
     * einem Bereich und einer offenen Familie: Diese Liste verlangt, dass jeder Satz in
     * der ausgelieferten Vorlage steht. Welche Berufe es gibt, entscheidet aber die
     * {@code config.yml}, und einen Beruf, den der Betreiber morgen eintraegt, kann die
     * Vorlage nicht kennen. Darum wird er ueber {@link #zuPfad} erkannt und nicht
     * aufgezaehlt - siehe dort.
     */
    private static final List<Class<? extends Satz>> BEREICHE = List.of(
            BundSatz.class, MarkSatz.class, ParzellenSatz.class, AnfragenSatz.class,
            SpielzeitSatz.class, ReiseSatz.class, WacheSatz.class, BerufSatz.class);

    /** Alle Saetze, aus allen acht Bereichen - einmal gebaut, unveraenderlich. */
    private static final List<Satz> ALLE = sammle();

    /** Jeder Pfad der Datei auf seinen Satz - auch {@code .eins} und {@code .viele}. */
    private static final Map<String, Satz> NACH_PFAD = verzeichne();

    private Sprachschluessel() {
    }

    private static List<Satz> sammle() {
        List<Satz> alle = new ArrayList<>();
        for (Class<? extends Satz> bereich : BEREICHE) {
            Satz[] saetze = bereich.getEnumConstants();
            if (saetze == null) {
                throw new IllegalStateException(bereich.getName()
                        + " ist keine Aufzaehlung - ein Bereich muss values() haben,"
                        + " damit man keinen Satz vergessen kann.");
            }
            alle.addAll(List.of(saetze));
        }
        return List.copyOf(alle);
    }

    private static Map<String, Satz> verzeichne() {
        Map<String, Satz> nachPfad = new LinkedHashMap<>();
        for (Satz satz : ALLE) {
            for (String pfad : satz.pfade()) {
                Satz vorher = nachPfad.put(pfad, satz);
                if (vorher != null) {
                    throw new IllegalStateException(pfad + " steht zweimal im Verzeichnis:"
                            + " " + vorher + " und " + satz + ". Zwei Saetze mit demselben"
                            + " Pfad wuerden einander stumm ueberschreiben.");
                }
            }
            if (satz.art() == Satz.Art.GEZAEHLT) {
                // Der Grundpfad selbst ist kein Blatt, aber zuPfad soll ihn kennen - sonst
                // meldete der Leser den Elternknoten als ueberzaehlig.
                nachPfad.putIfAbsent(satz.pfad(), satz);
            }
        }
        return Map.copyOf(nachPfad);
    }

    /** Alle Saetze, aus allen acht Bereichen - die EINE Liste. */
    static List<Satz> alle() {
        return ALLE;
    }

    /** Die acht Bereiche - fuer die Probe, die ihre Vollstaendigkeit zusichert. */
    static List<Class<? extends Satz>> bereiche() {
        return BEREICHE;
    }

    /** Der Satz zu einem Pfad aus der Datei, oder {@code null} fuer einen unbekannten. */
    static Satz zuPfad(String pfad) {
        return ausVerzeichnis(NACH_PFAD, pfad);
    }

    /**
     * Derselbe Nachschlag in einem eigenen Verzeichnis - die EINE Stelle fuer die Regel.
     *
     * <p>Zwei Leser brauchen ihn: der fuer die ausgelieferte Vorlage (ueber
     * {@link #zuPfad}) und der fuer die Datei des Betreibers (ueber ein Verzeichnis aus
     * einer eigenen Satzliste). Stuende die Familienregel nur in einem von beiden, waere
     * ein Berufsname in der einen Datei ein Satz und in der anderen eine Zeile, die zu
     * keinem Satz gehoert - und der Betreiber koennte seinem Beruf kein Wort geben.
     *
     * <p>Die Familie wird NACH dem Verzeichnis gefragt: Ein Pfad, den eine Aufzaehlung
     * fuehrt, gewinnt immer. So kann eine spaetere Konstante {@code beruf.name.etwas}
     * nicht stumm von der Familie verdeckt werden.
     */
    static Satz ausVerzeichnis(Map<String, Satz> verzeichnis, String pfad) {
        Satz satz = verzeichnis.get(pfad);
        if (satz != null) {
            return satz;
        }
        return Berufsname.passt(pfad) ? Berufsname.ausPfad(pfad) : null;
    }

    /** Alle Pfade, die in der Datei stehen duerfen - mit {@code .eins}/{@code .viele}. */
    static List<String> allePfade() {
        List<String> pfade = new ArrayList<>();
        for (Satz satz : ALLE) {
            pfade.addAll(satz.pfade());
        }
        return List.copyOf(pfade);
    }

    /**
     * Statt des {@code switch} in {@code Verlaufszeile:73-79} - das Wort zu einem
     * Buchungsgrund.
     *
     * <p>Der Pfad wird aus dem Aufzaehlungsnamen gebildet ({@link MarkSatz#grundpfad}),
     * nicht aus {@code Buchungsgrund.schluessel()}. Wirft, wenn dazu kein Satz steht -
     * ein fehlender Satz ist ein Bauunfall und kein Betriebsfall, und
     * {@code SendewegTest.grundIstVollstaendig} faengt ihn beim Bauen.
     */
    static Satz grund(Buchungsgrund grund) {
        String pfad = MarkSatz.grundpfad(grund);
        Satz satz = zuPfad(pfad);
        if (satz == null) {
            throw new IllegalStateException("Zu " + grund + " gehoert der Satz " + pfad
                    + ", und den gibt es nicht. Entweder fehlt er in MarkSatz, oder ein"
                    + " neuer Buchungsgrund wurde ohne Wort angelegt.");
        }
        return satz;
    }

    /**
     * Statt des {@code switch} in {@code ParzellenBefehle.absageZeilen} - der Satz zu
     * einem Bescheid des Besitzbuchs.
     *
     * <p><b>{@code NOCH_FREI} hat ZWEI Saetze</b>, gewaehlt an {@code amLimit}: "nimm sie
     * mit /parzelle nehmen" ist eine Einladung, und wer sein Limit schon erreicht hat,
     * soll sie nicht lesen. Das ist der Grund fuer den zweiten Parameter - und der Grund,
     * warum eine Probe ueber {@code values()} allein nicht genuegt: Sie wuerde nur zaehlen,
     * dass jeder Bescheid einen Satz hat, nicht dass er den richtigen von zwei bekommt.
     *
     * <p>Die Zuordnung steht hier und nicht am Sendeort, aus demselben Grund wie
     * {@link #grund}: Sie wird hier entschieden, und eine Probe kann sie unmittelbar
     * aufrufen. Der {@code switch} nennt jeden Bescheid, ohne {@code default} - ein neuer
     * Bescheid bricht damit den Bau und nicht erst das Spielbild.
     */
    static Satz bescheid(Besitzbuch.Bescheid bescheid, boolean amLimit) {
        return switch (bescheid) {
            case ERTEILT -> ParzellenSatz.BESCHEID_ERTEILT;
            case KEINE_PARZELLE -> ParzellenSatz.BESCHEID_KEINE_PARZELLE;
            case NOCH_FREI -> amLimit
                    ? ParzellenSatz.BESCHEID_NOCH_FREI_AM_LIMIT
                    : ParzellenSatz.BESCHEID_NOCH_FREI;
            case SCHON_VERGEBEN -> ParzellenSatz.BESCHEID_SCHON_VERGEBEN;
            case AUSSERHALB -> ParzellenSatz.BESCHEID_AUSSERHALB;
            case GRENZE_ERREICHT -> ParzellenSatz.BESCHEID_GRENZE_ERREICHT;
            case ZU_WENIG_MARK -> ParzellenSatz.BESCHEID_ZU_WENIG_MARK;
            case NICHT_EIGENTUEMER -> ParzellenSatz.BESCHEID_NICHT_EIGENTUEMER;
            case SCHON_EINGETRAGEN -> ParzellenSatz.BESCHEID_SCHON_EINGETRAGEN;
            case NICHT_EINGETRAGEN -> ParzellenSatz.BESCHEID_NICHT_EINGETRAGEN;
            case TEIL_EINER_GRUPPE -> ParzellenSatz.BESCHEID_TEIL_EINER_GRUPPE;
            case NACHBAR_FREI -> ParzellenSatz.BESCHEID_NACHBAR_FREI;
            case NACHBAR_FREMD -> ParzellenSatz.BESCHEID_NACHBAR_FREMD;
            case SCHON_IN_GRUPPE -> ParzellenSatz.BESCHEID_SCHON_IN_GRUPPE;
            case SCHON_ZUSAMMEN -> ParzellenSatz.BESCHEID_SCHON_ZUSAMMEN;
            case GRUPPE_ZU_GROSS -> ParzellenSatz.BESCHEID_GRUPPE_ZU_GROSS;
            case KEINE_GRUPPE -> ParzellenSatz.BESCHEID_KEINE_GRUPPE;
            case AUF_DEM_WEG -> ParzellenSatz.BESCHEID_AUF_DEM_WEG;
            case HAELT_ZUSAMMEN -> ParzellenSatz.BESCHEID_HAELT_ZUSAMMEN;
        };
    }

    /** Woher die Zahl kommt, die ein Bescheid in seinen Satz setzt - oder NICHTS. */
    enum Zahlquelle {
        KEINE,
        /** Wie viele Parzellen der Rang erlaubt - {@code hoechstzahl(spieler)}. */
        HOECHSTZAHL,
        /** Wie viele Felder eine Gruppe haben darf - {@code gruppenFelder(spieler)}. */
        GRUPPENFELDER
    }

    /**
     * Der zweite Teil derselben Antwort, damit {@code absageZeilen} nicht raten muss.
     *
     * <p>Gemessen setzen DREI der neunzehn Bescheide eine Zahl in den Satz, und zwar aus
     * ZWEI verschiedenen Quellen: {@code NOCH_FREI} am Limit und {@code GRENZE_ERREICHT}
     * nennen die Hoechstzahl der Parzellen, {@code GRUPPE_ZU_GROSS} die erlaubte
     * Gruppengroesse. Welche Zahl in welchen Satz geht, ist damit eine Angabe des
     * Verzeichnisses und keine Entscheidung am Sendeort - dort stuende sonst ein zweiter
     * {@code switch} ueber dieselbe Verzweigung, und zwei Verzweigungen ueber dieselbe
     * Frage laufen irgendwann auseinander.
     *
     * <p>Dass beide Quellen wirklich auseinandergehalten werden, haelt
     * {@code SendewegTest} fest: Seine Tafeln rufen {@code absageZeilen} mit
     * ABSICHTLICH verschiedenen Zahlen auf (7 Parzellen, 3 Felder). Mit zwei gleichen
     * Zahlen waere ein Vertauschen der Quellen gruen.
     */
    static Zahlquelle zahlquelle(Besitzbuch.Bescheid bescheid, boolean amLimit) {
        return switch (bescheid) {
            case NOCH_FREI -> amLimit ? Zahlquelle.HOECHSTZAHL : Zahlquelle.KEINE;
            case GRENZE_ERREICHT -> Zahlquelle.HOECHSTZAHL;
            case GRUPPE_ZU_GROSS -> Zahlquelle.GRUPPENFELDER;
            case ERTEILT, KEINE_PARZELLE, SCHON_VERGEBEN, AUSSERHALB, ZU_WENIG_MARK,
                    NICHT_EIGENTUEMER, SCHON_EINGETRAGEN, NICHT_EINGETRAGEN,
                    TEIL_EINER_GRUPPE, NACHBAR_FREI, NACHBAR_FREMD, SCHON_IN_GRUPPE,
                    SCHON_ZUSAMMEN, KEINE_GRUPPE, AUF_DEM_WEG, HAELT_ZUSAMMEN
                    -> Zahlquelle.KEINE;
        };
    }

    /**
     * Der aehnlichste bekannte Pfad zu einem unbekannten - fuer "meintest du ...?".
     *
     * <p>Die Levenshtein-Abstandsrechnung, und zwar auf dem GANZEN Pfad: Ein Tippfehler
     * steckt genauso oft in der Ebene wie im letzten Wort. Es gibt immer eine Antwort,
     * auch eine schlechte - sie steht in einem Bericht und nicht in einer Klage, also
     * kostet eine schlechte Antwort nichts.
     *
     * @return {@code null}, wenn es ueberhaupt keinen Pfad gibt
     */
    static String aehnlichster(String unbekannt) {
        String besser = null;
        int abstand = Integer.MAX_VALUE;
        for (String pfad : allePfade()) {
            int jetzt = abstand(unbekannt, pfad);
            if (jetzt < abstand) {
                abstand = jetzt;
                besser = pfad;
            }
        }
        return besser;
    }

    /** Levenshtein, mit zwei Zeilen statt einer Tabelle. */
    private static int abstand(String a, String b) {
        int[] vorher = new int[b.length() + 1];
        int[] jetzt = new int[b.length() + 1];
        for (int j = 0; j <= b.length(); j++) {
            vorher[j] = j;
        }
        for (int i = 1; i <= a.length(); i++) {
            jetzt[0] = i;
            for (int j = 1; j <= b.length(); j++) {
                int kosten = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
                jetzt[j] = Math.min(Math.min(jetzt[j - 1] + 1, vorher[j] + 1),
                        vorher[j - 1] + kosten);
            }
            int[] tausch = vorher;
            vorher = jetzt;
            jetzt = tausch;
        }
        return vorher[b.length()];
    }

    /**
     * Die Pfade, deren Deckung eine FAMILIENPROBE traegt und keine Textsuche.
     *
     * <p>Diese Saetze werden nirgends namentlich getippt: Sie werden ueber eine
     * Familienfunktion gewaehlt ({@link #grund}, {@link #bescheid}), und die steht in
     * dieser Datei. Eine Textsuche nach dem Namen der Konstante fand sie darum nur in
     * {@code Sprachschluessel.java} selbst - und ein Schluessel, der sich selbst
     * bescheinigt, bescheinigt nichts.
     *
     * <p>Die Liste wird MECHANISCH aus denselben Aufzaehlungen gebaut, nach denen die
     * Familienfunktionen verzweigen - nicht von Hand aufgeschrieben. Sonst stuende hier
     * eines Tages ein Pfad, den es nicht mehr gibt, und die Probe waere rot, ohne dass
     * etwas kaputt ist.
     *
     * <p>{@code GrenzeTest.jederSchluesselWirdGesendet} vergleicht diese Menge mit
     * {@code assertEquals} gegen die tatsaechlich nur hier gefundenen Schluessel. Wird ein
     * weiterer Satz selbstbescheinigend, ist die Probe rot; bekommt einer von diesen eine
     * Nennung draussen, ebenfalls. Was sie wirklich deckt, ist
     * {@code SendewegTest} - dort wird jeder Buchungsgrund durch die echte Zeile geschickt.
     */
    static List<String> familienDeckung() {
        List<String> pfade = new ArrayList<>();
        for (Buchungsgrund grund : Buchungsgrund.values()) {
            pfade.add(MarkSatz.grundpfad(grund));
        }
        // Beide Formen von amLimit, weil NOCH_FREI zwei verschiedene Saetze hat - mit
        // nur einem Durchlauf fehlte genau der Satz, den es am meisten braucht.
        for (Besitzbuch.Bescheid bescheid : Besitzbuch.Bescheid.values()) {
            pfade.add(bescheid(bescheid, false).pfad());
            pfade.add(bescheid(bescheid, true).pfad());
        }
        return List.copyOf(pfade);
    }

    /** Nur fuer die Proben: ein Verzeichnis aus einer eigenen Satzliste. */
    static Map<String, Satz> verzeichnisVon(List<Satz> saetze) {
        Map<String, Satz> nachPfad = new HashMap<>();
        for (Satz satz : saetze) {
            for (String pfad : satz.pfade()) {
                nachPfad.put(pfad, satz);
            }
            if (satz.art() == Satz.Art.GEZAEHLT) {
                nachPfad.putIfAbsent(satz.pfad(), satz);
            }
        }
        return nachPfad;
    }
}
