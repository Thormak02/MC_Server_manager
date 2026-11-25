package de.freimarken.paper;

import java.util.Set;

/**
 * Die Saetze von {@code /einsehen} (Plan 12c.3, Schritt 16a Teil D).
 *
 * <p><b>Der wichtigste Satz ist {@link #NUR_SEHEN}</b> - die Absage beim Klick in eine
 * fremde Kiste. Ohne sie klickt ein Moderator und es geschieht nichts: Er haelt die
 * Einsicht fuer kaputt und schaltet beim naechsten Mal lieber {@code /op} ein. Eine
 * stumme Sperre ist in diesem Werkzeug schlimmer als keine.
 *
 * <p><b>Der Stand hat ZWEI Saetze und nicht einen mit Platzhalter</b>
 * ({@link #STAND_AN} und {@link #STAND_AUS}): Wer liest "Einsicht: 0 Minuten", weiss
 * nicht, ob sie gerade ablief oder nie an war - und das ist der Unterschied zwischen
 * "noch einmal einschalten" und "ich habe mich geirrt".
 */
enum EinsichtSatz implements Satz {

    /**
     * Eingeschaltet - mit dem Zeitpunkt, bis zu dem sie gilt.
     *
     * <p>Der ZEITPUNKT und nicht nur die Dauer: Ein Moderator, der eine Meldung
     * bearbeitet, will wissen, wann er nachlegen muss - und ein "noch 5 Minuten" muss er
     * dafuer jedes Mal neu erfragen.
     *
     * <p>Derselbe Satz beim VERLAENGERN: Ein erneutes {@code /einsehen an} setzt die
     * Frist neu, und die Antwort nennt den neuen Zeitpunkt. Ein eigener Satz dafuer waere
     * eine Unterscheidung ohne Folge - in beiden Faellen gilt die Einsicht ab jetzt
     * genauso lange.
     */
    AN("einsehen.an", Art.SATZ, "bis", "dauer"),

    /** Ausgeschaltet. */
    AUS("einsehen.aus", Art.SATZ),

    /**
     * Sie war gar nicht an.
     *
     * <p>Ein eigener Satz und nicht {@link #AUS}: Wer "ist jetzt aus" liest auf etwas,
     * das nie an war, glaubt, er habe eben noch eingesehen - und das ist genau die
     * Annahme, mit der man eine fremde Kiste fuer die eigene haelt.
     */
    WAR_AUS("einsehen.war-aus", Art.SATZ),

    /** Der Stand: sie laeuft noch so lange. */
    STAND_AN("einsehen.stand-an", Art.SATZ, "bis", "dauer"),

    /** Der Stand: sie ist aus. */
    STAND_AUS("einsehen.stand-aus", Art.SATZ),

    /**
     * Die Absage beim Klick - DER SATZ, OHNE DEN DAS WERKZEUG UNBRAUCHBAR IST.
     *
     * <p>Plan 12c.3: <i>"hinsehen duerfen, anfassen nicht."</i> Das "nicht" muss man
     * LESEN koennen: Ein Klick, der nichts tut, sieht aus wie ein Fehler des Servers.
     *
     * <p>Er wird GEDROSSELT gesendet (eine Zeile je zwei Sekunden), wie die Absagen der
     * Schutzwache - sonst wird aus einem gehaltenen Klick ein Chatregen, und beim
     * zwanzigsten Mal liest ihn niemand mehr.
     */
    NUR_SEHEN("einsehen.nur-sehen", Art.SATZ),

    /**
     * Die Konsole kann nicht einsehen.
     *
     * <p>Nicht aus Prinzip: Einsehen ist ein Zustand EINES Spielers, der in eine Kiste
     * klickt. Die Konsole klickt nicht, und sie braucht die Einsicht auch nicht - was in
     * einer Kiste steht, liest der Betreiber ueber den Manager.
     */
    NUR_SPIELER("einsehen.nur-spieler", Art.SATZ),

    /**
     * Wie ein Zeitpunkt geschrieben wird.
     *
     * <p>{@link Art#MUSTER}, wie {@code strafe.zeit-muster}: Es wird beim Laden in einen
     * {@code DateTimeFormatter} uebersetzt. Hier ohne Datum - die Einsicht gilt Minuten,
     * und ein Datum davor waere Platz ohne Auskunft.
     */
    ZEIT_MUSTER("einsehen.zeit-muster", Art.MUSTER);

    private final String pfad;
    private final Art art;
    private final Set<String> platzhalter;

    EinsichtSatz(String pfad, Art art, String... platzhalter) {
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
     * KEIN Satz dieses Bereichs darf leer sein.
     *
     * <p>Besonders {@link #NUR_SEHEN} nicht: Ein leerer Wortlaut waere eine stumme
     * Sperre, und die ist in diesem Werkzeug schlimmer als keine. Es gibt hier auch
     * keine Ansage, die ein Betreiber abschalten wollen koennte - jeder Satz ist eine
     * Antwort auf eine Tat des Moderators selbst.
     */
    @Override
    public boolean darfLeerSein() {
        return false;
    }
}
