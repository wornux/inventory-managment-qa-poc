package com.wornux.ui.components;

import com.vaadin.flow.component.Tag;
import com.vaadin.flow.component.dependency.JsModule;
import com.vaadin.flow.component.dependency.NpmPackage;
import com.vaadin.flow.component.react.ReactAdapterComponent;
import java.util.List;

@Tag("inventory-movement-chart")
@JsModule("./components/inventory-movement-chart.tsx")
@NpmPackage(value = "recharts", version = "3.10.1")
public class InventoryMovementChart extends ReactAdapterComponent {

    public InventoryMovementChart() {
        getStyle()
                .set("display", "block")
                .set("height", "18rem")
                .set("min-width", "0")
                .set("width", "100%");
    }

    public void setData(List<MovementPoint> data) {
        setState("data", data);
    }

    public record MovementPoint(String label, long inbound, long outbound) {}
}
