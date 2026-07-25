package com.wornux.ui.views;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.component.html.Main;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import jakarta.annotation.security.PermitAll;

@Route("forbidden")
@PageTitle("Forbidden")
@PermitAll
public class ForbiddenView extends Main {

    public ForbiddenView() {
        setSizeFull();
        addClassNames("forbidden-view", "centered-state-view");

        var eyebrow = new Span("403");
        eyebrow.addClassName("centered-state-eyebrow");

        var title = new H1("Access forbidden");
        var message = new Paragraph(
                "Your account does not have permission to open this area. Use the drawer to continue with available modules.");

        var home = new Button("Back to overview", event -> getUI().ifPresent(ui -> ui.navigate(HomeView.class)));
        home.addThemeVariants(ButtonVariant.PRIMARY);

        add(eyebrow, title, message, home);
    }
}
