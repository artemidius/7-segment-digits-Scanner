"""Assemble a model-free release bundle and verify the local Maven publication."""
import hashlib
import io
from pathlib import Path
import shutil
import xml.etree.ElementTree as ET
import zipfile

ROOT = Path(__file__).resolve().parents[1]


def main():
    properties = dict(line.split('=', 1) for line in (ROOT/'gradle.properties').read_text().splitlines()
                      if '=' in line and not line.startswith('#'))
    group, artifact, version = (properties[key] for key in ['libraryGroup', 'libraryArtifact', 'libraryVersion'])
    repository = ROOT/'build/maven-repository'
    publication = repository/Path(*group.split('.'))/artifact/version
    prefix = f'{artifact}-{version}'
    aar = publication/f'{prefix}.aar'
    with zipfile.ZipFile(aar) as package:
        assert not any(name.endswith(('.tflite', '.jpg', '.pt', '.onnx')) for name in package.namelist()), 'Model leaked into AAR'
        with zipfile.ZipFile(io.BytesIO(package.read('classes.jar'))) as classes:
            assert 'dev/sevendigits/SevenDigitsRecognizer.class' in classes.namelist()
            assert any(name.endswith('/LICENSE') for name in classes.namelist()), 'AAR license missing'
    with zipfile.ZipFile(publication/f'{prefix}-sources.jar') as sources:
        assert 'META-INF/LICENSE' in sources.namelist(), 'Source JAR license missing'
    pom = ET.parse(publication/f'{prefix}.pom').getroot()
    ns = {'m': 'http://maven.apache.org/POM/4.0.0'}
    assert pom.find('m:version', ns).text == version
    dependencies = {node.find('m:artifactId', ns).text for node in pom.findall('m:dependencies/m:dependency', ns)}
    assert {'litert', 'kotlinx-coroutines-core', 'text-recognition'} <= dependencies
    assert pom.find('m:licenses/m:license/m:name', ns).text == 'Apache License, Version 2.0'
    output = ROOT/'dist'
    output.mkdir(exist_ok=True)
    for source in [aar, publication/f'{prefix}-sources.jar', ROOT/'LICENSE', ROOT/'THIRD_PARTY_NOTICES.md']:
        shutil.copyfile(source, output/source.name)
    # Include only this version, not stale versions from previous local builds.
    with zipfile.ZipFile(output/f'{prefix}-maven.zip', 'w', zipfile.ZIP_DEFLATED) as bundle:
        for source in sorted(publication.iterdir()):
            if source.is_file():
                bundle.write(source, Path('maven')/source.relative_to(repository))
        bundle.write(ROOT/'LICENSE', 'LICENSE')
        bundle.write(ROOT/'THIRD_PARTY_NOTICES.md', 'THIRD_PARTY_NOTICES.md')
    files = [output/aar.name, output/f'{prefix}-sources.jar', output/f'{prefix}-maven.zip',
             output/'LICENSE', output/'THIRD_PARTY_NOTICES.md']
    (output/'SHA256SUMS').write_text(''.join(f'{hashlib.sha256(path.read_bytes()).hexdigest()}  {path.name}\n' for path in files))
    print(f'Verified model-free AAR, sources, Maven bundle, license, and checksums in {output}')


if __name__ == '__main__':
    main()
