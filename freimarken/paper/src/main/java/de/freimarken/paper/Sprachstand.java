package de.freimarken.paper;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

/**
 * Die veraenderliche Zelle der Saetze - was gerade gilt.
 *
 * <p>Wortgleich die Form von {@code Wirtschaftsstand}, und aus wortgleichem Grund: Jede
 * Stelle, die einen Satz braucht, haelt diesen Halter als unveraenderliches Feld und fragt
 * ihn im Augenblick des Gebrauchs. Ein neuer Wortlaut erreicht damit jede Stelle, ohne dass
 * ein einziges Objekt neu gebaut wird - kein Zuhoerer wird angemeldet, kein Zeitgeber
 * gestellt, kein Befehl neu eingehaengt. Also gibt es auch nichts abzumelden, und nichts
 * kann doppelt dastehen.
 *
 * <p>{@code volatile} auf einem unveraenderlichen {@code record}: Die Vervollstaendigung
 * von Befehlen und das Senden des Befehlsbaums laufen ausdruecklich nebenlaeufig, und ein
 * Spieler kann mitten im Tausch eine Antwort bekommen. So sieht jeder Leser entweder das
 * alte oder das neue Buch - nie eine Mischung und nie ein halb gefuelltes.
 *
 * <p>Farbe steht in dieser Datei, und das ist Absicht: {@link #antwort} baut die Antwort
 * auf {@code /freimarken neuladen}, und das ist BETREIBERTEXT. Nach der Grenze dieses
 * Umbaus bleibt er im Programm - eine Diagnose darf nicht von der Datei abhaengen, deren
 * Zustand sie meldet. Darum steht {@code Sprachstand.java} in der erwarteten Farbmenge von
 * {@code GrenzeTest} und wird dort nie gestrichen.
 */
final class Sprachstand {

    /**
     * Die Schluessel, die ein Neuladen NICHT uebernimmt - die EINE Liste davon.
     *
     * <p>Sie steht in der Klasse, die es entscheidet: {@link #uebernimm} laesst genau diese
     * neun stehen, und {@link #nichtAngefasst} nennt genau diese neun. Gab es die Liste
     * zweimal, sagten Leser und Halter in derselben Antwort Verschiedenes - genau das ist
     * bei den Wirtschaftswerten einmal passiert.
     *
     * <p>Der Grund ist derselbe wie dort beim Zeitgeber: Die neun Befehlsbeschreibungen
     * gehen in {@code FreimarkenPlugin:529-564} bei der ANMELDUNG an
     * {@code verzeichnis.register()}. Ein Neuladen meldet keinen Befehl neu an - das ist
     * der ganze Trick dieser Form -, also wirkt eine geaenderte Beschreibung erst nach
     * einem Neustart. {@code /help} zeigt bis dahin die alte.
     */
    static final List<Satz> NUR_BEIM_START = BundSatz.beschreibungen();

    /** Was ein Neuladen bewirkt hat - und was es ausdruecklich nicht angefasst hat. */
    record Uebernahme(List<String> geaendert, List<String> nichtAngefasst) {
    }

    private volatile Sprachbuch stand;

    Sprachstand(Sprachbuch anfang) {
        if (anfang == null) {
            throw new IllegalArgumentException("Ohne Anfangsbuch gibt es nichts zu halten");
        }
        this.stand = anfang;
    }

    /** Was JETZT gilt. Beim Gebrauch zu fragen und nie in ein Feld zu kopieren. */
    Sprachbuch buch() {
        return stand;
    }

    /**
     * Uebernimmt, was neu gelesen wurde - und laesst stehen, was ein Neuladen nicht
     * aendern darf.
     *
     * <p>Gemeldet wird der SCHLUESSEL und nicht der neue Wortlaut: Eine Antwort mit 231
     * Saetzen liest niemand, und wer einen Satz geaendert hat, weiss, was er getippt hat.
     *
     * @param ausDerDatei ein vollstaendig gelesenes und geprueftes Buch
     */
    Uebernahme uebernimm(Sprachbuch ausDerDatei) {
        if (ausDerDatei == null) {
            throw new IllegalArgumentException("Nichts gelesen heisst nichts uebernehmen");
        }
        Sprachbuch alt = stand;
        Map<String, String> saetze = new LinkedHashMap<>(ausDerDatei.saetze());
        Map<String, DateTimeFormatter> muster = new LinkedHashMap<>(ausDerDatei.muster());
        Set<String> ausDerDateiJetzt = new LinkedHashSet<>(ausDerDatei.ausDerDatei());
        for (Satz satz : NUR_BEIM_START) {
            for (String pfad : satz.pfade()) {
                saetze.put(pfad, alt.saetze().get(pfad));
                if (alt.ausDerDatei().contains(pfad)) {
                    ausDerDateiJetzt.add(pfad);
                } else {
                    ausDerDateiJetzt.remove(pfad);
                }
            }
            if (satz.art() == Satz.Art.MUSTER) {
                muster.put(satz.pfad(), alt.muster().get(satz.pfad()));
            }
        }
        Sprachbuch neu = new Sprachbuch(saetze, ausDerDateiJetzt, muster);

        List<String> geaendert = new ArrayList<>();
        for (String pfad : saetze.keySet()) {
            if (!saetze.get(pfad).equals(alt.saetze().get(pfad))) {
                geaendert.add(pfad);
            }
        }
        List<String> nichtAngefasst = nichtAngefasst(ausDerDatei);
        // Der Tausch ist EIN Schreibzug auf ein unveraenderliches Buch - danach kann kein
        // Leser einen Satz aus dem alten neben einem aus dem neuen sehen.
        stand = neu;
        return new Uebernahme(List.copyOf(geaendert), nichtAngefasst);
    }

