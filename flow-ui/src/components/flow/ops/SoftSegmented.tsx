import React from 'react';
import './SoftSegmented.less';

export type SoftSegmentedOption<T extends string | number = string | number> = {
  label: React.ReactNode;
  value: T;
};

export type SoftSegmentedProps<T extends string | number = string | number> = {
  value: T;
  options: SoftSegmentedOption<T>[];
  onChange: (value: T) => void;
  ariaLabel?: string;
  className?: string;
};

/** 灰底白胶囊分段控件（Flow Ops 标准筛选条） */
function SoftSegmented<T extends string | number = string | number>({
  value,
  options,
  onChange,
  ariaLabel,
  className,
}: SoftSegmentedProps<T>) {
  return (
    <div
      className={`yf-soft-seg${className ? ` ${className}` : ''}`}
      role="group"
      aria-label={ariaLabel}
    >
      {options.map((opt) => (
        <button
          key={String(opt.value)}
          type="button"
          className={value === opt.value ? 'is-active' : ''}
          onClick={() => onChange(opt.value)}
        >
          {opt.label}
        </button>
      ))}
    </div>
  );
}

export default SoftSegmented;
