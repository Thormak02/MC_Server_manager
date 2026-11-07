package de.freimarken.paper;

import de.freimarken.kern.Bonusablage;
import de.freimarken.kern.Bonusregel;
import de.freimarken.kern.Bonusstand;
import de.freimarken.kern.Bonuswerte;
import de.freimarken.kern.Buchungsgrund;
import de.freimarken.kern.Kasse;
import de.freimarken.kern.Serienregel;
import de.freimarken.kern.Spielzeitablage;
import de.freimarken.kern.Tagesgrenze;
import de.freimarken.kern.Wirtschaftsstand;
import de.freimarken.kern.Zeitwerte;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import java.util.logging.Logger;
import net.kyori.adventure.text.Component;

/**
 * Der Tagesbonus - die eine Stelle, die ihn prueft und zahlt.
 *
 * <p>Die dritte und kleinste Quelle aus Plan 8.2. Sie belohnt zwei verschiedene Dinge, und
 * das ist die Entscheidung dieses Teils: das taegliche EINLOGGEN mit dem Grundbetrag und
 * das REGELMAESSIGE Spielen mit der Serie, die jeden Tag mehr gibt.
 *
 * <p>Kein Bukkit, derselbe Schnitt wie bei {@link Spielzeitbuch} und {@link Messstelle}:
 * Was ohne Server pruefbar ist, soll es auch sein. Das Spiel liefert nur eine UUID herein.
 *
 * <p><b>Der Waechter ueber "heute schon bezahlt" ist das BUCHUNGSBUCH und nicht die
 * Serienzeile.</b> Das ist die tragende Entscheidung, und sie ist der Grund, warum keine
 * Reihenfolge von Abstuerzen Geld erzeugen kann:
 * <ul>
 *   <li>Gefragt wird {@link Kasse#summeSeit} mit {@link Buchungsgrund#TAGESBONUS} seit dem
 *       Tagesbeginn. Steht dort etwas, ist heute bezahlt - unabhaengig davon, was in der
 *       Serienzeile steht.</li>
 *   <li>Gezahlt wird ZUERST, die Serienzeile danach. Bricht es dazwischen ab, hat der
 *       Spieler sein Geld, und die Zeile steht noch auf gestern: Der naechste Versuch
 *       sieht die Buchung und zahlt NICHT erneut, und am naechsten Tag ist seine Serie um
 *       einen Tag zu kurz. Ein verlorener Serientag ist der Preis dafuer, dass kein Weg
 *       doppelt zahlt - und er steht im Protokoll.</li>
 *   <li>Die andere Reihenfolge waere schlimmer: Serienzeile zuerst, dann zahlen. Bricht es
 *       dann ab, steht die Zeile auf heute, die Buchung fehlt - und der Spieler bekommt
 *       nie, was er heute haette. Beides kostet einen Tag; dieses kostet ihn Geld.</li>
 * </ul>
 *
 * <p><b>Nichts hiervon wirft nach aussen.</b> Gerufen wird aus dem Beitritt und aus dem
 * Spielzeittakt. Eine Ausnahme dort waere nicht ein verlorener Bonus, sondern ein
 * abgebrochener Beitritt oder ein abgebrochener Takt - und der Takt rechnet die Spielzeit
 * aller anwesenden Spieler ab.
 */
final class Tagesbonus {

    /**
     * Was gezahlt wurde - oder {@code null}, wenn nichts zu zahlen war.
     *
     * @param betrag        die gutgeschriebenen Mark
     * @param serie         der Serientag, den dieser Bonus traegt
     * @param laengsteSerie die laengste Serie dieses Spielers, nach dieser Zahlung
     * @param neuBegonnen   ob die Serie heute NEU begonnen hat - entweder der erste Bonus
     *                      ueberhaupt oder eine gerissene Serie. Der Spieler soll den
     *                      Unterschied lesen koennen: "Tag 1" nach einer Serie von
     *                      zwanzig ist eine andere Nachricht als "Tag 1" am Anfang
     */
    record Zahlung(long betrag, int serie, int laengsteSerie, boolean neuBegonnen) {

        Zahlung {
            if (betrag < 1L) {
                throw new IllegalArgumentException("Eine Zahlung ueber " + betrag
                        + " Mark ist keine - wo nichts gezahlt wurde, steht null");
            }
            if (serie < 1) {
                throw new IllegalArgumentException("Eine Zahlung traegt mindestens den"
                        + " ersten Serientag, nicht " + serie);
            }
        }

        /** Ob diese Zahlung die laengste Serie des Spielers EINGESTELLT hat. */
        boolean istBestmarke() {
            return serie == laengsteSerie;
        }
    }

    private final Wirtschaftsstand wirtschaft;
    private final Bonusablage ablage;
    private final Kasse kasse;
    private final Spielzeitablage spielzeit;
    private final Clock uhr;
    private final Logger protokoll;

