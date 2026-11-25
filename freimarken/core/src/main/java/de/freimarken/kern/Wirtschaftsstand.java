package de.freimarken.kern;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.Set;

/**
 * Die einzige veraenderliche Zelle des Plugins - was die Wirtschaft gerade kostet.
 *
 * <p>Jede Stelle, die mit Geld rechnet, haelt diesen Stand als unveraenderliches Feld und
 * fragt ihn im Augenblick des Gebrauchs. Darin liegt der ganze Trick des Neuladens: Ein
 * neuer Wert erreicht jede Stelle, ohne dass ein einziges Objekt neu gebaut wird. EIN
 * NEULADEN meldet keinen Zuhoerer an, stellt keinen Zeitgeber, haengt keinen Befehl neu ein
 * und tauscht keine Verbindung - also gibt es auch nichts abzumelden, und nichts kann doppelt
 * dastehen. Das ist der belegte Fehler bei einem Neuladen, das Objekte neu baut.
 *
 * <p>"Stellt keinen Zeitgeber" gilt fuer das NEULADEN und nicht fuer den Start: Der
 * Spielzeittakt wird in {@code onEnable} gestellt und in {@code onDisable} abgemeldet. Genau
 * deshalb darf sein Abstand ({@link Zeitwerte#PFAD_TAKT}) nicht neuladbar sein - nach zwei
 * Neuladen liefen sonst drei Zeitgeber und schrieben dieselbe Spielzeit dreimal.
 *
 * <p>{@code volatile} auf einem unveraenderlichen {@code record}: Die Vervollstaendigung
 * von Befehlen und das Senden des Befehlsbaums laufen ausdruecklich nebenlaeufig, und ein
 * Spieler kann mitten im Tausch kaufen. So sieht jeder Leser entweder den alten oder den
 * neuen Satz - nie eine Mischung und nie einen halb gefuellten.
 */
public final class Wirtschaftsstand {

    /**
     * Was ein Neuladen bewirkt hat - und was es ausdruecklich nicht angefasst hat.
     *
     * <p>Beide Listen sind fertige Klartextzeilen. Der zweite Teil ist das Gegenmittel
     * gegen das haeufigste Fehlerbild eines Neuladens: Der Befehl meldet Erfolg, ein Teil
     * wirkt aber erst nach dem Neustart. Er erscheint deshalb auch dann, wenn sich nichts
     * geaendert hat.
     */
    public record Uebernahme(List<String> geaendert, List<String> nichtAngefasst) {
    }

    /**
     * Die Schluessel, die ein Neuladen NICHT uebernimmt - die EINE Liste davon.
     *
     * <p>Sie steht in der Klasse, die es entscheidet: {@link #uebernimm} laesst genau diese
     * drei stehen, und {@link #nichtAngefasst} nennt genau diese drei. Der Leser der
     * Einstellungsdatei fragt dieselbe Liste, damit er fuer eine FEHLENDE Zeile den
     * richtigen Satz schreibt - fuer einen neuladbaren Schluessel "es gilt 4", fuer einen
     * von diesen hier nicht, denn was gerade gilt, weiss nur dieser Halter. Gab es die
     * Liste zweimal, sagten Leser und Halter in derselben Antwort Verschiedenes; genau das
     * ist einmal passiert ("Sie gelten aus den Vorgaben des Plugins" neben "es gilt weiter
     * America/New_York").
     *
     * <p>{@link Zeitwerte#PFAD_UNTAETIG} steht hier mit Absicht NICHT: Die
     * Untaetigkeitsgrenze ist neuladbar. Nur das eine Paar, das zum laufenden Takt nicht
     * passt, bleibt stehen - und das ist keine Eigenschaft des Schluessels, sondern eine
     * des Augenblicks, deshalb entsteht die Zeile dazu in {@link #nichtAngefasst} und nicht
     * aus dieser Liste.
     */
    public static final List<String> NUR_BEIM_START = List.of(
            Wirtschaftswerte.PFAD_STARTGUTHABEN, Zeitwerte.PFAD_ZONE, Zeitwerte.PFAD_TAKT);

    private volatile Wirtschaftswerte stand;

    public Wirtschaftsstand(Wirtschaftswerte anfang) {
        if (anfang == null) {
            throw new IllegalArgumentException("Ohne Anfangsstand gibt es nichts zu halten");
        }
        this.stand = anfang;
    }

    /** Was JETZT gilt. Beim Gebrauch zu fragen und nicht in ein Feld zu kopieren. */
    public Wirtschaftswerte werte() {
        return stand;
    }

