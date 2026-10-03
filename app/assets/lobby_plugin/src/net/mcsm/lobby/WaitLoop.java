package net.mcsm.lobby;

/**
 * Zahlen und Entscheidungen der Lobby-Warteschleife - bewusst OHNE Bukkit, damit sie
 * ohne laufenden Server testbar sind (wie {@link TransferGuard}).
 *
 * <p>Warum ueberhaupt gewartet wird: der Client haengt einen {@code ReadTimeoutHandler(30)}
 * als ERSTEN Handler in seine Netty-Pipeline und bricht eine stumme Verbindung nach 30 s
 * ab - in jedem Zustand, auch beim Login, und von Fabric/Forge/NeoForge unangetastet. Ein
 * schlafender Modpack-Server braucht gemessen 35-56 s zum Hochfahren. Stilles Durchhalten
 * im Handshake ist damit arithmetisch unmoeglich, und KEIN Timeout-Wert aendert das.
 *
 * <p>In der Lobby sitzt der Spieler dagegen im PLAY-Zustand, wo Keep-Alive in jeder Version
 * laeuft: hier darf er beliebig lange warten. Deshalb pollt die Lobby und transferiert
 * erst, wenn das Ziel wirklich Logins annimmt.
 *
 * <p>Bewusst OHNE Sekunden- oder Prozentzahlen in den Meldungen: die Startdauer wird noch
 * nicht gemessen, und {@code get_start_progress} springt von 5 auf 97 (Prozess-Spawn) auf
 * 100 (Done-Zeile) - eine daran gehaengte Anzeige behauptete die ganzen ~53 s Modladen
 * hindurch "97 %". Eine erfundene Zahl ist schlechter als keine.
 */
final class WaitLoop {

    /** 100 Ticks = 5 s. Jeder Lauf kostet eine Runde ueber das Netz zum Manager. */
    static final long INTERVAL_TICKS = 100L;

    /** 48 x 5 s = 240 s, gut 4x die gemessenen 56 s des langsamsten Modpacks. */
    static final int MAX_RUNS = 48;

    /** Alle 3 Laeufe (= 15 s) eine Zeile - oefter ist Spam, seltener wirkt wie eingefroren. */
    private static final int PROGRESS_EVERY = 3;

    private WaitLoop() {
    }

    /** Ist die Obergrenze erreicht? Dann abbrechen statt endlos weiterzufragen. */
    static boolean exhausted(int run) {
        return run >= MAX_RUNS;
    }

    /**
     * Soll dieser Lauf eine Fortschrittszeile schicken?
     *
     * <p>Im letzten Lauf NICHT: dort kommt die Obergrenz-Meldung, und "startet noch ..."
     * unmittelbar davor wuerde ihr widersprechen.
     */
    static boolean isProgressRun(int run) {
        return run > 0 && run % PROGRESS_EVERY == 0 && !exhausted(run);
    }
}
