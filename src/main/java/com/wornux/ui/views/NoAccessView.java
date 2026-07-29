package com.wornux.ui.views;

import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.component.html.Main;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import jakarta.annotation.security.PermitAll;

@Route("no-access")
@PageTitle("No access")
@PermitAll
public class NoAccessView extends Main {

    public NoAccessView() {
        setSizeFull();
        addClassName("no-access-view");

        var windowControls = new Div();
        windowControls.addClassName("no-access-window-controls");
        windowControls.getElement().setAttribute("aria-hidden", "true");
        windowControls.add(new Span(), new Span(), new Span());

        var windowTitle = new Span("access-control");
        windowTitle.addClassName("no-access-window-title");

        var titleBar = new Div(windowControls, windowTitle);
        titleBar.addClassName("no-access-title-bar");

        var prompt = new Span("$ accessctl status --current-user");
        prompt.addClassName("no-access-prompt");

        var authenticated = new Span("identity     authenticated");
        authenticated.addClassName("no-access-output");

        var authorization = new Span("authorization unavailable — no permissions assigned");
        authorization.addClassNames("no-access-output", "no-access-output-error");

        var title = new H1("No access assigned");
        title.setId("no-access-title");

        var message =
                new Paragraph("Your account is active, but it does not currently have permission to open any module.");
        var recovery = new Paragraph("Contact an administrator and ask them to assign the role you need.");
        recovery.addClassName("no-access-recovery");

        var body = new Div(prompt, authenticated, authorization, title, message, recovery);
        body.addClassName("no-access-body");

        var card = new Div(titleBar, body);
        card.addClassName("no-access-card");
        card.getElement().setAttribute("role", "status");
        card.getElement().setAttribute("aria-labelledby", "no-access-title");

        add(card);
    }
}
