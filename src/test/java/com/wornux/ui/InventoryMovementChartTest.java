package com.wornux.ui;

import static org.assertj.core.api.Assertions.assertThat;

import com.wornux.ui.components.InventoryMovementChart;
import java.util.List;
import org.junit.jupiter.api.Test;
import tools.jackson.core.type.TypeReference;

class InventoryMovementChartTest {

    @Test
    void setData_exposesMovementPointsToTheReactAdapterState() {
        var chart = new TestChart();
        var points = List.of(new InventoryMovementChart.MovementPoint("Mar 8, 26", 8, 4));

        chart.setData(points);

        assertThat(chart.data()).containsExactlyElementsOf(points);
        assertThat(chart.getElement().getTag()).isEqualTo("inventory-movement-chart");
    }

    private static final class TestChart extends InventoryMovementChart {

        private List<MovementPoint> data() {
            return getState("data", new TypeReference<>() {});
        }
    }
}
