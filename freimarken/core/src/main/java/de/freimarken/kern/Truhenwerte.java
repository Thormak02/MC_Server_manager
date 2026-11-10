package de.freimarken.kern;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

/**
 * Die Zahlen der Freien Truhe - Deckel, Orte und das Verbotene.
 *
 * <p>Dieselbe Bauart wie {@link Mietwerte} und {@link Bonuswerte}: ein Satz, eine Vorgabe
 * im Programm, und zu jedem Wert eine Frage, die der Leser stellen kann, ohne zu werfen.
 *
 * <p><b>Die STELLEN stehen nicht hier, sondern im {@link Truhenverzeichnis}.</b>
 * Derselbe Schnitt wie im Abschnitt {@code laeden:}, wo {@code Mietliste} die Zahlen und
 * {@code Ladenliste} die Einheiten liest: Die Zahlen sind neuladbar und gehoeren in den
 * {@link Wirtschaftsstand}, die Stellen sind von Hand eingemessenes Gelaende und werden
 * nur beim Start gelesen. Zusammen in einem Satz waere das Gelaende bei jedem Neuladen
 * mitgetauscht - und eine Truhe, die waehrend eines offenen Fensters den Ort wechselt,
 * rechnet die Entnahme eines Spielers gegen einen anderen Inhalt.
 *
 * <p><b>Mehrere Truhen teilen EINEN Deckel.</b> Der Plan nennt eine; wer zwei aufstellt,
 * soll damit nicht zwei Tagesmengen bekommen. Darum zaehlt der Deckel je SPIELER und
 * nicht je Truhe - und darum steht er hier und nicht an der Stelle.
 *
 * @param entnahmeJeTag wie viele Stueck ein Spieler an einem Tag behalten darf;
 *                      {@link #KEIN_DECKEL} heisst: so viele er will
 * @param verboten      Materialien, die nicht hineingehoeren, kleingeschrieben
 */
public record Truhenwerte(int entnahmeJeTag, Set<String> verboten) {

    public static final String PFAD_ENTNAHME = "freie-truhe.entnahme-je-tag";

    /** Die Liste der Materialien, die nicht hineingehoeren. */
    public static final String PFAD_VERBOTEN = "freie-truhe.verbotene-gegenstaende";

    /**
     * Die Zahl, die KEIN Deckel bedeutet.
     *
     * <p>Dieselbe Form und derselbe Grund wie bei {@code bundesmarkt.tagesdeckel}
     * [entschieden 10.11.2026]: eine ZAHL und kein {@code true}/{@code false}, denn
     * Bukkit liest jedes Wort ausser {@code true} stumm als {@code false}. Abgeschaltet
     * wird die Truhe nicht hiermit, sondern dadurch, dass keine Stelle eingetragen ist.
     */
    public static final int KEIN_DECKEL = 0;

    /**
     * KEIN Deckel ab Werk - wer etwas braucht, nimmt es.
     *
     * <p>Plan 7.2 nennt "etwa 16 Gegenstaende je Spieler und Tag" als Empfehlung; der
     * Betreiber hat am 10.11.2026 anders entschieden, und der Chat steht ueber dem
     * Dokument. Der Grund ist derselbe, aus dem der Bundesmarkt alles ankauft: Die Truhe
     * steht am Spawn, wo niemand bauen darf, und was darin liegt, hat jemand freiwillig
     * hineingelegt. Ein Deckel schuetzte dort nichts und kostete jeden Beschenkten eine
     * Erklaerung.
     *
     * <p><b>Der Deckel bleibt gebaut und einstellbar</b>, und das ist der eigentliche
     * Punkt: Er ist geprueft und wartet in {@code entnahme-je-tag}. Wer ihn morgen
     * braucht, traegt eine Zahl ein - nichts am Programm aendert sich dafuer.
     */
    public static final int VORGABE_ENTNAHME = KEIN_DECKEL;

    /** Mehr als eine Doppelkiste kann an einem Tag niemand herausnehmen. */
    public static final int HOECHSTE_ENTNAHME = Kistenshop.GROESSTE_MENGE;

    /**
     * NICHTS ist ab Werk verboten - eine leere Liste.
     *
     * <p>Der Plan nennt geschriebene Buecher und Shulkerkisten ("sonst wandert Besitz
     * hinein, der dort nicht hingehoert"); der Betreiber hat am 10.11.2026 anders
     * entschieden. Er stellt die Truhe selbst auf, und zwar am Spawn: Wer dort etwas
     * hineinlegt, hat es hineinlegen wollen.
     *
     * <p><b>Die Liste bleibt einstellbar</b>, und die config.yml nennt genau die Namen,
     * die hier standen, als Beispiel. Der Rueckweg - herausnehmen und dem Spieler
     * zurueckgeben - ist gebaut und geprueft; wer morgen Karten oder Trankflaschen
     * verbieten will, traegt sie ein.
     */
    public static final Set<String> VORGABE_VERBOTEN = Set.of();

    /** Genau das, was ohne Einstellungsdatei gelten wuerde. */
    public static final Truhenwerte VORGABE = new Truhenwerte(VORGABE_ENTNAHME,
            VORGABE_VERBOTEN);

