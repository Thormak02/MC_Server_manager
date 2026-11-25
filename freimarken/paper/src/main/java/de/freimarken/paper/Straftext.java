package de.freimarken.paper;

import de.freimarken.kern.Strafart;
import de.freimarken.kern.Strafdauer;
import de.freimarken.kern.Strafe;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.JoinConfiguration;

/**
 * Aus einer {@link Strafe} werden Chatzeilen - ohne Server, ohne Bukkit.
 *
 * <p>Dieselbe Bauart und derselbe Grund wie bei {@link Meldetext} und {@link Blocktext}:
 * Was hier steht, laeuft durch Proben, und was daraus wird, ist eine Liste von
 * Chatzeilen. Ein {@code Player} im Rumpf braeuchte einen laufenden Server - dann waere
 * die Zeile, die ein gesperrter Spieler an der Tuer liest, nur auf einem gestarteten
 * Server pruefbar, also gar nicht.
 *
 * <p><b>Die NAMEN kommen als Naht herein</b> ({@link Meldetext.Namensgeber}, geteilt mit
 * Teil C), nicht aus {@code Bukkit}: {@code getOfflinePlayer} wirft ohne Server. Eine
 * zweite Schnittstelle fuer dieselbe Frage waere eine, die eines Tages anders
 * zurueckfaellt.
 *
 * <p><b>Die DAUER wird an EINER Stelle in Worte gefasst</b> ({@link #dauerwort}), und das
 * ist hier wichtiger als bei den Meldungen: Dieselbe Zahl steht in der Zeile des
 * Moderators, in der Ansage an alle, in der Bannliste und in der Zeile an der Tuer. Wer
 * sie je Aufrufer rechnet, sagt dem Bestraften "noch 2 Stunden" und allen anderen "noch
 * 3".
 *
 * <p><b>Dauerhaft wird NIE als Zeitpunkt geschrieben.</b> Ein Datum im Jahr 2126 liest
 * sich wie eine Frist, und ein Spieler, der darauf wartet, ist einer, dem niemand gesagt
 * hat, dass er nicht mehr kommt. Die Umschaltung steht in {@link #bisWort} und
 * {@link #dauerwort} - beide fragen zuerst {@link Strafe#unbegrenzt()}.
 */
final class Straftext {

    /** Ab dieser Dauer wird in Minuten gerechnet. */
    private static final long SEKUNDEN_BIS_MINUTEN = 60L;

    /** Ab dieser Dauer wird in Stunden gerechnet. */
    private static final long MINUTEN_BIS_STUNDEN = 60L;

    /** Ab dieser Dauer wird in Tagen gerechnet. */
    private static final long STUNDEN_BIS_TAGE = 24L;

    /**
     * Wie lang ein Grund in einer LISTE hoechstens wird.
     *
     * <p>48 Zeichen, dieselbe Zahl wie bei den Meldungen
     * ({@link Meldetext#GRUND_IN_DER_LISTE}) und aus demselben Grund: Nummer, Art, Stand
     * und Zeitpunkt brauchen in derselben Chatzeile Platz. Der ganze Grund steht in der
     * Zeile, die der Betroffene liest - dort wird NICHT gekuerzt, denn das ist die
     * Erklaerung, die Plan 12c.3 verlangt.
     */
    static final int GRUND_IN_DER_LISTE = 48;

    private Straftext() {
    }

    /**
     * Eine Zeile der Vorgeschichte.
     *
     * <p>Mit dem STAND, und das ist der Teil, der die Liste lesbar macht: Eine
     * aufgehobene Strafe steht hier wie jede andere, und ohne das Wort davor saehe ein
     * Moderator drei Banne, von denen zwei zurueckgenommen wurden.
     */
    static Component verlaufszeile(Sprache sprache, Strafe eine, Instant jetzt,
                                   ZoneId zone) {
        return sprache.eineZeile(StrafSatz.VERLAUF_ZEILE,
                Einsatz.von("nummer", eine.nummer())
                        .mit("art", artwort(sprache, eine.art()))
                        .mit("stand", standwort(sprache, eine, jetzt))
                        .mit("wann", zeitwort(sprache, eine.verhaengtAm(), zone))
                        .mit("grund", gekuerzt(eine.grund())));
    }

