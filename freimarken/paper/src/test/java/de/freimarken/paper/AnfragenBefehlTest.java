package de.freimarken.paper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Die vier Teleportbefehle GETIPPT - und der Unterschied zwischen {@code /tpa} und
 * {@code /tpahere}.
 *
 * <p><b>Hier liegt die schaerfste Luecke, die ein Durchlauf schliesst.</b> Die vier
 * Befehle kommen aus EINEM Bauplan: {@code tpa} und {@code tpahere} rufen dieselbe private
 * {@code frageBefehl}-Methode, nur mit einer anderen {@code Richtung}. Im Quelltext sind
 * das zwei Zeilen, die sich in einem Wort unterscheiden - und keine Zahltafel sieht, ob
 * sie vertauscht sind. Vertauscht liest der Angefragte "will zu dir kommen", waehrend der
 * Fragende ihn zu sich holt; beide richten sich nach der falschen Richtung, und der
 * Teleport geht verkehrt.
 *
 * <p>{@code GrenzeTest.SENDEPAARE_IN_ANFRAGENBEFEHLE} nagelt fest, WELCHE Methode an WEN
 * sendet - aber nicht, mit welcher Richtung sie gerufen wird. Probe 43d haelt genau das.
 *
 * <p><b>Die Anfrage selbst laeuft hier ganz durch</b>, bis zu den Zeilen bei beiden
 * Spielern: Der Erfolgsweg von {@code frage} fasst {@code Reise} nicht an. Nur
 * {@code /tpaccept} MIT offener Anfrage reist wirklich und bleibt darum aussen vor - die
 * Absage ohne Anfrage ist geprueft.
 */
class AnfragenBefehlTest {

    private static final String FRAGER = "Cara";
    private static final String ANGEFRAGTER = "Dora";

    private Befehlsprobe baum;
    private Spielerprobe frager;
    private Spielerprobe angefragter;

    @BeforeAll
    static void serverStellen() {
        Serverprobe.einmalSetzen();
    }

    @BeforeEach
    void vorher() {
        // Geteilte Anwesenheit, darum vorher aufraeumen - sonst haengt das Ergebnis an
        // der Reihenfolge der Probenklassen.
        Serverprobe.niemandMehrDa();
        frager = new Spielerprobe(FRAGER, Spielerprobe.kennungAus(101),
                AnfragenBefehle.RECHT_TPA);
        angefragter = new Spielerprobe(ANGEFRAGTER, Spielerprobe.kennungAus(102),
                AnfragenBefehle.RECHT_TPA);
        Serverprobe.anmelden(frager, FRAGER);
        Serverprobe.anmelden(angefragter, ANGEFRAGTER);

        baum = neuerBaum();
    }

    /**
     * Ein frischer Baum mit frischen Anfragen.
     *
     * <p>Probe 43d braucht ihn zweimal: Eine gestellte Anfrage steht, und der
     * zweite Befehl bekaeme sonst die Absage "steht noch" statt der zweiten
     * Richtung. Mit zwei VERSCHIEDENEN Spielern waere es einfacher gewesen - und
     * genau das war der Fehler, den ein Eingriff aufgedeckt hat: Dann
     * unterscheiden sich die zwei Antworten schon durch den NAMEN des Fragenden,
     * und die Richtung darf vertauscht sein, ohne dass die Probe rot wird.
     * Gemessen am 06.11.2026: Der Tausch lief gruen.
     */
    private Befehlsprobe neuerBaum() {
        AnfragenBefehle befehle = new AnfragenBefehle(
                new Anfragen(Duration.ofMinutes(1)), null,
                Sprachprobe.spracheAusDemJar());
        return new Befehlsprobe(List.of(befehle.tpa(), befehle.tpahere(),
                befehle.tpaccept(), befehle.tpdeny()));
    }

    @Test
    @DisplayName("43 - ein Name, den niemand traegt, bekommt eine eigene Absage")
    void unbekannterNameBekommtAbsage() throws CommandSyntaxException {
        assertEquals(1, baum.tippe("tpa Niemand", frager));

        assertFalse(frager.chatBild().isEmpty(), "der Fragende erfaehrt es");
        assertEquals(List.of(), angefragter.chatBild(),
                "und sonst niemand - ein Name, den es nicht gibt, darf keinen"
                        + " Unbeteiligten erreichen");
    }

    @Test
    @DisplayName("43b - sich selbst zu fragen hat eine eigene Antwort")
    void sichSelbstFragen() throws CommandSyntaxException {
        assertEquals(1, baum.tippe("tpa " + FRAGER, frager));

        assertFalse(frager.chatBild().isEmpty());
        // Und sie ist eine ANDERE als die beim unbekannten Namen: "du bist schon bei dir"
        // gegen "ist nicht da". Ein vertauschter Zweig liesse beide gleich.
        Spielerprobe zweiter = new Spielerprobe(FRAGER, Spielerprobe.kennungAus(103),
                AnfragenBefehle.RECHT_TPA);
        Serverprobe.anmelden(zweiter, "Niemand2");
        baum.tippe("tpa Niemand3", zweiter);
        assertNotEquals(frager.chatBild(), zweiter.chatBild(),
                "sich selbst zu fragen ist nicht dasselbe wie einen Abwesenden zu fragen");
    }

