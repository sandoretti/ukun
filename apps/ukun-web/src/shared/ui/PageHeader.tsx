import type { ReactNode } from 'react';

type PageHeaderProps = {
  /** The `<h1>` of the screen. There is exactly one per route, and this is it. */
  title: string;
  /** One line saying what the screen is for. Optional: not every screen owes an explanation. */
  lead?: string;
  /**
   * The screen's primary action, if it has one — the «new product» of a listing. It arrives as
   * children rather than as a `renderAction` prop because it is one element in one slot, and a
   * component that takes a function to draw a button is a component that decided how the button
   * looks (skill `react-web`, rule 4).
   */
  children?: ReactNode;
};

/**
 * The head of a screen: title, one line of explanation, and the action the screen exists for.
 *
 * It was written three times, character for character, in the three back-office listings —stock
 * (RF-09), catalog (RF-10) and staff (RF-11)— each with the same row of utilities, the same
 * `h1.h2`, the same lead and the same link wearing a button. Three copies of a heading is three
 * places for it to drift, and it had already started: the gap under the row was `mb-2` in all three
 * only by luck.
 *
 * It lives in `shared/ui/` and not in `features/backoffice/` for the usual reason —features never
 * import each other— and because nothing in it is about the back-office: a listing in the shop with
 * one primary action would wear it unchanged. The shop screens that have a similar heading today are
 * deliberately left alone; they are delivered, and rewriting them fixes nothing.
 */
export function PageHeader({ title, lead, children }: PageHeaderProps) {
  return (
    // The action wraps under the title on a narrow screen instead of squeezing it: `flex-wrap` with
    // `align-items-start` is what keeps the two blocks reading top-down when the row runs out.
    <div className="d-flex flex-wrap align-items-start justify-content-between gap-3 mb-2">
      <div>
        <h1 className="h2 mb-2">{title}</h1>
        {lead !== undefined && <p className="text-body-secondary mb-0">{lead}</p>}
      </div>
      {children}
    </div>
  );
}