    /**
     * Uebernimmt, was neu gelesen wurde - und laesst stehen, was ein Neuladen nicht
     * aendern darf.
     *
     * <p>Das Startguthaben bleibt mit Absicht aus dem Tausch heraus: Die Kassen geben es
     * bei jeder Abfrage als Vorgabe fuer ein noch nicht eroeffnetes Konto zurueck, und ein
     * neuer Wert wuerde damit rueckwirkend aendern, was ein Spieler mitbringt, der noch
     * nie gebucht hat. Es hier stehen zu lassen ist billiger als ein Bestandsschutz, und
     * die Begruendung steht an derselben Stelle wie die Entscheidung.
     *
     * <p>Von den {@link Zeitwerte} wird nur die Untaetigkeitsgrenze getauscht. Die ZONE und
     * der TAKT bleiben stehen, jede aus eigenem Grund:
     * <ul>
     *   <li>Die Zone verschiebt die Tagesgrenze. Heute waere ein Tausch im Betrieb noch
     *       harmlos - zwei Zeilen fuer einen Kalendertag summieren sich richtig. Mit dem
     *       ersten Verbraucher der Grenze (Tagesbonus, Ladenmiete, Verdienstdeckel) rechnete
     *       eine Umstellung mitten am Tag denselben Tag zweimal ab oder liesse ihn ausfallen.
     *       Neuladbar zu machen, was es mit dem naechsten Schritt nicht mehr sein darf, heisst
     *       die Regel zweimal zu bauen.</li>
     *   <li>Der Takt steckt im gestellten Zeitgeber. Ihn beim Neuladen neu zu stellen waere
     *       genau der Fehler, gegen den diese Klasse gebaut ist: Nach zwei Neuladen liefen
     *       drei Zeitgeber und schrieben dieselbe Spielzeit dreimal.</li>
     *   <li>Die Untaetigkeitsgrenze dagegen wird beim Gebrauch gefragt. Kein Objekt wird neu
     *       gebaut, nichts angemeldet - also ist sie neuladbar, mit genau einer Ausnahme.</li>
     * </ul>
     *
     * <p><b>Die eine Ausnahme bei der Grenze.</b> Hier entsteht das Paar (LAUFENDER Takt,
     * NEUE Grenze), und das kann unbrauchbar sein, obwohl das Paar der Datei stimmt: Start
     * mit {@code takt-sekunden: 600} und {@code untaetig-minuten: 10}, dann beides auf 60
     * und 1 geaendert (ein gueltiges Paar) und neu geladen - der Takt bleibt bei 600, und
     * 1 Minute Grenze wuerde dazu jeden Takt ganz wegwerfen, auch die aktiv gespielte Zeit
     * darin ({@link Zeitwerte#brauchbareGrenzeZumTakt}). Vorher nahm dieser Weg die
     * Kombination stumm an; den Konstruktor werfen zu lassen waere nicht besser, denn das
     * brach den Befehl mitten im Lauf ab. Also bleibt die alte Grenze stehen, und
     * {@link #nichtAngefasst} sagt es in einer Zeile mit dem Grund. Das ist kein Bruch der
     * Zusage "alles oder nichts": Eine unbrauchbare Zeile faengt der Leser, und bei einer
     * Klage kommt es hier gar nicht an; dieses Paar ist keine unbrauchbare Zeile, sondern
     * eine, die erst mit dem Neustart passt - also derselbe Fall wie Zone und Takt.
     *
     * @param ausDerDatei ein vollstaendig gelesener und gepruefter Satz
     * @return welche Zeilen sich geaendert haben und welche nicht angefasst wurden
     */
    public Uebernahme uebernimm(Wirtschaftswerte ausDerDatei) {
        if (ausDerDatei == null) {
            throw new IllegalArgumentException("Nichts gelesen heisst nichts uebernehmen");
        }
        Wirtschaftswerte alt = stand;
        // Gefragt wird am LAUFENDEN Takt, nicht am Takt der Datei - der steckt im
        // gestellten Zeitgeber und wechselt erst mit dem Neustart.
        boolean grenzePasst = Zeitwerte.brauchbareGrenzeZumTakt(alt.zeit().taktSekunden(),
                ausDerDatei.zeit().untaetigMinuten());
        Zeitwerte zeit = new Zeitwerte(alt.zeit().zone(), alt.zeit().takt(),
                grenzePasst ? ausDerDatei.zeit().untaetigNach()
                        : alt.zeit().untaetigNach());
        // Alles aus der Datei - mit ZWEI Ausnahmen, und beide sind in NUR_BEIM_START
        // begruendet: das Startguthaben (es wuerde sonst rueckwirkend gelten) und der
        // Takt samt Zone (die stecken im gestellten Zeitgeber).
        Wirtschaftswerte neu = ausDerDatei.mitStartguthaben(alt.startguthaben())
                .mitZeit(zeit);

        List<String> geaendert = new ArrayList<>();
        nenneZahl(geaendert, Wirtschaftswerte.PFAD_GRUNDPREIS,
                alt.preise().grundpreis(), neu.preise().grundpreis());
        nenneZahl(geaendert, Wirtschaftswerte.PFAD_ZUSAMMENLEGUNG,
                alt.preise().zusammenlegungJeFeld(), neu.preise().zusammenlegungJeFeld());
        nenneZahl(geaendert, Wirtschaftswerte.PFAD_HOECHSTZAHL,
                alt.hoechstzahl(), neu.hoechstzahl());
        nenneZahl(geaendert, Wirtschaftswerte.PFAD_GRUPPEN_FELDER,
                alt.gruppenFelder(), neu.gruppenFelder());
        nenneZahl(geaendert, Zeitwerte.PFAD_UNTAETIG,
                alt.zeit().untaetigMinuten(), neu.zeit().untaetigMinuten());
        // Die vier Berufszahlen werden VOLLSTAENDIG getauscht, keine steht in
        // NUR_BEIM_START: Alle vier werden beim Gebrauch gefragt, und es wird nichts
        // gebaut und nichts angemeldet. Eine kuerzere Frist gilt sofort, eine kleinere
        // Platzzahl nimmt niemandem einen Beruf weg - er kann nur keinen neuen mehr
        // waehlen, bis er unter der Zahl liegt.
        nenneZahl(geaendert, Berufswerte.PFAD_WECHSELGEBUEHR,
                alt.berufe().wechselgebuehr(), neu.berufe().wechselgebuehr());
        nenneZahl(geaendert, Berufswerte.PFAD_SPERRFRIST,
                alt.berufe().sperrfristStunden(), neu.berufe().sperrfristStunden());
        nenneZahl(geaendert, Berufswerte.PFAD_GRUNDBERUFE,
                alt.berufe().grundberufe(), neu.berufe().grundberufe());
        nenneZahl(geaendert, Berufswerte.PFAD_SPEZIALISIERUNGEN,
                alt.berufe().spezialisierungen(), neu.berufe().spezialisierungen());
        // Die Verdienstzahlen ebenso vollstaendig getauscht, und die LOHNTAFEL mit: Sie
        // ist der Hebel, mit dem der Betreiber auf 2.000 bis 5.000 Mark je Spielstunde
        // einstellt, und er soll das im Betrieb tun koennen. Genannt wird nur ihre
        // ZEILENZAHL - vierzig Zeilen "alt -> neu" waeren keine Antwort mehr, sondern
        // eine Wand.
        nenneZahl(geaendert, Verdienstwerte.PFAD_TAGESDECKEL,
                alt.verdienst().tagesdeckel(), neu.verdienst().tagesdeckel());
        nenneZahl(geaendert, Verdienstwerte.PFAD_VOLL_BIS,
                alt.verdienst().vollBis(), neu.verdienst().vollBis());
        nenneZahl(geaendert, Verdienstwerte.PFAD_STUFE,
                alt.verdienst().stufe(), neu.verdienst().stufe());
        nenneZahl(geaendert, Verdienstwerte.PFAD_FAKTOR,
                alt.verdienst().faktorProzent(), neu.verdienst().faktorProzent());
        nenneZahl(geaendert, Verdienstwerte.PFAD_BODEN,
                alt.verdienst().bodenProzent(), neu.verdienst().bodenProzent());
        // Die STUNDENMENGE je Taetigkeit. Sie gehoert genannt, weil sie der wirksame
        // Hebel gegen Farmen ist: Wer sie verstellt und keine Zeile darueber liest,
        // weiss nicht, ob seine Aenderung angekommen ist - und dreht beim naechsten Mal
        // weiter, obwohl sie schon gilt.
        for (Taetigkeit taetigkeit : Taetigkeit.values()) {
            nenneZahl(geaendert, Verdienstwerte.PFAD_STUNDENMENGE + "."
                            + taetigkeit.schluessel(),
                    alt.verdienst().stundenmenge(taetigkeit),
                    neu.verdienst().stundenmenge(taetigkeit));
        }
        // Die zwei Listen werden VERGLICHEN und nicht gezaehlt: Ein ausgetauschter Name
        // bei gleicher Laenge ist genau die Aenderung, die der Betreiber gerade gemacht
        // hat - und beide Listen entscheiden ueber Geld. Bei der Weissliste haengt daran,
        // ob eine Spawnerfarm zahlt; beim Saatgut, ob ein Acker etwas einbringt.
        nenneMenge(geaendert, Verdienstwerte.PFAD_SPAWNGRUENDE,
                alt.verdienst().spawngruende(), neu.verdienst().spawngruende());
        nenneMenge(geaendert, Verdienstwerte.PFAD_SAATGUT,
                alt.verdienst().saatgut(), neu.verdienst().saatgut());
        // Die Tafel selbst wird VERGLICHEN und nicht gezaehlt: Eine Zeile mit einem
        // anderen Lohn laesst die Zeilenzahl gleich, und genau das ist die Aenderung, die
        // der Betreiber gerade gemacht hat. Genannt wird die Zahl der Zeilen - vierzig
        // Zeilen "alt -> neu" waeren keine Antwort mehr, sondern eine Wand.
        if (!alt.verdienst().loehne().equals(neu.verdienst().loehne())) {
            geaendert.add(Verdienstwerte.PFAD_LOEHNE + ": "
                    + alt.verdienst().loehne().anzahl() + " -> "
                    + neu.verdienst().loehne().anzahl() + " Zeilen (geaendert)");
        }
        // Der Bundesmarkt: nur noch der Deckel. Das SORTIMENT stand hier bis zum
        // 10.11.2026 und wurde verglichen statt gezaehlt; es ist mit der Tafel
        // bundesmarkt.ankauf gefallen - verkauft wird am Schild, und was es gibt,
        // entscheidet, welche Schilder stehen.
        nenneZahl(geaendert, Marktwerte.PFAD_TAGESDECKEL,
                alt.markt().tagesdeckel(), neu.markt().tagesdeckel());
        // Die Messstelle: beide Zahlen und die Artenliste. Die Liste wird VERGLICHEN und
        // nicht gezaehlt, aus demselben Grund wie beim Sortiment - wer "benutzen" gegen
        // "kaempfen" tauscht, hat weiter sechs Arten und eine andere Messung.
        nenneZahl(geaendert, Messwerte.PFAD_VERLANGTE_ARTEN,
                alt.messung().verlangteArten(), neu.messung().verlangteArten());
        nenneZahl(geaendert, Messwerte.PFAD_STRECKE,
                alt.messung().mindestStrecke(), neu.messung().mindestStrecke());
        nenneMenge(geaendert, Messwerte.PFAD_ARTEN, schluessel(alt.messung().arten()),
                schluessel(neu.messung().arten()));
        // Der Tagesbonus: vier Zahlen, jede mit ihrem Pfad. Keine Zusammenfassung wie
        // "der Bonus hat sich geaendert" - wer die Serie verstellt, soll lesen, WELCHE
        // Zahl jetzt gilt, und nicht beim naechsten Mal weiterdrehen.
        nenneZahl(geaendert, Bonuswerte.PFAD_GRUNDBETRAG,
                alt.bonus().grundbetrag(), neu.bonus().grundbetrag());
        nenneZahl(geaendert, Bonuswerte.PFAD_JE_SERIENTAG,
                alt.bonus().jeSerientag(), neu.bonus().jeSerientag());
        nenneZahl(geaendert, Bonuswerte.PFAD_HOECHSTBETRAG,
                alt.bonus().hoechstbetrag(), neu.bonus().hoechstbetrag());
        nenneZahl(geaendert, Bonuswerte.PFAD_LUECKE,
                alt.bonus().reisstAbLuecke(), neu.bonus().reisstAbLuecke());
        nenneZahl(geaendert, Bonuswerte.PFAD_MINDEST_MINUTEN,
                alt.bonus().mindestMinuten(), neu.bonus().mindestMinuten());
        // Die Freie Truhe: der Deckel und die Verbotsliste. Die Liste wird VERGLICHEN
        // und nicht gezaehlt, aus demselben Grund wie das Saatgut - wer eine
        // Trankflasche gegen eine Karte tauscht, hat weiter einen Eintrag und ein
        // anderes Verbot. Die STELLEN stehen nicht hier: Sie sind kein Teil dieses
        // Standes, sondern werden nur beim Start gelesen (Truhenverzeichnis).
        nenneZahl(geaendert, Truhenwerte.PFAD_ENTNAHME,
                alt.truhe().entnahmeJeTag(), neu.truhe().entnahmeJeTag());
        nenneMenge(geaendert, Truhenwerte.PFAD_VERBOTEN,
                alt.truhe().verboten(), neu.truhe().verboten());
        // Die Freistadtzahlen gehoeren NICHT in NUR_BEIM_START: Sie werden beim Gebrauch
        // gefragt, es wird nichts gebaut und nichts angemeldet. Ohne diese zwei Zeilen
        // aenderte sich der Preis einer Stadt beim Neuladen stumm.
        nenneZahl(geaendert, Freistadtwerte.PFAD_GRUENDUNGSPREIS,
                alt.freistaedte().gruendungspreis(), neu.freistaedte().gruendungspreis());
        nenneZahl(geaendert, Freistadtwerte.PFAD_HOECHSTZAHL,
                alt.freistaedte().hoechstzahl(), neu.freistaedte().hoechstzahl());
        nenneZahl(geaendert, Freistadtwerte.PFAD_HOECHSTZELLEN,
                alt.freistaedte().hoechstzellen(),
                neu.freistaedte().hoechstzellen());
        nenneZahl(geaendert, Freistadtwerte.PFAD_VERKAUFSGEBUEHR,
                alt.freistaedte().verkaufsgebuehrProzent(),
                neu.freistaedte().verkaufsgebuehrProzent());
        // DIE ZWEI AUFLAGENSCHALTER, nachgetragen am 17.11.2026 - aus genau dem Grund,
        // der zwei Zeilen hoeher steht. Sie fehlten hier, und damit war ihr Neuladen
        // voellig stumm: Wer das Ladenrecht nach einem Event wieder einschaltet und
        // "nichts geaendert" liest, glaubt das Neuladen wirke nicht - oder die Regel
        // sei kaputt. Ein abgeschalteter Riegel sieht im Spiel ohnehin aus wie ein
        // kaputter; dann darf wenigstens sein Umlegen nicht verschwiegen werden.
        nenneJaNein(geaendert, Freistadtwerte.PFAD_AUFLAGE_TELEPORTER,
                alt.freistaedte().auflageTeleporter(),
                neu.freistaedte().auflageTeleporter());
        nenneJaNein(geaendert, Freistadtwerte.PFAD_AUFLAGE_LADENRECHT,
                alt.freistaedte().auflageLadenrecht(),
                neu.freistaedte().auflageLadenrecht());
        // Die Inselzahlen, aus genau demselben Grund wie die Freistadtzahlen darueber:
        // Sie werden beim Gebrauch gefragt, es wird nichts gebaut und nichts angemeldet.
        // Ohne diese zwei Zeilen aenderte sich der Preis einer Insel beim Neuladen stumm.
        nenneZahl(geaendert, Inselwerte.PFAD_PREIS,
                alt.inseln().preis(), neu.inseln().preis());
        nenneZahl(geaendert, Inselwerte.PFAD_HOECHSTZAHL,
                alt.inseln().hoechstzahl(), neu.inseln().hoechstzahl());
        // Der Inselbonus als MENGE der Berufe: Welcher Beruf dazukam und welcher ging,
        // ist das, was der Betreiber lesen will. Die ZAHL je Beruf nennt der
        // Startbericht des Lesers - hier stuende sonst eine Zeile je Beruf, und bei
        // einem Satz, der sich sonst nicht geaendert hat, waere das die laengste.
        nenneMenge(geaendert, Verdienstwerte.PFAD_INSELBONUS,
                alt.verdienst().inselbonus().keySet(),
                neu.verdienst().inselbonus().keySet());
        // Die Angeltabelle, aus demselben Grund wie die zwei Bloecke darueber. Der
        // SCHALTER und die HAEUFIGKEIT als Zahl, die Tabelle als Menge: Welche Zeilen
        // dazukamen und welche gingen, ist das, was der Betreiber lesen will - "dazu:
        // sponge, weg: trident" sagt mehr als "die Tabelle hat sich geaendert".
        nenneJaNein(geaendert, Angelwerte.PFAD_AN,
                alt.angeln().an(), neu.angeln().an());
        nenneZahl(geaendert, Angelwerte.PFAD_HAEUFIGKEIT,
                alt.angeln().haeufigkeitProzent(), neu.angeln().haeufigkeitProzent());
        nenneMenge(geaendert, Angelwerte.PFAD_FAENGE,
                alt.angeln().gewichte().keySet(), neu.angeln().gewichte().keySet());
        // Wolkenwerder, aus demselben Grund wie die drei Bloecke darueber: Preis und
        // Obergrenze werden beim Nehmen gefragt, die Biomliste beim Waehlen - gebaut und
        // angemeldet wird nichts davon. Die Biome als MENGE und nicht als Liste: Welches
        // dazukam und welches wegfiel, ist das, was der Betreiber lesen will.
        nenneZahl(geaendert, Wolkenwerte.PFAD_PREIS,
                alt.wolken().preis(), neu.wolken().preis());
        nenneZahl(geaendert, Wolkenwerte.PFAD_HOECHSTZAHL,
                alt.wolken().hoechstzahl(), neu.wolken().hoechstzahl());
        nenneMenge(geaendert, Wolkenwerte.PFAD_BIOME,
                Set.copyOf(alt.wolken().biome()), Set.copyOf(neu.wolken().biome()));
        // DIE RANGLEITER, nachgetragen am 19.11.2026 mit Teil F - und zwar als Fund und
        // nicht als Erweiterung: Sie fehlte hier seit Teil A, und damit war ihr Neuladen
        // voellig stumm. Wer eine Stufe hinzufuegt und "nichts geaendert" liest, glaubt
        // das Neuladen wirke nicht. Es ist dasselbe Fehlerbild wie bei den zwei
        // Auflagenschaltern eine Seite hoeher, und es hat dieselbe Antwort.
        //
        // Der SCHALTER als Ja/Nein, die Leiter als MENGE ihrer Stufennamen: Welche Stufe
        // dazukam und welche wegfiel, ist das, was der Betreiber lesen will. Eine Zeile
        // je geaenderter Bedingung waere bei vier Stufen die laengste Antwort des
        // Befehls - und was in einer Stufe steht, nennt der Startbericht des Lesers.
        nenneJaNein(geaendert, Rangwerte.PFAD_AN, alt.raenge().an(), neu.raenge().an());
        nenneMenge(geaendert, Rangwerte.PFAD_LEITER,
                new TreeSet<>(alt.raenge().schluessel()),
                new TreeSet<>(neu.raenge().schluessel()));
        // UND JE STUFE IHRE DREI TEILE - nachgetragen am 20.11.2026, Befund der
        // Pruefrunde zu Teil G. Der Absatz darueber hat sich gegen eine Zeile je
        // geaenderter BEDINGUNG entschieden, und das bleibt richtig: Die Antwort waere
        // eine Wand. Aber die Entscheidung war "kuerzer", nicht "stumm" - wer
        // rang.leiter.buerger.zahlen von 400.000 auf 4.000 setzt, darf nicht "nichts
        // geaendert" lesen. Darum dieselbe Antwort, die die Lohntafel schon gibt: DASS
        // sich die Tabelle geaendert hat, mit ihrer Laenge. Was darin steht, nennt der
        // Startbericht des Lesers.
        for (Rangwerte.Stufe stufe : neu.raenge().leiter()) {
            var vorher = alt.raenge().stufe(stufe.schluessel());
            if (vorher.isEmpty()) {
                // Fuer eine NEUE Stufe ist nichts davon eine Aenderung, sondern ihr
                // erster Wert - und die Menge darueber nennt sie schon.
                continue;
            }
            String wurzel = Rangwerte.PFAD_LEITER + "." + stufe.schluessel() + ".";
            nenneTabelle(geaendert, wurzel + Rangwerte.SCHLUESSEL_BEDINGUNGEN,
                    vorher.get().bedingungen(), stufe.bedingungen());
            nenneTabelle(geaendert, wurzel + Rangwerte.SCHLUESSEL_ZAHLEN,
                    vorher.get().zahlen(), stufe.zahlen());
            nenneMenge(geaendert, wurzel + Rangwerte.SCHLUESSEL_RECHTE,
                    new TreeSet<>(vorher.get().rechte()), new TreeSet<>(stufe.rechte()));
        }
        // DIE TEAMROLLEN [Schritt 16a Teil A]: dieselbe Bauart wie die Rangleiter eine
        // Zeile darueber, und aus demselben Grund. Die MENGE der Rollennamen nennt, welche
        // dazukam und welche wegfiel; je Rolle nennt eine zweite Zeile, dass sich ihre
        // Rechte geaendert haben.
        //
        // Die zweite Zeile ist hier die wichtigere - wichtiger als bei den Raengen. Eine
        // geaenderte Rangzahl merkt ein Spieler beim naechsten Versuch; ein Recht, das
        // einer Rolle genommen wurde, merkt der Moderator erst, wenn er es braucht, und
        // dann haelt er es fuer einen Fehler des Plugins. Ein stummes Neuladen waere
        // genau der Zustand, in dem niemand nachsieht.
        nenneMenge(geaendert, Teamwerte.PFAD_ROLLEN,
                new TreeSet<>(alt.team().schluessel()),
                new TreeSet<>(neu.team().schluessel()));
        for (Teamwerte.Rolle rolle : neu.team().rollen()) {
            var vorherigeRolle = alt.team().rolle(rolle.schluessel());
            if (vorherigeRolle.isEmpty()) {
                // Fuer eine NEUE Rolle ist das keine Aenderung, sondern ihr erster Wert -
                // und die Menge darueber nennt sie schon.
                continue;
            }
            nenneMenge(geaendert, Teamwerte.PFAD_ROLLEN + "." + rolle.schluessel() + "."
                            + Teamwerte.SCHLUESSEL_RECHTE,
                    new TreeSet<>(vorherigeRolle.get().rechte()),
                    new TreeSet<>(rolle.rechte()));
        }
        // DAS MELDEWESEN [Schritt 16a Teil C]: ein Schalter und drei Zahlen. Sie
        // stehen hier und nicht als eigenes Feld im Plugin, weil es Bremsen gegen
        // Missbrauch sind - und Missbrauch kommt unangekuendigt. Wer die
        // Abklingzeit hochsetzt, weil gerade jemand spammt, braucht die Wirkung
        // jetzt und nicht nach dem naechsten Neustart.
        nenneJaNein(geaendert, Meldewerte.PFAD_AN,
                alt.melden().an(), neu.melden().an());
        nenneZahl(geaendert, Meldewerte.PFAD_ABKLINGZEIT,
                alt.melden().abklingzeitSekunden(),
                neu.melden().abklingzeitSekunden());
        nenneZahl(geaendert, Meldewerte.PFAD_HOECHSTE_OFFENE,
                alt.melden().hoechsteOffeneJeMelder(),
                neu.melden().hoechsteOffeneJeMelder());
        nenneZahl(geaendert, Meldewerte.PFAD_ZEILEN_JE_SEITE,
                alt.melden().zeilenJeSeite(), neu.melden().zeilenJeSeite());
        // DIE STRAFEN [Schritt 16a Teil B]: die Hoechstdauer je Art, zwei Schalter,
        // die Seitenlaenge und die Liste der mitgesperrten Befehle. Die erste Zeile
        // ist die wichtigste dieses Blockes - sie ist die Grenze zwischen dem, was
        // ein Moderator darf, und dem, was ein Verwalter darf (Plan 12c.2). Wer sie
        // enger zieht, zieht sie, weil gerade etwas geschehen ist; ein stummes
        // Neuladen waere genau dann stumm, wenn jemand hinsieht.
        //
        // Je ART eine Zeile, und zwar aus der Aufzaehlung gerechnet: Eine vierte
        // fortwirkende Art bekommt ihre Zeile dann von selbst. Eine von Hand
        // geschriebene Liste waere die Stelle, an der sie fehlt.
        for (Strafart art : Strafart.values()) {
            if (!art.giltFort()) {
                continue;
            }
            nenneZahl(geaendert, Strafwerte.PFAD_HOECHSTE_STUNDEN + "." + art.kuerzel(),
                    alt.strafen().hoechsteStundenFuer(art),
                    neu.strafen().hoechsteStundenFuer(art));
        }
        nenneJaNein(geaendert, Strafwerte.PFAD_BANNLISTE_OEFFENTLICH,
                alt.strafen().bannlisteOeffentlich(),
                neu.strafen().bannlisteOeffentlich());
        nenneJaNein(geaendert, Strafwerte.PFAD_ANSAGEN,
                alt.strafen().ansagen(), neu.strafen().ansagen());
        nenneZahl(geaendert, Strafwerte.PFAD_ZEILEN_JE_SEITE,
                alt.strafen().zeilenJeSeite(), neu.strafen().zeilenJeSeite());
        // Die Liste der mitgesperrten Befehle als MENGE: Welcher Name dazukam und
        // welcher wegfiel, ist die Auskunft, die zaehlt. Eine Zahl sagte nur, dass
        // sich etwas geaendert hat - und eine Stummschaltung, die /msg nicht mehr
        // sperrt, ist genau dann wirkungslos, wenn niemand hinsieht.
        nenneMenge(geaendert, Strafwerte.PFAD_STUMM_SPERRT_BEFEHLE,
                new TreeSet<>(alt.strafen().stummSperrtBefehle()),
                new TreeSet<>(neu.strafen().stummSperrtBefehle()));
        // DIE EINSICHTSFRIST [Schritt 16a Teil D]: eine Zahl, und sie ist die Bremse
        // gegen das Vergessen. Wer sie kuerzer stellt, tut es, weil eben jemand die
        // Einsicht anliess - ein stummes Neuladen waere genau dann stumm.
        nenneZahl(geaendert, Einsichtswerte.PFAD_DAUER,
                alt.einsehen().dauerSekunden(), neu.einsehen().dauerSekunden());
        // Die Wechselstube [Teil F]: zwei Kurse und der Wechseldeckel. Aus demselben
        // Grund wie die Bloecke darueber - sie werden beim Gebrauch gefragt, es wird
        // nichts gebaut und nichts angemeldet. Ohne diese drei Zeilen aenderte sich der
        // KURS beim Neuladen stumm, und das ist der Wert, an dem ein Betreiber am
        // ehesten dreht.
        nenneZahl(geaendert, Wechselwerte.PFAD_HIN,
                alt.wechsel().markJeGoldmark(), neu.wechsel().markJeGoldmark());
        nenneZahl(geaendert, Wechselwerte.PFAD_ZURUECK,
                alt.wechsel().markZurueck(), neu.wechsel().markZurueck());
        nenneZahl(geaendert, Wechselwerte.PFAD_DECKEL,
                alt.wechsel().tagesdeckel(), neu.wechsel().tagesdeckel());
        // DAS MUENZHAUS [Teil G]: der Schalter, der Bestand des Katalogs und je Posten
        // sein Preis. Die drei Gestalten haben verschiedene Aufgaben: Der Schalter sagt,
        // ob ueberhaupt verkauft wird; die MENGE sagt, welcher Posten dazukam oder
        // wegfiel; und der Preis je Posten ist der Wert, an dem ein Betreiber am
        // haeufigsten dreht - er darf nicht unter "nichts geaendert" verschwinden.
        //
        // Der dynamische Pfad hat sein Vorbild bei der Stundenmenge je Taetigkeit weiter
        // oben. Genannt werden nur Posten, die in BEIDEN Katalogen stehen: Fuer einen
        // neuen Posten ist der Preis keine Aenderung, sondern sein erster Wert, und die
        // Menge nennt ihn schon.
        // DIE ANMELDUNG [Schritt 13 Teil C]. Beide Zeilen gehoeren genannt, und die
        // FASSUNG ist die wichtigere: Wer sie erhoeht, verlangt von allen eine neue
        // Zustimmung - das ist die folgenreichste Aenderung, die ein Neuladen tragen
        // kann, und sie waere ohne diese Zeile stumm. Wer sie VERSEHENTLICH erhoeht
        // hat, liest es hier und kann sie zuruecknehmen, bevor jemand beitritt.
        nenneJaNein(geaendert, Anmeldewerte.PFAD_AN,
                alt.anmeldung().an(), neu.anmeldung().an());
        nenneWort(geaendert, Anmeldewerte.PFAD_FASSUNG,
                alt.anmeldung().fassung(), neu.anmeldung().fassung());
        nenneJaNein(geaendert, Muenzwerte.PFAD_AN,
                alt.muenzhaus().an(), neu.muenzhaus().an());
        nenneMenge(geaendert, Muenzwerte.PFAD_KATALOG,
                new java.util.LinkedHashSet<>(alt.muenzhaus().schluessel()),
                new java.util.LinkedHashSet<>(neu.muenzhaus().schluessel()));
        for (Muenzwerte.Posten posten : neu.muenzhaus().katalog()) {
            var vorher = alt.muenzhaus().posten(posten.schluessel());
            if (vorher.isPresent()) {
                String wurzel = Muenzwerte.PFAD_KATALOG + "." + posten.schluessel() + ".";
                nenneZahl(geaendert, wurzel + Muenzwerte.SCHLUESSEL_PREIS,
                        vorher.get().preis(), posten.preis());
                // GELIEFERTES UND BEDINGUNGEN AUCH - nachgetragen am 20.11.2026: Mit nur
                // dem Preis gilt eine Bedingung von 1 auf 50 gehobene Parzellenzahl
                // sofort, und die Antwort des Befehls lautet "nichts geaendert". Dann
                // ist der Posten ueber Nacht fuer niemanden mehr frei, und niemand hat
                // es gelesen.
                nenneTabelle(geaendert, wurzel + Muenzwerte.SCHLUESSEL_GEGENSTAENDE,
                        vorher.get().gegenstaende(), posten.gegenstaende());
                nenneTabelle(geaendert, wurzel + Muenzwerte.SCHLUESSEL_BEDINGUNGEN,
                        vorher.get().bedingungen(), posten.bedingungen());
            }
        }

        // ============================================================================
        // GEFUNDEN AM 20.11.2026, beim Bau des Muenzhauses - und darum hier und nicht in
        // einem eigenen Schritt: Alles ab hier gehoert zu Bestandteilen, die es seit
        // Monaten gibt und die in dieser Methode NIE genannt wurden. Ihr Neuladen war
        // vollstaendig stumm, so wie es die Rangleiter bis Teil F und die zwei
        // Auflagenschalter bis zum 17.11.2026 waren. Es ist derselbe Fund zum dritten
        // Mal, und darum haelt ihn ab jetzt eine Tafel: Jeder PFAD_* aus dem Kern muss
        // hier genannt sein oder in GrenzeTest mit Begruendung ausgenommen.
        //
        // Gemessen wurde mit allen "Klasse.PFAD_X"-Vorkommen dieser Datei gegen alle
        // "public static final String PFAD_*" des Kerns. Darunter ist GELD - Ladenmiete,
        // Kaution, Lizenz, die drei Marktgebuehren -, also genau die Art Wert, fuer die
        // Teil F die Wechselkurse nachgetragen hat.
        // ============================================================================

        // Die Laeden der Markthalle: fuenf Zahlen, drei davon Geld. Eine geaenderte
        // Miete gilt ab dem naechsten Mietlauf, eine geaenderte Lizenz beim naechsten
        // Mieten - gebaut und angemeldet wird nichts davon.
        nenneZahl(geaendert, Mietwerte.PFAD_MIETE,
                alt.laeden().miete(), neu.laeden().miete());
        nenneZahl(geaendert, Mietwerte.PFAD_KAUTION,
                alt.laeden().kaution(), neu.laeden().kaution());
        nenneZahl(geaendert, Mietwerte.PFAD_LIZENZ,
                alt.laeden().lizenz(), neu.laeden().lizenz());
        nenneZahl(geaendert, Mietwerte.PFAD_DAUER,
                alt.laeden().dauerTage(), neu.laeden().dauerTage());
        nenneZahl(geaendert, Mietwerte.PFAD_AUSFAELLE,
                alt.laeden().ausfaelleBisRaeumung(), neu.laeden().ausfaelleBisRaeumung());

        // Der Kistenhandel: drei Gebuehrensaetze, die Seitenlaenge des Verzeichnisses und
        // das Wort, mit dem ein Bundshop sich ausweist. Das WORT steht hier als Zeile mit
        // Anfuehrungszeichen, wie die Rangwerbung - es ist Text und keine Zahl.
        nenneZahl(geaendert, Shopwerte.PFAD_SPIELER_KAUF,
                alt.handel().spielershopKauf(), neu.handel().spielershopKauf());
        nenneZahl(geaendert, Shopwerte.PFAD_SPIELER_VERKAUF,
                alt.handel().spielershopVerkauf(), neu.handel().spielershopVerkauf());
        nenneZahl(geaendert, Shopwerte.PFAD_BUND_VERKAUF,
                alt.handel().bundshopVerkauf(), neu.handel().bundshopVerkauf());
        nenneZahl(geaendert, Shopwerte.PFAD_ZEILEN,
                alt.handel().zeilenJeSeite(), neu.handel().zeilenJeSeite());
        nenneWort(geaendert, Shopwerte.PFAD_WORT,
                alt.handel().bundWort(), neu.handel().bundWort());

        // Die Freistaedte, zweiter Teil: die Buergerrechte als Menge, die Erstattung beim
        // Aufgeben und die zwei Ladengebuehren. Dazu der VERFALL - vier Zahlen, die
        // darueber entscheiden, wann eine Stadt zurueckfaellt.
        nenneMenge(geaendert, Freistadtwerte.PFAD_BUERGERRECHTE,
                taten(alt.freistaedte().buergerrechte()),
                taten(neu.freistaedte().buergerrechte()));
        nenneZahl(geaendert, Freistadtwerte.PFAD_ERSTATTUNG,
                alt.freistaedte().erstattungProzent(),
                neu.freistaedte().erstattungProzent());
        nenneZahl(geaendert, Freistadtwerte.PFAD_LADENGEBUEHR_ANLAGE,
                alt.freistaedte().ladengebuehrAnlage(),
                neu.freistaedte().ladengebuehrAnlage());
        nenneZahl(geaendert, Freistadtwerte.PFAD_LADENGEBUEHR_TAG,
                alt.freistaedte().ladengebuehrTag(),
                neu.freistaedte().ladengebuehrTag());
        nenneJaNein(geaendert, Verfallswerte.PFAD_AN,
                alt.freistaedte().verfall().an(), neu.freistaedte().verfall().an());
        nenneZahl(geaendert, Verfallswerte.PFAD_UNBESUCHT_TAGE,
                alt.freistaedte().verfall().unbesuchtTage(),
                neu.freistaedte().verfall().unbesuchtTage());
        nenneZahl(geaendert, Verfallswerte.PFAD_WARNUNG_FRUEH,
                alt.freistaedte().verfall().warnungFruehTage(),
                neu.freistaedte().verfall().warnungFruehTage());
        nenneZahl(geaendert, Verfallswerte.PFAD_WARNUNG_SPAET,
                alt.freistaedte().verfall().warnungSpaetTage(),
                neu.freistaedte().verfall().warnungSpaetTage());

        // Wolkenwerder, zweiter Teil: Starttruhe, Biompreis, die Erzausbeute, die
        // Herausforderungen und die Punktetabelle. Die drei TABELLEN werden gezaehlt und
        // verglichen, nicht aufgezaehlt - was in einer Zeile steht, nennt der
        // Startbericht des Lesers.
        nenneTabelle(geaendert, Wolkenwerte.PFAD_STARTTRUHE,
                alt.wolken().starttruhe(), neu.wolken().starttruhe());
        nenneZahl(geaendert, Wolkenwerte.PFAD_BIOMPREIS,
                alt.wolken().biompreis(), neu.wolken().biompreis());
        nenneJaNein(geaendert, Erzwerte.PFAD_AN,
                alt.wolken().erze().an(), neu.wolken().erze().an());
        nenneZahl(geaendert, Erzwerte.PFAD_GRUND,
                alt.wolken().erze().grundProzent(), neu.wolken().erze().grundProzent());
        nenneZahl(geaendert, Erzwerte.PFAD_JE_STUFE,
                alt.wolken().erze().jeStufeProzent(),
                neu.wolken().erze().jeStufeProzent());
        nenneZahl(geaendert, Erzwerte.PFAD_HOECHST,
                alt.wolken().erze().hoechstProzent(),
                neu.wolken().erze().hoechstProzent());
        nenneJaNein(geaendert, Erzwerte.PFAD_MARKEN,
                alt.wolken().erze().marken(), neu.wolken().erze().marken());
        nenneTabelle(geaendert, Erzwerte.PFAD_TABELLE,
                alt.wolken().erze().tabelle(), neu.wolken().erze().tabelle());
        nenneJaNein(geaendert, Aufgabenwerte.PFAD_AN,
                alt.wolken().aufgaben().an(), neu.wolken().aufgaben().an());
        nenneMenge(geaendert, Aufgabenwerte.PFAD_LISTE,
                new java.util.LinkedHashSet<>(alt.wolken().aufgaben().schluessel()),
                new java.util.LinkedHashSet<>(neu.wolken().aufgaben().schluessel()));
        // JE HERAUSFORDERUNG das Verlangte und die Belohnung - derselbe Nachtrag wie bei
        // der Rangleiter und beim Katalog: Die BELOHNUNG ist Geld, und Geld, das sich
        // beim Neuladen stumm aendert, ist genau der Fall, gegen den Teil F die
        // Wechselkurse nachgetragen hat.
        for (Aufgabenwerte.Aufgabe aufgabe : neu.wolken().aufgaben().liste()) {
            var vorher = alt.wolken().aufgaben().aufgabe(aufgabe.schluessel());
            if (vorher.isEmpty()) {
                continue;
            }
            String wurzel = Aufgabenwerte.PFAD_LISTE + "." + aufgabe.schluessel() + ".";
            nenneTabelle(geaendert, wurzel + Aufgabenwerte.SCHLUESSEL_VERLANGT,
                    vorher.get().verlangt(), aufgabe.verlangt());
            nenneZahl(geaendert, wurzel + Aufgabenwerte.SCHLUESSEL_BELOHNUNG,
                    vorher.get().belohnung(), aufgabe.belohnung());
        }
        nenneTabelle(geaendert, Punktwerte.PFAD_TABELLE,
                alt.wolken().punkte().tabelle(), neu.wolken().punkte().tabelle());
        nenneZahl(geaendert, Punktwerte.PFAD_CHUNKS_JE_TAKT,
                alt.wolken().punkte().chunksJeTakt(),
                neu.wolken().punkte().chunksJeTakt());
        nenneZahl(geaendert, Punktwerte.PFAD_ABKLINGZEIT,
                alt.wolken().punkte().abklingSekunden(),
                neu.wolken().punkte().abklingSekunden());
        // Die Lotterie in Altfurt (Plan 14): sechs Zahlen und die Gewinntabelle. Alles
        // davon ist GELD - der Lospreis direkt, die Gewinne auf der anderen Seite. Eine
        // stumm geaenderte Staffel liesse jeden Spieler ab morgen zu einem Preis ziehen,
        // den niemand eingestellt hat; eine stumm geaenderte Tabelle verschoebe die
        // Wahrscheinlichkeiten, und das merkt niemand, weil jeder Zug ohnehin anders
        // ausgeht.
        nenneJaNein(geaendert, Lotteriewerte.PFAD_AN,
                alt.lotterie().an(), neu.lotterie().an());
        nenneZahl(geaendert, Lotteriewerte.PFAD_FREIE_LOSE,
                alt.lotterie().freieLoseJeTag(), neu.lotterie().freieLoseJeTag());
        nenneZahl(geaendert, Lotteriewerte.PFAD_HOECHSTE_LOSE,
                alt.lotterie().hoechsteLoseJeTag(), neu.lotterie().hoechsteLoseJeTag());
        nenneZahl(geaendert, Lotteriewerte.PFAD_GRUNDPREIS,
                alt.lotterie().grundpreis(), neu.lotterie().grundpreis());
        nenneZahl(geaendert, Lotteriewerte.PFAD_STEIGERUNG,
                alt.lotterie().steigerungProzent(), neu.lotterie().steigerungProzent());
        nenneZahl(geaendert, Lotteriewerte.PFAD_HOECHSTPREIS,
                alt.lotterie().hoechstpreis(), neu.lotterie().hoechstpreis());
        nenneMenge(geaendert, Lotteriewerte.PFAD_GEWINNE,
                new java.util.LinkedHashSet<>(alt.lotterie().schluessel()),
                new java.util.LinkedHashSet<>(neu.lotterie().schluessel()));
        for (Lotteriewerte.Gewinn gewinn : neu.lotterie().gewinne()) {
            var vorher = alt.lotterie().gewinn(gewinn.schluessel());
            if (vorher.isEmpty()) {
                continue;
            }
            String wurzel = Lotteriewerte.PFAD_GEWINNE + "." + gewinn.schluessel() + ".";
            nenneWort(geaendert, wurzel + Lotteriewerte.SCHLUESSEL_ART,
                    vorher.get().art().name(), gewinn.art().name());
            nenneZahl(geaendert, wurzel + Lotteriewerte.SCHLUESSEL_GEWICHT,
                    vorher.get().gewicht(), gewinn.gewicht());
            nenneZahl(geaendert, wurzel + Lotteriewerte.SCHLUESSEL_MENGE,
                    vorher.get().menge(), gewinn.menge());
            nenneWort(geaendert, wurzel + Lotteriewerte.SCHLUESSEL_GEGENSTAND,
                    vorher.get().gegenstand(), gewinn.gegenstand());
            nenneWort(geaendert, wurzel + Lotteriewerte.SCHLUESSEL_NAME,
                    vorher.get().anzeigename(), gewinn.anzeigename());
        }

        // Der Angelwettbewerb (Plan 14): das Fenster, zwei Geldzahlen, die Verteilung
        // und die Punkte. Das FENSTER ist dabei das Besondere - es ist die erste
        // Uhrzeit dieses Projekts, und eine stumm geaenderte Beginnzeit heisst, dass
        // Spieler eine Stunde lang an einem Teich auf einen Wettbewerb warten, der
        // woanders schon lief.
        nenneJaNein(geaendert, Wettbewerbswerte.PFAD_AN,
                alt.wettbewerb().an(), neu.wettbewerb().an());
        nenneWort(geaendert, Wettbewerbswerte.PFAD_BEGINN,
                alt.wettbewerb().beginn().toString(), neu.wettbewerb().beginn().toString());
        nenneZahl(geaendert, Wettbewerbswerte.PFAD_DAUER,
                alt.wettbewerb().dauer().toMinutes(), neu.wettbewerb().dauer().toMinutes());
        nenneMenge(geaendert, Wettbewerbswerte.PFAD_TAGE,
                wochentage(alt.wettbewerb()), wochentage(neu.wettbewerb()));
        nenneZahl(geaendert, Wettbewerbswerte.PFAD_STARTGELD,
                alt.wettbewerb().startgeld(), neu.wettbewerb().startgeld());
        nenneZahl(geaendert, Wettbewerbswerte.PFAD_ZUSCHUSS,
                alt.wettbewerb().zuschuss(), neu.wettbewerb().zuschuss());
        nenneWort(geaendert, Wettbewerbswerte.PFAD_VERTEILUNG,
                alt.wettbewerb().verteilungProzent().toString(),
                neu.wettbewerb().verteilungProzent().toString());
        nenneZahl(geaendert, Wettbewerbswerte.PFAD_PUNKTE_JE_FANG,
                alt.wettbewerb().punkteJeFang(), neu.wettbewerb().punkteJeFang());
        nenneTabelle(geaendert, Wettbewerbswerte.PFAD_PUNKTE,
                alt.wettbewerb().punkte(), neu.wettbewerb().punkte());

        if (!alt.rangWerbung().equals(neu.rangWerbung())) {
            geaendert.add(Wirtschaftswerte.PFAD_RANG_WERBUNG + ": \"" + alt.rangWerbung()
                    + "\" -> \"" + neu.rangWerbung() + "\"");
        }

        List<String> nichtAngefasst = nichtAngefasst(ausDerDatei);
        stand = neu;
        return new Uebernahme(geaendert, nichtAngefasst);
    }

