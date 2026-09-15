package com.letter.controller;

import com.letter.service.LetterService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class LetterController {
    private final LetterService letterService;

    public LetterController(LetterService letterService) {
        this.letterService = letterService;
    }

    @GetMapping("/letters")
    public String listLetters(Model model) {
        model.addAttribute("letters", letterService.findAll());
        return "letters";
    }

    @GetMapping("/letters/{id}")
    public String detail(@PathVariable("id") String rawId, Model model, HttpServletResponse response) {
        long id;
        try {
            id = Long.parseLong(rawId);
            if (id <= 0) throw new NumberFormatException();
        } catch (NumberFormatException exception) {
            response.setStatus(400);
            model.addAttribute("message", "信件编号格式不正确。");
            return "letter-error";
        }
        var letter = letterService.findById(id);
        if (letter.isEmpty()) {
            response.setStatus(404);
            model.addAttribute("message", "没有找到这封信，请返回信箱查看。");
            return "letter-error";
        }
        model.addAttribute("letter", letter.get());
        return "letter-detail";
    }

    @GetMapping("/letters/{id}/edit")
    public String edit(@PathVariable("id") String rawId, Model model, HttpServletResponse response) {
        String view = detail(rawId, model, response);
        if (!"letter-detail".equals(view)) return view;
        var letter = (com.letter.model.Letter) model.getAttribute("letter");
        if (!"DRAFT".equals(letter.getStatus())) {
            response.setStatus(409);
            model.addAttribute("message", "这封信已锁定，不能修改正文。");
            return "letter-error";
        }
        model.addAttribute("content", letter.getContent());
        return "letter-edit";
    }

    @PostMapping("/letters/{id}/edit")
    public String update(@PathVariable("id") String rawId,
                         @RequestParam(value = "content", defaultValue = "") String content,
                         Model model, HttpServletResponse response) {
        String view = edit(rawId, model, response);
        if (!"letter-edit".equals(view)) return view;
        long id = Long.parseLong(rawId);
        try {
            var result = letterService.updateDraft(id, content);
            if (result == LetterService.EditResult.UPDATED) return "redirect:/letters/" + id;
            response.setStatus(result == LetterService.EditResult.NOT_FOUND ? 404 : 409);
            model.addAttribute("message", result == LetterService.EditResult.NOT_FOUND
                ? "没有找到这封信，请返回信箱查看。" : "这封信已锁定，不能修改正文。");
            return "letter-error";
        } catch (IllegalArgumentException exception) {
            response.setStatus(400);
            model.addAttribute("content", content);
            model.addAttribute("message", exception.getMessage());
            return "letter-edit";
        }
    }

    @GetMapping("/letters/{id}/confirm")
    public String confirm(@PathVariable("id") String rawId, Model model, HttpServletResponse response) {
        String view = edit(rawId, model, response);
        return "letter-edit".equals(view) ? "letter-confirm" : view;
    }

    @PostMapping("/letters/{id}/send")
    public String send(@PathVariable("id") String rawId,
                       @RequestParam(value = "content", defaultValue = "") String content,
                       Model model, HttpServletResponse response) {
        long id;
        try {
            id = Long.parseLong(rawId);
            if (id <= 0) throw new NumberFormatException();
        } catch (NumberFormatException exception) {
            response.setStatus(400);
            model.addAttribute("message", "信件编号格式不正确。");
            return "letter-error";
        }
        var result = letterService.sendDraft(id, content);
        if (result == LetterService.SendResult.SENT || result == LetterService.SendResult.ALREADY_SENT) {
            return "redirect:/letters/" + id;
        }
        response.setStatus(result == LetterService.SendResult.NOT_FOUND ? 404
            : result == LetterService.SendResult.INVALID ? 400 : 409);
        model.addAttribute("message", switch (result) {
            case NOT_FOUND -> "没有找到这封信，请返回信箱查看。";
            case INVALID -> "正文不能为空，请修改草稿后再寄出。";
            case CHANGED -> "草稿内容已改变，请返回详情重新确认寄出。";
            default -> "这封信已锁定，不能寄出。";
        });
        return "letter-error";
    }

    @PostMapping("/letters")
    public String receiveLetter(@RequestParam("content") String content, Model model) {
        try {
            long id = letterService.saveDraft(content);
            return "redirect:/letters/" + id;
        } catch (IllegalArgumentException exception) {
            model.addAttribute("message", exception.getMessage());
            return "result";
        }
    }
}
