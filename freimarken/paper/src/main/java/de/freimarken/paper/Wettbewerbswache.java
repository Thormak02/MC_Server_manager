package de.freimarken.paper;

import de.freimarken.kern.Wettbewerbsbuch;
import java.time.Clock;
import java.util.Optional;
import org.bukkit.entity.Item;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerFishEvent;

/**
 * Die Hand, die einen Fang fuer den Angelwettbewerb ZAEHLT (Plan 14).
 *
 * <p><b>Die Prioritaet ist tragend, nicht Geschmack: {@link EventPriority#MONITOR}.</b>
 * {@link Angelwache} TAUSCHT den Fang auf {@link EventPriority#HIGH} - sie ersetzt
 * manchmal, was Vanilla gewuerfelt hat, durch eine Zeile der Angeltabelle. Zaehlte diese
 * Wache davor, zaehlte sie den Vanillafang: Ein Meeresherz waere dann ein Kabeljau, und
 * die 100 Punkte der Punktetabelle waeren Punkte, die niemand je bekommt. MONITOR ist
 * dieselbe Stufe, auf der auch {@link Verdienstwache#beimFischen} liest, und aus genau
 * demselben Grund.
 *
 * <p><b>Dieselben drei Tore wie die Zahlung und der Tausch</b>, und damit stehen sie nun
 * zum DRITTEN Mal im Haus: der Zustand {@code CAUGHT_FISH}, das OFFENE WASSER (das laesst
 * das Ein-Block-Loch heraus, an dem ein Klickautomat Tag und Nacht angelt) und
 * {@code instanceof Item} (der gehakte Tintenfisch ist kein Fang). Der Klassenkopf der
 * {@link Angelwache} nennt das den Preis dafuer, dass jede Wache fuer sich verstaendlich
 * bleibt; eine Tafel haelt die Reihenfolge fest.
 *
 * <p><b>KEIN Weltvergleich</b>, und das ist der eine Unterschied zu jenen beiden: Der
 * Wettbewerb ist nicht an Hafenwerder gebunden. Wer in Altfurt, in einer Freistadt oder
 * auf seiner Wolkeninsel angelt, soll mitspielen koennen - eine Beschraenkung auf eine
 * Welt waere eine Entscheidung ueber den Server, die der Betreiber nicht getroffen hat,
 * und sie waere aus der {@code config.yml} nicht zurueckzunehmen.
 *
 * <p><b>Diese Wache darf NIE werfen.</b> Sie haengt in einem Fangereignis, und ein Wurf
 * von hier nimmt dem Spieler seinen Fang. {@link Wettbewerbsbuch#zaehleFang} faengt
 * deshalb alles ab und gibt bei jedem Fehler leer zurueck - ein verlorener Punkt ist ein
 * Punkt und kein Geld.
 */
final class Wettbewerbswache implements Listener {

    private final Wettbewerbsbuch buch;
    private final Clock uhr;
    private final Sprache sprache;
    private final Blocktext.Namensgeber namen;
    private final java.util.function.Supplier<
            java.util.Collection<? extends org.bukkit.entity.Player>> alle;
    private final java.util.function.Function<java.util.UUID,
            org.bukkit.entity.Player> spieler;
    private final java.util.logging.Logger protokoll;

    /**
     * Der Merker der Beginn-Ansage - FLUECHTIG, siehe {@link #sageBeginnAn}.
     *
     * <p>{@code null} heisst "noch nichts angesagt". Nach einem Neustart mitten im
     * Fenster wird noch einmal angesagt, und das ist der ganze Schaden.
     */
    private java.time.LocalDate angesagt;

    /**
     * @param alle    wer gerade da ist - als Naht, denn {@code Bukkit.getOnlinePlayers}
     *                braucht einen Server. Ohne sie waere die Ansage der eine Zweig, den
     *                keine Probe durchlaeuft
     * @param spieler ein Spieler zu seiner Kennung, oder {@code null}, wenn er nicht da
     *                ist - fuer die Gewinnmeldung an jeden Sieger einzeln
     */
    Wettbewerbswache(Wettbewerbsbuch buch, Clock uhr, Sprache sprache,
                     Blocktext.Namensgeber namen,
                     java.util.function.Supplier<
                             java.util.Collection<? extends org.bukkit.entity.Player>> alle,
                     java.util.function.Function<java.util.UUID,
                             org.bukkit.entity.Player> spieler,
                     java.util.logging.Logger protokoll) {
        if (buch == null || uhr == null || sprache == null || namen == null
                || alle == null || spieler == null || protokoll == null) {
            throw new IllegalArgumentException("Die Wettbewerbswache braucht das Buch,"
                    + " eine Uhr, Sprache, die Namensnaht, beide Spielernaehte und das"
                    + " Protokoll");
        }
        this.buch = buch;
        this.uhr = uhr;
        this.sprache = sprache;
        this.namen = namen;
        this.alle = alle;
        this.spieler = spieler;
        this.protokoll = protokoll;
    }

