package de.freimarken.kern;

import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Die Flaeche, die eine Gruppe aus Rasterzellen bedeckt - und ihre Naht.
 *
 * <p>Die Naht ist alles, was sich in der Welt aendert, wenn aus mehreren Feldern ein
 * Grundstueck wird. Sie hat zwei Teile:
 *
 * <ul>
 *   <li>Die FUGEN - jede Weltkoordinate im Inneren der Gruppe, auf der kein Bauland
 *       liegt: das Band aus Mauer und Weg zwischen zwei Feldern und die Kreuzung, wo
 *       vier Felder zusammenstossen. Daraus wird Bauland.
 *   <li>Die MAUERSTUECKE an den Enden der Fugen, die den Mauerring wieder schliessen,
 *       den das Fuellen aufgerissen hat.
 * </ul>
 *
 * <p>Die Klasse steht im Kern und nicht im Paper-Teil, obwohl nur der Paper-Teil Bloecke
 * setzt. Grund: Die Frage "welche Koordinaten gehoeren dazu" ist reine Geometrie und damit
 * ohne Server pruefbar - und sie ist die Frage, bei der ein Fehler teuer ist. Wer sie im
 * Blockschreiber beantwortet, kann sie nur noch auf einem laufenden Server pruefen, und
 * dort faellt eine Zelle zu viel erst auf, wenn der Nachbar sich beschwert.
 *
 * <p>Entschieden wird hier nichts: {@link Rasterplan#imInneren} ist die eine Regel, und
 * diese Klasse laeuft nur die Koordinaten ab, bei denen sie zu fragen ist.
 */
public final class Gruppenflaeche {

    /** Arbeit an einer Stelle der Naht. */
    @FunctionalInterface
    public interface AnDerNaht {

        /**
         * @param weltX      Weltkoordinate, nicht Parzellennummer
         * @param weltZ      Weltkoordinate, nicht Parzellennummer
         * @param ohneGruppe was hier liegt, wenn DIESE Gruppe nicht besteht: der
         *                   Rasterplan - oder, wenn eine der anderen Flaechen die Stelle
         *                   braucht, deren Anspruch. Kann deshalb auch Bauland sein.
         * @param mitGruppe  was hier liegen muss, solange die Gruppe besteht: Bauland in
         *                   den Fugen, Mauer am Ende einer geschluckten Strasse
         */
        void an(int weltX, int weltZ, Rasterplan.Feld ohneGruppe, Rasterplan.Feld mitGruppe);
    }

    private final int vonX;
    private final int vonZ;
    private final int bisX;
    private final int bisZ;
    private final Set<Long> zellen;

    private Gruppenflaeche(int vonX, int vonZ, int bisX, int bisZ, Set<Long> zellen) {
        this.vonX = vonX;
        this.vonZ = vonZ;
        this.bisX = bisX;
        this.bisZ = bisZ;
        this.zellen = zellen;
    }

    /**
     * Die Flaeche, die diese Felder bedecken.
     *
     * @param felder die Felder einer Gruppe, mindestens eines - alle auf dem Raster
     * @throws IllegalArgumentException wenn keine Felder uebergeben wurden. Laut und nicht
     *                                 als leere Flaeche: Eine Gruppe ohne Felder gibt es
     *                                 nicht, und "nichts zu tun" waere beim Zurueckbau
     *                                 genau das falsche Ergebnis - der Weg bliebe
     *                                 Bauland, ohne dass es jemand merkt.
     */
    public static Gruppenflaeche aus(Collection<Parzelle> felder) {
        if (felder.isEmpty()) {
            throw new IllegalArgumentException("Eine Gruppenflaeche ohne Felder gibt es nicht");
        }
        int vonX = Integer.MAX_VALUE;
        int vonZ = Integer.MAX_VALUE;
        int bisX = Integer.MIN_VALUE;
        int bisZ = Integer.MIN_VALUE;
        Set<Long> zellen = new HashSet<>();
        for (Parzelle feld : felder) {
            vonX = Math.min(vonX, feld.x());
            vonZ = Math.min(vonZ, feld.z());
            bisX = Math.max(bisX, feld.x());
            bisZ = Math.max(bisZ, feld.z());
            zellen.add(schluessel(feld.x(), feld.z()));
        }
        return new Gruppenflaeche(vonX, vonZ, bisX, bisZ, Set.copyOf(zellen));
    }

    /** Wie viele Rasterzellen die Gruppe hat. */
    public int felder() {
        return zellen.size();
    }

    /** Ob diese Rasterzelle zur Gruppe gehoert. Passt auf {@link Rasterplan.ZellenPruefung}. */
    public boolean gehoert(int zelleX, int zelleZ) {
        return zellen.contains(schluessel(zelleX, zelleZ));
    }

    /**
     * Ob die Gruppe ohne dieses Feld noch in einem Stueck waere.
     *
     * <p>Gebraucht beim Herausloesen eines einzelnen Feldes. Wer das mittlere Feld einer
     * Reihe herausloest, laesst zwei Haelften zurueck, die sich nicht mehr beruehren - und
     * dann waere die Fuge zwischen ihnen Bauland ohne Verbindung zum eigenen Grundstueck,
     * mitten auf einer Strasse, die wieder oeffentlich sein muesste.
     *
     * <p>Zusammenhang ueber die Kante und nicht ueber die Ecke: Zwei Felder, die sich nur
     * an einer Ecke beruehren, haben keine Fuge zwischen sich. Dort liegt die Kreuzung, und
     * die gehoert allen - in der Welt waeren das zwei Grundstuecke.
     *
     * <p>Ein Feld, das gar nicht zur Gruppe gehoert, aendert nichts: Dann ist die Antwort
     * dieselbe wie fuer die ganze Flaeche.
     */
    public boolean bleibtGanzOhne(int zelleX, int zelleZ) {
        Set<Long> rest = new HashSet<>(zellen);
        rest.remove(schluessel(zelleX, zelleZ));
        return haengtZusammen(rest);
    }

    /** Ob diese Zellen ueber Kanten alle miteinander verbunden sind. */
    private static boolean haengtZusammen(Set<Long> zellen) {
        if (zellen.size() <= 1) {
            return true;
        }
        Deque<Long> offen = new ArrayDeque<>();
        Set<Long> erreicht = new HashSet<>();
        long start = zellen.iterator().next();
        offen.add(start);
        erreicht.add(start);
        while (!offen.isEmpty()) {
            long zelle = offen.poll();
            // Dieselbe Maske wie beim Packen, nur umgekehrt: Das Vorzeichen von x steckt
            // im oberen Wort, deshalb schiebt der Pfeil arithmetisch.
            int x = (int) (zelle >> 32);
            int z = (int) zelle;
            for (int[] schritt : new int[][] {{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
                long nachbar = schluessel(x + schritt[0], z + schritt[1]);
                if (zellen.contains(nachbar) && erreicht.add(nachbar)) {
                    offen.add(nachbar);
                }
            }
        }
        return erreicht.size() == zellen.size();
    }

    /**
     * Ruft die Arbeit fuer jede Stelle der Naht auf - einmal je Weltkoordinate.
     *
     * <p>Warum die Mauerstuecke sein muessen: Im Rasterplan gewinnt der Weg immer, damit
     * sich Wege kreuzen koennen statt an jeder Kreuzung vor einer Mauer zu enden. Am Ende
     * einer Fuge trifft die Querstrasse auf die Mauerreihe der Parzellen - eine Achse sagt
     * Weg, also liegt dort Pflaster. Vor dem Zusammenlegen ist das richtig, die Strasse
     * laeuft hindurch. Danach laeuft sie gegen Bauland, und ohne das Mauerstueck haette
     * das neue Grundstueck als einziges in der Stadt ein Loch in der Mauer, so breit wie
     * die Strasse.
     *
     * <p>Beides in EINEM Durchlauf, und beide Zustaende an derselben Stelle: Das Fuellen
     * schreibt {@code mitGruppe}, der Zurueckbau {@code ohneGruppe}. Zwei getrennte
     * Durchlaeufe koennten sich um eine Koordinate unterscheiden, und dann bliebe nach dem
     * Auftrennen ein Mauerstueck quer auf einer oeffentlichen Strasse stehen, das niemand
     * mehr entfernen darf.
     *
     * <p>Abgelaufen wird der Kasten um die Felder, einen Block ueber das Bauland hinaus -
     * genau so weit und keinen weiter reicht das Mauerstueck. Bauland wird sofort
     * uebersprungen, deshalb laedt ein Zusammenlegen keinen Chunk, in dem nichts zu tun
     * ist: Bei vier Feldern im Quadrat sind das rund 19.000 Rechenschritte, aber nur rund
     * 1.900 Koordinaten, die ueberhaupt einen Block anfassen.
     */
    public void ueberDieNaht(Rasterplan plan, AnDerNaht arbeit) {
        ueberDieNaht(plan, List.of(), arbeit);
    }

    /**
     * Dieselbe Naht, aber ohne die Stellen, die eine fruehere Gruppe schon genauso belegt
     * hat.
     *
     * <p>Das ist der Fall "ein Feld an eine bestehende Gruppe anhaengen". Die alte Fuge ist
     * seit dem ersten Zusammenlegen Bauland, der Spieler hat dort gebaut - und sie liegt
     * mitten im Kasten der neuen, groesseren Gruppe. Wer sie noch einmal fuellt, setzt sein
     * Erdgeschoss auf Gras zurueck und loescht die Schicht darueber: ohne Warnung, ohne
     * dass etwas herausfaellt, 448 Stellen je Band.
     *
     * <p>Beantworten kann das nur die Ablage, nicht die Welt. Gras auf einer Fuge sieht
     * gleich aus, ob es das Fuellen hingelegt hat oder der Spieler. Verglichen wird deshalb
     * nicht der Block, sondern die Zuordnung: Lag hier schon Bauland, weil dieselbe
     * Koordinate schon zur alten Gruppe gehoerte, bleibt sie unberuehrt.
     *
     * <p>Mehrere Flaechen und nicht eine, weil an der Stelle zwei Gruppen aneinander
     * stossen koennen. Ihre Vereinigung waere genau die neue Gruppe - dann gaebe es nie
     * etwas zu tun.
     *
     * <p>Ein Mauerstueck, das zur Fuge wird, kommt trotzdem: Beim vierten Feld wird aus dem
     * Bandende eine Kreuzung. Verglichen wird das Ergebnis, nicht die Teilnahme.
     *
     * <p>Dieselbe Methode traegt beide Richtungen. Beim Fuellen sind die anderen Flaechen
     * die Gruppen VOR dem Zusammenlegen, und geschrieben wird {@code mitGruppe}. Beim
     * Zurueckbauen sind es die Gruppen, die DANACH noch bestehen, und geschrieben wird
     * {@code ohneGruppe} - das, was sie an dieser Stelle brauchen.
     *
     * @param andere die anderen Flaechen, je Gruppe eine
     */
    public void ueberDieNaht(Rasterplan plan, Collection<Gruppenflaeche> andere,
                             AnDerNaht arbeit) {
        int vonWeltX = plan.ursprungX(vonX) - 1;
        int vonWeltZ = plan.ursprungZ(vonZ) - 1;
        int bisWeltX = plan.ursprungX(bisX) + plan.parzellenKante();
        int bisWeltZ = plan.ursprungZ(bisZ) + plan.parzellenKante();
        for (int x = vonWeltX; x <= bisWeltX; x++) {
            for (int z = vonWeltZ; z <= bisWeltZ; z++) {
                Rasterplan.Feld feld = plan.feldBei(x, z);
                Rasterplan.Feld mitGruppe = nahtstelle(plan, x, z, feld);
                if (mitGruppe == null) {
                    continue;
                }
                Rasterplan.Feld ohneGruppe = beiDenAnderen(plan, andere, x, z, feld);
                // Gleich heisst: hier ist nichts zu tun, die Stelle liegt schon richtig.
                if (ohneGruppe != mitGruppe) {
                    arbeit.an(x, z, ohneGruppe, mitGruppe);
                }
            }
        }
    }

    /**
     * Was hier liegen muss, solange diese Gruppe besteht - oder {@code null}, wenn diese
     * Gruppe an dieser Stelle nichts zu sagen hat.
     *
     * <p>Die eine Entscheidung, zweimal gebraucht: fuer diese Flaeche und fuer jede
     * frueheres. Sie zweimal zu formulieren waere die Art Fehler, die erst auffaellt, wenn
     * ein Spieler sein Haus verloren hat.
     *
     * <p>{@code null} und kein {@link java.util.Optional}: Diese Methode laeuft bei vier
     * Feldern rund 19.000 Mal je Durchlauf, und die Antwort "hier nichts" ist der haeufige
     * Fall.
     */
    private Rasterplan.Feld nahtstelle(Rasterplan plan, int x, int z, Rasterplan.Feld feld) {
        if (feld == Rasterplan.Feld.PARZELLE) {
            return null;
        }
        if (plan.imInneren(x, z, this::gehoert)) {
            return Rasterplan.Feld.PARZELLE;
        }
        if (feld == Rasterplan.Feld.WEG && stoesstAufDieGruppe(plan, x, z)) {
            return Rasterplan.Feld.MAUER;
        }
        return null;
    }

    /**
     * Was an dieser Stelle liegt, wenn diese Gruppe nicht besteht.
     *
     * <p>Nicht einfach der Rasterplan: Steht daneben noch eine Gruppe, hat die ihren
     * eigenen Anspruch auf die Stelle. Das ist der Unterschied, an dem der erste Anlauf
     * scheiterte - er verglich nur, ob eine andere Flaeche DASSELBE wollte, und schrieb
     * sonst stur den Rasterplan zurueck. Am Arm einer Kreuzung ist das falsch: Dort war
     * Bauland, und danach muss MAUER liegen, nicht Pflaster. Das Loch war fuenf Bloecke
     * breit, und der Eigentuemer durfte es nicht einmal selbst zumauern - die Stelle
     * gehoerte ihm nach dem Loesen nicht mehr.
     *
     * <p>Bauland gewinnt vor Mauer: Beansprucht eine Flaeche die Stelle als ihr Inneres,
     * gehoert sie ihr, auch wenn eine zweite dort eine Mauer braeuchte.
     */
    private static Rasterplan.Feld beiDenAnderen(Rasterplan plan,
                                                 Collection<Gruppenflaeche> andere,
                                                 int x, int z, Rasterplan.Feld feld) {
        Rasterplan.Feld anspruch = null;
        for (Gruppenflaeche flaeche : andere) {
            Rasterplan.Feld dort = flaeche.nahtstelle(plan, x, z, feld);
            if (dort == Rasterplan.Feld.PARZELLE) {
                return dort;
            }
            if (dort != null) {
                anspruch = dort;
            }
        }
        return anspruch != null ? anspruch : feld;
    }

    /**
     * Ob dieses Wegfeld unmittelbar an die gefuellte Flaeche stoesst - also eine Strasse
     * ist, die nach dem Zusammenlegen gegen Bauland laeuft.
     *
     * <p>Nur ueber die Kante, nicht ueber die Ecke: Ueber Ecke liegt am Ende einer Fuge
     * immer schon Mauer, weil dort beide Achsen Mauer sagen.
     *
     * <p>Ein Wegfeld grenzt nie an Bauland - dazwischen liegt im Rasterplan immer die
     * Mauerreihe. {@link Rasterplan#imInneren} kann hier also nur Fugen treffen, nie eine
     * Parzelle der Gruppe.
     */
    private boolean stoesstAufDieGruppe(Rasterplan plan, int x, int z) {
        return plan.imInneren(x - 1, z, this::gehoert)
                || plan.imInneren(x + 1, z, this::gehoert)
                || plan.imInneren(x, z - 1, this::gehoert)
                || plan.imInneren(x, z + 1, this::gehoert);
    }

    /**
     * Zwei Nummern in einer Zahl - ohne ein Objekt je Zelle.
     *
     * <p>Die Maske ist Pflicht: Ohne sie wuerde das Vorzeichen eines negativen z die
     * oberen 32 Bit fuellen, und die Zelle -1/-1 waere dieselbe wie 0/-1. Die Marktstadt
     * hat negative Nummern von -21 an, das traefe also die Haelfte der Stadt.
     */
    private static long schluessel(int zelleX, int zelleZ) {
        return ((long) zelleX << 32) | (zelleZ & 0xFFFFFFFFL);
    }
}
