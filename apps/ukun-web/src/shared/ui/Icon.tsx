import React from 'react';

type IconProps = {
  name: string;
  size?: 'sm' | 'md' | 'lg';
  className?: string;
};

export const Icon: React.FC<IconProps> = ({ name, size = 'md', className = '' }) => {
  const sizeMap = {
    sm: '16px',
    md: '24px',
    lg: '32px',
  };

  return (
    <span
      className={`material-symbols-outlined ${className}`}
      style={{ fontSize: sizeMap[size] }}
      aria-hidden="true"
    >
      {name}
    </span>
  );
};
