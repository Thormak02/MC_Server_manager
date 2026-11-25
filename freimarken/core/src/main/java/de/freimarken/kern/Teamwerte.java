package de.freimarken.kern;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

/**
 * Die Teamrollen und was jede darf (Plan 12c.2, Schritt 16a).
 *
 * <p><b>Eine LISTE aus der Datei und keine Aufzaehlung im Programm</b> - dieselbe
 * Entscheidung wie bei {@link Rangwerte} und {@link Berufsverzeichnis}. Der Plan nennt
 * fuenf Rollen (Helfer, Moderator, Verwalter, Eventleiter, Baumeister), aber welche ein
 * Server braucht, weiss der Betreiber: Ein kleiner Server hat einen Moderator und keinen
 * Eventleiter, ein grosser braucht eine sechste Rolle fuer seine Bauabteilung. Ein
 * {@code enum} waere eine Rolle, die niemand anlegen kann, ohne das Plugin neu zu bauen.
 *
 * <p><b>Was eine Rolle von einem Rang unterscheidet:</b> Ein Rang wird ERREICHT, eine
 * Rolle wird GEGEBEN. Darum hat eine Rolle keine Bedingungen und keine Zahlen - nur
 * Rechte. Und darum ist die Reihenfolge in der Datei hier gleichgueltig: Es gibt kein
 * oben und unten, keine Leiter und keinen Aufstieg. Helfer, Moderator und Verwalter sehen
 * in der Vorgabe wie eine Leiter aus, aber das ist eine Entscheidung des Betreibers ueber
 * seine Rechtelisten und keine Regel des Programms.
 *
 * <p><b>Ein Spieler kann mehrere Rollen haben</b>, und seine Rechte sind die VEREINIGUNG
 * ({@link #rechteFuer}). Der Plan sagt zu Strafen und Bauen "niemand braucht beides" -
 * das ist ein Satz ueber Personalpolitik und keiner ueber Technik. Wer seinen Baumeister
 * zusaetzlich zum Eventleiter macht, soll das tun koennen, ohne eine sechste Rolle
 * anzulegen, die beide Listen abschreibt.
 *
 * <p><b>Kein {@code an}-Schalter</b>, anders als bei {@link Rangwerte#an()}. Ein
 * ausgeschalteter Rang heisst "fuer jeden gelten die Zahlen der Datei" und ist ein
 * brauchbarer Zustand; ein ausgeschaltetes Team hiesse "allen Moderatoren sind die Rechte
 * entzogen", und das waere ein Schalter, dessen einziger Zweck ein Unfall ist. Der
 * Aus-Zustand ist eine leere Rollenliste, und die sagt dasselbe, ohne dass ein Schalter
 * daneben eine zweite Wahrheit haelt.
 *
 * @param rollen die Rollen in der Reihenfolge der Datei; darf leer sein - dann hat dieser
 *               Server kein Team, und das ist erlaubt
 */
public record Teamwerte(List<Teamwerte.Rolle> rollen) {

    /**
     * Eine Rolle und ihre Rechte.
     *
     * <p>Die Rechte stehen OHNE die Vorsilbe in der Datei, wie bei
     * {@link Rangwerte.Stufe#rechte()}: Die Vorsilbe gehoert dem Programm, und sie soll
     * nicht fuenfmal in der Datei abgeschrieben stehen, wo sie fuenfmal vertippt werden
     * kann. Angehaengt wird sie an EINER Stelle, in {@link #rechteFuer}.
     *
     * @param schluessel der Name der Rolle, kleingeschrieben wie in der Datei
     * @param rechte     was sie gibt, ohne Vorsilbe und ohne Doppelte
     */
    public record Rolle(String schluessel, List<String> rechte) {

