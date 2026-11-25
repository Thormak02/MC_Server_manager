package de.freimarken.paper;

import de.freimarken.kern.Meldestand;
import de.freimarken.kern.Meldung;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;
import net.kyori.adventure.text.Component;

/**
 * Aus einer {@link Meldung} werden Chatzeilen - ohne Server, ohne Bukkit.
 *
 * <p>Dieselbe Bauart und derselbe Grund wie bei {@link Blocktext}: Was hier steht, laeuft
 * durch Proben, und was daraus wird, ist eine Liste von Chatzeilen. Ein {@code Player} im
 * Rumpf braeuchte einen laufenden Server, und dann waere die Zeile, die ein Moderator
 * wirklich liest, nur auf einem gestarteten Server pruefbar - also gar nicht.
 *
 * <p><b>Die NAMEN kommen als Naht herein</b> ({@link Namensgeber}), nicht aus
 * {@code Bukkit}: {@code getOfflinePlayer} wirft ohne Server. Im Spiel steht dahinter
 * {@link Spielername#von}, das auf die ersten Zeichen der Kennung zurueckfaellt, wenn der
 * Server den Namen nicht mehr kennt.
 *
 * <p><b>Das ALTER wird hier gerechnet, und zwar an EINER Stelle</b>
 * ({@link #alter}). Drei Groessenordnungen - Minuten, Stunden, Tage - und eine Grenze
 * dazwischen; wer das je Aufrufer rechnet, bekommt in der Liste "vor 1 Stunden" und in
 * der Einzelansicht "vor 93 Minuten" fuer dieselbe Meldung.
 */
final class Meldetext {

    /**
     * Woher der Name zu einer Kennung kommt.
     *
     * <p>Eine Naht und kein statischer Aufruf - siehe den Klassenkopf.
     */
    interface Namensgeber {
        String nameVon(UUID wer);
    }

    /** Ab dieser Dauer wird in Stunden gerechnet. */
    private static final long MINUTEN_BIS_STUNDEN = 60L;

    /** Ab dieser Dauer wird in Tagen gerechnet. */
    private static final long STUNDEN_BIS_TAGE = 24L;

    private Meldetext() {
    }

    /**
     * Eine Zeile der Liste.
     *
     * <p>Der GRUND steht mit darin, beschnitten auf Chatbreite: Ohne ihn muesste ein
     * Moderator jede Meldung einzeln oeffnen, um zu entscheiden, welche dringt. Mit ihm
     * sieht er die Liste und waehlt.
     */
    static Component listenzeile(Sprache sprache, Meldung eine, Instant jetzt,
                                 Namensgeber namen) {
        return sprache.eineZeile(MeldeSatz.LISTE_ZEILE,
                Einsatz.von("nummer", eine.nummer())
                        .mit("melder", namen.nameVon(eine.melder()))
                        .mit("betroffener", namen.nameVon(eine.betroffener()))
                        .mit("grund", gekuerzt(eine.grund()))
                        .mit("alter", alter(sprache, eine.gemeldetAm(), jetzt))
                        .mit("stand", standwort(sprache, eine.stand())));
    }

    /**
     * Eine einzelne Meldung, vollstaendig.
     *
     * <p>Drei bis fuenf Zeilen: Kopf, Wer, Grund, Ort - dazu Bearbeiter und Vermerk, wenn
     * es sie gibt. <b>Weggelassen und nicht leer gesendet:</b> Eine Zeile "Bearbeiter:"
     * ohne Namen saehe aus wie ein Fehler, und bei einer offenen Meldung gibt es keinen.
     */
    static List<Component> einzeln(Sprache sprache, Meldung eine, Instant jetzt,
                                   Namensgeber namen) {
        List<Component> zeilen = new ArrayList<>();
        zeilen.add(sprache.eineZeile(MeldeSatz.ZEIGEN_KOPF,
                Einsatz.von("nummer", eine.nummer())
                        .mit("stand", standwort(sprache, eine.stand()))));
        zeilen.add(sprache.eineZeile(MeldeSatz.ZEIGEN_WER,
                Einsatz.von("melder", namen.nameVon(eine.melder()))
                        .mit("betroffener", namen.nameVon(eine.betroffener()))
                        .mit("alter", alter(sprache, eine.gemeldetAm(), jetzt))));
        // Der GANZE Grund, nicht der gekuerzte: Hier ist Platz, und genau dafuer oeffnet
        // man eine Meldung einzeln.
        zeilen.add(sprache.eineZeile(MeldeSatz.ZEIGEN_GRUND,
                Einsatz.von("grund", eine.grund())));
        zeilen.add(sprache.eineZeile(MeldeSatz.ZEIGEN_ORT,
                Einsatz.von("welt", eine.welt())
                        .mit("x", eine.x())
                        .mit("y", eine.y())
                        .mit("z", eine.z())
                        .mit("nummer", eine.nummer())));
        eine.wirdBearbeitetVon().ifPresent(wer ->
                zeilen.add(sprache.eineZeile(MeldeSatz.ZEIGEN_BEARBEITER,
                        Einsatz.von("bearbeiter", namen.nameVon(wer)))));
        if (!eine.vermerk().isEmpty()) {
            zeilen.add(sprache.eineZeile(MeldeSatz.ZEIGEN_VERMERK,
                    Einsatz.von("vermerk", eine.vermerk())));
        }
        return List.copyOf(zeilen);
    }

