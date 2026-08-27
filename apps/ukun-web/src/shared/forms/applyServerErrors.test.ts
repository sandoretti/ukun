import { describe, expect, it, vi } from 'vitest';

import { ProblemDetailError } from '../api/http';
import { applyDuplicateValue, applyServerErrors } from './applyServerErrors';

type Form = { name: string; address: { postalCode: string } };

const messageKeys = {
  name: 'validation.name',
  'address.postalCode': 'validation.postalCode',
};

function validationFailed(errors: { field: string; message: string }[]) {
  return new ProblemDetailError({ code: 'VALIDATION_FAILED', status: 400, errors }, 400);
}

describe('applyServerErrors', () => {
  it('places a nested field error on the field the API named', () => {
    const setError = vi.fn();

    const allPlaced = applyServerErrors<Form>(
      validationFailed([{ field: 'address.postalCode', message: 'must be five digits' }]),
      setError,
      messageKeys,
    );

    expect(allPlaced).toBe(true);
    expect(setError).toHaveBeenCalledWith(
      'address.postalCode',
      // The translation key, not the operator's English: what the reader sees is resolved here.
      { type: 'server', message: 'validation.postalCode' },
      { shouldFocus: true },
    );
  });

  it('focuses the first field only, so the form does not fight itself for the caret', () => {
    const setError = vi.fn();

    applyServerErrors<Form>(
      validationFailed([
        { field: 'name', message: 'must not be blank' },
        { field: 'address.postalCode', message: 'must be five digits' },
      ]),
      setError,
      messageKeys,
    );

    expect(setError.mock.calls.map(([field, , options]) => [field, options])).toEqual([
      ['name', { shouldFocus: true }],
      ['address.postalCode', { shouldFocus: false }],
    ]);
  });

  it('refuses to invent a field nobody claims, and says so', () => {
    const setError = vi.fn();

    // An error on a field this form does not render would sit in the form state forever: invisible,
    // and blocking every later submit. The caller falls back to the form-level message instead.
    const allPlaced = applyServerErrors<Form>(
      validationFailed([{ field: 'somethingElse', message: 'is invalid' }]),
      setError,
      messageKeys,
    );

    expect(allPlaced).toBe(false);
    expect(setError).not.toHaveBeenCalled();
  });

  it('reports a partial placement as unfinished', () => {
    const setError = vi.fn();

    const allPlaced = applyServerErrors<Form>(
      validationFailed([
        { field: 'name', message: 'must not be blank' },
        { field: 'somethingElse', message: 'is invalid' },
      ]),
      setError,
      messageKeys,
    );

    expect(allPlaced).toBe(false);
    expect(setError).toHaveBeenCalledTimes(1);
  });

  it('leaves any other problem alone: it is not a field error', () => {
    const setError = vi.fn();

    const allPlaced = applyServerErrors<Form>(
      new ProblemDetailError({ code: 'INTERNAL_ERROR' }, 500),
      setError,
      messageKeys,
    );

    expect(allPlaced).toBe(false);
    expect(setError).not.toHaveBeenCalled();
  });
});

function duplicateValue(field: string) {
  return new ProblemDetailError(
    { code: 'DUPLICATE_VALUE', status: 409, resource: 'productType', field },
    409,
  );
}

describe('applyDuplicateValue', () => {
  it('places the clash on the field the extension names, not on the one the screen expected', () => {
    const setError = vi.fn();

    const placed = applyDuplicateValue<Form>(duplicateValue('name'), setError, messageKeys);

    expect(placed).toBe(true);
    expect(setError).toHaveBeenCalledWith(
      'name',
      { type: 'server', message: 'validation.name' },
      { shouldFocus: true },
    );
  });

  it('refuses a field this form does not render, so the caller can fall back', () => {
    const setError = vi.fn();

    const placed = applyDuplicateValue<Form>(duplicateValue('email'), setError, messageKeys);

    expect(placed).toBe(false);
    expect(setError).not.toHaveBeenCalled();
  });

  it('leaves any other code alone, `VALIDATION_FAILED` included: that one is the other function', () => {
    const setError = vi.fn();

    const placed = applyDuplicateValue<Form>(
      validationFailed([{ field: 'name', message: 'must not be blank' }]),
      setError,
      messageKeys,
    );

    expect(placed).toBe(false);
    expect(setError).not.toHaveBeenCalled();
  });
});
