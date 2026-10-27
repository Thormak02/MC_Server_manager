~~super, bitte erstelle nun phase 2 des zweiten pflichtenhefts.~~

~~die API keys werde ich manuell in die .env eintragen, erstelle dort platzhalter, auf die der code dann zugreift. dies betrifft nur private APIs, öffentliche können weiterhin normal im code stehen.~~







~~das starten nach der Erstellung funktioniert nicht, aber das starten eines manuell erstellten Servers, der dann improtiert wurde funktioniert. überprüfe also bitte nochmal den servererstellungsprozess.~~





~~die jeweilige forge / fabric usw. Version soll unter Server details geändert werden können zb. um updates durchzuführen. außerdem soll es moglich sein zb. nach Releases / betas usw. zu filtern. selbes für mods und modversionen.~~





~~wenn kein verzeichnis bei der erstellung eines servers angegeben wird, soll ein standardverzeichnis genutzt werden. dieses soll beim erstmaligen starten der anwendung auf dem desktop erstellt werden. der pfad soll in der .env und den einstellungen im ui angepasst werden können. in dem server verzeichnis soll für jeden erstellten server jeweils ein unterverzeichnis erstellt werden. außerdem, wenn ich einen Server inklusive ordner lösche und dann wieder einen Server mit dem selben namen erstelle, werden die mods noch als installiert angezeigt, obwohl sie nicht mehr existieren, also die db einträge sind vermutlich nicht richtig gelöscht.~~







~~super, bitte erstelle nun Phase 3 (Uploads/Downloads, erweiterte Logs/Audit).~~





~~setze ich als Nächstes direkt Phase 6 (Modpack-Import + Komfortfunktionen) um~~





~~der modpackimport ist so falsch, es ist nicht wie modinstallation gedacht, sondern als neuer Server. man soll also einen Server aus einem modpack erstellen können.~~





~~die modpack suche funktioniert nur selektiv, manche werden korrekt gefunden, andere wie das atm10 Beispiel werden nicht gefunden, oder erst weiter unten. ähnlich ist es bei den mods.~~







~~die suche (mods sowie modpacks) soll jetzt noch smarter werden. sie soll aussehen / funktionieren wie die suche in der curseforge app. also mod Vorschau, wie bei curseforge, bild, kurze Beschreibung, download zahlen, usw. Hyperlink zur jeweiligen modseite bei curseforge / modrinth.~~

~~die suche soll funktionieren, wie in der curseforge app (siehe bilder) also mit Filter und sortierung zb. nach relevanz, downloads, beliebtheit usw. wenn man beispielsweise keinen suchbergiff eingibt, sollen die belibtesten bzw. mit den meisten downloads je nach sortierung und Kategorie angezeigt werden. man soll auch mehrere Kategorien auswählen können. Die suche soll sofort bei eingebe starten, nicht erst mit klick auf suche. das soll für modpacks sowie mods funktionieren.~~





~~bitte die Kategorien als Dropdown mit Checkbox Auswahl, Kategorien sollen auch wieder abwählbar sein, bei modrinth ist es falsch (siehe screenshot). die projektseite soll man mit klick auf den namen aufrufen, also per Hyperlink und nicht per Feld daneben. die seite soll sich immer im neuen tab öffnen. bei der suche soll es die Option beim runterscrollen mehr Inhalte zu laden, dafür muss die Inhalte liste mit den suchergebnissen separat / unabhängig von dem rest scrollen lassen. Import Preview reicht anzuzeigen, dass es keine Warnungen gibt, oder nur die Fehler sollte es welche geben, so ist es zu viel kryptischer bloat, wenn alle mods usw. aufgelistet werden (siehe bild).~~

~~MC Version (optional) nd Loader (optional) lieber auch als Dropdown Auswahl, wie bei Kategorien auch mit mehrfach Auswahl und Checkbox und abwählbar.~~





~~es sollte bei curseforge modpack import nur eines benötigt werden, also modpack code, zip oder url. eins sollte reichen, es tut nicht not, dass alle drei mandatory fields sind.~~



~~curseforge modpack Import über modpack code / Export zip (weiter ausarbeiten)?~~





~~curseforge soll als Standard ausgewählt sein, nicht modrinth~~





~~modpack Version soll änderbar sein (darauf achten, dass alte Inhalte korrekt gelöscht werden und korrekt durch neue ersetzt werden) um auf updates zu prüfen und diese durchzuführen~~





