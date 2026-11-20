package de.freimarken.paper;

import java.util.Set;

/**
 * Die Saetze der Meerinseln von Hafenwerder (Plan 6.2, Schritt 10).
 *
 * <p>Ein eigener Bereich und keine Zeilen bei {@link FreistadtSatz}: Eine Insel ist keine
 * Stadt. Sie hat keinen Namen, keine Kasse, keine Buerger und keine Auflage - sie ist ein
 * Stueck Besitz mit einem Festpreis, und was ein Spieler darueber liest, soll nicht nach
 * Stadtverwaltung klingen.
 *
 * <p><b>Was hier steht, ist der Vertrag und kein Wort.</b> Pfad, Art und die Namen der
 * Werte - der Wortlaut steht NUR in {@code sprache-vorlage.yml}. Deshalb ist die
 * Platzhalterprobe nicht selbstbezueglich: Wer beim Abschreiben einem Satz seinen
 * {@code {preis}} wegnimmt, bekommt eine rote Probe und keinen stummen Fehler.
 *
 * <p><b>Die fuenf LAGE-Saetze dienen ZWEI Zweigen</b>, und das ist eine Entscheidung:
 * {@code /werder hafen} fragt "was ist hier", {@code /werder hafen nehmen} bekommt bei
 * denselben drei Lagen eine Absage - offenes Meer, Ankunftsinsel, fremde Insel. Zwei
 * Satzpaare dafuer waeren zwei Beschreibungen derselben Stelle, und eine von ihnen wuerde
 * eines Tages anders. Was WIRKLICH nur zur Absage gehoert - die Obergrenze, das fehlende
 * Geld -, steht darum getrennt unter {@code absage}.
 */
enum WerderSatz implements Satz {

    /**
     * Die Ueberschrift ueber den eigenen Inseln - mit ihrer Zahl.
     *
     * <p>Gezaehlt, und die Null gehoert dazu: Wer keine Insel hat, liest statt der Liste
     * den Satz {@link #LEER} - die Null erscheint also nie im Spiel. Aufgeschrieben wird
     * sie trotzdem (Probe 11b verlangt sie), und das ist richtig: Wer den Zweig eines
     * Tages umbaut, sieht an der Tafel, welche Form dann herauskaeme.
     */
    KOPF("werder.kopf", Art.GEZAEHLT, "anzahl"),

    /** Eine Zeile je Insel - die Feldnummer ist ihre Adresse. */
    ZEILE("werder.zeile", Art.SATZ, "feld"),

    /**
     * Du hast keine Insel.
     *
     * <p>Diese Zeile MUSS es geben: Wer nur die Ueberschrift sieht, haelt es fuer einen
     * Fehler - und am ersten Tag ist das der Normalfall. Sie nennt den PREIS, denn das
     * ist die Frage, die als naechste kommt.
     */
    LEER("werder.leer", Art.SATZ, "preis"),

    /** Offenes Meer - hier gehoert nichts jemandem, und hier baut niemand. */
    HIER_MEER("werder.hier.meer", Art.SATZ),

    /**
     * Die Ankunftsinsel.
     *
     * <p>Ein eigener Satz neben {@link #HIER_FREI}, und zwar ein notwendiger: Dort stuende
     * sonst "frei - /werder hafen nehmen", und das waere eine Luege. Feld 0/0 gehoert
     * niemandem und bleibt es; hier landet das Tor aus Altfurt.
     */
    HIER_ANKUNFT("werder.hier.ankunft", Art.SATZ),

    /** Eine freie Insel - mit ihrer Adresse und dem Preis. */
    HIER_FREI("werder.hier.frei", Art.SATZ, "feld", "preis"),

    /** Die eigene Insel. */
    HIER_EIGENE("werder.hier.eigene", Art.SATZ, "feld"),

    /** Die Insel eines anderen - mit Adresse und Besitzer. */
    HIER_FREMD("werder.hier.fremd", Art.SATZ, "feld", "besitzer"),

    /**
     * Genommen - mit Adresse, Preis und dem, was danach bleibt.
     *
     * <p>Was DANACH bleibt und nicht nur der Preis: 75.000 liest man, ohne es zu fuehlen;
     * was uebrig ist, fuehlt man. Dieselbe Entscheidung wie bei
     * {@code freistadt.frage}.
     */
    GENOMMEN("werder.genommen", Art.SATZ, "feld", "preis", "danach"),

