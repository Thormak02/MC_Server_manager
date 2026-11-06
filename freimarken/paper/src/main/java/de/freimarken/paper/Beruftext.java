package de.freimarken.paper;

import de.freimarken.kern.Berufsregel;
import de.freimarken.kern.Berufsstand;
import de.freimarken.kern.Berufsverzeichnis;
import de.freimarken.kern.Berufswerte;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import net.kyori.adventure.text.Component;

/**
 * Jede Zeile, die {@code /beruf} ausgibt - ohne Spieler, ohne Bukkit, ohne Senden.
 *
 * <p>Die Naht dieses Bauschrittes: Jede Verzweigung von {@link BerufeBefehle} holt ihre
 * Zeilen hier und schickt sie nur noch hinaus. Derselbe Griff wie bei
 * {@link Spielzeittext} und {@link Bundtext} und aus demselben Grund - so ist jeder Satz
 * ohne Server pruefbar, und eine Bildprobe haelt ihn zeichengenau fest.
 *
 * <p><b>Die Namen der Berufe kommen aus der Sprachdatei</b>, ueber {@link Berufsname}. Der
 * Schluessel aus der {@code config.yml} erscheint nur an zwei Stellen in einem Satz: in
 * der Befehlszeile zum Abschreiben ({@code /beruf verlassen bergmann bestaetigen}) und in
 * der Absage auf einen Beruf, den es nicht gibt - dort gibt es kein Wort dafuer.
 *
 * <p><b>Die Dauer kommt aus {@link Spielzeittext#dauer}</b> und nicht aus einer eigenen
 * Rechnung. Zwei Rechnungen fuer dieselbe Frist liefen eines Tages auseinander, und dann
 * liest ein Spieler an einer Stelle "2 h 05 min" und an der anderen "2 Stunden".
 */
final class Beruftext {

    private Beruftext() {
    }

    /**
     * Die Liste aller Berufe - {@code /beruf liste}.
     *
     * <p>Ein Grundberuf je Zeile, seine Spezialisierungen dahinter: Das ist der Aufbau der
     * {@code config.yml}, und wer dort etwas sucht, soll es hier in derselben Ordnung
     * finden. Die Reihenfolge ist die der Datei und nicht die des Alphabets - der
     * Betreiber hat sortiert.
     */
    static List<Component> listeZeilen(Sprache sprache, Berufsverzeichnis verzeichnis) {
        List<Component> zeilen = new ArrayList<>(
                sprache.bau(BerufSatz.LISTE_KOPF, 1L, Einsatz.keine()));
        if (verzeichnis.berufe().isEmpty()) {
            zeilen.addAll(sprache.bau(BerufSatz.LISTE_LEER, 1L, Einsatz.keine()));
            return List.copyOf(zeilen);
        }
        for (String grundberuf : verzeichnis.grundberufe()) {
            List<String> dazu = verzeichnis.spezialisierungenZu(grundberuf);
            if (dazu.isEmpty()) {
                zeilen.addAll(sprache.bau(BerufSatz.LISTE_BERUF, 1L,
                        Einsatz.von("name", name(sprache, grundberuf))));
                continue;
            }
            zeilen.addAll(sprache.bau(BerufSatz.LISTE_BERUF_MIT, 1L,
                    Einsatz.von("name", name(sprache, grundberuf))
                            .mit("spezialisierungen", aufgezaehlt(sprache, dazu))));
        }
        zeilen.addAll(sprache.bau(BerufSatz.LISTE_FUSS, 1L, Einsatz.keine()));
        return List.copyOf(zeilen);
    }