**<i>~~bitte überprüfe nochmal beide pflichtenhefte, ob alle anforderungen umgesetzt wurden.~~</i>**

**<i>~~(hier copy paste beide pflichtenhefte)~~</i>**





~~in Details sollen op, banns, whitelist oben als eigener reiter sein, nicht nur (aber auch) unter Dateien. außerdem mit Assistent, wo man nur den namen eingeben muss. soll auch funktionieren, wenn die jeweilige Datei leer ist (das jeweilige Schema muss bei einem eintrag korrekt angelegt werden). unter whitelist reiter soll diese auch aktivierbar und deaktivierbar sein, am besten mit einem switch / toggle button. in den config Dateien sollen wirklich alle Felder per Assistent eingegeben werden können (sofern in assistent ansicht), nicht nur einige wenige (wie aktuell) es sollen alle einstellungen automatisch erkannt werden.~~





~~es soll möglich sein, die minecraft Version eines bestehenden Servers zu ändern, um zb. updates durchzuführen.~~

~~dies soll nur für vanilla / plugin server funktionieren. nicht für modded / modpacks~~





~~da man den modloader nicht ändern kann, reicht es, wenn die modsuche ebenfalls auf den jeweiligen modloader beschränkt ist und nur zu dem Server passende mods angezeigt werden und installiert werden können.~~







~~wenn eine neue minecraft Version released wird soll diese automatisch im Manager verfügbar sein, ohne sie manuell hinzufügen zu müssen. ist dies eventuell bereits umgesetzt? selbes auch für modloader wie zb. forge.~~







~~bitte prüfe nochmal alle seiten im gui, es gibt noch einige visuelle Bugs / es sind noch einige Felder verrutscht.~~



~~Das UI bitte auch generell aufräumen, proportionen, positionen usw.~~

~~die suche soll als hauptfunktion präsenter sein und nich wie ein weiterer filter nur nebensächlich wirken.~~







~~für Plugins wird noch bukkti Unterstützung benötigt.~~



~~mod dependicies sollen automatisch mit installiert werden, wenn eine mod installiert wird~~





~~die ram einstellung soll nicht mehr über ein freitext eingabe feld funktionieren, sondern als min max slider mit sinnvollen inkrementen mit maximal 64 GB~~



~~es ist gestartet, aber bei klick auf update check folgender Fehler:~~

~~Fetch fehlgeschlagen: fatal: 'origin' does not appear to be a git repository fatal: Could not read from remote repository. Please make sure you have the correct access rights and the repository exists.~~





~~updates push auf dev pc zu auto (oder manuell) download / update auf server pc~~





~~wenn bei port nichts eingetragen wurde automatisch einen freien festlegen, nicht einfach nur Standard port, nicht 5000 da läuft der Manager, sondern ein port, der noch nicht belegt ist.~~


super, dann jetzt bitte die portale. nicht einfach so portale platzieren, sondern das system liefern um diese als operator / builder manuell zu setzen


templates nicht nur für server erstellung, auch für datein wie server.properties, standart template festlegen (evtl. nach servertyp)



~~neoforge Integration~~



localisation (englisch), später



~~Server löschbestätigung als popup~~



~~Lobby Server unter selben port wie manager, von hier aus soll man auch server starten können.~~
welt ist eine struktur, die sich automatisch pro server erweitert. ist das möglich? siehe docs/lobby_gateway_plan.md


SSH zugriff?


mehrere verwaltungsumgebungen, die voneinander getrennt sind, server 1, 2 und 3 in umgebung A und server 4, 5 und 6 in umgebung B


~~Server umbenennen können~~


~~ein button um alle mods auf aktuellste Version zu updaten (im mods bereich, auch für plugins usw.), außerdem beim download automatisch aktuellste version ausgewählt. abhängigkeiten werden automatisch mit installiert. nicht für modpacks, wo oft eine spezifische version gefordert ist.~~





~~alle Server so einstellbar sein, sodass sie mit start des Managers starten~~



modpack Version im Dashboard anzeigen, wie mc Version



~~spielerzahl auch bei Servereinstellungen anpassen können~~





~~auf Sicherheitslücken prüfen~~



~~backup auf nas als netzwerklaufwerk~~





~~Plugins funktionieren nicht ganz, nochmal schauen, was genau~~



~~schedule Kalender funktioniert nicht, nochmal prüfen, was genau nicht geht~~


