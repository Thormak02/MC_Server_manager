package de.freimarken.kern;

/**
 * Der Ankauf des Bundes AM SCHILD - und sein Tagesdeckel.
 *
 * <p><b>Verkauft wird am SCHILD und nicht per Befehl</b> [entschieden 08.11.2026, gebaut
 * 10.11.2026]. Ein Bundshop, an den ein Kunde verkauft, IST der Bundesmarkt - und damit
 * ist {@code /markt verkaufen} weggefallen. Der Grund steht im Plan (7.1) und ist keiner
 * der Technik: Ein Markt, den man von ueberall bedienen kann, ist ein Menue. Wer einmal
 * weiss, dass der Befehl geht, geht nie wieder hin - und damit ist das gebaute Gebaeude
 * Zierde, und niemand trifft dort jemanden.
 *
 * <p><b>Der Preis steht auf dem SCHILD</b> [entschieden 10.11.2026]. Eine Tafel
 * {@code bundesmarkt.ankauf} gab es einen halben Tag lang; sie ist gefallen, weil sie ein
 * zweites Tor war: "es kann ja nur ge- und verkauft werden, wofuer es ein Schild gibt."
 * Ein Ankaufstand ist seither ein gewoehnlicher {@link Kistenshop} ohne Besitzer, mit
 * allem, was dort schon gilt - ganze Mark, Preisgrenze, und der Verkaufspreis nie ueber
 * dem Kaufpreis. Diese Klasse haelt darum nur noch, was den Bund von einem Spielerladen
 * unterscheidet: WELCHER Klick ein Ankauf ist, und der TAGESDECKEL.
 *
 * <p><b>Der Tagesdeckel schliesst die benannte Luecke aus Plan 7.4.</b> Dort stand
 * woertlich: "Ein Bundshop mit Verkaufspreis ist eine Geldquelle ohne Tagesdeckel - der
 * Deckel des Bundesmarkts rechnet ueber {@code bundesmarkt_verkauf}, der Shop bucht
 * {@code shop_verkauft}." Genau das ist zu: Ein Ankaufstand bucht
 * {@link Buchungsgrund#BUNDESMARKT_VERKAUF}, zaehlt sein Heute aus demselben Grund und
 * rechnet ueber dieselbe {@link Deckelregel} wie der Befehl vorher. Es gibt damit nur
 * noch EINEN Weg vom Spieler zum Geld des Bundes, und ob der gedeckelt ist, entscheidet
 * der Betreiber ({@link Marktwerte#KEIN_DECKEL}).
 *
 * <p>Die MARKTGEBUEHR bleibt, wo sie war: {@code Shopwerte.bundshopVerkauf}, ab Werk 0.
 * Sie ist eine Ausgabe des Kunden und darum keine zweite Quelle - der Deckel haengt am
 * Gutgeschriebenen.
 */
public final class Bundankauf {

    private Bundankauf() {
    }

    /**
     * Ob dieser Klick ein Ankauf des Bundes ist - die EINE Stelle dieser Frage.
     *
     * <p>Zwei Tatsachen, und beide muessen stimmen: Der Shop gehoert dem Bund, und der
     * Kunde VERKAUFT. Die andere Richtung - ein Bundshop, bei dem ein Kunde kauft - ist
     * eine Senke und braucht keinen Deckel: Dort fliesst Geld zum Bund und nicht von ihm
     * weg.
     *
     * <p>Als benannte Frage und nicht als Vergleich an den Stellen, die sie brauchen:
     * Abschriften von {@code demBund() && art == KUNDE_VERKAUFT} liefen auseinander, und
     * dann gilt der Deckel beim Rechnen und nicht beim Buchen.
     */
    public static boolean istAnkaufstand(Kistenshop shop, Handelsart art) {
        if (shop == null || art == null) {
            throw new IllegalArgumentException("Ohne Shop und Handelsart gibt es keine"
                    + " Frage nach dem Ankauf des Bundes");
        }
        return shop.demBund() && art == Handelsart.KUNDE_VERKAUFT;
    }

    /**
     * Wie viele Stufen der Tagesdeckel noch zulaesst - gedeckelt auf das Gewuenschte.
     *
     * <p>Gerechnet ueber {@link Deckelregel}, also durch dieselbe Rechnung wie der
     * Verdienstdeckel und wie {@code /markt verkaufen} vorher. Der WUNSCH ist, was dieser
     * Klick sonst buchen wuerde ({@code stufen * stufenpreis}) - so sieht der Deckel genau
     * die Summe, die auf dem Konto landet, und nicht eine gerundete davon.
     *
     * <p><b>Ohne Deckel kommt das Gewuenschte unveraendert zurueck</b>, und diese
     * Entscheidung steht HIER und nicht beim Aufrufer: Die Bedeutung der 0 gehoert an die
     * Stelle, die rechnet. Haette der Aufrufer sie zu treffen, stuende sie bei jedem
     * Aufrufer - und einer von ihnen fragte eines Tages {@code > 0} und der andere
     * {@code >= 0}.
     *
     * <p>Gibt 0, wenn heute nichts mehr geht. Der Aufrufer macht daraus die Absage; diese
     * Rechnung sagt nur, wie viel geht - dieselbe Arbeitsteilung wie bei
     * {@link Shopregel#passendeStufen}.
     *
     * @param werte      die Marktzahlen - sie allein wissen, ob gedeckelt wird
     * @param heuteSchon was dieser Spieler heute schon aus
     *                   {@link Buchungsgrund#BUNDESMARKT_VERKAUF} gutgeschrieben bekommen
     *                   hat
     * @param stufenpreis was eine Stufe einbringt
     * @param gewuenscht wie viele Stufen ohne den Deckel moeglich waeren
     */
    public static int stufenImDeckel(Marktwerte werte, long heuteSchon, long stufenpreis,
            int gewuenscht) {
        if (werte == null) {
            throw new IllegalArgumentException("Ohne Marktwerte gibt es keinen Deckel");
        }
        if (stufenpreis < 1L) {
            throw new IllegalArgumentException("Ohne Stufenpreis gibt es keine Stufen");
        }
        if (gewuenscht < 0) {
            throw new IllegalArgumentException("Es koennen nicht " + gewuenscht
                    + " Stufen gewuenscht sein");
        }
        if (!werte.gedeckelt() || gewuenscht == 0) {
            return gewuenscht;
        }
        long wunsch = (long) gewuenscht * stufenpreis;
        long erlaubt = Deckelregel.erlaubt(werte.tagesdeckel(), heuteSchon, wunsch);
        return (int) Math.min(gewuenscht, erlaubt / stufenpreis);
    }
}
