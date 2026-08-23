import React from 'react';
import {
  Area,
  AreaChart,
  ResponsiveContainer,
} from 'recharts';

export function Sparkline({
  values,
  tone = 'accent',
}: {
  values: number[];
  tone?: 'accent' | 'good' | 'warning' | 'danger';
}) {
  const data = values.map((value, index) => ({
    index,
    value,
  }));

  return (
    <div className={`sparkline sparkline-${tone}`}>
      <ResponsiveContainer width="100%" height="100%">
        <AreaChart data={data}>
          <Area
            type="monotone"
            dataKey="value"
            stroke="currentColor"
            fill="currentColor"
            fillOpacity={0.12}
            strokeWidth={2}
            isAnimationActive={false}
          />
        </AreaChart>
      </ResponsiveContainer>
    </div>
  );
}
