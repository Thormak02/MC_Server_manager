package de.freimarken.paper;

import java.text.NumberFormat;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.logging.Logger;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.TextReplacementConfig;
import net.kyori.adventure.text.format.Style;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.kyori.adventure.text.minimessage.tag.standard.StandardTags;
import org.bukkit.entity.Player;

/**
 * Der eine Ausgang nach Adventure - hier und nur hier wird aus Schluessel und Werten ein
 * Component.
 *
 * <p>Genau EIN Feld, und das ist der {@link Sprachstand}. Kein {@code boolean} im
 * Konstruktor, kein veraenderliches Feld, kein Zwischenspeicher: Was neuladbar ist, wird
 * beim GEBRAUCH am Halter gefragt und nie beim Bauen festgeschrieben. Das ist dieselbe
 * Regel, die {@code Wirtschaftsstand} fuer die Preise traegt, und
 * {@code SpracheTest.keinZwischenspeicherFuerZahlen} sichert sie zu.
 *
 * <p><b>Die Reihenfolge ist parsen, dann ersetzen - und das ist keine Geschmacksfrage.</b>
 * Gemessen an Adventure 5.2.0: MiniMessage liest den WORTLAUT (nie einen Wert),
 * {@code replaceText} ersetzt danach in EINEM Baumdurchlauf alle Marken durch BLAETTER. Ein
 * eingesetzter Wert wird damit von keinem weiteren Lauf mehr angesehen, und eine
 * geschweifte oder spitze Klammer darin kommt zeichengleich beim Spieler an. Der
 * Gegenbeweis ist ebenfalls gemessen: Wuerde der Wert erst in die Zeichenkette gespleisst
 * und dann geparst, kaeme ein {@code <red>} aus einer Spielereingabe als wirklich rot beim
 * Spieler an - eine Textfaelschung aus einer Datenbankmeldung.
 */
final class Sprache {

    /**
     * Der Wortvorrat des Lesers - {@code color()} und {@code decorations()}, nicht mehr.
     *
     * <p>Gemessen: Mit dem VOLLEN Vorrat erzeugt
     * {@code <click:run_command:'/op du'>hier</click>} einen anklickbaren Satz, der im
     * NAMEN DES SPIELERS laeuft. Mit diesem Vorrat bleibt er Text, und
     * {@link Sprachliste#tagklage} weist ihn beim Laden ab. Hover und Klick bleiben
     * moeglich - spaeter, mit einem eigenen Tag und einer eigenen Pruefung, nicht als
     * Nebenwirkung.
     *
     * <p>Derselbe Vorrat beantwortet beide Tore: {@code tagklage} fragt ihn mit
     * {@code has(name)}, und hier wird mit ihm geparst. Zwei Tore mit zwei Wortschaetzen
     * waeren ein Fehler fuer sich.
     */
    static final TagResolver VORRAT = TagResolver.builder()
            .resolver(StandardTags.color())
            .resolver(StandardTags.decorations())
            .build();

    /**
     * Unveraenderlich und faden-sicher, also {@code static final} und einmal gebaut.
     *
     * <p>{@code strict(true)}: Ein nicht geschlossenes Tag ist damit beim LADEN eine Klage
     * mit Zeile und Spalte. Dass ein falsch GESCHRIEBENES Tag trotzdem durchgeht, ist
     * gemessen - dagegen steht {@link Sprachliste#tagklage}.
     */
    static final MiniMessage MINIMESSAGE = MiniMessage.builder()
            .strict(true)
            .tags(VORRAT)
            .build();

    /**
     * Nur fuer den einen Fall, den ein Sendeort nicht beantworten kann: ein vergessener
     * Wert.
     *
     * <p>{@code static}, also kein Feld dieses Objekts - die Zusage "genau ein Feld" gilt
     * fuer die Exemplare. Ein Protokoll mitzureichen hiesse, es durch elf Konstruktoren zu
     * fuehren, und die Meldung gehoert in dieselbe Zeile wie der Pfad.
     */
    private static final Logger PROTOKOLL = Logger.getLogger(Sprache.class.getName());

