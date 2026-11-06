package de.freimarken.paper;

import de.freimarken.kern.Berufsangabe;
import de.freimarken.kern.Berufsverzeichnis;
import de.freimarken.kern.Berufswerte;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.bukkit.configuration.ConfigurationSection;

/**
 * Liest die Berufe aus der {@code config.yml} - der eine Leser.
 *
 * <p>Nach demselben Muster wie {@link Weltenliste} und {@link Wirtschaftsliste}: reine
 * Dateiarbeit, deshalb ohne Server pruefbar, und mit gesammelten KLAGEN statt Ausnahmen.
 * Ein Tippfehler in einem von zwanzig Berufen darf keinen Serverstart mitreissen; er darf
 * aber auch nicht still gelten.
 *
 * <p><b>Zwei Ergebnisse und nicht eines</b>, weil die beiden Haelften verschieden hart
 * sind:
 * <ul>
 *   <li>{@link Liste} ist die Liste der Berufe. Sie wird NUR BEIM START gelesen - an ihr
 *       haengen die Vervollstaendigung von {@code /beruf}, die im Befehlsbaum steckt (und
 *       der wird genau einmal angemeldet), und die Rolle jedes Berufes, mit der jede Zeile
 *       der Datenbank gelesen wird. Darum gibt es hier nur Klagen und keinen Bericht: Eine
 *       Klage heisst "dieser Eintrag ist uebersprungen", und sie darf das Neuladen der
 *       Gebuehr nicht verhindern.</li>
 *   <li>{@link Werte} sind die vier Zahlen. Sie sind neuladbar, und eine Klage daran heisst
 *       wie bei {@link Wirtschaftsliste} "es wird gar nichts uebernommen".</li>
 * </ul>
 *
 * <p>Die Helfer fuer Zahlen und Meldungen kommen aus {@link Wirtschaftsliste} und sind
 * nicht abgeschrieben - {@code ganzeZahl} faengt den Punkt, der in YAML keine Tausender
 * trennt, und {@code fehltText} entscheidet je Schluessel, ob ein "es gilt" dazu stimmt.
 * Zweimal gebaut wichen die beiden eines Tages ab.
 */
final class Berufsliste {

    /** Alle Schluessel, die diese Klasse liest - fuer die Meldung ueber fehlende Zeilen. */
    private static final List<String> SCHLUESSEL = List.of(
            Berufswerte.PFAD_WECHSELGEBUEHR,
            Berufswerte.PFAD_SPERRFRIST,
            Berufswerte.PFAD_GRUNDBERUFE,
            Berufswerte.PFAD_SPEZIALISIERUNGEN,
            Berufswerte.PFAD_LISTE);

    /** Der Schluessel am Grundberuf, unter dem seine Spezialisierungen stehen. */
    private static final String UNTER_SPEZIALISIERUNGEN = "spezialisierungen";

    /** Die Liste der Berufe - nur beim Start gelesen, darum ohne Bericht. */
    record Liste(Berufsverzeichnis verzeichnis, List<String> klagen) {
    }

    /** Die vier neuladbaren Zahlen - und woran es gehapert hat. */
    record Werte(Berufswerte werte, List<String> klagen, List<String> bericht) {
    }

    private Berufsliste() {
    }

