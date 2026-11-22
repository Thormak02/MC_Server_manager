package de.freimarken.paper;

import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.action.DialogAction;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;

/**
 * Die Paper-Haelfte eines Formulars: aus fertigen Zeilen wird ein Dialogfenster.
 *
 * <p><b>Diese Klasse ist die NAHT, und sie ist die einzige des Plugins ohne Zeugen.</b>
 * Jeder statische Einstieg der Dialog-API geht durch einen {@code ServiceLoader}
 * ({@code DialogInstancesProvider.instance()}), und auf dem Probenklassenpfad liegt keine
 * Umsetzung - eine Probe, die diese Methode ruft, stirbt im Klasseninitialisierer, bevor
 * ein Zeichen verglichen ist. {@code Bukkit.setServer} hilft dort NICHT, weil der Halter
 * nicht am Server haengt. Gemessen am 21.11.2026 im API-Jar 26.3.231.
 *
 * <p><b>Darum nimmt sie FERTIGE Components und keine Saetze.</b> Wer ihr Schluessel
 * hereingaebe, haette das Holen in die eine Klasse gelegt, die keine Probe durchlaeuft -
 * und {@code VorlageTest} 10i muesste jeden dieser Saetze als blinde Stelle
 * aufschreiben. So nennt der Befehl seine Saetze beim Namen, und hier steht nur noch
 * Verdrahtung: keine Entscheidung, keine Rechnung, kein Wort.
 *
 * <p><b>Die Breite des Knopfes steht hier und nicht in der Datei.</b> Sie ist kein Text
 * und keine Spielzahl, sondern das Mass des Fensters: 150 von 200 moeglichen Punkten ist
 * die Breite, die der Vanilla-Dialog fuer einen einzelnen Knopf benutzt.
 *
 * <p><b>ZWEI Methoden, zwei Dialogarten.</b> {@link #zeige} ist eine {@code notice} mit
 * einem Knopf OHNE Tat: Er schliesst das Fenster und tut sonst nichts - genau das, was ein
 * gelesener Text braucht. {@link #frage} ist eine {@code confirmation} mit zwei Knoepfen,
 * und der eine davon fuehrt einen BEFEHL aus [21.11.2026].
 *
 * <p><b>Ein Knopf mit Tat ist eine zweite Tuer in das Plugin</b>, und darum traegt er
 * keine eigene Entscheidung: Er tippt einen Befehl, so wie das Menue es tut, und jener
 * Befehl prueft seine Rechte selbst. Ein Knopf, der hier etwas gewaehrte, waere eine Tuer
 * ohne Rechtefrage - und sie liesse sich von jedem oeffnen, dem ein Fenster geschickt
 * wurde.
 */
final class Formularhand {

    /** Die Breite eines einzelnen Knopfes in einem Dialog - das Mass des Fensters. */
    private static final int KNOPFBREITE = 150;

    private final Logger protokoll;

    /** Ob der Ausfall schon im Protokoll steht - gemeldet wird er genau einmal. */
    private boolean gemeldet;

    Formularhand(Logger protokoll) {
        if (protokoll == null) {
            throw new IllegalArgumentException("Ohne Protokoll keine Hand");
        }
        this.protokoll = protokoll;
    }

    /**
     * Baut das Fenster und zeigt es - oder sagt, dass es nicht ging.
     *
     * <p><b>Gefangen wird ein Fehler auf dem SERVER</b> - eine Fassung ohne die
     * Dialog-Registrierung ({@code NoClassDefFoundError}) oder ein Wurf beim Bauen. Dann
     * schreibt der Aufrufer die Zeilen in den Chat.
     *
     * <p><b>Einen alten CLIENT faengt das NICHT</b> [21.11.2026]. {@code showDialog} ist
     * ein Senden und kein Fragen: Das Paket geht hinaus, eine Bruecke wie ViaProxy
     * verwirft es bei der Uebersetzung nach unten, und der Server erfaehrt davon nichts -
     * diese Methode gibt {@code true}, und der Spieler sieht nichts. Hier stand bis zum
     * 21.11.2026 das Gegenteil zugesagt. Wer das einloesen will, muss die Dialogfaehigkeit
     * VOR dem Senden am Spieler erfragen, und zwar gemessen; bis dahin ist der Ausweg
     * {@code /regeln chat}, und darum nennt die Beschreibung des Befehls ihn.
     *
     * <p><b>Gemeldet wird genau einmal</b>, mit Stapelabzug: Beim ersten Mal will der
     * Betreiber wissen, warum seine Spieler kein Fenster sehen; beim hundertsten Mal
     * waere es eine Protokollflut fuer einen Zustand, der sich nicht aendert.
     *
     * @return ob das Fenster offen ist
     */
    boolean zeige(Player wer, Component titel, List<Component> text, Component knopf) {
        try {
            List<DialogBody> koerper = new ArrayList<>();
            for (Component zeile : text) {
                koerper.add(DialogBody.plainMessage(zeile));
            }
            ActionButton schliessen = ActionButton.create(knopf, null, KNOPFBREITE, null);
            wer.showDialog(Dialog.create(bauer -> bauer.empty()
                    .base(DialogBase.builder(titel).body(koerper).build())
                    .type(DialogType.notice(schliessen))));
            return true;
        } catch (RuntimeException | LinkageError nichtDa) {
            // LinkageError mit gefangen: Fehlt die Dialog-API einer Serverfassung ganz,
            // ist es kein RuntimeException, sondern ein NoClassDefFoundError - und auch
            // der darf den Befehl nicht mitreissen.
            melde(nichtDa);
            return false;
        }
    }

