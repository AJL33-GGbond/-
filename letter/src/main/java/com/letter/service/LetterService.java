package com.letter.service;

import java.util.List;
import com.letter.model.Letter;
import com.letter.repository.LetterRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LetterService {
    private final LetterRepository letterRepository;

    public LetterService(LetterRepository letterRepository) {
        this.letterRepository = letterRepository;
    }

    public long saveDraft(String content) {
        if (content == null || content.isBlank()) {
            throw new IllegalArgumentException("正文不能为空，请返回填写。");
        }
        return letterRepository.saveDraft(content);
    }

    public enum SendResult { SENT, ALREADY_SENT, NOT_FOUND, INVALID, CHANGED, LOCKED }

    @Transactional
    public SendResult sendDraft(long id, String confirmedContent) {
        var found = letterRepository.findByIdForUpdate(id);
        if (found.isEmpty()) return SendResult.NOT_FOUND;
        var letter = found.get();
        if ("SENT".equals(letter.getStatus())) return SendResult.ALREADY_SENT;
        if (!"DRAFT".equals(letter.getStatus())) return SendResult.LOCKED;
        if (letter.getContent() == null || letter.getContent().isBlank()) return SendResult.INVALID;
        // Browser form submission can normalize line endings to CRLF.
        if (confirmedContent == null || !normalizeLineEndings(letter.getContent())
                .equals(normalizeLineEndings(confirmedContent))) return SendResult.CHANGED;
        return letterRepository.sendDraft(id) == 1 ? SendResult.SENT : SendResult.LOCKED;
    }

    private static String normalizeLineEndings(String content) {
        return content.replace("\r\n", "\n").replace('\r', '\n');
    }

    public enum EditResult { UPDATED, NOT_FOUND, LOCKED }

    public EditResult updateDraft(long id, String content) {
        if (content == null || content.isBlank()) {
            throw new IllegalArgumentException("正文不能为空，请填写后再保存。");
        }
        // The status condition in UPDATE also protects against a send during editing.
        if (letterRepository.updateDraft(id, content) == 1) {
            return EditResult.UPDATED;
        }
        var existing = letterRepository.findById(id);
        if (existing.isEmpty()) return EditResult.NOT_FOUND;
        // Some database configurations return zero when content/time are unchanged.
        return "DRAFT".equals(existing.get().getStatus())
            ? EditResult.UPDATED : EditResult.LOCKED;
    }

    public java.util.Optional<Letter> findById(long id) {
        return letterRepository.findById(id);
    }

    public List<Letter> findAll() {
        return letterRepository.findAll();
    }
}
