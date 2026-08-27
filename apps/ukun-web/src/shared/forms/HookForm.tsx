import type { BaseSyntheticEvent, ComponentProps, ReactNode } from 'react';
import { Form } from 'react-bootstrap';

/**
 * What `handleSubmit(...)` of react-hook-form hands back: a submit listener that **needs the event**
 * to do its job, and that answers with a promise because validation can be asynchronous.
 *
 * Writing the type down is half of what this module is for. A prop typed `() => void` accepts a
 * listener that quietly drops the event, and that is exactly the shape of the bug this replaces
 * (issue #151); one that has to answer `Promise<void>` cannot be satisfied by `() => void submit()`,
 * because that expression evaluates to `undefined`. The compiler now refuses what used to reach the
 * browser.
 */
export type FormSubmitHandler = (event?: BaseSyntheticEvent) => Promise<void>;

type HookFormProps = Omit<ComponentProps<typeof Form>, 'onSubmit' | 'noValidate'> & {
  /** The result of `handleSubmit(...)`, passed straight through — never wrapped by the caller. */
  onSubmit: FormSubmitHandler;
  children: ReactNode;
};

/**
 * The `<form>` element of this app: browser validation off, and the submit event delivered intact to
 * react-hook-form.
 *
 * The three things it settles were written out by hand in every form of the app, and getting any of
 * them wrong is invisible until somebody uses the screen:
 *
 * - **`noValidate`**, because validation is the zod schema's and never the browser's — its bubbles
 *   are untranslatable and would appear next to fields whose real rules live elsewhere.
 * - **The event reaches `handleSubmit`**, which is the only way it gets to call `preventDefault()`.
 *   Without it the browser *also* performs its native submit: the request the mutation sends goes
 *   out, and at the same time the page navigates to its own URL with every field as a query
 *   parameter, reloading the SPA and wiping the form. What the reader sees is a form that empties
 *   itself, having never shown the answer — the success, or the `409` under the field.
 * - **The promise is discarded on purpose** (`void`), because a submit listener returns nothing and a
 *   floating promise here is the rejection nobody handles.
 *
 * That last failure is not something a test in jsdom can catch: jsdom implements no navigation, so
 * the native submit is a no-op there and every assertion still passes. It was found in a browser,
 * against the real stack. Which is the argument for it being a component and not a convention: the
 * arrow is written once, where it is reviewed once, instead of in each screen where nothing checks
 * it. `HookForm.test.tsx` pins it from the one angle jsdom does expose — that the event comes back
 * with its default prevented.
 *
 * Everything else travels to react-bootstrap's `Form` untouched, which is what lets a caller keep
 * its `className="settings-form"` and its `id`.
 */
export function HookForm({ onSubmit, children, ...form }: HookFormProps) {
  return (
    <Form noValidate onSubmit={(event) => void onSubmit(event)} {...form}>
      {children}
    </Form>
  );
}