        public Rolle {
            if (schluessel == null || schluessel.isBlank()) {
                throw new IllegalArgumentException("Eine Teamrolle ohne Namen");
            }
            String klein = schluessel.trim().toLowerCase(Locale.ROOT);
            if (!klein.equals(schluessel)) {
                throw new IllegalArgumentException("Der Name einer Teamrolle muss schon"
                        + " kleingeschrieben hereinkommen: \"" + schluessel + "\"."
                        + " Normalisiert der Leser nicht, schlaegt der Satz"
                        + " team.rolle.<name> spaeter woertlich fehl");
            }
            if (klein.length() > HOECHSTE_SCHLUESSELLAENGE) {
                throw new IllegalArgumentException("Der Name einer Teamrolle darf"
                        + " hoechstens " + HOECHSTE_SCHLUESSELLAENGE + " Zeichen haben: \""
                        + klein + "\"");
            }
            if (!klein.matches(MUSTER)) {
                throw new IllegalArgumentException("Der Name einer Teamrolle passt nicht"
                        + " auf " + MUSTER + ": \"" + klein + "\"");
            }
            if (rechte == null) {
                throw new IllegalArgumentException("Die Rechteliste einer Teamrolle darf"
                        + " leer sein, aber nicht fehlen: \"" + klein + "\"");
            }
            // LinkedHashSet: Doppelte fallen weg, die Reihenfolge der Datei bleibt. Sie
            // steht im Startbericht, und eine Liste, die bei jedem Start anders sortiert
            // ist, laesst sich zwischen zwei Starts nicht vergleichen.
            Set<String> geprueft = new LinkedHashSet<>();
            for (String recht : rechte) {
                geprueft.add(geprueftesRecht(recht, klein));
            }
            rechte = List.copyOf(geprueft);
        }

        /**
         * Ein Recht, wie es in der Datei stehen darf - oder eine Klage.
         *
         * <p><b>Die Vorsilbe ist hier VERBOTEN</b>, genau wie bei einer Rangstufe. Wer
         * {@code freimarken.blockbuch} schreibt, bekaeme {@code freimarken.freimarken.
         * blockbuch} - ein Recht, das niemand anmeldet und das darum nie wirkt. Still,
         * denn ein Anhaengsel setzt jeden Namen klaglos.
         */
        private static String geprueftesRecht(String recht, String rolle) {
            if (recht == null || recht.isBlank()) {
                throw new IllegalArgumentException("Eine leere Rechtezeile bei der"
                        + " Teamrolle \"" + rolle + "\"");
            }
            String klein = recht.trim().toLowerCase(Locale.ROOT);
            if (klein.startsWith(VORSILBE)) {
                throw new IllegalArgumentException("Das Recht \"" + recht + "\" bei der"
                        + " Teamrolle \"" + rolle + "\" traegt die Vorsilbe " + VORSILBE
                        + " schon. Sie kommt vom Programm - mit ihr in der Datei stuende"
                        + " am Ende " + VORSILBE + klein + ", und das meldet niemand an");
            }
            if (klein.length() > HOECHSTE_RECHTELAENGE) {
                throw new IllegalArgumentException("Das Recht \"" + klein + "\" bei der"
                        + " Teamrolle \"" + rolle + "\" ist laenger als "
                        + HOECHSTE_RECHTELAENGE + " Zeichen");
            }
            if (!klein.matches(RECHTEMUSTER)) {
                throw new IllegalArgumentException("Das Recht \"" + klein + "\" bei der"
                        + " Teamrolle \"" + rolle + "\" passt nicht auf " + RECHTEMUSTER);
            }
            return klein;
        }

        /** Ob diese Rolle ueberhaupt etwas gibt. */
        public boolean leerIst() {
            return rechte.isEmpty();
        }
    }

    /** Der Abschnitt in der {@code config.yml}. */
    public static final String PFAD_ROLLEN = "team.rollen";

    /** Die Tabelle unter einer Rolle. */
    public static final String SCHLUESSEL_RECHTE = "rechte";

    /**
     * Die Vorsilbe jedes Rechtes dieses Plugins.
     *
     * <p>Geholt aus {@link Rangwerte#VORSILBE} und nicht abgeschrieben: Es ist
     * dieselbe Vorsilbe, und zwei Abschriften derselben Zeichenfolge laufen eines Tages
     * auseinander - dann vergibt eine Rolle {@code freimarken.blockbuch} und ein Rang
     * {@code freimarken2.blockbuch}, und rot wird dabei nichts.
     */
    public static final String VORSILBE = Rangwerte.VORSILBE;

