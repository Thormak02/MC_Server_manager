package de.freimarken.paper;

import de.freimarken.kern.Besitzbuch;
import de.freimarken.kern.Parzelle;
import de.freimarken.kern.Rasterplan;
import java.util.Optional;
import java.util.UUID;

/**
 * Besitz von Hand: {@code /freimarken besitz uebertragen|freigeben} - die REGEL dahinter.
 *
 * <p><b>Warum es diesen Befehl gibt</b> [22.11.2026]: Bis heute gab es im ganzen Plugin
 * keinen Weg, eine Parzelle auf einen anderen Spieler zu uebertragen oder fremden Besitz
 * freizugeben. Der einzige Besitzerwechsel war {@code Grundstuecksbuch.kaufe}, und der
 * braucht ein stehendes Angebot und Geld des Kaeufers. Fuer den Ernstfall - zwei Spieler
 * tauschen, einer verlaesst den Server, ein Fehler des Programms hat den Falschen
 * eingetragen - blieb nur die Datenbank von Hand. Eine Zeile, die dort entsteht, laesst
 * den Zwischenspeicher mit dem alten Eigentuemer zurueck, und bis zum naechsten Neustart
 * baut der Alte weiter.
 *
 * <p><b>Ohne Text, ohne Bukkit, ohne Protokoll</b>, genau wie {@link Betreibergeld}: So
 * laeuft jede Lage in einer Probe ohne Server. Was der Betreiber liest, baut
 * {@link FreimarkenPlugin} aus dem {@link Ausgang}.
 *
 * <p><b>Die Pruefungen baut diese Klasse nicht nach.</b> Sie ruft
 * {@code Besitzbuch.betreiberUebertragen} und {@code betreiberFreigeben}, und die setzen
 * den heutigen Eigentuemer als Handelnden ein - damit gilt dieselbe Hinderniskette wie
 * fuer {@code /parzelle aufgeben}, einschliesslich der Verweigerung auf einem einzelnen
 * Feld einer Gruppe. Eine eigene Kette waere die zweite Wahrheit darueber, was an einer
 * Stelle geht.
 *
 * <p><b>Die Umrechnung von Parzellennummer auf Weltkoordinate steht HIER</b> und nicht
 * beim Aufrufer: Der Betreiber tippt die Nummer, die {@code /parzelle info} und das
 * Dashboard ihm zeigen, das Buch rechnet in Weltkoordinaten. Haette der Aufrufer die
 * Umrechnung, waere sie bei zwei Zweigen zweimal da.
 *
 * <p>Hier stand einmal, {@code mitteX} sei dabei richtig und {@code ursprungX} falsch.
 * Das ist nachgerechnet NICHT so [22.11.2026]: Der Ursprung einer Parzelle liegt immer im
 * ersten Block der Parzelle selbst, {@code floorMod(n*71, 71)} ist 0 und damit kleiner
 * als die Kante 64, also {@code Feld.PARZELLE} und {@code floorDiv(n*71, 71) == n}. Fuer
 * {@code zustaendigerBesitz} sind beide Koordinaten dieselbe Rasterzelle, und keine Probe
 * kann sie ueber das Ergebnis unterscheiden. Eine Zusage, die nie zu treffen ist, ist
 * keine - darum steht sie nicht mehr da. {@code mitteX} bleibt trotzdem die bessere
 * Wahl, weil sie vom Rand am weitesten entfernt liegt.
 */
final class Betreiberbesitz {

    /**
     * Was aus dem Versuch geworden ist - je Lage eine, und keine Lage ohne eine.
     *
     * <p>Eine eigene Aufzaehlung und nicht der {@code Bescheid} des Buchs: Jener hat
     * neunzehn Werte, von denen hier sechs erreichbar sind, und ein Betreiber, der
     * {@code NACHBAR_FREMD} zu lesen bekommt, sucht den Fehler an der falschen Stelle.
     */
    enum Ausgang {

        /** Uebertragen - der neue Eigentuemer steht in der Zeile. */
        UEBERTRAGEN,

        /** Freigegeben - die Stelle ist wieder Bauland. */
        FREIGEGEBEN,

        /**
         * Diesen Namen kennt der Server nicht.
         *
         * <p>Derselbe Riegel wie bei {@link Betreibergeld}: {@code getOfflinePlayer} auf
         * einen unbekannten Namen erfindet eine frische Kennung. Uebertragen auf die
         * waere Besitzverlust - die Parzelle gehoerte danach niemandem, der sich anmelden
         * kann, und kein Befehl holt sie zurueck.
         */
        UNBEKANNTER_SPIELER,