~~Spielerkopf als icon bei namen in onlineliste, op, white und bannliste usw. anzeigen~~


~~Neustart geht nicht~~

~~Thormak2002 UUID: 69f230e1-af2c-4d0e-9885-93d66595a855 immer standardmäßig als level 4 operator festlegen, unter einstellungen managerweite whitelist, op list und bann list~~


~~es soll einstellbar sein, dass wen kein spieler auf dem server ist, das der server dann in einen standby geht, es vergeht keine zeit, keine ticks, keine geladenen chunks, der server soll also praktisch komplett heruntergefahren sein, bis sich wieder ein spieler verbinden möchte. also spieler login: Server startet - spieler disconnect und kein weiterer spieler auf server: server shutdown / sleep. das ganz muss wie gesagt togglebar sein und nur explizit auf wunsch eingestellt werden können. shutdown nach letztem disconnect soll je server verzögerbar sein, also zb. erst nach 5 minuten shutdown~~


 ~~Sleep-Verzögerung (Sek.) nicht nur in sek, sondern zb. auch minuten, stunden, tage auswählbar~~

~~manueller sleep button innerhalb des dashboards neben start, stopp und neustart, sowie in der detail ansicht~~

"City build" und minigames Server netzwerk, mit mehreren welten und selben invnetar usw. also zb. bau welt minenwelt usw. mit warp, shopsystem, jobs, geld usw. bungee cord, einfacher editor um server netzwerke zu bearbeiten, anderes system als lobby server

konsole überarbeiten, sodass sie praktisch wie ein cmd fenster funktioniert, in das man direkt schreiben und mit enter absenden kann, ohne das befehl senden feld darunter

bei manuellem backup: fortschrittsbalken, statt langes laden des tabs


~~die Einstellungen sind inzwischen zu unübersichtlich, bitte ebenfalls sinnvolle Tabs zur sortierung erstellen, wie bei der Server detailansicht~~


~~schlafender server wird in lobby nicht als solcher angezeigt und von einem beitrittsversuch über lobby nicht geweckt~~


~~es soll auch möglich sein, von einem server mit /lobby wieder zur lobby zu gelangen~~

~~alte plugins erkennen / updaten~~



die verwaltung des hub / lobby server soll wie die eines normalen servers mit untermenus usw. funktionieren und auch genauso aussehen, um mehr kontiniuität zu schaffen


~~logs und db live / deutlich öfter, als nur alle paar minuten plus alte löschen um speicher zu sparen~~



~~welten zwischen servern kopierbar zb. welt von david 1.21.11 spigot server nach neuen 26.1 server kopieren~~

KI Ideen:

Monitoring & Alarme

Verlaufs-Metriken: CPU/RAM/Spielerzahl/TPS als Zeitreihe speichern und als Graphen anzeigen (Trends statt nur Live-Werte).

TPS/MSPT-Überwachung (Tick-Performance) mit Lag-Warnung; automatischer Performance-Report bei anhaltendem Lag.

Crash-Analyse: neuesten crash-report automatisch erfassen, Kernfehler extrahieren und im UI anzeigen.

Benachrichtigungen (Discord/Webhook/E-Mail/eigene push nachrichgt auf handy) bei Crash, Start/Stopp, Fehler-Häufung, Backup-Ergebnis.


Spieler & Community

Spieler-Statistiken: Spielzeit, Sessions, erste/letzte Anmeldung, aktivste Spieler (Historie) (Server spezifisch, sowie übergreifend).

Schnellaktionen pro Spieler im UI: kicken, anschreiben, Gamemode/Teleport; Skin/Avatar in der Online-Liste.


Welten & Inhalte

Welten-Manager: mehrere Welten je Server, umschalten, einzelne Welt sichern/hoch-/runterladen, Seed anzeigen. später evtl.

~~Datapack-Verwaltung (installieren/aktivieren/deaktivieren), analog zu Mods.~~

~~Resourcepack-Hosting: Server-Resourcepack automatisch bereitstellen und resource-pack-URL setzen, ebenfalls über api, analog zu mods.~~

Chunk-Vorgenerierung (z.B. via Chunky) zur Lag-Reduktion eher nein, wenn nur on demand.


Backups (erweitert)

Inkrementelle Backups (nur geänderte Dateien) + Aufbewahrungsregeln (N täglich / M wöchentlich).

Off-site/Cloud-Backup (S3/Backblaze) zusätzlich zur lokalen Sicherung.