    /**
     * Was von diesem Stand ein Neuladen nicht anfasst - auch wenn gar nichts uebernommen
     * wurde.
     *
     * <p>Oeffentlich und ohne Nebenwirkung, weil der Block in JEDER Antwort stehen muss:
     * auch dann, wenn eine Klage das Uebernehmen verhindert hat. Sonst erfaehrt gerade
     * der Betreiber, bei dem etwas schiefging, nicht, dass Startguthaben, Tagesgrenze und
     * Takt ohnehin nicht neuladbar sind - und sucht den Fehler an der falschen Zeile.
     *
     * <p>Jede Zeile nennt zuerst, was GILT, und erst dann, was neu gelesen wurde. Der Leser
     * der Einstellungsdatei darf das nicht tun: Er kennt nur die Datei.
     *
     * @param ausDerDatei was gelesen wurde, oder {@code null}, wenn nichts gilt - dann
     *                    nennt die Zeile nur, was gerade gilt
     */
    public List<String> nichtAngefasst(Wirtschaftswerte ausDerDatei) {
        Wirtschaftswerte jetzt = stand;
        List<String> zeilen = new ArrayList<>();
        nurBeimStart(zeilen, Wirtschaftswerte.PFAD_STARTGUTHABEN,
                String.valueOf(jetzt.startguthaben()),
                ausDerDatei == null ? null : String.valueOf(ausDerDatei.startguthaben()),
                "Ein neuer Wert im Betrieb aenderte rueckwirkend das Guthaben jedes"
                        + " Spielers, der noch nie gebucht hat.");
        nurBeimStart(zeilen, Zeitwerte.PFAD_ZONE, jetzt.zeit().zone().getId(),
                ausDerDatei == null ? null : ausDerDatei.zeit().zone().getId(),
                "Eine neue Zone im Betrieb verschoebe die Tagesgrenze mitten am Tag;"
                        + " derselbe Tag wuerde dann zweimal abgerechnet oder fiele aus.");
        nurBeimStart(zeilen, Zeitwerte.PFAD_TAKT, jetzt.zeit().taktSekunden() + " s",
                ausDerDatei == null ? null : ausDerDatei.zeit().taktSekunden() + " s",
                "Der Abstand steckt im gestellten Zeitgeber; ihn neu zu stellen liesse zwei"
                        + " Zeitgeber dieselbe Spielzeit doppelt schreiben.");
        // Die vierte Zeile entsteht nur im Ausnahmefall von uebernimm(): Die Grenze IST
        // neuladbar, dieses eine Paar passt aber nicht zum laufenden Takt. Dieselbe Frage
        // wie dort, an derselben einen Regel - nur einmal fuer die Tat und einmal fuer das
        // Wort, denn nichtAngefasst() wird auch ohne Uebernahme gerufen.
        if (ausDerDatei != null && !Zeitwerte.brauchbareGrenzeZumTakt(
                jetzt.zeit().taktSekunden(), ausDerDatei.zeit().untaetigMinuten())) {
            zeilen.add(Zeitwerte.PFAD_UNTAETIG + ": es gilt weiter "
                    + jetzt.zeit().untaetigMinuten() + " min, neu gelesen wurde "
                    + ausDerDatei.zeit().untaetigMinuten() + " min - NICHT uebernommen. Die"
                    + " Grenze ist eine Stufe und kein Schnitt: Zum laufenden Takt von "
                    + jetzt.zeit().taktSekunden() + " s wuerde sie den GANZEN Takt"
                    + " wegwerfen, auch die aktiv gespielte Zeit darin. Mindestens "
                    + Math.ceilDiv(jetzt.zeit().taktSekunden(), 60L) + " min passen zu"
                    + " diesem Takt, 0 schaltet die Regel ab - oder " + Zeitwerte.PFAD_TAKT
                    + " kuerzen und neu starten, dann uebernimmt der Start beides zusammen.");
        }
        return zeilen;
    }

