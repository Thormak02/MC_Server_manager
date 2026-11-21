package de.freimarken.paper;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/**
 * WAS in einem Menue steht und WAS ein Klick bedeutet - reine Daten, kein Bukkit.
 *
 * <p><b>Das ist der Schnitt, auf dem das ganze Menuegeruest steht.</b> Ein Fach, ein
 * Satzschluessel, ein Gegenstandsname und ein Befehl sind Zahlen und Zeichenketten; daraus
 * ein Inventar zu machen braucht einen laufenden Server ({@code Bukkit.createInventory}
 * und {@code new ItemStack} greifen in Minecrafts Verzeichnisse). Also steht hier die
 * Entscheidung und dort die Hand - dieselbe Teilung wie bei {@code Vorrat} und
 * {@code Vorratshand}, und aus demselben Grund: So laeuft jede Lage dieses Menues durch
 * eine Probe, und zwar die Lagen, die im Spiel wehtun (ein Klick ins Leere, ein Klick auf
 * ein fremdes Fach, zwei Eintraege auf demselben Fach).
 *
 * <p><b>Ein Klick fuehrt einen BEFEHL aus - und nichts sonst.</b> Das ist die wichtigste
 * Entscheidung dieses Geruests: Das Menue gewaehrt nie etwas selbst. Es tippt fuer den
 * Spieler, und die Rechte- und Geldpruefung macht derselbe Befehlszweig, den er auch von
 * Hand erreicht. Plan 11.2 verlangt genau das ("ein Dialog ist nur Eingabe; geprueft wird
 * serverseitig danach") - und ohne diese Regel waere jedes neue Menue eine zweite Tuer,
 * hinter der die Pruefung fehlen kann.
 *
 * <p><b>Die Eintraege des Hauptmenues stehen im PROGRAMM</b>, ihre Worte in der
 * Sprachdatei. Welche Befehle es gibt, weiss das Programm; wie sie heissen und was sie
 * erklaeren, gehoert dem Betreiber. Eine Liste in der {@code config.yml} kommt, sobald es
 * ein ZWEITES Menue gibt (der Rundgang in Teil D braucht eines) - ein Einstellblock fuer
 * genau eine Tafel waere eine Zusage, die heute niemand einloest.
 */
record Menuetafel(Satz titel, int zeilen, List<Menuetafel.Eintrag> eintraege) {

    /** Die groesste Truhe, die Minecraft kennt - sechs Reihen zu neun Faechern. */
    static final int ZEILEN_HOECHSTENS = 6;

    /** Faecher je Zeile. Keine Einstellung: Das ist die Breite jeder Truhe. */
    static final int BREITE = 9;

    /**
     * Ein Eintrag des Menues.
     *
     * @param fach       das Fach, von 0 an gezaehlt wie in Bukkit
     * @param name       der Satz fuer den Anzeigenamen des Gegenstands. Art.SATZ und
     *                   nicht Art.WORT: Der Anzeigename ist ein {@code Component} und
     *                   darf die Farbwelt tragen. Aufgeloest wird er beim Bauen des
     *                   Menues, weil Paper Gegenstandsnamen nicht uebersetzt - dieselbe
     *                   Begruendung steht am Kopf von {@link MenueSatz}
     * @param erklaerung der Satz fuer die Zeile darunter. Nach Plan 9a.5 ist ein Menue ein
     *                   Formular und keine Schriftrolle: EINE Zeile, in Alltagssprache
     * @param gegenstand der Name des Gegenstands, kleingeschrieben wie in der Datei
     *                   ({@code grass_block}). Als ZEICHENKETTE, weil {@code Material} ein
     *                   Verzeichnis des Servers ist - die Hand loest ihn auf und klagt,
     *                   wenn es ihn nicht gibt. Der Konstruktor legt ihn ueber
     *                   {@link Menuetafel#artname} klein und ohne Rand ab
     * @param befehl     der Befehl OHNE Schraegstrich, den ein Klick ausfuehrt. Leer heisst
     *                   "nichts" - fuer eine Zierde oder eine Ueberschrift im Menue. Ein
     *                   Befehl, den {@link Hilfeplan} nicht kennt, wird abgewiesen: Ohne
     *                   ihn dort gibt es kein Recht, nach dem {@link #fuerRechte} fragen
     *                   koennte
     */
    record Eintrag(int fach, Satz name, Satz erklaerung, String gegenstand,
                   String befehl) {

        Eintrag {
            if (fach < 0) {
                throw new IllegalArgumentException("Ein Fach unter 0 gibt es nicht: "
                        + fach);
            }
            if (name == null || erklaerung == null) {
                throw new IllegalArgumentException("Ein Eintrag ohne Namen oder"
                        + " Erklaerung - beides liest der Spieler am Gegenstand");
            }
            if (gegenstand == null || gegenstand.isBlank()) {
                throw new IllegalArgumentException("Ein Eintrag ohne Gegenstand waere ein"
                        + " leeres Fach, und ein leeres Fach kann man nicht anklicken");
            }
            // Hier und nicht in der Hand: Die Hand gibt den Namen an matchMaterial, und
            // jenes macht aus einem fuehrenden Leerzeichen ein _GRASS_BLOCK und damit
            // null - eine Absage im Spiel fuer einen Rand, den niemand sieht.
            gegenstand = artname(gegenstand);
            befehl = befehl == null ? "" : befehl.trim();
            if (befehl.startsWith("/")) {
                throw new IllegalArgumentException("Der Befehl steht OHNE Schraegstrich"
                        + " da - performCommand nimmt ihn so: " + befehl);
            }
            if (!befehl.isEmpty() && Hilfeplan.ALLE.recht(befehl) == null) {
                throw new IllegalArgumentException("Den Befehl " + befehl + " kennt der"
                        + " Hilfeplan nicht, also gibt es kein Recht, nach dem das Menue"
                        + " fragen koennte - und es boete ihn jedem an. Erst in den Plan"
                        + " aufnehmen, dann in die Tafel.");
            }
        }

        /** Ob ein Klick auf diesen Eintrag etwas tut. */
        boolean tut() {
            return !befehl.isEmpty();
        }

        /**
         * Das Recht, das dieser Eintrag verlangt - aus dem {@link Hilfeplan}.
         *
         * <p>Nachgefragt und nicht abgeschrieben: Es ist dasselbe Recht, das
         * {@code /hilfe} fragt, und dasselbe, das der Befehl selbst an seiner Wurzel
         * verlangt. Eine Zierde ohne Befehl darf jeder sehen.
         */
        String recht() {
            return befehl.isEmpty() ? Hilfeplan.JEDER : Hilfeplan.ALLE.recht(befehl);
        }
    }

