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
        const expressPath = apiPath.replace(/\{([^}]+)\}/g, ':$1');
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