    /**
     * Baut das Fenster mit ZWEI Knoepfen und zeigt es - oder sagt, dass es nicht ging.
     *
     * <p>Eine {@code confirmation}: Der erste Knopf fuehrt {@code befehl} aus, der zweite
     * schliesst nur. Welcher welcher ist, entscheidet die Reihenfolge der Argumente von
     * {@code DialogType.confirmation(ja, nein)} - und das ist der einzige Unterschied
     * zwischen Annehmen und Abbrechen. Ein vertauschtes Paar waere ein Fenster, das auf
     * "Spaeter" zustimmt; die Probe des Aufrufers haelt die Reihenfolge fest.
     *
     * <p><b>{@code DialogAction.commandTemplate} und kein eigener Rumpf</b>: Der Knopf
     * tippt fuer den Spieler, genau wie das Menue, und der Befehl prueft danach seine
     * Rechte selbst. Ein {@code customClick} mit eigenem Rumpf waere eine Entscheidung in
     * der einen Klasse ohne Zeugen.
     *
     * <p><b>Der Befehl steht OHNE Schraegstrich da.</b> Dieselbe Form wie bei
     * {@code performCommand} im Menue - und derselbe Riegel: Wer hier einen mit
     * Schraegstrich hereingibt, bekommt ihn abgewiesen, bevor ein Fenster aufgeht.
     *
     * <p>Gefangen wird dasselbe wie in {@link #zeige} und mit derselben Einschraenkung:
     * ein Fehler auf dem SERVER. Einen alten CLIENT faengt das nicht - der Aufrufer
     * braucht also auch hier einen Weg ohne Fenster.
     *
     * @param befehl der Befehl, den der erste Knopf ausfuehrt - OHNE Schraegstrich
     * @return ob das Fenster offen ist
     */
    boolean frage(Player wer, Component titel, List<Component> text, Component jaKnopf,
            Component neinKnopf, String befehl) {
        if (befehl == null || befehl.isBlank() || befehl.startsWith("/")) {
            throw new IllegalArgumentException("Der Befehl eines Knopfes steht OHNE"
                    + " Schraegstrich da und darf nicht leer sein - commandTemplate"
                    + " nimmt ihn so: " + befehl);
        }
        try {
            List<DialogBody> koerper = new ArrayList<>();
            for (Component zeile : text) {
                koerper.add(DialogBody.plainMessage(zeile));
            }
            ActionButton ja = ActionButton.create(jaKnopf, null, KNOPFBREITE,
                    DialogAction.commandTemplate(befehl));
            ActionButton nein = ActionButton.create(neinKnopf, null, KNOPFBREITE, null);
            wer.showDialog(Dialog.create(bauer -> bauer.empty()
                    .base(DialogBase.builder(titel).body(koerper).build())
                    .type(DialogType.confirmation(ja, nein))));
            return true;
        } catch (RuntimeException | LinkageError nichtDa) {
            melde(nichtDa);
            return false;
        }
    }

    /**
     * Die eine Protokollzeile fuer beide Fenster - genau einmal.
     *
     * <p>Beim ersten Mal will der Betreiber wissen, warum seine Spieler kein Fenster
     * sehen; beim hundertsten Mal waere es eine Protokollflut fuer einen Zustand, der
     * sich nicht aendert. EINE Stelle fuer beide Methoden: Zwei Zaehler meldeten
     * denselben Ausfall zweimal, und zwei Texte sagten dasselbe verschieden.
     */
    private void melde(Throwable nichtDa) {
        if (!gemeldet) {
            gemeldet = true;
            protokoll.log(Level.WARNING, "Ein Formular liess sich nicht als Fenster"
                    + " zeigen - die Zeilen gehen stattdessen in den Chat. Der Grund"
                    + " steht unten; diese Meldung kommt nur beim ersten Mal.", nichtDa);
        }
    }
}
