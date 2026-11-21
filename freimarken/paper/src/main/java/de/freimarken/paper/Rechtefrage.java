package de.freimarken.paper;

/**
 * Ob ein Leser ein Recht hat.
 *
 * <p><b>Eine eigene Schnittstelle und kein {@code CommandSender} im Rumpf:</b> So laeuft
 * jede Rechtelage in einer Probe durch - ohne Server, ohne Spieler, ohne Brigadier. Der
 * Aufrufer gibt {@code wer::hasPermission} herein, eine Probe eine Menge.
 *
 * <p><b>Sie steht in einer eigenen Datei, weil es ZWEI Verbraucher gibt</b>
 * [21.11.2026]: {@link HilfeBefehle#zeilen} laesst eine Zeile weg, deren Recht fehlt,
 * und {@link Menuetafel#fuerRechte} laesst das ganze Fach weg. Beide fragen dieselbe
 * Frage, und beide beantworten sie mit dem Recht aus {@link Hilfeplan} - eine Liste, die
 * einen Befehl anbietet, den der Spieler nicht ausfuehren darf, macht aus einer
 * fehlenden Erlaubnis einen scheinbaren Fehler.
 */
interface Rechtefrage {
    boolean hat(String recht);
}
