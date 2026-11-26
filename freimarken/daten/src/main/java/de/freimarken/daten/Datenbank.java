package de.freimarken.daten;

import java.sql.Connection;
import java.sql.Driver;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.ZoneOffset;
import java.util.Calendar;
import java.util.List;
import java.util.Properties;
import java.util.TimeZone;

/**
 * Verbindung zur Datenbank und das Schema darin.
 *
 * <p>Bewusst klein gehalten: eine Verbindung, synchronisiert, mit Wiederaufbau. Ein
 * Verbindungspool waere bei zwanzig Spielern Ziererei - und jede weitere Bibliothek muss
 * spaeter mit in die Jar und dort gegen Signaturreste und Dienstdateien verteidigt werden.
 * Kommt der zweite Backend-Server, ist das die Stelle, an der ein Pool nachgeruestet wird.
 *
 * <p>Zeiten werden als {@code DATETIME(3)} abgelegt, nicht als {@code TIMESTAMP}: MariaDB
 * rechnet TIMESTAMP-Spalten in die Zeitzone der Sitzung um. Wer dann den Server umstellt
 * oder von einem anderen Rechner zugreift, liest andere Zeiten als er geschrieben hat.
 * Geschrieben wird immer UTC.
 *
 * <p><b>Die eine Ausnahme davon ist {@code spielzeit.tag}</b> (Schritt 9). Diese Spalte
 * traegt kein UTC, sondern das ORTSDATUM unter der eingestellten Tagesgrenze, in Java
 * gerechnet. Sie ist so gemeint und darf nicht nachgerechnet werden: Wer sie fuer einen
 * Fehler haelt und "zurueck" nach UTC verschiebt, verlegt den Tageswechsel um eine bis zwei
 * Stunden - in Berlin also mitten in den Abend.
 */
public final class Datenbank implements AutoCloseable {

