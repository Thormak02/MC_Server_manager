package de.freimarken.kern;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Die Lotterie in Altfurt - was ein Los kostet und was darin sein kann (Plan 14).
 *
 * <p><b>Das erste Los am Tag ist gratis, jedes weitere kostet mehr.</b> Das ist die
 * Entscheidung des Betreibers vom 22.11.2026, und sie hat zwei Seiten: Wer nur einmal am
 * Tag sein Glueck versucht, zahlt nie etwas - die Lotterie ist damit fuer jeden Spieler
 * erreichbar, auch fuer den, der gerade nichts hat. Wer dagegen zwanzig Lose am Tag
 * zieht, zahlt fuer das zwanzigste ein Vermoegen. Das ist dieselbe Bremse wie bei
 * {@link Preisstaffel} und aus demselben Grund: Ohne sie waere die Lotterie eine
 * Geldmaschine fuer den, der am meisten hat, statt eine Abwechslung fuer alle.
 *
 * <p><b>Die Steigerung ist EINGESTELLT und nicht die Verdopplung.</b> Anders als
 * {@link Preisstaffel}, die mit {@code <<} verdoppelt, rechnet die Lotterie mit einem
 * Prozentsatz: 200 ist die Verdopplung, 150 ein sanfterer Anstieg, 1000 ein brutaler.
 * Der Preis ist, dass nicht geschoben, sondern multipliziert wird - und dass darum
 * {@link #hoechstpreis} kein Luxus ist, sondern die Zeile, die den Ueberlauf faengt.
 *
 * <p><b>Drei Gewinnarten und kein KOSMETIK.</b> Der Betreiber hat Kosmetik als Gewinn
 * genannt, und sie steht auch in der ausgelieferten Tabelle - aber nicht als eigene Art
 * im Programm. Ein kosmetischer Gewinn ist ein {@link Art#GEGENSTAND} mit einem eigenen
 * {@code anzeigename}: ein "Fischerhut von Altfurt" ist ein Lederhelm, der so heisst. Das
 * ist nicht die halbe Loesung, sondern die ganze - was Kosmetik IST, entscheidet dann die
 * Einstellungsdatei und nicht das Programm, und ein neuer kosmetischer Gewinn braucht
 * keine Zeile Java. Eine vierte Art KOSMETIK ohne eine Stelle, die etwas Kosmetisches
 * vergeben KANN, waere eine Zusage ohne Bau.
 *
 * <p>Alle Zahlen kommen aus der Einstellungsdatei; gehalten wird der Satz in
 * {@link Wirtschaftsstand}. Reine Werte ohne Bukkit und ohne Zufall - die Ziehung macht
 * {@link Losregel}, und sie bekommt ihren Wurf als Zahl herein.
 *
 * @param an                 ob die Lotterie ueberhaupt laeuft
 * @param freieLoseJeTag     wie viele Lose am Tag gratis sind; 0 heisst "keines"
 * @param hoechsteLoseJeTag  wie viele Lose am Tag hoechstens gehen; 0 heisst unbegrenzt
 * @param grundpreis         was das erste BEZAHLTE Los kostet, in Mark
 * @param steigerungProzent  wie der Preis je Los steigt; 100 heisst "gar nicht"
 * @param hoechstpreis       der Deckel je Los, in Mark - klemmt in JEDER Rechenrunde
 * @param gewinne            die Gewinntabelle; je hoeher das Gewicht, je oefter
 */
public record Lotteriewerte(boolean an, int freieLoseJeTag, int hoechsteLoseJeTag,
                            long grundpreis, int steigerungProzent, long hoechstpreis,
                            List<Gewinn> gewinne) {

    /** Der Schluessel des Schalters in der Einstellungsdatei. */
    public static final String PFAD_AN = "lotterie.an";

    /** Der Schluessel fuer die Zahl der gratis Lose je Tag. */
    public static final String PFAD_FREIE_LOSE = "lotterie.freie-lose-je-tag";

    /** Der Schluessel fuer die Obergrenze je Tag. */
    public static final String PFAD_HOECHSTE_LOSE = "lotterie.hoechste-lose-je-tag";

    /** Der Schluessel fuer den Preis des ersten bezahlten Loses. */
    public static final String PFAD_GRUNDPREIS = "lotterie.grundpreis";

    /** Der Schluessel fuer die Steigerung in Prozent. */
    public static final String PFAD_STEIGERUNG = "lotterie.steigerung-prozent";

    /** Der Schluessel fuer den Hoechstpreis eines Loses. */
    public static final String PFAD_HOECHSTPREIS = "lotterie.hoechstpreis";

    /** Der Schluessel der Gewinntabelle selbst. */
    public static final String PFAD_GEWINNE = "lotterie.gewinne";

    /**
     * Die Unterschluessel einer Gewinnzeile - unter {@link #PFAD_GEWINNE}.{@code <name>}.
     *
     * <p>Jeder davon braucht seine Zeile in {@code Wirtschaftsstand.uebernimm}, wie die
     * Unterschluessel des Muenzhauskatalogs: Ohne sie gilt ein geaenderter Gewinn sofort,
     * und {@code /freimarken neuladen} antwortet "nichts geaendert". Tafel 31cw haelt das.
     */
    public static final String SCHLUESSEL_ART = "art";

    public static final String SCHLUESSEL_GEWICHT = "gewicht";

    public static final String SCHLUESSEL_MENGE = "menge";

    public static final String SCHLUESSEL_GEGENSTAND = "gegenstand";

    public static final String SCHLUESSEL_NAME = "name";

    /**
     * Die kleinste erlaubte Steigerung - 100 Prozent, also gar keine.
     *
     * <p>Darunter wuerde das Los mit jedem Zug BILLIGER, und dann waere die Staffel
     * keine Bremse mehr, sondern ein Mengenrabatt fuers Vielziehen. Genau 100 ist
     * erlaubt und heisst: jedes Los kostet gleich viel.
     */
    public static final int KLEINSTE_STEIGERUNG = 100;

    /**
     * Die groesste erlaubte Steigerung - Faktor 100 je Los.
     *
     * <p>Gegen den Vertipper und nicht gegen den Willen: Wer 10000 statt 1000 tippt,
     * macht aus dem dritten Los einen Preis, den niemand je zahlt. Die Grenze faengt
     * ausserdem die Rechnung ab, in der der Zwischenwert ueber den {@code long} geht,
     * bevor der Deckel ihn klemmen kann - siehe {@link #preisFuerNaechstes(int)}.
     */
    public static final int GROESSTE_STEIGERUNG = 10_000;

    /** Das hoechste Gewicht einer Zeile - gegen den Vertipper, wie bei {@link Angelwerte}. */
    public static final int HOECHSTES_GEWICHT = 1_000_000;

    /**
     * Der hoechste Deckel, mit dem die Staffel noch rechnen kann.
     *
     * <p>Gerechnet und nicht getippt, genau wie {@link Preisstaffel#HOECHSTER_GRUNDPREIS}:
     * Die Rechnung in {@link #preisFuerNaechstes(int)} bildet
     * {@code preis / 100 * steigerungProzent}, und {@code preis} liegt dabei unter dem
     * Deckel. Der groesste Zwischenwert ist damit
     * {@code hoechstpreis / 100 * GROESSTE_STEIGERUNG}, und der muss in einen
     * {@code long} passen - sonst klemmt der Deckel eine Zahl, die schon NEGATIV ist,
     * und ein negativer Preis ist eine Gutschrift beim Kauf.
     *
     * <p>Das ist Arithmetik und keine Balance; die Balance macht der ausgelieferte
     * Deckel, und der liegt um viele Zehnerpotenzen darunter.
     */
    public static final long HOECHSTER_HOECHSTPREIS = Long.MAX_VALUE / GROESSTE_STEIGERUNG;

    /** Wie viele Lose ab Werk gratis sind: genau eines, wie vom Betreiber bestimmt. */
    public static final int VORGABE_FREIE_LOSE = 1;

    /**
     * Wie viele Lose ab Werk am Tag gehen - zwanzig.
     *
     * <p>Eine Grenze und nicht "unbegrenzt", obwohl der Preis schon bremst: Beim
     * ausgelieferten Satz kostet das zwanzigste Los bereits den Hoechstpreis, und
     * jedes weitere waere nur noch eine Zeile im Buchungsbuch. Die Grenze macht aus
     * "sinnlos teuer" ein ehrliches "fuer heute ist Schluss".
     */
    public static final int VORGABE_HOECHSTE_LOSE = 20;

    /** Was das erste bezahlte Los ab Werk kostet. */
    public static final long VORGABE_GRUNDPREIS = 500L;

    /** Die ausgelieferte Steigerung: Verdopplung je Los. */
    public static final int VORGABE_STEIGERUNG = 200;

    /** Der ausgelieferte Deckel je Los. */
    public static final long VORGABE_HOECHSTPREIS = 250_000L;

    /**
     * Die ausgelieferte Gewinntabelle.
     *
     * <p>Die Gewichte sind so gewaehlt, dass die Lotterie ueber viele Lose VERLIERT und
     * nicht gewinnt - sie ist eine Geldsenke und keine Quelle. Das ist Plan 9.3: Jede
     * Senke muss eine sein, sonst laeuft die Wirtschaft voll. Gerechnet ueber die
     * Gewichtsumme von 1000 und die ausgelieferte Staffel bringt ein Los im Mittel
     * ungefaehr 180 Mark, und ab dem zweiten kostet es mehr als das.
     *
     * <p>Das MEERESHERZ steht mit Gewicht 2 darin, und es ist der Grund, ueberhaupt
     * zu ziehen - dasselbe Muster wie in {@link Angelwerte#VORGABE_FAENGE}. Die
     * GOLDMARK steht mit Gewicht 3 und drei Stueck: gering, wie der Betreiber es
     * gesagt hat, und damit kein Weg um den Wechselkurs herum.
     */
    public static final List<Gewinn> VORGABE_GEWINNE = List.of(
            new Gewinn("nieten-trost", Art.MARK, 420, 50L, "", ""),
            new Gewinn("handvoll-mark", Art.MARK, 250, 200L, "", ""),
            new Gewinn("beutel-mark", Art.MARK, 120, 750L, "", ""),
            new Gewinn("kiste-mark", Art.MARK, 40, 5_000L, "", ""),
            new Gewinn("eisenbarren", Art.GEGENSTAND, 60, 8L, "iron_ingot", ""),
            new Gewinn("erfahrungsflasche", Art.GEGENSTAND, 40, 6L, "experience_bottle", ""),
            new Gewinn("diamant", Art.GEGENSTAND, 25, 2L, "diamond", ""),
            new Gewinn("fischerhut", Art.GEGENSTAND, 20, 1L, "leather_helmet",
                    "Fischerhut von Altfurt"),
            new Gewinn("marktschuerze", Art.GEGENSTAND, 15, 1L, "leather_chestplate",
                    "Marktschuerze von Altfurt"),
            new Gewinn("goldener-apfel", Art.GEGENSTAND, 5, 1L, "enchanted_golden_apple", ""),
            new Gewinn("goldmark", Art.GOLDMARK, 3, 3L, "", ""),
            new Gewinn("meeresherz", Art.GEGENSTAND, 2, 1L, "heart_of_the_sea", ""));

    /** Die Werte, die das Programm mitbringt. */
    public static final Lotteriewerte VORGABE = new Lotteriewerte(
            true, VORGABE_FREIE_LOSE, VORGABE_HOECHSTE_LOSE, VORGABE_GRUNDPREIS,
            VORGABE_STEIGERUNG, VORGABE_HOECHSTPREIS, VORGABE_GEWINNE);

    public Lotteriewerte {
        if (freieLoseJeTag < 0) {
            throw new IllegalArgumentException("Die Zahl der gratis Lose kann nicht"
                    + " negativ sein: " + freieLoseJeTag);
        }
        if (hoechsteLoseJeTag < 0) {
            throw new IllegalArgumentException("Die Obergrenze kann nicht negativ sein: "
                    + hoechsteLoseJeTag + " - 0 heisst unbegrenzt");
        }
        if (hoechsteLoseJeTag > 0 && hoechsteLoseJeTag < freieLoseJeTag) {
            throw new IllegalArgumentException("Es sind " + freieLoseJeTag + " Lose gratis,"
                    + " aber nur " + hoechsteLoseJeTag + " am Tag erlaubt - dann ist ein"
                    + " Teil der gratis Lose nie zu ziehen, und niemand erfaehrt, welcher.");
        }
        if (grundpreis < 0) {
            throw new IllegalArgumentException("Ein Los kann nichts kosten, aber nicht"
                    + " weniger als nichts: " + grundpreis);
        }
        if (steigerungProzent < KLEINSTE_STEIGERUNG
                || steigerungProzent > GROESSTE_STEIGERUNG) {
            throw new IllegalArgumentException("Die Steigerung ist " + steigerungProzent
                    + " Prozent und liegt nicht zwischen " + KLEINSTE_STEIGERUNG + " und "
                    + GROESSTE_STEIGERUNG + ". Unter 100 wuerde jedes weitere Los"
                    + " BILLIGER - das waere ein Mengenrabatt fuers Vielziehen.");
        }
        if (hoechstpreis < grundpreis) {
            throw new IllegalArgumentException("Der Hoechstpreis " + hoechstpreis
                    + " liegt unter dem Grundpreis " + grundpreis + " - dann kostet schon"
                    + " das erste bezahlte Los mehr, als erlaubt ist.");
        }
        if (hoechstpreis > HOECHSTER_HOECHSTPREIS) {
            throw new IllegalArgumentException("Der Hoechstpreis " + hoechstpreis
                    + " liegt ueber " + HOECHSTER_HOECHSTPREIS + ". Darueber laeuft die"
                    + " Zwischenrechnung der Staffel ueber den long, und ein"
                    + " uebergelaufener Preis ist NEGATIV - also eine Gutschrift beim"
                    + " Kauf.");
        }
        if (gewinne == null) {
            throw new IllegalArgumentException("Ohne Tabelle gibt es keine Lotterie -"
                    + " eine LEERE Tabelle ist erlaubt und heisst: sie laeuft nicht");
        }
        List<Gewinn> kopie = new ArrayList<>();
        Set<String> gesehen = new LinkedHashSet<>();
        for (Gewinn gewinn : gewinne) {
            if (gewinn == null) {
                throw new IllegalArgumentException("Eine leere Zeile steht in der"
                        + " Gewinntabelle");
            }
            if (!gesehen.add(gewinn.schluessel())) {
                throw new IllegalArgumentException("Der Gewinn " + gewinn.schluessel()
                        + " steht zweimal in der Tabelle. Zwei Zeilen mit demselben"
                        + " Schluessel sind im Protokoll nicht zu unterscheiden, und die"
                        + " zweite verschiebt stumm die Wahrscheinlichkeit der ersten.");
            }
            kopie.add(gewinn);
        }
        // Die eigene Liste und nicht die hereingegebene: Eine veraenderliche Liste in
        // einem unveraenderlichen Satz waere eine Hintertuer in den Stand, der bei jeder
        // Ziehung gelesen wird - derselbe Grund wie bei Angelwerte.
        gewinne = List.copyOf(kopie);
    }

    /**
     * Was das naechste Los kostet.
     *
     * <p>Der Deckel klemmt in JEDER Runde und nicht erst am Ende, und das ist die
     * tragende Zeile dieser Methode: Multipliziert man erst durch und klemmt danach, ist
     * der Zwischenwert bei genug Losen laengst ueber den {@code long} gelaufen - und ein
     * uebergelaufener Preis ist NEGATIV. Ein negativer Preis ist eine Gutschrift beim
     * Kauf, also die Geldquelle, gegen die die ganze Staffel gebaut ist.
     *
     * @param schonHeute wie viele Lose der Spieler heute schon gezogen hat, nie negativ
     * @return Preis in Mark; 0 fuer die gratis Lose
     * @throws IllegalArgumentException bei negativer Eingabe
     */
    public long preisFuerNaechstes(int schonHeute) {
        if (schonHeute < 0) {
            throw new IllegalArgumentException("Die Zahl gezogener Lose kann nicht"
                    + " negativ sein: " + schonHeute);
        }
        if (schonHeute < freieLoseJeTag) {
            return 0L;
        }
        long preis = grundpreis;
        for (int stufe = freieLoseJeTag; stufe < schonHeute; stufe++) {
            if (preis >= hoechstpreis) {
                return hoechstpreis;
            }
            // Geklemmt VOR der naechsten Runde - siehe den Methodenkopf.
            preis = preis / 100L * steigerungProzent
                    + preis % 100L * steigerungProzent / 100L;
            if (preis >= hoechstpreis || preis < 0L) {
                return hoechstpreis;
            }
        }
        return Math.min(preis, hoechstpreis);
    }

    /**
     * Ob heute noch ein Los geht.
     *
     * @param schonHeute wie viele der Spieler heute schon gezogen hat
     */
    public boolean nochEinsHeute(int schonHeute) {
        return hoechsteLoseJeTag == 0 || schonHeute < hoechsteLoseJeTag;
    }

    /** Die Summe aller Gewichte - der Nenner jeder Ziehung. */
    public long gewichtsumme() {
        long summe = 0L;
        for (Gewinn gewinn : gewinne) {
            summe += gewinn.gewicht();
        }
        return summe;
    }

    /** Ob diese Lotterie ueberhaupt etwas tut. */
    public boolean greift() {
        return an && !gewinne.isEmpty() && gewichtsumme() > 0L;
    }

    /** Die Schluessel aller Gewinne, in der Reihenfolge der Datei. */
    public List<String> schluessel() {
        List<String> namen = new ArrayList<>();
        for (Gewinn gewinn : gewinne) {
            namen.add(gewinn.schluessel());
        }
        return List.copyOf(namen);
    }

    /** Der Gewinn mit diesem Schluessel - leer, wenn die Tabelle ihn nicht kennt. */
    public java.util.Optional<Gewinn> gewinn(String schluessel) {
        for (Gewinn gewinn : gewinne) {
            if (gewinn.schluessel().equals(schluessel)) {
                return java.util.Optional.of(gewinn);
            }
        }
        return java.util.Optional.empty();
    }

    /**
     * Die Staffel im Klartext - was die naechsten Lose kosten.
     *
     * <p>Dieselbe Schranke wie {@link Preisstaffel#alsText(int)}: gegen den Wert, der im
     * erlaubten Bereich liegt und trotzdem falsch ist. 50 statt 500 faengt keine
     * Zahlengrenze, beide sind plausibel - wer im Bericht "2. Los 50" liest, sieht es.
     *
     * @param bis wie viele Lose genannt werden, mindestens eines
     */
    public String staffelAlsText(int bis) {
        StringBuilder text = new StringBuilder();
        for (int i = 0; i < Math.max(bis, 1); i++) {
            if (i > 0) {
                text.append(" - ");
            }
            long preis = preisFuerNaechstes(i);
            text.append(i + 1).append(". ").append(preis == 0L ? "gratis" : preis);
        }
        return text.toString();
    }

    /**
     * Dieselben Werte mit anderem Schalter.
     *
     * <p>Der Weg zu "keine Lotterie", den {@link Wirtschaftswerte} in seiner Klage
     * nennt - wie {@link Muenzwerte#mitAn}. Die Tabelle bleibt dabei stehen: Wer die
     * Lotterie abschaltet, will sie meist nur vorlaeufig aus haben und nicht seine
     * Gewinne verlieren.
     */
    public Lotteriewerte mitAn(boolean anders) {
        return new Lotteriewerte(anders, freieLoseJeTag, hoechsteLoseJeTag, grundpreis,
                steigerungProzent, hoechstpreis, gewinne);
    }

    /** Dieselben Werte mit einer anderen Gewinntabelle. */
    public Lotteriewerte mitGewinnen(List<Gewinn> andere) {
        return new Lotteriewerte(an, freieLoseJeTag, hoechsteLoseJeTag, grundpreis,
                steigerungProzent, hoechstpreis, andere);
    }

    /** Dieselbe Lotterie mit einer anderen Staffel - fuer Proben und den Leser. */
    public Lotteriewerte mitStaffel(int freie, int hoechste, long grund, int steigerung,
                                    long deckel) {
        return new Lotteriewerte(an, freie, hoechste, grund, steigerung, deckel, gewinne);
    }

    /** Welche Art ein Gewinn ist - was ausgezahlt beziehungsweise geliefert wird. */
    public enum Art {

        /** Mark auf das Konto. */
        MARK,

        /**
         * Goldmark auf das Goldkonto.
         *
         * <p>Die dritte Quelle fuer Goldmark neben Kauf und Wechsel, und darum mit
         * Absicht knapp gehalten: Wer sie gross macht, baut einen Weg um den
         * Wechselkurs herum (Plan 9.2).
         */
        GOLDMARK,

        /**
         * Ein Gegenstand in den Rucksack.
         *
         * <p>Auch der Weg fuer kosmetische Gewinne - siehe den Klassenkopf.
         */
        GEGENSTAND
    }

    /**
     * Eine Zeile der Gewinntabelle.
     *
     * @param schluessel   der Name der Zeile in der Einstellungsdatei - er steht im
     *                     Protokoll und im Bericht, nie im Spiel
     * @param art          was ausgezahlt wird
     * @param gewicht      je hoeher, je oefter; mindestens 1
     * @param menge        bei {@link Art#MARK} und {@link Art#GOLDMARK} der Betrag, bei
     *                     {@link Art#GEGENSTAND} die Stueckzahl
     * @param gegenstand   der Gegenstandsname - nur bei {@link Art#GEGENSTAND}, sonst leer
     * @param anzeigename  wie der Gewinn im Spiel heisst; leer heisst "der Gegenstand
     *                     behaelt seinen eigenen Namen"
     */
    public record Gewinn(String schluessel, Art art, int gewicht, long menge,
                         String gegenstand, String anzeigename) {

        /**
         * Die hoechste Stueckzahl eines Gegenstandsgewinns.
         *
         * <p>Dieselbe Zahl wie bei einem Ladenposten: ein voller Rucksack. Mehr kann
         * nicht geliefert werden, und ein Gewinn, der nicht zu liefern ist, ist kein
         * Gewinn, sondern eine Absage mit Vorlauf.
         */
        public static final long HOECHSTE_STUECKZAHL = 36L * 64L;

        /**
         * Der hoechste Geldgewinn - dieselbe Hausgrenze wie bei Bonus und Aufgaben.
         *
         * <p>{@link Freistadtwerte#HOECHSTER_BETRAG}, damit hier nicht eine zweite
         * Grenze fuer dasselbe gilt. Ohne sie waere ein Gewinn von
         * {@link Long#MAX_VALUE} Mark erlaubt - und eine Lotterie, die einmal den
         * ganzen Zahlenraum auszahlt, ist das Ende jeder Wirtschaft.
         *
         * <p>Sie deckelt die EINZELNE Zeile. Die Summe ueber die Tabelle kann trotzdem
         * ueberlaufen, und darum faengt {@link Losregel#mittlererMarkgewinn} das selbst -
         * dieselbe Aufteilung wie bei {@link Preisstaffel#gesamtpreisFuer}.
         */
        public static final long HOECHSTER_GELDGEWINN = Freistadtwerte.HOECHSTER_BETRAG;

        public Gewinn {
            if (schluessel == null || schluessel.isBlank()) {
                throw new IllegalArgumentException("Ein Gewinn ohne Schluessel ist im"
                        + " Protokoll nicht zu benennen");
            }
            if (art == null) {
                throw new IllegalArgumentException("Der Gewinn " + schluessel
                        + " hat keine Art - dann ist nicht zu sagen, was er auszahlt");
            }
            if (gewicht < 1 || gewicht > HOECHSTES_GEWICHT) {
                throw new IllegalArgumentException("Das Gewicht von " + schluessel
                        + " ist " + gewicht + " und liegt nicht zwischen 1 und "
                        + HOECHSTES_GEWICHT + ". Ein Gewicht von 0 ist eine Zeile, die"
                        + " nie greift - die gehoert aus der Tabelle heraus.");
            }
            if (menge < 1L) {
                throw new IllegalArgumentException("Der Gewinn " + schluessel + " gibt "
                        + menge + " her - ein Gewinn, der nichts bringt, ist eine Niete,"
                        + " und eine Niete gehoert als MARK mit kleinem Betrag in die"
                        + " Tabelle, nicht als leere Zeile.");
            }
            gegenstand = gegenstand == null ? "" : gegenstand.trim();
            anzeigename = anzeigename == null ? "" : anzeigename.trim();
            if (art == Art.GEGENSTAND) {
                if (gegenstand.isEmpty()) {
                    throw new IllegalArgumentException("Der Gewinn " + schluessel
                            + " ist ein Gegenstand, nennt aber keinen - der Name ist der"
                            + " Gegenstand.");
                }
                if (menge > HOECHSTE_STUECKZAHL) {
                    throw new IllegalArgumentException("Der Gewinn " + schluessel
                            + " sind " + menge + " Stueck und damit mehr als ein voller"
                            + " Rucksack (" + HOECHSTE_STUECKZAHL + ") - er waere nie zu"
                            + " liefern.");
                }
            } else if (menge > HOECHSTER_GELDGEWINN) {
                throw new IllegalArgumentException("Der Gewinn " + schluessel + " zahlt "
                        + menge + " aus und liegt damit ueber " + HOECHSTER_GELDGEWINN
                        + ". Eine Lotterie, die einmal den ganzen Zahlenraum auszahlt,"
                        + " ist das Ende jeder Wirtschaft.");
            } else if (!gegenstand.isEmpty()) {
                throw new IllegalArgumentException("Der Gewinn " + schluessel + " zahlt "
                        + art + " aus, nennt aber den Gegenstand " + gegenstand
                        + " - eine der beiden Angaben wird stumm ignoriert, und niemand"
                        + " erfaehrt welche.");
            } else if (!anzeigename.isEmpty()) {
                throw new IllegalArgumentException("Der Gewinn " + schluessel + " zahlt "
                        + art + " aus und traegt trotzdem den Anzeigenamen "
                        + anzeigename + " - ein Betrag hat keinen Namen, auf dem er"
                        + " stehen koennte.");
            }
        }

        /** Wie der Gewinn im Spiel genannt wird. */
        public String imSpiel() {
            return anzeigename.isEmpty()
                    ? (art == Art.GEGENSTAND ? gegenstand : schluessel)
                    : anzeigename;
        }
    }
}
