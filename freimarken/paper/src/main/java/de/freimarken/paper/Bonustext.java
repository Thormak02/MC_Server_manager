package de.freimarken.paper;

import de.freimarken.kern.Bonusregel;
import de.freimarken.kern.Bonusstand;
import de.freimarken.kern.Bonuswerte;
import net.kyori.adventure.text.Component;

/**
 * Die Zeilen des Tagesbonus - reine Rechnung, kein Bukkit, kein Senden.
 *
 * <p><b>Das ist DIE NAHT dieses Bereichs</b>, wie {@link Markttext} fuer den Bundesmarkt:
 * Wer eine Zeile braucht, holt sie hier und schickt sie nur hinaus. Dadurch ist jedes
 * Wort, jede Farbe und jede Verzweigung ohne Server pruefbar.
 *
 * <p>Kein Wort steht hier: Der Wortlaut kommt aus {@code sprache-vorlage.yml} ueber
 * {@link BonusSatz}. Was hier bleibt, ist die WAHL - welcher der drei Zahlungssaetze
 * zutrifft und welcher der drei Standsaetze. Das weiss nur das Programm.
 */
final class Bonustext {

    private Bonustext() {
    }

    /**
     * Die Nachricht ueber eine Zahlung - genau EINE Zeile.
     *
     * <p>Die Verzweigung ist die ganze Aufgabe dieser Methode:
     * <ul>
     *   <li>Serie 1 - der erste Bonus oder ein Neuanfang. Nennt, was MORGEN kaeme, denn
     *       das ist der Grund, morgen wiederzukommen.
     *   <li>Bestmarke - dieser Tag ist seine laengste Serie.
     *   <li>sonst der fortgesetzte Tag.
     * </ul>
     * Die Reihenfolge der Pruefungen ist nicht beliebig: Der erste Tag ist IMMER auch die
     * Bestmarke, wenn der Spieler noch nie eine Serie hatte. Stuende die Bestmarke zuerst,
     * laese ein ganz neuer Spieler "Bestmarke: 1 Tag" - und das ist keine Bestmarke,
     * sondern sein erster Tag.
     */
    static Component zahlungZeile(Sprache sprache, Tagesbonus.Zahlung zahlung,
                                  Bonuswerte werte) {
        if (zahlung.serie() == 1) {
            return sprache.eineZeile(BonusSatz.GEZAHLT_ERSTER, Einsatz
                    .von("betrag", sprache.betrag(zahlung.betrag()))
                    .mit("morgen", sprache.betrag(Bonusregel.betrag(2, werte))));
        }
        BonusSatz satz = zahlung.istBestmarke()
                ? BonusSatz.GEZAHLT_BESTMARKE
                : BonusSatz.GEZAHLT_SERIE;
        return sprache.eineZeile(satz, Einsatz
                .von("betrag", sprache.betrag(zahlung.betrag()))
                .mit("serie", String.valueOf(zahlung.serie())));
    }

    /**
     * Die Zeile fuer {@code /mark} - was die Serie gerade wert ist.
     *
     * <p>Drei Faelle, und jeder braucht seinen Satz:
     * <ul>
     *   <li>Heute schon bekommen - mit der laufenden Serie, der laengsten und dem Betrag
     *       von morgen. Das ist der Normalfall, denn ohne eingestellte Mindestzeit faellt
     *       der Bonus beim Einloggen.
     *   <li>Heute noch offen und es wird auf taetige Minuten gewartet - mit den Minuten,
     *       die noch fehlen. Ohne diesen Satz liest ein Spieler nichts von einem Bonus
     *       und haelt ihn fuer abgeschafft.
     *   <li>Heute noch offen und es wird auf nichts gewartet - der Bonus steht an, oder
     *       die Kasse hat ihn nicht angenommen.
     * </ul>
     *
     * @param schonGezahlt        ob der Bonus heute schon gefallen ist. Danach gilt die
     *                            Serie des Standes; davor die, die HEUTE noch kommen
     *                            wuerde - und das sind zwei verschiedene Zahlen, wenn die
     *                            Serie gestern gerissen ist
     * @param serieHeute          der Serientag, den heute brachte oder braechte. Kommt
     *                            von {@code Serienregel} und wird hier NICHT gerechnet:
     *                            Ob heute fortsetzt oder neu beginnt, haengt an der
     *                            Luecke zum letzten Tag, und die braucht die eingestellte
     *                            Zone
     * @param fehlendeMinuten     wie viele taetige Minuten noch fehlen; ohne eingestellte
     *                            Mindestzeit ohne Bedeutung
     */
    static Component standZeile(Sprache sprache, Bonusstand stand, Bonuswerte werte,
                                boolean schonGezahlt, int serieHeute,
                                long fehlendeMinuten) {
        if (schonGezahlt) {
            return sprache.eineZeile(BonusSatz.STAND_SERIE, Einsatz
                    .von("serie", String.valueOf(stand.serie()))
                    .mit("laengste", String.valueOf(stand.laengsteSerie()))
                    .mit("morgen", sprache.betrag(
                            Bonusregel.betrag(stand.serie() + 1, werte))));
        }
        String betrag = sprache.betrag(Bonusregel.betrag(Math.max(1, serieHeute), werte));
        if (werte.wartetAufTaetigkeit() && fehlendeMinuten > 0L) {
            return sprache.eineZeile(BonusSatz.STAND_WARTET, Einsatz
                    .von("minuten", String.valueOf(fehlendeMinuten))
                    .mit("betrag", betrag)
                    .mit("serie", String.valueOf(Math.max(1, serieHeute))));
        }
        return sprache.eineZeile(BonusSatz.STAND_OFFEN, Einsatz
                .von("betrag", betrag)
                .mit("serie", String.valueOf(Math.max(1, serieHeute))));
    }
}