    /**
     * Die Antworten an den Melder - Kopf und je Meldung eine Zeile.
     *
     * <p><b>Das ist die Ausgabe, mit der Plan 12c.3 das Meldewesen begruendet.</b> Je
     * Ausgang ein eigener Satz: Wer liest "bearbeitet", weiss nicht, ob man ihm geglaubt
     * hat.
     */
    static List<Component> antworten(Sprache sprache, List<Meldung> warten,
                                     Namensgeber namen) {
        if (warten.isEmpty()) {
            return List.of();
        }
        List<Component> zeilen = new ArrayList<>(
                sprache.bau(MeldeSatz.ANTWORT_KOPF, warten.size(),
                        Einsatz.von("anzahl", warten.size())));
        for (Meldung eine : warten) {
            Einsatz werte = Einsatz.von("nummer", eine.nummer())
                    .mit("betroffener", namen.nameVon(eine.betroffener()))
                    .mit("vermerk", eine.vermerk().isEmpty()
                            ? sprache.wort(MeldeSatz.OHNE_VERMERK_WORT, Einsatz.keine())
                            : eine.vermerk());
            // Ein Schalter ohne default: Wer Meldestand einen fuenften beantworteten
            // Wert hinzufuegt, wird gefragt, was der Melder dann liest. Die zwei
            // unbeantworteten koennen hier nicht ankommen - ungeleseneVon gibt nur
            // beantwortete -, und ein Wurf sagt das, statt eine falsche Zeile zu senden.
            zeilen.addAll(switch (eine.stand()) {
                case ABGESCHLOSSEN -> sprache.bau(MeldeSatz.ANTWORT_ABGESCHLOSSEN, 1L,
                        werte);
                case ABGEWIESEN -> sprache.bau(MeldeSatz.ANTWORT_ABGEWIESEN, 1L, werte);
                case OFFEN, IN_ARBEIT -> throw new IllegalStateException(
                        "Die Meldung " + eine.nummer() + " steht auf " + eine.stand()
                                + " und ist damit keine Antwort. Hierher kommen nur"
                                + " beantwortete - wer das aendert, sendet dem Melder"
                                + " eine Zeile ueber eine Entscheidung, die niemand"
                                + " getroffen hat.");
            });
        }
        return List.copyOf(zeilen);
    }

    /**
     * Das Alter als fertiges Wort - die EINE Stelle, an der die Grenzen stehen.
     *
     * <p>Unter einer Minute "soeben", dann Minuten, ab einer Stunde Stunden, ab einem Tag
     * Tage. ABGERUNDET, und das ist Absicht: "vor 2 Stunden" fuer 2 Stunden 50 Minuten
     * ist naeher an der Wahrheit als "vor 3 Stunden", weil ein Moderator danach
     * entscheidet, was dringt.
     *
     * <p>Eine Dauer in der ZUKUNFT gibt "soeben" und keine negative Zahl: Das kommt bei
     * einer verstellten Uhr vor, und "vor -3 Minuten" ist keine Auskunft.
     */
    static String alter(Sprache sprache, Instant gemeldetAm, Instant jetzt) {
        Duration vergangen = Duration.between(gemeldetAm, jetzt);
        if (vergangen.isNegative() || vergangen.toMinutes() < 1L) {
            return sprache.wort(MeldeSatz.ALTER_SOEBEN, Einsatz.keine());
        }
        long minuten = vergangen.toMinutes();
        if (minuten < MINUTEN_BIS_STUNDEN) {
            return sprache.wort(MeldeSatz.ALTER_MINUTEN, Einsatz.von("anzahl", minuten));
        }
        long stunden = vergangen.toHours();
        if (stunden < STUNDEN_BIS_TAGE) {
            return sprache.wort(MeldeSatz.ALTER_STUNDEN, Einsatz.von("anzahl", stunden));
        }
        long tage = vergangen.toDays();
        return sprache.wort(MeldeSatz.ALTER_TAGE, Einsatz.von("anzahl", tage));
    }

    /** Das Wort fuer einen Stand - ueber {@link MeldeSatz#standwort}. */
    static String standwort(Sprache sprache, Meldestand stand) {
        return sprache.wort(MeldeSatz.standwort(stand), Einsatz.keine());
    }

    /**
     * Wie lang ein Grund in der LISTE hoechstens wird.
     *
     * <p>48 Zeichen, damit Nummer, zwei Namen, Alter und Stand in derselben Chatzeile
     * Platz haben. Der ganze Grund steht in der Einzelansicht - abgeschnitten wird nur
     * die Uebersicht, und das sagt das Zeichen am Ende.
     */
    static final int GRUND_IN_DER_LISTE = 48;

    /**
     * Der Grund, beschnitten auf Chatbreite.
     *
     * <p>Mit einem Auslassungszeichen, damit kein Moderator glaubt, der Grund sei kurz:
     * Ein stumm abgeschnittener Satz liest sich wie ein vollstaendiger, und dann oeffnet
     * er die Meldung nicht, in der die Haelfte steht.
     */
    private static String gekuerzt(String grund) {
        if (grund.length() <= GRUND_IN_DER_LISTE) {
            return grund;
        }
        return grund.substring(0, GRUND_IN_DER_LISTE - 1).trim() + "…";
    }
}