    /**
     * Der Abschnitt {@code berufe.liste}.
     *
     * <p>Das Dateiformat nennt EINEN Eintrag je Grundberuf und haengt seine
     * Spezialisierungen als Flussliste daran. Daraus entstehen hier die Angaben: zuerst der
     * Grundberuf, dann je Eintrag der Flussliste eine Spezialisierung mit diesem
     * Grundberuf. Die Liste am Grundberuf und nicht als eigener Abschnitt - so ist ein
     * neuer Beruf ein Eintrag und nicht ein Eintrag plus eine vergessene Zuordnung;
     * dieselbe Entscheidung wie beim {@code tor:} einer Welt.
     *
     * @param abschnitt der Abschnitt {@code berufe.liste}, darf {@code null} sein
     */
    static Liste listeAus(ConfigurationSection abschnitt) {
        List<String> klagen = new ArrayList<>();
        if (abschnitt == null) {
            klagen.add("Es gibt keinen Abschnitt " + Berufswerte.PFAD_LISTE + " in der"
                    + " config.yml - ohne Berufe hat /beruf nichts zu tun.");
            return new Liste(Berufsverzeichnis.ausAngaben(List.of()), klagen);
        }
        // Erst sammeln, dann urteilen: Ein Name, der zweimal vorkommt, laesst BEIDE
        // Nennungen fallen. Wuerde die erste gelten, entschiede die Reihenfolge der
        // Eintraege, was ein Beruf voraussetzt - und die Reihenfolge einer
        // Einstellungsdatei ist kein Ort fuer eine Entscheidung.
        Map<String, List<String>> nennungen = new LinkedHashMap<>();
        List<Berufsangabe> angaben = new ArrayList<>();
        for (String name : abschnitt.getKeys(false)) {
            String pfad = Berufswerte.PFAD_LISTE + "." + name;
            if (!brauchbarerName(pfad, name, klagen)) {
                continue;
            }
            // EINMAL kleingeschrieben, und zwar hier, wo der Name hereinkommt. Merkte sich
            // die Doppelt-Pruefung den rohen Schluessel und die Angabe den
            // kleingeschriebenen, waeren "Bergmann" und "bergmann" zwei Berufe - und der
            // zweite verdraengte den ersten still.
            String beruf = name.trim().toLowerCase(Locale.ROOT);
            if (abschnitt.isConfigurationSection(name)
                    || abschnitt.get(name) == null) {
                // Der zweite Fall ist in der Praxis unerreichbar und steht trotzdem hier:
                // Bukkit wirft eine Zeile OHNE Wert beim Laden weg, "bergmann:" allein
                // kommt also gar nicht als Schluessel an (gemessen in BerufslisteTest).
                // Erreichbar ist nur der erste Fall, und darum muss ein Grundberuf ohne
                // Spezialisierung "spezialisierungen: []" schreiben. Die Zeile bleibt
                // stehen, weil ein anderer Lader sie liefern koennte und sie dann
                // dasselbe heissen soll: keine Spezialisierung.
                merke(nennungen, beruf, pfad);
                angaben.add(new Berufsangabe(beruf, null));
                angaben.addAll(spezialisierungen(abschnitt, name, beruf, pfad, nennungen,
                        klagen));
                continue;
            }
            klagen.add(pfad + " ist kein Abschnitt (" + Wirtschaftsliste.wieGetippt(
                    abschnitt.get(name)) + ") - uebersprungen. Ein Grundberuf traegt"
                    + " seine Spezialisierungen unter " + UNTER_SPEZIALISIERUNGEN + ":,"
                    + " und ein Grundberuf ohne Spezialisierung bleibt leer.");
        }
        return new Liste(Berufsverzeichnis.ausAngaben(
                ohneDoppelte(angaben, nennungen, klagen)), klagen);
    }

