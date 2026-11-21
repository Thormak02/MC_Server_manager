package de.freimarken.paper;

import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.DialogBase;
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
 * <p><b>Kein {@code DialogAction} am Knopf</b>, und das ist Absicht: Ein Knopf OHNE Tat
 * schliesst das Fenster und tut sonst nichts - genau das, was ein gelesener Text braucht.
 * Ein Knopf MIT Tat waere eine zweite Tuer in das Plugin, und die muesste ihre Rechte
 * selbst pruefen. Wenn ein spaeterer Schritt die ANNAHME der Regeln baut, ist das eine
 * {@code confirmation} mit zwei Knoepfen - und der eine davon fuehrt dann einen BEFEHL
 * aus, so wie es das Menue schon tut.
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
            if (!gemeldet) {
                gemeldet = true;
                protokoll.log(Level.WARNING, "Ein Formular liess sich nicht als Fenster"
                        + " zeigen - die Zeilen gehen stattdessen in den Chat. Der Grund"
                        + " steht unten; diese Meldung kommt nur beim ersten Mal.",
                        nichtDa);
            }
            return false;
        }
    }
}