    /**
     * Die Schema-Schritte, in Reihenfolge. Angehaengt wird unten, nie dazwischen -
     * die Nummer eines Schrittes ist das, woran eine bestehende Datenbank erkennt, was
     * sie schon hat.
     *
     * <p><b>Und ein Schritt, der einmal gelaufen ist, wird auch nicht GEAENDERT.</b>
     * {@link #migriere()} fuehrt nur Schritte ab dem gespeicherten Stand aus; eine
     * Aenderung an einem alten Schritt bewirkt auf einer bestehenden Datenbank nichts.
     * Auf einer frischen - und die Probendatenbank ist beim ersten Lauf frisch - wirkt
     * sie sofort, und dann sagen die Proben etwas ueber ein Schema, das auf keinem
     * laufenden Server steht. Wer eine Spalte braucht, haengt einen Schritt mit
     * {@code ALTER TABLE ... IF NOT EXISTS} an. {@code SchemaTest} haelt das mit einer
     * Pruefsumme je Schritt.
     *
     * <p>Paketprivat und nicht {@code private}: Die Proben im selben Paket pruefen die
     * ECHTEN Schritttexte. Ueber den Quelltext geschnitten waeren es die Texte, die
     * jemand in der Datei sieht - nicht die, die die Datenbank bekommt.
     */
    static final List<String> SCHRITTE = List.of(
            """
            CREATE TABLE parzellen (
              id           BIGINT       NOT NULL AUTO_INCREMENT,
              welt         VARCHAR(128) NOT NULL,
              x            INT          NOT NULL,
              z            INT          NOT NULL,
              eigentuemer  CHAR(36)     NOT NULL,
              preis        BIGINT       NOT NULL,
              erworben_am  DATETIME(3)  NOT NULL,
              PRIMARY KEY (id),
              UNIQUE KEY ort (welt, x, z),
              KEY nach_eigentuemer (welt, eigentuemer)
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
            """,
            """
            CREATE TABLE parzellen_mitbauer (
              parzelle_id BIGINT   NOT NULL,
              spieler     CHAR(36) NOT NULL,
              PRIMARY KEY (parzelle_id, spieler),
              CONSTRAINT fk_mitbauer_parzelle FOREIGN KEY (parzelle_id)
                REFERENCES parzellen (id) ON DELETE CASCADE
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
            """,
            """
            CREATE TABLE kassen (
              spieler CHAR(36) NOT NULL,
              mark    BIGINT   NOT NULL,
              PRIMARY KEY (spieler)
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
            """,

            // Schritt 4: Das Modell aus Plan 6.1 - art, eltern_id, zustand.
            //
            // x und z werden NULL-faehig, weil ein zusammengelegter Besitz keine eigene
            // Rasterzelle hat; sein Bereich ergibt sich aus seinen Kindern. Der eindeutige
            // Schluessel ort (welt, x, z) bleibt unangetastet und behaelt genau seine eine
            // Aufgabe: InnoDB zaehlt NULL in einem eindeutigen Schluessel als verschieden,
            // also bleibt "ein Eigentuemer je Rasterzelle" eine Zusage der Datenbank,
            // waehrend beliebig viele rasterlose Zeilen hineinpassen.
            //
            // Die neuen Spalten haengen hinten an, ohne AFTER: Plan 12a.2 sagt zu, dass der
            // Manager Spieldaten direkt liest, und eine verschobene Spaltenreihenfolge
            // braeche das - auch wenn dieses Projekt selbst nirgends SELECT * benutzt.
            //
            // nach_elternteil ist Pflicht, nicht Ziererei: InnoDB verlangt fuer den
            // Fremdschluessel einen Index mit eltern_id links und legte sonst selbst einen
            // an - dann lieber selbst benannt.
            """
            ALTER TABLE parzellen
              MODIFY COLUMN IF EXISTS x INT NULL,
              MODIFY COLUMN IF EXISTS z INT NULL,
              ADD COLUMN IF NOT EXISTS art
                  ENUM('parzelle','gruppe','werder','freistadt','ladeneinheit')
                  NOT NULL DEFAULT 'parzelle',
              ADD COLUMN IF NOT EXISTS eltern_id BIGINT NULL DEFAULT NULL,
              ADD COLUMN IF NOT EXISTS zustand
                  ENUM('aktiv','gekuendigt','verfallen') NOT NULL DEFAULT 'aktiv',
              ADD KEY IF NOT EXISTS nach_elternteil (eltern_id)
            """,

            // Schritt 5: der Fremdschluessel allein.
            //
            // Eigener Schritt, weil ein Schemaschritt entweder ganz oder gar nicht laufen
            // muss: migriere() fuehrt erst das DDL aus und schreibt danach getrennt den
            // Stand fort. Bricht es dazwischen ab, ist die Aenderung da und der Stand
            // zurueck - deshalb traegt jeder Satz oben sein IF [NOT] EXISTS und laeuft ein
            // zweites Mal folgenlos durch.
            //
            // ON DELETE RESTRICT, nicht SET NULL: Plan 6.3 verlangt, dass Auftrennen auch
            // den Weg in der Welt wiederherstellt. Das darf nie die stille Nebenwirkung
            // eines DELETE sein. RESTRICT macht aus dem Fehlgriff eine laute Absage.
            """
            ALTER TABLE parzellen
              ADD CONSTRAINT fk_besitz_elternteil FOREIGN KEY IF NOT EXISTS (eltern_id)
                  REFERENCES parzellen (id) ON DELETE RESTRICT
            """,

            // Schritt 6: x und z sind immer beide gesetzt oder beide NULL.
            //
            // Jetzt faellig, weil gruppeSetzen der erste Code ist, der ueberhaupt
            // NULL-Koordinaten schreibt. Eine halbe Zeile - x gesetzt, z NULL - waere der
            // unangenehmste Zustand, den diese Tabelle annehmen kann: anzahlVon zaehlte
            // sie mit, weil AUF_DEM_RASTER nur x prueft, und lies() wuerde beim Lesen
            // werfen. Zwei Lesewege, zwei Wahrheiten.
            //
            // EIN Schritt mit IF NOT EXISTS, nicht ein DROP/ADD-Paar: migriere() fuehrt
            // erst das DDL aus und schreibt danach getrennt den Stand fort, und DDL
            // committet von selbst. Bricht es zwischen beiden ab, liefe ein ADD-Schritt
            // allein erneut und scheiterte am doppelten Namen - ein Schema, das sich
            // nicht mehr migrieren laesst.
            """
            ALTER TABLE parzellen
              ADD CONSTRAINT IF NOT EXISTS ck_besitz_ort
                  CHECK ((x IS NULL AND z IS NULL) OR (x IS NOT NULL AND z IS NOT NULL))
            """,

            // Schritt 7: das Buchungsbuch.
            //
            // betrag ist VORZEICHENBEHAFTET - eine Ausgabe negativ, eine Einnahme positiv.
            // Nur so gilt die Zusage, mit der sich Buchung und Protokoll gegenseitig
            // halten: Die Summe aller Buchungen eines eroeffneten Kontos ist sein Stand.
            //
            // stand_nachher ist Absicht und keine Redundanz. Ohne ihn liesse sich eine
            // einzelne Zeile nicht lesen, ohne alle vorherigen zu addieren - und eine
            // Luecke im Buch waere unsichtbar, weil es nichts gaebe, womit sie sich
            // widerspricht.
            //
            // KEINE CHECK-Pruefung auf die Richtung: Welcher Grund in welche Richtung
            // zeigt, entscheidet Buchungsgrund in Java, und eine zweite Fassung davon
            // hier waeren zwei Stellen, die dasselbe entscheiden. Der Grund steht als
            // ASCII-Schluessel, damit die heutige deutsche Wortwahl nicht in die
            // Geschichte einfriert.
            //
            // Der Index traegt die eine Abfrage, die es gibt: die letzten N Zeilen eines
            // Spielers. Die id als drittes Merkmal, weil zwei Buchungen im selben
            // Millisekundenschritt sonst in beliebiger Reihenfolge kaemen.
            """
            CREATE TABLE IF NOT EXISTS buchungen (
              id            BIGINT      NOT NULL AUTO_INCREMENT,
              spieler       CHAR(36)    NOT NULL,
              zeitpunkt     DATETIME(3) NOT NULL,
              grund         VARCHAR(32) NOT NULL,
              betrag        BIGINT      NOT NULL,
              stand_nachher BIGINT      NOT NULL,
              PRIMARY KEY (id),
              KEY nach_spieler (spieler, zeitpunkt, id),
              CONSTRAINT fk_buchung_kasse FOREIGN KEY (spieler)
                  REFERENCES kassen (spieler) ON DELETE RESTRICT
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
            """,

            // Schritt 8: der Altbestand kommt ins Buch.
            //
            // Die einzige Ausnahme von "jeder Schritt ist DDL", und sie ist noetig: Ohne
            // sie haetten alle heute vorhandenen Konten einen Stand ohne eine einzige
            // Buchung, und die Summenprobe waere von der ersten Minute an falsch.
            //
            // Der Grund heisst bestand und nicht eroeffnung, weil wir nicht wissen, was
            // vorher geschah. Diese Zeile behauptet nur etwas ueber das Jetzt und
            // erfindet keine Geschichte.
            //
            // Das NOT EXISTS traegt der Schritt selbst: migriere() legt keine Transaktion
            // um Anweisung und Standfortschreibung, also muss jeder Satz einen zweiten
            // Lauf folgenlos ueberstehen.
            """
            INSERT INTO buchungen (spieler, zeitpunkt, grund, betrag, stand_nachher)
              SELECT k.spieler, UTC_TIMESTAMP(3), 'bestand', k.mark, k.mark
                FROM kassen k
               WHERE NOT EXISTS (SELECT 1 FROM buchungen b WHERE b.spieler = k.spieler)
            """,

            // Schritt 9: die gemessene Spielzeit - eine Zeile je Spieler und Tag.
            //
            // tag ist die EINZIGE Spalte dieses Schemas ohne UTC: Sie traegt das
            // Ortsdatum unter der eingestellten Tagesgrenze, gerechnet in Java
            // (Tagesgrenze.tagVon), nie mit CURDATE(), UTC_DATE() oder NOW(). Die
            // Sitzung steht fest auf UTC - zwischen 00:00 und 01:00 Ortszeit nennte die
            // Datenbank noch den Vortag, und der eingestellte Wert Europe/Berlin waere
            // stumm wirkungslos. Dasselbe steht im Klassenkopf, weil die Falle beim
            // LESEN zuschnappt und nicht hier.
            //
            // Der Tag gehoert IN den Primaerschluessel und nicht als Nebenfeld neben
            // einen einzelnen Zaehler: Die Vergangenheit laesst sich nicht nachtraeglich
            // in Tage aufteilen, und die Ranglisten aus Plan 12b.3 wollen Zeitraeume.
            // Ausserdem steht so in der Zeile immer schon VERSTRICHENE Zeit und nie ein
            // laufender Zeitpunkt - darum kann kein Absturz eine offene Sitzung
            // hinterlassen und es gibt kein Aufraeumen beim Start. Der Preis sind rund
            // zwanzig Zeilen am Tag.
            //
            // sekunden als BIGINT, obwohl ein Tag nur 86.400 Sekunden hat: SMALLINT
            // UNSIGNED (65.535) reicht fuer einen einzigen Tag nicht einmal aus,
            // MEDIUMINT UNSIGNED (16.777.215) traegt 194 Tage in EINER Zeile und INT
            // 24.855 Tage - beides genug und beides falsch. Denn addiert wird in der Spalte
            // (sekunden = sekunden + ?), waehrend der Kern in long rechnet und mit
            // Math.addExact abbricht. Eine engere Spalte braeche frueher als die
            // Speicherfassung, und dann verhielten sich zwei Umsetzungen hinter einer
            // Schnittstelle verschieden. Die ersparten vier Byte sind bei 7.300 Zeilen
            // im Jahr 29 Kilobyte.
            //
            // CHAR(36) wie ueberall sonst und nicht BINARY(16): sonst laesst sich diese
            // Tabelle mit kassen und parzellen nicht verbinden.
            //
            // KEIN Fremdschluessel auf kassen - anders als bei buchungen (Schritt 7) und
            // mit Grund: Eine Buchung ohne Konto waere eine Zeile ohne das Ding, zu dem
            // sie gehoert, und die Summenprobe haengt daran. Spielzeit ist eine Messung
            // und haengt an niemandem. Ein RESTRICT-Fremdschluessel brachte dagegen die
            // EINE Transaktion des Takts zum Scheitern - also die Minute ALLER
            // anwesenden Spieler -, sobald ein einziger von ihnen noch keine Kassenzeile
            // hat: eine beim Beitritt fehlgeschlagene Kontoeroeffnung genuegt. Ein
            // CASCADE wiederum loeschte Messdaten stumm mit.
            //
            // KEIN zweiter Index. Beide Abfragen, die es gibt - die Sekunden eines Tages
            // und die Summe eines Spielers -, laufen ueber spieler, also ueber das linke
            // Glied des Primaerschluessels. Ein Index fuer eine Abfrage, die es noch
            // nicht gibt, ist derselbe Fehler wie ein Einstellschluessel fuer einen Wert,
            // den es noch nicht gibt; nachgeruestet ist er ein einzeiliger Schemaschritt.
            //
            // IF NOT EXISTS wie bei Schritt 7: migriere() legt keine Transaktion um
            // Anweisung und Standfortschreibung, also muss der Satz einen zweiten Lauf
            // folgenlos ueberstehen.
            """
            CREATE TABLE IF NOT EXISTS spielzeit (
              spieler  CHAR(36) NOT NULL,
              tag      DATE     NOT NULL,
              sekunden BIGINT   NOT NULL,
              PRIMARY KEY (spieler, tag)
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
            """,

            // Schritt 10: die Berufe - eine Zeile je Spieler und Beruf.
            //
            // KEINE Spalte fuer die Rolle. Ob "steinmetz" ein Grundberuf oder eine
            // Spezialisierung ist, entscheidet die Liste in der config.yml und sonst
            // niemand; eine zweite Fassung davon hier waeren zwei Stellen, die dasselbe
            // entscheiden - dieselbe Begruendung wie bei grund in Schritt 7.
            //
            // zustand statt DELETE. Eine abgelegte Zeile bleibt stehen, denn Teil G will
            // die Geschichte, und ein geloeschter Satz nimmt sie mit.
            //
            // ZWEI Zeitspalten, und das ist der Kern dieses Schrittes. geaendert_am traegt
            // die juengste WAHL und gibt die Reihenfolge, in der /beruf stand die Berufe
            // zeigt. abgelegt_am traegt das juengste ABLEGEN und ist die Sperrfrist
            // selbst. Eine Spalte fuer beides waere falsch, und zwar zweimal: Die Frist
            // haenge dann am Waehlen und sperrte einen neuen Spieler einen Tag lang aus
            // seinem zweiten Grundberuf, und ein spaeter wiedergewaehlter Beruf loeschte
            // mit seinem neuen Zeitpunkt die Frist, die er ausgeloest hat - Aufgeben,
            // warten, wiederwaehlen waere der Weg um beide Bremsen herum. Darum bleibt
            // abgelegt_am beim Wiederwaehlen unberuehrt.
            //
            // Beide sind UTC, wie jede DATETIME-Spalte dieses Schemas; gerechnet wird mit
            // ihnen nur eine DAUER, also brauchen sie keine Zone. Der Zeitpunkt kommt aus
            // Java und nie aus UTC_TIMESTAMP(3): Die Regel im Kern urteilt auf derselben
            // eingespeisten Uhr, und zwei Zeitquellen in einer Frist sind eine Frist, die
            // sich nicht nachrechnen laesst.
            //
            // KEIN Fremdschluessel auf kassen - derselbe Grund wie bei spielzeit (Schritt
            // 9): Ein Beruf haengt an niemandes Konto. Ein RESTRICT brachte das Waehlen
            // zum Scheitern, sobald eine Kontoeroeffnung einmal fehlgeschlagen ist, ein
            // CASCADE loeschte Berufe stumm mit.
            //
            // KEIN zweiter Index. Es gibt genau eine Abfrage - alle Zeilen eines Spielers
            // -, und die laeuft ueber das linke Glied des Primaerschluessels. Ein Index
            // fuer die Frage "wie viele Bergmaenner gibt es" ist nachgeruestet ein
            // einzeiliger Schemaschritt, und die Frage stellt erst Teil G.
            //
            // beruf VARCHAR(32) wie grund in Schritt 7. Der Leser der config.yml weist
            // einen laengeren Schluessel ab (Berufsangabe.HOECHSTENS_ZEICHEN); ohne diese
            // Pruefung schnitte MariaDB ihn stumm ab, und zwei Berufe fielen zusammen.
            //
            // IF NOT EXISTS wie bei Schritt 7 und 9: migriere() legt keine Transaktion um
            // Anweisung und Standfortschreibung, also muss der Satz einen zweiten Lauf
            // folgenlos ueberstehen.
            """
            CREATE TABLE IF NOT EXISTS berufe (
              spieler      CHAR(36)    NOT NULL,
              beruf        VARCHAR(32) NOT NULL,
              zustand      ENUM('aktiv','abgelegt') NOT NULL DEFAULT 'aktiv',
              geaendert_am DATETIME(3) NOT NULL,
              abgelegt_am  DATETIME(3) NULL,
              PRIMARY KEY (spieler, beruf)
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
            """,

            // Schritt 11: die TAETIGE Zeit - eine zweite Zahl in derselben Zeile.
            //
            // In dieselbe Zeile und nicht in eine zweite Tabelle. Die Zusage, auf die sich
            // jede Anzeige verlaesst, ist "taetig <= gemessen"; sie haelt nur, wenn beide
            // Zahlen in EINER Zeile stehen und EIN Zurueckrollen sie zusammen trifft. Zwei
            // Tabellen waeren ausserdem ein zweiter Schreibweg fuer Spielzeit, und den
            // verbietet Plan 8.8 ausdruecklich ("kein Setzen von Hand ... nicht einen
            // zweiten Schreibweg").
            //
            // DEFAULT 0 fuer alles, was schon gemessen ist: Von der Vergangenheit ist nicht
            // bekannt, wie viel davon taetig war, und eine geratene Zahl waere schlimmer
            // als eine 0. Die 0 ist dabei die sichere Richtung - sie gibt keinen Rang zu
            // frueh. Wer alte Zeilen daran erkennen will, erkennt sie an genau dieser 0.
            //
            // IF NOT EXISTS ist hier MariaDB-eigen, anders als bei den Schritten 7, 9 und
            // 10, wo es auch MySQL kennt. Das ist hingenommen: migriere() legt keine
            // Transaktion um Anweisung und Standfortschreibung, ein Schritt MUSS also einen
            // zweiten Lauf folgenlos ueberstehen, und das ist eine Bedingung - die
            // Dialektfreiheit von MariaSpielzeit.FORTSCHREIBEN ist eine Hoeflichkeit. Wer
            // dieses Schema auf MySQL fuehrt, laesst das IF NOT EXISTS weg und traegt
            // dafuer das Risiko des zweiten Laufs.
            """
            ALTER TABLE spielzeit
              ADD COLUMN IF NOT EXISTS taetig_sekunden BIGINT NOT NULL DEFAULT 0
            """,

            // Schritt 12: die Serien des Tagesbonus - eine Zeile je Spieler.
            //
            // KEINE Spalte fuer "heute schon bezahlt". Das entscheidet allein das
            // Buchungsbuch: Dort ist die Wahrheit ueber Geld, und nur dort kann keine
            // Reihenfolge von Abstuerzen eine zweite Zahlung am selben Tag erzeugen. Was
            // hier liegt, ist eine Zaehlung von Tagen - geht sie verloren, ist eine Serie
            // zu kurz und kein Geld entstanden.
            //
            // letzter_tag ist ein DATE und kein DATETIME (Plan 8.8: "gespeichert wird je
            // Spieler ein Datum und kein Zeitpunkt"). Ein Zeitpunkt haette eine zweite
            // Zone neben der eingestellten Tagesgrenze, und der Vergleich zweier Tage
            // waere genau die Falle, gegen die sie gebaut ist.
            //
            // NULL ist erlaubt und heisst "noch kein Bonus gehabt" - zusammen mit serie 0.
            // Die beiden gehoeren zusammen, und Bonusstand verweigert jede halbe Fassung.
            //
            // laengste_serie bleibt stehen, wenn die laufende reisst: Aus einem Datum und
            // einer Zahl ist nicht mehr herauszulesen, wie lang eine frueher gerissene
            // Serie war.
            //
            // Kein Fremdschluessel auf kassen, wie bei spielzeit und berufe: Eine Serie
            // kann entstehen, bevor ein Konto eroeffnet ist, und ein Fremdschluessel
            // machte aus der Reihenfolge zweier Schreibzuege eine Zusage.
            //
            // IF NOT EXISTS wie bei den Schritten 7, 9 und 10: migriere() legt keine
            // Transaktion um Anweisung und Standfortschreibung, also muss der Satz einen
            // zweiten Lauf folgenlos ueberstehen.
            """
            CREATE TABLE IF NOT EXISTS tagesbonus (
              spieler        CHAR(36) NOT NULL,
              letzter_tag    DATE     NULL,
              serie          INT      NOT NULL DEFAULT 0,
              laengste_serie INT      NOT NULL DEFAULT 0,
              PRIMARY KEY (spieler)
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
            """,

            // Schritt 13: die Mietvertraege ueber Ladeneinheiten.
            //
            // EINE EIGENE TABELLE und keine Zeile in parzellen, obwohl Plan 7.1 die
            // Ladeneinheit als "Besitz der Art ladeneinheit" nennt. Der Grund ist, dass
            // die EINHEIT inzwischen in der config.yml steht (Schritt 8, Teil A): Ihr
            // Quader und ihre Truhe sind von Hand gebaute Einrichtung und kein
            // gekaufter Besitz. Eine Parzellenzeile daneben waere ein zweiter Ort fuer
            // dieselbe Geometrie - und dann entscheidet die Reihenfolge zweier Lesevorgaenge,
            // wo ein Laden liegt. Was hier steht, ist der VERTRAG, und den gibt es in
            // parzellen nicht: Miete und Ablauftag haben dort keine Spalte, und 99 von
            // 100 Zeilen braeuchten sie nie.
            //
            // miete steht in der ZEILE und nicht nur in der config.yml. Das ist der
            // Bestandsschutz aus Plan 1943 ("anpassbar mit Bestandsschutz - gilt nur fuer
            // NEUE Vertraege"): Ohne diese Spalte traefe jede Erhoehung sofort jeden
            // laufenden Mieter, und zwar stumm.
            //
            // kaution steht aus einem ZWEITEN Grund in der Zeile: Bei einer Raeumung
            // wegen Zahlungsausfall verfaellt sie (Plan 7.1: zurueck nur "bei
            // ordentlicher Rueckgabe"), ohne dass eine Rueckzahlung gebucht wird. Waere
            // die gehaltene Kaution nur als "eingezahlt minus zurueckgezahlt" aus dem
            // Buchungsbuch zu rechnen, bliebe die verfallene Summe dort stehen - und
            // beim naechsten ordentlichen Kuendigen bekaeme der Spieler sie mit heraus.
            //
            // uq_ladenmiete_mieter ist "ein Laden je Spieler" (Plan 7.1), und zwar als
            // Zusage der Datenbank. Eine Pruefung im Befehl hat zwischen Fragen und
            // Schreiben eine Luecke; dieser Schluessel hat keine. MariaMiete.schreibe
            // darf darum KEIN ON DUPLICATE KEY UPDATE nehmen: Das feuert bei JEDEM
            // eindeutigen Schluessel, also auch bei diesem, und wuerde statt zu scheitern
            // stillschweigend die fremde Zeile aendern.
            //
            // Der Einheitenname kommt kleingeschrieben an (Mietvertrag normiert ihn), die
            // Kollation der Spalte entscheidet also nichts. VARCHAR(32), weil
            // Ladeneinheit.brauchbarerName hoechstens 32 Zeichen zulaesst.
            //
            // Kein Fremdschluessel auf kassen, wie bei spielzeit, berufe und tagesbonus:
            // Ein Vertrag kann entstehen, bevor ein Konto eroeffnet ist, und ein
            // Fremdschluessel machte aus der Reihenfolge zweier Schreibzuege eine Zusage.
            //
            // IF NOT EXISTS wie bei den Schritten 7, 9, 10 und 12: migriere() legt keine
            // Transaktion um Anweisung und Standfortschreibung, also muss der Satz einen
            // zweiten Lauf folgenlos ueberstehen.
            """
            CREATE TABLE IF NOT EXISTS ladenmiete (
              einheit    VARCHAR(32) NOT NULL,
              mieter     CHAR(36)    NOT NULL,
              beginn     DATE        NOT NULL,
              laeuft_bis DATE        NOT NULL,
              miete      BIGINT      NOT NULL,
              kaution    BIGINT      NOT NULL,
              ausfaelle  INT         NOT NULL DEFAULT 0,
              PRIMARY KEY (einheit),
              UNIQUE KEY uq_ladenmiete_mieter (mieter)
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
            """,

            // Schritt 14: das Abholfach - Teil C.
            //
            // Die Antwort auf den einen Satz aus Plan 7.1, der keine Ausnahme kennt: "Der
            // Kisteninhalt wandert in ein Abholfach und wird NIE geloescht." Eine
            // Raeumung darf einen Spieler Geld kosten - dafuer ist die verfallende
            // Kaution da -, aber niemals seine Gegenstaende.
            //
            // EINE EIGENE TABELLE und keine Spalte an ladenmiete, obwohl das Fach dort
            // entsteht. Das Fach UEBERLEBT den Vertrag: Es wird in dem Augenblick
            // gefuellt, in dem die Vertragszeile geloescht wird, und muss noch da sein,
            // wenn der Spieler Wochen spaeter wiederkommt und laengst einen anderen Laden
            // hat. Eine Spalte an einer geloeschten Zeile kann das nicht.
            //
            // AUTO_INCREMENT fuer die Nummer, und daraus folgt eine Zusage, die
            // Abholablage.nimm halten MUSS: Der Zaehler von InnoDB ueberlebt in MariaDB
            // einen Neustart nicht zuverlaessig, er beginnt dann bei MAX(id) + 1. Wurde
            // die hoechste Zeile vorher abgeholt, wird ihre Nummer ein zweites Mal
            // vergeben. Darum fragt jedes Loeschen nach empfaenger UND id - sonst holte
            // ein alt gewordenes Fenster die Ware eines fremden Spielers.
            //
            // MEDIUMTEXT fuer den Inhalt, nicht TEXT. Ein TEXT haelt 64 KB, und ein
            // Stapel mit viel Beschriftung - eine Kiste voller beschriebener Buecher -
            // kann darueber liegen. MariaDB schnitte ihn dann ab, und ein abgeschnittener
            // Stapel ist ein Gegenstand, der nicht mehr zu lesen ist. Abholgut weist
            // alles ueber 1 MB vorher ab; die Spalte haelt 16 MB, damit die Grenze IM
            // PROGRAMM greift und nicht in der Datenbank. Faellt ein Stapel erst hier
            // auf, scheitert nicht ein Stapel, sondern die ganze Raeumung.
            //
            // beschriftung steht NEBEN dem Inhalt und ist nicht die Wahrheit ueber ihn.
            // Sie steht da fuer den Tag, an dem sich ein Stapel nach einem
            // Minecraft-Update nicht mehr zurueckverwandeln laesst: Dann ist sie das
            // Einzige, was noch sagt, was verloren geht. "64x Eichenholz, nicht lesbar"
            // ist von Hand zu ersetzen, "ein Stapel, nicht lesbar" nicht.
            //
            // ix_abholfach_empfaenger traegt die id als zweites Glied, und das ist die
            // ganze Abfrage dieser Tabelle: alle Stuecke eines Spielers, aelteste zuerst.
            // Mit der id im Index liest MariaDB sie in der richtigen Reihenfolge, ohne zu
            // sortieren. Das ORDER BY in MariaAbholfach bleibt trotzdem stehen - eine
            // Reihenfolge, die nur aus dem Ausfuehrungsplan folgt, ist keine Zusage.
            //
            // eingelegt ist DATETIME(3) und UTC, wie jede Zeitspalte dieses Schemas. Der
            // Zeitpunkt kommt aus Java und nie aus UTC_TIMESTAMP(3) - dieselbe Regel wie
            // bei berufe (Schritt 10): zwei Zeitquellen in einer Reihenfolge sind eine
            // Reihenfolge, die sich nicht nachrechnen laesst.
            //
            // Kein Fremdschluessel auf kassen und keiner auf ladenmiete. Auf kassen aus
            // demselben Grund wie ueberall (ein Fach haengt an niemandes Konto), auf
            // ladenmiete aus einem staerkeren: Das Fach entsteht GENAU DANN, wenn die
            // Vertragszeile verschwindet. Ein Fremdschluessel darauf waere die Zusage,
            // dass das Fach ohne seinen Vertrag nicht existieren kann - und das Gegenteil
            // ist sein Zweck.
            //
            // IF NOT EXISTS wie bei den Schritten 7, 9, 10, 12 und 13.
            """
            CREATE TABLE IF NOT EXISTS abholfach (
              id           BIGINT      NOT NULL AUTO_INCREMENT,
              empfaenger   CHAR(36)    NOT NULL,
              herkunft     VARCHAR(48) NOT NULL,
              eingelegt    DATETIME(3) NOT NULL,
              beschriftung VARCHAR(96) NOT NULL,
              inhalt       MEDIUMTEXT  NOT NULL,
              PRIMARY KEY (id),
              KEY ix_abholfach_empfaenger (empfaenger, id)
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
            """,

            // Schritt 15: die Kistenshops - Plan 7.4.
            //
            // Der HAUPTSCHLUESSEL ist die SCHILDSTELLE und nicht die Kiste. An einer
            // Doppelkiste koennen zwei Schilder haengen, die zwei verschiedene Waren
            // handeln - eines auf jeder Haelfte. Mit der Kiste als Schluessel waere das
            // unmoeglich, und ein Laden mit einer Ware je Kiste braeuchte doppelt so
            // viel Platz.
            //
            // ix_kistenshop_kiste traegt die Gegenrichtung, und das ist der HEISSE PFAD:
            // "ist das eine Shopkiste" wird bei jedem Trichtertakt gefragt. Im Betrieb
            // laeuft diese Frage nicht ueber die Datenbank, sondern ueber
            // ZwischenspeicherShops - eine vollstaendige Kopie im Arbeitsspeicher. Der
            // Index steht trotzdem, denn genau diese Kopie wird beim Start aus dieser
            // Tabelle gefuellt, und ein Betreiber, der von Hand nachsieht, fragt mit
            // derselben Bedingung.
            //
            // besitzer ist NULL beim BUNDSHOP - er gehoert niemandem, hat unbegrenzten
            // Bestand und sein Geld kommt aus dem Nichts. Dieselbe Loesung wie
            // ChestShops Adminshop, nur ueber ein fehlendes Feld statt ueber den Namen
            // "Admin Shop" in der ersten Schildzeile: Ein Spieler, der sich so nennt,
            // kann damit nichts anfangen.
            //
            // ware ist MEDIUMTEXT, aus demselben Grund wie beim Abholfach (Schritt 14):
            // Eine voll verzauberte Spitzhacke wird zu einigen hundert Zeichen, eine
            // Kiste in einer Kiste zu mehr. Ein TEXT haelt 64 KB und SCHNEIDET darueber
            // stumm ab - und eine abgeschnittene Warenprobe ist eine Ware, die kein
            // Kunde mehr bekommt, weil der Vergleich mit dem Kisteninhalt immer
            // fehlschlaegt. Dass verzauberte Ausruestung handelbar ist, ist eine
            // Entscheidung vom 08.11.2026; diese Spalte ist ihre Folge.
            //
            // warenname steht NEBEN der Ware und ist nicht die Wahrheit ueber sie - wie
            // beschriftung beim Abholfach. Er steht auf dem Schild, und er ist das
            // Einzige, was noch sagt, was eine Ware war, wenn sie sich nach einem
            // Minecraft-Update nicht mehr zurueckverwandeln laesst. VARCHAR(32) bei
            // hoechstens 15 Zeichen Schildbreite: Luft fuer einen spaeter breiteren
            // Namen, ohne die Zeile zu sprengen.
            //
            // Kein Fremdschluessel auf kassen - derselbe Grund wie ueberall: Ein Shop
            // haengt an niemandes Konto, und ein Bundshop an gar keinem.
            //
            // IF NOT EXISTS wie bei den Schritten 7, 9, 10, 12, 13 und 14.
            """
            CREATE TABLE IF NOT EXISTS kistenshop (
              welt         VARCHAR(64) NOT NULL,
              schild_x     INT         NOT NULL,
              schild_y     INT         NOT NULL,
              schild_z     INT         NOT NULL,
              kiste_x      INT         NOT NULL,
              kiste_y      INT         NOT NULL,
              kiste_z      INT         NOT NULL,
              besitzer     CHAR(36)    NULL,
              menge        INT         NOT NULL,
              kaufpreis    BIGINT      NOT NULL,
              verkaufpreis BIGINT      NOT NULL,
              warenname    VARCHAR(32) NOT NULL,
              ware         MEDIUMTEXT  NOT NULL,
              PRIMARY KEY (welt, schild_x, schild_y, schild_z),
              KEY ix_kistenshop_kiste (welt, kiste_x, kiste_y, kiste_z),
              KEY ix_kistenshop_besitzer (besitzer)
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
            """,

            // Das Blockbuch - wer wann wo was getan hat, fuer die Moderation.
            //
            // DIE GROESSTE TABELLE DIESES PROJEKTS, und darum ist jede Entscheidung hier
            // eine ueber Schreiblast. Eine Welt mit "benutzen" in der Auswahl sammelt
            // Zehntausende Zeilen am Tag; alle uebrigen Tabellen zusammen wachsen um
            // Dutzende.
            //
            // KEIN FREMDSCHLUESSEL AUF kassen, und hier ist der Grund zwingender als
            // ueberall sonst: Ein RESTRICT liesse den Schreibzug scheitern, sobald ein
            // Spieler noch keine Kassenzeile hat - also beim allerersten Block eines
            // neuen Spielers, und das ist der haeufigste Fall von allen. Ein CASCADE
            // loeschte umgekehrt das Protokoll eines Spielers stumm mit, dessen Konto
            // je aufgeraeumt wird - und genau dann will die Moderation es lesen. Eine
            // Protokollzeile haengt an niemandem.
            //
            // DREI INDIZES, und jeder beantwortet eine Frage, die wirklich gestellt
            // wird. Auf einer Tabelle dieser Groesse ist das Schreiblast, die man
            // begruenden muss:
            //   ix_blockbuch_ort      "wer hat dieses Loch gegraben" - der Befehl auf
            //                         den Block im Blick
            //   ix_blockbuch_spieler  "wo war dieser Spieler" - die andere Richtung, die
            //                         ein Dutzend abgewiesener Versuche sichtbar macht
            //   ix_blockbuch_zeit     das Aufraeumen. Ohne ihn liefe jeder Tageswechsel
            //                         ueber die ganze Tabelle, im Hauptfaden, auf der
            //                         einen Verbindung - also genau der Stillstand,
            //                         gegen den der begrenzte Happen gebaut ist.
            //
            // zeitpunkt UND id in den beiden Lese-Indizes: Die Spalte ist DATETIME(3),
            // also millisekundengenau, und zwanzig Bloecke in einem Tick sind ein
            // normaler Abbau mit Effizienz V. Ohne die id als Tiebreaker waere die
            // Reihenfolge gleichzeitiger Zeilen beliebig, und eine Abfrage mit Grenze
            // liefe zweimal verschieden aus - dieselbe Zusage, die SpeicherBlockbuch mit
            // seinem Vergleicher haelt.
            //
            // gelungen als TINYINT(1) und nicht als zweiter Wert in der Spalte tat: Ein
            // "setzen-abgewiesen" verloere die Angabe, WAS abgewiesen wurde, und eine
            // Abfrage nach allen Setzungen muesste zwei Werte kennen.
            //
            // IF NOT EXISTS wie bei den Schritten 7, 9, 10, 12, 13, 14 und 15.
            """
            CREATE TABLE IF NOT EXISTS blockbuch (
              id        BIGINT      NOT NULL AUTO_INCREMENT,
              zeitpunkt DATETIME(3) NOT NULL,
              spieler   CHAR(36)    NOT NULL,
              welt      VARCHAR(64) NOT NULL,
              x         INT         NOT NULL,
              y         INT         NOT NULL,
              z         INT         NOT NULL,
              tat       VARCHAR(16) NOT NULL,
              gelungen  TINYINT(1)  NOT NULL,
              block     VARCHAR(64) NOT NULL,
              PRIMARY KEY (id),
              KEY ix_blockbuch_ort (welt, x, y, z, zeitpunkt, id),
              KEY ix_blockbuch_spieler (spieler, zeitpunkt, id),
              KEY ix_blockbuch_zeit (zeitpunkt)
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
            """,

            // Die Mitbenutzerliste eines Kistenshops - /shop freigeben <spieler>.
            //
            // EINE EIGENE TABELLE und keine Spalte an kistenshop, genau wie
            // parzellen_mitbauer beim Besitz: Es ist eine LISTE, und eine Liste in einer
            // Spalte waere entweder eine Laengengrenze oder ein Text, den niemand
            // abfragen kann. Dasselbe Muster, dieselbe Bauart - zusammengesetzter
            // Hauptschluessel aus der Besitzzeile plus Spieler.
            //
            // MIT Fremdschluessel ON DELETE CASCADE, und hier anders als beim Blockbuch:
            // Eine Freigabe OHNE ihren Shop ist sinnlos - sie koennte nie wieder
            // befragt werden, denn gefragt wird immer ueber den Shop. Wird das Schild
            // abgebaut, muessen die Freigaben mit weg; bliebe eine liegen und jemand
            // baute spaeter an derselben Stelle ein neues Schild, haette ein Fremder
            // unbemerkt Zutritt zu einem Laden, den er nie geschenkt bekam. Der
            // vierteilige Schluessel macht das moeglich - die Tabelle kistenshop hat
            // keine Ersatznummer, ihre Stelle IST der Schluessel.
            //
            // Kein eigener Index auf spieler: Gefragt wird immer "wer darf an DIESEN
            // Shop", nie "wo darf dieser Spieler ueberall hin". Kaeme die zweite Frage
            // je dazu - etwa fuer eine Anzeige "deine Freigaben" -, gehoert ein Index
            // dazu und nicht ein Durchlauf ueber die ganze Tabelle.
            //
            // IF NOT EXISTS wie bei den Schritten 7, 9, 10, 12, 13, 14, 15 und 16.
            """
            CREATE TABLE IF NOT EXISTS kistenshop_freigabe (
              welt     VARCHAR(64) NOT NULL,
              schild_x INT         NOT NULL,
              schild_y INT         NOT NULL,
              schild_z INT         NOT NULL,
              spieler  CHAR(36)    NOT NULL,
              PRIMARY KEY (welt, schild_x, schild_y, schild_z, spieler),
              CONSTRAINT fk_freigabe_shop
                FOREIGN KEY (welt, schild_x, schild_y, schild_z)
                REFERENCES kistenshop (welt, schild_x, schild_y, schild_z)
                ON DELETE CASCADE
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
            """,

            // Schritt 18: die Entnahmen aus der Freien Truhe - Plan 7.2.
            //
            // EINE ZEILE JE SPIELER UND TAG, aufschlagend geschrieben. Dieselbe Bauart
            // wie spielzeit (Schritt 9), und der Grund ist derselbe: Wer dreimal am Tag
            // an die Truhe geht, soll dreimal zaehlen. Ein setzendes Schreiben verloere
            // die vorigen Griffe, und der Deckel waere so oft offen, wie man die Truhe
            // schliesst.
            //
            // tag traegt wie dort das ORTSDATUM unter der eingestellten Tagesgrenze,
            // gerechnet in Java (Tagesgrenze.tagVon) - nie CURDATE(). Die Sitzung steht
            // fest auf UTC, also nennte die Datenbank zwischen 00:00 und 01:00 Ortszeit
            // noch den Vortag.
            //
            // stueck als INT und nicht SMALLINT: Gedeckelt ist der Tag, aber der Deckel
            // ist einstellbar und 0 heisst KEIN Deckel - dann sammelt die Zeile, was ein
            // Spieler an einem Tag aus einer taeglich gefuellten Truhe traegt. Der Kern
            // rechnet in int und bricht bei einem Ueberlauf ab; eine engere Spalte braeche
            // frueher als die Speicherfassung, und dann verhielten sich zwei Umsetzungen
            // hinter einer Schnittstelle verschieden.
            //
            // ix_freie_truhe_tag ist der Index fuer das RAEUMEN, und anders als bei
            // spielzeit gibt es ihn wirklich: Der taegliche Lauf loescht alles von VOR
            // heute, also ueber den Tag und nicht ueber den Spieler. Ohne ihn liefe jeder
            // Tageswechsel ueber die ganze Tabelle, im Hauptfaden, auf der einen
            // Verbindung - also genau der Stillstand, gegen den der begrenzte Happen des
            // Blockbuchs gebaut ist.
            //
            // KEIN Fremdschluessel auf kassen: In der Truhe liegt kein Geld, sondern eine
            // Zaehlung von Stueck. Ein RESTRICT liesse den Griff eines Spielers
            // scheitern, der noch nie etwas verdient hat - und der nimmt aus einer freien
            // Truhe am ehesten. Ein CASCADE loeschte umgekehrt den Tagesstand eines
            // Spielers, dessen Konto je aufgeraeumt wird.
            //
            // IF NOT EXISTS wie bei den Schritten 7, 9, 10, 12, 13, 14, 15, 16 und 17.
            """
            CREATE TABLE IF NOT EXISTS freie_truhe (
              spieler CHAR(36) NOT NULL,
              tag     DATE     NOT NULL,
              stueck  INT      NOT NULL,
              PRIMARY KEY (spieler, tag),
              KEY ix_freie_truhe_tag (tag)
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
            """,

            // Schritt 19: der NAME einer Freistadt - Plan 6.4, gebaut am 11.11.2026.
            //
            // EINE SPALTE AN parzellen UND KEINE EIGENE TABELLE. Der Grund steht im Kopf
            // von Art: Alle Arten liegen in derselben Tabelle, damit ein Grundstueck in
            // einer Freistadt derselbe Datensatz ist wie eine Parzelle in der Marktstadt,
            // nur mit einem Elternteil. Eine Freistadt-Tabelle daneben wuerde den zweiten
            // Ort fuer "wem gehoert dieses Feld" erst SCHAFFEN - und die Schutzwache
            // muesste dann bei jedem gesetzten Block zwei Orte fragen statt einen.
            //
            // Der Unterschied zur Ladeneinheit (Schritt 13), die sehr wohl eine eigene
            // Tabelle bekam: Dort stand die Geometrie schon in der config.yml, und die
            // Zeile trug einen VERTRAG mit Miete und Ablauftag. Hier steht die Geometrie
            // nirgends sonst, und es kommt genau ein Feld dazu.
            //
            // NULL-faehig, weil 99 von 100 Zeilen keinen Namen haben. Und genau NULL und
            // nicht die leere Zeichenkette: InnoDB zaehlt NULL im eindeutigen Schluessel
            // als verschieden, also stoeren beliebig viele namenlose Parzellen einander
            // nicht. Mit einer leeren Zeichenkette gaebe es genau eine namenlose Parzelle
            // je Welt.
            //
            // VARCHAR(32) wie Ladeneinheit und wie Stadtname.HOECHSTENS_ZEICHEN. Eine
            // Regel, die mehr zulaesst als die Spalte traegt, verschiebt die Absage in
            // eine abgeschnittene Zeichenkette.
            //
            // uq_besitz_name IST DER RIEGEL und keine Bequemlichkeit: "Ist der Name schon
            // vergeben?" im Befehl zu fragen hat zwischen Fragen und Schreiben eine
            // Luecke, dieser Schluessel hat keine. Je WELT und nicht global - zwei
            // Stadtfelderwelten sind zwei getrennte Raeume, genau wie bei ort (welt,x,z).
            //
            // IF NOT EXISTS wie bei jedem Schritt ab dem vierten.
            """
            ALTER TABLE parzellen
              ADD COLUMN IF NOT EXISTS name VARCHAR(32) NULL DEFAULT NULL,
              ADD UNIQUE KEY IF NOT EXISTS uq_besitz_name (welt, name)
            """,

            // Schritt 20: die GRUNDSTUECKE in den Freistaedten - Plan 6.2, Teil C2,
            // gebaut am 12.11.2026.
            //
            // EINE EIGENE TABELLE, UND DAMIT GEGEN DEN ERSTEN ABSATZ VON SCHRITT 19.
            // Jener sagt: "Eine Freistadt-Tabelle daneben wuerde den zweiten Ort fuer
            // 'wem gehoert dieses Feld' erst SCHAFFEN." Das stimmt weiter - und darum
            // bleibt die FREISTADT in parzellen, mit ihren Feldnummern in x/z. Nur die
            // GRUNDSTUECKE darin ziehen hier aus, und zwar aus fuenf Gruenden, die alle
            // am freien Zuschnitt haengen (entschieden am 11.11.2026):
            //
            // 1. Parzelle.x und .z sind primitive int. Ein Besitz ohne Rasterzelle laesst
            //    sich gar nicht als Parzelle LESEN - MariaLager.lies wirft dann
            //    ausdruecklich ("liegt nicht auf dem Raster").
            // 2. uq ort (welt, x, z) laesst je Zelle genau EINE Zeile, und die Freistadt
            //    belegt dort schon ihre eigenen Feldnummern.
            // 3. mitbauerSetzen schreibt vom Elternteil auf ALLE Kinder durch. Ein
            //    Stadtbesitzer ueberschriebe damit die Mitbauer jedes VERKAUFTEN
            //    Grundstuecks - und genau das verkauft er ja gerade nicht mit.
            // 4. Jeder vorhandene Elternteil-Weg ist auf art='gruppe' festgenagelt:
            //    ausGruppeLoesen, auftrennen, erweitereGruppe, istGruppe.
            // 5. fk_besitz_elternteil steht auf ON DELETE RESTRICT. Eine Stadt mit
            //    verkauften Grundstuecken liesse sich nicht mehr aufloesen, und die
            //    Meldung dazu hiesse "erst auftrennen".
            //
            // DER GRUND, AUS DEM SCHRITT 19 AUF EINER TABELLE BESTAND, BLEIBT GEWAHRT.
            // Es ging um den heissen Pfad: darfBauen laeuft bei jedem gesetzten Block.
            // Deshalb stehen hier ZELLNUMMERN und keine Bloecke (eine Zelle ist 16x16,
            // ein Stadtfeld traegt 16x16 davon), und deshalb liegt vor dieser Tabelle ein
            // VOLLSTAENDIGER Zwischenspeicher ohne Verfall. Die Frage "wem gehoert dieser
            // Block" fasst die Datenbank nie an - sie wird im Arbeitsspeicher aus
            // hoechstens 256 Quadern je Stadt beantwortet.
            //
            // Die Ladeneinheit (Schritt 13) hat aus demselben Grund schon ihre eigene
            // Tabelle: Dort trug die Zeile einen Vertrag, hier traegt sie eine Geometrie,
            // die in parzellen keine Spalte hat.
            //
            // VON/BIS IN ZELLEN, und der CHECK haelt sie normiert: von <= bis. Ein
            // verdrehter Quader waere eine Flaeche ohne Inhalt, und die
            // Ueberschneidungspruefung liesse jede zweite Zeile daneben durch. Der Kern
            // dreht in Zellquader.zwischen, aber ein Riegel in der Ablage kostet nichts
            // und faengt jeden anderen Schreibweg.
            //
            // KEIN eindeutiger Schluessel ueber die Flaeche, und das ist kein Versaeumnis:
            // Zwei Quader koennen sich UEBERSCHNEIDEN, ohne gleich zu sein - dagegen gibt
            // es keinen Schluessel. Den Riegel stellt stattdessen ein SELECT ... FOR
            // UPDATE auf die Grundstuecke der Stadt in DERSELBEN Transaktion wie das
            // INSERT; siehe MariaGrundstuecke.anlegen. Das ist der Unterschied zum Namen
            // in Schritt 19, wo ein Schluessel wirklich dichtmacht.
            //
            // fk_grundstueck_stadt auf parzellen mit ON DELETE RESTRICT: Eine Freistadt,
            // in der Grundstuecke liegen, darf nicht verschwinden. Dieselbe Richtung wie
            // beim Elternteil - und dieselbe Begruendung: Verweigern ist die
            // zurueckholbare Antwort.
            //
            // nach_welt ist der Index fuer das FUELLEN des Zwischenspeichers, also fuer
            // genau eine Abfrage je Serverstart. nach_stadt fuer die Liste des
            // Stadtbesitzers, nach_eigentuemer fuer die eigene Liste eines Spielers. Fuer
            // die Punktabfrage gibt es ABSICHTLICH keinen: Sie laeuft nie gegen die
            // Datenbank.
            """
            CREATE TABLE IF NOT EXISTS grundstuecke (
              id BIGINT NOT NULL AUTO_INCREMENT,
              welt VARCHAR(128) NOT NULL,
              stadt_id BIGINT NOT NULL,
              eigentuemer CHAR(36) NOT NULL,
              von_x INT NOT NULL,
              von_z INT NOT NULL,
              bis_x INT NOT NULL,
              bis_z INT NOT NULL,
              preis BIGINT NOT NULL DEFAULT 0,
              erworben_am DATETIME(3) NOT NULL,
              PRIMARY KEY (id),
              KEY nach_welt (welt),
              KEY nach_stadt (stadt_id),
              KEY nach_eigentuemer (welt, eigentuemer),
              CONSTRAINT fk_grundstueck_stadt FOREIGN KEY (stadt_id)
                REFERENCES parzellen (id) ON DELETE RESTRICT,
              CONSTRAINT ck_grundstueck_normiert
                CHECK (von_x <= bis_x AND von_z <= bis_z)
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
            """,

            // Schritt 21: die Mitbauer eines Grundstuecks.
            //
            // EINE EIGENE NEBENTABELLE, wie parzellen_mitbauer (Schritt 2), und NICHT
            // dieselbe: Die Fremdschluessel zeigen auf verschiedene Tabellen, und ein
            // gemeinsamer Elternschluessel liesse sich gar nicht bilden. Zwei Zeilen mit
            // derselben Nummer waeren sonst ein Grundstueck und eine Parzelle.
            //
            // ON DELETE CASCADE wie dort: Wird ein Grundstueck aufgeloest, sind seine
            // Mitbauer gegenstandslos. Das ist die andere Richtung als beim
            // Stadt-Fremdschluessel darueber, und beide sind richtig - ein Mitbauer ist
            // kein Besitz, der verloren gehen koennte.
            """
            CREATE TABLE IF NOT EXISTS grundstuecke_mitbauer (
              grundstueck_id BIGINT NOT NULL,
              spieler CHAR(36) NOT NULL,
              PRIMARY KEY (grundstueck_id, spieler),
              CONSTRAINT fk_grundstueck_mitbauer FOREIGN KEY (grundstueck_id)
                REFERENCES grundstuecke (id) ON DELETE CASCADE
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
            """,

            // Schritt 22: der ANGEBOTSPREIS eines Grundstuecks - Plan 6.2, Teil C3,
            // gebaut am 12.11.2026. Damit ist das Abnahmekriterium aus Plan 15
            // erreichbar: "ein Spieler verkauft einem anderen ein Grundstueck".
            //
            // NULL-FAEHIG, UND NULL IST NICHT NULL MARK. Das ist die ganze Entscheidung
            // dieser Spalte:
            //
            //   * NULL heisst "steht nicht zum Verkauf". Die meisten Zeilen sind das.
            //   * 0 heisst "zu verschenken" - und das ist ein Fall, den es geben MUSS:
            //     Ein Stadtbesitzer, der Buerger anwirbt, gibt die ersten Grundstuecke
            //     gratis. Mit einer 0 als "kein Angebot" waere genau das unmoeglich, und
            //     niemand koennte sagen warum.
            //
            // Darum Long und nicht long im Kern, und darum NULL DEFAULT NULL hier. Die
            // Spalte preis daneben bleibt, was sie war: was es GEKOSTET hat. Zwei
            // verschiedene Fragen, zwei Spalten - "was es wert sein soll" und "was
            // bezahlt wurde" laufen nach dem ersten Weiterverkauf auseinander.
            //
            // KEIN CHECK auf eine Obergrenze: Plan 16 hat die Preisfreiheit ausdruecklich
            // entschieden ("Der Stadtbesitzer setzt seine Preise frei; Wucher regelt sich
            // von selbst, weil daneben die Marktstadt mit gratis erster Parzelle liegt").
            // Die obere Grenze ist die des Geldes selbst und steht im Kern.
            //
            // ix_grundstueck_angebot auf (welt, angebotspreis): Die Liste der Angebote
            // einer Welt ist die eine Abfrage, die diese Spalte filtert - und sie laeuft
            // auf Befehl, nicht auf dem heissen Pfad. Ein Index ueber eine NULL-faehige
            // Spalte hilft hier trotzdem: InnoDB kann die vielen NULL-Zeilen damit
            // ueberspringen, und die sind die Mehrheit.
            """
            ALTER TABLE grundstuecke
              ADD COLUMN IF NOT EXISTS angebotspreis BIGINT NULL DEFAULT NULL,
              ADD KEY IF NOT EXISTS ix_grundstueck_angebot (welt, angebotspreis)
            """,

            // Schritt 23: die BUERGERRECHTE einer Freistadt - Teil D, 12.11.2026. Was
            // die Stadtbuerger im Gemeinschaftsgebiet duerfen, stellt der Stadtbesitzer
            // je Stadt ein.
            //
            // EINE ZEILE JE FREIGESCHALTETES RECHT und keine Spalte mit Flaggenbits, und
            // das hat drei Gruende. Erstens: Ein viertes Recht braucht dann keinen
            // Schemaschritt - es ist eine Zeile mehr, kein ALTER TABLE. Zweitens: Was
            // dasteht, ist lesbar - ein Betreiber sieht in der Tabelle "behaelter" und
            // nicht die Zahl 5. Drittens ist es die Bauart, die dieses Projekt fuer
            // Mengen schon zweimal gewaehlt hat (parzellen_mitbauer,
            // kistenshop_freigabe), und eine dritte Bauart fuer dieselbe Sache waere die
            // Stelle, an der jemand die falsche Annahme mitbringt.
            //
            // ON DELETE CASCADE wie bei parzellen_mitbauer: Wird eine Stadt aufgeloest,
            // sind ihre Buergerrechte gegenstandslos. Kein RESTRICT - ein Recht ist kein
            // Besitz, der verloren gehen koennte.
            //
            // KEIN Fremdschluessel auf eine Rechtetabelle: Welche Rechte es gibt, steht
            // in der Aufzaehlung Tat im Kern. Eine zweite Liste in der Datenbank waere
            // eine zweite Wahrheit darueber - und bei jedem neuen Recht muessten beide
            // gepflegt werden. Was hier steht und der Kern nicht kennt, faellt beim
            // Lesen mit Namen auf (Tat.ausSchluessel wirft), und das ist die sichere
            // Richtung: Ein stilles Ueberspringen waere ein Recht, das der Betreiber
            // eingetragen hat und das lautlos nicht gilt.
            """
            CREATE TABLE IF NOT EXISTS freistadt_rechte (
              stadt_id BIGINT      NOT NULL,
              recht    VARCHAR(32) NOT NULL,
              PRIMARY KEY (stadt_id, recht),
              CONSTRAINT fk_freistadt_recht FOREIGN KEY (stadt_id)
                REFERENCES parzellen (id) ON DELETE CASCADE
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
            """,

            // Schritt 24: die KONTONUMMER der Stadtkasse - Teil E, 16.11.2026.
            //
            // EINE GESPEICHERTE NUMMER UND KEINE AUS DER id GERECHNETE, und das ist der
            // Kern der Sache: id kommt aus AUTO_INCREMENT, und AUTO_INCREMENT vergibt
            // nach einem Neustart wieder, was ganz hinten frei geworden ist. Eine
            // Kontonummer, die man aus der id rechnet, haette damit das Konto einer
            // entfernten Stadt an die naechste mit derselben id weitergegeben -
            // Guthaben und Buchungsverlauf eingeschlossen. Das ist eine Luege ueber
            // Geld, und sie faellt erst auf, wenn jemand den Verlauf liest.
            //
            // CHAR(36) und nicht BINARY(16), wie ueberall in diesem Schema: Ein
            // Betreiber, der in die Tabelle sieht, soll die Nummer lesen koennen.
            //
            // NULL-FAEHIG, weil 99 von 100 Zeilen keine Stadt sind - dieselbe
            // Entscheidung wie bei name in Schritt 19, und mit derselben Folge: Was
            // beide Richtungen angeht, prueft Parzelle im Konstruktor, nicht ein CHECK
            // ueber zwei Spalten.
            //
            // KEIN eindeutiger Schluessel darauf, und das ist eine bewusste Auslassung:
            // Die Nummer ist ein Zufallswert aus 122 Bit, zwei gleiche sind nicht zu
            // erwarten - ein UNIQUE auf einer NULL-faehigen Spalte, in der fast nur
            // NULL steht, kostet dagegen einen Index ueber die ganze Tabelle. Dass
            // keine zweimal vorkommt, prueft der Vertrag der Ablage.
            """
            ALTER TABLE parzellen
              ADD COLUMN IF NOT EXISTS kasse CHAR(36) NULL
            """,

            // Schritt 25: die Nummern fuer die Staedte, die es SCHON GIBT.
            //
            // Die zweite Ausnahme von "jeder Schritt ist DDL", nach Schritt 8, und sie
            // ist aus demselben Grund noetig: Ohne sie traegt jede heute bestehende
            // Freistadt NULL, und Parzelle wirft beim Lesen - der Server startete nicht
            // mehr. Ein nachtraegliches Anlegen beim ersten Geldfluss waere die
            // Alternative, und sie waere schlechter: ein zweiter Schreibweg mitten im
            // Geld.
            //
            // UUID() ist MariaDBs eigene und damit dieselbe Form wie die aus Java -
            // 36 Zeichen mit Bindestrichen. Welche Fassung die Nummer traegt, liest
            // niemand aus.
            //
            // Das WHERE traegt den zweiten Lauf: Ein Schritt, der zweimal laeuft, darf
            // nicht zweimal wirken, und migriere() legt um Anweisung und
            // Standfortschreibung keine Transaktion. Mit kasse IS NULL bekommt jede
            // Stadt genau einmal eine Nummer - auch wenn der Server zwischen der
            // Anweisung und dem Stand ausfaellt.
            """
            UPDATE parzellen SET kasse = UUID()
              WHERE art = 'freistadt' AND kasse IS NULL
            """,

            // Schritt 26: der REGELTEXT einer Freistadt - Teil E, 16.11.2026.
            //
            // Was Besucher in dieser Stadt beachten sollen, vom Besitzer gesetzt. Die
            // Auflage aus Plan 16 haengt daran, und zwar MIT BEIDEN WIRKUNGEN: Erst
            // mit Regeln UND einem vergebenen Grundstueck bekommt eine Stadt ihren
            // Teleporter - und das Recht, dass in ihr ein neuer Kistenshop entsteht.
            // Wer diese Spalte kuerzt, nullbar macht oder beim Aufraeumen leert, nimmt
            // jeder betroffenen Stadt beides auf einmal.
            //
            // EINE SPALTE UND KEINE TABELLE, obwohl "Regelwand" nach mehreren Zeilen
            // klingt: Gesetzt wird der Text mit einem Befehl, und ein Befehl tippt eine
            // Zeile. Mehrere Zeilen waeren eine Tabelle, eine Reihenfolge, ein
            // Loeschweg je Zeile und eine zweite Frage, was "hat Regeln" heisst - fuer
            // etwas, das heute niemand eingeben kann.
            //
            // VARCHAR(256) und NICHT TEXT: Die Laenge steht damit in der Spalte und
            // nicht nur im Java-Riegel, und 256 Zeichen sind im Chat schon vier Zeilen.
            // Ein TEXT haette keine Grenze, und die erste Regelwand mit zehntausend
            // Zeichen waere eine Chatflut, die jeder Besucher beim Betreten liest.
            //
            // NULL-faehig, und hier heisst NULL etwas anderes als bei der Kontonummer:
            // NICHT "Fehler", sondern "noch keine Regeln". Das ist der Zustand jeder
            // frisch gegruendeten Stadt - darum hat Parzelle fuer die Regeln nur EINEN
            // Riegel und nicht zwei.
            """
            ALTER TABLE parzellen
              ADD COLUMN IF NOT EXISTS regeln VARCHAR(256) NULL
            """,

            // Schritt 27: der BESUCHSTAG einer Freistadt - Teil E, 17.11.2026.
            //
            // Der Tag, an dem zuletzt ein Spieler in dieser Stadt stand. Daran haengt
            // die Verfallsregel aus Plan 16: Eine Stadt, die zwoelf Monate unbesucht
            // ist UND keinen Stadtbuerger hat, ist eine Geisterstadt und blockiert ein
            // Feld, das jemand anderes bebauen wuerde.
            //
            // EIN DATE UND KEIN DATETIME, und das ist dieselbe Ausnahme von "alles
            // UTC", die der Kopf dieser Klasse fuer spielzeit.tag und
            // tagesbonus.letzter_tag begruendet: Gefragt ist der TAG unter zeit.zone.
            // Ein Zeitpunkt haette eine zweite Zone, und die Frist waere je nach
            // Leseort einen Tag laenger oder kuerzer. Geschrieben und gelesen wird mit
            // setObject/getObject(LocalDate.class), nie mit java.sql.Date - jenes
            // reist ueber die Zone der JVM, und valueOf und toLocalDate heben sich
            // dabei gegenseitig auf, sodass keine Probe rot wird.
            //
            // NULL-faehig, und NULL heisst "noch nie besucht" - nicht "1970". Eine
            // frisch gegruendete Stadt steht so da, und ihre Frist laeuft ab der
            // GRUENDUNG: erworben_am steht in derselben Zeile.
            //
            // KEIN INDEX, anders als bei freie_truhe.tag: Gelesen wird diese Spalte
            // vom Verfallslauf, der ohnehin alle Staedte einer Welt durchgeht
            // (besitzIn), und vom Stempel, der die Zeile schon in der Hand hat. Ein
            // Index waere Pflege fuer eine Suche, die es nicht gibt.
            """
            ALTER TABLE parzellen
              ADD COLUMN IF NOT EXISTS besucht DATE NULL
            """,

            // Schritt 28: die Art WOLKE - Schritt 11, 18.11.2026.
            //
            // Eine Skyblockinsel in Wolkenwerder. Sie traegt dieselben Spalten wie eine
            // Meerinsel - Feldnummern in x und z, keinen Namen, keine Kasse -, und
            // trotzdem braucht sie ihre eigene Art: Nur so laesst sich nach ihr FRAGEN.
            // Die Zuteilung in einer leeren Welt sucht das naechste freie Feld aus der
            // Liste der vergebenen (besitzIn), und mit art='werder' fuer beide Welten
            // liefe diese Abfrage ueber Hafenwerder mit.
            //
            // EIN EIGENER SCHRITT UND NICHT SCHRITT 4, obwohl das ENUM dort steht: Ein
            // geaenderter Schritt wirkt auf keiner laufenden Datenbank - migriere()
            // fuehrt nur Schritte ab dem gespeicherten Stand aus. Auf einem Server, der
            // schon lief, waere die neue Art damit stumm nicht vorhanden, und das erste
            // Nehmen scheiterte an der Spalte. SchemaTest haelt jeden Schritt mit einer
            // Pruefsumme fest, genau dagegen.
            //
            // MODIFY COLUMN IF EXISTS und das ENUM vollstaendig aufgezaehlt: MariaDB
            // kennt kein "ADD VALUE". Die Reihenfolge der Werte bedeutet nichts
            // (gespeichert wird der Text, nicht die Ordnungszahl), aber die Liste muss
            // JEDEN alten Wert nennen - ein fehlender machte die Spalte jeder alten
            // Zeile ungueltig. Kein UPDATE auf Altbestand: Diese Art hat heute niemand.
            """
            ALTER TABLE parzellen
              MODIFY COLUMN IF EXISTS art
                  ENUM('parzelle','gruppe','werder','freistadt','ladeneinheit',
                       'wolke')
                  NOT NULL DEFAULT 'parzelle'
            """,

            // Schritt 29: der ZUSTAND einer Wolkeninsel - Schritt 11, 18.11.2026.
            //
            // Biom, Punktzahl und der Zeitpunkt des letzten Blockzaehlens. Eine Zeile je
            // Insel, und sie entsteht erst beim ersten Schreiben: Ein Stand aus lauter
            // Vorgabewerten sagt nichts, und 1.443 solche Zeilen waeren eine Tabelle,
            // die nur Platz braucht.
            //
            // EINE EIGENE TABELLE UND KEINE SPALTEN AN parzellen, und das ist dieselbe
            // Entscheidung wie bei den Grundstuecken: Jene Zeile liegt auf dem heissen
            // Pfad - sie wird bei JEDEM gesetzten und JEDEM abgebauten Block gelesen, in
            // jeder Welt. Drei Spalten, die nur eine von sechs Arten fuellt, kaemen bei
            // jedem Klick mit. Dazu haette ein viertes Feld an Parzelle 43 Aufrufstellen
            // angefasst, von denen drei die Zeile von Hand nachbauen - und dort gingen
            // sie LAUTLOS verloren (der Fund vom 11.11.2026).
            //
            // biom NULL-FAEHIG, und NULL heisst "nie gewaehlt" und nicht "Fehler". Was
            // dann gilt, sagt Wolkenbiome.VORGABE im Programm - die Spalte beschreibt,
            // was der Spieler getan hat, und nicht, was gilt.
            //
            // gezaehlt_am als DATETIME(3) in UTC, wie jede Zeitspalte dieses Schemas
            // ausser den drei TAGesspalten. Begruendung im Kopf dieser Klasse.
            //
            // KEIN Index auf punkte, obwohl die Rangliste danach sortiert: Die Tabelle
            // hat hoechstens so viele Zeilen, wie die Welt Felder hat (1.443), und sie
            // wird fuer die Rangliste ganz gelesen. Ein Index waere Pflege fuer eine
            // Sortierung ueber eine Handvoll Kilobytes.
            """
            CREATE TABLE IF NOT EXISTS wolkeninsel (
              parzelle_id BIGINT       NOT NULL,
              biom        VARCHAR(32)  NULL,
              punkte      BIGINT       NOT NULL DEFAULT 0,
              gezaehlt_am DATETIME(3)  NULL,
              PRIMARY KEY (parzelle_id),
              CONSTRAINT fk_wolkeninsel_feld FOREIGN KEY (parzelle_id)
                REFERENCES parzellen (id) ON DELETE CASCADE
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
            """,

            // Schritt 30: die ERFUELLTEN HERAUSFORDERUNGEN - Schritt 11, 18.11.2026.
            //
            // Eine Zeile je Insel und Herausforderung. Die Zahl dieser Zeilen IST die
            // Insel-Stufe, an der die Erzausbeute des Kobbelsteingenerators haengt - eine
            // eigene Spalte stufe daneben waere eine zweite Wahrheit, und die erste
            // Zeile, die beim Erfuellen nicht mitgeschrieben wird, liesse die Ausbeute
            // hinter den Herausforderungen zurueckbleiben.
            //
            // EINE EIGENE TABELLE, weil es eine MENGE ist - dieselbe Bauart wie
            // parzellen_mitbauer, freistadt_rechte und kistenshop_freigabe. Eine Liste in
            // einer Spalte waere die vierte Bauart fuer dieselbe Sache, und die erste, bei
            // der jemand eine falsche Annahme mitbringt.
            //
            // DER HAUPTSCHLUESSEL IST DER RIEGEL gegen die doppelte Belohnung: Das
            // Erfuellen schreibt mit INSERT IGNORE, und die Zahl der geschriebenen Zeilen
            // ist die Antwort auf "war es neu". Eine Pruefung im Programm davor haette
            // zwischen Fragen und Schreiben eine Luecke, und zwei Abgaben im selben
            // Augenblick zahlten zweimal.
            //
            // FREMDSCHLUESSEL AUF parzellen MIT CASCADE, wie bei freistadt_rechte: Wer
            // seine Insel freigibt, gibt ihren Fortschritt mit auf. Ohne das erbte der
            // naechste Spieler auf diesem Feld die Erzausbeute eines Fremden.
            """
            CREATE TABLE IF NOT EXISTS wolke_herausforderung (
              parzelle_id BIGINT      NOT NULL,
              schluessel  VARCHAR(32) NOT NULL,
              PRIMARY KEY (parzelle_id, schluessel),
              CONSTRAINT fk_wolke_herausforderung FOREIGN KEY (parzelle_id)
                REFERENCES parzellen (id) ON DELETE CASCADE
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
            """,

            // Schritt 31: der RANG eines Spielers - Schritt 12 Teil C, 19.11.2026.
            //
            // EINE Zeile je Spieler, und sie entsteht erst beim ersten AUFSTIEG. Wer nie
            // aufgestiegen ist, hat keine Zeile, und das heisst: unterste Stufe. Zwei
            // Gruende, und beide stehen ausfuehrlich an Rangablage: Eine Zeile je
            // Besucher waere eine Tabelle, die nur Platz braucht - und wer eine Stufe aus
            // der config.yml nimmt, strandet sonst jeden, der auf ihr stand.
            //
            // KEIN VERLAUF, also PRIMARY KEY (spieler) und nicht (spieler, rang): Auf
            // die Frage "welchen Rang hat er" muss es genau EINE Antwort geben. Mit
            // einer Zeile je erreichter Stufe waere die Antwort "die juengste", und die
            // waere nach einer umsortierten Leiter die falsche. Wer den Verlauf will,
            // bekommt eine zweite Tabelle wie das Blockbuch und nicht eine zweite
            // Bedeutung fuer diese.
            //
            // rang als VARCHAR(32) und nicht als ENUM: Die Stufen stehen in der
            // config.yml und sind frei benennbar - ein ENUM waere die Leiter ein zweites
            // Mal, und ein neuer Rang in der Datei braeuchte einen Schemaschritt. Die 32
            // sind dieselben wie Rangwerte.HOECHSTE_SCHLUESSELLAENGE; wer eine davon
            // aendert, muss die andere mitnehmen, sonst scheitert der erste Aufstieg mit
            // einem langen Namen als Datenbankfehler mitten im Spiel.
            //
            // erreicht_am als DATETIME(3) in UTC, wie jede Zeitspalte dieses Schemas
            // ausser den drei TAGesspalten. NOT NULL, weil ein Rang ohne Zeitpunkt ein
            // halber Stand waere: Rangstand laesst ihn gar nicht erst bauen, und die
            // Spalte haelt dieselbe Zusage noch einmal.
            //
            // KEIN FREMDSCHLUESSEL auf kassen, wie bei berufe: Ein RESTRICT brachte den
            // Aufstieg zum Scheitern, sobald eine Kontoeroeffnung einmal fehlgeschlagen
            // ist; ein CASCADE loeschte Raenge stumm mit dem Konto.
            //
            // KEIN INDEX auf rang, obwohl "wie viele stehen auf welcher Stufe" eine
            // naheliegende Frage ist: Sie hat heute keinen Leser. Ein Index waere Pflege
            // fuer eine Suche, die es nicht gibt - dieselbe Entscheidung wie bei
            // wolkeninsel.punkte. Er kommt mit dem ersten Leser, als eigener Schritt.
            """
            CREATE TABLE IF NOT EXISTS raenge (
              spieler     CHAR(36)    NOT NULL,
              rang        VARCHAR(32) NOT NULL,
              erreicht_am DATETIME(3) NOT NULL,
              PRIMARY KEY (spieler)
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
            """,

            // Schritt 32: die GOLDMARK-Konten - Schritt 12 Teil E, 19.11.2026.
            //
            // EINE EIGENE TABELLE UND KEINE WAEHRUNGSSPALTE [entschieden 18.11.2026].
            // Zwei Waehrungen in kassen addieren sich zu einem Stand, und die tragende
            // Zusage des Buchungsbuchs - "fuer ein eroeffnetes Konto ist die Summe aller
            // Buchungen sein Stand" - wuerde diesen Stand BESTAETIGEN, obwohl er zwei
            // Dinge zusammenwirft. Die Summenprobe waere gruen und das Geld falsch. Mit
            // zwei Tabellenmengen gilt die Zusage je Waehrung, und der geteilte
            // KasseVertrag prueft sie zweimal.
            //
            // DER UNTERSCHIED ZUR STAEDTEKASSE: Jene liegt ausdruecklich auf DERSELBEN
            // Tabelle wie die Spielerkonten (siehe den Klassenkopf von Stadtkasse) - eine
            // Stadtkennung und eine Spielerkennung sind nie dieselbe, und es ist dieselbe
            // Waehrung. Hier ist es eine ANDERE Waehrung.
            //
            // Die Spalte heisst goldmark und nicht mark. Derselbe Name waere der kuerzere
            // Weg - die SQL-Saetze unterschieden sich dann nur im Tabellennamen - und eine
            // Luege, auf die jeder spaetere Leser hereinfaellt.
            //
            // KEIN FREMDSCHLUESSEL auf kassen: Wer Goldmark hat, muss kein Markkonto
            // haben. Ein RESTRICT brachte die erste Goldmarkbuchung zum Scheitern, wenn
            // das Markkonto fehlt; ein CASCADE loeschte gekaufte Goldmark mit ihm.
            //
            // KEIN Altbestandsschritt wie Schritt 8 fuer die Mark: Es gibt heute keine
            // Goldmark, also auch keine Geschichte, die nachzutragen waere.
            """
            CREATE TABLE IF NOT EXISTS goldkassen (
              spieler  CHAR(36) NOT NULL,
              goldmark BIGINT   NOT NULL,
              PRIMARY KEY (spieler)
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
            """,

            // Schritt 33: das Buch der GOLDMARK - Schritt 12 Teil E, 19.11.2026.
            //
            // Spaltengleich mit buchungen (Schritt 7), und das ist Absicht: Dieselbe
            // Klasse MariaKasse liest beide, und die Spaltennamen stehen in ihren
            // SQL-Saetzen. Eine abweichende Spalte hier waere eine zweite Leseweise fuer
            // dasselbe Buch.
            //
            // betrag VORZEICHENBEHAFTET und stand_nachher dabei - die Begruendung steht
            // vollstaendig am Schritt 7 und gilt hier unveraendert.
            //
            // EIGENER FREMDSCHLUESSELNAME: fk_goldbuchung_kasse. Namen von
            // Fremdschluesseln sind im Schema eindeutig; fk_buchung_kasse ein zweites Mal
            // haette den Schritt mit einem Fehler abgebrochen, den niemand erwartet.
            // Und er zeigt auf goldkassen, nicht auf kassen - ein Buch, das auf die
            // falsche Kasse zeigt, haelt seine Zusage fuer die andere Waehrung.
            """
            CREATE TABLE IF NOT EXISTS goldbuchungen (
              id            BIGINT      NOT NULL AUTO_INCREMENT,
              spieler       CHAR(36)    NOT NULL,
              zeitpunkt     DATETIME(3) NOT NULL,
              grund         VARCHAR(32) NOT NULL,
              betrag        BIGINT      NOT NULL,
              stand_nachher BIGINT      NOT NULL,
              PRIMARY KEY (id),
              KEY nach_spieler (spieler, zeitpunkt, id),
              CONSTRAINT fk_goldbuchung_kasse FOREIGN KEY (spieler)
                  REFERENCES goldkassen (spieler) ON DELETE RESTRICT
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
            """,

            // Schritt 34: die ANNAHME der Regeln - Schritt 13 Teil C, 21.11.2026.
            //
            // EINE Zeile je Spieler, und sie entsteht erst bei der ANNAHME. Wer nie
            // angenommen hat, hat keine Zeile - und das ist jeder, der zum ersten Mal
            // ankommt. Die Begruendung steht ausfuehrlich an Anmeldeablage; sie ist
            // dieselbe wie bei raenge: Eine Zeile je Besucher waere eine Tabelle, die
            // nur Platz braucht.
            //
            // DIE FASSUNG IST DER GRUND, WARUM HIER MEHR STEHT ALS EIN ZEITPUNKT. Plan
            // 10.1 verlangt die Annahme von "Regeln UND Nutzungsbedingungen". Aendert
            // der Betreiber die Regeln, ist eine Zustimmung von vorher keine Zustimmung
            // zu den neuen - sie waere eine stille Halbheit, die aussieht wie ein
            // Nachweis. Welche Fassung HEUTE gilt, steht in der config.yml
            // (anmeldung.fassung); hier steht, was angenommen WURDE.
            //
            // KEIN VERLAUF, also PRIMARY KEY (spieler) und nicht (spieler, fassung): Auf
            // die Frage "hat er die heutigen Regeln angenommen" muss es genau EINE
            // Antwort geben. Wer den Verlauf aller Zustimmungen braucht - und bei
            // Nutzungsbedingungen kann das sein -, bekommt eine zweite Tabelle wie das
            // Blockbuch und nicht eine zweite Bedeutung fuer diese.
            //
            // fassung als VARCHAR(32) und nicht als DATE: Was der Betreiber
            // hineinschreibt, ist seine Sache - ein Datum, eine Nummer oder beides. Die
            // 32 sind dieselben wie Anmeldestand.HOECHSTE_FASSUNGSLAENGE; wer eine davon
            // aendert, muss die andere mitnehmen, sonst scheitert die erste Annahme mit
            // einer langen Fassung als Datenbankfehler mitten im Spiel.
            //
            // angenommen_am als DATETIME(3) in UTC, wie jede Zeitspalte dieses Schemas
            // ausser den drei TAGesspalten. NOT NULL, weil eine Zustimmung ohne Datum
            // kein Nachweis ist: Anmeldestand laesst sie gar nicht erst bauen, und die
            // Spalte haelt dieselbe Zusage noch einmal.
            //
            // KEIN FREMDSCHLUESSEL auf kassen, wie bei raenge und berufe: Ein RESTRICT
            // brachte die Annahme zum Scheitern, sobald eine Kontoeroeffnung einmal
            // fehlgeschlagen ist; ein CASCADE loeschte den Nachweis stumm mit dem Konto.
            // Und gerade dieser Nachweis darf nicht an einem Konto haengen - angenommen
            // hat ein Mensch, nicht ein Guthaben.
            //
            // KEIN INDEX auf fassung, obwohl "wer hat die neuen Regeln noch nicht
            // angenommen" eine naheliegende Frage ist: Sie hat heute keinen Leser. Er
            // kommt mit dem ersten, als eigener Schritt.
            """
            CREATE TABLE IF NOT EXISTS anmeldungen (
              spieler      CHAR(36)    NOT NULL,
              fassung      VARCHAR(32) NOT NULL,
              angenommen_am DATETIME(3) NOT NULL,
              PRIMARY KEY (spieler)
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
            """,

            // Schritt 35: der RUNDGANG durch Altfurt - Schritt 13 Teil D, 21.11.2026.
            //
            // EINE Zeile je Spieler UND Station, also PRIMARY KEY (spieler, station) -
            // und nicht eine Zeile je Spieler mit einer Zahl. Der Grund steht
            // ausfuehrlich an Rundgangsablage: Eine Zahl saehe nicht, WELCHE Station
            // gesehen wurde, und der Betreiber darf seinen Weg aendern. Nach dem
            // Hinzufuegen einer Station waere der Rundgang mit einer Zahl weiter
            // abgeschlossen, und ein Spieler haette eine Station nie gesehen, die zum
            // Weg gehoert.
            //
            // DER HAUPTSCHLUESSEL IST DER RIEGEL gegen die doppelte Zeile: Geschrieben
            // wird mit INSERT IGNORE, und die Zahl der geschriebenen Zeilen ist die
            // Antwort auf "war es neu". An dieser Antwort haengt, ob der Spieler den
            // Stationstext liest. Dasselbe Muster wie bei wolke_herausforderung
            // (Schritt 30).
            //
            // station als VARCHAR(32) und nicht als ENUM: Welche Stationen es gibt,
            // steht in der config.yml und ist frei benennbar - ein ENUM waere der Weg
            // ein zweites Mal, und eine neue Station braeuchte einen Schemaschritt. Die
            // 32 sind dieselben wie Rundgangsstation.HOECHSTE_SCHLUESSELLAENGE; wer eine
            // davon aendert, muss die andere mitnehmen.
            //
            // gesehen_am als DATETIME(3) in UTC, wie jede Zeitspalte dieses Schemas
            // ausser den drei TAGesspalten. Gelesen wird er heute von NIEMANDEM - er
            // steht hier, weil eine Zeile ohne Zeitpunkt nichts kostet und ein Betreiber
            // eines Tages nachsehen will, wann ein Spieler wo war. NOT NULL, damit es
            // keine halbe Zeile gibt.
            //
            // KEIN FREMDSCHLUESSEL auf kassen, wie bei raenge, berufe und anmeldungen:
            // Ein RESTRICT brachte den Rundgang zum Scheitern, sobald eine
            // Kontoeroeffnung einmal fehlgeschlagen ist; ein CASCADE loeschte den
            // Fortschritt stumm mit dem Konto.
            //
            // KEIN INDEX auf station, obwohl "wie viele haben das Zunfthaus gesehen"
            // eine naheliegende Frage ist: Sie hat heute keinen Leser. Der
            // Hauptschluessel beginnt mit spieler, und das ist die Richtung, in der
            // gefragt wird.
            """
            CREATE TABLE IF NOT EXISTS rundgang (
              spieler    CHAR(36)    NOT NULL,
              station    VARCHAR(32) NOT NULL,
              gesehen_am DATETIME(3) NOT NULL,
              PRIMARY KEY (spieler, station)
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
            """,

            // Schritt 36: die LOSE DER LOTTERIE - Schritt 14, 22.11.2026.
            //
            // EINE Zeile je Spieler UND Tag, also PRIMARY KEY (spieler, tag). Eine Zeile
            // je Spieler mit einer Zahl genuegte nicht: Der Preis haengt daran, wie viele
            // Lose HEUTE gezogen sind, und ohne den Tag im Schluessel muesste jemand die
            // Zahl beim Tageswechsel zuruecksetzen - ein Lauf, den es nicht gibt und der
            // auf einem Server, der ueber Mitternacht aus war, nie liefe.
            //
            // HIER LIEGT GELD, und das ist der Unterschied zu tagesbonus (Schritt 29).
            // Dort steht die Wahrheit im Buchungsbuch, und ein Verlust der Zeile kostet
            // nur eine Serie. Hier ist diese Zahl die EINZIGE Wahrheit darueber, was das
            // naechste Los kostet: Das erste Los am Tag ist gratis und wird mit 0 Mark
            // gebucht, aus einer Summe von 0 ist also "noch keines gezogen" nicht von
            // "das gratis Los ist weg" zu unterscheiden. Ein Verlust dieser Zeile ist
            // damit eine Geldquelle, und darum zaehlt Lotteriebuch ZUERST und zahlt
            // danach.
            //
            // tag als DATE und nicht als DATETIME: Es ist einer der TAGesspalten dieses
            // Schemas, die bewusst NICHT in UTC stehen (wie tagesbonus.letzter_tag und
            // die zwei Tagesspalten davor). Welcher Tag gilt, entscheidet allein
            // Tagesgrenze.tagVon aus der eingestellten Zone - kein CURDATE(), kein
            // UTC_DATE(). Die Sitzung steht fest auf UTC, also nennte die Datenbank
            // zwischen 00:00 und 01:00 Ortszeit noch den Vortag.
            //
            // GESCHRIEBEN WIRD AUFSCHLAGEND, mit ON DUPLICATE KEY UPDATE anzahl =
            // anzahl + 1 - anders als bei tagesbonus, das setzt. Die Zahl ist eine
            // ZAEHLUNG und kein Zustand: Zwei Server, die beide ein Los melden, meinen
            // zwei Lose, und es sind zwei. Setzend verschluckte eines davon, und das
            // verbilligt das naechste.
            //
            // anzahl als INT und nicht SMALLINT: 32.767 Lose an einem Tag sind bei der
            // ausgelieferten Tagesgrenze von 20 unerreichbar, aber die Grenze ist eine
            // EINSTELLUNG und darf 0 (unbegrenzt) sein. Ein ueberlaufender SMALLINT
            // waere dann eine negative Zahl, und der Konstruktor von Losstand wuerfe
            // mitten im Spiel.
            //
            // KEIN FREMDSCHLUESSEL auf kassen, wie bei raenge, berufe, anmeldungen und
            // rundgang: Ein RESTRICT brachte die Lotterie zum Scheitern, sobald eine
            // Kontoeroeffnung einmal fehlgeschlagen ist; ein CASCADE loeschte die
            // Loszahl stumm mit dem Konto - und damit gaebe eine Kontoloeschung dem
            // Spieler sein gratis Los zurueck.
            //
            // KEIN RAEUMLAUF fuer alte Tage, und das ist eine bewusste Luecke: Eine
            // Zeile ist 44 Byte, und ein Server mit hundert taeglichen Spielern legt im
            // Jahr vier Megabyte an. Wer sie loeschen will, kann das jederzeit von Hand
            // tun - gelesen wird nur der heutige Tag. Ein Lauf, der alte Zeilen
            // wegwirft, waere die erste Stelle dieses Schemas, die Daten loescht, und
            // dafuer ist der Gewinn zu klein.
            """
            CREATE TABLE IF NOT EXISTS lotterie_lose (
              spieler CHAR(36) NOT NULL,
              tag     DATE     NOT NULL,
              anzahl  INT      NOT NULL,
              PRIMARY KEY (spieler, tag)
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
            """,

            // Schritt 37: die PUNKTE des Angelwettbewerbs - Schritt 14, 22.11.2026.
            //
            // EINE Zeile je Spieler UND Lauf. Der Lauf ist der Tag seines BEGINNS, auch
            // wenn das Fenster bis in den naechsten Tag reicht - nur so haelt die
            // Zuordnung ueber Mitternacht zusammen.
            //
            // DIE ZEILE IST DIE ANMELDUNG. Wer keine hat, sammelt keine Punkte; das ist
            // der Riegel dagegen, dass einem Angler ungefragt Startgeld abgebucht wird,
            // nur weil er im Fenster an einem Teich stand. Geschrieben wird mit INSERT
            // IGNORE, und die Zahl der geschriebenen Zeilen ist die Antwort auf "war es
            // neu" - an dieser Antwort haengt, ob abgebucht wird. Dasselbe Muster wie
            // bei wolke_herausforderung (Schritt 30) und rundgang (Schritt 35).
            //
            // eingezahlt STEHT HIER MIT DRIN, und das ist die tragende Spalte: Der Topf
            // ist die Summe dieser Betraege plus dem Zuschuss. Wer ihn aus "Teilnehmer
            // mal Startgeld" rechnet, rechnet falsch, sobald der Betreiber das Startgeld
            // mitten im Lauf aendert - /freimarken neuladen wirkt sofort.
            //
            // punkte als INT: Bei der ausgelieferten Tabelle bringt der teuerste Fang
            // 100 Punkte, und ein Fenster ist eine Stunde lang. Ein SMALLINT reichte
            // auch, aber die Punkte je Fang sind eine EINSTELLUNG und duerfen bis
            // 1.000.000 gehen.
            //
            // KEIN FREMDSCHLUESSEL auf kassen, wie bei raenge, berufe, anmeldungen,
            // rundgang und lotterie_lose: Ein RESTRICT brachte die Anmeldung zum
            // Scheitern, sobald eine Kontoeroeffnung einmal fehlgeschlagen ist; ein
            // CASCADE loeschte Punkte und Einzahlung stumm mit dem Konto - und dann
            // zahlte der Topf mehr aus, als noch darin steht.
            """
            CREATE TABLE IF NOT EXISTS wettbewerb_punkte (
              spieler    CHAR(36) NOT NULL,
              lauf       DATE     NOT NULL,
              punkte     INT      NOT NULL,
              eingezahlt BIGINT   NOT NULL,
              PRIMARY KEY (spieler, lauf)
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
            """,

            // Schritt 38: die ABGERECHNETEN Laeufe - Schritt 14, 22.11.2026.
            //
            // EINE Zeile je Lauf, und sie ist der wichtigste Riegel dieses ganzen
            // Teils: Ohne ihn zahlte jeder weitere Takt der Spielzeitwache denselben
            // Topf erneut aus - eine Geldquelle, die mit jedem Takt waechst, und zwar
            // am Ende JEDES Wettbewerbs.
            //
            // Geschrieben wird mit INSERT IGNORE; die Zahl der geschriebenen Zeilen ist
            // die Erlaubnis zum Auszahlen. Gemerkt wird ZUERST, dann gezahlt: Bricht es
            // dazwischen ab, bleibt ein Topf unverteilt (ein Verlust fuer die Sieger);
            // umgekehrt waere es eine Quelle.
            //
            // EINE EIGENE Tabelle und nicht eine Spalte an wettbewerb_punkte: Der
            // Abschluss gehoert dem LAUF und nicht einem Spieler. Als Spalte muesste er
            // bei jedem Teilnehmer gesetzt werden, und dann ginge die Frage "ist dieser
            // Lauf abgerechnet" je Zeile anders aus.
            //
            // topf und teilnehmer stehen mit darin, obwohl sie heute niemand liest: Sie
            // kosten acht beziehungsweise vier Byte je Lauf, und sie sind die einzige
            // Spur eines Wettbewerbs, nachdem die Punkte geraeumt wurden. Ein Betreiber,
            // der fragt "was lief letzten Freitag", hat sonst keine Antwort.
            """
            CREATE TABLE IF NOT EXISTS wettbewerb_lauf (
              lauf          DATE        NOT NULL,
              abgerechnet_am DATETIME(3) NOT NULL,
              topf          BIGINT      NOT NULL,
              teilnehmer    INT         NOT NULL,
              PRIMARY KEY (lauf)
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
            """,

            // SCHRITT 39 - die Teamrollen (Plan 12c.2, Schritt 16a Teil A).
            //
            // EINE ZEILE JE SPIELER UND ROLLE, und der Hauptschluessel ist genau dieses
            // Paar. Das ist der Unterschied zu raenge, wo der Spieler allein der
            // Schluessel ist: Ein Rang wird erreicht und ersetzt den vorigen, eine Rolle
            // wird gegeben und steht neben den anderen. Mit dem Spieler allein als
            // Schluessel verdraengte jede neue Rolle die vorige, und zwei Verwalter, die
            // im selben Augenblick zwei Rollen vergeben, haetten einer umsonst
            // gearbeitet.
            //
            // rolle als VARCHAR(32) und nicht als ENUM: Welche Rollen es gibt, steht in
            // der config.yml, und der Betreiber darf dort eine anlegen. Ein ENUM
            // braeuchte fuer jede neue Rolle einen Schemaschritt - also einen Neustart
            // mit Migration fuer eine Zeile in einer Textdatei. Die 32 sind
            // Teamwerte.HOECHSTE_SCHLUESSELLAENGE; sie steht hier als Zahl, weil ein
            // Textblock keine Konstante einsetzen kann, und SchemaTest haelt beide
            // zusammen.
            //
            // vergeber NULL ERLAUBT, und das ist keine Nachlaessigkeit: Die Konsole hat
            // keine UUID, und die erste Rolle eines frischen Servers wird von dort
            // vergeben - es gibt ja noch niemanden, der es koennte. Eine erfundene
            // Null-UUID stuende spaeter in der Auskunft als Spieler, den es nicht gibt,
            // und jemand suchte ihn.
            //
            // KEIN FREMDSCHLUESSEL auf kassen, wie bei raenge und lotterie_lose: Ein
            // Moderator soll seine Rolle behalten, auch wenn seine Kontoeroeffnung
            // einmal fehlgeschlagen ist, und ein CASCADE naehme sie ihm still mit dem
            // Konto. Die Rolle ist eine Vollmacht und kein Besitz.
            //
            // KEIN INDEX auf rolle allein: Gelesen wird nach Spieler (bei jedem
            // Beitritt) oder die ganze Tabelle (fuer die Teamliste). Ein Team hat
            // Dutzende Zeilen, nicht Millionen - ein zweiter Index waere Pflege ohne
            // Gewinn.
            """
            CREATE TABLE IF NOT EXISTS team (
              spieler     CHAR(36)    NOT NULL,
              rolle       VARCHAR(32) NOT NULL,
              vergeben_am DATETIME(3) NOT NULL,
              vergeber    CHAR(36)    NULL,
              PRIMARY KEY (spieler, rolle)
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
            """,

            // SCHRITT 40 - die Meldungen (Plan 12c.3, Schritt 16a Teil C).
            //
            // AUTO_INCREMENT, und das ist hier anders als bei jeder anderen Tabelle
            // dieses Schemas: Die Nummer ist die ANREDE. Ein Moderator tippt
            // /meldung 7 annehmen, und ohne kurze Zahl muesste er eine UUID oder einen
            // Zeitstempel abschreiben. Gelesen wird sie mit getGeneratedKeys.
            //
            // Die Nummer kommt nach einem Neustart WIEDER, wenn die hoechste Zeile
            // geloescht wurde (InnoDB ermittelt AUTO_INCREMENT beim Start aus
            // MAX(nummer)+1). Folgenlos, weil es kein Loeschen gibt - eine Meldung wird
            // erledigt, nicht entfernt. Wer von Hand aufraeumt, soll das wissen.
            //
            // stand als VARCHAR(16) und nicht als ENUM: Meldestand hat heute vier Werte,
            // und sein Javadoc nennt einen fuenften als benannte Luecke
            // (zurueckgeben). Ein ENUM braeuchte dafuer einen Schemaschritt, also einen
            // Neustart mit Migration fuer eine Zeile Java. Der Preis ist, dass eine von
            // Hand verdorbene Zeile beim Lesen wirft - mit ihrer Nummer in der Meldung,
            // damit sie zu finden ist.
            //
            // grund und vermerk als VARCHAR(256): dieselbe Zahl wie
            // Meldung.LAENGSTER_GRUND. Sie steht hier, weil ein Textblock keine
            // Konstante einsetzen kann, und Meldung prueft sie VOR dem Schreiben - ohne
            // das bricht der strict mode mitten in einer Meldung ab, und der Melder
            // glaubt, es sei nichts passiert.
            //
            // bearbeiter und erledigt_am NULL ERLAUBT: Eine offene Meldung hat beides
            // nicht. Meldung prueft das Paar in beide Richtungen, damit nie "offen"
            // dasteht, waehrend jemand daran arbeitet.
            //
            // gelesen ist die Spalte, ohne die die RUECKMELDUNG VERSCHWINDET: Ein
            // Moderator erledigt eine Meldung, waehrend der Melder offline ist, und
            // eine Chatzeile ginge an niemanden. Plan 12c.3 nennt die Rueckmeldung als
            // den Grund, aus dem das Meldewesen ueberhaupt funktioniert.
            //
            // ZWEI INDIZES, und beide tragen eine Abfrage, die je Beitritt laeuft:
            // (melder, stand) fuer die Zahl der offenen, die letzte Meldezeit und die
            // ungelesenen Antworten; (stand, gemeldet_am) fuer die Liste der offenen,
            // die aeltesten zuerst. Ohne sie liest jede dieser Abfragen die ganze
            // Tabelle, und die waechst mit jedem Vorfall.
            //
            // KEIN FREMDSCHLUESSEL auf kassen, wie bei raenge, team und lotterie_lose:
            // Eine Meldung ist ein Protokoll und haengt an keinem Konto. Ein CASCADE
            // loeschte sie mit dem Konto des Melders - und damit die Spur des Vorfalls.
            """
            CREATE TABLE IF NOT EXISTS meldungen (
              nummer      BIGINT       NOT NULL AUTO_INCREMENT,
              melder      CHAR(36)     NOT NULL,
              betroffener CHAR(36)     NOT NULL,
              grund       VARCHAR(256) NOT NULL,
              welt        VARCHAR(64)  NOT NULL,
              x           INT          NOT NULL,
              y           INT          NOT NULL,
              z           INT          NOT NULL,
              gemeldet_am DATETIME(3)  NOT NULL,
              stand       VARCHAR(16)  NOT NULL,
              bearbeiter  CHAR(36)     NULL,
              vermerk     VARCHAR(256) NOT NULL,
              erledigt_am DATETIME(3)  NULL,
              gelesen     TINYINT(1)   NOT NULL,
              PRIMARY KEY (nummer),
              KEY idx_meldung_melder (melder, stand),
              KEY idx_meldung_offen (stand, gemeldet_am)
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
            """,

            // SCHRITT 41 - die Strafen (Plan 12c.3, Schritt 16a Teil B).
            //
            // AUTO_INCREMENT wie bei den Meldungen, und aus demselben Grund: Die Nummer
            // ist die ANREDE. Ein Moderator tippt /strafe aufheben 7, und ohne kurze
            // Zahl muesste er eine UUID abschreiben. Gelesen wird sie mit
            // getGeneratedKeys.
            //
            // EIN ENDE UND KEINE DAUER, und das ist die tragende Entscheidung dieser
            // Tabelle: Nur nach einem Ende laesst sich fragen, wer JETZT gesperrt ist
            // (laeuft_ab_am IS NULL OR laeuft_ab_am > ?). Mit einer Spanne muesste jede
            // Zeile gelesen und gerechnet werden - und diese Abfrage laeuft bei JEDEM
            // Beitritt.
            //
            // laeuft_ab_am NULL ERLAUBT und heisst UNBEGRENZT. Das ist der Zustand, den
            // Plan 12c.2 dem Verwalter vorbehaelt; der Riegel dafuer steht in
            // Strafbuch.verhaenge und prueft BEIDE Faenger - das fehlende Ende und die
            // zu lange Dauer. Ohne den zweiten tippt ein Moderator 3650d.
            //
            // Bei einem RAUSWURF steht das Ende gleich dem Anfang. Ein Rauswurf ist
            // vorbei, sobald er geschieht, und er steht trotzdem in dieser Tabelle: Die
            // Zeile, die zeigt, dass derselbe Spieler vor dem Bann dreimal
            // rausgeworfen wurde, ist der eigentliche Fund.
            //
            // art als VARCHAR(16) und nicht als ENUM: Strafart hat heute drei Werte,
            // und ihr Javadoc nennt die Verwarnung als benannte Luecke. Ein ENUM
            // braeuchte dafuer einen Schemaschritt. Der Preis ist, dass eine von Hand
            // verdorbene Zeile beim Lesen wirft - mit ihrer Nummer in der Meldung.
            //
            // grund und aufhebungsgrund als VARCHAR(256): dieselbe Zahl wie
            // Strafe.LAENGSTER_GRUND. Der Grund ist PFLICHT (Plan 12c.3: "Eine Strafe,
            // die niemand erklaert, erzeugt nur Wiederkehr") und wird dem Betroffenen
            // gezeigt; Strafe prueft ihn VOR dem Schreiben, samt Abschnittszeichen -
            // er steht in der oeffentlichen Bannliste.
            //
            // von_wem und aufgehoben_von NULL ERLAUBT: null heisst die KONSOLE. Ein
            // erfundener Platzhalter waere eine Kennung, die in der Liste wie ein
            // Spieler aussieht.
            //
            // aufgehoben_am ist die Spalte, in deren IS-NULL-Bedingung der Riegel gegen
            // zwei gleichzeitige Aufhebungen steht (MariaStrafen.AUFHEBEN). Aufgehoben
            // wird NICHT geloescht: Gerade die zurueckgenommene Strafe ist die, nach der
            // ein zweiter Moderator fragt.
            //
            // ZWEI INDIZES, und beide tragen eine Abfrage, die oft laeuft:
            // (betroffener, art, aufgehoben_am, laeuft_ab_am) fuer die Frage der Wache
            // bei jedem Beitritt und bei jeder Chatzeile; (art, aufgehoben_am,
            // laeuft_ab_am, verhaengt_am) fuer die oeffentliche Bannliste. Ohne sie
            // liest jede dieser Abfragen die ganze Tabelle, und die waechst mit jedem
            // Vorfall.
            //
            // KEIN FREMDSCHLUESSEL auf kassen, wie bei raenge, team und meldungen: Eine
            // Strafe ist ein Protokoll und haengt an keinem Konto. Ein CASCADE loeschte
            // sie mit dem Konto des Betroffenen - und ein gebannter Spieler, dessen
            // Konto aufgeraeumt wird, waere damit entbannt.
            """
            CREATE TABLE IF NOT EXISTS strafen (
              nummer          BIGINT       NOT NULL AUTO_INCREMENT,
              betroffener     CHAR(36)     NOT NULL,
              art             VARCHAR(16)  NOT NULL,
              grund           VARCHAR(256) NOT NULL,
              von_wem         CHAR(36)     NULL,
              verhaengt_am    DATETIME(3)  NOT NULL,
              laeuft_ab_am    DATETIME(3)  NULL,
              aufgehoben_am   DATETIME(3)  NULL,
              aufgehoben_von  CHAR(36)     NULL,
              aufhebungsgrund VARCHAR(256) NOT NULL,
              PRIMARY KEY (nummer),
              KEY idx_strafe_geltend
                  (betroffener, art, aufgehoben_am, laeuft_ab_am),
              KEY idx_strafe_liste
                  (art, aufgehoben_am, laeuft_ab_am, verhaengt_am)
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
            """,

            // DER RUECKWEG [26.11.2026]: zwei Spalten an blockbuch, und beide
            // sind unverzichtbar.
            //
            // vorzustand traegt, was an der Stelle stand, BEVOR die Tat geschah -
            // als Vanilla-Text, etwa minecraft:oak_stairs[facing=east,half=bottom].
            // VOR der Tat und nicht danach, und das ist die Entscheidung, an der
            // alles haengt: Bei einem Abbau ist es der Block selbst, bei einer
            // SETZUNG das, was ueberbaut wurde (Luft, Wasser, Gras). Mit dem
            // Zustand NACH der Tat waere eine Setzung ueberhaupt nicht
            // zurueckzunehmen - der Rueckweg setzte den gesetzten Block noch
            // einmal. OHNE SIE kommt ein
            // zurueckgerollter Bau falsch zusammen: Treppen zeigen in die falsche
            // Richtung, Tueren haengen an der anderen Seite, Truhen stehen
            // einzeln statt als Doppeltruhe. Das sieht nach einem Rueckweg aus
            // und ist keiner. 255 Zeichen, weil ein Zustandstext mit allen
            // Eigenschaften (etwa ein Zaun mit vier Richtungen plus waterlogged)
            // darunter bleibt; der laengste in Vanilla liegt bei rund 180.
            //
            // zurueckgenommen ist der Riegel gegen den zweiten Lauf. Ohne ihn
            // rollt derselbe Grief beim zweiten Aufruf ein zweites Mal zurueck -
            // und macht damit die Reparatur weg, die jemand danach gebaut hat.
            // GEMERKT und nicht geloescht: Das Blockbuch ist ein Protokoll, und
            // ein geloeschter Grief laesst sich beim Wiederholungstaeter nicht
            // mehr nachweisen.
            //
            // ANGEHAENGT und nicht in den CREATE oben geschrieben: Auf einem
            // Server, der schon lief, steht die Tabelle - ein geaendertes CREATE
            // TABLE IF NOT EXISTS liefe dort wirkungslos durch, und die Spalten
            // fehlten stumm. Zwei getrennte Schritte, weil ein Schemaschritt
            // entweder ganz oder gar nicht laeuft.
            """
            ALTER TABLE blockbuch
              ADD COLUMN IF NOT EXISTS vorzustand VARCHAR(255) NOT NULL DEFAULT ''
            """,
            """
            ALTER TABLE blockbuch
              ADD COLUMN IF NOT EXISTS zurueckgenommen TINYINT(1) NOT NULL DEFAULT 0
            """);