    /**
     * Die Flussliste der Spezialisierungen eines Grundberufes.
     *
     * <p>Gelesen wird ROH ueber {@code get} und nicht mit {@code getStringList}: Letzteres
     * macht aus einer vergessenen Klammer ({@code spezialisierungen: steinmetz}) stumm eine
     * leere Liste, und der Betreiber sucht den Beruf, den er eingetragen hat.
     */
    private static List<Berufsangabe> spezialisierungen(ConfigurationSection abschnitt,
                                                        String schluessel,
                                                        String grundberuf, String pfad,
                                                        Map<String, List<String>> nennungen,
                                                        List<String> klagen) {
        ConfigurationSection eintrag = abschnitt.getConfigurationSection(schluessel);
        Object roh = eintrag == null ? null : eintrag.get(UNTER_SPEZIALISIERUNGEN);
        if (roh == null) {
            return List.of();
        }
        String unterpfad = pfad + "." + UNTER_SPEZIALISIERUNGEN;
        if (!(roh instanceof List<?> liste)) {
            klagen.add(unterpfad + " ist keine Liste (" + Wirtschaftsliste.wieGetippt(roh)
                    + ") - uebersprungen. Erwartet werden eckige Klammern, zum Beispiel"
                    + " [steinmetz]; leere Klammern heissen: keine Spezialisierung.");
            return List.of();
        }
        List<Berufsangabe> gefunden = new ArrayList<>();
        for (Object stueck : liste) {
            if (!(stueck instanceof String name)) {
                klagen.add(unterpfad + " enthaelt einen Eintrag, der kein Name ist ("
                        + Wirtschaftsliste.wieGetippt(stueck) + ") - uebersprungen.");
                continue;
            }
            if (!brauchbarerName(unterpfad, name, klagen)) {
                continue;
            }
            String sauber = name.trim().toLowerCase(Locale.ROOT);
            if (sauber.equals(grundberuf)) {
                // VOR dem Bauen der Angabe: Ihr Kompaktkonstruktor wirft bei einem
                // Verweis auf sich selbst, und ein Leser der config.yml darf nicht
                // werfen. Die Doppelt-Regel unten faengt denselben Namen auch, aber erst
                // nach dem Wurf - also nie.
                klagen.add(unterpfad + ": \"" + sauber + "\" ist der Grundberuf selbst"
                        + " - uebersprungen. Ein Beruf kann nicht auf sich selbst"
                        + " aufbauen; die Platzzaehlung zaehlte ihn dann zweimal, und die"
                        + " Absage hiesse \"Spezialisierungen voll\" bei einem Spieler,"
                        + " der keine hat.");
                continue;
            }
            merke(nennungen, sauber, unterpfad);
            gefunden.add(new Berufsangabe(sauber, grundberuf));
        }
        return gefunden;
    }

    /**
     * Die Namensregel - gefragt und nicht gewagt.
     *
     * <p>Gefragt wird {@link Berufsangabe#brauchbareLaenge} und
     * {@link Berufsangabe#brauchbareZeichen}, nicht der Konstruktor: Der wirft, und dieser
     * Leser darf nicht werfen. Dass beide dieselbe Regel benutzen, ist der Grund, warum sie
     * dort oeffentlich steht.
     */
    private static boolean brauchbarerName(String pfad, String name,
                                           List<String> klagen) {
        if (!Berufsangabe.brauchbareLaenge(name)) {
            klagen.add(pfad + ": Der Berufsschluessel \"" + name + "\" ist "
                    + name.trim().length() + " Zeichen lang, erlaubt sind "
                    + Berufsangabe.HOECHSTENS_ZEICHEN + " - uebersprungen. Er steht in"
                    + " einer Datenbankspalte; ein laengerer wuerde dort stumm"
                    + " abgeschnitten, und zwei Berufe fielen zusammen.");
            return false;
        }
        if (!Berufsangabe.brauchbareZeichen(name)) {
            klagen.add(pfad + ": Der Berufsschluessel \"" + name + "\" taugt nicht"
                    + " - uebersprungen. Erlaubt sind Kleinbuchstaben, Ziffern und"
                    + " Bindestrich, und beginnen muss er mit einem Buchstaben. Kein"
                    + " Punkt: Er ist in der sprache.yml der Trenner, und"
                    + " beruf.name.berg.mann waere dort eine Ebene tiefer statt ein Name.");
            return false;
        }
        return true;
    }

    private static void merke(Map<String, List<String>> nennungen, String name,
                              String pfad) {
        nennungen.computeIfAbsent(name, wer -> new ArrayList<>()).add(pfad);
    }

