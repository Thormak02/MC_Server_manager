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
