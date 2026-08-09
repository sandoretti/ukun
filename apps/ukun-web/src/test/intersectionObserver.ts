import { act } from '@testing-library/react';

type StubbedObserver = { callback: IntersectionObserverCallback; elements: Element[] };

const observers: StubbedObserver[] = [];

/**
 * jsdom ships no `IntersectionObserver`, and without one the catalog's sentinel would simply never
 * fire — which is a valid state (the button is the fallback) but leaves the automatic path untested.
 *
 * This stub never fires on its own: nothing in jsdom scrolls, so an observer that reported
 * "visible" as soon as it was created would load every chunk in every test. Visibility is something
 * a test asks for, with `scrollToSentinel()`.
 */
export function installIntersectionObserverStub() {
  class IntersectionObserverStub {
    private readonly entry: StubbedObserver;

    constructor(callback: IntersectionObserverCallback) {
      this.entry = { callback, elements: [] };
      observers.push(this.entry);
    }

    observe(element: Element) {
      this.entry.elements.push(element);
    }

    unobserve(element: Element) {
      this.entry.elements = this.entry.elements.filter((observed) => observed !== element);
    }

    disconnect() {
      observers.splice(observers.indexOf(this.entry), 1);
    }

    takeRecords() {
      return [];
    }
  }

  Object.defineProperty(window, 'IntersectionObserver', {
    writable: true,
    value: IntersectionObserverStub as unknown as typeof IntersectionObserver,
  });
}

export function resetIntersectionObservers() {
  observers.length = 0;
}

/** What scrolling to the bottom of the grid does: every observed sentinel comes into view. */
export async function scrollToSentinel() {
  await act(async () => {
    for (const observer of [...observers]) {
      observer.callback(
        observer.elements.map((target) => ({ target, isIntersecting: true }) as IntersectionObserverEntry),
        {} as IntersectionObserver,
      );
    }
  });
}
