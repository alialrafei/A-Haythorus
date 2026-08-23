import React from 'react';
import { Icon } from '@blueprintjs/core';
import type { IconName } from '@blueprintjs/icons';

export function EmptyState({
  icon,
  title,
  description,
}: {
  icon: IconName;
  title: string;
  description: string;
}) {
  return (
    <div className="empty-state">
      <div className="empty-state-icon">
        <Icon icon={icon} size={28} />
      </div>
      <h3>{title}</h3>
      <p>{description}</p>
    </div>
  );
}