    /**
     * Die Vorgeschichte eines Spielers - Kopf, Zeilen, Hinweis.
     *
     * <p>Der HINWEIS steht unter der Liste und nicht darueber: Wer die Liste liest, sucht
     * zuerst, was darin steht; der Weg zum Aufheben ist der Schritt danach.
     */
    static List<Component> verlauf(Sprache sprache, List<Strafe> strafen, String spieler,
                                   Instant jetzt, ZoneId zone) {
        if (strafen.isEmpty()) {
            return List.copyOf(sprache.bau(StrafSatz.VERLAUF_LEER, 1L,
                    Einsatz.von("spieler", spieler)));
        }
        List<Component> zeilen = new ArrayList<>(
                sprache.bau(StrafSatz.VERLAUF_KOPF, strafen.size(),
                        Einsatz.von("anzahl", strafen.size())
                                .mit("spieler", spieler)));
        for (Strafe eine : strafen) {
            zeilen.add(verlaufszeile(sprache, eine, jetzt, zone));
        }
        zeilen.addAll(sprache.bau(StrafSatz.VERLAUF_HINWEIS, 1L, Einsatz.keine()));
        return List.copyOf(zeilen);
    }

    /**
     * Die oeffentliche Bannliste - Kopf und je Bann eine Zeile.
     *
     * <p>Plan 12c.4: <i>"Bannliste mit Grund und Dauer - oeffentlich, wie im
     * Vorbild."</i> Beides steht in der Zeile. Der GRUND wird gekuerzt wie in jeder
     * Liste; wer mehr wissen will, fragt einen Moderator - die Einzelansicht einer Strafe
     * ist nicht oeffentlich, denn sie nennt den Bearbeiter.
     */
    static List<Component> bannliste(Sprache sprache, List<Strafe> banne, Instant jetzt,
                                     ZoneId zone, Meldetext.Namensgeber namen) {
        if (banne.isEmpty()) {
            return List.copyOf(sprache.bau(StrafSatz.BANNLISTE_LEER, 1L,
                    Einsatz.keine()));
        }
        List<Component> zeilen = new ArrayList<>(
                sprache.bau(StrafSatz.BANNLISTE_KOPF, banne.size(),
                        Einsatz.von("anzahl", banne.size())));
        for (Strafe eine : banne) {
            zeilen.add(sprache.eineZeile(StrafSatz.BANNLISTE_ZEILE,
                    Einsatz.von("spieler", namen.nameVon(eine.betroffener()))
                            .mit("bis", bisWort(sprache, eine, zone))
                            .mit("grund", gekuerzt(eine.grund()))));
        }
        return List.copyOf(zeilen);
    }

    /**
     * Die Zeilen, die der Betroffene liest - je Art und Dauer eine andere.
     *
     * <p><b>Das ist die Ausgabe, mit der Plan 12c.3 die Strafen begruendet:</b> <i>"der
     * Grund wird dem Betroffenen angezeigt. Eine Strafe, die niemand erklaert, erzeugt
     * nur Wiederkehr."</i> Darum steht der GANZE Grund darin, ungekuerzt.
     *
     * <p>Beim Bann je nach Dauer ein anderer Satz: Wer dauerhaft gesperrt ist, soll das
     * lesen und nicht ein Datum, das ihn hoffen laesst. Beim Rauswurf gibt es keine
     * Dauer, also auch keine Unterscheidung.
     *
     * <p><b>EINE LISTE und kein einzelnes Component</b>, und das ist kein Geschmack:
     * Die Saetze an der Tuer haben eine Umbruchmarke - Grund, Frist und Ausweg sind
     * drei Zeilen, weil ein gesperrter Spieler sie auf einem eigenen Bildschirm liest.
     * {@code Sprache.eineZeile} WIRFT fuer so einen Satz, und zwar mitten im Zweig, der
     * ihn senden soll: Der Spieler laese nichts und der Betreiber einen Stapelabzug
     * (gemessen von VorlageTest 10h). Wer ein einzelnes Component braucht - ein Kick
     * oder eine Abweisung an der Tuer nimmt nur eines -, nimmt {@link #alsEine}.
     *
     * @param wer wer sie verhaengt hat, schon als Wort - oder leer
     */
    static List<Component> getroffen(Sprache sprache, Strafe eine, Instant jetzt,
                                     ZoneId zone, String wer) {
        return switch (eine.art()) {
            case RAUSWURF -> sprache.bau(StrafSatz.GETROFFEN_RAUSWURF, 1L,
                    Einsatz.von("grund", eine.grund()).mit("wer", wer));
            case BANN -> eine.unbegrenzt()
                    ? sprache.bau(StrafSatz.GETROFFEN_BANN_DAUERHAFT, 1L,
                            Einsatz.von("grund", eine.grund()))
                    : sprache.bau(StrafSatz.GETROFFEN_BANN, 1L,
                            Einsatz.von("grund", eine.grund())
                                    .mit("bis", bisWort(sprache, eine, zone))
                                    .mit("dauer", restwort(sprache, eine, jetzt)));
            case STUMM -> eine.unbegrenzt()
                    ? sprache.bau(StrafSatz.GETROFFEN_STUMM_DAUERHAFT, 1L,
                            Einsatz.von("grund", eine.grund()))
                    : sprache.bau(StrafSatz.GETROFFEN_STUMM, 1L,
                            Einsatz.von("grund", eine.grund())
                                    .mit("dauer", restwort(sprache, eine, jetzt)));
        };
    }

