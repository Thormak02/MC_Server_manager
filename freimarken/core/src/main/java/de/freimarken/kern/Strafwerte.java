package de.freimarken.kern;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Was an den Strafen einstellbar ist (Plan 12c.3, Schritt 16a Teil B).
 *
 * <p><b>Die Hoechstdauer je Art ist der eigentliche Riegel dieses Teils.</b> Plan 12c.2
 * gibt dem Moderator das <i>zeitliche</i> Bannen und dem Verwalter das <i>dauerhafte</i>.
 * Ohne eine Zahl waere diese Unterscheidung wertlos: Ein Moderator tippt {@code 3650d},
 * und das ist formal befristet und praktisch endgueltig. Die Zahl hier sagt, wo das
 * Befristete aufhoert - was darueber liegt, braucht dasselbe Recht wie
 * {@code dauerhaft}.
 *
 * <p><b>Je Art ein Schluessel und nicht eine Zahl fuer alle.</b> Eine unbegrenzte
 * Stummschaltung ist genauso endgueltig wie ein Bann, aber die Dauer, ab der man
 * aufmerksam wird, ist eine andere: Sieben Tage stumm ist eine harte Strafe, sieben Tage
 * gebannt eine gewoehnliche. Ein gemeinsamer Wert waere fuer die eine zu hoch und fuer die
 * andere zu niedrig.
 *
 * <p><b>Was NICHT einstellbar ist: ob ein Grund verlangt wird.</b> Plan 12c.3 traegt
 * darauf - <i>"der Grund wird dem Betroffenen angezeigt. Eine Strafe, die niemand
 * erklaert, erzeugt nur Wiederkehr."</i> Ein Schalter dafuer waere die Einstellung, mit
 * der ein Betreiber seine eigene Moderation in Willkuer verwandelt, ohne es zu merken.
 *
 * @param hoechsteStunden       je fortwirkender Art: bis zu wie vielen Stunden ohne das
 *                              Recht fuer dauerhafte Strafen gesperrt werden darf. 0
 *                              heisst: jede Dauer braucht dieses Recht
 * @param bannlisteOeffentlich  ob {@code /bannliste} fuer jeden lesbar ist
 * @param ansagen               ob eine Strafe im Chat angesagt wird
 * @param zeilenJeSeite         wie viele Zeilen eine Liste auf einmal zeigt
 * @param stummSperrtBefehle    welche Befehle eine Stummschaltung mitsperrt - ohne sie
 *                              ist sie ueber einen Fluesterbefehl umgehbar
 */
