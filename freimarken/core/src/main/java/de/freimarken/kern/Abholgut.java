package de.freimarken.kern;

/**
 * Ein Stapel auf dem Weg ins Abholfach - was hineinkommt, noch ohne Platz darin.
 *
 * <p><b>Der Kern weiss nicht, was ein Gegenstand ist.</b> Er kennt keine
 * {@code ItemStack}-Klasse und soll keine kennen: Die Schicht darueber verwandelt einen
 * Stapel in Text und zurueck, und alles dazwischen - Datenbank, Regel, Probe - sieht nur
 * diesen Text. Darum ist {@link #inhalt} eine Zeichenfolge und kein {@code byte[]}: Ein
 * Feld aus Bytes in einem {@code record} haette ein {@code equals}, das Inhalte nicht
 * vergleicht, und eine Probe darauf waere gruen, ohne etwas zu pruefen.
 *
 * <p><b>Die Beschriftung ist NICHT die Wahrheit ueber den Inhalt</b> - der Inhalt ist es.
 * Sie steht trotzdem daneben, und zwar aus einem Grund, der sich erst in Jahren zeigt:
 * Laesst sich ein Stapel nach einem Minecraft-Update nicht mehr zurueckverwandeln, ist
 * die Beschriftung das Einzige, was noch sagt, was da verloren geht. Eine Zeile
 * "64x Eichenholz, nicht lesbar" im Protokoll ist von Hand zu ersetzen; eine Zeile
 * "ein Stapel, nicht lesbar" ist es nicht.
 *
 * @param beschriftung was drin ist, in Worten - fuer die Anzeige und fuer das Protokoll.
 *                     Kurz genug fuer eine Chatzeile
 * @param inhalt       der Stapel als Text, wie die Schicht darueber ihn geschrieben hat.
 *                     Was darin steht, ist dem Kern gleich; dass er nicht leer ist und
 *                     in eine Datenbankzeile passt, nicht
 */
public record Abholgut(String beschriftung, String inhalt) {

    /** Was in eine Chatzeile passt, neben Nummer und Herkunft. */
    public static final int LAENGSTE_BESCHRIFTUNG = 96;

    /**
     * Ein Megabyte Text je Stapel.
     *
     * <p>Erreichbar ist das nicht: Ein gewoehnlicher Stapel wird zu einigen Dutzend
     * Zeichen, ein beschriebenes Buch zu einigen Tausend. Die Grenze steht fuer den Fall,
     * den noch niemand gebaut hat - eine Kiste in einer Kiste in einer Kiste. Sie steht
     * UNTER dem, was die Datenbankspalte haelt ({@code MEDIUMTEXT}, 16 MB), und das ist
     * die Absicht: Ein Stapel, der hier auffaellt, wird liegen gelassen und protokolliert.
     * Faellt er erst der Datenbank auf, scheitert der ganze Schreibzug - und dann ist
     * nicht ein Stapel betroffen, sondern die ganze Raeumung.
     */
    public static final int LAENGSTER_INHALT = 1024 * 1024;

    public Abholgut {
        if (beschriftung == null || beschriftung.isBlank()) {
            throw new IllegalArgumentException("Ein Abholstueck ohne Beschriftung ist"
                    + " eines, von dem niemand sagen kann, was es war");
        }
        beschriftung = beschriftung.trim();
        if (beschriftung.length() > LAENGSTE_BESCHRIFTUNG) {
            throw new IllegalArgumentException("Die Beschriftung \"" + beschriftung
                    + "\" ist " + beschriftung.length() + " Zeichen lang - hoechstens "
                    + LAENGSTE_BESCHRIFTUNG + " passen in eine Zeile.");
        }
        if (inhalt == null || inhalt.isEmpty()) {
            throw new IllegalArgumentException("Ein Abholstueck ohne Inhalt ist keines."
                    + " Wer nichts einzulegen hat, legt nichts ein - eine leere Zeile im"
                    + " Fach waere ein Gegenstand, den der Spieler nie bekommt.");
        }
        // NICHT getrimmt: Was die Schicht darueber geschrieben hat, kommt Zeichen fuer
        // Zeichen zurueck. Ein Trimmen hier waere eine stille Aenderung an Daten, die
        // diese Schicht nicht versteht.
        if (inhalt.length() > LAENGSTER_INHALT) {
            throw new IllegalArgumentException("Der Stapel \"" + beschriftung + "\" wird"
                    + " zu " + inhalt.length() + " Zeichen Text - hoechstens "
                    + LAENGSTER_INHALT + " gehen ins Fach.");
        }
    }
}
