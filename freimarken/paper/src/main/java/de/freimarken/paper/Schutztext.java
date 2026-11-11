package de.freimarken.paper;

import de.freimarken.kern.Freistadtbuch;
import net.kyori.adventure.text.Component;

/**
 * Die vier Wortlaute des Handbalkens der Schutzwache - je einer eine reine Methode.
 *
 * <p><b>Eine eigene Klasse und keine Methoden in {@link Schutzwache}, und das ist
 * gemessen und keine Geschmacksfrage.</b> Der Klasseninitialisierer der Wache baut
 * {@code HEIKLE_GRUPPEN} aus {@code Tag.FLOWER_POTS} und fragt damit
 * {@code Bukkit.server}; eine Probe, die eine reine Methode DORT aufruft, stirbt im
 * {@code ExceptionInInitializerError}, bevor sie ein Zeichen vergleichen kann - es sei
 * denn, ein Stellvertreter steht unter {@code Bukkit.setServer}, und genau den baut
 * seit dem 06.11.2026 {@code Serverprobe} fuer {@code SchutzwacheTest}. Der Grund
 * fuer diese Datei bleibt davon unberuehrt: {@code SendewegTest} 33aj prueft ihr
 * Bild OHNE jeden Server, und das ist der billigere und schaerfere Weg. Genau so
 * lief der erste Versuch am 05.11.2026. Damit folgt dieser Zug dem Muster von
 * {@link Spielzeittext} und {@link Verlaufszeile}: Die Wache entscheidet, diese Klasse
 * baut, und {@code SendewegTest} 33aj liest das Bild ohne Server mit.
 *
 * <p><b>Kein Wort steht hier.</b> Jede Methode holt ihren Satz bei {@link Sprache}; die
 * vier Wortlaute stehen unter {@code wache.schutz} in {@code sprache-vorlage.yml}, der
 * Vertrag dazu in {@link WacheSatz}. Auch gefaerbt wird hier nichts mehr - Farbe und
 * Farbwechsel stehen IM Satz, wie es der Nutzer fuer die ganze Sprachdatei entschieden
 * hat.
 *
 * <p><b>Jede Methode gibt EIN Component und keine Liste, darum {@link Sprache#eineZeile}
 * und nicht {@code bau}:</b> Der Handbalken ueber dem Gegenstandsgurt nimmt ein
 * Component, und eine zweite Zeile verschwaende sich stumm. Dass es wirklich bei einer
 * bleibt, ist schon beim LADEN entschieden ({@link Satz.Art#HANDBALKEN}, geprueft in
 * {@link Sprachliste}) - der Wurf in {@code eineZeile} ist der zweite Riegel und nennt
 * den Pfad, falls doch einmal etwas durchkommt.
 *
 * <p><b>Kein {@code Player} in einer Signatur, und bei
 * {@link #fremdeParzelleBalken} ist das die Sache selbst:</b> Der Name, der dort
 * eingesetzt wird, gehoert dem EIGENTUEMER und nicht dem Leser. Kaeme der Leser hier mit
 * herein, liesse sich beides vertauschen - und ein Besucher laese seinen eigenen Namen
 * und hielte die Parzelle fuer seine. So ist der Tausch nicht moeglich, weil es nichts zu
 * vertauschen gibt; dass die Wache wirklich den Eigentuemer durchgibt, haelt
 * {@code GrenzeTest} 31r fest.
 */
final class Schutztext {

    private Schutztext() {
    }

    /** {@link WacheSatz#SCHUTZ_WEG_UND_MAUER} - er steht auf Weg oder Mauer. */
    static Component wegUndMauerBalken(Sprache sprache) {
        return sprache.eineZeile(WacheSatz.SCHUTZ_WEG_UND_MAUER, Einsatz.keine());
    }

    /** {@link WacheSatz#SCHUTZ_PARZELLE_FREI} - die Parzelle ist zu haben. */
    static Component parzelleFreiBalken(Sprache sprache) {
        return sprache.eineZeile(WacheSatz.SCHUTZ_PARZELLE_FREI, Einsatz.keine());
    }

    /** {@link WacheSatz#SCHUTZ_AUSSERHALB} - Bauland im Raster, aber jenseits der Grenze. */
    static Component ausserhalbBalken(Sprache sprache) {
        return sprache.eineZeile(WacheSatz.SCHUTZ_AUSSERHALB, Einsatz.keine());
    }

    /**
     * {@link WacheSatz#SCHUTZ_FREMDE_PARZELLE} - die Parzelle gehoert einem anderen.
     *
     * <p>Der Name kommt als fertiger Text herein und ist im Satz ein BLATT: Ein Farbtag
     * darin stuende dem Spieler woertlich im Handbalken und wirkte nicht. Dass der Server
     * den Namen nicht immer kennt, aendert daran nichts - dann kommen acht Zeichen einer
     * UUID, und der Satz traegt sie genauso.
     */
    static Component fremdeParzelleBalken(Sprache sprache, String eigentuemer) {
        return sprache.eineZeile(WacheSatz.SCHUTZ_FREMDE_PARZELLE,
                Einsatz.von("eigentuemer", eigentuemer));
    }