        /** Hier ist gar keine Parzelle - Weg, Mauer oder vor der Stadt. */
        KEIN_BESITZ,

        /**
         * Bauland in der Stadt, aber noch niemandem vergeben. Nicht KEIN_BESITZ.
         *
         * <p>Es gibt BEWUSST kein AUSSERHALB daneben [Fund 22.11.2026]: Das Buch
         * unterscheidet an dieser Stelle nur zwischen "auf Bauland innerhalb der
         * Grenze" und allem anderen, und jenseits der Stadt ist die Antwort darum
         * KEIN_BESITZ. Ein AUSSERHALB waere ein Ausgang, den nie etwas erreicht - eine
         * Zusage ohne Bau. Die Stadtgrenze haelt ausserdem schon das Argument des
         * Befehls ab, das nur Nummern von {@code grenze.von()} bis {@code bis()} nimmt.
         */
        NOCH_FREI,

        /**
         * Die Stelle gehoert zu einer Gruppe - einzeln geht hier nichts.
         *
         * <p>Nur beim FREIGEBEN: Dort wird geloescht, und ein einzelnes Feld
         * durchgeschrieben loeschte das ganze Grundstueck - dieselbe Verweigerung, die
         * {@code /parzelle aufgeben} ausspricht. Beim UEBERTRAGEN wechselt dagegen das
         * ganze Grundstueck, denn ein Grundstueck halb zu uebertragen gibt es nicht.
         */
        TEIL_EINER_GRUPPE,

        /** Der genannte Spieler ist schon Eigentuemer - es gibt nichts zu uebertragen. */
        SCHON_EIGENTUEMER
    }

    /**
     * Der Ausgang samt den Zahlen, die die Antwort nennt.
     *
     * @param spieler    der genannte Name; beim Freigeben der bisherige Eigentuemer, oder
     *                   {@code null}, wenn es keinen gab
     * @param haeltJetzt wie viele Parzellen der Empfaenger nach der Tat in dieser Welt
     *                   haelt; 0 bei einem Ausgang ohne Wechsel. Die Zahl steht in der
     *                   Antwort, weil die Rangobergrenze hier NICHT geprueft wird - der
     *                   Grund steht bei {@code Besitzbuch.betreiberUebertragen}
     */
    record Ergebnis(Ausgang ausgang, String spieler, String vorher, int parzelleX,
                    int parzelleZ, int haeltJetzt) {
    }

    /** Die Zahl ist nicht lesbar - die Tat ist trotzdem geschehen. */
    static final int ZAHL_UNBEKANNT = -1;

    /**
     * Woher die Kennung zu einem getippten Namen kommt.
     *
     * <p>Dieselbe Naht wie bei {@link Betreibergeld.Namenssuche}, und bewusst eine
     * eigene: Zwei Klassen, die sich eine Schnittstelle teilen, haengen aneinander,
     * sobald eine davon ein zweites Verfahren braucht.
     */
    interface Namenssuche {

        /** Die Kennung, oder {@code null}, wenn der Server den Namen nicht kennt. */
        UUID kennungVon(String name);
    }

    private Betreiberbesitz() {
    }