    /**
     * Ein Fang - und die Frage, ob er zaehlt.
     *
     * <p>{@code ignoreCancelled = true}: Ein abgebrochenes Fangereignis hat keinen Fang,
     * und ein Punkt dafuer waere ein Punkt fuer nichts.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void beimFischen(PlayerFishEvent ereignis) {
        if (ereignis.getState() != PlayerFishEvent.State.CAUGHT_FISH) {
            return;
        }
        if (!ereignis.getHook().isInOpenWater()) {
            return;
        }
        if (!(ereignis.getCaught() instanceof Item gegenstand)) {
            return;
        }
        // Der Name aus dem Stapel, den der Spieler WIRKLICH bekommt - nach dem Tausch
        // der Angelwache. Siehe den Klassenkopf: Das ist der ganze Grund fuer MONITOR.
        String name = gegenstand.getItemStack().getType().name()
                .toLowerCase(java.util.Locale.ROOT);
        buch.zaehleFang(ereignis.getPlayer().getUniqueId(), uhr.instant(), name);
    }

    /**
     * Sagt an, wenn ein Fenster aufgeht - und rechnet ab, wenn eines zu ist.
     *
     * <p>Der Mitfahrer im Takt der Spielzeitwache. <b>KEIN eigener Zeitgeber</b>, und das
     * ist eine Hausregel mit Begruendung (Plan 8.8): Der Takt zaehlt die Uhr und nicht
     * die Takte. Diese Methode ist darum ein BILLIGER Vortest - sie fragt das Buch, ob
     * sich etwas geaendert hat, und tut sonst nichts. Der Takt schlaegt oft.
     *
     * <p><b>Sie darf nie werfen</b>, aus demselben Grund wie die Mietwache und die
     * Blockraeumung: Eine Ausnahme von hier beendete den Zeitgeber, und der Verlust waere
     * die Spielzeit aller Spieler bis zum naechsten Neustart.
     */
    void imTakt() {
        try {
            java.time.Instant jetzt = uhr.instant();
            sageBeginnAn(jetzt);
            rechneAb(jetzt);
        } catch (RuntimeException fehler) {
            // Die zweite Schranke. Was das Buch selbst faengt, kommt hier nicht an.
        }
    }

    /**
     * Die Ansage, wenn ein Fenster aufgeht - einmal je Lauf.
     *
     * <p><b>Der Merker ist FLUECHTIG</b>, und das ist hier richtig: Nach einem Neustart
     * mitten im Fenster wird noch einmal angesagt. Eine doppelte Chatzeile ist der ganze
     * Schaden; ein Merker in der Datenbank waere eine Zeile je Tag fuer eine Zeile im
     * Chat. Bei den PUNKTEN ist es umgekehrt - dort liegt Geld, und darum liegen sie in
     * der Datenbank.
     */
    private void sageBeginnAn(java.time.Instant jetzt) {
        java.util.Optional<de.freimarken.kern.Wettbewerbswerte.Lauf> offen =
                buch.offenerLauf(jetzt);
        if (offen.isEmpty()) {
            return;
        }
        java.time.LocalDate lauf = offen.get().schluessel();
        if (lauf.equals(angesagt)) {
            return;
        }
        angesagt = lauf;
        if (sprache.leerGelassen(WettbewerbSatz.ANSAGE_BEGINN)) {
            // Wer die Ansage nicht will, laesst sie leer - dann erfaehrt man vom
            // Wettbewerb nur ueber /wettbewerb. Der Merker ist trotzdem gesetzt: Sonst
            // fragte diese Methode bei jedem Takt die Sprachdatei erneut.
            return;
        }
        de.freimarken.kern.Wettbewerbswerte werte = buch.werte();
        sendeAnAlle(sprache.bau(WettbewerbSatz.ANSAGE_BEGINN, 1L,
                Einsatz.von("bis", ortszeit(offen.get().ende()))
                        .mit("startgeld", sprache.betrag(werte.startgeld()))));
    }