    /**
     * Jeder Name, der zweimal vorkommt, faellt GANZ heraus - mit beiden Nennungen.
     *
     * <p>Nicht "die erste gilt": Steht {@code steinmetz} unter zwei Grundberufen, dann
     * entschiede die Reihenfolge der Eintraege, welchen er voraussetzt - und eine Datei
     * umzusortieren aenderte stumm die Spielregeln. Dasselbe fuer einen Namen, der Grund-
     * und Spezialberuf zugleich ist: Die Platzzaehlung der Regel kennt zwei Toepfe, und
     * ein Beruf in beiden zaehlte zweimal.
     *
     * <p>{@link Berufsverzeichnis#ausAngaben} laesst bei Doppelten die erste gelten; das
     * ist dort richtig, denn es kennt die Datei nicht und darf nichts wegwerfen, was ihm
     * jemand gibt. Die strengere Regel gehoert zum LESER, der sagen kann, wo beide stehen.
     */
    private static List<Berufsangabe> ohneDoppelte(List<Berufsangabe> angaben,
                                                   Map<String, List<String>> nennungen,
                                                   List<String> klagen) {
        List<String> doppelte = new ArrayList<>();
        for (Map.Entry<String, List<String>> eintrag : nennungen.entrySet()) {
            if (eintrag.getValue().size() > 1) {
                doppelte.add(eintrag.getKey());
                klagen.add("Der Berufsschluessel \"" + eintrag.getKey() + "\" steht "
                        + eintrag.getValue().size() + " mal in der config.yml ("
                        + String.join(", ", eintrag.getValue()) + ") - ALLE Nennungen"
                        + " fallen weg. Welche gilt, entschiede sonst die Reihenfolge der"
                        + " Eintraege.");
            }
        }
        if (doppelte.isEmpty()) {
            return angaben;
        }
        List<String> weg = List.copyOf(doppelte);
        List<Berufsangabe> bleibt = new ArrayList<>();
        for (Berufsangabe angabe : angaben) {
            // Auch eine Spezialisierung, deren GRUNDBERUF doppelt genannt ist, faellt mit:
            // Sonst haengt sie an einem Beruf, den es nicht mehr gibt - und
            // Berufsverzeichnis liesse sie still liegen, ohne dass es jemand liest.
            if (!weg.contains(angabe.schluessel())
                    && (angabe.grundberuf() == null
                            || !weg.contains(angabe.grundberuf()))) {
                bleibt.add(angabe);
            }
        }
        return bleibt;
    }

    /**
     * Die vier neuladbaren Zahlen.
     *
     * @param wurzel die ganze Konfiguration, darf {@code null} sein
     * @param gilt   das Verzeichnis, das JETZT gilt - nicht das der Datei. Die Liste ist
     *               nicht neuladbar, also muss die Querpruefung gegen den laufenden Stand
     *               gehen: Gegen die Datei geprueft ginge {@code hoechstens-grundberufe: 7}
     *               durch, sobald die Datei sieben Berufe nennt - der laufende Server kennt
     *               sechs, und der siebte Platz ist nie fuellbar. Dieselbe Trennung wie bei
     *               Takt und Untaetigkeitsgrenze
     */
    static Werte werteAus(ConfigurationSection wurzel, Berufsverzeichnis gilt) {
        List<String> klagen = new ArrayList<>();
        List<String> bericht = new ArrayList<>();
        if (wurzel == null) {
            return new Werte(Berufswerte.VORGABE, klagen, bericht);
        }
        nenneFehlende(wurzel, bericht);

        long gebuehr = Wirtschaftsliste.betrag(wurzel, Berufswerte.PFAD_WECHSELGEBUEHR,
                "wechselgebuehr", Berufswerte.VORGABE_WECHSELGEBUEHR,
                Berufswerte.HOECHSTE_WECHSELGEBUEHR, "Mark", klagen, bericht);
        long stunden = sperrfristStunden(wurzel, klagen, bericht);
        int grundberufe = plaetze(wurzel, Berufswerte.PFAD_GRUNDBERUFE,
                Berufswerte.VORGABE_GRUNDBERUFE, 1, gilt.anzahlDerRolle(false),
                "Grundberufe", klagen, bericht);
        int spezialisierungen = plaetze(wurzel, Berufswerte.PFAD_SPEZIALISIERUNGEN,
                Berufswerte.VORGABE_SPEZIALISIERUNGEN, 0, gilt.anzahlDerRolle(true),
                "Spezialisierungen", klagen, bericht);

        if (gebuehr == 0L) {
            bericht.add(Berufswerte.PFAD_WECHSELGEBUEHR + " ist 0 - ein Beruf aufzugeben"
                    + " kostet nichts, und von den zwei Bremsen bleibt allein die"
                    + " Sperrfrist.");
        }
        if (stunden == 0L) {
            bericht.add(Berufswerte.PFAD_SPERRFRIST + " ist 0 - es gibt keine Sperrfrist,"
                    + " und von den zwei Bremsen bleibt allein die Gebuehr.");
        }
        if (gebuehr == 0L && stunden == 0L) {
            bericht.add("Beide Bremsen sind aus: Ein Spieler kann seinen Beruf beliebig"
                    + " oft wechseln. Mit Teil E heisst das, dass er je Aufgabe den"
                    + " passenden Beruf anlegt.");
        }
        return new Werte(new Berufswerte(gebuehr, Duration.ofHours(stunden), grundberufe,
                spezialisierungen), klagen, bericht);
    }

