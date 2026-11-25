package de.freimarken.kern;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Wer wem welche Teamrolle geben und nehmen darf (Plan 12c, Schritt 16a Teil A).
 *
 * <p><b>Die tragende Regel dieser Klasse: Eine Rolle vergibt nur, wer jedes ihrer Rechte
 * selbst hat.</b> Nicht "wer Verwalter ist", nicht "wer kein Helfer ist" - sondern wer
 * die Vollmacht, die er weitergibt, selbst besitzt.
 *
 * <p>Der Grund ist ein Angriff, der sich sonst von selbst ergibt: Ein Moderator mit
 * {@code freimarken.team.vergeben} braeuchte nur einen Komplizen zum Verwalter zu machen
 * und haette ueber ihn {@code freimarken.besitz} - also die Vollmacht, fremden Besitz
 * umzuschreiben, die er selbst nicht hat. Ein Riegel "nur Verwalter duerfen vergeben"
 * fasste das nicht, denn welche Rolle wie viel darf, steht in der {@code config.yml} und
 * kann morgen anders sein. Der Riegel haengt darum an den RECHTEN und nicht am Namen -
 * dieselbe Linie wie {@code Rangzahl}, das die Zahl aus dem Recht liest und nicht aus
 * einer Liste im Programm.
 *
 * <p><b>Derselbe Riegel gilt beim NEHMEN</b>, und das ist weniger offensichtlich: Ohne
 * ihn nimmt ein Helfer dem Verwalter seine Rolle. Er gewinnt dabei nichts, aber er
 * entwaffnet den, der ihn aufhalten koennte - und ein Server, auf dem die untere Rolle
 * die obere abbauen kann, hat keine Rangordnung, sondern eine Reihenfolge.
 *
 * <p><b>Sich selbst eine Rolle geben ist erlaubt</b>, und zwar notwendig: Der Riegel
 * sorgt ohnehin dafuer, dass dabei kein Recht entsteht, das der Vergebende nicht schon
 * hat. Ein zusaetzliches Verbot waere ein zweiter Riegel fuer denselben Fall - und zwei
 * Riegel fuer einen Fall machen sich gegenseitig unpruefbar: Nimmt man den einen heraus,
 * bleibt die Probe gruen.
 *
 * <p><b>Die Rechte kommen als MENGE herein</b> und nicht als Spieler. So laeuft jede
 * Rechtelage ohne Server durch eine Probe - dieselbe Bauart wie {@code Rechtefrage} im
 * Plugin, nur eine Schicht tiefer.
 */
public final class Teambuch {

    /** Was aus einem Versuch wurde. */
    public enum Bescheid {
        /** Die Rolle war neu und ist jetzt vergeben. */
        VERGEBEN,
        /** Er hatte sie schon - nichts ist geschehen, auch die Vergabezeit bleibt. */
        HATTE_SCHON,
        /** Die Rolle ist genommen. */
        GENOMMEN,
        /** Er hatte sie nicht - nichts ist geschehen. */
        HATTE_NICHT,
        /** Dieser Server kennt keine Rolle dieses Namens. */
        UNBEKANNTE_ROLLE,
        /**
         * Dem Vergebenden fehlen Rechte, die die Rolle gibt.
         *
         * <p>Welche, steht in {@link Entscheid#fehlende()} - und zwar vollstaendig und
         * nicht nur das erste: Wer eine Rolle einrichtet, soll in einer Antwort lesen,
         * was ihm fehlt, und nicht fuenfmal fragen muessen.
         */
        ZU_WENIG_RECHTE,
        /** Dieser Server hat ueberhaupt keine Rollen eingetragen. */
        KEIN_TEAM,
        /** Die Ablage hat geworfen - nichts ist passiert. */
        GESTOERT
    }

