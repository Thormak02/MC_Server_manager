package de.freimarken.paper;

import de.freimarken.kern.Bedingungsart;
import de.freimarken.kern.BesitzLager;
import de.freimarken.kern.Kasse;
import de.freimarken.kern.Berufsablage;
import de.freimarken.kern.Rangablage;
import de.freimarken.kern.Rangregel;
import de.freimarken.kern.Rangstand;
import de.freimarken.kern.Rangwerte;
import de.freimarken.kern.Spielzeitablage;
import de.freimarken.kern.Spielzeitstand;
import de.freimarken.kern.Tagesgrenze;
import de.freimarken.kern.Wirtschaftsstand;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Logger;

/**
 * Der Aufstieg: messen, urteilen, eintragen - und sagen, was geschehen ist.
 *
 * <p><b>Das ist der erste Verbraucher von {@link Rangregel}.</b> Teil A hat die Regel
 * gebaut und gruen geprueft, und bis hierher hat sie niemand gerufen; Teil C hat die
 * Ablage gebaut, und niemand hat etwas hineingeschrieben. Hier kommt beides zusammen.
 *
 * <p><b>Zwei Wege herein, und sie teilen jede Zeile Rechnung</b> ({@link #hebe}):
 * <ul>
 *   <li>{@link #pruefe(UUID)} - gerufen je anwesendem Spieler im Spielzeittakt. Das ist
 *       der Weg, der "steigt von selbst" wahr macht.
 *   <li>{@link #nachstufung()} - gerufen EINMAL beim Start, ueber jeden, von dem die
 *       Spielzeit etwas weiss. Das ist der Weg fuer die Spieler, die schon da waren,
 *       bevor es die Leiter gab.
 * </ul>
 *
 * <p><b>Der Startlauf ist KEIN Einmallauf, sondern ein Lauf bei JEDEM Start</b>
 * [19.11.2026]. Erwogen war ein Merker "schon eingestuft"; es gibt in diesem Projekt
 * keinen fachlichen Merker, der einen Neustart ueberlebt (die Tabelle {@code raenge}
 * taugt nicht dafuer - dort heisst "keine Zeile" die unterste Stufe und nicht "noch
 * nicht geprueft"). Gebraucht wird er auch nicht: Der Lauf ist FOLGENLOS, wenn alles
 * stimmt. {@link Rangregel#aufstieg} geht nur nach oben, und wer schon auf seiner Stufe
 * steht, bekommt keinen Schreibzug. Dass er bei jedem Start laeuft, ist damit kein Preis,
 * sondern ein Gewinn: Er faengt auch den Spieler, der waehrend seiner Abwesenheit
 * aufstiegsreif wurde, und er zieht nach, wenn der Betreiber die Leiter aendert.
 *
 * <p><b>Gemessen wird nur, was die Leiter VERLANGT</b> ({@link Rangregel#gebraucht}), und
 * fuer niemanden, der schon oben steht. Das ist hier keine Sparsamkeit: Jede Messung
 * ausser dem Beruf ist eine Datenbankabfrage im Hauptfaden, und der Takt schlaegt jede
 * Minute je anwesendem Spieler. Steht die ganze Mannschaft auf der obersten Stufe, kostet
 * dieser Lauf eine Abfrage je Spieler und je Minute - die Rangzeile selbst; alles andere
 * entfaellt.
 *
 * <p><b>Was dieser Lauf NICHT tut: Rechte vergeben.</b> Das macht {@link Rangwache}, und
 * der Aufrufer muss sie rufen. Getrennt, weil ein Aufstieg ohne anwesenden Spieler
 * stattfinden kann (der Startlauf) und dann gibt es niemanden, dem man etwas anhaengen
 * koennte. Dass die Verdrahtung steht, haelt eine Quelltexttafel - eine Nachricht ohne
 * Rechte waere der schlimmste Fall: Der Spieler liest "du bist jetzt Siedler" und kann
 * genau so viel wie vorher.
 */
final class Aufstiegslauf {

    /**
     * Ein geschehener Aufstieg.
     *
     * @param spieler wer
     * @param vorher  die Stufe, auf der er stand - der Schluessel, nie {@code null}:
     *                Wer nie aufgestiegen ist, stand auf der untersten
     * @param nachher die Stufe, auf der er jetzt steht
     */
    record Aufstieg(UUID spieler, String vorher, String nachher) {