Integritätsprüfung (Checksummen) + Diff-Ansicht zwischen zwei Backups.


Automatisierung

Regel-Engine (event-getrieben): „wenn Log-Muster X → Befehl/Neustart/Benachrichtigung" (z.B. Auto-Neustart bei OutOfMemory, Auto-Kick bei Spam).

Ankündigungs-Scheduler: geplante Broadcasts / rotierende MOTD.

Aikar's-Flags-Preset: optimierte JVM/GC-Flags je RAM automatisch setzen.


Sicherheit & Zugriff

2FA (TOTP) für den Manager-Login. eher nein, wenn nur später

API-Tokens + dokumentierte REST-API für externe Automatisierung. sowieso nur über .env

RCON-Unterstützung (echtes Protokoll) als robuster Konsolenkanal, auch für extern gestartete Server.

Tunnel-Integration (playit.gg/ngrok) bzw. UPnP-Portöffnung, um ohne Portfreigabe erreichbar zu sein. lieber über eigene ionos domain "thormakmc.de"





Komfort/UX

Server-Tags/Gruppen + Favoriten, Sortierung/Filter im Dashboard.

Globale Suche über Server/Dateien/Logs.

PWA / mobil-optimierte Ansicht.

Onboarding-Assistent beim ersten Start.

getrennte nutzungsumgebungen, folgendes modell:
nutzer / Organisation modell: ein nutzer kann eine oder mehr organisationen besitzen, verwalten und delegieren, einer orga können mehrere nutzer angehören. rollenverwaltung in orga, aber für super admins immer zugriff auf alle und volle rechte
später: monetarisierung: server, orgas usw. vermieten, kosten nach kapazität, leistung und anzahl an orgas und server



Ideen aus dem Urlaub (09/2026), für viel später:

~~Graceful Rejection: ERLEDIGT als Vorab-Prüfung. Statt den Spieler wegzuschicken und danach aufzufangen (nach einem nativen Transfer ist die Lobby-Verbindung weg und kann ihn nicht mehr erreichen), wird VOR dem Wechsel gefragt - dann bleibt er einfach in seiner Lobby und liest den Grund im Chat. Eine Wahrheitsquelle (`lobby_service.check_join_allowed`) für BEIDE Lobbys: der Python-Hub ruft sie direkt auf, die Bukkit-Lobby über den lokalen TCP/JSON-Endpoint (`lobby_api_service`, Token, nur 127.0.0.1) - dadurch identische Texte, egal mit welchem Client. Greift bei Kompass/Command/Schild/Portal und auch bei `/lobby`. Geprüft werden: Server offline, Ban (mit Grund), Whitelist, voll, sowie per Status-Ping "läuft zwar, nimmt aber noch keine Verbindungen an". Überall fail-open - eine kaputte Prüfung darf niemanden aussperren.~~

~~Offen dazu: Modpack-Abgleich vor dem Wechsel (der Ping liefert bei Forge/NeoForge eine Mod-Liste - damit wäre "falsches Modpack" vorab erkennbar).~~ Weiterhin offen: Resourcepack-Abgleich (der Client meldet sein Pack nicht), der allererste Join (da gibt es noch keine Lobby zum Zurückkehren) und eine Ablehnung, die erst NACH dem Transfer passiert (z.B. ein Plugin auf dem Ziel) - dafür bräuchte es einen echten Proxy in der Verbindung.

Mobile Verwaltung: MCSM auch als App-Version + mobile Web-App zur Verwaltung. Selbes Backend, immer selber Stand wie die Web-Version. Dünne native App (PWA / Wrapper), die selbst nicht aktualisiert werden muss - alles läuft über die Web-App. Evtl. eigenes, für Mobile optimiertes Frontend. Außerdem die bestehende Website für mobile Browser optimieren. (erweitert die vorhandene "PWA / mobil-optimierte Ansicht")

Server-Vermietung: den Manager als Basis für Server-Vermietung schaffen - z.B. Bezahlmodelle, RAM- und Speicherzuweisung, Selbstverwaltung usw. URL-Schema: nutzername.servername.domain.de/... oder nutzername.domain.de/servername. (verwandt mit "mehrere getrennte Verwaltungsumgebungen" weiter oben)


live übersicht über map, spieler position, gebäude, inventar usw. Dynmap? interaktiv aus der vogelperspektive, pro server zuschaltbar und nach auswahl über link öffentlich einsehbar


