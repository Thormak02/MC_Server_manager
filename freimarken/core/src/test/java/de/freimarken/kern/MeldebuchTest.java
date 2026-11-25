package de.freimarken.kern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Wer melden darf, wer bearbeiten darf, und was der Melder davon erfaehrt.
 *
 * <p>Die wichtigste Probe dieser Datei ist {@link #antwortWartetAufDenMelder()}: Sie
 * haelt den Satz fest, mit dem der Plan das ganze Meldewesen begruendet - <i>"Der Melder
 * bekommt eine Rueckmeldung - sonst meldet beim zweiten Mal niemand mehr."</i> Gebaut ist
 * das als ZUSTAND und nicht als Chatzeile, denn eine Zeile an einen Abwesenden geht an
 * niemanden.
 *
 * <p>Die Werte kommen aus eigenen {@link Meldewerte} und nicht aus
 * {@link Meldewerte#VORGABE}: Was die Vorgabe einstellt, darf sich aendern, und eine
 * Probe, die daran haengt, bricht bei jeder Aenderung ein.
 */
@DisplayName("Melden und bearbeiten")
class MeldebuchTest {

    private static final Instant MITTAGS = Instant.parse("2026-11-25T12:00:00Z");

    private static final UUID ANNA = UUID.fromString(
            "00000000-0000-0000-0000-00000000000a");

    private static final UUID BERT = UUID.fromString(
            "00000000-0000-0000-0000-00000000000b");

    private static final UUID MODERATOR = UUID.fromString(
            "00000000-0000-0000-0000-00000000000c");

    private static final UUID ZWEITER = UUID.fromString(
            "00000000-0000-0000-0000-00000000000d");

    private static final String WELT = "altfurt";

    /**
     * Wie das Recht zum Bearbeiten heisst - die Probe setzt es SELBST.
     *
     * <p>Seit dem 25.11.2026 kennt der Kern den Namen nicht mehr: Er kommt beim Bau
     * herein, weil {@code GrenzeTest} 31al nur die Literale des Plugin-Moduls sieht.
     * Die Probe nennt darum einen eigenen, EXPLIZIT erfundenen Namen - damit haelt
     * sie fest, dass der Riegel am hereingegebenen Namen haengt und nicht an einer
     * Konstante, die zufaellig dieselbe ist.
     */
    private static final String RECHT = "probe.meldungen.bearbeiten";

    /** Die Rechte eines Moderators. */
    private static final Set<String> DARF = Set.of(RECHT);

    /** Die Rechte eines gewoehnlichen Spielers - leer, also NICHT die Konsole. */
    private static final Set<String> DARF_NICHT = Set.of();

    /** 60 Sekunden Abklingzeit, drei offene je Melder, zehn Zeilen. */
    private static final Meldewerte WERTE = new Meldewerte(true, 60L, 3, 10);

    private static Meldebuch buch(Meldeablage ablage) {
        return new Meldebuch(ablage, () -> WERTE, RECHT);
    }

    private static Meldebuch buch(Meldeablage ablage, Meldewerte werte) {
        return new Meldebuch(ablage, () -> werte, RECHT);
    }

    @Test
    @DisplayName("1 - eine Meldung wird angelegt und steht in der Ablage")
    void meldungWirdAngelegt() {
        Meldeablage ablage = new SpeicherMeldungen();

        Meldebuch.Entscheid entscheid = buch(ablage).melde(ANNA, BERT,
                "baut auf meiner Parzelle", WELT, 10, -60, -20, MITTAGS);

        assertEquals(Meldebuch.Bescheid.ANGELEGT, entscheid.bescheid());
        assertTrue(entscheid.geaendert(), "das gehoert ins Protokoll");
        Meldung eine = entscheid.meldung().orElseThrow();
        assertEquals(Meldestand.OFFEN, eine.stand());
        assertEquals(1, ablage.offeneVon(ANNA), "und sie steht wirklich in der Ablage");
    }

    @Test
    @DisplayName("2 - niemand meldet sich selbst, und es ist KEIN Wurf")
    void selbstmeldung() {
        Meldeablage ablage = new SpeicherMeldungen();

        Meldebuch.Entscheid entscheid = buch(ablage).melde(ANNA, ANNA, "mich selbst",
                WELT, 0, -60, 0, MITTAGS);

        // Ein Bescheid und kein Wurf: Der Konstruktor von Meldung wirft dafuer, und ein
        // Wurf im Befehl waere eine rote englische Zeile fuer einen Spieler, der sich
        // nur im Namen vertippt hat.
        assertEquals(Meldebuch.Bescheid.SELBST, entscheid.bescheid());
        assertEquals(0, ablage.offeneVon(ANNA));
    }

    @Test
    @DisplayName("3 - ein zu kurzer Grund wird abgewiesen, ohne Wurf")
    void grundUnbrauchbar() {
        Meldeablage ablage = new SpeicherMeldungen();

        assertEquals(Meldebuch.Bescheid.GRUND_UNBRAUCHBAR,
                buch(ablage).melde(ANNA, BERT, "ab", WELT, 0, -60, 0, MITTAGS)
                        .bescheid());
        // Und ein Abschnittszeichen: Der Melder soll in der Zeile des Moderators keine
        // Farben faelschen koennen.
        assertEquals(Meldebuch.Bescheid.GRUND_UNBRAUCHBAR,
                buch(ablage).melde(ANNA, BERT, "harmlos§cFEHLER", WELT, 0, -60, 0,
                        MITTAGS).bescheid());
        assertEquals(0, ablage.offeneVon(ANNA));
    }

    @Test
    @DisplayName("4 - DIE ABKLINGZEIT: die zweite Meldung in derselben Minute wartet")
    void abklingzeit() {
        Meldeablage ablage = new SpeicherMeldungen();
        Meldebuch buch = buch(ablage);
        assertEquals(Meldebuch.Bescheid.ANGELEGT, buch.melde(ANNA, BERT, "erster Vorfall",
                WELT, 0, -60, 0, MITTAGS).bescheid(), "die Vorbereitung");

        // 20 Sekunden spaeter, bei 60 Sekunden Abklingzeit.
        Meldebuch.Entscheid entscheid = buch.melde(ANNA, BERT, "zweiter Vorfall", WELT,
                0, -60, 0, MITTAGS.plusSeconds(20L));

        assertEquals(Meldebuch.Bescheid.ABKLINGZEIT, entscheid.bescheid());
        // Die RESTZEIT muss in der Antwort stehen: "warte noch" ohne Zahl ist eine
        // Absage, die den Spieler raten laesst, und er versucht es sofort wieder.
        assertEquals(40L, entscheid.wartezeit());
        assertEquals(1, ablage.offeneVon(ANNA), "nichts ist dazugekommen");
    }

    @Test
    @DisplayName("5 - nach Ablauf der Abklingzeit geht es wieder")
    void nachDerAbklingzeit() {
        Meldeablage ablage = new SpeicherMeldungen();
        Meldebuch buch = buch(ablage);
        buch.melde(ANNA, BERT, "erster Vorfall", WELT, 0, -60, 0, MITTAGS);

        // Die Gegenprobe zu 4: Ohne sie koennte die Abklingzeit IMMER greifen, und die
        // Probe darueber waere gruen.
        assertEquals(Meldebuch.Bescheid.ANGELEGT,
                buch.melde(ANNA, BERT, "zweiter Vorfall", WELT, 0, -60, 0,
                        MITTAGS.plusSeconds(60L)).bescheid());
        assertEquals(2, ablage.offeneVon(ANNA));
    }

    @Test
    @DisplayName("6 - eine Abklingzeit von 0 bremst nicht")
    void abklingzeitAus() {
        Meldeablage ablage = new SpeicherMeldungen();
        Meldebuch buch = buch(ablage, WERTE.mitAbklingzeit(0L));
        buch.melde(ANNA, BERT, "erster Vorfall", WELT, 0, -60, 0, MITTAGS);

        assertEquals(Meldebuch.Bescheid.ANGELEGT,
                buch.melde(ANNA, BERT, "zweiter Vorfall", WELT, 0, -60, 0, MITTAGS)
                        .bescheid(),
                "dieselbe Sekunde, und es geht - die Bremse ist dann allein die Zahl");
    }

    @Test
    @DisplayName("7 - eine Meldung IN DER ZUKUNFT verlangt die volle Wartezeit")
    void meldungAusDerZukunft() {
        Meldeablage ablage = new SpeicherMeldungen();
        Meldebuch buch = buch(ablage);
        buch.melde(ANNA, BERT, "aus der Zukunft", WELT, 0, -60, 0,
                MITTAGS.plusSeconds(600L));

        // Eine verstellte Uhr oder ein Probenwert. Die sichere Richtung ist, die volle
        // Zeit zu verlangen - sonst liesse sich die Bremse mit einer Zeitverstellung
        // aushebeln, und das ist genau der Weg, den ein Angreifer findet.
        Meldebuch.Entscheid entscheid = buch.melde(ANNA, BERT, "jetzt", WELT, 0, -60, 0,
                MITTAGS);
        assertEquals(Meldebuch.Bescheid.ABKLINGZEIT, entscheid.bescheid());
        assertEquals(60L, entscheid.wartezeit(), "die VOLLE Abklingzeit");
    }

    @Test
    @DisplayName("8 - DIE HOECHSTZAHL: die vierte offene Meldung wird verschoben")
    void hoechstzahlOffene() {
        Meldeablage ablage = new SpeicherMeldungen();
        Meldebuch buch = buch(ablage, WERTE.mitAbklingzeit(0L));
        for (int nummer = 0; nummer < 3; nummer++) {
            assertEquals(Meldebuch.Bescheid.ANGELEGT,
                    buch.melde(ANNA, UUID.randomUUID(), "Vorfall " + nummer, WELT,
                            0, -60, 0, MITTAGS).bescheid(), "die Vorbereitung");
        }

        Meldebuch.Entscheid entscheid = buch.melde(ANNA, BERT, "vierter Vorfall", WELT,
                0, -60, 0, MITTAGS);

        assertEquals(Meldebuch.Bescheid.ZU_VIELE_OFFENE, entscheid.bescheid());
        // Die GRENZE muss in der Antwort stehen, sonst weiss der Melder nicht, wann er
        // wieder darf.
        assertEquals(3, entscheid.grenze());
        assertEquals(3, ablage.offeneVon(ANNA));
    }

    @Test
    @DisplayName("9 - nach einer Bearbeitung geht die vierte Meldung durch")
    void nachBearbeitungWiederFrei() {
        Meldeablage ablage = new SpeicherMeldungen();
        Meldebuch buch = buch(ablage, WERTE.mitAbklingzeit(0L));
        long erste = 0L;
        for (int nummer = 0; nummer < 3; nummer++) {
            Meldung eine = buch.melde(ANNA, UUID.randomUUID(), "Vorfall " + nummer, WELT,
                    0, -60, 0, MITTAGS).meldung().orElseThrow();
            if (nummer == 0) {
                erste = eine.nummer();
            }
        }
        assertEquals(Meldebuch.Bescheid.ZU_VIELE_OFFENE, buch.melde(ANNA, BERT, "vierter",
                WELT, 0, -60, 0, MITTAGS).bescheid(), "die Vorbereitung");

        buch.erledige(erste, Meldestand.ABGESCHLOSSEN, MODERATOR, "erledigt", MITTAGS,
                DARF);

        // DAS IST DIE BOTSCHAFT DER ABSAGE: Die vierte Meldung wird nicht verweigert,
        // sondern verschoben. Sie geht, sobald eine der drei bearbeitet ist - und genau
        // das liest der Melder.
        assertEquals(Meldebuch.Bescheid.ANGELEGT,
                buch.melde(ANNA, BERT, "jetzt doch", WELT, 0, -60, 0, MITTAGS)
                        .bescheid());
    }

    @Test
    @DisplayName("10 - die Hoechstzahl zaehlt NUR die eigenen Meldungen")
    void hoechstzahlIstJeMelder() {
        Meldeablage ablage = new SpeicherMeldungen();
        Meldebuch buch = buch(ablage, WERTE.mitAbklingzeit(0L));
        for (int nummer = 0; nummer < 3; nummer++) {
            buch.melde(ANNA, UUID.randomUUID(), "annas Vorfall " + nummer, WELT,
                    0, -60, 0, MITTAGS);
        }

        // Sonst sperrte ein einziger Vielmelder das Meldewesen fuer alle - und das ist
        // der Angriff, der mit drei Meldungen auskommt.
        assertEquals(Meldebuch.Bescheid.ANGELEGT,
                buch.melde(BERT, ANNA, "berts Vorfall", WELT, 0, -60, 0, MITTAGS)
                        .bescheid());
    }

    @Test
    @DisplayName("11 - ist das Meldewesen aus, wird nichts angelegt")
    void meldewesenAus() {
        Meldeablage ablage = new SpeicherMeldungen();

        assertEquals(Meldebuch.Bescheid.AUS,
                buch(ablage, WERTE.mitAn(false))
                        .melde(ANNA, BERT, "Vorfall", WELT, 0, -60, 0, MITTAGS)
                        .bescheid());
        assertEquals(0, ablage.offeneVon(ANNA));
    }

    @Test
    @DisplayName("12 - DER RIEGEL: der zweite Moderator liest SCHON_GENOMMEN")
    void zweiterNehmerLiestSchonGenommen() {
        Meldeablage ablage = new SpeicherMeldungen();
        Meldebuch buch = buch(ablage);
        long nummer = buch.melde(ANNA, BERT, "Griefing", WELT, 0, -60, 0, MITTAGS)
                .meldung().orElseThrow().nummer();
        assertEquals(Meldebuch.Bescheid.GENOMMEN,
                buch.nimm(nummer, MODERATOR, MITTAGS, DARF).bescheid(), "der erste");

        Meldebuch.Entscheid entscheid = buch.nimm(nummer, ZWEITER, MITTAGS, DARF);

        // Ohne diesen Riegel sprechen beide denselben Spieler an - und im Protokoll
        // sieht das wie zwei getrennte Vorgaenge aus.
        assertEquals(Meldebuch.Bescheid.SCHON_GENOMMEN, entscheid.bescheid());
        // Die Antwort muss NENNEN, wer sie hat: Ein blosses "zu spaet" liesse den
        // zweiten Moderator ratlos.
        assertEquals(MODERATOR,
                entscheid.meldung().orElseThrow().wirdBearbeitetVon().orElseThrow());
    }

    @Test
    @DisplayName("13 - eine OFFENE Meldung laesst sich unmittelbar erledigen")
    void offeneUnmittelbarErledigen() {
        Meldeablage ablage = new SpeicherMeldungen();
        Meldebuch buch = buch(ablage);
        long nummer = buch.melde(ANNA, BERT, "Vorfall", WELT, 0, -60, 0, MITTAGS)
                .meldung().orElseThrow().nummer();

        // Ohne Zwischenschritt: Die Haelfte aller Meldungen ist auf einen Blick
        // beantwortet, und ein Pflichtschritt dazwischen waere einer, den man umgeht -
        // indem man gar nicht bearbeitet.
        Meldebuch.Entscheid entscheid = buch.erledige(nummer, Meldestand.ABGEWIESEN,
                MODERATOR, "kein Verstoss", MITTAGS, DARF);

        assertEquals(Meldebuch.Bescheid.ERLEDIGT, entscheid.bescheid());
        assertEquals(Meldestand.ABGEWIESEN, ablage.lies(nummer).orElseThrow().stand());
    }

    @Test
    @DisplayName("14 - eine erledigte Meldung laesst sich nicht noch einmal nehmen")
    void erledigteNichtMehrNehmen() {
        Meldeablage ablage = new SpeicherMeldungen();
        Meldebuch buch = buch(ablage);
        long nummer = buch.melde(ANNA, BERT, "Vorfall", WELT, 0, -60, 0, MITTAGS)
                .meldung().orElseThrow().nummer();
        buch.erledige(nummer, Meldestand.ABGESCHLOSSEN, MODERATOR, "fertig", MITTAGS,
                DARF);

        assertEquals(Meldebuch.Bescheid.SCHON_ERLEDIGT,
                buch.nimm(nummer, ZWEITER, MITTAGS, DARF).bescheid());
        assertEquals(Meldebuch.Bescheid.SCHON_ERLEDIGT,
                buch.erledige(nummer, Meldestand.ABGEWIESEN, ZWEITER, "doch nicht",
                        MITTAGS, DARF).bescheid());
    }

    @Test
    @DisplayName("15 - eine unbekannte Nummer meldet UNBEKANNT")
    void unbekannteNummer() {
        Meldebuch buch = buch(new SpeicherMeldungen());

        assertEquals(Meldebuch.Bescheid.UNBEKANNT,
                buch.nimm(4711L, MODERATOR, MITTAGS, DARF).bescheid());
        assertEquals(Meldebuch.Bescheid.UNBEKANNT,
                buch.erledige(4711L, Meldestand.ABGESCHLOSSEN, MODERATOR, "x", MITTAGS,
                        DARF).bescheid());
    }

    @Test
    @DisplayName("16 - ohne das Recht wird nichts bearbeitet")
    void ohneRechtNichtsBearbeiten() {
        Meldeablage ablage = new SpeicherMeldungen();
        Meldebuch buch = buch(ablage);
        long nummer = buch.melde(ANNA, BERT, "Vorfall", WELT, 0, -60, 0, MITTAGS)
                .meldung().orElseThrow().nummer();

        // Eine LEERE Rechtemenge ist nicht die Konsole, sondern ein Spieler ohne jedes
        // Recht - derselbe Unterschied wie im Teambuch.
        assertEquals(Meldebuch.Bescheid.OHNE_RECHT,
                buch.nimm(nummer, ANNA, MITTAGS, DARF_NICHT).bescheid());
        assertEquals(Meldebuch.Bescheid.OHNE_RECHT,
                buch.erledige(nummer, Meldestand.ABGEWIESEN, ANNA, "weg damit", MITTAGS,
                        DARF_NICHT).bescheid());
        assertEquals(Meldestand.OFFEN, ablage.lies(nummer).orElseThrow().stand(),
                "und es ist NICHTS passiert");
    }

    @Test
    @DisplayName("17 - die Konsole darf bearbeiten")
    void konsoleDarfBearbeiten() {
        Meldeablage ablage = new SpeicherMeldungen();
        Meldebuch buch = buch(ablage);
        long nummer = buch.melde(ANNA, BERT, "Vorfall", WELT, 0, -60, 0, MITTAGS)
                .meldung().orElseThrow().nummer();

        // null als Rechtemenge heisst DIE KONSOLE - dieselbe Festlegung wie im
        // Teambuch, und aus demselben Grund: Wer dort steht, hat Dateizugriff.
        assertEquals(Meldebuch.Bescheid.GENOMMEN,
                buch.nimm(nummer, MODERATOR, MITTAGS, null).bescheid());
    }

    @Test
    @DisplayName("18 - DIE ANTWORT WARTET auf den Melder, auch wenn er nicht da war")
    void antwortWartetAufDenMelder() {
        Meldeablage ablage = new SpeicherMeldungen();
        Meldebuch buch = buch(ablage);
        long nummer = buch.melde(ANNA, BERT, "Griefing an der Mauer", WELT, 0, -60, 0,
                MITTAGS).meldung().orElseThrow().nummer();

        // Der Moderator erledigt sie, waehrend anna offline ist. Eine Chatzeile ginge
        // jetzt an niemanden - DAS ist der Satz, mit dem der Plan das Meldewesen
        // begruendet: "sonst meldet beim zweiten Mal niemand mehr".
        buch.erledige(nummer, Meldestand.ABGESCHLOSSEN, MODERATOR,
                "angesprochen, hat aufgehoert", MITTAGS.plusSeconds(3600L), DARF);

        List<Meldung> antworten = buch.holeAntworten(ANNA);

        assertEquals(1, antworten.size(), antworten.toString());
        assertEquals("angesprochen, hat aufgehoert", antworten.get(0).vermerk());
        assertEquals(Meldestand.ABGESCHLOSSEN, antworten.get(0).stand());
    }

    @Test
    @DisplayName("19 - eine abgeholte Antwort kommt beim naechsten Mal NICHT wieder")
    void antwortKommtNurEinmal() {
        Meldeablage ablage = new SpeicherMeldungen();
        Meldebuch buch = buch(ablage);
        long nummer = buch.melde(ANNA, BERT, "Vorfall", WELT, 0, -60, 0, MITTAGS)
                .meldung().orElseThrow().nummer();
        buch.erledige(nummer, Meldestand.ABGEWIESEN, MODERATOR, "nichts dran", MITTAGS,
                DARF);
        assertEquals(1, buch.holeAntworten(ANNA).size(), "die Vorbereitung");

        // Sonst lase der Melder dieselbe Antwort bei JEDEM Beitritt erneut - und eine
        // Antwort, die sich nicht wegklicken laesst, wird zur Belaestigung.
        assertEquals(List.of(), buch.holeAntworten(ANNA));
    }

    @Test
    @DisplayName("20 - wer nichts gemeldet hat, bekommt keine Antworten")
    void ohneMeldungKeineAntwort() {
        assertEquals(List.of(), buch(new SpeicherMeldungen()).holeAntworten(ANNA));
    }

    @Test
    @DisplayName("21 - die offene Liste haelt die eingestellte Seitenlaenge ein")
    void offeneListeHaeltDieSeitenlaenge() {
        Meldeablage ablage = new SpeicherMeldungen();
        Meldebuch buch = buch(ablage, WERTE.mitAbklingzeit(0L).mitHoechsteOffene(100)
                .mitZeilenJeSeite(2));
        for (int nummer = 0; nummer < 5; nummer++) {
            buch.melde(ANNA, UUID.randomUUID(), "Vorfall " + nummer, WELT, 0, -60, 0,
                    MITTAGS.plusSeconds(nummer));
        }

        // Die Zahl kommt aus der DATEI und nicht aus dem Programm - sonst waere die
        // Seitenlaenge eine Zahl, die nur ein Neubau aendert.
        assertEquals(2, buch.offene().size());
        assertEquals("Vorfall 0", buch.offene().get(0).grund(), "die aelteste zuerst");
    }

    @Test
    @DisplayName("22 - eine werfende Ablage wird zum Bescheid und nicht zum Wurf")
    void werfendeAblage() {
        Meldebuch gestoert = new Meldebuch(new WerfendeAblage(), () -> WERTE,
                RECHT);

        // Der Aufrufer ist ein Befehl, und ein Wurf dort ist eine rote englische Zeile
        // fuer eine Lage, die der Spieler versteht, sobald sie auf Deutsch dasteht.
        assertEquals(Meldebuch.Bescheid.GESTOERT,
                gestoert.melde(ANNA, BERT, "Vorfall", WELT, 0, -60, 0, MITTAGS)
                        .bescheid());
        assertEquals(Meldebuch.Bescheid.GESTOERT,
                gestoert.nimm(1L, MODERATOR, MITTAGS, DARF).bescheid());
        assertEquals(Meldebuch.Bescheid.GESTOERT,
                gestoert.erledige(1L, Meldestand.ABGESCHLOSSEN, MODERATOR, "x", MITTAGS,
                        DARF).bescheid());
    }

    @Test
    @DisplayName("23 - brauchbarerGrund sagt dasselbe wie der Konstruktor")
    void brauchbarerGrundStimmtMitDemKonstruktor() {
        // Zwei Wahrheiten darueber, was ein brauchbarer Grund ist, waeren eine Absage im
        // Befehl und ein Wurf in der Ablage - oder umgekehrt, was schlimmer ist: eine
        // angenommene Meldung, die beim Schreiben wirft.
        assertFalse(Meldebuch.brauchbarerGrund(null));
        assertFalse(Meldebuch.brauchbarerGrund("ab"));
        assertFalse(Meldebuch.brauchbarerGrund("  "));
        assertFalse(Meldebuch.brauchbarerGrund("x".repeat(Meldung.LAENGSTER_GRUND + 1)));
        assertFalse(Meldebuch.brauchbarerGrund("mit§cFarbe"));
        assertTrue(Meldebuch.brauchbarerGrund("hax"));
        assertTrue(Meldebuch.brauchbarerGrund("x".repeat(Meldung.LAENGSTER_GRUND)));
        // Die Gegenprobe am Konstruktor selbst, fuer jeden Fall, den brauchbarerGrund
        // ablehnt beziehungsweise annimmt.
        assertThrows(IllegalArgumentException.class,
                () -> Meldung.neu(1L, ANNA, BERT, "ab", WELT, 0, -60, 0, MITTAGS));
        assertEquals("hax", Meldung.neu(1L, ANNA, BERT, "hax", WELT, 0, -60, 0, MITTAGS)
                .grund());
    }

    @Test
    @DisplayName("24 - ein zu langer Vermerk wird abgewiesen, ohne Wurf")
    void zuLangerVermerk() {
        Meldeablage ablage = new SpeicherMeldungen();
        Meldebuch buch = buch(ablage);
        long nummer = buch.melde(ANNA, BERT, "Vorfall", WELT, 0, -60, 0, MITTAGS)
                .meldung().orElseThrow().nummer();

        assertEquals(Meldebuch.Bescheid.GRUND_UNBRAUCHBAR,
                buch.erledige(nummer, Meldestand.ABGESCHLOSSEN, MODERATOR,
                        "x".repeat(Meldung.LAENGSTER_VERMERK + 1), MITTAGS, DARF)
                        .bescheid());
        assertEquals(Meldestand.OFFEN, ablage.lies(nummer).orElseThrow().stand(),
                "und die Meldung ist unangetastet");
    }

    @Test
    @DisplayName("25 - fehlende Werte fallen auf die Vorgabe zurueck")
    void fehlendeWerteFallenZurueck() {
        // Der Lieferant kann null geben, wenn das Neuladen mitten im Zug steht. Ein Wurf
        // waere ein Befehl, der in genau dieser Sekunde abbricht.
        Meldebuch buch = new Meldebuch(new SpeicherMeldungen(), () -> null, RECHT);

        assertEquals(Meldewerte.VORGABE, buch.werte());
    }

    @Test
    @DisplayName("26 - ohne Ablage, Werte, Melder oder Bearbeiter gibt es nichts")
    void riegel() {
        Meldeablage ablage = new SpeicherMeldungen();
        Meldebuch buch = buch(ablage);

        assertThrows(IllegalArgumentException.class,
                () -> new Meldebuch(null, () -> WERTE, RECHT));
        assertThrows(IllegalArgumentException.class,
                () -> new Meldebuch(ablage, null, RECHT));
        // Ohne Rechtenamen waere der Riegel stumm IMMER ZU: Keine Rechtemenge
        // enthaelt einen leeren Namen, also duerfte niemand bearbeiten - und das
        // saehe aus wie ein fehlendes Recht.
        assertThrows(IllegalArgumentException.class,
                () -> new Meldebuch(ablage, () -> WERTE, null));
        assertThrows(IllegalArgumentException.class,
                () -> new Meldebuch(ablage, () -> WERTE, "  "));
        assertThrows(IllegalArgumentException.class,
                () -> buch.melde(null, BERT, "Vorfall", WELT, 0, -60, 0, MITTAGS));
        assertThrows(IllegalArgumentException.class,
                () -> buch.melde(ANNA, null, "Vorfall", WELT, 0, -60, 0, MITTAGS));
        assertThrows(IllegalArgumentException.class,
                () -> buch.nimm(1L, null, MITTAGS, DARF));
        assertThrows(IllegalArgumentException.class,
                () -> buch.erledige(1L, Meldestand.ABGESCHLOSSEN, null, "x", MITTAGS,
                        DARF));
        assertThrows(IllegalArgumentException.class,
                () -> buch.erledige(1L, Meldestand.OFFEN, MODERATOR, "x", MITTAGS,
                        DARF));
        assertThrows(IllegalArgumentException.class, () -> buch.holeAntworten(null));
    }

    @Test
    @DisplayName("27 - ABKLINGZEIT ohne Restzeit und ZU_VIELE ohne Grenze klagen")
    void entscheidBrauchtSeineZahl() {
        // Sonst liest der Melder "warte noch 0 Sekunden" beziehungsweise eine Absage
        // ohne Zahl - beides sieht aus wie ein Fehler und nicht wie eine Regel.
        assertThrows(IllegalArgumentException.class,
                () -> new Meldebuch.Entscheid(Meldebuch.Bescheid.ABKLINGZEIT,
                        Optional.empty(), 0L, 0));
        assertThrows(IllegalArgumentException.class,
                () -> new Meldebuch.Entscheid(Meldebuch.Bescheid.ZU_VIELE_OFFENE,
                        Optional.empty(), 0L, 0));
    }

    /** Eine Ablage, die bei jedem Zugriff wirft - fuer den Bescheid GESTOERT. */
    private static final class WerfendeAblage implements Meldeablage {
        @Override
        public Meldung lege(UUID melder, UUID betroffener, String grund, String welt,
                            int x, int y, int z, Instant jetzt) {
            throw new IllegalStateException("Meldung nicht schreibbar");
        }

        @Override
        public Optional<Meldung> lies(long nummer) {
            throw new IllegalStateException("Meldung nicht lesbar");
        }

        @Override
        public boolean schreibe(Meldung geaendert, Meldestand erwartet) {
            throw new IllegalStateException("Meldung nicht schreibbar");
        }

        @Override
        public List<Meldung> offene(int hoechstens) {
            throw new IllegalStateException("Liste nicht lesbar");
        }

        @Override
        public int offeneVon(UUID melder) {
            throw new IllegalStateException("Zahl nicht lesbar");
        }

        @Override
        public Optional<Instant> letzteVon(UUID melder) {
            throw new IllegalStateException("Zeitpunkt nicht lesbar");
        }

        @Override
        public List<Meldung> ungeleseneVon(UUID melder) {
            throw new IllegalStateException("Antworten nicht lesbar");
        }
    }
}
