package de.freimarken.paper;

import de.freimarken.kern.Wechselbuch;
import de.freimarken.kern.Wechselwerte;
import java.util.ArrayList;
import java.util.List;
import net.kyori.adventure.text.Component;

/**
 * Die Zeilen der Wechselstube - reine Rechnung, kein Bukkit, kein Senden.
 *
 * <p>Dieselbe Naht wie {@link Rangtext} und {@link Bonustext}: Wer eine Zeile braucht,
 * holt sie hier und schickt sie nur hinaus. Dadurch ist jedes Wort, jede Farbe und jede
 * Verzweigung ohne Server pruefbar - und zwar nicht nur, DASS gesendet wird, sondern WAS.
 *
 * <p>Kein Wort steht hier: Der Wortlaut kommt aus {@code sprache-vorlage.yml} ueber
 * {@link MarkSatz}. Was hier bleibt, ist die WAHL - welcher Satz zu welchem Bescheid
 * gehoert, und welche Zahl in welchen Platzhalter.
 *
 * <p><b>Jede Zahl geht durch die Methode ihrer Waehrung</b>, {@link Sprache#betrag} oder
 * {@link Sprache#goldmark}. Beide nehmen einen {@code long}, und genau darum ist die
 * Verwechslung hier das Naheliegende: Eine Zeile mit "12 Mark", wo einer 12 Goldmark hat,
 * ist beim Kurs des Plans das Tausendfache daneben. Die Proben vergleichen darum nicht nur
 * Zahlen, sondern das fertige Bild.
 */
final class Tauschtext {

    private Tauschtext() {
    }

    /**
     * Die Tafel: Kopf, Stand, Kurse, der Wechseldeckel und die Hilfszeile.
     *
     * <p>Fuenf Zeilen in EINER Liste und nicht fuenf einzelne Sendungen - dieselbe Lehre
     * wie bei {@code /playtime}: Eine einzeln gesendete Zeile faellt bei einem Umbau
     * heraus, ohne dass eine Probe rot wird.
     *
     * <p>Die vierte Zeile hat zwei Gestalten, und die Wahl ist der Grund, warum diese
     * Methode mehr tut als Zeilen aneinanderzuhaengen: Ist der Rueckweg zu, steht dort
     * nicht "noch 0 moeglich" - das waere ein Deckel, der heute erreicht ist, und morgen
     * waere er wieder frei. Ein geschlossener Rueckweg ist morgen auch zu.
     */
    static List<Component> tafelZeilen(Sprache sprache, Wechselbuch.Auskunft auskunft) {
        List<Component> zeilen = new ArrayList<>(sprache.bau(MarkSatz.TAUSCH_KOPF, 1L,
                Einsatz.keine()));
        zeilen.addAll(standUndKurse(sprache, auskunft));
        zeilen.addAll(sprache.bau(MarkSatz.TAUSCH_HILFE, 1L, Einsatz.keine()));
        return List.copyOf(zeilen);
    }

    /**
     * Stand, Kurse und der sichtbare Wechseldeckel - die drei Zeilen in der Mitte.
     *
     * <p><b>Warum sie eine eigene Methode sind</b> [20.11.2026, Teil G]: Das Muenzhaus
     * zeigt genau diese drei Zeilen auch - Plan 2.1 verlangt "Wechseldeckel sichtbar" an
     * DEM Ort, und das Muenzhaus ist der Ort. Abgeschrieben waere es eine zweite Fassung
     * derselben Auskunft; mit eigenen Saetzen daneben waeren es zwei Wortlaute fuer eine
     * Zahl, und der Betreiber aenderte eines Tages nur einen davon.
     *
     * <p>Die dritte Zeile hat zwei Gestalten, und die Wahl ist der Grund, warum diese
     * Methode mehr tut als Zeilen aneinanderzuhaengen - siehe {@link #tafelZeilen}.
     */
    static List<Component> standUndKurse(Sprache sprache, Wechselbuch.Auskunft auskunft) {
        List<Component> zeilen = new ArrayList<>(sprache.bau(MarkSatz.TAUSCH_STAND, 1L,
                Einsatz.von("betrag", sprache.goldmark(auskunft.goldmark()))));
        zeilen.addAll(sprache.bau(MarkSatz.TAUSCH_KURSE, 1L,
                Einsatz.von("kauf", sprache.betrag(auskunft.werte().markJeGoldmark()))
                        .mit("verkauf",
                                sprache.betrag(auskunft.werte().markZurueck()))));
        if (auskunft.werte().rueckwegOffen()) {
            zeilen.addAll(sprache.bau(MarkSatz.TAUSCH_FREI, 1L,
                    Einsatz.von("anzahl", sprache.goldmark(auskunft.nochMoeglich()))
                            .mit("betrag", sprache.betrag(
                                    auskunft.werte().freiHeute(auskunft.heuteSchon())))));
        } else {
            zeilen.addAll(sprache.bau(MarkSatz.TAUSCH_ZU, 1L, Einsatz.keine()));
        }
        return List.copyOf(zeilen);
    }

