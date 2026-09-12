package jp.co.savingfavorite.main;

import jp.co.savingfavorite.UserRegistrationForm;
import jp.co.savingfavorite.UserService;
import org.springframework.ui.Model;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class MainController {
    private final UserService userService;

    public MainController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping("/")
    public String index(@RequestParam(required = false) String registered, Model model) {
        model.addAttribute("registrationForm", new UserRegistrationForm());
        model.addAttribute("registered", registered != null);
        return "main/index";
    }

    @PostMapping("/register")
    public String register(@ModelAttribute UserRegistrationForm form, Model model) {
        String error = validate(form);
        if (error != null) {
            model.addAttribute("registrationForm", form);
            model.addAttribute("registrationError", error);
            return "main/index";
        }

        userService.register(form);
        return "redirect:/?registered=true";
    }

    private String validate(UserRegistrationForm form) {
        if (isBlank(form.getName()) || isBlank(form.getEmail()) || isBlank(form.getPassword())) {
            return "名前、メールアドレス、パスワードを入力してください。";
        }
        if (!form.getEmail().contains("@")) {
            return "メールアドレスの形式を確認してください。";
        }
        if (form.getPassword().length() < 8) {
            return "パスワードは8文字以上で入力してください。";
        }
        if (!form.getPassword().equals(form.getPasswordConfirmation())) {
            return "パスワードと確認用パスワードが一致していません。";
        }
        if (userService.existsByEmail(form.getEmail())) {
            return "そのメールアドレスはすでに登録されています。";
        }
        return null;
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
