package de.freimarken.paper;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.freimarken.kern.Stadtplan;
import java.util.ArrayList;
import java.util.List;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Die Einstellungen der gerechneten Stadt. */
@DisplayName("Die Einstellungen der Stadt Altfurt")
class StadtlisteTest {

    /**
     * Die ausgelieferte Kante Altfurts.
     *
     * <p>{@code welten.altfurt.kante: 800}, also x und z von -400 bis 400. Die
     * Proben geben sie HEREIN und holen sie nicht aus dem Leser: Sonst pruefte
     * jede Probe nur, dass eine Zahl sich selbst gleicht.
     */
    private static final int KANTE = 800;

    /**
     * Der Zeilenumbruch als Konstante.
     *
     * <p>Nicht als Escape in der Zeichenkette: Eine Probe, die YAML-Text
     * enthaelt, wird noch oft nachbearbeitet, und ein Werkzeug, das Backslashes
     * halbiert, macht daraus stillschweigend echte Zeilenumbrueche im
     * Quelltext - das uebersetzt dann nicht mehr.
     */
    private static final String B_N = String.valueOf((char) 10);

    @Test
    @DisplayName("Ohne Abschnitt entsteht die Stadt mit zwei Quartieren")
    void ohneAbschnitt() {
        List<String> klagen = new ArrayList<>();

        Stadtliste ohne = Stadtliste.ausKonfiguration(new YamlConfiguration(), KANTE, klagen);

        assertEquals(Stadtliste.abWerk(), ohne, "das Werk gilt");
        assertTrue(ohne.bauen(), "ab Werk wird gebaut");
        assertEquals(2, ohne.quartiere(), "zwei Quartiere");
        assertEquals(List.of(), klagen, "ein fehlender Abschnitt ist keine Klage");
        assertEquals(Stadtliste.abWerk(), Stadtliste.ausKonfiguration(null, KANTE, klagen),
                "ohne Datei");
    }

    @Test
    @DisplayName("Der Schalter wirkt - ohne ihn gibt es keinen Plan")
    void derSchalter() {
        List<String> klagen = new ArrayList<>();
        YamlConfiguration datei = new YamlConfiguration();
        datei.set(Stadtliste.PFAD + "." + Stadtliste.SCHLUESSEL_BAUEN, false);

        Stadtliste aus = Stadtliste.ausKonfiguration(datei, KANTE, klagen);

        assertTrue(!aus.bauen(), "abgeschaltet");
        assertNull(aus.plan(), "ohne Schalter kein Plan - und damit kein Block");
        assertTrue(aus.bericht().contains("abgeschaltet"), aus.bericht());
        assertEquals(List.of(), klagen, "das Abschalten ist keine Klage");

        // UND MIT Schalter gibt es einen Plan, der zu der Zahl passt.
        datei.set(Stadtliste.PFAD + "." + Stadtliste.SCHLUESSEL_BAUEN, true);
        datei.set(Stadtliste.PFAD + "." + Stadtliste.SCHLUESSEL_QUARTIERE, 3);
        Stadtliste an = Stadtliste.ausKonfiguration(datei, KANTE, klagen);
        assertNotNull(an.plan(), "mit Schalter ein Plan");
        assertEquals(3, an.plan().quartiere(), "und die eingestellte Zahl kommt an");
    }

