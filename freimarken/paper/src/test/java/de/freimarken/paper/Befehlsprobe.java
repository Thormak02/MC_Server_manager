package de.freimarken.paper;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.Suggestion;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import net.kyori.adventure.text.Component;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * Ein getippter Befehl - durch den echten Befehlsbaum, mit dem echten Zweig dahinter.
 *
 * <p><b>Der vierte Stellvertreter, und der erste, der einen BEFEHL durchlaeuft.</b> Bis
 * hierher sind die Ereigniszweige durchlaufen ({@code SchutzwacheTest},
 * {@code AnkunftswacheTest}, {@code PortalwacheTest}); die Befehle trugen weiter die
 * Zaehltafeln von {@code GrenzeTest} allein. Deren Javadoc sagte den Grund selbst:
 * {@code AufrufstelleTest} ruft die reinen Methoden auf und sieht den Zweig nie, weil ein
 * Befehl ohne Server nicht auszufuehren war.
 *
 * <p><b>Gemessen ist er das doch</b>, und zwar ohne jede Nachbildung von Brigadier:
 * {@code Commands.literal} und {@code Commands.argument} sind reine Weiterleitungen an
 * {@code LiteralArgumentBuilder} beziehungsweise {@code RequiredArgumentBuilder} - im
 * Bytecode ein {@code invokestatic} und ein {@code areturn}, kein Blick auf den Server.
 * Nur {@code Commands.restricted} und {@code unrestricted} gehen ueber eine Bruecke, die
 * ein Server mitbringt, und die benutzt dieses Projekt an keiner Stelle. Also genuegt ein
 * eigener {@link CommandDispatcher}, in den der gebaute Knoten gehaengt wird - derselbe
 * Knoten, den auch Paper bekommt.
 *
 * <p><b>Was dadurch pruefbar wird, konnte vorher keine Tafel sagen:</b> ob ein Zweig
 * ueberhaupt erreichbar ist, ob {@code requires} das Richtige verlangt, welcher von
 * mehreren Unterbefehlen anspricht, und was ein Spieler am Ende liest. Eine Zahltafel
 * zaehlt Aufrufe im Quelltext; sie weiss nicht, ob der Befehl, den ein Mensch tippt, dort
 * ankommt.
 */
final class Befehlsprobe {

    private final CommandDispatcher<CommandSourceStack> baum = new CommandDispatcher<>();

    /**
     * Was die Konsole zu lesen bekommt - als {@code &}-Bild.
     *
     * <p>Nicht jeder Zweig ist spielerpflichtig: {@code /freimarken} antwortet
     * ausdruecklich auch dem Betreiber auf der Konsole, und seine
     * Verwaltungszweige tun nichts anderes. Ohne diese Mitschrift waere davon
     * nur zu sehen, DASS der Zweig lief.
     */
    private final List<Component> konsoleLas = new ArrayList<>();

    /**
     * Haengt die gebauten Knoten in einen frischen Baum - wie die Anmeldung im Spiel.
     *
     * <p>Mehrere Knoten auf einmal, weil {@code /parzelle} und {@code /mark} aus derselben
     * Datei kommen und eine Probe beide braucht.
     */
    Befehlsprobe(List<LiteralCommandNode<CommandSourceStack>> knoten) {
        knoten.forEach(baum.getRoot()::addChild);
    }

    /**
     * Tippt den Befehl als dieser Spieler.
     *
     * @return was Brigadier zurueckgibt - {@code 1} fuer einen ausgefuehrten Zweig
     * @throws CommandSyntaxException wenn der Befehl so nicht existiert ODER wenn
     *         {@code requires} ihn fuer diese Quelle versteckt. Brigadier unterscheidet
     *         das NICHT, und genau darin steckt die bekannte Unschoenheit: Auf der Konsole
     *         antwortet ein spielerpflichtiger Befehl wortgleich wie ein Befehl, den es
     *         nicht gibt.
     */
    int tippe(String befehl, Spielerprobe spieler) throws CommandSyntaxException {
        return baum.execute(befehl, alsSpieler(spieler.spieler()));
    }