    /** Woraus ein Rollenname bestehen darf - dasselbe Muster wie bei einer Rangstufe. */
    public static final String MUSTER = Rangwerte.MUSTER;

    /** Woraus ein Rechtename bestehen darf. */
    public static final String RECHTEMUSTER = Rangwerte.RECHTEMUSTER;

    /** Wie lang ein Rollenname werden darf. */
    public static final int HOECHSTE_SCHLUESSELLAENGE = Rangwerte.HOECHSTE_SCHLUESSELLAENGE;

    /** Wie lang ein Rechtename werden darf. */
    public static final int HOECHSTE_RECHTELAENGE = Rangwerte.HOECHSTE_RECHTELAENGE;

    /**
     * Wie viele Rollen es hoechstens geben darf.
     *
     * <p>Eine Grenze, damit eine verrutschte Einrueckung in der Datei nicht zu
     * zweihundert Rollen wird, von denen jede eine Sprachzeile verlangt und ohne sie den
     * Start verweigert. Sechzehn ist reichlich: Der Plan nennt fuenf.
     */
    public static final int HOECHSTE_ROLLEN = 16;

    /**
     * Die fuenf Rollen aus Plan 12c.2 - die Vorgabe, wenn die Datei nichts sagt.
     *
     * <p><b>Jede nennt nur Rechte, die es HEUTE gibt.</b> Das ist keine Vollstaendigkeit,
     * sondern der einzige Weg, der nicht luegt: {@code Rangliste.unbekannteRechte} fragt
     * beim Start die Rechteverwaltung des Servers, und eine Vorgabe, die
     * {@code strafe.bann} vergibt, bevor es den Befehl gibt, stuende dort als Fehler -
     * zu Recht, denn sie wirkte nicht. Mit jedem weiteren Teil von Schritt 16a kommen die
     * Rechte dazu, die er anmeldet.
     *
     * <p><b>Die Trennung von Helfer und Eventleiter ist mit Teil C eingetreten</b>
     * [25.11.2026]. Bis dahin hatten beide dieselbe Liste, und der Grund stand hier: Was
     * einen Helfer ausmacht, sind die Meldungen, und die gab es noch nicht. Jetzt gibt es
     * sie - der Helfer hat {@code meldungen}, der Eventleiter nicht. Was einen
     * Eventleiter ausmacht, sind die Events (Plan 15); bis dahin ist er die Rolle, die
     * zu Spielern springen darf und sonst nichts.
     *
     * <p>Das ist genau der Weg, den der Satz hier vorhergesagt hat: Zwei Rollen, die
     * heute gleich viel duerfen, trennen sich von selbst, sobald ihre Werkzeuge stehen.
     * {@link #ungereimtheiten()} meldet darum kein Paar mehr - und wenn wieder eines
     * auftaucht, hat jemand eine Rechtezeile vergessen.
     */
    public static final List<Rolle> VORGABE_ROLLEN = List.of(
            // Zu Spielern springen und MELDUNGEN BEARBEITEN - das ist, was Plan 12c.2
            // dem Helfer zuschreibt: "Meldungen annehmen und weiterreichen, Fragen
            // beantworten, zu Spielern springen". Mit Teil C gibt es das Werkzeug dafuer.
            new Rolle("helfer", List.of("tpa", "sofort", "meldungen")),
            // Dazu das Blockbuch (wer war das) und die Spielzeit anderer. Das sind die
            // zwei Auskuenfte, die Plan 12c.3 ausdruecklich der Moderation zuordnet und
            // nicht der Verwaltung.
            // Dazu die STRAFEN aus Plan 12c.2: "zusaetzlich stummschalten, kicken,
            // zeitlich bannen". Das Wort ZEITLICH ist der Grund, aus dem
            // strafen.dauerhaft NICHT dabei ist - jenes gehoert dem Verwalter, und
            // die Grenze dazwischen steht als Zahl in der config.yml.
            //
            // strafen.aufheben ist dabei: Wer befristet sperren darf, soll einen
            // Irrtum auch zuruecknehmen koennen. Eine UNBEGRENZTE Strafe aufzuheben
            // braucht trotzdem strafen.dauerhaft - sonst nimmt ein Moderator den
            // dauerhaften Bann eines Verwalters zurueck.
            new Rolle("moderator", List.of("tpa", "sofort", "meldungen", "blockbuch",
                    "spielzeit.andere", "strafen.stumm", "strafen.rauswurf",
                    "strafen.bann", "strafen.aufheben")),
            // Dazu die drei Eingriffe aus Plan 12c.2: Besitz uebertragen, Freistaedte
            // aufloesen, die Welt nachsehen. NICHT freimarken.geld - Geldkorrekturen sind
            // nach Plan 12a Sache des Super-Admins im Dashboard und keine Moderation.
            // Dazu die drei Eingriffe aus Plan 12c.2: Besitz uebertragen, Freistaedte
            // aufloesen, die Welt nachsehen - und die zwei Strafrechte, die den
            // Verwalter vom Moderator trennen: DAUERHAFT bannen und jemanden strafen,
            // der selbst im TEAM ist. Beide sind nach Plan 12c.2 seine Sache allein.
            new Rolle("verwalter", List.of("tpa", "sofort", "meldungen", "blockbuch",
                    "spielzeit.andere", "verwalten", "besitz", "freistadt.raeumen",
                    "team", "team.vergeben", "strafen.stumm", "strafen.rauswurf",
                    "strafen.bann", "strafen.aufheben", "strafen.dauerhaft",
                    "strafen.team")),
            new Rolle("eventleiter", List.of("tpa", "sofort")),
            // Baurecht ueberall und sonst nichts - getrennte Rollen fuer Strafen und
            // Bauen sind nach Plan 12c.2 Absicht.
            new Rolle("baumeister", List.of("tpa", "sofort", "bauen.ueberall")));