    @Test
    @DisplayName("Eine Quartierszahl jenseits der Weltgrenze wird geklagt")
    void jenseitsDerGrenze() {
        List<String> klagen = new ArrayList<>();
        YamlConfiguration datei = new YamlConfiguration();
        datei.set(Stadtliste.PFAD + "." + Stadtliste.SCHLUESSEL_QUARTIERE,
                Stadtliste.HOECHSTENS_QUARTIERE + 1);

        Stadtliste zuGross = Stadtliste.ausKonfiguration(datei, KANTE, klagen);

        assertEquals(1, klagen.size(), klagen.toString());
        assertTrue(klagen.get(0).contains("quartiere"), klagen.get(0));
        assertEquals(Stadtliste.QUARTIERE_AB_WERK, zuGross.quartiere(),
                "es gilt das Werk");

        // ZWEI GRENZEN, DIE NICHT MEHR ZUSAMMENFALLEN [24.11.2026]: Bis heute passte
        // die Arbeitsgrenze gerade noch in die Welt - neun Quartiere bis x 374 bei einer
        // Kante bis 400 -, und das sah nach Absicht aus. Es war ein Zufall: Die
        // Stadtmitte lag bei 72/13, die Stadt also schief im Weltfenster. Seit sie auf
        // 0/0 liegt, ist sie symmetrisch, und es passen ZWOELF Quartiere.
        //
        // Dass die beiden zusammenfielen, hat verdeckt, dass es zwei sind. Jetzt sagt
        // HOECHSTENS_QUARTIERE "zu viel Arbeit" und passtInDieWelt "kein Platz", und
        // beide werden einzeln geprueft.
        assertTrue(Stadtliste.passtInDieWelt(Stadtliste.HOECHSTENS_QUARTIERE, KANTE),
                "die Arbeitsgrenze passt in die ausgelieferte Welt");
        int platzgrenze = Stadtliste.HOECHSTENS_QUARTIERE;
        while (Stadtliste.passtInDieWelt(platzgrenze + 1, KANTE)) {
            platzgrenze++;
            assertTrue(platzgrenze < 100, "die Platzgrenze muss irgendwo liegen");
        }
        assertEquals(12, platzgrenze, "in kante 800 passen zwoelf Quartiere");
        assertTrue(platzgrenze > Stadtliste.HOECHSTENS_QUARTIERE,
                "die Arbeit ist die engere der beiden Grenzen: " + platzgrenze
                        + " passen, " + Stadtliste.HOECHSTENS_QUARTIERE
                        + " werden gebaut");
        assertFalse(Stadtliste.passtInDieWelt(platzgrenze + 1, KANTE),
                "eine mehr passt nicht");
        Stadtplan gerade = new Stadtplan(Stadtliste.HOECHSTENS_QUARTIERE);
        assertTrue(gerade.umriss().bisX() < KANTE / 2, "x " + gerade.umriss().bisX());
        // UND SIE IST VIEL ARBEIT, gemessen an Altfurt und nicht an einer runden Zahl:
        // Neun Quartiere sind ein Vielfaches von zwei. Eine feste 3000 stand hier bis
        // zum 24.11.2026 und wurde rot, als die Hoefe zwischen die Haeuser kamen -
        // obwohl die Aussage ("das ist zu viel Handarbeit") unberuehrt war.
        assertTrue(gerade.haeuser().size() > 10 * Stadtplan.altfurt().haeuser().size(),
                "und sie ist viel Arbeit: " + gerade.haeuser().size() + " Haeuser gegen "
                        + Stadtplan.altfurt().haeuser().size() + " in Altfurt");
    }

    @Test
    @DisplayName("Null Quartiere werden geklagt, nicht gebaut")
    void nullQuartiere() {
        List<String> klagen = new ArrayList<>();
        YamlConfiguration datei = new YamlConfiguration();
        datei.set(Stadtliste.PFAD + "." + Stadtliste.SCHLUESSEL_QUARTIERE, 0);

        Stadtliste keins = Stadtliste.ausKonfiguration(datei, KANTE, klagen);

        assertEquals(1, klagen.size(), klagen.toString());
        assertEquals(Stadtliste.QUARTIERE_AB_WERK, keins.quartiere());
        // Und von Hand laesst sich die Null auch nicht bauen.
        assertThrows(IllegalArgumentException.class,
                () -> new Stadtliste(true, 0, List.of()));
    }