    /**
     * Die Sperrfrist in Stunden.
     *
     * <p>Geprueft wird die ROH gelesene Zahl an {@link Berufswerte#brauchbareSperrfrist}
     * und nicht am Konstruktor: Der wirft, und dieser Leser darf nicht werfen.
     */
    private static long sperrfristStunden(ConfigurationSection wurzel,
                                          List<String> klagen, List<String> bericht) {
        String pfad = Berufswerte.PFAD_SPERRFRIST;
        Long gelesen = Wirtschaftsliste.ganzeZahl(wurzel, pfad,
                Berufswerte.VORGABE_SPERRFRIST_STUNDEN, klagen, bericht);
        if (gelesen == null) {
            return Berufswerte.VORGABE_SPERRFRIST_STUNDEN;
        }
        if (!Berufswerte.brauchbareSperrfrist(gelesen)) {
            klagen.add(pfad + " ist " + gelesen + " - verworfen. Erlaubt sind 0 bis "
                    + Berufswerte.LAENGSTE_SPERRFRIST_STUNDEN + " Stunden (30 Tage); 0"
                    + " heisst ausdruecklich: keine Sperrfrist. "
                    + Wirtschaftsliste.esBleibt(Berufswerte.VORGABE_SPERRFRIST_STUNDEN));
            return Berufswerte.VORGABE_SPERRFRIST_STUNDEN;
        }
        return gelesen;
    }