    /**
     * Der Kalender fuer JEDE Zeitspalte dieses Schemas - UTC, ohne Ausnahme.
     *
     * <p><b>Die Regel in einer Methode</b>, und sie steht hier und nicht in den
     * Tabellenklassen: Vorher hatte {@code MariaKasse} ihren eigenen Kalender, und
     * {@code MariaBlockbuch} und {@code MariaAbholfach} hatten gar keinen. Ohne Kalender
     * rechnet der Treiber in die Zone der JVM - jede Tabelle ist dann fuer sich stimmig
     * (hin und zurueck dieselbe Zeit), aber wer zwei nebeneinander liest, liegt um den
     * Zonenversatz daneben. Genau das ist am 10.11.2026 im Blockbuch aufgefallen.
     *
     * <p><b>Ein neuer Kalender je Aufruf</b>, denn {@link Calendar} ist nicht
     * nebenlaeufigkeitsfest: Ein geteiltes Exemplar waere ein veraenderlicher Zustand
     * zwischen zwei Datenbankfaeden.
     *
     * <p>Die EINE Ausnahme ist keine Zeitspalte: {@code spielzeit.tag} ist ein DATUM in
     * Ortszeit, gerechnet aus {@code zeit.zone}. Das ist Absicht und steht dort.
     */
    static Calendar utcKalender() {
        return Calendar.getInstance(TimeZone.getTimeZone(ZoneOffset.UTC));
    }