    @Test
    @DisplayName("Ein Schluessel ohne Wert faellt nicht durch die Grenzpruefung")
    void schluesselOhneWert() {
        List<String> klagen = new ArrayList<>();
        // ECHTER TEXT und kein set(.., null): Jenes LOESCHT den Schluessel, und dann
        // pruefte diese Probe etwas anderes als das, was sie behauptet.
        YamlConfiguration datei = new YamlConfiguration();
        assertDoesNotThrow(() -> datei.loadFromString("bauten:" + B_N
                + "  stadt:" + B_N + "    quartiere:" + B_N
                + "    bauen: true" + B_N));
        // GEMESSEN AN BUKKIT [23.11.2026]: Der Schluessel kommt NICHT an. Nicht
        // als null, nicht als 0 - contains() sagt schon false. Wer sich darauf
        // verlaesst, dass ein eingetragener Schluessel beim Leser ankommt, hat
        // einen Eintrag, der stumm verschwindet.
        assertFalse(datei.contains(Stadtliste.PFAD + "."
                        + Stadtliste.SCHLUESSEL_QUARTIERE),
                "ein Schluessel ohne Wert verschwindet beim Lesen");
        assertNull(datei.getString(Stadtliste.PFAD + "."
                + Stadtliste.SCHLUESSEL_QUARTIERE), "und ist auch kein Text");
        // DER NACHBARSCHLUESSEL kommt dagegen an - es ist also wirklich der
        // fehlende WERT und nicht ein kaputter Abschnitt.
        assertTrue(datei.contains(Stadtliste.PFAD + "."
                        + Stadtliste.SCHLUESSEL_BAUEN),
                "bauen: true steht im selben Abschnitt");

        Stadtliste liste = Stadtliste.ausKonfiguration(datei, KANTE, klagen);

        assertEquals(Stadtliste.QUARTIERE_AB_WERK, liste.quartiere(),
                "es gilt das Werk - und zwar ohne Klage, denn ein verschwundener"
                + " Schluessel ist von einem fehlenden nicht zu unterscheiden");
        assertEquals(List.of(), klagen, "keine Klage");
        assertTrue(liste.bauen(), "und gebaut wird trotzdem");
    }

    @Test
    @DisplayName("Der Bericht nennt Quartiere, Mauer, Kante und Hauszahl")
    void derBericht() {
        String bericht = Stadtliste.abWerk().bericht();

        Stadtplan plan = new Stadtplan(Stadtliste.QUARTIERE_AB_WERK);
        assertTrue(bericht.contains(String.valueOf(plan.mauerVon())),
                "die Mauer fehlt: " + bericht);
        assertTrue(bericht.contains(String.valueOf(plan.haeuser().size())),
                "die Hauszahl fehlt: " + bericht);
        assertTrue(bericht.contains(String.valueOf(2 * plan.aussenRing() + 1)),
                "die Kante fehlt: " + bericht);
    }