    /**
     * Eine Platzzahl - und die Querpruefung gegen die Liste, die gerade gilt.
     *
     * <p><b>Zwei Faelle mit verschiedener Haerte</b>, und das ist der Kern dieser Methode:
     * <ul>
     *   <li>Der Betreiber hat eine Zahl GETIPPT, die die Liste nicht hergibt - dann gilt
     *       sie nicht, und das ist eine Klage. Er verspricht einen Platz, den niemand
     *       fuellen kann.</li>
     *   <li>Die Zahl, die danach uebrig bleibt, passt immer noch nicht - weil die
     *       ausgelieferte Vorgabe mehr verspricht als eine kurze Liste hergibt. Dann wird
     *       sie auf die Liste gekuerzt, und das ist ein BERICHT: Es wird nichts
     *       abgewiesen, was jemand getippt hat. Eine Klage hier verhinderte jedes
     *       Neuladen, solange die Liste kurz ist.</li>
     * </ul>
     *
     * @param untergrenze 1 bei den Grundberufen, 0 bei den Spezialisierungen. Eine 0 bei
     *                    den Grundberufen hiesse, niemand darf einen Beruf - und das sieht
     *                    in einer Einstellungsdatei aus wie ein Tippfehler, weil es einer
     *                    ist. Bei den Spezialisierungen ist die 0 ein echter Betriebsfall
     * @param inDerListe  wie viele Berufe dieser Rolle das LAUFENDE Verzeichnis kennt
     */
    private static int plaetze(ConfigurationSection wurzel, String pfad, int vorgabe,
                               int untergrenze, int inDerListe, String rolle,
                               List<String> klagen, List<String> bericht) {
        Long gelesen = Wirtschaftsliste.ganzeZahl(wurzel, pfad, vorgabe, klagen, bericht);
        long wert = gelesen == null ? vorgabe : gelesen;
        // Geprueft wird der long und NICHT der verkuerzte int: (int) -2147483649 ist
        // 2147483647, und eine negative Zahl gaelte so als fast unbegrenzte Platzzahl.
        boolean brauchbar = untergrenze == 0
                ? Berufswerte.brauchbareAnzahlOderNull(wert)
                : Berufswerte.brauchbareAnzahl(wert);
        if (gelesen != null && !brauchbar) {
            klagen.add(pfad + " ist " + wert + " - verworfen. Erlaubt ist eine ganze Zahl"
                    + " ab " + untergrenze + "."
                    + (untergrenze == 1
                            ? " Eine 0 hiesse, niemand darf einen Beruf; wer die Berufe"
                                    + " abschalten will, nimmt das Recht weg."
                            : " Die 0 ist erlaubt und heisst: keine Spezialisierungen.")
                    + " " + Wirtschaftsliste.esBleibt(vorgabe));
            wert = vorgabe;
        } else if (gelesen != null && !Berufswerte.passtZurListe(wert, inDerListe)) {
            klagen.add(pfad + " ist " + wert + ", aber in " + Berufswerte.PFAD_LISTE
                    + " stehen nur " + inDerListe + " " + rolle + " - verworfen. Ein"
                    + " Platz, den keine Liste fuellen kann, ist ein Versprechen an den"
                    + " Spieler, das kein Beruf einloest. "
                    + Wirtschaftsliste.esBleibt(vorgabe));
            wert = vorgabe;
        }
        long gekuerzt = Math.max(untergrenze, Math.min(wert, inDerListe));
        if (gekuerzt != wert) {
            bericht.add(pfad + ": Es gelten " + gekuerzt + " statt " + wert + " - in "
                    + Berufswerte.PFAD_LISTE + " stehen " + inDerListe + " " + rolle
                    + ". Hier wird nichts abgewiesen: Die Zahl stammt aus den Vorgaben des"
                    + " Plugins, und sie auf die Liste zu kuerzen ist richtiger, als einen"
                    + " Platz zu versprechen, den niemand fuellen kann.");
        }
        return (int) gekuerzt;
    }

    /**
     * Nennt jede Berufszeile, die das Plugin mitbringt, die aber in der Datei nicht steht.
     *
     * <p>Eigene Liste und eigener Zaehlsatz, nicht die von {@link Wirtschaftsliste}: Zwei
     * Berichte, die dieselbe Zahl verschieden nennen, sind schlimmer als zwei Zeilen.
     */
    private static void nenneFehlende(ConfigurationSection wurzel, List<String> bericht) {
        List<String> fehlen = new ArrayList<>();
        for (String pfad : SCHLUESSEL) {
            if (!wurzel.contains(pfad, true)) {
                fehlen.add(pfad);
            }
        }
        if (!fehlen.isEmpty()) {
            bericht.add(fehlen.size() + " von " + SCHLUESSEL.size() + " Berufszeilen"
                    + " stehen nicht in deiner config.yml: " + String.join(", ", fehlen)
                    + ". Sichtbar und aenderbar sind sie erst, wenn sie in der Datei"
                    + " stehen; was das Plugin ohne die Zeile mitbringt, nennt die eigene"
                    + " Zeile zu jedem Schluessel.");
        }
    }
}
