package de.freimarken.paper;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

/**
 * Die Quelldateien dieses Moduls - fuer die Proben, die den Quelltext selbst lesen.
 *
 * <p>Drei Proben tun das, und alle drei aus demselben Grund: Sie bewachen eine Zusage, die
 * kein Typ traegt - "Farbe nur noch hier", "gesendet nur noch hier", "kein geteiltes
 * Zahlformat". Eine solche Probe ist nur so stark, wie sie wirklich Dateien findet; genau
 * deshalb gibt es daneben {@code GrenzeTest.quellenWurdenGefunden}, die rot wird, wenn
 * diese Suche leer laeuft.
 *
 * <p>Das Arbeitsverzeichnis einer Gradle-Probe ist das Projektverzeichnis, also
 * {@code .../freimarken/paper}. Gesucht wird trotzdem aufwaerts: Wer die Proben aus dem
 * Wurzelprojekt startet, soll keine leere, gruene Suite bekommen.
 */
final class Quellen {

    private static final String UNTERPFAD = "src/main/java/de/freimarken/paper";

    private Quellen() {
    }

    /** Der Ordner mit den Quelldateien, oder {@code null}, wenn er nicht gefunden wurde. */
    static Path ordner() {
        Path hier = Path.of("").toAbsolutePath();
        for (Path kandidat = hier; kandidat != null; kandidat = kandidat.getParent()) {
            Path versuch = kandidat.resolve(UNTERPFAD);
            if (Files.isDirectory(versuch)) {
                return versuch;
            }
            Path ueberPaper = kandidat.resolve("paper").resolve(UNTERPFAD);
            if (Files.isDirectory(ueberPaper)) {
                return ueberPaper;
            }
        }
        return null;
    }

    /** Alle {@code .java}-Dateien des Moduls, nach Namen geordnet. */
    static List<Path> alle() {
        Path ordner = ordner();
        if (ordner == null) {
            return List.of();
        }
        try (Stream<Path> lauf = Files.list(ordner)) {
            List<Path> dateien = new ArrayList<>(lauf
                    .filter(pfad -> pfad.getFileName().toString().endsWith(".java"))
                    .sorted(Comparator.comparing(pfad -> pfad.getFileName().toString()))
                    .toList());
            return List.copyOf(dateien);
        } catch (IOException nichtLesbar) {
            throw new UncheckedIOException(nichtLesbar);
        }
    }

    /** Der Text einer Quelldatei. */
    static String text(Path datei) {
        try {
            return Files.readString(datei);
        } catch (IOException nichtLesbar) {
            throw new UncheckedIOException(nichtLesbar);
        }
    }

    /** Wie oft eine Zeichenfolge in einer Datei steht. */
    static int zaehle(String text, String folge) {
        int anzahl = 0;
        for (int stelle = text.indexOf(folge); stelle >= 0;
                stelle = text.indexOf(folge, stelle + folge.length())) {
            anzahl++;
        }
        return anzahl;
    }
}