    /**
     * @param wirtschaft woher Zone und Bonuswerte kommen - beim GEBRAUCH gefragt und nicht
     *                   in ein Feld kopiert, damit ein Neuladen sie erreicht
     * @param spielzeit  nur fuer die TAETIGEN Minuten des Tages, und nur wenn eine
     *                   Mindestzeit eingestellt ist. Die gemessene Anwesenheit waere die
     *                   falsche Zahl: Sie ist im Stehen zu haben, und genau dagegen ist
     *                   die Mindestzeit gedacht
     * @param uhr        die Wanduhr. Gegen eine verstellte steht {@link Serienregel}: Ein
     *                   Tag in der Vergangenheit haelt die Serie an, statt zu zahlen
     */
    Tagesbonus(Wirtschaftsstand wirtschaft, Bonusablage ablage, Kasse kasse,
               Spielzeitablage spielzeit, Clock uhr, Logger protokoll) {
        this.wirtschaft = wirtschaft;
        this.ablage = ablage;
        this.kasse = kasse;
        this.spielzeit = spielzeit;
        this.uhr = uhr;
        this.protokoll = protokoll;
    }

    /**
     * Prueft, ob diesem Spieler heute ein Bonus zusteht - und zahlt ihn.
     *
     * <p>Darf beliebig oft gerufen werden: Der zweite Aufruf am selben Tag sieht die
     * Buchung des ersten und zahlt nicht. Das ist nicht Vorsicht, sondern Bedingung - er
     * wird beim Beitritt UND in jedem Taktschlag gerufen, damit eine eingestellte
     * Mindestzeit ueberhaupt erreicht werden kann.
     *
     * @return was gezahlt wurde, oder {@code null}: schon bezahlt, Mindestzeit noch nicht
     *         erreicht, oder die Ablage antwortet nicht
     */
    Zahlung pruefe(UUID wer) {
        if (wer == null) {
            return null;
        }
        Zeitwerte zeit = wirtschaft.werte().zeit();
        Bonuswerte werte = wirtschaft.werte().bonus();
        Instant jetzt = uhr.instant();
        LocalDate heute = Tagesgrenze.tagVon(jetzt, zeit.zone());
        Instant tagesbeginn = Tagesgrenze.tagesbeginn(heute, zeit.zone());

        Bonusstand stand;
        try {
            // Das Buchungsbuch ZUERST: Es ist der Waechter, und es ist die teuerste
            // Frage. Steht dort schon etwas, wird die Serienzeile gar nicht gelesen.
            if (kasse.summeSeit(wer, tagesbeginn, Buchungsgrund.TAGESBONUS) > 0L) {
                return null;
            }
            if (!zeitErreicht(wer, heute, werte)) {
                return null;
            }
            stand = ablage.lies(wer);
        } catch (RuntimeException fehler) {
            // Die Datenbank antwortet nicht. KEINE Zahlung auf Verdacht: Ohne die Frage
            // "heute schon bezahlt" ist jede Gutschrift eine zweite. Beim naechsten
            // Taktschlag wird erneut gefragt, also ist nichts verloren, solange der
            // Spieler noch da ist.
            protokoll.warning("Tagesbonus fuer " + wer + " nicht geprueft: "
                    + fehler.getMessage() + " - es wird nichts gezahlt und beim naechsten"
                    + " Taktschlag erneut gefragt.");
            return null;
        }

        Serienregel.Urteil urteil = Serienregel.urteile(stand.letzterTag(), heute,
                stand.serie(), werte.reisstAbLuecke());
        if (!urteil.zahlbar()) {
            // Erreichbar, obwohl das Buchungsbuch nichts fand: Die Serienzeile steht auf
            // heute oder spaeter. Entweder ist eine Buchung von heute geloescht worden,
            // oder die Tagesgrenze ist zurueckgestellt - beides muss nachlesbar sein,
            // denn beides sieht fuer den Spieler aus wie ein fehlender Bonus.
            protokoll.warning("Tagesbonus fuer " + wer + " nicht gezahlt: Im Buchungsbuch"
                    + " steht fuer heute (" + heute + ") nichts, die Serienzeile aber auf "
                    + stand.letzterTag() + ". Entweder fehlt eine Buchung, oder die"
                    + " Tagesgrenze " + zeit.zone() + " wurde zurueckgestellt.");
            return null;
        }

        long betrag = Bonusregel.betrag(urteil.serie(), werte);
        try {
            kasse.gutschreiben(wer, betrag, Buchungsgrund.TAGESBONUS);
        } catch (RuntimeException fehler) {
            protokoll.warning("Tagesbonus fuer " + wer + " nicht gebucht: "
                    + fehler.getMessage() + " - die Serie bleibt unveraendert, und beim"
                    + " naechsten Taktschlag wird es erneut versucht.");
            return null;
        }

        Bonusstand nachher = stand.nachZahlung(heute, urteil.serie());
        try {
            ablage.schreibe(nachher);
        } catch (RuntimeException fehler) {
            // Gezahlt ist gezahlt. Hier wird NICHT zurueckgebucht: Eine Rueckbuchung
            // waere eine zweite Geldbewegung fuer einen Fehler, der kein Geld betrifft,
            // und sie kann genauso scheitern. Was fehlt, ist ein Serientag - und der
            // steht hier.
            protokoll.severe("Tagesbonus fuer " + wer + " ist GEZAHLT (" + betrag
                    + " Mark, Serientag " + urteil.serie() + "), aber die Serie wurde"
                    + " nicht geschrieben: " + fehler.getMessage() + ". Das Geld ist im"
                    + " Buchungsbuch und wird heute nicht erneut gezahlt; die Serie steht"
                    + " weiter auf " + stand.letzterTag() + " und reisst darum morgen.");
        }
        return new Zahlung(betrag, nachher.serie(), nachher.laengsteSerie(),
                urteil.ausgang() == Serienregel.Ausgang.NEU_BEGONNEN);
    }