    /**
     * Der Treiber, direkt gehalten statt ueber DriverManager gesucht.
     *
     * <p>Notwendig fuer den Betrieb als Plugin: DriverManager sammelt seine Treiber einmalig
     * ueber den ServiceLoader, mit dem Klassenlader, der gerade dran ist. In Paper hat jedes
     * Plugin seinen eigenen Lader, und hat ein anderes Plugin den DriverManager vor uns
     * angefasst, ist die Sammlung schon abgeschlossen - unser mitgelieferter Treiber steht
     * dann nicht darin. Das Ergebnis waere "No suitable driver", und zwar nur auf dem echten
     * Server, nie im Test.
     */
    private static final Driver TREIBER = new org.mariadb.jdbc.Driver();

    /**
     * Wie lange eine einzelne Anweisung hoechstens auf Antwort wartet.
     *
     * <p>Muss gesetzt sein: Der Treiber nimmt ohne Angabe 0, also unbegrenzt. Schlaeft der
     * Datenbankrechner ein oder verwirft eine Firewall den bestehenden Fluss, wartet eine
     * Abfrage dann, bis der TCP-Stack von selbst aufgibt - Minuten. Das laeuft im
     * Hauptfaden des Spielservers, weil der Besitzschutz bei jedem Blockereignis dort
     * fragt. Fuenf Sekunden sind fuer ein lokales Netz reichlich und fuer einen stehenden
     * Server kurz genug, dass daraus ein Fehler wird statt eines Stillstands.
     */
    private static final String ANTWORTFRIST_MS = "5000";

