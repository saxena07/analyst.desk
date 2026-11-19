package dev.lakshya.analystdesk.tools;

import java.util.Optional;

/** Where saved notes live. In memory for now; Phase 13 replaces it with the pgvector notebook. */
public interface NoteStore {

    record Note(String noteId, String title, String text) {
    }

    Optional<Note> find(String noteId);

    void put(Note note);
}