    public Truhenwerte {
        if (!brauchbareEntnahme(entnahmeJeTag)) {
            throw new IllegalArgumentException("Eine Tagesentnahme von " + entnahmeJeTag
                    + " ist keine - erlaubt ist " + KEIN_DECKEL + " (kein Deckel) bis "
                    + HOECHSTE_ENTNAHME + ". Abgeschaltet wird die Truhe dadurch, dass"
                    + " keine Stelle eingetragen ist.");
        }
        if (verboten == null) {
            throw new IllegalArgumentException("Nichts ist keine Verbotsliste - eine"
                    + " LEERE Liste heisst: alles darf hinein");
        }
        // Kleingeschrieben und unveraenderlich: Verglichen wird gegen den Materialnamen,
        // den die Spielschicht klein hereingibt, und ein grosser Eintrag faende nie
        // etwas - stumm, denn "nichts verboten" sieht aus wie "alles erlaubt".
        verboten = wieAbgelegt(verboten);
    }

    /**
     * Dieselbe Liste, wie dieser Satz Werte sie halten wuerde: kleingeschrieben, ohne
     * leere Namen, ohne Doppelte.
     *
     * <p><b>Oeffentlich und nicht im Konstruktor versteckt</b>, damit der Leser der
     * Einstellungsdatei BERICHTEN kann, wie viele Gegenstaende am Ende wirklich gelten -
     * ohne dasselbe Glattziehen ein zweites Mal aufzuschreiben. Zwei Stellen, die
     * denselben Namen glattziehen, ziehen ihn eines Tages verschieden glatt; dann nennt
     * der Bericht eine Zahl, die die Truhe nicht kennt, und niemand sieht es.
     */
    public static Set<String> wieAbgelegt(Collection<String> namen) {
        Set<String> klein = new LinkedHashSet<>();
        for (String name : namen) {
            if (name != null && !name.isBlank()) {
                klein.add(name.trim().toLowerCase(Locale.ROOT));
            }
        }
        return Set.copyOf(klein);
    }

    /**
     * Ob dieses Material nicht hineingehoert.
     *
     * <p>Kleingeschrieben verglichen, wie die Liste abgelegt ist - siehe den
     * Konstruktor.
     */
    public boolean istVerboten(String material) {
        return material != null
                && verboten.contains(material.trim().toLowerCase(Locale.ROOT));
    }

    /** Ob ueberhaupt gedeckelt wird. */
    public boolean gedeckelt() {
        return entnahmeJeTag > KEIN_DECKEL;
    }

    /**
     * Ob diese Truhe ueberhaupt ZAEHLT - und damit, ob nur EINER hineinsehen darf.
     *
     * <p>Gezaehlt wird fuer genau zwei Dinge: den Tagesdeckel und die Rueckgabe
     * verbotener Gegenstaende. Beide rechnen mit dem Unterschied zwischen Oeffnen und
     * Schliessen, und dieser Unterschied muss GENAU EINEM Spieler gehoeren: Sehen zwei
     * zugleich hinein, bekommt der, der zuletzt schliesst, die Entnahme des anderen
     * angerechnet und die Einlage des anderen in die Hand.
     *
     * <p><b>Ohne Deckel und ohne Verbote wird darum nichts gezaehlt</b> [entschieden
     * 10.11.2026: "man soll auch gemeinsam hereinschauen koennen"]. Dann ist die Freie
     * Truhe eine gewoehnliche offene Kiste, die jede Nacht geleert wird - alle duerfen
     * gemeinsam hineinsehen, und kein Tagesstand wird gelesen oder geschrieben. Das
     * "einer zur Zeit" war nie Hoeflichkeit, sondern die Bedingung der Zaehlung; ohne
     * Zaehlung bliebe nur eine zugehaltene Gemeinschaftskiste.
     *
     * <p>Wer eines von beiden eintraegt, bekommt beides zurueck - die Zaehlung UND das
     * "einer zur Zeit". Gesagt wird es da, wo es auffaellt: in der Rechenprobe des
     * Startberichts und bei beiden Schluesseln in der {@code config.yml}.
     *
     * <p><b>Der TAGESSTAND haengt an der engeren Frage</b>: Er gehoert allein dem Deckel
     * ({@link #gedeckelt()}), denn nur der liest ihn. "Verbote ohne Deckel" ist darum die
     * Lage, in der gezaehlt wird und trotzdem keine Zeile in die Datenbank geht oder aus
     * ihr kommt - vorher hielt dort eine Stoerung der Datenbank die Truhe zu [Lesesuche
     * 10.11.2026].
     */
    public boolean zaehlt() {
        return gedeckelt() || !verboten.isEmpty();
    }

    /** Dieselben Werte mit anderer Tagesentnahme. */
    public Truhenwerte mitEntnahme(int andere) {
        return new Truhenwerte(andere, verboten);
    }

    /** Dieselben Werte mit anderer Verbotsliste. */
    public Truhenwerte mitVerboten(Set<String> andere) {
        return new Truhenwerte(entnahmeJeTag, andere);
    }

    public static boolean brauchbareEntnahme(int stueck) {
        return stueck >= KEIN_DECKEL && stueck <= HOECHSTE_ENTNAHME;
    }
}