    /**
     * Der Laden eines anderen - mit Einheit UND Mieter [Teil C].
     *
     * <p>Wie bei {@link #fremdeParzelleBalken} ist der eingesetzte Name die Sache selbst:
     * Er kommt von einem anderen Spieler, und ein Farbtag darin stuende dem Leser
     * woertlich im Handbalken. Darum geht er als BLATT hinein und nicht in einen Satz.
     */
    static Component fremderLadenBalken(Sprache sprache, String laden, String mieter) {
        return sprache.eineZeile(WacheSatz.SCHUTZ_FREMDER_LADEN,
                Einsatz.von("laden", laden).mit("mieter", mieter));
    }

    /** Ein Stand, den niemand gemietet hat - hier darf AUCH niemand bauen. */
    static Component ladenFreiBalken(Sprache sprache) {
        return sprache.eineZeile(WacheSatz.SCHUTZ_LADEN_FREI, Einsatz.keine());
    }

    /** Die Vertraege sind nicht nachsehbar - dann ist jede andere Auskunft erfunden. */
    static Component ladenGestoertBalken(Sprache sprache) {
        return sprache.eineZeile(WacheSatz.SCHUTZ_LADEN_GESTOERT, Einsatz.keine());
    }

    /**
     * Der Handbalken zu einem Urteil der Stadtwache - ausser bei einer fremden Stadt.
     *
     * <p><b>Ein {@code switch} ueber das Urteil und keine Familienfunktion in
     * {@code Sprachschluessel}</b>, anders als bei den Parzellenbescheiden: Jener Weg ist
     * noetig, wo eine Familie waechst (jeder neue Beruf, jeder neue Buchungsgrund). Die
     * Lagen in einer Stadtfelderwelt sind dagegen von der GEOMETRIE vorgegeben und
     * vollzaehlig - Feld oder Leere, innerhalb oder ausserhalb, vergeben oder frei. Und
     * ein {@code switch} ohne {@code default} ist hier der schaerfere Riegel: Kommt
     * eines Tages ein Urteil dazu, uebersetzt diese Datei nicht mehr, statt still den
     * falschen Balken zu zeigen.
     *
     * <p>{@link Freistadtbuch.Bauurteil#FREMDE_STADT} fehlt mit Absicht: Dieser Balken
     * traegt zwei Werte und braucht einen eigenen Weg -
     * {@link #fremdeStadtBalken}. {@link Freistadtbuch.Bauurteil#ERLAUBT} fehlt, weil es
     * dazu nichts zu sagen gibt: Wer bauen darf, bekommt keinen Handbalken.
     */
    static Component stadtBalken(Sprache sprache, Freistadtbuch.Bauurteil urteil) {
        WacheSatz satz = switch (urteil) {
            case LEERE -> WacheSatz.SCHUTZ_STADT_LEERE;
            case AUSSERHALB -> WacheSatz.SCHUTZ_STADT_AUSSERHALB;
            case ANKUNFTSFELD -> WacheSatz.SCHUTZ_ANKUNFTSFELD;
            case FREIES_FELD -> WacheSatz.SCHUTZ_STADT_FREI;
            case ERLAUBT, FREMDE_STADT -> throw new IllegalArgumentException(
                    "Fuer " + urteil + " gibt es hier keinen Balken - ERLAUBT braucht"
                            + " keinen, FREMDE_STADT geht ueber fremdeStadtBalken");
        };
        return sprache.eineZeile(satz, Einsatz.keine());
    }

    /**
     * Die Freistadt eines anderen - mit ihrem Namen und ihrem Eigentuemer.
     *
     * <p>Beide kommen als fertiger Text herein und sind im Satz BLAETTER, aus demselben
     * Grund wie bei {@link #fremdeParzelleBalken}: Ein Farbtag darin stuende dem Leser
     * woertlich im Handbalken. Beim STADTNAMEN ist das mehr als Vorsicht - er ist
     * Spielereingabe, und {@code Stadtname} laesst ohnehin nur Kleinbuchstaben, Ziffern
     * und Bindestriche durch.
     */
    static Component fremdeStadtBalken(Sprache sprache, String stadt, String eigentuemer) {
        return sprache.eineZeile(WacheSatz.SCHUTZ_FREMDE_STADT,
                Einsatz.von("stadt", stadt).mit("eigentuemer", eigentuemer));
    }

    /** Die Freistaedte sind nicht nachsehbar - wie {@link #ladenGestoertBalken}. */
    static Component stadtGestoertBalken(Sprache sprache) {
        return sprache.eineZeile(WacheSatz.SCHUTZ_STADT_GESTOERT, Einsatz.keine());
    }
}