rechte verwaltung: nutzern und nutzergruppen je per checkbox berechtigungen zuweisen


push benachrichtigungen auf handy und pc zb. bei crash


ai analysen für crashes usw

ai modpack / datapack / pluginpack / server generator und ersteller / modpack ersteller (modpack und quests und custom rezepte usw.) mit claude API -> curseforge code / link für installation



~~mir ist außerdem grade aufgefallen: es funktionieren nur vanilla tweaks, aber vanilla tweaks crafting rezepte werden nicht erkannt und nicht zur auswahl verfügbar~~

~~/lobby / /server lobby geht nicht~~


manager kann auch server pc restarten

~~zb. spigot oder andere pluginloader configs bearbeitbar machen~~



MOTD editor in Server einstellungen mit Farben, allen optionen usw.


KI-gestützte Funktionen nur ondemand, local ai ohne externe api?
-> Beantwortet: es stehen **200 $ Claude-API-Guthaben pro Monat** zur Verfügung. Die externe API ist damit gesetzt, lokale Modelle sind nicht nötig (bleiben aber als Rückfall denkbar, wenn das Monatsbudget aufgebraucht ist). Fundament und Log-/Crash-Analyse sind umgesetzt (siehe unten); die übrigen Funktionen stehen noch aus.
~~Beim Umsetzen zu beachten: Budget pro Monat deckeln und den Verbrauch sichtbar machen (sonst frisst eine Crash-Analyse-Schleife still das Guthaben); API-Schlüssel wie die übrigen Geheimnisse behandeln, nicht in die DB im Klartext; jeder KI-Aufruf ondemand und abschaltbar; Logs/Crash-Reports vor dem Versenden auf Spielernamen und IPs prüfen.~~
~~Rate Limit hinzufügen + missbrauch verhinden durch Themen lockin~~
~~API Anbieter einfach änderbar~~
~~API Keys nur in .env und die .env soll durch ein menufenster beschrieben werden, wie zb. bei der CurseForge API~~

**FUNDAMENT ERLEDIGT.** Alle Schranken greifen VOR dem Aufruf beim Anbieter - ein Aufruf, der nicht stattfindet, kostet nichts.
- Schlüssel: nicht in der .env, sondern per DPAPI verschlüsselt (an den Rechner gebunden, `CRYPTPROTECT_LOCAL_MACHINE`) in den Einstellungen. Eingabe über die Oberfläche, Anzeige nur maskiert, nie im Klartext zurückgelesen - auch nicht im Audit-Log. Entscheidung dazu: der Schlüssel muss nicht in die .env, er darf nur nicht einsehbar sein.
- Budget: Monatsdeckel (Standard 200 $), Verbrauchsbalken und Aufrufliste in den Einstellungen; Stunden- und Tageslimit je Nutzer. Moderatoren bekommen den halben Anteil.
- Jede Funktion einzeln abschaltbar, Modell pro Funktion festgelegt und änderbar.
- Themen-Sperre auf Ein- UND Ausgabe. Der Wortfilter gilt nur für den freien Agenten; bei den gebundenen Funktionen steht der Zweck schon durch den Aufruf fest.
- Vor dem Versand: IPs und Zugangsdaten raus. Spielernamen bleiben nach ausdrücklicher Entscheidung im Klartext - die Vorschau sagt das offen, statt es zu verschweigen. Umstellbar.
- Rechte: ab Moderator, mit kleinerem Kontingent. Der Agent darf Aktionen nur VORSCHLAGEN, ausgeführt wird erst nach Bestätigung und mit denselben Rechteprüfungen wie bei einem Klick.
- Anbieter austauschbar (`ANBIETER`-Register); aktuell Anthropic.

~~KI-Log-/Crash-Analyse: Fehlerursache erklären + konkrete Lösungsvorschläge (welche Mod, welcher Fix).~~
ERLEDIGT auf der Logs-Seite jedes Servers. Gesendet wird nicht das ganze Log, sondern die Zeilen, die eine Diagnose tragen: Kopf (Software, Version, geladene Mods - ohne den kann niemand sagen, WELCHE Mod schuld ist), alle Fehlerstellen mit Umfeld samt vollständigem Stacktrace, und das Ende. Das spart Guthaben und macht die Antwort genauer. Zwei Dinge, die beim Bauen aufgefallen sind und sonst still falsch gewesen wären: `\bException\b` findet "NullPointerException" NICHT (vor dem E steht kein Wortanfang) - genau diese Zeile ist die wichtigste im Log; und beim Kürzen darf nicht der Kopf wegfallen, sonst bleibt die Frage "welche Mod?" unbeantwortbar. Vor dem Senden zeigt "Was wird gesendet?" den tatsächlichen Ausschnitt - diese Vorschau kostet nichts. Für `view_only` ist die Funktion gar nicht sichtbar.