    /**
     * Eine Zeile des Blocks - fuer alle drei Schluessel dieselbe Form und dasselbe
     * Vergleichen.
     *
     * <p>Sie sagt bewusst NICHT "in der Datei steht", sondern "neu gelesen wurde". Der
     * Unterschied ist gemessen und nicht Geschmack: Fehlt die Zeile in der Datei, holt
     * {@code get} die Vorgabe stumm aus der Jar, und der Leser reicht sie hier herein. Die
     * Antwort lautete dann "es gilt weiter America/New_York, in der Datei steht
     * Europe/Berlin" - in der Datei stand keine Zeile, und der Betreiber suchte sie. Ob ein
     * Wert gelesen oder zurueckgefallen ist, sieht allein der Leser; er nennt die fehlenden
     * Zeilen selbst und schreibt fuer sie keinen Geltungssatz (siehe {@link #NUR_BEIM_START}).
     *
     * @param gilt    was gerade gilt - Klartext, damit der Takt seine Einheit behaelt
     * @param gelesen was der Leser mitgebracht hat, oder {@code null}, wenn nichts gilt
     * @param grund   warum ein Neuladen das nicht anfasst
     */
    private static void nurBeimStart(List<String> zeilen, String pfad, String gilt,
                                     String gelesen, String grund) {
        if (gelesen == null || gelesen.equals(gilt)) {
            zeilen.add(pfad + ": " + gilt + " - wird nur beim Start gelesen.");
            return;
        }
        zeilen.add(pfad + ": es gilt weiter " + gilt + ", neu gelesen wurde " + gelesen
                + " - erst ein Neustart uebernimmt das. " + grund);
    }