    /**
     * Was im Vorschlagsfenster steht, waehrend dieser Spieler tippt.
     *
     * <p><b>Die dritte Sache, die keine Tafel sagen kann.</b> Ein Vorschlagsgeber
     * haengt an EINEM Argumentknoten; haengt er am falschen, bleibt das Fenster leer
     * oder zeigt die Welten statt der Staedte. Im Quelltext sieht beides gleich aus.
     *
     * <p>{@code join} ohne Wartezeit, und das ist kein Leichtsinn: Die Vorschlaege
     * dieses Projekts rechnen auf dem rufenden Faden und geben ein fertiges Ergebnis
     * zurueck. Haengt es doch, haengt die Probe - und das ist die richtige Antwort,
     * denn im Spiel haengt dann der Spieler.
     *
     * @param angefangen der Befehl, so weit getippt - das letzte Wort darf halb sein
     */
    List<String> vorschlaege(String angefangen, Spielerprobe spieler) {
        return baum.getCompletionSuggestions(
                        baum.parse(angefangen, alsSpieler(spieler.spieler())))
                .join().getList().stream().map(Suggestion::getText).toList();
    }

    /** Die Zeilen, die die Konsole gelesen hat - in der Reihenfolge. */
    List<String> konsoleBild() {
        return konsoleLas.stream().map(Sprachprobe::bild).toList();
    }

    /** Tippt den Befehl auf der Konsole - ohne ausfuehrenden Spieler. */
    int tippeAufDerKonsole(String befehl) throws CommandSyntaxException {
        return baum.execute(befehl, alsKonsole());
    }

    /**
     * Die Quelle mit einem Spieler dahinter.
     *
     * <p>{@code getExecutor} UND {@code getSender} geben denselben Spieler, und das ist
     * nicht Bequemlichkeit: Im Spiel sind sie verschieden, sobald ein Befehl ueber
     * {@code /execute as} laeuft, und die Zweige dieses Projekts fragen darum
     * ausdruecklich den EXECUTOR. Wer das verwechselt, laesst einen Befehl auf einen
     * anderen Spieler wirken als den, der ihn tippt. Eine Probe, die beide verschieden
     * besetzt, gehoert dazu - sie ist noch nicht gebaut, und darum steht das hier.
     */
    private static CommandSourceStack alsSpieler(Player spieler) {
        return (CommandSourceStack) Proxy.newProxyInstance(
                Befehlsprobe.class.getClassLoader(),
                new Class<?>[] {CommandSourceStack.class},
                (eigen, methode, werte) -> switch (methode.getName()) {
                    case "getExecutor", "getSender" -> spieler;
                    case "toString" -> "Befehlsquelle " + spieler;
                    case "hashCode" -> System.identityHashCode(eigen);
                    case "equals" -> eigen == (werte == null ? null : werte[0]);
                    default -> throw new UnsupportedOperationException(
                            "Befehlsprobe beantwortet CommandSourceStack."
                                    + methode.getName() + " nicht. Der Zweig, den du"
                                    + " tippst, braucht sie - also gehoert sie hierher,"
                                    + " mit einer Antwort, die die Probe mitgibt.");
                });
    }

    /**
     * Die Quelle ohne Spieler - so sieht die Konsole aus.
     *
     * <p>{@code getExecutor} gibt {@code null}, und das ist der ganze Punkt: Jeder
     * spielerpflichtige Zweig prueft {@code instanceof Player}, und {@code null} ist das
     * nie. {@code getSender} gibt einen Sender, der kein Spieler ist.
     */
    private CommandSourceStack alsKonsole() {
        CommandSender konsole = (CommandSender) Proxy.newProxyInstance(
                Befehlsprobe.class.getClassLoader(),
                new Class<?>[] {CommandSender.class},
                (eigen, methode, werte) -> switch (methode.getName()) {
                    case "getName" -> "CONSOLE";
                    case "sendMessage" -> {
                        konsoleLas.add((Component) werte[0]);
                        yield null;
                    }
                    case "hasPermission", "isOp" -> true;
                    case "toString" -> "Konsole";
                    case "hashCode" -> System.identityHashCode(eigen);
                    case "equals" -> eigen == (werte == null ? null : werte[0]);
                    default -> throw new UnsupportedOperationException(
                            "Befehlsprobe beantwortet CommandSender." + methode.getName()
                                    + " nicht");
                });
        return (CommandSourceStack) Proxy.newProxyInstance(
                Befehlsprobe.class.getClassLoader(),
                new Class<?>[] {CommandSourceStack.class},
                (eigen, methode, werte) -> switch (methode.getName()) {
                    case "getExecutor" -> null;
                    case "getSender" -> konsole;
                    case "toString" -> "Befehlsquelle Konsole";
                    case "hashCode" -> System.identityHashCode(eigen);
                    case "equals" -> eigen == (werte == null ? null : werte[0]);
                    default -> throw new UnsupportedOperationException(
                            "Befehlsprobe beantwortet CommandSourceStack."
                                    + methode.getName() + " fuer die Konsole nicht");
                });
    }
}