    /** Wie lange der Aufbau einer neuen Verbindung hoechstens dauern darf. */
    private static final String AUFBAUFRIST_MS = "3000";

    /**
     * Wie lange eine einmal gepruefte Verbindung als brauchbar gilt.
     *
     * <p>{@code isValid} ist kein lokaler Test, sondern ein Ping ueber das Netz. Bei jedem
     * Statement geprueft verdoppelt das die Netzrunden: Fuer einen abgebauten Block auf
     * einer vergebenen Parzelle waren es vier statt zwei. Eine Sekunde Vertrauen kostet
     * nichts, denn eine Anweisung an einer inzwischen toten Verbindung scheitert mit einer
     * SQLException - und darauf wird ohnehin neu aufgebaut.
     */
    private static final long PRUEFABSTAND_MS = 1_000L;

    private final String url;
    private final String benutzer;
    private final String passwort;
    private Connection verbindung;
    private long zuletztGeprueft;

    public Datenbank(String url, String benutzer, String passwort) {
        this.url = url;
        this.benutzer = benutzer;
        this.passwort = passwort;
    }

    /**
     * Eine brauchbare Verbindung - baut sie bei Bedarf neu auf.
     *
     * <p>Notwendig, weil MariaDB untaetige Verbindungen nach einigen Stunden schliesst.
     * Ein Server, der nachts schlaeft und morgens weiterarbeitet, trifft genau das.
     */
    public synchronized Connection verbindung() throws SQLException {
        if (verbindung != null && !brauchbar()) {
            schliesseLeise();
        }
        if (verbindung == null) {
            Properties angaben = new Properties();
            angaben.setProperty("user", benutzer);
            angaben.setProperty("password", passwort);
            // Vorsorge fuer kuenftige TIMESTAMP-Spalten. Die Zeitrichtigkeit der
            // DATETIME-Spalten haengt NICHT daran, sondern daran, dass ueberall
            // LocalDateTime in UTC geschrieben und gelesen wird - Timestamp rechnet
            // sonst in die Zeitzone der JVM um, hin wie zurueck.
            angaben.setProperty("connectionTimeZone", "UTC");
            angaben.setProperty("forceConnectionTimeZoneToSession", "true");
            angaben.setProperty("socketTimeout", ANTWORTFRIST_MS);
            angaben.setProperty("connectTimeout", AUFBAUFRIST_MS);
            verbindung = TREIBER.connect(url, angaben);
            if (verbindung == null) {
                // connect() gibt null zurueck, wenn der Treiber die Adresse nicht annimmt.
                throw new SQLException("Keine MariaDB-Adresse: " + url);
            }
            zuletztGeprueft = System.nanoTime();
        }
        return verbindung;
    }

