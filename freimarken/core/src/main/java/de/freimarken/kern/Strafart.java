package de.freimarken.kern;

import java.util.Locale;
import java.util.Optional;

/**
 * Was einem Spieler geschieht (Plan 12c.3, Schritt 16a Teil B).
 *
 * <p>Die drei Arten des Plans, und sie unterscheiden sich in genau zwei Fragen: Wirkt die
 * Strafe FORT, nachdem sie verhaengt wurde, und WAS sperrt sie? Ein Rauswurf wirkt nicht
 * fort - er ist in dem Augenblick vorbei, in dem er geschieht. Darum beantwortet
 * {@link #giltFort()} fuer ihn {@code false}, und nur darum laesst sich die Frage
 * <i>"was gilt gegen diesen Spieler"</i> ueberhaupt stellen.
 *
 * <p><b>Ein Rauswurf steht trotzdem in derselben Tabelle.</b> Die Versuchung ist, ihn
 * weglassen zu wollen, weil er nichts sperrt - und dann fehlt genau die Zeile, die zeigt,
 * dass derselbe Spieler vor dem Bann dreimal rausgeworfen wurde. Plan 12c.3 verlangt
 * <i>"alles protokolliert, mit Grund und Bearbeiter"</i>; ein Rauswurf ist eine Tat eines
 * Moderators an einem Spieler, und damit gehoert er hinein.
 *
 * <p><b>Was NICHT hier steht: eine Verwarnung.</b> Der Plan nennt drei Werkzeuge, und eine
 * Verwarnung ist keines davon - sie sperrt nichts und ist damit nur eine Chatzeile mit
 * Protokoll. Das ist eine benannte Luecke und kein Versehen: Wer sie spaeter braucht,
 * nimmt eine vierte Konstante dazu, und {@link #giltFort()} sagt {@code false} wie beim
 * Rauswurf. Zwei Stellen aendern sich mit - {@link #ausWort} und das Recht je Art.
 *
 * <p>Der NAME wird gespeichert, nicht {@code ordinal()}: Eine vierte Art in der Mitte
 * dieser Aufzaehlung verschoebe sonst jede gespeicherte Zahl.
 */
public enum Strafart {

    /**
     * Stumm - der Spieler bleibt im Spiel, schreibt aber nicht.
     *
     * <p>Wirkt fort, hat also eine Dauer. <b>Sperrt den CHAT und nicht den Beitritt</b>,
     * und das ist der Sinn: Zwei Leute im Streit trennt man, ohne sie vom Server zu
     * nehmen. Wer nicht schreiben kann, kann weiterbauen - und hat nach zehn Minuten
     * meist nichts mehr zu schreiben.
     *
     * <p>Dass eine Stummschaltung ueber Fluesterbefehle umgangen werden kann, ist keine
     * Nebensache, sondern die Luecke, die sie wertlos macht. Welche Befehle sie
     * mitsperrt, steht darum in der Datei
     * ({@link Strafwerte#PFAD_STUMM_SPERRT_BEFEHLE}) - als Liste von Namen, weil jedes
     * Nachbarplugin eigene mitbringt.
     */
    STUMM("stumm", true, true, false),

    /**
     * Rauswurf - der Spieler fliegt sofort, kann aber wiederkommen.
     *
     * <p><b>Die einzige Art ohne Dauer</b>, und das ist keine Unregelmaessigkeit, sondern
     * ihr Wesen: Ein Rauswurf dauert so lange, wie der Spieler zum Wiederverbinden
     * braucht. Plan 12c.3 sagt <i>"jeweils mit Dauer und Grund"</i> - der GRUND gilt fuer
     * alle drei, die Dauer fuer die zwei, die fortwirken. Wer dem Rauswurf eine Dauer
     * gaebe, haette einen Bann mit anderem Namen.
     */
    RAUSWURF("rauswurf", false, false, false),

    /**
     * Bann - der Spieler kommt nicht auf den Server.
     *
     * <p>Wirkt fort und sperrt den Beitritt. <b>Die einzige Art, bei der die Dauer eine
     * Rechtefrage ist:</b> Plan 12c.2 gibt dem Moderator das <i>zeitliche</i> Bannen und
     * dem Verwalter das <i>dauerhafte</i>. Der Riegel dafuer steht in
     * {@link Strafbuch#verhaenge}, und er greift fuer beide fortwirkenden Arten - eine
     * unbegrenzte Stummschaltung ist genauso endgueltig wie ein Bann.
     */
    BANN("bann", true, false, true);

    private final String kuerzel;
    private final boolean giltFort;
    private final boolean sperrtChat;
    private final boolean sperrtBeitritt;

    Strafart(String kuerzel, boolean giltFort, boolean sperrtChat,
             boolean sperrtBeitritt) {
        this.kuerzel = kuerzel;
        this.giltFort = giltFort;
        this.sperrtChat = sperrtChat;
        this.sperrtBeitritt = sperrtBeitritt;
    }

    /**
     * Das Wort, mit dem diese Art im Befehl und in der Datei steht.
     *
     * <p>Kleingeschrieben und ohne Umlaut - es wird getippt, es steht in der
     * {@code config.yml}, und es ist der Zweigname in {@code /strafe}. <b>Nicht der
     * Rechtename:</b> Der steht als eigenes Literal im Plugin, weil eine Quelltexttafel
     * ein zusammengesetztes Recht nicht sieht.
     */
    public String kuerzel() {
        return kuerzel;
    }

    /**
     * Ob diese Strafe nach dem Verhaengen weiterwirkt.
     *
     * <p>Die Frage, an der alles haengt: Nur fortwirkende Arten haben eine Dauer, nur sie
     * lassen sich aufheben, und nur nach ihnen wird gefragt, wenn ein Spieler sich
     * anmeldet oder schreibt.
     */
    public boolean giltFort() {
        return giltFort;
    }

    /** Ob diese Strafe das Schreiben sperrt. */
    public boolean sperrtChat() {
        return sperrtChat;
    }

    /** Ob diese Strafe den Beitritt sperrt. */
    public boolean sperrtBeitritt() {
        return sperrtBeitritt;
    }

    /**
     * Ob diese Art eine Dauer braucht - dasselbe wie {@link #giltFort()}.
     *
     * <p>Ein eigener Name fuer dieselbe Frage, weil sie an den Aufrufstellen
     * verschiedenes bedeutet: Der Befehl fragt, ob er ein Dauerargument anbieten soll;
     * die Wache fragt, ob sie nachsehen muss. Waeren es zwei Felder, liesse sich eines
     * umstellen, ohne dass das andere es merkt - und dann hat eine Art eine Dauer, nach
     * der niemand fragt.
     */
    public boolean brauchtDauer() {
        return giltFort;
    }

    /**
     * Die Art mit diesem Wort.
     *
     * @return leer, wenn das Wort keine ist - ein Vertipper im Befehl ist eine Antwort
     *         und kein Absturz
     */
    public static Optional<Strafart> ausWort(String wort) {
        if (wort == null) {
            return Optional.empty();
        }
        String rein = wort.trim().toLowerCase(Locale.ROOT);
        for (Strafart art : values()) {
            if (art.kuerzel.equals(rein)) {
                return Optional.of(art);
            }
        }
        return Optional.empty();
    }
}