    /**
     * Der eigene Stand - {@code /beruf stand}.
     *
     * <p>Die Restfrist kommt als Parameter und wird hier nicht gerechnet: Sie steht in
     * {@link Berufsregel#restfrist}, und dieselbe Rechnung urteilt auch ueber den Antrag.
     * Zwei Rechnungen liefen eines Tages auseinander, und der Spieler las eine Zahl, nach
     * der er trotzdem abgewiesen wird.
     *
     * <p>Die Zeile zur Frist steht nur da, wenn eine laeuft - eine Zeile "in 0 min" waere
     * eine Warnung vor nichts. Die Zeile zur Gebuehr steht immer: Sie soll nicht erst in
     * der Absage auftauchen.
     */
    static List<Component> standZeilen(Sprache sprache, Berufsverzeichnis verzeichnis,
                                       Berufswerte werte, Berufsstand stand,
                                       Duration restfrist) {
        List<Component> zeilen = new ArrayList<>(
                sprache.bau(BerufSatz.STAND_KOPF, 1L, Einsatz.keine()));
        if (stand.berufe().isEmpty() && stand.nochNieGewaehlt()) {
            zeilen.addAll(sprache.bau(BerufSatz.STAND_KEINER, 1L, Einsatz.keine()));
        }
        for (String beruf : stand.berufe()) {
            zeilen.addAll(verzeichnis.istSpezialisierung(beruf)
                    ? sprache.bau(BerufSatz.STAND_SPEZIALISIERUNG, 1L,
                            Einsatz.von("name", name(sprache, beruf))
                                    .mit("grundberuf", name(sprache,
                                            verzeichnis.grundberufVon(beruf).orElse(beruf))))
                    : sprache.bau(BerufSatz.STAND_GRUNDBERUF, 1L,
                            Einsatz.von("name", name(sprache, beruf))));
        }
        zeilen.addAll(sprache.bau(BerufSatz.STAND_PLAETZE_GRUNDBERUFE, 1L,
                plaetze(verzeichnis, stand, false, werte.grundberufe())));
        zeilen.addAll(sprache.bau(BerufSatz.STAND_PLAETZE_SPEZIALISIERUNGEN, 1L,
                plaetze(verzeichnis, stand, true, werte.spezialisierungen())));
        if (!restfrist.isZero()) {
            zeilen.addAll(sprache.bau(BerufSatz.STAND_FRIST, 1L,
                    Einsatz.von("dauer", Spielzeittext.dauer(sprache, restfrist))));
        }
        zeilen.addAll(sprache.bau(BerufSatz.STAND_GEBUEHR, 1L,
                Einsatz.von("betrag", sprache.betrag(werte.wechselgebuehr()))));
        return List.copyOf(zeilen);
    }

    /** Gewaehlt - eine Zeile, kein Betrag. Waehlen ist gratis. */
    static List<Component> gewaehltZeilen(Sprache sprache, String beruf) {
        return sprache.bau(BerufSatz.GEWAEHLT, 1L,
                Einsatz.von("name", name(sprache, beruf)));
    }

    /** Abgelegt - mit dem Betrag, der dafuer gebucht wurde. */
    static List<Component> verlassenZeilen(Sprache sprache, String beruf, long kosten) {
        return sprache.bau(BerufSatz.VERLASSEN, 1L,
                Einsatz.von("name", name(sprache, beruf))
                        .mit("betrag", sprache.betrag(kosten)));
    }

    /**
     * Die Rueckfrage vor dem Ablegen - wie bei {@code /parzelle aufgeben}.
     *
     * <p>Der Beruf kommt ZWEIMAL in den Satz: als Wort fuer den Text und als Schluessel
     * fuer die Befehlszeile zum Abschreiben. Mit nur einem von beiden waere entweder die
     * Befehlszeile falsch oder der Text technisch.
     */
    static List<Component> warnungZeilen(Sprache sprache, String beruf, long kosten,
                                         Duration sperrfrist) {
        return sprache.bau(BerufSatz.VERLASSEN_WARNUNG, 1L,
                Einsatz.von("name", name(sprache, beruf))
                        .mit("schluessel", beruf)
                        .mit("betrag", sprache.betrag(kosten))
                        .mit("dauer", Spielzeittext.dauer(sprache, sperrfrist)));
    }