    @Test
    @DisplayName("Eine kleine Welt macht eine kleine Stadt - und sagt es je Quartier")
    void kleineWelt() {
        List<String> klagen = new ArrayList<>();
        YamlConfiguration datei = new YamlConfiguration();
        datei.set(Stadtliste.PFAD + "." + Stadtliste.SCHLUESSEL_QUARTIERE, 4);

        // GERECHNET: Vier Quartiere reichen bis x 169. In eine zu enge Welt muessen
        // Quartiere wegfallen, bis es passt.
        //
        // DREI FASSUNGEN AN EINEM TAG [24.11.2026]: 239 vor den Mauertuermen, dann 241
        // mit ihnen (sie treten TURM_VOR Bloecke vor die Mauerflucht, und der Umriss ist
        // die Flaeche, die abgegangen wird), und jetzt 169 - denn die Stadtmitte ist von
        // 72/13 auf 0/0 gewandert. Die Zahl ist damit zum ersten Mal die reine WEITE der
        // Stadt und nicht Weite plus Mittenversatz.
        assertEquals(169, new Stadtplan(4).umriss().bisX(), "vier Quartiere reichen weit");
        // UND SIE IST SYMMETRISCH, seit die Mitte der Nullpunkt ist - vorher war sie es
        // nicht, und daran haette man den Versatz sehen koennen.
        assertEquals(-169, new Stadtplan(4).umriss().vonX(),
                "und genauso weit nach Westen");
        // DIE WELTKANTE WIRD GERECHNET und nicht geraten [24.11.2026]: Hier stand 400,
        // und das passte, solange vier Quartiere bis x 241 reichten. Seit die Stadtmitte
        // auf 0/0 liegt, reichen sie nur bis 169 - und in eine Welt mit Kante 400 passen
        // sie bequem. Die Probe prueefte damit nichts mehr. Aus der gemessenen Weite
        // abgeleitet, bleibt sie richtig, wie weit die Stadt auch einmal reicht.
        int weite = new Stadtplan(4).umriss().bisX();
        int enge = 2 * weite - 20;
        assertFalse(Stadtliste.passtInDieWelt(4, enge),
                "in diese Welt passen vier Quartiere wirklich nicht: Kante " + enge
                        + " gegen Weite " + weite);
        Stadtliste klein = Stadtliste.ausKonfiguration(datei, enge, klagen);

        assertTrue(klein.quartiere() < 4, "es wurde verkleinert: " + klein.quartiere());
        assertTrue(Stadtliste.passtInDieWelt(klein.quartiere(), enge),
                "und was bleibt, passt");
        assertFalse(Stadtliste.passtInDieWelt(klein.quartiere() + 1, enge),
                "eines mehr passte nicht - es wurde nicht zu viel weggenommen");
        assertFalse(klagen.isEmpty(), "und es wurde gesagt");
        assertTrue(klagen.get(0).contains("Weltgrenze"), klagen.get(0));
    }

    @Test
    @DisplayName("Passt nicht einmal ein Quartier, entsteht keine Stadt")
    void winzigeWelt() {
        List<String> klagen = new ArrayList<>();
        // Ein Quartier reicht bis x 88. Eine Welt mit Kante 64 reicht von -32 bis 32.
        // Vorher 158, dann 160 mit den Mauertuermen; die 88 ist die reine Weite, seit
        // die Stadtmitte am 24.11.2026 auf 0/0 gewandert ist.
        assertEquals(88, new Stadtplan(1).umriss().bisX(), "ein Quartier reicht bis 88");

        Stadtliste nichts = Stadtliste.ausKonfiguration(new YamlConfiguration(), 64,
                klagen);

        assertFalse(nichts.bauen(), "es wird nicht gebaut");
        assertNull(nichts.plan(), "und es gibt keinen Plan");
        assertFalse(klagen.isEmpty(), "und es wurde gesagt");
        assertTrue(klagen.get(klagen.size() - 1).contains("keine Stadt"),
                klagen.toString());
    }

    @Test
    @DisplayName("Ohne bekannte Kante wird die Weltgrenze nicht geprueft")
    void ohneKante() {
        List<String> klagen = new ArrayList<>();
        // Eine fehlende Welt beklagt der Leser der Bauten schon - zwei Klagen ueber
        // dieselbe Sache liest niemand.
        Stadtliste ohne = Stadtliste.ausKonfiguration(new YamlConfiguration(), null,
                klagen);

        assertEquals(Stadtliste.QUARTIERE_AB_WERK, ohne.quartiere());
        assertTrue(ohne.bauen(), "gebaut wird trotzdem");
        assertEquals(List.of(), klagen, "und nicht geklagt");
        assertTrue(Stadtliste.passtInDieWelt(Stadtliste.HOECHSTENS_QUARTIERE, null),
                "ohne Kante passt alles");
        assertTrue(Stadtliste.passtInDieWelt(1, 0), "eine Kante 0 ist keine Grenze");
    }