    /**
     * Uebertraegt - oder sagt, warum nicht.
     *
     * <p>Die Reihenfolge der Fragen ist die Zusage: ERST der Name, dann die Stelle. Wer
     * sich im Namen vertippt UND die falsche Parzelle nennt, soll den Namen lesen - jener
     * ist der Fehler, der sonst zu Besitzverlust fuehrt.
     */
    static Ergebnis uebertrage(Besitzbuch buch, Namenssuche suche, String welt,
                               int parzelleX, int parzelleZ, String anName) {
        if (buch == null || suche == null) {
            throw new IllegalArgumentException("Ohne Besitzbuch und Namenssuche gibt es"
                    + " keine Uebertragung");
        }
        UUID anWen = suche.kennungVon(anName);
        Rasterplan plan = buch.plan();
        int weltX = plan.mitteX(parzelleX);
        int weltZ = plan.mitteZ(parzelleZ);

        // DER VORBESITZER WIRD VOR DER TAT GELESEN [Fund 22.11.2026]. Danach steht er in
        // der Zeile nicht mehr, und ohne ihn merkt ein Betreiber, der sich in der Nummer
        // vertippt hat, am Satz nicht einmal, dass er jemandem etwas weggenommen hat -
        // niemand kann den Griff dann zurueckdrehen. Auch beim unbekannten Namen wird er
        // genannt: Dann steht im Protokoll, WELCHE Stelle gemeint war.
        String bisher = buch.zustaendigerBesitz(welt, weltX, weltZ)
                .map(p -> p.eigentuemer().toString()).orElse(null);

        if (anWen == null) {
            return new Ergebnis(Ausgang.UNBEKANNTER_SPIELER, anName, bisher, parzelleX,
                    parzelleZ, 0);
        }
        Besitzbuch.Entscheid entscheid = buch.betreiberUebertragen(welt, weltX, weltZ,
                anWen);
        if (entscheid.bescheid() != Besitzbuch.Bescheid.ERTEILT) {
            return new Ergebnis(uebersetze(entscheid.bescheid()), anName, bisher,
                    parzelleX, parzelleZ, 0);
        }

        // AB HIER IST DER WECHSEL VOLLZOGEN. Die Zahl ist nur noch Beiwerk, und ein
        // Fehlschlag beim Zaehlen darf den Erfolg nicht umwerfen: Vorher stand hier ein
        // ungeschuetztes parzellenVon, und brach die Verbindung in diesem Augenblick weg,
        // las der Betreiber "es ist NICHTS geaendert" - obwohl die Parzelle dauerhaft
        // gewechselt hatte.
        int haelt;
        try {
            haelt = buch.parzellenVon(welt, anWen).size();
        } catch (RuntimeException beimZaehlen) {
            haelt = ZAHL_UNBEKANNT;
        }
        return new Ergebnis(Ausgang.UEBERTRAGEN, anName, bisher, parzelleX, parzelleZ,
                haelt);
    }

    /**
     * Gibt frei - oder sagt, warum nicht.
     *
     * <p>Der bisherige Eigentuemer wird VOR der Tat gelesen: Danach gibt es ihn in der
     * Zeile nicht mehr, und ohne seinen Namen stuende im Protokoll nur, dass irgendwo
     * etwas freigegeben wurde.
     */
    static Ergebnis gibFrei(Besitzbuch buch, Namenssuche suche, String welt,
                            int parzelleX, int parzelleZ) {
        if (buch == null) {
            throw new IllegalArgumentException("Ohne Besitzbuch gibt es keine Freigabe");
        }
        Rasterplan plan = buch.plan();
        int weltX = plan.mitteX(parzelleX);
        int weltZ = plan.mitteZ(parzelleZ);
        Optional<Parzelle> vorher = buch.zustaendigerBesitz(welt, weltX, weltZ);
        String bisher = vorher.map(p -> p.eigentuemer().toString()).orElse(null);

        Besitzbuch.Entscheid entscheid = buch.betreiberFreigeben(welt, weltX, weltZ);
        if (entscheid.bescheid() != Besitzbuch.Bescheid.ERTEILT) {
            return new Ergebnis(uebersetze(entscheid.bescheid()), bisher, bisher,
                    parzelleX, parzelleZ, 0);
        }
        // Beim Freigeben ist "spieler" derselbe wie "vorher": Es gibt keinen Empfaenger.
        // Beide Felder zu fuellen ist Absicht - so braucht die Antwort keine
        // Fallunterscheidung, und ein Leser, der nur "vorher" liest, bekommt in beiden
        // Zweigen dasselbe.
        return new Ergebnis(Ausgang.FREIGEGEBEN, bisher, bisher, parzelleX, parzelleZ,
                0);
    }

    /**
     * Der Bescheid des Buchs in einen Ausgang, den ein Betreiber lesen kann.
     *
     * <p>Die nicht erreichbaren Bescheide fallen LAUT durch: Ein {@code default}, der sie
     * in {@code KEIN_BESITZ} legte, machte aus einer geaenderten Regel im Buch eine
     * falsche Antwort - und die liest niemand als Fehler.
     */
    private static Ausgang uebersetze(Besitzbuch.Bescheid bescheid) {
        return switch (bescheid) {
            case KEINE_PARZELLE -> Ausgang.KEIN_BESITZ;
            case NOCH_FREI -> Ausgang.NOCH_FREI;
            case TEIL_EINER_GRUPPE -> Ausgang.TEIL_EINER_GRUPPE;
            case SCHON_VERGEBEN -> Ausgang.SCHON_EIGENTUEMER;
            default -> throw new IllegalStateException(
                    "Besitzbuch gab einen Bescheid zurueck, den dieser Zweig nicht"
                            + " kennt: " + bescheid + ". Die Regel im Buch hat sich"
                            + " geaendert - hier fehlt der passende Ausgang.");
        };
    }
}