    /**
     * Das Ergebnis eines Versuchs.
     *
     * @param bescheid was wurde
     * @param rolle    die Rolle, falls der Server sie kennt
     * @param fehlende bei {@link Bescheid#ZU_WENIG_RECHTE} die fehlenden Rechte MIT
     *                 Vorsilbe, sonst leer
     */
    public record Entscheid(Bescheid bescheid, Optional<Teamwerte.Rolle> rolle,
                            List<String> fehlende) {

        public Entscheid {
            if (bescheid == null) {
                throw new IllegalArgumentException("Ein Entscheid ohne Bescheid");
            }
            if (rolle == null) {
                throw new IllegalArgumentException("Die Rolle darf leer sein, aber nicht"
                        + " fehlen");
            }
            if (fehlende == null) {
                throw new IllegalArgumentException("Die Liste der fehlenden Rechte darf"
                        + " leer sein, aber nicht fehlen");
            }
            if (bescheid == Bescheid.ZU_WENIG_RECHTE && fehlende.isEmpty()) {
                throw new IllegalArgumentException("ZU_WENIG_RECHTE ohne ein einziges"
                        + " fehlendes Recht: Dann saehe die Absage aus wie eine Absage"
                        + " ohne Grund, und der Betreiber suchte an der falschen Stelle");
            }
            fehlende = List.copyOf(fehlende);
        }

        static Entscheid von(Bescheid bescheid, Teamwerte.Rolle rolle) {
            return new Entscheid(bescheid, Optional.ofNullable(rolle), List.of());
        }

        static Entscheid ohneRolle(Bescheid bescheid) {
            return new Entscheid(bescheid, Optional.empty(), List.of());
        }

        /** Ob sich dabei etwas geaendert hat - das entscheidet ueber das Protokoll. */
        public boolean geaendert() {
            return bescheid == Bescheid.VERGEBEN || bescheid == Bescheid.GENOMMEN;
        }
    }

    private final Teamablage ablage;

    /**
     * Die Werte - als Lieferant und nicht als Wert.
     *
     * <p>Die {@code config.yml} laesst sich im Betrieb neu laden, und ein hier
     * festgehaltenes Exemplar waere nach dem ersten Neuladen die alte Datei. Dieselbe
     * Bauart wie bei {@code Lotteriebuch}.
     */
    private final java.util.function.Supplier<Teamwerte> werte;

    public Teambuch(Teamablage ablage, java.util.function.Supplier<Teamwerte> werte) {
        if (ablage == null || werte == null) {
            throw new IllegalArgumentException("Das Teambuch braucht die Ablage und die"
                    + " Werte");
        }
        this.ablage = ablage;
        this.werte = werte;
    }

    /** Die Rollen, die dieser Server kennt. */
    public Teamwerte werte() {
        Teamwerte gelesen = werte.get();
        return gelesen == null ? Teamwerte.VORGABE : gelesen;
    }

    /** Welche Rollen dieser Spieler hat - durchgereicht, damit der Aufrufer EINE Naht hat. */
    public Teamstand stand(UUID spieler) {
        return ablage.lies(spieler);
    }

    /**
     * Alle, die eine Rolle haben - durchgereicht, aus demselben Grund wie {@link #stand}.
     *
     * <p>Durchgereicht und NICHT gefiltert: Wer eine Rolle hat, die dieser Server nicht
     * mehr kennt, steht mit darin. Das ist Absicht - diese Liste ist die einzige Stelle,
     * an der ein Betreiber einen solchen Altbestand ueberhaupt zu sehen bekommt, und wer
     * ihn herausfiltert, versteckt genau den Eintrag, der aufgeraeumt werden muss. Welche
     * Rollen die Datei kennt, sagt {@link Teamstand#unbekannteRollen}, und der Aufrufer
     * schreibt es dahinter.
     *
     * <p>Wirft weiter, wenn die Ablage schweigt - anders als {@link #gib} und
     * {@link #nimm}, die daraus einen Bescheid machen. Der Unterschied ist der Zweck:
     * Jene haben eine Tat zu melden, die nicht geschehen ist, diese hat nur eine Liste
     * nicht. Ein leerer Bescheid hiesse hier "niemand ist im Team", und das ist eine
     * falsche Auskunft - der Aufrufer faengt den Wurf und sagt, dass es gestoert ist.
     */
    public java.util.List<Teamstand> alle() {
        return ablage.alle();
    }

    /**
     * Gibt einem Spieler eine Rolle.
     *
     * @param wer      wer die Rolle bekommt
     * @param rolle    der Rollenname, beliebig geschrieben
     * @param wann     die Vergabezeit, UTC
     * @param vergeber wer vergibt; {@code null} heisst: die Konsole
     * @param rechte   die Rechte des Vergebenden, MIT Vorsilbe. Die Konsole gibt
     *                 {@code null} herein - sie darf alles, und zwar nicht aus Bequem-
     *                 lichkeit: Wer an der Konsole steht, hat Dateizugriff und koennte
     *                 die Rollen ohnehin direkt in die Datei schreiben. Ein Riegel, den
     *                 man umgehen kann, indem man eine Zeile tippt, ist kein Riegel,
     *                 sondern eine Unbequemlichkeit, die ein Betreiber umgeht
     */
    public Entscheid gib(UUID wer, String rolle, Instant wann, UUID vergeber,
                         Set<String> rechte) {
        Teamwerte gueltig = werte();
        if (!gueltig.greift()) {
            return Entscheid.ohneRolle(Bescheid.KEIN_TEAM);
        }
        Optional<Teamwerte.Rolle> gefunden = gueltig.rolle(rolle);
        if (gefunden.isEmpty()) {
            return Entscheid.ohneRolle(Bescheid.UNBEKANNTE_ROLLE);
        }
        Teamwerte.Rolle gewaehlt = gefunden.get();
        List<String> fehlende = fehlendeRechte(gewaehlt, rechte);
        if (!fehlende.isEmpty()) {
            return new Entscheid(Bescheid.ZU_WENIG_RECHTE, gefunden, fehlende);
        }
        try {
            boolean neu = ablage.gib(wer, gewaehlt.schluessel(), wann, vergeber);
            return Entscheid.von(neu ? Bescheid.VERGEBEN : Bescheid.HATTE_SCHON, gewaehlt);
        } catch (IllegalStateException gestoert) {
            // Durchgereicht als Bescheid und nicht als Wurf: Der Aufrufer ist ein Befehl,
            // und ein Wurf dort ist eine rote englische Zeile fuer eine Lage, die der
            // Betreiber versteht, sobald sie auf Deutsch dasteht.
            return Entscheid.von(Bescheid.GESTOERT, gewaehlt);
        }
    }

