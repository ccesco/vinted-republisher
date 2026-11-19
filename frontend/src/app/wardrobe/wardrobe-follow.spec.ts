import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import type { AutomaticRunOverview } from '../../generated/api';
import { OncePerPeriod, runChanged } from './wardrobe-follow';

function aRun(relaunchCount: number): AutomaticRunOverview {
  return {
    status: 'RUNNING', startedAt: '2026-11-17T10:00:00Z', currentListingIds: [1], relaunchCount,
    skippedListingIds: [], pauseRequested: false, stopRequested: false
  };
}

describe('run changed', () => {
  it('is false for the same overview', () => {
    expect(runChanged(aRun(3), aRun(3))).toBe(false);
  });

  it('is true when any field differs', () => {
    expect(runChanged(aRun(3), aRun(4))).toBe(true);
    expect(runChanged(aRun(3), { ...aRun(3), pauseRequested: true })).toBe(true);
  });

  it('is true for the first overview', () => {
    expect(runChanged(undefined, aRun(0))).toBe(true);
  });
});

describe('once per period', () => {
  beforeEach(() => vi.useFakeTimers());
  afterEach(() => vi.useRealTimers());

  it('runs at once the first time', () => {
    const action = vi.fn();
    new OncePerPeriod(2000, action).request();
    expect(action).toHaveBeenCalledTimes(1);
  });

  it('runs requests of the same period once more at its end', () => {
    const action = vi.fn();
    const reloads = new OncePerPeriod(2000, action);
    reloads.request();
    vi.advanceTimersByTime(500);
    reloads.request();
    reloads.request();
    expect(action).toHaveBeenCalledTimes(1);
    vi.advanceTimersByTime(1500);
    expect(action).toHaveBeenCalledTimes(2);
  });

  it('runs at once again once a period has passed', () => {
    const action = vi.fn();
    const reloads = new OncePerPeriod(2000, action);
    reloads.request();
    vi.advanceTimersByTime(2000);
    reloads.request();
    expect(action).toHaveBeenCalledTimes(2);
  });

  it('runs nothing more once closed', () => {
    const action = vi.fn();
    const reloads = new OncePerPeriod(2000, action);
    reloads.request();
    reloads.request();
    reloads.close();
    vi.advanceTimersByTime(2000);
    expect(action).toHaveBeenCalledTimes(1);
  });

  it('ignores a request once closed', () => {
    const action = vi.fn();
    const reloads = new OncePerPeriod(2000, action);
    reloads.close();
    reloads.request();
    vi.advanceTimersByTime(2000);
    expect(action).not.toHaveBeenCalled();
  });
});
