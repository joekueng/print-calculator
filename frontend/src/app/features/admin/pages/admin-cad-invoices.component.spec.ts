import { parseDecimalInput } from './admin-cad-invoices.component';

describe('parseDecimalInput', () => {
  it('accepts both decimal points and decimal commas', () => {
    expect(parseDecimalInput('1.5')).toBe(1.5);
    expect(parseDecimalInput('1,5')).toBe(1.5);
  });

  it('rejects malformed decimal values', () => {
    expect(parseDecimalInput('1.2.3')).toBeNaN();
    expect(parseDecimalInput('')).toBeNaN();
  });
});
