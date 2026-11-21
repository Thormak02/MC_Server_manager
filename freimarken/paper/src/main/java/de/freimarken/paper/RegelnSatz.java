package de.freimarken.paper;

import java.util.Set;

/**
 * Die Saetze der Regeln.
 *
 * <p><b>Der TEXT ist EIN Satz mit Umbruchmarken</b> und keine Liste von Zeilen, und das
 * ist eine Entscheidung mit Grund: Eine Liste in der Sprachdatei haette eine
 * veraenderliche Zahl von Schluesseln, und jeder Schluessel dieser Datei muss zu einem
 * Satz des Programms gehoeren ({@code VorlageTest} 2). Mit Umbruchmarken aendert der
 * Betreiber einen Satz und hat so viele Zeilen, wie er will - der Sendeort teilt sie, und
 * im Fenster wird jede Zeile ein eigener Koerperblock.
 *
 * <p>Darum traegt {@link #TEXT} auch KEINEN Eintrag in {@link Einzeiler}: Er ist der eine
 * Satz dieses Bereichs, der mehrzeilig sein MUSS. {@link #KOPF} und {@link #KNOPF}
 * stehen dort dagegen sehr wohl - sie gehen als EIN Component in den Fenstertitel und
 * auf den Knopf, und beides nimmt keine Liste.
 */
enum RegelnSatz implements Satz {

    /** Die Ueberschrift - im Chat die erste Zeile, im Fenster der Titel. */
    KOPF("regeln.kopf", Art.SATZ),

    /**
     * Die Regeln selbst. MEHRZEILIG: Umbruchmarken trennen die Zeilen.
     *
     * <p>Was ab Werk darin steht, ist ein Vorschlag und kein Gesetz - die Regeln eines
     * Servers macht sein Betreiber.
     */
    TEXT("regeln.text", Art.SATZ),

    /**
     * Die Zeile unter dem Text - nur im Chat.
     *
     * <p>{@link Satz#LEER_ERLAUBT}: Wer sie nicht will, laesst sie weg. Im Fenster steht
     * sie nicht: Dort ist der Knopf das Ende des Textes.
     */
    FUSS("regeln.fuss", Art.SATZ, Satz.LEER_ERLAUBT),

    /** Der Knopf, der das Fenster schliesst. */
    KNOPF("regeln.knopf", Art.SATZ);

    private final String pfad;
    private final Art art;
    private final boolean leerErlaubt;

    RegelnSatz(String pfad, Art art) {
        this(pfad, art, false);
    }

    RegelnSatz(String pfad, Art art, boolean leerErlaubt) {
        this.pfad = pfad;
        this.art = art;
        this.leerErlaubt = leerErlaubt;
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
        return Set.of();
    }

    @Override
    public boolean darfLeerSein() {
        return leerErlaubt;
    }
}
