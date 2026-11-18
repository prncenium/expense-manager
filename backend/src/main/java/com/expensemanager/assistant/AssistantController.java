package com.expensemanager.assistant;

import com.expensemanager.assistant.AssistantService.ChatRequest;
import com.expensemanager.assistant.AssistantService.ChatResponse;
import com.expensemanager.assistant.llm.LlmClient;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Muneem's routes (ASSISTANT-SPEC §7). */
@RestController
@RequestMapping("/api/assistant")
public class AssistantController {

    private final LlmClient llm;
    private final AssistantService assistantService;
    private final NoteService noteService;

    public AssistantController(LlmClient llm, AssistantService assistantService, NoteService noteService) {
        this.llm = llm;
        this.assistantService = assistantService;
        this.noteService = noteService;
    }

    /** Whether Muneem can answer, and which providers are configured (never the keys). */
    @GetMapping("/status")
    public Status status() {
        List<String> providers = llm.providers();
        return new Status(!providers.isEmpty(), providers);
    }

    public record Status(boolean enabled, List<String> providers) {
    }

    @PostMapping("/chat")
    public ChatResponse chat(@RequestBody ChatRequest request) {
        return assistantService.chat(request);
    }

    @GetMapping("/notes")
    public List<NoteService.Note> notes() {
        return noteService.list();
    }

    @DeleteMapping("/notes/{id}")
    public ResponseEntity<Void> deleteNote(@PathVariable long id) {
        noteService.delete(id);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/notes")
    public ResponseEntity<Void> deleteAllNotes() {
        noteService.deleteAll();
        return ResponseEntity.noContent().build();
    }
}