        Aufstieg {
            if (spieler == null || vorher == null || nachher == null) {
                throw new IllegalArgumentException("Ein Aufstieg ohne Spieler oder ohne"
                        + " eine seiner beiden Stufen");
            }
        }
    }

    /**
     * Was ein Startlauf getan hat.
     *
     * @param geprueft wie viele Spieler angesehen wurden
     * @param aufstiege wer dabei gehoben wurde, in der Reihenfolge des Laufs
     */
    record Ergebnis(int geprueft, List<Aufstieg> aufstiege) {

        Ergebnis {
            aufstiege = List.copyOf(aufstiege == null ? List.of() : aufstiege);
        }

        static Ergebnis nichts() {
            return new Ergebnis(0, List.of());
        }
    }

    private final Wirtschaftsstand wirtschaft;
    private final Rangablage raenge;
    private final Spielzeitablage spielzeit;
    private final BesitzLager lager;
    private final Berufsablage berufe;
    private final Kasse kasse;

    /**
     * Die Welt, in der "eigene Parzellen" gezaehlt werden.
     *
     * <p><b>Der Weltschluessel ist der Riegel und nicht die Art</b> - das ist eine Zusage
     * von {@code BesitzLager.anzahlVon} und steht in {@code BesitzLagerVertrag}: Gezaehlt
     * wird jede Zeile dieses Eigentuemers in DIESER Welt, die auf dem Raster liegt. In der
     * Marktstadt sind das die Parzellen und die Felder ihrer Gruppen; eine Meerinsel oder
     * eine Wolkeninsel liegt in einer anderen Welt und zaehlt hier nicht mit. Genau diese
     * Zahl begrenzt auch {@code ParzellenBefehle} - zwei verschiedene Zaehlungen fuer
     * "eigene Parzellen" waeren ein Rang, der an einer Zahl haengt, die der Spieler
     * nirgends sieht.
     */
    private final String marktwelt;

    private final Clock uhr;
    private final Logger protokoll;

    Aufstiegslauf(Wirtschaftsstand wirtschaft, Rangablage raenge,
                  Spielzeitablage spielzeit, BesitzLager lager, Berufsablage berufe,
                  Kasse kasse, String marktwelt, Clock uhr, Logger protokoll) {
        if (wirtschaft == null || raenge == null || spielzeit == null || lager == null
                || berufe == null || kasse == null || uhr == null || protokoll == null) {
            throw new IllegalArgumentException("Der Aufstiegslauf braucht die Werte, die"
                    + " Rangablage, alle vier Messquellen, eine Uhr und ein Protokoll");
        }
        if (marktwelt == null || marktwelt.isBlank()) {
            throw new IllegalArgumentException("Ohne Weltschluessel ist \"eigene"
                    + " Parzellen\" nicht zu zaehlen - der Schluessel IST der Riegel");
        }
        this.wirtschaft = wirtschaft;
        this.raenge = raenge;
        this.spielzeit = spielzeit;
        this.lager = lager;
        this.berufe = berufe;
        this.kasse = kasse;
        this.marktwelt = marktwelt;
        this.uhr = uhr;
        this.protokoll = protokoll;
    }

    /**
     * Prueft EINEN Spieler und hebt ihn, wenn ihm mehr zusteht.
     *
     * <p>Gerufen im Takt. Liest die Spielzeit selbst - anders als der Startlauf, der sie
     * fuer alle in einer Abfrage holt.
     *
     * @return der Aufstieg, oder leer: Es stand ihm nichts mehr zu, die Leiter greift
     *         nicht, oder ein anderer Weg war in derselben Millisekunde schneller
     */
    Optional<Aufstieg> pruefe(UUID spieler) {
        if (spieler == null) {
            return Optional.empty();
        }
        Rangwerte werte = wirtschaft.werte().raenge();
        if (!werte.greift()) {
            return Optional.empty();
        }
        Rangstand stand = raenge.lies(spieler);
        if (stehtSchonOben(werte, stand)) {
            // DIE SPARENDE ZEILE, und sie spart das meiste: Wer oben steht, wird nie
            // wieder gemessen - und das ist nach einiger Zeit die ganze Mannschaft.
            return Optional.empty();
        }
        return hebe(werte, stand, spielzeit.lies(spieler, heute()));
    }