    /**
     * Was von diesem Stand ein Neuladen nicht anfasst - auch wenn gar nichts uebernommen
     * wurde.
     *
     * <p>Oeffentlich und ohne Nebenwirkung, weil der Block in JEDER Antwort stehen muss:
     * auch dann, wenn eine Klage das Uebernehmen verhindert hat. Sonst erfaehrt gerade der
     * Betreiber, bei dem etwas schiefging, nicht, dass die Befehlsbeschreibungen ohnehin
     * nicht neuladbar sind - und sucht den Fehler an der falschen Zeile.
     *
     * @param ausDerDatei was gelesen wurde, oder {@code null}, wenn nichts gilt - dann
     *                    nennt die Zeile nur, dass es nicht neuladbar ist
     */
    List<String> nichtAngefasst(Sprachbuch ausDerDatei) {
        Sprachbuch jetzt = stand;
        List<String> zeilen = new ArrayList<>();
        zeilen.add("befehl.beschreibung.* (" + NUR_BEIM_START.size() + " Saetze) - sie"
                + " stehen seit der Anmeldung im Befehlsbaum; /help zeigt eine neue erst"
                + " nach einem Neustart.");
        if (ausDerDatei == null) {
            return List.copyOf(zeilen);
        }
        for (Satz satz : NUR_BEIM_START) {
            String gilt = jetzt.saetze().get(satz.pfad());
            String gelesen = ausDerDatei.saetze().get(satz.pfad());
            if (gelesen != null && !gelesen.equals(gilt)) {
                zeilen.add("  " + satz.pfad() + ": es gilt weiter \"" + gilt + "\", neu"
                        + " gelesen wurde \"" + gelesen + "\" - erst ein Neustart"
                        + " uebernimmt das.");
            }
        }
        return List.copyOf(zeilen);
    }

    /**
     * Die ganze Antwort auf den Sprachteil von {@code /freimarken neuladen} - als Zeilen.
     *
     * <p>Eine reine Methode ohne {@code Player} und ohne Server: Nur so kann eine Probe
     * nachsehen, WAS in der Antwort steht, und nicht bloss, dass etwas geschieht. Das ist
     * dasselbe Muster, das {@code SpielzeitBefehle.sende(Audience, String, List)} (:194-200)
     * im Projekt schon vormacht - sein Javadoc nennt den Grund: "damit eine Probe eine
     * Audience mitschreiben lassen kann".
     *
     * <p>Drei Lagen, und der Block "nicht angefasst" steht in ALLEN drei: Klage,
     * uebernommen, nichts geaendert. Er ist das Gegenmittel gegen das haeufigste
     * Fehlerbild eines Neuladens - der Befehl meldet Erfolg, und ein Teil wirkt erst nach
     * dem Neustart.
     */
    List<Component> antwort(Sprachliste.Ergebnis gelesen) {
        List<Component> zeilen = new ArrayList<>();
        if (!gelesen.klagen().isEmpty()) {
            zeilen.add(Component.text("Sprache NICHT uebernommen - "
                    + gelesen.klagen().size() + " Zeile(n) stimmen nicht:",
                    NamedTextColor.RED));
            for (String klage : gelesen.klagen()) {
                zeilen.add(Component.text("  " + klage, NamedTextColor.RED));
            }
            zeilen.add(Component.text("Es gelten weiter "
                    + stand.ausDerDatei().size() + " eigene und "
                    + (stand.saetze().size() - stand.ausDerDatei().size())
                    + " ausgelieferte Saetze.", NamedTextColor.GRAY));
            nenneNichtAngefasst(zeilen, nichtAngefasst(null));
            return List.copyOf(zeilen);
        }
        Uebernahme uebernahme = uebernimm(gelesen.buch());
        if (uebernahme.geaendert().isEmpty()) {
            zeilen.add(Component.text("Gelesen - an den Saetzen hat sich nichts geaendert.",
                    NamedTextColor.GRAY));
        } else {
            zeilen.add(Component.text("Sprache uebernommen - "
                    + uebernahme.geaendert().size() + " Satz/Saetze:",
                    NamedTextColor.GREEN));
            for (String pfad : uebernahme.geaendert()) {
                zeilen.add(Component.text("  " + pfad, NamedTextColor.GREEN));
            }
        }
        for (String zeile : gelesen.bericht()) {
            zeilen.add(Component.text("  " + zeile, NamedTextColor.DARK_GRAY));
        }
        nenneNichtAngefasst(zeilen, uebernahme.nichtAngefasst());
        return List.copyOf(zeilen);
    }

    private static void nenneNichtAngefasst(List<Component> zeilen, List<String> ausDemStand) {
        zeilen.add(Component.text("Nur beim Start gelesen - das aendert kein Neuladen:",
                NamedTextColor.GRAY));
        for (String zeile : ausDemStand) {
            zeilen.add(Component.text("  " + zeile, NamedTextColor.DARK_GRAY));
        }
    }
}
