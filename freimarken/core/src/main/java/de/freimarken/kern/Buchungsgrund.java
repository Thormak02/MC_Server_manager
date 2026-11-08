package de.freimarken.kern;

import java.util.Locale;

/**
 * Warum Geld geflossen ist.
 *
 * <p>Eine Aufzaehlung und kein Freitext. Der Grund muss gruppierbar sein, sonst kann der
 * Wirtschaftsbericht aus Plan 12b nie eine Senkensumme bilden - ein Freitext spaltet
 * dieselbe Senke in Tippfehler auf, und zwei Jahre spaeter steht im Buch dreimal fast
 * dasselbe.
 *
 * <p><b>Die Richtung gehoert an den Grund</b>, nicht an das Vorzeichen allein. Die erste
 * Parzelle ist gratis: Das ist eine Buchung ueber 0 Mark, also ohne Vorzeichen - und
 * trotzdem eine Ausgabe. Nur so bleibt sie im Buch von einer Gutschrift ueber 0
 * unterscheidbar.
 *
 * <p>Der Schluessel ist ASCII und kleingeschrieben, denn er landet in der Datenbank und
 * muss die heutige Wortwahl ueberleben. Was ein Spieler liest, steht bei den uebrigen
 * Meldungen im Paper-Teil; wer die deutschen Woerter hier einfrieren wuerde, koennte sie
 * spaeter nicht mehr aendern, ohne alte Zeilen unlesbar zu machen.
 *
 * <p>Nur Gruende, die auch gebucht werden. Die Ladenmiete kommt mit dem taeglichen
 * Lauf und die Marktgebuehr mit dem Laden selbst - sie hier schon einzutragen waere
 * Vorbauen, und ein Grund, den niemand bucht, sieht im Bericht aus wie eine Senke, die
 * nicht funktioniert.
 */
public enum Buchungsgrund {

    /** Das Startguthaben, im Augenblick der Eroeffnung. */
    EROEFFNUNG("eroeffnung", Richtung.EINNAHME),

    /**
     * Der Stand, den ein Konto beim Einfuehren des Buchungsbuchs schon hatte.
     *
     * <p>Heisst ausdruecklich nicht {@code eroeffnung}: Was vor dem Buch geschah, wissen
     * wir nicht. Diese Zeile behauptet nur etwas ueber das Jetzt und erfindet keine
     * Geschichte.
     */
    BESTAND("bestand", Richtung.EINNAHME),

    /** Eine Parzelle genommen. */
    PARZELLE_GEKAUFT("parzelle_gekauft", Richtung.AUSGABE),

    /** Eine Parzelle konnte nicht eingetragen werden - der Preis kommt zurueck. */
    PARZELLE_ERSTATTET("parzelle_erstattet", Richtung.EINNAHME),

    /** Zwei Parzellen zusammengelegt. */
    ZUSAMMENLEGUNG("zusammenlegung", Richtung.AUSGABE),

    /** Das Zusammenlegen ist gescheitert - die Gebuehr kommt zurueck. */
    ZUSAMMENLEGUNG_ERSTATTET("zusammenlegung_erstattet", Richtung.EINNAHME),

    /**
     * Einen Beruf abgelegt - die Wechselgebuehr.
     *
     * <p>Das WAEHLEN bucht nicht und hat darum keinen Grund: Ein freier Platz kostet
     * nichts. Bezahlt wird das Verlassen, denn andersherum waere die Gebuehr umgehbar -
     * erst aufgeben, einen Tag warten, dann gratis etwas anderes waehlen.
     */
    BERUF_VERLASSEN("beruf_verlassen", Richtung.AUSGABE),

    /** Das Ablegen ist gescheitert - die Gebuehr kommt zurueck. */
    BERUF_ERSTATTET("beruf_erstattet", Richtung.EINNAHME),

    /**
     * Was ein Beruf eingebracht hat - die erste und wichtigste QUELLE dieser Wirtschaft.
     *
     * <p><b>Gesammelt und nicht je Block.</b> Eine Buchung je abgebautem Block waere bei
     * fuenfzig Spielern eine Viertelmillion Zeilen in der Stunde und 30 bis 150 Prozent
     * des Hauptfadens nur fuer das Buchen - gerechnet am 6.11.2026. Gebucht wird darum,
     * was ein Zeitgeber gesammelt hat; die einzelne Taetigkeit steht in keiner Zeile.
     *
     * <p>Wer wissen will, WAS ein Spieler getan hat, fragt das Logbuch - es ist ein
     * austauschbarer Nachbar, und dieses Buch ist das Geldbuch. Hier steht nur, dass Geld
     * aus Arbeit kam, und genau das braucht der Verdienstdeckel: Er rechnet seine
     * Tagessumme aus DIESEN Zeilen, statt eine zweite Wahrheit daneben zu fuehren.
     */
    BERUF_VERDIENST("beruf_verdienst", Richtung.EINNAHME),

