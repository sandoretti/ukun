import fs from 'fs';
import yaml from 'yaml';
import path from 'path';

const openapiPaths = [
  path.resolve(process.cwd(), '../../docs/api/openapi/ukun-api.yaml'),
  path.resolve(process.cwd(), '../../docs/api/openapi/ukun-auth.yaml')
];
const outPath = path.resolve(process.cwd(), 'src/mocks/handlers.ts');
const mocksDir = path.resolve(process.cwd(), 'src/mocks');

if (!fs.existsSync(mocksDir)) {
  fs.mkdirSync(mocksDir, { recursive: true });
}

let handlers = `import { http, HttpResponse } from 'msw';\n\nexport const handlers = [\n`;

for (const openapiPath of openapiPaths) {
  if (fs.existsSync(openapiPath)) {
    const file = fs.readFileSync(openapiPath, 'utf8');
    const parsed = yaml.parse(file);
    if (parsed.paths) {
      for (const [apiPath, methods] of Object.entries(parsed.paths)) {
        const expressPath = apiPath
          .replace(/\{([^}]+)\}/g, ':$1')
          // Los métodos personalizados del contrato (`/api/admin/products/{id}:discontinue`,
          // `/internal/accounts/{accountId}:activate`) dejan dos parámetros pegados, y
          // path-to-regexp rechaza el patrón entero con "Must have text between two parameters".
          // Ese fallo no lo nota un test que casa antes con otro handler: revienta en el navegador
          // la primera vez que una petición no casa con ninguno y hay que recorrerlos todos.
          // El `\\:` del fichero generado es un `\:` en la plantilla, que es la forma de escapar
          // los dos puntos literales.
          .replace(/:([A-Za-z0-9_]+):/g, ':$1\\\\:');
        for (const method of Object.keys(methods)) {
          if (['get', 'post', 'put', 'delete', 'patch'].includes(method)) {
            handlers += `  http.${method}(\`\${import.meta.env.VITE_API_URL || ''}${expressPath}\`, () => {\n`;
            handlers += `    // TODO: Implement mock for ${method.toUpperCase()} ${apiPath}\n`;
            handlers += `    return HttpResponse.json({});\n`;
            handlers += `  }),\n`;
          }
        }
      }
    }
  }
}

handlers += `];\n`;

fs.writeFileSync(outPath, handlers);
console.log('Mocks generated at ' + outPath);
