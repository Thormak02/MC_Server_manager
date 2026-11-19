package de.freimarken.paper;

import java.util.Set;

/**
 * Die Saetze der Rangleiter.
 *
 * <p>Eine Datei je Bereich, wie bei {@link BonusSatz}: So teilen zwei gleichzeitig
 * arbeitende Haende nie dieselbe Datei. Die Datei dieses Schrittes ist {@link Rangtext}.
 *
 * <p><b>Was hier steht, ist der Vertrag und kein Wort.</b> Pfad, Art und die Namen der
 * Werte - der Wortlaut steht NUR in {@code sprache-vorlage.yml}.
 *
 * <p><b>Die Eigenheit dieses Bereichs: der Rangname ist ein PLATZHALTER und kein Satz.</b>
 * Welche Stufen es gibt, entscheidet der Betreiber in der {@code config.yml}; eine
 * Aufzaehlung hier koennte sie darum nicht kennen. Was in {@code {rang}} landet, ist der
 * Schluessel aus der Datei - {@code siedler}, {@code buerger} oder wie der Betreiber ihn
 * genannt hat. Das ist dieselbe Naht wie bei den Berufen, deren Namen auch aus der Datei
 * kommen und nicht aus einer Aufzaehlung.
 *
 * <p><b>Warum zwei der drei Saetze LEER sein duerfen</b> [19.11.2026]: Der Aufstieg selbst
 * MUSS sichtbar sein - wer neue Rechte bekommt und nicht liest, warum, haelt sie fuer
 * einen Zufall. Ob danach noch steht, was als naechstes kommt, ist Geschmack: Der eine
 * Betreiber will den Ausblick als Anreiz, der andere eine einzige Zeile. Dafuer braucht es
 * keinen Schalter in der {@code config.yml} - die leere Zeile IST der Schalter, und zwar
 * an der Stelle, an der auch die Worte stehen.
 */
enum RangSatz implements Satz {

    /**
     * Der Aufstieg selbst - die eine Zeile, die nicht fehlen darf.
     *
     * <p>{@code rang} ist der Schluessel der NEUEN Stufe. Kein {@code vorher}: Was er
     * vorher war, weiss der Spieler, und eine Zeile "vom Reisenden zum Siedler" liest
     * sich wie ein Formular. Wer sie trotzdem will, nimmt {@code {rang}} und schreibt
     * den Rest dazu.
     *
     * <p>NICHT leer erlaubt: Ein Aufstieg, der stumm faellt, ist der Fall, in dem ein
     * Spieler ploetzlich mehr darf und es nie erfaehrt - dieselbe Begruendung, aus der
     * der Tagesbonus seine Zeile nicht abschalten laesst.
     */
    AUFGESTIEGEN("raenge.aufgestiegen", Art.SATZ, "rang"),

    /**
     * Was als naechstes kommt - gesendet unmittelbar nach dem Aufstieg.
     *
     * <p>{@code rang} ist der Schluessel der Stufe DARUEBER. Dieselbe Aufgabe wie
     * {@code {morgen}} beim Tagesbonus: der Grund, weiterzuspielen.
     *
     * <p>{@link Satz#LEER_ERLAUBT} - siehe den Klassenkopf.
     */
    NAECHSTER("raenge.naechster", Art.SATZ, Satz.LEER_ERLAUBT, "rang"),

    /**
     * Es gibt keine Stufe mehr darueber.
     *
     * <p>Ein eigener Satz und nicht das Weglassen von {@link #NAECHSTER}: Wer die oberste
     * Stufe erreicht, soll das lesen und nicht aus einer fehlenden Zeile schliessen. Ohne
     * Platzhalter - der erreichte Rang steht schon in der Zeile darueber.
     *
     * <p>{@link Satz#LEER_ERLAUBT} - siehe den Klassenkopf.
     */
    HOECHSTE("raenge.hoechste", Art.SATZ, Satz.LEER_ERLAUBT);

    private final String pfad;
    private final Art art;
    private final Set<String> platzhalter;
    private final boolean leerErlaubt;

    RangSatz(String pfad, Art art, String... platzhalter) {
        this(pfad, art, false, platzhalter);
    }

    RangSatz(String pfad, Art art, boolean leerErlaubt, String... platzhalter) {
        this.pfad = pfad;
        this.art = art;
        this.platzhalter = Set.of(platzhalter);
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
        return platzhalter;
    }

    @Override
    public boolean darfLeerSein() {
        return leerErlaubt;
    }
}
