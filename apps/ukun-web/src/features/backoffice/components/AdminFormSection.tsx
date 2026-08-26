import type { ReactNode } from 'react';

type AdminFormSectionProps = {
  title: string;
  /** What the section decides, in one line. It is not decoration: it is where the rule is stated. */
  description?: string;
  /**
   * How the section reads. `danger` is for what cannot be undone, and it is a named variant rather
   * than an `isDanger` prop because the day a third tone appears it is another value here and not
   * another boolean (`docs/07-frontend-ux-ui.md` §Cómo se diseña un componente base).
   */
  tone?: 'default' | 'danger';
  children: ReactNode;
};

/**
 * One block of a long administration form: its heading, the rule it obeys, and its controls.
 *
 * **Everything shares one left edge and one width.** The heading starts where the labels start and
 * the fields end where the section ends, so the eye has a single column to run down instead of
 * jumping between a description on one side and an input on the other. Dividing the form into blocks
 * is what makes four decisions legible on one screen; putting the halves side by side was making the
 * form harder to read, not easier.
 */
export function AdminFormSection({ title, description, tone = 'default', children }: AdminFormSectionProps) {
  return (
    <section className="py-4">
      <h2 className={`h6 mb-1 ${tone === 'danger' ? 'text-danger' : ''}`.trim()}>{title}</h2>
      {description !== undefined && <p className="text-body-secondary small mb-3">{description}</p>}

      {children}
    </section>
  );
}
