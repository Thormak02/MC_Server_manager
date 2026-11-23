package de.freimarken.paper;

import de.freimarken.kern.Chunkfeld;
import de.freimarken.kern.Stadtplan;
import java.util.List;
import org.bukkit.configuration.ConfigurationSection;

/**
 * Liest {@code bauten.stadt} - ob die gerechnete Stadt entsteht und wie gross sie wird.
 *
 * <p><b>Warum das in der Datei steht und nicht im Programm</b>: Die Stadt legt rund
 * zweihundert Haeuser und zweihunderttausend Bloecke. Wer Altfurt von Hand bauen will,
 * muss sie abschalten koennen, ohne das Programm zu aendern - und wer nur den inneren
 * Ring sehen will, stellt ein Quartier ein statt zwei.
 *
 * <p><b>Zwei Quartiere ab Werk</b>, und das ist die Antwort auf einen Satz des
 * Betreibers: Die neun Bauten allein waren "viel zu klein und zu dicht beieinander". Ein
 * Quartier waere wieder ein Ring um einen Platz; zwei sind eine Stadt mit einer Gasse
 * darin, die nicht am Rand liegt.
 */
record Stadtliste(boolean bauen, int quartiere) {

    /** Wo die Einstellungen stehen. */
    static final String PFAD = "bauten.stadt";

    /** Der Schluessel des Schalters. */
    static final String SCHLUESSEL_BAUEN = "bauen";

    /** Der Schluessel der Quartierszahl. */
    static final String SCHLUESSEL_QUARTIERE = "quartiere";

    /** Ab Werk entsteht die Stadt mit zwei Quartieren. */
    static final boolean BAUEN_AB_WERK = true;

    /** Ab Werk zwei Quartiere - siehe Klassenkopf. */
    static final int QUARTIERE_AB_WERK = 2;

    /**
     * Mehr Quartiere als das wird nicht gebaut - unabhaengig davon, wie gross die
     * Welt ist.
     *
     * <p><b>GEMESSEN am 23.11.2026</b> und nicht gegriffen: Bei zehn Quartieren legt
     * der Plan 3347 Haeuser und setzt mit dem Pflaster 3 657 026 Bloecke. Gemessen
     * wurden 214 271 Bloecke in einer Sekunde; zehn Quartiere halten den Server
     * also eine gute Viertelminute an. Das ist die Grenze des Zumutbaren, und sie
     * hat nichts mit der Weltgroesse zu tun.
     *
     * <p>Die ZWEITE Grenze ist die Weltkante, und die kommt aus {@code welten:} -
     * siehe {@link #ausKonfiguration}. Beide gelten, und sie klagen verschieden:
     * Diese hier sagt "zu viel Arbeit", jene "kein Platz".
     */
    static final int HOECHSTENS_QUARTIERE = 10;

    Stadtliste {
        if (quartiere < 1) {
            throw new IllegalArgumentException(
                    "Eine Stadt braucht mindestens ein Quartier, nicht " + quartiere);
        }
    }

    /**
     * Liest den Abschnitt - fehlt er, gilt das Werk.
     *
     * <p>Eine Quartierszahl ausserhalb der Grenzen wird GEKLAGT und auf das Werk
     * zurueckgesetzt: Dreissig Quartiere waeren eine Stadt jenseits der Weltgrenze, und
     * ein Haus jenseits der Grenze laesst sich nicht betreten.
     *
     * <p><b>Die Weltkante kommt herein und wird nicht angenommen</b> [berichtigt
     * 23.11.2026]: Vorher stand die Grenze allein auf der ausgelieferten {@code kante:
     * 800}. Wer Altfurt kleiner einstellt, haette eine Stadt bekommen, die ueber die
     * Weltgrenze hinausreicht - und hinter der Grenze kommt niemand hin. Geprueft
     * wird mit {@link Chunkfeld#ausGrenze}, genau wie bei den eingetragenen Bauten:
     * ZWEI Stellen mit derselben Frage und verschiedenen Antworten waeren schlimmer
     * als eine strenge.
     *
     * @param kante die Kante der Welt, in der die Stadt steht, oder {@code null},
     *              wenn sie unbekannt ist - dann wird nur die Arbeitsgrenze geprueft
     *
     * <p><b>Ein Schluessel ohne WERT kommt nicht an</b> - am 23.11.2026 gegen
     * Bukkit gemessen: Fuer {@code quartiere:} ohne Wert sagt schon
     * {@code contains} false, und der Eintrag ist von einem fehlenden nicht zu
     * unterscheiden. Es gilt dann das Werk, und es wird NICHT geklagt - eine
     * Klage koennte nicht zwischen "nicht eingetragen" und "ohne Wert"
     * unterscheiden und erschiene darum bei jedem Server ohne diesen Abschnitt.
     * Darum fragt dieser Leser {@code contains} und nicht {@code getInt} mit
     * Rueckfall: {@code getInt} gaebe fuer einen fehlenden Schluessel ebenfalls
     * das Werk, aber fuer eine eingetragene 0 die 0 - und genau die soll die
     * Grenzpruefung sehen und beklagen.
     */
    static Stadtliste ausKonfiguration(ConfigurationSection wurzel, Integer kante,
            List<String> klagen) {
        // KEIN FRUEHER RUECKWEG OHNE ABSCHNITT [berichtigt 23.11.2026]: Vorher gab
        // der Leser bei fehlendem Abschnitt sofort das Werk heraus und sprang damit
        // ueber die Pruefung der Weltkante. Eine kleine Welt traegt aber auch die
        // zwei Quartiere AB WERK nicht - der Betreiber haette eine Stadt jenseits
        // seiner Weltgrenze bekommen, ohne einen einzigen Schluessel einzutragen.
        ConfigurationSection abschnitt =
                wurzel == null ? null : wurzel.getConfigurationSection(PFAD);
        boolean bauen = BAUEN_AB_WERK;
        int quartiere = QUARTIERE_AB_WERK;
        if (abschnitt != null) {
            bauen = abschnitt.getBoolean(SCHLUESSEL_BAUEN, BAUEN_AB_WERK);
            if (abschnitt.contains(SCHLUESSEL_QUARTIERE)) {
                quartiere = geleseneZahl(abschnitt, klagen);
            }
        }
        if (!bauen) {
            // Abgeschaltet: Ueber den Platz einer Stadt, die nicht entsteht, ist
            // nichts zu klagen.
            return new Stadtliste(false, quartiere);
        }
        // DIE WELTKANTE als zweite, eigene Grenze - mit eigener Klage.
        while (quartiere > 1 && !passtInDieWelt(quartiere, kante)) {
            klagen.add(PFAD + "." + SCHLUESSEL_QUARTIERE + ": " + quartiere
                    + " Quartiere reichen bis x "
                    + new Stadtplan(quartiere).umriss().bisX()
                    + " und damit ueber die Weltgrenze (Kante " + kante
                    + ") - dort kommt niemand hin. Es wird eines weniger gebaut.");
            quartiere--;
        }
        if (!passtInDieWelt(quartiere, kante)) {
            klagen.add(PFAD + ": Schon ein Quartier reicht ueber die Weltgrenze von"
                    + " Altfurt (Kante " + kante + ") - es wird keine Stadt gebaut."
                    + " Vergroessere welten.<welt>.kante oder stelle " + PFAD + "."
                    + SCHLUESSEL_BAUEN + ": false ein.");
            return new Stadtliste(false, QUARTIERE_AB_WERK);
        }
        return new Stadtliste(bauen, quartiere);
    }