    /**
     * Die Zeile fuer {@code /mark} - was die Serie gerade wert ist.
     *
     * <p>Hier und nicht beim Befehl, weil hier die Stuecke schon liegen: die Ablage, das
     * Buchungsbuch, die Uhr und die Zone. Der Befehl haette sie sich alle einzeln holen
     * muessen - und dabei die Serienregel ein zweites Mal gefragt, mit der Gefahr, eine
     * andere Antwort zu bekommen als die Zahlung.
     *
     * <p>Veraendert NICHTS: kein Buchen, kein Schreiben. Ein Blick in den eigenen Stand
     * darf keine Tat sein - dieselbe Trennung wie zwischen {@code Messstelle.bericht} und
     * {@code nimmUndLeere}.
     *
     * @return die Zeile, oder {@code null}, wenn die Datenbank nicht antwortet. Dann
     *         zeigt {@code /mark} seine uebrigen Zeilen und schweigt ueber den Bonus -
     *         eine Absage waere hier zu viel, denn gefragt war nach dem Guthaben
     */
    Component standZeile(UUID wer, Sprache sprache) {
        if (wer == null) {
            return null;
        }
        Zeitwerte zeit = wirtschaft.werte().zeit();
        Bonuswerte werte = wirtschaft.werte().bonus();
        LocalDate heute = Tagesgrenze.tagVon(uhr.instant(), zeit.zone());
        Instant tagesbeginn = Tagesgrenze.tagesbeginn(heute, zeit.zone());
        try {
            boolean schonGezahlt =
                    kasse.summeSeit(wer, tagesbeginn, Buchungsgrund.TAGESBONUS) > 0L;
            Bonusstand stand = ablage.lies(wer);
            Serienregel.Urteil urteil = Serienregel.urteile(stand.letzterTag(), heute,
                    stand.serie(), werte.reisstAbLuecke());
            long fehlend = 0L;
            if (!schonGezahlt && werte.wartetAufTaetigkeit()) {
                long taetigeMinuten =
                        spielzeit.lies(wer, heute).taetigSekundenHeute() / 60L;
                fehlend = Math.max(0L, werte.mindestMinuten() - taetigeMinuten);
            }
            return Bonustext.standZeile(sprache, stand, werte, schonGezahlt,
                    urteil.serie(), fehlend);
        } catch (RuntimeException fehler) {
            protokoll.warning("Tagesbonus fuer " + wer + " nicht anzeigbar: "
                    + fehler.getMessage());
            return null;
        }
    }

    /**
     * Ob die eingestellte Mindestzeit erreicht ist.
     *
     * <p>Ohne eingestellte Zeit wird die Spielzeitablage GAR NICHT gefragt: Das ist der
     * Normalfall (Vorgabe 0), er faellt beim Beitritt an, und eine Abfrage im Hauptfaden
     * kostet dort eine Netzrunde fuer eine Antwort, die niemand braucht.
     *
     * <p>Gezaehlt werden TAETIGE Sekunden aus Teil G und nicht die Anwesenheit. Der Grund
     * steht in {@link de.freimarken.kern.Messregel}: Anwesenheit ist im Stehen zu haben,
     * und wer eine Mindestzeit einstellt, will genau das nicht belohnen.
     */
    private boolean zeitErreicht(UUID wer, LocalDate heute, Bonuswerte werte) {
        if (!werte.wartetAufTaetigkeit()) {
            return true;
        }
        long gefordert = werte.mindestMinuten() * 60L;
        return spielzeit.lies(wer, heute).taetigSekundenHeute() >= gefordert;
    }
}
