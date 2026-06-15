package com.wornux.ui.views;

import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.formlayout.FormLayout;
import com.vaadin.flow.component.html.Anchor;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.textfield.EmailField;
import com.vaadin.flow.component.textfield.PasswordField;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.data.value.ValueChangeMode;
import com.vaadin.flow.data.binder.BeanValidationBinder;
import com.vaadin.flow.data.binder.ValidationResult;
import com.vaadin.flow.data.validator.EmailValidator;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.server.auth.AnonymousAllowed;
import com.wornux.user.AppUserService;
import com.wornux.user.SignupException;
import com.wornux.user.SignupRequest;
import jakarta.validation.ConstraintViolationException;

@Route(value = "signup", autoLayout = false)
@PageTitle("Sign up")
@AnonymousAllowed
public class SignupView extends Div {

    private final AppUserService appUserService;
    private final BeanValidationBinder<SignupRequest> binder = new BeanValidationBinder<>(SignupRequest.class);
    private final SignupRequest signupRequest = new SignupRequest();

    private final TextField username = new TextField("Username");
    private final EmailField email = new EmailField("Email");
    private final PasswordField password = new PasswordField("Password");
    private final PasswordField confirmPassword = new PasswordField("Confirm password");

    public SignupView(AppUserService appUserService) {
        this.appUserService = appUserService;
        addClassName("auth-page");

        var panel = new Div();
        panel.addClassName("auth-panel");

        var title = new H1("Create account");
        var subtitle = new Paragraph("Use a unique username, valid email, and a password with at least 8 characters.");
        subtitle.addClassName("auth-subtitle");

        var form = new FormLayout();
        form.addClassName("auth-form");
        configureFields();
        form.add(username, email, password, confirmPassword);

        var submit = new Button("Sign up", event -> submit());
        submit.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        submit.setWidthFull();

        var login = new Anchor("login", "Already have an account?");
        login.addClassName("auth-link");

        panel.add(title, subtitle, form, submit, login);
        add(panel);
    }

    private void configureFields() {
        username.setRequiredIndicatorVisible(true);
        username.setValueChangeMode(ValueChangeMode.EAGER);
        email.setRequiredIndicatorVisible(true);
        email.setErrorMessage("Invalid email address.");
        email.setValueChangeMode(ValueChangeMode.EAGER);
        password.setRequiredIndicatorVisible(true);
        password.setValueChangeMode(ValueChangeMode.EAGER);
        confirmPassword.setRequiredIndicatorVisible(true);
        confirmPassword.setValueChangeMode(ValueChangeMode.EAGER);

        binder.forField(username)
                .asRequired("Username is required.")
                .bind(SignupRequest::getUsername, SignupRequest::setUsername);
        binder.forField(email)
                .asRequired("Email is required.")
                .withValidator(new EmailValidator("Invalid email address."))
                .bind(SignupRequest::getEmail, SignupRequest::setEmail);
        binder.forField(password)
                .asRequired("Password is required.")
                .withValidator(value -> value != null && value.length() >= 8,
                        "Password must be at least 8 characters.")
                .bind(SignupRequest::getPassword, SignupRequest::setPassword);
        binder.forField(confirmPassword)
                .asRequired("Confirm password is required.")
                .bind(SignupRequest::getConfirmPassword, SignupRequest::setConfirmPassword);
        binder.withValidator((request, context) -> {
            if (request.getPassword() != null && request.getPassword().equals(request.getConfirmPassword())) {
                return ValidationResult.ok();
            }
            return ValidationResult.error("Passwords do not match.");
        });
        binder.readBean(signupRequest);
    }

    private void submit() {
        if (!binder.writeBeanIfValid(signupRequest)) {
            showError("Please fix the highlighted fields.");
            return;
        }

        try {
            appUserService.signup(signupRequest);
            UI.getCurrent().navigate("login?signup=success");
        } catch (SignupException exception) {
            showError(exception.getMessage());
        } catch (ConstraintViolationException exception) {
            showError("Please fix the highlighted fields.");
        }
    }

    private void showError(String message) {
        Notification notification = Notification.show(message, 5000, Notification.Position.TOP_CENTER);
        notification.addThemeVariants(NotificationVariant.LUMO_ERROR);
    }
}