    /**
     * Ist die gehaltene Verbindung noch zu gebrauchen?
     *
     * <p>Gedrosselt: Wurde sie vor weniger als {@link #PRUEFABSTAND_MS} schon geprueft,
     * gilt sie als gut, ohne dass dafuer ein Ping ueber das Netz geht. nanoTime und nicht
     * currentTimeMillis, weil nur nanoTime monoton laeuft - eine zurueckgestellte Uhr
     * wuerde den Abstand sonst negativ machen.
     */
    private boolean brauchbar() throws SQLException {
        if (verbindung.isClosed()) {
            return false;
        }
        long seit = (System.nanoTime() - zuletztGeprueft) / 1_000_000L;
        if (seit < PRUEFABSTAND_MS) {
            return true;
        }
        boolean gut = verbindung.isValid(2);
        if (gut) {
            zuletztGeprueft = System.nanoTime();
        }
        return gut;
    }

    /**
     * Der hoechste Schemaschritt, den dieses Jar kennt.
     *
     * <p>Damit niemand die Zahl ein zweites Mal aufschreibt: Eine Probe, die den
     * erreichten Stand gegen eine getippte Zahl haelt, wird mit jedem neuen Schritt rot,
     * ohne dass etwas kaputt ist - genau das ist beim Schritt 9 passiert.
     */
    public static int hoechsteFassung() {
        return SCHRITTE.size();
    }