    /**
     * Nennt eine Namensmenge, die sich geaendert hat - mit ihrer Laenge und dem
     * Unterschied.
     *
     * <p>Nicht die ganze Liste: Sechzehn Namen "alt -> neu" waeren keine Antwort mehr,
     * sondern eine Wand. Aber auch nicht nur die Laenge - ein ausgetauschter Name bei
     * gleicher Laenge ist genau die Aenderung, die gerade gemacht wurde. Genannt wird
     * darum die Laenge UND was hinzukam oder wegfiel.
     */
    private static void nenneMenge(List<String> geaendert, String pfad,
                                   Set<String> alt, Set<String> neu) {
        if (alt.equals(neu)) {
            return;
        }
        Set<String> dazu = new TreeSet<>(neu);
        dazu.removeAll(alt);
        Set<String> weg = new TreeSet<>(alt);
        weg.removeAll(neu);
        StringBuilder satz = new StringBuilder(pfad + ": " + alt.size() + " -> "
                + neu.size() + " Namen");
        if (!dazu.isEmpty()) {
            satz.append(", dazu: ").append(String.join(", ", dazu));
        }
        if (!weg.isEmpty()) {
            satz.append(", weg: ").append(String.join(", ", weg));
        }
        geaendert.add(satz.toString());
    }