    /**
     * Die Antwort auf einen Tausch - je Bescheid eine eigene.
     *
     * <p><b>Ein Schalter ohne {@code default}</b>, und das ist der Riegel an dieser
     * Stelle: Ein Schalterausdruck ueber eine Aufzaehlung muss vollstaendig sein, sonst
     * uebersetzt er nicht. Wer dem {@link Wechselbuch.Bescheid} einen achten Wert
     * hinzufuegt, wird vom Uebersetzer gefragt, was der Spieler dann liest - und bekommt
     * keine stille Vorgabe, die ihm eine falsche Zeile zeigt.
     *
     * <p>Der Deckel hat ZWEI Saetze, und die Wahl dazwischen steht hier: Wer heute noch
     * drei Goldmark tauschen darf, soll die Drei lesen; wer keine mehr darf, soll nicht
     * "nur noch 0" lesen. Das ist derselbe Unterschied, den {@code Besitzbuch} zwischen
     * "noch frei" und "am Limit" macht.
     *
     * @param werte die GELTENDEN Kurse - hereingegeben und nicht hier geholt, wie bei
     *              {@link Rangtext#aufstiegZeilen}: So bleibt diese Rechnung ohne
     *              Wirtschaftsstand prueffaehig
     */
    static List<Component> entscheidZeilen(Sprache sprache, Wechselwerte werte,
                                           Wechselbuch.Entscheid entscheid) {
        return switch (entscheid.bescheid()) {
            case GEKAUFT -> sprache.bau(MarkSatz.TAUSCH_GEKAUFT, 1L,
                    Einsatz.von("goldmark", sprache.goldmark(entscheid.anzahl()))
                            .mit("mark", sprache.betrag(entscheid.mark()))
                            .mit("stand", sprache.goldmark(entscheid.stand())));
            case VERKAUFT -> sprache.bau(MarkSatz.TAUSCH_VERKAUFT, 1L,
                    Einsatz.von("goldmark", sprache.goldmark(entscheid.anzahl()))
                            .mit("mark", sprache.betrag(entscheid.mark()))
                            .mit("stand", sprache.goldmark(entscheid.stand())));
            case ZU_WENIG_MARK -> sprache.bau(MarkSatz.TAUSCH_ZU_WENIG_MARK, 1L,
                    Einsatz.von("preis", sprache.betrag(entscheid.mark()))
                            .mit("fehlt", sprache.betrag(entscheid.fehlt())));
            case ZU_WENIG_GOLDMARK -> sprache.bau(MarkSatz.TAUSCH_ZU_WENIG_GOLDMARK, 1L,
                    Einsatz.von("anzahl", sprache.goldmark(entscheid.anzahl()))
                            .mit("stand", sprache.goldmark(entscheid.stand())));
            case RUECKWEG_ZU -> sprache.bau(MarkSatz.TAUSCH_ZU, 1L, Einsatz.keine());
            case DECKEL_ERREICHT -> deckelZeilen(sprache, werte, entscheid);
            case GESTOERT -> sprache.bau(MarkSatz.TAUSCH_GESTOERT, 1L, Einsatz.keine());
        };
    }

    /**
     * Die zwei Gestalten der Deckelabsage.
     *
     * <p>Die Markzahl kommt aus dem KURS und nicht aus dem Entscheid: Dessen
     * {@code mark()} nennt, was der Wunsch gebracht haette - und das ist hier gerade die
     * Zahl, die nicht geht. Wer sie einsetzte, schriebe dem Spieler die abgelehnte Summe
     * hin, als waere sie die erlaubte.
     */
    private static List<Component> deckelZeilen(Sprache sprache, Wechselwerte werte,
                                                Wechselbuch.Entscheid entscheid) {
        if (entscheid.nochMoeglich() <= 0L) {
            return sprache.bau(MarkSatz.TAUSCH_DECKEL_VOLL, 1L,
                    Einsatz.von("betrag", sprache.betrag(werte.tagesdeckel())));
        }
        return sprache.bau(MarkSatz.TAUSCH_DECKEL_ERREICHT, 1L,
                Einsatz.von("anzahl", sprache.goldmark(entscheid.nochMoeglich()))
                        .mit("betrag",
                                sprache.betrag(werte.erloes(entscheid.nochMoeglich()))));
    }
}
