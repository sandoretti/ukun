import { useRef, type PointerEvent, type RefObject } from 'react';

/** Spread onto the scrolling element; it keeps its own scrollbars and its own keyboard behaviour. */
type DragHandlers<T extends HTMLElement> = {
  onPointerDown: (event: PointerEvent<T>) => void;
  onPointerMove: (event: PointerEvent<T>) => void;
  onPointerUp: (event: PointerEvent<T>) => void;
  onPointerCancel: (event: PointerEvent<T>) => void;
};

/**
 * Drag an overflowing element around by grabbing its content, on top of —never instead of— the
 * scrolling it already has. What actually moves is `scrollLeft`/`scrollTop`, so the scrollbars, the
 * wheel and the arrow keys keep working and nothing has to be reimplemented for the keyboard.
 *
 * The element's ref belongs to the caller, who is the one attaching it: a hook that both created it
 * and handed it back would be read during render at every call site.
 *
 * `enabled` is a parameter because an element that does not overflow has nothing to drag, and a
 * pointer press on it belongs to whatever else it does (in the image viewer, zooming in).
 */
export function useDragScroll<T extends HTMLElement>(
  ref: RefObject<T | null>,
  enabled: boolean,
): DragHandlers<T> {
  /** Where the pointer went down, and where the element was scrolled to at that moment. */
  const origin = useRef<{ x: number; y: number; left: number; top: number } | null>(null);

  const onPointerDown = (event: PointerEvent<T>) => {
    const element = ref.current;
    if (!enabled || element === null) {
      return;
    }
    origin.current = {
      x: event.clientX,
      y: event.clientY,
      left: element.scrollLeft,
      top: element.scrollTop,
    };
    element.setPointerCapture(event.pointerId);
  };

  const onPointerMove = (event: PointerEvent<T>) => {
    const element = ref.current;
    if (origin.current === null || element === null) {
      return;
    }
    element.scrollLeft = origin.current.left - (event.clientX - origin.current.x);
    element.scrollTop = origin.current.top - (event.clientY - origin.current.y);
  };

  const onPointerUp = (event: PointerEvent<T>) => {
    origin.current = null;
    ref.current?.releasePointerCapture(event.pointerId);
  };

  return { onPointerDown, onPointerMove, onPointerUp, onPointerCancel: onPointerUp };
}
