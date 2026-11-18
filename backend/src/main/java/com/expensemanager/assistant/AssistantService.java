package com.expensemanager.assistant;

import com.expensemanager.assistant.llm.ChatMessage;
import com.expensemanager.assistant.llm.ChatMessage.ToolCall;
import com.expensemanager.assistant.llm.LlmClient;
import com.expensemanager.assistant.llm.LlmReply;
import com.expensemanager.entity.Category;
import com.expensemanager.exception.TooManyRequestsException;
import com.expensemanager.exception.ValidationException;
import com.expensemanager.repository.CategoryRepository;
import com.expensemanager.service.VendorService;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * One chat turn (ASSISTANT-SPEC §3, §7): validate, build the prompt, run the tool loop, return the
 * reply. Stateless: the browser sends its own recent history and nothing but notes is stored.
 * Deliberately not @Transactional, so no DB connection is held while waiting for the AI.
 */
@Service
public class AssistantService {

    static final int MESSAGE_MAX = 1000;
    static final int HISTORY_MAX_MESSAGES = 20;
    static final int HISTORY_MAX_CHARS = 12_000;
    static final int MAX_TOOL_ROUNDS = 5;
    static final int MAX_CALLS_PER_ROUND = 6;
    /** After this, the model must answer with what it has (keeps a turn under proxy timeouts). */
    static final long TURN_BUDGET_MILLIS = 45_000;
    static final int SUGGESTIONS_MAX = 3;
    static final int SUGGESTION_MAX_CHARS = 60;
    static final Set<String> LANGS = Set.of("en-IN", "hi-IN");
    static final String NO_ANSWER = "Sorry, I couldn't work that out. Please ask again in other words.";
    static final String RATE_LIMITED = "Too many messages to Muneem. Wait a minute and try again.";

    private final LlmClient llm;
    private final AssistantTools tools;
    private final NoteService noteService;
    private final CategoryRepository categoryRepository;
    private final VendorService vendorService;
    private final RateLimiter rateLimiter;
    private final Clock clock;

    public AssistantService(LlmClient llm, AssistantTools tools, NoteService noteService,
                            CategoryRepository categoryRepository, VendorService vendorService,
                            RateLimiter rateLimiter, Clock clock) {
        this.llm = llm;
        this.tools = tools;
        this.noteService = noteService;
        this.categoryRepository = categoryRepository;
        this.vendorService = vendorService;
        this.rateLimiter = rateLimiter;
        this.clock = clock;
    }

    public record HistoryItem(String role, String content) {
    }

    public record ChatRequest(String message, List<HistoryItem> history, Boolean voice, String lang) {
    }

    public record ChatResponse(String reply, String provider, List<String> toolsUsed, List<String> suggestions) {
    }

    public ChatResponse chat(ChatRequest request) {
        String message = request.message() == null ? "" : request.message().trim();
        if (message.isEmpty() || message.length() > MESSAGE_MAX) {
            throw new ValidationException("message", "must be 1-" + MESSAGE_MAX + " characters");
        }
        String lang = request.lang() == null || request.lang().isBlank() ? "en-IN" : request.lang().trim();
        if (!LANGS.contains(lang)) {
            throw new ValidationException("lang", "must be en-IN or hi-IN");
        }
        List<ChatMessage> history = history(request.history());
        if (!rateLimiter.tryAcquire()) {
            throw new TooManyRequestsException(RATE_LIMITED);
        }
        boolean voice = Boolean.TRUE.equals(request.voice());

        List<ChatMessage> messages = new ArrayList<>();
        messages.add(ChatMessage.system(Prompts.system(LocalDate.now(clock), categories(), vendorService.labels(),
                noteService.list().stream().map(NoteService.Note::note).toList(), voice, lang)));
        messages.addAll(history);
        messages.add(ChatMessage.user(message));

        long started = clock.millis();
        Set<String> toolsUsed = new LinkedHashSet<>();
        String provider;
        String text;
        for (int round = 0; ; round++) {
            boolean allowTools = round < MAX_TOOL_ROUNDS && clock.millis() - started < TURN_BUDGET_MILLIS;
            LlmReply reply = llm.chat(List.copyOf(messages), allowTools ? tools.definitions() : List.of());
            provider = reply.provider();
            ChatMessage answer = reply.message();
            if (!allowTools || !answer.hasToolCalls()) {
                text = answer.content();
                break;
            }
            List<ToolCall> calls = withIds(answer.toolCalls(), round);
            messages.add(new ChatMessage("assistant", answer.content(), calls, null));
            for (int i = 0; i < calls.size(); i++) {
                ToolCall call = calls.get(i);
                String name = call.function() == null ? null : call.function().name();
                String result;
                if (i < MAX_CALLS_PER_ROUND) {
                    toolsUsed.add(String.valueOf(name));
                    result = tools.call(name, call.function() == null ? null : call.function().arguments());
                } else {
                    // every call needs an answer, or the API rejects the next request
                    result = "{\"error\":\"skipped: at most " + MAX_CALLS_PER_ROUND + " tool calls at a time\"}";
                }
                messages.add(ChatMessage.tool(call.id(), result));
            }
        }
        if (text == null || text.isBlank()) {
            return new ChatResponse(NO_ANSWER, provider, List.copyOf(toolsUsed), List.of());
        }
        return split(text, provider, List.copyOf(toolsUsed));
    }

