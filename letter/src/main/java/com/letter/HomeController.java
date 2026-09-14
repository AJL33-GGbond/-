package com.letter;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class HomeController {

    private final JdbcTemplate jdbcTemplate;

    public HomeController(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @GetMapping("/")
    public String home() {
        return "index";
    }

    @GetMapping("/letters")
    public String listLetters(Model model) {
        var letters = jdbcTemplate.queryForList(            
            "SELECT id, content, created_at FROM letters ORDER BY id DESC"
        );

        model.addAttribute("letters", letters);
        return "letters";
    }

    @PostMapping("/letters")
    public String receiveLetter(
            @RequestParam("content") String content,
            Model model) {

        if (content.isBlank()) {
            model.addAttribute("message", "正文不能为空，请返回填写。");
            return "result";
        }

        jdbcTemplate.update(
            "INSERT INTO letters (content) VALUES (?)",
            content
        );

        model.addAttribute("message", "草稿已保存到数据库。");
        model.addAttribute("content", content);
        return "result";
    }
}