    /** Legt fehlende Tabellen an. Mehrfach aufrufbar. */
    public synchronized void migriere() throws SQLException {
        Connection c = verbindung();
        try (Statement anweisung = c.createStatement()) {
            anweisung.execute("""
                    CREATE TABLE IF NOT EXISTS schema_stand (
                      schritt   INT         NOT NULL,
                      erledigt  DATETIME(3) NOT NULL,
                      PRIMARY KEY (schritt)
                    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                    """);
        }

        int stand = 0;
        try (Statement anweisung = c.createStatement();
             var ergebnis = anweisung.executeQuery(
                     "SELECT COALESCE(MAX(schritt), 0) FROM schema_stand")) {
            if (ergebnis.next()) {
                stand = ergebnis.getInt(1);
            }
        }

        // Ein zurueckgelegtes aelteres Jar faende ein Schema vor, das es nicht kennt.
        // Heute waere das harmlos - es gibt kein SELECT *, alle Spalten stehen namentlich
        // da, und das alte INSERT laeuft in die Vorgabewerte. Ab dem naechsten
        // Schemaschritt ist das nicht mehr sicher, und dann ist ein verweigerter Start
        // die freundlichere Antwort als ein halb laufender.
        if (stand > SCHRITTE.size()) {
            throw new SQLException("Die Datenbank steht auf Schemastand " + stand
                    + ", dieses Jar kennt nur " + SCHRITTE.size()
                    + ". Es ist aelter als das Schema - bitte das neuere Jar einspielen.");
        }

        for (int nummer = stand + 1; nummer <= SCHRITTE.size(); nummer++) {
            try (Statement anweisung = c.createStatement()) {
                anweisung.execute(SCHRITTE.get(nummer - 1));
                anweisung.execute("INSERT INTO schema_stand (schritt, erledigt) VALUES ("
                        + nummer + ", UTC_TIMESTAMP(3))");
            }
        }
    }