    private final Sprachstand stand;

    Sprache(Sprachstand stand) {
        if (stand == null) {
            throw new IllegalArgumentException("Ohne Halter gibt es keinen Satz zu holen");
        }
        this.stand = stand;
    }

    // --- Senden ----------------------------------------------------------------

    void sende(Audience an, Satz schluessel, Einsatz werte) {
        bau(schluessel, 1L, werte).forEach(an::sendMessage);
    }

    void sendeGezaehlt(Audience an, Satz schluessel, long anzahl, Einsatz werte) {
        bau(schluessel, anzahl, werte).forEach(an::sendMessage);
    }

    /**
     * Der Handbalken ueber dem Gegenstandsgurt - genau eine Zeile.
     *
     * <p>{@code sendActionBar} nimmt ein Component und keine Liste. Dass der Satz nur
     * eine Zeile ergibt, ist beim LADEN entschieden ({@code Art.HANDBALKEN}) - hier
     * kann es also nicht mehr schiefgehen.
     *
     * <p><b>Ohne Aufrufer, und das ist nach Bauschritt 6 kein Versehen mehr:</b> Die
     * {@code Schutzwache} sendet ihren Handbalken weiter selbst und holt sich die
     * fertige Zeile bei {@link #eineZeile} - so bleibt sie in der Tafel
     * {@code GrenzeTest.SENDESTELLEN} zaehlbar, und in {@code grund} steht kein
     * {@code Player} neben dem Namen des Eigentuemers. Dieser Weg hier bleibt fuer
     * einen Sendeort, der nichts weiter zu entscheiden hat.
     */
    void sendeHandbalken(Player an, Satz schluessel, Einsatz werte) {
        an.sendActionBar(eineZeile(schluessel, werte));
    }

    // --- Bauen -----------------------------------------------------------------

    /** Der EINE Ort, an dem aus Schluessel und Werten Components werden. Je Umbruch eine Zeile. */
    List<Component> bau(Satz schluessel, long anzahl, Einsatz werte) {
        String wortlaut = stand.buch().wortlaut(schluessel, anzahl);
        try {
            return teile(ersetze(schluessel, wortlaut, werte));
        } catch (IllegalStateException fehlt) {
            // Nicht abbrechen: Der Spieler sieht {z} und weiss, dass etwas kaputt ist; bei
            // einem abgebrochenen Befehl wuesste er nur, dass nichts geschieht. Dieselbe
            // Abwaegung wie im Javadoc von Spielzeittext.dauer - ein Name, der einen
            // Umzug uebersteht, anders als die Zeilennummern, die hier vor dem Umbau
            // von /spielzeit standen und seitdem auf eine Rechenschleife zeigten.
            PROTOKOLL.severe(fehlt.getMessage() + " - gesendet wird der Satz mit"
                    + " sichtbaren Klammern. Ein abgebrochener Befehl liesse den Spieler"
                    + " ratlos; eine sichtbare Klammer sagt ihm, dass etwas kaputt ist.");
            return teile(MINIMESSAGE.deserialize(wortlaut));
        }
    }

    /**
     * Genau ein Component - fuer jeden Ort, der keine Liste senden kann.
     *
     * <p>Wirft, wenn der Satz mehr als eine Zeile ergibt. Fuer {@code Art.HANDBALKEN} ist
     * das beim Laden ausgeschlossen; fuer einen {@code Art.SATZ}, der in einer Zeile
     * gebraucht wird, ist es die eine Stelle, an der ein Betreiber mit einer Umbruchmarke
     * etwas kaputt machen koennte - und dann sagt der Wurf, welcher Satz es war.
     */
    Component eineZeile(Satz schluessel, Einsatz werte) {
        List<Component> zeilen = bau(schluessel, 1L, werte);
        if (zeilen.size() != 1) {
            throw new IllegalStateException(schluessel.pfad() + " ergibt " + zeilen.size()
                    + " Zeilen, gebraucht wird genau eine. An dieser Stelle kann nur eine"
                    + " Zeile gesendet werden - nimm die Umbruchmarke heraus.");
        }
        return zeilen.get(0);
    }