    /**
     * Nimmt einem Spieler eine Rolle.
     *
     * <p>Derselbe Rechteriegel wie beim Geben - siehe den Klassenkopf: Ohne ihn baut die
     * untere Rolle die obere ab.
     *
     * <p><b>Eine Rolle, die dieser Server nicht mehr kennt, laesst sich trotzdem
     * nehmen</b>, und das ist Absicht: Sie steht in der Ablage, weil der Betreiber ihre
     * Zeile aus der Datei genommen hat, und sie dort liegen zu lassen hiesse, dass die
     * Teamliste fuer immer einen Eintrag zeigt, den niemand loeschen kann. Geprueft wird
     * dann allerdings nichts - eine Rolle ohne Eintrag in der Datei hat keine Rechteliste,
     * mit der sich vergleichen liesse. Darum verlangt dieser Weg das Recht, das ohnehin
     * nur ein Verwalter hat, und sagt in der Antwort, dass er es war.
     */
    public Entscheid nimm(UUID wer, String rolle, Set<String> rechte) {
        Teamwerte gueltig = werte();
        Optional<Teamwerte.Rolle> gefunden = gueltig.rolle(rolle);
        if (gefunden.isPresent()) {
            List<String> fehlende = fehlendeRechte(gefunden.get(), rechte);
            if (!fehlende.isEmpty()) {
                return new Entscheid(Bescheid.ZU_WENIG_RECHTE, gefunden, fehlende);
            }
        }
        // Eine unbekannte Rolle kommt hier DURCH und nicht zurueck wie beim Geben: Sonst
        // bliebe sie fuer immer in der Ablage stehen. Welche Rolle gemeint ist, muss
        // trotzdem brauchbar sein - die Ablage klagt bei einer leeren.
        if (rolle == null || rolle.isBlank()) {
            return Entscheid.ohneRolle(Bescheid.UNBEKANNTE_ROLLE);
        }
        String name = gefunden.map(Teamwerte.Rolle::schluessel)
                .orElseGet(() -> rolle.trim().toLowerCase(java.util.Locale.ROOT));
        try {
            boolean weg = ablage.nimm(wer, name);
            return new Entscheid(weg ? Bescheid.GENOMMEN : Bescheid.HATTE_NICHT,
                    gefunden, List.of());
        } catch (IllegalStateException gestoert) {
            return new Entscheid(Bescheid.GESTOERT, gefunden, List.of());
        }
    }

    /**
     * Welche Rechte dieser Rolle dem Vergebenden fehlen.
     *
     * <p>{@code null} als Rechtemenge heisst DIE KONSOLE und gibt eine leere Liste -
     * siehe {@link #gib}. Eine LEERE Menge ist etwas anderes: ein Spieler ohne jedes
     * Recht, und dem fehlt alles.
     *
     * <p>Verglichen wird MIT Vorsilbe, weil die Rechte eines Spielers so heissen. Die
     * Vorsilbe haengt {@link Teamwerte#rechteFuer} an - an der einen Stelle, an der sie
     * auch beim Vergeben angehaengt wird.
     */
    private static List<String> fehlendeRechte(Teamwerte.Rolle rolle, Set<String> rechte) {
        if (rechte == null) {
            return List.of();
        }
        Set<String> gebraucht = new LinkedHashSet<>();
        for (String recht : rolle.rechte()) {
            gebraucht.add(Teamwerte.VORSILBE + recht);
        }
        List<String> fehlende = new ArrayList<>();
        for (String recht : gebraucht) {
            if (!rechte.contains(recht)) {
                fehlende.add(recht);
            }
        }
        return fehlende;
    }
}