    /**
     * Die Schluessel einer Artenmenge - damit {@link #nenneMenge} sie vergleichen kann.
     *
     * <p>Die Schluessel und nicht {@code name()}: In der Antwort an den Betreiber soll
     * stehen, was in SEINER Datei steht. Ein {@code LinkedHashSet} haelt dabei die
     * Reihenfolge der Datei - nenneMenge sortiert den Unterschied ohnehin selbst.
     */
    private static Set<String> schluessel(Set<Lebenszeichen> arten) {
        Set<String> namen = new java.util.LinkedHashSet<>();
        for (Lebenszeichen art : arten) {
            namen.add(art.schluessel());
        }
        return namen;
    }

    private static void nenneZahl(List<String> hinein, String pfad, long alt, long neu) {
        if (alt != neu) {
            hinein.add(pfad + ": " + alt + " -> " + neu);
        }
    }

    /**
     * Dasselbe fuer einen TEXT - mit Anfuehrungszeichen, damit ein Leerzeichen sichtbar
     * ist.
     *
     * <p>Dieselbe Gestalt, die die Rangwerbung schon hatte, bevor sie eine Methode war.
     */
    private static void nenneWort(List<String> hinein, String pfad, String alt,
            String neu) {
        if (!alt.equals(neu)) {
            hinein.add(pfad + ": \"" + alt + "\" -> \"" + neu + "\"");
        }
    }