    /** Ein {@code Art.WORT} als Wert fuer einen anderen Satz - ohne Farbe, ohne Component. */
    String wort(Satz schluessel, Einsatz werte) {
        if (schluessel.art() != Satz.Art.WORT) {
            throw new IllegalArgumentException(schluessel.pfad() + " ist "
                    + schluessel.art() + " und kein WORT. Nur ein WORT darf als Wert in"
                    + " einen anderen Satz - es traegt kein Farbtag, weil ein eingesetzter"
                    + " Wert ein Blatt ist.");
        }
        String wortlaut = stand.buch().wortlaut(schluessel);
        StringBuilder gebaut = new StringBuilder();
        var treffer = Sprachliste.MARKE.matcher(wortlaut);
        int zuletzt = 0;
        while (treffer.find()) {
            gebaut.append(wortlaut, zuletzt, treffer.start());
            String wert = werte.werte().get(treffer.group(1));
            if (wert == null) {
                PROTOKOLL.severe(schluessel.pfad() + " braucht {" + treffer.group(1)
                        + "} - gesendet wird der Wert mit sichtbaren Klammern.");
                gebaut.append(treffer.group());
            } else {
                gebaut.append(wert);
            }
            zuletzt = treffer.end();
        }
        gebaut.append(wortlaut, zuletzt, wortlaut.length());
        return gebaut.toString();
    }

    /** Zum Beispiel {@code "400.000 Mark"}: Wort aus der Datei, Tausenderpunkt aus dem Programm. */
    String betrag(long mark) {
        return wort(MarkSatz.WAEHRUNG_BETRAG, Einsatz.von("zahl", mark));
    }

    /** Nur die Zahl, ohne Waehrung. */
    String zahl(long wert) {
        return deutscheZahl(wert);
    }

    DateTimeFormatter muster(Satz schluessel) {
        return stand.buch().muster(schluessel);
    }

    /**
     * Die EINE Zahlform des Plugins - deutsche Tausenderpunkte.
     *
     * <p><b>Ein neues {@link NumberFormat} JE AUFRUF, und das steht so schon in
     * {@code Betraege.java:13-18}:</b> "Nicht nebenlaeufig benutzbar - NumberFormat ist das
     * nie. Alles hier laeuft im Serverhauptfaden, und wenn das einmal nicht mehr stimmt,
     * gehoert hier ein neues Exemplar je Aufruf her." Es stimmt nicht mehr: Die
     * Vervollstaendigung von Befehlen laeuft ausdruecklich nebenlaeufig
     * ({@code Wirtschaftsstand}, Javadoc 17-25), und {@code ParzellenBefehle} hat schon
     * {@code suggests()}. Sobald eine Vervollstaendigung einen Preis anzeigt, formatieren
     * zwei Faeden ueber dasselbe Objekt - und ein geteiltes {@code DecimalFormat} liefert
     * dann nicht einen falschen Betrag, sondern Schrott wie {@code "1.0001.000"}.
     *
     * <p>Der Preis: ein kleines Objekt je formatiertem Betrag. Messbar nur in einer
     * Schleife, und Chatzeilen entstehen im Menschentakt.
     *
     * <p>{@code Locale.GERMANY} bleibt im PROGRAMM. Eine Einstellzeile dazu waere eine
     * Zusage, die keine Probe halten kann: {@code Locale.forLanguageTag} wirft nie, es
     * verwirft eine fehlerhafte Eingabe stumm und liefert {@code ROOT} - und dann lautete
     * jeder Betrag im ganzen Plugin still {@code 25,000}.
     */
    static String deutscheZahl(long wert) {
        return NumberFormat.getIntegerInstance(Locale.GERMANY).format(wert);
    }

    // --- Der Weg vom Wortlaut zum Component ------------------------------------

