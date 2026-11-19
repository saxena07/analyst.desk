package dev.lakshya.analystdesk.tools;

import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Component
public class InMemoryNoteStore implements NoteStore {

    private final ConcurrentMap<String, Note> notes = new ConcurrentHashMap<>();

    @Override
    public Optional<Note> find(String noteId) {
        return Optional.ofNullable(notes.get(noteId));
    }

    @Override
    public void put(Note note) {
        notes.put(note.noteId(), note);
    }
}