    @Test
    @DisplayName("Keine ganze Zahl wird geklagt - nicht stumm zum Werk")
    void keineGanzeZahl() {
        List<String> klagen = new ArrayList<>();
        YamlConfiguration datei = new YamlConfiguration();
        datei.set(Stadtliste.PFAD + "." + Stadtliste.SCHLUESSEL_QUARTIERE, "zwei");

        Stadtliste wort = Stadtliste.ausKonfiguration(datei, KANTE, klagen);

        assertEquals(Stadtliste.QUARTIERE_AB_WERK, wort.quartiere(), "es gilt das Werk");
        assertEquals(1, klagen.size(), klagen.toString());
        assertTrue(klagen.get(0).contains("keine ganze"), klagen.get(0));
        assertTrue(klagen.get(0).contains("zwei"), klagen.get(0));

        // UND EINE KOMMAZAHL EBENSO: getInt schnitte sie stumm ab.
        klagen.clear();
        datei.set(Stadtliste.PFAD + "." + Stadtliste.SCHLUESSEL_QUARTIERE, 2.9);
        Stadtliste komma = Stadtliste.ausKonfiguration(datei, KANTE, klagen);
        assertEquals(Stadtliste.QUARTIERE_AB_WERK, komma.quartiere());
        assertEquals(1, klagen.size(), klagen.toString());
        assertTrue(klagen.get(0).contains("2.9"), klagen.get(0));
    }


    @Test
    @DisplayName("Die Vorlagennamen werden gelesen - und Unsinn darin geklagt")
    void dieVorlagennamen() {
        List<String> klagen = new ArrayList<>();
        YamlConfiguration datei = new YamlConfiguration();
        datei.set(Stadtliste.PFAD + "." + Stadtliste.SCHLUESSEL_VORLAGEN,
                List.of("haus-schmal", "haus_breit", "haus-schmal", "../geheim",
                        "Haus.nbt"));

        Stadtliste liste = Stadtliste.ausKonfiguration(datei, KANTE, klagen);

        // DIE BRAUCHBAREN bleiben, in der Reihenfolge der Datei.
        assertEquals(List.of("haus-schmal", "haus_breit"), liste.vorlagen(),
                "zwei brauchbare Namen");
        // DREI KLAGEN: die Doppelung und die zwei unmoeglichen Namen. Ein Name mit
        // Schraegstrich oder Punkt fuehrte aus dem Ordner heraus.
        String alle = String.join(" | ", klagen);
        assertEquals(3, klagen.size(), alle);
        assertTrue(alle.contains("zweimal"), alle);
        assertTrue(alle.contains("../geheim"), alle);
        assertTrue(alle.contains("Haus.nbt"), alle);

        // OHNE EINTRAG wird gerechnet, und das ist keine Klage.
        List<String> stille = new ArrayList<>();
        Stadtliste ohne = Stadtliste.ausKonfiguration(new YamlConfiguration(), KANTE,
                stille);
        assertEquals(List.of(), ohne.vorlagen());
        assertEquals(List.of(), stille);
        assertTrue(ohne.bericht().contains("keine"), ohne.bericht());
    }

    @Test
    @DisplayName("Der Plan bekommt die Masse von aussen - der Leser kennt nur Namen")
    void masseKommenVonAussen() {
        List<String> klagen = new ArrayList<>();
        YamlConfiguration datei = new YamlConfiguration();
        datei.set(Stadtliste.PFAD + "." + Stadtliste.SCHLUESSEL_VORLAGEN,
                List.of("haus-schmal"));
        Stadtliste liste = Stadtliste.ausKonfiguration(datei, KANTE, klagen);

        // OHNE MASSE wird gerechnet, obwohl ein Name steht - denn der Leser kann die
        // Datei nicht messen. Das Plugin holt die Masse und gibt sie herein.
        assertEquals(List.of(), liste.plan().vorlagen(),
                "ohne Masse keine Vorlage im Plan");
        var masse = List.of(new de.freimarken.kern.Vorlage("haus-schmal", 7, 14, 9));
        assertEquals(masse, liste.plan(masse).vorlagen(), "mit Massen schon");
        // Und die Stadt ist dieselbe Stadt - nur die Haeuser sind andere.
        assertEquals(liste.plan().aussenRing(), liste.plan(masse).aussenRing());
    }

}