    /**
     * Stuft JEDEN nach, von dem die Spielzeit etwas weiss - der Lauf beim Start.
     *
     * <p>Die Spielzeit kommt in EINER Abfrage ({@code alleStaende}); alles andere wird je
     * Spieler geholt, und zwar nur, was die Leiter verlangt. Bei zwanzig Spielern sind
     * das in der ausgelieferten Fassung rund vierzig Abfragen - die Rangzeile und die
     * Parzellenzahl je Spieler, der Beruf aus dem Zwischenspeicher. Wer tausend Spieler
     * hat, braucht hier einen Leser ueber alle Zeilen; dann ist DIESE Stelle zu aendern
     * und nicht der Takt.
     *
     * <p><b>Ein Fehler bei einem Spieler beendet den Lauf nicht.</b> Er wird gemeldet und
     * der naechste angesehen: Ein Startlauf, der am dritten von zwanzig stirbt, liesse
     * siebzehn ungestuft - und niemand saehe, dass es siebzehn waren.
     */
    Ergebnis nachstufung() {
        Rangwerte werte = wirtschaft.werte().raenge();
        if (!werte.greift()) {
            return Ergebnis.nichts();
        }
        List<Spielzeitstand> alle;
        try {
            alle = spielzeit.alleStaende(heute());
        } catch (RuntimeException fehler) {
            protokoll.warning("Die Nachstufung konnte die Spielzeiten nicht lesen: "
                    + fehler.getMessage() + ". Es wurde niemand nachgestuft; der"
                    + " Spielzeittakt holt es fuer jeden Anwesenden nach.");
            return Ergebnis.nichts();
        }
        List<Aufstieg> gehoben = new ArrayList<>();
        int geprueft = 0;
        for (Spielzeitstand zeit : alle) {
            geprueft++;
            try {
                Rangstand stand = raenge.lies(zeit.spieler());
                if (stehtSchonOben(werte, stand)) {
                    continue;
                }
                hebe(werte, stand, zeit).ifPresent(gehoben::add);
            } catch (RuntimeException fehler) {
                protokoll.warning("Die Nachstufung von " + zeit.spieler()
                        + " ist fehlgeschlagen: " + fehler.getMessage()
                        + ". Die uebrigen werden weiter geprueft.");
            }
        }
        return new Ergebnis(geprueft, gehoben);
    }

    /**
     * Die EINE Rechnung, die beide Wege teilen.
     *
     * <p>Hier und nicht zweimal: Zwei Fassungen derselben Entscheidung weichen eines
     * Tages ab, und dann steigt ein Spieler im Takt auf und beim naechsten Start wieder
     * nicht - oder umgekehrt.
     *
     * @param stand der Stand, auf dem geurteilt wird. Er ist zugleich der ERWARTETE Stand
     *              beim Schreiben: Zwischen Urteil und Schreibzug darf sich nichts
     *              geaendert haben, sonst gilt dieses Urteil nicht mehr
     */
    private Optional<Aufstieg> hebe(Rangwerte werte, Rangstand stand,
                                    Spielzeitstand zeit) {
        String jetziger = stand.stufeIn(werte).schluessel();
        Map<Bedingungsart, Long> gemessen = messe(stand.spieler(), zeit,
                Rangregel.gebraucht(werte));
        Optional<Rangwerte.Stufe> ziel = Rangregel.aufstieg(werte, jetziger, gemessen);
        if (ziel.isEmpty()) {
            return Optional.empty();
        }
        String neuer = ziel.get().schluessel();
        if (!raenge.setze(new Rangablage.Antrag(stand.spieler(), neuer, uhr.instant()),
                stand)) {
            // Ein anderer Weg war schneller - der Takt und der Startlauf koennen sich im
            // ersten Augenblick nach dem Start begegnen. Kein Fehler und keine Meldung an
            // den Spieler: Der Stand ist dann schon der richtige, und eine zweite
            // Nachricht ueber denselben Aufstieg waere schlimmer als keine.
            return Optional.empty();
        }
        return Optional.of(new Aufstieg(stand.spieler(), jetziger, neuer));
    }

