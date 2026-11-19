package dev.lakshya.analystdesk.tools;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

/** The one WRITE tool. Marked as a write tool in ToolRegistry; Phase 12 adds human confirmation before it runs. */
@Component
public class NotebookTools {

    public record Saved(String noteId, boolean created, String message) {
    }

    private final NoteStore store;

    public NotebookTools(NoteStore store) {
        this.store = store;
    }

    // LESSON: agents retry, and models repeat themselves. A write tool must be safe to call twice,
    // so the caller supplies the id and the same id never creates a second note.
    @Tool(description = """
            WRITES a research note to the permanent notebook. Use only when the user has asked to save a note.
            Never use it to look something up. Choose a noteId yourself (for example "hdfc-vs-icici-npa-fy24")
            and reuse the same noteId if you need to retry: saving the same noteId with the same content twice
            stores one note and is safe. The same noteId with different content is rejected.
            Returns ok=true with created=true (stored now) or created=false (already stored, nothing changed).""")
    public ToolResult saveNote(
            @ToolParam(description = "A unique id you choose for this note, lowercase with hyphens") String noteId,
            @ToolParam(description = "A short title") String title,
            @ToolParam(description = "The full text of the note") String text) {
        if (noteId == null || noteId.isBlank()) {
            return ToolResult.error("MISSING_NOTE_ID", "noteId is required",
                    "Choose a unique id such as \"hdfc-vs-icici-npa-fy24\".");
        }
        NoteStore.Note incoming = new NoteStore.Note(noteId, title, text);
        synchronized (this) {
            var existing = store.find(noteId);
            if (existing.isPresent()) {
                if (existing.get().equals(incoming)) {
                    return ToolResult.ok(new Saved(noteId, false, "Already saved; nothing changed."));
                }
                return ToolResult.error("ID_CONFLICT", "A different note already uses the id " + noteId,
                        "Choose a new noteId for this note.");
            }
            store.put(incoming);
        }
        return ToolResult.ok(new Saved(noteId, true, "Saved."));
    }
}