    /**
     * Mehrere Zeilen als EIN Component, mit echten Umbruechen dazwischen.
     *
     * <p>Fuer die zwei Stellen, die nur eines nehmen: {@code Player.kick} und
     * {@code AsyncPlayerPreLoginEvent.disallow}. Beide zeigen den Text auf einem
     * eigenen Bildschirm, und dort ist ein mehrzeiliger Text genau richtig - ein
     * Rauswurf nennt den Grund, und darunter steht, von wem er kommt.
     *
     * <p>Die EINE Stelle, die das zusammenfuegt: Mit zwei Stellen waere eine davon die,
     * die eines Tages nur die erste Zeile nimmt - und dann faellt der Grund weg, also
     * genau das, was Plan 12c.3 verlangt.
     */
    static Component alsEine(List<Component> zeilen) {
        if (zeilen.isEmpty()) {
            // Kann heute nicht eintreten - jeder dieser Saetze hat mindestens eine
            // Zeile, und ein leerer waere eine Klage beim Laden. Der Zweig steht hier,
            // damit ein Kick mit leerem Text keine Ausnahme wirft: Eine Abweisung ohne
            // Grund ist schlecht, eine Ausnahme beim Abweisen ist schlimmer.
            return Component.empty();
        }
        return Component.join(JoinConfiguration.newlines(), zeilen);
    }

    /**
     * Die Zeile fuer einen Befehl, den die Stummschaltung mitsperrt.
     *
     * <p>Ein eigener Satz: Wer {@code /msg} tippt und "du bist stummgeschaltet" liest,
     * haelt es fuer einen Fehler - er hat ja nicht in den Chat geschrieben.
     */
    static Component befehlGesperrt(Sprache sprache, Strafe eine, String befehl,
                                    Instant jetzt) {
        return sprache.eineZeile(StrafSatz.GETROFFEN_BEFEHL,
                Einsatz.von("befehl", befehl)
                        .mit("grund", eine.grund())
                        .mit("dauer", eine.unbegrenzt()
                                ? sprache.wort(StrafSatz.DAUER_DAUERHAFT,
                                        Einsatz.keine())
                                : restwort(sprache, eine, jetzt)));
    }

    /** Das Wort fuer eine Art - ueber {@link StrafSatz#artwort}. */
    static String artwort(Sprache sprache, Strafart art) {
        return sprache.wort(StrafSatz.artwort(art), Einsatz.keine());
    }

    /**
     * Wo eine Strafe steht - gilt, abgelaufen oder aufgehoben.
     *
     * <p>Die Reihenfolge der Fragen ist die Zusage: AUFGEHOBEN zuerst, denn eine
     * aufgehobene Strafe, deren Zeit auch noch abgelaufen ist, ist vor allem
     * zurueckgenommen - das ist die Auskunft, nach der ein zweiter Moderator fragt. Eine
     * Reihenfolge andersherum liesse die Aufhebung nach Ablauf der Frist verschwinden.
     */
    static String standwort(Sprache sprache, Strafe eine, Instant jetzt) {
        if (eine.istAufgehoben()) {
            return sprache.wort(StrafSatz.STAND_AUFGEHOBEN, Einsatz.keine());
        }
        if (eine.giltNoch(jetzt)) {
            return sprache.wort(StrafSatz.STAND_GILT, Einsatz.keine());
        }
        return sprache.wort(StrafSatz.STAND_ABGELAUFEN, Einsatz.keine());
    }

    /**
     * Eine Dauer als Wort - die EINE Stelle, an der die Groessenordnungen stehen.
     *
     * <p>Unter einer Minute Sekunden, dann Minuten, ab einer Stunde Stunden, ab einem Tag
     * Tage. ABGERUNDET, wie beim Alter einer Meldung: "noch 2 Std." fuer 2 Stunden 50
     * Minuten ist naeher an der Wahrheit als "noch 3", weil der Bestrafte danach
     * entscheidet, ob er wartet.
     *
     * <p>Eine unbegrenzte Dauer gibt das Wort {@code dauerhaft} und keine Zahl.
     */
    static String dauerwort(Sprache sprache, Strafdauer dauer) {
        if (dauer == null || dauer.unbegrenzt()) {
            return sprache.wort(StrafSatz.DAUER_DAUERHAFT, Einsatz.keine());
        }
        return spanne(sprache, dauer.spanne());
    }