    @Test
    @DisplayName("43c - tpaccept und tpdeny ohne offene Anfrage sagen es, jedes anders")
    void ohneAnfrageSagenBeideEigenesAb() throws CommandSyntaxException {
        assertEquals(1, baum.tippe("tpaccept", frager));
        List<String> annehmen = frager.chatBild();
        frager.vergessen();

        assertEquals(1, baum.tippe("tpdeny", frager));
        List<String> ablehnen = frager.chatBild();

        assertFalse(annehmen.isEmpty(), "ohne Anfrage antwortet /tpaccept");
        assertFalse(ablehnen.isEmpty(), "und /tpdeny auch");
        assertEquals(annehmen, ablehnen,
                "beide sagen dasselbe: es liegt keine Anfrage vor. Werden daraus zwei"
                        + " verschiedene Saetze, gehoert diese Probe geaendert - und dann"
                        + " mit Absicht.");
    }

    @Test
    @DisplayName("43d - /tpa und /tpahere sagen dem Angefragten VERSCHIEDENE Richtungen")
    void tpaUndTpahereUnterscheidenDieRichtung() throws CommandSyntaxException {
        // DERSELBE Fragende, DASSELBE Ziel, nur der Befehl ist ein anderer - so
        // ist die Richtung das EINZIGE, was sich unterscheiden kann. Mit zwei
        // Spielerpaaren unterschieden sich die Antworten schon durch den Namen,
        // und der Tausch der zwei Richtungen lief gruen durch (gemessen).
        baum.tippe("tpa " + ANGEFRAGTER, frager);
        List<String> zuMir = List.copyOf(angefragter.chatBild());
        List<String> fragerBeiZuMir = List.copyOf(frager.chatBild());
        angefragter.vergessen();
        frager.vergessen();

        // Frischer Baum, damit die eben gestellte Anfrage nicht im Weg steht.
        baum = neuerBaum();
        baum.tippe("tpahere " + ANGEFRAGTER, frager);
        List<String> hierher = angefragter.chatBild();

        assertFalse(zuMir.isEmpty(), "der Angefragte erfaehrt von der Anfrage");
        assertFalse(hierher.isEmpty());
        assertFalse(fragerBeiZuMir.isEmpty(), "und der Fragende bekommt eine"
                + " Bestaetigung");
        // DER KERN: ein Wort Unterschied im Quelltext. Vertauscht liest der
        // Angefragte das Gegenteil von dem, was geschieht, und richtet sich danach.
        assertNotEquals(zuMir, hierher,
                "/tpa heisst \"ich komme zu dir\", /tpahere heisst \"komm zu mir\"."
                        + " Vertauscht richtet sich der Angefragte nach der falschen"
                        + " Richtung, und keine Zahltafel sieht es.");
    }

    @Test
    @DisplayName("43e - /tpa ohne Namen fuehrt nirgendwohin")
    void tpaOhneNamenFuehrtNirgendwohin() {
        // An der Wurzel steht KEIN executes: /tpa allein ist unvollstaendig. Ohne diese
        // Probe koennte jemand eines hinzufuegen, das still nichts tut.
        assertThrows(CommandSyntaxException.class, () -> baum.tippe("tpa", frager));
        assertEquals(List.of(), frager.chatBild());
    }

    @Test
    @DisplayName("43f - ohne freimarken.tpa gibt es alle vier Befehle nicht")
    void ohneRechtGibtEsKeinenDerVier() {
        Spielerprobe ohneRecht = new Spielerprobe("Gustav",
                Spielerprobe.kennungAus(106));
        Serverprobe.anmelden(ohneRecht, "Gustav");

        for (String befehl : List.of("tpa " + ANGEFRAGTER, "tpahere " + ANGEFRAGTER,
                "tpaccept", "tpdeny")) {
            assertThrows(CommandSyntaxException.class,
                    () -> baum.tippe(befehl, ohneRecht),
                    befehl + " haengt am selben Recht wie die anderen drei");
        }
        assertEquals(List.of(), ohneRecht.chatBild());
    }

    @Test
    @DisplayName("43g - die Konsole kann keinen Teleport erbitten")
    void dieKonsoleFragtNicht() {
        for (String befehl : List.of("tpa " + ANGEFRAGTER, "tpahere " + ANGEFRAGTER,
                "tpaccept", "tpdeny")) {
            assertThrows(CommandSyntaxException.class,
                    () -> baum.tippeAufDerKonsole(befehl), befehl);
        }
    }
}
