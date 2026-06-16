package com.wornux.ui.views;

import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.component.html.Paragraph;
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

    private final Paragraph feedback = new Paragraph();

    public LoginView() {
        addClassName("auth-page");

        Div panel = new Div();
        panel.addClassName("auth-panel");

        H1 title = new H1("Inventory Management");

        Paragraph subtitle = new Paragraph("Sign in to manage products, stock, suppliers, and users.");
        subtitle.addClassName("auth-subtitle");

        feedback.addClassName("auth-feedback");
        feedback.setVisible(false);

        Button keycloakLogin = new Button("Iniciar sesión con Keycloak");
        keycloakLogin.addClassName("auth-login-button");

        keycloakLogin.addClickListener(event ->
                UI.getCurrent().getPage().setLocation("/oauth2/authorization/keycloak")
        );

        panel.add(title, subtitle, feedback, keycloakLogin);
        add(panel);
    }

    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        QueryParameters queryParameters = event.getLocation().getQueryParameters();
        Map<String, List<String>> parameters = queryParameters.getParameters();

        if (parameters.containsKey("error")) {
            feedback.setText("Authentication failed. Try again or contact administrator.");
            feedback.addClassName("auth-feedback-error");
            feedback.setVisible(true);
            return;
        }

        if (parameters.containsKey("logout")) {
            feedback.setText("You have been signed out.");
            feedback.removeClassName("auth-feedback-error");
            feedback.setVisible(true);
            return;
        }

        feedback.setVisible(false);
    }
}