    /**
     * Die Messwerte fuer einen Spieler - die Tuer fuer das Muenzhaus [Teil G].
     *
     * <p><b>Warum sie hier steht und nicht im Muenzhaus:</b> Gemessen wird an EINER
     * Stelle. Haette der Katalog seinen eigenen Messer, urteilte ein Kauf eines Tages
     * anders als eine Befoerderung - dieselbe Bedingung, dieselbe Zahl in der Datei, und
     * zwei verschiedene Antworten. Gerufen wird sie ueber
     * {@code Muenzhaus.Bedingungsmessung}, also als Naht und nicht als Abhaengigkeit.
     *
     * <p><b>Der Spielzeitstand wird nur geholt, wenn eine Zeitart gefragt ist</b>, und das
     * ist dieselbe Regel wie bei {@link #messe}: Jede Messung ausser dem Beruf ist eine
     * Datenbankabfrage im Hauptfaden. Ein Katalog ohne Zeitbedingung soll seine Tafel
     * nicht mit einer Abfrage bezahlen, deren Ergebnis niemand liest - und die werfen
     * kann.
     */
    Map<Bedingungsart, Long> messeFuer(UUID wer, List<Bedingungsart> gebraucht) {
        if (wer == null || gebraucht == null) {
            throw new IllegalArgumentException("Gemessen wird fuer einen Spieler, und nur"
                    + " das, was jemand verlangt");
        }
        boolean zeitNoetig = gebraucht.contains(Bedingungsart.TAETIGE_STUNDEN)
                || gebraucht.contains(Bedingungsart.ANWESENHEIT_STUNDEN);
        return messe(wer, zeitNoetig ? spielzeit.lies(wer, heute()) : null, gebraucht);
    }

    /**
     * Die Messwerte - und zwar NUR die verlangten.
     *
     * <p>Eine Art, die in keiner Stufe steht, wird nicht gefragt. Das ist nicht nur
     * billiger, sondern auch richtiger: Eine Abfrage, deren Ergebnis niemand liest, ist
     * eine Fehlerquelle ohne Nutzen - sie kann werfen.
     *
     * <p>Die EINHEITEN sind die von {@link Rangregel} verlangten: Sekunden fuer die
     * Zeitarten, Stueck fuer den Rest. Die Umrechnung aus den Stunden der Datei macht
     * {@link Bedingungsart#inMesseinheit}, und zwar an einer einzigen Stelle - haette sie
     * jeder Messer selbst gemacht, schnitte einer die angefangene Stunde ab und der
     * naechste nicht.
     */
    private Map<Bedingungsart, Long> messe(UUID wer, Spielzeitstand zeit,
                                           List<Bedingungsart> gebraucht) {
        Map<Bedingungsart, Long> gemessen = new EnumMap<>(Bedingungsart.class);
        for (Bedingungsart art : gebraucht) {
            gemessen.put(art, switch (art) {
                case TAETIGE_STUNDEN -> zeit.taetigSekundenGesamt();
                case ANWESENHEIT_STUNDEN -> zeit.sekundenGesamt();
                case PARZELLEN -> (long) lager.anzahlVon(marktwelt, wer);
                // Die ZAHL der gehaltenen Berufe und nicht 0/1: Die Bedingungsart laesst
                // als Wert nur die 1 zu (jaNein), die MESSUNG darf trotzdem die Wahrheit
                // sagen. Eine Kappung hier waere eine zweite Schranke fuer dieselbe Sache.
                case BERUF -> (long) berufe.lies(wer).berufe().size();
                case GUTHABEN -> kasse.guthaben(wer);
            });
        }
        return gemessen;
    }

    /** Ob ueber ihm keine Stufe mehr ist - dann gibt es nichts zu messen. */
    private static boolean stehtSchonOben(Rangwerte werte, Rangstand stand) {
        return werte.naechste(stand.stufeIn(werte).schluessel()).isEmpty();
    }

    /**
     * Der heutige Tag unter der eingestellten Tagesgrenze.
     *
     * <p>Gebraucht wird er nur als Pflichtangabe von {@code Spielzeitablage.lies} - der
     * Rang haengt an den GESAMTzahlen, nicht an denen von heute. Trotzdem derselbe Weg
     * wie bei {@link Tagesbonus}: Zwei Wege zu "welcher Tag ist heute" sind zwei
     * Antworten, sobald jemand die Zone aendert.
     */
    private LocalDate heute() {
        return Tagesgrenze.tagVon(uhr.instant(), wirtschaft.werte().zeit().zone());
    }
}