    /**
     * Alle Tabellen leeren - nur fuer Tests.
     *
     * <p>Bewusst {@code DELETE} und kein {@code DROP}: So bleibt das Schema stehen und ein
     * Testlauf prueft weiterhin gegen die echten Schluessel und Fremdschluessel.
     *
     * <p>Verweigert jedes Schema, dessen Name nicht auf {@code _test} endet. Die Sperre
     * sitzt hier und nicht in der Gradle-Datei oder in den Testklassen, weil sie dort von
     * jedem kuenftigen Testlauf und jeder kuenftigen Testklasse neu eingehalten werden
     * muesste - und eine davon wuerde es vergessen. Hier fragt die Methode die Datenbank
     * selbst, wo sie steckt, und kann nicht umgangen werden.
     *
     * @throws IllegalStateException wenn die Verbindung nicht in einem Testschema haengt
     */
    public synchronized void leereAlles() throws SQLException {
        String schema = schemaName();
        if (!schema.endsWith("_test")) {
            throw new IllegalStateException(
                    "leereAlles() verweigert das Schema \"" + schema + "\": Es loescht ALLE "
                            + "Parzellen, Mitbauer und Guthaben und ist nur fuer Tests "
                            + "gedacht. Erlaubt sind nur Schemata, deren Name auf _test "
                            + "endet. In data/freimarken/datenbank.properties gehoert dafuer "
                            + "url.test, angelegt von skripte/mariadb_einrichten.bat.");
        }
        try (Statement anweisung = verbindung().createStatement()) {
            // DIE GRUNDSTUECKE ZUERST, und zwar VOR den Parzellen [12.11.2026]:
            // fk_grundstueck_stadt zeigt auf parzellen.id und steht auf RESTRICT. Ohne
            // diese Zeile scheitert jedes leereAlles, sobald ein einziges Grundstueck
            // steht - und damit JEDE Datenbankprobe, auch die, die mit Grundstuecken
            // nichts zu tun hat. Die Mitbauer gehen ueber ON DELETE CASCADE mit; sie
            // stehen trotzdem hier, damit die Reihenfolge lesbar bleibt.
            // VOR parzellen, wie jede Nebentabelle mit Fremdschluessel darauf: Ein
            // DELETE auf die Elternzeile scheitert sonst an fk_freistadt_recht.
            // Die zwei Tabellen von Wolkenwerder - auch sie VOR parzellen, aus
            // demselben Grund: Ihr Fremdschluessel steht auf CASCADE, aber das DELETE
            // auf die Elternzeile scheitert ohne sie am Riegel von InnoDB.
            anweisung.execute("DELETE FROM wolke_herausforderung");
            anweisung.execute("DELETE FROM wolkeninsel");
            anweisung.execute("DELETE FROM freistadt_rechte");
            anweisung.execute("DELETE FROM grundstuecke_mitbauer");
            anweisung.execute("DELETE FROM grundstuecke");
            anweisung.execute("DELETE FROM parzellen_mitbauer");
            // Erst die Elternkette loesen. fk_besitz_elternteil steht auf RESTRICT und
            // zeigt auf dieselbe Tabelle; InnoDB prueft zeilenweise waehrend der
            // Anweisung, und trifft das DELETE die Gruppe vor ihren Kindern, verweigert
            // es die Zeile. Dieses UPDATE ist von der Tiefe der Kette unabhaengig - ein
            // "erst die Kinder, dann die Eltern" waere es nicht.
            anweisung.execute("UPDATE parzellen SET eltern_id = NULL");
            anweisung.execute("DELETE FROM parzellen");
            // Die Buchungen VOR den Kassen: fk_buchung_kasse steht auf RESTRICT, und eine
            // Kassenzeile mit Buchungen daran laesst sich nicht loeschen.
            anweisung.execute("DELETE FROM buchungen");
            // Die Spielzeit haengt an keinem Fremdschluessel, ihre Stelle in dieser Liste
            // ist also frei - sie steht hier, damit sie nicht uebersehen wird. Wer der
            // Tabelle je einen Fremdschluessel gibt, muss die Zeile einordnen; ohne sie
            // wandern gemessene Sekunden von einem Test in den naechsten.
            anweisung.execute("DELETE FROM spielzeit");
            // Der Tagesbonus haengt an keinem Fremdschluessel, seine Stelle in dieser
            // Liste ist also frei - er steht hier, damit er nicht uebersehen wird. Ohne
            // ihn wandern Serien von einem Test in den naechsten, und eine Probe ueber
            // den ersten Bonus eines Spielers faende dort schon eine Serie.
            anweisung.execute("DELETE FROM tagesbonus");
            // Die Mietvertraege haengen an keinem Fremdschluessel, ihre Stelle in dieser
            // Liste ist also frei - sie stehen hier, damit sie nicht uebersehen werden.
            // Ohne diese Zeile wandern Vertraege von einem Test in den naechsten, und
            // weil "ein Laden je Spieler" ein eindeutiger Schluessel ist, waere die
            // Folge nicht nur ein falscher Stand, sondern ein harter Wurf im naechsten
            // Test.
            anweisung.execute("DELETE FROM ladenmiete");
            // Das Abholfach, und hier steht eine Ausnahme von seiner eigenen Regel: Im
            // Betrieb wird nie geleert, in der Probenreihe MUSS geleert werden. Ohne
            // diese Zeile zaehlt die naechste Probe die Stuecke der vorigen mit, und die
            // Nummern steigen ueber Probenlaeufe hinweg - dann ist "das aelteste zuerst"
            // nicht mehr nachzurechnen. Dass leereAlles() jedes Schema ohne _test am Ende
            // verweigert, ist genau fuer solche Zeilen da.
            anweisung.execute("DELETE FROM abholfach");
            // Die Kistenshops. Ohne diese Zeile wandern Shops von einem Test in den
            // naechsten, und weil die Schildstelle der Hauptschluessel ist, waere die
            // Folge nicht nur ein falscher Stand, sondern ein harter Wurf beim naechsten
            // Anlegen an derselben Stelle.
            // Die Freigaben VOR den Shops: fk_freigabe_shop steht auf CASCADE, nimmt sie
            // also ohnehin mit - die Zeile steht trotzdem hier, und zwar aus demselben
            // Grund, aus dem parzellen_mitbauer als erste geloescht wird: Wer den
            // Fremdschluessel je auf RESTRICT aendert, soll nicht erst an einer roten
            // Probenreihe merken, dass hier eine Zeile fehlt.
            anweisung.execute("DELETE FROM kistenshop_freigabe");
            anweisung.execute("DELETE FROM kistenshop");
            // Die Berufe haengen an keinem Fremdschluessel, ihre Stelle in dieser Liste
            // ist also frei - sie stehen hier, damit sie nicht uebersehen werden. Ohne
            // diese Zeile wandern gewaehlte Berufe von einem Test in den naechsten, und
            // der geteilte Vertrag wird sporadisch rot.
            anweisung.execute("DELETE FROM berufe");
            // Die Raenge haengen an keinem Fremdschluessel, ihre Stelle in dieser Liste
            // ist also frei - sie stehen hier, damit sie nicht uebersehen werden. Ohne
            // diese Zeile wandern erreichte Raenge von einem Test in den naechsten, und
            // weil "ein Rang je Spieler" der Hauptschluessel ist, waere die Folge nicht
            // nur ein falscher Stand: Der geteilte Vertrag faende bei "ein unbekannter
            // Spieler hat keinen Rang" einen vorgefundenen vor.
            anweisung.execute("DELETE FROM raenge");
            // Die Annahmen haengen an keinem Fremdschluessel - mit Absicht, siehe
            // den Schemaschritt -, ihre Stelle in dieser Liste ist also frei. Sie
            // stehen hier, damit sie nicht uebersehen werden. Ohne diese Zeile
            // wanderte eine Zustimmung von einer Probe in die naechste, und eine
            // Probe ueber den ERSTEN Beitritt eines Spielers faende ihn schon
            // angemeldet - das Formular kaeme nie, und die Probe waere gruen.
            anweisung.execute("DELETE FROM anmeldungen");
            // Der Rundgang haengt an keinem Fremdschluessel - mit Absicht, siehe den
            // Schemaschritt -, seine Stelle in dieser Liste ist also frei. Er steht
            // hier, damit er nicht uebersehen wird. Ohne diese Zeile wanderten gesehene
            // Stationen von einer Probe in die naechste, und eine Probe ueber den ERSTEN
            // Schritt in ein Gebaeude faende ihn schon gesehen - sie waere gruen, ohne
            // etwas zu pruefen.
            anweisung.execute("DELETE FROM rundgang");
            // Das Blockbuch haengt an keinem Fremdschluessel - mit Absicht, siehe den
            // Schemaschritt -, seine Stelle in dieser Liste ist also frei. Es steht hier,
            // damit es nicht uebersehen wird. Ohne diese Zeile wandern Protokollzeilen
            // von einem Test in den naechsten, und das trifft gerade die Proben dieser
            // Tabelle hart: Sie zaehlen, was eine Abfrage mit Grenze zurueckgibt, und
            // fremde Zeilen am selben Ort schieben die eigenen aus der Antwort heraus -
            // ein Fehlschlag, der von der Reihenfolge der Proben abhaengt.
            anweisung.execute("DELETE FROM blockbuch");
            // Die Entnahmen der Freien Truhe haengen an keinem Fremdschluessel, ihre
            // Stelle in dieser Liste ist also frei - sie stehen hier, damit sie nicht
            // uebersehen werden. Ohne diese Zeile wandern Tagesstaende von einer Probe
            // in die naechste, und eine Probe ueber den ersten Griff eines Spielers
            // faende dort schon einen Deckel.
            anweisung.execute("DELETE FROM freie_truhe");
            // Die Lose der Lotterie haengen an keinem Fremdschluessel - mit Absicht,
            // siehe den Schemaschritt -, ihre Stelle in dieser Liste ist also frei. Sie
            // stehen hier, damit sie nicht uebersehen werden. Ohne diese Zeile wandern
            // Loszahlen von einer Probe in die naechste, und das trifft gerade die
            // Proben der Lotterie: Am Preis haengt die Zahl, eine Probe ueber "das erste
            // Los ist gratis" faende ein vorgefundenes vor und bekaeme den Grundpreis.
            // Gruen waere sie damit nicht - aber eine, die den Grundpreis erwartet, waere
            // es, und sie pruefte das Gegenteil von dem, was sie behauptet.
            anweisung.execute("DELETE FROM lotterie_lose");
            // Der Angelwettbewerb haengt an keinem Fremdschluessel - mit Absicht, siehe
            // die Schemaschritte -, seine Stelle in dieser Liste ist also frei. Beide
            // Tabellen stehen hier, damit sie nicht uebersehen werden. Die LAUF-Tabelle
            // ist dabei die gefaehrlichere: Bliebe eine Zeile stehen, gilt derselbe Lauf
            // in der naechsten Probe als abgerechnet - und eine Probe ueber die
            // Auszahlung waere gruen, ohne einen einzigen Preis zu sehen.
            anweisung.execute("DELETE FROM wettbewerb_punkte");
            anweisung.execute("DELETE FROM wettbewerb_lauf");
            // Die Teamrollen haengen an keinem Fremdschluessel - mit Absicht, siehe den
            // Schemaschritt -, ihre Stelle in dieser Liste ist also frei. Sie stehen
            // hier, damit sie nicht uebersehen werden. Ohne diese Zeile wandert eine
            // Rolle von einer Probe in die naechste, und das trifft gerade die Proben
            // des Teamvertrags: "Wer keine Rolle hat, ist nicht im Team" faende eine
            // vorgefundene vor. Die Proben nehmen zufaellige UUIDs, also wuerde es nur
            // alle() treffen - und genau dort ist es am schlimmsten, weil die Zeile
            // eines FREMDEN Spielers die eigene Zaehlung verschiebt.
            anweisung.execute("DELETE FROM team");
            // Die Meldungen haengen an keinem Fremdschluessel - mit Absicht, siehe den
            // Schemaschritt -, ihre Stelle in dieser Liste ist also frei. Sie stehen
            // hier, damit sie nicht uebersehen werden. Ohne diese Zeile wandert eine
            // Meldung von einer Probe in die naechste, und das trifft gerade die Proben
            // des Meldevertrags: "die offenen Meldungen kommen mit der aeltesten zuerst"
            // faende eine fremde, aeltere vor. Die Proben nehmen zufaellige UUIDs, also
            // bliebe offeneVon richtig - und genau offene() waere falsch.
            //
            // KEIN TRUNCATE, obwohl das den AUTO_INCREMENT-Zaehler zuruecksetzen wuerde:
            // leereAlles() benutzt ueberall DELETE, und ein einzelnes TRUNCATE waere
            // eine Ausnahme, die man beim naechsten Mal vergisst. Die Folge ist, dass
            // die Nummern ueber eine Probenreihe hinweg weiterzaehlen - darum prueft
            // keine Probe eine bestimmte Nummer, sondern nur, DASS sie vergeben wird
            // und dass sie steigt.
            anweisung.execute("DELETE FROM meldungen");
            anweisung.execute("DELETE FROM strafen");
            // DIE GOLDMARK, und zwar das Buch VOR den Konten: fk_goldbuchung_kasse steht
            // auf RESTRICT, genau wie bei der Mark. Beide Zeilen sind Pflicht - ohne sie
            // wandern Goldmarkstaende von einer Probe in die naechste, und weil "ein
            // Konto je Spieler" der Hauptschluessel ist, faende der geteilte KasseVertrag
            // bei "ein unbekannter Spieler hat das Startguthaben" einen vorgefundenen
            // Stand vor.
            anweisung.execute("DELETE FROM goldbuchungen");
            anweisung.execute("DELETE FROM goldkassen");
            anweisung.execute("DELETE FROM kassen");
        }
    }

    /**
     * Die gehaltene Verbindung wegwerfen; der naechste Zugriff baut eine neue auf.
     *
     * <p>Fuer den Fall, dass ein Aufrufer die Verbindung in einem Zustand zurueckliesse,
     * den er nicht aufraeumen konnte - etwa mit abgeschaltetem autoCommit, weil schon das
     * Zurueckstellen gescheitert ist. Eine solche Verbindung weiterzugeben waere der
     * schlimmste Fall: Schreibvorgaenge landeten in einer Transaktion, die niemand mehr
     * committet, und verschwinden lautlos.
     */
    public synchronized void verwerfen() {
        schliesseLeise();
    }

    /** In welchem Schema die Verbindung gerade haengt. Leer, wenn keines gewaehlt ist. */
    public synchronized String schemaName() throws SQLException {
        try (Statement anweisung = verbindung().createStatement();
             var ergebnis = anweisung.executeQuery("SELECT DATABASE()")) {
            if (!ergebnis.next()) {
                return "";
            }
            String name = ergebnis.getString(1);
            return name != null ? name : "";
        }
    }

    private void schliesseLeise() {
        if (verbindung != null) {
            try {
                verbindung.close();
            } catch (SQLException ignoriert) {
                // Eine Verbindung, die sich nicht schliessen laesst, ist ohnehin hin.
            }
            verbindung = null;
        }
    }

    @Override
    public synchronized void close() {
        schliesseLeise();
    }
}