    /**
     * Die Absage - EINE Stelle, die aus einem Ausgang einen Satz macht.
     *
     * <p><b>Ein {@code switch} ueber die Aufzaehlung und keine Zuordnungstabelle</b>, und
     * das ist der Grund: Ein {@code switch} ohne {@code default} muss vollstaendig sein.
     * Kommt in {@link Berufsregel.Ausgang} ein Fall hinzu, bricht hier der BAU - und nicht
     * das Spiel. Eine Tabelle lieferte {@code null}, und der Spieler las nichts.
     *
     * <p>Dass diese Zuordnung nicht in {@link Sprachschluessel} steht wie {@code grund}
     * und {@code bescheid}, hat denselben Grund von der anderen Seite: Jene werden an
     * mehreren Sendeorten gebraucht, diese an genau einem. Hier genannt findet die
     * Textsuche von {@code GrenzeTest} jede dieser elf Konstanten - in
     * {@code Sprachschluessel} genannt waere sie selbstbescheinigend und braeuchte einen
     * Eintrag in {@code familienDeckung}.
     *
     * @param guthaben was in seiner Kasse steht - nur fuer die EINE Absage, die es nennt
     */
    static List<Component> absageZeilen(Sprache sprache, Berufsregel.Urteil urteil,
                                        long guthaben) {
        return switch (urteil.ausgang()) {
            case ANGENOMMEN -> throw new IllegalArgumentException(
                    "Ein angenommenes Urteil hat keine Absage: " + urteil.beruf()
                            + ". Wer hier landet, hat den Zweig verwechselt.");
            // Die ROHE Eingabe und kein Name: Es gibt den Beruf nicht, also gibt es auch
            // kein Wort dafuer in der Sprachdatei.
            case UNBEKANNTER_BERUF -> sprache.bau(BerufSatz.ABSAGE_UNBEKANNT, 1L,
                    Einsatz.von("eingabe", urteil.beruf()));
            case SCHON_GEHALTEN -> mitNamen(sprache, BerufSatz.ABSAGE_SCHON_GEHALTEN,
                    urteil.beruf());
            case NICHT_GEHALTEN -> mitNamen(sprache, BerufSatz.ABSAGE_NICHT_GEHALTEN,
                    urteil.beruf());
            case GRUNDBERUFE_VOLL -> sprache.bau(BerufSatz.ABSAGE_GRUNDBERUFE_VOLL, 1L,
                    Einsatz.von("erlaubt", String.valueOf(urteil.erlaubt())));
            case SPEZIALISIERUNG_VOLL -> sprache.bau(
                    BerufSatz.ABSAGE_SPEZIALISIERUNG_VOLL, 1L,
                    Einsatz.von("erlaubt", String.valueOf(urteil.erlaubt())));
            case SPEZIALISIERUNG_OHNE_RECHT -> sprache.bau(BerufSatz.ABSAGE_OHNE_RECHT,
                    1L, Einsatz.keine());
            // Der GEBRAUCHTE Grundberuf steht im Urteil, nicht der gewuenschte Beruf.
            case GRUNDBERUF_FEHLT -> mitNamen(sprache, BerufSatz.ABSAGE_GRUNDBERUF_FEHLT,
                    urteil.beruf());
            // Und hier die SPEZIALISIERUNG, die zuerst weg muss.
            case TRAEGT_SPEZIALISIERUNG -> mitNamen(sprache,
                    BerufSatz.ABSAGE_TRAEGT_SPEZIALISIERUNG, urteil.beruf());
            case SPERRFRIST -> sprache.bau(BerufSatz.ABSAGE_SPERRFRIST, 1L,
                    Einsatz.von("dauer", Spielzeittext.dauer(sprache, urteil.restfrist())));
            case GELD_FEHLT -> sprache.bau(BerufSatz.ABSAGE_GELD_FEHLT, 1L,
                    Einsatz.von("betrag", sprache.betrag(urteil.kosten()))
                            .mit("guthaben", sprache.betrag(guthaben)));
        };
    }

    /**
     * Das Wort zu einem Beruf - aus der Sprachdatei, nie der Schluessel.
     *
     * <p>Oeffentlich fuer {@link BerufeBefehle}: Die Vervollstaendigung schlaegt die
     * Schluessel vor, die Absagen und Zusagen nennen das Wort. Beides aus derselben Quelle
     * zu holen waere falsch - der Spieler tippt den Schluessel und liest das Wort.
     */
    static String name(Sprache sprache, String beruf) {
        return sprache.wort(new Berufsname(beruf), Einsatz.keine());
    }

    private static List<Component> mitNamen(Sprache sprache, BerufSatz satz, String beruf) {
        return sprache.bau(satz, 1L, Einsatz.von("name", name(sprache, beruf)));
    }

    /**
     * Die Spezialisierungen eines Grundberufes als EINE Zeichenkette.
     *
     * <p>Der Trenner steht in der Sprachdatei und darf leer sein - dann stossen die Namen
     * aneinander. Haesslich, aber die Entscheidung des Betreibers; ein Trenner sendet
     * keine eigene Zeile, also verstummt dabei nichts.
     */
    private static String aufgezaehlt(Sprache sprache, List<String> berufe) {
        String trenner = sprache.wort(BerufSatz.LISTE_TRENNER, Einsatz.keine());
        List<String> worte = new ArrayList<>();
        for (String beruf : berufe) {
            worte.add(name(sprache, beruf));
        }
        return String.join(trenner, worte);
    }

    /**
     * Wie viele Plaetze einer Rolle belegt sind - GEZAEHLT VON DER REGEL.
     *
     * <p>{@link Berufsregel#belegte} und keine eigene Schleife: Zweimal gebaut zaehlte die
     * Anzeige eines Tages anders als die Regel, und ein Spieler las "1 von 2" und bekaeme
     * trotzdem eine Absage. Genau dafuer ist die Methode dort oeffentlich.
     */
    private static Einsatz plaetze(Berufsverzeichnis verzeichnis, Berufsstand stand,
                                   boolean spezialisierung, int erlaubt) {
        return Einsatz.von("gehalten",
                        String.valueOf(Berufsregel.belegte(verzeichnis, stand,
                                spezialisierung)))
                .mit("erlaubt", String.valueOf(erlaubt));
    }
}
