import { describe, expect, it } from 'vitest';

import { currencyAffix, formatMoney } from './money';

describe('formatMoney', () => {
  it('formats an amount in euros for each locale', () => {
    // Assertions stay tolerant of the ICU build's separators and whitespace: what is being checked
    // is the currency, the digits and the decimals, not the exact glyph between them.
    expect(formatMoney('12345.50', 'es').replace(/\s/g, ' ')).toMatch(/12\.345,50\s?€/);
    expect(formatMoney('12345.50', 'en').replace(/\s/g, ' ')).toMatch(/€\s?12,345\.50/);
  });

  it('keeps the exact decimals of the contract string', () => {
    expect(formatMoney('399.99', 'es')).toContain('399,99');
    expect(formatMoney('0.05', 'es')).toContain('0,05');
  });
});

describe('currencyAffix', () => {
  it('pone el símbolo del lado que le toca a cada idioma', () => {
    // Es la razón de existir de la función: el filtro de precio del catálogo llevaba un `€` escrito
    // en el marcado, siempre detrás del campo. En español está bien y en inglés está del lado
    // contrario, y `formatMoney` —que sí pregunta— decía lo contrario dos líneas más abajo.
    expect(currencyAffix('es')).toEqual({ symbol: '€', position: 'suffix' });
    expect(currencyAffix('en')).toEqual({ symbol: '€', position: 'prefix' });
  });
});
