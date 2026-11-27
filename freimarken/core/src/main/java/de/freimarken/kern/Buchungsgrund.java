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

    /** Eine Freistadt gegruendet - Plan 6.2, 400.000 Mark. */
    FREISTADT_GEGRUENDET("freistadt_gegruendet", Richtung.AUSGABE),

    /**
     * Die Stadt konnte nicht eingetragen werden - der Preis kommt zurueck.
     *
     * <p>Nicht theoretisch: Tippen zwei Spieler im selben Augenblick denselben Namen,
     * laesst der eindeutige Schluessel uq_besitz_name den zweiten auflaufen. Ohne diese
     * Gegenbuchung zahlte er 400.000 Mark fuer nichts.
     */
    FREISTADT_ERSTATTET("freistadt_erstattet", Richtung.EINNAHME),

    /**
     * Ein Grundstueck in einer Freistadt GEKAUFT - Plan 15, das Abnahmekriterium.
     *
     * <p><b>Der erste Kauf dieses Projekts, bei dem das Geld nicht beim Bund landet.</b>
     * Darum gibt es drei Gruende und nicht einen: Derselbe Betrag ist beim Kaeufer eine
     * Ausgabe und beim Verkaeufer eine Einnahme, und im Verlauf beider muss die
     * richtige Richtung stehen. Ein gemeinsamer Grund hiesse, dass einer von beiden
     * seinen Kontostand nicht mehr nachrechnen kann.
     */
    GRUNDSTUECK_GEKAUFT("grundstueck_gekauft", Richtung.AUSGABE),

    /** Die Gegenseite desselben Kaufs - der Erloes des Verkaeufers. */
    GRUNDSTUECK_VERKAUFT("grundstueck_verkauft", Richtung.EINNAHME),

    /**
     * Die Marktgebuehr auf einen Grundstuecksverkauf.
     *
     * <p>Dem abgezogen, der das Geld BEKOMMT - wie bei den Kistenshops, und nie als
     * Aufschlag auf den Preis. Der Preis im Angebot ist, was der Kaeufer zahlt.
     */
    GRUNDSTUECK_GEBUEHR("grundstueck_gebuehr", Richtung.AUSGABE),

    /**
     * Der Kauf ist nicht zustande gekommen - das Geld des Kaeufers kommt zurueck.
     *
     * <p>Nicht theoretisch: Zwei Kaeufer, die im selben Augenblick zugreifen, laufen am
     * Riegel {@code angebotspreis IS NOT NULL} auf. Ohne diese Gegenbuchung haette der
     * zweite den Preis gezahlt und nichts bekommen.
     */
    GRUNDSTUECK_ERSTATTET("grundstueck_erstattet", Richtung.EINNAHME),

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
     * Was bei einem Handel am Kistenshop GEZAHLT wurde (Plan 7.4).
     *
     * <p><b>Beide Richtungen, nicht nur der Kunde.</b> Beim Kauf zahlt der Kunde, beim
     * Ankauf der Shopbesitzer - es ist dieselbe Senke und darum derselbe Grund. Ein vierter
     * Grund fuer die zweite Richtung spaltete dieselbe Zahlung in zwei Schluessel, und
     * genau das soll der Kopf dieser Aufzaehlung verhindern.
     *
     * <p>Jeder Handel ist eine Buchung im Buchungsbuch, und das ist mehr als ChestShop
     * hat: Jenes fuehrt nur Logzeilen und kein abfragbares Protokoll. Der belegte Fall
     * dazu ist ChestShop #465 - eine Dupe-Meldung wurde geschlossen, WEIL der Melder kein
     * Log liefern konnte. Diese Lage kann hier nicht entstehen: Das Buch ist die Wahrheit
     * ueber Geld und beantwortet jede Frage nach einem Handel.
     */
    SHOP_GEKAUFT("shop_gekauft", Richtung.AUSGABE),

    /**
     * Was ein Verkaeufer an einem Kistenshop bekommen hat - der VOLLE Preis.
     *
     * <p><b>Der Preis und nicht der Erloes</b> [berichtigt 08.11.2026]. Hier stand bis
     * dahin das Gegenteil, und zusammen mit {@link #SHOP_GEBUEHR} - "eine EIGENE Zeile
     * und kein stiller Abzug" - zog das die Gebuehr ZWEIMAL ab: Bei 1000 Mark und 7
     * Prozent bekaeme der Verkaeufer 930 gutgeschrieben und 70 abgebucht, also 860 statt
     * 930. Beide Saetze koennten nur gleichzeitig gelten, wenn die Gebuehrzeile auf einem
     * DRITTEN Konto laege - und ein Staatskonto gibt es nicht, hier so wenig wie bei
     * {@link #LADEN_MIETE}.
     *
     * <p>Also: zwei Konten und eine Senke. Der Kunde zahlt (Zeile eins), der Verkaeufer
     * bekommt den vollen Preis (Zeile zwei), der Staat nimmt seinen Teil davon (Zeile
     * drei). Netto bleibt der Erloes, die Gebuehr ist sichtbar, und die Summe aller
     * Buchungen eines Kontos ist weiterhin sein Stand.
     *
     * <p>Die Reihenfolge ist zwingend: erst gutschreiben, dann die Gebuehr abbuchen.
     * Umgekehrt scheiterte das Abbuchen bei einem Verkaeufer ohne Guthaben, und er
     * behielte den ganzen Preis.
     */
    SHOP_VERKAUFT("shop_verkauft", Richtung.EINNAHME),

    /**
     * Die Marktgebuehr - was der Staat von einem Handel nimmt.
     *
     * <p>Eine EIGENE Zeile und kein stiller Abzug vom Erloes. Ohne sie waere die Gebuehr
     * nur als Differenz zweier Zahlen zu sehen, und ein Betreiber, der wissen will, was
     * seine Senke einbringt, muesste sie aus zwei Gruenden zusammenrechnen.
     *
     * <p>Auf dem Konto des EMPFAENGERS und nicht auf einem dritten - siehe
     * {@link #SHOP_VERKAUFT}. Netto bleibt damit der Erloes.
     *
     * <p><b>Die Zeile faellt nur, wenn die GERECHNETE Gebuehr ueber 0 liegt</b>, und das
     * entscheidet {@code Shopregel.Handel.kostetGebuehr} an einer Stelle. Nicht der SATZ
     * ist die Frage: Bei 5 Prozent auf 19 Mark ist die Gebuehr abgerundet 0, und eine
     * Zeile ueber 0 Mark waere genau das, was {@link Kasse} verbietet - "Keine Zeile im
     * Buch ohne Geldbewegung". Ab Werk stehen alle Saetze auf 0, diese Buchung faellt
     * also heute nie; sie steht da, damit sie nicht fehlt, wenn der erste Satz
     * hochgeht.
     */
    SHOP_GEBUEHR("shop_gebuehr", Richtung.AUSGABE),

    /**
     * Ein Handel ist nach dem Zahlen gescheitert - das Geld kommt zurueck.
     *
     * <p>Dieselbe Antwort wie {@link #PARZELLE_ERSTATTET}, {@link #BERUF_ERSTATTET} und
     * {@link #LADEN_ERSTATTET}, und sie wird hier aus demselben Grund gebraucht: Der
     * Zahler wird ZUERST abgebucht - das ist die Reihenfolge, die kein Geld erzeugen
     * kann -, und wenn danach die Ware nicht kommt, hat er gezahlt und nichts
     * bekommen. Ohne diesen Grund gaebe es dafuer keine Buchung, denn
     * {@link Kasse} verlangt zu jeder Bewegung einen Grund und kennt keinen
     * Vorgabewert.
     *
     * <p><b>Nur die ZAHLUNG wird gedreht, nie die Gutschrift.</b> Scheitert die
     * Gutschrift an den Empfaenger, hat der Kunde seine Ware schon; was dann fehlt,
     * ist der Erloes, und der wird NACHgebucht und nicht gegengebucht. Dieser Fall
     * bekommt darum keine Buchung, sondern eine SEVERE-Zeile - dieselbe Entscheidung
     * wie in {@code Mietbuch.kuendigen}.
     *
     * <p>Nach einer genauen Platzrechnung ist der Fall nicht erreichbar. Er steht da,
     * weil "nicht erreichbar" kein Zustand ist, auf den man Geld setzt.
     */
    SHOP_ERSTATTET("shop_erstattet", Richtung.EINNAHME),

    /**
     * Ein Abschluss ist gescheitert - die KAUTION kommt zurueck.
     *
     * <p>Nur sie, und das ist wichtig: Die Lizenz bleibt bezahlt, denn sie haengt an
     * keinem Laden. Stuende sie hier mit drin, zaehlte sie danach als bezahlt, ohne dass
     * Geld geblieben waere - und der naechste Laden desselben Spielers waere umsonst.
     */
    LADEN_ERSTATTET("laden_erstattet", Richtung.EINNAHME),

    /**
     * Die Marktgebuehr, wie sie in der STADTKASSE ankommt [Teil E1].
     *
     * <p>Das Gegenstueck zu {@link #GRUNDSTUECK_GEBUEHR}, und bis heute gab es keines:
     * Die Gebuehr wurde dem Verkaeufer abgebucht und NIEMANDEM gutgeschrieben - eine
     * reine Senke, wie die Senkentabelle aus Plan 12b sie vorsieht. Ab jetzt faellt sie
     * der Stadt zu, in deren Feld das Grundstueck liegt.
     *
     * <p><b>Damit ist sie keine Senke mehr</b>, und das soll hier stehen, damit der
     * Wirtschaftsbericht aus Plan 12b sie nicht doppelt zaehlt: Dieselben Mark
     * erscheinen jetzt zweimal im Buch - einmal als Ausgabe beim Verkaeufer, einmal als
     * Einnahme bei der Stadt. Wer beide Gruende addiert, bekommt null; wer nur den
     * ersten nimmt, haelt eine Umverteilung fuer eine Senke.
     */
    STADTKASSE_GEBUEHR("stadtkasse_gebuehr", Richtung.EINNAHME),

    /**
     * Was der Stadtbesitzer aus der Kasse nimmt - auf dem Konto der STADT.
     *
     * <p>Zwei Gruende fuer eine Bewegung, und das ist keine Doppelung: Ein Konto traegt
     * nur seine eigenen Zeilen, und in der Stadtkasse ist das Abheben eine Ausgabe,
     * beim Spieler eine Einnahme. Mit einem einzigen Grund waere die eine der beiden
     * Zeilen falsch gerichtet, und die Summenprobe des Buches faende es nicht - sie
     * rechnet je Konto.
     */
    STADTKASSE_ABGEHOBEN("stadtkasse_abgehoben", Richtung.AUSGABE),

    /** Dieselbe Bewegung beim SPIELER - siehe {@link #STADTKASSE_ABGEHOBEN}. */
    STADTKASSE_AUSGEZAHLT("stadtkasse_ausgezahlt", Richtung.EINNAHME),

    /**
     * Was in die Kasse zurueckgelegt wurde, weil die Auszahlung scheiterte.
     *
     * <p>Ein EIGENER Grund und nicht die Gebuehr: In der Kasse ist beides eine
     * Einnahme, aber die Gebuehr erzaehlt von einem Verkauf, und diese Zeile erzaehlt
     * von einem Fehler. Wer den Verlauf liest, soll den Unterschied sehen - sonst
     * stuende dort eine Marktgebuehr, zu der es keinen Verkauf gibt.
     *
     * <p>Gebucht wird er selten, naemlich nur dann, wenn das Abbuchen bei der Stadt
     * gelang und die Gutschrift beim Spieler nicht. Selten ist nicht nie: Genau das ist
     * der Augenblick, in dem Geld verschwinden koennte.
     */
    STADTKASSE_ZURUECK("stadtkasse_zurueck", Richtung.EINNAHME),

    /**
     * Der Anteil, den ein Stadtbesitzer beim AUFGEBEN zurueckbekommt [Teil E6].
     *
     * <p>Ein eigener Grund und nicht {@link #FREISTADT_ERSTATTET}: Jener erzaehlt von
     * einer Gruendung, die SCHIEFGING - das Geld war abgebucht und die Stadt entstand
     * nicht. Dieser erzaehlt von einer Stadt, die es gab und die ihr Besitzer beendet
     * hat. Wer seinen Verlauf liest, soll den Unterschied sehen; mit einem gemeinsamen
     * Grund stuende dort "Gruendung erstattet" zu einer Stadt, in der er ein Jahr
     * gebaut hat.
     *
     * <p>Gerechnet vom EINGESETZTEN Preis der Zeile und nicht vom heutigen aus der
     * Datei - die Begruendung steht bei {@code Freistadtwerte.VORGABE_ERSTATTUNG}.
     */
    FREISTADT_AUFGEGEBEN("freistadt_aufgegeben", Richtung.EINNAHME),

    /**
     * Die LADENGEBUEHR, wie sie der Shopbesitzer zahlt [Teil E6].
     *
     * <p>Ein eigener Grund und nicht {@link #LADEN_LIZENZ}: Jene ist die einmalige
     * Gebuehr fuer einen Laden in der MARKTHALLE und geht an niemanden - eine Senke.
     * Diese geht an die STADT, in deren Feld der Kistenshop steht, und sie faellt
     * zweimal: einmal beim Anlegen und, wenn der Betreiber es einstellt, taeglich.
     *
     * <p><b>Ein Grund fuer beide Faelle</b>, anders als bei der Stadtkasse, wo Abheben
     * und Auszahlen getrennt sind: Dort geht es um ZWEI Konten, hier um dieselbe
     * Bewegung auf demselben. Was der Spieler im Verlauf lesen will, ist "Ladengebuehr"
     * - ob sie einmalig oder taeglich war, sagt ihm das Datum.
     */
    FREISTADT_LADENGEBUEHR("freistadt_ladengebuehr", Richtung.AUSGABE),

    /** Dieselbe Bewegung in der STADTKASSE - siehe {@link #FREISTADT_LADENGEBUEHR}. */
    STADTKASSE_LADENGEBUEHR("stadtkasse_ladengebuehr", Richtung.EINNAHME),

    /** Eine Meerinsel genommen - Plan 6.2, 75.000 Mark [Schritt 10]. */
    WERDER_GEKAUFT("werder_gekauft", Richtung.AUSGABE),

    /**
     * Die Insel konnte nicht eingetragen werden - der Preis kommt zurueck.
     *
     * <p>Nicht theoretisch: Stehen zwei Spieler im selben Augenblick auf derselben
     * Insel und tippen beide, laesst der eindeutige Schluessel auf (welt, x, z) den
     * zweiten auflaufen. Ohne diese Gegenbuchung zahlte er 75.000 Mark fuer nichts.
     */
    WERDER_ERSTATTET("werder_erstattet", Richtung.EINNAHME),

    /**
     * Eine Wolkeninsel genommen - Plan 6.2, GRATIS [Schritt 11].
     *
     * <p><b>Ein Grund, obwohl ab Werk nichts fliesst</b>, und das ist derselbe Fall wie
     * bei der ersten Parzelle: {@code abbuchen(0)} geht immer durch und schreibt
     * trotzdem eine Buchung. Die Zeile mit 0 Mark ist die SPUR - sie sagt, wann die
     * Insel genommen wurde, und ohne sie fehlte dieser Vorgang im Kontobuch ganz.
     *
     * <p>Und sie ist nicht fuer immer 0: {@code wolken.preis} ist eine Einstellung.
     */
    WOLKE_GENOMMEN("wolke_genommen", Richtung.AUSGABE),

    /**
     * Die Wolkeninsel konnte nicht eingetragen werden - der Preis kommt zurueck.
     *
     * <p>Wie bei {@link #WERDER_ERSTATTET}, und hier noch wahrscheinlicher: Zwei
     * Spieler, die im selben Augenblick {@code /wolke nehmen} tippen, bekommen aus
     * der Ringsuche DASSELBE freie Feld - sie stehen dafuer nicht einmal am selben
     * Ort. Den zweiten laesst der eindeutige Schluessel auflaufen.
     */
    WOLKE_ERSTATTET("wolke_erstattet", Richtung.EINNAHME),

    /**
     * Eine Herausforderung auf der Wolkeninsel erfuellt - Plan 4.1 [Schritt 11].
     *
     * <p>Die Belohnung steht je Aufgabe in {@code wolken.aufgaben.liste} und darf 0 sein.
     * Auch dann wird gebucht, und zwar aus demselben Grund wie bei
     * {@link #WOLKE_GENOMMEN}: Die Zeile ist die SPUR. Sie sagt, WANN die Insel ihre
     * Stufe bekommen hat - und ohne sie fehlte der Vorgang im Kontobuch ganz.
     */
    WOLKE_AUFGABE("wolke_aufgabe", Richtung.EINNAHME),

    /**
     * Das Biom einer Wolkeninsel gewechselt - Plan 4.1 [Schritt 11, Teil F].
     *
     * <p>JEDES MAL, auch beim gleichen Biom: Der Wechsel setzt die Biomzellen eines
     * ganzen Feldes um, und das Biom entscheidet, was dort waechst und erscheint. Der
     * Preis steht in {@code wolken.biomwechsel} und darf 0 sein.
     */
    WOLKE_BIOM("wolke_biom", Richtung.AUSGABE),

    /**
     * Der Biomwechsel ist nicht zustande gekommen - der Preis kommt zurueck.
     *
     * <p>Wie bei {@link #WOLKE_ERSTATTET}: Die Ablage schweigt NACH dem Abbuchen. Lieber
     * zwei Zeilen im Kontobuch als ein Spieler, der fuer nichts bezahlt hat.
     */
    WOLKE_BIOM_ERSTATTET("wolke_biom_erstattet", Richtung.EINNAHME),

    /**
     * Was ein Spieler fuer Goldmark BEZAHLT hat - im Markbuch [Schritt 12 Teil F].
     *
     * <p>Plan 8.1: 1.000 Mark = 1 Goldmark, unbegrenzt. Das ist die eine Richtung, die
     * keinen Deckel hat, und sie braucht auch keinen: Sie VERNICHTET Mark.
     *
     * <p><b>Vier Gruende fuer zwei Tauschgaenge, und das ist keine Doppelung.</b> Ein
     * Wechsel bewegt Geld in ZWEI Buechern - Mark hier, Goldmark dort -, und ein Konto
     * traegt nur seine eigenen Zeilen. Dieselbe Bewegung ist im Markbuch eine Ausgabe
     * und im Goldbuch eine Einnahme; mit einem gemeinsamen Grund waere eine der beiden
     * Zeilen falsch gerichtet, und die Summenprobe faende es nicht - sie rechnet je
     * Konto und je Buch.
     */
    WECHSEL_MARK_WEG("wechsel_mark_weg", Richtung.AUSGABE),

    /** Die Goldmark, die aus diesem Tausch kam - im GOLDBUCH. Siehe {@link #WECHSEL_MARK_WEG}. */
    WECHSEL_GOLDMARK_HER("wechsel_goldmark_her", Richtung.EINNAHME),

    /**
     * Die Goldmark, die ein Spieler zurueckgetauscht hat - im GOLDBUCH.
     *
     * <p>Der Rueckweg, und er ist der gedeckelte: Plan 8.1 gibt 1 Goldmark = 800 Mark und
     * hoechstens 10.000 Mark am Tag. Gezaehlt wird der Deckel aber NICHT an diesem Grund,
     * sondern an {@link #WECHSEL_MARK_HER} - der Deckel ist in MARK bemessen.
     */
    WECHSEL_GOLDMARK_WEG("wechsel_goldmark_weg", Richtung.AUSGABE),

    /**
     * Die Mark, die aus dem Rueckweg kam - und <b>der Zeuge des Wechseldeckels</b>.
     *
     * <p>Wie beim Tagesbonus und bei der Ladenmiete ist das BUCHUNGSBUCH die Wahrheit
     * ueber "heute schon": Gefragt wird {@link Kasse#summeSeit} mit DIESEM Grund seit dem
     * Tagesbeginn, und was dort steht, ist der verbrauchte Teil des Deckels. Eine zweite
     * Tabelle daneben waere eine zweite Wahrheit, die nach dem ersten Absturz von der
     * ersten abweicht.
     *
     * <p><b>Darum gibt es {@link #WECHSEL_MARK_ZURUECK} daneben.</b> Eine Erstattung ist
     * auch Mark, die ins Konto kommt - aber sie ist KEIN Wechsel, und mit demselben Grund
     * fuellte sie den Tagesdeckel eines Spielers, dem der Tausch misslungen ist. Wer den
     * Schluessel dieses Grundes aendert, macht jeden heute schon getauschten Betrag
     * unsichtbar: Der Deckel steht dann wieder auf voll, und die Grenze des Plans - 400.000
     * Mark brauchen mindestens 40 Tage - gilt an diesem Tag nicht.
     */
    WECHSEL_MARK_HER("wechsel_mark_her", Richtung.EINNAHME),

    /**
     * Der Kaufpreis kommt zurueck, weil die Goldmark nicht ankam.
     *
     * <p>Dieselbe Antwort wie {@link #PARZELLE_ERSTATTET} und {@link #WOLKE_ERSTATTET},
     * und hier mit einem zweiten Zweck: Dieser Grund ist ausdruecklich NICHT der Zeuge des
     * Wechseldeckels. Stuende hier {@link #WECHSEL_MARK_HER}, verbrauchte eine misslungene
     * Hinrichtung - die gar nicht gedeckelt ist - den Tagesdeckel des Rueckwegs.
     */
    WECHSEL_MARK_ZURUECK("wechsel_mark_zurueck", Richtung.EINNAHME),

    /**
     * Die Goldmark kommt zurueck, weil die Mark nicht ankam - im GOLDBUCH.
     *
     * <p>Die Gegenseite von {@link #WECHSEL_MARK_ZURUECK}, und sie wird aus demselben
     * Grund gebraucht: Abgebucht wird ZUERST - das ist die Reihenfolge, die kein Geld
     * erzeugen kann -, und wenn danach die Gutschrift scheitert, hat der Spieler gezahlt
     * und nichts bekommen.
     */
    WECHSEL_GOLDMARK_ZURUECK("wechsel_goldmark_zurueck", Richtung.EINNAHME),

    /**
     * Ein Posten aus dem Muenzhaus - im GOLDBUCH [Teil G].
     *
     * <p>Der Preis eines Katalogpostens, in Goldmark. Was dafuer kommt, sind
     * GEGENSTAENDE und kein Recht und keine Zahl: Alles andere, was Plan 8.1 als
     * Katalogware nennt - Partikel, Haustier, Rangfarbe, ein weiterer Heimatpunkt -, hat
     * heute keine Mechanik, und ein Posten darauf waere Goldmark fuer etwas, das niemand
     * liest.
     *
     * <p><b>Abgebucht wird erst, wenn der PLATZ sicher ist.</b> Das ist die Reihenfolge
     * des Kistenshops ({@code Shophandel}: erst der Vorrat, dann der Platz, dann das
     * Geld), und sie ist der Grund, warum es neben diesem Grund nur eine Erstattung fuer
     * den Programmfehler gibt und keine fuer den Alltag: Ein voller Rucksack sagt ab,
     * bevor sich etwas bewegt, und hinterlaesst keine zwei Zeilen im Verlauf.
     */
    MUENZHAUS_KAUF("muenzhaus_kauf", Richtung.AUSGABE),

    /**
     * Der Preis kommt zurueck, weil die Lieferung nicht zustandekam - im GOLDBUCH.
     *
     * <p>Dieselbe Antwort wie {@link #WECHSEL_GOLDMARK_ZURUECK}, und sie wird aus
     * demselben Grund gebraucht: Wirft nach dem Abbuchen etwas - die Datenbank, das
     * Inventar -, hat der Spieler gezahlt und nichts bekommen. Goldmark hat genau zwei
     * Quellen (Plan 8.1), Echtgeld und den Tausch aus Mark; sie verschwindet hier also
     * endgueltig, wenn niemand sie zurueckbucht.
     *
     * <p><b>Ein eigener Grund und nicht {@link #MUENZHAUS_KAUF} mit umgedrehtem
     * Vorzeichen</b>: {@link Buchung} wirft, wenn Betrag und Richtung nicht
     * zusammenpassen. Und im Verlauf stuende sonst "Im Muenzhaus gekauft +5 Goldmark" -
     * ein Satz, der das Gegenteil von dem behauptet, was geschehen ist.
     */
    MUENZHAUS_ERSTATTET("muenzhaus_erstattet", Richtung.EINNAHME),

    /**
     * Der Betreiber hat Geld gutgeschrieben - {@code /freimarken geld} mit einer
     * positiven Zahl.
     *
     * <p>Ein EIGENER Grund und nicht einer der vorhandenen geborgt: Mehrere Gruende
     * werden als Marke gelesen ("ist heute schon ein Bonus gezahlt?", "wie viel ist heute
     * an den Bund verkauft?"). Betreibergeld unter {@code tagesbonus} liesse den Bonus
     * ausfallen, unter {@code bundesmarkt_verkauf} fuellte es den Tagesdeckel eines
     * Spielers, der nichts verkauft hat. Und im Wirtschaftsbericht gehoert es ohnehin
     * getrennt: Es ist die eine Geldquelle, die nicht aus dem Spiel kommt.
     */
    BETREIBER_GUTSCHRIFT("betreiber_gutschrift", Richtung.EINNAHME),

    /**
     * Der Betreiber hat Geld abgezogen - {@code /freimarken geld} mit einem Minus.
     *
     * <p>ZWEI Gruende und nicht einer mit Vorzeichen, wie ueberall hier: Die Richtung
     * gehoert an den Grund. Mit einem Grund waeren eine Gutschrift und ein Abzug ueber 0
     * im Buch nicht zu unterscheiden, und {@code /mark verlauf} koennte die Zeile nicht
     * benennen.
     */
    BETREIBER_ABZUG("betreiber_abzug", Richtung.AUSGABE),

    /**
     * Der Betreiber hat GOLDMARK gutgeschrieben - {@code /freimarken goldgeld} mit einer
     * positiven Zahl [23.11.2026].
     *
     * <p><b>EIGENE Gruende und nicht {@link #BETREIBER_GUTSCHRIFT} auf {@link Buch#JEDES}
     * gehoben:</b> {@code JEDES} sagt von sich selbst, es sei "richtig fuer das, was die
     * Kasse selbst tut, und falsch fuer alles, was ein Aufrufer bucht" - und dieser Zweig
     * ist ein Aufrufer. Mit einem Grund fuer beide Buecher waere eine Zeile ueber 1 Mark
     * nicht mehr von einer ueber 1 Goldmark zu unterscheiden, sobald sie ihre Tabelle
     * verlaesst; zum Kurs des Plans ist das der Faktor 800. Und es waere nicht
     * nachholbar: Alte Zeilen im Goldbuch truegen fuer immer den Schluessel
     * {@code betreiber_gutschrift}.
     *
     * <p>Dazu kaeme {@code JEDES} der einzige Riegel abhanden, den es gibt - die
     * Goldkasse ist die EINZIGE Tuer zum Goldbuch, das Markbuch hat bewusst keinen. Mit
     * {@code JEDES} passten die Betreibergruende in beide Buecher, und eine vertauschte
     * Kasse fiele nirgends mehr auf. So ist die Verwechslung ein UEBERSETZUNGSFEHLER.
     *
     * <p>Und dies ist die erste Buchung, die Plan 8.1s zweite Goldmarkquelle - Echtgeld -
     * ueberhaupt in das Buch bringt. Bis heute gab es nur den Tausch aus Mark.
     */
    BETREIBER_GOLDGUTSCHRIFT("betreiber_goldgutschrift", Richtung.EINNAHME),

    /**
     * Der Betreiber hat Goldmark abgezogen - siehe {@link #BETREIBER_GOLDGUTSCHRIFT}.
     *
     * <p>ZWEI Gruende und nicht einer mit Vorzeichen, aus demselben Grund wie bei der
     * Mark: Die Richtung gehoert an den Grund.
     */
    BETREIBER_GOLDABZUG("betreiber_goldabzug", Richtung.AUSGABE),

    /**
     * Ein Los der Lotterie in Altfurt (Plan 14).
     *
     * <p>AUSGABE, auch wenn der Betrag 0 ist - dasselbe wie bei der ersten Parzelle, die
     * ebenfalls gratis ist. Die Richtung gehoert an den Grund, siehe den Klassenkopf.
     *
     * <p><b>Diese Buchung ist NICHT die Wahrheit darueber, wie viele Lose heute schon
     * gezogen wurden</b>, und darin unterscheidet sie sich von {@link #LADEN_LIZENZ} und
     * {@link #TAGESBONUS}, die beide als Marke gelesen werden. Das gratis Los bucht 0
     * Mark; {@link Kasse#summeSeit} bleibt danach 0, und "noch keines gezogen" waere von
     * "das gratis Los ist weg" nicht zu unterscheiden. Auch die Summe hilft nicht: Zwei
     * Lose zu je 500 Mark sehen aus wie eines zu 1000. Die Zahl steht deshalb in
     * {@link Losablage} - sie ist die einzige Stelle, die sie hat.
     */
    LOS_GEZOGEN("los_gezogen", Richtung.AUSGABE),

    /**
     * Der Lospreis ist zurueck - der Gewinn kam nicht an.
     *
     * <p>Ein EIGENER Grund und nicht {@link #LOTTERIE_GEWINN} geborgt, wie bei
     * {@link #MUENZHAUS_ERSTATTET}: Unter dem Gewinngrund saehe der Spieler in seinem
     * Verlauf einen Gewinn, den er nie hatte - und im Wirtschaftsbericht waere die
     * Lotterie als Senke um diesen Betrag zu klein.
     */
    LOS_ERSTATTET("los_erstattet", Richtung.EINNAHME),

    /** Was ein Los in Mark ausgezahlt hat. */
    LOTTERIE_GEWINN("lotterie_gewinn", Richtung.EINNAHME),

    /**
     * Was ein Los in Goldmark ausgezahlt hat.
     *
     * <p>Ein eigener Grund, weil er in ein anderes BUCH gehoert: Die Goldkasse fuehrt
     * ihr eigenes, und {@code pruefeWaehrung} wirft dort fuer jeden Markgrund - auch beim
     * Lesen. Ein gemeinsamer Grund fuer beide Waehrungen waere also nicht nur unscharf im
     * Bericht, sondern ein Wurf mitten im Spiel.
     *
     * <p>Damit hat Goldmark eine DRITTE Quelle neben Kauf und Wechsel. Das ist eine
     * Entscheidung des Betreibers vom 22.11.2026 und mit Absicht knapp gehalten: In der
     * ausgelieferten Tabelle hat die Goldmark Gewicht 3 von 1000 und bringt drei Stueck.
     * Wer sie gross macht, baut einen Weg um den Wechselkurs herum (Plan 9.2).
     */
    LOTTERIE_GOLDGEWINN("lotterie_goldgewinn", Richtung.EINNAHME),

    /**
     * Das Startgeld des Angelwettbewerbs (Plan 14).
     *
     * <p><b>Diese Buchung ist NICHT der Beleg dafuer, dass jemand angemeldet ist</b> -
     * anders als {@link #LADEN_LIZENZ}, die genau das ist. Das Startgeld darf 0 sein,
     * und dann waere die Summe aus {@link Kasse#summeSeit} nicht von "gar nicht
     * angemeldet" zu unterscheiden. Die Anmeldung steht deshalb in
     * {@link Wettbewerbsablage}, und dort liegt auch der Betrag, der WIRKLICH gezahlt
     * wurde - aus ihm wird der Topf gerechnet und nicht aus der Zahl der Teilnehmer.
     */
    WETTBEWERB_START("wettbewerb_start", Richtung.AUSGABE),

    /**
     * Der Preis aus dem Topf des Angelwettbewerbs.
     *
     * <p>Der Topf sind die Startgelder plus der Zuschuss des Betreibers. Ab Werk ist der
     * Zuschuss 0 - dann ist dieser Grund in der Summe genau so gross wie
     * {@link #WETTBEWERB_START}, und der Wettbewerb ist im Wirtschaftsbericht eine
     * Umverteilung und keine Quelle. Die Differenz zwischen beiden Gruenden IST der
     * Zuschuss, und das ist die Zahl, die ein Betreiber im Bericht sehen will.
     */
    WETTBEWERB_PREIS("wettbewerb_preis", Richtung.EINNAHME),

    /**
     * Das Startgeld ist zurueck - die Anmeldung kam nicht zustande.
     *
     * <p>Ein EIGENER Grund und nicht {@link #WETTBEWERB_PREIS} geborgt, aus demselben
     * Grund wie bei {@link #LOS_ERSTATTET}: Unter dem Preisgrund saehe der Spieler einen
     * Gewinn, den er nie hatte - und im Wirtschaftsbericht waere die Differenz zwischen
     * Startgeldern und Preisen nicht mehr der Zuschuss des Betreibers.
     */
    WETTBEWERB_ERSTATTET("wettbewerb_erstattet", Richtung.EINNAHME),

    /**
     * Das STADTRECHT einer Freistadt - einmal gekauft, danach darf dort gehandelt
     * werden [Chat 27.11.2026: "nur in der mall oder in der eigenen stadt mit
     * freihandels lizens / stadtrecht erlaubt (kostet geld)"].
     *
     * <p><b>Der Grund IST der Nachweis</b>, und darum steht das Stadtrecht nicht als
     * Spalte an der Parzelle: Gekauft heisst "es gibt eine Buchung dieses Grundes auf
     * die Stadtkasse", gefragt mit {@code Kasse.summeSeit(kasse, Instant.EPOCH,
     * STADTRECHT)}. Eine eigene Spalte waere ein zweiter Ort fuer dieselbe Tatsache -
     * und sie waere beim Blick ins Buch nicht zu erklaeren, denn dort stuende das Geld
     * ohne Grund.
     *
     * <p>AUSGABE: Die Stadt zahlt. Gebucht wird auf die Stadtkasse und nicht auf den
     * Besitzer - die Stadt kauft das Recht, nicht er. Verkauft er die Stadt, geht es
     * mit; das ist gewollt und der Grund, warum die Buchung an der Kasse haengt.
     */
    STADTRECHT("stadtrecht", Richtung.AUSGABE);

    /** Ob Geld zum Spieler hin oder von ihm weg fliesst. */
    public enum Richtung {
        EINNAHME,
        AUSGABE
    }

    /**
     * In WELCHEM Buch dieser Grund steht - die Frage, die Teil F aufgeworfen hat.
     *
     * <p>Bis zum 19.11.2026 gab es nur ein Buch, und die Frage stellte sich nicht. Seit
     * der Goldmark gibt es zwei ({@code buchungen} und {@code goldbuchungen}), und ein
     * Grund, der im falschen landet, ist der schlimmste Fehler dieser Bauphase: Die Zeile
     * steht da, sie ist richtig gerichtet, die Summe des Kontos stimmt - und sie erzaehlt
     * von einer Waehrung, die dieses Konto nicht fuehrt. Niemand sieht es, bis jemand eine
     * Summe nachrechnet.
     *
     * <p><b>{@link #JEDES} ist der gemessene Fund dieses Teils</b> [19.11.2026]: Die
     * Aufzaehlung hiess zuerst {@code Waehrung} mit zwei Werten, und die erste Probe
     * gegen den Inhalt des Goldbuchs war rot - dort stand eine {@link #EROEFFNUNG}, und
     * die war als Markgrund eingetragen. Sie kommt nicht von einem Aufrufer, sondern von
     * der Kasse SELBST: Jede Umsetzung legt ein Konto mit einer Eroeffnungsbuchung an, in
     * welchem Buch sie auch fuehrt. Ein solcher Grund ist keine Waehrungsentscheidung,
     * und ihn einer Waehrung zuzuschlagen machte die Zusage "jede Zeile dieses Buches
     * traegt einen Grund dieses Buches" unhaltbar - also gibt es den dritten Wert.
     */
    public enum Buch {

        /** Nur im Markbuch - die Waehrung des Alltags. */
        MARK,

        /** Nur im Goldbuch (Plan 8.1). */
        GOLDMARK,

        /**
         * In jedem Buch - was eine Kasse von sich aus schreibt.
         *
         * <p>Sparsam zu vergeben: Jeder Grund hier ist einer, den der Riegel nicht mehr
         * prueft. Er ist richtig fuer das, was die Kasse selbst tut, und falsch fuer
         * alles, was ein Aufrufer bucht.
         */
        JEDES
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

    /**
     * In welchem Buch dieser Grund steht.
     *
     * <p><b>Ein Schalter ohne {@code default} und mit allen Gruenden einzeln</b>, und das
     * ist der ganze Zweck: Ein Schalterausdruck ueber eine Aufzaehlung muss vollstaendig
     * sein, sonst uebersetzt er nicht. Wer einen neuen Grund eintraegt, wird vom
     * Uebersetzer gefragt, in welches Buch er gehoert - und bekommt keine stille Vorgabe,
     * die ihn in das Markbuch legt. Eine Liste der Goldmarkgruende daneben waere genau
     * diese stille Vorgabe gewesen: Wer sie zu ergaenzen vergisst, merkt es nie.
     */
    public Buch buch() {
        return switch (this) {
            case WECHSEL_GOLDMARK_HER, WECHSEL_GOLDMARK_WEG, WECHSEL_GOLDMARK_ZURUECK,
                     MUENZHAUS_KAUF, MUENZHAUS_ERSTATTET, LOTTERIE_GOLDGEWINN,
                     BETREIBER_GOLDGUTSCHRIFT,
                     BETREIBER_GOLDABZUG -> Buch.GOLDMARK;
            case EROEFFNUNG -> Buch.JEDES;
            case BESTAND, PARZELLE_GEKAUFT, PARZELLE_ERSTATTET,
                     FREISTADT_GEGRUENDET, FREISTADT_ERSTATTET, GRUNDSTUECK_GEKAUFT,
                     GRUNDSTUECK_VERKAUFT, GRUNDSTUECK_GEBUEHR, GRUNDSTUECK_ERSTATTET,
                     ZUSAMMENLEGUNG, ZUSAMMENLEGUNG_ERSTATTET, BERUF_VERLASSEN,
                     BERUF_ERSTATTET, BERUF_VERDIENST, BUNDESMARKT_VERKAUF, TAGESBONUS,
                     LADEN_LIZENZ, LADEN_KAUTION, LADEN_KAUTION_ZURUECK, LADEN_MIETE,
                     SHOP_GEKAUFT, SHOP_VERKAUFT, SHOP_GEBUEHR, SHOP_ERSTATTET,
                     LADEN_ERSTATTET, STADTKASSE_GEBUEHR, STADTKASSE_ABGEHOBEN,
                     STADTKASSE_AUSGEZAHLT, STADTKASSE_ZURUECK, FREISTADT_AUFGEGEBEN,
                     FREISTADT_LADENGEBUEHR, STADTKASSE_LADENGEBUEHR, WERDER_GEKAUFT,
                     WERDER_ERSTATTET, WOLKE_GENOMMEN, WOLKE_ERSTATTET, WOLKE_AUFGABE,
                     WOLKE_BIOM, WOLKE_BIOM_ERSTATTET, WECHSEL_MARK_WEG, WECHSEL_MARK_HER,
                     WECHSEL_MARK_ZURUECK, BETREIBER_GUTSCHRIFT, BETREIBER_ABZUG,
                     LOS_GEZOGEN, LOS_ERSTATTET, LOTTERIE_GEWINN,
                     WETTBEWERB_START, WETTBEWERB_PREIS,
                     WETTBEWERB_ERSTATTET, STADTRECHT -> Buch.MARK;
        };
    }

    /**
     * Ob dieser Grund in dieses Buch gehoert.
     *
     * <p>Die Frage, die die zwei Riegel stellen - und sie steht HIER und nicht zweimal
     * dort: {@link Buch#JEDES} passt in beide, und ein Vergleich mit {@code !=} an zwei
     * Stellen haette diesen dritten Wert zweimal uebersehen. Genau das war der Fehler, mit
     * dem dieser Teil anfing.
     */
    public boolean gehoertIn(Buch welches) {
        if (welches == null || welches == Buch.JEDES) {
            throw new IllegalArgumentException("Gefragt wird nach einem bestimmten Buch,"
                    + " nicht nach " + welches);
        }
        return buch() == welches || buch() == Buch.JEDES;
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
