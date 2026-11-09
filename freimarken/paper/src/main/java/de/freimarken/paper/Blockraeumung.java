package de.freimarken.paper;

import de.freimarken.kern.Blockbuchablage;
import de.freimarken.kern.Blockbuchwerte;
import de.freimarken.kern.Tagesgrenze;
import de.freimarken.kern.Wirtschaftsstand;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Raeumt alte Zeilen aus dem Blockbuch - in begrenzten Happen, am Spielzeittakt.
 *
 * <p>Kein Bukkit, derselbe Schnitt wie bei {@link Mietlauf}. Das Spiel kommt hier gar
 * nicht herein: Was geloescht wird, entscheidet allein die Frist.
 *
 * <p><b>Dies ist das erste Loeschen nach ALTER in diesem Projekt.</b> Jedes andere
 * {@code DELETE} geht ueber einen Schluessel - eine Parzelle, einen Vertrag, ein
 * Abholstueck -, und das Abholfach verweigert eine Frist ausdruecklich. Darum hat dieser
 * Lauf zwei Vorsorgen, die es sonst nirgends braucht:
 *
 * <p><b>Erstens: der begrenzte Happen.</b> Ein unbegrenztes {@code DELETE} auf der
 * groessten Tabelle des Projekts haelt den Spielserver an - es laeuft im Hauptfaden, auf
 * der einen Verbindung, die die Datenschicht haelt. Und es trifft genau den
 * unguenstigsten Augenblick: Am ERSTEN faelligen Tageswechsel gehen Wochen auf einmal weg.
 * Je Schlag loescht darum hoechstens {@link Blockbuchwerte#jeAufraeumen()} Zeilen.
 *
 * <p><b>Zweitens: der Tagesmerker wird nur bei VOLLSTAENDIGER Raeumung gesetzt.</b> Das
 * ist der Unterschied zum {@link Mietlauf}, und ohne ihn waere der Happen eine Falle:
 * Jener Lauf ist nach einem Durchgang fertig und darf "heute erledigt" vermerken. Dieser
 * ist es nicht - reicht der Happen nicht, liegt der Rest noch da. Wuerde der Merker
 * trotzdem gesetzt, kaeme der naechste Versuch erst MORGEN, und bei einem Rueckstand von
 * Wochen holte die Frist nie auf: Die Tabelle wuechse schneller, als sie geraeumt wird.
 * Darum heisst "der Happen war voll" dasselbe wie "es ist noch mehr da", und der naechste
 * Takt raeumt weiter.
 *
 * <p><b>Die Altersgrenze wird in JAVA gerechnet, nie in SQL.</b> Kein
 * {@code NOW() - INTERVAL 30 DAY}: Das fragte die Uhr und die Zone des
 * Datenbankrechners, und die eingestellte Zone waere stumm wirkungslos - sichtbar erst an
 * Zeilen, die einen Tag zu frueh oder zu spaet verschwinden. Gerechnet wird mit
 * {@link Tagesgrenze} und der Zone aus dem {@link Wirtschaftsstand}, also derselben, nach
 * der auch der Tagesbonus und die Ladenmiete ihren Tageswechsel bestimmen.
 *
 * <p><b>Gerechnet wird bis zum TAGESBEGINN und nicht auf die Stunde.</b> Bei einer Frist
 * von 30 Tagen bleiben damit 31 Kalendertage stehen - der heutige und dreissig davor. Das
 * ist eine bewusste Rundung zur sicheren Seite: Eine Beschwerde, die am dreissigsten Tag
 * kommt, findet ihre Zeile noch. Die andere Richtung - auf die Stunde genau - loeschte
 * eine Zeile mitten am Tag, und dann haengt es an der Uhrzeit der Anfrage, ob die
 * Moderation sie noch sieht.
 *
 * <p><b>Nichts hiervon wirft nach aussen.</b> Gerufen wird aus dem Spielzeittakt, und
 * eine Ausnahme dort waere nicht eine verlorene Raeumung, sondern die Spielzeit aller
 * Spieler bis zum naechsten Neustart - dieselbe Begruendung wie bei {@link Mietlauf}.
 *
 * <p><b>Diese Raeumung laeuft auch, wenn das Blockbuch nichts mehr schreibt.</b> Sie
 * haengt darum nicht an der {@link Blockwache}, die bei einer leeren Auswahl gar nicht
 * angemeldet wird. Wer das Buch abschaltet, will seine alten Zeilen trotzdem nicht fuer
 * immer behalten - und sie waeren sonst genau das: unloeschbar, weil niemand mehr
 * aufraeumt.
 */
final class Blockraeumung {

    /** Was ein Taktschlag getan hat. */
    record Ergebnis(int geloescht, boolean mehrDa, boolean gestoert) {

        /** Der Normalfall: heute ist schon geraeumt, es war nichts zu tun. */
        static Ergebnis nichtsZuTun() {
            return new Ergebnis(0, false, false);
        }
    }

    private final Blockbuchablage ablage;
    private final Blockbuchwerte werte;

    /**
     * Woher die ZONE kommt - und nur sie.
     *
     * <p>Frist und Happen stehen in {@link Blockbuchwerte} und sind nicht neuladbar; die
     * Zone ist es, denn nach ihr richtet sich der Tageswechsel des ganzen Plugins. Zwei
     * Quellen fuer den Tageswechsel waeren zwei Tage, und dann raeumte dieser Lauf an
     * einem anderen Tag als der, an dem die Ladenmiete abbucht.
     */
    private final Wirtschaftsstand wirtschaft;

    private final Clock uhr;
    private final Logger protokoll;

    /**
     * Der Tag, an dem zuletzt VOLLSTAENDIG geraeumt wurde - nur im Arbeitsspeicher.
     *
     * <p>Keine Spalte in der Datenbank, dieselbe Entscheidung wie beim {@link Mietlauf}:
     * Ein Merker, der einen Neustart ueberlebt, waere eine zweite Wahrheit ueber "heute
     * erledigt". Hier ist er sogar harmloser als dort, denn ein zweiter Lauf am selben
     * Tag loescht nichts Falsches - er findet nur nichts mehr. Ein verlorener Merker
     * kostet also eine Abfrage, keine Zeile.
     *
     * <p>Gesetzt wird er nur nach einem stoerungsfreien Lauf, der ALLES erwischt hat.
     */
    private LocalDate zuletzt;

    Blockraeumung(Blockbuchablage ablage, Blockbuchwerte werte,
                  Wirtschaftsstand wirtschaft, Clock uhr, Logger protokoll) {
        if (ablage == null || werte == null || wirtschaft == null || uhr == null
                || protokoll == null) {
            throw new IllegalArgumentException("Ablage, Werte, Wirtschaftsstand, Uhr und"
                    + " Protokoll sind Pflicht");
        }
        this.ablage = ablage;
        this.werte = werte;
        this.wirtschaft = wirtschaft;
        this.uhr = uhr;
        this.protokoll = protokoll;
    }

    /**
     * Der Taktschlag: raeumt, solange heute noch etwas zu raeumen ist.
     *
     * <p>Die Tagesabfrage steht VOR dem Loeschen, und das ist keine Feinheit: Der Takt
     * schlaegt je nach Einstellung alle zehn bis sechshundert Sekunden, und an fast allen
     * Schlaegen ist nichts zu tun. Ohne die Schranke ginge bei jedem ein {@code DELETE}
     * ueber das Netz, das nichts findet - auf der groessten Tabelle des Projekts.
     */
    Ergebnis imTakt() {
        LocalDate heute = heute();
        if (heute.equals(zuletzt)) {
            return Ergebnis.nichtsZuTun();
        }
        Ergebnis ergebnis = raeume(heute);
        // NUR wenn alles weg ist UND nichts gestoert war. Sonst kaeme der naechste
        // Versuch erst morgen, und bei einem Rueckstand von Wochen holte die Frist nie
        // auf - siehe den Kopf dieser Klasse.
        if (!ergebnis.gestoert() && !ergebnis.mehrDa()) {
            zuletzt = heute;
        }
        return ergebnis;
    }

    /**
     * EIN Happen - fuer den Takt und fuer die Probe.
     *
     * @param heute der Tag, von dem aus die Frist zurueckgerechnet wird
     */
    Ergebnis raeume(LocalDate heute) {
        Instant grenze = grenzeFuer(heute);
        int happen = werte.jeAufraeumen();
        try {
            int weg = ablage.raeumeAuf(grenze, happen);
            // "Der Happen war voll" heisst "es ist vermutlich noch mehr da". Vermutlich
            // und nicht sicher: Traf der Happen die Zahl genau, ist der naechste Schlag
            // einer, der nichts findet - und der kostet eine Abfrage, nicht eine Zeile.
            // Die andere Annahme waere die teure: Sie liesse den Rest bis morgen liegen.
            boolean mehrDa = weg >= happen;
            if (weg > 0) {
                protokoll.info("Blockbuch aufgeraeumt: " + weg + " Zeilen vor "
                        + grenze + " geloescht (Frist " + werte.fristTage() + " Tage)."
                        + (mehrDa ? " Der Happen war voll - der naechste Takt raeumt"
                                + " weiter." : " Damit ist alles Faellige weg."));
            }
            return new Ergebnis(weg, mehrDa, false);
        } catch (RuntimeException fehler) {
            // Der Lauf wird wiederholt, es ist also nichts endgueltig verloren. Darum
            // eine WARNUNG und keine schwere Meldung: Ein Protokoll, das einen Tag
            // spaeter aufraeumt, ist kein Schaden - anders als eine verlorene Tagesmiete.
            protokoll.log(Level.WARNING, "Das Blockbuch konnte nicht aufgeraeumt werden -"
                    + " der naechste Takt versucht es erneut. Bis dahin waechst die"
                    + " Tabelle weiter.", fehler);
            return new Ergebnis(0, true, true);
        }
    }

    /**
     * Was vor diesem Zeitpunkt liegt, darf weg.
     *
     * <p>Eine eigene Methode, damit die Rechnung ohne Datenbank geprueft werden kann -
     * sie ist die ganze Entscheidung dieser Klasse.
     */
    Instant grenzeFuer(LocalDate heute) {
        return Tagesgrenze.tagesbeginn(heute.minusDays(werte.fristTage()),
                wirtschaft.werte().zeit().zone());
    }

    private LocalDate heute() {
        return Tagesgrenze.tagVon(uhr.instant(), wirtschaft.werte().zeit().zone());
    }
}
