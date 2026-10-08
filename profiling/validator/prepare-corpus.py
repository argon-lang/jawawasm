"""Convert ordinary module commands in the root spec suite for profiling."""
import json
import pathlib
import subprocess
import sys

repo = pathlib.Path(__file__).resolve().parents[2]
output = pathlib.Path(sys.argv[1]).resolve()
output.mkdir(parents=True, exist_ok=True)
modules = []
for script in sorted((repo / 'webassembly-testsuite').glob('*.wast')):
    directory = output / script.stem
    directory.mkdir(exist_ok=True)
    result = subprocess.run(
        [str(repo / 'cargo-tools/bin/wasm-tools'), 'json-from-wast', str(script),
         '--wasm-dir', str(directory)], capture_output=True, check=True)
    for command in json.loads(result.stdout)['commands']:
        if command['type'] in ('module', 'module_definition'):
            module = directory / command['filename']
            if module.suffix == '.wasm':
                modules.append(str(module))
(output / 'corpus.txt').write_text(''.join(m + '\n' for m in modules))
print(f'{len(modules)} modules')