    /** Checks roles and caps; drops the oldest messages until the text fits in {@value #HISTORY_MAX_CHARS} chars. */
    static List<ChatMessage> history(List<HistoryItem> items) {
        if (items == null || items.isEmpty()) {
            return List.of();
        }
        if (items.size() > HISTORY_MAX_MESSAGES) {
            throw new ValidationException("history", "at most " + HISTORY_MAX_MESSAGES + " messages");
        }
        Deque<ChatMessage> kept = new ArrayDeque<>();
        int chars = 0;
        for (int i = items.size() - 1; i >= 0; i--) {
            HistoryItem item = items.get(i);
            if (item == null || item.content() == null || !("user".equals(item.role()) || "assistant".equals(item.role()))) {
                throw new ValidationException("history", "each message needs role user or assistant and content");
            }
            chars += item.content().length();
            if (chars > HISTORY_MAX_CHARS) {
                break;
            }
            kept.addFirst(new ChatMessage(item.role(), item.content(), null, null));
        }
        return List.copyOf(kept);
    }

    /** Some providers omit tool-call ids; the API needs one to match each result to its call. */
    private static List<ToolCall> withIds(List<ToolCall> calls, int round) {
        List<ToolCall> out = new ArrayList<>(calls.size());
        for (int i = 0; i < calls.size(); i++) {
            ToolCall c = calls.get(i);
            String id = c.id() == null || c.id().isBlank() ? "call_" + round + "_" + i : c.id();
            out.add(new ToolCall(id, c.type() == null ? "function" : c.type(), c.function(), c.extraContent()));
        }
        return out;
    }

    /** Takes the ">> a | b | c" line off the reply and turns it into suggestions. */
    static ChatResponse split(String text, String provider, List<String> toolsUsed) {
        List<String> lines = new ArrayList<>(Arrays.asList(text.strip().split("\\R")));
        List<String> suggestions = List.of();
        for (int i = lines.size() - 1; i >= 0; i--) {
            String line = lines.get(i).strip();
            if (line.startsWith(Prompts.SUGGESTIONS_MARK)) {
                suggestions = Arrays.stream(line.substring(Prompts.SUGGESTIONS_MARK.length()).split("\\|"))
                        .map(String::strip)
                        .filter(s -> !s.isEmpty())
                        .map(s -> s.length() > SUGGESTION_MAX_CHARS ? s.substring(0, SUGGESTION_MAX_CHARS) : s)
                        .limit(SUGGESTIONS_MAX)
                        .toList();
                lines.remove(i);
                break;
            }
        }
        String reply = String.join("\n", lines).strip();
        return new ChatResponse(reply.isEmpty() ? NO_ANSWER : reply, provider, toolsUsed, suggestions);
    }

    private List<String> categories() {
        return categoryRepository.findAll(Sort.by("id")).stream().map(Category::getName).toList();
    }
}