    private static Component ersetze(Satz schluessel, String wortlaut, Einsatz werte) {
        Component roh = MINIMESSAGE.deserialize(wortlaut);
        return roh.replaceText(TextReplacementConfig.builder()
                .match(Sprachliste.MARKE.pattern())
                .replacement((treffer, bauer) -> {
                    String name = treffer.group(1);
                    String wert = werte.werte().get(name);
                    if (wert == null) {
                        throw new IllegalStateException(schluessel.pfad() + " braucht {"
                                + name + "}");
                    }
                    // Ein BLATT und kein Text: So sieht kein weiterer Lauf den Wert an.
                    return Component.text(wert);
                })
                .build());
    }

    /**
     * Teilt ein Component an jeder Umbruchmarke in eigene Zeilen.
     *
     * <p>Gebraucht wird das, weil der Server die Chatbreite des Clients nicht kennt und
     * also nicht selbst brechen kann - die Zeilenstruktur bleibt damit das, was sie heute
     * ist: eine Entscheidung des Autors, jetzt des Betreibers.
     *
     * <p>Geteilt wird am BAUM und nicht am Wortlaut, und das ist der Grund: Teilte man
     * {@code "<gray>Erste\nZweite</gray>"} vorher in zwei Zeichenketten, waere die erste
     * ein offenes Tag (strict wirft) und die zweite verlore ihre Farbe. Also wird einmal
     * geparst, dann der Baum flach gemacht - jedes Stueck mit seinem wirkenden Stil - und
     * daraus je Zeile wieder ein Component gebaut. Die Form des Baums aendert sich dabei,
     * das BILD nicht: Genau dafuer vergleicht {@code BildGleichTest} die
     * Alt-Serialisierung und nicht {@code Component.equals}.
     */
    private static List<Component> teile(Component ganz) {
        List<Stueck> stuecke = new ArrayList<>();
        flach(ganz, Style.empty(), stuecke);
        List<Component> zeilen = new ArrayList<>();
        List<Stueck> zeile = new ArrayList<>();
        for (Stueck stueck : stuecke) {
            String[] teile = stueck.text().split("\n", -1);
            for (int i = 0; i < teile.length; i++) {
                if (i > 0) {
                    zeilen.add(zusammen(zeile));
                    zeile.clear();
                }
                if (!teile[i].isEmpty()) {
                    zeile.add(new Stueck(teile[i], stueck.stil()));
                }
            }
        }
        zeilen.add(zusammen(zeile));
        return List.copyOf(zeilen);
    }

    /** Ein Textstueck mit dem Stil, der auf es wirkt - Elternstil schon eingerechnet. */
    private record Stueck(String text, Style stil) {
    }

    private static void flach(Component knoten, Style vonOben, List<Stueck> hinein) {
        // Der eigene Stil gewinnt, der des Elternteils fuellt die Luecken - genau so, wie
        // Minecraft es anzeigt.
        Style wirkend = knoten.style().merge(vonOben,
                Style.Merge.Strategy.IF_ABSENT_ON_TARGET);
        if (knoten instanceof TextComponent text && !text.content().isEmpty()) {
            hinein.add(new Stueck(text.content(), wirkend));
        }
        for (Component kind : knoten.children()) {
            flach(kind, wirkend, hinein);
        }
    }

    /**
     * Eine Zeile aus ihren Stuecken - das erste als Wurzel, die uebrigen angehaengt.
     *
     * <p>Dieselbe Form, die die heutigen Sendestellen bauen
     * ({@code Component.text(a, GRAY).append(Component.text(b, GOLD))}), damit die
     * Alt-Serialisierung zeichengleich ausfaellt. Jedes Stueck traegt seinen vollen Stil,
     * also kann ein angehaengtes nichts vom ersten erben, was es nicht selbst hat.
     */
    private static Component zusammen(List<Stueck> stuecke) {
        if (stuecke.isEmpty()) {
            return Component.empty();
        }
        Component gebaut = Component.text(stuecke.get(0).text())
                .style(stuecke.get(0).stil());
        for (int i = 1; i < stuecke.size(); i++) {
            gebaut = gebaut.append(Component.text(stuecke.get(i).text())
                    .style(stuecke.get(i).stil()));
        }
        return gebaut;
    }
}
