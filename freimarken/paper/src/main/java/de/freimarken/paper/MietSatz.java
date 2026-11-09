package de.freimarken.paper;

import java.util.Set;

/**
 * Die Saetze der Ladenmiete - mieten, verlaengern, kuendigen.
 *
 * <p>Eine eigene Datei neben {@link LadenSatz}, obwohl beide zum Befehl {@code /laden}
 * gehoeren: So teilen zwei gleichzeitig arbeitende Haende nie dieselbe Datei. Die Datei
 * dieses Schrittes ist {@link Miettext}.
 *
 * <p><b>Die Pfade stehen unter {@code laden.} und nicht unter {@code miete.}.</b> Der
 * Betreiber sucht unter dem Befehl, den er TIPPT, und er tippt {@code /laden} - dieselbe
 * Begruendung wie bei {@code spielzeit.tag-muster}. Dass Pfad und deklarierende Datei
 * unabhaengig sind, zeigt {@link MarkSatz}: Es fuehrt einen {@code zeit.}- und einen
 * {@code waehrung.}-Pfad.
 *
 * <p><b>Was hier steht, ist der Vertrag und kein Wort.</b> Pfad, Art und die Namen der
 * Werte - der Wortlaut steht NUR in {@code sprache-vorlage.yml}.
 *
 * <p><b>Die Eigenheit dieses Bereichs: ZWEI Saetze fuer dasselbe Mieten.</b> Beim ersten
 * Laden eines Spielers steckt die einmalige Lizenz im Betrag, danach nicht - und das ist
 * ein Unterschied von 50.000 Mark. Ein Satz fuer beides muesste die Lizenz entweder
 * verschweigen oder sie auch dann nennen, wenn sie nicht geflossen ist. Dieselbe Bauart
 * wie bei den drei Saetzen des Tagesbonus.
 */
enum MietSatz implements Satz {

    /** Was gemietet wurde und bis wann - die erste Zeile jedes Abschlusses. */
    GEMIETET_KOPF("laden.gemietet.kopf", Art.SATZ, "laden", "bis", "miettage"),

    /**
     * Was abgebucht wurde, wenn die Lizenz schon bezahlt war.
     *
     * <p>KEIN {@code lizenz}: Sie ist nicht geflossen, und eine 0 in der Zeile liesse den
     * Spieler glauben, er haette sie geschenkt bekommen.
     */
    GEMIETET_KOSTEN("laden.gemietet.kosten", Art.SATZ, "betrag"),

    /**
     * Was abgebucht wurde, wenn die Lizenz DARIN steckt - der erste Laden ueberhaupt.
     *
     * <p>Beide Zahlen, und das ist der Zweck dieses zweiten Satzes: Wer 55.000 abgebucht
     * sieht und 5.000 Kaution erwartet hat, soll in derselben Zeile lesen, warum.
     */
    GEMIETET_ERSTMALS("laden.gemietet.erstmals", Art.SATZ, "betrag", "lizenz"),

    /** Was taeglich faellig wird, und wie man verlaengert. */
    GEMIETET_TAEGLICH("laden.gemietet.taeglich", Art.SATZ, "miete"),

    /** Verlaengert - bis wann und wie viele Tage das sind. */
    VERLAENGERT("laden.verlaengert", Art.SATZ, "laden", "bis", "resttage"),

    /** Gekuendigt. */
    GEKUENDIGT("laden.gekuendigt.kopf", Art.SATZ, "laden"),

    /**
     * Die Kaution kommt zurueck - beim Kuendigen und beim Ablaufen.
     *
     * <p>Eine eigene Zeile und nicht ein Teil von {@link #GEKUENDIGT}: Es kann sein, dass
     * keine Kaution hinterlegt war (der Betreiber hatte sie auf 0 gestellt), und dann
     * waere "0 Mark zurueck" eine Zeile, die nach einem Fehler aussieht.
     */
    GEKUENDIGT_KAUTION("laden.gekuendigt.kaution", Art.SATZ, "betrag"),

    /** Dieser Stand steht nicht in der {@code config.yml}. */
    ABSAGE_KEINE_EINHEIT("laden.absage.keine-einheit", Art.SATZ, "laden"),

    /** Der Stand ist an jemand anderen vermietet. */
    ABSAGE_SCHON_VERMIETET("laden.absage.schon-vermietet", Art.SATZ, "laden"),

