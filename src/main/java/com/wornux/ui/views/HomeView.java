package com.wornux.ui.views;

import com.vaadin.flow.component.html.Anchor;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Main;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import jakarta.annotation.security.PermitAll;
import org.springframework.beans.factory.annotation.Value;

@Route("")
@PageTitle("Inventory")
@PermitAll
public class HomeView extends Main {

    public HomeView(@Value("${app.observability.grafana-url:http://localhost:3000}") String grafanaUrl) {
        setSizeFull();
        addClassName("home-view");

        var eyebrow = new Span("Overview");
        eyebrow.addClassName("home-eyebrow");

        var title = new H1("Inventory workspace");
        var subtitle = new Paragraph(
                "Use the drawer to open the modules available to your account, or inspect operations in Grafana.");
        subtitle.addClassName("home-subtitle");

        var hero = new Div(eyebrow, title, subtitle);
        hero.addClassName("home-hero");

        add(
                hero,
                pendingCard("Catalog", "Products, categories, and suppliers are available from the drawer."),
                observabilityCard(grafanaUrl),
                pendingCard("Administration", "User and role management appears for authorized administrators."));
    }

    private Div observabilityCard(String grafanaUrl) {
        var cardTitle = new H2("Operations");
        var link = new Anchor(grafanaUrl + "/dashboards", "Open Grafana");
        link.setTarget("_blank");
        link.getElement().setAttribute("rel", "noopener noreferrer");
        link.addClassName("home-card-badge");
        var header = new Div(cardTitle, link);
        header.addClassName("home-card-header");

        var body = new Paragraph("Review infrastructure, application, business, and security telemetry.");
        var card = new Div(header, body);
        card.addClassName("home-card");

        return card;
    }

    private Div pendingCard(String title, String text) {
        var cardTitle = new H2(title);
        var badge = new Span("Pending");
        badge.addClassName("home-card-badge");
        var header = new Div(cardTitle, badge);
        header.addClassName("home-card-header");

        var body = new Paragraph(text);
        var card = new Div(header, body);
        card.addClassName("home-card");

        return card;
    }
}
