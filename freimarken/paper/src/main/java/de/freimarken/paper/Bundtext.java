package de.freimarken.paper;

import java.util.ArrayList;
import java.util.List;
import net.kyori.adventure.text.Component;

/**
 * Die neun Wortlaute von {@code /freimarken} und {@code /freimarken preis} - je Zweig
 * eine reine Methode.
 *
 * <p><b>Eine eigene Klasse und keine Methoden in {@link FreimarkenPlugin}, und der Grund
 * ist eine Probe und kein Geschmack.</b> Jene Datei steht in
 * {@code GrenzeTest.FARBE_ERLAUBT}, denn ihre Betreiber- und Konsolenzeilen nennen ihre
 * Farbe weiter selbst - das Javadoc jener Menge nennt sie darum ein Loch, das offen
 * bleibt. Wer einen Spielersatz DORT neu schreibt, laeuft an Probe 31 vorbei. Diese Datei
 * steht in keiner der drei Erlaubnismengen ({@code FARBE_ERLAUBT},
 * {@code MINIMESSAGE_ERLAUBT}, {@code FARBTAG_ERLAUBT}) und ist damit ab der ersten Zeile
 * bewacht: kein Farbname, kein Farbtag als Zeichen, kein selbst gelesener Satz. Dass die
 * Frage, ob eine reine Methode in einem {@code JavaPlugin} ohne Server ueberhaupt
 * aufrufbar waere, damit gar nicht gestellt werden muss, ist der zweite Gewinn - bei
 * {@link Schutztext} war sie mit Nein beantwortet worden.
 *
 * <p><b>Kein Wort steht hier.</b> Jede Methode holt ihren Satz bei {@link Sprache}; die
 * neun Wortlaute stehen unter {@code stand} und {@code preis} in
 * {@code sprache-vorlage.yml}, der Vertrag dazu in {@link BundSatz}. Auch gefaerbt wird
 * hier nichts - Farbe und Farbwechsel stehen IM Satz, wie es der Nutzer fuer die ganze
 * Sprachdatei entschieden hat. {@code preis.kostet} wechselt die Farbe mitten im Satz,
 * und das ist eine Zeile der Datei und keine Kette von {@code append} mehr.
 *
 * <p><b>Die Zweigwahl liegt HIER und nicht beim Sendeort</b>, und darum kommen die zwei
 * Wahrheitswerte als Parameter herein: Die Ablage ist fluechtig oder eine Datenbank, und
 * der Leser darf verwalten oder nicht. So prueft {@code SendewegTest} jede Lage als Bild,
 * ohne Server und ohne Spieler. Dasselbe Muster traegt {@code Spielzeittext.werte} mit
 * seinem {@code boolean gesperrt}.
 *
 * <p><b>{@link Sprache#eineZeile} je Satz und nicht {@code bau}:</b> Jede Zeile ist ein
 * eigener Schluessel, der Betreiber faerbt jede fuer sich, und ein versehentlich
 * eingesetzter Umbruch nennt beim Wurf seinen Pfad. Eine Spalte, die ueber zwei Zeilen
 * laufen muesste, gibt es in diesen neun Saetzen nicht.
 */
final class Bundtext {

    private Bundtext() {
    }

    /**
     * Die Antwort auf das nackte {@code /freimarken} - vier oder fuenf Zeilen.
     *
     * @param version      der Stand des Plugins, aus {@code getPluginMeta()}
     * @param javaFassung  die Hauptnummer der Java-Ausgabe, aus {@code Runtime.version()}
     * @param minecraft    die Fassung des Servers, aus {@code getServer()}
     * @param fluechtig    ob die Ablage den Neustart nicht uebersteht - entscheidet
     *                     zwischen {@link BundSatz#STAND_ABLAGE_FLUECHTIG} und
     *                     {@link BundSatz#STAND_ABLAGE_MARIADB}
     * @param darfVerwalten ob der Leser {@code freimarken.verwalten} hat; nur dann kommt
     *                     die fuenfte Zeile dazu
     */
    static List<Component> standZeilen(Sprache sprache, String version, String javaFassung,
                                       String minecraft, boolean fluechtig,
                                       boolean darfVerwalten) {
        List<Component> zeilen = new ArrayList<>();
        zeilen.add(sprache.eineZeile(BundSatz.STAND_KOPF, Einsatz.keine()));
        zeilen.add(sprache.eineZeile(BundSatz.STAND_VERSION, Einsatz
                .von("version", version)
                .mit("java", javaFassung)
                .mit("minecraft", minecraft)));
        zeilen.add(sprache.eineZeile(fluechtig
                ? BundSatz.STAND_ABLAGE_FLUECHTIG
                : BundSatz.STAND_ABLAGE_MARIADB, Einsatz.keine()));
        zeilen.add(sprache.eineZeile(BundSatz.STAND_BEFEHLE, Einsatz.keine()));
        if (darfVerwalten) {
            zeilen.add(sprache.eineZeile(BundSatz.STAND_BEFEHLE_VERWALTUNG,
                    Einsatz.keine()));
        }
        return List.copyOf(zeilen);
    }

    /**
     * Die Antwort auf {@code /freimarken preis <anzahl>} - immer zwei Zeilen.
     *
     * <p>Die Nummer geht als fertiger Text hinein und nicht als {@code long}: Die
     * Zahlform von {@link Einsatz} setzt deutsche Tausenderpunkte, und die naechste
     * Parzelle ist eine nackte Zahl bis 65. Die zwei BETRAEGE gehen durch
     * {@link Sprache#betrag} - das ist seit Bauschritt 8 der einzige Weg zum Geldwort.
     *
     * @param besessen wie viele Parzellen der Spieler schon hat; die genannte Nummer ist
     *                 die naechste
     * @param preis    was die naechste kostet; {@code 0} ist die gratis-Lage
     * @param gesamt   was die bisherigen zusammen gekostet haben
     */
    static List<Component> preisZeilen(Sprache sprache, int besessen, long preis,
                                       long gesamt) {
        Component erste = preis == 0L
                ? sprache.eineZeile(BundSatz.PREIS_GRATIS, Einsatz.keine())
                : sprache.eineZeile(BundSatz.PREIS_KOSTET, Einsatz
                        .von("nummer", String.valueOf(besessen + 1))
                        .mit("betrag", sprache.betrag(preis)));
        return List.of(erste,
                sprache.eineZeile(BundSatz.PREIS_AUSGEGEBEN,
                        Einsatz.von("betrag", sprache.betrag(gesamt))));
    }
}
