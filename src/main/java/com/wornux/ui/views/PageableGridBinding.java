package com.wornux.ui.views;

import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.grid.dataview.GridLazyDataView;
import com.vaadin.flow.signals.Signal;
import java.util.function.BiFunction;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

final class PageableGridBinding {

    private PageableGridBinding() {}

    static <T, F> GridLazyDataView<T> bind(Grid<T> grid, Signal<F> filter, BiFunction<F, Pageable, Page<T>> fetch) {
        GridLazyDataView<T> dataView = grid.setItemsPageable(
                pageable -> fetch.apply(filter.peek(), pageable).getContent());

        Signal.effect(grid, () -> {
            filter.get();
            dataView.refreshAll();
        });

        return dataView;
    }
}
