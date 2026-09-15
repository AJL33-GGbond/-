package com.letter;

import java.time.LocalDateTime;
import java.util.List;
import com.letter.model.Letter;
import com.letter.repository.LetterRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
class LetterApplicationTests {
    @Autowired
    private WebApplicationContext context;
    @MockitoBean
    private LetterRepository repository;
    @MockitoBean
    private org.springframework.transaction.PlatformTransactionManager transactionManager;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context).build();
    }

    @Test
    void homeRendersForm() throws Exception {
        mvc.perform(get("/"))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("保存草稿")));
    }

    @Test
    void validDraftPreservesContent() throws Exception {
        String letter = "  虚构测试信 💌\n第二行";
        when(repository.saveDraft(letter)).thenReturn(7L);
        mvc.perform(post("/letters").param("content", letter))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/letters/7"));
        verify(repository).saveDraft(letter);
    }

    @Test
    void blankDraftDoesNotReachRepository() throws Exception {
        for (String blank : List.of("", "   ", "\n\t")) {
            mvc.perform(post("/letters").param("content", blank))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("正文不能为空")));
        }
        verifyNoInteractions(repository);
    }

    @Test
    void inboxRendersObjectsAndEscapesHtml() throws Exception {
        when(repository.findAll()).thenReturn(List.of(new Letter(
            7, "虚构信件<script>alert(1)</script>", "DRAFT",
            LocalDateTime.of(2026, 9, 15, 10, 30),
            LocalDateTime.of(2026, 9, 15, 10, 30), null
        )));
        mvc.perform(get("/letters"))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("信件编号 #7")))
            .andExpect(content().string(containsString("2026-09-15 10:30:00")))
            .andExpect(content().string(containsString("草稿")))
            .andExpect(content().string(containsString("尚未寄出")))
            .andExpect(content().string(containsString("&lt;script&gt;")))
            .andExpect(content().string(not(containsString("<script>"))));
    }

    @Test
    void sentLetterRendersSentTime() throws Exception {
        var created = LocalDateTime.of(2026, 9, 15, 10, 30);
        var sent = created.plusHours(1);
        when(repository.findAll()).thenReturn(List.of(
            new Letter(8, "虚构已寄出信", "SENT", created, sent, sent)));
        mvc.perform(get("/letters"))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("已寄出")))
            .andExpect(content().string(containsString("2026-09-15 11:30:00")))
            .andExpect(content().string(not(containsString("尚未寄出"))));
    }

    @Test
    void detailRefreshOnlyReadsAndEscapesContent() throws Exception {
        var time = LocalDateTime.of(2026, 9, 15, 10, 30);
        when(repository.findById(7)).thenReturn(java.util.Optional.of(
            new Letter(7, "正文<script>测试</script>", "DRAFT", time, time, null)));
        for (int i = 0; i < 2; i++) {
            mvc.perform(get("/letters/7"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("信件编号 #7")))
                .andExpect(content().string(containsString("&lt;script&gt;")));
        }
        verify(repository, never()).saveDraft(anyString());
    }

    @Test
    void missingLetterReturnsFriendly404() throws Exception {
        when(repository.findById(999)).thenReturn(java.util.Optional.empty());
        mvc.perform(get("/letters/999"))
            .andExpect(status().isNotFound())
            .andExpect(content().string(containsString("没有找到这封信")));
    }

    @Test
    void malformedIdsReturnFriendly400() throws Exception {
        for (String id : List.of("abc", "0", "-1", "999999999999999999999")) {
            mvc.perform(get("/letters/" + id))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(containsString("信件编号格式不正确")));
        }
        verifyNoInteractions(repository);
    }

    @Test
    void emptyInboxRendersHint() throws Exception {
        when(repository.findAll()).thenReturn(List.of());
        mvc.perform(get("/letters"))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("还没有保存的信件。")));
    }
    private Letter draftFixture(String state) {
        var time = LocalDateTime.of(2026, 9, 15, 10, 30);
        return new Letter(7, "原文\n<script>测试</script>", state, time, time, null);
    }

    @Test
    void editPrefillsEscapedContent() throws Exception {
        when(repository.findById(7)).thenReturn(java.util.Optional.of(draftFixture("DRAFT")));
        mvc.perform(get("/letters/7/edit"))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("原文\n&lt;script&gt;")))
            .andExpect(content().string(containsString("/letters/7/edit")));
    }

    @Test
    void editingUpdatesSameIdAndRedirects() throws Exception {
        when(repository.findById(7)).thenReturn(java.util.Optional.of(draftFixture("DRAFT")));
        when(repository.updateDraft(7, "新正文")).thenReturn(1);
        mvc.perform(post("/letters/7/edit").param("content", "新正文"))
            .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/letters/7"));
        verify(repository).updateDraft(7, "新正文");
        verify(repository, never()).saveDraft(anyString());
    }

    @Test
    void blankEditPreservesSubmittedInputAndDoesNotWrite() throws Exception {
        when(repository.findById(7)).thenReturn(java.util.Optional.of(draftFixture("DRAFT")));
        for (String blank : List.of("", "   ", "\n\t")) {
            mvc.perform(post("/letters/7/edit").param("content", blank))
                .andExpect(status().isBadRequest())
                .andExpect(model().attribute("content", blank))
                .andExpect(content().string(containsString("正文不能为空")));
        }
        verify(repository, never()).updateDraft(anyLong(), anyString());
    }

    @Test
    void sentLetterRejectsEditPageAndDirectPost() throws Exception {
        when(repository.findById(7)).thenReturn(java.util.Optional.of(draftFixture("SENT")));
        mvc.perform(get("/letters/7/edit")).andExpect(status().isConflict());
        mvc.perform(post("/letters/7/edit").param("content", "篡改"))
            .andExpect(status().isConflict());
        verify(repository, never()).updateDraft(anyLong(), anyString());
    }

    @Test
    void sentDuringEditReturnsConflict() throws Exception {
        when(repository.findById(7)).thenReturn(
            java.util.Optional.of(draftFixture("DRAFT")),
            java.util.Optional.of(draftFixture("SENT")));
        when(repository.updateDraft(7, "新正文")).thenReturn(0);
        mvc.perform(post("/letters/7/edit").param("content", "新正文"))
            .andExpect(status().isConflict());
    }

    @Test
    void missingEditAndInvalidIdReturnErrors() throws Exception {
        when(repository.findById(999)).thenReturn(java.util.Optional.empty());
        mvc.perform(get("/letters/999/edit")).andExpect(status().isNotFound());
        mvc.perform(post("/letters/999/edit").param("content", "正文"))
            .andExpect(status().isNotFound());
        mvc.perform(post("/letters/abc/edit").param("content", "正文"))
            .andExpect(status().isBadRequest());
        verify(repository, never()).updateDraft(anyLong(), anyString());
    }
    @Test
    void confirmationOnlyReadsAndEscapesContent() throws Exception {
        when(repository.findById(7)).thenReturn(java.util.Optional.of(draftFixture("DRAFT")));
        mvc.perform(get("/letters/7/confirm"))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("确认寄出")))
            .andExpect(content().string(containsString("&lt;script&gt;")));
        verify(repository, never()).sendDraft(anyLong());
    }

    @Test
    void sendAndRepeatOnlyTransitionOnce() throws Exception {
        when(repository.findByIdForUpdate(7)).thenReturn(
            java.util.Optional.of(draftFixture("DRAFT")), java.util.Optional.of(draftFixture("SENT")));
        when(repository.sendDraft(7)).thenReturn(1);
        for (int i = 0; i < 2; i++) {
            mvc.perform(post("/letters/7/send").param("content", draftFixture("DRAFT").getContent()))
                .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/letters/7"));
        }
        verify(repository, times(1)).sendDraft(7);
        verify(repository, never()).saveDraft(anyString());
    }

    @Test
    void staleConfirmationCannotSendChangedDraft() throws Exception {
        when(repository.findByIdForUpdate(7)).thenReturn(java.util.Optional.of(draftFixture("DRAFT")));
        mvc.perform(post("/letters/7/send").param("content", "旧内容"))
            .andExpect(status().isConflict())
            .andExpect(content().string(containsString("草稿内容已改变")));
        verify(repository, never()).sendDraft(anyLong());
    }

    @Test
    void emptyDraftAndMissingLetterCannotSend() throws Exception {
        var time = LocalDateTime.of(2026, 9, 15, 10, 30);
        when(repository.findByIdForUpdate(7)).thenReturn(java.util.Optional.of(
            new Letter(7, "   ", "DRAFT", time, time, null)));
        mvc.perform(post("/letters/7/send").param("content", "   ")).andExpect(status().isBadRequest());
        mvc.perform(post("/letters/999/send")).andExpect(status().isNotFound());
        mvc.perform(post("/letters/abc/send")).andExpect(status().isBadRequest());
        verify(repository, never()).sendDraft(anyLong());
    }
    @Test
    void confirmationAcceptsBrowserLineEndingsWithoutChangingBody() throws Exception {
        when(repository.findByIdForUpdate(7)).thenReturn(java.util.Optional.of(draftFixture("DRAFT")));
        when(repository.sendDraft(7)).thenReturn(1);
        mvc.perform(post("/letters/7/send").param("content",
                draftFixture("DRAFT").getContent().replace("\n", "\r\n")))
            .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/letters/7"));
        verify(repository).sendDraft(7);
        verify(repository, never()).updateDraft(anyLong(), anyString());
    }

    @Test
    void sentDetailHasNoEditOrSendControls() throws Exception {
        when(repository.findById(7)).thenReturn(java.util.Optional.of(draftFixture("SENT")));
        mvc.perform(get("/letters/7"))
            .andExpect(status().isOk())
            .andExpect(content().string(not(containsString("/letters/7/edit"))))
            .andExpect(content().string(not(containsString("/letters/7/confirm"))));
    }

    @Test
    void sendGetCannotChangeState() throws Exception {
        mvc.perform(get("/letters/7/send")).andExpect(status().isMethodNotAllowed());
        verify(repository, never()).sendDraft(anyLong());
    }
}
