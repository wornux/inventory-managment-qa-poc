import type { ReactElement } from 'react';
import {
  Bar,
  BarChart,
  CartesianGrid,
  Legend,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from 'recharts';
import {
  ReactAdapterElement,
  type RenderHooks,
} from 'Frontend/generated/flow/ReactAdapter.js';

const CHART_LABEL = 'Inbound and outbound inventory units for the latest seven activity days.';

type MovementPoint = {
  label: string;
  inbound: number;
  outbound: number;
};

class InventoryMovementChartElement extends ReactAdapterElement {
  protected override render(hooks: RenderHooks): ReactElement {
    const [data] = hooks.useState<MovementPoint[]>('data');
    const chartData = data ?? [];
    const description = chartData
      .map(({ label, inbound, outbound }) => `${label}: ${inbound} inbound, ${outbound} outbound`)
      .join('; ');

    return (
      <div style={{ height: '100%', minWidth: 0, width: '100%' }}>
        <ResponsiveContainer width="100%" height="100%">
          <BarChart
            accessibilityLayer
            aria-label={`${CHART_LABEL} ${description}`}
            data={chartData}
            margin={{ top: 12, right: 4, bottom: 0, left: -20 }}
            title={CHART_LABEL}
          >
            <CartesianGrid
              stroke="var(--vaadin-border-color-secondary)"
              strokeDasharray="3 5"
              vertical={false}
            />
            <XAxis
              axisLine={false}
              dataKey="label"
              tick={{ fill: 'var(--vaadin-text-color-secondary)', fontSize: 12 }}
              tickLine={false}
            />
            <YAxis
              axisLine={false}
              allowDecimals={false}
              tick={{ fill: 'var(--vaadin-text-color-secondary)', fontSize: 12 }}
              tickLine={false}
            />
            <Tooltip
              contentStyle={{
                background: 'var(--aura-surface-color-solid)',
                border: '1px solid var(--vaadin-border-color-secondary)',
                borderRadius: 'var(--vaadin-radius-m)',
                color: 'var(--vaadin-text-color)',
              }}
              cursor={{ fill: 'var(--vaadin-background-container)' }}
            />
            <Legend wrapperStyle={{ color: 'var(--vaadin-text-color-secondary)', fontSize: 12 }} />
            <Bar
              dataKey="inbound"
              fill="var(--aura-green)"
              name="Inbound units"
              radius={[4, 4, 0, 0]}
            />
            <Bar
              dataKey="outbound"
              fill="var(--aura-orange)"
              name="Outbound units"
              radius={[4, 4, 0, 0]}
            />
          </BarChart>
        </ResponsiveContainer>
      </div>
    );
  }
}

customElements.define('inventory-movement-chart', InventoryMovementChartElement);