    /**
     * Genommen, und es hat nichts gekostet.
     *
     * <p>Ein eigener Satz und keine 0 im Satz darueber: {@code inseln.preis: 0} ist
     * erlaubt, und "0 Mark abgebucht, danach hast du 1.200 Mark" ist eine Zeile, die
     * niemand schreiben wuerde. Dieselbe Form wie {@code parzelle.gratis}.
     */
    GENOMMEN_GRATIS("werder.genommen-gratis", Art.SATZ, "feld"),

    /** Jenseits der Weltgrenze - dort gibt es keine Inselfelder mehr. */
    ABSAGE_AUSSERHALB("werder.absage.ausserhalb", Art.SATZ),

    /**
     * Die Obergrenze ist erreicht - mit der Zahl, die fuer diesen Spieler gilt.
     *
     * <p>Gezaehlt, weil die Zahl aus einem RECHT kommt und nicht aus dem Programm: Wer
     * {@code freimarken.inseln.3} hat, liest eine andere Zahl als jeder andere.
     */
    ABSAGE_GENUG("werder.absage.genug", Art.GEZAEHLT, "hoechstzahl"),

    /**
     * Die Zeile aus der {@code config.yml} unter der Absage "du hast genug".
     *
     * <p>Nur die FARBE ist hier des Betreibers, der Wortlaut steht in
     * {@code hinweise.rang-werbung}. Gebaut wird sie in {@link Rangwerbung} - der einen
     * Stelle, die alle fuenf Grenzen bedient.
     */
    RANG_WERBUNG("werder.rang-werbung", Art.SATZ, "werbung"),

    /** Das Guthaben reicht nicht - mit Preis und Fehlbetrag. */
    ABSAGE_ZU_WENIG("werder.absage.zu-wenig", Art.SATZ, "preis", "fehlt"),

    /**
     * Die Ablage antwortet nicht.
     *
     * <p>Ein eigener Satz und kein stilles Nein: Wer 75.000 Mark ausgeben will und
     * irgendeine Absage liest, versucht es wieder. Hier soll er lesen, dass es NICHT an
     * ihm liegt - und der Betreiber findet die Ursache im Protokoll.
     */
    ABSAGE_GESTOERT("werder.absage.gestoert", Art.SATZ),

    /**
     * Es gibt gar keine Inselwelt.
     *
     * <p>Dieselbe Lage wie bei {@code freistadt.absage.keine-welt}: Der Befehl wird auch
     * dann angemeldet, wenn {@code inseln.welt} auf eine Welt zeigt, die es nicht gibt
     * oder die kein Inselraster traegt. Ein Befehl, den es je nach Einstellungsdatei gibt
     * oder nicht, waere fuer einen Spieler ein Fehler ohne Erklaerung.
     */
    ABSAGE_KEINE_WELT("werder.absage.keine-welt", Art.SATZ),

    /** Der Spieler steht nicht in Hafenwerder - mit dem Weg dorthin. */
    ABSAGE_FALSCHE_WELT("werder.absage.falsche-welt", Art.SATZ, "welt");

    private final String pfad;
    private final Art art;
    private final Set<String> platzhalter;

    WerderSatz(String pfad, Art art, String... platzhalter) {
        this.pfad = pfad;
        this.art = art;
        this.platzhalter = Set.of(platzhalter);
    }

    @Override
    public String pfad() {
        return pfad;
    }

    @Override
    public Art art() {
        return art;
    }

    @Override
    public Set<String> platzhalter() {
        return platzhalter;
    }

    /**
     * Keiner dieser Saetze darf leer sein.
     *
     * <p>Dieselbe Hausregel wie in {@link FreistadtSatz}: Jeder ist die EINZIGE Antwort
     * auf einen getippten Befehl. Wer die Inseln abschalten will, setzt
     * {@code inseln.hoechstzahl: 0} oder nimmt das Recht weg - ein leerer Satz liesse den
     * Befehl stumm arbeiten, und ein Spieler, der nichts liest, tippt ihn wieder.
     */
    @Override
    public boolean darfLeerSein() {
        return false;
    }
}
