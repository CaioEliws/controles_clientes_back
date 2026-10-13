import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { createRequire } from 'node:module';
const frontend = new URL('../../../frontend/', import.meta.url);
const requireFrontend = createRequire(new URL('package.json', frontend));
const ts = requireFrontend('typescript');
function carregar(relativo) {
  const fonte = readFileSync(new URL(relativo, frontend), 'utf8');
  const codigo = ts.transpileModule(fonte, { compilerOptions: { module: ts.ModuleKind.CommonJS } }).outputText;
  const exports = {};
  const requireLocal = (nome) => nome === '@/types' ? carregar('src/types/index.ts') : requireFrontend(nome);
  new Function('require', 'exports', codigo)(requireLocal, exports);
  return exports;
}
const { dadosParaFormulario, clienteCreatePayload, clienteUpdatePayload, clienteCreateSchema, clienteUpdateSchema } = carregar('src/schemas/clientes.schemas.ts');
const casos = ['36956870', '40.961.471-3', '52779723X', '52779723x', '001234567',
  '01234567', '1234567', '1234567890', '12345678901', '123456789012', '1234567890123',
  '1.234.567.890.123', '12/345/678', '64.729.511-8'];
for (const rg of casos) {
  const form = dadosParaFormulario({ nome: 'Pessoa RG', sexoDocumento: 'FEMININO', rg,
    enderecoRua: 'Rua A', enderecoBairro: 'Centro', enderecoNumero: 1 });
  assert.equal(clienteCreateSchema.safeParse(form).success, true);
  assert.equal(clienteUpdateSchema.safeParse(form).success, true);
  assert.equal(clienteCreatePayload(form).rg, rg);
  assert.equal(clienteUpdatePayload(form).rg, rg);
  assert.equal(dadosParaFormulario({ nome: 'Pessoa RG', rg }).rg, rg);
}
const input = readFileSync(new URL('src/components/ClienteFormFields.tsx', frontend), 'utf8');
assert.ok(input.includes('maxLength={nome === "rg" ? 20 : undefined}'));
assert.ok(!input.includes('formatarRg'));
console.log(`${casos.length} casos de entrada/paste/reabertura/payload sem máscara, 72 verificações aprovadas. Backend valida e formata RG.`);