    /** Die Vorgabe des Programms. */
    public static final Teamwerte VORGABE = new Teamwerte(VORGABE_ROLLEN);

    public Teamwerte {
        if (rollen == null) {
            throw new IllegalArgumentException("Die Rollenliste darf leer sein, aber"
                    + " nicht fehlen - eine leere heisst: dieser Server hat kein Team");
        }
        if (rollen.size() > HOECHSTE_ROLLEN) {
            throw new IllegalArgumentException("Hoechstens " + HOECHSTE_ROLLEN
                    + " Teamrollen, nicht " + rollen.size());
        }
        Set<String> gesehen = new LinkedHashSet<>();
        for (Rolle rolle : rollen) {
            if (rolle == null) {
                throw new IllegalArgumentException("Eine fehlende Teamrolle in der Liste");
            }
            if (!gesehen.add(rolle.schluessel())) {
                throw new IllegalArgumentException("Die Teamrolle \"" + rolle.schluessel()
                        + "\" steht zweimal in der Liste. Welche der beiden Rechtelisten"
                        + " gilt, entschiede die Reihenfolge - und die ist bei Rollen"
                        + " ausdruecklich bedeutungslos");
            }
        }
        rollen = List.copyOf(rollen);
    }

    /** Ob dieser Server ueberhaupt Rollen kennt. */
    public boolean greift() {
        return !rollen.isEmpty();
    }

    /** Die Rolle zu einem Namen, falls es sie gibt. */
    public Optional<Rolle> rolle(String schluessel) {
        if (schluessel == null) {
            return Optional.empty();
        }
        String klein = schluessel.trim().toLowerCase(Locale.ROOT);
        for (Rolle rolle : rollen) {
            if (rolle.schluessel().equals(klein)) {
                return Optional.of(rolle);
            }
        }
        return Optional.empty();
    }

    /** Ob es eine Rolle dieses Namens gibt. */
    public boolean kennt(String schluessel) {
        return rolle(schluessel).isPresent();
    }

    /** Die Namen aller Rollen, in der Reihenfolge der Datei. */
    public List<String> schluessel() {
        List<String> namen = new ArrayList<>(rollen.size());
        for (Rolle rolle : rollen) {
            namen.add(rolle.schluessel());
        }
        return List.copyOf(namen);
    }