    /**
     * Eine TABELLE: ihre Laenge und dass sie sich geaendert hat.
     *
     * <p>Dieselbe Antwort wie bei der Lohntafel, und aus demselben Grund: Eine Zeile mit
     * einem anderen Wert laesst die Laenge gleich, und genau das ist die Aenderung, die
     * der Betreiber gerade gemacht hat. Aufgezaehlt wird sie nicht - vierzig Zeilen
     * "alt -> neu" waeren keine Antwort mehr, sondern eine Wand; was in einer Zeile
     * steht, nennt der Startbericht des Lesers.
     */
    private static void nenneTabelle(List<String> hinein, String pfad, Map<?, ?> alt,
            Map<?, ?> neu) {
        if (!alt.equals(neu)) {
            hinein.add(pfad + ": " + alt.size() + " -> " + neu.size()
                    + " Zeilen (geaendert)");
        }
    }

    /**
     * Die Schluessel einer Tatenmenge - damit {@link #nenneMenge} sie vergleichen kann.
     *
     * <p>Wie bei den Lebenszeichen: die Schluessel und nicht {@code name()}, damit in der
     * Antwort steht, was in der Datei des Betreibers steht.
     */
    private static Set<String> taten(Set<Tat> rechte) {
        Set<String> namen = new java.util.LinkedHashSet<>();
        for (Tat recht : rechte) {
            namen.add(recht.schluessel());
        }
        return namen;
    }