~~KI-Konfigurationsassistent: server.properties/JVM anhand Ziel (RAM, Spielerzahl, Modpack) optimieren.~~
ERLEDIGT im Dateieditor, wenn eine `.properties` im Assistenten-Modus offen ist. Die KI bekommt die Datei plus die echten Eckdaten (Typ, Version, zugewiesener Speicher, RAM des Rechners, was die anderen Server davon schon belegen, Spielerzahl, Zahl der Mods) - ohne die waere jeder Ratschlag geraten. Sie antwortet strukturiert (erzwungener Werkzeugaufruf), nicht in Prosa, die man parsen muesste.
**Geschrieben wird nichts.** Angehakte Vorschlaege wandern in die Felder des vorhandenen Formulars und werden mit dessen normalem Knopf gespeichert - gleicher Pfad, gleiche Pruefung wie bei einer Handeingabe. Gefuellte Felder werden hervorgehoben, sonst uebersieht man eine stille Aenderung in einem Formular mit dreissig Feldern.
Was die KI NICHT aendern darf, weil der Schaden unsichtbar ist: `level-name` (neue leere Welt, die alte bleibt als Ordner liegen), `level-seed`/`level-type` (Abbruchkante im Gelaende, nicht rueckgaengig), `server-port`/`server-ip`/`query.port`/`rcon.port` (verwaltet der Manager fuer Gateway und Sleep-Proxy), `online-mode` (trennt jeden Spieler von seinem Spielstand), `rcon.password`/`enable-rcon`. Erlaubt, aber nicht vorgehakt: alles mit merkbarer Folge fuer den Betrieb (difficulty, gamemode, pvp, white-list, max-players ...).
Zusaetzlich geprueft wird jeder Vorschlag gegen die echte Datei: erfundene Schluessel, Werte falscher Art (Zahl statt true/false - das kostet den Serverstart) und bereits gesetzte Werte kommen nicht durch. **Nichts wird still verworfen** - was nicht uebernommen wird, steht mit Grund da. Ein Speichervorschlag wird gegen den RAM des Rechners geprueft und warnt bei Ueberbuchung.
Nur fuer `.properties`: ohne lesbaren Istzustand liesse sich ein Vorschlag nicht pruefen, und genau diese Pruefung ist der Schutz. Fuer YAML (spigot.yml, paper-global.yml) waere das der naechste Schritt.

KI-Chat-Moderation: In-Game-Chat auf Toxizität/Spam prüfen, automatische Warnungen.