    Menuetafel {
        if (titel == null) {
            throw new IllegalArgumentException("Ein Menue ohne Titel - nach Plan 9a.1"
                    + " traegt jedes den historischen Namen im Titel");
        }
        if (zeilen < 1 || zeilen > ZEILEN_HOECHSTENS) {
            throw new IllegalArgumentException("Ein Menue hat 1 bis " + ZEILEN_HOECHSTENS
                    + " Zeilen, nicht " + zeilen);
        }
        eintraege = List.copyOf(eintraege);
        Set<Integer> gesehen = new LinkedHashSet<>();
        for (Eintrag eintrag : eintraege) {
            if (eintrag.fach() >= zeilen * BREITE) {
                throw new IllegalArgumentException("Fach " + eintrag.fach() + " liegt"
                        + " ausserhalb eines Menues mit " + zeilen + " Zeilen ("
                        + (zeilen * BREITE) + " Faecher). Im Spiel waere der Eintrag"
                        + " unsichtbar - und die Tafel sagt nicht, dass er fehlt.");
            }
            if (!gesehen.add(eintrag.fach())) {
                throw new IllegalArgumentException("Zwei Eintraege auf Fach "
                        + eintrag.fach() + ". Welcher gewinnt, entschiede die"
                        + " Reihenfolge der Liste - und der andere waere ein Befehl, den"
                        + " niemand mehr erreicht.");
            }
        }
    }


    /**
     * Das Hauptmenue - elf Eintraege in drei Zeilen.
     *
     * <p><b>Die Reihenfolge ist der Weg eines Neulings</b> und nicht die Reihenfolge der
     * Bauschritte: erst der eigene Grund (Parzelle), dann das eigene Geld, dann der
     * eigene Rang und Beruf; danach die Orte, an denen man handelt; am Ende die zwei
     * Auskuenfte. Plan 10 nennt das "ein sichtbares naechstes Ziel".
     *
     * <p><b>Die Gegenstaende sind absichtlich gewoehnlich</b>: ein Grasblock fuer Grund,
     * ein Goldbarren fuer Geld, eine Spitzhacke fuer Arbeit. Ein Spieler erkennt sie,
     * ohne lesen zu muessen - und wer etwas anderes will, aendert eine Zeile hier. In
     * die {@code config.yml} gehoert diese Liste erst, wenn es ein ZWEITES Menue gibt;
     * ein Einstellblock fuer genau eine Tafel waere eine Zusage, die heute niemand
     * einloest.
     *
     * <p>Faecher 10 bis 16 sind die Mitte der zweiten Zeile, 19 bis 22 die der dritten -
     * so steht nichts am Rand, und der Rahmen bleibt leer.
     */
    static final Menuetafel HAUPT = new Menuetafel(MenueSatz.TITEL, 3, List.of(
            new Eintrag(10, MenueSatz.PARZELLE_NAME, MenueSatz.PARZELLE_ERKLAERUNG,
                    "grass_block", "parzelle"),
            new Eintrag(11, MenueSatz.MARK_NAME, MenueSatz.MARK_ERKLAERUNG,
                    "gold_ingot", "mark"),
            new Eintrag(12, MenueSatz.RANG_NAME, MenueSatz.RANG_ERKLAERUNG,
                    "paper", "rang"),
            new Eintrag(13, MenueSatz.BERUF_NAME, MenueSatz.BERUF_ERKLAERUNG,
                    "iron_pickaxe", "beruf"),
            new Eintrag(14, MenueSatz.MARKT_NAME, MenueSatz.MARKT_ERKLAERUNG,
                    "emerald", "markt"),
            new Eintrag(15, MenueSatz.LADEN_NAME, MenueSatz.LADEN_ERKLAERUNG,
                    "chest", "laden"),
            new Eintrag(16, MenueSatz.FREISTADT_NAME, MenueSatz.FREISTADT_ERKLAERUNG,
                    "bricks", "freistadt"),
            new Eintrag(19, MenueSatz.WERDER_NAME, MenueSatz.WERDER_ERKLAERUNG,
                    "prismarine", "werder"),
            new Eintrag(20, MenueSatz.WOLKE_NAME, MenueSatz.WOLKE_ERKLAERUNG,
                    "white_wool", "wolke"),
            new Eintrag(21, MenueSatz.HILFE_NAME, MenueSatz.HILFE_ERKLAERUNG,
                    "book", "hilfe"),
            new Eintrag(22, MenueSatz.REGELN_NAME, MenueSatz.REGELN_ERKLAERUNG,
                    "written_book", "regeln")));