    /** Die eingetragene Zahl, geprueft gegen die Arbeitsgrenze. */
    private static int geleseneZahl(ConfigurationSection abschnitt,
            List<String> klagen) {
        // isInt GEFRAGT und nicht nur getInt: Fuer "zwei" gibt getInt eine 0, fuer
        // 2.9 eine 2 - die eine Klage waere verwirrend, die andere gaebe es nicht.
        if (!abschnitt.isInt(SCHLUESSEL_QUARTIERE)) {
            klagen.add(PFAD + "." + SCHLUESSEL_QUARTIERE + " ist \""
                    + abschnitt.get(SCHLUESSEL_QUARTIERE) + "\" - das ist keine ganze"
                    + " Zahl. Es gilt " + QUARTIERE_AB_WERK + ".");
            return QUARTIERE_AB_WERK;
        }
        int gelesen = abschnitt.getInt(SCHLUESSEL_QUARTIERE, QUARTIERE_AB_WERK);
        if (gelesen < 1 || gelesen > HOECHSTENS_QUARTIERE) {
            klagen.add(PFAD + "." + SCHLUESSEL_QUARTIERE + " ist " + gelesen
                    + " - erlaubt ist 1 bis " + HOECHSTENS_QUARTIERE + " (mehr waere"
                    + " zu viel Arbeit: zehn Quartiere setzen ueber drei Millionen"
                    + " Bloecke). Es gilt " + QUARTIERE_AB_WERK + ".");
            return QUARTIERE_AB_WERK;
        }
        return gelesen;
    }

    /**
     * Ob die Stadt mit so vielen Quartieren in eine Welt dieser Kante passt.
     *
     * <p>Ohne Kante gilt sie als passend: Eine fehlende Welt beklagt der Leser der
     * Bauten schon, und zwei Klagen ueber dieselbe Sache liest niemand.
     */
    static boolean passtInDieWelt(int quartiere, Integer kante) {
        if (kante == null || kante <= 0) {
            return true;
        }
        return new Stadtplan(quartiere).umriss()
                .liegtIn(Chunkfeld.ausGrenze(0, 0, kante));
    }

    /** Die Einstellungen ab Werk. */
    static Stadtliste abWerk() {
        return new Stadtliste(BAUEN_AB_WERK, QUARTIERE_AB_WERK);
    }

    /** Der Plan zu diesen Einstellungen, oder {@code null}, wenn nicht gebaut wird. */
    Stadtplan plan() {
        return bauen ? new Stadtplan(quartiere) : null;
    }

    /** Die Zeile fuer den Startbericht. */
    String bericht() {
        if (!bauen) {
            return "Die gerechnete Stadt ist abgeschaltet (" + PFAD + "."
                    + SCHLUESSEL_BAUEN + ": false) - es stehen nur die eingetragenen"
                    + " Bauten.";
        }
        Stadtplan plan = new Stadtplan(quartiere);
        return "Altfurt wird gerechnet: " + quartiere + " Quartiere, Mauer auf Ring "
                + plan.mauerVon() + ", Kante " + (2 * plan.aussenRing() + 1)
                + " Bloecke, " + plan.haeuser().size() + " Zeilenhaeuser.";
    }
}