    /**
     * Der Spieler hat schon einen Laden - Plan 7.1: einer je Spieler.
     *
     * <p>{@code laden} ist SEIN Laden und nicht der gewuenschte: Sonst sucht er, welchen
     * er eigentlich hat.
     */
    ABSAGE_SCHON_EIN_LADEN("laden.absage.schon-ein-laden", Art.SATZ, "laden"),

    /**
     * Das Guthaben reicht nicht.
     *
     * <p>Drei Zahlen: was es kostet, was da ist, was fehlt. Die dritte rechnet das
     * Programm - ein Spieler soll nicht subtrahieren muessen, um zu wissen, wie viel er
     * noch verdienen muss.
     */
    ABSAGE_ZU_WENIG_GELD("laden.absage.zu-wenig-geld", Art.SATZ, "kosten", "guthaben",
            "fehlt"),

    /** Noch zu frueh zum Verlaengern - es ist mehr als eine Mietdauer uebrig. */
    ABSAGE_NOCH_ZU_FRUEH("laden.absage.noch-zu-frueh", Art.SATZ, "laden", "resttage",
            "dauer"),

    /** Kein Laden da - nichts zu verlaengern und nichts zu kuendigen. */
    ABSAGE_KEIN_LADEN("laden.absage.kein-laden", Art.SATZ),

    /**
     * Die Markthalle ist ein ORT - wer nicht dort ist, kann nicht mieten.
     *
     * <p>Nur das Mieten haengt daran; verlaengern und kuendigen gehen von ueberall. Die
     * Begruendung steht an {@link LadenBefehle}.
     */
    ABSAGE_FALSCHE_WELT("laden.absage.falsche-welt", Art.SATZ),

    /** Die Datenbank hat geschwiegen. Was geschah, steht im Protokoll. */
    ABSAGE_GESTOERT("laden.absage.gestoert", Art.SATZ),

    /**
     * Die Miete konnte nicht abgebucht werden - die Warnung [Teil C].
     *
     * <p>Vier Werte, und {@code ausfall} und {@code schranke} stehen BEIDE darin: "Das
     * war Ausfall 2 von 3" sagt dem Spieler, wie viel Luft er noch hat. Eine Zeile mit
     * nur einer der zwei Zahlen sagt es nicht - "zweiter Ausfall" allein kann der dritte
     * von zwanzig sein.
     *
     * <p>Als ORDNUNGSZAHL formuliert und nicht als "noch 2 Tage", und das ist kein
     * Geschmack: Eine Mehrzahlform laesst sich in MiniMessage nicht beugen, und "noch 1
     * Tage" ist genau der Fehler, der in Teil A schon einmal entstanden ist. "Ausfall 1
     * von 3" steht in jeder Zahl richtig da.
     */
    MIETE_GEWARNT("laden.miete.gewarnt", Art.SATZ, "laden", "betrag", "ausfall",
            "schranke"),

    /**
     * Der Laden ist weg, die Kaution verfallen.
     *
     * <p>Die Kaution steht in der Zeile, und das ist der Zweck der Kaution: Sie soll das
     * Weglaufen teurer machen als das Kuendigen, und das wirkt nur, wenn der Spieler die
     * Zahl SIEHT. Eine Raeumung ohne genannten Verlust ist eine Lehre, die niemand zieht.
     */
    MIETE_GERAEUMT("laden.miete.geraeumt", Art.SATZ, "laden", "ausfall", "schranke",
            "kaution"),

    /**
     * Die Mietdauer ist um, ohne dass verlaengert wurde.
     *
     * <p>Ein eigener Satz neben {@link #MIETE_GERAEUMT}, obwohl beide den Laden
     * wegnehmen: Der Unterschied ist die Kaution, und er ist fuer den Spieler der ganze
     * Unterschied. Wer abgelaufen ist, hat nichts falsch gemacht - er hat jeden Tag
     * gezahlt. Dieselbe Zeile fuer beides machte aus einem ordentlichen Ende einen
     * Vorwurf.
     */
    MIETE_ABGELAUFEN("laden.miete.abgelaufen", Art.SATZ, "laden", "bis"),

    /**
     * Was aus der Ladentruhe geworden ist - die Zeile unter jeder Raeumung.
     *
     * <p>"Stapel" bleibt im Plural gleich, also braucht diese Zahl keine Beugung.
     */
    MIETE_INS_FACH("laden.miete.ins-fach", Art.SATZ, "stapel"),