    /**
     * Der Gegenstandsname, wie er in einer Tafel steht - klein und ohne Rand.
     *
     * <p>Auf der REINEN Seite der Naht und nicht in {@link Menuehand} [21.11.2026]: Die
     * Rechnung ist eine Zeichenkette, und der Konstruktor des Eintrags ruft sie. Stand
     * sie in der Hand, loeste jede Tafel in jeder Probe die Klasseninitialisierung der
     * Bukkit-Haelfte aus.
     */
    static String artname(String name) {
        return name == null ? "" : name.trim().toLowerCase(Locale.ROOT);
    }

    /** Wie viele Faecher dieses Menue hat. */
    int faecher() {
        return zeilen * BREITE;
    }

    /**
     * Dieselbe Tafel, aber nur mit den Eintraegen, die dieser Leser tippen darf.
     *
     * <p><b>Der Riegel steht HIER und nicht in der Hand</b> [21.11.2026]. Die Hand legt
     * die gefilterte Tafel in den Halter, und die Wache liest sie von dort - damit geht
     * auch der KLICK durch diesen Riegel. Ein Fach, das leer geblieben ist, findet in
     * {@link #imFach} keinen Eintrag mehr, und niemand kann einen Befehl ausloesen, der
     * ihm nicht angeboten wurde. Haette der Filter in {@code Menuehand.oeffne} gestanden,
     * laege er hinter {@code Bukkit.createInventory} - unpruefbar, und die Wache braeuchte
     * einen zweiten Riegel fuer denselben Fall.
     *
     * <p>Die ZEILENZAHL bleibt: Ein Menue, das je nach Rechten schrumpft, verschiebe die
     * Faecher, und der Spieler fand seinen Eintrag jedes Mal an einer anderen Stelle.
     *
     * @param darf die Rechtefrage des Lesers ({@code wer::hasPermission})
     */
    Menuetafel fuerRechte(Rechtefrage darf) {
        if (darf == null) {
            throw new IllegalArgumentException("Ohne Rechtefrage gibt es keine Auswahl -"
                    + " eine fehlende Frage waere ein Menue, das alles anbietet");
        }
        List<Eintrag> erlaubt = new ArrayList<>();
        for (Eintrag eintrag : eintraege) {
            if (eintrag.recht().equals(Hilfeplan.JEDER) || darf.hat(eintrag.recht())) {
                erlaubt.add(eintrag);
            }
        }
        return new Menuetafel(titel, zeilen, erlaubt);
    }

    /**
     * Der Eintrag auf diesem Fach - oder leer.
     *
     * <p>Die eine Frage, die die Wache stellt. Ein Klick auf ein Fach ohne Eintrag ist
     * kein Fehler: Er wird abgesagt und es geschieht nichts.
     */
    Optional<Eintrag> imFach(int fach) {
        for (Eintrag eintrag : eintraege) {
            if (eintrag.fach() == fach) {
                return Optional.of(eintrag);
            }
        }
        return Optional.empty();
    }

    /** Jeder Befehl, den dieses Menue ausfuehren kann - fuer die Tafeln und den Start. */
    List<String> befehle() {
        List<String> gefunden = new ArrayList<>();
        for (Eintrag eintrag : eintraege) {
            if (eintrag.tut()) {
                gefunden.add(eintrag.befehl());
            }
        }
        return List.copyOf(gefunden);
    }
}