public record Strafwerte(Map<Strafart, Long> hoechsteStunden,
                         boolean bannlisteOeffentlich, boolean ansagen,
                         int zeilenJeSeite, List<String> stummSperrtBefehle) {

    /** Der Abschnitt mit einer Zahl je fortwirkender Art. */
    public static final String PFAD_HOECHSTE_STUNDEN =
            "moderation.strafen.hoechste-stunden";

    /** Ob die Bannliste fuer jeden lesbar ist. */
    public static final String PFAD_BANNLISTE_OEFFENTLICH =
            "moderation.strafen.bannliste-oeffentlich";

    /** Ob eine Strafe im Chat angesagt wird. */
    public static final String PFAD_ANSAGEN = "moderation.strafen.ansagen";

    /** Wie viele Zeilen eine Liste zeigt. */
    public static final String PFAD_ZEILEN_JE_SEITE =
            "moderation.strafen.zeilen-je-seite";

    /** Welche Befehle eine Stummschaltung mitsperrt. */
    public static final String PFAD_STUMM_SPERRT_BEFEHLE =
            "moderation.strafen.stumm-sperrt-befehle";

    /** Wie viele Zeilen eine Liste hoechstens haben darf. */
    public static final int HOECHSTE_ZEILEN = 50;

    /**
     * Die hoechste erlaubte Zahl in {@link #hoechsteStunden}.
     *
     * <p>Dieselbe Grenze wie {@link Strafdauer#LAENGSTE_TAGE}, in Stunden: Eine Zahl
     * darueber waere eine Hoechstdauer, die keine Dauer mehr erreichen kann - der Riegel
     * waere stumm offen, und das sieht niemand.
     */
    public static final long HOECHSTE_STUNDEN_GRENZE = Strafdauer.LAENGSTE_TAGE * 24L;

    /**
     * Die Befehle, die eine Stummschaltung ab Werk mitsperrt.
     *
     * <p><b>Diese Liste ist der Grund, aus dem eine Stummschaltung ueberhaupt wirkt.</b>
     * Wer nur den Chat sperrt, hat einen Spieler stummgeschaltet, der weiter
     * {@code /msg jeder Beleidigung} tippt - und der Moderator haelt ihn fuer still. Die
     * Namen sind Vanilla und Paper ({@code msg}, {@code tell}, {@code w},
     * {@code teammsg}, {@code tm}, {@code me}, {@code say}); was ein Nachbarplugin
     * mitbringt, traegt der Betreiber nach. Darum eine LISTE in der Datei und keine
     * eingebaute Entscheidung.
     *
     * <p>{@code /melden} steht ausdruecklich NICHT darin: Ein stummgeschalteter Spieler
     * darf melden. Wer gerade bestraft wurde, ist nicht derjenige, der nichts mehr
     * gesehen haben darf - und eine Moderation, die dem Bestraften den Meldeweg nimmt,
     * nimmt ihn genau dem, der ihn gegen einen Moderator braeuchte.
     */
    public static final List<String> VORGABE_STUMM_SPERRT =
            List.of("msg", "tell", "w", "teammsg", "tm", "me", "say");

    /**
     * Die Vorgabe des Programms.
     *
     * <p><b>168 Stunden stumm (sieben Tage) und 720 Stunden Bann (dreissig Tage)</b>, und
     * beide Zahlen sind die Antwort auf die Frage, was ein Moderator allein entscheiden
     * soll. Dreissig Tage sind eine schwere Strafe, von der ein Spieler zurueckkommt; was
     * darueber liegt, ist ein Abschied - und dafuer soll ein zweites Paar Augen
     * zustimmen. Sieben Tage stumm sind dieselbe Ueberlegung in kleiner: Wer eine Woche
     * nicht schreiben darf, hat die Botschaft verstanden.
     */
    public static final Strafwerte VORGABE = new Strafwerte(
            Map.of(Strafart.STUMM, 168L, Strafart.BANN, 720L),
            true, true, 10, VORGABE_STUMM_SPERRT);

    public Strafwerte {
        if (hoechsteStunden == null) {
            throw new IllegalArgumentException("Ohne Hoechstdauern gibt es keinen"
                    + " Riegel gegen den dauerhaften Bann aus Versehen");
        }
        Map<Strafart, Long> kopie = new EnumMap<>(Strafart.class);
        for (Strafart art : Strafart.values()) {
            if (!art.giltFort()) {
                if (hoechsteStunden.containsKey(art)) {
                    throw new IllegalArgumentException("Die Art " + art + " wirkt nicht"
                            + " fort und hat keine Dauer - eine Hoechstdauer dafuer"
                            + " waere eine Zahl, die nie gefragt wird");
                }
                continue;
            }
            Long stunden = hoechsteStunden.get(art);
            if (stunden == null) {
                // VOLLSTAENDIG und nicht nachsichtig: Eine fehlende Zahl waere eine Art,
                // fuer die kein Riegel greift - und das faellt erst auf, wenn jemand
                // damit unbegrenzt sperrt.
                throw new IllegalArgumentException("Fuer die Art " + art + " fehlt die"
                        + " Hoechstdauer. Ohne sie gibt es keine Grenze zwischen dem,"
                        + " was ein Moderator darf, und dem, was ein Verwalter darf");
            }
            if (!brauchbareStunden(stunden)) {
                throw new IllegalArgumentException("Die Hoechstdauer fuer " + art
                        + " liegt zwischen 0 und " + HOECHSTE_STUNDEN_GRENZE
                        + " Stunden, nicht " + stunden);
            }
            kopie.put(art, stunden);
        }
        hoechsteStunden = Map.copyOf(kopie);
        if (zeilenJeSeite < 1 || zeilenJeSeite > HOECHSTE_ZEILEN) {
            throw new IllegalArgumentException("Eine Seite hat 1 bis " + HOECHSTE_ZEILEN
                    + " Zeilen, nicht " + zeilenJeSeite);
        }
        if (stummSperrtBefehle == null) {
            throw new IllegalArgumentException("Die Liste der gesperrten Befehle darf"
                    + " leer sein, aber nicht fehlen - eine fehlende Liste waere eine"
                    + " Stummschaltung, die ueber /msg umgangen wird");
        }
        List<String> namen = new ArrayList<>();
        for (String name : stummSperrtBefehle) {
            if (name == null || name.isBlank()) {
                throw new IllegalArgumentException("Ein leerer Befehlsname in "
                        + PFAD_STUMM_SPERRT_BEFEHLE + ". Er wuerde auf JEDEN Befehl"
                        + " passen oder auf keinen - beides ist kein Zustand");
            }
            // Ohne Schraegstrich, ohne Namensraum und klein: Was ein Betreiber
            // tippt, ist "/msg", "msg" oder "cmi:r", und was der Zuhoerer sieht, ist
            // "/msg hans hallo". Die Form wird HIER vereinheitlicht und nicht beim
            // Vergleichen - sonst vergleicht die eine Stelle anders als die andere.
            //
            // DER NAMENSRAUM FAELLT WEG [behoben am 25.11.2026]: Vorher blieb der
            // Doppelpunkt stehen, waehrend sperrtBefehl ihn beim GETIPPTEN
            // abschneidet - ein Eintrag "cmi:r" traf damit in KEINER Gestalt, weder
            // bei /cmi:r noch bei /r. Die Datei sagt zu, der Namensraum werde
            // mitgefangen, der Startbericht zaehlte den toten Eintrag mit, und
            // ungereimtheiten() sah ihn nicht: Der Moderator hielt den Spieler fuer
            // still, und der fluesterte weiter.
            //
            // Die Sperre gilt dem BEFEHL und nicht dem Namensraum - "cmi:r" und "r"
            // sind derselbe Befehl, nur anders angesprochen.
            String rein = name.trim().toLowerCase(Locale.ROOT);
            while (rein.startsWith("/")) {
                rein = rein.substring(1);
            }
            int doppelpunkt = rein.indexOf(':');
            if (doppelpunkt >= 0) {
                rein = rein.substring(doppelpunkt + 1);
            }
            if (rein.isEmpty()) {
                throw new IllegalArgumentException("Der Befehlsname \"" + name
                        + "\" in " + PFAD_STUMM_SPERRT_BEFEHLE + " besteht nur aus"
                        + " Schraegstrichen und Namensraum. Gesperrt wird der BEFEHL,"
                        + " nicht der Namensraum - schreib \"msg\" und nicht"
                        + " \"plugin:\".");
            }
            if (!namen.contains(rein)) {
                namen.add(rein);
            }
        }
        stummSperrtBefehle = List.copyOf(namen);
    }

    /** Bis zu wie vielen Stunden diese Art ohne das Recht fuer Dauerhaftes darf. */
    public long hoechsteStundenFuer(Strafart art) {
        if (art == null) {
            throw new IllegalArgumentException("Ohne Art gibt es keine Hoechstdauer");
        }
        if (!art.giltFort()) {
            throw new IllegalArgumentException("Die Art " + art + " hat keine Dauer -"
                    + " nach ihrer Hoechstdauer zu fragen ist ein Rechenfehler, und eine"
                    + " 0 als Antwort waere eine Grenze, die alles verbietet");
        }
        return hoechsteStunden.get(art);
    }

    /**
     * Ob dieser Befehl bei einer Stummschaltung gesperrt ist.
     *
     * <p>Hereingegeben wird, was der Spieler getippt hat - mit Schraegstrich, mit
     * Argumenten, in beliebiger Schreibweise. Verglichen wird nur das erste Wort ohne
     * Schraegstrich: {@code /MSG Hans hallo} ist {@code msg}.
     *
     * <p><b>Ein Namensraum wird mitgefangen</b>: Paper macht jeden Plugin-Befehl auch
     * unter {@code plugin:befehl} erreichbar, und ohne diese Zeile waere
     * {@code /minecraft:msg} der Weg um jede Stummschaltung herum.
     */
    public boolean sperrtBefehl(String getippt) {
        if (getippt == null) {
            return false;
        }
        String rein = getippt.trim().toLowerCase(Locale.ROOT);
        while (rein.startsWith("/")) {
            rein = rein.substring(1);
        }
        int leer = rein.indexOf(' ');
        if (leer >= 0) {
            rein = rein.substring(0, leer);
        }
        int doppelpunkt = rein.indexOf(':');
        if (doppelpunkt >= 0) {
            rein = rein.substring(doppelpunkt + 1);
        }
        return !rein.isEmpty() && stummSperrtBefehle.contains(rein);
    }

    /**
     * Was an diesen Werten auffaellt, ohne dass es ein Fehler waere.
     *
     * <p>Ein Bericht und keine Klage - dieselbe Linie wie bei {@link Meldewerte} und
     * {@link Teamwerte}: Alles hier ist erlaubt, und im Spiel merkt es niemand, bis es zu
     * spaet ist.
     */
    public List<String> ungereimtheiten() {
        List<String> saetze = new ArrayList<>();
        if (stummSperrtBefehle.isEmpty()) {
            saetze.add(PFAD_STUMM_SPERRT_BEFEHLE + " ist leer - eine Stummschaltung"
                    + " sperrt dann nur den Chat und ist ueber /msg oder /tell zu"
                    + " umgehen. Der Moderator haelt den Spieler fuer still, und der"
                    + " schreibt weiter.");
        }
        for (Strafart art : Strafart.values()) {
            if (!art.giltFort()) {
                continue;
            }
            long stunden = hoechsteStunden.get(art);
            if (stunden == 0L) {
                saetze.add(PFAD_HOECHSTE_STUNDEN + "." + art.kuerzel() + " ist 0 - jede"
                        + " Strafe dieser Art braucht das Recht fuer dauerhafte Strafen."
                        + " Ein Moderator ohne dieses Recht kann sie dann gar nicht"
                        + " verhaengen.");
            } else if (stunden > 8760L) {
                saetze.add(PFAD_HOECHSTE_STUNDEN + "." + art.kuerzel() + " ist "
                        + stunden + " Stunden (" + (stunden / 24L) + " Tage). Wer ohne"
                        + " das Recht fuer dauerhafte Strafen so lange sperren darf,"
                        + " sperrt praktisch dauerhaft - die Unterscheidung aus Plan"
                        + " 12c.2 zwischen Moderator und Verwalter greift dann nicht"
                        + " mehr.");
            }
        }
        if (!bannlisteOeffentlich) {
            saetze.add(PFAD_BANNLISTE_OEFFENTLICH + " ist aus - /bannliste braucht dann"
                    + " ein Recht. Plan 12c.4 macht sie ausdruecklich oeffentlich: Sie"
                    + " zwingt zu Begruendungen, die standhalten, und genau deshalb"
                    + " gehoert sie dazu.");
        }
        if (!ansagen) {
            saetze.add(PFAD_ANSAGEN + " ist aus - niemand ausser dem Betroffenen"
                    + " erfaehrt von einer Strafe. Wer zusieht, wie jemand grieft und"
                    + " nichts geschieht, meldet beim naechsten Mal nicht.");
        }
        return List.copyOf(saetze);
    }

    /** Dieselben Werte mit anderer Hoechstdauer fuer eine Art - fuer Proben. */
    public Strafwerte mitHoechsteStunden(Strafart art, long stunden) {
        Map<Strafart, Long> anders = new EnumMap<>(Strafart.class);
        anders.putAll(hoechsteStunden);
        anders.put(art, stunden);
        return new Strafwerte(anders, bannlisteOeffentlich, ansagen, zeilenJeSeite,
                stummSperrtBefehle);
    }

    /** Dieselben Werte mit anderer Bannliste. */
    public Strafwerte mitBannlisteOeffentlich(boolean anders) {
        return new Strafwerte(hoechsteStunden, anders, ansagen, zeilenJeSeite,
                stummSperrtBefehle);
    }

    /** Dieselben Werte mit anderer Ansage. */
    public Strafwerte mitAnsagen(boolean anders) {
        return new Strafwerte(hoechsteStunden, bannlisteOeffentlich, anders,
                zeilenJeSeite, stummSperrtBefehle);
    }

    /** Dieselben Werte mit anderer Seitenlaenge. */
    public Strafwerte mitZeilenJeSeite(int zeilen) {
        return new Strafwerte(hoechsteStunden, bannlisteOeffentlich, ansagen, zeilen,
                stummSperrtBefehle);
    }

    /** Dieselben Werte mit anderer Befehlssperre. */
    public Strafwerte mitStummSperrtBefehle(List<String> namen) {
        return new Strafwerte(hoechsteStunden, bannlisteOeffentlich, ansagen,
                zeilenJeSeite, namen);
    }

    /** Ob aus dieser Zahl eine Hoechstdauer werden kann - fuer den Leser der Datei. */
    public static boolean brauchbareStunden(long stunden) {
        return stunden >= 0L && stunden <= HOECHSTE_STUNDEN_GRENZE;
    }

    /** Ob aus dieser Zahl eine Seitenlaenge werden kann. */
    public static boolean brauchbareZeilen(long zeilen) {
        return zeilen >= 1L && zeilen <= HOECHSTE_ZEILEN;
    }
}