~~KI-Changelog-Zusammenfassung vor einem Mod-Update („Was ändert sich, gibt es Breaking Changes?").~~
ERLEDIGT auf der Inhalte-Seite: pro Eintrag ein Knopf "Was aendert sich?" neben "Version anwenden". Geholt werden die Changelogs ALLER Fassungen zwischen der installierten und der gewaehlten, nicht nur der der Zielversion - der Satz, der entscheidet ("config format changed, delete your old config"), steht meist in einer Zwischenfassung, die man mit einem Sprung ueberspringt. Modrinth liefert den Text im Versionsobjekt (`list_modrinth_versions` wirft ihn weg, deshalb ein eigener Abruf), CurseForge ueber einen Endpunkt pro Datei und als HTML, das zu Text wird.
Die Antwort ist strukturiert (erzwungener Werkzeugaufruf): Zusammenfassung, Liste der Breaking Changes mit Folge und konkretem Schritt, weitere Aenderungen, und eine Empfehlung (jetzt / vorher ein Schritt / warten) mit Begruendung - das Erste, was man im Dialog sieht.
Zwei Sparmassnahmen: ist bei KEINER Fassung ein Changelog hinterlegt (bei Plugins der Normalfall), wird die KI gar nicht gefragt - sie koennte aus nichts nur etwas erfinden, bezahlt waere es trotzdem; und bei vielen Zwischenfassungen wird auf die neuesten gekuerzt. Gekuerzt wird nie stillschweigend: was weggelassen wurde, welche Fassungen keinen Changelog haben und ob die installierte Fassung ueberhaupt noch in der Versionsliste steht (zurueckgezogen), steht in der Antwort.


### KI-Funktionen: Haertung nach einer gegnerischen Pruefung

Nach dem Bau aller vier Funktionen hat eine mehrstufige Pruefung (je ein Pruefer fuer Geld, Datenabfluss, Auswahl-Logik, Oberflaeche und Logquellen, jeder Fund anschliessend von einem zweiten Agenten zu widerlegen versucht) **19 bestaetigte Fehler** gefunden. Alle behoben, jeder mit Test. Die drei schwersten hatten dieselbe Ursache: Sparsamkeit und Saeuberung galten nur fuer den Weg, an den ich gedacht hatte.

- **Das Fragefeld war unbegrenzt und ungesaeubert.** Eine 12-MB-Frage ging vollstaendig an den Anbieter; statt ~3 Cent kostete ein Aufruf bis zu 2 $. Die Kuerzung galt nur fuer den mitgelieferten Log. Jetzt: 8000 Zeichen, und die Frage wird gesaeubert wie alles andere - wer eine Fehlermeldung hineinkopiert, schickt keine IPs und Geheimnisse mehr mit.
- **Die Geldschranken zaehlten erst NACH der Antwort.** Waehrend ein Aufruf lief, sah ihn keine andere Pruefung: 20 gleichzeitige Aufrufe kamen alle durch, obwohl 5 erlaubt waren, und der Monatsdeckel wurde um 238 % ueberfahren. Jetzt wird VOR dem Aufruf gebucht (mit Schaetzung, danach durch die echten Zahlen ersetzt), Pruefen und Buchen unter einer Sperre. Scheitert das Buchen, findet der Aufruf nicht statt - ein nicht gezaehlter Aufruf hoehlt den Deckel aus.
- **Geheimnisse mitten in einer Logzeile blieben stehen.** Alle Muster hingen mit `^` am Zeilenanfang; das passt zu Configdateien, Logzeilen beginnen aber mit Zeitstempel und Thread-Praefix. Betroffen waren RCON-Passwort, Velocity-Forwarding-Secret, Plugin-API-Schluessel, Bearer-Token und Webhook-URLs - und die Zeile `JVM Flags: ... -Dapi_key=...`, die in JEDEM Crash-Report steht. Jetzt auch mitten in der Zeile, wobei nur der Wert entfernt wird, nicht der Rest der Zeile.
- **Die IP-Regel war zu zurueckhaltend.** Sie verlangte ein Kontextwort ("from", "connection"); eine freistehende Adresse blieb stehen. Jetzt umgekehrt: im Zweifel ist es eine Adresse. Stehen bleibt eine vierteilige Zahl nur, wenn die Zeile erkennbar von einer Version spricht.
- **Beim Kuerzen des Logausschnitts fielen ausgewaehlte Zeilen still weg**, obwohl Budget frei war - darunter die Zeile, die die schuldige Mod nennt. Die Zeilengrenze griff unterhalb von ~370 Zeilen gar nicht, und es gab keine Zeichengrenze, weshalb die Saeuberung danach blind die Mitte herausschnitt. Jetzt feste Quoten mit Rueckverteilung, Zeilen- UND Zeichengrenze.
- **Die angezeigten Zahlen passten nicht zum Text:** die Zeichenzahl stammte aus der ungekuerzten Auswahl (bis Faktor 100 daneben), die Zahl der Fehlerstellen zaehlte das ganze Log statt den Ausschnitt, und bei vollstaendig gesendetem Log stand fest 0. Bei einer Anzeige, deren ganzer Zweck Verlaesslichkeit ist.
- **Analysiert wurde die beim Laden gerenderte Datei, nicht die gewaehlte.** Wer oben den Crash-Report waehlte, ohne auf "Anzeigen" zu klicken, bezahlte eine Analyse von `latest.log`. Jetzt wird die Auswahl beim Klick gelesen und in der Antwort genannt.
- **Bei Logs ueber 20000 Zeilen fehlte der Kopf trotzdem**, weil nur der Schwanz gelesen wurde - also genau die Software-/Version-/Modliste, die die Frage "welche Mod?" beantwortet. Jetzt Kopf UND Ende, mit der echten Gesamtzahl.
- **Ein UNC-Pfad im Dateiparameter baute eine SMB-Verbindung zum fremden Host auf**, bevor die Containment-Pruefung griff - der Dienst laeuft als SYSTEM und haette sich dabei automatisch angemeldet. Jetzt wird der Wert geprueft, bevor daraus ein Pfad entsteht (kein UNC, kein Laufwerksbuchstabe, keine Geraetenamen, kein `..`).
- **Ein rotiertes `.log.gz` wurde komplett entpackt in den Speicher gelesen** (ueber ein Gigabyte moeglich; der Dienst haette alle Serverprozesse mitgenommen), und ein defektes Archiv ergab eine 500-Seite. Jetzt zeilenweise mit begrenztem Puffer und einer verstaendlichen Meldung.
- Dazu: die Vorschau-Route prueft jetzt auch die Rolle (ein ausgeblendeter Knopf ist keine Pruefung), `[object Object]` in der Vorschau behoben, 401/403 sagen auf Deutsch, was zu tun ist, und die Auswahl rechnet beim schlimmsten Log 0,07 s statt mehrerer Sekunden.
- **Nebenbefund an der Themen-Sperre:** sie war beim freien Agenten voellig aus, weil mitgeschickte Daten den Wortfilter aufhoben und der Agent immer die Lage-Uebersicht mitschickt - ausgerechnet bei der Funktion, fuer die der Filter gedacht ist. Jetzt greift er unabhaengig davon, einmal pro Auftrag an der echten Frage des Nutzers, und er sieht das ganze Gespraech (eine Rueckfrage wie "und der andere?" traegt kein Schluesselwort).

Natürlichsprachliche Steuerung: „starte alle Vanilla-Server", „sichere Server X" → Aktionen.

~~eine KI integration, die fragen beantworten kann und auch cowork mößig aufgaben durchführen kann, nicht nur vorgefertigte commands, sondern freie bearbeitung und freies prompting wie claude code cowork auf dem rechner. aufgaben wie brarbeite diese config datei oder tue etwas in den einstellungen usw. rechte dürfen dadurch nicht umgehbar sein~~
ERLEDIGT als eigene Seite **KI-Assistent** (Navigation links, nicht fuer `view_only`). Frage oder Auftrag eintippen; der Assistent sieht selbst nach und antwortet, und bei allem, was etwas veraendert, legt er einen **Vorschlag** vor.
14 Werkzeuge, zwei Sorten:
- **Sieht nach** (laeuft ohne Rueckfrage, nur auf Servern, die der Nutzer ohnehin sehen darf): Serverdetails, Datei lesen, Dateien auflisten, Log ansehen, Sicherungen auflisten, Inhalte auflisten.
- **Veraendert** (nur nach Bestaetigung): Sicherung anlegen, starten, stoppen, neustarten, Konsolenbefehl, Einstellung setzen, Inhalte aktualisieren, Welt uebernehmen.
**Rechte sind nicht umgehbar.** Der Vorschlag kommt aus dem Browser und wird bei der Ausfuehrung NICHT geglaubt: Werkzeugname, Server und Recht werden neu gegen die Datenbank geprueft, die Parameter von derselben Dienstfunktion wie bei einem Klick. Es gibt keinen "bestaetigt"-Marker und kein Token. Ein Server, den der Nutzer nicht sehen darf, bekommt dieselbe Absage wie ein Server, der nicht existiert - sonst verraet der Agent dessen Existenz. `welt_uebernehmen` verlangt Super-Admin wie in der Oberflaeche, `einstellung_setzen` haelt dieselben Sperren wie der Konfigurationsassistent.
**Der Satz, den man vor dem Bestaetigen liest, formuliert der Manager**, nicht das Modell - sonst koennte die Beschreibung von der Tat abweichen ("ich lege nur eine Sicherung an", waehrend der Aufruf den Server stoppt). Dazu kommt eine Gefahrenstufe und ein Satz, was danach anders ist. Nur EINE Aktion auf einmal: ein Sammel-Ja soll es nicht geben. Ein erfundener Werkzeugname landet im Vorschlag und nicht in der Ausfuehrung.
Kosten: hoechstens 5 Anfragen je Auftrag (jede Runde ist eine eigene Anfrage), bei JEDER Anfrage geht eine kurze Lage-Uebersicht mit - sonst muesste der Agent als ersten Schritt immer erst die Serverliste abfragen. Der Verlauf lebt im Browser, wird bei jedem Zug mitgeschickt und auf 24 Zuege begrenzt; Inhaltsbloecke (Werkzeugergebnisse) nimmt die Route von draussen NICHT an.