    /**
     * Verkauf an den Bundesmarkt.
     *
     * <p>Die zweite gedeckelte Quelle (Plan 8.2: "Verkauf an den Bundesmarkt - kleines
     * Sortiment, niedrige Preise"). Ein EIGENER Grund und nicht derselbe wie die Arbeit,
     * und das ist die tragende Entscheidung dieses Schrittes: Beide Quellen haben einen
     * Tagesdeckel, und beide rechnen ihre Tagessumme aus diesen Zeilen. Mit einem
     * gemeinsamen Grund waere jeder der zwei Deckel durch die ANDERE Taetigkeit zu
     * fuellen - wer am Markt verkauft, koennte danach nicht mehr arbeiten.
     *
     * <p>WAS verkauft wurde, steht nicht hier: Dieses Buch ist das Geldbuch. Hier steht,
     * dass Geld aus einem Verkauf an den Bund kam - dieselbe Trennung wie bei der Arbeit.
     */
    BUNDESMARKT_VERKAUF("bundesmarkt_verkauf", Richtung.EINNAHME),

    /**
     * Der Tagesbonus - die dritte und kleinste Quelle (Plan 8.2).
     *
     * <p><b>Dieser Grund ist der Waechter ueber "heute schon bezahlt".</b> Nicht die
     * Serienzeile in der Datenbank, sondern DIESE Buchung: Das Buchungsbuch ist die
     * Wahrheit ueber Geld, und nur hier kann keine Reihenfolge von Abstuerzen eine zweite
     * Zahlung am selben Tag erzeugen. Wer den Schluessel aendert, macht jeden heute schon
     * gezahlten Bonus unsichtbar - und am naechsten Tageswechsel wird er ein zweites Mal
     * gezahlt.
     */
    TAGESBONUS("tagesbonus", Richtung.EINNAHME),

    /**
     * Die Ladenlizenz - einmalig (Plan 7.1: 50.000 Mark, [belegt]).
     *
     * <p><b>Diese Buchung IST der Beleg, dass die Lizenz bezahlt ist.</b> Es gibt keine
     * Spalte dafuer und keinen zweiten Ort: Gefragt wird mit
     * {@link Kasse#summeSeit}, genau wie beim Tagesbonus nach "heute schon bezahlt". Wer
     * den Schluessel aendert, laesst jeden Spieler seine Lizenz ein zweites Mal zahlen -
     * und das sind 50.000 Mark, die niemand zurueckbekommt, weil niemand es merkt.
     */
    LADEN_LIZENZ("laden_lizenz", Richtung.AUSGABE),

    /**
     * Die Kaution beim Abschluss eines Mietvertrags.
     *
     * <p>Sie wird GEBUCHT und nicht gehalten. Ein gehaltener Betrag waere eine zweite
     * Quelle neben dem Buchungsbuch, und bei Geld gibt es nur eine. Was der Spieler
     * zurueckbekommt, rechnet der Vertrag aus seiner Miete - nicht aus einem Topf.
     */
    LADEN_KAUTION("laden_kaution", Richtung.AUSGABE),

    /**
     * Die Kaution zurueck - bei ordentlicher Rueckgabe (Plan 7.1).
     *
     * <p>Ordentlich sind ZWEI Wege: das Kuendigen und das Ablaufen ohne Verlaengerung
     * [entschieden 08.11.2026]. Wer abgelaufen ist, hat jeden Tag gezahlt und nichts
     * falsch gemacht - die Kaution ist das Pfand gegen das Weglaufen, nicht gegen das
     * Nicht-Verlaengern.
     *
     * <p>Beim Raeumen wegen Zahlungsausfall faellt diese Buchung ausdruecklich NICHT. Das
     * ist der ganze Zweck der Kaution: Sie macht das Weglaufen teurer als das Kuendigen.
     */
    LADEN_KAUTION_ZURUECK("laden_kaution_zurueck", Richtung.EINNAHME),

