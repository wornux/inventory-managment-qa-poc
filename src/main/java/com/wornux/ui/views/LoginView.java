package com.wornux.ui.views;

import com.vaadin.flow.component.html.Anchor;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.login.LoginForm;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.QueryParameters;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.server.auth.AnonymousAllowed;
import java.util.List;
import java.util.Map;

@Route(value = "login", autoLayout = false)
@PageTitle("Login")
@AnonymousAllowed
public class LoginView extends Div implements BeforeEnterObserver {

    private final LoginForm loginForm = new LoginForm();
    private final Paragraph feedback = new Paragraph();

    public LoginView() {
        addClassName("auth-page");

        var panel = new Div();
        panel.addClassName("auth-panel");

        var title = new H1("Inventory Management");
        var subtitle = new Paragraph("Sign in to manage products, stock, suppliers, and users.");
        subtitle.addClassName("auth-subtitle");

        loginForm.setAction("login");
        loginForm.addClassName("auth-form");

        feedback.addClassName("auth-feedback");
        feedback.setVisible(false);

        var signup = new Anchor("signup", "Create an account");
        signup.addClassName("auth-link");

        panel.add(title, subtitle, feedback, loginForm, signup);
        add(panel);
    }

    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        QueryParameters queryParameters = event.getLocation().getQueryParameters();
        Map<String, List<String>> parameters = queryParameters.getParameters();
        loginForm.setError(parameters.containsKey("error"));

        boolean signedUp = parameters.containsKey("signup");
        feedback.setText(signedUp ? "Account created. Sign in with your new credentials." : "");
        feedback.setVisible(signedUp);
    }
}
