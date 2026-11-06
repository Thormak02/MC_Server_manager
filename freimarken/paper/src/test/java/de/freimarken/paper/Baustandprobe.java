package de.freimarken.paper;

import io.papermc.paper.ServerBuildInfo;
import java.time.Instant;
import java.util.Optional;
import java.util.OptionalInt;
import net.kyori.adventure.key.Key;

/**
 * Der Baustand, den {@code Bukkit.setServer} verlangt - fuer die Proben, nicht fuer einen
 * Server.
 *
 * <p><b>Warum es diese Klasse gibt.</b> {@code Bukkit.setServer} schreibt eine Zeile mit
 * der Serverfassung ins Protokoll und holt sie bei {@code ServerBuildInfo.buildInfo()}.
 * Jene Methode sucht ihren einzigen Anbieter ueber {@link java.util.ServiceLoader}; in
 * einem echten Server bringt Paper ihn mit, in einer Probe gibt es keinen, und
 * {@code buildInfo()} wirft dann {@code NoSuchElementException: No value present} - noch
 * bevor der Stellvertreter ueberhaupt steht. Gemessen am 06.11.2026 war das die erste
 * Huerde ueberhaupt.
 *
 * <p>Angemeldet wird sie als Dienst in
 * {@code src/test/resources/META-INF/services/io.papermc.paper.ServerBuildInfo} - also nur
 * im Probenpfad. In die ausgelieferte Jar kommt sie nicht, und ein echter Server sieht sie
 * nie.
 *
 * <p><b>Die Werte sind erkennbar unecht, und das ist Absicht.</b> Eine Probe, die sich auf
 * eine Fassungsnummer von hier stuetzt, prueft den Stellvertreter und nicht das Programm.
 * Wer eine echte Fassung braucht, gibt sie als Wert in die Naht hinein - so machen es
 * {@code SendewegTest} 33am und {@code BildGleichTest} fuer {@code stand.version}.
 */
public final class Baustandprobe implements ServerBuildInfo {

    /** Pflicht fuer den {@link java.util.ServiceLoader}: ein Konstruktor ohne Werte. */
    public Baustandprobe() {
    }

    @Override
    public Key brandId() {
        return Key.key("freimarken", "probe");
    }

    @Override
    public boolean isBrandCompatible(Key andere) {
        return brandId().equals(andere);
    }

    @Override
    public String brandName() {
        return "Freimarken-Probe";
    }

    @Override
    public String minecraftVersionId() {
        return "1.21.4";
    }

    @Override
    public String minecraftVersionName() {
        return "1.21.4";
    }

    @Override
    public OptionalInt buildNumber() {
        return OptionalInt.empty();
    }

    @Override
    public Instant buildTime() {
        return Instant.EPOCH;
    }

    @Override
    public Optional<String> gitBranch() {
        return Optional.empty();
    }

    @Override
    public Optional<String> gitCommit() {
        return Optional.empty();
    }

    @Override
    public String asString(StringRepresentation form) {
        return "Freimarken-Probe (" + form + ")";
    }
}