    /**
     * Die Abrechnung samt Siegerliste.
     *
     * <p>Nur ein WIRKLICH abgerechneter Lauf wird angesagt. Der Takt schlaegt jede
     * Minute, und ein Lauf wird einmal abgerechnet - ohne diese Bedingung stuende jede
     * Minute eine Siegerliste im Chat.
     */
    private void rechneAb(java.time.Instant jetzt) {
        java.util.Optional<de.freimarken.kern.Wettbewerbswerte.Lauf> faellig =
                buch.faelligerLauf(jetzt);
        if (faellig.isEmpty()) {
            return;
        }
        Wettbewerbsbuch.Entscheid entscheid = buch.rechneAb(faellig.get(), jetzt);
        if (entscheid.bescheid() != Wettbewerbsbuch.Bescheid.ABGERECHNET) {
            // SCHON_ABGERECHNET und NIEMAND_DABEI sind der Normalfall jedes weiteren
            // Takts.
            return;
        }
        // INS PROTOKOLL, und zwar als ERSTES [Fund der eigenen Gegenlese, 22.11.2026]:
        // Hier wird GELD verteilt, und dafuer hatte dieser Lauf keine Zeile - anders als
        // Mietlauf, Verfallslauf und Ladengebuehrlauf, die alle protokollieren. Die
        // Tabelle wettbewerb_lauf haelt Topf und Teilnehmerzahl, aber nicht, WER was
        // bekam; ein Betreiber, der gefragt wird "warum habe ich 300 Mark bekommen",
        // hatte keine Antwort.
        //
        // Als EINE Zeile und nicht je Sieger: Ein Lauf mit zwanzig Teilnehmern soll das
        // Protokoll nicht fuellen. Die Namen stehen nicht darin - dafuer gibt es die
        // Kennungen, und sie sind das, womit der Betreiber in der Datenbank sucht.
        StringBuilder zeile = new StringBuilder("Angelwettbewerb " + faellig.get().tag()
                + " abgerechnet: Topf " + entscheid.topf() + " Mark, "
                + entscheid.teilnehmer() + " Teilnehmer");
        for (Wettbewerbsbuch.Gewinner gewinner : entscheid.gewinner()) {
            zeile.append(" - ").append(gewinner.platz()).append(". ")
                    .append(gewinner.spieler()).append(" (")
                    .append(gewinner.punkte()).append(" Punkte, ")
                    .append(gewinner.preis()).append(" Mark)");
        }
        protokoll.log(java.util.logging.Level.INFO, zeile.toString());

        // DER GEWINNSATZ ZUERST, und zwar an jeden Sieger einzeln: Er liest ihn auch
        // dann, wenn der Betreiber die Ansagen leer gelassen hat. Was ein Spieler
        // bekommen hat, soll er erfahren.
        for (Wettbewerbsbuch.Gewinner gewinner : entscheid.gewinner()) {
            org.bukkit.entity.Player wer = spieler.apply(gewinner.spieler());
            if (wer == null) {
                // Nicht da. Sein Geld ist gebucht - das Konto wartet nicht auf ihn.
                continue;
            }
            sende(wer, sprache.bau(WettbewerbSatz.GEWONNEN, 1L,
                    Einsatz.von("platz", gewinner.platz())
                            .mit("punkte", gewinner.punkte())
                            .mit("preis", sprache.betrag(gewinner.preis()))));
        }
        if (sprache.leerGelassen(WettbewerbSatz.ANSAGE_ENDE)) {
            return;
        }
        java.util.List<net.kyori.adventure.text.Component> zeilen =
                new java.util.ArrayList<>(sprache.bau(WettbewerbSatz.ANSAGE_ENDE, 1L,
                        Einsatz.von("teilnehmer", entscheid.teilnehmer())
                                .mit("topf", sprache.betrag(entscheid.topf()))));
        if (entscheid.gewinner().isEmpty()) {
            if (!sprache.leerGelassen(WettbewerbSatz.ANSAGE_NIEMAND)) {
                zeilen.addAll(sprache.bau(WettbewerbSatz.ANSAGE_NIEMAND, 1L,
                        Einsatz.keine()));
            }
        } else if (!sprache.leerGelassen(WettbewerbSatz.ANSAGE_SIEGER)) {
            for (Wettbewerbsbuch.Gewinner gewinner : entscheid.gewinner()) {
                zeilen.addAll(sprache.bau(WettbewerbSatz.ANSAGE_SIEGER, 1L,
                        Einsatz.von("platz", gewinner.platz())
                                .mit("name", namen.von(gewinner.spieler()))
                                .mit("punkte", gewinner.punkte())
                                .mit("preis", sprache.betrag(gewinner.preis()))));
            }
        }
        sendeAnAlle(zeilen);
    }

    /** Ein Zeitpunkt als ORTSZEIT - dieselbe Zone, in der das Fenster liegt. */
    private String ortszeit(java.time.Instant augenblick) {
        return augenblick.atZone(buch.zone()).toLocalTime().toString();
    }

    /** Die EINE Sendestelle dieser Klasse. */
    private void sende(net.kyori.adventure.audience.Audience an,
                       java.util.List<net.kyori.adventure.text.Component> zeilen) {
        for (net.kyori.adventure.text.Component zeile : zeilen) {
            an.sendMessage(zeile);
        }
    }

    /**
     * An ALLE, die gerade da sind.
     *
     * <p>Ueber die Naht und nicht ueber {@code Bukkit.getOnlinePlayers}: Jener Aufruf
     * braucht einen Server, und dann waere die Ansage der eine Zweig, den keine Probe
     * durchlaeuft.
     */
    private void sendeAnAlle(java.util.List<net.kyori.adventure.text.Component> zeilen) {
        for (org.bukkit.entity.Player wer : alle.get()) {
            sende(wer, zeilen);
        }
    }
}