    /**
     * Beim Beitritt: Dein Laden ist in Verzug.
     *
     * <p>Die einzige Stelle, an der ein Spieler von einem Ausfall erfaehrt, den er
     * verschlafen hat - der taegliche Lauf laeuft, waehrend er offline ist. Ohne diese
     * Zeile kaeme er zurueck und faende seinen Laden weg, ohne je gewarnt worden zu sein.
     */
    MIETE_VERZUG("laden.miete.verzug", Art.SATZ, "laden", "betrag", "ausfall",
            "schranke"),

    /** Was im Abholfach wartet - beim Beitritt und in der Liste. */
    ABHOLFACH_STAND("laden.abholfach.stand", Art.SATZ, "stapel"),

    /** Nichts da - die haeufigste Antwort auf {@code /laden abholfach}. */
    ABHOLFACH_LEER("laden.abholfach.leer", Art.SATZ),

    /** Geholt. */
    ABHOLFACH_GEHOLT("laden.abholfach.geholt", Art.SATZ, "stapel"),

    /**
     * Es ist noch was uebrig - die zweite Zeile, wenn das Inventar voll wurde.
     *
     * <p>Eine eigene Zeile und nicht ein Teil von {@link #ABHOLFACH_GEHOLT}: Der
     * haeufigste Fall ist, dass alles auf einmal passt, und dann waere "es warten noch 0
     * Stapel" eine Zeile, die nach einem Fehler aussieht.
     */
    ABHOLFACH_BLEIBT("laden.abholfach.bleibt", Art.SATZ, "stapel"),

    /** Gar kein Platz - es ist nichts herausgegangen. */
    ABHOLFACH_KEIN_PLATZ("laden.abholfach.kein-platz", Art.SATZ, "stapel"),

    /**
     * Ein Stapel liess sich nicht zurueckverwandeln.
     *
     * <p>Der Spieler soll es ERFAHREN und nicht raten, warum sein Fach nicht leer wird.
     * Die Stapel liegen noch da; was genau fehlt, steht mit Beschriftung im Protokoll.
     */
    ABHOLFACH_UNLESBAR("laden.abholfach.unlesbar", Art.SATZ, "stapel"),

    /** Der eigene Laden in der Liste - die letzte Zeile von {@code /laden}. */
    STAND("laden.stand.eigener", Art.SATZ, "laden", "bis", "resttage", "miete"),

    /**
     * Noch kein Laden - die letzte Zeile von {@code /laden} fuer alle anderen.
     *
     * <p>Eine eigene Zeile und nicht nichts: Wer die Liste sieht, soll erfahren, wie man
     * mietet. Ohne sie waere {@code /laden mieten} nur in der Hilfe zu finden.
     */
    STAND_KEINER("laden.stand.keiner", Art.SATZ),

    /**
     * Der Tag in den Mietzeilen - beim LADEN uebersetzt.
     *
     * <p>Beim Laden und nicht beim ersten Befehl: Ein unbrauchbares Muster ist dann eine
     * Klage mit Zeilennummer und kein Wurf mitten in einem Abschluss, der gerade Geld
     * gekostet hat. {@code Locale.GERMANY} bleibt im Programm - sonst wechselte ein
     * Monatsname die Sprache.
     *
     * <p>Ein EIGENES Muster und nicht das von {@code spielzeit.tag-muster}: Jenes ist der
     * Tag EINER Zeile ueber die heutige Spielzeit. Ein Vertragsende darf anders
     * geschrieben sein, und eine Aenderung am einen soll das andere nicht mitdrehen.
     */
    TAG_MUSTER("laden.tag-muster", Art.MUSTER);

    private final String pfad;
    private final Art art;
    private final Set<String> platzhalter;

    MietSatz(String pfad, Art art, String... platzhalter) {
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
     * <p>Jeder ist eine ganze Zeile an einen Spieler, und jede antwortet auf eine Tat, die
     * Geld bewegt oder einen Laden bindet. Eine leere Absage waere ein Befehl, der nichts
     * sagt und nichts tut; eine leere Zusage waere ein Abschluss ohne Beleg.
     */
    @Override
    public boolean darfLeerSein() {
        return false;
    }
}
