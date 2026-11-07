package de.freimarken.paper;

import java.util.Set;

/**
 * Die Saetze des Tagesbonus.
 *
 * <p>Zehn Dateien statt einer, eine je Bereich: So teilen zwei gleichzeitig arbeitende
 * Haende nie dieselbe Datei. Die Datei dieses Schrittes ist {@link Bonustext}.
 *
 * <p><b>Was hier steht, ist der Vertrag und kein Wort.</b> Pfad, Art und die Namen der
 * Werte - der Wortlaut steht NUR in {@code sprache-vorlage.yml}. Deshalb ist die
 * Platzhalterprobe nicht selbstbezueglich: Wer beim Abschreiben einem Satz seinen
 * {@code {betrag}} wegnimmt, bekommt eine rote Probe und keinen stummen Fehler.
 *
 * <p><b>Die Eigenheit dieses Bereichs sind DREI Nachrichten fuer eine Zahlung.</b> Sie
 * unterscheiden sich nicht im Betrag, sondern in dem, was sie dem Spieler ueber seine
 * Serie sagen - und das ist der Teil, der Regelmaessigkeit belohnt:
 * <ul>
 *   <li>{@link #GEZAHLT_ERSTER} - der allererste Bonus oder ein Neuanfang nach einer
 *       gerissenen Serie.
 *   <li>{@link #GEZAHLT_SERIE} - ein fortgesetzter Tag, mit der Zahl der Tage.
 *   <li>{@link #GEZAHLT_BESTMARKE} - derselbe Tag, aber es ist seine LAENGSTE Serie. Ein
 *       eigener Satz und nicht ein Zusatz: Wer seinen Rekord einstellt, soll das lesen
 *       und nicht aus zwei gleichen Zahlen schliessen.
 * </ul>
 * Welcher zutrifft, entscheidet das Programm - der Wortlaut gehoert dem Betreiber.
 */
enum BonusSatz implements Satz {

    /**
     * Der erste Bonus einer Serie - auch nach einer gerissenen.
     *
     * <p>Ohne {@code serie}, denn sie ist 1: Eine Zeile "Tag 1 deiner Serie" liest sich
     * wie eine Zaehlung, die bei 1 anfaengt und nichts bedeutet. Was hier zaehlt, ist
     * der Betrag und die Aussicht - darum steht {@code morgen} darin, der Betrag des
     * zweiten Tages.
     */
    GEZAHLT_ERSTER("tagesbonus.gezahlt.erster", Art.SATZ, "betrag", "morgen"),

    /**
     * Ein fortgesetzter Serientag.
     *
     * <p>{@code serie} ist die Zahl der Tage, {@code betrag} der heutige Betrag. Kein
     * {@link Art#GEZAEHLT}: "Tag 1" kommt hier nie vor - den traegt
     * {@link #GEZAHLT_ERSTER} -, also gibt es keine Einzahlform zu waehlen.
     */
    GEZAHLT_SERIE("tagesbonus.gezahlt.serie", Art.SATZ, "betrag", "serie"),

    /**
     * Ein fortgesetzter Serientag, der die eigene Bestmarke einstellt.
     *
     * <p>Dieselben Werte wie {@link #GEZAHLT_SERIE}; der Unterschied ist, was dastehen
     * soll. Eine Bestmarke ist der Augenblick, in dem die Serie etwas bedeutet.
     */
    GEZAHLT_BESTMARKE("tagesbonus.gezahlt.bestmarke", Art.SATZ, "betrag", "serie"),

    /**
     * Die Zeile in {@code /mark}, solange eine Serie laeuft.
     *
     * <p>{@code serie} und {@code laengste}, und beide braucht es: Die laufende sagt, was
     * morgen auf dem Spiel steht, die laengste sagt, was zu schlagen waere.
     */
    STAND_SERIE("tagesbonus.stand.serie", Art.SATZ, "serie", "laengste", "morgen"),

    /**
     * Die Zeile in {@code /mark}, solange der Bonus von heute noch offen ist.
     *
     * <p>{@code serie} ist der Serientag, den HEUTE bringen wuerde, {@code betrag} sein
     * Betrag. Beides wird hereingegeben und hier nicht gerechnet: Ob heute der naechste
     * Tag einer Serie oder ein Neuanfang ist, entscheidet {@code Serienregel}, und die
     * braucht Uhr und Zone.
     *
     * <p>Erreichbar auf zwei Wegen: Der Bonus steht unmittelbar an (ohne eingestellte
     * Mindestzeit faellt er beim Einloggen, also sieht ein Spieler das kaum), oder die
     * Kasse hat ihn nicht annehmen koennen und der naechste Taktschlag versucht es
     * erneut. Im zweiten Fall ist diese Zeile die einzige Auskunft, die er bekommt.
     */
    STAND_OFFEN("tagesbonus.stand.offen", Art.SATZ, "betrag", "serie"),

    /**
     * Die Zeile in {@code /mark}, solange der Bonus heute noch auf Taetigkeit wartet.
     *
     * <p>Nur erreichbar, wenn {@code tagesbonus.mindest-minuten} gesetzt ist. Ohne diesen
     * Satz tippt ein Spieler {@code /mark}, liest nichts von einem Bonus und haelt ihn
     * fuer abgeschafft.
     */
    STAND_WARTET("tagesbonus.stand.wartet", Art.SATZ, "minuten", "betrag", "serie");

    private final String pfad;
    private final Art art;
    private final Set<String> platzhalter;

    BonusSatz(String pfad, Art art, String... platzhalter) {
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
     * Kein Satz dieses Bereichs darf leer sein.
     *
     * <p>Jeder von ihnen ist eine ganze Zeile an einen Spieler, und eine leere Zeile
     * waere ein Bonus, der stumm faellt: Er bekommt Geld und liest nicht, warum. Wer den
     * Bonus nicht ankuendigen will, hat dafuer keinen Schalter - das ist dieselbe
     * Entscheidung wie beim fehlenden Ausschalter der Messstelle.
     */
    @Override
    public boolean darfLeerSein() {
        return false;
    }
}