    /**
     * Dasselbe fuer ein Ja oder Nein - den gab es bis zum 17.11.2026 nicht.
     *
     * <p>Eigene Methode und kein {@code nenneZahl} mit 0 und 1: Der Betreiber hat
     * {@code true} in die Datei geschrieben und soll {@code true} im Bericht lesen.
     * Eine 1 waere eine Uebersetzung, die er zurueckrechnen muesste.
     */
    private static void nenneJaNein(List<String> hinein, String pfad, boolean alt,
            boolean neu) {
        if (alt != neu) {
            hinein.add(pfad + ": " + alt + " -> " + neu);
        }
    }

    /**
     * Die Wochentage eines Wettbewerbs als Textmenge - in der Reihenfolge der Woche.
     *
     * <p>Als TEXT und nicht als Menge von {@code DayOfWeek}: Die Zeile "alt -> neu"
     * nennte sonst MONDAY, und der Betreiber tippt in seiner Datei "montag". Sortiert
     * nach der Woche und nicht nach dem Alphabet - eine Liste, die mit Donnerstag
     * anfaengt, liest niemand.
     */
    private static java.util.Set<String> wochentage(Wettbewerbswerte werte) {
        java.util.Set<String> namen = new java.util.LinkedHashSet<>();
        for (java.time.DayOfWeek tag : java.time.DayOfWeek.values()) {
            if (werte.tage().contains(tag)) {
                namen.add(tag.name().toLowerCase(java.util.Locale.ROOT));
            }
        }
        return namen;
    }

}