    /**
     * Die taegliche Ladenmiete - die staerkste Geldsenke des Servers (Plan 7.1).
     *
     * <p><b>Diese Buchung IST der Beleg, dass heute bezahlt ist.</b> Gefragt wird mit
     * {@link Kasse#summeSeit} seit dem Tagesbeginn, genau wie beim {@link #TAGESBONUS}
     * und bei der {@link #LADEN_LIZENZ}: Steht eine Zeile da, ist der Tag erledigt -
     * unabhaengig davon, was sonst wo steht. Und wie dort gilt die Vorzeichenfalle: Die
     * Summe ist fuer eine AUSGABE negativ, also wird auf {@code == 0} gefragt und nie auf
     * ein Vorzeichen.
     *
     * <p><b>Es wird hoechstens ein Tag berechnet, nie nachgefordert.</b> Ein Server, der
     * drei Tage aus war, bucht beim naechsten Start EINE Miete und nicht drei. Das ist
     * eine Entscheidung und kein Versehen: In diesen drei Tagen konnte niemand seinen
     * Laden benutzen, und eine Nachforderung traefe genau den Spieler, der nichts dafuer
     * kann - samt der Ausfaelle, die ihn dann raeumen.
     */
    LADEN_MIETE("laden_miete", Richtung.AUSGABE),

    /**
     * Was ein Kunde an einem Kistenshop bezahlt hat (Plan 7.4).
     *
     * <p>Jeder Handel ist eine Buchung im Buchungsbuch, und das ist mehr als ChestShop
     * hat: Jenes fuehrt nur Logzeilen und kein abfragbares Protokoll. Der belegte Fall
     * dazu ist ChestShop #465 - eine Dupe-Meldung wurde geschlossen, WEIL der Melder kein
     * Log liefern konnte. Diese Lage kann hier nicht entstehen: Das Buch ist die Wahrheit
     * ueber Geld und beantwortet jede Frage nach einem Handel.
     */
    SHOP_GEKAUFT("shop_gekauft", Richtung.AUSGABE),

    /**
     * Was ein Verkaeufer an einem Kistenshop bekommen hat - NACH Abzug der Gebuehr.
     *
     * <p>Der Erloes und nicht der Preis: Was der Kunde zahlt, steht in seiner eigenen
     * Zeile, und die Gebuehr in ihrer. Drei Zeilen fuer einen Handel, und jede nennt, was
     * WIRKLICH geflossen ist - dann ist die Summe aller Buchungen eines Kontos immer sein
     * Stand, und genau das hat der Spieltest vom 07.11.2026 gemessen.
     */
    SHOP_VERKAUFT("shop_verkauft", Richtung.EINNAHME),

    /**
     * Die Marktgebuehr - was der Staat von einem Handel nimmt.
     *
     * <p>Eine EIGENE Zeile und kein stiller Abzug vom Erloes. Ohne sie waere die Gebuehr
     * nur als Differenz zweier Zahlen zu sehen, und ein Betreiber, der wissen will, was
     * seine Senke einbringt, muesste sie aus zwei Gruenden zusammenrechnen. Ab Werk
     * stehen beide Saetze auf 0, also faellt diese Buchung heute nie - sie steht da,
     * damit sie nicht fehlt, wenn der erste Satz hochgeht.
     */
    SHOP_GEBUEHR("shop_gebuehr", Richtung.AUSGABE),

    /**
     * Ein Abschluss ist gescheitert - die KAUTION kommt zurueck.
     *
     * <p>Nur sie, und das ist wichtig: Die Lizenz bleibt bezahlt, denn sie haengt an
     * keinem Laden. Stuende sie hier mit drin, zaehlte sie danach als bezahlt, ohne dass
     * Geld geblieben waere - und der naechste Laden desselben Spielers waere umsonst.
     */
    LADEN_ERSTATTET("laden_erstattet", Richtung.EINNAHME);

    /** Ob Geld zum Spieler hin oder von ihm weg fliesst. */
    public enum Richtung {
        EINNAHME,
        AUSGABE
    }

    private final String schluessel;
    private final Richtung richtung;

    Buchungsgrund(String schluessel, Richtung richtung) {
        this.schluessel = schluessel;
        this.richtung = richtung;
    }

    /** Wie der Grund in der Datenbank steht. */
    public String schluessel() {
        return schluessel;
    }

    public Richtung richtung() {
        return richtung;
    }

    /** Ob Geld vom Spieler weg fliesst. */
    public boolean istAusgabe() {
        return richtung == Richtung.AUSGABE;
    }

    /**
     * Der Grund zu einem Schluessel aus der Datenbank.
     *
     * <p>Wirft bei einem unbekannten Schluessel, statt etwas zu raten. Eine Zeile, deren
     * Grund niemand kennt, kommt aus einer neueren Fassung des Plugins - und dann ist das
     * Buch nicht lesbar, sondern falsch lesbar, und das ist schlimmer.
     */
    public static Buchungsgrund ausSchluessel(String text) {
        if (text != null) {
            String klein = text.trim().toLowerCase(Locale.ROOT);
            for (Buchungsgrund grund : values()) {
                if (grund.schluessel.equals(klein)) {
                    return grund;
                }
            }
        }
        throw new IllegalArgumentException("Unbekannter Buchungsgrund: " + text);
    }
}
