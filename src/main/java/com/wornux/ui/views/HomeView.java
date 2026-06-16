package com.wornux.ui.views;

import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.component.html.Anchor;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import jakarta.annotation.security.PermitAll;

@Route("")
@PageTitle("Inventory")
@PermitAll
public class HomeView extends VerticalLayout {

    public HomeView() {
        addClassName("home-view");
        add(
                new H1("Inventory Management"),
                new Paragraph("You are signed in."),
                new Anchor("products", "Manage products"),
                new Anchor("categories", "Manage categories"),
                new Anchor("suppliers", "Manage suppliers"),
                new Anchor("stock-movements", "Stock movements"),
                new Anchor("users", "Manage users"),
                new Anchor("roles", "Manage roles"),
                new Anchor("permissions", "Manage permissions"));
    }
}
