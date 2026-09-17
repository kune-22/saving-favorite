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

    @GetMapping({"/", "/home", "/schedule", "/money", "/purchases", "/favorites", "/favorite-new", "/inventory", "/account"})
    public String index(java.security.Principal principal, Model model) {
        if (principal != null) {
            model.addAttribute("account", userService.findByEmail(principal.getName()));
            return "main/dashboard";
        }
        model.addAttribute("loggedIn", principal != null);
        return "main/index";
    }

    @GetMapping("/register")
    public String registration(@RequestParam(required = false) String registered,
            @RequestParam(defaultValue = "register") String mode, Model model,
            java.security.Principal principal) {
        if (principal != null) {
            return "redirect:/";
        }
        model.addAttribute("registrationForm", new UserRegistrationForm());
        model.addAttribute("registered", registered != null);
        model.addAttribute("loginMode", "login".equals(mode) || registered != null);
        return "main/register";
    }

    @PostMapping("/register")
    public String register(@ModelAttribute UserRegistrationForm form, Model model,
            jakarta.servlet.http.HttpServletRequest request) throws jakarta.servlet.ServletException {
        if (request.getUserPrincipal() != null) {
            return "redirect:/";
        }
        String error = validate(form);
        if (error != null) {
            form.setPassword(null);
            form.setPasswordConfirmation(null);
            model.addAttribute("registrationForm", form);
            model.addAttribute("registrationError", error);
            model.addAttribute("loginMode", false);
            return "main/register";
        }

        userService.register(form);
        if (request.getSession(false) != null) {
            request.changeSessionId();
        }
        request.login(form.getEmail().trim().toLowerCase(java.util.Locale.ROOT), form.getPassword());
        return "redirect:/";
    }

    @PostMapping("/favorites")
    public String addFavorite(@RequestParam String name,
            java.security.Principal principal,
            org.springframework.web.servlet.mvc.support.RedirectAttributes redirect) {
        if (isBlank(name) || name.trim().length() > 50) {
            redirect.addFlashAttribute("favoriteError", "推しの名前を1〜50文字で入力してください。");
        } else {
            userService.addFavorite(principal.getName(), name.trim());
        }
        return "redirect:/#favorites";
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
