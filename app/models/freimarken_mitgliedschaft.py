from datetime import datetime

from sqlalchemy import DateTime, ForeignKey, Index, Integer, String, func
from sqlalchemy.orm import Mapped, mapped_column

from app.db.base import Base


class FreimarkenMitgliedschaft(Base):
    """Eine bezahlte Mitgliedschaft und der Zuschlag, den sie gibt.

    WARUM DIE TABELLE IM MANAGER STEHT UND NICHT IM PLUGIN
    Der Plan sagt es ausdruecklich: "Das Plugin spricht nie mit dem Zahlungsanbieter"
    (docs/citybuild_plan.md, Schritt 12a.5 und Bauschritt 16). Das Plugin kennt nur die
    WIRKUNG, und die kommt ueber ein Recht der Form ``freimarken.extra.<name>.<zahl>``,
    gelesen in ``Rangzahl.ausRecht``. Wer wann wofuer bezahlt hat, ist Verwaltung und
    gehoert hierher - schon weil eine Rueckbuchung Monate spaeter kommt und das Plugin
    davon nichts wissen muss.

    JE SERVER EINE ZEILE
    Der Zuschlag wirkt ueber ein Recht, und ein Recht wird auf EINEM Server vergeben.
    Eine netzweite Mitgliedschaft ist darum mehrere Zeilen - eine je Server, auf dem sie
    gelten soll. Eine Spalte "gilt ueberall" waere eine Einstellung, die nur teilweise
    wirkt: Sie stuende in der Datenbank, und auf einem Server ohne das Recht waere sie
    stumm wirkungslos.

    DIE KENNUNG IST DER SCHLUESSEL, DER NAME NUR DIE BESCHRIFTUNG
    ``spieler_name`` ist der Name zur Zeit des Eintrags und wird NICHT nachgefuehrt. Nach
    einer Umbenennung ist er veraltet; gesucht und verglichen wird ueber
    ``spieler_kennung``. Derselbe Mensch kann sonst unter zwei Namen zweimal in der Liste
    stehen.

    BIS WANN DARF OFFEN SEIN
    ``bis`` ist NULL-faehig: Das heisst "laeuft, bis jemand sie beendet". Ein weit in der
    Zukunft liegendes Datum waere dasselbe mit einer Luege darin, und eine Auswertung
    "wessen Mitgliedschaft laeuft aus" wuerde sie mitzaehlen.
    """

    __tablename__ = "freimarken_mitgliedschaften"

    id: Mapped[int] = mapped_column(primary_key=True)
    server_id: Mapped[int] = mapped_column(
        ForeignKey("servers.id", ondelete="CASCADE"), index=True)

    spieler_kennung: Mapped[str] = mapped_column(String(36), index=True)
    spieler_name: Mapped[str] = mapped_column(String(32), default="")

    # Der Name des Zuschlags, so wie er im Recht steht: parzellen, gruppe, inseln,
    # wolken, freistaedte. Die gueltige Liste steht in app/assets/freimarken_felder.yml
    # und NICHT hier als Aufzaehlung - sonst muesste eine neue Art des Plugins die
    # Datenbank aendern.
    zuschlag_name: Mapped[str] = mapped_column(String(32))
    zuschlag_zahl: Mapped[int] = mapped_column(Integer)

    seit: Mapped[datetime] = mapped_column(DateTime(timezone=True))
    bis: Mapped[datetime | None] = mapped_column(
        DateTime(timezone=True), nullable=True, default=None)

    # Woher die Zahlung kam. Heute "von Hand"; wenn ein Anbieter dazukommt, steht hier
    # sein Name und in zahlung_beleg seine Belegnummer. Freitext und keine Aufzaehlung:
    # Welcher Anbieter es wird, ist noch nicht entschieden (Bauschritt 16).
    zahlung_herkunft: Mapped[str] = mapped_column(String(64), default="von Hand")
    zahlung_beleg: Mapped[str | None] = mapped_column(
        String(128), nullable=True, default=None)

    # Das WARUM aus Plan 12a.2 Regel 3. Es steht an der Zeile und nicht nur im
    # Protokoll: Wer in einem halben Jahr fragt, warum dieser Spieler drei Parzellen
    # mehr hat, liest die Zeile und nicht das Audit-Log.
    grund: Mapped[str] = mapped_column(String(256), default="")

    angelegt_von_user_id: Mapped[int | None] = mapped_column(
        ForeignKey("users.id", ondelete="SET NULL"), nullable=True, default=None)
    angelegt_am: Mapped[datetime] = mapped_column(
        DateTime(timezone=True), server_default=func.now())

    __table_args__ = (
        # Die Frage, die jede Seite stellt: "was hat dieser Spieler auf diesem Server".
        Index("ix_freimarken_mitglied_server_spieler", "server_id", "spieler_kennung"),
    )