    /**
     * Die Rechte dieser Rollen - MIT Vorsilbe, als EINE Menge.
     *
     * <p><b>Die Vereinigung und nicht die Liste der ersten Rolle</b>: Wer Moderator und
     * Baumeister ist, darf beides. Eine Rolle, die dieser Server nicht kennt, wird
     * stillschweigend uebersprungen - sie steht in der Ablage, weil der Betreiber ihre
     * Zeile aus der Datei genommen hat, und daraus darf kein Wurf beim Beitritt werden.
     * Gemeldet wird sie trotzdem, aber an anderer Stelle: in {@code Teamliste}, einmal
     * und nicht bei jedem Beitritt.
     *
     * <p><b>Hier wird die Vorsilbe angehaengt, und nur hier.</b> Das ist dieselbe Regel
     * wie bei {@code Rangzahl.knoten}: Ein Schreiber und ein Leser derselben Gestalt an
     * zwei Stellen laufen auseinander, und dann vergibt die Rolle ein Recht, nach dem
     * niemand fragt.
     *
     * @param namen die Rollen eines Spielers; {@code null} oder leer gibt eine leere Menge
     */
    public Set<String> rechteFuer(Iterable<String> namen) {
        if (namen == null) {
            return Set.of();
        }
        // LinkedHashSet: Die Reihenfolge ist die der Rollen und darin die der Datei.
        // Sie steht im Startbericht und in /team zeigen.
        Set<String> rechte = new LinkedHashSet<>();
        for (String name : namen) {
            rolle(name).ifPresent(rolle -> {
                for (String recht : rolle.rechte()) {
                    rechte.add(VORSILBE + recht);
                }
            });
        }
        return java.util.Collections.unmodifiableSet(rechte);
    }

    /**
     * Was an diesen Rollen auffaellt, ohne dass es ein Fehler waere.
     *
     * <p>Ein Bericht und keine Klage, genau wie {@link Rangwerte#ungereimtheiten()}: Alles
     * hier ist erlaubt und fast immer ein Vertipper. Im Spiel faellt keines davon auf -
     * eine Rolle ohne Rechte wird klaglos vergeben, und der Betroffene merkt nur, dass
     * sich nichts geaendert hat.
     */
    public List<String> ungereimtheiten() {
        List<String> saetze = new ArrayList<>();
        for (Rolle rolle : rollen) {
            if (rolle.leerIst()) {
                saetze.add(PFAD_ROLLEN + "." + rolle.schluessel() + ": Diese Rolle gibt"
                        + " kein einziges Recht. Vergeben laesst sie sich, aber wer sie"
                        + " bekommt, darf danach genau so viel wie vorher.");
            }
        }
        // Zwei Rollen mit genau derselben Rechteliste. Erlaubt und in der Vorgabe sogar
        // vorhanden (Helfer und Eventleiter, solange ihre Werkzeuge fehlen) - aber wer
        // sie NICHT absichtlich gleich gemacht hat, hat eine Zeile vergessen.
        for (int erste = 0; erste < rollen.size(); erste++) {
            for (int zweite = erste + 1; zweite < rollen.size(); zweite++) {
                Rolle links = rollen.get(erste);
                Rolle rechts = rollen.get(zweite);
                if (links.leerIst() || !new TreeSet<>(links.rechte())
                        .equals(new TreeSet<>(rechts.rechte()))) {
                    continue;
                }
                saetze.add(PFAD_ROLLEN + ": Die Rollen \"" + links.schluessel()
                        + "\" und \"" + rechts.schluessel() + "\" geben genau dieselben"
                        + " Rechte. Das ist erlaubt - zwei Namen fuer dieselbe Vollmacht"
                        + " trennen sich erst, wenn ihre Werkzeuge gebaut sind.");
            }
        }
        return List.copyOf(saetze);
    }

    /** Dieselben Werte mit anderen Rollen - fuer das Neuladen und fuer Proben. */
    public Teamwerte mitRollen(List<Rolle> andere) {
        return new Teamwerte(andere);
    }
}
