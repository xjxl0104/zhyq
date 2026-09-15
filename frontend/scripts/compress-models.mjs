import { readdir, readFile, writeFile } from 'node:fs/promises'
import { gzipSync } from 'node:zlib'

// Keep the original GLB for clients without gzip and for model export tools.
const directory = new URL('../dist/models/', import.meta.url)
for (const name of await readdir(directory)) {
  if (!name.endsWith('.glb')) continue
  const bytes = await readFile(new URL(name, directory))
  const compressed = gzipSync(bytes, { level: 9 })
  await writeFile(new URL(name + '.gz', directory), compressed)
  console.log(`${name}: ${bytes.length} → ${compressed.length} bytes (gzip)`)
}