    /**
     * Was von einer Strafe noch uebrig ist, als Wort.
     *
     * <p>Fuer die Zeile, die der Betroffene liest. Eine abgelaufene oder aufgehobene
     * Strafe gibt die kleinste Einheit und keine negative Zahl: Hierher kommt sie nur,
     * wenn zwischen dem Lesen und dem Senden die Frist abgelaufen ist, und "noch -3
     * Min." waere keine Auskunft.
     */
    static String restwort(Sprache sprache, Strafe eine, Instant jetzt) {
        if (eine.unbegrenzt()) {
            return sprache.wort(StrafSatz.DAUER_DAUERHAFT, Einsatz.keine());
        }
        long rest = eine.restSekunden(jetzt);
        if (rest <= 0L) {
            return sprache.wort(StrafSatz.DAUER_SEKUNDEN, Einsatz.von("anzahl", 0L));
        }
        return spanne(sprache, Duration.ofSeconds(rest));
    }

    /**
     * Bis wann eine Strafe gilt - als Zeitpunkt oder als {@code dauerhaft}.
     *
     * <p>Ein ZEITPUNKT und keine Restdauer, denn das ist, was ein Gesperrter wissen
     * will: Eine Zeile "noch 3 Tage" muss er jedes Mal neu lesen, ein Datum merkt er
     * sich. Gerechnet in die eingestellte Zone - UTC stuende im Chat als eine Uhrzeit,
     * die er nicht wiedererkennt.
     */
    static String bisWort(Sprache sprache, Strafe eine, ZoneId zone) {
        return eine.endet()
                .map(ende -> zeitwort(sprache, ende, zone))
                .orElseGet(() -> sprache.wort(StrafSatz.DAUER_DAUERHAFT,
                        Einsatz.keine()));
    }

    /**
     * Ein Zeitpunkt in der eingestellten Zone.
     *
     * <p>{@code atZone} ist Pflicht: Die Ablage schreibt UTC, und ohne die Umrechnung
     * stuende im Chat eine Zeit, die der Spieler nicht wiedererkennt - im Sommer zwei
     * Stunden daneben. Dasselbe tun {@link Blocktext} und {@link Verlaufszeile}; bei
     * einer Sperre ist es die Zeit, auf die jemand wartet.
     */
    static String zeitwort(Sprache sprache, Instant wann, ZoneId zone) {
        return sprache.muster(StrafSatz.ZEIT_MUSTER).format(wann.atZone(zone));
    }

    /**
     * Wer etwas getan hat - der Name, oder das Wort fuer die Konsole.
     *
     * <p>{@code null} heisst in der Ablage die Konsole. Ohne dieses Wort stuende in der
     * Liste eine leere Stelle, und ein erfundener Name waere schlimmer: Er saehe wie ein
     * Spieler aus, und jemand suchte nach ihm.
     */
    static String werwort(Sprache sprache, Optional<UUID> wer,
                          Meldetext.Namensgeber namen) {
        return wer.map(namen::nameVon)
                .orElseGet(() -> sprache.wort(StrafSatz.WER_KONSOLE, Einsatz.keine()));
    }

    /** Eine Spanne in der groessten passenden Einheit. */
    private static String spanne(Sprache sprache, Duration spanne) {
        long sekunden = spanne.getSeconds();
        if (sekunden < SEKUNDEN_BIS_MINUTEN) {
            return sprache.wort(StrafSatz.DAUER_SEKUNDEN,
                    Einsatz.von("anzahl", sekunden));
        }
        long minuten = spanne.toMinutes();
        if (minuten < MINUTEN_BIS_STUNDEN) {
            return sprache.wort(StrafSatz.DAUER_MINUTEN,
                    Einsatz.von("anzahl", minuten));
        }
        long stunden = spanne.toHours();
        if (stunden < STUNDEN_BIS_TAGE) {
            return sprache.wort(StrafSatz.DAUER_STUNDEN,
                    Einsatz.von("anzahl", stunden));
        }
        return sprache.wort(StrafSatz.DAUER_TAGE,
                Einsatz.von("anzahl", spanne.toDays()));
    }

    /**
     * Der Grund, beschnitten auf Chatbreite.
     *
     * <p>Mit einem Auslassungszeichen, damit kein Leser glaubt, der Grund sei kurz: Ein
     * stumm abgeschnittener Satz liest sich wie ein vollstaendiger. <b>Nur in LISTEN</b> -
     * die Zeile an den Betroffenen traegt den ganzen Grund, denn sie ist die Erklaerung,
     * die der Plan verlangt.
     */
    private static String gekuerzt(String grund) {
        if (grund.length() <= GRUND_IN_DER_LISTE) {
            return grund;
        }
        return grund.substring(0, GRUND_IN_DER_LISTE - 1).trim() + "…";
    }
}
